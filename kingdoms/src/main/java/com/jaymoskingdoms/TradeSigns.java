package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * Trade signs (a medieval deco pick): the town builders set a {@link TradeSignBlock} into the wall over the door of each
 * workplace, with the mark of its trade. Done at the one place every building is registered ({@link Blueprint#register}),
 * so the realm's own designs and the borrowed ones (CTOV, Dungeons and Taverns) all wear one. A building with no door,
 * or no wall over it, goes without. The sign is made of wood, so a builder crew pays for it as for any plank.
 */
public final class TradeSigns {
	/** Which building (by its id) hangs which mark, in a realm's towns and villages. */
	private static final Map<String, TradeSignBlock.Mark> MARKS = Map.ofEntries(
		Map.entry("smithy", TradeSignBlock.Mark.ANVIL), Map.entry("mason", TradeSignBlock.Mark.CHISEL),
		Map.entry("cookhouse", TradeSignBlock.Mark.LOAF), Map.entry("tavern", TradeSignBlock.Mark.MUG),
		Map.entry("infirmary", TradeSignBlock.Mark.NEEDLE), Map.entry("market", TradeSignBlock.Mark.SCALES),
		Map.entry("butcher", TradeSignBlock.Mark.CLEAVER), Map.entry("fishery", TradeSignBlock.Mark.FISH),
		Map.entry("stable", TradeSignBlock.Mark.HORSESHOE), Map.entry("hunting_lodge", TradeSignBlock.Mark.BOW),
		Map.entry("training_hall", TradeSignBlock.Mark.SWORD), Map.entry("barracks", TradeSignBlock.Mark.SWORD));
	/** How far above a door's foot the sign goes: over its top, in the wall's own line. */
	private static final int OVER_DOOR = 2;

	public static Block SIGN;

	private TradeSigns() {
	}

	public static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("trade_sign"));
		SIGN = Registry.register(BuiltInRegistries.BLOCK, key, new TradeSignBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.instrument(NoteBlockInstrument.BASS).strength(1.5F).sound(SoundType.WOOD).ignitedByLava().noLootTable().setId(key)));
	}

	/** {@code entry}, drawn with its trade's sign over its door if it is a workplace of a town or village. */
	static Blueprint.Entry hung(FactionType type, Blueprint.Entry entry) {
		TradeSignBlock.Mark mark = MARKS.get(entry.id());
		if (mark == null || type != FactionType.KINGDOM && type != FactionType.FIEFDOM && type != FactionType.VILLAGE) {
			return entry;
		}
		return new Blueprint.Entry(entry.id(), (level, faction, variant) -> hang(entry.maker().make(level, faction, variant), mark), entry.max(), entry.minPopulation());
	}

	/** {@code plan} with the sign of {@code mark} in the wall over its first door (as it is, if there is no such wall). */
	static Blueprint hang(Blueprint plan, TradeSignBlock.Mark mark) {
		for (Blueprint.Placement door : plan.blocks()) {
			BlockState state = door.state();
			if (!(state.getBlock() instanceof DoorBlock) || state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
				continue;
			}
			BlockPos over = door.offset().above(OVER_DOOR);
			List<Blueprint.Placement> blocks = new ArrayList<>(plan.blocks());
			for (int i = 0; i < blocks.size(); i++) {
				BlockState wall = blocks.get(i).state();
				if (blocks.get(i).offset().equals(over) && Block.isShapeFullBlock(wall.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
					// (a door faces outward in the plans, toward the street)
					blocks.set(i, new Blueprint.Placement(over, SIGN.defaultBlockState().setValue(TradeSignBlock.FACING, state.getValue(BlockStateProperties.HORIZONTAL_FACING))
						.setValue(TradeSignBlock.MARK, mark)));
					return new Blueprint(plan.id(), plan.width(), plan.depth(), plan.height(), List.copyOf(blocks));
				}
			}
			return plan;
		}
		return plan;
	}
}
