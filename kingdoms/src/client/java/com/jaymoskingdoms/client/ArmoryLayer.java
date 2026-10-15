package com.jaymoskingdoms.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jaymoskingdoms.Armory;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.27) The raised 3D pieces of {@link Armory}'s sets (crests, pauldrons, horns, skirts), drawn over the flat armour on
 * anything humanoid that wears them: players, Kingdoms' folk, zombies, villagers with a humanoid model. Each set's pieces
 * are read from {@code assets/jaymoskingdoms/armory/<set>.json} ({@code tools/gen_armory.py}) and baked into one model per
 * slot and layer: "main" in its own colours, "cloth" (greyscale) tinted by the piece's dye, or the set's cloth colour.
 * The models follow the wearer's head, body and limbs by copying the parent model's pose each frame, and, being humanoid
 * models themselves ({@link WornModel}), take every custom pose and Fresh Animations' movement the vanilla armour takes.
 */
public final class ArmoryLayer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** A set as read from its JSON: one layer definition per slot and layer (absent when it has no pieces there). */
	private record SetShape(Map<EquipmentSlot, LayerDefinition> main, Map<EquipmentSlot, LayerDefinition> cloth, int clothColor) {
	}

	private static final Map<String, SetShape> SHAPES = new HashMap<>();
	private static final Map<String, Map<EquipmentSlot, ModelLayerLocation>> MAIN_LAYERS = new HashMap<>();
	private static final Map<String, Map<EquipmentSlot, ModelLayerLocation>> CLOTH_LAYERS = new HashMap<>();

	/** Baked per renderer: set → slot → model. */
	private final Map<String, Map<EquipmentSlot, Piece>> main = new HashMap<>();
	private final Map<String, Map<EquipmentSlot, Piece>> cloth = new HashMap<>();

	private ArmoryLayer(RenderLayerParent<S, HumanoidModel<S>> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		for (String set : SHAPES.keySet()) {
			main.put(set, bake(context, MAIN_LAYERS.get(set)));
			cloth.put(set, bake(context, CLOTH_LAYERS.get(set)));
		}
	}

	private Map<EquipmentSlot, Piece> bake(EntityRendererProvider.Context context, Map<EquipmentSlot, ModelLayerLocation> layers) {
		Map<EquipmentSlot, Piece> out = new EnumMap<>(EquipmentSlot.class);
		layers.forEach((slot, layer) -> out.put(slot, new Piece(context.bakeLayer(layer), this::getParentModel)));
		return out;
	}

	/** Reads the sets' shapes and registers their model layers, and puts this layer on every humanoid renderer. */
	public static void register() {
		for (String set : Armory.SETS.keySet()) {
			SetShape shape = read(set);
			if (shape == null) {
				continue;
			}
			SHAPES.put(set, shape);
			MAIN_LAYERS.put(set, layers(set, "main", shape.main()));
			CLOTH_LAYERS.put(set, layers(set, "cloth", shape.cloth()));
		}
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer, context));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static ArmoryLayer<?> create(LivingEntityRenderer<?, ?, ?> renderer, EntityRendererProvider.Context context) {
		return new ArmoryLayer((RenderLayerParent) renderer, context);
	}

	private static Map<EquipmentSlot, ModelLayerLocation> layers(String set, String layer, Map<EquipmentSlot, LayerDefinition> definitions) {
		Map<EquipmentSlot, ModelLayerLocation> out = new EnumMap<>(EquipmentSlot.class);
		definitions.forEach((slot, definition) -> {
			ModelLayerLocation location = new ModelLayerLocation(JaymosKingdoms.id("armory/" + set + "_" + slot.getName()), layer);
			ModelLayerRegistry.registerModelLayer(location, () -> definition);
			out.put(slot, location);
		});
		return out;
	}

	private static @Nullable SetShape read(String set) {
		String path = "/assets/" + JaymosKingdoms.MOD_ID + "/armory/" + set + ".json";
		try (InputStream in = ArmoryLayer.class.getResourceAsStream(path)) {
			if (in == null) {
				JaymosKingdoms.LOGGER.warn("Armory: no shape for {} ({})", set, path);
				return null;
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray size = json.getAsJsonArray("texture_size");
			JsonArray clothSize = json.getAsJsonArray("cloth_texture_size");
			Map<EquipmentSlot, MeshDefinition> mainMeshes = new EnumMap<>(EquipmentSlot.class);
			Map<EquipmentSlot, MeshDefinition> clothMeshes = new EnumMap<>(EquipmentSlot.class);
			int counter = 0;
			for (JsonElement element : json.getAsJsonArray("pieces")) {
				JsonObject piece = element.getAsJsonObject();
				EquipmentSlot slot = EquipmentSlot.byName(piece.get("slot").getAsString());
				boolean isCloth = "cloth".equals(piece.get("layer").getAsString());
				MeshDefinition mesh = (isCloth ? clothMeshes : mainMeshes).computeIfAbsent(slot, s -> skeleton());
				PartDefinition bone = mesh.getRoot().getChild(piece.get("bone").getAsString());
				float[] pos = floats(piece.getAsJsonArray("pos"));
				float[] dims = floats(piece.getAsJsonArray("size"));
				int[] uv = {piece.getAsJsonArray("uv").get(0).getAsInt(), piece.getAsJsonArray("uv").get(1).getAsInt()};
				float[] pivot = piece.has("pivot") ? floats(piece.getAsJsonArray("pivot")) : new float[3];
				float[] rot = piece.has("rot") ? floats(piece.getAsJsonArray("rot")) : new float[3];
				CubeDeformation grow = piece.has("grow") ? new CubeDeformation(piece.get("grow").getAsFloat()) : CubeDeformation.NONE;
				bone.addOrReplaceChild(
					"p" + counter++,
					CubeListBuilder.create().texOffs(uv[0], uv[1]).addBox(pos[0], pos[1], pos[2], dims[0], dims[1], dims[2], grow),
					PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2], rot[0], rot[1], rot[2])
				);
			}
			Map<EquipmentSlot, LayerDefinition> mainDefs = new EnumMap<>(EquipmentSlot.class);
			Map<EquipmentSlot, LayerDefinition> clothDefs = new EnumMap<>(EquipmentSlot.class);
			mainMeshes.forEach((slot, mesh) -> mainDefs.put(slot, LayerDefinition.create(mesh, size.get(0).getAsInt(), size.get(1).getAsInt())));
			clothMeshes.forEach((slot, mesh) -> clothDefs.put(slot, LayerDefinition.create(mesh, clothSize.get(0).getAsInt(), clothSize.get(1).getAsInt())));
			return new SetShape(mainDefs, clothDefs, ARGB.opaque(json.get("cloth_color").getAsInt()));
		} catch (Exception e) {
			JaymosKingdoms.LOGGER.error("Armory: couldn't read {}", path, e);
			return null;
		}
	}

	private static float[] floats(JsonArray array) {
		float[] out = new float[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).getAsFloat();
		}
		return out;
	}

	/** The humanoid's bones, empty, at their usual places (their poses are copied from the wearer each frame anyway). */
	private static MeshDefinition skeleton() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		return mesh;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = switch (slot) {
				case HEAD -> state.headEquipment;
				case CHEST -> state.chestEquipment;
				case LEGS -> state.legsEquipment;
				default -> state.feetEquipment;
			};
			if (stack.isEmpty()) {
				continue;
			}
			String set = Armory.setOf(stack.getItem());
			if (set == null || !SHAPES.containsKey(set)) {
				continue;
			}
			Piece piece = main.get(set).get(slot);
			if (piece != null) {
				collector.submitModel(piece, state, poseStack, RenderTypes.armorCutoutNoCull(texture(set, false)), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
					state.outlineColor);
			}
			Piece clothPiece = cloth.get(set).get(slot);
			if (clothPiece != null) {
				DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
				int color = dyed != null ? ARGB.opaque(dyed.rgb()) : SHAPES.get(set).clothColor();
				collector.submitModel(clothPiece, state, poseStack, RenderTypes.armorCutoutNoCull(texture(set, true)), lightCoords, OverlayTexture.NO_OVERLAY, color, null,
					state.outlineColor);
			}
		}
	}

	private static final Map<String, Identifier> TEXTURES = new HashMap<>();

	private static Identifier texture(String set, boolean cloth) {
		return TEXTURES.computeIfAbsent(set + (cloth ? "_cloth" : ""), name -> JaymosKingdoms.id("textures/entity/armory/" + name + ".png"));
	}

	/** One slot's pieces of a set, posed as the wearer is (WornModel). */
	private static final class Piece extends WornModel {
		Piece(ModelPart root, Supplier<HumanoidModel<?>> wearer) {
			super(root, wearer);
		}
	}
}
