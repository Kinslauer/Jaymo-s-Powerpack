package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The great necromancer (fifth edition's lich, Andy's pick D5), built from the court necromancer: the same hood and black
 * robe, the same spells (spikes of bone, raised dead, the drain: {@link Sorcery#cast}), on a boss's health, with a boss bar
 * for whoever has struck him, three {@link Legendary} resistances, a {@link BossMoves} shriek, and a crypt that fights for him
 * ({@link LairBeats}). He sleeps on his dais until someone comes near or strikes him.
 * <p>
 * He keeps his life in a vessel hidden near his crypt ({@link Necropolis}): killed, he rises again a few days later while the
 * vessel stands, and for good only once it is smashed.
 */
public class GreatNecromancer extends Monster implements Sorcery.Caster {
	/** His health, armour, speed and blow. */
	static final double HEALTH = 260.0, ARMOR = 6.0, SPEED = 0.27, DAMAGE = 8.0, FOLLOW = 40.0;
	/** How far he strays from his crypt (blocks). */
	static final int TERRITORY = 28;
	/** How near a player must come to wake him (blocks), and how often he casts (ticks). */
	static final double WAKE_RANGE = 12.0;
	static final int CAST_EVERY = 40;
	/** The share of his health, below which he calls up the dead twice as fast. */
	static final float DESPERATE = 0.4F;

	private final int[] timers = new int[Sorcery.TIMERS];
	private @Nullable BlockPos crypt;
	private boolean dormant;
	private @Nullable ServerBossEvent bar;

	public GreatNecromancer(EntityType<? extends GreatNecromancer> type, Level level) {
		super(type, level);
		this.xpReward = 120;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.9));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	/** Stands him in his crypt at {@code hall}, asleep or awake. */
	void settle(BlockPos hall, boolean asleep) {
		this.crypt = hall;
		this.setHomeTo(hall, TERRITORY);
		this.setPersistenceRequired();
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Arcana.BONE_STAFF));
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		this.setCustomName(Component.translatable("entity.jaymoskingdoms.great_necromancer"));
		this.setCustomNameVisible(false);
		this.dormant = asleep;
		this.setNoAi(asleep);
	}

	/** The floor of his crypt, or null. */
	public @Nullable BlockPos crypt() {
		return this.crypt;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			for (int i = 0; i < this.timers.length; i++) {
				if (this.timers[i] > 0) {
					this.timers[i] -= this.getHealth() < this.getMaxHealth() * DESPERATE ? 2 : 1;
				}
			}
			if (this.dormant && (this.tickCount % 10 == 0 && level.getNearestPlayer(this, WAKE_RANGE) instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator())) {
				this.wake(level);
			}
		}
	}

	private void wake(ServerLevel level) {
		this.dormant = false;
		this.setNoAi(false);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7F, 1.4F);
		Fx.sigil(level, this.position().add(0.0, 0.1, 0.0), 4.0F, 0x7A1FA2, 40);
		level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0, this.getZ(), 20, 0.5, 0.8, 0.5, 0.03);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.dormant) {
			this.wake(level);
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.bar == null) {
			this.bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		BossBars.show(level, this, this.bar);
		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive() && this.tickCount % CAST_EVERY == 0 && this.distanceTo(target) <= Sorcery.RANGE && this.getSensing().hasLineOfSight(target)) {
			Sorcery.cast(this, target);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
		super.remove(reason);
	}

	@Override
	public void die(DamageSource source) {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			Thralls.release(level, this.getUUID());
			level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0, this.getZ(), 40, 0.5, 1.0, 0.5, 0.05);
		}
	}

	/** He leaves nothing while he is to rise again: his bones fall to ash. */
	@Override
	protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
		if (Necropolis.rises(this)) {
			level.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 1.0, this.getZ(), 40, 0.5, 1.0, 0.5, 0.03);
			return;
		}
		super.dropAllDeathLoot(level, source);
		this.spawnAtLocation(level, new ItemStack(Arcana.BONE_STAFF));
	}

	@Override
	public boolean isPushable() {
		return !this.dormant && super.isPushable();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("dormant", this.dormant);
		if (this.crypt != null) {
			output.putInt("crypt_x", this.crypt.getX());
			output.putInt("crypt_y", this.crypt.getY());
			output.putInt("crypt_z", this.crypt.getZ());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.dormant = input.getBooleanOr("dormant", false);
		if (input.getIntOr("crypt_y", Integer.MIN_VALUE) != Integer.MIN_VALUE) {
			this.crypt = new BlockPos(input.getIntOr("crypt_x", 0), input.getIntOr("crypt_y", 0), input.getIntOr("crypt_z", 0));
			this.setHomeTo(this.crypt, TERRITORY);
		}
		this.setNoAi(this.dormant);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.dormant ? null : SoundEvents.WITHER_SKELETON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITHER_SKELETON_DEATH;
	}

	// ---- Sorcery.Caster: he casts as a court necromancer does ----------------------------------------------------------

	@Override
	public PathfinderMob mob() {
		return this;
	}

	@Override
	public int role() {
		return Sorcery.NECROMANCER;
	}

	@Override
	public int factionId() {
		return -1;
	}

	@Override
	public int[] timers() {
		return this.timers;
	}

	@Override
	public @Nullable LivingEntity target() {
		return this.getTarget();
	}

	@Override
	public Climate climate() {
		return Climate.PLAINS;
	}

	@Override
	public boolean foe(LivingEntity e) {
		if (e == this || !e.isAlive() || e instanceof Player p && (p.isCreative() || p.isSpectator()) || Thralls.refuses(this, e)) {
			return false;
		}
		return e instanceof Player || e == this.getTarget() || e instanceof Mob m && m.getTarget() == this;
	}

	@Override
	public List<LivingEntity> fightingAllies(ServerLevel level) {
		return new ArrayList<>();
	}

	@Override
	public List<LivingEntity> side(ServerLevel level) {
		List<LivingEntity> out = new ArrayList<>();
		out.add(this);
		return out;
	}
}
