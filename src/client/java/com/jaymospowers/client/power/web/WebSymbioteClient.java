package com.jaymospowers.client.power.web;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Fate;
import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteState;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The symbiote and the ascended form, client side: the radial menu key (X), Wall Crawl movement (player motion is
 * client-authoritative), being steered by a takeover, the sidestep nudges, the black suit's render state, and
 * Spider-Sense's red outlines.
 */
public final class WebSymbioteClient {
	/** Whether a player's render state should get the black suit layer. */
	public static final RenderStateDataKey<Boolean> SUIT = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_suit");
	/** Living Armor's tendrils on this player: how grown (0-1), how far into a lash (0-1), and a clock for the sway. */
	public static final RenderStateDataKey<Tendrils> TENDRILS = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_tendrils");
	/**
	 * The tendrils drawing back in (Wall Crawl or Living Armor ending), as the suit comes off: how far along (0-1). They
	 * pour back over the body, coiling round it, then unwind into the chest (see WebSuitWrapLayer).
	 */
	public static final RenderStateDataKey<Float> RETRACT = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_retract");

	/**
	 * @param strike   how far through a strike (0-1), or -1
	 * @param aimYaw   where the strike goes, relative to the body (radians, + to the wearer's right, as Minecraft's yaw turns)
	 * @param aimPitch and how high (radians, + up)
	 * @param side     which side's tendrils strike (+1 left, -1 right)
	 */
	/** {@code held}: bit 1, the right shoulder's tendril is holding something (drawn reaching out instead); bit 2, the left's. */
	/** {@code armor}: Living Armor's (it hunches, swells); otherwise only the tendrils, out on their own for a moment. */
	/** {@code heavy}: the strike swings held debris (slower, further); {@code variant}: a slam or a swipe (see WebSymbiote.STRIKE_SLAM). */
	/** {@code firstPerson}: your own, drawn in first person (by First-person Model, or next to the hands): they reach further forward. */
	/** {@code combo}: a tendril combo (WebTendrilCombos.CLAP, CRUSH, SHATTER, THRASH, BARRAGE; -1 none): {@code strike} is then how far through it. */
	/** {@code rear}, {@code hit}: the shares of {@code strike} where it stops rearing and where it lands. */
	/** {@code feral}: how wild it still is (1 a fresh bond, 0 a full one): twitchier, restless tendrils. */
	public record Tendrils(float growth, float time, float strike, float aimYaw, float aimPitch, float side, float run, int held, boolean armor,
		int wall, float wallYaw, float wallPitch, boolean heavy, int variant, boolean firstPerson, int combo, float rear, float hit, float feral,
		float roar, float @org.jspecify.annotations.Nullable [] tethers, float @org.jspecify.annotations.Nullable [] legs) {
		/** {@code wall}: 0 none, 1 reaching for a wall (aimed by wallYaw/wallPitch), 2 fanned back into it. */
		/** {@code roar}: how far through a roar (0-1), or -1: they flail, then fling out wide. */
		/** {@code tethers}: hung off a wall, per tendril (yaw, pitch against the body, then its reach in blocks; 0 for a free one). */
		/** {@code legs}: walking you up a wall (see {@link WebTendrilWalk#legs}), per tendril: its chord and the way it bows. */

		/** The same, drawn from your own eyes. */
		public Tendrils inFirstPerson() {
			return new Tendrils(growth, time, strike, aimYaw, aimPitch, side, run, held, armor, wall, wallYaw, wallPitch, heavy, variant, true, combo,
				rear, hit, feral, roar, tethers, legs);
		}
	}
	/** Ticks into a meal (partial ticks included), for the maw. */
	public static final RenderStateDataKey<Float> EATING = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_eating");
	/** Which meal this is (when it started), so each bite's size is random but the same for everyone. */
	/** The big body rearing up for a slam: how far (0-1), for the overhead stance. */
	public static final RenderStateDataKey<Float> SLAM = RenderStateDataKey.create(() -> "jaymospowers:web_slam");
	/** The suit's charged jump coiling: how far (0-1), for the crouch. */
	public static final RenderStateDataKey<Float> CHARGE = RenderStateDataKey.create(() -> "jaymospowers:web_charge");
	/** Smoothed per wearer: how far into the gallop (0 standing/walking, 1 all fours), and when each started coiling. */
	private static final class Run {
		float value;
		long at;
	}

	private static final java.util.Map<Integer, Run> RUN = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Long> COILING = new java.util.HashMap<>();

	/** This wearer's gallop blend right now (eased in and out over a few frames). */
	public static float run(Entity entity) {
		Run run = RUN.get(entity.getId());
		return run == null ? 0.0F : run.value;
	}

	private static float smoothRun(Player player) {
		float target = player.isSprinting() && player.onGround() ? 1.0F : 0.0F;
		long now = System.nanoTime();
		Run run = RUN.computeIfAbsent(player.getId(), id -> {
			Run fresh = new Run();
			fresh.value = target;
			fresh.at = now;
			return fresh;
		});
		float seconds = Math.min(0.25F, (now - run.at) / 1.0E9F);
		run.at = now;
		// about a quarter of a second to drop onto all fours, a little longer to come back up
		float rate = target > run.value ? 5.0F : 3.5F;
		run.value += (target - run.value) * Math.min(1.0F, seconds * rate);
		if (RUN.size() > 64) {
			RUN.keySet().removeIf(id -> id != player.getId() && player.level().getEntity(id) == null);
		}
		return run.value;
	}

	/** How wild the bond still is (1 a fresh bond, 0 a full one), for the veins on a feral suit. */
	public static final RenderStateDataKey<Float> FERAL = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_feral");
	/** Ticks into a Spike Burst (or the roar's own, from just before it lands), for the spikes out of the whole body. */
	public static final RenderStateDataKey<Float> SPIKES = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_spikes");
	/** How far through a roar (0-1), for the maw thrown open. */
	public static final RenderStateDataKey<Float> ROAR = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_roar");
	/** Ticks into a curious stare (the head swollen, staring), for the maw. */
	/** Ticks into a maw counter (WebCounter: Jaw Snap, Maul, Maw Roar, Drag to the Jaws), with its kind. */
	public static final RenderStateDataKey<float[]> COUNTER_MAW = RenderStateDataKey.create(() -> "jaymospowers:web_counter_maw");
	public static final RenderStateDataKey<Float> STARE = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_stare");
	/** How long the meal's carry in takes (ticks), for the maw. */
	public static final RenderStateDataKey<Integer> MEAL_REACH = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_meal_reach");
	/** A two-bite meal (cow-sized), for the maw. */
	public static final RenderStateDataKey<Boolean> MEAL_BIG = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_meal_big");

	/** Player entity id → game time their Living Armor came up (for the tendrils' growth). */
	private static final java.util.Map<Integer, Long> ARMOR_SINCE = new java.util.HashMap<>();
	/** Player entity id → how grown their tendrils last were, and when they began drawing back in after the armour dropped. */
	private static final java.util.Map<Integer, Float> LAST_GROWTH = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Long> WITHDRAW = new java.util.HashMap<>();
	/** Ticks for fully grown tendrils to sink back into the body when Living Armor ends (the growth, run backwards). */
	static final float WITHDRAW_TICKS = 40.0F;
	/** Roaring, the tendrils burst out to full size this fast (ticks). */
	static final float ROAR_GROW_TICKS = 8.0F;

