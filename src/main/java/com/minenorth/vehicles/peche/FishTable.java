package com.minenorth.vehicles.peche;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minenorth.vehicles.VehiclesMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Table de pêche lue dans les données de Star Catcher ({@code data/<ns>/starcatcher/fish/*.json}, datapacks compris) :
 * même objet, même rareté, même chance de base et mêmes conditions que la canne. Rien n'est figé dans le code :
 * une mise à jour de Star Catcher ou un datapack qui ajoute des poissons est pris en compte au /reload.
 */
public final class FishTable {
    private FishTable() {}

    /** Condition d'une fiche : vraie si le poisson peut mordre à cet endroit, à ce moment. */
    private interface Rule { boolean test(ServerLevel level, BlockPos pos, Holder<Biome> biome); }

    public record Fish(Item item, String rarity, int weight, List<Rule> rules) {
        boolean allowed(ServerLevel level, BlockPos pos, Holder<Biome> biome) {
            for (Rule r : rules) if (!r.test(level, pos, biome)) return false;
            return true;
        }
    }

    private static volatile List<Fish> fish;

    /** Vide le cache (au /reload et au démarrage). */
    public static void invalidate() { fish = null; }

    public static List<Fish> all(MinecraftServer server) {
        List<Fish> f = fish;
        if (f == null) {
            f = load(server);
            fish = f;
        }
        return f;
    }

    /** Tire un poisson possible à cet endroit (pondéré par la chance de base Star Catcher), ou null si rien ne mord ici. */
    public static Item roll(ServerLevel level, BlockPos pos, RandomSource rnd) {
        return roll(level, pos, rnd, 1.0);
    }

    /** Idem, les raretés rare / épique / légendaire de Star Catcher pesant {@code rareBoost} fois plus (bonus de haute mer). */
    public static Item roll(ServerLevel level, BlockPos pos, RandomSource rnd, double rareBoost) {
        Holder<Biome> biome = level.getBiome(pos);
        List<Fish> ok = new ArrayList<>();
        double total = 0;
        for (Fish f : all(level.getServer())) {
            if (f.allowed(level, pos, biome)) {
                ok.add(f);
                total += weight(f, rareBoost);
            }
        }
        if (total <= 0) return null;
        double r = rnd.nextDouble() * total;
        for (Fish f : ok) {
            r -= weight(f, rareBoost);
            if (r < 0) return f.item;
        }
        return ok.get(ok.size() - 1).item;
    }

    private static double weight(Fish f, double rareBoost) {
        boolean rare = f.rarity().equals("rare") || f.rarity().equals("epic") || f.rarity().equals("legendary");
        return f.weight() * (rare ? rareBoost : 1.0);
    }

    private static List<Fish> load(MinecraftServer server) {
        Set<String> rarities = lower(FishingNetConfig.RARITIES.get());
        Set<String> namespaces = lower(FishingNetConfig.NAMESPACES.get());
        List<Fish> out = new ArrayList<>();
        Map<ResourceLocation, Resource> files = server.getResourceManager()
                .listResources("starcatcher/fish", rl -> rl.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> e : files.entrySet()) {
            try (Reader in = e.getValue().openAsReader()) {
                Fish f = parse(JsonParser.parseReader(in).getAsJsonObject(), rarities, namespaces);
                if (f != null) out.add(f);
            } catch (Exception ex) {
                VehiclesMod.LOGGER.warn("[Filet] Fiche Star Catcher illisible {} : {}", e.getKey(), ex.toString());
            }
        }
        VehiclesMod.LOGGER.info("[Filet] {} poissons Star Catcher pêchables au filet (sur {} fiches)", out.size(), files.size());
        return List.copyOf(out);
    }

    private static Set<String> lower(List<? extends String> l) {
        Set<String> s = new java.util.HashSet<>();
        for (String x : l) s.add(x.trim().toLowerCase(Locale.ROOT));
        return s;
    }

    private static Fish parse(JsonObject d, Set<String> rarities, Set<String> namespaces) {
        JsonObject ci = d.getAsJsonObject("catch_info");
        if (ci == null || (ci.has("type") && !"fish".equals(ci.get("type").getAsString()))) return null;   // modèles de forge, trophées
        if (!ci.has("item")) return null;
        ResourceLocation id = ResourceLocation.tryParse(ci.getAsJsonObject("item").get("identifier").getAsString());
        if (id == null || !namespaces.contains(id.getNamespace())) return null;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || !ForgeRegistries.ITEMS.containsKey(id)) return null;
        String rarity = d.has("rarity") ? d.get("rarity").getAsString().toLowerCase(Locale.ROOT) : "";
        if (!rarities.contains(rarity)) return null;
        int weight = d.has("base_chance") ? d.get("base_chance").getAsInt() : 0;
        if (weight <= 0) return null;
        List<Rule> rules = new ArrayList<>();
        boolean water = false;
        if (d.has("restrictions")) {
            for (JsonElement el : d.getAsJsonArray("restrictions")) {
                JsonObject r = el.getAsJsonObject();
                String type = r.get("type").getAsString();
                switch (type) {
                    case "starcatcher:dimension" -> {
                        String dim = r.get("dimension_entry").getAsString();
                        ResourceLocation want = dim.contains(":") ? new ResourceLocation(dim) : new ResourceLocation("minecraft", dim);
                        rules.add((lv, p, b) -> lv.dimension().location().equals(want));
                    }
                    case "starcatcher:fluid" -> {
                        for (JsonElement fl : r.getAsJsonArray("fluids")) if ("minecraft:water".equals(fl.getAsString())) water = true;
                    }
                    case "starcatcher:biome" -> rules.add(biomeRule(r));
                    case "starcatcher:elevation_restriction" -> {
                        long min = r.has("min_y") ? r.get("min_y").getAsLong() : Integer.MIN_VALUE;
                        long max = r.has("max_y") ? r.get("max_y").getAsLong() : Integer.MAX_VALUE;
                        rules.add((lv, p, b) -> p.getY() >= min && p.getY() <= max);
                    }
                    case "starcatcher:daytime_restriction" -> {
                        List<long[]> ranges = new ArrayList<>();
                        for (JsonElement x : r.getAsJsonArray("ranges")) {
                            JsonObject o = x.getAsJsonObject();
                            ranges.add(new long[]{o.get("min").getAsLong(), o.get("max").getAsLong()});
                        }
                        rules.add((lv, p, b) -> {
                            long t = lv.getDayTime() % 24000L;
                            for (long[] g : ranges) if (t >= g[0] && t <= g[1]) return true;
                            return false;
                        });
                    }
                    case "starcatcher:weather_restriction" -> {
                        String w = r.get("weather").getAsString();
                        rules.add((lv, p, b) -> switch (w) {
                            case "rain" -> lv.isRaining();
                            case "thunder" -> lv.isThundering();
                            case "clear" -> !lv.isRaining();
                            default -> true;
                        });
                    }
                    case "starcatcher:elevation_bias" -> { /* préférence d'altitude : n'interdit rien */ }
                    default -> { return null; }   // appât, structure, limite de prises, lumière, pourcentage... : réservé à la canne
                }
            }
        }
        if (!water) return null;
        return new Fish(item, rarity, weight, List.copyOf(rules));
    }

    private static Rule biomeRule(JsonObject r) {
        List<String> allow = strings(r.getAsJsonArray("biomes"));
        List<String> deny = r.has("blacklist") ? strings(r.getAsJsonArray("blacklist")) : List.of();
        return (lv, p, b) -> (allow.isEmpty() || matches(b, allow)) && !matches(b, deny);
    }

    private static List<String> strings(JsonArray a) {
        List<String> l = new ArrayList<>();
        if (a != null) for (JsonElement e : a) l.add(e.getAsString());
        return l;
    }

    private static boolean matches(Holder<Biome> b, List<String> ids) {
        for (String s : ids) {
            if (s.startsWith("#")) {
                ResourceLocation t = ResourceLocation.tryParse(s.substring(1));
                if (t != null && b.is(TagKey.create(Registries.BIOME, t))) return true;
            } else {
                ResourceLocation t = ResourceLocation.tryParse(s);
                if (t != null && b.is(t)) return true;
            }
        }
        return false;
    }
}
