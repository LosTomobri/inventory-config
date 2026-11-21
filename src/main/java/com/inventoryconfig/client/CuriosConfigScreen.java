package com.inventoryconfig.client;

import com.inventoryconfig.UiOptions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Boton "Curios" de la {@link ConfigScreen} (solo con Curios instalado): opciones del mod Curios en el inventario.
 * <ul>
 *   <li><b>Hide Curios Button:</b> saca el boton de Curios del inventario.</li>
 *   <li><b>Show Curios Slots in Inventory:</b> muestra los slots de Curios en el inventario, arriba del slot de la
 *       mano secundaria.</li>
 * </ul>
 * Los cambios van directo al borrador de la ConfigScreen (se aplican con Save, igual que Hide Crafting).
 */
public class CuriosConfigScreen extends Screen {

    private static final String LANG = "inventoryconfig.";
    private static final int BTN_W = 240;
    private static final int BTN_H = 20;
    private static final int STEP = 24;

    private final ConfigScreen parent;

    public CuriosConfigScreen(ConfigScreen parent) {
        super(Component.translatable(LANG + "gui.curios.title"));
        this.parent = parent;
    }

    private static Component text(String key, boolean on) {
        return Component.translatable(LANG + "gui." + key,
                Component.translatable(LANG + (on ? "gui.on" : "gui.off")));
    }

    @Override
    protected void init() {
        int x = (width - BTN_W) / 2;
        int y = Math.max(30, height / 2 - STEP - BTN_H / 2);

        addRenderableWidget(Button.builder(text("hide_curios", parent.ui.hideCuriosButton()), b -> {
            UiOptions o = parent.ui;
            parent.ui = new UiOptions(o.hideCrafting(), o.hideRecipes(), !o.hideCuriosButton(), o.showCuriosSlots());
            b.setMessage(text("hide_curios", parent.ui.hideCuriosButton()));
        }).bounds(x, y, BTN_W, BTN_H).build());
        y += STEP;

        addRenderableWidget(Button.builder(text("show_curios_slots", parent.ui.showCuriosSlots()), b -> {
            UiOptions o = parent.ui;
            parent.ui = new UiOptions(o.hideCrafting(), o.hideRecipes(), o.hideCuriosButton(), !o.showCuriosSlots());
            b.setMessage(text("show_curios_slots", parent.ui.showCuriosSlots()));
        }).bounds(x, y, BTN_W, BTN_H).build());
        y += STEP + 8;

        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.extra.back"), b -> onClose())
                .bounds((width - 120) / 2, y, 120, BTN_H).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        int y = Math.max(30, height / 2 - STEP - BTN_H / 2);
        g.drawCenteredString(font, title, width / 2, y - 16, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
