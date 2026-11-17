package com.jaymospowers.power.wind;

import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Airless Dome (Andy's Awakening pick: "Wind, high rank"; "Smoke can do the same"). The air is pulled out of a dome round
 * where you stand for {@link #TICKS} ticks. Everyone in it but you suffocates, armour or not (whatever has no need of air, the
 * drowned and the dead, goes on); a creeper's fuse will not take (it shrinks back while it is inside); and no caster can finish a
 * spell: a spell-casting illager's working falls apart, and Jaymo's Kingdoms' casters, players and folk alike, are hushed while
 * the {@link #TAG} is on them.
 * <p>
 * A Wind or Smoke user from the master rank of training ({@link #RANK}) calls it: on C when the power has no C of its own yet,
 * else sprint + C.
 */
public final class AirlessDome {
	// ---- tuning ----
	/** The training rank it comes at (Mastery's ranks: adept 1, expert 2, master 3). */
	public static final int RANK = 3;
	/** How wide (blocks), how long (ticks), the wait after (ticks). */
	static final double RADIUS = 7.0;
	static final int TICKS = 240, COOLDOWN = 20 * 90;
	/** Air drawn out of the lungs a tick (vanilla holds 300), and the harm of choking each second once it is gone. */
	static final int AIR_DRAWN = 8;
	static final float CHOKE = 2.0F;
	/** The scoreboard tag on whoever is in the dome; Kingdoms' Will reads it as hushed. */
	public static final String TAG = "jaymospowers_airless";

	private record Dome(UUID owner, ResourceKey<Level> dim, Vec3 at, long until, int colour, Set<Entity> tagged) {
	}

	private static final List<Dome> DOMES = new ArrayList<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private AirlessDome() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(AirlessDome::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Dome d : DOMES) {
				d.tagged().forEach(e -> e.removeTag(TAG));
			}
			DOMES.clear();
			READY.clear();
		});
	}

	/** Whether {@code player} has grown into it (both sides). */
	public static boolean unlocked(Player player) {
		return Mastery.unlocked(player) >= Mastery.ALL && Mastery.rank(player) >= RANK || player.isCreative();
	}

	/** Pulls the air out round {@code player}: true if it was called. */
	public static boolean cast(ServerPlayer player, int colour) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long ready = READY.getOrDefault(player.getUUID(), 0L);
		if (ready > now && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.airless.wait", (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return false;
		}
		READY.put(player.getUUID(), now + COOLDOWN);
		Vec3 at = player.position();
		DOMES.add(new Dome(player.getUUID(), level.dimension(), at, now + TICKS, colour, new HashSet<>()));
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), (float)RADIUS, colour);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 2.0F, 0.4F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.6F, 2.0F);
		return true;
	}

	/** A spellcasting illager's working can't be finished without breath: whatever spell it is on falls apart. */
	public static void breakSpell(LivingEntity e) {
		if (e instanceof SpellcasterIllager caster) {
			for (WrappedGoal goal : caster.getGoalSelector().getAvailableGoals()) {
				Class<?> outer = goal.getGoal().getClass().getEnclosingClass();
				if (goal.isRunning() && outer != null && SpellcasterIllager.class.isAssignableFrom(outer)) {
					goal.stop();
				}
			}
		}
	}

	private static void tick(MinecraftServer server) {
		Iterator<Dome> it = DOMES.iterator();
		while (it.hasNext()) {
			Dome d = it.next();
			ServerLevel level = server.getLevel(d.dim());
			long now = server.overworld().getGameTime();
			if (level == null || now >= d.until()) {
				d.tagged().forEach(e -> e.removeTag(TAG));
				if (level != null) {
					MagicFx.blast(level, d.at().add(0.0, 1.0, 0.0), (float)RADIUS * 0.5F, d.colour());
					level.playSound(null, d.at().x, d.at().y, d.at().z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.5F, 0.6F);
				}
				it.remove();
				continue;
			}
			if (now % 10 == 0) {
				MagicFx.orb(level, d.at().add(0.0, 0.5, 0.0), (float)RADIUS, d.colour(), 12);
			}
			Set<Entity> inside = new HashSet<>();
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(d.at(), d.at()).inflate(RADIUS),
				e -> e.isAlive() && !e.getUUID().equals(d.owner()) && e.position().distanceToSqr(d.at()) <= RADIUS * RADIUS)) {
				inside.add(e);
				if (d.tagged().add(e)) {
					e.addTag(TAG);
				}
				if (!e.canBreatheUnderwater() && !(e instanceof Player p && p.getAbilities().invulnerable)) {
					e.setAirSupply(Math.max(-20, e.getAirSupply() - AIR_DRAWN));
					if (e.getAirSupply() <= 0 && now % 20 == 0) {
						e.hurtServer(level, level.damageSources().drown(), CHOKE);
						level.sendParticles(MagicFx.mote(d.colour(), 0.6F), e.getX(), e.getEyeY(), e.getZ(), 4, 0.2, 0.1, 0.2, 0.01);
					}
				}
				if (e instanceof Creeper creeper) {
					// no air, no spark: the fuse goes back down
					creeper.setSwellDir(-1);
				}
				breakSpell(e);
			}
			for (Iterator<Entity> t = d.tagged().iterator(); t.hasNext();) {
				Entity e = t.next();
				if (!inside.contains(e)) {
					e.removeTag(TAG);
					t.remove();
				}
			}
		}
	}
}
