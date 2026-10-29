package com.jaymospowers.power.chrono.future;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * An Omen: a manned mech of the future, in red or blue (two entity types on one class, like the war mech and the
 * android mech, because each build has its own model and skin). It is a pilot's machine, not a patrol's: it fights
 * androids, android and war mechs and the dead on sight, and is neutral toward players unless one hurts it or its drone
 * ({@link FutureWorky}); then it answers, and so does the rest of its squad. It hoses its target with a heavy arm cannon
 * (the future's own gun code, {@link FutureGuns}) and smashes what gets close. Beaten, it leaves scrap, now and then a
 * cockpit log, and its pilot climbs out of the hatch (a curious survivor from the future, an MCA villager: {@link FutureOmenPilots}).
 * <p>Squads turn up rarely, with their drones, in the future only ({@link FutureOmens}). Nothing its own kind fires
 * (or its drones) does it harm.
 */
public class FutureOmen extends PathfinderMob {
	static final double HEALTH = 320.0, DAMAGE = 22.0, SPEED = 0.22, FOLLOW = 56.0, ARMOR = 22.0, TOUGHNESS = 10.0;
	/** Its arm cannon's bursts out to here, and closer than this it smashes instead. */
	static final double CANNON_RANGE = 40.0, CANNON_NEAREST = 4.0;
	/** A heavier machine gun than the war mech's: out to 40, 4.5 a round, bursts of 12, a pause of about 3 s. */
	static final FutureGuns.Kind CANNON_KIND = new FutureGuns.Kind(40.0, 4.5F, 1, 3.5F, 55, 12, 2);
	static final FutureGuns.Gun CANNON = new FutureGuns.Gun("tacz:m249", CANNON_KIND, "", 0);
	/** It looks for foes this often (ticks). */
	static final int SCAN_EVERY = 10;
	/** The pilot's chance of getting out alive, and the chance a cockpit log is left. */
	static final float PILOT_CHANCE = 0.85F, LOG_CHANCE = 0.35F;
	/** The pilot's few seconds of toughness after the blast (ticks). */
	static final int PILOT_SHIELD = 200;
	/** Scoreboard tag on a pilot out of an Omen. */
	public static final String PILOT_TAG = "jaymospowers.omen_pilot";

	private static final EntityDataAccessor<Boolean> DATA_AIMING = SynchedEntityData.defineId(FutureOmen.class, EntityDataSerializers.BOOLEAN);

	private int attackAnimationTick;
	private int aimingFor;
	private boolean pilotOut;

	public FutureOmen(EntityType<? extends FutureOmen> type, Level level) {
		super(type, level);
		this.xpReward = 70;
		addTag("jaymoskitchen.no_corpse");
	}

	/** The blue build rather than the red? */
	public boolean blue() {
		return getType() == FutureOmens.OMEN_BLUE;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.ARMOR, ARMOR)
			.add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_AIMING, false);
	}

	/** Who an Omen fights on sight: androids, every mech but its own kind, and the dead of the future. */
	static boolean hostileTo(LivingEntity entity) {
		return entity.isAlive() && (entity instanceof FutureAndroid || entity instanceof FutureMech || entity instanceof Zombie);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		// whoever hurts it (a player included) draws it and its squad; otherwise it hunts the machines and the dead
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, FutureOmen.class).setAlertOthers(FutureOmen.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, SCAN_EVERY, true, false,
			(target, level) -> hostileTo(target)));
	}

	/** Are its arm cannons up? */
	public boolean aiming() {
		return this.entityData.get(DATA_AIMING);
	}

	public int getAttackAnimationTick() {
		return this.attackAnimationTick;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.attackAnimationTick > 0) {
			this.attackAnimationTick--;
		}
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity foe = getTarget();
		if (aimingFor > 0 && --aimingFor == 0) {
			this.entityData.set(DATA_AIMING, false);
		}
		if (foe != null && foe.isAlive()) {
			double d = distanceTo(foe);
			if (hasLineOfSight(foe) && d > CANNON_NEAREST && d < CANNON_RANGE) {
				getLookControl().setLookAt(foe, 30.0F, 30.0F);
				this.entityData.set(DATA_AIMING, true);
				aimingFor = 30;
				FutureGuns.tickWith(level, this, foe, CANNON);
			}
		}
		if (getHealth() < getMaxHealth() * 0.3F && this.tickCount % 6 == 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.8, getZ(), 2, 0.4, 0.3, 0.4, 0.01);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.62, getZ(), 3, 0.6, 0.6, 0.6, 0.1);
		}
	}

	/** Its own kind's rounds (and its drones') do it no harm: the cannon hits whatever is first in line. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof FutureOmen || source.getEntity() instanceof FutureWorky) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		this.attackAnimationTick = 12;
		level.broadcastEntityEvent(this, (byte) 4);
		float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
		DamageSource source = damageSources().mobAttack(this);
		boolean hurt = target.hurtServer(level, source, damage / 2.0F + getRandom().nextInt(Math.max(1, (int) damage)));
		if (hurt) {
			Vec3 away = target.position().subtract(position()).multiply(1.0, 0.0, 1.0).normalize();
			target.setDeltaMovement(target.getDeltaMovement().add(away.x * 0.9, 0.7, away.z * 0.9));
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 12, 0.4, 0.4, 0.4, 0.3);
		}
		playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.6F, 0.55F);
		return hurt;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 4) {
			this.attackAnimationTick = 12;
			playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.6F, 0.55F);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		spawnAtLocation(level, new ItemStack(Items.IRON_INGOT, 6 + getRandom().nextInt(8)));
		spawnAtLocation(level, new ItemStack(Items.REDSTONE, 8 + getRandom().nextInt(9)));
		spawnAtLocation(level, new ItemStack(Items.COPPER_INGOT, 3 + getRandom().nextInt(5)));
		if (getRandom().nextFloat() < 0.25F) {
			spawnAtLocation(level, new ItemStack(Items.NETHERITE_SCRAP));
		}
		if (getRandom().nextFloat() < LOG_CHANCE) {
			spawnAtLocation(level, log(blue()));
		}
		level.explode(this, getX(), getY() + 1.8, getZ(), 1.5F, Level.ExplosionInteraction.NONE);
	}

	/** The hatch pops and the pilot climbs out (after the blast, so they are clear of it). */
	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!pilotOut && level() instanceof ServerLevel level) {
			pilotOut = true;
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 3.6, getZ(), 14, 0.5, 0.3, 0.5, 0.04);
			level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 3.9, getZ(), 8, 0.3, 0.2, 0.3, 0.06);
			playSound(SoundEvents.IRON_TRAPDOOR_OPEN, 1.5F, 0.6F);
			if (getRandom().nextFloat() < PILOT_CHANCE) {
				ejectPilot(level);
			}
		}
	}

	private void ejectPilot(ServerLevel level) {
		double angle = getRandom().nextDouble() * Math.PI * 2.0;
		LivingEntity pilot = FutureOmenPilots.spawn(level, new Vec3(getX() + Math.cos(angle) * 0.8, getY() + getBbHeight() * 0.85, getZ() + Math.sin(angle) * 0.8),
			getRandom().nextFloat() * 360.0F);
		if (pilot == null) {
			return;
		}
		pilot.setDeltaMovement(Math.cos(angle) * 0.25, 0.45, Math.sin(angle) * 0.25);
		pilot.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, PILOT_SHIELD, 4));
		pilot.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, PILOT_SHIELD, 0));
		pilot.addEffect(new MobEffectInstance(MobEffects.SPEED, PILOT_SHIELD * 2, 1));
	}

	/**
	 * A few pages from the cockpit, as the pilot wrote them: nothing explained, only what a pilot sees and what is
	 * left on the dash.
	 */
	static ItemStack log(boolean blue) {
		String[] pages = blue
			? new String[] {
				"Blue Two, whatever day this is.\n\nLos Perrito's lights went out district by district. From the hatch you could watch the dark come up Main Street.",
				"Worky mapped three clear streets. Two are not clear any more.\n\nThe dead come in at night. The tin ones come in always.",
				"My lunch is still on the dash.\n\nI will finish the sortie. Somebody has to be standing when the last bus gets out."}
			: new String[] {
				"Red One, sortie 31.\n\nBridge held again. Worky has the east slope lit up like a pinball machine, six of them, all walking the same slow walk.",
				"The fire doesn't turn them. The tin ones keep their formation and the others keep coming up behind.\n\nI have stopped counting buses.",
				"Fuel at a third. The hatch seal is sticking.\n\nIf you are reading this, there is a card under the seat. It says to leave the porch light on."};
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> list = new ArrayList<>();
		for (String page : pages) {
			list.add(Filterable.passThrough(Component.literal(page)));
		}
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Cockpit Log"), "Unsigned", 0, list, true));
		return book;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.IRON_GOLEM_STEP, 2.0F, 0.45F);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_DAMAGE;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.45F;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return distance > 160.0 * 160.0;
	}
}
