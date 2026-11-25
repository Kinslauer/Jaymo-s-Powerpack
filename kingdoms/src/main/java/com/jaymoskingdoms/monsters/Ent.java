package com.jaymoskingdoms.monsters;

import com.jaymoskingdoms.ClearCuts;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The ent (after Mo' Creatures' ent, GPL-3, see {@code licenses/mocreatures}): a great old tree that woke. It sleeps
 * as a tree for as long as its forest is left alone. When too much of an old forest is felled ({@link ClearCuts}) one wakes,
 * walks after those who cut, and plants a sapling of its own wood wherever it treads. When no one has cut for
 * {@link #ROOT_AFTER} ticks it roots where it stands and is a tree again.
 * <ul>
 * <li>Sympathy: it hunts the cutters, not the walkers: it never turns on a player who has not cut the wood it wakes for
 * (unless they strike it), and it forgives one who plants saplings of their own ({@link ClearCuts#replant}).</li>
 * <li>Feedback: every sapling it plants takes weight off the forest's tally ({@link ClearCuts#replanted}), so a wood that is
 * growing back stops calling it; its saplings are what grows the forest back.</li>
 * <li>Stewardship: a player who takes only what the wood can spare, or who replants, is never touched; it roots again and
 * the tree it becomes is a gift, wood that grows.</li>
 * </ul>
 */
public class Ent extends PathfinderMob {
	static final double HEALTH = 120.0, SPEED = 0.22, DAMAGE = 10.0, KNOCKBACK = 1.0, FOLLOW = 64.0;
	/** How often it plants a sapling while it walks (ticks) and the most it plants where it is crowded. */
	static final int PLANT_EVERY = 40;
	/** How long without a fresh cut or a cutter in sight before it roots (ticks). */
	public static final int ROOT_AFTER = 3600;
	/** How tall the tree it roots as is, and how wide its crown. */
	static final int TRUNK = 6, CROWN = 2;

	private static final EntityDataAccessor<Boolean> BIRCH = SynchedEntityData.defineId(Ent.class, EntityDataSerializers.BOOLEAN);

	/** Who cut, and when (game time). */
	private final Map<UUID, Long> cutters = new HashMap<>();
	private Block sapling = Blocks.OAK_SAPLING;
	private long quietSince;

	public Ent(EntityType<? extends Ent> type, Level level) {
		super(type, level);
		this.xpReward = 20;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK).add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(BIRCH, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		// only those who cut
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL && this.cutters.containsKey(target.getUUID())));
	}

	public boolean birch() {
		return this.entityData.get(BIRCH);
	}

	/** The wood it is made of: a sapling, and so the tree it will root as. */
	public void setWood(Block sapling) {
		this.sapling = sapling;
		this.entityData.set(BIRCH, BuiltInRegistries.BLOCK.getKey(sapling).getPath().startsWith("birch"));
	}

	/** {@code player} has cut: it goes after them, and its quiet starts again. */
	public void provoke(UUID player, long now) {
		this.cutters.put(player, now);
		this.quietSince = now;
		this.setPersistenceRequired();
	}

	/** {@code player} has made it up: it lets them be. */
	public void forgive(UUID player) {
		this.cutters.remove(player);
		if (this.getTarget() != null && this.getTarget().getUUID().equals(player)) {
			this.setTarget(null);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		long now = level.getGameTime();
		if (this.quietSince == 0L) {
			this.quietSince = now;
		}
		if (this.getTarget() != null) {
			this.quietSince = Math.max(this.quietSince, now - ROOT_AFTER / 2);
		}
		if (this.tickCount % PLANT_EVERY == 0) {
			this.plant(level);
		}
		if (now - this.quietSince > ROOT_AFTER) {
			this.root(level);
		}
	}

	/** A sapling of its own wood where it stands, on earth that will take it. */
	private void plant(ServerLevel level) {
		BlockPos at = this.blockPosition();
		BlockState sap = this.sapling.defaultBlockState();
		if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).is(BlockTags.DIRT) && sap.canSurvive(level, at)) {
			level.setBlock(at, sap, Block.UPDATE_ALL);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.4, at.getZ() + 0.5, 5, 0.3, 0.2, 0.3, 0.0);
			ClearCuts.replanted(level, at);
		}
	}

	/** It settles into the ground and is a tree again: a trunk of its wood with a crown, and the ent is gone. */
	private void root(ServerLevel level) {
		String wood = BuiltInRegistries.BLOCK.getKey(this.sapling).getPath().replace("_sapling", "").replace("_propagule", "");
		Block log = BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(wood + "_log")).orElse(Blocks.OAK_LOG);
		Block leaves = BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(wood + "_leaves")).orElse(Blocks.OAK_LEAVES);
		BlockPos base = this.blockPosition();
		while (level.getBlockState(base).isAir() && base.getY() > level.getMinY()) {
			base = base.below();
		}
		base = base.above();
		for (int dy = 0; dy < TRUNK; dy++) {
			BlockPos p = base.above(dy);
			BlockState there = level.getBlockState(p);
			if (there.isAir() || there.canBeReplaced() || there.is(BlockTags.LEAVES)) {
				level.setBlock(p, log.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		for (int dx = -CROWN; dx <= CROWN; dx++) {
			for (int dz = -CROWN; dz <= CROWN; dz++) {
				for (int dy = TRUNK - 3; dy <= TRUNK; dy++) {
					BlockPos p = base.offset(dx, dy, dz);
					if (dx * dx + dz * dz + (dy - TRUNK + 1) * (dy - TRUNK + 1) <= CROWN * CROWN + 2 && level.getBlockState(p).isAir()) {
						level.setBlock(p, leaves.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 1.5, this.getZ(), 24, 0.8, 1.2, 0.8, 0.0);
		level.playSound(null, this.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.NEUTRAL, 1.0F, 0.5F);
		this.discard();
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WOOD_HIT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WOOD_BREAK;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WOOD_BREAK;
	}

	@Override
	public void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("sapling", BuiltInRegistries.BLOCK.getKey(this.sapling).toString());
		output.putLong("quiet_since", this.quietSince);
	}

	@Override
	public void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.quietSince = input.getLongOr("quiet_since", 0L);
		this.setWood(BuiltInRegistries.BLOCK.getOptional(Identifier.parse(input.getStringOr("sapling", "minecraft:oak_sapling"))).orElse(Blocks.OAK_SAPLING));
	}
}
