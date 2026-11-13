package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The great lairs and who keeps them: a wild dragon's den (where it was first seen), the minotaur's labyrinth, a great
 * necromancer's crypt ({@link Necropolis}). Each is written down once, with the one who keeps it (its master), and stays
 * written (saved on the Overworld) until the master falls: then the lair is lifted for good ({@link #lift}), and what it
 * did to the land ({@link LairLand}) and to a fight in it ({@link LairBeats}) stops.
 * <p>
 * A dragon is a master only while it is wild; a tamed one (or one born to a player) never was.
 */
public final class GreatLairs {
	/** How often the loaded dragons and minotaurs are looked over for lairs not yet written down (ticks). */
	static final int LOOK_EVERY = 100;

	/** The kinds of great lair, how far its land is changed (chunks), and how far a fight in it is the lair's (blocks). */
	public enum Kind {
		DEN(6, 26), CRYPT(4, 16), RUINS(3, 22);

		public final int chunks, arena;

		Kind(int chunks, int arena) {
			this.chunks = chunks;
			this.arena = arena;
		}
	}

	/** One lair: its master, where, what kind, and whether it has been lifted. */
	public record Lair(UUID master, ResourceKey<Level> dimension, BlockPos at, Kind kind, boolean lifted) {
		static final Codec<Lair> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("master").forGetter(Lair::master),
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Lair::dimension),
				BlockPos.CODEC.fieldOf("at").forGetter(Lair::at),
				Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(Lair::kind),
				Codec.BOOL.optionalFieldOf("lifted", false).forGetter(Lair::lifted))
			.apply(i, Lair::new));

		/** The distance (blocks, flat) the land is changed to. */
		public int reach() {
			return this.kind.chunks * 16;
		}

		/** Whether {@code pos} in {@code level} is in the lair's land. */
		public boolean covers(Level level, BlockPos pos) {
			if (this.lifted || level.dimension() != this.dimension) {
				return false;
			}
			long dx = pos.getX() - this.at.getX(), dz = pos.getZ() - this.at.getZ();
			long r = reach();
			return dx * dx + dz * dz <= r * r;
		}
	}

	static final AttachmentType<List<Lair>> LAIRS = AttachmentRegistry.create(JaymosKingdoms.id("great_lairs"), b -> b.persistent(Lair.CODEC.listOf()));

	private static @Nullable List<Lair> cache;

	private GreatLairs() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GreatLairs::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> fell(entity));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> cache = null);
	}

	private static List<Lair> all(MinecraftServer server) {
		if (cache == null) {
			cache = new ArrayList<>(Factions.level(server).getAttachedOrElse(LAIRS, List.<Lair>of()));
		}
		return cache;
	}

	private static void save(MinecraftServer server) {
		if (cache != null) {
			Factions.level(server).setAttached(LAIRS, List.copyOf(cache));
		}
	}

	/** Every lair still standing in {@code level}'s dimension. */
	public static List<Lair> standing(ServerLevel level) {
		List<Lair> out = new ArrayList<>();
		for (Lair lair : all(level.getServer())) {
			if (!lair.lifted() && lair.dimension() == level.dimension()) {
				out.add(lair);
			}
		}
		return out;
	}

	/** The lair standing that covers {@code pos}, or null. */
	public static @Nullable Lair at(ServerLevel level, BlockPos pos) {
		for (Lair lair : all(level.getServer())) {
			if (lair.covers(level, pos)) {
				return lair;
			}
		}
		return null;
	}

	/** The lair {@code master} keeps, or null. */
	public static @Nullable Lair of(MinecraftServer server, UUID master) {
		for (Lair lair : all(server)) {
			if (lair.master().equals(master)) {
				return lair;
			}
		}
		return null;
	}

	/** Writes down a lair for {@code master} (once). */
	public static void found(MinecraftServer server, UUID master, ServerLevel level, BlockPos at, Kind kind) {
		if (of(server, master) != null) {
			return;
		}
		all(server).add(new Lair(master, level.dimension(), at, kind, false));
		save(server);
	}

	/** The lair of {@code master} is lifted: whatever it did stops. */
	public static void lift(MinecraftServer server, UUID master) {
		List<Lair> lairs = all(server);
		for (int i = 0; i < lairs.size(); i++) {
			Lair lair = lairs.get(i);
			if (lair.master().equals(master) && !lair.lifted()) {
				lairs.set(i, new Lair(lair.master(), lair.dimension(), lair.at(), lair.kind(), true));
				save(server);
			}
		}
	}

	/** A master falls: a dragon's or a minotaur's lair lifts at once (a necromancer's, when his vessel is gone, {@link Necropolis}). */
	private static void fell(LivingEntity entity) {
		MinecraftServer server = entity.level().getServer();
		if (server == null || entity instanceof GreatNecromancer) {
			return;
		}
		Lair lair = of(server, entity.getUUID());
		if (lair != null && !lair.lifted()) {
			lift(server, entity.getUUID());
		}
	}

	/** Dragons and minotaurs met in the world for the first time give their lairs. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				if (!(e instanceof LivingEntity living) || !living.isAlive()) {
					continue;
				}
				if (e instanceof com.jaymoskingdoms.dragons.WindDragon dragon) {
					Lair lair = of(server, dragon.getUUID());
					if (dragon.isTamed()) {
						if (lair != null && !lair.lifted()) {
							lift(server, dragon.getUUID());
						}
					} else if (lair == null) {
						found(server, dragon.getUUID(), level, dragon.blockPosition(), Kind.DEN);
					}
				} else if (e.entityTags().contains(com.jaymoskingdoms.beasts.Labyrinths.TAG) && of(server, e.getUUID()) == null) {
					found(server, e.getUUID(), level, e.blockPosition(), Kind.RUINS);
				}
			}
		}
	}
}
