package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The ki techniques:
 * <ul>
 * <li>G, Ki Blast: a fast bolt of ki from the palm. Cheap and quick; it hurts and knocks back, and fizzles on stone.</li>
 * <li>V (hold), Ki Wave: cup your hands and gather it ({@link #WAVE_FULL} ticks to full), let go and it fires as a beam
 * through everything in a line, ending in a blast. The fuller, the wider, the harder, the dearer. A full wave from a
 * Super breaks blocks (if mob griefing is on).</li>
 * <li>B, Flash Step: gone in a blink, up to {@link #STEP_RANGE} blocks the way you look; aimed at a creature, behind
 * it, facing its back.</li>
 * </ul>
 */
final class ZFighterKi {
	/** Ki Blast and Ki Wave's damage: ordinary, armour applies. */
	static final ResourceKey<DamageType> KI = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosPowers.id("zfighter_ki"));

	// ---- Ki Blast ----
	static final float BLAST_KI = 8.0F;
	static final int BLAST_COOLDOWN = 6;
	static final float BLAST_DAMAGE = 5.0F;
	static final double BLAST_SPEED = 1.6;
	static final int BLAST_LIFE = 40;
	static final float BLAST_EXHAUSTION = 0.5F;

	// ---- Ki Wave ----
	static final float WAVE_MIN_KI = 25.0F;
	static final float WAVE_MAX_KI = 60.0F;
	static final int WAVE_FULL = 60;
	static final int WAVE_COOLDOWN = 100;
	static final float WAVE_DAMAGE_MIN = 8.0F;
	static final float WAVE_DAMAGE_MAX = 24.0F;
	static final double WAVE_RANGE = 40.0;
	static final double WAVE_RADIUS_MIN = 0.8;
	static final double WAVE_RADIUS_MAX = 1.8;
	static final float WAVE_BLAST_MIN = 1.0F;
	static final float WAVE_BLAST_MAX = 3.0F;
	static final float WAVE_EXHAUSTION = 2.0F;
	/** How long the beam stays in the air (ticks, just to be seen). */
	static final int BEAM_TICKS = 12;

	// ---- Flash Step ----
	static final float STEP_KI = 15.0F;
	static final int STEP_COOLDOWN = 30;
	static final double STEP_RANGE = 10.0;
	static final double STEP_TARGET_RANGE = 16.0;
	static final float STEP_EXHAUSTION = 0.8F;

	private static final int BLAST_COLOR = 0xFFC85A;
	private static final int WAVE_COLOR = 0x8FD3FF;

	private static final class Bolt {
		final ServerLevel level;
		final UUID owner;
		Vec3 pos;
		final Vec3 velocity;
		final float damage;
		final int color;
		int age;

		Bolt(ServerLevel level, UUID owner, Vec3 pos, Vec3 velocity, float damage, int color) {
			this.level = level;
			this.owner = owner;
			this.pos = pos;
			this.velocity = velocity;
			this.damage = damage;
			this.color = color;
		}
	}

	private record Beam(ServerLevel level, Vec3 from, Vec3 to, double radius, int color, int[] age) {
	}

	private static final List<Bolt> BOLTS = new ArrayList<>();
	private static final List<Beam> BEAMS = new ArrayList<>();

	private ZFighterKi() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterKi::tick);
	}

	static void clear() {
		BOLTS.clear();
		BEAMS.clear();
	}

	static DamageSource source(ServerPlayer player) {
		return player.damageSources().source(KI, player);
	}

	// ---------------------------------------------------------------- Ki Blast

	static boolean blast(ServerPlayer player, ZFighterPower.Live live) {
		if (!ZFighterPower.spend(player, live, BLAST_KI)) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1.0F);
		// from the right palm, a little ahead
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-6 ? Vec3.ZERO : right.normalize();
		Vec3 from = player.getEyePosition().add(look.scale(0.8)).add(right.scale(0.3)).add(0.0, -0.25, 0.0);
		float damage = BLAST_DAMAGE * ZFighterPower.kiPower(player, live);
		BOLTS.add(new Bolt(level, player.getUUID(), from, look.scale(BLAST_SPEED), damage, ZFighterPower.FORM_COLOR[live.form]));
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(BLAST_EXHAUSTION);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.5F);
		return true;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
			Bolt bolt = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(bolt.owner);
			if (owner == null || owner.level() != bolt.level || !flyBolt(owner, bolt)) {
				it.remove();
			}
		}
		for (Iterator<Beam> it = BEAMS.iterator(); it.hasNext(); ) {
			Beam beam = it.next();
			drawBeam(beam);
			if (++beam.age()[0] >= BEAM_TICKS) {
				it.remove();
			}
		}
	}

	/** One tick of a bolt's flight. False when it's spent. */
	private static boolean flyBolt(ServerPlayer owner, Bolt bolt) {
		ServerLevel level = bolt.level;
		Vec3 from = bolt.pos;
		Vec3 to = from.add(bolt.velocity);
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(owner, from, end, new AABB(from, end).inflate(1.0),
			e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e != owner,
			from.distanceToSqr(end));
		DustParticleOptions dust = new DustParticleOptions(bolt.color == ZFighterPower.FORM_COLOR[0] ? BLAST_COLOR : bolt.color, 1.8F);
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			target.setInvulnerableTime(0);
			target.hurtServer(level, source(owner), bolt.damage);
			push(target, bolt.velocity, 0.6, 0.2);
			pop(level, hit.getLocation(), dust);
			return false;
		}
		if (block.getType() != HitResult.Type.MISS) {
			pop(level, block.getLocation(), dust);
			return false;
		}
		for (int i = 0; i < 3; i++) {
			Vec3 at = from.add(bolt.velocity.scale(i / 3.0));
			level.sendParticles(dust, true, false, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.0);
		}
		level.sendParticles(ParticleTypes.END_ROD, true, false, to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
		bolt.pos = to;
		return ++bolt.age < BLAST_LIFE;
	}

	private static void pop(ServerLevel level, Vec3 at, DustParticleOptions dust) {
		level.sendParticles(dust, true, false, at.x, at.y, at.z, 12, 0.25, 0.25, 0.25, 0.1);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.3);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.35F, 1.9F);
	}

	// ---------------------------------------------------------------- Ki Wave

	static void startWave(ServerPlayer player, ZFighterPower.Live live) {
		if (live.ki < WAVE_MIN_KI && !player.isCreative()) {
			ZFighterPower.spend(player, live, WAVE_MIN_KI);
			return;
		}
		live.waveFrom = player.level().getGameTime();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.8F);
	}

	/** The charge (0-1) of a wave gathered since {@code from}, held back to what his ki can pay for. */
	private static float charge(ServerPlayer player, ZFighterPower.Live live, long now) {
		float charge = Mth.clamp((now - live.waveFrom) / (float)WAVE_FULL, 0.0F, 1.0F);
		if (!player.isCreative()) {
			float affordable = (live.ki - WAVE_MIN_KI) / (WAVE_MAX_KI - WAVE_MIN_KI);
			charge = Math.min(charge, Mth.clamp(affordable, 0.0F, 1.0F));
		}
		return charge;
	}

	/** While V is held: ki gathers between the cupped hands. */
	static void tickWave(ServerPlayer player, ZFighterPower.Live live) {
		if (live.waveFrom < 0) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		float charge = charge(player, live, now);
		Vec3 look = player.getViewVector(1.0F);
		Vec3 hands = player.getEyePosition().add(look.scale(0.7)).add(0.0, -0.45, 0.0);
		RandomSource random = level.getRandom();
		DustParticleOptions dust = new DustParticleOptions(live.superOn() ? ZFighterPower.FORM_COLOR[live.form] : WAVE_COLOR, 0.8F + charge * 1.2F);
		for (int i = 0; i < 3; i++) {
			double ox = (random.nextDouble() - 0.5) * 1.6;
			double oy = (random.nextDouble() - 0.5) * 1.6;
			double oz = (random.nextDouble() - 0.5) * 1.6;
			level.sendParticles(dust, true, false, hands.x + ox, hands.y + oy, hands.z + oz, 0, -ox, -oy, -oz, 0.12);
		}
		level.sendParticles(dust, true, false, hands.x, hands.y, hands.z, 1 + (int)(charge * 3), 0.05 + charge * 0.1, 0.05 + charge * 0.1,
			0.05 + charge * 0.1, 0.0);
		if (charge >= 1.0F && now % 3 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, hands.x, hands.y, hands.z, 2, 0.2, 0.2, 0.2, 0.2);
		}
		if ((now - live.waveFrom) % 10 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F,
				0.8F + charge);
		}
	}

	/** V let go: the wave fires (true), or there wasn't one (false). */
	static boolean releaseWave(ServerPlayer player, ZFighterPower.Live live) {
		if (live.waveFrom < 0) {
			return false;
		}
		ServerLevel level = player.level();
		float charge = charge(player, live, level.getGameTime());
		live.waveFrom = -1L;
		float cost = Mth.lerp(charge, WAVE_MIN_KI, WAVE_MAX_KI);
		if (!ZFighterPower.spend(player, live, cost)) {
			return false;
		}
		fireWave(player, live, charge);
		return true;
	}

	private static void fireWave(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 from = player.getEyePosition().add(look.scale(0.7)).add(0.0, -0.3, 0.0);
		BlockHitResult block = level.clip(new ClipContext(from, from.add(look.scale(WAVE_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
			player));
		Vec3 to = block.getLocation();
		double radius = Mth.lerp(charge, WAVE_RADIUS_MIN, WAVE_RADIUS_MAX);
		float damage = Mth.lerp(charge, WAVE_DAMAGE_MIN, WAVE_DAMAGE_MAX) * ZFighterPower.kiPower(player, live);
		DamageSource source = source(player);
		Set<Entity> struck = new HashSet<>();
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(radius + 1.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			if (distanceToSegment(target.getBoundingBox().getCenter(), from, to) > radius + target.getBbWidth() * 0.5 || !struck.add(target)) {
				continue;
			}
			target.setInvulnerableTime(0);
			target.hurtServer(level, source, damage);
			push(target, look, 0.8 + charge * 1.2, 0.3);
		}
		boolean super_ = live.superOn();
		BEAMS.add(new Beam(level, from, to, radius, super_ ? ZFighterPower.FORM_COLOR[live.form] : WAVE_COLOR, new int[1]));
		float power = Mth.lerp(charge, WAVE_BLAST_MIN, WAVE_BLAST_MAX) * (super_ ? 1.3F : 1.0F);
		// a full wave from a Super tears the ground (as a mob would: mob griefing decides); anything less only hurts
		Level.ExplosionInteraction interaction = super_ && charge >= 1.0F ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
		level.explode(player, source, null, to.x, to.y, to.z, power, false, interaction);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		if (!player.onGround()) {
			// the recoil pushes a fighter in the air back
			player.push(look.scale(-0.4 * charge));
			player.syncVelocity = true;
		}
		if (!player.isCreative()) {
			player.causeFoodExhaustion(WAVE_EXHAUSTION * (1.0F + charge));
		}
	}

	private static void drawBeam(Beam beam) {
		ServerLevel level = beam.level();
		Vec3 path = beam.to().subtract(beam.from());
		double length = path.length();
		if (length < 1.0E-3) {
			return;
		}
		Vec3 dir = path.scale(1.0 / length);
		float fade = 1.0F - beam.age()[0] / (float)BEAM_TICKS;
		double radius = beam.radius() * (0.4 + 0.6 * fade);
		DustParticleOptions core = new DustParticleOptions(0xFFFFFF, 2.5F);
		DustParticleOptions glow = new DustParticleOptions(beam.color(), 3.0F);
		for (double d = 0.0; d <= length; d += 0.6) {
			Vec3 at = beam.from().add(dir.scale(d));
			level.sendParticles(core, true, false, at.x, at.y, at.z, 1, radius * 0.15, radius * 0.15, radius * 0.15, 0.0);
			level.sendParticles(glow, true, false, at.x, at.y, at.z, 2, radius * 0.4, radius * 0.4, radius * 0.4, 0.0);
		}
	}

	private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double lengthSqr = ab.lengthSqr();
		double t = lengthSqr < 1.0E-6 ? 0.0 : Mth.clamp(point.subtract(a).dot(ab) / lengthSqr, 0.0, 1.0);
		return point.distanceTo(a.add(ab.scale(t)));
	}

	// ---------------------------------------------------------------- Flash Step

	static boolean step(ServerPlayer player, ZFighterPower.Live live) {
		if (!player.isCreative() && live.ki < STEP_KI) {
			return ZFighterPower.spend(player, live, STEP_KI);
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 start = player.position();
		Vec3 dest = null;
		float yaw = player.getYRot();
		float pitch = player.getXRot();

		Vec3 reach = eye.add(look.scale(STEP_TARGET_RANGE));
		EntityHitResult aimed = ProjectileUtil.getEntityHitResult(player, eye, reach, player.getBoundingBox().expandTowards(look.scale(STEP_TARGET_RANGE))
			.inflate(1.0), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, STEP_TARGET_RANGE * STEP_TARGET_RANGE);
		if (aimed != null && aimed.getEntity() instanceof LivingEntity target && player.hasLineOfSight(target)) {
			// behind it, at its back
			float facing = target.getYRot() * Mth.DEG_TO_RAD;
			Vec3 forward = new Vec3(-Mth.sin(facing), 0.0, Mth.cos(facing));
			double gap = target.getBbWidth() * 0.5 + 0.9;
			for (Vec3 candidate : new Vec3[] {target.position().subtract(forward.scale(gap)),
				target.position().add(target.position().subtract(start).multiply(1.0, 0.0, 1.0).normalize().scale(gap))}) {
				if (free(player, candidate)) {
					dest = candidate;
					break;
				}
			}
			if (dest != null) {
				Vec3 toTarget = target.getEyePosition().subtract(dest.add(0.0, player.getEyeHeight(), 0.0));
				yaw = (float)(Mth.atan2(toTarget.z, toTarget.x) * Mth.RAD_TO_DEG) - 90.0F;
				pitch = (float)(-Mth.atan2(toTarget.y, Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z)) * Mth.RAD_TO_DEG);
			}
		}
		if (dest == null) {
			// the furthest free spot along the look, up to STEP_RANGE
			for (double d = 0.25; d <= STEP_RANGE; d += 0.25) {
				Vec3 candidate = start.add(look.scale(d));
				if (!free(player, candidate)) {
					break;
				}
				dest = candidate;
			}
			if (dest == null || dest.distanceToSqr(start) < 1.0) {
				return false;
			}
		}
		if (!ZFighterPower.spend(player, live, STEP_KI)) {
			return false;
		}
		// the afterimage: a column of fading motes where he stood
		DustParticleOptions image = ZFighterPower.dust(live);
		for (double h = 0.1; h < player.getBbHeight(); h += 0.3) {
			level.sendParticles(image, true, false, start.x, start.y + h, start.z, 3, 0.15, 0.05, 0.15, 0.0);
		}
		level.sendParticles(ParticleTypes.POOF, start.x, start.y + 1.0, start.z, 6, 0.2, 0.4, 0.2, 0.02);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.7F, 1.8F);
		live.stepUntil = level.getGameTime() + 10;
		player.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), yaw, pitch, false);
		player.resetFallDistance();
		level.sendParticles(ParticleTypes.CLOUD, dest.x, dest.y + 0.5, dest.z, 6, 0.2, 0.2, 0.2, 0.02);
		level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.6F);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(STEP_EXHAUSTION);
		}
		return true;
	}

	private static boolean free(ServerPlayer player, Vec3 feet) {
		return player.level().noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())));
	}

	// ---------------------------------------------------------------- shared

	/** Pushes {@code entity} along {@code dir} (horizontal strength, then up), less for knockback-resistant creatures. */
	static void push(LivingEntity entity, Vec3 dir, double horizontal, double up) {
		double resist = 1.0 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		if (resist <= 0.0) {
			return;
		}
		Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
		flat = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
		entity.push(flat.x * horizontal * resist, up * resist, flat.z * horizontal * resist);
		entity.syncVelocity = true;
	}
}
