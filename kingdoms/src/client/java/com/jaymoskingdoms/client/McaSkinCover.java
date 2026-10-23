package com.jaymoskingdoms.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.178) MCA's bare body isn't drawn where its clothes cover it whole (Andy: "the default skins are still being drawn
 * underneath the MCA villager custom and it is causing more clipping issues"). MCA draws a villager (and every player,
 * who is one: {@code McaVillagerBodyMixin}) as a bare skin-coloured body, then its clothes on a copy of that body only
 * 0.0625 of a pixel bigger. Each layer's model is posed from a copy of the body as the layers start and drawn later, so
 * once an animation (Fresh Animations, a power's pose) moves the copies even a little apart, or from a few blocks off
 * where the depth buffer can't tell the two shells apart, the bare body pokes through the shirt and trousers. As MCA's
 * skin model is finally drawn ({@code McaSkinCoverDrawMixin}), the body, arms and legs that the clothes' texture covers
 * whole (with their jacket, sleeve and trouser layers) are hidden, and shown again once drawn. The head never is: faces
 * and hoods have holes. Jaymo's Powers does the same for its costumes ({@code CostumeCover}).
 */
public final class McaSkinCover {
	/** Below this alpha the cutout shader drops a pixel (0.1), so the skin would show there. */
	private static final int SOLID_ALPHA = 26;
	/** The parts, as bits: body, right arm, left arm, right leg, left leg. */
	public static final int BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 4, RIGHT_LEG = 8, LEFT_LEG = 16;

	/** What the clothes of the villager being drawn cover (bits above); absent when no clothes were drawn on it. */
	static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:mca_skin_cover");

	/** The models MCA's skin layers draw the bare body with (one per renderer). */
	private static final Set<Model<?>> SKINS = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Per clothes texture, the parts it covers whole (resource textures; library clothes are read each time they load). */
	private static final Map<Identifier, Integer> COVERAGE = new HashMap<>();
	/** Parts hidden for the model being drawn now, to show again after. */
	private static final List<ModelPart> HIDDEN = new ArrayList<>();

	private McaSkinCover() {
	}

	/** From MCA's skin layer, as it is submitted: its model draws the bare body. */
	public static void skinModel(Model<?> model) {
		SKINS.add(model);
	}

	/** From MCA's clothes layer, before it decides anything: nothing covered unless it draws ({@link #clothes}). */
	public static void reset(EntityRenderState state) {
		state.setData(KEY, null);
	}

	/** From MCA's clothes layer as it draws {@code texture} on this villager: what of the body that covers. */
	public static void clothes(EntityRenderState state, Identifier texture) {
		int parts = coverage(texture);
		state.setData(KEY, parts == 0 ? null : parts);
	}

	/** Just before a model is drawn: if it's MCA's bare body under clothes that cover parts of it, those go. */
	public static void beforeDraw(Model<?> model, Object state) {
		if (!(state instanceof EntityRenderState render) || !(model instanceof HumanoidModel<?> body) || !SKINS.contains(model)) {
			return;
		}
		Integer covered = render.getData(KEY);
		if (covered == null) {
			return;
		}
		int parts = covered;
		hide(body.body, parts, BODY);
		if (model instanceof CommonVillagerModel<?> villager) {
			hide(villager.getBreastPart(), parts, BODY);
		}
		hide(body.rightArm, parts, RIGHT_ARM);
		hide(body.leftArm, parts, LEFT_ARM);
		hide(body.rightLeg, parts, RIGHT_LEG);
		hide(body.leftLeg, parts, LEFT_LEG);
	}

	/** Once drawn: what was hidden is shown again (the skin model is shared by everyone the renderer draws). */
	public static void afterDraw() {
		for (ModelPart part : HIDDEN) {
			part.visible = true;
		}
		HIDDEN.clear();
	}

	private static void hide(@Nullable ModelPart part, int parts, int bit) {
		if (part != null && (parts & bit) != 0 && part.visible) {
			part.visible = false;
			HIDDEN.add(part);
		}
	}

	/** The parts a clothes texture covers whole, its own boxes and the jacket, sleeve and trouser layers together. */
	private static int coverage(Identifier texture) {
		Integer known = COVERAGE.get(texture);
		if (known != null) {
			return known;
		}
		AbstractTexture loaded = Minecraft.getInstance().getTextureManager().getTexture(texture);
		if (loaded instanceof DynamicTexture dynamic) {
			// (clothes from MCA's Immersive Library: built in memory, not kept, since one id can be loaded again)
			NativeImage pixels = dynamic.getPixels();
			return pixels == null ? 0 : coverage(pixels);
		}
		NativeImage image = read(texture);
		int parts = 0;
		if (image != null) {
			try {
				parts = coverage(image);
			} finally {
				image.close();
			}
		}
		COVERAGE.put(texture, parts);
		return parts;
	}

	private static int coverage(NativeImage image) {
		if (image.getWidth() < 64 || image.getHeight() < 64) {
			// (an old 64x32 texture: no left limbs or outer layers to read)
			return 0;
		}
		int parts = 0;
		// MCA's bodies are a player's (64x64, wide arms): the box, then its outer layer
		parts |= covers(image, 16, 16, 16, 32, 8, 12, 4) ? BODY : 0;
		parts |= covers(image, 40, 16, 40, 32, 4, 12, 4) ? RIGHT_ARM : 0;
		parts |= covers(image, 32, 48, 48, 48, 4, 12, 4) ? LEFT_ARM : 0;
		parts |= covers(image, 0, 16, 0, 32, 4, 12, 4) ? RIGHT_LEG : 0;
		parts |= covers(image, 16, 48, 0, 48, 4, 12, 4) ? LEFT_LEG : 0;
		return parts;
	}

	/**
	 * Whether a box's whole net (w x h x d at u, v: its top and bottom, then its four sides) is solid in the box itself
	 * or in its outer layer's net (at u2, v2).
	 */
	private static boolean covers(NativeImage image, int u, int v, int u2, int v2, int w, int h, int d) {
		for (int y = 0; y < d + h; y++) {
			int from = y < d ? d : 0, to = y < d ? d + 2 * w : 2 * d + 2 * w;
			for (int x = from; x < to; x++) {
				if (!solid(image, u + x, v + y) && !solid(image, u2 + x, v2 + y)) {
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
			com.jaymoskingdoms.JaymosKingdoms.LOGGER.warn("Couldn't read MCA clothes texture {}", texture, e);
			return null;
		}
	}
}
