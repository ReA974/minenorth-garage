package com.minenorth.vehicles.miscs;

import com.minenorth.vehicles.VehiclesMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * L'asphalte de Roads 'n' Vehicles accélère les piétons (speedFactor 1,25, comme une piste) : on court beaucoup trop vite en ville.
 * Au démarrage, tout bloc qui accélère (aucun bloc vanilla ne le fait) est remis à la vitesse normale. Les véhicules MTS ne sont pas concernés
 * (physique à part). Doit tourner côté client aussi : c'est le client qui calcule le déplacement du joueur.
 */
@Mod.EventBusSubscriber(modid = VehiclesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RoadSpeed {
    private RoadSpeed() {}

    @SubscribeEvent
    public static void onSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            try {
                Field speed = ObfuscationReflectionHelper.findField(BlockBehaviour.class, "f_60448_");   // speedFactor
                List<String> fixed = new ArrayList<>();
                for (Block b : ForgeRegistries.BLOCKS.getValues()) {
                    if (b.getSpeedFactor() > 1.0f) {
                        speed.setFloat(b, 1.0f);
                        fixed.add(String.valueOf(ForgeRegistries.BLOCKS.getKey(b)));
                    }
                }
                if (!fixed.isEmpty()) VehiclesMod.LOGGER.info("[MineNorth] Vitesse normale rendue à {} bloc(s) de route : {}", fixed.size(), fixed);
            } catch (Exception ex) {
                VehiclesMod.LOGGER.warn("[MineNorth] Impossible de retirer le bonus de vitesse des routes", ex);
            }
        });
    }
}
