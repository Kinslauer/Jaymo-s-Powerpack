package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Latch and ride: sprint and press G at something living and the suit leaps onto its back and stays there, the wearer
 * carried along on it (pinned to its back every tick, the inverse of {@link WebHeldGrab}'s pin: the wearer is steered to
 * the mount, not the mount to the hand), for up to {@link #MAX_TICKS}. Left click bites and claws it on a short cooldown;
 * sneaking or G lets go.
 *
 * <p>Thin suit or Living Armor (not the big suit): the <b>latch</b>, onto something at least as big as a player; with the
 * tendrils out the hand takes anything up to a cow, so the latch is for what is too big for it (with the tendrils in, any
 * such target). The big suit: the <b>ride and maul</b>, onto anything bigger than a cow (a bounding box over the hand's
 * limit); the bites are heavier, and the mount's movement is only ridden, never steered. The wither and the ender dragon
 * are refused, and so is any player. Nothing is fed on: the bites only hurt.
 *
 * <p>The {@link #LATCH} attachment is synced, and everyone sees the clinging body (WebHeldGrabPose draws it).
 */
public final class WebLatch {
	/** How far (blocks from the eyes, plus half its width) something can be to leap onto it. */
	public static final double REACH = 6.0;
	/** Longest the wearer stays on before the hold gives out (ticks): 12 seconds. */
	public static final int MAX_TICKS = 240;
	/** Sneaking only lets go after this long (so the press that leapt doesn't). */
	static final int SNEAK_GRACE_TICKS = 10;
	/** The smallest a latch target is (a bounding box volume): about a player's. */
	static final double MIN_VOLUME = 0.6 * 1.8 * 0.6 * 0.9;
	/** The bite: damage (the latch), the maul's damage (the ride), and ticks between bites. */
	static final float BITE_DAMAGE = 3.5F, MAUL_DAMAGE = 7.0F;
	public static final int BITE_TICKS = 14, MAUL_TICKS = 18;
	/** How long the bite's animation plays (ticks); the pose uses it. */
	public static final int BITE_ANIM_TICKS = 10;
	/** Pinned to the mount: each tick the wearer closes this share of the gap (at most {@code PIN_MAX_SPEED} blocks per tick, the leap on); past {@code PIN_SNAP} blocks they're just put there. */
	static final double PIN_STEER = 0.8, PIN_MAX_SPEED = 1.6, PIN_SNAP = 5.0;
	/** Further than this from the mount and the hold has lost them. */
	static final double LOSE_RANGE = 12.0;
	/** A wide, low mount (a bear, a ravager: wider than this share of its height) is ridden on top; a tall one is clung to from behind. */
	static final double SQUAT_SHARE = 0.75;
	/** Clinging on a tall mount: how far behind it (past half its width), and how high up it (the share of its height the wearer's feet are at). */
	static final double CLING_BEHIND = 0.25, CLING_HEIGHT = 0.35;
	/** Riding on top: how far behind its middle (the share of its width), and how far under its top the wearer's feet are. */
	static final double RIDE_BEHIND = 0.1, RIDE_SINK = 0.3;
	/** Let go: a hop off backwards and up. */
	static final double HOP_BACK = 0.35, HOP_UP = 0.4;

	/** What the wearer is latched to (an entity id), since when (game time), whether it's the ride and maul, and when the last bite began. Synced to everyone. */
	public record Latch(int entity, long since, boolean ride, long biteStart) {
		public static final StreamCodec<ByteBuf, Latch> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Latch::entity, ByteBufCodecs.VAR_LONG, Latch::since,
			ByteBufCodecs.BOOL, Latch::ride, ByteBufCodecs.VAR_LONG, Latch::biteStart, Latch::new);
	}

	public static final AttachmentType<Latch> LATCH = AttachmentRegistry.create(JaymosPowers.id("web_latch"),
		builder -> builder.syncWith(Latch.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private WebLatch() {
	}

	/** The wearer is latched onto something. Both sides. */
	public static boolean latched(Player player) {
		return player.getAttached(LATCH) != null;
	}

	/** What the wearer is latched onto (null for nothing, or something gone). Both sides. */
	public static @Nullable LivingEntity mount(Player player) {
		Latch latch = player.getAttached(LATCH);
		return latch != null && player.level().getEntity(latch.entity()) instanceof LivingEntity living ? living : null;
	}

	private static double volume(Entity entity) {
		AABB box = entity.getBoundingBox();
		return box.getXsize() * box.getYsize() * box.getZsize();
	}

	/** Whether the wearer would leap onto this: right suit, right size, in reach, not refused. Both sides (the client picks with it, the server checks). */
	public static boolean canLatch(Player player, @Nullable Entity target) {
		if (!(target instanceof LivingEntity living) || living == player || !living.isAlive() || living.isSpectator() || living instanceof Player
			|| living instanceof ArmorStand || living instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
			|| living instanceof net.minecraft.world.entity.boss.wither.WitherBoss || WebHeldGrab.heldByAnyone(living) || WebTendrils.held(living)
			|| latched(player) || WebHeldGrab.holding(player) || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (living.getBoundingBox().getCenter().distanceTo(player.getEyePosition()) > REACH + living.getBbWidth() * 0.5 + 0.5) {
			return false;
		}
		double volume = volume(living);
		if (WebSymbiote.bulked(player)) {
			return volume > WebHeldGrab.GRAB_MAX_VOLUME;
		}
		return (WebSymbiote.suitShown(player) || WebSymbiote.livingArmor(player)) && volume >= MIN_VOLUME
			&& (!WebSymbiote.tendrilKeys(player) || volume > WebHeldGrab.GRAB_MAX_VOLUME);
	}

	/** What a sprinting G would leap onto: the latchable thing nearest the line of sight. Both sides. */
	public static @Nullable LivingEntity aimed(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		LivingEntity best = null;
		double bestDot = 0.9;
		List<Entity> near = player.level().getEntities(player, player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(REACH * 0.5 + 2.0),
			e -> canLatch(player, e));
		for (Entity e : near) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double dot = to.normalize().dot(look);
			if (dot > bestDot && e instanceof LivingEntity living) {
				bestDot = dot;
				best = living;
			}
		}
		return best;
	}

	/** G while sprinting at {@code target}: leap onto it. False if the latch is not available (the press goes on to the usual grab). */
	static boolean start(ServerPlayer player, @Nullable Entity target) {
		if (!player.isSprinting() || WebSymbiote.driving(player) || !canLatch(player, target) || !(target instanceof LivingEntity living)) {
			return false;
		}
		ServerLevel level = player.level();
		boolean ride = WebSymbiote.bulked(player);
		player.setAttached(LATCH, new Latch(living.getId(), level.getGameTime(), ride, level.getGameTime() - BITE_TICKS * 2L));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.9F, 0.7F);
		com.jaymospowers.core.Shakes.send(player, ride ? 0.6F : 0.35F, living.position());
		WebSymbiote.narrate(player, "seize");
		return true;
	}

	/** Lets go (a hop off backwards, if {@code hop}). */
	static void release(ServerPlayer player, boolean hop) {
		Latch latch = player.getAttached(LATCH);
		if (latch == null) {
			return;
		}
		player.removeAttached(LATCH);
		if (hop && player.isAlive()) {
			LivingEntity mount = player.level().getEntity(latch.entity()) instanceof LivingEntity living ? living : null;
			Vec3 back = mount == null ? player.getLookAngle().horizontal().scale(-1.0) : player.position().subtract(mount.position()).multiply(1.0, 0.0, 1.0);
			back = back.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 0.0) : back.normalize();
			Vec3 velocity = back.scale(HOP_BACK).add(0.0, HOP_UP, 0.0);
			player.setDeltaMovement(velocity);
			ServerPlayNetworking.send(player, new WebNet.Nudge(velocity));
		}
	}

	/** Left click while latched: a bite and a claw at the mount (it never feeds on the living). */
	static void bite(ServerPlayer player) {
		Latch latch = player.getAttached(LATCH);
		LivingEntity mount = mount(player);
		if (latch == null || mount == null || !mount.isAlive() || !player.isAlive()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now - latch.biteStart() < (latch.ride() ? MAUL_TICKS : BITE_TICKS) || now - latch.since() < 2) {
			return;
		}
		player.setAttached(LATCH, new Latch(latch.entity(), latch.since(), latch.ride(), now));
		mount.hurtServer(level, level.damageSources().playerAttack(player), latch.ride() ? MAUL_DAMAGE : BITE_DAMAGE);
		Vec3 at = player.getEyePosition().lerp(mount.getBoundingBox().getCenter(), 0.5);
		level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(WebTendrilCombos.BLOOD, latch.ride() ? 1.4F : 1.0F), at.x, at.y, at.z,
			latch.ride() ? 22 : 12, 0.3, 0.3, 0.3, 0.0);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.1F, latch.ride() ? 0.5F : 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.6F, 1.2F);
		com.jaymospowers.core.Shakes.send(player, latch.ride() ? 0.7F : 0.45F, at);
		WebSymbiote.narrate(player, "maul");
	}

	/** Where the wearer's feet go on this mount: on top of a wide low one, up its back for a tall one. */
	static Vec3 spot(Player player, LivingEntity mount, boolean ride) {
		double yaw = Math.toRadians(mount.yBodyRot);
		Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		double scale = player.getScale();
		if (mount.getBbWidth() >= mount.getBbHeight() * SQUAT_SHARE) {
			return mount.position().subtract(forward.scale(RIDE_BEHIND * mount.getBbWidth())).add(0.0, mount.getBbHeight() - RIDE_SINK * scale, 0.0);
		}
		return mount.position().subtract(forward.scale(mount.getBbWidth() * 0.5 + CLING_BEHIND * scale)).add(0.0, mount.getBbHeight() * CLING_HEIGHT, 0.0);
	}

	/** Every tick, per web slinger (from {@link WebHeldGrab#tick}): the wearer is steered to the mount's back and held there. */
	static void tick(ServerPlayer player) {
		Latch latch = player.getAttached(LATCH);
		if (latch == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		LivingEntity mount = mount(player);
		boolean bulked = WebSymbiote.bulked(player);
		if (!player.isAlive() || player.isSpectator() || mount == null || !mount.isAlive() || mount.level() != level || WebSymbiote.driving(player)
			|| now - latch.since() > MAX_TICKS || mount.distanceTo(player) > LOSE_RANGE || bulked != latch.ride()
			|| !(bulked || WebSymbiote.suitShown(player) || WebSymbiote.livingArmor(player))) {
			release(player, true);
			return;
		}
		if (player.isShiftKeyDown() && now - latch.since() > SNEAK_GRACE_TICKS) {
			release(player, true);
			return;
		}
		Vec3 spot = spot(player, mount, latch.ride());
		Vec3 gap = spot.subtract(player.position());
		if (gap.length() > PIN_SNAP) {
			player.teleportTo(level, spot.x, spot.y, spot.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
			player.setDeltaMovement(Vec3.ZERO);
		} else {
			Vec3 v = gap.scale(PIN_STEER);
			double speed = v.length();
			if (speed > PIN_MAX_SPEED) {
				v = v.scale(PIN_MAX_SPEED / speed);
			}
			// (a player's motion is their own client's: it's told every tick)
			v = v.add(mount.getDeltaMovement().scale(0.5));
			player.setDeltaMovement(v);
			ServerPlayNetworking.send(player, new WebNet.Nudge(v));
		}
		player.resetFallDistance();
	}

	/** Whether the bite just played is still animating, for the pose: ticks since it began, or -1. */
	public static float biteTicks(Player player, float partialTick) {
		Latch latch = player.getAttached(LATCH);
		if (latch == null) {
			return -1.0F;
		}
		float t = player.level().getGameTime() + partialTick - latch.biteStart();
		return t >= 0.0F && t < BITE_ANIM_TICKS ? t : -1.0F;
	}
}
