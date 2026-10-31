package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.442) Havens patch 7a, havens and the tribes (ideas from King's Monsters, ARR: our own names and code).
 * <ul>
 * <li><b>Tribes who spare non-humans.</b> Every haven near a goblin or orc tribe pays it {@link #HAVEN_TRIBUTE} emeralds a
 * day out of its treasury, and the tribe's war bands leave it be while it's paid up ({@link #spared}); human realms pay
 * nothing and are raided as ever. A haven short of the tribute is fair game again. Each standing Ogre King takes
 * {@link #OGRE_TRIBUTE} a day from the nearest haven, into his hoard. Their people talk of it ({@link #gossip}). A
 * non-human's grudge with a tribe rises at {@link #NONHUMAN_SHARE} of the usual: the tribes smell their own kind of
 * outcast, mask or no.</li>
 * <li><b>Calling on a tribe.</b> A grudge lowered below nothing is the tribe's favour ({@link #FAVOUR_MOST} at most): gifts
 * to its chief or pedlar once the grudge is paid off ({@link #GIFT_EMERALDS} for {@link #GIFT_FAVOUR}), and killing its
 * rival brood's folk. It fades slowly ({@link #FAVOUR_DAILY} a day). At {@link #ALLY_AT} or better, when raiders fall on
 * the realm or haven you're sworn to, the tribe's king sends a war band ({@link #called}) that fights the raiders and goes
 * home once they're gone. Good standing with an orc tribe ({@link #FRIEND_AT}) opens the greenskin road of the life story
 * for the world ({@link #FRIEND_DEED}).</li>
 * </ul>
 */
public final class TribePacts {
	// ================================================================================================================
	// Tuning
	/** A tribe's favour: its most (as a negative grudge), what calls a war band, what opens the greenskin road, how fast it fades (a day). */
	public static final int FAVOUR_MOST = 60, ALLY_AT = -30, FRIEND_AT = -20, FAVOUR_DAILY = 1;
	/** A gift to a chief or pedlar once the grudge is paid off: emeralds, and the favour it wins. */
	public static final int GIFT_EMERALDS = 6, GIFT_FAVOUR = 10;
	/** A non-human's grudge rises at this share of the usual (at least 1). */
	static final float NONHUMAN_SHARE = 0.5F;
	/** Havens' tribute to the tribes: emeralds a day to each tribe whose nearest camp is this near; spared this many days after paying. */
	static final int HAVEN_TRIBUTE = 4, PAID_DAYS = 2;
	static final double TRIBUTE_REACH = 1200.0;
	/** An Ogre King's tribute from the nearest haven this near his hold: emeralds a day. */
	static final int OGRE_TRIBUTE = 3;
	static final double OGRE_REACH = 1200.0;
	/** A called war band: from a tribe camp this near the raided seat; its size (a base, one more per so much favour, the most). */
	static final double CALL_REACH = 1500.0;
	static final int CALL_BASE = 3, CALL_PER = 15, CALL_MAX = 6;
	/** Where it musters (blocks from the seat), at most once in this long a player (ticks), and the longest it stays. */
	static final int CALL_NEAR = 24, CALL_FAR = 40;
	static final long CALL_COOLDOWN = 24000L, ALLY_TICKS = 20 * 60 * 6;
	/** It goes home this long after the raid is over (ticks). */
	static final long ALLY_LINGER = 20 * 30;
	/** A haven's people talk of what they pay, this often when they come over to gossip. */
	static final float TRIBUTE_GOSSIP_CHANCE = 0.25F;
	/** Lines: haven tribute to the tribes, to an Ogre King, short of it; a called band's arrival and battle cries. */
	static final int TRIBE_LINES = 4, OGRE_LINES = 4, SHORT_LINES = 3, CALLED_LINES = 3, ALLY_LINES = 4;
	// ================================================================================================================

	/** The road deed good standing with an orc tribe does (opens {@code LifeStory.Start.GREENSKIN}). */
	public static final String FRIEND_DEED = "tribe.orc_friend";
	/** On a called war band's members. */
	static final String ALLY_TAG = "jaymoskingdoms.tribe_ally";

	/** On the Overworld: "haven id|tribe" → the day it last paid that tribe; "ogre|haven id" → the day it last paid an Ogre King. Saved. */
	static final AttachmentType<Map<String, Long>> PAID = AttachmentRegistry.create(JaymosKingdoms.id("tribe_tribute"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	/** On the Overworld: the last day tribute was paid. Saved. */
	static final AttachmentType<Long> PAID_ON = AttachmentRegistry.create(JaymosKingdoms.id("tribe_tribute_day"), builder -> builder.persistent(Codec.LONG));

	/** A called war band: its tribe, the settlement it came to, who called it, when, its members, when the raid was last seen over. */
	private static final class Band {
		final String tribe;
		final int target;
		final UUID caller;
		final long started;
		final List<UUID> members;
		long quietSince = -1L;

		Band(String tribe, int target, UUID caller, long started, List<UUID> members) {
			this.tribe = tribe;
			this.target = target;
			this.caller = caller;
			this.started = started;
			this.members = members;
		}
	}

	private static final List<Band> BANDS = new ArrayList<>();
	/** Player → when a tribe last answered their call (game time). */
	private static final Map<UUID, Long> CALLED = new HashMap<>();

	private TribePacts() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(ALLY_TAG) && band(entity.getUUID()) == null) {
				// (one left from before a restart: gone home)
				entity.discard();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BANDS.clear();
			CALLED.clear();
		});
	}

	// ---- the grudge and favour --------------------------------------------------------------------------------------------

	/** (From {@link Tribes#add}) what a rise of {@code by} in {@code player}'s grudge comes to: less for a non-human. */
	static int grudgeShare(MinecraftServer server, UUID player, int by) {
		ServerPlayer p = server.getPlayerList().getPlayer(player);
		if (p == null) {
			return by;
		}
		float share = Prejudice.degree(Heritage.of(p)) > 0 ? NONHUMAN_SHARE : 1.0F;
		return share >= 1.0F ? by : Math.max(1, Math.round(by * share));
	}

	/** (From {@link Tribes#add}) a grudge with {@code tribe} is now {@code now}: good standing with an orc tribe opens the greenskin road. */
	static void favoured(MinecraftServer server, String tribe, int now) {
		if (now <= FRIEND_AT && tribe.startsWith(Tribes.ORC + "@")) {
			Deeds.done(server, FRIEND_DEED);
		}
	}

	// ---- tribute ---------------------------------------------------------------------------------------------------------

	/** {@code camp} (a goblin village or orc camp) leaves {@code f} be: a haven that lives alongside the tribes, or one paid up with its tribe. */
	static boolean spared(MinecraftServer server, Faction camp, Faction f) {
		if (Havens.greenskin(server, f)) {
			return true;
		}
		String tribe = Tribes.of(server, camp.id());
		if (tribe == null || !Havens.is(server, f)) {
			return false;
		}
		Long day = paid(server).get(f.id() + "|" + tribe);
		return day != null && today(server) - day < PAID_DAYS;
	}

	private static Map<String, Long> paid(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(PAID, Map.of());
	}

	private static long today(MinecraftServer server) {
		return Factions.level(server).getGameTime() / 24000L;
	}

	/** (From {@link Tribes}' tick) once a day: the havens pay the tribes and the Ogre Kings; called bands see to the raid. */
	static void tick(ServerLevel level) {
		MinecraftServer server = level.getServer();
		long day = today(server);
		Long last = level.getAttached(PAID_ON);
		if (last == null || last < day) {
			level.setAttached(PAID_ON, day);
			payDay(level, day);
		}
		bands(level);
	}

	/** Every haven pays the tribes near it (if its treasury holds it), and the nearest to each Ogre King pays him. */
	private static void payDay(ServerLevel level, long day) {
		MinecraftServer server = level.getServer();
		Map<String, Faction> nearest = new HashMap<>();
		for (Faction camp : Factions.all(server, FactionType.BARBARIAN)) {
			Camps.CampData data = camp.fallen() ? null : Camps.data(server, camp.id());
			String tribe = data == null || data.clearedAt() >= 0 ? null : Tribes.of(server, camp.id());
			if (tribe != null) {
				nearest.putIfAbsent(tribe, camp);
			}
		}
		Map<String, Long> paid = new HashMap<>(paid(server));
		List<Faction> havens = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && f.type() != FactionType.BARBARIAN && Havens.is(server, f)) {
				havens.add(f);
			}
		}
		for (Faction haven : havens) {
			if (Havens.greenskin(server, haven)) {
				continue;
			}
			for (String tribe : nearest.keySet()) {
				Faction camp = closest(Tribes.camps(server, tribe), haven.seat());
				if (camp == null || Raids.horizontal(camp.seat(), haven.seat()) > TRIBUTE_REACH) {
					continue;
				}
				if (pay(server, haven.id(), HAVEN_TRIBUTE)) {
					Faction seat = Tribes.seat(server, tribe);
					if (seat != null) {
						Factions.update(server, seat.id(), x -> x.withTreasury(x.treasury() + HAVEN_TRIBUTE));
					}
					paid.put(haven.id() + "|" + tribe, day);
				}
			}
		}
		for (var king : OgreHolds.holds(server).entrySet()) {
			Faction haven = closest(havens, king.getValue());
			if (haven == null || Raids.horizontal(haven.seat(), king.getValue()) > OGRE_REACH) {
				continue;
			}
			if (pay(server, haven.id(), OGRE_TRIBUTE)) {
				paid.put("ogre|" + haven.id(), day);
				hoard(level, king.getValue(), OGRE_TRIBUTE);
			} else {
				paid.put("short|" + haven.id(), day);
			}
		}
		level.setAttached(PAID, Map.copyOf(paid));
	}

	/** Takes {@code emeralds} from settlement {@code id}'s treasury; false (and nothing taken) if it hasn't them. */
	private static boolean pay(MinecraftServer server, int id, int emeralds) {
		Faction f = Factions.get(server, id);
		if (f == null || f.treasury() < emeralds) {
			return false;
		}
		Factions.update(server, id, x -> x.withTreasury(Math.max(0L, x.treasury() - emeralds)));
		return true;
	}

	/** The tribute goes into a chest of the king's hold, if it's loaded. */
	private static void hoard(ServerLevel level, BlockPos at, int emeralds) {
		if (!level.isLoaded(at)) {
			return;
		}
		int r = OgreHolds.HOLD_RADIUS;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -3, -r), at.offset(r, 5, r))) {
			if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
				for (int slot = 0; slot < chest.getContainerSize(); slot++) {
					ItemStack s = chest.getItem(slot);
					if (s.isEmpty() || s.is(Items.EMERALD) && s.getCount() + emeralds <= s.getMaxStackSize()) {
						chest.setItem(slot, new ItemStack(Items.EMERALD, s.getCount() + emeralds));
						chest.setChanged();
						return;
					}
				}
			}
		}
	}

	private static @Nullable Faction closest(List<Faction> of, BlockPos to) {
		Faction best = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : of) {
			double d = Raids.horizontal(f.seat(), to);
			if (d < bestD) {
				best = f;
				bestD = d;
			}
		}
		return best;
	}

	/**
	 * (From {@code Prejudice.gossip}) one of a haven's people talks of what it pays: to an Ogre King, to the tribes, or that
	 * it couldn't. Null if not this time.
	 */
	static @Nullable Component gossip(ServerLevel level, LivingEntity villager, ServerPlayer player) {
		RandomSource random = level.getRandom();
		MinecraftServer server = level.getServer();
		Faction haven = Havens.home(villager);
		if (haven == null || random.nextFloat() >= TRIBUTE_GOSSIP_CHANCE) {
			return null;
		}
		Map<String, Long> paid = paid(server);
		long day = today(server);
		Component name = Identities.shownName(player);
		List<String> said = new ArrayList<>();
		if (day - paid.getOrDefault("ogre|" + haven.id(), -99L) < PAID_DAYS) {
			said.add("ogre." + random.nextInt(OGRE_LINES));
		}
		if (day - paid.getOrDefault("short|" + haven.id(), -99L) < PAID_DAYS) {
			said.add("short." + random.nextInt(SHORT_LINES));
		}
		String prefix = haven.id() + "|";
		if (paid.entrySet().stream().anyMatch(e -> e.getKey().startsWith(prefix) && day - e.getValue() < PAID_DAYS)) {
			said.add("tribe." + random.nextInt(TRIBE_LINES));
		}
		return said.isEmpty() ? null : Component.translatable("tribute.jaymoskingdoms.haven." + said.get(random.nextInt(said.size())), name);
	}

	// ---- calling on a tribe ------------------------------------------------------------------------------------------------

	/**
	 * (From {@code Raids.start}, {@code BorderClashes.start}) raiders set out against {@code target}: the tribe that favours one
	 * of its sworn players most (at {@link #ALLY_AT} or better, with a camp near) sends a war band to its seat.
	 */
	static void called(ServerLevel level, Faction target, Faction from) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		String fromTribe = Tribes.of(server, from.id());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Faction sworn = Factions.of(server, player.getUUID());
			if (sworn == null || sworn.id() != target.id() || now - CALLED.getOrDefault(player.getUUID(), -CALL_COOLDOWN) < CALL_COOLDOWN) {
				continue;
			}
			String best = null;
			int bestG = ALLY_AT + 1;
			Faction bestCamp = null;
			for (String tribe : tribes(server)) {
				int g = Tribes.grudge(server, player.getUUID(), tribe);
				if (g >= bestG || tribe.equals(fromTribe)) {
					// (never against itself)
					continue;
				}
				Faction camp = closest(Tribes.camps(server, tribe), target.seat());
				if (camp != null && Raids.horizontal(camp.seat(), target.seat()) <= CALL_REACH) {
					best = tribe;
					bestG = g;
					bestCamp = camp;
				}
			}
			if (best != null && send(level, best, bestCamp, target, player, -bestG)) {
				CALLED.put(player.getUUID(), now);
				return;
			}
		}
	}

	/** Every tribe with a standing camp. */
	private static Set<String> tribes(MinecraftServer server) {
		Set<String> out = new HashSet<>();
		for (Faction camp : Factions.all(server, FactionType.BARBARIAN)) {
			String tribe = camp.fallen() ? null : Tribes.of(server, camp.id());
			if (tribe != null) {
				out.add(tribe);
			}
		}
		return out;
	}

	/** Musters {@code tribe}'s war band (from {@code camp}) on {@code target}'s land, out toward the camp. */
	private static boolean send(ServerLevel level, String tribe, Faction camp, Faction target, ServerPlayer caller, int favour) {
		RandomSource random = level.getRandom();
		Vec3 toward = Vec3.atCenterOf(camp.seat()).subtract(Vec3.atCenterOf(target.seat())).multiply(1.0, 0.0, 1.0);
		toward = toward.lengthSqr() < 1.0 ? new Vec3(1.0, 0.0, 0.0) : toward.normalize();
		int r = CALL_NEAR + random.nextInt(CALL_FAR - CALL_NEAR);
		BlockPos column = BlockPos.containing(target.seat().getX() + toward.x * r, target.seat().getY(), target.seat().getZ() + toward.z * r);
		if (!level.isLoaded(column)) {
			return false;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		if (!level.getFluidState(at.below()).isEmpty()) {
			return false;
		}
		boolean goblins = tribe.startsWith(Tribes.GOBLIN + "@");
		int size = Math.min(CALL_MAX, CALL_BASE + favour / CALL_PER);
		List<UUID> members = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			int role = goblins ? (i % 3 == 2 ? Goblins.SLINGER : Goblins.GOBLIN) : (i % 3 == 2 ? Orcs.RANGER : Orcs.GRUNT);
			PathfinderMob m = Tribes.muster(level, camp, role, at.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2), target.seat());
			if (m != null) {
				m.addTag(ALLY_TAG);
				members.add(m.getUUID());
			}
		}
		if (members.isEmpty()) {
			return false;
		}
		BANDS.add(new Band(tribe, target.id(), caller.getUUID(), level.getGameTime(), members));
		Entity first = level.getEntity(members.getFirst());
		if (first != null) {
			level.playSound(null, first.getX(), first.getY(), first.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 1.6F, goblins ? 1.5F : 0.8F);
			Speech.say(level, first, Component.translatable("talk.jaymoskingdoms.tribe.called." + random.nextInt(CALLED_LINES), Identities.shownName(caller),
				Tribes.name(tribe)), Speech.GOLD, 32.0);
		}
		JaymosKingdoms.LOGGER.info("{} answers {}'s call: {} to {}", Tribes.name(tribe).getString(), caller.getName().getString(), members.size(), target.name());
		return true;
	}

	private static @Nullable Band band(UUID member) {
		for (Band b : BANDS) {
			if (b.members.contains(member)) {
				return b;
			}
		}
		return null;
	}

	/** Called bands go home once the raid on their settlement is over (and a little longer), or they've stayed their most. */
	private static void bands(ServerLevel level) {
		long now = level.getGameTime();
		for (var it = BANDS.iterator(); it.hasNext();) {
			Band b = it.next();
			List<PathfinderMob> left = new ArrayList<>();
			for (UUID id : b.members) {
				if (level.getEntity(id) instanceof PathfinderMob m && m.isAlive()) {
					left.add(m);
				}
			}
			boolean raided = Raids.raided(b.target) || BorderClashes.under(b.target);
			if (raided) {
				b.quietSince = -1L;
			} else if (b.quietSince < 0) {
				b.quietSince = now;
			}
			if (left.isEmpty() || now - b.started > ALLY_TICKS || b.quietSince >= 0 && now - b.quietSince > ALLY_LINGER) {
				for (PathfinderMob m : left) {
					level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1.0, m.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
					m.discard();
				}
				it.remove();
			}
		}
	}

	/** {@code mob} is one of a called war band. */
	static boolean ally(Entity mob) {
		return mob.entityTags().contains(ALLY_TAG);
	}

	/**
	 * (From {@code McaCompat.foe}) a called war band's member goes for the raiders on its settlement and anyone set on the
	 * one who called it, and no one else; the settlement's guards leave the band be, and haven guards a tribe's pedlar.
	 * Null when neither is one of theirs.
	 */
	static @Nullable Boolean foe(LivingEntity guard, LivingEntity target) {
		if (ally(guard)) {
			Band b = band(guard.getUUID());
			if (b == null) {
				return false;
			}
			if (Raids.isRaider(target) || BorderClashes.puristRaider(target)) {
				return true;
			}
			return target instanceof Mob m && m.getTarget() != null && m.getTarget().getUUID().equals(b.caller);
		}
		if (ally(target)) {
			// (only the raiders go for them)
			return Raids.isRaider(guard) || BorderClashes.puristRaider(guard);
		}
		MinecraftServer server = guard.level().getServer();
		if (server != null && Pedlars.party(target) && guard instanceof Worker w && Havens.is(server, Factions.get(server, w.factionId()))) {
			// (a haven trades with the tribes' pedlars)
			return false;
		}
		return null;
	}

	/** (From {@code Workers.step}) a called band's member between fights: to the settlement's seat, and a cry now and then. True if this was their step. */
	static boolean allyStep(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		Band b = band(mob.getUUID());
		if (b == null || Rulers.target(mob) != null || Rulers.foeNear(level, mob) || McaCompat.busy(mob) || Downed.isDowned(mob)) {
			return false;
		}
		Faction target = Factions.get(level.getServer(), b.target);
		if (target == null) {
			return false;
		}
		BlockPos seat = target.seat();
		if (seat.distToCenterSqr(mob.position()) > 14.0 * 14.0) {
			if (mob.tickCount % 40 == 0 || mob.getNavigation().isDone()) {
				Vec3 at = mob.position(), toward = Vec3.atBottomCenterOf(seat).subtract(at);
				Vec3 leg = toward.length() > 32.0 ? at.add(toward.normalize().scale(32.0)) : Vec3.atBottomCenterOf(seat);
				mob.getNavigation().moveTo(leg.x, leg.y, leg.z, 1.1);
			}
		}
		if (mob.tickCount % 240 == 0 && mob.getRandom().nextInt(4) == 0) {
			Speech.say(level, mob, Component.translatable("talk.jaymoskingdoms.tribe.ally." + mob.getRandom().nextInt(ALLY_LINES)), Speech.GOLD, 20.0);
		}
		return true;
	}
}
