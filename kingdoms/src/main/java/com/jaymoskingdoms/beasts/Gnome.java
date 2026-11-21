package com.jaymoskingdoms.beasts;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The gnomes (Andy, 2026-11-19: "can we give gnomes real models?"; Lore.md, "The gnomes"). The ground's own small folk.
 * ATi Structures' gnome huts and Grim Kingdoms' gnome towers place baby zombies named "Gnome"; {@link Gnomes} turns each
 * into one of these where it stands, and that spot is its house ({@link #home}).
 * <p>
 * A gnome keeps a garden: by day it kneels in the beds round its house and works bone meal into them
 * ({@link #GARDEN_EVERY}), so the flowers spread. It is neutral. Someone who tramples its beds, breaks a block near its
 * house or opens a chest there ({@link Gnomes}) has the whole house ({@link #HOUSE_RANGE}) come at them with sticks for
 * {@link #ANGER_TICKS}. Some carry a plank shield, which takes half of a blow from the front.
 * <p>
 * A gift of what grows (the tag {@code jaymoskingdoms:gnome_gifts}: seeds, saplings, flowers, bone meal) calms a gnome
 * angry at the giver; otherwise it trades a stone-gift from below (raw iron, an emerald, now and then a diamond), once
 * every {@link #GIFT_COOLDOWN}. If its garden has been stripped (it once had {@link #BARE_MIN} times two plants and now has
 * fewer than {@link #BARE_MIN}), it digs down into the ground and is gone.
 */
public class Gnome extends PathfinderMob implements GeoEntity {
	static final double HEALTH = 16.0, ARMOR = 2.0, DAMAGE = 3.0, SPEED = 0.28, FOLLOW = 24.0;
	/** How far its house and garden reach from its home (blocks), and how far a house's anger carries between gnomes. */
	static final int HOME_RANGE = 12;
	static final double HOUSE_RANGE = 24.0;
	/** How long a house stays angry (ticks; a minute), and how far off its foe has to get to be let go. */
	static final int ANGER_TICKS = 1200;
	static final double LET_GO = 32.0;
	/** How often a gnome looks for trampling boots in its beds (ticks). */
	static final int TRAMPLE_CHECK = 20;
	/** Gardening: how often (ticks, plus up to as much again), how long it kneels, how long it may take to get there, and
	 * how many plants make a garden full (no more bone meal). */
	static final int GARDEN_EVERY = 600, GARDEN_TICKS = 60, GARDEN_WALK = 200, GARDEN_FULL = 48;
	/** A stripped garden: checked this often (ticks); fewer plants than this, after it once had twice as many, and it leaves. */
	static final int BARE_CHECK = 1200, BARE_MIN = 3;
	/** How long it takes to dig down out of sight (ticks; the {@code dig} animation is 1.5 s). */
	static final int DIG_TICKS = 30;
	/** How long before a gnome trades again (ticks; 5 minutes). */
	static final int GIFT_COOLDOWN = 6000;
	/** The stone-gift: the chance of a diamond, and of an emerald (otherwise raw iron, 1 to 2). */
	static final float DIAMOND_CHANCE = 0.05F, EMERALD_CHANCE = 0.35F;
	/** How much of a blow from the front the plank shield takes, and the chance a gnome from a spawn egg carries one. */
	static final float SHIELD_BLOCK = 0.5F, SHIELD_CHANCE = 0.35F;
	static final int ANIMATION_BLEND = 4;
	/** What gnomes take as a gift. */
	public static final TagKey<Item> GIFTS = TagKey.create(Registries.ITEM, JaymosKingdoms.id("gnome_gifts"));

	/** What a gnome is doing (synced, for its animation). */
	public static final byte NONE = 0, GARDEN = 1, DIG = 2;

	private static final EntityDataAccessor<Boolean> SHIELD = SynchedEntityData.defineId(Gnome.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Byte> ACT = SynchedEntityData.defineId(Gnome.class, EntityDataSerializers.BYTE);
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
	private static final RawAnimation GARDENING = RawAnimation.begin().thenLoop("garden");
	private static final RawAnimation DIGGING = RawAnimation.begin().thenPlayAndHold("dig");
	private static final RawAnimation BONK = RawAnimation.begin().thenPlay("bonk");
	private static final RawAnimation HURT = RawAnimation.begin().thenPlay("hurt");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private @Nullable BlockPos home, bed;
	private long angerUntil, giftReady;
	private int gardenCooldown = GARDEN_EVERY, gardenLeft, walkLeft, digLeft, bestGarden;

	public Gnome(EntityType<? extends Gnome> type, Level level) {
		super(type, level);
		this.xpReward = 3;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FOLLOW_RANGE, FOLLOW);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(SHIELD, false);
		entityData.define(ACT, NONE);
	}

	/** (Both sides) whether it carries the plank shield. */
	public boolean shield() {
		return this.entityData.get(SHIELD);
	}

	/** Where it lives, and whether it carries a shield: set when {@link Gnomes} turns a structure's gnome into this one. */
	void settle(BlockPos home, boolean shield) {
		this.home = home.immutable();
		this.setHomeTo(this.home, HOME_RANGE);
		this.entityData.set(SHIELD, shield);
		this.setPersistenceRequired();
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Gnome>("move", ANIMATION_BLEND, test -> {
			Gnome gnome = test.animatable();
			byte act = gnome.entityData.get(ACT);
			if (act == DIG) {
				return test.setAndContinue(DIGGING);
			}
			if (test.isMoving()) {
				return test.setAndContinue(gnome.isAggressive() ? RUN : WALK);
			}
			return test.setAndContinue(act == GARDEN ? GARDENING : IDLE);
		}));
		controllers.add(new AnimationController<Gnome>("action", ANIMATION_BLEND, test -> PlayState.STOP)
			.triggerableAnim("bonk", BONK).triggerableAnim("hurt", HURT));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.4, true));
		this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.8));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6) {
			@Override
			public boolean canUse() {
				return Gnome.this.entityData.get(ACT) == NONE && Gnome.this.bed == null && super.canUse();
			}
		});
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	// ---- the house's temper ---------------------------------------------------------------------------------------------

	/** Whether {@code pos} is inside this gnome's house and garden. */
	boolean keeps(BlockPos pos) {
		return this.home != null && this.home.closerThan(pos, HOME_RANGE);
	}

	/** The house within {@link #HOUSE_RANGE} of {@code pos}. */
	static java.util.List<Gnome> house(ServerLevel level, Vec3 pos) {
		return level.getEntitiesOfClass(Gnome.class, new AABB(pos, pos).inflate(HOUSE_RANGE), g -> g.isAlive() && g.entityData.get(ACT) != DIG);
	}

	/** Someone wronged the house: every gnome in it comes for them. */
	void rouse(ServerLevel level, LivingEntity foe) {
		if (foe instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return;
		}
		if (this.getTarget() != foe) {
			this.playSound(SoundEvents.VILLAGER_NO, 1.0F, this.getVoicePitch());
		}
		for (Gnome g : house(level, this.position())) {
			g.angerUntil = level.getGameTime() + ANGER_TICKS;
			g.entityData.set(ACT, NONE);
			g.bed = null;
			if (!g.isNoAi()) {
				g.setTarget(foe);
			}
		}
	}

	/** A gift: the house lets the giver be. */
	private void calm(ServerLevel level, Player giver) {
		for (Gnome g : house(level, this.position())) {
			if (g.getTarget() == giver) {
				g.setTarget(null);
				g.angerUntil = 0L;
			}
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(GIFTS) || this.entityData.get(ACT) == DIG) {
			return super.mobInteract(player, hand);
		}
		if (this.level() instanceof ServerLevel level) {
			if (this.getTarget() == player) {
				this.calm(level, player);
			} else if (level.getGameTime() < this.giftReady) {
				// it has nothing to give yet, and won't take what it can't pay for
				this.playSound(SoundEvents.VILLAGER_NO, 1.0F, this.getVoicePitch());
				return InteractionResult.SUCCESS;
			} else {
				this.giftReady = level.getGameTime() + GIFT_COOLDOWN;
				this.spawnAtLocation(level, this.stoneGift());
			}
			held.consume(1, player);
			level.sendParticles(ParticleTypes.HEART, this.getX(), this.getEyeY() + 0.4, this.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
			this.playSound(SoundEvents.VILLAGER_YES, 1.0F, this.getVoicePitch());
		}
		return InteractionResult.SUCCESS;
	}

	/** Something the gnomes brought up from below. */
	private ItemStack stoneGift() {
		float r = this.random.nextFloat();
		if (r < DIAMOND_CHANCE) {
			return new ItemStack(Items.DIAMOND);
		}
		if (r < DIAMOND_CHANCE + EMERALD_CHANCE) {
			return new ItemStack(Items.EMERALD);
		}
		return new ItemStack(Items.RAW_IRON, 1 + this.random.nextInt(2));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.shield() && source.getEntity() != null && !source.is(DamageTypeTags.BYPASSES_SHIELD)) {
			Vec3 from = (source.getSourcePosition() != null ? source.getSourcePosition() : source.getEntity().position()).subtract(this.position());
			Vec3 facing = Vec3.directionFromRotation(0.0F, this.getYRot());
			if (from.x * facing.x + from.z * facing.z > 0.0) {
				amount *= 1.0F - SHIELD_BLOCK;
				this.playSound(SoundEvents.SHIELD_BLOCK.value(), 0.8F, 1.2F);
			}
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			this.triggerAnim("action", "hurt");
			if (source.getEntity() instanceof LivingEntity foe && !(foe instanceof Gnome)) {
				this.rouse(level, foe);
			}
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		this.triggerAnim("action", "bonk");
		this.playSound(SoundEvents.WOOD_HIT, 0.8F, 1.3F);
		return super.doHurtTarget(level, target);
	}

	// ---- its days ---------------------------------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.entityData.get(ACT) == DIG) {
			this.digDown(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive() || level.getGameTime() > this.angerUntil || this.distanceTo(target) > LET_GO
			|| target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			this.setTarget(null);
			target = null;
		}
		this.setAggressive(target != null);
		if (target != null || this.home == null) {
			return;
		}
		if ((this.tickCount + this.getId()) % TRAMPLE_CHECK == 0) {
			this.watchBeds(level);
		}
		if ((this.tickCount + this.getId()) % BARE_CHECK == 0) {
			int plants = this.countGarden(level);
			this.bestGarden = Math.max(this.bestGarden, plants);
			if (this.bestGarden >= BARE_MIN * 2 && plants < BARE_MIN) {
				// the beds are stripped: down into the ground, and gone
				this.entityData.set(ACT, DIG);
				this.digLeft = DIG_TICKS;
				this.getNavigation().stop();
				return;
			}
		}
		this.garden(level);
	}

	/** Anyone standing in its beds (on a flower, sapling or mushroom, or on farmland) rouses the house. */
	private void watchBeds(ServerLevel level) {
		for (Player p : level.getEntitiesOfClass(Player.class, new AABB(this.home).inflate(HOME_RANGE),
			p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {
			BlockPos feet = p.blockPosition();
			if (this.keeps(feet) && (plant(level.getBlockState(feet)) || level.getBlockState(feet.below()).is(Blocks.FARMLAND))) {
				this.rouse(level, p);
				return;
			}
		}
	}

	private static boolean plant(BlockState state) {
		return state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM);
	}

	/** The flowers, saplings and mushrooms in its garden. */
	private int countGarden(ServerLevel level) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(this.home.offset(-HOME_RANGE, -4, -HOME_RANGE), this.home.offset(HOME_RANGE, 4, HOME_RANGE))) {
			if (plant(level.getBlockState(pos))) {
				n++;
			}
		}
		return n;
	}

	/** By day, now and then: to a bed in the garden, kneel there, and work bone meal into it. */
	private void garden(ServerLevel level) {
		if (this.bed != null) {
			if (this.gardenLeft > 0) {
				this.getNavigation().stop();
				this.getLookControl().setLookAt(Vec3.atCenterOf(this.bed));
				if (--this.gardenLeft == 0) {
					this.feed(level, this.bed);
					this.entityData.set(ACT, NONE);
					this.bed = null;
				}
			} else if (this.position().distanceTo(Vec3.atBottomCenterOf(this.bed)) < 1.8) {
				this.gardenLeft = GARDEN_TICKS;
				this.entityData.set(ACT, GARDEN);
			} else if (--this.walkLeft <= 0) {
				this.bed = null;
			} else if (this.tickCount % 20 == 0) {
				this.getNavigation().moveTo(this.bed.getX() + 0.5, this.bed.getY(), this.bed.getZ() + 0.5, 0.7);
			}
			return;
		}
		long time = level.getOverworldClockTime() % 24000L;
		if (time >= 12000L || --this.gardenCooldown > 0) {
			return;
		}
		this.gardenCooldown = GARDEN_EVERY + this.random.nextInt(GARDEN_EVERY);
		if (this.countGarden(level) >= GARDEN_FULL) {
			return;
		}
		BlockPos column = this.home.offset(this.random.nextInt(HOME_RANGE * 2 + 1) - HOME_RANGE, 0, this.random.nextInt(HOME_RANGE * 2 + 1) - HOME_RANGE);
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		BlockPos ground = top.below();
		if (Math.abs(top.getY() - this.home.getY()) > 4 || !this.keeps(top) || !grows(level, ground)) {
			return;
		}
		this.bed = ground;
		this.walkLeft = GARDEN_WALK;
		this.getNavigation().moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0.7);
	}

	/** Whether bone meal does anything here (grass, moss, a sapling or flower that will grow). */
	private static boolean grows(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof BonemealableBlock b && b.isValidBonemealTarget(level, pos, state, BonemealSource.MOB);
	}

	/** Bone meal worked into the bed, as a player's would be. */
	private void feed(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof BonemealableBlock b && b.isValidBonemealTarget(level, pos, state, BonemealSource.MOB)) {
			if (b.isBonemealSuccess(level, this.random, pos, state, BonemealSource.MOB)) {
				b.performBonemeal(level, this.random, pos, state, BonemealSource.MOB);
			}
			level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
		}
	}

	/** Clawing down into the earth; gone when the dig is done. */
	private void digDown(ServerLevel level) {
		this.getNavigation().stop();
		this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
		BlockState under = level.getBlockState(this.blockPosition().below());
		if (this.digLeft % 5 == 0 && !under.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), this.getX(), this.getY() + 0.1, this.getZ(), 8, 0.25, 0.05, 0.25, 0.1);
			this.playSound(SoundEvents.ROOTED_DIRT_BREAK, 0.8F, 1.1F);
		}
		if (--this.digLeft <= 0) {
			this.discard();
		}
	}

	// ---- coming into the world, and saving --------------------------------------------------------------------------------

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData data) {
		data = super.finalizeSpawn(level, difficulty, reason, data);
		this.settle(this.blockPosition(), this.random.nextFloat() < SHIELD_CHANCE);
		return data;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.home != null) {
			output.putLong("gnome_home", this.home.asLong());
		}
		output.putBoolean("shield", this.shield());
		output.putLong("anger_until", this.angerUntil);
		output.putLong("gift_ready", this.giftReady);
		output.putInt("best_garden", this.bestGarden);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		long home = input.getLongOr("gnome_home", Long.MIN_VALUE);
		if (home != Long.MIN_VALUE) {
			this.home = BlockPos.of(home);
			this.setHomeTo(this.home, HOME_RANGE);
		}
		this.entityData.set(SHIELD, input.getBooleanOr("shield", false));
		this.angerUntil = input.getLongOr("anger_until", 0L);
		this.giftReady = input.getLongOr("gift_ready", 0L);
		this.bestGarden = input.getIntOr("best_garden", 0);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.entityData.get(ACT) == DIG ? null : SoundEvents.VILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VILLAGER_DEATH;
	}

	/** A small folk's voice: the villagers' grumble, pitched up. */
	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 1.5F;
	}
}
