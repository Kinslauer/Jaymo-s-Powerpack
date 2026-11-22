package com.jaymospowers.gear;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.TreeLocks;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A deck of throwing cards: right-click throws three at once in a narrow fan, each a {@link ThrownGear} that does one random
 * thing where it hits ({@link #hit}, weighted): a plain cut, a burn, poison, or a small burst that breaks no blocks. The deck
 * wears a card's worth of use per card thrown.
 */
public final class ThrowingCards {
	// ---- tuning ----
	/** Cards in a deck, thrown at a time, the fan's spread between neighbours (degrees), the speed (blocks/tick) and the wait between throws (ticks). */
	public static final int DECK = 36, VOLLEY = 3, COOLDOWN = 14;
	public static final float SPREAD = 5.0F, SPEED = 1.7F;
	/** The odds of each thing a card does: cut, burn, poison, burst. */
	static final int CUT = 40, BURN = 25, POISON = 25, BURST = 10;
	/** The harm of a cut, of a burn's or poison's sting, of a burst; a burn's flames (seconds), poison (ticks), a burst's reach (blocks). */
	static final float CUT_HARM = 3.0F, STING_HARM = 1.0F, BURST_HARM = 3.0F;
	static final int BURN_SECONDS = 4, POISON_TICKS = 100;
	static final double BURST_RADIUS = 1.8;

	public static Item DECK_ITEM, CARD;

	private ThrowingCards() {
	}

	static void init() {
		ResourceKey<Item> deck = ResourceKey.create(Registries.ITEM, JaymosPowers.id("throwing_cards"));
		DECK_ITEM = Registry.register(BuiltInRegistries.ITEM, deck, new DeckItem(new Item.Properties().setId(deck).stacksTo(1).durability(DECK)));
		// the card in flight: only ever thrown from the deck, never held
		ResourceKey<Item> card = ResourceKey.create(Registries.ITEM, JaymosPowers.id("throwing_card"));
		CARD = Registry.register(BuiltInRegistries.ITEM, card, new Item(new Item.Properties().setId(card)));
	}

	/** The deck. */
	public static final class DeckItem extends Item {
		DeckItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack deck = player.getItemInHand(hand);
			if (!TreeLocks.check(player, TreeLocks.THROWER)) {
				return InteractionResult.FAIL;
			}
			if (level instanceof ServerLevel server) {
				int cards = Math.min(VOLLEY, deck.getMaxDamage() - deck.getDamageValue());
				for (int i = 0; i < cards; i++) {
					float turn = (i - (cards - 1) * 0.5F) * SPREAD;
					ThrownGear card = new ThrownGear(server, player, new ItemStack(CARD));
					Vec3 eye = player.getEyePosition();
					card.setPos(eye.x, eye.y - 0.2, eye.z);
					Vec3 aim = Vec3.directionFromRotation(player.getXRot(), player.getYRot() + turn);
					card.shoot(aim.x, aim.y, aim.z, SPEED, 0.5F);
					server.addFreshEntity(card);
				}
				deck.hurtAndBreak(cards, player, hand);
				server.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.6F);
			}
			player.getCooldowns().addCooldown(deck, COOLDOWN);
			return InteractionResult.SUCCESS;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymospowers.throwing_cards").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("tooltip.jaymospowers.throwing_cards.left", stack.getMaxDamage() - stack.getDamageValue())
				.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** A card reached {@code hit}: it does its one thing. */
	public static void hit(ServerLevel level, @Nullable Entity owner, HitResult hit) {
		Vec3 at = hit.getLocation();
		int roll = level.getRandom().nextInt(CUT + BURN + POISON + BURST);
		LivingEntity victim = hit instanceof EntityHitResult struck && struck.getEntity() instanceof LivingEntity living ? living : null;
		if (roll < BURST) {
			burst(level, at, owner);
		} else if (victim == null) {
			// a card that found no one only flutters down
			return;
		} else if (roll < BURST + CUT) {
			victim.hurtServer(level, level.damageSources().generic(), CUT_HARM);
			Bleeding.open(victim, BodyPart.BODY, false);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.8F);
		} else if (roll < BURST + CUT + BURN) {
			victim.hurtServer(level, level.damageSources().generic(), STING_HARM);
			victim.igniteForSeconds(BURN_SECONDS);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
		} else {
			victim.hurtServer(level, level.damageSources().generic(), STING_HARM);
			victim.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0), owner);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SPIDER_HURT, SoundSource.PLAYERS, 0.6F, 1.8F);
		}
	}

	/** A small burst: it hurts what is near and breaks nothing. */
	private static void burst(ServerLevel level, Vec3 at, @Nullable Entity owner) {
		MagicFx.blast(level, at, 1.4F, 0xFFB040);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8F, 1.5F);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BURST_RADIUS),
			e -> e.isAlive() && !e.isSpectator() && e != owner)) {
			if (near.position().distanceTo(at) <= BURST_RADIUS + 0.4) {
				near.hurtServer(level, level.damageSources().magic(), BURST_HARM);
			}
		}
	}
}
