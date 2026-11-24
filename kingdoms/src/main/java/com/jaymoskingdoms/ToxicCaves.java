package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.biome.Biome;

/**
 * The Toxic Caves' air (Andy, 2026-11-24: the Obsidian-hided Stoneborn and the legendary Warrior-born "can be the only ones
 * that can tolerate the biome"; Lore.md, "The sick caves and who can stand them"). Anyone else breathing it there is kept
 * Irradiated (Alex's Caves' sickness, {@link #DOSE_TICKS} at a time, refreshed every {@link #EVERY}) unless sealed in a
 * full hazmat suit, the future's gear. The Obsidian-hided ({@link HeritageVariants.Variant#OBSIDIAN}) were made by it and
 * shrug off Irradiated wherever it comes from. The legendary take it as strength, which Jaymo's Powers handles
 * ({@code ZFighterRadiance}); here they are only left out of the dose. Only with Alex's Caves loaded.
 */
public final class ToxicCaves {
	static final ResourceKey<Biome> TOXIC_CAVES = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(AlexsCavesCompat.MOD_ID, "toxic_caves"));
	static final Identifier IRRADIATED = Identifier.fromNamespaceAndPath(AlexsCavesCompat.MOD_ID, "irradiated");
	static final String[] HAZMAT = {"hazmat_mask", "hazmat_chestplate", "hazmat_leggings", "hazmat_boots"};
	static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Ticks between checks, and how long each dose of the air lasts. */
	static final int EVERY = 40, DOSE_TICKS = 100;

	private ToxicCaves() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ToxicCaves::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		Holder<MobEffect> sick = BuiltInRegistries.MOB_EFFECT.get(IRRADIATED).map(h -> (Holder<MobEffect>)h).orElse(null);
		if (sick == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (HeritageVariants.of(player) == HeritageVariants.Variant.OBSIDIAN) {
				player.removeEffect(sick);
				continue;
			}
			if (player.isCreative() || player.isSpectator() || !player.isAlive() || PowersCompat.legendary(player) || sealed(player)
				|| !player.level().getBiome(player.blockPosition()).is(TOXIC_CAVES)) {
				continue;
			}
			MobEffectInstance had = player.getEffect(sick);
			if (had == null || had.getDuration() < DOSE_TICKS - EVERY) {
				player.addEffect(new MobEffectInstance(sick, DOSE_TICKS, had == null ? 0 : had.getAmplifier()));
			}
		}
	}

	/** In all four pieces of Alex's hazmat suit. */
	private static boolean sealed(ServerPlayer player) {
		for (int i = 0; i < SLOTS.length; i++) {
			if (!AlexsCavesCompat.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(player.getItemBySlot(SLOTS[i]).getItem()).getNamespace())
				|| !HAZMAT[i].equals(BuiltInRegistries.ITEM.getKey(player.getItemBySlot(SLOTS[i]).getItem()).getPath())) {
				return false;
			}
		}
		return true;
	}
}
