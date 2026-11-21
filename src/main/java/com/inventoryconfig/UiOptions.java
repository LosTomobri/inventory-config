package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Opciones de la interfaz del inventario que viajan junto al layout: ocultar el crafteo (la grilla de 2x2, el
 * resultado y su rotulo) ocultar el recetario (el boton y el panel de recetas) y, si el mod Curios esta instalado, ocultar su boton y mostrar sus slots en el inventario. Valen para todos los jugadores
 * y solo cuando el mod esta activo para su modo de juego.
 */
public record UiOptions(boolean hideCrafting, boolean hideRecipes, boolean hideCuriosButton,
                        boolean showCuriosSlots) {

    public static final UiOptions EMPTY = new UiOptions(false, false, false, false);

    public static final StreamCodec<ByteBuf, UiOptions> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, UiOptions::hideCrafting,
            ByteBufCodecs.BOOL, UiOptions::hideRecipes,
            ByteBufCodecs.BOOL, UiOptions::hideCuriosButton,
            ByteBufCodecs.BOOL, UiOptions::showCuriosSlots,
            UiOptions::new);

    /** Guarda las dos opciones en {@code tag} (el mundo o un perfil). */
    public void save(CompoundTag tag) {
        tag.putBoolean("hideCrafting", hideCrafting);
        tag.putBoolean("hideRecipes", hideRecipes);
        tag.putBoolean("hideCuriosButton", hideCuriosButton);
        tag.putBoolean("showCuriosSlots", showCuriosSlots);
    }

    /** Lee las opciones de {@code tag}; lo que falta (mundos y perfiles viejos) queda apagado. */
    public static UiOptions load(CompoundTag tag) {
        return new UiOptions(tag.getBoolean("hideCrafting"), tag.getBoolean("hideRecipes"),
                tag.getBoolean("hideCuriosButton"), tag.getBoolean("showCuriosSlots"));
    }
}
