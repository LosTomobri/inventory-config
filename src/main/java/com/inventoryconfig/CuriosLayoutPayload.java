package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Servidor -> cliente: que slots de Curios tiene el menu del inventario, en orden (el primero va en el indice
 * {@link CuriosSupport#base} del menu, el siguiente en el que sigue...). El servidor lo manda SIEMPRE antes de
 * cambiar su menu, y los paquetes llegan en orden, asi que el cliente ya tiene los mismos slots cuando le llega
 * cualquier paquete de contenido que los mencione (si no, vanilla falla al buscar un slot que no existe).
 */
public record CuriosLayoutPayload(List<CuriosEntry> entries) implements CustomPacketPayload {

    /** Mas que el maximo de {@link CuriosSupport#WIDE}, con margen. */
    private static final int MAX_ENTRIES = 64;

    public static final CustomPacketPayload.Type<CuriosLayoutPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(InventoryConfig.MODID, "curios_layout"));

    public static final StreamCodec<ByteBuf, CuriosLayoutPayload> CODEC = StreamCodec.composite(
            CuriosEntry.CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), CuriosLayoutPayload::entries,
            CuriosLayoutPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CuriosLayoutPayload payload, IPayloadContext context) {
        // Sin Curios en este cliente no hay nada que armar. El cliente se engancha en CuriosSupport.layoutHook.
        if (!CuriosSupport.loaded()) return;
        CuriosSupport.layoutHook.accept(List.copyOf(payload.entries()));
    }
}
