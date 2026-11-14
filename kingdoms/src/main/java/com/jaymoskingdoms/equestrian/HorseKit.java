package com.jaymoskingdoms.equestrian;

import com.jaymoskingdoms.CourtWizards;
import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.ZFighterFolk;
import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The last of Icy's Better Horses' kit (its code licence allows it; the art is ours): the horse medkit and the glide
 * harness.
 * <ul>
 * <li><b>Medkit:</b> used on a hurt horse (any horse, anyone's), it heals {@link #MEDKIT_HEAL} at once and more over
 * the next few seconds, and draws out poison. {@link #MEDKIT_USES} uses.</li>
 * <li><b>Glide harness:</b> a saddle with membrane wings folded along the flanks. Ridden off a drop, the horse
 * spreads them and sinks slowly ({@code GlideHarnessClient} caps the fall, since the rider's client moves the
 * horse), and neither horse nor rider takes the fall.</li>
 * </ul>
 * Andy: the glide harness and Kitchen's ender saddlebags are had only from a court wizard ({@link #open}); the medkit
 * is crafted.
 */
public final class HorseKit {
	/** Health a medkit gives back at once, and the regeneration after it (ticks, level). */
	public static final float MEDKIT_HEAL = 8.0F;
	static final int MEDKIT_REGEN_TICKS = 100, MEDKIT_REGEN_LEVEL = 1;
	public static final int MEDKIT_USES = 4;
	/** Fastest a harnessed horse falls (blocks per tick; a free fall reaches about 3.9). */
	public static final double GLIDE_SINK = 0.18;
	/** Andy: the enchanted tack is had only from a court wizard ("Enchanted tack", {@link #ANSWER}), at these prices. */
	public static final String ANSWER = "jaymoskingdoms_enchanted_tack";
	static final long HARNESS_PRICE = 16, ENDER_BAGS_PRICE = 20;
	/** Jaymo's Kitchen's ender saddlebags (sold only when Kitchen is loaded). */
	static final Identifier ENDER_BAGS = Identifier.fromNamespaceAndPath("jaymoskitchen", "ender_saddlebag");

	private HorseKit() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (player.isSpectator() || !stack.is(Tackroom.HORSE_MEDKIT) || !(entity instanceof AbstractHorse horse) || !horse.isAlive()
				|| (horse.getHealth() >= horse.getMaxHealth() && !horse.hasEffect(MobEffects.POISON))) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				horse.heal(MEDKIT_HEAL);
				horse.removeEffect(MobEffects.POISON);
				horse.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MEDKIT_REGEN_TICKS, MEDKIT_REGEN_LEVEL));
				server.playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 1.0F, 1.2F);
				server.sendParticles(ParticleTypes.HEART, horse.getX(), horse.getY() + horse.getBbHeight(), horse.getZ(), 4, 0.4, 0.2, 0.4, 0.0);
				stack.hurtAndBreak(1, player, hand);
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(HorseKit::tick);
	}

	/** Whether "Enchanted tack" shows on {@code who}: a court wizard. */
	public static boolean canAsk(LivingEntity who) {
		return !who.level().isClientSide() && CourtWizards.is(who) && !Downed.isDowned(who);
	}

	/** The court wizard's enchanted tack: the glide harness, and (with Kitchen) ender saddlebags. */
	public static void open(ServerPlayer player, LivingEntity who) {
		if (!canAsk(who)) {
			return;
		}
		List<Item> wares = new ArrayList<>();
		List<Long> prices = new ArrayList<>();
		wares.add(Tackroom.GLIDE_HARNESS);
		prices.add(Money.worth(HARNESS_PRICE));
		BuiltInRegistries.ITEM.getOptional(ENDER_BAGS).ifPresent(bags -> {
			wares.add(bags);
			prices.add(Money.worth(ENDER_BAGS_PRICE));
		});
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		for (int i = 0; i < wares.size(); i++) {
			options.add(Component.translatable("talk.jaymoskingdoms.enchanted_tack.buy", wares.get(i).getName(wares.get(i).getDefaultInstance()),
				Money.text(player, prices.get(i))));
			enabled.add(true);
		}
		options.add(Component.translatable("talk.jaymoskingdoms.enchanted_tack.leave"));
		enabled.add(true);
		ZFighterFolk.ask(player, who, Component.translatable("talk.jaymoskingdoms.enchanted_tack.offer"), options, enabled, pick -> {
			if (pick < 0 || pick >= wares.size() || !canAsk(who)) {
				return;
			}
			if (!Money.charge(player, prices.get(pick))) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.enchanted_tack.poor").withStyle(ChatFormatting.RED));
				return;
			}
			player.getInventory().placeItemBackInInventory(new ItemStack(wares.get(pick)), net.minecraft.util.Prediction.SERVER_ONLY);
			if (player.level() instanceof ServerLevel level) {
				level.playSound(null, who.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 0.8F, 1.2F);
			}
		});
	}

	/** Whether this horse wears the glide harness. */
	public static boolean harnessed(@Nullable AbstractHorse horse) {
		return horse != null && horse.getItemBySlot(EquipmentSlot.SADDLE).is(Tackroom.GLIDE_HARNESS);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.getVehicle() instanceof AbstractHorse horse && harnessed(horse) && !horse.onGround()) {
				horse.resetFallDistance();
				player.resetFallDistance();
			}
		}
	}
}
