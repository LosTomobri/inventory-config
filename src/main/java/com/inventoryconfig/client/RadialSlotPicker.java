package com.inventoryconfig.client;

import com.inventoryconfig.RadialLayout;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Selector de slot de una casilla del radial (modo RADIAL_ASSIGN de {@link SlotPickerBase}): solo el inventario
 * principal (9-35), sin los deshabilitados ni los que ya estan en otra casilla. Un click asigna el slot y vuelve.
 * No toca las reglas: un mismo slot puede estar a la vez en una hotbar extra y en el radial.
 */
class RadialSlotPicker extends SlotPickerBase {

    private final RadialConfigScreen back;
    private final int cell;

    RadialSlotPicker(ConfigScreen config, RadialConfigScreen back, int cell) {
        super(Component.translatable(LANG + "radial.pick.title", cell + 1), config);
        this.back = back;
        this.cell = cell;
    }

    @Override
    protected boolean showRules() {
        return false;
    }

    @Override
    protected void onCellClicked(int slot) {
        back.setCell(cell, slot);
        minecraft.setScreen(back);
    }

    @Override
    protected void addBottomButtons(int by) {
        Button clear = Button.builder(Component.translatable(LANG + "radial.pick.unassign"), b -> {
            back.setCell(cell, RadialLayout.EMPTY_CELL);
            minecraft.setScreen(back);
        }).bounds(left, by, 86, 20).build();
        clear.active = RadialLayout.assignable(back.cells.get(cell));
        addRenderableWidget(clear);
        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.cancel"), b -> onClose())
                .bounds(left + 90, by, 86, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(back); // cancelar: la casilla queda como estaba
    }

    @Override
    protected Component blockedReason(int slot) {
        Component name = ConfigScreen.slotName(slot);
        if (owner.isOff(slot)) return Component.translatable(LANG + "slot.disabled", name);
        if (!RadialLayout.assignable(slot)) return Component.translatable(LANG + "extra.pick.locked", name);
        for (int i = 0; i < back.cells.size(); i++) {
            if (i != cell && back.cells.get(i) == slot) {
                return Component.translatable(LANG + "radial.pick.used", name, i + 1);
            }
        }
        return null;
    }

    @Override
    protected int currentSlot() {
        return back.cells.get(cell);
    }

    @Override
    protected Component helpText() {
        return Component.translatable(LANG + "radial.pick.help");
    }

    @Override
    protected Component headerText() {
        return Component.translatable(LANG + "radial.pick.title", cell + 1);
    }
}
