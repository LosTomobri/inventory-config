package com.inventoryconfig;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * /inventoryconfig                       abre la interfaz de configuracion (hay que ser jugador)
 * /inventoryconfig status                muestra el estado actual
 * /inventoryconfig survival [true|false] supervivencia y aventura (por defecto: true)
 * /inventoryconfig creative [true|false] creativo (por defecto: false)
 *
 * Requiere permisos de operador (nivel 2).
 */
@EventBusSubscriber(modid = InventoryConfig.MODID)
public class ModCommand {

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("inventoryconfig")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ConfigData data = ConfigData.get(ctx.getSource().getServer());
                    // Estado al dia y perfiles; el segundo paquete abre la pantalla.
                    PacketDistributor.sendToPlayer(player, InventoryConfig.payload(ctx.getSource().getServer()));
                    PacketDistributor.sendToPlayer(player, new ProfilesPayload(true, new HashMap<>(data.profiles)));
                    return 1;
                })
                .then(Commands.literal("status").executes(ctx -> {
                    ConfigData c = ConfigData.get(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.translatable("inventoryconfig.cmd.status",
                            c.survival, c.creative, Long.bitCount(c.disabledMask), c.profiles.size()), false);
                    return 1;
                }));

        root.then(option("survival", c -> c.survival, (c, v) -> c.survival = v));
        root.then(option("creative", c -> c.creative, (c, v) -> c.creative = v));

        d.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> option(
            String name, Function<ConfigData, Boolean> getter, BiConsumer<ConfigData, Boolean> setter) {
        return Commands.literal(name)
                .executes(ctx -> {
                    boolean value = getter.apply(ConfigData.get(ctx.getSource().getServer()));
                    ctx.getSource().sendSuccess(() -> Component.translatable("inventoryconfig.cmd.value", name, value), false);
                    return value ? 1 : 0;
                })
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean value = BoolArgumentType.getBool(ctx, "value");
                            ConfigData data = ConfigData.get(ctx.getSource().getServer());
                            setter.accept(data, value);
                            data.setDirty();
                            InventoryConfig.sync(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable("inventoryconfig.cmd.set", name, value), true);
                            return 1;
                        }));
    }
}
