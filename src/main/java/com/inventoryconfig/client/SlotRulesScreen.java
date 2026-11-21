package com.inventoryconfig.client;

import com.inventoryconfig.RulesLayout;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Boton "Slot Rules" de la {@link ConfigScreen}: aca se editan las reglas (item fijo, exclusivo, no soltar) de los
 * 41 slots del inventario: hotbar, inventario principal, armadura y offhand. Solo se bloquean los slots
 * deshabilitados. "Done" guarda en el borrador de la ConfigScreen.
 */
public class SlotRulesScreen extends SlotPickerBase {

    private final ConfigScreen parent;

    public SlotRulesScreen(ConfigScreen parent) {
        super(Component.translatable(LANG + "gui.rules.title"), parent);
        this.parent = parent;
        // Si se deshabilito un slot que tenia reglas, se limpian antes de mostrarlas.
        parent.cleanExtra();
        this.work = parent.rules;
    }

    @Override
    protected void addBottomButtons(int by) {
        addRenderableWidget(Button.builder(Component.translatable(LANG + "rules.done"), b -> done())
                .bounds(left, by, 86, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.cancel"), b -> onClose())
                .bounds(left + 90, by, 86, 20).build());
    }

    /** Mismo interruptor que en Extra Slots ("Hide in Inventory"): comparten el valor del borrador. */
    @Override
    protected void init() {
        super.init();
        int px = left + ConfigScreen.PANEL_W + GAP;
        addRenderableWidget(Button.builder(hideText(), b -> {
            parent.hideAssigned = !parent.hideAssigned;
            b.setMessage(hideText());
        }).bounds(px, top + ConfigScreen.PANEL_H - 20, SIDE_W, 20).build());
    }

    private Component hideText() {
        return Component.translatable(LANG + "gui.extra.hide", Component.translatable(
                LANG + (parent.hideAssigned ? "gui.on" : "gui.off")));
    }

    private void done() {
        parent.rules = RulesLayout.sanitize(work, parent.mask);
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent); // cancelar: se descartan los cambios de esta pantalla
    }

    @Override
    protected Component blockedReason(int slot) {
        Component name = ConfigScreen.slotName(slot);
        if (owner.isOff(slot)) return Component.translatable(LANG + "slot.disabled", name);
        return null;
    }

    @Override
    protected Component helpText() {
        return Component.translatable(LANG + "rules.help");
    }

    @Override
    protected Component headerText() {
        return Component.translatable(LANG + "gui.rules.title");
    }
}
