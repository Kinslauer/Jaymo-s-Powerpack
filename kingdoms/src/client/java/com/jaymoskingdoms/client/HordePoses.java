package com.jaymoskingdoms.client;

import com.jaymoskingdoms.HordeKinds;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jspecify.annotations.Nullable;

/**
 * How a horde's dead move ({@link HordeKinds}), laid over the zombie's own pose right after it's posed like
 * {@link CastPose}:
 * <ul>
 * <li>Walking, the arms reach and claw, one then the other, with the stride (more the faster it goes).</li>
 * <li>{@link HordeKinds#SHOULDER}: pounding a door, it turns its shoulder into it, the near arm tucked.</li>
 * <li>{@link HordeKinds#STAGGER}: hit hard, its head snaps back and its arms fly up.</li>
 * <li>{@link HordeKinds#LUNGE}: the last step onto its prey, both arms thrust high and in.</li>
 * <li>{@link HordeKinds#HOWL}: a scout's head thrown back, arms spread, as it howls.</li>
 * </ul>
 * While any of it is on a zombie, Entity Model Features (Fresh Animations) leaves that zombie alone
 * ({@code EmfBladePause}), so the two never fight over its arms.
 * <p>
 * Arm turns are the model's (radians): x forward and up from hanging, y in across the chest (right arm: negative), z
 * out to the side (right arm: positive).
 */
public final class HordePoses {
	/** The reach: how far each arm claws forward and back with the stride, how much higher the hands ride, how far in. */
	static final float CLAW = 0.35F, RAISE = 0.15F, IN = 0.15F;
	/** Walking faster than this (the walk's speed) draws the reach; this fast it's full. */
	static final float REACH_FROM = 0.05F, REACH_FULL = 0.6F;
	/** How long each act lasts (ticks): shoulder, stagger, lunge, howl. */
	static final float SHOULDER_TICKS = 10.0F, STAGGER_TICKS = 12.0F, LUNGE_TICKS = 8.0F, HOWL_TICKS = 24.0F;

	/** This frame's {reach weight, stride, act, ticks into it} for a horde's zombie, on its render state. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:horde_pose");

	/** Entity id → {act, the entity's age when it began}. */
	private static final Int2ObjectMap<int[]> ACTS = new Int2ObjectOpenHashMap<>();

	private HordePoses() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(HordeKinds.Act.TYPE, (payload, context) -> {
			Minecraft minecraft = context.client();
			Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(payload.entity());
			if (entity != null) {
				ACTS.put(payload.entity(), new int[] {payload.act(), entity.tickCount});
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ACTS.clear());
	}

	private static float length(int act) {
		return switch (act) {
			case HordeKinds.SHOULDER -> SHOULDER_TICKS;
			case HordeKinds.STAGGER -> STAGGER_TICKS;
			case HordeKinds.LUNGE -> LUNGE_TICKS;
			case HordeKinds.HOWL -> HOWL_TICKS;
			default -> 0.0F;
		};
	}

	/** {act, ticks into it} for {@code entity} now, or null (an act run out is forgotten). */
	private static float @Nullable [] act(Entity entity, float partialTicks) {
		int[] act = ACTS.get(entity.getId());
		if (act == null) {
			return null;
		}
		float age = entity.tickCount + partialTicks - act[1];
		if (age < 0.0F || age >= length(act[0])) {
			ACTS.remove(entity.getId());
			return null;
		}
		return new float[] {act[0], age};
	}

	private static boolean marching(Entity entity) {
		return entity instanceof Zombie && entity.hasAttached(HordeKinds.MARCHING);
	}

	/** Whether EMF should leave this entity's model alone: a horde's zombie walking or in the middle of an act. */
	public static boolean posed(Object entity) {
		return entity instanceof LivingEntity living && marching(living)
			&& (living.walkAnimation.speed() > REACH_FROM || ACTS.containsKey(living.getId()));
	}

	/** Render-state extraction: a horde zombie's reach and act for this frame. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(state instanceof ZombieRenderState) || !marching(entity)) {
			return;
		}
		float reach = Mth.clamp((state.walkAnimationSpeed - REACH_FROM) / (REACH_FULL - REACH_FROM), 0.0F, 1.0F);
		float[] act = ACTS.isEmpty() ? null : act(entity, partialTicks);
		if (reach > 0.0F || act != null) {
			state.setData(KEY, new float[] {reach, state.walkAnimationPos, act == null ? 0.0F : act[0], act == null ? 0.0F : act[1]});
		}
	}

	/** Right after a model was posed for this state: the reach, then the act over it. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof ZombieRenderState zombie) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] pose = zombie.getData(KEY);
		if (pose == null) {
			return;
		}
		float reach = pose[0];
		if (reach > 0.0F) {
			float claw = Mth.sin(pose[1] * 0.6662F) * CLAW * reach;
			m.rightArm.xRot += -RAISE * reach + claw;
			m.leftArm.xRot += -RAISE * reach - claw;
			m.rightArm.yRot -= IN * reach;
			m.leftArm.yRot += IN * reach;
		}
		int act = (int) pose[2];
		if (act == 0) {
			return;
		}
		float t = pose[3] / length(act);
		// in quick, out slower
		float w = t < 0.3F ? ease(t / 0.3F) : ease((1.0F - t) / 0.7F);
		switch (act) {
			case HordeKinds.SHOULDER -> {
				// the body turned, the right shoulder leading into the door, that arm tucked and the other braced
				float turn = 0.55F * w;
				m.body.yRot += turn;
				m.head.yRot += turn * 0.5F;
				m.rightArm.x = -Mth.cos(m.body.yRot) * 5.0F;
				m.rightArm.z = Mth.sin(m.body.yRot) * 5.0F;
				m.leftArm.x = Mth.cos(m.body.yRot) * 5.0F;
				m.leftArm.z = -Mth.sin(m.body.yRot) * 5.0F;
				set(m.rightArm, -0.35F, turn - 0.2F, 0.1F, w);
				set(m.leftArm, -1.3F, turn + 0.3F, 0.0F, w);
			}
			case HordeKinds.STAGGER -> {
				m.head.xRot -= 0.55F * w;
				set(m.rightArm, -2.5F, 0.0F, 0.45F, w);
				set(m.leftArm, -2.5F, 0.0F, -0.45F, w);
			}
			case HordeKinds.LUNGE -> {
				m.head.xRot += 0.15F * w;
				set(m.rightArm, -1.95F, -0.3F, 0.0F, w);
				set(m.leftArm, -1.95F, 0.3F, 0.0F, w);
			}
			case HordeKinds.HOWL -> {
				m.head.xRot = Mth.lerp(w, m.head.xRot, -0.7F);
				set(m.rightArm, -0.9F, 0.0F, 0.6F, w);
				set(m.leftArm, -0.9F, 0.0F, -0.6F, w);
			}
			default -> {
			}
		}
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static void set(ModelPart part, float x, float y, float z, float w) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
