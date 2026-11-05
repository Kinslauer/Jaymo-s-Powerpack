package com.jaymospowers.client.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.LockOnClient;
import com.jaymospowers.combat.AttackTells;
import com.jaymospowers.combat.Guard;
import com.jaymospowers.combat.IncomingBlows;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The incoming-blow cue on this side (see {@link IncomingBlows}): for a player's swing, the star at their weapon hand and
 * the warning bell (only you get them); for every blow coming at you, an arc of dots round the crosshair on the side it
 * comes from (top: in front of you, bottom: behind), closing on the crosshair as it comes in. White: parry it. Red: too
 * heavy, block or dodge. Gold in its last {@link Guard#PERFECT_TICKS} (more while locked on to the one swinging): raise
 * the guard while it's gold for a perfect parry; any time before that is still a parry. The close is brought forward by
 * your own ping (up to {@link #MAX_PING_TICKS}), since your guard reaches the server that much later.
 */
public final class IncomingBlowsClient {
	/** The arc's radius as the cue starts and as the blow lands (pixels). */
	static final float START_RADIUS = 34.0F, END_RADIUS = 11.0F;
	/** Its dots: how many, how far apart (degrees), and their size (pixels). */
	static final int DOTS = 5, DOT_SIZE = 2;
	static final float DOT_STEP = 9.0F;
	/** How long the arc lingers after the blow, flashing (ticks). */
	static final int LINGER = 3;
	/** At most this many cues at once (the oldest go). */
	static final int MAX_CUES = 6;
	/** Ping compensation is capped at this (ticks). */
	static final int MAX_PING_TICKS = 4;
	static final int WHITE = 0xFFFFFF, RED = 0xE02020, GOLD = 0xFFC940, SHADOW = 0x000000;

	private record Cue(int attacker, long start, int ticks, boolean heavy) {
	}

	private static final List<Cue> CUES = new ArrayList<>();
	private static long now;

	private IncomingBlowsClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(IncomingBlows.Incoming.TYPE, (payload, context) -> received(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(IncomingBlowsClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("incoming_blows"), IncomingBlowsClient::draw);
	}

	private static void received(Minecraft minecraft, IncomingBlows.Incoming payload) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			return;
		}
		Entity attacker = level.getEntity(payload.attacker());
		if (!(attacker instanceof LivingEntity living)) {
			return;
		}
		int ticks = Math.max(1, payload.ticks() - pingTicks(minecraft, player));
		CUES.removeIf(cue -> cue.attacker() == payload.attacker());
		if (CUES.size() >= MAX_CUES) {
			CUES.remove(0);
		}
		CUES.add(new Cue(payload.attacker(), now, ticks, payload.heavy()));
		if (payload.tell()) {
			tell(level, living, payload.heavy());
		}
	}

	/** Your round trip to the server, in ticks: your guard lands that much later than you raise it. */
	private static int pingTicks(Minecraft minecraft, LocalPlayer player) {
		if (minecraft.getConnection() == null) {
			return 0;
		}
		PlayerInfo info = minecraft.getConnection().getPlayerInfo(player.getUUID());
		return info == null ? 0 : Mth.clamp(info.getLatency() / 50, 0, MAX_PING_TICKS);
	}

	/** A player's swing at you: the star at their weapon hand and the bell, as a monster's wind-up shows everyone. */
	private static void tell(ClientLevel level, LivingEntity attacker, boolean heavy) {
		Vec3 facing = Vec3.directionFromRotation(0.0F, attacker.getYHeadRot());
		Vec3 side = new Vec3(-facing.z, 0.0, facing.x).scale(attacker.getMainArm() == HumanoidArm.RIGHT ? -0.35 : 0.35);
		Vec3 at = attacker.getEyePosition().add(facing.scale(0.4)).add(side).add(0.0, -0.2, 0.0);
		int color = heavy ? AttackTells.HEAVY_TELL_COLOR : AttackTells.TELL_COLOR;
		float size = heavy ? AttackTells.HEAVY_GLINT_SIZE : AttackTells.GLINT_SIZE;
		level.addAlwaysVisibleParticle(new AttackTells.Glint(color, size, false), true, at.x, at.y, at.z, 0.0, 0.0, 0.0);
		level.addAlwaysVisibleParticle(new AttackTells.Glint(0xFFFFFF, size * AttackTells.CORE_SHARE, true), true, at.x, at.y, at.z, 0.0, 0.0, 0.0);
		level.playLocalSound(at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, AttackTells.PING_VOLUME,
			heavy ? AttackTells.HEAVY_PING_PITCH : AttackTells.PING_PITCH, false);
	}

	private static void tick(Minecraft minecraft) {
		now++;
		if (minecraft.level == null) {
			CUES.clear();
			return;
		}
		for (Iterator<Cue> it = CUES.iterator(); it.hasNext(); ) {
			Cue cue = it.next();
			Entity attacker = minecraft.level.getEntity(cue.attacker());
			if (now - cue.start() > cue.ticks() + LINGER || attacker == null || !attacker.isAlive()) {
				it.remove();
			}
		}
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (CUES.isEmpty() || player == null || minecraft.level == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float cx = graphics.guiWidth() / 2.0F, cy = graphics.guiHeight() / 2.0F;
		float yaw = player.getViewYRot(partial);
		Vec3 eye = player.getEyePosition(partial);
		LivingEntity locked = LockOnClient.locked();
		for (Cue cue : CUES) {
			Entity attacker = minecraft.level.getEntity(cue.attacker());
			if (attacker == null) {
				continue;
			}
			Vec3 to = attacker.getEyePosition(partial).subtract(eye);
			// 0: straight ahead (drawn above the crosshair), clockwise round to behind (below)
			float bearing = (float) Math.toDegrees(Mth.atan2(to.z, to.x)) - 90.0F - yaw;
			float elapsed = now - cue.start() + partial;
			float share = Mth.clamp(elapsed / cue.ticks(), 0.0F, 1.0F);
			float radius = Mth.lerp(share, START_RADIUS, END_RADIUS);
			int perfect = Guard.PERFECT_TICKS + (locked != null && locked.getId() == cue.attacker() ? Guard.LOCK_PERFECT_BONUS : 0);
			boolean landed = elapsed >= cue.ticks();
			int rgb;
			if (cue.heavy()) {
				rgb = RED;
			} else if (landed) {
				rgb = WHITE;
			} else {
				rgb = cue.ticks() - elapsed <= perfect ? GOLD : WHITE;
			}
			int alpha = landed ? (((int) elapsed & 1) == 0 ? 255 : 90) : Math.round(Mth.lerp(share, 150.0F, 255.0F));
			for (int i = 0; i < DOTS; i++) {
				float angle = (float) Math.toRadians(bearing + (i - (DOTS - 1) / 2.0F) * DOT_STEP);
				int x = Math.round(cx + Mth.sin(angle) * radius), y = Math.round(cy - Mth.cos(angle) * radius);
				int middle = i == DOTS / 2 ? 1 : 0;
				graphics.fill(x - 1 - middle, y - 1 - middle, x + DOT_SIZE + 1 + middle, y + DOT_SIZE + 1 + middle, (alpha * 3 / 5) << 24 | SHADOW);
				graphics.fill(x - middle, y - middle, x + DOT_SIZE + middle, y + DOT_SIZE + middle, alpha << 24 | rgb);
			}
		}
	}
}
