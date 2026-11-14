package com.jaymoskingdoms.client.forms;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.ShackleTether;
import com.jaymoskingdoms.forms.Lasso;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * The lasso's rope as everyone sees it, drawn the way Jaymo's Powers draws the whip's lash. Andy: "the chain axe and
 * lasso should operate similar to the [whip] and flail, therefore they need blockbench models". In the hand the lasso
 * is its coil (a model); the rope is a line of points on Verlet physics ({@link Line}: gravity, drag, fixed links,
 * resting on blocks), stepped every tick, from the top of the coil as it was really drawn ({@link LassoSockets}) to:
 * <ul>
 * <li>nothing, while the lasso is only held: a short tail with the loop (a model) swinging on its end;</li>
 * <li>the loop in flight ({@link Lasso.Loop}), the loop's model leading the rope;</li>
 * <li>the one it holds ({@link Lasso#HOLDER}), with a noose round their middle.</li>
 * </ul>
 * The server's rules (reach, pulls, breaking out) are untouched: this is only how the rope looks.
 */
public final class LassoRope {
	/** The held one's rope is tied at this share of its height; the noose stands this far off them, this thick. */
	static final double TIED_AT = 0.6, NOOSE_GAP = 0.08, NOOSE_THICK = 0.035;
	/** With no coil drawn to take it from: how far below the eyes, and ahead of them, the holder's hand is (blocks). */
	static final double HAND_DROP = 0.45, HAND_AHEAD = 0.35;
	/** The same for someone else, off their body: this share of their height up, this far to the arm's side and ahead. */
	static final double HAND_HEIGHT = 0.42, HAND_SIDE = 0.38, HAND_FRONT = 0.45;

	// ---- the physics ----------------------------------------------------------------------------------------------------
	/** Points along the rope, and passes over its links a tick. */
	static final int POINTS = 16, PASSES = 12;
	/** Pull down (blocks a tick, a tick), and what a point keeps of its speed each tick. */
	static final double GRAVITY = 0.04, DRAG = 0.9;
	/** The tail an idle lasso hangs; a rope to something is this much longer than the straight line, and at least this long. */
	static final double IDLE_LENGTH = 0.7, SLACK = 1.08, LEAST = 0.5;
	/** The rope reels in or pays out this share of the way to its length a tick; a point this far off its line is put back. */
	static final double REEL = 0.35, SNAP = 8.0;
	/** The first and last points take up the difference between the tick's ends and this frame's, fading over this many. */
	static final int END_BLEND = 5;
	/** No ropes for holders further off than this. */
	static final double RANGE = 64.0;

	/** The loop on the rope's end: the item model drawn there, and its size (1: a block a model's 16 px). */
	static final Identifier LOOP_MODEL = JaymosKingdoms.id("lasso_loop");
	static final float LOOP_SCALE = 0.9F;

	/** What a rope's far end is on: nothing, a loop in flight, someone held. */
	private record End(@Nullable Entity on) {
	}

	/** Ropes by their holder's entity id. */
	private static final Map<Integer, Line> LINES = new HashMap<>();

	private LassoRope() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(LassoRope::render);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null || minecraft.player == null) {
				LINES.clear();
				LassoSockets.clear();
			} else if (!minecraft.isPaused()) {
				tick(minecraft, minecraft.level);
			}
		});
	}

	// ---- whose ropes ---------------------------------------------------------------------------------------------------

	/** Every rope there is to draw: its holder's id, and what its far end is on. */
	private static Map<Integer, End> ends(ClientLevel level) {
		Map<Integer, End> ends = new HashMap<>();
		for (Player player : level.players()) {
			if (!player.isSpectator() && (player.getMainHandItem().is(Lasso.ITEM) || player.getOffhandItem().is(Lasso.ITEM))) {
				ends.put(player.getId(), new End(null));
			}
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof Lasso.Loop loop && loop.getOwner() != null) {
				ends.put(loop.getOwner().getId(), new End(loop));
			} else if (entity instanceof LivingEntity held && held.getAttached(Lasso.HOLDER) != null) {
				ends.put(held.getAttached(Lasso.HOLDER), new End(held));
			}
		}
		return ends;
	}

	/** Where the rope is fastened to what it is on. */
	private static Vec3 fastened(Entity on, float partialTick) {
		return on.getPosition(partialTick).add(0.0, on instanceof Lasso.Loop ? on.getBbHeight() * 0.5 : on.getBbHeight() * TIED_AT, 0.0);
	}

	/**
	 * Where the rope leaves the holder's hand: the top of the coil as it was last drawn; with none drawn (the lasso in the
	 * pack or the off hand), roughly the hand.
	 */
	private static Vec3 hand(Minecraft minecraft, LivingEntity holder, float partialTick) {
		boolean first = holder == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		Vec3 offset = LassoSockets.offset(holder, first);
		if (offset != null) {
			return holder.getPosition(partialTick).add(offset);
		}
		if (first) {
			return holder.getEyePosition(partialTick).subtract(0.0, HAND_DROP, 0.0).add(holder.getViewVector(partialTick).scale(HAND_AHEAD));
		}
		float yaw = Mth.rotLerp(partialTick, holder.yBodyRotO, holder.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 ahead = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw)), right = new Vec3(-ahead.z, 0.0, ahead.x);
		double side = holder.getMainArm() == HumanoidArm.RIGHT == holder.getMainHandItem().is(Lasso.ITEM) ? HAND_SIDE : -HAND_SIDE;
		return holder.getPosition(partialTick).add(0.0, holder.getBbHeight() * HAND_HEIGHT, 0.0).add(right.scale(side)).add(ahead.scale(HAND_FRONT));
	}

	// ---- the physics ----------------------------------------------------------------------------------------------------

	/** One rope: its points now and a tick ago, and the length it is paid out to. */
	private static final class Line {
		final Vec3[] now = new Vec3[POINTS], old = new Vec3[POINTS];
		double length;
		/** Whether its far end was fastened to something at the last step. */
		boolean fast;

		Line(Vec3 hand, double length) {
			this.length = length;
			this.hang(hand);
		}

		private void hang(Vec3 hand) {
			for (int i = 0; i < POINTS; i++) {
				this.now[i] = this.old[i] = hand.add(0.0, -this.length * i / (POINTS - 1), 0.0);
			}
		}

		/** A tick: the hand at {@code hand}, the far end at {@code end} (null: swinging free), the rope {@code want} long. */
		void step(Level level, Vec3 hand, @Nullable Vec3 end, double want) {
			if (this.now[0].distanceToSqr(hand) > SNAP * SNAP) {
				this.hang(hand);
			}
			this.length += (want - this.length) * REEL;
			this.fast = end != null;
			int last = POINTS - 1;
			for (int i = 1; i <= (this.fast ? last - 1 : last); i++) {
				Vec3 next = this.now[i].add(this.now[i].subtract(this.old[i]).scale(DRAG)).add(0.0, -GRAVITY, 0.0);
				this.old[i] = this.now[i];
				this.now[i] = next;
			}
			this.old[0] = this.now[0];
			this.now[0] = hand;
			if (end != null) {
				this.old[last] = this.now[last];
				this.now[last] = end;
			}
			double link = this.length / last;
			for (int pass = 0; pass < PASSES; pass++) {
				for (int i = 0; i < last; i++) {
					Vec3 gap = this.now[i + 1].subtract(this.now[i]);
					double far = gap.length();
					if (far < 1.0E-6) {
						continue;
					}
					Vec3 fix = gap.scale((far - link) / far);
					boolean fixedA = i == 0, fixedB = this.fast && i + 1 == last;
					if (fixedA && fixedB) {
						continue;
					} else if (fixedA) {
						this.now[i + 1] = this.now[i + 1].subtract(fix);
					} else if (fixedB) {
						this.now[i] = this.now[i].add(fix);
					} else {
						this.now[i] = this.now[i].add(fix.scale(0.5));
						this.now[i + 1] = this.now[i + 1].subtract(fix.scale(0.5));
					}
				}
			}
			for (int i = 1; i <= (this.fast ? last - 1 : last); i++) {
				this.now[i] = rest(level, this.now[i]);
			}
		}

		/** A point that has sunk into a block lies on top of it. */
		private static Vec3 rest(Level level, Vec3 at) {
			BlockPos pos = BlockPos.containing(at);
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (shape.isEmpty()) {
				return at;
			}
			double top = pos.getY() + shape.max(Direction.Axis.Y);
			return at.y < top && at.y >= pos.getY() + shape.min(Direction.Axis.Y) ? new Vec3(at.x, top + 0.02, at.z) : at;
		}

		/** The rope this frame: between the ticks, its ends where the hand and the fastening are drawn now. */
		Vec3[] frame(Vec3 hand, @Nullable Vec3 end, float partialTick) {
			Vec3[] line = new Vec3[POINTS];
			for (int i = 0; i < POINTS; i++) {
				line[i] = this.old[i].lerp(this.now[i], partialTick);
			}
			Vec3 handOff = hand.subtract(line[0]), endOff = end != null && this.fast ? end.subtract(line[POINTS - 1]) : Vec3.ZERO;
			for (int i = 0; i < END_BLEND; i++) {
				double share = 1.0 - i / (double)END_BLEND;
				line[i] = line[i].add(handOff.scale(share));
				line[POINTS - 1 - i] = line[POINTS - 1 - i].add(endOff.scale(share));
			}
			return line;
		}
	}

	private static void tick(Minecraft minecraft, ClientLevel level) {
		Map<Integer, End> ends = ends(level);
		LINES.keySet().retainAll(ends.keySet());
		Vec3 eye = minecraft.player.position();
		for (Map.Entry<Integer, End> entry : ends.entrySet()) {
			if (!(level.getEntity(entry.getKey()) instanceof LivingEntity holder) || holder.distanceToSqr(eye) > RANGE * RANGE) {
				LINES.remove(entry.getKey());
				continue;
			}
			Vec3 hand = hand(minecraft, holder, 1.0F);
			Entity on = entry.getValue().on();
			Vec3 end = on == null ? null : fastened(on, 1.0F);
			double want = end == null ? IDLE_LENGTH : Math.max(LEAST, hand.distanceTo(end) * SLACK);
			LINES.computeIfAbsent(entry.getKey(), id -> new Line(hand, want)).step(level, hand, end, want);
		}
	}

	// ---- drawing -------------------------------------------------------------------------------------------------------

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || LINES.isEmpty()) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Map<Integer, End> ends = ends(level);
		for (Map.Entry<Integer, Line> entry : LINES.entrySet()) {
			End end = ends.get(entry.getKey());
			if (end == null || !(level.getEntity(entry.getKey()) instanceof LivingEntity holder)) {
				continue;
			}
			Entity on = end.on();
			Vec3 fastened = on == null ? null : fastened(on, partialTick);
			Vec3[] line = entry.getValue().frame(hand(minecraft, holder, partialTick), fastened, partialTick);
			Vec3 tip = line[POINTS - 1];
			int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(tip));
			for (int i = 0; i < POINTS; i++) {
				line[i] = line[i].subtract(camera);
			}
			PoseStack poseStack = context.poseStack();
			boolean noose = on instanceof LivingEntity;
			double round = noose ? on.getBbWidth() * 0.5 + NOOSE_GAP : 0.0;
			Vec3 middle = noose ? new Vec3(on.getPosition(partialTick).x, tip.y, on.getPosition(partialTick).z).subtract(camera.x, 0.0, camera.z) : null;
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityCutout(ShackleTether.ROPE), (pose, buffer) -> {
				ShackleTether.rope(buffer, pose, line, light);
				if (noose) {
					ShackleTether.collar(buffer, pose, middle, round, NOOSE_THICK, light);
				}
			});
			if (!noose) {
				loop(context, level, line[POINTS - 1], line[POINTS - 1].subtract(line[POINTS - 2]), light);
			}
		}
	}

	/**
	 * The loop on the rope's end: its own model (item model lasso_loop, which hangs from the top middle of its box),
	 * carried on along the rope's last link ({@code along}).
	 */
	private static void loop(LevelRenderContext context, ClientLevel level, Vec3 at, Vec3 along, int light) {
		Vec3 down = along.lengthSqr() < 1.0E-8 ? new Vec3(0.0, -1.0, 0.0) : along.normalize();
		ItemStack stack = new ItemStack(Lasso.ITEM);
		stack.set(DataComponents.ITEM_MODEL, LOOP_MODEL);
		// its own state each time: the frame keeps what it is handed until it is drawn
		ItemStackRenderState shown = new ItemStackRenderState();
		Minecraft.getInstance().getItemModelResolver().updateForTopItem(shown, stack, ItemDisplayContext.NONE, level, null, 0);
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(at.x, at.y, at.z);
		poseStack.rotate(new Quaternionf().rotationTo(0.0F, -1.0F, 0.0F, (float)down.x, (float)down.y, (float)down.z));
		poseStack.scale(LOOP_SCALE, LOOP_SCALE, LOOP_SCALE);
		// an item model is drawn about the middle of its box: bring the top of it to the rope's end
		poseStack.translate(0.0F, -0.5F, 0.0F);
		shown.submit(poseStack, context.submitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
