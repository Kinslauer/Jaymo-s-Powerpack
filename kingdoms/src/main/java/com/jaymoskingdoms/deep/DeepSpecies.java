package com.jaymoskingdoms.deep;

/**
 * The deep beasts' kinds (Andy, 2026-11-25: sea beasts out of the Abyssal Chasm, in Rim's body shapes repainted and renamed;
 * the shapes are ported by {@code tools/rim_port.py}). Each is its own entity type on {@link DeepBeast}. Sizes are the
 * box the beast stands in (blocks); the model is drawn at {@link #scale}.
 */
public enum DeepSpecies {
	/** A tall, blade-headed striding beast: the first to rise. */
	LANCER("deep_lancer", 5.0F, 8.0F, 420.0, 22.0, 0.26, 1.0F, 0),
	/** A long-necked, winged and pincered beast. Quicker, lighter, and it rises from the second tier. */
	GLOOMWING("deep_gloomwing", 3.0F, 10.0F, 320.0, 18.0, 0.32, 1.0F, 2),
	/** A broad, heavy brute with tusks. It only rises from the fourth tier, and is the toughest. */
	BEHEMOTH("deep_behemoth", 5.5F, 8.0F, 620.0, 30.0, 0.22, 1.0F, 4);

	public final String id;
	/** The width and height of the box it stands in (blocks). */
	public final float width, height;
	public final double health, damage, speed;
	/** How big the model is drawn (1 is the rig's own size, a sixteenth of a block a unit). */
	public final float scale;
	/** The rising tier it first comes at ({@link Deeps#tier}). */
	public final int fromTier;

	DeepSpecies(String id, float width, float height, double health, double damage, double speed, float scale, int fromTier) {
		this.id = id;
		this.width = width;
		this.height = height;
		this.health = health;
		this.damage = damage;
		this.speed = speed;
		this.scale = scale;
		this.fromTier = fromTier;
	}
}
