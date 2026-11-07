package com.jaymospowers.client.face;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Motion patch 3) Living faces on players, after Actions &amp; Stuff's face animations: they blink every few seconds, their
 * pupils shift towards whoever is near them, and their brows come down when they're hurt or swinging at something.
 * <p>Done on the skin itself, like Kitchen's closed eyes on the dead ({@code ClosedEyes}): the skin is read once and,
 * only when its head front has a plain two-pixel eye pair (a white beside a pupil, the same row each side), copies are
 * painted with the pupils to one side or the other and with frowning brows. Skins with odd eyes (one pixel, three
 * wide, glowing, none) are left exactly as they are. The blink is Kitchen's closed eyes, held for a few ticks.
 * Worked out from synced state (where others stand, the hurt flash, the swing), so everyone sees the same face; a player
 * knocked out or dead keeps the shut eyes they already have.
 */
public final class PlayerFaces {
	// ===== the knobs ================================================================================================
	/** A blink every this many ticks (at least, plus up to the spread), lasting this many ticks. */
	static final int BLINK_EVERY = 60, BLINK_SPREAD = 60, BLINK_TICKS = 3;
	/** Pupils look at the nearest living thing within this far (blocks) once it's this many degrees off the face. */
	static final double LOOK_RANGE = 8.0;
	static final float LOOK_OFF = 18.0F;
	/** How often (ticks) each player looks about again. */
	static final int LOOK_CHECK_TICKS = 10;
	/** Eye pixels: a white (luminance at least, colour spread at most), and a pupil this far in colour from the skin. */
	static final int WHITE_MIN = 170, WHITE_SPREAD = 55, PUPIL_FROM_SKIN = 60;
	/** The frowning brow: this share of the skin's colour. */
	static final float BROW_SHADE = 0.45F;
	// ================================================================================================================

	/** Pupils: {@code -1} to the texture's left (the player's right), {@code 0} as painted, {@code 1} the other way. */
	public record Face(int look, boolean frown) {
	}

	public static final RenderStateDataKey<Face> KEY = RenderStateDataKey.create(() -> "jaymospowers:face");

	/** Per skin: its painted faces (index {@link #index}), or an empty array when its eyes can't be found. */
	private static final Map<Identifier, Identifier[]> PAINTED = new HashMap<>();
	private static int made;
	/** Per entity id: when it last looked about, and which way its pupils went. */
	private static final Map<Integer, long[]> LOOKED = new HashMap<>();

