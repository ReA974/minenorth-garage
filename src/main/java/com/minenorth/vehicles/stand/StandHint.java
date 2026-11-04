package com.minenorth.vehicles.stand;

import com.minenorth.vehicles.VehiclesMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** En regardant une borne de près : « Clic droit : ouvrir le garage / la fourrière » dans la barre d'action (comme les marqueurs GTA RP). */
@Mod.EventBusSubscriber(modid = VehiclesMod.MODID, value = Dist.CLIENT)
public final class StandHint {
    private StandHint() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.player.tickCount % 10 != 0) return;
        if (!(mc.hitResult instanceof BlockHitResult b) || b.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.level.getBlockState(b.getBlockPos()).getBlock() instanceof StandBlock s)) return;
        if (mc.player.distanceToSqr(b.getLocation()) > 4.5 * 4.5) return;
        mc.player.displayClientMessage(Component.literal(s.kind == StandBlock.Kind.GARAGE ? "§b[Clic droit] §fOuvrir le garage" : "§6[Clic droit] §fFourrière : récupérer un véhicule"), true);
    }
}
