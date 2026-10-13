package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.net.PowerPayloads;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A survival player's affinity: picked once, the first time they're in survival without a power, and it decides which
 * power awakens in them (the awakening itself still takes its 3-5 days). "Let fate decide" ({@link #FATE}) keeps the
 * old random awakening.
 */
public final class Affinity {
	/** The chosen power's id, or {@link #FATE}. Saved, kept through death, synced to its owner (for the screens). */
	public static final AttachmentType<Identifier> AFFINITY = AttachmentRegistry.create(JaymosPowers.id("affinity"),
		builder -> builder.persistent(Identifier.CODEC).copyOnDeath().syncWith(Identifier.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	/** "Let fate decide": a random power awakens. */
	public static final Identifier FATE = JaymosPowers.id("fate");
	/** The choice screen is offered at all (switched off for now; /power affinity still sets an affinity). */
	public static final boolean ENABLED = false;
	/** The chance an awakening gives the affinity's power (the rest of the time fate picks any power). */
	public static final float CHANCE = 0.95F;
	/** How often (ticks) a powerless survival player without an affinity is offered the choice. */
	private static final int PROMPT_EVERY = 100;

	/** Players offered the choice this session (so the screen isn't pushed at them again and again). */
	private static final Set<UUID> PROMPTED = new HashSet<>();

	private Affinity() {
	}

	public static @Nullable Identifier of(Player player) {
		return player.getAttached(AFFINITY);
	}

	public static boolean chosen(Player player) {
		return of(player) != null;
	}

	/** The power the player's affinity leans to, or null (none chosen, fate, or an unknown id). */
	public static @Nullable Power power(Player player) {
		Identifier id = of(player);
		return id == null || id.equals(FATE) ? null : Powers.get(id);
	}

	/** Every so often: a powerless survival player who hasn't chosen yet is shown the choice (once per session). */
	static void tick(ServerPlayer player) {
		if (!ENABLED || player.tickCount % PROMPT_EVERY != 0 || chosen(player) || player.isCreative() || player.isSpectator()
			|| PROMPTED.contains(player.getUUID())) {
			return;
		}
		PROMPTED.add(player.getUUID());
		ServerPlayNetworking.send(player, new PowerPayloads.OpenAffinity());
	}

	static void forget(Player player) {
		PROMPTED.remove(player.getUUID());
	}

	/** From the affinity screen: a choice, made once (only a command changes it after). */
	static void choose(ServerPlayer player, String id) {
		if (!ENABLED || chosen(player) || Powers.of(player) != null) {
			return;
		}
		Identifier parsed = Identifier.tryParse(id);
		if (parsed == null) {
			return;
		}
		Power power = Powers.get(parsed);
		if (power == null && !parsed.equals(FATE)) {
			return;
		}
		player.setAttached(AFFINITY, parsed);
		Component message = power == null
			? Component.translatable("message.jaymospowers.affinity_fate").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC)
			: Component.translatable("message.jaymospowers.affinity", power.name().copy()
				.withStyle(style -> style.withColor(TextColor.fromRgb(power.color())))).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
		player.sendSystemMessage(message);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** For commands: sets (or with null, clears) the affinity directly. */
	public static void set(ServerPlayer player, @Nullable Identifier id) {
		if (id == null) {
			player.removeAttached(AFFINITY);
			PROMPTED.remove(player.getUUID());
		} else {
			player.setAttached(AFFINITY, id);
		}
	}
}
