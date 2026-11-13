package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Feats at milestones (fifth edition's feats). Each knowledge level a player reaches ({@link Knowledge}: a great foe slain) owes
 * them one feat, picked from the {@link Feat} list on the feats screen (the H menu's Feats tab). A pick is for keeps; only a Writ of
 * Rebirth ({@link Classes}) unmakes the picks, and then they are owed again. Players who already had levels when feats came in are
 * owed one for each, as the owing is worked out from the levels, not stored.
 * <p>
 * What each feat does lives with the system it changes: {@link FeatsBody} (Alert, Lucky, Mobile, Tough, Skulker, Observant, Dungeon
 * Delver), {@link FeatsFight} (Sentinel, Shield Master, Mage Slayer, Mounted Fighter, Charger, Crossbow Expert) and
 * {@link FeatsTrade} (Healer, Chef, Linguist, Actor, Inspiring Leader).
 */
public final class Feats {
	/** The feats. */
	public enum Feat {
		ALERT, LUCKY, MOBILE, SENTINEL, TOUGH, HEALER, CHEF, SHIELD_MASTER, MAGE_SLAYER, SKULKER, OBSERVANT, MOUNTED_FIGHTER, CHARGER,
		LINGUIST, ACTOR, INSPIRING_LEADER, CROSSBOW_EXPERT, DUNGEON_DELVER;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static @Nullable Feat byKey(String key) {
			for (Feat f : values()) {
				if (f.key().equals(key)) {
					return f;
				}
			}
			return null;
		}
	}

	/** The feats a player has taken, by key. Saved, kept through death, synced to its own player. */
	public static final AttachmentType<List<String>> TAKEN = AttachmentRegistry.create(JaymosKingdoms.id("feats"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	/** Player → server: take feat {@code feat} (its ordinal). */
	public record Pick(int feat) implements CustomPacketPayload {
		public static final Type<Pick> TYPE = new Type<>(JaymosKingdoms.id("feat_pick"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pick> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.feat), buf -> new Pick(buf.readVarInt()));

		@Override
		public Type<Pick> type() {
			return TYPE;
		}
	}

	/** How often (ticks) a player with a pick owed is reminded on the action bar, if they have not been already. */
	static final int REMIND_EVERY = 20 * 30;
	/** The picks owed when each player was last told. */
	private static final Map<UUID, Integer> TOLD = new HashMap<>();

	private Feats() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Pick.TYPE, Pick.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Pick.TYPE, (payload, context) -> context.server().execute(() -> pick(context.player(), payload.feat())));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REMIND_EVERY != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				int owed = owed(player);
				if (owed > 0 && TOLD.getOrDefault(player.getUUID(), 0) < owed && Classes.classOf(player) != null) {
					TOLD.put(player.getUUID(), owed);
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feat.owed", owed).withStyle(net.minecraft.ChatFormatting.GOLD));
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.3F);
				}
			}
		});
	}

	static void clear() {
		TOLD.clear();
	}

	/** The feats unmade with the character. */
	static void unmake(ServerPlayer player) {
		player.removeAttached(TAKEN);
		TOLD.remove(player.getUUID());
	}

	/** The feats {@code player} has taken. */
	public static List<String> taken(Player player) {
		return player.getAttachedOrElse(TAKEN, List.of());
	}

	/** Whether {@code player} has taken {@code feat}. */
	public static boolean has(@Nullable Player player, Feat feat) {
		return player != null && taken(player).contains(feat.key());
	}

	/** By key, for Jaymo's Powers (which reads this by reflection). */
	public static boolean has(Player player, String key) {
		return taken(player).contains(key);
	}

	/** How many feats {@code player} may still pick: a feat for each knowledge level reached. */
	public static int owed(Player player) {
		return Math.max(0, Knowledge.level(player) - taken(player).size());
	}

	private static void pick(ServerPlayer player, int ordinal) {
		Feat[] all = Feat.values();
		if (ordinal < 0 || ordinal >= all.length || owed(player) <= 0 || Classes.classOf(player) == null) {
			return;
		}
		Feat feat = all[ordinal];
		if (has(player, feat)) {
			return;
		}
		List<String> next = new ArrayList<>(taken(player));
		next.add(feat.key());
		player.setAttached(TAKEN, List.copyOf(next));
		TOLD.put(player.getUUID(), owed(player));
		FeatsTrade.taken(player, feat);
		Classes.refresh(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.2F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feat.taken", Component.translatable("feat.jaymoskingdoms." + feat.key()))
			.withStyle(net.minecraft.ChatFormatting.GOLD));
	}

	/** Every feat's say in a blow, for the damage chain: Dungeon Delver's traps, then the fighting feats. */
	public static float dealt(net.minecraft.world.entity.LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
		return FeatsFight.dealt(target, source, FeatsBody.dealt(target, source, amount));
	}

	/** The feats that work through the body: Tough's health, Mobile's stride. */
	static void attributes(ServerPlayer player) {
		double health = has(player, Feat.TOUGH) ? FeatsBody.TOUGH_BASE + FeatsBody.TOUGH_PER_LEVEL * Knowledge.level(player) : 0.0;
		double move = has(player, Feat.MOBILE) ? FeatsBody.MOBILE_SPEED : 0.0;
		float before = player.getMaxHealth();
		Skills.modifier(player, Attributes.MAX_HEALTH, "feat_tough", health, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.MOVEMENT_SPEED, "feat_mobile", move, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (player.getHealth() > player.getMaxHealth() || before > player.getMaxHealth()) {
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
	}
}
