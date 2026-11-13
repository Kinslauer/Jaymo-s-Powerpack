package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Storm Rage (SevTech-Plan patch 5's note; Andy, 2026-11-12: "add it"). Now and then a rust storm blows over Rustfall, one
 * of Jaymo's Powers' far worlds: a red-brown haze and a howling wind, and while it blows its foes rage, a world tier
 * harder than they were ({@link WorldTiers#raise}; back as they were when it's over). The warning comes only from
 * someone who reads the sky: a sky hermit ({@link Hermits}) or a galactic patrolman ({@code ZFighterFolk}) near you
 * says it, wherever you are, while the storm is brewing. Never a Tidings bird, never a line out of nowhere: no one near,
 * no warning. The storm itself says nothing on screen: you see the haze and hear the wind.
 * <p>
 * Kept in memory: a restart just brews the next storm afresh.
 */
public final class RustStorms {
	/** Between storms (ticks: least, more), how long one brews (the warning window), and how long it blows. */
	static final long BETWEEN_MIN = 24000L, BETWEEN_MORE = 24000L, BREWS = 6000L, BLOWS = 3600L;
	/** How often it's looked at, how near a hermit or patrolman must be to tell you, and how near a player foes rage. */
	static final int TICKS = 40;
	static final double TELL_RANGE = 24.0, RAGE_RANGE = 64.0;
	/** How much harder (world tiers) a raging foe is. */
	static final int RAGE = 1;
	static final int RUST = 0xB5532A;

	/** On a raging foe: the world tier it had before the storm. */
	static final AttachmentType<Integer> RAGED = AttachmentRegistry.create(JaymosKingdoms.id("rust_storm_raged"), builder -> builder.persistent(Codec.INT));

	/** When the next storm starts blowing (game time; MIN_VALUE: not yet brewed). */
	private static long next = Long.MIN_VALUE;
	private static final Set<UUID> WARNED = new HashSet<>();
	private static boolean blowing;

	private RustStorms() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RustStorms::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// (one that was raging when it was saved, and the storm's over)
			if (entity instanceof Mob mob && mob.hasAttached(RAGED) && !(blowing && rustfall(level))) {
				calm(mob);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			next = Long.MIN_VALUE;
			WARNED.clear();
			blowing = false;
		});
	}

	static boolean rustfall(ServerLevel level) {
		return StarTouched.world(level) == 1;
	}

	private static @Nullable ServerLevel rustfall(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			if (rustfall(level)) {
				return level;
			}
		}
		return null;
	}

	/** A storm is blowing on Rustfall now. */
	public static boolean blowing() {
		return blowing;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % TICKS != 0) {
			return;
		}
		ServerLevel rust = rustfall(server);
		if (rust == null) {
			return;
		}
		long now = rust.getGameTime();
		RandomSource random = rust.getRandom();
		if (next == Long.MIN_VALUE) {
			next = now + BETWEEN_MIN + (long)(random.nextDouble() * BETWEEN_MORE);
		}
		if (!blowing && now >= next) {
			blowing = true;
			for (ServerPlayer p : rust.players()) {
				rust.playSound(null, p.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 0.8F, 0.5F);
			}
		}
		if (blowing && now >= next + BLOWS) {
			blowing = false;
			WARNED.clear();
			next = now + BETWEEN_MIN + (long)(random.nextDouble() * BETWEEN_MORE);
			for (Mob mob : rust.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Mob.class), m -> m.hasAttached(RAGED))) {
				calm(mob);
			}
			return;
		}
		if (!blowing && now >= next - BREWS) {
			warn(server);
		}
		if (blowing) {
			blow(rust);
		}
	}

	/** While it brews: a hermit or patrolman near a player who hasn't heard tells them. */
	private static void warn(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (WARNED.contains(p.getUUID()) || !p.isAlive()) {
				continue;
			}
			ServerLevel level = p.level();
			for (Mob teller : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(TELL_RANGE), RustStorms::readsTheSky)) {
				boolean patrol = teller.entityTags().contains(ZFighterFolk.PATROL_TAG);
				String key = "message.jaymoskingdoms.rust_storm.warn." + (patrol ? "patrolman" : "hermit") + (rustfall(level) ? ".here" : "");
				Speech.sayTo(level, teller, p, Component.translatable(key), Speech.YELLOW);
				WARNED.add(p.getUUID());
				break;
			}
		}
	}

	private static boolean readsTheSky(Mob m) {
		return m.isAlive() && (Hermits.is(m) || m.entityTags().contains(ZFighterFolk.PATROL_TAG));
	}

	/** While it blows: the haze and wind round each player on Rustfall, and the foes near them rage. */
	private static void blow(ServerLevel rust) {
		for (ServerPlayer p : rust.players()) {
			rust.sendParticles(p, Fx.miasma(RUST, 2.0F), false, false, p.getX(), p.getY() + 1.5, p.getZ(), 24, 10.0, 4.0, 10.0, 0.05);
			if (rust.getRandom().nextInt(4) == 0) {
				rust.playSound(null, p.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 0.4F, 0.4F + rust.getRandom().nextFloat() * 0.2F);
			}
			for (Mob mob : rust.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(RAGE_RANGE),
				m -> m.isAlive() && !m.hasAttached(RAGED) && WorldTiers.hostile(m))) {
				int was = mob.getAttachedOrElse(WorldTiers.MOB_TIER, 0);
				mob.setAttached(RAGED, was);
				WorldTiers.raise(mob, Math.min(was + RAGE, WorldTiers.Tier.values().length - 1));
				Fx.burst(rust, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), RUST, 1.2F, 6, mob.getBbWidth() * 0.5, mob.getBbHeight() * 0.4,
					mob.getBbWidth() * 0.5);
			}
		}
	}

	/** {@code mob}'s rage passes: back to the tier it had. */
	private static void calm(Mob mob) {
		Integer was = mob.getAttached(RAGED);
		mob.removeAttached(RAGED);
		if (was != null) {
			WorldTiers.raise(mob, was);
		}
	}

	/** For other code that names foes: {@code e} is raging in a rust storm. */
	public static boolean raging(Entity e) {
		return e.hasAttached(RAGED);
	}
}
