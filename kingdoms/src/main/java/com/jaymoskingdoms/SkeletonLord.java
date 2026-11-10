package com.jaymoskingdoms;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.monsters.Monsters;
import com.jaymoskingdoms.monsters.SilverSkeleton;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.351) The <b>Skeleton Lord</b>: a dead sorcerer-king in a ragged robe, a crystal-topped staff in his fist, drawn as
 * the Blockbench Workshop's "Skeleton Lord" (CC BY 4.0, with its own animations). He sleeps as a statue overgrown with
 * amethyst in a dungeon ({@link GolemLairs}: {@link #LAIR_SHARE} of the Overworld's rogue golem lairs hold him instead),
 * hard to hurt and standing still, until a player comes near or strikes him. Then the crystals burst off him and he
 * laughs, and fights: blows of his staff, and every so often a spell that throws everything round him back and hurts it.
 * A purple boss bar once he and a player have traded blows. Drops bones, amethyst and silver coins. Ops can hatch one from
 * its spawn egg (it wakes at once).
 *
 * <p>Drawn with GeckoLib (SkeletonLordRenderer; the model and its animations, our three added, in
 * kingdoms/art/skeleton_lord.bbmodel). He is the Stormborn Tomb's boss too, and fights with more than his spell: his staff
 * swung at whoever he strikes; a slam of the staff's foot that cracks the ground round him and throws everything near up;
 * and his Grave Call, at {@link #CALL_AT} of his health, raising silver skeletons that climb up out of the floor round him.
 */
public class SkeletonLord extends Monster implements GeoEntity {
	public static EntityType<SkeletonLord> TYPE;
	public static Item SPAWN_EGG;

	static final double HEALTH = 260.0, SPEED = 0.25, DAMAGE = 12.0, ARMOR = 8.0, KNOCKBACK_RESISTANCE = 0.8, FOLLOW = 32.0;
	/** Share of the Overworld's rogue golem lairs that hold a Skeleton Lord instead. */
	public static final float LAIR_SHARE = 0.35F;
	/** A player this near (blocks), in sight, wakes him; asleep, his crystals keep off this much of a blow. */
	static final double WAKE_RANGE = 8.0;
	static final float STATUE_GUARD = 0.75F;
	/** How long his waking laugh holds him (ticks). */
	public static final int WAKE_TICKS = 40;
	/** His spell: how often (ticks), at a foe this near (blocks), how long it takes (ticks, its animation's 0.96 s), the tick it
	 * lands on, how far round him it reaches (blocks), how hard it throws and lifts, and its harm. */
	static final int SPELL_COOLDOWN = 120, CAST_TICKS = 19, CAST_LANDS = 10;
	static final double SPELL_RANGE = 7.0, SPELL_RADIUS = 6.0, SPELL_PUSH = 2.2, SPELL_LIFT = 0.55;
	static final float SPELL_DAMAGE = 6.0F;
	/** The chance his spell, and a blow of his staff, leaves his curse on a player it lands on (SkeletonCurse). */
	static final float CURSE_SPELL = 0.5F, CURSE_STAFF = 0.15F;
	/** What he drops: bones, amethyst shards, silver coins (0.7.486; once emeralds), (min, max of each). */
	static final int BONES_MIN = 6, BONES_MAX = 10, AMETHYST_MIN = 4, AMETHYST_MAX = 8, SILVER_MIN = 3, SILVER_MAX = 6;
	public static final byte WAKE = 96, CAST = 97;
	/** The slam: how often (ticks), at a foe within this reach (blocks), how long it takes (its 1.5 s animation), the tick the
	 * staff lands, how far round him it reaches, its harm and how hard it throws up. */
	static final int SLAM_COOLDOWN = 160, SLAM_TICKS = 30, SLAM_LANDS = 15;
	static final double SLAM_RANGE = 8.0, SLAM_RADIUS = 4.5, SLAM_LIFT = 0.8;
	static final float SLAM_DAMAGE = 9.0F;
	/** His Grave Call: at these shares of his health (each once), how long it takes (its 2 s animation), the tick the dead come
	 * up, how many each time, how far from him (blocks), and the most of his risen alive near him at once. */
	static final float[] CALL_AT = {0.75F, 0.4F};
	static final int[] CALL_COUNT = {2, 3};
	static final int CALL_TICKS = 40, CALL_LANDS = 24, CALL_MAX_RISEN = 6;
	static final double CALL_MIN = 3.0, CALL_MAX = 6.0, RISEN_NEAR = 24.0;
	/** Walking faster than this (the walk animation's speed) he runs. */
	static final float RUN_FROM = 0.6F;

	static final RawAnimation STATUE = RawAnimation.begin().thenLoop("statue");
	static final RawAnimation LAUGH = RawAnimation.begin().thenLoop("NYEHEHEHE");
	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
	static final RawAnimation SPELL = RawAnimation.begin().thenPlay("spell_knockback");
	static final RawAnimation STAFF_SWING = RawAnimation.begin().thenPlay("staff_swing");
	static final RawAnimation SLAM = RawAnimation.begin().thenPlay("slam");
	static final RawAnimation GRAVE_CALL = RawAnimation.begin().thenPlay("grave_call");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int slamming, slamIn = SLAM_COOLDOWN / 2, calling, calls;

	private static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(SkeletonLord.class, EntityDataSerializers.BOOLEAN);

	private final ServerBossEvent bar = new ServerBossEvent(this.getUUID(), Component.translatable("entity.jaymoskingdoms.skeleton_lord"),
		BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
	/** He and a player have traded blows since he woke: only then is his boss bar shown. */
	private boolean engaged;
	private int waking, casting, spellIn = SPELL_COOLDOWN / 2;
	/** (Client) the tick he woke and the tick he last began a spell. */
	private int wokeAt = -1000, castAt = -1000;

	public SkeletonLord(EntityType<? extends SkeletonLord> type, Level level) {
		super(type, level);
		this.bar.setVisible(false);
		this.xpReward = 60;
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("skeleton_lord"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<SkeletonLord>of(SkeletonLord::new, MobCategory.MONSTER)
			.sized(1.2F, 2.9F).fireImmune().clientTrackingRange(10).build(key));
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
		ResourceKey<Item> eggKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("skeleton_lord_spawn_egg"));
		SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, eggKey, new SpawnEggItem(new Item.Properties().setId(eggKey).spawnEgg(TYPE)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(SPAWN_EGG));
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.ARMOR, ARMOR).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
			.add(Attributes.FOLLOW_RANGE, FOLLOW);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DORMANT, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** Set down asleep, a crystal statue, for a lair. */
	public void sleep() {
		this.entityData.set(DORMANT, true);
		this.setPersistenceRequired();
	}

	public boolean dormant() {
		return this.entityData.get(DORMANT);
	}

	/** (Client) ticks since he woke, and since he last began a spell. */
	public int sinceWoke() {
		return this.tickCount - this.wokeAt;
	}

	public int sinceCast() {
		return this.tickCount - this.castAt;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.stepTick = this.tickCount;
		if (this.dormant()) {
			Player near = level.getNearestPlayer(this, WAKE_RANGE);
			if (near != null && !near.isSpectator() && !near.isCreative() && this.hasLineOfSight(near)) {
				this.wake(level);
			}
			return;
		}
		if (this.waking > 0) {
			this.waking--;
		}
		if (this.slamming > 0) {
			if (--this.slamming == SLAM_TICKS - SLAM_LANDS) {
				this.slamLands(level);
			}
		} else if (this.calling > 0) {
			if (--this.calling == CALL_TICKS - CALL_LANDS) {
				this.graveCall(level);
			}
		} else if (this.casting == 0 && this.waking == 0) {
			this.nextMove(level);
		}
		if (this.slamming == 0 && this.calling == 0) {
			this.spell(level);
		}
		this.bar.setVisible(this.engaged);
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
	}

	private void wake(ServerLevel level) {
		if (!this.dormant()) {
			return;
		}
		this.entityData.set(DORMANT, false);
		this.waking = WAKE_TICKS;
		this.getNavigation().stop();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_CLUSTER.defaultBlockState()), this.getX(), this.getY() + 1.5,
			this.getZ(), 80, 0.6, 1.2, 0.6, 0.2);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.6F, 0.7F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.5F);
		level.broadcastEntityEvent(this, WAKE);
	}

	/** His spell: begun at a foe close by, it lands partway through and throws everything round him back. */
	private void spell(ServerLevel level) {
		if (this.casting > 0) {
			this.casting--;
			if (this.casting == CAST_TICKS - CAST_LANDS) {
				this.blast(level);
			}
			return;
		}
		if (--this.spellIn > 0 || this.waking > 0) {
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || this.distanceTo(target) > SPELL_RANGE) {
			this.spellIn = 10;
			return;
		}
		this.casting = CAST_TICKS;
		this.spellIn = SPELL_COOLDOWN;
		this.getNavigation().stop();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.5F, 0.6F);
		level.broadcastEntityEvent(this, CAST);
	}

	/** Starts his Grave Call when his health has fallen past the next of {@link #CALL_AT}, else a slam when it's due. */
	private void nextMove(ServerLevel level) {
		if (this.calls < CALL_AT.length && this.getHealth() <= this.getMaxHealth() * CALL_AT[this.calls]) {
			this.calling = CALL_TICKS;
			this.getNavigation().stop();
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2.0F, 0.5F);
			this.triggerAnim("action", "grave_call");
			return;
		}
		LivingEntity target = this.getTarget();
		if (--this.slamIn > 0 || target == null || this.distanceTo(target) > SLAM_RANGE || !this.onGround()) {
			return;
		}
		this.slamming = SLAM_TICKS;
		this.slamIn = SLAM_COOLDOWN;
		this.getNavigation().stop();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 0.6F, 0.5F);
		this.triggerAnim("action", "slam");
	}

	/** The staff's foot comes down: everything on the ground round him is hurt and thrown up. */
	private void slamLands(ServerLevel level) {
		BlockState ground = level.getBlockState(this.blockPosition().below());
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(SLAM_RADIUS),
			e -> e != this && e.isAlive() && !(e instanceof Monster) && e.onGround() && this.distanceTo(e) <= SLAM_RADIUS)) {
			e.hurtServer(level, this.damageSources().mobAttack(this), SLAM_DAMAGE);
			e.push(0.0, SLAM_LIFT, 0.0);
			e.needsSync = true;
			if (e instanceof Player) {
				this.engaged = true;
			}
		}
		if (!ground.isAir()) {
			for (int i = 0; i < 48; i++) {
				double a = i * Math.PI * 2.0 / 48.0, r = 1.0 + this.random.nextDouble() * (SLAM_RADIUS - 1.0);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.getX() + Math.cos(a) * r, this.getY() + 0.1,
					this.getZ() + Math.sin(a) * r, 3, 0.1, 0.1, 0.1, 0.15);
			}
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.6F);
	}

	/** The dead come up round him, climbing out of the floor, and go for whoever he fights. */
	private void graveCall(ServerLevel level) {
		int count = CALL_COUNT[Math.min(this.calls, CALL_COUNT.length - 1)];
		this.calls++;
		int near = level.getEntitiesOfClass(SilverSkeleton.class, this.getBoundingBox().inflate(RISEN_NEAR), Entity::isAlive).size();
		count = Math.min(count, CALL_MAX_RISEN - near);
		for (int i = 0; i < count; i++) {
			double a = this.random.nextDouble() * Math.PI * 2.0, r = CALL_MIN + this.random.nextDouble() * (CALL_MAX - CALL_MIN);
			BlockPos at = BlockPos.containing(this.getX() + Math.cos(a) * r, this.getY(), this.getZ() + Math.sin(a) * r);
			if (!level.getBlockState(at).isAir() || level.getBlockState(at.below()).isAir()) {
				continue;
			}
			SilverSkeleton risen = Monsters.SILVER_SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (risen == null) {
				continue;
			}
			risen.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, this.random.nextFloat() * 360.0F, 0.0F);
			risen.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.MOB_SUMMONED, null);
			risen.rise();
			risen.setTarget(this.getTarget());
			level.addFreshEntity(risen);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(at.below())), at.getX() + 0.5, at.getY() + 0.1,
				at.getZ() + 0.5, 30, 0.4, 0.1, 0.4, 0.1);
		}
		level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 3.0, this.getZ(), 30, 1.0, 1.5, 1.0, 0.05);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2.0F, 0.4F);
	}

	private void blast(ServerLevel level) {
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(SPELL_RADIUS),
			e -> e != this && e.isAlive() && !(e instanceof Monster) && this.distanceTo(e) <= SPELL_RADIUS);
		for (LivingEntity e : near) {
			Vec3 away = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			e.hurtServer(level, this.damageSources().indirectMagic(this, this), SPELL_DAMAGE);
			e.push(away.x * SPELL_PUSH, SPELL_LIFT, away.z * SPELL_PUSH);
			e.needsSync = true;
			if (e instanceof Player) {
				this.engaged = true;
			}
			if (e instanceof ServerPlayer player && this.random.nextFloat() < CURSE_SPELL) {
				SkeletonCurse.curse(player);
			}
		}
		for (int i = 0; i < 40; i++) {
			double a = i * Math.PI * 2.0 / 40.0;
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX() + Math.cos(a) * 1.5, this.getY() + 0.3, this.getZ() + Math.sin(a) * 1.5, 0,
				Math.cos(a), 0.05, Math.sin(a), 0.4);
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 1.5, this.getZ(), 60, 0.5, 1.0, 0.5, 0.3);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2.0F, 0.6F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 1.4F);
	}

	/** The tick the AI step last ran, so the immobile fallback in aiStep does not run it twice. */
	private int stepTick = -1;

	/** Being immobile skips the server AI step, so the timers that end those states run here instead. */
	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level && this.isAlive() && this.isImmobile() && this.stepTick != this.tickCount) {
			this.customServerAiStep(level);
		}
	}

	/** Asleep, laughing or casting, he stands where he is. */
	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || this.dormant() || this.waking > 0 || this.casting > 0 || this.slamming > 0 || this.calling > 0;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean asleep = this.dormant();
		boolean hurt = super.hurtServer(level, source, asleep ? amount * (1.0F - STATUE_GUARD) : amount);
		if (asleep && source.getEntity() != null) {
			this.wake(level);
		}
		if (hurt && source.getEntity() instanceof Player) {
			this.engaged = true;
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = !this.dormant() && this.waking == 0 && this.casting == 0 && this.slamming == 0 && this.calling == 0
			&& super.doHurtTarget(level, target);
		if (hit) {
			this.triggerAnim("action", "staff_swing");
		}
		if (hit && target instanceof Player) {
			this.engaged = true;
		}
		if (hit && target instanceof ServerPlayer player && this.random.nextFloat() < CURSE_STAFF) {
			SkeletonCurse.curse(player);
		}
		return hit;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == WAKE) {
			this.wokeAt = this.tickCount;
		} else if (id == CAST) {
			this.castAt = this.tickCount;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<SkeletonLord>("move", 5, test -> {
			SkeletonLord me = test.animatable();
			if (me.dormant()) {
				return test.setAndContinue(STATUE);
			}
			if (me.sinceWoke() < WAKE_TICKS) {
				return test.setAndContinue(LAUGH);
			}
			if (me.sinceCast() < CAST_TICKS) {
				return test.setAndContinue(SPELL);
			}
			if (!test.isMoving()) {
				return test.setAndContinue(IDLE);
			}
			return test.setAndContinue(me.walkAnimation.speed() > RUN_FROM ? RUN : WALK);
		}));
		controllers.add(new AnimationController<SkeletonLord>("action", test -> PlayState.STOP).triggerableAnim("staff_swing", STAFF_SWING)
			.triggerableAnim("slam", SLAM).triggerableAnim("grave_call", GRAVE_CALL));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, new ItemStack(Items.BONE, BONES_MIN + this.random.nextInt(BONES_MAX - BONES_MIN + 1)));
		this.spawnAtLocation(level, new ItemStack(Items.AMETHYST_SHARD, AMETHYST_MIN + this.random.nextInt(AMETHYST_MAX - AMETHYST_MIN + 1)));
		for (ItemStack coins : Money.stacks(Money.worth(SILVER_MIN + this.random.nextInt(SILVER_MAX - SILVER_MIN + 1)))) {
			this.spawnAtLocation(level, coins);
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		this.bar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bar.removePlayer(player);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.dormant() ? null : SoundEvents.SKELETON_AMBIENT;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.55F;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return this.dormant() ? SoundEvents.AMETHYST_BLOCK_HIT : SoundEvents.SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITHER_SKELETON_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SKELETON_STEP, 1.0F, 0.6F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("dormant", this.dormant());
		output.putInt("grave_calls", this.calls);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DORMANT, input.getBooleanOr("dormant", false));
		this.calls = input.getIntOr("grave_calls", 0);
		this.bar.setVisible(false);
	}
}
