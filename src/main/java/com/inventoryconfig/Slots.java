package com.inventoryconfig;

import net.minecraft.world.inventory.Slot;

/** Utilidades para los slots que reemplaza el mod. */
public final class Slots {
    private Slots() {
    }

    /** El slot original de vanilla (o del mod ajeno) detras de un HiddenSlot / ShiftedSlot. */
    public static Slot base(Slot slot) {
        if (slot instanceof HiddenSlot h) return h.original;
        if (slot instanceof ShiftedSlot s) return s.original;
        return slot;
    }
}
