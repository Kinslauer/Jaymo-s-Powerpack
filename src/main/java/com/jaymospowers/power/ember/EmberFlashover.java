package com.jaymospowers.power.ember;

import com.jaymospowers.core.Flames;
import com.jaymospowers.power.smoke.SmokeField;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.14.0) Flashover, smoke merged into fire: Kindle or a fireball ({@link EmberKindle}, {@link EmberFireball}) meeting
 * an Emberborn Smoke user's <b>own</b> thick smoke ignites the whole cloud. A wave of flame races out from that block
 * through every connected block of their smoke, one ring every {@link #RING_TICKS} ticks, burning everything inside
 * ({@link Flames}, plus fire damage once a wave) and burning the smoke away; what's left goes up as one puff of black
 * soot (nobody's, so it never lights again). At most {@link #MAX_CELLS} blocks a wave. "Smokescreen or a Smoke Bomb,
 * then a flick of flame, is a fire trap." The wave reads the gas through {@code SmokeField}'s flashover section only.
 */
public final class EmberFlashover {
	// ---- tuning ----
	/** Ticks between rings of the wave (a ring is one block further out). */
	static final int RING_TICKS = 2;
	/** The most blocks of smoke one wave burns. */
	static final int MAX_CELLS = 400;
	/** The wave runs on through the owner's smoke at least this thick. */
	static final float SPREAD_LEAST = 0.3F;
	/** Fire damage to each creature caught (once a wave), and how long they burn (seconds). */
	static final float DAMAGE = 4.0F;
	static final int BURN_SECONDS = 6;
	/** Of the smoke burnt, this share goes up as black soot at the end. */
	static final float SOOT_SHARE = 0.15F;
	/** Ticks before the same player's smoke can flash over again. */
	static final int COOLDOWN = 20;

	private static final class Wave {
		final ServerLevel level;
		final ServerPlayer owner;
		final int ownerId;
		final LongArrayFIFOQueue ring = new LongArrayFIFOQueue();
		final LongOpenHashSet seen = new LongOpenHashSet();
		final IntOpenHashSet caught = new IntOpenHashSet();
		int burnt;
		int age;
		float smoke;
		double sumX, sumY, sumZ;

		Wave(ServerLevel level, ServerPlayer owner, BlockPos start) {
			this.level = level;
			this.owner = owner;
			this.ownerId = owner.getId();
			long key = start.asLong();
			this.ring.enqueue(key);
			this.seen.add(key);
		}
	}

	private static final List<Wave> WAVES = new ArrayList<>();
	/** When each player's smoke last flashed over (game time), by entity id. */
	private static final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap LAST = new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();

	private EmberFlashover() {
	}

	static void clear() {
		WAVES.clear();
		LAST.clear();
	}

	/** {@code owner}'s smoke at {@code start} catches: a wave sets off through the cloud (unless one just did). */
	static void ignite(ServerLevel level, ServerPlayer owner, BlockPos start) {
		long now = level.getGameTime();
		if (LAST.containsKey(owner.getId()) && now - LAST.get(owner.getId()) < COOLDOWN && now >= LAST.get(owner.getId())) {
			return;
		}
		for (Wave wave : WAVES) {
			if (wave.ownerId == owner.getId() && wave.level == level) {
				return;
			}
		}
		LAST.put(owner.getId(), now);
		WAVES.add(new Wave(level, owner, start));
		Vec3 at = Vec3.atCenterOf(start);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 2.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5F, 0.4F);
	}

	static void tick() {
		if (WAVES.isEmpty()) {
			return;
		}
		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext();) {
			Wave wave = it.next();
			if (++wave.age % RING_TICKS != 0) {
				continue;
			}
			if (!ring(wave)) {
				end(wave);
				it.remove();
			}
		}
	}

	/** Burns one ring of the wave and lines up the next; false once it's done. */
	private static boolean ring(Wave wave) {
		ServerLevel level = wave.level;
		int count = wave.ring.size();
		if (count == 0 || wave.burnt >= MAX_CELLS) {
			return false;
		}
		for (int i = 0; i < count; i++) {
			long key = wave.ring.dequeueLong();
			if (wave.burnt >= MAX_CELLS) {
				break;
			}
			if (SmokeField.ownedDensity(level, key, wave.ownerId) < SPREAD_LEAST) {
				continue;
			}
			wave.smoke += SmokeField.burnAway(level, key);
			wave.burnt++;
			BlockPos pos = BlockPos.of(key);
			Vec3 at = Vec3.atCenterOf(pos);
			wave.sumX += at.x;
			wave.sumY += at.y;
			wave.sumZ += at.z;
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 3, 0.35, 0.35, 0.35, 0.02);
			if (level.getRandom().nextInt(4) == 0) {
				level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 1, 0.3, 0.3, 0.3, 0.0);
			}
			if (level.getRandom().nextInt(12) == 0) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.7F);
			}
			burn(wave, level, pos);
			for (Direction direction : Direction.values()) {
				long next = BlockPos.offset(key, direction);
				if (wave.seen.add(next)) {
					wave.ring.enqueue(next);
				}
			}
		}
		return !wave.ring.isEmpty() && wave.burnt < MAX_CELLS;
	}

	/** Everything in the burning block (never the owner) is set alight and, once a wave, takes fire damage. */
	private static void burn(Wave wave, ServerLevel level, BlockPos pos) {
		AABB box = new AABB(pos).inflate(0.2);
		Flames.area(level, box, BURN_SECONDS, wave.owner, e -> true);
		for (Entity entity : level.getEntities(wave.owner, box, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
			if (wave.caught.add(entity.getId())) {
				((LivingEntity)entity).hurtServer(level, level.damageSources().source(DamageTypes.IN_FIRE, wave.owner), EmberKindle.seasonal(wave.owner, DAMAGE));
			}
		}
	}

	/** The smoke burnt away goes up as black soot, from the middle of what burnt. */
	private static void end(Wave wave) {
		if (wave.burnt <= 0) {
			return;
		}
		Vec3 middle = new Vec3(wave.sumX / wave.burnt, wave.sumY / wave.burnt, wave.sumZ / wave.burnt);
		wave.level.sendParticles(ParticleTypes.LARGE_SMOKE, middle.x, middle.y, middle.z, 20, 1.2, 0.8, 1.2, 0.02);
		SmokeField.puffSooty(wave.level, middle, wave.smoke * SOOT_SHARE, null, 1.0F);
	}
}
