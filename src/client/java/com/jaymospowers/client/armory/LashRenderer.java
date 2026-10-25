package com.jaymospowers.client.armory;

import com.jaymospowers.armory.Lash;
import com.jaymospowers.core.finisher.Finishers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.148) Draws every flail's chain and ball and every whip's lash, from the hand to where {@link Lash#head} puts the
 * head (the synced {@link Lash.State}).
 * <p>
 * (0.16.8) Andy: "the flail chain is completley disconnected from the stick ... these weapons should be physics based so
 * the entire thing is accurately rendered in hand and in world. like this mod" (the Whip mod, MIT; nothing copied).
 * Each held flail and whip is now a rope of points on Verlet physics ({@link Rope}: gravity, drag, fixed links, resting
 * on and dragging over blocks), stepped every tick. Its first point is the handle's tip as the item was really drawn
 * ({@link LashSockets}); its last is the head, pinned to {@link Lash#head}'s path while the weapon is doing something
 * (so the game's timing and hits are unchanged) and free otherwise: an idle flail's ball hangs and swings off its
 * handle, an idle whip's lash trails from its grip, and a lash flying back or a ball let fall swings in on its own.
 * In the hand Balkon's flail is drawn as its bare handle and the whip as its grip, so nothing is painted on twice.
 * The chain is vanilla's iron chain (two crossed strips, as the chain block), the ball is spiked and made of what the
 * flail is made of (oak, cobblestone, copper, iron, gold, diamond, netherite).
 * <p>
 * (0.16.2) A flail's whirl is drawn from the moment you press use ({@link #shown}); {@link LashPose} holds the arm.
 */
public final class LashRenderer {
	/** The chain: as long as the whirl's reach plus a little; idle, hanging this long off the handle. */
	private static final double CHAIN_LENGTH = Lash.WHIRL_RADIUS + 0.25, CHAIN_IDLE = 0.55;
	/** One run of the iron chain texture (16 px of it) over this length; its strips are 3 px of that wide. */
	private static final double CHAIN_TILE = 0.42;
	private static final Identifier CHAIN_TEXTURE = Identifier.withDefaultNamespace("textures/block/iron_chain.png");
	/** The lash: its full length, thick at the grip and thin at the tip, leather brown with a pale popper at the end. */
	private static final double WHIP_LENGTH = Lash.WHIP_REACH + 0.6, WHIP_IDLE = 1.7, WHIP_BASE = 0.04, WHIP_TIP = 0.011;
	private static final int LEATHER_DARK = 0x3B2414, LEATHER_LIGHT = 0x7A4E2C, POPPER = 0xC9B48E;
	private static final int SIDES = 6;
	/** The ball: half its width, and its spikes' length. */
	private static final double BALL = 0.075, SPIKE = 0.06;
	/** (0.15.148) Garrotte: how wide the lash's turn round the neck is. */
	private static final double NECK_LOOP = 0.13;
	/** (0.16.2) How far the wrist turns (radians) as the ball goes round. */
	private static final float WRIST = 0.12F;
	/** (0.16.2) Ball and Chain: the arm straight up (raise, out) while it whirls, and the ball's circle round that hand. */
	private static final float EXEC_RAISE = 3.0F, EXEC_OUT = 0.35F;
	private static final double EXEC_RADIUS = 1.1, EXEC_DROOP = 0.1;

	// ---- (0.16.8) the physics ------------------------------------------------------------------------------------------
	/** Points along a flail's chain and a whip's lash. */
	private static final int CHAIN_POINTS = 14, LASH_POINTS = 26;
	/** Pull down (blocks a tick, a tick), what a point keeps of its speed each tick, and passes over the links. */
	private static final double GRAVITY = 0.045, DRAG = 0.9, LASH_DRAG = 0.86;
	private static final int PASSES = 16;
	/** The ball weighs this many links; a free line reels in or pays out this share of the way a tick. */
	private static final double BALL_WEIGHT = 4.0, REEL = 0.35;
	/** On a block a point keeps this share of its slide; one this far from its line is put back on it (a teleport). */
	private static final double FRICTION = 0.5, SNAP = 6.0;
	/** The first points take up the difference between the tick's handle and this frame's, fading out over this many. */
	private static final int HANDLE_BLEND = 6;
	/** No physics for players further off than this. */
	private static final double RANGE = 64.0;

	/** (0.16.2) Whirls this client has started drawing before the server's word came: per player, the whirl as guessed. */
	private static final Map<Player, Lash.State> GUESSED = new WeakHashMap<>();
	private static final Map<Player, Rope> ROPES = new WeakHashMap<>();

	private LashRenderer() {
	}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(LashRenderer::render);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null || minecraft.player == null) {
				ROPES.clear();
				LashSockets.clear();
				return;
			}
			if (!minecraft.isPaused()) {
				climb(minecraft.player);
				tick(minecraft, minecraft.level);
			}
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (Lash.isFlail(stack)) {
				lines.add(Component.translatable("item.jaymospowers.flail.whirl").withStyle(ChatFormatting.GRAY));
			}
		});
	}

	// ---- what each player's flail or whip is doing --------------------------------------------------------------------

	/**
	 * (0.16.2) What a player's flail or whip is doing as drawn: the synced {@link Lash.State}, or, from the moment use is
	 * pressed with a flail, the whirl as it will be (kept until the server's whirl ends, so it doesn't jump when the
	 * server's word comes).
	 */
	public static Lash.@Nullable State shown(Player player, long gameTime) {
		Lash.State state = player.getAttached(Lash.STATE);
		boolean whirling = player.isUsingItem() && Lash.isFlail(player.getUseItem());
		Lash.State guess = GUESSED.get(player);
		if (guess == null && whirling && state == null) {
			guess = new Lash.State(Lash.FLAIL, Lash.WHIRL, gameTime - player.getTicksUsingItem(), -1, Vec3.ZERO,
				new Vec3(Lash.startAngle(player), 0.0, 0.0));
			GUESSED.put(player, guess);
		}
		if (guess == null) {
			return state;
		}
		if (state != null && state.phase() != Lash.WHIRL || state == null && !whirling) {
			GUESSED.remove(player);
			return state;
		}
		return guess;
	}

	/**
	 * (0.16.2) The flail arm at {@code time}: how far raised from hanging and out to the side (radians; null: no flail
	 * out), the wrist turning with the ball while it whirls.
	 */
	public static float @Nullable [] arm(Player player, double time) {
		if (Finishers.of(player) != null) {
			return null;
		}
		Lash.State state = shown(player, (long)Math.floor(time));
		if (state == null || state.kind() != Lash.FLAIL) {
			return null;
		}
		double t = Math.max(0.0, time - state.since());
		float raise = Lash.armRaise(state, t), out = Lash.ARM_OUT;
		if (state.phase() == Lash.WHIRL) {
			double angle = state.aim().x + Lash.spinAngle(t);
			float turn = WRIST * Mth.clamp((float)(t / Lash.PAY_OUT), 0.0F, 1.0F);
			raise += turn * (float)Math.cos(angle);
			out += turn * (float)Math.sin(angle);
		}
		return new float[] {raise, out};
	}

	/** (0.16.8) A flail or whip out: where its head is held (null: free, the physics has it), and how long the line is. */
	private record Pull(@Nullable Vec3 head, double length, @Nullable Vec3 neck) {
	}

	/** What the head of {@code player}'s flail or whip ({@code flail}) is held to at {@code time}, from its handle at {@code handle}. */
	private static Pull pull(ClientLevel level, Player player, boolean flail, Vec3 handle, double time, float partialTick) {
		Finishers.Finish finish = Finishers.of(player);
		if (finish != null && finish.type() == (flail ? Finishers.Kind.FLAIL : Finishers.Kind.WHIP)
			&& level.getEntity(finish.target()) instanceof LivingEntity body) {
			double t = time - finish.start();
			if (flail) {
				Vec3 ball = executionBall(player, body.getPosition(partialTick).add(0.0, 0.25, 0.0), t, partialTick);
				return new Pull(ball, ball == null ? CHAIN_IDLE : Math.max(CHAIN_LENGTH * 0.6, handle.distanceTo(ball) + 0.1), null);
			}
			Vec3 neck = Finishers.headAt(body);
			boolean round = t >= Finishers.Kind.WHIP_CRACK;
			Vec3 tip = round ? neck : handle.lerp(neck, Math.max(0.0, t) / Finishers.Kind.WHIP_CRACK);
			return new Pull(tip, Math.max(WHIP_IDLE, handle.distanceTo(tip) * 1.03), round ? neck : null);
		}
		Lash.State state = shown(player, (long)Math.floor(time));
		if (state == null || state.kind() != (flail ? Lash.FLAIL : Lash.WHIP)) {
			return new Pull(null, flail ? CHAIN_IDLE : WHIP_IDLE, null);
		}
		if (state.phase() == Lash.RETURN) {
			// let go: the head swings back in on its own as the line is taken in
			return new Pull(null, flail ? CHAIN_IDLE : WHIP_IDLE, null);
		}
		Vec3 head = Lash.head(player, state, time, partialTick);
		double gap = handle.distanceTo(head);
		if (flail) {
			return new Pull(head, Math.max(CHAIN_LENGTH, gap + 0.05), null);
		}
		// a crack snakes out with slack; wrapped round or hooked over, it's pulled nearly taut
		double slack = state.phase() == Lash.CRACK ? 1.08 : 1.02;
		return new Pull(head, Mth.clamp(gap * slack, WHIP_IDLE, WHIP_LENGTH), null);
	}

	/** (0.16.2) Ball and Chain's arm (FinisherPose's keys): up for the whirls, down for the blows. */
	private static float executionRaise(double t) {
		double[][] keys = {{0, 0.25}, {4, EXEC_RAISE}, {14, EXEC_RAISE}, {18, 0.45}, {22, 0.45}, {26, EXEC_RAISE}, {33, EXEC_RAISE},
			{36, 0.35}, {41, 0.4}, {46, 0.25}};
		for (int i = 1; i < keys.length; i++) {
			if (t < keys[i][0]) {
				double s = Math.max(0.0, (t - keys[i - 1][0]) / (keys[i][0] - keys[i - 1][0]));
				return (float)Mth.lerp(s, keys[i - 1][1], keys[i][1]);
			}
		}
		return 0.25F;
	}

	/**
	 * Ball and Chain's ball ({@link Finishers.Kind#FLAIL}), {@code t} ticks in: paid out and whirled round the raised
	 * hand, down on them at the first blow (18), up and round again, down at the kill (36), lying there; (0.16.8) then
	 * let go (null), to swing back to the handle on its chain.
	 */
	private static @Nullable Vec3 executionBall(Player player, Vec3 at, double t, float partialTick) {
		Vec3 over = Lash.armHand(player, partialTick, EXEC_RAISE, EXEC_OUT).add(0.0, -EXEC_DROOP, 0.0);
		java.util.function.DoubleFunction<Vec3> orbit = s -> over.add(Math.cos(s * 0.8) * EXEC_RADIUS, 0.0, Math.sin(s * 0.8) * EXEC_RADIUS);
		Vec3 hand = Lash.armHand(player, partialTick, executionRaise(t), 0.0);
		if (t < 3.0) {
			return hand.lerp(orbit.apply(t), Math.max(0.0, t) / 3.0);
		} else if (t < 14.0 || t >= 26.0 && t < 32.0) {
			return orbit.apply(t);
		} else if (t < 18.0) {
			double s = (t - 14.0) / 4.0;
			return orbit.apply(14.0).lerp(at, s * s);
		} else if (t < 23.0) {
			return at;
		} else if (t < 26.0) {
			return at.lerp(orbit.apply(26.0), (t - 23.0) / 3.0);
		} else if (t < 36.0) {
			double s = (t - 32.0) / 4.0;
			return orbit.apply(32.0).lerp(at, s * s);
		} else if (t < 42.0) {
			return at;
		}
		return null;
	}

	// ---- the handle ------------------------------------------------------------------------------------------------------

	private static boolean firstPerson(Minecraft minecraft, Player player) {
		return player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
	}

	/**
	 * Where the line leaves the handle this frame: its tip as the item was last drawn ({@link LashSockets}); before it's
	 * been drawn, the hand's screen corner in first person (as the fishing line does), else roughly the hand.
	 */
	private static Vec3 handle(Minecraft minecraft, Player player, float partialTick, double time) {
		boolean first = firstPerson(minecraft, player);
		Vec3 offset = LashSockets.offset(player, first);
		if (offset != null) {
			return player.getPosition(partialTick).add(offset);
		}
		if (first) {
			int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
			float fov = minecraft.options.fov().get().floatValue();
			Camera camera = minecraft.gameRenderer.mainCamera();
			return player.getEyePosition(partialTick).add(camera.getNearPlane(fov).getPointOnPlane(side * 0.525F, -0.1F).scale(960.0 / fov));
		}
		float[] arm = arm(player, time);
		return arm != null ? Lash.armHand(player, partialTick, arm[0], arm[1]) : Lash.hand(player, partialTick);
	}

	// ---- the physics ------------------------------------------------------------------------------------------------------

	/** (0.16.8) A flail's chain or a whip's lash: points that fall, drag, and stay one link apart. */
	private static final class Rope {
		final boolean flail;
		final int count;
		final Vec3[] now, then, drawn;
		double length;
		/** The head was held this tick (its point is placed exactly each frame, not left to the physics). */
		boolean held;

		Rope(boolean flail, Vec3 handle, double length) {
			this.flail = flail;
			this.count = flail ? CHAIN_POINTS : LASH_POINTS;
			this.now = new Vec3[this.count + 1];
			this.then = new Vec3[this.count + 1];
			this.drawn = new Vec3[this.count + 1];
			this.length = length;
			hang(handle);
		}

		/** Straight down from the handle, at rest. */
		void hang(Vec3 handle) {
			for (int i = 0; i <= this.count; i++) {
				Vec3 at = handle.add(0.0, -this.length * i / this.count, 0.0);
				this.now[i] = at;
				this.then[i] = at;
				this.drawn[i] = at;
			}
		}

		void step(Level level, Vec3 handle, Pull pull) {
			if (this.now[0].distanceToSqr(handle) > SNAP * SNAP) {
				hang(handle);
			}
			System.arraycopy(this.now, 0, this.drawn, 0, this.now.length);
			this.held = pull.head() != null;
			this.length = this.held ? pull.length() : Mth.lerp(REEL, this.length, pull.length());
			double link = this.length / this.count;
			double drag = this.flail ? DRAG : LASH_DRAG;
			this.then[0] = this.now[0];
			this.now[0] = handle;
			for (int i = 1; i <= this.count; i++) {
				if (i == this.count && this.held) {
					this.then[i] = this.now[i];
					this.now[i] = pull.head();
					continue;
				}
				Vec3 speed = this.now[i].subtract(this.then[i]).scale(drag);
				Vec3 next = this.now[i].add(speed).add(0.0, -GRAVITY, 0.0);
				this.then[i] = this.now[i];
				this.now[i] = settle(level, this.now[i], next, i);
			}
			// every link back to its length, the handle (and a held head) not moving, the ball heavier than a link
			for (int pass = 0; pass < PASSES; pass++) {
				for (int i = 0; i < this.count; i++) {
					Vec3 a = this.now[i], b = this.now[i + 1];
					Vec3 gap = b.subtract(a);
					double d = gap.length();
					if (d < 1.0E-7) {
						continue;
					}
					double wa = i == 0 ? 0.0 : 1.0;
					double wb = i + 1 == this.count ? (this.held ? 0.0 : this.flail ? 1.0 / BALL_WEIGHT : 1.0) : 1.0;
					if (wa + wb <= 0.0) {
						continue;
					}
					Vec3 fix = gap.scale((d - link) / d / (wa + wb));
					this.now[i] = a.add(fix.scale(wa));
					this.now[i + 1] = b.subtract(fix.scale(wb));
				}
			}
		}

		/** A point moving from {@code from} to {@code to}: stopped on whatever block it meets, sliding a little along it. */
		private Vec3 settle(Level level, Vec3 from, Vec3 to, int i) {
			BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
			if (hit.getType() == HitResult.Type.MISS) {
				return to;
			}
			Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i());
			Vec3 rest = hit.getLocation().add(normal.scale(0.02));
			// what's left of the move, along the face, and slowed by it
			Vec3 left = to.subtract(hit.getLocation());
			Vec3 along = left.subtract(normal.scale(left.dot(normal))).scale(FRICTION);
			Vec3 at = rest.add(along);
			this.then[i] = at.subtract(along);
			return at;
		}

		/** The points this frame: between last tick's and this tick's, the handle's end eased onto {@code handle}. */
		Vec3[] frame(Vec3 handle, @Nullable Vec3 head, float partialTick) {
			Vec3[] points = new Vec3[this.count + 1];
			Vec3 shift = handle.subtract(this.drawn[0].lerp(this.now[0], partialTick));
			for (int i = 0; i <= this.count; i++) {
				Vec3 at = this.drawn[i].lerp(this.now[i], partialTick);
				if (i < HANDLE_BLEND) {
					at = at.add(shift.scale(1.0 - i / (double)HANDLE_BLEND));
				}
				points[i] = at;
			}
			points[0] = handle;
			if (head != null) {
				// a held head is exactly where the game has it, and the last links ease onto it
				Vec3 off = head.subtract(points[this.count]);
				for (int i = Math.max(1, this.count - HANDLE_BLEND); i <= this.count; i++) {
					double s = (i - (this.count - HANDLE_BLEND)) / (double)HANDLE_BLEND;
					points[i] = points[i].add(off.scale(Mth.clamp(s, 0.0, 1.0)));
				}
			}
			return points;
		}
	}

	/** Which a player has out in their main hand: 1 a flail, 2 a whip, 0 neither. */
	private static int holding(Player player) {
		return Lash.isFlail(player.getMainHandItem()) ? 1 : Lash.isWhip(player.getMainHandItem()) ? 2 : 0;
	}

	private static void tick(Minecraft minecraft, ClientLevel level) {
		double time = level.getGameTime();
		Vec3 eye = minecraft.player.position();
		for (Player player : level.players()) {
			int held = holding(player);
			if (held == 0 || player.isRemoved() || player.isSpectator() || player.distanceToSqr(eye) > RANGE * RANGE) {
				ROPES.remove(player);
				continue;
			}
			boolean flail = held == 1;
			Vec3 handle = handle(minecraft, player, 1.0F, time);
			Pull pull = pull(level, player, flail, handle, time, 1.0F);
			Rope rope = ROPES.get(player);
			if (rope == null || rope.flail != flail) {
				rope = new Rope(flail, handle, pull.length());
				ROPES.put(player, rope);
			}
			rope.step(level, handle, pull);
		}
	}

	// ---- drawing -------------------------------------------------------------------------------------------------------

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double time = level.getGameTime() + partialTick;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Player player : level.players()) {
			Rope rope = ROPES.get(player);
			if (rope == null || player.isRemoved() || player.position().distanceToSqr(camera) > RANGE * RANGE) {
				continue;
			}
			Vec3 handle = handle(minecraft, player, partialTick, time);
			Pull pull = pull(level, player, rope.flail, handle, time, partialTick);
			Vec3[] line = rope.frame(handle, rope.held ? pull.head() : null, partialTick);
			int[] light = new int[line.length];
			for (int i = 0; i < line.length; i++) {
				light[i] = lightAt(level, line[i]);
				line[i] = line[i].subtract(camera);
			}
			if (rope.flail) {
				chain(context, line, light);
				Vec3 last = line[line.length - 1], before = line[line.length - 2];
				Lash.State state = shown(player, level.getGameTime());
				double turn = state != null && state.phase() == Lash.WHIRL ? Lash.spinAngle(time - state.since()) : 0.0;
				ball(context, last, last.subtract(before), turn, material(player), light[line.length - 1]);
			} else {
				lash(context, line, light);
				if (pull.neck() != null) {
					neckLoop(context, handle, pull.neck(), camera, light[line.length - 1]);
				}
			}
		}
	}

	private static int lightAt(Level level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at);
		return LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
	}

	/** (0.16.8) The chain: vanilla's iron chain, two crossed strips of it (as the chain block) following the line. */
	private static void chain(LevelRenderContext context, Vec3[] line, int[] light) {
		// cut the line into pieces no longer than one run of the texture, so each piece's v stays within it
		List<Vec3> stops = new ArrayList<>();
		List<Integer> lights = new ArrayList<>();
		List<Double> runs = new ArrayList<>();
		stops.add(line[0]);
		lights.add(light[0]);
		runs.add(0.0);
		double run = 0.0;
		for (int i = 1; i < line.length; i++) {
			Vec3 a = line[i - 1], b = line[i];
			double length = a.distanceTo(b);
			double at = 0.0;
			while (length - at > 1.0E-6) {
				double step = Math.min(length - at, CHAIN_TILE - run);
				at += step;
				run += step;
				stops.add(a.lerp(b, at / length));
				lights.add(light[i]);
				runs.add(run);
				if (run >= CHAIN_TILE - 1.0E-6) {
					run = 0.0;
					stops.add(stops.get(stops.size() - 1));
					lights.add(light[i]);
					runs.add(0.0);
				}
			}
		}
		double half = 1.5 / 16.0 * CHAIN_TILE;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(CHAIN_TEXTURE), (pose, buffer) -> {
			for (int k = 0; k + 1 < stops.size(); k++) {
				Vec3 a = stops.get(k), b = stops.get(k + 1);
				Vec3 dir = b.subtract(a);
				if (dir.lengthSqr() < 1.0E-10) {
					continue;
				}
				dir = dir.normalize();
				Vec3 u = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
				Vec3 v = dir.cross(u).normalize();
				float v0 = (float)(runs.get(k) / CHAIN_TILE), v1 = (float)(runs.get(k + 1) / CHAIN_TILE);
				if (v1 <= v0) {
					continue;
				}
				int la = lights.get(k), lb = lights.get(k + 1);
				strip(buffer, pose, a, b, u.scale(half), v, 0.0F, 3.0F / 16.0F, v0, v1, la, lb);
				strip(buffer, pose, a, b, v.scale(half), u, 3.0F / 16.0F, 6.0F / 16.0F, v0, v1, la, lb);
			}
		});
	}

	/** One flat strip of chain from {@code a} to {@code b}, {@code side} either way, facing {@code normal}. */
	private static void strip(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 side, Vec3 normal, float u0, float u1, float v0,
		float v1, int la, int lb) {
		vertex(buffer, pose, a.subtract(side), u0, v0, la, normal);
		vertex(buffer, pose, a.add(side), u1, v0, la, normal);
		vertex(buffer, pose, b.add(side), u1, v1, lb, normal);
		vertex(buffer, pose, b.subtract(side), u0, v1, lb, normal);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 at, float u, float v, int light, Vec3 normal) {
		buffer.addVertex(pose, (float)at.x, (float)at.y, (float)at.z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light).setNormal(pose, (float)normal.x, (float)normal.y, (float)normal.z);
	}

	/** The lash: tapering to the tip, the popper paler. */
	private static void lash(LevelRenderContext context, Vec3[] line, int[] light) {
		int last = line.length - 1;
		tube(context, line, i -> Mth.lerp(i / (double)last, WHIP_BASE, WHIP_TIP),
			i -> dim(i >= last - 1 ? POPPER : i % 3 == 0 ? LEATHER_DARK : LEATHER_LIGHT, light[i]));
	}

	/** (0.15.148) Garrotte: a turn of the lash round the neck, square to the lash. */
	private static void neckLoop(LevelRenderContext context, Vec3 handle, Vec3 neck, Vec3 camera, int light) {
		Vec3 axis = handle.subtract(neck).normalize();
		Vec3 u = Math.abs(axis.y) < 0.95 ? axis.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : axis.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 v = axis.cross(u).normalize();
		int segments = 18;
		Vec3[] loop = new Vec3[segments + 1];
		for (int i = 0; i <= segments; i++) {
			double a = i * Math.PI * 2.0 / segments;
			loop[i] = neck.add(u.scale(Math.cos(a) * NECK_LOOP)).add(v.scale(Math.sin(a) * NECK_LOOP)).subtract(camera);
		}
		tube(context, loop, i -> WHIP_BASE * 0.6, i -> dim(i % 3 == 0 ? LEATHER_DARK : LEATHER_LIGHT, light));
	}

	/**
	 * The rappel, on your own side (your movement is yours): the lash hooked over a wall's top ({@link Lash#HOOK}) and use
	 * held. (0.16.8) Andy: "the rappelling should only pull you up as you walk, so no auto gliding to the impact point".
	 * You hang on the lash at the length it caught you (it holds you, it doesn't pull); walk and you take it in hand over
	 * hand and go up the wall, and over the top at the lip; hold Sneak and you pay it out and let yourself down.
	 */
	private static double climbRope = -1.0;
	private static long ropeSince = Long.MIN_VALUE;

	private static void climb(LocalPlayer player) {
		Lash.State state = player.getAttached(Lash.STATE);
		if (state == null || state.phase() != Lash.HOOK || !player.isUsingItem()) {
			climbRope = -1.0;
			return;
		}
		Vec3 lip = state.aim();
		// you hold the lash up over your head
		Vec3 grip = player.position().add(0.0, player.getBbHeight() * 0.9, 0.0);
		if (state.since() != ropeSince || climbRope < 0.0) {
			ropeSince = state.since();
			climbRope = grip.distanceTo(lip);
		}
		boolean walking = player.input.keyPresses.forward();
		double below = lip.y - player.getY();
		Vec3 toWall = new Vec3(lip.x - player.getX(), 0.0, lip.z - player.getZ());
		double gap = toWall.horizontalDistance();
		Vec3 dir = gap < 1.0E-4 ? Vec3.ZERO : toWall.scale(1.0 / gap);
		Vec3 v = player.getDeltaMovement();
		if (walking && below > Lash.OVER_THE_TOP) {
			// hand over hand: up the wall, leaning in
			climbRope = Math.max(0.5, climbRope - Lash.CLIMB_SPEED);
			double in = Math.min(Lash.WALL_HUG, Math.max(0.0, gap - player.getBbWidth() * 0.5 - 0.05));
			v = new Vec3(dir.x * in, Lash.CLIMB_SPEED, dir.z * in);
		} else if (walking && below > -0.05) {
			// at the top: up and over the lip
			v = new Vec3(dir.x * Lash.OVER_PUSH, Lash.CLIMB_SPEED * 1.6, dir.z * Lash.OVER_PUSH);
		} else if (player.isShiftKeyDown()) {
			climbRope = Math.min(Lash.WHIP_REACH + 1.0, climbRope + Lash.LOWER_SPEED);
		}
		// the lash holds you at its length: anything taking you further from the lip is taken off (you swing, you hang)
		Vec3 next = grip.add(v);
		Vec3 out = next.subtract(lip);
		double far = out.length();
		if (far > climbRope && far > 1.0E-4) {
			Vec3 radial = out.scale(1.0 / far);
			double away = v.dot(radial);
			if (away > 0.0) {
				v = v.subtract(radial.scale(away));
			}
			v = v.subtract(radial.scale(Math.min(0.3, far - climbRope)));
		}
		player.setDeltaMovement(v);
		player.resetFallDistance();
	}

	private interface Radius {
		double at(int i);
	}

	private interface Colour {
		int at(int i);
	}

	/** A tube through {@code line}: a ring of {@link #SIDES} at each point, quads between, lit from above. */
	private static void tube(LevelRenderContext context, Vec3[] line, Radius radius, Colour colour) {
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
			Vec3[] prev = null;
			for (int i = 0; i < line.length; i++) {
				Vec3 dir = line[Math.min(i + 1, line.length - 1)].subtract(line[Math.max(i - 1, 0)]);
				if (dir.lengthSqr() < 1.0E-8) {
					dir = new Vec3(0.0, 1.0, 0.0);
				}
				dir = dir.normalize();
				Vec3 u = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
				Vec3 v = dir.cross(u).normalize();
				double r = radius.at(i);
				Vec3[] ring = new Vec3[SIDES];
				for (int j = 0; j < SIDES; j++) {
					double a = j * Math.PI * 2.0 / SIDES;
					ring[j] = line[i].add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r));
				}
				if (prev != null) {
					int base = colour.at(i - 1);
					for (int j = 0; j < SIDES; j++) {
						int n = (j + 1) % SIDES;
						double a = (j + 0.5) * Math.PI * 2.0 / SIDES;
						double up = u.scale(Math.cos(a)).add(v.scale(Math.sin(a))).y;
						quad(buffer, pose, prev[j], prev[n], ring[n], ring[j], shade(base, up));
					}
				}
				prev = ring;
			}
		});
	}

	/**
	 * (0.16.8) The flail's head: a small spiked ball of what the flail is made of (its block's texture), hanging along
	 * the last link ({@code along}) and, while it whirls, turning as it goes.
	 */
	private static void ball(LevelRenderContext context, Vec3 at, Vec3 along, double turn, Identifier texture, int light) {
		Vec3 y = along.lengthSqr() < 1.0E-8 ? new Vec3(0.0, -1.0, 0.0) : along.normalize();
		Vec3 x0 = Math.abs(y.y) < 0.95 ? y.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : y.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 z0 = x0.cross(y).normalize();
		double c = Math.cos(turn), s = Math.sin(turn);
		Vec3 x = x0.scale(c).add(z0.scale(s)), z = z0.scale(c).subtract(x0.scale(s));
		// the ball hangs below the chain's end, not round it
		Vec3 centre = at.add(y.scale(BALL));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(texture), (pose, buffer) -> {
			Vec3[] axes = {x, y, z};
			for (int k = 0; k < 3; k++) {
				Vec3 a = axes[k], b = axes[(k + 1) % 3], n = axes[(k + 2) % 3];
				for (int sign = -1; sign <= 1; sign += 2) {
					Vec3 face = centre.add(n.scale(sign * BALL));
					Vec3 normal = n.scale(sign);
					Vec3 p0 = face.add(a.scale(-BALL)).add(b.scale(-BALL)), p1 = face.add(a.scale(BALL)).add(b.scale(-BALL));
					Vec3 p2 = face.add(a.scale(BALL)).add(b.scale(BALL)), p3 = face.add(a.scale(-BALL)).add(b.scale(BALL));
					// the middle of the block's face, so its pixels read at this size
					vertex(buffer, pose, p0, 0.25F, 0.25F, light, normal);
					vertex(buffer, pose, p1, 0.75F, 0.25F, light, normal);
					vertex(buffer, pose, p2, 0.75F, 0.75F, light, normal);
					vertex(buffer, pose, p3, 0.25F, 0.75F, light, normal);
					// a spike out of each face but the one on the chain: four sides meeting at the point
					if (n == y && sign < 0) {
						continue;
					}
					Vec3 tip = face.add(n.scale(sign * SPIKE));
					double w = BALL * 0.5;
					Vec3[] q = {face.add(a.scale(-w)).add(b.scale(-w)), face.add(a.scale(w)).add(b.scale(-w)), face.add(a.scale(w)).add(b.scale(w)),
						face.add(a.scale(-w)).add(b.scale(w))};
					for (int j = 0; j < 4; j++) {
						Vec3 e0 = q[j], e1 = q[(j + 1) % 4];
						Vec3 side = e1.subtract(e0).cross(tip.subtract(e0)).normalize().scale(sign);
						vertex(buffer, pose, e0, 0.4F, 0.6F, light, side);
						vertex(buffer, pose, e1, 0.6F, 0.6F, light, side);
						vertex(buffer, pose, tip, 0.5F, 0.4F, light, side);
						vertex(buffer, pose, tip, 0.5F, 0.4F, light, side);
					}
				}
			}
		});
	}

	/** The ball's material, by the flail's tier (Balkon's ids: flail.wood, flail.iron, ...): that block's texture. */
	private static Identifier material(Player player) {
		String path = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath();
		String block = path.endsWith("wood") ? "oak_planks" : path.endsWith("stone") ? "cobblestone" : path.endsWith("copper") ? "copper_block"
			: path.endsWith("gold") ? "gold_block" : path.endsWith("diamond") ? "diamond_block" : path.endsWith("netherite") ? "netherite_block"
			: "iron_block";
		return Identifier.withDefaultNamespace("textures/block/" + block + ".png");
	}

	/** {@code rgb} darkened by the light where it is. */
	private static int dim(int rgb, int light) {
		float f = Math.max(0.2F, Math.max(LightCoordsUtil.block(light), LightCoordsUtil.sky(light)) / 15.0F);
		int r = (int)((rgb >> 16 & 0xFF) * f), g = (int)((rgb >> 8 & 0xFF) * f), b = (int)((rgb & 0xFF) * f);
		return r << 16 | g << 8 | b;
	}

	/** {@code rgb} shaded by how much its face looks up (-1 down, 1 up). */
	private static int shade(int rgb, double up) {
		float f = (float)(0.65 + 0.35 * up);
		int r = Mth.clamp((int)((rgb >> 16 & 0xFF) * f), 0, 255), g = Mth.clamp((int)((rgb >> 8 & 0xFF) * f), 0, 255);
		int b = Mth.clamp((int)((rgb & 0xFF) * f), 0, 255);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
		buffer.addVertex(pose, (float)a.x, (float)a.y, (float)a.z).setColor(color);
		buffer.addVertex(pose, (float)b.x, (float)b.y, (float)b.z).setColor(color);
		buffer.addVertex(pose, (float)c.x, (float)c.y, (float)c.z).setColor(color);
		buffer.addVertex(pose, (float)d.x, (float)d.y, (float)d.z).setColor(color);
	}
}
