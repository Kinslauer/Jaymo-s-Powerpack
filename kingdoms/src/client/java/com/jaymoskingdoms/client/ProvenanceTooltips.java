package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heirlooms;
import com.jaymoskingdoms.Provenance;
import com.jaymoskingdoms.StolenGoods;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The tooltip lines for where a thing came from ({@link Provenance}): one small line (how it was come by), and on Shift
 * the day, the place, the biome, the structure and who found it. Under it a stolen mark ({@link StolenGoods}) and an
 * heirloom's forebears ({@link Heirlooms}).
 */
final class ProvenanceTooltips {
	private ProvenanceTooltips() {
	}

	static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			List<String> forebears = Heirlooms.FOREBEARS == null ? null : stack.get(Heirlooms.FOREBEARS);
			if (forebears != null && !forebears.isEmpty()) {
				lines.add(Component.translatable("tooltip.jaymoskingdoms.heirloom", String.join(", ", forebears)).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
			}
			StolenGoods.Mark stolen = StolenGoods.STOLEN == null ? null : stack.get(StolenGoods.STOLEN);
			if (stolen != null) {
				lines.add(Component.translatable("tooltip.jaymoskingdoms.stolen", stolen.day()).withStyle(ChatFormatting.DARK_RED));
			}
			Provenance.Origin o = Provenance.ORIGIN == null ? null : stack.get(Provenance.ORIGIN);
			if (o == null) {
				return;
			}
			lines.add(Component.translatable("tooltip.jaymoskingdoms.provenance." + o.source(), what(o)).withStyle(o.famous() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
			if (!Minecraft.getInstance().hasShiftDown()) {
				lines.add(Component.translatable("tooltip.jaymoskingdoms.provenance.more").withStyle(ChatFormatting.DARK_GRAY));
				return;
			}
			lines.add(Component.translatable("tooltip.jaymoskingdoms.provenance.day", o.day()).withStyle(ChatFormatting.DARK_GRAY));
			o.pos().ifPresent(p -> lines.add(Component.translatable("tooltip.jaymoskingdoms.provenance.where", pretty(o.dimension()), p.getX(), p.getY(), p.getZ())
				.withStyle(ChatFormatting.DARK_GRAY)));
			if (!o.biome().isEmpty()) {
				Identifier biome = Identifier.tryParse(o.biome());
				lines.add(Component.translatable("tooltip.jaymoskingdoms.provenance.biome", biome == null ? Component.literal(o.biome())
					: Component.translatable("biome." + biome.getNamespace() + "." + biome.getPath())).withStyle(ChatFormatting.DARK_GRAY));
			}
			if (!o.structure().isEmpty()) {
				lines.add(Component.translatable("tooltip.jaymoskingdoms.provenance.structure", pretty(o.structure())).withStyle(ChatFormatting.DARK_GRAY));
			}
			if (!o.finder().isEmpty() && !made(o.source())) {
				lines.add(Component.translatable("tooltip.jaymoskingdoms.provenance.finder", o.finder()).withStyle(ChatFormatting.DARK_GRAY));
			}
		});
	}

	/** Made by someone: the small line names them already. */
	private static boolean made(String source) {
		return Provenance.CRAFTED.equals(source) || Provenance.SMELTED.equals(source) || Provenance.SMITHED.equals(source);
	}

	/** The small line's one argument: the maker, the merchant, or the mob. */
	private static Component what(Provenance.Origin o) {
		if (made(o.source())) {
			return Component.literal(o.finder());
		}
		if (Provenance.MOB.equals(o.source()) || Provenance.BOSS.equals(o.source())) {
			Identifier id = Identifier.tryParse(o.detail());
			return id == null ? Component.literal(o.detail()) : Component.translatable("entity." + id.getNamespace() + "." + id.getPath());
		}
		return Component.literal(o.detail());
	}

	/** An id for showing: {@code minecraft:ancient_city} as "Ancient City". */
	private static String pretty(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		path = path.substring(path.lastIndexOf('/') + 1);
		StringBuilder out = new StringBuilder();
		for (String word : path.split("_")) {
			if (!word.isEmpty()) {
				out.append(out.isEmpty() ? "" : " ").append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
			}
		}
		return out.toString();
	}
}
