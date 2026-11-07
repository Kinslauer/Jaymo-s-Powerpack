package com.jaymospowers.client.baubles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.costume.HoodedCloak;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.37) Andy: "Would this trinkets' system animate any other baubles? If so i want them to be integrated". Every
 * bauble worn in a slot that takes it is drawn on the body: hats on the head, scarves and pendants at the neck, charms
 * and pouches on the belt, gloves, bracers and rings on the hands, shoes on the feet, the featherfall cloak on the back.
 * <p>The boxes and textures are our own ({@code tools/gen_worn_looks.py}, which writes {@code worn_looks.json} and
 * {@code textures/entity/worn/}; see docs/Worn-Looks-Spec.md): Artifacts' worn models were looked at, not copied. Each
 * look is a player model with only its own parts, built on the player's bones so it follows every pose. Scarf tails,
 * pendants, charms, hanging pouches, hat feathers and the cloak swing with the stride; parts sit out over armour when the
 * matching piece is worn. Lanterns, the coin purse, the bottles at the hip, the lantern pole, fairies and quivers keep
 * their own drawing.
 * <p>(0.21.37) Andy: "Can we not port their looks too?" The baubles that kept their Artifacts look now use Artifacts' own
 * worn models ({@code artifacts/worn/WornArtifacts}); the looks here are for our starters and the sixteen we repainted.
 */
public final class WornLooks {
	// ===== the knobs ================================================================================================
	/** How far a part is scaled out about its bone's middle over armour: head, chest (neck and hands), legs (belt), feet. */
	static final float[] OVER_HELMET = {1.22F, 1.22F, 1.22F}, OVER_CHEST_BODY = {1.22F, 1.04F, 1.5F},
		OVER_CHEST_ARM = {1.45F, 1.0F, 1.45F}, OVER_LEGS = {1.1F, 1.0F, 1.15F}, OVER_BOOTS = {1.3F, 1.0F, 1.3F};
	/** Idle sway (fraction of a swing's amplitude) and its speed (radians a tick). */
	static final float IDLE = 0.15F, IDLE_SPEED = 0.09F;
	/** A cape-like part lifts back by this many of its amplitudes at a run. */
	static final float CAPE_LIFT = 3.0F;
	// ================================================================================================================

	private static final String JSON = "/assets/jaymospowers/worn_looks.json";
	/** The ring slots are 1 and 2; the second's ring goes on the left hand. */
	private static final int SECOND_RING_SLOT = 2;

	/** What a player shows this frame: each slot's item id (null where nothing with a look is worn). */
	record Shown(@Nullable Identifier[] slots) {
	}

	static final RenderStateDataKey<Shown> KEY = RenderStateDataKey.create(() -> "jaymospowers:worn_looks");
	/** Every slot's worn item id this frame, for the Artifacts looks ({@code artifacts/worn/WornArtifacts}). */
	public static final RenderStateDataKey<List<Identifier>> WORN = RenderStateDataKey.create(() -> "jaymospowers:worn_bauble_ids");

	/** One look, read from the json: its texture and the parts to build. */
	record Look(Identifier item, Identifier texture, int width, int height, boolean hideCape, List<Part> parts, boolean hasRing) {
	}

	record Box(float u, float v, float[] from, float[] size, float inflate, boolean fit) {
	}

	record Swing(char axis, float amp, float phase, String kind) {
	}

	record Part(String bone, float[] pivot, float[] rot, List<Box> boxes, @Nullable Swing swing, String sides, @Nullable String armour,
		List<Part> children) {
	}

	private static final Map<Identifier, Look> LOOKS = new LinkedHashMap<>();

	private WornLooks() {
	}

