package com.inventoryconfig;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fase 3.5, lado servidor: \"No soltar\". Lo que haya en un slot con esa regla no se puede tirar al suelo.
 * <p>
 * {@link ItemTossEvent} se cancela, pero en NeoForge cancelarlo NO devuelve el item (ya salio del inventario), asi que
 * hay que reponerlo a mano. Para saber de que slot salio hay que mirar el estado del tick anterior: se guarda una
 * copia de los slots protegidos y, mientras el cursor lleva un stack, de cual slot protegido salio.
 * <p>
 * Orden de las comprobaciones al tirar un stack T:
 * <ol>
 *   <li>T viene del cursor y el cursor lo levanto un slot protegido (tirar haciendo click fuera de la ventana);</li>
 *   <li>un slot protegido perdio items del mismo tipo que T desde el tick anterior (Q / Ctrl+Q, en el juego o
 *       sobre un slot en una pantalla).</li>
 * </ol>
 */
@EventBusSubscriber(modid = InventoryConfig.MODID)
public final class NoDropGuard {

    private static final class Snap {
        /** Copia de los slots fisicos protegidos al final del tick anterior (null = ese slot no esta protegido). */
        final ItemStack[] slots = new ItemStack[ConfigData.SLOT_COUNT];
        /** Slot protegido de donde salio lo que lleva el cursor, o -1. */
        int carriedFrom = -1;
        boolean hadCarried = false;
    }

    private static final Map<UUID, Snap> SNAPS = new HashMap<>();

    /** Verdadero mientras el propio mod tira un item (expulsiones por reglas): esos no se protegen. */
    private static boolean internal = false;

    private NoDropGuard() {
    }

    /** El mod tira un item que no tiene lugar: no pasa por la proteccion de \"No soltar\". */
    public static void dropInternal(ServerPlayer player, ItemStack stack) {
        internal = true;
        try {
            player.drop(stack, false);
        } finally {
            internal = false;
        }
    }

    /** Se llama al final del tick del jugador, despues de las reglas: deja la \"foto\" para el tick siguiente. */
    static void tick(ServerPlayer player) {
        SlotRules.Ctx c = SlotRules.ctx(player);
        long mask = InventoryLock.isActive(player) ? c.noDropMask() : 0L;
        if (mask == 0L) {
            SNAPS.remove(player.getUUID());
            return;
        }
        Snap s = SNAPS.computeIfAbsent(player.getUUID(), id -> new Snap());
        Inventory inv = player.getInventory();
        ItemStack carried = player.containerMenu.getCarried();

        if (carried.isEmpty()) {
            s.carriedFrom = -1;
        } else if (s.carriedFrom < 0 && !s.hadCarried) {
            // Lo levanto en este tick: de que slot protegido salio?
            for (int i = 0; i < ConfigData.SLOT_COUNT; i++) {
                ItemStack prev = s.slots[i];
                if (prev == null || prev.isEmpty() || !ItemStack.isSameItemSameComponents(prev, carried)) continue;
                ItemStack now = inv.getItem(i);
                if (now.isEmpty() || now.getCount() < prev.getCount()) {
                    s.carriedFrom = i;
                    break;
                }
            }
        }
        s.hadCarried = !carried.isEmpty();

        for (int i = 0; i < ConfigData.SLOT_COUNT; i++) {
            s.slots[i] = (mask >> i & 1L) != 0L ? inv.getItem(i).copy() : null;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onToss(ItemTossEvent event) {
        if (internal || !(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!InventoryLock.isActive(player)) return;
        Snap s = SNAPS.get(player.getUUID());
        if (s == null) return;

        ItemEntity entity = event.getEntity();
        ItemStack tossed = entity.getItem();
        if (tossed.isEmpty()) return;

        int origin = origin(player, s, tossed);
        if (origin < 0) return;

        // Se cancela y se repone a mano. El item de la entidad se vacia por si ya se habia agregado al mundo
        // (una entidad con item vacio se descarta sola): asi no puede duplicarse.
        ItemStack back = tossed.copy();
        event.setCanceled(true);
        entity.setItem(ItemStack.EMPTY);
        giveBack(player, origin, back);
    }

    private static int origin(ServerPlayer player, Snap s, ItemStack tossed) {
        ItemStack carried = player.containerMenu.getCarried();
        if (s.carriedFrom >= 0 && !carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, tossed)) {
            return s.carriedFrom;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < ConfigData.SLOT_COUNT; i++) {
            ItemStack prev = s.slots[i];
            if (prev == null || prev.isEmpty() || !ItemStack.isSameItemSameComponents(prev, tossed)) continue;
            ItemStack now = inv.getItem(i);
            if (now.isEmpty()
                    || (ItemStack.isSameItemSameComponents(now, prev) && now.getCount() + tossed.getCount() <= prev.getCount())) {
                return i;
            }
        }
        return -1;
    }

    /** Devuelve el stack al mismo slot; si ya hay otra cosa ahi, al inventario; si no entra, al suelo. */
    private static void giveBack(ServerPlayer player, int slot, ItemStack back) {
        Inventory inv = player.getInventory();
        ItemStack now = inv.getItem(slot);
        if (now.isEmpty()) {
            inv.setItem(slot, back);
            return;
        }
        if (ItemStack.isSameItemSameComponents(now, back)
                && now.getCount() + back.getCount() <= now.getMaxStackSize()) {
            now.grow(back.getCount());
            return;
        }
        if (!inv.add(back) && !back.isEmpty()) {
            dropInternal(player, back);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SNAPS.remove(event.getEntity().getUUID());
    }
}
