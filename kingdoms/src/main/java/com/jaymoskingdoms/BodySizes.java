package com.jaymoskingdoms;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.290) Andy: "stone borns should be able to be naturally wider and taller, where sylvans are the smallest". Each
 * heritage's body, as MCA's Size (height) and Width genes (0 .. 1; MCA draws 0.75x at 0 and 1.25x at 1):
 * <ul>
 * <li>Villagers are born into their blood's ranges (a fresh one spread across them, {@code McaGeneticsMixin}; a child or
 * one already in the world brought in from the nearer edge, {@link VillagerHeritage#born} and on load).</li>
 * <li>Players' MCA sliders are kept to them, on the editor figure ({@code McaEditorSkinMixin}) and whenever their look is
 * saved ({@code McaPlayerSizeMixin}). This takes over the old Verdant-only top of the Size slider (0.7.87): Verdant are
 * still the only ones who reach it.</li>
 * </ul>
 * My picks besides Stoneborn and Sylvan: Andy can change them here. A new heritage gets {@link #ORDINARY} until it's
 * given its own line in {@link #of}.
 */
public final class BodySizes {
	/** A heritage's height and width, least and most, as MCA genes. */
	public record Body(float minSize, float maxSize, float minWidth, float maxWidth) {
	}

	/** Anyone of no heritage: MCA's whole slider. */
	static final Body ANY = new Body(0.0F, 1.0F, 0.0F, 1.0F);
	/** Plain folk, and any heritage without a line of its own. */
	static final Body ORDINARY = new Body(0.15F, 0.75F, 0.15F, 0.75F);
	/** The biggest: tall and broad as the rock they're cut from. */
	static final Body STONEBORN = new Body(0.55F, 0.9F, 0.6F, 1.0F);
	/** The smallest: short and slight. */
	static final Body SYLVAN = new Body(0.0F, 0.4F, 0.0F, 0.35F);
	/** Andy: "verdants are naturally taller". The tallest (the top of the slider stays theirs), and slender. */
	static final Body VERDANT = new Body(0.6F, 1.0F, 0.15F, 0.7F);
	static final Body TIDEBORN = new Body(0.2F, 0.75F, 0.25F, 0.8F);
	static final Body EMBERBORN = new Body(0.2F, 0.75F, 0.2F, 0.75F);
	/** Hardy northerners: big-boned. */
	static final Body FROSTBORN = new Body(0.3F, 0.8F, 0.35F, 0.85F);
	/** Lean. */
	static final Body NIGHTBORN = new Body(0.15F, 0.7F, 0.1F, 0.6F);
	static final Body WARRIORBORN = new Body(0.25F, 0.8F, 0.3F, 0.85F);
	static final Body STORMBORN = new Body(0.2F, 0.75F, 0.15F, 0.65F);
	static final Body DAWNBORN = new Body(0.25F, 0.75F, 0.2F, 0.75F);
	/** Tall and thin, like the Endermen. */
	static final Body ENDBORN = new Body(0.35F, 0.85F, 0.05F, 0.5F);
	static final Body DRAGONBLOOD = new Body(0.35F, 0.85F, 0.35F, 0.85F);
	/**
	 * Andy: "divine born should vary from the most extreme ends of the spectrum, so should rubber, but they change with
	 * what they absorb". Both span the whole slider; a Rubber-folk's shifts toward each foe they swallow ({@link #ABSORB_SHIFT}).
	 */
	static final Body DIVINE = ANY, RUBBERFOLK = ANY;
	/**
	 * How far a Rubber-folk's height and width move toward a swallowed foe's (a share of the gap), and the player shape
	 * a foe is measured against (vanilla's 0.6 x 1.8 at MCA's 1x).
	 */
	public static final float ABSORB_SHIFT = 0.75F;
	static final float PLAYER_WIDTH = 0.6F, PLAYER_HEIGHT = 1.8F;
	/** Squat from the deep. */
	static final Body DEEPBORN = new Body(0.15F, 0.7F, 0.3F, 0.8F);
	static final Body BIOBUILT = new Body(0.2F, 0.8F, 0.3F, 0.85F);
	/**
	 * (0.7.292) Andy: "Orcs are bigger, goblins smaller" ({@link Greenskins}). A goblin is short and stringy on top of its
	 * {@link Goblins#SCALE} (its king the broadest of them); an orc is drawn the tallest and broadest there is (MCA keeps
	 * anyone's box a man's size at most, so an orc still fits a door; a warchief's own scale makes him huge).
	 */
	static final Body GOBLIN = new Body(0.15F, 0.5F, 0.2F, 0.75F), ORC = new Body(0.75F, 1.0F, 0.85F, 1.0F);

	/**
	 * The tag on an MCA villager whose height and width MCA has just rolled ({@code McaGeneticsMixin}), till it's born to
	 * its blood and they're spread across its body ({@link VillagerHeritage#born}).
	 */
	public static final String FRESH = "jaymoskingdoms_fresh_body";
	/** How far inside its range a villager born outside it lands, at most (a share of the range). */
	static final float BORN_INSIDE = 0.25F;

	private BodySizes() {
	}

	/** {@code kind}'s body (no heritage: {@link #ANY}). */
	public static Body of(Heritage.@Nullable Kind kind) {
		if (kind == null) {
			return ANY;
		}
		return switch (kind) {
			case STONEBORN -> STONEBORN;
			case SYLVAN -> SYLVAN;
			case VERDANT -> VERDANT;
			case TIDEBORN -> TIDEBORN;
			case EMBERBORN -> EMBERBORN;
			case FROSTBORN -> FROSTBORN;
			case NIGHTBORN -> NIGHTBORN;
			case WARRIORBORN -> WARRIORBORN;
			case STORMBORN -> STORMBORN;
			case DAWNBORN -> DAWNBORN;
			case ENDBORN -> ENDBORN;
			case DRAGONBLOOD -> DRAGONBLOOD;
			case DEEPBORN -> DEEPBORN;
			case DIVINE -> DIVINE;
			case BIOBUILT -> BIOBUILT;
			case RUBBERFOLK -> RUBBERFOLK;
			case GOBLIN -> GOBLIN;
			case ORC -> ORC;
			default -> ORDINARY;
		};
	}

	/** {@code size} brought into {@code kind}'s height range. */
	public static float clampSize(Heritage.@Nullable Kind kind, float size) {
		Body body = of(kind);
		return Mth.clamp(size, body.minSize, body.maxSize);
	}

	/** {@code width} brought into {@code kind}'s width range. */
	public static float clampWidth(Heritage.@Nullable Kind kind, float width) {
		Body body = of(kind);
		return Mth.clamp(width, body.minWidth, body.maxWidth);
	}

	/** The middle of {@code kind}'s height range (the life story's figure). */
	public static float middleSize(Heritage.@Nullable Kind kind) {
		Body body = of(kind);
		return (body.minSize + body.maxSize) / 2.0F;
	}

	/** The middle of {@code kind}'s width range. */
	public static float middleWidth(Heritage.@Nullable Kind kind) {
		Body body = of(kind);
		return (body.minWidth + body.maxWidth) / 2.0F;
	}

	/** The MCA gene (0 .. 1) that draws a body {@code length} long, against a player's {@code base} (0.75x .. 1.25x). */
	public static float geneFor(float length, float base) {
		return Mth.clamp((length / base - 0.75F) * 2.0F, 0.0F, 1.0F);
	}

	/** The Size gene a body {@code height} tall would have. */
	public static float sizeFor(float height) {
		return geneFor(height, PLAYER_HEIGHT);
	}

	/** The Width gene a body {@code width} wide would have. */
	public static float widthFor(float width) {
		return geneFor(width, PLAYER_WIDTH);
	}

	/** A freshly rolled gene (0 .. 1) spread across {@code min} .. {@code max}, so a blood's folk vary as much as MCA's. */
	public static float spread(float gene, float min, float max) {
		return min + Mth.clamp(gene, 0.0F, 1.0F) * (max - min);
	}

	/** A gene kept in {@code min} .. {@code max}: as it was if it's inside, else a little way in from the nearer edge. */
	public static float keep(float gene, float min, float max, RandomSource random) {
		float inside = random.nextFloat() * BORN_INSIDE * (max - min);
		return gene < min ? min + inside : gene > max ? max - inside : gene;
	}
}
