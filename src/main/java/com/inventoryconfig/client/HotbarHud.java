package com.inventoryconfig.client;

import com.inventoryconfig.InventoryConfig;
import com.inventoryconfig.ModState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Set;

/**
 * La hotbar original en juego cuando hay slots deshabilitados:
 * <ul>
 *   <li>los slots deshabilitados no se dibujan (los demas quedan en su lugar, asi la vida / hambre siguen alineadas
 *       y la tecla N sigue siendo la N-esima posicion);</li>
 *   <li>si estan los 9 deshabilitados, la hotbar desaparece y la barra de experiencia, la vida, el hambre, el aire,
 *       la barra de salto y el nombre del item bajan al lugar que ocupaba.</li>
 * </ul>
 * Con ningun slot deshabilitado no se toca nada: se ve la hotbar de vanilla.
 */
@EventBusSubscriber(modid = InventoryConfig.MODID, value = Dist.CLIENT)
public final class HotbarHud {

    /** Alto de la hotbar de vanilla: lo que baja todo cuando no hay hotbar. */
    static final int SHIFT = ExtraBarLayout.HOTBAR_H;

    private static final ResourceLocation SELECTION = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
    private static final ResourceLocation OFFHAND_LEFT = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_left");
    private static final ResourceLocation OFFHAND_RIGHT = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_right");
    private static final ResourceLocation ATTACK_BG =
            ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_background");
    private static final ResourceLocation ATTACK_PROGRESS =
            ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_progress");

    /** Capas de vanilla que van pegadas a la hotbar y bajan cuando esta no esta. */
    private static final Set<ResourceLocation> SHIFTED = Set.of(
            ResourceLocation.withDefaultNamespace("player_health"),
            ResourceLocation.withDefaultNamespace("armor_level"),
            ResourceLocation.withDefaultNamespace("food_level"),
            ResourceLocation.withDefaultNamespace("air_level"),
            ResourceLocation.withDefaultNamespace("vehicle_health"),
            ResourceLocation.withDefaultNamespace("jump_meter"),
            ResourceLocation.withDefaultNamespace("experience_bar"),
            ResourceLocation.withDefaultNamespace("experience_level"),
            ResourceLocation.withDefaultNamespace("selected_item_name"));

    /** Se hizo pushPose en la capa actual (hay que hacer popPose en Post). */
    private static boolean pushed;

    private HotbarHud() {
    }

    /** Si la hotbar original no se dibuja (los 9 slots deshabilitados). Lo usa tambien la hotbar extra ABOVE_HOTBAR. */
    static boolean hidden(Minecraft mc) {
        return HotbarSkip.allDisabled(mc);
    }

    // ---------------------------------------------------------------- hotbar

    @SubscribeEvent
    public static void onHotbar(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        Minecraft mc = Minecraft.getInstance();
        long mask = HotbarSkip.mask(mc);
        if (mask == 0L || mc.options.hideGui || mc.player == null || !(mc.getCameraEntity() instanceof Player)) return;
        draw(event.getGuiGraphics(), event.getPartialTick(), mc, mask);
        event.setCanceled(true); // (tampoco llega el espejo de ExtraMirror: ya se dibuja con el espejo puesto)
    }

    private static void draw(GuiGraphics g, DeltaTracker dt, Minecraft mc, long mask) {
        Player player = mc.player;
        Inventory inv = player.getInventory();
        int cx = g.guiWidth() / 2;
        int sh = g.guiHeight();
        int left = cx - 91;
        ItemStack offhand = player.getOffhandItem();
        HumanoidArm arm = player.getMainArm().getOpposite();

        g.pose().pushPose();
        g.pose().translate(0, 0, -90);
        // Fondo: un tramo por cada grupo de slots habilitados seguidos.
        int i = 0;
        while (i < 9) {
            if ((mask >> i & 1L) != 0L) {
                i++;
                continue;
            }
            int j = i;
            while (j < 9 && (mask >> j & 1L) == 0L) j++;
            ExtraHud.strip(g, left + i * ExtraBarLayout.CELL, sh - ExtraBarLayout.HOTBAR_H, j - i);
            i = j;
        }
        int selected = inv.selected;
        if (selected >= 0 && selected < 9 && (mask >> selected & 1L) == 0L) {
            g.blitSprite(SELECTION, left - 1 + selected * ExtraBarLayout.CELL, sh - ExtraBarLayout.HOTBAR_H - 1, 24, 23);
        }
        boolean offhandOn = !offhand.isEmpty() && (ModState.disabledMask >> 40 & 1L) == 0L;
        if (offhandOn) {
            if (arm == HumanoidArm.LEFT) {
                g.blitSprite(OFFHAND_LEFT, left - 29, sh - 23, 29, 24);
            } else {
                g.blitSprite(OFFHAND_RIGHT, cx + 91, sh - 23, 29, 24);
            }
        }
        g.pose().popPose();

        int seed = 1;
        for (int m = 0; m < 9; m++) {
            if ((mask >> m & 1L) != 0L) continue;
            renderSlot(g, dt, mc, left + 3 + m * ExtraBarLayout.CELL, sh - 19, ExtraMirror.shownHotbar(inv, m), seed++);
        }
        if (offhandOn) {
            int x = arm == HumanoidArm.LEFT ? left - 26 : cx + 91 + 10;
            renderSlot(g, dt, mc, x, sh - 19, offhand, seed);
        }

        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            float f = player.getAttackStrengthScale(0.0F);
            if (f < 1.0F) {
                int y = sh - 20;
                int x = arm == HumanoidArm.RIGHT ? left - 22 : cx + 91 + 6;
                int r = (int) (f * 19.0F);
                RenderSystem.enableBlend();
                g.blitSprite(ATTACK_BG, x, y, 18, 18);
                g.blitSprite(ATTACK_PROGRESS, 18, 18, 0, 18 - r, x, y + 18 - r, 18, r);
                RenderSystem.disableBlend();
            }
        }
    }

    /** Como el slot de la hotbar de vanilla, con la animacion de "recien recogido". */
    private static void renderSlot(GuiGraphics g, DeltaTracker dt, Minecraft mc, int x, int y, ItemStack stack, int seed) {
        if (stack.isEmpty()) return;
        float pop = stack.getPopTime() - dt.getGameTimeDeltaPartialTick(false);
        if (pop > 0.0F) {
            float s = 1.0F + pop / 5.0F;
            g.pose().pushPose();
            g.pose().translate(x + 8, y + 12, 0);
            g.pose().scale(1.0F / s, (s + 1.0F) / 2.0F, 1.0F);
            g.pose().translate(-(x + 8), -(y + 12), 0);
        }
        g.renderItem(stack, x, y, seed);
        if (pop > 0.0F) g.pose().popPose();
        g.renderItemDecorations(mc.font, stack, x, y);
    }

    // ---------------------------------------------------------------- lo que baja cuando no hay hotbar

    /** LOWEST: despues de que cualquiera pudo cancelar la capa (si se cancela no hay Post y el pushPose quedaria sin pop). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onShiftPre(RenderGuiLayerEvent.Pre event) {
        pushed = false;
        if (!SHIFTED.contains(event.getName())) return;
        Minecraft mc = Minecraft.getInstance();
        if (!hidden(mc)) return;
        event.getGuiGraphics().pose().pushPose();
        event.getGuiGraphics().pose().translate(0, SHIFT, 0);
        pushed = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onShiftPost(RenderGuiLayerEvent.Post event) {
        if (!pushed) return;
        pushed = false;
        event.getGuiGraphics().pose().popPose();
    }
}
