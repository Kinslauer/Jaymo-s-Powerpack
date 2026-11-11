package com.jaymoskingdoms.client.beasts;

import com.jaymoskingdoms.beasts.Beasts;
import com.jaymoskingdoms.beasts.Minotaur;
import com.jaymoskingdoms.beasts.SkulkGolem;
import com.jaymoskingdoms.client.bb.BbModel;
import com.jaymoskingdoms.client.inhabitants.InhabitantRenderer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.util.Mth;

/**
 * (0.7.329) How the second batch of Blockbench Workshop creatures ({@link Beasts}) are drawn: each by its own model and
 * animations ({@link BbMobRenderer}), scaled to its box. The minotaur, the skulk golem and the redstone golem are drawn
 * by GeckoLib.
 */
public final class BeastsClient {
	/** The Piglin King's stride: leg and arm swing at a full walk, the forearms' bend, and its idle breath (degrees). */
	static final float KING_LEG_SWING = 32.0F, KING_ARM_SWING = 24.0F, KING_FOREARM_BEND = -12.0F, KING_BREATH = 3.0F;

	private BeastsClient() {
	}

	public static void register() {
		EntityRenderers.register(Beasts.MINOTAUR,
			context -> new InhabitantRenderer<Minotaur>(context, "minotaur", 0.85F, 1.2F, "head"));
		// (0.7.403) Andy: "piglin arm is broken". The file's idle and walk hold a twisted fighting stance with the left arm
		// thrust out, pushed off its shoulder and missing what it was drawn to hold; it stands and walks in its rest pose
		// here instead, and its own moves (claw, volley, roar) still play as drawn
		look(Beasts.PIGLIN_KING, new BbMobRenderer.Look("piglin_king", 0.96F, 1.3F, "none", "none", null, null, 0.5F, 1.0F, "head")
			.withExtra(BeastsClient::kingStride));
		EntityRenderers.register(Beasts.REDSTONE_GOLEM, RedstoneGolemRenderer::new);
		// the skulk golem topples forward in its own death animation, so it doesn't also fall over sideways
		EntityRenderers.register(Beasts.SKULK_GOLEM, context -> new InhabitantRenderer<SkulkGolem>(context, "skulk_golem", 1.0F, 0.9F, "head") {
			@Override
			protected float getDeathMaxRotation(com.geckolib.renderer.base.GeoRenderState state) {
				return 0.0F;
			}
		});
		look(Beasts.WILD_BOAR, new BbMobRenderer.Look("wild_boar", 0.9F, 0.6F, "idle", "walk", "run", "death", 0.4F, 0.6F, "h_head"));
		// (0.7.339) the third batch: the haunted stand has no walk (it glides) and no idle (it stands dead still)
		look(Beasts.GORGON, new BbMobRenderer.Look("gorgon", 0.8F, 0.6F, "rest", "running", null, null, 0.45F, 1.0F, "head"));
		look(Beasts.WAR_BARREL, new BbMobRenderer.Look("war_barrel", 0.9F, 1.4F, "idle", "walk", null, null, 0.5F, 1.0F, null));
		look(Beasts.HAUNTED_STAND, new BbMobRenderer.Look("haunted_stand", 1.0F, 0.4F, "none", "none", null, null, 0.5F, 1.0F, "head"));
		look(Beasts.SCALEBANE, new BbMobRenderer.Look("scalebane_archer", 0.96F, 0.5F, "idle", "walking", "running", null, 0.6F, 0.7F, "head"));
	}

	private static <T extends net.minecraft.world.entity.Mob & com.jaymoskingdoms.beasts.Acting> void look(
		net.minecraft.world.entity.EntityType<T> type, BbMobRenderer.Look look) {
		EntityRenderers.register(type, context -> new BbMobRenderer<>(context, look));
	}

	/** The Piglin King's walk (legs and arms swinging opposite) and idle (a slow breath in the arms), when not mid-move. */
	private static void kingStride(BbModel bb, BbMobRenderer.State state) {
		if (state.action != null) {
			return;
		}
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * speed;
		float breath = Mth.sin(state.ageInTicks * 0.06F) * KING_BREATH;
		bb.part("left_leg").xRot += swing * KING_LEG_SWING * Mth.DEG_TO_RAD;
		bb.part("right_leg").xRot -= swing * KING_LEG_SWING * Mth.DEG_TO_RAD;
		bb.part("left_arm").xRot -= swing * KING_ARM_SWING * Mth.DEG_TO_RAD;
		bb.part("right_arm").xRot += swing * KING_ARM_SWING * Mth.DEG_TO_RAD;
		bb.part("left_arm").zRot -= breath * Mth.DEG_TO_RAD;
		bb.part("right_arm").zRot += breath * Mth.DEG_TO_RAD;
		bb.part("left_forearm").xRot += KING_FOREARM_BEND * Mth.DEG_TO_RAD;
		bb.part("right_forearm").xRot += KING_FOREARM_BEND * Mth.DEG_TO_RAD;
	}
}
