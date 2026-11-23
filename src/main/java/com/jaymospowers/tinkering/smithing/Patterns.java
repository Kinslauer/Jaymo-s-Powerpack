package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.WeaponParts;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) Patterns from old gear. A smith who takes a finished piece apart at the Drafting Table (the piece and a
 * sheet of paper on the table, {@link DraftingTableMenu}; taking the pattern is the go-ahead) learns how it was made: the piece is
 * destroyed and its design comes out as a blueprint of its shape, one level under the piece's own grade, as a drafted
 * copy is. Any forged weapon, weapon head or armour piece has a pattern. For most it is a blueprint like any other; for
 * the sets the anvil won't start without their schematic ({@link FrontierForging}) it is how a set nobody sells, like
 * the Juggernaut plate, can be forged at all. Other mods add their own rule for who can read a design ({@link #GATES}).
 */
public final class Patterns {
	/** Why this smith can't take a piece apart for its design (Kingdoms: not enough of the Knowledge), or null. */
	public static final List<Function<ServerPlayer, Component>> GATES = new ArrayList<>();

	private Patterns() {
	}

	/** The forging shape {@code piece} is an example of ({@link Blueprints#shape}), or null if nothing is forged like it. */
	public static @Nullable String shapeOf(ItemStack piece) {
		if (piece.isEmpty() || piece.is(Blueprints.BLUEPRINT_ITEM)) {
			return null;
		}
		for (Forging.Option option : Forging.all().values()) {
			if (option.result().is(piece.getItem())) {
				return Blueprints.shape(option);
			}
		}
		// a finished weapon teaches its head
		for (WeaponParts.Kind kind : WeaponParts.loaded()) {
			if (WeaponParts.king(kind)) {
				continue;
			}
			for (WeaponParts.Metal metal : WeaponParts.Metal.values()) {
				if (metal.molten != null && kind.in(metal) == piece.getItem()) {
					return "weapon/" + kind.part();
				}
			}
		}
		return null;
	}

	/** The blueprint taking {@code piece} apart gives. */
	public static ItemStack pattern(ItemStack piece, String shape) {
		Grade grade = Grades.of(piece);
		ItemStack pattern = Blueprints.stack(shape, Blueprints.xpOf(grade == null ? Grade.WELL : grade.lower()));
		// (medieval picks) a realm's marked piece gives a captured pattern: it lets its holder forge that realm's version (RealmPatterns)
		String realm = piece.get(RealmPatterns.REALM_DESIGN);
		return realm == null ? pattern : RealmPatterns.capture(pattern, realm);
	}

	/** Why {@code smith} can't take a piece apart for its design (the first gate that says no), or null. */
	static @Nullable Component refused(ServerPlayer smith) {
		for (Function<ServerPlayer, Component> gate : GATES) {
			Component why = gate.apply(smith);
			if (why != null) {
				return why;
			}
		}
		return null;
	}
}
