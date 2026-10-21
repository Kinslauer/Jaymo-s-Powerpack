package com.jaymospowers.aether.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import com.jaymospowers.aether.AetherFlora;

/**
 * A moa egg (blue, white or black), laid by a moa. Set on a block of woven skyroot sticks it becomes a moa egg block
 * ({@link MoaEggBlock}) that hatches in about 12000 ticks. Used anywhere else it hatches a chick of that kind on the spot,
 * as it always did. Either way the chick is one a player raised: it can be saddled once grown (feed it to hurry that).
 */
public class MoaEggItem extends Item {
	private final Moa.Kind kind;

	public MoaEggItem(Moa.Kind kind, Item.Properties properties) {
		super(properties);
		this.kind = kind;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos clicked = context.getClickedPos();
		if (context.getClickedFace() == Direction.UP && level.getBlockState(clicked).is(AetherFlora.WOVEN_SKYROOT_STICKS)
			&& level.getBlockState(clicked.above()).canBeReplaced()) {
			if (level instanceof ServerLevel server) {
				BlockState egg = AetherAnimals.MOA_EGG.stateFor(this.kind, true, level.getBlockState(clicked));
				server.setBlockAndUpdate(clicked.above(), egg);
				server.playSound(null, clicked, SoundEvents.TURTLE_LAY_EGG, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
				if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
					context.getItemInHand().shrink(1);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (level instanceof ServerLevel server) {
			Direction face = context.getClickedFace();
			BlockPos at = context.getClickedPos().relative(face);
			Moa chick = AetherMobs.MOA.create(server, EntitySpawnReason.SPAWN_ITEM_USE);
			if (chick == null) {
				return InteractionResult.FAIL;
			}
			chick.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			chick.setYRot(context.getRotation());
			chick.setKind(this.kind);
			chick.setRaised(true);
			chick.setAge(-Moa.CHICK_TICKS);
			server.addFreshEntity(chick);
			server.playSound(null, at, SoundEvents.TURTLE_EGG_HATCH, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.3F);
			if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
				context.getItemInHand().shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
