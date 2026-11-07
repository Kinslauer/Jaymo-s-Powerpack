package com.jaymospowers.freerun;

import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.stamina.Dodge;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.15.67) The landing roll (Andy: "your roll negates fall damage if done while landing, give it a graceful window"): a
 * dodge ({@link Dodge}) made as you land takes all the fall damage away. The window is wide both ways:
 * <ul>
 * <li>early: the dodge (Sneak tapped twice, or the dodge key) pressed in the air up to {@link #EARLY_TICKS} before
 * touchdown is held by the client ({@code DodgeClient}) and rolls you the moment you land;</li>
 * <li>late: fall damage is held back {@link #LATE_TICKS} (plus {@link #SLACK_TICKS} for the network) after touchdown;
 * a dodge in that time cancels it, otherwise it lands as it would have, a blink late.</li>
 * </ul>
 * The roll is the ordinary dodge, so it costs its feathers; with none left there is no roll and the fall hurts.
 * <p>(Motion patch 0) Everyone's now (Andy: "give everyone the dodge roll on landing, i.e. remove it from the tree, but
 * keep the skillcheck"): Sure Footing left Kingdoms' Freerun tree. And a hard fall that isn't rolled lands in the
 * three-point <b>hero landing</b> ({@link WebPose#HERO_LAND}, drawn for everyone), with a ring of dust kicked out of
 * the ground: fall damage of {@link #HERO_DAMAGE} or more that you'd live through. A roll after it takes its place.
 */
public final class LandingRoll {
	// ===== the knobs ================================================================================================
	/** Client: a dodge pressed this many ticks (0.5 s) before landing still rolls you as you land. */
	public static final int EARLY_TICKS = 10;
	/** Server: a dodge this many ticks (0.3 s) after landing still takes the fall damage away. */
	public static final int LATE_TICKS = 6;
	/** Server: extra ticks for the client's dodge to arrive. */
	static final int SLACK_TICKS = 4;
	/** (Motion patch 0) Fall damage at least this (hearts x2) that you'd survive lands you in the hero landing. */
	public static final float HERO_DAMAGE = 6.0F;
	/** The hero landing's ring of dust: how wide (blocks) and how many puffs round it. */
	static final double DUST_RADIUS = 1.6;
	static final int DUST_PUFFS = 18;
	// ================================================================================================================

	private record Pending(DamageSource source, float amount, long at) {
	}

	/** Server: fall damage held back, by player. */
	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	/** Server: game time of each player's last dodge. */
	private static final Map<UUID, Long> ROLLED = new HashMap<>();
	/** True while held-back damage is dealt, so it isn't held back again. */
	private static boolean dealing;

	private LandingRoll() {
	}

	/** After {@link Freerun}'s own fall rule (Leap of Faith), so a soft landing is never held back. */
	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (dealing || !(entity instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL)) {
				return true;
			}
			long now = player.level().getGameTime();
			Long rolled = ROLLED.get(player.getUUID());
			if (rolled != null && now - rolled <= SLACK_TICKS) {
				// the roll got here first
				rolled(player);
				return false;
			}
			PENDING.put(player.getUUID(), new Pending(source, amount, now));
			if (amount >= HERO_DAMAGE && amount < player.getHealth() + player.getAbsorptionAmount()) {
				heroLanding(player);
			}
			return false;
		});
		Dodge.onDodge((player, direction) -> {
			long now = player.level().getGameTime();
			ROLLED.put(player.getUUID(), now);
			Pending pending = PENDING.remove(player.getUUID());
			if (pending != null) {
				// rolled out of it: the hero landing that started at touchdown gives way to the roll
				WebPose pose = player.getAttached(WebPower.POSE);
				if (pose != null && pose.kind() == WebPose.HERO_LAND && now - pose.start() <= LATE_TICKS + SLACK_TICKS) {
					player.removeAttached(WebPower.POSE);
				}
				rolled(player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(LandingRoll::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			// leaving doesn't dodge the fall
			Pending pending = PENDING.remove(handler.player.getUUID());
			if (pending != null) {
				deal(handler.player, pending);
			}
			ROLLED.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PENDING.clear();
			ROLLED.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Pending> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			if (player.level().getGameTime() - entry.getValue().at() > LATE_TICKS + SLACK_TICKS) {
				it.remove();
				deal(player, entry.getValue());
			}
		}
	}

	private static void deal(ServerPlayer player, Pending pending) {
		if (!player.isAlive()) {
			return;
		}
		dealing = true;
		try {
			player.hurtServer(player.level(), pending.source(), pending.amount());
		} finally {
			dealing = false;
		}
	}

	/** The fall rolled off: a thud, a puff of dust, and a little Stealth experience. */
	private static void rolled(ServerPlayer player) {
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SMALL_FALL, SoundSource.PLAYERS, 0.8F, 0.9F);
		level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.4, 0.05, 0.4, 0.03);
		player.addTag(Freerun.XP_TAG);
	}

	/** The three-point landing, for anyone: the pose everyone draws, a heavy thud and the ring of dust. */
	static void heroLanding(ServerPlayer player) {
		WebPose current = player.getAttached(WebPower.POSE);
		if (current != null && player.level().getGameTime() - current.start() < current.ticks() && !WebPose.trick(current.kind())) {
			// a dodge or a ragdoll keeps the body
			return;
		}
		WebPower.pose(player, WebPose.HERO_LAND, player.getRandom().nextBoolean() ? 1 : -1);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 0.7F, 0.7F);
		dust(level, player);
	}

	/** A ring of dust kicked out of the ground round a landing (the web slinger's hero landing too). */
	public static void dust(ServerLevel level, Player player) {
		BlockState floor = level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ()));
		boolean solid = !floor.isAir() && floor.getRenderShape() != net.minecraft.world.level.block.RenderShape.INVISIBLE;
		for (int i = 0; i < DUST_PUFFS; i++) {
			float angle = i * Mth.TWO_PI / DUST_PUFFS;
			double dx = Mth.cos(angle), dz = Mth.sin(angle);
			double x = player.getX() + dx * DUST_RADIUS * 0.4, y = player.getY() + 0.1, z = player.getZ() + dz * DUST_RADIUS * 0.4;
			// count 0: the offsets are the puff's speed, out along the ring
			level.sendParticles(ParticleTypes.POOF, x, y, z, 0, dx, 0.02, dz, 0.18);
			if (solid) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), x + dx * DUST_RADIUS * 0.6, y, z + dz * DUST_RADIUS * 0.6, 2, 0.1, 0.05, 0.1, 0.1);
			}
		}
	}
}
