package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.KingdomsCompat;
import com.jaymospowers.core.gifted.Gifted;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Patrol as lawmen (Andy's Buder's Lanterns picks, ideas only: "being a lantern, you're basically a galactic cop ... I want to
 * add the buddy cop crime dynamic").
 * <ul>
 * <li><b>A Patrol warrant.</b> An officer the realms of Jaymo's Kingdoms want for a crime ({@code Theft.wantedUntil}) is a crooked
 * officer: the Patrol writes a warrant, and every new crime adds to it and costs a rank. A patrolman comes down for them
 * ({@link ZFighterPatrol.Cause#ARREST}, at most every {@link #ARREST_GAP}) and fights them, harder the more crimes the warrant
 * holds, until they are beaten down to {@link #BROUGHT_IN_HEALTH} (he stops there: brought in), or they come quietly when they
 * talk to him. Either way the warrant is served.</li>
 * <li><b>Arrests.</b> An officer who hands a hogtied crook to a realm's ruler for its bounty (Kingdoms' {@code Captures}) has made
 * an arrest: it counts toward their rank and pays training, the bounty being the realm's ({@link #handedOver}).</li>
 * <li><b>A partner.</b> From {@link #PARTNER_RANK} a patrolman can be asked for a partner: a patrolman of their own
 * ({@link ZFighterPatrol.Cause#PARTNER}) who stays with them, fights what they fight, talks like any patrolman, and comes along
 * on every pod trip (on landing he steps out beside them). If he falls, a patrolman can be asked for another.</li>
 * </ul>
 */
public final class PatrolLaw {
	// ---- tuning ----
	/** How often members are checked against the realms' wanted lists (ticks). */
	static final int CHECK_EVERY = 100;
	/** The wait between patrolmen sent down for a crooked officer (ticks), and how long one stays. */
	static final long ARREST_GAP = 24000L;
	static final int ARREST_STAY = 9600;
	/** How far he hunts them (blocks); how much harder he hits for each crime on the warrant; the health he stops at. */
	static final double ARREST_RANGE = 96.0, CRIME_HITS = 0.25;
	static final float BROUGHT_IN_HEALTH = 4.0F;
	/** The rank (index into ZFighterPatrol's ranks) from which an officer may ask for a partner. */
	public static final int PARTNER_RANK = 1;
	/** A partner closes in past this (blocks), and is brought to their side past this or in another world. */
	static final double PARTNER_FOLLOW = 6.0, PARTNER_FAR = 48.0;
	/** A partner not found about them for this long (ticks) is sent again (the old one goes home if he turns up). */
	static final long PARTNER_LOST = 600L;

	/** A crooked officer's Patrol warrant: crimes on it (0: none), and the latest wanted-until seen. */
	public record Warrant(int crimes, long lastUntil, long nextLanding) {
		static final Codec<Warrant> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("crimes").forGetter(Warrant::crimes),
			Codec.LONG.fieldOf("last_until").forGetter(Warrant::lastUntil),
			Codec.LONG.optionalFieldOf("next_landing", 0L).forGetter(Warrant::nextLanding)).apply(i, Warrant::new));
		static final Warrant NONE = new Warrant(0, 0L, 0L);
	}

	/** An officer's partner: his name, and the one walking about now. */
	public record Partner(int name, UUID entity, long seen) {
		static final Codec<Partner> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("name").forGetter(Partner::name),
			UUIDUtil.CODEC.fieldOf("entity").forGetter(Partner::entity),
			Codec.LONG.optionalFieldOf("seen", 0L).forGetter(Partner::seen)).apply(i, Partner::new));
	}

	public static final AttachmentType<Warrant> WARRANT = AttachmentRegistry.create(JaymosPowers.id("patrol_warrant"),
		builder -> builder.persistent(Warrant.CODEC).copyOnDeath());
	public static final AttachmentType<Partner> PARTNER = AttachmentRegistry.create(JaymosPowers.id("patrol_partner"),
		builder -> builder.persistent(Partner.CODEC).copyOnDeath());

	private PatrolLaw() {
	}

	// ---------------------------------------------------------------- the warrant

	static Warrant warrant(ServerPlayer player) {
		return player.getAttachedOrElse(WARRANT, Warrant.NONE);
	}

	/** Every {@link #CHECK_EVERY}: members wanted by a realm get (or add to) a Patrol warrant, and one is sent down for them. */
	static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % CHECK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!ZFighterPatrol.member(player) || !player.isAlive() || player.isSpectator()) {
				continue;
			}
			Warrant w = warrant(player);
			Object got = KingdomsCompat.call("com.jaymoskingdoms.Theft", "wantedUntil", new Class<?>[] {ServerPlayer.class}, 0L, player);
			long until = got instanceof Long l ? l : 0L;
			if (until > w.lastUntil()) {
				// a new crime: on the warrant, and a rank lost
				w = new Warrant(w.crimes() + 1, until, w.nextLanding());
				player.setAttached(WARRANT, w);
				demote(player);
				player.sendSystemMessage(Component.translatable(w.crimes() == 1 ? "message.jaymospowers.patrol.warrant_issued" : "message.jaymospowers.patrol.warrant_grows",
					w.crimes()).withStyle(ChatFormatting.DARK_RED));
			}
			partner(server, player, now);
			ServerLevel level = player.level();
			if (w.crimes() <= 0 || now < w.nextLanding() || level.dimensionType().hasCeiling()) {
				continue;
			}
			if (ZFighterPatrol.arrive(level, player.blockPosition(), level.getRandom().nextInt(ZFighterPatrol.NAMES), ZFighterPatrol.Cause.ARREST,
				player.getUUID(), ARREST_STAY)) {
				player.setAttached(WARRANT, new Warrant(w.crimes(), w.lastUntil(), now + ARREST_GAP));
				player.sendSystemMessage(Component.translatable("message.jaymospowers.patrol.warrant_coming").withStyle(ChatFormatting.RED));
			}
		}
	}

	/** A rank lost: their arrests go back to the least the rank below asks. */
	private static void demote(ServerPlayer player) {
		ZFighterPatrol.Member m = player.getAttached(ZFighterPatrol.MEMBER);
		int rank = ZFighterPatrol.rank(m.arrests());
		if (rank <= 0) {
			return;
		}
		player.setAttached(ZFighterPatrol.MEMBER, new ZFighterPatrol.Member(ZFighterPatrol.RANKS[rank - 1], m.fuelled()));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.patrol.demoted", ZFighterPatrol.rankName(rank - 1)).withStyle(ChatFormatting.RED));
	}

	/** The warrant is served (brought in, or came quietly): cleared, and the patrolman goes home. */
	static void served(ServerPlayer player, LivingEntity man, boolean quietly) {
		Warrant w = warrant(player);
		player.setAttached(WARRANT, new Warrant(0, w.lastUntil(), 0L));
		ZFighterPatrol.Visit visit = man.getAttached(ZFighterPatrol.VISIT);
		if (visit != null) {
			man.setAttached(ZFighterPatrol.VISIT, visit.leaving(man.level().getGameTime() + ZFighterPatrol.ANSWERED_STAY));
		}
		if (man instanceof PathfinderMob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
		player.sendSystemMessage(Component.translatable(quietly ? "message.jaymospowers.patrol.came_quietly" : "message.jaymospowers.patrol.brought_in")
			.withStyle(ChatFormatting.AQUA));
	}

	/** An arresting patrolman's turn: after the officer he came for, until they're beaten down. False: no one to arrest (he leaves). */
	static boolean arrest(ServerLevel level, PathfinderMob man, ZFighterPatrol.Visit visit) {
		UUID id = visit.target().orElse(null);
		if (!(id != null && level.getPlayerByUUID(id) instanceof ServerPlayer p) || !p.isAlive() || man.distanceTo(p) > ARREST_RANGE || warrant(p).crimes() <= 0) {
			man.setAttached(ZFighterPatrol.VISIT, visit.leaving(level.getGameTime() + ZFighterPatrol.ANSWERED_STAY));
			return false;
		}
		float hit = (float)(ZFighterPatrol.PUNCH * (1.0 + CRIME_HITS * (warrant(p).crimes() - 1)));
		if (p.getHealth() - hit <= BROUGHT_IN_HEALTH && man.distanceTo(p) <= ZFighterPatrol.REACH) {
			served(p, man, false);
			return true;
		}
		ZFighterPatrol.fight(level, man, p, hit);
		return true;
	}

	// ---------------------------------------------------------------- arrests

	/** (Kingdoms' Captures, by reflection) {@code player} handed a hogtied crook to a realm's ruler: an officer's arrest. */
	public static void handedOver(ServerPlayer player, LivingEntity crook) {
		if (ZFighterPatrol.member(player)) {
			ZFighterPatrol.countArrest(player, crook.getDisplayName(), false);
		}
	}

	// ---------------------------------------------------------------- the partner

	/** {@code player} is given a partner, who steps out beside them. */
	static void givePartner(ServerPlayer player) {
		int name = player.getRandom().nextInt(ZFighterPatrol.NAMES);
		LivingEntity man = spawnPartner(player, name);
		if (man != null) {
			player.setAttached(PARTNER, new Partner(name, man.getUUID(), player.level().getGameTime()));
		}
	}

	private static @Nullable LivingEntity spawnPartner(ServerPlayer player, int name) {
		ServerLevel level = player.level();
		Vec3 at = player.position().add(player.getLookAngle().multiply(-1.5, 0.0, -1.5));
		LivingEntity man = ZFighterNpcs.villager(level, at, player.getYRot(), ZFighterPatrol.TAG, "entity.jaymospowers.patrolman_" + name, name % 4 == 1);
		if (man != null) {
			man.setAttached(ZFighterPatrol.VISIT, new ZFighterPatrol.Visit(man.blockPosition(), Long.MAX_VALUE, ZFighterPatrol.Cause.PARTNER,
				java.util.Optional.of(player.getUUID())));
			Gifted.give(man, "zfighter", Gifted.BOLD, true, true);
		}
		return man;
	}

	/** Keeps {@code player}'s partner with them: noted while about, sent again if long lost (or left in another world). */
	private static void partner(MinecraftServer server, ServerPlayer player, long now) {
		Partner p = player.getAttached(PARTNER);
		if (p == null) {
			return;
		}
		Entity man = player.level().getEntity(p.entity());
		if (man instanceof LivingEntity alive && alive.isAlive()) {
			player.setAttached(PARTNER, new Partner(p.name(), p.entity(), now));
			return;
		}
		if (now - p.seen() >= PARTNER_LOST) {
			LivingEntity again = spawnPartner(player, p.name());
			if (again != null) {
				player.setAttached(PARTNER, new Partner(p.name(), again.getUUID(), now));
			}
		}
	}

	/** (From {@link ZFighterPatrol#arrived}) the pod is down: the partner steps out beside them, the one left behind goes home. */
	static void arrived(ServerPlayer player) {
		Partner p = player.getAttached(PARTNER);
		if (p == null) {
			return;
		}
		for (ServerLevel level : player.level().getServer().getAllLevels()) {
			if (level.getEntity(p.entity()) instanceof LivingEntity old && old.level() != player.level()) {
				old.discard();
			}
		}
		if (!(player.level().getEntity(p.entity()) instanceof LivingEntity here) || !here.isAlive()) {
			LivingEntity man = spawnPartner(player, p.name());
			if (man != null) {
				player.setAttached(PARTNER, new Partner(p.name(), man.getUUID(), player.level().getGameTime()));
			}
		}
	}

	/** A partner's turn: back his officer up, keep at their side. False: he isn't theirs any more (goes home). */
	static boolean partnerStep(ServerLevel level, PathfinderMob man, ZFighterPatrol.Visit visit) {
		UUID id = visit.target().orElse(null);
		ServerPlayer officer = id == null ? null : level.getServer().getPlayerList().getPlayer(id);
		Partner p = officer == null ? null : officer.getAttached(PARTNER);
		if (officer == null) {
			// his officer is away: he waits where they left him
			man.getNavigation().stop();
			return true;
		}
		if (p == null || !p.entity().equals(man.getUUID())) {
			// his officer has another partner now (or none): this one goes home
			man.discard();
			return false;
		}
		if (officer.level() != level || man.distanceTo(officer) > PARTNER_FAR) {
			man.teleportTo(officer.getX(), officer.getY(), officer.getZ());
			return true;
		}
		LivingEntity foe = officer.getLastHurtByMob();
		if (foe == null || !foe.isAlive() || foe == man) {
			foe = officer.getLastHurtMob();
		}
		if (foe != null && foe.isAlive() && foe != man && foe.level() == level && man.distanceTo(foe) < ZFighterPatrol.FIGHT_RANGE
			&& !(foe instanceof ServerPlayer)) {
			ZFighterPatrol.fight(level, man, foe, ZFighterPatrol.PUNCH);
			return true;
		}
		man.setTarget(null);
		man.getLookControl().setLookAt(officer, 30.0F, 30.0F);
		if (man.distanceTo(officer) > PARTNER_FOLLOW) {
			man.getNavigation().moveTo(officer, 1.0);
		} else {
			man.getNavigation().stop();
		}
		return true;
	}

	/** {@code man} fell: if he was someone's partner, they hear of it (a patrolman can be asked for another). */
	static void fell(LivingEntity man) {
		ZFighterPatrol.Visit visit = man.getAttached(ZFighterPatrol.VISIT);
		if (visit == null || visit.cause() != ZFighterPatrol.Cause.PARTNER || !(man.level() instanceof ServerLevel level)) {
			return;
		}
		UUID id = visit.target().orElse(null);
		ServerPlayer officer = id == null ? null : level.getServer().getPlayerList().getPlayer(id);
		Partner p = officer == null ? null : officer.getAttached(PARTNER);
		if (p != null && p.entity().equals(man.getUUID())) {
			officer.removeAttached(PARTNER);
			officer.sendSystemMessage(Component.translatable("message.jaymospowers.patrol.partner_fell", man.getDisplayName()).withStyle(ChatFormatting.RED));
		}
	}
}
