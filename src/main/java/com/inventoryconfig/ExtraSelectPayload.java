package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Cliente -> servidor (fase 6): el jugador solto la tecla de una hotbar extra y eligio {@code slot}
 * (indice de Inventory 9..35), o pide deshacer el intercambio con {@link #REVERT}.
 * El servidor valida todo otra vez: no confia en el cliente.
 */
public record ExtraSelectPayload(int slot) implements CustomPacketPayload {

    /** Deshace el intercambio activo (si lo hay). */
    public static final int REVERT = -1;

    public static final CustomPacketPayload.Type<ExtraSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(InventoryConfig.MODID, "extra_select"));

    public static final StreamCodec<ByteBuf, ExtraSelectPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ExtraSelectPayload::slot,
            ExtraSelectPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ExtraSelectPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ExtraSwap.select(player, payload.slot());
        }
    }
}
