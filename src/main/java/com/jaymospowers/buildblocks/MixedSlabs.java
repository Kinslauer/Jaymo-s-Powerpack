package com.jaymospowers.buildblocks;

import com.jaymospowers.JaymosPowers;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Mixed slabs: a slab put into the empty half of a different kind of slab makes one {@link MixedSlabBlock} that keeps both,
 * and mining it takes only the half the player looks at. Any {@link SlabBlock}, vanilla or modded, works; the same slab
 * on a slab is still vanilla's double slab. Each half is placed and mined as that slab would be.
 */
public final class MixedSlabs {
	/** Hardness of the placeholder block; every half is mined at its own slab's speed ({@link MixedSlabBlock}). */
	public static final float HARDNESS = 2.0F;

	public static Block BLOCK;
	public static BlockEntityType<MixedSlabEntity> ENTITY;

	private MixedSlabs() {
	}

	public static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("mixed_slab"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key,
			new MixedSlabBlock(BlockBehaviour.Properties.of().strength(HARDNESS).sound(SoundType.STONE).noOcclusion().setId(key)));
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("mixed_slab"),
			new BlockEntityType<>(MixedSlabEntity::new, Set.of(BLOCK)));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> mix(player, level, player.getItemInHand(hand), hit));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
			if (!(entity instanceof MixedSlabEntity slabs) || !state.is(BLOCK)) {
				return true;
			}
			if (level instanceof ServerLevel server) {
				takeHalf(server, player, pos, slabs);
			}
			return false;
		});
	}

	/** Whether the player is looking at the upper half of the block at {@code pos}. */
	public static boolean upperHalf(Player player, BlockPos pos) {
		HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
		double y = hit instanceof BlockHitResult block && block.getBlockPos().equals(pos) ? hit.getLocation().y : player.getEyeY();
		return y - pos.getY() >= 0.5;
	}

	/**
	 * A slab of another kind used on a single slab, on the empty half (the same clicks that would make a double slab):
	 * both sides decide the same way, the server does it.
	 */
	private static InteractionResult mix(Player player, Level level, ItemStack stack, BlockHitResult hit) {
		if (player.isSpectator() || !(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof SlabBlock placed)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState there = level.getBlockState(pos);
		if (!(there.getBlock() instanceof SlabBlock) || there.getBlock() == placed || !there.hasProperty(SlabBlock.TYPE)
			|| !placed.defaultBlockState().hasProperty(SlabBlock.TYPE)) {
			return InteractionResult.PASS;
		}
		SlabType type = there.getValue(SlabBlock.TYPE);
		if (type == SlabType.DOUBLE) {
			return InteractionResult.PASS;
		}
		Direction face = hit.getDirection();
		boolean high = hit.getLocation().y - pos.getY() > 0.5;
		boolean aimed = type == SlabType.BOTTOM ? face == Direction.UP || (high && face.getAxis().isHorizontal())
			: face == Direction.DOWN || (!high && face.getAxis().isHorizontal());
		if (!aimed || !player.mayBuild() || !level.mayInteract(player, pos) || !level.isUnobstructed(BLOCK.defaultBlockState(), pos, CollisionContext.of(player))) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			boolean lowerHeld = type == SlabType.BOTTOM;
			BlockState old = MixedSlabEntity.single(there.getBlock(), type);
			BlockState added = MixedSlabEntity.single(placed, lowerHeld ? SlabType.TOP : SlabType.BOTTOM);
			server.setBlock(pos, BLOCK.defaultBlockState(), Block.UPDATE_ALL);
			if (server.getBlockEntity(pos) instanceof MixedSlabEntity slabs) {
				slabs.set(lowerHeld ? old : added, lowerHeld ? added : old);
			}
			SoundType sound = added.getSoundType();
			server.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			server.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	/** Mines the half the player looks at: it drops as its slab would, and the other slab stays. */
	private static void takeHalf(ServerLevel level, Player player, BlockPos pos, MixedSlabEntity slabs) {
		if (!player.mayBuild() || !level.mayInteract(player, pos)) {
			return;
		}
		boolean upper = upperHalf(player, pos);
		BlockState gone = slabs.half(upper), stays = slabs.half(!upper);
		level.setBlock(pos, stays, Block.UPDATE_ALL);
		level.levelEvent(2001, pos, Block.getId(gone));
		if (!player.isCreative()) {
			ItemStack tool = player.getMainHandItem();
			if (player.hasCorrectToolForDrops(gone)) {
				Block.dropResources(gone, level, pos, null, player, tool);
			}
			tool.mineBlock(level, gone, pos, player);
		}
		level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
	}
}
