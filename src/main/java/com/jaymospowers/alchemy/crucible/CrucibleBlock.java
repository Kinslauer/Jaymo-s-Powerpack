package com.jaymospowers.alchemy.crucible;

import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.Phials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The crucible: an iron-rimmed copper pot. Fill it with a water bucket and light a fire under it ({@link
 * com.jaymospowers.tinkering.kiln.Heat}: fire, a lit campfire, lava, magma, a lit forge hearth); after
 * {@link Crucibles#HEAT_TICKS} it bubbles, and whatever is thrown in dissolves into essence ({@link CrucibleEntity}).
 * <ul>
 * <li>Water bucket: fills it; an empty bucket takes the water back (and whatever essence was in it goes as flux).</li>
 * <li>Empty phial: draws up to {@link Crucibles#DRAW} of the essence there is most of. An essence phial pours in.</li>
 * <li>Empty hand: shows what is in it. Sneaking with an empty hand tips the whole pot out (all of it to flux).</li>
 * </ul>
 * Its top is open (a hollow shape), so thrown items fall in.
 */
public class CrucibleBlock extends Block implements EntityBlock {
	private static final VoxelShape OUTER = Block.box(1, 0, 1, 15, 15, 15);
	private static final VoxelShape INNER = Block.box(3, 4, 3, 13, 16, 13);
	private static final VoxelShape SHAPE = Shapes.join(OUTER, INNER, BooleanOp.ONLY_FIRST);
	/** What a click is aimed at, so a click on the open top still lands on the pot. */
	private static final VoxelShape INTERACTION = Block.box(1, 4, 1, 15, 15, 15);

	public CrucibleBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return INTERACTION;
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CrucibleEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != Crucibles.CRUCIBLE_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<CrucibleEntity>) CrucibleEntity::serverTick;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CrucibleEntity pot)) {
			return InteractionResult.PASS;
		}
		if (stack.is(Items.WATER_BUCKET)) {
			if (pot.water()) {
				return InteractionResult.FAIL;
			}
			if (!level.isClientSide()) {
				pot.setWater(true);
				player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
				level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.BUCKET)) {
			if (!pot.water()) {
				return InteractionResult.FAIL;
			}
			if (!level.isClientSide()) {
				pot.tipOut((ServerLevel) level);
				player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
				level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Alchemy.PHIAL)) {
			if (!level.isClientSide()) {
				Essence main = pot.essences().main();
				if (main == null) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.crucible.nothing_to_draw"));
				} else {
					int drawn = pot.draw(main, Crucibles.DRAW);
					give(player, hand, stack, Phials.of(main, drawn));
					level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Alchemy.ESSENCE_PHIAL)) {
			if (!level.isClientSide()) {
				EssenceList in = Phials.contents(stack);
				Essence e = in.main();
				if (e == null) {
					return InteractionResult.FAIL;
				}
				if (!pot.water()) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.crucible.dry"));
				} else {
					int room = Crucibles.CAPACITY - pot.essences().total();
					int poured = Math.min(in.get(e), room);
					if (poured <= 0) {
						player.sendOverlayMessage(Component.translatable("message.jaymospowers.crucible.full"));
					} else {
						pot.add((ServerLevel) level, EssenceList.of(e, poured));
						give(player, hand, stack, Phials.of(e, in.get(e) - poured));
						level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
					}
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** Swaps one used-up phial for {@code result}; the rest of a stack stays, and the result goes to the inventory. */
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
		if (!(level.getBlockEntity(pos) instanceof CrucibleEntity pot)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			if (player.isShiftKeyDown()) {
				if (pot.water() || !pot.essences().isEmpty()) {
					pot.tipOut((ServerLevel) level);
				}
			} else if (!pot.water()) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.crucible.dry"));
			} else if (pot.essences().isEmpty()) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.crucible.clear"));
			} else {
				player.sendOverlayMessage(describe(player, pot.essences()));
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** "Fire 30, Metal 12 ...": only the essences the player has learned of; the rest stay "something unknown". */
	static Component describe(Player player, EssenceList essences) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		int unknown = 0;
		boolean first = true;
		for (var en : essences.amounts().entrySet()) {
			if (!Knowledge.hasSeen(player, en.getKey())) {
				unknown += en.getValue();
				continue;
			}
			out.append(Component.literal(first ? "" : ", ")).append(Component.translatable(en.getKey().key())).append(" " + en.getValue());
			first = false;
		}
		if (unknown > 0) {
			out.append(Component.literal(first ? "" : ", ")).append(Component.translatable("message.jaymospowers.crucible.unknown", unknown));
		}
		return out;
	}

	/** Steam over a hot pot, tinted by what is in it. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!(level.getBlockEntity(pos) instanceof CrucibleEntity pot) || !pot.hot()) {
			return;
		}
		double x = pos.getX() + 0.5, z = pos.getZ() + 0.5, top = pos.getY() + CrucibleEntity.surface(pot.fill());
		int colour = 0xFF000000 | Crucibles.mix(pot.essences());
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.BUBBLE_POP, x + (random.nextDouble() - 0.5) * 0.55, top + 0.02, z + (random.nextDouble() - 0.5) * 0.55,
				0.0, 0.0, 0.0);
		}
		if (random.nextInt(5) == 0) {
			level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, colour), x + (random.nextDouble() - 0.5) * 0.5,
				top + 0.1, z + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.0, 0.0);
		}
	}
}
