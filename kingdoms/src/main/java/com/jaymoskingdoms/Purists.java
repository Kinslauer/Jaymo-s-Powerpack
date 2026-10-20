package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.98) The Purist ruler ({@link Tempers.Temper#PURIST}): a proud king of one blood ({@link Heritage}) who insists his
 * realm be of it too, and looks down on everyone who isn't. His blood is fixed by who he is ({@link #blood}), as his
 * temper is, and he's made of it if he wasn't.
 * <ul>
 * <li><b>His people</b>: most born on his land are of his blood ({@link #lean}), every folk his realm sets up is
 * ({@link VillagerHeritage#folkSetup}), and his guards are drafted from his own kind first ({@code McaBridge}).</li>
 * <li><b>Townsfolk</b> ({@link #tick}): those of his blood jeer at a stranger who isn't, and shoulder them aside when
 * they're in the way ("stepping over" them); they greet one of their own warmly. Now and then they turn on a neighbour
 * of another blood the same way, where a player can see it, and the neighbour slinks off.</li>
 * <li><b>Prices</b> ({@link #prices}): his realm's traders charge outsiders more and their own kind less.</li>
 * <li><b>Good name</b> ({@link #reputation}): won quickly and lost slowly by his own kind; the reverse for outsiders.</li>
 * <li><b>Gossip</b> ({@link #gossip}): his people boast of their blood, or (those not of it) grumble at being
 * looked down on.</li>
 * </ul>
 * How he treats his people on his rounds is {@link Tempers#meet}'s; his guards about him, {@link Retinue}'s; his
 * guards' tolls, searches, bouts and loyalty tests, {@link Approaches}'.
 */
public final class Purists {
	/** How likely each blood is for a purist king, by {@link Heritage.Kind} order (out of their sum): any blood can be proud. */
	static final int[] BLOOD_W = {6, 3, 3, 3, 2, 3, 3, 3, 2, 3};
	/** One born on his land (within {@link #LAND_RANGE} of his seat) is of his blood this often. */
	static final float BIRTH_SHARE = 0.85F;
	static final double LAND_RANGE = 96.0;
	/** His city (from the seat), where townsfolk mind a stranger's blood. */
	static final double CITY_RADIUS = Retinue.CITY_RADIUS;
	/** How often players are looked at for townsfolk to mind (ticks). */
	static final int TICK = 40;
	/** How far a townsperson notices a stranger; how close is in their way; how hard they shoulder past. */
	static final double NOTICE_RANGE = 10.0, IN_THE_WAY = 1.6, SHOULDER = 0.55;
	/** The chance, each look, a townsperson jeers; at most once in this long per player (ticks), and shouldering at most this often. */
	static final float JEER_CHANCE = 0.25F;
	static final int JEER_TICKS = 20 * 30, SHOULDER_TICKS = 20 * 4;
	/** One of their own: a warm greeting this often at most (ticks), at this chance each look. */
	static final int KIN_GREET_TICKS = 20 * 120;
	static final float KIN_GREET_CHANCE = 0.15F;
	/** Townsfolk turning on a neighbour of another blood (seen by a player within this range): the chance each look, and how far apart. */
	static final double SCENE_RANGE = 16.0, SCENE_REACH = 6.0;
	static final float SCENE_CHANCE = 0.08F;
	static final int SCENE_TICKS = 20 * 45;
	/** How far words carry. */
	static final double SPEECH_RANGE = 14.0;
	/** Prices: outsiders pay this share more (at least one more), his own kind this share less (in whole items, rounded down). */
	static final float OUTSIDER_MARKUP = 0.35F, KIN_DISCOUNT = 0.2F;
	/** Good name with a purist realm, in percent: won and lost by his own kind, and by outsiders. */
	static final int KIN_GAIN = 150, KIN_LOSS = 75, OUTSIDER_GAIN = 50, OUTSIDER_LOSS = 150;
	/** Gossip about blood in his realm: how often, instead of the usual. */
	static final float GOSSIP_CHANCE = 0.35F;
	/** Lines of each kind ({@code purist.jaymoskingdoms.<kind>.<n>}, from 0). */
	static final int JEER_LINES = 6, SHOULDER_LINES = 4, KIN_LINES = 4, SCENE_LINES = 5, SLINK_LINES = 3, PROUD_GOSSIP_LINES = 5, GRUMBLE_GOSSIP_LINES = 4;

	/** "player" → when they were last jeered at; last shouldered; last greeted as kin. */
	private static final Map<UUID, Long> JEERED = new HashMap<>(), SHOULDERED = new HashMap<>(), GREETED = new HashMap<>();
	/** Faction id → when townsfolk last turned on a neighbour there. */
	private static final Map<Integer, Long> SCENES = new HashMap<>();

	private Purists() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Purists::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JEERED.clear();
			SHOULDERED.clear();
			GREETED.clear();
			SCENES.clear();
		});
	}

	// ---- whose blood ---------------------------------------------------------------------------------------------------

	/** A purist realm: its own ruler is a purist (a fief's lord is his own man). */
	static boolean is(@Nullable Faction f) {
		return f != null && !f.fallen() && Tempers.of(f) == Tempers.Temper.PURIST;
	}

	/** {@code f}'s ruler's blood, drawn from who he is (as his temper is); null without a ruler of its own. */
	static Heritage.@Nullable Kind blood(@Nullable Faction f) {
		if (f == null || f.leader().isEmpty()) {
			return null;
		}
		UUID id = f.leader().get();
		long mix = Long.rotateLeft(id.getLeastSignificantBits(), 13) * 0x9E3779B97F4A7C15L + id.getMostSignificantBits();
		int total = 0;
		for (int w : BLOOD_W) {
			total += w;
		}
		int pick = (int)Math.floorMod(mix ^ (mix >>> 31), (long)total);
		Heritage.Kind[] kinds = Heritage.Kind.values();
		for (int i = 0; i < kinds.length && i < BLOOD_W.length; i++) {
			pick -= BLOOD_W[i];
			if (pick < 0) {
				return kinds[i];
			}
		}
		return Heritage.Kind.HEARTLANDER;
	}

	/** {@code f}'s ruler's blood, named. */
	static Component bloodName(Faction f) {
		Heritage.Kind kind = blood(f);
		return kind == null ? Component.translatable("purist.jaymoskingdoms.no_blood") : name(kind);
	}

	static Component name(Heritage.@Nullable Kind kind) {
		return kind == null ? Component.translatable("purist.jaymoskingdoms.no_blood") : Component.translatable("heritage.jaymoskingdoms." + kind.key());
	}

	/** The heritage of {@code e}: a player's, or a villager's. */
	static Heritage.@Nullable Kind of(Entity e) {
		return e instanceof Player p ? Heritage.of(p) : VillagerHeritage.of(e);
	}

	/** {@code e} is of the purist {@code f}'s blood. */
	static boolean kin(Faction f, Entity e) {
		Heritage.Kind blood = blood(f);
		return blood != null && of(e) == blood;
	}

	/** Not of a purist realm's blood: a player who isn't of it (false anywhere else). */
	static boolean outsider(@Nullable Faction f, Entity e) {
		return is(f) && !kin(f, e);
	}

	// ---- his people ----------------------------------------------------------------------------------------------------

	/**
	 * Born at {@code pos} (from {@link VillagerHeritage#roll}): on a purist's land, most often his blood. Null leaves it
	 * to the usual weights.
	 */
	static Heritage.@Nullable Kind lean(ServerLevel level, BlockPos pos, RandomSource random) {
		Faction f = Factions.around(level.getServer(), level, pos, LAND_RANGE);
		if (!is(f) || random.nextFloat() >= BIRTH_SHARE) {
			return null;
		}
		return blood(f);
	}

	/** A folk set up for faction {@code id}: a purist realm's own are all of his blood (null: the usual roll). */
	static Heritage.@Nullable Kind folk(MinecraftServer server, int id) {
		Faction f = Factions.get(server, id);
		return is(f) ? blood(f) : null;
	}

	/** Good name won or lost with a purist realm by {@code player}, after the ruler's temper ({@link Tempers#reputation}). */
	static int reputation(Faction kingdom, ServerPlayer player, int by) {
		if (!is(kingdom)) {
			return by;
		}
		boolean kin = kin(kingdom, player);
		return by > 0 ? Math.max(1, by * (kin ? KIN_GAIN : OUTSIDER_GAIN) / 100) : by * (kin ? KIN_LOSS : OUTSIDER_LOSS) / 100;
	}

	/** {@code villager}'s offers to {@code player} (from {@code VillagerPriceMixin}, after vanilla's own): dearer for outsiders on a purist's land. */
	public static void prices(AbstractVillager villager, Player player) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		Faction f = Banners.owner(level.getServer(), ChunkPos.containing(villager.blockPosition()));
		if (!is(f)) {
			return;
		}
		boolean kin = kin(f, player);
		for (MerchantOffer offer : villager.getOffers()) {
			int cost = offer.getBaseCostA().getCount();
			int diff = kin ? -(int)Math.floor(cost * KIN_DISCOUNT) : Math.max(1, (int)Math.ceil(cost * OUTSIDER_MARKUP));
			offer.addToSpecialPriceDiff(diff);
		}
	}

	// ---- the townsfolk -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % TICK != 0) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.isCreative() || !p.isAlive()) {
				continue;
			}
			Faction f = Factions.realmNear(server, p.blockPosition(), CITY_RADIUS);
			if (!is(f) || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			if (kin(f, p)) {
				greet(level, f, p, now);
			} else {
				mind(level, f, p, now);
			}
			scene(level, f, p, now);
		}
	}

	/** Townsfolk of {@code f}'s blood near {@code e} (not asleep, not children, not fighting, not {@code e}). */
	private static List<LivingEntity> proud(ServerLevel level, Faction f, Entity e, double range) {
		Heritage.Kind blood = blood(f);
		return level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(range), x -> x != e && x.isAlive() && !x.isBaby()
			&& !x.isSleeping() && VillagerHeritage.eligible(x) && VillagerHeritage.of(x) == blood && !(x instanceof Mob m && m.getTarget() != null)
			&& (!(x instanceof Folk folk) || folk.factionId() == f.id()));
	}

	private static @Nullable LivingEntity nearest(List<LivingEntity> among, Entity to) {
		LivingEntity best = null;
		for (LivingEntity x : among) {
			if (best == null || x.distanceToSqr(to) < best.distanceToSqr(to)) {
				best = x;
			}
		}
		return best;
	}

	/** An outsider in his city: jeered at, and shouldered aside when they're in the way. */
	private static void mind(ServerLevel level, Faction f, ServerPlayer p, long now) {
		List<LivingEntity> near = proud(level, f, p, NOTICE_RANGE);
		LivingEntity by = nearest(near, p);
		if (by == null) {
			return;
		}
		RandomSource random = level.getRandom();
		Component theirs = name(Heritage.of(p)), ours = bloodName(f);
		if (by.distanceTo(p) <= IN_THE_WAY && now - SHOULDERED.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) >= SHOULDER_TICKS) {
			SHOULDERED.put(p.getUUID(), now);
			shoulder(level, by, p);
			Speech.say(level, by, Component.translatable("purist.jaymoskingdoms.shoulder." + random.nextInt(SHOULDER_LINES), theirs, ours), Speech.RED,
				SPEECH_RANGE);
			return;
		}
		if (now - JEERED.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) >= JEER_TICKS && random.nextFloat() < JEER_CHANCE) {
			JEERED.put(p.getUUID(), now);
			look(by, p);
			Speech.say(level, by, Component.translatable("purist.jaymoskingdoms.jeer." + random.nextInt(JEER_LINES), theirs, ours, Identities.shownName(p)),
				Speech.RED, SPEECH_RANGE);
			level.playSound(null, by.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.7F, 0.9F);
		}
	}

	/** One of their own in his city: a warm word now and then. */
	private static void greet(ServerLevel level, Faction f, ServerPlayer p, long now) {
		RandomSource random = level.getRandom();
		if (now - GREETED.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) < KIN_GREET_TICKS || random.nextFloat() >= KIN_GREET_CHANCE) {
			return;
		}
		LivingEntity by = nearest(proud(level, f, p, NOTICE_RANGE), p);
		if (by == null) {
			return;
		}
		GREETED.put(p.getUUID(), now);
		look(by, p);
		Speech.say(level, by, Component.translatable("purist.jaymoskingdoms.kin." + random.nextInt(KIN_LINES), bloodName(f), Identities.shownName(p)),
			Speech.GOLD, SPEECH_RANGE);
	}

	/** Townsfolk turning on a neighbour of another blood, near {@code p} (who sees it). */
	private static void scene(ServerLevel level, Faction f, ServerPlayer p, long now) {
		RandomSource random = level.getRandom();
		if (now - SCENES.getOrDefault(f.id(), Long.MIN_VALUE / 2) < SCENE_TICKS || random.nextFloat() >= SCENE_CHANCE) {
			return;
		}
		Heritage.Kind blood = blood(f);
		List<LivingEntity> others = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SCENE_RANGE),
			x -> x.isAlive() && !x.isSleeping() && VillagerHeritage.eligible(x) && VillagerHeritage.of(x) != null && VillagerHeritage.of(x) != blood
				&& !(x instanceof Mob m && m.getTarget() != null)));
		if (others.isEmpty()) {
			return;
		}
		LivingEntity victim = others.get(random.nextInt(others.size()));
		LivingEntity bully = nearest(proud(level, f, victim, SCENE_REACH), victim);
		if (bully == null) {
			return;
		}
		SCENES.put(f.id(), now);
		Component theirs = name(VillagerHeritage.of(victim)), ours = bloodName(f);
		look(bully, victim);
		Speech.say(level, bully, Component.translatable("purist.jaymoskingdoms.scene." + random.nextInt(SCENE_LINES), theirs, ours, victim.getDisplayName()),
			Speech.RED, SPEECH_RANGE);
		if (bully.distanceTo(victim) <= SCENE_REACH / 2.0) {
			shoulder(level, bully, victim);
		}
		Speech.say(level, victim, Component.translatable("purist.jaymoskingdoms.slink." + random.nextInt(SLINK_LINES)), Speech.WHITE, SPEECH_RANGE);
		slink(victim, bully);
	}

	/** {@code by} shoulders {@code whom} out of the way, never hurting them. */
	static void shoulder(ServerLevel level, LivingEntity by, Entity whom) {
		look(by, whom);
		if (by instanceof Mob m) {
			m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		Vec3 away = whom.position().subtract(by.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(1.0, 0.0, 0.0);
		}
		// (half to the side: they walk on through where you stood)
		Vec3 dir = away.normalize();
		Vec3 side = new Vec3(-dir.z, 0.0, dir.x).scale(level.getRandom().nextBoolean() ? 0.5 : -0.5);
		Vec3 push = dir.add(side).normalize().scale(SHOULDER);
		whom.push(push.x, 0.15, push.z);
		if (whom instanceof ServerPlayer sp) {
			sp.syncVelocity = true;
		}
		level.playSound(null, whom.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 0.5F, 1.1F);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, by.getX(), by.getY() + by.getBbHeight() + 0.2, by.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
	}

	/** A villager heads away from {@code from}, head down. */
	static void slink(LivingEntity who, Entity from) {
		Vec3 away = who.position().subtract(from.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			return;
		}
		Vec3 to = who.position().add(away.normalize().scale(5.0));
		if (who instanceof Villager v) {
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, 0.6F, 1));
		} else if (who instanceof Mob m) {
			m.getNavigation().moveTo(to.x, to.y, to.z, 0.8);
		}
	}

	private static void look(LivingEntity by, Entity at) {
		if (by instanceof Mob m) {
			m.getLookControl().setLookAt(at, 30.0F, 30.0F);
		}
	}

	// ---- talk ----------------------------------------------------------------------------------------------------------

	/**
	 * (Coming over to gossip, in a purist realm) their blood and the king's pride: one of his blood boasts, one of
	 * another grumbles. Null if not this time, or not here.
	 */
	static @Nullable Component gossip(ServerLevel level, Entity villager, ServerPlayer player, @Nullable Faction f) {
		if (!is(f) || level.getRandom().nextFloat() >= GOSSIP_CHANCE || VillagerHeritage.of(villager) == null) {
			return null;
		}
		RandomSource random = level.getRandom();
		boolean ours = kin(f, villager);
		Component name = Identities.shownName(player);
		if (ours) {
			String side = kin(f, player) ? "kin" : "outsider";
			return Component.translatable("purist.jaymoskingdoms.gossip.proud." + side + "." + random.nextInt(PROUD_GOSSIP_LINES), bloodName(f),
				name(Heritage.of(player)), name);
		}
		return Component.translatable("purist.jaymoskingdoms.gossip.grumble." + random.nextInt(GRUMBLE_GOSSIP_LINES), bloodName(f),
			name(VillagerHeritage.of(villager)), name);
	}
}
