package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.184) The elders a {@link Lineage} leaves behind live lives of their own. Andy: "they can still choose to do what
 * they used to under your control, only autonomous now. So you could ask your father/mother to go on an adventure with
 * you for old times sake, or even find them on travels of their own."
 * Asked along, they follow you as any MCA family does. Left be, an elder stays home a few days, then sets off for a
 * village or a realm's seat some way off ({@link #FAR_MIN} to {@link #FAR_MAX} blocks), stays a few days there, and comes
 * home again. They only set off or come back while nobody's near enough to see them go, and not while following anyone.
 * The journey itself passes unseen: they're simply there, so a traveller can come upon them in a far town. Whoever they
 * used to be (their heir, the player) gets an MCA letter when they leave and when they're home.
 * An elder who isn't loaded when their visit ends stays where they are until someone comes by, then heads home.
 */
public final class Elders {
	/** Days at home between journeys, and days away on one. */
	static final int HOME_DAYS_MIN = 2, HOME_DAYS_MAX = 5, AWAY_DAYS_MIN = 1, AWAY_DAYS_MAX = 3;
	/** How far a journey goes (blocks from home). */
	static final int FAR_MIN = 250, FAR_MAX = 1500;
	/** Nobody within this far may see them vanish or appear (blocks). */
	static final double UNSEEN = 64.0;
	/** With nowhere to go, they look again after this long (ticks). */
	static final long RETRY_TICKS = 6000L;
	/** How often the elders are looked at (ticks). */
	static final int CHECK_EVERY = 100;
	static final long DAY_TICKS = 24000L;
	public static final int HOME = 0, AWAY = 1;

	/**
	 * An elder's journeys: {@code heir} the player they used to be, {@code home} where they live, {@code away} and
	 * {@code place} where they've gone (while {@link #AWAY}), and the game time {@code until} they next move.
	 */
	public record Journey(UUID heir, BlockPos home, BlockPos away, String place, int phase, long until) {
		static final Codec<Journey> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("heir").forGetter(Journey::heir),
			BlockPos.CODEC.fieldOf("home").forGetter(Journey::home),
			BlockPos.CODEC.fieldOf("away").forGetter(Journey::away),
			Codec.STRING.fieldOf("place").forGetter(Journey::place),
			Codec.INT.fieldOf("phase").forGetter(Journey::phase),
			Codec.LONG.fieldOf("until").forGetter(Journey::until)).apply(i, Journey::new));
	}

	/** On a lineage's elder: their journeys. Saved. */
	public static final AttachmentType<Journey> JOURNEY = AttachmentRegistry.create(JaymosKingdoms.id("elder_journey"),
		builder -> builder.persistent(Journey.CODEC));

	/** The elders loaded just now. */
	private static final Set<PathfinderMob> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());

	private Elders() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && mob.hasAttached(JOURNEY)) {
				LOADED.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity));
		ServerTickEvents.END_SERVER_TICK.register(Elders::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED.clear());
	}

	/** {@code elder} was just left behind by {@code heir}: home is where they stand, and they'll stay a while first. */
	static void start(PathfinderMob elder, ServerPlayer heir) {
		long now = elder.level().getGameTime();
		elder.setAttached(JOURNEY, new Journey(heir.getUUID(), elder.blockPosition(), elder.blockPosition(), "", HOME,
			now + days(elder, HOME_DAYS_MIN, HOME_DAYS_MAX)));
		LOADED.add(elder);
	}

	private static long days(PathfinderMob elder, int min, int max) {
		return (min + elder.getRandom().nextInt(max - min + 1)) * DAY_TICKS;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0 || LOADED.isEmpty()) {
			return;
		}
		for (PathfinderMob elder : new ArrayList<>(LOADED)) {
			if (elder.isRemoved() || !elder.isAlive()) {
				LOADED.remove(elder);
				continue;
			}
			step(elder);
		}
	}

	private static void step(PathfinderMob elder) {
		Journey j = elder.getAttached(JOURNEY);
		if (j == null || !(elder.level() instanceof ServerLevel level) || level != level.getServer().overworld()) {
			return;
		}
		long now = level.getGameTime();
		if (now < j.until() || Downed.isDowned(elder) || elder.isSleeping() || elder.isPassenger()
			|| elder instanceof Villager v && McaCompat.following(v) != null || seen(level, elder)) {
			return;
		}
		Component name = elder.getDisplayName();
		if (j.phase() == HOME) {
			Place to = destination(level, j.home(), elder);
			if (to == null) {
				elder.setAttached(JOURNEY, new Journey(j.heir(), j.home(), j.away(), j.place(), HOME, now + RETRY_TICKS));
				return;
			}
			move(level, elder, to.pos());
			elder.setAttached(JOURNEY, new Journey(j.heir(), j.home(), to.pos(), to.name(), AWAY, now + days(elder, AWAY_DAYS_MIN, AWAY_DAYS_MAX)));
			McaCompat.letter(level, j.heir(), Component.translatable("message.jaymoskingdoms.elder.set_off", name, to.name()));
		} else {
			move(level, elder, j.home());
			elder.setAttached(JOURNEY, new Journey(j.heir(), j.home(), j.home(), "", HOME, now + days(elder, HOME_DAYS_MIN, HOME_DAYS_MAX)));
			McaCompat.letter(level, j.heir(), Component.translatable("message.jaymoskingdoms.elder.home", name, j.place()));
		}
	}

	/** Whether a player is near enough {@code elder} to see them go. */
	private static boolean seen(ServerLevel level, PathfinderMob elder) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(elder) < UNSEEN * UNSEEN) {
				return true;
			}
		}
		return false;
	}

	/** Somewhere to go and its name. */
	private record Place(BlockPos pos, String name) {
	}

	/** A village (MCA's) or a standing realm's seat between {@link #FAR_MIN} and {@link #FAR_MAX} from {@code home}, or null. */
	private static @Nullable Place destination(ServerLevel level, BlockPos home, PathfinderMob elder) {
		List<Place> places = new ArrayList<>();
		for (McaCompat.Town town : McaCompat.towns(level)) {
			places.add(new Place(town.center(), town.name()));
		}
		for (Faction f : Factions.all(level.getServer())) {
			if (f.type().isRealm() && !f.fallen()) {
				places.add(new Place(f.seat(), f.name()));
			}
		}
		places.removeIf(p -> {
			double d = Math.sqrt(p.pos().distSqr(home));
			return d < FAR_MIN || d > FAR_MAX;
		});
		if (places.isEmpty()) {
			return null;
		}
		Place p = places.get(elder.getRandom().nextInt(places.size()));
		return new Place(p.pos().offset(elder.getRandom().nextInt(17) - 8, 0, elder.getRandom().nextInt(17) - 8), p.name());
	}

	/** Puts {@code elder} on the ground at {@code to} (its chunk loaded for it), with a puff where they stood. */
	private static void move(ServerLevel level, PathfinderMob elder, BlockPos to) {
		level.sendParticles(ParticleTypes.POOF, elder.getX(), elder.getY() + 1.0, elder.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
		elder.getNavigation().stop();
		level.getChunk(to.getX() >> 4, to.getZ() >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, to.getX(), to.getZ());
		elder.teleportTo(to.getX() + 0.5, y, to.getZ() + 0.5);
	}
}
