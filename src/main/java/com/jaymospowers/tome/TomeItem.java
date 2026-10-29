package com.jaymospowers.tome;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The spell tome, a new kind of weapon (Andy: "casting quick bolts with left click that can be charged, switching
 * elements and storing a spell on right click"). The left click is in {@link Tome} (the client sends the press and the
 * release, as vanilla sends nothing for a click on air); this class is the right click:
 * <ul>
 * <li>Sneak + right click: the next element (fire, frost, lightning, arcane).</li>
 * <li>Right click with nothing stored: channel for {@link Tome#CHANNEL_TICKS} ticks and the tome keeps a spell of the
 * current element, at full charge's power. With one stored: it's cast at once, whatever element the tome is on now.</li>
 * </ul>
 */
public class TomeItem extends Item {
	public TomeItem(Properties properties) {
		super(properties);
	}

	public static TomeElement element(ItemStack stack) {
		return TomeElement.byId(stack.get(Tome.ELEMENT));
	}

	/** The element of the spell the tome holds, or null. */
	public static @Nullable TomeElement stored(ItemStack stack) {
		String id = stack.get(Tome.STORED);
		return id == null ? null : TomeElement.byId(id);
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.translatable("item.jaymospowers.spell_tome.named", super.getName(stack),
			Component.translatable(element(stack).langKey()).withColor(element(stack).color));
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		TomeElement stored = stored(stack);
		if (stored != null) {
			builder.accept(Component.translatable("item.jaymospowers.spell_tome.stored", Component.translatable(stored.langKey())).withColor(stored.color));
		}
		builder.accept(Component.translatable("item.jaymospowers.spell_tome.hint.bolt").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("item.jaymospowers.spell_tome.hint.element").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("item.jaymospowers.spell_tome.hint.store").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isShiftKeyDown()) {
			if (player.getCooldowns().isOnCooldown(stack)) {
				return InteractionResult.PASS;
			}
			TomeElement next = element(stack).next();
			stack.set(Tome.ELEMENT, next.id);
			player.getCooldowns().addCooldown(stack, Tome.CYCLE_COOLDOWN);
			if (level instanceof ServerLevel server) {
				this.cycleCue(server, player, next);
			}
			return InteractionResult.SUCCESS;
		}
		TomeElement stored = stored(stack);
		if (stored != null) {
			if (level instanceof ServerLevel server) {
				stack.remove(Tome.STORED);
				stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
				TomeBolt.cast(server, player, stored, 1.0F);
				stack.hurtAndBreak(Tome.RELEASE_WEAR, player, hand);
				player.swing(hand, stack.getAttackAnimation(), true);
			}
			return InteractionResult.SUCCESS;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	/** A page turns, a chime, and a ring of the new element's motes round the tome. */
	private void cycleCue(ServerLevel level, Player player, TomeElement element) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F,
			0.8F + 0.25F * element.ordinal());
		Vec3 hand = Tome.handPoint(player);
		level.sendParticles(element.trail, hand.x, hand.y, hand.z, 12, 0.2, 0.2, 0.2, 0.03);
		level.sendParticles(ParticleTypes.ENCHANT, hand.x, hand.y, hand.z, 8, 0.2, 0.2, 0.2, 0.4);
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return Tome.CHANNEL_TICKS;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
		if (level instanceof ServerLevel server && user instanceof Player player) {
			Tome.channelTick(server, player, element(stack), 1.0F - remaining / (float) Tome.CHANNEL_TICKS);
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		if (level instanceof ServerLevel server && user instanceof Player player) {
			TomeElement element = element(stack);
			stack.set(Tome.STORED, element.id);
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			stack.hurtAndBreak(Tome.STORE_WEAR, player, player.getUsedItemHand());
			Vec3 hand = Tome.handPoint(player);
			server.sendParticles(element.trail, hand.x, hand.y, hand.z, 20, 0.25, 0.25, 0.25, 0.05);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.8F, 1.4F);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
			player.sendOverlayMessage(Component.empty());
		}
		return stack;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
		if (level instanceof ServerLevel && user instanceof Player player) {
			player.sendOverlayMessage(Component.empty());
		}
		return false;
	}
}
