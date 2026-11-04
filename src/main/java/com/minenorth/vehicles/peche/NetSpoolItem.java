package com.minenorth.vehicles.peche;

import com.minenorth.vehicles.miscs.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Bobine de fil de pêche (vendue à la poissonnerie) : clic droit avec le filet dans l'autre main = réparation. Vérifié par le serveur. */
public class NetSpoolItem extends Item {
    public NetSpoolItem() { super(new Item.Properties().stacksTo(16)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack spool = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(spool, level.isClientSide);
        ItemStack net = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(net.getItem() instanceof FishingNetItem)) {
            Msg.send(sp, "&cTiens ton filet de pêche dans l'autre main pour le réparer.");
            return InteractionResultHolder.fail(spool);
        }
        if (net.getDamageValue() == 0) {
            Msg.send(sp, "&7Ton filet est comme neuf.");
            return InteractionResultHolder.fail(spool);
        }
        net.setDamageValue(Math.max(0, net.getDamageValue() - FishingNetConfig.BOBINE_REPAIR.get()));
        if (!sp.getAbilities().instabuild) spool.shrink(1);
        level.playSound(null, sp.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1f, 1.2f);
        int pct = Math.round(100f * (net.getMaxDamage() - net.getDamageValue()) / net.getMaxDamage());
        Msg.send(sp, "&aFilet ravaudé : &f" + pct + " %");
        return InteractionResultHolder.consume(spool);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
