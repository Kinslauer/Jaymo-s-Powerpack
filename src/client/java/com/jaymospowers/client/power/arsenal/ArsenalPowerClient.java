package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.power.arsenal.ArsenalPower;

/** Pocket Arsenal, client side. STUB: the agent building this power replaces this file. */
public final class ArsenalPowerClient implements PowerClient {
	public static final ArsenalPowerClient INSTANCE = new ArsenalPowerClient();

	private ArsenalPowerClient() {
	}

	/** Called once from {@link com.jaymospowers.client.JaymosPowersClient}: renderers, client packets, the PowerClient. */
	public static void init() {
		PowerClients.register(ArsenalPower.ID, INSTANCE);
	}
}
