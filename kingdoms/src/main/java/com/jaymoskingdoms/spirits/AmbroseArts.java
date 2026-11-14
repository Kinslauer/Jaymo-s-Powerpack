package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Ambrose's later arts (Andy, 2026-11-14: "okay start on the other spirits"): the Warden's care reaches past his keeper
 * to everyone who stands with them, and he would still rather end a fight than win it.
 * <ul>
 * <li><b>Shared Ward</b> (level {@value #WARD_LEVEL}): you and every friend within {@value #WARD_RANGE} blocks are warded
 * (four hearts that are not yours to lose, and blows land softer) for {@value #WARD_TICKS} ticks.</li>
 * <li><b>Lay Low</b> ({@value #CALM_LEVEL}): the foe you look at has no fight left in it for {@value #CALM_TICKS} ticks; a
 * great one is only weakened.</li>
 * <li><b>Hearthlight</b> ({@value #LIGHT_LEVEL}): a pool of light where you stand for {@value #LIGHT_TICKS} ticks: friends in
 * it mend, the undead in it burn.</li>
 * <li><b>Stand Again</b> ({@value #RISE_LEVEL}): every friend within {@value #RISE_RANGE} blocks, and you, are mended at once
 * and rid of every ill working on them.</li>
 * </ul>
 */
public final class AmbroseArts {
	static final int WARD_LEVEL = 9, CALM_LEVEL = 11, LIGHT_LEVEL = 13, RISE_LEVEL = 15;
	static final double WARD_RANGE = 8.0, CALM_REACH = 14.0, LIGHT_RADIUS = 5.0, RISE_RANGE = 8.0;
	static final int WARD_TICKS = 300, CALM_TICKS = 200, LIGHT_TICKS = 240;
	static final float CALM_MOST = 100.0F, LIGHT_MEND = 1.0F, LIGHT_BURN = 2.0F, RISE_MEND = 8.0F;
	static final int GREEN = 0x9BE08F;

	static final Move WARD = new Ambrose.Def("shared_ward", 20.0F, 20 * 30, WARD_LEVEL, Poses.AEGIS, Mana.WARD, (p, s, c) -> ward(p));
	static final Move CALM = new Ambrose.Def("lay_low", 14.0F, 200, CALM_LEVEL, Poses.REACH, Mana.WILL, (p, s, c) -> layLow(p));
	static final Move LIGHT = new Ambrose.Def("hearthlight", 26.0F, 20 * 45, LIGHT_LEVEL, Poses.BLESS, Mana.WARD, (p, s, c) -> hearthlight(p));
	static final Move RISE = new Ambrose.Def("stand_again", 30.0F, 20 * 60, RISE_LEVEL, Poses.MEND, Mana.WARD, (p, s, c) -> standAgain(p));
	static final List<Move> MOVES = List.of(WARD, CALM, LIGHT, RISE);

	private record Pool(ServerPlayer owner, ServerLevel level, Vec3 at, long until) {
	}

	private static final Map<UUID, Pool> POOLS = new HashMap<>();

	private AmbroseArts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (POOLS.isEmpty() || server.getTickCount() % 20 != 0) {
				return;
			}
			for (Iterator<Pool> it = POOLS.values().iterator(); it.hasNext();) {
				Pool pool = it.next();
				if (pool.level().getGameTime() > pool.until() || pool.owner().isRemoved()) {
					it.remove();
					continue;
				}
				for (LivingEntity e : pool.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pool.at(), pool.at()).inflate(LIGHT_RADIUS, 2.5, LIGHT_RADIUS),
					e -> e.isAlive() && e.position().distanceToSqr(pool.at()) <= LIGHT_RADIUS * LIGHT_RADIUS + 4.0)) {
					if (friend(pool.owner(), e)) {
						PowersCompat.mend(e, LIGHT_MEND);
					} else if (e.is(EntityTypeTags.UNDEAD) && SpiritFoes.foe(pool.owner(), e, false)) {
						SpiritFoes.strike(pool.level(), pool.owner(), e, LIGHT_BURN);
						e.igniteForTicks(40);
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> POOLS.clear());
	}

	/** One of {@code player}'s own: themselves, another player, a pet, a thrall, their faction's folk. */
	static boolean friend(ServerPlayer player, LivingEntity e) {
		return e == player || e instanceof Player other && !other.isSpectator() || HandMoves.ally(player, e);
	}

	static List<LivingEntity> friends(ServerPlayer player, double range) {
		return new ArrayList<>(player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
			e -> e.isAlive() && friend(player, e) && e.distanceToSqr(player) <= range * range));
	}

	static boolean ward(ServerPlayer player) {
		ServerLevel level = (ServerLevel)player.level();
		for (LivingEntity e : friends(player, WARD_RANGE)) {
			e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, WARD_TICKS, 1), player);
			e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, WARD_TICKS, 0), player);
			Mana.sendSeen(level, Fx.wisp(GREEN, 1.0F), e.getX(), e.getY() + 1.0, e.getZ(), 10, 0.4, 0.7, 0.4, 0.01);
		}
		Fx.ring(level, player.position().add(0.0, 0.2, 0.0), (float)WARD_RANGE, GREEN);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
		return true;
	}

	private static boolean layLow(ServerPlayer player) {
		for (LivingEntity foe : SpiritFoes.cone(player, CALM_REACH, 10.0, true)) {
			if (player.hasLineOfSight(foe)) {
				calm(player, foe);
				CastHints.setReach(player, foe.distanceTo(player));
				return true;
			}
		}
		return false;
	}

	/** {@code foe} is talked down: no fight in it for a while; a great one is only weakened. */
	static void calm(ServerPlayer player, LivingEntity foe) {
		ServerLevel level = (ServerLevel)player.level();
		if (foe.getMaxHealth() <= CALM_MOST) {
			Staggered.hold(foe, CALM_TICKS);
		} else {
			foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CALM_TICKS, 1), player);
		}
		Mana.sendSeen(level, Fx.wisp(GREEN, 1.0F), foe.getX(), foe.getY() + foe.getBbHeight(), foe.getZ(), 12, 0.3, 0.3, 0.3, 0.01);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	private static boolean hearthlight(ServerPlayer player) {
		ServerLevel level = (ServerLevel)player.level();
		Vec3 at = player.position();
		POOLS.put(player.getUUID(), new Pool(player, level, at, level.getGameTime() + LIGHT_TICKS));
		Fx.pool(level, at.add(0.0, 0.05, 0.0), (float)LIGHT_RADIUS, GREEN, LIGHT_TICKS);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.4F);
		return true;
	}

	static boolean standAgain(ServerPlayer player) {
		ServerLevel level = (ServerLevel)player.level();
		for (LivingEntity e : friends(player, RISE_RANGE)) {
			PowersCompat.mend(e, RISE_MEND);
			PowersCompat.closeCut(e);
			for (MobEffectInstance effect : List.copyOf(e.getActiveEffects())) {
				if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
					e.removeEffect(effect.getEffect());
				}
			}
			e.clearFire();
			Fx.burst(level, e.getX(), e.getY() + 1.0, e.getZ(), GREEN, 1.2F, 14, 0.4, 0.7, 0.4);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 0.8F);
		return true;
	}
}
