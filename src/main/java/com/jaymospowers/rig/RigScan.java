package com.jaymospowers.rig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.83) The Mind's scan (level 3; Helm and Core): a sweep round the wearer that, on their screen only, makes hostile
 * mobs and other players glow and marks chests and ores on the HUD for a few seconds (client: {@code RigScanClient}).
 * The Mind sums up what it found, in its own voice, and says what the target (the lock, else the nearest hostile) is
 * weak to. With no power: hold B. With one: the Rig key's Scan.
 */
public final class RigScan {
	/** Charge, cooldown (ticks). */
	public static final int SCAN_COST = 80, SCAN_COOLDOWN = 200;
	/** How far it sees creatures, and blocks (a cube this many blocks each way). */
	static final double ENTITY_RANGE = 32.0;
	static final int BLOCK_RANGE = 12;
	/** The most it marks (nearest first). */
	static final int MAX_ENTITIES = 48, MAX_BLOCKS = 96;

	private record Found(BlockPos pos, int kind, double distance) {
	}

	private RigScan() {
	}

	static void scan(ServerPlayer player, RigActives.State state) {
		if (state.scanCooldown > 0) {
			return;
		}
		if (!Rig.spend(player, SCAN_COST)) {
			RigMind.empty(player);
			return;
		}
		state.scanCooldown = SCAN_COOLDOWN;
		ServerLevel level = player.level();

		List<Integer> ids = new ArrayList<>();
		List<Integer> kinds = new ArrayList<>();
		int hostiles = 0, players = 0;
		LivingEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		List<LivingEntity> seen = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ENTITY_RANGE),
			e -> e != player && e.isAlive() && !e.isSpectator() && (e instanceof Player || e instanceof Enemy));
		seen.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		for (LivingEntity entity : seen) {
			double distance = entity.distanceToSqr(player);
			if (distance > ENTITY_RANGE * ENTITY_RANGE) {
				continue;
			}
			boolean isPlayer = entity instanceof Player;
			if (isPlayer) {
				players++;
			} else {
				hostiles++;
				if (distance < nearestDistance) {
					nearestDistance = distance;
					nearest = entity;
				}
			}
			if (ids.size() < MAX_ENTITIES) {
				ids.add(entity.getId());
				kinds.add(isPlayer ? RigNet.FOUND_PLAYER : RigNet.FOUND_HOSTILE);
			}
		}

		List<Found> found = new ArrayList<>();
		int chests = 0, ores = 0;
		BlockPos centre = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-BLOCK_RANGE, -BLOCK_RANGE, -BLOCK_RANGE), centre.offset(BLOCK_RANGE, BLOCK_RANGE, BLOCK_RANGE))) {
			BlockState block = level.getBlockState(pos);
			if (block.isAir()) {
				continue;
			}
			int kind;
			if (block.is(ConventionalBlockTags.ORES)) {
				ores++;
				kind = RigNet.FOUND_ORE;
			} else if (block.is(ConventionalBlockTags.CHESTS) || block.is(ConventionalBlockTags.BARRELS) || block.getBlock() instanceof ShulkerBoxBlock) {
				chests++;
				kind = RigNet.FOUND_CHEST;
			} else {
				continue;
			}
			found.add(new Found(pos.immutable(), kind, pos.distSqr(centre)));
		}
		found.sort(Comparator.comparingDouble(Found::distance));
		List<Long> blocks = new ArrayList<>();
		for (int i = 0; i < found.size() && i < MAX_BLOCKS; i++) {
			blocks.add(found.get(i).pos().asLong());
			kinds.add(found.get(i).kind());
		}
		ServerPlayNetworking.send(player, new RigNet.Scan(ids, blocks, kinds));

		// the sweep, on the wearer's screen: a ring of sparks going out, and a low hum everyone near hears
		for (int i = 0; i < 32; i++) {
			double a = i * Math.PI * 2.0 / 32.0;
			level.sendParticles(player, ParticleTypes.ELECTRIC_SPARK, true, false, player.getX() + Math.cos(a) * 1.2, player.getY() + 0.9,
				player.getZ() + Math.sin(a) * 1.2, 0, Math.cos(a), 0.0, Math.sin(a), 0.6);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.8F);

		// the Mind's summary, and the target's weakness
		if (hostiles + players + chests + ores == 0) {
			RigMind.tell(player, Rig.core(player), "scan_none");
		} else {
			RigMind.tell(player, Rig.core(player), "scan", hostiles, players, chests, ores);
		}
		LivingEntity target = RigActives.locked(player);
		if (target == null) {
			target = nearest;
		}
		if (target != null) {
			player.sendSystemMessage(Component.translatable("rig.jaymospowers.scan.weak", target.getDisplayName(), Math.round(target.getHealth()),
				Math.round(target.getMaxHealth()), Component.translatable("rig.jaymospowers.weak." + weakness(target))).withStyle(ChatFormatting.DARK_AQUA));
		}
	}

	/** What the Mind says {@code target} is weak to (lang {@code rig.jaymospowers.weak.<it>}). */
	static String weakness(LivingEntity target) {
		if (target instanceof Player) {
			return "player";
		}
		if (target instanceof Creeper) {
			return "creeper";
		}
		if (target instanceof Enderman) {
			return "water";
		}
		EntityType<?> type = target.getType();
		if (is(type, EntityTypeTags.SENSITIVE_TO_SMITE)) {
			return "smite";
		}
		if (is(type, EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
			return "bane";
		}
		if (is(type, EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)) {
			return "freeze";
		}
		if (is(type, EntityTypeTags.SENSITIVE_TO_IMPALING)) {
			return "impaling";
		}
		if (is(type, EntityTypeTags.ILLAGER)) {
			return "illager";
		}
		return "none";
	}

	private static boolean is(@Nullable EntityType<?> type, TagKey<EntityType<?>> tag) {
		return type != null && type.builtInRegistryHolder().is(tag);
	}
}
