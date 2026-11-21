package com.inventoryconfig.client;

import com.inventoryconfig.InventoryLock;
import com.inventoryconfig.ModState;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Inventario "achicado": vanilla dibuja la parte de arriba de la textura hasta el primer tramo quitado
 * (con el muñeco), y aca se pegan las partes de abajo (hotbar + borde) salteando los renglones apagados.
 * Que tramos se quitan lo decide {@link LayoutAnalyzer} segun los slots apagados.
 */
public class CompactInventoryScreen extends InventoryScreen implements CompactScreen {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");

    private static final int FULL_HEIGHT = 166;
    /** Zona del crafteo dentro de la textura (la misma que tapa la pantalla de configuracion). */
    private static final int CRAFT_X1 = 95;
    private static final int CRAFT_Y1 = 6;
    private static final int CRAFT_X2 = 172;
    private static final int CRAFT_Y2 = 80;

    private CompactLayout layout = CompactLayout.NONE;

    public CompactInventoryScreen(Player player) {
        super(player);
    }

    /**
     * "Hide Recipes": se saca el boton del recetario (el unico ImageButton de 20x18 del inventario) y, si el
     * recetario estaba abierto, se cierra y la ventana vuelve al centro.
     */
    @Override
    protected void init() {
        super.init();
        if (!ModState.ui.hideRecipes()) return;
        RecipeBookComponent book = getRecipeBookComponent();
        if (book.isVisible()) {
            book.toggleVisibility();
            this.leftPos = book.updateScreenPosition(this.width, this.imageWidth);
        }
        for (GuiEventListener child : List.copyOf(children())) {
            if (child instanceof ImageButton button && button.getWidth() == 20 && button.getHeight() == 18) {
                removeWidget(button);
            }
        }
    }

    /** Sin crafteo tampoco va el rotulo "Crafting" (vanilla lo dibuja aparte, encima del fondo). */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (ModState.ui.hideCrafting()) return;
        super.renderLabels(g, mouseX, mouseY);
    }

    @Override
    public int fullHeight() {
        return FULL_HEIGHT;
    }

    @Override
    public CompactLayout layout() {
        return layout;
    }

    @Override
    public void applyLayout(CompactLayout layout) {
        this.layout = layout;
        this.imageHeight = layout.isEmpty() ? FULL_HEIGHT : layout.collapsedHeight();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (layout.isEmpty()) {
            super.renderBg(g, partialTick, mouseX, mouseY);
            coverCrafting(g);
            return;
        }
        BandRenderer.enableBlend();
        int collapsed = this.imageHeight;
        this.imageHeight = layout.firstTop();        // el super dibuja solo la parte de arriba + el muñeco
        super.renderBg(g, partialTick, mouseX, mouseY);
        this.imageHeight = collapsed;
        for (CompactLayout.Segment s : layout.segments()) {
            if (s.shift() == 0) continue;
            g.blit(TEXTURE, this.leftPos, this.topPos + s.start() - s.shift(),
                    0, s.start(), this.imageWidth, s.end() - s.start());
        }
        coverCrafting(g);
    }

    /** "Hide Crafting": tapa con el color del panel todo el crafteo (grilla, flecha y resultado). Queda el muñeco. */
    private void coverCrafting(GuiGraphics g) {
        if (!ModState.ui.hideCrafting()) return;
        g.fill(this.leftPos + CRAFT_X1, this.topPos + CRAFT_Y1, this.leftPos + CRAFT_X2, this.topPos + CRAFT_Y2,
                PanelColors.of(this));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        // Si desactivan el mod con el inventario abierto, vuelve al inventario normal.
        if (this.minecraft != null && this.minecraft.player != null
                && !InventoryLock.isActive(this.minecraft.player)) {
            this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
        }
    }
}
