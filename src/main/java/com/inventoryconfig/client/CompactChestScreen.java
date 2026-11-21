package com.inventoryconfig.client;

import com.inventoryconfig.InventoryLock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/**
 * Cofre / cofre doble / ender chest / barril sin los renglones de inventario apagados.
 * <p>
 * La textura generic_54 tiene dos pedazos: la parte del contenedor (17 + filas*18 de alto) y una
 * parte inferior fija de 96 (rotulo + 3 filas + hotbar + borde, desde v=126). Se dibuja la ventana
 * por segmentos, salteando los tramos que decidio {@link LayoutAnalyzer}.
 */
public class CompactChestScreen extends ContainerScreen implements CompactScreen {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    private static final int BOTTOM_V = 126;      // donde empieza la parte inferior en la textura

    private final int rows;
    private CompactLayout layout = CompactLayout.NONE;

    public CompactChestScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.rows = menu.getRowCount();
    }

    /** Alto dibujado de la ventana normal. */
    @Override
    public int fullHeight() {
        return 113 + this.rows * 18;
    }

    @Override
    public CompactLayout layout() {
        return layout;
    }

    @Override
    public void applyLayout(CompactLayout layout) {
        this.layout = layout;
        // Sin cambios: el mismo alto que pone vanilla (114 + filas*18).
        this.imageHeight = layout.isEmpty() ? 114 + this.rows * 18 : layout.collapsedHeight();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        BandRenderer.enableBlend();
        if (layout.isEmpty()) {
            blitRange(g, 0, fullHeight(), this.topPos);
            return;
        }
        for (CompactLayout.Segment s : layout.segments()) {
            blitRange(g, s.start(), s.end(), this.topPos + s.start() - s.shift());
        }
    }

    /** Dibuja las filas [from, to) de la ventana original en destY, sabiendo de donde sale cada una en la textura. */
    private void blitRange(GuiGraphics g, int from, int to, int destY) {
        int containerHeight = this.rows * 18 + 17;
        if (from < containerHeight) {
            int end = Math.min(to, containerHeight);
            g.blit(TEXTURE, this.leftPos, destY, 0, from, this.imageWidth, end - from);
            destY += end - from;
            from = end;
        }
        if (from < to) {
            g.blit(TEXTURE, this.leftPos, destY, 0, BOTTOM_V + (from - containerHeight), this.imageWidth, to - from);
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        // Si desactivan el mod con el cofre abierto, vuelve al cofre normal.
        if (this.minecraft != null && this.minecraft.player != null
                && !InventoryLock.isActive(this.minecraft.player)) {
            this.minecraft.setScreen(new ContainerScreen(this.menu, this.minecraft.player.getInventory(), this.title));
        }
    }
}
