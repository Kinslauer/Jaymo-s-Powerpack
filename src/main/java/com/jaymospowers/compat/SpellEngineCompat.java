package com.jaymospowers.compat;

import com.jaymospowers.baubles.Baubles;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.compat.container.ContainerCompat;
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
 */
public final class SpellEngineCompat {
	private static final String MOD_ID = "spell_engine";
	/** The source's name in Spell Engine's list. */
	static final String SOURCE = "jaymospowers_baubles";

	private SpellEngineCompat() {
	}

	public static void init() {
		if (FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			Hooks.init();
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
		}
	}
}
