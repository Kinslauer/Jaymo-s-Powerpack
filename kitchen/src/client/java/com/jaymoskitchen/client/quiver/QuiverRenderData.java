package com.jaymoskitchen.client.quiver;

import com.jaymoskitchen.quiver.QuiverContents;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * What the back-worn quiver needs to draw: how many arrow shafts poke out, and each one's fletching colour.
 * Each shaft stands for {@link #ARROWS_PER_SHAFT} arrows, so a full quiver of 32 shows all 16.
 */
public record QuiverRenderData(int shafts, int[] fletchingColors) {
	public static final RenderStateDataKey<QuiverRenderData> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:quiver");
	public static final int SHAFTS = 16;
	public static final int ARROWS_PER_SHAFT = QuiverContents.CAPACITY / SHAFTS;

	private static final int PLAIN = 0xFFF2F2F2;
	private static final int SPECTRAL = 0xFFFFD84A;

	public static QuiverRenderData of(QuiverContents contents) {
		List<ItemStack> arrows = contents.eachArrow();
		int shafts = Math.min(SHAFTS, (arrows.size() + ARROWS_PER_SHAFT - 1) / ARROWS_PER_SHAFT);
		int[] colors = new int[shafts];
		for (int i = 0; i < shafts; i++) {
			colors[i] = fletching(arrows.get(i * ARROWS_PER_SHAFT));
		}
		return new QuiverRenderData(shafts, colors);
	}

	/** Plain feathers are white, spectral arrows gold, tipped arrows take their potion's colour. */
	private static int fletching(ItemStack arrow) {
		if (arrow.is(Items.SPECTRAL_ARROW)) {
			return SPECTRAL;
		}
		PotionContents potion = arrow.get(DataComponents.POTION_CONTENTS);
		if (arrow.is(Items.TIPPED_ARROW) && potion != null) {
			return 0xFF000000 | potion.getColor();
		}
		return PLAIN;
	}
}
