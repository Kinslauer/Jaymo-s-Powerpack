package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterScouter;
import com.jaymospowers.power.zfighter.ZFighterSensesNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.45, Andy's NOEA picks) The Scouter's readout: while a scouter is on your head, the living thing you look at (up
 * to {@link #RANGE} blocks, nothing solid between) is reported to the server, which works out its power level and sends
 * it back. A small panel by the crosshair shows the name and the number. A number past the scouter's cap flickers as
 * {@code 9000+}; one the server says overloaded it makes the panel glitch (scrambled digits, jitter, red) for
 * {@link #GLITCH_TICKS} as the lens cracks. Ki it can't read shows {@code ???}.
 */
public final class ZFighterScouterClient {
	static final double RANGE = 48.0;
	/** Ticks between asks about the same target, and how long a reading stays up without a fresh one. */
	static final int ASK_EVERY = 8;
	static final int STALE_TICKS = 24;
	/** The glitch after an overload, and how often its digits are scrambled (ticks). */
	static final int GLITCH_TICKS = 40;
	static final int SCRAMBLE_EVERY = 2;
	private static final String GLITCH_CHARS = "0123456789#%&?!@$";

	private static int lookId = -1;
	private static long askedAt = Long.MIN_VALUE / 2;
	private static int readId = -1;
	private static int readLevel;
	private static int readFlags;
	private static long readAt = Long.MIN_VALUE / 2;
	private static String readName = "";
	private static long glitchUntil = Long.MIN_VALUE / 2;

	private ZFighterScouterClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterSensesNet.Reading.TYPE, (payload, ctx) -> {
			Minecraft minecraft = ctx.client();
			if (minecraft.level == null) {
				return;
			}
			long now = minecraft.level.getGameTime();
			readId = payload.id();
			readLevel = payload.level();
			readFlags = payload.flags();
			readAt = now;
			Entity entity = minecraft.level.getEntity(payload.id());
			readName = entity == null ? "" : entity.getDisplayName().getString();
			if ((readFlags & ZFighterSensesNet.OVERLOADED) != 0) {
				glitchUntil = now + GLITCH_TICKS;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterScouterClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("zfighter_scouter"), ZFighterScouterClient::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			lookId = -1;
			readId = -1;
			glitchUntil = Long.MIN_VALUE / 2;
		});
	}

	private static boolean wearing(LocalPlayer player) {
		return ZFighterScouter.wearing(player);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			return;
		}
		if (!wearing(player) || player.isSpectator() || minecraft.gui.screen() != null) {
			lookId = -1;
			return;
		}
		Entity target = lookedAt(minecraft, player);
		int id = target == null ? -1 : target.getId();
		long now = level.getGameTime();
		if (id >= 0 && (id != lookId || now - askedAt >= ASK_EVERY)) {
			askedAt = now;
			ClientPlayNetworking.send(new ZFighterSensesNet.ScouterRead(id));
		}
		lookId = id;
	}

	/** The living thing under the crosshair out to {@link #RANGE} blocks, if nothing solid is in the way. */
	private static LivingEntity lookedAt(Minecraft minecraft, LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(RANGE));
		AABB box = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
			e -> e instanceof LivingEntity living && living.isAlive() && !living.isSpectator() && e != player, RANGE * RANGE);
		if (hit == null || !(hit.getEntity() instanceof LivingEntity living)) {
			return null;
		}
		HitResult wall = minecraft.level.clip(new ClipContext(eye, hit.getLocation(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return wall.getType() == HitResult.Type.MISS || wall.getLocation().distanceToSqr(eye) >= hit.getLocation().distanceToSqr(eye) - 0.5 ? living : null;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		long now = minecraft.level.getGameTime();
		boolean glitching = now < glitchUntil;
		boolean live = wearing(player) && lookId >= 0 && lookId == readId && now - readAt <= STALE_TICKS;
		if (!glitching && !live) {
			return;
		}
		Font font = minecraft.font;
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		String name = readName.isEmpty() ? "?" : com.jaymospowers.client.UiStyle.cut(font, readName, 90);
		String number;
		int color = 0xFF7CFFB0;
		if (glitching) {
			number = scramble(now, 5);
			color = 0xFFFF4A3A;
		} else if ((readFlags & ZFighterSensesNet.HIDDEN) != 0 || readLevel < 0) {
			number = "???";
			color = 0xFFA8A8B8;
		} else if ((readFlags & ZFighterSensesNet.PAST_CAP) != 0) {
			// past its cap: it can only say "more", and the digits shiver
			number = (now / 3 % 2 == 0 ? ZFighterScouter.CAP : ZFighterScouter.CAP - 1) + "+";
			color = 0xFFFFD54A;
		} else {
			number = Integer.toString(readLevel);
		}
		String line = Component.translatable("hud.jaymospowers.zfighter_scouter.level", number).getString();
		int w = Math.max(font.width(name), font.width(line)) + 8;
		int x = cx + 14;
		int y = cy - 12;
		if (glitching) {
			// the lens is failing: the panel jumps about and tears
			x += (int)(Mth.sin(now * 2.7F) * 3.0F);
			y += (int)(Mth.cos(now * 3.9F) * 2.0F);
			if (now / 3 % 3 == 0) {
				return;
			}
		}
		graphics.fill(x, y, x + w, y + 22, 0xA0001810);
		graphics.outline(x, y, w, 22, glitching ? 0xC0FF4A3A : 0xC07CFFB0);
		graphics.text(font, glitching ? scramble(now + 7, name.length()) : name, x + 4, y + 3, 0xFFE8FFF0);
		graphics.text(font, line, x + 4, y + 12, color);
	}

	/** {@code length} characters of digits and noise that change every {@link #SCRAMBLE_EVERY} ticks. */
	private static String scramble(long now, int length) {
		StringBuilder out = new StringBuilder();
		long seed = now / SCRAMBLE_EVERY * 7919L;
		for (int i = 0; i < Math.max(1, length); i++) {
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			out.append(GLITCH_CHARS.charAt((int)((seed >>> 33) % GLITCH_CHARS.length())));
		}
		return out.toString();
	}
}
