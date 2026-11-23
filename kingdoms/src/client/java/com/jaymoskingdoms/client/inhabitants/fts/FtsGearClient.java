package com.jaymoskingdoms.client.inhabitants.fts;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.inhabitants.fts.FtsGear;
import com.jaymoskingdoms.inhabitants.fts.GearArmour;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The gear of From the Shadows Reborn (Sonmok14 and L_Ender, AGPL v3; their models and textures, see
 * licenses/from-the-shadows/): the diabolium and crust armour, worn as the original's 3D models drawn by GeckoLib's
 * armour renderer.
 */
final class FtsGearClient {
	private FtsGearClient() {
	}

	static void register() {
		set("diabolium_armor", FtsGear.DIABOLIUM_HELMET, FtsGear.DIABOLIUM_CHEST, FtsGear.DIABOLIUM_LEGGINGS, FtsGear.DIABOLIUM_BOOTS);
		set("crust_armor", FtsGear.CRUST_HELMET, FtsGear.CRUST_CHEST, FtsGear.CRUST_LEGGINGS);
	}

	/** One model for the whole set: GeckoLib draws the bones of the slot being worn. */
	private static void set(String model, GearArmour... pieces) {
		GeoRenderProvider provider = new GeoRenderProvider() {
			private GeoArmorRenderer<?, ?> renderer;

			@Override
			public GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack stack, EquipmentSlot slot) {
				if (this.renderer == null) {
					this.renderer = new GeoArmorRenderer<GearArmour, HumanoidRenderState>(new SetModel(model));
				}
				return this.renderer;
			}
		};
		for (GearArmour piece : pieces) {
			piece.provider = provider;
		}
	}

	/** A set's model and animation by name (geckolib/models/item/&lt;set&gt;.geo.json), its texture the original's armour sheet. */
	static final class SetModel extends DefaultedItemGeoModel<GearArmour> {
		private final Identifier texture;

		SetModel(String name) {
			super(JaymosKingdoms.id(name));
			this.texture = JaymosKingdoms.id("textures/armor/" + name + ".png");
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return this.texture;
		}
	}
}
