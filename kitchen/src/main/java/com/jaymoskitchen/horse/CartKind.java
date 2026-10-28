package com.jaymoskitchen.horse;

/**
 * The horse-drawn vehicles, after NiftyCarts (MIT, see LICENSE_niftycarts), in every wood. The wagon is our own design
 * on their supply cart's frame, and the coach is a longer one of those.
 */
public enum CartKind {
	/** A chest (whose lid opens) and a bench for a passenger, on NiftyCarts' frame, a size up. */
	WAGON("wagon", 27, NiftyCarts.WHEEL_RADIUS * NiftyCarts.WAGON_SCALE, 2.0, -0.25, true, true),
	/**
	 * A long bed with three benches (six seats) on four wheels, drawn at the wagon's size. No storage; always too heavy
	 * to pull by hand, and with Jaymo's Kingdoms installed only a draft horse will pull it. It rests level: with four
	 * wheels there are no shafts to tip it forward.
	 */
	COACH("coach", 0, NiftyCarts.WHEEL_RADIUS * NiftyCarts.WAGON_SCALE, 2.5, -0.25, true, false),
	/** A double chest's worth of cargo, shown piled in the bed; only pulled by hand while empty. */
	SUPPLY_CART("supply_cart", 54, NiftyCarts.WHEEL_RADIUS, 1.7, -0.35, true, true),
	/** Carries two animals (they climb in when they bump into it), or an animal and a rider. */
	ANIMAL_CART("animal_cart", 0, NiftyCarts.WHEEL_RADIUS, 1.7, -0.25, true, true),
	/** Tills the ground behind it with the hoes on its three arms, once they're lowered. */
	PLOW("plow", 3, NiftyCarts.WHEEL_RADIUS, 1.3, -0.15, true, true);

	/** The registry name (per-wood items are "&lt;wood&gt;_&lt;name&gt;"). */
	public final String id;
	/** Inventory slots. */
	public final int slots;
	/** Wheel radius, in model pixels. */
	public final float wheelRadius;
	/** Gap from the puller: the cart's middle trails this far plus half the puller's width behind it. */
	public final double spacing;
	/** How much it slows a player pulling it by hand (horses keep their own pace). */
	public final double handDrag;
	/** A NiftyCarts-style cart: made in every wood. */
	public final boolean nifty;
	/** Rests tipped forward on its shafts when nothing's pulling it, and pitches toward its puller (the two-wheelers). */
	public final boolean tilts;

	CartKind(String id, int slots, float wheelRadius, double spacing, double handDrag, boolean nifty, boolean tilts) {
		this.id = id;
		this.slots = slots;
		this.wheelRadius = wheelRadius;
		this.spacing = spacing;
		this.handDrag = handDrag;
		this.nifty = nifty;
		this.tilts = tilts;
	}
}
