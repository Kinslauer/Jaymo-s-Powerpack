package com.jaymospowers.client;

import com.jaymospowers.client.armory.TaczFirstPerson;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.utils.EMFAnimationPauseHandler;
import traben.entity_model_features.utils.EMFEntity;

/** The only class that touches Entity Model Features (see EmfCompat); loaded only when EMF is. */
final class EmfAccess {
	private static boolean broken;

	private EmfAccess() {
	}

	/**
	 * (0.11.78) Whether EMF has just animated this body: it has animations and nothing paused them (our poses, Better
	 * Combat's swings and emotes do; then the vanilla pose stands, with our poses laid over it). See EmfCompat#bodyAnimated.
	 */
	static boolean animatedNow(EntityRenderState state, HumanoidModel<?> body) {
		if (broken || !(body.root() instanceof EMFModelPartRoot root) || !root.hasAnimation()) {
			return false;
		}
		try {
			EMFEntityRenderState emf = EMFEntityRenderState.from(state);
			return emf != null && !EMFAnimationPauseHandler.shouldAnimationsPause(emf);
		} catch (Throwable e) {
			broken = true;
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't ask Entity Model Features about the body's pose; costumes keep the plain one", e);
			return false;
		}
	}

	/** The players we paused EMF for ourselves (see EmfCompat#hold), by entity id. */
	private static final IntOpenHashSet HELD = new IntOpenHashSet();

	static void hold(int id, boolean posed) {
		if (broken || posed == HELD.contains(id)) {
			return;
		}
		Entity entity = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(id);
		if (entity == null) {
			HELD.remove(id);
			return;
		}
		try {
			if (!posed) {
				EMFAnimationApi.resumeAllCustomAnimationsForEntity((EMFEntity)entity);
				HELD.remove(id);
			} else if (!EMFAnimationPauseHandler.entitiesPaused.contains(entity.getUUID())) {
				// a pause someone else put on is theirs to lift
				EMFAnimationApi.pauseAllCustomAnimationsForEntity((EMFEntity)entity);
				HELD.add(id);
			}
		} catch (Throwable e) {
			broken = true;
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't pause Entity Model Features for a posed player; Touch'n Motion may lift the pause", e);
		}
	}

	static void registerPause() {
		try {
			// EMF asks this right after the model's own animation (LivingEntityRenderer.submit), and again as each model is
			// finally drawn, after the renderer has moved on: then only the entity tells us whether it's posed. In first
			// person (EMF's isInHand, the whole hands pass) a TaCZ gun's arms are left to the gun (TaczFirstPerson).
			EMFAnimationApi.registerPauseCondition(entity -> PoseTakeover.drawingPosed() || PoseTakeover.posedEntity(entity)
				|| EMFState.isInHand && TaczFirstPerson.holdingGun(entity));
		} catch (Exception e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't hook into Entity Model Features; its player animations may fight our poses", e);
		}
	}
}
