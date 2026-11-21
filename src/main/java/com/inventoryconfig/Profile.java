package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Un perfil guardado: el layout completo (slots deshabilitados, hotbars extra, reglas de slot y radial).
 * Los modos Survival / Creative no forman parte del perfil.
 */
public record Profile(LayoutBundle bundle) {

    public static final StreamCodec<ByteBuf, Profile> CODEC = LayoutBundle.CODEC.map(Profile::new, Profile::bundle);

    public long mask() {
        return bundle.mask();
    }

    public ExtraLayout extra() {
        return bundle.extra();
    }

    public RulesLayout rules() {
        return bundle.rules();
    }

    public RadialLayout radial() {
        return bundle.radial();
    }

    public UiOptions ui() {
        return bundle.ui();
    }

    /** Perfil valido: todo limpiado contra su propia mascara. */
    public static Profile of(LayoutBundle bundle) {
        return new Profile(bundle.sanitized());
    }
}
