package com.jaymospowers.core.brawl;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Debris;
import com.jaymospowers.power.zfighter.ZFighterKingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Fights break soft blocks only (NPC fights patch 3, row F12): a body thrown by a finisher plows through the blocks of
 * {@code #jaymospowers:brawl_breakable} (leaves, glass and panes, dirt, grass, sand, gravel, snow, ice, flowers, short plants)
 * and a slam breaks them under it. Never stone, wood or anything else; never on a kingdom's or a settlement's land (read through
 * Kingdoms' {@code Factions} by {@link ZFighterKingdoms#protectedLand}); never with {@code mobGriefing} off.
 */
public final class SoftBreak {
	public static final TagKey<Block> BREAKABLE = TagKey.create(Registries.BLOCK, JaymosPowers.id("brawl_breakable"));
	/** Within this far (blocks) of a settlement's seat, or on any claimed land, nothing breaks. */
	static final double SETTLEMENT_RADIUS = 64.0;
	/** At most this many blocks break in one call. */
	static final int MOST = 24;

	private SoftBreak() {
	}

	/** Whether this block may be broken by a fight: soft (the tag) and not a block holding anything. */
	public static boolean soft(BlockState state) {
		return state.is(BREAKABLE) && !state.hasBlockEntity();
	}

	/**
	 * Breaks the soft blocks inside {@code box} (a body's reach, or the ground under a slam): leaves and glass drop nothing,
	 * the rest drop as they normally do. Returns how many broke.
	 */
	public static int plow(ServerLevel level, AABB box, @Nullable Entity by) {
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return 0;
		}
		Boolean allowed = null;
		int broken = 0;
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			BlockState state = level.getBlockState(pos);
			if (!soft(state)) {
				continue;
			}
			if (allowed == null) {
				allowed = !ZFighterKingdoms.protectedLand(level, pos, SETTLEMENT_RADIUS);
			}
			if (!allowed) {
				return broken;
			}
			boolean drops = !(state.is(BlockTags.LEAVES) || state.getSoundType() == SoundType.GLASS);
			BlockPos at = pos.immutable();
			Debris.puff(level, at, state, 0.6F);
			level.destroyBlock(at, drops, by);
			if (++broken >= MOST) {
				break;
			}
		}
		return broken;
	}
}
