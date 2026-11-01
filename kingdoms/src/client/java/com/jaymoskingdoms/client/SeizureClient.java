package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Seizures;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.185) The client half of {@link Seizures}: while the blood or the wolf has you, it walks you at its prey (no key
 * does what it would: each new press is a heave against it, filling the bar), your view is dragged round and jolted,
 * and the screen's edges close in (blood red for the fever, dark amber for the wolf). (0.7.332) A werewolf's edges as themselves
 * ({@code WolfScreen}) darken with how far the curse has them, day or night.
 */
public final class SeizureClient {
	/** How fast it turns your view toward its prey (share a tick), how hard it walks you (blocks a tick: the blood, the beast). */
	static final float TURN = 0.3F;
	static final double FEVER_SPEED = 0.26, BEAST_SPEED = 0.32, JUMP = 0.42, JUMP_PER_LEVEL = 0.1, SWIM_UP = 0.08;
	/** It stops this close (blocks), and looks this far ahead for a way through. */
	static final double STOP = 1.4, LOOK = 0.8;
	static final float[] DETOURS = {0.0F, 35.0F, -35.0F, 70.0F, -70.0F, 110.0F, -110.0F};
	/** Its grip on your view: a jitter every tick (degrees), a jolt each press. */
	static final float JITTER = 1.2F, JOLT = 2.5F;
	/** The edges while it has you (0-1), and their beat (ticks). */
	static final float EDGE = 0.55F, EDGE_BEAT = 0.3F, BEAT = 16.0F;
	/** The edges ease in and out this much a tick. */
	static final float FADE = 0.05F;
	/** The struggle bar: width, height, how far above the bottom (pixels). */
	static final int BAR_W = 120, BAR_H = 5, BAR_UP = 62;

	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");

	private static Seizures.Kind kind = Seizures.Kind.FEVER;
	private static int target = -1, ticks, needed;
	private static float fade, meter;
	private static boolean canGiveIn;
	private static Input heldKeys = Input.EMPTY;
	private static int pressTick = -1;
	/** The edges now (eased) and their colour (fever red or wolf amber). */
	private static float edge, lastEdge;
	private static boolean red;

