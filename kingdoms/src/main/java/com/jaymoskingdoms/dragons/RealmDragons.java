package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.FactionType;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Realm wyverns (Andy, on Dragonforge's sky patrols: "there should be dragons that do this rarely for kingdoms"): about one
 * kingdom in {@link #ONE_IN} keeps a wyvern at its seat. It belongs to the realm, not to anyone: tamed with no owner, it
 * keeps the seat as a wandering tamed dragon keeps its home ({@code WindDragon.keepHome}): strolls about it, flies patrols
 * round it and takes on monsters that come near. One slain is not replaced until {@link #REPLACE_TICKS} have passed.
 */
public final class RealmDragons {
	/** One kingdom in this many keeps one (chosen by its id, so a kingdom always does or never does). */
	static final int ONE_IN = 6;
	/** How often the seats are looked at (ticks), how far round a seat its wyvern is looked for, and how long before a lost one is replaced. */
	static final int CHECK_EVERY = 1200;
	static final double SEARCH = 128.0;
	static final long REPLACE_TICKS = 24000L * 3;

	/** When each realm's wyvern was last seen (or set down), by faction id, saved on the factions' level. */
	static final AttachmentType<Map<String, Long>> SEEN = AttachmentRegistry.create(JaymosKingdoms.id("realm_wyverns_seen"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	private RealmDragons() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RealmDragons::tick);
	}

	/** Whether the kingdom {@code id} keeps a wyvern. */
	public static boolean keeps(int id) {
		return Math.floorMod(id * 0x9E3779B1, ONE_IN) == 0;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0 || Wyverns.WYVERN == null) {
			return;
		}
		ServerLevel level = Factions.level(server);
		Map<String, Long> seen = new HashMap<>(level.getAttachedOrElse(SEEN, Map.of()));
		long now = level.getGameTime();
		boolean changed = false;
		for (Faction f : Factions.all(server, FactionType.KINGDOM)) {
			BlockPos seat = f.seat();
			if (!keeps(f.id()) || !level.isLoaded(seat) || !level.areEntitiesLoaded(ChunkPos.pack(seat))) {
				continue;
			}
			String key = Integer.toString(f.id());
			List<WyvernDragon> found = level.getEntitiesOfClass(WyvernDragon.class, new AABB(seat).inflate(SEARCH), w -> w.realm == f.id() && w.isAlive());
			if (!found.isEmpty()) {
				seen.put(key, now);
				changed = true;
				continue;
			}
			Long last = seen.get(key);
			if (last != null && now - last < REPLACE_TICKS) {
				continue;
			}
			if (place(level, f)) {
				seen.put(key, now);
				changed = true;
			}
		}
		if (changed) {
			level.setAttached(SEEN, Map.copyOf(seen));
		}
	}

	/** Sets a grown wyvern down at {@code f}'s seat, the realm's own. */
	private static boolean place(ServerLevel level, Faction f) {
		WyvernDragon wyvern = Wyverns.WYVERN.create(level, EntitySpawnReason.EVENT);
		if (wyvern == null) {
			return false;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, f.seat());
		wyvern.snapTo(top.getX() + 0.5, top.getY() + 1.0, top.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		wyvern.finalizeSpawn(level, level.getCurrentDifficultyAt(top), EntitySpawnReason.EVENT, null);
		wyvern.setTamed(true);
		wyvern.setPersistenceRequired();
		wyvern.realm = f.id();
		wyvern.setCustomName(Component.translatable("name.jaymoskingdoms.realm_wyvern", f.name()));
		if (!level.noCollision(wyvern)) {
			wyvern.discard();
			return false;
		}
		level.addFreshEntity(wyvern);
		JaymosKingdoms.LOGGER.debug("set a realm wyvern down at {}'s seat", f.name());
		return true;
	}
}
