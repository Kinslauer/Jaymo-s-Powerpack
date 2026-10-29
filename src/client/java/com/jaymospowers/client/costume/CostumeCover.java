package com.jaymospowers.client.costume;

import com.jaymospowers.client.mixin.LivingEntityRendererLayersAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.34) The skin under a worn costume isn't drawn where the costume covers it (Andy: "costumes still clip, almost
 * all of the villager skin underneath is visible in motion"). Out-growing it (0.13.26) wasn't enough: with MCA a
 * player's villager body is drawn by MCA's own layers (skin, face, clothes, hair), each with its own model, posed from a
 * copy of the body taken as the layers start; our poses that add to a limb (a reel, a kick, a carried body's give) land
 * on that copy twice, and MCA never draws the model's root, so in power animations the costume and the body under it
 * went separate ways and the skin stood out of the suit. Now, as each model is finally drawn
 * ({@code CostumeCoverModelFeatureRendererMixin}), the body's parts that the suit covers whole are hidden on the models
 * that draw this player's skin (the renderer's own body and MCA's layers), and shown again once drawn.
 * <p>
 * A part counts as covered when the cut's two textures together leave none of it bare (read from the textures, so the
 * gi's bare forearms and the ragged cut's holes still show the skin). The head is never hidden: masks have eye holes.
 */
public final class CostumeCover {
	/** Below this alpha the cutout shader drops a pixel (0.1), so the skin would show there. */
	private static final int SOLID_ALPHA = 26;
	/** A draw of the body tinted at least this opaque is the body itself (Chrono's afterimages are at most half). */
	private static final int SOLID_TINT = 192;
	/** The parts, as bits: body, right arm, left arm, right leg, left leg. */
	static final int BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 4, RIGHT_LEG = 8, LEFT_LEG = 16;

	/** What this player's costume covers, and which models draw their skin. */
	record Covered(int parts, Model<?>[] skins) {
	}

	static final RenderStateDataKey<Covered> KEY = RenderStateDataKey.create(() -> "jaymospowers:costume_cover");

	/** Per suit texture (primary), the parts it covers whole. */
	private static final Map<Identifier, Integer> COVERAGE = new HashMap<>();
	/** Per renderer, MCA's layer models (they draw the villager body), looked up once. */
	private static final Map<Object, Model<?>[]> MCA_SKINS = new WeakHashMap<>();
	private static final String MCA_LAYERS = "net.conczin.mca.client.render.layer.";
	/** Parts hidden for the model being drawn now, to show again after. */
	private static final List<ModelPart> HIDDEN = new ArrayList<>();
	private static boolean mcaBroken;

	private CostumeCover() {
	}

	/** From the costume's layer, as it's submitted: which parts are covered and which models draw this player's skin. */
	static void mark(EntityRenderState state, RenderLayerParent<?, PlayerModel> renderer, Identifier primary, Identifier secondary) {
		int parts = coverage(primary, secondary);
		if (parts == 0) {
			state.setData(KEY, null);
			return;
		}
		Model<?>[] mca = mcaSkins(renderer);
		Model<?>[] skins = new Model<?>[mca.length + 1];
		skins[0] = renderer.getModel();
		System.arraycopy(mca, 0, skins, 1, mca.length);
		state.setData(KEY, new Covered(parts, skins));
	}

	/**
	 * Just before a model is drawn: if it draws the skin of a player whose costume covers parts of it, those go. Only a
	 * solid draw ({@code tint}'s alpha at least {@link #SOLID_TINT}): a see-through copy of the body (Chrono's streak
	 * afterimages, drawn from a kept render state with no costume over them) stays whole.
	 */
	public static void beforeDraw(Model<?> model, Object state, int tint) {
		if (model instanceof CostumeModels.Worn worn) {
			worn.beforeDraw();
			return;
		}
		if (!(model instanceof PlayerModel player) || !(state instanceof EntityRenderState render) || ARGB.alpha(tint) < SOLID_TINT) {
			return;
		}
		Covered covered = render.getData(KEY);
		if (covered == null || !skin(covered, model)) {
			return;
		}
		int parts = covered.parts();
		hide(player.body, parts, BODY);
		hide(player.rightArm, parts, RIGHT_ARM);
		hide(player.leftArm, parts, LEFT_ARM);
		hide(player.rightLeg, parts, RIGHT_LEG);
		hide(player.leftLeg, parts, LEFT_LEG);
	}

