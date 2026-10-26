package com.jaymospowers.client.alchemy;

/** Client side of {@link com.jaymospowers.alchemy.Alchemy}: each part's renderers, screens and effects. */
public final class AlchemyClient {
	private AlchemyClient() {
	}

	public static void init() {
		CodexClient.init();
		CrucibleClient.init();
		FluxClient.init();
		InfusionClient.init();
		GearClient.init();
	}
}
