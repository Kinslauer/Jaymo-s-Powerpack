package com.jaymoskingdoms.client.beasts;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.Beasts;
import com.jaymoskingdoms.client.bb.BbModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.util.Mth;

/**
 * (0.7.329) How the second batch of Blockbench Workshop creatures ({@link Beasts}) are drawn: each by its own model and
 * animations ({@link BbMobRenderer}), scaled to its box. The skulk golem has no idle: awake it stands in its rest pose,
 * and as a statue it holds its "estatue" move. The redstone golem's model has only an idle and a walk, so its pound
 * and eruption are posed here.
 */
public final class BeastsClient {
	/** The redstone golem's moves (as the server's RedstoneGolem): the tick its fists come down. */
	static final float POUND_HIT = 18.0F, ERUPT_HIT = 28.0F;
	/** Its arms raised overhead, and brought down (degrees). */
	static final float ARMS_UP = -160.0F, ARMS_DOWN = -35.0F;

	private BeastsClient() {
	}

	public static void register() {
		look(Beasts.MINOTAUR, new BbMobRenderer.Look("minotaur", 0.85F, 1.2F, "idle", "running", null, null, 0.35F, 1.0F, "head"));
		look(Beasts.PIGLIN_KING, new BbMobRenderer.Look("piglin_king", 0.96F, 1.3F, "idle", "walk", null, null, 0.5F, 1.0F, "head"));
		look(Beasts.REDSTONE_GOLEM, new BbMobRenderer.Look("redstone_golem", 0.94F, 1.5F, "redstone_golem_idle_animation_1",
			"redstone_golem_walk_animation_1", null, null, 0.4F, 1.0F, "redstone_golem_head")
			.withGlow(JaymosKingdoms.id("textures/entity/bb/redstone_golem_1.png")).withExtra(BeastsClient::golemArms));
		look(Beasts.SKULK_GOLEM, new BbMobRenderer.Look("skulk_golem", 1.0F, 0.9F, "none", "walk", null, null, 0.4F, 1.0F, "head"));
		look(Beasts.WILD_BOAR, new BbMobRenderer.Look("wild_boar", 0.9F, 0.6F, "idle", "walk", "run", "death", 0.4F, 0.6F, "h_head"));
		look(Beasts.SCALEBANE, new BbMobRenderer.Look("scalebane_archer", 0.96F, 0.5F, "idle", "walking", "running", null, 0.6F, 0.7F, "head"));
	}

	private static <T extends net.minecraft.world.entity.Mob & com.jaymoskingdoms.beasts.Acting> void look(
		net.minecraft.world.entity.EntityType<T> type, BbMobRenderer.Look look) {
		EntityRenderers.register(type, context -> new BbMobRenderer<>(context, look));
	}

	/** The redstone golem's pound (both fists up, then down on the ground) and eruption (the same, slower, held down). */
	private static void golemArms(BbModel bb, BbMobRenderer.State state) {
		if (state.action == null) {
			return;
		}
		float hit = "pound".equals(state.action) ? POUND_HIT : ERUPT_HIT;
		float t = state.actionAge;
		float angle;
		if (t < hit - 4.0F) {
			angle = ARMS_UP * Mth.clamp(t / 8.0F, 0.0F, 1.0F);
		} else if (t < hit) {
			angle = Mth.lerp((t - (hit - 4.0F)) / 4.0F, ARMS_UP, ARMS_DOWN);
		} else {
			angle = ARMS_DOWN * Mth.clamp(1.0F - (t - hit - 6.0F) / 6.0F, 0.0F, 1.0F);
		}
		for (String arm : new String[] {"redstone_golem_arm_left", "redstone_golem_arm_right"}) {
			ModelPart part = bb.find(arm);
			if (part != null) {
				part.xRot += angle * Mth.DEG_TO_RAD;
			}
		}
	}
}
