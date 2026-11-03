package com.minenorth.vehicles.menu;

import com.minenorth.vehicles.client.MenuClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Serveur -> client : vente entre joueurs.
 * Côté vendeur (offer == null) : ses véhicules et les acheteurs possibles, du plus proche au plus loin.
 * Côté acheteur (offer != null) : l'offre reçue, à accepter ou refuser.
 */
public final class OpenSalePacket {
    /** @param index index réel dans le garage du vendeur (renvoyé avec l'id d'item) */
    public record Car(int index, String label, String item, String plate) {}

    /** @param name nom RP (carte d'identité), à défaut le nom affiché du joueur */
    public record Buyer(String uuid, String name, int distance) {}

    public record Offer(String seller, String label, String item, String plate, int price, long balance) {}

    public final int id;
    /** Véhicule présélectionné (celui choisi dans le garage), -1 sinon. */
    public final int preselect;
    public final int maxPrice;
    public final List<Car> cars;
    public final List<Buyer> buyers;
    public final Offer offer;

    public OpenSalePacket(int id, int preselect, int maxPrice, List<Car> cars, List<Buyer> buyers, Offer offer) {
        this.id = id;
        this.preselect = preselect;
        this.maxPrice = maxPrice;
        this.cars = cars;
        this.buyers = buyers;
        this.offer = offer;
    }

    public static void encode(OpenSalePacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.id);
        b.writeVarInt(m.preselect + 1);
        b.writeVarInt(m.maxPrice);
        b.writeVarInt(m.cars.size());
        for (Car c : m.cars) {
            b.writeVarInt(c.index());
            b.writeUtf(c.label(), 128);
            b.writeUtf(c.item(), 256);
            b.writeUtf(c.plate(), 32);
        }
        b.writeVarInt(m.buyers.size());
        for (Buyer by : m.buyers) {
            b.writeUtf(by.uuid(), 64);
            b.writeUtf(by.name(), 64);
            b.writeVarInt(by.distance());
        }
        b.writeBoolean(m.offer != null);
        if (m.offer != null) {
            b.writeUtf(m.offer.seller(), 64);
            b.writeUtf(m.offer.label(), 128);
            b.writeUtf(m.offer.item(), 256);
            b.writeUtf(m.offer.plate(), 32);
            b.writeVarInt(m.offer.price());
            b.writeVarLong(m.offer.balance());
        }
    }

    public static OpenSalePacket decode(FriendlyByteBuf b) {
        int id = b.readVarInt();
        int pre = b.readVarInt() - 1;
        int max = b.readVarInt();
        int n = Math.min(1024, b.readVarInt());
        List<Car> cars = new ArrayList<>();
        for (int i = 0; i < n; i++) cars.add(new Car(b.readVarInt(), b.readUtf(128), b.readUtf(256), b.readUtf(32)));
        int nb = Math.min(64, b.readVarInt());
        List<Buyer> buyers = new ArrayList<>();
        for (int i = 0; i < nb; i++) buyers.add(new Buyer(b.readUtf(64), b.readUtf(64), b.readVarInt()));
        Offer offer = b.readBoolean()
                ? new Offer(b.readUtf(64), b.readUtf(128), b.readUtf(256), b.readUtf(32), b.readVarInt(), b.readVarLong()) : null;
        return new OpenSalePacket(id, pre, max, cars, buyers, offer);
    }

    public static void handle(OpenSalePacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MenuClient.openSale(m)));
        ctx.setPacketHandled(true);
    }
}
