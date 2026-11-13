package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Turning your back costs you, and polearms hold the line (fifth edition's opportunity attacks and the Polearm Master
 * and Sentinel feats, Andy's picks C1 and C2). Players and everyone else alike:
 * <ul>
 * <li><b>Breaking off</b>: one who steps out of a foe's reach carelessly gets a free blow from that foe as they go. To
 * break off clean, back away with a shield raised, or dodge out.</li>
 * <li><b>Into the reach</b>: whoever holds a spear, halberd or glaive ({@link #POLEARMS}) gets a free blow on a foe
 * stepping in toward them, and the blow stops them where they stand. Guards with halberds hold doorways.</li>
 * </ul>
 * Each striker gets one free blow every {@link #COOLDOWN}; a player's lands only with their swing ready.
 */
public final class Opportunity {
	/** How far a blow reaches (blocks), and a polearm's. */
	static final double REACH = 3.0, POLE_REACH = 4.0;
	/** One free blow a striker gets in this long (ticks). */
	static final int COOLDOWN = 40;
	/** A player's free blow needs the swing this ready (0-1). */
	static final float READY = 0.8F;
	/** A player engaged a foe if they struck it within this long (ticks). */
	static final int ENGAGED = 100;
	/** One stopped by a polearm's blow is held up this long (ticks), slowed this hard. */
	static final int STOPPED = 12, STOPPED_AMP = 4;
	/** Who might be about a player (blocks). */
	static final double LOOK = 6.0;

	/** Spears, halberds, glaives, pikes. */
	public static final TagKey<Item> POLEARMS = TagKey.create(Registries.ITEM, JaymosKingdoms.id("polearms"));

	/** Last tick's distance between each striker and the one they could strike (by their ids). */
	private static Map<Long, Double> last = new HashMap<>();
	/** When each striker last took a free blow (game time), by id. */
	private static final Map<Integer, Long> STRUCK = new HashMap<>();

	private Opportunity() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Opportunity::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			last.clear();
			STRUCK.clear();
		});
	}

	/** Whether {@code stack} is a polearm. */
	public static boolean polearm(ItemStack stack) {
		return stack.is(POLEARMS);
	}

	private static void tick(MinecraftServer server) {
		Map<Long, Double> now = new HashMap<>();
		for (ServerLevel level : server.getAllLevels()) {
			long time = level.getGameTime();
			for (Entity e : level.getAllEntities()) {
				if (e instanceof Mob mob && mob.isAlive() && mob.getTarget() instanceof LivingEntity foe && foe.isAlive() && melee(mob)) {
					pair(level, time, now, mob, foe);
				}
			}
			for (ServerPlayer player : level.players()) {
				if (!player.isAlive() || player.isSpectator() || !melee(player)) {
					continue;
				}
				AABB around = player.getBoundingBox().inflate(LOOK);
				for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, around, f -> f != player && f.isAlive() && engaged(player, f))) {
					pair(level, time, now, player, foe);
				}
			}
		}
		last = now;
		if (STRUCK.size() > 512) {
			STRUCK.clear();
		}
	}

	/** {@code player} is fighting {@code foe}: it's after them, or they struck it a moment ago. */
	private static boolean engaged(ServerPlayer player, LivingEntity foe) {
		return foe instanceof Mob mob && mob.getTarget() == player
			|| player.getLastHurtMob() == foe && player.tickCount - player.getLastHurtMobTimestamp() <= ENGAGED;
	}

	/** Whether {@code who} fights hand to hand: something in hand that isn't a bow, or a mob that isn't an archer. */
	private static boolean melee(LivingEntity who) {
		ItemStack hand = who.getMainHandItem();
		if (hand.getItem() instanceof BowItem || hand.getItem() instanceof CrossbowItem) {
			return false;
		}
		if (who instanceof Player) {
			return !hand.isEmpty();
		}
		return !(who instanceof RangedAttackMob) || !hand.isEmpty();
	}

	private static void pair(ServerLevel level, long time, Map<Long, Double> now, LivingEntity striker, LivingEntity mover) {
		long key = (long) striker.getId() << 32 | (mover.getId() & 0xFFFFFFFFL);
		double d = striker.distanceTo(mover);
		now.put(key, d);
		Double was = last.get(key);
		// (a body thrown back by a blow didn't step away: only one moving by itself)
		if (was == null || !able(striker) || mover.hurtTime > 0 || mover.isPassenger()) {
			return;
		}
		boolean pole = polearm(striker.getMainHandItem());
		double reach = pole ? POLE_REACH : REACH;
		Vec3 away = mover.position().subtract(striker.position());
		Vec3 moved = mover.getDeltaMovement();
		double along = moved.x * away.x + moved.z * away.z;
		if (was <= reach && d > reach && along > 0.0 && !cleanBreak(mover, away) && !FeatsBody.slipsAway(mover) && !Paths.slipsAway(mover)) {
			strike(level, time, striker, mover, FeatsFight.sentinel(striker));
		} else if (pole && was > reach && d <= reach && along < 0.0) {
			strike(level, time, striker, mover, true);
		}
	}

	/** Whether {@code striker} can take a free blow now. */
	private static boolean able(LivingEntity striker) {
		return !PowersCompat.helpless(striker) && !PowersCompat.tripped(striker) && !striker.isSleeping() && !striker.isPassenger()
			&& !striker.hasEffect(MobEffects.BLINDNESS);
	}

	/** Backing off with a shield up, or dodging out: no free blow. */
	private static boolean cleanBreak(LivingEntity mover, Vec3 away) {
		if (mover instanceof ServerPlayer player) {
			if (PowersCompat.dodging(player)) {
				return true;
			}
			Vec3 look = player.getLookAngle();
			// facing the foe they leave (looking back along the way they came)
			boolean facing = look.x * away.x + look.z * away.z < 0.0;
			return player.isBlocking() && facing;
		}
		return false;
	}

	private static void strike(ServerLevel level, long time, LivingEntity striker, LivingEntity mover, boolean stop) {
		Long when = STRUCK.get(striker.getId());
		if (when != null && time - when < (FeatsFight.sentinel(striker) ? FeatsFight.SENTINEL_GAP : COOLDOWN) || !striker.hasLineOfSight(mover)) {
			return;
		}
		if (striker instanceof ServerPlayer player) {
			if (player.getAttackStrengthScale(0.0F) < READY) {
				return;
			}
			STRUCK.put(striker.getId(), time);
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			player.attack(mover);
		} else if (striker instanceof Mob mob) {
			STRUCK.put(striker.getId(), time);
			mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			mob.doHurtTarget(level, mover);
		} else {
			return;
		}
		if (stop && mover.isAlive()) {
			Vec3 v = mover.getDeltaMovement();
			mover.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
			mover.syncVelocity = true;
			mover.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STOPPED, STOPPED_AMP, false, false, false));
			level.playSound(null, mover.getX(), mover.getY(), mover.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.6F, 0.7F);
		}
	}
}
