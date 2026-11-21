package com.inventoryconfig.client;

import com.inventoryconfig.ExtraBar;
import com.inventoryconfig.InventoryConfig;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Dibuja las hotbars extra con la textura y el selector de la hotbar de vanilla, en las posiciones de
 * {@link ExtraBarLayout} (las mismas de la vista de config). Muestra el numero de cada barra del lado de adentro de
 * la pantalla y resalta la barra activa mientras se mantiene su tecla. Se oculta con F1.
 */
@EventBusSubscriber(modid = InventoryConfig.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ExtraHud {

    private static final ResourceLocation HOTBAR = ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final ResourceLocation SELECTION = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
    private static final ResourceLocation LAYER =
            ResourceLocation.fromNamespaceAndPath(InventoryConfig.MODID, "extra_hotbars");

    private ExtraHud() {
    }

    @SubscribeEvent
    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER, ExtraHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        ExtraMirror.restore(); // por si otro mod cancelo la hotbar de vanilla con el espejo puesto
        if (mc.options.hideGui || !ExtraView.enabled(mc)) return;
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        for (ExtraView.View v : ExtraView.views()) {
            drawBar(g, mc, v, sw, sh);
        }
        RuleVisuals.drawHud(g, mc, sw, sh); // item fantasma de los slots vacios con item fijo
    }

    private static void drawBar(GuiGraphics g, Minecraft mc, ExtraView.View v, int sw, int sh) {
        ExtraBar.Position pos = v.position();
        boolean vertical = pos.vertical();
        int n = v.slots().size();
        int[] r = ExtraBarLayout.rect(pos, n, sw, sh);
        // Sin hotbar original (los 9 slots deshabilitados), la barra que iba encima baja a su lugar.
        if (pos == ExtraBar.Position.ABOVE_HOTBAR && HotbarHud.hidden(mc)) r[1] += HotbarHud.SHIFT;
        boolean active = ExtraInput.activeBar() == v.number();

        background(g, r, vertical, n);
        if (active) g.renderOutline(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, 0xFFFFFF55);

        for (int i = 0; i < n; i++) {
            int[] c = ExtraBarLayout.cell(r, vertical, i);
            int slot = v.slots().get(i);
            // Espejo: el slot en mano muestra el item extra (que en realidad esta en la hotbar).
            ItemStack stack = ExtraMirror.shown(mc.player.getInventory(), slot);
            g.renderItem(stack, c[0] + 2, c[1] + 2, i + 1);
            g.renderItemDecorations(mc.font, stack, c[0] + 2, c[1] + 2);
            // Con la tecla apretada el selector es el resaltado; si no, queda en el slot extra en mano.
            boolean selected = active ? slot == ExtraInput.highlightSlot() : slot == ExtraMirror.heldExtra();
            if (selected) {
                // Igual que vanilla: el selector (24 x 23) se corre 2 px a la izquierda / arriba del slot.
                g.blitSprite(SELECTION, c[0] - 2, c[1] - 2, 24, 23);
            }
        }

        // Numero de la barra (para saber que tecla es), del lado de adentro de la pantalla.
        String label = String.valueOf(v.number());
        int tw = mc.font.width(label);
        int lx;
        int ly;
        if (vertical) {
            lx = pos == ExtraBar.Position.LEFT ? r[0] + r[2] + 3 : r[0] - 3 - tw;
            ly = r[1] + (r[3] - 8) / 2;
        } else {
            lx = r[0] + (r[2] - tw) / 2;
            boolean top = pos == ExtraBar.Position.TOP_LEFT || pos == ExtraBar.Position.TOP_CENTER
                    || pos == ExtraBar.Position.TOP_RIGHT;
            ly = top ? r[1] + r[3] + 2 : r[1] - 10;
        }
        g.drawString(mc.font, label, lx, ly, active ? 0xFFFFFF55 : 0xFFFFFFFF);
    }

    /** Fondo de la hotbar de vanilla recortado a {@code n} slots; las verticales son la misma imagen girada 90 grados. */
    private static void background(GuiGraphics g, int[] r, boolean vertical, int n) {
        if (vertical) {
            g.pose().pushPose();
            g.pose().translate(r[0] + r[2], r[1], 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(90f));
            strip(g, 0, 0, n);
            g.pose().popPose();
        } else {
            strip(g, r[0], r[1], n);
        }
    }

    static void strip(GuiGraphics g, int x, int y, int n) {
        int body = 1 + n * ExtraBarLayout.CELL;
        g.blitSprite(HOTBAR, ExtraBarLayout.HOTBAR_W, ExtraBarLayout.HOTBAR_H, 0, 0, x, y, body, ExtraBarLayout.HOTBAR_H);
        g.blitSprite(HOTBAR, ExtraBarLayout.HOTBAR_W, ExtraBarLayout.HOTBAR_H, ExtraBarLayout.HOTBAR_W - 1, 0,
                x + body, y, 1, ExtraBarLayout.HOTBAR_H);
    }
}
