package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;

/**
 * (0.7.46) Festival days, after Village Life's festivals (remade, not copied: that mod is All Rights Reserved). Now and
 * then a realm at peace with food in store keeps a festival day: the bell rings at dawn and the word goes round, and
 * through the day its townsfolk free of work gather at the square to dance, feast from the storehouse, raise a toast and
 * listen to a musician; the children run about between them. At dusk the realm sets off fireworks in its colours. A
 * player who comes to the square that day is welcomed (reputation, once a festival), and the townsfolk's spirits lift.
 * <p>
 * A lord of the realm (or its player ruler) may call one with {@code /kingdom festival}: it's paid from the treasury
 * and held that day (the next if it's past noon). Ops: {@code /kingdom festival now}.
 */
final class Festivals {
	/** How often festivals are thought about (ticks); how far from the seat townsfolk are drawn in, and players welcomed. */
	static final int EVERY = 20;
	static final double DRAWN = 96.0, WELCOME = 24.0;
	/** A realm keeps a festival of its own about once in this many days (always on the same days for each realm). */
	static final int OWN_EVERY_DAYS = 8;
	/** (0.7.121) Kitchen's season length in days: each season opens with a festival. */
	static final int SEASON_DAYS = 21;
	/** When a festival day's gathering runs, by the time of day; the fireworks go up from dusk till this. */
	static final long FROM = 1000L, DUSK = 12000L, TILL = 13600L;
	/** What a called festival costs (emeralds from the treasury), and how many days must pass between called ones. */
	static final int CALL_COST = 32, CALL_GAP_DAYS = 3;
	/** The chance each think that a free villager joins in; how close is at the square; how far from the bell they mill. */
	static final float JOIN_CHANCE = 0.15F;
	static final int ARRIVE = 2, SPREAD = 6;
	static final float WALK = 0.6F, RUN = 0.8F;
	/** While at it: the chance each think of a line (heard this far), and of doing the thing. */
	static final float SAY_CHANCE = 0.02F, ACT_CHANCE = 0.35F;
	static final double SPEECH_RANGE = 12.0;
	/** How long each one joins in for (ticks) before they wander off (or join again). */
	static final int STAY_MIN = 600, STAY_MORE = 900, TRAVEL_TICKS = 900;
	/** The feast: food taken from the storehouse per festival, at most. */
	static final int FEAST_FOOD = 24;
	/** A player at the square: the reputation it earns them (once a festival); townsfolk's mood lift (MCA) when they join. */
	static final int WELCOME_REP = 5, CHEER = 4;
	/** Fireworks: the chance each think at dusk that one goes up, how far from the seat. */
	static final float FIREWORK_CHANCE = 0.25F;
	static final int FIREWORK_SPREAD = 10;
	/** Lines each part of a festival has (lang {@code festival.jaymoskingdoms.<part>.N}). */
	static final int LINES = 4;

	enum Part {
		DANCE, FEAST, TOAST, MUSIC, PLAY;

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Called festivals that are still to come or under way: realm id → the day it's held. */
	static final AttachmentType<Map<String, Long>> CALLED = AttachmentRegistry.create(JaymosKingdoms.id("festivals"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).initializer(HashMap::new));

	private static final class Going {
		final int realm;
		final Part part;
		BlockPos to;
		final long started;
		final int stay;

		Going(int realm, Part part, BlockPos to, long now, int stay) {
			this.realm = realm;
			this.part = part;
			this.to = to;
			this.started = now;
			this.stay = stay;
		}
	}

	private static final Map<UUID, Going> GOING = new HashMap<>();
	/** Per realm: the day it last announced a festival, fed its feast so far, and the players welcomed this festival. */
	private static final Map<Integer, Long> ANNOUNCED = new HashMap<>();
	private static final Map<Integer, Integer> FED = new HashMap<>();
	private static final Map<Integer, Set<UUID>> WELCOMED = new HashMap<>();
	/** Per realm: who plays music this festival. */
	private static final Map<Integer, UUID> MUSICIAN = new HashMap<>();
	/** Per realm: the day its own festival was weighed, and whether it's kept (1) or not (0). */
	private static final Map<Integer, long[]> OWN = new HashMap<>();

