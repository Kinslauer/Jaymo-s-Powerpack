package com.jaymoskingdoms.nether;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * (Kingdoms 0.7.147) The Nether faction's trophies. A Hornkin's horn (warriors, brutes and warlords drop them): a ruler
 * pays a bounty in emeralds for each ({@code Bounties}). A warlord's war horn, from a fallen Hornkin Warlord or Piglin
 * Warboss: blown, it rallies you and the players near you.
 */
public final class NetherTrophies {
	/** What a ruler pays for each Hornkin horn. */
	public static final int HORN_BOUNTY = 2;
	/** The war horn: who hears it, what it gives and for how long, and how long until it can be blown again. */
	static final double WAR_HORN_RANGE = 16.0;
	static final int WAR_HORN_TICKS = 20 * 30, WAR_HORN_COOLDOWN = 20 * 120;

	public static Item HORNKIN_HORN, WAR_HORN;

	private NetherTrophies() {
	}

	static void register() {
		HORNKIN_HORN = item("hornkin_horn", Item::new, new Item.Properties().stacksTo(64));
		WAR_HORN = item("war_horn", WarHorn::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(HORNKIN_HORN));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(WAR_HORN));
	}

	private static Item item(String name, java.util.function.Function<Item.Properties, Item> make, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
	}

	/** A warlord's war horn: blown, you and the players near you fight harder and faster a while. */
	static final class WarHorn extends Item {
		WarHorn(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN, SoundSource.PLAYERS, 4.0F, 0.9F);
			server.sendParticles(ParticleTypes.NOTE, player.getX(), player.getEyeY() + 0.5, player.getZ(), 6, 0.6, 0.3, 0.6, 1.0);
			for (Player ally : server.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(WAR_HORN_RANGE), p -> p.isAlive() && !p.isSpectator())) {
				ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, WAR_HORN_TICKS, 0), player);
				ally.addEffect(new MobEffectInstance(MobEffects.SPEED, WAR_HORN_TICKS, 0), player);
				if (ally != player) {
					ally.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.war_horn", player.getDisplayName()));
				}
			}
			player.getCooldowns().addCooldown(stack, WAR_HORN_COOLDOWN);
			return InteractionResult.SUCCESS;
		}
	}
}
