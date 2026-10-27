package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ApeGrab;
import com.jaymospowers.power.zfighter.ZFighterGoldenApe;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Great Ape's body, drawn in the player's place on everyone's screen (as Xenoform draws its aliens): the player is
 * hidden and a shaggy ape in his pose stands there instead, at his (giant) size. A big furred head with a jutting
 * muzzle, a heavy brow and red eyes; a barrel chest with a shoulder hump; long arms hanging to the knees; short thick
 * legs; a tail. Its skin is {@code textures/entity/zfighter_great_ape.png} ({@code tools/gen_great_ape.py}). (0.16.36)
 * Every character's ape is its own ({@link WarriorLooks#ape}): head, arms, bulk, tail and the fur's shade.
 */
public final class ZFighterApe {
	/** Whether this player is the Great Ape, on his render state. */
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_great_ape");
	/** (0.12.3) Whether the Great Ape lies crippled (flat on his back), on his render state. */
	public static final RenderStateDataKey<Boolean> CRIPPLED = RenderStateDataKey.create(() -> "jaymospowers:zfighter_ape_crippled");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_great_ape.png");
	/** (0.17.50) The golden Great Ape's skin ({@code tools/gen_golden_ape.py}), worn by a Z Fighter who has gone Super. */
	static final Identifier GOLDEN_TEXTURE = JaymosPowers.id("textures/entity/zfighter_great_ape_golden.png");
	public static final RenderStateDataKey<Boolean> GOLDEN = RenderStateDataKey.create(() -> "jaymospowers:zfighter_golden_ape");
	/** (0.16.36) Whose ape it is: its build and fur. */
	public static final RenderStateDataKey<WarriorLooks.Ape> STYLE = RenderStateDataKey.create(() -> "jaymospowers:zfighter_ape_style");
	/** (0.17.54) How far through its slow stride the ape is, in steps ({@link ZFighterApeSteps#stride}). */
	public static final RenderStateDataKey<Float> STRIDE = RenderStateDataKey.create(() -> "jaymospowers:zfighter_ape_stride");
	/** (0.17.63) How far into his grab he is (ticks, {@link ApeGrab#TOTAL} long), or null when he isn't grabbing. */
	public static final RenderStateDataKey<Float> GRAB = RenderStateDataKey.create(() -> "jaymospowers:zfighter_ape_grab");
	/** Vanilla's walk cycle: its legs swing with {@code cos(walkAnimationPos * this)}. */
	private static final float VANILLA_WALK = 0.6662F;

	private static Model<AvatarRenderState> model;

	private ZFighterApe() {
	}

