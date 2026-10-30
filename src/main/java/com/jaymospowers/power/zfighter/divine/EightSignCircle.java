package com.jaymospowers.power.zfighter.divine;

import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.48, Weapon Forms patch 6) A Divine-born's Eight-Sign Circle: a ring of eight signs drawn on the ground that lasts
 * {@link #LIFETIME} ticks. Anything inside it other than its caster is sealed: it cannot use powers or forms for as long as
 * it stands in the ring and {@link #LINGER} ticks after it leaves.
 * <ul>
 * <li>Players: the ring is a Powers silencer ({@link Powers#addSilencer}), so every ability, mode and ultimate key press is
 * refused server-side, whatever the power.</li>
 * <li>Everything else, Jaymo's Kingdoms' forms included: {@link #sealed(Entity)} is public and static, and Kingdoms'
 * {@code PowersCompat.sealed} calls it by reflection before a form fires.</li>
 * </ul>
 * Never saved: a ring is gone with the server.
 */
public final class EightSignCircle {
	/** How long a ring stands, how far it reaches (blocks), and how high above and below its centre it holds. */
	public static final int LIFETIME = 160;
	public static final double RADIUS = 6.5, HEIGHT = 4.0;
	/** How long a seal outlasts stepping out (ticks). */
	public static final int LINGER = 30;
	/** Ki the caster pays, and the wait before the next ring (ticks). */
	public static final float KI_COST = 35.0F;
	public static final int COOLDOWN = 400;
	private static final int SIGNS = 8, RING_POINTS = 48;
	private static final DustParticleOptions GOLD = new DustParticleOptions(0xF2D26B, 1.0F);

	private record Ring(ResourceKey<Level> dimension, Vec3 centre, UUID caster, long until) {
	}

	private static final List<Ring> RINGS = new ArrayList<>();
	/** When each sealed creature's seal runs out (game time). */
	private static final Map<UUID, Long> SEALED = new HashMap<>();

	private EightSignCircle() {
	}

	public static void init() {
		Powers.addSilencer(EightSignCircle::sealed);
		ServerTickEvents.END_SERVER_TICK.register(EightSignCircle::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
	}

	public static void clear() {
		RINGS.clear();
		SEALED.clear();
	}

	/**
	 * Whether {@code entity} is sealed right now by an Eight-Sign Circle: it can't use powers or forms. Public so that
	 * Jaymo's Kingdoms can ask by reflection.
	 */
	public static boolean sealed(Entity entity) {
		if (SEALED.isEmpty()) {
			return false;
		}
		Long until = SEALED.get(entity.getUUID());
		return until != null && until > entity.level().getGameTime();
	}

	/** Draws a ring at {@code centre} for {@code caster}. */
	public static void open(ServerPlayer caster) {
		ServerLevel level = caster.level();
		Vec3 centre = caster.position();
		RINGS.removeIf(r -> r.caster().equals(caster.getUUID()));
		RINGS.add(new Ring(level.dimension(), centre, caster.getUUID(), level.getGameTime() + LIFETIME));
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.7F);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
		draw(level, centre, 1.0);
	}

	private static void tick(MinecraftServer server) {
		if (RINGS.isEmpty() && SEALED.isEmpty()) {
			return;
		}
		SEALED.values().removeIf(until -> until <= server.overworld().getGameTime());
		for (Iterator<Ring> it = RINGS.iterator(); it.hasNext(); ) {
			Ring ring = it.next();
			ServerLevel level = server.getLevel(ring.dimension());
			if (level == null || level.getGameTime() >= ring.until()) {
				it.remove();
				if (level != null) {
					level.playSound(null, ring.centre().x, ring.centre().y, ring.centre().z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
				}
				continue;
			}
			long now = level.getGameTime();
			AABB box = new AABB(ring.centre(), ring.centre()).inflate(RADIUS, HEIGHT, RADIUS);
			for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.getUUID().equals(ring.caster()))) {
				double dx = living.getX() - ring.centre().x, dz = living.getZ() - ring.centre().z;
				if (dx * dx + dz * dz > RADIUS * RADIUS) {
					continue;
				}
				boolean fresh = !sealed(living);
				SEALED.put(living.getUUID(), now + LINGER);
				if (fresh && living instanceof ServerPlayer player) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_eight_sign.sealed").withStyle(ChatFormatting.GOLD));
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.6F);
				}
			}
			if (now % 5 == 0) {
				double fade = Math.min(1.0, (ring.until() - now) / 20.0);
				draw(level, ring.centre(), fade);
			}
		}
	}

	/** The ring (gold motes) with the eight signs (light columns) on it, following the ground. */
	private static void draw(ServerLevel level, Vec3 centre, double strength) {
		for (int i = 0; i < RING_POINTS; i++) {
			double a = i * Math.PI * 2.0 / RING_POINTS;
			double x = centre.x + Math.cos(a) * RADIUS, z = centre.z + Math.sin(a) * RADIUS;
			level.sendParticles(GOLD, true, false, x, groundY(level, x, centre.y, z) + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		for (int i = 0; i < SIGNS; i++) {
			double a = i * Math.PI * 2.0 / SIGNS;
			double x = centre.x + Math.cos(a) * RADIUS, z = centre.z + Math.sin(a) * RADIUS;
			double y = groundY(level, x, centre.y, z);
			level.sendParticles(ParticleTypes.END_ROD, true, false, x, y + 0.4, z, 2, 0.05, 0.4 * strength, 0.05, 0.01);
			level.sendParticles(ParticleTypes.ENCHANT, true, false, x, y + 1.0, z, 3, 0.2, 0.4, 0.2, 0.3);
			// the sign's inward stroke, toward the centre
			double ix = centre.x + Math.cos(a) * RADIUS * 0.8, iz = centre.z + Math.sin(a) * RADIUS * 0.8;
			level.sendParticles(GOLD, true, false, ix, groundY(level, ix, centre.y, iz) + 0.1, iz, 1, 0.1, 0.0, 0.1, 0.0);
		}
	}

	/** The top of the ground at (x, z) near {@code y}: the first solid floor found going down from two blocks above. */
	private static double groundY(ServerLevel level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Math.floor(x), Math.floor(y) + 2, Math.floor(z));
		for (int i = 0; i < 8; i++) {
			if (!level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
				return pos.getY();
			}
			pos.move(0, -1, 0);
		}
		return y;
	}
}
