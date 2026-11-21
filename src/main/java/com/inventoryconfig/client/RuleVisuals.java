package com.inventoryconfig.client;

import com.inventoryconfig.ExtraBar;
import com.inventoryconfig.HiddenSlot;
import com.inventoryconfig.InventoryConfig;
import com.inventoryconfig.InventoryLock;
import com.inventoryconfig.ModState;
import com.inventoryconfig.RuleItem;
import com.inventoryconfig.RulesLayout;
import com.inventoryconfig.SlotRule;
import com.inventoryconfig.SlotRules;
import com.inventoryconfig.Slots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fase 4: pulido visual de las reglas de slot.
 * <ul>
 *   <li><b>Item fantasma:</b> un slot vacio con item fijo muestra ese item atenuado (inventario y contenedores,
 *       hotbar y barras extra); {@link #drawHud} lo dibuja ExtraHud.</li>
 * </ul>
 */
@EventBusSubscriber(modid = InventoryConfig.MODID, value = Dist.CLIENT)
public final class RuleVisuals {

    /** Para que no se arme un ItemStack nuevo en cada cuadro. */
    private static final Map<RuleItem, ItemStack> STACKS = new HashMap<>();

    /** Atenuado del item fantasma sobre el HUD (los slots de la hotbar son oscuros). */
    private static final int HUD_DIM = 0x99202020;

    private RuleVisuals() {
    }

    // ---------------------------------------------------------------- helpers

    static ItemStack stackOf(RuleItem item) {
        return STACKS.computeIfAbsent(item, RuleItem::displayStack);
    }

    /** El item, dibujado y luego tapado con {@code dimArgb} (un color translucido): queda \"apagado\". */
    static void ghost(GuiGraphics g, RuleItem item, int x, int y, int dimArgb) {
        ItemStack stack = stackOf(item);
        if (stack.isEmpty()) return;
        g.renderFakeItem(stack, x, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 180); // por encima del item (que se dibuja a z = 150)
        g.fill(x, y, x + 16, y + 16, dimArgb);
        g.pose().popPose();
    }

    /**
     * Item que muestra la vista previa de una regla. Con varios items se muestra uno solo y va rotando (uno por
     * segundo, cada slot con su desfase): cualquiera de ellos se puede colocar. Null si la regla no tiene items.
     */
    static RuleItem preview(SlotRule rule, int salt) {
        List<RuleItem> items = rule.items();
        if (items.isEmpty()) return null;
        if (items.size() == 1) return items.get(0);
        long step = Util.getMillis() / 1000L;
        return items.get((int) Math.floorMod(step + salt * 7L, (long) items.size()));
    }

    /** Slot del inventario del jugador que se puede mostrar (no apagado ni inactivo), o -1. */
    private static int playerSlot(Minecraft mc, Slot slot) {
        if (slot == null || slot instanceof HiddenSlot || !slot.isActive() || mc.player == null) return -1;
        if (slot.container != mc.player.getInventory()) return -1;
        return Slots.base(slot).getContainerSlot();
    }

    // ---------------------------------------------------------------- inventario / contenedores

    @SubscribeEvent
    public static void onBackground(ContainerScreenEvent.Render.Background event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !InventoryLock.isActive(mc.player)) return;
        SlotRules.Ctx c = SlotRules.ctx(mc.player);
        if (c.isEmpty()) return;
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        // El mismo color del panel con transparencia: el item queda como \"escrito\" en el fondo.
        int dim = (PanelColors.of(screen) & 0xFFFFFF) | 0xB0000000;
        for (Slot slot : screen.getMenu().slots) {
            int cs = playerSlot(mc, slot);
            if (cs < 0 || slot.hasItem()) continue;
            RuleItem id = preview(c.rule(cs), cs);
            if (id != null) ghost(event.getGuiGraphics(), id, left + slot.x, top + slot.y, dim);
        }
    }

    // ---------------------------------------------------------------- HUD

    /**
     * Item fantasma en los slots vacios de la hotbar original y de las barras extra. Lo llama {@link ExtraHud} despues
     * de dibujar las barras (y con el espejo ya devuelto). Se usa el contenido y la regla LOGICOS de cada slot
     * ({@link ExtraMirror}), asi que con un intercambio activo cada slot muestra lo que corresponde.
     */
    static void drawHud(GuiGraphics g, Minecraft mc, int sw, int sh) {
        RulesLayout rules = ModState.rules;
        if (rules.bySlot().isEmpty() || mc.player == null) return;
        Inventory inv = mc.player.getInventory();

        if (!HotbarHud.hidden(mc)) {
            long mask = HotbarSkip.mask(mc);
            int left = sw / 2 - 91;
            for (int m = 0; m < 9; m++) {
                if ((mask >> m & 1L) != 0L || !ExtraMirror.shownHotbar(inv, m).isEmpty()) continue;
                final int x = left + 3 + m * ExtraBarLayout.CELL;
                RuleItem id = preview(rules.get(m), m);
                if (id != null) ghost(g, id, x, sh - 19, HUD_DIM);
            }
        }

        for (ExtraView.View v : ExtraView.views()) {
            int n = v.slots().size();
            int[] r = ExtraBarLayout.rect(v.position(), n, sw, sh);
            if (v.position() == ExtraBar.Position.ABOVE_HOTBAR && HotbarHud.hidden(mc)) r[1] += HotbarHud.SHIFT;
            boolean vertical = v.position().vertical();
            for (int i = 0; i < n; i++) {
                int slot = v.slots().get(i);
                if (!ExtraMirror.shown(inv, slot).isEmpty()) continue;
                int[] c = ExtraBarLayout.cell(r, vertical, i);
                RuleItem id = preview(rules.get(slot), slot);
                if (id != null) ghost(g, id, c[0] + 2, c[1] + 2, HUD_DIM);
            }
        }
    }
}
