package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Andy (2026-11-27): "all new players should start with lowend on and texture look vanilla". On an install's first
 * launch, {@link LowEndMode} is switched on (shaders off, Fast graphics) and {@link TextureLook}'s vanilla look is picked
 * (the pack's retexture packs off and never downloaded), whatever the PC. The first world joined says so once, with how
 * to go back: {@code /lowend off} puts the shaders and graphics settings back as they started, {@code /texturelook reason}
 * switches the retexture packs on.
 *
 * <p>An install counts as new when it has no low-end file yet: every launch writes one, so a player who
 * has played before keeps the settings they had, and later launches of a new install are left to the player's own
 * choices. The check is made at client start-up, before {@link PackResourcePacks} or {@link LowEndMode} write anything,
 * and the defaults are put in place before the pack's resource packs are switched on.
 */
final class NewPlayerDefaults {
	/** True when this launch is the install's first (read once, before anything of ours writes its files). */
	private static boolean fresh;
	/** Said once in chat on the first world joined, on a new install. */
	private static boolean announce;

	private NewPlayerDefaults() {
	}

	/** Called from the client entrypoint before {@link PackResourcePacks#enableOnce} is registered, so it runs first. */
	static void init() {
		fresh = LowEndMode.newInstall();
		ClientLifecycleEvents.CLIENT_STARTED.register(NewPlayerDefaults::apply);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (announce) {
				announce = false;
				client.execute(() -> {
					if (client.player != null) {
						client.player.sendSystemMessage(Component.translatable("message.jaymospowers.new_player_defaults"));
					}
				});
			}
		});
	}

	private static void apply(Minecraft minecraft) {
		if (!fresh) {
			return;
		}
		fresh = false;
		TextureLook.pickVanilla(minecraft);
		LowEndMode.switchOnForNewPlayer(minecraft);
		announce = true;
		JaymosPowers.LOGGER.info("New install: low-end mode on and the vanilla texture look picked");
	}
}
