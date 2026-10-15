package com.jaymospowers.mixin;

import com.jaymospowers.core.Crawl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A crawling player never hops: Jump is how you get up (the client ends the crawl first when there's room). */
@Mixin(LivingEntity.class)
abstract class CrawlJumpMixin {
	@Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noHopWhileCrawling(CallbackInfo ci) {
		if ((Object)this instanceof Player player && Crawl.crawling(player) && Crawl.canCrawl(player)) {
			ci.cancel();
		}
	}
}
