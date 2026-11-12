package com.jaymospowers.client.power.chrono;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Lets a body's render layers (armour, worn layers, capes, costume pieces) draw onto an afterimage: every model they
 * submit is drawn translucent in its own texture, tinted and faded as the afterimage is (Andy, 2026-11-12, on Overclock's
 * afterimages leaving the gear behind: "it should clone them"). Held items keep their own render (an item's quads carry
 * their own type) but are tinted the afterimage's colour. Shadows, name tags, flames, leashes and the rest are dropped.
 */
final class GhostCollector implements SubmitNodeCollector {
	private final SubmitNodeCollector out;
	private final int tint;

	GhostCollector(SubmitNodeCollector out, int tint) {
		this.out = out;
		this.tint = tint;
	}

	// ---- the texture a render type draws (RenderType.state.textures, kept private by the game) ----
	private static @Nullable Field stateField, texturesField;
	private static @Nullable Method locationMethod;
	private static boolean broken;
	private static final Map<RenderType, Identifier> TEXTURES = new IdentityHashMap<>();

	static @Nullable Identifier texture(RenderType type) {
		Identifier known = TEXTURES.get(type);
		if (known != null || broken) {
			return known;
		}
		try {
			if (stateField == null) {
				stateField = RenderType.class.getDeclaredField("state");
				stateField.setAccessible(true);
			}
			Object setup = stateField.get(type);
			if (texturesField == null) {
				texturesField = setup.getClass().getDeclaredField("textures");
				texturesField.setAccessible(true);
			}
			Map<?, ?> textures = (Map<?, ?>)texturesField.get(setup);
			Object binding = textures.get("Sampler0");
			if (binding == null) {
				return null;
			}
			if (locationMethod == null) {
				locationMethod = binding.getClass().getDeclaredMethod("location");
				locationMethod.setAccessible(true);
			}
			Identifier at = (Identifier)locationMethod.invoke(binding);
			if (at != null && TEXTURES.size() < 512) {
				TEXTURES.put(type, at);
			}
			return at;
		} catch (ReflectiveOperationException | RuntimeException e) {
			broken = true;
			com.jaymospowers.JaymosPowers.LOGGER.warn("Afterimages can't read render textures; they'll show the body only", e);
			return null;
		}
	}

	@Override
	public OrderedSubmitNodeCollector order(int order) {
		return this;
	}

	@Override
	public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay, int color,
		@Nullable UvMapping uv, int outline) {
		Identifier texture = texture(type);
		if (texture == null) {
			return;
		}
		this.out.submitModel(model, state, pose, RenderTypes.entityTranslucent(texture), light, overlay, ARGB.multiply(color, this.tint), uv,
			EntityRenderState.NO_OUTLINE);
	}

	@Override
	public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outline, int[] tints, ItemQuads quads,
		ItemStackRenderState.FoilType foil) {
		int[] tinted = new int[tints.length];
		for (int i = 0; i < tints.length; i++) {
			tinted[i] = ARGB.multiply(tints[i], this.tint | 0xFF000000);
		}
		this.out.submitItem(pose, context, light, overlay, 0, tinted, quads, ItemStackRenderState.FoilType.NONE);
	}

	@Override
	public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay, int color,
		ModelFeatureRenderer.CrumblingOverlay crumbling) {
	}

	@Override
	public void submitShadow(PoseStack pose, float radius, List<EntityRenderState.ShadowPiece> pieces) {
	}

	@Override
	public void submitNameTag(PoseStack pose, Vec3 at, int y, Component name, boolean seeThrough, int light, CameraRenderState camera) {
	}

	@Override
	public void submitText(PoseStack pose, float x, float y, FormattedCharSequence text, boolean shadow, Font.DisplayMode mode, int light, int color,
		int background, int outline) {
	}

	@Override
	public void submitTextBackground(PoseStack pose, float x0, float y0, float x1, float y1, int color, Font.DisplayMode mode, int light) {
	}

	@Override
	public void submitFlame(PoseStack pose, EntityRenderState state, org.joml.Quaternionf rotation) {
	}

	@Override
	public void submitLeash(PoseStack pose, EntityRenderState.LeashState leash) {
	}

	@Override
	public void submitMovingBlock(PoseStack pose, MovingBlockRenderState state, int light) {
	}

	@Override
	public void submitBlockModel(PoseStack pose, RenderType type, List<BlockStateModelPart> parts, int[] tints, int light, int overlay, int outline) {
	}

	@Override
	public void submitBreakingBlockModel(PoseStack pose, List<BlockStateModelPart> parts, int progress, boolean flag) {
	}

	@Override
	public void submitShapeOutline(PoseStack pose, VoxelShape shape, RenderType type, int color, float width, boolean flag) {
	}

	@Override
	public void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
	}

	@Override
	public void submitQuadParticleGroup(QuadParticleRenderState state) {
	}

	@Override
	public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean flag) {
	}
}
