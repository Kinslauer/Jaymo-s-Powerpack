package com.jaymospowers.compat;

import com.jaymospowers.baubles.Baubles;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import com.jaymospowers.core.wounds.Downed;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.compat.container.ContainerCompat;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.container.SpellContainerSource;

/**
 * (RPG Series) Spell Engine (optional; GPL-3.0, shipped unchanged with Zsolt Molnar's Wizards, Archers and Rogues &amp;
 * Warriors). Spell Engine looks for quivers and spell trinkets in Trinkets' slots; the pack has our own {@link Baubles}
 * instead, so this hands it what's worn there:
 * <ul>
 * <li>as a spell source after the main hand (rings, necklaces and charms that carry spells);</li>
 * <li>as an ammo container, so a quiver worn in the body slot feeds bows and arrow spells.</li>
 * </ul>
 * The slots' tags take Spell Engine's own item tags. The worn list is synced to its wearer, so the client sees it too.
 * Spells themselves are learned on Jaymo's Kingdoms' skill web or from a trainer, never from books (its Learnables), so
 * there's no slot for spell books.
 * <p>(0.20.58) Impale throws the spear itself, which lands to be picked up again ({@link SpellThrows}).
 * <p>Holding the big axes' Whirlwind too long knocks the spinner out ({@link com.jaymospowers.core.wounds.SpinDizzy}):
 * this says who is spinning, stops the spin, and refuses a new one to anyone down.
 */
public final class SpellEngineCompat {
	private static final String MOD_ID = "spell_engine";
	/** The source's name in Spell Engine's list. */
	static final String SOURCE = "jaymospowers_baubles";
	/** The RPG Series' Whirlwind: the held spin of the double axe and the other big axes. */
	static final Identifier WHIRLWIND = Identifier.fromNamespaceAndPath("rpg_series", "whirlwind");
	private static boolean loaded;

	private SpellEngineCompat() {
	}

	public static void init() {
		if (FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			loaded = true;
			Hooks.init();
		}
	}

	/** Whether {@code entity} is holding Whirlwind's spin just now (false without Spell Engine). Either side. */
	public static boolean spinning(LivingEntity entity) {
		return loaded && Hooks.spinning(entity);
	}

	/** Stops {@code player}'s Whirlwind where it is (no last blow, no cooldown). Server side. */
	public static void stopSpin(Player player) {
		if (loaded) {
			Hooks.stopSpin(player);
		}
	}

	/** What a player wears, empty slots left out (either side). */
	static List<ItemStack> worn(Player player) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack stack : Baubles.worn(player)) {
			if (!stack.isEmpty()) {
				out.add(stack);
			}
		}
		return out;
	}

	/** Kept apart so Spell Engine's classes load only when it's there. */
	private static final class Hooks {
		static void init() {
			SpellContainerSource.addItemSource(SpellContainerSource.ItemEntry.of(SOURCE, (player, name) -> worn(player)),
				SpellContainerSource.MAIN_HAND.name());
			ContainerCompat.addProvider(SpellEngineCompat::worn);
			SpellThrows.init();
			// no spinning up again while down (knocked out by the spin or anything else)
			SpellEvents.CASTING_ATTEMPT.PRE.register(args -> WHIRLWIND.equals(idOf(args.spell())) && Downed.isDowned(args.caster())
				? SpellCast.Attempt.none() : null);
		}

		static boolean spinning(LivingEntity entity) {
			if (!(entity instanceof SpellCaster.Entity caster)) {
				return false;
			}
			SpellCast.Process process = caster.getSpellCastProcess();
			return process != null && WHIRLWIND.equals(idOf(process.spell()));
		}

		static void stopSpin(Player player) {
			if (player instanceof SpellCaster.Player caster && caster.getInteractor() != null && spinning(player)) {
				caster.getInteractor().requestClear();
			}
		}

		private static @org.jspecify.annotations.Nullable Identifier idOf(net.minecraft.core.Holder<net.spell_engine.api.spell.Spell> spell) {
			return spell.unwrapKey().map(key -> key.identifier()).orElse(null);
		}
	}
}
