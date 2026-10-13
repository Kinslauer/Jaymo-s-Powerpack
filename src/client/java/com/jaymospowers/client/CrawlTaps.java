package com.jaymospowers.client;

import com.jaymospowers.core.Crawl;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Crawl gesture: press Jump and Sneak together twice in quick succession and you go flat; you stay flat while Jump is
 * held (Sneak can be let go). Releasing Jump stands you up when there's headroom; under a one-block ceiling vanilla keeps
 * you crawling until you're out.
 */
final class CrawlTaps {
	/** The most ticks between two Jump + Sneak presses that count as a double tap. */
	private static final int DOUBLE_TAP_TICKS = 8;

	private static boolean bothWereDown;
	private static int sinceLastTap = Integer.MAX_VALUE;
	private static boolean crawling;

	private CrawlTaps() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(CrawlTaps::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			crawling = false;
			Crawl.localCrawler = -1;
			bothWereDown = false;
			sinceLastTap = Integer.MAX_VALUE;
			return;
		}
		boolean jump = minecraft.options.keyJump.isDown();
		boolean both = jump && minecraft.options.keyShift.isDown();
		if (sinceLastTap < Integer.MAX_VALUE) {
			sinceLastTap++;
		}
		if (crawling) {
			if (!jump || !Crawl.canCrawl(player)) {
				stop();
			}
		} else if (both && !bothWereDown && minecraft.gui.screen() == null) {
			if (sinceLastTap <= DOUBLE_TAP_TICKS && Crawl.canCrawl(player)) {
				crawling = true;
				Crawl.localCrawler = player.getId();
				ClientPlayNetworking.send(new Crawl.Key(true));
				sinceLastTap = Integer.MAX_VALUE;
			} else {
				sinceLastTap = 0;
			}
		}
		bothWereDown = both;
	}

	private static void stop() {
		crawling = false;
		Crawl.localCrawler = -1;
		ClientPlayNetworking.send(new Crawl.Key(false));
	}
}
