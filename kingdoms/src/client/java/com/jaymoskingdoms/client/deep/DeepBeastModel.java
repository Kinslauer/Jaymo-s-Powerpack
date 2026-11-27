package com.jaymoskingdoms.client.deep;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A deep beast's model: Rim's rig (CC0), ported by {@code tools/rim_port.py} to
 * {@code assets/jaymoskingdoms/deep_models/<kind>.json}: parts, each with a pivot and rotation (degrees) relative to its
 * parent and its boxes (origin, size, where on the sheet), the way a vanilla model is built. A part with no parent in the
 * file hangs from the root. It is moved by the names the rigs share: legs and arms swing as it walks, the tail segments
 * ({@code tail1}, {@code tail2}...) and necks sway, the jaw opens on a bite, wings stir.
 */
final class DeepBeastModel extends EntityModel<DeepBeastState> {
	/** A rig read: the sheet's size and its parts. */
	record Spec(int width, int height, List<Part> parts) {
	}

	record Part(String name, String parent, float[] pivot, float[] rotation, List<Cube> cubes) {
	}

	record Cube(float[] origin, float[] size, int[] uv) {
	}

	/** How far a stride swings the legs and the arms, and how fast the stride turns over (it is a giant: slow steps). */
	static final float LEG_SWING = 0.7F, ARM_SWING = 0.45F, STRIDE = 0.3F;
	/** How wide its jaw opens on a bite (radians), and how far the tail sways at rest. */
	static final float JAW_OPEN = 0.9F, TAIL_REST = 0.1F;

	private final Map<String, ModelPart> parts = new HashMap<>();
	private final List<ModelPart> tail = new ArrayList<>();

	DeepBeastModel(ModelPart root, Spec spec) {
		super(root);
		for (Part p : spec.parts()) {
			ModelPart parent = this.parts.getOrDefault(p.parent(), root);
			ModelPart part = parent.getChild(p.name());
			this.parts.put(p.name(), part);
			if (p.name().matches("tail\\d+")) {
				this.tail.add(part);
			}
		}
	}

	static Spec load(String name) {
		String path = "/assets/jaymoskingdoms/deep_models/" + name + ".json";
		try (Reader in = new InputStreamReader(DeepBeastModel.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
			JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
			List<Part> parts = new ArrayList<>();
			for (JsonElement e : o.getAsJsonArray("parts")) {
				JsonObject p = e.getAsJsonObject();
				List<Cube> cubes = new ArrayList<>();
				for (JsonElement c : p.getAsJsonArray("cubes")) {
					JsonObject cube = c.getAsJsonObject();
					int[] uv = new int[2];
					uv[0] = cube.getAsJsonArray("uv").get(0).getAsInt();
					uv[1] = cube.getAsJsonArray("uv").get(1).getAsInt();
					cubes.add(new Cube(floats(cube.getAsJsonArray("origin")), floats(cube.getAsJsonArray("size")), uv));
				}
				parts.add(new Part(p.get("name").getAsString(), p.get("parent").getAsString(), floats(p.getAsJsonArray("pivot")),
					floats(p.getAsJsonArray("rotation")), cubes));
			}
			return new Spec(o.get("width").getAsInt(), o.get("height").getAsInt(), parts);
		} catch (Exception e) {
			throw new IllegalStateException("deep beast model " + name, e);
		}
	}

	private static float[] floats(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsFloat();
		}
		return out;
	}

	static LayerDefinition layer(Spec spec) {
		MeshDefinition mesh = new MeshDefinition();
		Map<String, PartDefinition> defs = new HashMap<>();
		for (Part p : spec.parts()) {
			PartDefinition parent = defs.getOrDefault(p.parent(), mesh.getRoot());
			CubeListBuilder cubes = CubeListBuilder.create();
			for (Cube c : p.cubes()) {
				cubes.texOffs(c.uv()[0], c.uv()[1]).addBox(c.origin()[0], c.origin()[1], c.origin()[2], c.size()[0], c.size()[1], c.size()[2]);
			}
			float[] r = p.rotation();
			defs.put(p.name(), parent.addOrReplaceChild(p.name(), cubes, PartPose.offsetAndRotation(p.pivot()[0], p.pivot()[1], p.pivot()[2],
				r[0] * Mth.DEG_TO_RAD, r[1] * Mth.DEG_TO_RAD, r[2] * Mth.DEG_TO_RAD)));
		}
		return LayerDefinition.create(mesh, spec.width(), spec.height());
	}

	private void add(String name, float x, float y, float z) {
		ModelPart p = this.parts.get(name);
		if (p != null) {
			p.xRot += x;
			p.yRot += y;
			p.zRot += z;
		}
	}

	@Override
	public void setupAnim(DeepBeastState state) {
		super.setupAnim(state);
		float phase = state.walkAnimationPos * STRIDE, speed = Math.min(1.0F, state.walkAnimationSpeed * 2.0F), age = state.ageInTicks;
		float swing = Mth.cos(phase), counter = Mth.cos(phase + Mth.PI);
		add("right_leg", swing * LEG_SWING * speed, 0.0F, 0.0F);
		add("left_leg", counter * LEG_SWING * speed, 0.0F, 0.0F);
		// a knee bends as the leg comes forward
		add("right_shin", Math.max(0.0F, -swing) * LEG_SWING * speed, 0.0F, 0.0F);
		add("left_shin", Math.max(0.0F, -counter) * LEG_SWING * speed, 0.0F, 0.0F);
		add("right_arm", counter * ARM_SWING * speed, 0.0F, 0.0F);
		add("left_arm", swing * ARM_SWING * speed, 0.0F, 0.0F);
		// the head looks where it is told, the neck follows a little, and it breathes
		float yaw = Mth.clamp(state.yRot, -50.0F, 50.0F) * Mth.DEG_TO_RAD, pitch = Mth.clamp(state.xRot, -30.0F, 30.0F) * Mth.DEG_TO_RAD;
		add("neck", pitch * 0.3F, yaw * 0.3F, 0.0F);
		add("head", pitch * 0.7F, yaw * 0.7F, 0.0F);
		add("body", Mth.sin(age * 0.05F) * 0.015F, 0.0F, 0.0F);
		add("jaw", state.bite * JAW_OPEN + (Mth.sin(age * 0.08F) + 1.0F) * 0.03F, 0.0F, 0.0F);
		add("right_wing", 0.0F, 0.0F, Mth.sin(age * 0.07F) * 0.08F);
		add("left_wing", 0.0F, 0.0F, -Mth.sin(age * 0.07F) * 0.08F);
		for (int i = 0; i < this.tail.size(); i++) {
			this.tail.get(i).yRot += Mth.sin(age * 0.05F - i * 0.6F) * TAIL_REST + Mth.cos(phase - i * 0.5F) * 0.12F * speed;
		}
	}
}
