package com.jaymoskingdoms.client.compat;

import com.jaymoskingdoms.Greenskins;
import com.jaymoskingdoms.Introductions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.JadeIds;
import snownee.jade.api.theme.IThemeHelper;

/**
 * The client half of {@link JadeIntroductions}: the tooltip callback itself. Kept out of the plugin class because Jade
 * loads that class on the server too, and the callback's types (Jade's tooltip box) reach client-only Minecraft classes:
 * the server failed to load the plugin ("NoClassDefFoundError: NarrationSupplier") at every start.
 */
final class JadeIntroductionsClient {
	private JadeIntroductionsClient() {
	}

	static void register(IWailaClientRegistration registration) {
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
				// (0.7.474) a goblin or an orc is ours, not one of MCA's people
				tooltip.replace(JadeIds.CORE_MOD_NAME, IThemeHelper.get().modName(Component.translatable("identity.jaymoskingdoms.greenskin_mod")));
			}
			if (!Introductions.healthKnownHere(e)) {
				tooltip.remove(JadeIds.MC_ENTITY_HEALTH);
			}
		});
	}
}
