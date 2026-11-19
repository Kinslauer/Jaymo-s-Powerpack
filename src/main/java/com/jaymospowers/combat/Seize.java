package com.jaymospowers.combat;

import com.jaymospowers.core.Carry;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.darktide.DarktideStatus;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hold them for the beating, for everyone: sneak + use (right-click) on a hostile, person-sized thing within reach takes hold of
 * it and holds it {@link #DISTANCE} blocks in front of you for {@link #HOLD_TICKS} (3 s) while your attacks land. It faces you
 * and can't move, act or hit anyone. When it ends (the time, another sneak + use, or you or it going down, dying or leaving) a
 * {@link #LOCKED_TICKS} (3 s) window follows in which you can't grab again. Players, villagers, anything you could lift
 * ({@link Carry#canBeCarriedBy}: sneak + hold use) and anything too big or small to be a person are left to the other
 * sneak + use moves; a gauge in the action bar counts the hold down.
 */
public final class Seize {
	// ---- tuning ----
	/** How long the hold lasts, and the window after it before another grab (ticks). */
	public static final int HOLD_TICKS = 60, LOCKED_TICKS = 60;
	/** How far in front of you it is held (blocks), and how far you may grab from (blocks, from the edge of its box). */
	public static final double DISTANCE = 1.8, REACH = 3.0;
	/** The size of a person-sized thing (blocks): height from, to, and the widest. */
	public static final double MIN_HEIGHT = 1.4, MAX_HEIGHT = 2.4, MAX_WIDTH = 1.1;
	/** The action bar gauge's length. */
	private static final int GAUGE = 10;

	private record Hold(UUID foe, long since) {
	}

	private static final Map<UUID, Hold> HOLDS = new HashMap<>();
	private static final Set<UUID> HELD_FOES = new HashSet<>();
	private static final Map<UUID, Long> LOCKED = new HashMap<>();

	private Seize() {
	}

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer holder) || !holder.isShiftKeyDown()
				|| !(entity instanceof LivingEntity foe)) {
				return InteractionResult.PASS;
			}
			if (HOLDS.containsKey(holder.getUUID())) {
				release(holder);
				return InteractionResult.SUCCESS;
			}
			if (!canSeize(holder, foe)) {
				return InteractionResult.PASS;
			}
			seize(holder, foe);
			return InteractionResult.SUCCESS;
		});
		// what is held can't hurt anyone
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			var attacker = source.getEntity();
			var direct = source.getDirectEntity();
			return !(attacker != null && HELD_FOES.contains(attacker.getUUID()) || direct != null && HELD_FOES.contains(direct.getUUID()));
		});
		ServerTickEvents.END_SERVER_TICK.register(Seize::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HOLDS.clear();
			HELD_FOES.clear();
			LOCKED.clear();
		});
	}

	/** Whether {@code holder} is holding someone now. */
	public static boolean holding(Player holder) {
		return HOLDS.containsKey(holder.getUUID());
	}

	/** Whether {@code foe} is held by someone now. */
	public static boolean held(LivingEntity foe) {
		return HELD_FOES.contains(foe.getUUID());
	}

	static boolean canSeize(ServerPlayer holder, LivingEntity foe) {
		if (foe instanceof Player || foe instanceof ArmorStand || !foe.isAlive() || foe.isSpectator() || foe.isPassenger() || foe.isVehicle()
			|| HELD_FOES.contains(foe.getUUID()) || !(foe instanceof Enemy || foe instanceof Mob mob && mob.getTarget() == holder)) {
			return false;
		}
		if (holder.isSpectator() || Downed.isDowned(holder) || Downed.isDowned(foe) || Carry.isCarried(holder) || Carry.isCarrying(holder)
			|| Carry.canBeCarriedBy(holder, foe) || Stances.immovable(foe) || holder.getMainHandItem().is(Items.NAME_TAG) || holder.getMainHandItem().is(Items.LEAD)) {
			return false;
		}
		AABB box = foe.getBoundingBox();
		if (box.getYsize() < MIN_HEIGHT || box.getYsize() > MAX_HEIGHT || box.getXsize() > MAX_WIDTH || box.getZsize() > MAX_WIDTH) {
			return false;
		}
		long now = holder.level().getGameTime();
		return now >= LOCKED.getOrDefault(holder.getUUID(), 0L) && Math.sqrt(box.distanceToSqr(holder.getEyePosition())) <= REACH;
	}

	private static void seize(ServerPlayer holder, LivingEntity foe) {
		ServerLevel level = holder.level();
		HOLDS.put(holder.getUUID(), new Hold(foe.getUUID(), level.getGameTime()));
		HELD_FOES.add(foe.getUUID());
		holder.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 0.7F);
		pin(foe);
	}

	/** Holds {@code foe} still this tick: its stride stopped, its mind off the fight. */
	private static void pin(LivingEntity foe) {
		DarktideStatus.stun(foe, 4);
		foe.setDeltaMovement(0.0, Math.min(0.0, foe.getDeltaMovement().y), 0.0);
		foe.syncVelocity = true;
		if (foe instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.setTarget(null);
		}
	}

	private static void tick(MinecraftServer server) {
		if (HOLDS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Hold>> it = HOLDS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Hold> entry = it.next();
			ServerPlayer holder = server.getPlayerList().getPlayer(entry.getKey());
			Hold hold = entry.getValue();
			LivingEntity foe = holder != null && holder.level().getEntity(hold.foe()) instanceof LivingEntity living ? living : null;
			long now = holder == null ? 0L : holder.level().getGameTime();
			boolean fine = holder != null && foe != null && holder.isAlive() && foe.isAlive() && foe.level() == holder.level() && !holder.isSpectator()
				&& !Downed.isDowned(holder) && !Downed.isDowned(foe) && !Carry.isCarried(holder) && !Carry.isCarrying(holder)
				&& now - hold.since() < HOLD_TICKS && holder.distanceTo(foe) < DISTANCE + 3.0;
			if (!fine) {
				it.remove();
				finish(entry.getKey(), hold, holder, foe);
				continue;
			}
			hang(holder, foe);
			pin(foe);
			int left = Mth.ceil(GAUGE * (1.0F - (float) (now - hold.since()) / HOLD_TICKS));
			holder.sendOverlayMessage(Component.translatable("message.jaymospowers.seize.holding", "=".repeat(left) + "-".repeat(GAUGE - left))
				.withStyle(ChatFormatting.GOLD));
		}
	}

	/** Puts {@code foe} {@link #DISTANCE} ahead of {@code holder}, facing them, unless something is in the way. */
	private static void hang(ServerPlayer holder, LivingEntity foe) {
		Vec3 look = holder.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, holder.getYRot()) : flat.normalize();
		Vec3 at = holder.position().add(flat.scale(DISTANCE * holder.getScale()));
		Vec3 face = holder.position().subtract(at);
		float yaw = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90.0F;
		if (holder.level().noCollision(foe, foe.getBoundingBox().move(at.subtract(foe.position())))) {
			foe.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		} else {
			foe.setYRot(yaw);
		}
		foe.setYHeadRot(yaw);
		foe.setYBodyRot(yaw);
		foe.needsSync = true;
		foe.resetFallDistance();
	}

	private static void finish(UUID holderId, Hold hold, @Nullable ServerPlayer holder, @Nullable LivingEntity foe) {
		HELD_FOES.remove(hold.foe());
		if (holder != null) {
			LOCKED.put(holderId, holder.level().getGameTime() + LOCKED_TICKS);
		}
		if (foe != null && foe.isAlive()) {
			foe.setDeltaMovement(Vec3.ZERO);
			foe.syncVelocity = true;
		}
	}

	/** Lets go early. */
	private static void release(ServerPlayer holder) {
		Hold hold = HOLDS.remove(holder.getUUID());
		if (hold != null) {
			finish(holder.getUUID(), hold, holder, holder.level().getEntity(hold.foe()) instanceof LivingEntity foe ? foe : null);
		}
	}
}
