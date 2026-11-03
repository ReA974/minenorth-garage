package com.minenorth.vehicles.handle;

import com.minenorth.vehicles.config.VehicleConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.UUID;

/**
 * Seul point de contact avec Immersive Vehicles (MTS).
 * Aucune classe MTS n'est utilisée : un véhicule est une entité (mts:builder_existing) dont le NBT contient
 * tout l'état (pièces, inventaires, carburant, dégâts...). On sauvegarde/restaure donc ce NBT tel quel.
 */
public final class MtsBridge {
    public static final String OWNER = "mnrp_owner";
    /** Drapeau « véhicule assuré » : vit dans les données persistantes de l'entité, donc dans le NBT sauvegardé au garage. */
    public static final String INSURED = "mnrp_insured";

    private MtsBridge() {}

    public static String typeId(Entity e) {
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return k == null ? "" : k.toString();
    }

    public static boolean isVehicle(Entity e) {
        return typeId(e).equals(VehicleConfig.VEHICLE_ENTITY.get());
    }

    public static UUID ownerOf(Entity e) {
        CompoundTag d = e.getPersistentData();
        return d.hasUUID(OWNER) ? d.getUUID(OWNER) : null;
    }

    public static void setOwner(Entity e, UUID id) {
        e.getPersistentData().putUUID(OWNER, id);
    }

    /** Véhicule dans lequel le joueur est assis, ou null. */
    public static Entity vehicleOf(ServerPlayer p) {
        Entity ride = p.getVehicle();
        if (ride == null) return null;
        for (Entity e = ride; e != null; e = e.getVehicle()) {
            if (isVehicle(e)) return e;
        }
        // Le siège MTS n'est pas lié par un lien vanilla : on prend le véhicule le plus proche du siège
        return nearest((ServerLevel) p.level(), ride.position(), VehicleConfig.SEARCH_RADIUS.get(), null);
    }

