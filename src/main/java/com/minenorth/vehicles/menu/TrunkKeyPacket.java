package com.minenorth.vehicles.menu;

import com.minenorth.vehicles.coffre.Trunk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : appui sur la touche « Coffre du véhicule ». Aucune donnée : le serveur choisit le véhicule et vérifie tout. */
public final class TrunkKeyPacket {
    public TrunkKeyPacket() {}

    public static void encode(TrunkKeyPacket m, FriendlyByteBuf b) {}

    public static TrunkKeyPacket decode(FriendlyByteBuf b) {
        return new TrunkKeyPacket();
    }

    public static void handle(TrunkKeyPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p != null) Trunk.openByKey(p);
        });
        ctx.setPacketHandled(true);
    }
}
