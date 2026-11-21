package com.inventoryconfig.client;

/**
 * Pantallas propias del mod (inventario, cofre): se achican a partir del {@link CompactLayout}
 * que calcula {@link LayoutAnalyzer}, y se dibujan solas.
 */
interface CompactScreen {
    /** Alto de la ventana sin achicar. */
    int fullHeight();

    CompactLayout layout();

    /** Guarda el layout y ajusta el alto de la ventana. */
    void applyLayout(CompactLayout layout);
}
