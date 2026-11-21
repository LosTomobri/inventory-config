package com.inventoryconfig.client;

import com.inventoryconfig.ExtraSelectPayload;
import com.inventoryconfig.InventoryConfig;
import com.inventoryconfig.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Fase 6, lado cliente: el espejo del intercambio.
 * <p>
 * El estado real (servidor) tiene el item extra en el slot S de la hotbar y el item de S en el slot extra E.
 * Para que la hotbar normal no cambie a la vista, mientras se dibuja la capa HOTBAR de vanilla se intercambian
 * S y E en el inventario del cliente (solo durante ese dibujo) y se devuelven al terminar. La barra extra hace
 * lo mismo al reves con {@link #shown}: en E dibuja el item extra, que en realidad esta en S. La mano en
 * primera / tercera persona usa el estado real, asi que muestra el item extra.
 */
@EventBusSubscriber(modid = InventoryConfig.MODID, value = Dist.CLIENT)
public final class ExtraMirror {

    /** Hay un intercambio puesto en el inventario del cliente solo para dibujar. */
    private static boolean swapped;
    private static int swapA;
    private static int swapB;

    private ExtraMirror() {
    }

    /** Si hay un intercambio activo (lo manda el servidor). */
    static boolean active() {
        int h = ModState.swapHotbar;
        int e = ModState.swapExtra;
        return h >= 0 && h <= 8 && e >= 9 && e <= 35;
    }

    /** Slot extra (9..35) que esta en mano, o -1. */
    static int heldExtra() {
        return active() ? ModState.swapExtra : -1;
    }

    /** Lo que hay que dibujar en el slot extra {@code slot}: en el slot en mano, el item extra (que esta en la hotbar). */
    static ItemStack shown(Inventory inv, int slot) {
        if (active() && slot == ModState.swapExtra) return inv.getItem(ModState.swapHotbar);
        return inv.getItem(slot);
    }

    /** Lo que hay que dibujar en el slot {@code slot} (0..8) de la hotbar original: en el slot en mano, el item que se cambio. */
    static ItemStack shownHotbar(Inventory inv, int slot) {
        if (active() && slot == ModState.swapHotbar) return inv.getItem(ModState.swapExtra);
        return inv.getItem(slot);
    }

    /** Le pide al servidor que deshaga el intercambio (si hay uno): se uso un slot normal de la hotbar. */
    static void requestRevert() {
        if (active()) PacketDistributor.sendToServer(new ExtraSelectPayload(ExtraSelectPayload.REVERT));
    }

    // ---------------------------------------------------------------- espejo de la hotbar de vanilla

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHotbarPre(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR) || !active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        swapA = ModState.swapHotbar;
        swapB = ModState.swapExtra;
        swapItems(mc.player.getInventory());
        swapped = true;
    }

    @SubscribeEvent
    public static void onHotbarPost(RenderGuiLayerEvent.Post event) {
        if (event.getName().equals(VanillaGuiLayers.HOTBAR)) restore();
    }

    /**
     * Devuelve el inventario a su estado real. Se llama tambien desde el HUD extra y cada tick, por si otro mod
     * cancelo la capa de la hotbar despues de que se la preparo (entonces no hay evento Post).
     */
    static void restore() {
        if (!swapped) return;
        swapped = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) swapItems(mc.player.getInventory());
    }

    private static void swapItems(Inventory inv) {
        ItemStack a = inv.getItem(swapA);
        ItemStack b = inv.getItem(swapB);
        inv.setItem(swapA, b);
        inv.setItem(swapB, a);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        restore();
    }

    // ---------------------------------------------------------------- reversiones que decide el cliente

    /** Abrir el inventario (o cualquier contenedor) deshace el intercambio. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof AbstractContainerScreen<?>) requestRevert();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        reset();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        swapped = false;
        ModState.swapHotbar = -1;
        ModState.swapExtra = -1;
    }
}
