package com.jaymoskingdoms.client;

import com.jaymoskingdoms.MobCombat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.AbstractIllager;

/**
 * Better Combat's attack animations on mobs (the client half of {@link MobCombat}). Player Animation Library only
 * animates players' models, but its animation core is model-agnostic: {@link PalMobAnimator} plays the attack on a
 * controller of our own per mob and lays its bones onto the mob's humanoid (or illager) model after vanilla posed it
 * (the {@code ModelFeatureRenderer} and {@code LivingEntityRenderer} mixins), so armour and held items follow the swing.
 * <p>
 * Between swings, a mob holding a two-handed weapon holds it in both hands in the weapon's Better Combat pose
 * ({@link MobCombat#holdPose}, {@link PalMobAnimator#hold}), as a player does; and someone fishing plays the angler's poses
 * ({@code com.jaymoskingdoms.Anglers#pose}: our own animations in {@code assets/jaymoskingdoms/player_animations}).
 * <p>
 * This class never touches the library's classes; without it (or without Better Combat), mobs swing their arm as
 * vanilla does.
 */
public final class MobAnimations {
	/** Player Animation Library's mod id. */
	static final boolean PAL = FabricLoader.getInstance().isModLoaded("player_animation_library");

	/** The mob's animation for this frame (the library's controller, opaque here), on its render state. */
	static final RenderStateDataKey<Object> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:mob_swing");

	private MobAnimations() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MobCombat.MobSwing.TYPE, (payload, context) -> {
			// (a dodger's roll is our own keyframes, with or without the library: NpcRollPose)
			if (com.jaymoskingdoms.NpcDodge.ROLL_ANIMATION.equals(payload.animation())) {
				NpcRollPose.start(context.client(), payload.entityId(), payload.length(), payload.mirror());
				return;
			}
			if (PAL) {
				PalMobAnimator.play(payload.entityId(), payload.animation(), payload.length(), payload.mirror());
			}
		});
		NpcRollPose.register();
		if (PAL) {
			ClientTickEvents.END_CLIENT_TICK.register(client -> PalMobAnimator.tick(client.level));
		}
	}

	/** Render-state extraction: hands this frame's animation to the models (none for players: they have their own). */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!PAL || entity instanceof Avatar) {
			return;
		}
		Object animation = PalMobAnimator.extract(entity, partialTicks);
		String angling = animation == null ? com.jaymoskingdoms.Anglers.pose(entity) : null;
		if (angling != null) {
			// fishing: casting, waiting on a bite, fighting a fish, swinging it in (Anglers)
			animation = PalMobAnimator.hold(entity, angling, partialTicks);
		}
		if (animation == null && entity instanceof net.minecraft.world.entity.Mob mob) {
			// between swings: a two-handed weapon held in both hands (MobCombat.holdPose)
			String pose = MobCombat.holdPose(mob);
			if (pose != null) {
				animation = PalMobAnimator.hold(entity, pose, partialTicks);
			}
		}
		state.setData(KEY, animation);
	}

	/** Right after a model was posed for this state: lays the attack animation over it. */
	public static void apply(Model<?> model, Object state) {
		if (!PAL || !(state instanceof LivingEntityRenderState living)) {
			return;
		}
		Object animation = living.getData(KEY);
		if (animation == null) {
			return;
		}
		if (model instanceof HumanoidModel<?> humanoid) {
			PalMobAnimator.pose(animation, humanoid.head, humanoid.body, humanoid.rightArm, humanoid.leftArm, humanoid.rightLeg, humanoid.leftLeg);
		} else if (model instanceof IllagerModel<?> && state instanceof IllagerRenderState illagerState
			&& illagerState.armPose == AbstractIllager.IllagerArmPose.ATTACKING && model instanceof IllagerParts parts) {
			PalMobAnimator.pose(animation, parts.jaymoskingdoms$head(), null, parts.jaymoskingdoms$rightArm(), parts.jaymoskingdoms$leftArm(),
				parts.jaymoskingdoms$rightLeg(), parts.jaymoskingdoms$leftLeg());
		}
	}

	/** An illager model's limbs (its fields are private; {@code IllagerModelAccessor} implements this). */
	public interface IllagerParts {
		ModelPart jaymoskingdoms$head();

		ModelPart jaymoskingdoms$rightArm();

		ModelPart jaymoskingdoms$leftArm();

		ModelPart jaymoskingdoms$rightLeg();

		ModelPart jaymoskingdoms$leftLeg();
	}
}
