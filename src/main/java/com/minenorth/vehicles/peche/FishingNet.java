package com.minenorth.vehicles.peche;

import com.minenorth.vehicles.VehiclesMod;
import com.minenorth.vehicles.garage.VehicleType;
import com.minenorth.vehicles.handle.MtsBridge;
import com.minenorth.vehicles.miscs.Gui;
import com.minenorth.vehicles.miscs.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de pêche des bateaux. Tout est décidé par le serveur : le client n'envoie que le clic droit avec l'objet.
 * <ul>
 * <li>Clic droit à bord d'un bateau sur l'eau : le filet est mis à l'eau (un seul par bateau, un seul par joueur, filet pas déchiré).
 *     Il se voit derrière le bateau (câble, bouées rouges et blanches, nappe qui bouillonne) : particules envoyées par le serveur.</li>
 * <li>Tant que le pêcheur est à bord et que le bateau avance sur l'eau (vitesse mesurée par le serveur), un poisson Star Catcher est
 *     tiré toutes les {@code secondesParPoisson} secondes, plus vite en haute mer, sous la pluie, à l'aube et au crépuscule.
 *     Jauge de remplissage dans la barre d'action.</li>
 * <li>Nouveau clic droit : remontée au treuil ({@code dureeRemontee} s, pêcheur à bord) ; la prise sort poisson par poisson, avec une
 *     petite chance de coffre d'épave ou de bouteille à la mer. Le filet s'use à chaque remontée ; bobine de fil pour le réparer.</li>
 * </ul>
 * L'état vit dans les données du véhicule ({@code mnrp_filet}) : il suit le bateau au garage. Si le bateau disparaît hors garage,
 * les poissons tombent au sol ; l'assurance rend un bateau sans filet (pas de duplication).
 */
@Mod.EventBusSubscriber(modid = VehiclesMod.MODID)
public final class FishingNet {
    public static final String TAG = "mnrp_filet";
    private static final TagKey<Biome> DEEP_OCEAN = BiomeTags.IS_DEEP_OCEAN;
    private static final TagKey<Biome> OCEAN = BiomeTags.IS_OCEAN;
    private static final TagKey<Biome> BEACH = BiomeTags.IS_BEACH;
    private static final DustParticleOptions BOUEE_ROUGE = new DustParticleOptions(new Vector3f(0.9f, 0.15f, 0.15f), 1.6f);
    private static final DustParticleOptions BOUEE_BLANCHE = new DustParticleOptions(new Vector3f(0.95f, 0.95f, 0.95f), 1.6f);
    private static final DustParticleOptions CABLE = new DustParticleOptions(new Vector3f(0.75f, 0.68f, 0.5f), 0.7f);
    private static final String[] MESSAGES = {
            "« Si tu lis ceci, le trésor du Capitaine Morvan dort sous la vieille jetée. »",
            "« Marie, je rentre au port dans trois jours. Garde-moi une place au bar. — J. »",
            "« À qui trouvera ce message : la mer m'a tout pris, sauf l'envie de recommencer. »",
            "« Coordonnées effacées par l'eau... on devine seulement : N 4?° ... »",
            "« Ne fais jamais confiance au receleur du port. Jamais. »",
            "« Jour 12 sur l'île. Toujours pas de réseau. Envoyez des frites. »",
    };

    private FishingNet() {}

    /** Bateaux dont le filet est à l'eau, suivis en mémoire (uuid véhicule -> suivi). */
    private static final Map<UUID, Track> TRACKED = new HashMap<>();

    private static final class Track {
        final ServerLevel level;
        Vec3 last;
        Vec3 dir = new Vec3(0, 0, 1);   // cap du bateau (dernier déplacement), pour placer le filet derrière lui
        boolean fullWarned, idleWarned;
        Track(ServerLevel level, Vec3 pos) { this.level = level; this.last = pos; }
    }

    // ------------------------------------------------------------------ utilisation de l'objet

