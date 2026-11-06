package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import com.jaymoskingdoms.Captains.Doing;
import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.617) Rivals patch 2, <b>their schemes and yours</b> (ideas from Shadow of War, a commercial game: our own code, names
 * and lines).
 * <ul>
 * <li><b>Bodyguards:</b> each day a captain of rank {@link #GUARD_RANK} or more keeps one (two at the top rank) lesser captain
 * of his own posted beside him. A bodyguard keeps near his boss and takes up his fights. Turn one and, the next time you
 * fight his old boss, he comes and stabs him.</li>
 * <li><b>Spies:</b> send a sworn captain to <i>plant</i> himself in his old camp: each dawn he sends word of one more of its
 * captains' traits, and when you fight a captain there he turns on him. The other way about, a captain sworn to have your
 * head may plant himself among the villagers of a town you're in: no different from the rest until he strikes, on a
 * night you're there, with a short film. A Will staredown on him unmasks him first.</li>
 * <li><b>More ways to fight them:</b> sneak and use a spider eye or a poisonous potato at a camp with a feast set and the
 * feast is poisoned; a gold ingot at a camp with a duel set rigs it, so the loser lives and owes you (he tells you all he
 * is); and a captain killed quick, before he ever struck back, is pinned on another captain at his camp, whom his own
 * put to death.</li>
 * <li><b>Avenge a friend:</b> a captain who kills a player has a price on his head on the boards; whoever else kills him
 * is paid, and gets the friend's head back.</li>
 * </ul>
 */
public final class CaptainSchemes {
	// ================================================================================================================
	// Tuning
	/** Bodyguards: from this rank, one; at {@link Captains#RANK_MAX}, two. How far a bodyguard keeps from his boss. */
	static final int GUARD_RANK = 4;
	static final double GUARD_NEAR = 10.0, GUARD_REACH = 48.0;
	/** A turned bodyguard's or a plant's stab: this share of his old boss's health; how far a plant comes from. */
	static final float STAB_SHARE = 0.35F;
	static final int STAB_FILM = 70;
	/** Their plants: the chance each dawn a sworn foe plants himself in a town you're in, how near you; how near you must be at night for him to strike. */
	static final float PLANT_CHANCE = 0.12F;
	static final double PLANT_NEAR = 64.0, STRIKE_NEAR = 24.0;
	/** The chance each check (every {@link Captains#EVERY} ticks) a plant strikes, at night with you near. */
	static final float STRIKE_CHANCE = 0.02F;
	/** Schemes: how near a camp you must be to poison its feast or rig its duel. */
	static final double SCHEME_NEAR = 32.0;
	/** A quick kill: dead within this long (ticks) of your first blow, never having struck you. */
	static final long QUICK_KILL = 60L;
	/** Avenging a friend: the price on a captain's head (silver, in thousandths of its worth). */
	static final long PRICE = 12000L;
	static final int LINES = 2;
	// ================================================================================================================

	/** A plant among a town's folk: the captain's id, the villager he passes as, the town, and whose head he's after. */
	record Plant(String captain, UUID villager, int town, String player) {
		static final Codec<Plant> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("captain").forGetter(Plant::captain),
				net.minecraft.core.UUIDUtil.STRING_CODEC.fieldOf("villager").forGetter(Plant::villager),
				Codec.INT.fieldOf("town").forGetter(Plant::town),
				Codec.STRING.fieldOf("player").forGetter(Plant::player))
			.apply(i, Plant::new));
	}

	/** On the Overworld: each boss's bodyguards; bodyguards turned (guard id → boss id); enemy plants; prices on heads (captain → friend). Saved. */
	static final AttachmentType<Map<String, List<String>>> GUARDS = AttachmentRegistry.create(JaymosKingdoms.id("captains_guards"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())));
	static final AttachmentType<Map<String, String>> TURNED = AttachmentRegistry.create(JaymosKingdoms.id("captains_turned_guards"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
	static final AttachmentType<List<Plant>> PLANTS = AttachmentRegistry.create(JaymosKingdoms.id("captains_plants"),
		builder -> builder.persistent(Plant.CODEC.listOf()));
	static final AttachmentType<Map<String, String>> PRICES = AttachmentRegistry.create(JaymosKingdoms.id("captains_prices"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
	static final AttachmentType<Long> DAY = AttachmentRegistry.create(JaymosKingdoms.id("captains_schemes_day"), builder -> builder.persistent(Codec.LONG));

	/** Doings a player has set a hand to: the doing's captain a's id → the player. */
	static final String POISONED = "feast_poisoned", RIGGED = "duel_rigged";

	/** Captain id → player → when their fight began (game time), and whether he struck them. In memory. */
	private static final Map<String, Map<UUID, Long>> FIRST_BLOW = new HashMap<>();
	private static final Set<String> STRUCK_BACK = new HashSet<>();
	/** Captain id → the players whose inside men have already struck in this fight. */
	private static final Map<String, Set<UUID>> STABBED = new HashMap<>();
	/** Doings rigged, by captain a's id → the player who rigged it. */
	private static final Map<String, UUID> RIGGERS = new HashMap<>();

	private CaptainSchemes() {
	}

	static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer sp && player.isShiftKeyDown() && level instanceof ServerLevel server && scheme(server, sp, player.getItemInHand(hand))) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FIRST_BLOW.clear();
			STRUCK_BACK.clear();
			STABBED.clear();
			RIGGERS.clear();
		});
	}

	private static ServerLevel world(MinecraftServer server) {
		return Factions.level(server);
	}

	// ---- every so often --------------------------------------------------------------------------------------------------

	/** (From {@link Captains#tick}) bodyguards posted and plants set at dawn; plants about to strike. */
	static void tick(ServerLevel level) {
		MinecraftServer server = level.getServer();
		long day = level.getGameTime() / 24000L;
		Long last = level.getAttached(DAY);
		if (last == null || last < day) {
			level.setAttached(DAY, day);
			guards(server);
			plant(server, level.getRandom());
		}
		strikes(server);
	}

	/** Each boss's bodyguards, kept up: the fallen and the sworn struck off (the sworn remembered as turned), more chosen. */
	private static void guards(MinecraftServer server) {
		Map<String, List<String>> all = new HashMap<>(world(server).getAttachedOrElse(GUARDS, Map.of()));
		Map<String, String> turned = new HashMap<>(world(server).getAttachedOrElse(TURNED, Map.of()));
		Set<String> taken = new HashSet<>();
		for (Map.Entry<String, List<String>> e : List.copyOf(all.entrySet())) {
			Captain boss = Captains.get(server, e.getKey());
			List<String> kept = new ArrayList<>();
			for (String g : e.getValue()) {
				Captain guard = Captains.get(server, g);
				if (guard == null || guard.fallen()) {
					continue;
				}
				if (SwornCaptains.sworn(server, g)) {
					turned.put(g, e.getKey());
					continue;
				}
				kept.add(g);
			}
			if (boss == null || boss.fallen() || SwornCaptains.sworn(server, boss.id()) || kept.isEmpty()) {
				all.remove(e.getKey());
			} else {
				all.put(e.getKey(), List.copyOf(kept));
				taken.addAll(kept);
			}
		}
		for (Captain boss : List.copyOf(Captains.all(server).values())) {
			if (boss.fallen() || boss.rank() < GUARD_RANK || SwornCaptains.sworn(server, boss.id()) || taken.contains(boss.id())) {
				continue;
			}
			int want = boss.rank() >= Captains.RANK_MAX ? 2 : 1;
			List<String> have = new ArrayList<>(all.getOrDefault(boss.id(), List.of()));
			for (Captain g : Captains.of(server, boss.tribe())) {
				if (have.size() >= want) {
					break;
				}
				if (g.id().equals(boss.id()) || g.chief() || g.rank() >= boss.rank() || taken.contains(g.id()) || all.containsKey(g.id())
					|| SwornCaptains.sworn(server, g.id())) {
					continue;
				}
				have.add(g.id());
				taken.add(g.id());
				if (boss.post() >= 0 && g.post() != boss.post()) {
					Captains.put(server, g.withPost(boss.post(), false));
				}
				Post.news(server, Component.translatable("news.jaymoskingdoms.captain.bodyguard", g.name(), boss.name(), Rivals.name(boss.tribe())));
			}
			if (!have.isEmpty()) {
				all.put(boss.id(), List.copyOf(have));
			}
		}
		world(server).setAttached(GUARDS, Map.copyOf(all));
		world(server).setAttached(TURNED, Map.copyOf(turned));
	}

	/** The boss {@code guardId} keeps beside, or null. */
	static @Nullable String bossOf(MinecraftServer server, String guardId) {
		for (Map.Entry<String, List<String>> e : world(server).getAttachedOrElse(GUARDS, Map.of()).entrySet()) {
			if (e.getValue().contains(guardId)) {
				return e.getKey();
			}
		}
		return null;
	}

	/** (From {@link Captains#stepMob}) a bodyguard keeps near his boss and takes up his fight. */
	static void step(ServerLevel level, PathfinderMob mob, Captain c, @Nullable LivingEntity target) {
		if (mob.tickCount % 20 != 0) {
			return;
		}
		String bossId = bossOf(level.getServer(), c.id());
		Captain boss = bossId == null ? null : Captains.get(level.getServer(), bossId);
		Entity body = boss == null ? null : boss.body().map(level::getEntity).orElse(null);
		if (!(body instanceof Mob b) || !b.isAlive() || b.distanceTo(mob) > GUARD_REACH) {
			return;
		}
		LivingEntity his = Rulers.target(b);
		if (target == null && his != null && his.isAlive()) {
			mob.setTarget(his);
			Speech.say(level, mob, Dialects.tr(mob, "talk.jaymoskingdoms.captain.schemes.guard." + mob.getRandom().nextInt(LINES), Component.literal(boss.name())),
				Speech.RED, Captains.SAY_RANGE);
		} else if (target == null && mob.distanceTo(b) > GUARD_NEAR) {
			mob.getNavigation().moveTo(b, 1.0);
		}
	}

	// ---- inside men: a turned bodyguard, your plant ------------------------------------------------------------------------

	/** (From {@link Captains#hurt}) {@code player} struck captain {@code c}: their quick kill timed, and their inside men called. */
	static void struck(ServerLevel level, Captain c, LivingEntity body, ServerPlayer player) {
		FIRST_BLOW.computeIfAbsent(c.id(), k -> new HashMap<>()).putIfAbsent(player.getUUID(), level.getGameTime());
		if (!STABBED.computeIfAbsent(c.id(), k -> new HashSet<>()).add(player.getUUID())) {
			return;
		}
		MinecraftServer server = level.getServer();
		Map<String, String> turned = world(server).getAttachedOrElse(TURNED, Map.of());
		for (Map.Entry<String, SwornCaptains.Bond> e : SwornCaptains.bondsOf(server, player.getUUID()).entrySet()) {
			boolean guard = c.id().equals(turned.get(e.getKey()));
			boolean plant = "plant".equals(e.getValue().job()) && e.getValue().camp() == c.post() && c.post() >= 0;
			if (!guard && !plant) {
				continue;
			}
			Captain inside = Captains.get(server, e.getKey());
			if (inside == null || inside.fallen()) {
				continue;
			}
			LivingEntity knife = SwornCaptains.atSide(level, inside, player, body.position());
			if (knife == null) {
				continue;
			}
			// (he turns on his old boss: a blow to the back, a film, the boss's men see it)
			body.hurtServer(level, level.damageSources().mobAttack(knife), body.getMaxHealth() * STAB_SHARE);
			if (knife instanceof Mob m) {
				m.setTarget(body);
			}
			level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.3F, 0.7F);
			level.sendParticles(ParticleTypes.CRIT, body.getX(), body.getY(0.6), body.getZ(), 20, 0.3, 0.4, 0.3, 0.2);
			PowersCompat.film(knife, player, PowersCompat.FILM_SPEAKER, STAB_FILM, knife.getEyePosition(), body, Captains.FILM_WITNESSES);
			Speech.say(level, knife, Dialects.tr(knife, "talk.jaymoskingdoms.captain.schemes." + (guard ? "turned_guard." : "plant.") + level.getRandom().nextInt(LINES),
				Component.literal(c.name())), Speech.RED, Captains.SAY_RANGE);
			if (guard) {
				Map<String, String> left = new HashMap<>(turned);
				left.remove(e.getKey());
				world(server).setAttached(TURNED, Map.copyOf(left));
			}
			if (plant) {
				SwornCaptains.home(server, e.getKey());
			}
			Post.news(server, Component.translatable("news.jaymoskingdoms.captain.stabbed", inside.name(), c.name(), Rivals.name(c.tribe())));
			return;
		}
	}

	/** (From {@link Captains#hurt}) captain {@code c} struck {@code player}: no quick kill of him now. */
	static void struckBack(Captain c, ServerPlayer player) {
		STRUCK_BACK.add(c.id() + "/" + player.getUUID());
	}

	// ---- their plants -----------------------------------------------------------------------------------------------------

	/** Dawn: now and then a captain sworn to have a player's head plants himself among a town's folk near them. */
	private static void plant(MinecraftServer server, RandomSource random) {
		List<Plant> plants = new ArrayList<>(world(server).getAttachedOrElse(PLANTS, List.of()));
		plants.removeIf(p -> {
			Captain c = Captains.get(server, p.captain());
			return c == null || c.fallen() || SwornCaptains.sworn(server, p.captain());
		});
		Set<String> planted = new HashSet<>();
		Set<String> players = new HashSet<>();
		for (Plant p : plants) {
			planted.add(p.captain());
			players.add(p.player());
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (players.contains(player.getStringUUID()) || random.nextFloat() >= PLANT_CHANCE || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			Captain foe = null;
			for (String id : player.getAttachedOrElse(Captains.SWORN, List.of())) {
				Captain c = Captains.get(server, id);
				if (c != null && !c.fallen() && !c.chief() && !planted.contains(id) && Rivals.side(c.tribe()).camps && c.post() >= 0
					&& !SwornCaptains.sworn(server, id) && Captains.of(server, c.tribe()).size() > 1) {
					foe = c;
					break;
				}
			}
			if (foe == null) {
				continue;
			}
			List<Worker> folk = Workers.near(level, player.getBoundingBox().inflate(PLANT_NEAR), w -> w.mob().isAlive() && !Captains.is(w.mob()) && !Rulers.is(w.mob())
				&& town(server, w.factionId()));
			if (folk.isEmpty()) {
				continue;
			}
			Worker mask = folk.get(random.nextInt(folk.size()));
			Entity body = foe.body().map(level::getEntity).orElse(null);
			if (body != null) {
				body.discard();
			}
			Captains.put(server, foe.withBody(null));
			plants.add(new Plant(foe.id(), mask.mob().getUUID(), mask.factionId(), player.getStringUUID()));
			planted.add(foe.id());
		}
		world(server).setAttached(PLANTS, List.copyOf(plants));
	}

	private static boolean town(MinecraftServer server, int faction) {
		Faction f = Factions.get(server, faction);
		return f != null && !f.fallen() && f.type() != FactionType.BARBARIAN;
	}

	/** (From {@link Captains#tick}) captain {@code id} is planted in a town: he's not at his camp. */
	static boolean away(MinecraftServer server, String id) {
		for (Plant p : world(server).getAttachedOrElse(PLANTS, List.of())) {
			if (p.captain().equals(id)) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable Plant plantIn(MinecraftServer server, UUID villager) {
		for (Plant p : world(server).getAttachedOrElse(PLANTS, List.of())) {
			if (p.villager().equals(villager)) {
				return p;
			}
		}
		return null;
	}

	/** (From {@link Staredowns#great}) {@code mob} is a plant, to be stared down by the one he's after (or anyone). */
	public static boolean planted(Mob mob) {
		MinecraftServer server = mob.level().getServer();
		return server != null && plantIn(server, mob.getUUID()) != null;
	}

	/** At night, now and then, a plant with the one he's after near him strikes. */
	private static void strikes(MinecraftServer server) {
		List<Plant> plants = world(server).getAttachedOrElse(PLANTS, List.of());
		if (plants.isEmpty()) {
			return;
		}
		for (Plant p : List.copyOf(plants)) {
			ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(p.player()));
			if (player == null || !(player.level() instanceof ServerLevel level) || !level.isDarkOutside()) {
				continue;
			}
			Entity mask = level.getEntity(p.villager());
			if (!(mask instanceof LivingEntity villager) || !villager.isAlive()) {
				if (mask == null) {
					continue;
				}
				drop(server, p);
				continue;
			}
			if (villager.distanceTo(player) <= STRIKE_NEAR && level.getRandom().nextFloat() < STRIKE_CHANCE) {
				reveal(level, p, villager, player, false);
			}
		}
	}

	/** (From {@link Staredowns#broke}) {@code sp} stared {@code seen} down: if he's a plant, he's unmasked. True if he was. */
	public static boolean unmask(ServerLevel level, ServerPlayer sp, Mob seen) {
		Plant p = plantIn(level.getServer(), seen.getUUID());
		if (p == null) {
			return false;
		}
		reveal(level, p, seen, sp, true);
		return true;
	}

	/** {@code p} shows himself: the villager he passed as gone, and he there in his own body, after {@code player}; unmasked, he's shaken. */
	private static void reveal(ServerLevel level, Plant p, LivingEntity mask, ServerPlayer player, boolean unmasked) {
		MinecraftServer server = level.getServer();
		drop(server, p);
		Captain c = Captains.get(server, p.captain());
		Faction camp = c == null ? null : Factions.get(server, c.post());
		if (c == null || camp == null) {
			return;
		}
		BlockPos at = mask.blockPosition();
		level.sendParticles(ParticleTypes.POOF, mask.getX(), mask.getY() + 1.0, mask.getZ(), 16, 0.3, 0.6, 0.3, 0.02);
		PathfinderMob mob = Rivals.muster(level, c, camp, at, at, 4);
		if (mob == null) {
			return;
		}
		Captains.embodyIn(level, c, mob);
		mask.discard();
		mob.setTarget(player);
		if (unmasked) {
			mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 2));
			mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 100, 0));
		} else {
			PowersCompat.film(mob, player, PowersCompat.FILM_SPEAKER, STAB_FILM, mob.getEyePosition(), player, Captains.FILM_WITNESSES);
		}
		Speech.say(level, mob, Dialects.tr(mob, "talk.jaymoskingdoms.captain.schemes." + (unmasked ? "unmasked." : "ambush.") + level.getRandom().nextInt(LINES),
			Recollections.nameTo(player, mob)), Speech.RED, Captains.SAY_RANGE);
		Post.news(server, Component.translatable(unmasked ? "news.jaymoskingdoms.captain.plant.unmasked" : "news.jaymoskingdoms.captain.plant.struck",
			c.name(), Rivals.name(c.tribe()), player.getDisplayName()));
	}

	private static void drop(MinecraftServer server, Plant p) {
		List<Plant> left = new ArrayList<>(world(server).getAttachedOrElse(PLANTS, List.of()));
		left.remove(p);
		world(server).setAttached(PLANTS, List.copyOf(left));
	}

	// ---- your schemes: a poisoned feast, a rigged duel ------------------------------------------------------------------------

	/** Sneaking, {@code stack} used near a camp with a feast or a duel set: poisoned or rigged. True if it was. */
	private static boolean scheme(ServerLevel level, ServerPlayer player, ItemStack stack) {
		boolean poison = stack.is(Items.SPIDER_EYE) || stack.is(Items.POISONOUS_POTATO), gold = stack.is(Items.GOLD_INGOT);
		if (!poison && !gold) {
			return false;
		}
		MinecraftServer server = level.getServer();
		List<Doing> doings = new ArrayList<>(world(server).getAttachedOrElse(Captains.DOINGS, List.of()));
		for (int i = 0; i < doings.size(); i++) {
			Doing d = doings.get(i);
			boolean fits = poison ? "feast".equals(d.kind()) : "duel".equals(d.kind());
			Camps.CampData data = fits && d.camp() >= 0 ? Camps.data(server, d.camp()) : null;
			if (data == null || player.level() != world(server) || !(data.center().distToCenterSqr(player.position()) < SCHEME_NEAR * SCHEME_NEAR)) {
				continue;
			}
			doings.set(i, new Doing(poison ? POISONED : RIGGED, d.a(), d.b(), d.camp(), d.until()));
			world(server).setAttached(Captains.DOINGS, List.copyOf(doings));
			RIGGERS.put(d.a(), player.getUUID());
			stack.shrink(1);
			player.sendOverlayMessage(Component.translatable(poison ? "message.jaymoskingdoms.captain.feast_poisoned" : "message.jaymoskingdoms.captain.duel_rigged"));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.6F, 0.8F);
			return true;
		}
		return false;
	}

	/** (From {@link Captains#settle}) a doing a player set their hand to, at dawn. True if it was one. */
	static boolean settle(ServerLevel level, Doing d, Captain a, @Nullable Captain b) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		UUID by = RIGGERS.remove(d.a());
		ServerPlayer player = by == null ? null : server.getPlayerList().getPlayer(by);
		if (POISONED.equals(d.kind())) {
			Captains.fall(level, a, random.nextBoolean());
			Post.news(server, Component.translatable("news.jaymoskingdoms.captain.feast.poisoned", a.name(), Rivals.name(a.tribe())));
			return true;
		}
		if (RIGGED.equals(d.kind()) && b != null) {
			boolean aWins = random.nextBoolean();
			Captain won = aWins ? a : b, lost = aWins ? b : a;
			Captains.raise(level, won, random);
			if (player != null) {
				// (he lives, and owes you: he tells you all he is)
				for (Captains.Trait t : Captains.traits(lost)) {
					Captains.learn(player, lost.id(), t);
				}
				Post.report(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.captain.duel_owed", lost.name(), won.name()));
				Captains.outcome(server, lost.id(), player.getUUID(), "spared", level.getGameTime());
			}
			Post.news(server, Component.translatable("news.jaymoskingdoms.captain.duel.rigged", won.name(), lost.name()));
			return true;
		}
		return false;
	}

	// ---- deaths: a frame, a price paid ---------------------------------------------------------------------------------------

	/** (From {@link Captains#died}) {@code c} fell by {@code source}: a quick kill pinned on another; a price on his head paid. */
	static void died(ServerLevel level, Captain c, DamageSource source) {
		MinecraftServer server = level.getServer();
		STABBED.remove(c.id());
		Map<UUID, Long> first = FIRST_BLOW.remove(c.id());
		ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
		boolean struckBack = player != null && STRUCK_BACK.remove(c.id() + "/" + player.getUUID());
		STRUCK_BACK.removeIf(k -> k.startsWith(c.id() + "/"));
		if (player != null && !struckBack && first != null && first.containsKey(player.getUUID())
			&& level.getGameTime() - first.get(player.getUUID()) <= QUICK_KILL && c.post() >= 0) {
			frame(level, c);
		}
		Map<String, String> prices = new HashMap<>(world(server).getAttachedOrElse(PRICES, Map.of()));
		String friend = prices.remove(c.id());
		if (friend != null) {
			world(server).setAttached(PRICES, Map.copyOf(prices));
			if (player != null && !player.getStringUUID().equals(friend)) {
				for (ItemStack coins : Money.stacks(PRICE)) {
					give(player, coins);
				}
				if (!c.trophy().equals(friend)) {
					// (his head back, if the captain wasn't wearing it)
					ItemStack head = new ItemStack(Items.PLAYER_HEAD);
					try {
						head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(UUID.fromString(friend)));
						give(player, head);
					} catch (IllegalArgumentException ignored) {
						// (not a player's id)
					}
				}
				Component friendName = Post.nameOf(server, UUID.fromString(friend));
				Post.news(server, Component.translatable("news.jaymoskingdoms.captain.avenged", player.getDisplayName(), c.name(), friendName));
				Post.report(UUID.fromString(friend), Component.translatable("tidings.jaymoskingdoms.captain.avenged", player.getDisplayName(), c.name()));
			}
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.spawnAtLocation((ServerLevel)player.level(), stack);
		}
	}

	/** A quick kill at {@code c}'s camp: his own blame another captain posted there, and put him to death. */
	private static void frame(ServerLevel level, Captain c) {
		MinecraftServer server = level.getServer();
		List<Captain> there = Captains.of(server, c.tribe()).stream().filter(x -> !x.id().equals(c.id()) && !x.chief() && x.post() == c.post()
			&& !SwornCaptains.sworn(server, x.id())).toList();
		if (there.isEmpty()) {
			return;
		}
		Captain blamed = there.get(level.getRandom().nextInt(there.size()));
		Entity body = blamed.body().map(level::getEntity).orElse(null);
		if (body instanceof LivingEntity living && living.isAlive()) {
			Speech.say(level, living, Dialects.tr(living, "talk.jaymoskingdoms.captain.schemes.framed." + level.getRandom().nextInt(LINES)), Speech.RED, Captains.SAY_RANGE);
		}
		Captains.fall(level, blamed, false);
		Post.news(server, Component.translatable("news.jaymoskingdoms.captain.framed", blamed.name(), c.name(), Rivals.name(c.tribe())));
	}

	/** (From {@link Captains#slew}) {@code c} killed {@code player}: a price on his head, on the boards. */
	static void priced(MinecraftServer server, Captain c, ServerPlayer player) {
		if (server.getPlayerList().getPlayerCount() < 1) {
			return;
		}
		Map<String, String> prices = new HashMap<>(world(server).getAttachedOrElse(PRICES, Map.of()));
		if (prices.containsKey(c.id())) {
			return;
		}
		prices.put(c.id(), player.getStringUUID());
		world(server).setAttached(PRICES, Map.copyOf(prices));
		Post.news(server, Component.translatable("news.jaymoskingdoms.captain.price", c.name(), Rivals.name(c.tribe()), player.getDisplayName()));
	}
}
