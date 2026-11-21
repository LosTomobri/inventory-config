package com.inventoryconfig.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Que pantallas sabe achicar el mod. Es el UNICO lugar donde hay que anotar una pantalla nueva
 * (vanilla o de otro mod): que tramos se quitan lo decide {@link LayoutAnalyzer} solo.
 * <p>
 * Una pantalla que no esta anotada no queda sin probar: el modo automatico ({@link TextureBands}) la analiza al
 * abrirla y, si su fondo es una textura que coincide con sus slots, la achica solo. Si tampoco eso sirve, sus
 * slots apagados se tapan con el color del panel y la ventana queda del tamaño original.
 * <p>
 * Hay dos formas de anotar una pantalla:
 * <ul>
 *   <li>{@link #registerImageHeight}: la pantalla dibuja su fondo con {@code blit(textura, x, y, 0, 0, imageWidth, imageHeight)},
 *       o sea, achicar imageHeight achica el fondo (hornos, tolva, dispensador, mesa de crafteo, Curios...).
 *       Es lo mas simple y alcanza para casi todas las pantallas "estilo vanilla".</li>
 *   <li>{@link #registerReplacement}: la pantalla dibuja de otra forma (como el cofre), asi que se cambia por una
 *       subclase propia que implemente {@link CompactScreen}.</li>
 * </ul>
 */
public final class ScreenAdapters {

    /** Cambia una pantalla por otra propia. Devuelve null si no corresponde. */
    @FunctionalInterface
    public interface Replacement {
        Screen apply(Screen screen, Player player);
    }

    private static final List<Replacement> REPLACEMENTS = new CopyOnWriteArrayList<>();
    private static final List<Predicate<AbstractContainerScreen<?>>> IMAGE_HEIGHT = new CopyOnWriteArrayList<>();
    private static final List<Predicate<AbstractContainerScreen<?>>> EXCLUDED = new CopyOnWriteArrayList<>();

    /**
     * Modo automatico: las pantallas que no estan anotadas se analizan al abrirse y, si su fondo es una textura
     * que coincide con sus slots, se achican igual (ver {@link TextureBands}). Poner en false para volver a
     * achicar solo las anotadas.
     */
    public static volatile boolean autoDetect = true;

    static {
        // Pantallas propias (solo las exactas de vanilla, no subclases de otros mods).
        registerReplacement((screen, player) ->
                screen.getClass() == InventoryScreen.class ? new CompactInventoryScreen(player) : null);
        registerReplacement((screen, player) ->
                screen.getClass() == ContainerScreen.class
                        ? new CompactChestScreen(((ContainerScreen) screen).getMenu(), player.getInventory(), screen.getTitle())
                        : null);

        // Pantallas que dibujan el fondo con imageHeight.
        registerImageHeight(DispenserScreen.class,      // dispensador y soltador
                HopperScreen.class,
                ShulkerBoxScreen.class,
                AbstractFurnaceScreen.class,            // horno, alto horno, ahumador
                BrewingStandScreen.class,
                CraftingScreen.class,
                StonecutterScreen.class,
                CartographyTableScreen.class,
                SmithingScreen.class,                   // mesa de herreria
                GrindstoneScreen.class,                 // afiladora
                LoomScreen.class,
                AnvilScreen.class,
                EnchantmentScreen.class,
                BeaconScreen.class);                    // baliza: imageHeight 219, el renglon quitado empieza en 136
        // Pantallas que el modo automatico no debe tocar.
        registerExcluded(CreativeModeInventoryScreen.class);

        // Mods: por nombre de clase, sin depender de ellos.
        registerImageHeightByName("top.theillusivec4.curios.");
    }

    private ScreenAdapters() {
    }

    public static void registerReplacement(Replacement replacement) {
        REPLACEMENTS.add(replacement);
    }

    /** La pantalla (o una subclase) dibuja el fondo con imageHeight. */
    public static void registerImageHeight(Class<?>... screens) {
        for (Class<?> c : screens) {
            IMAGE_HEIGHT.add(c::isInstance);
        }
    }

    /** Igual, pero por nombre de clase (prefijo de paquete o nombre completo), para mods sin dependencia de compilacion. */
    public static void registerImageHeightByName(String classNamePrefix) {
        IMAGE_HEIGHT.add(screen -> screen.getClass().getName().startsWith(classNamePrefix));
    }

    /** El modo automatico no toca esta pantalla (ni sus subclases). */
    public static void registerExcluded(Class<?>... screens) {
        for (Class<?> c : screens) {
            EXCLUDED.add(c::isInstance);
        }
    }

    /** Igual, por nombre de clase (prefijo de paquete o nombre completo). */
    public static void registerExcludedByName(String classNamePrefix) {
        EXCLUDED.add(screen -> screen.getClass().getName().startsWith(classNamePrefix));
    }

    /** Si el modo automatico puede probar con esta pantalla. */
    static boolean autoAllowed(AbstractContainerScreen<?> screen) {
        if (!autoDetect) return false;
        for (Predicate<AbstractContainerScreen<?>> p : EXCLUDED) {
            if (p.test(screen)) return false;
        }
        return true;
    }

    static Screen replace(Screen screen, Player player) {
        for (Replacement r : REPLACEMENTS) {
            Screen replaced = r.apply(screen, player);
            if (replaced != null) return replaced;
        }
        return null;
    }

    static boolean usesImageHeight(AbstractContainerScreen<?> screen) {
        for (Predicate<AbstractContainerScreen<?>> p : IMAGE_HEIGHT) {
            if (p.test(screen)) return true;
        }
        return false;
    }
}
