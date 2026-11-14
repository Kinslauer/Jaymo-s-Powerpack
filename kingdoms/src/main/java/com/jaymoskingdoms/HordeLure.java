package com.jaymoskingdoms;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The horde lure (Andy's pick from Undead Nights, 2026-11-14), told by smell and sound, never by text:
 * <ul>
 * <li><b>The grave scent</b> ({@link #SCENT}): now and then ({@link #SCENT_CHANCE}) a player who kills one of a horde
 * comes away with its smell on them for {@link #SCENT_TICKS}. While it lasts the hordes ({@link ZombieHordes}) find
 * them {@link #SCENT_FACTOR} times as often and wait a third as long between.</li>
 * <li><b>The grave lure</b> ({@link #LURE}): rotten flesh, a bone and coarse grave dirt bound up together (crafted),
 * for a group that wants the fight. Used at night, under rain, on an overcast horde day, underground or in the Nether,
 * it's torn open and a horde comes at once, and the user carries the grave scent. In the Sun it does nothing (a dull
 * thud) and isn't used up.</li>
 * </ul>
 */
public final class HordeLure {
	static final float SCENT_CHANCE = 0.04F, SCENT_FACTOR = 3.0F;
	static final int SCENT_TICKS = 6000;
	static final int SCENT_COLOUR = 0x5B6B3A;

	public static Holder<MobEffect> SCENT;
	public static Item LURE;

	/** The grave scent: harmless itself, it only draws the dead. */
	static final class Scent extends MobEffect {
		Scent() {
			super(MobEffectCategory.HARMFUL, SCENT_COLOUR);
		}
	}

	/** The grave lure. */
	static final class Lure extends Item {
		Lure(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			boolean dark = level.dimension() == Level.NETHER || ZombieHordes.underground(sp)
				|| level.dimension() == Level.OVERWORLD && ZombieHordes.surfaceWeather(server.getServer().overworld());
			if (!dark || ZombieHordes.send(sp, level.dimension() == Level.NETHER ? Moons.Moon.NONE : Moons.now(server.getServer())) <= 0) {
				level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
				return InteractionResult.FAIL;
			}
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.5F);
			sp.addEffect(new MobEffectInstance(SCENT, SCENT_TICKS));
			ItemStack stack = sp.getItemInHand(hand);
			stack.consume(1, sp);
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
			out.accept(Component.translatable("item.jaymoskingdoms.grave_lure.tooltip").withStyle(ChatFormatting.GRAY));
		}
	}

	private HordeLure() {
	}

	static void register() {
		SCENT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, JaymosKingdoms.id("grave_scent"), new Scent());
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("grave_lure"));
		LURE = Registry.register(BuiltInRegistries.ITEM, key, new Lure(new Item.Properties().setId(key).stacksTo(16)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.MAP, LURE));
	}

	/** How much more often the hordes find {@code player}: {@link #SCENT_FACTOR} with the grave scent on them, else 1. */
	static float scentFactor(ServerPlayer player) {
		return SCENT != null && player.hasEffect(SCENT) ? SCENT_FACTOR : 1.0F;
	}

	/** One of a horde slain by a player: now and then its smell comes away on them. */
	static void slain(LivingEntity entity, DamageSource source) {
		if (ZombieHordes.inHorde(entity) && source.getEntity() instanceof ServerPlayer player && SCENT != null
			&& player.getRandom().nextFloat() < SCENT_CHANCE) {
			player.addEffect(new MobEffectInstance(SCENT, SCENT_TICKS));
		}
	}
}
