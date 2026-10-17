package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.VillagerHeritage;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A villager's heritage and tail ({@link VillagerHeritage}) in its own saved data too: a baby carried as an item is
 * born again from that data alone (MCA's {@code BabyItem}), without the attachment or the tags.
 */
@Mixin(VillagerEntityMCA.class)
abstract class McaHeritageMixin {
	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void jaymoskingdoms$saveBlood(ValueOutput output, CallbackInfo ci) {
		VillagerEntityMCA self = (VillagerEntityMCA)(Object)this;
		Heritage.Kind kind = VillagerHeritage.of(self);
		if (kind != null) {
			output.putString(VillagerHeritage.SAVE_KEY, kind.key());
			output.putString(VillagerHeritage.SAVE_TAIL, VillagerHeritage.tail(self));
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void jaymoskingdoms$loadBlood(ValueInput input, CallbackInfo ci) {
		Heritage.Kind kind = Heritage.Kind.byKey(input.getStringOr(VillagerHeritage.SAVE_KEY, ""));
		if (kind != null) {
			String tail = input.getStringOr(VillagerHeritage.SAVE_TAIL, "");
			VillagerHeritage.born((VillagerEntityMCA)(Object)this, kind, tail.isEmpty() ? null : tail);
		}
	}
}
