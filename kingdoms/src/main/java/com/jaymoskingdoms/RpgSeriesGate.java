package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.internals.casting.SpellCast;
import org.jspecify.annotations.Nullable;

/**
 * (RPG Series patch 1) Zsolt Molnar's Wizards, Archers and Rogues &amp; Warriors (shipped unchanged with Spell Engine,
 * Powers' packMods) are our classes' skills: only the matching class casts them. Anyone can still swing the weapons
 * (Better Combat) and wear the armour.
 * <ul>
 * <li>Wizards (every school, and the staffs' and wands' own bolts): the Mage.</li>
 * <li>Archers (Power Shot, Barrage, Magic Arrow, Entangling Roots, Rain of Arrows, Spirit Wolf): the Archer and the
 * Marksman.</li>
 * <li>Rogues' rogue book (Slice and Dice, Shock Powder, Shadow Step, Vanish, Mutilate, Bear Trap): the Thief; the
 * Survivalist sets the Bear Trap too.</li>
 * <li>Its warrior book (Shout, Charge, Last Stand, Mortal Strike, Throw, Throw Net): the Warlord, the Brute and the Rider;
 * the Survivalist throws the net too.</li>
 * </ul>
 * Spell Engine asks before every cast on both sides (the class is synced to its player), so the client never even sends
 * a cast it would refuse, and the player is told on the action bar which class casts it. Spells of any other mod are
 * left alone.
 */
public final class RpgSeriesGate {
	private static final String MOD_ID = "spell_engine";
	/** Rogues &amp; Warriors' rogue book; everything else of {@code rogues:} is the warrior book. */
	static final Set<String> ROGUE_SPELLS = Set.of("slice_and_dice", "shock_powder", "shadow_step", "vanish", "mutilate", "bear_trap");
	/** Spells the Survivalist casts besides their own class's: the trapper's two. */
	static final Set<String> SURVIVALIST_SPELLS = Set.of("bear_trap", "throw_net");
	/** Ticks between two "only a ... casts this" lines for the same player. */
	static final int TELL_EVERY = 40;

	private static final Map<Player, Long> TOLD = new WeakHashMap<>();

	private RpgSeriesGate() {
	}

	public static void init() {
		if (FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			Hooks.init();
		}
	}

	/**
	 * The classes that cast {@code spell}, or null when it isn't one of the RPG Series' (anyone may cast it). Empty for a
	 * namespace of theirs no class takes.
	 */
	static @Nullable Set<PlayerClass> casters(Identifier spell) {
		String path = spell.getPath();
		return switch (spell.getNamespace()) {
			case "wizards" -> EnumSet.of(PlayerClass.MAGE);
			case "archers" -> EnumSet.of(PlayerClass.ARCHER, PlayerClass.MARKSMAN);
			case "rogues" -> {
				EnumSet<PlayerClass> classes = ROGUE_SPELLS.contains(path) ? EnumSet.of(PlayerClass.THIEF)
					: EnumSet.of(PlayerClass.WARLORD, PlayerClass.BRUTE, PlayerClass.RIDER);
				if (SURVIVALIST_SPELLS.contains(path)) {
					classes.add(PlayerClass.SURVIVALIST);
				}
				yield classes;
			}
			default -> null;
		};
	}

	/** Whether {@code player} may cast {@code spell}: creative players always may. */
	static boolean mayCast(Player player, Identifier spell) {
		Set<PlayerClass> casters = casters(spell);
		if (casters == null || player.isCreative()) {
			return true;
		}
		PlayerClass cls = Classes.classOf(player);
		return cls != null && casters.contains(cls);
	}

	/** Tells the player (client side, where they pressed) which class casts it, at most every {@link #TELL_EVERY} ticks. */
	private static void tell(Player player, Identifier spell) {
		if (!player.level().isClientSide()) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = TOLD.get(player);
		if (last != null && now - last < TELL_EVERY) {
			return;
		}
		TOLD.put(player, now);
		Set<PlayerClass> casters = casters(spell);
		if (casters == null || casters.isEmpty()) {
			return;
		}
		Component names = null;
		for (PlayerClass cls : casters) {
			Component name = Component.translatable("class.jaymoskingdoms." + cls.key());
			names = names == null ? name : Component.translatable("message.jaymoskingdoms.rpg_gate.or", names, name);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rpg_gate.only", names).withStyle(ChatFormatting.GRAY));
	}

	/** Kept apart so Spell Engine's classes load only when it's there. */
	private static final class Hooks {
		static void init() {
			SpellEvents.CASTING_ATTEMPT.PRE.register(args -> {
				Identifier spell = args.spell().unwrapKey().map(key -> key.identifier()).orElse(null);
				if (spell == null || mayCast(args.caster(), spell)) {
					return null;
				}
				tell(args.caster(), spell);
				return SpellCast.Attempt.none();
			});
		}
	}
}
