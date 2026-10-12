package com.minenorth.vehicles.pompe;

import com.minenorth.vehicles.VehiclesMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Pompes à carburant d'Immersive Vehicles (MTS), par réflexion : pas de dépendance de compilation à MTS.
 * On utilise le système natif : la pompe est une TileEntityFuelPump (décor « fuel_pump ») dont le réservoir est un EntityFluidTank,
 * exactement le même type que le réservoir des véhicules. Le plein reprend la logique de TileEntityFuelPump.fuelVehicle :
 * le réservoir du véhicule est rempli avec le fluide de la pompe (mod « wildcard »), puis celui de la pompe est vidé d'autant.
 * Classes lues dans Immersive Vehicles 24.0.0 (1.20.1).
 */
final class MtsPump {
    private MtsPump() {}

    static final String WILDCARD = "wildcard";

    private static boolean init, ok;
    private static Field teField, creativeField, builderEntity, fuelTank, bufferField, purchasedField, dispensedField;
    private static Class<?> chargerClass;
    private static double chargerMax = 1000;
    private static Class<?> pumpClass;
    private static Method getTank, tankFluid, tankMod, tankLevel, tankMax, tankFill, tankDrain, check;

    private static boolean init() {
        if (init) return ok;
        init = true;
        try {
            teField = Class.forName("mcinterface1201.BuilderTileEntity").getDeclaredField("tileEntity");
            teField.setAccessible(true);
            pumpClass = Class.forName("minecrafttransportsimulator.blocks.tileentities.instances.TileEntityFuelPump");
            creativeField = Class.forName("minecrafttransportsimulator.blocks.tileentities.instances.ATileEntityFuelPump").getField("isCreative");
            getTank = pumpClass.getMethod("getTank");
            Class<?> tank = Class.forName("minecrafttransportsimulator.entities.instances.EntityFluidTank");
            tankFluid = tank.getMethod("getFluid");
            tankMod = tank.getMethod("getFluidMod");
            tankLevel = tank.getMethod("getFluidLevel");
            tankMax = tank.getMethod("getMaxLevel");
            tankFill = tank.getMethod("fill", String.class, String.class, double.class, boolean.class);
            tankDrain = tank.getMethod("drain", double.class, boolean.class);
            chargerClass = Class.forName("minecrafttransportsimulator.blocks.tileentities.instances.TileEntityCharger");
            bufferField = chargerClass.getField("internalBuffer");
            Class<?> abstractPump = Class.forName("minecrafttransportsimulator.blocks.tileentities.instances.ATileEntityFuelPump");
            purchasedField = abstractPump.getField("fuelPurchased");
            dispensedField = abstractPump.getField("fuelDispensedThisPurchase");
            try {
                Field max = chargerClass.getDeclaredField("MAX_BUFFER");
                max.setAccessible(true);
                chargerMax = ((Number) max.get(null)).doubleValue();
            } catch (ReflectiveOperationException ignored) { /* 1000 par défaut, valeur de MTS 24.0.0 */ }
            builderEntity = Class.forName("mcinterface1201.BuilderEntityExisting").getDeclaredField("entity");
            builderEntity.setAccessible(true);
            Class<?> powered = Class.forName("minecrafttransportsimulator.entities.instances.AEntityVehicleE_Powered");
            fuelTank = powered.getField("fuelTank");
            check = powered.getMethod("checkFuelTankCompatibility", String.class);
            ok = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            VehiclesMod.LOGGER.warn("[Pompe] API MTS introuvable : les pompes restent celles de MTS.", e);
        }
        return ok;
    }

