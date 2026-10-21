package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Void Gates, the Void Arsenal's V (corrupted, in place of Barrage): the rift learns to open anywhere.
 * <ul>
 * <li><b>V</b>: a gate is fixed where you aim (up to {@link #PLACE_RANGE} blocks), up to {@link #MAX_GATES}; another
 * replaces the oldest. Hold V ({@link #CLOSE_HOLD} ticks) aimed at one of your gates (within {@link #CLOSE_RADIUS}
 * blocks) and it closes.</li>
 * <li><b>Sneak + V looking down</b>: your pocket's rift opens in front of you, as with Z; a gate fixed inside your
 * pocket room is where it lets you in.</li>
 * <li><b>Sneak + V</b>: you step through to the gate you're aiming toward, from any distance and through anything in
 * between (the gate nearest your aim, in this dimension).</li>
 * <li><b>Hold Sneak + V with no gates</b>: a gate is cast out ahead of you, drifting forward (a body-sized outline shows
 * where you'll arrive) until it meets a wall or {@link #PROJECT_MAX} blocks; let go and you're there.</li>
 * </ul>
 * Gates are saved with the player and kept through death; only their owner sees them (as faint void motes).
 */
public final class ArsenalGates {
	public static final int MAX_GATES = 5;
	static final double PLACE_RANGE = 64.0;
	/** How close to your aim a gate must be to be picked (cosine of the angle off your look). */
	static final double AIM_CONE = 0.8;
	/** Holding V this long, aimed this close to one of your gates, closes it. */
	static final double CLOSE_RADIUS = 1.5;
	static final int CLOSE_HOLD = 12;
	/** Sneak + V looking at least this far down opens the pocket's rift instead. */
	static final float POCKET_PITCH = 70.0F;
	/** The cast-out gate: blocks a tick, and the farthest it goes. */
	static final double PROJECT_SPEED = 0.8, PROJECT_MAX = 32.0;
	/** Between two steps through. */
	static final int COOLDOWN = 30;

	public record Gate(ResourceKey<Level> dimension, Vec3 pos) {
		public static final Codec<Gate> CODEC = RecordCodecBuilder.create(i -> i.group(
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Gate::dimension),
				Vec3.CODEC.fieldOf("pos").forGetter(Gate::pos))
			.apply(i, Gate::new));
	}

	/** A player's gates (saved, kept through death). */
	public static final AttachmentType<List<Gate>> GATES = AttachmentRegistry.create(JaymosPowers.id("arsenal_gates"),
		builder -> builder.persistent(Gate.CODEC.listOf()).copyOnDeath());

	private static final DustParticleOptions VOID = new DustParticleOptions(0x5A1E8C, 1.2F);
	private static final DustParticleOptions OUTLINE = new DustParticleOptions(0xC07CFF, 0.8F);

	/** A gate being cast out ahead: where it is now, the way it's going, how far it has gone. */
	private static final class Projection {
		Vec3 pos;
		final Vec3 dir;
		double travelled;
		boolean stopped;

		Projection(Vec3 pos, Vec3 dir) {
			this.pos = pos;
			this.dir = dir;
		}
	}

	private static final Map<UUID, Projection> PROJECTING = new HashMap<>();
	private static final Map<UUID, Long> READY_AT = new HashMap<>();
	/** When plain V went down (a tap places a gate on release; held on a gate, it closes it). */
	private static final Map<UUID, Long> PRESSED = new HashMap<>();

	private ArsenalGates() {
	}

	static List<Gate> gates(ServerPlayer player) {
		List<Gate> gates = player.getAttached(GATES);
		return gates == null ? List.of() : gates;
	}

	/** V, down or up (the Void Arsenal only). */
	static void key(ServerPlayer player, boolean down) {
		if (!down) {
			if (PRESSED.remove(player.getUUID()) != null) {
				place(player);
				return;
			}
			Projection projection = PROJECTING.remove(player.getUUID());
			if (projection != null) {
				step(player, player.level(), projection.pos);
			}
			return;
		}
		if (!player.isShiftKeyDown()) {
			PRESSED.put(player.getUUID(), player.level().getGameTime());
			return;
		}
		ServerLevel level = player.level();
		if (level.getGameTime() < READY_AT.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		if (player.getXRot() >= POCKET_PITCH && ArsenalPower.INSTANCE.slotUnlocked(player, "mode")) {
			// looking down into your pocket: its rift opens in front of you (onto your pocket gate, if you've fixed one there)
			ArsenalPocket.toggle(player);
			return;
		}
		Gate aimed = aimed(player);
		if (aimed != null) {
			step(player, level, aimed.pos());
		} else if (gates(player).stream().noneMatch(g -> g.dimension() == level.dimension())) {
			// no gate in this world: cast one out ahead
			Vec3 look = player.getLookAngle();
			PROJECTING.put(player.getUUID(), new Projection(player.position().add(0.0, 0.1, 0.0), new Vec3(look.x, Math.max(-0.3, look.y), look.z).normalize()));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.3F, 1.8F);
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal_gate_none_aimed").withStyle(ChatFormatting.DARK_PURPLE));
		}
	}

	/** Where a gate would sit, where you aim (null if nothing's there to hold it). */
	private static @Nullable Vec3 aimSpot(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(PLACE_RANGE)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation().add(hit.getDirection().getUnitVec3().scale(0.4));
	}

	/** Your gate where you aim (within {@link #CLOSE_RADIUS}), if there is one. */
	private static @Nullable Gate gateAt(ServerPlayer player) {
		Vec3 at = aimSpot(player);
		if (at == null) {
			return null;
		}
		for (Gate gate : gates(player)) {
			if (gate.dimension() == player.level().dimension() && gate.pos().distanceToSqr(at) < CLOSE_RADIUS * CLOSE_RADIUS) {
				return gate;
			}
		}
		return null;
	}

	/** V held on one of your gates: it closes. */
	private static void closeGate(ServerPlayer player, Gate gate) {
		ServerLevel level = player.level();
		List<Gate> gates = new ArrayList<>(gates(player));
		gates.remove(gate);
		player.setAttached(GATES, gates);
		Vec3 p = gate.pos();
		level.sendParticles(ParticleTypes.PORTAL, p.x, p.y + 0.5, p.z, 30, 0.2, 0.5, 0.2, 0.6);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.ENDER_CHEST_CLOSE, SoundSource.PLAYERS, 0.8F, 0.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal_gate_closed", gates.size(), MAX_GATES)
			.withStyle(ChatFormatting.DARK_PURPLE));
	}

	/** A gate where you aim. */
	private static void place(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 at = aimSpot(player);
		if (at == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal_gate_too_far").withStyle(ChatFormatting.DARK_PURPLE));
			return;
		}
		List<Gate> gates = new ArrayList<>(gates(player));
		while (gates.size() >= MAX_GATES) {
			gates.remove(0);
		}
		gates.add(new Gate(level.dimension(), at));
		player.setAttached(GATES, gates);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.5, at.z, 30, 0.3, 0.6, 0.3, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.8F, 0.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal_gate_placed", gates.size(), MAX_GATES)
			.withStyle(ChatFormatting.DARK_PURPLE));
	}

	/** The gate in this world nearest where you aim (within {@link #AIM_CONE}), whatever's between. */
	private static @Nullable Gate aimed(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Gate best = null;
		double bestDot = AIM_CONE;
		for (Gate gate : gates(player)) {
			if (gate.dimension() != player.level().dimension()) {
				continue;
			}
			Vec3 to = gate.pos().add(0.0, 0.9, 0.0).subtract(eye);
			if (to.lengthSqr() < 4.0) {
				continue;
			}
			double dot = to.normalize().dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = gate;
			}
		}
		return best;
	}

	/** Through the rift to {@code to} (to the nearest spot there you fit). */
	private static void step(ServerPlayer player, ServerLevel level, Vec3 to) {
		Vec3 spot = fit(player, level, to);
		if (spot == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal_gate_blocked").withStyle(ChatFormatting.DARK_PURPLE));
			return;
		}
		Vec3 from = player.position();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 40, 0.4, 0.8, 0.4, 0.08);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9F, 0.6F);
		if (player.isPassenger()) {
			player.stopRiding();
		}
		player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		ArsenalPlaces.arrived(player);
		level.sendParticles(ParticleTypes.PORTAL, spot.x, spot.y + 1.0, spot.z, 60, 0.4, 0.9, 0.4, 0.4);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9F, 0.5F);
		READY_AT.put(player.getUUID(), level.getGameTime() + COOLDOWN);
	}

	/** The nearest spot at {@code at} the player fits: there, a little above, or a little below. */
	static @Nullable Vec3 fit(ServerPlayer player, ServerLevel level, Vec3 at) {
		for (double dy : new double[] {0.0, 0.5, 1.0, 1.5, 2.0, 3.0, -0.5, -1.0, -2.0}) {
			Vec3 spot = at.add(0.0, dy, 0.0);
			AABB box = player.getDimensions(player.getPose()).makeBoundingBox(spot);
			if (level.noCollision(player, box)) {
				return spot;
			}
		}
		return null;
	}

	/** Per tick: the gates shimmer for their owner, and a cast-out gate drifts on. */
	static void tick(ServerPlayer player) {
		ServerLevel level = player.level();
		Long pressed = PRESSED.get(player.getUUID());
		if (pressed != null && level.getGameTime() - pressed >= 2) {
			Gate held = gateAt(player);
			if (held != null) {
				Vec3 p = held.pos();
				show(player, level, ParticleTypes.REVERSE_PORTAL, p.x, p.y + 0.9, p.z, 4, 0.3, 0.6, 0.3, 0.02);
				if (level.getGameTime() - pressed >= CLOSE_HOLD) {
					PRESSED.remove(player.getUUID());
					closeGate(player, held);
				}
			}
		}
		Projection projection = PROJECTING.get(player.getUUID());
		if (projection != null) {
			if (!player.isAlive() || !com.jaymospowers.core.Fate.isCorrupted(player)) {
				PROJECTING.remove(player.getUUID());
				return;
			}
			if (!projection.stopped) {
				Vec3 next = projection.pos.add(projection.dir.scale(PROJECT_SPEED));
				AABB box = player.getDimensions(player.getPose()).makeBoundingBox(next);
				if (!level.noCollision(player, box) || projection.travelled + PROJECT_SPEED > PROJECT_MAX) {
					projection.stopped = true;
				} else {
					projection.pos = next;
					projection.travelled += PROJECT_SPEED;
				}
			}
			outline(player, level, projection.pos);
		}
		if (player.tickCount % 10 == 0) {
			for (Gate gate : gates(player)) {
				if (gate.dimension() == level.dimension() && gate.pos().distanceToSqr(player.position()) < 96.0 * 96.0) {
					Vec3 p = gate.pos();
					show(player, level, ParticleTypes.PORTAL, p.x, p.y + 0.9, p.z, 3, 0.25, 0.6, 0.25, 0.3);
					show(player, level, VOID, p.x, p.y + 0.9, p.z, 2, 0.2, 0.5, 0.2, 0.0);
				}
			}
		}
	}

	/** Where you'll arrive: a body-sized outline (feet ring, head ring, and the sides between). */
	private static void outline(ServerPlayer player, ServerLevel level, Vec3 at) {
		double r = player.getBbWidth() * 0.6;
		double h = player.getBbHeight();
		for (int i = 0; i < 12; i++) {
			double a = i / 12.0 * Math.PI * 2.0;
			double x = at.x + Math.cos(a) * r, z = at.z + Math.sin(a) * r;
			show(player, level, OUTLINE, x, at.y + 0.05, z, 1, 0.0, 0.0, 0.0, 0.0);
			show(player, level, OUTLINE, x, at.y + h, z, 1, 0.0, 0.0, 0.0, 0.0);
			if (i % 3 == 0) {
				for (double y = 0.4; y < h; y += 0.4) {
					show(player, level, OUTLINE, x, at.y + y, z, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
		}
		show(player, level, ParticleTypes.REVERSE_PORTAL, at.x, at.y + h * 0.5, at.z, 2, 0.2, 0.4, 0.2, 0.01);
	}

	private static void show(ServerPlayer player, ServerLevel level, ParticleOptions particle, double x, double y, double z, int count,
		double dx, double dy, double dz, double speed) {
		level.sendParticles(player, particle, true, false, x, y, z, count, dx, dy, dz, speed);
	}

	static void forget(ServerPlayer player) {
		PROJECTING.remove(player.getUUID());
		PRESSED.remove(player.getUUID());
		READY_AT.remove(player.getUUID());
	}

	/** Out of the void (purified, or the power gone): the gates close. */
	static void close(ServerPlayer player) {
		forget(player);
		player.removeAttached(GATES);
	}
}
