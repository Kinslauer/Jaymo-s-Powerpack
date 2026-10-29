package com.jaymospowers.power.chrono.future;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterNpcs;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.192) Andy: "The mech pilots should be very curious MCA villagers who are from the future so they are surprised you
 * are there. They will try their best to fill you in on what happened to the world, as you tell them your story."
 * <p>
 * An Omen's pilot ({@link FutureOmen#PILOT_TAG}) is an MCA villager when Jaymo's Kingdoms and MCA are here (the Z Fighter
 * characters' route, {@link ZFighterNpcs#villager}; a plain villager without them). Nobody from before is alive in the
 * future, so the first time a pilot sees a player they stop and stare, say so, and then come over and watch them a while
 * ({@link #tick}). "Talk about work" in MCA's menu opens their talk ({@link #talk}): the player tells their story (where
 * and when they came from; a Kingdoms life story's start or heritage is read if there is one), and the pilot answers with
 * what became of the world, one piece a talk ({@link #PIECES}, {@link #TELL_GAP} apart), kept on the pilot ({@link #TOLD}).
 * <p>
 * The lore is only ever spoken, in the pilot's own voice, never put as screen text or narration.
 */
public final class FutureOmenPilots {
	/** What a pilot has told or seen, as bits: the pieces of the world's story ({@link #PIECES}), the player's story, the first sight. */
	public static final AttachmentType<Integer> TOLD = AttachmentRegistry.create(JaymosPowers.id("omen_pilot_told"),
		builder -> builder.persistent(Codec.INT));
	static final int STORY_TOLD = 1 << 8, SURPRISED = 1 << 9;

	/** The pieces they tell, in order: the city's fall, the androids, the dead, the Omen program, the last stand, the Worky drones. */
	static final int PIECES = 6;
	/** How many wordings each line has (lang keys {@code talk.jaymospowers.omen.<name>.1} to {@code .<n>}). */
	static final int SURPRISE_LINES = 4, GREET_FIRST_LINES = 3, GREET_AGAIN_LINES = 3, PIECE_LINES = 2, REST_LINES = 3, DONE_LINES = 3,
		GENERIC_LINES = 3, START_LINES = 2, HERITAGE_LINES = 1;
	/** The life story starts and heritages with a reply of their own; any other gets the generic one. */
	static final Set<String> STARTS = Set.of("timelost", "skyfall", "village", "burned", "wild", "shipwreck", "cave", "city", "snowed"),
		HERITAGES = Set.of("emberborn", "frostborn", "stoneborn", "tideborn", "sylvan", "nightborn", "earthling");
	/** Their names (lang keys {@code entity.jaymospowers.omen_pilot_<n>}), and which of them are women. */
	static final int NAMES = 8;
	static final Set<Integer> WOMEN = Set.of(0, 2, 5, 7);

	/** A pilot checks for players this often (ticks), and notices them this far (blocks) with a clear line. */
	static final int CHECK_EVERY = 10;
	static final double NOTICE = 24.0;
	/** They stare this long first (ticks), then watch for this long, walking up to this close (blocks) at this pace. */
	static final int STARE_TICKS = 60, WATCH_TICKS = 1200;
	static final double WATCH_CLOSE = 3.0, WATCH_SPEED = 0.7;
	/** Once done watching, a pilot is curious again after this long (ticks). */
	static final int AGAIN_AFTER = 6000;
	/** A pilot tells the next piece no sooner than this after the last (ticks): a minute. */
	static final int TELL_GAP = 1200;
	/** A pilot with no player this far off is gone (checked every {@link #SWEEP_EVERY} ticks): the future keeps no strays. */
	static final double GONE_FAR = 128.0;
	static final int SWEEP_EVERY = 600;

	private static final int STARING = 0, WATCHING = 1, DONE = 2;

	/** What a pilot is doing about a player. */
	private static final class Watch {
		int phase;
		long until;

		Watch(int phase, long until) {
			this.phase = phase;
			this.until = until;
		}
	}

	private static final Map<UUID, Watch> WATCHES = new HashMap<>();
	/** When each pilot may tell the next piece. */
	private static final Map<UUID, Long> NEXT_TELL = new HashMap<>();

	private static @Nullable AttachmentType<?> storyType, heritageType;
	private static boolean lookedForStory;

	private FutureOmenPilots() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(FutureOmenPilots::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WATCHES.clear();
			NEXT_TELL.clear();
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			// a plain villager pilot (no MCA): its right-click is the talk, not a trade
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !pilot(entity) || entity.getType() != EntityTypes.VILLAGER) {
				return InteractionResult.PASS;
			}
			talk(sp, (LivingEntity)entity);
			return InteractionResult.SUCCESS;
		});
	}

	static boolean pilot(Entity e) {
		return e instanceof LivingEntity && e.entityTags().contains(FutureOmen.PILOT_TAG);
	}

	/** A pilot at {@code at}: an MCA villager if there is MCA (and Kingdoms), else a plain one. Added to the world; null if neither could be made. */
	static @Nullable LivingEntity spawn(ServerLevel level, Vec3 at, float yaw) {
		int name = level.getRandom().nextInt(NAMES);
		LivingEntity pilot = ZFighterNpcs.villager(level, at, yaw, FutureOmen.PILOT_TAG, "entity.jaymospowers.omen_pilot_" + name, WOMEN.contains(name));
		if (pilot != null) {
			return pilot;
		}
		Villager plain = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
		if (plain == null) {
			return null;
		}
		plain.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		plain.addTag(FutureOmen.PILOT_TAG);
		level.addFreshEntity(plain);
		return plain;
	}

	// ---------------------------------------------------------------- curiosity

	/**
	 * Every few seconds: a pilot with a player in sight stops and stares the first time ("nobody from before is alive"),
	 * then comes up to watch them, then goes back to what it was doing. Every {@link #SWEEP_EVERY} ticks pilots far from
	 * every player are let go.
	 */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		ServerLevel future = server.getLevel(Future.FUTURE);
		if (future == null || future.players().isEmpty()) {
			WATCHES.clear();
			return;
		}
		long now = future.getGameTime();
		List<Villager> pilots = new ArrayList<>();
		for (ServerPlayer player : future.players()) {
			for (Villager pilot : future.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NOTICE), e -> e.isAlive() && pilot(e))) {
				if (!pilots.contains(pilot)) {
					pilots.add(pilot);
				}
			}
		}
		for (Villager pilot : pilots) {
			Player near = future.getNearestPlayer(pilot, NOTICE);
			if (near instanceof ServerPlayer player && !player.isSpectator() && pilot.hasLineOfSight(player)) {
				watch(future, pilot, player, now);
			}
		}
		if (server.getTickCount() % SWEEP_EVERY == 0) {
			for (Villager pilot : future.getEntities(EntityTypeTest.forClass(Villager.class), e -> e.isAlive() && pilot(e))) {
				if (future.getNearestPlayer(pilot, GONE_FAR) == null) {
					WATCHES.remove(pilot.getUUID());
					NEXT_TELL.remove(pilot.getUUID());
					pilot.discard();
				}
			}
		}
	}

	private static void watch(ServerLevel level, Villager pilot, ServerPlayer player, long now) {
		Watch w = WATCHES.get(pilot.getUUID());
		if (w != null && w.phase == DONE && now >= w.until) {
			w = null;
		}
		if (w == null) {
			if (!seen(pilot, SURPRISED)) {
				mark(pilot, SURPRISED);
				say(player, pilot, line("surprise", SURPRISE_LINES, level));
				w = new Watch(STARING, now + STARE_TICKS);
			} else {
				w = new Watch(WATCHING, now + WATCH_TICKS);
			}
			WATCHES.put(pilot.getUUID(), w);
		}
		if (w.phase == DONE) {
			return;
		}
		pilot.getLookControl().setLookAt(player, 30.0F, 30.0F);
		if (w.phase == STARING) {
			pilot.getNavigation().stop();
			if (now >= w.until) {
				w.phase = WATCHING;
				w.until = now + WATCH_TICKS;
			}
			return;
		}
		if (pilot.distanceTo(player) > WATCH_CLOSE) {
			pilot.getNavigation().moveTo(player, WATCH_SPEED);
		} else {
			pilot.getNavigation().stop();
		}
		if (now >= w.until) {
			w.phase = DONE;
			w.until = now + AGAIN_AFTER;
		}
	}

	// ---------------------------------------------------------------- talking

	/**
	 * Called by {@code ZFighterNpcs.talk} ("Talk about work" in MCA's menu), or the right-click of a plain villager pilot:
	 * {@code npc}'s talk screen for {@code player}. False if {@code npc} isn't a pilot.
	 */
	public static boolean talk(ServerPlayer player, LivingEntity npc) {
		if (!pilot(npc) || !npc.isAlive() || npc.level() != player.level()) {
			return false;
		}
		ServerLevel level = (ServerLevel)npc.level();
		if (npc instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
		}
		WATCHES.put(npc.getUUID(), new Watch(DONE, level.getGameTime() + AGAIN_AFTER / 2));
		boolean surprised = seen(npc, SURPRISED);
		mark(npc, SURPRISED);
		if (!ZFighterNpcs.villagers() || npc.getType() == EntityTypes.VILLAGER) {
			// without the talk screen: each right-click, the pilot's next words
			Component words = !surprised ? line("surprise", SURPRISE_LINES, level) : !seen(npc, STORY_TOLD) ? hear(player, npc) : tell(npc, level);
			ZFighterNpcs.ask(player, npc, words, List.of());
			return true;
		}
		menu(player, npc, !surprised ? line("surprise", SURPRISE_LINES, level)
			: seen(npc, STORY_TOLD) ? line("greet.again", GREET_AGAIN_LINES, level) : line("greet.first", GREET_FIRST_LINES, level));
		return true;
	}

	/** {@code words} from the pilot, and the two things to say back. */
	private static void menu(ServerPlayer player, LivingEntity npc, Component words) {
		boolean heard = seen(npc, STORY_TOLD);
		ZFighterNpcs.ask(player, npc, words, List.of(
			ZFighterNpcs.Option.of("talk.jaymospowers.omen.opt.story", !heard, () -> menu(player, npc, hear(player, npc))),
			ZFighterNpcs.Option.of("talk.jaymospowers.omen.opt.ask", heard, () -> menu(player, npc, tell(npc, (ServerLevel)npc.level())))));
	}

	/** The player tells their story: the pilot's answer to it (their Kingdoms start or heritage, if they have one). */
	private static Component hear(ServerPlayer player, LivingEntity npc) {
		mark(npc, STORY_TOLD);
		ServerLevel level = (ServerLevel)npc.level();
		String key = storyKey(player);
		if (key.equals("generic")) {
			return line("story.generic", GENERIC_LINES, level);
		}
		return line("story." + key, key.startsWith("start.") ? START_LINES : HERITAGE_LINES, level);
	}

	/** The pilot tells the next piece of what became of the world, or says they've told what they can, or that they need a moment. */
	private static Component tell(LivingEntity npc, ServerLevel level) {
		int told = told(npc);
		int next = -1;
		for (int i = 0; i < PIECES; i++) {
			if ((told & (1 << i)) == 0) {
				next = i;
				break;
			}
		}
		if (next < 0) {
			return line("done", DONE_LINES, level);
		}
		long now = level.getGameTime();
		if (now < NEXT_TELL.getOrDefault(npc.getUUID(), 0L)) {
			return line("rest", REST_LINES, level);
		}
		NEXT_TELL.put(npc.getUUID(), now + TELL_GAP);
		mark(npc, 1 << next);
		return line("piece." + next, PIECE_LINES, level);
	}

	/** One of {@code count} wordings of {@code name}, in the pilot's voice. */
	private static Component line(String name, int count, ServerLevel level) {
		return Component.translatable("talk.jaymospowers.omen." + name + "." + (1 + level.getRandom().nextInt(count)));
	}

	private static void say(ServerPlayer player, LivingEntity npc, Component words) {
		player.sendSystemMessage(Component.translatable("talk.jaymospowers.zfighter.says", npc.getDisplayName(), words));
	}

	private static int told(LivingEntity npc) {
		return npc.getAttachedOrElse(TOLD, 0);
	}

	private static boolean seen(LivingEntity npc, int bit) {
		return (told(npc) & bit) != 0;
	}

	private static void mark(LivingEntity npc, int bit) {
		npc.setAttached(TOLD, told(npc) | bit);
	}

	// ---------------------------------------------------------------- the player's story (Jaymo's Kingdoms, if it's here)

	/**
	 * What of {@code player}'s Kingdoms life story is worth a reply: {@code "start.<key>"} for how they set out,
	 * {@code "heritage.<key>"} for their blood, else {@code "generic"}. Read by reflection; Kingdoms may not be here.
	 */
	@SuppressWarnings("unchecked")
	private static String storyKey(ServerPlayer player) {
		if (!lookedForStory) {
			lookedForStory = true;
			try {
				storyType = (AttachmentType<?>)Class.forName("com.jaymoskingdoms.LifeStory").getField("STORY").get(null);
				heritageType = (AttachmentType<?>)Class.forName("com.jaymoskingdoms.Heritage").getField("HERITAGE").get(null);
			} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
				storyType = null;
				heritageType = null;
			}
		}
		try {
			if (storyType != null) {
				List<String> story = (List<String>)player.getAttached(storyType);
				// the last answer is how they set out on the road
				if (story != null && !story.isEmpty() && STARTS.contains(story.get(story.size() - 1))) {
					return "start." + story.get(story.size() - 1);
				}
			}
			if (heritageType != null) {
				String heritage = (String)player.getAttached(heritageType);
				if (heritage != null && HERITAGES.contains(heritage)) {
					return "heritage." + heritage;
				}
			}
		} catch (ClassCastException e) {
			// a Kingdoms that keeps these differently: the generic reply
		}
		return "generic";
	}
}
