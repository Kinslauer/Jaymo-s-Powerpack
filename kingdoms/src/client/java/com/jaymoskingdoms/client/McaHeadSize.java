package com.jaymoskingdoms.client;

import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.client.render.VillagerVisuals;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * MCA's head size on a model it doesn't draw itself (see {@link GeoArmourFit}). MCA scales the whole drawing by the head
 * size before the root's own move and turn, and then draws the head; here the same scale goes on the head part under the
 * root, so the piece lands where MCA's head does even when the root is moved (a downed or crawling body). Only loaded
 * with MCA.
 */
final class McaHeadSize {
	private McaHeadSize() {
	}

	static void apply(HumanoidModel<?> model, HumanoidRenderState state) {
		if (!(state instanceof VillagerStateHolder holder) || !holder.mca$isGeneticsRendererActive()) {
			return;
		}
		VillagerVisuals visuals = holder.mca$getVisuals();
		if (visuals == null) {
			return;
		}
		float s = visuals.dimensions().getHead();
		if (s <= 0.0F || s == 1.0F) {
			return;
		}
		// scale(s) * root * head == root * head', with head' = move((s - 1) * root^-1 * rootPos) * scale(s) * head
		ModelPart root = model.root();
		ModelPart head = model.head;
		Vector3f shift = new Vector3f(root.x, root.y, root.z).mul(s - 1.0F);
		if (root.xRot != 0.0F || root.yRot != 0.0F || root.zRot != 0.0F) {
			new Quaternionf().rotationZYX(root.zRot, root.yRot, root.xRot).conjugate().transform(shift);
		}
		shift.div(root.xScale, root.yScale, root.zScale);
		head.x = head.x * s + shift.x;
		head.y = head.y * s + shift.y;
		head.z = head.z * s + shift.z;
		head.xScale *= s;
		head.yScale *= s;
		head.zScale *= s;
	}
}
