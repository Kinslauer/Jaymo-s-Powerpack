package com.jaymospowers.client;

import com.jaymospowers.core.Ledge;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The local player's ledge grab and climb (see {@link Ledge} for the rules and the numbers). Holding forward and Jump
 * while rising at a wall catches its edge once the eyes come level with it; the body then hangs still (the vanilla
 * travel is skipped, {@code LedgePlayerMixin}). A fresh press of Jump pulls up and over along a fixed path (straight up
 * beside the wall, then across onto the top); Sneak, Back, looking away, the grip giving out or the server refusing
 * lets go. The moves go through {@code move()}, so the server sees ordinary, collision-free movement.
 */
public final class LedgeClient {
	private enum Phase { NONE, HANG, CLIMB }

	private static Phase phase = Phase.NONE;
	private static Ledge.@Nullable Spot spot;
	/** Where the edge was caught (the hang settles from here). */
	private static Vec3 caughtAt = Vec3.ZERO;
	private static int ticks;
	private static int cooldown;
	/** The server has the hold (its synced copy arrived). */
	private static boolean confirmed;
	/** Jump was let go since the grab: the next press climbs. */
	private static boolean jumpReleased;

	private LedgeClient() {
	}

	static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(LedgeClient::swallowClicks);
		ClientTickEvents.END_CLIENT_TICK.register(LedgeClient::tick);
	}

	/** The local player is on a ledge (hanging or climbing). */
	public static boolean active() {
		return phase != Phase.NONE;
	}

	/** How far through the climb the local player is (0-1) at {@code partialTick}, or -1 while hanging (or not on a ledge). */
	public static float localProgress(float partialTick) {
		return phase == Phase.CLIMB ? Mth.clamp((ticks + partialTick) / Ledge.CLIMB_TICKS, 0.0F, 1.0F) : -1.0F;
	}

	/** Both hands are on the edge: no swings, no item use (vanilla's and Better Combat's both start from these clicks). */
	private static void swallowClicks(Minecraft minecraft) {
		if (phase == Phase.NONE) {
			return;
		}
		while (minecraft.options.keyAttack.consumeClick()) {
			// (dropped)
		}
		while (minecraft.options.keyUse.consumeClick()) {
			// (dropped)
		}
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			reset();
			return;
		}
		if (cooldown > 0) {
			cooldown--;
		}
		Input input = player.input.keyPresses;
		switch (phase) {
			case NONE -> tryGrab(player, input);
			case HANG -> tickHang(player, input);
			case CLIMB -> tickClimb(player);
		}
	}

	private static void tryGrab(LocalPlayer player, Input input) {
		if (cooldown > 0 || !input.forward() || !input.jump() || input.shift() || input.backward() || player.onGround()
			|| player.getDeltaMovement().y < Ledge.GRAB_MIN_VY || !Ledge.canHang(player)) {
			return;
		}
		Ledge.Spot found = Ledge.find(player);
		if (found == null) {
			return;
		}
		spot = found;
		phase = Phase.HANG;
		ticks = 0;
		confirmed = false;
		jumpReleased = false;
		caughtAt = player.position();
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		Ledge.localId = player.getId();
		Ledge.localHold = new Ledge.Hold(found.face().get2DDataValue(), player.level().getGameTime(), false);
		ClientPlayNetworking.send(new Ledge.Grab(found.pos(), found.face().get2DDataValue()));
	}

	private static void tickHang(LocalPlayer player, Input input) {
		Ledge.Spot at = spot;
		ticks++;
		boolean held = player.hasAttached(Ledge.HOLD);
		confirmed |= held;
		jumpReleased |= !input.jump();
		float away = Math.abs(Mth.wrapDegrees(player.getYRot() - (at == null ? 0.0F : at.face().toYRot())));
		if (at == null || !Ledge.canHang(player) || !Ledge.holdable(player.level(), at.pos()) || input.shift() || input.backward()
			|| away > Ledge.LOOK_AWAY_DEGREES || ticks > Ledge.HANG_MAX_TICKS || confirmed && !held || !confirmed && ticks > Ledge.CONFIRM_TICKS) {
			letGo(player);
			return;
		}
		if (input.jump() && jumpReleased && ticks >= Ledge.GRAB_SETTLE_TICKS) {
			phase = Phase.CLIMB;
			ticks = 0;
			Ledge.localHold = new Ledge.Hold(at.face().get2DDataValue(), player.level().getGameTime(), true);
			ClientPlayNetworking.send(new Ledge.Act(true));
		}
	}

	private static void tickClimb(LocalPlayer player) {
		ticks++;
		if (!player.isAlive() || player.isPassenger() || com.jaymospowers.core.Carry.isCarried(player)
			|| com.jaymospowers.core.Carry.isThrown(player)) {
			letGo(player);
			return;
		}
		if (ticks >= Ledge.CLIMB_TICKS) {
			// standing on top
			end();
			cooldown = Ledge.REGRAB_COOLDOWN_TICKS;
			if (ClientPlayNetworking.canSend(Ledge.Act.TYPE)) {
				ClientPlayNetworking.send(new Ledge.Act(false));
			}
		}
	}

	/**
	 * In place of the player's travel while on a ledge (the hold's own movement, client-authoritative): settle into the
	 * hang and stay there, or follow the climb's path.
	 */
	public static void travel(LocalPlayer player) {
		Ledge.Spot at = spot;
		if (at == null) {
			return;
		}
		Vec3 target;
		if (phase == Phase.HANG) {
			float t = Math.min(1.0F, (ticks + 1.0F) / Ledge.GRAB_SETTLE_TICKS);
			target = caughtAt.lerp(at.hang(), smooth(t));
		} else {
			float p = Math.min(1.0F, (ticks + 1.0F) / Ledge.CLIMB_TICKS);
			target = climbPath(at, p);
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.move(MoverType.SELF, target.subtract(player.position()));
		if (phase == Phase.CLIMB && ticks + 1 >= Ledge.CLIMB_TICKS) {
			// set down on the top
			player.move(MoverType.SELF, new Vec3(0.0, -0.05, 0.0));
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
	}

	/** The climb: straight up beside the wall until the feet clear the edge, then across onto the top. */
	private static Vec3 climbPath(Ledge.Spot at, float p) {
		Vec3 hang = at.hang();
		Vec3 top = at.onTop();
		float rise = Ledge.CLIMB_RISE_SHARE;
		if (p < rise) {
			double y = Mth.lerp(smooth(p / rise), hang.y, top.y);
			return new Vec3(hang.x, y, hang.z);
		}
		float q = smooth((p - rise) / (1.0F - rise));
		return new Vec3(Mth.lerp(q, hang.x, top.x), top.y, Mth.lerp(q, hang.z, top.z));
	}

	private static float smooth(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	private static void letGo(LocalPlayer player) {
		Ledge.Spot at = spot;
		end();
		cooldown = Ledge.REGRAB_COOLDOWN_TICKS;
		if (at != null) {
			Direction face = at.face();
			if (com.jaymospowers.core.Carry.isThrown(player)) {
				// knocked off by a hit: thrown clear of the wall, limp
				player.setDeltaMovement(-face.getStepX() * Ledge.KNOCKED_PUSH, Ledge.KNOCKED_LIFT, -face.getStepZ() * Ledge.KNOCKED_PUSH);
			} else {
				player.setDeltaMovement(-face.getStepX() * Ledge.LET_GO_PUSH, 0.0, -face.getStepZ() * Ledge.LET_GO_PUSH);
			}
		}
		if (ClientPlayNetworking.canSend(Ledge.Act.TYPE)) {
			ClientPlayNetworking.send(new Ledge.Act(false));
		}
	}

	private static void end() {
		phase = Phase.NONE;
		spot = null;
		ticks = 0;
		Ledge.localHold = null;
		Ledge.localId = -1;
	}

	private static void reset() {
		end();
		cooldown = 0;
		confirmed = false;
	}
}
