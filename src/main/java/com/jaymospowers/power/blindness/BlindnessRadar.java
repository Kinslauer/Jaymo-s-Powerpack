package com.jaymospowers.power.blindness;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * The radar sense of someone who can't see ({@link Blind#sightless}): a feel for what is near that no flash can take.
 * <ul>
 * <li><b>Flash-proof:</b> the Blindness and Darkness effects other powers, weapons and bombs throw (a flare, a smoke
 * bomb, a sun blast) do nothing to them: they are taken off again at once. The blindfold's own blindness (short and
 * ambient) is left alone.</li>
 * <li><b>Contacts:</b> every {@link #SCAN_EVERY} ticks the hostile creatures within {@link #RANGE} blocks are felt and
 * sent to the owner's screen ({@link Contacts}), nearest {@link #MAX_CONTACTS} first.</li>
 * <li><b>Warning:</b> a hostile creature behind you ({@link #BEHIND_COS}) or out of sight, within {@link #WARN_RANGE},
 * shows as a red mark pinned to that side of the screen and sounds a low knock and a chime ({@link #WARN_GAP} ticks
 * between warnings for the same creature).</li>
 * <li>The creature a pursuit has marked ({@link BlindnessPursuit}) shows in gold.</li>
 * </ul>
 */
public final class BlindnessRadar {
	// ---- tuning ----
	/** How far the radar feels (blocks), how often it looks (ticks), and the most contacts sent. */
	static final double RANGE = 24.0;
	static final int SCAN_EVERY = 10, MAX_CONTACTS = 8;
	/** Behind you: the cosine of the angle off your look, below which a creature is behind (-0.17 is about 100 degrees). */
	static final double BEHIND_COS = -0.17;
	/** How near a creature behind or out of sight sounds the warning (blocks), and the wait before it sounds again for the same one (ticks). */
	static final double WARN_RANGE = 14.0;
	static final int WARN_GAP = 60;
	/** The blindfold's own blindness is ambient and no longer than this (ticks); anything else is a flash. */
	static final int BLINDFOLD_MAX = BlindnessDevotion.BLINDFOLD_BLIND_TICKS;

	/** What kind of contact a mark is. */
	public static final byte AHEAD = 0, WARNING = 1, MARKED = 2;

	/** One thing felt: where, and what kind. */
	public record Contact(float x, float y, float z, byte kind) {
	}

	/** Server to the owner: everything the radar feels now (empty clears the screen). */
	public record Contacts(List<Contact> list) implements CustomPacketPayload {
		public static final Type<Contacts> TYPE = new Type<>(JaymosPowers.id("blindness_radar"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Contacts> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.list.size());
				for (Contact c : p.list) {
					buf.writeFloat(c.x);
					buf.writeFloat(c.y);
					buf.writeFloat(c.z);
					buf.writeByte(c.kind);
				}
			},
			buf -> {
				int n = Math.min(buf.readVarInt(), 32);
				List<Contact> list = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					list.add(new Contact(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte()));
				}
				return new Contacts(list);
			});

		@Override
		public Type<Contacts> type() {
			return TYPE;
		}
	}

	/** Creature id to the game time it last warned someone (per owner), and who has contacts on their screen. */
	private static final Map<UUID, Map<Integer, Long>> WARNED = new HashMap<>();
	private static final Map<UUID, Boolean> SHOWING = new HashMap<>();

	private BlindnessRadar() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Contacts.TYPE, Contacts.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(BlindnessRadar::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			WARNED.remove(handler.player.getUUID());
			SHOWING.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WARNED.clear();
			SHOWING.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean sense = !player.isSpectator() && player.isAlive() && Blind.sightless(player);
			if (sense) {
				proof(player);
			}
			if (player.tickCount % SCAN_EVERY != 0) {
				continue;
			}
			if (!sense) {
				if (SHOWING.remove(player.getUUID()) != null) {
					WARNED.remove(player.getUUID());
					send(player, new Contacts(List.of()));
				}
				continue;
			}
			scan(player);
		}
	}

	/** Takes a flash's Blindness or Darkness off again; the blindfold's own short, ambient blindness stays. */
	private static void proof(ServerPlayer player) {
		MobEffectInstance blind = player.getEffect(MobEffects.BLINDNESS);
		if (blind != null && (!blind.isAmbient() || blind.getDuration() > BLINDFOLD_MAX || blind.getAmplifier() > 0)) {
			player.removeEffect(MobEffects.BLINDNESS);
		}
		if (player.hasEffect(MobEffects.DARKNESS)) {
			player.removeEffect(MobEffects.DARKNESS);
		}
	}

	private static void scan(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
		flatLook = flatLook.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flatLook.normalize();
		List<Mob> found = new ArrayList<>(level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE),
			m -> m instanceof Enemy && m.isAlive() && m.distanceToSqr(player) <= RANGE * RANGE));
		found.sort(Comparator.comparingDouble(m -> m.distanceToSqr(player)));
		Map<Integer, Long> warned = WARNED.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
		warned.values().removeIf(at -> now - at > WARN_GAP * 4);
		List<Contact> out = new ArrayList<>();
		boolean knock = false;
		Mob marked = BlindnessPursuit.target(player);
		for (Mob mob : found) {
			Vec3 to = mob.getEyePosition().subtract(eye);
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			double cos = flat.lengthSqr() < 1.0E-4 ? 1.0 : flat.normalize().dot(flatLook);
			boolean hidden = cos < BEHIND_COS || !player.hasLineOfSight(mob);
			byte kind = mob == marked ? MARKED : hidden && mob.distanceToSqr(player) <= WARN_RANGE * WARN_RANGE ? WARNING : AHEAD;
			if (kind == WARNING) {
				Long last = warned.get(mob.getId());
				if (last == null || now - last >= WARN_GAP) {
					warned.put(mob.getId(), now);
					knock = true;
				}
			}
			if (out.size() < MAX_CONTACTS) {
				out.add(new Contact((float)mob.getX(), (float)(mob.getY() + mob.getBbHeight() * 0.5), (float)mob.getZ(), kind));
			}
		}
		if (marked != null && out.stream().noneMatch(c -> c.kind == MARKED) && marked.distanceToSqr(player) <= RANGE * RANGE * 4.0) {
			out.add(new Contact((float)marked.getX(), (float)(marked.getY() + marked.getBbHeight() * 0.5), (float)marked.getZ(), MARKED));
		}
		if (knock) {
			player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_BASEDRUM, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(),
				0.9F, 0.6F, level.getRandom().nextLong()));
			player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(),
				0.5F, 0.7F, level.getRandom().nextLong()));
		}
		if (out.isEmpty() && SHOWING.remove(player.getUUID()) == null) {
			return;
		}
		if (!out.isEmpty()) {
			SHOWING.put(player.getUUID(), Boolean.TRUE);
		}
		send(player, new Contacts(out));
	}

	private static void send(ServerPlayer player, Contacts contacts) {
		if (ServerPlayNetworking.canSend(player, Contacts.TYPE)) {
			ServerPlayNetworking.send(player, contacts);
		}
	}
}
