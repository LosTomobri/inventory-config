package com.inventoryconfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Fase 3.1: logica comun (cliente y servidor) de las reglas de slot. Lee {@link ModState#rules} en el cliente y
 * {@link ConfigData} en el servidor (el mismo patron que {@link InventoryLock#disabledMask}).
 * <p>
 * Todo se consulta a traves de un {@link Ctx}: una foto de las reglas y del intercambio de {@link ExtraSwap} en ese
 * momento. Mientras hay un intercambio activo, el contenido REAL de la hotbar S y del slot extra E esta cruzado
 * (S tiene lo de E y E lo de S), asi que la regla que corresponde a un slot fisico se busca en el slot logico:
 * S -> regla de E y E -> regla de S (fase 3.6).
 */
public final class SlotRules {

    private SlotRules() {
    }

    /** Reglas de la config y, si lo hay, el intercambio activo del jugador. */
    public record Ctx(RulesLayout rules, int swapHotbar, int swapExtra) {

        public boolean isEmpty() {
            return rules.bySlot().isEmpty();
        }

        public boolean swapActive() {
            return swapHotbar >= 0;
        }

        /** El slot cuya regla le toca al contenido que hay fisicamente en {@code physical}. */
        public int logical(int physical) {
            if (swapHotbar >= 0) {
                if (physical == swapHotbar) return swapExtra;
                if (physical == swapExtra) return swapHotbar;
            }
            return physical;
        }

        public SlotRule rule(int physical) {
            return rules.get(logical(physical));
        }

        /** El item esta reservado: algun slot lo tiene entre sus items fijos y es exclusivo. */
        public boolean reserved(ItemStack stack) {
            for (SlotRule r : rules.bySlot().values()) {
                if (r.exclusive() && r.accepts(stack)) return true;
            }
            return false;
        }

        /**
         * Items fijos + exclusivo: el item puede estar en el slot fisico {@code physical} si la regla de ese slot no
         * pide otros items (con varios, vale cualquiera de ellos) y, si el item esta reservado, si ese slot es uno
         * de los suyos. Vacio siempre se permite.
         */
        public boolean allows(int physical, ItemStack stack) {
            if (stack.isEmpty() || rules.bySlot().isEmpty()) return true;
            SlotRule rule = rule(physical);
            if (rule.hasItems()) return rule.accepts(stack);
            return !reserved(stack);
        }

        /** El slot fisico tiene \"no soltar\". */
        public boolean noDrop(int physical) {
            return rule(physical).noDrop();
        }

        /** Bits de los slots fisicos (0..40) con \"no soltar\". */
        public long noDropMask() {
            long m = 0L;
            for (Map.Entry<Integer, SlotRule> e : rules.bySlot().entrySet()) {
                if (!e.getValue().noDrop()) continue;
                int logical = e.getKey();
                // el contenido fisico de un slot es el de su logico: la inversa del cruce es el mismo cruce
                m |= 1L << logical(logical);
            }
            return m;
        }

        /** El stack es uno de los items fijos del slot fisico (para darle prioridad al guardar). */
        public boolean isFixedForSlot(int physical, ItemStack stack) {
            return rule(physical).accepts(stack);
        }
    }

    public static ResourceLocation idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /** Reglas vigentes para el jugador (en el cliente, las que mando el servidor). */
    public static RulesLayout layout(Player player) {
        if (player.level().isClientSide()) return ModState.rules;
        MinecraftServer server = player.getServer();
        return server == null ? RulesLayout.EMPTY : ConfigData.get(server).rules;
    }

    public static Ctx ctx(Player player) {
        RulesLayout rules = layout(player);
        if (rules.bySlot().isEmpty()) return new Ctx(rules, -1, -1);
        if (player.level().isClientSide()) {
            return new Ctx(rules, ModState.swapHotbar, ModState.swapExtra);
        }
        ExtraSwap.State st = ExtraSwap.get(player);
        return new Ctx(rules, st.hotbar(), st.extra());
    }

    // ---------------------------------------------------------------- guardar items (rule-aware)

    public static boolean canMerge(ItemStack target, ItemStack stack) {
        return !target.isEmpty()
                && target.isStackable()
                && target.getCount() < target.getMaxStackSize()
                && ItemStack.isSameItemSameComponents(target, stack);
    }

    /**
     * Hay donde poner el item: hotbar / principal habilitados que lo acepten (vacios o que se puedan sumar) o la
     * mano secundaria (solo sumando). Un item nunca cuenta un slot que lo rechaza por regla.
     */
    public static boolean fits(Player player, ItemStack stack, long disabledMask) {
        return fits(player, stack, disabledMask, ctx(player));
    }

    private static boolean fits(Player player, ItemStack stack, long disabledMask, Ctx c) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            if ((disabledMask >> i & 1L) != 0L || !c.allows(i, stack)) continue;
            ItemStack t = inv.getItem(i);
            if (t.isEmpty() || canMerge(t, stack)) return true;
        }
        return (disabledMask >> 40 & 1L) == 0L && c.allows(40, stack) && canMerge(inv.getItem(40), stack);
    }

    /**
     * Mueve el item a los slots de hotbar / principal habilitados que lo acepten, sin tocar {@code skip}.
     * Orden: 1) slots que tienen ESE item como item fijo (sumando o en vacio), 2) sumar en cualquier otro permitido,
     * 3) vacios. Lo que no entra queda en {@code stack}.
     */
    public static void insert(Player player, ItemStack stack, long disabledMask, int skip) {
        Ctx c = ctx(player);
        Inventory inv = player.getInventory();
        for (int pass = 0; pass < 3 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < 36 && !stack.isEmpty(); i++) {
                if (i == skip || (disabledMask >> i & 1L) != 0L || !c.allows(i, stack)) continue;
                ItemStack t = inv.getItem(i);
                if (pass == 0) {
                    if (!c.isFixedForSlot(i, stack)) continue;
                    if (!put(inv, i, t, stack)) continue;
                } else if (pass == 1) {
                    if (!mergeInto(t, stack)) continue;
                } else {
                    if (!t.isEmpty()) continue;
                    inv.setItem(i, stack.copy());
                    stack.setCount(0);
                }
            }
        }
    }

    /** Suma al stack del slot o, si esta vacio, lo deja ahi. */
    private static boolean put(Inventory inv, int i, ItemStack target, ItemStack stack) {
        if (target.isEmpty()) {
            inv.setItem(i, stack.copy());
            stack.setCount(0);
            return true;
        }
        return mergeInto(target, stack);
    }

    private static boolean mergeInto(ItemStack target, ItemStack stack) {
        if (!canMerge(target, stack)) return false;
        int move = Math.min(stack.getCount(), target.getMaxStackSize() - target.getCount());
        target.grow(move);
        stack.shrink(move);
        return move > 0;
    }

    // ---------------------------------------------------------------- tick (servidor)

    /**
     * 3.3: lo que {@code mayPlace} no cubre (tecla numerica, F, recoger del suelo, /give, creativo, dispensers...).
     * Todo item que quedo en un slot que lo rechaza se reubica en un slot permitido o, si no hay lugar, se tira.
     * Los slots deshabilitados los atiende {@link InventoryLock} (ya sacan su contenido por su cuenta).
     * <p>
     * Si hay que mover algo de la hotbar S o del slot extra E con un intercambio activo, primero se deshace el
     * intercambio (3.6) y se vuelve a revisar con los items en su lugar.
     */
    public static void enforce(net.minecraft.server.level.ServerPlayer player, long disabledMask) {
        for (int attempt = 0; attempt < 2; attempt++) {
            Ctx c = ctx(player);
            if (c.isEmpty()) return;
            Inventory inv = player.getInventory();
            boolean reverted = false;
            for (int i = 0; i < ConfigData.SLOT_COUNT; i++) {
                if ((disabledMask >> i & 1L) != 0L) continue;
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty() || c.allows(i, stack)) continue;
                if (c.swapActive() && (i == c.swapHotbar() || i == c.swapExtra())) {
                    ExtraSwap.revert(player);
                    reverted = true;
                    break;
                }
                inv.setItem(i, ItemStack.EMPTY);
                insert(player, stack, disabledMask, i);
                if (!stack.isEmpty()) {
                    NoDropGuard.dropInternal(player, stack);
                }
            }
            if (!reverted) return;
        }
    }
}