	// ===== wall crawl ===============================================================================================
	/**
	 * Wall Crawl (0.8.6): you don't float, the tendrils drag you. One reaches {@code CLIMB_REACH} blocks ahead along the
	 * wall (or ceiling) the way you're going, takes {@code CLIMB_REACH_TICKS} to bite in (you hang, sagging a touch),
	 * then hauls you there for at most {@code CLIMB_PULL_TICKS}: {@code CLIMB_PULL_STEER} of the gap a tick, never faster
	 * than {@code CLIMB_PULL_MAX}, stopping {@code CLIMB_SHORT} short (the cord taut). Then the other side's reaches on.
	 */
	static final double CLIMB_REACH = 2.3, CLIMB_PULL_STEER = 0.32, CLIMB_PULL_MAX = 0.42, CLIMB_SHORT = 0.45, CLIMB_ARRIVE = 0.25;
	static final int CLIMB_REACH_TICKS = 3, CLIMB_PULL_TICKS = 9;
	/** While a tendril reaches: you sag this much a tick; and you hang this far out from the wall. */
	static final double CLIMB_SAG = -0.03, CLIMB_OFF_WALL = 0.4;
	/** Pressed up into a ceiling this hard each tick to stay stuck. */
	static final double CEILING_PRESS = 0.1;
	/**
	 * Off the wall, Wall Crawl carries you on four tendrils (Doctor Octopus style, see WebTendrilWalk): held up
	 * {@code STRIDE_HEIGHT} blocks off the ground (eased there at {@code STRIDE_LIFT} of the gap a tick), walked along at
	 * {@code STRIDE_SPEED} ({@code STRIDE_SPRINT} sprinting), the grip taking {@code STRIDE_GRIP} of the change a tick.
	 * Jump climbs and Sneak lowers at {@code STRIDE_CLIMB}; climbing needs {@code STRIDE_RISE_ANCHORS} ways to grab, and
	 * with nothing to grab within reach you fall. Sneak on the ground stands you on your own feet.
	 */
	static final double STRIDE_HEIGHT = 1.2, STRIDE_LIFT = 0.3, STRIDE_SPEED = 0.2, STRIDE_SPRINT = 0.3, STRIDE_GRIP = 0.35;
	static final double STRIDE_CLIMB = 0.22;
	/**
	 * Sneak against a wall with Wall Crawl on: the tendrils slam straight into it, no reaching first. You're thrown at the
	 * wall ({@code LATCH_SLAM} blocks/tick), lifted off the ground ({@code LATCH_LIFT}) and pinned there for
	 * {@code LATCH_HOLD_TICKS} before the keys move you again.
	 */
	static final double LATCH_SLAM = 0.35, LATCH_LIFT = 0.3;
	static final int LATCH_HOLD_TICKS = 8;
	/**
	 * The same under a ceiling: one within {@code CEILING_LATCH_REACH} blocks over your head ({@code CEILING_LATCH_GROUND}
	 * standing on the ground) and you're yanked up into it, {@code CEILING_SLAM_EXTRA} faster against gravity. By a wall,
	 * the ceiling wins only looking up more than {@code CEILING_LATCH_LOOK} degrees.
	 */
	static final double CEILING_LATCH_REACH = 1.8, CEILING_LATCH_GROUND = 0.9, CEILING_SLAM_EXTRA = 0.15;
	static final float CEILING_LATCH_LOOK = 30.0F;
	static final int STRIDE_RISE_ANCHORS = 4;
	/**
	 * Wall Crawl and the suit's charged leap: holding Jump while the tendrils carry you coils them, sinking you to
	 * {@code STRIDE_COIL} of your stride height; let go and they fling you (the charged leap), leaving you to fly for
	 * {@code LEAP_GRACE_TICKS} before they take hold again.
	 */
	static final double STRIDE_COIL = 0.55;
	static final int LEAP_GRACE_TICKS = 8;
	/** Climbing off the top of a wall: a little hop forward onto the ledge. */
	static final double LEDGE_HOP = 0.35;
	static final double LEDGE_FORWARD = 0.18;
	// ===== takeover ================================================================================================
	/**
	 * How fast it drags you (blocks/tick): sprinting whenever it can, chasing, hunting or running; walking on tight
	 * corners, step-ups and edges, mid-leap, in water, or when you're too hungry to sprint. And how hard it wrenches
	 * your view around (0..1 per tick).
	 */
	static final double TAKEOVER_SPEED = 0.38, WALK_SPEED = 0.24;
	static final float TAKEOVER_TURN = 0.35F;
	/** Running: this much faster than the chase, and the view swings round this hard. */
	static final double FLEE_SPEED_SCALE = 1.2;
	static final float FLEE_TURN = 0.5F;
	/** A waypoint counts as reached this close (blocks, horizontally; tighter at a corner) and this close in height. */
	static final double WAYPOINT_REACH = 0.7, CORNER_REACH = 0.45, WAYPOINT_REACH_Y = 1.2;
	/** It skips ahead to a later waypoint (up to this many) if you're already that close to it. */
	static final int WAYPOINT_SKIP = 2;
	/** A corner (the way turns by more than acos of this) slows it to a walk, from this far out (blocks). */
	static final double CORNER_COS = 0.7, CORNER_SLOW_RANGE = 1.2;
	/** It hops (a plain jump, never the suit's charged leap) for a waypoint this much higher than your feet (blocks), from this close. */
	static final double STEP_JUMP = 0.4, STEP_JUMP_RANGE = 1.6, JUMP_SPEED = 0.42;
	/**
	 * A waypoint more than {@code LEAP_MIN_RISE} above your feet (blocks) is a ledge: it walks to its foot (within
	 * {@code LEAP_START_RANGE}), coils only as much charge as the height needs (see {@link com.jaymospowers.power.web.WebLeap},
	 * never more than its {@code TAKEOVER_MAX_CHARGE}), holding still but for {@code LEAP_HOLD} of its speed a tick, and
	 * leaps, nudged on over the edge at {@code LEAP_FORWARD} (blocks/tick).
	 */
	static final double LEAP_MIN_RISE = 1.4, LEAP_START_RANGE = 1.3, LEAP_HOLD = 0.3, LEAP_FORWARD = 0.12;
	/** A coil's squelch every this many ticks. */
	static final int LEAP_SOUND_EVERY = 6;
	/** Edge guard: it looks this many ticks of movement ahead for ground within this drop, else it creeps. */
	static final double LEDGE_LOOK_TICKS = 3.0, LEDGE_DROP = 1.1, LEDGE_CREEP = 0.25;
	/** Swimming up along a path (or toward it), per tick. */
	static final double SWIM_UP = 0.06, FLEE_SWIM_UP = 0.08;
	/** Fleeing into a wall: the tendrils haul you up it at this speed (blocks/tick) instead of running into it. */
	static final double FLEE_CLIMB = 0.3;
	/** Close enough to its prey to stop walking (blocks, horizontally). */
	static final double TAKEOVER_STOP = 1.5;
	/** Held still while the tendrils rip: horizontal speed kept per tick. */
	static final double RIP_HOLD = 0.5;
	/** The straight-line fallback: how far ahead it looks for a way (blocks), and the turns it tries, nearest first (degrees). */
	private static final double TAKEOVER_LOOK = 0.7;
	private static final float[] TAKEOVER_DETOURS = {0.0F, 30.0F, -30.0F, 60.0F, -60.0F, 90.0F, -90.0F, 125.0F, -125.0F};
	// ===== spider-sense ============================================================================================
	/** Red outline colour, and how long a server threat list stays good without a refresh. */
	static final int SENSE_COLOR = 0xFFE02424;
	static final int SENSE_STALE_TICKS = 30;
	// ===============================================================================================================

	static KeyMapping menuKey;

	private static boolean wallClinging;
	private static boolean ceilingClinging;
	/** The climb: which side's tendril bit last, where (null: none), the way it pulls, whether it bit the top of a ledge, and ticks since it reached out. */
	private static int climbHand;
	private static @org.jspecify.annotations.Nullable Vec3 climbAt;
	private static Vec3 climbWant = Vec3.ZERO;
	private static boolean climbLedge;
	private static int climbPhase;
	private static boolean climbSent;
	/** Sneak last tick (to catch the press), and how many more ticks a slammed-in latch pins you. */
	private static boolean sneakWas;
	/** Ticks left after a charged leap off the walking tendrils before Wall Crawl takes hold again. */
	private static int leapGrace;

	/** Wall Crawl's tendrils are holding you up (so a charged leap can be let go from them, as from the ground). */
	static boolean launchable(LocalPlayer player) {
		return WebSymbiote.isToggled(player, WebSymbioteState.CRAWL) && !wallClinging && !ceilingClinging && WebTendrilWalk.striding(player);
	}

	/** A charged leap just went off: the tendrils let go for a moment. */
	static void leapt() {
		leapGrace = LEAP_GRACE_TICKS;
	}
	private static int latchHold;
	/** Keys pressed against a takeover's hold: last tick's movement keys (for presses), the last tick one was sent, and a local tally for the meter. */
	private static Input heldKeys = Input.EMPTY;
	private static int struggleTick = -1;
	private static float struggleMeter;
	private static final boolean[] powerKeysWere = new boolean[8];
	private static int takeoverTicks;
	private static int takeoverTarget = -1;
	/** Running from {@code takeoverTarget}, not at it. */
	private static boolean takeoverFlee;
	/** The server's way for the takeover (see WebNet.TakeoverPath): the mode, the waypoints (feet, centred), the next one, and the block being torn out. */
	private static int pathMode = WebNet.TakeoverPath.FALLBACK;
	private static java.util.List<Vec3> pathNodes = java.util.List.of();
	private static int pathIndex;
	private static net.minecraft.core.BlockPos ripFocus = net.minecraft.core.BlockPos.ZERO;
	/** A takeover's leap: ticks coiled (-1 none), ticks it coils for, the charge it leaps with; and whether this tick touched it. */
	private static int leapCoil = -1;
	/** An escape leap after a meal: ticks coiled (-1 none), and the way it leaps (flat; zero: straight up). */
	private static int escapeCoil = -1;
	private static Vec3 escapeDir = Vec3.ZERO;
	/** How long past a full coil it waits for footing before it gives the leap up. */
	private static final int ESCAPE_GROUND_WAIT = 40;
	private static int leapTicks;
	private static float leapCharge;
	private static boolean leapTouched;
	/** The takeover has you sprinting (vanilla mustn't stop it for want of a forward key; see WebSprintMixin). */
	private static boolean sprintHeld;
	private static final Set<Integer> THREATS = new HashSet<>();
	private static long threatsAt;

	private WebSymbioteClient() {
	}

