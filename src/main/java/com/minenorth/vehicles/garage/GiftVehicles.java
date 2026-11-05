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

    /** Clé du tag de l'objet véhicule offert : numéro de carte du personnage. */
    public static final String ITEM_TAG = "mnrp_cadeau";

    /**
     * Met l'objet MTS du véhicule dans la main du joueur (sinon dans l'inventaire). Quand il le pose, le véhicule est mis à son nom
     * (drapeau « cadeau », clé, plein). Inventaire plein : le véhicule apparaît à côté de lui ({@link #give}).
     * Appelé par réflexion par le mod Bienvenue : ne pas changer le nom ni les paramètres.
     */
    public static boolean giveItem(ServerPlayer p, String itemId, String label, String card) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        net.minecraft.world.item.Item it = rl == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
        if (it == null || it == net.minecraft.world.item.Items.AIR) return false;
        net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(it);
        String c = card == null || card.isEmpty() ? p.getUUID().toString() : card;
        st.getOrCreateTag().putString(ITEM_TAG, c);
        st.setHoverName(com.minenorth.vehicles.miscs.Gui.comp("&d&l" + label + " &7(cadeau de bienvenue)"));
        if (p.getMainHandItem().isEmpty()) {
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, st);
        } else if (!p.getInventory().add(st)) {
            com.minenorth.vehicles.miscs.Msg.send(p, "&eInventaire plein : ton véhicule est posé à côté de toi.");
            return give(p, itemId, label, card);
        }
        p.containerMenu.broadcastChanges();
        com.minenorth.vehicles.miscs.Msg.send(p, "&aTon &e" + label + " &aest dans ton inventaire : clic droit au sol pour le poser. Il sera à ton nom.");
        return true;
    }

    private record Placing(String card, long until) {}

    /** Joueur qui vient de poser un véhicule offert -> carte et échéance (5 s). */
    private static final java.util.Map<java.util.UUID, Placing> PLACING = new java.util.HashMap<>();

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onUse(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        net.minecraft.world.item.ItemStack st = e.getItemStack();
        if (!st.hasTag() || !st.getTag().contains(ITEM_TAG)) return;
        String card = st.getTag().getString(ITEM_TAG);
        String[] id = com.minenorth.vehicles.registry.IdentityBridge.identity(p.getServer(), p.getUUID());
        String mine = id != null && id.length > 5 && id[5] != null && !id[5].isEmpty() ? id[5] : p.getUUID().toString();
        if (!card.equals(mine)) {   // offert à un autre personnage : ni don ni revente
            e.setCanceled(true);
            com.minenorth.vehicles.miscs.Msg.send(p, "&cCe véhicule de bienvenue a été offert à quelqu'un d'autre : tu ne peux pas le poser.");
            return;
        }
        PLACING.put(p.getUUID(), new Placing(card, System.currentTimeMillis() + 5000));
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
        // propriétaire, drapeau, clé et plein au tick suivant (l'entité vient juste d'arriver dans le monde)
        CLAIMS.add(new Claim(level, v.position(), best.getUUID(), "", "Véhicule de bienvenue", pl.card(), 40));
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
