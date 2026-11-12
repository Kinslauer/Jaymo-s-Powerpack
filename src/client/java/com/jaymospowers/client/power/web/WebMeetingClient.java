package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.UltimateCinematic;
import com.jaymospowers.power.web.WebMeeting;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteState;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The first meeting, on the host's own screen ({@code WebMeeting} is the server half; the film's bars, cleared HUD and
 * camera are {@code UltimateCinematic}'s, which asks {@link #view} for the shot). The world goes dark round the two of them
 * (a vignette over the host's screen only), a second copy of the host stands where the server put it, in the big suit,
 * baring its teeth, and at the end it pours back into the host and the light comes up.
 *
 * <p>How it is framed. The camera stays on one side of the line between the host and the figure for the whole film (the 180
 * degree rule): every position has a positive lateral offset from that line, and the blends between shots are straight
 * lines between two such positions. Shot one is a two-shot, wide, that comes round from behind the host to square on and holds
 * with the host on one third line of the frame and the figure on the other (the distance is worked out from the field of
 * view and the window's shape); shot two is over the host's shoulder on the figure, which sits on the same side of the frame
 * as in the wide shot (the host's shoulder on the other), eyes on the upper third; shot three is the wide two-shot again as
 * it pours back.
 */
public final class WebMeetingClient {
	// ================================================================================================================
	// Tuning
	/** Shares of the film: the wide shot to {@code WIDE_END}, the blend to the shoulder shot, its end, the blend back, the wide hold. */
	static final float WIDE_END = 0.42F, TO_SHOULDER = 0.5F, SHOULDER_END = 0.76F, TO_WIDE = 0.86F;
	/** The wide shot: how far round it starts (degrees off square), how much of its time it spends coming round, and the push-in over it. */
	static final float WIDE_ROUND = 0.6F;
	static final double WIDE_START_ANGLE = 50.0, WIDE_PUSH = 0.06, WIDE_MIN = 3.5, WIDE_MAX = 12.0;
	/** Heights (blocks over each one's feet): the camera in each shot, the host's head, the figure's head. */
	static final double WIDE_HEIGHT = 1.3, SHOULDER_HEIGHT = 1.75, HOST_HEAD = 1.55, FOE_HEAD = 2.4;
	/** The shoulder shot: how far behind the host (from, to) and out to the side (blocks); never less than {@code MIN_LATERAL} off the line. */
	static final double SHOULDER_BACK_FROM = 0.9, SHOULDER_BACK_TO = 0.4, SHOULDER_SIDE = 1.1, MIN_LATERAL = 0.6;
	/** The teeth are bared from this share of the film to this one. */
	static final float TEETH_FROM = 0.5F, TEETH_TO = 0.88F;
	/** It pours back from this share: it slides to the host and shrinks. */
	static final float POUR_FROM = 0.8F, POUR_TO = 0.97F;
	/** The dark: how far it comes in and out (shares of the film), the base veil and the edges' strength (0-1), the edge's reach (share of the width) and strips. */
	static final float DARK_IN = 0.1F, DARK_OUT = 0.9F, VEIL = 0.55F, EDGE = 0.9F, EDGE_REACH = 0.45F;
	static final int EDGE_STRIPS = 24;
	/** Pouring particles per tick. */
	static final int POUR_PARTICLES = 6;
	/** The film must have been running this long before a stop counts as a skip (ticks). */
	static final int SKIP_AFTER = 6;

	private static boolean active;
	private static int age, length;
	private static Vec3 foe = Vec3.ZERO, host = Vec3.ZERO;
	private static @Nullable Figure figure;

	private WebMeetingClient() {
	}

	/** A copy of the host, drawn through the player renderer with the host's skin and no name. */
	private static final class Figure extends RemotePlayer {
		private final PlayerSkin skin;

		Figure(ClientLevel level, GameProfile profile, PlayerSkin skin) {
			super(level, profile);
			this.skin = skin;
		}

		@Override
		public PlayerSkin getSkin() {
			return this.skin;
		}

		@Override
		public boolean shouldShowName() {
			return false;
		}

		@Override
		public boolean isCustomNameVisible() {
			return false;
		}
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(WebMeeting.Scene.TYPE, (payload, context) -> start(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (active && !minecraft.isPaused()) {
				tick(minecraft);
			}
		});
		HudElementRegistry.addLast(JaymosPowers.id("web_meeting_dark"), (HudElement)WebMeetingClient::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop(client));
	}

	/** Whether the figure with this entity id has its teeth out just now (the big body's jaw reads it). */
	static boolean baring(int entityId) {
		Figure copy = figure;
		if (!active || copy == null || copy.getId() != entityId) {
			return false;
		}
		float t = share(0.0F);
		return t >= TEETH_FROM && t <= TEETH_TO;
	}

	private static float share(float partial) {
		return length <= 0 ? 0.0F : Mth.clamp((age + partial) / length, 0.0F, 1.0F);
	}

	private static void start(Minecraft minecraft, WebMeeting.Scene scene) {
		LocalPlayer self = minecraft.player;
		ClientLevel level = minecraft.level;
		if (self == null || level == null) {
			return;
		}
		stop(minecraft);
		active = true;
		age = 0;
		length = scene.ticks();
		foe = scene.foe();
		host = self.position();
		Figure copy = new Figure(level, new GameProfile(UUID.randomUUID(), "symbiote"), self.getSkin());
		WebSymbioteState mine = WebSymbiote.state(self);
		copy.setAttached(WebSymbiote.STATE, new WebSymbioteState(true, WebSymbiote.SATIETY_MAX, 0, 0,
			WebSymbioteState.SUIT | WebSymbioteState.BULK, mine.hints(), WebSymbiote.BOND_MAX, mine.strain()));
		float yaw = (float)Math.toDegrees(Math.atan2(-(host.x - foe.x), host.z - foe.z));
		copy.snapTo(foe.x, foe.y, foe.z, yaw, 0.0F);
		copy.setYHeadRot(yaw);
		copy.setYBodyRot(yaw);
		copy.yBodyRotO = yaw;
		copy.yHeadRotO = yaw;
		copy.setNoGravity(true);
		level.addEntity(copy);
		figure = copy;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;
		Figure copy = figure;
		if (self == null || copy == null || ++age >= length) {
			stop(minecraft);
			return;
		}
		// the film was skipped (or hit away) from the host's side: the server lets them go
		if (age > SKIP_AFTER && !UltimateCinematic.filming()) {
			ClientPlayNetworking.send(new WebMeeting.Skip());
			stop(minecraft);
			return;
		}
		float t = share(0.0F);
		if (t >= POUR_FROM) {
			// it pours back: it slides to the host, thinning, trailing goo
			float u = Mth.clamp((t - POUR_FROM) / (POUR_TO - POUR_FROM), 0.0F, 1.0F);
			float k = u * u * (3.0F - 2.0F * u);
			Vec3 at = foe.lerp(host, k);
			copy.setPos(at.x, at.y, at.z);
			var scale = copy.getAttribute(Attributes.SCALE);
			if (scale != null) {
				scale.setBaseValue(Math.max(0.05, 1.0 - 0.9 * k));
			}
			Vec3 to = host.add(0.0, 1.0, 0.0).subtract(at.add(0.0, 1.2, 0.0));
			Vec3 along = to.lengthSqr() < 1.0E-4 ? Vec3.ZERO : to.normalize().scale(0.25);
			for (int i = 0; i < POUR_PARTICLES; i++) {
				copy.level().addParticle(ParticleTypes.SQUID_INK, at.x + (copy.getRandom().nextDouble() - 0.5) * 0.8,
					at.y + 0.3 + copy.getRandom().nextDouble() * 1.8, at.z + (copy.getRandom().nextDouble() - 0.5) * 0.8, along.x, along.y, along.z);
			}
		}
	}

	private static void stop(Minecraft minecraft) {
		Figure copy = figure;
		figure = null;
		active = false;
		if (copy != null && minecraft.level != null) {
			minecraft.level.removeEntity(copy.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	/** The dark: a veil and a vignette on the host's screen, in over {@link #DARK_IN} of the film and out over the last tenth. */
	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!active) {
			return;
		}
		float t = share(delta.getGameTimeDeltaPartialTick(false));
		float in = smooth(t / DARK_IN);
		float out = 1.0F - smooth((t - DARK_OUT) / (1.0F - DARK_OUT));
		float dark = Math.min(in, out);
		if (dark <= 0.0F) {
			return;
		}
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		graphics.fill(0, 0, width, height, argb(VEIL * dark));
		int reach = Math.round(width * EDGE_REACH);
		int strip = Math.max(1, reach / EDGE_STRIPS);
		for (int i = 0; i < EDGE_STRIPS; i++) {
			float fall = 1.0F - i / (float)EDGE_STRIPS;
			int alpha = argb(EDGE * dark * fall * fall);
			graphics.fill(i * strip, 0, (i + 1) * strip, height, alpha);
			graphics.fill(width - (i + 1) * strip, 0, width - i * strip, height, alpha);
		}
		int band = Math.round(height * 0.3F);
		graphics.fillGradient(0, 0, width, band, argb(EDGE * dark), 0);
		graphics.fillGradient(0, height - band, width, height, 0, argb(EDGE * dark));
	}

	private static int argb(float alpha) {
		return Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24;
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	// ================================================================================================================
	// The shots

	/**
	 * The camera this frame ({x, y, z, yaw, pitch, roll}) for the film {@code t} of the way through, with the figure's feet
	 * at {@code foeFeet}; {@code side} (+1 or -1) is which side of the line the camera keeps. Null if the two are on top of each other.
	 */
	public static double @Nullable [] view(LocalPlayer self, Vec3 foeFeet, float t, float side) {
		Vec3 a = self.position();
		Vec3 gap = foeFeet.subtract(a).horizontal();
		double sep = gap.length();
		if (sep < 0.5) {
			return null;
		}
		Vec3 d = gap.scale(1.0 / sep);
		// the camera's side of the line
		Vec3 n = new Vec3(-d.z, 0.0, d.x).scale(side);
		Minecraft minecraft = Minecraft.getInstance();
		double tanV = Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2.0);
		double aspect = minecraft.getWindow().getWidth() / (double)Math.max(1, minecraft.getWindow().getHeight());
		double tanH = tanV * aspect;
		// each subject a third of the way from the middle to the edge: its lateral offset is sep/2, so the distance is
		double back = Mth.clamp(1.5 * sep / tanH, WIDE_MIN, WIDE_MAX);
		Vec3 mid = new Vec3((a.x + foeFeet.x) / 2.0, 0.0, (a.z + foeFeet.z) / 2.0);
		double headMid = (a.y + HOST_HEAD + foeFeet.y + FOE_HEAD) / 2.0;
		Vec3[] shot;
		if (t < WIDE_END) {
			shot = wide(mid, d, n, back, tanV, headMid, a.y, WIDE_ROUND * t / WIDE_END);
		} else if (t < TO_SHOULDER) {
			float k = smooth((t - WIDE_END) / (TO_SHOULDER - WIDE_END));
			shot = blend(wide(mid, d, n, back, tanV, headMid, a.y, WIDE_ROUND), shoulder(a, foeFeet, d, n, side, tanV, 0.0F), k);
		} else if (t < SHOULDER_END) {
			shot = shoulder(a, foeFeet, d, n, side, tanV, (t - TO_SHOULDER) / (SHOULDER_END - TO_SHOULDER));
		} else if (t < TO_WIDE) {
			float k = smooth((t - SHOULDER_END) / (TO_WIDE - SHOULDER_END));
			shot = blend(shoulder(a, foeFeet, d, n, side, tanV, 1.0F), wide(mid, d, n, back, tanV, headMid, a.y, WIDE_ROUND), k);
		} else {
			float u = (t - TO_WIDE) / (1.0F - TO_WIDE);
			shot = wide(mid, d, n, back, tanV, headMid, a.y, WIDE_ROUND + (1.0F - WIDE_ROUND) * u);
		}
		Vec3 pos = keepSide(a, d, n, shot[0]);
		pos = clipped(self, shot[1], pos);
		return aimed(pos, shot[1]);
	}

	/**
	 * The two-shot: from the side of the line, {@code back} off, coming round from behind the host to square on over the
	 * first {@code WIDE_ROUND} of the way, then pushing in a touch. {@code u} runs 0 to 1 over the wide shot's whole time.
	 * Looking at the middle, with the heads on the upper third.
	 */
	private static Vec3[] wide(Vec3 mid, Vec3 d, Vec3 n, double back, double tanV, double headMid, double feetY, float u) {
		double round = Math.toRadians(WIDE_START_ANGLE) * (1.0 - smooth(u / WIDE_ROUND));
		double dist = back * (1.0 - WIDE_PUSH * Mth.clamp((u - WIDE_ROUND) / (1.0F - WIDE_ROUND), 0.0F, 1.0F));
		// round: off square toward the host's back (behind the host is the -d end)
		Vec3 pos = mid.add(n.scale(dist * Math.cos(round))).subtract(d.scale(dist * Math.sin(round))).add(0.0, feetY + WIDE_HEIGHT, 0.0);
		Vec3 look = new Vec3(mid.x, headMid - dist * tanV / 3.0, mid.z);
		return new Vec3[] {pos, look};
	}

	/**
	 * Over the host's shoulder on the figure: behind the host and out to the camera's side, looking at the figure's head,
	 * which sits on the third line on the side it stood in the wide shot (that is {@code side}'s side of the frame) with its
	 * eyes on the upper third.
	 */
	private static Vec3[] shoulder(Vec3 a, Vec3 foeFeet, Vec3 d, Vec3 n, float side, double tanV, float u) {
		double behind = Mth.lerp(smooth(u), SHOULDER_BACK_FROM, SHOULDER_BACK_TO);
		Vec3 pos = a.subtract(d.scale(behind)).add(n.scale(SHOULDER_SIDE)).add(0.0, SHOULDER_HEIGHT, 0.0);
		Vec3 head = foeFeet.add(0.0, FOE_HEAD, 0.0);
		Vec3 forward = head.subtract(pos);
		double depth = forward.length();
		forward = forward.scale(1.0 / Math.max(1.0E-4, depth));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		right = right.lengthSqr() < 1.0E-6 ? n : right.normalize();
		double tanH = tanV * Minecraft.getInstance().getWindow().getWidth() / (double)Math.max(1, Minecraft.getInstance().getWindow().getHeight());
		// it is on the +side third: aim to its other side, and a touch above it
		Vec3 look = head.subtract(right.scale(side * depth * tanH / 3.0)).subtract(0.0, depth * tanV / 3.0, 0.0);
		return new Vec3[] {pos, look};
	}

	private static Vec3[] blend(Vec3[] from, Vec3[] to, float k) {
		return new Vec3[] {from[0].lerp(to[0], k), from[1].lerp(to[1], k)};
	}

	/** {@code pos}, pushed out to at least {@link #MIN_LATERAL} on the camera's side of the line (it never crosses it). */
	private static Vec3 keepSide(Vec3 a, Vec3 d, Vec3 n, Vec3 pos) {
		double lateral = pos.subtract(a).dot(n);
		return lateral >= MIN_LATERAL ? pos : pos.add(n.scale(MIN_LATERAL - lateral));
	}

	/** {@code want}, or short of it where a wall stands between it and what it is looking at. */
	private static Vec3 clipped(LocalPlayer self, Vec3 from, Vec3 want) {
		HitResult hit = self.level().clip(new ClipContext(from, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, self));
		if (hit.getType() == HitResult.Type.MISS) {
			return want;
		}
		Vec3 back = hit.getLocation().subtract(from);
		double length = back.length();
		return length > 0.3 ? from.add(back.scale((length - 0.3) / length)) : from;
	}

	private static double[] aimed(Vec3 pos, Vec3 look) {
		Vec3 dir = look.subtract(pos);
		dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
		double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
		double pitch = Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0, 1.0)));
		return new double[] {pos.x, pos.y, pos.z, yaw, pitch, 0.0};
	}
}
