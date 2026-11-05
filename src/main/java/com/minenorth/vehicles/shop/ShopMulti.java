package com.minenorth.vehicles.shop;

import com.minenorth.vehicles.VehiclesMod;
import com.minenorth.vehicles.config.VehicleConfig;
import com.minenorth.vehicles.handle.Money;
import com.minenorth.vehicles.handle.EconomyBridge;
import com.minenorth.vehicles.handle.MtsBridge;
import com.minenorth.vehicles.handle.MtsFuel;
import com.minenorth.vehicles.handle.Payment;
import com.minenorth.vehicles.menu.MenuNet;
import com.minenorth.vehicles.menu.OpenShopPacket;
import com.minenorth.vehicles.menu.ScreenActionPacket;
import com.minenorth.vehicles.miscs.Gui;
import com.minenorth.vehicles.miscs.Msg;
import com.minenorth.vehicles.registry.IdentityBridge;
import com.minenorth.vehicles.registry.PlateRegistry;
import fr.minenorth.api.PayResult;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/** Vendeurs multiples : chacun a ses catégories (shops.json) et sa propre zone de pose (/vendeurzone set <id>). */
@Mod.EventBusSubscriber(modid = VehiclesMod.MODID)
public final class ShopMulti {
    public static final class Waiting {
        public String shopId, itemId;
        public long expireTick;
    }

    public static final Map<UUID, Waiting> WAITING = new HashMap<>();

    private record Check(Entity entity, long due) {}

    private static final List<Check> CHECKS = new ArrayList<>();

    private ShopMulti() {}

    private static Item registryItem(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item it = rl == null ? Items.AIR : ForgeRegistries.ITEMS.getValue(rl);
        return it == null ? Items.AIR : it;
    }

    private static Item iconOf(String id) {
        Item it = registryItem(id);
        return it == Items.AIR ? Items.MINECART : it;
    }

    // ------------------------------------------------------------------ menus

    public static void open(ServerPlayer p, String shopId) {
        ShopProfiles.Profile sp = ShopProfiles.get(shopId);
        if (sp == null) {
            Msg.send(p, "&cVendeur inconnu : " + shopId + " (voir shops.json).");
            return;
        }
        if (!sp.service.isEmpty() && !dutyOk(p, sp.service)) return;
        if (!accessOk(p, sp)) return;
        if (!ShopZoneData.get(p.getServer()).zones.containsKey(sp.id)) {
            Msg.send(p, "&cAucune zone de pose pour ce vendeur. Le staff doit faire /vendeurzone set " + sp.id + ".");
            return;
        }
        List<String> cats = categoriesOf(sp);
        if (cats.isEmpty()) {
            Msg.send(p, "&cAucun véhicule pour ce vendeur (voir catalog.json et shops.json).");
            return;
        }
        if (VehicleConfig.CUSTOM_GUI.get() && MenuNet.hasClientMod(p)) {   // écran concession (aperçu 3D) ; coffre pour les clients sans le mod
            openScreen(p, sp, cats);
            return;
        }
        if (cats.size() == 1) openModels(p, sp, cats.get(0), false);
        else openCategories(p, sp, cats);
    }

    /** Concession de service (police / pompiers) : il faut appartenir au service ET avoir pris son service (le staff passe toujours). */
    private static boolean dutyOk(ServerPlayer p, String service) {
        if (p.hasPermissions(2)) return true;
        if (!com.minenorth.vehicles.garage.Garage.serviceMember(p, service)) {
            Msg.send(p, "&cConcession réservée aux " + com.minenorth.vehicles.garage.Garage.serviceLabel(service) + ".");
            return false;
        }
        if (!com.minenorth.vehicles.garage.Garage.serviceOnDuty(p, service)) {
            Msg.send(p, "&cPrends d'abord ton service (tablette > prise de service) pour accéder à la concession.");
            return false;
        }
        return true;
    }

