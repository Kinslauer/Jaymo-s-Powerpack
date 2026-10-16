package com.jaymospowers.client.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Powers;
import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigActives;
import com.jaymospowers.rig.RigCall;
import com.jaymospowers.rig.RigNet;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Rig on the client. Keys: with no power, R hover, G palm blast, V shoulder launcher, B the Mind's report (held: a
 * scan, level 3), C park / call the suit (level 3) (the power keys are free then). With a power those stay the power's,
 * and the Rig key (J by default) uses the selected active; Sneak + J picks the next (Scan and Suit join the cycle at
 * level 3). Holding the Rig key locks on to what's under the crosshair (level 2; held with nothing there, it lets go).
 * Double-tap Jump while hovering: full flight (level 5). Hover and flight are held here (the client moves the player):
 * Jump rises, Sneak sinks; in flight you go where you look. The Helm's HUD is {@link RigHud}; scans are
 * {@link RigScanClient}.
 */
public final class RigClient {
	/** Hover: rise / sink speed (b/t; slower than 0.5, so the descent is a controlled one), the push forward and its cap. */
	static final double HOVER_RISE = 0.22, HOVER_SINK = 0.2, HOVER_PUSH = 0.03, HOVER_MAX = 0.35;
	/** The gentle bob while holding height. */
	static final double HOVER_BOB = 0.015;
	/** (0.11.87) Full flight: top speed along the look (b/t; Sprint: this much more), climb / dive, how quickly it gets there (share a tick). */
	static final double FLIGHT_SPEED = 0.85, FLIGHT_SPRINT = 1.3, FLIGHT_CLIMB = 0.35, FLIGHT_ACCEL = 0.12, FLIGHT_STRAFE = 0.35;
	/** Holding the Rig key this long (ticks) locks on instead of using the active; B held this long scans. */
	static final int LOCK_HOLD = 8, SCAN_HOLD = 12;
	/** Two Jump presses this close (ticks) toggle full flight. */
	static final int JUMP_TAP_TICKS = 7;
	/** Lock-on picks the creature nearest the crosshair within this cone (dot of the directions). */
	static final double LOCK_CONE = 0.97;
	/** The takeover: how long the screen shows it, and how many ticks the view turns to the threat (share per tick). */
	static final int TAKEOVER_SCREEN = 30, TAKEOVER_TURN = 8, TAKEOVER_HANDS_OFF = 14;
	static final float TAKEOVER_TURN_SHARE = 0.4F;

	private static KeyMapping rigKey;
	private static final boolean[] powerKeysDown = new boolean[PowerKey.values().length];
	/** The active J uses (one of {@link RigNet#CYCLE}). */
	static int selected = RigNet.PALM;
	/** The server's last word. */
	static RigNet.Status status = RigNet.Status.NONE;
	private static int hoverTicks;
	private static Vec3 flightVelocity = Vec3.ZERO;
	private static boolean rigWasDown, rigShift, rigHeldDone;
	private static int rigHeld;
	private static int scanHeld;
	private static boolean callWasDown, jumpWasDown;
	private static int sinceJump = Integer.MAX_VALUE;
	/** Ticks left of the takeover's screen (0: none), and the threat the view turns to. */
	static int takeoverTicks;
	private static Vec3 takeoverThreat = Vec3.ZERO;

	private RigClient() {
	}

