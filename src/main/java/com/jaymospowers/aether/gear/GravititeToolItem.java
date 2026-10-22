package com.jaymospowers.aether.gear;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A gravitite pickaxe, axe, shovel or hoe: used on a block it lifts it ({@link GravititeTools#lift}). The block's own use comes
 * first (a door opens, a lever flips); sneaking with an axe, shovel or hoe, or a block that can't be lifted, leaves the tool's
 * ordinary job (stripping, flattening, tilling). On aether soil the shovel and hoe still flatten and till first, as every
 * shovel and hoe does ({@code AetherSoilTools}).
 */
public class GravititeToolItem extends Item {
	public GravititeToolItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		boolean pickaxe = context.getItemInHand().is(ItemTags.PICKAXES);
		if ((pickaxe || context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) && GravititeTools.lift(context)) {
			return InteractionResult.SUCCESS;
		}
		return super.useOn(context);
	}
}
