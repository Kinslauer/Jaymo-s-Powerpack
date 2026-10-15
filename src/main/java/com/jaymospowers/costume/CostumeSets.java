package com.jaymospowers.costume;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.core.Powers;
import java.util.function.Function;
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

	/** Whether {@code player} may sew a piece of {@code power}'s set (null: anyone's piece). Either side. */
	public static boolean has(Player player, @Nullable String power) {
		return power == null || JaymosPowers.id(power).equals(player.getAttached(Powers.POWER));
	}

	/** Whether every piece of {@code design} is one {@code player} may sew. */
	public static boolean allowed(Player player, CostumeDesign design) {
		return has(player, design.cut().power) && has(player, design.mask().power) && has(player, design.cape().power);
	}

	/** {@code design} with any piece {@code player} may not sew put back to the plain one (the designer's starting point). */
	public static CostumeDesign fit(Player player, CostumeDesign design) {
		CostumeDesign out = design;
		if (!has(player, out.cut().power)) {
			out = out.withCut(CostumeDesign.Cut.BODYSUIT);
		}
		if (!has(player, out.mask().power)) {
			out = out.withMask(CostumeDesign.Mask.NONE);
		}
		if (!has(player, out.cape().power)) {
			out = out.withCape(CostumeDesign.Cape.NONE);
		}
		return out;
	}

	/** The next of {@code values} from {@code now}, {@code by} steps (+1 or -1) on, skipping pieces {@code player} may not sew. */
	public static <E extends Enum<E>> E step(Player player, E[] values, E now, int by, Function<E, @Nullable String> power) {
		int i = now.ordinal();
		for (int n = 0; n < values.length; n++) {
			i = Math.floorMod(i + by, values.length);
			if (has(player, power.apply(values[i]))) {
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
