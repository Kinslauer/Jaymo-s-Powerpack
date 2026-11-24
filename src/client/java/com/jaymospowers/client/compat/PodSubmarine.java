package com.jaymospowers.client.compat;

import com.github.alexmodguy.alexscaves.citadel.client.model.AdvancedModelBox;
import com.github.alexmodguy.alexscaves.client.model.SubmarineModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/**
 * Andy (2026-11-24): the space pod is Alex's Caves' submarine (Alex's Caves Continued, LGPL-3.0, shipped unchanged). Once a
 * pod's hull is whole (keel and all four plates) it is drawn with Alex's own submarine model and textures, read from his
 * mod at run time: the side thrusters are its two side fins, the motor and back propeller its back fin, the periscope its
 * beacon mast. Damaged parts show his scuffed-hull overlays; a finished pod lights his control buttons. Without Alex's Caves,
 * or while the hull is still going together, our own pod pieces are drawn ({@code PodRenderer}).
 */
public final class PodSubmarine {
	/** The submarine drawn at this size (his is about six blocks long; the pod's box is two). */
	static final float SCALE = 0.6F;
	/** Where his model's origin sits above the feet, in his blocks (his renderer lifts it 1.5). */
	static final float LIFT = 1.6F;
	// his parts, in the order SubmarineModel.getAllParts() lists them
	private static final int RARM = 1, LARM = 2, WATER_MASK = 8, MOTOR = 9, PERISCOPE = 11;
	private static final Identifier TEXTURE = tex("submarine");
	private static final Identifier BUTTONS = tex("submarine_buttons");
	private static final Identifier[] DAMAGE = {tex("submarine_low"), tex("submarine_medium"), tex("submarine_high"),
		tex("submarine_critical")};
	private static SubmarineModel model;
	private static List<AdvancedModelBox> parts;

	private PodSubmarine() {
	}

	private static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath("alexscaves", "textures/entity/submarine/" + name + ".png");
	}

	/**
	 * Queues the submarine for a pod whose hull is whole. The pose is the pod renderer's (turned to its heading, flipped
	 * like a mob model). {@code fins} holds which of the left, right and back fins are on, {@code mast} whether the mast is.
	 */
	public static void submit(SubmitNodeCollector collector, PoseStack poseStack, int light, boolean[] fins, boolean mast,
		int damaged, boolean finished) {
		if (model == null) {
			model = new SubmarineModel();
			parts = new ArrayList<>();
			model.getAllParts().forEach(parts::add);
		}
		poseStack.pushPose();
		poseStack.translate(0.0F, -LIFT * SCALE, 0.0F);
		// his front is -z, the pod's +z
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.scale(SCALE, SCALE, SCALE);
		draw(collector, poseStack, RenderTypes.entityCutout(TEXTURE), light, fins, mast);
		if (damaged > 0) {
			draw(collector, poseStack, RenderTypes.entityTranslucent(DAMAGE[Math.min(damaged, DAMAGE.length) - 1]), light, fins,
				mast);
		}
		if (finished) {
			draw(collector, poseStack, RenderTypes.eyes(BUTTONS), LightCoordsUtil.FULL_BRIGHT, fins, mast);
		}
		poseStack.popPose();
	}

	private static void draw(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, int light, boolean[] fins,
		boolean mast) {
		collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
			// the draw runs later, so the shared model's parts are shown and hidden here, just before it
			parts.get(LARM).showModel = fins[0];
			parts.get(RARM).showModel = fins[1];
			parts.get(MOTOR).showModel = fins[2];
			parts.get(PERISCOPE).showModel = mast;
			parts.get(WATER_MASK).showModel = false;
			PoseStack at = new PoseStack();
			at.last().set(pose);
			model.renderToBuffer(at, buffer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
		});
	}
}
