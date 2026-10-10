package com.jaymoskitchen.horse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Places a cart (in this item's wood), its shafts pointing the way you're facing. */
public class CartItem extends Item {
	private final EntityType<CartEntity> type;
	private final String wood;

	public CartItem(EntityType<CartEntity> type, String wood, Item.Properties properties) {
		super(properties);
		this.type = type;
		this.wood = wood;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		Vec3 at;
		if (context.getClickedFace() == Direction.UP) {
			at = context.getClickLocation();
		} else {
			BlockPos spot = context.getClickedPos().relative(context.getClickedFace());
			at = new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		}
		Player player = context.getPlayer();
		CartEntity cart = new CartEntity(type, level);
		cart.setWood(wood);
		cart.snapTo(at.x, at.y, at.z, player != null ? player.getYRot() : 0.0F, 0.0F);
		if (!level.noCollision(cart, cart.getBoundingBox())) {
			return InteractionResult.FAIL;
		}
		level.addFreshEntity(cart);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
		context.getItemInHand().consume(1, player);
		return InteractionResult.SUCCESS_SERVER;
	}
}
