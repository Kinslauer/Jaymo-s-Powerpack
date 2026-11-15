package com.jaymoskingdoms.oldage;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.Fx;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (medieval picks, cv-rulers) A ruler of the old age, asleep in their tomb under their city ({@link OldRuins}) with their
 * guard round them ({@link TombGuard}), each people's own ({@link People}): they wake when the tomb is broken into (a
 * seal broken, {@link OldAge#TOMB_SEAL}), when someone comes right up to them, or when struck, and their guard wakes
 * with them. A boss: a bar in their people's colour, an entrance film ({@code BossIntros}), their own fight music
 * ({@code FoeThemes}, {@link People#theme}).
 * <p>
 * Each fights with their people's arms (Jaymo's Powers' {@code OldAgeArms}) and has their people's move every so often:
 * the Sunstep king's <b>sun curse</b> (hunger and weariness on everyone near), the Pillared consul's <b>shield bash</b>
 * (and the guard rallied), the Barrow-king's <b>grave chill</b> (a cold stamp that freezes and slows), the Court's
 * empress's <b>blink cut</b> (gone, and behind you). Brought to half their health they call up more of their guard,
 * once. Fallen, they leave their arms, the old money (emeralds) and their people's relic.
 */
public class OldRuler extends Monster {
	// ---- tuning ----
	static final double HEALTH = 200.0, ARMOR = 8.0, SPEED = 0.26, DAMAGE = 7.0, FOLLOW = 32.0;
	/** How far they stray from their tomb (blocks). */
	static final int TERRITORY = 20;
	/** How near a player must come to wake them (blocks), and how far round they wake their guard. */
	static final double WAKE_RANGE = 4.0, GUARD_RANGE = 24.0;
	/** How often their people's move comes (ticks). */
	static final int MOVE_EVERY = 160;
	/** The share of their health at which more of their guard rise, and how many. */
	static final float RALLY_AT = 0.5F;
	static final int RALLIED = 2;
	/** The sun curse: how far, how long. */
	static final double CURSE_RANGE = 7.0;
	static final int CURSE_TICKS = 100;
	/** The shield bash: how near, how hard, how far it throws; the rally's strength, how long. */
	static final double BASH_RANGE = 3.5, BASH_PUSH = 1.2;
	static final float BASH_DAMAGE = 4.0F;
	static final int RALLY_TICKS = 120;
	/** The grave chill: how far, how hard, how long it freezes. */
	static final double CHILL_RANGE = 5.0;
	static final float CHILL_DAMAGE = 4.0F;
	static final int CHILL_FREEZE = 160, CHILL_SLOW = 60;
	/** The blink cut: the farthest they blink from. */
	static final double BLINK_RANGE = 12.0;
	/** What they leave: emeralds (the old money). */
	static final int EMERALDS_MIN = 8, EMERALDS_MAX = 16;

	private static final EntityDataAccessor<Integer> PEOPLE = SynchedEntityData.defineId(OldRuler.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(OldRuler.class, EntityDataSerializers.BOOLEAN);

	private @Nullable BlockPos tomb;
	private boolean rallied;
	private @Nullable ServerBossEvent bar;

	public OldRuler(EntityType<? extends OldRuler> type, Level level) {
		super(type, level);
		this.xpReward = 100;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.KNOCKBACK_RESISTANCE, 0.7);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(PEOPLE, 0);
		builder.define(DORMANT, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.9));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	public People people() {
		return People.byId(this.entityData.get(PEOPLE));
	}

	/** Awake (out of their sleep): only then a boss, with a bar and a film. */
	public boolean awake() {
		return !this.entityData.get(DORMANT);
	}

	/** Lays them in their tomb at {@code at}, asleep or awake, in their people's arms. */
	void settle(People people, BlockPos at, boolean asleep) {
		this.entityData.set(PEOPLE, people.ordinal());
		this.tomb = at;
		this.setHomeTo(at, TERRITORY);
		this.setPersistenceRequired();
		this.setItemSlot(EquipmentSlot.MAINHAND, arm(people.weapon, people == People.MOUND ? Items.IRON_AXE : Items.IRON_SWORD));
		if (!people.shield.isEmpty()) {
			this.setItemSlot(EquipmentSlot.OFFHAND, arm(people.shield, Items.SHIELD));
		}
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
		this.setCustomName(Component.translatable("entity.jaymoskingdoms.old_ruler." + people.id));
		this.setCustomNameVisible(false);
		this.entityData.set(DORMANT, asleep);
		this.setNoAi(asleep);
	}

	/** One of the old age's arms (Jaymo's Powers', by id), or {@code otherwise} without it. */
	static ItemStack arm(String id, net.minecraft.world.item.Item otherwise) {
		ItemStack stack = com.jaymoskingdoms.Spoils.powers(id);
		return stack.isEmpty() ? new ItemStack(otherwise) : stack;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && !this.awake() && this.tickCount % 10 == 0
			&& level.getNearestPlayer(this, WAKE_RANGE) instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator() && this.hasLineOfSight(p)) {
			this.wake(level);
		}
	}

	/** They wake, and their guard with them. */
	void wake(ServerLevel level) {
		if (this.awake()) {
			return;
		}
		this.entityData.set(DORMANT, false);
		this.setNoAi(false);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.HOSTILE, 1.4F, 0.5F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.2F, 0.5F);
		Fx.sigil(level, this.position().add(0.0, 0.1, 0.0), 3.5F, this.colour(), 40);
		level.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 1.2, this.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
		for (TombGuard g : level.getEntitiesOfClass(TombGuard.class, this.getBoundingBox().inflate(GUARD_RANGE))) {
			g.wake(level);
		}
	}

	int colour() {
		return switch (this.people()) {
			case SUNSTEP -> 0xF2C14E;
			case PILLARED -> 0xC8463C;
			case MOUND -> 0xB8D4E8;
			case COURT -> 0x7A4FB8;
		};
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		this.wake(level);
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.awake()) {
			return;
		}
		if (this.bar == null) {
			this.bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), this.people().bar, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		BossBars.show(level, this, this.bar);
		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive() && this.tickCount % MOVE_EVERY == 0 && this.distanceTo(target) <= FOLLOW) {
			this.move(level, target);
		}
		if (!this.rallied && this.getHealth() < this.getMaxHealth() * RALLY_AT) {
			this.rallied = true;
			this.rally(level);
		}
	}

	/** Their people's move. */
	private void move(ServerLevel level, LivingEntity target) {
		Vec3 at = this.position();
		int colour = this.colour();
		switch (this.people()) {
			case SUNSTEP -> {
				Fx.ring(level, at.add(0.0, 0.2, 0.0), (float) CURSE_RANGE, colour);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.HUSK_CONVERTED_TO_ZOMBIE, SoundSource.HOSTILE, 1.0F, 0.6F);
				for (Player p : this.near(level, CURSE_RANGE)) {
					p.addEffect(new MobEffectInstance(MobEffects.HUNGER, CURSE_TICKS, 1));
					p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CURSE_TICKS / 2, 0));
					p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, CURSE_TICKS, 0));
				}
			}
			case PILLARED -> {
				for (TombGuard g : level.getEntitiesOfClass(TombGuard.class, this.getBoundingBox().inflate(GUARD_RANGE), TombGuard::isAlive)) {
					g.addEffect(new MobEffectInstance(MobEffects.STRENGTH, RALLY_TICKS, 0));
					g.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RALLY_TICKS, 0));
				}
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6F, 1.4F);
				if (this.distanceTo(target) <= BASH_RANGE) {
					target.knockback(BASH_PUSH, at.x - target.getX(), at.z - target.getZ(), this.damageSources().mobAttack(this), BASH_DAMAGE);
					target.hurtServer(level, this.damageSources().mobAttack(this), BASH_DAMAGE);
					level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
					Fx.blast(level, target.position().add(0.0, 1.0, 0.0), 1.2F, colour);
				}
			}
			case MOUND -> {
				Fx.ring(level, at.add(0.0, 0.1, 0.0), (float) CHILL_RANGE, colour);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.HOSTILE, 1.5F, 0.5F);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.5F, 0.5F);
				level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 0.3, this.getZ(), 40, CHILL_RANGE / 2, 0.2, CHILL_RANGE / 2, 0.02);
				for (Player p : this.near(level, CHILL_RANGE)) {
					p.hurtServer(level, this.damageSources().mobAttack(this), CHILL_DAMAGE);
					p.setTicksFrozen(Math.max(p.getTicksFrozen(), CHILL_FREEZE));
					p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHILL_SLOW, 1));
				}
			}
			case COURT -> {
				if (this.distanceTo(target) > BLINK_RANGE) {
					return;
				}
				Vec3 behind = target.position().subtract(Vec3.directionFromRotation(0.0F, target.getYRot()).scale(1.6));
				BlockPos feet = BlockPos.containing(behind);
				if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					|| !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
					return;
				}
				Fx.burst(level, this.getX(), this.getY() + 1.0, this.getZ(), colour, 1.2F, 30, 0.3, 0.8, 0.3);
				this.teleportTo(behind.x, target.getY(), behind.z);
				this.getLookControl().setLookAt(target);
				Fx.burst(level, this.getX(), this.getY() + 1.0, this.getZ(), colour, 1.2F, 30, 0.3, 0.8, 0.3);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 0.7F);
				this.doHurtTarget(level, target);
			}
		}
	}

	private List<Player> near(ServerLevel level, double range) {
		return level.getEntitiesOfClass(Player.class, new AABB(this.blockPosition()).inflate(range),
			p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceTo(this) <= range);
	}

	/** At half their health: more of their guard rise from the floor round them. */
	private void rally(ServerLevel level) {
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 1.0F, 0.5F);
		for (int i = 0; i < RALLIED; i++) {
			double a = this.getRandom().nextDouble() * Math.PI * 2.0;
			BlockPos at = BlockPos.containing(this.getX() + Math.cos(a) * 2.5, this.getY(), this.getZ() + Math.sin(a) * 2.5);
			if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) {
				at = this.blockPosition();
			}
			TombGuard g = OldAge.GUARD.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (g == null) {
				continue;
			}
			g.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, this.getYRot(), 0.0F);
			g.settle(this.people(), this.tomb != null ? this.tomb : this.blockPosition(), false, i % 2 == 1);
			level.addFreshEntity(g);
			g.setTarget(this.getTarget());
			Fx.pool(level, g.position().add(0.0, 0.05, 0.0), 1.2F, this.colour(), 30);
			level.sendParticles(ParticleTypes.ASH, g.getX(), g.getY() + 0.5, g.getZ(), 20, 0.4, 0.5, 0.4, 0.02);
		}
	}

	/** Killed, discarded or unloaded with their chunk: the bar goes from every screen. */
	@Override
	public void onRemoval(RemovalReason reason) {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
		super.onRemoval(reason);
	}

	@Override
	public void die(DamageSource source) {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 1.0, this.getZ(), 50, 0.5, 1.0, 0.5, 0.04);
			Fx.burst(level, this.getX(), this.getY() + 1.2, this.getZ(), this.colour(), 2.0F, 60, 0.6, 1.2, 0.6);
		}
	}

	/** Their arms, the old money and their people's relic. */
	@Override
	protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
		super.dropAllDeathLoot(level, source);
		People p = this.people();
		this.spawnAtLocation(level, arm(p.weapon, p == People.MOUND ? Items.IRON_AXE : Items.IRON_SWORD));
		if (!p.shield.isEmpty()) {
			this.spawnAtLocation(level, arm(p.shield, Items.SHIELD));
		}
		this.spawnAtLocation(level, new ItemStack(Items.EMERALD, EMERALDS_MIN + this.getRandom().nextInt(EMERALDS_MAX - EMERALDS_MIN + 1)));
		this.spawnAtLocation(level, new ItemStack(OldAge.relic(p)));
	}

	@Override
	public boolean isPushable() {
		return this.awake() && super.isPushable();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("people", this.entityData.get(PEOPLE));
		output.putBoolean("dormant", !this.awake());
		output.putBoolean("rallied", this.rallied);
		if (this.tomb != null) {
			output.putLong("tomb", this.tomb.asLong());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(PEOPLE, input.getIntOr("people", 0));
		this.entityData.set(DORMANT, input.getBooleanOr("dormant", false));
		this.rallied = input.getBooleanOr("rallied", false);
		long t = input.getLongOr("tomb", Long.MIN_VALUE);
		if (t != Long.MIN_VALUE) {
			this.tomb = BlockPos.of(t);
			this.setHomeTo(this.tomb, TERRITORY);
		}
		this.setNoAi(!this.awake());
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.awake() ? SoundEvents.WITHER_SKELETON_AMBIENT : null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITHER_SKELETON_DEATH;
	}
}
