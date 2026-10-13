package com.minenorth.vehicles.fourriere;

import com.minenorth.vehicles.VehiclesMod;
import com.minenorth.vehicles.miscs.Gui;
import com.minenorth.vehicles.miscs.Msg;
import fr.minenorth.api.CompanyService;
import fr.minenorth.api.MineNorth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

/**
 * « Appeler le mécano » (menu de mise en fourrière de la police) : le message et le point sur la carte ne vont qu'aux membres de
 * l'entreprise de dépannage (config {@code mechanicCompany}), et seulement si elle est ouverte. Le point disparaît quand un mécano
 * arrive sur place, quand le véhicule disparaît ou après {@code mechanicMarkerMinutes}.
 */
@Mod.EventBusSubscriber(modid = VehiclesMod.MODID)
public final class MechanicCalls {
    private MechanicCalls() {}

    private static final int COLOR = 0xFB8C00;
    private static long counter;

    private static final class Call {
        final String marker, dim;
        final int x, y, z;
        final UUID vehicle;
        final long expireTick;
        final Set<UUID> notified = new HashSet<>();

        Call(String marker, String dim, int x, int y, int z, UUID vehicle, long expireTick) {
            this.marker = marker; this.dim = dim; this.x = x; this.y = y; this.z = z; this.vehicle = vehicle; this.expireTick = expireTick;
        }
    }

    private static final List<Call> CALLS = new ArrayList<>();

    // ------------------------------------------------------------------ pont vers la carte (par réflexion, comme les autres mods)

    private static boolean mapTried;
    private static Method mapSet, mapRemove;

    private static boolean mapReady() {
        if (!mapTried) {
            mapTried = true;
            try {
                if (ModList.get().isLoaded("minenorth_map")) {
                    Class<?> api = Class.forName("fr.minenorth.map.api.MapApi");
                    mapSet = api.getMethod("setMarker", ServerPlayer.class, String.class, String.class, int.class, int.class, int.class, int.class, boolean.class);
                    mapRemove = api.getMethod("removeMarker", ServerPlayer.class, String.class);
                }
            } catch (Throwable t) {
                mapSet = mapRemove = null;
            }
        }
        return mapSet != null;
    }

    private static void setMarker(ServerPlayer p, Call c) {
        if (!mapReady()) return;
        try { mapSet.invoke(null, p, c.marker, c.dim, c.x, c.y, c.z, COLOR, false); } catch (Throwable ignored) { }
    }

    private static void removeMarker(ServerPlayer p, String name) {
        if (!mapReady()) return;
        try { mapRemove.invoke(null, p, name); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ appel

    public static void call(ServerPlayer police, Entity vehicle, String label) {
        MinecraftServer server = police.getServer();
        String companyName = ImpoundConfig.MECHANIC_COMPANY.get().trim();
        if (companyName.isEmpty()) {   // ancien comportement : message à tout le serveur
            server.getPlayerList().broadcastSystemMessage(Gui.comp(ImpoundConfig.MECHANIC_MESSAGE.get()), false);
            Msg.send(police, "&aLe mécano a été prévenu.");
            return;
        }
        CompanyService companies = MineNorth.companies();
        if (companies == CompanyService.NONE) {
            Msg.send(police, "&cImpossible de prévenir le mécano : le mod Entreprises est absent.");
            return;
        }
        int id = companies.idOf(server, companyName);
        if (id <= 0) {
            Msg.send(police, "&cL'entreprise de dépannage « " + companyName + " » est introuvable ou inactive (config mechanicCompany).");
            return;
        }
        if (!companies.isOpen(server, id)) {
            Msg.send(police, "&cLe dépannage (" + companyName + ") est fermé : personne n'est en service pour le moment.");
            return;
        }
        int x = vehicle.blockPosition().getX(), y = vehicle.blockPosition().getY(), z = vehicle.blockPosition().getZ();
        String dim = vehicle.level().dimension().location().toString();
        long expire = server.overworld().getGameTime() + ImpoundConfig.MECHANIC_MINUTES.get() * 1200L;
        Call c = new Call("Dépannage #" + (++counter), dim, x, y, z, vehicle.getUUID(), expire);
        String msg = ImpoundConfig.MECHANIC_MESSAGE.get() + " &7(" + label + " en &f" + x + " " + y + " " + z + "&7, demandé par &f" + MineNorth.displayName(police) + "&7)";
        int reached = 0;
        for (UUID m : companies.memberIds(server, id)) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p == null) continue;
            p.sendSystemMessage(Gui.comp(msg));
            setMarker(p, c);
            c.notified.add(m);
            reached++;
        }
        if (reached == 0) {   // ouverte mais personne connecté : ne devrait pas arriver (isOpen exige un membre connecté)
            Msg.send(police, "&cPersonne du dépannage n'est connecté pour le moment.");
            return;
        }
        CALLS.add(c);
        Msg.send(police, "&aLe dépannage (" + companyName + ") a été prévenu : " + reached + " personne(s), point posé sur leur carte.");
    }

    // ------------------------------------------------------------------ fin de l'appel

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || CALLS.isEmpty() || e.getServer().getTickCount() % 20 != 0) return;
        MinecraftServer server = e.getServer();
        long now = server.overworld().getGameTime();
        Iterator<Call> it = CALLS.iterator();
        while (it.hasNext()) {
            Call c = it.next();
            boolean done = now >= c.expireTick;
            Entity veh = null;
            for (var level : server.getAllLevels()) {
                veh = level.getEntity(c.vehicle);
                if (veh != null) break;
            }
            if (veh == null || veh.isRemoved()) done = true;
            if (!done) {   // un mécano est arrivé sur place ?
                for (UUID m : c.notified) {
                    ServerPlayer p = server.getPlayerList().getPlayer(m);
                    if (p != null && p.level().dimension().location().toString().equals(c.dim)
                            && p.distanceToSqr(c.x + 0.5, c.y, c.z + 0.5) < 49) { done = true; break; }
                }
            }
            if (!done) continue;
            for (UUID m : c.notified) {
                ServerPlayer p = server.getPlayerList().getPlayer(m);
                if (p != null) removeMarker(p, c.marker);
            }
            it.remove();
        }
    }
}
