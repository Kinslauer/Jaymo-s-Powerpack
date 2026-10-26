package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Production;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * (0.7.237) The spirit sickness, and the brush with death that starts it. Andy (2026-10-26): "unlock: 1 in 18 per brush with
 * death while attuned, then spirit sickness for up to 5 days (sooner with meditating, casting, nearly dying again); waking
 * cures it." An attuned player with no spirit who is left at a fifth of their health or less (or downed) has a
 * {@link #BRUSH_CHANCE} in chance that something answers. They fall sick: mana comes back at half pace and the spirit-to-be
 * whispers in their sleep, once a day and now and then. The sickness runs five days, shortened by what stirs the spirit:
 * meditating, casting, another brush with death. When it wakes ({@link #wake}) the sickness is cured and the spirit is theirs.
 */
public final class SpiritSickness {
	/** Health share left (or less) that counts as a brush with death, the least time between brushes (ticks), and the odds (1 in). */
	static final float BRUSH_HEALTH = 0.2F;
	static final int BRUSH_EVERY = 2400, BRUSH_CHANCE = 18;
	/** Mana comes back this share as fast while sick. */
	static final float SICK_REGEN = 0.5F;
	/** The sickness lasts this many days; this much push wakes the spirit sooner. */
	static final int SICK_DAYS = 5, PUSH_WAKES = 300;
	/** Push from a further brush with death. A cast adds its cost over {@code PUSH_PER_COST}; a second meditating adds 1. */
	static final int PUSH_BRUSH = 60, PUSH_PER_COST = 5;
	/** The spirit-to-be whispers every this many ticks (at the chance), and always on a new day. */
	static final int SICK_WHISPER_EVERY = 2400;
	static final float SICK_WHISPER_CHANCE = 0.3F;
	/** A new spirit starts at this bond. */
	static final int WOKEN_BOND = 10;

	/** Which spirit stirs, the day (the Overworld's) the sickness began, and how much it has been pushed. */
	public record State(int kind, long sinceDay, int push) {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("kind", 0).forGetter(State::kind),
				Codec.LONG.optionalFieldOf("since", 0L).forGetter(State::sinceDay),
				Codec.INT.optionalFieldOf("push", 0).forGetter(State::push))
			.apply(i, State::new));
		public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, State::kind,
			ByteBufCodecs.VAR_LONG, State::sinceDay,
			ByteBufCodecs.VAR_INT, State::push,
			State::new);

		State pushed(int by) {
			return new State(kind, sinceDay, push + by);
		}
	}

	/** Game time of each player's last brush that counted. */
	private static final Map<UUID, Long> BRUSHED = new HashMap<>();
	/** The day of each player's last whisper from the spirit-to-be. */
	private static final Map<UUID, Long> WHISPERED = new HashMap<>();

	private SpiritSickness() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BRUSHED.clear();
			WHISPERED.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && entity instanceof ServerPlayer player && entity.isAlive()) {
				brush(player);
			}
		});
	}

	static void forget(UUID id) {
		BRUSHED.remove(id);
		WHISPERED.remove(id);
	}

	public static boolean sick(net.minecraft.world.entity.player.Player player) {
		return player.hasAttached(Spirits.SICK);
	}

	/** Mana comes back this many times as fast (1 well, {@link #SICK_REGEN} sick). */
	public static float regenTimes(net.minecraft.world.entity.player.Player player) {
		return sick(player) ? SICK_REGEN : 1.0F;
	}

	/** The cast of {@code price} mana stirs a sleeping spirit. */
	public static void pushed(ServerPlayer player, float price) {
		State state = player.getAttached(Spirits.SICK);
		if (state != null && price > 0.0F) {
			player.setAttached(Spirits.SICK, state.pushed(Math.max(1, Math.round(price / PUSH_PER_COST))));
		}
	}

	/** {@code player} has taken a blow: if it left them at death's door, the spirit may answer (or stir). */
	private static void brush(ServerPlayer player) {
		if (!Mana.attuned(player) || player.hasAttached(Spirits.SPIRIT) || player.getAttachedOrElse(Spirits.RELEASED, false) || player.isCreative()) {
			return;
		}
		boolean low = player.getHealth() <= player.getMaxHealth() * BRUSH_HEALTH || Downed.isDowned(player);
		long now = player.level().getGameTime();
		if (!low || now < BRUSHED.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) + BRUSH_EVERY) {
			return;
		}
		State sick = player.getAttached(Spirits.SICK);
		if (sick != null) {
			// (a further brush with death stirs it more)
			BRUSHED.put(player.getUUID(), now);
			player.setAttached(Spirits.SICK, sick.pushed(PUSH_BRUSH));
			return;
		}
		BRUSHED.put(player.getUUID(), now);
		if (player.getRandom().nextInt(BRUSH_CHANCE) != 0) {
			return;
		}
		int kind = roll(player);
		long day = Production.day(player.level().getServer());
		player.setAttached(Spirits.SICK, new State(kind, day, 0));
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.spirit.sick.begin").withStyle(ChatFormatting.DARK_AQUA));
		JaymosKingdoms.LOGGER.debug("{} brushed death and sickens for spirit {}", player.getGameProfile().name(), kind);
	}

	/**
	 * Which spirit answers: by heritage if it leans (Tideborn, Verdant, Dawnborn to Ambrose; Sylvan, Nightborn to Thessaly;
	 * Stoneborn, Warriorborn to Varro; Emberborn, Earthling to Grimsby), else one of the five at random. The Quiet One is
	 * only ever the random one (one in five of those), never a lean.
	 */
	static int roll(ServerPlayer player) {
		Heritage.Kind heritage = Heritage.of(player);
		if (heritage != null) {
			switch (heritage) {
				case TIDEBORN, VERDANT, DAWNBORN -> {
					return Spirits.AMBROSE;
				}
				case SYLVAN, NIGHTBORN -> {
					return Spirits.THESSALY;
				}
				case STONEBORN, WARRIORBORN -> {
					return Spirits.VARRO;
				}
				case EMBERBORN, EARTHLING -> {
					return Spirits.GRIMSBY;
				}
				default -> {
				}
			}
		}
		return player.getRandom().nextInt(Spirits.QUIET + 1);
	}

	/** Once a second for each sick player: the whisper, the push of meditating, and the waking. */
	static void tick(ServerPlayer player, long now) {
		State state = player.getAttached(Spirits.SICK);
		if (state == null) {
			return;
		}
		if (PowersCompat.meditating(player)) {
			state = state.pushed(1);
			player.setAttached(Spirits.SICK, state);
		}
		long day = Production.day(player.level().getServer());
		if (day >= state.sinceDay() + SICK_DAYS || state.push() >= PUSH_WAKES) {
			wake(player, state);
			return;
		}
		int dayN = (int)Math.clamp(day - state.sinceDay() + 1L, 1L, SICK_DAYS);
		Long last = WHISPERED.get(player.getUUID());
		boolean newDay = last == null || last != day;
		if (newDay || now % SICK_WHISPER_EVERY < 20L && player.getRandom().nextFloat() < SICK_WHISPER_CHANCE) {
			if (SpiritVoice.say(player, state.kind(), "sick." + dayN)) {
				WHISPERED.put(player.getUUID(), day);
			}
		}
	}

	/** The spirit wakes: the sickness is cured, the spirit is theirs. */
	static void wake(ServerPlayer player, State state) {
		player.removeAttached(Spirits.SICK);
		player.setAttached(Spirits.SPIRIT, SpiritState.fresh(state.kind(), WOKEN_BOND));
		WHISPERED.remove(player.getUUID());
		SpiritVoice.sayNow(player, state.kind(), "wake");
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.spirit.woke", Component.translatable("spirit.jaymoskingdoms." + Spirits.of(state.kind()).id() + ".name"))
			.withStyle(ChatFormatting.AQUA));
		ServerLevel level = (ServerLevel)player.level();
		level.sendParticles(Mana.MOTE, player.getX(), player.getY() + 1.0, player.getZ(), 50, 0.5, 0.8, 0.5, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.08);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
		Mana.ripple(player, Mana.WILL);
	}
}
