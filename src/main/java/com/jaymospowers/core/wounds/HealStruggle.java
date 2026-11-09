package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.HoldProgress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "if you are too damaged to get up, you can struggle with a healing item in hand to get yourself back up". A downed
 * player who is awake but too hurt to rise ({@link Downed#tooHurt}), with a healing item in hand (the
 * {@link #SELF_REVIVE} tag: golden apples, our bandages, healing brews and the healer's satchel; and any potion of
 * healing, drunk or splash), holds use for {@link #STRUGGLE_TICKS}: the bar fills, the item is used up and they're up with
 * at least {@link #RISE_HEALTH}. Let go and the bar keeps where it got to; a blow breaks it off and it starts again. Nothing
 * tells you of it (Andy: "it shouldnt tell you this at all"); the downed keys let use through for it ({@code DownedKeysMixin}).
 */
public final class HealStruggle {
	/** How long the struggle up takes (ticks); how long use may go unpressed before it's let go (vanilla repeats it every 4). */
	public static final int STRUGGLE_TICKS = 80, GRACE = 8;
	/** The least health they get up with. */
	static final float RISE_HEALTH = 4.0F;
	static final int BAR = 12;

	/** What a downed player can struggle up with. More can be added by datapack. */
	public static final TagKey<Item> SELF_REVIVE = TagKey.create(Registries.ITEM, JaymosPowers.id("self_revive"));

	private static final class Struggle {
		final InteractionHand hand;
		long held;
		int ticks;

		Struggle(InteractionHand hand, long held) {
			this.hand = hand;
			this.held = held;
		}
	}

	private static final Map<UUID, Struggle> STRUGGLES = new HashMap<>();

	private HealStruggle() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HealStruggle::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STRUGGLES.clear());
	}

	/** This time down: let go, the bar stays for the next hold ({@link HoldProgress}). */
	private static String job(Player player) {
		return "heal_struggle:" + Downed.since(player);
	}

	/** Whether {@code stack} is something to struggle up with. Both sides. */
	public static boolean healing(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(SELF_REVIVE)) {
			return true;
		}
		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
		return potion != null && (potion.is(Potions.HEALING) || potion.is(Potions.STRONG_HEALING));
	}

	/** Whether {@code player} is down, awake and too hurt to rise: the struggle is theirs to make. Both sides. */
	public static boolean stuck(Player player) {
		return Downed.isDowned(player) && Downed.awake(player) && Downed.tooHurt(player);
	}

	/** Whether {@code player} could struggle up now, with what's in either hand. Both sides (the downed keys). */
	public static boolean canStruggle(Player player) {
		return stuck(player) && (healing(player.getMainHandItem()) || healing(player.getOffhandItem()));
	}

	/** A downed player's use (from {@link Downed}'s slack hands): a healing item, too hurt to rise, is the struggle. Else null. */
	static @Nullable InteractionResult use(Player player, InteractionHand hand) {
		if (!stuck(player) || !healing(player.getItemInHand(hand))) {
			return null;
		}
		if (player instanceof ServerPlayer server) {
			long now = server.level().getGameTime();
			Struggle s = STRUGGLES.get(server.getUUID());
			if (s == null || s.hand != hand) {
				s = new Struggle(hand, now);
				s.ticks = HoldProgress.get(server.getUUID(), job(server));
				STRUGGLES.put(server.getUUID(), s);
			} else {
				s.held = now;
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** {@code player} took a blow: the struggle is broken off, and starts again. */
	static void struck(ServerPlayer player) {
		if (STRUGGLES.remove(player.getUUID()) != null) {
			HoldProgress.forget(player.getUUID(), job(player));
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.heal_struggle_broken").withStyle(ChatFormatting.RED));
		}
	}

	private static void tick(MinecraftServer server) {
		if (STRUGGLES.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Struggle>> it = STRUGGLES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Struggle> entry = it.next();
			Struggle s = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			ItemStack stack = player.getItemInHand(s.hand);
			long now = level.getGameTime();
			if (!stuck(player) || !healing(stack) || now - s.held > GRACE) {
				it.remove();
				if (Downed.isDowned(player)) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.heal_struggle_stopped").withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			s.ticks++;
			HoldProgress.set(player.getUUID(), job(player), s.ticks);
			if (s.ticks % 10 == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.5F,
					0.7F + level.getRandom().nextFloat() * 0.2F);
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.getItem()), player.getX(), player.getY() + 0.3, player.getZ(), 3,
					0.2, 0.05, 0.2, 0.03);
			}
			if (s.ticks < STRUGGLE_TICKS) {
				if (s.ticks % 2 == 0) {
					int filled = Math.round(BAR * s.ticks / (float) STRUGGLE_TICKS);
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.heal_struggling", "|".repeat(filled) + ".".repeat(BAR - filled))
						.withStyle(ChatFormatting.YELLOW));
				}
				continue;
			}
			it.remove();
			HoldProgress.forget(player.getUUID(), job(player));
			ItemStack used = stack.copyWithCount(1);
			Downed.getUp(level, player);
			if (Downed.isDowned(player)) {
				// (held down: a Will's No Getting Up, or legs still growing back; the item is kept)
				continue;
			}
			stack.consume(1, player);
			player.setHealth(Math.max(player.getHealth(), Math.min(player.getMaxHealth(), RISE_HEALTH)));
			Wounds.reconcile(player);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4F, 0.6F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.heal_struggled", used.getHoverName()).withStyle(ChatFormatting.GREEN));
		}
	}
}
