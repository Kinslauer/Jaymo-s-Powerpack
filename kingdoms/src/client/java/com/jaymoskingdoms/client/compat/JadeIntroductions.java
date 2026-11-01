package com.jaymoskingdoms.client.compat;

import com.jaymoskingdoms.Greenskins;
import com.jaymoskingdoms.Introductions;
import net.minecraft.network.chat.Component;
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
 * loaded by Jade (the {@code jade} entrypoint). (0.7.472) A goblin or an orc is named as one ("Orc", not MCA's "Male
 * Villager") and shown as ours, not Minecraft Comes Alive's.
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
			if (Greenskins.of(e) != null) {
				// (0.7.472) a goblin or an orc is ours, not one of MCA's people
				tooltip.replace(JadeIds.CORE_MOD_NAME, IThemeHelper.get().modName(Component.translatable("identity.jaymoskingdoms.greenskin_mod")));
			}
			if (!Introductions.healthKnownHere(e)) {
				tooltip.remove(JadeIds.MC_ENTITY_HEALTH);
			}
		});
	}
}
