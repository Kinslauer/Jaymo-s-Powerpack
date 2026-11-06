package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.NightGifts;
import com.jaymoskingdoms.PowersCompat;
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
 * <p>
 * (0.7.625) Andy: "chrono in time flow should get the display, but for fist users across the board the menu can show
 * while you have an empty hand and are locked on." Powers' takedowns ({@code combat/Takedowns}) are bar spells too
 * ({@code jaymoskingdoms:move_<move>}): in Time Flow the takedown and the blink, always; with an empty hand and locked
 * on, the takedown your power has. They go at your lock-on foe, else the one in your sights.
 * <p>
 * (0.7.632) Andy: "please place them on the bar" (a vampire's O and P). A vampire's or dhampir's Blood Dash, Groundpound
 * and Blood Gaze (the eye laser) are bar spells too ({@code jaymoskingdoms:night_<move>}, {@link NightGifts#move}), with
 * anything or nothing in hand.
 */
public final class FormSkills {
	private static final String MOD_ID = "spell_engine";
	/** A form's spell is {@code jaymoskingdoms:form_<form id>}. */
	static final String PREFIX = "form_";
	/** The source's name in Spell Engine's list. */
	static final String SOURCE = "jaymoskingdoms_forms";
	/** The custom delivery every form spell names. */
	static final Identifier HANDLER = JaymosKingdoms.id("weapon_form");
	/** (0.7.625) A Powers takedown's spell is {@code jaymoskingdoms:move_<move>}, delivered by this. */
	static final String MOVE_PREFIX = "move_";
	static final Identifier MOVE_HANDLER = JaymosKingdoms.id("power_move");
	/** (0.7.632) A vampire's moves on the bar: {@code jaymoskingdoms:night_<move>}, delivered by this; the moves, by NightGifts' index. */
	static final String NIGHT_PREFIX = "night_";
	static final Identifier NIGHT_HANDLER = JaymosKingdoms.id("night_move");
	static final List<String> NIGHT_MOVES = List.of("dash", "pound", "gaze");
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

	/** (0.7.625) The Powers takedown a spell id stands for, or null. */
	public static @Nullable String moveOf(@Nullable Identifier spell) {
		if (spell == null || !spell.getNamespace().equals(JaymosKingdoms.MOD_ID) || !spell.getPath().startsWith(MOVE_PREFIX)) {
			return null;
		}
		return spell.getPath().substring(MOVE_PREFIX.length());
	}

	/** (0.7.632) Which of a vampire's moves a spell id stands for (NightGifts' index), or -1. */
	public static int nightOf(@Nullable Identifier spell) {
		if (spell == null || !spell.getNamespace().equals(JaymosKingdoms.MOD_ID) || !spell.getPath().startsWith(NIGHT_PREFIX)) {
			return -1;
		}
		return NIGHT_MOVES.indexOf(spell.getPath().substring(NIGHT_PREFIX.length()));
	}

	/** (0.7.625) A form's or a takedown's spell, (0.7.632) or a vampire's move. */
	public static boolean ours(@Nullable Identifier spell) {
		return formOf(spell) != null || moveOf(spell) != null || nightOf(spell) >= 0;
	}

	/** The form spells on {@code player}'s bar now, (0.7.625) its Powers takedowns first. */
	public static List<String> spells(Player player) {
		List<String> out = new ArrayList<>();
		// (0.7.632) a vampire's (or dhampir's) Blood Dash, Groundpound and eye laser, whatever's in hand
		NightGifts.Gift gift = NightGifts.gift(player);
		if (gift != null && gift.blood()) {
			for (String move : NIGHT_MOVES) {
				out.add(JaymosKingdoms.MOD_ID + ":" + NIGHT_PREFIX + move);
			}
		}
		for (String move : PowersCompat.barMoves(player)) {
			out.add(JaymosKingdoms.MOD_ID + ":" + MOVE_PREFIX + move);
		}
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
			net.spell_engine.api.spell.event.SpellHandlers.registerCustomDelivery(MOVE_HANDLER, (level, spell, caster, targets, context, position) -> {
				String move = moveOf(spell.unwrapKey().map(key -> key.identifier()).orElse(null));
				return move != null && caster instanceof ServerPlayer player && PowersCompat.castMove(player, move);
			});
			net.spell_engine.api.spell.event.SpellHandlers.registerCustomDelivery(NIGHT_HANDLER, (level, spell, caster, targets, context, position) -> {
				int which = nightOf(spell.unwrapKey().map(key -> key.identifier()).orElse(null));
				return which >= 0 && caster instanceof ServerPlayer player && NightGifts.move(player, which);
			});
		}
	}
}
