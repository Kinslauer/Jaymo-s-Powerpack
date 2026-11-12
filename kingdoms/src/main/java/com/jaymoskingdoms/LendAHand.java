package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lend a hand (fifth edition's Help action, Andy's pick A6). A friend at your shoulder is worth having, and it works for
 * anyone with a friend: another player, or a companion who takes your orders ({@link Followers#takesOrders}).
 * <ul>
 * <li><b>A lock</b>: with a friend within {@link #LOCK_NEAR} blocks the pick's sweet spots are wider and the pick strains
 * less ({@link #lockWidth}, {@link #lockStrain}, asked by {@link Lockpicking}): the second pair of hands holds the light
 * and the tension.</li>
 * <li><b>A lift</b>: a friend alongside you ({@link #LIFT_NEAR} blocks, a player friend crouched to take the other end)
 * counts as {@link #HELP_BRAWN} points of hauling brawn for Kitchen's corpses ({@link #brawn}: a body that is a lot to
 * carry alone, and a carcass too big for one pair of arms, are lighter or liftable between two), and takes the weight
 * off whoever carries someone or something in Powers' Carry ({@link #LIFT_EASE}).</li>
 * <li><b>"Watch out"</b>: a friend who sees a foe closing on you from behind, or from somewhere you aren't looking, calls
 * out (a speech bubble and a ping for you). That gives you the upper hand ({@link UpperHand}) on your next blow, for up to
 * {@link #WARN_TICKS}. A friend can call out for you once in {@link #WARN_AGAIN} ticks.</li>
 * <li><b>A haggle</b>: a friend within {@link #HAGGLE_NEAR} blocks speaks up for you, and the guard is likelier to take
 * half ({@link #haggle}, asked by {@link Approaches}).</li>
 * </ul>
 */
public final class LendAHand {
	/** How near a friend is to help with a lock, a lift, and a haggle (blocks). */
	static final double LOCK_NEAR = 3.5, LIFT_NEAR = 3.0, HAGGLE_NEAR = 8.0;
	/** With a friend: the pick's sweet spots this much wider (a factor), and its strain this much (a factor). */
	static final float LOCK_WIDER = 1.35F, LOCK_EASIER = 0.7F;
	/** Hauling brawn a helper adds (Kitchen counts a point as a level off the Slowness of a body), and the walking speed a carrier gets back (a share). */
	static final int HELP_BRAWN = 2;
	static final double LIFT_EASE = 0.10;
	/** A friend this far (blocks) can see a foe come for you; a foe this near (blocks) is close enough to call out. */
	static final double WARN_RANGE = 16.0, WARN_FOE = 12.0;
	/** A foe more behind you than this (the cosine of your look to it) is one you may not have seen. */
	static final double UNSEEN_COS = 0.3;
	/** A friend who is a player must be looking toward the foe within this cosine to have seen it. */
	static final double SEEN_COS = 0.2;
	/** The upper hand a call gives lasts this long (ticks) or until your next blow; a friend calls again after this long. */
	static final int WARN_TICKS = 100, WARN_AGAIN = 160;
	/** How often (ticks) friends look out for you. */
	static final int EVERY = 10;
	/** A friend's word adds this to the chance a guard takes half. */
	static final float HAGGLE_BONUS = 0.25F;
	static final int SPEECH_LINES = 4;

	private static final AttributeModifier.Operation OP = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
	/** Who has been called out to, until when (game time); and when a friend may call out for them again. */
	private static final Map<UUID, Long> WARNED = new HashMap<>(), CALL_AGAIN = new HashMap<>();

	private LendAHand() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LendAHand::tick);
		UpperHand.add((attacker, target) -> attacker instanceof ServerPlayer p && warned(p) ? 1 : 0);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.getEntity() instanceof ServerPlayer p && !WARNED.isEmpty()) {
				WARNED.remove(p.getUUID());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			WARNED.remove(handler.player.getUUID());
			CALL_AGAIN.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WARNED.clear();
			CALL_AGAIN.clear();
		});
	}

	// ---- who a friend is ---------------------------------------------------------------------------------------------------

	/** Whether {@code e} is a friend who can lend {@code who} a hand now: another player, or a companion of theirs, awake and on their feet. */
	static boolean friend(Entity e, ServerPlayer who) {
		if (e == who || !e.isAlive()) {
			return false;
		}
		if (e instanceof ServerPlayer p) {
			return !p.isSpectator() && !p.isSleeping() && !PowersCompat.helpless(p);
		}
		return e instanceof Villager v && !v.isSleeping() && !PowersCompat.helpless(v) && Followers.takesOrders(v, who);
	}

	/** The friends of {@code who} within {@code range} blocks. */
	static List<Entity> friends(ServerPlayer who, double range) {
		return who.level().getEntities(who, who.getBoundingBox().inflate(range), e -> friend(e, who) && e.distanceToSqr(who) <= range * range);
	}

	// ---- a lock ------------------------------------------------------------------------------------------------------------

	/** The factor on the lockpicking game's sweet spots for {@code picker}: wider with a friend by. */
	public static float lockWidth(ServerPlayer picker) {
		return friends(picker, LOCK_NEAR).isEmpty() ? 1.0F : LOCK_WIDER;
	}

	/** The factor on the pick's strain for {@code picker}: less with a friend by. */
	public static float lockStrain(ServerPlayer picker) {
		return friends(picker, LOCK_NEAR).isEmpty() ? 1.0F : LOCK_EASIER;
	}

	// ---- a lift ------------------------------------------------------------------------------------------------------------

	/** Whether {@code carrier} has a friend alongside to take the other end: a companion, or a player friend crouched by them. */
	static boolean lifted(ServerPlayer carrier) {
		for (Entity e : friends(carrier, LIFT_NEAR)) {
			if (!(e instanceof ServerPlayer p) || p.isShiftKeyDown()) {
				return true;
			}
		}
		return false;
	}

	/** Hauling brawn {@code player} has from a friend lifting with them (Kitchen asks, from its hauling). */
	public static int brawn(net.minecraft.world.entity.player.Player player) {
		return player instanceof ServerPlayer sp && lifted(sp) ? HELP_BRAWN : 0;
	}

	// ---- a haggle ----------------------------------------------------------------------------------------------------------

	/** The chance added to a haggle for {@code player}'s friend speaking up (0 if none by); the friend says so. */
	public static float haggle(ServerLevel level, ServerPlayer player) {
		List<Entity> friends = friends(player, HAGGLE_NEAR);
		if (friends.isEmpty()) {
			return 0.0F;
		}
		Entity friend = friends.get(player.getRandom().nextInt(friends.size()));
		Speech.say(level, friend, Component.translatable("speech.jaymoskingdoms.lend.haggle_" + player.getRandom().nextInt(SPEECH_LINES)), Speech.GOLD, 16.0);
		return HAGGLE_BONUS;
	}

	// ---- "watch out" -------------------------------------------------------------------------------------------------------

	/** Whether a friend has called out to {@code player} and they've not struck since. */
	public static boolean warned(ServerPlayer player) {
		Long until = WARNED.get(player.getUUID());
		return until != null && until > player.level().getGameTime();
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			long now = player.level().getGameTime();
			boolean carrying = PowersCompat.carrying(player);
			Skills.modifier(player, Attributes.MOVEMENT_SPEED, "lend_a_hand_lift", carrying && lifted(player) ? LIFT_EASE : 0.0, OP);
			Long again = CALL_AGAIN.get(player.getUUID());
			if (again == null || again <= now) {
				watchOut(player, now);
			}
		}
	}

	/** A foe coming for {@code player} from where they aren't looking, seen by a friend: the friend calls out. */
	private static void watchOut(ServerPlayer player, long now) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		for (Mob foe : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(WARN_FOE), m -> m.isAlive() && m instanceof Enemy && m.getTarget() == player)) {
			Vec3 toFoe = foe.position().subtract(player.position()).normalize();
			if (look.dot(toFoe) > UNSEEN_COS && !foe.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
				continue;
			}
			for (Entity friend : friends(player, WARN_RANGE)) {
				if (!(friend instanceof LivingEntity f) || !f.hasLineOfSight(foe)) {
					continue;
				}
				if (friend instanceof ServerPlayer && f.getLookAngle().dot(foe.position().subtract(f.position()).normalize()) < SEEN_COS) {
					continue;
				}
				call(level, player, f, now);
				return;
			}
		}
	}

	private static void call(ServerLevel level, ServerPlayer player, LivingEntity friend, long now) {
		WARNED.put(player.getUUID(), now + WARN_TICKS);
		CALL_AGAIN.put(player.getUUID(), now + WARN_AGAIN);
		Speech.say(level, friend, Component.translatable("speech.jaymoskingdoms.lend.watch_out_" + player.getRandom().nextInt(SPEECH_LINES)), Speech.RED, 24.0);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.7F, 1.7F);
	}
}
