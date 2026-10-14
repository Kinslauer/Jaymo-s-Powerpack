package com.jaymospowers.core;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The shake every ordinary impact gets, on top of the powers' own ({@link Shakes}): landing from a fall, a hit taken,
 * a hit dealt, and the footsteps of anything heavy. All of it scales with the body's heft (how much bigger than a
 * player it is, see {@link #heft}), so Bulwark, Knell, a symbiote's big body or a warden shakes the ground where a
 * person barely nudges your view.
 */
public final class Impacts {
	/** A body's heft is its standing width x height against a player's; the symbiote's suit counts this much heavier. */
	public static final float SUIT_HEFT = 1.4F, MAX_HEFT = 12.0F;

	/** Landings: degrees per block of fall x heft, past this many; at most {@code LAND_MAX}. */
	public static final float LAND_PER_BLOCK = 0.11F, LAND_FROM = 3.0F, LAND_MAX = 3.5F;
	/** Others feel a landing within this many blocks, plus this many per heft. */
	public static final double LAND_RANGE = 4.0, LAND_RANGE_PER_HEFT = 3.0;
	/** A mob has to be at least this heavy for its landings and steps to shake anyone. */
	public static final float MOB_HEFT = 2.2F;

	/** Footsteps: a player this heavy shakes the ground with each step, this many degrees per heft over a player's. */
	public static final float STEP_HEFT = 1.6F, STEP_PER_HEFT = 0.25F, STEP_MIN = 0.12F, STEP_MAX = 1.4F;
	/** A heavy mob's steps shake at this share (so a village's golems don't rattle you all day). */
	public static final float MOB_STEP_SHARE = 0.4F;
	public static final double STEP_RANGE = 4.0, STEP_RANGE_PER_HEFT = 2.0;

	/** Hits taken: degrees per point of damage (x the attacker's heft, square-rooted); at most this. */
	public static final float TAKEN_PER_DAMAGE = 0.1F, TAKEN_MIN = 0.15F, TAKEN_MAX = 3.5F;
	/** Hits dealt: degrees per point of damage (x your heft, square-rooted); at most this. */
	public static final float DEALT_PER_DAMAGE = 0.05F, DEALT_MIN = 0.1F, DEALT_MAX = 2.0F;
	/** A body this heavy shakes everyone near the things it hits, at this share, within this many blocks per heft. */
	public static final float BIG_HIT_HEFT = 2.0F, BIG_HIT_SHARE = 0.5F;
	public static final double BIG_HIT_RANGE_PER_HEFT = 3.0;

	/** This tick's strongest hit for each player (a sweep or a blast that hits ten things is one shake). */
	private static final Map<ServerPlayer, Pending> HITS = new HashMap<>();

	private record Pending(float strength, Vec3 from) {
	}

	private Impacts() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Impacts::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			HITS.forEach((player, hit) -> Shakes.send(player, hit.strength, hit.from));
			HITS.clear();
		});
	}

	/** How much heavier than a player this body is (1 = a player; Bulwark ~2.3, an iron golem ~3.5). */
	public static float heft(Entity entity) {
		EntityDimensions d = entity.getDimensions(Pose.STANDING);
		float heft = d.width() * d.height() / (0.6F * 1.8F);
		if (entity instanceof Player player && com.jaymospowers.power.web.WebSymbiote.suitShown(player)) {
			heft *= SUIT_HEFT;
		}
		return Mth.clamp(heft, 0.1F, MAX_HEFT);
	}

	private static boolean heavyMob(LivingEntity entity, float heft) {
		return heft >= MOB_HEFT && (entity instanceof Enemy || entity instanceof AbstractGolem);
	}

	/** Something landed after falling {@code fall} blocks (from the fall-damage check, server side). */
	public static void landed(LivingEntity entity, double fall) {
		if (!(entity.level() instanceof ServerLevel level) || entity.isInWater()) {
			return;
		}
		float heft = heft(entity);
		float strength = Math.min(LAND_MAX, LAND_PER_BLOCK * (float)(fall * heft - LAND_FROM));
		if (strength <= 0.0F) {
			return;
		}
		double range = LAND_RANGE + LAND_RANGE_PER_HEFT * heft;
		if (entity instanceof ServerPlayer player) {
			Shakes.felt(player, player.position(), range, strength, Shakes.ticksFor(strength));
		} else if (heavyMob(entity, heft)) {
			Shakes.around(level, entity.position(), range, strength);
		}
	}

	/** Something took a step (server side): heavy bodies shake the ground. */
	public static void stepped(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level) || entity.isShiftKeyDown() || entity.isInWater()) {
			return;
		}
		float heft = heft(entity);
		float strength = Mth.clamp(STEP_PER_HEFT * (heft - 1.0F), STEP_MIN, STEP_MAX);
		double range = STEP_RANGE + STEP_RANGE_PER_HEFT * heft;
		if (entity instanceof ServerPlayer player) {
			// the Great Ape's strides shake on their own (ZFighterLineage)
			if (heft >= STEP_HEFT && !com.jaymospowers.power.zfighter.ZFighterLineage.ape(player)) {
				Shakes.felt(player, player.position(), range, strength, Shakes.ticksFor(strength));
			}
		} else if (heavyMob(entity, heft)) {
			Shakes.around(level, entity.position(), range, strength * MOB_STEP_SHARE);
		}
	}

	private static void afterDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE)
			|| !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		Entity attacker = source.getEntity();
		Vec3 from = source.getSourcePosition();
		if (attacker == victim || from == null && attacker == null) {
			return;
		}
		if (from == null) {
			from = attacker.getBoundingBox().getCenter();
		}
		// a blocked blow still jars the arm
		float damage = blocked ? base * 0.5F : taken;
		if (damage <= 0.0F) {
			return;
		}
		float attackerHeft = attacker == null ? 1.0F : heft(attacker);
		if (victim instanceof ServerPlayer player) {
			hit(player, Mth.clamp(TAKEN_PER_DAMAGE * damage * Mth.sqrt(attackerHeft), TAKEN_MIN, TAKEN_MAX), from);
		}
		Vec3 struck = victim.getBoundingBox().getCenter();
		if (attacker instanceof ServerPlayer player && player != victim) {
			hit(player, Mth.clamp(DEALT_PER_DAMAGE * damage * Mth.sqrt(attackerHeft * Math.max(1.0F, heft(victim))), DEALT_MIN, DEALT_MAX), struck);
		}
		if (attacker != null && attackerHeft >= BIG_HIT_HEFT) {
			float strength = Math.min(DEALT_MAX, DEALT_PER_DAMAGE * damage * attackerHeft) * BIG_HIT_SHARE;
			double range = BIG_HIT_RANGE_PER_HEFT * attackerHeft;
			for (ServerPlayer near : level.players()) {
				if (near != attacker && near != victim) {
					double d = near.position().distanceTo(struck);
					if (d < range) {
						hit(near, strength * (float)Math.pow(1.0 - d / range, Shakes.FALLOFF_POWER), struck);
					}
				}
			}
		}
	}

	private static void hit(ServerPlayer player, float strength, Vec3 from) {
		Pending was = HITS.get(player);
		if (was == null || was.strength < strength) {
			HITS.put(player, new Pending(strength, from));
		}
	}
}
