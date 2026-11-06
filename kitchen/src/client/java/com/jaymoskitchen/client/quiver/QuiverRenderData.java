package com.jaymoskitchen.client.quiver;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.quiver.QuiverContents;
import com.jaymoskitchen.quiver.WornQuiver;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * What the back-worn quiver needs to draw: which quiver's colours, how many arrow shafts poke out, each one's fletching
 * colour, and (0.5.81) how far into drawing a bow the wearer is, so the top arrow is seen pulled out of the quiver.
 * Each shaft stands for {@link #ARROWS_PER_SHAFT} arrows, so a full quiver of 32 shows all 16.
 */
public record QuiverRenderData(Identifier texture, int shafts, int[] fletchingColors, float draw) {
	public static final RenderStateDataKey<QuiverRenderData> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:quiver");
	public static final int SHAFTS = 16;
	public static final int ARROWS_PER_SHAFT = QuiverContents.CAPACITY / SHAFTS;

	private static final int PLAIN = 0xFFF2F2F2;
	private static final int SPECTRAL = 0xFFFFD84A;

	/** Ticks a hand takes to pull an arrow out of the quiver and up to the bow. */
	static final float DRAW_TICKS = 5.0F;

	/** The pack's quivers by item id: every one is drawn on the same model in its own colours (0.5.81). */
	private static final Identifier DEFAULT_TEXTURE = JaymosKitchen.id("textures/entity/quiver.png");
	private static final Map<Identifier, Identifier> TEXTURES = Map.of(
		Identifier.fromNamespaceAndPath("archers", "small_quiver"), JaymosKitchen.id("textures/entity/quiver/small_quiver.png"),
		Identifier.fromNamespaceAndPath("archers", "medium_quiver"), JaymosKitchen.id("textures/entity/quiver/medium_quiver.png"),
		Identifier.fromNamespaceAndPath("archers", "large_quiver"), JaymosKitchen.id("textures/entity/quiver/large_quiver.png"));

	public static QuiverRenderData of(WornQuiver worn, Avatar wearer, float partialTick) {
		// drawing a bow or loading a crossbow: the top arrow comes up out of the quiver over the first few ticks
		float draw = -1.0F;
		if (wearer.isUsingItem() && wearer.getUseItem().getItem() instanceof ProjectileWeaponItem) {
			draw = Math.min(1.0F, (wearer.getTicksUsingItem() + partialTick) / DRAW_TICKS);
		}
		return of(TEXTURES.getOrDefault(worn.item(), DEFAULT_TEXTURE), worn.contents(), draw);
	}

	static QuiverRenderData of(Identifier texture, QuiverContents contents, float draw) {
		List<ItemStack> arrows = contents.eachArrow();
		int shafts = Math.min(SHAFTS, (arrows.size() + ARROWS_PER_SHAFT - 1) / ARROWS_PER_SHAFT);
		int[] colors = new int[shafts];
		for (int i = 0; i < shafts; i++) {
			colors[i] = fletching(arrows.get(i * ARROWS_PER_SHAFT));
		}
		return new QuiverRenderData(texture, shafts, colors, draw);
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
