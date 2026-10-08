package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebAnchors;
import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebPower;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The local player's web movement: swinging on up to two webs at once (one from each hand, each on its own key and
 * rope, as a rope constraint on top of vanilla physics), keeping momentum after a release, and being reeled in
 * (Zip / Yank-to). Runs at the end of each client tick, after vanilla has moved the player, and sets the velocity
 * they'll move with next tick. Player motion is client-authoritative, so this is the real movement; the server only
 * checks the anchors and waives fall damage and the flying kick.
 */
final class WebSwing {
	// ===== swing feel: the knobs to tweak after playtesting =========================================================
	/** The rope starts at this fraction of the distance to the anchor (< 1 = a small tug the moment it sticks). */
	static final double ATTACH_SLACK = 0.94;
	/** Shortest the rope can be reeled to. */
	static final double ROPE_MIN = 3.0;
	/** Pull-back speed per block the rope is over-stretched (rope elasticity), and the most it pulls back per tick. */
	static final double ROPE_SPRING = 0.3;
	static final double ROPE_MAX_PULL = 0.7;
	/** Extra gravity while swinging (vanilla is 0.08/tick): heavier, snappier arcs that build speed at the bottom. */
	static final double SWING_GRAVITY = 0.025;
	/** Velocity kept per tick while on the web (replaces vanilla's 0.91 horizontal / 0.98 vertical air drag). */
	static final double SWING_DRAG = 0.993;
	/** Holding forward on the downswing pumps this much extra speed along the swing, per tick. */
	static final double PUMP = 0.03;
	/** Jump reels in, sneak lets out (blocks per tick), every web that's out. */
	static final double REEL_IN = 0.45;
	static final double LET_OUT = 0.35;
	/** Speed cap while swinging or flying from a swing (blocks/tick; sprinting is ~0.28). */
	static final double MAX_SPEED = 2.6;
	/** Release timing: letting go while travelling this many degrees above level is perfect; the window either side. */
	static final double RELEASE_SWEET_ANGLE = 35.0;
	static final double RELEASE_WINDOW = 35.0;
	/** The boost a perfect release gives (scaled down linearly to 0 at the window's edges). */
	static final double RELEASE_FORWARD = 0.35;
	static final double RELEASE_UP = 0.5;
	/** Horizontal velocity kept per tick in the air after a release, until you land (vanilla air is 0.91). */
	static final double FLIGHT_DRAG = 0.985;
	/** Holding a swing key in the air with nothing to stick to retries every this many ticks. */
	static final int RETRY_TICKS = 3;
	// ===== zip / yank-to ===========================================================================================
	/** How quickly the reel-in steers your velocity onto the line (0..1 per tick). */
	static final double ZIP_STEER = 0.55;
	/** At the end of a zip onto a wall: forward push onto the ledge. */
	static final double PERCH_FORWARD = 0.22;
	// ===============================================================================================================

	/** Vanilla's air drag, which the swing and flight undo before applying their own. */
	private static final double VANILLA_AIR_DRAG = 0.91;
	private static final double VANILLA_AIR_DRAG_Y = 0.98;
	/** The rope pulls on the body at about hand height. */
	private static final double ROPE_HEIGHT = 1.2;

	/** One hand's swing web ({@link WebNet#MAIN} or {@link WebNet#OFF}). */
	static final class Strand {
		final int hand;
		boolean attached;
		Vec3 anchor = Vec3.ZERO;
		double rope;
		int swingTicks;
		boolean keyHeld;
		int retryIn;

		Strand(int hand) {
			this.hand = hand;
		}
	}

	final Strand[] strands = {new Strand(WebNet.MAIN), new Strand(WebNet.OFF)};
	// after a release
	boolean flying;
	// zip / yank-to
	WebNet.@Nullable Pull pull;
	private int pullTicks;
	private int stuckTicks;
	private Vec3 lastPos = Vec3.ZERO;

	void reset() {
		for (Strand strand : strands) {
			strand.attached = false;
			strand.keyHeld = false;
		}
		flying = false;
		pull = null;
	}

	/** Any web stuck right now. */
	boolean attached() {
		return strands[WebNet.MAIN].attached || strands[WebNet.OFF].attached;
	}

