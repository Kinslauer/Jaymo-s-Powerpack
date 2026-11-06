package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.Heritage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.522) Ignivorus, the Nether's fire dragon ({@code docs/SaintsDragons-Plan.md}): its moves and numbers after Saint's
 * Dragons' Ignivorus by Leon Saint (MIT code; none of its art, models or sounds are used). Andy: "put the effects onto
 * Kintsugi's model then put that in the nether", so it's drawn as eanncrod's Kintsugi wyvern (CC BY 4.0, as Eurus and
 * Aeolus) cooled from lava: obsidian, ash, ember or crimson hide, its seams glowing lava cracks
 * ({@code tools/gen_kintsugi_dragons.py}). Big (about 4.3 blocks tall, 14 across the wings).
 * <p>
 * It flies about anywhere in the Nether ({@link Dragons}, any {@code #minecraft:is_nether} biome) and leaves players be
 * until one hurts it. Then, besides the swoop, the hover, the bite and the tail sweep every dragon has:
 * <ul>
 * <li><b>Fire breath</b>, as Notus's ({@link DragonBreath}).</li>
 * <li><b>Fireball volley:</b> at {@link #VOLLEY_MIN}-{@link #VOLLEY_MAX} blocks, three exploding fireballs in a row.</li>
 * <li><b>Magma pillars:</b> on its feet, it stamps and a line of lava pillars bursts up from the ground toward its foe, one
 * after another, burning and throwing whoever they catch.</li>
 * </ul>
 * Fire can't hurt it. Cooked porkchop is its favourite; Emberborn win it over twice as fast; ridden, R is its breath.
 */
public class IgnivorusDragon extends WindDragon {
	// ---- body ----
	static final double HEALTH = 450.0, ARMOR = 12.0, TOUGHNESS = 6.0, SPEED = 0.24, BITE = 22.0;
	/** Wild: a heavy flier, slower to circle than Notus, a hard dive. */
	static final double CRUISE = 0.5, SWOOP = 1.3;
	/** Its riders' seat and its mouth: the Kintsugi wyvern drawn at 0.55 (Eurus's at 0.28, scaled). */
	static final double SEAT_HEIGHT = 3.25, SEAT_FRONT = 0.4, SEAT_BACK = -0.9;
	static final double MOUTH_AHEAD_GROUND = 3.3, MOUTH_UP_GROUND = 2.95, MOUTH_AHEAD_AIR = 3.7, MOUTH_UP_AIR = 2.0;
	/** Ridden: stamina a second flapping and gliding (Notus 1.6 and 0.25: it's heavier). */
	static final float FLAP_DRAIN = 1.8F, GLIDE_DRAIN = 0.3F;

	// ---- the fireball volley ----
	/** How far off its foe must be (blocks), how often it fires (ticks), how many, how far apart (ticks), how hard they blow. */
	static final double VOLLEY_MIN = 10.0, VOLLEY_MAX = 48.0, FIREBALL_SPEED = 1.1;
	static final int VOLLEY_EVERY = 160, VOLLEY_SHOTS = 3, VOLLEY_GAP = 8, FIREBALL_POWER = 1;

	// ---- the magma pillars ----
	/** How near its foe must be, how often, how many pillars, how far apart (blocks) and when (ticks); their reach, damage, burn, throw. */
	static final double PILLAR_RANGE = 16.0, PILLAR_STEP = 2.2, PILLAR_RADIUS = 1.6, PILLAR_UP = 0.9;
	static final int PILLAR_EVERY = 180, PILLARS = 7, PILLAR_GAP = 4, PILLAR_BURN = 6;
	static final float PILLAR_DAMAGE = 12.0F;

	// ---- (0.7.612) its moves, for the model (docs/SaintsDragons-Animation-Spec.md) ----
	public static final int NONE = 0, VOLLEY_MOVE = 1, STAMP_MOVE = 2, BITE_MOVE = 3, SWEEP_MOVE = 4;
	/** How long each is shown (ticks): the wyvern's own fire, slam, bite and sweep played a little faster than authored. */
	static final int[] ACTION_TICKS = {0, 60, 50, 32, 42};
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(IgnivorusDragon.class, EntityDataSerializers.INT);
	private int actionLeft;

	private int volleyIn = VOLLEY_EVERY / 2, pillarIn = PILLAR_EVERY / 2;
	/** Shots still to fire in this volley, and ticks till the next. */
	private int shots, shotIn;
	private LivingEntity volleyAt;
	/** Pillars still to rise: where and when (in ticks from now). */
	private final List<Pillar> pillars = new ArrayList<>();

	private static final class Pillar {
		final Vec3 at;
		int in;

		Pillar(Vec3 at, int in) {
			this.at = at;
			this.in = in;
		}
	}

	public IgnivorusDragon(EntityType<? extends IgnivorusDragon> type, Level level) {
		super(type, level);
		this.xpReward = 120;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return dragonAttributes(HEALTH, ARMOR, TOUGHNESS, SPEED, BITE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, NONE);
	}

	@Override
	public int action() {
		return this.entityData.get(DATA_ACTION);
	}

	/** Shows {@code action} on its model for its length (a move already showing gives way only to the volley and stamp). */
	private void act(int action) {
		this.entityData.set(DATA_ACTION, action);
		this.actionLeft = ACTION_TICKS[action];
	}

	@Override
	protected void bitten(LivingEntity target) {
		if (action() == NONE) {
			act(BITE_MOVE);
		}
	}

	@Override
	protected void swept() {
		if (action() == NONE) {
			act(SWEEP_MOVE);
		}
	}

	/** How far through {@code action} its model is after {@code age} ticks (0 to 1). */
	public static float actionShare(int action, float age) {
		int length = action <= 0 || action >= ACTION_TICKS.length ? 1 : ACTION_TICKS[action];
		return Math.min(1.0F, Math.max(0.0F, age / length));
	}

	@Override
	public boolean favourite(ItemStack stack) {
		return stack.is(Items.COOKED_PORKCHOP);
	}

	@Override
	protected float bondFactor(Player player) {
		return Heritage.is(player, Heritage.Kind.EMBERBORN) ? BOND_FACTOR : 1.0F;
	}

	@Override
	protected void breathe(ServerLevel level, Vec3 mouth, Vec3 aim, boolean griefing) {
		DragonBreath.breathe(level, this, mouth, aim, this.tickCount, griefing);
	}

	/** It leaves players be until one hurts it. */
	@Override
	protected boolean aggressive() {
		return false;
	}

	/** Not one of the wind dragons: it doesn't count toward Eurus's and Aeolus's showing themselves. */
	@Override
	protected boolean windDragon() {
		return false;
	}

	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.RED;
	}

	@Override
	protected double cruiseSpeed() {
		return CRUISE;
	}

	@Override
	protected double swoopSpeed() {
		return SWOOP;
	}

	@Override
	protected float flapDrain() {
		return FLAP_DRAIN;
	}

	@Override
	protected float glideDrain() {
		return GLIDE_DRAIN;
	}

	@Override
	protected float flapVolume() {
		return 3.0F;
	}

	@Override
	protected double mouthAhead(boolean flying) {
		return flying ? MOUTH_AHEAD_AIR : MOUTH_AHEAD_GROUND;
	}

	@Override
	protected double mouthUp(boolean flying) {
		return flying ? MOUTH_UP_AIR : MOUTH_UP_GROUND;
	}

	@Override
	protected Vec3 seat(int index) {
		return new Vec3(0.0, SEAT_HEIGHT, index == 0 ? SEAT_FRONT : SEAT_BACK);
	}

	// ---- the fight ----

	@Override
	protected void fight(ServerLevel level, LivingEntity target) {
		super.fight(level, target);
		double distance = this.distanceTo(target);
		if (--this.volleyIn <= 0 && this.shots == 0 && distance > VOLLEY_MIN && distance < VOLLEY_MAX && this.hasLineOfSight(target)) {
			this.volleyIn = VOLLEY_EVERY + this.random.nextInt(60);
			this.shots = VOLLEY_SHOTS;
			this.shotIn = 0;
			this.volleyAt = target;
			act(VOLLEY_MOVE);
			level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 3.0F, 0.5F);
		}
		if (--this.pillarIn <= 0 && this.pillars.isEmpty() && this.onGround() && distance < PILLAR_RANGE) {
			this.pillarIn = PILLAR_EVERY + this.random.nextInt(60);
			pillars(level, target);
		}
	}

	/** A stamp, and the line of pillars it sends toward {@code target}. */
	private void pillars(ServerLevel level, LivingEntity target) {
		act(STAMP_MOVE);
		level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.5F);
		level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY() + 0.2, this.getZ(), 30, 2.0, 0.2, 2.0, 0.1);
		Vec3 way = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		way = way.lengthSqr() < 1.0E-4 ? this.getLookAngle().multiply(1.0, 0.0, 1.0).normalize() : way.normalize();
		Vec3 from = this.position().add(way.scale(this.getBbWidth() * 0.5 + 1.0));
		for (int i = 0; i < PILLARS; i++) {
			this.pillars.add(new Pillar(from.add(way.scale(i * PILLAR_STEP)), 6 + i * PILLAR_GAP));
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.actionLeft > 0 && --this.actionLeft == 0) {
			this.entityData.set(DATA_ACTION, NONE);
		}
		// the volley, a shot at a time
		if (this.shots > 0 && --this.shotIn <= 0) {
			LivingEntity at = this.volleyAt;
			if (at == null || !at.isAlive() || this.isTamed()) {
				this.shots = 0;
			} else {
				this.shots--;
				this.shotIn = VOLLEY_GAP;
				fireball(level, at);
			}
		}
		// the pillars, one after another
		for (int i = this.pillars.size() - 1; i >= 0; i--) {
			Pillar p = this.pillars.get(i);
			if (--p.in == 2) {
				// a warning: the ground glows and smokes where it's about to burst
				level.sendParticles(ParticleTypes.LARGE_SMOKE, p.at.x, ground(level, p.at) + 0.2, p.at.z, 8, 0.5, 0.1, 0.5, 0.01);
			}
			if (p.in <= 0) {
				erupt(level, p.at);
				this.pillars.remove(i);
			}
		}
	}

	private void fireball(ServerLevel level, LivingEntity at) {
		Vec3 mouth = this.mouth();
		Vec3 aim = at.getBoundingBox().getCenter().add(at.getDeltaMovement().scale(6.0)).subtract(mouth).normalize()
			.add(this.random.triangle(0.0, 0.05), this.random.triangle(0.0, 0.05), this.random.triangle(0.0, 0.05));
		LargeFireball ball = new LargeFireball(level, this, aim.normalize().scale(FIREBALL_SPEED), FIREBALL_POWER);
		ball.setPos(mouth.x, mouth.y, mouth.z);
		level.addFreshEntity(ball);
		level.playSound(null, BlockPos.containing(mouth), SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 2.5F, 0.6F);
	}

	/** The ground's top under {@code at} (a few blocks up or down). */
	private static double ground(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at.x, at.y + 3.0, at.z);
		for (int i = 0; i < 8; i++, pos = pos.below()) {
			if (!level.getBlockState(pos).isAir() && level.getBlockState(pos).isSolid()) {
				return pos.getY() + 1.0;
			}
		}
		return at.y;
	}

	/** One pillar: a burst of lava and flame, and whoever stands in it hurt, set alight and thrown up. */
	private void erupt(ServerLevel level, Vec3 at) {
		double y = ground(level, at);
		level.playSound(null, BlockPos.containing(at.x, y, at.z), SoundEvents.LAVA_POP, SoundSource.HOSTILE, 2.5F, 0.6F);
		level.playSound(null, BlockPos.containing(at.x, y, at.z), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2.0F, 0.6F);
		for (int h = 0; h < 5; h++) {
			level.sendParticles(ParticleTypes.FLAME, at.x, y + h * 0.8, at.z, 10, 0.35, 0.3, 0.35, 0.03);
			level.sendParticles(ParticleTypes.LAVA, at.x, y + h * 0.8, at.z, 2, 0.3, 0.3, 0.3, 0.0);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, y + 4.0, at.z, 6, 0.4, 0.4, 0.4, 0.02);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at.x - PILLAR_RADIUS, y - 0.5,
			at.z - PILLAR_RADIUS, at.x + PILLAR_RADIUS, y + 4.0, at.z + PILLAR_RADIUS), e -> e != this && e.isAlive() && spareable(e))) {
			if (e.hurtServer(level, this.damageSources().inFire(), PILLAR_DAMAGE)) {
				e.igniteForSeconds(PILLAR_BURN);
				e.push(0.0, PILLAR_UP, 0.0);
				e.syncVelocity = true;
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return super.removeWhenFarAway(distance) && !fighting();
	}
}
