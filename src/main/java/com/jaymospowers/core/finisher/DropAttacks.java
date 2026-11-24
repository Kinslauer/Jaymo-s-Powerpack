package com.jaymospowers.core.finisher;

import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Mortality;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Drop attacks. Andy (2026-11-23): "every player landing on an enemy should deal a devastating animated attack ...
 * everyone is capable of it for an instant down, stealth stat and the hidden blade allow for an instant kill".
 * <p>
 * A player who falls at least {@link #MIN_FALL} blocks onto a foe (a monster, anything after them, or another player)
 * comes down on them: no fall damage, and they're down at once. Those who go down (people, humanoids) are felled by the
 * blow; anything else small enough to lay out ({@link Downed#stunnable}: a wolf, a spider) is knocked out cold. Then a
 * finisher-style move for what's in hand plays astride them ({@link Finishers#dropKind}). Its last beat is a blow, and
 * they stay down for finishing, unless the player's Kingdoms Stealth is high enough or it's the hidden blade
 * ({@code HiddenBlade.drop}, Kingdoms'): then it's their death. Aware or not, landing on them is the ambush. Bosses and
 * anything too big to lay out only take the fall as a heavy blow. While the move plays you can't be hurt.
 */
public final class DropAttacks {
	// ---- tuning ----
	/** The fall that makes a drop attack (blocks): the hidden blade's leap's. */
	static final double MIN_FALL = 4.0;
	/** How far below the feet the falling box reaches for a foe (blocks), and round where they land for the last-moment catch. */
	static final double REACH = 0.6, LAND_REACH = 0.4;
	/** How long one laid out cold by a drop stays out (ticks), when it isn't one that goes down. */
	static final int KNOCKED_OUT_TICKS = 20 * 20;
	/** How long one laid out for a killing drop lies still (the move kills it long before). */
	static final int KILL_LAY_TICKS = 20 * 5;
	/** A blow that downs: their health and shields this many times over (armour can't save them), plus this. */
	static final float FELL_TIMES = 50.0F, FELL_PLUS = 1000.0F;
	/** A boss or something too big to lay out takes the fall as a blow this many times what it would have hurt you. */
	static final float HEAVY_TIMES = 2.0F;

	/** Kingdoms' {@code HiddenBlade.drop(ServerPlayer, LivingEntity)}: 0 downs, 1 kills (Stealth), 2 the hidden blade's (kills). */
	private static final @Nullable Method KINGDOMS_DROP;
	/** Each player's feet last tick, for the swept fall. */
	private static final Map<UUID, Double> LAST_Y = new HashMap<>();

	static {
		Method drop = null;
		if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			try {
				drop = Class.forName("com.jaymoskingdoms.HiddenBlade").getMethod("drop", ServerPlayer.class, LivingEntity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				drop = null;
			}
		}
		KINGDOMS_DROP = drop;
	}

	private DropAttacks() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DropAttacks::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(DropAttacks::allowDamage);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_Y.remove(handler.player.getUUID()));
	}

	/** Whether {@code player} is playing a drop attack now. */
	static boolean dropping(Player player) {
		Finishers.Finish finish = Finishers.of(player);
		return finish != null && finish.type().drop();
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Double lastY = LAST_Y.put(player.getUUID(), player.getY());
			if (dropping(player)) {
				// (rooted astride them: whatever's left of the fall is part of the move)
				player.resetFallDistance();
				continue;
			}
			falling(player, lastY == null ? player.getY() : lastY);
		}
	}

	/** A falling player: does this tick's fall (swept from last tick's feet to {@link #REACH} below now) come down on a foe? */
	private static void falling(ServerPlayer player, double lastY) {
		if (!canDrop(player)) {
			return;
		}
		AABB b = player.getBoundingBox();
		double top = Math.max(lastY, player.getY()) + 0.1;
		AABB box = new AABB(b.minX, player.getY() - REACH, b.minZ, b.maxX, top, b.maxZ);
		// (its top below where their feet were: they came down on it, not fell alongside it)
		LivingEntity target = target((ServerLevel) player.level(), player, box, top);
		if (target != null) {
			land((ServerLevel) player.level(), player, target);
		}
	}

	private static boolean canDrop(ServerPlayer player) {
		return !player.onGround() && player.fallDistance >= MIN_FALL && !player.isPassenger() && !player.isFallFlying()
			&& !player.getAbilities().flying && !player.isInWater() && !player.isSpectator() && player.level() instanceof ServerLevel
			&& !Downed.isDowned(player) && !Finishers.busy(player);
	}

	/**
	 * Fall damage: none while a drop attack plays (nor any other hurt: you can't be touched mid-move); else, the
	 * last-moment catch, a foe standing right where they land that the falling box missed.
	 */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		if (dropping(player)) {
			return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		}
		if (!source.is(DamageTypeTags.IS_FALL) || !canDropLanding(player)) {
			return true;
		}
		LivingEntity target = target((ServerLevel) player.level(), player, player.getBoundingBox().inflate(LAND_REACH), Double.MAX_VALUE);
		return target == null || !land((ServerLevel) player.level(), player, target);
	}

	/** {@link #canDrop}, for the landing itself (on the ground by now). */
	private static boolean canDropLanding(ServerPlayer player) {
		return player.fallDistance >= MIN_FALL && !player.isPassenger() && !player.isSpectator() && player.level() instanceof ServerLevel
			&& !Downed.isDowned(player) && !Finishers.busy(player);
	}

	/** The nearest foe in {@code box} {@code player} could come down on, its top no higher than {@code topBelow}. */
	private static @Nullable LivingEntity target(ServerLevel level, ServerPlayer player, AABB box, double topBelow) {
		LivingEntity best = null;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && foe(player, e))) {
			if (e.getBoundingBox().maxY > topBelow) {
				continue;
			}
			if (best == null || e.distanceToSqr(player) < best.distanceToSqr(player)) {
				best = e;
			}
		}
		return best;
	}

	/** A foe to land on: alive and on its feet, not ridden by or carrying {@code player}, nobody's pet; a monster, one after them, or a player. */
	private static boolean foe(ServerPlayer player, LivingEntity e) {
		if (!e.isAlive() || Downed.isDowned(e) || Finishers.targeted(e) || e == player.getVehicle() || e.getVehicle() == player
			|| e instanceof TamableAnimal tame && tame.isTame()) {
			return false;
		}
		if (e instanceof Player other) {
			return !other.isSpectator() && !other.isCreative();
		}
		return e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == player;
	}

	/** {@code player} comes down on {@code target}. True if the drop took (their fall is spent on it). */
	private static boolean land(ServerLevel level, ServerPlayer player, LivingEntity target) {
		float fall = fallDamage(player);
		player.resetFallDistance();
		boolean big = Mortality.boss(target) || !(Downed.downable(target) || Downed.stunnable(target));
		if (big) {
			// too big to pin: the fall's a heavy blow, and it fights on
			target.hurtServer(level, level.damageSources().playerAttack(player), Math.max(1.0F, fall) * HEAVY_TIMES);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.8F);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
			return true;
		}
		int rank = kingdomsRank(player, target);
		boolean kill = rank >= 1;
		Finishers.Kind kind = rank == 2 ? Finishers.Kind.D_HIDDEN_BLADE : Finishers.dropKind(player);
		layDown(level, player, target, kill);
		if (!target.isAlive() || !Downed.isDowned(target)) {
			// (it wouldn't go down: a totem, a blow it can't take, a player out of reach of PvP; the fall stays spent)
			return true;
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.8F, 1.1F);
		Finishers.drop(level, player, target, kind, kill);
		return true;
	}

	/**
	 * {@code target} flattened by the landing: one who goes down is felled by it, as a killing blow would (the crimes and
	 * witnesses follow it), unless it's to die anyway; anything else is laid out cold. A player only by the blow (no PvP, no drop).
	 */
	private static void layDown(ServerLevel level, ServerPlayer player, LivingEntity target, boolean kill) {
		boolean person = target instanceof Player;
		if (person || !kill && Downed.downable(target)) {
			float fell = (target.getHealth() + target.getAbsorptionAmount()) * FELL_TIMES + FELL_PLUS;
			target.hurtServer(level, level.damageSources().playerAttack(player), fell);
		}
		if (!person && target.isAlive() && !Downed.isDowned(target)) {
			Downed.knockOut(level, target, kill ? KILL_LAY_TICKS : KNOCKED_OUT_TICKS, true, "message.jaymospowers.drop.knocked_down");
		}
	}

	/** Kingdoms' word on this drop: 0 it downs, 1 it kills (their Stealth), 2 the hidden blade's (kills). 0 without Kingdoms. */
	private static int kingdomsRank(ServerPlayer player, LivingEntity target) {
		if (KINGDOMS_DROP == null) {
			return 0;
		}
		try {
			return (int) KINGDOMS_DROP.invoke(null, player, target);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	/** The fall vanilla would hurt {@code player} for from here: (blocks fallen - safe fall) times the fall damage multiplier, rounded up. */
	private static float fallDamage(ServerPlayer player) {
		double safe = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		double mult = player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
		return (float) Math.max(0.0, Math.ceil((player.fallDistance - safe) * mult));
	}
}
