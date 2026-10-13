package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebBehemoth;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The feral undead behemoth (a risen body the symbiote took, see WebBehemoth): the big body's black skin and veins
 * over Kitchen's zombie player, and a crown of tendrils on its back that thrash wildly and never stop, each one
 * lashing at whatever it's after on its own.
 */
public final class WebBehemothClient {
	// ---- tuning ----
	/** The big body over the zombie: as the player's (WebSymbioteLayer.BULK), veins just above it. */
	private static final float BULK = 0.7F, VEIN_GAP = 0.04F;
	private static final int VEIN_TICKS = 4;
	/** Back tendrils: how many, how long (times its scale), how thick at the root and tip. */
	private static final int TENDRILS = WebBehemoth.TENDRILS;
	/** The thrashing: how fast and how wide (share of the length), and the sudden jerks on top. */
	private static final double THRASH_SPEED = 0.45, THRASH_WIDE = 0.9, JERK = 0.8;
	private static final double LENGTH = 1.7, ROOT_RADIUS = 0.11, TIP_RADIUS = 0.03;
	private static final int PIECES = 10;
	/** A lash: reared until this share of it, lands at {@code WebBehemoth.STRIKE_HIT}, how high it rears. */
	private static final float REAR_SHARE = 0.35F;
	private static final double REAR_UP = 1.6;

	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_suit.png");
	private static final Identifier[] VEINS = {
		JaymosPowers.id("textures/entity/web_symbiote_veins_0.png"), JaymosPowers.id("textures/entity/web_symbiote_veins_1.png"),
		JaymosPowers.id("textures/entity/web_symbiote_veins_2.png"), JaymosPowers.id("textures/entity/web_symbiote_veins_3.png")};

	/** On a behemoth's render state: it wears the big body. */
	public static final RenderStateDataKey<Boolean> HOST = RenderStateDataKey.create(() -> "jaymospowers:web_behemoth");

	/** On a mob's render state: a roaming symbiote is in it (see WebInhabit), so it wears the suit over its own body. */
	public static final RenderStateDataKey<Boolean> INHABITED = RenderStateDataKey.create(() -> "jaymospowers:web_inhabited");
	/** The suit over any mob's own model: black goo with veins, a texture that reads the same on any UV layout. */
	private static final Identifier GOO = JaymosPowers.id("textures/entity/web_symbiote_goo.png");

	/** Behemoths (and hostile mobs a symbiote is in) seen lately (entity ids), for their tendrils. */
	private static final Set<Integer> SEEN = new HashSet<>();

	private WebBehemothClient() {
	}

