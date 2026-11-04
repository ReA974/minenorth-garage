package com.minenorth.vehicles.peche;

import com.minenorth.vehicles.VehiclesMod;
import com.minenorth.vehicles.garage.VehicleType;
import com.minenorth.vehicles.handle.MtsBridge;
import com.minenorth.vehicles.miscs.Gui;
import com.minenorth.vehicles.miscs.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de pêche des bateaux. Tout est décidé par le serveur : le client n'envoie que le clic droit avec l'objet.
 * <ul>
 * <li>Clic droit à bord d'un bateau sur l'eau : le filet est mis à l'eau (un seul par bateau, un seul par joueur).</li>
 * <li>Tant que le pêcheur est à bord et que le bateau avance sur l'eau (vitesse mesurée par le serveur entre deux relevés),
 *     un poisson Star Catcher est tiré toutes les {@code secondesParPoisson} secondes, selon le lieu (biome, altitude),
 *     l'heure et la météo, jusqu'à la capacité du filet.</li>
 * <li>Nouveau clic droit : remontée (durée réglable, pêcheur à bord jusqu'au bout), puis les poissons vont dans l'inventaire.</li>
 * </ul>
 * L'état vit dans les données du véhicule ({@code mnrp_filet}) : il suit le bateau au garage. Si le bateau disparaît hors garage,
 * les poissons tombent au sol ; l'assurance rend un bateau sans filet (pas de duplication).
 */
@Mod.EventBusSubscriber(modid = VehiclesMod.MODID)
public final class FishingNet {
    public static final String TAG = "mnrp_filet";

    private FishingNet() {}

    /** Bateaux dont le filet est à l'eau, suivis en mémoire (uuid véhicule -> suivi). */
    private static final Map<UUID, Track> TRACKED = new HashMap<>();

    private static final class Track {
        final ServerLevel level;
        Vec3 last;
        boolean fullWarned, idleWarned;
        Track(ServerLevel level, Vec3 pos) { this.level = level; this.last = pos; }
    }

    // ------------------------------------------------------------------ utilisation de l'objet

    public static void use(ServerPlayer p) {
        if (!FishingNetConfig.ENABLED.get()) {
            Msg.send(p, "&cLe filet de pêche est désactivé sur ce serveur.");
            return;
        }
        Entity boat = MtsBridge.vehicleOf(p);
        if (boat == null || p.getVehicle() == null) {
            Msg.send(p, "&cMonte à bord d'un bateau pour utiliser le filet.");
            return;
        }
        CompoundTag net = boat.getPersistentData().getCompound(TAG);
        if (net.hasUUID("owner")) {
            UUID owner = net.getUUID("owner");
            if (!owner.equals(p.getUUID()) && !p.getUUID().equals(MtsBridge.ownerOf(boat)) && !p.hasPermissions(2)) {
                Msg.send(p, "&cCe filet ne t'appartient pas.");
                return;
            }
            if (net.getLong("haulEnd") > 0) {
                Msg.send(p, "&eLe filet est déjà en train de remonter...");
                return;
            }
            startHaul(p, boat, net);
            return;
        }
        if (FishingNetConfig.BOATS_ONLY.get() && VehicleType.of(MtsBridge.itemId(MtsBridge.snapshot(boat)), null) != VehicleType.MER) {
            Msg.send(p, "&cLe filet ne se déploie que depuis un bateau.");
            return;
        }
        if (!onWater((ServerLevel) boat.level(), boat)) {
            Msg.send(p, "&cLe bateau doit être sur l'eau pour mettre le filet à l'eau.");
            return;
        }
        for (Map.Entry<UUID, Track> e : TRACKED.entrySet()) {
            Entity other = e.getValue().level.getEntity(e.getKey());
            if (other != null && other != boat && p.getUUID().equals(ownerOfNet(other))) {
                Msg.send(p, "&cTon filet est déjà à l'eau sur un autre bateau : remonte-le d'abord.");
                return;
            }
        }
        net = new CompoundTag();
        net.putUUID("owner", p.getUUID());
        net.putInt("progress", 0);
        net.put("Items", new ListTag());
        boat.getPersistentData().put(TAG, net);
        TRACKED.put(boat.getUUID(), new Track((ServerLevel) boat.level(), boat.position()));
        boat.level().playSound(null, boat.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1f, 0.6f);
        Msg.send(p, "&bFilet à l'eau ! &7Avance doucement (entre " + fmt(FishingNetConfig.MIN_SPEED.get()) + " et "
                + fmt(FishingNetConfig.MAX_SPEED.get()) + " km/h). Capacité : " + FishingNetConfig.CAPACITY.get()
                + " poissons. Clic droit avec le filet pour le remonter.");
    }

    private static void startHaul(ServerPlayer p, Entity boat, CompoundTag net) {
        int secs = FishingNetConfig.HAUL_SECONDS.get();
        net.putLong("haulEnd", boat.level().getGameTime() + Math.max(1, secs * 20L));
        net.putUUID("hauler", p.getUUID());
        boat.getPersistentData().put(TAG, net);
        TRACKED.computeIfAbsent(boat.getUUID(), k -> new Track((ServerLevel) boat.level(), boat.position()));
        Msg.send(p, "&eRemontée du filet... &7(" + secs + " s, reste à bord)");
    }

    // ------------------------------------------------------------------ boucle serveur

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || TRACKED.isEmpty()) return;
        MinecraftServer server = e.getServer();
        if (server.getTickCount() % 20 != 0) return;
        Iterator<Map.Entry<UUID, Track>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Track> en = it.next();
            Track t = en.getValue();
            Entity boat = t.level.getEntity(en.getKey());
            if (boat == null || !boat.isAlive()) { it.remove(); continue; }
            CompoundTag net = boat.getPersistentData().getCompound(TAG);
            if (!net.hasUUID("owner")) { it.remove(); continue; }
            Vec3 now = boat.position();
            double kmh = now.distanceTo(t.last) * 3.6;   // blocs parcourus en 1 s
            t.last = now;
            long haulEnd = net.getLong("haulEnd");
            if (haulEnd > 0) {
                if (haul(boat, net, haulEnd)) it.remove();
                continue;
            }
            ServerPlayer fisher = server.getPlayerList().getPlayer(net.getUUID("owner"));
            if (fisher == null || MtsBridge.vehicleOf(fisher) != boat) continue;   // pêcheur absent : le filet attend
            ListTag items = net.getList("Items", Tag.TAG_COMPOUND);
            int count = count(items);
            int cap = FishingNetConfig.CAPACITY.get();
            if (count >= cap) {
                if (!t.fullWarned) Msg.send(fisher, "&6Le filet est plein (" + cap + " poissons) : remonte-le !");
                t.fullWarned = true;
                continue;
            }
            if (!onWater(t.level, boat) || kmh < FishingNetConfig.MIN_SPEED.get() || kmh > FishingNetConfig.MAX_SPEED.get()) continue;
            int progress = net.getInt("progress") + 1;
            if (progress >= FishingNetConfig.INTERVAL.get()) {
                progress = 0;
                BlockPos pos = boat.blockPosition();
                Item fish = FishTable.roll(t.level, pos, t.level.getRandom());
                if (fish != null) {
                    add(items, new ItemStack(fish));
                    net.put("Items", items);
                    fisher.displayClientMessage(Gui.comp("&bFilet : &f" + (count + 1) + "/" + cap + " &7poissons"), true);
                    t.idleWarned = false;
                } else if (!t.idleWarned) {
                    Msg.send(fisher, "&7Rien ne mord ici... essaie un autre coin, une autre heure ou une autre météo.");
                    t.idleWarned = true;
                }
            }
            net.putInt("progress", progress);
            boat.getPersistentData().put(TAG, net);
        }
    }

    /** @return true quand le filet est remonté (ou abandonné) et ne doit plus être suivi */
    private static boolean haul(Entity boat, CompoundTag net, long haulEnd) {
        MinecraftServer server = boat.getServer();
        ServerPlayer p = net.hasUUID("hauler") ? server.getPlayerList().getPlayer(net.getUUID("hauler")) : null;
        if (p == null || MtsBridge.vehicleOf(p) != boat) {
            net.remove("haulEnd");
            net.remove("hauler");
            boat.getPersistentData().put(TAG, net);
            if (p != null) Msg.send(p, "&cRemontée interrompue : il faut rester à bord. Le filet reste à l'eau.");
            return false;
        }
        if (boat.level().getGameTime() < haulEnd) return false;
        List<ItemStack> fish = stacks(net.getList("Items", Tag.TAG_COMPOUND));
        boat.getPersistentData().remove(TAG);
        int n = 0;
        for (ItemStack s : fish) {
            n += s.getCount();
            if (!p.getInventory().add(s.copy())) p.drop(s.copy(), false);
        }
        boat.level().playSound(null, boat.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1f, 0.8f);
        Msg.send(p, n == 0 ? "&7Filet remonté : vide." : "&aFilet remonté : &f" + n + " &apoisson" + (n > 1 ? "s" : "")
                + ". &7Revends-les à la poissonnerie.");
        return true;
    }

    // ------------------------------------------------------------------ disparition, assurance, rechargement

    /** Bateau détruit, revendu ou ramassé hors garage : les poissons tombent au sol. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) {
        Entity v = e.getEntity();
        if (e.getLevel().isClientSide() || !(e.getLevel() instanceof ServerLevel level) || !MtsBridge.isVehicle(v)) return;
        if (v.getPersistentData().getBoolean(MtsBridge.HANDLED)) return;
        Entity.RemovalReason why = v.getRemovalReason();
        if (why != Entity.RemovalReason.KILLED && why != Entity.RemovalReason.DISCARDED) return;
        CompoundTag net = v.getPersistentData().getCompound(TAG);
        if (!net.contains("Items")) return;
        List<ItemStack> list = stacks(net.getList("Items", Tag.TAG_COMPOUND));
        v.getPersistentData().remove(TAG);
        TRACKED.remove(v.getUUID());
        final double x = v.getX(), y = v.getY() + 0.5, z = v.getZ();
        level.getServer().execute(() -> {
            for (ItemStack s : list) level.addFreshEntity(new ItemEntity(level, x, y, z, s.copy()));
        });
    }

    /** Le filet ne fait jamais partie de l'état d'origine rendu par l'assurance. */
    public static void stripFromOrigin(CompoundTag origin) {
        origin.getCompound("ForgeData").remove(TAG);
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent e) { FishTable.invalidate(); }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        FishTable.invalidate();
        TRACKED.clear();
    }

    /** Un bateau dont le filet était à l'eau avant un redémarrage reprend la pêche dès qu'il est chargé. */
    @SubscribeEvent
    public static void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getLevel() instanceof ServerLevel level)) return;
        Entity v = e.getEntity();
        if (!v.getPersistentData().contains(TAG, Tag.TAG_COMPOUND) || !MtsBridge.isVehicle(v)) return;
        TRACKED.putIfAbsent(v.getUUID(), new Track(level, v.position()));
    }

    // ------------------------------------------------------------------ outils

    private static UUID ownerOfNet(Entity boat) {
        CompoundTag n = boat.getPersistentData().getCompound(TAG);
        return n.hasUUID("owner") ? n.getUUID("owner") : null;
    }

    /** Coque sur l'eau : bloc du véhicule ou juste dessous rempli d'eau. */
    static boolean onWater(ServerLevel level, Entity boat) {
        BlockPos p = BlockPos.containing(boat.getX(), boat.getY() + 0.2, boat.getZ());
        return level.getFluidState(p).is(FluidTags.WATER) || level.getFluidState(p.below()).is(FluidTags.WATER);
    }

    private static int count(ListTag items) {
        int n = 0;
        for (int i = 0; i < items.size(); i++) n += items.getCompound(i).getByte("Count");
        return n;
    }

    private static void add(ListTag items, ItemStack s) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack cur = ItemStack.of(items.getCompound(i));
            if (ItemStack.isSameItemSameTags(cur, s) && cur.getCount() < cur.getMaxStackSize()) {
                cur.grow(1);
                items.set(i, cur.save(new CompoundTag()));
                return;
            }
        }
        items.add(s.save(new CompoundTag()));
    }

    private static List<ItemStack> stacks(ListTag items) {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = ItemStack.of(items.getCompound(i));
            if (!s.isEmpty()) l.add(s);
        }
        return l;
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
