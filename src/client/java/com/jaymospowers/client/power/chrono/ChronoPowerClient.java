package com.jaymospowers.client.power.chrono;

import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.power.chrono.ChronoPower;

/** Chronokinesis, client side. STUB: the agent building this power replaces this file. */
public final class ChronoPowerClient implements PowerClient {
	public static final ChronoPowerClient INSTANCE = new ChronoPowerClient();

	private ChronoPowerClient() {
	}

	/** Called once from {@link com.jaymospowers.client.JaymosPowersClient}: renderers, client packets, the PowerClient. */
	public static void init() {
		PowerClients.register(ChronoPower.ID, INSTANCE);
	}
}
