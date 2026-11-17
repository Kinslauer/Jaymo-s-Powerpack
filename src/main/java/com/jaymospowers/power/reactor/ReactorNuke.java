package com.jaymospowers.power.reactor;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Nuke, the Reactor Heart's C once ascended. Hold C and the heart is wound up, slowly ({@link #WIND} ticks, slowed to a
 * crawl while it builds); let go wound all the way and it goes off round you: {@link #RADIUS} blocks, up to {@link #HARM}
 * at the middle, and you take none of it. It leaves <b>fallout</b>: for {@link #FALLOUT_TICKS} ticks the ground within
 * {@link #FALLOUT_RADIUS} sickens everyone in it but you ({@link #FALLOUT_HARM} a second, and poison). Let go early and it
 * winds down for nothing. Every bit of charge goes into it. No block is broken.
 */
final class ReactorNuke {
	// ---- tuning ----
	private static final String TABLE = "reactor_nuke";
	/** The wind-up (ticks), the blast's reach and harm at the middle, the wait after. */
	static final int WIND = 100, COOLDOWN = 20 * 180;
	static final double RADIUS = 14.0;
	static final float HARM = 30.0F;
	/** The fallout: how long (ticks), how wide, harm a second. */
	static final int FALLOUT_TICKS = 20 * 30;
	static final double FALLOUT_RADIUS = 10.0;
	static final float FALLOUT_HARM = 1.0F;

	private static final Map<UUID, Long> WINDING = new HashMap<>();

	private record Fallout(UUID owner, ResourceKey<Level> dim, Vec3 at, long until) {
	}

	private static final List<Fallout> FALLOUT = new ArrayList<>();

	private ReactorNuke() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ReactorNuke::serverTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WINDING.clear();
			FALLOUT.clear();
		});
	}

	static void forget(ServerPlayer player) {
		WINDING.remove(player.getUUID());
	}

	static void key(ServerPlayer player, boolean down) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (down) {
			if (!PickKit.ready(TABLE, player, 0) && !player.getAbilities().instabuild) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.reactor.nuke_wait", (PickKit.left(TABLE, player, 0) + 19) / 20)
					.withStyle(ChatFormatting.GRAY));
				return;
			}
			WINDING.put(player.getUUID(), now);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.5F);
			return;
		}
		Long since = WINDING.remove(player.getUUID());
		if (since == null) {
			return;
		}
		if (now - since < WIND) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
			return;
		}
		detonate(player);
	}

	private static void detonate(ServerPlayer player) {
		ServerLevel level = player.level();
		PickKit.spend(TABLE, player, 0, COOLDOWN);
		float charge = ReactorPower.live(player).charge;
		ReactorPower.live(player).charge = 0.0F;
		float f = 0.6F + 0.4F * charge / ReactorPower.FULL;
		Vec3 at = player.position().add(0.0, 1.0, 0.0);
		ReactorPower.blast(player, at, RADIUS, HARM * f, 2.0);
		MagicFx.blast(level, at, (float)RADIUS, ReactorPower.CORE, 30);
		MagicFx.ring(level, player.position().add(0.0, 0.2, 0.0), (float)RADIUS * 1.3F, ReactorPower.GLOW);
		MagicFx.beam(level, player.position(), player.position().add(0.0, RADIUS * 2.0, 0.0), 3.0F, ReactorPower.CORE, 30);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 6.0F, 0.4F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 0.5F);
		com.jaymospowers.core.Shakes.around(level, at, RADIUS * 4.0, 2.5F);
		FALLOUT.add(new Fallout(player.getUUID(), level.dimension(), player.position(), level.getGameTime() + FALLOUT_TICKS));
	}

	/** Per player: the wind-up holds them still and hums. */
	static void tick(ServerPlayer player) {
		Long since = WINDING.get(player.getUUID());
		if (since == null) {
			return;
		}
		ServerLevel level = player.level();
		long t = level.getGameTime() - since;
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 4, false, false));
		if (t % 5 == 0) {
			float k = Math.min(1.0F, t / (float)WIND);
			MagicFx.orb(level, player.position().add(0.0, 1.0, 0.0), 0.6F + 1.4F * k, ReactorPower.GLOW, 6);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.5F + k);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.reactor.nuke_wind", PickKit.gauge(k, 10))
				.withStyle(k >= 1.0F ? ChatFormatting.WHITE : ChatFormatting.GREEN));
		}
	}

	private static void serverTick(MinecraftServer server) {
		if (FALLOUT.isEmpty()) {
			return;
		}
		for (Iterator<Fallout> it = FALLOUT.iterator(); it.hasNext();) {
			Fallout f = it.next();
			ServerLevel level = server.getLevel(f.dim());
			if (level == null || level.getGameTime() >= f.until()) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (now % 10 == 0) {
				level.sendParticles(MagicFx.miasma(ReactorPower.GLOW, 1.4F), f.at().x, f.at().y + 0.6, f.at().z, 10, FALLOUT_RADIUS * 0.5, 0.4,
					FALLOUT_RADIUS * 0.5, 0.01);
			}
			if (now % 20 != 0) {
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(f.owner());
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(f.at(), f.at()).inflate(FALLOUT_RADIUS, 4.0, FALLOUT_RADIUS),
				e -> e.isAlive() && !e.getUUID().equals(f.owner()) && e.position().distanceToSqr(f.at()) <= FALLOUT_RADIUS * FALLOUT_RADIUS)) {
				e.hurtServer(level, owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().magic(), FALLOUT_HARM);
				e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
			}
		}
	}
}
