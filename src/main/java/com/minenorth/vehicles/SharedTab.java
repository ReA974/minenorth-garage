package com.minenorth.vehicles;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Les objets de ce mod vont dans l'onglet créatif commun « MineNorth » (fourni par minenorth_api). */
@Mod.EventBusSubscriber(modid = "minenorth_rp_vehicles", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SharedTab {
    private SharedTab() {}

    @SubscribeEvent
    public static void fill(BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().location().equals(new ResourceLocation("minenorthapi", "minenorth"))) return;
        com.minenorth.vehicles.depannage.ModItems.TAB_ITEMS.accept(e.getParameters(), e);
    }
}
