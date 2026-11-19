package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Awakening pick) <b>Awakened strangers</b>: gifted folk on the road (an MCA villager with a power, Jaymo's Powers' gifted
 * folk, who fights with it) who seek out others who carry a power to test their gift on them. Now and then
 * ({@link #CHANCE} each {@link #LOOK_EVERY} ticks, no oftener than {@link #GAP} for one player) one comes walking up to a
 * player with a power, out in the wild of the Overworld, named with the title of their mastery ("Adept" or, for a master
 * of it, "Master" of their power: masters likelier the higher the world tier), calls them out (a speech bubble), and
 * fights them, by hand and with the power. Hurt to {@link #YIELD_AT} of their health, they yield, say so, and go on their
 * way; so do they when their quarry is gone or after {@link #FIGHT_TICKS}. Without MCA, a plain villager.
 * <p>
 * Andy (2026-11-19, beamed down by one a few minutes into a new life: "way too strong to encounter that early ... tune that
 * fighter WAY down"): none come for a player who hasn't lived {@link #LIVED_FIRST} in this life yet; no masters in the
 * Hearth tier; they walk up and call out before they fight, and square up for {@link #SQUARE_UP} first (their power's
 * moves need a target, so nothing is thrown before the call); their blows on a player land at {@link #HARM} of their
 * strength for the player's world tier, never more than {@link #MOST} of the player's health in one; and a quarry who goes
 * down has lost: the stranger says so and leaves them be.
 */
public final class AwakenedStrangers {
	// ---- tuning ----
	/** How often each player is looked at (ticks), the chance a stranger comes, and the least time between two for one player. */
	static final int LOOK_EVERY = 1200;
	static final float CHANCE = 0.05F;
	static final long GAP = 24000L;
	/** How far off they arrive (blocks). */
	static final int ARRIVE_MIN = 22, ARRIVE_MAX = 34;
	/** The chance they're a master of it, and how much likelier each world tier. */
	static final float MASTER_CHANCE = 0.0F, MASTER_PER_TIER = 0.1F;
	/** How long a player must have lived in this life before any stranger comes for them (ticks: two days). */
	static final int LIVED_FIRST = 48000;
	/** How long after calling out they square up before the first blow or move (ticks). */
	static final int SQUARE_UP = 40;
	/**
	 * By the player's world tier (Hearth, Borderlands, Wildlands, Dread, Legend): the share of a stranger's harm that lands
	 * on a player, and the most of the player's full health one blow or move may take.
	 */
	static final float[] HARM = {0.35F, 0.6F, 0.8F, 1.0F, 1.0F};
	static final float[] MOST = {0.2F, 0.3F, 0.45F, 1.0F, 1.0F};
	/** How long a fight may go on (ticks), the share of health they yield at, and how long they take to go once done. */
	static final int FIGHT_TICKS = 6000, LEAVE_TICKS = 300;
	static final float YIELD_AT = 0.25F;
	/** A stranger with no attack of their own (a plain villager) hits for this much. */
	static final float FIST = 3.0F;
	/** Their reach, and the beat of their blows (ticks). */
	static final double REACH = 2.6;
	static final int BLOW_EVERY = 20;
	/** How near before they call out (blocks), and how many lines of each kind there are. */
	static final double CALL_RANGE = 14.0;
	static final int CALL_LINES = 4, YIELD_LINES = 3, WIN_LINES = 2;
	/** On every stranger. */
	public static final String TAG = "jaymoskingdoms.awakened_stranger";

	/** A stranger on the road: whom they've come for, when they came, whether they've called out, and when they leave (0: not yet). */
	private static final class Stranger {
		final UUID quarry;
		final long since;
		boolean called;
		long calledAt;
		long leaveAt;

		Stranger(UUID quarry, long since) {
			this.quarry = quarry;
			this.since = since;
		}
	}

	private static final Map<UUID, Stranger> STRANGERS = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private AwakenedStrangers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(AwakenedStrangers::tick);
		// one left from before a restart (no longer followed) goes on their way
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (is(entity) && !STRANGERS.containsKey(entity.getUUID())) {
				entity.discard();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STRANGERS.clear();
			LAST.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		for (ServerPlayer player : overworld.players()) {
			if ((now + player.getId() * 7L) % LOOK_EVERY != 0 || !player.isAlive() || player.isCreative() || player.isSpectator()
				|| PowersCompat.power(player) == null
				|| player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_DEATH)) < LIVED_FIRST) {
				continue;
			}
			Long last = LAST.get(player.getUUID());
			if (last != null && now - last < GAP || overworld.getRandom().nextFloat() >= CHANCE
				|| Factions.at(server, overworld, player.blockPosition()) != null || PowersCompat.giftable().isEmpty()) {
				continue;
			}
			if (come(overworld, player)) {
				LAST.put(player.getUUID(), now);
			}
		}
		if (now % 10 == 0 && !STRANGERS.isEmpty()) {
			STRANGERS.entrySet().removeIf(e -> !step(overworld, e.getKey(), e.getValue(), now));
		}
	}

	/** A stranger comes for {@code player}: true if one did. */
	static boolean come(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		BlockPos at = null;
		for (int tries = 0; tries < 8 && at == null; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0, distance = Mth.nextInt(random, ARRIVE_MIN, ARRIVE_MAX);
			BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * distance, 0, player.getZ() + Math.sin(angle) * distance);
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (level.getBlockState(top.below()).isSolid() && level.getFluidState(top).isEmpty() && Math.abs(top.getY() - player.getY()) < 16) {
				at = top;
			}
		}
		if (at == null) {
			return false;
		}
		Vec3 spot = Vec3.atBottomCenterOf(at);
		Villager v = McaCompat.LOADED ? McaCompat.talePerson(level, spot, null, null, "", true) : McaCompat.visitor(level, spot, null, false);
		if (v == null) {
			return false;
		}
		List<String> powers = PowersCompat.giftable();
		String power = powers.get(random.nextInt(powers.size()));
		int tier = WorldTiers.of(player).ordinal();
		boolean master = random.nextFloat() < MASTER_CHANCE + MASTER_PER_TIER * tier;
		if (!PowersCompat.give(v, power, PowersCompat.BOLD, master, true)) {
			v.discard();
			return false;
		}
		v.addTag(TAG);
		v.addTag(GiftedFolk.ROLLED);
		v.setPersistenceRequired();
		Component title = Component.translatable("stranger.jaymoskingdoms.name", v.getName().copy(),
			Component.translatable(master ? "stranger.jaymoskingdoms.master" : "stranger.jaymoskingdoms.adept"),
			Component.translatable("power.jaymospowers." + power));
		v.setCustomName(title);
		v.setCustomNameVisible(true);
		STRANGERS.put(v.getUUID(), new Stranger(player.getUUID(), level.getGameTime()));
		return true;
	}

	/** One stranger's step (every 10 ticks): false once they're gone. */
	private static boolean step(ServerLevel level, UUID id, Stranger s, long now) {
		if (!(level.getEntity(id) instanceof PathfinderMob mob) || !mob.isAlive()) {
			return false;
		}
		if (s.leaveAt != 0L) {
			if (now >= s.leaveAt) {
				mob.discard();
				return false;
			}
			return true;
		}
		ServerPlayer quarry = level.getServer().getPlayerList().getPlayer(s.quarry);
		RandomSource random = level.getRandom();
		boolean beaten = quarry != null && Downed.isDowned(quarry);
		if (quarry == null || quarry.level() != level || !quarry.isAlive() || beaten || quarry.distanceTo(mob) > 64.0 || now - s.since > FIGHT_TICKS) {
			if (quarry != null && (!quarry.isAlive() || beaten) && s.called) {
				Speech.say(level, mob, Component.translatable("speech.jaymoskingdoms.stranger.won." + random.nextInt(WIN_LINES)), Speech.GOLD, 24.0);
			}
			leave(mob, s, now);
			return true;
		}
		if (mob.getHealth() <= mob.getMaxHealth() * YIELD_AT) {
			Speech.sayTo(level, mob, quarry, Component.translatable("speech.jaymoskingdoms.stranger.yield." + random.nextInt(YIELD_LINES)), Speech.GOLD);
			leave(mob, s, now);
			return true;
		}
		if (!s.called && mob.distanceTo(quarry) <= CALL_RANGE) {
			s.called = true;
			s.calledAt = now;
			Speech.sayTo(level, mob, quarry, Component.translatable("speech.jaymoskingdoms.stranger.call." + random.nextInt(CALL_LINES)), Speech.GOLD);
		}
		if (!s.called || now - s.calledAt < SQUARE_UP) {
			// (walking up, then squaring up: no target yet, so their power throws nothing before the call and the beat after it)
			mob.getLookControl().setLookAt(quarry, 30.0F, 30.0F);
			if (s.called) {
				mob.getNavigation().stop();
			} else {
				mob.getNavigation().moveTo(quarry, 0.9);
			}
			return true;
		}
		// set on them: their power's moves go at their target (Powers' gifted folk), and they close in for blows
		mob.setTarget(quarry);
		if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
			mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, quarry);
		}
		mob.getLookControl().setLookAt(quarry, 30.0F, 30.0F);
		if (mob.distanceTo(quarry) > REACH) {
			mob.getNavigation().moveTo(quarry, 1.1);
		} else if (mob.tickCount % BLOW_EVERY < 10) {
			mob.getNavigation().stop();
			mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			if (mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null) {
				mob.doHurtTarget(level, quarry);
			} else {
				// a plain villager (no MCA) has no attack of its own
				quarry.hurtServer(level, level.damageSources().mobAttack(mob), FIST);
			}
		}
		return true;
	}

	/** Done (beaten, the quarry gone, or long enough): the stranger stops fighting and walks off, gone a while later. */
	private static void leave(PathfinderMob mob, Stranger s, long now) {
		s.leaveAt = now + LEAVE_TICKS;
		mob.setTarget(null);
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		mob.getNavigation().stop();
		Vec3 away = mob.position().add((mob.getRandom().nextDouble() - 0.5) * 60.0, 0.0, (mob.getRandom().nextDouble() - 0.5) * 60.0);
		mob.getNavigation().moveTo(away.x, away.y, away.z, 0.8);
	}

	/**
	 * A stranger's blow or move on a player, from the hurt chain ({@code LivingEntityHurtMixin}, last): {@link #HARM} of it
	 * for the player's world tier, and never more than {@link #MOST} of their full health.
	 */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0.0F || !(target instanceof Player player) || !is(source.getEntity())) {
			return amount;
		}
		int tier = Math.clamp(WorldTiers.of(player).ordinal(), 0, HARM.length - 1);
		return Math.min(amount * HARM[tier], player.getMaxHealth() * MOST[tier]);
	}

	/** Whether {@code mob} is an awakened stranger. */
	public static boolean is(net.minecraft.world.entity.@Nullable Entity mob) {
		return mob != null && mob.entityTags().contains(TAG);
	}
}
