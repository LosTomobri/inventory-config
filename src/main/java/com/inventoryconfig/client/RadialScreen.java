package com.inventoryconfig.client;

import com.inventoryconfig.ClientConfig;
import com.inventoryconfig.ExtraSelectPayload;
import com.inventoryconfig.ModState;
import com.inventoryconfig.RadialLayout;
import com.inventoryconfig.RuleItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Menu radial en juego: 8 casillas en circulo, cada una con el item de un slot del inventario principal (9-35).
 * Se abre siempre con la tecla (sin necesidad de tener algo configurado: las casillas vacias muestran "+").
 * <ul>
 *   <li>La casilla se elige por angulo desde el centro de la pantalla (8 sectores de 45 grados); dentro de la zona
 *       muerta del centro no hay seleccion.</li>
 *   <li>Modo HOLD: se abre al apretar la tecla y al soltarla se confirma (soltar en la zona muerta cancela).
 *       Modo TOGGLE: se abre con la tecla y se elige con un click; la tecla otra vez, o Esc, cancelan.</li>
 *   <li>Confirmar manda {@link ExtraSelectPayload}: el servidor hace el mismo intercambio con espejo que las hotbars
 *       extra (ver {@code ExtraSwap}) y lo valida de nuevo.</li>
 * </ul>
 * Los items se dibujan con {@link ExtraMirror#shown}, asi que con un intercambio activo se ve el contenido logico.
 * Un slot vacio cuya regla tiene items filtrados muestra la vista previa atenuada de uno de ellos.
 */
public class RadialScreen extends Screen {

    private static final String LANG = "inventoryconfig.";
    private static final int BOX = 44;
    private static final int BOX_HOVER = 52;

    /** Casillas con las que se abrio: si la config cambia mientras esta abierta, se cierra. */
    private final List<Integer> cells;
    /** TOGGLE: la tecla con la que se abrio ya se solto una vez (antes, sus repeticiones no deben cerrarla). */
    private boolean armed = false;

    public RadialScreen() {
        super(Component.translatable(LANG + "gui.radial.title"));
        this.cells = List.copyOf(ModState.radial.cells());
    }

    // ---------------------------------------------------------------- apertura

    /**
     * Se puede abrir en juego (sin otra pantalla y con el jugador vivo), aunque no haya nada configurado: las casillas
     * sin slot (o con el mod inactivo para el modo de juego) se ven vacias ("+") y no se pueden elegir.
     */
    static boolean canOpen(Minecraft mc) {
        return mc.player != null && mc.screen == null && mc.player.isAlive();
    }

    // ---------------------------------------------------------------- geometria

    private int cx() {
        return width / 2;
    }

    private int cy() {
        return height / 2;
    }

    private int ring() {
        return Math.max(60, Math.min(100, Math.min(width, height) / 2 - 40));
    }

    /** Centro de la casilla i: 0 = arriba, y de ahi en sentido horario cada 45 grados. */
    private int[] center(int i) {
        double a = Math.toRadians(i * 45.0);
        return new int[]{cx() + (int) Math.round(ring() * Math.sin(a)), cy() - (int) Math.round(ring() * Math.cos(a))};
    }

    /** Slot de la casilla i si se puede usar (asignado, no deshabilitado y con el mod activo), o -1. */
    private int slotOf(int i) {
        if (!ExtraView.enabled(Minecraft.getInstance())) return RadialLayout.EMPTY_CELL;
        int s = cells.get(i);
        if (!RadialLayout.assignable(s) || ((ModState.disabledMask >> s) & 1L) != 0L) return RadialLayout.EMPTY_CELL;
        return s;
    }

    /** Casilla bajo el cursor segun el angulo, o -1 (zona muerta, o sector sin slot usable). */
    private int hover(double mx, double my) {
        double dx = mx - cx();
        double dy = my - cy();
        if (Math.hypot(dx, dy) < ClientConfig.RADIAL_DEAD_ZONE.get()) return -1;
        double angle = (Math.toDegrees(Math.atan2(dx, -dy)) + 360.0) % 360.0;
        int cell = (int) Math.floor((angle + 22.5) / 45.0) % RadialLayout.CELLS;
        return slotOf(cell) >= 0 ? cell : -1;
    }

    /** Posicion actual del mouse en coordenadas de GUI (sirve tambien cuando el evento no la trae). */
    private double[] mouseGui() {
        Minecraft mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
        return new double[]{mx, my};
    }

    // ---------------------------------------------------------------- acciones

    private void confirm() {
        double[] m = mouseGui();
        int cell = hover(m[0], m[1]);
        if (cell >= 0) {
            PacketDistributor.sendToServer(new ExtraSelectPayload(slotOf(cell)));
        }
        onClose();
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        // Cambio la config o murio: se cancela sin confirmar. (Si el mod deja de aplicar por el modo de juego, las
        // casillas simplemente pasan a estar vacias.)
        if (mc.player == null || !mc.player.isAlive() || !cells.equals(ModState.radial.cells())) {
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- teclado y mouse

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ExtraKeys.RADIAL.matches(keyCode, scanCode)) {
            // HOLD: las repeticiones de la tecla apretada no hacen nada. TOGGLE: apretarla de nuevo cancela.
            if (ClientConfig.RADIAL_MODE.get() == ClientConfig.RadialMode.TOGGLE && armed) onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers); // Esc cancela
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ExtraKeys.RADIAL.matches(keyCode, scanCode)) {
            if (ClientConfig.RADIAL_MODE.get() == ClientConfig.RadialMode.HOLD) {
                confirm();
            } else {
                armed = true;
            }
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (ExtraKeys.RADIAL.matchesMouse(button)) {
            if (ClientConfig.RADIAL_MODE.get() == ClientConfig.RadialMode.TOGGLE && armed) onClose();
            return true;
        }
        if (button == 0) {
            int cell = hover(mx, my);
            if (cell >= 0) {
                PacketDistributor.sendToServer(new ExtraSelectPayload(slotOf(cell)));
                onClose();
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (ExtraKeys.RADIAL.matchesMouse(button)) {
            if (ClientConfig.RADIAL_MODE.get() == ClientConfig.RadialMode.HOLD) {
                confirm();
            } else {
                armed = true;
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    // ---------------------------------------------------------------- dibujo

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        // Mundo desenfocado y oscurecido (sin widgets: no se llama a super.render, asi el desenfoque se pide una vez).
        renderBlurredBackground(partialTick);
        g.fill(0, 0, width, height, 0x70000000);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int hover = hover(mx, my);
        int held = ExtraMirror.heldExtra();

        for (int i = 0; i < RadialLayout.CELLS; i++) {
            int slot = slotOf(i);
            int[] c = center(i);
            if (slot < 0) {
                g.drawCenteredString(font, "+", c[0], c[1] - 4, 0x60FFFFFF); // casilla sin slot: "+" tenue
                continue;
            }
            boolean hot = i == hover;
            int size = hot ? BOX_HOVER : BOX;
            int x0 = c[0] - size / 2;
            int y0 = c[1] - size / 2;
            g.fill(x0, y0, x0 + size, y0 + size, hot ? 0xD0404040 : 0xB0101010);
            if (hot) {
                g.renderOutline(x0, y0, size, size, 0xFFFFFFFF);
                g.renderOutline(x0 + 1, y0 + 1, size - 2, size - 2, 0xFFFFFFFF);
            } else {
                g.renderOutline(x0, y0, size, size, slot == held ? 0xFFFFFF55 : 0xFF808080);
            }

            // Item del slot, escalado x2 (con su cantidad).
            ItemStack stack = ExtraMirror.shown(mc.player.getInventory(), slot);
            g.pose().pushPose();
            g.pose().translate(c[0] - 16, c[1] - 16, 0); // el item mide 16: x2 = 32, centrado en la casilla
            g.pose().scale(2f, 2f, 1f);
            if (stack.isEmpty()) {
                // Slot vacio con items filtrados: vista previa atenuada (rota si hay varios), como en la hotbar.
                RuleItem preview = RuleVisuals.preview(ModState.rules.get(slot), slot);
                if (preview != null) RuleVisuals.ghost(g, preview, 0, 0, 0x99202020);
            } else {
                g.renderItem(stack, 0, 0);
                g.renderItemDecorations(font, stack, 0, 0);
            }
            g.pose().popPose();
        }

        // Centro: solo el nombre del item resaltado ("Empty" si el slot esta vacio).
        if (hover >= 0) {
            ItemStack stack = ExtraMirror.shown(mc.player.getInventory(), slotOf(hover));
            Component name = stack.isEmpty() ? Component.translatable(LANG + "radial.empty") : stack.getHoverName();
            int maxW = Math.max(40, 2 * (ring() - BOX / 2 - 6));
            float scale = Math.min(1.5f, maxW / (float) Math.max(1, font.width(name)));
            g.pose().pushPose();
            g.pose().translate(cx(), cy(), 0);
            g.pose().scale(scale, scale, 1f);
            g.drawCenteredString(font, name, 0, -4, 0xFFFFFFFF);
            g.pose().popPose();
        }
    }
}
