package com.jaymoskingdoms.tinker;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The abilities a Tinkerer can study and copy in brass and spring ({@link MimicStudy}), each with the gadget that mimics it
 * ({@link MimicGadgets}). An ability is seen in the players whose power it belongs to (Jaymo's Powers marks a player using
 * their power, {@code jaymospowers_odd:power/<id>}) and in the creatures that have it ({@link #mobs}, entity id paths).
 *
 * <p>Every gadget that shoots or throws spends something real ({@link #fuel}): the knowledge is copied, the stuff is not.
 * The guard and the claws wear out instead ({@link #durability}).
 */
public enum Mimic {
	// ---- the powers ----
	LIFT("lift_rig", List.of("gravity"), List.of("shulker"), cost(Items.IRON_INGOT, 3, Items.REDSTONE, 4, Items.COMPASS, 1),
		fuel(1, Items.REDSTONE), 0, 0, 1),
	GUST("gust_bellows", List.of("wind"), List.of("breeze"), cost(Items.LEATHER, 4, Items.IRON_INGOT, 1, Items.GUNPOWDER, 2),
		fuel(1, Items.GUNPOWDER), 20, 0, 1),
	ZIP("grapple_spinner", List.of("web"), List.of(), cost(Items.IRON_INGOT, 2, Items.STRING, 8, Items.TRIPWIRE_HOOK, 1),
		fuel(2, Items.STRING), 30, 0, 1),
	REWIND("rewind_watch", List.of("chrono"), List.of(), cost(Items.GOLD_INGOT, 4, Items.CLOCK, 1, Items.REDSTONE, 2),
		fuel(3, Items.GOLD_NUGGET), 400, 0, 1),
	FROST("frost_projector", List.of("cryo"), List.of(), cost(Items.IRON_INGOT, 3, Items.PACKED_ICE, 2, Items.COPPER_INGOT, 2),
		fuel(1, Items.SNOWBALL), 0, 0, 1),
	SMOTHER("smother_pump", List.of("smoke"), List.of(), cost(Items.LEATHER, 2, Items.IRON_INGOT, 2, Items.CHARCOAL, 4),
		fuel(1, Items.CHARCOAL, Items.COAL), 60, 0, 1),
	SONIC("sonic_horn", List.of("resonance"), List.of("warden"), cost(Items.COPPER_INGOT, 4, Items.AMETHYST_SHARD, 4, Items.LEATHER, 1),
		fuel(1, Items.AMETHYST_SHARD), 60, 0, 1),
	PARRY("parry_gauntlet", List.of("darktide"), List.of(), cost(Items.IRON_INGOT, 4, Items.LEATHER, 2, Items.INK_SAC, 2),
		null, 0, 250, 1),
	CHAIN("chain_hook", List.of("arsenal"), List.of(), cost(Items.IRON_CHAIN, 2, Items.IRON_INGOT, 2, Items.TRIPWIRE_HOOK, 1),
		fuel(1, Items.IRON_NUGGET), 30, 0, 1),
	GROWTH("growth_band", List.of("xenoform"), List.of(), cost(Items.GOLD_INGOT, 2, Items.GLOWSTONE_DUST, 4, Items.SLIME_BALL, 2),
		fuel(2, Items.GLOWSTONE_DUST), 1200, 0, 1),
	KI("ki_blaster", List.of("zfighter"), List.of(), cost(Items.COPPER_INGOT, 3, Items.REDSTONE, 6, Items.GOLD_INGOT, 1),
		fuel(1, Items.REDSTONE), 20, 0, 1),
	// ---- the creatures ----
	BLINK("blink_coil", List.of(), List.of("enderman"), cost(Items.COPPER_INGOT, 3, Items.ENDER_PEARL, 2, Items.CHORUS_FRUIT, 4),
		fuel(1, Items.CHORUS_FRUIT, Items.POPPED_CHORUS_FRUIT), 40, 0, 1),
	CLIMB("climbing_claws", List.of(), List.of("spider", "cave_spider"), cost(Items.IRON_INGOT, 3, Items.LEATHER, 2, Items.STRING, 4),
		null, 0, 300, 1),
	FIREBURST("flame_spitter", List.of(), List.of("blaze"), cost(Items.IRON_INGOT, 3, Items.BLAZE_ROD, 1, Items.COPPER_INGOT, 2),
		fuel(1, Items.BLAZE_POWDER), 20, 0, 1),
	FIREBALL("bellows_cannon", List.of(), List.of("ghast"), cost(Items.IRON_INGOT, 5, Items.LEATHER, 2, Items.BLAZE_POWDER, 2),
		fuel(1, Items.FIRE_CHARGE), 40, 0, 1),
	BEAM("beam_lens", List.of(), List.of("guardian", "elder_guardian"), cost(Items.PRISMARINE_CRYSTALS, 4, Items.COPPER_INGOT, 2, Items.GLASS, 2),
		fuel(1, Items.PRISMARINE_CRYSTALS), 20, 0, 1),
	FANGS("jaw_rake", List.of(), List.of("evoker"), cost(Items.IRON_INGOT, 3, Items.BONE, 6),
		fuel(2, Items.BONE), 40, 0, 1),
	BLAST("hissing_charge", List.of(), List.of("creeper"), cost(Items.GUNPOWDER, 3, Items.PAPER, 1, Items.STRING, 1),
		null, 10, 0, 2),
	POTION("potion_sling", List.of(), List.of("witch"), cost(Items.LEATHER, 2, Items.STRING, 4, Items.STICK, 2),
		null, 20, 0, 1);

	/** What it costs to build, item by item. */
	public record Part(Item item, int count) {
	}

	/** What one use spends: {@code count} of any one of {@code items}. */
	public record Fuel(int count, List<Item> items) {
	}

	/** The gadget's item id path (in {@code jaymoskingdoms}), and its lang key part. */
	public final String gadget;
	/** The Jaymo's Powers powers (id paths) it is seen in. */
	public final List<String> powers;
	/** The creatures (entity id paths) it is seen in. */
	public final List<String> mobs;
	/** What building one takes, at a bench. */
	public final List<Part> cost;
	/** What a use spends (null: nothing but the gadget's own wear, or the charge itself, or the potion it slings). */
	public final @Nullable Fuel fuel;
	/** The pause after a use (ticks), its durability (0: none), and how many one building makes. */
	public final int cooldown, durability, makes;

	Mimic(String gadget, List<String> powers, List<String> mobs, List<Part> cost, @Nullable Fuel fuel, int cooldown, int durability, int makes) {
		this.gadget = gadget;
		this.powers = powers;
		this.mobs = mobs;
		this.cost = cost;
		this.fuel = fuel;
		this.cooldown = cooldown;
		this.durability = durability;
		this.makes = makes;
	}

	/** Its save key. */
	public String key() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public static @Nullable Mimic byKey(String key) {
		for (Mimic m : values()) {
			if (m.key().equals(key)) {
				return m;
			}
		}
		return null;
	}

	/** The ability whose use marks a player of power {@code power} (an id path), or null. */
	public static @Nullable Mimic ofPower(String power) {
		for (Mimic m : values()) {
			if (m.powers.contains(power)) {
				return m;
			}
		}
		return null;
	}

	private static List<Part> cost(Object... pairs) {
		java.util.ArrayList<Part> out = new java.util.ArrayList<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			out.add(new Part((Item)pairs[i], (Integer)pairs[i + 1]));
		}
		return List.copyOf(out);
	}

	private static Fuel fuel(int count, Item... items) {
		return new Fuel(count, List.of(items));
	}
}
