package com.jaymospowers.client.hud;

import com.jaymospowers.client.power.zfighter.ZFighterClientConfig;
import com.jaymospowers.core.BodyMenu;
import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.DownedLie;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Andy (2026-11-20): "add tutorial tooltip popups for these mechanics along with wall sit recoveries". The first time each
 * comes up, one {@link TipCard} says how it works: someone down in front of you (the body menu on a tap of use, the drag on
 * a hold, the revive), someone you could execute (a hold of attack), someone you stand behind unseen with a weapon in hand
 * (the stealth kill), a lasso in your hand (throw, hogtie, then the menu),
 * yourself downed (getting up), and yourself downed with a wall beside you (sitting up against it and rising up it). Each
 * is shown once per player, flagged {@code body_tip.<player uuid>.<part>} in {@code config/jaymospowers-client.properties};
 * one waits until the card before it is gone, as {@link UiTips}' do. Keys are named by their bindings.
 */
public final class BodyTips {
	/** How often (ticks) it looks; ticks into a session before any card. */
	static final int EVERY = 10, SETTLE_TICKS = 200;
	/** The lasso, by its item id (Jaymo's Kingdoms). */
	private static final String LASSO = "jaymoskingdoms:lasso";

	private BodyTips() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BodyTips::tick);
	}

	private static String flag(LocalPlayer player, String part) {
		return "body_tip." + player.getUUID() + "." + part;
	}

	private static boolean shown(LocalPlayer player, String part) {
		return "true".equals(ZFighterClientConfig.get(flag(player, part), "false"));
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || player.tickCount < SETTLE_TICKS || player.tickCount % EVERY != 0 || TipCard.busy() || minecraft.gui.screen() != null
			|| player.isSpectator()) {
			return;
		}
		if (Downed.isDowned(player)) {
			if (!shown(player, "downed")) {
				offer(player, "downed", key("key.sneak"));
			} else if (!shown(player, "wall_sit") && DownedLie.wall(player) != null) {
				offer(player, "wall_sit", key("key.jaymospowers.sit"), key("key.sneak"));
			}
			return;
		}
		if (minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity body && body != player
			&& player.isWithinEntityInteractionRange(body, 1.0)) {
			if (BodyMenu.body(body) && !shown(player, "body_menu")) {
				offer(player, "body_menu", key("key.use"), key("key.sneak"));
				return;
			}
			if (Finishers.helpless(body) && !shown(player, "execute")) {
				offer(player, "execute", key("key.attack"));
				return;
			}
			if (!shown(player, "stealth_kill") && Finishers.stealthKillable(player, body)) {
				offer(player, "stealth_kill", key("key.sneak"), key("key.attack"));
				return;
			}
		}
		if (!shown(player, "lasso") && LASSO.equals(BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString())) {
			offer(player, "lasso", key("key.use"));
		}
	}

	private static Component key(String name) {
		return Component.keybind(name);
	}

	private static void offer(LocalPlayer player, String part, Object... args) {
		ZFighterClientConfig.set(flag(player, part), "true");
		TipCard.show(Component.translatable("body_tip.jaymospowers." + part + ".title"), Component.translatable("body_tip.jaymospowers." + part, args));
	}
}
