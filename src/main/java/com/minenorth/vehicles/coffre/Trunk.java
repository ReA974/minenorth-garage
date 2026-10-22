package com.minenorth.vehicles.coffre;

import com.minenorth.vehicles.VehiclesMod;
import com.minenorth.vehicles.config.VehicleConfig;
import com.minenorth.vehicles.garage.Garage;
import com.minenorth.vehicles.handle.EconomyBridge;
import com.minenorth.vehicles.handle.Money;
import com.minenorth.vehicles.handle.MtsBridge;
import com.minenorth.vehicles.handle.Payment;
import com.minenorth.vehicles.miscs.Gui;
import com.minenorth.vehicles.miscs.Msg;
import com.minenorth.vehicles.shop.Catalog;
import fr.minenorth.api.PayResult;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coffre des véhicules. Le contenu et le palier d'extension sont rangés dans les données persistantes de l'entité
 * (clé {@value #TAG}), donc ils suivent le véhicule au garage, à la fourrière et à la vente sans rien de plus.
 * Tout se passe côté serveur : un seul conteneur partagé par véhicule (pas de copie, pas de duplication), écrit
 * dans le NBT à chaque changement. Si le véhicule disparaît sans passer par ce mod (destruction, revente au receleur...),
 * son contenu est lâché au sol avant la suppression.
 */
@Mod.EventBusSubscriber(modid = VehiclesMod.MODID)
public final class Trunk {
    public static final String TAG = "mnrp_coffre";
    private static final Map<UUID, TrunkContainer> OPEN = new HashMap<>();

    private Trunk() {}

    // ------------------------------------------------------------------ tailles

    private static int round9(int n) {
        return Math.max(9, Math.min(54, ((n + 8) / 9) * 9));
    }

    private static String categoryOf(String itemId) {
        Catalog.Vehicle v = Catalog.byItem(itemId);
        return v == null ? null : v.category;
    }

    /** {base, max} d'emplacements pour ce véhicule, d'après sa catégorie au catalogue. */
    private static int[] range(Entity e) {
        String cat = categoryOf(MtsBridge.itemId(MtsBridge.snapshot(e)));
        int base = TrunkConfig.DEFAULT_BASE.get(), max = TrunkConfig.DEFAULT_MAX.get();
        if (cat != null) {
            for (String s : TrunkConfig.SIZES.get()) {
                String[] p = s.split(";");
                if (p.length != 3 || !p[0].trim().equalsIgnoreCase(cat.trim())) continue;
                try {
                    base = Integer.parseInt(p[1].trim());
                    max = Integer.parseInt(p[2].trim());
                } catch (NumberFormatException ignored) {
                    // ligne illisible : valeurs par défaut
                }
                break;
            }
        }
        base = round9(base);
        return new int[]{base, Math.max(base, round9(max))};
    }

    public static int level(Entity e) {
        return Math.max(0, e.getPersistentData().getCompound(TAG).getInt("Niveau"));
    }

    private static NonNullList<ItemStack> stored(Entity e) {
        NonNullList<ItemStack> list = NonNullList.withSize(54, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(e.getPersistentData().getCompound(TAG), list);
        return list;
    }

    private static int maxLevel(int[] r) {
        return (r[1] - r[0] + TrunkConfig.STEP.get() - 1) / TrunkConfig.STEP.get();
    }

    /** Nombre d'emplacements : base + paliers, jamais moins que ce que le contenu occupe déjà (si la config baisse). */
    public static int size(Entity e) {
        int[] r = range(e);
        int size = Math.min(r[1], r[0] + Math.min(level(e), maxLevel(r)) * TrunkConfig.STEP.get());
        NonNullList<ItemStack> list = stored(e);
        int used = 0;
        for (int i = 0; i < list.size(); i++) if (!list.get(i).isEmpty()) used = i + 1;
        return round9(Math.max(size, used));
    }

    private static int filled(Entity e) {
        int n = 0;
        for (ItemStack s : stored(e)) if (!s.isEmpty()) n++;
        return n;
    }

    private static int priceFor(ServerPlayer p, int level) {
        List<? extends Integer> prices = TrunkConfig.PRICES.get();
        int base = prices.isEmpty() ? 0 : prices.get(Math.min(level, prices.size() - 1));
        return (int) fr.minenorth.api.MineNorth.discounted(p.getServer(), p.getUUID(), base);   // remise de grade (VIP)
    }

    // ------------------------------------------------------------------ accès

    /** Le joueur peut-il se servir du coffre ? Un véhicule verrouillé n'est ouvert que par son propriétaire (OP compris : mêmes règles). */
    private static boolean canUse(ServerPlayer p, Entity e) {
        if (!TrunkConfig.ENABLED.get() || e.isRemoved() || e.level() != p.level() || p.isSpectator()) return false;
        if (p.distanceTo(e) > TrunkConfig.DISTANCE.get() + 2) return false;
        UUID owner = MtsBridge.ownerOf(e);
        return owner != null && owner.equals(p.getUUID()) || !MtsBridge.isLocked(e);
    }

    private static boolean isOwner(ServerPlayer p, Entity e) {
        return p.getUUID().equals(MtsBridge.ownerOf(e));
    }

    // ------------------------------------------------------------------ conteneur partagé

    private static final class TrunkContainer extends SimpleContainer {
        final Entity vehicle;
        int viewers;
        boolean dead, loading;

        TrunkContainer(Entity vehicle, int size) {
            super(size);
            this.vehicle = vehicle;
            loading = true;
            NonNullList<ItemStack> list = stored(vehicle);
            for (int i = 0; i < size; i++) super.setItem(i, list.get(i));
            loading = false;
        }

        private boolean gone() {
            if (vehicle.isRemoved()) dead = true;
            return dead;
        }

        private void save() {
            if (loading || gone()) return;
            NonNullList<ItemStack> list = NonNullList.withSize(54, ItemStack.EMPTY);
            for (int i = 0; i < getContainerSize(); i++) list.set(i, getItem(i));
            CompoundTag d = vehicle.getPersistentData();
            CompoundTag t = d.getCompound(TAG);
            ContainerHelper.saveAllItems(t, list, true);
            d.put(TAG, t);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            save();
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return gone() ? ItemStack.EMPTY : super.removeItem(slot, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return gone() ? ItemStack.EMPTY : super.removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (!loading && gone()) return;
            super.setItem(slot, stack);
        }

        @Override
        public boolean stillValid(Player player) {
            return !gone() && player instanceof ServerPlayer sp && canUse(sp, vehicle);
        }

        @Override
        public void startOpen(Player player) {
            viewers++;
        }

        @Override
        public void stopOpen(Player player) {
            viewers--;
            if (viewers <= 0) {
                save();
                if (OPEN.get(vehicle.getUUID()) == this) OPEN.remove(vehicle.getUUID());
            }
        }
    }

    private static MenuType<ChestMenu> typeOf(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    /** Ouvre le coffre (après les mêmes contrôles que le menu). */
    private static void openChest(ServerPlayer p, Entity e) {
        if (!canUse(p, e)) {
            Msg.send(p, MtsBridge.isLocked(e) ? "&cCe véhicule est verrouillé : son coffre est inaccessible." : "&cTu es trop loin du véhicule.");
            return;
        }
        TrunkContainer c = OPEN.get(e.getUUID());
        if (c == null || c.gone()) {
            c = new TrunkContainer(e, size(e));
            OPEN.put(e.getUUID(), c);
        }
        final TrunkContainer fc = c;
        int rows = fc.getContainerSize() / 9;
        String id = MtsBridge.itemId(MtsBridge.snapshot(e));
        Component title = Gui.comp("&aCoffre - " + Garage.labelOf(id, MtsBridge.snapshot(e)));
        p.openMenu(new SimpleMenuProvider((wid, inv, pl) -> new ChestMenu(typeOf(rows), wid, inv, (Container) fc, rows), title));
    }

    // ------------------------------------------------------------------ menus

    /** Coffre du véhicule le plus proche du joueur (le sien), depuis le menu du garage. */
    public static void openNearest(ServerPlayer p) {
        if (!TrunkConfig.ENABLED.get()) {
            Msg.send(p, "&cLe coffre des véhicules est désactivé.");
            return;
        }
        List<Entity> list = Garage.candidates(p, false);
        if (list.isEmpty()) {
            Msg.send(p, "&cAucun de tes véhicules à moins de " + VehicleConfig.SEARCH_RADIUS.get() + " blocs.");
            return;
        }
        openMenu(p, list.get(0));
    }

    public static void openMenu(ServerPlayer p, Entity e) {
        if (!canUse(p, e)) {
            Msg.send(p, MtsBridge.isLocked(e) ? "&cCe véhicule est verrouillé : son coffre est inaccessible." : "&cTu es trop loin du véhicule.");
            return;
        }
        CompoundTag snap = MtsBridge.snapshot(e);
        String id = MtsBridge.itemId(snap);
        int[] r = range(e);
        int lvl = Math.min(level(e), maxLevel(r)), size = size(e);
        boolean mine = isOwner(p, e);

        ItemStack[] items = new ItemStack[27];
        items[11] = Gui.item(Items.CHEST, "&e&lOuvrir le coffre", "&7Emplacements : &f" + size, "&7Occupés : &f" + filled(e) + "/" + size);
        items[13] = Gui.item(Garage.iconOf(id), "&e&l" + Garage.labelOf(id, snap), "&7Coffre : &f" + size + " emplacements");
        if (lvl >= maxLevel(r)) {
            items[15] = Gui.item(Items.BARREL, "&6&lExtension de coffre", "&aCoffre au maximum", "&7" + size + " emplacements (maximum " + r[1] + ")");
        } else if (!mine) {
            items[15] = Gui.item(Items.BARREL, "&6&lExtension de coffre", "&7Réservée au propriétaire du véhicule.");
        } else {
            int step = Math.min(TrunkConfig.STEP.get(), r[1] - size);
            items[15] = Gui.item(Items.BARREL, "&6&lExtension de coffre",
                    "&7Palier : &f" + lvl + "/" + maxLevel(r), "&7Gain : &f+" + step + " emplacements",
                    "&7Prix : &c" + priceFor(p, lvl) + "€", "&8Clique pour choisir le mode de paiement");
        }
        items[26] = Gui.item(Items.BARRIER, "&c« Fermer");
        Gui.open(p, "&6Coffre du véhicule", 3, items, slot -> {
            if (slot == 11) Gui.later(p, () -> {
                p.closeContainer();
                if (!e.isRemoved()) openChest(p, e);
            });
            else if (slot == 15 && mine && lvl < maxLevel(r)) Gui.later(p, () -> openUpgrade(p, e));
            else if (slot == 26) Gui.later(p, p::closeContainer);
        });
    }

    private static void openUpgrade(ServerPlayer p, Entity e) {
        if (!canUse(p, e) || !isOwner(p, e)) return;
        if (TrunkConfig.NEED_GARAGE_ZONE.get() && Garage.zoneAt(p) == null) {
            Msg.send(p, "&cL'extension de coffre se fait dans une zone garage.");
            return;
        }
        int[] r = range(e);
        int lvl = level(e);
        if (lvl >= maxLevel(r)) {
            Msg.send(p, "&eLe coffre est déjà au maximum.");
            return;
        }
        int price = priceFor(p, lvl);
        int cash = Money.count(p, VehicleConfig.currency());
        PayResult card = Payment.cardStatus(p, price);

        ItemStack[] items = new ItemStack[9];
        items[4] = Gui.item(Items.BARREL, "&6&lExtension de coffre", "&7Palier : &f" + (lvl + 1) + "/" + maxLevel(r),
                "&7Prix : &c" + price + "€", "&8L'extension reste liée à ce véhicule.");
        Item cashIcon = VehicleConfig.item("minenorth_eurobank:bill_50e");
        items[2] = Gui.item(cashIcon == Items.AIR ? Items.GOLD_INGOT : cashIcon, "&a&lPayer en espèces",
                "&7Espèces sur toi : &f" + cash + "€", cash >= price ? "&aClique pour payer" : "&cEspèces insuffisantes");
        Item cardIcon = VehicleConfig.item("minenorth_eurobank:bank_card");
        String cardLine = card == PayResult.OK ? "&aClique pour payer"
                : "&c" + (card == PayResult.INSUFFICIENT_FUNDS ? "Solde insuffisant" : card.message());
        items[6] = Gui.item(cardIcon == Items.AIR ? Items.PAPER : cardIcon, "&b&lPayer par carte",
                "&7Solde du compte : &f" + EconomyBridge.balance(p) + "€", cardLine);
        items[8] = Gui.item(Items.ARROW, "&c« Retour");
        Gui.open(p, "&6Payer l'extension", 1, items, slot -> {
            if (slot == 2) Gui.later(p, () -> buy(p, e, lvl, Payment.Method.CASH));
            else if (slot == 6) Gui.later(p, () -> buy(p, e, lvl, Payment.Method.CARD));
            else if (slot == 8) Gui.later(p, () -> openMenu(p, e));
        });
    }

    private static void buy(ServerPlayer p, Entity e, int expectedLevel, Payment.Method method) {
        if (!canUse(p, e) || !isOwner(p, e)) return;
        int[] r = range(e);
        if (level(e) != expectedLevel || expectedLevel >= maxLevel(r)) {
            Msg.send(p, "&eLe coffre a changé : réessaie.");
            return;
        }
        int price = priceFor(p, expectedLevel);
        List<VehicleConfig.Denom> den = VehicleConfig.currency();
        String err = Payment.precheck(p, price, method, den, "pour l'extension de coffre");
        if (err != null) {
            Msg.send(p, "&c" + err);
            return;
        }
        Payment.Result paid = Payment.pay(p, price, method, den, "garage:coffre");
        if (!paid.ok()) {
            Msg.send(p, "&c" + paid.message());
            return;
        }
        CompoundTag d = e.getPersistentData();
        CompoundTag t = d.getCompound(TAG);
        t.putInt("Niveau", expectedLevel + 1);
        d.put(TAG, t);
        Msg.send(p, "&aExtension de coffre achetée pour " + price + "€ : le coffre fait maintenant " + size(e) + " emplacements.");
        openMenu(p, e);
    }

    // ------------------------------------------------------------------ événements

    /** Accroupi + clic droit à mains vides sur un véhicule : menu du coffre. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer sp) || !sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty()) return;
        if (!TrunkConfig.ENABLED.get() || !TrunkConfig.SNEAK_CLICK.get() || !MtsBridge.isVehicle(e.getTarget())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        openMenu(sp, e.getTarget());
    }

    /**
     * Le véhicule disparaît sans passer par le garage / la fourrière (destruction, revente au receleur, ramassage...) :
     * son contenu est lâché au sol, jamais perdu ni conservé. L'assurance, elle, rend le véhicule avec un coffre vide.
     */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) {
        Entity v = e.getEntity();
        if (e.getLevel().isClientSide() || !(e.getLevel() instanceof ServerLevel level) || !MtsBridge.isVehicle(v)) return;
        if (v.getPersistentData().getBoolean(MtsBridge.HANDLED)) return;
        Entity.RemovalReason why = v.getRemovalReason();
        if (why != Entity.RemovalReason.KILLED && why != Entity.RemovalReason.DISCARDED) return;
        CompoundTag t = v.getPersistentData().getCompound(TAG);
        if (!t.contains("Items")) return;
        NonNullList<ItemStack> list = stored(v);
        t.remove("Items");
        v.getPersistentData().put(TAG, t);
        TrunkContainer c = OPEN.get(v.getUUID());
        if (c != null) c.dead = true;
        final double x = v.getX(), y = v.getY() + 0.5, z = v.getZ();
        level.getServer().execute(() -> {
            for (ItemStack s : list) {
                if (!s.isEmpty()) level.addFreshEntity(new ItemEntity(level, x, y, z, s.copy()));
            }
        });
    }
}
