package com.jaymospowers.power.web;

import com.jaymospowers.core.Powers;
import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.darktide.DarktideStatus;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hanging upside down, and webbing up from there. A slinger who holds still on a swing line fixed above them (a
 * ceiling, a branch, an overhang) turns head down for everyone to see ({@link WebPower#UPSIDE}); Jump and Sneak reel
 * them up and down as on any hanging line. While they hang, B on a creature below, in reach, webs it up to the ceiling:
 * <ul>
 * <li><b>Downed</b> (or a corpse): snatched up and stuck flat against the ceiling in cobwebs, until the webs go.</li>
 * <li><b>Standing</b>: hauled up on a line and left hanging upright from the ceiling, wrapped in cobwebs ({@link
 * WebHitches}, {@link WebTraps}); a player so caught can still cut the line by swinging at it.</li>
 * </ul>
 */
public final class WebHanging {
	// ================================================================================================================
	// Tuning
	/** B's reach for a creature below (blocks), and how far above it a ceiling is looked for. */
	static final double REACH = 9.0, CEILING_SCAN = 10.0;
	/** The least height above the ground-floor creature the slinger's anchor must be, and a cooldown (ticks). */
	static final int COOLDOWN = 50;
	/** Hauled up at this speed (blocks/tick); gives up after this many ticks. */
	static final double HAUL_SPEED = 0.55;
	static final int HAUL_GIVE_UP = 60;
	/** An upright catch hangs this far (blocks) under the ceiling; a flat one is pressed to it. */
	static final double UPRIGHT_GAP = 0.5, FLAT_GAP = 0.05;
	/** How long the cobwebs round a catch last (ticks), and how long a flat catch stays stuck. */
	static final int WEB_TICKS = 1200, STUCK_TICKS = 1200;
	/** A catch bigger than this (width x width x height, blocks cubed) is too heavy to haul. */
	static final double MAX_VOLUME = 14.0;
	/** The least a slinger's line must be above them (blocks) for the server to believe they hang from it. */
	static final double MIN_ABOVE = 1.0;
	// ================================================================================================================

	private record Haul(ResourceKey<Level> dimension, UUID target, UUID by, BlockPos ceiling, double ceilingY, boolean flat, int[] age) {
	}

	private record Stuck(ResourceKey<Level> dimension, UUID target, Vec3 at, long until) {
	}

	private static final List<Haul> HAULS = new ArrayList<>();
	private static final List<Stuck> STUCK = new ArrayList<>();
	private static final Map<ServerPlayer, Long> NEXT = new WeakHashMap<>();

	private WebHanging() {
	}

	static void register() {
		ServerPlayNetworking.registerGlobalReceiver(WebNet.Upside.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (payload.on() && Powers.has(player, WebPower.INSTANCE) && player.isAlive() && !player.isSpectator() && WebPower.swingingNow(player)
				&& !player.onGround()) {
				player.setAttached(WebPower.UPSIDE, true);
			} else {
				player.removeAttached(WebPower.UPSIDE);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(WebHanging::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HAULS.clear();
			STUCK.clear();
		});
	}

	/** Whether {@code player} hangs head down right now. */
	public static boolean hanging(Entity entity) {
		return entity.getAttached(WebPower.UPSIDE) != null;
	}

	/** B while hanging head down: webs up the creature below. True if B was used up by it (so Yank doesn't also go). */
	static boolean webUp(ServerPlayer player) {
		if (!hanging(player)) {
			return false;
		}
		if (!WebPower.swingingNow(player) || player.onGround()) {
			player.removeAttached(WebPower.UPSIDE);
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (NEXT.getOrDefault(player, 0L) > now) {
			return true;
		}
		Entity target = pick(player);
		if (target == null) {
			return false;
		}
		boolean flat = !(target instanceof LivingEntity living) || Downed.isDowned(living) || Finishers.helpless(living);
		// the ceiling over it
		double top = target.getBoundingBox().maxY;
		BlockPos from = BlockPos.containing(target.getX(), top + 0.1, target.getZ());
		BlockPos ceiling = null;
		for (int dy = 0; dy <= CEILING_SCAN; dy++) {
			BlockPos at = from.above(dy);
			if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
				ceiling = at;
				break;
			}
		}
		if (ceiling == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_up.nothing"));
			return true;
		}
		NEXT.put(player, now + COOLDOWN);
		HAULS.add(new Haul(level.dimension(), target.getUUID(), player.getUUID(), ceiling, ceiling.getY(), flat, new int[1]));
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		WebTraps.cling(level, BlockPos.containing(target.getX(), target.getY(), target.getZ()), WebSymbiote.suitShown(player), 40);
		Vec3 from3 = player.getEyePosition();
		Vec3 to3 = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.ITEM_COBWEB, to3.x, to3.y, to3.z, 14, 0.3, 0.5, 0.3, 0.03);
		level.playSound(null, from3.x, from3.y, from3.z, SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.9F, 1.7F);
		level.playSound(null, to3.x, to3.y, to3.z, SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		return true;
	}

	/** The creature (or corpse) under the crosshair, else the nearest one below in reach. */
	private static @Nullable Entity pick(ServerPlayer player) {
		Entity seen = WebPower.entityInSight(player, REACH);
		if (valid(player, seen)) {
			return seen;
		}
		Entity best = null;
		double bestD = Double.MAX_VALUE;
		AABB box = player.getBoundingBox().inflate(4.0, REACH, 4.0).move(0.0, -REACH * 0.5, 0.0);
		for (Entity e : player.level().getEntities(player, box, e -> valid(player, e))) {
			if (e.getY() >= player.getY()) {
				continue;
			}
			double d = e.distanceToSqr(player);
			if (d < bestD) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	private static boolean valid(ServerPlayer player, @Nullable Entity e) {
		if (e == null || e == player || !e.isAlive() || e.isSpectator() || e.hasPassenger(player) || e.getVehicle() != null) {
			return false;
		}
		if (!(e instanceof LivingEntity) && !com.jaymospowers.power.gravity.GravityPower.isCorpse(e)) {
			return false;
		}
		if (e instanceof Player other && !player.canHarmPlayer(other)) {
			return false;
		}
		if (e.getBbWidth() * e.getBbWidth() * e.getBbHeight() > MAX_VOLUME || e.distanceTo(player) > REACH + 2.0) {
			return false;
		}
		return !stuck(e) && !hitched(e);
	}

	private static boolean hitched(Entity e) {
		return e.hasAttached(WebPower.HITCH);
	}

	private static boolean stuck(Entity e) {
		for (Stuck s : STUCK) {
			if (s.target().equals(e.getUUID())) {
				return true;
			}
		}
		for (Haul h : HAULS) {
			if (h.target().equals(e.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code e} is stuck flat on a ceiling or being hauled to one. */
	public static boolean webbedUp(Entity e) {
		return stuck(e);
	}

	private static void tick(MinecraftServer server) {
		// a hanging slinger who no longer has a line out comes upright
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (hanging(player) && (!WebPower.swingingNow(player) || player.onGround() || !player.isAlive())) {
				player.removeAttached(WebPower.UPSIDE);
			}
		}
		for (Iterator<Haul> it = HAULS.iterator(); it.hasNext(); ) {
			Haul haul = it.next();
			ServerLevel level = server.getLevel(haul.dimension());
			Entity target = level == null ? null : level.getEntity(haul.target());
			ServerPlayer by = level == null ? null : level.getServer().getPlayerList().getPlayer(haul.by());
			if (target == null || !target.isAlive() || by == null || haul.age()[0]++ > HAUL_GIVE_UP) {
				it.remove();
				if (target != null) {
					target.setNoGravity(false);
				}
				continue;
			}
			double gap = haul.flat() ? FLAT_GAP : UPRIGHT_GAP;
			double goal = haul.ceilingY() - target.getBbHeight() - gap;
			double rise = goal - target.getY();
			if (rise <= 0.08 || target.getBoundingBox().maxY >= haul.ceilingY() - gap) {
				it.remove();
				arrive(level, by, target, haul);
				continue;
			}
			target.setNoGravity(true);
			Vec3 v = new Vec3(0.0, Math.min(HAUL_SPEED, rise), 0.0);
			target.setDeltaMovement(v);
			target.needsSync = true;
			target.syncVelocity = true;
			if (target instanceof ServerPlayer sp) {
				ServerPlayNetworking.send(sp, new WebNet.Nudge(v));
			}
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			if (target instanceof LivingEntity living) {
				DarktideStatus.stun(living, 10);
			}
			if (level.getGameTime() % 3 == 0) {
				Vec3 at = target.getBoundingBox().getCenter();
				level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 2, 0.2, 0.4, 0.2, 0.01);
			}
		}
		for (Iterator<Stuck> it = STUCK.iterator(); it.hasNext(); ) {
			Stuck s = it.next();
			ServerLevel level = server.getLevel(s.dimension());
			Entity target = level == null ? null : level.getEntity(s.target());
			if (target == null || !target.isAlive() || level.getGameTime() >= s.until()) {
				it.remove();
				if (target != null) {
					target.setNoGravity(false);
				}
				continue;
			}
			// pinned flat where it was stuck
			target.setNoGravity(true);
			target.setDeltaMovement(Vec3.ZERO);
			if (target.position().distanceToSqr(s.at()) > 0.01) {
				target.setPos(s.at());
			}
			if (target instanceof ServerPlayer sp && level.getGameTime() % 4 == 0) {
				ServerPlayNetworking.send(sp, new WebNet.Nudge(Vec3.ZERO));
			}
		}
	}

	/** The catch has reached the ceiling: stuck flat in cobwebs, or left on a line, hanging upright. */
	private static void arrive(ServerLevel level, ServerPlayer by, Entity target, Haul haul) {
		boolean dark = WebSymbiote.suitShown(by);
		target.setDeltaMovement(Vec3.ZERO);
		target.setNoGravity(false);
		Vec3 anchor = new Vec3(target.getX(), haul.ceilingY(), target.getZ());
		if (haul.flat()) {
			target.setNoGravity(true);
			STUCK.add(new Stuck(haul.dimension(), target.getUUID(), target.position(), level.getGameTime() + STUCK_TICKS));
			web(level, target, dark);
		} else if (target instanceof LivingEntity living) {
			WebHitches.hitch(level, by, living, anchor, haul.ceiling(), Direction.DOWN);
			WebTraps.ensnare(level, living, WebBall.SLOWNESS_AMPLIFIER, WebBall.SLOWNESS_MAX_TICKS, dark);
			DarktideStatus.stun(living, WebTakedowns.PIN_TICKS);
			web(level, target, dark);
		}
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(dark ? WebSymbiote.dust(1.4F) : ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 26, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	/** Cobwebs in every open cell the catch fills, and round it. */
	private static void web(ServerLevel level, Entity target, boolean dark) {
		AABB box = target.getBoundingBox();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX - 0.3, box.minY, box.minZ - 0.3),
			BlockPos.containing(box.maxX + 0.3, box.maxY, box.maxZ + 0.3))) {
			WebTraps.cling(level, pos.immutable(), dark, WEB_TICKS);
		}
	}
}