	/** Once drawn: what was hidden is shown again (the models are shared by every player the renderer draws). */
	public static void afterDraw() {
		for (ModelPart part : HIDDEN) {
			part.visible = true;
		}
		HIDDEN.clear();
	}

	private static boolean skin(Covered covered, Model<?> model) {
		for (Model<?> skin : covered.skins()) {
			if (skin == model) {
				return true;
			}
		}
		return false;
	}

	private static void hide(ModelPart part, int parts, int bit) {
		if ((parts & bit) != 0 && part.visible) {
			part.visible = false;
			HIDDEN.add(part);
		}
	}

	/** MCA's layers on this renderer (skin, face, clothes, hair) hold their models in a public field {@code model}. */
	private static Model<?>[] mcaSkins(RenderLayerParent<?, PlayerModel> renderer) {
		if (mcaBroken || !(renderer instanceof LivingEntityRenderer<?, ?, ?>)) {
			return new Model<?>[0];
		}
		return MCA_SKINS.computeIfAbsent(renderer, r -> {
			List<Model<?>> models = new ArrayList<>();
			try {
				for (RenderLayer<?, ?> layer : ((LivingEntityRendererLayersAccessor)r).jaymospowers$layers()) {
					if (!layer.getClass().getName().startsWith(MCA_LAYERS)) {
						continue;
					}
					Field field = layer.getClass().getField("model");
					if (field.get(layer) instanceof Model<?> model) {
						models.add(model);
					}
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				mcaBroken = true;
				com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't find MCA's body layers; its skin may show through costumes", e);
			}
			return models.toArray(new Model<?>[0]);
		});
	}

	/** The parts a suit's two textures cover whole (cached; 0 if they can't be read). */
	private static int coverage(Identifier primary, Identifier secondary) {
		return COVERAGE.computeIfAbsent(primary, p -> {
			NativeImage a = read(primary), b = read(secondary);
			try {
				if (a == null || b == null) {
					return 0;
				}
				if (a.getWidth() != 64) {
					// (0.17.165) the thief cut's sheet is its own model's (CostumeModels.thief), whose boxes cover every part
					return BODY | RIGHT_ARM | LEFT_ARM | RIGHT_LEG | LEFT_LEG;
				}
				boolean slim = primary.getPath().contains("_slim_");
				int arm = slim ? 3 : 4;
				int parts = 0;
				parts |= covers(a, b, 16, 16, 8, 12, 4) ? BODY : 0;
				parts |= covers(a, b, 40, 16, arm, 12, 4) ? RIGHT_ARM : 0;
				parts |= covers(a, b, 32, 48, arm, 12, 4) ? LEFT_ARM : 0;
				parts |= covers(a, b, 0, 16, 4, 12, 4) ? RIGHT_LEG : 0;
				parts |= covers(a, b, 16, 48, 4, 12, 4) ? LEFT_LEG : 0;
				return parts;
			} finally {
				if (a != null) {
					a.close();
				}
				if (b != null) {
					b.close();
				}
			}
		});
	}

	/** Whether a box's whole net (w x h x d at u, v: its top and bottom, then its four sides) is solid in either texture. */
	private static boolean covers(NativeImage a, NativeImage b, int u, int v, int w, int h, int d) {
		for (int y = v; y < v + d + h; y++) {
			int from = y < v + d ? u + d : u, to = y < v + d ? u + d + 2 * w : u + 2 * d + 2 * w;
			for (int x = from; x < to; x++) {
				if (!solid(a, x, y) && !solid(b, x, y)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean solid(NativeImage image, int x, int y) {
		return x < image.getWidth() && y < image.getHeight() && ARGB.alpha(image.getPixel(x, y)) >= SOLID_ALPHA;
	}

	private static @Nullable NativeImage read(Identifier texture) {
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
		if (resource.isEmpty()) {
			return null;
		}
		try (InputStream in = resource.get().open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't read costume texture {}", texture, e);
			return null;
		}
	}
}
