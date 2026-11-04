package com.jaymoskingdoms;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Hordes, Dialects: orcs and goblins speak two dialects of one broken English. The orc's is short, loud and
 * brutal; the goblin's is quicker, greedier, more nervous and a bit sharper. A line that both broods share (or that a
 * human may share with them) lives twice, as {@code dialect.jaymoskingdoms.<brood>.<the plain key>}; this picks the
 * right one by who is speaking. Lines only one brood ever says keep their own keys.
 */
public final class Dialects {
	public static final String ORC = "orc", GOBLIN = "goblin";
	/** Where the dialect keys sit in the lang file (before the brood, then the plain key). */
	static final String PREFIX = "dialect.jaymoskingdoms.";

	private Dialects() {
	}

	/** The dialect {@code e} speaks ({@link #ORC}, {@link #GOBLIN}), or null if it is neither an orc nor a goblin. */
	public static @Nullable String of(@Nullable Entity e) {
		if (e == null) {
			return null;
		}
		Heritage.Kind kind = Greenskins.of(e);
		if (kind != null) {
			return kind == Heritage.Kind.GOBLIN ? GOBLIN : ORC;
		}
		// (0.7.490) a Folk orc or goblin carries no heritage (no MCA, or a shaman not yet handed to a villager): its role
		// tells its brood, the orc shaman's too (Tribes.of leaves him out)
		if (e instanceof Worker w) {
			Heritage.Kind role = Greenskins.brood(w.role());
			if (role != null) {
				return role == Heritage.Kind.GOBLIN ? GOBLIN : ORC;
			}
		}
		String tribe = Tribes.of(e);
		return tribe == null ? null : ofTribe(tribe);
	}

	/** The dialect of a tribe key ({@code goblin@x,z}, {@code orc@x,z}). */
	public static String ofTribe(@Nullable String tribe) {
		return tribe != null && tribe.startsWith(Tribes.GOBLIN + "@") ? GOBLIN : ORC;
	}

	/** {@code key} in {@code brood}'s dialect. */
	public static String key(String brood, String key) {
		return PREFIX + brood + "." + key;
	}

	/** A line only orcs and goblins say: {@code speaker}'s dialect (orc if it can't be told). */
	public static MutableComponent tr(@Nullable Entity speaker, String key, Object... args) {
		String brood = of(speaker);
		return Component.translatable(key(brood == null ? ORC : brood, key), args);
	}

	/** A line only orcs and goblins say, by tribe key. */
	public static MutableComponent trTribe(@Nullable String tribe, String key, Object... args) {
		return Component.translatable(key(ofTribe(tribe), key), args);
	}

	/** A line in a dialect picked outright. */
	public static MutableComponent trBrood(String brood, String key, Object... args) {
		return Component.translatable(key(brood, key), args);
	}

	/** A line anybody may say: a greenskin speaker's dialect, or the plain line for everyone else. */
	public static MutableComponent trShared(@Nullable Entity speaker, String key, Object... args) {
		String brood = of(speaker);
		return Component.translatable(brood == null ? key : key(brood, key), args);
	}
}
