package com.jaymospowers.client.bb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * (Powers 0.17.149, a copy of Kingdoms' own) A Blockbench model in game, baked by {@code tools/bbmodel_bake.py} into {@code bbmodels/<name>.json}: its
 * bones as ModelParts (pivots and resting angles as drawn), every face its own textured quad (so per-face UVs, rotated
 * cubes and inflate come through; vanilla's cubes only know box UVs), and its animations, played onto the parts with
 * {@link #play}. Keyframes go linear, Catmull-Rom or step as Blockbench has them; a Molang value (the procedural loops
 * some exporters write) is worked out each frame by {@link Molang}.
 */
public final class BbModel {
	public final ModelPart root;
	public final Identifier texture;
	private final Map<String, ModelPart> parts = new LinkedHashMap<>();
	private final Map<String, Anim> anims = new HashMap<>();

	private BbModel(ModelPart root, Identifier texture) {
		this.root = root;
		this.texture = texture;
	}

	/** Loads {@code assets/<namespace>/bbmodels/<name>.json}; its texture is {@code textures/entity/bb/<name>.png}. */
	public static BbModel load(String namespace, String name) {
		String path = "/assets/" + namespace + "/bbmodels/" + name + ".json";
		try (InputStream in = BbModel.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("no Blockbench model " + path);
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			return build(json, Identifier.fromNamespaceAndPath(namespace, "textures/entity/bb/" + name + ".png"));
		} catch (java.io.IOException e) {
			throw new IllegalStateException("couldn't read " + path, e);
		}
	}

	private static BbModel build(JsonObject json, Identifier texture) {
		JsonArray bones = json.getAsJsonArray("bones");
		int count = bones.size();
		String[] names = new String[count];
		int[] parents = new int[count];
		List<List<ModelPart.Cube>> cubes = new ArrayList<>();
		PartPose[] poses = new PartPose[count];
		Map<String, Integer> seen = new HashMap<>();
		for (int i = 0; i < count; i++) {
			JsonObject bone = bones.get(i).getAsJsonObject();
			String name = bone.get("name").getAsString();
			int n = seen.merge(name, 1, Integer::sum);
			names[i] = n == 1 ? name : name + "_" + n;
			parents[i] = bone.get("parent").getAsInt();
			float[] pivot = floats(bone.getAsJsonArray("pivot"));
			float[] rot = floats(bone.getAsJsonArray("rot"));
			poses[i] = PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2], rot[0], rot[1], rot[2]);
			cubes.add(cubes(bone.getAsJsonArray("quads")));
		}
		// children before parents: build from the leaves up
		ModelPart[] built = new ModelPart[count];
		List<Map<String, ModelPart>> children = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			children.add(new LinkedHashMap<>());
		}
		for (int i = count - 1; i >= 0; i--) {
			ModelPart part = new ModelPart(cubes.get(i), children.get(i));
			part.setInitialPose(poses[i]);
			part.loadPose(poses[i]);
			built[i] = part;
			if (parents[i] >= 0) {
				// keep Blockbench's order among siblings
				Map<String, ModelPart> siblings = children.get(parents[i]);
				Map<String, ModelPart> reordered = new LinkedHashMap<>();
				reordered.put(names[i], part);
				reordered.putAll(siblings);
				siblings.clear();
				siblings.putAll(reordered);
			}
		}
		Map<String, ModelPart> top = new LinkedHashMap<>();
		for (int i = 0; i < count; i++) {
			if (parents[i] < 0) {
				top.put(names[i], built[i]);
			}
		}
		BbModel model = new BbModel(new ModelPart(List.of(), top), texture);
		for (int i = 0; i < count; i++) {
			model.parts.put(names[i], built[i]);
		}
		JsonObject anims = json.getAsJsonObject("anims");
		if (anims != null) {
			for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
				model.anims.put(e.getKey(), Anim.read(e.getValue().getAsJsonObject(), model.parts));
			}
		}
		return model;
	}

	/** The quads, six to a cube (a vanilla cube's six faces are swapped for ours), the last cube with as many as are left. */
	private static List<ModelPart.Cube> cubes(JsonArray quads) {
		List<ModelPart.Cube> out = new ArrayList<>();
		Direction[] sides = Direction.values();
		for (int start = 0; start < quads.size(); start += 6) {
			int n = Math.min(6, quads.size() - start);
			float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
			float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
			ModelPart.Polygon[] polygons = new ModelPart.Polygon[n];
			for (int q = 0; q < n; q++) {
				float[] f = floats(quads.get(start + q).getAsJsonArray());
				ModelPart.Vertex[] vertices = new ModelPart.Vertex[4];
				for (int v = 0; v < 4; v++) {
					float x = f[v * 5], y = f[v * 5 + 1], z = f[v * 5 + 2];
					vertices[v] = new ModelPart.Vertex(x, y, z, f[v * 5 + 3], f[v * 5 + 4]);
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					minZ = Math.min(minZ, z);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
					maxZ = Math.max(maxZ, z);
				}
				polygons[q] = new ModelPart.Polygon(vertices, new Vector3f(f[20], f[21], f[22]));
			}
			EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
			for (int s = 0; s < n; s++) {
				faces.add(sides[s]);
			}
			ModelPart.Cube cube = new ModelPart.Cube(0, 0, minX, minY, minZ, Math.max(maxX - minX, 0.001F), Math.max(maxY - minY, 0.001F),
				Math.max(maxZ - minZ, 0.001F), 0.0F, 0.0F, 0.0F, false, 16.0F, 16.0F, faces);
			int fill = Math.min(cube.polygons.length, n);
			System.arraycopy(polygons, 0, cube.polygons, 0, fill);
			out.add(cube);
		}
		return out;
	}

	private static float[] floats(JsonArray array) {
		float[] out = new float[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).getAsFloat();
		}
		return out;
	}

	/** A bone by its Blockbench name (the second of two with one name is {@code name_2}). */
	public ModelPart part(String name) {
		ModelPart part = this.parts.get(name);
		if (part == null) {
			throw new IllegalStateException("no bone " + name);
		}
		return part;
	}

	public @Nullable ModelPart find(String name) {
		return this.parts.get(name);
	}

	public boolean has(String anim) {
		return this.anims.containsKey(anim);
	}

	/** An animation's length (seconds), 0 for none. */
	public float length(String anim) {
		Anim a = this.anims.get(anim);
		return a == null ? 0.0F : a.length;
	}

	/**
	 * Adds {@code anim} at {@code seconds} into it, weighted: rotations onto each part's angles, positions onto its pivot,
	 * scales blended from 1. A looping animation wraps; the rest hold their last frame.
	 */
	public void play(String anim, float seconds, float weight) {
		Anim a = this.anims.get(anim);
		if (a == null || weight <= 0.0F) {
			return;
		}
		float t = a.loop && a.length > 0.0F ? seconds % a.length : Math.min(seconds, a.length);
		for (Track track : a.tracks) {
			track.apply(t, weight);
		}
	}

	private record Anim(float length, boolean loop, List<Track> tracks) {
		static Anim read(JsonObject json, Map<String, ModelPart> parts) {
			List<Track> tracks = new ArrayList<>();
			for (JsonElement e : json.getAsJsonArray("tracks")) {
				JsonObject t = e.getAsJsonObject();
				ModelPart part = parts.get(t.get("bone").getAsString());
				if (part == null) {
					continue;
				}
				String ch = t.get("ch").getAsString();
				JsonArray keys = t.getAsJsonArray("keys");
				Key[] out = new Key[keys.size()];
				for (int i = 0; i < out.length; i++) {
					JsonArray k = keys.get(i).getAsJsonArray();
					out[i] = new Key(k.get(0).getAsFloat(), Molang.of(k.get(1)), Molang.of(k.get(2)), Molang.of(k.get(3)), k.get(4).getAsInt());
				}
				tracks.add(new Track(part, ch.equals("rot") ? 0 : ch.equals("pos") ? 1 : 2, out));
			}
			String loop = json.get("loop").getAsString();
			return new Anim(json.get("len").getAsFloat(), loop.equals("loop") || loop.equals("true"), tracks);
		}
	}

	private record Key(float time, Molang x, Molang y, Molang z, int interp) {}

	private record Track(ModelPart part, int channel, Key[] keys) {
		void apply(float t, float weight) {
			float[] v = this.sample(t);
			switch (this.channel) {
				case 0 -> {
					this.part.xRot += v[0] * Mth.DEG_TO_RAD * weight;
					this.part.yRot += v[1] * Mth.DEG_TO_RAD * weight;
					this.part.zRot += v[2] * Mth.DEG_TO_RAD * weight;
				}
				case 1 -> {
					this.part.x += v[0] * weight;
					this.part.y += v[1] * weight;
					this.part.z += v[2] * weight;
				}
				default -> {
					this.part.xScale *= 1.0F + (v[0] - 1.0F) * weight;
					this.part.yScale *= 1.0F + (v[1] - 1.0F) * weight;
					this.part.zScale *= 1.0F + (v[2] - 1.0F) * weight;
				}
			}
		}

		private float[] sample(float t) {
			Key[] k = this.keys;
			if (k.length == 1 || t <= k[0].time) {
				return value(k[0], t);
			}
			for (int i = 0; i < k.length - 1; i++) {
				Key a = k[i], b = k[i + 1];
				if (t > b.time) {
					continue;
				}
				float span = b.time - a.time;
				if (span <= 1.0E-6F || a.interp == 2) {
					return value(a, t);
				}
				float f = (t - a.time) / span;
				float[] va = value(a, t), vb = value(b, t);
				if (a.interp == 1 || b.interp == 1) {
					float[] v0 = value(k[Math.max(i - 1, 0)], t), v3 = value(k[Math.min(i + 2, k.length - 1)], t);
					return new float[] {catmull(v0[0], va[0], vb[0], v3[0], f), catmull(v0[1], va[1], vb[1], v3[1], f), catmull(v0[2], va[2], vb[2], v3[2], f)};
				}
				return new float[] {Mth.lerp(f, va[0], vb[0]), Mth.lerp(f, va[1], vb[1]), Mth.lerp(f, va[2], vb[2])};
			}
			return value(k[k.length - 1], t);
		}

		private static float[] value(Key key, float t) {
			return new float[] {key.x.eval(t), key.y.eval(t), key.z.eval(t)};
		}

		private static float catmull(float p0, float p1, float p2, float p3, float f) {
			float f2 = f * f, f3 = f2 * f;
			return 0.5F * (2.0F * p1 + (p2 - p0) * f + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * f2 + (3.0F * p1 - p0 - 3.0F * p2 + p3) * f3);
		}
	}
}
