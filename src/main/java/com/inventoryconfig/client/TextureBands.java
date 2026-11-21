package com.inventoryconfig.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.inventoryconfig.Slots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Modo automatico para pantallas que nadie anoto (por ejemplo, la de un mod).
 * <p>
 * No hace falta saber como dibuja su fondo: se deja la ventana donde esta, vanilla dibuja lo suyo recortado
 * hasta el primer tramo quitado (ver {@code ClientHandler#onBackgroundRendered}), y la parte de abajo se pega
 * directamente desde la textura del fondo, subida.
 * <p>
 * Eso solo es valido si se sabe de que fila de la textura sale cada fila de la ventana. Dos casos:
 * <ul>
 *   <li><b>lineal</b>: la textura es el fondo dibujado en (0, 0) (fila de ventana = fila de textura);</li>
 *   <li><b>estilo cofre</b>: la textura es generic_54 (la del cofre), donde el fondo son dos pedazos: la parte del
 *       contenedor (17 + filas*18) y una parte inferior fija desde v=126. Las filas salen de contar los slots.</li>
 * </ul>
 * Antes de usarla se comprueba mirando los pixeles de la textura: el centro de cada slot tiene que ser una
 * casilla (distinto del color del panel) y, justo arriba y abajo de cada slot, tiene que haber panel liso.
 * Si no se cumple, no se toca nada y la pantalla queda como siempre: solo se tapan sus slots apagados.
 */
final class TextureBands {

    private static final String CHEST_TEXTURE = "textures/gui/container/generic_54.png";
    private static final int CHEST_BOTTOM_V = 126;   // donde empieza la parte inferior de generic_54

    /**
     * Textura de fondo comprobada, con su tamaño real (no siempre es 256x256).
     *
     * @param chestRows  0 = lineal; si no, la cantidad de filas del contenedor (estilo cofre)
     * @param fullHeight alto de la ventana que dibuja la textura (-1 = el que ya tenia la pantalla)
     */
    record Result(ResourceLocation texture, int width, int height, int chestRows, int fullHeight) {

        /** De que fila de la textura sale la fila {@code r} de la ventana. */
        int sourceRow(int r) {
            if (chestRows <= 0) return r;
            int containerHeight = chestRows * 18 + 17;
            return r < containerHeight ? r : CHEST_BOTTOM_V + (r - containerHeight);
        }
    }

    private TextureBands() {
    }

    /** Busca entre las texturas de la pantalla una que sirva; devuelve null si ninguna. */
    static Result probe(AbstractContainerScreen<?> screen, int fullHeight, List<String> notes) {
        List<ResourceLocation> candidates = PanelColors.textures(screen);
        if (candidates.isEmpty()) {
            notes.add("no encontre ninguna textura de fondo en la clase de la pantalla");
            return null;
        }
        for (ResourceLocation texture : candidates) {
            BufferedImage image = read(texture);
            if (image == null) {
                notes.add(texture + ": no se pudo leer");
                continue;
            }
            int rows = texture.getPath().equals(CHEST_TEXTURE) ? chestRows(screen) : 0;
            int height = rows > 0 ? 113 + rows * 18 : fullHeight;
            Result result = new Result(texture, image.getWidth(), image.getHeight(), rows, rows > 0 ? height : -1);
            String problem = validate(image, screen, height, result);
            if (problem == null) return result;
            notes.add(texture + (rows > 0 ? " (estilo cofre, " + rows + " filas)" : "") + ": " + problem);
        }
        return null;
    }

    /** Filas del contenedor (slots que no son del jugador, de a 9), o 0 si no cierra la cuenta. */
    private static int chestRows(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        Inventory inv = mc.player.getInventory();
        int others = 0;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != inv) others++;
        }
        return others > 0 && others % 9 == 0 ? others / 9 : 0;
    }

    private static BufferedImage read(ResourceLocation texture) {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isEmpty()) return null;
            try (InputStream in = resource.get().open()) {
                return ImageIO.read(in);
            }
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /** null si la textura es el fondo de la pantalla; si no, el motivo. */
    private static String validate(BufferedImage image, AbstractContainerScreen<?> screen, int height, Result map) {
        int width = screen.getXSize();
        if (image.getWidth() < width || image.getHeight() < map.sourceRow(height - 1) + 1) {
            return "es mas chica que la ventana (" + image.getWidth() + "x" + image.getHeight()
                    + " contra " + width + "x" + height + ")";
        }
        List<Slot> slots = screen.getMenu().slots;
        int panel = PanelColors.panelColorOf(image);

        // 1) en cada slot tiene que haber una casilla
        int total = 0;
        int sockets = 0;
        for (Slot slot : slots) {
            Slot b = Slots.base(slot);
            if (!inside(b, width, height)) continue;
            total++;
            if (differs(image, b.x + 8, map.sourceRow(b.y + 8), panel)) sockets++;
        }
        if (total == 0) return "no hay slots dentro de la ventana";
        if (sockets * 10 < total * 9) {
            return "los slots no coinciden con casillas de la textura (" + sockets + " de " + total + ")";
        }

        // 2) justo arriba y abajo de cada slot (si ahi no hay otro slot) tiene que haber panel liso:
        //    asi se descarta una textura que tenga casillas donde la pantalla no tiene slots
        int checked = 0;
        int plain = 0;
        for (Slot slot : slots) {
            Slot b = Slots.base(slot);
            if (!inside(b, width, height)) continue;
            for (int y : new int[]{b.y - 3, b.y + 19}) {
                if (y < 0 || y >= height || covered(slots, b.x + 8, y)) continue;
                checked++;
                if (!differs(image, b.x + 8, map.sourceRow(y), panel)) plain++;
            }
        }
        if (checked > 0 && plain * 100 < checked * 85) {
            return "hay casillas en la textura donde la pantalla no tiene slots (" + (checked - plain)
                    + " de " + checked + " puntos): las filas de la textura no coinciden con las de la ventana";
        }
        return null;
    }

    private static boolean inside(Slot b, int width, int height) {
        return b.x >= 0 && b.y >= 0 && b.x + 16 <= width && b.y + 16 <= height;
    }

    /** Si el punto cae sobre la casilla de algun slot. */
    private static boolean covered(List<Slot> slots, int x, int y) {
        for (Slot slot : slots) {
            Slot b = Slots.base(slot);
            if (x >= b.x - 1 && x < b.x + 17 && y >= b.y - 1 && y < b.y + 17) return true;
        }
        return false;
    }

    private static boolean differs(BufferedImage image, int x, int y, int panel) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) return false;
        int argb = image.getRGB(x, y);
        return (argb >>> 24) == 0xFF && distance(argb, panel) > 24;
    }

    private static int distance(int a, int b) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF))
                + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                + Math.abs((a & 0xFF) - (b & 0xFF));
    }

    /** Pega, desde la textura, los pedazos de abajo (los que suben) en su lugar nuevo. */
    static void drawRemaining(AbstractContainerScreen<?> screen, GuiGraphics g, CompactLayout layout, Result tex) {
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        int width = screen.getXSize();
        BandRenderer.enableBlend(); // sin blend, los pixeles transparentes de la textura salen blancos
        for (CompactLayout.Segment s : layout.segments()) {
            if (s.shift() == 0) continue; // lo de arriba ya lo dibujo vanilla
            int from = s.start();
            int to = s.end();
            int destY = top + from - s.shift();
            // la fila de la textura cambia de pedazo en el limite del contenedor (solo estilo cofre)
            while (from < to) {
                int end = to;
                if (tex.chestRows() > 0) {
                    int boundary = tex.chestRows() * 18 + 17;
                    if (from < boundary) end = Math.min(to, boundary);
                }
                g.blit(tex.texture(), left, destY, 0.0F, (float) tex.sourceRow(from),
                        width, end - from, tex.width(), tex.height());
                destY += end - from;
                from = end;
            }
        }
    }
}
