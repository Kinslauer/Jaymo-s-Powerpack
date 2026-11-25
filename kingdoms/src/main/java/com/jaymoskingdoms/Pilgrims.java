package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Village mods patch 5, Andy's pick "the wandering pilgrim") Now and then, out on the roads by day, a pilgrim of the old
 * temples crosses a traveller's way. Speak with one and the tale {@link #TALE} begins: the pilgrim names where the old
 * peoples' temples stand (lore, "The old age's peoples": the stepped tombs in the sands, the columned temples on the
 * plains, the long halls in the north, the walled palace in Blossom), only as hints, with no mark on any map, and asks
 * you to walk the old road: stand at the altar of four of them. The pilgrim walks on; the tale waits, and each old altar
 * you reach for the first time ({@link #ALTAR_REACH}, inside one of the old cities) is written in your journal
 * ({@link Tales#reached}). A pilgrim is gone again after {@link #LIFE}.
 */
public final class Pilgrims {
	// ===== the knobs ================================================================================================
	/** The tale a pilgrim begins. */
	static final String TALE = "the_pilgrims_road";
	/** How often a pilgrim may turn up (ticks between tries), the chance each try for each player out of town, and how long one stays. */
	static final int EVERY = 1200;
	static final float CHANCE = 0.03F;
	static final long LIFE = 24000L;
	/** The least time between pilgrims for one player (ticks). */
	static final long COOLDOWN = 24000L * 3;
	/** How far from a player a pilgrim turns up (blocks), and how far from any town the player must be. */
	static final int SPAWN_NEAR = 24, SPAWN_FAR = 40;
	static final double TOWN_RANGE = 96.0;
	/** How near an old altar counts as standing at it, and how often a walker is looked at (ticks). */
	static final int ALTAR_REACH = 6, CHECK_TICKS = 40;
	/** Pilgrims' given names. */
	static final String[] NAMES = {"Aldith", "Osric", "Wenna", "Tobin", "Hild", "Elric", "Marrow", "Ysolde"};
	// ================================================================================================================

	static final String TAG = "jaymoskingdoms.pilgrim";
	/** The old peoples' cities on the ground ({@code oldage/OldRuins}): structure id paths, each {@code <people>_ruins} (the Truth's house is in the Aether). */
	static final String[] PEOPLES = {"sunstep", "pillared", "mound", "court", "anvil", "deep", "grove"};

	/** The pilgrims about: who, and when they came. */
	private static final Map<UUID, Long> ABOUT = new HashMap<>();
	/** When each player last met one. */
	private static final Map<UUID, Long> MET = new HashMap<>();

	private Pilgrims() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			if (tick % CHECK_TICKS == 0) {
				walkers(server);
			}
			if (tick % EVERY == 0) {
				ServerLevel level = Factions.level(server);
				gone(level);
				come(level);
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ABOUT.clear();
			MET.clear();
		});
		// (a pilgrim left in the world when its chunk unloaded, or across a restart: its day is over, so it walks on)
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (is(entity) && !ABOUT.containsKey(entity.getUUID())) {
				ABOUT.put(entity.getUUID(), -LIFE);
			}
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !is(entity)
				|| !(entity instanceof Villager pilgrim) || !(world instanceof ServerLevel level)) {
				return InteractionResult.PASS;
			}
			if (!Tales.found(level, sp, TALE, pilgrim)) {
				Speech.tell(level, pilgrim, sp, Component.translatable("pilgrim.jaymoskingdoms.walk_well"), Speech.WHITE);
				return InteractionResult.PASS;
			}
			// (the pilgrim tells it at once, so the telling is never left waiting on one who has walked on)
			if (!(Tales.pending(sp, pilgrim) && Tales.talk(sp, pilgrim))) {
				Speech.tell(level, pilgrim, sp, Component.translatable("pilgrim.jaymoskingdoms.greet"), Speech.WHITE);
			}
			return InteractionResult.SUCCESS;
		});
	}

	static boolean is(@Nullable Entity e) {
		return e != null && e.entityTags().contains(TAG);
	}

	/** Pilgrims whose day is over walk out of the world (when no one is near enough to see it). */
	private static void gone(ServerLevel level) {
		long now = level.getGameTime();
		for (Iterator<Map.Entry<UUID, Long>> it = ABOUT.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Long> e = it.next();
			Entity pilgrim = level.getEntity(e.getKey());
			if (pilgrim == null) {
				// (its chunk is unloaded: it's dealt with when it loads again, ENTITY_LOAD above)
				continue;
			}
			if (!pilgrim.isAlive()) {
				it.remove();
			} else if (now - e.getValue() > LIFE && level.getNearestPlayer(pilgrim, SPAWN_NEAR) == null && !telling(level, pilgrim)) {
				McaCompat.vanish(pilgrim);
				it.remove();
			}
		}
	}

	/** Someone is still to hear {@code pilgrim} out (their run is at its first step, the telling): the pilgrim waits. */
	private static boolean telling(ServerLevel level, Entity pilgrim) {
		for (ServerPlayer player : level.players()) {
			if (Tales.atStep(player, TALE, pilgrim.getUUID(), "telling")) {
				return true;
			}
		}
		return false;
	}

	/** By day, now and then, a pilgrim near a player out on the road (one at a time near anyone). */
	private static void come(ServerLevel level) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		if (!level.isBrightOutside() || !ABOUT.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || !player.isAlive() || now - MET.getOrDefault(player.getUUID(), -COOLDOWN) < COOLDOWN
				|| random.nextFloat() >= CHANCE || Factions.around(server, level, player.blockPosition(), TOWN_RANGE) != null
				|| Tales.running(player, TALE)) {
				continue;
			}
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = SPAWN_NEAR + random.nextInt(SPAWN_FAR - SPAWN_NEAR);
			BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * r, player.getY(), player.getZ() + Math.sin(angle) * r);
			if (!Loaded.made(level, column)) {
				continue;
			}
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (!level.getFluidState(ground.below()).isEmpty()) {
				continue;
			}
			Villager pilgrim = McaCompat.visitor(level, Vec3.atBottomCenterOf(ground), null, false);
			if (pilgrim == null) {
				continue;
			}
			pilgrim.addTag(TAG);
			McaCompat.rename(pilgrim, NAMES[random.nextInt(NAMES.length)] + " the Pilgrim");
			ABOUT.put(pilgrim.getUUID(), now);
			MET.put(player.getUUID(), now);
			JaymosKingdoms.LOGGER.info("A pilgrim turns up near {}", player.getName().getString());
			return;
		}
	}

	/** Each player walking the pilgrim's road: at an old altar they haven't stood at, the tale notes it and goes on. */
	private static void walkers(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		for (ServerPlayer player : level.players()) {
			if (!Tales.running(player, TALE)) {
				continue;
			}
			String people = city(level, player.blockPosition());
			if (people != null && nearAltar(level, player.blockPosition())) {
				Tales.reached(player, TALE, "pilgrim_" + people);
			}
		}
	}

	/** The old people whose city {@code at} stands in, or null. */
	private static @Nullable String city(ServerLevel level, BlockPos at) {
		String[] found = new String[1];
		StructureStart start = level.structureManager().getStructureWithPieceAt(at, (Holder<Structure> h) -> {
			String people = h.unwrapKey().map(k -> people(k.identifier().getNamespace(), k.identifier().getPath())).orElse(null);
			if (people != null) {
				found[0] = people;
			}
			return people != null;
		});
		return start.isValid() ? found[0] : null;
	}

	private static @Nullable String people(String namespace, String path) {
		if (!namespace.equals(JaymosKingdoms.MOD_ID)) {
			return null;
		}
		for (String p : PEOPLES) {
			if (path.equals(p + "_ruins")) {
				return p;
			}
		}
		return null;
	}

	/** An old altar ({@code OldAge.ALTAR}) within {@link #ALTAR_REACH} of {@code at}. */
	private static boolean nearAltar(ServerLevel level, BlockPos at) {
		if (com.jaymoskingdoms.oldage.OldAge.ALTAR == null) {
			return false;
		}
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-ALTAR_REACH, -2, -ALTAR_REACH), at.offset(ALTAR_REACH, 2, ALTAR_REACH))) {
			if (level.getBlockState(p).is(com.jaymoskingdoms.oldage.OldAge.ALTAR)) {
				return true;
			}
		}
		return false;
	}
}
