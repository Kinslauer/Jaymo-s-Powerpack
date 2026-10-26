package com.jaymospowers.client.alchemy;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.codex.Codex;
import com.jaymospowers.alchemy.codex.CodexNet;
import com.jaymospowers.alchemy.codex.Lens;
import com.jaymospowers.alchemy.essence.Essences;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * Client side of the Scrying Lens and the Apothecary's Codex ({@link Codex}): the table of essences the server sends,
 * the Codex screen, the lens panel ({@link LensHud}), the tint of the essence phial's liquid ({@link EssenceTint}),
 * and a line of essences on item tooltips while Shift is held (once Alchemy Basics is learned).
 */
public final class CodexClient {
	private CodexClient() {
	}

	public static void init() {
		// the tint the essence phial's item definition names; before the first resource load
		ItemTintSources.ID_MAPPER.put(JaymosPowers.id("essence"), EssenceTint.MAP_CODEC);
		// item names and tooltips in common code ask who is looking
		Codex.VIEWER = () -> {
			Minecraft minecraft = Minecraft.getInstance();
			return minecraft.isSameThread() ? minecraft.player : null;
		};
		ClientPlayNetworking.registerGlobalReceiver(Essences.Sync.TYPE, (payload, context) -> Essences.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(CodexNet.Open.TYPE, (payload, context) -> context.client().gui.setScreen(new CodexScreen()));
		LensHud.init();
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Player player = Minecraft.getInstance().player;
			if (player == null || !Minecraft.getInstance().hasShiftDown() || !Knowledge.knows(player, Lens.BASICS)) {
				return;
			}
			EssenceList list = Essences.of(stack);
			if (list.isEmpty()) {
				return;
			}
			MutableComponent line = Component.translatable("tooltip.jaymospowers.essences").append(" ").withStyle(ChatFormatting.DARK_PURPLE);
			boolean first = true;
			for (var en : Lens.sorted(list)) {
				if (!first) {
					line.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
				}
				first = false;
				Essence e = en.getKey();
				line.append(Knowledge.hasSeen(player, e) ? Component.translatable(e.key()).append(" " + en.getValue()).withColor(CodexIcons.colour(e))
					: Component.literal("?").withStyle(ChatFormatting.GRAY));
			}
			lines.add(line);
		});
	}
}
