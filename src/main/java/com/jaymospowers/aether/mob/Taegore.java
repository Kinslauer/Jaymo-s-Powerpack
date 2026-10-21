package com.jaymospowers.aether.mob;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.AetherBlocks;
import com.jaymospowers.aether.AetherFlora;
import com.jaymospowers.aether.AetherItems;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The taegore, a shy, heavy digger. It keeps away from running players, and now and then walks to soft ground 10 to 40
 * blocks off (somewhere it has not dug lately), digs for {@link #DIG_TICKS} ticks and turns up one thing: a stick, valkyrie
 * wings, a blue berry, a satival bulb, a twig or a holystone rock.
 */
public class Taegore extends ZoneAnimal {
	// ---- tuning ----
	public static final double HEALTH = 18.0, SPEED = 0.25, STEP = 1.0;
	/** A player running within this many blocks scares it: it runs at {@link #FLEE_SPEED}. */
	public static final float FLEE_RANGE = 24.0F;
	public static final double FLEE_WALK = 1.0, FLEE_SPEED = 1.7;
	/** A player wearing any piece from this item tag (beast pelt armour, when the gear track adds it) does not scare it. */
	public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> PELT_ARMOR = net.minecraft.tags.TagKey.create(
		net.minecraft.core.registries.Registries.ITEM, JaymosPowers.id("beast_pelt_armor"));
	/** How far a dig spot is (blocks), how long the dig takes (ticks), the wait between digs, and how many spots it remembers. */
	public static final int DIG_MIN = 10, DIG_MAX = 40, DIG_TICKS = 240, DIG_WAIT = 9600, DIG_WAIT_FIRST = 2400, MEMORY = 20;

	private static final EntityDataAccessor<Boolean> DIGGING = SynchedEntityData.defineId(Taegore.class, EntityDataSerializers.BOOLEAN);

	private int digWait = DIG_WAIT_FIRST;
	private final ArrayDeque<BlockPos> explored = new ArrayDeque<>();

	public Taegore(EntityType<? extends Taegore> type, Level level) {
		super(type, level);
		this.digWait = DIG_WAIT_FIRST / 2 + this.random.nextInt(DIG_WAIT_FIRST * 3);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.STEP_HEIGHT, STEP);
	}

	private static boolean wearsPelt(Player player) {
		for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {net.minecraft.world.entity.EquipmentSlot.HEAD,
			net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
			if (player.getItemBySlot(slot).is(PELT_ARMOR)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.25));
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, FLEE_RANGE, FLEE_WALK, FLEE_SPEED, p -> p instanceof Player pl && pl.isSprinting() && !wearsPelt(pl)));
		this.goalSelector.addGoal(3, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(4, new TemptGoal(this, 1.2, AetherAnimals::isAnimalFood, false));
		this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(6, new DigGoal());
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DIGGING, false);
	}

	public boolean isDigging() {
		return this.entityData.get(DIGGING);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.digWait > 0 && !this.isBaby()) {
			this.digWait--;
		}
	}

	/** Soft ground it will dig. */
	private static boolean diggable(BlockState state) {
		return state.is(AetherBlocks.AETHER_GRASS_BLOCK) || state.is(AetherBlocks.AETHER_DIRT) || state.is(AetherBlocks.COARSE_AETHER_DIRT)
			|| state.is(AetherBlocks.SHIMMERING_SILT) || state.is(AetherBlocks.FERROSITE_MUD) || state.is(AetherFlora.BRYALINN_MOSS_BLOCK)
			|| state.is(AetherFlora.SHAYELINN_MOSS_BLOCK) || state.is(AetherFlora.AMBRELINN_MOSS_BLOCK);
	}

	/** What a dig turns up (equal odds). */
	private ItemStack findings() {
		List<ItemStack> table = List.of(new ItemStack(Items.STICK), new ItemStack(AetherFlora.VALKYRIE_WINGS), new ItemStack(AetherItems.BLUE_BERRY),
			new ItemStack(AetherFlora.SATIVAL_BULB), new ItemStack(AetherFlora.HOLYSTONE_TWIG), new ItemStack(AetherBlocks.HOLYSTONE_ROCK));
		return table.get(this.random.nextInt(table.size())).copy();
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		Taegore baby = AetherAnimals.TAEGORE.create(level, EntitySpawnReason.BREEDING);
		if (baby != null) {
			baby.setZone(this.zone());
		}
		return baby;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("dig_wait", this.digWait);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.digWait = input.getIntOr("dig_wait", DIG_WAIT_FIRST);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.HOGLIN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HOGLIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HOGLIN_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.HOGLIN_STEP, 0.15F, 0.8F);
	}

	/** Pick a place to dig, walk there, dig. */
	private final class DigGoal extends Goal {
		private BlockPos spot;
		private int dig, giveUp;

		DigGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		private BlockPos pick() {
			Taegore self = Taegore.this;
			for (int attempt = 0; attempt < 12; attempt++) {
				double angle = self.getRandom().nextDouble() * Math.PI * 2.0;
				double dist = DIG_MIN + self.getRandom().nextDouble() * (DIG_MAX - DIG_MIN);
				BlockPos column = BlockPos.containing(self.getX() + Math.cos(angle) * dist, self.getY(), self.getZ() + Math.sin(angle) * dist);
				if (!self.level().hasChunkAt(column)) {
					continue;
				}
				// the ground near its own height (a ledge it can reach), not the top of some island overhead
				BlockPos ground = null;
				for (int dy = 4; dy >= -6; dy--) {
					BlockPos here = column.above(dy);
					if (self.level().getBlockState(here.above()).isAir() && diggable(self.level().getBlockState(here))) {
						ground = here;
						break;
					}
				}
				if (ground != null && !self.explored.contains(ground)) {
					return ground;
				}
			}
			return null;
		}

		@Override
		public boolean canUse() {
			Taegore self = Taegore.this;
			if (self.digWait > 0 || self.isBaby() || !self.onGround() || self.getRandom().nextInt(30) != 0) {
				return false;
			}
			this.spot = this.pick();
			return this.spot != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.spot != null && this.giveUp < 600 && Taegore.this.digWait <= 0;
		}

		@Override
		public void start() {
			this.dig = 0;
			this.giveUp = 0;
			Taegore.this.getNavigation().moveTo(this.spot.getX() + 0.5, this.spot.getY() + 1.0, this.spot.getZ() + 0.5, 1.0);
		}

		@Override
		public void stop() {
			Taegore self = Taegore.this;
			self.entityData.set(DIGGING, false);
			if (self.digWait <= 0) {
				// gave up: try again some other time
				self.digWait = 200;
			}
			this.spot = null;
		}

		@Override
		public void tick() {
			Taegore self = Taegore.this;
			this.giveUp++;
			boolean there = self.distanceToSqr(this.spot.getX() + 0.5, this.spot.getY() + 1.0, this.spot.getZ() + 0.5) < 4.0;
			if (!there) {
				if (self.getNavigation().isDone()) {
					self.getNavigation().moveTo(this.spot.getX() + 0.5, this.spot.getY() + 1.0, this.spot.getZ() + 0.5, 1.0);
				}
				return;
			}
			self.getNavigation().stop();
			self.getLookControl().setLookAt(this.spot.getX() + 0.5, this.spot.getY() + 0.5, this.spot.getZ() + 0.5);
			self.entityData.set(DIGGING, true);
			this.dig++;
			if (self.level() instanceof ServerLevel level) {
				if (this.dig % 6 == 0) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(this.spot)), this.spot.getX() + 0.5, this.spot.getY() + 1.0,
						this.spot.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.05);
					level.playSound(null, this.spot, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 0.6F, 0.9F);
				}
				if (this.dig >= DIG_TICKS) {
					self.spawnAtLocation(level, self.findings());
					self.explored.addLast(this.spot);
					while (self.explored.size() > MEMORY) {
						self.explored.removeFirst();
					}
					self.digWait = DIG_WAIT;
					self.entityData.set(DIGGING, false);
					this.spot = null;
				}
			}
		}
	}
}
