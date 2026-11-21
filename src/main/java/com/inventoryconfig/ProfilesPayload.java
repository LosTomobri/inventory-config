package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Servidor -> cliente: lista de perfiles (nombre -> slots deshabilitados + hotbars extra).
 * Si {@code open} es true, ademas abre la pantalla de configuracion (lo manda /inventoryconfig).
 */
public record ProfilesPayload(boolean open, Map<String, Profile> profiles) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfilesPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(InventoryConfig.MODID, "profiles"));

    private static final StreamCodec<ByteBuf, Map<String, Profile>> MAP_CODEC =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, Profile.CODEC);

    public static final StreamCodec<ByteBuf, ProfilesPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ProfilesPayload::open,
            MAP_CODEC, ProfilesPayload::profiles,
            ProfilesPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ProfilesPayload payload, IPayloadContext context) {
        ModState.profiles = new TreeMap<>(payload.profiles());
        ModState.configHook.accept(payload.open());
    }
}
