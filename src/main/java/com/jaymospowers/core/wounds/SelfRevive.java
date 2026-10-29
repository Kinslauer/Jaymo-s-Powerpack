package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.49) Second Wind, the self revive on the Medicine page of Jaymo's Kingdoms' Survival tree. Andy (2026-10-21):
 * "they also gain a self revive through this tree, called second wind that allows them to eat while downed to rise, once
 * a day". Down (not passed out or knocked out), with food in hand: hold use to eat it for {@link #EAT_TICKS} (Steady
 * Hands quickens it), and you get up as a revive would get you up (Lifesaver's half health too). Then not again for
 * {@link #EVERY} ({@link #LAST}). The downed keys let use through for it ({@code DownedKeysMixin}), and
 * {@link Downed}'s slack hands let the food through.
 */
public final class SelfRevive {
	/** How long the eating takes (ticks); how long use may go unpressed before it's let go (vanilla repeats it every 4). */
	public static final int EAT_TICKS = 60, GRACE = 8;
	/** Once a day (ticks). */
	public static final long EVERY = 24000L;
	/** The perk's key in Kingdoms' tree. */
	static final String PERK = "self_revive";
	static final int BAR = 12;

	/** When Second Wind last got them up (game time). Saved, kept through death, synced to its player (their keys). */
	public static final AttachmentType<Long> LAST = AttachmentRegistry.create(JaymosPowers.id("self_revive_last"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath().syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.targetOnly()));

	/** A player eating to rise: when use last came, and how far along. */
	private static final class Eating {
		long held;
		int ticks, hits;
		final InteractionHand hand;

		Eating(InteractionHand hand, long held) {
			this.hand = hand;
			this.held = held;
		}
	}

	private static final Map<UUID, Eating> EATING = new HashMap<>();

	private SelfRevive() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SelfRevive::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> EATING.clear());
	}

	/** Whether {@code player} could rise by eating now: down, awake, knows Second Wind, and a day since the last time. Both sides. */
	public static boolean ready(Player player) {
		if (!Downed.isDowned(player) || Downed.passedOut(player) || player.hasAttached(Downed.KNOCKED) || MedicineSkill.rank(player, PERK) <= 0) {
			return false;
		}
		Long last = player.getAttached(LAST);
		long now = player.level().getGameTime();
		return last == null || now - last >= EVERY || now < last;
	}

	static boolean food(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.FOOD);
	}

	/** Whether {@code player} holds food they could rise on (either hand). Both sides. */
	public static boolean canEat(Player player) {
		return ready(player) && (food(player.getMainHandItem()) || food(player.getOffhandItem()));
	}

	/** A downed player's use (from {@link Downed}'s slack hands): food, and Second Wind ready, is the eating. Else null. */
	static @Nullable InteractionResult use(Player player, InteractionHand hand) {
		if (!ready(player) || !food(player.getItemInHand(hand))) {
			return null;
		}
		if (player instanceof ServerPlayer server) {
			long now = server.level().getGameTime();
			Eating e = EATING.get(server.getUUID());
			if (e == null) {
				EATING.put(server.getUUID(), new Eating(hand, now));
			} else {
				e.held = now;
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** (0.18.5) {@code player} took a hit: the {@code most}th breaks off their eating ({@link Downed}'s held actions). */
	static void struck(ServerPlayer player, int most) {
		Eating e = EATING.get(player.getUUID());
		if (e != null && ++e.hits >= most) {
			EATING.remove(player.getUUID());
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.self_revive_broken").withStyle(ChatFormatting.RED));
		}
	}

	private static void tick(MinecraftServer server) {
		if (EATING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Eating>> it = EATING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Eating> entry = it.next();
			Eating e = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			ItemStack stack = player.getItemInHand(e.hand);
			long now = level.getGameTime();
			if (!ready(player) || !food(stack) || now - e.held > GRACE) {
				it.remove();
				if (Downed.isDowned(player)) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.self_revive_stopped").withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			e.ticks++;
			if (e.ticks % 4 == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.5F,
					0.9F + level.getRandom().nextFloat() * 0.2F);
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.getItem()), player.getX(), player.getEyeY() - 0.1, player.getZ(), 3,
					0.1, 0.05, 0.1, 0.05);
			}
			int need = MedicineSkill.ticks(player, EAT_TICKS);
			if (e.ticks < need) {
				if (e.ticks % 2 == 0) {
					int filled = Math.round(BAR * e.ticks / (float) need);
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.self_reviving", "|".repeat(filled) + ".".repeat(BAR - filled))
						.withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			it.remove();
			stack.consume(1, player);
			player.setAttached(LAST, now);
			Downed.getUp(level, player);
			MedicineSkill.broughtRound(player, player);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.6F, 1.0F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.self_revived").withStyle(ChatFormatting.GREEN));
		}
	}
}
