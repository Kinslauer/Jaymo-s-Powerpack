package com.jaymospowers.power.zfighter;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.17.56, Andy's NOEA picks) The one place the shared Z Fighter code calls into for the lineages' extras, so those files
 * only carry one-line hooks: {@link #tick} (from {@link ZFighterPower#tick}), {@link #cover} (from {@link ZFighterPower#spend}),
 * and the classes' own hooks for damage ({@link ZFighterFrostReserve#harden}), maximum ki and fists
 * ({@link ZFighterVerdantMerge#ki}, {@link ZFighterWarriorComeback#bonus}). The extras themselves: Earthling
 * {@link ZFighterEarthlingRace}, Verdant {@link ZFighterVerdantMerge} and {@link ZFighterVerdantWater}, Frost-born
 * {@link ZFighterFrostReserve}, Warrior-born {@link ZFighterWarriorComeback}. A Divine-born (Kingdoms heritage {@code divine})
 * is hidden from ki sense and the scouter ({@link ZFighterKiSense#hidesKi}) and (0.17.145) its ki flows easier
 * ({@link ZFighterDivineGrace}); a Rubber-folk's blows and falls bounce off ({@link ZFighterRubberBody}). Both, like the
 * Bio-built, count as Earthlings ({@link ZFighterLineage#fromHeritage}), with (0.17.138) the Bio-built's forms' strengths on
 * top ({@link ZFighterBioForms}).
 */
final class ZFighterRaces {
	/** The merged fists and health are put back on this often (ticks). */
	static final int MERGE_EVERY = 20;

	private ZFighterRaces() {
	}

	static void init() {
		ZFighterVerdantMerge.init();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ZFighterEarthlingRace.clear();
			ZFighterVerdantMerge.clear();
			ZFighterFrostReserve.clear();
			ZFighterWarriorComeback.clear();
		});
	}

	private static void forget(ServerPlayer player) {
		ZFighterEarthlingRace.forget(player);
		ZFighterVerdantMerge.forget(player);
		ZFighterFrostReserve.forget(player);
		ZFighterWarriorComeback.forget(player);
	}

	/** Every tick, per Z Fighter, before the ki is clamped. */
	static void tick(ServerPlayer player, ZFighterPower.Live live, int max) {
		if (player.tickCount % MERGE_EVERY == 0) {
			ZFighterVerdantMerge.attributes(player);
		}
		ZFighterBioForms.tick(player, live, max);
		switch (ZFighterLineage.of(player)) {
			case ZFighterLineage.EARTHLING -> ZFighterEarthlingRace.tick(player, live);
			case ZFighterLineage.VERDANT -> ZFighterVerdantWater.tick(player, live);
			case ZFighterLineage.FROST -> ZFighterFrostReserve.tick(player, live, max);
			case ZFighterLineage.WARRIOR -> ZFighterWarriorComeback.tick(player, live);
			default -> {
			}
		}
	}

	/**
	 * A technique costs {@code cost} and he has less ki: the Frost-born's reserve, then the Earthling's blood, may make up the
	 * difference (his ki is topped up to {@code cost}). False: nothing could.
	 */
	static boolean cover(ServerPlayer player, ZFighterPower.Live live, float cost) {
		return switch (ZFighterLineage.of(player)) {
			case ZFighterLineage.FROST -> ZFighterFrostReserve.cover(player, live, cost);
			case ZFighterLineage.EARTHLING -> ZFighterEarthlingRace.blood(player, live, cost);
			default -> false;
		};
	}
}
