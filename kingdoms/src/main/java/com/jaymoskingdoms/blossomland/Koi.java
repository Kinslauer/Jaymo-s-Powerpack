package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.BlossomVillages;
import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.creatures.Looks;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The koi of a blossom town's garden pond.
 * <ul>
 * <li><b>They come to be fed.</b> A koi swims to whoever stands at the water with food in hand, and to food thrown in;
 * it eats from the hand as well (use the food on it). It eats bread, seeds, rice, most things.</li>
 * <li><b>Kept, they grow and breed.</b> Each feed grows it a little ({@link #FEED_GROW}), from a finger-length fry to a
 * fish as long as your arm. Two grown ones ({@link #GROWN}) fed within {@link #FED_TICKS} of each other spawn a fry,
 * while the pond has water for it ({@link #WATER_PER_KOI} blocks of water a fish, {@link #POND_MOST} at most).</li>
 * <li><b>Left alone, nothing is lost.</b> An unfed pond's koi stay small and come and go; a fed koi stays for good.</li>
 * </ul>
 * Wild ones appear only in still water inside a blossom settlement.
 */
public class Koi extends AbstractFish implements Looks {
	static final double HEALTH = 6.0;
	/** Growth a feed gives, the size from which it breeds, and the ticks between two feeds that count for anything. */
	static final float FEED_GROW = 0.1F, GROWN = 0.6F;
	static final int FEED_EVERY = 200;
	/** Two grown koi fed within this long of each other may spawn; then neither does again for this long. */
	static final long FED_TICKS = 2400L;
	static final int SPAWN_REST = 12000;
	/** Water a pond needs for each fish, the most fish it keeps, and how far a pond is looked over. */
	static final int WATER_PER_KOI = 8, POND_MOST = 10, POND_REACH = 6;
	/** How far it sees food in the water or in a hand. */
	static final double FOOD_RANGE = 8.0, BEG_RANGE = 6.0, EAT_REACH = 1.3;

	private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(Koi.class, EntityDataSerializers.FLOAT);

	private int nextFeed, spawnRest;
	private long fedAt = Long.MIN_VALUE / 2;

	public Koi(EntityType<? extends Koi> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, HEALTH);
	}

	/** Still water inside a blossom settlement, under the sky. */
	public static boolean checkSpawn(EntityType<Koi> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos).isSource() || !level.canSeeSky(pos.above())) {
			return false;
		}
		ServerLevel server = level.getLevel();
		Faction here = Factions.at(server.getServer(), server, pos);
		return here != null && BlossomVillages.blossom(server, here)
			&& server.getEntitiesOfClass(Koi.class, new net.minecraft.world.phys.AABB(pos).inflate(POND_REACH), k -> true).size() < 3;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SIZE, 0.0F);
	}

	@Override
	protected void registerGoals() {
		// not the fishes' own goals: those run from people, and a koi comes to them
		this.goalSelector.addGoal(0, new PanicGoal(this, 1.4));
		this.goalSelector.addGoal(1, new FoodGoal());
		this.goalSelector.addGoal(2, new BegGoal());
		this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 40));
	}

	public float size() {
		return this.entityData.get(SIZE);
	}

	@Override
	public int look() {
		return 0;
	}

	@Override
	public float fullness() {
		return this.size();
	}

	static boolean food(ItemStack stack) {
		return stack.has(DataComponents.FOOD) || stack.is(ItemTags.CHICKEN_FOOD) || stack.is(Items.WHEAT);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.nextFeed > 0) {
			this.nextFeed--;
		}
		if (this.spawnRest > 0) {
			this.spawnRest--;
		}
		if (this.nextFeed == 0 && this.tickCount % 5 == 0) {
			List<ItemEntity> near = level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(EAT_REACH), i -> i.isAlive() && i.isInWater() && food(i.getItem()));
			if (!near.isEmpty()) {
				ItemEntity bite = near.get(0);
				ItemStack stack = bite.getItem();
				stack.shrink(1);
				if (stack.isEmpty()) {
					bite.discard();
				} else {
					bite.setItem(stack);
				}
				this.fed(level);
			}
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!food(stack) || stack.is(Items.WATER_BUCKET)) {
			return InteractionResult.PASS;
		}
		if (this.level() instanceof ServerLevel level) {
			if (this.nextFeed > 0) {
				return InteractionResult.CONSUME;
			}
			stack.consume(1, player);
			this.fed(level);
		}
		return InteractionResult.SUCCESS;
	}

	/** One feed: it grows, it stays, and with another grown, fed koi by it there may be a fry. */
	private void fed(ServerLevel level) {
		this.nextFeed = FEED_EVERY;
		this.fedAt = level.getGameTime();
		this.entityData.set(SIZE, Math.min(1.0F, this.size() + FEED_GROW));
		this.setPersistenceRequired();
		this.heal(2.0F);
		this.playSound(SoundEvents.GENERIC_EAT.value(), 0.4F, 1.6F);
		level.sendParticles(ParticleTypes.BUBBLE, this.getX(), this.getY() + 0.2, this.getZ(), 5, 0.15, 0.1, 0.15, 0.02);
		if (this.size() < GROWN || this.spawnRest > 0) {
			return;
		}
		List<Koi> pond = level.getEntitiesOfClass(Koi.class, this.getBoundingBox().inflate(POND_REACH), k -> k.isAlive());
		Koi mate = null;
		for (Koi k : pond) {
			if (k != this && k.size() >= GROWN && k.spawnRest == 0 && level.getGameTime() - k.fedAt <= FED_TICKS) {
				mate = k;
				break;
			}
		}
		if (mate == null || pond.size() >= Math.min(POND_MOST, this.water(level) / WATER_PER_KOI)) {
			return;
		}
		Koi fry = BlossomAnimals.KOI.create(level, EntitySpawnReason.BREEDING);
		if (fry == null) {
			return;
		}
		fry.snapTo(this.getX(), this.getY(), this.getZ(), this.random.nextFloat() * 360.0F, 0.0F);
		fry.setPersistenceRequired();
		level.addFreshEntity(fry);
		this.spawnRest = SPAWN_REST;
		mate.spawnRest = SPAWN_REST;
		level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.5, this.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
	}

	/** Blocks of water about it. */
	private int water(ServerLevel level) {
		int n = 0;
		BlockPos at = this.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-POND_REACH, -2, -POND_REACH), at.offset(POND_REACH, 2, POND_REACH))) {
			if (level.getFluidState(p).is(FluidTags.WATER)) {
				n++;
			}
		}
		return n;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return !this.isPersistenceRequired() && !this.hasCustomName();
	}

	@Override
	public ItemStack getBucketItemStack() {
		return new ItemStack(Items.WATER_BUCKET);
	}

	@Override
	protected SoundEvent getFlopSound() {
		return SoundEvents.COD_FLOP;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("size", this.size());
		output.putInt("spawn_rest", this.spawnRest);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(SIZE, input.getFloatOr("size", 0.0F));
		this.spawnRest = input.getIntOr("spawn_rest", 0);
	}

	/** Swims to food lying in the water. */
	private final class FoodGoal extends Goal {
		private ItemEntity food;

		FoodGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (Koi.this.nextFeed > 0 || Koi.this.tickCount % 10 != 0) {
				return false;
			}
			List<ItemEntity> seen = Koi.this.level().getEntitiesOfClass(ItemEntity.class, Koi.this.getBoundingBox().inflate(FOOD_RANGE, 3.0, FOOD_RANGE),
				i -> i.isAlive() && i.isInWater() && food(i.getItem()));
			this.food = seen.isEmpty() ? null : seen.get(0);
			return this.food != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.food != null && this.food.isAlive() && Koi.this.nextFeed == 0;
		}

		@Override
		public void tick() {
			Koi.this.getNavigation().moveTo(this.food, 1.3);
		}

		@Override
		public void stop() {
			this.food = null;
		}
	}

	/** Comes to whoever stands at the water with food in hand, and waits there mouthing at the surface. */
	private final class BegGoal extends Goal {
		private Player giver;

		BegGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (Koi.this.tickCount % 20 != 0) {
				return false;
			}
			this.giver = Koi.this.level().getNearestPlayer(Koi.this.getX(), Koi.this.getY(), Koi.this.getZ(), BEG_RANGE,
				e -> e instanceof Player p && !p.isSpectator() && (food(p.getMainHandItem()) || food(p.getOffhandItem())));
			return this.giver != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.giver != null && this.giver.isAlive() && Koi.this.distanceToSqr(this.giver) < BEG_RANGE * BEG_RANGE * 2.0
				&& (food(this.giver.getMainHandItem()) || food(this.giver.getOffhandItem()));
		}

		@Override
		public void tick() {
			Vec3 to = this.giver.position();
			Koi.this.getLookControl().setLookAt(this.giver);
			if (Koi.this.tickCount % 10 == 0) {
				Koi.this.getNavigation().moveTo(to.x, Koi.this.getY() + 0.3, to.z, 1.1);
			}
		}

		@Override
		public void stop() {
			this.giver = null;
		}
	}
}
