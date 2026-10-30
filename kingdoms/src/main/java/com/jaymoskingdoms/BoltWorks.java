package com.jaymoskingdoms;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.389) What a tome's bolt does to the world (Andy: "charged ice tome should freeze water, and all should have fitting
 * interactions"). Any bolt does the small things where it lands: fire lights candles and campfires and melts snow and ice,
 * water and air put fires, candles and campfires out. A charged one ({@link #CHARGED} or more) does more, over a patch that
 * grows with the charge ({@link #radius}):
 * <ul>
 * <li><b>Ice</b> freezes the water it flies over or through (frosted ice that melts again; a full charge leaves real ice),
 * lava where it lands to obsidian (flowing lava to cobblestone), and lays snow.</li>
 * <li><b>Fire</b> sets the block it hits alight; flown into water it dies in a hiss of steam.</li>
 * <li><b>Water</b> douses lava (as ice does) and soaks farmland.</li>
 * <li><b>Earth</b> knocks sand and gravel loose and cracks stone bricks.</li>
 * <li><b>Air</b> swings doors, trapdoors and gates and scatters loose items.</li>
 * <li><b>Lightning</b> scrapes the green off copper and, landing in water, shocks whatever swims near.</li>
 * <li><b>Restoration</b> grows crops and saplings; <b>death</b> withers them, browns grass and kills flowers; <b>blood</b>
 * spatters the ground with stains (Jaymo's Powers' blood trail, where it's loaded).</li>
 * </ul>
 * Blocks change only where the caster may build ({@link #mayChange}).
 */
final class BoltWorks {
	/** A bolt charged this far (0 to 1) does the larger things. */
	static final float CHARGED = 0.3F;
	/** The patch it works on: from this radius at {@link #CHARGED} to {@link #MAX_RADIUS} at a full charge (blocks). */
	static final int MIN_RADIUS = 1, MAX_RADIUS = 3;
	/** How wide an ice bolt freezes the water along its path, at a charge and at a full one (blocks). */
	static final int PATH_FREEZE = 1, PATH_FREEZE_FULL = 2;
	/** How long frosted ice lasts before it starts to thaw (ticks, from and to). */
	static final int THAW_FROM = 60, THAW_TO = 120;
	/** Lightning in water: how far it shocks (blocks, plus this per charge), and how hard. */
	static final double SHOCK_RANGE = 4.0, SHOCK_PER_CHARGE = 3.0;
	static final float SHOCK_DAMAGE = 2.0F, SHOCK_DAMAGE_FULL = 6.0F;
	/** Restoration: the chance each crop or sapling in the patch grows one step. */
	static final float GROW_CHANCE = 0.6F;
	/** Blood: stains spattered (at a charge, plus per full charge) and their size. */
	static final int STAINS = 3, STAINS_PER_CHARGE = 6;
	static final float STAIN_SIZE = 0.8F;
	static final int BLOOD_COLOUR = 0x7A0A0A;
	/** Air: how hard it scatters loose items. */
	static final double GUST = 0.5;

	private BoltWorks() {
	}

	/** The patch's radius for a bolt of {@code charge}, or 0 below {@link #CHARGED}. */
	static int radius(float charge) {
		return charge < CHARGED ? 0 : MIN_RADIUS + Math.round((charge - CHARGED) / (1.0F - CHARGED) * (MAX_RADIUS - MIN_RADIUS));
	}

	static boolean mayChange(ServerLevel level, ServerPlayer caster, BlockPos pos) {
		return caster.mayBuild() && level.mayInteract(caster, pos);
	}

	/**
	 * What a bolt does where it flies through {@code at}, before it moves on; false to stop it there (fire into water).
	 * {@code frozen}: the blocks this bolt has already frozen, so each is done once.
	 */
	static boolean along(ServerLevel level, ServerPlayer caster, String element, Vec3 at, float charge, Set<BlockPos> frozen) {
		BlockPos pos = BlockPos.containing(at);
		switch (element) {
			case "fire" -> {
				if (level.getFluidState(pos).is(FluidTags.WATER)) {
					level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 0.15, 0.1, 0.15, 0.02);
					level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.4F);
					return false;
				}
			}
			case "ice" -> {
				if (charge >= CHARGED) {
					int r = charge >= 1.0F ? PATH_FREEZE_FULL : PATH_FREEZE;
					// over water as well as through it: what lies a little under the path
					for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -r - 1, -r), pos.offset(r, r, r))) {
						if (p.distSqr(pos) <= (r + 0.5) * (r + 0.5) && !frozen.contains(p) && freeze(level, caster, p.immutable(), charge >= 1.0F)) {
							frozen.add(p.immutable());
						}
					}
				}
			}
			case "water", "air" -> {
				if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
					level.removeBlock(pos, false);
					level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
				}
			}
			default -> {
			}
		}
		return true;
	}

	/** What a bolt does where it ends: on {@code wall} (the block it struck, or null), or at {@code target} (whom it struck, or null). */
	static void land(ServerLevel level, ServerPlayer caster, String element, Vec3 at, @Nullable BlockHitResult wall, @Nullable LivingEntity target, float charge) {
		BlockPos centre = wall != null ? wall.getBlockPos() : BlockPos.containing(at);
		int r = radius(charge);
		// the small things, for any bolt, on the block struck and the space before it
		for (BlockPos p : wall != null ? new BlockPos[] {centre, centre.relative(wall.getDirection())} : new BlockPos[] {centre, centre.below()}) {
			small(level, caster, element, p);
		}
		if (element.equals("lightning")) {
			shock(level, caster, at, target, charge);
		}
		if (r == 0) {
			return;
		}
		switch (element) {
			case "fire" -> {
				if (wall != null) {
					BlockPos before = centre.relative(wall.getDirection());
					if (mayChange(level, caster, before) && level.getBlockState(before).isAir() && BaseFireBlock.canBePlacedAt(level, before, wall.getDirection())) {
						level.setBlock(before, BaseFireBlock.getState(level, before), Block.UPDATE_ALL_IMMEDIATE);
					}
				} else if (target != null) {
					BlockPos feet = target.blockPosition();
					if (mayChange(level, caster, feet) && level.getBlockState(feet).isAir() && BaseFireBlock.canBePlacedAt(level, feet, Direction.UP)) {
						level.setBlock(feet, BaseFireBlock.getState(level, feet), Block.UPDATE_ALL_IMMEDIATE);
					}
				}
			}
			case "air" -> {
				Vec3 centreAt = Vec3.atCenterOf(centre);
				for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(centre).inflate(r + 1))) {
					Vec3 away = item.position().subtract(centreAt);
					Vec3 push = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : away.normalize();
					item.setDeltaMovement(item.getDeltaMovement().add(push.x * GUST, 0.25, push.z * GUST));
				}
			}
			case "blood" -> {
				int n = STAINS + Math.round(charge * STAINS_PER_CHARGE);
				for (int i = 0; i < n; i++) {
					double x = at.x + (level.getRandom().nextDouble() * 2.0 - 1.0) * r;
					double z = at.z + (level.getRandom().nextDouble() * 2.0 - 1.0) * r;
					PowersCompat.stain(level, BLOOD_COLOUR, x, at.y + 0.6, z, STAIN_SIZE * (0.7F + level.getRandom().nextFloat() * 0.6F));
				}
			}
			default -> {
			}
		}
		Set<BlockPos> done = new HashSet<>();
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
			if (p.distSqr(centre) > (r + 0.5) * (r + 0.5)) {
				continue;
			}
			BlockPos pos = p.immutable();
			if (done.add(pos) && mayChange(level, caster, pos)) {
				patch(level, caster, element, pos, charge);
			}
		}
	}

	/** Candles, campfires, fire, snow: what any bolt does to the block at {@code pos}. */
	private static void small(ServerLevel level, ServerPlayer caster, String element, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		boolean lightable = (state.is(BlockTags.CANDLES) || state.is(BlockTags.CAMPFIRES)) && state.hasProperty(BlockStateProperties.LIT);
		switch (element) {
			case "fire" -> {
				if (lightable && !state.getValue(BlockStateProperties.LIT)
					&& (!state.hasProperty(BlockStateProperties.WATERLOGGED) || !state.getValue(BlockStateProperties.WATERLOGGED))) {
					level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL_IMMEDIATE);
					level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
				} else if (mayChange(level, caster, pos) && (state.is(Blocks.SNOW) || state.is(Blocks.FROSTED_ICE) || state.is(Blocks.ICE))) {
					melt(level, pos, state);
				}
			}
			case "water", "air", "ice" -> {
				if (lightable && state.getValue(BlockStateProperties.LIT)) {
					level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL_IMMEDIATE);
					level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
				} else if (state.getBlock() instanceof BaseFireBlock) {
					level.removeBlock(pos, false);
					level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
				}
			}
			default -> {
			}
		}
	}

	/** What a charged bolt of {@code element} does to one block of its patch. */
	private static void patch(ServerLevel level, ServerPlayer caster, String element, BlockPos pos, float charge) {
		BlockState state = level.getBlockState(pos);
		switch (element) {
			case "ice" -> {
				if (!freeze(level, caster, pos, charge >= 1.0F) && !douse(level, pos)) {
					// snow on open ground
					BlockPos below = pos.below();
					if (state.isAir() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && Blocks.SNOW.defaultBlockState().canSurvive(level, pos)
						&& level.getRandom().nextFloat() < 0.7F) {
						level.setBlock(pos, Blocks.SNOW.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
			case "fire" -> {
				if (state.is(Blocks.SNOW) || state.is(Blocks.FROSTED_ICE) || state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK)) {
					melt(level, pos, state);
				}
			}
			case "water" -> {
				if (!douse(level, pos) && state.hasProperty(BlockStateProperties.MOISTURE) && state.is(Blocks.FARMLAND)) {
					level.setBlock(pos, state.setValue(BlockStateProperties.MOISTURE, 7), Block.UPDATE_CLIENTS);
				} else if (state.getBlock() instanceof BaseFireBlock) {
					level.removeBlock(pos, false);
				}
			}
			case "earth" -> {
				if (state.getBlock() instanceof FallingBlock && FallingBlock.isFree(level.getBlockState(pos.below()))) {
					FallingBlockEntity.fall(level, pos, state);
				} else if (state.is(Blocks.STONE_BRICKS) && level.getRandom().nextFloat() < 0.5F) {
					level.setBlock(pos, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
					level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
						pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
				}
			}
			case "air" -> {
				if (state.getBlock() instanceof DoorBlock door && state.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
					door.setOpen(caster, level, state, pos, !state.getValue(DoorBlock.OPEN));
				} else if (state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock) {
					boolean open = !state.getValue(BlockStateProperties.OPEN);
					level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
					level.playSound(null, pos, open ? SoundEvents.WOODEN_TRAPDOOR_OPEN : SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 1.0F);
				}
			}
			case "lightning" -> {
				WeatheringCopper.getPrevious(state).ifPresent(cleaner -> {
					level.setBlock(pos, cleaner, Block.UPDATE_ALL);
					level.sendParticles(ParticleTypes.SCRAPE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.1);
				});
			}
			case "restoration" -> {
				if (state.getBlock() instanceof BonemealableBlock crop && !(state.getBlock() instanceof GrassBlock) && !state.is(BlockTags.DIRT)
					&& crop.isValidBonemealTarget(level, pos, state, BonemealSource.INTERACTION) && level.getRandom().nextFloat() < GROW_CHANCE) {
					crop.performBonemeal(level, level.getRandom(), pos, state, BonemealSource.INTERACTION);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.0);
				}
			}
			case "death" -> {
				if (state.is(Blocks.GRASS_BLOCK)) {
					level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
				} else if ((state.is(BlockTags.SMALL_FLOWERS) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN))
					&& Blocks.DEAD_BUSH.defaultBlockState().canSurvive(level, pos)) {
					level.setBlock(pos, Blocks.DEAD_BUSH.defaultBlockState(), Block.UPDATE_ALL);
				} else if (state.hasProperty(BlockStateProperties.AGE_7) && state.getValue(BlockStateProperties.AGE_7) > 0) {
					level.setBlock(pos, state.setValue(BlockStateProperties.AGE_7, state.getValue(BlockStateProperties.AGE_7) - 1), Block.UPDATE_ALL);
				} else {
					return;
				}
				level.sendParticles(Necromancy.VIOLET_MOTE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.0);
			}
			default -> {
			}
		}
	}

	/** Still water at {@code pos} to ice: frosted (it thaws) or, {@code hard}, real ice. */
	private static boolean freeze(ServerLevel level, ServerPlayer caster, BlockPos pos, boolean hard) {
		BlockState state = level.getBlockState(pos);
		FluidState fluid = state.getFluidState();
		if (!state.is(Blocks.WATER) || !fluid.isSource() || !mayChange(level, caster, pos)) {
			return false;
		}
		if (hard) {
			level.setBlock(pos, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
		} else {
			level.setBlock(pos, Blocks.FROSTED_ICE.defaultBlockState(), Block.UPDATE_ALL);
			level.scheduleTick(pos, Blocks.FROSTED_ICE, Mth.nextInt(level.getRandom(), THAW_FROM, THAW_TO));
		}
		if (level.getRandom().nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0.0);
		}
		return true;
	}

	/** Lava at {@code pos} cooled: a source to obsidian, flowing lava to cobblestone. */
	private static boolean douse(ServerLevel level, BlockPos pos) {
		FluidState fluid = level.getFluidState(pos);
		if (!fluid.is(FluidTags.LAVA) || !level.getBlockState(pos).is(Blocks.LAVA)) {
			return false;
		}
		level.setBlock(pos, (fluid.isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState(), Block.UPDATE_ALL);
		level.levelEvent(1501, pos, 0); // the hiss and smoke of lava cooling
		return true;
	}

	/** Snow and ice at {@code pos} melted: snow gone, ice to water. */
	private static void melt(ServerLevel level, BlockPos pos, BlockState state) {
		boolean ice = state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE);
		level.setBlock(pos, ice && !level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES, pos) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
	}

	/** Lightning landing in water (or on someone in it) runs through it to whatever else is in the water near. */
	private static void shock(ServerLevel level, ServerPlayer caster, Vec3 at, @Nullable LivingEntity target, float charge) {
		boolean wet = level.getFluidState(BlockPos.containing(at)).is(FluidTags.WATER) || target != null && target.isInWater();
		if (!wet) {
			return;
		}
		double range = SHOCK_RANGE + charge * SHOCK_PER_CHARGE;
		float damage = SHOCK_DAMAGE + charge * (SHOCK_DAMAGE_FULL - SHOCK_DAMAGE);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(range),
			e -> e != caster && e.isAlive() && e.isInWater() && e.distanceToSqr(at) <= range * range)) {
			e.hurtServer(level, level.damageSources().indirectMagic(caster, caster), damage);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY(0.5), e.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 30, range * 0.3, 0.2, range * 0.3, 0.3);
	}
}
