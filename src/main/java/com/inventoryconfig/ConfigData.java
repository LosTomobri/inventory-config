package com.inventoryconfig;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Configuracion del mod, guardada por mundo y valida para TODOS los jugadores del servidor.
 * <p>
 * Los slots deshabilitados se guardan como una mascara de bits sobre los indices de {@code Inventory}:
 * 0-8 hotbar, 9-35 inventario principal, 36-39 armadura (36 botas ... 39 casco), 40 mano secundaria.
 * Por defecto no hay ninguno deshabilitado (inventario completo).
 */
public class ConfigData extends SavedData {
    private static final String NAME = InventoryConfig.MODID;

    /** Cantidad de slots del inventario del jugador (indices 0..40). */
    public static final int SLOT_COUNT = 41;
    /** Mascara con todos los slots. */
    public static final long ALL = (1L << SLOT_COUNT) - 1;
    /** Maximo de perfiles guardados y largo maximo del nombre. */
    public static final int MAX_PROFILES = 64;
    public static final int MAX_NAME = 32;

    /** Inventory Config activo para supervivencia / aventura. */
    public boolean survival = true;
    /** Inventory Config activo para creativo. */
    public boolean creative = false;
    /** Bit i = slot i deshabilitado. */
    public long disabledMask = 0L;
    /** Hotbars extra (maximo una por posicion), en orden de creacion. */
    public List<ExtraBar> extraBars = new ArrayList<>();
    /** "Hide in Inventory": los slots asignados a hotbars extra no se ven en el inventario (global). */
    public boolean hideAssignedSlots = false;
    /** Reglas por slot (item fijo / exclusivo / no soltar). */
    public RulesLayout rules = RulesLayout.EMPTY;
    /** Casillas del menu radial (global). */
    public RadialLayout radial = RadialLayout.EMPTY;
    /** Ocultar crafteo / recetario en el inventario (global). */
    public UiOptions ui = UiOptions.EMPTY;
    /** Perfiles: nombre -> layout completo (slots deshabilitados, hotbars extra, reglas y radial). */
    public final Map<String, Profile> profiles = new TreeMap<>();

    /** Las hotbars extra y el interruptor, tal como se mandan por red. */
    public ExtraLayout extra() {
        return new ExtraLayout(List.copyOf(extraBars), hideAssignedSlots);
    }

    /** Slots del inventario (9..35) asignados a alguna hotbar extra. */
    public long extraAssignedMask() {
        return new ExtraLayout(extraBars, hideAssignedSlots).assignedMask();
    }

    /** Slots del inventario (9..35) asignados a alguna casilla del menu radial. */
    public long radialAssignedMask() {
        return radial.assignedMask();
    }

    /** Slots que se ocultan en el inventario por "Hide in Inventory" (0 con el interruptor apagado). */
    public long extraHiddenMask() {
        return hiddenMask(extra(), radial, rules);
    }

    /**
     * "Hide in Inventory": con el interruptor encendido se ocultan los slots asignados a hotbars extra, los de las
     * casillas del radial y los que tienen items fijos en sus reglas. Lo usan igual el servidor y el cliente.
     */
    public static long hiddenMask(ExtraLayout extra, RadialLayout radial, RulesLayout rules) {
        if (!extra.hideAssigned()) return 0L;
        return (extra.assignedMask() | radial.assignedMask() | rules.itemSlotsMask()) & ALL;
    }

    /** El layout completo actual, tal como lo guarda un perfil. */
    public LayoutBundle bundle() {
        return new LayoutBundle(disabledMask, extra(), rules, radial, ui);
    }

    /** Aplica un layout ya validado (ver {@link LayoutBundle#sanitize}). No marca el dato como sucio. */
    public void setBundle(LayoutBundle b) {
        this.disabledMask = b.mask();
        setExtra(b.extra());
        this.rules = b.rules();
        this.radial = b.radial();
        this.ui = b.ui();
    }

    public void setExtra(ExtraLayout layout) {
        this.extraBars = new ArrayList<>(layout.bars());
        this.hideAssignedSlots = layout.hideAssigned();
    }

    public static ConfigData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ConfigData::new, ConfigData::load, null), NAME);
    }

    public static ConfigData load(CompoundTag tag, HolderLookup.Provider registries) {
        ConfigData data = new ConfigData();
        if (tag.contains("survival")) data.survival = tag.getBoolean("survival");
        if (tag.contains("creative")) data.creative = tag.getBoolean("creative");
        data.disabledMask = tag.getLong("disabledMask") & ALL;
        data.hideAssignedSlots = tag.getBoolean("hideAssignedSlots");
        List<ExtraBar> bars = ExtraLayout.barsFromTag(tag);
        data.setExtra(ExtraLayout.sanitize(bars, data.hideAssignedSlots, data.disabledMask));
        // Mundos viejos: si no existen "slotRules" / "radial" quedan vacios.
        data.rules = RulesLayout.sanitize(RulesLayout.fromTag(tag, "slotRules"), data.disabledMask);
        data.radial = RadialLayout.sanitize(tag.contains("radial", Tag.TAG_INT_ARRAY)
                ? RadialLayout.fromArray(tag.getIntArray("radial")) : RadialLayout.EMPTY, data.disabledMask);
        data.ui = UiOptions.load(tag); // mundos viejos: sin las claves queda todo apagado
        CompoundTag profiles = tag.getCompound("profiles");
        for (String key : profiles.getAllKeys()) {
            if (profiles.contains(key, Tag.TAG_COMPOUND)) {
                CompoundTag p = profiles.getCompound(key);
                long mask = p.getLong("mask") & ALL;
                ExtraLayout layout = new ExtraLayout(ExtraLayout.barsFromTag(p), p.getBoolean("hide"));
                RulesLayout rules = RulesLayout.fromTag(p, "slotRules");
                RadialLayout radial = p.contains("radial", Tag.TAG_INT_ARRAY)
                        ? RadialLayout.fromArray(p.getIntArray("radial")) : RadialLayout.EMPTY;
                data.profiles.put(key, Profile.of(new LayoutBundle(mask, layout, rules, radial, UiOptions.load(p))));
            } else {
                // Formato viejo: solo la mascara. Se carga sin hotbars extra.
                data.profiles.put(key, Profile.of(new LayoutBundle(profiles.getLong(key) & ALL,
                        ExtraLayout.EMPTY, RulesLayout.EMPTY, RadialLayout.EMPTY, UiOptions.EMPTY)));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("survival", survival);
        tag.putBoolean("creative", creative);
        tag.putLong("disabledMask", disabledMask);
        tag.putBoolean("hideAssignedSlots", hideAssignedSlots);
        tag.put("extraBars", ExtraLayout.barsToTag(extraBars));
        tag.put("slotRules", rules.toTag());
        tag.putIntArray("radial", radial.toArray());
        ui.save(tag);
        CompoundTag profiles = new CompoundTag();
        this.profiles.forEach((name, profile) -> {
            CompoundTag p = new CompoundTag();
            p.putLong("mask", profile.mask());
            p.putBoolean("hide", profile.extra().hideAssigned());
            p.put("extraBars", ExtraLayout.barsToTag(profile.extra().bars()));
            p.put("slotRules", profile.rules().toTag());
            p.putIntArray("radial", profile.radial().toArray());
            profile.ui().save(p);
            profiles.put(name, p);
        });
        tag.put("profiles", profiles);
        return tag;
    }
}
