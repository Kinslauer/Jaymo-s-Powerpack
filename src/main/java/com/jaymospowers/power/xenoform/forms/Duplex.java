package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoDouble;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoForms;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Duplex, the copper duplicator (scanned from a copper golem): a stocky copper body with a lightning rod on its head
 * that can split off doubles of itself. Each double is weak, but they all fight what you fight.
 * <ul>
 * <li>R, Split: a double steps out of you (up to {@link #MAX_DOUBLES}).</li>
 * <li>G, Rally: your doubles go for the creature you aim at; aimed at nothing, they come back to you.</li>
 * <li>V, Switch: trade places with the double you aim at (or the farthest one).</li>
 * <li>B, Merge: every double runs back into you, each healing you a little.</li>
 * <li>C (ascended), Copper Storm: lightning strikes every double, shocking everything round it, and leaves them
 * charged (fast, strong, glowing); up to {@link #STORM_MAX_DOUBLES} doubles while it lasts. C again strikes again.</li>
 * </ul>
 * A loyal double that dies stings you for {@link #DEATH_STING}. Corrupted: every {@link #CORRUPT_EVERY}th split comes
 * out rogue, red-tinted and after you.
 */
public final class Duplex extends XenoForm {
	// ---- tuning ----
	public static final int SPLIT_COOLDOWN = 40, MAX_DOUBLES = 3, STORM_MAX_DOUBLES = 6;
	public static final int RALLY_COOLDOWN = 20;
	public static final double RALLY_REACH = 16.0;
	public static final int SWITCH_COOLDOWN = 40;
	public static final double SWITCH_REACH = 32.0;
	public static final int MERGE_COOLDOWN = 160;
	public static final float MERGE_HEAL = 3.0F;
	/** Doubles this far off (or farther) don't make it back for a merge. */
	public static final double MERGE_RANGE = 24.0;
	/** What a loyal double's death costs you. */
	public static final float DEATH_STING = 2.0F;
	/** Copper Storm: the shock round each double (radius, damage), C's cooldown between strikes, and how long the charge lasts. */
	public static final double STORM_RADIUS = 3.0;
	public static final float STORM_DAMAGE = 6.0F;
	public static final int STORM_COOLDOWN = 400, CHARGE_TICKS = 400;
	/** Corrupted: one split in this many is rogue. */
	public static final int CORRUPT_EVERY = 4;

	public Duplex() {
		super("duplex", 0.9F, 18.0F, 3.0, 0.1, 0.2, 0.0, Items.COPPER_INGOT, 0xE07B4A);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof CopperGolem;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		popAll(player.level(), live.doubles);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R) && split(player, live)) {
					cooldown(player, live, R, SPLIT_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G) && rally(player, live)) {
					cooldown(player, live, G, RALLY_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V) && swap(player, live)) {
					cooldown(player, live, V, SWITCH_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && merge(player, live)) {
					cooldown(player, live, B, MERGE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		if (player.tickCount % 10 != 0) {
			return;
		}
		ServerLevel level = player.level();
		live.doubles.removeIf(id -> !(level.getEntity(id) instanceof XenoDouble twin) || !twin.isAlive());
		// whoever hurts you, the doubles go for
		LivingEntity attacker = player.getLastHurtByMob();
		if (attacker != null && attacker.isAlive() && player.tickCount - player.getLastHurtByMobTimestamp() < 40 && !mine(player, attacker)) {
			for (XenoDouble twin : loyal(player, live)) {
				if (twin.getTarget() == null) {
					twin.setTarget(attacker);
				}
			}
		}
	}

	/** What you hit, your doubles go for. */
	@Override
	public void onDealtDamage(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
		if (mine(player, target) || !target.isAlive()) {
			return;
		}
		for (XenoDouble twin : loyal(player, live)) {
			if (twin.getTarget() == null) {
				twin.setTarget(target);
			}
		}
	}

	/** A loyal double died: it stings. Called by the double itself. */
	public static void doubleDied(ServerPlayer player, ServerLevel level) {
		if (XenoformState.form(player) != XenoForms.DUPLEX) {
			return;
		}
		player.hurtServer(level, player.damageSources().magic(), DEATH_STING);
		level.sendParticles(ParticleTypes.WAX_OFF, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.5, 0.3, 0.05);
	}

	/** Whether {@code e} is one of the player's doubles (rogue or not). */
	private static boolean mine(ServerPlayer player, Entity e) {
		return e instanceof XenoDouble twin && twin.isDoubleOf(player);
	}

	/** The player's living, loyal doubles. */
	private static List<XenoDouble> loyal(ServerPlayer player, XenoformPower.Live live) {
		List<XenoDouble> out = new ArrayList<>();
		for (int id : live.doubles) {
			if (player.level().getEntity(id) instanceof XenoDouble twin && twin.isAlive() && !twin.rogue()) {
				out.add(twin);
			}
		}
		return out;
	}

	private static void popAll(ServerLevel level, List<Integer> ids) {
		for (int id : ids) {
			if (level.getEntity(id) instanceof XenoDouble twin) {
				twin.pop(level);
			}
		}
		ids.clear();
	}

	// ---- R: Split ----

	private static boolean split(ServerPlayer player, XenoformPower.Live live) {
		int cap = live.ultimate ? STORM_MAX_DOUBLES : MAX_DOUBLES;
		live.doubles.removeIf(id -> !(player.level().getEntity(id) instanceof XenoDouble twin) || !twin.isAlive());
		if (live.doubles.size() >= cap) {
			return false;
		}
		boolean rogue = live.corrupted && ++live.splits % CORRUPT_EVERY == 0;
		XenoDouble twin = spawnDouble(player, live, rogue);
		if (twin == null) {
			return false;
		}
		sound(player, SoundEvents.COPPER_GOLEM_SPAWN, 1.0F, 1.2F);
		sound(player, SoundEvents.COPPER_PLACE, 1.0F, 0.8F);
		return true;
	}

	/** A double beside you (left or right, whichever has room; else where you stand). */
	private static @Nullable XenoDouble spawnDouble(ServerPlayer player, XenoformPower.Live live, boolean rogue) {
		ServerLevel level = player.level();
		XenoDouble twin = XenoDouble.TYPE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (twin == null) {
			return null;
		}
		Vec3 side = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F).scale(1.2);
		Vec3 at = player.position();
		for (Vec3 candidate : new Vec3[] {at.add(side), at.subtract(side)}) {
			if (level.noCollision(XenoDouble.TYPE.getDimensions().makeBoundingBox(candidate))) {
				at = candidate;
				break;
			}
		}
		twin.snapTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
		twin.setYHeadRot(player.getYRot());
		twin.addTag(XenoformState.MINION_TAG);
		twin.bindTo(player, rogue);
		if (live.chargedUntil > level.getGameTime() && !rogue) {
			charge(twin, (int)(live.chargedUntil - level.getGameTime()));
		}
		if (!level.addFreshEntity(twin)) {
			return null;
		}
		live.doubles.add(twin.getId());
		if (rogue) {
			twin.setTarget(player);
			level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 1.0, at.z, 14, 0.3, 0.5, 0.3, 0.02);
		}
		level.sendParticles(ParticleTypes.WAX_ON, at.x, at.y + 1.0, at.z, 14, 0.3, 0.5, 0.3, 0.05);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 10, 0.3, 0.5, 0.3, 0.08);
		return twin;
	}

	// ---- G: Rally ----

	private static boolean rally(ServerPlayer player, XenoformPower.Live live) {
		List<XenoDouble> doubles = loyal(player, live);
		if (doubles.isEmpty()) {
			return false;
		}
		LivingEntity target = aimed(player, RALLY_REACH, e -> !mine(player, e));
		ServerLevel level = player.level();
		for (XenoDouble twin : doubles) {
			twin.setTarget(target);
			if (target == null) {
				twin.getNavigation().moveTo(player, XenoDouble.FOLLOW_SPEED);
			}
		}
		if (target != null) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 10, 0.2, 0.1, 0.2, 0.05);
			sound(player, SoundEvents.COPPER_GOLEM_STEP, 1.0F, 1.6F);
		} else {
			sound(player, SoundEvents.COPPER_GOLEM_STEP, 1.0F, 0.9F);
		}
		return true;
	}

	// ---- V: Switch ----

	private static boolean swap(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		LivingEntity aimedAt = aimedDouble(player);
		XenoDouble pick = aimedAt instanceof XenoDouble twin && !twin.rogue() ? twin : null;
		if (pick == null) {
			double best = -1.0;
			for (XenoDouble twin : loyal(player, live)) {
				double d = twin.distanceToSqr(player);
				if (d > best && d < SWITCH_REACH * SWITCH_REACH) {
					best = d;
					pick = twin;
				}
			}
		}
		if (pick == null) {
			return false;
		}
		Vec3 mine = player.position();
		Vec3 theirs = pick.position();
		player.teleportTo(level, theirs.x, theirs.y, theirs.z, Set.of(), player.getYRot(), player.getXRot(), false);
		pick.teleportTo(mine.x, mine.y, mine.z);
		pick.getNavigation().stop();
		player.resetFallDistance();
		burst(level, ParticleTypes.WAX_OFF, mine.add(0.0, 1.0, 0.0), 10, 0.3, 0.05);
		burst(level, ParticleTypes.WAX_OFF, theirs.add(0.0, 1.0, 0.0), 10, 0.3, 0.05);
		sound(player, SoundEvents.COPPER_BULB_TURN_ON, 1.0F, 1.4F);
		return true;
	}

	/** The double under the crosshair (aimed() skips allies, and loyal doubles are your allies). */
	private static @Nullable LivingEntity aimedDouble(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(SWITCH_REACH));
		var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player.level(), player, eye, end,
			player.getBoundingBox().expandTowards(player.getLookAngle().scale(SWITCH_REACH)).inflate(1.0),
			e -> e instanceof XenoDouble twin && twin.isAlive() && twin.isDoubleOf(player), 0.3F);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	// ---- B: Merge ----

	private static boolean merge(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		int merged = 0;
		for (Iterator<Integer> it = live.doubles.iterator(); it.hasNext();) {
			if (!(level.getEntity(it.next()) instanceof XenoDouble twin) || !twin.isAlive()) {
				it.remove();
				continue;
			}
			if (twin.rogue() || twin.distanceToSqr(player) > MERGE_RANGE * MERGE_RANGE) {
				continue;
			}
			// a streak of copper from the double back into you
			Vec3 from = twin.position().add(0.0, 1.0, 0.0);
			Vec3 to = player.position().add(0.0, 1.0, 0.0);
			int steps = Math.max(2, (int)(from.distanceTo(to) * 2.0));
			for (int i = 0; i <= steps; i++) {
				Vec3 p = from.lerp(to, i / (double)steps);
				level.sendParticles(ParticleTypes.WAX_ON, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
			twin.pop(level);
			it.remove();
			merged++;
		}
		if (merged == 0) {
			return false;
		}
		player.heal(MERGE_HEAL * merged);
		burst(level, ParticleTypes.HAPPY_VILLAGER, player.position().add(0.0, 1.0, 0.0), 6 * merged, 0.4, 0.02);
		sound(player, SoundEvents.COPPER_GOLEM_SPAWN, 1.0F, 0.7F);
		return true;
	}

	// ---- C: Copper Storm ----

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		// the storm brings a full set of doubles with it
		while (loyal(player, live).size() < MAX_DOUBLES && live.doubles.size() < STORM_MAX_DOUBLES) {
			if (spawnDouble(player, live, false) == null) {
				break;
			}
		}
		if (loyal(player, live).isEmpty()) {
			return false;
		}
		storm(player, live);
		return true;
	}

	@Override
	public void onUltimateAgain(ServerPlayer player, XenoformPower.Live live) {
		if (ultimateReady(player, live) && !loyal(player, live).isEmpty()) {
			storm(player, live);
		}
	}

	@Override
	public void onUltimateEnd(ServerPlayer player, XenoformPower.Live live) {
		live.chargedUntil = 0L;
		ServerLevel level = player.level();
		// back down to the everyday count: the newest extras go first
		live.doubles.removeIf(id -> !(level.getEntity(id) instanceof XenoDouble twin) || !twin.isAlive());
		while (live.doubles.size() > MAX_DOUBLES) {
			int id = live.doubles.remove(live.doubles.size() - 1);
			if (level.getEntity(id) instanceof XenoDouble twin) {
				twin.pop(level);
			}
		}
	}

	/** Lightning (for show) on every loyal double, a shock round each, and the charge. */
	private static void storm(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		List<XenoDouble> doubles = loyal(player, live);
		List<LivingEntity> shocked = new ArrayList<>();
		for (XenoDouble twin : doubles) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.snapTo(twin.getX(), twin.getY(), twin.getZ());
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, twin.getBoundingBox().inflate(STORM_RADIUS),
				e -> e != player && e.isAlive() && !mine(player, e) && !spared(player, e) && !e.isSpectator()
					&& e.distanceToSqr(twin) <= STORM_RADIUS * STORM_RADIUS)) {
				if (!shocked.contains(e)) {
					shocked.add(e);
				}
			}
			charge(twin, CHARGE_TICKS);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, twin.getX(), twin.getY() + 1.0, twin.getZ(), 30, 0.5, 0.8, 0.5, 0.2);
		}
		for (LivingEntity e : shocked) {
			e.hurtServer(level, player.damageSources().lightningBolt(), STORM_DAMAGE);
		}
		live.chargedUntil = level.getGameTime() + CHARGE_TICKS;
		sound(player, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 1.3F);
		ultimateCooldown(player, live, STORM_COOLDOWN);
	}

	private static void charge(XenoDouble twin, int ticks) {
		twin.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1, false, false));
		twin.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, 0, false, false));
		twin.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
	}
}
