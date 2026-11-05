package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.resources.FaceList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.13) MCA's side of {@link McaLooks}; only loaded with MCA. The bodies are MCA's skin layer's body (wide arms, the
 * 64x64 skin, no wear parts), a little past MCA's skin and face (skin 0, face 0.01) so the looks don't fight them, posed
 * the way MCA poses its own layers (they take the copy of the body's pose its skin layer kept). The eyes are MCA's face
 * texture for this villager (its face gene), every pixel lit, as Kingdoms' {@code McaEyes}.
 */
final class McaSkin {
	static final float MARKS_INFLATE = 0.005F, GLOW_INFLATE = 0.02F;
	private static @Nullable PlayerEntityExtendedModel<?> marks, glow;
	private static final Map<String, Identifier> EYES = new HashMap<>();

	private McaSkin() {
	}

	static boolean draws(PlayerModel parent) {
		return parent instanceof PlayerEntityExtendedModel<?>;
	}

	static @Nullable PlayerModel marks(PlayerModel parent) {
		if (!(parent instanceof PlayerEntityExtendedModel<?> player)) {
			return null;
		}
		if (marks == null) {
			marks = create(MARKS_INFLATE);
		}
		return pose(player, marks);
	}

	static @Nullable PlayerModel glow(PlayerModel parent) {
		if (!(parent instanceof PlayerEntityExtendedModel<?> player)) {
			return null;
		}
		if (glow == null) {
			glow = create(GLOW_INFLATE);
		}
		return pose(player, glow);
	}

	private static PlayerEntityExtendedModel<?> create(float inflate) {
		PlayerEntityExtendedModel<?> model = new PlayerEntityExtendedModel<>(
			LayerDefinition.create(VillagerEntityModelMCA.bodyData(new CubeDeformation(inflate), false), 64, 64).bakeRoot(), false);
		return model.receiveDeferredAnimationPose().hideWears();
	}

	private static PlayerEntityExtendedModel<?> pose(PlayerEntityExtendedModel<?> parent, PlayerEntityExtendedModel<?> model) {
		parent.copyPropertiesTo(model);
		model.copyVisibility(parent);
		return model;
	}

	/** MCA's eyes for the villager body drawn with {@code state}, lit {@code color}; null if it isn't one or can't be read. */
	static @Nullable Identifier eyes(Object state, int color) {
		Identifier face;
		try {
			FaceList faces = FaceList.getInstance();
			if (faces == null) {
				return null;
			}
			face = faces.pick("normal", VillagerVisuals.require(state).faceGene());
		} catch (RuntimeException e) {
			return null;
		}
		if (face == null) {
			return null;
		}
		String key = face + "#" + Integer.toHexString(color);
		if (EYES.containsKey(key)) {
			return EYES.get(key);
		}
		Minecraft minecraft = Minecraft.getInstance();
		Optional<Resource> resource = minecraft.getResourceManager().getResource(face);
		Identifier made = null;
		if (resource.isPresent()) {
			try (InputStream in = resource.get().open(); NativeImage pixels = NativeImage.read(in)) {
				NativeImage eyes = new NativeImage(pixels.getWidth(), pixels.getHeight(), true);
				for (int x = 0; x < pixels.getWidth(); x++) {
					for (int y = 0; y < pixels.getHeight(); y++) {
						if (ARGB.alpha(pixels.getPixel(x, y)) > 0) {
							eyes.setPixel(x, y, color);
						}
					}
				}
				made = JaymosPowers.id("mca_eyes/" + Integer.toHexString(key.hashCode()) + "_" + EYES.size());
				Identifier id = made;
				minecraft.getTextureManager().register(made, new DynamicTexture(id::toString, eyes));
			} catch (java.io.IOException | RuntimeException e) {
				made = null;
			}
		}
		EYES.put(key, made);
		return made;
	}
}
