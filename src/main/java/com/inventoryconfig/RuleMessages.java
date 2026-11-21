package com.inventoryconfig;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fase 4: aviso corto en la action bar cuando una regla rechaza algo. Cada mensaje tiene un enfriamiento por
 * jugador, asi que no se spamea (por ejemplo, parado encima de un item que no se puede recoger).
 */
@EventBusSubscriber(modid = InventoryConfig.MODID)
public final class RuleMessages {

    /** Ticks que tiene que pasar antes de repetir el mismo mensaje al mismo jugador. */
    private static final long COOLDOWN = 60L;

    private static final Map<UUID, Map<String, Long>> LAST = new HashMap<>();

    private RuleMessages() {
    }

    /** {@code key}: pickup / nodrop (clave de idioma {@code inventoryconfig.msg.rule.<key>}). */
    public static void send(ServerPlayer player, String key) {
        long now = player.serverLevel().getGameTime();
        Map<String, Long> mine = LAST.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        Long last = mine.get(key);
        if (last != null && now - last < COOLDOWN && now >= last) return;
        mine.put(key, now);
        player.displayClientMessage(Component.translatable("inventoryconfig.msg.rule." + key), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }
}
