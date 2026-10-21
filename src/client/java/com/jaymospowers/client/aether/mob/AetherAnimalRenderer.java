package com.jaymospowers.client.aether.mob;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.mob.Aerbunny;
import com.jaymospowers.aether.mob.Aerwhale;
import com.jaymospowers.aether.mob.Burrukai;
import com.jaymospowers.aether.mob.Insect;
import com.jaymospowers.aether.mob.Kirrid;
import com.jaymospowers.aether.mob.Moa;
import com.jaymospowers.aether.mob.Prismallard;
import com.jaymospowers.aether.mob.RideableAnimal;
import com.jaymospowers.aether.mob.Sheepuff;
import com.jaymospowers.aether.mob.SkyrootLizard;
import com.jaymospowers.aether.mob.Taegore;
import com.jaymospowers.aether.mob.Wool;
import com.jaymospowers.aether.mob.Zone;
import com.jaymospowers.aether.mob.ZoneAnimal;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/**
 * Draws the Aether's animals: one renderer, many looks. What differs per animal (which skin, what extra state
 * it shows) is the {@link Look}; the model does the posing from the {@link State}. Chicks and young are drawn at half
 * size; an aerbunny swells while puffed.
 */
final class AetherAnimalRenderer<T extends Mob> extends MobRenderer<T, AetherAnimalRenderer.State, AetherAnimalModel> {
	/** Half size for the young. */
	private static final float BABY_SCALE = 0.5F;
	/** How much an aerbunny swells when puffed (0 to 1). */
	private static final float PUFF_SWELL = 0.4F;

	static final class State extends LivingEntityRenderState {
		/** Moa kind, sheepuff or kirrid colour, lizard leaf kind or insect kind. */
		int variant;
		/** The zone (highfields, magnetic, arctic) of a kirrid, burrukai or taegore. */
		int zone;
		boolean saddled, puffed, sheared;
		/** A kirrid's head plate; a burrukai winding up or angry; a taegore digging; an insect at rest. */
		boolean plated, windup, angry, digging, resting;
		/** How much bigger than the model it is drawn (a whale is drawn smaller than its model). */
		float size = 1.0F;
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
		state.size = 1.0F;
		if (mob instanceof RideableAnimal rideable) {
			state.saddled = rideable.isSaddled();
			state.flap = Mth.lerp(partialTicks, rideable.oFlap, rideable.flap);
			state.flapSpeed = Mth.lerp(partialTicks, rideable.oFlapSpeed, rideable.flapSpeed);
		}
		this.look.extract(mob, state, partialTicks);
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		float scale = (state.isBaby ? BABY_SCALE : 1.0F) * (1.0F + state.puff * PUFF_SWELL) * state.size;
		poseStack.scale(scale, scale, scale);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return this.look.skin(state);
	}

	// ---- the looks ----

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

	/** Skin names: {@code kirrid_<zone>_<wool>} (the wool is a dye name, or {@code undyed}). */
	static Look<Kirrid> kirrid() {
		Identifier[][] skins = new Identifier[Zone.values().length][Wool.NAMES.length + 1];
		for (Zone zone : Zone.values()) {
			for (int i = 0; i <= Wool.NAMES.length; i++) {
				skins[zone.ordinal()][i] = skin("kirrid_" + zone.id + "_" + (i == Wool.NAMES.length ? "undyed" : Wool.NAMES[i]));
			}
		}
		return new Look<>() {
			@Override
			public void extract(Kirrid mob, State state, float partialTicks) {
				state.zone = mob.zone().ordinal();
				state.variant = mob.color();
				state.sheared = mob.isSheared();
				state.plated = mob.hasPlate();
				state.eating = mob.isGrazing() ? 1.0F : 0.0F;
			}

			@Override
			public Identifier skin(State state) {
				return skins[Math.floorMod(state.zone, skins.length)][Math.floorMod(state.variant, Wool.NAMES.length + 1)];
			}
		};
	}

	/** One skin a zone: {@code <name>_<zone>}. */
	private static <T extends ZoneAnimal> Look<T> zoned(String name) {
		Identifier[] skins = new Identifier[Zone.values().length];
		for (Zone zone : Zone.values()) {
			skins[zone.ordinal()] = skin(name + "_" + zone.id);
		}
		return new Look<>() {
			@Override
			public void extract(T mob, State state, float partialTicks) {
				state.zone = mob.zone().ordinal();
				if (mob instanceof Burrukai burrukai) {
					state.windup = burrukai.isWindingUp();
					state.angry = burrukai.isAngry();
				}
				if (mob instanceof Taegore taegore) {
					state.digging = taegore.isDigging();
				}
			}

			@Override
			public Identifier skin(State state) {
				return skins[Math.floorMod(state.zone, skins.length)];
			}
		};
	}

	static Look<Burrukai> burrukai() {
		return zoned("burrukai");
	}

	static Look<Taegore> taegore() {
		return zoned("taegore");
	}

	static Look<SkyrootLizard> lizard() {
		Identifier[] skins = new Identifier[SkyrootLizard.VARIANTS.length];
		for (int i = 0; i < skins.length; i++) {
			skins[i] = skin("skyroot_lizard_" + SkyrootLizard.VARIANTS[i]);
		}
		return new Look<>() {
			@Override
			public void extract(SkyrootLizard mob, State state, float partialTicks) {
				state.variant = mob.variant();
			}

			@Override
			public Identifier skin(State state) {
				return skins[Math.floorMod(state.variant, skins.length)];
			}
		};
	}

	static Look<Prismallard> prismallard() {
		Identifier skin = skin("prismallard");
		return new Look<>() {
			@Override
			public void extract(Prismallard mob, State state, float partialTicks) {
				// it beats its wings in the air, and when it runs
				state.flap = mob.tickCount * 1.1F + partialTicks;
				state.flapSpeed = mob.onGround() ? Math.min(1.0F, state.walkAnimationSpeed * 0.8F) : 1.0F;
			}

			@Override
			public Identifier skin(State state) {
				return skin;
			}
		};
	}

	/** A glitterwing's or shroudwing's kind decides the skin. */
	static Look<Insect> insect(boolean glitter) {
		String[] kinds = glitter ? Insect.GLITTERWING : Insect.SHROUDWING;
		Identifier[] skins = new Identifier[kinds.length];
		for (int i = 0; i < skins.length; i++) {
			skins[i] = skin((glitter ? "glitterwing_" : "shroudwing_") + kinds[i]);
		}
		return new Look<>() {
			@Override
			public void extract(Insect mob, State state, float partialTicks) {
				state.variant = mob.variant();
				state.resting = mob.isResting();
			}

			@Override
			public Identifier skin(State state) {
				return skins[Math.floorMod(state.variant, skins.length)];
			}
		};
	}

	/** The whale is drawn at this share of its model, so the 3-block body fits the model's huge head. */
	private static final float WHALE_SIZE = 0.8F;

	static Look<Aerwhale> aerwhale() {
		Identifier skin = skin("aerwhale");
		return new Look<>() {
			@Override
			public void extract(Aerwhale mob, State state, float partialTicks) {
				state.size = WHALE_SIZE;
			}

			@Override
			public Identifier skin(State state) {
				return skin;
			}
		};
	}
}
