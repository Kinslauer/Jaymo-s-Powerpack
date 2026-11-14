package com.jaymoskingdoms.client.spirits;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.model.GrimReaperEntityModel;
import net.conczin.mca.client.render.GrimReaperRenderState;
import net.conczin.mca.entity.ReaperAttackState;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The Reaper's body: Minecraft Comes Alive's own Grim Reaper (Andy: "is MCA's reaper in? if so it should look like that"),
 * its model and its texture used where they are, in MCA's jar. Only {@link ReaperClient} touches this, and only when MCA is
 * loaded.
 */
final class McaReaper {
	static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mca", "textures/entity/grimreaper.png");
	/** The scythe: at rest, drawn back, brought down. */
	static final int REST = 0, DRAWN = 1, DOWN = 2;

	private static GrimReaperEntityModel model;

	private McaReaper() {
	}

	/** MCA's Grim Reaper at {@code pose}, {@code age} ticks old, its scythe as {@code scythe}, tinted {@code argb} (its alpha how solid it is). */
	static void submit(SubmitNodeCollector collector, PoseStack pose, float age, int scythe, int argb, int order) {
		if (model == null) {
			model = new GrimReaperEntityModel(LayerDefinition.create(GrimReaperEntityModel.getModelData(CubeDeformation.NONE), 64, 64).bakeRoot());
		}
		GrimReaperRenderState state = new GrimReaperRenderState();
		state.ageInTicks = age;
		state.attackState = scythe == DRAWN ? ReaperAttackState.PRE : scythe == DOWN ? ReaperAttackState.POST : ReaperAttackState.IDLE;
		collector.order(order).submitModel(model, state, pose, RenderTypes.entityTranslucent(TEXTURE), 0xF000F0, OverlayTexture.NO_OVERLAY, argb, null, 0);
	}
}