	private SeizureClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Seizures.Seize.TYPE, (payload, context) -> {
			boolean fresh = target < 0;
			kind = Seizures.Kind.of(payload.kind());
			target = payload.target();
			ticks = target < 0 ? 0 : payload.ticks();
			needed = payload.needed();
			fade = payload.fade();
			canGiveIn = payload.giveIn();
			if (fresh || target < 0) {
				meter = 0.0F;
			}
			if (target >= 0 && context.client().gui.screen() != null) {
				context.client().gui.setScreen(null);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(SeizureClient::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("seizure"), SeizureClient::hud);
	}

	private static void clear() {
		target = -1;
		ticks = needed = 0;
		meter = 0.0F;
		canGiveIn = false;
		heldKeys = Input.EMPTY;
	}

	/** Something has you: no key does what it would. */
	public static boolean locked() {
		return target >= 0 && ticks > 0;
	}

	/** You may give in now ({@code U}). */
	static boolean canGiveIn() {
		return locked() && canGiveIn;
	}

	static void giveIn() {
		if (canGiveIn() && ClientPlayNetworking.canSend(Seizures.GiveIn.TYPE)) {
			ClientPlayNetworking.send(new Seizures.GiveIn());
			canGiveIn = false;
		}
	}

	/** The movement keys while it has you: each new press is a heave against it. */
	public static void struggleKeys(Input keys) {
		boolean pressed = keys.forward() && !heldKeys.forward() || keys.backward() && !heldKeys.backward() || keys.left() && !heldKeys.left()
			|| keys.right() && !heldKeys.right() || keys.jump() && !heldKeys.jump() || keys.shift() && !heldKeys.shift()
			|| keys.sprint() && !heldKeys.sprint();
		heldKeys = keys;
		if (pressed) {
			struggle();
		}
	}

	private static void struggle() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !locked() || needed <= 0 || player.tickCount == pressTick) {
			return;
		}
		pressTick = player.tickCount;
		meter += 1.0F;
		// every heave against it jolts your view
		jolt(player, JOLT * (0.5F + share()));
		if (ClientPlayNetworking.canSend(Seizures.Struggle.TYPE)) {
			ClientPlayNetworking.send(new Seizures.Struggle());
		}
	}

	private static float share() {
		return needed > 0 ? Mth.clamp(meter / needed, 0.0F, 1.0F) : 0.0F;
	}

	private static void jolt(LocalPlayer player, float degrees) {
		player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5F) * 2.0F * degrees);
		player.setXRot(Mth.clamp(player.getXRot() + (player.getRandom().nextFloat() - 0.5F) * 2.0F * degrees, -90.0F, 90.0F));
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		lastEdge = edge;
		if (player == null || minecraft.level == null) {
			clear();
			edge = lastEdge = 0.0F;
			return;
		}
		float want = 0.0F;
		if (locked()) {
			red = kind == Seizures.Kind.FEVER;
			want = EDGE;
		}
		// (0.7.332) a werewolf's mood on the screen, at night or not, is WolfScreen's
		edge = want > edge ? Math.min(want, edge + FADE) : Math.max(want, edge - FADE);
		if (!locked() || minecraft.isPaused()) {
			return;
		}
		ticks--;
		meter = Math.max(0.0F, meter - fade);
		jolt(player, JITTER * (kind == Seizures.Kind.CHANGE ? 2.0F : 1.0F));
		Entity prey = minecraft.level.getEntity(target);
		if (kind == Seizures.Kind.CHANGE || prey == null || prey == player) {
			// doubled over where you stand (or its prey's out of sight a moment)
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x * 0.5, v.y, v.z * 0.5);
			return;
		}
		steer(player, prey, kind == Seizures.Kind.FERAL ? BEAST_SPEED : FEVER_SPEED);
	}

	/** Straight at it if the way's open (or a step it can hop), else the nearest way round; up out of water. */
	private static void steer(LocalPlayer player, Entity prey, double speed) {
		steerAt(player, prey.getBoundingBox().getCenter(), speed);
	}

	/** (0.7.465) {@link #steer} at a point rather than a being (the Grue walking its host's routine, a spirit pulling you out of flux). */
	public static void steerAt(LocalPlayer player, Vec3 point, double speed) {
		Vec3 to = point.subtract(player.getEyePosition());
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float)(-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
		player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * TURN);
		player.setXRot(Mth.clamp(player.getXRot() + (pitch - player.getXRot()) * TURN, -90.0F, 90.0F));
		Vec3 flat = new Vec3(to.x, 0.0, to.z);
		Vec3 v = player.getDeltaMovement();
		if (flat.lengthSqr() <= STOP * STOP) {
			player.setDeltaMovement(v.x * 0.5, v.y, v.z * 0.5);
			return;
		}
		Vec3 dir = flat.normalize();
		Vec3 way = null;
		boolean hop = false;
		for (int i = 0; i < DETOURS.length && way == null; i++) {
			Vec3 tried = dir.yRot(DETOURS[i] * Mth.DEG_TO_RAD);
			AABB ahead = player.getBoundingBox().move(tried.scale(LOOK));
			if (player.level().noCollision(player, ahead)) {
				way = tried;
			} else if (player.level().noCollision(player, ahead.move(0.0, 1.1, 0.0))) {
				way = tried;
				hop = true;
			}
		}
		if (way == null) {
			way = dir;
			hop = true;
		}
		double up = v.y;
		if (player.isInWater()) {
			up = Math.max(up, SWIM_UP);
		} else if (player.onGround() && (hop || player.horizontalCollision)) {
			MobEffectInstance jump = player.getEffect(MobEffects.JUMP_BOOST);
			up = JUMP + (jump == null ? 0.0 : (jump.getAmplifier() + 1) * JUMP_PER_LEVEL);
		}
		Vec3 move = way.scale(speed);
		player.setDeltaMovement(move.x, up, move.z);
	}

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		float partialTicks = delta.getGameTimeDeltaPartialTick(false);
		float e = Mth.lerp(partialTicks, lastEdge, edge);
		if (e > 0.0F) {
			float t = ((minecraft.level.getGameTime() % 1000L) + partialTicks) % BEAT / BEAT;
			float k = Math.min(1.0F, e * (1.0F + EDGE_BEAT * Mth.sin(t * Mth.TWO_PI)));
			// (the vignette pipeline takes away as much of each colour as given: leave red alone for blood, some red and green for amber)
			int color = red ? ARGB.colorFromFloat(1.0F, 0.15F * k, k, k) : ARGB.colorFromFloat(1.0F, 0.45F * k, 0.7F * k, k);
			graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
				graphics.guiHeight(), color);
		}
		if (!locked() || needed <= 0) {
			return;
		}
		int x = (graphics.guiWidth() - BAR_W) / 2;
		int y = graphics.guiHeight() - BAR_UP;
		int fill = Math.round(BAR_W * share());
		int colour = kind == Seizures.Kind.FEVER ? 0xFFB01010 : 0xFFC08020;
		graphics.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, 0xA0000000);
		graphics.fill(x, y, x + fill, y + BAR_H, colour);
		Component line = canGiveIn
			? Component.translatable("hud.jaymoskingdoms.seize.fight_or_give_in", WerewolfKey.name())
			: Component.translatable(kind == Seizures.Kind.CHANGE ? "hud.jaymoskingdoms.seize.hold_back" : "hud.jaymoskingdoms.seize.fight");
		graphics.centeredText(minecraft.font, line, graphics.guiWidth() / 2, y - 11, 0xFFE0D0D0);
	}
}
