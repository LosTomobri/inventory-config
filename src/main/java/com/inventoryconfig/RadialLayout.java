package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Las 8 casillas del menu radial (en sentido horario desde arriba): cada una es un indice de
 * {@code Inventory} (9..35, solo inventario principal) o {@link #EMPTY_CELL}.
 */
public record RadialLayout(List<Integer> cells) {

    public static final int CELLS = 8;
    public static final int EMPTY_CELL = -1;
    public static final int MIN_SLOT = 9;
    public static final int MAX_SLOT = 35;

    public static final RadialLayout EMPTY = new RadialLayout(List.of(-1, -1, -1, -1, -1, -1, -1, -1));

    public static final StreamCodec<ByteBuf, RadialLayout> CODEC =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(CELLS)).map(RadialLayout::new, RadialLayout::cells);

    public static boolean assignable(int slot) {
        return slot >= MIN_SLOT && slot <= MAX_SLOT;
    }

    /** Mascara de bits con los slots asignados a alguna casilla. */
    public long assignedMask() {
        long m = 0L;
        for (int s : cells) {
            if (assignable(s)) m |= 1L << s;
        }
        return m;
    }

    public boolean isEmpty() {
        return assignedMask() == 0L;
    }

    public static RadialLayout fromArray(int[] arr) {
        List<Integer> list = new ArrayList<>();
        for (int s : arr) list.add(s);
        return new RadialLayout(list);
    }

    public int[] toArray() {
        return cells.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Deja el radial valido (el servidor lo aplica siempre): exactamente 8 casillas, solo slots 9..35,
     * no deshabilitados y sin repetidos. Cualquier otra cosa pasa a -1.
     */
    public static RadialLayout sanitize(RadialLayout radial, long disabledMask) {
        List<Integer> out = new ArrayList<>(CELLS);
        boolean[] used = new boolean[ConfigData.SLOT_COUNT];
        List<Integer> in = radial == null || radial.cells() == null ? List.of() : radial.cells();
        for (int i = 0; i < CELLS; i++) {
            int s = i < in.size() && in.get(i) != null ? in.get(i) : EMPTY_CELL;
            boolean valid = assignable(s) && !used[s] && ((disabledMask >> s) & 1L) == 0L;
            if (valid) used[s] = true;
            out.add(valid ? s : EMPTY_CELL);
        }
        return new RadialLayout(List.copyOf(out));
    }
}
