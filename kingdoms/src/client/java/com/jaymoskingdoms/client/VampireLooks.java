package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Vampires;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.22) What everyone sees of a vampire player: ashen skin on the face and hands always; at night, fangs and red
 * eyes that glow in the dark. Textures from {@code tools/gen_vampire_looks.py}, laid over the player's own skin. The
 * red eyes go where that skin's own eyes are ({@link #findEyes}), worked out once per skin; the stock texture's
 * spots (the default skins' eyes) are the fallback while a skin downloads or when no eyes can be told apart.
 */
public final class VampireLooks extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** On a player's render state: 1 = a vampire by day, 2 = by night; absent for anyone else. */
	public static final RenderStateDataKey<Integer> LOOK = RenderStateDataKey.create(() -> "jaymoskingdoms:vampire_look");

	private static final Identifier PALE = JaymosKingdoms.id("textures/entity/vampire/pale.png");
	private static final Identifier FANGS = JaymosKingdoms.id("textures/entity/vampire/fangs.png");
	private static final Identifier EYES = JaymosKingdoms.id("textures/entity/vampire/eyes.png");

	/** Per skin texture: its own red-eyes texture (or the stock one). */
	private static final Map<Identifier, Identifier> EYES_BY_SKIN = new HashMap<>();
	/** How far (RGB distance) a face pixel must be from the skin tone to count as part of an eye. */
	static final int EYE_CONTRAST = 70;
	static final int GLOW = 0xFFFF1E18;

	private VampireLooks(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new VampireLooks(avatar));
			}
		});
	}

	/** Called from the render-state mixin: what {@code entity} shows this frame. */
	public static Integer look(LivingEntity entity) {
		if (!(entity instanceof Player) || !Vampires.isVampire(entity)) {
			return null;
		}
		return Vampires.night(entity.level().getOverworldClockTime()) ? 2 : 1;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Integer look = state.getData(LOOK);
		if (look == null || state.isInvisible) {
			return;
		}
		PlayerModel model = this.getParentModel();
		collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityTranslucent(PALE), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
			state.outlineColor);
		if (look == 2) {
			collector.order(2).submitModel(model, state, poseStack, RenderTypes.entityCutout(FANGS), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
			collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(eyesFor(state.skin.body().texturePath())), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
		}
	}

	/** The red-eyes texture for {@code skin}, made the first time it's asked for once the skin's pixels can be read. */
	private static Identifier eyesFor(Identifier skin) {
		Identifier known = EYES_BY_SKIN.get(skin);
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
				return EYES; // still downloading: ask again next frame
			}
			try (InputStream in = resource.get().open()) {
				pixels = NativeImage.read(in);
				owned = true;
			} catch (IOException | RuntimeException e) {
				EYES_BY_SKIN.put(skin, EYES);
				return EYES;
			}
		}
		Identifier made = EYES;
		try {
			if (pixels != null && pixels.getWidth() == 64 && pixels.getHeight() >= 32) {
				NativeImage eyes = findEyes(pixels);
				if (eyes != null) {
					made = JaymosKingdoms.id("vampire_eyes/" + Integer.toHexString(skin.hashCode()) + "_" + EYES_BY_SKIN.size());
					minecraft.getTextureManager().register(made, new DynamicTexture(made::toString, eyes));
				}
			}
		} finally {
			if (owned) {
				pixels.close();
			}
		}
		EYES_BY_SKIN.put(skin, made);
		return made;
	}

	/**
	 * Finds the eyes on a 64x64 skin's face (x 8-15, y 8-15, with the hat layer at x 40-47 over it): the row between 9
	 * and 13 with the most mirrored eye columns standing out from the skin tone ({@link #eyeColumns}; ties go to the
	 * stock row, 12), plus the row below or above when the same columns stand out there too. Where the hat layer covers an eye, it's painted there too, so masks and glasses glow through.
	 * Null when nothing pairs up (a blank or masked face): the stock spots are used.
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
		int bestRow = -1, bestPairs = 0;
		java.util.List<Integer> columns = java.util.List.of();
		for (int row = 1; row <= 5; row++) {
			java.util.List<Integer> found = eyeColumns(face, tone, row);
			if (found.size() > bestPairs || found.size() == bestPairs && !found.isEmpty() && Math.abs(row - 4) < Math.abs(bestRow - 4)) {
				bestPairs = found.size();
				bestRow = row;
				columns = found;
			}
		}
		if (bestRow < 0) {
			return null;
		}
		final int row = bestRow;
		NativeImage out = new NativeImage(64, 64, true);
		for (int x : columns) {
			for (int dy : new int[] {0, 1, -1}) {
				int y = row + dy;
				if (y < 1 || y > 5 || dy != 0 && !eyeColumns(face, tone, y).contains(x)) {
					continue;
				}
				for (int fx : new int[] {x, 7 - x}) {
					out.setPixel(8 + fx, 8 + y, GLOW);
					if (ARGB.alpha(skin.getPixel(40 + fx, 8 + y)) > 128) {
						out.setPixel(40 + fx, 8 + y, GLOW);
					}
				}
				if (dy != 0) {
					break; // one extra row at most
				}
			}
		}
		return out;
	}

	/**
	 * The eye columns (left-half face x, mirrored on the right) standing out in {@code row}: x 1-2 (the usual eyes,
	 * skin x 9-10 and 13-14) when the bridge between them (x 3-4) is plain skin, so a fringe of hair across the whole
	 * row doesn't count; or x 3 alone (close-set eyes, x 11 and 12) when 1-2 are plain skin. The outer column (x 0) is
	 * left out: it's mostly hair or sideburns.
	 */
	private static java.util.List<Integer> eyeColumns(int[][] face, int tone, int row) {
		java.util.List<Integer> out = new java.util.ArrayList<>();
		boolean bridge = stands(face, tone, 3, row);
		if (!bridge) {
			for (int x = 1; x <= 2; x++) {
				if (stands(face, tone, x, row)) {
					out.add(x);
				}
			}
		} else if (!stands(face, tone, 1, row) && !stands(face, tone, 2, row) && !stands(face, tone, 0, row)) {
			out.add(3);
		}
		return out;
	}

	/** Column x (and its mirror) of {@code row} stand out from the skin tone. */
	private static boolean stands(int[][] face, int tone, int x, int row) {
		return contrast(face[x][row], tone) >= EYE_CONTRAST && contrast(face[7 - x][row], tone) >= EYE_CONTRAST;
	}

	private static int mostCommon(int[][] face) {
		Map<Integer, Integer> counts = new HashMap<>();
		int best = face[0][7], bestCount = 0;
		for (int x = 0; x < 8; x++) {
			for (int y = 2; y < 8; y++) {
				int c = face[x][y] & 0xF0F0F0; // near enough counts as the same shade
				int n = counts.merge(c, 1, Integer::sum);
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
		return (int) Math.sqrt(dr * dr + dg * dg + db * db);
	}
}
