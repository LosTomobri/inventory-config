package com.inventoryconfig.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * De que color se tapan los slots apagados que no se pueden quitar de la pantalla.
 * <p>
 * Con un gris fijo (el de vanilla) se vería como un parche claro en cualquier pack de texturas oscuro
 * o en una pantalla de mod con otros colores. Por eso se toma el color directamente de la textura de la
 * pantalla (leyendo un pixel del panel, cerca de la esquina). Orden de prioridad:
 * <ol>
 *   <li>un color fijado con {@link #register};</li>
 *   <li>el pixel del panel de la textura (se busca un ResourceLocation "textures/gui/...png" en la clase de la pantalla);</li>
 *   <li>el gris de vanilla.</li>
 * </ol>
 */
public final class PanelColors {
    /** Gris del panel de vanilla. */
    public static final int VANILLA = 0xFFC6C6C6;

    private record Override(Predicate<AbstractContainerScreen<?>> match, int argb) {
    }

    private static final List<Override> OVERRIDES = new CopyOnWriteArrayList<>();
    private static final Map<AbstractContainerScreen<?>, Integer> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Pixeles de la textura donde hay panel liso (pasado el borde, antes del primer slot). */
    private static final int[][] SAMPLES = {{4, 4}, {5, 5}, {4, 6}, {6, 4}};

    private PanelColors() {
    }

    /** Fija el color (ARGB) para las pantallas que cumplan la condicion. */
    public static void register(Predicate<AbstractContainerScreen<?>> match, int argb) {
        OVERRIDES.add(new Override(match, 0xFF000000 | argb));
    }

    static int of(AbstractContainerScreen<?> screen) {
        for (Override o : OVERRIDES) {
            if (o.match().test(screen)) return o.argb();
        }
        synchronized (CACHE) {
            Integer cached = CACHE.get(screen);
            if (cached != null) return cached;
            int color = detect(screen);
            CACHE.put(screen, color);
            return color;
        }
    }

    private static int detect(AbstractContainerScreen<?> screen) {
        try {
            ResourceLocation texture = findTexture(screen);
            if (texture == null) return VANILLA;
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isEmpty()) return VANILLA;
            try (InputStream in = resource.get().open()) {
                BufferedImage image = ImageIO.read(in);
                return image == null ? VANILLA : sample(image);
            }
        } catch (Exception | LinkageError e) {
            return VANILLA;
        }
    }

    /** El color mas repetido entre unos pocos pixeles del panel. */
    private static int sample(BufferedImage image) {
        Map<Integer, Integer> votes = new HashMap<>();
        int best = VANILLA;
        int bestVotes = 0;
        for (int[] p : SAMPLES) {
            if (p[0] >= image.getWidth() || p[1] >= image.getHeight()) continue;
            int argb = image.getRGB(p[0], p[1]);
            if ((argb >>> 24) != 0xFF) continue; // transparente: no es panel
            int n = votes.merge(argb, 1, Integer::sum);
            if (n > bestVotes) {
                bestVotes = n;
                best = argb;
            }
        }
        return best;
    }

    /** Color del panel de una textura (el pixel mas repetido en unos puntos lisos). */
    static int panelColorOf(BufferedImage image) {
        return sample(image);
    }

    private static ResourceLocation findTexture(AbstractContainerScreen<?> screen) {
        List<ResourceLocation> all = textures(screen);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Texturas candidatas a ser el fondo de la pantalla: los ResourceLocation "textures/....png" de su clase y
     * sus padres (hasta AbstractContainerScreen). Primero las de "/container/", despues las de "textures/gui/"
     * y al final cualquier otra.
     */
    static List<ResourceLocation> textures(AbstractContainerScreen<?> screen) {
        List<ResourceLocation> container = new ArrayList<>();
        List<ResourceLocation> gui = new ArrayList<>();
        List<ResourceLocation> other = new ArrayList<>();
        for (Class<?> c = screen.getClass(); c != null && c != AbstractContainerScreen.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() != ResourceLocation.class) continue;
                try {
                    f.setAccessible(true);
                    Object value = f.get(Modifier.isStatic(f.getModifiers()) ? null : screen);
                    if (!(value instanceof ResourceLocation rl)) continue;
                    String path = rl.getPath();
                    if (!path.startsWith("textures/") || !path.endsWith(".png")) continue;
                    List<ResourceLocation> target = path.contains("/container/") ? container
                            : path.startsWith("textures/gui/") ? gui : other;
                    if (!target.contains(rl)) target.add(rl);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // este campo no se puede leer: se prueba con el siguiente
                }
            }
        }
        List<ResourceLocation> all = new ArrayList<>(container);
        all.addAll(gui);
        all.addAll(other);
        return all;
    }
}
