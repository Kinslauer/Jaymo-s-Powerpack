package com.jaymoskingdoms.glowwood.creatures;

import com.jaymoskingdoms.glowwood.GlowWoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A little walking mushroom. Shy of players (unless they wear the Crown of the Bloom), it tends the small fungi around
 * it. Tame it with Truffles (one in three): it follows, sits on use, and now and then sheds a mushroom of its kind.
 * Four cap variants, chosen by biome.
 */
public class SporelingEntity extends TamableAnimal {
	public static final byte EVENT_SNEEZE = 101;
	public static final byte EVENT_HAPPY = 102;
	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(SporelingEntity.class,
			EntityDataSerializers.INT);

	public final AnimationState sneezeState = new AnimationState();
	public final AnimationState happyState = new AnimationState();
	private int shedTimer = -1;
	private int sneezeTicks;

	public SporelingEntity(EntityType<? extends SporelingEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return TamableAnimal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 16.0);
	}

	/** Natural spawns: on dirt-like ground, mycelium or moss, in any light (they like the shade). */
	public static boolean checkSpawnRules(EntityType<SporelingEntity> type, LevelAccessor level, EntitySpawnReason spawnType,
										  BlockPos pos, RandomSource random) {
		BlockState below = level.getBlockState(pos.below());
		return (below.is(BlockTags.DIRT) || below.is(GlowWoods.FUNGUS_SOIL)) && GlowCreatures.nearGiant(level, pos, random);
	}

	public enum Variant {
		GLOWCAP("glowcap"), BOUNCECAP("bouncecap"), INKCAP("inkcap"), CHANTERELLE("chanterelle");

		private final String id;

		Variant(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public Item item() {
			return switch (this) {
				case GLOWCAP -> GlowWoods.GLOWCAP.asItem();
				case BOUNCECAP -> GlowWoods.BOUNCECAP.asItem();
				case INKCAP -> GlowWoods.INKCAP.asItem();
				case CHANTERELLE -> GlowFoods.chanterelle();
			};
		}

		public static Variant byId(int id) {
			Variant[] values = values();
			return values[Math.floorMod(id, values.length)];
		}
	}

	// ---- data ---------------------------------------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, 0);
	}

	public Variant getVariant() {
		return Variant.byId(this.entityData.get(DATA_VARIANT));
	}

	public void setVariant(Variant variant) {
		this.entityData.set(DATA_VARIANT, variant.ordinal());
	}

	@Override
	public void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Variant", getVariant().id());
		output.putInt("ShedTimer", shedTimer);
	}

	@Override
	public void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		String variant = input.getStringOr("Variant", "");
		for (Variant v : Variant.values()) {
			if (v.id().equals(variant)) {
				setVariant(v);
			}
		}
		shedTimer = input.getIntOr("ShedTimer", -1);
	}

	// ---- spawning -----------------------------------------------------------------------------------------------

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnType,
										@Nullable SpawnGroupData groupData) {
		setVariant(variantFor(level.getBiome(this.blockPosition()), level.getRandom()));
		return super.finalizeSpawn(level, difficulty, spawnType, groupData);
	}

	static Variant variantFor(Holder<Biome> biome, RandomSource random) {
		if (biome.is(Biomes.MUSHROOM_FIELDS) || biome.is(Biomes.LUSH_CAVES) || biome.is(Biomes.DRIPSTONE_CAVES)) {
			return Variant.GLOWCAP;
		}
		if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(Biomes.DARK_FOREST)) {
			return Variant.INKCAP;
		}
		if (biome.is(BiomeTags.IS_TAIGA) || biome.is(Biomes.GROVE)) {
			return Variant.CHANTERELLE;
		}
		if (biome.is(Biomes.FLOWER_FOREST) || biome.is(Biomes.MEADOW) || biome.is(Biomes.CHERRY_GROVE)
				|| biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
			return Variant.BOUNCECAP;
		}
		return Variant.byId(random.nextInt(Variant.values().length));
	}

	// ---- goals --------------------------------------------------------------------------------------------------

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.4));
		this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 6.0F, 1.0, 1.3, this::scaredOf) {
			@Override
			public boolean canUse() {
				return !SporelingEntity.this.isTame() && super.canUse();
			}
		});
		this.goalSelector.addGoal(4, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(5, new TemptGoal(this, 1.0, this::isFood, false));
		this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 6.0F, 2.0F));
		this.goalSelector.addGoal(7, new TendFungiGoal(this));
		this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
	}

	private boolean scaredOf(LivingEntity entity) {
		if (!(entity instanceof Player player) || player.isCreative() || player.isSpectator()) {
			return false;
		}
		return !CrownOfTheBloomItem.isWorn(player) && !isFood(player.getMainHandItem()) && !isFood(player.getOffhandItem());
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return GlowFoods.isTruffle(stack);
	}

	// ---- interaction --------------------------------------------------------------------------------------------

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (this.level().isClientSide()) {
			boolean handled = this.isOwnedBy(player) || (!this.isTame() && this.isFood(stack));
			return handled ? InteractionResult.CONSUME : InteractionResult.PASS;
		}
		if (!this.isTame()) {
			if (this.isFood(stack)) {
				stack.consume(1, player);
				if (this.random.nextInt(3) == 0) {
					this.tame(player);
					this.navigation.stop();
					this.setTarget(null);
					this.setOrderedToSit(true);
					this.level().broadcastEntityEvent(this, (byte) 7);
					celebrate();
				} else {
					this.level().broadcastEntityEvent(this, (byte) 6);
				}
				return InteractionResult.SUCCESS;
			}
			return super.mobInteract(player, hand);
		}
		if (this.isOwnedBy(player)) {
			if (this.isFood(stack) && this.getHealth() < this.getMaxHealth()) {
				stack.consume(1, player);
				this.heal(4.0F);
				celebrate();
				return InteractionResult.SUCCESS;
			}
			InteractionResult result = super.mobInteract(player, hand);
			if (result.consumesAction()) {
				return result;
			}
			this.setOrderedToSit(!this.isOrderedToSit());
			this.jumping = false;
			this.navigation.stop();
			this.setTarget(null);
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	/** Hops and spins with joy (fed, tamed, or done tending a mushroom). */
	public void celebrate() {
		if (!this.level().isClientSide()) {
			this.level().broadcastEntityEvent(this, EVENT_HAPPY);
			this.playSound(GlowSounds.SPORELING_HAPPY, 0.7F, this.getVoicePitch());
		}
	}

	public void sneeze() {
		if (!this.level().isClientSide() && sneezeTicks <= 0) {
			this.level().broadcastEntityEvent(this, EVENT_SNEEZE);
			this.playSound(GlowSounds.SPORELING_SNEEZE, 0.7F, this.getVoicePitch());
			sneezeTicks = 13;
		}
	}

	// ---- ticking ------------------------------------------------------------------------------------------------

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level) {
			if (sneezeTicks > 0 && --sneezeTicks == 0) {
				// The "choo": a puff of spores from under the cap.
				double yaw = Math.toRadians(this.yBodyRot);
				level.sendParticles(getVariant() == Variant.GLOWCAP ? GlowFx.glowSpore() : GlowFx.spore(),
						this.getX() - Math.sin(yaw) * 0.35, this.getY() + 0.35, this.getZ() + Math.cos(yaw) * 0.35,
						10, 0.12, 0.08, 0.12, 0.03);
			}
			if (!this.isInSittingPose() && this.random.nextInt(1600) == 0) {
				sneeze();
			}
			if (this.isTame() && this.isAlive()) {
				if (shedTimer < 0) {
					shedTimer = nextShed();
				} else if (--shedTimer == 0) {
					this.spawnAtLocation(level, new ItemStack(getVariant().item()));
					sneeze();
					shedTimer = nextShed();
				}
			}
		}
	}

	private int nextShed() {
		int min = GlowCreatures.SHED_MIN_MINUTES * 1200;
		int max = Math.max(min + 1, GlowCreatures.SHED_MAX_MINUTES * 1200);
		return min + this.random.nextInt(max - min);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_SNEEZE) {
			sneezeState.start(this.tickCount);
		} else if (id == EVENT_HAPPY) {
			happyState.start(this.tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	// ---- breeding, loot, sounds ---------------------------------------------------------------------------------

	@Nullable
	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		SporelingEntity child = GlowCreatures.SPORELING.create(level, EntitySpawnReason.BREEDING);
		if (child != null) {
			Variant variant = this.random.nextBoolean() || !(partner instanceof SporelingEntity other)
					? this.getVariant() : other.getVariant();
			child.setVariant(variant);
			if (this.isTame() && this.getOwnerReference() != null) {
				child.setOwnerReference(this.getOwnerReference());
				child.setTame(true, true);
			}
		}
		return child;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		this.spawnAtLocation(level, new ItemStack(getVariant().item()));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return GlowSounds.SPORELING_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return GlowSounds.SPORELING_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return GlowSounds.SPORELING_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6F;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}
}
