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
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = VehiclesMod.MODID)
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
        // MTS peut n'ajouter l'entité au monde qu'au tick suivant : on la cherche maintenant puis pendant 2 s.
        // Le véhicule est bien parti : on renvoie vrai (jamais un 2e véhicule si on le trouve un peu plus tard).
        Claim c = new Claim(level, new Vec3(x, y, z), p.getUUID(), itemId, label, card == null || card.isEmpty() ? p.getUUID().toString() : card, 40);
        if (!c.tryClaim(p.getServer())) CLAIMS.add(c);
        return true;
    }

    // ------------------------------------------------------------------ remise EN MAIN (comme un achat en concession)

    /**
     * Met l'objet MTS du véhicule dans la main du joueur (sinon dans l'inventaire), comme un achat en concession : objet VIERGE.
     * Important : MTS prend tout tag de l'objet pour l'état d'un véhicule déjà sauvegardé et ne monte alors pas les pièces par défaut
     * (vélo posé sans roues, qui tombe). Le lien « cadeau » est donc gardé côté serveur (GiftData) jusqu'à la pose : le véhicule
     * qui apparaît près du joueur, du même modèle, est mis à son nom (drapeau « cadeau », clé, plein).
     * Inventaire plein : le véhicule apparaît à côté de lui ({@link #give}). Appelé par réflexion par le mod Bienvenue.
     */
    public static boolean giveItem(ServerPlayer p, String itemId, String label, String card) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        net.minecraft.world.item.Item it = rl == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
        if (it == null || it == net.minecraft.world.item.Items.AIR) return false;
        net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(it);   // aucun tag : MTS montera roues et moteur
        String c = card == null || card.isEmpty() ? p.getUUID().toString() : card;
        if (p.getMainHandItem().isEmpty()) {
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, st);
        } else if (!p.getInventory().add(st)) {
            com.minenorth.vehicles.miscs.Msg.send(p, "&eInventaire plein : ton véhicule est posé à côté de toi.");
            return give(p, itemId, label, card);
        }
        GiftData.get(p.getServer()).add(p.getUUID(), itemId, c);
        p.containerMenu.broadcastChanges();
        com.minenorth.vehicles.miscs.Msg.send(p, "&aTon &e" + label + " &aest dans ton inventaire : clic droit au sol pour le poser. Il sera à ton nom.");
        return true;
    }

    /** Cadeaux remis en main mais pas encore posés : joueur -> {id d'objet, carte}. Sauvegardé (data/minenorth_cadeaux.dat). */
    public static final class GiftData extends net.minecraft.world.level.saveddata.SavedData {
        final java.util.Map<java.util.UUID, java.util.List<String[]>> pending = new java.util.HashMap<>();

        static GiftData get(net.minecraft.server.MinecraftServer s) {
            return s.overworld().getDataStorage().computeIfAbsent(GiftData::load, GiftData::new, "minenorth_cadeaux");
        }

        void add(java.util.UUID p, String item, String card) {
            pending.computeIfAbsent(p, k -> new java.util.ArrayList<>()).add(new String[]{item, card});
            setDirty();
        }

        /** Cadeau en attente de ce modèle pour ce joueur (retiré de la liste), ou null. */
        String[] take(java.util.UUID p, String item) {
            java.util.List<String[]> l = pending.get(p);
            if (l == null) return null;
            for (java.util.Iterator<String[]> it = l.iterator(); it.hasNext(); ) {
                String[] g = it.next();
                if (g[0].equalsIgnoreCase(item)) {
                    it.remove();
                    if (l.isEmpty()) pending.remove(p);
                    setDirty();
                    return g;
                }
            }
            return null;
        }

        boolean has(java.util.UUID p, String item) {
            java.util.List<String[]> l = pending.get(p);
            if (l != null) for (String[] g : l) if (g[0].equalsIgnoreCase(item)) return true;
            return false;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            pending.forEach((k, v) -> v.forEach(g -> {
                CompoundTag t = new CompoundTag();
                t.putUUID("p", k);
                t.putString("item", g[0]);
                t.putString("card", g[1]);
                list.add(t);
            }));
            tag.put("pending", list);
            return tag;
        }

        static GiftData load(CompoundTag tag) {
            GiftData d = new GiftData();
            for (net.minecraft.nbt.Tag t : tag.getList("pending", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                if (c.hasUUID("p")) d.pending.computeIfAbsent(c.getUUID("p"), k -> new java.util.ArrayList<>()).add(new String[]{c.getString("item"), c.getString("card")});
            }
            return d;
        }
    }

    /** Joueur qui pose à l'instant un véhicule offert -> {id d'objet, échéance (5 s)}. */
    private record Placing(String item, long until) {}

    private static final java.util.Map<java.util.UUID, Placing> PLACING = new java.util.HashMap<>();

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onUse(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        net.minecraft.world.item.ItemStack st = e.getItemStack();
        net.minecraft.resources.ResourceLocation k = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(st.getItem());
        if (k != null && st.hasTag() && st.getTag().contains("mnrp_cadeau") && k.getNamespace().equals("mts")) {
            // objet remis par l'ancienne version (tag « cadeau » + nom) : MTS l'aurait posé sans roues. On retire le tag avant MTS.
            GiftData.get(p.getServer()).add(p.getUUID(), k.toString(), st.getTag().getString("mnrp_cadeau"));
            st.setTag(null);
        }
        if (k == null || !GiftData.get(p.getServer()).has(p.getUUID(), k.toString())) return;
        PLACING.put(p.getUUID(), new Placing(k.toString(), System.currentTimeMillis() + 5000));
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || PLACING.isEmpty() || !(e.getLevel() instanceof ServerLevel level) || !MtsBridge.isVehicle(e.getEntity())) return;
        Entity v = e.getEntity();
        long now = System.currentTimeMillis();
        PLACING.values().removeIf(pl -> pl.until() < now);
        ServerPlayer best = null;
        double bd = 12 * 12;
        for (ServerPlayer p : level.players()) {
            if (!PLACING.containsKey(p.getUUID())) continue;
            double d = p.distanceToSqr(v);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        if (best == null) return;
        Placing pl = PLACING.remove(best.getUUID());
        String[] g = GiftData.get(level.getServer()).take(best.getUUID(), pl.item());
        if (g == null) return;
        // propriétaire, drapeau, clé et plein au tick suivant (l'entité vient juste d'arriver dans le monde)
        CLAIMS.add(new Claim(level, v.position(), best.getUUID(), g[0], "Véhicule de bienvenue", g[1], 40));
    }

    /** Véhicule offert en attente : propriétaire, drapeau « cadeau », clé et plein dès que MTS l'a posé. */
    private static final class Claim {
        final ServerLevel level;
        final Vec3 at;
        final java.util.UUID player;
        final String itemId, label, card;
        int ticksLeft;

        Claim(ServerLevel level, Vec3 at, java.util.UUID player, String itemId, String label, String card, int ticks) {
            this.level = level;
            this.at = at;
            this.player = player;
            this.itemId = itemId;
            this.label = label;
            this.card = card;
            this.ticksLeft = ticks;
        }

        boolean tryClaim(net.minecraft.server.MinecraftServer server) {
            Entity best = null;
            double bd = Double.MAX_VALUE;
            for (Entity e : level.getEntities((Entity) null, new AABB(at, at).inflate(6), MtsBridge::isVehicle)) {
                if (MtsBridge.ownerOf(e) != null) continue;
                double d = e.distanceToSqr(at);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
            if (best == null) return false;
            MtsBridge.setOwner(best, player);
            best.getPersistentData().putString(GIFT, card);
            ServerPlayer p = server.getPlayerList().getPlayer(player);
            if (p != null) {   // clé au même nom que celui que le garage donne au rangement
                CompoundTag snap = MtsBridge.snapshot(best);
                String keyLabel = Garage.labelOf(MtsBridge.itemId(snap), snap);
                Garage.giveKey(p, keyLabel == null || keyLabel.isEmpty() ? label : keyLabel);
            }
            com.minenorth.vehicles.shop.ShopMulti.fillLater(best);   // plein (essence, ou batterie pour le skate électrique)
            VehiclesMod.LOGGER.info("[Vehicules] {} offert (carte {}).", itemId, card);
            return true;
        }
    }

    private static final java.util.List<Claim> CLAIMS = new java.util.ArrayList<>();

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || CLAIMS.isEmpty()) return;
        java.util.Iterator<Claim> it = CLAIMS.iterator();
        while (it.hasNext()) {
            Claim c = it.next();
            if (c.tryClaim(e.getServer())) {
                it.remove();
            } else if (--c.ticksLeft <= 0) {
                it.remove();
                VehiclesMod.LOGGER.warn("[Vehicules] Véhicule offert {} posé par MTS mais introuvable (carte {}).", c.itemId, c.card);
                ServerPlayer p = e.getServer().getPlayerList().getPlayer(c.player);
                if (p != null) com.minenorth.vehicles.miscs.Msg.send(p, "&cTon véhicule de bienvenue n'a pas pu être mis à ton nom. Préviens un membre du staff.");
            }
        }
    }
}
