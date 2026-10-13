package com.jaymoskingdoms.client.equestrian;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.equestrian.Bonding;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.jaymoskingdoms.equestrian.Tack;
import com.jaymoskingdoms.equestrian.Tackroom;
import com.jaymoskingdoms.equestrian.WarElephant;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.44) A horse's tack drawn on it in 3D ({@link Bonding}): the war and racing saddles (a vanilla saddle is the Mo'
 * Creatures saddle, drawn by {@link MocHorseModel}, which also draws the headstall and reins any saddle comes with), the
 * bridle and its reins, the blanket (in its dye) and horseshoes. The pieces are boxes on {@link MocHorseModel}'s bones,
 * read from {@code assets/jaymoskingdoms/tack/tack.json} ({@code tools/gen_tack.py}); the models follow the horse's own
 * animation (they're horse models themselves). Also carries the kneel onto the render state, for
 * {@code LivingEntityRendererMixin}.
 */
public final class TackLayer extends RenderLayer<MocHorseRenderer.State, MocHorseModel> {
	/** What it wears: its saddle (0 none or vanilla's, 1 war, 2 racing), bridle, blanket colour (0 none), shoes; ridden; how far it kneels (0-1). */
	public record Look(int saddle, boolean bridle, int blanket, boolean shod, boolean ridden, float kneel) {
	}

	public static final RenderStateDataKey<Look> LOOK = RenderStateDataKey.create(() -> "jaymoskingdoms:tack_look");
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/tack/tack.png");
	private static final Identifier BLANKET_TEXTURE = JaymosKingdoms.id("textures/entity/tack/tack_blanket.png");
	/** The bones the pieces hang on, and the groups they come in. */
	private static final String[] BONES = {"body", "saddle", "stirrup_left", "stirrup_right", "head", "rein_left", "rein_right",
		"leg1_c", "leg2_c", "leg3_c", "leg4_c"};
	private static final String[] GROUPS = {"war_saddle", "racing_saddle", "bridle", "bridle_reins", "shoes", "blanket"};
	/** A kneel takes this many ticks to go down (and come up). */
	private static final float KNEEL_TICKS = 6.0F;
	private static @Nullable LayerDefinition mainLayer;
	private static @Nullable LayerDefinition blanketLayer;

	private final @Nullable TackModel main;
	private final @Nullable TackModel blanket;

	TackLayer(RenderLayerParent<MocHorseRenderer.State, MocHorseModel> parent) {
		super(parent);
		this.main = mainLayer == null ? null : new TackModel(mainLayer.bakeRoot());
		this.blanket = blanketLayer == null ? null : new TackModel(blanketLayer.bakeRoot());
	}

	/** Reads the tack's shapes (at start-up, before the horse renderers are made). */
	public static void register() {
		JsonObject json = read();
		if (json == null) {
			return;
		}
		List<JsonObject> main = new ArrayList<>();
		List<JsonObject> blanket = new ArrayList<>();
		for (JsonElement e : json.getAsJsonArray("pieces")) {
			JsonObject p = e.getAsJsonObject();
			("blanket".equals(p.get("layer").getAsString()) ? blanket : main).add(p);
		}
		mainLayer = layer(main, json.getAsJsonArray("texture_size"));
		blanketLayer = layer(blanket, json.getAsJsonArray("blanket_texture_size"));
	}

	private static @Nullable JsonObject read() {
		String path = "/assets/" + JaymosKingdoms.MOD_ID + "/tack/tack.json";
		try (InputStream in = TackLayer.class.getResourceAsStream(path)) {
			if (in == null) {
				JaymosKingdoms.LOGGER.warn("Equestrian: no tack shapes ({})", path);
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			JaymosKingdoms.LOGGER.error("Equestrian: couldn't read {}", path, e);
			return null;
		}
	}

	/** The horse's bones, without its own boxes, carrying the tack's pieces (each group's pieces under one child, to show or hide). */
	private static LayerDefinition layer(List<JsonObject> pieces, JsonArray size) {
		MeshDefinition mesh = MocHorseModel.bones();
		PartDefinition root = mesh.getRoot();
		Map<String, PartDefinition> groups = new HashMap<>();
		int n = 0;
		for (JsonObject p : pieces) {
			String bone = p.get("bone").getAsString();
			String group = p.get("group").getAsString();
			PartDefinition parent = groups.computeIfAbsent(bone + "/" + group,
				k -> root.getChild(bone).addOrReplaceChild("tack_" + group, CubeListBuilder.create(), PartPose.ZERO));
			float[] pos = floats(p.getAsJsonArray("pos"));
			float[] dims = floats(p.getAsJsonArray("size"));
			float[] rot = p.has("rot") ? floats(p.getAsJsonArray("rot")) : new float[3];
			JsonArray uv = p.getAsJsonArray("uv");
			CubeDeformation grow = p.has("grow") ? new CubeDeformation(p.get("grow").getAsFloat()) : CubeDeformation.NONE;
			parent.addOrReplaceChild("p" + n++,
				CubeListBuilder.create().texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).addBox(pos[0], pos[1], pos[2], dims[0], dims[1], dims[2], grow),
				PartPose.rotation(rot[0] * Mth.DEG_TO_RAD, rot[1] * Mth.DEG_TO_RAD, rot[2] * Mth.DEG_TO_RAD));
		}
		return LayerDefinition.create(mesh, size.get(0).getAsInt(), size.get(1).getAsInt());
	}

	private static float[] floats(JsonArray array) {
		float[] out = new float[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).getAsFloat();
		}
		return out;
	}

	/** (From {@code AbstractHorseRendererMixin}) what {@code horse} wears, onto its render state. */
	public static void extract(AbstractHorse horse, EquineRenderState state, float partialTicks) {
		if (!Equestrian.covered(horse) || horse instanceof WarElephant || state.isBaby) {
			state.setData(LOOK, null);
			return;
		}
		ItemStack saddle = horse.getItemBySlot(EquipmentSlot.SADDLE);
		int kind = saddle.is(Tackroom.WAR_SADDLE) ? 1 : saddle.is(Tackroom.RACING_SADDLE) ? 2 : 0;
		Tack t = Bonding.tack(horse);
		int blanket = 0;
		if (!t.blanket().isEmpty()) {
			DyedItemColor dyed = t.blanket().get(DataComponents.DYED_COLOR);
			blanket = ARGB.opaque(dyed != null ? dyed.rgb() : Tackroom.BLANKET_COLOR);
		}
		var b = horse.getAttached(Bonding.BOND);
		boolean shod = b != null && b.shod();
		Long until = horse.getAttached(Bonding.KNEEL);
		float kneel = 0.0F;
		if (until != null) {
			float left = until - (horse.level().getGameTime() + partialTicks);
			kneel = Mth.clamp(Math.min(left, 30.0F - left) / KNEEL_TICKS, 0.0F, 1.0F);
		}
		if (kind == 0 && t.empty() && !shod && kneel <= 0.0F) {
			state.setData(LOOK, null);
			return;
		}
		state.setData(LOOK, new Look(kind, !t.bridle().isEmpty(), blanket, shod, horse.isVehicle(), kneel));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, MocHorseRenderer.State state, float yRot, float xRot) {
		Look look = state.getData(LOOK);
		if (look == null || state.isInvisible) {
			return;
		}
		if (look.blanket() != 0 && this.blanket != null) {
			renderColoredCutoutModel(this.blanket, BLANKET_TEXTURE, poseStack, collector, lightCoords, state, look.blanket(), 1);
		}
		if ((look.saddle() != 0 || look.bridle() || look.shod()) && this.main != null) {
			renderColoredCutoutModel(this.main, TEXTURE, poseStack, collector, lightCoords, state, -1, 3);
		}
	}

	/** The tack's pieces on the horse's bones; each group shown as the render state says. */
	private static final class TackModel extends MocHorseModel {
		private final Map<String, List<ModelPart>> groups = new HashMap<>();
		private final List<ModelPart> bones = new ArrayList<>();

		TackModel(ModelPart root) {
			super(root);
			for (String bone : BONES) {
				ModelPart part = root.getChild(bone);
				this.bones.add(part);
				for (String group : GROUPS) {
					if (part.hasChild("tack_" + group)) {
						this.groups.computeIfAbsent(group, g -> new ArrayList<>()).add(part.getChild("tack_" + group));
					}
				}
			}
		}

		@Override
		public void setupAnim(MocHorseRenderer.State state) {
			super.setupAnim(state);
			Look look = state.getData(LOOK);
			if (look == null) {
				return;
			}
			// the horse model hides its own saddle and headstall bones; ours hang on them all the same
			for (ModelPart bone : this.bones) {
				bone.visible = true;
			}
			show("war_saddle", look.saddle() == 1);
			show("racing_saddle", look.saddle() == 2);
			show("bridle", look.bridle());
			show("bridle_reins", look.bridle() && look.ridden());
			show("shoes", look.shod());
			show("blanket", true);
		}

		private void show(String group, boolean visible) {
			for (ModelPart part : this.groups.getOrDefault(group, List.of())) {
				part.visible = visible;
			}
		}
	}
}
