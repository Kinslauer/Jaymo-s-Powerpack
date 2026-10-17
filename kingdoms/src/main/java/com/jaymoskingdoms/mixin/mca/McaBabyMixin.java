package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.VillagerHeritage;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.registry.DataComponentsMCA;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A baby made with a player: MCA combines the child's genes from a stand-in for the player, who carries no heritage, so
 * the baby's blood is settled again here from the real parents ({@link VillagerHeritage#babyBlood}) and written into
 * the data it's born from.
 */
@Mixin(BabyItem.class)
abstract class McaBabyMixin {
	@Inject(method = "createItem", at = @At("RETURN"))
	private static void jaymoskingdoms$babyBlood(Entity mother, Entity father, long seed, CallbackInfoReturnable<ItemStack> cir) {
		Heritage.Kind kind = VillagerHeritage.babyBlood(mother, father);
		if (kind != null) {
			CustomData.update(DataComponentsMCA.BABY_NBT, cir.getReturnValue(), tag -> {
				tag.putString(VillagerHeritage.SAVE_KEY, kind.key());
				// the tail is settled when it's born
				tag.remove(VillagerHeritage.SAVE_TAIL);
			});
		}
	}
}
