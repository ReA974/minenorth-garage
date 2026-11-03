package com.minenorth.vehicles.coffre;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** config/minenorth-coffre.toml */
public final class TrunkConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED, SNEAK_CLICK, NEED_GARAGE_ZONE, KEY, NEED_OPEN, UNKNOWN_OPEN;
    public static final ForgeConfigSpec.IntValue DEFAULT_BASE, DEFAULT_MAX, STEP, DISTANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SIZES, TYPE_SIZES, TRUNK_VARIABLES;
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
        KEY = b.comment("Touche « Coffre du véhicule » (C par défaut, réglable dans Options > Commandes) : à pied, ouvre le coffre",
                        "du véhicule visé ou du plus proche. Au volant, C reste le klaxon de MTS.")
                .define("toucheCoffre", true);
        NEED_OPEN = b.comment("Pour fouiller un coffre (touche, clic accroupi ou menu), le véhicule doit être déverrouillé ET son coffre ouvert",
                        "(porte du coffre ouverte dans MTS), propriétaire compris. Le coffre se referme si on verrouille ou ferme pendant l'usage.",
                        "Sur false : ancien fonctionnement (verrouillé = accessible au seul propriétaire, porte ignorée).")
                .define("coffreOuvertRequis", true);
        UNKNOWN_OPEN = b.comment("Modèle sans porte de coffre reconnue (ni emplacement de coffre MTS, ni variable de la liste) :",
                        "true = le coffre compte comme ouvert (seul le verrouillage compte), false = jamais accessible.")
                .define("sansPorteCoffreOuvert", true);
        TRUNK_VARIABLES = b.comment("Variables MTS de porte de coffre, si l'emplacement de coffre du modèle ne dit pas laquelle ouvrir.",
                        "Le coffre est ouvert si l'une d'elles est active sur le véhicule (nom exact, majuscules comprises).")
                .defineList("variablesPorteCoffre", List.of("door_trunk", "trunk", "trunkback", "door_tailgate", "tailgate", "hatchback",
                                "door_hatchback", "door_boot", "boot", "door_rear", "coffre", "door_coffre"),
                        o -> o instanceof String s && !s.isBlank());
        b.pop();
        b.push("tailles");
        SIZES = b.comment("Taille du coffre par catégorie du catalogue, au format « Catégorie;emplacements de base;emplacements maximum ».",
                        "Les tailles sont arrondies à un multiple de 9 (de 9 à 54). Le nom ignore les majuscules et peut n'être qu'une partie",
                        "du nom de catégorie (« Moto » vaut pour « Motos » ou « Moto cross ») ; le nom exact passe avant, puis le premier de la liste.",
                        "Un coffre déjà plus rempli que sa nouvelle taille garde tout son contenu (la taille ne descend jamais sous le contenu).")
                .defineList("categories", List.of("Scooter;9;9", "Moto;9;18", "Quad;9;18", "Citadine;18;27", "Compacte;18;36", "Sportive;9;27",
                                "Berline;27;36", "Break;27;45", "SUV;27;45", "4x4;27;45", "Pick-up;36;54", "Pickup;36;54", "Utilitaire;36;54",
                                "Van;36;54", "Fourgon;45;54", "Camion;45;54", "Bus;18;36"),
                        o -> o instanceof String s && s.split(";").length == 3);
        TYPE_SIZES = b.comment("Taille par type de véhicule quand la catégorie n'est pas dans la liste ci-dessus (ou véhicule hors catalogue),",
                        "au format « type;base;maximum » : terre, air (avions, hélicoptères), mer (bateaux).")
                .defineList("types", List.of("terre;18;36", "air;27;45", "mer;27;54"), o -> o instanceof String s && s.split(";").length == 3);
        DEFAULT_BASE = b.comment("Emplacements de base si ni la catégorie ni le type ne sont dans les listes.")
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
