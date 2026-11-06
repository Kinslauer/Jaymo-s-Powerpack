package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.Heritage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.522) Atroxiia, the frost wyrm of the lesser dragons ({@code docs/SaintsDragons-Plan.md}): its moves and numbers
 * are ported from Saint's Dragons by Leon Saint (MIT licence; none of its art, models or sounds are used: the model is
 * our own, {@code AtroxiiaDragonModel}, painted by {@code tools/gen_atroxiia.py}). A walker and a swimmer
 * ({@link WindDragon#swims}) of the snowfields and the cold coasts: a pale serpent with no hind legs, raised on two
 * great arms and its own long tail, which arches over its back into a gold-bound spear of ice. Its head wears a striped
 * nemes cloth and gold band. It leaves frozen water where it strolls, and only fights whoever hurts it.
 * <p>
 * Its moves, besides the bite and the tail sweep every dragon has:
 * <ul>
 * <li><b>Slam:</b> a fist brought down before it ({@link #SLAM}).</li>
 * <li><b>Swipe:</b> a quick claw swung across ({@link #SWIPE}), knocking them aside.</li>
 * <li><b>Precise strike:</b> at a few blocks, three quick jabs of the tail spear that drag them in ({@link #STRIKE} each),
 * the last one stunning them.</li>
 * <li><b>Gungnir:</b> it draws the spear back over its shoulder, lunges and drives it through everything in a line
 * ahead ({@link #GUNGNIR}, a stun).</li>
 * <li><b>Devastating sweep:</b> pressed close, it spins the tail round in a wide ring ({@link #SWEEP_HIT}).</li>
 * <li><b>Helheim quake:</b> it rears and brings both fists down twice: the ground shakes for {@link #QUAKE_RADIUS}
 * blocks round it (anyone on their feet is hurt and thrown, the second one stuns them long) and the water near freezes.</li>
 * </ul>
 * Tamed and ridden it's steered like a horse on land and swims where the rider looks in water; R drives Gungnir where
 * the rider looks. Its favourite is raw cod; Frostborn win it over twice as fast.
 */
public class AtroxiiaDragon extends WindDragon {
	static final double HEALTH = 200.0, ARMOR = 10.0, TOUGHNESS = 4.0, SPEED = 0.22, BITE = 10.0;
	/** The slam: damage, how far ahead it lands and its reach there, pace, the tick it lands. */
	static final float SLAM = 16.0F;
	static final double SLAM_AHEAD = 3.0, SLAM_RADIUS = 3.0;
	static final int SLAM_EVERY = 60, SLAM_HIT = 9;
	/** The swipe: damage, the throw, pace. */
	static final float SWIPE = 12.0F;
	static final double SWIPE_THROW = 0.9;
	static final int SWIPE_EVERY = 40;
	/** The precise strike: each jab's damage, its range (past its own and their half widths), the pull, pace, the jabs' ticks, the stun. */
	static final float STRIKE = 9.0F;
	static final double STRIKE_MIN = 1.5, STRIKE_MAX = 5.0, STRIKE_PULL = 0.45, STRIKE_RADIUS = 1.4;
	static final int STRIKE_EVERY = 140, STRIKE_STUN = 40;
	static final int[] STRIKE_HITS = {7, 13, 19};
	/** Gungnir: damage, its range (past the half widths), the lunge, the line's length and reach, pace, wind-up, the stun. */
	static final float GUNGNIR = 40.0F;
	static final double GUNGNIR_MIN = 3.0, GUNGNIR_MAX = 9.0, GUNGNIR_LUNGE = 1.1, GUNGNIR_LENGTH = 8.0, GUNGNIR_RADIUS = 1.6;
	static final int GUNGNIR_EVERY = 240, GUNGNIR_WINDUP = 17, GUNGNIR_STUN = 40;
	/** The devastating sweep: damage, reach, the throw, pace, the tick it lands. */
	static final float SWEEP_HIT = 13.0F;
	static final double SWEEP_REACH = 7.0, SWEEP_HURL = 1.65;
	static final int SWEEP_PACE = 120, SWEEP_LAND = 10;
	/** The Helheim quake: damage, radius, how far off it's used, the two throws, pace, the two ticks, the stun, the freeze. */
	static final float QUAKE = 25.0F;
	static final double QUAKE_RADIUS = 9.0, QUAKE_RANGE = 18.0, QUAKE_THROW = 0.75, QUAKE_THROW_LAST = 1.8;
	static final int QUAKE_EVERY = 400, QUAKE_FIRST = 20, QUAKE_SECOND = 34, QUAKE_STUN = 100, FREEZE_RADIUS = 8;
	/** It quakes freely once worn below this share of its health (before, only at range). */
	static final float QUAKE_AT = 0.5F;
	/** Its frozen trail: how far round its feet water freezes as it strolls, and how often (ticks). */
	static final int FROST_RADIUS = 3, FROST_EVERY = 4;
	/** Ridden: Gungnir's pace (ticks). */
	static final int RIDER_GUNGNIR_EVERY = 100;

	/** Its moves, for the model ({@link #action}). */
	public static final int NONE = 0, SLAM_MOVE = 1, SWIPE_MOVE = 2, STRIKE_MOVE = 3, GUNGNIR_MOVE = 4, SWEEP_MOVE = 5, QUAKE_MOVE = 6, BITE_MOVE = 7, LASH_MOVE = 8;
	/** How long each lasts (ticks). */
	static final int[] ACTION_TICKS = {0, 16, 12, 26, GUNGNIR_WINDUP + 13, 20, QUAKE_SECOND + 12, 16, 24};

	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(AtroxiiaDragon.class, EntityDataSerializers.INT);

	private int slamIn, swipeIn, strikeIn = STRIKE_EVERY / 2, gungnirIn = GUNGNIR_EVERY / 2, sweepAtIn, quakeIn = QUAKE_EVERY / 2, riderIn;
	/** The move under way: which, the tick it began, at whom, and the way it's aimed. */
	private int move = NONE, moveStart;
	private @Nullable LivingEntity moveTarget;
	private Vec3 moveAim = Vec3.ZERO;

	public AtroxiiaDragon(EntityType<? extends AtroxiiaDragon> type, Level level) {
		super(type, level);
		this.xpReward = 40;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return dragonAttributes(HEALTH, ARMOR, TOUGHNESS, SPEED, BITE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, NONE);
	}

	// ---- what sets it apart ---------------------------------------------------------------------------------------------

	@Override
	protected boolean flies() {
		return false;
	}

	@Override
	public boolean swims() {
		return true;
	}

	@Override
	public double swimSpeed() {
		return 0.38;
	}

	@Override
	protected boolean aggressive() {
		return false;
	}

	@Override
	protected boolean bossBar() {
		return false;
	}

	@Override
	protected boolean windDragon() {
		return false;
	}

	@Override
	public boolean favourite(ItemStack stack) {
		return stack.is(Items.COD);
	}

	@Override
	protected float bondFactor(Player player) {
		return Heritage.is(player, Heritage.Kind.FROSTBORN) ? BOND_FACTOR : 1.0F;
	}

	/** It has no breath: its R is Gungnir ({@link #riderBreath}). */
	@Override
	protected void breathe(ServerLevel level, Vec3 mouth, Vec3 aim, boolean griefing) {
	}

	@Override
	protected double breathRange() {
		return 0.0;
	}

	@Override
	protected Vec3 seat(int index) {
		return new Vec3(0.0, index == 0 ? 3.5 : 3.3, index == 0 ? 0.36 : -0.9);
	}

	@Override
	protected double mouthAhead(boolean flying) {
		return 3.3;
	}

	@Override
	protected double mouthUp(boolean flying) {
		return 4.1;
	}

	@Override
	public int action() {
		return this.entityData.get(DATA_ACTION);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	private void begin(int move, @Nullable LivingEntity target, Vec3 aim) {
		this.move = move;
		this.moveStart = this.tickCount;
		this.moveTarget = target;
		Vec3 flat = aim.multiply(1.0, 0.0, 1.0);
		this.moveAim = flat.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0.0F, this.getYRot()) : flat.normalize();
		this.entityData.set(DATA_ACTION, move);
	}

	// ---- ticking ----------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// (0.7.536) pulled for now (Dragons.ATROXIIA_ON): any left in a world (or summoned) go quietly
		if (!Dragons.ATROXIIA_ON) {
			this.ejectPassengers();
			this.discard();
			return;
		}
		this.riderIn--;
		if (this.move != NONE) {
			moveTick(level, this.tickCount - this.moveStart);
		}
		// its frozen trail: strolling (not fighting, not ridden) over still water, it walks on ice
		if (this.tickCount % FROST_EVERY == 0 && this.onGround() && !this.isInWater() && !fighting() && !this.isVehicle()) {
			frostWalk(level);
		}
	}

	private void moveTick(ServerLevel level, int age) {
		switch (this.move) {
			case SLAM_MOVE -> {
				if (age == SLAM_HIT) {
					slam(level);
				}
			}
			case STRIKE_MOVE -> {
				for (int hit : STRIKE_HITS) {
					if (age == hit) {
						strike(level, hit == STRIKE_HITS[STRIKE_HITS.length - 1]);
					}
				}
			}
			case GUNGNIR_MOVE -> {
				if (age == GUNGNIR_WINDUP - 5) {
					// the lunge
					this.setDeltaMovement(this.getDeltaMovement().add(this.moveAim.x * GUNGNIR_LUNGE, 0.1, this.moveAim.z * GUNGNIR_LUNGE));
					level.playSound(null, this.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.HOSTILE, 1.6F, 0.6F);
				} else if (age == GUNGNIR_WINDUP) {
					gungnir(level);
				}
			}
			case SWEEP_MOVE -> {
				if (age == SWEEP_LAND) {
					sweepRing(level);
				}
			}
			case QUAKE_MOVE -> {
				if (age == QUAKE_FIRST) {
					quake(level, false);
				} else if (age == QUAKE_SECOND) {
					quake(level, true);
				}
			}
			default -> {
			}
		}
		if (age >= ACTION_TICKS[this.move]) {
			this.move = NONE;
			this.moveTarget = null;
			this.entityData.set(DATA_ACTION, NONE);
		}
	}

	/** (0.7.612) The plain bite and tail sweep every walker has, shown as moves of their own (docs/SaintsDragons-Animation-Spec.md). */
	@Override
	protected void bitten(LivingEntity target) {
		if (this.move == NONE) {
			begin(BITE_MOVE, target, target.position().subtract(this.position()));
		}
	}

	@Override
	protected void swept() {
		if (this.move == NONE) {
			begin(LASH_MOVE, null, Vec3.directionFromRotation(0.0F, this.getYRot()));
		}
	}

	// ---- fighting -------------------------------------------------------------------------------------------------------

	@Override
	protected void walkerFight(ServerLevel level, LivingEntity target) {
		if (this.move != NONE) {
			// (in the middle of a move: it holds its ground and keeps its eyes on them)
			this.getNavigation().stop();
			lookAt(target);
			return;
		}
		groundFight(level, target, false);
		this.slamIn--;
		this.swipeIn--;
		this.strikeIn--;
		this.gungnirIn--;
		this.sweepAtIn--;
		this.quakeIn--;
		double distance = this.distanceTo(target);
		double halves = this.getBbWidth() * 0.5 + target.getBbWidth() * 0.5;
		double reach = 3.2 + halves;
		Vec3 to = target.position().subtract(this.position());
		boolean worn = this.getHealth() < this.getMaxHealth() * QUAKE_AT;
		if (this.quakeIn <= 0 && distance <= QUAKE_RANGE && target.onGround() && (worn || distance > reach) && this.onGround()) {
			this.quakeIn = QUAKE_EVERY;
			begin(QUAKE_MOVE, target, to);
			level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.5F);
		} else if (this.sweepAtIn <= 0 && distance < halves + 1.5) {
			this.sweepAtIn = SWEEP_PACE;
			begin(SWEEP_MOVE, target, to);
		} else if (this.gungnirIn <= 0 && distance >= GUNGNIR_MIN + halves && distance <= GUNGNIR_MAX + halves) {
			this.gungnirIn = GUNGNIR_EVERY;
			begin(GUNGNIR_MOVE, target, to);
			level.playSound(null, this.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.4F, 0.5F);
		} else if (this.strikeIn <= 0 && distance >= STRIKE_MIN + halves && distance <= STRIKE_MAX + halves) {
			this.strikeIn = STRIKE_EVERY;
			begin(STRIKE_MOVE, target, to);
		} else if (distance <= reach && this.slamIn <= 0 && inFront(target)) {
			this.slamIn = SLAM_EVERY;
			begin(SLAM_MOVE, target, to);
		} else if (distance <= reach && this.swipeIn <= 0) {
			this.swipeIn = SWIPE_EVERY;
			begin(SWIPE_MOVE, target, to);
			if (target.hurtServer(level, this.damageSources().mobAttack(this), SWIPE)) {
				Vec3 side = new Vec3(-this.moveAim.z, 0.0, this.moveAim.x);
				target.push(side.x * SWIPE_THROW, 0.25, side.z * SWIPE_THROW);
				target.syncVelocity = true;
			}
			level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.6F, 0.7F);
		}
	}

	private void slam(ServerLevel level) {
		Vec3 at = this.position().add(this.moveAim.scale(SLAM_AHEAD));
		BlockState under = level.getBlockState(BlockPos.containing(at).below());
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), at.x, this.getY() + 0.1, at.z, 30, 1.2, 0.1, 1.2, 0.15);
		level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, this.getY() + 0.4, at.z, 20, 1.2, 0.2, 1.2, 0.05);
		level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.7F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SLAM_RADIUS, 2.0, SLAM_RADIUS),
			e -> spareable(e) && e.position().distanceToSqr(at) < SLAM_RADIUS * SLAM_RADIUS)) {
			if (e.hurtServer(level, this.damageSources().mobAttack(this), SLAM)) {
				e.push(this.moveAim.x * 0.5, 0.35, this.moveAim.z * 0.5);
				e.syncVelocity = true;
			}
		}
	}

	/** A jab of the tail spear along its aim: whoever's in the line is hurt and dragged in; the last jab stuns. */
	private void strike(ServerLevel level, boolean last) {
		LivingEntity target = this.moveTarget;
		if (target != null && target.isAlive()) {
			Vec3 to = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > 0.01) {
				this.moveAim = to.normalize();
			}
			lookAt(target);
		}
		double reach = STRIKE_MAX + this.getBbWidth() * 0.5 + 1.0;
		for (LivingEntity e : inLine(level, reach, STRIKE_RADIUS)) {
			if (e.hurtServer(level, this.damageSources().mobAttack(this), STRIKE)) {
				e.push(-this.moveAim.x * STRIKE_PULL, 0.1, -this.moveAim.z * STRIKE_PULL);
				e.syncVelocity = true;
				if (last) {
					stun(level, e, STRIKE_STUN);
				}
			}
		}
		Vec3 tip = this.position().add(this.moveAim.scale(reach * 0.7)).add(0.0, 1.5, 0.0);
		level.sendParticles(ParticleTypes.SNOWFLAKE, tip.x, tip.y, tip.z, 8, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, this.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 1.4F, last ? 0.6F : 0.9F);
	}

	/** Gungnir: the spear driven through everything in a line ahead of it, and a stun. */
	private void gungnir(ServerLevel level) {
		double reach = GUNGNIR_LENGTH + this.getBbWidth() * 0.5;
		for (LivingEntity e : inLine(level, reach, GUNGNIR_RADIUS)) {
			if (e.hurtServer(level, this.damageSources().mobAttack(this), GUNGNIR)) {
				e.push(this.moveAim.x * 0.8, 0.3, this.moveAim.z * 0.8);
				e.syncVelocity = true;
				stun(level, e, GUNGNIR_STUN);
			}
		}
		for (int i = 1; i <= 8; i++) {
			Vec3 p = this.position().add(this.moveAim.scale(reach * i / 8.0)).add(0.0, 1.2, 0.0);
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 4, 0.15, 0.15, 0.15, 0.02);
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		level.playSound(null, this.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 0.8F, 1.4F);
		level.playSound(null, this.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
	}

	/** Whoever is within {@code radius} of the line from its chest {@code length} blocks along its aim. */
	private java.util.List<LivingEntity> inLine(ServerLevel level, double length, double radius) {
		Vec3 from = this.position().add(0.0, 1.0, 0.0);
		Vec3 end = from.add(this.moveAim.scale(length));
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(radius + 1.0, 2.5, radius + 1.0), e -> {
			if (!spareable(e)) {
				return false;
			}
			Vec3 c = e.getBoundingBox().getCenter();
			double along = c.subtract(from).dot(this.moveAim);
			if (along < 0.0 || along > length) {
				return false;
			}
			Vec3 near = from.add(this.moveAim.scale(along));
			return c.multiply(1.0, 0.0, 1.0).distanceTo(near.multiply(1.0, 0.0, 1.0)) < radius + e.getBbWidth() * 0.5
				&& Math.abs(c.y - near.y) < 3.0;
		});
	}

	/** The devastating sweep: the tail spun round in a wide ring. */
	private void sweepRing(ServerLevel level) {
		level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.5F, 0.4F);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY() + 1.2, this.getZ(), 10, 3.5, 0.3, 3.5, 0.0);
		level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 1.0, this.getZ(), 40, 4.0, 0.4, 4.0, 0.08);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(SWEEP_REACH, 1.5, SWEEP_REACH),
			e -> spareable(e) && e.distanceToSqr(this) < SWEEP_REACH * SWEEP_REACH)) {
			if (e.hurtServer(level, this.damageSources().mobAttack(this), SWEEP_HIT)) {
				Vec3 away = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 0.01 ? this.moveAim : away.normalize();
				e.push(away.x * SWEEP_HURL, 0.45, away.z * SWEEP_HURL);
				e.syncVelocity = true;
			}
		}
	}

	/** The Helheim quake: everyone on their feet near it hurt and thrown; the last one stuns them long and freezes the water. */
	private void quake(ServerLevel level, boolean last) {
		level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5F, 0.4F);
		level.playSound(null, this.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.5F, 0.4F);
		BlockState under = level.getBlockState(this.blockPosition().below());
		for (int ring = 1; ring <= 3; ring++) {
			double r = QUAKE_RADIUS * ring / 3.0;
			for (int i = 0; i < 12 * ring; i++) {
				double a = i * Math.PI * 2.0 / (12 * ring);
				double x = this.getX() + Math.cos(a) * r, z = this.getZ() + Math.sin(a) * r;
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), x, this.getY() + 0.1, z, 2, 0.2, 0.05, 0.2, 0.1);
				level.sendParticles(ParticleTypes.SNOWFLAKE, x, this.getY() + 0.3, z, 1, 0.1, 0.1, 0.1, 0.03);
			}
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(QUAKE_RADIUS, 2.0, QUAKE_RADIUS),
			e -> spareable(e) && e.onGround() && e.distanceToSqr(this) < (QUAKE_RADIUS + 1.5) * (QUAKE_RADIUS + 1.5))) {
			if (e.hurtServer(level, this.damageSources().mobAttack(this), QUAKE)) {
				Vec3 away = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 0.01 ? this.moveAim : away.normalize();
				double hurl = last ? QUAKE_THROW_LAST : QUAKE_THROW;
				e.push(away.x * hurl, 0.4 + hurl * 0.2, away.z * hurl);
				e.syncVelocity = true;
				if (last) {
					stun(level, e, QUAKE_STUN);
				}
			}
		}
		if (last) {
			freeze(level, FREEZE_RADIUS);
		}
	}

	/** Still water round it (within {@code radius}, about its feet) turns to frosted ice, which melts again. */
	private void freeze(ServerLevel level, int radius) {
		BlockPos feet = this.blockPosition();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				for (int dy = -2; dy <= 1; dy++) {
					at.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
					if (level.getBlockState(at).is(Blocks.WATER) && level.getFluidState(at).isSource() && level.getBlockState(at.above()).isAir()) {
						level.setBlockAndUpdate(at, Blocks.FROSTED_ICE.defaultBlockState());
						level.scheduleTick(at.immutable(), Blocks.FROSTED_ICE, Mth.nextInt(this.random, 60, 120));
						break;
					}
				}
			}
		}
	}

	/** Its frozen trail: the still water under and round its feet as it walks. */
	private void frostWalk(ServerLevel level) {
		BlockPos below = this.blockPosition().below();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -FROST_RADIUS; dx <= FROST_RADIUS; dx++) {
			for (int dz = -FROST_RADIUS; dz <= FROST_RADIUS; dz++) {
				if (dx * dx + dz * dz > FROST_RADIUS * FROST_RADIUS) {
					continue;
				}
				at.set(below.getX() + dx, below.getY(), below.getZ() + dz);
				if (level.getBlockState(at).is(Blocks.WATER) && level.getFluidState(at).isSource() && level.getBlockState(at.above()).isAir()) {
					level.setBlockAndUpdate(at, Blocks.FROSTED_ICE.defaultBlockState());
					level.scheduleTick(at.immutable(), Blocks.FROSTED_ICE, Mth.nextInt(this.random, 60, 120));
				}
			}
		}
	}

	/** Ridden, R drives Gungnir where the rider looks. */
	@Override
	public void riderBreath(float yaw, float pitch) {
		if (this.riderIn > 0 || this.move != NONE || exhausted()) {
			return;
		}
		this.riderIn = RIDER_GUNGNIR_EVERY;
		this.setYRot(yaw);
		begin(GUNGNIR_MOVE, null, Vec3.directionFromRotation(0.0F, yaw));
	}

	/** How far through {@code action} its model is after {@code age} ticks (0 to 1). */
	public static float actionShare(int action, float age) {
		int length = action <= 0 || action >= ACTION_TICKS.length ? 1 : ACTION_TICKS[action];
		return Mth.clamp(age / length, 0.0F, 1.0F);
	}
}
