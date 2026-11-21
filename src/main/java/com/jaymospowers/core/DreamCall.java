package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Phantom;

/**
 * Called in a dream (Andy's Buder's Lanterns pick, ideas only; Andy: "there should be a chance for the view to pan up as you are
 * sleeping indicating the dream, and it takes you into a grue like realm, it is dark during a nightmare, and you awaken a corrupted
 * power if you survive, and a dream is peaceful, and you awaken your power the next day"). Built where the sleeper lies, no realm of
 * its own (Andy, 2026-11-18: "Dream arena").
 * <p>A powerless survival player who falls fast asleep may be called, once a night ({@link #CALL_CHANCE}): their view lifts off the
 * pillow ({@link #PAN_TICKS}; the client's {@code DreamView}), and then
 * <ul>
 * <li><b>a dream</b>: quiet light while they sleep on; the power wakes with them in the morning ({@link #DREAMT}).</li>
 * <li><b>a nightmare</b> ({@link #NIGHTMARE_CHANCE}): they start up into the dark (blind past a few blocks), and phantoms come for
 * them out of it for {@link #NIGHTMARE_TICKS}. Lived through, the power wakes then and there, corrupted. A blow that would bring
 * them to {@link #WAKE_HEALTH} or lower wakes them instead, screaming, with nothing: a nightmare never kills.</li>
 * </ul>
 */
public final class DreamCall {
	// ---- tuning ----
	/** The chance a night's sleep calls a powerless sleeper, and that the call is a nightmare. */
	static final float CALL_CHANCE = 0.35F, NIGHTMARE_CHANCE = 0.3F;
	/** How long the view lifts before the dream (ticks); how long a dream's light lasts; how long a nightmare must be lived through. */
	static final int PAN_TICKS = 70, DREAM_TICKS = 200, NIGHTMARE_TICKS = 1200;
	/** The nightmare's phantoms: how many at first, one more every so often (ticks), at most; how far off they come (blocks). */
	static final int PHANTOMS_FIRST = 2, PHANTOM_EVERY = 300, PHANTOMS_MOST = 5;
	static final double PHANTOM_OFF = 10.0, PHANTOM_UP = 6.0;
	/** A nightmare's blow that would leave them this low or lower wakes them instead. */
	static final float WAKE_HEALTH = 4.0F;
	/** What tags a nightmare's phantoms (they go when it ends, and never stay over a restart). */
	public static final String PHANTOM_TAG = "jaymospowers.dream_phantom";

	/** The kinds of call, as the client is told: none (it's over), a dream, a nightmare. */
	public static final int NONE = 0, DREAM = 1, NIGHTMARE = 2;
	/** The parts of a call: the view lifting, then the dream itself. */
	public static final int PAN = 0, INSIDE = 1;

	/** Server to client: the call of kind {@code kind} is at part {@code part}, for {@code ticks}. */
	public record Dream(int kind, int part, int ticks) implements CustomPacketPayload {
		public static final Type<Dream> TYPE = new Type<>(JaymosPowers.id("dream_call"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Dream> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.kind);
				buf.writeVarInt(p.part);
				buf.writeVarInt(p.ticks);
			},
			buf -> new Dream(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Dream> type() {
			return TYPE;
		}
	}

