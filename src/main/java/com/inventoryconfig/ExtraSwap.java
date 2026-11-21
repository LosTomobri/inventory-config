package com.inventoryconfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Fase 6, lado servidor: el intercambio con espejo de las hotbars extra.
 * <p>
 * Al elegir el slot extra E con la hotbar original en el slot seleccionado S, los dos items se intercambian de
 * verdad (asi la mano, los ataques y los mods ajenos ven el item extra en la mano sin mixins). El cliente dibuja
 * el espejo para que en la hotbar normal no se note (ver {@code ExtraMirror}).
 * <p>
 * El par (S, E) se guarda en los datos persistentes del jugador, junto a su inventario: si el juego se cierra
 * o se cae con un intercambio activo, al volver a entrar se deshace solo. Se revierte cuando:
 * se elige otro slot extra, cambia el slot seleccionado, se abre un contenedor, cambia la config, el jugador
 * muere, se desconecta o deja de aplicarle el mod (modo de juego).
 */
@EventBusSubscriber(modid = InventoryConfig.MODID)
public final class ExtraSwap {

    private static final String TAG = InventoryConfig.MODID + "_swap";

    /** Intercambio activo: {@code hotbar} (0..8) tiene el item extra y {@code extra} (9..35) el de la hotbar. */
    public record State(int hotbar, int extra) {
        public static final State NONE = new State(-1, -1);

        public boolean active() {
            return hotbar >= 0;
        }
    }

    /** Jugadores cuyo estado hay que mandar al cliente (se manda al final del tick, despues de los slots). */
    private static final Set<UUID> DIRTY = new HashSet<>();

    private ExtraSwap() {
    }

    /** Intercambio activo del jugador (solo servidor). */
    public static State get(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(TAG, Tag.TAG_COMPOUND)) return State.NONE;
        CompoundTag t = root.getCompound(TAG);
        int h = t.getInt("hotbar");
        int e = t.getInt("extra");
        if (h < 0 || h > 8 || !ExtraBar.assignable(e)) {
            root.remove(TAG); // dato corrupto
            return State.NONE;
        }
        return new State(h, e);
    }

    // ---------------------------------------------------------------- acciones

    /** El cliente solto la tecla de una barra y eligio {@code slot} (o pide deshacer con REVERT). */
    public static void select(ServerPlayer player, int slot) {
        if (slot == ExtraSelectPayload.REVERT) {
            revert(player);
            return;
        }
        if (!canUse(player, slot)) return; // peticion invalida: no pasa nada

        Inventory inv = player.getInventory();
        int s = inv.selected;
        State cur = get(player);
        if (cur.active() && cur.hotbar() == s && cur.extra() == slot) return; // ya esta asi

        revert(player); // si habia otro slot extra en mano, vuelve a su lugar antes de hacer el nuevo
        swap(inv, s, slot);
        CompoundTag t = new CompoundTag();
        t.putInt("hotbar", s);
        t.putInt("extra", slot);
        player.getPersistentData().put(TAG, t);
        DIRTY.add(player.getUUID());
    }

    /** Deshace el intercambio activo (si no hay, no hace nada). */
    public static void revert(ServerPlayer player) {
        State st = get(player);
        if (!st.active()) return;
        player.getPersistentData().remove(TAG);
        swap(player.getInventory(), st.hotbar(), st.extra());
        DIRTY.add(player.getUUID());
    }

    public static void revertAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) revert(p);
    }

    /**
     * Si se puede hacer el intercambio: el mod aplica al jugador, esta vivo y con el inventario normal, el slot es
     * uno asignado a alguna barra o a una casilla del radial (y habilitado) y el slot seleccionado es de la hotbar. Si no, se cancela sin
     * efecto (por ejemplo, un cliente que manda un slot que no le corresponde).
     */
    private static boolean canUse(ServerPlayer player, int slot) {
        if (!player.isAlive() || player.isSpectator() || !InventoryLock.isActive(player)) return false;
        if (player.containerMenu != player.inventoryMenu) return false;
        if (!ExtraBar.assignable(slot) || InventoryLock.isDisabled(player, slot)) return false;
        ConfigData data = ConfigData.get(player.server);
        long allowed = data.extraAssignedMask() | data.radialAssignedMask();
        if ((allowed >> slot & 1L) == 0L) return false;
        int s = player.getInventory().selected;
        return s >= 0 && s <= 8;
    }

    private static void swap(Inventory inv, int a, int b) {
        ItemStack x = inv.getItem(a);
        ItemStack y = inv.getItem(b);
        inv.setItem(a, y);
        inv.setItem(b, x);
    }

    // ---------------------------------------------------------------- eventos

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State st = get(p);
        if (!st.active()) return;
        if (p.getInventory().selected != st.hotbar() // se eligio un slot normal (rueda, numero, otro mod...)
                || p.containerMenu != p.inventoryMenu // se abrio un cofre, mesa de crafteo...
                || !p.isAlive() || p.isSpectator() || !InventoryLock.isActive(p)) {
            revert(p);
        }
    }

    /** Antes de que se tiren los items de la muerte: cada uno vuelve a su lugar. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) revert(p);
    }

    /** Se revierte antes de que el servidor guarde al jugador. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            revert(p);
            DIRTY.remove(p.getUUID());
        }
    }

    /** Si quedo un intercambio de una sesion que se cayo, se deshace; y el cliente arranca sin espejo. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            revert(p);
            sendState(p);
        }
    }

    /** Manda el estado a los jugadores que cambiaron: despues de que se sincronizaron sus slots. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (DIRTY.isEmpty()) return;
        for (UUID id : DIRTY) {
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(id);
            if (p != null) sendState(p);
        }
        DIRTY.clear();
    }

    private static void sendState(ServerPlayer p) {
        State st = get(p);
        PacketDistributor.sendToPlayer(p, new ExtraSwapPayload(st.hotbar(), st.extra()));
    }
}
