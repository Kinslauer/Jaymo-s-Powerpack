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
	 * @param strike   how far through a strike (0-1), or -1
	 * @param aimYaw   where the strike goes, relative to the body (radians, + to the wearer's right, as Minecraft's yaw turns)
	 * @param aimPitch and how high (radians, + up)
	 * @param side     which side's tendrils strike (+1 left, -1 right)
	 */
	/** {@code held}: bit 1, the right shoulder's tendril is holding something (drawn reaching out instead); bit 2, the left's. */
	/** {@code armor}: Living Armor's (it hunches, swells); otherwise only the tendrils, out on their own for a moment. */
	/** {@code heavy}: the strike swings held debris (slower, further); {@code variant}: a slam or a swipe (see WebSymbiote.STRIKE_SLAM). */
	/** {@code firstPerson}: your own, drawn in first person (by First-person Model, or next to the hands): they reach further forward. */
	/** {@code combo}: a tendril combo (WebTendrilCombos.CLAP, CRUSH, SHATTER; -1 none): {@code strike} is then how far through it. */
	public record Tendrils(float growth, float time, float strike, float aimYaw, float aimPitch, float side, float run, int held, boolean armor,
		int wall, float wallYaw, float wallPitch, boolean heavy, int variant, boolean firstPerson, int combo) {
		/** {@code wall}: 0 none, 1 reaching for a wall (aimed by wallYaw/wallPitch), 2 fanned back into it. */

		/** The same, drawn from your own eyes. */
		public Tendrils inFirstPerson() {
			return new Tendrils(growth, time, strike, aimYaw, aimPitch, side, run, held, armor, wall, wallYaw, wallPitch, heavy, variant, true, combo);
		}
	}
	/** Ticks into a meal (partial ticks included), for the maw. */
	public static final RenderStateDataKey<Float> EATING = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_eating");
	/** Which meal this is (when it started), so each bite's size is random but the same for everyone. */
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

	/** Ticks into a curious stare (the head swollen, staring), for the maw. */
	public static final RenderStateDataKey<Float> STARE = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_stare");
	/** How long the meal's carry in takes (ticks), for the maw. */
	public static final RenderStateDataKey<Integer> MEAL_REACH = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_meal_reach");
	/** A two-bite meal (cow-sized), for the maw. */
	public static final RenderStateDataKey<Boolean> MEAL_BIG = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_meal_big");

	/** Player entity id → game time their Living Armor came up (for the tendrils' growth). */
	private static final java.util.Map<Integer, Long> ARMOR_SINCE = new java.util.HashMap<>();

	// ===== wall crawl ===============================================================================================
	/** Climbing speed up and down walls, and crawling speed along walls and ceilings (blocks/tick). */
	static final double CRAWL_CLIMB = 0.2;
	static final double CRAWL_SPEED = 0.17;
	/** Pressed up into a ceiling this hard each tick to stay stuck. */
	static final double CEILING_PRESS = 0.1;
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
				if (player != null && minecraft.gui.screen() == null && !player.isSpectator() && WebSymbiote.isBonded(player) && takeoverTicks <= 0) {
					minecraft.gui.setScreen(new WebSymbioteScreen());
				} else if (player != null && minecraft.gui.screen() == null && !player.isSpectator()
					&& com.jaymospowers.client.power.wind.WindPowerClient.isActive(player)) {
					// the same key is Wind's Tempest Kick
					ClientPlayNetworking.send(new com.jaymospowers.power.wind.WindKick.Kick());
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(WebNet.Takeover.TYPE, (payload, context) -> {
			takeoverTarget = payload.entityId();
			takeoverTicks = payload.entityId() < 0 ? 0 : payload.ticks();
			takeoverFlee = payload.flee();
			if (takeoverTicks <= 0) {
				clearPath();
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
		ClientPlayNetworking.registerGlobalReceiver(WebNet.Shake.TYPE, (payload, context) -> WebShake.start(payload.strength()));
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
			}
		});
	}

	static void reset() {
		wallClinging = false;
		ceilingClinging = false;
		takeoverTicks = 0;
		takeoverTarget = -1;
		clearPath();
		THREATS.clear();
	}

	/** From the avatar renderer's extractRenderState: does this player wear the suit. */
	public static void extract(Entity entity, AvatarRenderState state) {
		state.setData(SUIT, entity instanceof Player player && WebSymbiote.suitShown(player) ? Boolean.TRUE : null);
		if (entity instanceof Player player) {
			WebSuitGrowth.extract(player, state, net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
		}
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
			// no cape poking out through the suit
			state.showCape = false;
		}
		// Living Armor grows tendrils (whether or not the suit is showing); the big body swells under them
		boolean armored = entity instanceof Player p && WebSymbiote.armored(p);
		boolean bulk = entity instanceof Player p && WebSymbiote.bulked(p);
		Long staring = entity.getAttached(WebSymbiote.STARING);
		state.setData(STARE, staring == null ? null : Math.max(0.0F, entity.level().getGameTime() - staring
			+ net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
		long out = entity instanceof Player p && WebSymbiote.isBonded(p) ? WebSymbiote.tendrilsOut(p) : -1L;
		if (entity instanceof Player player && (armored || out >= 0)) {
			long now = player.level().getGameTime();
			long since = ARMOR_SINCE.computeIfAbsent(player.getId(), id -> now);
			float growth = Math.min(1.0F, (now - since) / WebTendrilLayer.GROWN_TICKS);
			if (!armored) {
				// out on their own: they lash out, then draw back in
				growth = Math.min(growth * 1.6F, Math.max(0.0F, (out - now) / 8.0F));
			}
			// a strike of their own (the server picks the target)
			float strike = -1.0F, aimYaw = 0.0F, aimPitch = 0.0F, side = 1.0F;
			boolean heavy = false;
			int variant = WebSymbiote.STRIKE_SLAM;
			WebSymbiote.Strike s = player.getAttached(WebSymbiote.STRIKE);
			Entity target = s == null ? null : player.level().getEntity(s.target());
			if (s != null && target != null) {
				float partial = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
				strike = net.minecraft.util.Mth.clamp((now - s.start() + partial) / (float)s.ticks(), 0.0F, 1.0F);
				heavy = s.heavy();
				variant = s.variant();
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
				strike = net.minecraft.util.Mth.clamp((now - c.start() + partial) / (float)com.jaymospowers.power.web.WebTendrilCombos.TICKS, 0.0F, 1.0F);
				heavy = true;
				variant = WebSymbiote.STRIKE_SLAM;
				Entity victim = combo == com.jaymospowers.power.web.WebTendrilCombos.CRUSH ? player.level().getEntity(c.second()) : null;
				net.minecraft.world.phys.Vec3 at = victim != null ? victim.getBoundingBox().getCenter()
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
			state.setData(TENDRILS, new Tendrils(growth, player.tickCount, strike, aimYaw, aimPitch, side, smoothRun(player), held, bulk,
				wallMode, wallYaw, wallPitch, heavy, variant, firstPerson, combo));
		} else {
			ARMOR_SINCE.remove(entity.getId());
			state.setData(TENDRILS, null);
		}
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
		// tethered to a wall by the tendrils: held right where you are
		WebSymbiote.Wall wall = player.getAttached(WebSymbiote.WALL);
		if (wall != null && wall.landed() >= 0L && !player.input.keyPresses.jump()) {
			player.setDeltaMovement(Vec3.ZERO);
			player.resetFallDistance();
		}
		if (takeoverTicks > 0) {
			tickTakeover(player);
		}
		tickCrawl(player, swing);
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
		return takeoverTicks > 0;
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
		if (target == null || !target.isAlive() || --takeoverTicks <= 0) {
			takeoverTicks = 0;
			clearPath();
			return;
		}
		leapTouched = false;
		if (pathMode == WebNet.TakeoverPath.RIP) {
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
		} else if (player.onGround() && (hop || player.horizontalCollision)) {
			up = JUMP_SPEED;
		}
		boolean sprinting = canSprint(player) && !hop;
		sprint(player, sprinting);
		Vec3 move = best.scale((sprinting ? TAKEOVER_SPEED : WALK_SPEED) * FLEE_SPEED_SCALE);
		player.setDeltaMovement(move.x, up, move.z);
	}

	/** Wall Crawl: stick to walls and ceilings and climb them. */
	private static void tickCrawl(LocalPlayer player, WebSwing swing) {
		if (!WebSymbiote.isToggled(player, WebSymbioteState.CRAWL) || player.getAbilities().flying || player.isPassenger() || player.isInWater()
			|| player.isInLava() || swing.attached() || swing.pull != null || swing.wingsTicks > 0 || player.isFallFlying()) {
			wallClinging = false;
			ceilingClinging = false;
			return;
		}
		AABB box = player.getBoundingBox();
		boolean wall = !player.level().noCollision(player, box.inflate(0.12, -0.1, 0.12));
		boolean ceiling = !player.onGround() && !player.level().noCollision(player, box.move(0.0, 0.15, 0.0).deflate(0.05, 0.0, 0.05));
		Input input = player.input.keyPresses;
		Vec3 move = inputDirection(player, input);
		if (ceiling && !input.shift() && (input.jump() || ceilingClinging)) {
			// on the ceiling: pressed up into it, crawling about upside down
			ceilingClinging = true;
			wallClinging = false;
			player.setDeltaMovement(move.x * CRAWL_SPEED, CEILING_PRESS, move.z * CRAWL_SPEED);
			player.resetFallDistance();
			return;
		}
		ceilingClinging = false;
		if (wall && (!player.onGround() || input.forward() && player.horizontalCollision)) {
			double vy = input.jump() || input.forward() ? CRAWL_CLIMB : input.shift() ? -CRAWL_CLIMB : 0.0;
			player.setDeltaMovement(move.x * CRAWL_SPEED, vy, move.z * CRAWL_SPEED);
			player.resetFallDistance();
			wallClinging = true;
			return;
		}
		if (wallClinging && !player.onGround() && player.getDeltaMovement().y > 0.0) {
			// over the top: hop onto the ledge
			Vec3 forward = player.getLookAngle().horizontal();
			forward = forward.lengthSqr() < 1.0E-4 ? Vec3.ZERO : forward.normalize();
			player.setDeltaMovement(forward.x * LEDGE_FORWARD, LEDGE_HOP, forward.z * LEDGE_FORWARD);
		}
		wallClinging = false;
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

	/** A takeover darkens the edges of the screen, pulsing like a heartbeat. */
	static void renderHud(GuiGraphicsExtractor graphics, LocalPlayer player) {
		if (takeoverTicks <= 0) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float pulse = 0.55F + 0.45F * Mth.sin(player.tickCount * 0.6F);
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
