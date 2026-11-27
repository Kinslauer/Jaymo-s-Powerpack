package com.jaymospowers.alchemy.bomb;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The two states the last two bombs leave (Hunter's Road, patch 7, "D5"), as effects any living thing can carry. Nothing here does
 * the stopping itself: each place that must stop asks {@link #snuffed} or {@link #unhealed}, and Kingdoms asks the same by the
 * effects' ids ({@code jaymospowers:snuffed}, {@code jaymospowers:unhealing}).
 * <ul>
 * <li><b>Snuffed</b> (the magic snuffer): no teleporting (mobs' and pearls' and chorus fruit's), no portals, no casting (Spell Engine's
 * spells, Kingdoms' spells, forbidden arts, a vampire's leap), and, briefly, no powers: the holder's keys do nothing and its
 * passive tick pauses, as under {@link com.jaymospowers.alchemy.picks.Dampening} (and a great foe does not shrug it off: it is
 * only {@link #SNUFF_TICKS} long). The bomb also leaves a {@link Zone} where it fell, for {@link #SNUFF_TICKS}, that snuffs
 * everything inside it and shuts the portals standing in it.</li>
 * <li><b>Unhealing</b> (the healing-stopper dust): nothing heals ({@code LivingEntity.heal} does nothing: a troll's mending, a
 * gnawer's knitting, a vampire's regeneration, a potion's), invisibility is lost and cannot be held (a fog-wight, a vampire's mist),
 * and the changing of a body is stopped (a vampire's bat and mist, a werewolf's turning).</li>
 * </ul>
 */
public final class BombStatus {
	/** How long snuffing lasts, and how long the zone stands (ticks); how long the dust holds (ticks). */
	public static final int SNUFF_TICKS = 20 * 12, UNHEAL_TICKS = 20 * 25;
	/** How often a zone renews the snuffing of what stands in it (ticks); how long each renewal is. */
	public static final int ZONE_EVERY = 10, ZONE_RENEW = 30;

	public static Holder<MobEffect> SNUFFED, UNHEALING;

	/** A snuffed place: a sphere of {@code radius} blocks round {@code at} until game time {@code until}. */
	public record Zone(ResourceKey<Level> dimension, BlockPos at, int radius, long until) {
		boolean holds(Level level, BlockPos pos) {
			return level.dimension().equals(this.dimension) && this.at.distSqr(pos) <= (double) this.radius * this.radius && level.getGameTime() < this.until;
		}
	}

	private static final List<Zone> ZONES = new ArrayList<>();

	private BombStatus() {
	}

	static void init() {
		SNUFFED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id("snuffed")),
			new Status(0x6A5A8A));
		UNHEALING = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id("unhealing")),
			new Status(0x8A6A5A));
		Powers.addSilencer(BombStatus::snuffed);
		Powers.addPauser(BombStatus::snuffed);
		ServerTickEvents.END_SERVER_TICK.register(BombStatus::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ZONES.clear());
	}

	/** Whether {@code who} cannot teleport, cast or use a power just now. */
	public static boolean snuffed(LivingEntity who) {
		return SNUFFED != null && (who.hasEffect(SNUFFED) || zone(who.level(), who.blockPosition()));
	}

	/** {@link #snuffed(LivingEntity)} for a player (a power's silencer). */
	public static boolean snuffed(ServerPlayer who) {
		return snuffed((LivingEntity) who);
	}

	/** Whether {@code pos} lies in a snuffed place. */
	public static boolean zone(Level level, BlockPos pos) {
		if (ZONES.isEmpty()) {
			return false;
		}
		for (Zone z : ZONES) {
			if (z.holds(level, pos)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code who} is under the healing-stopper dust. */
	public static boolean unhealed(LivingEntity who) {
		return UNHEALING != null && who.hasEffect(UNHEALING);
	}

	/** Snuffs {@code radius} blocks round {@code at}: everything alive in it now, and a zone that renews it for {@link #SNUFF_TICKS}. */
	static void snuff(ServerLevel level, BlockPos at, int radius) {
		ZONES.add(new Zone(level.dimension(), at.immutable(), radius, level.getGameTime() + SNUFF_TICKS));
		renew(level, at, radius);
	}

	private static void renew(ServerLevel level, BlockPos at, int radius) {
		AABB box = new AABB(at).inflate(radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (e.blockPosition().distSqr(at) <= (double) radius * radius) {
				e.addEffect(new MobEffectInstance(SNUFFED, ZONE_RENEW, 0, false, true, true));
				if (e instanceof ServerPlayer player) {
					Powers.releaseKeys(player);
				}
			}
		}
	}

	/** Puts the dust on {@code who}: it keeps no regeneration and no invisibility. */
	static void stop(LivingEntity who, int ticks) {
		who.removeEffect(MobEffects.REGENERATION);
		who.removeEffect(MobEffects.INVISIBILITY);
		who.addEffect(new MobEffectInstance(UNHEALING, ticks, 0, false, true, true));
	}

	private static void tick(MinecraftServer server) {
		if (ZONES.isEmpty()) {
			return;
		}
		for (Zone z : List.copyOf(ZONES)) {
			ServerLevel level = server.getLevel(z.dimension());
			if (level == null || level.getGameTime() >= z.until()) {
				ZONES.remove(z);
				continue;
			}
			if (level.getGameTime() % ZONE_EVERY == 0) {
				renew(level, z.at(), z.radius());
				Vec3 c = Vec3.atCenterOf(z.at());
				level.sendParticles(MagicFx.PALE, c.x, c.y, c.z, 6, z.radius() * 0.5, z.radius() * 0.3, z.radius() * 0.5, 0.01);
			}
		}
	}

	/** A plain status: an icon and a name; what it does is asked of {@link #snuffed} and {@link #unhealed}. */
	private static final class Status extends MobEffect {
		Status(int color) {
			super(MobEffectCategory.HARMFUL, color);
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
			return true;
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			return false;
		}
	}
}
