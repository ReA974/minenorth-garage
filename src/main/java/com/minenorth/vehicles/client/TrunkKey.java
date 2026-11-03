package com.minenorth.vehicles.client;

import com.minenorth.vehicles.VehiclesMod;
import com.minenorth.vehicles.menu.MenuNet;
import com.minenorth.vehicles.menu.TrunkKeyPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Touche « Coffre du véhicule » (C par défaut, Options > Commandes) : à pied, demande au serveur d'ouvrir le coffre du
 * véhicule visé ou du plus proche. Assis dans un véhicule, rien n'est envoyé : C reste le klaxon de MTS.
 */
@Mod.EventBusSubscriber(modid = VehiclesMod.MODID, value = Dist.CLIENT)
public final class TrunkKey {
    private TrunkKey() {}

    public static final KeyMapping KEY = new KeyMapping("key.minenorth_rp_vehicles.coffre", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.minenorth");

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (KEY.consumeClick()) {
            if (mc.player == null || mc.screen != null || mc.player.isPassenger() || mc.getConnection() == null) continue;
            MenuNet.CHANNEL.sendToServer(new TrunkKeyPacket());
        }
    }

    @Mod.EventBusSubscriber(modid = VehiclesMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent e) {
            e.register(KEY);
        }
    }
}
