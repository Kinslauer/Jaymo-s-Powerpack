package com.jaymospowers.client.blood;

import com.jaymospowers.client.mixin.ItemLayerRenderStateAccessor;
import com.jaymospowers.client.mixin.ItemStackRenderStateAccessor;
import com.jaymospowers.client.wet.ArmourGrimeTextures;
import com.jaymospowers.core.blood.Bloodied;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.41) Andy liked Hematic's blood on swords and axes (ideas only). A bloody item ({@link Bloodied#BLOOD}: a blade
 * that cut, a hammer that crushed) carries its blood wherever it's drawn: in the hand, on the ground, in a frame, by one
 * more layer laid over its own, the item's picture with blood painted where it has pixels
 * ({@link ArmourGrimeTextures#icon}), on both faces. Only flat items (a sprite drawn with depth, as swords and axes are);
 * a modelled one is left as it is. In the inventory the same blood is laid over its icon ({@code ArmourGrimeIconMixin}).
 */
public final class BloodItemOverlay {
	/** Flat items are this deep at most (model units; a sprite item is 1/16). */
	static final float FLAT = 0.15F;
	/** How far over the item's faces the blood is drawn (model units). Far enough to clear the item's own faces so it no longer z-fights with them. */
	static final float OVER = 0.02F;

	/** What the extra layer draws: the painted picture over the item's bounds. */
	record Overlay(Identifier texture, float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
	}

	static final SpecialModelRenderer<Overlay> RENDERER = new SpecialModelRenderer<>() {
		@Override
		public void submit(@Nullable Overlay overlay, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlayCoords, boolean foil,
			int outline) {
			if (overlay != null) {
				collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(overlay.texture()),
					(pose, buffer) -> faces(pose, buffer, overlay, light, overlayCoords));
			}
		}

		@Override
		public void getExtents(Consumer<Vector3fc> output) {
		}

		@Override
		public @Nullable Overlay extractArgument(ItemStack stack) {
			return null;
		}
	};

	private BloodItemOverlay() {
	}

	/** After {@code item}'s own layers are laid into {@code output}: its blood as one more, if it has any. */
	public static void append(ItemStackRenderState output, ItemStack item, ItemDisplayContext context) {
		if (context == ItemDisplayContext.GUI || Bloodied.blood(item) == null) {
			return;
		}
		ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) output;
		if (state.jaymospowers$activeLayerCount() <= 0) {
			return;
		}
		ItemLayerRenderStateAccessor base = (ItemLayerRenderStateAccessor) state.jaymospowers$layers()[0];
		if (base.jaymospowers$specialRenderer() != null || base.jaymospowers$extents() == null) {
			return;
		}
		Vector3fc[] extents = base.jaymospowers$extents().get();
		if (extents == null || extents.length == 0) {
			return;
		}
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (Vector3fc v : extents) {
			minX = Math.min(minX, v.x());
			minY = Math.min(minY, v.y());
			minZ = Math.min(minZ, v.z());
			maxX = Math.max(maxX, v.x());
			maxY = Math.max(maxY, v.y());
			maxZ = Math.max(maxZ, v.z());
		}
		if (maxZ - minZ > FLAT || maxX - minX < 0.5F || maxY - minY < 0.5F) {
			return;
		}
		Identifier painted = ArmourGrimeTextures.icon(item);
		if (painted == null) {
			return;
		}
		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		layer.setItemTransform(base.jaymospowers$itemTransform());
		layer.setLocalTransform(base.jaymospowers$localTransform());
		layer.setupSpecialModel(RENDERER, new Overlay(painted, minX, maxX, minY, maxY, minZ, maxZ));
	}

	/** The picture over the item's front and back faces: its top at the top, the front unmirrored as the item's own is. */
	private static void faces(PoseStack.Pose pose, VertexConsumer buffer, Overlay o, int light, int overlay) {
		float front = o.maxZ() + OVER, back = o.minZ() - OVER;
		vertex(buffer, pose, o.minX(), o.maxY(), front, 0.0F, 0.0F, light, overlay, 1.0F);
		vertex(buffer, pose, o.minX(), o.minY(), front, 0.0F, 1.0F, light, overlay, 1.0F);
		vertex(buffer, pose, o.maxX(), o.minY(), front, 1.0F, 1.0F, light, overlay, 1.0F);
		vertex(buffer, pose, o.maxX(), o.maxY(), front, 1.0F, 0.0F, light, overlay, 1.0F);
		vertex(buffer, pose, o.minX(), o.maxY(), back, 0.0F, 0.0F, light, overlay, -1.0F);
		vertex(buffer, pose, o.maxX(), o.maxY(), back, 1.0F, 0.0F, light, overlay, -1.0F);
		vertex(buffer, pose, o.maxX(), o.minY(), back, 1.0F, 1.0F, light, overlay, -1.0F);
		vertex(buffer, pose, o.minX(), o.minY(), back, 0.0F, 1.0F, light, overlay, -1.0F);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, int overlay,
		float normalZ) {
		buffer.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, normalZ);
	}
}
