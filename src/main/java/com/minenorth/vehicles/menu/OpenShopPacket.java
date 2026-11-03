package com.minenorth.vehicles.menu;

import com.minenorth.vehicles.client.MenuClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Serveur -> client : la concession (modèles, couleurs, prix déjà remisés, soldes du joueur). */
public final class OpenShopPacket {
    public record Color(String name, String item, int price) {}

    public record Model(String name, String category, int seats, int trunk, int speed, List<Color> colors) {}

    public final int id;
    public final String title;
    public final long cash, bank;
    /** Message d'erreur de la carte (pas de compte, carte bloquée...), vide si la carte est utilisable. */
    public final String cardError;
    /** Le joueur a déjà un véhicule acheté à poser : l'achat est bloqué. */
    public final boolean waiting;
    public final List<Model> models;

    public OpenShopPacket(int id, String title, long cash, long bank, String cardError, boolean waiting, List<Model> models) {
        this.id = id;
        this.title = title;
        this.cash = cash;
        this.bank = bank;
        this.cardError = cardError;
        this.waiting = waiting;
        this.models = models;
    }

    public static void encode(OpenShopPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.id);
        b.writeUtf(m.title, 256);
        b.writeVarLong(m.cash);
        b.writeVarLong(m.bank);
        b.writeUtf(m.cardError, 256);
        b.writeBoolean(m.waiting);
        b.writeVarInt(m.models.size());
        for (Model md : m.models) {
            b.writeUtf(md.name(), 128);
            b.writeUtf(md.category(), 128);
            b.writeVarInt(md.seats());
            b.writeVarInt(md.trunk());
            b.writeVarInt(md.speed());
            b.writeVarInt(md.colors().size());
            for (Color c : md.colors()) {
                b.writeUtf(c.name(), 64);
                b.writeUtf(c.item(), 256);
                b.writeVarInt(c.price());
            }
        }
    }

    public static OpenShopPacket decode(FriendlyByteBuf b) {
        int id = b.readVarInt();
        String title = b.readUtf(256);
        long cash = b.readVarLong(), bank = b.readVarLong();
        String cardError = b.readUtf(256);
        boolean waiting = b.readBoolean();
        int n = Math.min(512, b.readVarInt());
        List<Model> models = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String name = b.readUtf(128), cat = b.readUtf(128);
            int seats = b.readVarInt(), trunk = b.readVarInt(), speed = b.readVarInt();
            int nc = Math.min(64, b.readVarInt());
            List<Color> colors = new ArrayList<>();
            for (int j = 0; j < nc; j++) colors.add(new Color(b.readUtf(64), b.readUtf(256), b.readVarInt()));
            models.add(new Model(name, cat, seats, trunk, speed, colors));
        }
        return new OpenShopPacket(id, title, cash, bank, cardError, waiting, models);
    }

    public static void handle(OpenShopPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MenuClient.openShop(m)));
        ctx.setPacketHandled(true);
    }
}
