package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.power.chrono.ChronoRapid;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A speedster's bow is at full draw after a few ticks: released then, the arrow goes as if drawn for the whole time. */
@Mixin(BowItem.class)
abstract class ChronoRapidBowMixin {
	@ModifyVariable(method = "releaseUsing", at = @At("HEAD"), argsOnly = true)
	private int jaymospowers$rapidDraw(int remaining, @Local(argsOnly = true) LivingEntity entity) {
		BowItem self = (BowItem) (Object) this;
		int drawn = self.getUseDuration(entity.getUseItem(), entity) - remaining;
		return ChronoRapid.rapid(entity) && drawn >= ChronoRapid.BOW_DRAW_TICKS ? 0 : remaining;
	}
}
