package com.minenorth.vehicles.peche;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** config/minenorth-filet.toml : filet de pêche des bateaux. */
public final class FishingNetConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED, BOATS_ONLY;
    public static final ForgeConfigSpec.IntValue CAPACITY, INTERVAL, HAUL_SECONDS, WEAR_BASE, BOBINE_REPAIR;
    public static final ForgeConfigSpec.DoubleValue MIN_SPEED, MAX_SPEED, TREASURE_CHANCE, BONUS_DEEP, RARE_BOOST_DEEP, BONUS_RAIN, BONUS_TWILIGHT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RARITIES, NAMESPACES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("filet");
        ENABLED = b.comment("Active le filet de pêche des bateaux.").define("actif", true);
        CAPACITY = b.comment("Nombre maximum de poissons dans le filet avant de devoir le remonter.")
                .defineInRange("capacite", 16, 1, 256);
        INTERVAL = b.comment("Secondes de navigation (filet à l'eau, bateau en mouvement) pour attraper un poisson.")
                .defineInRange("secondesParPoisson", 45, 5, 3600);
        HAUL_SECONDS = b.comment("Durée de la remontée du filet (secondes). Le pêcheur doit rester à bord jusqu'au bout.")
                .defineInRange("dureeRemontee", 8, 0, 300);
        MIN_SPEED = b.comment("Vitesse minimale du bateau (km/h, mesurée par le serveur) pour que le filet pêche.")
                .defineInRange("vitesseMinKmh", 3.0, 0.0, 200.0);
        MAX_SPEED = b.comment("Au-dessus de cette vitesse (km/h), le filet traîne en surface et ne prend rien.")
                .defineInRange("vitesseMaxKmh", 40.0, 1.0, 500.0);
        BOATS_ONLY = b.comment("Le filet ne se déploie que depuis un véhicule de type « mer » (catégorie Bateau du catalogue, ou nom de bateau).")
                .define("bateauxSeulement", true);
        RARITIES = b.comment("Raretés Star Catcher que le filet peut ramener (common, uncommon, rare, epic, legendary, trash).",
                        "La chance de chaque poisson est son « base_chance » Star Catcher, avec les mêmes conditions (biome, altitude,",
                        "heure, météo, dimension, eau). Les poissons à appât, à structure ou à limite de prises restent à la canne.")
                .defineList("raretes", List.of("common", "uncommon", "rare", "epic", "legendary"), o -> o instanceof String s && !s.isBlank());
        NAMESPACES = b.comment("Espaces de noms des objets acceptés (par défaut seuls les poissons de Star Catcher, rachetés par la poissonnerie).")
                .defineList("espacesDeNoms", List.of("starcatcher"), o -> o instanceof String s && !s.isBlank());
        b.pop();
        b.push("bonus");
        BONUS_DEEP = b.comment("Haute mer (biomes d'océan profond) : vitesse de pêche en plus (0,35 = +35 %). Au large (océan) : le tiers.")
                .defineInRange("hauteMer", 0.35, 0.0, 5.0);
        RARE_BOOST_DEEP = b.comment("Haute mer : les poissons rares, épiques et légendaires de Star Catcher pèsent autant de fois plus.")
                .defineInRange("raresHauteMer", 1.5, 1.0, 10.0);
        BONUS_RAIN = b.comment("Pluie : vitesse de pêche en plus (orage : x1,6).").defineInRange("pluie", 0.15, 0.0, 5.0);
        BONUS_TWILIGHT = b.comment("Aube et crépuscule : vitesse de pêche en plus.").defineInRange("aubeCrepuscule", 0.20, 0.0, 5.0);
        TREASURE_CHANCE = b.comment("Chance par remontée (filet plein) d'un coffre d'épave ou d'une bouteille à la mer ; doublée près des côtes.")
                .defineInRange("chanceTresor", 0.05, 0.0, 1.0);
        b.pop();
        b.push("usure");
        WEAR_BASE = b.comment("Usure par remontée (le filet a 64 points ; +1 par tranche de 4 poissons). Déchiré à 1 point : plus déployable.")
                .defineInRange("usureParRemontee", 2, 0, 64);
        BOBINE_REPAIR = b.comment("Points rendus au filet par une bobine de fil de pêche (clic droit, filet dans l'autre main).")
                .defineInRange("reparationBobine", 16, 1, 64);
        b.pop();
        SPEC = b.build();
    }

    private FishingNetConfig() {}
}
