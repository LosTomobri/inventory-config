package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Regla de un slot del inventario ({@code Inventory} 0..40).
 * <ul>
 *   <li>{@code items}: el slot solo acepta alguno de esos items (lista vacia = sin restriccion de item). Se puede
 *       poner el que haya disponible: con varios items, cualquiera de ellos entra.</li>
 *   <li>{@code exclusive}: esos items no pueden estar en ningun slot cuya regla no los incluya (requiere items).</li>
 *   <li>{@code noDrop}: lo que haya en el slot no se puede tirar ni mover a un contenedor externo.</li>
 * </ul>
 */
public record SlotRule(List<RuleItem> items, boolean exclusive, boolean noDrop) {

    /** Maximo de items distintos en una regla. */
    public static final int MAX_ITEMS = 64;

    public static final SlotRule NONE = new SlotRule(List.of(), false, false);

    /** Copia inmutable, sin nulos ni repetidos y respetando el orden en que se eligieron. */
    public SlotRule {
        LinkedHashSet<RuleItem> set = new LinkedHashSet<>();
        if (items != null) {
            for (RuleItem item : items) {
                if (item != null && set.size() < MAX_ITEMS) set.add(item);
            }
        }
        items = List.copyOf(set);
    }

    public static final StreamCodec<ByteBuf, SlotRule> CODEC = StreamCodec.composite(
            RuleItem.CODEC.apply(ByteBufCodecs.list(MAX_ITEMS)), SlotRule::items,
            ByteBufCodecs.BOOL, SlotRule::exclusive,
            ByteBufCodecs.BOOL, SlotRule::noDrop,
            SlotRule::new);

    /** Sin items fijos y sin "no soltar": no hace falta guardarla. */
    public boolean isEmpty() {
        return items.isEmpty() && !noDrop;
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    /** El stack es uno de los items fijos del slot (mismo item y, si lo piden, los mismos datos extra). */
    public boolean accepts(ItemStack stack) {
        for (RuleItem item : items) {
            if (item.matches(stack)) return true;
        }
        return false;
    }

    /** Ranura de armadura que corresponde a un indice de Inventory (36 botas .. 39 casco), o null si no es de armadura. */
    public static EquipmentSlot armorSlot(int invSlot) {
        return switch (invSlot) {
            case 36 -> EquipmentSlot.FEET;
            case 37 -> EquipmentSlot.LEGS;
            case 38 -> EquipmentSlot.CHEST;
            case 39 -> EquipmentSlot.HEAD;
            default -> null;
        };
    }

    /** El item existe en el registro y sirve para ese slot (en slots de armadura, solo si se equipa en esa ranura). */
    public static boolean validItemFor(int invSlot, ResourceLocation id) {
        if (id == null) return false;
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty() || item.get() == net.minecraft.world.item.Items.AIR) return false;
        EquipmentSlot armor = armorSlot(invSlot);
        if (armor == null) return true;
        ItemStack stack = new ItemStack(item.get());
        EquipmentSlot slot = stack.getEquipmentSlot(); // NeoForge: ranura declarada por el item (puede ser null)
        if (slot == null) {
            Equipable eq = Equipable.get(stack); // armaduras vanilla, elytra, calabaza, cabezas...
            if (eq != null) slot = eq.getEquipmentSlot();
        }
        return slot == armor;
    }

    /** Version limpia: sin items invalidos, y "exclusive" apagado si no queda ningun item. */
    public SlotRule sanitized(int invSlot) {
        List<RuleItem> valid = items.stream().filter(it -> validItemFor(invSlot, it.id())).map(RuleItem::cleaned).toList();
        return new SlotRule(valid, exclusive && !valid.isEmpty(), noDrop);
    }
}
