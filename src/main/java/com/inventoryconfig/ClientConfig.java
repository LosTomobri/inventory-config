package com.inventoryconfig;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Opciones por jugador (archivo de config del cliente). */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    /** Cuanto hay que mantener la tecla de una hotbar extra para activarla; menos que esto es un toque normal. */
    public static final ModConfigSpec.IntValue HOLD_MS;

    /** Radial Menu: HOLD = abre al apretar y elige al soltar; TOGGLE = abre con la tecla y se elige con un click. */
    public enum RadialMode { HOLD, TOGGLE }

    public static final ModConfigSpec.EnumValue<RadialMode> RADIAL_MODE;
    /** Radio (en pixeles) de la zona muerta del centro del radial: dentro no hay seleccion. */
    public static final ModConfigSpec.IntValue RADIAL_DEAD_ZONE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        HOLD_MS = b.comment("Milliseconds a number key must be held to open its extra hotbar (a shorter press selects the normal hotbar slot).")
                .defineInRange("holdThresholdMs", 250, 50, 2000);
        RADIAL_MODE = b.comment("Radial menu: HOLD opens while the key is held and picks on release; TOGGLE opens with a key press and picks with a click.")
                .defineEnum("radialMode", RadialMode.HOLD);
        RADIAL_DEAD_ZONE = b.comment("Radius in pixels of the radial menu's centre dead zone (no selection inside it).")
                .defineInRange("radialDeadZone", 24, 0, 100);
        SPEC = b.build();
    }

    private ClientConfig() {
    }
}
