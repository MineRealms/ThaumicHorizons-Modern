package fr.wind_blade.isorropia.common.network;

import fr.wind_blade.isorropia.Isorropia;
import net.minecraft.network.FriendlyByteBuf;
//? if forge {
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
//?}

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The single mod channel. Packets register themselves through {@link #register} from {@link #init};
 * ids are assigned in call order, so append new packets at the end of {@link #init}.
 */
public final class IsorropiaNetwork {
    private static final String PROTOCOL = "2";
    //? if forge {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Isorropia.id("main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    //?}
    private static int nextId;

    private IsorropiaNetwork() {
    }

    //? if forge {
    public static void init() {
        register(SyncLivingDataPacket.class, NetworkDirection.PLAY_TO_CLIENT,
                SyncLivingDataPacket::encode, SyncLivingDataPacket::decode, SyncLivingDataPacket::handle);
        register(ClientFxPacket.class, NetworkDirection.PLAY_TO_CLIENT,
                ClientFxPacket::encode, ClientFxPacket::decode, ClientFxPacket::handle);
        register(LensChangePacket.class, NetworkDirection.PLAY_TO_SERVER,
                LensChangePacket::encode, LensChangePacket::decode, LensChangePacket::handle);
        register(OpenFingersPacket.class, NetworkDirection.PLAY_TO_SERVER,
                OpenFingersPacket::encode, OpenFingersPacket::decode, OpenFingersPacket::handle);
        register(SyncInfusionRecipesPacket.class, NetworkDirection.PLAY_TO_CLIENT,
                SyncInfusionRecipesPacket::encode, SyncInfusionRecipesPacket::decode, SyncInfusionRecipesPacket::handle);
        register(ToggleInfusionPacket.class, NetworkDirection.PLAY_TO_SERVER,
                ToggleInfusionPacket::encode, ToggleInfusionPacket::decode, ToggleInfusionPacket::handle);
    }

    public static <M> void register(Class<M> type, NetworkDirection direction, BiConsumer<M, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, M> decoder, BiConsumer<M, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(nextId++, type, encoder, decoder, (message, context) -> {
            handler.accept(message, context);
            context.get().setPacketHandled(true);
        }, Optional.of(direction));
    }
    //?} else {
    /*public static void init(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar(PROTOCOL);
            registrar.playToClient(SyncLivingDataPacket.TYPE, SyncLivingDataPacket.CODEC, SyncLivingDataPacket::handle);
            registrar.playToClient(ClientFxPacket.TYPE, ClientFxPacket.CODEC, ClientFxPacket::handle);
            registrar.playToServer(LensChangePacket.TYPE, LensChangePacket.CODEC, LensChangePacket::handle);
            registrar.playToServer(OpenFingersPacket.TYPE, OpenFingersPacket.CODEC, OpenFingersPacket::handle);
            registrar.playToClient(SyncInfusionRecipesPacket.TYPE, SyncInfusionRecipesPacket.CODEC, SyncInfusionRecipesPacket::handle);
            registrar.playToServer(ToggleInfusionPacket.TYPE, ToggleInfusionPacket.CODEC, ToggleInfusionPacket::handle);
        });
    }
    *///?}

    /** Serverbound helpers, so the client call sites stay loader-agnostic. */
    public static void sendToServer(LensChangePacket packet) {
        //? if forge {
        CHANNEL.sendToServer(packet);
        //?} else {
        /*net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
        *///?}
    }

    public static void sendToServer(OpenFingersPacket packet) {
        //? if forge {
        CHANNEL.sendToServer(packet);
        //?} else {
        /*net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
        *///?}
    }

    public static void sendToServer(ToggleInfusionPacket packet) {
        //? if forge {
        CHANNEL.sendToServer(packet);
        //?} else {
        /*net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
        *///?}
    }
}
