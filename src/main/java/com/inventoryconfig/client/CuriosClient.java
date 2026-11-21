package com.inventoryconfig.client;

import com.inventoryconfig.CuriosEntry;
import com.inventoryconfig.CuriosSupport;
import com.inventoryconfig.InventoryConfig;
import com.inventoryconfig.curios.CuriosMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

/**
 * Lado cliente de los slots de Curios en el inventario: guarda el layout que mando el servidor y lo mantiene
 * aplicado al menu del inventario (ver {@link CuriosMenu}). Esta clase NO usa tipos de Curios (se carga siempre);
 * todo lo que los usa pasa por {@link CuriosMenu}, solo si Curios esta instalado.
 */
@EventBusSubscriber(modid = InventoryConfig.MODID, value = Dist.CLIENT)
public final class CuriosClient {

    /** Ultimo layout del servidor; null si todavia no mando ninguno en esta sesion. */
    private static volatile List<CuriosEntry> layout;

    static {
        // Se corre antes de que llegue el primer paquete (esta clase se carga con el mod).
        CuriosSupport.layoutHook = CuriosClient::onLayout;
    }

    private CuriosClient() {
    }

    private static void onLayout(List<CuriosEntry> entries) {
        layout = entries;
        apply();
    }

    private static void apply() {
        List<CuriosEntry> wanted = layout;
        Minecraft mc = Minecraft.getInstance();
        if (wanted == null || mc.player == null || !CuriosSupport.loaded()) return;
        // La pantalla del creativo reemplaza la lista de slots del menu mientras esta abierta: se espera a que se cierre.
        if (mc.screen instanceof CreativeModeInventoryScreen) return;
        CuriosMenu.reconcile(mc.player, wanted);
    }

    /** El inventario de Curios del cliente se sincroniza (y se puede reemplazar) despues: se va completando cada tick. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        apply();
    }

    /**
     * Cambio de dimension / reaparicion: el cliente crea un jugador nuevo con el menu de vanilla. El servidor puede
     * mandarle enseguida TODO el contenido de su menu (con los slots de Curios), asi que se arman ya mismo.
     */
    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        List<CuriosEntry> wanted = layout;
        if (wanted == null || !CuriosSupport.loaded()) return;
        CuriosMenu.reconcile(event.getNewPlayer(), wanted);
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        layout = null;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        layout = null;
    }

    /** Slot de Curios vacio: nombre de su tipo al pasar el mouse (los vacios no tienen tooltip en vanilla). */
    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || !CuriosSupport.loaded()) return;
        Slot hovered = screen.getSlotUnderMouse();
        if (hovered == null || hovered.hasItem() || !screen.getMenu().getCarried().isEmpty()) return;
        Component name = CuriosMenu.slotName(hovered);
        if (name != null) {
            Minecraft mc = Minecraft.getInstance();
            event.getGuiGraphics().renderTooltip(mc.font, name, event.getMouseX(), event.getMouseY());
        }
    }
}
