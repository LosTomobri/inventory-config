package com.inventoryconfig.client;

import com.inventoryconfig.RuleItem;
import com.inventoryconfig.RulesLayout;
import com.inventoryconfig.SlotRule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Base comun de los selectores de slot: la vista del inventario completo, un slot seleccionable a la vez y, a la
 * derecha, el panel de reglas del slot seleccionado (item fijo, exclusivo, no soltar). Las reglas se editan en una
 * copia de trabajo ({@link #work}); cada subclase decide cuando se confirman en el borrador de {@link ConfigScreen}.
 * <p>
 * Las subclases definen que slots se pueden elegir ({@link #blockedReason}) y los botones de abajo.
 */
abstract class SlotPickerBase extends Screen {

    protected static final String LANG = "inventoryconfig.";
    protected static final int SIDE_W = 130;
    protected static final int GAP = 8;

    protected final ConfigScreen owner;
    /** Copia de trabajo de las reglas: se confirma en las subclases. */
    protected RulesLayout work;
    /** Slot de Inventory seleccionado (-1 = ninguno). */
    protected int selected = -1;
    protected int left;
    protected int top;

    protected SlotPickerBase(Component title, ConfigScreen owner) {
        super(title);
        this.owner = owner;
        this.work = owner.rules;
    }

    /** Por que no se puede elegir {@code slot}; null si se puede. */
    protected abstract Component blockedReason(int slot);

    /** Botones de abajo del inventario (y = coordenada de la fila). */
    protected abstract void addBottomButtons(int by);

    /** Texto de ayuda que tapa el crafteo. */
    protected abstract Component helpText();

    /** Titulo sobre el inventario. */
    protected abstract Component headerText();

    /** Si este selector edita reglas (panel de la derecha, marcas y lineas de reglas en el tooltip). */
    protected boolean showRules() {
        return true;
    }

    /** Click izquierdo sobre un slot elegible. Por defecto lo selecciona (y aparece el panel de reglas). */
    protected void onCellClicked(int slot) {
        selected = slot;
        rebuildWidgets();
    }

    /** Slot que ya esta asignado en este contexto (se marca en verde), o -1. */
    protected int currentSlot() {
        return -1;
    }

    // ---------------------------------------------------------------- reglas

    protected SlotRule rule() {
        return selected < 0 ? SlotRule.NONE : work.get(selected);
    }

    /** Guarda la regla del slot seleccionado en la copia de trabajo (una regla vacia se borra). */
    protected void setRule(SlotRule r) {
        if (selected < 0) return;
        Map<Integer, SlotRule> map = new TreeMap<>(work.bySlot());
        SlotRule clean = new SlotRule(r.items(), r.exclusive() && r.hasItems(), r.noDrop());
        if (clean.isEmpty()) map.remove(selected);
        else map.put(selected, clean);
        work = new RulesLayout(map);
    }

    protected static ItemStack stackOf(RuleItem item) {
        return RuleVisuals.stackOf(item);
    }

    private static Component itemName(RuleItem item) {
        ItemStack stack = stackOf(item);
        return stack.isEmpty() ? Component.literal(item.id().toString()) : stack.getHoverName();
    }

    private static Component onOff(String key, boolean on) {
        return Component.translatable(LANG + key, Component.translatable(LANG + (on ? "gui.on" : "gui.off")));
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        // Igual que ConfigScreen: el inventario siempre en el mismo lugar.
        left = ConfigScreen.panelLeft(width);
        top = Math.max(20, (height - ConfigScreen.PANEL_H - 26) / 2);

        addBottomButtons(top + ConfigScreen.PANEL_H + 6);
        if (selected >= 0 && showRules()) addRulePanel();
    }

    private void addRulePanel() {
        int px = left + ConfigScreen.PANEL_W + GAP;
        int y = top + 22;
        SlotRule r = rule();

        Component itemLabel;
        if (r.items().isEmpty()) itemLabel = Component.translatable(LANG + "rules.item.none");
        else if (r.items().size() == 1) itemLabel = itemName(r.items().get(0));
        else itemLabel = Component.translatable(LANG + "rules.item.many", r.items().size());
        addRenderableWidget(Button.builder(Component.translatable(LANG + "rules.items", itemLabel), b -> openItemPicker())
                .bounds(px, y, SIDE_W, 20).build());
        y += 22;

        Button exclusive = Button.builder(onOff("rules.exclusive", r.exclusive()), b -> {
            SlotRule cur = rule();
            setRule(new SlotRule(cur.items(), !cur.exclusive(), cur.noDrop()));
            rebuildWidgets();
        }).bounds(px, y, SIDE_W, 20).build();
        exclusive.active = r.hasItems(); // exclusivo necesita al menos un item fijo
        addRenderableWidget(exclusive);
        y += 22;

        addRenderableWidget(Button.builder(onOff("rules.nodrop", r.noDrop()), b -> {
            SlotRule cur = rule();
            setRule(new SlotRule(cur.items(), cur.exclusive(), !cur.noDrop()));
            rebuildWidgets();
        }).bounds(px, y, SIDE_W, 20).build());
        y += 22;

        Button clear = Button.builder(Component.translatable(LANG + "rules.clear"), b -> {
            setRule(SlotRule.NONE);
            rebuildWidgets();
        }).bounds(px, y, SIDE_W, 20).build();
        clear.active = !r.isEmpty();
        addRenderableWidget(clear);
    }

    private void openItemPicker() {
        minecraft.setScreen(new ItemPickerScreen(this, selected, rule().items(), picked -> {
            SlotRule cur = rule();
            setRule(new SlotRule(picked, cur.exclusive(), cur.noDrop()));
        }));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- mouse

    private int cellAt(double mx, double my) {
        for (int i = 0; i < ConfigScreen.CELLS.length; i++) {
            int x = left + ConfigScreen.CELLS[i][1] - 1;
            int y = top + ConfigScreen.CELLS[i][2] - 1;
            if (mx >= x && mx < x + 18 && my >= y && my < y + 18) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 1) {
            // Click derecho: primero se deselecciona; sin seleccion, se cierra (como cancelar).
            if (selected >= 0) {
                selected = -1;
                rebuildWidgets();
            } else {
                onClose();
            }
            return true;
        }
        if (button == 0) {
            int i = cellAt(mx, my);
            if (i >= 0) {
                int slot = ConfigScreen.CELLS[i][0];
                if (blockedReason(slot) == null) onCellClicked(slot);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- dibujo

    private List<Component> tooltipFor(int slot, Component reason) {
        List<Component> lines = new ArrayList<>();
        lines.add(reason != null ? reason : ConfigScreen.slotName(slot));
        return lines;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick); // fondo y widgets

        g.drawCenteredString(font, headerText(), left + ConfigScreen.PANEL_W / 2, top - 14, 0xFFFFFFFF);

        g.blit(ConfigScreen.TEXTURE, left, top, 0, 0, ConfigScreen.PANEL_W, ConfigScreen.PANEL_H);
        g.fill(left + 95, top + 6, left + 172, top + 60, ConfigScreen.PANEL_COLOR);
        g.drawWordWrap(font, helpText(), left + 98, top + 10, 72, 0xFF404040);

        int hover = cellAt(mx, my);
        int current = currentSlot();
        for (int i = 0; i < ConfigScreen.CELLS.length; i++) {
            int slot = ConfigScreen.CELLS[i][0];
            int x = left + ConfigScreen.CELLS[i][1] - 1;
            int y = top + ConfigScreen.CELLS[i][2] - 1;
            Component blocked = blockedReason(slot);
            if (blocked != null) {
                boolean off = owner.isOff(slot);
                g.fill(x, y, x + 18, y + 18, off ? 0xC0D02020 : 0xB0303030);
                if (off) g.drawCenteredString(font, "X", x + 9, y + 5, 0xFFFFFFFF);
                continue;
            }
            if (slot == selected) g.fill(x, y, x + 18, y + 18, 0xA055FF55);
            else if (slot == current) g.fill(x, y, x + 18, y + 18, 0x6055FF55);
            if (showRules()) drawRuleMarks(g, slot, x, y);
            if (i == hover) g.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
        }

        // Cabecera del panel de la derecha
        int px = left + ConfigScreen.PANEL_W + GAP;
        if (!showRules()) {
            // sin panel de reglas
        } else if (selected >= 0) {
            RuleItem item = RuleVisuals.preview(rule(), selected);
            int tx = px;
            if (item != null) {
                g.renderFakeItem(stackOf(item), px, top + 2);
                tx = px + 20;
            }
            g.drawString(font, ConfigScreen.slotName(selected), tx, top + 6, 0xFFFFFFFF);
        } else {
            g.drawWordWrap(font, Component.translatable(LANG + "rules.select"), px, top + 6, SIDE_W, 0xFFFFFFFF);
        }

        if (hover >= 0) {
            int slot = ConfigScreen.CELLS[hover][0];
            Component reason = blockedReason(slot);
            g.renderTooltip(font, tooltipFor(slot, reason), Optional.empty(), mx, my);
        }
    }

    /**
     * Marcas de reglas en una casilla: items fijos = un item atenuado (rota si hay varios), "1" = exclusivo, "D" = no soltar.
     * Se dibujan con un z alto para quedar por encima del item.
     */
    private void drawRuleMarks(GuiGraphics g, int slot, int x, int y) {
        SlotRule r = work.get(slot);
        if (r.isEmpty()) return;
        RuleItem shown = RuleVisuals.preview(r, slot);
        if (shown != null) {
            g.renderFakeItem(stackOf(shown), x + 1, y + 1);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0x90808080);
            g.pose().popPose();
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        if (r.exclusive()) g.drawString(font, "1", x + 11, y + 1, 0xFFFFFF55, true);
        if (r.noDrop()) g.drawString(font, "D", x + 11, y + 9, 0xFFFF5555, true);
        g.pose().popPose();
    }
}
