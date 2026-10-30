package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.CombatArts;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.forms.Forms.Family;
import com.jaymoskingdoms.forms.Forms.Form;
import com.jaymoskingdoms.forms.Forms.Kind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.383) The fist family's forms (an empty hand, or a cestus-like weapon in the {@code form_fists} tag).
 * <ul>
 * <li>Ground Stomp: a hop, and on coming down a small shockwave ring (the Aether Giants' stomp at a fist's size): foes
 * standing on the ground within {@value #STOMP_RADIUS} blocks are hurt and thrown up, foes already in the air are not. A Brute
 * stomps {@value #BRUTE_BONUS} times as hard.
 * <li>Falling Fist: pressed while falling {@value #FALL_MIN} blocks or more, you drive down; the first foe your fall
 * meets takes part of the fall's force. You still take your whole fall damage on landing, as everyone does.
 * </ul>
 */
final class FistForms {
	static final Form GROUND_STOMP = new Form("ground_stomp", Family.FIST, Kind.ART, 120, 6, 2.0F, 6);
	static final Form FALLING_FIST = new Form("falling_fist", Family.FIST, Kind.ART, 100, 0, 1.5F, 6);

	/** Ground Stomp: the hop's lift, the ring's reach (blocks) and how high up it catches foes, what it deals (times your attack), the throw up, the shove out. */
	static final double STOMP_HOP = 0.55, STOMP_RADIUS = 4.0, STOMP_HEIGHT = 1.5, STOMP_LIFT = 0.65, STOMP_SHOVE = 0.35;
	static final float STOMP_DAMAGE = 1.1F;
	/** The ticks the hop may last at most (the stomp lands wherever you are by then) and the least before a landing counts. */
	static final int STOMP_TIMEOUT = 30, STOMP_MIN_AIR = 3;
	/** Falling Fist: the fall it needs (blocks), how hard it drives you down (blocks a tick), the reach round and below you the fist finds a foe in, the force it passes on (damage per block fallen) and its cap. */
	static final double FALL_MIN = 3.0, DIVE_SPEED = 1.1, FIST_REACH = 1.3, FIST_BELOW = 1.2;
	static final float FALL_DAMAGE_PER_BLOCK = 1.0F, FALL_DAMAGE_CAP = 24.0F;
	/** A dive that finds nothing gives up after this many ticks. */
	static final int DIVE_TIMEOUT = 100;
	/** A Brute's fists hit this many times as hard. */
	static final float BRUTE_BONUS = 1.25F;
	/** The screen shake of each (about 0.3 a nudge, 1 a blow). */
	static final float STOMP_SHAKE = 0.5F, FALL_SHAKE = 0.6F;

	/** A hop under way. */
	private static final class Hop {
		final long start;
		final int rank;
		boolean left;

		Hop(long start, int rank) {
			this.start = start;
			this.rank = rank;
		}
	}

	/** A dive under way: the fall as of the last tick (the landing resets it before we can look). */
	private static final class Dive {
		final long start;
		final int rank;
		double fall;

		Dive(long start, int rank, double fall) {
			this.start = start;
			this.rank = rank;
			this.fall = fall;
		}
	}

	private static final Map<UUID, Hop> HOPS = new HashMap<>();
	private static final Map<UUID, Dive> DIVES = new HashMap<>();

	private FistForms() {
	}

	static void addTo(Map<String, Form> forms) {
		forms.put(GROUND_STOMP.id(), GROUND_STOMP);
		forms.put(FALLING_FIST.id(), FALLING_FIST);
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FistForms::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			HOPS.remove(handler.player.getUUID());
			DIVES.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOPS.clear();
			DIVES.clear();
		});
	}

	static void perform(ServerPlayer player, Form form, int rank) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (form == GROUND_STOMP) {
			// the hop; the stomp comes when it lands (tick)
			if (!player.onGround()) {
				return;
			}
			player.setDeltaMovement(player.getDeltaMovement().x, STOMP_HOP, player.getDeltaMovement().z);
			player.needsSync = true;
			HOPS.put(player.getUUID(), new Hop(level.getGameTime(), rank));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 0.8F);
		} else if (form == FALLING_FIST) {
			if (player.onGround() || player.fallDistance < FALL_MIN || player.isInWater() || player.getAbilities().flying) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.falling_fist.need").withStyle(ChatFormatting.GRAY));
				return;
			}
			DIVES.put(player.getUUID(), new Dive(level.getGameTime(), rank, player.fallDistance));
			// (the fall is left alone: it still counts in full when you land)
			Vec3 move = player.getDeltaMovement();
			player.setDeltaMovement(move.x * 0.3, Math.min(move.y, -DIVE_SPEED), move.z * 0.3);
			player.needsSync = true;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 1.4F);
		}
	}

	private static float brute(ServerPlayer player) {
		return Classes.is(player, Classes.PlayerClass.BRUTE) ? BRUTE_BONUS : 1.0F;
	}

	private static void tick(MinecraftServer server) {
		if (HOPS.isEmpty() && DIVES.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Hop>> it = HOPS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Hop> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Hop hop = entry.getValue();
			if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			long age = level.getGameTime() - hop.start;
			if (!player.onGround()) {
				hop.left = true;
				if (age < STOMP_TIMEOUT) {
					continue;
				}
			} else if (!hop.left && age < STOMP_MIN_AIR) {
				continue;
			}
			it.remove();
			stomp(level, player, hop.rank);
		}
		for (Iterator<Map.Entry<UUID, Dive>> it = DIVES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Dive> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Dive dive = entry.getValue();
			if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level) || player.onGround() || player.isInWater()
				|| player.isFallFlying() || level.getGameTime() - dive.start > DIVE_TIMEOUT) {
				it.remove();
				continue;
			}
			dive.fall = Math.max(dive.fall, player.fallDistance);
			// held down, whatever the air does
			Vec3 move = player.getDeltaMovement();
			if (move.y > -DIVE_SPEED) {
				player.setDeltaMovement(move.x * 0.5, -DIVE_SPEED, move.z * 0.5);
				player.needsSync = true;
			}
			List<LivingEntity> below = Forms.targets(level, player, player.getBoundingBox().inflate(FIST_REACH, 0.0, FIST_REACH)
				.expandTowards(0.0, -FIST_BELOW + Math.min(0.0, move.y), 0.0));
			if (!below.isEmpty()) {
				it.remove();
				fist(level, player, dive, below);
			}
		}
	}

	/** The ring: foes on the ground within reach are hurt and thrown up; those in the air are not. */
	private static void stomp(ServerLevel level, ServerPlayer player, int rank) {
		double radius = Forms.reach(STOMP_RADIUS, rank);
		Vec3 at = player.position();
		BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
		// three rings of the ground's dust running out
		for (int ring = 1; ring <= 3; ring++) {
			double r = radius * ring / 3.0;
			int points = Math.max(10, (int)(r * Math.PI * 2));
			for (int i = 0; i < points; i++) {
				double a = Math.PI * 2 * i / points, cx = Math.cos(a), cz = Math.sin(a);
				if (!ground.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x + cx * r, at.y + 0.1, at.z + cz * r, 1, 0.05, 0.05, 0.05, 0.12);
				}
				if ((i & 1) == 0) {
					level.sendParticles(ParticleTypes.POOF, at.x + cx * r, at.y + 0.15, at.z + cz * r, 1, cx * 0.1, 0.02, cz * 0.1, 0.05);
				}
			}
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0F, 1.1F);
		List<LivingEntity> hits = new ArrayList<>();
		for (LivingEntity foe : Forms.targets(level, player, new AABB(at, at).inflate(radius, STOMP_HEIGHT, radius))) {
			double d = Math.sqrt(Math.pow(foe.getX() - at.x, 2) + Math.pow(foe.getZ() - at.z, 2));
			// on the ground near your level (not jumping, flying or riding)
			if (d > radius + foe.getBbWidth() / 2.0 || !foe.onGround() || foe.isPassenger() || Math.abs(foe.getY() - at.y) > STOMP_HEIGHT) {
				continue;
			}
			hits.add(foe);
			CombatArts.strike(level, player, foe, Forms.damage(player, STOMP_DAMAGE, rank) * brute(player));
			if (!foe.isAlive()) {
				continue;
			}
			Vec3 out = new Vec3(foe.getX() - at.x, 0.0, foe.getZ() - at.z);
			Vec3 push = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(STOMP_SHOVE);
			foe.setDeltaMovement(foe.getDeltaMovement().add(push.x, STOMP_LIFT, push.z));
			foe.needsSync = true;
		}
		PowersCompat.shake(player, STOMP_SHAKE);
		Forms.landed(player, GROUND_STOMP, hits);
	}

	/** The fist lands on the foes below: part of the fall's force goes into them. */
	private static void fist(ServerLevel level, ServerPlayer player, Dive dive, List<LivingEntity> foes) {
		float force = (float)Math.min(FALL_DAMAGE_CAP, dive.fall * FALL_DAMAGE_PER_BLOCK) * (1.0F + Forms.RANK_DAMAGE * (dive.rank - 1)) * brute(player);
		for (LivingEntity foe : foes) {
			CombatArts.strike(level, player, foe, force + Forms.damage(player, 0.5F, dive.rank));
			level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getY(0.8), foe.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
			level.sendParticles(ParticleTypes.POOF, foe.getX(), foe.getY(), foe.getZ(), 6, 0.3, 0.1, 0.3, 0.05);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.8F, 1.2F);
		PowersCompat.shake(player, FALL_SHAKE);
		Forms.landed(player, FALLING_FIST, foes);
	}
}
