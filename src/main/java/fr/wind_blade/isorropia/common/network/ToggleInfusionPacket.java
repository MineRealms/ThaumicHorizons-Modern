package fr.wind_blade.isorropia.common.network;

import fr.wind_blade.isorropia.common.capabilities.LivingData;
import fr.wind_blade.isorropia.common.infusion.SelfInfusions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
//? if forge {
import net.minecraftforge.network.NetworkEvent;
//?}
import java.util.function.Supplier;

/**
 * Flips one of TH's infusion toggles: the spider climb and the chameleon skin both stay on until the player
 * switches them off with their keys (upstream's keyC and keyX). The server owns the flag, so the client only
 * says which toggle it pressed.
 */
//? if forge {
public record ToggleInfusionPacket(int kind) {
//?} else {
/*public record ToggleInfusionPacket(int kind) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<ToggleInfusionPacket> TYPE =
            new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(fr.wind_blade.isorropia.Isorropia.id("toggle_infusion"));
    public static final net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, ToggleInfusionPacket> CODEC =
            net.minecraft.network.codec.StreamCodec.ofMember(ToggleInfusionPacket::encode, ToggleInfusionPacket::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<ToggleInfusionPacket> type() {
        return TYPE;
    }
*///?}
    public static void encode(ToggleInfusionPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.kind);
    }

    public static ToggleInfusionPacket decode(FriendlyByteBuf buf) {
        return new ToggleInfusionPacket(buf.readVarInt());
    }

    //? if forge {
    public static void handle(ToggleInfusionPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                apply(player, packet.kind);
            }
        });
    }
    //?} else {
    /*public static void handle(ToggleInfusionPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                apply(player, packet.kind);
            }
        });
    }
    *///?}

    public static final int CLIMB = 0;
    public static final int CHAMELEON = 1;

    private static void apply(ServerPlayer player, int kind) {
        LivingData data = LivingData.get(player).orElse(null);
        if (data == null) {
            return;
        }
        if (kind == CLIMB && data.hasSelfInfusion(SelfInfusions.SPIDER_CLIMB)) {
            data.setClimbDisabled(!data.isClimbDisabled());
            data.sync();
        } else if (kind == CHAMELEON && data.hasSelfInfusion(SelfInfusions.CHAMELEON_SKIN)) {
            data.setInvisibilityDisabled(!data.isInvisibilityDisabled());
            data.sync();
        }
    }
}
