package com.jaymospowers.power.web;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * Golems and villagers know. An iron golem near a host whose symbiote shows (the suit or Living Armor on) turns on
 * them; villagers shy away from the black strain's host, walking off the way they came whenever it comes near (a nudge
 * on the walk target, since villagers are brain-driven, and a plain path for the wandering trader). Off with the suit,
 * the golems calm down (their own anger runs out), and nobody is punished for leaving them alone.
 */
final class WebSymbioteVillage {
	/** It looks about every this many ticks. */
	static final int SCAN_EVERY = 10;
	/** A golem this close takes notice; a villager this close shies. */
	static final double GOLEM_RANGE = 24.0, SHY_RANGE = 10.0;
	/** A shying villager walks to a point this far off, at this speed. */
	static final double SHY_STEP = 8.0;
	static final float SHY_SPEED = 0.7F;
	/** The symbiote remarks on it at most this often (ticks), each. */
	static final int REMARK_EVERY = 9000;
	static final int GOLEM_LINES = 2, SHY_LINES = 2;

	private static final Map<UUID, long[]> REMARKED = new HashMap<>();

	private WebSymbioteVillage() {
	}

	static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> REMARKED.remove(handler.player.getUUID()));
	}

	static void tick(ServerPlayer player, WebSymbioteState state, long now) {
		if (player.tickCount % SCAN_EVERY != 0 || player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return;
		}
		ServerLevel level = player.level();
		if (WebSymbiote.suitShown(player) || WebSymbiote.livingArmor(player)) {
			boolean angered = false;
			for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, player.getBoundingBox().inflate(GOLEM_RANGE),
				g -> g.isAlive() && g.getTarget() != player)) {
				golem.setTarget(player);
				angered = true;
			}
			if (angered) {
				remark(player, now, 0, "village.golem." + (1 + player.getRandom().nextInt(GOLEM_LINES)));
			}
		}
		if (state.kind() == WebSymbioteStrain.BLACK) {
			boolean shied = false;
			for (AbstractVillager villager : level.getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(SHY_RANGE),
				v -> v.isAlive() && v.distanceTo(player) <= SHY_RANGE)) {
				Vec3 away = villager.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
				if (away.lengthSqr() < 1.0E-4) {
					away = new Vec3(1.0, 0.0, 0.0);
				}
				Vec3 to = villager.position().add(away.normalize().scale(SHY_STEP));
				if (villager instanceof Villager brained) {
					brained.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, SHY_SPEED, 1));
				} else {
					villager.getNavigation().moveTo(to.x, to.y, to.z, SHY_SPEED);
				}
				shied = true;
			}
			if (shied) {
				remark(player, now, 1, "village.shy." + (1 + player.getRandom().nextInt(SHY_LINES)));
			}
		}
	}

	/** One line of the {@code kind} now and then. */
	private static void remark(ServerPlayer player, long now, int kind, String key) {
		long[] last = REMARKED.computeIfAbsent(player.getUUID(), id -> new long[] {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2});
		if (now - last[kind] >= REMARK_EVERY) {
			last[kind] = now;
			WebSymbiote.whisper(player, key);
		}
	}
}
