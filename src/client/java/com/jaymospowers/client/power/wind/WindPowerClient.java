package com.jaymospowers.client.power.wind;

import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.power.wind.WindPower;

/** Wind Control, client side. STUB: the agent building this power replaces this file. */
public final class WindPowerClient implements PowerClient {
	public static final WindPowerClient INSTANCE = new WindPowerClient();

	private WindPowerClient() {
	}

	/** Called once from {@link com.jaymospowers.client.JaymosPowersClient}: renderers, client packets, the PowerClient. */
	public static void init() {
		PowerClients.register(WindPower.ID, INSTANCE);
	}
}
