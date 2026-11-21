package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Un item de una regla de slot: el id de registro y, si hace falta, los datos que lo distinguen de otros stacks del
 * mismo item (ver {@link ItemIdentity}). Con {@code data} vacio vale cualquier stack de ese item.
 */
public record RuleItem(ResourceLocation id, CompoundTag data) {

    public static final StreamCodec<ByteBuf, RuleItem> CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, RuleItem::id,
            ByteBufCodecs.COMPOUND_TAG, RuleItem::data,
            RuleItem::new);

    public RuleItem {
        data = data == null ? new CompoundTag() : data;
    }

    public static RuleItem of(ResourceLocation id) {
        return new RuleItem(id, new CompoundTag());
    }

    /** El item "de ese tipo" de un stack: su id y su identidad. */
    public static RuleItem of(ItemStack stack) {
        return new RuleItem(BuiltInRegistries.ITEM.getKey(stack.getItem()), ItemIdentity.of(stack));
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id)
                && ItemIdentity.matches(data, stack);
    }

    /** Version con los datos limpios (ver {@link ItemIdentity#clean}). */
    public RuleItem cleaned() {
        return new RuleItem(id, ItemIdentity.clean(data));
    }

    /** Un stack para mostrar (con los datos aplicados, asi un arma se dibuja como esa arma); vacio si el id no existe. */
    public ItemStack displayStack() {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        if (!data.isEmpty()) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data.copy()));
        return stack;
    }
}
