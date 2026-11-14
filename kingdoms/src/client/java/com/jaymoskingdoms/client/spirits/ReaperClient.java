package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Reaper;
import com.jaymoskingdoms.client.ManaClient;
import com.jaymoskingdoms.client.hud.HudZones;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Reaper, as it is seen ({@link Reaper} has the rules). Andy: "it should be visible for ANYONE who is near death, not the
 * attuned, because this isnt ki. its death itself", and it "appears like the mana spirits, whenever a ghost is nearby, and is
 * animated harvesting it with a scythe". Drawn in the world from the synced {@link Reaper#REAPER} state alone, for every
 * being that carries one within {@link #VIEW_RANGE}, when the viewer is near death ({@link #nearDeath}) or it is harvesting:
 * <ul>
 * <li>the Pall and the Mantle: smoke off the shoulders, then a mantle of it;</li>
 * <li>from the Reach: the hooded figure looming over its keeper's back, Minecraft Comes Alive's Grim Reaper ({@link McaReaper});</li>
 * <li>from the Wing-arms: the Grue's wings out of its back, opening when it strikes;</li>
 * <li>the Shroud: the wings folded, and shadowflame round its keeper.</li>
 * </ul>
 * Harvesting a ghost, it leaves its keeper's back and stands over the ghost, the scythe drawn back and brought down, and
 * everyone sees it. Its keeper, in health, has only a line on the HUD: the stage, the Toll, the dead near by.
 */
public final class ReaperClient {
	/** How far away a Reaper is drawn at all (blocks). */
	static final double VIEW_RANGE = 48.0;
	/** The figure over its keeper's back: how far behind and how far lifted (blocks), its size, and how much it grows per body near by. */
	static final float BEHIND = 0.5F, LIFT = 0.55F, SIZE = 1.3F, SIZE_PER_DEAD = 0.03F;
	/** How solid it is over the back, and harvesting (alpha). */
	static final float LOOM_ALPHA = 0.8F, HARVEST_ALPHA = 0.95F;
	/** Harvesting: how far from the ghost it stands (blocks), and the share of the harvest the scythe is drawn back for. */
	static final double HARVEST_STAND = 1.3;
	static final float HARVEST_DRAW = 0.7F;
	/** The wings: their size over the figure's, how long they stay open after it strikes (ticks), and their pose folded and open (radians). */
	static final float WING_SIZE = 1.7F, OPEN_TICKS = 30.0F;
	static final float FOLD_SWEEP = 1.15F, OPEN_SWEEP = 0.45F, FOLD_DROOP = 0.9F, OPEN_DROOP = -0.25F, WING_BEAT = 0.2F, WING_SPEED = 0.12F;
	/** The wings' body (dark, see-through) and their red edge's light. */
	static final float WING_ALPHA = 0.7F, WING_EDGE = 0.55F;
	static final int EDGE = 0xFFE0142E, CORE = 0xFF2A0006;
	/** The HUD line's colour. */
	static final int HUD = 0xFFC8404A;
	static final int FULL_BRIGHT = 0xF000F0;
	/** After the spirits' figures. */
	static final int ORDER = SpiritManifest.ORDER + 12;
	static final RenderStateDataKey<Float> OPEN = RenderStateDataKey.create(() -> "jaymoskingdoms:reaper_wings");

	private static final boolean MCA = FabricLoader.getInstance().isModLoaded("mca");
	private static final ParticleOptions SMOKE = Fx.miasma(Reaper.SMOKE, 1.3F), THICK = Fx.miasma(Reaper.SMOKE, 2.0F), EMBER = Fx.spark(Reaper.RED, 0.8F);
	private static WingModel wings;

	private ReaperClient() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(ReaperClient::render);
		ClientTickEvents.END_CLIENT_TICK.register(ReaperClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, JaymosKingdoms.id("reaper_line"), ReaperClient::line);
	}

	/** Whether {@code viewer} is near death: low on health, or down. */
	static boolean nearDeath(LivingEntity viewer) {
		return viewer.getHealth() <= viewer.getMaxHealth() * Reaper.NEAR_DEATH || PowersCompat.downedState(viewer) > 0;
	}

	/** How far through its harvest {@code state} is at {@code now} (0-1), or -1 when it isn't harvesting. */
	private static float harvest(Reaper.State state, double now) {
		if (state.harvestAt() <= 0L) {
			return -1.0F;
		}
		float k = (float)(now - state.harvestAt()) / Reaper.HARVEST_TICKS;
		return k < 0.0F || k > 1.2F ? -1.0F : Math.min(k, 1.0F);
	}

	// ---- drawing -------------------------------------------------------------------------------------------------------

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer viewer = minecraft.player;
		if (level == null || viewer == null) {
			return;
		}
		boolean dying = nearDeath(viewer);
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double now = level.getGameTime() + partial;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		for (Entity e : level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity keeper) || keeper.distanceToSqr(camera) > VIEW_RANGE * VIEW_RANGE) {
				continue;
			}
			Reaper.State state = keeper.getAttached(Reaper.REAPER);
			if (state == null) {
				continue;
			}
			float harvest = harvest(state, now);
			Entity ghost = harvest >= 0.0F ? level.getEntity(state.harvestId()) : null;
			if (ghost != null) {
				drawHarvest(context, keeper, ghost, harvest, partial, camera, now);
				continue;
			}
			int stage = Reaper.stage(state);
			if (!dying || stage < Reaper.FIGURE_STAGE || keeper.isInvisible() || keeper == minecraft.getCameraEntity() && firstPerson) {
				continue;
			}
			drawLooming(context, keeper, state, stage, partial, camera, now);
		}
	}

	/** Over its keeper's back: the hooded figure, and from the Wing-arms on, its wings. */
	private static void drawLooming(LevelRenderContext context, LivingEntity keeper, Reaper.State state, int stage, float partial, Vec3 camera, double now) {
		Vec3 at = keeper.getPosition(partial).subtract(camera);
		float bodyRot = Mth.rotLerp(partial, keeper.yBodyRotO, keeper.yBodyRot);
		float size = SIZE + SIZE_PER_DEAD * state.dead();
		float age = (float)(now % 100000.0) + (keeper.getId() & 63);
		PoseStack pose = context.poseStack();
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		pose.scale(keeper.getScale(), keeper.getScale(), keeper.getScale());
		pose.rotateDegrees(Axis.YP, 180.0F - bodyRot);
		pose.scale(-1.0F, -1.0F, 1.0F);
		pose.scale(SpiritArms.PLAYER_SCALE, SpiritArms.PLAYER_SCALE, SpiritArms.PLAYER_SCALE);
		pose.translate(0.0F, SpiritArms.MODEL_DROP, 0.0F);
		// (model space: y runs down, and behind its keeper is +z)
		pose.translate(0.0F, -LIFT - 0.04F * Mth.sin(age * 0.07F), BEHIND);
		pose.scale(size, size, size);
		SubmitNodeCollector collector = context.submitNodeCollector();
		if (MCA) {
			McaReaper.submit(collector, pose, age, McaReaper.REST, ARGB.colorFromFloat(LOOM_ALPHA, 1.0F, 1.0F, 1.0F), ORDER);
		}
		if (stage >= Reaper.WINGS_STAGE) {
			// open while it strikes; at the Shroud, folded otherwise
			float since = (float)(now - state.actAt());
			float struck = state.actAt() > 0L && since >= 0.0F ? Mth.clamp(1.0F - (since - OPEN_TICKS * 0.5F) / (OPEN_TICKS * 0.5F), 0.0F, 1.0F) : 0.0F;
			float open = stage >= Reaper.SHROUD_STAGE ? struck : 0.6F + 0.4F * struck;
			drawWings(collector, pose, age, open);
		}
		pose.popPose();
	}

	private static void drawWings(SubmitNodeCollector collector, PoseStack pose, float age, float open) {
		if (wings == null) {
			wings = new WingModel(LayerDefinition.create(wingMesh(), 64, 64).bakeRoot());
		}
		HumanoidRenderState state = new HumanoidRenderState();
		state.ageInTicks = age;
		state.setData(OPEN, open);
		pose.pushPose();
		pose.scale(WING_SIZE, WING_SIZE, WING_SIZE);
		collector.order(ORDER + 1).submitModel(wings, state, pose, RenderTypes.entityTranslucent(ManaClient.TEXTURE), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
			ARGB.color((int)(WING_ALPHA * 255.0F), ARGB.red(CORE), ARGB.green(CORE), ARGB.blue(CORE)), null, 0);
		float rise = (age * ManaClient.RISE) % 1.0F;
		collector.order(ORDER + 2).submitModel(wings, state, pose, RenderTypes.energySwirl(ManaClient.TEXTURE, 0.21F, -rise), FULL_BRIGHT,
			OverlayTexture.NO_OVERLAY, ManaClient.tint(EDGE, WING_EDGE), null, 0);
		pose.popPose();
	}

	/** Harvesting: it stands over the ghost, between it and its keeper, the scythe drawn back and then brought down. */
	private static void drawHarvest(LevelRenderContext context, LivingEntity keeper, Entity ghost, float harvest, float partial, Vec3 camera, double now) {
		if (!MCA) {
			return;
		}
		Vec3 g = ghost.getPosition(partial);
		Vec3 from = keeper.getPosition(partial).subtract(g);
		Vec3 flat = new Vec3(from.x, 0.0, from.z);
		Vec3 side = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
		Vec3 at = g.add(side.scale(HARVEST_STAND));
		// (it faces the ghost)
		float yaw = (float)(Mth.atan2(-side.z, -side.x) * Mth.RAD_TO_DEG) - 90.0F;
		float formed = Mth.clamp(harvest / 0.15F, 0.0F, 1.0F);
		PoseStack pose = context.poseStack();
		pose.pushPose();
		pose.translate(at.x - camera.x, at.y - camera.y + 0.1, at.z - camera.z);
		pose.rotateDegrees(Axis.YP, 180.0F - yaw);
		pose.scale(-1.0F, -1.0F, 1.0F);
		pose.scale(SpiritArms.PLAYER_SCALE, SpiritArms.PLAYER_SCALE, SpiritArms.PLAYER_SCALE);
		pose.translate(0.0F, SpiritArms.MODEL_DROP, 0.0F);
		pose.translate(0.0F, -(SIZE - 1.0F) * 1.5F, 0.0F);
		pose.scale(SIZE, SIZE, SIZE);
		McaReaper.submit(context.submitNodeCollector(), pose, (float)(now % 100000.0), harvest < HARVEST_DRAW ? McaReaper.DRAWN : McaReaper.DOWN,
			ARGB.colorFromFloat(HARVEST_ALPHA * formed, 1.0F, 1.0F, 1.0F), ORDER);
		pose.popPose();
	}

	// ---- its smoke -----------------------------------------------------------------------------------------------------

	/** Each tick: the smoke and the shadowflame of every Reaper the viewer can see. */
	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer viewer = minecraft.player;
		if (level == null || viewer == null || minecraft.isPaused()) {
			return;
		}
		boolean dying = nearDeath(viewer);
		double now = level.getGameTime();
		RandomSource random = level.getRandom();
		for (Entity e : level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity keeper) || keeper.distanceToSqr(viewer) > VIEW_RANGE * VIEW_RANGE) {
				continue;
			}
			Reaper.State state = keeper.getAttached(Reaper.REAPER);
			if (state == null) {
				continue;
			}
			Entity ghost = harvest(state, now) >= 0.0F ? level.getEntity(state.harvestId()) : null;
			if (ghost != null) {
				// the ghost comes apart toward the scythe
				level.addParticle(SMOKE, ghost.getRandomX(0.4), ghost.getRandomY(), ghost.getRandomZ(0.4), 0.0, 0.03, 0.0);
				continue;
			}
			if (!dying || keeper.isInvisible()) {
				continue;
			}
			smoke(level, keeper, Reaper.stage(state), state.dead(), random);
		}
	}

	private static void smoke(ClientLevel level, LivingEntity keeper, int stage, int dead, RandomSource random) {
		float yaw = keeper.yBodyRot * Mth.DEG_TO_RAD;
		double bx = Mth.sin(yaw) * 0.3, bz = -Mth.cos(yaw) * 0.3;
		double sx = Mth.cos(yaw), sz = Mth.sin(yaw);
		double shoulder = keeper.getY() + keeper.getBbHeight() * 0.8;
		// the Pall: smoke off the shoulders; the Mantle and on: more of it, and higher
		int puffs = stage == 1 ? (random.nextInt(3) == 0 ? 1 : 0) : Math.min(stage, 3);
		for (int i = 0; i < puffs; i++) {
			double side = (random.nextDouble() * 2.0 - 1.0) * (stage == 1 ? 0.35 : 0.5);
			level.addParticle(stage >= 2 ? THICK : SMOKE, keeper.getX() + bx + sx * side, shoulder + random.nextDouble() * (stage >= 2 ? 0.7 : 0.25),
				keeper.getZ() + bz + sz * side, 0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
		}
		if (stage >= Reaper.SHROUD_STAGE) {
			// the Shroud: black fire climbing a cloak's width round its keeper, with a few live coals in it
			for (int i = 0; i < 4 + dead / 2; i++) {
				double a = random.nextDouble() * Math.PI * 2.0, r = 0.45 + random.nextDouble() * 0.2;
				double x = keeper.getX() + Math.cos(a) * r, z = keeper.getZ() + Math.sin(a) * r;
				level.addParticle(random.nextInt(5) == 0 ? EMBER : THICK, x, keeper.getY() + random.nextDouble() * keeper.getBbHeight() * 0.9, z, 0.0,
					0.04 + random.nextDouble() * 0.04, 0.0);
			}
		}
	}

	// ---- the HUD -------------------------------------------------------------------------------------------------------

	/** Its keeper's line: the stage it has grown to, the Toll, the dead near by. */
	private static void line(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Reaper.State state = player == null ? null : player.getAttached(Reaper.REAPER);
		if (state == null) {
			return;
		}
		Component text = Component.translatable("gui.jaymoskingdoms.reaper.line", Component.translatable("gui.jaymoskingdoms.reaper.stage." + Reaper.stage(state)),
			state.toll(), state.dead());
		int[] at = HudZones.place(HudZones.FOCUS, "jaymoskingdoms:reaper_line", 30, minecraft.font.width(text), 9);
		if (at != null) {
			graphics.text(minecraft.font, text, at[0], at[1], HUD, true);
		}
	}

	// ---- the wings -----------------------------------------------------------------------------------------------------

	/** The Grue's wings: an arm bone reaching out and up, long finger bones, a torn membrane between them (as its own figure's are). */
	private static MeshDefinition wingMesh() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition root = mesh.getRoot();
		for (String part : new String[] {"head", "right_arm", "left_arm", "right_leg", "left_leg"}) {
			root.addOrReplaceChild(part, CubeListBuilder.create(), PartPose.ZERO);
		}
		root.getChild("head").addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		for (int w = 0; w < 2; w++) {
			float s = w == 0 ? 1.0F : -1.0F;
			CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
			box(c, 0.0F, -0.8F, 1.6F, 1.6F, 1.6F);
			box(c, s * -4.0F, -2.4F, 8.0F, 1.4F, 1.4F);
			box(c, s * -9.0F, -5.0F, 3.0F, 1.2F, 1.2F);
			box(c, s * -10.6F, -5.6F, 1.4F, 1.4F, 1.4F);
			box(c, s * -11.2F, -11.0F, 1.0F, 6.0F, 1.0F);
			box(c, s * -12.0F, -13.2F, 1.6F, 2.2F, 1.0F);
			box(c, s * -15.6F, -6.0F, 9.0F, 1.0F, 1.0F);
			box(c, s * -14.4F, -3.6F, 1.0F, 12.0F, 1.0F);
			c.addBox(s * -7.0F - 6.0F, -1.4F, 0.1F, 12.0F, 7.0F, 0.4F);
			c.addBox(s * -13.0F - 3.0F, -4.6F, 0.1F, 6.0F, 12.0F, 0.4F);
			body.addOrReplaceChild(w == 0 ? "wing_right" : "wing_left", c, PartPose.offset(s * -1.6F, 1.2F, 2.2F));
		}
		return mesh;
	}

	/** A box centred on x {@code cx} (and on z 0), from {@code y} down {@code h}. */
	private static void box(CubeListBuilder c, float cx, float y, float w, float h, float d) {
		c.addBox(cx - w / 2.0F, y, -d / 2.0F, w, h, d);
	}

	/** Both wings: a HumanoidModel (as the spirits' figures are) whose only visible part is the body that carries them. */
	private static final class WingModel extends HumanoidModel<HumanoidRenderState> {
		private final ModelPart right, left;

		WingModel(ModelPart root) {
			super(root);
			this.right = this.body.getChild("wing_right");
			this.left = this.body.getChild("wing_left");
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			resetPose();
			this.head.visible = false;
			this.hat.visible = false;
			this.rightArm.visible = false;
			this.leftArm.visible = false;
			this.rightLeg.visible = false;
			this.leftLeg.visible = false;
			Float data = state.getData(OPEN);
			this.body.visible = data != null;
			if (data == null) {
				return;
			}
			float open = data;
			float beat = Mth.sin(state.ageInTicks * WING_SPEED) * WING_BEAT * open;
			float sweep = Mth.lerp(open, FOLD_SWEEP, OPEN_SWEEP), droop = Mth.lerp(open, FOLD_DROOP, OPEN_DROOP) + beat;
			this.right.yRot = sweep;
			this.left.yRot = -sweep;
			this.right.zRot = -droop;
			this.left.zRot = droop;
		}
	}
}
