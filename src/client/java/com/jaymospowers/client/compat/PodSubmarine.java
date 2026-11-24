package com.jaymospowers.client.compat;

import com.github.alexmodguy.alexscaves.citadel.client.model.AdvancedModelBox;
import com.github.alexmodguy.alexscaves.client.model.SubmarineModel;
import com.jaymospowers.power.zfighter.pod.PodPart;
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
import net.minecraft.util.Mth;

/**
 * Andy (2026-11-24): the space pod is Alex's Caves' submarine (Alex's Caves Continued, LGPL-3.0, shipped unchanged), drawn
 * with his own model and textures read from his mod at run time, its thrusters all turned to face down, and taken apart
 * into the pod's slots so it still goes together piece by piece:
 * <ul>
 * <li>keel: the hull's keel box, the seat and the levers; tank: the hull's core; plates: its front, right, top and left
 * boxes; canopy: the front cabin</li>
 * <li>fins: the left and right thrusters (stood upright beside the hull) and the motor (upright behind it); bell: their
 * propellers, all at the bottom</li>
 * <li>mast: the periscope</li>
 * </ul>
 * A damaged fin leans out {@link #BEND} degrees; damaged parts show his scuffed-hull overlays; a finished pod lights his
 * control buttons. Only touched when Alex's Caves is loaded ({@code PodRenderer.SUBMARINE}); otherwise our own pod is drawn.
 */
public final class PodSubmarine {
	/** The submarine drawn at this size (his is about six blocks long; the pod's box is two). */
	static final float SCALE = 0.6F;
	/** Where his model's origin sits above the feet, in his blocks (his renderer lifts it 1.5). */
	static final float LIFT = 1.6F;
	/** How far a damaged fin leans out (degrees). */
	static final float BEND = 20.0F;
	// his parts, in the order SubmarineModel.getAllParts() lists them
	private static final int HULL = 0, RARM = 1, LARM = 2, SEAT = 3, LPROP = 4, RPROP = 5, LLEVER = 6, RLEVER = 7,
		WATER_MASK = 8, MOTOR = 9, BACK_PROP = 10, PERISCOPE = 11;
	// the hull's boxes, in the order his model adds them
	private static final int TOP = 0, CORE = 1, RIGHT = 2, LEFT = 3, CABIN = 4, BUMPER = 5, NOSE = 6, KEEL = 7;
	private static final Identifier TEXTURE = tex("submarine");
	private static final Identifier BUTTONS = tex("submarine_buttons");
	private static final Identifier[] DAMAGE = {tex("submarine_low"), tex("submarine_medium"), tex("submarine_high"),
		tex("submarine_critical")};
	private static SubmarineModel model;
	private static List<AdvancedModelBox> parts;
	private static List<Object> hullBoxes;

	private PodSubmarine() {
	}

	private static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath("alexscaves", "textures/entity/submarine/" + name + ".png");
	}

	private static void init() {
		model = new SubmarineModel();
		parts = new ArrayList<>();
		model.getAllParts().forEach(parts::add);
		hullBoxes = new ArrayList<>(parts.get(HULL).cubeList);
		// the thrusters stand on end with their propellers at the bottom (his point forward); pixels from the hull's pivot
		stand(parts.get(RARM), 27.0F, -11.5F, -8.0F, Mth.HALF_PI);
		stand(parts.get(LARM), -27.0F, 6.5F, -8.0F, Mth.HALF_PI);
		stand(parts.get(MOTOR), 0.0F, 7.0F, 40.0F, -Mth.HALF_PI);
		parts.get(WATER_MASK).showModel = false;
	}

	private static void stand(AdvancedModelBox part, float x, float y, float z, float pitch) {
		part.rotationPointX = x;
		part.rotationPointY = y;
		part.rotationPointZ = z;
		part.rotateAngleX = pitch;
	}

	/** Queues the submarine for a pod. The pose is the pod renderer's (turned to its heading, flipped like a mob model). */
	public static void submit(SubmitNodeCollector collector, PoseStack poseStack, int light, int filled, int damaged,
		boolean finished) {
		if (model == null) {
			init();
		}
		poseStack.pushPose();
		poseStack.translate(0.0F, -LIFT * SCALE, 0.0F);
		// his front is -z, the pod's +z
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.scale(SCALE, SCALE, SCALE);
		draw(collector, poseStack, RenderTypes.entityCutout(TEXTURE), light, filled, damaged);
		int hurt = Integer.bitCount(damaged & filled);
		if (hurt > 0) {
			draw(collector, poseStack, RenderTypes.entityTranslucent(DAMAGE[Math.min(hurt, DAMAGE.length) - 1]), light, filled,
				damaged);
		}
		if (finished) {
			draw(collector, poseStack, RenderTypes.eyes(BUTTONS), LightCoordsUtil.FULL_BRIGHT, filled, damaged);
		}
		poseStack.popPose();
	}

	private static void draw(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, int light, int filled,
		int damaged) {
		collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
			// the draw runs later, so the shared model is set to this pod's pieces here, just before it
			show(filled, damaged);
			PoseStack at = new PoseStack();
			at.last().set(pose);
			model.renderToBuffer(at, buffer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void show(int filled, int damaged) {
		List boxes = parts.get(HULL).cubeList;
		boxes.clear();
		boolean keel = has(filled, PodPart.KEEL, 0);
		if (keel) {
			boxes.add(hullBoxes.get(KEEL));
		}
		if (has(filled, PodPart.TANK, 0)) {
			boxes.add(hullBoxes.get(CORE));
		}
		if (has(filled, PodPart.PLATE, 0)) {
			boxes.add(hullBoxes.get(BUMPER));
			boxes.add(hullBoxes.get(NOSE));
		}
		if (has(filled, PodPart.PLATE, 1)) {
			boxes.add(hullBoxes.get(RIGHT));
		}
		if (has(filled, PodPart.PLATE, 2)) {
			boxes.add(hullBoxes.get(TOP));
		}
		if (has(filled, PodPart.PLATE, 3)) {
			boxes.add(hullBoxes.get(LEFT));
		}
		if (has(filled, PodPart.CANOPY, 0)) {
			boxes.add(hullBoxes.get(CABIN));
		}
		parts.get(SEAT).showModel = keel;
		parts.get(LLEVER).showModel = keel;
		parts.get(RLEVER).showModel = keel;
		fin(parts.get(LARM), filled, damaged, 0);
		fin(parts.get(RARM), filled, damaged, 1);
		fin(parts.get(MOTOR), filled, damaged, 2);
		boolean bell = has(filled, PodPart.BELL, 0);
		parts.get(LPROP).showModel = bell;
		parts.get(RPROP).showModel = bell;
		parts.get(BACK_PROP).showModel = bell;
		parts.get(PERISCOPE).showModel = has(filled, PodPart.MAST, 0);
	}

	/** Fin slots 0, 1, 2 are the left thruster, the right thruster and the motor; a damaged one swings its foot out. */
	private static void fin(AdvancedModelBox part, int filled, int damaged, int slot) {
		part.showModel = has(filled, PodPart.FIN, slot);
		float lean = has(damaged, PodPart.FIN, slot) ? BEND * Mth.DEG_TO_RAD : 0.0F;
		part.rotateAngleZ = slot == 1 ? -lean : lean;
	}

	private static boolean has(int mask, PodPart part, int slot) {
		return (mask & part.bit(slot)) != 0;
	}
}
