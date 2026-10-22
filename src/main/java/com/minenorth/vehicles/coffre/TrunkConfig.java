package com.minenorth.vehicles.coffre;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** config/minenorth-coffre.toml */
public final class TrunkConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED, SNEAK_CLICK, NEED_GARAGE_ZONE;
    public static final ForgeConfigSpec.IntValue DEFAULT_BASE, DEFAULT_MAX, STEP, DISTANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SIZES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> PRICES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("coffre");
        ENABLED = b.comment("Active le coffre des véhicules et l'extension de coffre.").define("actif", true);
        SNEAK_CLICK = b.comment("Accroupi + clic droit à mains vides sur un véhicule ouvre son menu de coffre.",
                        "À mettre sur false si ce geste gêne une action de MTS : le menu reste ouvrable par « Coffre du véhicule » dans le menu du garage.")
                .define("clicAccroupi", true);
        DISTANCE = b.comment("Distance maximale (blocs) entre le joueur et le véhicule pour utiliser son coffre.")
                .defineInRange("distanceMax", 6, 2, 32);
        b.pop();
        b.push("tailles");
        SIZES = b.comment("Taille du coffre par catégorie du catalogue, au format « Catégorie;emplacements de base;emplacements maximum ».",
                        "Les tailles sont arrondies à un multiple de 9 (de 9 à 54). Le nom de catégorie ignore les majuscules.")
                .defineList("categories", List.of("Moto;9;18", "Citadine;9;18", "Berline;18;36", "SUV;27;45", "Utilitaire;27;54", "Camion;36;54"),
                        o -> o instanceof String s && s.split(";").length == 3);
        DEFAULT_BASE = b.comment("Emplacements de base d'une catégorie absente de la liste (ou d'un véhicule hors catalogue).")
                .defineInRange("baseParDefaut", 18, 9, 54);
        DEFAULT_MAX = b.comment("Emplacements maximum d'une catégorie absente de la liste.").defineInRange("maxParDefaut", 36, 9, 54);
        b.pop();
        b.push("extension");
        STEP = b.comment("Emplacements gagnés à chaque palier d'extension.").defineInRange("emplacementsParPalier", 9, 1, 54);
        PRICES = b.comment("Prix (€) de chaque palier, dans l'ordre : le 1er nombre pour le palier 1, etc.",
                        "Si le véhicule a plus de paliers que de prix, le dernier prix est repris.")
                .defineList("prixDesPaliers", List.of(2000, 4000, 8000, 15000, 25000), o -> o instanceof Integer i && i >= 0);
        NEED_GARAGE_ZONE = b.comment("L'extension ne peut être achetée que dans une zone garage (atelier).").define("zoneGarageRequise", false);
        b.pop();
        SPEC = b.build();
    }

    private TrunkConfig() {}
}
