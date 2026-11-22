package com.jaymospowers.core.brawl;

import com.jaymospowers.combat.ClashFx;
import com.jaymospowers.core.BlowClash;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Trading blows (NPC fights patch 3, row F9): when two fighters, a brawling NPC and a player or another NPC, land a blow on
 * each other within {@link #WINDOW} ticks, the second blow does not land: the fists meet instead. Sparks at the midpoint, a
 * shockwave ring and cracks, a boom, both pushed back (further the higher their rank; less in the air), a shake for everyone near,
 * and the first blow is taken back too (the health it cost is given back), so neither hurt. One clash for each pair every
 * {@link #PAIR_GAP} ticks, so a fight still goes on. Works against players exactly as between NPCs: a player's bare-handed or
 * weapon blow meets an NPC's the same way.
 */
public final class TradeBlows {
	/** The two blows meet when the second lands within this many ticks of the first, and the two are within {@link #REACH} blocks. */
	static final int WINDOW = 3;
	static final double REACH = 5.0;
	/** One clash for each pair this often (ticks, about 2 s). */
	static final int PAIR_GAP = 40;
	/** Pushed back at rank 0 and 1 (blocks a tick: about 2 to 4 blocks on the ground), and in the air. */
	static final double PUSH_MIN = 0.9, PUSH_MAX = 1.8, PUSH_AIR_MIN = 0.35, PUSH_AIR_MAX = 0.7, PUSH_LIFT = 0.2;
	/** The impact's power (0 to 1) at rank 0 and 1. */
	static final float POWER_MIN = 0.3F, POWER_MAX = 1.0F;
	/** How long a remembered blow is kept (ticks), swept this often. */
	static final int KEEP = 100, SWEEP = 200;

	/** A blow landed: on whom, when, and how healthy they were before it. */
	private record Blow(UUID victim, long at, float healthBefore) {
	}

	private static final Map<UUID, Blow> LAST = new HashMap<>();
	private static final Map<String, Long> RESTING = new HashMap<>();

	private TradeBlows() {
	}

	/** From {@link Brawl#init}. */
	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(TradeBlows::allow);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST.clear();
			RESTING.clear();
		});
	}

	private static boolean allow(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0.0F || !(victim.level() instanceof ServerLevel level) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			|| source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)
			|| !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || attacker == victim) {
			return true;
		}
		long now = level.getGameTime();
		if (now % SWEEP == 0) {
			LAST.values().removeIf(b -> now - b.at() > KEEP);
			RESTING.values().removeIf(until -> now >= until);
		}
		// did the one being hit land a blow on this attacker a moment ago?
		Blow back = LAST.get(victim.getUUID());
		if (back != null && back.victim().equals(attacker.getUUID()) && now - back.at() <= WINDOW && fighters(attacker, victim)
			&& now >= RESTING.getOrDefault(pair(attacker, victim), 0L)) {
			meet(level, attacker, victim, back);
			RESTING.put(pair(attacker, victim), now + PAIR_GAP);
			LAST.remove(victim.getUUID());
			return false;
		}
		LAST.put(attacker.getUUID(), new Blow(victim.getUUID(), now, victim.getHealth()));
		return true;
	}

	/** Both alive and close, neither locked in another clash, and at least one a brawling NPC (a clash of two players is theirs to play). */
	private static boolean fighters(LivingEntity a, LivingEntity b) {
		if (!a.isAlive() || !b.isAlive() || a.distanceTo(b) > REACH || BlowClash.locked(a) || BlowClash.locked(b)
			|| com.jaymospowers.core.BeamClash.clashing(a) || com.jaymospowers.core.BeamClash.clashing(b)) {
			return false;
		}
		if (a instanceof Player p && (p.isSpectator() || p.isCreative()) || b instanceof Player q && (q.isSpectator() || q.isCreative())) {
			return false;
		}
		return a instanceof Mob m && Brawl.fighting(m) || b instanceof Mob n && Brawl.fighting(n);
	}

	private static String pair(LivingEntity a, LivingEntity b) {
		UUID x = a.getUUID(), y = b.getUUID();
		return x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x;
	}

	/** {@code second} swung at {@code first}, who had swung at them: the fists meet. */
	private static void meet(ServerLevel level, LivingEntity second, LivingEntity first, Blow back) {
		// the first blow is taken back: whoever it hit gets back what it cost (it hit {@code second})
		if (second.isAlive() && second.getHealth() < back.healthBefore()) {
			second.setHealth(Math.min(second.getMaxHealth(), back.healthBefore()));
		}
		float rank = Math.max(rank(second), rank(first));
		Vec3 a = second.getBoundingBox().getCenter(), b = first.getBoundingBox().getCenter();
		Vec3 mid = a.add(b).scale(0.5);
		Vec3 line = b.subtract(a);
		ClashFx.sparks(level, mid, line, ClashFx.CLASH_SPARKS, ClashFx.CLASH_SCALE);
		MagicFx.ring(level, mid, line, 1.6F + 1.2F * rank, Spectacle.SHOCK_COLOR, MagicFx.RING_TICKS);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.5F, 0.6F);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.2F, 0.7F);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6F, 1.7F);
		Spectacle.impact(level, new Vec3(mid.x, Math.min(second.getY(), first.getY()), mid.z), Mth.lerp(rank, POWER_MIN, POWER_MAX));
		push(second, first, rank);
		push(first, second, rank);
	}

	/** {@code who} is thrown back from {@code from}: along the ground, or less hard in the air. */
	private static void push(LivingEntity who, LivingEntity from, float rank) {
		Vec3 away = who.position().subtract(from.position());
		away = away.lengthSqr() < 1.0E-4 ? who.getLookAngle().reverse() : away;
		boolean air = !who.onGround();
		double speed = air ? Mth.lerp(rank, PUSH_AIR_MIN, PUSH_AIR_MAX) : Mth.lerp(rank, PUSH_MIN, PUSH_MAX);
		Vec3 out = air ? away.normalize() : away.multiply(1.0, 0.0, 1.0).normalize();
		double resist = 1.0 - who.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
		who.setDeltaMovement(out.scale(speed * resist).add(0.0, air ? 0.0 : PUSH_LIFT, 0.0));
		who.syncVelocity = true;
	}

	private static float rank(LivingEntity e) {
		return e instanceof Mob mob ? Brawl.rank(mob) : 0.0F;
	}
}
