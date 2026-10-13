package com.minenorth.vehicles.shop;

import com.minenorth.vehicles.VehiclesMod;
import com.minenorth.vehicles.config.VehicleConfig;
import com.minenorth.vehicles.miscs.Msg;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * /vendeur <id> [joueur]      : ouvre un vendeur (pour un PNJ : vendeur camions %player%)
 * /vendeurzone set <id>        : zone de pose de ce vendeur (se placer au centre)
 * /vendeurzone list | remove <id>
 * /vendeuracces <id> tous | mairie | entreprise <nom> [direction] : concession réservée à la mairie ou à une entreprise
 */
@Mod.EventBusSubscriber(modid = VehiclesMod.MODID)
public final class ShopMultiCommands {
    private static final SuggestionProvider<CommandSourceStack> SHOPS = (ctx, b) -> SharedSuggestionProvider.suggest(ShopProfiles.ids(), b);

    private ShopMultiCommands() {}

    private static boolean admin(CommandSourceStack s) {
        return s.hasPermission(VehicleConfig.COMMAND_LEVEL.get());
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("vendeur").requires(ShopMultiCommands::admin)
                .then(Commands.argument("shop", StringArgumentType.word()).suggests(SHOPS)
                        .executes(c -> {
                            ShopMulti.open(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "shop"));
                            return 1;
                        })
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            ShopMulti.open(EntityArgument.getPlayer(c, "player"), StringArgumentType.getString(c, "shop"));
                            return 1;
                        }))));

        // /vendeuracces <id> tous | mairie | entreprise <nom> [direction] : qui peut acheter dans cette concession (écrit dans shops.json)
        e.getDispatcher().register(Commands.literal("vendeuracces").requires(ShopMultiCommands::admin)
                .then(Commands.argument("shop", StringArgumentType.word()).suggests(SHOPS)
                        .then(Commands.literal("tous").executes(c -> access(c, "tous", "", false)))
                        .then(Commands.literal("mairie").executes(c -> access(c, "mairie", "", false)))
                        .then(Commands.literal("entreprise")
                                .then(Commands.argument("nom", StringArgumentType.string()).suggests(COMPANIES)
                                        .executes(c -> access(c, "entreprise", StringArgumentType.getString(c, "nom"), false))
                                        .then(Commands.literal("direction")
                                                .executes(c -> access(c, "entreprise", StringArgumentType.getString(c, "nom"), true)))))));

        e.getDispatcher().register(Commands.literal("vendeurzone").requires(ShopMultiCommands::admin)
                .then(Commands.literal("set").then(Commands.argument("shop", StringArgumentType.word()).suggests(SHOPS)
                        .executes(ShopMultiCommands::set)))
                .then(Commands.literal("remove").then(Commands.argument("shop", StringArgumentType.word()).suggests(SHOPS)
                        .executes(ShopMultiCommands::remove)))
                .then(Commands.literal("list").executes(c -> list(c.getSource().getPlayerOrException()))));
    }

    /** Entreprises actives (liste de complétion), entre guillemets si le nom contient un espace. */
    private static final SuggestionProvider<CommandSourceStack> COMPANIES = (ctx, b) -> {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String s : fr.minenorth.api.MineNorth.companies().activeCompanies(ctx.getSource().getServer())) {
            String[] parts = s.split("\\|", 2);
            if (parts.length == 2) names.add(parts[1].contains(" ") ? "\"" + parts[1] + "\"" : parts[1]);
        }
        return SharedSuggestionProvider.suggest(names, b);
    };

    private static int access(CommandContext<CommandSourceStack> c, String mode, String company, boolean direction) {
        String id = StringArgumentType.getString(c, "shop").toLowerCase(Locale.ROOT);
        if (mode.equals("entreprise")) {
            int cid = fr.minenorth.api.MineNorth.companies().idOf(c.getSource().getServer(), company);
            if (cid <= 0) {
                c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Entreprise active introuvable : " + company));
                return 0;
            }
            company = fr.minenorth.api.MineNorth.companies().name(c.getSource().getServer(), cid);   // nom exact
        }
        String err = ShopProfiles.setAccess(id, mode, company, direction);
        if (err != null) {
            c.getSource().sendFailure(net.minecraft.network.chat.Component.literal(err));
            return 0;
        }
        String what = mode.equals("tous") ? "ouverte à tout le monde" : mode.equals("mairie") ? "réservée à la mairie (maire, agents et staff)"
                : "réservée " + (direction ? "à la direction de " : "aux employés de ") + company + " (et au staff)";
        c.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal("Concession \"" + id + "\" " + what + "."));
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "shop").toLowerCase(Locale.ROOT);
        if (ShopProfiles.get(id) == null) {
            Msg.send(p, "&cVendeur inconnu : " + id + " (ajoute-le dans shops.json). Vendeurs : " + ShopProfiles.ids());
            return 0;
        }
        ShopZoneData d = ShopZoneData.get(p.getServer());
        ShopZoneData.Zone z = new ShopZoneData.Zone();
        z.dim = p.level().dimension().location().toString();
        z.pos = p.blockPosition().below();
        d.zones.put(id, z);
        d.setDirty();
        Msg.send(p, "&aZone de pose du vendeur \"" + id + "\" définie à ta position.");
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "shop").toLowerCase(Locale.ROOT);
        ShopZoneData d = ShopZoneData.get(p.getServer());
        if (d.zones.remove(id) == null) {
            Msg.send(p, "&cPas de zone pour : " + id);
            return 0;
        }
        d.setDirty();
        Msg.send(p, "&aZone du vendeur \"" + id + "\" supprimée.");
        return 1;
    }

    private static int list(ServerPlayer p) {
        ShopZoneData d = ShopZoneData.get(p.getServer());
        Msg.send(p, "&eVendeurs (shops.json) :");
        for (String id : ShopProfiles.ids()) {
            ShopProfiles.Profile sp = ShopProfiles.get(id);
            ShopZoneData.Zone z = d.zones.get(id);
            String zone = z == null ? "&cpas de zone" : "&azone " + z.pos.getX() + " " + z.pos.getY() + " " + z.pos.getZ();
            Msg.send(p, "&7- &f" + id + " &8| " + (sp.categories.isEmpty() ? "toutes catégories" : String.join(", ", sp.categories)) + " &8| " + zone);
        }
        return 1;
    }
}
