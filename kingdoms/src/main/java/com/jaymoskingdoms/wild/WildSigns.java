package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the animals tell you (Andy's nature picks, 2026-11-26; {@link Nature}).
 * <ul>
 * <li><b>A storm felt coming</b>: in the last {@link #STORM_AHEAD} ticks before thunder (and while it lasts), grazers
 * and small prey make for cover (under trees, an overhang), and the birds go quiet.</li>
 * <li><b>The dawn chorus and night calls</b>: birds near players sing far more at dawn ({@link #DAWN_SINGS}) and a little
 * by day; at night the owls call, and in spring and summer the frogs and crickets fill the dark.</li>
 * <li><b>The woods go quiet round a hunter</b>: no bird sings within {@link #HUSH} of a hunter that has something in its
 * sights, or one stalking a player ({@link WolfStalkers#stalking}); the first bird to see it gives one alarm call.</li>
 * </ul>
 * The quiet is kept in {@code mixin/fifth/BirdSongMixin}, which already silenced birds in a great lair's land.
 */
public final class WildSigns {
	static final int STEP_EVERY = 20;
	static final int STORM_AHEAD = 20 * 60;
	static final int COVER_LOOK = 12;
	static final double HUSH = 24.0;
	static final int DAWN_SINGS = 3, DAY_SINGS = 30, NIGHT_CALLS = 6;

	private static final TagKey<EntityType<?>> SONGBIRDS = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("songbirds"));

	private record Hunter(Level level, Vec3 at) {
	}

	/** Where hunters are on the hunt just now (refreshed every step). */
	private static List<Hunter> hunting = List.of();
	private static boolean storm;
	/** A bird giving its alarm call sings through the quiet. */
	private static boolean alarm;

	private WildSigns() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WildSigns::tick);
	}

	/** Whether {@code mob} is a bird kept quiet: a hunter near, or a storm coming. */
	public static boolean hushed(Mob mob) {
		if (alarm || !(Nature.is(mob, Nature.CHORUS) || Nature.is(mob, SONGBIRDS))) {
			return false;
		}
		if (storm) {
			return true;
		}
		for (Hunter h : hunting) {
			if (h.level == mob.level() && h.at.distanceToSqr(mob.position()) < HUSH * HUSH) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % STEP_EVERY != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		var weather = overworld.getWeatherData();
		storm = overworld.isThundering() || !weather.isThundering() && weather.getThunderTime() > 0 && weather.getThunderTime() < STORM_AHEAD
			&& (overworld.isRaining() || weather.getRainTime() < STORM_AHEAD);
		List<Hunter> now = new ArrayList<>();
		for (TagKey<EntityType<?>> kind : List.of(Nature.HERD_HUNTERS, Nature.BEARS)) {
			for (Mob h : Nature.of(kind)) {
				if (h.getTarget() != null || WolfStalkers.stalking(h)) {
					if (now.stream().noneMatch(o -> o.level == h.level() && o.at.distanceToSqr(h.position()) < HUSH * HUSH)) {
						alarm(h);
					}
					now.add(new Hunter(h.level(), h.position()));
				}
			}
		}
		hunting = now;
		if (storm && t % 100 == 0) {
			for (TagKey<EntityType<?>> kind : List.of(Nature.GRAZERS, Nature.SMALL_PREY)) {
				for (Mob m : Nature.of(kind)) {
					if (Nature.free(m) && m.level() instanceof ServerLevel level && level.canSeeSky(m.blockPosition().above())) {
						BlockPos cover = cover(level, m.blockPosition());
						if (cover != null) {
							Nature.go(m, Vec3.atBottomCenterOf(cover), 1.1);
						}
					}
				}
			}
		}
		calls(overworld);
	}

	/** The first bird near a hunter that's just started gives one alarm call. */
	private static void alarm(Mob hunter) {
		if (!(hunter.level() instanceof ServerLevel level)) {
			return;
		}
		for (Mob bird : level.getEntitiesOfClass(Mob.class, hunter.getBoundingBox().inflate(HUSH),
			b -> b.isAlive() && (Nature.is(b, Nature.CHORUS) || Nature.is(b, SONGBIRDS)))) {
			if (level.getRandom().nextInt(4) == 0) {
				alarm = true;
				try {
					bird.playAmbientSound();
				} finally {
					alarm = false;
				}
			}
			return;
		}
	}

	/** Dawn chorus, day song, night calls. */
	private static void calls(ServerLevel overworld) {
		long day = Math.floorMod(overworld.getOverworldClockTime(), 24000L);
		boolean dawn = day >= 23000L || day < 1500L, night = day >= 13000L && day < 23000L;
		RandomSource random = overworld.getRandom();
		if (!night && !storm) {
			int odds = dawn ? DAWN_SINGS : DAY_SINGS;
			for (Mob bird : Nature.of(Nature.CHORUS)) {
				if (random.nextInt(odds) == 0 && !hushed(bird)) {
					bird.playAmbientSound();
				}
			}
		}
		if (night) {
			String season = Nature.season(overworld);
			boolean warm = season == null || "spring".equals(season) || "summer".equals(season);
			for (Mob caller : Nature.of(Nature.NIGHT_CALLERS)) {
				boolean owl = Nature.is(caller, Nature.OWLS);
				if ((owl || warm) && random.nextInt(NIGHT_CALLS) == 0) {
					caller.playAmbientSound();
				}
			}
		}
	}

	private static @Nullable BlockPos cover(ServerLevel level, BlockPos at) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < 10; i++) {
			BlockPos p = at.offset(random.nextInt(COVER_LOOK * 2 + 1) - COVER_LOOK, random.nextInt(5) - 2, random.nextInt(COVER_LOOK * 2 + 1) - COVER_LOOK);
			if (level.isLoaded(p) && level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolid() && !level.canSeeSky(p.above())) {
				return p;
			}
		}
		return null;
	}
}