	static void init() {
		// any mob can be inhabited: every living renderer gets the suit layer
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> helper.register(inhabited(renderer)));
		if (FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			Kitchen.init();
		}
	}

	/** From EntityRenderer.extractRenderState: marks a behemoth for its suit layer, and remembers it for its tendrils. */
	public static void extract(Entity entity, EntityRenderState state) {
		if (entity.hasAttached(WebBehemoth.HOST)) {
			state.setData(HOST, Boolean.TRUE);
			SEEN.add(entity.getId());
		} else if (state.getData(HOST) != null) {
			state.setData(HOST, null);
		}
		if (com.jaymospowers.power.web.WebInhabit.isHost(entity)) {
			state.setData(INHABITED, Boolean.TRUE);
			if (com.jaymospowers.power.web.WebInhabit.lashing(entity)) {
				SEEN.add(entity.getId());
			}
		} else if (state.getData(INHABITED) != null) {
			state.setData(INHABITED, null);
		}
	}

	/** Every behemoth's back tendrils (from WebLineRenderer, in the level pass). */
	static void render(LevelRenderContext context, ClientLevel level, Vec3 camera, float partialTick) {
		if (SEEN.isEmpty()) {
			return;
		}
		SEEN.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity host)
			|| !host.hasAttached(WebBehemoth.HOST) && !com.jaymospowers.power.web.WebInhabit.lashing(host) || !host.isAlive());
		for (int id : SEEN) {
			LivingEntity host = (LivingEntity)level.getEntity(id);
			if (host == null || host.getPosition(partialTick).distanceToSqr(camera) > 64.0 * 64.0) {
				continue;
			}
			tendrils(context, level, host, camera, partialTick);
		}
	}

	private static void tendrils(LevelRenderContext context, ClientLevel level, LivingEntity host, Vec3 camera, float partialTick) {
		float scale = host.getScale();
		float time = host.tickCount + partialTick;
		float yaw = Mth.lerp(partialTick, host.yBodyRotO, host.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		Vec3 back = host.getPosition(partialTick).add(0.0, host.getBbHeight() * 0.74, 0.0).subtract(forward.scale(0.2 * scale));
		// each tendril's lash: which target, and how far through
		Entity[] targets = new Entity[TENDRILS];
		float[] swings = new float[TENDRILS];
		java.util.Arrays.fill(swings, -1.0F);
		for (WebBehemoth.Strike strike : host.getAttachedOrElse(WebBehemoth.STRIKE, java.util.List.<WebBehemoth.Strike>of())) {
			if (strike.tendril() >= 0 && strike.tendril() < TENDRILS) {
				targets[strike.tendril()] = level.getEntity(strike.target());
				swings[strike.tendril()] = Mth.clamp((level.getGameTime() - strike.start() + partialTick) / WebBehemoth.STRIKE_TICKS, 0.0F, 1.0F);
			}
		}
		int light = net.minecraft.util.LightCoordsUtil.getLightCoords(level, BlockPos.containing(back));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(WebLineRenderer.TENDRIL_TEXTURE), (pose, buffer) -> {
			for (int t = 0; t < TENDRILS; t++) {
				double side = (t % 2 == 0 ? 1.0 : -1.0) * (0.6 + 0.4 * (t / 2));
				Vec3 root = back.add(right.scale(side * 0.12 * scale)).add(0.0, (0.15 - 0.12 * (t / 2)) * scale, 0.0);
				double length = LENGTH * scale * (1.0 - 0.12 * (t / 2));
				// thrashing wildly, never still: fast, wide, and jerking out at nothing now and then
				double a = time * THRASH_SPEED;
				double jerk = Math.pow(Math.max(0.0, Math.sin(a * 0.7 + t * 2.3) * Math.sin(a * 0.31 + t * 1.3)), 6.0) * JERK;
				double sway = (Math.sin(a + t * 1.7) * 0.6 + Math.sin(a * 2.3 + t * 2.9) * 0.3) * THRASH_WIDE;
				Vec3 bend = right.scale(side * (0.55 + jerk) + sway).add(up.scale(0.5 + 0.5 * Math.cos(a * 1.3 + t)))
					.add(forward.scale(-0.3 + 0.7 * Math.sin(a * 0.9 + t * 0.7) + jerk));
				Vec3 tip = root.add(bend.normalize().scale(length * (1.0 + 0.3 * jerk)));
				Vec3 mid = root.add(up.scale(length * 0.45)).add(right.scale(side * length * 0.2))
					.add(right.scale(Math.sin(a * 1.7 + t) * length * 0.25)).add(forward.scale(Math.cos(a * 1.5 + t * 2.0) * length * 0.2));
				Entity target = targets[t];
				float swing = swings[t];
				if (target != null && swing >= 0.0F) {
					// it rears up and whips down onto whatever it's after, then draws back
					Vec3 aim = target.getPosition(partialTick).add(0.0, target.getBbHeight() * 0.5, 0.0);
					Vec3 reared = root.add(up.scale(REAR_UP * scale)).add(right.scale(side * 0.4 * scale)).subtract(forward.scale(0.3 * scale));
					float hit = WebBehemoth.STRIKE_HIT / (float)WebBehemoth.STRIKE_TICKS;
					if (swing < REAR_SHARE) {
						tip = tip.lerp(reared, smooth(swing / REAR_SHARE));
					} else if (swing < hit) {
						float k = (swing - REAR_SHARE) / (hit - REAR_SHARE);
						tip = reared.lerp(aim, k * k);
					} else {
						tip = aim.lerp(tip, smooth((swing - hit) / (1.0F - hit)));
					}
					mid = root.lerp(tip, 0.5).add(up.scale(0.6 * scale));
				}
				Vec3 prev = root.subtract(camera);
				for (int k = 1; k <= PIECES; k++) {
					double s = k / (double)PIECES;
					Vec3 p = root.lerp(mid, s).lerp(mid.lerp(tip, s), s).subtract(camera);
					double r0 = Mth.lerp((k - 1) / (double)PIECES, ROOT_RADIUS, TIP_RADIUS) * scale;
					double r1 = Mth.lerp(s, ROOT_RADIUS, TIP_RADIUS) * scale;
					WebLineRenderer.tube(buffer, pose, prev, p, r0, r1, k + t, light);
					prev = p;
				}
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static RenderLayer inhabited(net.minecraft.client.renderer.entity.LivingEntityRenderer renderer) {
		return new Inhabited(renderer);
	}

	/** The black suit over an inhabited mob: its own model, drawn again in the goo (the way glowing eyes are). */
	private static final class Inhabited<S extends net.minecraft.client.renderer.entity.state.LivingEntityRenderState,
		M extends net.minecraft.client.model.EntityModel<? super S>> extends RenderLayer<S, M> {
		Inhabited(net.minecraft.client.renderer.entity.RenderLayerParent<S, M> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			if (state.getData(INHABITED) == null || state.isInvisible) {
				return;
			}
			renderColoredCutoutModel(this.getParentModel(), GOO, poseStack, collector, lightCoords, state, -1, 1);
		}
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** Only touched when Jaymo's Kitchen is loaded: the big body layered over its zombie player. */
	private static final class Kitchen {
		static void init() {
			LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
				if (renderer instanceof com.jaymoskitchen.client.corpse.ZombiePlayerRenderer zombie) {
					helper.register(new Suit(zombie));
				}
			});
		}
	}

	private static final class Suit extends RenderLayer<com.jaymoskitchen.client.corpse.ZombiePlayerRenderer.State, com.jaymoskitchen.client.corpse.ZombiePlayerModel> {
		private final com.jaymoskitchen.client.corpse.ZombiePlayerModel bulk = model(BULK);
		private final com.jaymoskitchen.client.corpse.ZombiePlayerModel veins = model(BULK + VEIN_GAP);

		Suit(com.jaymoskitchen.client.corpse.ZombiePlayerRenderer renderer) {
			super(renderer);
		}

		private static com.jaymoskitchen.client.corpse.ZombiePlayerModel model(float inflate) {
			return new com.jaymoskitchen.client.corpse.ZombiePlayerModel(
				LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(inflate), false), 64, 64).bakeRoot());
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			com.jaymoskitchen.client.corpse.ZombiePlayerRenderer.State state, float yRot, float xRot) {
			if (state.getData(HOST) == null) {
				return;
			}
			coloredCutoutModelCopyLayerRender(this.bulk, TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
			Identifier frame = VEINS[(int)(state.ageInTicks / VEIN_TICKS) % VEINS.length];
			coloredCutoutModelCopyLayerRender(this.veins, frame, poseStack, collector, lightCoords, state, -1, 1);
		}
	}
}
