package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.BlossomVillages;
import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.KitchenCompat;
import com.jaymoskingdoms.creatures.Looks;
import com.jaymoskingdoms.seas.Mohknee;
import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the blossom lands' land animals share: a pose number for the renderer ({@link Looks}), being fed (by hand or by
 * what they find; a fed animal behaves differently for a while), running from the hunters of the pack
 * ({@code jaymoskingdoms:predators}), and going to a block they want ({@link SeekBlock}). They are not bred by hand:
 * their numbers are the land's.
 */
public abstract class BlossomAnimal extends Animal implements Looks {
	/** Poses the shared model knows: standing, and head down (eating, bowing, asleep). */
	public static final int STANDING = 0, HEAD_DOWN = 1;
	static final TagKey<EntityType<?>> PREDATORS = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("predators"));
	private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(BlossomAnimal.class, EntityDataSerializers.INT);

	/** Game time until which it counts as fed, and tick count until which it holds its head down. */
	protected long fedUntil;
	protected int headDownUntil;

	protected BlossomAnimal(EntityType<? extends BlossomAnimal> type, Level level) {
		super(type, level);
	}

	/** Natural spawning: animal ground and light, among the cherry anywhere or in any of its lands on Mohknee. */
	public static boolean checkSpawn(EntityType<? extends Animal> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return Animal.checkAnimalSpawnRules(type, level, reason, pos, random) && (level.getBiome(pos).is(Biomes.CHERRY_GROVE) || Mohknee.on(pos.getX(), pos.getZ()));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(LOOK, STANDING);
	}

	@Override
	public int look() {
		return this.entityData.get(LOOK);
	}

	protected void setLook(int look) {
		if (this.look() != look) {
			this.entityData.set(LOOK, look);
		}
	}

	@Override
	public float fullness() {
		return 0.0F;
	}

	/** Lowers its head for {@code ticks} (it comes up again by itself). */
	protected void headDown(int ticks) {
		this.headDownUntil = this.tickCount + ticks;
		this.setLook(HEAD_DOWN);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.headDownUntil != 0 && this.tickCount >= this.headDownUntil) {
			this.headDownUntil = 0;
			if (this.look() == HEAD_DOWN) {
				this.setLook(STANDING);
			}
		}
	}

	public boolean fed() {
		return this.level().getGameTime() < this.fedUntil;
	}

	/** Fed for {@code ticks} from now (never shortening what it already has). */
	protected void feed(ServerLevel level, long ticks) {
		this.fedUntil = Math.max(this.fedUntil, level.getGameTime() + ticks);
		this.playSound(SoundEvents.GENERIC_EAT.value(), 0.6F, 1.2F);
	}

	/** Takes {@code food} from a player's hand: one item, hearts, fed for {@code ticks}. Returns PASS when it isn't food. */
	protected InteractionResult handFeed(Player player, InteractionHand hand, Predicate<ItemStack> food, long ticks) {
		ItemStack stack = player.getItemInHand(hand);
		if (!food.test(stack)) {
			return InteractionResult.PASS;
		}
		if (this.level() instanceof ServerLevel level) {
			stack.consume(1, player);
			this.feed(level, ticks);
			level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + this.getBbHeight() + 0.2, this.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
		}
		return InteractionResult.SUCCESS;
	}

	static boolean winter(Level level) {
		return "winter".equals(KitchenCompat.season(level));
	}

	static boolean predator(Entity e) {
		return e.getType().builtInRegistryHolder().is(PREDATORS);
	}

	/** Whether it stands in a blossom settlement's land. */
	protected boolean amongBlossomFolk() {
		if (!(this.level() instanceof ServerLevel level)) {
			return false;
		}
		Faction here = Factions.at(level.getServer(), level, this.blockPosition());
		return here != null && BlossomVillages.blossom(level, here);
	}

	/** Runs from the pack's hunters. */
	protected Goal fleePredators(float range, double speed) {
		return new AvoidEntityGoal<>(this, LivingEntity.class, range, speed, speed * 1.25, BlossomAnimal::predator);
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putLong("fed_until", this.fedUntil);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.fedUntil = input.getLongOr("fed_until", 0L);
	}

	/**
	 * Goes to the nearest block it wants within reach and does something there: looks every {@link #every} ticks while
	 * {@link #wants()}, walks to it, and on arriving calls {@link #arrive}.
	 */
	protected abstract class SeekBlock extends Goal {
		private final int range, up, every;
		private final double speed, reach;
		private @Nullable BlockPos target;
		private int left;

		protected SeekBlock(int range, int up, int every, double speed, double reach) {
			this.range = range;
			this.up = up;
			this.every = every;
			this.speed = speed;
			this.reach = reach;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		protected abstract boolean wants();

		protected abstract boolean matches(ServerLevel level, BlockPos pos, BlockState state);

		protected abstract void arrive(ServerLevel level, BlockPos pos);

		@Override
		public boolean canUse() {
			BlossomAnimal me = BlossomAnimal.this;
			if ((me.tickCount + me.getId()) % this.every != 0 || !this.wants() || !(me.level() instanceof ServerLevel level)) {
				return false;
			}
			BlockPos at = me.blockPosition();
			double best = Double.MAX_VALUE;
			this.target = null;
			for (BlockPos p : BlockPos.betweenClosed(at.offset(-this.range, -this.up, -this.range), at.offset(this.range, this.up, this.range))) {
				double d = p.distSqr(at);
				if (d < best && this.matches(level, p, level.getBlockState(p))) {
					best = d;
					this.target = p.immutable();
				}
			}
			return this.target != null;
		}

		@Override
		public void start() {
			this.left = 300;
			this.go();
		}

		private void go() {
			BlossomAnimal.this.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, this.speed);
		}

		@Override
		public boolean canContinueToUse() {
			return this.target != null && this.left > 0 && this.wants();
		}

		@Override
		public void tick() {
			BlossomAnimal me = BlossomAnimal.this;
			this.left--;
			if (!(me.level() instanceof ServerLevel level) || this.target == null) {
				return;
			}
			if (me.position().distanceToSqr(Vec3.atBottomCenterOf(this.target)) <= this.reach * this.reach) {
				BlockPos there = this.target;
				this.target = null;
				if (this.matches(level, there, level.getBlockState(there))) {
					me.getNavigation().stop();
					this.arrive(level, there);
				}
			} else if (me.getNavigation().isDone() && this.left % 20 == 0) {
				this.go();
			}
		}

		@Override
		public void stop() {
			this.target = null;
		}
	}

	/** Keeps with its own kind: one that has strayed walks back to the nearest other. */
	protected final class KeepTogether extends Goal {
		private final double far, speed;
		private @Nullable LivingEntity other;

		protected KeepTogether(double far, double speed) {
			this.far = far;
			this.speed = speed;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			BlossomAnimal me = BlossomAnimal.this;
			if ((me.tickCount + me.getId()) % 40 != 0) {
				return false;
			}
			LivingEntity nearest = null;
			double best = Double.MAX_VALUE;
			for (BlossomAnimal a : me.level().getEntitiesOfClass(me.getClass(), me.getBoundingBox().inflate(this.far * 3.0, 8.0, this.far * 3.0), a -> a != me && a.isAlive())) {
				double d = a.distanceToSqr(me);
				if (d < best) {
					best = d;
					nearest = a;
				}
			}
			this.other = nearest;
			return nearest != null && best > this.far * this.far;
		}

		@Override
		public boolean canContinueToUse() {
			return this.other != null && this.other.isAlive() && BlossomAnimal.this.distanceToSqr(this.other) > 9.0 && !BlossomAnimal.this.getNavigation().isDone();
		}

		@Override
		public void start() {
			BlossomAnimal.this.getNavigation().moveTo(this.other, this.speed);
		}

		@Override
		public void stop() {
			this.other = null;
		}
	}
}
