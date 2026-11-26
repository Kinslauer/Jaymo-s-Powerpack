package com.jaymoskitchen.mixin;

import com.jaymoskitchen.stomach.Bites;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Finer hunger ({@link Bites}; Granular Hunger's idea, CC0, by tetro48): the exhaustion is spent a bite at a time before
 * vanilla's own check (which then never sees a whole point's worth), food fills a bitten point without waste, and a
 * bitten point lets you eat again.
 */
@Mixin(FoodData.class)
abstract class GranularFoodDataMixin implements Bites.Holder {
	@Shadow
	private int foodLevel;
	@Shadow
	private float saturationLevel;
	@Shadow
	private float exhaustionLevel;

	@Unique
	private int jaymoskitchen$bites;

	@Override
	public int jaymoskitchen$bites() {
		return jaymoskitchen$bites;
	}

	@Override
	public void jaymoskitchen$setBites(int bites) {
		jaymoskitchen$bites = Math.clamp(bites, 0, Bites.PER_POINT - 1);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void jaymoskitchen$biteByBite(ServerPlayer player, CallbackInfo ci) {
		boolean peaceful = player.level().getDifficulty() == Difficulty.PEACEFUL;
		while (exhaustionLevel > Bites.EXHAUSTION) {
			exhaustionLevel -= Bites.EXHAUSTION;
			if (saturationLevel > 0.0F) {
				saturationLevel = Math.max(saturationLevel - Bites.SATURATION, 0.0F);
			} else if (!peaceful && foodLevel > 0) {
				if (++jaymoskitchen$bites >= Bites.PER_POINT) {
					jaymoskitchen$bites = 0;
					foodLevel--;
				}
			}
		}
		if (peaceful || foodLevel <= 0) {
			// (peaceful fills the bar by itself; an empty bar has nothing left to bite)
			jaymoskitchen$bites = 0;
		}
		Bites.sync(player, jaymoskitchen$bites);
	}

	/** Food onto a bar it would overfill fills the bitten point first. */
	@Inject(method = "add", at = @At("HEAD"))
	private void jaymoskitchen$fillBitten(int food, float saturation, CallbackInfo ci) {
		if (food > 0 && foodLevel + food > Bites.FULL) {
			jaymoskitchen$bites = 0;
		}
	}

	@Inject(method = "needsFood", at = @At("RETURN"), cancellable = true)
	private void jaymoskitchen$bittenNeedsFood(CallbackInfoReturnable<Boolean> cir) {
		if (jaymoskitchen$bites > 0) {
			cir.setReturnValue(true);
		}
	}

	/** Peaceful's refill (a point a half second while food is needed) never runs past a full bar: a bitten point is filled instead. */
	@ModifyVariable(method = "setFoodLevel", at = @At("HEAD"), argsOnly = true)
	private int jaymoskitchen$notPastFull(int food) {
		if (food > Bites.FULL) {
			jaymoskitchen$bites = 0;
			return Bites.FULL;
		}
		return food;
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void jaymoskitchen$readBites(ValueInput input, CallbackInfo ci) {
		jaymoskitchen$setBites(input.getIntOr("jaymoskitchen_bites", 0));
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void jaymoskitchen$saveBites(ValueOutput output, CallbackInfo ci) {
		output.putInt("jaymoskitchen_bites", jaymoskitchen$bites);
	}
}
