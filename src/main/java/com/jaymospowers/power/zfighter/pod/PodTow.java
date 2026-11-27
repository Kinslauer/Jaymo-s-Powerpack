package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tow a friend home (Patch 9, tr_tow). A pod that crashed (hull gone in the crossing, or lost in an ambush) stands where it came
 * down with an empty tank, tagged {@link #CRASHED_TAG} and with its pilot's name on it. A friend whose own pod has a TOW_LINE
 * module sneaks at the crashed pod with an empty hand to make the line fast ({@link #hook}); on their next trip, which must be
 * Home, the crashed pod goes with them ({@link #take}, a little more fuel, a slower crossing) and stands beside them when they
 * land ({@link #land}). Its pilot, if they stood by it when it was taken, is brought down with it.
 */
public final class PodTow {
	// ---- tuning ----
	/** The tag a crashed pod carries, and the start of the tag naming its pilot. */
	public static final String CRASHED_TAG = "jaymospowers.crashed", OWNER_PREFIX = "jaymospowers.crashed_owner.";
	/** How near (blocks) a pilot's own pod must be to make a line fast; how far the towed pod may be from it when it sets out; how near its pilot must stand to ride along. */
	static final double HOOK_RANGE = 14.0, TOW_RANGE = 24.0, BOARD_RANGE = 6.0;
	/** What a tow costs on top of the trip (fuel), and how much slower it crosses (share of the speed). */
	public static final int EXTRA_FUEL = 1;
	public static final double SLOW = 0.8;

	/** A towed pod's record: what the pod that lands is made from, and who rides home with it. */
	public record Towed(PodEntity.Extras extras, int filled, int damaged, boolean finished, int grade, boolean lens, int fuelGrade, float yaw, @Nullable UUID owner,
		@Nullable UUID rider) {
	}

	/** Pilot to the crashed pod they made fast. */
	private static final Map<UUID, UUID> HOOKED = new HashMap<>();

	private PodTow() {
	}

	/** Whether {@code pod} crashed and waits for a tow. */
	public static boolean crashed(PodEntity pod) {
		return pod.entityTags().contains(CRASHED_TAG);
	}

	/** Marks {@code pod} crashed, its pilot {@code owner}'s. */
	public static void mark(PodEntity pod, UUID owner) {
		pod.addTag(CRASHED_TAG);
		pod.addTag(OWNER_PREFIX + owner);
	}

	/** Takes the crash off {@code pod} (it is mended, or towed home). */
	public static void clear(PodEntity pod) {
		pod.removeTag(CRASHED_TAG);
		for (String tag : List.copyOf(pod.entityTags())) {
			if (tag.startsWith(OWNER_PREFIX)) {
				pod.removeTag(tag);
			}
		}
	}

	private static @Nullable UUID ownerOf(PodEntity pod) {
		for (String tag : pod.entityTags()) {
			if (tag.startsWith(OWNER_PREFIX)) {
				try {
					return UUID.fromString(tag.substring(OWNER_PREFIX.length()));
				} catch (IllegalArgumentException e) {
					return null;
				}
			}
		}
		return null;
	}

	/** {@code player} sneaking at {@code crashed} with an empty hand: makes the line of their nearest towing pod fast to it. False if it is not a crashed pod at all. */
	static boolean hook(ServerPlayer player, PodEntity crashed) {
		if (!crashed(crashed)) {
			return false;
		}
		PodEntity own = null;
		double best = HOOK_RANGE * HOOK_RANGE;
		for (PodEntity pod : player.level().getEntitiesOfClass(PodEntity.class, player.getBoundingBox().inflate(HOOK_RANGE))) {
			double d = pod.distanceToSqr(player);
			if (pod != crashed && !pod.locked() && !crashed(pod) && PodModules.has(pod, PodModules.Kind.TOW_LINE) && d < best) {
				best = d;
				own = pod;
			}
		}
		if (own == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.tow_no_line").withStyle(ChatFormatting.GRAY));
			return true;
		}
		HOOKED.put(player.getUUID(), crashed.getUUID());
		ServerLevel level = player.level();
		Vec3 mid = own.position().add(crashed.position()).scale(0.5).add(0.0, 1.0, 0.0);
		MagicFx.stroke(level, own.position().add(0.0, 1.0, 0.0), crashed.position().add(0.0, 1.0, 0.0), 0.1F, MagicFx.CRACKLE_COLOR);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.tow_fast").withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** The crashed pod {@code player} has made fast, if it is still there and near {@code own}, and {@code own} still has its tow line. */
	public static @Nullable PodEntity hooked(ServerPlayer player, PodEntity own) {
		UUID id = HOOKED.get(player.getUUID());
		if (id == null) {
			return null;
		}
		Entity found = player.level().getEntity(id);
		if (found instanceof PodEntity pod && pod.isAlive() && crashed(pod) && PodModules.has(own, PodModules.Kind.TOW_LINE) && pod.distanceTo(own) <= TOW_RANGE) {
			return pod;
		}
		HOOKED.remove(player.getUUID());
		return null;
	}

	/** The line is cast off. */
	public static void release(ServerPlayer player) {
		HOOKED.remove(player.getUUID());
	}

	/** Takes {@code towed} away with {@code player}: its record, and it leaves the world. */
	public static Towed take(ServerPlayer player, PodEntity towed) {
		UUID owner = ownerOf(towed);
		UUID rider = null;
		if (owner != null && !owner.equals(player.getUUID())) {
			ServerPlayer friend = player.level().getServer().getPlayerList().getPlayer(owner);
			if (friend != null && friend.level() == towed.level() && friend.distanceTo(towed) <= BOARD_RANGE) {
				rider = owner;
			}
		}
		Towed record = new Towed(towed.extras(), towed.filled(), towed.damagedMask(), towed.finished(), towed.grade(), towed.lens(), towed.fuelGrade(), towed.getYRot(), owner,
			rider);
		HOOKED.remove(player.getUUID());
		towed.discard();
		return record;
	}

	/** The towed pod stands at {@code feet} in {@code level}, empty of fuel and no longer crashed; its pilot, if they came, comes down beside it. */
	public static PodEntity land(ServerLevel level, Vec3 feet, Towed towed) {
		PodEntity pod = PodEntity.spawn(level, feet, false, 0);
		pod.setFuelGrade(towed.fuelGrade());
		pod.setParts(towed.filled(), towed.damaged());
		pod.setFinished(towed.finished());
		pod.setGrade(towed.grade());
		pod.setLens(towed.lens());
		pod.applyExtras(towed.extras());
		pod.setFuel(0);
		pod.setYRot(towed.yaw());
		if (towed.rider() != null) {
			ServerPlayer friend = level.getServer().getPlayerList().getPlayer(towed.rider());
			if (friend != null) {
				friend.teleportTo(level, feet.x + 2.5, feet.y, feet.z, Set.of(), friend.getYRot(), friend.getXRot(), true);
				friend.resetFallDistance();
				friend.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.tow_home").withStyle(ChatFormatting.GOLD));
			}
		}
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0F, 0.9F);
		return pod;
	}
}
