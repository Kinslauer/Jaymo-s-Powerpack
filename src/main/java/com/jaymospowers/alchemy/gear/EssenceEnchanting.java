package com.jaymospowers.alchemy.gear;

import com.jaymospowers.alchemy.Corruption;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.flux.FluxEffects;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Essence enchanting: at the enchanting table ({@link com.jaymospowers.enchanting.Enchanting}) every enchantment can also be
 * paid for in essence, drawn from the phials the player carries, instead of in items. Each enchantment is made of two
 * essences ({@link #makeup}); the price is {@link #PER_LEVEL} of each for every level being granted. It is quick and
 * clean-looking, but the table leaves flux and a little lingering corruption behind ({@link #aftermath}).
 * The {@link AlchemyGear#ESSENCE_ENCHANTING research} must be known.
 */
public final class EssenceEnchanting {
	/** Of each of the enchantment's two essences, per level granted. */
	public static final int PER_LEVEL = 3;
	/** Flux left in the table's chunk, per point of essence spent (rounded down, at least {@link #MIN_FLUX}). */
	public static final double FLUX_PER_ESSENCE = 0.25;
	public static final int MIN_FLUX = 2;
	/** Lingering corruption from one essence enchanting: this, plus one more from level {@link #HEAVY_LEVEL} up. */
	public static final int CORRUPTION = 1, HEAVY_LEVEL = 3;
	/** Menu button ids at or above this mean "pay in essence"; the enchantment is the id minus this. */
	public static final int BUTTON_OFFSET = 1 << 16;
	/** What an enchantment this table doesn't know is made of. */
	private static final Essence[] FALLBACK = {Essence.ARCANA, Essence.MIND};

	private static final Map<String, Essence[]> MAKEUP = new HashMap<>();

	static {
		// protections: salt (keeping things as they are) and what they guard against
		add("protection", Essence.SALT, Essence.EARTH);
		add("fire_protection", Essence.SALT, Essence.FIRE);
		add("feather_falling", Essence.SALT, Essence.AIR);
		add("blast_protection", Essence.SALT, Essence.SPARK);
		add("projectile_protection", Essence.SALT, Essence.MOTION);
		add("respiration", Essence.AIR, Essence.LIFE);
		add("aqua_affinity", Essence.WATER, Essence.MIND);
		add("thorns", Essence.PLANT, Essence.WAR);
		add("depth_strider", Essence.WATER, Essence.MOTION);
		add("frost_walker", Essence.FROST, Essence.MOTION);
		add("soul_speed", Essence.SPIRIT, Essence.MOTION);
		add("swift_sneak", Essence.SHADOW, Essence.MOTION);
		// weapons
		add("sharpness", Essence.METAL, Essence.WAR);
		add("smite", Essence.LIGHT, Essence.WAR);
		add("bane_of_arthropods", Essence.VENOM, Essence.WAR);
		add("knockback", Essence.MOTION, Essence.WAR);
		add("fire_aspect", Essence.FIRE, Essence.WAR);
		add("looting", Essence.WEALTH, Essence.BEAST);
		add("sweeping_edge", Essence.AIR, Essence.WAR);
		add("density", Essence.EARTH, Essence.WAR);
		add("breach", Essence.METAL, Essence.EARTH);
		add("wind_burst", Essence.AIR, Essence.SPARK);
		add("lunge", Essence.MOTION, Essence.WAR);
		// tools
		add("efficiency", Essence.CRAFT, Essence.MOTION);
		add("silk_touch", Essence.CRYSTAL, Essence.CRAFT);
		add("unbreaking", Essence.SALT, Essence.METAL);
		add("fortune", Essence.WEALTH, Essence.EARTH);
		add("mending", Essence.LIFE, Essence.ARCANA);
		// bows, crossbows, tridents, fishing
		add("power", Essence.WAR, Essence.AIR);
		add("punch", Essence.MOTION, Essence.AIR);
		add("flame", Essence.FIRE, Essence.SPARK);
		add("infinity", Essence.ARCANA, Essence.SALT);
		add("multishot", Essence.MOTION, Essence.CRAFT);
		add("quick_charge", Essence.SPARK, Essence.MOTION);
		add("piercing", Essence.METAL, Essence.MOTION);
		add("loyalty", Essence.SPIRIT, Essence.BEAST);
		add("impaling", Essence.WATER, Essence.WAR);
		add("riptide", Essence.WATER, Essence.SPARK);
		add("channeling", Essence.SPARK, Essence.ARCANA);
		add("luck_of_the_sea", Essence.WEALTH, Essence.WATER);
		add("lure", Essence.BEAST, Essence.WATER);
		// the curses
		add("binding_curse", Essence.SHADOW, Essence.SALT);
		add("vanishing_curse", Essence.SHADOW, Essence.MERCURY);
	}

	private EssenceEnchanting() {
	}

	private static void add(String id, Essence a, Essence b) {
		MAKEUP.put(id, new Essence[] {a, b});
	}

	/** The two essences an enchantment is made of (a fallback pair for any this table has no entry for). */
	public static Essence[] makeup(Holder<Enchantment> enchantment) {
		String path = enchantment.unwrapKey().filter(k -> k.identifier().getNamespace().equals("minecraft")).map(k -> k.identifier().getPath()).orElse("");
		return MAKEUP.getOrDefault(path, FALLBACK);
	}

	/** What granting {@code level} of the enchantment costs in essence. */
	public static EssenceList cost(Holder<Enchantment> enchantment, int level) {
		Essence[] pair = makeup(enchantment);
		int each = Math.max(1, level) * PER_LEVEL;
		return EssenceList.of(pair[0], each).plus(pair[1], each);
	}

	/** Whether the player may pay in essence at all (knows {@code essence_enchanting}). */
	public static boolean available(Player player) {
		return Knowledge.knows(player, AlchemyGear.ESSENCE_ENCHANTING);
	}

	/** What the table leaves after an essence payment: flux in its chunk, and corruption in whoever paid. */
	public static void aftermath(ServerPlayer player, ServerLevel level, BlockPos table, EssenceList cost, int enchantLevel) {
		FluxEffects.burst(level, table, Math.max(MIN_FLUX, (int) (cost.total() * FLUX_PER_ESSENCE)));
		Corruption.add(player, Corruption.Kind.LINGERING, CORRUPTION + (enchantLevel >= HEAVY_LEVEL ? 1 : 0));
	}
}
