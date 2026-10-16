package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebBehemoth;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
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
	/**
	 * Back tendrils (Andy: "enemies mobs affected by the symbiote should also get the living armor tendrils"): Living
	 * Armor's own (WebTendrilLayer), out of the back of every mob the symbiote rides. Rooted this share of the mob's
	 * height up (upright), or on top of its back for a four-legged one ({@code QUADRUPED_ROOT}, when it's wider than
	 * {@code QUADRUPED_WIDE} of its height); this much of its width behind its middle. As big as a player's on a mob
	 * {@code SIZE_HEIGHT} tall (between {@code SIZE_MIN} and {@code SIZE_MAX}); a behemoth's sit out on its big body.
	 */
	private static final float UPRIGHT_ROOT = 0.77F, QUADRUPED_ROOT = 0.95F, QUADRUPED_WIDE = 0.75F, BACK_DEPTH = 0.25F;
	private static final float SIZE_HEIGHT = 1.95F, SIZE_MIN = 0.35F, SIZE_MAX = 2.0F, BEHEMOTH_DEPTH = 1.0F;
	/** They grow out over this long (ticks) once a mob's taken; a hostile host's (and a behemoth's) are wild, a peaceful one's half so. */
	private static final float GROW_TICKS = 30.0F, FERAL_HOSTILE = 1.0F, FERAL_PEACEFUL = 0.5F;
	/** A lash swings the tendrils on its side (reared until this share of it); a slam swings them all, stretched long. */
	private static final float LASH_REAR = 0.25F;

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

	/** On a ridden mob's render state: its back tendrils (WebTendrilLayer.submitOnMob). */
	public static final RenderStateDataKey<WebSymbioteClient.Tendrils> MOB_TENDRILS = RenderStateDataKey.create(() -> "jaymospowers:web_mob_tendrils");

	/** When each ridden mob was first seen ridden (its age, ticks): its tendrils grow out from then. */
	private static final Map<Entity, Float> FIRST_SEEN = new WeakHashMap<>();

	private WebBehemothClient() {
	}

	static void init() {
		// any mob can be inhabited: every living renderer gets the suit layer
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> helper.register(inhabited(renderer)));
		if (FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			Kitchen.init();
		}
	}

	/** From EntityRenderer.extractRenderState: marks a behemoth or a ridden mob for its suit layer, with its back tendrils. */
	public static void extract(Entity entity, EntityRenderState state) {
		boolean behemoth = entity.hasAttached(WebBehemoth.HOST);
		boolean inhabited = com.jaymospowers.power.web.WebInhabit.isHost(entity);
		state.setData(HOST, behemoth ? Boolean.TRUE : null);
		state.setData(INHABITED, inhabited ? Boolean.TRUE : null);
		if ((behemoth || inhabited) && entity instanceof LivingEntity host && host.isAlive()) {
			state.setData(MOB_TENDRILS, tendrils(host, behemoth || host instanceof net.minecraft.world.entity.monster.Enemy));
		} else {
			FIRST_SEEN.remove(entity);
			state.setData(MOB_TENDRILS, null);
		}
	}

	/** Every behemoth's back tendrils used to be drawn here (from WebLineRenderer); they're WebTendrilLayer's now, on the mob's own model. */
	static void render(LevelRenderContext context, ClientLevel level, Vec3 camera, float partialTick) {
	}

	/**
	 * A ridden mob's back tendrils, as a wearer's are worked out (WebSymbioteClient): grown since it was first seen
	 * ridden; swinging with its newest lash (the tendrils on the target's side rear and whip at it) or, over all of
	 * them, its tendril slam (reared as the bundle snatches, crashing down with the prey).
	 */
	private static WebSymbioteClient.Tendrils tendrils(LivingEntity host, boolean hostile) {
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float age = host.tickCount + partial;
		float first = FIRST_SEEN.computeIfAbsent(host, h -> age);
		float growth = Mth.clamp((age - first) / GROW_TICKS, 0.0F, 1.0F);
		long now = host.level().getGameTime();
		float strike = -1.0F, rear = LASH_REAR, hit = WebBehemoth.STRIKE_HIT / (float)WebBehemoth.STRIKE_TICKS;
		boolean heavy = false;
		Entity target = null;
		WebBehemoth.Slam slam = host.getAttached(WebBehemoth.SLAM);
		if (slam != null && now - slam.start() < WebBehemoth.SLAM_TICKS) {
			target = host.level().getEntity(slam.target());
			strike = Mth.clamp((now - slam.start() + partial) / WebBehemoth.SLAM_TICKS, 0.0F, 1.0F);
			rear = WebBehemoth.SLAM_REACHED / (float)WebBehemoth.SLAM_TICKS;
			hit = WebBehemoth.SLAM_HIT / (float)WebBehemoth.SLAM_TICKS;
			heavy = true;
		}
		if (target == null) {
			WebBehemoth.Strike newest = null;
			for (WebBehemoth.Strike s : host.getAttachedOrElse(WebBehemoth.STRIKE, java.util.List.<WebBehemoth.Strike>of())) {
				if (now - s.start() < WebBehemoth.STRIKE_TICKS && (newest == null || s.start() > newest.start())) {
					newest = s;
				}
			}
			target = newest == null ? null : host.level().getEntity(newest.target());
			strike = target == null ? -1.0F : Mth.clamp((now - newest.start() + partial) / WebBehemoth.STRIKE_TICKS, 0.0F, 1.0F);
			rear = LASH_REAR;
			hit = WebBehemoth.STRIKE_HIT / (float)WebBehemoth.STRIKE_TICKS;
			heavy = false;
		}
		float aimYaw = 0.0F, aimPitch = 0.0F, side = 1.0F;
		if (target != null) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(host.getEyePosition(partial));
			float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
			aimYaw = Mth.wrapDegrees(yaw - host.yBodyRot) * Mth.DEG_TO_RAD;
			aimPitch = (float)Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z));
			side = aimYaw >= 0.0F ? -1.0F : 1.0F;
		}
		return new WebSymbioteClient.Tendrils(growth, age, strike, aimYaw, aimPitch, side, 0.0F, 0, true, 0, 0.0F, 0.0F, heavy,
			com.jaymospowers.power.web.WebSymbiote.STRIKE_SLAM, false, -1, rear, hit, hostile ? FERAL_HOSTILE : FERAL_PEACEFUL, -1.0F, null, null);
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
			if (state.isInvisible) {
				return;
			}
			if (state.getData(INHABITED) != null) {
				renderColoredCutoutModel(this.getParentModel(), GOO, poseStack, collector, lightCoords, state, -1, 1);
			}
			WebSymbioteClient.Tendrils tendrils = state.getData(MOB_TENDRILS);
			if (tendrils != null) {
				// sized to the mob's own model: its box, without its scale (the pose stack has that already)
				float scale = state.scale > 0.0F ? state.scale : 1.0F;
				float height = state.boundingBoxHeight / scale, width = state.boundingBoxWidth / scale;
				boolean fourLegs = width > QUADRUPED_WIDE * height;
				float rootY = 24.0F - (fourLegs ? QUADRUPED_ROOT : UPRIGHT_ROOT) * height * 16.0F;
				float size = Mth.clamp(height / SIZE_HEIGHT, SIZE_MIN, SIZE_MAX);
				float rootZ = fourLegs ? 0.0F : Math.max(0.0F, width * 16.0F * BACK_DEPTH - 2.0F * size)
					+ (state.getData(HOST) != null ? BEHEMOTH_DEPTH : 0.0F);
				WebTendrilLayer.submitOnMob(poseStack, collector, lightCoords, state, tendrils, rootY, rootZ, size);
			}
		}
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
