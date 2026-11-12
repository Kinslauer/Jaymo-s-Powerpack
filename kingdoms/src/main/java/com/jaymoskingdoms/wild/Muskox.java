package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The muskox of the far cold: a giant shaggy cousin of the goats, in herds, brown or black. It does what the real one
 * does, it changes the land it lives on, and it pays back being looked after:
 *
 * <ul>
 * <li><b>Grazes lichen</b> ({@link GrazeGoal}): when it's hungry (every {@link #GRAZE_EVERY} ticks or so) it walks to the
 * game's glow lichen within {@link #GRAZE_RANGE} blocks and eats it off the rock. Only the game's own lichen: a
 * sprouted or snipped one (Jaymo's Powers) is another block, and is left alone.</li>
 * <li><b>Sheds its underwool</b> ({@link #shed}): after each feed it drops string, more the better it lives
 * ({@link #comfort}): room about it, a herd of {@link #HERD_LEAST} to {@link #HERD_MOST} others near, open sky by day,
 * its home country ({@link #HOME}). Crowded ({@link #CROWD} others within {@link #CROWD_RANGE}) it sheds nothing.</li>
 * <li><b>Keeps together</b>: {@link HerdPull} draws a straggler back to its herd, and the herd toward lichen.</li>
 * <li><b>Recovers its losses</b>: when one dies the others within {@link #LOSS_RANGE} remember it for
 * {@link #LOSS_TICKS}. One that remembers, has no calf within {@link #CALF_RANGE} and fewer than {@link #HERD_CAP}
 * of its kind within {@link #HERD_RANGE}, is ready to breed for {@link #LOVE_TICKS} after its next feed.</li>
 * <li><b>Runs from its hunters</b> ({@link FleeGoal}): anything in {@link #PREDATORS} within {@link #FLEE_RANGE}, each
 * its own way. Polar bears hunt it ({@link FedPredators}).</li>
 * <li><b>Stands its ground with people</b>: it leaves them be, but one that's struck (a grown one, by anything but
 * its hunters) butts back hard for a while ({@link #GRUDGE_TICKS}). A calf only runs.</li>
 * </ul>
 * Bred by hand with glow lichen. Its model and coats are our own, from {@code docs/Muskox-Model-Spec.md}.
 */
public class Muskox extends Animal {
	static final double HEALTH = 24.0, SPEED = 0.18, DAMAGE = 4.0, ATTACK_KNOCKBACK = 2.0, KNOCKBACK_RESISTANCE = 0.6;
	/** One in this many wears the black coat (by its id, so it never changes). */
	static final int BLACK_ONE_IN = 3;
	/** Natural spawning: weight and herd size where {@link #HAS_MUSKOX} says, and the light it needs. */
	static final int SPAWN_WEIGHT = 4, SPAWN_LEAST = 3, SPAWN_MOST = 6;
	/** Ticks between feeds (and up to this much more), how far it finds lichen (blocks across, up and down). */
	static final int GRAZE_EVERY = 2400, GRAZE_JITTER = 600, GRAZE_RANGE = 10, GRAZE_UP = 4;
	/** With none found it looks again after this long; it gives a patch up after this long on the way (ticks). */
	static final int GRAZE_RETRY = 200, GRAZE_GIVE_UP = 300;
	/** How near it must stand to eat (blocks), how long the head stays down, and when the bite lands (ticks). */
	static final double GRAZE_REACH = 2.3;
	static final int GRAZE_ANIM = 40, GRAZE_BITE = 30, GRAZE_RAMP = 6;
	static final float GRAZE_HEAL = 2.0F;
	/** String shed after a feed, least and most, by how many of the four comforts it has. */
	static final int[] SHED_LEAST = {0, 1, 2, 4, 6}, SHED_MOST = {1, 2, 4, 6, 8};
	/** A good herd: this many others within this far. Crowded: more than this many within this far. */
	static final int HERD_LEAST = 2, HERD_MOST = 8, CROWD = 12;
	static final double COMFORT_RANGE = 16.0, CROWD_RANGE = 8.0;
	/** Room about it: no solid block this far out at head height on this many of its four sides, nor this far above. */
	static final int OPEN_REACH = 3, OPEN_SIDES = 3, OPEN_ABOVE = 2;
	/** A death is remembered by those this near, this long (three days). */
	static final double LOSS_RANGE = 32.0;
	static final long LOSS_TICKS = 72000L;
	/** Recovering: no calf this near, fewer than this many of its kind this near, and how long it's ready to breed. */
	static final double CALF_RANGE = 16.0, HERD_RANGE = 24.0;
	static final int HERD_CAP = 10, LOVE_TICKS = 2400;
	/** It runs from a hunter this near, this far, this fast; it looks about this often (ticks). */
	static final double FLEE_RANGE = 12.0, FLEE_SPEED = 1.7;
	static final int FLEE_RUN = 16, FLEE_RUN_Y = 7, FLEE_EVERY = 15;
	/** How fast it closes to butt, and how long after its last hurt it lets the matter drop (ticks). */
	static final double BUTT_SPEED = 1.5;
	static final int GRUDGE_TICKS = 160, BUTT_ANIM = 10;

	/** What it runs from. */
	static final TagKey<EntityType<?>> PREDATORS = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("predators"));
	/** Its home country (where it sheds best), and where herds spawn. */
	static final TagKey<Biome> HOME = TagKey.create(Registries.BIOME, JaymosKingdoms.id("muskox_home"));
	static final TagKey<Biome> HAS_MUSKOX = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_muskox"));

	private static final byte EVENT_BUTT = 4, EVENT_GRAZE = 10;

	/** (Server) ticks until it's hungry again; game time until which it remembers a death in the herd. */
	private int grazeIn;
	private long lossUntil;
	/** (Server) running from a hunter right now ({@link HerdPull} lets it be). */
	private boolean fleeing;
	/** (Client) ticks left of its head down to graze, and of a butt. */
	private int grazeAnim, buttAnim;

	public Muskox(EntityType<? extends Muskox> type, Level level) {
		super(type, level);
		this.grazeIn = this.random.nextInt(GRAZE_EVERY);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.ATTACK_KNOCKBACK, ATTACK_KNOCKBACK)
			.add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
	}

	/** Natural spawning: on any firm ground in the light (its country is bare rock, gravel and snow as often as grass). */
	public static boolean checkSpawn(EntityType<Muskox> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && isBrightEnoughToSpawn(level, pos);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new FleeGoal());
		this.goalSelector.addGoal(1, new PanicGoal(this, FLEE_SPEED) {
			@Override
			protected boolean shouldPanic() {
				// a calf runs from whatever hurts it; the grown ones butt back
				return Muskox.this.isBaby() && super.shouldPanic();
			}
		});
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, BUTT_SPEED, true));
		this.goalSelector.addGoal(3, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(4, new TemptGoal(this, 1.1, stack -> stack.is(Items.GLOW_LICHEN), false));
		this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(6, new GrazeGoal());
		this.goalSelector.addGoal(7, new HerdPull.PullGoal(this));
		this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				LivingEntity by = Muskox.this.getLastHurtByMob();
				return !Muskox.this.isBaby() && by != null && !predator(by) && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				// left alone a while, it lets the matter drop
				return Muskox.this.tickCount - Muskox.this.getLastHurtByMobTimestamp() <= GRUDGE_TICKS && super.canContinueToUse();
			}
		});
	}

	static boolean predator(Entity e) {
		return e.getType().builtInRegistryHolder().is(PREDATORS);
	}

	/** Its coat: black for one in {@link #BLACK_ONE_IN}, else brown. */
	public boolean blackCoat() {
		return Math.floorMod(this.getUUID().hashCode(), BLACK_ONE_IN) == 0;
	}

	/** Running from a hunter right now. */
	boolean fleeing() {
		return this.fleeing;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.grazeIn > 0) {
			this.grazeIn--;
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level().isClientSide()) {
			if (this.grazeAnim > 0) {
				this.grazeAnim--;
			}
			if (this.buttAnim > 0) {
				this.buttAnim--;
			}
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_GRAZE) {
			this.grazeAnim = GRAZE_ANIM;
		} else if (id == EVENT_BUTT) {
			this.buttAnim = BUTT_ANIM;
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** (Client) how far its head is down to graze, 0 to 1. */
	public float grazeAmount(float partialTicks) {
		if (this.grazeAnim <= 0) {
			return 0.0F;
		}
		float left = this.grazeAnim - partialTicks, run = GRAZE_ANIM - left;
		return Math.clamp(Math.min(run, left) / GRAZE_RAMP, 0.0F, 1.0F);
	}

	/** (Client) how far into a butt it is: 0, up to 1 at the blow, and back. */
	public float buttAmount(float partialTicks) {
		if (this.buttAnim <= 0) {
			return 0.0F;
		}
		return (float) Math.sin(Math.PI * Math.clamp((BUTT_ANIM - this.buttAnim + partialTicks) / BUTT_ANIM, 0.0F, 1.0F));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		level.broadcastEntityEvent(this, EVENT_BUTT);
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			this.playSound(SoundEvents.GOAT_RAM_IMPACT, 1.0F, 0.7F);
		}
		return hit;
	}

	// ---- grazing, and what comes of a feed -------------------------------------------------------------------------

	/**
	 * The nearest of the game's glow lichen within {@code range} blocks across and {@code up} up or down, in loaded land
	 * (not the one at {@code skip}, which it couldn't get to).
	 */
	static @Nullable BlockPos findLichen(ServerLevel level, BlockPos from, int range, int up, @Nullable BlockPos skip) {
		if (!level.hasChunksAt(from.getX() - range, from.getZ() - range, from.getX() + range, from.getZ() + range)) {
			return null;
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockPos best = null;
		double bestSq = Double.MAX_VALUE;
		for (int dy = -up; dy <= up; dy++) {
			for (int dx = -range; dx <= range; dx++) {
				for (int dz = -range; dz <= range; dz++) {
					double sq = dx * dx + dy * dy * 4.0 + dz * dz;
					if (sq < bestSq && level.getBlockState(at.setWithOffset(from, dx, dy, dz)).is(Blocks.GLOW_LICHEN) && !at.equals(skip)) {
						bestSq = sq;
						best = at.immutable();
					}
				}
			}
		}
		return best;
	}

	/** It has eaten the lichen at {@code pos}: the lichen is gone, and it's fed. */
	private void eat(ServerLevel level, BlockPos pos) {
		if (level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			level.destroyBlock(pos, false);
		}
		this.playSound(SoundEvents.GOAT_EAT, 1.0F, this.getVoicePitch());
		this.grazeIn = GRAZE_EVERY + this.random.nextInt(GRAZE_JITTER);
		this.heal(GRAZE_HEAL);
		this.fed(level);
	}

	/** After a feed: it sheds by how well it lives, and a herd that has lost one of its own makes ready to breed. */
	private void fed(ServerLevel level) {
		int near = 0, crowd = 0, herd = 0, calves = 0;
		for (Muskox other : level.getEntitiesOfClass(Muskox.class, this.getBoundingBox().inflate(HERD_RANGE), m -> m != this && m.isAlive())) {
			double sq = other.distanceToSqr(this);
			if (sq <= HERD_RANGE * HERD_RANGE) {
				herd++;
			}
			if (sq <= COMFORT_RANGE * COMFORT_RANGE) {
				near++;
			}
			if (sq <= CROWD_RANGE * CROWD_RANGE) {
				crowd++;
			}
			if (other.isBaby() && sq <= CALF_RANGE * CALF_RANGE) {
				calves++;
			}
		}
		int string = this.shed(level, near, crowd);
		if (string > 0) {
			this.spawnAtLocation(level, new ItemStack(Items.STRING, string));
		}
		if (level.getGameTime() < this.lossUntil && calves == 0 && herd + 1 < HERD_CAP && this.getAge() == 0 && !this.isInLove()) {
			this.setInLoveTime(LOVE_TICKS);
			// (hearts, as a hand-fed one shows)
			level.broadcastEntityEvent(this, (byte) 18);
		}
	}

	/** How much string it sheds now, with {@code near} others in its herd and {@code crowd} right on top of it. */
	private int shed(ServerLevel level, int near, int crowd) {
		if (crowd > CROWD) {
			return 0;
		}
		int comforts = this.comfort(level, near);
		return SHED_LEAST[comforts] + this.random.nextInt(SHED_MOST[comforts] - SHED_LEAST[comforts] + 1);
	}

	/** How many of its four comforts it has: room about it, a herd, open sky by day, its home country. */
	private int comfort(ServerLevel level, int near) {
		BlockPos head = BlockPos.containing(this.getX(), this.getEyeY(), this.getZ());
		int comforts = 0;
		if (this.roomy(level, head)) {
			comforts++;
		}
		if (near >= HERD_LEAST && near <= HERD_MOST) {
			comforts++;
		}
		if (level.isBrightOutside() && level.canSeeSky(head)) {
			comforts++;
		}
		if (level.getBiome(this.blockPosition()).is(HOME)) {
			comforts++;
		}
		return comforts;
	}

	private boolean roomy(ServerLevel level, BlockPos head) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int up = 1; up <= OPEN_ABOVE; up++) {
			if (level.getBlockState(at.setWithOffset(head, 0, up, 0)).isSolid()) {
				return false;
			}
		}
		int open = 0;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			boolean clear = true;
			for (int out = 1; out <= OPEN_REACH && clear; out++) {
				clear = !level.getBlockState(at.setWithOffset(head, side.getStepX() * out, 0, side.getStepZ() * out)).isSolid();
			}
			if (clear) {
				open++;
			}
		}
		return open >= OPEN_SIDES;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			long until = level.getGameTime() + LOSS_TICKS;
			for (Muskox other : level.getEntitiesOfClass(Muskox.class, this.getBoundingBox().inflate(LOSS_RANGE), m -> m != this && m.isAlive())) {
				other.lossUntil = until;
			}
		}
	}

	// ---- breeding ---------------------------------------------------------------------------------------------------

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.GLOW_LICHEN);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return Wild.MUSKOX.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	public void spawnChildFromBreeding(ServerLevel level, Animal partner) {
		super.spawnChildFromBreeding(level, partner);
		// the loss is made good
		this.lossUntil = 0L;
		if (partner instanceof Muskox other) {
			other.lossUntil = 0L;
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("graze_in", this.grazeIn);
		output.putLong("loss_until", this.lossUntil);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.grazeIn = input.getIntOr("graze_in", this.grazeIn);
		this.lossUntil = input.getLongOr("loss_until", 0L);
	}

	// ---- its voice ----------------------------------------------------------------------------------------------------

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.GOAT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.GOAT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GOAT_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.GOAT_STEP, 0.15F, 0.8F);
	}

	@Override
	public float getVoicePitch() {
		// (a goat's bleat, far deeper)
		return super.getVoicePitch() * 0.55F;
	}

	// ---- its goals ----------------------------------------------------------------------------------------------------

	/** Runs from the nearest hunter within {@link #FLEE_RANGE}, looking about every {@link #FLEE_EVERY} ticks. */
	private final class FleeGoal extends Goal {
		private long lookAt;
		private @Nullable Vec3 to;

		FleeGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			long now = Muskox.this.level().getGameTime();
			if (now < this.lookAt) {
				return false;
			}
			this.lookAt = now + FLEE_EVERY;
			List<Mob> hunters = Muskox.this.level().getEntitiesOfClass(Mob.class, Muskox.this.getBoundingBox().inflate(FLEE_RANGE, 4.0, FLEE_RANGE),
				m -> m.isAlive() && predator(m) && !(m instanceof TamableAnimal pet && pet.isTame()));
			Mob nearest = null;
			double nearestSq = FLEE_RANGE * FLEE_RANGE;
			for (Mob hunter : hunters) {
				double sq = hunter.distanceToSqr(Muskox.this);
				if (sq <= nearestSq) {
					nearestSq = sq;
					nearest = hunter;
				}
			}
			if (nearest == null) {
				return false;
			}
			this.to = DefaultRandomPos.getPosAway(Muskox.this, FLEE_RUN, FLEE_RUN_Y, nearest.position());
			return this.to != null;
		}

		@Override
		public void start() {
			Muskox.this.fleeing = true;
			if (this.to != null) {
				Muskox.this.getNavigation().moveTo(this.to.x, this.to.y, this.to.z, FLEE_SPEED);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return !Muskox.this.getNavigation().isDone();
		}

		@Override
		public void stop() {
			Muskox.this.fleeing = false;
			this.to = null;
			// (it looks about again at once: still hunted, it runs on)
			this.lookAt = 0L;
		}
	}

	/** Hungry, it walks to the nearest glow lichen and eats it. */
	private final class GrazeGoal extends Goal {
		private long lookAt;
		private @Nullable BlockPos lichen, unreached;
		private @Nullable Path path;
		private int giveUp, chewing;
		private boolean done;

		GrazeGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (Muskox.this.grazeIn > 0 || Muskox.this.isBaby() || !(Muskox.this.level() instanceof ServerLevel level)) {
				return false;
			}
			long now = level.getGameTime();
			if (now < this.lookAt) {
				return false;
			}
			this.lookAt = now + GRAZE_RETRY + Muskox.this.random.nextInt(GRAZE_RETRY / 4);
			this.lichen = findLichen(level, Muskox.this.blockPosition(), GRAZE_RANGE, GRAZE_UP, this.unreached);
			if (this.lichen == null) {
				return false;
			}
			this.path = Muskox.this.getNavigation().createPath(this.lichen, 1);
			return this.path != null;
		}

		@Override
		public void start() {
			this.giveUp = GRAZE_GIVE_UP;
			this.chewing = 0;
			this.done = false;
			Muskox.this.getNavigation().moveTo(this.path, 1.0);
		}

		@Override
		public boolean canContinueToUse() {
			return !this.done && this.giveUp > 0 && this.lichen != null && Muskox.this.level().getBlockState(this.lichen).is(Blocks.GLOW_LICHEN);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (this.lichen == null || !(Muskox.this.level() instanceof ServerLevel level)) {
				return;
			}
			Vec3 at = Vec3.atCenterOf(this.lichen);
			Muskox.this.getLookControl().setLookAt(at.x, at.y, at.z, 30.0F, 30.0F);
			if (Muskox.this.distanceToSqr(at) > GRAZE_REACH * GRAZE_REACH) {
				this.giveUp--;
				this.chewing = 0;
				if (Muskox.this.getNavigation().isDone()) {
					Muskox.this.getNavigation().moveTo(at.x, at.y, at.z, 1.0);
				}
				return;
			}
			Muskox.this.getNavigation().stop();
			if (this.chewing == 0) {
				level.broadcastEntityEvent(Muskox.this, EVENT_GRAZE);
			}
			if (++this.chewing >= GRAZE_BITE) {
				Muskox.this.eat(level, this.lichen);
				this.done = true;
			}
		}

		@Override
		public void stop() {
			// (one it couldn't get to, up a cliff say, is passed over next time)
			this.unreached = this.done ? null : this.lichen;
			this.lichen = null;
			this.path = null;
		}
	}
}
