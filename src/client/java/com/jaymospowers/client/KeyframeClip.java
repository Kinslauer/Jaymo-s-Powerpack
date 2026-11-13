package com.jaymospowers.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jaymospowers.JaymosPowers;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * One animation out of a GeckoLib (Bedrock format) {@code .animation.json}, the kind Blockbench saves, read for a model
 * that isn't a GeckoLib one: the vanilla player model, whose parts our poses set by hand. Andy (2026-11-12): "can we also
 * revisit the getup animation with gecko lib?" The rise from downed is now a keyframed clip in
 * {@code geckolib/animations/entity/player_getup.animation.json}, so it can be opened and reworked in Blockbench like any
 * of the pack's GeckoLib animations, and {@link CrawlPose} plays it.
 * <p>
 * Only bone rotations are read, in degrees, with Blockbench's signs: GeckoLib turns a bone's x and y the other way from
 * a vanilla {@code ModelPart} ({@link #partX}, {@link #partY} give the part's radians). Keyframes are {@code linear} or
 * {@code catmullrom}; anything else is read as linear. A missing or broken file reads as null, and the caller keeps its
 * old hand-written pose.
 */
public final class KeyframeClip {
	private record Key(float time, float[] value, boolean smooth) {
	}

	private final float length;
	private final Map<String, List<Key>> rotations;

	private KeyframeClip(float length, Map<String, List<Key>> rotations) {
		this.length = length;
		this.rotations = rotations;
	}

	/** Reads {@code animation} from {@code assets/<namespace>/geckolib/animations/entity/<file>.animation.json}, or null. */
	public static @Nullable KeyframeClip load(String namespace, String file, String animation) {
		String path = "/assets/" + namespace + "/geckolib/animations/entity/" + file + ".animation.json";
		try (InputStream in = KeyframeClip.class.getResourceAsStream(path)) {
			if (in == null) {
				return null;
			}
			JsonObject anim = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
				.getAsJsonObject("animations").getAsJsonObject(animation);
			Map<String, List<Key>> rotations = new HashMap<>();
			for (Map.Entry<String, JsonElement> bone : anim.getAsJsonObject("bones").entrySet()) {
				JsonObject channels = bone.getValue().getAsJsonObject();
				if (!channels.has("rotation")) {
					continue;
				}
				List<Key> keys = new ArrayList<>();
				JsonElement rotation = channels.get("rotation");
				if (rotation.isJsonArray()) {
					keys.add(new Key(0.0F, vector(rotation), false));
				} else {
					for (Map.Entry<String, JsonElement> key : rotation.getAsJsonObject().entrySet()) {
						JsonElement v = key.getValue();
						boolean smooth = false;
						if (v.isJsonObject()) {
							JsonObject o = v.getAsJsonObject();
							smooth = o.has("lerp_mode") && "catmullrom".equals(o.get("lerp_mode").getAsString());
							v = o.has("vector") ? o.get("vector") : o.get("post");
						}
						keys.add(new Key(Float.parseFloat(key.getKey()), vector(v), smooth));
					}
				}
				keys.sort((a, b) -> Float.compare(a.time, b.time));
				rotations.put(bone.getKey(), keys);
			}
			float length = anim.has("animation_length") ? anim.get("animation_length").getAsFloat() : 1.0F;
			return new KeyframeClip(length, rotations);
		} catch (RuntimeException | java.io.IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't read the animation {} in {}", animation, path, e);
			return null;
		}
	}

	private static float[] vector(JsonElement v) {
		var a = v.getAsJsonArray();
		return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	/** How long the clip runs (seconds). */
	public float length() {
		return this.length;
	}

	/** Whether the clip turns {@code bone}. */
	public boolean has(String bone) {
		return this.rotations.containsKey(bone);
	}

	/** {@code bone}'s rotation (degrees, Blockbench signs) on {@code axis} (0 x, 1 y, 2 z) at {@code share} (0 to 1) of the clip. */
	public float degrees(String bone, int axis, float share) {
		List<Key> keys = this.rotations.get(bone);
		if (keys == null || keys.isEmpty()) {
			return 0.0F;
		}
		float time = Mth.clamp(share, 0.0F, 1.0F) * this.length;
		if (time <= keys.get(0).time) {
			return keys.get(0).value[axis];
		}
		for (int i = 1; i < keys.size(); i++) {
			Key b = keys.get(i);
			if (time > b.time) {
				continue;
			}
			Key a = keys.get(i - 1);
			float t = b.time - a.time <= 1.0E-5F ? 1.0F : (time - a.time) / (b.time - a.time);
			if (!a.smooth && !b.smooth) {
				return Mth.lerp(t, a.value[axis], b.value[axis]);
			}
			// Catmull-Rom through the keys either side, as Blockbench draws it
			float p0 = keys.get(Math.max(0, i - 2)).value[axis];
			float p3 = keys.get(Math.min(keys.size() - 1, i + 1)).value[axis];
			return catmullRom(t, p0, a.value[axis], b.value[axis], p3);
		}
		return keys.get(keys.size() - 1).value[axis];
	}

	/** {@code bone}'s turn about x as a vanilla model part's ({@code xRot}, radians). */
	public float partX(String bone, float share) {
		return -degrees(bone, 0, share) * Mth.DEG_TO_RAD;
	}

	/** {@code bone}'s turn about y as a vanilla model part's ({@code yRot}, radians). */
	public float partY(String bone, float share) {
		return -degrees(bone, 1, share) * Mth.DEG_TO_RAD;
	}

	/** {@code bone}'s turn about z as a vanilla model part's ({@code zRot}, radians). */
	public float partZ(String bone, float share) {
		return degrees(bone, 2, share) * Mth.DEG_TO_RAD;
	}

	private static float catmullRom(float t, float p0, float p1, float p2, float p3) {
		float t2 = t * t, t3 = t2 * t;
		return 0.5F * (2.0F * p1 + (-p0 + p2) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2 + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
	}
}
