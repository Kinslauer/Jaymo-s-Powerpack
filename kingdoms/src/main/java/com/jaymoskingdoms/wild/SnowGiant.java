package com.jaymoskingdoms.wild;

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The snow giant of the cold plains: a white, shaggy great beast twice a grizzly's size, on the grizzly's body and
 * moves ({@link Grizzly}: the rear-up and slam, the paw swipes, the charge, the maul, the enraged roar). The Frostborn
 * count it a neighbour: it never goes for anyone on sight ({@link #wary}). Hurt, it warns and then fights, and it has
 * more than a bear does:
 * <ul>
 * <li><b>Chilling blows.</b> Everything it strikes is frozen through and slowed ({@link #CHILL_TICKS}).</li>
 * <li><b>The leap.</b> At a foe standing off ({@link #LEAP_MIN}..{@link #LEAP_MAX}) it jumps and comes down on them,
 * and the landing throws and chills everyone within {@link #LANDING_REACH}.</li>
 * <li><b>Thrown ice.</b> Once it is enraged it rears and hurls a block of ice at a foe out of reach; badly hurt
 * ({@link #BARRAGE_AT}) it hurls {@link #BARRAGE} one after another.</li>
 * </ul>
 * It always drops its gauntlet (Powers' Rimeclaw, when Powers is there).
 */
public class SnowGiant extends Grizzly {
	static final double HEALTH = 320.0, ARMOR = 10.0, DAMAGE = 13.0, SPEED = 0.29, KNOCKBACK_RESISTANCE = 0.95;
	/** Its blows: how long they freeze and slow (ticks), and the slowness level. */
	static final int CHILL_TICKS = 100, CHILL_SLOW = 1;
	/** Its leap: from how far, how often (ticks), its speed for each block of the gap, the most, its lift. */
	static final double LEAP_MIN = 6.0, LEAP_MAX = 15.0, LEAP_SPEED_PER_BLOCK = 0.11, LEAP_SPEED_MOST = 1.5, LEAP_UP = 0.75;
	static final int LEAP_EVERY = 20 * 9;
	/** Its landing: reach, damage, how hard it throws, and how high. */
	static final double LANDING_REACH = 5.0, LANDING_THROW = 1.3, LANDING_UP = 0.45;
	static final float LANDING_DAMAGE = 12.0F;
	/** Thrown ice: from how far, how often (ticks), when in the rear-up it leaves its paws, its speed, spread and damage. */
	static final double THROW_MIN = 7.0, THROW_MAX = 26.0;
	static final int THROW_EVERY = 20 * 6, THROW_AT = 18, THROW_GAP = 5;
	static final float THROW_SPEED = 1.5F, THROW_SPREAD = 3.0F, ICE_DAMAGE = 9.0F;
	/** Below this share of its health a throw is a barrage of this many. */
	static final float BARRAGE_AT = 0.2F;
	static final int BARRAGE = 4;
	/** Spawning: no other this near (blocks), and the share of tries that come to anything. */
	static final double ALONE = 256.0;
	static final float SPAWN_CHANCE = 0.12F;
	private static final Identifier GAUNTLET = Identifier.fromNamespaceAndPath("jaymospowers", "rimeclaw");

	/** Ice in flight. */
	private static final Set<Snowball> ICE = new HashSet<>();

	private int leapCooldown = LEAP_EVERY / 2, throwCooldown;
	private boolean leaping;
	private int leapTicks, throwsLeft;

	public SnowGiant(EntityType<? extends SnowGiant> type, Level level) {
		super(type, level);
		this.xpReward = 80;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Grizzly.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE).add(Attributes.STEP_HEIGHT, 1.5);
	}

	/** A block of thrown ice does its harm here: the snowball itself does none. */
	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(source.getDirectEntity() instanceof Snowball ice) || !ICE.remove(ice) || !(entity.level() instanceof ServerLevel level)) {
				return true;
			}
			ICE.removeIf(Entity::isRemoved);
			if (entity instanceof SnowGiant) {
				return false;
			}
			Entity thrower = ice.getOwner();
			DamageSource real = thrower instanceof LivingEntity living ? level.damageSources().mobAttack(living) : level.damageSources().freeze();
			if (entity.hurtServer(level, real, ICE_DAMAGE)) {
				chill(entity);
			}
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), ice.getX(), ice.getY(), ice.getZ(), 24,
				0.3, 0.3, 0.3, 0.1);
			level.playSound(null, ice.getX(), ice.getY(), ice.getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
			return false;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ICE.clear());
	}

	/** Natural spawning: under the sky in the cold plains, alone for a long way round, rare. */
	public static boolean checkSnowSpawn(EntityType<SnowGiant> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (!level.canSeeSky(pos) || !level.getBlockState(pos.below()).isSolid() || random.nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		return reason == EntitySpawnReason.CHUNK_GENERATION || level.getEntitiesOfClass(SnowGiant.class, new AABB(pos).inflate(ALONE)).isEmpty();
	}

	@Override
	protected boolean wary() {
		return true;
	}

	static void chill(LivingEntity struck) {
		struck.setTicksFrozen(Math.max(struck.getTicksFrozen(), struck.getTicksRequiredToFreeze() + CHILL_TICKS));
		struck.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHILL_TICKS, CHILL_SLOW));
	}

	@Override
	protected void struck(ServerLevel level, LivingEntity struck) {
		chill(struck);
		level.sendParticles(ParticleTypes.SNOWFLAKE, struck.getX(), struck.getY() + struck.getBbHeight() * 0.6, struck.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		this.leapCooldown--;
		this.throwCooldown--;
		LivingEntity target = this.getTarget();
		if (this.leaping) {
			if (++this.leapTicks > 4 && (this.onGround() || this.isInWater() || this.leapTicks > 60)) {
				this.leaping = false;
				this.landing(level);
			}
		} else if (target != null && target.isAlive() && !this.acting() && this.onGround() && this.hasLineOfSight(target)) {
			double d = this.distanceTo(target);
			if (this.enraged() && d > THROW_MIN && d < THROW_MAX && this.throwCooldown <= 0) {
				this.throwCooldown = THROW_EVERY;
				this.throwsLeft = this.getHealth() < this.getMaxHealth() * BARRAGE_AT ? BARRAGE : 1;
				this.getNavigation().stop();
				this.act("rear", THROW_AT + THROW_GAP * this.throwsLeft + 4);
				this.playSound(SoundEvents.POLAR_BEAR_WARNING, 2.5F, 0.4F);
			} else if (d > LEAP_MIN && d < LEAP_MAX && this.leapCooldown <= 0) {
				this.leapCooldown = LEAP_EVERY;
				Vec3 to = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
				this.setDeltaMovement(to.normalize().scale(Math.min(LEAP_SPEED_MOST, d * LEAP_SPEED_PER_BLOCK)).add(0.0, LEAP_UP, 0.0));
				this.syncVelocity = true;
				this.leaping = true;
				this.leapTicks = 0;
				com.jaymoskingdoms.PowersCompat.tellIncoming(this, target, 16, true);
				this.playSound(SoundEvents.POLAR_BEAR_WARNING, 2.5F, 0.5F);
			}
		}
		if (this.throwsLeft > 0 && this.acting() && "rear".equals(this.action())) {
			int run = this.actionRun();
			if (run >= THROW_AT && (run - THROW_AT) % THROW_GAP == 0) {
				this.throwsLeft--;
				if (target != null && target.isAlive()) {
					this.hurlIce(level, target);
				}
			}
		} else if (!this.acting()) {
			this.throwsLeft = 0;
		}
		super.customServerAiStep(level);
	}

	/** It comes down: everyone about it but its own kind is hurt, thrown and chilled. */
	private void landing(ServerLevel level) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(LANDING_REACH, 1.5, LANDING_REACH),
			e -> e != this && e.isAlive() && !(e instanceof SnowGiant) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			if (e.hurtServer(level, this.damageSources().mobAttack(this), LANDING_DAMAGE)) {
				Vec3 away = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
				Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(LANDING_THROW);
				e.setDeltaMovement(e.getDeltaMovement().add(push.x, LANDING_UP, push.z));
				e.needsSync = true;
				chill(e);
			}
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 0.3, this.getZ(), 80, LANDING_REACH * 0.5, 0.2, LANDING_REACH * 0.5, 0.08);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()), this.getX(), this.getY() + 0.2, this.getZ(), 60,
			LANDING_REACH * 0.4, 0.1, LANDING_REACH * 0.4, 0.2);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.5F);
		com.jaymoskingdoms.PowersCompat.impactFrame(level, this.position());
	}

	private void hurlIce(ServerLevel level, LivingEntity target) {
		Snowball ice = new Snowball(level, this, new ItemStack(Items.PACKED_ICE));
		ice.setPos(this.getX(), this.getY() + this.getBbHeight() * 0.9, this.getZ());
		Vec3 to = target.getEyePosition().subtract(ice.position());
		ice.shoot(to.x, to.y + to.horizontalDistance() * 0.12, to.z, THROW_SPEED, THROW_SPREAD);
		level.addFreshEntity(ice);
		ICE.add(ice);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5F, 0.4F);
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
			BuiltInRegistries.ITEM.getOptional(GAUNTLET).ifPresent(item -> this.spawnAtLocation(level, new ItemStack(item)));
		}
		super.die(source);
	}
}
