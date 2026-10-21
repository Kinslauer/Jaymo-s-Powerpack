package com.jaymoskitchen.mill.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A windmill or waterwheel, folded up: used on the end of a level shaft, it's set up there, turning across the
 * shaft. It needs its whole sweep open.
 */
public class RotorItem extends Item {
	public final Rotor rotor;

	public RotorItem(Rotor rotor, Item.Properties properties) {
		super(properties);
		this.rotor = rotor;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos shaft = context.getClickedPos();
		Direction face = context.getClickedFace();
		BlockState state = level.getBlockState(shaft);
		Player player = context.getPlayer();
		if (!(state.getBlock() instanceof ShaftBlock) || face.getAxis() != state.getValue(ShaftBlock.AXIS) || !face.getAxis().isHorizontal()) {
			if (player != null && !level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.power.on_shaft"));
			}
			return InteractionResult.FAIL;
		}
		BlockPos hub = shaft.relative(face);
		Direction facing = face.getOpposite();
		if (!level.getBlockState(hub).canBeReplaced() || !rotor.sweepClear(level, hub, facing)) {
			if (player != null && !level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.power.no_room", rotor.radius * 2 + 1));
			}
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide()) {
			BlockState placed = (rotor == Rotor.WINDMILL ? Power.WINDMILL : Power.WATERWHEEL).defaultBlockState().setValue(RotorBlock.FACING, facing);
			level.setBlock(hub, placed, Block.UPDATE_ALL);
			level.gameEvent(player, GameEvent.BLOCK_PLACE, hub);
			level.playSound(null, hub, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
			context.getItemInHand().consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}
}
