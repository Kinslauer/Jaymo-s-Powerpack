package com.jaymoskingdoms.client;

import com.jaymoskingdoms.NpcEyes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (ey-dev) The dev view of an NPC's eyes ({@link NpcEyes.View}), drawn on the screen of the operator spectating it and nobody
 * else's: its view cone (green), its foe (a red line to one it sees, orange to one it can't), the spot it last saw its foe
 * (yellow), the spot it is going to search (cyan), the sound it stopped to listen to (violet), the spot a friend told it of
 * (blue), its path ahead (white, its end magenta when the path stops short of where it's going), its brain's walk target (pale
 * green), and above its head what it is focused on or thinking over ({@link com.jaymoskingdoms.NpcFocus}): the top line's colour
 * is its state (white focused, yellow picking what to do, orange re-planning its path, red stuck), and under it its alert, task,
 * activity, running goals and behaviours, target and path.
 */
public final class NpcEyesClient {
	/** The cone is drawn out to this many blocks at most; its rim has this many sides; the spokes from the eye to it. */
	static final double REACH = 12.0;
	static final int RIM = 24, SPOKES = 8;
	/** A spot's cross, the lines' width, and how high over the eyes the words float (blocks). */
	static final double CROSS = 0.4, LINE_WIDTH = 2.0, LABEL_RISE = 0.7;
	/** The words' scale, and how long (ticks) a view stays after the last one was sent. */
	static final float TEXT_SCALE = 0.025F;
	static final int STALE_TICKS = 3 * NpcEyes.DEV_EVERY;
	static final int CONE = 0xFF55FF55, SEEN = 0xFFFFE040, SPOT = 0xFF40E0F0, HEARD = 0xFFC060FF, TOLD = 0xFF6080FF, SEES = 0xFFFF4040, BLIND = 0xFFFFA030, WORDS = 0xFFFFFFFF;
	/** The path ahead, its end when it falls short, and the walk target. */
	static final int PATH = 0xFFE8E8E8, SHORT = 0xFFFF40D0, WALK = 0xFFA0FFA0;
	/** The top line's colour by state (NpcFocus: steady, thinking, re-planning, stuck), and the lines under it. */
	static final int[] STATE = {0xFFFFFFFF, 0xFFFFE040, 0xFFFFA030, 0xFFFF5050};
	static final int DETAIL = 0xFFC8C8C8;
	/** The path is drawn this far over its nodes' floors (blocks), and the lines' gap (font pixels). */
	static final double PATH_RISE = 0.15;
	static final int LINE_GAP = 1;

	/** The last view sent, and the game time it came. */
	private static NpcEyes.@Nullable View view;
	private static long viewAt;

	private NpcEyesClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(NpcEyes.View.TYPE, (payload, context) -> context.client().execute(() -> {
			ClientLevel level = context.client().level;
			view = payload.mob() < 0 || level == null ? null : payload;
			viewAt = level == null ? 0L : level.getGameTime();
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> view = null);
		LevelRenderEvents.COLLECT_SUBMITS.register(NpcEyesClient::render);
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		NpcEyes.View shown = view;
		if (shown == null || level == null || minecraft.player == null || !minecraft.player.isSpectator() || level.getGameTime() - viewAt > STALE_TICKS
			|| !(level.getEntity(shown.mob()) instanceof LivingEntity mob) || !mob.isAlive()) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 eye = mob.getEyePosition(partial).subtract(camera.pos), axis = mob.getViewVector(partial);
		Entity foe = shown.foe() >= 0 ? level.getEntity(shown.foe()) : null;
		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
			cone(buffer, pose, eye, axis, shown.cos(), Math.min(shown.range(), REACH));
			if (foe != null) {
				line(buffer, pose, eye, foe.getEyePosition(partial).subtract(camera.pos), shown.sees() ? SEES : BLIND);
			}
			cross(buffer, pose, shown.seen(), camera.pos, SEEN);
			cross(buffer, pose, shown.spot(), camera.pos, SPOT);
			cross(buffer, pose, shown.heard(), camera.pos, HEARD);
			cross(buffer, pose, shown.told(), camera.pos, TOLD);
			path(buffer, pose, mob.getPosition(partial).subtract(camera.pos), shown, camera.pos);
			cross(buffer, pose, shown.walk(), camera.pos, WALK);
		});
		label(context, camera, eye.add(0.0, LABEL_RISE, 0.0), shown);
	}

	/** The view cone: its spokes and its rim, {@code cos} the cosine of its half angle, {@code reach} blocks out. */
	private static void cone(VertexConsumer buffer, PoseStack.Pose pose, Vec3 eye, Vec3 axis, double cos, double reach) {
		Vec3 up = Math.abs(axis.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
		Vec3 u = axis.cross(up).normalize(), v = axis.cross(u).normalize();
		double sin = Math.sqrt(Math.max(0.0, 1.0 - cos * cos));
		Vec3 centre = eye.add(axis.scale(reach * cos));
		Vec3 last = null;
		for (int i = 0; i <= RIM; i++) {
			double a = i * (Math.PI * 2.0 / RIM);
			Vec3 rim = centre.add(u.scale(Math.cos(a) * reach * sin)).add(v.scale(Math.sin(a) * reach * sin));
			if (last != null) {
				line(buffer, pose, last, rim, CONE);
			}
			if (i < RIM && i % (RIM / SPOKES) == 0) {
				line(buffer, pose, eye, rim, CONE);
			}
			last = rim;
		}
	}

	/** The path still ahead, from its feet node to node, with a cross at its end (magenta if it falls short). */
	private static void path(VertexConsumer buffer, PoseStack.Pose pose, Vec3 feet, NpcEyes.View shown, Vec3 camera) {
		if (shown.path().isEmpty()) {
			return;
		}
		Vec3 last = feet.add(0.0, PATH_RISE, 0.0);
		for (BlockPos node : shown.path()) {
			Vec3 at = Vec3.atBottomCenterOf(node).add(0.0, PATH_RISE, 0.0).subtract(camera);
			line(buffer, pose, last, at, PATH);
			last = at;
		}
		cross(buffer, pose, last.add(camera), camera, shown.reaches() ? PATH : SHORT);
	}

	/** A three-way cross where {@code at} is. */
	private static void cross(VertexConsumer buffer, PoseStack.Pose pose, @Nullable Vec3 at, Vec3 camera, int color) {
		if (at == null) {
			return;
		}
		Vec3 c = at.subtract(camera);
		line(buffer, pose, c.add(CROSS, 0.0, 0.0), c.add(-CROSS, 0.0, 0.0), color);
		line(buffer, pose, c.add(0.0, CROSS, 0.0), c.add(0.0, -CROSS, 0.0), color);
		line(buffer, pose, c.add(0.0, 0.0, CROSS), c.add(0.0, 0.0, -CROSS), color);
	}

	private static void line(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, int color) {
		Vec3 d = to.subtract(from);
		double length = d.length();
		if (length < 1.0E-4) {
			return;
		}
		float nx = (float) (d.x / length), ny = (float) (d.y / length), nz = (float) (d.z / length);
		buffer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth((float) LINE_WIDTH);
		buffer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth((float) LINE_WIDTH);
	}

	/** What it's focused on or thinking over, and why, over its head facing the camera: the top line first, the last just over the eyes. */
	private static void label(LevelRenderContext context, CameraRenderState camera, Vec3 at, NpcEyes.View shown) {
		Font font = Minecraft.getInstance().font;
		List<Component> lines = shown.lines();
		if (lines.isEmpty()) {
			// (a view without a focus: the fight senses' word alone)
			Component text = Component.translatable("gui.jaymoskingdoms.eyes.why." + shown.why());
			if (shown.blind() > 0) {
				text = Component.translatable("gui.jaymoskingdoms.eyes.blind", text, String.format("%.1f", shown.blind() / 20.0));
			}
			lines = List.of(text);
		}
		int state = shown.state() >= 0 && shown.state() < STATE.length ? shown.state() : 0;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(at.x, at.y, at.z);
		poseStack.rotate(camera.orientation);
		poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
		int step = font.lineHeight + LINE_GAP, n = lines.size();
		for (int i = 0; i < n; i++) {
			FormattedCharSequence words = lines.get(i).getVisualOrderText();
			float x = -font.width(words) / 2.0F, y = (i - (n - 1)) * step;
			int color = i == 0 && !shown.lines().isEmpty() ? STATE[state] : shown.lines().isEmpty() ? WORDS : DETAIL;
			context.submitNodeCollector().submitText(poseStack, x, y, words, false, Font.DisplayMode.SEE_THROUGH, 0xF000F0, color, 0x80000000, 0);
		}
		poseStack.popPose();
	}
}
