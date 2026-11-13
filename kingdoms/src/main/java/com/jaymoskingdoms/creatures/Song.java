package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.Deafened;
import com.jaymoskingdoms.Saves;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The song on the sea cliffs (the harpy's). A singing harpy is heard this far ({@link #HEARD}); each second a hearer rolls a
 * Wits save ({@link Saves.Kind#CHARM}), and one who fails is charmed for a few seconds: their feet are turned toward the singer
 * and walk there, and they will not, by themselves, turn away. A deaf hearer ({@link Deafened}: ears stopped with
 * {@link Creatures#EAR_WAX} or ringing) never hears it, and sailors, who stop their ears with wax before they cast off,
 * never do either: anyone aboard a boat is deaf to it.
 */
public final class Song {
	/** A singing harpy is heard this far (blocks); each second a hearer is tried; the charm lasts this long (ticks). */
	static final double HEARD = 40.0;
	static final int TRY_EVERY = 20, CHARMED_TICKS = 50;
	/** The pull a tick toward the singer on the ground, and how far the song turns the head a tick (degrees). */
	static final double PULL = 0.07, TURN = 7.0;
	/** A harpy sings when someone is in hearing and it is not hurt, with this chance each second. */
	static final float SINGS = 0.6F;
	/** Ears stopped with wax stay stopped this long (ticks). */
	public static final int WAXED_TICKS = 20 * 60 * 6;

	private static final Map<UUID, Long> CHARMED = new HashMap<>();
	private static final Map<UUID, UUID> SINGER = new HashMap<>();

	private Song() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Song::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			CHARMED.clear();
			SINGER.clear();
		});
	}

	/** Whether {@code harpy} is singing just now (someone to hear it, and the mood). */
	static boolean singing(ServerLevel level, Harpy harpy) {
		if (harpy.tickCount % TRY_EVERY != 0) {
			return harpy.look() == Harpy.SINGING;
		}
		boolean heard = false;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceTo(harpy) < HEARD) {
				heard = true;
				break;
			}
		}
		return heard && harpy.getRandom().nextFloat() < SINGS;
	}

	/** Whether {@code who} has their ears stopped for good reason: deaf, or aboard a boat with the crew. */
	static boolean stopped(LivingEntity who) {
		return Deafened.is(who) || who.getVehicle() instanceof AbstractBoat;
	}

	private static void tick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			long now = level.getGameTime();
			for (Harpy h : level.getEntities(Creatures.HARPY, e -> e.isAlive() && e.look() == Harpy.SINGING)) {
				if (now % TRY_EVERY != 0) {
					continue;
				}
				level.playSound(null, h.getX(), h.getY(), h.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.HOSTILE, 3.0F, 0.8F + h.getRandom().nextFloat() * 0.8F);
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, h.getX(), h.getY(1.2), h.getZ(), 1, 0.3, 0.3, 0.3, 1.0);
				for (ServerPlayer p : level.players()) {
					if (p.isSpectator() || p.isCreative() || p.distanceTo(h) > HEARD || stopped(p) || CHARMED.getOrDefault(p.getUUID(), 0L) > now) {
						continue;
					}
					if (!Saves.resist(p, Saves.Kind.CHARM)) {
						CHARMED.put(p.getUUID(), now + CHARMED_TICKS);
						SINGER.put(p.getUUID(), h.getUUID());
					}
				}
			}
			for (ServerPlayer p : level.players()) {
				Long until = CHARMED.get(p.getUUID());
				if (until == null) {
					continue;
				}
				if (until <= now || stopped(p) || !(level.getEntity(SINGER.get(p.getUUID())) instanceof Harpy h) || !h.isAlive()) {
					CHARMED.remove(p.getUUID());
					SINGER.remove(p.getUUID());
					continue;
				}
				Vec3 to = h.position().subtract(p.position());
				double d = Math.sqrt(to.x * to.x + to.z * to.z);
				if (d < 1.5) {
					continue;
				}
				// the feet turn toward the singer, and walk
				float want = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
				float diff = Mth.wrapDegrees(want - p.getYRot());
				float yaw = p.getYRot() + (float) Mth.clamp(diff, -TURN, TURN);
				p.connection.send(new ClientboundPlayerRotationPacket(yaw, false, p.getXRot(), false));
				Vec3 v = p.getDeltaMovement();
				p.setDeltaMovement(v.x + to.x / d * PULL, v.y, v.z + to.z / d * PULL);
				p.syncVelocity = true;
			}
		}
	}
}
