package com.inventoryconfig;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Slot "apagado": no se dibuja, no se puede clickear, no acepta ni entrega items. */
public class HiddenSlot extends Slot {
    public final Slot original;

    public HiddenSlot(Slot old) {
        super(old.container, old.getContainerSlot(), old.x, old.y);
        this.index = old.index;
        this.original = old;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public boolean isActive() {
        return false;
    }
}
