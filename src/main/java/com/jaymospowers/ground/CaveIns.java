package com.jaymospowers.ground;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cave-ins (Andy's TFC Hardrock picks). Mining raw stone under a roof now and then ({@link #CHANCE}) shakes the roof: dust
 * sifts down for {@link #WARNING} ticks, then a patch of it ({@link #RADIUS} round the mined block, up to {@link #MOST}
 * blocks) comes down as falling cobblestone. A support beam ({@link SupportBeamBlock}) within {@link #HELD} blocks holds the
 * roof, and one set up during the warning still saves it. Rare to start, as Andy asked for a gentle start.
 */
public final class CaveIns {
	/** The chance a mined stone block shakes the roof, the ticks of warning, how far the fall spreads and its most blocks. */
	static final float CHANCE = 0.01F, FALL_SHARE = 0.6F;
	static final int WARNING = 40, RADIUS = 2, MOST = 8, ROOF_REACH = 4;
	/** How far a support beam holds the roof (blocks, each way). */
	static final int HELD = 4;
	/** Damage per block fallen and the most from one falling block. */
	static final float HURT = 2.0F;
	static final int HURT_MAX = 12;

	private record Shaking(ServerLevel level, BlockPos pos, long at) {
	}

	private static final List<Shaking> SHAKING = new ArrayList<>();

	private CaveIns() {
	}

	static void init() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel server) || player.isCreative() || !state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| server.canSeeSky(pos) || server.getRandom().nextFloat() >= CHANCE || held(server, pos)) {
				return;
			}
			SHAKING.add(new Shaking(server, pos.immutable(), server.getGameTime() + WARNING));
			server.playSound(null, pos, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.2F, 0.5F);
		});
		ServerTickEvents.END_SERVER_TICK.register(CaveIns::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHAKING.clear());
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Shaking> it = SHAKING.iterator(); it.hasNext(); ) {
			Shaking s = it.next();
			if (!s.level.isLoaded(s.pos)) {
				it.remove();
				continue;
			}
			long now = s.level.getGameTime();
			if (now < s.at) {
				if (now % 4 == 0) {
					s.level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE.defaultBlockState()),
						s.pos.getX() + 0.5, s.pos.getY() + 1.9, s.pos.getZ() + 0.5, 6, RADIUS, 0.1, RADIUS, 0.0);
				}
				continue;
			}
			it.remove();
			if (!held(s.level, s.pos)) {
				collapse(s.level, s.pos);
			}
		}
	}

	/** A support beam stands near enough to hold the roof over {@code pos}. */
	static boolean held(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-HELD, -HELD, -HELD), pos.offset(HELD, HELD, HELD))) {
			if (level.getBlockState(p).getBlock() instanceof SupportBeamBlock) {
				return true;
			}
		}
		return false;
	}

	private static void collapse(ServerLevel level, BlockPos pos) {
		int fell = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -RADIUS; dx <= RADIUS && fell < MOST; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS && fell < MOST; dz++) {
				if (dx * dx + dz * dz > RADIUS * RADIUS + 1 || level.getRandom().nextFloat() >= FALL_SHARE) {
					continue;
				}
				// the first solid block over open air in this column
				for (int dy = 1; dy <= ROOF_REACH; dy++) {
					p.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					BlockState roof = level.getBlockState(p);
					if (roof.isAir()) {
						continue;
					}
					if (roof.is(BlockTags.BASE_STONE_OVERWORLD) && level.getBlockState(p.below()).isAir()) {
						BlockState rubble = roof.is(Blocks.DEEPSLATE) || roof.is(Blocks.TUFF) ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
							: Blocks.COBBLESTONE.defaultBlockState();
						level.setBlock(p, rubble, 3);
						FallingBlockEntity falling = FallingBlockEntity.fall(level, p.immutable(), rubble);
						falling.setHurtsEntities(HURT, HURT_MAX);
						fell++;
					}
					break;
				}
			}
		}
		if (fell > 0) {
			level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.5F, 0.6F);
		}
	}
}
