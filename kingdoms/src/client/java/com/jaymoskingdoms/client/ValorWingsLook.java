package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Valor;
import com.jaymoskingdoms.ValorWings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.jaymoskingdoms.client.bb.BbModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
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
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * (0.7.410) A Skyward Valor's wings ({@link ValorWings}), drawn on whoever has them out: the Valor's own look (iron plates,
 * stone, scales, ash feathers, a heat haze, black lacquer; {@code tools/gen_valor_wings.py}), fading as the pool runs down,
 * the King's coating's soul colour beating along their ribs. They move the way Xenoform Wyrm's do: in flight they beat, and
 * fold back at speed. At rest (coated, on the ground) they lie wound round the arms like a coating ({@link SleevesModel},
 * {@code tools/gen_valor_wing_sleeves.py}); when they're used (taking off, catching a blow) they draw up off the arms and
 * unfurl from the back, and wind back down when it's over. All of them wispy: see-through, thinning away at the edges.
 * <p>
 * (0.7.480) They used to wrap round the body like Xenoform Wyrm's cloak, swung forward round the sides and closed across
 * the chest, which looked like wings growing out through the chest (Andy); now they're only ever open from the back.
 * <p>
 * Each Valor's wings are one of the pack's dragons' ({@link ValorWings#DESIGNS}, {@code tools/gen_valor_wing_designs.py}):
 * the ender dragon's shape (Xenoform Wyrm's), Notus and Boreas's fire dragon wings (nyxaris's "Fire Dragon", CC BY 4.0),
 * Zephyrus's short ragged wings, or Eurus and Aeolus's wyvern wings (eanncrod's "Kintsugi", CC BY 4.0), all laid out the same
 * way (a wing along x with its membrane trailing back, and a tip) so the same poses move them, recoloured into the look.
 */
public final class ValorWingsLook {
	/** The wings' least show as the pool empties (alpha share). */
	static final float FADE_FLOOR = 0.25F;
	/** In flight: the beat's speed and depth; folded at speed, the yaw they sweep back to and their lift; how fast is fast (blocks a tick). */
	static final float FLAP_RATE = 0.55F, FLAP_DEPTH = 0.95F, FOLDED_YAW = 1.35F, FOLDED_LIFT = 0.15F, FAST_FROM = 0.3F, FAST_FULL = 0.6F;
	/** How far the tips bend behind the beat. */
	static final float TIP_FLEX = 0.6F;
	/** Unfurling: how much of the way a tick (opening, winding back onto the arms); a sleeve's growth over the arm, bare or armoured. */
	static final float UNFURL_RATE = 0.3F, WIND_RATE = 0.1F, SLEEVE_BARE = 0.6F, SLEEVE_ARMOURED = 1.3F;
	/** Unfurling: the wings come out swept back along the back at this yaw, folded this tight, and spread as they open. */
	static final float BUD_YAW = 1.45F, BUD_FOLD = 1.6F;
	/** Each design's size (its wings are drawn from its dragon's at this much). */
	static final float[] SCALE = {1.0F, 0.36F, 0.6F, 0.115F};
	/** A flap's speed and depth. */
	static final float COUNTER_RATE = 1.3F, COUNTER_DEPTH = 1.1F;
	/** Where each wing roots on the back (model pixels from the neck); the bone's and the tip's length. */
	static final float ROOT_X = 2.0F, ROOT_Y = 2.0F, ROOT_Z = 2.5F;
	static final int BONE = 13, TIP = 12;

	public static final RenderStateDataKey<Wings> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:valor_wings");

	/**
	 * Wings out: the look, flying, a flap just now, how full the pool is, King's coating and its soul colour, how fast, and
	 * how far unfurled (0 wound round the arms, 1 open on the back).
	 */
	public record Wings(int design, int look, boolean flying, boolean flap, float fade, boolean kings, int soul, float speed, float unfurl) {
	}

	/** Each one's unfurling, and when it was last seen (its age in ticks), by entity: eased frame to frame. */
	private static final Map<LivingEntity, float[]> UNFURL = new WeakHashMap<>();
	private static final Identifier[] SLEEVES = new Identifier[Valor.Look.values().length];
	private static final Identifier SLEEVE_VEINS = JaymosKingdoms.id("textures/entity/valor/wings_sleeve_veins.png");

	private static final Identifier[][] TEXTURES = new Identifier[ValorWings.DESIGNS.length][Valor.Look.values().length];
	private static final Identifier[] VEINS = new Identifier[ValorWings.DESIGNS.length];

	private ValorWingsLook() {
	}

	/** wings_<look> for the wyrm's (the first), wings_<design>_<look> for the rest. */
	static Identifier texture(int design, int look) {
		int i = Mth.clamp(look, 0, Valor.Look.values().length - 1);
		if (TEXTURES[design][i] == null) {
			TEXTURES[design][i] = JaymosKingdoms.id("textures/entity/valor/wings_" + prefix(design) + Valor.Look.values()[i].key() + ".png");
		}
		return TEXTURES[design][i];
	}

	static Identifier veins(int design) {
		if (VEINS[design] == null) {
			VEINS[design] = JaymosKingdoms.id("textures/entity/valor/wings_" + prefix(design) + "veins.png");
		}
		return VEINS[design];
	}

	static Identifier sleeve(int look) {
		int i = Mth.clamp(look, 0, Valor.Look.values().length - 1);
		if (SLEEVES[i] == null) {
			SLEEVES[i] = JaymosKingdoms.id("textures/entity/valor/wings_sleeve_" + Valor.Look.values()[i].key() + ".png");
		}
		return SLEEVES[i];
	}

	private static String prefix(int design) {
		return design == 0 ? "" : ValorWings.DESIGNS[design] + "_";
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> || renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(layer(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> layer(RenderLayerParent<?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: the wings, while they're out. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		Valor.Data data = ValorWings.out(entity) && !entity.isInvisible() ? Valor.of(entity) : null;
		if (data == null) {
			UNFURL.remove(entity);
			state.setData(KEY, null);
			return;
		}
		int bits = Valor.state(entity);
		boolean flying = (bits & Valor.FLYING) != 0, flap = (bits & Valor.FLAP) != 0;
		state.setData(KEY, new Wings(data.wingDesign(), data.look(), flying, flap, Valor.poolShare(entity), (bits & Valor.KINGS) != 0,
			data.soulColor(), (float) entity.getDeltaMovement().horizontalDistance(), unfurl(entity, state.ageInTicks, flying || flap)));
	}

	/** Eases {@code entity}'s wings toward open (in use) or wound round the arms: quick to open, slower to wind back. */
	private static float unfurl(LivingEntity entity, float age, boolean open) {
		float[] u = UNFURL.get(entity);
		if (u == null) {
			// just out: taking off they open from the arms; coated, they're wound round them already
			u = new float[] {0.0F, age};
			UNFURL.put(entity, u);
		}
		float dt = Mth.clamp(age - u[1], 0.0F, 5.0F);
		u[1] = age;
		u[0] = open ? Math.min(1.0F, u[0] + UNFURL_RATE * dt) : Math.max(0.0F, u[0] - WIND_RATE * dt);
		return u[0];
	}

	/** On every humanoid (players, MCA's villagers, our own folk): the wings over the body. */
	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		/** By design, made when first drawn. */
		private final WingsModel[] models = new WingsModel[ValorWings.DESIGNS.length];
		/** The sleeves, [armoured][slim]. */
		private final SleevesModel[][] sleeves = new SleevesModel[2][2];

		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Wings wings = state.getData(KEY);
			if (wings == null || state.isInvisible) {
				return;
			}
			int design = Mth.clamp(wings.design(), 0, this.models.length - 1);
			if (this.models[design] == null) {
				this.models[design] = new WingsModel(this::getParentModel, design);
			}
			float fade = FADE_FLOOR + (1.0F - FADE_FLOOR) * Mth.clamp(wings.fade(), 0.0F, 1.0F);
			float unfurl = Mth.clamp(wings.unfurl(), 0.0F, 1.0F);
			int glow = wings.kings() ? ValorLook.glow(wings.soul(), state.ageInTicks) : 0;
			// wound round the arms, fading as they draw up off them
			if (unfurl < 1.0F) {
				boolean armoured = !state.chestEquipment.isEmpty();
				boolean slim = state instanceof AvatarRenderState avatar && avatar.skin.model() == PlayerModelType.SLIM;
				int a = armoured ? 1 : 0, s = slim ? 1 : 0;
				if (this.sleeves[a][s] == null) {
					this.sleeves[a][s] = new SleevesModel(this::getParentModel, armoured, slim);
				}
				SleevesModel sleeves = this.sleeves[a][s];
				float show = fade * (1.0F - smooth(unfurl));
				collector.order(1).submitModel(sleeves, state, poseStack, RenderTypes.entityTranslucent(sleeve(wings.look())), lightCoords,
					OverlayTexture.NO_OVERLAY, ARGB.white(show), null, state.outlineColor);
				if (wings.kings()) {
					collector.order(2).submitModel(sleeves, state, poseStack, RenderTypes.eyes(SLEEVE_VEINS), lightCoords, OverlayTexture.NO_OVERLAY,
						ARGB.scaleRGB(glow, show), null, state.outlineColor);
				}
			}
			// open on the back
			if (unfurl > 0.0F) {
				if (this.models[design] == null) {
					this.models[design] = new WingsModel(this::getParentModel, design);
				}
				WingsModel model = this.models[design];
				float show = fade * Math.min(1.0F, unfurl * 2.0F);
				collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityTranslucent(texture(design, wings.look())), lightCoords,
					OverlayTexture.NO_OVERLAY, ARGB.white(show), null, state.outlineColor);
				if (wings.kings()) {
					collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(veins(design)), lightCoords, OverlayTexture.NO_OVERLAY,
						ARGB.scaleRGB(glow, show), null, state.outlineColor);
				}
			}
		}
	}

	/**
	 * (0.7.480) The wings at rest: wound round each arm like a coating, over the Valor's own coat, riding the arm through
	 * every pose. Unfurling, they draw up off the arm from the hand to the shoulder.
	 */
	static final class SleevesModel extends WornModel {
		SleevesModel(Supplier<HumanoidModel<?>> wearer, boolean armoured, boolean slim) {
			super(root(armoured, slim), wearer);
		}

		private static ModelPart root(boolean armoured, boolean slim) {
			CubeDeformation grow = new CubeDeformation(armoured ? SLEEVE_ARMOURED : SLEEVE_BARE);
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
			int w = slim ? 3 : 4, u = slim ? 32 : 0;
			root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(u, 0).addBox(-3.0F, -2.0F, -2.0F, w, 12, 4, grow),
				PartPose.offset(-5.0F, slim ? 2.5F : 2.0F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(u, 0).mirror().addBox(-1.0F, -2.0F, -2.0F, w, 12, 4, grow),
				PartPose.offset(5.0F, slim ? 2.5F : 2.0F, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
			return LayerDefinition.create(mesh, 64, 32).bakeRoot();
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			super.setupAnim(state);
			Wings wings = state.getData(KEY);
			// drawn up toward the shoulder as they unfurl
			float left = 1.0F - 0.85F * smooth(wings == null ? 0.0F : Mth.clamp(wings.unfurl(), 0.0F, 1.0F));
			this.rightArm.yScale *= left;
			this.leftArm.yScale *= left;
		}
	}

	/** The wings: a humanoid with nothing on it but them, so they ride the wearer's body through every pose. */
	static final class WingsModel extends WornModel {
		private final int design;
		private final ModelPart rightWing, leftWing, rightTip, leftTip;

		WingsModel(Supplier<HumanoidModel<?>> wearer, int design) {
			super(root(design), wearer);
			this.design = design;
			this.rightWing = this.body.getChild("right_wing");
			this.leftWing = this.body.getChild("left_wing");
			this.rightTip = this.rightWing.getChild("right_wing_tip");
			this.leftTip = this.leftWing.getChild("left_wing_tip");
		}

		/** A humanoid with nothing on it but the wings: the wyrm's and Zephyrus's built here, Notus's and Eurus's baked. */
		private static ModelPart root(int design) {
			if (design == 1 || design == 3) {
				BbModel bb = BbModel.load(JaymosKingdoms.MOD_ID, "valor_wings_" + ValorWings.DESIGNS[design]);
				ModelPart right = bb.part("right_wing"), left = bb.part("left_wing");
				PartPose r = PartPose.offset(-ROOT_X, ROOT_Y, ROOT_Z).withScale(SCALE[design]), l = PartPose.offset(ROOT_X, ROOT_Y, ROOT_Z).withScale(SCALE[design]);
				right.setInitialPose(r);
				right.loadPose(r);
				left.setInitialPose(l);
				left.loadPose(l);
				Map<String, ModelPart> wings = new HashMap<>();
				wings.put("right_wing", right);
				wings.put("left_wing", left);
				Map<String, ModelPart> parts = new HashMap<>();
				parts.put("head", new ModelPart(List.of(), new HashMap<>(Map.of("hat", empty()))));
				parts.put("body", new ModelPart(List.of(), wings));
				for (String limb : new String[] {"right_arm", "left_arm", "right_leg", "left_leg"}) {
					parts.put(limb, empty());
				}
				return new ModelPart(List.of(), parts);
			}
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
			if (design == 2) {
				zephyrus(body);
				return LayerDefinition.create(mesh, 256, 256).bakeRoot();
			}
			wyrm(body);
			return LayerDefinition.create(mesh, 64, 64).bakeRoot();
		}

		private static ModelPart empty() {
			return new ModelPart(List.of(), new HashMap<>());
		}

		/** Zephyrus's wings (Zephyrus's own wing boxes, zephyrus.geo.json, on its sheet), small. */
		private static void zephyrus(PartDefinition body) {
			float s = SCALE[2];
			PartDefinition left = body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(94, 95).addBox(0.0F, -1.5F, -1.5F, 18, 3, 3)
				.texOffs(112, 0).addBox(0.0F, 0.0F, 1.5F, 18, 0, 26), PartPose.offset(ROOT_X, ROOT_Y, ROOT_Z).withScale(s));
			left.addOrReplaceChild("left_wing_tip", CubeListBuilder.create().texOffs(0, 103).addBox(0.0F, -1.0F, -1.0F, 20, 2, 2)
				.texOffs(0, 44).addBox(0.0F, 0.0F, 1.0F, 20, 0, 22), PartPose.offset(17.0F, 0.0F, 0.0F));
			PartDefinition right = body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(94, 95).mirror().addBox(-18.0F, -1.5F, -1.5F, 18, 3, 3)
				.texOffs(112, 0).addBox(-18.0F, 0.0F, 1.5F, 18, 0, 26), PartPose.offset(-ROOT_X, ROOT_Y, ROOT_Z).withScale(s));
			right.addOrReplaceChild("right_wing_tip", CubeListBuilder.create().texOffs(0, 103).mirror().addBox(-20.0F, -1.0F, -1.0F, 20, 2, 2)
				.texOffs(0, 44).addBox(-20.0F, 0.0F, 1.0F, 20, 0, 22), PartPose.offset(-17.0F, 0.0F, 0.0F));
		}

		/**
		 * The wyrm's: each wing a bone with its membrane trailing behind, and a tip with its own (texture: membrane 0,0; the
		 * tip's 0,14; the bone 0,26; the tip's bone 0,32).
		 */
		private static void wyrm(PartDefinition body) {
			PartDefinition left = body.addOrReplaceChild("left_wing", CubeListBuilder.create().mirror()
				.addBox("bone", 0.0F, -1.0F, -1.0F, BONE, 2, 2, 0, 26).addBox("skin", 0.0F, 0.0F, 0.0F, BONE, 0, 12, 0, 0),
				PartPose.offset(ROOT_X, ROOT_Y, ROOT_Z));
			left.addOrReplaceChild("left_wing_tip", CubeListBuilder.create().mirror()
				.addBox("bone", 0.0F, -0.5F, -0.5F, TIP, 1, 1, 0, 32).addBox("skin", 0.0F, 0.0F, 0.0F, TIP, 0, 10, 0, 14),
				PartPose.offset(BONE, 0.0F, 0.0F));
			PartDefinition right = body.addOrReplaceChild("right_wing", CubeListBuilder.create()
				.addBox("bone", -BONE, -1.0F, -1.0F, BONE, 2, 2, 0, 26).addBox("skin", -BONE, 0.0F, 0.0F, BONE, 0, 12, 0, 0),
				PartPose.offset(-ROOT_X, ROOT_Y, ROOT_Z));
			right.addOrReplaceChild("right_wing_tip", CubeListBuilder.create()
				.addBox("bone", -TIP, -0.5F, -0.5F, TIP, 1, 1, 0, 32).addBox("skin", -TIP, 0.0F, 0.0F, TIP, 0, 10, 0, 14),
				PartPose.offset(-BONE, 0.0F, 0.0F));
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			super.setupAnim(state);
			Wings wings = state.getData(KEY);
			if (wings == null) {
				return;
			}
			float t = state.ageInTicks;
			float fly = wings.flying() ? 1.0F : 0.0F;
			float fold = fly * smooth(Mth.clamp((wings.speed() - FAST_FROM) / (FAST_FULL - FAST_FROM), 0.0F, 1.0F));
			float flap = wings.flap() ? 1.0F : 0.0F;
			// unfurling: they come out from the back folded tight and swept back along it, growing, then spread
			float unfurl = smooth(Mth.clamp(wings.unfurl(), 0.0F, 1.0F));
			float bud = 1.0F - unfurl;

			// open: a slow stir, the deep strokes of flight dying away as they fold at speed, or a flap's sharp beats
			float idle = Mth.sin(t * 0.25F) * 0.05F;
			float beat = Mth.lerp(fly, idle, Mth.sin(t * FLAP_RATE) * FLAP_DEPTH * (1.0F - fold));
			beat += flap * Mth.sin(t * COUNTER_RATE) * COUNTER_DEPTH;
			float openZ = (-0.3F + beat + fold * FOLDED_LIFT) * unfurl;
			float openY = Mth.lerp(bud, Mth.lerp(fold, 0.6F + Mth.cos(t * 0.25F) * 0.1F, FOLDED_YAW), BUD_YAW);
			float openTip = -(Mth.sin(t * FLAP_RATE - 2.0F) + 0.5F) * TIP_FLEX * fly * (1.0F - fold) * unfurl;

			this.rightWing.yRot = openY;
			this.leftWing.yRot = -openY;
			this.rightWing.zRot = openZ;
			this.leftWing.zRot = -openZ;
			this.rightTip.zRot = openTip;
			this.leftTip.zRot = -openTip;
			// budding, the tips fold back along the bones (toward +z, behind)
			this.rightTip.yRot = BUD_FOLD * bud;
			this.leftTip.yRot = -BUD_FOLD * bud;
			float grow = 0.2F + 0.8F * unfurl;
			for (ModelPart wing : new ModelPart[] {this.rightWing, this.leftWing}) {
				wing.xScale *= grow;
				wing.yScale *= grow;
				wing.zScale *= grow;
			}
		}
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}
}
