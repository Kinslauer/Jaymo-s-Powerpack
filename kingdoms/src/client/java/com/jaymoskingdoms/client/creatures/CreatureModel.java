package com.jaymoskingdoms.client.creatures;

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
 * A creature's model, built from its shape file ({@code assets/jaymoskingdoms/creature_models/<name>.json}, written together
 * with its texture by {@code tools/gen_creatures.py}): a list of boxes, each with a pivot, a size, where it sits on the
 * sheet, and how it moves ("head" turns, "legR"/"legL" and "armR"/"armL" swing, "tail" sways, "feeler" quivers, "wingL"/"wingR"
 * flap, "wobble" bobs, "crawl" writhes).
 */
final class CreatureModel extends EntityModel<CreatureState> {
	/** A shape file read: its sheet size, scale, whether it is drawn see-through, and its boxes. */
	record Spec(int width, int height, float scale, boolean translucent, List<Box> parts) {
	}

	record Box(String name, String parent, float[] pivot, float[] box, int[] uv, String anim) {
	}

	private record Moving(ModelPart part, String anim, int index) {
	}

	private final ModelPart root;
	private final List<Moving> moving = new ArrayList<>();

	CreatureModel(ModelPart root, Spec spec) {
		super(root);
		this.root = root;
		Map<String, ModelPart> found = new HashMap<>();
		int i = 0;
		for (Box b : spec.parts()) {
			ModelPart parent = b.parent() == null ? root : found.get(b.parent());
			ModelPart part = parent.getChild(b.name());
			found.put(b.name(), part);
			if (!b.anim().isEmpty()) {
				this.moving.add(new Moving(part, b.anim(), i++));
			}
		}
	}

	static Spec load(String name) {
		String path = "/assets/jaymoskingdoms/creature_models/" + name + ".json";
		try (Reader in = new InputStreamReader(CreatureModel.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
			JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
			List<Box> parts = new ArrayList<>();
			for (JsonElement e : o.getAsJsonArray("parts")) {
				JsonObject p = e.getAsJsonObject();
				parts.add(new Box(p.get("name").getAsString(), p.get("parent").isJsonNull() ? null : p.get("parent").getAsString(), floats(p.getAsJsonArray("pivot")),
					floats(p.getAsJsonArray("box")), ints(p.getAsJsonArray("uv")), p.get("anim").getAsString()));
			}
			return new Spec(o.get("width").getAsInt(), o.get("height").getAsInt(), o.get("scale").getAsFloat(), o.get("translucent").getAsBoolean(), parts);
		} catch (Exception e) {
			throw new IllegalStateException("creature model " + name, e);
		}
	}

	private static float[] floats(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsFloat();
		}
		return out;
	}

	private static int[] ints(JsonArray a) {
		int[] out = new int[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsInt();
		}
		return out;
	}

	static LayerDefinition layer(Spec spec) {
		MeshDefinition mesh = new MeshDefinition();
		Map<String, PartDefinition> defs = new HashMap<>();
		for (Box b : spec.parts()) {
			PartDefinition parent = b.parent() == null ? mesh.getRoot() : defs.get(b.parent());
			float[] x = b.box();
			PartDefinition def = parent.addOrReplaceChild(b.name(), CubeListBuilder.create().texOffs(b.uv()[0], b.uv()[1])
				.addBox(x[0], x[1], x[2], x[3], x[4], x[5]), PartPose.offset(b.pivot()[0], b.pivot()[1], b.pivot()[2]));
			defs.put(b.name(), def);
		}
		return LayerDefinition.create(mesh, spec.width(), spec.height());
	}

	@Override
	public void setupAnim(CreatureState state) {
		super.setupAnim(state);
		float walk = state.walkAnimationPos, speed = state.walkAnimationSpeed, age = state.ageInTicks;
		if (state.lie) {
			this.root.xRot = -Mth.HALF_PI;
		}
		for (Moving m : this.moving) {
			ModelPart p = m.part();
			switch (m.anim()) {
				case "head" -> {
					p.yRot += state.yRot * Mth.DEG_TO_RAD;
					p.xRot += state.xRot * Mth.DEG_TO_RAD;
				}
				case "legR" -> p.xRot += Mth.cos(walk * 0.6662F) * 1.4F * speed;
				case "legL" -> p.xRot += Mth.cos(walk * 0.6662F + Mth.PI) * 1.4F * speed;
				case "armR" -> {
					p.xRot += Mth.cos(walk * 0.6662F + Mth.PI) * 1.2F * speed;
					p.zRot += Mth.cos(age * 0.09F) * 0.05F + 0.05F;
				}
				case "armL" -> {
					p.xRot += Mth.cos(walk * 0.6662F) * 1.2F * speed;
					p.zRot -= Mth.cos(age * 0.09F) * 0.05F + 0.05F;
				}
				case "tail" -> p.yRot += Mth.cos(walk * 0.6662F) * 0.4F * speed + Mth.sin(age * 0.08F + m.index()) * 0.12F;
				case "feeler" -> {
					p.yRot += Mth.sin(age * 0.3F + m.index() * 1.7F) * 0.25F;
					p.xRot += Mth.sin(age * 0.21F + m.index()) * 0.1F;
				}
				case "wingL" -> p.zRot += state.flaps ? Mth.sin(age * 0.9F) * 0.7F : 1.1F;
				case "wingR" -> p.zRot -= state.flaps ? Mth.sin(age * 0.9F) * 0.7F : 1.1F;
				case "wobble" -> {
					p.y += Mth.sin(age * 0.1F) * 0.8F;
					p.yScale = 1.0F + Mth.sin(age * 0.12F) * 0.03F;
				}
				case "crawl" -> p.zRot += Mth.sin(age * 0.6F) * 0.3F;
				default -> {
				}
			}
		}
	}
}
