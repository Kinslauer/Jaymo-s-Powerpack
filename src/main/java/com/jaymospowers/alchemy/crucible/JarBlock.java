package com.jaymospowers.alchemy.crucible;

import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.Phials;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The essence jar: glass with a wooden lid, holding {@link Crucibles#JAR_SIZE} of one essence ({@link JarEntity}).
 * <ul>
 * <li>An essence phial pours in (the same essence, or any when the jar is empty).</li>
 * <li>An empty phial draws {@link Crucibles#DRAW} out.</li>
 * <li>An empty hand reads what is inside (sneaking or not).</li>
 * </ul>
 * Broken, it keeps its contents on the dropped item ({@link Alchemy#ESSENCES}); an alembic fills it from a crucible.
 */
public class JarBlock extends Block implements EntityBlock {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 12, 13), Block.box(4, 12, 4, 12, 14, 12));

	public JarBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new JarEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof JarEntity jar)) {
			return InteractionResult.PASS;
		}
		if (stack.is(Alchemy.ESSENCE_PHIAL)) {
			EssenceList in = Phials.contents(stack);
			Essence e = in.main();
			if (e == null) {
				return InteractionResult.FAIL;
			}
			if (!level.isClientSide()) {
				int poured = jar.add(e, in.get(e));
				if (poured <= 0) {
					player.sendOverlayMessage(Component.translatable(jar.essence() != null && jar.essence() != e ? "message.jaymospowers.jar.other" : "message.jaymospowers.jar.full"));
				} else {
					give(player, hand, stack, Phials.of(e, in.get(e) - poured));
					level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Alchemy.PHIAL)) {
			if (!level.isClientSide()) {
				Essence e = jar.essence();
				if (e == null) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.jar.empty"));
				} else {
					int drawn = jar.take(Crucibles.DRAW);
					give(player, hand, stack, Phials.of(e, drawn));
					level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	private static void give(Player player, InteractionHand hand, ItemStack used, ItemStack result) {
		if (used.getCount() == 1) {
			player.setItemInHand(hand, result);
		} else {
			used.shrink(1);
			player.getInventory().placeItemBackInInventory(result, Prediction.SERVER_ONLY);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof JarEntity jar)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			Essence e = jar.essence();
			if (e == null) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.jar.empty"));
			} else {
				Component name = Knowledge.hasSeen(player, e) ? Component.translatable(e.key()) : Component.translatable("message.jaymospowers.jar.unknown");
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.jar.holds", name, jar.amount(), Crucibles.JAR_SIZE));
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** A jar item: carries what was in the jar, and says so. */
	public static class JarItem extends BlockItem {
		public JarItem(Block block, Item.Properties properties) {
			super(block, properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
			EssenceList held = Phials.contents(stack);
			Essence e = held.main();
			if (e != null) {
				builder.accept(Component.translatable("tooltip.jaymospowers.essence_jar.holds", Component.translatable(e.key()), held.get(e),
					Crucibles.JAR_SIZE).withStyle(ChatFormatting.GRAY));
			}
		}
	}
}
