package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.util.Prediction;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.61) Your deeds, and how a settlement's people take them. Put out a fire in a settlement, cut down whatever was
 * hurting one of its people, or bring one of them home from captivity ({@link Captives}), and they react there and then:
 * the one you saved thanks you (and now and then presses something on you), those who saw it cheer, their hearts warm
 * to you (MCA), and so does the settlement. After that they remember: for {@value #REMEMBERED_DAYS} days folk you pass
 * there call out to you about it, its gossips talk of it ({@link Approaches}), and word travels to other settlements,
 * whose people have heard what you did elsewhere.
 */
public final class Deeds {
	/** What was done. */
	enum Kind {
		FIRE, SAVE, RESCUE;

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Reputation each deed earns with the settlement: a fire put out, a life saved, a captive freed, and brought home. */
	static final int FIRE_REP = 1, SAVE_REP = 6, FREED_REP = 10;
	/** The most reputation fires and saves earn with one settlement in a day (so a blaze or a horde isn't a fortune). */
	static final int DAILY_REP = 60;
	/** Hearts (MCA): the one saved, the one freed, each who saw it, and each who saw a captive brought home. (A homecoming's own: {@link Captives}.) */
	static final int SAVE_HEARTS = 10, FREED_HEARTS = 10, WITNESS_HEARTS = 2, HOMECOMING_HEARTS = 5;
	/** MCA mood lift for those who saw it. */
	static final int CHEER = 5;
	/** How far off folk see a deed; how near a player must be to a fire that went out to have put it out; how near the one saved. */
	static final double WITNESS_RANGE = 16.0, HANDS_ON_RANGE = 6.0, SAVED_RANGE = 24.0;
	/** A fire that goes out this soon after a player near it used their hands (or a bucket) is theirs (ticks). */
	static final long HANDS_ON_TICKS = 60L;
	/** A blow on a villager is remembered this long (ticks): kill the one who dealt it in time and you saved them. */
	static final long HIT_MEMORY = 200L;
	/** Saving the same villager again counts only after this long; fire thanks come at most this often (ticks). */
	static final long SAVE_GAP = 1200L, FIRE_THANKS_GAP = 600L;
	/** The chance the one you saved hands you something. */
	static final float SAVE_GIFT_CHANCE = 0.3F;
	/** What they hand you. */
	static final List<Item> GIFTS = List.of(Items.EMERALD, Items.BREAD, Items.APPLE, Items.COOKIE, Items.BAKED_POTATO, Items.PUMPKIN_PIE,
		Items.HONEY_BOTTLE);
	/** How often folk may call out to someone they remember (ticks), and the least gap between two calls to one player. */
	static final int GREET_EVERY = 100;
	static final long GREET_GAP = 2400L;
	/** The chance, each time it may, that someone does call out; and how near they must be. */
	static final float GREET_CHANCE = 0.35F;
	static final double GREET_RANGE = 12.0;
	/** Days a settlement keeps talking of what you did. */
	static final long REMEMBERED_DAYS = 7L;
	/** Each day, the chance word of what you did in a settlement reaches one more; the deeds it must have seen first. */
	static final float SPREAD_CHANCE = 0.4F;
	static final int SPREAD_LEAST = 2;
	/** The chance a gossip who knows what you did talks of that rather than something else. */
	static final float GOSSIP_CHANCE = 0.5F;
	/** How much more a rescue, and a save, weighs than a fire when folk choose which to bring up. */
	static final int RESCUE_WEIGHT = 4, SAVE_WEIGHT = 2;

	/** What one settlement knows you did: fires put out, people saved, captives brought back; the day of the last; who told them (-1: they saw). */
	public record Deed(int fires, int saves, int rescues, long day, int heardFrom) {
		public static final Codec<Deed> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("fires", 0).forGetter(Deed::fires),
				Codec.INT.optionalFieldOf("saves", 0).forGetter(Deed::saves),
				Codec.INT.optionalFieldOf("rescues", 0).forGetter(Deed::rescues),
				Codec.LONG.optionalFieldOf("day", 0L).forGetter(Deed::day),
				Codec.INT.optionalFieldOf("heard_from", -1).forGetter(Deed::heardFrom))
			.apply(i, Deed::new));

		int total() {
			return fires + saves + rescues;
		}

		int count(Kind kind) {
			return switch (kind) {
				case FIRE -> fires;
				case SAVE -> saves;
				case RESCUE -> rescues;
			};
		}
	}

	/** Per player: settlement id → what it knows they did. Saved, kept through death. */
	static final AttachmentType<Map<String, Deed>> DEEDS = AttachmentRegistry.create(JaymosKingdoms.id("deeds"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Deed.CODEC)).copyOnDeath());

	private record Hit(UUID victim, long at) {
	}

	/** Players → the last tick they used their hands on a block or an item. */
	private static final Map<UUID, Long> HANDS_ON = new HashMap<>();
	/** Who struck a villager → whom, and when. */
	private static final Map<UUID, Hit> HITS = new HashMap<>();
	/** "player/villager" → when they were last saved by them; player → last fire thanks; player → last called out to. */
	private static final Map<String, Long> SAVED = new HashMap<>();
	private static final Map<UUID, Long> THANKED = new HashMap<>(), GREETED = new HashMap<>();
	/** "player/settlement/day" → reputation earned there that day from fires and saves. */
	private static final Map<String, Integer> GAINED = new HashMap<>();
	private static long lastDay = Long.MIN_VALUE;

	private Deeds() {
	}

	static void register() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> handsOn(player));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> handsOn(player));
		UseItemCallback.EVENT.register((player, level, hand) -> handsOn(player));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (!blocked && entity instanceof Villager && source.getEntity() instanceof LivingEntity attacker && attacker != entity
				&& !friendly(attacker)) {
				HITS.put(attacker.getUUID(), new Hit(entity.getUUID(), entity.level().getGameTime()));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			HITS.remove(entity.getUUID());
			if (source.getEntity() instanceof ServerPlayer player && player != entity && entity.level() instanceof ServerLevel level) {
				slain(level, player, entity);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Deeds::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HANDS_ON.clear();
			HITS.clear();
			SAVED.clear();
			THANKED.clear();
			GREETED.clear();
			GAINED.clear();
			lastDay = Long.MIN_VALUE;
		});
	}

	private static InteractionResult handsOn(Player player) {
		if (player instanceof ServerPlayer sp) {
			HANDS_ON.put(sp.getUUID(), sp.level().getGameTime());
		}
		return InteractionResult.PASS;
	}

	/** {@code who} is on the villagers' side (a villager, or a settlement's folk): their blows are a quarrel, not an attack to save anyone from. */
	static boolean friendly(LivingEntity who) {
		return who instanceof Villager && !Barbarians.isCampVillager(who) || who instanceof Folk folk && folk.style() != FactionType.BARBARIAN;
	}

	/** Today, as the settlements count days. */
	static long today(MinecraftServer server) {
		return Math.floorDiv(Factions.level(server).getOverworldClockTime(), 24000L);
	}

	// ---- fires ---------------------------------------------------------------------------------------------------

	/** A watched fire at {@code pos} went out, not by a villager's hand ({@link Firefighting}): a player who just used their hands near it put it out. */
	static void fireOut(ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		ServerPlayer by = null;
		double best = HANDS_ON_RANGE * HANDS_ON_RANGE;
		for (ServerPlayer p : level.players()) {
			Long at = HANDS_ON.get(p.getUUID());
			double d = p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			if (at != null && now - at <= HANDS_ON_TICKS && d <= best && !p.isSpectator()) {
				best = d;
				by = p;
			}
		}
		Faction f = settlement(level, pos, Firefighting.WATCHED);
		if (by == null || f == null) {
			return;
		}
		record(by, f, Kind.FIRE);
		gain(by, f, FIRE_REP);
		Long last = THANKED.get(by.getUUID());
		if (last != null && now - last < FIRE_THANKS_GAP) {
			return;
		}
		List<LivingEntity> seen = witnesses(level, by, null);
		if (seen.isEmpty()) {
			return;
		}
		THANKED.put(by.getUUID(), now);
		for (LivingEntity w : seen) {
			// (0.7.109) each who saw it remembers it
			Recollections.remember(w, by, Recollections.Kind.SAW_DEED, Component.translatable("recall.jaymoskingdoms.deed.fire"));
		}
		LivingEntity thanker = seen.get(level.getRandom().nextInt(seen.size()));
		thank(level, thanker, by, "talk.jaymoskingdoms.deed.fire.thanks", WITNESS_HEARTS);
		for (LivingEntity w : seen) {
			if (w != thanker) {
				warm(w, by, WITNESS_HEARTS);
			}
		}
	}

	// ---- saving ----------------------------------------------------------------------------------------------------

	/** {@code player} killed {@code dead}: if it had struck (or was going for) a villager, that villager was saved. */
	private static void slain(ServerLevel level, ServerPlayer player, LivingEntity dead) {
		Hit hit = HITS.remove(dead.getUUID());
		LivingEntity victim = null;
		if (hit != null && level.getGameTime() - hit.at() <= HIT_MEMORY && level.getEntity(hit.victim()) instanceof LivingEntity v) {
			victim = v;
		} else if (dead instanceof Mob mob && mob.getTarget() instanceof Villager v && !friendly(dead)) {
			victim = v;
		}
		if (victim instanceof Villager saved && saved.isAlive() && saved.distanceTo(player) <= SAVED_RANGE && !Captives.bound(saved)) {
			saved(level, player, saved);
		}
	}

	/** {@code player} saved {@code saved}'s life. */
	private static void saved(ServerLevel level, ServerPlayer player, Villager saved) {
		long now = level.getGameTime();
		String key = player.getUUID() + "/" + saved.getUUID();
		Long last = SAVED.get(key);
		if (last != null && now - last < SAVE_GAP) {
			return;
		}
		SAVED.put(key, now);
		thank(level, saved, player, "talk.jaymoskingdoms.deed.save.thanks", SAVE_HEARTS);
		// (0.7.109) and never forgets it
		Recollections.remember(saved, player, Recollections.Kind.SAVED, Component.empty());
		if (level.getRandom().nextFloat() < SAVE_GIFT_CHANCE) {
			give(player, new ItemStack(GIFTS.get(level.getRandom().nextInt(GIFTS.size()))));
			saved.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		cheer(level, player, saved, "talk.jaymoskingdoms.deed.save.cheer");
		Faction f = settlement(level, saved.blockPosition(), Villages.VILLAGE_RADIUS * 2.0);
		if (f != null) {
			record(player, f, Kind.SAVE);
			gain(player, f, SAVE_REP);
		}
	}

	// ---- rescues (Captives) ------------------------------------------------------------------------------------------

	/** {@code player} cut {@code captive} loose; {@code home} is where they were taken from, if anywhere. */
	static void freed(ServerLevel level, ServerPlayer player, Villager captive, @Nullable Faction home) {
		// (0.7.340) one taken from somewhere names it as they thank you: that's how you learn where to bring them
		if (home != null) {
			thank(level, captive, player, Component.translatable(Oddities.line("talk.jaymoskingdoms.deed.rescue.lead", level.getRandom()),
				Identities.shownName(player), home.name()), FREED_HEARTS);
		} else {
			thank(level, captive, player, "talk.jaymoskingdoms.deed.rescue.freed", FREED_HEARTS);
		}
		Recollections.remember(captive, player, Recollections.Kind.FREED, Component.empty());
		if (home != null) {
			Reputation.change(player, home, FREED_REP);
		}
	}

	/** {@code player} brought {@code captive} home to {@code home}: the whole place knows of it. */
	static void broughtHome(ServerLevel level, ServerPlayer player, Villager captive, Faction home, List<ItemStack> rewards, int rep, int hearts) {
		thank(level, captive, player, "talk.jaymoskingdoms.deed.rescue.home", hearts);
		// (0.7.109) they, and their family, remember it always
		Recollections.broughtHome(level, player, captive);
		for (ItemStack reward : rewards) {
			give(player, reward);
		}
		captive.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		cheer(level, player, captive, "talk.jaymoskingdoms.deed.rescue.cheer", HOMECOMING_HEARTS);
		record(player, home, Kind.RESCUE);
		Reputation.change(player, home, rep);
	}

	/** (0.7.100) Up to {@code most} grown villagers nearest {@code captive} come as their family and thank {@code player}. */
	static void family(ServerLevel level, ServerPlayer player, Villager captive, int most, double range, int hearts) {
		List<Villager> near = level.getEntitiesOfClass(Villager.class, captive.getBoundingBox().inflate(range), v -> v != captive && v.isAlive()
			&& !v.isBaby() && !v.isSleeping() && !v.isNoAi() && !v.hasAttached(Captives.CAPTIVE) && !Theft.rioting(v));
		// (their own kin first, if any live here; else the neighbours nearest, who're as good as family)
		near.sort(java.util.Comparator.<Villager>comparingInt(v -> McaCompat.kin(captive, v) == McaCompat.STRANGERS ? 1 : 0)
			.thenComparingDouble(v -> v.distanceToSqr(captive)));
		for (Villager kin : near.subList(0, Math.min(most, near.size()))) {
			thank(level, kin, player, "talk.jaymoskingdoms.deed.rescue.family", hearts);
		}
	}

	// ---- reactions ---------------------------------------------------------------------------------------------------

	/** {@code who} turns to {@code player}, thanks them in a line of {@code family}, and warms to them by {@code hearts}. */
	private static void thank(ServerLevel level, LivingEntity who, ServerPlayer player, String family, int hearts) {
		thank(level, who, player, Component.translatable(Oddities.line(family, level.getRandom()), Identities.shownName(player)), hearts);
	}

	private static void thank(ServerLevel level, LivingEntity who, ServerPlayer player, Component words, int hearts) {
		lookAt(who, player);
		Speech.tell(level, who, player, words, Speech.GOLD);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, who.getX(), who.getY() + who.getBbHeight() + 0.3, who.getZ(), 8, 0.4, 0.3, 0.4, 0.0);
		level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, who.isBaby() ? 1.5F : 1.0F);
		warm(who, player, hearts);
	}

	/** Everyone else who saw {@code player} help {@code helped} warms to them, and one cheers aloud. */
	private static void cheer(ServerLevel level, ServerPlayer player, LivingEntity helped, String family) {
		cheer(level, player, helped, family, WITNESS_HEARTS);
	}

	private static void cheer(ServerLevel level, ServerPlayer player, LivingEntity helped, String family, int hearts) {
		List<LivingEntity> seen = witnesses(level, player, helped);
		if (seen.isEmpty()) {
			return;
		}
		LivingEntity loud = seen.get(level.getRandom().nextInt(seen.size()));
		lookAt(loud, player);
		Speech.say(level, loud, Component.translatable(Oddities.line(family, level.getRandom()), Identities.shownName(player)), Speech.WHITE, WITNESS_RANGE + 8.0);
		Component deed = Component.translatable(family.contains(".rescue.") ? "recall.jaymoskingdoms.deed.rescue" : "recall.jaymoskingdoms.deed.save");
		for (LivingEntity w : seen) {
			warm(w, player, hearts);
			// (0.7.109) each who saw it remembers it
			Recollections.remember(w, player, Recollections.Kind.SAW_DEED, deed);
			if (w instanceof Villager v) {
				McaCompat.cheer(v, CHEER);
			}
		}
	}

	private static void warm(LivingEntity who, ServerPlayer player, int hearts) {
		McaCompat.hearts(who, player, hearts);
	}

	/** Grown villagers (and a settlement's folk) near {@code player} who can see them, but not {@code besides}, captives or rioters. */
	private static List<LivingEntity> witnesses(ServerLevel level, ServerPlayer player, @Nullable LivingEntity besides) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WITNESS_RANGE * Identities.hoodSight(player)),
			e -> e.isAlive() && e != besides && friendly(e))) {
			if (e.isSleeping() || e.isBaby() || e instanceof Mob m && m.isNoAi() || Theft.rioting(e) || !e.hasLineOfSight(player)) {
				continue;
			}
			out.add(e);
		}
		return out;
	}

	private static void lookAt(LivingEntity who, Entity at) {
		if (who instanceof Villager v) {
			v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(at, true));
		} else if (who instanceof Mob mob) {
			mob.getLookControl().setLookAt(at, 30.0F, 30.0F);
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!stack.isEmpty() && !player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	/** The settlement (not a camp) whose people live round {@code pos}, within {@code radius} of its seat. */
	private static @Nullable Faction settlement(ServerLevel level, BlockPos pos, double radius) {
		if (level != Factions.level(level.getServer())) {
			return null;
		}
		Faction f = Factions.around(level.getServer(), level, pos, radius);
		return f == null || f.fallen() || f.type() == FactionType.BARBARIAN ? null : f;
	}

	// ---- memory ------------------------------------------------------------------------------------------------------

	/** {@code f} saw {@code player} do {@code kind}. */
	private static void record(ServerPlayer player, Faction f, Kind kind) {
		// (0.7.419) road deeds: a fire put out, a life saved (anyone doing it opens a road for everyone, RoadLocks)
		if (kind == Kind.FIRE) {
			done(player.level().getServer(), "fire.doused");
		} else if (kind == Kind.SAVE) {
			done(player.level().getServer(), "life.saved");
		}
		if (Identities.hidden(player, f.id())) {
			// (0.7.62) a masked stranger did it: the costume's name is made by it (its reputation), not theirs
			return;
		}
		Map<String, Deed> all = new HashMap<>(player.getAttachedOrElse(DEEDS, Map.of()));
		String id = Integer.toString(f.id());
		Deed d = all.get(id);
		if (d == null || d.heardFrom() >= 0) {
			// (seen with their own eyes now, whatever they'd heard)
			d = d == null ? new Deed(0, 0, 0, 0L, -1) : new Deed(d.fires(), d.saves(), d.rescues(), d.day(), -1);
		}
		long day = today(player.level().getServer());
		all.put(id, new Deed(d.fires() + (kind == Kind.FIRE ? 1 : 0), d.saves() + (kind == Kind.SAVE ? 1 : 0),
			d.rescues() + (kind == Kind.RESCUE ? 1 : 0), day, -1));
		player.setAttached(DEEDS, Map.copyOf(all));
	}

	/** Reputation for a fire or a save, up to {@link #DAILY_REP} a day with each settlement. */
	private static void gain(ServerPlayer player, Faction f, int by) {
		String key = player.getUUID() + "/" + f.id() + "/" + today(player.level().getServer());
		int had = GAINED.getOrDefault(key, 0);
		int got = Math.min(by, DAILY_REP - had);
		if (got > 0) {
			GAINED.put(key, had + got);
			Reputation.change(player, f, got);
		}
	}

	/** What settlement {@code factionId} still talks of {@code player} doing, or null if nothing (or it's long forgotten). */
	static @Nullable Deed known(Player player, int factionId, long today) {
		Deed d = player.getAttachedOrElse(DEEDS, Map.of()).get(Integer.toString(factionId));
		return d == null || d.total() == 0 || today - d.day() > REMEMBERED_DAYS ? null : d;
	}

	/** Which of their deeds folk bring up: by chance, a rescue most of all, then a save. */
	private static Kind pick(Deed d, RandomSource random) {
		int fire = d.fires() > 0 ? 1 : 0, save = d.saves() > 0 ? SAVE_WEIGHT : 0, rescue = d.rescues() > 0 ? RESCUE_WEIGHT : 0;
		int roll = random.nextInt(Math.max(1, fire + save + rescue));
		return roll < rescue ? Kind.RESCUE : roll < rescue + save ? Kind.SAVE : Kind.FIRE;
	}

	/** The line a villager of {@code f} has about {@code player}'s deeds ({@code what}: greet or gossip), or null. */
	private static @Nullable Component line(ServerLevel level, ServerPlayer player, Faction f, String what) {
		Deed d = known(player, f.id(), today(level.getServer()));
		if (d == null) {
			return null;
		}
		Kind kind = pick(d, level.getRandom());
		Faction from = d.heardFrom() >= 0 ? Factions.get(level.getServer(), d.heardFrom()) : null;
		String family = what.equals("gossip") ? "talk.jaymoskingdoms.gossip.deed." + kind.key() : "talk.jaymoskingdoms.deed." + kind.key() + ".greet";
		if (from != null) {
			return Component.translatable(Oddities.line(family + ".heard", level.getRandom()), Identities.shownName(player), from.name());
		}
		return Component.translatable(Oddities.line(family, level.getRandom()), Identities.shownName(player));
	}

	/** (Approaches) A gossip of {@code f}'s talk about what {@code player} did, or null if they know of nothing (or don't feel like it). */
	static @Nullable Component gossip(ServerLevel level, ServerPlayer player, @Nullable Faction f) {
		if (f == null || level.getRandom().nextFloat() >= GOSSIP_CHANCE) {
			return null;
		}
		return line(level, player, f, "gossip");
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		long day = today(server);
		boolean newDay = lastDay != Long.MIN_VALUE && day != lastDay;
		lastDay = day;
		if (newDay) {
			GAINED.clear();
			HITS.values().removeIf(h -> now - h.at() > HIT_MEMORY);
			SAVED.values().removeIf(t -> now - t > SAVE_GAP);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				spread(server, player, day);
			}
		}
		if (now % GREET_EVERY != 0) {
			return;
		}
		HITS.values().removeIf(h -> now - h.at() > HIT_MEMORY);
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator()) {
				greet(level, player, now);
			}
		}
	}

	/** Someone who remembers what {@code player} did here calls out to them as they pass. */
	private static void greet(ServerLevel level, ServerPlayer player, long now) {
		Long last = GREETED.get(player.getUUID());
		if (last != null && now - last < GREET_GAP || level.getRandom().nextFloat() >= GREET_CHANCE) {
			return;
		}
		Faction f = Theft.lawAt(level, player.blockPosition());
		if (f == null || Identities.hidden(player, f.id()) || known(player, f.id(), today(level.getServer())) == null) {
			return;
		}
		List<LivingEntity> near = new ArrayList<>();
		for (LivingEntity e : witnesses(level, player, null)) {
			if (e.distanceTo(player) <= GREET_RANGE && !(e instanceof Mob m && m.getTarget() != null)) {
				near.add(e);
			}
		}
		if (near.isEmpty()) {
			return;
		}
		Component said = line(level, player, f, "greet");
		if (said == null) {
			return;
		}
		GREETED.put(player.getUUID(), now);
		LivingEntity who = near.get(level.getRandom().nextInt(near.size()));
		lookAt(who, player);
		who.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		Speech.tell(level, who, player, said, Speech.WHITE);
	}

	/** A new day: word of what {@code player} did in each settlement that saw enough of it may reach one more. */
	private static void spread(MinecraftServer server, ServerPlayer player, long day) {
		Map<String, Deed> all = player.getAttachedOrElse(DEEDS, Map.of());
		if (all.isEmpty()) {
			return;
		}
		RandomSource random = player.getRandom();
		Map<String, Deed> next = new HashMap<>(all);
		for (Map.Entry<String, Deed> e : all.entrySet()) {
			Deed d = e.getValue();
			Faction origin = Factions.get(server, Integer.parseInt(e.getKey()));
			if (origin == null || d.heardFrom() >= 0 || d.total() < SPREAD_LEAST || day - d.day() > REMEMBERED_DAYS || random.nextFloat() >= SPREAD_CHANCE) {
				continue;
			}
			// (to the nearest settlement that hasn't heard)
			Faction to = null;
			double best = Double.MAX_VALUE;
			for (Faction f : Factions.all(server)) {
				if (f.fallen() || f.type() == FactionType.BARBARIAN || f.id() == origin.id() || next.containsKey(Integer.toString(f.id()))) {
					continue;
				}
				double dist = f.seat().distSqr(origin.seat());
				if (dist < best) {
					best = dist;
					to = f;
				}
			}
			if (to != null) {
				next.put(Integer.toString(to.id()), new Deed(d.fires(), d.saves(), d.rescues(), day, origin.id()));
			}
		}
		if (next.size() != all.size()) {
			player.setAttached(DEEDS, Map.copyOf(next));
		}
	}

	// ---- road deeds (0.7.418) -------------------------------------------------------------------------------------------

	/** (0.7.418) Records road deed {@code id} as done, for the whole world ({@link RoadDeeds}). True if it is new. */
	public static boolean done(MinecraftServer server, String id) {
		return RoadDeeds.done(server, id);
	}

	/** (0.7.418) Whether road deed {@code id} has been done in this world ({@link RoadDeeds}). */
	public static boolean has(MinecraftServer server, String id) {
		return RoadDeeds.has(server, id);
	}
}
