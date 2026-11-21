package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reglas por slot de Inventory (0..40). Solo se guardan los slots que tienen alguna regla. */
public record RulesLayout(Map<Integer, SlotRule> bySlot) {

    public static final RulesLayout EMPTY = new RulesLayout(Map.of());

    public static final StreamCodec<ByteBuf, RulesLayout> CODEC =
            ByteBufCodecs.<ByteBuf, Integer, SlotRule, Map<Integer, SlotRule>>map(
                            size -> new TreeMap<>(), ByteBufCodecs.VAR_INT, SlotRule.CODEC, ConfigData.SLOT_COUNT)
                    .map(RulesLayout::new, RulesLayout::bySlot);

    public SlotRule get(int slot) {
        return bySlot.getOrDefault(slot, SlotRule.NONE);
    }

    /** Mascara de bits con los slots que tienen "no soltar". */
    public long noDropMask() {
        long m = 0L;
        for (Map.Entry<Integer, SlotRule> e : bySlot.entrySet()) {
            if (e.getValue().noDrop() && inRange(e.getKey())) m |= 1L << e.getKey();
        }
        return m;
    }

    /** Mascara de bits con los slots que tienen items asignados (los que oculta "Hide in Inventory"). */
    public long itemSlotsMask() {
        long m = 0L;
        for (Map.Entry<Integer, SlotRule> e : bySlot.entrySet()) {
            if (e.getValue().hasItems() && inRange(e.getKey())) m |= 1L << e.getKey();
        }
        return m;
    }

    private static boolean inRange(int slot) {
        return slot >= 0 && slot < ConfigData.SLOT_COUNT;
    }

    // ---------------------------------------------------------------- NBT

    public ListTag toTag() {
        ListTag out = new ListTag();
        for (Map.Entry<Integer, SlotRule> e : bySlot.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putInt("slot", e.getKey());
            ListTag items = new ListTag();
            for (RuleItem item : e.getValue().items()) {
                CompoundTag it = new CompoundTag();
                it.putString("id", item.id().toString());
                if (!item.data().isEmpty()) it.put("data", item.data().copy());
                items.add(it);
            }
            t.put("entries", items);
            t.putBoolean("exclusive", e.getValue().exclusive());
            t.putBoolean("noDrop", e.getValue().noDrop());
            out.add(t);
        }
        return out;
    }

    /** Sin validar: despues se llama a {@link #sanitize}. Si la clave no existe (mundo viejo) devuelve EMPTY. */
    public static RulesLayout fromTag(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) return EMPTY;
        Map<Integer, SlotRule> map = new TreeMap<>();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            List<RuleItem> items = new ArrayList<>();
            if (t.contains("entries", Tag.TAG_LIST)) {
                ListTag entries = t.getList("entries", Tag.TAG_COMPOUND);
                for (int j = 0; j < entries.size(); j++) {
                    CompoundTag it = entries.getCompound(j);
                    ResourceLocation id = ResourceLocation.tryParse(it.getString("id"));
                    if (id != null) items.add(new RuleItem(id, it.getCompound("data")));
                }
            } else if (t.contains("items", Tag.TAG_LIST)) {
                // Formato intermedio: lista de ids.
                ListTag names = t.getList("items", Tag.TAG_STRING);
                for (int j = 0; j < names.size(); j++) {
                    ResourceLocation id = ResourceLocation.tryParse(names.getString(j));
                    if (id != null) items.add(RuleItem.of(id));
                }
            } else if (t.contains("item", Tag.TAG_STRING)) {
                // Formato viejo: un solo item.
                ResourceLocation id = ResourceLocation.tryParse(t.getString("item"));
                if (id != null) items.add(RuleItem.of(id));
            }
            map.put(t.getInt("slot"), new SlotRule(items, t.getBoolean("exclusive"), t.getBoolean("noDrop")));
        }
        return new RulesLayout(map);
    }

    // ---------------------------------------------------------------- sanitize

    /**
     * Deja las reglas validas (el servidor lo aplica siempre): descarta slots fuera de 0..40 o deshabilitados,
     * items que no existen o que no sirven para esa ranura de armadura, apaga "exclusive" sin item y quita las
     * reglas que quedan vacias.
     */
    public static RulesLayout sanitize(RulesLayout rules, long disabledMask) {
        if (rules == null) return EMPTY;
        Map<Integer, SlotRule> out = new TreeMap<>();
        for (Map.Entry<Integer, SlotRule> e : rules.bySlot().entrySet()) {
            Integer slot = e.getKey();
            SlotRule rule = e.getValue();
            if (slot == null || rule == null || !inRange(slot)) continue;
            if (((disabledMask >> slot) & 1L) != 0L) continue;
            SlotRule clean = rule.sanitized(slot);
            if (!clean.isEmpty()) out.put(slot, clean);
        }
        return new RulesLayout(Collections.unmodifiableMap(out));
    }
}
