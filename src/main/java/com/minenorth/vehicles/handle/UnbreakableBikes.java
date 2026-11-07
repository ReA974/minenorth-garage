package com.minenorth.vehicles.handle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Vélos incassables (pack cmacitizenstransport, modèles « bike… », ceux que Nova offre) : un coup de joueur ne les abîme pas, et toutes les demi-secondes
 * leurs dégâts MTS (chocs, balles, explosions) sont remis à zéro, état « hors d'usage » compris. Par réflexion : aucune dépendance de compilation à MTS.
 */
@Mod.EventBusSubscriber(modid = "minenorth_rp_vehicles")
public final class UnbreakableBikes {
    private UnbreakableBikes() {}

    private static final String PACK = "cmacitizenstransport", PREFIX = "bike";
    private static int tick;

    public static boolean isBike(Entity e) {
        if (!MtsBridge.isVehicle(e)) return false;
        Object def = field(field(e, "entity"), "definition");
        return PACK.equals(field(def, "packID")) && field(def, "systemName") instanceof String s && s.startsWith(PREFIX);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (isBike(e.getTarget())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++tick % 10 != 0) return;
        for (ServerLevel level : e.getServer().getAllLevels()) {
            for (Entity en : level.getAllEntities()) if (isBike(en)) repair(field(en, "entity"));
        }
    }

    private static void repair(Object vehicle) {
        try {
            Object var = field(vehicle, "damageVar");
            if (var == null) return;
            double dmg = ((Number) var.getClass().getMethod("getValue").invoke(var)).doubleValue();
            if (dmg > 0) {
                Method set = var.getClass().getMethod("setTo", double.class, boolean.class);
                set.invoke(var, 0d, true);
            }
            Field out = find(vehicle.getClass(), "outOfHealth");
            if (out != null && out.getBoolean(vehicle)) out.setBoolean(vehicle, false);
        } catch (Throwable ignored) {}
    }

    private static Field find(Class<?> c, String name) {
        for (; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static Object field(Object o, String name) {
        if (o == null) return null;
        try {
            Field f = find(o.getClass(), name);
            return f == null ? null : f.get(o);
        } catch (Throwable t) {
            return null;
        }
    }
}
