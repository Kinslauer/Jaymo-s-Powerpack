package com.jaymoskingdoms.client.compat;

import com.jaymoskingdoms.Introductions;
import net.minecraft.world.entity.Entity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.theme.IThemeHelper;

/**
 * (0.7.187) Jade's panel on a player or MCA villager you haven't been introduced to ({@link Introductions}) shows only
 * what you can see: what they are ("Stranger", "Villager"), never their name or trade, and their health only once
 * you've struck them. Done once the panel is gathered, so it holds whatever each player's Jade settings say. Only
 * loaded by Jade (the {@code jade} entrypoint).
 */
@WailaPlugin
public final class JadeIntroductions implements IWailaPlugin {
	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.addTooltipCollectedCallback((box, accessor) -> {
			if (!(accessor instanceof EntityAccessor entityAccessor)) {
				return;
			}
			Entity e = entityAccessor.getEntity();
			ITooltip tooltip = box.getTooltip();
			if (!Introductions.knownHere(e)) {
				// Jade names a custom-named entity itself rather than asking getDisplayName (which hides it)
				tooltip.replace(JadeIds.CORE_OBJECT_NAME, IThemeHelper.get().title(e.getDisplayName()));
				tooltip.remove(JadeIds.MC_VILLAGER_PROFESSION);
			}
			if (!Introductions.healthKnownHere(e)) {
				tooltip.remove(JadeIds.MC_ENTITY_HEALTH);
			}
		});
	}
}
