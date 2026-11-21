package com.inventoryconfig.client;

import com.inventoryconfig.RadialLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Boton \"Radial Menu\" de la {@link ConfigScreen}: 8 casillas en circulo (arriba, y en sentido horario). Click
 * izquierdo abre el selector de slot ({@link RadialSlotPicker}); click derecho vacia la casilla. Cada casilla lleva UN
 * slot del inventario principal (9-35) y un slot no puede estar en dos casillas. Se edita una copia de trabajo:
 * \"Done\" la pasa al borrador de la ConfigScreen (se guarda con Save alli) y \"Cancel\" la descarta.
 */
public class RadialConfigScreen extends Screen {

    private static final String LANG = "inventoryconfig.";
    private static final int BOX_H = 30;

    private final ConfigScreen parent;
    /** Copia de trabajo: la casilla i tiene el slot cells.get(i) o -1. */
    final List<Integer> cells;

    public RadialConfigScreen(ConfigScreen parent) {
        super(Component.translatable(LANG + "gui.radial.title"));
        this.parent = parent;
        // Si se deshabilito un slot que estaba en el radial, se limpia antes de mostrarlo.
        parent.cleanExtra();
        this.cells = new ArrayList<>(RadialLayout.sanitize(parent.radial, parent.mask).cells());
    }

    void setCell(int cell, int slot) {
        cells.set(cell, slot);
    }

    // ---------------------------------------------------------------- geometria

    /** Radio vertical del anillo: se achica en pantallas bajas. */
    private int ry() {
        return Math.max(40, Math.min(70, (height - 90) / 2 - BOX_H / 2));
    }

    private int rx() {
        return ry() * 10 / 7;
    }

    private int boxW() {
        return Math.max(36, Math.min(64, (int) (rx() * 0.707) - 2));
    }

    private int cx() {
        return width / 2;
    }

    private int cy() {
        return (height - 26) / 2;
    }

    /** Centro de la casilla i: 0 = arriba, y de ahi en sentido horario cada 45 grados. */
    private int[] center(int i) {
        double a = Math.toRadians(i * 45.0);
        return new int[]{cx() + (int) Math.round(rx() * Math.sin(a)), cy() - (int) Math.round(ry() * Math.cos(a))};
    }

    private int cellAt(double mx, double my) {
        int w = boxW();
        for (int i = 0; i < RadialLayout.CELLS; i++) {
            int[] c = center(i);
            if (mx >= c[0] - w / 2.0 && mx < c[0] + w / 2.0 && my >= c[1] - BOX_H / 2.0 && my < c[1] + BOX_H / 2.0) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        int by = Math.min(height - 24, cy() + ry() + BOX_H / 2 + 10);
        addRenderableWidget(Button.builder(Component.translatable(LANG + "radial.done"), b -> done())
                .bounds(cx() - 92, by, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.cancel"), b -> onClose())
                .bounds(cx() + 2, by, 90, 20).build());
    }

    private void done() {
        parent.radial = RadialLayout.sanitize(new RadialLayout(List.copyOf(cells)), parent.mask);
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent); // cancelar: se descartan los cambios de esta pantalla
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- mouse

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int i = cellAt(mx, my);
        if (i < 0) return false;
        if (button == 0) {
            minecraft.setScreen(new RadialSlotPicker(parent, this, i));
            return true;
        }
        if (button == 1) {
            cells.set(i, RadialLayout.EMPTY_CELL);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- dibujo

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick); // fondo y botones

        g.drawCenteredString(font, title, cx(), Math.max(6, cy() - ry() - BOX_H / 2 - 14), 0xFFFFFFFF);
        int hover = cellAt(mx, my);
        int w = boxW();

        for (int i = 0; i < RadialLayout.CELLS; i++) {
            int[] c = center(i);
            int x0 = c[0] - w / 2;
            int y0 = c[1] - BOX_H / 2;
            int slot = cells.get(i);
            boolean assigned = RadialLayout.assignable(slot);
            int border = i == hover ? 0xFFFFFFFF : assigned ? 0xFF55FF55 : 0xFF808080;
            g.fill(x0, y0, x0 + w, y0 + BOX_H, assigned ? 0xC0204020 : 0xC0202020);
            g.renderOutline(x0, y0, w, BOX_H, border);
            if (assigned) {
                // \"Inventory 12\" (recortado si la casilla es angosta)
                String name = font.plainSubstrByWidth(ConfigScreen.slotName(slot).getString(), w - 4);
                g.drawCenteredString(font, name, c[0], c[1] - 4, 0xFFFFFFFF);
            } else {
                g.drawCenteredString(font, "+", c[0], c[1] - 4, 0xFFAAAAAA);
            }
        }

        drawCenterText(g);

        if (hover >= 0) {
            int slot = cells.get(hover);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable(LANG + "radial.cell", hover + 1));
            if (RadialLayout.assignable(slot)) {
                tip.add(ConfigScreen.slotName(slot));
                tip.add(Component.translatable(LANG + "radial.tip.clear"));
            } else {
                tip.add(Component.translatable(LANG + "radial.tip.assign"));
            }
            g.renderTooltip(font, tip, Optional.empty(), mx, my);
        }
    }

    /**
     * Texto del centro (la tecla con la que se abre y la ayuda). Solo puede usar el hueco que dejan las casillas:
     * de ancho, el que hay entre las casillas de los costados; de alto, el que hay entre las diagonales de arriba
     * y de abajo. El texto se parte en lineas del ancho del hueco y, si todavia no entra en alto, se achica.
     */
    private void drawCenterText(GuiGraphics g) {
        Component hint = Component.translatable(LANG + "radial.hint", ExtraKeys.RADIAL.getTranslatedKeyMessage());
        Component help = Component.translatable(LANG + "radial.help");

        int maxW = Math.max(40, 2 * (rx() - boxW() / 2) - 8);
        int diagDy = (int) Math.round(ry() * Math.cos(Math.toRadians(45.0)));
        int maxH = Math.max(20, 2 * (diagDy - BOX_H / 2) - 4);

        final int lineH = 10;
        final int gap = 4;
        float scale = 1.0f;
        List<FormattedCharSequence> hintLines;
        List<FormattedCharSequence> helpLines;
        while (true) {
            int wrap = Math.max(20, (int) (maxW / scale));
            hintLines = font.split(hint, wrap);
            helpLines = font.split(help, wrap);
            float total = ((hintLines.size() + helpLines.size()) * lineH + gap - 1) * scale;
            if (total <= maxH || scale <= 0.5f) break;
            scale -= 0.1f;
        }

        float total = ((hintLines.size() + helpLines.size()) * lineH + gap - 1) * scale;
        g.pose().pushPose();
        g.pose().translate(cx(), cy() - total / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        int y = 0;
        for (FormattedCharSequence line : hintLines) {
            g.drawCenteredString(font, line, 0, y, 0xFFAAAAAA);
            y += lineH;
        }
        y += gap;
        for (FormattedCharSequence line : helpLines) {
            g.drawCenteredString(font, line, 0, y, 0xFF808080);
            y += lineH;
        }
        g.pose().popPose();
    }
}
