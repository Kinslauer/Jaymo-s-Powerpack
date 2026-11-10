package com.jaymoskingdoms.client.astronomy;

import com.jaymoskingdoms.astronomy.ArmillaryNet;
import com.jaymoskingdoms.astronomy.AstronomersJournal;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The Astronomer's Journal's client side: opens the book ({@link AstronomersJournalScreen}) and shows a reading's outcome on the armillary screen. */
public final class AstronomersJournalClient {
	private AstronomersJournalClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(AstronomersJournal.Open.TYPE,
			(payload, context) -> context.client().gui.setScreen(new AstronomersJournalScreen()));
		ClientPlayNetworking.registerGlobalReceiver(ArmillaryNet.Reading.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof ArmillaryScreen screen) {
				screen.reading(payload);
			}
		});
	}
}
