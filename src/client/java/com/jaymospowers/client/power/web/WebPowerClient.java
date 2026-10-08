package com.jaymospowers.client.power.web;

import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.power.web.WebPower;

/** Web Slinger, client side. STUB: the agent building this power replaces this file. */
public final class WebPowerClient implements PowerClient {
	public static final WebPowerClient INSTANCE = new WebPowerClient();

	private WebPowerClient() {
	}

	/** Called once from {@link com.jaymospowers.client.JaymosPowersClient}: renderers, client packets, the PowerClient. */
	public static void init() {
		PowerClients.register(WebPower.ID, INSTANCE);
	}
}
