package com.jaymospowers.costume;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.core.Powers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.78) Costumes, stage 3: each power adds its own set to the Costume Table (Andy: "web lines and gloves for Web, a
 * gale cloak for Wind, ash wraps for Smoke, and so on"). A cut, mask or cape with a {@code power} is offered in the
 * designer only while you have that power ({@link Powers#of}), and the server refuses to sew it otherwise. A costume
 * already sewn stays as it is: it can be worn, traded and drawn whoever has it.
 */
public final class CostumeSets {
	private CostumeSets() {
	}

	/**
	 * A cut, mask or cape: it may belong to a power's set ({@link #power}) and may be a pattern piece ({@link #pattern}),
	 * locked for a player until they have used a Costume Pattern for it ({@link CostumePatterns}).
	 */
	public interface Piece extends net.minecraft.util.StringRepresentable {
		/** "cut", "mask" or "cape". */
		String kind();

		boolean pattern();

		@Nullable String power();

		/** What a Costume Pattern and a player's learned list call it: {@code mask:domino}. */
		default String key() {
			return kind() + ":" + getSerializedName();
		}
	}

	/** Whether {@code player} may use {@code piece}: its power's set (if any) and, for a pattern piece, having learned it. */
	public static boolean may(Player player, Piece piece) {
		return has(player, piece.power()) && CostumePatterns.known(player, piece);
	}

	/** Whether {@code player} may sew a piece of {@code power}'s set (null: anyone's piece). Either side. */
	public static boolean has(Player player, @Nullable String power) {
		return power == null || JaymosPowers.id(power).equals(player.getAttached(Powers.POWER));
	}

	/** Whether every piece of {@code design} is one {@code player} may sew. */
	public static boolean allowed(Player player, CostumeDesign design) {
		return may(player, design.cut()) && may(player, design.mask()) && may(player, design.cape());
	}

	/** {@code design} with any piece {@code player} may not sew put back to the plain one (the designer's starting point). */
	public static CostumeDesign fit(Player player, CostumeDesign design) {
		CostumeDesign out = design;
		if (!may(player, out.cut())) {
			out = out.withCut(CostumeDesign.Cut.BODYSUIT);
		}
		if (!may(player, out.mask())) {
			out = out.withMask(CostumeDesign.Mask.NONE);
		}
		if (!may(player, out.cape())) {
			out = out.withCape(CostumeDesign.Cape.NONE);
		}
		return out;
	}

	/** The next of {@code values} from {@code now}, {@code by} steps (+1 or -1) on, skipping pieces {@code player} may not sew. */
	public static <E extends Enum<E> & Piece> E step(Player player, E[] values, E now, int by) {
		int i = now.ordinal();
		for (int n = 0; n < values.length; n++) {
			i = Math.floorMod(i + by, values.length);
			if (may(player, values[i])) {
				return values[i];
			}
		}
		return now;
	}

	/** The power whose set {@code design} is from (its first piece that has one), or null. */
	public static @Nullable String power(CostumeDesign design) {
		return design.cut().power != null ? design.cut().power : design.mask().power != null ? design.mask().power : design.cape().power;
	}

	/** {@code power}'s name, for "Web set" lines (the id's path if it isn't registered). */
	public static Component powerName(String power) {
		Power p = Powers.byName(power);
		return p != null ? p.name() : Component.literal(power);
	}
}
