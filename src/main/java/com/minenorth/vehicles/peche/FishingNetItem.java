package com.minenorth.vehicles.peche;

import com.minenorth.vehicles.depannage.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Filet de pêche : clic droit à bord d'un bateau = mise à l'eau, puis remontée. Toute la logique est côté serveur ({@link FishingNet}).
 * Il s'use à chaque remontée (64 points) ; déchiré, il ne se déploie plus : bobine de fil de pêche ou enclume pour le réparer.
 */
public class FishingNetItem extends Item {
    public static final int DURABILITY = 64;

    public FishingNetItem() { super(new Item.Properties().durability(DURABILITY)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) FishingNet.use(sp, held);
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    /** Filet déchiré : il ne reste qu'un point (il n'est jamais détruit). */
    public static boolean isTorn(ItemStack s) {
        return s.getItem() instanceof FishingNetItem && s.getDamageValue() >= s.getMaxDamage() - 1;
    }

    /** Filet porté par le joueur : main principale, autre main, puis inventaire. */
    public static ItemStack find(Player p) {
        for (InteractionHand h : InteractionHand.values()) {
            if (p.getItemInHand(h).getItem() instanceof FishingNetItem) return p.getItemInHand(h);
        }
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof FishingNetItem) return s;
        return ItemStack.EMPTY;
    }

    /** Réparation à l'enclume : bobine de fil de pêche ou ficelle. */
    @Override
    public boolean isValidRepairItem(ItemStack net, ItemStack repair) {
        return repair.is(ModItems.FISHING_NET_SPOOL.get()) || repair.is(Items.STRING);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable(getDescriptionId() + ".tooltip2").withStyle(ChatFormatting.DARK_AQUA));
        if (isTorn(stack)) tip.add(Component.translatable(getDescriptionId() + ".dechire").withStyle(ChatFormatting.RED));
    }
}
