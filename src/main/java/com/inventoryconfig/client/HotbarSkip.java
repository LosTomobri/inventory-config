package com.inventoryconfig.client;

import com.inventoryconfig.InventoryConfig;
import com.inventoryconfig.InventoryLock;
import com.inventoryconfig.ModState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Slots de la hotbar original (0..8) deshabilitados en la config: en juego no se pueden elegir.
 * La rueda y las teclas 1..9 los saltean, y si el slot seleccionado queda deshabilitado se pasa al siguiente habilitado.
 * <p>
 * Si estan los 9 deshabilitados no se saltea nada: la hotbar no se dibuja (ver {@link HotbarHud}) y la rueda / las teclas
 * siguen cambiando el slot seleccionado, que es la forma de "guardar" un item extra que se tiene en mano.
 */
@EventBusSubscriber(modid = InventoryConfig.MODID, value = Dist.CLIENT)
public final class HotbarSkip {

    static final long HOTBAR_BITS = 0x1FFL;

    private HotbarSkip() {
    }

    /** Bits 0..8 de los slots deshabilitados; 0 si el mod no aplica al jugador (modo de juego). */
    static long mask(Minecraft mc) {
        if (mc.player == null || !InventoryLock.isActive(mc.player)) return 0L;
        return ModState.disabledMask & HOTBAR_BITS;
    }

    static boolean allDisabled(Minecraft mc) {
        return mask(mc) == HOTBAR_BITS;
    }

    /** El slot {@code i} (0..8) no se puede elegir: esta deshabilitado y hay al menos uno habilitado. */
    static boolean blocked(Minecraft mc, int i) {
        long m = mask(mc);
        return m != 0L && m != HOTBAR_BITS && (m >> i & 1L) != 0L;
    }

    /** Siguiente slot habilitado desde {@code from} en la direccion {@code dir} (+1 / -1), o -1 si no hay. */
    static int next(long mask, int from, int dir) {
        for (int k = 1; k <= 9; k++) {
            int s = Math.floorMod(from + dir * k, 9);
            if ((mask >> s & 1L) == 0L) return s;
        }
        return -1;
    }

    /** Rueda: salta los slots deshabilitados. (LOW: si una hotbar extra activa ya la uso, no llega aca.) */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.player.isSpectator()) return;
        long mask = mask(mc);
        if (mask == 0L || mask == HOTBAR_BITS) return;
        double delta = event.getScrollDeltaY();
        if (delta == 0) return;
        event.setCanceled(true);
        Inventory inv = mc.player.getInventory();
        // Igual que vanilla: rueda hacia arriba = slot anterior.
        int n = next(mask, inv.selected, delta > 0 ? -1 : 1);
        if (n >= 0) inv.selected = n;
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        long mask = mask(mc);
        if (mask == 0L || mask == HOTBAR_BITS) return;

        // Teclas 1..9 de los slots deshabilitados: se descartan los clicks antes de que vanilla los use.
        KeyMapping[] keys = mc.options.keyHotbarSlots;
        for (int i = 0; i < keys.length && i < 9; i++) {
            if ((mask >> i & 1L) == 0L) continue;
            while (keys[i].consumeClick()) {
                // descartado
            }
        }

        // El slot seleccionado quedo deshabilitado (cambio la config, otro mod, pick block...): al siguiente habilitado.
        Inventory inv = mc.player.getInventory();
        if ((mask >> inv.selected & 1L) != 0L) {
            int n = next(mask, inv.selected, 1);
            if (n >= 0) inv.selected = n;
        }
    }
}
