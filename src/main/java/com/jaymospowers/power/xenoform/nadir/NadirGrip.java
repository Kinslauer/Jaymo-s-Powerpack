package com.jaymospowers.power.xenoform.nadir;

import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.web.WebNet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Nadir's Grip (sprint + V, held, at a creature), the telekinesis of the Hand of God mod (Andy, 2026-11-25: "the rest
 * seem great for nadir"; ideas only, it is All Rights Reserved). The void closes round the creature in a ball of
 * violet and lifts it: it hangs before you, {@link #HOLD_MIN} to {@link #HOLD_MAX} blocks out, and goes where you look.
 * Let go of V and it is thrown the way you swing your view ({@link #THROW_SPEED}); let go with the view held still and
 * it just drops. While you hold it: G crushes it in on itself ({@link #IMPLODE}), B tears it apart ({@link #TEAR}, its
 * arms and head torn off if it is killed). A thrown creature that slams into something takes up to {@link #SLAM}.
 * Held at most {@link #MAX_TICKS}; the creature's struggle breaks it past half again {@link #REACH}.
 */
public final class NadirGrip {
	// ---- tuning ----
	/** How far it takes hold (blocks), and how near and far it is held. */
	public static final double REACH = 24.0, HOLD_MIN = 3.0, HOLD_MAX = 8.0;
	/** How hard it is pulled to where it is held (share of the gap a tick), and its fastest (blocks a tick). */
	static final double PULL = 0.35, PULL_MAX = 1.6;
	/** The longest hold (ticks), and the cooldown after (V's). */
	public static final int MAX_TICKS = 200, COOLDOWN = 140;
	/** A throw: its speed, the least swing of the view (over {@link #SWING_TICKS}) that throws instead of dropping. */
	static final double THROW_SPEED = 2.4, SWING_MIN = 0.12;
	static final int SWING_TICKS = 4;
	/** Damage: a thrown creature slamming into something (at full speed), crushed in, torn apart. */
	static final float SLAM = 14.0F, IMPLODE = 34.0F, TEAR = 28.0F;
	/** How long a thrown creature is watched for its slam (ticks). */
	static final int FLUNG_TICKS = 40;
	/** The void's grip: its colour, and the dark of its heart. */
	public static final int ENERGY = 0x9A5CFF, DEEP = 0x2A0F4A;

	private static final class Grip {
		final int target;
		final double distance;
		final long since;
		final ArrayDeque<Vec3> looks = new ArrayDeque<>();

		Grip(int target, double distance, long since) {
			this.target = target;
			this.distance = distance;
			this.since = since;
		}
	}

	private record Flung(UUID owner, LivingEntity body, long until) {
	}

	private static final Map<UUID, Grip> GRIPS = new HashMap<>();
	private static final List<Flung> FLUNG = new ArrayList<>();

	private NadirGrip() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(NadirGrip::tickFlung);
	}

	/** Whether {@code player} holds a creature now. */
	public static boolean holding(ServerPlayer player) {
		return GRIPS.containsKey(player.getUUID());
	}

	/** Takes hold of {@code target}. */
	public static void grab(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		double distance = Mth.clamp(target.distanceTo(player), HOLD_MIN, HOLD_MAX);
		GRIPS.put(player.getUUID(), new Grip(target.getId(), distance, level.getGameTime()));
		Vec3 c = target.getBoundingBox().getCenter();
		MagicFx.blast(level, c, size(target) * 1.4F, ENERGY);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8F, 0.5F);
	}

	private static float size(LivingEntity body) {
		return Math.max(0.6F, Math.max(body.getBbWidth(), body.getBbHeight()) * 0.7F);
	}

	/** Every tick of the form: the held creature follows the look, wrapped in the void's violet. */
	public static void tick(ServerPlayer player) {
		Grip grip = GRIPS.get(player.getUUID());
		if (grip == null) {
			return;
		}
		ServerLevel level = player.level();
		LivingEntity body = held(player, grip);
		if (body == null || level.getGameTime() - grip.since > MAX_TICKS) {
			end(player);
			return;
		}
		Vec3 look = player.getViewVector(1.0F);
		grip.looks.addLast(look);
		while (grip.looks.size() > SWING_TICKS + 1) {
			grip.looks.removeFirst();
		}
		Vec3 spot = player.getEyePosition().add(look.scale(grip.distance)).subtract(0.0, body.getBbHeight() * 0.5, 0.0);
		Vec3 pull = spot.subtract(body.position()).scale(PULL);
		if (pull.length() > PULL_MAX) {
			pull = pull.normalize().scale(PULL_MAX);
		}
		push(body, pull);
		body.resetFallDistance();
		Vec3 c = body.getBoundingBox().getCenter();
		if (level.getGameTime() % 3 == 0) {
			MagicFx.orb(level, c, size(body), ENERGY, 5);
		}
		if (level.getGameTime() % 2 == 0) {
			Vec3 hand = player.getEyePosition().add(look.scale(0.6)).add(0.0, -0.35, 0.0);
			MagicFx.beam(level, hand, c, 0.04F, ENERGY, 3);
			level.sendParticles(MagicFx.mote(ENERGY, 0.9F), c.x, c.y, c.z, 2, body.getBbWidth() * 0.5, body.getBbHeight() * 0.4,
				body.getBbWidth() * 0.5, 0.02);
		}
		if (level.getGameTime() % 20 == 0) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.7F, 1.4F);
		}
	}

	/** The held creature, while it's alive, in this world and not torn out of reach; else null. */
	private static @Nullable LivingEntity held(ServerPlayer player, Grip grip) {
		Entity e = player.level().getEntity(grip.target);
		double reach = REACH * 1.5;
		return e instanceof LivingEntity living && living.isAlive() && living.distanceToSqr(player) <= reach * reach ? living : null;
	}

	/** V let go: thrown the way the view swings, or dropped if it was held still. */
	public static void release(ServerPlayer player) {
		Grip grip = GRIPS.remove(player.getUUID());
		if (grip == null) {
			return;
		}
		LivingEntity body = held(player, grip);
		if (body == null) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 now = player.getViewVector(1.0F);
		Vec3 swing = grip.looks.isEmpty() ? Vec3.ZERO : now.subtract(grip.looks.peekFirst());
		Vec3 c = body.getBoundingBox().getCenter();
		if (swing.length() < SWING_MIN) {
			push(body, Vec3.ZERO);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
			return;
		}
		Vec3 way = now.add(swing.normalize().scale(Math.min(1.0, swing.length() * 2.0))).normalize();
		push(body, way.scale(THROW_SPEED));
		FLUNG.add(new Flung(player.getUUID(), body, level.getGameTime() + FLUNG_TICKS));
		MagicFx.ring(level, c, way, size(body) * 2.0F, ENERGY, 8);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2F, 0.6F);
	}

	/** G while held: the void crushes it in on itself. */
	public static void implode(ServerPlayer player) {
		Grip grip = GRIPS.remove(player.getUUID());
		LivingEntity body = grip == null ? null : held(player, grip);
		if (body == null) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 c = body.getBoundingBox().getCenter();
		MagicFx.ring(level, c, player.getViewVector(1.0F), size(body) * 2.5F, ENERGY, 6);
		MagicFx.blast(level, c, size(body) * 0.8F, DEEP, 10);
		level.sendParticles(MagicFx.miasma(DEEP, 2.0F), c.x, c.y, c.z, 14, body.getBbWidth() * 0.4, body.getBbHeight() * 0.4, body.getBbWidth() * 0.4, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9F, 1.8F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.4F, 0.6F);
		body.setInvulnerableTime(0);
		body.hurtServer(level, player.damageSources().playerAttack(player), IMPLODE);
		push(body, Vec3.ZERO);
		Shakes.around(level, c, 12.0, 0.8F);
	}

	/** B while held: the void pulls it apart. Killed, it loses its arms and head. */
	public static void tear(ServerPlayer player) {
		Grip grip = GRIPS.remove(player.getUUID());
		LivingEntity body = grip == null ? null : held(player, grip);
		if (body == null) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 c = body.getBoundingBox().getCenter();
		Vec3 side = player.getViewVector(1.0F).cross(new Vec3(0.0, 1.0, 0.0));
		side = side.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		MagicFx.beam(level, c.add(side.scale(size(body) * 1.6)), c.subtract(side.scale(size(body) * 1.6)), 0.12F, ENERGY, 6);
		MagicFx.blast(level, c, size(body) * 1.6F, ENERGY);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.5F);
		body.setInvulnerableTime(0);
		body.hurtServer(level, player.damageSources().playerAttack(player), TEAR);
		if (body.isDeadOrDying() && Dismember.canLoseParts(body)) {
			for (int part : new int[] {Dismember.RIGHT_ARM, Dismember.LEFT_ARM, Dismember.HEAD}) {
				if ((Dismember.missing(body) & part) == 0) {
					Dismember.sever(body, part, player);
				}
			}
		}
		push(body, Vec3.ZERO);
	}

	/** Lets go without a throw (the form ends, the hold times out). */
	public static void end(ServerPlayer player) {
		Grip grip = GRIPS.remove(player.getUUID());
		LivingEntity body = grip == null ? null : held(player, grip);
		if (body != null) {
			push(body, Vec3.ZERO);
		}
	}

	/** Sets a body's motion (a player's own client is told, as it moves itself). */
	private static void push(LivingEntity body, Vec3 velocity) {
		body.setDeltaMovement(velocity);
		body.syncVelocity = true;
		if (body instanceof ServerPlayer p && ServerPlayNetworking.canSend(p, WebNet.Nudge.TYPE)) {
			ServerPlayNetworking.send(p, new WebNet.Nudge(velocity));
		}
	}

	/** Thrown creatures: one that slams into a wall, the ground or a ceiling going fast is hurt by how fast. */
	private static void tickFlung(MinecraftServer server) {
		if (FLUNG.isEmpty()) {
			return;
		}
		for (Iterator<Flung> it = FLUNG.iterator(); it.hasNext(); ) {
			Flung f = it.next();
			LivingEntity body = f.body;
			if (!body.isAlive() || body.isRemoved() || !(body.level() instanceof ServerLevel level) || level.getGameTime() > f.until) {
				it.remove();
				continue;
			}
			double speed = body.getDeltaMovement().length();
			if ((body.horizontalCollision || body.verticalCollision) && level.getGameTime() + FLUNG_TICKS - 2 > f.until) {
				it.remove();
				float share = (float)Mth.clamp(Math.max(speed, 0.6) / THROW_SPEED, 0.3, 1.0);
				Vec3 c = body.getBoundingBox().getCenter();
				ServerPlayer owner = server.getPlayerList().getPlayer(f.owner);
				body.setInvulnerableTime(0);
				body.hurtServer(level, owner != null ? owner.damageSources().playerAttack(owner) : level.damageSources().flyIntoWall(), SLAM * share);
				MagicFx.blast(level, c, 1.2F + share, ENERGY);
				level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.8F);
				Shakes.around(level, c, 14.0, 0.6F * share);
			}
		}
	}
}
