package com.inventoryconfig;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(InventoryConfig.MODID)
public class InventoryConfig {
    public static final String MODID = "inventoryconfig";

    /*
     * /inventoryconfig abre la interfaz de configuracion (solo operadores): se eligen los slots a
     * deshabilitar y se guarda. Vale para todos los jugadores del servidor y se guarda por mundo
     * (ver ConfigData). Tambien:
     *   /inventoryconfig survival true|false   aplica a supervivencia y aventura (por defecto: true)
     *   /inventoryconfig creative true|false   aplica a creativo (por defecto: false)
     */

    public InventoryConfig(ModContainer container) {
        // Solo cuenta en el cliente (el umbral de "mantener tecla" de las hotbars extra).
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    public static boolean survivalOn(MinecraftServer server) {
        return ConfigData.get(server).survival;
    }

    public static boolean creativeOn(MinecraftServer server) {
        return ConfigData.get(server).creative;
    }

    /** Bit i = slot i del inventario deshabilitado. */
    public static long disabledMask(MinecraftServer server) {
        return ConfigData.get(server).disabledMask;
    }

    /** Estado actual de la config, tal como se manda a los clientes. */
    public static SyncPayload payload(MinecraftServer server) {
        ConfigData data = ConfigData.get(server);
        return new SyncPayload(data.survival, data.creative, data.bundle());
    }

    /** Avisa a todos los clientes del estado actual. */
    public static void sync(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(payload(server));
    }
}
