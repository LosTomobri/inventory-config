package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Una hotbar extra. {@code slots} tiene hasta {@link #MAX_SLOTS} elementos: cada uno es un indice de
 * {@code Inventory} (9..35) o {@link #UNASSIGNED} (la "U" roja de la config).
 */
public record ExtraBar(Position position, List<Integer> slots) {

    public static final int MAX_SLOTS = 5;
    /** Maximo de slots entre todas las barras: son los 27 slots 9..35 y no se repiten. */
    public static final int MAX_TOTAL = 27;
    public static final int MIN_SLOT = 9;
    public static final int MAX_SLOT = 35;
    public static final int UNASSIGNED = -1;

    /** LEFT y RIGHT son verticales; el resto, horizontales. Una barra por posicion. */
    public enum Position {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT, LEFT, RIGHT, ABOVE_HOTBAR, BOTTOM_LEFT, BOTTOM_RIGHT;

        public static final Position[] VALUES = values();

        public boolean vertical() {
            return this == LEFT || this == RIGHT;
        }
    }

    private static final StreamCodec<ByteBuf, Position> POSITION_CODEC = ByteBufCodecs.idMapper(
            i -> Position.VALUES[Math.floorMod(i, Position.VALUES.length)], Position::ordinal);

    private static final StreamCodec<ByteBuf, List<Integer>> SLOTS_CODEC =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_SLOTS));

    public static final StreamCodec<ByteBuf, ExtraBar> CODEC = StreamCodec.composite(
            POSITION_CODEC, ExtraBar::position,
            SLOTS_CODEC, ExtraBar::slots,
            ExtraBar::new);

    public static boolean assignable(int slot) {
        return slot >= MIN_SLOT && slot <= MAX_SLOT;
    }
}
