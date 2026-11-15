package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (gb-customs) The realms' ways: each land's own laws and manners, which its villagers and guards hold you to. Nothing
 * of it is told in chat; you learn it from how they look at you, what they say over their heads, and what it does to
 * your name there.
 * <ul>
 * <li>The north (snow and taiga realms) settles quarrels with fists: striking one of their townsfolk is a duel, not an
 * assault. Nobody else joins in and it's no crime; beat them (they back off hurt) and they think better of you
 * ({@link #duels}, {@link #yielded}).</li>
 * <li>The sands (desert and badlands realms) charge a toll on water: fill a bucket or a bottle in their land where their
 * people see and it costs {@link #TOLL} silver a day into the treasury, or it is water theft ({@link Theft.Crime#WATER}).</li>
 * <li>Blossom (cherry realms) bows on greeting: their people bow as you come up to them, and expect a bow back (sneak,
 * facing them). Bowing back pleases them; walking on doesn't ({@link #tick}).</li>
 * <li>The plains hold a market day every {@link #MARKET_EVERY} days (each realm on its own day): prices drop and the
 * criers call it in the streets ({@link #prices}).</li>
 * <li>And crimes differ: in Blossom the cherry trees are sacred, and felling one is a crime ({@link #sacred}).</li>
 * </ul>
 */
public final class Customs {
	/** A market day comes round every this many days in a plains realm. */
	static final int MARKET_EVERY = 7;
	/** On a market day its traders take this share off. */
	static final float MARKET_DISCOUNT = 0.15F;
	/** A crier calls the market to a player at most this often (ticks), with this chance each time, from this close. */
	static final long CRY_EVERY = 20L * 40;
	static final float CRY_CHANCE = 0.4F;
	static final double CRY_RANGE = 12.0;
	/** The sands' water toll, in silver, once a day per realm. */
	static final int TOLL = 1;
	/** Blossom's people greet a player from this close, one at a time, at most this often (ticks). */
	static final double GREET_RANGE = 4.0;
	static final long GREET_EVERY = 20L * 30;
	/** How long they hold their bow, and how long you have to bow back (ticks). */
	static final int BOW_TICKS = 30, ANSWER_TICKS = 20 * 4;
	/** You face them, bowing back, when your look is at least this close to them (a cosine). */
	static final double ANSWER_COS = 0.8;
	/** What a bow back, and a snub, do to your name there; at most this many of each count a day per realm. */
	static final int BOW_REP = 1, SNUB_REP = -1, BOW_DAILY = 3;
	/** A duel won in the north: this much more thought of you there, once a day per realm. */
	static final int DUEL_REP = 2;
	/** Lines each sign has. */
	static final int LINES = 3;
	static final double SPEECH_RANGE = 12.0;

	/** Faction id → its land. */
	private static final Map<Integer, Climate> CLIMATES = new HashMap<>();
	/** "player|faction|what" → the day it last counted (toll paid, a bow, a snub, a duel). */
	private static final Map<String, long[]> DAILY = new HashMap<>();
	/** Player → the Blossom greeting waiting on them. */
	private static final Map<UUID, Greeting> GREETINGS = new HashMap<>();
	/** Player → when they were last greeted, and last called to market. */
	private static final Map<UUID, Long> LAST_GREET = new HashMap<>(), LAST_CRY = new HashMap<>();
	/** "entity|player" → the day that one greeted that player (once a day each). */
	private static final Map<String, Long> GREETED = new HashMap<>();

	private record Greeting(UUID who, int faction, long bowUntil, long answerBy) {
	}

	private Customs() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Customs::tick);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
				drew(sp, server, player.getItemInHand(hand).is(Items.BUCKET) || player.getItemInHand(hand).is(Items.GLASS_BOTTLE));
			}
			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CLIMATES.clear();
			DAILY.clear();
			GREETINGS.clear();
			LAST_GREET.clear();
			LAST_CRY.clear();
			GREETED.clear();
		});
	}

	// ---- whose ways ---------------------------------------------------------------------------------------------

	static Climate climate(ServerLevel level, Faction f) {
		return BlossomVillages.blossom(level, f) ? Climate.CHERRY : CLIMATES.computeIfAbsent(f.id(), x -> Climate.at(level, f.seat()));
	}

	private static boolean north(ServerLevel level, Faction f) {
		Climate c = climate(level, f);
		return c == Climate.SNOWY || c == Climate.TAIGA;
	}

	private static boolean sands(ServerLevel level, Faction f) {
		Climate c = climate(level, f);
		return c == Climate.DESERT || c == Climate.BADLANDS;
	}

	/** {@code f}'s people settle a quarrel with fists: a fight with one is a duel, no crime and no crowd. */
	static boolean duels(ServerLevel level, @Nullable Faction f) {
		return f != null && f.type().isRealm() && north(level, f);
	}

	/** {@code f} holds its market today. */
	static boolean marketDay(ServerLevel level, Faction f) {
		return f.type().isRealm() && climate(level, f) == Climate.PLAINS && Math.floorMod(Patrons.today(level.getServer()) + f.id(), MARKET_EVERY) == 0;
	}

	/** Breaking {@code state} in {@code f}'s land is a crime there that it isn't elsewhere (a Blossom cherry tree). */
	static boolean sacred(ServerLevel level, Faction f, BlockState state) {
		return (state.is(net.minecraft.world.level.block.Blocks.CHERRY_LOG) || state.is(net.minecraft.world.level.block.Blocks.CHERRY_WOOD)) && climate(level, f) == Climate.CHERRY;
	}

	/** The first time today {@code what} counts for {@code player} in {@code f}, or at most {@code most} times: true while it still counts. */
	private static boolean countToday(MinecraftServer server, ServerPlayer player, Faction f, String what, int most) {
		long day = Patrons.today(server);
		long[] c = DAILY.computeIfAbsent(player.getUUID() + "|" + f.id() + "|" + what, k -> new long[] {day, 0});
		if (c[0] != day) {
			c[0] = day;
			c[1] = 0;
		}
		if (c[1] >= most) {
			return false;
		}
		c[1]++;
		return true;
	}

	private static boolean countedToday(MinecraftServer server, ServerPlayer player, Faction f, String what) {
		long[] c = DAILY.get(player.getUUID() + "|" + f.id() + "|" + what);
		return c != null && c[0] == Patrons.today(server) && c[1] > 0;
	}

	private static void say(ServerLevel level, LivingEntity who, String what, int colour) {
		Speech.say(level, who, Component.translatable("message.jaymoskingdoms.customs." + what + "." + level.getRandom().nextInt(LINES)), colour, SPEECH_RANGE);
	}

	// ---- the north's duels --------------------------------------------------------------------------------------

	/** (From {@link Retaliation}) a duel's opening: the one struck takes it up. */
	static void duelTaken(ServerLevel level, LivingEntity who) {
		say(level, who, "duel", Speech.YELLOW);
	}

	/** (From {@link Retaliation}) {@code who} backed off hurt from {@code player} in a duel: beaten fair, and they own it. */
	static void yielded(ServerPlayer player, ServerLevel level, @Nullable Faction f, LivingEntity who) {
		say(level, who, "yield", Speech.WHITE);
		if (f != null && countToday(level.getServer(), player, f, "duel", 1)) {
			Reputation.change(player, f, DUEL_REP);
		}
	}

	// ---- the sands' water toll ----------------------------------------------------------------------------------

	/** {@code player} used an empty bucket or bottle ({@code drawing}): at a source of water in the sands' land, the toll. */
	private static void drew(ServerPlayer player, ServerLevel level, boolean drawing) {
		if (!drawing || player.isCreative() || player.isSpectator() || level != Factions.level(level.getServer())) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
		if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
			return;
		}
		BlockPos pos = hit.getBlockPos();
		Faction f = Theft.lawAt(level, pos);
		if (f == null || !f.type().isRealm() || !sands(level, f) || Theft.exempt(player, f) || f.member(player.getUUID()) != null
			|| Wars.enemy(player, f) || countedToday(level.getServer(), player, f, "toll")) {
			// (its own people draw freely, and a paid toll holds all day)
			return;
		}
		List<LivingEntity> seen = Theft.crimeWitnesses(level, f, player);
		LivingEntity keeper = seen.stream().filter(e -> !(e instanceof IronGolem)).findFirst().orElse(null);
		if (keeper == null) {
			return;
		}
		if (Money.charge(player, f, Money.worth(TOLL))) {
			countToday(level.getServer(), player, f, "toll", 1);
			Factions.update(level.getServer(), f.id(), x -> x.withTreasury(x.treasury() + TOLL));
			say(level, keeper, "toll", Speech.GOLD);
			level.playSound(null, keeper.getX(), keeper.getY(), keeper.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		} else {
			Theft.commit(player, level, f, Theft.Crime.WATER, null, false, seen);
		}
	}

	// ---- the plains' market day ---------------------------------------------------------------------------------

	/** (From {@code VillagerPriceMixin}) on its realm's market day, a plains trader takes {@link #MARKET_DISCOUNT} off. */
	public static void prices(Villager villager, net.minecraft.world.entity.player.Player player) {
		if (!(villager.level() instanceof ServerLevel level) || level != Factions.level(level.getServer())) {
			return;
		}
		Faction f = Theft.lawAt(level, villager.blockPosition());
		if (f == null || !marketDay(level, f)) {
			return;
		}
		for (MerchantOffer offer : villager.getOffers()) {
			int off = Math.round(offer.getBaseCostA().getCount() * MARKET_DISCOUNT);
			if (off > 0) {
				offer.addToSpecialPriceDiff(-off);
			}
		}
	}

	// ---- the tick: Blossom's bows and the plains' criers --------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		for (Iterator<Map.Entry<UUID, Greeting>> it = GREETINGS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			if (answer(server, level, e.getKey(), e.getValue(), now)) {
				it.remove();
			}
		}
		if (now % 20 != 0) {
			return;
		}
		if (now % (20 * 60) == 0) {
			long day = Patrons.today(server);
			GREETED.values().removeIf(d -> d != day);
		}
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
				continue;
			}
			Faction f = Theft.lawAt(level, player.blockPosition());
			if (f == null || !f.type().isRealm() || Wars.enemy(player, f) || Theft.outlaw(player, f.id())) {
				continue;
			}
			Climate c = climate(level, f);
			if (c == Climate.CHERRY && !GREETINGS.containsKey(player.getUUID())) {
				greet(server, level, player, f, now);
			} else if (c == Climate.PLAINS && level.isBrightOutside() && marketDay(level, f)) {
				cry(level, player, now);
			}
		}
	}

	private static boolean ours(ServerLevel level, LivingEntity e, Faction f) {
		if (!e.isAlive() || e.isBaby() || e.isSleeping() || Retaliation.brawling(e) || Downed.isDowned(e)) {
			return false;
		}
		if (e instanceof Folk folk) {
			return folk.factionId() == f.id() && folk.getTarget() == null && !Orcs.isOrc(folk);
		}
		if (e instanceof Villager v) {
			Faction theirs = Theft.lawAt(level, v.blockPosition());
			return theirs != null && theirs.id() == f.id() && v.getTradingPlayer() == null;
		}
		return false;
	}

	/** One of a Blossom realm's people coming up close to {@code player} bows to them. */
	private static void greet(MinecraftServer server, ServerLevel level, ServerPlayer player, Faction f, long now) {
		Long last = LAST_GREET.get(player.getUUID());
		if (last != null && now - last < GREET_EVERY) {
			return;
		}
		long day = Patrons.today(server);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GREET_RANGE), e -> ours(level, e, f))) {
			String key = e.getUUID() + "|" + player.getUUID();
			if (e.distanceTo(player) > GREET_RANGE || !e.hasLineOfSight(player) || GREETED.getOrDefault(key, -1L) == day) {
				continue;
			}
			GREETED.put(key, day);
			LAST_GREET.put(player.getUUID(), now);
			GREETINGS.put(player.getUUID(), new Greeting(e.getUUID(), f.id(), now + BOW_TICKS, now + ANSWER_TICKS));
			say(level, e, "bow", Speech.WHITE);
			return;
		}
	}

	/** The greeting on {@code id} this tick: they hold their bow, and watch for one back. True once it's done with. */
	private static boolean answer(MinecraftServer server, ServerLevel level, UUID id, Greeting g, long now) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		Faction f = Factions.get(server, g.faction);
		if (player == null || f == null || player.level() != level || !(level.getEntity(g.who) instanceof Mob who) || !who.isAlive()) {
			return true;
		}
		if (now < g.bowUntil) {
			bow(who, player);
		}
		Vec3 to = who.getEyePosition().subtract(player.getEyePosition());
		boolean facing = to.lengthSqr() < (GREET_RANGE + 2) * (GREET_RANGE + 2) && player.getViewVector(1.0F).dot(to.normalize()) >= ANSWER_COS;
		if (player.isShiftKeyDown() && facing) {
			// bowed back: they're pleased
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, who.getX(), who.getY(1.0), who.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
			level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.1F);
			if (countToday(server, player, f, "bow", BOW_DAILY)) {
				Reputation.change(player, f, BOW_REP);
			}
			return true;
		}
		if (now >= g.answerBy) {
			// walked on without a bow: a snub
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, who.getX(), who.getY(1.0), who.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
			say(level, who, "snub", Speech.YELLOW);
			if (countToday(server, player, f, "snub", BOW_DAILY)) {
				Reputation.change(player, f, SNUB_REP);
			}
			return true;
		}
		return false;
	}

	/** {@code who} bows to {@code player}: stands still and bends its head to the ground between them. */
	private static void bow(Mob who, ServerPlayer player) {
		Vec3 d = player.position().subtract(who.position()).multiply(1.0, 0.0, 1.0);
		Vec3 at = who.position().add(d.lengthSqr() < 1.0E-4 ? Vec3.ZERO : d.normalize());
		BlockPos ground = BlockPos.containing(at.x, who.getY() - 0.6, at.z);
		if (who instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(ground));
		} else {
			who.getNavigation().stop();
		}
		who.getLookControl().setLookAt(at.x, who.getY() - 0.6, at.z);
	}

	/** A market crier near {@code player} calls the day's market. */
	private static void cry(ServerLevel level, ServerPlayer player, long now) {
		Long last = LAST_CRY.get(player.getUUID());
		if (last != null && now - last < CRY_EVERY) {
			return;
		}
		LAST_CRY.put(player.getUUID(), now);
		if (level.getRandom().nextFloat() >= CRY_CHANCE) {
			return;
		}
		Villager crier = null;
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(CRY_RANGE), v -> v.isAlive() && !v.isBaby() && !v.isSleeping()
			&& v.hasLineOfSight(player))) {
			if (crier == null || v.distanceToSqr(player) < crier.distanceToSqr(player)) {
				crier = v;
			}
		}
		if (crier != null) {
			say(level, crier, "market", Speech.GOLD);
		}
	}
}
