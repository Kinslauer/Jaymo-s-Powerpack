package com.jaymoskingdoms.client.compat;

import com.jaymoskingdoms.Introductions;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * (0.7.187) Jade's panel on a player or MCA villager you haven't been introduced to ({@link Introductions}) shows only
 * what you can see: what they are ("Stranger", "Villager"), never their name or trade, and their health only once
 * you've struck them. Done once the panel is gathered, so it holds whatever each player's Jade settings say. Only
 * loaded by Jade (the {@code jade} entrypoint). (0.7.474) A goblin or an orc is named as one ("Orc", not MCA's "Male
 * Villager") and shown as ours, not Minecraft Comes Alive's.
 */
@WailaPlugin
public final class JadeIntroductions implements IWailaPlugin {
	@Override
	public void registerClient(IWailaClientRegistration registration) {
		// the callback lives in its own class: Jade loads this one on the server too (see JadeIntroductionsClient)
		JadeIntroductionsClient.register(registration);
	}
}
