package com.inventoryconfig.client;

import com.inventoryconfig.ExtraBar;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Selector de slot de Extra Slots: elige a que slot del inventario apunta un slot de una hotbar extra. Solo se
 * pueden elegir los slots 9-35 que no esten deshabilitados ni asignados ya a otro slot de cualquier barra; el resto
 * aparece bloqueado. Un click asigna el slot y vuelve. Las reglas de slot (item fijo, exclusivo, no soltar) ya no
 * se editan aca: se hacen completas en {@link SlotRulesScreen}.
 */
public class SlotPickerScreen extends SlotPickerBase {

    private final ExtraSlotsScreen parent;
    private final ExtraBar.Position pos;
    private final int index;

    SlotPickerScreen(ExtraSlotsScreen parent, ExtraBar.Position pos, int index) {
        super(Component.translatable(LANG + "extra.pick.title"), parent.owner());
        this.parent = parent;
        this.pos = pos;
        this.index = index;
    }

    @Override
    protected boolean showRules() {
        return false;
    }

    @Override
    protected void onCellClicked(int slot) {
        parent.assign(pos, index, slot);
        minecraft.setScreen(parent);
    }

    @Override
    protected void addBottomButtons(int by) {
        Button unassign = Button.builder(Component.translatable(LANG + "extra.pick.unassign"), b -> unassign())
                .bounds(left, by, 86, 20).build();
        unassign.active = current() != ExtraBar.UNASSIGNED;
        addRenderableWidget(unassign);
        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.cancel"), b -> onClose())
                .bounds(left + 90, by, 86, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent); // cancelar: el lugar queda como estaba
    }

    /** Deja este lugar de la barra sin asignar. */
    private void unassign() {
        parent.assign(pos, index, ExtraBar.UNASSIGNED);
        minecraft.setScreen(parent);
    }

    /** Slot que ya tiene este lugar de la barra (-1 si es "U"). */
    private int current() {
        ExtraBar bar = parent.barAt(pos);
        if (bar == null || index >= bar.slots().size()) return ExtraBar.UNASSIGNED;
        return bar.slots().get(index);
    }

    @Override
    protected int currentSlot() {
        return current();
    }

    @Override
    protected Component blockedReason(int slot) {
        Component name = ConfigScreen.slotName(slot);
        if (!ExtraBar.assignable(slot)) return Component.translatable(LANG + "extra.pick.locked", name);
        if (owner.isOff(slot)) return Component.translatable(LANG + "slot.disabled", name);
        List<ExtraBar> bars = owner.extraBars;
        for (int b = 0; b < bars.size(); b++) {
            List<Integer> slots = bars.get(b).slots();
            for (int i = 0; i < slots.size(); i++) {
                boolean itself = bars.get(b).position() == pos && i == index;
                if (slots.get(i) == slot && !itself) {
                    return Component.translatable(LANG + "extra.pick.used", name, b + 1);
                }
            }
        }
        return null;
    }

    @Override
    protected Component helpText() {
        return Component.translatable(LANG + "extra.pick.help");
    }

    @Override
    protected Component headerText() {
        int barNumber = owner.extraBars.indexOf(parent.barAt(pos)) + 1;
        return Component.translatable(LANG + "extra.pick.title", barNumber, index + 1);
    }
}
