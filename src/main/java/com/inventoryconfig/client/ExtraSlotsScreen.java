package com.inventoryconfig.client;

import com.inventoryconfig.ExtraBar;
import com.inventoryconfig.ExtraLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vista de pantalla del boton "Extra Slots": la hotbar original (fija) abajo al centro y un marco punteado en
 * cada una de las 8 posiciones. Click izquierdo suma un slot (una "U" roja) o, sobre un slot existente, abre el
 * selector de inventario; click derecho resta. Edita las barras de la {@link ConfigScreen} que la abrio; nada se
 * guarda hasta apretar Save alli.
 */
public class ExtraSlotsScreen extends Screen {

    private static final String LANG = "inventoryconfig.";
    private static final ResourceLocation HOTBAR = ResourceLocation.withDefaultNamespace("hud/hotbar");

    private final ConfigScreen owner;
    private ExtraBar.Position hoverPos;
    private int hoverIdx;
    private Button hideButton;

    public ExtraSlotsScreen(ConfigScreen owner) {
        super(Component.translatable(LANG + "gui.extra.title"));
        this.owner = owner;
        // Si se deshabilito un slot que estaba asignado, vuelve a "U".
        owner.cleanExtra();
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;
        hideButton = addRenderableWidget(Button.builder(hideText(), b -> {
            owner.hideAssigned = !owner.hideAssigned;
            b.setMessage(hideText());
        }).bounds(cx - 80, cy - 10, 160, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.extra.back"), b -> onClose())
                .bounds(cx - 50, cy + 14, 100, 20).build());
    }

    private Component hideText() {
        return Component.translatable(LANG + "gui.extra.hide", Component.translatable(
                LANG + (owner.hideAssigned ? "gui.on" : "gui.off")));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(owner);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- edicion

    private int indexOf(ExtraBar.Position p) {
        for (int i = 0; i < owner.extraBars.size(); i++) {
            if (owner.extraBars.get(i).position() == p) return i;
        }
        return -1;
    }

    ExtraBar barAt(ExtraBar.Position p) {
        int i = indexOf(p);
        return i < 0 ? null : owner.extraBars.get(i);
    }

    private int total() {
        int t = 0;
        for (ExtraBar b : owner.extraBars) t += b.slots().size();
        return t;
    }

    /** Suma una "U" al final de la barra (la crea si no existe). */
    private void add(ExtraBar.Position p) {
        if (total() >= ExtraBar.MAX_TOTAL) return;
        int bi = indexOf(p);
        if (bi < 0) {
            owner.extraBars.add(new ExtraBar(p, List.of(ExtraBar.UNASSIGNED)));
            return;
        }
        List<Integer> s = new ArrayList<>(owner.extraBars.get(bi).slots());
        if (s.size() >= ExtraBar.MAX_SLOTS) return;
        s.add(ExtraBar.UNASSIGNED);
        owner.extraBars.set(bi, new ExtraBar(p, List.copyOf(s)));
    }

    /** Resta el slot {@code slotIdx} (-1 = el ultimo); los siguientes se corren. Una barra sin slots deja de existir. */
    private void remove(ExtraBar.Position p, int slotIdx) {
        int bi = indexOf(p);
        if (bi < 0) return;
        List<Integer> s = new ArrayList<>(owner.extraBars.get(bi).slots());
        int idx = slotIdx < 0 ? s.size() - 1 : slotIdx;
        if (idx < 0 || idx >= s.size()) return;
        s.remove(idx);
        if (s.isEmpty()) owner.extraBars.remove(bi);
        else owner.extraBars.set(bi, new ExtraBar(p, List.copyOf(s)));
    }

    /** Lo llama el selector: el slot {@code idx} de la barra pasa a apuntar al slot {@code slot} del inventario. */
    void assign(ExtraBar.Position p, int idx, int slot) {
        int bi = indexOf(p);
        if (bi < 0) return;
        List<Integer> s = new ArrayList<>(owner.extraBars.get(bi).slots());
        if (idx < 0 || idx >= s.size()) return;
        s.set(idx, slot);
        owner.extraBars.set(bi, new ExtraBar(p, List.copyOf(s)));
    }

    ConfigScreen owner() {
        return owner;
    }

    // ---------------------------------------------------------------- mouse

    /** Busca sobre que marco / slot esta el mouse y lo deja en hoverPos / hoverIdx (null = ninguno). */
    private void locate(double mx, double my) {
        hoverPos = null;
        for (ExtraBar.Position p : ExtraBar.Position.VALUES) {
            int[] r = ExtraBarLayout.rect(p, ExtraBar.MAX_SLOTS, width, height);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                int along = p.vertical() ? (int) my - r[1] : (int) mx - r[0];
                hoverPos = p;
                hoverIdx = Math.max(0, Math.min(ExtraBar.MAX_SLOTS - 1, Math.floorDiv(along - 1, ExtraBarLayout.CELL)));
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0 && button != 1) return false;
        locate(mx, my);
        if (hoverPos == null) return false;
        ExtraBar bar = barAt(hoverPos);
        int n = bar == null ? 0 : bar.slots().size();
        if (hoverIdx < n) {
            if (button == 0) minecraft.setScreen(new SlotPickerScreen(this, hoverPos, hoverIdx));
            else remove(hoverPos, hoverIdx);
        } else if (button == 0) {
            add(hoverPos);
        } else {
            remove(hoverPos, -1);
        }
        return true;
    }

    // ---------------------------------------------------------------- dibujo

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick); // fondo y widgets
        locate(mx, my);

        g.drawCenteredString(font, title, width / 2, height / 2 - 44, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable(LANG + "gui.extra.help"),
                width / 2, height / 2 - 30, 0xFFAAAAAA);

        // Hotbar original: fija, solo de referencia.
        g.blitSprite(HOTBAR, (width - ExtraBarLayout.HOTBAR_W) / 2, height - ExtraBarLayout.HOTBAR_H,
                ExtraBarLayout.HOTBAR_W, ExtraBarLayout.HOTBAR_H);

        int total = total();
        for (ExtraBar.Position p : ExtraBar.Position.VALUES) {
            drawBar(g, p, total);
        }
        if (hoverPos != null) {
            g.renderComponentTooltip(font, tooltip(total), mx, my);
        }
    }

