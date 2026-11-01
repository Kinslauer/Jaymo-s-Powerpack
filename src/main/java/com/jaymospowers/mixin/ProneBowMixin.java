package com.jaymospowers.mixin;

import com.jaymospowers.core.Crawl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.18.85) Prone on the belly there's no room to draw a bow: only on your back (Crawl#SUPINE). */
@Mixin(BowItem.class)
abstract class ProneBowMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$proneBow(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (Crawl.crawling(player) && Crawl.canCrawl(player) && !Crawl.supine(player)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