	private Festivals() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Festivals::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GOING.clear();
			ANNOUNCED.clear();
			FED.clear();
			WELCOMED.clear();
			MUSICIAN.clear();
			OWN.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("festival").executes(Festivals::call)
				.then(Commands.literal("now").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(Festivals::now)))));
	}

	/** {@code v} is at a festival just now. */
	static boolean going(Villager v) {
		return GOING.containsKey(v.getUUID());
	}

	/** (0.7.102) {@code v} is raising a toast at a festival just now (and sells drink: {@link Remedies}). */
	static boolean toasting(Villager v) {
		Going g = GOING.get(v.getUUID());
		return g != null && g.part == Part.TOAST;
	}

	static long day(ServerLevel level) {
		return Math.floorDiv(level.getOverworldClockTime(), Production.DAY_TICKS);
	}

	/** {@code f} keeps a festival on {@code day}: one it called, or one of its own (if at peace with food in store). */
	static boolean festivalDay(ServerLevel level, Faction f, long day) {
		Long called = level.getAttachedOrCreate(CALLED).get(Integer.toString(f.id()));
		if (called != null && called == day) {
			return true;
		}
		// (0.7.121) with Kitchen's seasons, every realm (fit to) keeps the first day of each season too
		int ofYear = KitchenCompat.dayOfYear(level);
		boolean seasonDay = ofYear >= 0 && ofYear % SEASON_DAYS == 0;
		if (!seasonDay && Math.floorMod(day + f.id() * 5L, OWN_EVERY_DAYS) != 0) {
			return false;
		}
		// (whether it's fit to keep its own is settled once, the first time the day's looked at)
		long[] own = OWN.get(f.id());
		if (own == null || own[0] != day) {
			boolean fit = !Economy.atWar(level.getServer(), f) && !Economy.underAttack(level.getServer(), f) && Economy.hasFood(level, f);
			own = new long[] {day, fit ? 1 : 0};
			OWN.put(f.id(), own);
		}
		return own[1] == 1;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		long t = Royals.timeOfDay(level);
		long day = day(level);
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			if (!festivalDay(level, f, day) || t < FROM || t >= TILL) {
				if (ANNOUNCED.remove(f.id()) != null) {
					// (it's over: everyone goes about their day)
					GOING.values().removeIf(g -> g.realm == f.id());
					FED.remove(f.id());
					WELCOMED.remove(f.id());
					MUSICIAN.remove(f.id());
				}
				continue;
			}
			if (ANNOUNCED.getOrDefault(f.id(), -1L) != day) {
				announce(level, f);
				ANNOUNCED.put(f.id(), day);
				FED.put(f.id(), 0);
				WELCOMED.put(f.id(), new HashSet<>());
				MUSICIAN.remove(f.id());
			}
			welcome(level, f);
			if (t >= DUSK) {
				fireworks(level, f);
			}
			List<Villager> folk = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(DRAWN), Villager::isAlive);
			for (Villager v : folk) {
				Going g = GOING.get(v.getUUID());
				if (g == null) {
					if (Routines.freeOf(v, Routines.WORK_TO) && !Routines.busy(v) && level.getRandom().nextFloat() < JOIN_CHANCE) {
						join(level, f, v, now);
					}
				} else if (!freeYet(v) || !step(level, f, v, g, now)) {
					GOING.remove(v.getUUID());
				}
			}
		}
		GOING.values().removeIf(g -> now - g.started > TRAVEL_TICKS + STAY_MIN + STAY_MORE + 200);
	}

	/** Still free to keep at it (a festival takes the day off work, so the hour isn't asked). */
	private static boolean freeYet(Villager v) {
		return Routines.freeOf(v, Routines.WORK_TO);
	}

	private static void announce(ServerLevel level, Faction f) {
		level.playSound(null, f.seat(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
		Component word = Component.translatable("message.jaymoskingdoms.festival", f.name()).withStyle(ChatFormatting.GOLD);
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(f.seat(), 160.0) || f.member(p.getUUID()) != null) {
				p.sendSystemMessage(word);
			}
		}
	}

	/** Players at the square are made welcome, once a festival. */
	private static void welcome(ServerLevel level, Faction f) {
		Set<UUID> welcomed = WELCOMED.computeIfAbsent(f.id(), k -> new HashSet<>());
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(f.seat()).inflate(WELCOME), p -> !p.isSpectator())) {
			if (welcomed.add(p.getUUID()) && Reputation.get(p, f.id()) >= 0) {
				Reputation.change(p, f, WELCOME_REP);
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.festival_welcome", f.name()).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private static void fireworks(ServerLevel level, Faction f) {
		RandomSource random = level.getRandom();
		if (random.nextFloat() >= FIREWORK_CHANCE) {
			return;
		}
		BlockPos at = Construction.standable(level, f.seat().offset(random.nextInt(FIREWORK_SPREAD * 2 + 1) - FIREWORK_SPREAD, 0,
			random.nextInt(FIREWORK_SPREAD * 2 + 1) - FIREWORK_SPREAD), 4);
		DyeColor second = DyeColor.values()[random.nextInt(DyeColor.values().length)];
		FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
		FireworkExplosion burst = new FireworkExplosion(shapes[random.nextInt(shapes.length)],
			IntList.of(f.color().getFireworkColor(), second.getFireworkColor()), IntList.of(DyeColor.WHITE.getFireworkColor()),
			random.nextBoolean(), random.nextBoolean());
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + random.nextInt(2), List.of(burst)));
		level.addFreshEntity(new FireworkRocketEntity(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, rocket));
	}

	/** {@code v} joins in: dancing, the feast, a toast, the music (one musician a festival) or, a child, running about. */
	private static void join(ServerLevel level, Faction f, Villager v, long now) {
		RandomSource random = level.getRandom();
		Part part;
		if (v.isBaby()) {
			part = Part.PLAY;
		} else {
			UUID musician = MUSICIAN.get(f.id());
			if (musician == null || !GOING.containsKey(musician)) {
				MUSICIAN.put(f.id(), v.getUUID());
				part = Part.MUSIC;
			} else {
				Part[] grown = {Part.DANCE, Part.DANCE, Part.FEAST, Part.FEAST, Part.TOAST};
				part = grown[random.nextInt(grown.length)];
			}
		}
		BlockPos spot = part == Part.MUSIC ? f.seat().offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1)
			: f.seat().offset(random.nextInt(SPREAD * 2 + 1) - SPREAD, 0, random.nextInt(SPREAD * 2 + 1) - SPREAD);
		Going g = new Going(f.id(), part, Construction.standable(level, spot, 3), now, STAY_MIN + random.nextInt(STAY_MORE));
		GOING.put(v.getUUID(), g);
		walk(v, g.to, v.isBaby() ? RUN : WALK);
		McaCompat.cheer(v, CHEER);
	}

	/** One think at the festival; false once they've had enough. */
	private static boolean step(ServerLevel level, Faction f, Villager v, Going g, long now) {
		if (now - g.started > g.stay + TRAVEL_TICKS) {
			return false;
		}
		RandomSource random = level.getRandom();
		if (!v.blockPosition().closerThan(g.to, ARRIVE)) {
			if (now - g.started > TRAVEL_TICKS) {
				return false;
			}
			if (v.getNavigation().isDone()) {
				walk(v, g.to, v.isBaby() ? RUN : WALK);
			}
			return true;
		}
		if (g.part == Part.PLAY) {
			// (the children dash about between the grown-ups)
			if (v.getNavigation().isDone()) {
				g.to = Construction.standable(level, f.seat().offset(random.nextInt(SPREAD * 2 + 1) - SPREAD, 0, random.nextInt(SPREAD * 2 + 1) - SPREAD), 3);
				walk(v, g.to, RUN);
			}
		}
		if (random.nextFloat() < ACT_CHANCE) {
			act(level, f, v, g, random);
		}
		if (random.nextFloat() < SAY_CHANCE) {
			Speech.say(level, v, Component.translatable("festival.jaymoskingdoms." + g.part.key() + "." + random.nextInt(LINES)), Speech.GOLD, SPEECH_RANGE);
		}
		return true;
	}

	private static void act(ServerLevel level, Faction f, Villager v, Going g, RandomSource random) {
		switch (g.part) {
			case DANCE -> {
				v.getJumpControl().jump();
				v.setYRot(v.getYRot() + 45.0F + random.nextInt(90));
				v.setYHeadRot(v.getYRot());
				if (random.nextInt(2) == 0) {
					level.sendParticles(ParticleTypes.NOTE, v.getX(), v.getY() + 2.2, v.getZ(), 1, 0.3, 0.0, 0.3, random.nextInt(24) / 24.0);
				}
				if (random.nextInt(5) == 0) {
					level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.5F, 1.0F);
				}
			}
			case FEAST -> {
				int fed = FED.getOrDefault(f.id(), 0);
				ItemStack food = ItemStack.EMPTY;
				if (fed < FEAST_FOOD) {
					food = Storage.take(level, f, Storage::isFood, 1);
					if (!food.isEmpty()) {
						FED.put(f.id(), fed + 1);
					}
				}
				// (once the storehouse's share is eaten, they make do with what they brought: bread)
				ItemStack shown = food.isEmpty() ? new ItemStack(Items.BREAD) : food;
				v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, shown.getItem()), v.getX(), v.getEyeY() - 0.2, v.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
				level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.5F, 1.0F);
			}
			case TOAST -> {
				v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getEyeY() + 0.4, v.getZ(), 3, 0.2, 0.1, 0.2, 0.0);
				if (random.nextInt(3) == 0) {
					level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL, 0.5F, 1.0F);
				}
			}
			case MUSIC -> {
				// (a tune on the flute, a note at a time, a little up and down the scale)
				v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				int note = random.nextInt(13);
				float pitch = (float)Math.pow(2.0, (note - 12) / 12.0) * 2.0F;
				level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.RECORDS, 1.0F, pitch);
				level.sendParticles(ParticleTypes.NOTE, v.getX(), v.getY() + 2.2, v.getZ(), 1, 0.0, 0.0, 0.0, note / 24.0);
			}
			case PLAY -> {
				if (random.nextInt(4) == 0) {
					level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.6F, 1.7F);
				}
			}
		}
	}

	private static void walk(Villager v, BlockPos to, float speed) {
		BehaviorUtils.setWalkAndLookTargetMemories(v, to, speed, 1);
	}

	// ---- calling one --------------------------------------------------------------------------------------------------

	/** {@code /kingdom festival}: a lord of the realm (or its ruler) calls a festival, paid from the treasury. */
	private static int call(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Faction f = Factions.of(server, player.getUUID());
		Faction.Member member = f == null ? null : f.member(player.getUUID());
		boolean ruler = f != null && f.leader().map(player.getUUID()::equals).orElse(false);
		if (f == null || !f.type().isRealm() || !ruler && (member == null || Rank.forStanding(member.standing()) != Rank.LORD)) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.festival_lords"));
			return 0;
		}
		ServerLevel level = Factions.level(server);
		long day = day(level) + (Royals.timeOfDay(level) >= 6000L ? 1 : 0);
		Map<String, Long> called = level.getAttachedOrCreate(CALLED);
		Long last = called.get(Integer.toString(f.id()));
		if (last != null && day - last < CALL_GAP_DAYS) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.festival_soon", CALL_GAP_DAYS));
			return 0;
		}
		if (Economy.atWar(server, f)) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.festival_war"));
			return 0;
		}
		if (f.treasury() < CALL_COST) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.festival_cost", CALL_COST));
			return 0;
		}
		Factions.update(server, f.id(), x -> x.withTreasury(x.treasury() - CALL_COST));
		hold(level, f, day);
		boolean today = day == day(level);
		c.getSource().sendSuccess(() -> Component.translatable(today ? "message.jaymoskingdoms.festival_called_today"
			: "message.jaymoskingdoms.festival_called", f.name(), CALL_COST), false);
		return 1;
	}

	/** {@code /kingdom festival now} (ops): the realm here keeps a festival today. */
	private static int now(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		ServerLevel level = Factions.level(server);
		Faction f = Factions.around(server, c.getSource().getLevel(), BlockPos.containing(c.getSource().getPosition()), 160.0);
		if (f == null || !f.type().isRealm()) {
			c.getSource().sendFailure(Component.literal("No realm here"));
			return 0;
		}
		hold(level, f, day(level));
		c.getSource().sendSuccess(() -> Component.literal(f.name() + " keeps a festival today"), true);
		return 1;
	}

	private static void hold(ServerLevel level, Faction f, long day) {
		Map<String, Long> called = new HashMap<>(level.getAttachedOrCreate(CALLED));
		called.put(Integer.toString(f.id()), day);
		level.setAttached(CALLED, called);
	}
}
