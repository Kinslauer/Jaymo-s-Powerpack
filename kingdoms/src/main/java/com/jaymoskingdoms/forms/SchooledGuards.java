package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.Worker;
import com.jaymoskingdoms.WeaponForms;
import com.jaymoskingdoms.forms.Forms.Family;
import com.jaymoskingdoms.forms.Schools.School;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.385) Schooled guards (Weapon Forms, patch 5). A realm's guard (the folk or an MCA villager in the job:
 * {@link Downed#isGuard}) whose post lies in a school's land ({@link Schools#localSchool}) fights with that school's forms.
 * While it has a combat target within {@link #MIN_RANGE}-{@link #MAX_RANGE} blocks, every {@link #CHECK_EVERY} ticks, once its
 * wait is over ({@link #GAP} ticks, {@link #GAP_VETERAN} for a {@link WeaponForms#experienced} one, plus up to
 * {@link #GAP_JITTER}) it may use one move of the family of the weapon it holds (a weapon of another family than its school's
 * is used {@link #OFF_STYLE_PERCENT} per cent as often):
 * <ul>
 * <li><b>blade</b>: a sweep across the front arc;</li>
 * <li><b>spear</b>: a lunge that carries the guard forward and runs through the foe and one behind it;</li>
 * <li><b>fist</b>: a stomp, a ring that shoves foes back and slows them;</li>
 * <li><b>knife</b>: two quick stabs;</li>
 * <li><b>claw</b>: two rakes.</li>
 * </ul>
 * These are simple versions written for mobs, not the player forms' own code. Every move is shown first (a glint at the weapon
 * and a pause of {@link #WIND_UP} ticks, the stomp's red), so it can be dodged. A move only ever strikes the guard's target
 * and monsters near it (never villagers, folk or other players), is capped at {@link #MAX_DAMAGE}, and is dropped if the fight
 * is over before it lands. Nothing is saved.
 */
public final class SchooledGuards {
	// ---- tuning ----
	/** How often (ticks) guards near players are looked at; how far from a player (blocks) a guard is looked at. */
	static final int CHECK_EVERY = 10;
	static final double SCAN = 24.0;
	/** The wait between one move and the next (ticks): ordinary, veteran; and the random extra on top. */
	static final int GAP = 160, GAP_VETERAN = 90, GAP_JITTER = 60;
	/** The chance, at each look once the wait is over, that the move comes. */
	static final float CHANCE = 0.5F;
	/** How often a weapon of another family than the school's is used, per cent. */
	static final int OFF_STYLE_PERCENT = 40;
	/** The foe's distance (blocks, eye to its box) a move starts at. */
	static final double MIN_RANGE = 1.0, MAX_RANGE = 5.5;
	/** Ticks from the glint to the blow. */
	static final int WIND_UP = 8;
	/** Damage when the guard has no attack damage of its own, and the most one blow of a move may do. */
	static final float FALLBACK_DAMAGE = 4.0F, MAX_DAMAGE = 14.0F;
	/** Each move's share of the guard's attack damage (per hit). */
	static final float SWEEP = 1.3F, LUNGE = 1.4F, STOMP = 1.0F, STAB = 0.7F, RAKE = 0.8F;
	/** The sweep's reach and half-arc (degrees); the lunge's reach and the guard's dash (blocks a tick); the stomp's radius. */
	static final double SWEEP_REACH = 3.2, SWEEP_HALF = 65.0, LUNGE_REACH = 4.5, LUNGE_DASH = 0.85, LUNGE_WIDTH = 0.9, STOMP_RADIUS = 3.5;
	/** The stomp's shove and lift on a foe, and the slowness it leaves (ticks). */
	static final double STOMP_PUSH = 0.7, STOMP_LIFT = 0.3;
	static final int STOMP_SLOW = 30;
	/** The gap (ticks) between the two hits of a stab or a rake. */
	static final int FOLLOW_UP = 4;
	/** Share of the damage a duel's move does (the duel's floor is its own safety). */
	static final float DUEL_SCALE = 0.8F;
	/** The glint's colours: a blow that can be met, and a heavy one. */
	static final int TELL = 0xFFFFFF, HEAVY_TELL = 0xE02020;

	/** A move waiting to land. */
	private static final class Pending {
		final Mob mob;
		final UUID target;
		final Family family;
		final long at;
		final boolean duel;
		final int step;

		Pending(Mob mob, UUID target, Family family, long at, boolean duel, int step) {
			this.mob = mob;
			this.target = target;
			this.family = family;
			this.at = at;
			this.duel = duel;
			this.step = step;
		}
	}

	private static final WeakHashMap<Mob, Long> READY = new WeakHashMap<>();
	private static final List<Pending> PENDING = new ArrayList<>();

	private SchooledGuards() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SchooledGuards::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			READY.clear();
			PENDING.clear();
		});
	}

	// ---- who ---------------------------------------------------------------------------------------------------------

	/** The school of {@code e}: its own if it's a master, else the one whose land its post is in; null for none. */
	public static @Nullable School schoolOf(ServerLevel level, LivingEntity e) {
		if (Schools.isMaster(e)) {
			return Schools.masterOf(e);
		}
		BlockPos post = e instanceof Worker w && w.site() != null ? w.site() : e.blockPosition();
		return Schools.localSchool(level, post);
	}

	/** The realm {@code e} serves: a folk's own, else the one whose land (or seat, within 64 blocks) it stands on. */
	public static @Nullable Faction realmOf(ServerLevel level, LivingEntity e) {
		MinecraftServer server = level.getServer();
		if (e instanceof Worker w && w.factionId() >= 0) {
			Faction f = Factions.get(server, w.factionId());
			if (f != null && !f.fallen()) {
				return f;
			}
		}
		Faction at = Factions.at(server, level, e.blockPosition());
		return at != null ? at : Factions.around(server, level, e.blockPosition(), 64.0);
	}

	/**
	 * The family of the move {@code mob} would use with the weapon in its hand for a guard of {@code school}: the school's own
	 * if the weapon fits it, else another family the weapon fits; null if none (a bow, an empty hand outside a fist school).
	 */
	private static @Nullable Family familyFor(Mob mob, School school) {
		var held = mob.getMainHandItem();
		if (school.family().holds(held)) {
			return school.family();
		}
		if (held.isEmpty()) {
			return null;
		}
		for (Family f : Family.values()) {
			if (f != Family.FIST && f != school.family() && f.holds(held)) {
				return f;
			}
		}
		return null;
	}

	// ---- the tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!PENDING.isEmpty()) {
			resolve(server);
		}
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) {
				continue;
			}
			for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(SCAN),
				m -> m.isAlive() && m.getTarget() != null && Downed.isGuard(m))) {
				if (seen.add(mob.getUUID())) {
					consider(level, mob);
				}
			}
		}
	}

	private static void consider(ServerLevel level, PathfinderMob mob) {
		LivingEntity target = mob.getTarget();
		long now = level.getGameTime();
		if (target == null || !target.isAlive() || target.level() != level || Downed.isDowned(mob) || Downed.isDowned(target)
			|| READY.getOrDefault(mob, 0L) > now || level.getRandom().nextFloat() >= CHANCE) {
			return;
		}
		double gap = gap(mob, target);
		if (gap < MIN_RANGE || gap > MAX_RANGE || !mob.getSensing().hasLineOfSight(target)) {
			return;
		}
		School school = schoolOf(level, mob);
		if (school == null) {
			return;
		}
		Family family = familyFor(mob, school);
		if (family == null || family != school.family() && level.getRandom().nextInt(100) >= OFF_STYLE_PERCENT) {
			return;
		}
		start(level, mob, target, family, false);
	}

	/**
	 * {@code mob} (a guard of a school) uses a move of the family of the weapon it holds on {@code target}, if its wait is over:
	 * the glint now, the blow {@link #WIND_UP} ticks later. {@code duel}: it is an honour duel's move (it lands only while the
	 * duel lasts, and softer). True if it began.
	 */
	public static boolean showForm(ServerLevel level, PathfinderMob mob, LivingEntity target, boolean duel) {
		long now = level.getGameTime();
		School school = schoolOf(level, mob);
		if (school == null || READY.getOrDefault(mob, 0L) > now) {
			return false;
		}
		Family family = familyFor(mob, school);
		if (family == null) {
			return false;
		}
		start(level, mob, target, family, duel);
		return true;
	}

	private static void start(ServerLevel level, Mob mob, LivingEntity target, Family family, boolean duel) {
		long now = level.getGameTime();
		int gap = WeaponForms.experienced(mob) ? GAP_VETERAN : GAP;
		READY.put(mob, now + gap + level.getRandom().nextInt(GAP_JITTER + 1));
		mob.getNavigation().stop();
		mob.getLookControl().setLookAt(target, 60.0F, 60.0F);
		tell(level, mob, family == Family.FIST);
		PENDING.add(new Pending(mob, target.getUUID(), family, now + WIND_UP, duel, 0));
	}

	private static void resolve(MinecraftServer server) {
		List<Pending> due = new ArrayList<>();
		for (Pending p : PENDING) {
			if (p.mob.level() instanceof ServerLevel level && level.getGameTime() >= p.at || p.mob.isRemoved()) {
				due.add(p);
			}
		}
		if (due.isEmpty()) {
			return;
		}
		PENDING.removeAll(due);
		for (Pending p : due) {
			if (!(p.mob.level() instanceof ServerLevel level) || p.mob.isRemoved() || !p.mob.isAlive() || Downed.isDowned(p.mob)
				|| !(level.getEntity(p.target) instanceof LivingEntity target) || !target.isAlive() || target.level() != level) {
				continue;
			}
			// a duel's move lands only while that duel lasts; a guard's own only while it still has someone to fight
			if (p.duel ? !Duels.dueling(p.mob, target) : p.mob.getTarget() == null || Downed.isDowned(target)) {
				continue;
			}
			strike(level, p, target);
		}
	}

	// ---- the moves ---------------------------------------------------------------------------------------------------

	private static void strike(ServerLevel level, Pending p, LivingEntity target) {
		Mob mob = p.mob;
		float base = Math.min(MAX_DAMAGE, damageOf(mob)) * (p.duel ? DUEL_SCALE : 1.0F);
		Vec3 facing = horizontal(target.position().subtract(mob.position()));
		mob.getLookControl().setLookAt(target, 60.0F, 60.0F);
		switch (p.family) {
			case BLADE -> {
				List<LivingEntity> hit = foes(level, mob, target, SWEEP_REACH, facing, SWEEP_HALF);
				side(level, mob, facing, Family.BLADE);
				hurtAll(level, mob, hit, base * SWEEP, 0.4, 0.15);
				sound(level, mob, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F);
			}
			case SPEAR -> {
				if (gap(mob, target) <= LUNGE_REACH) {
					mob.setDeltaMovement(mob.getDeltaMovement().add(facing.scale(LUNGE_DASH)).add(0.0, 0.1, 0.0));
					mob.needsSync = true;
					List<LivingEntity> hit = line(level, mob, target, facing);
					Vec3 from = mob.getEyePosition().add(0.0, -0.3, 0.0);
					Trails.of(level, Family.SPEAR, from, from.add(facing.scale(LUNGE_REACH)));
					hurtAll(level, mob, hit, base * LUNGE, 0.5, 0.1);
					sound(level, mob, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F);
				}
			}
			case FIST -> {
				stomp(level, mob, target, base * STOMP);
			}
			case KNIFE, CLAW -> {
				float share = p.family == Family.KNIFE ? STAB : RAKE;
				if (gap(mob, target) <= SWEEP_REACH) {
					Vec3 from = mob.getEyePosition().add(0.0, -0.3, 0.0);
					Trails.of(level, p.family, from, target.getBoundingBox().getCenter());
					target.setInvulnerableTime(0);
					hurtAll(level, mob, List.of(target), base * share, 0.15, 0.05);
					sound(level, mob, SoundEvents.PLAYER_ATTACK_WEAK, 1.2F);
					if (p.step == 0) {
						PENDING.add(new Pending(mob, target.getUUID(), p.family, level.getGameTime() + FOLLOW_UP, p.duel, 1));
					}
				}
			}
		}
	}

	private static void stomp(ServerLevel level, Mob mob, LivingEntity target, float damage) {
		level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.1, mob.getZ(), 24, STOMP_RADIUS * 0.4, 0.05, STOMP_RADIUS * 0.4, 0.05);
		level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + 0.2, mob.getZ(), 12, STOMP_RADIUS * 0.4, 0.1, STOMP_RADIUS * 0.4, 0.3);
		sound(level, mob, SoundEvents.GENERIC_EXPLODE.value(), 0.6F);
		List<LivingEntity> hit = new ArrayList<>();
		for (LivingEntity e : around(level, mob, target, STOMP_RADIUS)) {
			if (e.onGround()) {
				hit.add(e);
			}
		}
		for (LivingEntity foe : hit) {
			if (foe.hurtServer(level, mob.damageSources().mobAttack(mob), damage) && foe.isAlive()) {
				Vec3 away = horizontal(foe.position().subtract(mob.position()));
				foe.setDeltaMovement(foe.getDeltaMovement().add(away.scale(STOMP_PUSH)).add(0.0, STOMP_LIFT, 0.0));
				foe.needsSync = true;
				foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STOMP_SLOW, 1, false, true));
			}
		}
	}

	private static void hurtAll(ServerLevel level, Mob mob, List<LivingEntity> foes, float damage, double push, double lift) {
		for (LivingEntity foe : foes) {
			if (foe.hurtServer(level, mob.damageSources().mobAttack(mob), damage) && foe.isAlive()) {
				Vec3 away = horizontal(foe.position().subtract(mob.position()));
				foe.setDeltaMovement(foe.getDeltaMovement().add(away.scale(push)).add(0.0, lift, 0.0));
				foe.needsSync = true;
			}
		}
	}

	/** The foes of a front arc: {@code target} (if in reach) and monsters beside it. */
	private static List<LivingEntity> foes(ServerLevel level, Mob mob, LivingEntity target, double reach, Vec3 facing, double halfArc) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : around(level, mob, target, reach)) {
			Vec3 to = horizontal(e.position().subtract(mob.position()));
			double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, to.dot(facing)))));
			if (e == target || angle <= halfArc) {
				out.add(e);
			}
		}
		return out;
	}

	/** The foes along a lunge: those within {@link #LUNGE_WIDTH} of the line out to {@link #LUNGE_REACH}. */
	private static List<LivingEntity> line(ServerLevel level, Mob mob, LivingEntity target, Vec3 facing) {
		List<LivingEntity> out = new ArrayList<>();
		Vec3 from = mob.position();
		for (LivingEntity e : around(level, mob, target, LUNGE_REACH + 1.0)) {
			Vec3 rel = e.position().subtract(from);
			double along = rel.x * facing.x + rel.z * facing.z;
			double across = Math.abs(rel.x * facing.z - rel.z * facing.x);
			if (e == target || along > 0.0 && along <= LUNGE_REACH + 0.5 && across <= LUNGE_WIDTH) {
				out.add(e);
			}
		}
		return out;
	}

	/** {@code target} if it's in reach, and the monsters near it: never a villager, folk, another guard or another player. */
	private static List<LivingEntity> around(ServerLevel level, Mob mob, LivingEntity target, double reach) {
		List<LivingEntity> out = new ArrayList<>();
		AABB box = mob.getBoundingBox().inflate(reach + 1.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != mob && e.isAlive() && e.isAttackable())) {
			boolean mine = e == target || e instanceof Enemy && !(e instanceof Worker) && !mob.isAlliedTo(e);
			if (mine && gap(mob, e) <= reach) {
				out.add(e);
			}
		}
		return out;
	}

	// ---- bits --------------------------------------------------------------------------------------------------------

	static float damageOf(Mob mob) {
		return mob.getAttribute(Attributes.ATTACK_DAMAGE) == null ? FALLBACK_DAMAGE : (float)mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
	}

	static double gap(LivingEntity from, LivingEntity to) {
		Vec3 eye = from.getEyePosition();
		AABB box = to.getBoundingBox();
		double x = Math.max(box.minX, Math.min(eye.x, box.maxX));
		double y = Math.max(box.minY, Math.min(eye.y, box.maxY));
		double z = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
		return eye.distanceTo(new Vec3(x, y, z));
	}

	private static Vec3 horizontal(Vec3 v) {
		Vec3 flat = new Vec3(v.x, 0.0, v.z);
		return flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, 0.0F) : flat.normalize();
	}

	/** The glint at the weapon as a move begins (the stomp's red). */
	private static void tell(ServerLevel level, Mob mob, boolean heavy) {
		Vec3 facing = Vec3.directionFromRotation(0.0F, mob.getYHeadRot());
		Vec3 at = mob.getEyePosition().add(facing.scale(0.4)).add(0.0, -0.3, 0.0);
		level.sendParticles(new DustParticleOptions(heavy ? HEAVY_TELL : TELL, 1.2F), at.x, at.y, at.z, heavy ? 8 : 5, 0.08, 0.08, 0.08, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.05);
	}

	/** The sweep's arc, drawn across the front. */
	private static void side(ServerLevel level, Mob mob, Vec3 facing, Family family) {
		Vec3 left = new Vec3(-facing.z, 0.0, facing.x).scale(1.4);
		Vec3 mid = mob.position().add(0.0, 1.1, 0.0).add(facing.scale(1.6));
		Trails.of(level, family, mid.add(left), mid.subtract(left));
	}

	private static void sound(ServerLevel level, Mob mob, net.minecraft.sounds.SoundEvent sound, float pitch) {
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundSource.HOSTILE, 0.9F, pitch);
	}
}
