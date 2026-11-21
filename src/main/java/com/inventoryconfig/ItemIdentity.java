package com.inventoryconfig;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

/**
 * Datos extra que distinguen a un stack de otros del MISMO item. Hay mods donde todos los items comparten el id de
 * registro y lo que los hace distintos (el arma, el accesorio...) esta en los datos del stack: por ejemplo un
 * {@code GunId}. Eso vive en el componente {@code minecraft:custom_data}.
 * <p>
 * La "identidad" de un stack son las entradas de texto de su custom_data cuya clave termina en "id" (GunId, AmmoId,
 * AttachmentId...). Se ignoran las que no son identificadores (cantidad de municion, modo de disparo, UUID del dueno),
 * que cambian mientras se juega. Un filtro vacio coincide con cualquier stack del item.
 */
public final class ItemIdentity {

    private static final int MAX_ENTRIES = 8;
    private static final int MAX_KEY = 64;
    private static final int MAX_VALUE = 256;

    private ItemIdentity() {
    }

    private static boolean idKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.endsWith("id") && !k.contains("uuid");
    }

    /** Identidad del stack (vacia si no tiene datos que lo distingan). */
    public static CompoundTag of(ItemStack stack) {
        CompoundTag out = new CompoundTag();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return out;
        CompoundTag tag = data.copyTag();
        for (String key : tag.getAllKeys()) {
            if (out.size() >= MAX_ENTRIES) break;
            if (idKey(key) && tag.contains(key, Tag.TAG_STRING)) out.putString(key, tag.getString(key));
        }
        return out;
    }

    /** El stack cumple el filtro: tiene todas sus entradas con el mismo valor. Filtro vacio = siempre. */
    public static boolean matches(CompoundTag filter, ItemStack stack) {
        if (filter.isEmpty()) return true;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        CompoundTag tag = data.copyTag();
        for (String key : filter.getAllKeys()) {
            if (!tag.contains(key, Tag.TAG_STRING) || !tag.getString(key).equals(filter.getString(key))) return false;
        }
        return true;
    }

    /** Filtro valido: solo entradas de texto con tamano acotado (el servidor no confia en lo que manda el cliente). */
    public static CompoundTag clean(CompoundTag in) {
        CompoundTag out = new CompoundTag();
        if (in == null) return out;
        for (String key : in.getAllKeys()) {
            if (out.size() >= MAX_ENTRIES) break;
            if (key.length() > MAX_KEY || !in.contains(key, Tag.TAG_STRING)) continue;
            String value = in.getString(key);
            if (value.length() <= MAX_VALUE) out.putString(key, value);
        }
        return out;
    }
}
