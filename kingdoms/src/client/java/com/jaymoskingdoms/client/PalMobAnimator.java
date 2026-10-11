package com.jaymoskingdoms.client;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.util.RenderUtil;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.HumanoidAnimationController;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.molang.MolangLoader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The part of {@link MobAnimations} that uses Player Animation Library (only loaded when it is). One humanoid animation
 * controller per swinging mob, kept while it plays: the attack (an animation Better Combat ships, loaded by the library
 * from every resource pack's {@code player_animations}) is stretched over the swing's length, ticked with the game,
 * evaluated once a frame, and read bone by bone onto the model's parts.
 */
final class PalMobAnimator {
	/** The animated mobs on this client, by entity id. */
	private static final Map<Integer, Playing> PLAYING = new HashMap<>();
	/** The library's names for the humanoid bones, in {@link #pose}'s order. */
	private static final String[] BONES = {"head", "torso", "right_arm", "left_arm", "right_leg", "left_leg"};
	/** Set if the library can't be used as expected (then nothing is animated). */
	private static boolean broken;

	private PalMobAnimator() {
	}

	/** A mob's controller, its speed (the stretch), and a bone to read each part through. */
	private static final class Playing {
		final HumanoidAnimationController controller;
		final SpeedModifier speed = new SpeedModifier(1.0F);
		final PlayerAnimBone[] bones = new PlayerAnimBone[BONES.length];

		Playing() {
			this.controller = newController();
			this.controller.addModifierLast(this.speed);
			for (int i = 0; i < BONES.length; i++) {
				this.bones[i] = new PlayerAnimBone(BONES[i]);
			}
		}
	}

	/**
	 * A humanoid controller with the library's own Molang engine, built reflectively: the engine's type lives in a
	 * library nested in Player Animation Library's jar, which isn't on our compile path. Triggered animations play
	 * whatever the state handler says, so it plays nothing on its own.
	 */
	private static HumanoidAnimationController newController() {
		try {
			Method create = MolangLoader.class.getMethod("createNewEngine", AnimationController.class);
			Function<Object, Object> engine = controller -> {
				try {
					return create.invoke(null, controller);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			};
			AnimationController.AnimationStateHandler idle = (controller, data, setter) -> PlayState.STOP;
			return HumanoidAnimationController.class.getConstructor(AnimationController.AnimationStateHandler.class, Function.class)
				.newInstance(idle, engine);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Player Animation Library's humanoid controller has changed", e);
		}
	}

	/** Starts {@code animation} on the mob, stretched over {@code length} ticks. */
	static void play(int entityId, String animation, int length) {
		Identifier id = Identifier.tryParse(animation);
		if (id == null) {
			return;
		}
		Animation anim = PlayerAnimResources.getAnimation(id);
		if (anim == null) {
			return;
		}
		if (broken) {
			return;
		}
		Playing playing;
		try {
			playing = PLAYING.computeIfAbsent(entityId, k -> new Playing());
		} catch (RuntimeException e) {
			// the library changed under us: mobs keep vanilla's swing from now on
			broken = true;
			com.jaymoskingdoms.JaymosKingdoms.LOGGER.warn("Can't animate mobs' Better Combat swings", e);
			return;
		}
		float animLength = anim.data().<Number>get("endTick").map(Number::floatValue).orElse(anim.length());
		playing.speed.speed = Math.max(0.05F, animLength / Math.max(1, length));
		playing.controller.triggerAnimation(anim);
	}

	static void tick(@Nullable ClientLevel level) {
		if (PLAYING.isEmpty()) {
			return;
		}
		if (level == null) {
			PLAYING.clear();
			return;
		}
		for (Iterator<Map.Entry<Integer, Playing>> it = PLAYING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Playing> entry = it.next();
			Entity entity = level.getEntity(entry.getKey());
			HumanoidAnimationController controller = entry.getValue().controller;
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || !controller.isActive()) {
				it.remove();
				continue;
			}
			controller.tick(new AnimationData(velocity(living), 1.0F, false));
		}
	}

	/** This frame's pose of the mob's animation (the handle {@link #pose} reads), or null when it isn't swinging. */
	static @Nullable Object extract(LivingEntity entity, float partialTicks) {
		Playing playing = PLAYING.get(entity.getId());
		if (playing == null || !playing.controller.isActive()) {
			return null;
		}
		playing.controller.setupAnim(new AnimationData(velocity(entity), partialTicks, false));
		return playing;
	}

	/** Lays the animation's bones onto the model's parts (any may be null: an illager has no body to bend). */
	static void pose(Object handle, @Nullable ModelPart head, @Nullable ModelPart body, @Nullable ModelPart rightArm,
		@Nullable ModelPart leftArm, @Nullable ModelPart rightLeg, @Nullable ModelPart leftLeg) {
		if (!(handle instanceof Playing playing)) {
			return;
		}
		ModelPart[] parts = {head, body, rightArm, leftArm, rightLeg, leftLeg};
		for (int i = 0; i < parts.length; i++) {
			ModelPart part = parts[i];
			if (part == null) {
				continue;
			}
			PlayerAnimBone bone = RenderUtil.copyVanillaPart(part, playing.bones[i]);
			playing.controller.get3DTransform(bone);
			RenderUtil.translatePartToBone(part, bone, part.getInitialPose());
		}
	}

	private static float velocity(LivingEntity entity) {
		return (float) entity.getDeltaMovement().horizontalDistance();
	}
}
