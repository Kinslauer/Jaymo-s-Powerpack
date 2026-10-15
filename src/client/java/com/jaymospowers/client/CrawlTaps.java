package com.jaymospowers.client;

import com.jaymospowers.core.Crawl;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Going prone, three ways: press the Crawl key (Mouse 5 by default, rebindable), sneak forward into a one-block-high gap
 * (you drop flat by yourself and get back up once you're out with room overhead), or hold Jump + Sneak together for a
 * moment. You then stay flat with nothing held; press Jump or the Crawl key to get up, which waits until there's room
 * to stand. While crawling, double-tap Left or Right to roll one block that way; keep holding it to keep rolling, for up
 * to 2 s one way before you must roll back (rolling back puts the fire on you out).
 */
final class CrawlTaps {
	/** How long Jump + Sneak must be held together to go prone (short, so a quick sneak-jump doesn't drop you). */
	private static final int HOLD_TICKS = 4;
	/** The most ticks between two Left (or two Right) presses that count as a double tap for a roll. */
	private static final int ROLL_TAP_TICKS = 7;
	/** How far ahead of you (blocks) sneaking forward looks for a gap only a crawl fits through. */
	private static final double GAP_REACH = 0.35;
	/** A gap crawl that never gets under the ceiling ends after this long (you backed off). */
	private static final int GAP_GIVE_UP_TICKS = 20;

	private static KeyMapping crawlKey;
	/** Ticks Jump + Sneak have been held together (0 when they aren't). */
	private static int bothHeld;
	private static boolean crawling;
	/** Whether this crawl began by sneaking into a gap (it then ends by itself once you're out from under it). */
	private static boolean autoCrawl;
	/** Ticks of a gap crawl so far, and whether the low ceiling has been over you yet. */
	private static int autoTicks;
	private static boolean autoCovered;
	/** Jump must be let go once after going prone before a press of it stands you up (the gesture holds it down). */
	private static boolean jumpReleased;
	/** Set on getting up until Jump + Sneak are no longer both held. */
	private static boolean gestureSpent;

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
		crawlKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.crawl", InputConstants.Type.MOUSE,
			InputConstants.MOUSE_BUTTON_5, JaymosPowersClient.CATEGORY));
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
			autoCrawl = false;
			Crawl.localCrawler = -1;
			bothHeld = 0;
			rollLeft = 0;
			rollStart = Long.MIN_VALUE;
			lastRoll = null;
			return;
		}
		boolean free = minecraft.gui.screen() == null;
		boolean jump = minecraft.options.keyJump.isDown();
		boolean both = jump && minecraft.options.keyShift.isDown();
		bothHeld = both && free ? bothHeld + 1 : 0;
		// getting up with Jump while still on Sneak mustn't count as the gesture again
		gestureSpent &= both;
		boolean keyPressed = false;
		while (crawlKey.consumeClick()) {
			keyPressed = free;
		}
		if (crawling) {
			boolean jumpPressed = jump && jumpReleased;
			jumpReleased |= !jump;
			if (!Crawl.canCrawl(player)) {
				stop();
			} else if (keyPressed || jumpPressed || autoCrawl) {
				boolean room = roomToStand(player);
				autoCovered |= !room;
				autoTicks++;
				// a gap crawl ends once you're out the far side, or if you never went under (backed off)
				boolean autoDone = autoCrawl && (autoCovered || autoTicks > GAP_GIVE_UP_TICKS);
				if (room && (keyPressed || jumpPressed || autoDone)) {
					stop();
				}
			}
		} else if (Crawl.canCrawl(player) && (keyPressed || bothHeld >= HOLD_TICKS && !gestureSpent)) {
			start(player, false, !jump);
		} else if (Crawl.canCrawl(player) && free && sneakingIntoGap(minecraft, player)) {
			start(player, true, !jump);
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

	private static void start(LocalPlayer player, boolean auto, boolean jumpUp) {
		crawling = true;
		autoCrawl = auto;
		autoTicks = 0;
		autoCovered = false;
		jumpReleased = jumpUp;
		Crawl.localCrawler = player.getId();
		ClientPlayNetworking.send(new Crawl.Key(true));
	}

	/** Whether the player is sneaking forward on the ground into an opening a crawl fits through but a crouch doesn't. */
	private static boolean sneakingIntoGap(Minecraft minecraft, LocalPlayer player) {
		if (!player.isShiftKeyDown() || !player.onGround() || !minecraft.options.keyUp.isDown()) {
			return false;
		}
		double yaw = Math.toRadians(player.getYRot());
		Vec3 ahead = new Vec3(-Math.sin(yaw) * GAP_REACH, 0.0, Math.cos(yaw) * GAP_REACH);
		AABB crouched = player.getDimensions(Pose.CROUCHING).makeBoundingBox(player.position()).move(ahead);
		AABB flat = player.getDimensions(Pose.SWIMMING).makeBoundingBox(player.position()).move(ahead);
		return !player.level().noCollision(player, crouched) && player.level().noCollision(player, flat);
	}

	/** Whether there's room overhead to stand up (or at least crouch, holding Sneak) right where the player lies. */
	private static boolean roomToStand(LocalPlayer player) {
		Pose up = player.isShiftKeyDown() ? Pose.CROUCHING : Pose.STANDING;
		return player.level().noCollision(player, player.getDimensions(up).makeBoundingBox(player.position()).deflate(1.0E-7));
	}

	private static void stop() {
		crawling = false;
		autoCrawl = false;
		gestureSpent = true;
		Crawl.localCrawler = -1;
		ClientPlayNetworking.send(new Crawl.Key(false));
	}
}