    /** Concession réservée à la mairie (maire, agents) ou à une entreprise (membres ou direction) ; le staff passe toujours. */
    private static boolean accessOk(ServerPlayer p, ShopProfiles.Profile sp) {
        if (!sp.mairie && sp.company.isEmpty()) return true;
        if (p.hasPermissions(2)) return true;
        if (sp.mairie) {
            if (fr.minenorth.api.MineNorth.state() == fr.minenorth.api.StateService.NONE) {
                Msg.send(p, "&cConcession indisponible : réservée à la mairie mais le mod État est absent.");
                return false;
            }
            if (!fr.minenorth.api.MineNorth.state().isTownHall(p)) {
                Msg.send(p, "&cConcession réservée à la mairie (maire et agents).");
                return false;
            }
            return true;
        }
        var companies = fr.minenorth.api.MineNorth.companies();
        if (companies == fr.minenorth.api.CompanyService.NONE) {
            Msg.send(p, "&cConcession indisponible : réservée à une entreprise mais le mod Entreprises est absent.");
            return false;
        }
        int id = companies.idOf(p.getServer(), sp.company);
        if (id <= 0) {
            Msg.send(p, "&cConcession réservée à l'entreprise « " + sp.company + " » qui n'existe plus.");
            return false;
        }
        if (!companies.isMember(p, id, sp.companyDirection)) {
            Msg.send(p, "&cConcession réservée " + (sp.companyDirection ? "à la direction de " : "aux employés de ") + sp.company + ".");
            return false;
        }
        return true;
    }

