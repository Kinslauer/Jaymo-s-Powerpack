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

/**
 * A moa egg (blue, white or black), laid by a moa. Using it on the ground hatches a chick of that kind on the spot: a
 * chick a player raised, so one that can be saddled once it has grown (feed it blue berries to hurry that).
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
