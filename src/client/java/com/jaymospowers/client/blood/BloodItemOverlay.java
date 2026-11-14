package com.jaymospowers.client.blood;

import com.jaymospowers.client.mixin.ItemLayerRenderStateAccessor;
import com.jaymospowers.client.mixin.ItemStackRenderStateAccessor;
import com.jaymospowers.client.wet.ArmourGrimeTextures;
import com.jaymospowers.core.blood.Bloodied;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.41) Andy liked Hematic's blood on swords and axes (ideas only). A bloody item ({@link Bloodied#BLOOD}: a blade
 * that cut, a hammer that crushed) carries its blood wherever it's drawn: in the hand, on the ground, in a frame, by one
 * more layer laid over its own, the item's picture with blood painted where it has pixels
 * ({@link ArmourGrimeTextures#icon}), on both faces, for flat items (a sprite drawn with depth, as swords and axes are).
 * A modelled (3D) item, JMB's longsword or our dagger, gets its own faces drawn again just over themselves, each in a
 * copy of its texture with the blood painted on ({@link ArmourGrimeTextures#sprite}). In the inventory the same blood is
 * laid over its icon ({@code ArmourGrimeIconMixin}).
 */
public final class BloodItemOverlay {
	/** Flat items are this deep at most (model units; a sprite item is 1/16). */
	static final float FLAT = 0.15F;
	/** How far over the item's faces the blood is drawn (model units). Far enough to clear the item's own faces so it no longer z-fights with them. */
	static final float OVER = 0.02F;

	/** What the extra layer draws: the painted picture over the item's bounds. */
	record Overlay(Identifier texture, float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
	}

	/** A modelled item's blood: its faces, in groups by the texture they're drawn from. */
	record Solid(List<Group> groups) {
	}

	/** Faces that share one atlas sprite, and that sprite's blood-painted copy. */
	record Group(Identifier painted, TextureAtlasSprite sprite, List<BakedQuad> quads) {
	}

	static final SpecialModelRenderer<Solid> SOLID_RENDERER = new SpecialModelRenderer<>() {
		@Override
		public void submit(@Nullable Solid solid, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlayCoords, boolean foil,
			int outline) {
			if (solid == null) {
				return;
			}
			for (Group group : solid.groups()) {
				collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(group.painted()),
					(pose, buffer) -> quads(pose, buffer, group, light, overlayCoords));
			}
		}

		@Override
		public void getExtents(Consumer<Vector3fc> output) {
		}

		@Override
		public @Nullable Solid extractArgument(ItemStack stack) {
			return null;
		}
	};

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
			solid(output, base, item);
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

	/** A modelled item: its faces again, each group in its sprite's blood-painted copy. */
	private static void solid(ItemStackRenderState output, ItemLayerRenderStateAccessor base, ItemStack item) {
		ItemQuads quads = base.jaymospowers$quads();
		if (quads == null || quads.isEmpty()) {
			return;
		}
		Bloodied.Blood blood = Bloodied.blood(item);
		Map<TextureAtlasSprite, List<BakedQuad>> bySprite = new LinkedHashMap<>();
		for (BakedQuad quad : quads.all()) {
			bySprite.computeIfAbsent(quad.materialInfo().sprite(), s -> new ArrayList<>()).add(quad);
		}
		List<Group> groups = new ArrayList<>();
		for (Map.Entry<TextureAtlasSprite, List<BakedQuad>> e : bySprite.entrySet()) {
			Identifier painted = ArmourGrimeTextures.sprite(e.getKey().contents().name(), blood);
			if (painted != null) {
				groups.add(new Group(painted, e.getKey(), e.getValue()));
			}
		}
		if (groups.isEmpty()) {
			return;
		}
		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		layer.setItemTransform(base.jaymospowers$itemTransform());
		layer.setLocalTransform(base.jaymospowers$localTransform());
		layer.setupSpecialModel(SOLID_RENDERER, new Solid(groups));
	}

	/** One group's faces, pushed {@link #OVER} out along their facing, the atlas UVs turned into the painted copy's. */
	private static void quads(PoseStack.Pose pose, VertexConsumer buffer, Group group, int light, int overlay) {
		TextureAtlasSprite sprite = group.sprite();
		float u0 = sprite.getU0(), du = sprite.getU1() - u0, v0 = sprite.getV0(), dv = sprite.getV1() - v0;
		for (BakedQuad quad : group.quads()) {
			Direction facing = quad.direction();
			float nx = facing.getStepX(), ny = facing.getStepY(), nz = facing.getStepZ();
			for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
				Vector3fc p = quad.position(i);
				long uv = quad.packedUV(i);
				float u = (UVPair.unpackU(uv) - u0) / du, v = (UVPair.unpackV(uv) - v0) / dv;
				buffer.addVertex(pose, p.x() + nx * OVER, p.y() + ny * OVER, p.z() + nz * OVER).setColor(0xFFFFFFFF).setUv(u, v)
					.setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
			}
		}
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
