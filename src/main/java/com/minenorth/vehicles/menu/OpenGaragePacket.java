package com.minenorth.vehicles.menu;

import com.minenorth.vehicles.client.MenuClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Serveur -> client : le garage du joueur (véhicules rangés + actions disponibles ici). */
public final class OpenGaragePacket {
    /**
     * @param index index réel dans la liste du garage (renvoyé tel quel avec l'id d'item pour la sortie)
     * @param type  nom de {@link com.minenorth.vehicles.garage.VehicleType}
     * @param here  ce garage peut sortir ce véhicule
     * @param insurePrice prix (remisé) pour l'assurer
     */
    public record Car(int index, String label, String item, String type, boolean insured, boolean here, int insurePrice) {}

    public final int id, max;
    public final String title;
    /** Le joueur est dans une zone garage : sortie / rangement possibles. Sinon consultation seule. */
    public final boolean inZone;
    /** Garage de service : pas d'assurance ni de vente. */
    public final boolean service;
    public final boolean handMode;
    public final List<Car> cars;

    public OpenGaragePacket(int id, String title, int max, boolean inZone, boolean service, boolean handMode, List<Car> cars) {
        this.id = id;
        this.title = title;
        this.max = max;
        this.inZone = inZone;
        this.service = service;
        this.handMode = handMode;
        this.cars = cars;
    }

    public static void encode(OpenGaragePacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.id);
        b.writeUtf(m.title, 256);
        b.writeVarInt(m.max);
        b.writeBoolean(m.inZone);
        b.writeBoolean(m.service);
        b.writeBoolean(m.handMode);
        b.writeVarInt(m.cars.size());
        for (Car c : m.cars) {
            b.writeVarInt(c.index());
            b.writeUtf(c.label(), 128);
            b.writeUtf(c.item(), 256);
            b.writeUtf(c.type(), 16);
            b.writeBoolean(c.insured());
            b.writeBoolean(c.here());
            b.writeVarInt(c.insurePrice());
        }
    }

    public static OpenGaragePacket decode(FriendlyByteBuf b) {
        int id = b.readVarInt();
        String title = b.readUtf(256);
        int max = b.readVarInt();
        boolean inZone = b.readBoolean(), service = b.readBoolean(), hand = b.readBoolean();
        int n = Math.min(1024, b.readVarInt());
        List<Car> cars = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            cars.add(new Car(b.readVarInt(), b.readUtf(128), b.readUtf(256), b.readUtf(16), b.readBoolean(), b.readBoolean(), b.readVarInt()));
        }
        return new OpenGaragePacket(id, title, max, inZone, service, hand, cars);
    }

    public static void handle(OpenGaragePacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MenuClient.openGarage(m)));
        ctx.setPacketHandled(true);
    }
}
