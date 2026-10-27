package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BioBuilt;
import com.jaymoskingdoms.EarthlingSurge;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.NightbornBlood;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.79) Every heritage's own look, the way the Emberborn's ({@link EmberLook}) was done: marks on the hands and
 * forearms lit like skin, more drawn emissive over them so they glow in the dark, eyes that glow (found on each skin,
 * {@link VampireLooks#glowingEyes}), and now and then a particle off a hand. Andy picked them from mockups
 * (art-overhaul/lineages/); textures from {@code tools/gen_heritage_looks.py}. Heartlander and Warrior-born stay as
 * they are, and the Emberborn keep {@link EmberLook}.
 * <ul>
 * <li><b>Stoneborn:</b> stone over the hands, amethyst glowing in its cracks; violet eyes that glow by day only;
 * amethyst glints.</li>
 * <li><b>Tideborn:</b> (0.7.80: the glow, eyes and fin edges lit under water, or at night while still wet; drips only while
 * wet) fishmen: gills on the cheeks and the sides of the head, fins (model parts) on the head and the
 * forearms in one of three sets (by UUID: crest and cheek gills; ear fins; crest, ear fins and long arm fins), sea-green
 * scales and rows of deep-sea lights on the forearms; cyan eyes; bubbles under water, drips on land.</li>
 * <li><b>Sylvan:</b> (0.7.80: sneaking dims the glow and stops the fireflies; a Thief's, or one with a Stealth perk, goes
 * out) a thick glowing vine winding round each arm, its own way on each player (by UUID, four); pale
 * green eyes; fireflies.</li>
 * <li><b>Verdant:</b> only the pink forearm patches, glowing in the dark (the green skin and antennae are
 * {@link VerdantLook}'s).</li>
 * <li><b>Frostborn:</b> the Verdant's pink patches too, and rime on the fingers; ice-white eyes; snowflakes.</li>
 * <li><b>Nightborn:</b> shadow soaking the hands with stars in it; moon-silver eyes that glow by day only; dark wisps
 * at night only. (0.7.88) A Nightborn vampire's ({@link NightbornBlood#starlit}): the shadow deep red, the eyes red-silver
 * at night (moon-silver by day, as ever; its own red eyes give way to them), and blood mist for the wisps.</li>
 * <li><b>Earthling:</b> training wraps round the fists, and (0.7.184: a Z Fighter's only) a ki glow over them only while
 * near death has made it flare ({@code EarthlingSurge}); no eye glow, no particles.</li>
 * <li><b>Stormborn:</b> (0.7.106) forked lightning-scar lines up the forearms, pale blue, dim until a thunderstorm makes
 * them flicker bright; sparks then. Plain eyes.</li>
 * <li><b>Dawnborn:</b> (0.7.106) a gold sun-ring on the back of each hand; warm gold eyes that glow by day only.</li>
 * <li><b>Endborn:</b> (0.7.106) purple specks scattered up the forearms; purple eyes; portal motes drift off the hands at
 * night, out of the rain.</li>
 * <li><b>Dragonblood:</b> (0.7.106) dark scale patches on the forearms and one cheek, glowing ember-orange in the dark
 * only; amber eyes that glow at night only.</li>
 * <li><b>Deepborn:</b> (0.7.106) teal sculk veins up the forearms pulsing slowly; pale eyes; now and then a sculk pop in
 * dark places.</li>
 * <li><b>Divine-born:</b> (0.7.262, textures from {@code kingdoms/tools/gen_race_looks.py}) a faint gold glow in fine
 * filigree up the forearms and on the brow, warm white eyes, a halo (a flat gold ring, a model part) floating over the head,
 * and motes of light drifting off the skin.</li>
 * <li><b>Bio-built:</b> (0.7.262) steel-green seams and plating that grow with each form ({@link BioBuilt#form}: three drawings,
 * the second plated to the elbow, the third to the shoulder and the cheeks), amber nodes that glow; amber eyes. A variant's
 * own marks get the form's plates laid over them ({@code biobuilt_plates_*}).</li>
 * <li><b>Rubber-folk:</b> (0.7.262) glossy highlights on the forearms and knuckles over the pink skin (the pink is MCA's skin
 * tint, {@code HeritageVariants#skin}); no glow, no eyes, no particles.</li>
 * </ul>
 * Particles: not round your own first-person camera, not on the invisible or spectators.
 */
public final class HeritageLooks extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** How a heritage's eyes glow. (0.7.106) {@code NIGHT}: in the dark of night only. */
	enum Eyes { ALWAYS, DAY, NIGHT, STORM, NEVER }

	/** One heritage's look: its texture name, how many drawings to pick from (by UUID), eyes, and particle rate (0: none). */
	enum Kind {
		STONEBORN(Heritage.Kind.STONEBORN, "stoneborn", 1, 0xFFD6AAFF, Eyes.DAY, 24),
		TIDEBORN(Heritage.Kind.TIDEBORN, "tideborn", 3, 0xFF6EFAEB, Eyes.ALWAYS, 14),
		SYLVAN(Heritage.Kind.SYLVAN, "sylvan", 4, 0xFFD6F596, Eyes.ALWAYS, 30),
		VERDANT(Heritage.Kind.VERDANT, "verdant", 1, 0, Eyes.NEVER, 0),
		FROSTBORN(Heritage.Kind.FROSTBORN, "frostborn", 1, 0xFFC8ECFF, Eyes.ALWAYS, 16),
		NIGHTBORN(Heritage.Kind.NIGHTBORN, "nightborn", 1, 0xFFECEEFF, Eyes.DAY, 12),
		EARTHLING(Heritage.Kind.EARTHLING, "earthling", 1, 0, Eyes.NEVER, 0),
		STORMBORN(Heritage.Kind.STORMBORN, "stormborn", 1, 0xFFC4E4FF, Eyes.STORM, 20),
		DAWNBORN(Heritage.Kind.DAWNBORN, "dawnborn", 1, 0xFFFFD27A, Eyes.DAY, 0),
		ENDBORN(Heritage.Kind.ENDBORN, "endborn", 1, 0xFFD69CFF, Eyes.ALWAYS, 10),
		DRAGONBLOOD(Heritage.Kind.DRAGONBLOOD, "dragonblood", 1, 0xFFFFB040, Eyes.NIGHT, 0),
		DEEPBORN(Heritage.Kind.DEEPBORN, "deepborn", 1, 0xFFB8F4EC, Eyes.ALWAYS, 40),
		// (0.7.262) the Bio-built have three drawings: their forms
		DIVINE(Heritage.Kind.DIVINE, "divine", 1, 0xFFFFF4D0, Eyes.ALWAYS, 30),
		BIOBUILT(Heritage.Kind.BIOBUILT, "biobuilt", 3, 0xFFFFB454, Eyes.ALWAYS, 0),
		RUBBERFOLK(Heritage.Kind.RUBBERFOLK, "rubberfolk", 1, 0, Eyes.NEVER, 0);

		final Heritage.Kind heritage;
		final Identifier[] skin, glow;
		/** The eyes' colour (opaque ARGB), and the stock eyes texture. */
		final int eyeColor;
		final @Nullable Identifier stockEyes;
		final Eyes eyes;
		/** One particle in this many ticks (0: none). */
		final int particleEvery;
		final Map<Identifier, Identifier> eyesBySkin = new HashMap<>();

		Kind(Heritage.Kind heritage, String name, int drawings, int eyeColor, Eyes eyes, int particleEvery) {
			this.heritage = heritage;
			this.skin = new Identifier[drawings];
			this.glow = new Identifier[drawings];
			for (int i = 0; i < drawings; i++) {
				String file = drawings == 1 ? name : name + "_" + i;
				this.skin[i] = JaymosKingdoms.id("textures/entity/heritage/" + file + "_skin.png");
				this.glow[i] = JaymosKingdoms.id("textures/entity/heritage/" + file + "_glow.png");
			}
			this.eyeColor = eyeColor;
			this.stockEyes = eyes == Eyes.NEVER ? null : JaymosKingdoms.id("textures/entity/heritage/" + name + "_eyes.png");
			this.eyes = eyes;
			this.particleEvery = particleEvery;
		}

		static @Nullable Kind of(Heritage.@Nullable Kind heritage) {
			for (Kind kind : values()) {
				if (kind.heritage == heritage) {
					return kind;
				}
			}
			return null;
		}

		static @Nullable Kind of(Player player) {
			for (Kind kind : values()) {
				if (Heritage.is(player, kind.heritage)) {
					return kind;
				}
			}
			return null;
		}
	}

	/**
	 * On a player's render state: their heritage's look, which drawing, whether their eyes glow now, and the glow's tint
	 * (opaque ARGB, -1 full; 0 none: a dry Tideborn, a Sylvan who sneaks unseen). (0.7.214) {@code style}: their heritage
	 * variant's look ({@link VariantLooks}), drawn instead of the first's marks; then {@code kind} is null for a Heartlander,
	 * a Warrior-born or an Emberborn, who have no first look here.
	 */
	record Look(@Nullable Kind kind, int variant, boolean eyesLit, int glow, boolean blood, VariantLooks.@Nullable Style style) {
	}

	/** (0.7.88) A Nightborn vampire's look: its marks, its red-silver eyes at night, its blood mist. */
	private static final Identifier BLOOD_SKIN = JaymosKingdoms.id("textures/entity/heritage/nightborn_blood_skin.png");
	private static final Identifier BLOOD_GLOW = JaymosKingdoms.id("textures/entity/heritage/nightborn_blood_glow.png");
	private static final Identifier BLOOD_EYES = JaymosKingdoms.id("textures/entity/heritage/nightborn_blood_eyes.png");
	static final int BLOOD_EYE_COLOR = 0xFFFFB8C0;
	private static final Map<Identifier, Identifier> BLOOD_EYES_BY_SKIN = new HashMap<>();
	private static final DustParticleOptions BLOOD_MIST = new DustParticleOptions(0x7A0A12, 0.9F);

	/** (0.7.80) A sneaking Sylvan's glow, dimmed to this (opaque ARGB tint; out entirely for a Thief or with a Stealth perk). */
	static final int SYLVAN_SNEAK_GLOW = 0xFF4A4A4A;
	/**
	 * (0.7.106) A Stormborn's scars: dim like this out of a storm; in one, bright with a flicker one tick in
	 * {@code STORM_FLICKER} (in threes), dimmer to this between. A Deepborn's veins pulse from {@code DEEP_PULSE_LOW} of
	 * full and back, over {@code DEEP_PULSE_TICKS}.
	 */
	static final int STORM_CALM_GLOW = 0xFF505A64, STORM_GLOW = 0xFFB4B4B4, STORM_FLICKER = 11, DEEP_PULSE_TICKS = 60;
	static final float DEEP_PULSE_LOW = 0.35F;

	public static final RenderStateDataKey<Look> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:heritage_look");

	// ---- Tideborn fins: flat boxes (no width), textured both sides (tools/gen_heritage_looks.py lays fins.png out)
	private static final Identifier FINS = JaymosKingdoms.id("textures/entity/heritage/fins.png");
	private static final Identifier FINS_GLOW = JaymosKingdoms.id("textures/entity/heritage/fins_glow.png");
	/** How far the ear fins and the arm fins flare out from straight back (radians). */
	static final float EAR_FLARE = 0.6F, ARM_FLARE = 0.5F;
	/** The fin sets, by drawing: a crest, ear fins, long arm fins (every set has arm fins). */
	static final boolean[] CREST = {true, false, true}, EARS = {false, true, true}, LONG_ARMS = {false, false, true};
	private static final ModelPart[] HEAD_FINS = new ModelPart[3];
	private static ModelPart rightArmFin, leftArmFin, rightArmFinLong, leftArmFinLong;

	/** (0.7.262) A Divine-born's drifting light. */
	private static final DustParticleOptions LIGHT_MOTE = new DustParticleOptions(0xFFE9A0, 0.6F);

	/** Stoneborn glints (amethyst). */
	private static final DustParticleOptions AMETHYST = new DustParticleOptions(0xB17BEB, 0.6F);

	private HeritageLooks(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new HeritageLooks(avatar));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(HeritageLooks::tick);
	}

	/** From the living renderer's extractRenderState: a player's heritage look on their render state. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		Kind kind = entity instanceof Player player ? Kind.of(player) : null;
		VariantLooks.Style style = entity instanceof Player ? VariantLooks.of(entity) : null;
		if (kind == null && style == null) {
			state.setData(KEY, null);
			return;
		}
		if (kind == null) {
			// (0.7.214) a Heartlander's, a Warrior-born's or an Emberborn's variant: its own marks and eyes, lit in full
			state.setData(KEY, new Look(null, 0, style.eyes() != null && eyesLit(style.eyes(), entity), -1, false, style));
			return;
		}
		boolean blood = kind == Kind.NIGHTBORN && NightbornBlood.starlit(entity);
		boolean lit = blood || eyesLit(kind, entity, style);
		int glow = mood(kind, entity);
		int flags = entity.getAttachedOrElse(Heritage.LOOK, 0);
		if (kind == Kind.TIDEBORN) {
			// (0.7.80) a Tideborn glows under water, and at night while still wet; dry, not at all
			boolean on = entity.isUnderWater() || (flags & Heritage.LOOK_WET) != 0 && entity.level().isDarkOutside();
			glow = on ? -1 : 0;
			lit &= on;
		} else if (kind == Kind.SYLVAN && entity.isCrouching()) {
			// sneaking, a Sylvan's glow dims; a thief's goes out
			glow = (flags & Heritage.LOOK_UNSEEN) != 0 ? 0 : SYLVAN_SNEAK_GLOW;
			lit &= glow != 0;
		} else if (kind == Kind.EARTHLING) {
			// (0.7.184) only a Z Fighter's fists light up; any other Earthling just has the wraps
			glow = EarthlingSurge.surging(entity) && EarthlingSurge.shows(entity) ? -1 : 0;
		}
		// (0.7.262) a Bio-built's drawing is its form
		int drawing = kind == Kind.BIOBUILT ? BioBuilt.form(entity) : variant(entity.getUUID(), kind == Kind.TIDEBORN ? CREST.length : kind.skin.length);
		state.setData(KEY, new Look(kind, drawing, lit, glow, blood, style));
	}

	/**
	 * (0.7.184) For Jaymo's Powers' gi (Andy: "Can you add the wraps to the gi?"), read by reflection, so it takes the
	 * render state as an Object and never changes its signature: 0 when the player being drawn isn't an Earthling, 1 for
	 * the wraps alone, 2 with a Z Fighter's ki glow over them. Powers draws them on the gi itself, in its pose, since under
	 * a costume this layer draws no marks.
	 */
	public static int earthlingWraps(Object state) {
		Look look = state instanceof AvatarRenderState avatar ? avatar.getData(KEY) : null;
		if (look == null || look.kind() != Kind.EARTHLING) {
			return 0;
		}
		return look.glow() != 0 ? 2 : 1;
	}

	/** Whether {@code kind}'s eyes glow on {@code entity} now (a player's or a villager's). */
	static boolean eyesLit(Kind kind, LivingEntity entity) {
		return eyesLit(kind.eyes, entity);
	}

	/** (0.7.214) The same, in variant {@code style} (its own eyes, if it has them). */
	static boolean eyesLit(Kind kind, LivingEntity entity, VariantLooks.@Nullable Style style) {
		return eyesLit(style != null && style.eyes() != null ? style.eyes() : kind.eyes, entity);
	}

	/** Whether eyes that glow {@code eyes} glow on {@code entity} now. */
	static boolean eyesLit(Eyes eyes, LivingEntity entity) {
		return switch (eyes) {
			case ALWAYS -> true;
			case DAY -> entity.level().isBrightOutside();
			case NIGHT -> entity.level().isDarkOutside();
			case STORM -> entity.level().isThundering();
			case NEVER -> false;
		};
	}

	/**
	 * (0.7.106) The glow's tint that changes with the weather and the hour (players and villagers alike): a Stormborn's
	 * scars flicker in a thunderstorm and sit dim out of one, a Dragonblood's scales glow at night only, a Deepborn's veins
	 * pulse. -1 (full) for the rest.
	 */
	static int mood(Kind kind, LivingEntity entity) {
		return switch (kind) {
			case STORMBORN -> {
				if (!entity.level().isThundering()) {
					yield STORM_CALM_GLOW;
				}
				int t = (entity.tickCount + (entity.getId() * 7)) / 3;
				yield t % STORM_FLICKER == 0 ? -1 : STORM_GLOW;
			}
			case DRAGONBLOOD -> entity.level().isDarkOutside() ? -1 : 0;
			case DEEPBORN -> {
				float wave = 0.5F + 0.5F * Mth.sin((entity.tickCount + entity.getId() * 13) * Mth.TWO_PI / DEEP_PULSE_TICKS);
				int g = Math.round(255 * (DEEP_PULSE_LOW + (1.0F - DEEP_PULSE_LOW) * wave));
				yield 0xFF000000 | g << 16 | g << 8 | g;
			}
			default -> -1;
		};
	}

	static int variant(UUID id, int drawings) {
		return Math.floorMod(Long.hashCode(id.getMostSignificantBits() * 17L + id.getLeastSignificantBits()), drawings);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Look look = state.getData(KEY);
		if (look == null || state.isInvisible || PowersCover.face(state)) {
			// (0.7.81) a mask or the symbiote's suit over it all (a costume always covers the body too)
			return;
		}
		// under a hood no head fins, under a costume's sleeves no arm fins
		headFinsCovered = PowersCover.head(state);
		armFinsCovered = PowersCover.body(state);
		PlayerModel model = this.getParentModel();
		// (0.7.219) a player MCA draws: on MCA's skin body, in the pose MCA gives its skin (McaLookModels)
		PlayerModel marks = model, glowing = model;
		if (McaCompat.LOADED) {
			PlayerModel mcaMarks = McaLookModels.playerMarks(model), mcaGlow = McaLookModels.playerGlow(model);
			if (mcaMarks != null && mcaGlow != null) {
				marks = mcaMarks;
				glowing = mcaGlow;
			}
		}
		Kind kind = look.kind();
		VariantLooks.Style style = look.style();
		// (0.7.184) under a costume no marks or glow either (Andy: the Earthling's ki glow "costume clipping is back"):
		// they're drawn on the plain body's pose, the costume on MCA's, so where the cut leaves skin bare (the gi's
		// forearms) the marks slid out through its sleeves in motion
		Identifier skin = look.blood() ? BLOOD_SKIN : style != null ? style.skin() : kind.skin[look.variant()];
		Identifier glow = look.blood() ? BLOOD_GLOW : style != null ? style.glow() : kind.glow[look.variant()];
		if (!armFinsCovered) {
			collector.order(1).submitModel(marks, state, poseStack, RenderTypes.entityCutout(skin), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
		}
		if (look.glow() != 0 && !armFinsCovered) {
			collector.order(2).submitModel(glowing, state, poseStack, RenderTypes.eyes(glow), lightCoords, OverlayTexture.NO_OVERLAY, look.glow(), null,
				state.outlineColor);
		}
		if (look.eyesLit() && !look.blood() && style != null && style.stockEyes() != null) {
			// (0.7.214) a variant's own eyes
			Identifier eyes = VampireLooks.glowingEyes(state.skin.body().texturePath(), style.eyeColor(), style.stockEyes(), style.eyesPrefix(),
				style.eyesBySkin());
			collector.order(2).submitModel(glowing, state, poseStack, RenderTypes.eyes(eyes), lightCoords, OverlayTexture.NO_OVERLAY, look.glow(), null,
				state.outlineColor);
		} else if (look.eyesLit() && kind != null && kind.stockEyes != null) {
			Integer vampire = state.getData(VampireLooks.LOOK);
			boolean redSilver = look.blood() && vampire != null && vampire == 2;
			Identifier eyes = redSilver ? VampireLooks.glowingEyes(state.skin.body().texturePath(), BLOOD_EYE_COLOR, BLOOD_EYES, "nightborn_blood_eyes/",
				BLOOD_EYES_BY_SKIN) : VampireLooks.glowingEyes(state.skin.body().texturePath(), kind.eyeColor, kind.stockEyes,
				kind.name().toLowerCase(java.util.Locale.ROOT) + "_eyes/", kind.eyesBySkin);
			collector.order(2).submitModel(glowing, state, poseStack, RenderTypes.eyes(eyes), lightCoords, OverlayTexture.NO_OVERLAY, look.glow(), null,
				state.outlineColor);
		}
		if (kind == Kind.BIOBUILT && style != null && look.variant() > 0 && !armFinsCovered) {
			// (0.7.262) a variant's marks wear their form's plates over them
			collector.order(1).submitModel(marks, state, poseStack, RenderTypes.entityCutout(PLATES_SKIN[look.variant() - 1]), lightCoords,
				OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
			if (look.glow() != 0) {
				collector.order(2).submitModel(glowing, state, poseStack, RenderTypes.eyes(PLATES_GLOW[look.variant() - 1]), lightCoords,
					OverlayTexture.NO_OVERLAY, look.glow(), null, state.outlineColor);
			}
		}
		if (kind == Kind.DIVINE && !headFinsCovered && marks.head.visible) {
			halo(poseStack, collector, marks, state.ageInTicks);
		}
		if (kind == Kind.TIDEBORN) {
			finTextures(style);
			fins(poseStack, collector, lightCoords, marks, look.variant(), look.glow() != 0);
		}
	}

	/** (0.7.262) The Bio-built's plates laid over a variant's own marks, by form (the second, the third). */
	private static final Identifier[] PLATES_SKIN = {JaymosKingdoms.id("textures/entity/heritage/biobuilt_plates_1_skin.png"),
		JaymosKingdoms.id("textures/entity/heritage/biobuilt_plates_2_skin.png")};
	private static final Identifier[] PLATES_GLOW = {JaymosKingdoms.id("textures/entity/heritage/biobuilt_plates_1_glow.png"),
		JaymosKingdoms.id("textures/entity/heritage/biobuilt_plates_2_glow.png")};

	// ---- (0.7.262) the Divine-born's halo: a flat gold ring of four thin bars, a model part, lit like the glow
	private static final Identifier HALO = JaymosKingdoms.id("textures/entity/heritage/halo.png");
	/** How far above the top of the head it floats (model pixels), how far it bobs, and how fast (radians a tick). */
	static final float HALO_RISE = 4.0F, HALO_BOB = 0.5F, HALO_SPEED = 0.08F;
	private static ModelPart halo;

	/** The halo, made the first time it's drawn (head space: the top of the head at y -8). */
	private static ModelPart halo() {
		if (halo == null) {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("ring", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-5.0F, 0.0F, -5.0F, 10.0F, 0.0F, 1.0F)
				.texOffs(0, 2).addBox(-5.0F, 0.0F, 4.0F, 10.0F, 0.0F, 1.0F)
				.texOffs(0, 4).addBox(-5.0F, 0.0F, -4.0F, 1.0F, 0.0F, 8.0F)
				.texOffs(0, 14).addBox(4.0F, 0.0F, -4.0F, 1.0F, 0.0F, 8.0F), PartPose.ZERO);
			halo = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		}
		return halo;
	}

	private static void halo(PoseStack poseStack, SubmitNodeCollector collector, net.minecraft.client.model.HumanoidModel<?> model, float age) {
		poseStack.pushPose();
		model.head.translateAndRotate(poseStack);
		poseStack.translate(0.0F, (-8.0F - HALO_RISE + Mth.sin(age * HALO_SPEED) * HALO_BOB) / 16.0F, 0.0F);
		collector.order(2).submitModelPart(halo(), poseStack, RenderTypes.eyes(HALO), 0xF000F0,
			OverlayTexture.NO_OVERLAY, null, -1);
		poseStack.popPose();
	}

	/** (0.7.214) The fins' textures for the Tideborn being drawn: a variant's own, or the first look's. */
	private static Identifier finsNow = FINS, finsGlowNow = FINS_GLOW;

	static void finTextures(VariantLooks.@Nullable Style style) {
		finsNow = style != null && style.fins() != null ? style.fins() : FINS;
		finsGlowNow = style != null && style.finsGlow() != null ? style.finsGlow() : FINS_GLOW;
	}

	/** (0.7.81) What Jaymo's Powers covers on the player being drawn (set in submit, read by fins). */
	private static boolean headFinsCovered, armFinsCovered;

	/** A Tideborn's fins: on the head (crest, ear fins) and the backs of the forearms. */
	static void fins(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, net.minecraft.client.model.HumanoidModel<?> model, int set,
		boolean glow, boolean headCovered, boolean armsCovered) {
		headFinsCovered = headCovered;
		armFinsCovered = armsCovered;
		fins(poseStack, collector, lightCoords, model, set, glow);
	}

	private static void fins(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, net.minecraft.client.model.HumanoidModel<?> model, int set, boolean glow) {
		if (model.head.visible && !headFinsCovered) {
			poseStack.pushPose();
			model.head.translateAndRotate(poseStack);
			fin(poseStack, collector, lightCoords, glow, headFins(set));
			poseStack.popPose();
		}
		bakeArmFins();
		if (model.rightArm.visible && !armFinsCovered) {
			poseStack.pushPose();
			model.rightArm.translateAndRotate(poseStack);
			fin(poseStack, collector, lightCoords, glow, LONG_ARMS[set] ? rightArmFinLong : rightArmFin);
			poseStack.popPose();
		}
		if (model.leftArm.visible && !armFinsCovered) {
			poseStack.pushPose();
			model.leftArm.translateAndRotate(poseStack);
			fin(poseStack, collector, lightCoords, glow, LONG_ARMS[set] ? leftArmFinLong : leftArmFin);
			poseStack.popPose();
		}
	}

	private static void fin(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, boolean glow, ModelPart part) {
		collector.order(1).submitModelPart(part, poseStack, RenderTypes.entityCutout(finsNow), lightCoords, OverlayTexture.NO_OVERLAY, null, -1);
		if (glow) {
			collector.order(2).submitModelPart(part, poseStack, RenderTypes.eyes(finsGlowNow), lightCoords, OverlayTexture.NO_OVERLAY, null, -1);
		}
	}

	/** Fin set {@code set}'s head fins, made the first time they're drawn (head space: the top of the head at y -8). */
	private static ModelPart headFins(int set) {
		if (HEAD_FINS[set] == null) {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			if (CREST[set]) { // down the middle of the head, front to back
				root.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, -4.0F, -3.0F, 0.0F, 4.0F, 6.0F),
					PartPose.offset(0.0F, -8.0F, 0.0F));
			}
			if (EARS[set]) { // from the sides of the head behind the eyes, swept back and out
				for (int side = -1; side <= 1; side += 2) {
					root.addOrReplaceChild(side < 0 ? "right_ear" : "left_ear",
						CubeListBuilder.create().texOffs(0, 12).addBox(0.0F, 0.0F, 0.0F, 0.0F, 5.0F, 4.0F),
						PartPose.offsetAndRotation(side * 4.0F, -6.0F, -1.0F, 0.0F, side * EAR_FLARE, 0.0F));
				}
			}
			HEAD_FINS[set] = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		}
		return HEAD_FINS[set];
	}

	/** The arm fins (arm space: the shoulder pivot; the forearm is y 3-10, its back z 2). */
	private static void bakeArmFins() {
		if (rightArmFin != null) {
			return;
		}
		rightArmFin = armFin(-1, 16, 12, 6, 3.0F);
		leftArmFin = armFin(1, 16, 12, 6, 3.0F);
		rightArmFinLong = armFin(-1, 24, 0, 8, 1.0F);
		leftArmFinLong = armFin(1, 24, 0, 8, 1.0F);
	}

	private static ModelPart armFin(int side, int u, int v, int height, float top) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("fin", CubeListBuilder.create().texOffs(u, v).addBox(0.0F, 0.0F, 0.0F, 0.0F, height, 3.0F),
			PartPose.offsetAndRotation(side * 1.0F, top, 2.0F, 0.0F, side * ARM_FLARE, 0.0F));
		return LayerDefinition.create(mesh, 32, 32).bakeRoot();
	}

	/** Every client tick: now and then a particle off a hand (whatever each heritage has). */
	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			Kind kind = Kind.of(player);
			RandomSource random = player.getRandom();
			VariantLooks.Style style = VariantLooks.of(player);
			if (style != null) {
				// (0.7.214) a variant's own particle instead
				if (!player.isInvisible() && !player.isSpectator() && !(player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson())
					&& VariantLooks.particleNow(style, player, random)) {
					VariantLooks.particle(level, player, style, random);
				}
				continue;
			}
			if (kind == null || kind.particleEvery == 0 || random.nextInt(kind.particleEvery) != 0 || player.isInvisible() || player.isSpectator()
				|| player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
				continue;
			}
			particle(level, player, kind, random, (player.getAttachedOrElse(Heritage.LOOK, 0) & Heritage.LOOK_WET) != 0);
		}
	}

	/** One of {@code kind}'s particles off {@code player}'s hand (a villager's too, {@link VillagerLooks}); {@code wet}: a Tideborn still wet. */
	static void particle(ClientLevel level, LivingEntity player, Kind kind, RandomSource random, boolean wet) {
		{
			// a hand hangs at the side, a little below the waist
			float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
			double side = (random.nextBoolean() ? 1.0 : -1.0) * player.getBbWidth() * 0.6;
			double x = player.getX() - Mth.cos(yaw) * side, z = player.getZ() - Mth.sin(yaw) * side;
			double y = player.getY() + player.getBbHeight() * 0.4;
			switch (kind) {
				case STONEBORN -> level.addParticle(AMETHYST, x, y, z, 0.0, 0.01, 0.0);
				case TIDEBORN -> {
					// bubbles under water; on land, drips only while still wet (0.7.80)
					boolean under = player.isUnderWater();
					if (under || wet) {
						ParticleOptions drop = under ? ParticleTypes.BUBBLE : ParticleTypes.DRIPPING_WATER;
						level.addParticle(drop, x, y, z, 0.0, under ? 0.08 : 0.0, 0.0);
					}
				}
				case SYLVAN -> {
					if (!player.isCrouching()) {
						level.addParticle(ParticleTypes.FIREFLY, player.getX() + (random.nextDouble() - 0.5) * 2.0,
					player.getY() + random.nextDouble() * 2.0, player.getZ() + (random.nextDouble() - 0.5) * 2.0, 0.0, 0.0, 0.0);
					}
				}
				case FROSTBORN -> level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0.0, -0.02, 0.0);
				case STORMBORN -> {
					if (level.isThundering()) {
						level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.05, 0.0);
					}
				}
				case ENDBORN -> {
					if (level.isDarkOutside() && !player.isInWaterOrRain()) {
						level.addParticle(ParticleTypes.PORTAL, x, y, z, (random.nextDouble() - 0.5) * 0.5, -random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.5);
					}
				}
				case DEEPBORN -> {
					if (level.getMaxLocalRawBrightness(player.blockPosition()) <= 7) {
						level.addParticle(ParticleTypes.SCULK_CHARGE_POP, x, y, z, 0.0, 0.02, 0.0);
					}
				}
				case DIVINE -> level.addParticle(LIGHT_MOTE, player.getX() + (random.nextDouble() - 0.5) * 1.0, player.getY() + random.nextDouble() * player.getBbHeight(),
					player.getZ() + (random.nextDouble() - 0.5) * 1.0, 0.0, 0.02, 0.0);
				case NIGHTBORN -> {
					if (level.isDarkOutside()) {
						// (0.7.88) a Nightborn vampire's wisps are blood mist
						level.addParticle(NightbornBlood.starlit(player) ? BLOOD_MIST : ParticleTypes.SMOKE, x, y, z, 0.0, 0.02, 0.0);
					}
				}
				default -> {
				}
			}
		}
	}
}
