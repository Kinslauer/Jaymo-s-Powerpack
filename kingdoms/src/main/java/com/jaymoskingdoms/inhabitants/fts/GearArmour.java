package com.jaymoskingdoms.inhabitants.fts;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A piece of the diabolium or crust armour (from DiaboliumArmorItem and CrustArmorItem in From the Shadows Reborn by
 * Sonmok14 and L_Ender, AGPL v3; the models and textures are theirs, the code our own, see licenses/from-the-shadows/).
 * Worn, it is drawn by GeckoLib's armour renderer from {@code geckolib/models/item/<set>_armor.geo.json}; the renderer is
 * the client's ({@code FtsClient}), handed to each piece through {@link #provider} because this class lives where client
 * classes can't be named. The set's bonus is in {@link FtsGear}.
 */
public class GearArmour extends Item implements GeoItem {
	/** The client's GeckoLib render provider (set when the client starts); null on a server, where nothing asks for it. */
	public volatile Object provider;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final String tooltip;

	/** @param tooltip the lang key of the set's two tooltip lines ({@code <tooltip>.1}, {@code <tooltip>.2}) */
	public GearArmour(Item.Properties properties, String tooltip) {
		super(properties);
		this.tooltip = tooltip;
	}

	@Override
	public Object getRenderProvider() {
		return this.provider;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// (the armour only stands: no animation)
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable(this.tooltip + ".1").withStyle(ChatFormatting.BLUE));
		builder.accept(Component.translatable(this.tooltip + ".2").withStyle(ChatFormatting.BLUE));
	}
}