	public static void init() {
		load();
		// (0.21.37) the baubles that kept their Artifacts look are drawn with Artifacts' own models
		com.jaymospowers.client.baubles.artifacts.worn.WornArtifacts.init();
		for (Look look : LOOKS.values()) {
			for (Variant variant : Variant.values()) {
				if (variant.ringLeft && !look.hasRing) {
					continue;
				}
				ModelLayerRegistry.registerModelLayer(layer(look, variant), () -> build(look, variant));
			}
		}
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar, context.getModelSet()));
			}
		});
	}

	/** Whether {@code item} has a worn look. */
	public static boolean has(Identifier item) {
		return LOOKS.containsKey(item);
	}

	/** From the avatar renderer's extractRenderState: what this player shows; hides the cape under a cloak. */
	public static void extract(Avatar entity, AvatarRenderState state) {
		if (!(entity instanceof Player player)) {
			state.setData(KEY, null);
			state.setData(WORN, null);
			return;
		}
		List<Identifier> looks = Baubles.looks(player);
		state.setData(WORN, looks.isEmpty() ? null : looks);
		Identifier[] slots = null;
		for (int i = 0; i < looks.size(); i++) {
			Look look = LOOKS.get(looks.get(i));
			if (look == null) {
				continue;
			}
			// (0.21.48) a cloak hood worn as a bauble is drawn by HoodedCloakLayer, not here: skip it while the hood is down
			Identifier lookId = looks.get(i);
			if (slots == null) {
				slots = new Identifier[looks.size()];
			}
			slots[i] = look.item;
			if (look.hideCape) {
				state.showCape = false;
			}
		}
		state.setData(KEY, slots == null ? null : new Shown(slots));
	}

	// ---- loading and building -----------------------------------------------------------------------------------

	private static void load() {
		try (InputStream in = WornLooks.class.getResourceAsStream(JSON)) {
			if (in == null) {
				JaymosPowers.LOGGER.warn("No worn bauble looks ({} missing)", JSON);
				return;
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				JsonObject o = entry.getValue().getAsJsonObject();
				List<Part> parts = parts(o.getAsJsonArray("parts"));
				Identifier item = Identifier.parse(entry.getKey());
				LOOKS.put(item, new Look(item, Identifier.parse(o.get("texture").getAsString()), o.get("width").getAsInt(),
					o.get("height").getAsInt(), o.get("hide_cape").getAsBoolean(), parts, anyRing(parts)));
			}
		} catch (Exception e) {
			JaymosPowers.LOGGER.error("Couldn't read the worn bauble looks", e);
		}
	}

	private static boolean anyRing(List<Part> parts) {
		for (Part part : parts) {
			if (part.sides.equals("slot") || anyRing(part.children)) {
				return true;
			}
		}
		return false;
	}

	private static List<Part> parts(JsonArray array) {
		List<Part> out = new ArrayList<>();
		for (JsonElement e : array) {
			JsonObject o = e.getAsJsonObject();
			List<Box> boxes = new ArrayList<>();
			for (JsonElement b : o.getAsJsonArray("boxes")) {
				JsonObject bo = b.getAsJsonObject();
				JsonArray uv = bo.getAsJsonArray("uv");
				boxes.add(new Box(uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), floats(bo.getAsJsonArray("frm")),
					floats(bo.getAsJsonArray("size")), bo.get("inflate").getAsFloat(), bo.get("fit").getAsBoolean()));
			}
			Swing swing = null;
			if (o.has("swing") && o.get("swing").isJsonObject()) {
				JsonObject so = o.getAsJsonObject("swing");
				swing = new Swing(so.get("axis").getAsString().charAt(0), so.get("amp").getAsFloat(), so.get("phase").getAsFloat(),
					so.get("kind").getAsString());
			}
			String armour = o.has("armour") && !o.get("armour").isJsonNull() ? o.get("armour").getAsString() : null;
			out.add(new Part(o.get("bone").getAsString(), floats(o.getAsJsonArray("pivot")), floats(o.getAsJsonArray("rot")), boxes,
				swing, o.get("sides").getAsString(), armour, parts(o.getAsJsonArray("children"))));
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

	/** Model shape (wide or slim arms) and, for rings, which hand: the first ring slot's on the right, the second's on the left. */
	enum Variant {
		WIDE(false, false), SLIM(true, false), WIDE_LEFT_RING(false, true), SLIM_LEFT_RING(true, true);

		final boolean slim, ringLeft;

		Variant(boolean slim, boolean ringLeft) {
			this.slim = slim;
			this.ringLeft = ringLeft;
		}

		static Variant of(boolean slim, boolean ringLeft) {
			return ringLeft ? (slim ? SLIM_LEFT_RING : WIDE_LEFT_RING) : (slim ? SLIM : WIDE);
		}
	}

	static ModelLayerLocation layer(Look look, Variant variant) {
		return new ModelLayerLocation(JaymosPowers.id("worn/" + look.item.getNamespace() + "/" + look.item.getPath()),
			variant.name().toLowerCase(java.util.Locale.ROOT));
	}

	/** Names each built part {@code p<n>} under its bone so the model can find it again, in the same order as {@link #placed}. */
	static LayerDefinition build(Look look, Variant variant) {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, variant.slim);
		PartDefinition root = mesh.getRoot().clearRecursively();
		int[] counter = {0};
		for (Part part : look.parts) {
			for (Placed placed : placements(part, variant)) {
				add(root.getChild(placed.bone), placed, variant, counter);
			}
		}
		return LayerDefinition.create(mesh, look.width, look.height);
	}

	/** A part as placed on one side: its bone, whether it's mirrored onto the left. */
	record Placed(Part part, String bone, boolean mirrored) {
	}

	static List<Placed> placements(Part part, Variant variant) {
		List<Placed> out = new ArrayList<>(2);
		String right = part.bone, left = part.bone.replace("right_", "left_");
		boolean limb = !left.equals(right);
		switch (part.sides) {
			case "both" -> {
				out.add(new Placed(part, right, false));
				if (limb) {
					out.add(new Placed(part, left, true));
				}
			}
			case "left" -> out.add(new Placed(part, left, limb));
			case "slot" -> out.add(variant.ringLeft ? new Placed(part, left, limb) : new Placed(part, right, false));
			default -> out.add(new Placed(part, right, false));
		}
		return out;
	}

	private static void add(PartDefinition parent, Placed placed, Variant variant, int[] counter) {
		Part part = placed.part;
		boolean arm = placed.bone.endsWith("_arm"), mirror = placed.mirrored;
		float sign = mirror ? -1.0F : 1.0F;
		CubeListBuilder cubes = CubeListBuilder.create().mirror(mirror);
		for (Box box : part.boxes) {
			float x = box.from[0], w = box.size[0];
			if (arm && variant.slim && box.fit) {
				// slim arms are a pixel narrower
				x += 0.5F;
				w -= 1.0F;
			}
			if (mirror) {
				x = -(x + w);
			}
			cubes.texOffs((int) box.u, (int) box.v)
				.addBox(x, box.from[1], box.from[2], w, box.size[1], box.size[2], new CubeDeformation(box.inflate));
		}
		float px = part.pivot[0];
		// arm parts sit on the arm's middle, which on slim arms is half a pixel in toward the body
		if (arm && variant.slim && isTop(part)) {
			px += 0.5F;
		}
		PartDefinition child = parent.addOrReplaceChild("p" + counter[0]++, cubes,
			PartPose.offsetAndRotation(sign * px, part.pivot[1], part.pivot[2], part.rot[0], sign * part.rot[1], sign * part.rot[2]));
		for (Part sub : part.children) {
			add(child, new Placed(sub, placed.bone, mirror), variant, counter);
		}
	}

	/** Top-level arm parts are the ones pivoted on the arm's middle (x -1 of the right arm). */
	private static boolean isTop(Part part) {
		return part.bone.endsWith("_arm") && Math.abs(part.pivot[0] + 1.0F) < 0.01F;
	}

	// ---- the model and the layer --------------------------------------------------------------------------------

	/** One look on one player model: its parts, found again in the order they were built, each with its pose to sway from. */
	static final class Model extends PlayerModel {
		final Look look;
		final List<Moving> moving = new ArrayList<>();

		record Moving(ModelPart part, Part spec, boolean mirrored, boolean top) {
		}

		Model(ModelPart root, Look look, Variant variant) {
			super(root, variant.slim);
			this.look = look;
			int[] counter = {0};
			for (Part part : look.parts) {
				for (Placed placed : placements(part, variant)) {
					find(bone(placed.bone), placed.part, placed.mirrored, true, counter);
				}
			}
		}

		private ModelPart bone(String name) {
			return switch (name) {
				case "head" -> this.head;
				case "right_arm" -> this.rightArm;
				case "left_arm" -> this.leftArm;
				case "right_leg" -> this.rightLeg;
				case "left_leg" -> this.leftLeg;
				default -> this.body;
			};
		}

		private void find(ModelPart parent, Part part, boolean mirrored, boolean top, int[] counter) {
			ModelPart self = parent.getChild("p" + counter[0]++);
			moving.add(new Moving(self, part, mirrored, top));
			for (Part sub : part.children) {
				find(self, sub, mirrored, false, counter);
			}
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float stride = state.walkAnimationPos * 0.6662F;
			float pace = Math.min(1.0F, state.walkAnimationSpeed);
			for (Moving m : moving) {
				ModelPart part = m.part;
				part.resetPose();
				Swing swing = m.spec.swing;
				if (swing != null) {
					float phase = swing.phase + (m.mirrored && swing.kind.equals("stride") ? Mth.PI : 0.0F);
					float idle = IDLE * Mth.sin(state.ageInTicks * IDLE_SPEED + phase);
					float angle = switch (swing.kind) {
						case "cape" -> swing.amp * (CAPE_LIFT * pace + 0.6F * Mth.sin(stride * 2.0F + phase) * pace + idle);
						case "hang" -> swing.amp * (0.6F * Mth.sin(stride + phase) * pace + idle);
						default -> swing.amp * (Mth.sin(stride + phase) * pace + idle);
					};
					float sign = m.mirrored && swing.axis != 'x' ? -1.0F : 1.0F;
					switch (swing.axis) {
						case 'y' -> part.yRot += sign * angle;
						case 'z' -> part.zRot += sign * angle;
						default -> part.xRot += angle;
					}
				}
				if (m.top && m.spec.armour != null) {
					float[] scale = overArmour(state, m.spec);
					if (scale != null) {
						part.xScale = scale[0];
						part.yScale = scale[1];
						part.zScale = scale[2];
					}
				}
			}
		}

		private static float @Nullable [] overArmour(AvatarRenderState state, Part part) {
			return switch (part.armour) {
				case "head" -> state.headEquipment.isEmpty() ? null : OVER_HELMET;
				case "chest" -> state.chestEquipment.isEmpty() ? null : part.bone.endsWith("_arm") ? OVER_CHEST_ARM : OVER_CHEST_BODY;
				case "legs" -> state.legsEquipment.isEmpty() ? null : OVER_LEGS;
				case "feet" -> state.feetEquipment.isEmpty() ? null : OVER_BOOTS;
				default -> null;
			};
		}
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final EntityModelSet models;
		private final Map<ModelLayerLocation, Model> baked = new HashMap<>();

		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityModelSet models) {
			super(renderer);
			this.models = models;
		}

		private Model model(Look look, Variant variant) {
			ModelLayerLocation location = layer(look, variant);
			return baked.computeIfAbsent(location, l -> new Model(models.bakeLayer(l), look, variant));
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Shown shown = state.getData(KEY);
			if (shown == null || state.isInvisible) {
				return;
			}
			boolean slim = state.skin != null && state.skin.model() == PlayerModelType.SLIM;
			Identifier[] slots = shown.slots();
			for (int i = 0; i < slots.length; i++) {
				Look look = slots[i] == null ? null : LOOKS.get(slots[i]);
				if (look == null) {
					continue;
				}
				boolean ringLeft = look.hasRing && i == SECOND_RING_SLOT;
				Model model = model(look, Variant.of(slim, ringLeft));
				collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(look.texture), lightCoords,
					OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
			}
		}
	}
}
