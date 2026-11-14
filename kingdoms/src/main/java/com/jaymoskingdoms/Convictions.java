package com.jaymoskingdoms;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a companion truly believes, and what comes of going against it (Andy, 2026-11-14: companions should leave "if you
 * go against anything they truly believe or the quests failure puts you below the minimum").
 * <p>
 * A companion's beliefs come from their temperament ({@link Temperaments}): the kindhearted hold to mercy, the devout to
 * mercy and faith, the dutiful to the law, the brave to courage, the proud to honour, the greedy to gain, the hotheaded
 * and the spiteful to vengeance. A tale's choices and endings say what they go against ({@code "against"} in its file),
 * and cutting down a foe who yielded is always against mercy. Every companion near you who holds one of those leaves you
 * there and then. {@code self} is the companion whose own tale it is: a choice that betrays them sends them off wherever
 * they are. Greenskin captains have no temperament, so only their own tale and their bond can part them from you.
 */
final class Convictions {
	/** A companion this near you (blocks) sees what you do. */
	static final double WITNESS = 32.0;
	/** MCA hearts a companion who leaves you takes with them. */
	static final int LEAVE_HEARTS = 60;
	/** Lines per reason for going. */
	static final int LINES = 2;

	enum Belief {
		MERCY("mercy"), FAITH("faith"), LAW("law"), COURAGE("courage"), HONOUR("honour"), GAIN("gain"), VENGEANCE("vengeance");

		final String key;

		Belief(String key) {
			this.key = key;
		}

		static @Nullable Belief byKey(String key) {
			for (Belief b : values()) {
				if (b.key.equals(key)) {
					return b;
				}
			}
			return null;
		}
	}

	/** The one whose own tale it is. */
	static final String SELF = "self";

	private Convictions() {
	}

	/** What {@code m} truly believes in. */
	static Set<Belief> of(Mob m) {
		Temperaments.Temperament t = Temperaments.of(m);
		if (t == null) {
			return EnumSet.noneOf(Belief.class);
		}
		return switch (t) {
			case KINDHEARTED -> EnumSet.of(Belief.MERCY);
			case DEVOUT -> EnumSet.of(Belief.MERCY, Belief.FAITH);
			case DUTIFUL -> EnumSet.of(Belief.LAW);
			case BRAVE -> EnumSet.of(Belief.COURAGE);
			case PROUD -> EnumSet.of(Belief.HONOUR);
			case GREEDY -> EnumSet.of(Belief.GAIN);
			case HOTHEADED, SPITEFUL -> EnumSet.of(Belief.VENGEANCE);
			default -> EnumSet.noneOf(Belief.class);
		};
	}

	/**
	 * {@code player} has done something {@code against} these beliefs: every companion near them who holds one leaves
	 * them, and so does {@code own} (the companion whose own tale it is) if {@code self} is among them.
	 */
	static void affronted(ServerPlayer player, Collection<String> against, @Nullable Mob own) {
		if (against.isEmpty() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (own != null && own.isAlive() && against.contains(SELF)) {
			leave(level, own, player, SELF);
		}
		for (Mob m : Bonds.companions(player, WITNESS)) {
			if (m == own && against.contains(SELF)) {
				continue;
			}
			Set<Belief> holds = of(m);
			for (String key : against) {
				Belief b = Belief.byKey(key);
				if (b != null && holds.contains(b)) {
					leave(level, m, player, b.key);
					break;
				}
			}
		}
	}

	/** {@code m}'s bond with {@code player} has fallen below what they'd stay for: they go. */
	static void belowMinimum(Mob m, ServerPlayer player) {
		if (m.isAlive() && player.level() instanceof ServerLevel level && player.getUUID().equals(Bonds.lordOf(m)) && Bonds.of(m, player.getUUID()) < Bonds.CLOSE) {
			leave(level, m, player, "failed");
		}
	}

	/** {@code m} leaves {@code player} for good, saying why ({@code why}: a belief, {@code self}, or {@code failed}). */
	static void leave(ServerLevel level, Mob m, ServerPlayer player, String why) {
		if (!player.getUUID().equals(Bonds.lordOf(m)) || m.hasAttached(Loans.LOAN) || m.hasAttached(Loans.TAKEN)) {
			// (one out on loan or held by a camp isn't with you to leave)
			return;
		}
		Speech.tell(level, m, player, Component.translatable("tale.jaymoskingdoms.leaves." + why + "." + level.getRandom().nextInt(LINES)), Speech.RED);
		if (SwornCaptains.free(m) && SwornCaptains.forsake(level, m)) {
			return;
		}
		Bonds.add(m, player.getUUID(), -Bonds.MAX);
		if (m instanceof Villager v) {
			McaCompat.hearts(v, player, -LEAVE_HEARTS);
			Followers.take(v, player, Followers.FREE);
			McaCompat.stopFollowing(v);
			v.removeAttached(Followers.ORDER);
			v.removeAttached(Loans.OWNER);
			v.removeAttached(Loans.LEND);
			v.removeAttached(Loans.TAVERN);
			v.removeAttached(Loans.WANT);
			v.removeAttached(Loans.ERRAND);
		}
		Vec3 away = m.position().subtract(player.position()).normalize().scale(24.0);
		m.getNavigation().moveTo(m.getX() + away.x, m.getY(), m.getZ() + away.z, 0.9);
	}
}
