package com.jaymospowers.power.wind;

import com.jaymospowers.core.gifted.GiftKit;
import com.jaymospowers.core.gifted.Gifted;
import com.jaymospowers.core.UltimateFilm;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.3) Wind for a gifted villager ({@link Gifted}): a gale that throws the foe back, an updraft that lifts them to fall,
 * a burst that clears the ground round them, and a dash away on the wind. Storm-taken (corrupted, Wind's Stormbound),
 * they call lightning down, and the storm crackles round them in the rain. A ruler's ultimate is the Tempest; out of a
 * fight they blow out the fires near them.
 * <p>
 * (0.17.12) They fly (Andy: "Wind gifted villagers should fly when they see fit, maybe a little hover when they can"): in a
 * fight they soar up over the foe and hover there, casting down, or take wing away from one too close; out of a fight
 * they hover a little, now and then fly off to somewhere near; and they never fall far, the wind catches them.
 */
final class WindGift implements GiftKit {
	/** The gale: its reach, damage, throw (horizontal, up) and cooldown (ticks). */
	static final double GALE_RANGE = 16.0, GALE_THROW = 1.3, GALE_LIFT = 0.3;
	static final float GALE_DAMAGE = 3.0F;
	static final int GALE_COOLDOWN = 60;
	/** The updraft: its reach, lift and cooldown. */
	static final double UPDRAFT_RANGE = 12.0, UPDRAFT_LIFT = 1.1;
	static final int UPDRAFT_COOLDOWN = 160;
	/** The burst: its radius, throw, damage and cooldown. */
	static final double BURST_RADIUS = 4.0, BURST_THROW = 1.1, BURST_LIFT = 0.4;
	static final float BURST_DAMAGE = 2.0F;
	static final int BURST_COOLDOWN = 90;
	/** The dash: how hard and high they go, and its cooldown. */
	static final double DASH_SPEED = 1.4, DASH_LIFT = 0.45;
	static final int DASH_COOLDOWN = 120;
	/** Storm-taken: lightning's reach, damage and cooldown; the crackle in the rain this often (in passives). */
	static final double THUNDER_RANGE = 20.0;
	static final float THUNDER_DAMAGE = 8.0F;
	static final int THUNDER_COOLDOWN = 200;
	static final float CRACKLE_CHANCE = 0.3F;

	/** A ruler's ultimate, the Tempest: a whirlwind round the foe that throws everything near it high (its radius, lift, damage). */
	static final double TEMPEST_RADIUS = 6.0, TEMPEST_LIFT = 1.6;
	static final float TEMPEST_DAMAGE = 6.0F;
	static final int TEMPEST_FILM = 50;
	/** Everyday: fires this near are blown out. */
	static final int DOUSE_RADIUS = 5;
	/** Soaring in a fight: how high over the ground (or the foe), how long they hover (ticks), and its cooldown. */
	static final double SOAR_HEIGHT = 4.0;
	static final int SOAR_TICKS = 100, SOAR_COOLDOWN = 300;
	/** Taking wing from a foe: how far off and how high they fly, and its cooldown. */
	static final double WING_DISTANCE = 14.0, WING_HEIGHT = 5.0;
	static final int WING_COOLDOWN = 400;
	/** Out of a fight: the chance (when there's no fire to put out) of a little hover, how high and how long. */
	static final float HOVER_CHANCE = 0.5F;
	static final double HOVER_HEIGHT = 1.5;
	static final int HOVER_TICKS = 60;
	/** Out of a fight: the chance of a flight off to somewhere near (this far off, at most this much higher or lower). */
	static final float FLY_CHANCE = 0.35F;
	static final int FLY_MIN = 10, FLY_MAX = 20, FLY_CLIMB = 12;
	static final double FLY_HEIGHT = 6.0;
	/** How fast they fly (blocks a tick, across and up), and the longest any flight lasts. */
	static final double FLY_SPEED = 0.32, CLIMB_SPEED = 0.25;
	static final int FLIGHT_MAX_TICKS = 240;
	/** The wind catches a fall this long (blocks), and lets them down this gently (ticks of slow falling). */
	static final double CATCH_FALL = 4.0;
	static final int LANDING_TICKS = 60;
	/** On anyone this kit has lifted off the ground (so a flight cut short by unloading is ended when they're back). */
	static final String ALOFT = "jaymospowers_wind_aloft";

	/** A flight: where to (null to hover where they are), how high (world y), until when (game time), and a foe to watch. */
	private record Flight(@Nullable Vec3 dest, double height, long until, @Nullable LivingEntity foe) {
	}

	private static final Map<PathfinderMob, Flight> FLIGHTS = new WeakHashMap<>();

	static void init() {
		Gifted.register(new WindGift());
		// (0.17.103) a Rubber-folk who swallowed one: Flight, as an updraft
		com.jaymospowers.core.SwallowedPower.register(WindPower.ID, 80, WindGift::swallowed);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FLIGHTS.clear());
	}

	@Override
	public Identifier power() {
		return WindPower.ID;
	}

	@Override
	public List<Move> moves() {
		return List.of(
			Move.corrupt("thunder", Kind.STRIKE, THUNDER_COOLDOWN, 3.0, THUNDER_RANGE, WindGift::thunder),
			Move.of("gale", Kind.STRIKE, GALE_COOLDOWN, 2.0, GALE_RANGE, WindGift::gale),
			Move.of("burst", Kind.CLOSE, BURST_COOLDOWN, 0.0, BURST_RADIUS, WindGift::burst),
			Move.of("updraft", Kind.STRIKE, UPDRAFT_COOLDOWN, 2.0, UPDRAFT_RANGE, WindGift::updraft),
			Move.of("soar", Kind.SUPPORT, SOAR_COOLDOWN, 3.0, GALE_RANGE, WindGift::soar),
			Move.of("wing", Kind.ESCAPE, WING_COOLDOWN, 0.0, 5.0, WindGift::wing),
			Move.of("dash", Kind.ESCAPE, DASH_COOLDOWN, 0.0, 5.0, WindGift::dash));
	}

	// ---- flight -----------------------------------------------------------------------------------------------------

	/** Up over the foe, to hover and cast down at them. */
	private static boolean soar(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (FLIGHTS.containsKey(caster) || caster.isInWater()) {
			return false;
		}
		double height = Math.max(caster.getY() + SOAR_HEIGHT, foe.getY() + 1.5);
		if (!room(level, caster, height - caster.getY())) {
			return false;
		}
		takeOff(level, caster, new Flight(null, height, level.getGameTime() + SOAR_TICKS, foe));
		return true;
	}

	/** Off and away from a foe too close, on the wind. */
	private static boolean wing(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (FLIGHTS.containsKey(caster) || caster.isInWater() || !room(level, caster, WING_HEIGHT)) {
			return false;
		}
		Vec3 away = caster.position().subtract(foe.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().horizontal().reverse() : away;
		Vec3 dest = caster.position().add(away.normalize().scale(WING_DISTANCE));
		takeOff(level, caster, new Flight(dest, caster.getY() + WING_HEIGHT, level.getGameTime() + FLIGHT_MAX_TICKS, null));
		return true;
	}

	/** Whether there's air {@code up} blocks over {@code mob}'s head to rise into. */
	private static boolean room(ServerLevel level, PathfinderMob mob, double up) {
		return level.noCollision(mob, mob.getBoundingBox().expandTowards(0.0, up, 0.0));
	}

	private static void takeOff(ServerLevel level, PathfinderMob mob, Flight flight) {
		FLIGHTS.put(mob, flight);
		mob.addTag(ALOFT);
		mob.setNoGravity(true);
		mob.getNavigation().stop();
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, mob.getX(), mob.getY(), mob.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.NEUTRAL, 1.0F, 1.1F);
	}

	/** Down again: the wind lets them down gently. */
	private static void land(PathfinderMob mob) {
		FLIGHTS.remove(mob);
		mob.removeTag(ALOFT);
		mob.setNoGravity(false);
		mob.fallDistance = 0.0;
		if (!mob.onGround()) {
			mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, LANDING_TICKS, 0, false, false));
		}
	}

	@Override
	public void tick(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		Flight flight = FLIGHTS.get(mob);
		if (flight == null) {
			if (mob.entityTags().contains(ALOFT)) {
				land(mob);
			}
			catchFall(level, mob);
			return;
		}
		long now = level.getGameTime();
		if (now >= flight.until || !mob.isAlive() || mob.isInWater() || mob.isPassenger() || mob.entityTags().contains("jaymospowers_downed")) {
			land(mob);
			return;
		}
		Vec3 at = mob.position();
		double tx = at.x, tz = at.z;
		if (flight.dest != null) {
			Vec3 across = flight.dest.subtract(at).horizontal();
			if (across.lengthSqr() < 2.25) {
				land(mob);
				return;
			}
			tx = flight.dest.x;
			tz = flight.dest.z;
			mob.setYRot((float)(Mth.atan2(across.z, across.x) * Mth.RAD_TO_DEG) - 90.0F);
			mob.yBodyRot = mob.getYRot();
			mob.yHeadRot = mob.getYRot();
		} else if (flight.foe != null && flight.foe.isAlive()) {
			mob.getLookControl().setLookAt(flight.foe, 30.0F, 30.0F);
		}
		double ty = flight.height + Math.sin(now * 0.15) * 0.15;
		Vec3 across = new Vec3(tx - at.x, 0.0, tz - at.z);
		if (across.lengthSqr() > FLY_SPEED * FLY_SPEED) {
			across = across.normalize().scale(FLY_SPEED);
		}
		double up = Mth.clamp(ty - at.y, -CLIMB_SPEED, CLIMB_SPEED);
		mob.setDeltaMovement(across.x, up, across.z);
		mob.fallDistance = 0.0;
		mob.getNavigation().stop();
		if (now % 4 == 0) {
			level.sendParticles(ParticleTypes.SMALL_GUST, at.x, at.y - 0.1, at.z, 1, 0.2, 0.05, 0.2, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 1, 0.2, 0.05, 0.2, 0.01);
		}
	}

	/** The wind catches them in a long fall. */
	private static void catchFall(ServerLevel level, PathfinderMob mob) {
		if (mob.onGround() || mob.fallDistance < CATCH_FALL || mob.getDeltaMovement().y > -0.5 || mob.isInWater()) {
			return;
		}
		mob.fallDistance = 0.0;
		mob.setDeltaMovement(mob.getDeltaMovement().multiply(1.0, 0.2, 1.0));
		mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, LANDING_TICKS, 0, false, false));
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, mob.getX(), mob.getY(), mob.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.NEUTRAL, 0.8F, 1.3F);
	}

	/** Out of a fight: a little hover where they stand, or a flight off to somewhere near. */
	private static void wander(ServerLevel level, PathfinderMob mob) {
		if (FLIGHTS.containsKey(mob) || !mob.onGround() || mob.isInWater() || mob.isPassenger()) {
			return;
		}
		long now = level.getGameTime();
		if (mob.getRandom().nextFloat() < HOVER_CHANCE) {
			if (room(level, mob, HOVER_HEIGHT)) {
				takeOff(level, mob, new Flight(null, mob.getY() + HOVER_HEIGHT, now + HOVER_TICKS, null));
			}
			return;
		}
		if (mob.getRandom().nextFloat() >= FLY_CHANCE) {
			return;
		}
		double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
		double far = FLY_MIN + mob.getRandom().nextDouble() * (FLY_MAX - FLY_MIN);
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			BlockPos.containing(mob.getX() + Math.cos(angle) * far, mob.getY(), mob.getZ() + Math.sin(angle) * far));
		if (Math.abs(ground.getY() - mob.getY()) > FLY_CLIMB || !level.getFluidState(ground.below()).isEmpty() || !room(level, mob, FLY_HEIGHT)) {
			return;
		}
		Vec3 dest = Vec3.atBottomCenterOf(ground);
		takeOff(level, mob, new Flight(dest, Math.max(mob.getY(), dest.y) + FLY_HEIGHT, now + FLIGHT_MAX_TICKS, null));
	}

	private static boolean gale(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 from = caster.getEyePosition(), to = foe.getBoundingBox().getCenter();
		Vec3 step = to.subtract(from);
		int steps = Math.max(1, (int)(step.length() * 2.0));
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.add(step.scale((double)i / steps));
			level.sendParticles(ParticleTypes.SMALL_GUST, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		level.sendParticles(ParticleTypes.GUST, to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.NEUTRAL, 0.8F, 1.4F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.NEUTRAL, 0.8F, 1.2F);
		foe.hurtServer(level, level.damageSources().mobAttack(caster), GALE_DAMAGE);
		WindStaff.push(foe, step.horizontal().normalize().scale(GALE_THROW), GALE_LIFT);
		return true;
	}

	private static boolean updraft(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (!foe.onGround()) {
			return false;
		}
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, foe.getX(), foe.getY(), foe.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, foe.getX(), foe.getY() + 0.1, foe.getZ(), 16, 0.4, 0.1, 0.4, 0.08);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.NEUTRAL, 1.0F, 1.2F);
		WindStaff.push(foe, Vec3.ZERO, UPDRAFT_LIFT);
		return true;
	}

	private static boolean burst(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = caster.position();
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, c.x, c.y + 0.5, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(BURST_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy))) {
			Vec3 away = e.position().subtract(c).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().horizontal() : away;
			e.hurtServer(level, level.damageSources().mobAttack(caster), BURST_DAMAGE);
			WindStaff.push(e, away.normalize().scale(BURST_THROW), BURST_LIFT);
		}
		return true;
	}

	private static boolean dash(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (!caster.onGround()) {
			return false;
		}
		Vec3 away = caster.position().subtract(foe.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().horizontal().reverse() : away;
		level.sendParticles(ParticleTypes.CLOUD, caster.getX(), caster.getY() + 0.1, caster.getZ(), 12, 0.3, 0.1, 0.3, 0.05);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.NEUTRAL, 1.0F, 1.2F);
		WindStaff.push(caster, away.normalize().scale(DASH_SPEED), DASH_LIFT);
		caster.fallDistance = 0.0;
		return true;
	}

	private static boolean thunder(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (!level.canSeeSky(foe.blockPosition())) {
			return false;
		}
		bolt(level, foe.position());
		foe.hurtServer(level, level.damageSources().lightningBolt(), THUNDER_DAMAGE);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, caster.getX(), caster.getY(0.7), caster.getZ(), 14, 0.3, 0.5, 0.3, 0.2);
		return true;
	}

	/** A bolt that only looks and sounds like one (it sets no town alight; its harm is the move's own). */
	private static void bolt(ServerLevel level, Vec3 at) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
	}

	@Override
	public void passive(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		if (gift.corrupted() && level.isRainingAt(mob.blockPosition()) && mob.getRandom().nextFloat() < CRACKLE_CHANCE) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY(0.6), mob.getZ(), 4, 0.3, 0.4, 0.3, 0.1);
		}
	}

	@Override
	public void everyday(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		// a breath of wind round them, and any fire close by blown out
		BlockPos at = mob.blockPosition();
		boolean doused = false;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-DOUSE_RADIUS, -2, -DOUSE_RADIUS), at.offset(DOUSE_RADIUS, 3, DOUSE_RADIUS))) {
			if (level.getBlockState(p).getBlock() instanceof BaseFireBlock) {
				level.removeBlock(p, false);
				level.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.02);
				doused = true;
			}
		}
		level.sendParticles(ParticleTypes.SMALL_GUST, mob.getX(), mob.getY(0.5), mob.getZ(), 3, 0.6, 0.4, 0.6, 0.0);
		if (doused) {
			level.playSound(null, at, SoundEvents.BREEZE_WHIRL, SoundSource.NEUTRAL, 0.8F, 1.4F);
			level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
		} else {
			wander(level, mob);
		}
	}

	@Override
	public Ultimate ultimate() {
		return new Ultimate(UltimateFilm.SKYWARD, TEMPEST_FILM, false, WindGift::tempest);
	}

	private static boolean tempest(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = foe.position();
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, c.x, c.y + 1.0, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		for (int i = 0; i < 40; i++) {
			double a = i * Math.PI / 10.0, r = TEMPEST_RADIUS * (i % 10) / 10.0;
			level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * r, c.y + i * 0.15, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.02);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.NEUTRAL, 2.0F, 0.6F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BREEZE_WHIRL, SoundSource.NEUTRAL, 2.0F, 0.6F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, foe.getBoundingBox().inflate(TEMPEST_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy || e.getLastHurtByMob() == caster || caster.getLastHurtByMob() == e))) {
			e.hurtServer(level, level.damageSources().mobAttack(caster), TEMPEST_DAMAGE);
			WindStaff.push(e, Vec3.ZERO, TEMPEST_LIFT);
		}
		if (gift.corrupted()) {
			bolt(level, c);
		}
		return true;
	}

	@Override
	public void corrupted(ServerLevel level, PathfinderMob mob) {
		bolt(level, mob.position());
	}

	/** (0.17.103) Wind's R for a Rubber-folk who swallowed it ({@link com.jaymospowers.core.SwallowedPower}): an updraft lifts them, and they come down soft. */
	static boolean swallowed(net.minecraft.server.level.ServerPlayer player) {
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 30, 0.5, 0.1, 0.5, 0.15);
		level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 0.5, player.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 0.9F);
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x, 1.3, v.z);
		player.syncVelocity = true;
		player.resetFallDistance();
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 80, 0, false, false, true));
		return true;
	}
}
