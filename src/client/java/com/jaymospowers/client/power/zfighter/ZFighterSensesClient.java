package com.jaymospowers.client.power.zfighter;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * (0.17.42, Andy's NOEA picks) The client hub of the senses patch: ki sense ({@link ZFighterKiSenseClient}), the Scouter's
 * readout ({@link ZFighterScouterClient}), Instant Teleport's list ({@link ZFighterTeleportScreen}), impact frames
 * ({@link ZFighterImpactFramesClient}), banking ({@link ZFighterBank}) and the air burst ({@link ZFighterAirBurstClient}).
 * {@link ZFighterPowerClient} makes one call to {@link #init} and one to {@link #tick}; the beam clash timing game is
 * {@code client/BeamClashTimingClient}.
 */
public final class ZFighterSensesClient {
	private ZFighterSensesClient() {
	}

	/** Called once from {@link ZFighterPowerClient#init}. */
	static void init() {
		ZFighterKiSenseClient.init();
		ZFighterScouterClient.init();
		ZFighterTeleportScreen.register();
		ZFighterImpactFramesClient.init();
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterBank::tick);
	}

	/** Every client tick of the power, right after the flight's own. */
	static void tick(Minecraft minecraft, LocalPlayer player) {
		ZFighterAirBurstClient.tick(minecraft, player);
	}
}
