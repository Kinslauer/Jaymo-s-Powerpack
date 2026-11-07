package com.jaymospowers.power.zfighter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.146) Andy (2026-10-28): "Mad tinkerer, Hermit, all z fighter npcs need MCA villager equivalents. it destroys
 * immersion seeing one of those things instead of a humanoid ... they should have context sensitive talk interactions
 * that handle the quest objectives instead of them appearing in chat like now."
 * <p>
 * With Jaymo's Kingdoms and MCA, every Z Fighter character (the Keeper, the Tower Hermit, the Mad Tinkerer, the Judge, the
 * Master of the Road, the Fortune Teller, the demon masters) is an MCA villager standing at their post (Kingdoms'
 * {@code ZFighterFolk}, {@link #villager}), and the frozen wandering traders of older worlds are swapped for one
 * ({@link #convert}). Right-clicking one opens MCA's menu, and "Talk about work" (or, for a town's master, "Talk about
 * training") opens their talk screen ({@link #talk}): what they have for you, as answers to pick. Whatever an answer
 * would have put in chat (the lesson learned, the parts still missing, the saga's next step) comes back on the screen as
 * their reply instead ({@link #reply}, caught by {@code ZFighterTranscriptMixin}), with a way back to their menu.
 * <p>
 * Without Kingdoms or MCA they're the old traders and the old right-clicks.
 */
public final class ZFighterNpcs {
	/** How often the old traders near players are looked for (ticks), and how near (blocks). */
	static final int CONVERT_EVERY = 100;
	static final double CONVERT_NEAR = 64.0;

	/** One answer on a talk screen: its words, whether it can be picked, and what picking it does. */
	public record Option(Component label, boolean enabled, Runnable pick) {
		public static Option of(String key, boolean enabled, Runnable pick, Object... args) {
			return new Option(Component.translatable(key, args), enabled, pick);
		}
	}

	private static final boolean MCA = FabricLoader.getInstance().isModLoaded("mca");
	private static @Nullable Method SPAWN, ASK;

	static {
		if (MCA && FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			try {
				Class<?> folk = Class.forName("com.jaymoskingdoms.ZFighterFolk");
				SPAWN = folk.getMethod("spawn", ServerLevel.class, Vec3.class, float.class, String.class, Component.class, boolean.class);
				ASK = folk.getMethod("ask", ServerPlayer.class, LivingEntity.class, Component.class, List.class, List.class, IntConsumer.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				SPAWN = null;
				ASK = null;
			}
		}
	}

	/** Whose chat is being caught for a reply, and what was caught. */
	private static @Nullable ServerPlayer listening;
	private static final List<Component> HEARD = new ArrayList<>();

	private ZFighterNpcs() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterNpcs::tick);
	}

	/** Whether the characters are MCA villagers here (Kingdoms and MCA, and a Kingdoms that knows them). */
	public static boolean villagers() {
		return SPAWN != null && ASK != null;
	}

	/** {@code entity}'s right-click is MCA's (its menu leads to the talk), not the old one. */
	static boolean viaMca(Entity entity) {
		return villagers() && entity instanceof Villager;
	}

	/**
	 * One of the characters, as an MCA villager at {@code at} facing {@code yaw}, tagged {@code tag} and named by
	 * {@code nameKey}; null without Kingdoms and MCA (the caller makes the old trader).
	 */
	public static @Nullable LivingEntity villager(ServerLevel level, Vec3 at, float yaw, String tag, String nameKey, boolean female) {
		if (!villagers()) {
			return null;
		}
		try {
			return (LivingEntity)SPAWN.invoke(null, level, at, yaw, tag, Component.translatable(nameKey), female);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** A woman plays this part ({@link ZFighterAfterlife#TELLER_TAG}); the rest are men. */
	static boolean female(String tag) {
		return ZFighterAfterlife.TELLER_TAG.equals(tag);
	}

	// ---------------------------------------------------------------- older worlds

	/** Every few seconds: the old frozen traders near a player become villagers, with their tags and their place. */
	private static void tick(MinecraftServer server) {
		if (!villagers() || server.overworld().getGameTime() % CONVERT_EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			List<WanderingTrader> old = new ArrayList<>();
			for (ServerPlayer player : level.players()) {
				for (WanderingTrader trader : level.getEntitiesOfClass(WanderingTrader.class, player.getBoundingBox().inflate(CONVERT_NEAR),
					e -> e.isAlive() && tag(e) != null)) {
					if (!old.contains(trader)) {
						old.add(trader);
					}
				}
			}
			for (WanderingTrader trader : old) {
				convert(level, trader);
			}
		}
	}

	/** {@code trader} made a villager in its place (its tags kept, the scientist's leaving time among them). */
	private static void convert(ServerLevel level, WanderingTrader trader) {
		String tag = tag(trader);
		String name = nameKey(trader, tag);
		LivingEntity villager = villager(level, trader.position(), trader.getYRot(), tag, name, female(tag));
		if (villager == null) {
			return;
		}
		for (String other : trader.entityTags()) {
			villager.addTag(other);
		}
		trader.discard();
	}

	/** The tag that makes {@code e} one of the characters, or null. */
	static @Nullable String tag(Entity e) {
		for (String tag : e.entityTags()) {
			switch (tag) {
				case ZFighterSites.KEEPER_TAG, ZFighterSites.HERMIT_TAG, ZFighterAndroid.TAG, ZFighterAfterlife.JUDGE_TAG, ZFighterAfterlife.MASTER_TAG,
					ZFighterAfterlife.TELLER_TAG, ZFighterDemonRealm.MASTER_TAG -> {
					return tag;
				}
				default -> {
				}
			}
		}
		return null;
	}

	/** The name an old trader was given (a demon master's tells his layer), as a lang key. */
	private static String nameKey(Entity e, String tag) {
		if (e.getCustomName() != null && e.getCustomName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
			return t.getKey();
		}
		return switch (tag) {
			case ZFighterSites.KEEPER_TAG -> "entity.jaymospowers.keeper";
			case ZFighterSites.HERMIT_TAG -> "entity.jaymospowers.hermit";
			case ZFighterAndroid.TAG -> "entity.jaymospowers.scientist";
			case ZFighterAfterlife.JUDGE_TAG -> "entity.jaymospowers.afterlife_judge";
			case ZFighterAfterlife.MASTER_TAG -> "entity.jaymospowers.afterlife_master";
			case ZFighterAfterlife.TELLER_TAG -> "entity.jaymospowers.fortune_teller";
			default -> "entity.jaymospowers.demon_master_1";
		};
	}

	// ---------------------------------------------------------------- talking

	/**
	 * Called by Kingdoms (reflection), from MCA's "Talk about work" or "Talk about training": {@code npc}'s talk screen for
	 * {@code player}. False when {@code npc} isn't one of the characters.
	 */
	public static boolean talk(ServerPlayer player, LivingEntity npc) {
		if (!npc.isAlive() || npc.level() != player.level()) {
			return false;
		}
		String tag = tag(npc);
		if (tag == null) {
			if (npc.entityTags().contains(com.jaymospowers.power.chrono.future.FutureOmen.PILOT_TAG)) {
				// (0.17.192) an Omen's pilot, in the future
				return com.jaymospowers.power.chrono.future.FutureOmenPilots.talk(player, npc);
			}
			if (npc.entityTags().contains(com.jaymospowers.power.xenoform.RivalHunter.TAG) || npc.entityTags().contains(com.jaymospowers.power.xenoform.RivalHunter.FLED_TAG)) {
				// (0.18.125) the band hunter
				com.jaymospowers.power.xenoform.RivalHunter.talk(player, npc);
				return true;
			}
			if (npc.entityTags().contains(com.jaymospowers.power.xenoform.BandForge.SMITH_TAG)) {
				// (0.18.123) the star forge's smith
				com.jaymospowers.power.xenoform.BandForge.talk(player, npc);
				return true;
			}
			if (npc.entityTags().contains(com.jaymospowers.power.xenoform.StarReaders.TAG)) {
				// (0.18.121) a star-reader, on a crater's rim
				com.jaymospowers.power.xenoform.StarReaders.talk(player, npc);
				return true;
			}
			if (npc.entityTags().contains(ZFighterPatrol.TAG)) {
				// (0.18.6) a galactic patrolman
				ZFighterPatrol.talk(player, npc);
				return true;
			}
			if (npc.entityTags().contains(ZFighterMasters.TAG) || npc.entityTags().contains(ZFighterMasters.DARK_TAG)) {
				ZFighterMasters.talk(player, npc);
				return true;
			}
			return false;
		}
		switch (tag) {
			case ZFighterSites.KEEPER_TAG -> ZFighterSites.keeperTalk(player, npc);
			case ZFighterSites.HERMIT_TAG -> ZFighterSites.hermitTalk(player, npc);
			case ZFighterAndroid.TAG -> ZFighterAndroid.talkMenu(player, npc);
			case ZFighterDemonRealm.MASTER_TAG -> ZFighterDemonRealm.talk(player, npc);
			default -> ZFighterAfterlife.talk(player, npc, tag);
		}
		return true;
	}

	/** (0.19.33) Whether the talk screen is there (Kingdoms and MCA), so a choice can be put to a player. */
	public static boolean canAsk() {
		return ASK != null;
	}

	/** {@code npc} says {@code words}, with these answers and a farewell. Without the talk screen, the words go to chat. */
	public static void ask(ServerPlayer player, LivingEntity npc, Component words, List<Option> options) {
		List<Option> all = new ArrayList<>(options);
		all.add(Option.of("talk.jaymospowers.zfighter.farewell", true, () -> {
		}));
		if (ASK == null) {
			player.sendSystemMessage(Component.translatable("talk.jaymospowers.zfighter.says", npc.getDisplayName(), words));
			return;
		}
		List<Component> labels = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		for (Option o : all) {
			labels.add(o.label());
			enabled.add(o.enabled());
		}
		IntConsumer pick = i -> {
			if (i >= 0 && i < all.size() && all.get(i).enabled()) {
				all.get(i).pick().run();
			}
		};
		try {
			ASK.invoke(null, player, npc, words, labels, enabled, pick);
		} catch (ReflectiveOperationException | RuntimeException e) {
			player.sendSystemMessage(Component.translatable("talk.jaymospowers.zfighter.says", npc.getDisplayName(), words));
		}
	}

	/**
	 * Does {@code action} for {@code player}, and shows what came of it (what it would have put in chat) as {@code npc}'s
	 * reply, with a way back to their menu ({@code menu}). If it said nothing (it opened a screen of its own: a town's
	 * errands), nothing more is shown.
	 */
	static void reply(ServerPlayer player, LivingEntity npc, Runnable action, Runnable menu) {
		List<Component> said = heard(player, action);
		if (said.isEmpty()) {
			return;
		}
		MutableComponent words = Component.empty();
		for (int i = 0; i < said.size(); i++) {
			if (i > 0) {
				words.append("\n");
			}
			words.append(said.get(i));
		}
		ask(player, npc, words, List.of(Option.of("talk.jaymospowers.zfighter.back", true, menu)));
	}

	/** Runs {@code action}, catching what it sends {@code player}'s chat instead of sending it. */
	static List<Component> heard(ServerPlayer player, Runnable action) {
		ServerPlayer was = listening;
		List<Component> before = new ArrayList<>(HEARD);
		listening = player;
		HEARD.clear();
		try {
			action.run();
			return new ArrayList<>(HEARD);
		} finally {
			listening = was;
			HEARD.clear();
			HEARD.addAll(before);
		}
	}

	/** From {@code ZFighterTranscriptMixin}: true if {@code message} to {@code player} was caught for a reply (not sent). */
	public static boolean noted(ServerPlayer player, Component message) {
		if (listening != player) {
			return false;
		}
		HEARD.add(message);
		return true;
	}

	/** A line in grey, for a reply where the speaker has nothing to add. */
	static Component aside(String key, Object... args) {
		return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
	}
}
