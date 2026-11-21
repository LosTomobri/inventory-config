package com.inventoryconfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Todo lo que forma "un layout" y viaja junto: slots deshabilitados, hotbars extra, reglas de slot y radial.
 * Existe porque {@code StreamCodec.composite} acepta como maximo 6 campos. Es lo que guarda un perfil.
 */
public record LayoutBundle(long mask, ExtraLayout extra, RulesLayout rules, RadialLayout radial, UiOptions ui) {

    public static final StreamCodec<ByteBuf, LayoutBundle> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, LayoutBundle::mask,
            ExtraLayout.CODEC, LayoutBundle::extra,
            RulesLayout.CODEC, LayoutBundle::rules,
            RadialLayout.CODEC, LayoutBundle::radial,
            UiOptions.CODEC, LayoutBundle::ui,
            LayoutBundle::new);

    public static final LayoutBundle EMPTY =
            new LayoutBundle(0L, ExtraLayout.EMPTY, RulesLayout.EMPTY, RadialLayout.EMPTY, UiOptions.EMPTY);

    /** Bundle valido: mascara acotada y todo lo demas limpiado contra esa mascara. El servidor siempre pasa por aca. */
    public static LayoutBundle sanitize(long mask, ExtraLayout extra, RulesLayout rules, RadialLayout radial,
                                        UiOptions ui) {
        long m = mask & ConfigData.ALL;
        ExtraLayout e = extra == null ? ExtraLayout.EMPTY : extra;
        return new LayoutBundle(m,
                ExtraLayout.sanitize(e.bars(), e.hideAssigned(), m),
                RulesLayout.sanitize(rules, m),
                RadialLayout.sanitize(radial, m),
                ui == null ? UiOptions.EMPTY : ui);
    }

    public LayoutBundle sanitized() {
        return sanitize(mask, extra, rules, radial, ui);
    }
}
