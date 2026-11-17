package com.jaymospowers.power.arsenal;

import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Training;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.wind.AirlessDome;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Rift Seal (Andy's Awakening pick: "Pocket Arsenal's top rank, with our own name and look"). From the last rank of
 * training ({@link #RANK}), C (sprint + C once ascended, whose C is the Hundred Blades): hold it and the rift's edge spreads out
 * round you from {@link #LEAST} to {@link #MOST} blocks; let go and the world inside is folded off into a pocket of its own,
 * walled in void. For as long as it holds:
 * <ul>
 * <li>nobody inside can leave it: whoever reaches the wall is thrown back, and whoever gets out anyway (a pearl, a gate, a
 * portal) is pulled back in;</li>
 * <li>every player and creature inside but you has no powers: their power keys are refused (the Eight-Sign Circle's silencer
 * asks {@link #sealed}), Jaymo's Kingdoms' casters are hushed ({@link #TAG}) and its forms sealed, a gifted villager's power
 * sleeps, and an illager's spell falls apart;</li>
 * <li>it drains you every second: hunger, then, once you are starving, your health.</li>
 * </ul>
 * C again lets it go; it gives way on its own after {@link #TICKS} ticks, or if you die or leave it.
 */
public final class ArsenalSeal {
	// ---- tuning ----
	/** The training rank it comes at: the last. */
	public static final int RANK = Training.RANKS;
	/** Its reach (blocks): where it starts, the most, how fast it spreads while C is held (a tick). */
	static final double LEAST = 9.0, MOST = 20.0, SPREAD = 0.25;
	/** How long it may hold, the wait after (ticks). */
	static final int TICKS = 20 * 60, COOLDOWN = 20 * 180;
	/** The drain each second: hunger (exhaustion; 4 is a food point), then health once food is at or under {@link #STARVING}. */
	static final float DRAIN_EXHAUSTION = 3.0F, DRAIN_HEALTH = 1.0F;
	static final int STARVING = 6;
	/** How hard the wall throws back. */
	static final double WALL_PUSH = 0.6;
	/** The scoreboard tag on whoever is inside; Kingdoms' Will reads it as hushed. */
	public static final String TAG = "jaymospowers_sealed";
	static final int VOID = 0x2A0E45, RIM = 0xC07CFF;

	private static final class Seal {
		final UUID owner;
		final ServerLevel level;
		Vec3 at;
		double radius = LEAST;
		boolean grown;
		long until;
		final Set<Entity> inside = new HashSet<>();

		Seal(UUID owner, ServerLevel level, Vec3 at) {
			this.owner = owner;
			this.level = level;
			this.at = at;
		}
	}

	private static final Map<UUID, Seal> SEALS = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();
	/** Everyone inside a standing seal, for the silencer. */
	private static final Set<UUID> SEALED = new HashSet<>();

	private ArsenalSeal() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ArsenalSeal::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SEALS.values().forEach(s -> s.inside.forEach(e -> e.removeTag(TAG)));
			SEALS.clear();
			READY.clear();
			SEALED.clear();
		});
	}

	/** Whether {@code player} has grown into it (both sides). */
	public static boolean unlocked(Player player) {
		return Mastery.rank(player) >= RANK || player.isCreative();
	}

	/** Whether {@code e} stands inside someone else's Rift Seal and so has no powers (public: the Eight-Sign silencer asks). */
	public static boolean sealed(Entity e) {
		return !SEALED.isEmpty() && SEALED.contains(e.getUUID());
	}

	/** Whether {@code player}'s seal is spreading or standing (so C's letting go is the seal's). */
	static boolean busy(ServerPlayer player) {
		return SEALS.containsKey(player.getUUID());
	}

	static void lost(ServerPlayer player) {
		Seal seal = SEALS.get(player.getUUID());
		if (seal != null) {
			open(seal);
			SEALS.remove(player.getUUID());
		}
	}

	/** C pressed ({@code down}) or let go. True if it was the seal's. */
	static boolean key(ServerPlayer player, boolean down) {
		Seal seal = SEALS.get(player.getUUID());
		if (!down) {
			if (seal != null && !seal.grown) {
				close(player, seal);
				return true;
			}
			return seal != null;
		}
		if (seal != null) {
			// C again: let it go
			if (seal.grown) {
				open(seal);
				SEALS.remove(player.getUUID());
			}
			return true;
		}
		long now = player.level().getGameTime();
		long ready = READY.getOrDefault(player.getUUID(), 0L);
		if (ready > now && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.seal_wait", (ready - now + 19) / 20).withStyle(ChatFormatting.DARK_PURPLE));
			return true;
		}
		SEALS.put(player.getUUID(), new Seal(player.getUUID(), player.level(), player.position()));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.6F, 0.5F);
		return true;
	}

	/** Let go of C: the edge closes where it has spread to. */
	private static void close(ServerPlayer player, Seal seal) {
		ServerLevel level = player.level();
		seal.at = player.position();
		seal.grown = true;
		seal.until = level.getGameTime() + TICKS;
		READY.put(player.getUUID(), level.getGameTime() + COOLDOWN);
		MagicFx.blast(level, seal.at.add(0.0, 1.0, 0.0), (float)seal.radius * 0.4F, RIM);
		MagicFx.sigil(level, seal.at.add(0.0, 0.05, 0.0), (float)seal.radius, VOID, 30);
		level.playSound(null, seal.at.x, seal.at.y, seal.at.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, seal.at.x, seal.at.y, seal.at.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.2F, 0.5F);
	}

	/** The seal gives way: everyone inside has their powers back. */
	private static void open(Seal seal) {
		seal.inside.forEach(e -> {
			e.removeTag(TAG);
			SEALED.remove(e.getUUID());
		});
		seal.inside.clear();
		if (seal.grown) {
			MagicFx.ring(seal.level, seal.at.add(0.0, 0.1, 0.0), (float)seal.radius, RIM);
			seal.level.playSound(null, seal.at.x, seal.at.y, seal.at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5F, 0.5F);
		}
	}

	private static void tick(MinecraftServer server) {
		if (SEALS.isEmpty()) {
			return;
		}
		for (Iterator<Seal> it = SEALS.values().iterator(); it.hasNext();) {
			Seal seal = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(seal.owner);
			ServerLevel level = seal.level;
			long now = level.getGameTime();
			if (owner == null || !owner.isAlive() || !com.jaymospowers.core.Powers.has(owner, ArsenalPower.INSTANCE)
				|| seal.grown && (now >= seal.until || owner.level() != level || owner.position().distanceToSqr(seal.at) > seal.radius * seal.radius * 1.5)) {
				open(seal);
				it.remove();
				continue;
			}
			if (!seal.grown) {
				// C held: the edge spreads
				seal.at = owner.position();
				seal.radius = Math.min(MOST, seal.radius + SPREAD);
				if (now % 4 == 0) {
					MagicFx.ring(level, seal.at.add(0.0, 0.1, 0.0), (float)seal.radius, RIM);
				}
				owner.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.seal_spread", Math.round(seal.radius)).withStyle(ChatFormatting.DARK_PURPLE));
				continue;
			}
			if (now % 10 == 0) {
				MagicFx.orb(level, seal.at, (float)seal.radius, VOID, 12);
			}
			if (now % 20 == 0 && !owner.getAbilities().instabuild) {
				// it drains you
				if (owner.getFoodData().getFoodLevel() > STARVING) {
					owner.causeFoodExhaustion(DRAIN_EXHAUSTION);
				} else {
					owner.hurtServer(level, level.damageSources().magic(), DRAIN_HEALTH);
				}
			}
			double r2 = seal.radius * seal.radius;
			// whoever is inside is held: those at the wall thrown back, those who got out pulled back
			for (Iterator<Entity> in = seal.inside.iterator(); in.hasNext();) {
				Entity e = in.next();
				if (e.isRemoved() || e instanceof LivingEntity l && !l.isAlive()) {
					e.removeTag(TAG);
					SEALED.remove(e.getUUID());
					in.remove();
					continue;
				}
				if (e.level() != level) {
					if (e instanceof ServerPlayer p) {
						p.teleportTo(level, seal.at.x, seal.at.y, seal.at.z, Set.of(), p.getYRot(), p.getXRot(), false);
					}
					continue;
				}
				Vec3 off = e.position().subtract(seal.at);
				double d2 = off.lengthSqr();
				Vec3 in2 = d2 < 1.0E-4 ? Vec3.ZERO : off.normalize().reverse();
				if (d2 > r2) {
					Vec3 back = seal.at.add(off.normalize().scale(seal.radius - 1.5));
					e.teleportTo(back.x, back.y, back.z);
					e.setDeltaMovement(in2.scale(WALL_PUSH));
					e.syncVelocity = true;
					level.sendParticles(MagicFx.VOID, back.x, back.y + 1.0, back.z, 20, 0.3, 0.6, 0.3, 0.1);
				} else if (d2 > (seal.radius - 1.0) * (seal.radius - 1.0)) {
					e.setDeltaMovement(in2.scale(WALL_PUSH).add(0.0, 0.1, 0.0));
					e.syncVelocity = true;
					Vec3 wall = seal.at.add(off.normalize().scale(seal.radius));
					level.sendParticles(MagicFx.wisp(RIM, 1.0F), wall.x, wall.y + 1.0, wall.z, 6, 0.2, 0.6, 0.2, 0.02);
				}
				if (e instanceof LivingEntity l) {
					AirlessDome.breakSpell(l);
				}
			}
			// whoever comes into it is sealed in too (the owner is held in but keeps their power)
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(seal.at, seal.at).inflate(seal.radius),
				e -> e.isAlive() && !e.isSpectator() && e.position().distanceToSqr(seal.at) <= r2)) {
				if (seal.inside.add(e) && e != owner) {
					e.addTag(TAG);
					SEALED.add(e.getUUID());
				}
			}
		}
	}
}
