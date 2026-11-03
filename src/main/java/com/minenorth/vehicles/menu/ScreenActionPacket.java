package com.minenorth.vehicles.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : action sur l'écran concession / garage (le serveur revérifie tout). */
public final class ScreenActionPacket {
    // concession
    public static final int BUY_CASH = 1, BUY_CARD = 2, SHOW_GARAGE = 3;
    // garage
    public static final int RETRIEVE = 10, STORE = 11, TRUNK = 12, INSURE = 13, SELL = 14, TOGGLE_MODE = 15;
    // vente entre joueurs
    public static final int SALE_PROPOSE = 20, SALE_ACCEPT = 21, SALE_REFUSE = 22;

    public final int screenId, action, a, b;
    public final String text;

    public ScreenActionPacket(int screenId, int action, int a, int b, String text) {
        this.screenId = screenId;
        this.action = action;
        this.a = a;
        this.b = b;
        this.text = text;
    }

    public static void encode(ScreenActionPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.screenId);
        buf.writeVarInt(m.action);
        buf.writeVarInt(m.a);
        buf.writeVarInt(m.b);
        buf.writeUtf(m.text, 256);
    }

    public static ScreenActionPacket decode(FriendlyByteBuf buf) {
        return new ScreenActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(256));
    }

    public static void handle(ScreenActionPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p != null) MenuNet.handleAction(p, m);
        });
        ctx.setPacketHandled(true);
    }
}
