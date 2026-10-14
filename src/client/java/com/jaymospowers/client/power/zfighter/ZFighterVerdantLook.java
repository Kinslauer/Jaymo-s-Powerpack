package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.10.72) Andy: every Verdant has antennae and some shade of green skin. A Verdant-born Z Fighter (never the Great Ape)
 * gets both: two stalks from the top of the forehead curling forward, each with a knob
 * ({@code textures/entity/zfighter_antennae.png}, greyscale, tinted), and their own skin repainted green. Only the skin
 * tones in the skin are repainted (warm, not too grey, not too dark), so clothes keep their colours; the paint keeps
 * the skin's own shading. Each player's shade is their own, from their UUID. Kingdoms gives the Verdant World's
 * villagers the same look.
 */
public final class ZFighterVerdantLook {
	/** The Verdant's green (opaque ARGB), or absent for anyone else. */
	public static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_verdant");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_antennae.png");
	/** Greens from the palest to the deepest, and how far a shade leans yellow or blue. */
	static final int PALE = 0xA6DE86, DEEP = 0x2F6E2A;
	static final int LEAN = 26;
	/** What reads as skin: hue (degrees), saturation and brightness bounds. */
	static final float SKIN_HUE_MIN = 0.0F, SKIN_HUE_MAX = 40.0F, SKIN_SAT_MIN = 0.12F, SKIN_SAT_MAX = 0.78F, SKIN_VALUE_MIN = 0.22F;
	/** A skin pixel this bright comes out the Verdant's own green; brighter and darker ones keep their shading around it. */
	static final float SKIN_MIDTONE = 0.78F;
	/** Where the stalks stand (pixels from the head's middle, out to each side and back from the brow), how far they lean. */
	static final float APART = 1.5F, BACK = -2.0F, LEAN_OUT = 0.3F, LEAN_BACK = -0.2F, CURL = 0.75F;

	private static ModelPart antennae;
	/** Repainted skins: "<skin>|<green>" → the green skin's texture. */
	private static final Map<String, Identifier> SKINS = new HashMap<>();

	private ZFighterVerdantLook() {
	}

	/** The green of a shade (0 pale .. 1 deep), some leaning yellow and some blue. */
	static int green(float shade) {
		float s = Mth.clamp(shade, 0.0F, 1.0F);
		float lean = Mth.frac(s * 13.37F) - 0.5F;
		int r = Mth.clamp(Math.round(Mth.lerp(s, ARGB.red(PALE), ARGB.red(DEEP)) + lean * LEAN), 0, 255);
		int g = Math.round(Mth.lerp(s, ARGB.green(PALE), ARGB.green(DEEP)));
		int b = Mth.clamp(Math.round(Mth.lerp(s, ARGB.blue(PALE), ARGB.blue(DEEP)) - lean * LEAN), 0, 255);
		return ARGB.color(255, r, g, b);
	}

	/** A player's own shade, the same every time (mostly middling). */
	static int green(UUID id) {
		long bits = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
		float a = (bits & 0xFFFF) / 65535.0F;
		float b = (bits >>> 16 & 0xFFFF) / 65535.0F;
		return green((a + b) / 2.0F);
	}

	static void extract(Avatar entity, AvatarRenderState state) {
		boolean verdant = entity instanceof Player player && ZFighterLineage.of(player) == ZFighterLineage.VERDANT && !ZFighterLineage.ape(player);
		state.setData(KEY, verdant ? green(entity.getUUID()) : null);
	}

	/** The local player's green, for their own arms in first person; null when they're no Verdant. */
	public static @Nullable Integer local() {
		Player player = Minecraft.getInstance().player;
		return player != null && ZFighterLineage.of(player) == ZFighterLineage.VERDANT && !ZFighterLineage.ape(player) ? green(player.getUUID()) : null;
	}

	/** {@code skin} with its skin tones painted {@code green}; the skin itself until its pixels can be read. */
	public static Identifier skin(Identifier skin, int green) {
		String key = skin + "|" + Integer.toHexString(green);
		Identifier done = SKINS.get(key);
		if (done != null) {
			return done;
		}
		NativeImage source = pixels(skin);
		if (source == null) {
			return skin;
		}
		NativeImage painted = new NativeImage(source.getWidth(), source.getHeight(), true);
		painted.copyFrom(source);
		paint(painted, green);
		Identifier id = JaymosPowers.id("zfighter_verdant_skin/" + skin.getNamespace() + "/" + skin.getPath() + "_" + Integer.toHexString(green & 0xFFFFFF));
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, painted));
		SKINS.put(key, id);
		return id;
	}

	/** The skin's pixels: a downloaded skin's own copy, or read from the resources (the default skins). */
	private static @Nullable NativeImage pixels(Identifier skin) {
		Minecraft minecraft = Minecraft.getInstance();
		Optional<Resource> resource = minecraft.getResourceManager().getResource(skin);
		if (resource.isPresent()) {
			try (InputStream in = resource.get().open()) {
				return NativeImage.read(in);
			} catch (Exception e) {
				JaymosPowers.LOGGER.warn("Verdant skin: couldn't read {}", skin, e);
				return null;
			}
		}
		AbstractTexture texture = minecraft.getTextureManager().getTexture(skin);
		return texture instanceof DynamicTexture dynamic ? dynamic.getPixels() : null;
	}

	private static void paint(NativeImage image, int green) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getPixel(x, y);
				int alpha = ARGB.alpha(argb);
				if (alpha == 0) {
					continue;
				}
				int r = ARGB.red(argb), g = ARGB.green(argb), b = ARGB.blue(argb);
				// warm tones only (red >= green >= blue): hue 0-60 degrees
				if (r < g || g < b || r == 0) {
					continue;
				}
				float value = r / 255.0F;
				float saturation = (r - b) / (float)r;
				float hue = r == b ? 0.0F : 60.0F * (g - b) / (r - b);
				if (hue < SKIN_HUE_MIN || hue > SKIN_HUE_MAX || saturation < SKIN_SAT_MIN || saturation > SKIN_SAT_MAX || value < SKIN_VALUE_MIN) {
					continue;
				}
				float light = value / SKIN_MIDTONE;
				image.setPixel(x, y, ARGB.color(alpha, Mth.clamp(Math.round(ARGB.red(green) * light), 0, 255),
					Mth.clamp(Math.round(ARGB.green(green) * light), 0, 255), Mth.clamp(Math.round(ARGB.blue(green) * light), 0, 255)));
			}
		}
	}

	private static ModelPart antennae() {
		if (antennae == null) {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			for (int side = -1; side <= 1; side += 2) {
				String name = side < 0 ? "right" : "left";
				PartDefinition stalk = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
					PartPose.offsetAndRotation(side * APART, -8.0F, BACK, LEAN_BACK, 0.0F, side * LEAN_OUT));
				PartDefinition tip = stalk.addOrReplaceChild(name + "_tip", CubeListBuilder.create().texOffs(4, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
					PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, CURL, 0.0F, side * 0.1F));
				tip.addOrReplaceChild(name + "_knob", CubeListBuilder.create().texOffs(0, 6).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 2.0F, 2.0F),
					PartPose.offset(0.0F, -4.0F, 0.0F));
			}
			antennae = LayerDefinition.create(mesh, 16, 16).bakeRoot();
		}
		return antennae;
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Integer green = state.getData(KEY);
			if (green == null || state.isInvisible || !this.getParentModel().head.visible) {
				return;
			}
			poseStack.pushPose();
			this.getParentModel().head.translateAndRotate(poseStack);
			collector.submitModelPart(antennae(), poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, null, green);
			poseStack.popPose();
		}
	}
}
