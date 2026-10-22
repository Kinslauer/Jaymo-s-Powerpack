package com.jaymospowers.client;

import com.jaymospowers.core.Crawl;
import com.jaymospowers.core.wounds.Downed;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Going prone: press the Crawl key (Down Arrow by default, rebindable) and you stay flat with nothing held; press it
 * again (or Jump) to get up, which waits until there's room to stand. While crawling, double-tap Left or Right to roll
 * one block that way; keep holding it to keep rolling, for up to 2 s one way before you must roll back (rolling back
 * puts the fire on you out).
 * <p>
 * (0.15.108) Pressed while hanging from a ledge, it climbs you in onto your belly when there's room to lie up there
 * ({@link LedgeClient#proneClimb}; otherwise it does nothing, rather than dropping you). A slide that ends with Sneak still
 * held leaves you prone ({@link #goProne}); you get up when you let go of Sneak (once there's room), or as usual.
 */
public final class CrawlTaps {
	/** The most ticks between two Left (or two Right) presses that count as a double tap for a roll. */
	private static final int ROLL_TAP_TICKS = 7;

	private static KeyMapping crawlKey;
	private static boolean crawling;
	/** Jump must be let go once after going prone before a press of it stands you up. */
	private static boolean jumpReleased;
	/** (0.15.108) Prone from a held slide: letting go of Sneak gets you up. */
	private static boolean whileSneaking;

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

	/** (0.15.89) Downed in water without Kitchen's ragdoll: drifting up (blocks a tick, added each tick) to at most this. */
	static final double ADRIFT_RISE = 0.02, ADRIFT_MAX_RISE = 0.06;

	private CrawlTaps() {
	}

	static void init() {
		crawlKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.crawl", InputConstants.KEY_DOWN,
			JaymosPowersClient.CATEGORY));
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
			whileSneaking = false;
			Crawl.localCrawler = -1;
			rollLeft = 0;
			rollStart = Long.MIN_VALUE;
			lastRoll = null;
			return;
		}
		boolean free = minecraft.gui.screen() == null;
		boolean jump = minecraft.options.keyJump.isDown();
		boolean keyPressed = false;
		while (crawlKey.consumeClick()) {
			keyPressed = free;
		}
		if (crawling) {
			boolean jumpPressed = jump && jumpReleased;
			jumpReleased |= !jump;
			boolean sneakLetGo = whileSneaking && !minecraft.options.keyShift.isDown();
			if (!Crawl.canCrawl(player) || (keyPressed || jumpPressed || sneakLetGo) && roomToStand(player)) {
				stop();
			} else if (keyPressed) {
				// (0.15.108) the key takes over a held-slide prone: it stays down till pressed again
				whileSneaking = false;
			}
		} else if (keyPressed && LedgeClient.hanging()) {
			// (0.15.108) on a ledge: in onto your belly if there's room, never a drop
			LedgeClient.proneClimb(player);
		} else if (keyPressed && Crawl.canCrawl(player)) {
			goProne(player, false);
		}
		tickRoll(minecraft, player);
		tickAdrift(player);
	}

	/**
	 * (0.15.89) Downed in water there's no swimming (DownedKeysMixin): the body floats up limp. Kitchen's ragdoll does
	 * that when it's there; without it they drift up here.
	 */
	private static void tickAdrift(LocalPlayer player) {
		if (!Downed.isDowned(player) || !player.isInWater() || com.jaymospowers.power.web.KitchenCompat.isLimp(player)) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x * 0.9, Math.min(ADRIFT_MAX_RISE, v.y + ADRIFT_RISE), v.z * 0.9);
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
		if (com.jaymospowers.core.wounds.Downed.passedOut(player)) {
			// (0.14.1) passed out: lying still
			return;
		}
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

	/** Whether there's room overhead to stand up (or at least crouch, holding Sneak) right where the player lies. */
	private static boolean roomToStand(LocalPlayer player) {
		Pose up = player.isShiftKeyDown() ? Pose.CROUCHING : Pose.STANDING;
		return player.level().noCollision(player, player.getDimensions(up).makeBoundingBox(player.position()).deflate(1.0E-7));
	}

	/**
	 * (0.15.108) Go prone now (as the Crawl key does). {@code whileSneaking}: from a held slide, so letting go of Sneak gets
	 * you up again.
	 */
	public static void goProne(LocalPlayer player, boolean whileSneaking) {
		if (crawling || !Crawl.canCrawl(player)) {
			return;
		}
		crawling = true;
		CrawlTaps.whileSneaking = whileSneaking;
		jumpReleased = !Minecraft.getInstance().options.keyJump.isDown();
		Crawl.localCrawler = player.getId();
		ClientPlayNetworking.send(new Crawl.Key(true));
	}

	private static void stop() {
		crawling = false;
		whileSneaking = false;
		Crawl.localCrawler = -1;
		ClientPlayNetworking.send(new Crawl.Key(false));
	}
}
