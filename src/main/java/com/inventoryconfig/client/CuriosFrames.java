package com.inventoryconfig.client;

import com.inventoryconfig.CuriosSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * Dibuja el marco de los slots de Curios que se muestran en el inventario. Vanilla ya dibuja el item (o el icono del
 * tipo, si esta vacio), pero la textura del inventario no tiene casillas en ese lugar: se copia el marco del slot de la
 * mano secundaria. No usa tipos de Curios.
 */
final class CuriosFrames {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    /** Marco del slot de la mano secundaria dentro de la textura (el slot esta en 77,62). */
    private static final int FRAME_U = 76;
    private static final int FRAME_V = 61;
    private static final int FRAME = 18;

    private CuriosFrames() {
    }

    static void draw(AbstractContainerScreen<?> screen, GuiGraphics g) {
        if (!CuriosSupport.loaded()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        AbstractContainerMenu menu = screen.getMenu();
        if (menu != mc.player.inventoryMenu) return;

        List<Slot> slots = menu.slots;
        for (int i = CuriosSupport.base(menu); i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (!slot.isActive()) continue; // relleno: todavia no se puede usar
            g.blit(TEXTURE, screen.getGuiLeft() + slot.x - 1, screen.getGuiTop() + slot.y - 1,
                    FRAME_U, FRAME_V, FRAME, FRAME);
        }
    }
}