	/** A peaceful dream was dreamt: the power wakes with them in the morning. Saved. */
	public static final AttachmentType<Boolean> DREAMT = AttachmentRegistry.create(JaymosPowers.id("dreamt"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	private static final class Call {
		final int kind;
		int part = PAN;
		long until;
		long nextPhantom;
		final List<Phantom> phantoms = new ArrayList<>();

		Call(int kind, long until) {
			this.kind = kind;
			this.until = until;
		}
	}

	private static final Map<UUID, Call> CALLS = new HashMap<>();
	/** The night (day number) each player was last rolled for. Not saved: a restart may roll a night again. */
	private static final Map<UUID, Long> ROLLED = new HashMap<>();

	private DreamCall() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Dream.TYPE, Dream.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(DreamCall::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CALLS.clear();
			ROLLED.clear();
		});
		// a nightmare never kills: the blow that would bring them too low wakes them instead
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && CALLS.get(player.getUUID()) instanceof Call c && c.kind == NIGHTMARE && c.part == INSIDE
				&& player.getHealth() - amount <= WAKE_HEALTH) {
				end(player, c, false);
				CALLS.remove(player.getUUID());
				return false;
			}
			return true;
		});
		// a nightmare's phantom left over from before a restart goes
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(PHANTOM_TAG) && !owned(entity)) {
				entity.discard();
			}
		});
	}

	private static boolean owned(net.minecraft.world.entity.Entity e) {
		for (Call c : CALLS.values()) {
			if (c.phantoms.contains(e)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- every tick

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Call c = CALLS.get(player.getUUID());
			if (c != null) {
				step(player, c);
				continue;
			}
			ServerLevel level = player.level();
			if (player.getAttached(DREAMT) != null && !player.isSleeping() && level.isBrightOutside()) {
				// morning: the dream's power wakes with them
				player.removeAttached(DREAMT);
				if (Powers.of(player) == null) {
					Powers.awaken(player);
				}
				continue;
			}
			if (!player.isSleepingLongEnough() || Powers.of(player) != null || player.isCreative() || player.isSpectator()
				|| player.getAttached(DREAMT) != null || com.jaymospowers.alchemy.picks.GivingUp.settled(player)) {
				continue;
			}
			long night = level.getOverworldClockTime() / 24000L;
			if (ROLLED.getOrDefault(player.getUUID(), -1L) == night) {
				continue;
			}
			ROLLED.put(player.getUUID(), night);
			if (level.getRandom().nextFloat() >= CALL_CHANCE) {
				continue;
			}
			int kind = level.getRandom().nextFloat() < NIGHTMARE_CHANCE ? NIGHTMARE : DREAM;
			CALLS.put(player.getUUID(), new Call(kind, level.getGameTime() + PAN_TICKS));
			ServerPlayNetworking.send(player, new Dream(kind, PAN, PAN_TICKS));
		}
		for (Iterator<Map.Entry<UUID, Call>> it = CALLS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Call> e = it.next();
			if (server.getPlayerList().getPlayer(e.getKey()) == null) {
				// they left mid-call: it's over, and its phantoms go
				e.getValue().phantoms.forEach(net.minecraft.world.entity.Entity::discard);
				it.remove();
			}
		}
	}

	private static void step(ServerPlayer player, Call c) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (c.part == PAN) {
			if (!player.isSleeping()) {
				// woken before it came: nothing
				CALLS.remove(player.getUUID());
				ServerPlayNetworking.send(player, new Dream(NONE, PAN, 0));
				return;
			}
			if (now < c.until) {
				return;
			}
			c.part = INSIDE;
			if (c.kind == DREAM) {
				c.until = now + DREAM_TICKS;
				ServerPlayNetworking.send(player, new Dream(DREAM, INSIDE, DREAM_TICKS));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.6F);
			} else {
				c.until = now + NIGHTMARE_TICKS;
				player.stopSleeping();
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, NIGHTMARE_TICKS + 40, 0, false, false));
				for (int i = 0; i < PHANTOMS_FIRST; i++) {
					phantom(level, player, c);
				}
				c.nextPhantom = now + PHANTOM_EVERY;
				ServerPlayNetworking.send(player, new Dream(NIGHTMARE, INSIDE, NIGHTMARE_TICKS));
				player.sendSystemMessage(Component.translatable("message.jaymospowers.dream.nightmare").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 0.6F);
			}
			return;
		}
		if (c.kind == NIGHTMARE) {
			c.phantoms.removeIf(p -> !p.isAlive());
			for (Phantom p : c.phantoms) {
				p.setTarget(player);
			}
			if (now >= c.nextPhantom && c.phantoms.size() < PHANTOMS_MOST) {
				phantom(level, player, c);
				c.nextPhantom = now + PHANTOM_EVERY;
			}
			if (!player.isAlive()) {
				end(player, c, false);
				CALLS.remove(player.getUUID());
				return;
			}
		} else if (!player.isSleeping()) {
			// woken from a dream: it's dreamt all the same
			c.until = now;
		}
		if (now >= c.until) {
			end(player, c, true);
			CALLS.remove(player.getUUID());
		}
	}

	/** One of the nightmare's phantoms, out of the dark above and round them, after them alone. */
	private static void phantom(ServerLevel level, ServerPlayer player, Call c) {
		Phantom p = EntityTypes.PHANTOM.create(level, EntitySpawnReason.EVENT);
		if (p == null) {
			return;
		}
		double a = level.getRandom().nextDouble() * Math.PI * 2.0;
		p.snapTo(player.getX() + Math.cos(a) * PHANTOM_OFF, player.getY() + PHANTOM_UP, player.getZ() + Math.sin(a) * PHANTOM_OFF, (float)Math.toDegrees(a), 0.0F);
		p.addTag(PHANTOM_TAG);
		p.setTarget(player);
		level.addFreshEntity(p);
		c.phantoms.add(p);
	}

	/** The call is over: lived through ({@code through}) or not. */
	private static void end(ServerPlayer player, Call c, boolean through) {
		c.phantoms.forEach(net.minecraft.world.entity.Entity::discard);
		c.phantoms.clear();
		ServerPlayNetworking.send(player, new Dream(NONE, INSIDE, 0));
		if (c.kind == DREAM) {
			player.setAttached(DREAMT, true);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.dream.dreamt").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
			return;
		}
		player.removeEffect(MobEffects.BLINDNESS);
		if (!through) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.dream.woke").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 0.6F);
			return;
		}
		if (Powers.of(player) == null) {
			Powers.awaken(player);
			Fate.corrupt(player);
		}
	}
}
