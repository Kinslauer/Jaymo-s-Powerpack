package com.jaymospowers.client;

import com.jaymospowers.core.Crawl;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Crawl gesture: press Jump and Sneak together twice in quick succession and you go flat; you stay flat while Jump is
 * held (Sneak can be let go). Releasing Jump stands you up when there's headroom; under a one-block ceiling vanilla keeps
 * you crawling until you're out. While crawling, double-tap Left or Right to roll one block that way (puts fire out).
 */
final class CrawlTaps {
	/** The most ticks between two Jump + Sneak presses that count as a double tap. */
	private static final int DOUBLE_TAP_TICKS = 8;
	/** The most ticks between two Left (or two Right) presses that count as a double tap for a roll. */
	private static final int ROLL_TAP_TICKS = 7;

	private static boolean bothWereDown;
	private static int sinceLastTap = Integer.MAX_VALUE;
	private static boolean crawling;

	private static boolean leftWasDown;
	private static boolean rightWasDown;
	/** Ticks since the last Left / Right press while crawling; which one it was. */
	private static int sinceSideTap = Integer.MAX_VALUE;
	private static boolean lastTapRight;
	/** Ticks of the current roll still to slide (0 when not rolling), and which way. */
	private static int rollLeft;
	private static boolean rollRight;
	/** Client game time the local roll started, for drawing it before the server's copy arrives. */
	private static long rollStart = Long.MIN_VALUE;

	private CrawlTaps() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(CrawlTaps::tick);
	}

	/** The local player's roll progress (see {@link Crawl#rollProgress}), or null when they aren't rolling. */
	static @Nullable Float localRollProgress(long gameTime, float partialTick) {
		float t = (gameTime - rollStart + partialTick) / Crawl.ROLL_TICKS;
		if (rollStart == Long.MIN_VALUE || t < 0.0F || t >= 1.0F) {
			return null;
		}
		return rollRight ? t : -t;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			crawling = false;
			Crawl.localCrawler = -1;
			bothWereDown = false;
			sinceLastTap = Integer.MAX_VALUE;
			rollLeft = 0;
			rollStart = Long.MIN_VALUE;
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
		tickRoll(minecraft, player);
	}

	private static void tickRoll(Minecraft minecraft, LocalPlayer player) {
		boolean left = minecraft.options.keyLeft.isDown();
		boolean right = minecraft.options.keyRight.isDown();
		if (sinceSideTap < Integer.MAX_VALUE) {
			sinceSideTap++;
		}
		if (rollLeft > 0) {
			rollLeft--;
			// slide a share of the block each tick (the next tick's move uses this; walls stop it as usual)
			double yaw = Math.toRadians(player.getYRot());
			double step = (rollRight ? Crawl.ROLL_DISTANCE : -Crawl.ROLL_DISTANCE) / Crawl.ROLL_TICKS;
			Vec3 motion = player.getDeltaMovement();
			player.setDeltaMovement(-Math.cos(yaw) * step, motion.y, -Math.sin(yaw) * step);
		} else if (crawling && minecraft.gui.screen() == null) {
			boolean pressedLeft = left && !leftWasDown;
			boolean pressedRight = right && !rightWasDown;
			if (pressedLeft != pressedRight) {
				if (sinceSideTap <= ROLL_TAP_TICKS && lastTapRight == pressedRight) {
					rollLeft = Crawl.ROLL_TICKS;
					rollRight = pressedRight;
					rollStart = player.level().getGameTime();
					ClientPlayNetworking.send(new Crawl.Roll(pressedRight));
					sinceSideTap = Integer.MAX_VALUE;
				} else {
					sinceSideTap = 0;
					lastTapRight = pressedRight;
				}
			}
		} else {
			sinceSideTap = Integer.MAX_VALUE;
		}
		leftWasDown = left;
		rightWasDown = right;
	}

	private static void stop() {
		crawling = false;
		Crawl.localCrawler = -1;
		ClientPlayNetworking.send(new Crawl.Key(false));
	}
}