	// ---- input ----------------------------------------------------------------------------------------------------

	void press(LocalPlayer player, int hand) {
		Strand strand = strands[hand];
		strand.keyHeld = true;
		if (pull != null) {
			return;
		}
		if (!tryAttach(player, strand)) {
			play(player, SoundEvents.FISHING_BOBBER_THROW, 0.4F, 2.0F);
			strand.retryIn = RETRY_TICKS;
		}
	}

	void releaseKey(LocalPlayer player, int hand) {
		Strand strand = strands[hand];
		strand.keyHeld = false;
		if (strand.attached) {
			detach(player, strand, true);
		}
	}

	private boolean tryAttach(LocalPlayer player, Strand strand) {
		if (player.getAbilities().flying || player.isInWater() || player.isInLava()) {
			return false;
		}
		if (strand.hand == WebNet.OFF && !WebPower.offHandFree(player)) {
			return false;
		}
		WebAnchors.Anchor found = WebAnchors.find(player);
		if (found == null) {
			return false;
		}
		strand.attached = true;
		flying = false;
		strand.anchor = found.pos();
		strand.swingTicks = 0;
		double distance = strand.anchor.distanceTo(player.position().add(0.0, ROPE_HEIGHT, 0.0));
		strand.rope = Math.max(ROPE_MIN, distance * ATTACH_SLACK);
		ClientPlayNetworking.send(new WebNet.Swing(strand.hand, true, strand.anchor));
		play(player, SoundEvents.FISHING_BOBBER_THROW, 0.9F, 1.7F + player.getRandom().nextFloat() * 0.2F);
		puff(player, strand.anchor, 5);
		return true;
	}