    /** Pompe MTS (TileEntityFuelPump) posée à cet endroit, ou null. Utilisable côté client et serveur. */
    static Object pumpAt(Level level, BlockPos pos) {
        if (!init()) return null;
        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null || !teField.getDeclaringClass().isInstance(be)) return null;
            Object te = teField.get(be);
            return te != null && pumpClass.isInstance(te) ? te : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Si ce bloc est une borne de recharge MTS (TileEntityCharger) : stock d'énergie plein et plus de plafond d'achat, pour qu'elle
     * distribue sans arrêt. Renvoie vrai si c'était une borne.
     */
    static boolean keepChargerFull(BlockEntity be) {
        if (!init()) return false;
        try {
            if (!teField.getDeclaringClass().isInstance(be)) return false;
            Object te = teField.get(be);
            if (te == null || !chargerClass.isInstance(te)) return false;
            if (bufferField.getDouble(te) < chargerMax) bufferField.setDouble(te, chargerMax);
            if (purchasedField.getInt(te) < 1_000_000_000) {
                purchasedField.setInt(te, 2_000_000_000);
                dispensedField.setDouble(te, 0);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ pompe

    private static Field maxLevelField;

    /**
     * Réservoir de la pompe, avec la capacité de la config. MTS fixe la capacité (35 L par défaut) dans un champ privé final que le décor
     * relit à chaque chargement : on le réhausse donc à chaque utilisation (seulement vers le haut).
     */
    static Object tank(Object pump) throws ReflectiveOperationException {
        Object t = getTank.invoke(pump);
        try {
            double liters = com.minenorth.vehicles.config.VehicleConfig.PUMP_CAPACITY.get();
            if (liters > 0) {
                if (maxLevelField == null) {
                    maxLevelField = Class.forName("minecrafttransportsimulator.entities.instances.EntityFluidTank").getDeclaredField("maxLevel");   // le réservoir de la pompe est une sous-classe anonyme
                    maxLevelField.setAccessible(true);
                }
                long want = Math.min(Integer.MAX_VALUE, Math.round(liters * com.minenorth.vehicles.config.VehicleConfig.PUMP_MB_PER_LITER.get()));
                if (want > maxLevelField.getInt(t)) maxLevelField.setInt(t, (int) want);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            VehiclesMod.LOGGER.warn("[Pompe] Capacité de la pompe non modifiable : {}", e.toString());
        }
        unlockFill(pump);
        return t;
    }

    /**
     * Le réservoir d'une pompe NON illimitée (classe anonyme dans TileEntityFuelPump) ne se remplit que jusqu'à
     * fuelPurchased - fuelDispensedThisPurchase - niveau : sans carburant « acheté » par le système de paiement natif de MTS, il refuse tout
     * (fill renvoie 0). Le paiement se fait ici, par le menu MineNorth : on met donc le plafond d'achat très haut.
     */
    private static void unlockFill(Object pump) {
        try {
            if (creativeField.getBoolean(pump)) return;
            if (purchasedField.getInt(pump) < 1_000_000_000) {
                purchasedField.setInt(pump, 2_000_000_000);
                dispensedField.setDouble(pump, 0);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            VehiclesMod.LOGGER.warn("[Pompe] Plafond d'achat de la pompe non modifiable : {}", e.toString());
        }
    }

    static boolean creative(Object pump) {
        try {
            return creativeField.getBoolean(pump);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ pompe limitée / illimitée
    // MTS : à chaque tick, ATileEntityFuelPump.update() met isCreative = true, puis false si un emplacement de son inventaire de carburant
    // (fuelItems) contient un objet. Une pompe est donc illimitée tant que cet inventaire est vide ; pour la limiter on y pose un objet repère
    // (barrière marquée), que seul un OP peut voir (le menu MineNorth remplace l'écran natif).

    private static boolean markerInit, markerOk;
    private static Field fuelItemsField, wrappedStack;
    private static Constructor<?> wrapperCtor;
    private static Method invGet, invSet, invSize, stackEmpty;
    private static final String MARKER_TAG = "mn_pump_limit";

    private static boolean markerInit() {
        if (markerInit) return markerOk;
        markerInit = true;
        if (!init()) return false;
        try {
            Class<?> abs = Class.forName("minecrafttransportsimulator.blocks.tileentities.instances.ATileEntityFuelPump");
            fuelItemsField = abs.getField("fuelItems");
            Class<?> inv = Class.forName("minecrafttransportsimulator.entities.instances.EntityInventoryContainer");
            Class<?> wrapIf = Class.forName("minecrafttransportsimulator.mcinterface.IWrapperItemStack");
            invGet = inv.getMethod("getStack", int.class);
            invSet = inv.getMethod("setStack", wrapIf, int.class);
            invSize = inv.getMethod("getSize");
            stackEmpty = wrapIf.getMethod("isEmpty");
            Class<?> w = Class.forName("mcinterface1201.WrapperItemStack");
            wrapperCtor = w.getDeclaredConstructor(ItemStack.class);
            wrapperCtor.setAccessible(true);
            for (Field f : w.getDeclaredFields()) {
                if (f.getType() == ItemStack.class) {
                    f.setAccessible(true);
                    wrappedStack = f;
                }
            }
            markerOk = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            VehiclesMod.LOGGER.warn("[Pompe] Inventaire de carburant MTS introuvable : /pompe limiter indisponible.", e);
        }
        return markerOk;
    }

    private static boolean isMarker(Object wrapper) throws ReflectiveOperationException {
        if (wrappedStack == null || wrapper == null) return false;
        ItemStack st = (ItemStack) wrappedStack.get(wrapper);
        return st != null && st.is(Items.BARRIER) && st.hasTag() && st.getTag().contains(MARKER_TAG);
    }

    /** Vrai si l'inventaire de carburant de la pompe contient un objet (repère ou autre) : MTS la traite alors comme limitée. */
    static boolean hasFuelItems(Object pump) throws ReflectiveOperationException {
        if (!markerInit()) return false;
        Object inv = fuelItemsField.get(pump);
        for (int i = 0; i < (Integer) invSize.invoke(inv); i++) {
            if (!(Boolean) stackEmpty.invoke(invGet.invoke(inv, i))) return true;
        }
        return false;
    }

    /** limited = true : pose l'objet repère (la pompe devient limitée à son réservoir). false : le retire (illimitée si l'inventaire est vide). */
    static boolean setLimited(Object pump, boolean limited) throws ReflectiveOperationException {
        if (!markerInit()) return false;
        Object inv = fuelItemsField.get(pump);
        int n = (Integer) invSize.invoke(inv);
        if (limited) {
            if (!hasFuelItems(pump)) {
                ItemStack marker = new ItemStack(Items.BARRIER);
                marker.getOrCreateTag().putBoolean(MARKER_TAG, true);
                marker.setHoverName(Component.literal("Pompe limitée (MineNorth)"));
                invSet.invoke(inv, wrapperCtor.newInstance(marker), 0);
            }
            creativeField.setBoolean(pump, false);
        } else {
            for (int i = 0; i < n; i++) {
                if (isMarker(invGet.invoke(inv, i))) invSet.invoke(inv, wrapperCtor.newInstance(ItemStack.EMPTY), i);
            }
            if (!hasFuelItems(pump)) creativeField.setBoolean(pump, true);
        }
        return true;
    }

    // ------------------------------------------------------------------ réservoirs (pompe ou véhicule : même classe)

    static String fluid(Object tank) throws ReflectiveOperationException {
        Object s = tankFluid.invoke(tank);
        return s == null ? "" : s.toString();
    }

    static String mod(Object tank) throws ReflectiveOperationException {
        Object s = tankMod.invoke(tank);
        return s == null ? "" : s.toString();
    }

    static double level(Object tank) throws ReflectiveOperationException { return ((Number) tankLevel.invoke(tank)).doubleValue(); }

    static double max(Object tank) throws ReflectiveOperationException { return ((Number) tankMax.invoke(tank)).doubleValue(); }

    /** Renvoie la quantité réellement versée ; doIt = false : simulation. */
    static double fill(Object tank, String fluid, String mod, double mb, boolean doIt) throws ReflectiveOperationException {
        return ((Number) tankFill.invoke(tank, fluid, mod, mb, doIt)).doubleValue();
    }

    /** Vide n'importe quel fluide du réservoir (comme le fait la pompe native après avoir rempli le véhicule). */
    static double drain(Object tank, double mb, boolean doIt) throws ReflectiveOperationException {
        return ((Number) tankDrain.invoke(tank, mb, doIt)).doubleValue();
    }

    // ------------------------------------------------------------------ véhicule

    /** Objet véhicule MTS motorisé porté par cette entité, ou null (remorque, avion sans moteur, autre entité…). */
    static Object vehicle(Entity e) {
        if (e == null || !init()) return null;
        try {
            if (!builderEntity.getDeclaringClass().isInstance(e)) return null;
            Object v = builderEntity.get(e);
            return v != null && fuelTank.getDeclaringClass().isInstance(v) ? v : null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    static Object vehicleTank(Object vehicle) throws ReflectiveOperationException { return fuelTank.get(vehicle); }

    /** Les moteurs du véhicule acceptent-ils ce fluide ? (même test que la pompe native : checkFuelTankCompatibility) */
    static boolean accepts(Object vehicle, String fluid) throws ReflectiveOperationException {
        return "VALID".equals(String.valueOf(check.invoke(vehicle, fluid)));
    }

    /** Réservoir du véhicule rempli avec le fluide de la pompe, puis réservoir de la pompe vidé d'autant. Renvoie les mB transférés. */
    static double transfer(Object pumpTank, Object vehicleTank, String fluid, double mb, boolean creative, boolean replace) throws ReflectiveOperationException {
        if (replace) drain(vehicleTank, max(vehicleTank), true);   // autre carburant : réservoir vidé (le fluide est effacé à 0)
        double filled = fill(vehicleTank, fluid, WILDCARD, mb, true);
        if (filled > 0 && !creative) drain(pumpTank, filled, true);
        return filled;
    }
}
