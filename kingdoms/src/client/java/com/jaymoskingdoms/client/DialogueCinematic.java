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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;
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
 *
 * <p>Andy (2026-11-21): "a lot of text is over the villager's head and the framing of the convo window occludes it ...
 * keep the framing, just put the villager's text in a box on the left side of the screen, and then a list of responses
 * on the right that pops up after the villager finishes their sentence." While the cutscene runs, what the villager
 * says goes in a box low on the left, under their name, a few letters a tick ({@link #CHARS_PER_TICK}), not over their
 * head. MCA's answers and its button column on the right ({@code McaDialogueBoxMixin}, {@code McaButtonColumnMixin})
 * show only once the line is all out ({@link #answersUp}).
 *
 * <p>Andy (2026-11-21): "the camera should pan from the over user shoulder pov, maybe fading the player's character model
 * out while their line's play so you can see the person you interact with. it should also pan and swap to outside on the
 * left or right of both characters." While the villager speaks you aren't drawn ({@link #hidesPlayer}), so the shot over
 * your shoulder sees them clearly. Your own lines are shot side-on, from outside the two of you, the other side each time.
 * The camera glides from shot to shot ({@link #PAN_PER_SECOND}) instead of cutting.
 *
 * <p>Andy (2026-11-21): the answers as a wheel worded by your origin ({@link TalkWheel}), and "the chat should also be
 * hidden for the duration of the interaction". The line you pick is what you say: it goes in the box under your own name
 * ({@link #mine}) until the villager answers. Chat isn't drawn while the talk runs; what was said there is still in it after.
 */
public final class DialogueCinematic {
	/** Each bar's height, as a share of the screen; how much of the way they slide a tick. */
	static final float BAR_SHARE = 0.11F, BAR_EASE = 0.25F;
	/** The villager's shot, over the player's shoulder: how far behind the player's eyes, to the side, and up (blocks). */
	static final double CLOSE_BACK = 0.9, CLOSE_SIDE = 0.55, CLOSE_UP = 0.15;
	/** The player's shot, wider and behind them: back, side, up (blocks). */
	static final double WIDE_BACK = 2.0, WIDE_SIDE = 0.9, WIDE_UP = 0.5;
	/** The side-on view of the two of them (the player's lines, or no room behind the player): how far out from the middle between them (blocks). */
	static final double PROFILE_OUT = 2.4;
	/** How quickly the camera glides to a new shot: the share of the way left it closes in a second (as 1 - e^-k). */
	static final double PAN_PER_SECOND = 4.0;
	/** How far the camera stays off a wall; the share of a shot's distance that must be free for it to be used. */
	static final double WALL_MARGIN = 0.3, ROOM_NEEDED = 0.8;
	/** How long the camera stays on the player after they pick an answer before it swaps to the villager's reply (ticks). */
	static final int PLAYER_HOLD = 40;
	/** The menu shutting this soon after a pick or a line (ticks) means a reply is coming: the cutscene plays on for it. */
	static final int REPLY_WINDOW = 10;
	/** The villager's reply shot after the menu has shut: at least this long, at most this long (ticks). */
	static final int REPLY_MIN = 40, REPLY_MAX = 20 * 12;
	/** The villager's box: how fast their words come out (letters a tick), and how long a reply stays up once it's all out (ticks). */
	static final float CHARS_PER_TICK = 1.5F;
	static final int READ_TICKS = 50;
	/** With nothing said yet, the answers come up this long after the talk opens (ticks), in case a greeting is on its way. */
	static final int OPEN_WAIT = 10;
	/** The box: its left margin, its width at most and its text's wrap width, its padding, and its gap above the bottom bar (GUI pixels). */
	public static final int BOX_MARGIN = 10, BOX_WIDTH = 220, BOX_PAD = 6, BOX_GAP = 8;
	/** Box fill and border, the dark purple of MCA's info tags and our answer column; the name's colour. */
	public static final int BOX_FILL = 0xC0100010, BORDER_TOP = 0x905000FF, BORDER_BOTTOM = 0x9028007F, NAME_COLOR = 0xFFFFD27A;

	private static final Identifier[] HIDDEN = { VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR,
		VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
		VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR,
		VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS,
		VanillaHudElements.CHAT };

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
	/** What the villager is saying in the box (null: nothing yet, or the player's turn), its rows, its letters, and the tick it began. */
	private static @Nullable Component line;
	private static List<FormattedCharSequence> rows = List.of();
	private static int letters, lineFrom, startedAt;
	/** The box's line is the player's own (picked on the wheel), not the villager's. */
	private static boolean lineMine;
	/** Which side the player's side-on shot is from (flips each line); where the camera is and looks, eased (null: cut to the shot). */
	private static boolean leftSide;
	private static @Nullable Vec3 camAt, camLook;
	private static long camMillis;

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
		line = null;
		startedAt = ticks;
		camAt = null;
		camLook = null;
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
			if (!lineMine) {
				line = null; // their last line is answered: the box waits for the next (or shows yours)
			}
			leftSide = !leftSide;
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
		line = null;
		lineMine = false;
		rows = List.of();
		camAt = null;
		camLook = null;
	}

	/** Whether {@code entity} isn't drawn now: the player, while the villager is speaking to them. */
	public static boolean hidesPlayer(Entity entity) {
		return running && speaker != Speaker.PLAYER && entity == Minecraft.getInstance().player;
	}

	/**
	 * {@code entityId} says {@code text}: if it's the villager being talked to, it goes in the box (true) instead of over
	 * their head. The same words again leave the box as it is.
	 */
	public static boolean said(int entityId, Component text) {
		if (!running || entityId != villager) {
			return false;
		}
		if (line != null && !lineMine && line.getString().equals(text.getString())) {
			return true;
		}
		show(text, false);
		return true;
	}

	/** The player picked a line on the wheel ({@link TalkWheel}): it's what they say, in the box under their name. */
	public static void mine(Component text) {
		if (running) {
			show(text, true);
		}
	}

	private static void show(Component text, boolean own) {
		line = text;
		lineMine = own;
		rows = Minecraft.getInstance().font.split(text, BOX_WIDTH - BOX_PAD * 2);
		int n = 0;
		for (FormattedCharSequence row : rows) {
			int[] count = { 0 };
			row.accept((index, style, codePoint) -> {
				count[0]++;
				return true;
			});
			n += count[0];
		}
		letters = n;
		lineFrom = ticks;
	}

	/** The tick the villager's line is all out. */
	private static int lineDoneAt() {
		return lineFrom + Mth.ceil(letters / CHARS_PER_TICK);
	}

	/** Whether the answers may show: no talk on, or the villager's line is all out (or nothing said a moment after opening). */
	public static boolean answersUp() {
		if (!running) {
			return true;
		}
		if (lineMine) {
			// your own line: the villager's answer replaces it; should none come, the wheel comes back after a while
			return ticks >= lineDoneAt() + PLAYER_HOLD;
		}
		return line == null ? ticks - startedAt >= OPEN_WAIT && speaker != Speaker.PLAYER : ticks >= lineDoneAt();
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
		// (an illager's talk screen too: it is laid out as MCA's and gets the same camera, IllagerTalkScreen)
		boolean talking = self != null && minecraft.level != null && (screen instanceof InteractScreen || screen instanceof IllagerTalkScreen);
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
		return ticks >= shot + REPLY_MAX || line != null && !lineMine && ticks >= lineDoneAt() + READ_TICKS;
	}

	@Nullable
	private static Entity villagerOf(Screen screen) {
		if (screen instanceof McaInteractScreenAccessor accessor) {
			return accessor.jaymoskingdoms$villager().asEntity();
		}
		if (screen instanceof IllagerTalkScreen talk && Minecraft.getInstance().level != null) {
			return Minecraft.getInstance().level.getEntity(talk.speaker());
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
		double flip = leftSide ? -1.0 : 1.0;
		Vec3[] shoulder = { me, me.add(axis.scale(-back)).add(side.scale(out)).add(0.0, up, 0.0) };
		Vec3[] otherShoulder = { me, me.add(axis.scale(-back)).add(side.scale(-out)).add(0.0, up, 0.0) };
		Vec3[] profile = { mid, mid.add(side.scale(PROFILE_OUT * flip)).add(0.0, WIDE_UP, 0.0) };
		Vec3[] otherProfile = { mid, mid.add(side.scale(-PROFILE_OUT * flip)).add(0.0, WIDE_UP, 0.0) };
		// the villager's lines over one shoulder, else the other, else side-on; the player's side-on (this line's side
		// first), else from behind; whichever has room first
		Vec3[][] shots = wide ? new Vec3[][] { profile, otherProfile, shoulder, otherShoulder }
			: new Vec3[][] { shoulder, otherShoulder, profile, otherProfile };
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
		// glide there from where the camera was, not a cut
		long millis = net.minecraft.util.Util.getMillis();
		if (camAt == null || camLook == null || camAt.distanceToSqr(pos) > 64.0) {
			camAt = pos;
			camLook = look;
		} else {
			double share = 1.0 - Math.exp(-PAN_PER_SECOND * Math.min(0.25, (millis - camMillis) / 1000.0));
			camAt = camAt.lerp(pos, share);
			camLook = camLook.lerp(look, share);
		}
		camMillis = millis;
		pos = camAt;
		look = camLook;
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
		if (running && line != null && !rows.isEmpty()) {
			box(graphics, delta.getGameTimeDeltaPartialTick(false), height - bar);
		}
	}

	/** The villager's name and words, typed out, in a box low on the left with its foot {@link #BOX_GAP} over {@code floor}. */
	private static void box(GuiGraphicsExtractor graphics, float partial, int floor) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		Entity them = minecraft.level == null ? null : minecraft.level.getEntity(villager);
		Entity speaking = lineMine ? minecraft.player : them;
		Component name = speaking == null ? null : speaking.getDisplayName();
		int rowHeight = font.lineHeight + 1;
		int textWidth = 0;
		for (FormattedCharSequence row : rows) {
			textWidth = Math.max(textWidth, font.width(row));
		}
		if (name != null) {
			textWidth = Math.max(textWidth, font.width(name));
		}
		int x0 = BOX_MARGIN, x1 = x0 + textWidth + BOX_PAD * 2;
		int high = BOX_PAD * 2 + rows.size() * rowHeight + (name != null ? rowHeight + 2 : 0);
		int y1 = floor - BOX_GAP, y0 = y1 - high;
		graphics.fill(x0, y0, x1, y1, BOX_FILL);
		graphics.fillGradient(x0, y0, x0 + 1, y1, BORDER_TOP, BORDER_BOTTOM);
		graphics.fillGradient(x1 - 1, y0, x1, y1, BORDER_TOP, BORDER_BOTTOM);
		graphics.fill(x0, y0, x1, y0 + 1, BORDER_TOP);
		graphics.fill(x0, y1 - 1, x1, y1, BORDER_BOTTOM);
		int y = y0 + BOX_PAD;
		if (name != null) {
			graphics.text(font, name.getVisualOrderText(), x0 + BOX_PAD, y, NAME_COLOR);
			y += rowHeight + 2;
		}
		// the words come out a few letters a tick
		int left = (int) ((ticks - lineFrom + partial) * CHARS_PER_TICK);
		for (FormattedCharSequence row : rows) {
			if (left <= 0) {
				break;
			}
			int[] room = { left };
			FormattedCharSequence shown = sink -> row.accept((index, style, codePoint) -> room[0]-- > 0 && sink.accept(index, style, codePoint));
			graphics.text(font, shown, x0 + BOX_PAD, y, 0xFFFFFFFF);
			int[] count = { 0 };
			row.accept((index, style, codePoint) -> {
				count[0]++;
				return true;
			});
			left -= count[0];
			y += rowHeight;
		}
	}
}
