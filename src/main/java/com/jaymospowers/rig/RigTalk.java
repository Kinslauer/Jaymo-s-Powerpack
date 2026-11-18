package com.jaymospowers.rig;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Mind in chat: the wearer's typed words are caught and answered in the Mind's personality, and only the wearer
 * hears the reply (whenever a Core is worn). The Mind never explains the lore - it only hints (Andy's rule).
 */
public final class RigTalk {
	private RigTalk() {
	}

	/** The Rig menu's pick: reassure that the Mind is listening (chat is caught whenever a Core is worn). */
	static void use(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.talk.on"));
	}

	/** A line the wearer typed at the Mind: answer or act in the Mind's personality, privately. */
	static void handle(ServerPlayer player, String message) {
		ItemStack core = Rig.core(player);
		if (core.isEmpty()) {
			return;
		}
		String personality = RigMind.personality(core);
		if (personality == null) {
			return;
		}
		String m = message.toLowerCase(java.util.Locale.ROOT);
		if (m.contains("light") || m.contains("lamp") || m.contains("glow")) {
			RigMind.tell(player, core, "talk.light");
		} else if (m.contains("dig") || m.contains("out") || m.contains("bury")) {
			RigMind.tell(player, core, "talk.dig");
		} else if (m.contains("furnace") || m.contains("craft") || m.contains("make")) {
			RigMind.tell(player, core, "talk.craft");
		} else if (m.contains("where") || m.contains("village") || m.contains("home")) {
			RigMind.tell(player, core, "talk.where");
		} else if (m.contains("help") || m.contains("what")) {
			RigMind.tell(player, core, "talk.help");
		} else {
			RigMind.tell(player, core, "talk.unclear");
		}
	}
}
