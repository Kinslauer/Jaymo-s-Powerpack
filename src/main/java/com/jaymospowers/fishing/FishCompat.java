package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Track C: fish from other mods count (True Fishing's idea, our own code). Naturalist's bass and catfish, an
 * Aquaculture-style mod's pike or perch, any mod's fish: a hook takes them like ours. One whose name says what it is
 * ({@link #NAMES}: a "catfish", a "trout", a "lobster") is fished as that species of ours and lands as ours; any other
 * fish (anything that is a fish to the game, or in {@code #jaymospowers:hookable_fish}) is {@link Species#OTHER}, and
 * landing it gives whatever its own mod drops for it.
 */
public final class FishCompat {
	/** Name fragments of other mods' fish, and which of our species each is (checked in order: "catfish" before "cat"). */
	private static final List<Map.Entry<String, Species>> NAMES = List.of(
		Map.entry("catfish", Species.CATFISH), Map.entry("wels", Species.CATFISH), Map.entry("pike", Species.PIKE),
		Map.entry("muskellunge", Species.PIKE), Map.entry("perch", Species.PERCH), Map.entry("bluegill", Species.PERCH),
		Map.entry("zander", Species.ZANDER), Map.entry("walleye", Species.ZANDER), Map.entry("bream", Species.BREAM),
		Map.entry("tench", Species.TENCH), Map.entry("carp", Species.CARP), Map.entry("eel", Species.EEL),
		Map.entry("trout", Species.TROUT), Map.entry("chub", Species.CHUB), Map.entry("char", Species.CHAR),
		Map.entry("grayling", Species.GRAYLING), Map.entry("whitefish", Species.WHITEFISH), Map.entry("burbot", Species.BURBOT),
		Map.entry("mackerel", Species.MACKEREL), Map.entry("herring", Species.HERRING), Map.entry("pollock", Species.POLLOCK),
		Map.entry("bass", Species.SEA_BASS), Map.entry("halibut", Species.HALIBUT), Map.entry("flounder", Species.HALIBUT),
		Map.entry("swordfish", Species.SWORDFISH), Map.entry("marlin", Species.SWORDFISH), Map.entry("tuna", Species.TUNA),
		Map.entry("salmon", Species.SALMON), Map.entry("cod", Species.COD), Map.entry("lobster", Species.LOBSTER),
		Map.entry("crayfish", Species.CRAYFISH), Map.entry("crab", Species.CRAB), Map.entry("shrimp", Species.SHRIMP),
		Map.entry("prawn", Species.SHRIMP), Map.entry("jelly", Species.JELLYFISH));

	/** Other mods' creatures a hook should take as fish, beyond those the game already knows as fish. */
	public static final TagKey<EntityType<?>> HOOKABLE = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("hookable_fish"));

	private FishCompat() {
	}

	/** Which species another mod's {@code e} is fished as, or null if it's no fish (ours and vanilla's are named elsewhere). */
	static @Nullable Species speciesOf(LivingEntity e) {
		boolean fish = e instanceof AbstractFish || e.getType().builtInRegistryHolder().is(HOOKABLE);
		if (!fish) {
			return null;
		}
		String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
		if (path.contains("tadpole")) {
			return null;
		}
		for (Map.Entry<String, Species> n : NAMES) {
			if (path.contains(n.getKey())) {
				return n.getValue();
			}
		}
		return Species.OTHER;
	}

	/** Whether {@code e} is another mod's fish that lands as its own drops (no species of ours to give). */
	static boolean ownDrops(LivingEntity e) {
		return !(e instanceof WildFish) && !(e instanceof Critter) && speciesOf(e) == Species.OTHER;
	}

	/** What another mod's fish {@code e} drops, landed by {@code player} (its own loot table). */
	static List<ItemStack> drops(ServerLevel level, LivingEntity e, ServerPlayer player) {
		List<ItemStack> out = new ArrayList<>();
		e.getLootTable().ifPresent(table -> e.dropFromLootTable(level, level.damageSources().playerAttack(player), true, table, out::add));
		return out;
	}
}
