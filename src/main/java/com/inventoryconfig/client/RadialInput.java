package com.inventoryconfig.client;

import com.inventoryconfig.InventoryConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Abre el {@link RadialScreen} al apretar la tecla del Radial Menu (por defecto "<", configurable en Controls).
 * Abre en juego (sin otra pantalla) y con el jugador vivo, aunque no haya ninguna casilla asignada.
 * Soltar la tecla (modo HOLD) o el click (modo TOGGLE) lo maneja la propia pantalla.
 */
@EventBusSubscriber(modid = InventoryConfig.MODID, value = Dist.CLIENT)
public final class RadialInput {

    private RadialInput() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (!ExtraKeys.RADIAL.matches(event.getKey(), event.getScanCode())) return;
        open();
    }

    /** Por si el jugador asigna el radial a un boton del mouse. */
    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (!ExtraKeys.RADIAL.matchesMouse(event.getButton())) return;
        if (open()) event.setCanceled(true); // el click que abre no debe llegar a la pantalla nueva
    }

    private static boolean open() {
        Minecraft mc = Minecraft.getInstance();
        if (!RadialScreen.canOpen(mc)) return false;
        mc.setScreen(new RadialScreen());
        return true;
    }
}
