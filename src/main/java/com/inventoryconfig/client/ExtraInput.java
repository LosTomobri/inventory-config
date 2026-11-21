package com.inventoryconfig.client;

import com.inventoryconfig.ClientConfig;
import com.inventoryconfig.ExtraBar;
import com.inventoryconfig.ExtraSelectPayload;
import com.inventoryconfig.InventoryConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.List;

/**
 * Uso de las hotbars extra en juego: se mantiene la tecla de una barra y se rueda para moverse dentro de ella.
 * <ul>
 *   <li><b>Toque corto</b> (menos de {@link ClientConfig#HOLD_MS}): selecciona el slot normal de la hotbar original,
 *       pero al soltar (opcion A de la spec).</li>
 *   <li><b>Mantener</b>: activa la barra; la rueda mueve el resaltado (y no la hotbar normal); al soltar se confirma.</li>
 * </ul>
 * Para que vanilla no seleccione al apretar, cada tick se descartan los clicks de sus teclas 1..9 que coinciden con
 * la tecla de una barra extra. Una pulsacion mas corta que un tick no se pierde porque se toma del evento de teclado.
 */
@EventBusSubscriber(modid = InventoryConfig.MODID, value = Dist.CLIENT)
public final class ExtraInput {

    /** Numero (1..8) de la barra cuya tecla esta apretada; 0 = ninguna. */
    private static int downBar = 0;
    private static long downSince;
    /** La tecla se mantuvo lo suficiente (o se roda la rueda): la barra esta activa. */
    private static boolean active = false;
    /** Slot de Inventory resaltado en la barra activa. */
    private static int highlight = ExtraBar.UNASSIGNED;
    /** Ultimo slot confirmado en cada barra: ahi empieza el resaltado la proxima vez. */
    private static final EnumMap<ExtraBar.Position, Integer> LAST = new EnumMap<>(ExtraBar.Position.class);

    private ExtraInput() {
    }

    /** Numero de la barra activa (tecla mantenida), o 0. Lo usa el HUD. */
    static int activeBar() {
        return active ? downBar : 0;
    }

    /** Slot de Inventory resaltado en la barra activa. */
    static int highlightSlot() {
        return highlight;
    }

    private static boolean hotbarToolbarKeysDown(Minecraft mc) {
        return mc.options.keySaveHotbarActivator.isDown() || mc.options.keyLoadHotbarActivator.isDown();
    }

    // ---------------------------------------------------------------- teclado

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        int action = event.getAction();
        if (action == GLFW.GLFW_REPEAT) return;
        Minecraft mc = Minecraft.getInstance();

        if (action == GLFW.GLFW_RELEASE) {
            if (downBar > 0 && ExtraKeys.MAPPINGS[downBar - 1].matches(event.getKey(), event.getScanCode())) {
                release(mc);
            }
            return;
        }
        if (downBar > 0 || mc.screen != null || !ExtraView.enabled(mc) || hotbarToolbarKeysDown(mc)) return;
        for (ExtraView.View v : ExtraView.views()) {
            if (ExtraKeys.MAPPINGS[v.number() - 1].matches(event.getKey(), event.getScanCode())) {
                downBar = v.number();
                downSince = System.nanoTime();
                active = false;
                return;
            }
        }
        // Una tecla de la hotbar de vanilla que no es de ninguna barra extra: vanilla va a elegir ese slot, asi que
        // el intercambio se deshace (aunque sea el mismo slot, donde vanilla no manda nada al servidor).
        KeyMapping[] vanillaKeys = mc.options.keyHotbarSlots;
        for (int j = 0; j < vanillaKeys.length; j++) {
            if (vanillaKeys[j].matches(event.getKey(), event.getScanCode())) {
                // Un slot deshabilitado no se puede elegir (HotbarSkip descarta el click): no hay nada que deshacer.
                if (!HotbarSkip.blocked(mc, j)) ExtraMirror.requestRevert();
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        boolean enabled = ExtraView.enabled(mc);

        if (downBar > 0) {
            ExtraView.View v = ExtraView.view(downBar);
            if (mc.screen != null || !enabled || v == null) {
                reset(); // se abrio una pantalla, cambio la config, murio...: se cancela sin confirmar
            } else if (!active && (System.nanoTime() - downSince) / 1_000_000L >= ClientConfig.HOLD_MS.get()) {
                activate(v);
            }
        }

        // Vanilla elige el slot al apretar: se le quitan los clicks de las teclas que usan las barras extra.
        if (enabled && !hotbarToolbarKeysDown(mc)) {
            for (ExtraView.View v : ExtraView.views()) {
                KeyMapping extra = ExtraKeys.MAPPINGS[v.number() - 1];
                for (KeyMapping vanilla : mc.options.keyHotbarSlots) {
                    if (vanilla.getKey().equals(extra.getKey())) {
                        while (vanilla.consumeClick()) {
                            // descartado
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- rueda

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (downBar == 0) return;
        double delta = event.getScrollDeltaY();
        if (delta == 0) return;
        ExtraView.View v = ExtraView.view(downBar);
        if (v == null) return;
        if (!active) activate(v);
        // Igual que vanilla: rueda hacia arriba = hacia la izquierda / arriba.
        step(v, delta > 0 ? -1 : 1);
        event.setCanceled(true);
    }

    // ---------------------------------------------------------------- estado

    private static void activate(ExtraView.View v) {
        active = true;
        Integer last = LAST.get(v.position());
        highlight = last != null && v.slots().contains(last) ? last : v.slots().get(0);
    }

    private static void step(ExtraView.View v, int dir) {
        List<Integer> slots = v.slots();
        int idx = Math.max(0, slots.indexOf(highlight));
        highlight = slots.get(Math.floorMod(idx + dir, slots.size()));
    }

    private static void reset() {
        downBar = 0;
        active = false;
        highlight = ExtraBar.UNASSIGNED;
    }

    private static void release(Minecraft mc) {
        int bar = downBar;
        boolean wasActive = active;
        int slot = highlight;
        reset();
        ExtraView.View v = ExtraView.view(bar);
        if (v == null || mc.player == null || !ExtraView.enabled(mc)) return;
        if (wasActive) {
            LAST.put(v.position(), slot);
            onConfirm(v, slot);
        } else {
            tap(mc, bar);
        }
    }

    /** Toque corto: selecciona el slot de la hotbar original que usa esa misma tecla. */
    private static void tap(Minecraft mc, int bar) {
        KeyMapping extra = ExtraKeys.MAPPINGS[bar - 1];
        KeyMapping[] vanilla = mc.options.keyHotbarSlots;
        for (int j = 0; j < vanilla.length; j++) {
            if (vanilla[j].getKey().equals(extra.getKey())) {
                if (HotbarSkip.blocked(mc, j)) return; // slot deshabilitado: el toque no hace nada
                mc.player.getInventory().selected = j;
                ExtraMirror.requestRevert(); // un toque es elegir un slot normal: se deshace el intercambio
                return;
            }
        }
    }

    /**
     * Se soltó la tecla de una barra activa: {@code slot} (indice de Inventory) quedo elegido. El servidor hace el
     * intercambio (revirtiendo antes el que hubiera) y el estado vuelve en {@code ExtraSwapPayload}.
     */
    private static void onConfirm(ExtraView.View view, int slot) {
        PacketDistributor.sendToServer(new ExtraSelectPayload(slot));
    }
}
