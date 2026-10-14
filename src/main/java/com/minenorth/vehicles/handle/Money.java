package com.minenorth.vehicles.handle;

import com.minenorth.vehicles.config.VehicleConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class Money {
    private Money() {}

    public static int count(ServerPlayer p, List<VehicleConfig.Denom> den) {
        int total = 0;
        for (VehicleConfig.Denom d : den) total += d.value() * countItem(p, d.item());
        return total;
    }

    /** Espèces comptées dans l'inventaire et dans les portefeuilles. */
    private static int countItem(Player p, Item item) {
        return fr.minenorth.api.Carried.count(p, item);
    }

    private static void remove(Player p, Item item, int n) {
        fr.minenorth.api.Carried.remove(p, item, n);
    }

    /** Retire le prix (grosses coupures d'abord ; surpaye avec les plus petites si le compte n'est pas rond). */
    public static void take(ServerPlayer p, int price, List<VehicleConfig.Denom> den) {
        int remaining = price;
        for (VehicleConfig.Denom d : den) { // décroissant
            if (remaining <= 0) break;
            int k = Math.min(countItem(p, d.item()), remaining / d.value());
            if (k > 0) {
                remove(p, d.item(), k);
                remaining -= k * d.value();
            }
        }
        for (int i = den.size() - 1; i >= 0 && remaining > 0; i--) { // croissant
            VehicleConfig.Denom d = den.get(i);
            while (remaining > 0 && countItem(p, d.item()) > 0) {
                remove(p, d.item(), 1);
                remaining -= d.value();
            }
        }
        // Un billet trop gros a été pris : on rend la monnaie.
        if (remaining < 0) give(p, -remaining, den);
        p.getInventory().setChanged();
    }

    /** Donne un montant en billets/pièces de la config (du plus gros au plus petit). */
    public static void give(ServerPlayer p, int amount, List<VehicleConfig.Denom> den) {
        int remaining = amount;
        for (VehicleConfig.Denom d : den) {            // décroissant
            int k = remaining / d.value();
            while (k > 0) {
                int n = Math.min(k, d.item().getMaxStackSize());
                ItemStack stack = new ItemStack(d.item(), n);
                p.getInventory().add(stack);
                if (!stack.isEmpty()) p.drop(stack, false);
                k -= n;
                remaining -= n * d.value();
            }
        }
        if (remaining > 0) {
            com.minenorth.vehicles.VehiclesMod.LOGGER.warn("[Vehicules] {}€ de monnaie non rendus à {} : aucune coupure assez petite dans la config 'currency'.",
                    remaining, p.getGameProfile().getName());
        }
    }
}
