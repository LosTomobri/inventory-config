package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Todo lo de las hotbars extra que viaja junto: las barras (en orden de creacion, que es su numero de tecla)
 * y el interruptor "Hide in Inventory".
 */
public record ExtraLayout(List<ExtraBar> bars, boolean hideAssigned) {

    public static final ExtraLayout EMPTY = new ExtraLayout(List.of(), false);

    public static final StreamCodec<ByteBuf, ExtraLayout> CODEC = StreamCodec.composite(
            ExtraBar.CODEC.apply(ByteBufCodecs.list(ExtraBar.Position.VALUES.length)), ExtraLayout::bars,
            ByteBufCodecs.BOOL, ExtraLayout::hideAssigned,
            ExtraLayout::new);

    /** Barras -> lista NBT (se guarda en el mundo y en los perfiles). */
    public static ListTag barsToTag(List<ExtraBar> bars) {
        ListTag out = new ListTag();
        for (ExtraBar bar : bars) {
            CompoundTag t = new CompoundTag();
            t.putString("position", bar.position().name());
            t.putIntArray("slots", bar.slots().stream().mapToInt(Integer::intValue).toArray());
            out.add(t);
        }
        return out;
    }

    /** Lista NBT -> barras (sin validar: despues se llama a {@link #sanitize}). Las posiciones desconocidas se descartan. */
    public static List<ExtraBar> barsFromTag(CompoundTag tag) {
        List<ExtraBar> bars = new ArrayList<>();
        ListTag barTags = tag.getList("extraBars", Tag.TAG_COMPOUND);
        for (int i = 0; i < barTags.size(); i++) {
            CompoundTag t = barTags.getCompound(i);
            try {
                ExtraBar.Position pos = ExtraBar.Position.valueOf(t.getString("position"));
                List<Integer> slots = new ArrayList<>();
                for (int s : t.getIntArray("slots")) slots.add(s);
                bars.add(new ExtraBar(pos, slots));
            } catch (IllegalArgumentException ignored) {
                // posicion desconocida: se descarta la barra
            }
        }
        return bars;
    }

    /** Mascara de bits con los slots del inventario asignados a alguna barra. */
    public long assignedMask() {
        long m = 0L;
        for (ExtraBar bar : bars) {
            for (int s : bar.slots()) {
                if (ExtraBar.assignable(s)) m |= 1L << s;
            }
        }
        return m;
    }

    /**
     * Deja el layout valido (el servidor lo aplica siempre antes de guardar):
     * una barra por posicion, maximo 5 slots por barra y 27 en total, indices solo 9..35, sin repetidos y sin
     * slots deshabilitados. Lo invalido pasa a "U" (sin asignar); las barras vacias se descartan.
     */
    public static ExtraLayout sanitize(List<ExtraBar> bars, boolean hide, long disabledMask) {
        List<ExtraBar> out = new ArrayList<>();
        EnumSet<ExtraBar.Position> usedPositions = EnumSet.noneOf(ExtraBar.Position.class);
        boolean[] used = new boolean[ConfigData.SLOT_COUNT];
        int total = 0;
        for (ExtraBar bar : bars) {
            if (bar == null || bar.position() == null || bar.slots() == null) continue;
            if (!usedPositions.add(bar.position())) continue;
            List<Integer> slots = new ArrayList<>();
            for (Integer boxed : bar.slots()) {
                if (boxed == null || slots.size() >= ExtraBar.MAX_SLOTS || total >= ExtraBar.MAX_TOTAL) break;
                int s = boxed;
                boolean valid = ExtraBar.assignable(s) && !used[s] && ((disabledMask >> s) & 1L) == 0L;
                if (valid) used[s] = true;
                slots.add(valid ? s : ExtraBar.UNASSIGNED);
                total++;
            }
            if (!slots.isEmpty()) out.add(new ExtraBar(bar.position(), List.copyOf(slots)));
        }
        return new ExtraLayout(List.copyOf(out), hide);
    }
}
