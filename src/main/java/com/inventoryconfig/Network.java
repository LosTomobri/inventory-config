package com.inventoryconfig;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = InventoryConfig.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Network {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // Version "9": ademas de las hotbars extra y el intercambio con espejo, reglas de slot (varios items, cada
        // uno con sus datos extra) y menu radial y las opciones de interfaz (crafteo, recetario y Curios); viajan
        // dentro del LayoutBundle (SyncPayload, ConfigActionPayload y perfiles).
        // Version "10": ademas, los slots de Curios que se muestran en el inventario (CuriosLayoutPayload).
        PayloadRegistrar registrar = event.registrar("10");
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.CODEC, SyncPayload::handle);
        registrar.playToClient(ProfilesPayload.TYPE, ProfilesPayload.CODEC, ProfilesPayload::handle);
        registrar.playToServer(ConfigActionPayload.TYPE, ConfigActionPayload.CODEC, ConfigActionPayload::handle);
        registrar.playToServer(ExtraSelectPayload.TYPE, ExtraSelectPayload.CODEC, ExtraSelectPayload::handle);
        registrar.playToClient(ExtraSwapPayload.TYPE, ExtraSwapPayload.CODEC, ExtraSwapPayload::handle);
        registrar.playToClient(CuriosLayoutPayload.TYPE, CuriosLayoutPayload.CODEC, CuriosLayoutPayload::handle);
    }
}