    /** Véhicule le plus proche (optionnellement limité à un propriétaire). */
    public static Entity nearest(ServerLevel level, Vec3 pos, double radius, UUID owner) {
        AABB box = new AABB(pos, pos).inflate(radius);
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : level.getEntities((Entity) null, box, MtsBridge::isVehicle)) {
            if (owner != null && !owner.equals(ownerOf(e))) continue;
            double d = e.position().distanceToSqr(pos);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** Id d'item MTS correspondant au véhicule (même format que le catalogue : mts:pack.systeme[_variante]). */
    public static String itemId(CompoundTag nbt) {
        String pack = nbt.getString("packID");
        String sys = nbt.getString("systemName");
        if (pack.isEmpty() || sys.isEmpty()) return "";
        return ("mts:" + pack + "." + sys + nbt.getString("subName")).toLowerCase(Locale.ROOT);
    }

    /** État du véhicule au moment où il est sorti du garage (assuré seulement) : c'est celui qu'on lui rend après une destruction. */
    public static final String ORIGIN = "mnrp_origin";

    /** La photo d'origine garde le palier de coffre mais jamais son contenu (sinon l'assurance dupliquerait les objets). */
    public static void setOrigin(Entity e, CompoundTag state) {
        CompoundTag copy = state.copy();
        CompoundTag fd = copy.getCompound("ForgeData");
        if (fd.contains(com.minenorth.vehicles.coffre.Trunk.TAG, 10)) fd.getCompound(com.minenorth.vehicles.coffre.Trunk.TAG).remove("Items");
        e.getPersistentData().put(ORIGIN, copy);
    }

    /**
     * Véhicule verrouillé par MTS. MTS 24 : variable calculée {@code lockedVar} du véhicule ; versions plus anciennes :
     * champ booléen « locked » ou variable du même nom. Faux si illisible.
     */
    public static boolean isLocked(Entity e) {
        try {
            Object in = field(e, "entity");
            if (in == null) return false;
            Object var = field(in, "lockedVar");
            if (var != null) return variableOn(var);
            if (field(in, "locked") instanceof Boolean b) return b;
            Object v = in.getClass().getMethod("getVariable", String.class).invoke(in, "locked");
            return v instanceof Number n && n.doubleValue() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Variable calculée MTS (ComputedVariable) à une valeur non nulle. */
    private static boolean variableOn(Object var) throws Exception {
        return ((Number) var.getClass().getMethod("getValue").invoke(var)).doubleValue() > 0;
    }

    /**
     * Le coffre du véhicule est-il ouvert ? D'abord l'emplacement de coffre du modèle (type contenant « trunk » / « coffre ») :
     * MTS y donne les variables à ouvrir pour s'en servir ({@code interactableVariables}). Sinon une variable de porte connue
     * ({@code noms}) déjà présente sur le véhicule.
     * @return null si le modèle n'a ni emplacement de coffre ni variable reconnue, ou si MTS est illisible
     */
    public static Boolean trunkOpen(Entity e, java.util.List<? extends String> noms) {
        try {
            Object in = field(e, "entity");
            if (in == null) return null;
            boolean found = false;
            if (field(field(in, "definition"), "parts") instanceof java.util.List<?> slots) {
                java.lang.reflect.Method listTrue = in.getClass().getMethod("isVariableListTrue", java.util.List.class);
                for (Object slot : slots) {
                    if (!trunkSlot(slot) || !(field(slot, "interactableVariables") instanceof java.util.List<?> vars) || vars.isEmpty()) continue;
                    found = true;
                    if (Boolean.TRUE.equals(listTrue.invoke(in, vars))) return true;
                }
            }
            if (field(in, "computedVariables") instanceof java.util.Map<?, ?> m) {
                for (String n : noms) {
                    Object var = m.get(n.trim());
                    if (var == null) continue;
                    found = true;
                    if (variableOn(var)) return true;
                }
            }
            return found ? Boolean.FALSE : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean trunkSlot(Object slotDef) {
        if (!(field(slotDef, "types") instanceof java.util.List<?> types)) return false;
        for (Object t : types) {
            if (t instanceof String s) {
                String l = s.toLowerCase(Locale.ROOT);
                if (l.contains("trunk") || l.contains("coffre")) return true;
            }
        }
        return false;
    }

    public static CompoundTag origin(Entity e) {
        CompoundTag d = e.getPersistentData();
        return d.contains(ORIGIN, 10) ? d.getCompound(ORIGIN).copy() : null;
    }

    /** Retrait voulu par ce mod (rangement, fourrière, recentrage...) : l'assurance ne doit PAS jouer. */
    public static final String HANDLED = "mnrp_handled";

    public static void discard(Entity e) {
        e.getPersistentData().putBoolean(HANDLED, true);
        e.discard();
    }

    public static boolean isInsured(Entity e) { return e.getPersistentData().getBoolean(INSURED); }

    public static boolean isInsured(CompoundTag saved) { return saved.getCompound("ForgeData").getBoolean(INSURED); }

    public static void setInsured(CompoundTag saved, boolean insured) {
        CompoundTag fd = saved.getCompound("ForgeData");
        if (insured) fd.putBoolean(INSURED, true); else fd.remove(INSURED);
        saved.put("ForgeData", fd);
    }

    /** MTS a marqué le véhicule comme détruit (dégâts >= points de vie du modèle). */
    public static boolean outOfHealth(Entity e) {
        return field(field(e, "entity"), "outOfHealth") instanceof Boolean b && b;
    }

    /** Points de vie du modèle (définition MTS : general.health), 0 si illisible. */
    public static double health(Entity e) {
        return field(field(field(field(e, "entity"), "definition"), "general"), "health") instanceof Number n ? n.doubleValue() : 0;
    }

    /** Plus grande valeur numérique de clé « damage » dans le NBT (dégâts du véhicule, où qu'ils soient rangés), 0 si absente. */
    public static double damageIn(CompoundTag t) {
        double max = 0;
        for (String k : t.getAllKeys()) {
            net.minecraft.nbt.Tag v = t.get(k);
            if (v instanceof CompoundTag c) max = Math.max(max, damageIn(c));
            else if (k.equals("damage") && v instanceof net.minecraft.nbt.NumericTag n) max = Math.max(max, n.getAsDouble());
        }
        return max;
    }

    /** Remet à zéro les dégâts d'un NBT de véhicule sauvegardé (variable « damage », où qu'elle soit rangée). */
    public static void repair(CompoundTag t) {
        for (String k : new java.util.ArrayList<>(t.getAllKeys())) {
            net.minecraft.nbt.Tag v = t.get(k);
            if (v instanceof CompoundTag c) repair(c);
            else if (k.equals("damage") && v instanceof net.minecraft.nbt.NumericTag) t.putDouble(k, 0);
        }
    }

    // ------------------------------------------------------------------ plaque posée (lue dans MTS par réflexion)

    private static Object field(Object o, String name) {
        if (o == null) return null;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static final String[] PLATE_FIELDS = {"Code", "License Plate", "Plate", "Plaque", "Immatriculation"};

    private static String plateIn(Object holder) {
        if (!(field(holder, "text") instanceof java.util.Map<?, ?> m)) return "";
        for (java.util.Map.Entry<?, ?> en : m.entrySet()) {
            if (field(en.getKey(), "fieldName") instanceof String n && en.getValue() instanceof String v && !v.isBlank())
                for (String c : PLATE_FIELDS) if (c.equalsIgnoreCase(n.trim())) return v.trim();
        }
        return "";
    }

    /** Texte de la plaque posée sur le véhicule (pièce mts:gvp.eu_plate), ou "" s'il n'en a pas ou si MTS est illisible. */
    public static String plate(Entity e) {
        try {
            Object in = field(e, "entity");
            if (in == null) return "";
            String found = plateIn(in);
            if (!found.isEmpty()) return found;
            if (field(in, "allParts") instanceof Iterable<?> parts) {
                for (Object part : parts) {
                    found = plateIn(part);
                    if (!found.isEmpty()) return found;
                }
                for (Object part : parts) {
                    if (field(field(part, "definition"), "systemName") instanceof String s && s.toLowerCase(Locale.ROOT).contains("plate")
                            && field(part, "text") instanceof java.util.Map<?, ?> m)
                        for (Object v : m.values()) if (v instanceof String str && !str.isBlank()) return str.trim();
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    /** Définition MTS : motorized.isAircraft / isBlimp (lue par réflexion, false si illisible). */
    public static boolean isAircraft(Entity e) {
        try {
            Object mo = field(field(field(e, "entity"), "definition"), "motorized");
            return field(mo, "isAircraft") instanceof Boolean a && a || field(mo, "isBlimp") instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    public static CompoundTag snapshot(Entity vehicle) {
        CompoundTag tag = new CompoundTag();
        vehicle.saveWithoutId(tag);
        tag.remove("UUID");
        tag.remove("Passengers");
        if (isAircraft(vehicle)) { // le type aérien suit le véhicule dans son NBT
            CompoundTag fd = tag.getCompound("ForgeData");
            fd.putString(com.minenorth.vehicles.garage.VehicleType.NBT_KEY, "air");
            tag.put("ForgeData", fd);
        }
        if (tag.contains("ForgeData", 10)) tag.getCompound("ForgeData").remove(ORIGIN); // la photo d'origine ne suit pas le véhicule au garage
        return tag;
    }

    // ------------------------------------------------------------------ véhicule neuf (concession)

    /**
     * Fait apparaître un véhicule neuf exactement comme MTS quand on pose son item (pièces par défaut du pack comprises),
     * au point donné. Par réflexion ; false si MTS refuse ou si son API a changé.
     */
    public static boolean spawnNew(ServerLevel level, String itemId, double x, double y, double z, float yaw, ServerPlayer placer) {
        try {
            Object item = wrapped(itemId);
            if (item == null) return false;
            Object world = Class.forName("mcinterface1201.WrapperWorld").getMethod("getWrapperFor", net.minecraft.world.level.Level.class).invoke(null, level);
            Object player = placer == null ? null
                    : Class.forName("mcinterface1201.WrapperPlayer").getMethod("getWrapperFor", net.minecraft.world.entity.player.Player.class).invoke(null, placer);
            Class<?> vehClass = Class.forName("minecrafttransportsimulator.entities.instances.EntityVehicleF_Physics");
            Object veh = null;
            for (java.lang.reflect.Constructor<?> c : vehClass.getConstructors()) {
                if (c.getParameterCount() == 4) veh = c.newInstance(world, player, item, null);
            }
            if (veh == null) return false;
            Class<?> p3 = Class.forName("minecrafttransportsimulator.baseclasses.Point3D");
            Object pos = field(veh, "position");
            p3.getMethod("set", double.class, double.class, double.class).invoke(pos, x, y, z);
            p3.getMethod("set", p3).invoke(field(veh, "prevPosition"), pos);
            Class<?> rm = Class.forName("minecrafttransportsimulator.baseclasses.RotationMatrix");
            Object orient = field(veh, "orientation");
            rm.getMethod("setToAngles", p3).invoke(orient, p3.getConstructor(double.class, double.class, double.class).newInstance(0d, (double) yaw, 0d));
            rm.getMethod("set", rm).invoke(field(veh, "prevOrientation"), orient);
            // comme ItemVehicle.onBlockClicked : ajout au monde puis pièces par défaut (données nulles = véhicule neuf)
            for (java.lang.reflect.Method m : world.getClass().getMethods()) {
                if (m.getName().equals("spawnEntity") && m.getParameterCount() == 1) {
                    m.invoke(world, veh);
                    break;
                }
            }
            for (java.lang.reflect.Method m : vehClass.getMethods()) {
                if (m.getName().equals("addPartsPostAddition") && m.getParameterCount() == 2) {
                    m.invoke(veh, player, null);
                    break;
                }
            }
            return true;
        } catch (Throwable t) {
            com.minenorth.vehicles.VehiclesMod.LOGGER.warn("[Vehicules] Apparition directe de {} impossible : {}", itemId, t.toString());
            return false;
        }
    }

    /**
     * Monte la plaque sur un véhicule déjà apparu : son NBT réel (sauvegardé par MTS, rien n'est perdu) reçoit la pièce
     * plaque sur chaque emplacement de plaque, puis le véhicule est recréé au même endroit.
     * @return le véhicule recréé, ou null si aucun emplacement de plaque (le véhicule d'origine reste intact)
     */
    public static Entity mountPlate(ServerLevel level, Entity vehicle, String plateItemId, String plate) {
        try {
            CompoundTag snap = snapshot(vehicle);
            Object veh = wrapped(itemId(snap));
            Object plateItem = wrapped(plateItemId);
            CompoundTag plateTag = plateItem == null ? null : identity(plateItem);
            if (veh == null || plateTag == null || !(field(field(veh, "definition"), "parts") instanceof java.util.List<?> slots)) return null;
            plateTag.putString("textCode", plate);   // même format que la plaque en item (champ « Code » de la pièce)
            plateTag.putString("textCountry Code", "FR");
            int n = 0;
            for (int i = 0; i < slots.size(); i++) {
                if (!plateSlot(slots.get(i))) continue;
                snap.put("part_" + i, plateTag.copy());
                n++;
            }
            if (n == 0) return null;
            Entity again = restore(level, snap, vehicle.getX(), vehicle.getY(), vehicle.getZ());
            if (again == null) return null;
            discard(vehicle);
            level.addFreshEntityWithPassengers(again);
            return again;
        } catch (Throwable t) {
            com.minenorth.vehicles.VehiclesMod.LOGGER.warn("[Vehicules] Plaque non montée : {}", t.toString());
            return null;
        }
    }

    /** Objet MTS derrière l'item Forge (BuilderItem.getWrappedItem), ou null. */
    private static Object wrapped(String itemId) throws Exception {
        net.minecraft.world.item.Item it = VehicleConfig.item(itemId);
        if (it == net.minecraft.world.item.Items.AIR) return null;
        try {
            return it.getClass().getMethod("getWrappedItem").invoke(it);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** packID / systemName / subName de l'objet MTS, comme dans son NBT. */
    private static CompoundTag identity(Object wrapped) {
        Object def = field(wrapped, "definition");
        if (!(field(def, "packID") instanceof String pack) || !(field(def, "systemName") instanceof String sys)) return null;
        Object sub = field(field(wrapped, "subDefinition"), "subName");
        CompoundTag t = new CompoundTag();
        t.putString("packID", pack);
        t.putString("systemName", sys);
        t.putString("subName", sub instanceof String s ? s : "");
        return t;
    }

    /** Emplacement de plaque : un type ou une pièce par défaut qui parle de plaque. */
    private static boolean plateSlot(Object slotDef) {
        if (field(slotDef, "types") instanceof java.util.List<?> types) {
            for (Object t : types) if (t instanceof String s && s.toLowerCase(Locale.ROOT).contains("plate")) return true;
        }
        return field(slotDef, "defaultPart") instanceof String dp && dp.toLowerCase(Locale.ROOT).contains("plate");
    }

    /** Recrée l'entité (non ajoutée au monde) depuis un NBT sauvegardé, à la position donnée. */
    public static Entity restore(ServerLevel level, CompoundTag saved, double x, double y, double z) {
        CompoundTag tag = saved.copy();
        tag.putString("id", VehicleConfig.VEHICLE_ENTITY.get());

        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(x));
        pos.add(DoubleTag.valueOf(y));
        pos.add(DoubleTag.valueOf(z));
        tag.put("Pos", pos);

        ListTag motion = new ListTag();
        motion.add(DoubleTag.valueOf(0));
        motion.add(DoubleTag.valueOf(0));
        motion.add(DoubleTag.valueOf(0));
        tag.put("Motion", motion);

        // MTS relit sa propre position / vitesse dans ses clés
        tag.putDouble("positionx", x);
        tag.putDouble("positiony", y);
        tag.putDouble("positionz", z);
        for (String k : new String[]{"motionx", "motiony", "motionz"}) {
            if (tag.contains(k)) tag.putDouble(k, 0);
        }
        return EntityType.loadEntityRecursive(tag, level, en -> en);
    }
}
