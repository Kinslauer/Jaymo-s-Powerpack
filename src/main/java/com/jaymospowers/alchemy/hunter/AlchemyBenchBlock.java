package com.jaymospowers.alchemy.hunter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The alchemy bench (Hunter's Road, patch 6, "D6"): a low oak bench with a copper rack and a glass rail. Use it with strong
 * spirit in the pack and everything that needs refilling is refilled ({@link Refills#refillAll}): spent phials and casings,
 * and the doses of a hunter's flask.
 */
public class AlchemyBenchBlock extends Block {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 10, 0, 16, 14, 16), Block.box(1, 0, 1, 4, 10, 4), Block.box(12, 0, 1, 15, 10, 4),
		Block.box(1, 0, 12, 4, 10, 15), Block.box(12, 0, 12, 15, 10, 15), Block.box(2, 14, 10, 14, 15, 14));

	public static Block BENCH;

	public AlchemyBenchBlock(Block.Properties properties) {
		super(properties);
	}

	static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("alchemy_bench"));
		BENCH = Registry.register(BuiltInRegistries.BLOCK, key, new AlchemyBenchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion().setId(key)));
		Alchemy.item("alchemy_bench", p -> new BlockItem(BENCH, p.useBlockDescriptionPrefix()));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return bench(level, player);
	}

	@Override
	protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		return bench(level, player);
	}

	private static InteractionResult bench(Level level, Player player) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer server) {
			int n = Refills.refillAll(server);
			server.sendOverlayMessage(Component.translatable(n > 0 ? "message.jaymospowers.bench.refilled" : "message.jaymospowers.bench.nothing", n));
		}
		return InteractionResult.SUCCESS;
	}
}