	/** Let go of one web. {@code timed}: a deliberate release, which may earn the upswing boost (if it was the last web). */
	void detach(LocalPlayer player, Strand strand, boolean timed) {
		if (!strand.attached) {
			return;
		}
		strand.attached = false;
		ClientPlayNetworking.send(new WebNet.Swing(strand.hand, false, Vec3.ZERO));
		if (attached()) {
			// still hanging from the other hand: no release yet
			return;
		}
		flying = !player.onGround();
		if (!timed || player.onGround()) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		double speed = v.length();
		if (speed < 0.4 || v.y <= 0.0) {
			return;
		}
		double angle = Math.toDegrees(Math.asin(v.y / speed));
		double quality = 1.0 - Math.abs(angle - RELEASE_SWEET_ANGLE) / RELEASE_WINDOW;
		if (quality <= 0.0) {
			return;
		}
		Vec3 forward = v.horizontal().normalize();
		player.setDeltaMovement(v.add(forward.scale(RELEASE_FORWARD * quality)).add(0.0, RELEASE_UP * quality, 0.0));
		if (quality > 0.7) {
			play(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.6F);
			Vec3 at = player.position().add(0.0, 1.0, 0.0);
			for (int i = 0; i < 6; i++) {
				player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, -v.x * 0.1 + rnd(player) * 0.1, -0.05, -v.z * 0.1 + rnd(player) * 0.1);
			}
		} else {
			play(player, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.5F, 1.4F);
		}
	}

	private void detachAll(LocalPlayer player, boolean timed) {
		for (Strand strand : strands) {
			detach(player, strand, timed);
		}
	}

	/** The server refused that hand's web, or its anchor broke. */
	void rejected(LocalPlayer player, int hand) {
		if (hand < 0 || hand >= strands.length) {
			return;
		}
		Strand strand = strands[hand];
		if (strand.attached) {
			strand.attached = false;
			if (!attached()) {
				flying = !player.onGround();
			}
			play(player, SoundEvents.LEAD_BREAK, 0.6F, 1.6F);
		}
	}

	void startPull(LocalPlayer player, WebNet.Pull pull) {
		for (Strand strand : strands) {
			if (strand.attached) {
				strand.attached = false;
				ClientPlayNetworking.send(new WebNet.Swing(strand.hand, false, Vec3.ZERO));
			}
		}
		flying = false;
		this.pull = pull;
		pullTicks = 0;
		stuckTicks = 0;
		lastPos = player.position();
	}

	// ---- per tick -------------------------------------------------------------------------------------------------

	void tick(Minecraft minecraft, LocalPlayer player) {
		if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying || player.isPassenger()) {
			detachAll(player, false);
			flying = false;
			pull = null;
			return;
		}
		if (pull != null) {
			tickPull(player);
			return;
		}
		Strand off = strands[WebNet.OFF];
		if (off.attached && !WebPower.offHandFree(player)) {
			// something was put in the off hand: that web lets go
			detach(player, off, false);
		}
		if (attached()) {
			tickSwing(player);
		} else if (flying) {
			tickFlight(player);
		}
		for (Strand strand : strands) {
			if (!strand.attached && strand.keyHeld && !player.onGround() && --strand.retryIn <= 0) {
				strand.retryIn = RETRY_TICKS;
				tryAttach(player, strand);
			}
		}
	}

	private void tickSwing(LocalPlayer player) {
		if (player.isInWater() || player.isInLava()) {
			detachAll(player, false);
			return;
		}
		for (Strand strand : strands) {
			if (strand.attached && ++strand.swingTicks % 5 == 0 && !WebAnchors.nearSolid(player.level(), strand.anchor)) {
				strand.attached = false;
				ClientPlayNetworking.send(new WebNet.Swing(strand.hand, false, Vec3.ZERO));
				play(player, SoundEvents.LEAD_BREAK, 0.6F, 1.6F);
			}
		}
		if (!attached()) {
			flying = !player.onGround();
			return;
		}
		Input input = player.input.keyPresses;
		boolean air = !player.onGround();
		Vec3 v = player.getDeltaMovement();
		if (air && !player.isFallFlying()) {
			v = new Vec3(v.x / VANILLA_AIR_DRAG, v.y / VANILLA_AIR_DRAG_Y, v.z / VANILLA_AIR_DRAG).scale(SWING_DRAG).add(0.0, -SWING_GRAVITY, 0.0);
		}
		Vec3 body = player.position().add(0.0, ROPE_HEIGHT, 0.0);
		Vec3 pullDir = Vec3.ZERO;
		for (Strand strand : strands) {
			if (!strand.attached) {
				continue;
			}
			if (input.jump()) {
				strand.rope = Math.max(ROPE_MIN, strand.rope - REEL_IN);
			} else if (input.shift()) {
				strand.rope = Math.min(WebAnchors.SWING_RANGE, strand.rope + LET_OUT);
			}
			Vec3 to = strand.anchor.subtract(body);
			if (to.lengthSqr() > 1.0E-8) {
				pullDir = pullDir.add(to.normalize());
			}
		}
		// pump: forward on the downswing adds speed along the arc (across the webs' combined pull)
		if (air && input.forward() && v.y < 0.0 && pullDir.lengthSqr() > 1.0E-6) {
			Vec3 dir = pullDir.normalize();
			Vec3 tangent = v.subtract(dir.scale(v.dot(dir)));
			if (tangent.lengthSqr() > 1.0E-4) {
				v = v.add(tangent.normalize().scale(PUMP));
			}
		}
		// each rope: no moving further away than its length, and a springy pull back when stretched. Two passes, so
		// two webs settle against each other instead of fighting
		for (int pass = 0; pass < 2; pass++) {
			for (Strand strand : strands) {
				if (!strand.attached) {
					continue;
				}
				Vec3 toAnchor = strand.anchor.subtract(body);
				double distance = toAnchor.length();
				if (distance < 1.0E-4 || distance <= strand.rope) {
					continue;
				}
				Vec3 dir = toAnchor.scale(1.0 / distance);
				double inward = v.dot(dir);
				double want = Math.min((distance - strand.rope) * ROPE_SPRING, ROPE_MAX_PULL);
				if (inward < want) {
					v = v.add(dir.scale(want - inward));
				}
			}
		}
		player.setDeltaMovement(cap(v));
		player.resetFallDistance();
		int ticks = Math.max(strands[0].swingTicks, strands[1].swingTicks);
		if (air && v.lengthSqr() > 1.2 * 1.2 && ticks % 12 == 0) {
			play(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.25F, 0.6F + (float)Math.min(0.6, v.length() * 0.2));
		}
	}

	private void tickFlight(LocalPlayer player) {
		if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying() || player.onClimbable()) {
			flying = false;
			return;
		}
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(cap(new Vec3(v.x / VANILLA_AIR_DRAG * FLIGHT_DRAG, v.y, v.z / VANILLA_AIR_DRAG * FLIGHT_DRAG)));
	}

	private void tickPull(LocalPlayer player) {
		WebNet.Pull pull = this.pull;
		pullTicks++;
		Vec3 dest = pull.target();
		double arrive = 0.9;
		if (pull.entityId() >= 0) {
			Entity entity = player.level().getEntity(pull.entityId());
			if (entity == null || !entity.isAlive()) {
				finishPull(player, pull);
				return;
			}
			dest = entity.position();
			arrive = entity.getBbWidth() * 0.5 + 1.2;
		}
		Vec3 to = dest.subtract(player.position());
		double distance = to.length();
		if (player.position().distanceToSqr(lastPos) < 0.05 * 0.05 && pullTicks > 3) {
			stuckTicks++;
		} else {
			stuckTicks = 0;
		}
		lastPos = player.position();
		if (distance < arrive || pullTicks > pull.maxTicks() || stuckTicks >= 3 || player.isInWater()) {
			finishPull(player, pull);
			return;
		}
		double speed = Math.min(WebPower.ZIP_SPEED, 0.35 + distance * 0.45);
		Vec3 v = player.getDeltaMovement().lerp(to.scale(speed / distance), ZIP_STEER);
		player.setDeltaMovement(v);
		player.resetFallDistance();
	}

	private void finishPull(LocalPlayer player, WebNet.Pull pull) {
		this.pull = null;
		Direction face = pull.face() >= 0 && pull.face() < Direction.values().length ? Direction.values()[pull.face()] : null;
		Vec3 v = player.getDeltaMovement();
		if (face != null && face.getAxis().isHorizontal()) {
			// perch: hop up and onto the ledge above the hit point, if there's one within reach
			BlockPos wall = BlockPos.containing(pull.anchor().subtract(face.getUnitVec3().scale(0.05)));
			double hop = 0.42;
			for (int k = 1; k <= 3; k++) {
				BlockPos top = wall.above(k);
				if (free(player, top) && free(player, top.above())) {
					double height = top.getY() - player.getY() + 0.2;
					hop = Math.max(0.42, Math.min(0.95, Math.sqrt(2.0 * 0.08 * Math.max(0.0, height)) + 0.08));
					break;
				}
			}
			Vec3 into = face.getUnitVec3().scale(-PERCH_FORWARD);
			player.setDeltaMovement(into.x, hop, into.z);
		} else if (face == Direction.UP) {
			player.setDeltaMovement(v.x * 0.3, 0.25, v.z * 0.3);
		} else {
			player.setDeltaMovement(v.scale(0.4).add(0.0, 0.2, 0.0));
		}
		play(player, SoundEvents.SLIME_SQUISH_SMALL, 0.5F, 1.5F);
	}

	private static boolean free(LocalPlayer player, BlockPos pos) {
		return player.level().getBlockState(pos).getCollisionShape(player.level(), pos).isEmpty();
	}

	private static Vec3 cap(Vec3 v) {
		double speed = v.length();
		return speed > MAX_SPEED ? v.scale(MAX_SPEED / speed) : v;
	}

	private static double rnd(LocalPlayer player) {
		return player.getRandom().nextDouble() - 0.5;
	}

	static void play(LocalPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
	}

	private static void puff(LocalPlayer player, Vec3 at, int count) {
		for (int i = 0; i < count; i++) {
			player.level().addParticle(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, rnd(player) * 0.05, rnd(player) * 0.05, rnd(player) * 0.05);
		}
	}

	/** Where the local player's own web from {@code hand} currently ends, or null. Zips and yanks come from the main hand. */
	@Nullable Vec3 lineEnd(int hand, float partialTick) {
		if (strands[hand].attached) {
			return strands[hand].anchor;
		}
		WebNet.Pull pull = this.pull;
		if (pull != null && hand == WebNet.MAIN) {
			if (pull.entityId() >= 0) {
				Minecraft minecraft = Minecraft.getInstance();
				Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(pull.entityId());
				if (entity != null) {
					return entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.5, 0.0);
				}
			}
			return pull.anchor();
		}
		return null;
	}
}
