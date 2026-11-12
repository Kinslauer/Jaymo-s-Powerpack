package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Andy (2026-11-11): "The tinkerer is the only other chrono user... he conquered his echo, and conquered death... he
 * should cheat death the same way if ever he is killed, and his echos will reappear and act in his place, which is why
 * he seems to be multiple places at once."
 * <p>
 * The Mad Tinkerer ({@link ZFighterAndroid#TAG}) can be hurt and killed, unlike the other characters. When he dies, the
 * body falls as anyone's would, and {@link #RETURN_DELAY} later one of his echoes steps out of the air where he fell, in
 * the same shimmer a Chrono's echo arrives in, and carries on in his place: same bench, same leaving time, same work.
 * An operation he was in the middle of goes on under the echo's hands ({@link ZFighterAndroid#handOver}). He talks as
 * if nothing happened. Wherever else he is standing at that moment, he flinches and mutters about it to whoever is near
 * ({@link #ASIDE_NEAR}): he is in more than one place, and always was.
 * <p>
 * Not saved: an echo still owed when the server stops never comes, and he turns up again the usual way.
 */
public final class TinkererEchoes {
	/** How long after he dies his echo steps in (ticks). */
	static final int RETURN_DELAY = 60;
	/** How long the echo shimmers after arriving (ticks), and how often (ticks). */
	static final int SHIMMER_TICKS = 1200;
	static final int SHIMMER_EVERY = 10;
	/** Who hears him come back (blocks), and who hears another of him react to it (blocks). */
	static final double BACK_NEAR = 32.0;
	static final double ASIDE_NEAR = 16.0;
	/** How many lines of each kind there are (lang {@code message.jaymospowers.tinkerer_echo.back.N} and {@code .aside.N}). */
	static final int BACK_LINES = 4;
	static final int ASIDE_LINES = 3;
	/** The echo's shards: the Chrono echo's own pale blue glint. */
	static final ParticleOptions SHARD = MagicFx.wisp(0xA8E8FF, 0.7F);

	/** An echo owed where he fell. */
	private record Owed(UUID dead, ResourceKey<Level> dimension, Vec3 at, float yaw, List<String> tags, long due) {
	}

	private static final List<Owed> OWED = new ArrayList<>();
	/** Echoes that arrived lately, and until when they shimmer (game time). */
	private static final Map<UUID, Long> SHIMMERING = new HashMap<>();

	private TinkererEchoes() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register(TinkererEchoes::died);
		ServerTickEvents.END_SERVER_TICK.register(TinkererEchoes::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OWED.clear();
			SHIMMERING.clear();
		});
	}

	/** He can be hurt and killed: the characters' spawning makes them untouchable, he isn't. */
	static void mortal(Entity tinkerer) {
		tinkerer.setPermanentlyInvulnerable(false);
	}

	/** Whether {@code scientist} died and an echo of him is still on its way. */
	static boolean owed(UUID scientist) {
		for (Owed owed : OWED) {
			if (owed.dead.equals(scientist)) {
				return true;
			}
		}
		return false;
	}

	private static void died(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (!entity.entityTags().contains(ZFighterAndroid.TAG) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		OWED.add(new Owed(entity.getUUID(), level.dimension(), entity.position(), entity.getYRot(), List.copyOf(entity.entityTags()),
			level.getGameTime() + RETURN_DELAY));
		SHIMMERING.remove(entity.getUUID());
		// (a ripple where he fell: time already closing over the gap)
		level.sendParticles(SHARD, entity.getX(), entity.getY() + 1.0, entity.getZ(), 24, 0.4, 0.8, 0.4, 0.05);
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 0.5F);
		// and everywhere else he is, he feels it
		for (ServerLevel other : level.getServer().getAllLevels()) {
			for (Entity him : other.getAllEntities()) {
				if (him != entity && him.isAlive() && him.entityTags().contains(ZFighterAndroid.TAG)) {
					aside(other, him);
				}
			}
		}
	}

	private static void aside(ServerLevel level, Entity him) {
		Component line = Component.translatable("message.jaymospowers.tinkerer_echo.aside." + (1 + level.getRandom().nextInt(ASIDE_LINES)));
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(him) <= ASIDE_NEAR * ASIDE_NEAR) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_scientist_says", him.getDisplayName(), line)
					.withStyle(ChatFormatting.AQUA));
			}
		}
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (Iterator<Owed> it = OWED.iterator(); it.hasNext(); ) {
			Owed owed = it.next();
			if (now < owed.due) {
				continue;
			}
			it.remove();
			ServerLevel level = server.getLevel(owed.dimension);
			if (level != null && level.isLoaded(BlockPos.containing(owed.at))) {
				step(level, owed);
			}
		}
		if (now % SHIMMER_EVERY == 0 && !SHIMMERING.isEmpty()) {
			for (Iterator<Map.Entry<UUID, Long>> it = SHIMMERING.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<UUID, Long> entry = it.next();
				Entity echo = null;
				for (ServerLevel level : server.getAllLevels()) {
					echo = level.getEntity(entry.getKey());
					if (echo != null) {
						break;
					}
				}
				if (echo == null || !echo.isAlive() || now > entry.getValue()) {
					it.remove();
					continue;
				}
				if (echo.level() instanceof ServerLevel level) {
					level.sendParticles(SHARD, echo.getX(), echo.getY() + 1.0, echo.getZ(), 2, 0.3, 0.7, 0.3, 0.01);
				}
			}
		}
	}

	/** His echo steps in where he fell and takes up where he left off. */
	private static void step(ServerLevel level, Owed owed) {
		Entity echo = ZFighterAndroid.tinkerer(level, owed.at, owed.yaw);
		if (echo == null) {
			return;
		}
		for (String tag : owed.tags) {
			echo.addTag(tag);
		}
		ZFighterAndroid.handOver(owed.dead, echo.getUUID());
		SHIMMERING.put(echo.getUUID(), level.getGameTime() + SHIMMER_TICKS);
		double x = echo.getX(), y = echo.getY() + 1.0, z = echo.getZ();
		// (the Chrono echo's arrival: a violet burst, light drawn in from around, a bell and glass)
		level.sendParticles(MagicFx.VOID, x, y, z, 60, 0.5, 0.9, 0.5, 0.3);
		level.sendParticles(SHARD, x, y, z, 30, 0.6, 1.0, 0.6, 0.1);
		for (int i = 0; i < 12; i++) {
			double angle = i * Math.PI / 6.0, dx = Math.cos(angle), dz = Math.sin(angle);
			level.sendParticles(MagicFx.LIGHT, x + dx * 3.0, y, z + dz * 3.0, 0, -dx, 0.0, -dz, 0.25);
		}
		level.playSound(null, x, y, z, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.2F, 0.5F);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.2F, 0.5F);
		level.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.0F, 0.5F);
		Component line = Component.translatable("message.jaymospowers.tinkerer_echo.back." + (1 + level.getRandom().nextInt(BACK_LINES)));
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(echo) <= BACK_NEAR * BACK_NEAR) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_scientist_says", echo.getDisplayName(), line)
					.withStyle(ChatFormatting.AQUA));
			}
		}
	}
}
