package com.jaymospowers.mortal;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The axe on a chain: an iron axe in the hand, and on right click it is thrown out to the end of its chain
 * ({@link #REACH} blocks, stopped by walls) and hauled straight back. Whoever it meets first takes {@link #SHARE} of
 * the axe's blow and is dragged a step toward you; sneaking, the haul is the point and they are pulled all the way in
 * ({@link #HAUL}). {@link #WAIT} ticks between throws; each throw that bites wears the axe.
 * Forged at the Tool Station from a Chain Axe Head, a Tool Handle and a length of chain ({@code WeaponParts}).
 */
public final class ChainAxeItem extends Item {
	static final double REACH = 9.0, TUG = 0.35, HAUL = 1.1;
	static final float SHARE = 0.8F;
	static final int WAIT = 30;

	public ChainAxeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level world, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(world instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		HitResult wall = level.clip(new ClipContext(eye, eye.add(look.scale(REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = wall.getType() == HitResult.Type.MISS ? REACH : wall.getLocation().distanceTo(eye);
		LivingEntity target = MortalKit.aimed(player, reach);
		Vec3 end = target != null ? target.getBoundingBox().getCenter() : eye.add(look.scale(reach));
		ItemParticleOption links = new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.IRON_CHAIN)));
		Vec3 from = eye.add(0.0, -0.3, 0.0);
		int steps = Math.max(2, (int) (end.distanceTo(from) * 3.0));
		for (int i = 1; i <= steps; i++) {
			Vec3 at = from.lerp(end, i / (double) steps);
			level.sendParticles(links, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2F, 0.8F);
		if (target != null) {
			float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * SHARE;
			if (target.hurtServer(level, player.damageSources().playerAttack(player), damage)) {
				Vec3 back = player.position().subtract(target.position());
				double far = Math.max(0.5, back.length());
				double pull = player.isShiftKeyDown() ? Math.min(HAUL, 0.25 + far * 0.12) : TUG;
				if (target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0) {
					target.setDeltaMovement(back.scale(pull / far).add(0.0, 0.22, 0.0));
					target.needsSync = true;
					target.syncVelocity = true;
				}
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.8F);
				stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
			}
		}
		player.getCooldowns().addCooldown(stack, WAIT);
		player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		return InteractionResult.SUCCESS_SERVER;
	}
}