	private PlayerFaces() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player) || !(state instanceof AvatarRenderState avatar) || state.deathTime > 0.0F
			|| com.jaymoskitchen.client.corpse.ClosedEyes.closed(state)) {
			state.setData(KEY, null);
			return;
		}
		// a blink now and then, its timing its own per player
		long time = player.level().getGameTime();
		int id = player.getId();
		int period = BLINK_EVERY + Math.floorMod(id * 7919, BLINK_SPREAD);
		if (Math.floorMod(time + id * 31L, period) < BLINK_TICKS && !player.isSleeping()) {
			com.jaymoskitchen.client.corpse.ClosedEyes.mark(state);
			state.setData(KEY, null);
			return;
		}
		boolean frown = player.hurtTime > 0 || avatar.swingAnimation > 0.0F;
		int look;
		long[] seen = LOOKED.get(id);
		if (seen != null && time - seen[0] < LOOK_CHECK_TICKS && time >= seen[0]) {
			look = (int)seen[1];
		} else {
			look = look(player);
			LOOKED.put(id, new long[] {time, look});
		}
		state.setData(KEY, look == 0 && !frown ? null : new Face(look, frown));
	}

	/** Which way the pupils go: towards the nearest living thing close by and well off to the side of the face. */
	private static int look(Player player) {
		Entity nearest = null;
		double best = LOOK_RANGE * LOOK_RANGE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(LOOK_RANGE),
			e -> e != player && e.isAlive() && !e.isInvisible())) {
			double d = e.distanceToSqr(player);
			if (d < best) {
				best = d;
				nearest = e;
			}
		}
		if (nearest == null) {
			return 0;
		}
		Vec3 to = nearest.getEyePosition().subtract(player.getEyePosition());
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float off = Mth.wrapDegrees(yaw - player.getYHeadRot());
		if (Math.abs(off) < LOOK_OFF || Math.abs(off) > 100.0F) {
			return 0;
		}
		// turning right (a growing yaw) is the player's right, which is the texture's left on the face
		return off > 0.0F ? -1 : 1;
	}

	private static int index(Face face) {
		return (face.look() + 1) * 2 + (face.frown() ? 1 : 0);
	}

	/** The skin to draw {@code state} with: {@code texture} with its face moved, when it has one to move. */
	public static Identifier texture(Object state, Identifier texture) {
		if (!(state instanceof AvatarRenderState avatar)) {
			return texture;
		}
		Face face = avatar.getData(KEY);
		if (face == null) {
			return texture;
		}
		// (a skin still downloading can't be read yet: nothing is kept, so it's tried again)
		Identifier[] faces = PAINTED.computeIfAbsent(texture, PlayerFaces::paint);
		if (faces == null || faces.length == 0) {
			return texture;
		}
		Identifier painted = faces[index(face)];
		return painted == null ? texture : painted;
	}

	/** Reads the skin and paints its faces; an empty array when its eyes aren't a plain pair, null if it can't be read yet. */
	private static Identifier @Nullable [] paint(Identifier texture) {
		NativeImage skin = pixels(texture);
		if (skin == null) {
			return null;
		}
		try {
			Eyes eyes = find(skin);
			if (eyes == null) {
				return new Identifier[0];
			}
			Identifier[] faces = new Identifier[6];
			for (int look = -1; look <= 1; look++) {
				for (int f = 0; f < 2; f++) {
					Face face = new Face(look, f == 1);
					if (look == 0 && f == 0) {
						continue;
					}
					NativeImage copy = new NativeImage(skin.getWidth(), skin.getHeight(), true);
					copy.copyFrom(skin);
					eyes.paint(copy, face);
					Identifier id = JaymosPowers.id("face/" + made++);
					Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, copy));
					faces[index(face)] = id;
				}
			}
			return faces;
		} finally {
			skin.close();
		}
	}

	/**
	 * Where the eyes are: the row, each eye's left pixel (texture x), its white and pupil colours, and the pixel size
	 * ({@code s}, for HD skins). Each eye is two pixels: one white, one pupil.
	 */
	private record Eyes(int s, int row, int leftEye, int rightEye, int white, int pupilLeft, int pupilRight, int skin) {
		void paint(NativeImage image, Face face) {
			eye(image, leftEye, pupilLeft, face.look(), false, face.frown());
			eye(image, rightEye, pupilRight, face.look(), true, face.frown());
		}

		private void eye(NativeImage image, int x, int pupil, int look, boolean right, boolean frown) {
			if (look != 0) {
				int pupilAt = look < 0 ? x : x + s;
				int whiteAt = look < 0 ? x + s : x;
				fill(image, pupilAt, row, pupil);
				fill(image, whiteAt, row, white);
			}
			if (frown) {
				// the inner end of the brow comes down over the eye: the pixel above the eye nearer the nose
				int inner = right ? x : x + s;
				int brow = ARGB.color(255, Math.round(ARGB.red(skin) * BROW_SHADE), Math.round(ARGB.green(skin) * BROW_SHADE),
					Math.round(ARGB.blue(skin) * BROW_SHADE));
				fill(image, inner, row - s, brow);
			}
		}

		private void fill(NativeImage image, int x, int y, int colour) {
			for (int dy = 0; dy < s; dy++) {
				for (int dx = 0; dx < s; dx++) {
					image.setPixel(x + dx, y + dy, colour);
				}
			}
		}
	}

	/** Looks for a plain eye pair on the head's front (a 64-wide skin's x 8-15, y 8-15, scaled for HD skins). */
	static @Nullable Eyes find(NativeImage image) {
		int s = image.getWidth() / 64;
		if (s < 1 || image.getHeight() < 16 * s) {
			return null;
		}
		int x0 = 8 * s, y0 = 8 * s;
		int skin = skinColour(image, x0, y0, s);
		// the eyes sit in the upper middle of the face: rows 2 to 5 of 8
		for (int r = 2; r <= 5; r++) {
			int y = y0 + r * s;
			int left = eyeAt(image, x0, x0 + 4 * s, y, s, skin), right = eyeAt(image, x0 + 4 * s, x0 + 8 * s, y, s, skin);
			if (left >= 0 && right >= 0) {
				int white = isWhite(image.getPixel(left, y)) ? image.getPixel(left, y) : image.getPixel(left + s, y);
				int pupilLeft = isWhite(image.getPixel(left, y)) ? image.getPixel(left + s, y) : image.getPixel(left, y);
				int pupilRight = isWhite(image.getPixel(right, y)) ? image.getPixel(right + s, y) : image.getPixel(right, y);
				return new Eyes(s, y, left, right, white, pupilLeft, pupilRight, skin);
			}
		}
		return null;
	}

	/** The left x of the one white-and-pupil pair in [from, to) on this row, or -1 (none, or more than one eye-ish run). */
	private static int eyeAt(NativeImage image, int from, int to, int y, int s, int skin) {
		int found = -1, odd = 0;
		for (int x = from; x < to; x += s) {
			int c = image.getPixel(x, y);
			if (ARGB.alpha(c) < 128) {
				return -1;
			}
			boolean white = isWhite(c), pupil = !white && distance(c, skin) > PUPIL_FROM_SKIN;
			if (!white && !pupil) {
				continue;
			}
			odd++;
			if (x + s < to) {
				int n = image.getPixel(x + s, y);
				boolean nWhite = isWhite(n), nPupil = !nWhite && distance(n, skin) > PUPIL_FROM_SKIN;
				if (white && nPupil || pupil && nWhite) {
					if (found >= 0) {
						return -1;
					}
					found = x;
					x += s;
					odd--;
				}
			}
		}
		// exactly one two-pixel eye and nothing else odd beside it on this side of the row
		return odd == 0 ? found : -1;
	}

	private static boolean isWhite(int c) {
		return luminance(c) >= WHITE_MIN && spread(c) <= WHITE_SPREAD;
	}

	/** The face's most common colour (the bottom half of the head front: cheeks and chin, mostly skin). */
	private static int skinColour(NativeImage image, int x0, int y0, int s) {
		Map<Integer, Integer> count = new HashMap<>();
		for (int y = y0 + 5 * s; y < y0 + 8 * s; y += s) {
			for (int x = x0; x < x0 + 8 * s; x += s) {
				count.merge(image.getPixel(x, y), 1, Integer::sum);
			}
		}
		return count.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0xFFC69C7E);
	}

	private static int luminance(int c) {
		return (ARGB.red(c) * 299 + ARGB.green(c) * 587 + ARGB.blue(c) * 114) / 1000;
	}

	private static int spread(int c) {
		int r = ARGB.red(c), g = ARGB.green(c), b = ARGB.blue(c);
		return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
	}

	private static int distance(int a, int b) {
		return Math.abs(ARGB.red(a) - ARGB.red(b)) + Math.abs(ARGB.green(a) - ARGB.green(b)) + Math.abs(ARGB.blue(a) - ARGB.blue(b));
	}

	private static @Nullable NativeImage pixels(Identifier texture) {
		Minecraft minecraft = Minecraft.getInstance();
		var resource = minecraft.getResourceManager().getResource(texture);
		if (resource.isPresent()) {
			try (var in = resource.get().open()) {
				return NativeImage.read(in);
			} catch (Exception e) {
				return null;
			}
		}
		AbstractTexture made = minecraft.getTextureManager().getTexture(texture);
		if (!(made instanceof DynamicTexture dynamic) || dynamic.getPixels() == null) {
			return null;
		}
		NativeImage copy = new NativeImage(dynamic.getPixels().getWidth(), dynamic.getPixels().getHeight(), true);
		copy.copyFrom(dynamic.getPixels());
		return copy;
	}
}
