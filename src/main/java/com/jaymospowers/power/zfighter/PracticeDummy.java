package com.jaymospowers.power.zfighter;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * (0.18.71) The Sparring Dummy for anyone who isn't a Z Fighter: it stands a still practice dummy (a mannequin) where it's
 * used, for Weapon Forms practice and setting the belt. Blows land on it but never hurt it; sneak and strike it to take
 * it back up. A Z Fighter's use is still the sparring partner ({@link ZFighterSparring}).
 */
final class PracticeDummy {
	static final String TAG = "jaymospowers_practice_dummy";

	private PracticeDummy() {
	}

	static void init() {
		// struck, it shrugs it off: a thud and a puff of straw
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof Mannequin) || !entity.entityTags().contains(TAG)) {
				return true;
			}
			if (entity.level() instanceof ServerLevel level && source.getEntity() instanceof Player) {
				level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1.2, entity.getZ(), 4, 0.2, 0.3, 0.2, 0.05);
				level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_STAND_HIT, SoundSource.PLAYERS, 0.8F, 1.0F);
			}
			return false;
		});
		// sneak and strike it: it's picked back up
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof Mannequin) || !entity.entityTags().contains(TAG) || !player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server && entity.isAlive()) {
				entity.discard();
				server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_STAND_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
				if (!sp.isCreative()) {
					sp.getInventory().placeItemBackInInventory(new ItemStack(ZFighterItems.SPARRING_DUMMY), Prediction.SERVER_ONLY);
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** Stands a practice dummy on the face {@code context} clicked, facing the one who set it. */
	static InteractionResult place(UseOnContext context, ServerPlayer player, ServerLevel level) {
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.above()).canBeReplaced()) {
			return InteractionResult.FAIL;
		}
		Mannequin dummy = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (dummy == null) {
			return InteractionResult.FAIL;
		}
		dummy.addTag(TAG);
		dummy.setCustomName(Component.translatable("entity.jaymospowers.practice_dummy"));
		dummy.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
		if (!level.addFreshEntity(dummy)) {
			return InteractionResult.FAIL;
		}
		level.playSound(null, dummy.getX(), dummy.getY(), dummy.getZ(), SoundEvents.ARMOR_STAND_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
		context.getItemInHand().consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
