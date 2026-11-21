package com.inventoryconfig.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Dibuja el fondo de una pantalla AJENA a la que se le quitaron tramos.
 * <p>
 * Vanilla ya dibujo el fondo con el alto achicado, o sea las primeras filas de la textura en su lugar:
 * la parte de arriba queda bien. Lo de abajo (hotbar + borde) se dibuja aca, volviendo a llamar a
 * su renderBg con el alto original, subido y recortado (scissor) para que solo aparezca ese pedazo.
 */
final class BandRenderer {
    private BandRenderer() {
    }

    /** Vuelve a encender el blend: los pixeles con alpha 0 de la textura no se tienen que ver. */
    static void enableBlend() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    static void drawRemaining(AbstractContainerScreen<?> screen, GuiGraphics g, CompactLayout layout,
                              int mouseX, int mouseY) {
        if (!ScreenHeight.isSupported()) return;

        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        int width = screen.getXSize();
        int collapsed = screen.getYSize();
        int full = layout.fullHeight();
        // Unas pantallas dibujan en topPos y otras en (alto de pantalla - imageHeight) / 2:
        // con las dos cosas puestas a la altura original, dibujan en el mismo lugar y se corrige con un translate.
        int fullTop = (screen.height - full) / 2;

        ScreenHeight.set(screen, full);
        ScreenHeight.setTop(screen, fullTop);
        // La ventana que queda: nada de lo que se dibuje aca puede salir de ahi.
        int bottomLimit = top + collapsed;
        try {
            for (CompactLayout.Segment s : layout.segments()) {
                // Se dibujan todos los tramos, tambien los que no se mueven: vanilla ya no dibujo el fondo principal
                // (ver ClientHandler#onRenderPre), asi no queda nada suyo debajo de los pixeles transparentes.
                g.enableScissor(left, top + s.start() - s.shift(), left + width,
                        Math.min(top + s.end() - s.shift(), bottomLimit));
                // enableScissor vacia el batch de render y eso APAGA el blend; sin blend los pixeles transparentes
                // de la textura (esquinas, fila extra del shulker) se dibujan opacos, con su RGB (blanco).
                enableBlend();
                g.pose().pushPose();
                g.pose().translate(0.0F, (float) (top - fullTop - s.shift()), 0.0F);
                ScreenHeight.renderBg(screen, g, mouseX, mouseY);
                g.pose().popPose();
                g.disableScissor();
            }
        } finally {
            ScreenHeight.set(screen, collapsed);
            ScreenHeight.setTop(screen, top);
        }
    }
}
