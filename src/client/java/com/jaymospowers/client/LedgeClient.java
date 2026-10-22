package com.jaymospowers.client;

import com.jaymospowers.core.Crawl;
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
 *
 * <p>(0.15.108) Forward alone (no Jump held) is enough to catch an edge. Going prone while hanging ({@link #proneClimb},
 * from {@code CrawlTaps}), or Jump under a gap too low to stand in, pulls you up and in onto your belly: the crawl begins
 * as the climb does (the body drops to 0.6 high, lifted so the eyes stay put), rises to the top and slides in, and you
 * stay prone after it.
 */
public final class LedgeClient {
	private enum Phase { NONE, HANG, CLIMB }

	private static Phase phase = Phase.NONE;
	private static Ledge.@Nullable Spot spot;
	/** Where the edge was caught (the hang settles from here). */
	private static Vec3 caughtAt = Vec3.ZERO;
	private static int ticks;
	/** (0.15.108) The climb under way is the prone one, onto the belly into a low gap. */
	private static boolean prone;
	private static int cooldown;
	/** The server has the hold (its synced copy arrived). */
	private static boolean confirmed;
	/** Jump was let go since the grab: the next press climbs. */
	private static boolean jumpReleased;
	/** (0.14.3) Caught at the top of a freerun climb ({@link #catchFromClimb}): holding forward pulls up and over, no Jump needed. */
	private static boolean autoClimb;
	/** (0.15.12) Caught by sneaking off an edge: the body goes out from the edge, then down. Shift was down for it: it lets go only once released and pressed again. */
	private static boolean edge, shiftLatch;
	/** (0.15.12) Sneak + Back from a hang: a drop that looks for a ledge this far (blocks) under the one let go of, for this many ticks. */
	private static final int DROP_TICKS = 10;
	private static final double DROP_MAX = 3.2;
	private static int dropLeft;
	private static double dropTop;

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
		return phase == Phase.CLIMB ? Mth.clamp((ticks + partialTick) / climbTicks(), 0.0F, 1.0F) : -1.0F;
	}

	/** (0.15.108) The local player is hanging (caught and not yet climbing). */
	static boolean hanging() {
		return phase == Phase.HANG;
	}

	private static int climbTicks() {
		return prone ? Ledge.PRONE_CLIMB_TICKS : Ledge.CLIMB_TICKS;
	}

	/**
	 * (0.15.108) Going prone while hanging: pull up and in onto the belly when the gap above the edge has room to lie in.
	 * Returns whether the climb began (the crawl starts with it).
	 */
	static boolean proneClimb(LocalPlayer player) {
		Ledge.Spot at = spot;
		if (phase != Phase.HANG || at == null || at.onBelly() == null || !confirmed || !Crawl.canCrawl(player)) {
			return false;
		}
		startClimb(player, at, true);
		return true;
	}

	private static void startClimb(LocalPlayer player, Ledge.Spot at, boolean flat) {
		phase = Phase.CLIMB;
		prone = flat;
		ticks = 0;
		Ledge.localHold = new Ledge.Hold(at.face().get2DDataValue(), player.level().getGameTime(), true, flat);
		ClientPlayNetworking.send(new Ledge.Act(true, flat));
		if (flat) {
			CrawlTaps.goProne(player, false);
		}
	}

	/** Both hands are on the edge: no swings, no item use (vanilla's and Better Combat's both start from these clicks). */
	private static void swallowClicks(Minecraft minecraft) {
		// (0.14.3) and while both hands hold a wall or a bar (freerunning)
		if (phase == Phase.NONE && !com.jaymospowers.client.freerun.FreerunClient.holding()) {
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
		if (dropLeft > 0) {
			tickDrop(player);
		}
		switch (phase) {
		case NONE -> tryGrab(player, input);
			case HANG -> tickHang(player, input);
			case CLIMB -> tickClimb(player);
		}
	}

	private static void tryGrab(LocalPlayer player, Input input) {
		if (cooldown > 0 || !input.forward() || input.shift() || input.backward() || player.onGround()
			|| player.getDeltaMovement().y < Ledge.GRAB_MIN_VY || !Ledge.canHang(player) || com.jaymospowers.client.freerun.FreerunClient.active()) {
			return;
		}
		Ledge.Spot found = Ledge.find(player);
		if (found == null) {
			return;
		}
		grab(player, found);
	}

	/**
	 * (0.15.12) Falling after a drop from a hang (Sneak + Back): the first ledge 1 to 3 blocks under the one let go of, in
	 * reach as the body passes it, is caught.
	 */
	private static void tickDrop(LocalPlayer player) {
		dropLeft--;
		if (phase != Phase.NONE || player.onGround() || player.isInWater() || !Ledge.canHang(player)) {
			dropLeft = 0;
			return;
		}
		Ledge.Spot found = Ledge.find(player);
		if (found != null && found.top() <= dropTop - 0.9 && found.top() >= dropTop - DROP_MAX) {
			dropLeft = 0;
			grab(player, found);
		}
	}

	/**
	 * (0.15.12) Sneaking off an edge with Climber (see {@code FreerunClient}): hang from the edge just left, below it, turned
	 * round to face its wall. {@code found} is {@link Ledge#edgeSpot}.
	 */
	public static void catchEdge(LocalPlayer player, Ledge.Spot found) {
		if (phase != Phase.NONE || !Ledge.canHang(player)) {
			return;
		}
		grab(player, found);
		edge = true;
		shiftLatch = true;
		player.setYRot(found.face().toYRot());
		player.yRotO = player.getYRot();
		player.setYHeadRot(player.getYRot());
		player.yHeadRotO = player.getYRot();
	}

	/**
	 * (0.14.3) A freerunner's climb (Climber, {@code FreerunClient}) reached the top of its wall: the edge is caught
	 * there, and (forward still held) they pull up and over once the hang settles. The cling has already ended.
	 */
	public static void catchFromClimb(LocalPlayer player, Ledge.Spot found) {
		if (phase != Phase.NONE || !Ledge.canHang(player)) {
			return;
		}
		grab(player, found);
		autoClimb = true;
	}

	private static void grab(LocalPlayer player, Ledge.Spot found) {
		autoClimb = false;
		edge = false;
		shiftLatch = false;
		prone = false;
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
		if (!input.shift()) {
			shiftLatch = false;
		}
		if (at != null && input.shift() && input.backward() && !shiftLatch) {
			// (0.15.12) Sneak + Back: drop, and catch a ledge 1 to 3 blocks lower if there is one
			double top = at.top();
			letGo(player);
			player.setDeltaMovement(0.0, 0.0, 0.0);
			cooldown = 0;
			dropLeft = DROP_TICKS;
			dropTop = top;
			return;
		}
		float away = Math.abs(Mth.wrapDegrees(player.getYRot() - (at == null ? 0.0F : at.face().toYRot())));
		if (at == null || !Ledge.canHang(player)	|| !Ledge.holdable(player.level(), at.pos()) || input.shift() && !shiftLatch || input.backward()
			|| away > Ledge.LOOK_AWAY_DEGREES || ticks > Ledge.HANG_MAX_TICKS || confirmed && !held || !confirmed && ticks > Ledge.CONFIRM_TICKS) {
			letGo(player);
			return;
		}
		if ((input.jump() && jumpReleased || autoClimb && confirmed && input.forward()) && ticks >= Ledge.GRAB_SETTLE_TICKS) {
			if (at.stand()) {
				startClimb(player, at, false);
			} else if (confirmed && Crawl.canCrawl(player)) {
				// (0.15.108) no room to stand up there: in on the belly
				startClimb(player, at, true);
			}
		}
	}

	private static void tickClimb(LocalPlayer player) {
		ticks++;
		if (!player.isAlive() || player.isPassenger() || com.jaymospowers.core.Carry.isCarried(player)
			|| com.jaymospowers.core.Carry.isThrown(player)) {
			letGo(player);
			return;
		}
		if (ticks >= climbTicks()) {
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
			if (edge) {
				// out from the edge first, then down it
				Vec3 hang = at.hang();
				target = t < 0.5F ? new Vec3(Mth.lerp(smooth(t * 2.0F), caughtAt.x, hang.x), caughtAt.y, Mth.lerp(smooth(t * 2.0F), caughtAt.z, hang.z))
					: new Vec3(hang.x, Mth.lerp(smooth(t * 2.0F - 1.0F), caughtAt.y, hang.y), hang.z);
			} else {
				target = caughtAt.lerp(at.hang(), smooth(t));
			}
		} else if (prone) {
			// (the first tick still has the standing body: it only moves once the crawl has made it 0.6 high)
			target = ticks < 1 || at.onBelly() == null ? at.hang() : pronePath(at, Math.min(1.0F, (ticks + 1.0F) / Ledge.PRONE_CLIMB_TICKS));
		} else {
			float p = Math.min(1.0F, (ticks + 1.0F) / Ledge.CLIMB_TICKS);
			target = climbPath(at, p);
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.move(MoverType.SELF, target.subtract(player.position()));
		if (phase == Phase.CLIMB && ticks + 1 >= climbTicks()) {
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

	/**
	 * (0.15.108) The prone climb: the flat body (eyes where the hanging ones were) rises beside the wall to the top, then
	 * slides in across it onto the belly.
	 */
	private static Vec3 pronePath(Ledge.Spot at, float p) {
		Vec3 hang = at.hang();
		Vec3 belly = at.onBelly();
		double from = Math.min(hang.y + Ledge.PRONE_EYE_KEEP, belly.y);
		float rise = Ledge.PRONE_RISE_SHARE;
		if (p < rise) {
			return new Vec3(hang.x, Mth.lerp(smooth(p / rise), from, belly.y), hang.z);
		}
		float q = smooth((p - rise) / (1.0F - rise));
		return new Vec3(Mth.lerp(q, hang.x, belly.x), belly.y, Mth.lerp(q, hang.z, belly.z));
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
		edge = false;
		prone = false;
		autoClimb = false;
		spot = null;
		ticks = 0;
		Ledge.localHold = null;
		Ledge.localId = -1;
	}

	private static void reset() {
	end();
	dropLeft = 0;
		cooldown = 0;
		confirmed = false;
	}
}
