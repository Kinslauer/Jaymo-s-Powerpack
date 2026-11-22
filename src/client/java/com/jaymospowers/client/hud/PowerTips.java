package com.jaymospowers.client.hud;

import com.jaymospowers.client.power.zfighter.ZFighterClientConfig;
import com.jaymospowers.core.Powers;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * First-time tips for every power's least obvious inputs (Andy's Powerborne picks), a {@link TipCard} like Z Fighter's
 * ({@code ZFighterTips}, which keeps its own, so Z Fighter is not here). The first time a player is seen with a power, one
 * card names its two to four inputs that the key guide doesn't make plain, in one or two short lines. It is shown once per
 * player ever: {@code power_tip.<player uuid>.<power>=true} in {@code config/jaymospowers-client.properties}, written the
 * moment the card is queued. Powers handed over by another track keep their keys in their own lang lines
 * ({@code tip.jaymospowers.<power>}): the card names each key by its binding, so a rebound key reads right.
 * <p>
 * The text's arguments are the keys, in this order: ability 1 to 4, ultimate, mode, then the power's own extras
 * ({@link #EXTRAS}): Wind's six storm keys, Web's attack and use keys.
 */
public final class PowerTips {
	/** The powers with a card (Z Fighter has its own): by the id path their attachment holds. */
	private static final Set<String> POWERS = Set.of("arsenal", "chrono", "cryo", "darktide", "gravity", "resonance", "smoke", "web", "wind", "xenoform");
	/** How often (ticks) the player's power is looked at. */
	private static final int EVERY = 20;
	/** The keys every card's text can name, in order. */
	private static final String[] KEYS = {"key.jaymospowers.ability_1", "key.jaymospowers.ability_2", "key.jaymospowers.ability_3",
		"key.jaymospowers.ability_4", "key.jaymospowers.ultimate", "key.jaymospowers.mode"};
	/** After {@link #KEYS}: keys only one power's card names. */
	private static final String[] WIND_EXTRAS = {"key.jaymospowers.wind_storm_draw", "key.jaymospowers.wind_storm_hurl", "key.jaymospowers.wind_storm_recall",
		"key.jaymospowers.wind_storm_twin", "key.jaymospowers.wind_storm_landing", "key.jaymospowers.wind_storm_road"};
	private static final String[] WEB_EXTRAS = {"key.attack", "key.use"};

	private PowerTips() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(PowerTips::tick);
	}

	private static String flag(LocalPlayer player, String power) {
		return "power_tip." + player.getUUID() + "." + power;
	}

	private static String[] extras(String power) {
		return switch (power) {
			case "wind" -> WIND_EXTRAS;
			case "web" -> WEB_EXTRAS;
			default -> new String[0];
		};
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || player.tickCount % EVERY != 0) {
			return;
		}
		Identifier id = player.getAttached(Powers.POWER);
		if (id == null || !POWERS.contains(id.getPath()) || "true".equals(ZFighterClientConfig.get(flag(player, id.getPath()), "false"))) {
			return;
		}
		String power = id.getPath();
		ZFighterClientConfig.set(flag(player, power), "true");
		String[] extra = extras(power);
		Object[] args = new Object[KEYS.length + extra.length];
		for (int i = 0; i < args.length; i++) {
			args[i] = Component.keybind(i < KEYS.length ? KEYS[i] : extra[i - KEYS.length]);
		}
		TipCard.show(Component.translatable("tip.jaymospowers." + power + ".title"), Component.translatable("tip.jaymospowers." + power, args));
	}
}
