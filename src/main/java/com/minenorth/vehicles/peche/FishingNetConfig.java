package com.minenorth.vehicles.peche;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** config/minenorth-filet.toml : filet de pêche des bateaux. */
public final class FishingNetConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED, BOATS_ONLY;
    public static final ForgeConfigSpec.IntValue CAPACITY, INTERVAL, HAUL_SECONDS;
    public static final ForgeConfigSpec.DoubleValue MIN_SPEED, MAX_SPEED;
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
        SPEC = b.build();
    }

    private FishingNetConfig() {}
}
