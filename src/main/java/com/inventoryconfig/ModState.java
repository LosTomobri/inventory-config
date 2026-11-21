package com.inventoryconfig;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Estado conocido por el cliente (lo actualizan los paquetes del servidor). */
public final class ModState {
    /** Inventory Config activo para supervivencia / aventura. */
    public static volatile boolean survival = false;
    /** Inventory Config activo para creativo. */
    public static volatile boolean creative = false;
    /** Bit i = slot i del inventario deshabilitado (0-8 hotbar, 9-35 principal, 36-39 armadura, 40 offhand). */
    public static volatile long disabledMask = 0L;
    /** Hotbars extra e interruptor "Hide in Inventory" (global, viene del servidor). */
    public static volatile ExtraLayout extra = ExtraLayout.EMPTY;
    /** Reglas por slot (item fijo / exclusivo / no soltar). Global, viene del servidor. */
    public static volatile RulesLayout rules = RulesLayout.EMPTY;
    /** Casillas del menu radial. Global, viene del servidor. */
    public static volatile RadialLayout radial = RadialLayout.EMPTY;
    /** Ocultar crafteo / recetario del inventario. Global, viene del servidor. */
    public static volatile UiOptions ui = UiOptions.EMPTY;
    /** Intercambio con espejo activo (fase 6): slot de hotbar (0-8) y slot extra (9-35); -1 = ninguno. */
    public static volatile int swapHotbar = -1;
    public static volatile int swapExtra = -1;
    /** Perfiles guardados en el servidor: nombre -> perfil (layout completo). */
    public static volatile Map<String, Profile> profiles = new TreeMap<>();

    /**
     * Lo setea el cliente: se llama cuando llegan los perfiles. Con true abre la pantalla de configuracion;
     * con false refresca la que este abierta. En el servidor no hace nada.
     */
    public static volatile Consumer<Boolean> configHook = open -> {
    };

    /** Cuantos pixeles hay que subir un slot dentro de un menu. */
    @FunctionalInterface
    public interface SlotShift {
        /**
         * @param slot el slot original, con su posicion de vanilla (sin subir)
         * @return pixeles hacia arriba (0 = no moverlo)
         */
        int dy(AbstractContainerMenu menu, Slot slot);
    }

    /**
     * Lo setea el cliente: segun como quedo la pantalla que muestra ese menu (que tramos se quitaron),
     * dice cuanto sube cada slot. En el servidor queda siempre en 0 (alli las posiciones no importan).
     */
    public static volatile SlotShift slotShift = (menu, slot) -> 0;

    private ModState() {
    }
}
