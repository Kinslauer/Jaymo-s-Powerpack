package com.jaymospowers.aether.sky;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.compat.Stacked;
import com.jaymospowers.power.wind.aether.WindAether;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.jaymospowers.power.gravity.GravityPower;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

/**
 * (0.20.96) Andy (seven-mods plan, patch 10): the Aether's light and heavy islands, and its gas caves.
 * <ul>
 * <li><b>Gravity pockets:</b> the sky is cut into stretches {@link #CELL} blocks across; about one in {@link #LIGHT_ONE_IN}
 * is light (you jump far and fall slowly, and motes drift up round you) and about one in {@link #HEAVY_ONE_IN} is heavy
 * (a short jump, a hard fall, dust settling). Only on the islands (above {@link #ISLAND_FROM} in the Aether's own
 * heights), not in the caverns.</li>
 * <li><b>Gas caves:</b> about one stretch of the Hestveil caverns in {@link #GAS_ONE_IN} ({@link #GAS_CELL} across) is
 * full of a green haze. Breathe it without a {@link Baubles#SKY_MASK} in the head slot and it poisons and sickens you.</li>
 * </ul>
 * The player's gravity, jump and safe fall are attribute modifiers, which sync to them, so their own movement feels it.
 */
public final class SkyPockets {
	static final int EVERY = 20;
	static final int CELL = 160, LIGHT_ONE_IN = 7, HEAVY_ONE_IN = 9;
	static final int ISLAND_FROM = 60;
	static final int GAS_CELL = 96, GAS_ONE_IN = 3, GAS_FROM = 8, GAS_TO = 58;
	static final double LIGHT_GRAVITY = -0.55, HEAVY_GRAVITY = 0.6, LIGHT_JUMP = 0.45, HEAVY_JUMP = -0.2, LIGHT_FALL = 8.0, HEAVY_FALL = -1.0;
	static final Identifier GRAVITY_ID = JaymosPowers.id("sky_pocket_gravity"), JUMP_ID = JaymosPowers.id("sky_pocket_jump"),
		FALL_ID = JaymosPowers.id("sky_pocket_fall");
	static final DustParticleOptions GAS = new DustParticleOptions(0x8FBF4A, 2.0F);

	public enum Pocket {
		NONE, LIGHT, HEAVY
	}

