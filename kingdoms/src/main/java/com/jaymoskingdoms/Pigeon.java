package com.jaymoskingdoms;

import java.util.EnumSet;
import java.util.UUID;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.200) A messenger pigeon ({@link PigeonPost}): a rock dove of the plains and woods, tamed with seeds as a parrot is,
 * that never rides a shoulder. Use a sealed letter on your own pigeon and it takes it, climbs away out of sight and flies
 * it to whoever it's for, wherever they are; it comes down to them, hands it over and flies home to you. A wild one
 * brings the world's news ({@link Post}) and is gone again.
 * <p>On an errand ({@link #errand}) it flies by its own goal, above the parrot's: away ({@link #DEPART}, then it's
 * taken out of the world into the post's record), or in to someone ({@link #ARRIVE}).
 */
public final class Pigeon extends Parrot {
	public static final int NONE = 0, DEPART = 1, ARRIVE = 2;
	/** Its flight: faster than a parrot's. */
	static final double FLYING_SPEED = 0.6, HEALTH = 6.0;
	/** Flying off: how long before it's out of sight (ticks), how far ahead and up it makes for each tick. */
	static final int DEPART_TICKS = 60;
	static final double DEPART_AHEAD = 10.0, DEPART_RISE = 5.0;
	/** Coming in: how near is handing over; within {@link #NEAR} after {@link #REACH_TICKS} it reaches out anyway, and after {@link #LAND_TICKS} it alights at their feet. */
	static final double HAND_OVER = 1.8, NEAR = 8.0, NAVIGATE = 12.0, GIVE_UP_RANGE = 160.0;
	static final int REACH_TICKS = 300, LAND_TICKS = 600, ARRIVE_TICKS = 900;
	static final double SPEED = 1.6;

	public static EntityType<Pigeon> TYPE;
	/** Wild ones: where they live, how many to a spawn weight (a sheep is 12). */
	static final int SPAWN_WEIGHT = 4, SPAWN_MIN = 2, SPAWN_MAX = 4;

	int errand = NONE;
	/** Who it's flying in to. */
	@Nullable UUID toward;
	/** What it carries (a letter), or empty. */
	ItemStack carried = ItemStack.EMPTY;
	/** On its way home to its owner; a wild one, gone once it's delivered. */
	boolean homeward, wild;
	private int errandTicks;
	private @Nullable Vec3 away;

	public Pigeon(EntityType<? extends Pigeon> type, Level level) {
		super(type, level);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("pigeon"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Pigeon>of(Pigeon::new, MobCategory.CREATURE)
			.sized(0.5F, 0.8F).eyeHeight(0.5F).clientTrackingRange(10).build(key));
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
		SpawnPlacements.register(TYPE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING, Pigeon::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.FOREST, Biomes.BIRCH_FOREST,
			Biomes.FLOWER_FOREST, Biomes.SAVANNA, Biomes.CHERRY_GROVE), MobCategory.CREATURE, TYPE, SPAWN_WEIGHT, SPAWN_MIN, SPAWN_MAX);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Parrot.createAttributes().add(Attributes.FLYING_SPEED, FLYING_SPEED).add(Attributes.MAX_HEALTH, HEALTH);
	}

	public static boolean checkSpawn(EntityType<Pigeon> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return Animal.checkAnimalSpawnRules(type, level, reason, pos, random);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(-1, new ErrandGoal());
	}

	/** No squawk: a pigeon is quiet but for its wings. */
	@Override
	public @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	public boolean canSitOnShoulder() {
		return false;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.errand != NONE) {
			return InteractionResult.PASS;
		}
		if (player.getItemInHand(hand).is(Letters.SEALED) && this.isTame() && this.isOwnedBy(player)) {
			if (player instanceof ServerPlayer sp) {
				PigeonPost.send(this, sp, hand);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level && !this.carried.isEmpty()) {
			this.spawnAtLocation(level, this.carried);
			this.carried = ItemStack.EMPTY;
		}
		super.die(source);
	}

	/** Off on an errand: away out of sight. */
	void depart() {
		this.errand = DEPART;
		this.errandTicks = 0;
		this.away = null;
		this.setOrderedToSit(false);
		this.setInSittingPose(false);
		this.getNavigation().stop();
		this.playSound(SoundEvents.PARROT_FLY, 1.0F, 1.2F);
	}

	/** In to {@code to}, carrying {@code stack} (or nothing). */
	void arrive(UUID to, ItemStack stack, boolean homeward) {
		this.errand = ARRIVE;
		this.errandTicks = 0;
		this.toward = to;
		this.carried = stack;
		this.homeward = homeward;
	}

	/** Its errand over: a pigeon again. */
	void rest() {
		this.errand = NONE;
		this.toward = null;
		this.carried = ItemStack.EMPTY;
		this.homeward = false;
		this.getNavigation().stop();
	}

	/** Flies its errand, ahead of every other goal. */
	final class ErrandGoal extends Goal {
		ErrandGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return Pigeon.this.errand != NONE;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Pigeon bird = Pigeon.this;
			if (!(bird.level() instanceof ServerLevel level)) {
				return;
			}
			bird.errandTicks++;
			if (bird.errand == DEPART) {
				if (bird.away == null) {
					float yaw = bird.getRandom().nextFloat() * (float)(Math.PI * 2.0);
					bird.away = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
				}
				Vec3 to = bird.position().add(bird.away.scale(DEPART_AHEAD)).add(0.0, DEPART_RISE, 0.0);
				bird.getMoveControl().setWantedPosition(to.x, to.y, to.z, SPEED);
				bird.getLookControl().setLookAt(to.x, to.y, to.z);
				if (bird.errandTicks >= DEPART_TICKS) {
					PigeonPost.departed(level, bird);
				}
				return;
			}
			ServerPlayer target = bird.toward == null ? null : level.getServer().getPlayerList().getPlayer(bird.toward);
			if (target == null || target.level() != level || bird.distanceTo(target) > GIVE_UP_RANGE || bird.errandTicks > ARRIVE_TICKS) {
				PigeonPost.lost(level, bird);
				return;
			}
			double d = bird.distanceTo(target);
			if (d <= HAND_OVER || d <= NEAR && bird.errandTicks > REACH_TICKS) {
				PigeonPost.arrived(level, bird, target);
				return;
			}
			if (bird.errandTicks > LAND_TICKS) {
				// (it can't find a way in: it alights at their feet)
				bird.teleportTo(target.getX(), target.getY() + 1.0, target.getZ());
				return;
			}
			bird.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (d > NAVIGATE) {
				bird.getMoveControl().setWantedPosition(target.getX(), target.getEyeY() + 1.0, target.getZ(), SPEED);
			} else if (bird.getNavigation().isDone() || bird.tickCount % 10 == 0) {
				bird.getNavigation().moveTo(target.getX(), target.getEyeY() - 0.3, target.getZ(), SPEED);
			}
		}
	}
}
