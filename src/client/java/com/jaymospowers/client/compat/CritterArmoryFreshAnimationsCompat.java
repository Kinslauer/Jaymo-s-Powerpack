package com.jaymospowers.client.compat;

import java.lang.reflect.Field;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.animal.equine.AbstractEquineModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Fresh Animations (run by Entity Model Features) re-animates animal models after Critter Armory captured their pose
 * for its armour layers, so Critter Armory's pig, sheep and horse armour is drawn in the vanilla pose and slides off
 * the animated model. This compat keeps Fresh Animations' pose on the render state just before the render layers, so
 * Critter Armory's armour models take it - the same shape as Kingdoms' WornModel and ArmorPoseLivingEntityRendererMixin
 * keep Fresh Animations' pose on our own armour. Pigs and sheep are drawn through vanilla QuadrupedModels, horses
 * through AbstractEquineModels (their armour is the vanilla horse body armour layer). Only active when both Entity
 * Model Features and Critter Armory are loaded; Critter Armory is linked, never edited.
 */
public final class CritterArmoryFreshAnimationsCompat {
	public static final boolean EMF = FabricLoader.getInstance().isModLoaded("entity_model_features");
	public static final boolean CRITTER_ARMORY = FabricLoader.getInstance().isModLoaded("critterarmory");

	/** The body's pose as Fresh Animations animated it, kept on the render state for Critter Armory's armour models. */
	public static final RenderStateDataKey<PartPose[]> CRITTER_ARMOR_EMF_POSES =
			RenderStateDataKey.create(() -> "jaymospowers:critter_armor_emf_poses");

	/** QuadrupedModel's parts (pigs, sheep): head, body, then the legs. */
	private static final Field[] QUADRUPED_PARTS = fields(QuadrupedModel.class,
		"head", "body", "rightHindLeg", "leftHindLeg", "rightFrontLeg", "leftFrontLeg");
	/** AbstractEquineModel's parts (horses, donkeys, mules): head and neck, body, then the legs, in the same order. */
	private static final Field[] EQUINE_PARTS = fields(AbstractEquineModel.class,
		"headParts", "body", "rightHindLeg", "leftHindLeg", "rightFrontLeg", "leftFrontLeg");

	private CritterArmoryFreshAnimationsCompat() {
	}

	/** Whether both Fresh Animations' engine and Critter Armory are loaded, so the compat has something to do. */
	public static boolean active() {
		return EMF && CRITTER_ARMORY;
	}

	private static Field[] fields(Class<?> type, String... names) {
		try {
			Field[] fields = new Field[names.length];
			for (int i = 0; i < names.length; i++) {
				fields[i] = type.getDeclaredField(names[i]);
				fields[i].setAccessible(true);
			}
			return fields;
		} catch (Exception e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't read " + type.getName() + "'s parts; Critter Armory's armour keeps the vanilla pose under Fresh Animations", e);
			return new Field[0];
		}
	}

	/**
	 * Keeps the animal's body pose on the render state, if Entity Model Features has just animated it this frame (it has
	 * animations and none are paused: Powers' own poses and paused animations stay as they are). Only call when active().
	 */
	public static void capture(EntityModel<?> model, EntityRenderState state) {
		Field[] fields;
		if (model instanceof QuadrupedModel<?>) {
			fields = QUADRUPED_PARTS;
		} else if (model instanceof AbstractEquineModel<?>) {
			fields = EQUINE_PARTS;
		} else {
			return;
		}
		if (fields.length != 6 || !CritterArmoryEmf.animatedNow(state, model)) {
			return;
		}
		PartPose[] pose = new PartPose[6];
		try {
			for (int i = 0; i < 6; i++) {
				pose[i] = ((ModelPart) fields[i].get(model)).storePose();
			}
		} catch (IllegalAccessException e) {
			return;
		}
		state.setData(CRITTER_ARMOR_EMF_POSES, pose);
	}

	/** Whether this is a plain model (such as Critter Armory's armour model), not one Entity Model Features drives. */
	public static boolean plainModel(EntityModel<?> model) {
		return !CritterArmoryEmf.isEmfModel(model);
	}

	/**
	 * Loads the captured pose into an armour model (head, body, then the legs as {@link #capture} kept them), when the
	 * state has one and the model is a plain one.
	 */
	public static void apply(EntityModel<?> model, EntityRenderState state, ModelPart head, ModelPart body, ModelPart rightHind,
		ModelPart leftHind, ModelPart rightFront, ModelPart leftFront) {
		PartPose[] poses = state.getData(CRITTER_ARMOR_EMF_POSES);
		if (poses == null || poses.length != 6 || !plainModel(model)) {
			return;
		}
		head.loadPose(poses[0]);
		body.loadPose(poses[1]);
		rightHind.loadPose(poses[2]);
		leftHind.loadPose(poses[3]);
		rightFront.loadPose(poses[4]);
		leftFront.loadPose(poses[5]);
	}
}
