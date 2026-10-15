package com.jaymospowers.client;

import com.jaymospowers.core.Crawl;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Crawl gesture: hold Jump and Sneak together for a moment and you go flat; you stay flat while Jump is held (Sneak can
 * be let go). Releasing Jump stands you up when there's headroom; under a one-block ceiling vanilla keeps you crawling
 * until you're out. While crawling, double-tap Left or Right to roll one block that way; keep holding it to keep rolling,
 * for up to 2 s one way before you must roll back (rolling back puts the fire on you out).
 */
final class CrawlTaps {
	/** How long Jump + Sneak must be held together to go prone (short, so a quick sneak-jump doesn't drop you). */
	private static final int HOLD_TICKS = 4;
	/** The most ticks between two Left (or two Right) presses that count as a double tap for a roll. */
	private static final int ROLL_TAP_TICKS = 7;

	/** Ticks Jump + Sneak have been held together (0 when they aren't). */
	private static int bothHeld;
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
	/** The local player's last roll, to spend a direction after 2 s just as the server does. */
	private static Crawl.@Nullable Rolling lastRoll;

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
			bothHeld = 0;
			rollLeft = 0;
			rollStart = Long.MIN_VALUE;
			lastRoll = null;
			return;
		}
		boolean jump = minecraft.options.keyJump.isDown();
		boolean both = jump && minecraft.options.keyShift.isDown();
		bothHeld = both && minecraft.gui.screen() == null ? bothHeld + 1 : 0;
		if (crawling) {
			if (!jump || !Crawl.canCrawl(player)) {
				stop();
			}
		} else if (bothHeld >= HOLD_TICKS && Crawl.canCrawl(player)) {
			crawling = true;
			Crawl.localCrawler = player.getId();
			ClientPlayNetworking.send(new Crawl.Key(true));
		}
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
			// holding the key keeps you rolling that way, one block after another
			if (rollLeft == 0 && crawling && minecraft.gui.screen() == null && (rollRight ? right : left)) {
				startRoll(player, rollRight);
			}
		} else if (crawling && minecraft.gui.screen() == null) {
			boolean pressedLeft = left && !leftWasDown;
			boolean pressedRight = right && !rightWasDown;
			if (pressedLeft != pressedRight) {
				if (sinceSideTap <= ROLL_TAP_TICKS && lastTapRight == pressedRight) {
					startRoll(player, pressedRight);
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

	private static void startRoll(LocalPlayer player, boolean right) {
		long now = player.level().getGameTime();
		Crawl.Rolling next = Crawl.nextRoll(lastRoll, now, right);
		if (next == null) {
			// this way is spent: roll back the other way first
			return;
		}
		lastRoll = next;
		rollLeft = Crawl.ROLL_TICKS;
		rollRight = right;
		rollStart = now;
		ClientPlayNetworking.send(new Crawl.Roll(right));
	}

	private static void stop() {
		crawling = false;
		Crawl.localCrawler = -1;
		ClientPlayNetworking.send(new Crawl.Key(false));
	}
}
