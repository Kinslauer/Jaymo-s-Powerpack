package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.332) Garlic and silver. The pack had no garlic, so there's a plain {@code garlic} item (it turns up now and then
 * when grass is cut: {@link #GRASS_DROP}). Using garlic, or anything whose id has "silver" in it, on a vampire (right
 * click) makes it flinch: a short shove back, a hiss, a line on the action bar. A vampire player can hold out (a quick
 * struggle, rolled for them): {@link #HOLD_OUT} at no surrender, nothing at a whole surrender, so the more they've given
 * in, the likelier the flinch. A dhampir doesn't flinch; vampires among the folk always do. Garlic is used up by a
 * flinch; silver isn't.
 */
public final class Garlic {
	/** The chance cutting grass drops a head of garlic. */
	static final float GRASS_DROP = 0.03F;
	/** The chance a vampire player holds out at no surrender (it falls to nothing at a whole surrender). */
	static final float HOLD_OUT = 0.5F;
	/** The shove (blocks a tick) and the lift. */
	static final double SHOVE = 0.8, LIFT = 0.3;
	/** One try on the same vampire this often (ticks). */
	static final long TRY_EVERY = 20L;

	public static Item GARLIC;

	/** No try by this player before this game time (not kept). */
	private static final Map<UUID, Long> TRIED = new HashMap<>();

	private Garlic() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("garlic"));
		GARLIC = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(GARLIC));
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (level instanceof ServerLevel server && (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)) && !player.isCreative()
				&& server.getRandom().nextFloat() < GRASS_DROP) {
				Block.popResource(server, pos, new ItemStack(GARLIC));
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer user) || !(entity instanceof LivingEntity target)
				|| !Vampires.isVampire(target) || target == user || user.isSpectator()) {
				return InteractionResult.PASS;
			}
			ItemStack held = user.getItemInHand(hand);
			if (!bane(held)) {
				return InteractionResult.PASS;
			}
			try_(server, user, target, held, hand);
			return InteractionResult.SUCCESS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TRIED.clear());
	}

	/** Whether {@code stack} is garlic or silver. */
	static boolean bane(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.is(GARLIC) || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("silver");
	}

	private static void try_(ServerLevel level, ServerPlayer user, LivingEntity target, ItemStack held, InteractionHand hand) {
		long now = level.getGameTime();
		if (now < TRIED.getOrDefault(user.getUUID(), 0L)) {
			return;
		}
		TRIED.put(user.getUUID(), now + TRY_EVERY);
		boolean holds = false;
		if (target instanceof ServerPlayer vampire) {
			// a half-blood doesn't flinch; the rest hold out by how much of themselves they have left
			holds = VampirePlayers.dhampir(vampire) || vampire.getRandom().nextFloat() < HOLD_OUT * (1.0F - BloodFever.surrender(vampire) / 100.0F);
		}
		if (holds) {
			user.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.flinch.held_out").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			if (target instanceof ServerPlayer vampire) {
				vampire.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.flinch.held_out_self").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			}
			return;
		}
		Vec3 away = target.position().subtract(user.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? user.getLookAngle().multiply(1.0, 0.0, 1.0) : away;
		away = away.normalize().scale(SHOVE);
		target.setDeltaMovement(away.x, LIFT, away.z);
		target.syncVelocity = true;
		level.playSound(null, target.blockPosition(), SoundEvents.CAT_HISS_BABY.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY(0.6), target.getZ(), 10, 0.25, 0.4, 0.25, 0.02);
		user.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.flinch.user").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		if (target instanceof ServerPlayer vampire) {
			vampire.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.flinch.self").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		}
		if (held.is(GARLIC) && !user.isCreative()) {
			held.shrink(1);
		}
	}
}
