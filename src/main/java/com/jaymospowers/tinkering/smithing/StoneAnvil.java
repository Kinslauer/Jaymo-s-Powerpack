package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.kiln.Kiln;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * (Smithing) The Stone Anvil: sneak-hammer a stone block with a smithing hammer. It is a Smithing Anvil of tier
 * {@link SmithingAnvilBlock#STONE_TIER}: heads hammered on it are never better than {@link #CAP}, steel-class metals
 * won't work on it ({@link SmithTiers}), and after {@link #MAX_USES} finished forgings it crumbles to cobblestone (once
 * the last piece has been taken off it).
 */
public final class StoneAnvil {
	/** Finished forgings a stone anvil survives. */
	public static final int MAX_USES = 64;
	/** The best grade a smith can reach on it. */
	public static final Grade CAP = Grade.WELL;
	/** Ticks between looks at anvils waiting to crumble. */
	public static final int CRUMBLE_CHECK_TICKS = 10;

	public static Block STONE_ANVIL;

	private record Spot(ServerLevel level, BlockPos pos) {
	}

	private static final List<Spot> CRUMBLING = new ArrayList<>();

	private StoneAnvil() {
	}

	/** Registers the block and its item; called from {@code Smithing.register()} ahead of the anvil's block entity type. */
	static Block create() {
		STONE_ANVIL = Kiln.blockWithTooltip("stone_anvil", p -> new SmithingAnvilBlock(p, SmithingAnvilBlock.STONE_TIER), BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE).strength(3.0F, 6.0F).sound(SoundType.STONE).pushReaction(PushReaction.IMMOVEABLE));
		return STONE_ANVIL;
	}

	static void register() {
		SmithingHammerItem.CONVERSIONS.add(state -> state.is(BlockTags.BASE_STONE_OVERWORLD)
			? STONE_ANVIL.defaultBlockState().setValue(SmithingAnvilBlock.FACING, net.minecraft.core.Direction.NORTH) : null);
		Forging.CAPS.add((smith, anvil, option) -> anvil.tier() == SmithingAnvilBlock.STONE_TIER ? CAP : null);
		Forging.CHECKS.add((smith, anvil, option) -> {
			if (anvil.tier() == SmithingAnvilBlock.STONE_TIER && anvil.uses() >= MAX_USES) {
				crumbleSoon(anvil);
				return Component.translatable("message.jaymospowers.smithing.stone_anvil_worn");
			}
			return null;
		});
		Forging.FINISHES.add((smith, anvil, option, result) -> {
			if (anvil.tier() == SmithingAnvilBlock.STONE_TIER && anvil.addUse() >= MAX_USES) {
				crumbleSoon(anvil);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!CRUMBLING.isEmpty() && server.getTickCount() % CRUMBLE_CHECK_TICKS == 0) {
				CRUMBLING.removeIf(StoneAnvil::crumble);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(Kiln.tab()).register(output -> output.accept(STONE_ANVIL));
	}

	private static void crumbleSoon(SmithingAnvilEntity anvil) {
		if (anvil.getLevel() instanceof ServerLevel level) {
			Spot spot = new Spot(level, anvil.getBlockPos());
			if (!CRUMBLING.contains(spot)) {
				CRUMBLING.add(spot);
			}
		}
	}

	/** Turns a spent stone anvil to cobblestone once nothing is left on it; true when this spot is done with. */
	private static boolean crumble(Spot spot) {
		ServerLevel level = spot.level();
		BlockPos pos = spot.pos();
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof SmithingAnvilBlock block) || block.tier != SmithingAnvilBlock.STONE_TIER
			|| !(level.getBlockEntity(pos) instanceof SmithingAnvilEntity anvil)) {
			return true;
		}
		if (!anvil.work().isEmpty() || !anvil.result().isEmpty()) {
			return false;
		}
		level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 20, 0.4, 0.3,
			0.4, 0.1);
		return true;
	}
}
