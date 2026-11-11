package com.jaymoskingdoms.client.nether;

import com.geckolib.constant.DefaultAnimations;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.nether.Imp;
import com.jaymoskingdoms.nether.Imps;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Draws the imp (com.jaymoskingdoms.nether.Imp) with GeckoLib: one model and one set of animations for both kinds
 * (geckolib/models/entity/imp.geo.json, geckolib/animations/entity/imp.animation.json), the zombified in its own skin.
 * The fireball over its head is a bone of the model, shown only while it holds one. Its thrown fireball is drawn as a
 * ghast's, a fire charge three times the size.
 */
public final class ImpRenderer extends GeoEntityRenderer<Imp, LivingEntityRenderState> {
	static final float SHADOW = 0.4F, FIREBALL_SCALE = 3.0F;
	static final DataTicket<Boolean> HOLDS_BALL = DataTicket.create("jaymoskingdoms_imp_ball", Boolean.class);

	ImpRenderer(EntityRendererProvider.Context context, boolean zombified) {
		super(context, zombified ? new DefaultedEntityGeoModel<Imp>(JaymosKingdoms.id("imp")).withAltTexture(JaymosKingdoms.id("zombified_imp"))
			: new DefaultedEntityGeoModel<Imp>(JaymosKingdoms.id("imp")));
		this.shadowRadius = SHADOW;
	}

	public static void register() {
		EntityRenderers.register(Imps.IMP, context -> new ImpRenderer(context, false));
		EntityRenderers.register(Imps.ZOMBIFIED_IMP, context -> new ImpRenderer(context, true));
		EntityRenderers.register(Imps.IMP_FIREBALL, context -> new ThrownItemRenderer<>(context, FIREBALL_SCALE, true));
	}

	@Override
	public void addRenderData(Imp imp, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(imp, related, state, partialTick);
		state.addGeckolibData(HOLDS_BALL, imp.charging() != 0 && imp.isAlive());
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(info, snapshots);
		DefaultAnimations.hardcodedHeadRotation(info, snapshots, "head");
		boolean ball = info.getOrDefaultGeckolibData(HOLDS_BALL, false);
		snapshots.ifPresent("fireball", bone -> bone.skipRender(!ball));
	}
}
