package com.jaymoskingdoms.client;

import com.jaymoskingdoms.DialogueCue;
import com.jaymoskingdoms.DialogueCue.Speaker;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.hud.HudZones;
import com.jaymoskingdoms.client.mixin.mca.McaInteractScreenAccessor;
import net.conczin.mca.client.gui.InteractScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A letterbox cutscene for talking to an NPC: black bars, the HUD cleared, and the camera always facing the villager -
 * close over the player's shoulder on the villager's face while the villager speaks, a wider shot from behind the
 * player taking in both of them while the player speaks. Walls pull the camera in, and a shot with no room behind
 * the player swaps to the other shoulder or to a side-on view, so the camera never ends up inside anyone. The server tells the client whose turn it is ({@link DialogueCue}); the cutscene
 * starts when MCA's talk screen opens and ends when it closes.
 *
 * <p>(Andy 2026-11-19: "the npcs should stop moving and return their message from the sepearate cam. the convo ends when
 * it ends") MCA shuts its screen the moment a chat, joke or story is picked. When it shuts just after a pick, the
 * cutscene plays on: the player's line on camera, then the villager's reply from their own shot for as long as their
 * words are up over their head, and only then is it over. The server keeps the villager standing still till the client
 * says so ({@link DialogueCue.Done}, {@link com.jaymoskingdoms.Conversations}).
 */
public final class DialogueCinematic {
	/** Each bar's height, as a share of the screen; how much of the way they slide a tick. */
	static final float BAR_SHARE = 0.11F, BAR_EASE = 0.25F;
	/** The villager's shot, over the player's shoulder: how far behind the player's eyes, to the side, and up (blocks). */
	static final double CLOSE_BACK = 0.9, CLOSE_SIDE = 0.55, CLOSE_UP = 0.15;
	/** The player's shot, wider and behind them: back, side, up (blocks). */
	static final double WIDE_BACK = 2.0, WIDE_SIDE = 0.9, WIDE_UP = 0.5;
	/** The side-on view when there's no room behind the player: how far out from the middle between them (blocks). */
	static final double PROFILE_OUT = 1.8;
	/** How far the camera stays off a wall; the share of a shot's distance that must be free for it to be used. */
	static final double WALL_MARGIN = 0.3, ROOM_NEEDED = 0.8;
	/** How long the camera stays on the player after they pick an answer before it swaps to the villager's reply (ticks). */
	static final int PLAYER_HOLD = 40;
	/** The menu shutting this soon after a pick or a line (ticks) means a reply is coming: the cutscene plays on for it. */
	static final int REPLY_WINDOW = 10;
	/** The villager's reply shot after the menu has shut: at least this long, at most this long (ticks). */
	static final int REPLY_MIN = 40, REPLY_MAX = 20 * 12;

	private static final Identifier[] HIDDEN = { VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR,
		VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
		VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR,
		VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS };

	private static boolean running;
	private static @Nullable Speaker speaker;
	private static int villager = -1;
	private static float bars, barsO;
	private static @Nullable CameraType viewBefore;
	/** The villager's reply, waiting for the player's turn on camera to end; the client tick it may show. */
	private static @Nullable Speaker pending;
	private static int ticks, holdUntil;
	/** The menu has shut after a pick: the reply plays out (from {@code replyFrom}) with no screen up. */
	private static boolean replying;
	private static int replyFrom, lastCue = Integer.MIN_VALUE / 2;

	private DialogueCinematic() {
	}

	static void register() {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mca")) {
			return; // MCA's talk screen is what starts it; without MCA nothing here may touch MCA's classes
		}
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick(minecraft));
		for (Identifier id : HIDDEN) {
			HudElementRegistry.replaceElement(id, old -> (graphics, delta) -> {
				if (!running) {
					old.extractRenderState(graphics, delta);
				}
			});
		}
		HudElementRegistry.addLast(JaymosKingdoms.id("dialogue_cinematic"), (HudElement) DialogueCinematic::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop(client, false));
	}

	/** MCA's talk screen opened on {@code villager}: the villager speaks first. */
	static void start(Minecraft minecraft, int villager) {
		if (running && DialogueCinematic.villager == villager) {
			return;
		}
		stop(minecraft);
		running = true;
		replying = false;
		speaker = Speaker.VILLAGER;
		DialogueCinematic.villager = villager;
		viewBefore = minecraft.options.getCameraType();
	}

	/** The speaker changed to {@code speaker} on {@code villager}'s talk screen. */
	static void cue(Minecraft minecraft, int villager, Speaker speaker) {
		if (!running) {
			start(minecraft, villager);
		}
		DialogueCinematic.villager = villager;
		lastCue = ticks;
		if (speaker == Speaker.PLAYER) {
			// the player's line gets its moment on camera; a reply sent the same tick waits for it
			DialogueCinematic.speaker = speaker;
			pending = null;
			holdUntil = ticks + PLAYER_HOLD;
		} else if (ticks < holdUntil) {
			pending = speaker;
		} else {
			DialogueCinematic.speaker = speaker;
		}
	}

	/** The talk is over (or the player left): give the camera back, and the villager their feet. */
	static void stop(Minecraft minecraft) {
		stop(minecraft, true);
	}

	private static void stop(Minecraft minecraft, boolean tell) {
		if (running && viewBefore != null && minecraft.player != null) {
			minecraft.options.setCameraType(viewBefore);
		}
		if (running && tell && villager >= 0 && minecraft.getConnection() != null && ClientPlayNetworking.canSend(DialogueCue.Done.TYPE)) {
			ClientPlayNetworking.send(new DialogueCue.Done(villager));
		}
		running = false;
		replying = false;
		speaker = null;
		pending = null;
		holdUntil = 0;
		villager = -1;
		viewBefore = null;
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		barsO = bars;
		if (pending != null && ticks >= holdUntil) {
			speaker = pending;
			pending = null;
		}
		LocalPlayer self = minecraft.player;
		Screen screen = minecraft.gui.screen();
		boolean talking = self != null && minecraft.level != null && screen instanceof InteractScreen;
		if (talking && !running) {
			Entity v = villagerOf(screen);
			if (v != null) {
				start(minecraft, v.getId());
			}
		}
		if (talking) {
			replying = false;
		} else if (running && !replying && screen == null && self != null && minecraft.level != null && ticks - lastCue <= REPLY_WINDOW) {
			// the menu shut just after a pick: their reply is still to come
			replying = true;
			replyFrom = ticks;
		}
		if (replying && pending == null && speaker == Speaker.PLAYER && ticks >= holdUntil) {
			// (nothing more came from the server: it's their turn all the same)
			speaker = Speaker.VILLAGER;
		}
		if (running && (!(talking || replying && screen == null) || self == null || minecraft.level == null || replying && replied(minecraft))) {
			stop(minecraft);
		}
		if (running) {
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		bars += ((running ? 1.0F : 0.0F) - bars) * BAR_EASE;
		if (!running && bars < 0.01F) {
			bars = 0.0F;
		}
	}

	/** The reply has been said: shown for its time, and their words gone from over their head (or the longest wait). */
	private static boolean replied(Minecraft minecraft) {
		Entity them = minecraft.level == null ? null : minecraft.level.getEntity(villager);
		if (them == null || !them.isAlive()) {
			return true;
		}
		int shot = Math.max(replyFrom, holdUntil);
		if (ticks < shot + REPLY_MIN) {
			return false;
		}
		return ticks >= shot + REPLY_MAX || SpeechBubbles.own(villager).isEmpty();
	}

	@Nullable
	private static Entity villagerOf(Screen screen) {
		if (screen instanceof McaInteractScreenAccessor accessor) {
			return accessor.jaymoskingdoms$villager().asEntity();
		}
		return null;
	}

	/** Where the camera sits and which way it looks this frame, or null when the cutscene isn't running. */
	public static double @Nullable [] view(Minecraft minecraft, float partialTicks) {
		if (!running || minecraft.player == null || minecraft.level == null) {
			return null;
		}
		LocalPlayer self = minecraft.player;
		Entity them = minecraft.level.getEntity(villager);
		if (them == null) {
			return null;
		}
		Vec3 me = self.getEyePosition(partialTicks);
		Vec3 you = them.getEyePosition(partialTicks);
		// the line from the player to the villager, flat, and the player's right hand side of it
		Vec3 axis = new Vec3(you.x - me.x, 0.0, you.z - me.z);
		axis = axis.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, self.getYRot()) : axis.normalize();
		Vec3 side = new Vec3(-axis.z, 0.0, axis.x);
		boolean wide = speaker == Speaker.PLAYER;
		double back = wide ? WIDE_BACK : CLOSE_BACK, out = wide ? WIDE_SIDE : CLOSE_SIDE, up = wide ? WIDE_UP : CLOSE_UP;
		// the villager's face while they speak, both of them while the player does
		Vec3 look = wide ? me.add(you).scale(0.5) : you;
		Vec3 mid = me.add(you).scale(0.5);
		// over one shoulder, else the other, else side-on to them both; whichever has room first
		Vec3[][] shots = {
			{ me, me.add(axis.scale(-back)).add(side.scale(out)).add(0.0, up, 0.0) },
			{ me, me.add(axis.scale(-back)).add(side.scale(-out)).add(0.0, up, 0.0) },
			{ mid, mid.add(side.scale(PROFILE_OUT)).add(0.0, up, 0.0) },
			{ mid, mid.add(side.scale(-PROFILE_OUT)).add(0.0, up, 0.0) } };
		Vec3 pos = null;
		double best = -1.0;
		for (Vec3[] shot : shots) {
			Vec3 at = clipped(self, shot[0], shot[1]);
			double room = at.distanceTo(shot[0]) / shot[1].distanceTo(shot[0]);
			if (room >= ROOM_NEEDED) {
				pos = at;
				break;
			}
			if (room > best) {
				best = room;
				pos = at;
			}
		}
		Vec3 dir = look.subtract(pos);
		if (dir.lengthSqr() < 1.0E-6) {
			dir = Vec3.directionFromRotation(0.0F, self.getYRot());
		} else {
			dir = dir.normalize();
		}
		double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
		double pitch = Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0, 1.0)));
		return new double[] { pos.x, pos.y, pos.z, yaw, pitch };
	}

	/** {@code want}, or short of it where a wall stands between it and {@code from}. */
	private static Vec3 clipped(LocalPlayer self, Vec3 from, Vec3 want) {
		HitResult hit = self.level().clip(new ClipContext(from, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, self));
		if (hit.getType() == HitResult.Type.MISS) {
			return want;
		}
		Vec3 back = hit.getLocation().subtract(from);
		double far = back.length();
		return far > WALL_MARGIN ? from.add(back.scale((far - WALL_MARGIN) / far)) : from;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float shown = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), barsO, bars);
		if (shown <= 0.0F) {
			return;
		}
		if (running) {
			HudZones.hideFor(100);
		}
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		int bar = Math.round(height * BAR_SHARE * shown);
		graphics.fill(0, 0, width, bar, 0xFF000000);
		graphics.fill(0, height - bar, width, height, 0xFF000000);
	}
}