	/** From the avatar renderer's extractRenderState: the Great Ape hides the player's own body, armour and held items. */
	static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		boolean ape = entity instanceof Player player && ZFighterLineage.ape(player) && !player.isInvisible();
		state.setData(KEY, ape);
		if (!ape) {
			return;
		}
		state.setData(CRIPPLED, ZFighterLineage.crippled((Player)entity));
		state.setData(STYLE, WarriorLooks.ape(entity));
		state.setData(STRIDE, ZFighterApeSteps.stride(entity, partialTicks));
		float grab = ApeGrab.phase(entity, partialTicks);
		state.setData(GRAB, grab >= 0.0F ? grab : null);
		state.setData(GOLDEN, ZFighterGoldenApe.golden((Player)entity) ? Boolean.TRUE : null);
		state.setData(ApeSmashClient.POSE, ApeSmashClient.pose((Player)entity));
		state.isInvisible = true;
		state.isInvisibleToPlayer = true;
		state.headEquipment = ItemStack.EMPTY;
		state.chestEquipment = ItemStack.EMPTY;
		state.legsEquipment = ItemStack.EMPTY;
		state.feetEquipment = ItemStack.EMPTY;
		state.headItem.clear();
		state.wornHeadType = null;
		state.wornHeadProfile = null;
		state.heldOnHead.clear();
		state.rightHandItemState.clear();
		state.leftHandItemState.clear();
		state.leftHandItemStack = ItemStack.EMPTY;
		state.showCape = false;
		state.showExtraEars = false;
		state.parrotOnLeftShoulder = null;
		state.parrotOnRightShoulder = null;
		state.arrowCount = 0;
		state.stingerCount = 0;
	}

	static Model<AvatarRenderState> model() {
		if (model == null) {
			model = new Model<>();
		}
		return model;
	}

	/**
	 * A villager raging as the wild Great Ape (Jaymo's Kingdoms' Warrior-born villagers, {@code VillagerApe}): the same
	 * ape in its place, its own body, clothes and gear hidden.
	 */
	public static void extractMob(LivingEntity entity, HumanoidRenderState state, float partialTicks) {
		boolean ape = !(entity instanceof Player) && Boolean.TRUE.equals(entity.getAttached(ZFighterLineage.GREAT_APE)) && !entity.isInvisible();
		state.setData(KEY, ape ? Boolean.TRUE : null);
		if (!ape) {
			return;
		}
		state.setData(CRIPPLED, ZFighterLineage.crippled(entity));
		state.setData(STYLE, WarriorLooks.ape(entity));
		state.setData(STRIDE, ZFighterApeSteps.stride(entity, partialTicks));
		float grab = ApeGrab.phase(entity, partialTicks);
		state.setData(GRAB, grab >= 0.0F ? grab : null);
		state.isInvisible = true;
		state.isInvisibleToPlayer = true;
		state.headEquipment = ItemStack.EMPTY;
		state.chestEquipment = ItemStack.EMPTY;
		state.legsEquipment = ItemStack.EMPTY;
		state.feetEquipment = ItemStack.EMPTY;
		state.headItem.clear();
		state.wornHeadType = null;
		state.wornHeadProfile = null;
		state.rightHandItemState.clear();
		state.leftHandItemState.clear();
		state.rightHandItemStack = ItemStack.EMPTY;
		state.leftHandItemStack = ItemStack.EMPTY;
	}

	private static Model<HumanoidRenderState> mobModel;

	static Model<HumanoidRenderState> mobModel() {
		if (mobModel == null) {
			mobModel = new Model<>();
		}
		return mobModel;
	}

	/** (0.16.36) The fur's tint for the ape being drawn (-1: as painted). */
	static int fur(HumanoidRenderState state) {
		WarriorLooks.Ape style = state.getData(STYLE);
		return style == null ? -1 : style.fur();
	}

	/** Draws the ape over a hidden villager (any humanoid mob's renderer). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static MobLayer<?> mobLayer(RenderLayerParent<?, ?> renderer) {
		return new MobLayer((RenderLayerParent) renderer);
	}

	static final class MobLayer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		MobLayer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			if (!Boolean.TRUE.equals(state.getData(KEY))) {
				return;
			}
			boolean down = Boolean.TRUE.equals(state.getData(CRIPPLED));
			if (down) {
				poseStack.pushPose();
				poseStack.translate(0.0F, 1.5F, 0.0F);
				poseStack.rotateDegrees(com.mojang.math.Axis.XP, -90.0F);
				poseStack.translate(0.0F, -1.5F, -0.25F);
			}
			renderColoredCutoutModel(mobModel(), TEXTURE, poseStack, collector, lightCoords, state, fur(state), 1);
			if (down) {
				poseStack.popPose();
			}
		}
	}

	/** The ape: a humanoid model, so it walks, swings and looks round as he does (or a raging villager does). */
	static final class Model<S extends HumanoidRenderState> extends HumanoidModel<S> {
		private final ModelPart tail;

		Model() {
			super(LayerDefinition.create(mesh(), 128, 128).bakeRoot());
			this.tail = this.body.getChild("tail");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-5.0F, -9.0F, -5.0F, 10.0F, 9.0F, 9.0F)
				.texOffs(40, 0).addBox(-3.5F, -4.5F, -8.0F, 7.0F, 5.0F, 3.0F)
				.texOffs(64, 0).addBox(-5.0F, -9.5F, -6.0F, 10.0F, 2.0F, 2.0F)
				.texOffs(90, 0).addBox(-7.0F, -7.0F, -1.0F, 2.0F, 3.0F, 1.0F)
				.texOffs(90, 0).mirror().addBox(5.0F, -7.0F, -1.0F, 2.0F, 3.0F, 1.0F), PartPose.ZERO);
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 20).addBox(-6.0F, -1.0F, -3.5F, 12.0F, 13.0F, 7.0F)
				.texOffs(40, 20).addBox(-5.0F, -2.0F, 0.0F, 10.0F, 4.0F, 4.0F), PartPose.ZERO);
			body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 70).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 12.0F),
				PartPose.offsetAndRotation(0.0F, 10.5F, 3.0F, -0.6F, 0.0F, 0.0F));
			root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 44).addBox(-5.0F, -2.0F, -2.5F, 5.0F, 17.0F, 5.0F),
				PartPose.offset(-6.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(24, 44).addBox(0.0F, -2.0F, -2.5F, 5.0F, 17.0F, 5.0F),
				PartPose.offset(6.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(48, 44).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 12.0F, 6.0F),
				PartPose.offset(-3.0F, 12.0F, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(76, 44).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 12.0F, 6.0F),
				PartPose.offset(3.0F, 12.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(S state) {
			// (0.17.54) vanilla's own walk, played on its slow stride: one leg comes forward each step (ZFighterApeSteps)
			Float stride = state.getData(STRIDE);
			float walked = state.walkAnimationPos;
			if (stride != null) {
				state.walkAnimationPos = stride * Mth.PI / VANILLA_WALK;
			}
			super.setupAnim(state);
			state.walkAnimationPos = walked;
			float time = state.ageInTicks;
			float walk = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			// the arms hang a little out from the bulk of him and sway heavily
			this.rightArm.zRot += 0.12F + 0.03F * Mth.sin(time * 0.07F);
			this.leftArm.zRot -= 0.12F + 0.03F * Mth.sin(time * 0.07F);
			// the tail swings, more as he lumbers along
			this.tail.yRot = (0.25F + 0.35F * walk) * Mth.sin(time * (0.08F + 0.12F * walk));
			this.tail.xRot = -0.6F + 0.1F * Mth.sin(time * 0.05F);
			// (0.16.36) this character's own build: head, arm length, bulk, tail
			WarriorLooks.Ape build = state.getData(STYLE);
			float head = build == null ? 1.0F : build.head(), arms = build == null ? 1.0F : build.arms();
			float bulk = build == null ? 1.0F : build.bulk(), tail = build == null ? 1.0F : build.tail();
			this.head.xScale = this.head.yScale = this.head.zScale = head;
			this.rightArm.xScale = this.leftArm.xScale = this.rightArm.zScale = this.leftArm.zScale = bulk;
			this.rightArm.yScale = this.leftArm.yScale = arms;
			this.body.xScale = this.body.zScale = bulk;
			this.tail.zScale = tail;
			// (0.17.63) his grab: the hand reared up and back, brought down, held trembling at his chest, hurled into the ground
			Float grab = state.getData(GRAB);
			if (grab != null) {
				this.grab(grab, time);
				return;
			}
			// (0.16.6) his punch: the right fist drawn back as it charges, then thrown out and down
			float[] smash = state.getData(ApeSmashClient.POSE);
			if (smash != null) {
				float drawn = smash[0];
				if (smash[1] >= 0.0F) {
					// the blow: out fast, then easing back
					float t = smash[1];
					float out = t < 0.4F ? t / 0.4F : 1.0F - (t - 0.4F) / 0.6F;
					this.rightArm.xRot = Mth.lerp(out, this.rightArm.xRot, ApeSmashClient.THROW_X);
					this.rightArm.zRot = Mth.lerp(out, this.rightArm.zRot, 0.0F);
					this.body.yRot = -ApeSmashClient.TWIST * out;
				} else {
					float shake = drawn >= 1.0F ? 0.04F * Mth.sin(time * 2.5F) : 0.0F;
					this.rightArm.xRot = Mth.lerp(drawn, this.rightArm.xRot, ApeSmashClient.DRAW_X) + shake;
					this.rightArm.zRot += ApeSmashClient.DRAW_Z * drawn;
					this.body.yRot = ApeSmashClient.TWIST * drawn;
				}
			}
		}

		/** (0.17.63) His grab, {@code t} ticks in (the twin of the Aether Giant's, {@code kingdoms/client/remnants/GiantModel}). */
		private void grab(float t, float age) {
			float windup = ApeGrab.WINDUP, snatch = ApeGrab.SNATCH, hold = ApeGrab.HOLD, slam = ApeGrab.SLAM, recover = ApeGrab.RECOVER;
			float rest = this.rightArm.zRot;
			float x, z, lean, head;
			if (t < windup) {
				float u = ease(t / windup);
				x = Mth.lerp(u, this.rightArm.xRot, GRAB_RAISED);
				z = rest + u * GRAB_FLARE;
				lean = u * GRAB_LEAN_BACK;
				head = -u * 0.4F;
			} else if (t < windup + snatch) {
				float u = ease((t - windup) / snatch);
				x = Mth.lerp(u, GRAB_RAISED, GRAB_DOWN);
				z = rest + (1.0F - u) * GRAB_FLARE;
				lean = Mth.lerp(u, GRAB_LEAN_BACK, GRAB_LEAN_FORWARD);
				head = Mth.lerp(u, -0.4F, 0.5F);
			} else if (t < windup + snatch + hold) {
				float u = ease(Math.min(1.0F, (t - windup - snatch) / ApeGrab.LIFT_TICKS));
				x = Mth.lerp(u, GRAB_DOWN, GRAB_HELD) + u * GRAB_TREMBLE * Mth.sin(age * 2.1F);
				z = rest + u * GRAB_TREMBLE * Mth.sin(age * 1.7F);
				lean = Mth.lerp(u, GRAB_LEAN_FORWARD, 0.0F);
				head = Mth.lerp(u, 0.5F, 0.15F);
			} else if (t < windup + snatch + hold + slam) {
				float u = (t - windup - snatch - hold) / slam;
				u *= u;
				x = Mth.lerp(u, GRAB_HELD, GRAB_HURLED);
				z = rest;
				lean = u * GRAB_LEAN_FORWARD;
				head = Mth.lerp(u, 0.15F, 0.6F);
			} else {
				float u = ease(Math.min(1.0F, (t - windup - snatch - hold - slam) / recover));
				x = Mth.lerp(u, GRAB_HURLED, this.rightArm.xRot);
				z = rest;
				lean = (1.0F - u) * GRAB_LEAN_FORWARD;
				head = (1.0F - u) * 0.6F;
			}
			this.rightArm.xRot = x;
			this.rightArm.yRot = 0.0F;
			this.rightArm.zRot = z;
			this.body.xRot += lean;
			this.head.xRot += head;
			// the other arm braces out a little
			this.leftArm.zRot -= Math.abs(lean) * 0.5F;
		}

		private static float ease(float t) {
			t = Mth.clamp(t, 0.0F, 1.0F);
			return t * t * (3.0F - 2.0F * t);
		}
	}

	/** The grab's poses (radians on the right arm): reared up behind him, down on his catch, held at his chest, hurled, and the trembling. */
	private static final float GRAB_RAISED = -2.8F, GRAB_FLARE = 0.5F, GRAB_DOWN = -0.35F, GRAB_HELD = -1.35F, GRAB_HURLED = 0.3F,
		GRAB_LEAN_BACK = -0.3F, GRAB_LEAN_FORWARD = 0.45F, GRAB_TREMBLE = 0.04F;

	/** Draws the ape over the hidden player. Registered after the tail and hair layers, which the hidden body skips. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			if (!Boolean.TRUE.equals(state.getData(KEY))) {
				return;
			}
			boolean down = Boolean.TRUE.equals(state.getData(CRIPPLED));
			if (down) {
				// crippled: laid flat on his back, about his feet (model space: y down, the feet 1.5 below the origin)
				poseStack.pushPose();
				poseStack.translate(0.0F, 1.5F, 0.0F);
				poseStack.rotateDegrees(com.mojang.math.Axis.XP, -90.0F);
				poseStack.translate(0.0F, -1.5F, -0.25F);
			}
			// not the "copy layer" call: that skips invisible states, and the body was made invisible
			// (0.17.50) the golden ape's fur is painted gold already: no tint on top
			boolean golden = Boolean.TRUE.equals(state.getData(GOLDEN));
			renderColoredCutoutModel(model(), golden ? GOLDEN_TEXTURE : TEXTURE, poseStack, collector, lightCoords, state, golden ? -1 : fur(state), 1);
			if (down) {
				poseStack.popPose();
			}
			// let his shadow fall (the renderer skips it for invisible states, after the layers)
			state.isInvisible = false;
		}
	}
}
