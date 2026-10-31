package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.443) Havens patch 7b, a greenskin life. Andy: "unlockable by reaching good rep with the orcs". A life-story road
 * ({@link LifeStory.Start#GREENSKIN}, opened for the world once anyone stands well with an orc tribe,
 * {@link TribePacts#FRIEND_DEED}) for one of human-passing blood: raised in the nearest orc camp (a half-orc) or goblin
 * village (a goblin-raised human), you wake just outside it.
 * <ul>
 * <li><b>The camps take you as one of their own</b> ({@link #spares}): no goblin or orc of any tribe sets on you unless you
 * strike it first, your grudge with every tribe rises at {@link #GRUDGE_SHARE} of the usual, and you start in your home
 * tribe's favour ({@link #HOME_FAVOUR}, enough to call on it).</li>
 * <li><b>Human towns take you for the far end of non-human</b> ({@link #minds}): the townsfolk who mind blood mind you at
 * {@link #DEGREE}, as hard as any blood but the Bio-built's, and the tolerant at {@link #TOLERANT} of it. A goblin-raised
 * human can hide it under a full mask; a half-orc can't. Non-humans and the havens don't hold it against you.</li>
 * </ul>
 */
public final class GreenskinLife {
	// ================================================================================================================
	// Tuning
	/** Their grudge with any tribe rises at this share of the usual. */
	static final float GRUDGE_SHARE = 0.25F;
	/** How far from human the townsfolk who mind take them for (the Bio-built are 6), and what the tolerant still mind (a share). */
	static final int DEGREE = 5;
	static final float TOLERANT = 0.4F;
	/** The favour their home tribe starts them with. */
	static final int HOME_FAVOUR = 30;
	/** How far from the camp's fire they wake (blocks). */
	static final int WAKE_MIN = 10, WAKE_MAX = 18;
	// ================================================================================================================

	public static final String ORC = "orc", GOBLIN = "goblin";

	/** On a player: "orc" (a half-orc) or "goblin" (goblin-raised), "|", and their home tribe. Saved, kept through death. */
	static final AttachmentType<String> RAISED = AttachmentRegistry.create(JaymosKingdoms.id("greenskin_raised"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath());

	private GreenskinLife() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("greenskin life attachment: {}", RAISED);
	}

	/** {@code p} was raised among the goblins or orcs. */
	public static boolean raised(@Nullable Entity p) {
		return p instanceof Player && p.hasAttached(RAISED);
	}

	/** {@code p} is a half-orc (raised in an orc camp). */
	public static boolean halfOrc(@Nullable Entity p) {
		String r = p instanceof Player ? p.getAttached(RAISED) : null;
		return r != null && r.startsWith(ORC + "|");
	}

	/** The standing goblin village or orc camp nearest {@code from}, or null. */
	static @Nullable Faction home(MinecraftServer server, BlockPos from) {
		Faction best = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			Camps.CampData data = f.fallen() ? null : Camps.data(server, f.id());
			if (data == null || data.clearedAt() >= 0 || Tribes.of(server, f.id()) == null) {
				continue;
			}
			double d = f.seat().distSqr(from);
			if (d < bestD) {
				best = f;
				bestD = d;
			}
		}
		return best;
	}

	/** (From the life story) {@code player} was raised in {@code camp}: they're one of its tribe's own, and in its favour. */
	static void raise(ServerPlayer player, Faction camp) {
		MinecraftServer server = player.level().getServer();
		String tribe = Tribes.of(server, camp.id());
		if (tribe == null) {
			return;
		}
		player.setAttached(RAISED, (Camps.orc(server, camp.id()) ? ORC : GOBLIN) + "|" + tribe);
		Tribes.add(server, player.getUUID(), tribe, -HOME_FAVOUR);
	}

	/** (From {@link Tribes#spares}, the folk goblins' and orcs' prey) a goblin or orc leaves {@code target} be: one raised among them, unless struck. */
	static boolean spares(LivingEntity mob, @Nullable LivingEntity target) {
		return raised(target) && mob.getLastHurtByMob() != target;
	}

	/**
	 * (From {@link Prejudice#minds}) how much {@code who} holds {@code whom}'s greenskin upbringing against them, or -1 if
	 * {@code whom} wasn't raised so (or hides it under a mask) and their blood decides as ever.
	 */
	static float minds(LivingEntity who, Entity whom) {
		if (!(whom instanceof Player p) || !raised(p) || !halfOrc(p) && Prejudice.masked(p)) {
			return -1.0F;
		}
		if (Greenskins.of(who) != null || Havens.home(who) != null || Prejudice.degree(VillagerHeritage.of(who)) > 0) {
			// (their own kind, the havens, and anyone not plainly human: no worse than any)
			return 0.0F;
		}
		if (!(who.level() instanceof ServerLevel level)) {
			return 0.0F;
		}
		Faction f = Banners.owner(level.getServer(), net.minecraft.world.level.ChunkPos.containing(who.blockPosition()));
		if (Purists.is(f)) {
			return DEGREE * Prejudice.PURIST_HARSH;
		}
		return Prejudice.tolerant(who) ? DEGREE * TOLERANT : DEGREE;
	}

	/** (From {@link Prejudice#degree(Entity)}) how far from human {@code p} is seen to be by their upbringing (0: not raised so, or masked). */
	static int degree(Player p) {
		return !raised(p) || !halfOrc(p) && Prejudice.masked(p) ? 0 : DEGREE;
	}
}
