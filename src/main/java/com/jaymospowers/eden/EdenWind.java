package com.jaymospowers.eden;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * The wind of the Verdant World's wind valley: a steady sideways push on whatever is out under the open sky there. It
 * comes from one quarter for the whole valley and swings slowly round ({@link #TURN_TICKS} for a full turn). Thrown and
 * shot things and dropped items are pushed here, on the server; a player is pushed on their own client
 * ({@code client/eden/EdenWindClient}), since a player's movement is the client's.
 */
public final class EdenWind {
	// ---- tuning ----
	/** The push per tick on someone standing, and in the air (a jump, a fall, a glide). */
	public static final double PUSH_GROUND = 0.008, PUSH_AIR = 0.022;
	/** The push per tick on thrown and shot things and on dropped items. */
	public static final double PUSH_LOOSE = 0.012;
	/** Wind users are pushed this many times harder: the valley wind is theirs, and takes them with it. */
	public static final double WIND_KIN = 2.5;
	/** Ticks for the wind to swing once round the compass (five days). */
	public static final long TURN_TICKS = 120000L;
	/** Loose things are pushed within this many blocks of a player. */
	public static final double NEAR = 48.0;

	public static final TagKey<Biome> WINDY = TagKey.create(Registries.BIOME, JaymosPowers.id("eden_windy"));

	private EdenWind() {
	}

	/** The way the wind blows now (a level unit vector). */
	public static Vec3 blowing(Level level) {
		double angle = Math.PI * 2.0 * (Math.floorMod(level.getGameTime(), TURN_TICKS) / (double) TURN_TICKS);
		return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
	}

	/** Whether the wind reaches {@code entity}: in a windy biome, under the open sky. */
	public static boolean in(Entity entity) {
		BlockPos pos = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
		Level level = entity.level();
		return level.getBiome(pos).is(WINDY) && level.canSeeSky(pos);
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(EdenWind::tick);
	}

	private static void tick(ServerLevel level) {
		if (level.players().isEmpty()) {
			return;
		}
		Vec3 push = null;
		for (ServerPlayer player : level.players()) {
			if (!level.getBiome(player.blockPosition()).is(WINDY)) {
				continue;
			}
			if (push == null) {
				push = blowing(level).scale(PUSH_LOOSE);
			}
			for (Entity loose : level.getEntities(player, player.getBoundingBox().inflate(NEAR), e -> e instanceof Projectile || e instanceof ItemEntity)) {
				if (!loose.onGround() && in(loose)) {
					loose.setDeltaMovement(loose.getDeltaMovement().add(push));
				}
			}
		}
	}
}
