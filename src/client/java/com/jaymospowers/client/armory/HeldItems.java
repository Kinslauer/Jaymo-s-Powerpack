package com.jaymospowers.client.armory;

import com.jaymospowers.ground.FirstTools;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Motion patch 4) Holding and using everyday things, after Actions &amp; Stuff:
 * <ul>
 * <li><b>Flint and steel</b>, and flint and a rock ({@link FirstTools#striking}): struck with both hands, the other hand
 * held out in front and the striking hand brought down across it, with a spray of sparks off flint and steel (the rock
 * strike already throws its own).</li>
 * <li><b>Shears:</b> brought in towards the middle of the view, snipping as they're used.</li>
 * <li><b>Compass and clock:</b> held up in front of you to read, as Not Enough Animations holds a map; in third person
 * the arm comes up when you look down at it.</li>
 * <li><b>Lantern</b> (any lantern block): held out a little, hanging and swinging with your step.</li>
 * </ul>
 * Third person is worked out from what each player holds, how they swing and walk (all synced), so everyone sees the same.
 */
public final class HeldItems {
	// ===== the knobs ================================================================================================
	/** Third person: the strike's held-out hand and how far the striking hand comes down (radians). */
	static final float STRIKE_HOLD = -1.15F, STRIKE_FROM = -1.75F, STRIKE_TO = -0.85F;
	/** Third person: reading a compass or clock once looking down past this (degrees); the arm raised this far. */
	static final float READ_PITCH = 25.0F, READ_ARM = -1.05F, READ_IN = 0.35F;
	/** Third person: the lantern held out (radians) and its swing with the step (radians at a full walk). */
	static final float LANTERN_OUT = -0.35F, LANTERN_SWAY = 0.18F;
	/** First person: the compass or clock raised and tilted to the eye (blocks, degrees). */
	static final float READ_UP = 0.10F, READ_CENTRE = 0.12F, READ_TILT = 28.0F;
	/** First person: shears moved in (blocks) and how hard a snip shakes them (degrees). */
	static final float SHEARS_IN = 0.2F, SHEARS_UP = 0.04F, SNIP = 14.0F;
	/** First person: how far the lantern swings (degrees) and the strike's extra downward chop (degrees). */
	static final float LANTERN_SWING = 7.0F, STRIKE_CHOP = 32.0F;
	/** Sparks off flint and steel per strike. */
	static final int SPARKS = 7;
	// ================================================================================================================

	enum Hold {
		STRIKE, READ, LANTERN
	}

	/** {@code arm}: the hand holding it (the striking hand for a strike); {@code amount}: swing progress for a strike, sway for a lantern. */
	public record Held(Hold hold, HumanoidArm arm, float amount, float in) {
	}

	public static final RenderStateDataKey<Held> KEY = RenderStateDataKey.create(() -> "jaymospowers:held_item");

	/** Per entity id: whether it was swinging last tick (for the sparks). */
	private static final Map<Integer, Boolean> SWINGING = new HashMap<>();

	private HeldItems() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(HeldItems::tick);
	}

	static boolean reads(ItemStack stack) {
		return stack.is(Items.COMPASS) || stack.is(Items.RECOVERY_COMPASS) || stack.is(Items.CLOCK);
	}

	static boolean lantern(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof LanternBlock;
	}

	static HumanoidArm arm(Player player, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		state.setData(KEY, entity instanceof Player player && state instanceof AvatarRenderState ? held(player, state, partialTicks) : null);
	}

	private static @Nullable Held held(Player player, LivingEntityRenderState state, float partialTicks) {
		if (player.isSleeping() || player.isSpectator() || player.isVisuallySwimming() || player.isFallFlying()) {
			return null;
		}
		ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
		LivingEntity.SwingDescription swing = player.getCurrentSwing();
		float progress = player.getSwingAnimation(partialTicks);
		if (player.isSwinging() && swing != null && (strikes(player.getItemInHand(swing.hand())) || FirstTools.striking(player))) {
			return new Held(Hold.STRIKE, arm(player, swing.hand()), progress, 1.0F);
		}
		if (player.isUsingItem() || player.isSwinging()) {
			return null;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = hand == InteractionHand.MAIN_HAND ? main : off;
			if (reads(stack) && player.getXRot() > READ_PITCH) {
				float in = Mth.clamp((player.getXRot() - READ_PITCH) / 15.0F, 0.0F, 1.0F);
				return new Held(Hold.READ, arm(player, hand), 0.0F, in);
			}
			if (lantern(stack)) {
				float sway = Mth.sin(state.walkAnimationPos * 0.6662F) * Math.min(1.0F, state.walkAnimationSpeed);
				return new Held(Hold.LANTERN, arm(player, hand), sway, 1.0F);
			}
		}
		return null;
	}

	static boolean strikes(ItemStack stack) {
		return stack.is(Items.FLINT_AND_STEEL);
	}

	/** After the model animates: the arms for each hold. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h) || living.deathTime > 0.0F) {
			return;
		}
		Held held = living.getData(KEY);
		if (held == null) {
			return;
		}
		boolean right = held.arm() == HumanoidArm.RIGHT;
		ModelPart arm = right ? h.rightArm : h.leftArm, other = right ? h.leftArm : h.rightArm;
		float side = right ? 1.0F : -1.0F;
		switch (held.hold()) {
			case STRIKE -> {
				// the other hand out in front, palm up; the striking hand brought down across it
				other.xRot = STRIKE_HOLD;
				other.yRot = 0.35F * side;
				other.zRot = 0.0F;
				float p = Mth.sin(held.amount() * Mth.PI);
				arm.xRot = Mth.lerp(p, STRIKE_FROM, STRIKE_TO);
				arm.yRot = -0.45F * side;
				arm.zRot = 0.0F;
			}
			case READ -> {
				float e = held.in();
				arm.xRot = Mth.lerp(e, arm.xRot, READ_ARM);
				arm.yRot = Mth.lerp(e, arm.yRot, -READ_IN * side);
				arm.zRot = Mth.lerp(e, arm.zRot, 0.0F);
			}
			case LANTERN -> {
				arm.xRot = LANTERN_OUT + held.amount() * LANTERN_SWAY * 0.5F;
				arm.zRot = held.amount() * LANTERN_SWAY * side * 0.4F + 0.08F * side;
			}
		}
	}

	/** Sparks fly from flint and steel as a strike begins, for everyone in view. */
	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			SWINGING.clear();
			return;
		}
		for (Player player : level.players()) {
			boolean swinging = player.isSwinging();
			boolean was = SWINGING.getOrDefault(player.getId(), false);
			SWINGING.put(player.getId(), swinging);
			LivingEntity.SwingDescription swing = player.getCurrentSwing();
			if (!swinging || was || swing == null || !strikes(player.getItemInHand(swing.hand()))) {
				continue;
			}
			// in front of the chest, where the hands meet
			Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
			Vec3 at = player.position().add(look.scale(0.45)).add(0.0, player.getBbHeight() * 0.6, 0.0);
			for (int i = 0; i < SPARKS; i++) {
				double vx = (level.getRandom().nextDouble() - 0.5) * 0.15 + look.x * 0.08, vz = (level.getRandom().nextDouble() - 0.5) * 0.15 + look.z * 0.08;
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, vx, -0.02 - level.getRandom().nextDouble() * 0.05, vz);
			}
		}
		if (level.getGameTime() % 600 == 0) {
			SWINGING.keySet().removeIf(id -> level.getEntity(id) == null);
		}
	}

	/** First person, just before the held item is drawn: the item moved for its use. */
	public static void firstPerson(PoseStack poseStack, HumanoidArm arm, ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || stack.isEmpty()) {
			return;
		}
		float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		float swing = player.isSwinging() ? player.getSwingAnimation(partialTicks) : 0.0F;
		if (reads(stack)) {
			poseStack.translate(-READ_CENTRE * side, READ_UP, 0.0F);
			poseStack.rotateDegrees(Axis.XP, READ_TILT);
		} else if (stack.is(Items.SHEARS)) {
			poseStack.translate(-SHEARS_IN * side, SHEARS_UP, 0.0F);
			if (swing > 0.0F) {
				poseStack.rotateDegrees(Axis.ZP, Mth.sin(swing * Mth.PI * 4.0F) * SNIP * side);
			}
		} else if (lantern(stack)) {
			float walk = player.walkAnimation.position(partialTicks), speed = Math.min(1.0F, player.walkAnimation.speed(partialTicks));
			// swung about its handle, at the top of the held lantern
			poseStack.translate(0.0F, 0.25F, 0.0F);
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(walk * 0.6662F) * LANTERN_SWING * speed);
			poseStack.rotateDegrees(Axis.XP, Mth.cos(walk * 0.6662F) * LANTERN_SWING * 0.5F * speed);
			poseStack.translate(0.0F, -0.25F, 0.0F);
		} else if ((strikes(stack) || FirstTools.striking(player)) && swing > 0.0F) {
			poseStack.rotateDegrees(Axis.XP, -Mth.sin(swing * Mth.PI) * STRIKE_CHOP);
			poseStack.translate(-0.12F * side * Mth.sin(swing * Mth.PI), 0.0F, 0.0F);
		}
	}
}
