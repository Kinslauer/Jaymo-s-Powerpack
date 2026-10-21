package com.jaymospowers.aether.mob;

import com.jaymospowers.aether.AetherBlocks;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The sheepuff, the Aether's cloud sheep. It grazes aether grass; after eating {@link #GRASS_TO_PUFF} patches it puffs
 * up: it drifts down slowly, takes no fall damage and jumps high. Shearing a puffed one lets the air out (and gives
 * more wool than a plain shearing); a plain shearing leaves it bare until it grazes again. Wool is mostly white, and
 * sometimes light gray, gray, cyan or a colour of its climate (see {@link #randomColor}); dye changes it.
 */
public class Sheepuff extends Animal {
	// ---- tuning ----
	public static final double HEALTH = 8.0, SPEED = 0.23;
	/** Patches of grass it eats before it puffs up. */
	public static final int GRASS_TO_PUFF = 2;
	/** Puffed: the most it falls a tick, and its jump strength (vanilla's is 0.42). */
	public static final double PUFFED_FALL = 0.05, PUFFED_JUMP = 0.9, NORMAL_JUMP = 0.42;
	/** Wool from a shearing: plain, and puffed (inclusive ranges). */
	public static final int WOOL_MIN = 1, WOOL_MAX = 3, PUFFED_WOOL_MIN = 2, PUFFED_WOOL_MAX = 4;
	/** How long it munches (ticks), and the chance each tick it starts (one in this many; kids graze sooner). */
	public static final int EAT_TICKS = 40, EAT_ODDS = 1000, BABY_EAT_ODDS = 50;
	/** Wild colour odds (a share each): light gray, gray and cyan; the zone's own fourth colour; and a one in this many chance of magenta (highfields only). */
	public static final float COMMON = 0.05F, ZONE_ODDS = 0.03F;
	public static final int MAGENTA_ODDS = 500;

	/** Wool colours by index (see {@link Wool}); the skins are {@code sheepuff_<name>}. */
	public static final String[] COLORS = Wool.NAMES;

	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(Sheepuff.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> PUFFED = SynchedEntityData.defineId(Sheepuff.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> SHEARED = SynchedEntityData.defineId(Sheepuff.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> EATING = SynchedEntityData.defineId(Sheepuff.class, EntityDataSerializers.BOOLEAN);

	/** Patches eaten since it last puffed. */
	private int grazed;

	public Sheepuff(EntityType<? extends Sheepuff> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.JUMP_STRENGTH, NORMAL_JUMP);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.25));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.1, AetherAnimals::isAnimalFood, false));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(5, new EatGrassGoal());
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	// ---- state ----

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(COLOR, 0);
		entityData.define(PUFFED, false);
		entityData.define(SHEARED, false);
		entityData.define(EATING, false);
	}

	/** Its colour, an index into {@link #COLORS}. */
	public int color() {
		return this.entityData.get(COLOR);
	}

	public void setColor(int color) {
		this.entityData.set(COLOR, Math.floorMod(color, COLORS.length));
	}

	public boolean isPuffed() {
		return this.entityData.get(PUFFED);
	}

	public void setPuffed(boolean puffed) {
		this.entityData.set(PUFFED, puffed);
		this.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(puffed ? PUFFED_JUMP : NORMAL_JUMP);
	}

	public boolean isSheared() {
		return this.entityData.get(SHEARED);
	}

	public void setSheared(boolean sheared) {
		this.entityData.set(SHEARED, sheared);
	}

	/** Munching right now (the head goes down). */
	public boolean isEating() {
		return this.entityData.get(EATING);
	}

	/** A wild colour for the biome at {@code pos}: mostly white; light gray, gray and cyan 5% each; and 3% of the zone's own (yellow, light blue, brown). */
	public static int randomColor(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		if (!Zone.in(level, pos)) {
			return 0;
		}
		Zone zone = Zone.at(level, pos);
		if (zone == Zone.HIGHFIELDS && random.nextInt(MAGENTA_ODDS) == 0) {
			return Wool.index("magenta");
		}
		float roll = random.nextFloat();
		if (roll < COMMON) {
			return Wool.index("light_gray");
		}
		if (roll < COMMON * 2) {
			return Wool.index("gray");
		}
		if (roll < COMMON * 3) {
			return Wool.index("cyan");
		}
		if (roll < COMMON * 3 + ZONE_ODDS) {
			return Wool.index(switch (zone) {
				case HIGHFIELDS -> "yellow";
				case MAGNETIC -> "light_blue";
				case ARCTIC -> "brown";
			});
		}
		return 0;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		this.setColor(randomColor(level, this.blockPosition(), this.random));
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	// ---- life ----

	@Override
	public void tick() {
		super.tick();
		if (this.isPuffed()) {
			Vec3 motion = this.getDeltaMovement();
			if (!this.onGround() && motion.y < -PUFFED_FALL) {
				this.setDeltaMovement(motion.x, -PUFFED_FALL, motion.z);
			}
			this.resetFallDistance();
		}
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return !this.isPuffed() && super.causeFallDamage(distance, multiplier, source);
	}

	/** It has eaten a patch of grass: wool grows back, and a second patch puffs it up. */
	private void grazedPatch() {
		this.setSheared(false);
		if (!this.isPuffed() && ++this.grazed >= GRASS_TO_PUFF) {
			this.grazed = 0;
			this.setPuffed(true);
			this.playSound(SoundEvents.SHEEP_AMBIENT, 1.0F, 1.6F);
		}
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.SHEARS) && !this.isBaby() && (this.isPuffed() || !this.isSheared())) {
			if (this.level() instanceof ServerLevel level) {
				this.shear(level);
				if (player instanceof ServerPlayer sp) {
					held.hurtAndBreak(1, sp, hand);
				}
			}
			return InteractionResult.SUCCESS;
		}
		DyeColor dye = held.get(DataComponents.DYE);
		if (dye != null && !this.isBaby() && Wool.index(dye) != this.color()) {
			if (!this.level().isClientSide()) {
				this.setColor(Wool.index(dye));
				held.consume(1, player);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	private void shear(ServerLevel level) {
		level.playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
		boolean puffed = this.isPuffed();
		int count = puffed ? PUFFED_WOOL_MIN + this.random.nextInt(PUFFED_WOOL_MAX - PUFFED_WOOL_MIN + 1)
			: WOOL_MIN + this.random.nextInt(WOOL_MAX - WOOL_MIN + 1);
		this.spawnAtLocation(level, new ItemStack(this.wool(), count));
		if (puffed) {
			// the air goes out of it, the wool stays
			this.setPuffed(false);
		} else {
			this.setSheared(true);
		}
	}

	/** Its wool item. */
	private net.minecraft.world.item.Item wool() {
		return Wool.item(this.color());
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level && !this.isBaby() && !this.isSheared()) {
			this.spawnAtLocation(level, new ItemStack(this.wool()));
		}
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return AetherAnimals.isAnimalFood(stack);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		Sheepuff baby = AetherMobs.SHEEPUFF.create(level, EntitySpawnReason.BREEDING);
		if (baby != null) {
			// it takes after either parent
			baby.setColor(partner instanceof Sheepuff other && this.random.nextBoolean() ? other.color() : this.color());
		}
		return baby;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("color", this.color());
		output.putBoolean("puffed", this.isPuffed());
		output.putBoolean("sheared", this.isSheared());
		output.putInt("grazed", this.grazed);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setColor(input.getIntOr("color", 0));
		this.setPuffed(input.getBooleanOr("puffed", false));
		this.setSheared(input.getBooleanOr("sheared", false));
		this.grazed = input.getIntOr("grazed", 0);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SHEEP_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SHEEP_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SHEEP_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SHEEP_STEP, 0.15F, 1.0F);
	}

	/** Eats the aether grass under its feet (it turns to aether dirt), now and then. */
	private final class EatGrassGoal extends Goal {
		private int ticks;

		EatGrassGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		private boolean grassBelow() {
			Block block = Sheepuff.this.level().getBlockState(Sheepuff.this.blockPosition().below()).getBlock();
			return block == AetherBlocks.AETHER_GRASS_BLOCK;
		}

		@Override
		public boolean canUse() {
			Sheepuff sheepuff = Sheepuff.this;
			// a puffed one is too light to graze
			return !sheepuff.isPuffed() && sheepuff.getRandom().nextInt(sheepuff.isBaby() ? BABY_EAT_ODDS : EAT_ODDS) == 0 && this.grassBelow();
		}

		@Override
		public boolean canContinueToUse() {
			return this.ticks > 0;
		}

		@Override
		public void start() {
			this.ticks = EAT_TICKS;
			Sheepuff.this.entityData.set(EATING, true);
			Sheepuff.this.getNavigation().stop();
		}

		@Override
		public void stop() {
			this.ticks = 0;
			Sheepuff.this.entityData.set(EATING, false);
		}

		@Override
		public void tick() {
			this.ticks = Math.max(0, this.ticks - 1);
			if (this.ticks == 4 && this.grassBelow() && Sheepuff.this.level() instanceof ServerLevel level) {
				BlockPos pos = Sheepuff.this.blockPosition().below();
				level.levelEvent(2001, pos, Block.getId(level.getBlockState(pos)));
				level.setBlockAndUpdate(pos, AetherBlocks.AETHER_DIRT.defaultBlockState());
				Sheepuff.this.grazedPatch();
			}
		}
	}
}
