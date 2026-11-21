package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Servidor -> cliente (fase 6): intercambio activo del jugador. {@code hotbar} es el slot de la hotbar original
 * que tiene el item extra (0..8) y {@code extra} el slot extra (9..35) que tiene el item de la hotbar;
 * -1 y -1 = no hay intercambio. El cliente lo usa para dibujar el espejo.
 */
public record ExtraSwapPayload(int hotbar, int extra) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ExtraSwapPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(InventoryConfig.MODID, "extra_swap"));

    public static final StreamCodec<ByteBuf, ExtraSwapPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ExtraSwapPayload::hotbar,
            ByteBufCodecs.VAR_INT, ExtraSwapPayload::extra,
            ExtraSwapPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ExtraSwapPayload payload, IPayloadContext context) {
        ModState.swapHotbar = payload.hotbar();
        ModState.swapExtra = payload.extra();
    }
}
