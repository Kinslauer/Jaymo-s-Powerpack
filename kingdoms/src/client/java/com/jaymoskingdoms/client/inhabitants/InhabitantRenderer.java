package com.jaymoskingdoms.client.inhabitants;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.constant.DefaultAnimations;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

/**
 * How Kingdoms draws a GeckoLib mob: its files found by name (geckolib/models/entity/{@code name}.geo.json,
 * geckolib/animations/entity/{@code name}.animation.json, textures/entity/{@code name}.png), its head turned where it looks
 * (a bone named {@code head}, if it has one), at {@code scale}, with a shadow of {@code shadow} blocks. A mob that needs more
 * (bones shown or hidden by its state) extends this and adds to {@link #adjustModelBonesForRender}.
 */
public class InhabitantRenderer<T extends LivingEntity & GeoAnimatable> extends GeoEntityRenderer<T, LivingEntityRenderState> {
	private final String headBone;

	public InhabitantRenderer(EntityRendererProvider.Context context, String name, float scale, float shadow, String headBone) {
		this(context, new DefaultedEntityGeoModel<T>(JaymosKingdoms.id(name)), scale, shadow, headBone);
	}

	/** One whose model is its own (a texture picked by its state, say). */
	protected InhabitantRenderer(EntityRendererProvider.Context context, GeoModel<T> model, float scale, float shadow, String headBone) {
		super(context, model);
		this.withScale(scale);
		this.shadowRadius = shadow;
		this.headBone = headBone;
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(info, snapshots);
		if (this.headBone != null) {
			DefaultAnimations.hardcodedHeadRotation(info, snapshots, this.headBone);
		}
	}
}
