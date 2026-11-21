package com.inventoryconfig;

import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Copia de un slot, subida {@code dy} pixeles (cuando la pantalla se achica y lo de abajo sube; con {@code dy = 0} no
 * se mueve) y con las reglas de slot (item fijo / exclusivo / no soltar) aplicadas.
 * Las reglas del slot original (que acepta, que deja sacar, si esta activo) se delegan en el, asi que
 * tambien sirve para slots de mods que sean subclases de {@link Slot} sobre el inventario del jugador.
 */
public class ShiftedSlot extends Slot {
    public final Slot original;
    public final int dy;
    /** Dueno del inventario (los slots que se envuelven son siempre del inventario del jugador). */
    private final Player owner;

    public ShiftedSlot(Slot old, int dy) {
        super(old.container, old.getContainerSlot(), old.x, old.y - dy);
        this.index = old.index;
        this.original = old;
        this.dy = dy;
        this.owner = old.container instanceof Inventory inv ? inv.player : null;
    }

    /** Item fijo y exclusivo, mirando la regla del slot (con el cruce del intercambio de ExtraSwap, si lo hay). */
    @Override
    public boolean mayPlace(ItemStack stack) {
        if (!original.mayPlace(stack)) return false;
        return owner == null || SlotRules.ctx(owner).allows(getContainerSlot(), stack);
    }

    /**
     * \"No soltar\": con un contenedor externo abierto (cualquier menu que no sea el inventario del jugador) el slot
     * no entrega su item, asi no se puede mover al cofre con click, shift-click, doble click ni arrastre.
     * Dentro del inventario del jugador se puede mover con normalidad.
     */
    @Override
    public boolean mayPickup(Player player) {
        if (!original.mayPickup(player)) return false;
        if (owner == null || player.containerMenu == player.inventoryMenu) return true;
        return !SlotRules.ctx(owner).noDrop(getContainerSlot());
    }

    @Override
    public boolean isActive() {
        return original.isActive();
    }

    @Override
    public int getMaxStackSize() {
        return original.getMaxStackSize();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return original.getMaxStackSize(stack);
    }

    /** Los slots de armadura avisan del equipamiento al ponerles algo (sonido, eventos): se delega. */
    @Override
    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        original.setByPlayer(newStack, oldStack);
    }

    /** Sin esto un slot de armadura / mano secundaria envuelto perderia su icono de \"vacio\". */
    @Override
    public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        return original.getNoItemIcon();
    }
}
