package com.jaymoskitchen.client.season;

import com.jaymoskitchen.season.Season;
import com.jaymoskitchen.season.SeasonalCrops;
import com.jaymoskitchen.season.Tropics;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hovering a seed, a sapling or anything else that plants a crop says which seasons it grows in, from the same block tags
 * the server goes by ({@link SeasonalCrops}); tags are sent to every client, so nothing extra is synced. A crop in none of the
 * {@code jaymoskitchen:seasons/*} tags grows in all but winter. Tropical crops add their wet-season line ({@link Tropics}).
 * Saplings aren't held to the seasons, and the tooltip says so.
 */
public final class SeasonTooltip {
	private SeasonTooltip() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			// tags are bound only once a world is joined
			if (Minecraft.getInstance().level == null || !(stack.getItem() instanceof BlockItem item)) {
				return;
			}
			BlockState state = item.getBlock().defaultBlockState();
			if (state.getBlock() instanceof SaplingBlock) {
				lines.add(Component.translatable("tooltip.jaymoskitchen.seasons.all").withStyle(ChatFormatting.DARK_GREEN));
				return;
			}
			if (!SeasonalCrops.seasonal(state)) {
				return;
			}
			MutableComponent list = Component.empty();
			int count = 0;
			for (Season season : Season.values()) {
				if (SeasonalCrops.inSeason(state, season)) {
					if (count++ > 0) {
						list.append(Component.translatable("tooltip.jaymoskitchen.seasons.sep"));
					}
					list.append(Component.translatable("season.jaymoskitchen." + season.key()));
				}
			}
			lines.add(count == Season.values().length ? Component.translatable("tooltip.jaymoskitchen.seasons.all").withStyle(ChatFormatting.DARK_GREEN)
				: Component.translatable("tooltip.jaymoskitchen.seasons", list).withStyle(ChatFormatting.DARK_GREEN));
			if (state.is(Tropics.CROPS)) {
				lines.add(Component.translatable("tooltip.jaymoskitchen.seasons.tropical").withStyle(ChatFormatting.DARK_AQUA));
			}
			lines.add(Component.translatable("tooltip.jaymoskitchen.seasons.roof").withStyle(ChatFormatting.GRAY));
		});
	}
}
