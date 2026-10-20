package com.jaymospowers.client.aether.mob;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.mob.Aerbunny;
import com.jaymospowers.aether.mob.Moa;
import com.jaymospowers.aether.mob.RideableAnimal;
import com.jaymospowers.aether.mob.Sheepuff;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/**
 * Draws the Aether's passive animals: one renderer, five looks. What differs per animal (which skin, what extra state
 * it shows) is the {@link Look}; the model does the posing from the {@link State}. Chicks and young are drawn at half
 * size; an aerbunny swells while puffed.
 */
final class AetherAnimalRenderer<T extends Mob> extends MobRenderer<T, AetherAnimalRenderer.State, AetherAnimalModel> {
	/** Half size for the young. */
	private static final float BABY_SCALE = 0.5F;
	/** How much an aerbunny swells when puffed (0 to 1). */
	private static final float PUFF_SWELL = 0.4F;

	static final class State extends LivingEntityRenderState {
		/** Moa kind or sheepuff colour. */
		int variant;
		boolean saddled, puffed, sheared;
		/** Wing beat: the phase, and how hard (0 on the ground to 1 in the air). */
		float flap, flapSpeed;
		/** Grazing, 0 to 1. */
		float eating;
		/** An aerbunny's swell, 0 to 1, and 1 when it is off the ground. */
		float puff, air;
	}

	/** What one animal adds to the state, and which skin it wears. */
	interface Look<T extends Mob> {
		void extract(T mob, State state, float partialTicks);

		Identifier skin(State state);
	}

	private final Look<T> look;

	AetherAnimalRenderer(EntityRendererProvider.Context context, AetherAnimalModel model, float shadow, Look<T> look) {
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
		state.air = mob.onGround() ? 0.0F : 1.0F;
		if (mob instanceof RideableAnimal rideable) {
			state.saddled = rideable.isSaddled();
			state.flap = Mth.lerp(partialTicks, rideable.oFlap, rideable.flap);
			state.flapSpeed = Mth.lerp(partialTicks, rideable.oFlapSpeed, rideable.flapSpeed);
		}
		this.look.extract(mob, state, partialTicks);
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		float scale = (state.isBaby ? BABY_SCALE : 1.0F) * (1.0F + state.puff * PUFF_SWELL);
		poseStack.scale(scale, scale, scale);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return this.look.skin(state);
	}

	// ---- the five looks ----

	private static Identifier skin(String name) {
		return JaymosPowers.id("textures/entity/aether/" + name + ".png");
	}

	static Look<Moa> moa() {
		Identifier[] skins = new Identifier[Moa.Kind.values().length];
		for (Moa.Kind kind : Moa.Kind.values()) {
			skins[kind.ordinal()] = skin("moa_" + kind.id);
		}
		return new Look<>() {
			@Override
			public void extract(Moa moa, State state, float partialTicks) {
				state.variant = moa.kind().ordinal();
			}

			@Override
			public Identifier skin(State state) {
				return skins[Math.floorMod(state.variant, skins.length)];
			}
		};
	}

	static Look<com.jaymospowers.aether.mob.Phyg> phyg() {
		Identifier skin = skin("phyg");
		return new Look<>() {
			@Override
			public void extract(com.jaymospowers.aether.mob.Phyg mob, State state, float partialTicks) {
			}

			@Override
			public Identifier skin(State state) {
				return skin;
			}
		};
	}

	static Look<com.jaymospowers.aether.mob.FlyingCow> cow() {
		Identifier skin = skin("flying_cow");
		return new Look<>() {
			@Override
			public void extract(com.jaymospowers.aether.mob.FlyingCow mob, State state, float partialTicks) {
			}

			@Override
			public Identifier skin(State state) {
				return skin;
			}
		};
	}

	static Look<Sheepuff> sheepuff() {
		Identifier[] skins = new Identifier[Sheepuff.COLORS.length];
		for (int i = 0; i < skins.length; i++) {
			skins[i] = skin("sheepuff_" + Sheepuff.COLORS[i]);
		}
		return new Look<>() {
			@Override
			public void extract(Sheepuff mob, State state, float partialTicks) {
				state.variant = mob.color();
				state.puffed = mob.isPuffed();
				state.sheared = mob.isSheared();
				state.eating = mob.isEating() ? 1.0F : 0.0F;
			}

			@Override
			public Identifier skin(State state) {
				return skins[Math.floorMod(state.variant, skins.length)];
			}
		};
	}

	static Look<Aerbunny> aerbunny() {
		Identifier skin = skin("aerbunny");
		return new Look<>() {
			@Override
			public void extract(Aerbunny mob, State state, float partialTicks) {
				state.puff = Mth.clamp(mob.puff() / 6.0F, 0.0F, 1.0F);
			}

			@Override
			public Identifier skin(State state) {
				return skin;
			}
		};
	}
}
