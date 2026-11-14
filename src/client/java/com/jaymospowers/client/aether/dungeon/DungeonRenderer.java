package com.jaymospowers.client.aether.dungeon;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.dungeon.DetonationSentry;
import com.jaymospowers.aether.dungeon.Mimic;
import com.jaymospowers.aether.dungeon.SentryGolem;
import com.jaymospowers.aether.dungeon.Slider;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/** Draws the Sentry Ruins' mobs: one renderer, four looks (which skin, and what the model is told). */
final class DungeonRenderer<T extends Mob> extends MobRenderer<T, DungeonRenderer.State, DungeonModel> {
	static final class State extends LivingEntityRenderState {
		/** Awake (the slider, the sentry). */
		boolean awake;
		/** 0 to 1: a lit fuse's burn, a throw's windup, a mimic's unfolding. */
		float progress;
		/** The swing of a blow or bite, 0 to 1. */
		float attack;
		/** Standing as carved (a warden that has not woken). */
		boolean still;
		/** Ticks since {@link #progress} last fell back to nothing (a golem's throw follows its windup). */
		float actionTime;
		/** A golem left alone fidgets: 0 not now, 1 looking around, 2 checking itself; and the ticks into it. */
		int fidget;
		float fidgetTime;
	}

	/** What one mob adds to the state. */
	interface Look<T extends Mob> {
		void extract(T mob, State state, float partialTicks);

		/** The skin's name (a png under textures/entity/aether/). */
		String skin(State state);

		/** The name of a skin drawn over the first that glows in the dark (lit eyes, a core), or null. */
		default @Nullable String glow(State state) {
			return null;
		}

		/** A cube does not tip over when it dies (it bursts instead). */
		default boolean lies() {
			return true;
		}
	}

	private final Look<T> look;
	/** Per mob (by id): whether it was mid-progress when last seen and the tick that ended, for {@link State#actionTime}. */
	private final Map<Integer, float[]> actions = new HashMap<>();
	/** The map is emptied past this many mobs; a mob first seen counts as long past it. */
	private static final int ACTIONS_KEPT = 512;
	private static final float LONG_AGO = 10000.0F;

	DungeonRenderer(EntityRendererProvider.Context context, DungeonModel model, float shadow, Look<T> look) {
		super(context, model, shadow);
		this.look = look;
		this.addLayer(new RenderLayer<>(this) {
			@Override
			public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
				String glow = state.isInvisible ? null : look.glow(state);
				if (glow != null) {
					collector.order(1).submitModel(this.getParentModel(), state, poseStack,
						RenderTypes.eyes(JaymosPowers.id("textures/entity/aether/" + glow + ".png")), lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
				}
			}
		});
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
		float now = mob.tickCount + partialTicks;
		if (this.actions.size() > ACTIONS_KEPT) {
			this.actions.clear();
		}
		float busy = state.progress > 0.0F ? 1.0F : 0.0F;
		float[] since = this.actions.computeIfAbsent(mob.getId(), id -> new float[] {busy, now - LONG_AGO});
		if (since[0] != busy) {
			since[0] = busy;
			since[1] = now;
		}
		state.actionTime = now - since[1];
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

	/** A standing golem looks around or checks itself for {@link #FIDGET_TICKS}, once every this many ticks (plus up to the spread). */
	private static final int FIDGET_EVERY = 300, FIDGET_SPREAD = 200, FIDGET_TICKS = 120;

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

	static Look<com.jaymospowers.aether.sky.SkyWarden> skyWarden() {
		return new Look<>() {
			@Override
			public void extract(com.jaymospowers.aether.sky.SkyWarden mob, State state, float partialTicks) {
				state.awake = mob.awake();
				state.still = !state.awake;
			}

			@Override
			public String skin(State state) {
				return "sky_warden";
			}
		};
	}
}
