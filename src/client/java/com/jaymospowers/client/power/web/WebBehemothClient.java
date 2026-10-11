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
 * over Kitchen's zombie player, and a crown of tendrils on its back that writhe and lash at its prey.
 */
public final class WebBehemothClient {
	// ---- tuning ----
	/** The big body over the zombie: as the player's (WebSymbioteLayer.BULK), veins just above it. */
	private static final float BULK = 0.7F, VEIN_GAP = 0.04F;
	private static final int VEIN_TICKS = 4;
	/** Back tendrils: how many, how long (times its scale), how thick at the root and tip. */
	private static final int TENDRILS = 6;
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

	/** Behemoths seen lately (entity ids), for their tendrils. */
	private static final Set<Integer> SEEN = new HashSet<>();

	private WebBehemothClient() {
	}

	static void init() {
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
	}

	/** Every behemoth's back tendrils (from WebLineRenderer, in the level pass). */
	static void render(LevelRenderContext context, ClientLevel level, Vec3 camera, float partialTick) {
		if (SEEN.isEmpty()) {
			return;
		}
		SEEN.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity host) || !host.hasAttached(WebBehemoth.HOST) || !host.isAlive());
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
		// a lash: which target, and how far through
		WebBehemoth.Strike strike = host.getAttached(WebBehemoth.STRIKE);
		Entity target = strike == null ? null : level.getEntity(strike.target());
		float swing = strike == null ? -1.0F : Mth.clamp((level.getGameTime() - strike.start() + partialTick) / WebBehemoth.STRIKE_TICKS, 0.0F, 1.0F);
		int light = net.minecraft.util.LightCoordsUtil.getLightCoords(level, BlockPos.containing(back));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(WebLineRenderer.TENDRIL_TEXTURE), (pose, buffer) -> {
			for (int t = 0; t < TENDRILS; t++) {
				double side = (t % 2 == 0 ? 1.0 : -1.0) * (0.6 + 0.4 * (t / 2));
				Vec3 root = back.add(right.scale(side * 0.12 * scale)).add(0.0, (0.15 - 0.12 * (t / 2)) * scale, 0.0);
				double length = LENGTH * scale * (1.0 - 0.12 * (t / 2));
				// at rest: up, back and out over the shoulders, never still
				double sway = Math.sin(time * 0.11 + t * 1.7) * 0.35 + Math.sin(time * 0.37 + t * 2.9) * 0.12;
				Vec3 bend = right.scale(side * 0.55 + sway).add(up.scale(0.7 + 0.2 * Math.cos(time * 0.09 + t)))
					.add(forward.scale(-0.55 + 0.15 * Math.sin(time * 0.13 + t * 0.7)));
				Vec3 tip = root.add(bend.normalize().scale(length));
				Vec3 mid = root.add(up.scale(length * 0.45)).add(right.scale(side * length * 0.2));
				if (target != null && t < 2 && swing >= 0.0F) {
					// the front two rear up and whip down onto the prey, then draw back
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
