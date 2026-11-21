package com.inventoryconfig.client;

import com.inventoryconfig.ExtraBar;
import com.inventoryconfig.InventoryLock;
import com.inventoryconfig.ModState;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Las hotbars extra tal como se usan en juego: solo cuentan los slots asignados (las "U" no se dibujan ni se
 * pueden seleccionar), y una barra sin ningun slot asignado no aparece. El numero de cada barra es su orden de
 * creacion (el que dice la tecla), aunque haya barras que no se vean.
 */
final class ExtraView {

    /** @param number 1..8 (orden de creacion); @param slots indices de Inventory (9..35) asignados, en orden. */
    record View(int number, ExtraBar.Position position, List<Integer> slots) {
    }

    private ExtraView() {
    }

    static List<View> views() {
        List<ExtraBar> bars = ModState.extra.bars();
        List<View> out = new ArrayList<>();
        for (int i = 0; i < bars.size(); i++) {
            List<Integer> assigned = new ArrayList<>();
            for (int s : bars.get(i).slots()) {
                if (ExtraBar.assignable(s)) assigned.add(s);
            }
            if (!assigned.isEmpty()) out.add(new View(i + 1, bars.get(i).position(), List.copyOf(assigned)));
        }
        return out;
    }

    static View view(int number) {
        for (View v : views()) {
            if (v.number() == number) return v;
        }
        return null;
    }

    /** Si las hotbars extra aplican al jugador (siguen los interruptores Survival / Creative; espectador nunca). */
    static boolean enabled(Minecraft mc) {
        return mc.player != null && InventoryLock.isActive(mc.player);
    }
}
