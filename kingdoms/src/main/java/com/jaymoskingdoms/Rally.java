package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A lord's rally. A player who holds a realm ({@link Faction#leader} is theirs) or is a lord of one
 * ({@link Wars#isLord}) sneaks and blows a goat horn: for {@link #LASTS} ticks that realm's guards and archers within
 * {@link #RANGE}, and the iron golems of its town, come to them and face what threatens them. Villagers who do not
 * fight stay where they are. The horn rests {@link #COOLDOWN} after. It runs ahead of the Hunter's howl on the same
 * horn (a Hunter who is also a lord howls by blowing it standing, rallies by sneaking), so one blast never does both.
 * Rallies live in memory.
 */
public final class Rally {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** How far the horn carries (blocks), how long the call holds (ticks), how long the horn rests (ticks). */
	static final double RANGE = 48.0;
	static final int LASTS = 600, COOLDOWN = 1200;
	/** How often the called are sent on (ticks); the pace they come at; how near is near enough (blocks). */
	static final int EVERY = 20;
	static final double PACE = 1.25, NEAR = 4.0;
	/** The called are set on a foe within this of the lord (blocks). */
	static final double FOE_RANGE = 20.0;
	/** The most called at once. */
	static final int MOST = 24;

	private record Call(int faction, long until) {
	}

	private static final Map<UUID, Call> CALLS = new HashMap<>();

	private Rally() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CALLS.clear());
		ServerTickEvents.END_SERVER_TICK.register(Rally::tick);
		// ahead of the default phase, so the Hunter's howl on the same horn is not blown as well
		Identifier early = JaymosKingdoms.id("rally_early");
		UseItemCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		UseItemCallback.EVENT.register(early, (player, world, hand) -> {
			ItemStack held = player.getItemInHand(hand);
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level) || !held.is(Items.GOAT_HORN)
				|| !sp.isShiftKeyDown() || sp.getCooldowns().isOnCooldown(held)) {
				return InteractionResult.PASS;
			}
			Faction realm = realmOf(level.getServer(), sp);
			if (realm == null) {
				return InteractionResult.PASS;
			}
			sp.getCooldowns().addCooldown(held, COOLDOWN);
			sound(level, sp);
			int called = call(level, sp, realm);
			sp.sendOverlayMessage(Component.translatable(called == 0 ? "message.jaymoskingdoms.rally.none" : "message.jaymoskingdoms.rally.called", called));
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	/** The realm {@code player} holds or is a lord of, nearest its seat, or null. */
	static @Nullable Faction realmOf(MinecraftServer server, ServerPlayer player) {
		Faction best = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm() || !(f.leader().filter(player.getUUID()::equals).isPresent() || Wars.isLord(f, player.getUUID()))) {
				continue;
			}
			double d = player.blockPosition().distSqr(f.seat());
			if (d < bestD) {
				bestD = d;
				best = f;
			}
		}
		return best;
	}

	private static void sound(ServerLevel level, ServerPlayer player) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0), SoundSource.PLAYERS, 2.0F, 0.9F);
		Fx.ring(level, player.position().add(0.0, 0.1, 0.0), 5.0F, 0xE8C25A);
	}

	/** Who answers the call: the realm's guards and archers, and the golems of its town, near {@code lord}. */
	private static List<PathfinderMob> answering(ServerLevel level, ServerPlayer lord, Faction realm) {
		AABB box = lord.getBoundingBox().inflate(RANGE, RANGE / 2, RANGE);
		List<PathfinderMob> out = new java.util.ArrayList<>();
		for (Worker w : Workers.near(level, box, g -> g.isAlive() && g.factionId() == realm.id() && (g.role() == Folk.GUARD || g.role() == Folk.ARCHER))) {
			out.add(w.mob());
		}
		for (IronGolem g : level.getEntitiesOfClass(IronGolem.class, box, g -> g.isAlive() && (g.isPlayerCreated() || Careers.inTown(level, realm, g.blockPosition())))) {
			out.add(g);
		}
		if (out.size() > MOST) {
			out.sort(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(lord)));
			return out.subList(0, MOST);
		}
		return out;
	}

	/** Starts the call; how many answer. */
	private static int call(ServerLevel level, ServerPlayer lord, Faction realm) {
		CALLS.put(lord.getUUID(), new Call(realm.id(), level.getGameTime() + LASTS));
		return send(level, lord, realm);
	}

	private static void tick(MinecraftServer server) {
		if (CALLS.isEmpty() || server.getTickCount() % EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		CALLS.entrySet().removeIf(e -> {
			ServerPlayer lord = server.getPlayerList().getPlayer(e.getKey());
			Faction realm = Factions.get(server, e.getValue().faction());
			if (lord == null || !lord.isAlive() || realm == null || realm.fallen() || e.getValue().until() <= now) {
				return true;
			}
			send(lord.level(), lord, realm);
			return false;
		});
	}

	/** Sends the called to {@code lord}, and at a foe near them. */
	private static int send(ServerLevel level, ServerPlayer lord, Faction realm) {
		LivingEntity foe = null;
		double best = FOE_RANGE * FOE_RANGE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, lord.getBoundingBox().inflate(FOE_RANGE), x -> x.isAlive() && x instanceof Enemy)) {
			double d = e.distanceToSqr(lord);
			if (d < best) {
				best = d;
				foe = e;
			}
		}
		int n = 0;
		for (PathfinderMob mob : answering(level, lord, realm)) {
			n++;
			if (foe != null && mob.getTarget() == null) {
				mob.setTarget(foe);
			}
			if (mob.distanceToSqr(lord) <= NEAR * NEAR) {
				continue;
			}
			if (mob instanceof Villager v) {
				BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(lord, true), (float)PACE, (int)NEAR);
			} else {
				mob.getNavigation().moveTo(lord, PACE);
			}
			if (mob.getRandom().nextInt(3) == 0) {
				Vec3 at = mob.position();
				level.sendParticles(Fx.mote(0xE8C25A, 0.7F), at.x, at.y + 2.1, at.z, 2, 0.2, 0.1, 0.2, 0.0);
			}
		}
		return n;
	}
}
