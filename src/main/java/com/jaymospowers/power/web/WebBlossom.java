package com.jaymospowers.power.web;

import com.jaymospowers.core.AbilityPower;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Web Blossom (Sneak + C, once every ability is unlocked): you leap into the air and spin, and a spray of web lines
 * from both wrists goes round the ring at every creature in sight (within {@link #RANGE}, in line of sight), each web
 * cocooning what it hits ({@link WebStrength#cocoon}). Only what is a fight for you is webbed: hostile creatures,
 * whatever is after you, and what you last hit or were hit by; never a tamed pet, a villager or a player you aren't
 * fighting. Each web uses one shot of a shooter's fluid like any Web Shot (the suit's own mass needs none), and the
 * blossom stops where the wrists run dry.
 */
public final class WebBlossom {
	/** How far it sees (blocks), how many it can web at once, and how long a creature stays fought-with after you hit or were hit by it (ticks). */
	public static final double RANGE = 16.0;
	static final int MAX_TARGETS = 10;
	static final int FIGHT_TICKS = 200;
	/** Cocoon length (ticks), the wait after a blossom, the leap's lift, ticks before the first web, and webs thrown a tick. */
	static final int COCOON_TICKS = 80, COOLDOWN = 200, START_DELAY = 4, PER_TICK = 2;
	static final double LEAP = 0.85;
	/** A line drawn to its target this many ticks. */
	static final int LINE_TICKS = 5;

	private static final class Bloom {
		final List<Integer> targets;
		final boolean dark;
		int next;
		int tick;
		/** The off wrist's line goes at this tick (it has no timer of its own). */
		int offClear = -1;

		Bloom(List<Integer> targets, boolean dark) {
			this.targets = targets;
			this.dark = dark;
		}
	}

	private static final Map<UUID, Bloom> BLOOMS = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private WebBlossom() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BLOOMS.clear();
			READY.clear();
		});
	}

	/** Sneak + C. */
	static void use(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (BLOOMS.containsKey(player.getUUID()) || now < READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		boolean dark = WebSymbiote.suitShown(player) || WebSymbiote.isToggled(player, WebSymbioteState.ARMOR);
		if (!dark && WebShooters.shotHand(player) < 0) {
			player.sendOverlayMessage(Component.translatable(WebShooters.worn(player) != null
				? "message.jaymospowers.web_shot.dry" : "message.jaymospowers.web_shot.no_shooters").withStyle(ChatFormatting.GRAY));
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.35F, 1.8F);
			return;
		}
		List<Integer> targets = targets(player);
		if (targets.isEmpty()) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.4F, 2.0F);
			return;
		}
		READY.put(player.getUUID(), now + COOLDOWN);
		BLOOMS.put(player.getUUID(), new Bloom(targets, dark));
		// up and spinning
		ServerPlayNetworking.send(player, new WebNet.Nudge(new Vec3(player.getDeltaMovement().x * 0.3, LEAP, player.getDeltaMovement().z * 0.3)));
		WebPower.pose(player, WebPose.AIR_SPIN, 1);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	/** The creatures in sight that are a fight for {@code player}, nearest first, going round the ring by bearing. */
	private static List<Integer> targets(ServerPlayer player) {
		double range = AbilityPower.size(player, RANGE);
		AABB area = player.getBoundingBox().inflate(range);
		List<LivingEntity> found = new ArrayList<>();
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, area, e -> fight(player, e))) {
			if (e.distanceToSqr(player) <= range * range && player.hasLineOfSight(e)) {
				found.add(e);
			}
		}
		found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		if (found.size() > MAX_TARGETS) {
			found = new ArrayList<>(found.subList(0, MAX_TARGETS));
		}
		found.sort(Comparator.comparingDouble(e -> Math.atan2(e.getZ() - player.getZ(), e.getX() - player.getX())));
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : found) {
			ids.add(e.getId());
		}
		return ids;
	}

	private static boolean fight(ServerPlayer player, LivingEntity e) {
		if (e == player || !e.isAlive() || e.isSpectator() || e == player.getVehicle()) {
			return false;
		}
		int now = player.tickCount;
		boolean fighting = e == player.getLastHurtMob() && now - player.getLastHurtMobTimestamp() < FIGHT_TICKS
			|| e == player.getLastHurtByMob() && now - player.getLastHurtByMobTimestamp() < FIGHT_TICKS
			|| e instanceof Mob mob && mob.getTarget() == player;
		if (e instanceof Player other) {
			return fighting && !other.isCreative();
		}
		if (fighting) {
			return true;
		}
		if (e instanceof TamableAnimal pet && pet.isTame() || e instanceof Villager) {
			return false;
		}
		return e instanceof Enemy;
	}

	/** Every tick, per web slinger: the webs go out round the ring. */
	static void tick(ServerPlayer player) {
		Bloom bloom = BLOOMS.get(player.getUUID());
		if (bloom == null) {
			return;
		}
		if (!player.isAlive() || player.isSpectator()) {
			BLOOMS.remove(player.getUUID());
			return;
		}
		int t = ++bloom.tick;
		if (bloom.offClear > 0 && t >= bloom.offClear) {
			player.removeAttached(WebPower.LINE_OFF);
			bloom.offClear = -1;
		}
		if (t < START_DELAY) {
			return;
		}
		ServerLevel level = player.level();
		for (int n = 0; n < PER_TICK && bloom.next < bloom.targets.size(); n++) {
			Entity entity = level.getEntity(bloom.targets.get(bloom.next));
			int index = bloom.next++;
			if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
				continue;
			}
			int wrist = bloom.dark ? -1 : WebShooters.shotHand(player);
			if (!bloom.dark) {
				if (wrist < 0) {
					bloom.next = bloom.targets.size();
					break;
				}
				WebShooters.fire(player, wrist, false);
			}
			web(player, level, target, index % 2, bloom);
		}
		if (bloom.next >= bloom.targets.size() && bloom.offClear < 0) {
			BLOOMS.remove(player.getUUID());
		}
	}

	private static void web(ServerPlayer player, ServerLevel level, LivingEntity target, int hand, Bloom bloom) {
		Vec3 end = target.getBoundingBox().getCenter();
		WebLine line = new WebLine(WebLine.YANK, end, target.getId());
		if (hand == WebNet.MAIN) {
			WebPower.showLine(player, line, LINE_TICKS);
		} else {
			player.setAttached(WebPower.LINE_OFF, line);
			bloom.offClear = bloom.tick + LINE_TICKS;
		}
		Vec3 wrist = player.position().add(0.0, player.getBbHeight() * 0.65, 0.0);
		for (int i = 1; i <= 6; i++) {
			Vec3 p = wrist.lerp(end, i / 6.0);
			level.sendParticles(bloom.dark ? WebSymbiote.dust(1.0F) : ParticleTypes.ITEM_COBWEB, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		WebStrength.cocoon(level, target, COCOON_TICKS, bloom.dark);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.5F, 1.6F + 0.1F * hand);
	}

	static void forget(ServerPlayer player) {
		BLOOMS.remove(player.getUUID());
		READY.remove(player.getUUID());
	}
}
