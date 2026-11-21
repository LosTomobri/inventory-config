package com.inventoryconfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = InventoryConfig.MODID)
public class InventoryLock {

    /** Mascara de slots deshabilitados (en el cliente, la que mando el servidor). */
    public static long disabledMask(Player player) {
        if (player.level().isClientSide()) return ModState.disabledMask;
        MinecraftServer server = player.getServer();
        return server == null ? 0L : InventoryConfig.disabledMask(server);
    }

    /**
     * Si el slot esta deshabilitado en la config.
     * Indices de Inventory: 0-8 hotbar, 9-35 principal, 36-39 armadura, 40 offhand.
     */
    public static boolean isDisabled(Player player, int containerSlot) {
        if (containerSlot < 0 || containerSlot >= ConfigData.SLOT_COUNT) return false;
        return (disabledMask(player) >> containerSlot & 1L) != 0L;
    }

    /**
     * Si el mod aplica a este jugador segun su modo de juego:
     * creativo usa el flag de creativo; supervivencia y aventura usan el de supervivencia;
     * espectador nunca.
     */
    public static boolean isActive(Player player) {
        if (player.isSpectator()) return false;
        boolean creative = player.isCreative();
        if (player.level().isClientSide()) {
            return creative ? ModState.creative : ModState.survival;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        return creative ? InventoryConfig.creativeOn(server) : InventoryConfig.survivalOn(server);
    }

    /**
     * Slots asignados (hotbars extra, radial o items fijos de las reglas) que se ocultan del inventario
     * ("Hide in Inventory"); 0 si el interruptor esta apagado. Se ocultan igual que los deshabilitados (HiddenSlot) pero NO se expulsan sus items y siguen
     * funcionando en la hotbar extra.
     */
    public static long extraHiddenMask(Player player) {
        if (player.level().isClientSide()) {
            return ConfigData.hiddenMask(ModState.extra, ModState.radial, ModState.rules);
        }
        MinecraftServer server = player.getServer();
        return server == null ? 0L : ConfigData.get(server).extraHiddenMask();
    }

    /** Si el crafteo del inventario esta oculto por la config ("Hide Crafting"). */
    public static boolean hideCrafting(Player player) {
        if (player.level().isClientSide()) return ModState.ui.hideCrafting();
        MinecraftServer server = player.getServer();
        return server != null && ConfigData.get(server).ui.hideCrafting();
    }

    /** Si el recetario del inventario esta oculto por la config ("Hide Recipes"). */
    public static boolean hideRecipes(Player player) {
        if (player.level().isClientSide()) return ModState.ui.hideRecipes();
        MinecraftServer server = player.getServer();
        return server != null && ConfigData.get(server).ui.hideRecipes();
    }

    /** Si los slots de Curios se muestran en el inventario por la config ("Show Curios Slots"). */
    public static boolean showCuriosSlots(Player player) {
        if (player.level().isClientSide()) return ModState.ui.showCuriosSlots();
        MinecraftServer server = player.getServer();
        return server != null && ConfigData.get(server).ui.showCuriosSlots();
    }

    /**
     * Los 5 slots de crafteo del inventario del jugador (0 = resultado, 1..4 = grilla 2x2) se apagan igual que
     * los deshabilitados (HiddenSlot): no se ven, no se clickean y no aceptan ni entregan items.
     */
    private static void updateCraftingSlots(AbstractContainerMenu menu, boolean hide) {
        for (int i = 0; i < 5 && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (hide) {
                if (!(slot instanceof HiddenSlot)) menu.slots.set(i, new HiddenSlot(slot));
            } else if (slot instanceof HiddenSlot h) {
                menu.slots.set(i, h.original);
            }
        }
    }

    /** Pone (o quita) los slots ocultos / subidos en el inventario del jugador y en el menu abierto. */
    public static void applySlots(Player player) {
        Inventory inv = player.getInventory();
        boolean active = isActive(player);
        long extraHidden = active ? extraHiddenMask(player) : 0L;

        updateSlots(player.inventoryMenu, inv, active, extraHidden);
        updateCraftingSlots(player.inventoryMenu, active && hideCrafting(player));
        AbstractContainerMenu open = player.containerMenu;
        if (open != player.inventoryMenu) {
            updateSlots(open, inv, active, extraHidden);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        // Cliente y servidor.
        applySlots(player);

        // Solo servidor.
        if (!(player instanceof ServerPlayer sp)) return;
        // Slots de Curios en el inventario: los agrega, y tambien los saca si el mod o la opcion se apagan.
        CuriosSupport.serverTick(sp);
        if (!isActive(sp)) {
            NoDropGuard.tick(sp); // limpia la foto de \"No soltar\"
            return;
        }
        long mask = disabledMask(sp);
        Inventory inv = sp.getInventory();

        // Items en slots deshabilitados -> otro slot habilitado (que los acepte) o al suelo.
        if (mask != 0L) {
            // Mientras dura un intercambio con espejo, el slot de la hotbar tiene el item extra: no se expulsa.
            int swapped = ExtraSwap.get(sp).hotbar();
            for (int i = 0; i < ConfigData.SLOT_COUNT; i++) {
                if ((mask >> i & 1L) == 0L || i == swapped) continue;
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;
                inv.setItem(i, ItemStack.EMPTY);
                SlotRules.insert(sp, stack, mask, i);
                if (!stack.isEmpty()) {
                    NoDropGuard.dropInternal(sp, stack);
                }
            }
        }

        // Fase 3.3: lo que quedo en un slot que lo rechaza por regla (item fijo / exclusivo) se reubica.
        SlotRules.enforce(sp, mask);
        // Fase 3.5: foto de los slots con \"No soltar\" para saber de donde sale lo que se tira.
        NoDropGuard.tick(sp);
    }

    /** Inventario antes de recoger (cantidades por slot), para saber donde puso vanilla lo recogido. */
    private static final java.util.Map<java.util.UUID, int[]> BEFORE_PICKUP = new java.util.HashMap<>();

    /**
     * Evita recoger del suelo cuando no hay lugar (si no, el item se tiraria en bucle). Con reglas, \"hay lugar\"
     * solo cuenta los slots que aceptan el item: un item exclusivo solo se recoge si entra en su slot reservado.
     */
    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        BEFORE_PICKUP.remove(player.getUUID()); // por si un Pre anterior no llego a su Post (lo cancelo otro mod)
        if (!isActive(player)) return;
        ItemStack stack = event.getItemEntity().getItem();
        if (!SlotRules.fits(player, stack, disabledMask(player))) {
            event.setCanPickup(TriState.FALSE);
            return;
        }
        // Prioridad del slot fijo (3.4): se anota el inventario para redirigir lo recogido en el Post.
        if (!player.level().isClientSide() && hasFixedSlotFor(player, stack)) {
            int[] counts = new int[ConfigData.SLOT_COUNT];
            Inventory inv = player.getInventory();
            for (int i = 0; i < counts.length; i++) counts[i] = inv.getItem(i).getCount();
            BEFORE_PICKUP.put(player.getUUID(), counts);
        }
    }

    /**
     * 3.4: vanilla pone lo recogido en el primer slot libre; si el item es el item fijo de algun slot, lo recogido
     * se mueve ahi primero (como un auto-refill). Solo lo que se acaba de recoger: no se toca el resto.
     * Lo que el slot fijo no puede absorber (esta lleno) se queda donde cayo, salvo que el item sea exclusivo:
     * eso lo corrige {@link SlotRules#enforce} en el mismo tick.
     */
    @SubscribeEvent
    public static void onPickupPost(ItemEntityPickupEvent.Post event) {
        Player player = event.getPlayer();
        int[] before = BEFORE_PICKUP.remove(player.getUUID());
        if (before == null || !(player instanceof ServerPlayer sp) || !isActive(sp)) return;
        ItemStack picked = event.getOriginalStack();
        if (picked.isEmpty()) return;

        SlotRules.Ctx c = SlotRules.ctx(sp);
        Inventory inv = sp.getInventory();
        long mask = disabledMask(sp);
        for (int src = 0; src < 36; src++) {
            ItemStack at = inv.getItem(src);
            if (at.isEmpty() || !ItemStack.isSameItemSameComponents(at, picked)) continue;
            if (c.isFixedForSlot(src, picked)) continue; // ya esta en un slot fijo
            int gained = at.getCount() - before[src];
            if (gained <= 0) continue;
            // el slot fijo de destino: el primero que lo acepte y tenga lugar
            for (int dst = 0; dst < 36 && gained > 0; dst++) {
                if (dst == src || (mask >> dst & 1L) != 0L || !c.isFixedForSlot(dst, picked)) continue;
                ItemStack t = inv.getItem(dst);
                int room;
                if (t.isEmpty()) {
                    room = at.getMaxStackSize();
                } else if (SlotRules.canMerge(t, at)) {
                    room = t.getMaxStackSize() - t.getCount();
                } else {
                    continue;
                }
                int move = Math.min(room, gained);
                if (move <= 0) continue;
                if (t.isEmpty()) {
                    ItemStack part = at.copyWithCount(move);
                    inv.setItem(dst, part);
                } else {
                    t.grow(move);
                }
                at.shrink(move);
                gained -= move;
            }
            if (at.isEmpty()) inv.setItem(src, ItemStack.EMPTY);
        }
    }

    /** Algun slot (habilitado) tiene este item como item fijo. */
    private static boolean hasFixedSlotFor(Player player, ItemStack stack) {
        SlotRules.Ctx c = SlotRules.ctx(player);
        if (c.isEmpty()) return false;
        long mask = disabledMask(player);
        for (int i = 0; i < 36; i++) {
            if ((mask >> i & 1L) == 0L && c.isFixedForSlot(i, stack)) return true;
        }
        return false;
    }

    /**
     * Deja cada slot del inventario del jugador como corresponde: apagado (HiddenSlot), subido
     * (ShiftedSlot, segun lo que diga {@link ModState#slotShift}) o el original.
     */
    private static void updateSlots(AbstractContainerMenu menu, Inventory inv, boolean active, long extraHidden) {
        // Con reglas hay que envolver TODOS los slots del jugador (un item exclusivo se rechaza hasta en los que no
        // tienen regla); sin reglas solo los que hay que subir.
        boolean rules = active && !SlotRules.layout(inv.player).bySlot().isEmpty();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container != inv) continue;
            Slot base = Slots.base(slot);

            if (!active) {
                if (slot != base) menu.slots.set(i, base);
                continue;
            }
            int cs = base.getContainerSlot();
            if (isDisabled(inv.player, cs) || (cs >= 0 && cs < 64 && (extraHidden >> cs & 1L) != 0L)) {
                if (!(slot instanceof HiddenSlot)) menu.slots.set(i, new HiddenSlot(base));
                continue;
            }
            int wanted = Math.max(0, ModState.slotShift.dy(menu, base));
            boolean wrap = wanted > 0 || rules;
            boolean wrapped = slot instanceof ShiftedSlot;
            int current = slot instanceof ShiftedSlot s ? s.dy : 0;
            if (wrap != wrapped || (wrap && wanted != current) || slot instanceof HiddenSlot) {
                // (un slot que estaba oculto y ahora se habilito vuelve a su posicion)
                menu.slots.set(i, wrap ? new ShiftedSlot(base, wanted) : base);
            }
        }
    }
}
