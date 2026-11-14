package com.jaymoskingdoms.equestrian;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
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
 */
public final class HorseKit {
	/** Health a medkit gives back at once, and the regeneration after it (ticks, level). */
	public static final float MEDKIT_HEAL = 8.0F;
	static final int MEDKIT_REGEN_TICKS = 100, MEDKIT_REGEN_LEVEL = 1;
	public static final int MEDKIT_USES = 4;
	/** Fastest a harnessed horse falls (blocks per tick; a free fall reaches about 3.9). */
	public static final double GLIDE_SINK = 0.18;

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
