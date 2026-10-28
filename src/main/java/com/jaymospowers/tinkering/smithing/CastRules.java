package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.WeaponPartItem;
import com.jaymospowers.tinkering.WeaponParts;
import com.jaymospowers.tinkering.smeltery.CastItem;
import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) What the casting table may still do. The longest blades are hammer-only: no cast is made from them and
 * nothing pours into their casts. Every other head can still be cast: a cast head comes out Well Forged; a cast copied
 * from a graded head remembers its grade and pours heads one grade lower. {@code Melting.tableResult} runs its answer
 * through {@link #adjust}.
 */
public final class CastRules {
	/** Weapon heads ({@link WeaponParts.Kind#part()}) too long to cast. */
	public static final Set<String> HAMMER_ONLY_WEAPONS = Set.of("claymore_blade", "broadsword_blade", "longsword_blade", "katana_blade",
		"ninjato_blade", "war_scythe_blade", "glaive_blade", "halberd_head");
	/** Tool heads too long to cast (in metal; stone ones are shaped at the Part Builder). */
	public static final Set<PartType> HAMMER_ONLY_PARTS = Set.of(PartType.BROAD_BLADE, PartType.LARGE_PLATE);
	/** The grade a head poured from a plain cast has. */
	public static final Grade CAST_GRADE = Grade.WELL;

	private CastRules() {
	}

	static void register() {
	}

	/** Whether a weapon head of this kind is hammer-only. */
	public static boolean hammerOnly(WeaponParts.@Nullable Kind kind) {
		return kind != null && HAMMER_ONLY_WEAPONS.contains(kind.part());
	}

	/** Whether this is a metal head that can only be hammered. */
	public static boolean hammerOnly(ItemStack stack) {
		if (stack.getItem() instanceof WeaponPartItem weapon) {
			return hammerOnly(weapon.kind) && WeaponPartItem.metal(stack) != WeaponParts.Metal.STONE;
		}
		if (stack.getItem() instanceof ToolPartItem part && HAMMER_ONLY_PARTS.contains(part.part)) {
			TinkerMaterial material = ToolPartItem.material(stack);
			return material != null && Molten.of(material) != null;
		}
		return false;
	}

	private static boolean head(ItemStack stack) {
		return stack.getItem() instanceof WeaponPartItem || stack.getItem() instanceof ToolPartItem part && part.part.kind == PartType.Kind.HEAD;
	}

	/** Whether this cast (or the thing a cast would be made from) is of a hammer-only shape. */
	static boolean hammerOnlyShape(ItemStack cast) {
		CastItem.Shape shape = cast.getItem() instanceof CastItem item ? item.shape : CastItem.Shape.of(cast);
		if (shape == null) {
			return false;
		}
		if (shape == CastItem.Shape.WEAPON) {
			WeaponParts.Kind kind = cast.getItem() instanceof CastItem ? WeaponParts.ofCast(cast)
				: cast.getItem() instanceof WeaponPartItem weapon ? weapon.kind : null;
			return hammerOnly(kind);
		}
		return shape.part != null && HAMMER_ONLY_PARTS.contains(shape.part);
	}

	/**
	 * What the table makes from {@code cast} and {@code molten} once the rules are applied to the plain answer {@code made}:
	 * null for a hammer-only shape, a poured head given its grade, a new cast given the grade it was copied from.
	 */
	public static @Nullable ItemStack adjust(ItemStack cast, Molten molten, @Nullable ItemStack made) {
		if (made == null || made.isEmpty()) {
			return made;
		}
		if (hammerOnlyShape(cast)) {
			return null;
		}
		if (cast.getItem() instanceof CastItem) {
			if (head(made)) {
				Grade remembered = Grade.byId(cast.get(Finishing.CAST_GRADE));
				Grades.set(made, remembered == null ? CAST_GRADE : remembered.lower());
			}
		} else if (made.getItem() instanceof CastItem && head(cast)) {
			Grade grade = Grades.of(cast);
			if (grade != null) {
				made.set(Finishing.CAST_GRADE, grade.id);
			}
		}
		return made;
	}
}