	public static void init() {
		rigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.rig", InputConstants.KEY_J, JaymosPowersClient.CATEGORY));
		ClientPlayNetworking.registerGlobalReceiver(RigNet.Status.TYPE, (payload, context) -> status = payload);
		ClientPlayNetworking.registerGlobalReceiver(RigNet.Scan.TYPE, (payload, context) -> RigScanClient.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(RigNet.Takeover.TYPE, (payload, context) -> {
			takeoverTicks = TAKEOVER_SCREEN;
			takeoverThreat = payload.threat();
		});
		ClientTickEvents.END_CLIENT_TICK.register(RigClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("rig_hud"), RigHud::render);
	}

	public static KeyMapping rigKey() {
		return rigKey;
	}

	/** Whether the local player is on their thrusters (hover or full flight): the barrel roll works then. */
	public static boolean flying(LocalPlayer player) {
		return status.hovering() && Rig.fullSet(player) && !player.onGround();
	}

	/** The locked target, if it's loaded here. */
	static @Nullable LivingEntity locked(LocalPlayer player) {
		if (status.lock() < 0) {
			return null;
		}
		Entity entity = player.level().getEntity(status.lock());
		return entity instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	/** How long B has been held toward a scan (0: not). */
	static int scanHeldTicks() {
		return scanHeld;
	}

	/** Whether {@code active} is in the J cycle at this level. */
	static boolean available(int active, int level) {
		return switch (active) {
			case RigNet.SCAN -> level >= Rig.SCAN_LEVEL;
			case RigNet.CALL -> level >= Rig.CALL_LEVEL;
			default -> true;
		};
	}

	private static void send(int action) {
		ClientPlayNetworking.send(new RigNet.Use(action));
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			status = RigNet.Status.NONE;
			java.util.Arrays.fill(powerKeysDown, false);
			takeoverTicks = 0;
			RigScanClient.clear();
			return;
		}
		boolean wearing = Rig.wearsAny(player);
		boolean screenFree = minecraft.gui.screen() == null && !player.isSpectator();
		boolean free = screenFree && wearing;
		boolean powerless = Powers.of(player) == null;
		int level = Rig.level(player);
		// no power: the four power keys are the Rig's
		for (PowerKey key : PowerKey.values()) {
			boolean down = free && powerless && JaymosPowersClient.keyMapping(key).isDown();
			if (down && !powerKeysDown[key.ordinal()]) {
				send(key.ordinal());
			}
			powerKeysDown[key.ordinal()] = down;
		}
		// B held: a scan (the report has already gone on the press)
		if (powerKeysDown[PowerKey.ABILITY_4.ordinal()]) {
			if (++scanHeld == SCAN_HOLD && level >= Rig.SCAN_LEVEL) {
				send(RigNet.SCAN);
			}
		} else {
			scanHeld = 0;
		}
		// C (no power: the ultimate key answers nothing else): park or call the suit
		boolean call = screenFree && powerless && (wearing || RigCall.parked(player)) && JaymosPowersClient.ultimateKey().isDown();
		if (call && !callWasDown) {
			send(RigNet.CALL);
		}
		callWasDown = call;
		rigKeyTick(player, screenFree, wearing, level);
		jumpTick(minecraft, player, level);
		if (!wearing) {
			status = RigNet.Status.NONE;
		}
		if (takeoverTicks > 0) {
			takeoverTicks--;
			int into = TAKEOVER_SCREEN - takeoverTicks;
			if (into <= TAKEOVER_TURN) {
				turnToward(player, takeoverThreat);
			}
		}
		RigScanClient.tick(minecraft, player);
	}

	/** The Rig key: a tap uses the active (Sneak: picks the next), a hold locks on. With nothing worn, it calls a parked suit. */
	private static void rigKeyTick(LocalPlayer player, boolean screenFree, boolean wearing, int level) {
		int clicks = 0;
		while (rigKey.consumeClick()) {
			clicks++;
		}
		boolean down = screenFree && rigKey.isDown();
		if (down && !rigWasDown) {
			rigHeld = 0;
			rigShift = player.isShiftKeyDown();
			rigHeldDone = false;
		}
		if (down) {
			rigHeld++;
			if (rigHeld == LOCK_HOLD && wearing && !rigShift && Rig.wears(player, Rig.Piece.HELM) && level >= Rig.LOCK_LEVEL) {
				LivingEntity target = pick(player);
				ClientPlayNetworking.send(new RigNet.Lock(target == null ? -1 : target.getId()));
				rigHeldDone = true;
			}
		}
		boolean tapped = !down && rigWasDown && !rigHeldDone || clicks > 0 && !down && !rigWasDown && screenFree;
		if (tapped) {
			boolean shift = rigWasDown ? rigShift : player.isShiftKeyDown();
			if (!wearing) {
				if (RigCall.parked(player)) {
					send(RigNet.CALL);
				}
			} else if (shift) {
				int at = 0;
				for (int i = 0; i < RigNet.CYCLE.length; i++) {
					if (RigNet.CYCLE[i] == selected) {
						at = i;
					}
				}
				for (int step = 1; step <= RigNet.CYCLE.length; step++) {
					int next = RigNet.CYCLE[(at + step) % RigNet.CYCLE.length];
					if (available(next, level)) {
						selected = next;
						break;
					}
				}
			} else {
				if (!available(selected, level)) {
					selected = RigNet.PALM;
				}
				send(selected);
			}
		}
		rigWasDown = down;
	}

	/** Double-tap Jump while hovering with the whole set at level 5: full flight on / off. */
	private static void jumpTick(Minecraft minecraft, LocalPlayer player, int level) {
		boolean jump = minecraft.options.keyJump.isDown() && minecraft.gui.screen() == null;
		if (sinceJump < Integer.MAX_VALUE) {
			sinceJump++;
		}
		if (jump && !jumpWasDown && status.hovering() && Rig.fullSet(player) && level >= Rig.FLIGHT_LEVEL) {
			if (sinceJump <= JUMP_TAP_TICKS) {
				send(RigNet.FLIGHT);
				sinceJump = Integer.MAX_VALUE;
			} else {
				sinceJump = 0;
			}
		}
		jumpWasDown = jump;
	}

	/** The living thing nearest the crosshair, in sight, within lock range. */
	private static @Nullable LivingEntity pick(LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestDot = LOCK_CONE;
		for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RigActives.LOCK_RANGE),
			e -> e != player && e.isAlive() && !e.isSpectator() && !e.isInvisible())) {
			Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > RigActives.LOCK_RANGE || distance < 1.0E-3) {
				continue;
			}
			double dot = to.scale(1.0 / distance).dot(look);
			if (dot > bestDot && player.hasLineOfSight(entity)) {
				bestDot = dot;
				best = entity;
			}
		}
		return best;
	}

	/** The takeover turns your view toward the threat. */
	private static void turnToward(LocalPlayer player, Vec3 threat) {
		Vec3 to = threat.add(0.0, 1.0, 0.0).subtract(player.getEyePosition());
		if (to.lengthSqr() < 1.0E-3) {
			return;
		}
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float)(-(Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG));
		float newYaw = player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * TAKEOVER_TURN_SHARE;
		float newPitch = Mth.clamp(player.getXRot() + (pitch - player.getXRot()) * TAKEOVER_TURN_SHARE, -90.0F, 90.0F);
		player.setYRot(newYaw);
		player.setXRot(newPitch);
		player.setYHeadRot(newYaw);
	}

	/**
	 * Hover and full flight, from {@code JaymosPowersClient}'s tick, before the barrel roll (which slides on top of this
	 * tick's velocity).
	 */
	public static void motion(Minecraft minecraft, LocalPlayer player) {
		boolean handsOff = takeoverTicks > TAKEOVER_SCREEN - TAKEOVER_HANDS_OFF;
		if (!status.hovering() || !Rig.fullSet(player) || minecraft.isPaused() || handsOff) {
			hoverTicks = 0;
			flightVelocity = player.getDeltaMovement();
			return;
		}
		if (status.flying()) {
			flight(minecraft, player);
		} else {
			hover(minecraft, player);
			flightVelocity = player.getDeltaMovement();
		}
	}

	private static void hover(Minecraft minecraft, LocalPlayer player) {
		hoverTicks++;
		Vec3 motion = player.getDeltaMovement();
		double vy;
		if (minecraft.options.keyJump.isDown()) {
			vy = HOVER_RISE;
		} else if (minecraft.options.keyShift.isDown()) {
			vy = -HOVER_SINK;
		} else if (hoverTicks < 4 && player.onGround()) {
			// lift off
			vy = HOVER_RISE * 1.5;
		} else {
			vy = Math.sin(hoverTicks * 0.15) * HOVER_BOB;
		}
		double vx = motion.x, vz = motion.z;
		if (minecraft.options.keyUp.isDown()) {
			double yaw = Math.toRadians(player.getYRot());
			vx += -Math.sin(yaw) * HOVER_PUSH;
			vz += Math.cos(yaw) * HOVER_PUSH;
			double speed = Math.sqrt(vx * vx + vz * vz);
			if (speed > HOVER_MAX) {
				vx *= HOVER_MAX / speed;
				vz *= HOVER_MAX / speed;
			}
		}
		player.setDeltaMovement(vx, vy, vz);
		player.resetFallDistance();
	}

	/** Full flight: where you look, with some weight to it; let go and it slows to a hold. */
	private static void flight(Minecraft minecraft, LocalPlayer player) {
		hoverTicks++;
		Vec3 look = player.getLookAngle();
		Vec3 want = Vec3.ZERO;
		if (minecraft.options.keyUp.isDown()) {
			want = look.scale(FLIGHT_SPEED * (minecraft.options.keySprint.isDown() ? FLIGHT_SPRINT : 1.0));
		} else if (minecraft.options.keyDown.isDown()) {
			want = look.scale(-FLIGHT_SPEED * 0.35);
		}
		double yaw = Math.toRadians(player.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
		if (minecraft.options.keyRight.isDown()) {
			want = want.add(right.scale(FLIGHT_STRAFE));
		}
		if (minecraft.options.keyLeft.isDown()) {
			want = want.add(right.scale(-FLIGHT_STRAFE));
		}
		if (minecraft.options.keyJump.isDown()) {
			want = want.add(0.0, FLIGHT_CLIMB, 0.0);
		} else if (minecraft.options.keyShift.isDown()) {
			want = want.add(0.0, -FLIGHT_CLIMB, 0.0);
		} else if (want.lengthSqr() < 1.0E-4) {
			want = new Vec3(0.0, Math.sin(hoverTicks * 0.15) * HOVER_BOB, 0.0);
		}
		// walls and floors stop you (the server hurts a hard stop)
		Vec3 actual = player.getDeltaMovement();
		if (player.horizontalCollision) {
			flightVelocity = new Vec3(actual.x, flightVelocity.y, actual.z);
		}
		if (player.verticalCollision) {
			flightVelocity = new Vec3(flightVelocity.x, 0.0, flightVelocity.z);
		}
		flightVelocity = flightVelocity.lerp(want, FLIGHT_ACCEL);
		player.setDeltaMovement(flightVelocity);
		player.resetFallDistance();
	}
}
