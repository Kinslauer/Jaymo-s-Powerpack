package com.jaymoskingdoms.sailing;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The wind moves things. Ported in spirit from Global Wind (Depixelation, MIT: its entity and living-entity mixins let
 * the wind push what is airborne and slide what stands on slippery ground), but reading Kingdoms' own wind ({@link Winds}
 * over {@code Storms}), so the push, ships' sails, windmills, smoke and arrows all agree. Its skylight test is replaced
 * by a short ray upwind ({@link #sheltered}).
 *
 * <p>MIT License, Copyright (c) 2025 Depixelation. Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, subject to the following conditions: the above copyright notice and this
 * permission notice shall be included in all copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED
 * "AS IS", WITHOUT WARRANTY OF ANY KIND. (Full text in {@code ports/globalwind/LICENSE}.)
 *
 * <p>Grounded creatures feel nothing. Anyone airborne drifts downwind from a fresh wind up, less the bulkier they are.
 * Items roll in a gale (from a lighter wind on ice). Players are moved by their own client ({@code WindFeel}); the server
 * moves mobs and items. The Overworld only; creative players and spectators aren't pushed.
 */
public final class WindPush {
	/** The airborne drift: from this wind (Winds' "fresh") it starts, at this one it's full; blocks a tick, per tick, at full. */
	static final float AIR_FROM = 0.62F, AIR_FULL = 1.5F;
	public static final double AIR_PUSH = 0.008;
	/** A fallen item rolls from this wind (a gale) on the ground, or this on ice; blocks a tick, per tick, at full. */
	static final float ITEM_FROM = 1.0F, ICE_FROM = 0.62F;
	static final double ITEM_PUSH = 0.02;
	/** The bulk (width * width * height) of a player, the drift's yardstick; a bigger body drifts less, within this range. */
	static final double REF_VOLUME = 0.6 * 0.6 * 1.8;
	static final double BULK_LEAST = 0.35, BULK_MOST = 1.4;
	/** The ray upwind (blocks) that finds shelter, and how long (ticks) a reading is kept. */
	static final double SHELTER_RAY = 8.0;
	static final int SHELTER_KEEP = 10;
	/** A ship in a cove catches this much less wind. */
	static final float COVE = 0.6F;
	/** The server moves mobs and items this often (ticks), by that many times the push. */
	static final int EVERY = 2;

	private static final class Reading {
		long when = Long.MIN_VALUE;
		boolean sheltered;
	}

	private static final Map<Entity, Reading> SERVER = new WeakHashMap<>();
	private static final Map<Entity, Reading> CLIENT = new WeakHashMap<>();
	/** The client's readings for places, and how many it keeps. */
	private static final Map<Long, Reading> SPOTS = new java.util.HashMap<>();
	static final int SPOTS_MOST = 512;

	private WindPush() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WindPush::tick);
	}

	/** How hard the wind blows at {@code at}, as this side has it. */
	private static float strength(Level level, double x, double z) {
		return level.isClientSide() ? Winds.clientStrength(level, x, z) : Winds.strength(level, x, z);
	}

	/** The way the wind blows, as this side has it (flat, a unit long). */
	private static Vec3 way(Level level) {
		if (level.isClientSide()) {
			float a = com.jaymoskingdoms.disasters.Storms.clientWindAngle;
			return new Vec3(Math.cos(a), 0.0, Math.sin(a));
		}
		return Winds.way();
	}

	/**
	 * Whether a solid block (glass included) stands within a few blocks upwind of {@code e}'s head: a short ray, kept for
	 * a few ticks.
	 */
	public static boolean sheltered(Entity e, double height) {
		Level level = e.level();
		Map<Entity, Reading> map = level.isClientSide() ? CLIENT : SERVER;
		Reading r = map.computeIfAbsent(e, k -> new Reading());
		long now = level.getGameTime();
		if (now - r.when >= SHELTER_KEEP || now < r.when) {
			r.when = now;
			Vec3 from = new Vec3(e.getX(), e.getY() + height, e.getZ());
			Vec3 to = from.subtract(way(level).scale(SHELTER_RAY));
			r.sheltered = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getType() != HitResult.Type.MISS;
		}
		return r.sheltered;
	}

	/** {@link #sheltered} for a place (a banner on its pole), on the client: the same ray, kept for a few ticks. */
	public static boolean shelteredAt(Level level, BlockPos pos) {
		if (SPOTS.size() > SPOTS_MOST) {
			SPOTS.clear();
		}
		Reading r = SPOTS.computeIfAbsent(pos.asLong(), k -> new Reading());
		long now = level.getGameTime();
		if (now - r.when >= SHELTER_KEEP || now < r.when) {
			r.when = now;
			Vec3 from = Vec3.atCenterOf(pos);
			Vec3 to = from.subtract(way(level).scale(SHELTER_RAY));
			r.sheltered = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
				.getType() != HitResult.Type.MISS;
		}
		return r.sheltered;
	}

	/** How much wind a ship's sails catch for her place: less in a cove. */
	public static float exposure(Entity ship) {
		return sheltered(ship, 2.5) ? 1.0F - COVE : 1.0F;
	}

	/** A body's share of the drift: the bigger, the less (a Stoneborn drifts less than a Sylvan). */
	static double bulk(Entity e) {
		double volume = Math.max(0.01, e.getBbWidth() * e.getBbWidth() * e.getBbHeight());
		return Mth.clamp(Math.sqrt(REF_VOLUME / volume), BULK_LEAST, BULK_MOST);
	}

	/** This tick's airborne drift of {@code e} (blocks a tick, per tick): zero in calm air, under cover or in another dimension. */
	public static Vec3 airDrift(Entity e) {
		Level level = e.level();
		if (level.dimension() != Level.OVERWORLD) {
			return Vec3.ZERO;
		}
		float ramp = Mth.clamp((strength(level, e.getX(), e.getZ()) - AIR_FROM) / (AIR_FULL - AIR_FROM), 0.0F, 1.0F);
		if (ramp <= 0.0F || sheltered(e, e.getEyeHeight())) {
			return Vec3.ZERO;
		}
		return way(level).scale(AIR_PUSH * ramp * bulk(e));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		for (Entity e : level.getAllEntities()) {
			// (the level's entity list can hold an empty slot while land loads: a null here stopped the server, 2026-11-19 8:11 AM)
			if (e == null || e instanceof Player || e.isPassenger() || e.isInWater() || e.isNoGravity() && !(e instanceof ItemEntity)) {
				continue;
			}
			if (e instanceof ItemEntity item) {
				item(item);
			} else if (e instanceof LivingEntity living && !living.onGround() && living.fallDistance > 0.5F) {
				push(living, airDrift(living).scale(EVERY));
			}
		}
	}

	private static void item(ItemEntity item) {
		Level level = item.level();
		if (!item.onGround()) {
			push(item, airDrift(item).scale(EVERY));
			return;
		}
		boolean ice = level.getBlockState(BlockPos.containing(item.getX(), item.getBoundingBox().minY - 0.5, item.getZ())).is(BlockTags.ICE);
		float from = ice ? ICE_FROM : ITEM_FROM;
		float ramp = Mth.clamp((Winds.strength(level, item.getX(), item.getZ()) - from) / (AIR_FULL - from), 0.0F, 1.0F);
		if (ramp > 0.0F && !sheltered(item, 0.3)) {
			push(item, Winds.way().scale(ITEM_PUSH * ramp * EVERY));
		}
	}

	private static void push(Entity e, Vec3 by) {
		if (by.lengthSqr() > 0.0) {
			e.setDeltaMovement(e.getDeltaMovement().add(by));
		}
	}
}