    public static void use(ServerPlayer p, ItemStack held) {
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
        if (FishingNetItem.isTorn(held)) {
            Msg.send(p, "&cTon filet est déchiré : répare-le avec une &fbobine de fil de pêche &c(poissonnerie) ou à l'enclume.");
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
        net.putFloat("progress", 0);
        net.put("Items", new ListTag());
        boat.getPersistentData().put(TAG, net);
        TRACKED.put(boat.getUUID(), new Track((ServerLevel) boat.level(), boat.position()));
        boat.level().playSound(null, boat.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1f, 0.6f);
        boat.level().playSound(null, boat.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8f, 1.0f);
        Msg.send(p, "&bFilet à l'eau ! &7Avance doucement (entre " + fmt(FishingNetConfig.MIN_SPEED.get()) + " et "
                + fmt(FishingNetConfig.MAX_SPEED.get()) + " km/h). Capacité : " + FishingNetConfig.CAPACITY.get()
                + " poissons. Haute mer, pluie, aube et crépuscule font mordre plus vite. Clic droit avec le filet pour le remonter.");
    }

    private static void startHaul(ServerPlayer p, Entity boat, CompoundTag net) {
        int secs = FishingNetConfig.HAUL_SECONDS.get();
        net.putLong("haulEnd", boat.level().getGameTime() + Math.max(1, secs * 20L));
        net.putUUID("hauler", p.getUUID());
        net.putInt("hauled", 0);
        boat.getPersistentData().put(TAG, net);
        TRACKED.computeIfAbsent(boat.getUUID(), k -> new Track((ServerLevel) boat.level(), boat.position()));
        Msg.send(p, "&eRemontée du filet au treuil... &7(" + secs + " s, reste à bord)");
    }

    // ------------------------------------------------------------------ boucle serveur

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || TRACKED.isEmpty()) return;
        MinecraftServer server = e.getServer();
        int tick = server.getTickCount();
        if (tick % 5 == 0) visuals();
        if (tick % 20 != 0) return;
        Iterator<Map.Entry<UUID, Track>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Track> en = it.next();
            Track t = en.getValue();
            Entity boat = t.level.getEntity(en.getKey());
            if (boat == null || !boat.isAlive()) { it.remove(); continue; }
            CompoundTag net = boat.getPersistentData().getCompound(TAG);
            if (!net.hasUUID("owner")) { it.remove(); continue; }
            Vec3 now = boat.position();
            Vec3 move = now.subtract(t.last);
            double kmh = move.length() * 3.6;   // blocs parcourus en 1 s
            if (move.horizontalDistanceSqr() > 0.04) t.dir = new Vec3(move.x, 0, move.z).normalize();
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
            Bonus bonus = bonus(t.level, boat.blockPosition());
            if (count >= cap) {
                if (!t.fullWarned) Msg.send(fisher, "&6Le filet est plein (" + cap + " poissons) : remonte-le !");
                t.fullWarned = true;
                gauge(fisher, count, cap, bonus, "&6plein");
                continue;
            }
            boolean moving = onWater(t.level, boat) && kmh >= FishingNetConfig.MIN_SPEED.get() && kmh <= FishingNetConfig.MAX_SPEED.get();
            String state = !onWater(t.level, boat) ? "&chors de l'eau" : kmh < FishingNetConfig.MIN_SPEED.get() ? "&7avance un peu"
                    : kmh > FishingNetConfig.MAX_SPEED.get() ? "&ctrop vite" : "&apêche";
            if (moving) {
                float progress = net.getFloat("progress") + (float) bonus.speed;
                if (progress >= FishingNetConfig.INTERVAL.get()) {
                    progress = 0;
                    Item fish = FishTable.roll(t.level, boat.blockPosition(), t.level.getRandom(), bonus.rareBoost);
                    if (fish != null) {
                        add(items, new ItemStack(fish));
                        net.put("Items", items);
                        count++;
                        t.idleWarned = false;
                        t.level.sendParticles(ParticleTypes.SPLASH, boat.getX() - t.dir.x * 5, boat.getY() + 0.3, boat.getZ() - t.dir.z * 5,
                                20, 0.6, 0.1, 0.6, 0.1);
                        t.level.playSound(null, boat.blockPosition(), SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 0.6f, 1.2f);
                    } else if (!t.idleWarned) {
                        Msg.send(fisher, "&7Rien ne mord ici... essaie un autre coin, une autre heure ou une autre météo.");
                        t.idleWarned = true;
                    }
                }
                net.putFloat("progress", progress);
                boat.getPersistentData().put(TAG, net);
            }
            gauge(fisher, count, cap, bonus, state);
        }
    }

    /** Jauge dans la barre d'action : remplissage, état, bonus actifs, usure du filet. */
    private static void gauge(ServerPlayer p, int count, int cap, Bonus b, String state) {
        int cells = 10, full = Math.round(cells * (float) count / cap);
        StringBuilder bar = new StringBuilder("&bFilet ");
        bar.append(count >= cap ? "&6" : "&a");
        for (int i = 0; i < cells; i++) bar.append(i == full ? "&8" : "").append('■');
        bar.append(" &f").append(count).append('/').append(cap).append(" &7· ").append(state);
        if (!b.label.isEmpty()) bar.append(" &7· &d").append(b.label);
        ItemStack netItem = FishingNetItem.find(p);
        if (!netItem.isEmpty()) {
            int pct = Math.round(100f * (netItem.getMaxDamage() - netItem.getDamageValue()) / netItem.getMaxDamage());
            bar.append(" &7· filet ").append(pct < 25 ? "&c" : "&f").append(pct).append('%');
        }
        p.displayClientMessage(Gui.comp(bar.toString()), true);
    }

    /** Remontée : la prise sort poisson par poisson au fil du treuil. @return true quand le filet est remonté et n'est plus suivi */
    private static boolean haul(Entity boat, CompoundTag net, long haulEnd) {
        MinecraftServer server = boat.getServer();
        ServerLevel level = (ServerLevel) boat.level();
        ServerPlayer p = net.hasUUID("hauler") ? server.getPlayerList().getPlayer(net.getUUID("hauler")) : null;
        if (p == null || MtsBridge.vehicleOf(p) != boat) {
            net.remove("haulEnd");
            net.remove("hauler");
            boat.getPersistentData().put(TAG, net);
            if (p != null) Msg.send(p, "&cRemontée interrompue : il faut rester à bord. Le reste de la prise est encore dans le filet.");
            return false;
        }
        long now = level.getGameTime();
        ListTag items = net.getList("Items", Tag.TAG_COMPOUND);
        int left = count(items);
        long secsLeft = Math.max(1, (haulEnd - now + 19) / 20);
        int give = now >= haulEnd ? left : (int) Math.ceil(left / (double) secsLeft);
        // treuil
        level.playSound(null, boat.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.9f, 0.55f);
        level.playSound(null, boat.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.7f, 0.7f);
        StringBuilder shown = new StringBuilder();
        for (int i = 0; i < give; i++) {
            ItemStack one = takeOne(items);
            if (one.isEmpty()) break;
            if (shown.length() > 0) shown.append("&7, ");
            shown.append("&f").append(one.getHoverName().getString());
            if (!p.getInventory().add(one.copy())) p.drop(one.copy(), false);
            net.putInt("hauled", net.getInt("hauled") + 1);
        }
        net.put("Items", items);
        if (give > 0) {
            level.playSound(null, boat.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.6f, 1.3f);
            level.sendParticles(ParticleTypes.SPLASH, boat.getX(), boat.getY() + 0.8, boat.getZ(), 15, 0.5, 0.2, 0.5, 0.1);
            p.displayClientMessage(Gui.comp("&bTreuil &7▲ &a+ " + shown), true);
        } else {
            p.displayClientMessage(Gui.comp("&bTreuil &7▲ &8..."), true);
        }
        if (now < haulEnd) {
            boat.getPersistentData().put(TAG, net);
            return false;
        }
        int n = net.getInt("hauled");
        boat.getPersistentData().remove(TAG);
        treasure(p, level, boat, n);
        wear(p, n);
        level.playSound(null, boat.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1f, 1.0f);
        Msg.send(p, n == 0 ? "&7Filet remonté : vide." : "&aFilet remonté : &f" + n + " &apoisson" + (n > 1 ? "s" : "")
                + ". &7Revends-les à la poissonnerie.");
        return true;
    }

    /** Petite chance de coffre d'épave (table « trésor » de la pêche) ou de bouteille à la mer ; double près des côtes. */
    private static void treasure(ServerPlayer p, ServerLevel level, Entity boat, int fish) {
        if (fish <= 0) return;
        RandomSource r = level.getRandom();
        double chance = FishingNetConfig.TREASURE_CHANCE.get() * Math.min(1.0, 0.25 + fish / (double) FishingNetConfig.CAPACITY.get());
        if (level.getBiome(boat.blockPosition()).is(BEACH) || nearShore(level, boat.blockPosition())) chance *= 2;
        if (r.nextDouble() >= chance) return;
        List<ItemStack> loot = new ArrayList<>();
        String what;
        if (r.nextFloat() < 0.45f) {
            ItemStack bottle = new ItemStack(Items.PAPER);
            bottle.setHoverName(Gui.comp("&bMessage dans une bouteille"));
            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Gui.comp("&7&o" + MESSAGES[r.nextInt(MESSAGES.length)]))));
            bottle.getOrCreateTagElement("display").put("Lore", lore);
            loot.add(bottle);
            loot.add(new ItemStack(Items.GLASS_BOTTLE));
            what = "&bune bouteille à la mer &7avec un message";
        } else {
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, boat.position())
                    .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                    .withLuck(p.getLuck())
                    .create(LootContextParamSets.FISHING);
            loot.addAll(server(level).getLootData().getLootTable(BuiltInLootTables.FISHING_TREASURE).getRandomItems(params));
            what = "&6un coffre d'épave";
        }
        for (ItemStack s : loot) if (!p.getInventory().add(s.copy())) p.drop(s.copy(), false);
        level.playSound(null, boat.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, boat.getX(), boat.getY() + 1.2, boat.getZ(), 20, 0.6, 0.4, 0.6, 0.05);
        Msg.send(p, "&e&lIncroyable ! &fLe filet a aussi remonté " + what + "&f !");
    }

    private static MinecraftServer server(ServerLevel level) { return level.getServer(); }

    /** Usure du filet tenu (ou du premier dans l'inventaire) : 1 + 1 par tranche de 4 poissons ; jamais détruit, seulement déchiré. */
    private static void wear(ServerPlayer p, int fish) {
        ItemStack s = FishingNetItem.find(p);
        if (s.isEmpty()) return;
        int dmg = FishingNetConfig.WEAR_BASE.get() + fish / 4;
        s.setDamageValue(Math.min(s.getMaxDamage() - 1, s.getDamageValue() + dmg));
        if (FishingNetItem.isTorn(s)) {
            Msg.send(p, "&cTon filet est déchiré ! &7Répare-le avec une bobine de fil de pêche (poissonnerie) ou à l'enclume.");
        } else if (s.getMaxDamage() - s.getDamageValue() < s.getMaxDamage() / 4) {
            Msg.send(p, "&6Ton filet est bien usé : pense à le réparer.");
        }
    }

    // ------------------------------------------------------------------ visuel : câble, bouées, nappe du filet

    private static void visuals() {
        for (Map.Entry<UUID, Track> en : TRACKED.entrySet()) {
            Track t = en.getValue();
            Entity boat = t.level.getEntity(en.getKey());
            if (boat == null) continue;
            CompoundTag net = boat.getPersistentData().getCompound(TAG);
            if (!net.hasUUID("owner") || !onWater(t.level, boat)) continue;
            Vec3 back = t.dir.scale(-1);
            Vec3 side = new Vec3(-t.dir.z, 0, t.dir.x);
            double haulFrac = 1;
            long haulEnd = net.getLong("haulEnd");
            if (haulEnd > 0) {   // pendant la remontée, le filet se rapproche du bateau
                long total = Math.max(1, FishingNetConfig.HAUL_SECONDS.get() * 20L);
                haulFrac = Math.max(0.15, (haulEnd - t.level.getGameTime()) / (double) total);
            }
            double len = 6.0 * haulFrac;
            double y = Math.floor(boat.getY()) + 0.95;
            if (!t.level.getFluidState(BlockPos.containing(boat.getX(), y, boat.getZ())).is(FluidTags.WATER)) y = boat.getY() + 0.1;
            Vec3 stern = boat.position().add(back.scale(1.6));
            // câble de la poupe au filet
            for (double d = 0; d < len; d += 0.6) {
                Vec3 c = stern.add(back.scale(d));
                t.level.sendParticles(CABLE, c.x, y + 0.15 + 0.25 * (1 - d / len), c.z, 1, 0, 0, 0, 0);
            }
            // ligne de bouées rouges et blanches à l'avant du filet
            Vec3 head = stern.add(back.scale(len));
            for (int i = -2; i <= 2; i++) {
                Vec3 b = head.add(side.scale(i * 0.8));
                t.level.sendParticles(i % 2 == 0 ? BOUEE_ROUGE : BOUEE_BLANCHE, b.x, y + 0.2, b.z, 1, 0.02, 0.02, 0.02, 0);
            }
            // nappe du filet qui bouillonne derrière, plus agitée quand le filet se remplit
            int fill = count(net.getList("Items", Tag.TAG_COMPOUND));
            int bubbles = 4 + Math.min(12, fill);
            Vec3 mid = head.add(back.scale(1.3));
            t.level.sendParticles(ParticleTypes.FISHING, mid.x, y + 0.05, mid.z, bubbles, 1.4, 0.0, 1.0, 0.02);
            t.level.sendParticles(ParticleTypes.BUBBLE, mid.x, y - 0.4, mid.z, bubbles / 2, 1.2, 0.2, 0.9, 0.02);
        }
    }

    // ------------------------------------------------------------------ bonus de lieu, météo, heure

    private record Bonus(double speed, double rareBoost, String label) {}

    private static Bonus bonus(ServerLevel level, BlockPos pos) {
        Holder<Biome> b = level.getBiome(pos);
        double speed = 1, rare = 1;
        List<String> l = new ArrayList<>();
        if (b.is(DEEP_OCEAN)) {
            speed += FishingNetConfig.BONUS_DEEP.get();
            rare = FishingNetConfig.RARE_BOOST_DEEP.get();
            l.add("haute mer");
        } else if (b.is(OCEAN)) {
            speed += FishingNetConfig.BONUS_DEEP.get() / 3;
            l.add("au large");
        } else if (b.is(BEACH) || nearShore(level, pos)) {
            l.add("côte : épaves");
        }
        if (level.isThundering()) {
            speed += FishingNetConfig.BONUS_RAIN.get() * 1.6;
            l.add("orage");
        } else if (level.isRaining()) {
            speed += FishingNetConfig.BONUS_RAIN.get();
            l.add("pluie");
        }
        long t = level.getDayTime() % 24000L;
        if (t >= 22500 || t <= 1000 || (t >= 11500 && t <= 13500)) {
            speed += FishingNetConfig.BONUS_TWILIGHT.get();
            l.add(t >= 11500 && t <= 13500 ? "crépuscule" : "aube");
        }
        return new Bonus(speed, rare, String.join(", ", l));
    }

    /** Terre ferme à moins de 12 blocs (sondage en croix au niveau de l'eau) : zone de côte. */
    private static boolean nearShore(ServerLevel level, BlockPos pos) {
        for (int d = 4; d <= 12; d += 4) {
            for (int[] o : new int[][]{{d, 0}, {-d, 0}, {0, d}, {0, -d}}) {
                BlockPos q = pos.offset(o[0], 0, o[1]);
                if (!level.isLoaded(q)) continue;
                if (!level.getFluidState(q).is(FluidTags.WATER) && !level.getFluidState(q.below()).is(FluidTags.WATER)
                        && level.getBlockState(q.below()).isSolid()) return true;
            }
        }
        return false;
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

    /** Retire un poisson de la liste (le dernier pris en premier). */
    private static ItemStack takeOne(ListTag items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            ItemStack cur = ItemStack.of(items.getCompound(i));
            if (cur.isEmpty()) {
                items.remove(i);
                continue;
            }
            ItemStack one = cur.split(1);
            if (cur.isEmpty()) items.remove(i); else items.set(i, cur.save(new CompoundTag()));
            return one;
        }
        return ItemStack.EMPTY;
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
