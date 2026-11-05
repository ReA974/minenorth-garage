package com.minenorth.vehicles.garage;

import com.minenorth.vehicles.VehiclesMod;
import com.minenorth.vehicles.handle.MtsBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Véhicules offerts (vélo ou skate de bienvenue, mod Bienvenue) : apparus neufs à côté du joueur, à son nom, avec le drapeau
 * {@link #GIFT} (numéro de carte d'identité du personnage) dans les données de l'entité. Le drapeau suit le véhicule au garage
 * (NBT sauvegardé tel quel) : un véhicule offert ne peut pas être vendu entre joueurs (VehicleSale) ni volé (mod Vol, même clé).
 * Appelé par réflexion par le mod Bienvenue : ne pas changer le nom ni les paramètres de {@link #give}.
 */
public final class GiftVehicles {
    /** Clé des données persistantes du véhicule : numéro de carte du personnage à qui il a été offert. */
    public static final String GIFT = "mnrp_cadeau";

    private GiftVehicles() {}

    public static boolean isGift(Entity e) { return e != null && e.getPersistentData().contains(GIFT); }

    public static boolean isGift(CompoundTag saved) { return saved != null && saved.getCompound("ForgeData").contains(GIFT); }

    /**
     * Fait apparaître le véhicule neuf {@code itemId} à 2 blocs devant le joueur, à son nom, marqué « offert » au personnage {@code card},
     * et lui donne la clé. Renvoie faux si MTS refuse l'apparition (rien n'est donné).
     */
    public static boolean give(ServerPlayer p, String itemId, String label, String card) {
        if (!(p.level() instanceof ServerLevel level)) return false;
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        double x = p.getX() + look.x * 2, y = p.getY() + 0.1, z = p.getZ() + look.z * 2;
        float yaw = MtsBridge.snap90(p.getYRot());
        if (!MtsBridge.spawnNew(level, itemId, x, y, z, (float) MtsBridge.mtsYaw(yaw), p)) return false;
        Entity best = null;
        double bd = Double.MAX_VALUE;
        Vec3 c = new Vec3(x, y, z);
        for (Entity e : level.getEntities((Entity) null, new AABB(c, c).inflate(6), MtsBridge::isVehicle)) {
            if (MtsBridge.ownerOf(e) != null) continue;
            double d = e.distanceToSqr(c);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) {
            VehiclesMod.LOGGER.warn("[Vehicules] Véhicule offert {} posé par MTS mais introuvable près de {}.", itemId, p.getGameProfile().getName());
            return false;
        }
        MtsBridge.setOwner(best, p.getUUID());
        best.getPersistentData().putString(GIFT, card == null || card.isEmpty() ? p.getUUID().toString() : card);
        // clé au même nom que celui que le garage donne au rangement (elle est reprise quand on le range, rendue à la sortie)
        CompoundTag snap = MtsBridge.snapshot(best);
        String keyLabel = Garage.labelOf(MtsBridge.itemId(snap), snap);
        Garage.giveKey(p, keyLabel == null || keyLabel.isEmpty() ? label : keyLabel);
        VehiclesMod.LOGGER.info("[Vehicules] {} offert à {} (carte {}).", itemId, p.getGameProfile().getName(), card);
        return true;
    }
}
