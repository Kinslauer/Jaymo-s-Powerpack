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

	/** The goblin scout stands a little over a block tall (as the village's goblins do); the ghost is a person's height. */
	static final float SCOUT_SCALE = 1.05F, COLOSSUS_SCALE = 1.4F, BEETLE_SCALE = 0.8F, KNIGHT_SCALE = 0.9F, REVENANT_SCALE = 0.62F, GHOST_SCALE = 0.9375F;

	/** The sand worm stands about nine blocks out of the sand; the thing under the wreck is three across with arms of seven. */
	static final float WORM_SCALE = 1.6F, THING_SCALE = 2.0F;
	/** The worm's whole height with its jaws (model pixels), and the server's timings for coming up and going under (ticks). */
	static final float WORM_TALL = 125.0F, WORM_RISE_TICKS = 10.0F, WORM_SINK_TICKS = 20.0F;

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
		// the sand worm and the thing under the wreck: our own models and animations (kingdoms/tools/gen_lair_beasts.py)
		look(Beasts.SAND_WORM, new BbMobRenderer.Look("sand_worm", WORM_SCALE, 0.0F, "idle", "idle", null, "death", 1.0F, 1.0F, null)
			.withExtra(BeastsClient::wormDepth));
		// (its arms stand far over its body: drawn while any of them could be in view)
		EntityRenderers.register(Beasts.WRECK_THING, context -> new BbMobRenderer<com.jaymoskingdoms.beasts.WreckThing>(context,
			new BbMobRenderer.Look("wreck_thing", THING_SCALE, 0.0F, "idle", "swim", null, "death", 0.5F, 1.0F, null)) {
			@Override
			protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(com.jaymoskingdoms.beasts.WreckThing thing, float partialTicks) {
				return thing.getBoundingBox().inflate(5.0, 0.0, 5.0).expandTowards(0.0, 9.0, 0.0);
			}
		});
		look(Beasts.GORGON, new BbMobRenderer.Look("gorgon", 0.8F, 0.6F, "rest", "running", null, null, 0.45F, 1.0F, "head"));
		look(Beasts.WAR_BARREL, new BbMobRenderer.Look("war_barrel", 0.9F, 1.4F, "idle", "walk", null, null, 0.5F, 1.0F, null));
		look(Beasts.HAUNTED_STAND, new BbMobRenderer.Look("haunted_stand", 1.0F, 0.4F, "none", "none", null, null, 0.5F, 1.0F, "head"));
		// the scout's own four animations; the ghost only hangs and bobs, drawn see-through and lit from within
		look(Beasts.RUNE_COLOSSUS,
			new BbMobRenderer.Look("rune_bound_colossus", COLOSSUS_SCALE, 1.0F, "idle", "walk", "run", "death", 0.75F, 1.5F, "head"));
		look(Beasts.CRYSTAL_BEETLE, new BbMobRenderer.Look("crystal_beetle", BEETLE_SCALE, 0.4F, "idle", "walk", null, null, 0.9F, 1.0F, "head"));
		// the knight's sword is a real item in his fist: drawn on the model's empty sword bone, down its chain of bones
		EntityRenderers.register(Beasts.ZOMBIE_KNIGHT, context -> new BbMobRenderer<com.jaymoskingdoms.barrow.ZombieKnight>(context,
			new BbMobRenderer.Look("zombie_knight", KNIGHT_SCALE, 0.5F, "idle", "walk", null, null, 0.5F, 1.0F, "hi_head"))
			.holding("root", "waist", "body", "arm_right", "sword"));
		look(Beasts.REVENANT, new BbMobRenderer.Look("cryptbound_revenant", REVENANT_SCALE, 0.7F, "idle", "walk", null, "death", 0.55F, 1.0F, "helm"));
		look(Beasts.GOBLIN_SCOUT, new BbMobRenderer.Look("goblin_scout", SCOUT_SCALE, 0.3F, "idle", "walk", null, null, 0.8F, 1.0F, "head"));
		EntityRenderers.register(Beasts.GRAVE_GHOST, context -> new BbMobRenderer<com.jaymoskingdoms.GraveGhost>(context,
			new BbMobRenderer.Look("grave_ghost", GHOST_SCALE, 0.0F, "Idle", "none", null, null, 0.5F, 1.0F, "Head")) {
			@Override
			protected net.minecraft.client.renderer.rendertype.RenderType getRenderType(BbMobRenderer.State state, boolean bodyVisible, boolean translucent,
				boolean glowing) {
				return bodyVisible ? net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(this.getTextureLocation(state))
					: super.getRenderType(state, bodyVisible, translucent, glowing);
			}
		});
		look(Beasts.SCALEBANE, new BbMobRenderer.Look("scalebane_archer", 0.96F, 0.5F, "idle", "walking", "running", null, 0.6F, 0.7F, "head"));
	}

	private static <T extends net.minecraft.world.entity.Mob & com.jaymoskingdoms.beasts.Acting> void look(
		net.minecraft.world.entity.EntityType<T> type, BbMobRenderer.Look look) {
		EntityRenderers.register(type, context -> new BbMobRenderer<>(context, look));
	}

	/**
	 * How much of the sand worm is out of the ground: none while it burrows or the sand boils over it, pushed up through
	 * its rise, drawn back down through its sink (its animations only shape the body).
	 */
	private static void wormDepth(BbModel bb, BbMobRenderer.State state) {
		String action = state.action;
		float out = action == null || "warn".equals(action) ? 0.0F
			: "rise".equals(action) ? Mth.clamp(state.actionAge / WORM_RISE_TICKS, 0.0F, 1.0F)
			: "sink".equals(action) ? 1.0F - Mth.clamp(state.actionAge / WORM_SINK_TICKS, 0.0F, 1.0F) : 1.0F;
		bb.part("root").y += (1.0F - out) * WORM_TALL;
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
