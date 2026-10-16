package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
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
 * <li><b>Tideborn:</b> fishmen: gills on the cheeks and the sides of the head, fins (model parts) on the head and the
 * forearms in one of three sets (by UUID: crest and cheek gills; ear fins; crest, ear fins and long arm fins), sea-green
 * scales and rows of deep-sea lights on the forearms; cyan eyes; bubbles under water, drips on land.</li>
 * <li><b>Sylvan:</b> a thick glowing vine winding round each arm, its own way on each player (by UUID, four); pale
 * green eyes; fireflies.</li>
 * <li><b>Verdant:</b> only the pink forearm patches, glowing in the dark (the green skin and antennae are
 * {@link VerdantLook}'s).</li>
 * <li><b>Frostborn:</b> the Verdant's pink patches too, and rime on the fingers; ice-white eyes; snowflakes.</li>
 * <li><b>Nightborn:</b> shadow soaking the hands with stars in it; moon-silver eyes that glow by day only; dark wisps
 * at night only.</li>
 * <li><b>Earthling:</b> training wraps and a ki glow round the fists; no eye glow, no particles.</li>
 * </ul>
 * Particles: not round your own first-person camera, not on the invisible or spectators.
 */
public final class HeritageLooks extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** How a heritage's eyes glow. */
	enum Eyes { ALWAYS, DAY, NEVER }

	/** One heritage's look: its texture name, how many drawings to pick from (by UUID), eyes, and particle rate (0: none). */
	enum Kind {
		STONEBORN(Heritage.Kind.STONEBORN, "stoneborn", 1, 0xFFD6AAFF, Eyes.DAY, 24),
		TIDEBORN(Heritage.Kind.TIDEBORN, "tideborn", 3, 0xFF6EFAEB, Eyes.ALWAYS, 14),
		SYLVAN(Heritage.Kind.SYLVAN, "sylvan", 4, 0xFFD6F596, Eyes.ALWAYS, 30),
		VERDANT(Heritage.Kind.VERDANT, "verdant", 1, 0, Eyes.NEVER, 0),
		FROSTBORN(Heritage.Kind.FROSTBORN, "frostborn", 1, 0xFFC8ECFF, Eyes.ALWAYS, 16),
		NIGHTBORN(Heritage.Kind.NIGHTBORN, "nightborn", 1, 0xFFECEEFF, Eyes.DAY, 12),
		EARTHLING(Heritage.Kind.EARTHLING, "earthling", 1, 0, Eyes.NEVER, 0);

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

		static @Nullable Kind of(Player player) {
			for (Kind kind : values()) {
				if (Heritage.is(player, kind.heritage)) {
					return kind;
				}
			}
			return null;
		}
	}

	/** On a player's render state: their heritage's look, which drawing, and whether their eyes glow now. */
	record Look(Kind kind, int variant, boolean eyesLit) {
	}

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
		if (kind == null) {
			state.setData(KEY, null);
			return;
		}
		boolean lit = switch (kind.eyes) {
			case ALWAYS -> true;
			case DAY -> entity.level().isBrightOutside();
			case NEVER -> false;
		};
		state.setData(KEY, new Look(kind, variant(entity.getUUID(), kind.skin.length), lit));
	}

	static int variant(UUID id, int drawings) {
		return Math.floorMod(Long.hashCode(id.getMostSignificantBits() * 17L + id.getLeastSignificantBits()), drawings);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Look look = state.getData(KEY);
		if (look == null || state.isInvisible) {
			return;
		}
		PlayerModel model = this.getParentModel();
		Kind kind = look.kind();
		collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityCutout(kind.skin[look.variant()]), lightCoords,
			OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
		collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(kind.glow[look.variant()]), lightCoords,
			OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
		if (look.eyesLit() && kind.stockEyes != null) {
			Identifier eyes = VampireLooks.glowingEyes(state.skin.body().texturePath(), kind.eyeColor, kind.stockEyes,
				kind.name().toLowerCase(java.util.Locale.ROOT) + "_eyes/", kind.eyesBySkin);
			collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(eyes), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
		}
		if (kind == Kind.TIDEBORN) {
			fins(poseStack, collector, lightCoords, model, look.variant());
		}
	}

	/** A Tideborn's fins: on the head (crest, ear fins) and the backs of the forearms. */
	private static void fins(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, PlayerModel model, int set) {
		if (model.head.visible) {
			poseStack.pushPose();
			model.head.translateAndRotate(poseStack);
			fin(poseStack, collector, lightCoords, headFins(set));
			poseStack.popPose();
		}
		bakeArmFins();
		if (model.rightArm.visible) {
			poseStack.pushPose();
			model.rightArm.translateAndRotate(poseStack);
			fin(poseStack, collector, lightCoords, LONG_ARMS[set] ? rightArmFinLong : rightArmFin);
			poseStack.popPose();
		}
		if (model.leftArm.visible) {
			poseStack.pushPose();
			model.leftArm.translateAndRotate(poseStack);
			fin(poseStack, collector, lightCoords, LONG_ARMS[set] ? leftArmFinLong : leftArmFin);
			poseStack.popPose();
		}
	}

	private static void fin(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, ModelPart part) {
		collector.order(1).submitModelPart(part, poseStack, RenderTypes.entityCutout(FINS), lightCoords, OverlayTexture.NO_OVERLAY, null, -1);
		collector.order(2).submitModelPart(part, poseStack, RenderTypes.eyes(FINS_GLOW), lightCoords, OverlayTexture.NO_OVERLAY, null, -1);
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
			if (kind == null || kind.particleEvery == 0 || random.nextInt(kind.particleEvery) != 0 || player.isInvisible() || player.isSpectator()
				|| player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
				continue;
			}
			// a hand hangs at the side, a little below the waist
			float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
			double side = (random.nextBoolean() ? 1.0 : -1.0) * player.getBbWidth() * 0.6;
			double x = player.getX() - Mth.cos(yaw) * side, z = player.getZ() - Mth.sin(yaw) * side;
			double y = player.getY() + player.getBbHeight() * 0.4;
			switch (kind) {
				case STONEBORN -> level.addParticle(AMETHYST, x, y, z, 0.0, 0.01, 0.0);
				case TIDEBORN -> {
					boolean wet = player.isUnderWater();
					ParticleOptions drop = wet ? ParticleTypes.BUBBLE : ParticleTypes.DRIPPING_WATER;
					level.addParticle(drop, x, y, z, 0.0, wet ? 0.08 : 0.0, 0.0);
				}
				case SYLVAN -> level.addParticle(ParticleTypes.FIREFLY, player.getX() + (random.nextDouble() - 0.5) * 2.0,
					player.getY() + random.nextDouble() * 2.0, player.getZ() + (random.nextDouble() - 0.5) * 2.0, 0.0, 0.0, 0.0);
				case FROSTBORN -> level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0.0, -0.02, 0.0);
				case NIGHTBORN -> {
					if (level.isDarkOutside()) {
						level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.02, 0.0);
					}
				}
				default -> {
				}
			}
		}
	}
}
