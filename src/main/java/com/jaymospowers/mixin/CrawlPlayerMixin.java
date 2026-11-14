package com.jaymospowers.mixin;

import com.jaymospowers.core.Crawl;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player crawling by the gesture lies flat (the swimming pose, as vanilla crawling under a low ceiling uses); (0.15.12) so does one sliding (Freerun), and one in a dodge's roll ({@code Dodge#rollingLow}: under a low gap; vanilla stands them up only where there's room). */
@Mixin(Player.class)
abstract class CrawlPlayerMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$crawl(CallbackInfo ci) {
		Player self = (Player)(Object)this;
		if (Crawl.crawling(self) && Crawl.canCrawl(self)	|| (com.jaymospowers.freerun.Slide.sliding(self) || com.jaymospowers.stamina.Dodge.rollingLow(self)) && !self.isSleeping() && !self.isPassenger() && !self.isFallFlying()) {
			self.setPose(Pose.SWIMMING);
			ci.cancel();
		}
	}
}
