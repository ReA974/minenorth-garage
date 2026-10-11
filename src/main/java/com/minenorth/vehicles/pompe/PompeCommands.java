package com.minenorth.vehicles.pompe;

import com.minenorth.vehicles.VehiclesMod;
import com.minenorth.vehicles.config.VehicleConfig;
import com.minenorth.vehicles.miscs.Msg;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /pompe limiter | illimitee | statut : sur la pompe MTS visée (6 blocs). Une pompe illimitée distribue sans jamais vider son réservoir ;
 * une pompe limitée est vidée par les pleins et se remplit avec l'essence raffinée (menu de la pompe). Voir MtsPump.setLimited.
 */
@Mod.EventBusSubscriber(modid = VehiclesMod.MODID)
public final class PompeCommands {
    private PompeCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("pompe")
                .requires(s -> s.hasPermission(VehicleConfig.COMMAND_LEVEL.get()))
                .then(Commands.literal("limiter").executes(c -> run(c, 1)))
                .then(Commands.literal("illimitee").executes(c -> run(c, 0)))
                .then(Commands.literal("statut").executes(c -> run(c, 2))));
    }

    private static int run(CommandContext<CommandSourceStack> c, int mode) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        HitResult hit = p.pick(6.0, 0f, false);
        Object pump = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? MtsPump.pumpAt(p.level(), b.getBlockPos()) : null;
        if (pump == null) {
            Msg.send(p, "&cVise une pompe à carburant MTS (moins de 6 blocs).");
            return 0;
        }
        try {
            if (mode == 2) {
                Object tank = MtsPump.tank(pump);
                double perL = VehicleConfig.PUMP_MB_PER_LITER.get();
                Msg.send(p, "&7Pompe : " + (MtsPump.creative(pump) ? "&aillimitée" : "&elimitée &7(stock " + Math.round(MtsPump.level(tank) / perL * 10) / 10.0
                        + " L / " + Math.round(MtsPump.max(tank) / perL * 10) / 10.0 + " L)") + "&7.");
                return 1;
            }
            if (!MtsPump.setLimited(pump, mode == 1)) {
                Msg.send(p, "&cImpossible : inventaire de la pompe MTS illisible (voir le log).");
                return 0;
            }
            if (mode == 1) {
                Msg.send(p, "&aPompe limitée : elle se vide à chaque plein. Remplis-la avec l'essence raffinée (clic droit sur la pompe > Remplir).");
            } else if (MtsPump.hasFuelItems(pump)) {
                Msg.send(p, "&eRepère retiré, mais un autre objet est dans l'inventaire de la pompe : elle reste limitée. Vide-le avec l'écran natif (OP + accroupi).");
            } else {
                Msg.send(p, "&aPompe illimitée : elle ne se vide plus.");
            }
            return 1;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            VehiclesMod.LOGGER.warn("[Pompe] /pompe : erreur", ex);
            Msg.send(p, "&cErreur sur la pompe (voir le log).");
            return 0;
        }
    }
}
