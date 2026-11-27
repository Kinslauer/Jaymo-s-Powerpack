package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-26, a clip of a villager screaming "AAAAH!" just after a crook roughed her up for her purse): "what was
 * hurting this villager? after they exclaim like that you should be able to ask what's wrong or it should be something they
 * bring up before an introduction."
 *
 * <p>Why a villager last cried out is kept for {@link #KEEP_TICKS}: a crime they suffered or saw ({@link Crooks}), what last
 * hurt them (a blow, an arrow, fire, a fall, hunger...), a monster that set them running (MCA's panic scream comes with no
 * reason of its own), or a cry they raised ({@link Speech#say}). A more telling cause isn't overwritten by a lesser one
 * while it's fresh ({@link Rank}).
 *
 * <p>Opening their talk menu while it's fresh, they bring it up first, before any greeting ({@link #opening}, once per
 * player for each outcry). That line's key starts with {@link #OPENING}, so the talk wheel's reply spoke
 * ({@code Replies}) offers "What happened?", and they tell you what it was ({@link #answer}): who did it and what's become
 * of them, what bit them, what burned them.
 */
public final class Outcry {
	/** How long an outcry is remembered (ticks: three minutes). */
	static final long KEEP_TICKS = 20L * 60L * 3L;
	/** How often panicking villagers near players are looked at for a cause (ticks), and how far from a player (blocks). */
	static final int LOOK_EVERY = 10;
	static final double LOOK_RANGE = 48.0;
	/** The opening lines' key prefix (the reply spoke's topic), and the answers'. */
	public static final String OPENING = "outcry.jaymoskingdoms.opening.";
	static final String ANSWER = "outcry.jaymoskingdoms.answer.";
	static final String AFTER = "outcry.jaymoskingdoms.after.";
	/** The look given in place of words to one who shares no tongue with them. */
	static final String WORDLESS = "dialogue.chat.jaymoskingdoms.outcry_wordless";

	/** How telling a cause is: a fresh one is only replaced by one at least as telling. */
	enum Rank {
		/** A cry raised (a Kingdoms callout), with nothing better known. */
		CRIED,
		/** Panicking with no hurt: a monster close by, or nothing they could name. */
		SCARED,
		/** Hurt by something. */
		HURT,
		/** A crime done to them, or seen. */
		CRIME
	}

	/** Why {@code key} (lang: {@link #OPENING}/{@link #ANSWER} + key); {@code culprit} who did it, if anyone; {@code told} the players told. */
	private record Cause(String key, Object[] args, @Nullable UUID culprit, Rank rank, long at, Set<UUID> told) {
	}

	private static final Map<UUID, Cause> CAUSES = new HashMap<>();

	private Outcry() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && entity instanceof Villager v && McaCompat.isMca(v) && v.isAlive()) {
				hurt(v, source);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Outcry::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CAUSES.clear());
	}

	// ---- what they cried out about ------------------------------------------------------------------------------------------

	/** (From {@link Crooks}) {@code victim} was beaten for their purse by {@code crook}. */
	static void mugged(LivingEntity victim, LivingEntity crook) {
		if (victim instanceof Villager v) {
			note(v, "mugged", new Object[] {crook.getDisplayName()}, crook.getUUID(), Rank.CRIME);
		}
	}

	/** (From {@link Crooks}) {@code witness} saw {@code crook} at {@code crime} ("thief", "mugged", "arson"). */
	static void saw(LivingEntity witness, String crime, LivingEntity crook) {
		if (witness instanceof Villager v) {
			note(v, "saw_" + crime, new Object[] {crook.getDisplayName()}, crook.getUUID(), Rank.CRIME);
		}
	}

	/** (From {@link Speech#say}) {@code speaker} raised a cry ({@code words}, a {@code .cry.} line). */
	static void cried(Entity speaker, Component words) {
		if (speaker instanceof Villager v && McaCompat.isMca(v) && words.getContents() instanceof TranslatableContents t && t.getKey().contains(".cry.")) {
			note(v, "cried", new Object[] {words}, null, Rank.CRIED);
		}
	}

	/** {@code v} was hurt by {@code source}. */
	private static void hurt(Villager v, DamageSource source) {
		Entity by = source.getEntity();
		if (by instanceof LivingEntity who && who != v) {
			String key = who instanceof Player ? "hurt_by_player" : who instanceof Villager || who instanceof Folk ? "hurt_by_folk" : "hurt_by_beast";
			Component name = who instanceof Player ? who.getDisplayName() : who instanceof Villager || who instanceof Folk ? who.getDisplayName()
				: who.getType().getDescription();
			note(v, key, new Object[] {name}, who.getUUID(), Rank.HURT);
			return;
		}
		note(v, what(source), new Object[0], null, Rank.HURT);
	}

	/** What hurt them, when no one did. */
	private static String what(DamageSource source) {
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return "shot";
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return "burned";
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			return "fell";
		}
		if (source.is(DamageTypeTags.IS_DROWNING)) {
			return "drowning";
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return "blast";
		}
		if (source.is(DamageTypeTags.IS_FREEZING)) {
			return "freezing";
		}
		if (source.is(DamageTypeTags.IS_LIGHTNING)) {
			return "lightning";
		}
		if (source.is(DamageTypes.STARVE)) {
			return "starving";
		}
		if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)) {
			return "thorns";
		}
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)) {
			return "sick";
		}
		return "hurt";
	}

	/** Panicking villagers near players (MCA's scream): a monster in sight, or nothing they could name, when no cause is fresh. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			long now = level.getGameTime();
			for (ServerPlayer p : level.players()) {
				for (Villager v : level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(LOOK_RANGE), Villager::isAlive)) {
					Cause c = CAUSES.get(v.getUUID());
					if (c != null && now - c.at() <= KEEP_TICKS || !McaCompat.isMca(v) || !McaCompat.panicking(v)) {
						continue;
					}
					LivingEntity foe = v.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).orElse(null);
					if (foe != null) {
						note(v, "hostile", new Object[] {foe.getType().getDescription()}, foe.getUUID(), Rank.SCARED);
					} else {
						note(v, "spooked", new Object[0], null, Rank.SCARED);
					}
				}
			}
		}
		long now = server.overworld().getGameTime();
		CAUSES.values().removeIf(c -> now - c.at() > KEEP_TICKS);
	}

	private static void note(Villager v, String key, Object[] args, @Nullable UUID culprit, Rank rank) {
		long now = v.level().getGameTime();
		Cause old = CAUSES.get(v.getUUID());
		if (old != null && now - old.at() <= KEEP_TICKS && old.rank().compareTo(rank) > 0) {
			// (a fresher, lesser cause, say the bruise from the same mugging, doesn't hide the crime)
			return;
		}
		Set<UUID> told = old != null && old.key().equals(key) && now - old.at() <= KEEP_TICKS ? old.told() : new HashSet<>();
		CAUSES.put(v.getUUID(), new Cause(key, args, culprit, rank, now, told));
	}

	// ---- telling of it ------------------------------------------------------------------------------------------------------

	private static @Nullable Cause fresh(LivingEntity v) {
		Cause c = CAUSES.get(v.getUUID());
		return c == null || v.level().getGameTime() - c.at() > KEEP_TICKS ? null : c;
	}

	/** {@code v} is still shaken by something (they've no mind to remark on your clothes). */
	public static boolean upset(LivingEntity v) {
		return fresh(v) != null;
	}

	/** The key for {@code player}: being struck by the very one asking is another thing to say. */
	private static String keyFor(Cause c, ServerPlayer player) {
		return "hurt_by_player".equals(c.key()) && player.getUUID().equals(c.culprit()) ? "hurt_by_you" : c.key();
	}

	private static Object[] argsFor(Cause c, ServerPlayer player, ServerLevel level) {
		if ("hurt_by_player".equals(c.key()) && c.culprit() != null && level.getEntity(c.culprit()) instanceof Player other) {
			// (a player they've no name for is "a stranger")
			return new Object[] {Introductions.called(player, other)};
		}
		return c.args();
	}

	/**
	 * (MCA's menu opening, from {@link McaBridge#outcryOpening}) What {@code v} says first to {@code player} while an outcry
	 * is fresh, once per player for each outcry; null if nothing (or they've told this player already). Wordless if they
	 * share no tongue.
	 */
	public static @Nullable Component opening(LivingEntity v, ServerPlayer player, boolean understands) {
		Cause c = fresh(v);
		if (c == null || c.told().contains(player.getUUID())) {
			return null;
		}
		c.told().add(player.getUUID());
		if (!understands) {
			return Component.translatable(WORDLESS);
		}
		return Component.translatable(OPENING + keyFor(c, player), argsFor(c, player, (ServerLevel) v.level()));
	}

	/**
	 * (The reply spoke's "What happened?") What {@code v} tells {@code player} of it: what it was, and for a crime or a blow,
	 * what's become of the one who did it. Null if nothing's fresh.
	 */
	public static @Nullable Component answer(ServerPlayer player, LivingEntity v) {
		Cause c = fresh(v);
		if (c == null) {
			return null;
		}
		ServerLevel level = (ServerLevel) v.level();
		Component said = Component.translatable(ANSWER + keyFor(c, player), argsFor(c, player, level));
		String after = after(c, player, level);
		return after == null ? said : Component.empty().append(said).append(" ").append(Component.translatable(AFTER + after));
	}

	/** What's become of the culprit of a crime or a blow: taken by the guards, dead, or (a crime's) still loose; null if nothing to tell. */
	private static @Nullable String after(Cause c, ServerPlayer player, ServerLevel level) {
		if (c.culprit() == null || c.rank().compareTo(Rank.HURT) < 0 || player.getUUID().equals(c.culprit())) {
			return null;
		}
		Entity who = level.getEntity(c.culprit());
		if (who != null && Arrests.held(who)) {
			return "taken";
		}
		if (who != null && !who.isAlive()) {
			return "dead";
		}
		// (out of sight, or still about: either way not caught)
		return c.rank() == Rank.CRIME ? "loose" : null;
	}

	/** (Tests and the talk wheel) the cause {@code v} last cried out about, or null. */
	static @Nullable String cause(LivingEntity v) {
		Cause c = fresh(v);
		return c == null ? null : c.key();
	}
}
