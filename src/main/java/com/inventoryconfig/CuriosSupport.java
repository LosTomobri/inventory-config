package com.inventoryconfig;

import com.inventoryconfig.curios.CuriosMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.fml.ModList;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Parte de la integracion con Curios que NO usa ningun tipo de Curios (se puede cargar sin el mod instalado):
 * si esta cargado, donde va cada slot y el punto de entrada del servidor. Todo lo que si toca la API de Curios
 * esta en {@link CuriosMenu}, que solo se carga despues de comprobar {@link #loaded()}.
 * <p>
 * Los slots van en una columna arriba de la mano secundaria (x = 77; y = 44, 26 y 8; de abajo hacia arriba).
 * Con "Hide Crafting" activado el crafteo deja lugar para cuatro columnas mas a la derecha (15 en total).
 */
public final class CuriosSupport {
    public static final String MODID = "curios";

    /** Posicion del primer slot (el de abajo de la primera columna) y separacion entre slots. */
    private static final int X0 = 77;
    private static final int Y0 = 44;
    private static final int CELL = 18;
    private static final int ROWS = 3;

    /** Cuantos slots entran: solo la columna de arriba de la mano secundaria, o ademas la zona del crafteo. */
    public static final int NARROW = ROWS;
    public static final int WIDE = ROWS * 5;

    /** Lo setea el cliente: llega el layout del servidor. En el servidor no hace nada. */
    public static volatile Consumer<List<CuriosEntry>> layoutHook = entries -> {
    };

    /** Donde empiezan los slots de Curios en cada menu (cuantos slots tenia antes de agregarlos). */
    private static final Map<AbstractContainerMenu, Integer> BASES = Collections.synchronizedMap(new WeakHashMap<>());

    private static Boolean loaded;

    private CuriosSupport() {
    }

    public static boolean loaded() {
        if (loaded == null) {
            ModList list = ModList.get();
            if (list == null) return false;
            loaded = list.isLoaded(MODID);
        }
        return loaded;
    }

    /** Indice del primer slot de Curios del menu (los de antes son los de vanilla). */
    public static int base(AbstractContainerMenu menu) {
        return BASES.computeIfAbsent(menu, m -> m.slots.size());
    }

    /** Posicion x (relativa a la ventana) del slot numero {@code i} de la lista. */
    public static int x(int i) {
        return X0 + i / ROWS * CELL;
    }

    /** Posicion y (relativa a la ventana) del slot numero {@code i} de la lista. */
    public static int y(int i) {
        return Y0 - i % ROWS * CELL;
    }

    /** Lo llama el servidor en cada tick de cada jugador. */
    public static void serverTick(ServerPlayer player) {
        if (!loaded()) return;
        CuriosMenu.tickServer(player);
    }
}
