package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Servidor -> cliente: estado actual de la config (modos activos y el layout completo: slots deshabilitados,
 * hotbars extra, reglas, radial y opciones de interfaz).
 */
public record SyncPayload(boolean survival, boolean creative, LayoutBundle layout)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(InventoryConfig.MODID, "sync"));

    public static final StreamCodec<ByteBuf, SyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncPayload::survival,
            ByteBufCodecs.BOOL, SyncPayload::creative,
            LayoutBundle.CODEC, SyncPayload::layout,
            SyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncPayload payload, IPayloadContext context) {
        LayoutBundle l = payload.layout();
        ModState.survival = payload.survival();
        ModState.creative = payload.creative();
        ModState.disabledMask = l.mask();
        ModState.extra = l.extra();
        ModState.rules = l.rules();
        ModState.radial = l.radial();
        ModState.ui = l.ui();
    }
}
