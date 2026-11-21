package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;

/**
 * Cliente -> servidor: lo que se hace desde la pantalla de configuracion. Solo lo acepta un operador
 * (nivel 2): el servidor lo vuelve a comprobar, no alcanza con que el cliente haya podido abrir la pantalla.
 */
public record ConfigActionPayload(int action, String name, boolean survival, boolean creative, LayoutBundle bundle)
        implements CustomPacketPayload {

    /** Aplica survival / creative / bundle (mascara, extra, reglas, radial) al servidor (todos los jugadores). */
    public static final int APPLY = 0;
    /** Guarda el bundle con el nombre dado como perfil. */
    public static final int SAVE_PROFILE = 1;
    /** Borra el perfil con ese nombre. */
    public static final int DELETE_PROFILE = 2;

    public static final CustomPacketPayload.Type<ConfigActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(InventoryConfig.MODID, "action"));

    public static final StreamCodec<ByteBuf, ConfigActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ConfigActionPayload::action,
            ByteBufCodecs.STRING_UTF8, ConfigActionPayload::name,
            ByteBufCodecs.BOOL, ConfigActionPayload::survival,
            ByteBufCodecs.BOOL, ConfigActionPayload::creative,
            LayoutBundle.CODEC, ConfigActionPayload::bundle,
            ConfigActionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConfigActionPayload p, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.translatable("inventoryconfig.msg.no_permission"));
            return;
        }
        MinecraftServer server = player.server;
        ConfigData data = ConfigData.get(server);
        // El servidor no confia en el cliente: valida y limpia TODO el layout contra su propia mascara.
        LayoutBundle bundle = p.bundle().sanitized();
        long mask = bundle.mask();
        String name = p.name() == null ? "" : p.name().trim();

        switch (p.action()) {
            case APPLY -> {
                // Con la config nueva los slots de un intercambio activo podrian dejar de valer: se deshacen antes.
                ExtraSwap.revertAll(server);
                data.survival = p.survival();
                data.creative = p.creative();
                data.setBundle(bundle);
                data.setDirty();
                InventoryConfig.sync(server);
                player.sendSystemMessage(Component.translatable("inventoryconfig.msg.saved", Long.bitCount(mask)));
            }
            case SAVE_PROFILE -> {
                if (name.isEmpty() || name.length() > ConfigData.MAX_NAME) {
                    player.sendSystemMessage(Component.translatable("inventoryconfig.msg.invalid_name"));
                    return;
                }
                if (!data.profiles.containsKey(name) && data.profiles.size() >= ConfigData.MAX_PROFILES) {
                    player.sendSystemMessage(Component.translatable("inventoryconfig.msg.too_many_profiles"));
                    return;
                }
                // El layout ya viene validado igual que en APPLY, contra la mascara del perfil.
                data.profiles.put(name, new Profile(bundle));
                data.setDirty();
                sendProfiles(player, data);
                player.sendSystemMessage(Component.translatable("inventoryconfig.msg.profile_saved", name));
            }
            case DELETE_PROFILE -> {
                if (data.profiles.remove(name) != null) {
                    data.setDirty();
                    sendProfiles(player, data);
                    player.sendSystemMessage(Component.translatable("inventoryconfig.msg.profile_deleted", name));
                }
            }
            default -> {
            }
        }
    }

    public static void sendProfiles(ServerPlayer player, ConfigData data) {
        PacketDistributor.sendToPlayer(player, new ProfilesPayload(false, new HashMap<>(data.profiles)));
    }
}
