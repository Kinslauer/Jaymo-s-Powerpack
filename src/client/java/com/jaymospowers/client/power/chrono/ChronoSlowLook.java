package com.jaymospowers.client.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.chrono.ChronoSlowTime;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How a Chrono's slowed or stopped world looks and sounds to everyone in it (Andy, 2026-11-25: Hand of God's "time
 * manipulation and speed has very good looking fx for chrono"; ideas only, it is All Rights Reserved): the colour drains
 * to an old gold with the edges sinking darker (the {@code chrono_slow} post effect), a slow heartbeat beats under it,
 * and whatever moves leaves a trail of gold motes behind it (they hang, held by the frozen world, and thin out as time
 * steps on). Within {@link #RANGE} of a runner slowing time ({@link ChronoSlowTime#SLOWING}), or anywhere while time
 * stands still.
 */
public final class ChronoSlowLook {
	/** How near a slowing runner must be (blocks); the heartbeat's beat (ticks); how far trails are drawn (blocks); the least move that leaves one. */
	static final double RANGE = 48.0, TRAIL_RANGE = 32.0, TRAIL_MOVE = 0.03;
	static final int BEAT = 24;
	static final int TRAIL = 0xFFD27A;
	static final Identifier EFFECT = JaymosPowers.id("chrono_slow");

	private static final Map<Integer, Vec3> LAST = new HashMap<>();
	private static boolean on;
	private static int beat;

	private ChronoSlowLook() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ChronoSlowLook::tick);
	}

	/** The post effect the world wears this frame (ChronoSlowGameRendererMixin), or null. */
	public static @Nullable Identifier effect() {
		return on ? EFFECT : null;
	}

	private static boolean slowed(ClientLevel level, LocalPlayer me) {
		if (ChronoTimeStopClient.active()) {
			return true;
		}
		for (Player player : level.players()) {
			if (player.hasAttached(ChronoSlowTime.SLOWING) && player.distanceToSqr(me) <= RANGE * RANGE) {
				return true;
			}
		}
		return false;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer me = minecraft.player;
		if (level == null || me == null || minecraft.isPaused()) {
			if (level == null) {
				on = false;
				LAST.clear();
			}
			return;
		}
		boolean was = on;
		on = slowed(level, me);
		if (!on) {
			LAST.clear();
			return;
		}
		if (!was || ++beat >= BEAT) {
			beat = 0;
			// lub... dub
			level.playLocalSound(me.getX(), me.getEyeY(), me.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 0.7F, false);
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (entity == me || !(entity instanceof LivingEntity || entity instanceof Projectile) || entity.distanceToSqr(me) > TRAIL_RANGE * TRAIL_RANGE) {
				continue;
			}
			Vec3 now = entity.getBoundingBox().getCenter();
			Vec3 last = LAST.put(entity.getId(), now);
			if (last != null && last.distanceToSqr(now) > TRAIL_MOVE * TRAIL_MOVE) {
				// a mote at each step, left hanging where it was
				Vec3 mid = last.lerp(now, 0.5);
				float size = entity instanceof Projectile ? 0.9F : 0.6F + entity.getBbWidth() * 0.4F;
				level.addParticle(MagicFx.mote(TRAIL, size), last.x, last.y, last.z, 0.0, 0.0, 0.0);
				level.addParticle(MagicFx.mote(TRAIL, size * 0.7F), mid.x, mid.y, mid.z, 0.0, 0.0, 0.0);
			}
		}
	}
}
