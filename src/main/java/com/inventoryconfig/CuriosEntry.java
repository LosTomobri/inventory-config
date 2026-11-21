package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Un slot de Curios que se muestra en el inventario: el tipo ({@code ring}, {@code necklace}...) y cual de sus
 * casillas es. No usa ningun tipo de Curios, asi se puede mandar por red aunque el mod no este instalado.
 */
public record CuriosEntry(String identifier, int index) {

    public static final StreamCodec<ByteBuf, CuriosEntry> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CuriosEntry::identifier,
            ByteBufCodecs.VAR_INT, CuriosEntry::index,
            CuriosEntry::new);
}
