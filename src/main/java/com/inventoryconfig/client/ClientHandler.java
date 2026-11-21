package com.inventoryconfig.client;

import com.inventoryconfig.HiddenSlot;
import com.inventoryconfig.InventoryConfig;
import com.inventoryconfig.InventoryLock;
import com.inventoryconfig.ModState;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquesta la interfaz. El flujo para cada pantalla de contenedor es siempre el mismo:
 * <ol>
 *   <li>se apagan los slots que corresponda (en {@link InventoryLock});</li>
 *   <li>{@link LayoutAnalyzer} mira que slots quedaron apagados y decide que tramos se pueden quitar;</li>
 *   <li>si la pantalla esta anotada en {@link ScreenAdapters}, se achica y se sube lo de abajo;</li>
 *   <li>lo que no se pudo quitar (slots sueltos, pantallas no anotadas) se tapa con el color del panel.</li>
 * </ol>
 */
@EventBusSubscriber(modid = InventoryConfig.MODID, value = Dist.CLIENT)
public class ClientHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Pantallas ya reportadas en el log (una sola vez por clase). */
    private static final Set<Class<?>> REPORTED = ConcurrentHashMap.newKeySet();

    /** Pantalla a la que se le puso imageHeight = 0 para que vanilla no dibuje el fondo principal, y su alto real. */
    private static AbstractContainerScreen<?> blanked;
    private static int blankedHeight;

    /** GuiGraphics en el que se activo el recorte del fondo de vanilla (null si no hay ninguno activo). */
    private static GuiGraphics clipGraphics;

    /** Layout de las pantallas AJENAS achicadas (las propias lo guardan ellas mismas). */
    private static final Map<AbstractContainerScreen<?>, CompactLayout> LAYOUTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Pantallas achicadas por el modo automatico (se dibujan desde la textura, no por imageHeight). */
    private static final Map<AbstractContainerScreen<?>, TextureBands.Result> TEXTURED =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Posicion original (la que calcula vanilla) de los botones del recetario. */
    private static final Map<AbstractWidget, Integer> BASE_Y =
            Collections.synchronizedMap(new WeakHashMap<>());

    static {
        // Se corre antes de abrir cualquier pantalla (la primera es el menu principal).
        ModState.slotShift = ClientHandler::slotShift;
        ModState.configHook = ConfigScreen::onProfiles;
    }

    /** Ultima config vista (survival, creative, mascara) para detectar cambios; null hasta el primer tick. */
    private static long[] lastConfig;

    /**
     * Si la config cambia con un contenedor abierto, se cierra: su layout (que tramos se quitaron) se calculo
     * con la config anterior y quedaria mal. Al volver a abrirlo se arma con la nueva.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // (los slots asignados a barras, radial o reglas tambien cuentan cuando "Hide in Inventory" los oculta)
        long extraHidden = com.inventoryconfig.ConfigData.hiddenMask(ModState.extra, ModState.radial, ModState.rules);
        long[] now = {ModState.survival ? 1 : 0, ModState.creative ? 1 : 0, ModState.disabledMask, extraHidden,
                ModState.ui.hideCrafting() ? 1 : 0, ModState.ui.hideRecipes() ? 1 : 0, ModState.ui.hideCuriosButton() ? 1 : 0,
                ModState.ui.showCuriosSlots() ? 1 : 0};
        long[] before = lastConfig;
        lastConfig = now;
        if (before == null || java.util.Arrays.equals(before, now)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen instanceof AbstractContainerScreen<?>) {
            mc.player.closeContainer();
        }
    }

    // ---------------------------------------------------------------- estado

    static CompactLayout layoutOf(AbstractContainerScreen<?> screen) {
        if (screen instanceof CompactScreen own) return own.layout();
        CompactLayout layout = LAYOUTS.get(screen);
        return layout != null ? layout : CompactLayout.NONE;
    }

    /** Lo que usa InventoryLock: cuanto sube un slot segun la pantalla que muestra ese menu. */
    private static int slotShift(AbstractContainerMenu menu, Slot slot) {
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen
                && screen.getMenu() == menu) {
            return layoutOf(screen).shiftAt(slot.y - 1);
        }
        return 0;
    }

    // ---------------------------------------------------------------- eventos

    /** Cambia el inventario normal y los cofres por su version propia. */
    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !InventoryLock.isActive(mc.player)) return;

        Screen replaced = ScreenAdapters.replace(event.getNewScreen(), mc.player);
        if (replaced != null) event.setNewScreen(replaced);
    }

    /**
     * Antes de armar la pantalla: apaga los slots, decide que tramos se quitan, achica el alto
     * y recien despues sube los slots de abajo (cuando ya se sabe como quedo la pantalla).
     */
    @SubscribeEvent
    public static void onInitPre(ScreenEvent.Init.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        InventoryLock.applySlots(mc.player);
        if (InventoryLock.isActive(mc.player)) {
            prepare(screen);
        }
        InventoryLock.applySlots(mc.player);
    }

    private static void prepare(AbstractContainerScreen<?> screen) {
        if (screen instanceof CompactScreen own) {
            own.applyLayout(LayoutAnalyzer.analyze(screen, own.fullHeight()));
            return;
        }
        if (LAYOUTS.containsKey(screen)) return; // ya achicada: no se vuelve a analizar

        boolean annotated = ScreenAdapters.usesImageHeight(screen) && ScreenHeight.isSupported();
        int full = annotated ? ScreenHeight.get(screen) : screen.getYSize();
        List<String> notes = new ArrayList<>();
        CompactLayout layout = LayoutAnalyzer.analyze(screen, full, notes);
        if (layout.isEmpty()) {
            report(screen, "sin cambios", notes);
            return;
        }

        if (annotated) {
            // Anotada: el fondo se dibuja con imageHeight, asi que achicarlo achica la ventana (y se recentra).
            LAYOUTS.put(screen, layout);
            ScreenHeight.set(screen, layout.collapsedHeight());
            report(screen, "achicada (anotada, por imageHeight)", notes);
            return;
        }
        if (ScreenAdapters.autoAllowed(screen)) {
            // No anotada: se analiza la textura de fondo. La ventana no se mueve ni cambia de alto.
            TextureBands.Result texture = TextureBands.probe(screen, full, notes);
            if (texture != null && texture.fullHeight() > 0 && texture.fullHeight() != full) {
                // textura estilo cofre: la ventana que dibuja mide otra cosa que imageHeight, se vuelve a analizar
                layout = LayoutAnalyzer.analyze(screen, texture.fullHeight(), notes);
            }
            if (texture != null && !layout.isEmpty()) {
                LAYOUTS.put(screen, layout);
                TEXTURED.put(screen, texture);
                report(screen, "achicada (automatico, por textura " + texture.texture() + ")", notes);
                return;
            }
        }
        report(screen, "solo se tapan los slots apagados", notes);
    }

    /**
     * Deja en el log, una vez por clase de pantalla, que se hizo y por que: sirve para ver al toque que le pasa
     * a la pantalla de un mod. Para anotarla a mano: ScreenAdapters.registerImageHeightByName("clase").
     */
    private static void report(AbstractContainerScreen<?> screen, String result, List<String> notes) {
        if (!REPORTED.add(screen.getClass())) return;
        Minecraft mc = Minecraft.getInstance();
        Inventory inv = mc.player != null ? mc.player.getInventory() : null;
        int total = 0;
        int own = 0;
        int hidden = 0;
        for (Slot slot : screen.getMenu().slots) {
            total++;
            if (slot.container == inv) own++;
            if (slot instanceof HiddenSlot) hidden++;
        }
        LOGGER.info("Inventory Config: {} -> {} | slots: {} (del inventario del jugador: {}, apagados: {}){}",
                screen.getClass().getName(), result, total, own, hidden,
                notes.isEmpty() ? "" : " | " + String.join(" ; ", notes));
    }

    /** Los botones que un mod haya puesto debajo de un tramo quitado suben junto con el resto. */
    @SubscribeEvent
    public static void onInitPost(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        CompactLayout layout = layoutOf(screen);
        if (layout.isEmpty()) return;

        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                int shift = layout.shiftAt(widget.getY() - screen.getGuiTop());
                if (shift > 0) widget.setY(widget.getY() - shift);
            }
        }
    }

    /**
     * "Hide Curios Button": Curios agrega su boton en su propio Init.Post, asi que esto corre al final (LOWEST)
     * para sacarlo despues de que lo agregue. Solo con Curios instalado y el mod activo para el jugador.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInitPostCurios(ScreenEvent.Init.Post event) {
        if (!ModState.ui.hideCuriosButton() || !CuriosCompat.loaded()) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !InventoryLock.isActive(mc.player)) return;
        for (GuiEventListener child : List.copyOf(event.getListenersList())) {
            if (CuriosCompat.isCuriosButton(child)) event.removeListener(child);
        }
    }

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        restoreHeight();
        CompactLayout layout = layoutOf(screen);
        // Solo si la ventana se recentro (achicada por imageHeight): en el modo automatico no se mueve.
        if (!layout.isEmpty() && !TEXTURED.containsKey(screen)) alignRecipeButtons(screen, layout);

        // Pantalla ajena achicada por imageHeight: vanilla dibujaria las filas de abajo de la textura (hotbar de
        // arriba de todo, slots) y se asomarian por los pixeles transparentes de la parte que subimos (esquinas
        // y fila extra del shulker). Con imageHeight = 0 su blit del fondo no dibuja nada; el fondo completo se
        // arma en onBackground, tramo por tramo. Se restaura ahi mismo, antes de dibujar widgets y slots.
        if (!layout.isEmpty() && !(screen instanceof CompactScreen) && !TEXTURED.containsKey(screen)
                && ScreenHeight.isSupported()) {
            blanked = screen;
            blankedHeight = ScreenHeight.get(screen);
            ScreenHeight.set(screen, 0);
        }
    }

    private static void restoreHeight() {
        if (blanked != null) {
            ScreenHeight.set(blanked, blankedHeight);
            blanked = null;
        }
    }

    /**
     * El boton del recetario viene en height/2 + algo (pensado para la ventana completa).
     * Al achicar la ventana, el centro baja la mitad de lo que se quito, asi que el boton baja lo mismo.
     */
    private static void alignRecipeButtons(AbstractContainerScreen<?> screen, CompactLayout layout) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof ImageButton button && button.getWidth() == 20 && button.getHeight() == 18) {
                int base = BASE_Y.computeIfAbsent(button, AbstractWidget::getY);
                button.setY(base + layout.totalHeight() / 2);
            }
        }
    }

    /**
     * Se dispara despues del oscurecido/desenfoque y justo ANTES de que vanilla dibuje el fondo de la pantalla.
     * <p>
     * Vanilla dibuja la ventana con el alto ya achicado, o sea que llega a pintar filas de textura que despues
     * se tapan con la parte de abajo (hotbar + borde). Pero esa parte tiene pixeles transparentes (las esquinas
     * y, en el shulker, la fila extra del final) por donde se asomaban esos restos: muescas en las esquinas y
     * "dientes" de slots debajo del borde. Para que vanilla dibuje solo lo que queda arriba del primer tramo
     * quitado, se recorta con un scissor hasta ese punto. {@link #onBackground} lo saca apenas vanilla termina.
     */
    @SubscribeEvent
    public static void onBackgroundRendered(ScreenEvent.BackgroundRendered event) {
        releaseClip();
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (screen instanceof CompactScreen) return; // las pantallas propias se dibujan solas
        CompactLayout layout = layoutOf(screen);
        if (layout.isEmpty()) return;

        GuiGraphics g = event.getGuiGraphics();
        g.enableScissor(0, 0, screen.width, screen.getGuiTop() + layout.firstTop());
        BandRenderer.enableBlend(); // el scissor vacia el batch y apaga el blend: vanilla dibujaria las esquinas opacas
        clipGraphics = g;
    }

    private static void releaseClip() {
        if (clipGraphics != null) {
            clipGraphics.disableScissor();
            clipGraphics = null;
        }
    }

    /** Red de seguridad: si por algo no se llego a onBackground, el recorte no puede quedar puesto. */
    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        releaseClip();
        restoreHeight();
    }

    /**
     * - Pantallas ajenas achicadas: dibuja la parte de abajo del fondo (hotbar + borde), subida.
     * - Cualquier pantalla: tapa con el color del panel los slots apagados que no se quitaron.
     * Va con prioridad maxima para sacar el recorte antes que cualquier otro que dibuje en este evento.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBackground(ContainerScreenEvent.Render.Background event) {
        releaseClip();
        restoreHeight(); // antes de dibujar: BandRenderer necesita el alto real
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        GuiGraphics g = event.getGuiGraphics();
        CompactLayout layout = layoutOf(screen);
        BandRenderer.enableBlend();

        if (!layout.isEmpty() && !(screen instanceof CompactScreen)) {
            TextureBands.Result texture = TEXTURED.get(screen);
            if (texture != null) {
                TextureBands.drawRemaining(screen, g, layout, texture);
            } else {
                BandRenderer.drawRemaining(screen, g, layout, event.getMouseX(), event.getMouseY());
            }
        }
        maskHiddenSlots(screen, g, layout);
        CuriosFrames.draw(screen, g); // marcos de los slots de Curios del inventario (si los hay)
    }

    private static void maskHiddenSlots(AbstractContainerScreen<?> screen, GuiGraphics g, CompactLayout layout) {
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        int color = 0;
        boolean colorKnown = false;

        for (Slot slot : screen.getMenu().slots) {
            if (!(slot instanceof HiddenSlot)) continue;
            int cellTop = slot.y - 1;
            if (layout.inBand(cellTop)) continue; // ese tramo ya no se dibuja
            if (!colorKnown) {
                color = PanelColors.of(screen);
                colorKnown = true;
            }
            int x = left + slot.x - 1;
            int y = top + cellTop - layout.shiftAt(cellTop);
            g.fill(x, y, x + 18, y + 18, color);
        }
    }
}
