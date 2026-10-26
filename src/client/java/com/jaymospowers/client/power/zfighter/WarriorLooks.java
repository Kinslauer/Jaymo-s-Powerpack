package com.jaymospowers.client.power.zfighter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.36) Andy: "the warrior born tails should vary a bit between races, and so should the ape forms, all these things
 * are randomized per character so no two are the same". Every Warrior-born's tail ({@link ZFighterTail}) and Great Ape
 * ({@link ZFighterApe}) is rolled from their UUID, so it's the same on every screen and every day: the tail's length,
 * thickness, droop, curl and sway, the ape's head, arms, bulk and tail, and the fur's shade. Jaymo's Kingdoms' heritage
 * variant (read by reflection, {@code HeritageVariants.VARIANT}) sets the fur's family: the Clanborn brown, the
 * Battle-scarred's dark fur and kinked tail, the Warpainted's red fur with ochre bands. Tune the ranges here.
 */
public final class WarriorLooks {
	/** The Warrior-born's three looks, as Kingdoms keys them (anything else, or no Kingdoms: the first). */
	enum Line { CLANBORN, BATTLESCARRED, WARPAINTED }

	/** Fur shades (multiplied over the fur's own brown) for each look; one is picked per character, then nudged. */
	static final int[][] FUR = {
		{0xFFFFFFFF, 0xFFF2E2D2, 0xFFE0C8B0, 0xFFFFF0D8},
		{0xFFA89888, 0xFF8C7C70, 0xFF6E625A, 0xFF7A6E6A},
		{0xFFFFC8A8, 0xFFFFB090, 0xFFE8A080, 0xFFFFBC98}};
	/** The Warpainted's ochre bands, every other segment. */
	static final int OCHRE = 0xFFE07040;
	/** How far a character's fur is nudged off its shade (each channel, out of 255). */
	static final int FUR_JITTER = 14;

	/**
	 * A character's tail: segments, each one's length (pixels) and thickness (scale), the first segment's droop, the curl of
	 * the rest, the sway's size, speed and phase, the fur's tint, ochre bands, and a kink (the segment bent hard, -1: none).
	 */
	public record Tail(int segments, float length, float thickness, float droop, float curl, float sway, float swaySpeed, float phase, int fur,
		boolean banded, int kink) {
	}

	/** A character's Great Ape: its head, arm length, bulk and tail length (scales), and the fur's tint. */
	public record Ape(float head, float arms, float bulk, float tail, int fur) {
	}

	private static final @Nullable AttachmentType<String> VARIANT = variantType();
	/** Rolled once per character and look (each frame asks). */
	private static final Map<UUID, Tail> TAILS = new HashMap<>();
	private static final Map<UUID, Ape> APES = new HashMap<>();
	/** Past this many remembered, start over. */
	static final int CACHE_MAX = 512;

	private WarriorLooks() {
	}

	@SuppressWarnings("unchecked")
	private static @Nullable AttachmentType<String> variantType() {
		if (!FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			return null;
		}
		try {
			return (AttachmentType<String>)Class.forName("com.jaymoskingdoms.HeritageVariants").getField("VARIANT").get(null);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			return null;
		}
	}

	static Line line(Entity entity) {
		String key = VARIANT == null ? null : entity.getAttached(VARIANT);
		if ("battlescarred".equals(key)) {
			return Line.BATTLESCARRED;
		}
		return "warpainted".equals(key) ? Line.WARPAINTED : Line.CLANBORN;
	}

	private static RandomSource random(UUID id, long salt) {
		return RandomSource.create(id.getMostSignificantBits() * 31L ^ id.getLeastSignificantBits() ^ salt);
	}

	private static int fur(Line line, RandomSource random) {
		int[] shades = FUR[line.ordinal()];
		int c = shades[random.nextInt(shades.length)];
		return ARGB.color(255, jitter(ARGB.red(c), random), jitter(ARGB.green(c), random), jitter(ARGB.blue(c), random));
	}

	private static int jitter(int channel, RandomSource random) {
		return Mth.clamp(channel + random.nextIntBetweenInclusive(-FUR_JITTER, FUR_JITTER), 0, 255);
	}

	/** {@code entity}'s tail. */
	public static Tail tail(Entity entity) {
		Line line = line(entity);
		Tail known = TAILS.get(entity.getUUID());
		if (known != null && known.banded() == (line == Line.WARPAINTED) && (known.kink() >= 0) == (line == Line.BATTLESCARRED)) {
			return known;
		}
		if (TAILS.size() > CACHE_MAX) {
			TAILS.clear();
		}
		Tail tail = roll(entity, line);
		TAILS.put(entity.getUUID(), tail);
		return tail;
	}

	private static Tail roll(Entity entity, Line line) {
		RandomSource r = random(entity.getUUID(), 0x7A11L);
		int fur = fur(line, random(entity.getUUID(), 0xF0FL));
		int segments = 5 + r.nextInt(4);
		int kink = -1;
		if (line == Line.BATTLESCARRED) {
			// an old break: one segment bent hard, and often the tip lost
			kink = 1 + r.nextInt(segments - 2);
			segments -= r.nextInt(2);
		}
		return new Tail(segments, Mth.randomBetween(r, 2.2F, 3.0F), Mth.randomBetween(r, 0.85F, 1.2F), Mth.randomBetween(r, -0.95F, -0.55F),
			Mth.randomBetween(r, 0.18F, 0.45F), Mth.randomBetween(r, 0.15F, 0.3F), Mth.randomBetween(r, 0.8F, 1.25F), r.nextFloat() * Mth.TWO_PI, fur,
			line == Line.WARPAINTED, kink);
	}

	/** {@code entity}'s Great Ape (its fur the same shade as its tail). */
	public static Ape ape(Entity entity) {
		Ape known = APES.get(entity.getUUID());
		if (known != null && known.fur() == tail(entity).fur()) {
			return known;
		}
		if (APES.size() > CACHE_MAX) {
			APES.clear();
		}
		RandomSource r = random(entity.getUUID(), 0xA9EL);
		Ape ape = new Ape(Mth.randomBetween(r, 0.9F, 1.12F), Mth.randomBetween(r, 0.9F, 1.15F), Mth.randomBetween(r, 0.92F, 1.12F),
			Mth.randomBetween(r, 0.8F, 1.3F), tail(entity).fur());
		APES.put(entity.getUUID(), ape);
		return ape;
	}
}
