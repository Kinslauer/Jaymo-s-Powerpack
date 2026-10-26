package com.jaymospowers.alchemy.codex;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * The Scrying Lens: hold use on a creature, a block or the item in your other hand for a second to read what it is
 * made of ({@link Lens}). It must stay on the same thing the whole time, or the focus slips.
 */
public class LensItem extends Item {
	/** How long (ticks) the lens must be held on a thing. */
	public static final int USE_TICKS = 20;
	/** Cooldown (ticks) after a scan, so a flick of the mouse can't chain them. */
	static final int COOLDOWN = 10;
	/** Sparkle on the thing every this many ticks of holding. */
	static final int SPARKLE_EVERY = 3;

	/** The thing each player (server side) started looking at, to notice the aim wandering. */
	private static final Map<UUID, String> FOCUS = new ConcurrentHashMap<>();

	public LensItem(Properties properties) {
		super(properties);
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.SPYGLASS;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return USE_TICKS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		Lens.Thing thing = Lens.aim(player, hand);
		if (thing == null) {
			if (player instanceof ServerPlayer server) {
				server.sendOverlayMessage(Component.translatable("message.jaymospowers.lens.nothing"));
			}
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer server) {
			FOCUS.put(server.getUUID(), thing.key());
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.4F, 1.6F);
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		Lens.Thing thing = Lens.aim(player, player.getUsedItemHand());
		if (thing == null || !thing.key().equals(FOCUS.get(player.getUUID()))) {
			FOCUS.remove(player.getUUID());
			player.stopUsingItem();
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.lens.lost"));
			return;
		}
		if ((USE_TICKS - remainingUseTicks) % SPARKLE_EVERY == 0) {
			serverLevel.sendParticles(ParticleTypes.ENCHANT, thing.at().x, thing.at().y, thing.at().z, 3, 0.3, 0.3, 0.3, 0.3);
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		if (user instanceof ServerPlayer player) {
			String focus = FOCUS.remove(player.getUUID());
			Lens.Thing thing = Lens.aim(player, player.getUsedItemHand());
			if (thing != null && thing.key().equals(focus)) {
				Lens.scan(player, thing);
			}
			player.getCooldowns().addCooldown(stack, COOLDOWN);
		}
		return stack;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
		FOCUS.remove(user.getUUID());
		return false;
	}
}
