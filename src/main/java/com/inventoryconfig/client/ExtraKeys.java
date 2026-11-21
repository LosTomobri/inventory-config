package com.inventoryconfig.client;

import com.inventoryconfig.ExtraBar;
import com.inventoryconfig.InventoryConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Una tecla por barra (hasta 8) en la categoria "Inventory Config" de Controls. Por defecto son los numeros 1..8,
 * igual que las de la hotbar de vanilla; para que Controls no las marque en rojo tienen un contexto de conflicto
 * propio que no choca con nadie. El jugador las puede cambiar.
 */
@EventBusSubscriber(modid = InventoryConfig.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ExtraKeys {

    static final String CATEGORY = "key.categories.inventoryconfig";

    /** Activo en juego (sin pantallas), pero solo "choca" con si mismo. */
    static final IKeyConflictContext CONTEXT = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    /** MAPPINGS[n - 1] es la tecla de la barra n. */
    static final KeyMapping[] MAPPINGS = new KeyMapping[ExtraBar.Position.VALUES.length];

    /**
     * Tecla del Radial Menu. Por defecto es la de \"<\": en teclados ISO (espanol / latam) es la tecla extra a la
     * izquierda de la Z y GLFW la reporta como WORLD_2; en teclados US no existe y se reasigna en Controls.
     */
    static final KeyMapping RADIAL = new KeyMapping("key." + InventoryConfig.MODID + ".radial", CONTEXT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_WORLD_2, CATEGORY);

    static {
        for (int i = 0; i < MAPPINGS.length; i++) {
            MAPPINGS[i] = new KeyMapping("key." + InventoryConfig.MODID + ".extra_bar_" + (i + 1), CONTEXT,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_1 + i, CATEGORY);
        }
    }

    private ExtraKeys() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        for (KeyMapping m : MAPPINGS) event.register(m);
        event.register(RADIAL);
    }
}
