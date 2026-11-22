package com.jaymoskingdoms.music;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Who is in an ordinary fight, for its fight music (the client's {@code SkirmishMusic} plays it). Only a real fight counts,
 * not one stray zombie: {@link #CROWD} foes after you at once, a fight with another player, or a fight still trading blows
 * {@link #LASTS} ticks after it began (a lone foe that only chases you does not count). The fight is over after {@link #CALM} ticks with no foe after you and no blow struck. This side
 * only says "on" a few times a second and "off" when it ends. A great foe's own theme ({@link FoeThemes}) wins on the client.
 */
public final class Skirmishes {
	/** How near a foe after you counts (blocks), and how often fights are looked for (ticks). */
	static final double RANGE = 24.0;
	static final int SCAN_EVERY = 10;
	/** "On" is told again this often while the fight goes on (ticks), so the client can tell it has ended if told no more. */
	public static final int REPEAT_TICKS = 40;
	/** A blow struck or taken this recently keeps you in the fight (ticks). */
	static final int BLOW_FRESH = 100;
	/** Foes after you at once that make a real fight at once, how long a smaller fight must last first, and the calm that ends it (ticks). */
	static final int CROWD = 2, LASTS = 100, CALM = 160;

	/** The fight's music on, or off. */
	public record Play(boolean on) implements CustomPacketPayload {
		public static final Type<Play> TYPE = new Type<>(JaymosKingdoms.id("skirmish"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Play> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Play::on, Play::new);

		@Override
		public Type<Play> type() {
			return TYPE;
		}
	}

	/** Since when the player has been fighting, when they last were, whether the music is on, and when they were last told. */
	private record Fight(long since, long last, boolean on, long told) {
	}

	private static final Map<UUID, Fight> FIGHTS = new HashMap<>();

	private Skirmishes() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Play.TYPE, Play.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Skirmishes::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FIGHTS.remove(handler.player.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> FIGHTS.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FIGHTS.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SCAN_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			scan(player);
		}
	}

	private static void scan(ServerPlayer player) {
		long now = player.level().getGameTime();
		Fight fight = FIGHTS.get(player.getUUID());
		int after = 0;
		boolean blows = false, duel = false;
		if (player.isAlive() && !player.isSpectator() && !player.isCreative()) {
			after = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE), m -> m.isAlive() && m.getTarget() == player).size();
			LivingEntity by = player.getLastHurtByMob(), struck = player.getLastHurtMob();
			boolean hurt = foe(player, by) && player.tickCount - player.getLastHurtByMobTimestamp() < BLOW_FRESH;
			boolean hit = foe(player, struck) && player.tickCount - player.getLastHurtMobTimestamp() < BLOW_FRESH;
			blows = hurt || hit;
			duel = hurt && by instanceof Player || hit && struck instanceof Player;
		}
		boolean engaged = after > 0 || blows;
		if (fight == null && !engaged) {
			return;
		}
		if (!engaged && now - fight.last() >= CALM) {
			FIGHTS.remove(player.getUUID());
			if (fight.on()) {
				ServerPlayNetworking.send(player, new Play(false));
			}
			return;
		}
		// a moment's calm keeps the fight (and its music) going until CALM has passed
		long since = fight == null ? now : fight.since(), last = engaged ? now : fight.last();
		boolean on = fight != null && fight.on() || after >= CROWD || duel || blows && now - since >= LASTS;
		long told = fight == null ? Long.MIN_VALUE : fight.told();
		if (on && (fight == null || !fight.on() || now - told >= REPEAT_TICKS) && ServerPlayNetworking.canSend(player, Play.TYPE)) {
			ServerPlayNetworking.send(player, new Play(true));
			told = now;
		}
		FIGHTS.put(player.getUUID(), new Fight(since, last, on, told));
	}

	/** Whether {@code other} is someone to fight: another player, a monster, or anything after {@code player}. */
	private static boolean foe(ServerPlayer player, @Nullable LivingEntity other) {
		return other != null && other != player && (other instanceof Player || other instanceof Enemy || other instanceof Mob mob && mob.getTarget() == player);
	}
}
