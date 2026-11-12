package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Assassin's gambits, the last two rows of Stealth's fifth page. Each is a perk ({@link Skills.Perk}) and each works
 * from hiding, on top of the Senses and the hidden blade.
 * <ul>
 * <li><b>Watched</b> ({@link Skills.Perk#WATCHED}): keep a target in your crosshair while it hasn't noticed you and the
 * bonus on your next blow builds, up to {@link #WATCHED_CAP} (a share of the blow) at rank 2 and half that at rank 1. A mob
 * that turns on you, or a player that looks your way, breaks it. The blow spends it.</li>
 * <li><b>The Stare</b> ({@link Skills.Perk#THE_STARE}): the same unnoticed gaze, held {@link #STARE_TICKS} ticks while you
 * sneak, closes the target's sight in: a player is struck by darkness, a mob's follow range is cut for a while.</li>
 * <li><b>Slip</b> ({@link Skills.Perk#SLIP_BEHIND}): tap sneak just before a blow lands ({@link #SLIP_WINDOW} ticks) and the
 * blow finds nothing, you stand behind the one who struck. A guard held up is the plant (Powers) and takes precedence. Slow
 * to come round again ({@link #SLIP_COOLDOWN}).</li>
 * <li><b>Body Count</b> ({@link Skills.Perk#BODY_COUNT}): each kill you made from hiding within {@link #BODY_WINDOW} ticks of
 * the last heals a little more than the one before.</li>
 * </ul>
 */
public final class RogueGambits {
	// ---- tuning ----
	/** Watched: how far the crosshair reaches (blocks), the most the bonus reaches (share of the blow) at rank 2, ticks to build to the cap. */
	static final double GAZE_RANGE = 24.0, WATCHED_CAP = 0.5;
	static final int WATCHED_BUILD_TICKS = 120;
	/** Ticks a lost crosshair is forgiven before the bonus is lost. */
	static final int GAZE_GRACE = 10;
	/** How a player must face you (cosine) to count as seeing you. */
	static final double SEEN_COS = 0.35;
	/** The Stare: ticks held to close the sight in, the darkness on a player, the follow range cut on a mob (share) and for how long, the wait before the same target again. */
	static final int STARE_TICKS = 60, STARE_DARK_TICKS = 100, STARE_MOB_TICKS = 600, STARE_AGAIN_TICKS = 600;
	static final double STARE_FOLLOW_CUT = -0.6;
	/** Slip: how recent a sneak press must be for a blow to be slipped (ticks), the wait between slips (ticks), how close behind the striker you land, the longest reach of a blow slipped (blocks). */
	static final int SLIP_WINDOW = 8, SLIP_COOLDOWN = 200;
	static final double SLIP_BEHIND = 1.4, SLIP_REACH = 6.0;
	/** Body Count: the window between kills (ticks), the heal of the first kill and each further one (health, per rank), and the most kills that add. */
	static final int BODY_WINDOW = 400, BODY_MOST = 6;
	static final float BODY_HEAL = 1.0F;

	private static final net.minecraft.resources.Identifier STARE_ID = JaymosKingdoms.id("stare_follow");

	/** What a player keeps in their crosshair: who, since when, when last seen in it, whether the stare has fired. */
	private static final class Gaze {
		final UUID target;
		long since, last;
		boolean stared;

		Gaze(UUID target, long now) {
			this.target = target;
			this.since = now;
			this.last = now;
		}
	}

	private static final Map<UUID, Gaze> GAZES = new HashMap<>();
	private static final Map<UUID, Long> PRESSED = new HashMap<>(), SLIPPED = new HashMap<>();
	private static final Set<UUID> SNEAKING = new HashSet<>();
	private static final Map<UUID, List<Long>> KILLS = new HashMap<>();
	/** player → [target id, game time] of the last blow struck from hiding, for the kill that follows it. */
	private static final Map<UUID, long[]> HIDDEN_BLOW = new HashMap<>();
	/** target → the game time a stare can fire on it again. */
	private static final Map<UUID, Long> STARED = new HashMap<>();
	/** mobs whose follow range is cut: until when. */
	private static final Map<Mob, Long> BLINDED = new java.util.WeakHashMap<>();

	private RogueGambits() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RogueGambits::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RogueGambits::slip);
		ServerLivingEntityEvents.AFTER_DEATH.register(RogueGambits::died);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GAZES.clear();
			PRESSED.clear();
			SLIPPED.clear();
			SNEAKING.clear();
			KILLS.clear();
			HIDDEN_BLOW.clear();
			STARED.clear();
			BLINDED.clear();
		});
	}

	private static void forget(UUID who) {
		GAZES.remove(who);
		PRESSED.remove(who);
		SLIPPED.remove(who);
		SNEAKING.remove(who);
		KILLS.remove(who);
		HIDDEN_BLOW.remove(who);
	}

	// ---- the gaze ----------------------------------------------------------------------------------------------------

	/** The living thing {@code player} has most nearly in the crosshair within {@link #GAZE_RANGE}, in plain sight, or null. */
	private static @Nullable LivingEntity crosshair(ServerLevel level, ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 far = eye.add(player.getLookAngle().scale(GAZE_RANGE));
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GAZE_RANGE),
			e -> e != player && e.isAlive() && !e.isSpectator() && (e instanceof Mob || e instanceof Player))) {
			var hit = e.getBoundingBox().inflate(0.15).clip(eye, far);
			if (hit.isEmpty()) {
				continue;
			}
			double d = eye.distanceToSqr(hit.get());
			if (d < bestDist && player.hasLineOfSight(e)) {
				best = e;
				bestDist = d;
			}
		}
		return best;
	}

	/** {@code target} hasn't noticed {@code player}: a mob not after them, a player not facing them. */
	private static boolean unnoticed(LivingEntity target, ServerPlayer player) {
		if (target instanceof Mob mob) {
			return mob.getTarget() != player && !(mob instanceof TamableAnimal tame && tame.isOwnedBy(player));
		}
		if (target instanceof ServerPlayer other) {
			if (other.isCreative() || other.isSpectator() || Parties.allied(player, other)) {
				return false;
			}
			Vec3 to = player.getEyePosition().subtract(other.getEyePosition());
			return to.lengthSqr() < 1.0E-6 || other.getLookAngle().dot(to.normalize()) < SEEN_COS || !other.hasLineOfSight(player);
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			UUID id = player.getUUID();
			// (sneak presses, for Slip)
			boolean sneaking = player.isShiftKeyDown();
			if (sneaking ? SNEAKING.add(id) : SNEAKING.remove(id)) {
				if (sneaking) {
					PRESSED.put(id, now);
				}
			}
			int watched = Skills.rank(player, Skills.Perk.WATCHED);
			int stare = Skills.rank(player, Skills.Perk.THE_STARE);
			if ((watched <= 0 && stare <= 0) || !player.isAlive() || player.isSpectator() || Downed.isDowned(player)) {
				GAZES.remove(id);
				continue;
			}
			if (now % 2 == 0) {
				gaze(level, player, now, watched, stare);
			}
		}
		if (server.getTickCount() % 20 == 0) {
			for (Iterator<Map.Entry<Mob, Long>> it = BLINDED.entrySet().iterator(); it.hasNext();) {
				var e = it.next();
				if (e.getValue() <= server.overworld().getGameTime() || e.getKey().isRemoved()) {
					AttributeInstance follow = e.getKey().getAttribute(Attributes.FOLLOW_RANGE);
					if (follow != null) {
						follow.removeModifier(STARE_ID);
					}
					it.remove();
				}
			}
		}
	}

	private static void gaze(ServerLevel level, ServerPlayer player, long now, int watched, int stare) {
		UUID id = player.getUUID();
		Gaze gaze = GAZES.get(id);
		LivingEntity looked = crosshair(level, player);
		if (looked != null && unnoticed(looked, player)) {
			if (gaze == null || !gaze.target.equals(looked.getUUID())) {
				gaze = new Gaze(looked.getUUID(), now);
				GAZES.put(id, gaze);
			}
			gaze.last = now;
			if (now % 10 == 0 && watched > 0) {
				int pct = Math.round(bonus(player, gaze, now) * 100.0F);
				if (pct > 0) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.gambit.watched", pct).withStyle(ChatFormatting.GRAY));
				}
			}
			if (stare > 0 && !gaze.stared && player.isShiftKeyDown() && now - gaze.since >= STARE_TICKS && STARED.getOrDefault(looked.getUUID(), 0L) <= now) {
				gaze.stared = true;
				STARED.put(looked.getUUID(), now + STARE_AGAIN_TICKS);
				close(level, player, looked, now);
			}
		} else if (gaze != null && (looked != null && gaze.target.equals(looked.getUUID()) || now - gaze.last > GAZE_GRACE)) {
			// (it noticed you, or you looked away too long)
			GAZES.remove(id);
		}
	}

	/** The share of the blow the gaze has built, by Watched's ranks. */
	private static float bonus(ServerPlayer player, Gaze gaze, long now) {
		int rank = Skills.rank(player, Skills.Perk.WATCHED);
		if (rank <= 0) {
			return 0.0F;
		}
		float cap = (float)(WATCHED_CAP * rank / 2.0);
		return Math.min(cap, cap * (now - gaze.since) / WATCHED_BUILD_TICKS);
	}

	/** The Stare lands: {@code looked} has its sight closed in. */
	private static void close(ServerLevel level, ServerPlayer player, LivingEntity looked, long now) {
		if (looked instanceof ServerPlayer other) {
			other.addEffect(new MobEffectInstance(MobEffects.DARKNESS, STARE_DARK_TICKS, 0, false, false));
			other.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.gambit.stared_player").withStyle(ChatFormatting.DARK_GRAY));
		} else if (looked instanceof Mob mob) {
			AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
			if (follow != null) {
				follow.addOrUpdateTransientModifier(new AttributeModifier(STARE_ID, STARE_FOLLOW_CUT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				BLINDED.put(mob, now + STARE_MOB_TICKS);
			}
		}
		Fx.ring(level, looked.position().add(0.0, 0.1, 0.0), 1.2F, 0x4A3F8A);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.5F, 0.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.gambit.stare", looked.getDisplayName()).withStyle(ChatFormatting.GRAY));
	}

	// ---- the blow ----------------------------------------------------------------------------------------------------

	/**
	 * What a player's blow deals, from {@code amount}, after the sneak attack and the rest: Watched's built bonus, spent on
	 * this blow. Also notes a blow struck from hiding, for Body Count. Called from the hurt mixin, last.
	 */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer sp) || target == sp || target.getInvulnerableTime() > 10 || !(sp.level() instanceof ServerLevel level)) {
			return amount;
		}
		boolean hidden = false;
		Gaze gaze = GAZES.get(sp.getUUID());
		if (gaze != null && gaze.target.equals(target.getUUID())) {
			float bonus = bonus(sp, gaze, level.getGameTime());
			if (bonus > 0.0F) {
				amount *= 1.0F + bonus;
				hidden = true;
			}
			GAZES.remove(sp.getUUID());
		}
		if (!hidden && target instanceof Mob && Senses.unaware(target, sp)) {
			hidden = true;
		}
		if (hidden && Skills.rank(sp, Skills.Perk.BODY_COUNT) > 0) {
			HIDDEN_BLOW.put(sp.getUUID(), new long[] {target.getId(), level.getGameTime()});
		}
		return amount;
	}

	/** A kill that followed a blow struck from hiding: the more of them in a short while, the more it heals. */
	private static void died(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) {
			return;
		}
		int rank = Skills.rank(sp, Skills.Perk.BODY_COUNT);
		long[] blow = HIDDEN_BLOW.remove(sp.getUUID());
		long now = level.getGameTime();
		if (rank <= 0 || blow == null || blow[0] != victim.getId() || now - blow[1] > 2) {
			return;
		}
		List<Long> kills = KILLS.computeIfAbsent(sp.getUUID(), k -> new ArrayList<>());
		kills.removeIf(t -> now - t > BODY_WINDOW);
		kills.add(now);
		int n = Math.min(kills.size(), BODY_MOST);
		float heal = BODY_HEAL * rank * n;
		sp.heal(heal);
		Fx.burst(level, sp.getX(), sp.getY(0.5), sp.getZ(), 0x9A5FD0, 0.8F, 6, 0.3, 0.4, 0.3);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.gambit.body_count", n).withStyle(ChatFormatting.GRAY));
	}

	// ---- lifting a weapon --------------------------------------------------------------------------------------------

	/**
	 * Lift Weapon ({@link Skills.Perk#LIFT_WEAPON}): the pickpocket's hand closes on what {@code mark} holds, not their purse.
	 * Only a villager or a settlement's folk (the pickpocket's marks), and only a tool or weapon in the main hand; the mark is left
	 * empty-handed, and the pickpocket's crime rules apply as for any lift (seen, it is a theft). Empty if there's nothing to lift.
	 */
	static net.minecraft.world.item.ItemStack lifted(ServerPlayer player, LivingEntity mark) {
		if (Skills.rank(player, Skills.Perk.LIFT_WEAPON) <= 0 || mark.getMainHandItem().isEmpty()) {
			return net.minecraft.world.item.ItemStack.EMPTY;
		}
		net.minecraft.world.item.ItemStack held = mark.getMainHandItem().copy();
		mark.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
		return held;
	}

	// ---- slip --------------------------------------------------------------------------------------------------------

	/** A blow on a player who tapped sneak a moment ago finds nothing: they stand behind the striker. */
	private static boolean slip(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level) || !(source.getDirectEntity() instanceof LivingEntity striker)
			|| source.getEntity() != striker || striker == player || amount <= 0.0F || Skills.rank(player, Skills.Perk.SLIP_BEHIND) <= 0
			|| player.isBlocking() || player.isSpectator() || player.isCreative() || Downed.isDowned(player)) {
			return true;
		}
		long now = level.getGameTime();
		Long pressed = PRESSED.get(player.getUUID());
		if (pressed == null || now - pressed > SLIP_WINDOW || SLIPPED.getOrDefault(player.getUUID(), 0L) > now || player.distanceTo(striker) > SLIP_REACH) {
			return true;
		}
		Vec3 face = Vec3.directionFromRotation(0.0F, striker.yBodyRot);
		Vec3 side = new Vec3(-face.z, 0.0, face.x);
		Vec3 start = player.position();
		Vec3 back = striker.position().subtract(face.scale(SLIP_BEHIND));
		Vec3 dest = null;
		for (Vec3 candidate : new Vec3[] {back, back.add(side.scale(0.9)), back.subtract(side.scale(0.9)), striker.position().subtract(face.scale(0.8))}) {
			if (level.noCollision(player, player.getBoundingBox().move(candidate.subtract(start)))) {
				dest = candidate;
				break;
			}
		}
		if (dest == null) {
			return true;
		}
		SLIPPED.put(player.getUUID(), now + SLIP_COOLDOWN);
		PRESSED.remove(player.getUUID());
		Fx.ring(level, start.add(0.0, 0.1, 0.0), 1.0F, 0x4A3F8A);
		float yaw = net.minecraft.util.Mth.wrapDegrees((float)(net.minecraft.util.Mth.atan2(striker.getZ() - dest.z, striker.getX() - dest.x) * 57.2957763671875) - 90.0F);
		player.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), yaw, 0.0F, false);
		Fx.ring(level, dest.add(0.0, 0.1, 0.0), 1.0F, 0x4A3F8A);
		level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.gambit.slip").withStyle(ChatFormatting.GRAY));
		return false;
	}
}