    private void drawBar(GuiGraphics g, ExtraBar.Position p, int total) {
        int[] r = ExtraBarLayout.rect(p, ExtraBar.MAX_SLOTS, width, height);
        boolean hovered = p == hoverPos;
        dashed(g, r[0], r[1], r[2], r[3], hovered ? 0xFFFFFFFF : 0xFF909090);

        ExtraBar bar = barAt(p);
        int n = bar == null ? 0 : bar.slots().size();
        for (int i = 0; i < n; i++) {
            int[] c = ExtraBarLayout.cell(r, p.vertical(), i);
            int slot = bar.slots().get(i);
            boolean unassigned = slot == ExtraBar.UNASSIGNED;
            g.fill(c[0] + 1, c[1] + 1, c[0] + 19, c[1] + 19, unassigned ? 0xFFC02020 : 0xFF6E6E6E);
            outline(g, c[0] + 1, c[1] + 1, 18, 18, 0xFF000000);
            g.drawCenteredString(font, unassigned ? "U" : String.valueOf(slot - 8), c[0] + 10, c[1] + 6, 0xFFFFFFFF);
            if (hovered && i == hoverIdx) g.fill(c[0] + 1, c[1] + 1, c[0] + 19, c[1] + 19, 0x50FFFFFF);
        }
        // Lugar del proximo slot.
        if (n < ExtraBar.MAX_SLOTS && total < ExtraBar.MAX_TOTAL) {
            int[] c = ExtraBarLayout.cell(r, p.vertical(), n);
            dashed(g, c[0] + 1, c[1] + 1, 18, 18, 0xFFCCCCCC);
            g.drawCenteredString(font, "+", c[0] + 10, c[1] + 6, hovered && hoverIdx >= n ? 0xFFFFFFFF : 0xFF909090);
        }
    }

    private List<Component> tooltip(int total) {
        ExtraBar bar = barAt(hoverPos);
        int n = bar == null ? 0 : bar.slots().size();
        Component posName = Component.translatable(LANG + "extra.position." + hoverPos.name().toLowerCase(Locale.ROOT));
        int barNumber = indexOf(hoverPos) + 1;
        List<Component> tip = new ArrayList<>();
        tip.add(bar == null
                ? Component.translatable(LANG + "extra.bar_new", posName)
                : Component.translatable(LANG + "extra.bar", barNumber, posName));
        if (hoverIdx < n) {
            int slot = bar.slots().get(hoverIdx);
            tip.add(slot == ExtraBar.UNASSIGNED
                    ? Component.translatable(LANG + "extra.unassigned")
                    : ConfigScreen.slotName(slot));
            tip.add(Component.translatable(LANG + "extra.tip.assign").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable(LANG + "extra.tip.remove").withStyle(ChatFormatting.GRAY));
        } else {
            boolean full = n >= ExtraBar.MAX_SLOTS || total >= ExtraBar.MAX_TOTAL;
            tip.add(Component.translatable(LANG + (full ? "extra.tip.full" : "extra.tip.add"))
                    .withStyle(ChatFormatting.GRAY));
            if (n > 0) tip.add(Component.translatable(LANG + "extra.tip.remove_last").withStyle(ChatFormatting.GRAY));
        }
        return tip;
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Rectangulo punteado ("- - - - -"): rayas de 3 px y huecos de 2 px. */
    private static void dashed(GuiGraphics g, int x, int y, int w, int h, int color) {
        for (int i = 0; i < w; i += 5) {
            int len = Math.min(3, w - i);
            g.fill(x + i, y, x + i + len, y + 1, color);
            g.fill(x + i, y + h - 1, x + i + len, y + h, color);
        }
        for (int j = 0; j < h; j += 5) {
            int len = Math.min(3, h - j);
            g.fill(x, y + j, x + 1, y + j + len, color);
            g.fill(x + w - 1, y + j, x + w, y + j + len, color);
        }
    }
}
