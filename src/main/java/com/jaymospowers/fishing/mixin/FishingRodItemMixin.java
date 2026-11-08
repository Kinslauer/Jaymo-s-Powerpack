package com.jaymospowers.fishing.mixin;

import com.jaymospowers.fishing.FishCast;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (Track A) The vanilla rod casts on release: holding use with no hook out charges the cast, letting go throws it, farther
 * the longer it was held ({@link FishCast}). Sneak + use with no hook out turns the drag. With a hook out, use works as before.
 */
@Mixin(FishingRodItem.class)
abstract class FishingRodItemMixin extends Item {
    FishingRodItemMixin(Item.Properties properties) {
        super(properties);
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void jaymospowers$chargeCast(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = FishCast.use(level, player, hand);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity user) {
        return FishCast.USE_TICKS;
    }

    @Override
    public boolean releaseUsing(ItemStack itemStack, Level level, LivingEntity entity, int remainingTime) {
        InteractionHand hand = entity.getUsedItemHand();
        return FishCast.release(itemStack, level, entity, remainingTime,
                () -> this.use(level, (Player) entity, hand));
    }
}
