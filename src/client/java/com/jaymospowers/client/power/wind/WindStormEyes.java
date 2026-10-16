package com.jaymospowers.client.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.wind.WindModes;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.11.99) In the Thunderhead (both wind modes at once) the eyes go white and glow, storm-lit: an emissive pass over
 * the player's own skin, the white laid where that skin's own eyes are ({@link #findEyes}, worked out once per skin;
 * the stock spots, the default skins' eyes, while a skin downloads or when no eyes can be told apart). The crackling
 * lightning while flying is {@link WindAura}.
 */
final class WindStormEyes extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** The eyes' white (opaque ARGB), and how far a face pixel must be from the skin tone (RGB distance) to be an eye. */
	static final int WHITE = 0xFFF4F8FF;
	static final int EYE_CONTRAST = 70;
	private static final Identifier STOCK = JaymosPowers.id("textures/entity/wind_storm_eyes.png");
	/** Per skin texture: its own white-eyes texture (or the stock one). */
	private static final Map<Identifier, Identifier> BY_SKIN = new HashMap<>();

	private WindStormEyes(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new WindStormEyes(avatar));
			}
		});
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (state.isInvisible || minecraft.level == null) {
			return;
		}
		Entity entity = minecraft.level.getEntity(state.id);
		if (!(entity instanceof Player player) || WindModes.mode(player) != WindModes.BOTH) {
			return;
		}
		collector.order(2).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(eyesFor(state.skin.body().texturePath())), lightCoords,
			OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
	}

	/** The white-eyes texture for {@code skin}, made the first time it's asked for once the skin's pixels can be read. */
	private static Identifier eyesFor(Identifier skin) {
		Identifier known = BY_SKIN.get(skin);
		if (known != null) {
			return known;
		}
		Minecraft minecraft = Minecraft.getInstance();
		NativeImage pixels = null;
		boolean owned = false;
		AbstractTexture texture = minecraft.getTextureManager().getTexture(skin);
		if (texture instanceof DynamicTexture dynamic) {
			pixels = dynamic.getPixels();
		} else {
			Optional<Resource> resource = minecraft.getResourceManager().getResource(skin);
			if (resource.isEmpty()) {
				return STOCK; // still downloading: ask again next frame
			}
			try (InputStream in = resource.get().open()) {
				pixels = NativeImage.read(in);
				owned = true;
			} catch (IOException | RuntimeException e) {
				BY_SKIN.put(skin, STOCK);
				return STOCK;
			}
		}
		Identifier made = STOCK;
		try {
			if (pixels != null && pixels.getWidth() == 64 && pixels.getHeight() >= 32) {
				NativeImage eyes = findEyes(pixels);
				if (eyes != null) {
					made = JaymosPowers.id("wind_storm_eyes/" + Integer.toHexString(skin.hashCode()) + "_" + BY_SKIN.size());
					minecraft.getTextureManager().register(made, new DynamicTexture(made::toString, eyes));
				}
			}
		} finally {
			if (owned) {
				pixels.close();
			}
		}
		BY_SKIN.put(skin, made);
		return made;
	}

	/**
	 * The eyes on a 64x64 skin's face (x 8-15, y 8-15, the hat layer at x 40-47 over it): the row (face y 1-5) with the
	 * most mirrored pairs of columns standing out from the skin tone (x 1-2, or x 3 alone for close-set eyes; ties go
	 * to the stock row, 4), lit white on the face and the hat. Null when nothing pairs up.
	 */
	static NativeImage findEyes(NativeImage skin) {
		int[][] face = new int[8][8];
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				int hat = skin.getPixel(40 + x, 8 + y);
				face[x][y] = ARGB.alpha(hat) > 128 ? hat : skin.getPixel(8 + x, 8 + y);
			}
		}
		int tone = mostCommon(face);
		int bestRow = -1;
		java.util.List<Integer> columns = java.util.List.of();
		for (int row = 1; row <= 5; row++) {
			java.util.List<Integer> found = new java.util.ArrayList<>();
			if (!stands(face, tone, 3, row)) {
				for (int x = 1; x <= 2; x++) {
					if (stands(face, tone, x, row)) {
						found.add(x);
					}
				}
			} else if (!stands(face, tone, 1, row) && !stands(face, tone, 2, row)) {
				found.add(3);
			}
			if (found.size() > columns.size() || found.size() == columns.size() && !found.isEmpty() && Math.abs(row - 4) < Math.abs(bestRow - 4)) {
				columns = found;
				bestRow = row;
			}
		}
		if (bestRow < 0 || columns.isEmpty()) {
			return null;
		}
		NativeImage out = new NativeImage(64, 64, true);
		for (int x : columns) {
			for (int fx : new int[] {x, 7 - x}) {
				out.setPixel(8 + fx, 8 + bestRow, WHITE);
				if (ARGB.alpha(skin.getPixel(40 + fx, 8 + bestRow)) > 128) {
					out.setPixel(40 + fx, 8 + bestRow, WHITE);
				}
			}
		}
		return out;
	}

	private static boolean stands(int[][] face, int tone, int x, int row) {
		return contrast(face[x][row], tone) >= EYE_CONTRAST && contrast(face[7 - x][row], tone) >= EYE_CONTRAST;
	}

	private static int mostCommon(int[][] face) {
		Map<Integer, Integer> counts = new HashMap<>();
		int best = face[0][7], bestCount = 0;
		for (int x = 0; x < 8; x++) {
			for (int y = 2; y < 8; y++) {
				int n = counts.merge(face[x][y] & 0xF0F0F0, 1, Integer::sum);
				if (n > bestCount) {
					bestCount = n;
					best = face[x][y];
				}
			}
		}
		return best;
	}

	private static int contrast(int a, int b) {
		int dr = ARGB.red(a) - ARGB.red(b), dg = ARGB.green(a) - ARGB.green(b), db = ARGB.blue(a) - ARGB.blue(b);
		return (int)Math.sqrt(dr * dr + dg * dg + db * db);
	}
}
