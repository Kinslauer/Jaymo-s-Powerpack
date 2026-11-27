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
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.Professions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Realm dragons (Andy, on Dragonforge's sky patrols: "there should be dragons that do this rarely for kingdoms"; and "the
 * only exception is the kingdom's having their own dragon tamers"): about one kingdom in {@link #ONE_IN} keeps a dragon at
 * its seat, matched to its land ({@link #kindFor}: Notus in the hot lands, Boreas in the cold, Zephyrus in the swamps,
 * Stegonaut in the jungles, a wyvern anywhere else), and a dragon tamer (an MCA villager, {@code Professions.DRAGON_TAMER})
 * who keeps it. These are the one exception to dragons staying out of the Overworld until one has fallen. The dragon
 * belongs to the realm, not to anyone: tamed with no owner, it keeps the seat as a wandering tamed dragon keeps its home
 * ({@code WindDragon.keepHome}), and takes on whatever hurts its tamer. One slain is not replaced until
 * {@link #REPLACE_TICKS} have passed; a tamer lost is replaced at once while the dragon lives.
 */
public final class RealmDragons {
	/** One kingdom in this many keeps one (chosen by its id, so a kingdom always does or never does). */
	static final int ONE_IN = 6;
	/** How often the seats are looked at (ticks), how far round a seat its wyvern is looked for, and how long before a lost one is replaced. */
	static final int CHECK_EVERY = 1200;
	static final double SEARCH = 128.0;
	static final long REPLACE_TICKS = 24000L * 3;
	/** The tag a realm's dragon tamer carries, and how far from the seat they keep. */
	public static final String TAMER_TAG = "jaymoskingdoms.dragon_tamer";
	static final double TAMER_HOME = 24.0;

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
			List<WindDragon> found = level.getEntitiesOfClass(WindDragon.class, new AABB(seat).inflate(SEARCH), w -> w.realm == f.id() && w.isAlive());
			if (!found.isEmpty()) {
				seen.put(key, now);
				changed = true;
				keepTamer(level, f, found.get(0));
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

	/** The dragon a realm whose seat stands on {@code biome} keeps. */
	static EntityType<? extends WindDragon> kindFor(Holder<Biome> biome) {
		float heat = biome.value().getBaseTemperature();
		if (biome.is(BiomeTags.IS_JUNGLE) && Dragons.STEGONAUT_DRAGON != null) {
			return Dragons.STEGONAUT_DRAGON;
		}
		if ((biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) && Dragons.ZEPHYRUS_DRAGON != null) {
			return Dragons.ZEPHYRUS_DRAGON;
		}
		if ((heat >= 1.5F || biome.is(BiomeTags.IS_BADLANDS)) && Dragons.NOTUS_DRAGON != null) {
			return Dragons.NOTUS_DRAGON;
		}
		if (heat < 0.15F && Dragons.BOREAS_DRAGON != null) {
			return Dragons.BOREAS_DRAGON;
		}
		return Wyverns.WYVERN;
	}

	/** Sets a grown dragon of its land down at {@code f}'s seat, the realm's own, and its tamer beside it. */
	private static boolean place(ServerLevel level, Faction f) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, f.seat());
		WindDragon dragon = kindFor(level.getBiome(top)).create(level, EntitySpawnReason.EVENT);
		if (dragon == null) {
			return false;
		}
		dragon.snapTo(top.getX() + 0.5, top.getY() + 1.0, top.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(top), EntitySpawnReason.EVENT, null);
		dragon.setAge(0);
		dragon.setTamed(true);
		dragon.setPersistenceRequired();
		dragon.realm = f.id();
		dragon.setCustomName(Component.translatable("name.jaymoskingdoms.realm_dragon", f.name()));
		if (!level.noCollision(dragon)) {
			dragon.discard();
			return false;
		}
		level.addFreshEntity(dragon);
		keepTamer(level, f, dragon);
		JaymosKingdoms.LOGGER.debug("set a realm {} down at {}'s seat", dragon.species(), f.name());
		return true;
	}

	/** {@code dragon}'s tamer: the one it has, or a new one set down beside it. */
	private static void keepTamer(ServerLevel level, Faction f, WindDragon dragon) {
		if (dragon.keeper != null && level.getEntity(dragon.keeper) instanceof Villager v && v.isAlive()) {
			return;
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(SEARCH), v -> v.entityTags().contains(TAMER_TAG) && v.isAlive())) {
			dragon.keeper = v.getUUID();
			return;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dragon.blockPosition().offset(2, 0, 2));
		Villager tamer = McaCompat.newVillager(level, Vec3.atBottomCenterOf(at));
		if (tamer == null) {
			return;
		}
		tamer.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		tamer.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		tamer.setVillagerData(tamer.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(Professions.DRAGON_TAMER)));
		// (a little experience, so the game doesn't take the job off them for want of a job site)
		tamer.setVillagerXp(Math.max(1, tamer.getVillagerXp()));
		tamer.setPersistenceRequired();
		tamer.addTag(TAMER_TAG);
		tamer.setHomeTo(f.seat(), (int)TAMER_HOME);
		level.addFreshEntity(tamer);
		dragon.keeper = tamer.getUUID();
	}
}
