package com.jaymospowers.power.gravity;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Mortality;
import com.jaymospowers.elemental.ElementalCorruption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (Jung) Gravity's part in Elemental Corruption: weight's answer. The reckoning lifts every creature and loose thing
 * round the killer about {@link #LIFT_BLOCKS} blocks into the air, and the ground erupts in a ring {@link #RING_RADIUS}
 * blocks out ({@link #RING_BLASTS} blasts). Through the wrath the loose ground round them (sand, gravel, dirt,
 * cobblestone) won't lie still: now and then a block cracks, drops loose as a falling block, or rises a few blocks and
 * slams back down, leaving a little crater; and the creatures near them are slowly drawn in.
 */
final class GravityWrath implements ElementalCorruption.Wrath {
	// ---- tuning ----
	/** The reckoning's lift: creatures and loose things this near (blocks), about this high, over this many ticks. */
	static final double LIFT_RADIUS = 12.0;
	static final int LIFT_BLOCKS = 10, LIFT_TICKS = 40;
	/** The ring of blasts: this many, this far out (blocks), each this strong (TNT is 4). */
	static final int RING_BLASTS = 6;
	static final double RING_RADIUS = 10.0;
	static final float BLAST = 3.0F;
	/** The restless ground: a loose block within this far (blocks) is stirred every this many ticks. */
	static final int GROUND_RADIUS = 10, GROUND_EVERY = 10;
	/** A rising block's upward kick (blocks a tick; about four or five blocks up), and a dropped one's. */
	static final double RISE = 0.75, DROP = 0.3;
	/** The pull: creatures this near (blocks) are drawn in this fast (blocks a tick), every this many ticks. */
	static final double PULL_RADIUS = 14.0, PULL = 0.08;
	static final int PULL_EVERY = 5;
	/** The weight bending the air round the holder. */
	static final int COLOR = 0x6A3FB8;

	/** Blocks lifted by the wrath, still in the air: when one comes down it leaves a crater. */
	private static final List<Lifted> LIFTED = new ArrayList<>();

	private record Lifted(FallingBlockEntity block, ServerLevel level) {
	}

	@Override
	public void erupt(ServerPlayer player, ServerLevel level) {
		Vec3 at = player.position();
		// (levitation lifts a twentieth of a block a tick for each level: so many levels, for so long, is about LIFT_BLOCKS)
		int amplifier = Math.max(0, Math.round(LIFT_BLOCKS * 20.0F / LIFT_TICKS) - 1);
		for (Entity entity : level.getEntities(player, new AABB(at, at).inflate(LIFT_RADIUS), e -> e.isAlive() && e.distanceToSqr(at) <= LIFT_RADIUS * LIFT_RADIUS)) {
			if (entity instanceof Player) {
				continue;
			}
			if (entity instanceof LivingEntity living) {
				if (!Mortality.boss(living)) {
					living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LIFT_TICKS, amplifier));
				}
			} else if (entity instanceof ItemEntity || entity instanceof FallingBlockEntity) {
				entity.setDeltaMovement(entity.getDeltaMovement().add(0.0, 1.1, 0.0));
				entity.syncVelocity = true;
			}
		}
		for (int i = 0; i < RING_BLASTS; i++) {
			double angle = Mth.TWO_PI * i / RING_BLASTS;
			int x = Mth.floor(at.x + Math.cos(angle) * RING_RADIUS), z = Mth.floor(at.z + Math.sin(angle) * RING_RADIUS);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			// (nobody's blast: the gods', not the holder's, so their wrath doesn't double it)
			level.explode(null, x + 0.5, y, z + 0.5, BLAST, Level.ExplosionInteraction.MOB);
		}
		MagicFx.ring(level, at, (float) RING_RADIUS, COLOR);
	}

	@Override
	public void tick(ServerPlayer player, ServerLevel level, long age) {
		land();
		if (age % PULL_EVERY == 0) {
			for (LivingEntity living : ElementalCorruption.near(player, PULL_RADIUS)) {
				if (Mortality.boss(living)) {
					continue;
				}
				Vec3 in = player.position().subtract(living.position()).multiply(1.0, 0.0, 1.0);
				if (in.lengthSqr() > 1.0) {
					living.push(in.normalize().scale(PULL));
					living.syncVelocity = true;
				}
			}
		}
		if (age % GROUND_EVERY == 0) {
			stir(player, level);
		}
	}

	@Override
	public void aura(ServerPlayer player, ServerLevel level) {
		level.sendParticles(MagicFx.mote(COLOR, 1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.6, 0.8, 0.6, 0.0);
	}

	@Override
	public void disperse(ServerPlayer player, ServerLevel level) {
		LIFTED.removeIf(lifted -> lifted.level() == level);
	}

	/** A loose block near them: cracks where it lies, drops loose, or rises and slams back down. */
	private static void stir(ServerPlayer player, ServerLevel level) {
		RandomSource random = level.getRandom();
		int x = Mth.floor(player.getX()) + random.nextInt(GROUND_RADIUS * 2 + 1) - GROUND_RADIUS;
		int z = Mth.floor(player.getZ()) + random.nextInt(GROUND_RADIUS * 2 + 1) - GROUND_RADIUS;
		BlockPos top = BlockPos.containing(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		BlockState state = level.getBlockState(top);
		if (!loose(state) || Math.abs(top.getY() - player.getY()) > GROUND_RADIUS) {
			return;
		}
		int how = random.nextInt(3);
		if (how == 0) {
			// it cracks
			level.levelEvent(2001, top, Block.getId(state));
			level.playSound(null, top, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.6F, 0.6F);
		} else if (how == 1) {
			FallingBlockEntity falling = FallingBlockEntity.fall(level, top, state);
			falling.setDeltaMovement(0.0, DROP, 0.0);
		} else {
			FallingBlockEntity falling = FallingBlockEntity.fall(level, top, state);
			falling.setDeltaMovement((random.nextDouble() - 0.5) * 0.2, RISE, (random.nextDouble() - 0.5) * 0.2);
			LIFTED.add(new Lifted(falling, level));
		}
	}

	/** Lifted blocks that have come down: each leaves a little crater where it landed. */
	private static void land() {
		for (Iterator<Lifted> it = LIFTED.iterator(); it.hasNext();) {
			Lifted lifted = it.next();
			FallingBlockEntity block = lifted.block();
			if (!block.isRemoved() && !block.onGround() && block.tickCount < 200) {
				continue;
			}
			it.remove();
			if (block.tickCount < 4) {
				continue;
			}
			crater(lifted.level(), block.blockPosition().below());
		}
	}

	/** The slam: the loose ground at and round {@code at} is knocked away (up to five blocks). */
	private static void crater(ServerLevel level, BlockPos at) {
		level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.5F, 1.4F);
		for (BlockPos pos : new BlockPos[] {at, at.north(), at.south(), at.east(), at.west()}) {
			BlockState state = level.getBlockState(pos);
			// (the block it hit, and the loose blocks round it that lie open to the sky)
			if (loose(state) && (pos.equals(at) || level.getBlockState(pos.above()).isAir())) {
				level.levelEvent(2001, pos, Block.getId(state));
				level.removeBlock(pos, false);
			}
		}
	}

	/** Sand, gravel, dirt or cobblestone (and their kin), with nothing kept in it. */
	private static boolean loose(BlockState state) {
		return !state.hasBlockEntity() && (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.DIRT)
			|| state.is(Blocks.COARSE_DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE));
	}
}
