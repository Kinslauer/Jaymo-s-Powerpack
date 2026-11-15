package com.jaymoskitchen.mill.power;

import net.minecraft.world.item.Item;

/**
 * A windmill sail, rolled up on its yard (GT New Horizons picks, patch 8): used on a windmill's hub it's fitted to the
 * first bare or torn spar ({@link RotorEntity#fit}). Its wear goes with it: the damage bar is what it has used of its
 * {@link SailKind#life}, and a sail taken down (sneak with an empty hand) comes back as worn as it was. Patched with its
 * own stuff on the hub or at an anvil.
 */
public class SailItem extends Item {
	public final SailKind kind;

	public SailItem(SailKind kind, Item.Properties properties) {
		super(properties);
		this.kind = kind;
	}
}
