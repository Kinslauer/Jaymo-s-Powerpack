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
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Draws the Aether's animals: one renderer, many looks. What differs per animal (which skin, what extra state
 * it shows) is the {@link Look}; the model does the posing from the {@link State}. The young of the moa, kirrid,
 * burrukai and taegore have a model of their own; the other young are drawn at half size. An aerbunny swells while puffed.
 */
final class AetherAnimalRenderer<T extends Mob> extends MobRenderer<T, AetherAnimalRenderer.State, AetherAnimalModel> {
	/** Half size for the young that have no model of their own. */
	private static final float BABY_SCALE = 0.5F;
	/** How much an aerbunny swells when puffed (0 to 1). */
	private static final float PUFF_SWELL = 0.4F;

	static final class State extends LivingEntityRenderState {
		/** Moa kind, sheepuff or kirrid colour, lizard leaf kind or insect kind. */
		int variant;
		/** The zone (highfields, magnetic, arctic) of a kirrid, burrukai or taegore. */
		int zone;
		boolean saddled, puffed, sheared;
		/** Tamed (an aerbunny's collar); carrying a saddlebag (a moa). */
		boolean tame, bagged;
		/** Which of the beak and leg colours this moa has (its own, kept for life). */
		int keratin;
		/** A kirrid's head plate; a burrukai winding up or angry; a taegore digging; an insect at rest. */
		boolean plated, windup, angry, digging, resting;
		/** How much bigger than the model it is drawn (the whale far bigger), and how far (blocks) it is drawn lowered. */
		float size = 1.0F, lower;
		/** Wing beat: the phase, and how hard (0 on the ground to 1 in the air). */
		float flap, flapSpeed;
		/** Grazing, 0 to 1. */
		float eating;
		/** An aerbunny's swell, 0 to 1, and 1 when it is off the ground. */
		float puff, air;
		/** What it is doing (0 nothing, 1 winding up, 2 digging, 3 resting, 4 in the air) and the ticks since that changed. */
		int action;
		float actionTime;
		/** A cloud skiff's rudder, leaning into its turn (radians). */
		float steer;
		/** Drawn with its own baby model (so not shrunk). */
		boolean babyModel;
	}

	/** What one animal adds to the state, and which skin it wears. */
	interface Look<T extends Mob> {
		void extract(T mob, State state, float partialTicks);

		Identifier skin(State state);

		/** A second skin drawn over the first where it is not clear (a collar, a moa's own beak and leg colour), or null. */
		default @Nullable Identifier overlay(State state) {
			return null;
		}

		/** A skin drawn over the first that glows in the dark (an insect's lit marks), or null. */
		default @Nullable Identifier glow(State state) {
			return null;
		}

		/** The skin of its own baby model (see {@link AetherAnimalRenderer#babies}). */
		default Identifier babySkin(State state) {
			return this.skin(state);
		}
	}

	private final Look<T> look;
	/** One model per zone (highfields, magnetic, arctic) for the animals whose shape differs by zone, or null. */
	private final AetherAnimalModel @Nullable [] zoneModels;
	/** The grown model, and the young's own models (one, or one per zone), or null where the young are a small adult. */
	private final AetherAnimalModel adult;
	private AetherAnimalModel @Nullable [] babyModels;
	/** Per mob (by id): the action last seen and the tick it began, for {@link State#actionTime}. */
	private final Map<Integer, float[]> actions = new HashMap<>();
	/** The map is emptied past this many mobs; a mob first seen mid-action counts as long into it. */
	private static final int ACTIONS_KEPT = 512;
	private static final float LONG_AGO = 10000.0F;

	AetherAnimalRenderer(EntityRendererProvider.Context context, AetherAnimalModel model, float shadow, Look<T> look) {
		super(context, model, shadow);
		this.look = look;
		this.zoneModels = null;
		this.adult = model;
		this.addLayers();
	}

	/** For an animal with a different shape in each zone: {@code zoneModels} in {@link Zone} order. */
	AetherAnimalRenderer(EntityRendererProvider.Context context, AetherAnimalModel[] zoneModels, float shadow, Look<T> look) {
		super(context, zoneModels[0], shadow);
		this.look = look;
		this.zoneModels = zoneModels;
		this.adult = zoneModels[0];
		this.addLayers();
	}

