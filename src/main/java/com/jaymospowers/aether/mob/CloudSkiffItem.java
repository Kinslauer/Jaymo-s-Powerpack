package com.jaymospowers.aether.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** A folded cloud skiff: used on a block it unfolds there, facing the way its owner faces. */
public class CloudSkiffItem extends Item {
	public CloudSkiffItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getLevel() instanceof ServerLevel level) {
			CloudSkiff skiff = AetherAnimals.CLOUD_SKIFF.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
			if (skiff == null) {
				return InteractionResult.FAIL;
			}
			Vec3 at = context.getClickLocation();
			float yaw = context.getPlayer() == null ? 0.0F : context.getPlayer().getYRot();
			skiff.snapTo(at.x, at.y + 0.1, at.z, yaw, 0.0F);
			skiff.yBodyRot = skiff.yHeadRot = yaw;
			if (!level.noCollision(skiff)) {
				return InteractionResult.FAIL;
			}
			level.addFreshEntity(skiff);
			level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, at);
			context.getItemInHand().consume(1, context.getPlayer());
		}
		return InteractionResult.SUCCESS;
	}
}
