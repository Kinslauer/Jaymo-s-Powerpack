package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.538) Opening a chest makes noise (Andy, 2026-11-04: "opening chests makes noise that can wake sleeping villagers
 * or guards"). Every sleeper but a player within {@link #RANGE} of a chest, barrel or other storage you open may wake:
 * {@link #CLOSE_CHANCE} at arm's length, falling off to nothing at the edge, less through a wall ({@link #WALL_FACTOR}),
 * less again if you open it sneaking ({@link Skills#sneakFactor}, so Soft Step and the Stealth heritages quieten it too).
 * Each rank of Light Fingers takes {@link #LIGHT_FINGERS_HUSH} off both the chance and the creak itself (the chest or
 * barrel's open sound, through {@code ContainerOpenersHushMixin} + {@code ChestCreakVolumeMixin}).
 *
 * <p>Whoever wakes mutters, turns to the noise and walks over to see, and stays up for {@link #STAY_AWAKE_TICKS} (their
 * bed can't take them back before). Awake, they're witnesses like anyone else ({@link Theft#witnesses} skips sleepers),
 * so taking from a realm's chest while a guard you woke is looking is a theft seen.
 */
public final class ChestNoise {
	/** How far the noise carries (blocks). */
	static final double RANGE = 12.0;
	/** The chance a sleeper this close ({@link #CLOSE_RANGE} or nearer) wakes; it falls off evenly to 0 at {@link #RANGE}. */
	static final float CLOSE_CHANCE = 0.85F;
	static final double CLOSE_RANGE = 2.0;
	/** A wall between the chest and the sleeper: the chance is this much of it. */
	static final float WALL_FACTOR = 0.4F;
	/** Opened sneaking: this share of the chance (before Soft Step and heritage take more off). */
	static final double SNEAK_FACTOR = 0.5;
	/** A sleeper who wakes stays up this long (ticks), and comes to look at this pace. */
	static final long STAY_AWAKE_TICKS = 300L;
	static final float LOOK_SPEED = 0.6F;
	/** (Andy: "the sound lowers with thief skill in light fingers") each rank of Light Fingers takes this share off the creak and the wake chance. */
	static final float LIGHT_FINGERS_HUSH = 0.15F;
	/** How many "who's there?" lines there are. */
	static final int LINES = 4;

	/** A sleeper the noise woke: who made it, where, and when they may sleep again. */
	private record Woken(LivingEntity who, UUID by, BlockPos at, long until) {
	}

	private static final Map<UUID, Woken> WOKEN = new HashMap<>();
	/** Who is opening a container right now (set around {@code ContainerOpenersCounter.incrementOpeners}, server thread). */
	private static @Nullable LivingEntity opener;

	private ChestNoise() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server && !(sp.isSecondaryUseActive() && !sp.getItemInHand(hand).isEmpty())
				&& !sp.isSpectator() && storage(server.getBlockEntity(hit.getBlockPos()))) {
				opened(sp, server, hit.getBlockPos());
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(ChestNoise::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WOKEN.clear());
	}

	/** How loud {@code who} opens a chest: 1, less a share per rank of Light Fingers. */
	static float loudness(LivingEntity who) {
		return who instanceof Player p ? Math.max(0.0F, 1.0F - LIGHT_FINGERS_HUSH * Skills.rank(p, Skills.Perk.LIGHT_FINGERS)) : 1.0F;
	}

	/** (mixin) {@code who} starts opening a container (null: done). */
	public static void opening(@Nullable LivingEntity who) {
		opener = who;
	}

	/** (mixin) How loud a block sound played now is: quieter while a light-fingered thief opens a chest. */
	public static float hush() {
		LivingEntity who = opener;
		return who == null || who.level().isClientSide() ? 1.0F : loudness(who);
	}

	/** A chest, barrel, shulker box, or another mod's chest, barrel, crate or coffer (by its id). */
	private static boolean storage(BlockEntity entity) {
		if (entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity || entity instanceof ShulkerBoxBlockEntity) {
			return true;
		}
		if (entity == null) {
			return false;
		}
		String path = BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).getPath();
		return path.contains("chest") || path.contains("barrel") || path.contains("crate") || path.contains("coffer");
	}

	/** {@code player} opened the storage at {@code pos}: every sleeper in earshot rolls to wake. */
	private static void opened(ServerPlayer player, ServerLevel level, BlockPos pos) {
		Vec3 noise = Vec3.atCenterOf(pos);
		double quiet = (player.isShiftKeyDown() ? Skills.sneakFactor(player, SNEAK_FACTOR) : 1.0) * loudness(player);
		long now = level.getGameTime();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(RANGE),
			e -> e.isAlive() && e.isSleeping() && !(e instanceof Player))) {
			double d = e.position().distanceTo(noise);
			if (d > RANGE) {
				continue;
			}
			double chance = CLOSE_CHANCE * Mth.clamp((RANGE - d) / (RANGE - CLOSE_RANGE), 0.0, 1.0) * quiet;
			if (walled(level, noise, e)) {
				chance *= WALL_FACTOR;
			}
			if (level.getRandom().nextDouble() < chance) {
				wake(level, e, player, pos, now);
			}
		}
	}

	/** Something solid stands between the chest and the sleeper's head. */
	static boolean walled(ServerLevel level, Vec3 noise, LivingEntity sleeper) {
		Vec3 head = sleeper.getEyePosition();
		// (start just off the chest toward them, so the chest itself doesn't count)
		Vec3 from = noise.add(head.subtract(noise).normalize().scale(0.75));
		return level.clip(new ClipContext(from, head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sleeper)).getType() == HitResult.Type.BLOCK;
	}

	/** (Footsteps) {@code player}'s noise at {@code pos} wakes {@code sleeper}, as a chest's creak would. */
	static void wakeBy(ServerLevel level, LivingEntity sleeper, ServerPlayer player, BlockPos pos) {
		wake(level, sleeper, player, pos, level.getGameTime());
	}

	private static void wake(ServerLevel level, LivingEntity sleeper, ServerPlayer player, BlockPos pos, long now) {
		sleeper.stopSleeping();
		WOKEN.put(sleeper.getUUID(), new Woken(sleeper, player.getUUID(), pos.immutable(), now + STAY_AWAKE_TICKS));
		face(sleeper, player.getEyePosition());
		come(sleeper, pos);
		Speech.say(level, sleeper, Component.translatable("message.jaymoskingdoms.chest_noise.woke." + level.getRandom().nextInt(LINES)),
			Speech.WHITE, RANGE);
	}

	/** Turns {@code who}'s head and body toward {@code at} at once, so they see what's there this tick. */
	private static void face(LivingEntity who, Vec3 at) {
		Vec3 to = at.subtract(who.getEyePosition());
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float)(-(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG));
		who.setYRot(yaw);
		who.setYHeadRot(yaw);
		who.setYBodyRot(yaw);
		who.setXRot(pitch);
	}

	/** {@code who} goes over to see what the noise was. */
	private static void come(LivingEntity who, BlockPos pos) {
		if (who instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, pos, LOOK_SPEED, 2);
		} else if (who instanceof Mob mob) {
			mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
		}
	}

	/** Keeps the woken up, looking at the one who woke them while they're near. */
	private static void tick(MinecraftServer server) {
		if (WOKEN.isEmpty()) {
			return;
		}
		for (Iterator<Woken> it = WOKEN.values().iterator(); it.hasNext();) {
			Woken w = it.next();
			LivingEntity who = w.who();
			if (who.isRemoved() || !who.isAlive() || who.level().getGameTime() >= w.until()) {
				it.remove();
				continue;
			}
			if (who.isSleeping()) {
				// (their bed called them back too soon)
				who.stopSleeping();
				come(who, w.at());
			}
			Entity by = ((ServerLevel)who.level()).getEntity(w.by());
			if (who instanceof Mob mob && by != null && by.distanceTo(who) < RANGE) {
				mob.getLookControl().setLookAt(by);
			}
		}
	}

	/** {@code who} was woken by a noise and is still up for it (other systems may ask). */
	public static boolean roused(LivingEntity who) {
		return WOKEN.containsKey(who.getUUID());
	}
}
