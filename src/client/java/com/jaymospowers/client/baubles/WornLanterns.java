package com.jaymospowers.client.baubles;

import com.jaymospowers.baubles.Baubles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import com.jaymospowers.client.armory.HeldItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
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
 * <p>
 * NPCs wear theirs the same way (Andy, 2026-11-27, pointing at Traveler's Lantern: "npcs and players should have this
 * animation when a lantern is equipped"). An NPC on a humanoid body (MCA villagers and the folk, guards, zombies and the
 * like) with a lantern in its off hand, which is where {@code NightLights} puts the light it carries at night, has it
 * hung at the off-hand hip on the same spring instead of in the hand ({@link #extractNpc}); the hand is drawn empty.
 * <p>
 * A hip lantern gives a quiet clink as the leg knocks it on the stride ({@link #clinks}).
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
	/**
	 * (0.20.0) A hip item that isn't a lantern (Jaymo's Kingdoms' coin purse, in {@code #jaymospowers:hip_shown}) hangs at the
	 * right hip, by its hook (the top of the sprite), at {@code PURSE_SIZE} blocks across. Its model is the item's own, so the
	 * purse shows how full it is (custom_model_data). {@code FIXED_SCALE}: the scale the fixed display transform applies already.
	 */
	static final float PURSE_X = 4.9F, PURSE_Y = 10.0F, PURSE_Z = -0.5F, PURSE_SIZE = 0.35F, FIXED_SCALE = 0.5F;
	/**
	 * (0.20.82) Andy: "they dont hang, ON the hip they are inside". It hung at 3.4 pixels out, inside the leg (which reaches
	 * 4, and its trouser layer past that); now it hangs outside the hip, and this much further out over leggings or a
	 * chestplate.
	 */
	static final float PURSE_OVER_ARMOUR = 1.0F, PURSE_ROLL_IN = 0.1F;
	/** The clink: how loud, the strides (0 to 1) it needs, how often a knock clinks, and how far away it's heard (blocks). */
	static final float CLINK_VOLUME = 0.12F, CLINK_STRIDE = 0.45F, CLINK_CHANCE = 0.6F, CLINK_RANGE = 16.0F;
	/** The pole's wood. */
	static final Identifier POLE_TEXTURE = Identifier.withDefaultNamespace("textures/block/stripped_spruce_log.png");
	// ================================================================================================================

	/** What a player wears hung on them, as drawn this frame. */
	record Worn(@Nullable BlockState hip, @Nullable ItemStackRenderState purse, @Nullable BlockState pole, float hipSwing, float hipRoll, float poleSwing, float poleRoll,
		boolean backpack) {
	}

	static final RenderStateDataKey<Worn> KEY = RenderStateDataKey.create(() -> "jaymospowers:worn_lanterns");
	private static final Map<LivingEntity, Swing[]> SWINGS = new WeakHashMap<>();
	/** Per entity id: the stride the off-hand leg last knocked the hip lantern on. */
	private static final Map<Integer, Integer> KNOCKS = new HashMap<>();
	private static @Nullable ModelPart pole;

	private WornLanterns() {
	}

	public static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer<>(avatar));
			} else if (renderer instanceof HumanoidMobRenderer<?, ?, ?> mob) {
				helper.register(npcLayer(mob));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(WornLanterns::clinks);
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
		ItemStack hipStack = Baubles.shown(player, 0);
		BlockState hip = Baubles.hipLantern(hipStack) ? hanging(hipStack) : null;
		// (0.20.0) a hip item that is no lantern (a coin purse) is drawn with the item renderer
		ItemStackRenderState purse = null;
		if (hip == null && !hipStack.isEmpty() && Baubles.hipItem(hipStack)) {
			purse = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver().updateForLiving(purse, hipStack, ItemDisplayContext.FIXED, player);
			if (purse.isEmpty()) {
				purse = null;
			}
		}
		BlockState onPole = Baubles.shown(player, 1).isEmpty() ? null : Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
		if (hip == null && purse == null && onPole == null) {
			state.setData(KEY, null);
			synchronized (SWINGS) {
				SWINGS.remove(entity);
			}
			return;
		}
		state.setData(KEY, worn(entity, state, partialTicks, hip, purse, onPole, com.jaymospowers.client.backpack.BackpackClient.worn(state)));
		if (onPole != null) {
			state.showCape = false;
		}
	}

	/** What {@code entity} wears, swung on its springs as it moves now. */
	private static Worn worn(LivingEntity entity, HumanoidRenderState state, float partialTicks, @Nullable BlockState hip, @Nullable ItemStackRenderState purse,
		@Nullable BlockState onPole, boolean backpack) {
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
		return new Worn(hip, purse, onPole,
			swings[0].swing, swings[0].roll + Swing.STEP_ROLL * Mth.sin(step) * stride,
			swings[1].swing, swings[1].roll + Swing.STEP_ROLL * 0.6F * Mth.sin(step) * stride,
			backpack);
	}

	/**
	 * From {@code HumanoidMobRenderer.extractRenderState}, once the hands' items and arm poses are in the state: an NPC's
	 * off-hand lantern hung at its hip, and the hand drawn empty.
	 */
	public static void extractNpc(LivingEntity entity, HumanoidRenderState state, float partialTicks) {
		if (entity instanceof Player) {
			return;
		}
		BlockState hip = npcHip(entity);
		if (hip == null || state.isVisuallySwimming || state.isFallFlying) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, worn(entity, state, partialTicks, hip, null, null, false));
		// the off hand's side, now empty
		if (entity.getMainArm() == HumanoidArm.RIGHT) {
			state.leftHandItemState.clear();
			state.leftHandItemStack = ItemStack.EMPTY;
			state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		} else {
			state.rightHandItemState.clear();
			state.rightHandItemStack = ItemStack.EMPTY;
			state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		}
	}

	/** An NPC's off-hand lantern, hanging; null if it carries none there (or is using that hand). */
	private static @Nullable BlockState npcHip(LivingEntity entity) {
		if (entity instanceof Player || !(entity instanceof Mob) || entity.isSleeping() || entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.OFF_HAND) {
			return null;
		}
		ItemStack off = entity.getOffhandItem();
		return HeldItems.lantern(off) ? hanging(off) : null;
	}

	/** Whether {@code entity} has a lantern hung at its hip: a player's belt slot, an NPC's off hand. */
	private static boolean hipLantern(LivingEntity entity) {
		if (entity instanceof Player player) {
			return Baubles.hipLantern(Baubles.shown(player, 0));
		}
		// an NPC only where it is drawn hung (a humanoid body: an illager's still holds it)
		return npcHip(entity) != null && Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof HumanoidMobRenderer<?, ?, ?>;
	}

	/**
	 * Each tick: a hip lantern near you clinks when the leg on its side swings forward into it on a walk or run (yours too,
	 * in first person). Heard, like a step, only close by.
	 */
	private static void clinks(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused() || minecraft.getCameraEntity() == null) {
			KNOCKS.clear();
			return;
		}
		Vec3 eye = minecraft.getCameraEntity().position();
		for (Entity e : level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || e.distanceToSqr(eye) > CLINK_RANGE * CLINK_RANGE || living.isInvisible() || !hipLantern(living)) {
				continue;
			}
			float speed = living.walkAnimation.speed();
			// the leg on the lantern's side is forward-most once a stride (the walk's sine at its trough for that leg)
			float side = living.getMainArm() == HumanoidArm.RIGHT ? 0.0F : Mth.PI;
			int knock = Mth.floor((living.walkAnimation.position() * 0.6662F + side + Mth.HALF_PI) / Mth.TWO_PI);
			Integer last = KNOCKS.put(living.getId(), knock);
			if (last == null || last == knock || speed < CLINK_STRIDE || !living.onGround() || level.getRandom().nextFloat() > CLINK_CHANCE) {
				continue;
			}
			float volume = CLINK_VOLUME * Math.min(1.0F, speed) * (living instanceof Player ? 1.0F : 0.8F);
			level.playLocalSound(living.getX(), living.getY() + living.getBbHeight() * 0.4, living.getZ(), SoundEvents.LANTERN_HIT,
				living.getSoundSource(), volume, 1.3F + level.getRandom().nextFloat() * 0.3F, false);
		}
		if (level.getGameTime() % 600 == 0) {
			KNOCKS.keySet().removeIf(id -> level.getEntity(id) == null);
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

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?, ?> npcLayer(HumanoidMobRenderer<?, ?, ?> renderer) {
		return new Layer((RenderLayerParent)renderer);
	}

	/** Draws the hip lantern and the lantern pole on the body as it is posed now (a player's, or an NPC's hip lantern). */
	static final class Layer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
		Layer(RenderLayerParent<S, M> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Worn worn = state.getData(KEY);
			ClientLevel level = Minecraft.getInstance().level;
			if (worn == null || state.isInvisible || level == null) {
				return;
			}
			M body = this.getParentModel();
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
			if (worn.purse() != null) {
				// the right hip (the model's -x), swinging with the same sway as a lantern, nudged out of the leg's way
				poseStack.pushPose();
				boolean armoured = !state.chestEquipment.isEmpty() || !state.legsEquipment.isEmpty();
				poseStack.translate(-(PURSE_X + (armoured ? PURSE_OVER_ARMOUR : 0.0F)) / 16.0F, PURSE_Y / 16.0F, PURSE_Z / 16.0F);
				poseStack.rotateDegrees(Axis.XP, (-body.body.xRot + worn.hipSwing() + Math.min(0.0F, body.rightLeg.xRot) * LEG_NUDGE) * Mth.RAD_TO_DEG);
				// ((0.20.82) it swings out from the leg freely, but only a little in toward it)
				poseStack.rotateDegrees(Axis.ZP, Math.max(-PURSE_ROLL_IN, worn.hipRoll()) * Mth.RAD_TO_DEG);
				// the layer's space is the model's (y down, x flipped): flipped upright, the sprite's top (its hook) at the origin
				float scale = PURSE_SIZE / FIXED_SCALE;
				poseStack.scale(-scale, -scale, scale);
				poseStack.translate(0.0F, -FIXED_SCALE, 0.0F);
				// flat against the hip, its face turned out to the side
				poseStack.rotateDegrees(Axis.YP, -90.0F);
				worn.purse().submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
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
		private static void hang(PoseStack poseStack, SubmitNodeCollector collector, ClientLevel level, HumanoidRenderState state, BlockState block,
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
