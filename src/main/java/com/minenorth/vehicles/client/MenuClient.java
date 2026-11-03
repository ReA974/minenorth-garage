package com.minenorth.vehicles.client;

import com.minenorth.vehicles.menu.OpenGaragePacket;
import com.minenorth.vehicles.menu.OpenMenuPacket;
import com.minenorth.vehicles.menu.OpenSalePacket;
import com.minenorth.vehicles.menu.OpenShopPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MenuClient {
    private MenuClient() {}

    public static void open(OpenMenuPacket m) {
        Minecraft.getInstance().setScreen(new MenuScreen(m));
    }

    public static void openShop(OpenShopPacket m) {
        Minecraft.getInstance().setScreen(new ShopScreen(m));
    }

    public static void openGarage(OpenGaragePacket m) {
        Minecraft.getInstance().setScreen(new GarageScreen(m));
    }

    public static void openSale(OpenSalePacket m) {
        Minecraft.getInstance().setScreen(m.offer == null ? new SaleScreen(m) : new OfferScreen(m));
    }
}
