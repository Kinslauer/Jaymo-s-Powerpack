package com.jaymoskitchen.corpse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A small animal's corpse in your pocket (a chicken, a rabbit...), on its way to a butcher block.
 * Use it on a block to put it down; on a butcher block it's laid on top, ready for the axe.
 */
public class CorpseItem extends Item {
	public CorpseItem(Item.Properties properties) {
		super(properties);
	}

	/** Picks up a corpse: the item remembers how it looks and what's in it, and is named after the animal. */
	public static ItemStack of(CorpseEntity corpse) {
		ItemStack stack = new ItemStack(Corpses.CORPSE_ITEM);
		stack.set(Corpses.CORPSE_DATA, corpse.toData());
		EntityType<?> type = corpse.sourceType();
		if (type != null) {
			stack.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymoskitchen.corpse.of", type.getDescription()));
		}
		return stack;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		CorpseData data = stack.get(Corpses.CORPSE_DATA);
		if (data == null) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos clicked = context.getClickedPos();
		Vec3 at;
		if (Corpses.isButcherBlock(level.getBlockState(clicked))) {
			// laid out on top of the block, ready to be chopped
			at = new Vec3(clicked.getX() + 0.5, clicked.getY() + 1.0, clicked.getZ() + 0.5);
		} else {
			BlockPos spot = clicked.relative(context.getClickedFace());
			at = new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		}
		CorpseEntity corpse = new CorpseEntity(Corpses.CORPSE, server);
		float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() + 90.0F : 0.0F;
		corpse.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		corpse.restore(data);
		server.addFreshEntity(corpse);
		server.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
		stack.consume(1, context.getPlayer());
		return InteractionResult.SUCCESS_SERVER;
	}
}
