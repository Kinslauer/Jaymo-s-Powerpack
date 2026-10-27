package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * (0.7.261, Andy's NOEA picks) Rubber-folk: pink and stretchy. Their reach is longer ({@link #REACH}: the stretch, as a
 * longer arm and not a drawn one), a lost limb grows back on its own ({@link #REGROW_EVERY}, as a Verdant's does, through
 * Jaymo's Powers' dismemberment), and they can take in the weak: look at a small, low-health foe, or any not-too-big foe
 * that lies downed, and hold the heritage key close by ({@link HeritageHold}) to swallow it for a heal ({@link #absorb}).
 * They have a sweet tooth: cookies, pie, honey, berries, melon, cake, and Jaymo's Kitchen's tarts, pies, cobbler and
 * brownies mend them ({@link #sweet}). Flaw: rubber burns, and pops: fire burns them longer, and arrows hurt more
 * ({@link Heritage#dealt}).
 */
public final class RubberFolk {
	/** The stretch: extra reach to hit and to use things (blocks). */
	static final double REACH = 1.0;
	/** Fire burns this much longer (a share), and arrows and spines hurt this many times as much (the flaw). */
	static final double BURN = 0.6;
	static final float POPS = 1.25F;
	/** A lost limb grows back this often (ticks), one at a time. */
	static final int REGROW_EVERY = 400;
	/**
	 * Absorbing: how far the foe may be (blocks), how long the hold takes (ticks), the share of its health they take back, at
	 * least and at most, and food. A weak foe is small (at most this wide and tall) with this little health at most; a downed
	 * one may be bigger.
	 */
	static final double ABSORB_REACH = 2.5;
	static final int ABSORB_TICKS = 40, ABSORB_FOOD = 2;
	static final float ABSORB_HEAL_SHARE = 0.4F, ABSORB_HEAL_MIN = 2.0F, ABSORB_HEAL_MAX = 8.0F;
	static final float WEAK_HEALTH = 12.0F, WEAK_WIDTH = 1.0F, WEAK_HEIGHT = 1.4F;
	static final float DOWNED_HEALTH = 40.0F, DOWNED_WIDTH = 1.6F, DOWNED_HEIGHT = 2.4F;
	/** A sweet thing mends this much; a baked one from Kitchen, and a slice of cake. */
	static final float SWEET_HEAL = 4.0F, KITCHEN_HEAL = 6.0F, CAKE_HEAL = 4.0F;
	/** Pink motes. */
	private static final DustParticleOptions PINK = new DustParticleOptions(0xF08CB8, 0.8F);

	private static final Map<UUID, HeritageHold.Channel> CHANNELS = new HashMap<>();
	/** The sweet each is eating now, and the ticks it had left when last seen. */
	private static final Map<UUID, ItemStack> EATING = new HashMap<>();
	private static final Map<UUID, Integer> LEFT = new HashMap<>();

	private RubberFolk() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RubberFolk::tick);
		// a slice of cake, eaten (it can only be eaten hungry, as in vanilla)
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && Heritage.is(sp, Heritage.Kind.RUBBERFOLK)
				&& sp.canEat(false) && !sp.isShiftKeyDown()) {
				var state = level.getBlockState(hit.getBlockPos());
				if (state.is(Blocks.CAKE) || state.is(BlockTags.CANDLE_CAKES)) {
					relish(sp, CAKE_HEAL);
				}
			}
			return InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			CHANNELS.remove(handler.player.getUUID());
			EATING.remove(handler.player.getUUID());
			LEFT.remove(handler.player.getUUID());
		});
	}

	/** The stretch and the burn, on {@code player} as they stand (taken off anyone who isn't Rubber-folk). */
	static void attributes(ServerPlayer player, Heritage.Kind kind) {
		boolean rubber = kind == Heritage.Kind.RUBBERFOLK;
		Skills.modifier(player, Attributes.ENTITY_INTERACTION_RANGE, "heritage_rubber_reach", rubber ? REACH : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.BURNING_TIME, "heritage_rubber_burn", rubber ? BURN : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** A sweet thing: a cookie, a pie, honey, berries, a slice of melon, a cake, or one of Kitchen's. */
	static boolean sweet(ItemStack stack) {
		return stack.is(Items.COOKIE) || stack.is(Items.PUMPKIN_PIE) || stack.is(Items.HONEY_BOTTLE) || stack.is(Items.SWEET_BERRIES)
			|| stack.is(Items.MELON_SLICE) || stack.is(Items.CAKE) || KitchenCompat.sweet(stack);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (!Heritage.is(player, Heritage.Kind.RUBBERFOLK)) {
				if (!CHANNELS.isEmpty()) {
					CHANNELS.remove(id);
				}
				if (!EATING.isEmpty()) {
					EATING.remove(id);
					LEFT.remove(id);
				}
				continue;
			}
			// a lost limb grows back, one at a time
			if (server.getTickCount() % REGROW_EVERY == 0 && player.isAlive() && PowersCompat.regrow(player, false) && player.level() instanceof ServerLevel level) {
				level.sendParticles(PINK, player.getX(), player.getY(0.6), player.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rubber.regrown"));
			}
			// a sweet finished (seen eating it last tick, with next to nothing left)
			if (player.isUsingItem() && sweet(player.getUseItem())) {
				EATING.put(id, player.getUseItem().copy());
				LEFT.put(id, player.getUseItemRemainingTicks());
			} else if (EATING.containsKey(id)) {
				ItemStack was = EATING.remove(id);
				Integer left = LEFT.remove(id);
				if (left != null && left <= 2) {
					relish(player, was.is(Items.CAKE) ? CAKE_HEAL : KitchenCompat.sweet(was) ? KITCHEN_HEAL : SWEET_HEAL);
				}
			}
			LivingEntity foe = HeritageHold.advance(CHANNELS, player, e -> HeritageHold.foe(player, e) && weak(e), ABSORB_REACH, ABSORB_TICKS, PINK,
				"message.jaymoskingdoms.rubber.absorbing");
			if (foe != null && player.level() instanceof ServerLevel level) {
				absorb(level, player, foe);
			}
		}
	}

	/** Small and low in health, or downed and not too big: weak enough to swallow. */
	static boolean weak(LivingEntity e) {
		if (Downed.isDowned(e)) {
			return e.getMaxHealth() <= DOWNED_HEALTH && e.getBbWidth() <= DOWNED_WIDTH && e.getBbHeight() <= DOWNED_HEIGHT;
		}
		return e.getMaxHealth() <= WEAK_HEALTH && e.getBbWidth() <= WEAK_WIDTH && e.getBbHeight() <= WEAK_HEIGHT;
	}

	/** {@code foe} is swallowed whole: gone, and a share of its health theirs. */
	private static void absorb(ServerLevel level, ServerPlayer player, LivingEntity foe) {
		float heal = Mth.clamp(foe.getMaxHealth() * ABSORB_HEAL_SHARE, ABSORB_HEAL_MIN, ABSORB_HEAL_MAX);
		level.sendParticles(PINK, foe.getX(), foe.getY(0.5), foe.getZ(), 20, 0.3, 0.4, 0.3, 0.03);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.8F);
		foe.discard();
		player.heal(heal);
		player.getFoodData().setFoodLevel(Math.min(20, player.getFoodData().getFoodLevel() + ABSORB_FOOD));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rubber.absorbed"));
	}

	/** A sweet mends {@code player} by {@code amount}, with a little pink pop. */
	private static void relish(ServerPlayer player, float amount) {
		if (player.getHealth() < player.getMaxHealth() && !Downed.isDowned(player)) {
			player.heal(amount);
		}
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY(1.1), player.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
		}
	}

	/** (the flaw) arrows and spines pop them: the factor on harm from {@code source}. */
	static float taken(net.minecraft.world.damagesource.DamageSource source) {
		return source.is(DamageTypeTags.IS_PROJECTILE) || source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS)
			|| source.is(net.minecraft.world.damagesource.DamageTypes.STALAGMITE) ? POPS : 1.0F;
	}
}