	/** The look's overlay and glow, drawn over whichever model is in use (it was posed for the first pass). */
	private void addLayers() {
		this.addLayer(new RenderLayer<>(this) {
			@Override
			public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
				if (state.isInvisible) {
					return;
				}
				Identifier overlay = AetherAnimalRenderer.this.look.overlay(state);
				if (overlay != null) {
					collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.entityCutout(overlay), lightCoords,
						LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
				}
				Identifier glow = AetherAnimalRenderer.this.look.glow(state);
				if (glow != null) {
					collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(glow), lightCoords,
						OverlayTexture.NO_OVERLAY, state.outlineColor);
				}
			}
		});
	}

	/** Gives the young their own shape: one model, or one per zone in {@link Zone} order. */
	AetherAnimalRenderer<T> babies(AetherAnimalModel... babyModels) {
		this.babyModels = babyModels;
		return this;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.babyModel && this.babyModels != null) {
			this.model = this.babyModels[Math.floorMod(state.zone, this.babyModels.length)];
		} else if (this.zoneModels != null) {
			this.model = this.zoneModels[Math.floorMod(state.zone, this.zoneModels.length)];
		} else {
			this.model = this.adult;
		}
		super.submit(state, poseStack, collector, camera);
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
		state.lower = 0.0F;
		state.babyModel = this.babyModels != null && mob.isBaby();
		if (mob instanceof RideableAnimal rideable) {
			state.saddled = rideable.isSaddled();
			state.flap = Mth.lerp(partialTicks, rideable.oFlap, rideable.flap);
			state.flapSpeed = Mth.lerp(partialTicks, rideable.oFlapSpeed, rideable.flapSpeed);
		}
		this.look.extract(mob, state, partialTicks);
		state.action = state.windup ? 1 : state.digging ? 2 : state.resting ? 3 : state.air > 0.0F ? 4 : 0;
		float now = mob.tickCount + partialTicks;
		if (this.actions.size() > ACTIONS_KEPT) {
			this.actions.clear();
		}
		float[] since = this.actions.computeIfAbsent(mob.getId(), id -> new float[] {state.action, now - LONG_AGO});
		if (since[0] != state.action) {
			since[0] = state.action;
			since[1] = now;
		}
		state.actionTime = now - since[1];
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		float scale = (state.isBaby && !state.babyModel ? BABY_SCALE : 1.0F) * (1.0F + state.puff * PUFF_SWELL) * state.size;
		poseStack.scale(scale, scale, scale);
		// y points down here, and is already scaled
		poseStack.translate(0.0F, state.lower / scale, 0.0F);
	}

	/** The whale's head and fluke reach far past its body's box. */
	@Override
	protected AABB getBoundingBoxForCulling(T mob, float partialTicks) {
		AABB box = super.getBoundingBoxForCulling(mob, partialTicks);
		return mob instanceof Aerwhale ? box.inflate(WHALE_REACH, WHALE_REACH * 0.5, WHALE_REACH) : box;
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.babyModel ? this.look.babySkin(state) : this.look.skin(state);
	}

	// ---- the looks ----

	private static Identifier skin(String name) {
		return JaymosPowers.id("textures/entity/aether/" + name + ".png");
	}

	/** How many beak and leg colours a moa may have besides its kind's own (skins {@code moa_keratin_<n>}). */
	private static final int MOA_KERATINS = 4;

	static Look<Moa> moa() {
		Identifier[] skins = new Identifier[Moa.Kind.values().length];
		Identifier[] chicks = new Identifier[skins.length];
		Identifier[] keratin = new Identifier[MOA_KERATINS], chickKeratin = new Identifier[MOA_KERATINS];
		for (int i = 0; i < MOA_KERATINS; i++) {
			keratin[i] = skin("moa_keratin_" + i);
			chickKeratin[i] = skin("moa_baby_keratin_" + i);
		}
		for (Moa.Kind kind : Moa.Kind.values()) {
			skins[kind.ordinal()] = skin("moa_" + kind.id);
			chicks[kind.ordinal()] = skin("moa_" + kind.id + "_baby");
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

			@Override
			public Identifier babySkin(State state) {
				return chicks[Math.floorMod(state.variant, chicks.length)];
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
		Identifier collar = skin("aerbunny_collar");
		return new Look<>() {
			@Override
			public void extract(Aerbunny mob, State state, float partialTicks) {
				state.puff = Mth.clamp(mob.puff() / 6.0F, 0.0F, 1.0F);
				state.tame = mob.isTame();
			}

			@Override
			public @Nullable Identifier overlay(State state) {
				return state.tame ? collar : null;
			}

			@Override
			public Identifier skin(State state) {
				return skin;
			}
		};
	}

	/** Skin names: {@code kirrid_<zone>_<wool>} (the wool is a dye name, or {@code undyed}), and {@code ..._baby} for a kid. */
	static Look<Kirrid> kirrid() {
		Identifier[][] skins = new Identifier[Zone.values().length][Wool.NAMES.length + 1];
		Identifier[][] kids = new Identifier[Zone.values().length][Wool.NAMES.length + 1];
		for (Zone zone : Zone.values()) {
			for (int i = 0; i <= Wool.NAMES.length; i++) {
				String name = "kirrid_" + zone.id + "_" + (i == Wool.NAMES.length ? "undyed" : Wool.NAMES[i]);
				skins[zone.ordinal()][i] = skin(name);
				kids[zone.ordinal()][i] = skin(name + "_baby");
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

			@Override
			public Identifier babySkin(State state) {
				return kids[Math.floorMod(state.zone, kids.length)][Math.floorMod(state.variant, Wool.NAMES.length + 1)];
			}
		};
	}

	/** One skin a zone: {@code <name>_<zone>}, and {@code <name>_<zone>_baby} for its young. */
	private static <T extends ZoneAnimal> Look<T> zoned(String name) {
		Identifier[] skins = new Identifier[Zone.values().length];
		Identifier[] young = new Identifier[skins.length];
		for (Zone zone : Zone.values()) {
			skins[zone.ordinal()] = skin(name + "_" + zone.id);
			young[zone.ordinal()] = skin(name + "_" + zone.id + "_baby");
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

			@Override
			public Identifier babySkin(State state) {
				return young[Math.floorMod(state.zone, young.length)];
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
	/** The insects' models are drawn this much smaller, and the glitterwing's flat model lifted a little (blocks, down is positive). */
	private static final float INSECT_SIZE = 0.725F, GLITTERWING_LOWER = -0.125F;

	static Look<Insect> insect(boolean glitter) {
		String[] kinds = glitter ? Insect.GLITTERWING : Insect.SHROUDWING;
		Identifier[] skins = new Identifier[kinds.length];
		Identifier[] glows = new Identifier[kinds.length];
		for (int i = 0; i < skins.length; i++) {
			skins[i] = skin((glitter ? "glitterwing_" : "shroudwing_") + kinds[i]);
			glows[i] = skin((glitter ? "glitterwing_" : "shroudwing_") + kinds[i] + "_glow");
		}
		return new Look<>() {
			@Override
			public void extract(Insect mob, State state, float partialTicks) {
				state.variant = mob.variant();
				state.resting = mob.isResting();
				state.size = INSECT_SIZE;
				state.lower = glitter ? GLITTERWING_LOWER : 0.0F;
			}

			@Override
			public Identifier glow(State state) {
				return glows[Math.floorMod(state.variant, glows.length)];
			}

			@Override
			public Identifier skin(State state) {
				return skins[Math.floorMod(state.variant, skins.length)];
			}
		};
	}

	/** A skiff's rudder leans this many radians for each degree it turned this tick, up to the limit. */
	private static final float RUDDER_LEAN = 0.08F, RUDDER_MAX = 0.785F;

	static Look<com.jaymospowers.aether.mob.CloudSkiff> cloudSkiff() {
		Identifier skin = skin("cloud_skiff");
		return new Look<>() {
			@Override
			public void extract(com.jaymospowers.aether.mob.CloudSkiff mob, State state, float partialTicks) {
				state.steer = Mth.clamp(Mth.wrapDegrees(mob.yBodyRot - mob.yBodyRotO) * RUDDER_LEAN, -RUDDER_MAX, RUDDER_MAX);
			}

			@Override
			public Identifier skin(State state) {
				return skin;
			}
		};
	}

	/**
	 * The whale is drawn this many times its model: about fifteen blocks nose to tail, a little longer than Kingdoms' great
	 * whale (about fourteen). Lowered so its head and jaw sit in its body's box, and seen this far (blocks) past that box.
	 */
	private static final float WHALE_SIZE = 2.7F, WHALE_LOWER = 0.0F, WHALE_REACH = 9.0F;

	static Look<Aerwhale> aerwhale() {
		Identifier skin = skin("aerwhale");
		return new Look<>() {
			@Override
			public void extract(Aerwhale mob, State state, float partialTicks) {
				state.size = WHALE_SIZE;
				state.lower = WHALE_LOWER;
			}

			@Override
			public Identifier skin(State state) {
				return skin;
			}
		};
	}
}
