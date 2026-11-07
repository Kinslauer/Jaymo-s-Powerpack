package com.jaymospowers.client.jewellery;

import com.jaymospowers.jewellery.CutGem;
import com.jaymospowers.jewellery.Gems;
import com.jaymospowers.jewellery.sockets.Sockets;
import com.jaymospowers.tinkering.smithing.Grades;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/** (0.20.7) The gem socket lines on a forged weapon or armour piece, just under its grade line. */
public final class SocketTooltip {
	private SocketTooltip() {
	}

	static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (lines.isEmpty() || !Sockets.socketable(stack)) {
				return;
			}
			int count = Sockets.count(stack);
			List<CutGem> gems = Sockets.gems(stack);
			if (count == 0 && gems.isEmpty()) {
				return;
			}
			List<Component> mine = new ArrayList<>();
			int set = Math.min(count, gems.size());
			mine.add(Component.translatable("tooltip.jaymospowers.sockets.count", set, count).withStyle(ChatFormatting.GRAY));
			for (int i = 0; i < gems.size(); i++) {
				CutGem gem = gems.get(i);
				Gems.Gem type = gem.gemType();
				Component gemName = type == null ? Component.translatable("item.jaymospowers.cut_gem") : type.displayName();
				if (i < count) {
					mine.add(Component.translatable("tooltip.jaymospowers.sockets.gem", gem.cutType().displayName(), gemName,
						Component.translatable("grade.jaymospowers.cut." + gem.grade().id)).withStyle(style -> style.withColor(TextColor.fromRgb(gem.colour()))));
				}
			}
			for (int i = gems.size(); i < count; i++) {
				mine.add(Component.translatable("tooltip.jaymospowers.sockets.empty").withStyle(ChatFormatting.DARK_GRAY));
			}
			for (int i = count; i < gems.size(); i++) {
				CutGem gem = gems.get(i);
				Gems.Gem type = gem.gemType();
				mine.add(Component.translatable("tooltip.jaymospowers.sockets.dormant", gem.cutType().displayName(),
					type == null ? Component.translatable("item.jaymospowers.cut_gem") : type.displayName())
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
			if (Sockets.free(stack) > 0) {
				mine.add(Component.translatable("tooltip.jaymospowers.sockets.hint").withStyle(ChatFormatting.DARK_GRAY));
			}
			int at = lines.indexOf(Grades.of(stack).displayName());
			at = at < 0 ? Math.min(lines.size(), 1) : at + 1;
			lines.addAll(at, mine);
		});
	}
}