	static void init() {
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.web_symbiote", InputConstants.KEY_X, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			// worked out once a tick, not once per entity per frame
			LocalPlayer self = minecraft.player;
			sensing = self != null && WebPowerClient.isActive(self) && Fate.isAscended(self);
			while (menuKey.consumeClick()) {
				LocalPlayer player = minecraft.player;
				if (inputLocked()) {
					struggle();
					continue;
				}
				if (player != null && minecraft.gui.screen() == null && !player.isSpectator() && WebSymbiote.isBonded(player) && takeoverTicks <= 0
					&& !player.isShiftKeyDown()) {
					minecraft.gui.setScreen(new WebSymbioteScreen());
				} else if (player != null && minecraft.gui.screen() == null && !player.isSpectator() && WebPowerClient.isActive(player)
					&& takeoverTicks <= 0) {
					// a web slinger's own Power Skill (Sneak + X in the symbiote): webbed fists
					ClientPlayNetworking.send(new com.jaymospowers.power.web.WebNet.Fists());
				} else if (player != null && minecraft.gui.screen() == null && !player.isSpectator()
					&& com.jaymospowers.client.power.wind.WindPowerClient.isActive(player)) {
					// the same key is Wind's Tempest Kick
					ClientPlayNetworking.send(new com.jaymospowers.power.wind.WindKick.Kick());
				}
			}
		});
		// a takeover has your hands too: clicks are struggles, and nothing is used
		ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
			if (!inputLocked()) {
				return;
			}
			while (minecraft.options.keyUse.consumeClick()) {
				struggle();
			}
			minecraft.options.keyUse.setDown(false);
			minecraft.options.keyAttack.setDown(false);
		});
		ClientPlayNetworking.registerGlobalReceiver(WebNet.Escape.TYPE, (payload, context) -> {
			Vec3 dir = new Vec3(payload.x(), 0.0, payload.z());
			escapeDir = dir.lengthSqr() < 1.0E-4 ? Vec3.ZERO : dir.normalize();
			escapeCoil = 0;
			if (ClientPlayNetworking.canSend(WebNet.ChargeHold.TYPE)) {
				ClientPlayNetworking.send(new WebNet.ChargeHold(true));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(WebNet.Takeover.TYPE, (payload, context) -> {
			takeoverTarget = payload.entityId();
			takeoverTicks = payload.entityId() < 0 ? 0 : payload.ticks();
			takeoverFlee = payload.flee();
			if (takeoverTicks <= 0) {
				clearPath();
				struggleMeter = 0.0F;
			}
			if (takeoverTicks > 0 && context.client().gui.screen() instanceof WebSymbioteScreen) {
				context.client().gui.setScreen(null);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(WebNet.TakeoverPath.TYPE, (payload, context) -> {
			pathMode = payload.mode();
			ripFocus = payload.focus();
			java.util.List<Vec3> nodes = new java.util.ArrayList<>(payload.nodes().size());
			for (net.minecraft.core.BlockPos pos : payload.nodes()) {
				nodes.add(Vec3.atBottomCenterOf(pos));
			}
			pathNodes = nodes;
			pathIndex = 0;
			LocalPlayer player = context.player();
			if (player != null && !nodes.isEmpty()) {
				// pick up from the nearest of the first few (the path starts where the server last saw you)
				double best = Double.MAX_VALUE;
				for (int i = 0; i < Math.min(4, nodes.size()); i++) {
					double d = nodes.get(i).distanceToSqr(player.position());
					if (d < best) {
						best = d;
						pathIndex = i;
					}
				}
			}
		});
		// its voice darkens your screen (whispers, and a whole feeding)
		ClientPlayNetworking.registerGlobalReceiver(WebNet.Dread.TYPE, (payload, context) -> WebSymbioteDread.onPacket(payload));
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.MISC_OVERLAYS,
			com.jaymospowers.JaymosPowers.id("web_symbiote_dread"), WebSymbioteDread::render);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WebSymbioteDread.clear());
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!minecraft.isPaused()) {
				WebSymbioteDread.tick();
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(WebNet.Nudge.TYPE, (payload, context) -> {
			LocalPlayer player = context.player();
			if (player != null) {
				player.setDeltaMovement(payload.velocity());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(WebNet.Shove.TYPE, (payload, context) -> {
			LocalPlayer player = context.player();
			if (player != null) {
				player.setDeltaMovement(player.getDeltaMovement().add(payload.add()));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(WebNet.Sense.TYPE, (payload, context) -> {
			THREATS.clear();
			THREATS.addAll(payload.ids());
			threatsAt = context.player() == null ? 0L : context.player().level().getGameTime();
		});
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new WebSymbioteLayer(avatar));
				helper.register(new WebTendrilLayer(avatar));
				helper.register(new WebMouthLayer(avatar));
				helper.register(new WebSpikeLayer(avatar));
				helper.register(new WebMorphLayer(avatar));
				helper.register(new WebSuitWrapLayer(avatar));
			}
		});
	}

	static void reset() {
		wallClinging = false;
		ceilingClinging = false;
		climbAt = null;
		climbSent = false;
		struggleMeter = 0.0F;
		takeoverTicks = 0;
		takeoverTarget = -1;
		escapeCoil = -1;
		clearPath();
		THREATS.clear();
	}

	/** Worn under the suit: swallowed by it, unless it's an elytra. */
	private static net.minecraft.world.item.ItemStack engulfed(net.minecraft.world.item.ItemStack worn) {
		return worn.has(net.minecraft.core.component.DataComponents.GLIDER) ? worn : net.minecraft.world.item.ItemStack.EMPTY;
	}

	/**
	 * This is you, drawn in first person by First-person Model, and it trusts you enough to let you see: the suit's head
	 * (the mask, the swollen head, the maw) isn't drawn, so it doesn't cover your view. Under
	 * {@link WebSymbiote#MASK_CLEAR_BOND} it is: you're looking out through its mask, not quite in control.
	 */
	public static boolean ownFirstPerson(AvatarRenderState state) {
		net.minecraft.client.player.LocalPlayer self = net.minecraft.client.Minecraft.getInstance().player;
		return self != null && state.id == self.getId() && FirstPersonCompat.bodyShown()
			&& WebSymbiote.state(self).bond() >= WebSymbiote.MASK_CLEAR_BOND;
	}

	/** From the avatar renderer's extractRenderState: does this player wear the suit. */
	public static void extract(Entity entity, AvatarRenderState state) {
		state.setData(SUIT, entity instanceof Player player && WebSymbiote.suitShown(player) ? Boolean.TRUE : null);
		state.setData(FERAL, entity instanceof Player player && WebSymbiote.isBonded(player)
			? 1.0F - WebSymbiote.state(player).bond() / (float)WebSymbiote.BOND_MAX : null);
		WebMorphLayer.extract(entity, state);
		if (entity instanceof Player player) {
			WebSuitGrowth.extract(player, state, net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
		}
		Long slam = entity.getAttached(com.jaymospowers.power.web.WebSlam.STANCE);
		state.setData(SLAM, slam == null ? null : Math.min(1.0F, (entity.level().getGameTime() - slam
			+ net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) / com.jaymospowers.power.web.WebSlam.RAISE_TICKS));
		// coiling for a charged leap: deeper the longer it's held (WebSwing.CHARGE_FULL)
		if (entity.getAttached(com.jaymospowers.power.web.WebPower.CHARGING) != null) {
			long now = System.nanoTime();
			long since = COILING.computeIfAbsent(entity.getId(), id -> now);
			state.setData(CHARGE, Math.min(1.0F, (now - since) / 1.0E9F / (WebSwing.CHARGE_FULL / 20.0F)));
		} else {
			COILING.remove(entity.getId());
			state.setData(CHARGE, null);
		}
		WebSymbiote.Meal meal = entity.getAttached(WebSymbiote.EATING);
		state.setData(MEAL_REACH, meal == null ? null : meal.reach());
		state.setData(EATING, meal == null ? null : Math.max(0.0F, entity.level().getGameTime() - meal.start() + net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
		state.setData(MEAL_BIG, meal != null && meal.big());
		if (state.getData(SUIT) != null || entity instanceof Player p && WebSymbiote.isToggled(p, WebSymbioteState.BULK)) {
			// no cape poking out through the suit, and the armour engulfed by it (an elytra still spreads from it)
			state.showCape = false;
			state.headEquipment = engulfed(state.headEquipment);
			state.chestEquipment = engulfed(state.chestEquipment);
			state.legsEquipment = engulfed(state.legsEquipment);
			state.feetEquipment = engulfed(state.feetEquipment);
		}
		// Living Armor grows tendrils (whether or not the suit is showing); the big body swells under them
		boolean armored = entity instanceof Player p && WebSymbiote.armored(p);
		boolean bulk = entity instanceof Player p && WebSymbiote.bulked(p);
		Long staring = entity.getAttached(WebSymbiote.STARING);
		com.jaymospowers.power.web.WebPose roarPose = entity.getAttached(com.jaymospowers.power.web.WebPower.POSE);
		float roarAt = roarPose == null || roarPose.kind() != com.jaymospowers.power.web.WebPose.ROAR ? -1.0F
			: roarPose.progress(entity.level().getGameTime() + net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
		state.setData(ROAR, roarAt >= 0.0F && roarAt < 1.0F && entity instanceof Player p && WebSymbiote.isBonded(p) ? roarAt : null);
		state.setData(SPIKES, entity instanceof Player p && WebSymbiote.isBonded(p) ? WebSpikeLayer.age(roarPose,
			entity.level().getGameTime() + net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) : null);
		com.jaymospowers.power.web.WebCounter.Active counter = entity.getAttached(com.jaymospowers.power.web.WebCounter.ACTIVE);
		float counterAt = counter == null || !com.jaymospowers.power.web.WebCounter.mawKind(counter.kind()) ? -1.0F
			: entity.level().getGameTime() - counter.start() + net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		state.setData(COUNTER_MAW, counterAt >= 0.0F && counterAt < com.jaymospowers.power.web.WebCounter.ticks(counter.kind())
			&& entity instanceof Player p && WebSymbiote.isBonded(p) ? new float[] {counterAt, counter.kind()} : null);
		state.setData(STARE, staring == null ? null : Math.max(0.0F, entity.level().getGameTime() - staring
			+ net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
		long out = entity instanceof Player p && WebSymbiote.isBonded(p) ? WebSymbiote.tendrilsOut(p) : -1L;
		// Wall Crawl on: they stay out the whole time (holding Jump, coiling a leap, between grabs), not only while they hold you
		boolean climbing = entity instanceof Player p && WebSymbiote.isBonded(p)
			&& (WebTendrilWalk.climbing(p) || WebSymbiote.isToggled(p, WebSymbioteState.CRAWL));
		if (entity instanceof Player player && (armored || out >= 0 || climbing)) {
			long now = player.level().getGameTime();
			long since = ARMOR_SINCE.computeIfAbsent(player.getId(), id -> now);
			float growth = Math.min(1.0F, (now - since) / WebTendrilLayer.GROWN_TICKS);
			WITHDRAW.remove(player.getId());
			if (armored) {
				LAST_GROWTH.put(player.getId(), growth);
			} else if (climbing) {
				// out on their own to climb: quick to bud, there as long as you climb (then drawn back in); once one has
				// bitten into the wall they're all the way out at once
				growth = player.getAttached(com.jaymospowers.power.web.WebTendrils.CLIMB) != null ? 1.0F : Math.min(1.0F, growth * 1.6F);
				LAST_GROWTH.put(player.getId(), growth);
			} else {
				// out on their own: they lash out, then draw back in
				growth = Math.min(growth * 1.6F, Math.max(0.0F, (out - now) / 8.0F));
			}
			// a strike of their own (the server picks the target)
			float strike = -1.0F, aimYaw = 0.0F, aimPitch = 0.0F, side = 1.0F;
			boolean heavy = false;
			int variant = WebSymbiote.STRIKE_SLAM;
			float rear = WebSymbiote.STRIKE_REAR, hit = WebSymbiote.STRIKE_HIT / (float)WebSymbiote.STRIKE_TICKS;
			WebSymbiote.Strike s = player.getAttached(WebSymbiote.STRIKE);
			Entity target = s == null ? null : player.level().getEntity(s.target());
			if (s != null && target != null) {
				float partial = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
				strike = net.minecraft.util.Mth.clamp((now - s.start() + partial) / (float)s.ticks(), 0.0F, 1.0F);
				heavy = s.heavy();
				variant = s.variant();
				rear = s.rearShare();
				hit = s.hitShare();
				net.minecraft.world.phys.Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
				float yaw = (float)(net.minecraft.util.Mth.atan2(to.z, to.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90.0F;
				aimYaw = net.minecraft.util.Mth.wrapDegrees(yaw - player.yBodyRot) * net.minecraft.util.Mth.DEG_TO_RAD;
				aimPitch = (float)net.minecraft.util.Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z));
				side = aimYaw >= 0.0F ? -1.0F : 1.0F;
			}
			// a combo outranks a strike: every upper tendril joins in (clap, crush or shatter)
			int combo = -1;
			com.jaymospowers.power.web.WebTendrils.Combo c = player.getAttached(com.jaymospowers.power.web.WebTendrils.COMBO);
			if (c != null) {
				float partial = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
				combo = c.kind();
				strike = net.minecraft.util.Mth.clamp((now - c.start() + partial) / (float)com.jaymospowers.power.web.WebTendrilCombos.ticks(c), 0.0F, 1.0F);
				heavy = true;
				variant = WebSymbiote.STRIKE_SLAM;
				rear = com.jaymospowers.power.web.WebTendrilCombos.REAR_TICKS / (float)com.jaymospowers.power.web.WebTendrilCombos.TICKS;
				hit = com.jaymospowers.power.web.WebTendrilCombos.HIT_TICKS / (float)com.jaymospowers.power.web.WebTendrilCombos.TICKS;
				if (combo == com.jaymospowers.power.web.WebTendrilCombos.THRASH) {
					// the rest of the upper tendrils slam along with each blow, one cycle at a time
					int lead = c.first() >= 0 ? c.first() : c.second();
					float tt = com.jaymospowers.power.web.WebTendrilCombos.thrashTime(c, lead, now - c.start()) + partial;
					int cycle = com.jaymospowers.power.web.WebTendrilCombos.THRASH_CYCLE;
					strike = tt < 0.0F || tt >= com.jaymospowers.power.web.WebTendrilCombos.thrashSlams(c) * cycle ? -1.0F : (tt % cycle) / cycle;
					rear = com.jaymospowers.power.web.WebTendrilCombos.THRASH_RAISE / (float)cycle;
					hit = (com.jaymospowers.power.web.WebTendrilCombos.THRASH_RAISE + com.jaymospowers.power.web.WebTendrilCombos.THRASH_DOWN) / (float)cycle;
				}
				Entity victim = combo == com.jaymospowers.power.web.WebTendrilCombos.CRUSH ? player.level().getEntity(c.second())
					: combo == com.jaymospowers.power.web.WebTendrilCombos.BARRAGE ? player.level().getEntity(c.first()) : null;
				net.minecraft.world.phys.Vec3 at = victim != null ? victim.getBoundingBox().getCenter()
					: combo == com.jaymospowers.power.web.WebTendrilCombos.THRASH
						? player.position().add(player.getLookAngle().horizontal().normalize().scale(com.jaymospowers.power.web.WebTendrilCombos.THRASH_FORWARD))
					: player.getEyePosition().add(player.getLookAngle().horizontal().normalize().scale(2.4)).subtract(0.0, 0.3, 0.0);
				net.minecraft.world.phys.Vec3 to = at.subtract(player.getEyePosition());
				float yaw = (float)(net.minecraft.util.Mth.atan2(to.z, to.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90.0F;
				aimYaw = net.minecraft.util.Mth.wrapDegrees(yaw - player.yBodyRot) * net.minecraft.util.Mth.DEG_TO_RAD;
				aimPitch = (float)net.minecraft.util.Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z));
				side = aimYaw >= 0.0F ? -1.0F : 1.0F;
			}
			int held = 0;
			com.jaymospowers.power.web.WebTendrils.Grips grips = player.getAttached(com.jaymospowers.power.web.WebTendrils.GRIPS);
			if (grips != null) {
				boolean mainRight = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
				for (int hand = com.jaymospowers.power.web.WebNet.MAIN; hand <= com.jaymospowers.power.web.WebNet.OFF; hand++) {
					if (grips.get(hand) >= 0) {
						boolean right = (hand == com.jaymospowers.power.web.WebNet.MAIN) == mainRight;
						held |= right ? 1 : 2;
					}
				}
			}
			int wallMode = 0;
			float wallYaw = 0.0F, wallPitch = 0.0F;
			WebSymbiote.Wall wall = player.getAttached(WebSymbiote.WALL);
			if (wall != null) {
				wallMode = wall.landed() < 0L ? 1 : 2;
				net.minecraft.world.phys.Vec3 to = wall.anchor().subtract(player.getEyePosition());
				float yaw = (float)(net.minecraft.util.Mth.atan2(to.z, to.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90.0F;
				wallYaw = net.minecraft.util.Mth.wrapDegrees(yaw - player.yBodyRot) * net.minecraft.util.Mth.DEG_TO_RAD;
				wallPitch = (float)net.minecraft.util.Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z));
			} else if (player.getAttached(WebSymbiote.RIPPING) instanceof net.minecraft.core.BlockPos ripping) {
				// tearing a way through: every tendril reaching into the block
				wallMode = 1;
				net.minecraft.world.phys.Vec3 to = net.minecraft.world.phys.Vec3.atCenterOf(ripping).subtract(player.getEyePosition());
				float yaw = (float)(net.minecraft.util.Mth.atan2(to.z, to.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90.0F;
				wallYaw = net.minecraft.util.Mth.wrapDegrees(yaw - player.yBodyRot) * net.minecraft.util.Mth.DEG_TO_RAD;
				wallPitch = (float)net.minecraft.util.Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z));
			}
			boolean firstPerson = player == net.minecraft.client.Minecraft.getInstance().player && FirstPersonCompat.bodyShown();
			// roaring: they burst out fast and flail
			float roar = -1.0F;
			com.jaymospowers.power.web.WebPose pose = player.getAttached(com.jaymospowers.power.web.WebPower.POSE);
			float partialTick = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			if (pose != null && pose.kind() == com.jaymospowers.power.web.WebPose.ROAR) {
				float p = pose.progress(now + partialTick);
				if (p >= 0.0F && p < 1.0F) {
					roar = p;
					growth = Math.max(growth, Math.min(1.0F, (now - since + partialTick) / ROAR_GROW_TICKS));
				}
			}
			// hung off a wall: each lower tendril on its own tether
			float[] tethers = wall != null && wall.landed() >= 0L && !wall.points().isEmpty() ? WebTendrilLayer.tethers(player, wall, partialTick) : null;
			// climbing: four of them walk you along the wall
			float[] legs = tethers == null && wall == null ? WebTendrilLayer.walk(player, partialTick) : null;
			state.setData(RETRACT, null);
			state.setData(TENDRILS, new Tendrils(growth, player.tickCount, strike, aimYaw, aimPitch, side, smoothRun(player), held, bulk,
				wallMode, wallYaw, wallPitch, heavy, variant, firstPerson, combo, rear, hit,
				1.0F - WebSymbiote.state(player).bond() / (float)WebSymbiote.BOND_MAX, roar, tethers, legs));
		} else {
			ARMOR_SINCE.remove(entity.getId());
			state.setData(TENDRILS, withdrawing(entity));
			state.setData(RETRACT, retract(entity));
		}
	}

	/** Living Armor just ended: the tendrils shrink back into the body the way they grew, or null once they're in. */
	private static @org.jspecify.annotations.Nullable Tendrils withdrawing(Entity entity) {
		Float last = LAST_GROWTH.get(entity.getId());
		if (!(entity instanceof Player player) || last == null || !WebSymbiote.isBonded(player)) {
			LAST_GROWTH.remove(entity.getId());
			WITHDRAW.remove(entity.getId());
			return null;
		}
		long now = player.level().getGameTime();
		long start = WITHDRAW.computeIfAbsent(player.getId(), id -> now);
		float growth = last - (now - start) / WITHDRAW_TICKS;
		if (growth <= 0.0F) {
			LAST_GROWTH.remove(player.getId());
			WITHDRAW.remove(player.getId());
			return null;
		}
		boolean firstPerson = player == net.minecraft.client.Minecraft.getInstance().player && FirstPersonCompat.bodyShown();
		return new Tendrils(growth, player.tickCount, -1.0F, 0.0F, 0.0F, 1.0F, smoothRun(player), 0, WebSymbiote.bulked(player),
			0, 0.0F, 0.0F, false, WebSymbiote.STRIKE_SLAM, firstPerson, -1, WebSymbiote.STRIKE_REAR,
			WebSymbiote.STRIKE_HIT / (float)WebSymbiote.STRIKE_TICKS, 1.0F - WebSymbiote.state(player).bond() / (float)WebSymbiote.BOND_MAX, -1.0F, null, null);
	}

	/** How far the tendrils have drawn back in (0-1), or null when they aren't. */
	private static @org.jspecify.annotations.Nullable Float retract(Entity entity) {
		Float last = LAST_GROWTH.get(entity.getId());
		Long start = WITHDRAW.get(entity.getId());
		if (last == null || start == null || last <= 0.0F) {
			return null;
		}
		float t = (entity.level().getGameTime() - start) / (WITHDRAW_TICKS * last);
		return t >= 0.0F && t < 1.0F ? t : null;
	}

		/** First person, after the hands: your own tendrils, unless First-person Model is already drawing your body. */
	public static void firstPersonTendrils(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
		@org.jspecify.annotations.Nullable AvatarRenderState avatar) {
		if (avatar != null) {
			WebTendrilLayer.submitFirstPerson(poseStack, collector, avatar);
		}
	}

	// ---- per tick (while the web power is active) --------------------------------------------------------------------

	static void tick(Minecraft minecraft, LocalPlayer player, WebSwing swing) {
		// tethered to a wall by the tendrils: hung off it on them, leaning against their pull (a web of yours outpulls them)
		WebSymbiote.Wall wall = player.getAttached(WebSymbiote.WALL);
		if (wall != null && wall.landed() >= 0L && !player.input.keyPresses.jump() && !swing.attached() && swing.pull == null) {
			tether(player, wall);
		} else {
			lean = Vec3.ZERO;
		}
		// roaring: it holds you where you stand
		if (WebSymbiote.roaring(player)) {
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(0.0, Math.min(v.y, 0.0), 0.0);
		}
		if (escapeCoil >= 0 && takeoverTicks <= 0) {
			tickEscape(player);
		} else if (takeoverTicks > 0) {
			tickTakeover(player);
			struggleMeter = Math.max(0.0F, struggleMeter - WebSymbiote.TAKEOVER_STRUGGLE_FADE);
		} else {
			tickCrawl(player, swing);
		}
	}

	/** How far you're leaning away from the tether spot right now (eases toward where the keys push). */
	private static Vec3 lean = Vec3.ZERO;

	/**
	 * Hung off a wall: a springy pull toward the spot (lifting you off the wall at first). You sway the way you look
	 * (up to {@link WebSymbiote#WALL_AIM_SHIFT} along the wall), and the movement keys lean you further (as far as
	 * {@link WebSymbiote#WALL_STRETCH}); let go of them and it hauls you back, bouncing on the tethers like elastic.
	 */
	private static void tether(LocalPlayer player, WebSymbiote.Wall wall) {
		Vec3 target = wall.target(player.level().getGameTime() - wall.landed()).add(sway(player, wall));
		Input keys = player.input.keyPresses;
		double forward = (keys.forward() ? 1.0 : 0.0) - (keys.backward() ? 1.0 : 0.0);
		double strafe = (keys.left() ? 1.0 : 0.0) - (keys.right() ? 1.0 : 0.0);
		Vec3 ahead = Vec3.directionFromRotation(0.0F, player.getYRot());
		Vec3 left = new Vec3(ahead.z, 0.0, -ahead.x);
		Vec3 push = ahead.scale(forward).add(left.scale(strafe));
		Vec3 want = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(WebSymbiote.WALL_STRETCH);
		lean = lean.add(want.subtract(lean).scale(WebSymbiote.WALL_LEAN_RATE));
		Vec3 gap = target.add(lean).subtract(player.position());
		Vec3 v = player.getDeltaMovement().scale(WebSymbiote.WALL_CARRY).add(gap.scale(WebSymbiote.WALL_SPRING));
		// (holding up against gravity's pull this tick)
		player.setDeltaMovement(v.add(0.0, player.getGravity(), 0.0));
		player.resetFallDistance();
	}

	/** Where you look, along the wall (its plane only): how far you sway that way on the tethers. */
	static Vec3 sway(net.minecraft.world.entity.player.Player player, WebSymbiote.Wall wall) {
		Vec3 normal = net.minecraft.core.Direction.from3DDataValue(wall.face()).getUnitVec3();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 along = look.subtract(normal.scale(look.dot(normal)));
		return along.scale(WebSymbiote.WALL_AIM_SHIFT);
	}

	/**
	 * Mouse look while hung off a wall: held facing away from it (see {@link WebSymbiote#WALL_LOOK}). The last-frame
	 * rotation moves with any correction, so the view stops at the edge rather than jittering past it.
	 */
	public static void holdLook(net.minecraft.world.entity.Entity entity) {
		if (entity != net.minecraft.client.Minecraft.getInstance().player) {
			return;
		}
		WebSymbiote.Wall wall = entity.getAttached(WebSymbiote.WALL);
		if (wall == null || wall.landed() < 0L) {
			return;
		}
		net.minecraft.core.Direction face = net.minecraft.core.Direction.from3DDataValue(wall.face());
		if (face.getAxis().isHorizontal()) {
			Vec3 normal = face.getUnitVec3();
			float out = (float)(Mth.atan2(normal.z, normal.x) * Mth.RAD_TO_DEG) - 90.0F;
			float off = Mth.wrapDegrees(entity.getYRot() - out);
			float held = Mth.clamp(off, -WebSymbiote.WALL_LOOK, WebSymbiote.WALL_LOOK);
			if (held != off) {
				entity.setYRot(entity.getYRot() + held - off);
				entity.yRotO += held - off;
			}
		} else {
			// on a ceiling you look no higher than level, on a floor no lower
			float pitch = entity.getXRot();
			float held = face == net.minecraft.core.Direction.DOWN ? Math.max(0.0F, pitch) : Math.min(0.0F, pitch);
			if (held != pitch) {
				entity.setXRot(held);
				entity.xRotO += held - pitch;
			}
		}
	}

	/** A takeover has you: no key you press does what it would (each is a struggle against its hold instead). */
	public static boolean inputLocked() {
		return takeoverTicks > 0;
	}

	/** Each struggle press shakes your view this much (degrees), and this much more with the bar full. */
	private static final float STRUGGLE_SHAKE = 0.25F, STRUGGLE_SHAKE_PER = 0.6F;

	/** A takeover has this player (yours from the takeover packet; anyone's from the synced {@link WebSymbiote#STRUGGLE}). */
	public static boolean seized(Player player) {
		return player == Minecraft.getInstance().player && takeoverTicks > 0 || player.getAttached(WebSymbiote.STRUGGLE) != null;
	}

	/**
	 * How far this player has fought out of a takeover's hold (0-1): your own from the local tally (it answers each
	 * press at once), anyone else's as the server syncs it.
	 */
	public static float struggleShare(Player player) {
		if (player == Minecraft.getInstance().player && takeoverTicks > 0) {
			int needed = WebSymbiote.breakFreeNeeded(player);
			return needed > 0 ? Mth.clamp(struggleMeter / needed, 0.0F, 1.0F) : 0.0F;
		}
		Float share = player.getAttached(WebSymbiote.STRUGGLE);
		return share == null ? 0.0F : Mth.clamp(share, 0.0F, 1.0F);
	}

	/** The movement keys during a takeover: each new press is a struggle. */
	public static void struggleKeys(Input keys) {
		boolean pressed = keys.forward() && !heldKeys.forward() || keys.backward() && !heldKeys.backward() || keys.left() && !heldKeys.left()
			|| keys.right() && !heldKeys.right() || keys.jump() && !heldKeys.jump() || keys.shift() && !heldKeys.shift()
			|| keys.sprint() && !heldKeys.sprint();
		heldKeys = keys;
		if (pressed) {
			struggle();
		}
	}

	/** The power keys during a takeover (from the power key loop): each new press is a struggle. */
	public static void strugglePowerKey(int index, boolean down) {
		if (index < 0 || index >= powerKeysWere.length) {
			return;
		}
		if (down && !powerKeysWere[index]) {
			struggle();
		}
		powerKeysWere[index] = down;
	}

	/** One press against its hold (at most one a tick reaches the server; see WebSymbiote.breakFreeNeeded). */
	public static void struggle() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !inputLocked() || player.tickCount == struggleTick) {
			return;
		}
		struggleTick = player.tickCount;
		if (WebSymbiote.breakFreeNeeded(player) > 0) {
			struggleMeter += 1.0F;
			// every heave against it jolts your view, harder the further out of it you've torn
			com.jaymospowers.client.ScreenShake.add(STRUGGLE_SHAKE + STRUGGLE_SHAKE_PER * struggleShare(player));
		}
		if (ClientPlayNetworking.canSend(WebNet.Struggle.TYPE)) {
			ClientPlayNetworking.send(new WebNet.Struggle());
		}
	}

	private static void clearPath() {
		pathMode = WebNet.TakeoverPath.FALLBACK;
		pathNodes = java.util.List.of();
		pathIndex = 0;
		sprintHeld = false;
		endLeap();
	}

	/** A takeover has the legs right now: the suit's own charged jump stands aside (its steps are plain hops). */
	public static boolean driving() {
		return takeoverTicks > 0 || escapeCoil >= 0;
	}

	/** It's coiling (or just leapt) to get you clear after a meal: the movement keys don't answer. */
	public static boolean escaping() {
		return escapeCoil >= 0;
	}

	/** Fed on a kill, it coils where you stand and leaps clear (see {@link WebSymbiote#ESCAPE_CHANCE}). */
	private static void tickEscape(LocalPlayer player) {
		escapeCoil++;
		if (player.isInWater() || player.isPassenger() || escapeCoil > com.jaymospowers.power.web.WebLeap.CHARGE_FULL + ESCAPE_GROUND_WAIT) {
			// (it never found its footing)
			endEscape();
			return;
		}
		if (!escapeDir.equals(Vec3.ZERO)) {
			turnToward(player, escapeDir, FLEE_TURN, false);
		}
		sprint(player, false);
		Vec3 v = player.getDeltaMovement();
		if (escapeCoil < com.jaymospowers.power.web.WebLeap.CHARGE_FULL || !player.onGround()) {
			// coiling: down low, still but for a little give
			if (escapeCoil % LEAP_SOUND_EVERY == 0 && escapeCoil < com.jaymospowers.power.web.WebLeap.CHARGE_FULL) {
				WebSwing.play(player, net.minecraft.sounds.SoundEvents.SLIME_SQUISH_SMALL, 0.5F,
					0.6F + escapeCoil / (float)com.jaymospowers.power.web.WebLeap.CHARGE_FULL);
			}
			if (player.onGround()) {
				player.setDeltaMovement(v.x * LEAP_HOLD, v.y, v.z * LEAP_HOLD);
			}
			return;
		}
		// the full charge, up and away
		Vec3 out = escapeDir.scale(com.jaymospowers.power.web.WebLeap.CHARGE_FORWARD_MAX);
		player.setDeltaMovement(out.x, com.jaymospowers.power.web.WebLeap.speed(1.0F), out.z);
		WebSwing.play(player, net.minecraft.sounds.SoundEvents.SLIME_JUMP, 1.0F, 0.5F);
		WebSwing.play(player, net.minecraft.sounds.SoundEvents.WIND_CHARGE_BURST.value(), 0.8F, 0.6F);
		endEscape();
	}

	private static void endEscape() {
		if (escapeCoil >= 0 && ClientPlayNetworking.canSend(WebNet.ChargeHold.TYPE)) {
			ClientPlayNetworking.send(new WebNet.ChargeHold(false));
		}
		escapeCoil = -1;
	}

	/** The takeover wants you sprinting. */
	public static boolean holdsSprint() {
		return takeoverTicks > 0 && sprintHeld;
	}

	/** Food enough (or flight) to sprint, and not swimming. */
	private static boolean canSprint(LocalPlayer player) {
		return (player.getFoodData().hasEnoughFood() || player.getAbilities().mayfly) && !player.isInWater();
	}

	private static void sprint(LocalPlayer player, boolean on) {
		sprintHeld = on;
		player.setSprinting(on);
	}

	/** Lets go of a coil (the server stops showing the crouch). */
	private static void endLeap() {
		if (leapCoil >= 0 && ClientPlayNetworking.canSend(WebNet.ChargeHold.TYPE)) {
			ClientPlayNetworking.send(new WebNet.ChargeHold(false));
		}
		leapCoil = -1;
	}

	/** It's steering, view and all: along the server's path, straight at its prey when it's right there, or still while it rips. */
	private static void tickTakeover(LocalPlayer player) {
		Entity target = player.level().getEntity(takeoverTarget);
		// (the symbiote gone, a power swapped away: it can't be driving any more)
		if (target == null || !target.isAlive() || --takeoverTicks <= 0 || !WebSymbiote.isBonded(player)) {
			takeoverTicks = 0;
			clearPath();
			return;
		}
		leapTouched = false;
		if (target == player) {
			// its kill carried off and eaten: it holds you still till it's done
			sprint(player, false);
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x * RIP_HOLD, v.y, v.z * RIP_HOLD);
		} else if (pathMode == WebNet.TakeoverPath.RIP) {
			holdForRip(player);
		} else if (pathMode == WebNet.TakeoverPath.PATH && followPath(player, target)) {
			// on its way
		} else if (takeoverFlee) {
			flee(player, target);
		} else {
			straight(player, target);
		}
		if (!leapTouched && leapCoil >= 0) {
			endLeap();
		}
	}

	/** The tendrils are tearing a way through: it stands its ground and stares at the block. */
	private static void holdForRip(LocalPlayer player) {
		Vec3 to = Vec3.atCenterOf(ripFocus).subtract(player.getEyePosition());
		turnToward(player, to, TAKEOVER_TURN, true);
		sprint(player, false);
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x * RIP_HOLD, v.y, v.z * RIP_HOLD);
	}

	/** Turns the view toward {@code to} (a share of the way each tick); {@code pitch}: up and down too, else levels out. */
	private static void turnToward(LocalPlayer player, Vec3 to, float share, boolean pitch) {
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * share);
		if (pitch) {
			float p = (float)(-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
			player.setXRot(Mth.clamp(player.getXRot() + (p - player.getXRot()) * share, -90.0F, 90.0F));
		} else {
			player.setXRot(player.getXRot() * 0.8F);
		}
	}

	/**
	 * Walks the server's waypoints: faces and moves toward the next, hops up steps, eases off at corners, and won't
	 * walk off an edge the path doesn't drop down. False once the path has run out (the caller takes over).
	 */
	private static boolean followPath(LocalPlayer player, Entity target) {
		Vec3 pos = player.position();
		if (!takeoverFlee && target.position().subtract(pos).horizontalDistance() <= TAKEOVER_STOP && Math.abs(target.getY() - pos.y) < 1.5) {
			straight(player, target);
			return true;
		}
		int n = pathNodes.size();
		// reached (or already past) the next waypoint: on to the one after
		for (int j = Math.min(n - 1, pathIndex + WAYPOINT_SKIP); j >= pathIndex; j--) {
			Vec3 node = pathNodes.get(j);
			double reach = j + 1 < n && corner(j) ? CORNER_REACH : WAYPOINT_REACH;
			if (node.subtract(pos).horizontalDistance() <= reach && Math.abs(node.y - pos.y) <= WAYPOINT_REACH_Y) {
				pathIndex = j + 1;
				break;
			}
		}
		if (pathIndex >= n) {
			return false;
		}
		Vec3 node = pathNodes.get(pathIndex);
		Vec3 flat = new Vec3(node.x - pos.x, 0.0, node.z - pos.z);
		double dh = flat.length();
		Vec3 dir = dh < 1.0E-4 ? Vec3.ZERO : flat.scale(1.0 / dh);
		if (takeoverFlee) {
			turnToward(player, flat, FLEE_TURN, false);
		} else {
			// it walks the way but its eyes stay on the prey
			Vec3 at = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
			turnToward(player, dir.lengthSqr() > 0.0 ? dir : at, TAKEOVER_TURN, false);
			float p = (float)(-Mth.atan2(at.y, at.horizontalDistance()) * Mth.RAD_TO_DEG);
			player.setXRot(Mth.clamp(player.getXRot() + (p - player.getXRot()) * TAKEOVER_TURN, -90.0F, 90.0F));
		}
		// a ledge too tall to step: a charged leap (charged only as far as it needs)
		double rise = landing(player, node) - pos.y;
		boolean ledge = rise > LEAP_MIN_RISE && !player.isInWater();
		if (ledge && (player.onGround() || leapCoil >= 0) && leapUp(player, dir, dh, rise)) {
			return true;
		}
		// sprinting, but for a tight corner, a step up, an edge, a leap or an empty belly
		double sprintSpeed = TAKEOVER_SPEED * (takeoverFlee ? FLEE_SPEED_SCALE : 1.0);
		boolean step = node.y > pos.y + STEP_JUMP && dh < STEP_JUMP_RANGE;
		boolean corner = pathIndex + 1 < n && dh < CORNER_SLOW_RANGE && corner(pathIndex);
		boolean edge = player.onGround() && node.y >= pos.y - 0.5 && !groundAhead(player, dir.scale(sprintSpeed));
		boolean sprinting = canSprint(player) && !step && !corner && !edge && !ledge;
		sprint(player, sprinting);
		double speed = sprinting ? sprintSpeed : WALK_SPEED;
		// never faster than it takes to land on the waypoint
		Vec3 move = dir.scale(Math.min(speed, Math.max(dh, speed * 0.5)));
		Vec3 v = player.getDeltaMovement();
		double up = v.y;
		if (player.isInWater()) {
			up = Math.max(up, takeoverFlee ? FLEE_SWIM_UP : SWIM_UP);
		} else if (player.onGround() && !ledge && (step || player.horizontalCollision)) {
			// a step: a plain hop
			up = JUMP_SPEED;
		} else if (player.onGround() && node.y >= pos.y - 0.5 && !groundAhead(player, move)) {
			// an edge the path doesn't go over: creep, straight for the waypoint
			move = move.scale(LEDGE_CREEP);
		}
		player.setDeltaMovement(move.x, up, move.z);
		return true;
	}

	/**
	 * A ledge too tall to step: on to its foot, then coil (only as much charge as the rise needs, never more than
	 * {@link com.jaymospowers.power.web.WebLeap#TAKEOVER_MAX_CHARGE}) and leap. False while it's still walking to the foot.
	 */
	private static boolean leapUp(LocalPlayer player, Vec3 dir, double dh, double rise) {
		leapTouched = true;
		if (leapCoil < 0) {
			if (dh > LEAP_START_RANGE && !player.horizontalCollision) {
				return false;
			}
			double gravity = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
			leapCharge = com.jaymospowers.power.web.WebLeap.chargeFor(rise, gravity, com.jaymospowers.power.web.WebLeap.TAKEOVER_MAX_CHARGE);
			leapTicks = Math.max(1, Mth.ceil(leapCharge * com.jaymospowers.power.web.WebLeap.CHARGE_FULL));
			leapCoil = 0;
			if (ClientPlayNetworking.canSend(WebNet.ChargeHold.TYPE)) {
				ClientPlayNetworking.send(new WebNet.ChargeHold(true));
			}
		}
		if (!player.onGround()) {
			endLeap();
			return false;
		}
		sprint(player, false);
		Vec3 v = player.getDeltaMovement();
		if (++leapCoil < leapTicks) {
			if (leapCoil % LEAP_SOUND_EVERY == 0) {
				WebSwing.play(player, net.minecraft.sounds.SoundEvents.SLIME_SQUISH_SMALL, 0.4F, 0.6F + leapCoil / (float)com.jaymospowers.power.web.WebLeap.CHARGE_FULL);
			}
			player.setDeltaMovement(v.x * LEAP_HOLD, v.y, v.z * LEAP_HOLD);
			return true;
		}
		player.setDeltaMovement(dir.x * LEAP_FORWARD, com.jaymospowers.power.web.WebLeap.speed(leapCharge), dir.z * LEAP_FORWARD);
		WebSwing.play(player, net.minecraft.sounds.SoundEvents.SLIME_JUMP, 1.0F, 0.6F);
		if (leapCharge > 0.5F) {
			WebSwing.play(player, net.minecraft.sounds.SoundEvents.WIND_CHARGE_BURST.value(), 0.6F, 0.6F);
		}
		endLeap();
		return true;
	}

	/** Where your feet land on waypoint {@code node}: on top of whatever's in its block (a slab, a snow layer). */
	private static double landing(LocalPlayer player, Vec3 node) {
		net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(node.x, node.y + 0.01, node.z);
		net.minecraft.world.phys.shapes.VoxelShape shape = player.level().getBlockState(at).getCollisionShape(player.level(), at);
		return node.y + (shape.isEmpty() ? 0.0 : shape.max(net.minecraft.core.Direction.Axis.Y));
	}

	/** Whether the way turns sharply at waypoint {@code i} (coming from where you are, or from the one before). */
	private static boolean corner(int i) {
		if (i + 1 >= pathNodes.size()) {
			return false;
		}
		Vec3 here = pathNodes.get(i);
		Vec3 before = i > 0 ? pathNodes.get(i - 1) : null;
		if (before == null) {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player == null) {
				return false;
			}
			before = player.position();
		}
		Vec3 in = new Vec3(here.x - before.x, 0.0, here.z - before.z);
		Vec3 out = new Vec3(pathNodes.get(i + 1).x - here.x, 0.0, pathNodes.get(i + 1).z - here.z);
		if (in.lengthSqr() < 1.0E-4 || out.lengthSqr() < 1.0E-4) {
			return false;
		}
		return in.normalize().dot(out.normalize()) < CORNER_COS;
	}

	/** Ground (or water) within a short drop where {@code move} takes you in a few ticks. */
	private static boolean groundAhead(LocalPlayer player, Vec3 move) {
		AABB ahead = player.getBoundingBox().move(move.x * LEDGE_LOOK_TICKS, 0.0, move.z * LEDGE_LOOK_TICKS);
		AABB below = new AABB(ahead.minX, ahead.minY - LEDGE_DROP, ahead.minZ, ahead.maxX, ahead.minY - 0.01, ahead.maxZ);
		if (!player.level().noCollision(player, below)) {
			return true;
		}
		net.minecraft.core.BlockPos under = net.minecraft.core.BlockPos.containing(ahead.getCenter().x, ahead.minY - 0.5, ahead.getCenter().z);
		return !player.level().getFluidState(under).isEmpty();
	}

	/** Straight at its prey (it's right there, or there's no path): round small things if it can, hopping steps. */
	private static void straight(LocalPlayer player, Entity target) {
		Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float)(-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
		player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * TAKEOVER_TURN);
		player.setXRot(Mth.clamp(player.getXRot() + (pitch - player.getXRot()) * TAKEOVER_TURN, -90.0F, 90.0F));
		Vec3 flat = new Vec3(to.x, 0.0, to.z);
		sprint(player, false);
		if (flat.lengthSqr() > 1.5 * 1.5) {
			// find a way: straight at it if it's open (or a step it can hop), else the nearest open way round
			Vec3 dir = flat.normalize();
			Vec3 way = null;
			boolean hop = false;
			for (int i = 0; i < TAKEOVER_DETOURS.length && way == null; i++) {
				Vec3 tried = dir.yRot(TAKEOVER_DETOURS[i] * Mth.DEG_TO_RAD);
				AABB ahead = player.getBoundingBox().move(tried.scale(TAKEOVER_LOOK));
				if (player.level().noCollision(player, ahead)) {
					way = tried;
				} else if (player.level().noCollision(player, ahead.move(0.0, 1.1, 0.0))) {
					// a ledge one block up: jump it
					way = tried;
					hop = true;
				}
			}
			if (way == null) {
				way = dir;
				hop = true;
			}
			Vec3 v = player.getDeltaMovement();
			double up = v.y;
			if (player.isInWater()) {
				up = Math.max(up, SWIM_UP);
			} else if (player.onGround() && (hop || player.horizontalCollision)) {
				up = JUMP_SPEED;
			}
			boolean sprinting = canSprint(player) && !hop;
			sprint(player, sprinting);
			Vec3 move = way.scale(sprinting ? TAKEOVER_SPEED : WALK_SPEED);
			player.setDeltaMovement(move.x, up, move.z);
		}
	}

	/**
	 * Running for its life: of the ways round (every 30 degrees), it takes the one most away from the threat that's
	 * clear for a few blocks, liking those under open sky; it sprints, hops steps, and swims up.
	 */
	private static void flee(LocalPlayer player, Entity threat) {
		Vec3 away = player.position().subtract(threat.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal().reverse() : away.normalize();
		Vec3 best = away;
		double bestScore = -10.0;
		boolean hop = false;
		for (int i = 0; i < 12; i++) {
			Vec3 dir = away.yRot(i * 30.0F * Mth.DEG_TO_RAD);
			double score = dir.dot(away) * 2.0;
			boolean blocked = false, step = false;
			for (int d = 1; d <= 3; d++) {
				AABB ahead = player.getBoundingBox().move(dir.scale(d * 0.9));
				if (!player.level().noCollision(player, ahead)) {
					if (d == 1 && player.level().noCollision(player, ahead.move(0.0, 1.1, 0.0))) {
						step = true;
						continue;
					}
					blocked = true;
					break;
				}
			}
			if (blocked) {
				continue;
			}
			net.minecraft.core.BlockPos there = net.minecraft.core.BlockPos.containing(player.position().add(dir.scale(3.0)));
			if (player.level().canSeeSky(there.above())) {
				score += 1.0;
			}
			if (score > bestScore) {
				bestScore = score;
				best = dir;
				hop = step;
			}
		}
		float yaw = (float)(Mth.atan2(best.z, best.x) * Mth.RAD_TO_DEG) - 90.0F;
		player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * FLEE_TURN);
		player.setXRot(player.getXRot() * 0.8F);
		Vec3 v = player.getDeltaMovement();
		double up = v.y;
		if (player.isInWater()) {
			up = Math.max(up, FLEE_SWIM_UP);
		} else if (player.onGround() && hop) {
			up = JUMP_SPEED;
		} else if (player.horizontalCollision) {
			// against a wall: the tendrils dig in and climb it (the server rips through if there's no way up)
			up = Math.max(up, FLEE_CLIMB);
		}
		boolean sprinting = canSprint(player) && !hop;
		sprint(player, sprinting);
		Vec3 move = best.scale((sprinting ? TAKEOVER_SPEED : WALK_SPEED) * FLEE_SPEED_SCALE);
		player.setDeltaMovement(move.x, up, move.z);
	}

	/**
	 * Wall Crawl: stuck to walls and ceilings, and dragged along them by the tendrils (see {@link #CLIMB_REACH}): forward
	 * or Jump climbs, Sneak or back goes down, strafing goes along the wall; on a ceiling (Jump or a Sneak press under it
	 * sticks you to it, another Sneak press drops you) the movement keys drag you about upside down. Over the top of a wall, a tendril bites the ledge and
	 * hauls you onto it.
	 */
	private static void tickCrawl(LocalPlayer player, WebSwing swing) {
		if (leapGrace > 0) {
			// just leapt off the tendrils: they let you fly
			leapGrace--;
			wallClinging = false;
			ceilingClinging = false;
			letGoOfWall();
			return;
		}
		if (!WebSymbiote.isToggled(player, WebSymbioteState.CRAWL) || player.getAbilities().flying || player.isPassenger() || player.isInWater()
			|| player.isInLava() || swing.attached() || swing.pull != null || swing.wingsTicks > 0 || player.isFallFlying()) {
			wallClinging = false;
			ceilingClinging = false;
			letGoOfWall();
			return;
		}
		AABB box = player.getBoundingBox();
		boolean wall = !player.level().noCollision(player, box.inflate(0.12, -0.1, 0.12));
		boolean ceiling = !player.onGround() && !player.level().noCollision(player, box.move(0.0, 0.15, 0.0).deflate(0.05, 0.0, 0.05));
		Input input = player.input.keyPresses;
		Vec3 move = inputDirection(player, input);
		boolean sneakPressed = input.shift() && !sneakWas;
		sneakWas = input.shift();
		if (ceilingClinging && sneakPressed && latchHold <= 0) {
			// Sneak again on the ceiling: they let go and you drop
			ceilingClinging = false;
			letGoOfWall();
			return;
		}
		if (sneakPressed && !ceilingClinging && !wallClinging) {
			// Sneak by a ceiling (looking up, or with no wall to hand): slammed up into it, just as into a wall
			double reach = (player.onGround() ? CEILING_LATCH_GROUND : CEILING_LATCH_REACH) * player.getScale();
			Vec3 overhead = ceilingOver(player, reach);
			if (overhead != null && (!wall || player.getXRot() < -CEILING_LATCH_LOOK)) {
				slamCeiling(player, overhead);
				return;
			}
		}
		if (latchHold > 0) {
			latchHold--;
		}
		if (ceilingClinging && !ceiling && latchHold > 0) {
			// slammed in, still flying up to it
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x * 0.5, Math.max(v.y, LATCH_SLAM), v.z * 0.5);
			player.resetFallDistance();
			return;
		}
		if (ceiling && (input.jump() && !input.shift() || ceilingClinging)) {
			// on the ceiling: dragged about upside down, the same as along a wall (pinned still just after a slam)
			ceilingClinging = true;
			wallClinging = false;
			drag(player, latchHold > 0 ? Vec3.ZERO : move, new Vec3(0.0, -1.0, 0.0));
			player.resetFallDistance();
			return;
		}
		ceilingClinging = false;
		if (wall && sneakPressed && !wallClinging) {
			slamLatch(player);
			return;
		}
		if (wall && (!player.onGround() || input.forward() && player.horizontalCollision || latchHold > 0)) {
			Vec3 out = wallNormal(player);
			// the way you want to go along the wall: up or down, and sideways (pinned still just after a slam)
			double vertical = latchHold > 0 ? 0.0 : (input.jump() || input.forward() ? 1.0 : 0.0) - (input.shift() || input.backward() ? 1.0 : 0.0);
			Vec3 along = out.cross(new Vec3(0.0, 1.0, 0.0));
			along = along.lengthSqr() < 1.0E-4 ? Vec3.ZERO : along.normalize();
			Vec3 want = new Vec3(0.0, vertical, 0.0).add(along.scale(move.dot(along)));
			wallClinging = true;
			drag(player, want.lengthSqr() < 1.0E-4 ? Vec3.ZERO : want.normalize(), out);
			player.resetFallDistance();
			return;
		}
		if (wallClinging && climbLedge && climbAt != null && !player.onGround()) {
			// hauled up over the edge: the tendril drags you onto the ledge
			drag(player, climbWant, Vec3.ZERO);
			if (player.getBoundingBox().minY < climbAt.y + 0.05) {
				return;
			}
		}
		boolean wasClinging = wallClinging;
		if (wallClinging && !player.onGround() && player.getDeltaMovement().y > 0.0) {
			// over the top: a hop onto the ledge
			Vec3 forward = player.getLookAngle().horizontal();
			forward = forward.lengthSqr() < 1.0E-4 ? Vec3.ZERO : forward.normalize();
			player.setDeltaMovement(forward.x * LEDGE_FORWARD, LEDGE_HOP, forward.z * LEDGE_FORWARD);
		}
		wallClinging = false;
		letGoOfWall();
		if (!wasClinging) {
			stride(player, input, move);
		}
	}

	/** Carried on four tendrils, off the wall (see {@link #STRIDE_HEIGHT}). */
	private static void stride(LocalPlayer player, Input input, Vec3 move) {
		if (input.shift() && player.onGround() || player.isSpectator()) {
			// on your own feet
			return;
		}
		int anchors = WebTendrilWalk.anchors(player, WebTendrilWalk.STRIDE_REACH * player.getScale());
		if (anchors < 2) {
			// nothing to grab: you fall
			return;
		}
		double scale = player.getScale();
		double ground = WebTendrilWalk.groundBelow(player, WebTendrilWalk.STRIDE_GROUND * scale);
		double vy;
		if (WebSwing.holdingCharge(player)) {
			// coiling a charged leap: they sink you low on bent legs, ready to fling you (let go to leap)
			vy = ground >= 0.0 ? Mth.clamp((STRIDE_HEIGHT * STRIDE_COIL * scale - ground) * STRIDE_LIFT, -STRIDE_CLIMB, STRIDE_CLIMB) : 0.0;
		} else if (input.jump()) {
			vy = anchors >= STRIDE_RISE_ANCHORS ? STRIDE_CLIMB : 0.0;
		} else if (input.shift()) {
			vy = -STRIDE_CLIMB;
		} else if (ground >= 0.0) {
			// held up on them, rising and dipping a touch with each stride
			double bob = move.lengthSqr() > 0.0 ? Mth.sin(player.tickCount * 0.45F) * 0.06 : 0.0;
			vy = Mth.clamp((STRIDE_HEIGHT * scale + bob - ground) * STRIDE_LIFT, -STRIDE_CLIMB, STRIDE_CLIMB);
		} else {
			// hanging between whatever they hold
			vy = 0.0;
		}
		Vec3 want = move.scale(player.isSprinting() ? STRIDE_SPRINT : STRIDE_SPEED);
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(Mth.lerp(STRIDE_GRIP, v.x, want.x), vy, Mth.lerp(STRIDE_GRIP, v.z, want.z));
		player.resetFallDistance();
	}

	/** Sneak at a wall: both tendrils slam into it at once, throwing you against it, and you hang there. */
	private static void slamLatch(LocalPlayer player) {
		Vec3 out = wallNormal(player);
		Vec3 center = player.getBoundingBox().getCenter();
		net.minecraft.world.phys.BlockHitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(center,
			center.subtract(out.scale(1.5)), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		Vec3 at = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit.getLocation() : center.subtract(out.scale(0.4));
		wallClinging = true;
		climbAt = at;
		climbWant = Vec3.ZERO;
		climbLedge = false;
		// already bitten in and hauled: no reach to wait out
		climbPhase = CLIMB_REACH_TICKS + CLIMB_PULL_TICKS;
		latchHold = LATCH_HOLD_TICKS;
		if (ClientPlayNetworking.canSend(WebNet.ClimbLatch.TYPE)) {
			// both of them, a little apart (the server cracks the wall where each bites)
			Vec3 side = out.cross(new Vec3(0.0, 1.0, 0.0));
			side = side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize().scale(0.45);
			ClientPlayNetworking.send(new WebNet.ClimbLatch(WebNet.MAIN, at.add(side).add(0.0, 0.3, 0.0), true));
			ClientPlayNetworking.send(new WebNet.ClimbLatch(WebNet.OFF, at.subtract(side).add(0.0, 0.3, 0.0), true));
			climbSent = true;
		}
		player.setDeltaMovement(out.scale(-LATCH_SLAM).add(0.0, player.onGround() ? LATCH_LIFT : 0.0, 0.0));
		player.resetFallDistance();
		WebSwing.play(player, net.minecraft.sounds.SoundEvents.SLIME_ATTACK, 1.0F, 0.5F);
		WebSwing.play(player, player.level().getBlockState(net.minecraft.core.BlockPos.containing(at.subtract(out.scale(0.05)))).getSoundType().getHitSound(),
			1.0F, 0.6F);
	}

	/** The ceiling point straight over your head within {@code reach} (blocks above the top of your head), or null. */
	private static @org.jspecify.annotations.Nullable Vec3 ceilingOver(LocalPlayer player, double reach) {
		AABB box = player.getBoundingBox();
		Vec3 top = new Vec3(box.getCenter().x, box.maxY - 0.05, box.getCenter().z);
		net.minecraft.world.phys.BlockHitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(top, top.add(0.0, reach + 0.05, 0.0),
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getDirection() == net.minecraft.core.Direction.DOWN
			? hit.getLocation() : null;
	}

	/** Sneak under a ceiling: both tendrils slam up into it at once, yanking you up against it, and you hang there. */
	private static void slamCeiling(LocalPlayer player, Vec3 at) {
		ceilingClinging = true;
		wallClinging = false;
		climbAt = at;
		climbWant = Vec3.ZERO;
		climbLedge = false;
		climbPhase = CLIMB_REACH_TICKS + CLIMB_PULL_TICKS;
		latchHold = LATCH_HOLD_TICKS;
		if (ClientPlayNetworking.canSend(WebNet.ClimbLatch.TYPE)) {
			// both of them, either side of your head (the server cracks the ceiling where each bites)
			Vec3 side = player.getLookAngle().horizontal().cross(new Vec3(0.0, 1.0, 0.0));
			side = side.lengthSqr() < 1.0E-4 ? new Vec3(0.45, 0.0, 0.0) : side.normalize().scale(0.45);
			ClientPlayNetworking.send(new WebNet.ClimbLatch(WebNet.MAIN, at.add(side), true));
			ClientPlayNetworking.send(new WebNet.ClimbLatch(WebNet.OFF, at.subtract(side), true));
			climbSent = true;
		}
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x * 0.3, LATCH_SLAM + CEILING_SLAM_EXTRA, v.z * 0.3);
		player.resetFallDistance();
		WebSwing.play(player, net.minecraft.sounds.SoundEvents.SLIME_ATTACK, 1.0F, 0.5F);
		WebSwing.play(player, player.level().getBlockState(net.minecraft.core.BlockPos.containing(at.add(0.0, 0.05, 0.0))).getSoundType().getHitSound(),
			1.0F, 0.6F);
	}

	/** Which way the wall you're on faces (out of it, toward you): the side your box is pressed against. */
	private static Vec3 wallNormal(LocalPlayer player) {
		AABB box = player.getBoundingBox().deflate(0.0, 0.1, 0.0);
		Vec3 look = player.getLookAngle().horizontal();
		Vec3 best = Vec3.ZERO;
		double bestDot = -2.0;
		for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			Vec3 d = dir.getUnitVec3();
			if (!player.level().noCollision(player, box.move(d.scale(0.15)))) {
				// the one you're facing wins
				double dot = d.dot(look);
				if (dot > bestDot) {
					bestDot = dot;
					best = d.reverse();
				}
			}
		}
		if (best.lengthSqr() < 1.0E-4) {
			best = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, -1.0) : look.normalize().reverse();
		}
		return best;
	}

	/**
	 * One tick of the drag: a tendril reaches on ahead ({@code want}, along the surface facing {@code out}; zero: hang
	 * where you are), bites in, and hauls you to it; then the other side's reaches on.
	 */
	private static void drag(LocalPlayer player, Vec3 want, Vec3 out) {
		boolean ceiling = out.y < -0.5;
		Vec3 center = player.getBoundingBox().getCenter();
		if (want.lengthSqr() < 1.0E-4 && !climbLedge) {
			// still: hanging on, pressed to the surface
			player.setDeltaMovement(ceiling ? new Vec3(0.0, CEILING_PRESS, 0.0) : out.scale(-0.02));
			climbPhase = 0;
			return;
		}
		boolean turned = climbAt == null || want.lengthSqr() > 1.0E-4 && want.dot(climbWant) < 0.5;
		Vec3 goal = climbAt == null ? center : climbGoal(player, out);
		boolean arrived = climbPhase > CLIMB_REACH_TICKS && goal.distanceTo(center) < CLIMB_ARRIVE;
		if (turned || arrived || climbPhase >= CLIMB_REACH_TICKS + CLIMB_PULL_TICKS) {
			if (want.lengthSqr() < 1.0E-4) {
				// a ledge pull with nowhere further to go
				climbLedge = false;
				return;
			}
			reachOn(player, want, out);
			goal = climbGoal(player, out);
		}
		climbPhase++;
		if (climbPhase <= CLIMB_REACH_TICKS) {
			// reaching on ahead: you hang, sagging a little
			player.setDeltaMovement(ceiling ? new Vec3(0.0, CEILING_PRESS, 0.0) : out.scale(-0.02).add(0.0, CLIMB_SAG, 0.0));
			return;
		}
		// bitten in: hauled to it, hard at first, easing as it nears
		Vec3 v = goal.subtract(center).scale(CLIMB_PULL_STEER);
		if (v.length() > CLIMB_PULL_MAX) {
			v = v.normalize().scale(CLIMB_PULL_MAX);
		}
		if (ceiling) {
			v = new Vec3(v.x, Math.max(v.y, CEILING_PRESS), v.z);
		}
		player.setDeltaMovement(v);
		if (climbPhase == CLIMB_REACH_TICKS + 1) {
			WebSwing.play(player, net.minecraft.sounds.SoundEvents.SLIME_SQUISH_SMALL, 0.5F, 0.6F);
		}
	}

	/** The other side's tendril reaches {@link #CLIMB_REACH} on along the surface and bites in (the server shows the cord). */
	private static void reachOn(LocalPlayer player, Vec3 want, Vec3 out) {
		climbHand = climbHand == WebNet.MAIN ? WebNet.OFF : WebNet.MAIN;
		climbWant = want;
		climbPhase = 0;
		climbLedge = false;
		Vec3 center = player.getBoundingBox().getCenter();
		Vec3 ahead = center.add(want.scale(CLIMB_REACH));
		Vec3 into = out.lengthSqr() < 1.0E-4 ? want : out.reverse();
		net.minecraft.world.phys.BlockHitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(ahead.subtract(into.scale(0.3)),
			ahead.add(into.scale(1.8)), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		Vec3 at;
		if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			at = hit.getLocation();
		} else {
			// nothing there to bite: over the top of the wall? It bites the ledge, to haul you up onto it
			Vec3 over = ahead.add(into.scale(0.7));
			net.minecraft.world.phys.BlockHitResult top = player.level().clip(new net.minecraft.world.level.ClipContext(over.add(0.0, 1.0, 0.0),
				over.subtract(0.0, 2.0, 0.0), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
			if (top.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && top.getDirection() == net.minecraft.core.Direction.UP) {
				at = top.getLocation();
				climbLedge = true;
				climbWant = want.add(into).normalize();
			} else {
				at = ahead.add(into.scale(0.5));
			}
		}
		climbAt = at;
		if (ClientPlayNetworking.canSend(WebNet.ClimbLatch.TYPE)) {
			ClientPlayNetworking.send(new WebNet.ClimbLatch(climbHand, at, true));
			climbSent = true;
		}
		WebSwing.play(player, net.minecraft.sounds.SoundEvents.SLIME_ATTACK, 0.35F, 0.7F + player.getRandom().nextFloat() * 0.2F);
	}

	/** Where the bite hauls your middle: just short of it, off the surface (onto the ledge, over the top). */
	private static Vec3 climbGoal(LocalPlayer player, Vec3 out) {
		Vec3 at = climbAt == null ? player.getBoundingBox().getCenter() : climbAt;
		double half = player.getBbHeight() * 0.5;
		if (climbLedge) {
			return at.add(0.0, half + 0.1, 0.0);
		}
		if (out.y < -0.5) {
			// hanging under the ceiling
			return at.subtract(0.0, half - 0.1, 0.0).subtract(climbWant.scale(CLIMB_SHORT));
		}
		return at.add(out.scale(CLIMB_OFF_WALL)).subtract(climbWant.scale(CLIMB_SHORT));
	}

	/** Off the wall: the tendrils let go. */
	private static void letGoOfWall() {
		climbAt = null;
		climbLedge = false;
		climbPhase = 0;
		if (climbSent && ClientPlayNetworking.canSend(WebNet.ClimbLatch.TYPE)) {
			ClientPlayNetworking.send(new WebNet.ClimbLatch(WebNet.MAIN, Vec3.ZERO, false));
			ClientPlayNetworking.send(new WebNet.ClimbLatch(WebNet.OFF, Vec3.ZERO, false));
		}
		climbSent = false;
	}

	/** The movement keys as a world direction (unit or zero), from where the player faces. */
	private static Vec3 inputDirection(LocalPlayer player, Input input) {
		double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
		double strafe = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
		if (forward == 0 && strafe == 0) {
			return Vec3.ZERO;
		}
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double sin = Mth.sin(yaw);
		double cos = Mth.cos(yaw);
		Vec3 dir = new Vec3(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin);
		return dir.normalize();
	}

	// ---- HUD ---------------------------------------------------------------------------------------------------------

	/** How much of the takeover's dark edge gives way with the bar full, and how hard it flickers as the bar swings. */
	private static final float HUD_CLEAR = 0.6F, HUD_FLICKER = 0.45F;

	/** A takeover darkens the edges of the screen, pulsing like a heartbeat. */
	static void renderHud(GuiGraphicsExtractor graphics, LocalPlayer player) {
		if (takeoverTicks <= 0) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float pulse = 0.55F + 0.45F * Mth.sin(player.tickCount * 0.6F);
		// fighting its hold: a thin bar filling as you struggle (only once you're able to break free)
		int needed = WebSymbiote.breakFreeNeeded(player);
		float share = struggleShare(player);
		if (needed > 0 && struggleMeter > 0.0F) {
			int barWidth = 120;
			int filled = Math.round(barWidth * share);
			int bx = (width - barWidth) / 2;
			int by = height - 66;
			graphics.fill(bx - 1, by - 1, bx + barWidth + 1, by + 4, 0xA0000000);
			graphics.fill(bx, by, bx + filled, by + 3, 0xFFB050E0);
			// halfway: get past it twice and you tear yourself out of it (WebSymbiote.LOCKOUT_SURGE)
			int half = bx + Math.round(barWidth * WebSymbiote.LOCKOUT_SURGE);
			graphics.fill(half, by - 2, half + 1, by + 5, 0xFFE8D8F0);
		}
		// the further out of it you've torn, the more the dark gives way, flickering as the two of you fight for it
		float flicker = share <= 0.0F ? 0.0F : Mth.sin(player.tickCount * 1.9F) * Mth.sin(player.tickCount * 0.53F + 1.0F) * HUD_FLICKER * share;
		pulse *= Mth.clamp(1.0F - HUD_CLEAR * share + flicker, 0.15F, 1.0F);
		for (int band = 0; band < 6; band++) {
			int alpha = (int)((150 - band * 24) * pulse);
			int color = alpha << 24 | 0x0E0614;
			int inset = band * 6;
			graphics.fill(inset, inset, width - inset, inset + 6, color);
			graphics.fill(inset, height - inset - 6, width - inset, height - inset, color);
			graphics.fill(inset, inset + 6, inset + 6, height - inset - 6, color);
			graphics.fill(width - inset - 6, inset + 6, width - inset, height - inset - 6, color);
		}
	}

	// ---- spider-sense ------------------------------------------------------------------------------------------------

	/** Whether Spider-Sense is on for us this tick. */
	private static boolean sensing;

	/** The outline colour for {@code entity} on our screen, or 0: red if it's about to hurt an ascended web slinger. */
	public static int senseColor(Entity entity) {
		if (!sensing) {
			return 0;
		}
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || entity == player) {
			return 0;
		}
		if (entity.distanceToSqr(player) > com.jaymospowers.power.web.WebAscension.SENSE_RANGE * com.jaymospowers.power.web.WebAscension.SENSE_RANGE) {
			return 0;
		}
		boolean fresh = player.level().getGameTime() - threatsAt <= SENSE_STALE_TICKS;
		if (fresh && THREATS.contains(entity.getId())) {
			return SENSE_COLOR;
		}
		if (entity instanceof Creeper creeper && creeper.getSwelling(0.0F) > 0.0F) {
			return SENSE_COLOR;
		}
		if (entity instanceof AbstractSkeleton skeleton && skeleton.isUsingItem()) {
			return SENSE_COLOR;
		}
		return 0;
	}
}
