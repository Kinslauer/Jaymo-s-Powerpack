package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Prejudice;
import com.jaymoskingdoms.Purists;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.130) A purist king's traders charge those not of his blood more, and their own kind less ({@link Purists#prices});
 * (0.7.221) a trader who looks down on a non-human blood charges it more ({@link Prejudice#prices}). */
@Mixin(Villager.class)
abstract class VillagerPriceMixin {
	@Inject(method = "updateSpecialPrices", at = @At("TAIL"))
	private void jaymoskingdoms$purist(Player player, CallbackInfo ci) {
		Purists.prices((Villager)(Object)this, player);
		// (0.7.221) and one who looks down on your blood
		Prejudice.prices((Villager)(Object)this, player);
		// (0.7.509) and one who distrusts your faith, or holds it with you
		com.jaymoskingdoms.Faiths.prices((Villager)(Object)this, player);
		// (0.7.439) a haven's own wares, or a human town buying them
		com.jaymoskingdoms.HavenWares.offers((Villager)(Object)this, player);
		// (0.7.519) and a trade charter partner's people pay less
		com.jaymoskingdoms.Treaties.prices((Villager)(Object)this, player);
		// (0.7.523) and how they feel about you: fond ones charge less, sour ones more
		com.jaymoskingdoms.Bearings.prices((Villager)(Object)this, player);
		// a haggler companion by you (docs/Pawn-Plan.md row G2)
		com.jaymoskingdoms.Specialties.prices((Villager)(Object)this, player);
		// (0.7.490) priced in the coin of the town it stands in, or a money-changer's counter
		com.jaymoskingdoms.Currencies.localize((Villager)(Object)this, player);
	}
}