    private static List<String> categoriesOf(ShopProfiles.Profile sp) {
        List<String> out = new ArrayList<>();
        for (String c : Catalog.CATEGORIES) {
            if (!sp.categories.isEmpty() && sp.categories.stream().noneMatch(x -> x.equalsIgnoreCase(c))) continue;
            for (Catalog.Vehicle v : Catalog.VEHICLES) {
                if (v.category.equals(c)) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
    }

    /** Écran concession : tous les modèles des catégories du vendeur, couleurs et prix remisés ; l'achat repasse par buy(). */
    private static void openScreen(ServerPlayer p, ShopProfiles.Profile sp, List<String> cats) {
        List<List<Catalog.Vehicle>> variants = new ArrayList<>();
        List<OpenShopPacket.Model> models = new ArrayList<>();
        for (String cat : cats) {
            Map<String, List<Catalog.Vehicle>> byModel = new LinkedHashMap<>();
            for (Catalog.Vehicle v : Catalog.VEHICLES) {
                if (v.category.equals(cat)) byModel.computeIfAbsent(v.model, k -> new ArrayList<>()).add(v);
            }
            for (List<Catalog.Vehicle> vs : byModel.values()) {
                List<OpenShopPacket.Color> colors = new ArrayList<>();
                int seats = 0, trunk = 0, speed = 0;
                for (Catalog.Vehicle v : vs) {
                    int price = (int) fr.minenorth.api.MineNorth.discounted(p.server, p.getUUID(), sp.priceOf(v.price));
                    colors.add(new OpenShopPacket.Color(v.color, v.item, Math.max(0, price)));
                    seats = Math.max(seats, v.seats);
                    trunk = Math.max(trunk, v.trunk);
                    speed = Math.max(speed, v.speed);
                }
                variants.add(vs);
                models.add(new OpenShopPacket.Model(vs.get(0).model, cat, seats, trunk, speed, colors));
            }
        }
        PayResult card = Payment.cardStatus(p, 1);
        String cardError = card == PayResult.OK || card == PayResult.INSUFFICIENT_FUNDS ? "" : card.message();
        int id = MenuNet.openScreen(p, m -> {
            if (m.action == ScreenActionPacket.SHOW_GARAGE) {
                com.minenorth.vehicles.garage.Garage.openScreen(p);
                return;
            }
            if (m.action != ScreenActionPacket.BUY_CASH && m.action != ScreenActionPacket.BUY_CARD) return;
            if (m.a < 0 || m.a >= variants.size() || m.b < 0 || m.b >= variants.get(m.a).size()) return;
            Catalog.Vehicle v = variants.get(m.a).get(m.b);
            if (!Catalog.VEHICLES.contains(v)) {   // catalogue rechargé entre-temps
                Msg.send(p, "&cLe catalogue a changé, rouvre la concession.");
                return;
            }
            buy(p, sp, v, m.action == ScreenActionPacket.BUY_CARD && sp.priceOf(v.price) > 0 ? Payment.Method.CARD : Payment.Method.CASH);
        });
        MenuNet.send(p, new OpenShopPacket(id, sp.title, Money.count(p, VehicleConfig.currency()), EconomyBridge.balance(p),
                cardError, WAITING.containsKey(p.getUUID()), models));
    }

    private static void openCategories(ServerPlayer p, ShopProfiles.Profile sp, List<String> cats) {
        int n = Math.min(54, cats.size());
        ItemStack[] items = new ItemStack[n];
        for (int i = 0; i < n; i++) {
            Item icon = registryItem(Catalog.CATEGORY_ICON.getOrDefault(cats.get(i), "minecraft:book"));
            items[i] = Gui.item(icon == Items.AIR ? Items.BOOK : icon, "&e&l" + cats.get(i));
        }
        Gui.open(p, sp.title, (n + 8) / 9, items, slot -> {
            if (slot < n) Gui.later(p, () -> openModels(p, sp, cats.get(slot), true));
        });
    }

    private static void openModels(ServerPlayer p, ShopProfiles.Profile sp, String cat, boolean multi) {
        Map<String, List<Catalog.Vehicle>> models = new LinkedHashMap<>();
        for (Catalog.Vehicle v : Catalog.VEHICLES) {
            if (v.category.equals(cat)) models.computeIfAbsent(v.model, k -> new ArrayList<>()).add(v);
        }
        List<String> names = new ArrayList<>(models.keySet());
        int n = Math.min(53, names.size());
        int rows = (n + (multi ? 1 : 0) + 8) / 9;
        int back = rows * 9 - 1;
        ItemStack[] items = new ItemStack[rows * 9];
        for (int i = 0; i < n; i++) {
            List<Catalog.Vehicle> vs = models.get(names.get(i));
            int min = Integer.MAX_VALUE;
            for (Catalog.Vehicle v : vs) min = Math.min(min, sp.priceOf(v.price));
            items[i] = Gui.item(iconOf(vs.get(0).item), "&e&l" + names.get(i),
                    "&7À partir de &a" + (min == 0 ? "Gratuit" : min + "€"), "&7" + vs.size() + " couleur(s)");
        }
        if (multi) items[back] = Gui.item(Items.ARROW, "&c« Retour");
        Gui.open(p, "&e" + cat, rows, items, slot -> {
            if (slot < n) Gui.later(p, () -> openColors(p, sp, cat, names.get(slot), multi));
            else if (multi && slot == back) Gui.later(p, () -> open(p, sp.id));
        });
    }

    private static void openColors(ServerPlayer p, ShopProfiles.Profile sp, String cat, String model, boolean multi) {
        List<Catalog.Vehicle> list = new ArrayList<>();
        for (Catalog.Vehicle v : Catalog.VEHICLES) {
            if (v.category.equals(cat) && v.model.equals(model)) list.add(v);
        }
        int n = Math.min(53, list.size());
        int rows = (n + 1 + 8) / 9;
        int back = rows * 9 - 1;
        ItemStack[] items = new ItemStack[rows * 9];
        for (int i = 0; i < n; i++) {
            Catalog.Vehicle v = list.get(i);
            Item glass = registryItem(Catalog.COLOR_ICON.getOrDefault(v.color, "minecraft:white_stained_glass"));
            items[i] = Gui.item(glass == Items.AIR ? Items.WHITE_STAINED_GLASS : glass, "&e&l" + v.color + " &7- &a" + (sp.priceOf(v.price) == 0 ? "Gratuit" : sp.priceOf(v.price) + "€"),
                    sp.priceOf(v.price) == 0 ? "&7Clique pour récupérer le véhicule (dotation)" : "&7Clique pour choisir le mode de paiement");
        }
        items[back] = Gui.item(Items.ARROW, "&c« Retour");
        Gui.open(p, "&0" + model, rows, items, slot -> {
            if (slot < n) Gui.later(p, () -> {
                Catalog.Vehicle chosen = list.get(slot);
                if (sp.priceOf(chosen.price) == 0) {   // dotation : pas de paiement
                    p.closeContainer();
                    buy(p, sp, chosen, Payment.Method.CASH);
                } else openPayment(p, sp, cat, model, multi, chosen);
            });
            else if (slot == back) Gui.later(p, () -> openModels(p, sp, cat, multi));
        });
    }

    /** Choix du mode de paiement : espèces ou carte, avec l'état de chacun. */
    private static void openPayment(ServerPlayer p, ShopProfiles.Profile sp, String cat, String model, boolean multi, Catalog.Vehicle v) {
        List<VehicleConfig.Denom> den = VehicleConfig.currency();
        int cash = Money.count(p, den);
        int price = (int) fr.minenorth.api.MineNorth.discounted(p.server, p.getUUID(), sp.priceOf(v.price));   // remise de grade (VIP)
        PayResult card = Payment.cardStatus(p, price);
        String label = v.model + (v.color.equals("Défaut") ? "" : " (" + v.color + ")");

        ItemStack[] items = new ItemStack[9];
        items[4] = Gui.item(iconOf(v.item), "&e&l" + label, "&7Prix : &a" + price + "€");
        Item cashIcon = VehicleConfig.item("minenorth_eurobank:bill_50e");
        items[2] = Gui.item(cashIcon == Items.AIR ? Items.GOLD_INGOT : cashIcon, "&a&lPayer en espèces",
                "&7Espèces sur toi : &f" + cash + "€",
                cash >= price ? "&aClique pour payer" : "&cEspèces insuffisantes");
        Item cardIcon = VehicleConfig.item("minenorth_eurobank:bank_card");
        String cardLine = card == PayResult.OK ? "&aClique pour payer"
                : "&c" + (card == PayResult.INSUFFICIENT_FUNDS ? "Solde insuffisant" : card.message());
        items[6] = Gui.item(cardIcon == Items.AIR ? Items.PAPER : cardIcon, "&b&lPayer par carte",
                "&7Solde du compte : &f" + EconomyBridge.balance(p) + "€", cardLine);
        items[8] = Gui.item(Items.ARROW, "&c« Retour");
        Gui.open(p, "&0Paiement", 1, items, slot -> {
            if (slot == 2) Gui.later(p, () -> {
                p.closeContainer();
                buy(p, sp, v, Payment.Method.CASH);
            });
            else if (slot == 6) Gui.later(p, () -> {
                p.closeContainer();
                buy(p, sp, v, Payment.Method.CARD);
            });
            else if (slot == 8) Gui.later(p, () -> openColors(p, sp, cat, model, multi));
        });
    }

    // ------------------------------------------------------------------ achat

    private static void buy(ServerPlayer p, ShopProfiles.Profile sp, Catalog.Vehicle v, Payment.Method method) {
        MinecraftServer server = p.getServer();
        if (!sp.service.isEmpty() && !dutyOk(p, sp.service)) return;     // re-vérifié à l'achat : service quitté entre-temps
        if (!accessOk(p, sp)) return;                                    // idem pour la mairie / l'entreprise
        int price = (int) fr.minenorth.api.MineNorth.discounted(p.server, p.getUUID(), sp.priceOf(v.price));
        if (!ShopZoneData.get(server).zones.containsKey(sp.id)) {
            Msg.send(p, "&cAucune zone de pose pour ce vendeur.");
            return;
        }
        if (WAITING.containsKey(p.getUUID())) {
            Msg.send(p, "&cTu as déjà un véhicule à poser dans la zone. Pose-le d'abord !");
            return;
        }
        Item it = registryItem(v.item);
        if (it == Items.AIR) {
            Msg.send(p, "&cItem du véhicule introuvable : " + v.item + " (voir catalog.json, /vehicleshop check).");
            return;
        }
        List<VehicleConfig.Denom> den = VehicleConfig.currency();
        if (price > 0) {
            String err = Payment.precheck(p, price, method, den, "pour acheter ce véhicule");
            if (err != null) {
                Msg.send(p, "&c" + err);
                return;
            }
            Payment.Result paid = Payment.pay(p, price, method, den, "garage:vente");
            if (!paid.ok()) {
                Msg.send(p, "&c" + paid.message());
                return;
            }
        }
        String plate = generatePlate(server);
        Item plateItem = registryItem(PLATE_ITEM);
        boolean plates = plateItem != Items.AIR && sp.givesPlates();
        String label = v.model + (v.color.equals("Défaut") ? "" : " (" + v.color + ")");

        // 1) MTS fait apparaître le véhicule dans la zone de pose (comme si on posait l'item), la plaque est montée juste après
        Entity spawned = spawnInZone(p, sp, v.item);
        if (spawned != null) {
            if (plates) register(p, sp, v, method, plate);
            giveKey(p, label);
            ARRIVALS.add(new Arrival(spawned, p.getUUID(), plates ? plate : null, server.getTickCount() + 10));
            Msg.send(p, (price == 0 ? "&a" + label + " : véhicule de service remis" : "&a" + label + " achetée pour " + price + "€ ("
                    + (method == Payment.Method.CARD ? "carte" : "espèces") + ")") + " ! Elle t'attend dans la zone de livraison.");
            return;
        }

        // 2) repli : l'item du véhicule à poser soi-même dans la zone (ancien fonctionnement)
        handOver(p, sp, v, method, plates ? plate : null, plateItem, label, price);
    }

    private static final String PLATE_ITEM = "mts:gvp.eu_plate";

    /** Ancien fonctionnement : item du véhicule + plaques + clé, à poser dans la zone marquée. */
    private static void handOver(ServerPlayer p, ShopProfiles.Profile sp, Catalog.Vehicle v, Payment.Method method, String plate,
                                 Item plateItem, String label, int price) {
        ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(registryItem(v.item)));
        if (plate != null) {
            register(p, sp, v, method, plate);
            givePlates(p, plateItem, plate);
        }
        giveKey(p, label);
        Waiting w = new Waiting();
        w.shopId = sp.id;
        w.itemId = v.item;
        w.expireTick = p.getServer().getTickCount() + VehicleConfig.PLACE_TIMEOUT.get() * 20L;
        WAITING.put(p.getUUID(), w);
        Msg.send(p, price == 0 ? "&a" + label + " : véhicule de service remis ! Pose-le sur la zone marquée."
                : "&a" + label + " achetée pour " + price + "€ (" + (method == Payment.Method.CARD ? "carte" : "espèces")
                + ") ! Pose ton véhicule sur la zone marquée.");
    }

    private static void givePlates(ServerPlayer p, Item plateItem, String plate) {
        ItemStack plateStack = new ItemStack(plateItem, 2);
        CompoundTag plateNbt = plateStack.getOrCreateTag();
        plateNbt.putString("textCode", plate);
        plateNbt.putString("textCountry Code", "FR");
        plateStack.setHoverName(Gui.comp("&fPlaque : &e" + plate));
        ItemHandlerHelper.giveItemToPlayer(p, plateStack);
    }

    private static void giveKey(ServerPlayer p, String label) {
        if (!VehicleConfig.GIVE_KEY.get()) return;
        Item k = registryItem(VehicleConfig.KEY_ITEM.get());
        if (k == Items.AIR) return;
        ItemStack key = new ItemStack(k);
        key.setHoverName(Gui.comp("&e&l" + VehicleConfig.KEY_NAME_PREFIX.get() + " " + label));
        ItemHandlerHelper.giveItemToPlayer(p, key);
    }

    private static ServerLevel levelOf(MinecraftServer server, String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        return rl == null ? null : server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, rl));
    }

    /** Véhicule neuf posé par MTS au centre de la zone de pose du vendeur, propriétaire défini ; null si l'apparition a échoué. */
    private static Entity spawnInZone(ServerPlayer p, ShopProfiles.Profile sp, String itemId) {
        ShopZoneData.Zone z = ShopZoneData.get(p.getServer()).zones.get(sp.id);
        ServerLevel level = z == null ? null : levelOf(p.getServer(), z.dim);
        if (level == null) return null;
        Vec3 c = Vec3.atCenterOf(z.pos);
        double y = z.pos.getY() + 1;
        // Sens de la zone (admin au /vendeurzone set) ; ancienne zone : sens de l'acheteur, arrondi au quart de tour.
        // MTS compte le lacet à l'envers de Minecraft : l'ancien appel (lacet brut) sortait le véhicule en miroir, de travers.
        float yaw = Float.isNaN(z.yaw) ? MtsBridge.snap90(p.getYRot()) : z.yaw;
        if (!MtsBridge.spawnNew(level, itemId, c.x, y, c.z, (float) MtsBridge.mtsYaw(yaw), p)) return null;
        // l'entité vient d'être ajoutée au monde : c'est le véhicule sans propriétaire de ce modèle le plus proche du point
        Entity best = null, anyNew = null;
        double bd = Double.MAX_VALUE, ad = Double.MAX_VALUE;
        for (Entity e : level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(c, c).inflate(8), MtsBridge::isVehicle)) {
            if (MtsBridge.ownerOf(e) != null) continue;
            double d = e.distanceToSqr(c.x, y, c.z);
            if (d < ad) {
                ad = d;
                anyNew = e;
            }
            if (d < bd && itemId.equalsIgnoreCase(MtsBridge.itemId(MtsBridge.snapshot(e)))) {
                bd = d;
                best = e;
            }
        }
        if (best == null) best = anyNew;   // id d'item écrit autrement : le véhicule sans propriétaire le plus proche
        if (best == null) VehiclesMod.LOGGER.warn("[Vehicules] {} posé par MTS mais introuvable dans la zone {}.", itemId, sp.id);
        else MtsBridge.setOwner(best, p.getUUID());
        return best;
    }

    /** Véhicule neuf apparu : la plaque est montée et le plein fait quelques ticks après l'apparition. */
    private record Arrival(Entity vehicle, UUID player, String plate, long due) {}

    private static final List<Arrival> ARRIVALS = new ArrayList<>();

    private static void checkArrival(MinecraftServer server, Arrival a) {
        Entity e = a.vehicle();
        ServerPlayer p = server.getPlayerList().getPlayer(a.player());
        if (e.isRemoved() || !(e.level() instanceof ServerLevel level)) return;
        if (a.plate() != null) {
            Entity plated = MtsBridge.mountPlate(level, e, PLATE_ITEM, a.plate());
            if (plated != null) {
                e = plated;
                if (p != null) Msg.send(p, "&7Plaque &e" + a.plate() + " &7montée sur ton véhicule.");
            } else if (p != null) {   // pas d'emplacement de plaque sur ce modèle : plaques à poser soi-même
                Item plateItem = registryItem(PLATE_ITEM);
                if (plateItem != Items.AIR) givePlates(p, plateItem, a.plate());
                Msg.send(p, "&eCe modèle n'a pas d'emplacement de plaque reconnu : plaques &f" + a.plate() + " &edonnées en main.");
            }
        }
        if (VehicleConfig.FILL_FUEL.get()) FUEL.put(e, 100);
    }

    private static double radiusOf(String shopId) {
        ShopProfiles.Profile sp = ShopProfiles.get(shopId);
        return sp != null && sp.placeRadius > 0 ? sp.placeRadius : VehicleConfig.SHOP_PLACE_RADIUS.get();
    }

    /** Inscrit la vente au fichier des immatriculations (consultable par la police) avec l'identité RP de l'acheteur. */
    private static void register(ServerPlayer p, ShopProfiles.Profile sp, Catalog.Vehicle v, Payment.Method method, String plate) {
        PlateRegistry.Entry e = new PlateRegistry.Entry();
        e.plate = plate;
        e.model = v.model;
        e.color = v.color.equals("Défaut") ? "" : v.color;
        e.itemId = v.item;
        e.shopId = sp.id;
        e.method = method == Payment.Method.CARD ? "carte" : "espèces";
        e.price = v.price;
        e.owner = p.getUUID();
        e.ownerName = p.getGameProfile().getName();
        String[] id = IdentityBridge.identity(p.getServer(), p.getUUID());
        if (id != null) {
            e.firstName = id[0];
            e.lastName = id[1];
            e.birthDate = id[2];
            e.birthPlace = id[3];
            e.nationality = id[4];
            e.cardNumber = id[5];
        }
        e.time = System.currentTimeMillis();
        PlateRegistry.get(p.getServer()).add(p.getServer(), e);
        Msg.send(p, "&7Immatriculation &e" + plate + " &7enregistrée au nom de &f" + e.displayName() + "&7.");
    }

    /** Plaque aléatoire qui n'existe pas encore dans le fichier des immatriculations. */
    private static String generatePlate(MinecraftServer server) {
        PlateRegistry reg = PlateRegistry.get(server);
        String plate = generatePlate();
        for (int i = 0; i < 50 && reg.taken(plate); i++) plate = generatePlate();
        return plate;
    }

    private static String generatePlate() {
        String letters = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // Exclusion des lettres ambiguës I/O
        Random r = new Random();
        char l1 = letters.charAt(r.nextInt(letters.length()));
        char l2 = letters.charAt(r.nextInt(letters.length()));
        char l3 = letters.charAt(r.nextInt(letters.length()));
        char l4 = letters.charAt(r.nextInt(letters.length()));
        int num = r.nextInt(900) + 100;
        return "" + l1 + l2 + "-" + num + "-" + l3 + l4;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || e.loadedFromDisk() || WAITING.isEmpty()) return;
        if (!MtsBridge.isVehicle(e.getEntity())) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        CHECKS.add(new Check(e.getEntity(), server.getTickCount() + 5));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        WAITING.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long now = server.getTickCount();

        if (!CHECKS.isEmpty()) {
            List<Check> due = new ArrayList<>();
            Iterator<Check> it = CHECKS.iterator();
            while (it.hasNext()) {
                Check c = it.next();
                if (c.due() <= now) {
                    due.add(c);
                    it.remove();
                }
            }
            for (Check c : due) onVehiclePlaced(server, c.entity());
        }
        if (!WAITING.isEmpty()) tickWaiting(server, now);
        if (!FUEL.isEmpty()) tickFuel();
        if (!ARRIVALS.isEmpty()) {
            List<Arrival> due = new ArrayList<>();
            ARRIVALS.removeIf(a -> a.due() <= now && due.add(a));
            for (Arrival a : due) checkArrival(server, a);
        }
    }

    /** Véhicules achetés en attente du plein -> essais restants. */
    private static final Map<Entity, Integer> FUEL = new HashMap<>();

    /** Plein d'un véhicule neuf dans les ticks qui viennent (moteurs montés juste après l'apparition) : véhicules offerts (GiftVehicles). */
    public static void fillLater(Entity e) { FUEL.put(e, 100); }

    private static void tickFuel() {
        Iterator<Map.Entry<Entity, Integer>> it = FUEL.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, Integer> en = it.next();
            Entity ent = en.getKey();
            if (ent.isRemoved()) {
                it.remove();
                continue;
            }
            MtsFuel.Result r = MtsFuel.fill(ent, VehicleConfig.FUEL_FLUID.get());
            if (r != MtsFuel.Result.RETRY || en.getValue() <= 1) it.remove();
            else en.setValue(en.getValue() - 1);
        }
    }

    private static void onVehiclePlaced(MinecraftServer server, Entity ent) {
        if (ent.isRemoved() || MtsBridge.ownerOf(ent) != null) return;
        ShopZoneData zd = ShopZoneData.get(server);
        String dim = ent.level().dimension().location().toString();
        Iterator<Map.Entry<UUID, Waiting>> it = WAITING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Waiting> en = it.next();
            Waiting w = en.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            ShopZoneData.Zone z = zd.zones.get(w.shopId);
            if (p == null || z == null || !z.dim.equals(dim)) continue;
            double radius = radiusOf(w.shopId);
            if (ent.distanceToSqr(p) > (radius + 14) * (radius + 14)) continue;

            Vec3 c = Vec3.atCenterOf(z.pos);
            double dx = ent.getX() - c.x, dz = ent.getZ() - c.z;
            if (Math.sqrt(dx * dx + dz * dz) <= radius) {
                MtsBridge.setOwner(ent, en.getKey());
                it.remove();
                if (VehicleConfig.FILL_FUEL.get()) FUEL.put(ent, 100);   // plein fait au tick suivant (moteurs montés après l'apparition)
                Msg.send(p, "&aVéhicule posé avec succès ! Bonne route.");
            } else {
                MtsBridge.discard(ent);
                Item item = registryItem(w.itemId);
                if (item != Items.AIR) ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(item));
                Msg.send(p, "&cTu dois poser ton véhicule DANS la zone marquée ! Reprends l'item dans ton inventaire et réessaie.");
            }
            return;
        }
    }

    private static void tickWaiting(MinecraftServer server, long now) {
        ShopZoneData zd = ShopZoneData.get(server);
        Iterator<Map.Entry<UUID, Waiting>> it = WAITING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Waiting> en = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null || now > en.getValue().expireTick) {
                if (p != null) Msg.send(p, "&eLa zone de pose a été libérée (temps écoulé).");
                it.remove();
                continue;
            }
            ShopZoneData.Zone z = zd.zones.get(en.getValue().shopId);
            if (now % 10 != 0 || z == null || !p.level().dimension().location().toString().equals(z.dim)) continue;
            ServerLevel lvl = (ServerLevel) p.level();
            Vec3 c = Vec3.atCenterOf(z.pos);
            double r = radiusOf(en.getValue().shopId);
            lvl.sendParticles(p, ParticleTypes.HAPPY_VILLAGER, true, c.x, c.y + 0.6, c.z, 2, 0.2, 0.05, 0.2, 0.0);
            for (int a = 0; a < 360; a += 30) {
                double rad = Math.toRadians(a);
                lvl.sendParticles(p, ParticleTypes.HAPPY_VILLAGER, true, c.x + Math.cos(rad) * r, c.y + 0.6,
                        c.z + Math.sin(rad) * r, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }
}
