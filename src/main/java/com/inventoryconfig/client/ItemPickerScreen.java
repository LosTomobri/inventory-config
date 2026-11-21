package com.inventoryconfig.client;

import com.inventoryconfig.RuleItem;
import com.inventoryconfig.SlotRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Menu propio para elegir los items de una regla: barra de busqueda (por nombre visible o por id; "@mod" filtra por
 * mod), grilla con scroll (rueda o barra) y botones Clear / Done / Cancel. Un click sobre un item lo marca o lo
 * desmarca: se pueden elegir varios (el slot aceptara el que haya disponible). En slots de armadura solo muestra los
 * items que se equipan en esa ranura. "Done" devuelve la lista por {@code onPick} y vuelve a la pantalla que la abrio.
 */
public class ItemPickerScreen extends Screen {

    private static final String LANG = "inventoryconfig.";
    private static final int COLS = 9;
    private static final int ROWS = 6;
    private static final int CELL = 18;
    private static final int PAD = 8;
    private static final int BAR_W = 8;
    private static final int W = PAD * 2 + COLS * CELL + 4 + BAR_W;
    private static final int GRID_DY = PAD + 34;
    private static final int H = GRID_DY + ROWS * CELL + 4 + 20 + PAD;

    private final Screen parent;
    /** Items marcados (en el orden en que se eligieron). */
    private final Set<RuleItem> chosen = new LinkedHashSet<>();
    private final Consumer<List<RuleItem>> onPick;

    private final List<ItemStack> all = new ArrayList<>();
    private final List<RuleItem> keys = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<Integer> shown = new ArrayList<>(); // indices en "all"

    private EditBox search;
    private String query = "";
    private int scroll = 0;
    private boolean draggingBar = false;
    private int px;
    private int py;

    ItemPickerScreen(Screen parent, int invSlot, List<RuleItem> current,
                     Consumer<List<RuleItem>> onPick) {
        super(Component.translatable(LANG + "item.title"));
        this.parent = parent;
        this.chosen.addAll(current);
        this.onPick = onPick;

        boolean armor = SlotRule.armorSlot(invSlot) != null;
        // Un item por cada id de registro...
        Map<RuleItem, ItemStack> found = new LinkedHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (armor && !SlotRule.validItemFor(invSlot, id)) continue;
            found.putIfAbsent(RuleItem.of(id), item.getDefaultInstance());
        }
        // ...y uno por cada variante con datos propios (items que comparten id y se distinguen por un GunId, etc.).
        for (ItemStack variant : variants()) {
            if (variant.isEmpty()) continue;
            RuleItem key = RuleItem.of(variant);
            if (key.data().isEmpty()) continue; // sin datos que lo distingan: ya esta arriba
            if (armor && !SlotRule.validItemFor(invSlot, key.id())) continue;
            found.putIfAbsent(key, variant.copyWithCount(1));
        }
        // Lo que ya estaba elegido y no aparece (variante que ya no existe) se sigue mostrando, para poder quitarlo.
        for (RuleItem key : current) {
            if (found.containsKey(key)) continue;
            ItemStack stack = key.displayStack();
            if (!stack.isEmpty()) found.put(key, stack);
        }

