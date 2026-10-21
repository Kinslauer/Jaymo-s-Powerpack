package com.jaymospowers.client.aether.dungeon;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.dungeon.DetonationSentry;
import com.jaymospowers.aether.dungeon.Mimic;
import com.jaymospowers.aether.dungeon.SentryGolem;
import com.jaymospowers.aether.dungeon.Slider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/** Draws the Sentry Ruins' mobs: one renderer, four looks (which skin, and what the model is told). */
final class DungeonRenderer<T extends Mob> extends MobRenderer<T, DungeonRenderer.State, DungeonModel> {
	static final class State extends LivingEntityRenderState {
		/** Awake (the slider, the sentry). */
		boolean awake;
		/** 0 to 1: a lit fuse's burn, a throw's windup, a mimic's unfolding. */
		float progress;
		/** The swing of a blow or bite, 0 to 1. */
		float attack;
	}

	/** What one mob adds to the state. */
	interface Look<T extends Mob> {
		void extract(T mob, State state, float partialTicks);

		/** The skin's name (a png under textures/entity/aether/). */
		String skin(State state);

		/** A cube does not tip over when it dies (it bursts instead). */
		default boolean lies() {
			return true;
		}
	}

	private final Look<T> look;

	DungeonRenderer(EntityRendererProvider.Context context, DungeonModel model, float shadow, Look<T> look) {
		super(context, model, shadow);
		this.look = look;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T mob, State state, float partialTicks) {
		super.extractRenderState(mob, state, partialTicks);
		state.attack = mob.getSwingAnimation(partialTicks);
		this.look.extract(mob, state, partialTicks);
	}

	@Override
	protected float getFlipDegrees() {
		return this.look.lies() ? 90.0F : 0.0F;
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return JaymosPowers.id("textures/entity/aether/" + this.look.skin(state) + ".png");
	}

	// ---- the looks ----

	static Look<Slider> slider() {
		return new Look<>() {
			@Override
			public void extract(Slider mob, State state, float partialTicks) {
				state.awake = mob.awake();
			}

			@Override
			public String skin(State state) {
				return state.awake ? "slider_awake" : "slider";
			}

			@Override
			public boolean lies() {
				return false;
			}
		};
	}

	static Look<DetonationSentry> detonationSentry() {
		return new Look<>() {
			@Override
			public void extract(DetonationSentry mob, State state, float partialTicks) {
				state.awake = mob.awake();
				state.progress = mob.fuse() < 0 ? 0.0F : Mth.clamp((mob.fuse() + partialTicks) / DetonationSentry.FUSE_TICKS, 0.05F, 1.0F);
			}

			@Override
			public String skin(State state) {
				// the eyes flash red every few ticks once the fuse is lit
				if (state.progress > 0.0F && (int)(state.ageInTicks / 3.0F) % 2 == 0) {
					return "detonation_sentry_lit";
				}
				return state.awake ? "detonation_sentry_awake" : "detonation_sentry";
			}
		};
	}

	static Look<SentryGolem> sentryGolem() {
		return new Look<>() {
			@Override
			public void extract(SentryGolem mob, State state, float partialTicks) {
				state.progress = mob.wind() <= 0 ? 0.0F : Mth.clamp((mob.wind() + partialTicks) / SentryGolem.WINDUP, 0.0F, 1.0F);
			}

			@Override
			public String skin(State state) {
				return "sentry_golem";
			}
		};
	}

	static Look<Mimic> mimic() {
		return new Look<>() {
			@Override
			public void extract(Mimic mob, State state, float partialTicks) {
				state.progress = Mth.clamp((mob.unfold() + partialTicks) / Mimic.SPAWN_TICKS, 0.0F, 1.0F);
			}

			@Override
			public String skin(State state) {
				return "mimic";
			}
		};
	}
}
