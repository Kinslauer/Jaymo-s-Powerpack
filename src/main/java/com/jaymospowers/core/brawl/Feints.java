package com.jaymospowers.core.brawl;

import com.jaymospowers.combat.ClashFx;
import com.jaymospowers.power.zfighter.ZFighterGuard;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (NPC fights 1b) Swings that don't land, both ways, for every brawler ({@link Brawl}). Every arm swing, a player's or an
 * NPC's, is noted ({@link #swung}, from a mixin on {@code LivingEntity#swing}); a few ticks on, one that did no damage
 * ({@link #WHIFF_WINDOW}) is a whiff or a feint, and each brawler near it who the swing was aimed at reacts, by rank:
 * <ul>
 * <li>a low-rank one <b>bites</b>: it flinches into a short guard, and when that drops it is open for {@link #OPEN_TICKS}
 * (no guard, no dodge, {@link #OPEN_BONUS} the damage), so the real blow that follows lands hard;</li>
 * <li>a high-rank one <b>reads</b> it ({@link #READ_MIN} to {@link #READ_MAX} of the time): it doesn't bite; it sidesteps or
 * blinks away and punishes the whiff with a counter.</li>
 * </ul>
 * And a high-rank brawler throws feints of its own ({@link #throwFeint}) at a foe who is guarding: a swing that does nothing,
 * then a real poke a few ticks on. A player's Z Fighter guard is baited by it ({@link ZFighterGuard#feinted}): a guard raised
 * in answer to the feint spends its parry.
 */
public final class Feints {
	/** A swing that has done no damage this many ticks on is a whiff. */
	static final int WHIFF_WINDOW = 3;
	/** The brawlers reacting must be this near the swinger (blocks) and in front of it (dot of look and direction). */
	static final double NEAR = 4.5, FACING = 0.3;
	/** A brawler reacts to a swing at most this often (ticks). */
	static final int REACT_GAP = 20;
	/** The chance a swing is read, at rank 0.. and rank 1; a read one is sidestepped (this share) or, from {@link #VANISH_RANK}, blinked away from. */
	static final float READ_MIN = 0.1F, READ_MAX = 0.85F, SIDESTEP_SHARE = 0.5F, VANISH_RANK = 0.5F;
	/** A bitten feint: the flinch guard (ticks), then the open moment (ticks) and the damage taken in it. */
	static final int BITE_GUARD = 10, OPEN_TICKS = 14;
	static final float OPEN_BONUS = 1.5F;
	/** The counter after a read whiff lands this many ticks on. */
	static final int PUNISH_DELAY = 1;
	// ---- the feints they throw ----
	/** From this rank they feint; the chance each check (times rank), the checks' spacing, the gap between feints (ticks). */
	public static final float FEINT_RANK = 0.4F;
	static final float FEINT_CHANCE = 0.3F;
	static final int FEINT_CHECK = 4, FEINT_GAP = 90, FEINT_JITTER = 60;
	static final double FEINT_REACH = 3.5;
	/** The real poke follows after this many ticks, for this share of their blow. */
	static final int FOLLOW_DELAY = 10;
	static final float FOLLOW_SHARE = 0.8F, FOLLOW_FALLBACK = 4.0F;

	private record Swing(LivingEntity who, long at) {
	}

	/** A brawler's own feint: when it may next try one, and the poke that follows. */
	private static final class Thrown {
		long checkAt, feintAt, followAt;
		@Nullable LivingEntity foe;
	}

	private static final List<Swing> PENDING = new ArrayList<>();
	/** When each attacker last landed a blow on anyone (game time). */
	private static final Map<LivingEntity, Long> LANDED = new WeakHashMap<>();
	private static final Map<Mob, Long> REACTED = new WeakHashMap<>();
	private static final Map<Mob, Thrown> THROWN = new WeakHashMap<>();

	private Feints() {
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
			if (amount > 0.0F && source.getEntity() instanceof LivingEntity attacker && victim.level() instanceof ServerLevel level) {
				LANDED.put(attacker, level.getGameTime());
			}
			return true;
		});
		ServerTickEvents.END_SERVER_TICK.register(Feints::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING.clear();
			LANDED.clear();
			REACTED.clear();
			THROWN.clear();
		});
	}

	/** From the mixin: {@code who} swung an arm (anyone's, server side). */
	public static void swung(LivingEntity who) {
		if (who.level() instanceof ServerLevel level && PENDING.size() < 512) {
			PENDING.add(new Swing(who, level.getGameTime()));
		}
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty() && THROWN.isEmpty()) {
			return;
		}
		for (Iterator<Swing> it = PENDING.iterator(); it.hasNext();) {
			Swing swing = it.next();
			if (!(swing.who.level() instanceof ServerLevel level) || swing.who.isRemoved()) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (now - swing.at < WHIFF_WINDOW) {
				continue;
			}
			it.remove();
			Long landed = LANDED.get(swing.who);
			if (swing.who.isAlive() && (landed == null || landed < swing.at - 1)) {
				whiff(level, swing.who, now);
			}
		}
		for (Map.Entry<Mob, Thrown> e : new ArrayList<>(THROWN.entrySet())) {
			Mob mob = e.getKey();
			Thrown t = e.getValue();
			if (mob == null || t.followAt == 0L) {
				continue;
			}
			if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive()) {
				THROWN.remove(mob);
				continue;
			}
			if (level.getGameTime() >= t.followAt) {
				t.followAt = 0L;
				follow(level, mob, t.foe);
			}
		}
	}

	/** A swing by {@code who} found nothing: the brawlers it was aimed at react. */
	private static void whiff(ServerLevel level, LivingEntity who, long now) {
		Vec3 look = who.getLookAngle().multiply(1.0, 0.0, 1.0);
		for (Map.Entry<Mob, Float> e : Brawl.brawlersNear(who, NEAR, now).entrySet()) {
			Mob mob = e.getKey();
			float rank = e.getValue();
			if (REACTED.getOrDefault(mob, 0L) > now) {
				continue;
			}
			Vec3 to = mob.position().subtract(who.position()).multiply(1.0, 0.0, 1.0);
			boolean aimed = mob.getTarget() == who || look.lengthSqr() > 1.0E-4 && to.lengthSqr() > 1.0E-4 && look.normalize().dot(to.normalize()) >= FACING;
			if (!aimed) {
				continue;
			}
			REACTED.put(mob, now + REACT_GAP);
			if (mob.getRandom().nextFloat() < Mth.lerp(rank, READ_MIN, READ_MAX)) {
				read(level, mob, who, rank, now);
			} else {
				// bitten: a flinch into a guard that parries nothing, then open
				Brawl.flinch(mob, now, BITE_GUARD, OPEN_TICKS);
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.HOSTILE, 0.8F, 0.6F);
			}
		}
	}

	/** Read: not fooled; out of the way if it can, and the whiff is punished. */
	private static void read(ServerLevel level, Mob mob, LivingEntity who, float rank, long now) {
		if (rank >= VANISH_RANK && mob.getRandom().nextFloat() >= SIDESTEP_SHARE && Brawl.vanishNow(level, mob, who, now)) {
			return; // behind it, and the blink's own counter
		}
		if (mob.getRandom().nextFloat() < SIDESTEP_SHARE) {
			Brawl.sidestep(mob, now);
		}
		Brawl.counterAt(mob, who, now + PUNISH_DELAY);
	}

	// ================================================================ the feints they throw

	/**
	 * Called from the brain every time it ticks a fighter with a foe: a high-rank fighter feints at a foe who is guarding (a
	 * player's Z Fighter guard, or an NPC's), in reach and not already in a blow.
	 */
	static void throwFeint(ServerLevel level, Mob mob, LivingEntity foe, float rank, boolean busy, long now) {
		if (rank < FEINT_RANK || busy) {
			return;
		}
		Thrown t = THROWN.computeIfAbsent(mob, m -> new Thrown());
		if (now < t.checkAt || now < t.feintAt) {
			return;
		}
		t.checkAt = now + FEINT_CHECK;
		if (mob.distanceTo(foe) > FEINT_REACH || !guarding(foe) || mob.getRandom().nextFloat() >= FEINT_CHANCE * rank) {
			return;
		}
		t.feintAt = now + FEINT_GAP + mob.getRandom().nextInt(FEINT_JITTER);
		t.followAt = now + FOLLOW_DELAY;
		t.foe = foe;
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.6F, 1.3F);
		if (foe instanceof ServerPlayer player) {
			ZFighterGuard.feinted(player);
		}
	}

	/** A swing at {@code foe} that lands nothing, to bait its guard or parry (a string's feint blow throws it): a player's Z Fighter guard is baited too. */
	public static void feintAt(Mob mob, LivingEntity foe) {
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (mob.level() instanceof ServerLevel level) {
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.6F, 1.3F);
		}
		if (foe instanceof ServerPlayer player) {
			ZFighterGuard.feinted(player);
		}
	}

	private static boolean guarding(LivingEntity foe) {
		return foe instanceof Player player ? ZFighterGuard.guarding(player) : Boolean.TRUE.equals(foe.getAttached(ZFighterGuard.GUARD));
	}

	/** The real poke after a feint: a blow for part of the fighter's own, if the foe is still there. */
	private static void follow(ServerLevel level, Mob mob, @Nullable LivingEntity foe) {
		if (foe == null || !foe.isAlive() || mob.distanceTo(foe) > FEINT_REACH + 1.0) {
			return;
		}
		float blow = mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? (float)mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : FOLLOW_FALLBACK;
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		foe.setInvulnerableTime(0);
		if (foe.hurtServer(level, level.damageSources().mobAttack(mob), blow * FOLLOW_SHARE)) {
			Vec3 at = foe.getBoundingBox().getCenter();
			ClashFx.sparks(level, at, at.subtract(mob.position()), ClashFx.BLOCK_SPARKS, ClashFx.BLOCK_SCALE);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 0.9F, 1.0F);
		}
	}
}
