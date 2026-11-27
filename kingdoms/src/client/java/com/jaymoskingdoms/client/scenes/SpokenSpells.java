package com.jaymoskingdoms.client.scenes;

import com.jaymoskingdoms.MidnightBargain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * A Warlock's spoken spells on this game ({@link MidnightBargain}): while the server says the seal is in hand
 * ({@link MidnightBargain.Words}) and the microphone is on, {@link VoiceEars} listens for the names of the spells they know
 * ("Fire Bolt", "Misty Step"), and a name heard is sent back to be cast ({@link MidnightBargain.Spoken}). A story scene's
 * listening comes first; this picks up again once it's over. {@code /kingdommic on|off} switches the microphone outside a scene.
 */
public final class SpokenSpells {
	static final String EARS = "spoken_spells";
	/** The spells the server says may be said now, and each one's name as it's said (plain). */
	private static List<String> spells = List.of();
	private static final Map<String, String> BY_NAME = new HashMap<>();

	private SpokenSpells() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MidnightBargain.Words.TYPE, (payload, context) -> {
			spells = List.copyOf(payload.spells());
			if (spells.isEmpty()) {
				VoiceEars.stop(EARS);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(SpokenSpells::tick);
		// /kingdommic on|off: the microphone switched on or off outside a scene
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal("kingdommic")
			.then(ClientCommands.literal("on").executes(c -> mic(c.getSource(), true)))
			.then(ClientCommands.literal("off").executes(c -> mic(c.getSource(), false)))));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			spells = List.of();
			VoiceEars.stop(EARS);
		});
	}

	private static int mic(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, boolean on) {
		VoiceEars.setEnabled(on);
		source.sendFeedback(Component.translatable(!VoiceEars.possible() ? "story.jaymoskingdoms.scene.mic_none"
			: on ? "story.jaymoskingdoms.scene.mic_on" : "story.jaymoskingdoms.scene.mic_off"));
		return 1;
	}

	private static void tick(Minecraft minecraft) {
		if (spells.isEmpty() || StorySceneClient.running() || minecraft.player == null || !VoiceEars.enabled() || minecraft.player.tickCount % 10 != 0) {
			return;
		}
		String owner = VoiceEars.owner();
		if (owner != null && !owner.equals(EARS)) {
			return; // something else is listening (a scene's last beat letting go)
		}
		BY_NAME.clear();
		List<String> names = new ArrayList<>();
		for (String spell : spells) {
			String name = StorySceneScreen.plain(Component.translatable("class.jaymoskingdoms.option." + spell).getString());
			if (!name.isEmpty() && !BY_NAME.containsKey(name)) {
				BY_NAME.put(name, spell);
				names.add(name);
			}
		}
		VoiceEars.listen(EARS, names, (phrase, confidence) -> {
			String spell = BY_NAME.get(StorySceneScreen.plain(phrase));
			if (spell != null && ClientPlayNetworking.canSend(MidnightBargain.Spoken.TYPE)) {
				ClientPlayNetworking.send(new MidnightBargain.Spoken(spell));
			}
		});
	}
}
