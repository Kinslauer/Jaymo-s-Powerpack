package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
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
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.433) Havens patch 4b (ideas from Shadow of War: our own names and code): captains challenged, turned and crowned.
 * <ul>
 * <li><b>Challenges and death threats.</b> Sneak and use a sheet of paper on anything near a goblin village or orc camp of
 * a tribe: a challenge is nailed up for its strongest captain there. Come back within a day and he steps out to fight you
 * alone while his own stand back ({@link #foe}). Beat him and he stays dead (unless he's raised), and the tribe respects
 * it; run or fall and he climbs. Never come and he calls you a coward on the post. Whenever a captain swears to have your
 * head (a coward, a turncoat, one back from the dead you killed), his death threat goes up on the town boards.</li>
 * <li><b>Turning captains.</b> A downed captain stared down with Valor's coating up (Tempered or better, his rank no more
 * than {@value #VALOR_REACH} past yours; never a Deathless one), or bound by Varro's Binding (Varro, level
 * {@code BINDING_LEVEL}), is sworn to you: he gets up, leaves his camp and follows you as MCA's followers do. Ask him in
 * talk to <b>spy</b> on his old tribe (back in {@value #SPY_DAYS} days with what he heard of its captains) or to <b>lead a
 * raid</b> on its nearest camp (settled at dawn: a rival captain falls and he brings back loot, or he falls). Proud and
 * spiteful ones may turn back on you at dawn ({@link #BETRAY_PROUD}, {@link #BETRAY_SPITEFUL}; half that if bound).</li>
 * <li><b>Taking a king's seat.</b> A tribe's king at its seat takes {@link #KING_SHIELD} of every blow while any of its
 * captains still stand (fall or turn them first). Kill him then with a sworn captain of his brood at your call, and that
 * captain is crowned in his place instead of the camp falling: the tribe is yours ({@link #rules}), no grudge, no hunts.</li>
 * </ul>
 */
public final class SwornCaptains {
	// ---- tuning ----------------------------------------------------------------------------------------------------
	/** Valor turns a captain at this rank (Tempered) or past it, if his rank is at most Valor's rank + {@link #VALOR_REACH}. */
	static final int VALOR_RANK = Valor.TEMPERED, VALOR_REACH = 2;
	/** How much the tribe's grudge with you rises when you turn one of its captains. */
	static final int TURN_GRUDGE = 20;
	/** The chance at each dawn that a proud or spiteful sworn captain turns back; a bound one ({@code binding}) this share of it. */
	static final float BETRAY_PROUD = 0.08F, BETRAY_SPITEFUL = 0.12F, BOUND_HOLDS = 0.5F;
	/** A following captain further than this from you (blocks) catches up. */
	static final double FOLLOW_FAR = 48.0;
	/** Spying: days away, and how many of the tribe's secrets he brings back at most. */
	static final int SPY_DAYS = 2, SPY_TELLS = 2;
	/** A raid's odds (even ranks), and how much each rank of difference moves them; its loot (emeralds). */
	static final float RAID_WINS = 0.55F, RAID_PER_RANK = 0.1F;
	static final int RAID_LOOT = 6, RAID_GRUDGE = 8;
	/** A king's share of each blow while his captains stand; how often he says so (ticks). */
	static final float KING_SHIELD = 0.2F;
	static final long KING_SAYS = 200L;
	/** A challenge: how near a camp it's nailed up, how near you must come to answer it, how far is running, how long it stands (ticks). */
	static final double CHALLENGE_REACH = 32.0, CHALLENGE_NEAR = 24.0, CHALLENGE_FLED = 56.0;
	static final long CHALLENGE_TICKS = 24000L;
	/** A challenge won: the grudge falls by this much (they respect it). */
	static final int CHALLENGE_GRUDGE = 15;
	/** How often this looks about (ticks). */
	static final int EVERY = 20;
	static final double SAY_RANGE = 24.0;
	static final int LINES = 3;

	/** A sworn captain's body. */
	static final String TAG = "jaymoskingdoms.captain.sworn";
	/** Talk answers to a sworn captain from his lord. */
	public static final String SPY_ANSWER = "jaymoskingdoms_sworn_spy", RAID_ANSWER = "jaymoskingdoms_sworn_raid";

	/**
	 * Whom a captain is sworn to (a player's id), how ({@code valor} or {@code binding}), his temperament then, what he's
	 * doing ({@code guard} at your side, {@code spy}, {@code raid}, or {@code king} on a seat), until when (game time, −1)
	 * and where (the raided camp, or his seat; −1).
	 */
	record Bond(String lord, String how, String temper, String job, long until, int camp) {
		static final Codec<Bond> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("lord").forGetter(Bond::lord),
				Codec.STRING.fieldOf("how").forGetter(Bond::how),
				Codec.STRING.optionalFieldOf("temper", "").forGetter(Bond::temper),
				Codec.STRING.optionalFieldOf("job", "guard").forGetter(Bond::job),
				Codec.LONG.optionalFieldOf("until", -1L).forGetter(Bond::until),
				Codec.INT.optionalFieldOf("camp", -1).forGetter(Bond::camp))
			.apply(i, Bond::new));

		Bond doing(String what, long when, int where) {
			return new Bond(lord, how, temper, what, when, where);
		}

		@Nullable UUID lordId() {
			try {
				return UUID.fromString(lord);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
	}

	/** A challenge nailed up: to which captain, by whom, at which camp, until when, and whether it's being fought. */
	record Challenge(String captain, String player, int camp, long until, boolean begun) {
		static final Codec<Challenge> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("captain").forGetter(Challenge::captain),
				Codec.STRING.fieldOf("player").forGetter(Challenge::player),
				Codec.INT.fieldOf("camp").forGetter(Challenge::camp),
				Codec.LONG.fieldOf("until").forGetter(Challenge::until),
				Codec.BOOL.optionalFieldOf("begun", false).forGetter(Challenge::begun))
			.apply(i, Challenge::new));
	}

	/** On the Overworld: every sworn captain's bond, by captain id. Saved. */
	static final AttachmentType<Map<String, Bond>> BONDS = AttachmentRegistry.create(JaymosKingdoms.id("captains_bonds"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Bond.CODEC)));
	/** On the Overworld: the tribes a player's sworn captain rules, tribe → player id. Saved. */
	static final AttachmentType<Map<String, String>> HELD = AttachmentRegistry.create(JaymosKingdoms.id("captains_held"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
	/** On the Overworld: the challenges nailed up. Saved. */
	static final AttachmentType<List<Challenge>> CHALLENGES = AttachmentRegistry.create(JaymosKingdoms.id("captains_challenges"),
		builder -> builder.persistent(Challenge.CODEC.listOf()));
	/** On the Overworld: the last day the bonds were seen to. Saved. */
	static final AttachmentType<Long> DAY = AttachmentRegistry.create(JaymosKingdoms.id("captains_bonds_day"), builder -> builder.persistent(Codec.LONG));

	/** How many looks in a row a following captain's body wasn't found where his lord is, by captain id. */
	private static final Map<String, Integer> MISSING = new HashMap<>();
	/** After this many looks ({@link #EVERY} ticks apart) a following captain left behind in unloaded land comes on after his lord. */
	static final int MISSING_LOOKS = 10;
	/** When a king last told someone his captains still stand, by his id. */
	private static final Map<UUID, Long> KING_SAID = new HashMap<>();

	private SwornCaptains() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == 0) {
				tick(Factions.level(server));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			KING_SAID.clear();
			MISSING.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel) {
				challengerFell(player);
			}
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()
				|| !sp.getMainHandItem().is(Items.PAPER) || !(world instanceof ServerLevel level)) {
				return InteractionResult.PASS;
			}
			return challenge(level, sp, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	// ---- who's sworn -------------------------------------------------------------------------------------------------

	private static Map<String, Bond> bonds(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(BONDS, Map.of());
	}

	static @Nullable Bond bond(MinecraftServer server, String id) {
		return bonds(server).get(id);
	}

	private static void put(MinecraftServer server, String id, @Nullable Bond b) {
		Map<String, Bond> all = new HashMap<>(bonds(server));
		if (b == null) {
			all.remove(id);
		} else {
			all.put(id, b);
		}
		Factions.level(server).setAttached(BONDS, Map.copyOf(all));
	}

	/** Captain {@code id} is sworn to a player (on a seat or not). */
	static boolean sworn(MinecraftServer server, String id) {
		return bonds(server).containsKey(id);
	}

	/** Captain {@code id} is sworn and off his tribe's ladder (at a player's side, or away on their errand; not seated). */
	static boolean free(MinecraftServer server, String id) {
		Bond b = bond(server, id);
		return b != null && !"king".equals(b.job());
	}

	/** {@code e} is a sworn captain's body (at his lord's side). */
	public static boolean free(Entity e) {
		return e.entityTags().contains(TAG);
	}

	/** The player {@code e} (a sworn captain's body) is sworn to, or null. */
	static @Nullable UUID lordOf(Entity e) {
		MinecraftServer server = e.level().getServer();
		String id = server == null || !Captains.is(e) ? null : Captains.idOf(e);
		Bond b = id == null ? null : bond(server, id);
		return b == null ? null : b.lordId();
	}

	/** {@code player} is the lord of {@code e} (a sworn captain): he never goes for them. */
	public static boolean spares(LivingEntity e, LivingEntity player) {
		return free(e) && player.getUUID().equals(lordOf(e));
	}

	/** {@code tribe} is ruled by a captain sworn to {@code player}. */
	public static boolean rules(MinecraftServer server, String tribe, UUID player) {
		return player.toString().equals(Factions.level(server).getAttachedOrElse(HELD, Map.of()).get(tribe));
	}

	private static void hold(MinecraftServer server, String tribe, @Nullable String lord) {
		Map<String, String> held = new HashMap<>(Factions.level(server).getAttachedOrElse(HELD, Map.of()));
		if (lord == null) {
			held.remove(tribe);
		} else {
			held.put(tribe, lord);
		}
		Factions.level(server).setAttached(HELD, Map.copyOf(held));
	}

	/**
	 * (From {@code McaCompat.foe}) who a sworn captain or a challenged camp goes for: a sworn captain for whoever is after his
	 * lord or whom his lord is after (never the lord); in a challenge being fought, the challenged captain for his challenger
	 * and the rest of the camp for no one but him. Null where it's none of this.
	 */
	public static @Nullable Boolean foe(LivingEntity guard, LivingEntity target) {
		MinecraftServer server = guard.level().getServer();
		if (server == null) {
			return null;
		}
		if (free(guard)) {
			UUID lord = lordOf(guard);
			if (lord == null) {
				return null;
			}
			if (lord.equals(target.getUUID()) || free(target) && lord.equals(lordOf(target))) {
				return false;
			}
			ServerPlayer p = server.getPlayerList().getPlayer(lord);
			if (p == null || p.level() != guard.level()) {
				return null;
			}
			if (target instanceof Mob m && m.getTarget() == p || p.getLastHurtByMob() == target || p.getLastHurtMob() == target) {
				return true;
			}
			return null;
		}
		List<Challenge> all = Factions.level(server).getAttachedOrElse(CHALLENGES, List.of());
		if (all.isEmpty() || !(guard instanceof Worker w)) {
			return null;
		}
		String id = Captains.is(guard) ? Captains.idOf(guard) : null;
		for (Challenge c : all) {
			if (!c.begun() || !target.getUUID().toString().equals(c.player())) {
				continue;
			}
			if (c.captain().equals(id)) {
				return true;
			}
			if (w.factionId() == c.camp()) {
				// (his own stand back and watch)
				return false;
			}
		}
		return null;
	}

	// ---- turning -----------------------------------------------------------------------------------------------------

	/**
	 * (From {@code Captains.stared}) {@code sp} stared downed captain {@code seen} down: with Valor's coating up and strong
	 * enough, he's turned (or refuses: no fear left in a Deathless one, or he's too great for it). True if Valor had a say.
	 */
	static boolean byValor(ServerLevel level, ServerPlayer sp, Mob seen, Captain c) {
		int valor = Valor.rank(sp);
		if (c.chief() || valor < VALOR_RANK || !Valor.reinforced(sp) && !Valor.armed(sp)) {
			return false;
		}
		RandomSource random = level.getRandom();
		if (c.has(Captains.Trait.DEATHLESS) || c.rank() > valor + VALOR_REACH) {
			String why = c.has(Captains.Trait.DEATHLESS) ? "deathless" : "unbent";
			Speech.say(level, seen, Component.translatable("talk.jaymoskingdoms.captain.refuses." + why + "." + random.nextInt(LINES),
				Recollections.nameTo(sp, seen)), Speech.RED, SAY_RANGE);
			level.playSound(null, seen.getX(), seen.getY(), seen.getZ(), SoundEvents.VILLAGER_NO, SoundSource.HOSTILE, 1.2F, 0.6F);
			return true;
		}
		turn(level, sp, seen, c, "valor");
		return true;
	}

	/** (Varro's Binding) {@code player} binds the downed captain {@code seen} to them. False if he's no downed captain, or a chief. */
	public static boolean bind(ServerLevel level, ServerPlayer player, LivingEntity seen) {
		Captain c = seen instanceof Mob m && Captains.starable(m) ? Captains.of(seen) : null;
		if (c == null || c.chief() || c.fallen() || sworn(level.getServer(), c.id())) {
			return false;
		}
		turn(level, player, seen, c, "binding");
		return true;
	}

	/** {@code c} ({@code body}) is sworn to {@code sp}: up, off his camp, at their side. */
	private static void turn(ServerLevel level, ServerPlayer sp, LivingEntity body, Captain c, String how) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		long now = level.getGameTime();
		Temperaments.Temperament t = Temperaments.of(body);
		put(server, c.id(), new Bond(sp.getStringUUID(), how, t == null ? "" : t.name(), "guard", -1L, -1));
		Captains.put(server, c.withPost(-1, false));
		Captains.outcome(server, c.id(), sp.getUUID(), "sworn", now);
		List<String> foes = new ArrayList<>(sp.getAttachedOrElse(Captains.SWORN, List.of()));
		if (foes.remove(c.id())) {
			sp.setAttached(Captains.SWORN, List.copyOf(foes));
		}
		body.addTag(Downed.REVIVE_TAG);
		if (McaCompat.swear(body, sp)) {
			body.addTag(TAG);
			if (body instanceof Mob m) {
				m.setTarget(null);
			}
			// (the fight that downed him is over: he forgets it)
			body.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
			body.setLastHurtByMob(null);
		} else {
			// (no MCA: he goes off about your business, and isn't seen again till he's sent for)
			level.sendParticles(ParticleTypes.POOF, body.getX(), body.getY() + 1.0, body.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			body.discard();
			Captains.put(server, c.withPost(-1, false).withBody(null));
			put(server, c.id(), new Bond(sp.getStringUUID(), how, t == null ? "" : t.name(), "spy", now + 24000L * SPY_DAYS, -1));
		}
		if ("binding".equals(how)) {
			level.sendParticles(ParticleTypes.ENCHANT, body.getX(), body.getY(0.6), body.getZ(), 40, 0.4, 0.6, 0.4, 0.6);
			level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.7F);
		} else {
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, body.getX(), body.getY(0.6), body.getZ(), 20, 0.4, 0.6, 0.4, 0.2);
			level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 0.6F);
		}
		Speech.say(level, body, Component.translatable("talk.jaymoskingdoms.captain.sworn." + how + "." + random.nextInt(LINES),
			Recollections.nameTo(sp, body)), Speech.RED, SAY_RANGE);
		Tribes.add(server, sp.getUUID(), c.tribe(), TURN_GRUDGE);
		Post.news(server, Component.translatable("news.jaymoskingdoms.captain.sworn", c.name(), Tribes.name(c.tribe()), sp.getDisplayName()));
	}

	// ---- at your side ------------------------------------------------------------------------------------------------

	/** (From {@code Workers.step}) a sworn captain's step at his lord's side: he follows and catches up. His brain goes on. */
	static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		MinecraftServer server = level.getServer();
		String id = Captains.idOf(mob);
		Bond b = id == null ? null : bond(server, id);
		if (b == null || "king".equals(b.job())) {
			// (his bond is ended: he's nobody's now, and his tribe's again when it next sees him)
			mob.removeTag(TAG);
			return false;
		}
		if (Captains.has(mob, Captains.Trait.DEATHLESS) && mob.tickCount % 8 == 0) {
			level.sendParticles(mob.getRandom().nextBoolean() ? ParticleTypes.SOUL : ParticleTypes.SMOKE, mob.getRandomX(0.6), mob.getRandomY(), mob.getRandomZ(0.6),
				1, 0.0, 0.03, 0.0, 0.01);
		}
		UUID lordId = b.lordId();
		ServerPlayer lord = lordId == null ? null : server.getPlayerList().getPlayer(lordId);
		if (lord == null || lord.level() != level || mob.tickCount % 20 != 0 || Downed.isDowned(mob)) {
			return false;
		}
		if (mob instanceof Villager v && !lord.getUUID().equals(McaCompat.following(v))) {
			McaCompat.follow(v, lord);
		}
		if (mob.distanceTo(lord) > FOLLOW_FAR && lord.onGround() && !lord.isSpectator()) {
			Vec3 at = beside(level, lord);
			mob.teleportTo(at.x, at.y, at.z);
			mob.getNavigation().stop();
		}
		return false;
	}

	private static Vec3 beside(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		BlockPos near = player.blockPosition().offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near);
		return Vec3.atBottomCenterOf(Math.abs(top.getY() - player.getBlockY()) <= 3 ? top : player.blockPosition());
	}

	/** (Talk) {@code player} may send {@code villager} spying or raiding: he's sworn to them, at their side. */
	public static boolean takesOrders(ServerPlayer player, LivingEntity villager) {
		return free(villager) && player.getUUID().equals(lordOf(villager));
	}

	/** (Talk) {@code player} sends {@code villager}, a sworn captain, off: {@code answer} is spying or raiding. What he says. */
	public static Component order(ServerPlayer player, LivingEntity villager, String answer) {
		ServerLevel level = (ServerLevel)villager.level();
		MinecraftServer server = level.getServer();
		Captain c = Captains.of(villager);
		Bond b = c == null ? null : bond(server, c.id());
		RandomSource random = level.getRandom();
		if (c == null || b == null) {
			return Component.translatable("talk.jaymoskingdoms.captain.order.no");
		}
		long now = level.getGameTime();
		if (RAID_ANSWER.equals(answer)) {
			Faction camp = nearestCamp(server, c.tribe(), villager.position());
			if (camp == null) {
				return Component.translatable("talk.jaymoskingdoms.captain.order.nothing", Recollections.nameTo(player, villager));
			}
			put(server, c.id(), b.doing("raid", (now / 24000L + 1) * 24000L, camp.id()));
			Post.news(server, Component.translatable("news.jaymoskingdoms.captain.raid.set", c.name(), Component.literal(camp.name()), player.getDisplayName()));
			leave(level, c, villager);
			return Component.translatable("talk.jaymoskingdoms.captain.order.raid." + random.nextInt(LINES), Recollections.nameTo(player, villager),
				Component.literal(camp.name()));
		}
		put(server, c.id(), b.doing("spy", now + 24000L * SPY_DAYS, -1));
		leave(level, c, villager);
		return Component.translatable("talk.jaymoskingdoms.captain.order.spy." + random.nextInt(LINES), Recollections.nameTo(player, villager));
	}

	/** He's off: his body gone from the world till he's back. */
	private static void leave(ServerLevel level, Captain c, LivingEntity body) {
		level.sendParticles(ParticleTypes.POOF, body.getX(), body.getY() + 1.0, body.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		body.discard();
		Captains.put(level.getServer(), c.withBody(null));
	}

	private static @Nullable Faction nearestCamp(MinecraftServer server, String tribe, Vec3 from) {
		Faction best = null;
		for (Faction f : Tribes.camps(server, tribe)) {
			if (best == null || f.seat().distToCenterSqr(from) < best.seat().distToCenterSqr(from)) {
				best = f;
			}
		}
		return best;
	}

	/**
	 * Whether {@code c}, following {@code lord}, has no body by them: none at all (back from spying or a raid), or his body
	 * left behind where the world isn't loaded (another dimension, far off) for {@link #MISSING_LOOKS} looks. A body left
	 * behind that loads again later gives way to the new one ({@code Captains}' load check).
	 */
	private static boolean missing(ServerPlayer lord, Captain c) {
		if (c.body().isEmpty()) {
			MISSING.remove(c.id());
			return true;
		}
		Entity body = ((ServerLevel)lord.level()).getEntity(c.body().get());
		if (body != null && body.isAlive()) {
			MISSING.remove(c.id());
			return false;
		}
		int looks = MISSING.merge(c.id(), 1, Integer::sum);
		if (looks < MISSING_LOOKS) {
			return false;
		}
		MISSING.remove(c.id());
		return true;
	}

	/** His body back at {@code lord}'s side (from spying or a raid, or left behind). */
	private static void summon(ServerLevel level, Captain c, ServerPlayer lord) {
		boolean orcs = c.tribe().startsWith(Tribes.ORC + "@");
		List<ItemStack> gear = new ArrayList<>();
		gear.add(new ItemStack(orcs ? c.rank() >= 3 ? Items.IRON_AXE : Items.STONE_AXE : c.rank() >= 3 ? Items.IRON_SWORD : Items.STONE_SWORD));
		gear.add(c.rank() >= 2 ? new ItemStack(c.rank() >= 4 ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET) : ItemStack.EMPTY);
		gear.add(c.rank() >= 2 ? new ItemStack(c.rank() >= 4 ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE) : ItemStack.EMPTY);
		gear.add(c.rank() >= Captains.RANK_MAX ? new ItemStack(Items.IRON_LEGGINGS) : ItemStack.EMPTY);
		PathfinderMob mob = McaCompat.swornGreenskin(level, orcs ? Heritage.Kind.ORC : Heritage.Kind.GOBLIN, c.name(), beside(level, lord), lord, gear);
		if (mob == null) {
			return;
		}
		Captains.build(mob, c.rank());
		if (c.has(Captains.Trait.DEATHLESS)) {
			Captains.deathless(mob);
		}
		mob.addTag(TAG);
		Captains.bind(level, c, mob);
		Speech.say(level, mob, Component.translatable("talk.jaymoskingdoms.captain.order.back." + level.getRandom().nextInt(LINES), Recollections.nameTo(lord, mob)),
			Speech.RED, SAY_RANGE);
	}

	// ---- each day ----------------------------------------------------------------------------------------------------

	private static void tick(ServerLevel level) {
		MinecraftServer server = level.getServer();
		challenges(level);
		long now = level.getGameTime(), day = now / 24000L;
		Long last = level.getAttached(DAY);
		boolean dawn = last == null || last < day;
		if (dawn) {
			level.setAttached(DAY, day);
		}
		for (Map.Entry<String, Bond> e : List.copyOf(bonds(server).entrySet())) {
			Captain c = Captains.get(server, e.getKey());
			Bond b = e.getValue();
			if (c == null || c.fallen()) {
				put(server, e.getKey(), null);
				continue;
			}
			UUID lordId = b.lordId();
			ServerPlayer lord = lordId == null ? null : server.getPlayerList().getPlayer(lordId);
			if ("guard".equals(b.job()) && lord != null && missing(lord, c)) {
				summon((ServerLevel)lord.level(), c, lord);
			} else if ("spy".equals(b.job()) && now >= b.until() && lord != null) {
				spied(level, c, b, lord);
			} else if ("raid".equals(b.job()) && now >= b.until()) {
				raided(level, c, b);
			}
			if (dawn && betrays(level.getRandom(), b)) {
				betray(level, c, b);
			}
		}
		if (dawn) {
			held(level);
		}
	}

	/** He's back from spying: what he heard of his old tribe's captains, told; then at your side. */
	private static void spied(ServerLevel level, Captain c, Bond b, ServerPlayer lord) {
		MinecraftServer server = level.getServer();
		int told = 0;
		for (int i = 0; i < SPY_TELLS; i++) {
			Map.Entry<Captain, Captains.Trait> e = Captains.unknown(lord, c.tribe(), -1, java.util.EnumSet.allOf(Captains.Sort.class), level.getRandom());
			if (e == null || e.getKey().id().equals(c.id())) {
				break;
			}
			Captains.learn(lord, e.getKey().id(), e.getValue());
			Post.tidings(lord.getUUID(), Component.translatable("tidings.jaymoskingdoms.captain.spied", c.name(), Captains.told(e.getKey(), e.getValue())));
			told++;
		}
		if (told == 0) {
			Post.tidings(lord.getUUID(), Component.translatable("tidings.jaymoskingdoms.captain.spied.nothing", c.name()));
		}
		put(server, c.id(), b.doing("guard", -1L, -1));
	}

	/** Dawn after a raid: a rival captain falls and he's back with loot, or he falls himself. */
	private static void raided(ServerLevel level, Captain c, Bond b) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		Faction camp = Factions.get(server, b.camp());
		Captain rival = null;
		for (Captain x : Captains.of(server, c.tribe())) {
			if (!x.chief() && (rival == null || x.post() == b.camp() && rival.post() != b.camp())) {
				rival = x;
			}
		}
		float odds = RAID_WINS + RAID_PER_RANK * (c.rank() - (rival == null ? 1 : rival.rank()));
		Component where = Component.literal(camp == null ? "?" : camp.name());
		UUID lordId = b.lordId();
		Component lordName = lordId == null ? Component.literal("?") : Post.nameOf(server, lordId);
		if (random.nextFloat() < Math.max(0.15F, Math.min(0.9F, odds))) {
			if (rival != null) {
				Captains.fall(level, rival, true);
			}
			Captains.raise(level, c, random);
			if (lordId != null) {
				Post.later(lordId, new ItemStack(Items.EMERALD, RAID_LOOT + random.nextInt(RAID_LOOT)));
				Tribes.add(server, lordId, c.tribe(), RAID_GRUDGE);
			}
			put(server, c.id(), b.doing("guard", -1L, -1));
			Post.news(server, rival == null
				? Component.translatable("news.jaymoskingdoms.captain.raid.won", c.name(), where, lordName)
				: Component.translatable("news.jaymoskingdoms.captain.raid.won_over", c.name(), where, lordName, rival.name()));
		} else {
			put(server, c.id(), null);
			Captains.fall(level, c, false);
			Post.news(server, Component.translatable("news.jaymoskingdoms.captain.raid.lost", c.name(), where, lordName));
		}
	}

	/** Whether a proud or spiteful captain turns back on his lord this dawn. */
	private static boolean betrays(RandomSource random, Bond b) {
		float chance = "PROUD".equals(b.temper()) ? BETRAY_PROUD : "SPITEFUL".equals(b.temper()) ? BETRAY_SPITEFUL : 0.0F;
		if ("binding".equals(b.how())) {
			chance *= BOUND_HOLDS;
		}
		return chance > 0.0F && random.nextFloat() < chance;
	}

	/** He turns back on his lord: off to his tribe again (a king keeps the seat for himself), and sworn to have their head. */
	private static void betray(ServerLevel level, Captain c, Bond b) {
		MinecraftServer server = level.getServer();
		put(server, c.id(), null);
		Entity body = c.body().map(level::getEntity).orElse(null);
		if ("king".equals(b.job())) {
			hold(server, c.tribe(), null);
		} else {
			if (body instanceof LivingEntity living && living.isAlive()) {
				Speech.say(level, living, Component.translatable("talk.jaymoskingdoms.captain.betrays." + level.getRandom().nextInt(LINES)), Speech.RED, SAY_RANGE);
				level.sendParticles(ParticleTypes.POOF, living.getX(), living.getY() + 1.0, living.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
				living.discard();
			}
			List<Faction> camps = Tribes.camps(server, c.tribe());
			Captains.put(server, c.withPost(camps.isEmpty() ? -1 : Captains.quietest(server, c.tribe(), camps), false).withBody(null));
		}
		UUID lordId = b.lordId();
		if (lordId == null) {
			return;
		}
		Captains.outcome(server, c.id(), lordId, "betrayed", level.getGameTime());
		ServerPlayer lord = server.getPlayerList().getPlayer(lordId);
		if (lord != null) {
			List<String> foes = new ArrayList<>(lord.getAttachedOrElse(Captains.SWORN, List.of()));
			if (!foes.contains(c.id())) {
				foes.add(c.id());
				lord.setAttached(Captains.SWORN, List.copyOf(foes));
			}
		}
		Component lordName = Post.nameOf(server, lordId);
		Post.news(server, Component.translatable("king".equals(b.job()) ? "news.jaymoskingdoms.captain.betrayed.king" : "news.jaymoskingdoms.captain.betrayed",
			c.name(), Tribes.name(c.tribe()), lordName));
		threat(server, c, lordName);
	}

	/** (From {@code Captains.died}) captain {@code c} died: if he was sworn, his bond (and any rule of his) ends. Whether he was. */
	static boolean fell(ServerLevel level, Captain c) {
		MinecraftServer server = level.getServer();
		Bond b = bond(server, c.id());
		if (b == null) {
			return false;
		}
		put(server, c.id(), null);
		UUID lordId = b.lordId();
		if ("king".equals(b.job())) {
			hold(server, c.tribe(), null);
		}
		if (lordId != null) {
			Post.tidings(lordId, Component.translatable("tidings.jaymoskingdoms.captain.sworn_fell", c.name()));
		}
		return true;
	}

	// ---- death threats -----------------------------------------------------------------------------------------------

	/** {@code c}'s death threat for {@code who}, nailed up for the town boards (and in their own Tidings). */
	static void threat(MinecraftServer server, Captain c, Component who) {
		Post.news(server, Component.translatable("news.jaymoskingdoms.captain.threat." + Factions.level(server).getRandom().nextInt(LINES),
			c.name(), Tribes.name(c.tribe()), who));
	}

	/** (From {@code Captains.returns}) {@code c} is back: a threat for every player who killed him. */
	static void back(MinecraftServer server, Captain c) {
		for (Map.Entry<String, Captains.Meeting> e : c.met().entrySet()) {
			if ("slain".equals(e.getValue().last())) {
				try {
					threat(server, c, Post.nameOf(server, UUID.fromString(e.getKey())));
				} catch (IllegalArgumentException ignored) {
					// (a bad entry)
				}
			}
		}
	}

	// ---- challenges --------------------------------------------------------------------------------------------------

	private static List<Challenge> challenges(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(CHALLENGES, List.of());
	}

	private static void setChallenges(MinecraftServer server, List<Challenge> all) {
		Factions.level(server).setAttached(CHALLENGES, List.copyOf(all));
	}

	/** {@code sp} nails a challenge up near {@code at}: true if there's a tribe's camp there with a captain to take it. */
	private static boolean challenge(ServerLevel level, ServerPlayer sp, BlockPos at) {
		MinecraftServer server = level.getServer();
		Faction camp = null;
		String tribe = null;
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			Camps.CampData data = f.fallen() ? null : Camps.data(server, f.id());
			String t = data == null || data.clearedAt() >= 0 ? null : Tribes.of(server, f.id());
			if (t != null && data.center().closerThan(at, CHALLENGE_REACH)) {
				camp = f;
				tribe = t;
				break;
			}
		}
		if (camp == null) {
			return false;
		}
		String me = sp.getStringUUID();
		if (challenges(server).stream().anyMatch(c -> c.player().equals(me))) {
			sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.captain.challenge.out"), true);
			return true;
		}
		final int campId = camp.id();
		Captain taker = null;
		for (Captain c : Captains.of(server, tribe)) {
			if (!c.chief() && (taker == null || c.post() == campId && taker.post() != campId)) {
				taker = c;
			}
		}
		if (taker == null) {
			return false;
		}
		if (taker.post() != campId) {
			Captains.put(server, taker.withPost(campId, false).withBody(null));
		}
		List<Challenge> all = new ArrayList<>(challenges(server));
		all.add(new Challenge(taker.id(), me, campId, level.getGameTime() + CHALLENGE_TICKS, false));
		setChallenges(server, all);
		sp.getMainHandItem().shrink(1);
		level.playSound(null, at, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, at, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.3F, 1.6F);
		Post.news(server, Component.translatable("news.jaymoskingdoms.captain.challenge.set", sp.getDisplayName(), taker.name(), Component.literal(camp.name())));
		return true;
	}

	/** The challenges' day: answered when their challenger comes, run from, or let lapse (a coward, then). */
	private static void challenges(ServerLevel level) {
		MinecraftServer server = level.getServer();
		List<Challenge> all = challenges(server);
		if (all.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		List<Challenge> kept = new ArrayList<>();
		boolean changed = false;
		for (Challenge ch : all) {
			Captain c = Captains.get(server, ch.captain());
			Camps.CampData data = Camps.data(server, ch.camp());
			UUID pid;
			try {
				pid = UUID.fromString(ch.player());
			} catch (IllegalArgumentException e) {
				changed = true;
				continue;
			}
			if (c == null || c.fallen() || data == null || data.clearedAt() >= 0 || sworn(server, c.id())) {
				changed = true;
				continue;
			}
			ServerPlayer p = server.getPlayerList().getPlayer(pid);
			Vec3 center = Vec3.atCenterOf(data.center());
			if (!ch.begun()) {
				if (now > ch.until()) {
					coward(level, c, pid);
					changed = true;
					continue;
				}
				Entity body = c.body().map(level::getEntity).orElse(null);
				if (p != null && p.level() == level && p.position().closerThan(center, CHALLENGE_NEAR) && body instanceof PathfinderMob mob && mob.isAlive()) {
					Rulers.charge(mob, p);
					Speech.say(level, mob, Component.translatable("talk.jaymoskingdoms.captain.challenge.answer." + level.getRandom().nextInt(LINES),
						Recollections.nameTo(p, mob)), Speech.RED, SAY_RANGE);
					level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0F, 1.2F);
					kept.add(new Challenge(ch.captain(), ch.player(), ch.camp(), ch.until(), true));
					changed = true;
					continue;
				}
				kept.add(ch);
				continue;
			}
			if (p == null || p.level() != level || !p.position().closerThan(center, CHALLENGE_FLED)) {
				// (he ran from it)
				Captains.raise(level, c, level.getRandom());
				Post.news(server, Component.translatable("news.jaymoskingdoms.captain.challenge.fled", Post.nameOf(server, pid), c.name()));
				changed = true;
				continue;
			}
			kept.add(ch);
		}
		if (changed) {
			setChallenges(server, kept);
		}
	}

	/** Nobody came to {@code c}'s challenge: he calls them a coward, and swears to have their head. */
	private static void coward(ServerLevel level, Captain c, UUID pid) {
		MinecraftServer server = level.getServer();
		Captains.outcome(server, c.id(), pid, "coward", level.getGameTime());
		ServerPlayer p = server.getPlayerList().getPlayer(pid);
		if (p != null) {
			List<String> foes = new ArrayList<>(p.getAttachedOrElse(Captains.SWORN, List.of()));
			if (!foes.contains(c.id())) {
				foes.add(c.id());
				p.setAttached(Captains.SWORN, List.copyOf(foes));
			}
		}
		Component who = Post.nameOf(server, pid);
		Post.news(server, Component.translatable("news.jaymoskingdoms.captain.challenge.coward", c.name(), who));
		threat(server, c, who);
	}

	/** A challenger fell (anywhere): any challenge of theirs being fought is the captain's. */
	private static void challengerFell(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = Factions.level(server);
		List<Challenge> kept = new ArrayList<>();
		boolean changed = false;
		for (Challenge ch : challenges(server)) {
			if (ch.begun() && ch.player().equals(player.getStringUUID())) {
				Captain c = Captains.get(server, ch.captain());
				if (c != null && !c.fallen()) {
					Captains.raise(level, c, level.getRandom());
					Post.news(server, Component.translatable("news.jaymoskingdoms.captain.challenge.lost", player.getDisplayName(), c.name()));
				}
				changed = true;
			} else {
				kept.add(ch);
			}
		}
		if (changed) {
			setChallenges(server, kept);
		}
	}

	/**
	 * (From {@code Captains.died}) captain {@code c} died: if he fell in a challenge to the one who made it, it's won (he
	 * stays dead, the tribe respects it). Whether it was.
	 */
	static boolean challengeWon(ServerLevel level, Captain c, DamageSource source) {
		MinecraftServer server = level.getServer();
		List<Challenge> kept = new ArrayList<>();
		Challenge won = null;
		for (Challenge ch : challenges(server)) {
			if (ch.captain().equals(c.id())) {
				if (ch.begun() && source.getEntity() instanceof ServerPlayer p && p.getStringUUID().equals(ch.player())) {
					won = ch;
				}
			} else {
				kept.add(ch);
			}
		}
		if (kept.size() != challenges(server).size()) {
			setChallenges(server, kept);
		}
		if (won == null || !(source.getEntity() instanceof ServerPlayer p)) {
			return false;
		}
		Tribes.add(server, p.getUUID(), c.tribe(), -CHALLENGE_GRUDGE);
		Post.news(server, Component.translatable("news.jaymoskingdoms.captain.challenge.won", p.getDisplayName(), c.name(), Tribes.name(c.tribe())));
		return true;
	}

	// ---- the king's seat ---------------------------------------------------------------------------------------------

	/** The tribe {@code e} is king of (a chief at his tribe's seat), or null. */
	private static @Nullable String kingOf(Entity e) {
		MinecraftServer server = e.level().getServer();
		if (server == null || !(e instanceof Worker w) || !CampChiefs.isRole(w.role())) {
			return null;
		}
		String tribe = Tribes.of(server, w.factionId());
		Faction seat = tribe == null ? null : Tribes.seat(server, tribe);
		return seat != null && seat.id() == w.factionId() ? tribe : null;
	}

	/** {@code tribe}'s captains still standing below its king (not turned). */
	private static int warchiefs(MinecraftServer server, String tribe) {
		int n = 0;
		for (Captain c : Captains.of(server, tribe)) {
			if (!c.chief()) {
				n++;
			}
		}
		return n;
	}

	/**
	 * (From {@code LivingEntityHurtMixin}) a blow on a tribe's king from a player or a sworn captain: while any of his captains
	 * stand, he takes only {@link #KING_SHIELD} of it, and says so now and then.
	 */
	public static float guarded(LivingEntity self, DamageSource source, float amount) {
		if (amount <= 0.0F || !(self.level() instanceof ServerLevel level)) {
			return amount;
		}
		Entity by = source.getEntity();
		if (!(by instanceof ServerPlayer) && !(by != null && free(by))) {
			return amount;
		}
		String tribe = kingOf(self);
		MinecraftServer server = level.getServer();
		if (tribe == null || Factions.level(server).getAttachedOrElse(HELD, Map.of()).containsKey(tribe)) {
			return amount;
		}
		int standing = warchiefs(server, tribe);
		if (standing <= 0) {
			return amount;
		}
		long now = level.getGameTime();
		if (now - KING_SAID.getOrDefault(self.getUUID(), -KING_SAYS) >= KING_SAYS) {
			KING_SAID.put(self.getUUID(), now);
			Speech.say(level, self, Component.translatable("talk.jaymoskingdoms.captain.king_guarded." + level.getRandom().nextInt(LINES), standing),
				Speech.RED, SAY_RANGE);
		}
		level.sendParticles(ParticleTypes.CRIT, self.getX(), self.getY(0.6), self.getZ(), 4, 0.3, 0.3, 0.3, 0.1);
		return amount * KING_SHIELD;
	}

	/**
	 * (From {@code Camps.chiefSlain}) a tribe's king died. If a player (or their sworn captain) killed him with none of his
	 * captains standing, and they have a sworn captain of his brood, that captain is crowned in his place: the camp stands,
	 * and the tribe is theirs. True if so (the camp isn't cleared).
	 */
	static boolean seatTaken(ServerLevel level, LivingEntity king, int campId, DamageSource source) {
		MinecraftServer server = level.getServer();
		String tribe = kingOf(king);
		if (tribe == null || Captains.is(king) && sworn(server, String.valueOf(Captains.idOf(king))) || warchiefs(server, tribe) > 0) {
			return false;
		}
		Entity by = source.getEntity();
		UUID lordId = by instanceof ServerPlayer p ? p.getUUID() : by != null && free(by) ? lordOf(by) : null;
		ServerPlayer lord = lordId == null ? null : server.getPlayerList().getPlayer(lordId);
		if (lord == null) {
			return false;
		}
		String brood = tribe.substring(0, tribe.indexOf('@'));
		Captain heir = null;
		for (Map.Entry<String, Bond> e : bonds(server).entrySet()) {
			Captain c = Captains.get(server, e.getKey());
			if (c != null && !c.fallen() && lordId.toString().equals(e.getValue().lord()) && !"king".equals(e.getValue().job())
				&& c.tribe().startsWith(brood + "@") && (heir == null || c.rank() > heir.rank())) {
				heir = c;
			}
		}
		Faction camp = Factions.get(server, campId);
		Camps.CampData data = Camps.data(server, campId);
		if (heir == null || camp == null || data == null) {
			return false;
		}
		Bond b = bond(server, heir.id());
		if (b == null) {
			return false;
		}
		Entity old = heir.body().map(level::getEntity).orElse(null);
		if (old != null) {
			level.sendParticles(ParticleTypes.POOF, old.getX(), old.getY() + 1.0, old.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			old.discard();
		}
		Direction gate = data.gate();
		if (Tribes.ORC.equals(brood)) {
			OrcCamps.crown(level, camp, OrcBuildings.throne(data.center(), gate), gate);
		} else {
			GoblinVillages.crown(level, camp, GoblinBuildings.throne(data.center(), gate), gate);
		}
		Captains.put(server, heir.withPost(campId, true).withBody(null));
		put(server, heir.id(), b.doing("king", -1L, campId));
		hold(server, tribe, lordId.toString());
		Tribes.add(server, lordId, tribe, -Tribes.MOST);
		level.playSound(null, king.getX(), king.getY(), king.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 0.8F);
		Post.news(server, Component.translatable("news.jaymoskingdoms.captain.seat_taken", heir.name(), Tribes.name(tribe), lord.getDisplayName()));
		return true;
	}

	/** Each dawn: a tribe stays yours while your captain keeps its seat. */
	private static void held(ServerLevel level) {
		MinecraftServer server = level.getServer();
		for (Map.Entry<String, String> e : List.copyOf(level.getAttachedOrElse(HELD, Map.of()).entrySet())) {
			String tribe = e.getKey();
			Faction seat = Tribes.seat(server, tribe);
			boolean kept = false;
			for (Map.Entry<String, Bond> b : bonds(server).entrySet()) {
				Captain c = Captains.get(server, b.getKey());
				if ("king".equals(b.getValue().job()) && e.getValue().equals(b.getValue().lord()) && c != null && !c.fallen() && c.tribe().equals(tribe)
					&& seat != null && seat.id() == b.getValue().camp()) {
					kept = true;
					break;
				}
			}
			if (!kept) {
				hold(server, tribe, null);
				try {
					Post.news(server, Component.translatable("news.jaymoskingdoms.captain.seat_lost", Tribes.name(tribe), Post.nameOf(server, UUID.fromString(e.getValue()))));
				} catch (IllegalArgumentException ignored) {
					// (a bad entry)
				}
			}
		}
	}
}
