package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.550) Andy: "weapon form skills should move to that system too. makes the moves easier to access with less keys".
 * Each Weapon Form is also a Spell Engine spell ({@code data/jaymoskingdoms/spell/form_<id>.json}), so the forms you know
 * for the weapon in hand sit on the RPG Series' skill bar beside its class skills and fire on its keys (1, 2, 3...; the
 * use key stays the weapon's own right-click, the guard). Spell Engine shows the cooldown on the slot.
 * <ul>
 * <li>The forms come from one more spell source ({@link Forms#barForms}: known, fitting the hand, belt order first).</li>
 * <li>A form weapon with no spell container of its own (a vanilla sword, a spear, a claw) is given one that takes only the
 * {@code #jaymoskingdoms:weapon_forms} spells, so the bar shows ({@code SpellContainerSourceMixin}); one that has its
 * own (a Rogues dagger) takes the forms beside its own skills.</li>
 * <li>Casting runs the form's own code ({@link Forms#cast}) through a custom delivery. A form that can't go (no fallen foe
 * for a masterstroke, too hungry, sealed) delivers nothing, so the bar's cooldown doesn't start.</li>
 * </ul>
 * Without Spell Engine nothing here loads and the stance works with weapons as before.
 */
public final class FormSkills {
	private static final String MOD_ID = "spell_engine";
	/** A form's spell is {@code jaymoskingdoms:form_<form id>}. */
	static final String PREFIX = "form_";
	/** The source's name in Spell Engine's list. */
	static final String SOURCE = "jaymoskingdoms_forms";
	/** The custom delivery every form spell names. */
	static final Identifier HANDLER = JaymosKingdoms.id("weapon_form");
	/** The spell tag of every form spell: what a form weapon's own container takes. */
	public static final String TAG = JaymosKingdoms.MOD_ID + ":weapon_forms";

	private FormSkills() {
	}

	public static void init() {
		if (FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			Hooks.init();
		}
	}

	/** The spell id of {@code formId}. */
	public static String spellId(String formId) {
		return JaymosKingdoms.MOD_ID + ":" + PREFIX + formId;
	}

	/** The form a spell id stands for, or null when it isn't a form's. */
	public static @Nullable String formOf(@Nullable Identifier spell) {
		if (spell == null || !spell.getNamespace().equals(JaymosKingdoms.MOD_ID) || !spell.getPath().startsWith(PREFIX)) {
			return null;
		}
		String id = spell.getPath().substring(PREFIX.length());
		return Forms.get(id) == null ? null : id;
	}

	/** The form spells on {@code player}'s bar now. */
	static List<String> spells(Player player) {
		List<String> out = new ArrayList<>();
		for (String id : Forms.barForms(player)) {
			out.add(spellId(id));
		}
		return out;
	}

	/** Kept apart so Spell Engine's classes load only when it's there. */
	private static final class Hooks {
		static void init() {
			net.spell_engine.internals.container.SpellContainerSource.addSource(new net.spell_engine.internals.container.SpellContainerSource.Entry(SOURCE,
				(player, name) -> {
					List<String> spells = spells(player);
					return spells.isEmpty() ? List.of() : List.of(new net.spell_engine.internals.container.SpellContainerSource.SourcedContainer(name, null,
						new net.spell_engine.api.spell.container.SpellContainer(net.spell_engine.api.spell.container.SpellContainer.ContentType.NONE, "", "", "", 0,
							spells, 0)));
				}, FormSkills::spells), net.spell_engine.internals.container.SpellContainerSource.MAIN_HAND.name());
			net.spell_engine.api.spell.event.SpellHandlers.registerCustomDelivery(HANDLER, (level, spell, caster, targets, context, position) -> {
				String form = formOf(spell.unwrapKey().map(key -> key.identifier()).orElse(null));
				return form != null && caster instanceof ServerPlayer player && Forms.cast(player, form);
			});
		}
	}
}
