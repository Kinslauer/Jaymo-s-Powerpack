package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The server's say over how bodies come apart. {@code jaymoskitchen:pinata} (off) turns every body that bursts into
 * sweets, and bodies burst into sweets whatever it says in Alex's Caves' Candy Cavity (found by its biome id, so
 * nothing here needs that mod or Jaymo's Kingdoms). {@code jaymoskitchen:ragdoll_cap} is how many corpses' ragdolls
 * may be moving at once ({@link RagdollBudget}); the oldest and farthest settle first.
 */
public final class GoreRules {
	/** The Candy Cavity's biome id (Alex's Caves), the same biome Jaymo's Kingdoms' Rubber-folk call home. */
	static final ResourceKey<Biome> CANDY_CAVITY = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("alexscaves", "candy_cavity"));
	/** Most corpse ragdolls moving at once, by default, and the least and most the rule takes. */
	static final int RAGDOLL_CAP = 24, RAGDOLL_CAP_MIN = 1, RAGDOLL_CAP_MAX = 512;

	public static GameRule<Boolean> PINATA;
	public static GameRule<Integer> RAGDOLL_CAP_RULE;

	private GoreRules() {
	}

	public static void register() {
		PINATA = GameRuleBuilder.forBoolean(false).category(GameRuleCategory.MOBS).buildAndRegister(JaymosKitchen.id("pinata"));
		RAGDOLL_CAP_RULE = GameRuleBuilder.forInteger(RAGDOLL_CAP).range(RAGDOLL_CAP_MIN, RAGDOLL_CAP_MAX)
			.category(GameRuleCategory.MOBS).buildAndRegister(JaymosKitchen.id("ragdoll_cap"));
		Strung.register();
	}

	/** Whether a body bursting at {@code at} bursts into sweets: the rule is on, or it's in the Candy Cavity. */
	public static boolean pinata(Level level, BlockPos at) {
		if (level instanceof net.minecraft.server.level.ServerLevel server && server.getGameRules().get(PINATA)) {
			return true;
		}
		return level.getBiome(at).is(CANDY_CAVITY);
	}

	/** Most corpse ragdolls that may be moving at once in {@code level}. */
	static int cap(net.minecraft.server.level.ServerLevel level) {
		return level.getGameRules().get(RAGDOLL_CAP_RULE);
	}
}
