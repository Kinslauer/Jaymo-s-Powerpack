package com.jaymospowers.power.gravity;

import com.jaymospowers.core.Fate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gravity Wave (B held, then let go; Andy, 2026-10-21: "a cone AoE in front of me shooting out a cone of gravitational
 * waves, could carve up terrain"). A fan of gravitic pressure rolls out the way you look, from your chest to
 * {@link #RANGE_MIN}-{@link #RANGE_MAX} blocks with the charge, over {@link #TRAVEL_TICKS}. It's wide across
 * ({@link #HALF_WIDTH_DEG} each side) and flatter up and down ({@link #HALF_HEIGHT_DEG}), so aimed level it cuts a
 * trench that deepens as it goes. Whatever it passes is hurt, thrown back along it and slowed; the ground it passes
 * gives way, the softer the more readily, harder blocks only near you and with more charge (never anything unbreakable,
 * with contents, or that you couldn't break yourself). Collapse makes it reach further and hit harder.
 */
final class GravityWave {
	/** How far it reaches (blocks) with no charge and a full one, and how long it takes to get there (ticks). */
	static final double RANGE_MIN = 8.0, RANGE_MAX = 16.0;
	static final int TRAVEL_TICKS = 10;
	/** The fan's spread: degrees either side, and up and down, of where you look. */
	static final double HALF_WIDTH_DEG = 35.0, HALF_HEIGHT_DEG = 14.0;
	/** It's never thinner than this up and down (blocks), so close by it still has some body. */
	static final double MIN_THICKNESS = 1.0;
	/** Damage, with the charge (half-hearts), less toward the far end by up to {@code DAMAGE_FALLOFF}. */
	static final float DAMAGE_MIN = 4.0F, DAMAGE_MAX = 12.0F, DAMAGE_FALLOFF = 0.4F;
	/** The throw along the wave (blocks/tick), with the charge, and how much it lifts. */
	static final double SHOVE_MIN = 1.0, SHOVE_MAX = 2.2, SHOVE_UP = 0.35;
	/** Slowed this long, this hard, by the weight of it. */
	static final int SLOW_TICKS = 40, SLOW_LEVEL = 2;
	/**
	 * Carving: the hardest block it can break, with the charge (dirt 0.5, stone 1.5, deepslate and ores 3), weakening
	 * by {@code CARVE_FALLOFF} toward its far end; this share drop as items; at most this many a tick.
	 */
	static final float CARVE_HARDNESS_MIN = 0.9F, CARVE_HARDNESS_MAX = 3.2F, CARVE_FALLOFF = 0.5F;
	static final float CARVE_DROP_CHANCE = 0.15F;
	static final int CARVE_MAX_PER_TICK = 220;
	/** Collapse: this much further. (It hits {@link GravityPower#COLLAPSE_DAMAGE} times harder.) */
	static final double COLLAPSE_RANGE_BONUS = 3.0;

	private static final DustParticleOptions WAVE = new DustParticleOptions(0xA070FF, 1.8F);
	private static final DustParticleOptions DARK = new DustParticleOptions(0x1A0F2E, 1.4F);

	private static final class Wave {
		final ResourceKey<Level> dimension;
		final UUID owner;
		final Vec3 from, dir, right, up;
		final double range;
		final float charge, damage;
		final long start;
		final Set<Integer> hit = new HashSet<>();
		/** The blocks to carve, by the tick the wave reaches them. */
		final List<List<BlockPos>> carve = new ArrayList<>();

		Wave(ResourceKey<Level> dimension, UUID owner, Vec3 from, Vec3 dir, Vec3 right, double range, float charge, float damage, long start) {
			this.dimension = dimension;
			this.owner = owner;
			this.from = from;
			this.dir = dir;
			this.right = right;
			this.up = right.cross(dir).normalize();
			this.range = range;
			this.charge = charge;
			this.damage = damage;
			this.start = start;
			for (int i = 0; i < TRAVEL_TICKS; i++) {
				this.carve.add(new ArrayList<>());
			}
		}

		/** How far along the wave {@code p} is (blocks), or -1 if it's outside the fan. {@code slack}: blocks added round it. */
		double along(Vec3 p, double slack) {
			Vec3 rel = p.subtract(this.from);
			double along = rel.dot(this.dir);
			if (along <= 0.0 || along > this.range + slack) {
				return -1.0;
			}
			double across = Math.abs(rel.dot(this.right)), upDown = Math.abs(rel.dot(this.up));
			if (across > along * Math.tan(Math.toRadians(HALF_WIDTH_DEG)) + slack
				|| upDown > Math.max(MIN_THICKNESS, along * Math.tan(Math.toRadians(HALF_HEIGHT_DEG))) + slack) {
				return -1.0;
			}
			return along;
		}
	}

	private static final List<Wave> WAVES = new ArrayList<>();

	private GravityWave() {
	}

	/** How far a wave reaches at {@code charge} (before Collapse's bonus). */
	static double range(float charge) {
		return Mth.lerp(charge, RANGE_MIN, RANGE_MAX);
	}

	/** B let go after a hold: the wave sets off. The caller has checked the cooldown and sets it after. */
	static void fire(ServerPlayer player, float charge) {
		ServerLevel level = player.level();
		boolean collapsed = Fate.isCorrupted(player);
		double range = range(charge) + (collapsed ? COLLAPSE_RANGE_BONUS : 0.0);
		float damage = Mth.lerp(charge, DAMAGE_MIN, DAMAGE_MAX) * (collapsed ? GravityPower.COLLAPSE_DAMAGE : 1.0F);
		Vec3 dir = player.getViewVector(1.0F).normalize();
		// across: square to where you look, level with the ground (straight up or down, square to where you face)
		Vec3 right = dir.cross(new Vec3(0.0, 1.0, 0.0));
		if (right.lengthSqr() < 1.0E-4) {
			double yaw = Math.toRadians(player.getYRot());
			right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
		}
		right = right.normalize();
		Vec3 from = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
		Wave wave = new Wave(level.dimension(), player.getUUID(), from, dir, right, range, charge, damage, level.getGameTime());
		plan(level, player, wave);
		WAVES.add(wave);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 0.55F + 0.2F * (1.0F - charge));
		level.playSound(null, from.x, from.y, from.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.6F);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		com.jaymospowers.core.Shakes.heavy(player, from, range * 1.5, 0.8F + 1.0F * charge);
	}

	/** Every block in the fan that might give way, sorted into the tick the wave reaches it. */
	private static void plan(ServerLevel level, ServerPlayer player, Wave wave) {
		Vec3 a = wave.from, b = wave.from.add(wave.dir.scale(wave.range));
		double spread = wave.range * Math.tan(Math.toRadians(HALF_WIDTH_DEG)) + 1.0;
		AABB box = new AABB(a, b).inflate(spread);
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY()) {
				continue;
			}
			double along = wave.along(Vec3.atCenterOf(pos), 0.0);
			if (along < 0.0) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.is(net.minecraft.tags.BlockTags.PORTALS)) {
				continue;
			}
			int tick = Mth.clamp((int)(along / wave.range * TRAVEL_TICKS), 0, TRAVEL_TICKS - 1);
			wave.carve.get(tick).add(pos.immutable());
		}
	}

	static void tick(MinecraftServer server) {
		if (WAVES.isEmpty()) {
			return;
		}
		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
			Wave wave = it.next();
			ServerLevel level = server.getLevel(wave.dimension);
			ServerPlayer owner = server.getPlayerList().getPlayer(wave.owner);
			int age = level == null ? TRAVEL_TICKS : (int)(level.getGameTime() - wave.start);
			if (level == null || owner == null || age >= TRAVEL_TICKS) {
				it.remove();
				continue;
			}
			double far = wave.range * (age + 1) / TRAVEL_TICKS;
			front(level, wave, far);
			hit(level, owner, wave, far);
			carve(level, owner, wave, wave.carve.get(age));
			if (age == TRAVEL_TICKS - 1) {
				Vec3 end = wave.from.add(wave.dir.scale(wave.range));
				level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.8F, 0.6F);
			}
		}
	}

	/** The wave's leading edge: an arc of purple pressure and dark shards {@code at} blocks out. */
	private static void front(ServerLevel level, Wave wave, double at) {
		double halfWidth = at * Math.tan(Math.toRadians(HALF_WIDTH_DEG));
		double halfHeight = Math.max(MIN_THICKNESS, at * Math.tan(Math.toRadians(HALF_HEIGHT_DEG)));
		int across = 8 + (int)at;
		var random = level.getRandom();
		for (int i = 0; i <= across; i++) {
			double s = (i / (double)across) * 2.0 - 1.0;
			double v = (random.nextDouble() * 2.0 - 1.0) * halfHeight;
			Vec3 p = wave.from.add(wave.dir.scale(at)).add(wave.right.scale(s * halfWidth)).add(wave.up.scale(v));
			level.sendParticles(WAVE, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
			if (random.nextInt(3) == 0) {
				level.sendParticles(DARK, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0.0);
			}
		}
		Vec3 mid = wave.from.add(wave.dir.scale(at));
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, mid.x, mid.y, mid.z, 6, halfWidth * 0.4, halfHeight * 0.4, halfWidth * 0.4, 0.1);
	}

	/** Everything the wave has just reached (out to {@code far}) is hurt, thrown along it and slowed, once. */
	private static void hit(ServerLevel level, ServerPlayer owner, Wave wave, double far) {
		double spread = far * Math.tan(Math.toRadians(HALF_WIDTH_DEG)) + 2.0;
		AABB area = new AABB(wave.from, wave.from.add(wave.dir.scale(far))).inflate(spread);
		Vec3 flat = new Vec3(wave.dir.x, 0.0, wave.dir.z);
		Vec3 push = flat.lengthSqr() < 1.0E-4 ? wave.dir : flat.normalize();
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
			e -> e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !wave.hit.contains(e.getId()))) {
			double along = wave.along(target.getBoundingBox().getCenter(), target.getBbWidth() * 0.5 + 0.3);
			if (along < 0.0 || along > far + 0.5) {
				continue;
			}
			wave.hit.add(target.getId());
			float share = 1.0F - DAMAGE_FALLOFF * (float)(along / wave.range);
			GravityPower.hurtByWeight(target, owner, wave.damage * share);
			double shove = Mth.lerp(wave.charge, SHOVE_MIN, SHOVE_MAX) * share;
			Vec3 v = push.scale(shove).add(0.0, SHOVE_UP + Math.max(0.0, wave.dir.y) * shove, 0.0);
			target.setDeltaMovement(v);
			target.needsSync = true;
			target.syncVelocity = true;
			if (target instanceof ServerPlayer hitPlayer) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(hitPlayer, new com.jaymospowers.power.web.WebNet.Nudge(v));
			}
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_LEVEL), owner);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 12,
				0.3, 0.3, 0.3, 0.1);
		}
	}

	/** The ground the wave has just reached gives way: soft blocks always, harder ones by the wave's strength there. */
	private static void carve(ServerLevel level, ServerPlayer owner, Wave wave, List<BlockPos> blocks) {
		var random = level.getRandom();
		float hardest = Mth.lerp(wave.charge, CARVE_HARDNESS_MIN, CARVE_HARDNESS_MAX);
		int broken = 0;
		for (BlockPos pos : blocks) {
			if (broken >= CARVE_MAX_PER_TICK) {
				break;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || !level.mayInteract(owner, pos)) {
				continue;
			}
			float hardness = state.getDestroySpeed(level, pos);
			if (hardness < 0.0F) {
				continue;
			}
			double along = Vec3.atCenterOf(pos).subtract(wave.from).dot(wave.dir);
			float strength = hardest * (1.0F - CARVE_FALLOFF * (float)(along / wave.range));
			// a soft block goes where a hard one holds; ragged at the edges
			float chance = Mth.clamp(1.2F - hardness / Math.max(0.01F, strength), 0.0F, 1.0F);
			if (random.nextFloat() >= chance) {
				continue;
			}
			level.destroyBlock(pos, random.nextFloat() < CARVE_DROP_CHANCE, owner);
			if (broken % 4 == 0) {
				com.jaymospowers.core.Debris.puff(level, pos, state, 1.6F);
			}
			broken++;
		}
	}
}
