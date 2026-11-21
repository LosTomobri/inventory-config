package com.inventoryconfig.client;

import com.inventoryconfig.InventoryConfig;
import com.inventoryconfig.InventoryLock;
import com.inventoryconfig.SlotRules;
import com.inventoryconfig.Slots;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Fase 3.5, lado cliente: \"No soltar\" sin parpadeos. El servidor es la autoridad ({@code NoDropGuard}); esto solo
 * evita mandarle lo que va a rechazar, con la misma tecnica que {@link HotbarSkip}: descartar los clicks de la tecla.
 * <ul>
 *   <li>En el juego: se descarta la tecla de tirar si el slot seleccionado tiene \"No soltar\".</li>
 *   <li>En una pantalla: la tecla de tirar sobre un slot protegido, el click fuera de la ventana con un stack que
 *       levanto un slot protegido, y (con un contenedor externo abierto) la tecla numerica / F que cambiaria un
 *       slot del contenedor por uno protegido.</li>
 * </ul>
 * Con un intercambio de barra extra activo, la regla se busca en el slot logico (lo resuelve {@link SlotRules.Ctx}).
 */
@EventBusSubscriber(modid = InventoryConfig.MODID, value = Dist.CLIENT)
public final class NoDropInput {

    /** Lo que lleva el cursor lo levanto un slot con \"No soltar\". */
    private static boolean carriedProtected = false;

    private NoDropInput() {
    }

    private static boolean active(Minecraft mc) {
        return mc.player != null && !mc.player.isSpectator() && InventoryLock.isActive(mc.player);
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !active(mc)) return;
        SlotRules.Ctx c = SlotRules.ctx(mc.player);
        if (c.isEmpty() || !c.noDrop(mc.player.getInventory().selected)) return;
        while (mc.options.keyDrop.consumeClick()) {
            // descartado
        }
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || screen.getMenu().getCarried().isEmpty()) {
            carriedProtected = false;
        }
    }

    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc)) return;
        SlotRules.Ctx c = SlotRules.ctx(mc.player);
        Slot hovered = screen.getSlotUnderMouse();
        if (c.isEmpty() || hovered == null) return;
        Inventory inv = mc.player.getInventory();
        boolean own = hovered.container == inv;
        int key = event.getKeyCode();
        int scan = event.getScanCode();

        // Tirar el slot protegido (Q / Ctrl+Q sobre el).
        if (mc.options.keyDrop.matches(key, scan) && own && hovered.hasItem()
                && c.noDrop(Slots.base(hovered).getContainerSlot())) {
            event.setCanceled(true);
            return;
        }

        // Tecla numerica / F: cambia el slot de abajo del mouse por el de la hotbar / mano secundaria. Si el slot de
        // abajo es del contenedor externo, lo protegido iria a parar al contenedor.
        if (own || mc.player.containerMenu == mc.player.inventoryMenu) return;
        int target = targetSlot(mc, key, scan);
        if (target >= 0 && !inv.getItem(target).isEmpty() && c.noDrop(target)) {
            event.setCanceled(true);
        }
    }

    /** Slot de Inventory que cambia esa tecla (0..8 por 1..9, 40 por F), o -1. */
    private static int targetSlot(Minecraft mc, int key, int scan) {
        KeyMapping[] hotbar = mc.options.keyHotbarSlots;
        for (int j = 0; j < hotbar.length && j < 9; j++) {
            if (hotbar[j].matches(key, scan)) return j;
        }
        return mc.options.keySwapOffhand.matches(key, scan) ? 40 : -1;
    }

    @SubscribeEvent
    public static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc)) return;
        Player player = mc.player;
        boolean carrying = !screen.getMenu().getCarried().isEmpty();

        if (!carrying) {
            // Si el click levanta un stack de un slot protegido, el cursor queda marcado.
            Slot hovered = screen.getSlotUnderMouse();
            SlotRules.Ctx c = SlotRules.ctx(player);
            carriedProtected = !c.isEmpty() && hovered != null && hovered.hasItem()
                    && hovered.container == player.getInventory()
                    && c.noDrop(Slots.base(hovered).getContainerSlot());
            return;
        }
        if (!carriedProtected) return;
        double mx = event.getMouseX();
        double my = event.getMouseY();
        boolean outside = mx < screen.getGuiLeft() || mx >= screen.getGuiLeft() + screen.getXSize()
                || my < screen.getGuiTop() || my >= screen.getGuiTop() + screen.getYSize();
        // (un boton o el recetario por fuera de la ventana no cuentan como \"fuera\")
        if (outside && screen.getSlotUnderMouse() == null && screen.getChildAt(mx, my).isEmpty()) {
            event.setCanceled(true);
        }
    }
}
