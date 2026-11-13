package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.beasts.ActingMonster;
import com.jaymoskingdoms.beasts.Beasts;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The crystal beetle, drawn from yair's Blockbench model. Andy: "these should spawn in amethyst geodes." A cousin of
 * the barrow beetle that lives nowhere else, kept to the three rules for living things:
 * <ul>
 * <li><b>Sympathy.</b> It is a grazer that keeps to its food, as leaf beetles keep to one plant: it never leaves its
 * geode, it eats what grows there, and what it eats shows on it. It bites only what hurts it.</li>
 * <li><b>Feedback.</b> Every {@link #FEED_EVERY} it goes to the furthest-grown bud in reach and gnaws it down one stage
 * ({@link #graze}). What it eats grows on its shell as crystal ({@link #shards}). After {@link #SEED_FEEDS} meals what it
 * leaves behind quickens a plain amethyst block into a budding one ({@link #seed}), so a few beetles keep a geode
 * growing; but a geode with too many is gnawed bare, and a beetle that finds nothing to eat neither seeds nor breeds
 * until the buds are back. Two fed beetles together lay one more, up to {@link #CAP} to a geode.</li>
 * <li><b>Stewardship.</b> Shears take the crystal off its shell without hurting it ({@link #mobInteract}), and it grows
 * back as it eats: someone who leaves the buds for the beetles gets shards from the beetles and a geode that spreads.
 * Killed, it leaves almost nothing. Nobody who ignores them loses anything.</li>
 * </ul>
 * They turn up by themselves in geodes near players ({@link #register}), slowly.
 */
public class CrystalBeetle extends ActingMonster {
	static final double HEALTH = 10.0, ARMOR = 4.0, DAMAGE = 2.0, SPEED = 0.2;
	/** How far it strays from where it hatched, and how many live in one geode (counted this far about). */
	static final int HOME_RADIUS = 9, CAP = 4;
	static final double COUNT_RANGE = 14.0;
	/** A meal: how often (ticks), how far it looks for a bud, how near it must be to eat, and the bite's length (the animation's). */
	static final int FEED_EVERY = 2400, FEED_REACH = 6, BITE_TICKS = 20, BITE_LANDS = 10;
	static final double EAT_WITHIN = 1.8;
	/** The most crystal its shell carries (shards), the meals between seedings, and the meals a beetle needs before it lays. */
	static final int MAX_SHARDS = 4, SEED_FEEDS = 6, BROOD_FEEDS = 4;
	/** How near another fed beetle must be to lay, and how far it looks for a block to quicken. */
	static final double MATE_RANGE = 6.0;
	static final int SEED_REACH = 4;
	/** Turning up: how often geodes near each player are looked at (ticks), how far, the tries a look, and its chance of a beetle. */
	static final int LOOK_EVERY = 400, LOOK_RANGE = 32, LOOK_HEIGHT = 20, LOOK_TRIES = 24;
	static final float LOOK_CHANCE = 0.25F;
	/** Amethyst's colour, for the motes of a quickened block. */
	static final int COLOR = 0xB38EF3;

	private static final String[] ACTIONS = {"attack"};
	/** The buds, least grown first. */
	private static final Block[] STAGES = {Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER};

	/** The crystal on its shell, its meals since it last seeded and since it last laid, and the ticks to its next meal. */
	private int shards, seedFeeds, broodFeeds, hungry = FEED_EVERY / 4;
	/** The bud it is walking to. */
	private @Nullable BlockPos meal;

	public CrystalBeetle(EntityType<? extends CrystalBeetle> type, Level level) {
		super(type, level);
		this.xpReward = 1;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CrystalBeetle::turnUp);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
	}

	/** The crystal on its shell (0 to {@link #MAX_SHARDS}). */
	public int shards() {
		return this.shards;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.hasHome()) {
			this.setHomeTo(this.blockPosition(), HOME_RADIUS);
		}
		LivingEntity target = this.getTarget();
		if (this.acting("attack")) {
			if (this.actionRun() == BITE_LANDS && target != null) {
				this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), 1.2, true, 0.2, 0.0);
			}
			return;
		}
		if (target != null && target.isAlive()) {
			// it bites back at what hurt it, and only inside its geode
			if (!this.isWithinHome(target.blockPosition())) {
				this.setTarget(null);
				return;
			}
			this.getNavigation().moveTo(target, 1.3);
			if (this.distanceTo(target) < 1.6 + target.getBbWidth() / 2.0) {
				this.act("attack", BITE_TICKS);
			}
			return;
		}
		if (this.meal != null) {
			this.eat(level);
			return;
		}
		if (--this.hungry <= 0) {
			this.hungry = FEED_EVERY + this.getRandom().nextInt(FEED_EVERY / 4);
			this.meal = this.findBud(level);
			if (this.meal == null) {
				// nothing left to graze: it goes without, and a beetle that goes without neither seeds nor lays
				this.seedFeeds = 0;
				this.broodFeeds = 0;
			}
		}
	}

	/** The furthest-grown bud within reach (so the small ones are left to grow), or null. */
	private @Nullable BlockPos findBud(ServerLevel level) {
		BlockPos best = null;
		int bestStage = -1;
		for (BlockPos pos : BlockPos.betweenClosed(this.blockPosition().offset(-FEED_REACH, -3, -FEED_REACH),
			this.blockPosition().offset(FEED_REACH, 4, FEED_REACH))) {
			int stage = stage(level.getBlockState(pos));
			if (stage > bestStage) {
				bestStage = stage;
				best = pos.immutable();
			}
		}
		return best;
	}

	private static int stage(BlockState state) {
		for (int i = 0; i < STAGES.length; i++) {
			if (state.is(STAGES[i])) {
				return i;
			}
		}
		return -1;
	}

	/** Walking to its meal, and gnawing it down a stage once there. */
	private void eat(ServerLevel level) {
		BlockPos at = this.meal;
		BlockState bud = level.getBlockState(at);
		int stage = stage(bud);
		if (stage < 0) {
			this.meal = null;
			return;
		}
		Vec3 centre = Vec3.atCenterOf(at);
		if (this.position().distanceTo(centre) > EAT_WITHIN + 1.0) {
			if (this.getNavigation().isDone() && !this.getNavigation().moveTo(centre.x, centre.y, centre.z, 1.0)) {
				// (out of its reach, up a wall: it will look again next time)
				this.meal = null;
			}
			return;
		}
		this.meal = null;
		this.getNavigation().stop();
		this.getLookControl().setLookAt(centre);
		this.act("attack", BITE_TICKS);
		this.graze(level, at, bud, stage);
		this.shards = Math.min(MAX_SHARDS, this.shards + 1);
		if (++this.seedFeeds >= SEED_FEEDS) {
			this.seedFeeds = 0;
			this.seed(level);
		}
		if (++this.broodFeeds >= BROOD_FEEDS) {
			this.lay(level);
		}
	}

	/** The bud at {@code at} is gnawed down one stage (the least of them is eaten whole). */
	private void graze(ServerLevel level, BlockPos at, BlockState bud, int stage) {
		BlockState less = stage == 0 ? level.getFluidState(at).createLegacyBlock() : STAGES[stage - 1].defaultBlockState()
			.setValue(BlockStateProperties.FACING, bud.getValue(BlockStateProperties.FACING))
			.setValue(BlockStateProperties.WATERLOGGED, bud.getValue(BlockStateProperties.WATERLOGGED));
		level.setBlockAndUpdate(at, less);
		level.playSound(null, at, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.NEUTRAL, 0.6F, 1.4F);
		level.levelEvent(2001, at, Block.getId(bud));
	}

	/** What it leaves behind quickens one plain amethyst block near it that has room to bud. */
	private void seed(ServerLevel level) {
		for (BlockPos pos : BlockPos.randomBetweenClosed(this.getRandom(), 24, this.getBlockX() - SEED_REACH, this.getBlockY() - 2,
			this.getBlockZ() - SEED_REACH, this.getBlockX() + SEED_REACH, this.getBlockY() + 3, this.getBlockZ() + SEED_REACH)) {
			if (!level.getBlockState(pos).is(Blocks.AMETHYST_BLOCK)) {
				continue;
			}
			for (Direction side : Direction.values()) {
				if (level.getBlockState(pos.relative(side)).isAir()) {
					level.setBlockAndUpdate(pos, Blocks.BUDDING_AMETHYST.defaultBlockState());
					Fx.burst(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, COLOR, 1.0F, 14, 0.4, 0.4, 0.4);
					level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.2F);
					return;
				}
			}
		}
	}

	/** With another fed beetle near and room in the geode, one more hatches. */
	private void lay(ServerLevel level) {
		if (level.getEntitiesOfClass(CrystalBeetle.class, this.getBoundingBox().inflate(COUNT_RANGE), Entity::isAlive).size() >= CAP) {
			return;
		}
		for (CrystalBeetle mate : level.getEntitiesOfClass(CrystalBeetle.class, this.getBoundingBox().inflate(MATE_RANGE),
			b -> b != this && b.isAlive() && b.broodFeeds >= BROOD_FEEDS)) {
			CrystalBeetle young = Beasts.CRYSTAL_BEETLE.create(level, EntitySpawnReason.BREEDING);
			if (young == null) {
				return;
			}
			young.snapTo(this.getX(), this.getY(), this.getZ(), this.getRandom().nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(young);
			this.broodFeeds = 0;
			mate.broodFeeds = 0;
			return;
		}
	}

	/** Shears take the crystal off its shell; it isn't hurt, and doesn't mind. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.SHEARS) || this.shards <= 0) {
			return super.mobInteract(player, hand);
		}
		if (this.level() instanceof ServerLevel level) {
			this.spawnAtLocation(level, new ItemStack(Items.AMETHYST_SHARD, this.shards));
			this.shards = 0;
			stack.hurtAndBreak(1, player, hand);
			level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.NEUTRAL, 0.8F, 1.2F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// (its bite is its move)
		return false;
	}

	@Override
	public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("shards", this.shards);
		output.putInt("seed_feeds", this.seedFeeds);
		output.putInt("brood_feeds", this.broodFeeds);
		output.putInt("hungry", this.hungry);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.shards = input.getIntOr("shards", 0);
		this.seedFeeds = input.getIntOr("seed_feeds", 0);
		this.broodFeeds = input.getIntOr("brood_feeds", 0);
		this.hungry = input.getIntOr("hungry", FEED_EVERY / 4);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.AMETHYST_CLUSTER_BREAK;
	}

	@Override
	protected float getSoundVolume() {
		return 0.4F;
	}

	@Override
	public float getVoicePitch() {
		return 0.7F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.AMETHYST_BLOCK_STEP, 0.15F, 1.4F);
	}

	// ---- turning up ------------------------------------------------------------------------------------------------------

	/** Now and then a geode near a player that has room for one gets a beetle. */
	private static void turnUp(MinecraftServer server) {
		if (Beasts.CRYSTAL_BEETLE == null || server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			RandomSource random = level.getRandom();
			if (player.isSpectator() || random.nextFloat() >= LOOK_CHANCE) {
				continue;
			}
			for (int i = 0; i < LOOK_TRIES; i++) {
				BlockPos pos = player.blockPosition().offset(random.nextInt(LOOK_RANGE * 2 + 1) - LOOK_RANGE,
					random.nextInt(LOOK_HEIGHT * 2 + 1) - LOOK_HEIGHT, random.nextInt(LOOK_RANGE * 2 + 1) - LOOK_RANGE);
				if (!level.isLoaded(pos) || !level.getBlockState(pos).is(Blocks.BUDDING_AMETHYST)) {
					continue;
				}
				BlockPos floor = floor(level, pos);
				if (floor != null && level.getEntitiesOfClass(CrystalBeetle.class, new AABB(floor).inflate(COUNT_RANGE), Entity::isAlive).isEmpty()) {
					CrystalBeetle beetle = Beasts.CRYSTAL_BEETLE.create(level, EntitySpawnReason.NATURAL);
					if (beetle != null) {
						beetle.snapTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
						level.addFreshEntity(beetle);
					}
				}
				break;
			}
		}
	}

	/** Open air on amethyst inside the geode {@code budding} is part of: somewhere a beetle can stand, or null. */
	private static @Nullable BlockPos floor(ServerLevel level, BlockPos budding) {
		for (BlockPos pos : BlockPos.betweenClosed(budding.offset(-4, -4, -4), budding.offset(4, 4, 4))) {
			BlockState under = level.getBlockState(pos.below());
			if ((under.is(Blocks.AMETHYST_BLOCK) || under.is(Blocks.BUDDING_AMETHYST)) && level.getBlockState(pos).isAir()
				&& level.getBlockState(pos.above()).isAir()
				&& level.noCollision(Beasts.CRYSTAL_BEETLE.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
				return pos.immutable();
			}
		}
		return null;
	}
}
