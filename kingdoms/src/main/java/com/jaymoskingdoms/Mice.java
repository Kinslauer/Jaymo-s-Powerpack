package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.580) Andy (seven-mods plan, patch 4): Caverns &amp; Chasms' rats, as behaviour on the mice (and rats) Cube Animals
 * already has, no new mob. A wild mouse now and then creeps to a chest, barrel or Kitchen cabinet near it that has food
 * in it, when nobody stands close, and steals a bite (one food item, gone). Cats chase mice off, and catch the slow
 * ones. Feed one grain, bread or cheese and it may take to you (one in {@link #TAME_ODDS}): a tame mouse follows you,
 * stays, and steals nothing.
 */
final class Mice {
	static final int EVERY = 20;
	/** How far round players mice are looked at; how far a mouse smells food; how near a person keeps it off. */
	static final double AROUND = 32.0, SMELL = 12.0, SHY = 5.0;
	/** The chance a free mouse goes for food each second; ticks between raids; ticks to get there before it gives up. */
	static final float RAID_CHANCE = 0.08F;
	static final long AGAIN_TICKS = 2400, RAID_TICKS = 400;
	static final double REACH = 1.6, CAT_RANGE = 12.0, CAT_CATCH = 1.3, FOLLOW_FAR = 24.0, FOLLOW_NEAR = 4.0;
	static final float CAT_BITE = 2.0F;
	static final int TAME_ODDS = 3;
	static final String TAME = "jaymoskingdoms.tame_mouse", OWNER = "jaymoskingdoms.mouse_owner=";

	record Raid(BlockPos at, long since) {
	}

	private static final Map<UUID, Raid> RAIDS = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private Mice() {
	}

	static boolean mouse(Entity e) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
		return id.getNamespace().equals("cubeanimals") && (id.getPath().equals("mouse") || id.getPath().equals("rat"));
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == 0) {
				tick(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RAIDS.clear();
			LAST.clear();
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !(entity instanceof PathfinderMob m) || !mouse(m)
				|| m.entityTags().contains(TAME)) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getItemInHand(hand);
			if (!bait(held)) {
				return InteractionResult.PASS;
			}
			ServerLevel level = sp.level();
			if (!sp.isCreative()) {
				held.shrink(1);
			}
			level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.4F, 1.8F);
			if (level.getRandom().nextInt(TAME_ODDS) == 0) {
				m.addTag(TAME);
				m.addTag(OWNER + sp.getStringUUID());
				m.setPersistenceRequired();
				RAIDS.remove(m.getUUID());
				level.sendParticles(ParticleTypes.HEART, m.getX(), m.getY() + 0.4, m.getZ(), 5, 0.2, 0.2, 0.2, 0.0);
			} else {
				level.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getY() + 0.3, m.getZ(), 3, 0.1, 0.1, 0.1, 0.0);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** Grain, bread or cheese (any mod's). */
	static boolean bait(ItemStack stack) {
		if (stack.is(Items.WHEAT) || stack.is(Items.WHEAT_SEEDS) || stack.is(Items.BREAD)) {
			return true;
		}
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("cheese");
	}

	private static void tick(MinecraftServer server) {
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(AROUND), e -> e.isAlive() && mouse(e))) {
				if (seen.add(m.getUUID())) {
					think(level, m);
				}
			}
		}
		RAIDS.keySet().retainAll(seen);
	}

	private static void think(ServerLevel level, PathfinderMob m) {
		long now = level.getGameTime();
		if (m.entityTags().contains(TAME)) {
			ServerPlayer owner = owner(level, m);
			if (owner != null && owner.level() == level) {
				double d = m.distanceToSqr(owner);
				if (d > FOLLOW_NEAR * FOLLOW_NEAR && d < FOLLOW_FAR * FOLLOW_FAR) {
					m.getNavigation().moveTo(owner, 1.2);
				}
			}
			return;
		}
		// a cat near: it runs, and the cat runs after it
		Cat cat = cat(level, m);
		if (cat != null) {
			RAIDS.remove(m.getUUID());
			cat.getNavigation().moveTo(m, 1.3);
			if (cat.distanceToSqr(m) <= CAT_CATCH * CAT_CATCH) {
				cat.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				m.hurtServer(level, level.damageSources().mobAttack(cat), CAT_BITE);
			}
			Vec3 away = DefaultRandomPos.getPosAway(m, 10, 7, cat.position());
			if (away != null) {
				m.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
			}
			return;
		}
		Raid raid = RAIDS.get(m.getUUID());
		if (raid == null) {
			if (now - LAST.getOrDefault(m.getUUID(), Long.MIN_VALUE / 2) < AGAIN_TICKS || level.getRandom().nextFloat() >= RAID_CHANCE) {
				return;
			}
			BlockPos at = pantry(level, m);
			if (at == null) {
				return;
			}
			raid = new Raid(at, now);
			RAIDS.put(m.getUUID(), raid);
		}
		if (now - raid.since() > RAID_TICKS || watched(level, raid.at())) {
			RAIDS.remove(m.getUUID());
			LAST.put(m.getUUID(), now);
			return;
		}
		if (m.position().distanceToSqr(Vec3.atCenterOf(raid.at())) <= REACH * REACH + 1.0) {
			steal(level, m, raid.at());
			RAIDS.remove(m.getUUID());
			LAST.put(m.getUUID(), now);
			return;
		}
		if (m.getNavigation().isDone()) {
			m.getNavigation().moveTo(raid.at().getX() + 0.5, raid.at().getY(), raid.at().getZ() + 0.5, 1.1);
		}
	}

	private static @Nullable ServerPlayer owner(ServerLevel level, PathfinderMob m) {
		for (String tag : m.entityTags()) {
			if (tag.startsWith(OWNER)) {
				try {
					return level.getServer().getPlayerList().getPlayer(UUID.fromString(tag.substring(OWNER.length())));
				} catch (IllegalArgumentException e) {
					return null;
				}
			}
		}
		return null;
	}

	private static @Nullable Cat cat(ServerLevel level, PathfinderMob m) {
		Cat best = null;
		double bestD = CAT_RANGE * CAT_RANGE;
		for (Cat c : level.getEntitiesOfClass(Cat.class, m.getBoundingBox().inflate(CAT_RANGE), c -> c.isAlive() && !c.isOrderedToSit() && !c.isBaby())) {
			double d = c.distanceToSqr(m);
			if (d < bestD) {
				bestD = d;
				best = c;
			}
		}
		return best;
	}

	/** A store with food in it near {@code m}, with nobody close by; or null. */
	private static @Nullable BlockPos pantry(ServerLevel level, PathfinderMob m) {
		BlockPos from = m.blockPosition();
		int r = (int)SMELL;
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(from.offset(-r, -3, -r), from.offset(r, 3, r))) {
			BlockEntity be = level.getBlockEntity(p);
			if (!(be instanceof Container box) || !store(be) || food(box) < 0) {
				continue;
			}
			double d = p.distSqr(from);
			if (d < bestD && !watched(level, p)) {
				bestD = d;
				best = p.immutable();
			}
		}
		return best;
	}

	private static boolean store(BlockEntity be) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock());
		String path = id.getPath();
		return path.endsWith("chest") && !path.contains("ender") || path.equals("barrel") || id.getNamespace().equals("jaymoskitchen") && path.contains("cabinet");
	}

	/** The slot of some food in {@code box}, or -1. */
	private static int food(Container box) {
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (box.getItem(i).has(DataComponents.FOOD)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean watched(ServerLevel level, BlockPos p) {
		return !level.getEntitiesOfClass(ServerPlayer.class, new AABB(p).inflate(SHY), pl -> !pl.isSpectator()).isEmpty();
	}

	private static void steal(ServerLevel level, PathfinderMob m, BlockPos at) {
		if (!(level.getBlockEntity(at) instanceof Container box)) {
			return;
		}
		int slot = food(box);
		if (slot < 0) {
			return;
		}
		ItemStack bite = box.getItem(slot);
		level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, bite.getItem()), m.getX(), m.getY() + 0.2, m.getZ(), 5, 0.1,
			0.05, 0.1, 0.03);
		bite.shrink(1);
		box.setChanged();
		level.playSound(null, at, SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.35F, 1.9F);
	}
}