	private SkyPockets() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SkyPockets::tick);
	}

	private static long mix(long seed, int a, int b, long salt) {
		long h = seed ^ salt;
		h ^= a * 0x9E3779B97F4A7C15L;
		h = Long.rotateLeft(h, 23) ^ b * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		return h ^ h >>> 32;
	}

	/** The pocket at {@code (x, z)} of the Aether in the world of {@code seed}. */
	public static Pocket pocket(long seed, int x, int z) {
		long h = Math.floorMod(mix(seed, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL), 0x51C7L), (long) LIGHT_ONE_IN * HEAVY_ONE_IN);
		if (h < HEAVY_ONE_IN) {
			return Pocket.LIGHT;
		}
		return h < HEAVY_ONE_IN + LIGHT_ONE_IN ? Pocket.HEAVY : Pocket.NONE;
	}

	/** Whether the Hestveil at {@code (x, z)} is gassy. */
	public static boolean gassy(long seed, int x, int z) {
		return Math.floorMod(mix(seed, Math.floorDiv(x, GAS_CELL), Math.floorDiv(z, GAS_CELL), 0x6A5L), GAS_ONE_IN) == 0;
	}

	/** (0.20.99) Mobs given a swapped pocket last time, by their level, so they can be let go when it ends. */
	private static final Map<UUID, ServerLevel> SWAPPED_MOBS = new HashMap<>();

	private static long cell(int x, int z) {
		return ((long) Math.floorDiv(x, CELL) << 32) ^ (Math.floorDiv(z, CELL) & 0xFFFFFFFFL);
	}

	/**
	 * (0.20.99) Andy: a gravity user stretches his weightlessness over the island he stands on. While his Weightless is
	 * on and he is in a light or heavy stretch, that stretch turns over (light becomes heavy, heavy light) for every
	 * player and every mob in it, until he switches it off or leaves.
	 */
	public static Pocket swap(Pocket pocket) {
		return pocket == Pocket.LIGHT ? Pocket.HEAVY : pocket == Pocket.HEAVY ? Pocket.LIGHT : Pocket.NONE;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		long seed = server.overworld().getSeed();
		int shift = Stacked.shift(server, WindAether.AETHER);
		// the stretches a weightless gravity user is turning over, and one of them standing in each
		Map<Long, ServerPlayer> swapped = new HashMap<>();
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			if (sp.isAlive() && !sp.isSpectator() && sp.getAttached(GravityPower.WEIGHTLESS_ON) != null && Stacked.in(sp, WindAether.AETHER)
				&& (int) Math.floor(sp.getY()) - shift >= ISLAND_FROM && pocket(seed, sp.getBlockX(), sp.getBlockZ()) != Pocket.NONE) {
				swapped.putIfAbsent(cell(sp.getBlockX(), sp.getBlockZ()), sp);
			}
		}
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			Pocket pocket = Pocket.NONE;
			if (sp.isAlive() && !sp.isSpectator() && Stacked.in(sp, WindAether.AETHER)) {
				ServerLevel level = sp.level();
				int localY = (int) Math.floor(sp.getY()) - shift;
				if (localY >= ISLAND_FROM) {
					pocket = pocket(seed, sp.getBlockX(), sp.getBlockZ());
					if (swapped.containsKey(cell(sp.getBlockX(), sp.getBlockZ()))) {
						pocket = swap(pocket);
					}
				} else if (localY >= GAS_FROM && localY <= GAS_TO && gassy(seed, sp.getBlockX(), sp.getBlockZ())
					&& level.getBiome(sp.blockPosition()).unwrapKey().map(k -> k.identifier().getPath().equals("hestveil_caverns")).orElse(false)) {
					gas(sp, level);
				}
				signs(sp, level, pocket);
			}
			apply(sp, pocket);
		}
		// the mobs in a turned-over stretch feel it too; the rest let go
		Map<UUID, ServerLevel> now = new HashMap<>();
		for (Map.Entry<Long, ServerPlayer> e : swapped.entrySet()) {
			ServerPlayer user = e.getValue();
			ServerLevel level = user.level();
			Pocket turned = swap(pocket(seed, user.getBlockX(), user.getBlockZ()));
			int x0 = Math.floorDiv(user.getBlockX(), CELL) * CELL, z0 = Math.floorDiv(user.getBlockZ(), CELL) * CELL;
			AABB box = new AABB(x0, shift + ISLAND_FROM, z0, x0 + CELL, level.getMaxY(), z0 + CELL);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, box, Mob::isAlive)) {
				apply(mob, turned);
				now.put(mob.getUUID(), level);
			}
		}
		for (Map.Entry<UUID, ServerLevel> e : SWAPPED_MOBS.entrySet()) {
			if (!now.containsKey(e.getKey()) && e.getValue().getEntity(e.getKey()) instanceof LivingEntity mob) {
				apply(mob, Pocket.NONE);
			}
		}
		SWAPPED_MOBS.clear();
		SWAPPED_MOBS.putAll(now);
	}

	private static void apply(LivingEntity sp, Pocket pocket) {
		set(sp, Attributes.GRAVITY, GRAVITY_ID, pocket == Pocket.LIGHT ? LIGHT_GRAVITY : pocket == Pocket.HEAVY ? HEAVY_GRAVITY : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(sp, Attributes.JUMP_STRENGTH, JUMP_ID, pocket == Pocket.LIGHT ? LIGHT_JUMP : pocket == Pocket.HEAVY ? HEAVY_JUMP : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(sp, Attributes.SAFE_FALL_DISTANCE, FALL_ID, pocket == Pocket.LIGHT ? LIGHT_FALL : pocket == Pocket.HEAVY ? HEAVY_FALL : 0.0,
			AttributeModifier.Operation.ADD_VALUE);
	}

	/** Puts the modifier {@code id} on (or, at 0, takes it off). */
	static void set(LivingEntity sp, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance inst = sp.getAttribute(attribute);
		if (inst == null) {
			return;
		}
		AttributeModifier had = inst.getModifier(id);
		if (amount == 0.0) {
			if (had != null) {
				inst.removeModifier(id);
			}
		} else if (had == null || had.amount() != amount) {
			inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	private static void signs(ServerPlayer sp, ServerLevel level, Pocket pocket) {
		if (pocket == Pocket.LIGHT && level.getRandom().nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 0.3, sp.getZ(), 4, 3.0, 0.5, 3.0, 0.02);
		} else if (pocket == Pocket.HEAVY && level.getRandom().nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.ASH, sp.getX(), sp.getY() + 3.0, sp.getZ(), 12, 3.0, 1.0, 3.0, 0.0);
		}
	}

	private static void gas(ServerPlayer sp, ServerLevel level) {
		level.sendParticles(GAS, sp.getX(), sp.getY() + 1.0, sp.getZ(), 10, 3.0, 1.5, 3.0, 0.0);
		if (sp.isCreative() || Baubles.wearing(sp, Baubles.SKY_MASK)) {
			return;
		}
		sp.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, false, true));
		if (level.getRandom().nextInt(4) == 0) {
			sp.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0, false, false));
			level.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, 0.5F, 1.4F);
		}
	}

}
