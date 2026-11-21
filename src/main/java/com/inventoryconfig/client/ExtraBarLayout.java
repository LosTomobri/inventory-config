package com.inventoryconfig.client;

import com.inventoryconfig.ExtraBar;

/**
 * Donde va cada hotbar extra en la pantalla. Lo usan la vista de Extra Slots (fase 2) y, despues, el HUD (fase 4),
 * asi lo que se ve en la config es lo mismo que se ve en juego.
 * <p>
 * Un slot mide {@link #CELL} px (igual que en la hotbar de vanilla) y la barra suma 2 px de borde.
 * Los slots van de izquierda a derecha (de arriba hacia abajo en LEFT y RIGHT).
 */
public final class ExtraBarLayout {
    public static final int CELL = 20;
    public static final int MARGIN = 2;
    /** Separacion entre ABOVE_HOTBAR y la hotbar original. */
    public static final int ABOVE_GAP = 4;
    public static final int HOTBAR_W = 182;
    public static final int HOTBAR_H = 22;

    private ExtraBarLayout() {
    }

    /** {x, y, ancho, alto} de la barra de {@code count} slots, para una pantalla de sw x sh (en pixeles de GUI). */
    public static int[] rect(ExtraBar.Position pos, int count, int sw, int sh) {
        boolean v = pos.vertical();
        int w = v ? HOTBAR_H : count * CELL + 2;
        int h = v ? count * CELL + 2 : HOTBAR_H;
        int x = switch (pos) {
            case TOP_LEFT, LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_CENTER, ABOVE_HOTBAR -> (sw - w) / 2;
            case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> sw - w - MARGIN;
        };
        int y = switch (pos) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> MARGIN;
            case LEFT, RIGHT -> (sh - h) / 2;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - h - MARGIN;
            case ABOVE_HOTBAR -> sh - HOTBAR_H - ABOVE_GAP - h;
        };
        return new int[]{x, y, w, h};
    }

    /** Esquina de arriba a la izquierda del slot {@code index} (de {@link #CELL} x {@link #CELL}) dentro de {@code rect}. */
    public static int[] cell(int[] rect, boolean vertical, int index) {
        return vertical
                ? new int[]{rect[0] + 1, rect[1] + 1 + index * CELL}
                : new int[]{rect[0] + 1 + index * CELL, rect[1] + 1};
    }
}
