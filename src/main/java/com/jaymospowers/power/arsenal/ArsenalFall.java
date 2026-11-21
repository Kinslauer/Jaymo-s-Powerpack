package com.jaymospowers.power.arsenal;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Endless Fall (Andy's Awakening pick: "Pocket Arsenal's void gates"). The Void Arsenal's sprint + V: a gate opens in the floor
 * under the foe you look at (within {@link #REACH}) and another over its head, and it falls through the one and out of the other,
 * {@link #PASSES} times, each pass through the floor a blow. Something no portal will take (the dragon, the wither) is not
 * taken. Its fall is reset each pass: the gates' blows are the harm, not the drop.
 */
final class ArsenalFall {
	// ---- tuning ----
	private static final String TABLE = "arsenal_awakening";
	private static final int SLOT = 0;
	/** How far you aim it, how high the upper gate opens over the floor (at most; lower under a roof, never under {@link #LEAST}). */
	static final double REACH = 24.0, HEIGHT = 7.0, LEAST = 2.5;
	/** Passes, harm each pass, how fast it drops (blocks a tick), the longest a pass may take (ticks), the wait after. */
	static final int PASSES = 8, PASS_MOST = 16, COOLDOWN = 20 * 25;
	static final float HARM = 2.5F;
	static final double DROP = 1.1;
	static final int VOID = 0x5A1E8C, RIM = 0xC07CFF;

	private static final class Loop {
		final ServerPlayer owner;
		final LivingEntity foe;
		final Vec3 floor, top;
		int pass;
		long passStart;

		Loop(ServerPlayer owner, LivingEntity foe, Vec3 floor, Vec3 top, long now) {
			this.owner = owner;
			this.foe = foe;
			this.floor = floor;
			this.top = top;
			this.passStart = now;
		}
	}

	private static final Map<UUID, Loop> LOOPS = new HashMap<>();

	private ArsenalFall() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ArsenalFall::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LOOPS.clear());
	}

	static void forget(ServerPlayer player) {
		LOOPS.remove(player.getUUID());
	}

	/** Sprint + V (the Void Arsenal): opens the gates on what you look at. */
	static void cast(ServerPlayer player) {
		if (!PickKit.ready(TABLE, player, SLOT) && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.fall_wait", (PickKit.left(TABLE, player, SLOT) + 19) / 20)
				.withStyle(ChatFormatting.DARK_PURPLE));
			return;
		}
		LivingEntity foe = PickKit.aimed(player, REACH);
		if (foe == null || !foe.canUsePortal(false)) {
			player.sendOverlayMessage(Component.translatable(foe == null ? "message.jaymospowers.arsenal.fall_none" : "message.jaymospowers.arsenal.fall_refused")
				.withStyle(ChatFormatting.DARK_PURPLE));
			return;
		}
		ServerLevel level = player.level();
		Vec3 floor = foe.position();
		Vec3 top = null;
		for (double dy = HEIGHT; dy >= LEAST; dy -= 0.5) {
			Vec3 at = floor.add(0.0, dy, 0.0);
			AABB box = foe.getDimensions(foe.getPose()).makeBoundingBox(at);
			if (level.noCollision(foe, box)) {
				top = at;
				break;
			}
		}
		if (top == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.fall_room").withStyle(ChatFormatting.DARK_PURPLE));
			return;
		}
		PickKit.spend(TABLE, player, SLOT, COOLDOWN);
		Loop loop = new Loop(player, foe, floor, top, level.getGameTime());
		LOOPS.put(player.getUUID(), loop);
		level.playSound(null, floor.x, floor.y, floor.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.5F, 1.6F);
		drop(level, loop);
	}

	/** The foe comes out of the upper gate, falling. */
	private static void drop(ServerLevel level, Loop loop) {
		LivingEntity foe = loop.foe;
		if (foe.isPassenger()) {
			foe.stopRiding();
		}
		foe.teleportTo(loop.top.x, loop.top.y, loop.top.z);
		foe.setDeltaMovement(0.0, -DROP, 0.0);
		foe.syncVelocity = true;
		foe.resetFallDistance();
		loop.passStart = level.getGameTime();
		MagicFx.ring(level, loop.top.add(0.0, foe.getBbHeight() + 0.1, 0.0), Math.max(0.8F, foe.getBbWidth()), RIM);
		level.sendParticles(MagicFx.VOID, loop.top.x, loop.top.y + foe.getBbHeight(), loop.top.z, 12, 0.3, 0.1, 0.3, 0.05);
	}

	private static void tick(MinecraftServer server) {
		if (LOOPS.isEmpty()) {
			return;
		}
		for (Iterator<Loop> it = LOOPS.values().iterator(); it.hasNext();) {
			Loop loop = it.next();
			LivingEntity foe = loop.foe;
			ServerPlayer owner = loop.owner;
			if (!foe.isAlive() || foe.isRemoved() || owner.isRemoved() || !owner.isAlive() || foe.level() != owner.level()) {
				it.remove();
				continue;
			}
			ServerLevel level = owner.level();
			long now = level.getGameTime();
			foe.resetFallDistance();
			if (foe instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			if (now % 3 == 0) {
				// the floor gate stays open under it
				MagicFx.ring(level, loop.floor.add(0.0, 0.05, 0.0), Math.max(0.8F, foe.getBbWidth()), VOID);
			}
			boolean through = foe.getY() <= loop.floor.y + 0.4 || foe.onGround() || now - loop.passStart >= PASS_MOST;
			if (!through || now == loop.passStart) {
				continue;
			}
			// through the floor gate: the blow, then out of the upper one again
			loop.pass++;
			foe.setInvulnerableTime(0);
			foe.hurtServer(level, owner.damageSources().playerAttack(owner), HARM);
			level.sendParticles(MagicFx.VOID, loop.floor.x, loop.floor.y + 0.2, loop.floor.z, 16, 0.3, 0.1, 0.3, 0.15);
			level.playSound(null, loop.floor.x, loop.floor.y, loop.floor.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 0.6F + 0.1F * loop.pass);
			if (loop.pass >= PASSES || !foe.isAlive()) {
				// the gates close: it's left where it stood, its fall forgiven
				foe.teleportTo(loop.floor.x, loop.floor.y, loop.floor.z);
				foe.setDeltaMovement(Vec3.ZERO);
				foe.syncVelocity = true;
				foe.resetFallDistance();
				MagicFx.blast(level, loop.floor.add(0.0, 0.5, 0.0), 1.2F, RIM);
				it.remove();
				continue;
			}
			drop(level, loop);
		}
	}
}
