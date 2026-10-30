package com.jaymospowers.client.tinkering.smithing;

import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.smithing.Edges;
import com.jaymospowers.tinkering.smithing.Provenance;
import com.jaymospowers.tinkering.smithing.Wear;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** (Smithing) The maker's mark and the "Broken" line on tooltips. */
final class WiderSmithingClient {
	private WiderSmithingClient() {
	}

	static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (lines.isEmpty()) {
				return;
			}
			if (Wear.broken(stack)) {
				lines.add(1, Component.translatable("tooltip.jaymospowers.broken").withStyle(ChatFormatting.RED));
			}
			// (0.18.56) a Cold or Hot Edge from a quench
			String edge = stack.get(Edges.EDGE);
			if (edge != null) {
				var trait = Edges.trait(edge);
				lines.add(Tinkering.traitDetails.getAsBoolean() ? Component.translatable("tooltip.jaymospowers.tinkering.trait", trait.name(1),
					trait.description().copy().withStyle(ChatFormatting.DARK_GRAY)).withStyle(trait.colour) : trait.name(1).copy().withStyle(trait.colour));
			}
			String maker = stack.get(Provenance.FORGED_BY);
			if (maker != null) {
				lines.add(Component.translatable("tooltip.jaymospowers.forged_by", maker).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		});
	}
}