        List<Map.Entry<RuleItem, ItemStack>> entries = new ArrayList<>(found.entrySet());
        entries.sort(Comparator.<Map.Entry<RuleItem, ItemStack>, String>comparing(e -> e.getKey().id().getNamespace())
                .thenComparing(e -> e.getKey().id().getPath())
                .thenComparing(e -> e.getKey().data().toString()));
        for (Map.Entry<RuleItem, ItemStack> e : entries) {
            keys.add(e.getKey());
            all.add(e.getValue());
            StringBuilder text = new StringBuilder(e.getValue().getHoverName().getString().toLowerCase(Locale.ROOT));
            for (String k : e.getKey().data().getAllKeys()) {
                text.append(' ').append(e.getKey().data().getString(k).toLowerCase(Locale.ROOT));
            }
            names.add(text.toString());
        }
        filter();
    }

    /** Variantes de items (con sus datos) que muestra el inventario creativo. Vacio si no hay mundo abierto. */
    private static List<ItemStack> variants() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return List.of();
        try {
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(),
                    mc.player.canUseGameMasterBlocks(), mc.level.registryAccess());
            return List.copyOf(CreativeModeTabs.searchTab().getSearchTabDisplayItems());
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    // ---------------------------------------------------------------- filtro

    private void filter() {
        shown.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        String ns = null;
        String text = q;
        if (q.startsWith("@")) {
            int sp = q.indexOf(' ');
            ns = sp < 0 ? q.substring(1) : q.substring(1, sp);
            text = sp < 0 ? "" : q.substring(sp + 1).trim();
        }
        for (int i = 0; i < all.size(); i++) {
            ResourceLocation id = keys.get(i).id();
            if (ns != null && !id.getNamespace().contains(ns)) continue;
            if (!text.isEmpty() && !names.get(i).contains(text) && !id.toString().contains(text)) continue;
            shown.add(i);
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int totalRows() {
        return (shown.size() + COLS - 1) / COLS;
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - ROWS);
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = Math.max(4, (height - H) / 2);

        search = new EditBox(font, px + PAD, py + PAD, W - PAD * 2, 18, Component.translatable(LANG + "item.search"));
        search.setHint(Component.translatable(LANG + "item.search"));
        search.setMaxLength(64);
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            scroll = 0;
            filter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        int by = py + GRID_DY + ROWS * CELL + 4;
        int bw = (W - PAD * 2 - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable(LANG + "item.clear"), b -> chosen.clear())
                .bounds(px + PAD, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(LANG + "rules.done"), b -> done())
                .bounds(px + PAD + bw + 4, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.cancel"), b -> onClose())
                .bounds(px + W - PAD - bw, by, bw, 20).build());
    }

    /** Confirma la seleccion (vacia = sin items) y vuelve. */
    private void done() {
        onPick.accept(List.copyOf(chosen));
        minecraft.setScreen(parent);
    }

    private void toggle(RuleItem item) {
        if (!chosen.remove(item) && chosen.size() < SlotRule.MAX_ITEMS) chosen.add(item);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- mouse

    private int gridX() {
        return px + PAD;
    }

    private int gridY() {
        return py + GRID_DY;
    }

    private int barX() {
        return gridX() + COLS * CELL + 4;
    }

    /** Posicion en "shown" de la casilla bajo el mouse, o -1. */
    private int cellAt(double mx, double my) {
        int c = Math.floorDiv((int) mx - gridX(), CELL);
        int r = Math.floorDiv((int) my - gridY(), CELL);
        if (mx < gridX() || my < gridY() || c < 0 || c >= COLS || r < 0 || r >= ROWS) return -1;
        int idx = (scroll + r) * COLS + c;
        return idx < shown.size() ? idx : -1;
    }

    private boolean overBar(double mx, double my) {
        return mx >= barX() && mx < barX() + BAR_W && my >= gridY() && my < gridY() + ROWS * CELL;
    }

    private void scrollFromMouse(double my) {
        int max = maxScroll();
        if (max == 0) return;
        int barH = ROWS * CELL;
        double t = (my - gridY()) / barH;
        scroll = Math.max(0, Math.min(max, (int) Math.round(t * max)));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            if (overBar(mx, my)) {
                draggingBar = true;
                scrollFromMouse(my);
                return true;
            }
            int i = cellAt(mx, my);
            if (i >= 0) {
                toggle(keys.get(shown.get(i)));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingBar && button == 0) {
            scrollFromMouse(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingBar = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ---------------------------------------------------------------- dibujo

    /** El panel y las casillas van de fondo, para que los widgets (busqueda, botones) se dibujen encima. */
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        super.renderBackground(g, mx, my, partialTick);
        g.fill(px - 1, py - 1, px + W + 1, py + H + 1, 0xFF000000);
        g.fill(px, py, px + W, py + H, ConfigScreen.PANEL_COLOR);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = gridX() + c * CELL;
                int y = gridY() + r * CELL;
                g.fill(x, y, x + CELL, y + CELL, 0xFF373737);
                g.fill(x + 1, y + 1, x + CELL, y + CELL, 0xFFFFFFFF);
                g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0xFF8B8B8B);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick); // fondo, panel y widgets

        int hover = cellAt(mx, my);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scroll + r) * COLS + c;
                if (idx >= shown.size()) break;
                int x = gridX() + c * CELL;
                int y = gridY() + r * CELL;
                int all_i = shown.get(idx);
                g.renderItem(all.get(all_i), x + 1, y + 1);
                boolean isCurrent = chosen.contains(keys.get(all_i));
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                if (isCurrent) g.renderOutline(x, y, CELL, CELL, 0xFF55FF55);
                if (idx == hover) g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x80FFFFFF);
                g.pose().popPose();
            }
        }

        g.drawString(font, Component.translatable(LANG + "item.selected", chosen.size()),
                gridX(), py + PAD + 22, 0xFF404040, false);

        if (shown.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(LANG + "item.none"),
                    gridX() + COLS * CELL / 2, gridY() + ROWS * CELL / 2 - 4, 0xFF404040);
        }

        // Barra de scroll
        int barY = gridY();
        int barH = ROWS * CELL;
        g.fill(barX(), barY, barX() + BAR_W, barY + barH, 0xFF373737);
        int max = maxScroll();
        int thumbH = max == 0 ? barH : Math.max(12, barH * ROWS / totalRows());
        int thumbY = max == 0 ? barY : barY + (barH - thumbH) * scroll / max;
        g.fill(barX() + 1, thumbY + 1, barX() + BAR_W - 1, thumbY + thumbH - 1, 0xFFC6C6C6);

        if (hover >= 0) {
            g.renderTooltip(font, all.get(shown.get(hover)), mx, my);
        }
    }
}
