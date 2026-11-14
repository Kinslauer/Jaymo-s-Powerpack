package com.jaymoskingdoms;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A mangonel, the stone thrower a realm's war party pushes to a siege ({@link SiegeTrain}): a timber frame on four
 * wheels, a throwing arm in a twisted skein of rope and a stop beam it slams against. Two of the party's soldiers push
 * it ({@link Haul}); set down short of the enemy's seat, the first of them works it. It turns to its mark, throws a
 * {@link SiegeStone} high over the walls from {@link #NEAREST} to {@link #FURTHEST} blocks, and is winched down again.
 * With no crew it does nothing. Players can break it with {@link #HEALTH} worth of blows. Never saved.
 */
public final class Mangonel extends Entity {
	/** It throws at a foe from this far to this far (blocks). */
	public static final double NEAREST = 12.0, FURTHEST = 46.0;
	/** Between throws (ticks), plus up to {@link #JITTER} more. */
	static final int RELOAD = 200, JITTER = 60;
	/** How far it's heaved round per tick (degrees), and how close to its mark it must be to throw. */
	static final float TRAVERSE = 3.0F, ON_MARK = 6.0F;
	/** How wide of its mark a stone may fall (blocks). */
	static final double SPREAD = 2.0;
	/** Its operator works it from this near (blocks). */
	static final double WORK_REACH = 6.0;
	/** How often it looks for a mark (ticks). */
	static final int MARK_CHECK = 20;
	/** Where the stone leaves the cup: this high, this far forward of its middle (blocks). */
	static final double CUP_HEIGHT = 2.7, CUP_FORWARD = 0.4;
	/** Blows it takes from players before it breaks. */
	static final float HEALTH = 50.0F;
	/** The entity event that throws its arm on the client. */
	static final byte THROWN = 60;
	/** Client side: the arm flies up in this long, rests on the stop for this long, and is winched down in this long (ticks). */
	public static final int THROW_TICKS = 3, REST_TICKS = 40, WINCH_TICKS = 120;

	public static EntityType<Mangonel> TYPE;

	final Haul haul = new Haul();
	private long next;
	private float damage;
	private @Nullable LivingEntity mark;

	/** Client side: ticks since it last threw, and how far it has rolled (blocks, forward positive). */
	public int sinceThrown = THROW_TICKS + REST_TICKS + WINCH_TICKS;
	public float rolled, rolledO;
	private @Nullable Vec3 was;

	public Mangonel(EntityType<? extends Mangonel> type, Level level) {
		super(type, level);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("mangonel"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Mangonel>of(Mangonel::new, MobCategory.MISC)
			.noLootTable()
			.sized(2.0F, 2.0F)
			.clientTrackingRange(10)
			.updateInterval(2)
			.build(key));
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			if (this.sinceThrown < THROW_TICKS + REST_TICKS + WINCH_TICKS) {
				this.sinceThrown++;
			}
			this.rolledO = this.rolled;
			if (this.was != null) {
				this.rolled += SiegeTrain.rolled(this, this.was);
			}
			this.was = this.position();
			return;
		}
		if (this.haul.to != null) {
			this.haul.step(level, this);
			return;
		}
		PathfinderMob operator = this.haul.operator(level);
		if (operator == null || operator.distanceToSqr(this) > WORK_REACH * WORK_REACH) {
			return;
		}
		long now = level.getGameTime();
		if ((now + this.getId()) % MARK_CHECK == 0) {
			this.mark = SiegeTrain.mark(level, operator, this.position(), NEAREST, FURTHEST);
		}
		LivingEntity mark = this.mark;
		if (mark == null || !mark.isAlive()) {
			return;
		}
		double dx = mark.getX() - this.getX(), dz = mark.getZ() - this.getZ();
		float want = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		this.setYRot(this.getYRot() + Mth.clamp(Mth.wrapDegrees(want - this.getYRot()), -TRAVERSE, TRAVERSE));
		if (now >= this.next && Math.abs(Mth.wrapDegrees(want - this.getYRot())) < ON_MARK) {
			this.next = now + RELOAD + level.getRandom().nextInt(JITTER);
			this.loose(level, operator, mark);
		}
	}

	private void loose(ServerLevel level, PathfinderMob operator, LivingEntity mark) {
		Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
		Vec3 cup = this.position().add(forward.x * CUP_FORWARD, CUP_HEIGHT, forward.z * CUP_FORWARD);
		Vec3 at = mark.position().add(level.getRandom().triangle(0.0, SPREAD), 0.0, level.getRandom().triangle(0.0, SPREAD));
		double dx = at.x - cup.x, dz = at.z - cup.z;
		double flat = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		double angle = lob(flat, at.y - cup.y);
		Vec3 velocity = new Vec3(dx / flat * Math.cos(angle), Math.sin(angle), dz / flat * Math.cos(angle)).scale(SiegeStone.SPEED);
		SiegeStone.loose(level, operator, cup, velocity);
		level.playSound(null, this.getX(), this.getY() + 1.5, this.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 3.0F, 0.45F);
		level.playSound(null, this.getX(), this.getY() + 1.5, this.getZ(), SoundEvents.WOOD_HIT, SoundSource.HOSTILE, 3.0F, 0.5F);
		level.broadcastEntityEvent(this, THROWN);
	}

	/** The angle (radians) that lobs a stone {@code flat} blocks off and {@code up} blocks higher: the high arc, or 45 degrees if out of reach. */
	static double lob(double flat, double up) {
		double v = SiegeStone.SPEED, g = SiegeStone.GRAVITY;
		double root = v * v * v * v - g * (g * flat * flat + 2.0 * up * v * v);
		return root < 0.0 ? Math.PI / 4.0 : Math.atan((v * v + Math.sqrt(root)) / (g * flat));
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == THROWN) {
			this.sinceThrown = 0;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.isRemoved() || !(source.getEntity() instanceof Player player) || player.isSpectator()) {
			return false;
		}
		this.damage += player.isCreative() ? HEALTH : amount;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 1.0F, 0.8F);
		if (this.damage >= HEALTH) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 1.5F, 0.7F);
			level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 1.0, this.getZ(), 16, 0.7, 0.6, 0.7, 0.02);
			this.discard();
		}
		return true;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
