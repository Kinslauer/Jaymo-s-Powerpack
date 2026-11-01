package com.jaymospowers.client.baubles;

import com.jaymospowers.baubles.Baubles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Motion patch 4) Worn lanterns (Andy: "Lanterns should be placeable in the baubles slot and hang from your hip, or a
 * lantern on a stick you can wear on back"). Drawn on every player from what the server syncs ({@link Baubles#SHOWN}):
 * <ul>
 * <li>A lantern in the belt slot hangs at the hip on the off-hand side, a little in front of the thigh.</li>
 * <li>The lantern pole in the body slot rises from the small of the back, leaning back, with its lantern hung out over
 * the head.</li>
 * </ul>
 * Both hang straight down whatever the body does (crouching included) and swing on a spring as you start, stop, turn,
 * jump and fall, rocking with each step; the hip lantern is nudged forward out of the way of the leg. With
 * LambDynamicLights installed they light the way around you too ({@code LdlWornLanternMixin}).
 */
public final class WornLanterns {
	// ===== the knobs ================================================================================================
	/** The hip lantern's hook, in the body's pixels (x out to the side, y down from the neck, z back), and its size (blocks). */
	static final float HIP_X = 3.2F, HIP_Y = 10.5F, HIP_Z = -2.8F, HIP_SIZE = 0.6F;
	/** How much of the leg's forward swing pushes the hip lantern forward out of its way. */
	static final float LEG_NUDGE = 0.9F;
	/** The pole's foot on the back (pixels), its length and lean back (radians), and the hook reaching forward off its top. */
	static final float POLE_X = -1.5F, POLE_Y = 11.0F, POLE_Z = 2.8F, POLE_LENGTH = 30.0F, POLE_LEAN = -0.15F, POLE_HOOK = 1.5F;
	/** The pole lantern's size (blocks); the pole sits this much further out over a chestplate, and over a backpack. */
	static final float POLE_SIZE = 0.55F, OVER_ARMOUR = 1.0F, OVER_BACKPACK = 6.0F;
	/** The pole's wood. */
	static final Identifier POLE_TEXTURE = Identifier.withDefaultNamespace("textures/block/stripped_spruce_log.png");
	// ================================================================================================================

	/** What a player wears hung on them, as drawn this frame. */
	record Worn(@Nullable BlockState hip, @Nullable BlockState pole, float hipSwing, float hipRoll, float poleSwing, float poleRoll,
		boolean backpack) {
	}

	static final RenderStateDataKey<Worn> KEY = RenderStateDataKey.create(() -> "jaymospowers:worn_lanterns");
	private static final Map<Avatar, Swing[]> SWINGS = new WeakHashMap<>();
	private static @Nullable ModelPart pole;

	private WornLanterns() {
	}

	public static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (stack.is(Baubles.LANTERN_POLE)) {
				lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymospowers.lantern_pole").withStyle(ChatFormatting.GRAY));
			} else if (Baubles.hipLantern(stack)) {
				lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymospowers.hip_lantern").withStyle(ChatFormatting.GRAY));
			}
		});
	}

	/** A lantern item's block, hanging; null if it isn't one. */
	static @Nullable BlockState hanging(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) {
			return null;
		}
		BlockState state = item.getBlock().defaultBlockState();
		return state.hasProperty(LanternBlock.HANGING) ? state.setValue(LanternBlock.HANGING, true) : state;
	}

	/** From the avatar renderer's extractRenderState (with the backpack's): the worn lanterns and how they swing. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		if (!(entity instanceof Player player)) {
			state.setData(KEY, null);
			return;
		}
		BlockState hip = hanging(Baubles.shown(player, 0));
		BlockState onPole = Baubles.shown(player, 1).isEmpty() ? null : Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
		if (hip == null && onPole == null) {
			state.setData(KEY, null);
			synchronized (SWINGS) {
				SWINGS.remove(entity);
			}
			return;
		}
		Swing[] swings;
		synchronized (SWINGS) {
			swings = SWINGS.computeIfAbsent(entity, e -> new Swing[] {new Swing(1.0F), new Swing(0.7F)});
		}
		Vec3 now = entity.getPosition(partialTicks);
		float yaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
		float step = state.walkAnimationPos * 0.6662F, stride = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
		for (Swing swing : swings) {
			swing.update(now, yaw, state.ageInTicks);
		}
		state.setData(KEY, new Worn(hip, onPole,
			swings[0].swing, swings[0].roll + Swing.STEP_ROLL * Mth.sin(step) * stride,
			swings[1].swing, swings[1].roll + Swing.STEP_ROLL * 0.6F * Mth.sin(step) * stride,
			com.jaymospowers.client.backpack.BackpackClient.worn(state)));
		if (onPole != null) {
			state.showCape = false;
		}
	}

	/**
	 * A hung lantern on a damped spring each way: its bottom trails back as you speed up or fall, swings forward as you
	 * stop or land, and out to the side as you turn or sidestep.
	 */
	static final class Swing {
		static final float STIFFNESS = 0.2F, DAMPING = 0.35F;
		static final float PUSH_FORWARD = 3.0F, PUSH_FALL = 1.2F, PUSH_SIDE = 2.6F, DRAG = 0.9F;
		/** Its limits (radians), and each step's rock. */
		static final float SWING_MAX = 0.9F, ROLL_MAX = 0.6F, STEP_ROLL = 0.12F;

		final float weight;
		float swing, swingSpeed, roll, rollSpeed;
		private float age = Float.NaN;
		private Vec3 pos = Vec3.ZERO;
		private double forward, side, up;

		/** {@code weight}: how hard the pushes move this one (a pole's lantern, up high, swings less). */
		Swing(float weight) {
			this.weight = weight;
		}

		void update(Vec3 now, float bodyYaw, float ageInTicks) {
			if (Float.isNaN(this.age) || ageInTicks < this.age || ageInTicks - this.age > 10.0F) {
				this.age = ageInTicks;
				this.pos = now;
				return;
			}
			float dt = ageInTicks - this.age;
			if (dt <= 0.0F) {
				return;
			}
			this.age = ageInTicks;
			Vec3 v = now.subtract(this.pos).scale(1.0 / dt);
			this.pos = now;
			float yaw = bodyYaw * Mth.DEG_TO_RAD;
			double forward = -Mth.sin(yaw) * v.x + Mth.cos(yaw) * v.z;
			double side = Mth.cos(yaw) * v.x + Mth.sin(yaw) * v.z;
			double accelForward = (forward - this.forward) / dt, accelSide = (side - this.side) / dt, accelUp = (v.y - this.up) / dt;
			this.forward = forward;
			this.side = side;
			this.up = v.y;
			// a positive swing takes the bottom back (+z in the body's space); bounded so a teleport can't fling it round
			float pushSwing = (float)Mth.clamp((PUSH_FORWARD * accelForward + DRAG * forward - PUSH_FALL * accelUp) * this.weight, -1.0, 1.0);
			float pushRoll = (float)Mth.clamp(PUSH_SIDE * accelSide * this.weight, -1.0, 1.0);
			for (float left = dt; left > 0.0F; left -= 1.0F) {
				float h = Math.min(1.0F, left);
				this.swingSpeed += (STIFFNESS * (pushSwing - this.swing) - DAMPING * this.swingSpeed) * h;
				this.swing = Mth.clamp(this.swing + this.swingSpeed * h, -SWING_MAX, SWING_MAX);
				this.rollSpeed += (STIFFNESS * (pushRoll - this.roll) - DAMPING * this.rollSpeed) * h;
				this.roll = Mth.clamp(this.roll + this.rollSpeed * h, -ROLL_MAX, ROLL_MAX);
			}
		}
	}

	/** The pole: a stripped-spruce staff along -y from its foot, with a short hook reaching forward (-z) off its top. */
	static ModelPart pole() {
		if (pole == null) {
			MeshDefinition mesh = new MeshDefinition();
			float third = POLE_LENGTH / 3.0F;
			mesh.getRoot().addOrReplaceChild("pole", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-1.0F, -third, -1.0F, 2.0F, third, 2.0F)
				.texOffs(4, 0).addBox(-1.0F, -2.0F * third, -1.0F, 2.0F, third, 2.0F)
				.texOffs(8, 0).addBox(-1.0F, -POLE_LENGTH, -1.0F, 2.0F, third, 2.0F)
				.texOffs(0, 12).addBox(-0.5F, -POLE_LENGTH - 0.5F, -1.0F - POLE_HOOK, 1.0F, 1.0F, POLE_HOOK + 1.0F), PartPose.ZERO);
			pole = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("pole");
		}
		return pole;
	}

	/** Draws the hip lantern and the lantern pole on the body as it is posed now. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Worn worn = state.getData(KEY);
			ClientLevel level = Minecraft.getInstance().level;
			if (worn == null || state.isInvisible || level == null) {
				return;
			}
			PlayerModel body = this.getParentModel();
			poseStack.pushPose();
			body.root().translateAndRotate(poseStack);
			body.body.translateAndRotate(poseStack);
			if (worn.hip() != null) {
				// the off-hand side: the model's +x is the body's left
				float side = state.mainArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
				ModelPart leg = side > 0.0F ? body.leftLeg : body.rightLeg;
				poseStack.pushPose();
				poseStack.translate(side * HIP_X / 16.0F, HIP_Y / 16.0F, HIP_Z / 16.0F);
				hang(poseStack, collector, level, state, worn.hip(), HIP_SIZE, -body.body.xRot, worn.hipSwing() + Math.min(0.0F, leg.xRot) * LEG_NUDGE,
					worn.hipRoll());
				poseStack.popPose();
			}
			if (worn.pole() != null) {
				boolean armour = !state.chestEquipment.isEmpty();
				float out = POLE_Z + (armour ? OVER_ARMOUR : 0.0F) + (worn.backpack() ? OVER_BACKPACK : 0.0F);
				poseStack.pushPose();
				poseStack.translate(POLE_X / 16.0F, POLE_Y / 16.0F, out / 16.0F);
				poseStack.rotateDegrees(Axis.XP, POLE_LEAN * Mth.RAD_TO_DEG);
				collector.submitModelPart(pole(), poseStack, RenderTypes.entityCutout(POLE_TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, null);
				poseStack.translate(0.0F, -POLE_LENGTH / 16.0F, -POLE_HOOK / 16.0F);
				hang(poseStack, collector, level, state, worn.pole(), POLE_SIZE, -body.body.xRot - POLE_LEAN, worn.poleSwing(), worn.poleRoll());
				poseStack.popPose();
			}
			poseStack.popPose();
		}

		/**
		 * A lantern hung from here: turned back upright by {@code upright} (radians about x) so it hangs straight down, swung,
		 * and drawn as its hanging block with the top of its chain at the hook.
		 */
		private static void hang(PoseStack poseStack, SubmitNodeCollector collector, ClientLevel level, AvatarRenderState state, BlockState block,
			float size, float upright, float swing, float roll) {
			poseStack.rotateDegrees(Axis.XP, (upright + swing) * Mth.RAD_TO_DEG);
			poseStack.rotateDegrees(Axis.ZP, roll * Mth.RAD_TO_DEG);
			MovingBlockRenderState moving = new MovingBlockRenderState();
			BlockPos at = BlockPos.containing(state.x, state.y + 1.0, state.z);
			moving.blockState = block;
			moving.blockPos = at;
			moving.randomSeedPos = at;
			moving.biome = level.getBiome(at);
			moving.cardinalLighting = level.cardinalLighting();
			moving.lightEngine = level.getLightEngine();
			// the layer's space is the model's (y down); flipped upright, the block's top (the chain's hook) at the origin
			poseStack.scale(-size, -size, size);
			poseStack.translate(-0.5F, -1.0F, -0.5F);
			collector.submitMovingBlock(poseStack, moving, 0);
		}
	}
}
