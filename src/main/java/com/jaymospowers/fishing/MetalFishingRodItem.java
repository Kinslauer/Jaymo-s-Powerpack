package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The metal fishing rod, forged through Jaymo's Tinkering: a cast iron head fitted with a handle and binding at the
 * Tool Station. Stronger line than a wooden rod, and it can hook big fish - up to sharks - on the right water.
 */
public class MetalFishingRodItem extends Item {
    public MetalFishingRodItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Cast the hook like a fishing rod: (Track A) hold use to charge, release to throw ({@link FishCast}); sneak + use turns the
     * drag. While a fight is on, reeling is driven by holding use (FishingRealClient). Use with an idle hook out retrieves it.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionResult charge = FishCast.use(level, player, hand);
        if (charge != null) {
            return charge;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.fishing != null) {
            // already have a hook out: reel it in (or strike a taken hook: FishingHookMixin)
            if (!level.isClientSide()) {
                int dmg = player.fishing.retrieve(stack);
                stack.hurtAndBreak(dmg, player, hand.asEquipmentSlot());
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL,
                    1.0F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
            stack.causeUseVibration(player, net.minecraft.world.level.gameevent.GameEvent.ITEM_INTERACT_FINISH);
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || FishingReal.fight(serverPlayer).fighting()) {
            return InteractionResult.SUCCESS;
        }
        net.minecraft.world.entity.EntityType<? extends FishingHook> hookType =
                (net.minecraft.world.entity.EntityType<? extends FishingHook>) BuiltInRegistries.ENTITY_TYPE
                        .get(net.minecraft.resources.Identifier.withDefaultNamespace("fishing_hook")).orElseThrow().value();
        FishingHook hook = new FishingHook(hookType, serverLevel);
        hook.setOwner(player);
        player.fishing = hook;
        hook.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());
        hook.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
        serverLevel.addFreshEntity(hook);
        serverLevel.playSound(null, player.getX(), player.getEyeY() - 0.1D, player.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        stack.hurtAndBreak(1, serverPlayer, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        stack.causeUseVibration(player, net.minecraft.world.level.gameevent.GameEvent.ITEM_INTERACT_START);
        return InteractionResult.SUCCESS;
    }

    /** (Track A) Charging the cast lasts until use is let go. */
    @Override
    public int getUseDuration(ItemStack itemStack, net.minecraft.world.entity.LivingEntity user) {
        return FishCast.USE_TICKS;
    }

    /** (Track A) Letting go of use throws the hook, farther the longer it was charged. */
    @Override
    public boolean releaseUsing(ItemStack itemStack, Level level, net.minecraft.world.entity.LivingEntity entity, int remainingTime) {
        InteractionHand hand = entity.getUsedItemHand();
        return FishCast.release(itemStack, level, entity, remainingTime, () -> this.use(level, (Player) entity, hand));
    }
}
