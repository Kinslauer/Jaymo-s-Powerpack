package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * (Awakening pick) Who did what. While a boss (the vanilla four and Kingdoms' boss-bar mobs, {@code Relics#boss}) is fought,
 * the harm each player deals it is tallied (by their own hand, their shots, their power's moves: whatever names them). When
 * it falls, everyone who fought it gets a card on screen ({@link Card}, drawn by the client's {@code BossShareCard}): the
 * fighters, most harm first, with each one's share; not chat.
 */
public final class BossShare {
	/** The most fighters a card names (the one it is sent to is always on it). */
	public static final int MOST_NAMED = 6;

	/** Server → client: a boss fell; the harm dealt it in all, its fighters' names and harm (most first) and which of them you are (-1: none). */
	public record Card(String boss, float total, List<String> names, List<Float> harm, int you) implements CustomPacketPayload {
		public static final Type<Card> TYPE = new Type<>(JaymosPowers.id("boss_share"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Card> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeUtf(p.boss);
			buf.writeFloat(p.total);
			buf.writeVarInt(p.names.size());
			for (int i = 0; i < p.names.size(); i++) {
				buf.writeUtf(p.names.get(i));
				buf.writeFloat(p.harm.get(i));
			}
			buf.writeVarInt(p.you + 1);
		}, buf -> {
			String boss = buf.readUtf();
			float total = buf.readFloat();
			int n = buf.readVarInt();
			List<String> names = new ArrayList<>(n);
			List<Float> harm = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				names.add(buf.readUtf());
				harm.add(buf.readFloat());
			}
			return new Card(boss, total, names, harm, buf.readVarInt() - 1);
		});

		@Override
		public Type<Card> type() {
			return TYPE;
		}
	}

	/** A boss's fight: each fighter's harm dealt (in the order they joined), and its health after the last blow. */
	private static final class Fight {
		final Map<UUID, Float> harm = new LinkedHashMap<>();
		final Map<UUID, String> names = new HashMap<>();
		float health;
	}

	private static final Map<UUID, Fight> FIGHTS = new HashMap<>();

	private BossShare() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Card.TYPE, Card.CODEC);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.getEntity() instanceof ServerPlayer player && com.jaymospowers.relics.Relics.boss(entity)) {
				Fight fight = FIGHTS.computeIfAbsent(entity.getUUID(), id -> {
					Fight f = new Fight();
					f.health = entity.getMaxHealth();
					return f;
				});
				fight.harm.merge(player.getUUID(), taken, Float::sum);
				fight.names.put(player.getUUID(), player.getGameProfile().name());
				fight.health = Math.max(0.0F, entity.getHealth());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Fight fight = FIGHTS.remove(entity.getUUID());
			if (source.getEntity() instanceof ServerPlayer player && com.jaymospowers.relics.Relics.boss(entity)) {
				// the blow that fells is never seen above (no AFTER_DAMAGE on a killing blow): it counts what was left
				if (fight == null) {
					fight = new Fight();
					fight.health = entity.getMaxHealth();
				}
				fight.harm.merge(player.getUUID(), fight.health, Float::sum);
				fight.names.put(player.getUUID(), player.getGameProfile().name());
			}
			if (fight != null) {
				fell(entity, fight);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (!(entity instanceof LivingEntity living) || !living.isDeadOrDying()) {
				FIGHTS.remove(entity.getUUID());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> FIGHTS.clear());
	}

	private static void fell(LivingEntity boss, Fight fight) {
		List<Map.Entry<UUID, Float>> order = new ArrayList<>(fight.harm.entrySet());
		order.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
		String name = boss.getDisplayName().getString();
		float total = 0.0F;
		for (Map.Entry<UUID, Float> e : order) {
			total += e.getValue();
		}
		for (int me = 0; me < order.size(); me++) {
			if (!(((net.minecraft.server.level.ServerLevel)boss.level()).getServer().getPlayerList().getPlayer(order.get(me).getKey()) instanceof ServerPlayer player)) {
				continue;
			}
			List<String> names = new ArrayList<>();
			List<Float> harm = new ArrayList<>();
			int shown = Math.min(order.size(), MOST_NAMED);
			for (int i = 0; i < shown; i++) {
				// the last line is yours, if you'd not be named otherwise
				int pick = i == shown - 1 && me >= shown ? me : i;
				names.add(fight.names.getOrDefault(order.get(pick).getKey(), "?"));
				harm.add(order.get(pick).getValue());
			}
			// (Powerborne picks) a fighter who did a tenth of the harm may be given a costume pattern
			com.jaymospowers.costume.CostumePatterns.bossReward(player, total > 0.0F ? order.get(me).getValue() / total : 0.0F);
			int you = Math.min(me, shown - 1);
			ServerPlayNetworking.send(player, new Card(name, total, names, harm, you));
		}
	}
}
