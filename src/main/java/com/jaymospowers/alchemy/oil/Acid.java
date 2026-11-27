package com.jaymospowers.alchemy.oil;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Acid on the ground (Hunter's Road, patch 7, "T-C2"; the idea is Toxony's, written clean-room): a thrown armour-eating acid oil
 * ({@link OilKind#ACID}) that lands near ore <b>dissolves the rock round it and leaves extra ore</b>: every ore within
 * {@link #REACH} blocks of where it fell gives up its host rock (the stone beside it turns to nothing) and comes loose, dropping
 * what it would drop and as much again ({@link #EXTRA}); at most {@link #MOST_ORES} ores a splash. The <b>strong acid</b> (the
 * top grade, {@link #STRONG_GRADE}) also eats some blocks away outright: {@link #EATEN_SHARE} of the soft ones round it
 * (nothing harder than {@link #SOFT}, nothing in the {@link #PROOF} tag, nothing that holds things).
 */
public final class Acid {
	// ---- tuning ----
	/** Blocks (from where it lands) ore is looked for, by grade 1 to 3. */
	public static final int[] REACH = {2, 3, 4};
	/** The most ores one splash works on; the extra copies of what each drops. */
	public static final int MOST_ORES = 10, EXTRA = 1;
	/** The grade at and above which acid is strong, the share of soft blocks it eats within {@link #EAT_REACH}, and how hard "soft" is. */
	public static final int STRONG_GRADE = 3;
	public static final float EATEN_SHARE = 0.4F, SOFT = 3.0F;
	public static final int EAT_REACH = 2;

	/** Ore blocks (Fabric's {@code c:ores}, and vanilla's own ore tags besides). */
	public static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));
	/** The rock an acid dissolves round an ore (stone, deepslate and their kin, netherrack). */
	private static final List<TagKey<Block>> ROCK = List.of(BlockTags.BASE_STONE_OVERWORLD, BlockTags.BASE_STONE_NETHER);
	/** What acid never eats (a data tag). */
	public static final TagKey<Block> PROOF = TagKey.create(Registries.BLOCK, JaymosPowers.id("acid_proof"));

	private Acid() {
	}

	/** Whether {@code state} is an ore. */
	public static boolean ore(BlockState state) {
		return state.is(ORES) || state.is(BlockTags.ORES);
	}

	/** Acid of grade {@code tier} falls at {@code at}. */
	public static void splash(ServerLevel level, BlockPos at, int tier, @Nullable Entity owner) {
		int t = Math.max(1, Math.min(3, tier));
		int reach = REACH[t - 1];
		List<BlockPos> ores = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-reach, -reach, -reach), at.offset(reach, reach, reach))) {
			if (p.distSqr(at) <= (double) reach * reach && ores.size() < MOST_ORES && ore(level.getBlockState(p))) {
				ores.add(p.immutable());
			}
		}
		for (BlockPos ore : ores) {
			BlockState state = level.getBlockState(ore);
			for (Direction d : Direction.values()) {
				BlockPos side = ore.relative(d);
				BlockState rock = level.getBlockState(side);
				if (ROCK.stream().anyMatch(rock::is) && !rock.is(PROOF)) {
					level.removeBlock(side, false);
				}
			}
			List<ItemStack> drops = Block.getDrops(state, level, ore, null);
			level.removeBlock(ore, false);
			for (ItemStack drop : drops) {
				Block.popResource(level, ore, drop);
				for (int i = 0; i < EXTRA; i++) {
					Block.popResource(level, ore, drop.copy());
				}
			}
			level.sendParticles(ParticleTypes.SMOKE, ore.getX() + 0.5, ore.getY() + 0.5, ore.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
		}
		if (t >= STRONG_GRADE) {
			eat(level, at);
		}
		level.playSound(null, at, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.6F);
		level.sendParticles(ParticleTypes.ITEM_SLIME, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 14, 0.8, 0.3, 0.8, 0.05);
	}

	/** The strong acid eats some soft blocks round {@code at} away. */
	private static void eat(ServerLevel level, BlockPos at) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-EAT_REACH, -EAT_REACH, -EAT_REACH), at.offset(EAT_REACH, EAT_REACH, EAT_REACH))) {
			if (p.distSqr(at) > EAT_REACH * EAT_REACH || level.getRandom().nextFloat() > EATEN_SHARE) {
				continue;
			}
			BlockState state = level.getBlockState(p);
			float hardness = state.getDestroySpeed(level, p);
			if (state.isAir() || hardness < 0.0F || hardness > SOFT || state.is(PROOF) || state.hasBlockEntity() || ore(state)) {
				continue;
			}
			level.removeBlock(p, false);
			level.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.01);
		}
	}
}
