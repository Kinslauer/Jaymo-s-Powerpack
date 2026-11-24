package com.jaymoskingdoms.inhabitants.fts;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.world.item.Item;

/**
 * The plague doctor's mask (from PlagueDoctorMaskItem in From the Shadows Reborn by Sonmok14 and L_Ender, AGPL v3; the
 * beak-and-brim model and its texture are theirs, the code is our own, see licenses/from-the-shadows/). Worn, it is drawn
 * by GeckoLib's armour renderer from {@code geckolib/models/item/plague_doctor_mask.geo.json}; the renderer is the client's
 * ({@code FtsClient}), handed over through {@link #PROVIDER} because this class lives where client classes can't be named.
 * Wearing it keeps Nightfall's plague out ({@code Nightfall#catches}).
 */
public class PlagueDoctorMask extends Item implements GeoItem {
	/** The client's GeckoLib render provider (set when the client starts); null on a server, where nothing asks for it. */
	public static volatile Object PROVIDER;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public PlagueDoctorMask(Item.Properties properties) {
		super(properties);
	}

	@Override
	public Object getRenderProvider() {
		return PROVIDER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// (a still mask: nothing to animate)
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
