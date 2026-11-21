package com.inventoryconfig.curios;

import com.inventoryconfig.CuriosEntry;
import com.inventoryconfig.CuriosLayoutPayload;
import com.inventoryconfig.CuriosSupport;
import com.inventoryconfig.InventoryLock;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Slots de Curios dentro del menu del inventario del jugador ({@code player.inventoryMenu}), en cliente y servidor.
 * <p>
 * IMPORTANTE: esta clase usa la API de Curios, asi que solo se puede cargar si el mod esta instalado
 * (se entra siempre por {@link CuriosSupport}, que lo comprueba antes).
 * <p>
 * Como funciona:
 * <ul>
 *   <li>El servidor decide la lista ({@link #wanted}): los slots visibles de Curios, ordenados por el orden de cada
 *       tipo, hasta donde entren. Si cambia, primero se la manda al cliente ({@link CuriosLayoutPayload}) y despues
 *       arma su menu. Asi los dos menus tienen siempre los mismos slots en los mismos indices.</li>
 *   <li>Cada slot es un {@link CurioSlot} de Curios sobre el inventario del jugador, o sea que las reglas (que
 *       acepta, equipar / desequipar, eventos) son exactamente las de la pantalla de Curios.</li>
 *   <li>El cliente puede recibir la lista antes de tener sincronizado el inventario de Curios: mientras tanto pone
 *       slots de relleno ({@link PendingSlot}, apagados) para que el menu tenga el mismo largo que el del servidor, y
 *       los cambia por los reales apenas se pueda ({@link #reconcile} se llama en cada tick).</li>
 * </ul>
 * Los slots se agregan SIEMPRE al final, asi no se mueve ninguno de vanilla ni de otros mods.
 */
public final class CuriosMenu {

    /** Despues de dejar el modo creativo se espera un poco: la pantalla creativa maneja la lista de slots por su cuenta. */
    private static final int CREATIVE_GRACE_TICKS = 20;

    private static final Map<ServerPlayer, Integer> LAST_CREATIVE = new WeakHashMap<>();

    private CuriosMenu() {
    }

    // ---------------------------------------------------------------- servidor

    /**
     * Los slots que tiene que mostrar el menu de este jugador. Vacio si el mod esta apagado para su modo de juego,
     * si "Show Curios Slots" esta apagado o si es creativo (esa pantalla tiene su propio inventario).
     */
    private static List<CuriosEntry> wanted(ServerPlayer player) {
        if (!InventoryLock.isActive(player) || !InventoryLock.showCuriosSlots(player)) return List.of();
        if (player.isCreative()) {
            LAST_CREATIVE.put(player, player.tickCount);
            return List.of();
        }
        Integer last = LAST_CREATIVE.get(player);
        if (last != null && player.tickCount - last < CREATIVE_GRACE_TICKS) return List.of();

        int capacity = InventoryLock.hideCrafting(player) ? CuriosSupport.WIDE : CuriosSupport.NARROW;
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(player);
        if (inventory.isEmpty()) return List.of();

        Map<String, ICurioStacksHandler> curios = inventory.get().getCurios();
        List<String> ids = new ArrayList<>(curios.keySet());
        ids.sort(Comparator.comparingInt((String id) -> order(player, id)).thenComparing(Comparator.naturalOrder()));

        List<CuriosEntry> out = new ArrayList<>();
        for (String id : ids) {
            ICurioStacksHandler handler = curios.get(id);
            if (handler == null || !handler.isVisible()) continue;
            int count = handler.getStacks().getSlots();
            for (int i = 0; i < count && out.size() < capacity; i++) {
                out.add(new CuriosEntry(id, i));
            }
            if (out.size() >= capacity) break;
        }
        return out;
    }

    private static int order(Player player, String id) {
        return CuriosApi.getSlot(id, player.level()).map(ISlotType::getOrder).orElse(Integer.MAX_VALUE);
    }

    /** Un tick del servidor para este jugador: si la lista cambio, se la avisa al cliente y arma su propio menu. */
    public static void tickServer(ServerPlayer player) {
        List<CuriosEntry> want = wanted(player);
        if (!installed(player.inventoryMenu).equals(want)) {
            // Primero el aviso y despues el cambio: ningun paquete del menu nuevo puede llegar antes que este.
            PacketDistributor.sendToPlayer(player, new CuriosLayoutPayload(want));
        }
        reconcile(player, want);
    }

    // ---------------------------------------------------------------- los dos lados

    /**
     * Deja el menu del inventario con exactamente estos slots de Curios al final. No hace nada si ya los tiene
     * (y siguen apuntando al inventario de Curios actual, que se puede reemplazar al sincronizar).
     *
     * @return si hubo que armar los slots de nuevo
     */
    public static boolean reconcile(Player player, List<CuriosEntry> layout) {
        AbstractContainerMenu menu = player.inventoryMenu;
        int base = CuriosSupport.base(menu);
        if (menu.slots.size() < base) return false;

        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(player);
        List<ICurioStacksHandler> handlers = new ArrayList<>(layout.size());
        boolean same = menu.slots.size() == base + layout.size();
        for (int i = 0; i < layout.size(); i++) {
            ICurioStacksHandler handler = resolve(inventory, layout.get(i));
            handlers.add(handler);
            if (same && !matches(menu.slots.get(base + i), layout.get(i), handler, CuriosSupport.x(i), CuriosSupport.y(i))) {
                same = false;
            }
        }
        if (same) return false;

        while (menu.slots.size() > base) {
            int last = menu.slots.size() - 1;
            menu.slots.remove(last);
            menu.lastSlots.remove(last);
            menu.remoteSlots.remove(last);
        }
        for (int i = 0; i < layout.size(); i++) {
            menu.addSlot(create(player, layout.get(i), handlers.get(i), CuriosSupport.x(i), CuriosSupport.y(i)));
        }
        return true;
    }

    /** Los slots de Curios que tiene ahora el menu, en orden. */
    private static List<CuriosEntry> installed(AbstractContainerMenu menu) {
        List<CuriosEntry> out = new ArrayList<>();
        for (int i = CuriosSupport.base(menu); i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot instanceof CurioSlot curio) {
                out.add(new CuriosEntry(curio.getIdentifier(), curio.getSlotIndex()));
            } else if (slot instanceof PendingSlot pending) {
                out.add(pending.entry);
            }
        }
        return out;
    }

    /** El inventario de Curios de ese tipo, o null si todavia no esta (o no tiene tantas casillas). */
    private static ICurioStacksHandler resolve(Optional<ICuriosItemHandler> inventory, CuriosEntry entry) {
        return inventory.flatMap(h -> h.getStacksHandler(entry.identifier()))
                .filter(h -> entry.index() >= 0 && entry.index() < h.getStacks().getSlots())
                .orElse(null);
    }

    private static boolean matches(Slot slot, CuriosEntry entry, ICurioStacksHandler handler, int x, int y) {
        if (slot.x != x || slot.y != y) return false;
        if (handler == null) {
            return slot instanceof PendingSlot pending && pending.entry.equals(entry);
        }
        return slot instanceof CurioSlot curio
                && curio.getItemHandler() == handler.getStacks()
                && curio.getSlotIndex() == entry.index()
                && entry.identifier().equals(curio.getIdentifier());
    }

    private static Slot create(Player player, CuriosEntry entry, ICurioStacksHandler handler, int x, int y) {
        if (handler == null) return new PendingSlot(entry, x, y);
        // Los mismos argumentos que usa el menu de Curios (CuriosContainer): sin boton de cosmeticos.
        return new CurioSlot(player, handler.getStacks(), entry.index(), entry.identifier(), x, y,
                handler.getRenders(), handler.getActiveStates(), handler.canToggleRendering(), false, false);
    }

    // ---------------------------------------------------------------- cliente

    /** Nombre del tipo de un slot de Curios vacio (para el tooltip), o null si no es un slot de Curios. */
    public static Component slotName(Slot slot) {
        return slot instanceof CurioSlot curio ? Component.translatable("curios.identifier." + curio.getIdentifier()) : null;
    }

    /** Relleno de un slot que todavia no se puede armar: ocupa el indice pero no se ve ni se puede usar. */
    private static final class PendingSlot extends Slot {
        private final CuriosEntry entry;

        private PendingSlot(CuriosEntry entry, int x, int y) {
            super(new SimpleContainer(1), 0, x, y);
            this.entry = entry;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }
}
