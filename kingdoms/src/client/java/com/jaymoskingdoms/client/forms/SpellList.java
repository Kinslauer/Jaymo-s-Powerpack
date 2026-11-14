package com.jaymoskingdoms.client.forms;

import com.jaymoskingdoms.forms.FormSkills;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The Spell Engine spells you have right now, for the belt menu's Spells tab: what your spell book, your weapon and your
 * gear give you (the same lists Spell Engine fills its skill bar from). Weapon forms are left out, they have their own
 * tabs. Empty without Spell Engine.
 */
final class SpellList {
	static final boolean LOADED = FabricLoader.getInstance().isModLoaded("spell_engine");

	/** One spell: its id, its name, and whether it is a passive (always on, never on the skill bar). */
	record Row(Identifier id, Component name, boolean passive) {
	}

	private SpellList() {
	}

	static List<Row> of(@Nullable Player player) {
		if (!LOADED || player == null) {
			return List.of();
		}
		try {
			return Hooks.of(player);
		} catch (RuntimeException | LinkageError e) {
			// a changed Spell Engine: no Spells tab rather than a closed menu
			return List.of();
		}
	}

	/** What the spell does, as Spell Engine's own tooltip says it (description, cast time, cost, cooldown). */
	static List<Component> describe(Row row, Player player) {
		List<Component> out = new ArrayList<>();
		out.add(row.name());
		try {
			out.addAll(Hooks.describe(row, player));
		} catch (RuntimeException | LinkageError e) {
			// the name alone
		}
		return out;
	}

	/** Everything that touches Spell Engine's classes, loaded only when the mod is there. */
	private static final class Hooks {
		static List<Row> of(Player player) {
			var spells = net.spell_engine.internals.container.SpellContainerSource.getSpellsOf(player);
			List<Row> out = new ArrayList<>();
			add(out, spells.actives(), false);
			add(out, spells.passives(), true);
			return out;
		}

		private static void add(List<Row> out, List<Holder<net.spell_engine.api.spell.Spell>> spells, boolean passive) {
			for (Holder<net.spell_engine.api.spell.Spell> spell : spells) {
				Identifier id = spell.unwrapKey().map(key -> key.identifier()).orElse(null);
				if (id != null && FormSkills.formOf(id) == null && out.stream().noneMatch(r -> r.id().equals(id))) {
					out.add(new Row(id, Component.translatable(net.spell_engine.client.gui.SpellTooltip.spellTranslationKey(id)), passive));
				}
			}
		}

		static List<Component> describe(Row row, Player player) {
			return net.spell_engine.client.gui.SpellTooltip.spellDescriptionWithDetails(row.id(), player, player.getMainHandItem(), 0);
		}
	}
}
