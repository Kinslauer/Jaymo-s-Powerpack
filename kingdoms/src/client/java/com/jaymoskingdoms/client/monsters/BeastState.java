package com.jaymoskingdoms.client.monsters;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * What the Mo' Creatures beasts' models need to draw a frame (the scorpion, the manticore, the zebra and the golem that
 * throws itself; the wyvern has the wind dragons' own state): the flags and counters their original models read off the
 * entity, copied across once a frame by their renderers.
 */
public final class BeastState extends LivingEntityRenderState {
	int variant;
	/** A scorpion mother carrying her brood; her tail raised to sting; how far her claws' blow has run (ticks of the old model's 20). */
	boolean babies, stinging;
	int clawCounter;
	/** A manticore on the wing: in the air, beating its wings, with its tail raised to sting, its jaw open. */
	boolean airborne, flapping;
	int mouth;
	/** A zebra: saddled and ridden, its mouth open, its tail moving. */
	boolean saddled, ridden, mouthOpen, tailMoving, eating, rearing;
	/** The big golem: how many of its blocks it carries (a bit for each, see {@code BigGolem}), its state (0 asleep, 1 gathering, 2 whole, 3 hurt, 4 dying) and its core showing. */
	int blocks, golemState;
	/** The golem's cubes by slot (a texture number, 30 for none), its chest open, its arms raised to throw. */
	final int[] cubes = new int[23];
	boolean open, raised;
	/** The small life and the sea's: a snail in its shell, a manta (not a stingray), how far a buried stingray sits in the sand (blocks). */
	boolean hiding, manta;
	float sink;
	/** A big cat that is only a cat (the liger): no wings, no stinger, and no mane on the tigon. */
	boolean plain, maneless;
	float tick, attackAnim;
}
