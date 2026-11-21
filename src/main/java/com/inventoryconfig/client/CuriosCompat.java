package com.inventoryconfig.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.neoforged.fml.ModList;

/** Integracion opcional con Curios: sin el mod instalado no se toca nada. */
final class CuriosCompat {
    static final String MODID = "curios";

    private CuriosCompat() {
    }

    static boolean loaded() {
        return ModList.get().isLoaded(MODID);
    }

    /**
     * Si el widget es el boton de Curios del inventario. Se reconoce por el nombre de la clase (CuriosButton)
     * para no depender de la API de Curios en tiempo de compilacion.
     */
    static boolean isCuriosButton(Object widget) {
        return widget instanceof AbstractWidget && widget.getClass().getSimpleName().contains("CuriosButton");
    }
}
