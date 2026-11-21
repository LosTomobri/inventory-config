package com.inventoryconfig.client;

import com.inventoryconfig.HiddenSlot;
import com.inventoryconfig.Slots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Decide como se achica una pantalla mirando UNICAMENTE que slots estan apagados.
 * No sabe nada de cofres, hornos ni mods: si hay renglones completos de slots apagados y
 * se pueden quitar sin romper nada, devuelve esos tramos.
 * <p>
 * Un tramo se puede quitar si:
 * <ul>
 *   <li>es un renglon completo (9 slots de ancho), no un slot suelto (esos solo se tapan con el color del panel);</li>
 *   <li>no hay ningun slot visible adentro (en esa franja horizontal);</li>
 *   <li>los slots visibles que quedan abajo se pueden subir (son del inventario del jugador).</li>
 * </ul>
 * Si no se puede, se explica por que en {@code notes} (para el log).
 */
final class LayoutAnalyzer {
    private static final int CELL = 18;
    private static final int FULL_ROW = 9 * CELL;

    private LayoutAnalyzer() {
    }

    private static final class Region {
        int top;
        int bottom;
        int x1;
        int x2;

        Region(int top, int bottom, int x1, int x2) {
            this.top = top;
            this.bottom = bottom;
            this.x1 = x1;
            this.x2 = x2;
        }
    }

    /** @param fullHeight alto original de la ventana, antes de achicarla */
    static CompactLayout analyze(AbstractContainerScreen<?> screen, int fullHeight) {
        return analyze(screen, fullHeight, null);
    }

    /** @param notes si no es null, ahi se agregan los motivos por los que algo no se pudo quitar */
    static CompactLayout analyze(AbstractContainerScreen<?> screen, int fullHeight, List<String> notes) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || fullHeight <= 0) return CompactLayout.NONE;
        Inventory inv = mc.player.getInventory();
        List<Slot> slots = screen.getMenu().slots;

        // 1) slots apagados agrupados por renglon: y -> [x inicial, x final) de cada casilla
        Map<Integer, List<int[]>> rows = new TreeMap<>();
        for (Slot slot : slots) {
            if (slot instanceof HiddenSlot) {
                rows.computeIfAbsent(slot.y, k -> new ArrayList<>())
                        .add(new int[]{slot.x - 1, slot.x - 1 + CELL});
            }
        }
        if (rows.isEmpty()) {
            note(notes, "no hay slots apagados: ningun slot de este menu es del inventario del jugador "
                    + "(o el menu usa otro contenedor/wrapper para mostrarlo)");
            return CompactLayout.NONE;
        }

        // 2) renglones completos -> regiones (los que se tocan se unen)
        List<Region> regions = new ArrayList<>();
        for (Map.Entry<Integer, List<int[]>> e : rows.entrySet()) {
            List<int[]> xs = e.getValue();
            if (coverage(xs) < FULL_ROW) {
                note(notes, "renglon y=" + e.getKey() + " incompleto (" + xs.size() + " slots apagados, hacen falta 9 juntos)");
                continue;
            }
            int top = e.getKey() - 1;
            int bottom = top + CELL;
            int x1 = Integer.MAX_VALUE;
            int x2 = Integer.MIN_VALUE;
            for (int[] x : xs) {
                x1 = Math.min(x1, x[0]);
                x2 = Math.max(x2, x[1]);
            }
            Region last = regions.isEmpty() ? null : regions.get(regions.size() - 1);
            if (last != null && top <= last.bottom) {
                last.bottom = Math.max(last.bottom, bottom);
                last.x1 = Math.min(last.x1, x1);
                last.x2 = Math.max(last.x2, x2);
            } else {
                regions.add(new Region(top, bottom, x1, x2));
            }
        }

        // 3) descartar las que no se pueden quitar
        List<CompactLayout.Band> bands = new ArrayList<>();
        for (Region r : regions) {
            if (r.top <= 0 || r.bottom > fullHeight) {
                note(notes, "tramo y=" + r.top + ".." + r.bottom + " fuera de la ventana (alto " + fullHeight + ")");
                continue;
            }
            String blocker = blockedBy(r, slots, inv);
            if (blocker != null) {
                note(notes, "tramo y=" + r.top + ".." + r.bottom + " no se puede quitar: " + blocker);
                continue;
            }
            bands.add(new CompactLayout.Band(r.top, r.bottom));
        }
        return bands.isEmpty() ? CompactLayout.NONE : new CompactLayout(fullHeight, bands);
    }

    private static void note(List<String> notes, String text) {
        if (notes != null) notes.add(text);
    }

    /** Devuelve por que el tramo no se puede quitar, o null si se puede. */
    private static String blockedBy(Region r, List<Slot> slots, Inventory inv) {
        for (Slot slot : slots) {
            if (slot instanceof HiddenSlot || !slot.isActive()) continue;
            Slot b = Slots.base(slot);
            int cx1 = b.x - 1;
            int cx2 = b.x - 1 + CELL;
            int cy1 = b.y - 1;
            int cy2 = b.y - 1 + CELL;
            if (cx2 <= r.x1 || cx1 >= r.x2) continue; // fuera de la franja (ej. slots laterales de Curios)
            if (cy2 > r.top && cy1 < r.bottom) {
                return "hay un slot visible adentro (" + describe(b) + ")";
            }
            if (cy1 >= r.bottom && slot.container != inv) {
                // abajo hay un slot que no es del inventario del jugador: no hay forma segura de subirlo
                return "hay un slot de otro contenedor abajo (" + describe(b) + ")";
            }
        }
        return null;
    }

    private static String describe(Slot s) {
        return "x=" + s.x + " y=" + s.y + " " + s.getClass().getName()
                + " / contenedor " + s.container.getClass().getSimpleName();
    }

    /** Largo total cubierto por los intervalos [a, b), sin contar dos veces lo que se solapa. */
    private static int coverage(List<int[]> xs) {
        List<int[]> sorted = new ArrayList<>(xs);
        sorted.sort(Comparator.comparingInt(a -> a[0]));
        int total = 0;
        int start = sorted.get(0)[0];
        int end = sorted.get(0)[1];
        for (int i = 1; i < sorted.size(); i++) {
            int[] a = sorted.get(i);
            if (a[0] <= end) {
                end = Math.max(end, a[1]);
            } else {
                total += end - start;
                start = a[0];
                end = a[1];
            }
        }
        return total + end - start;
    }
}
