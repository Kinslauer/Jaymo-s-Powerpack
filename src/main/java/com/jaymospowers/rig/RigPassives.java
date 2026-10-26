package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.KitchenCompat;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Each piece's passive, worn alone or in the set (no charge needed):
 * <ul>
 * <li>Arms: the punch boost (bare-fisted blows hit harder and throw further; Gravity's augment adds weight, Z Fighter's
 * knockback), and lifting heavy corpses: Sneak + right-click an iron golem's body (Jaymo's Kitchen; otherwise only
 * Gravity can) to carry it. (0.17.7) Also a big carcass (a cow, a horse), which nobody else can lift by hand.</li>
 * <li>Thrusters: a jump boost.</li>
 * <li>Helm (HUD) and Core (charge, the Mind) are read elsewhere.</li>
 * </ul>
 */
public final class RigPassives {
	/** Bare-fisted, with the Arms: extra attack damage and knockback (Gravity, Z Fighter: these extra). */
	static final double PUNCH_DAMAGE = 2.0, PUNCH_KNOCKBACK = 0.5, GRAVITY_PUNCH_DAMAGE = 2.0, ZFIGHTER_PUNCH_KNOCKBACK = 1.0;
	/** The Thrusters' jump boost (jump strength, vanilla 0.42). */
	static final double JUMP_BOOST = 0.12;

	private static final Identifier PUNCH_ID = JaymosPowers.id("rig_punch"), PUNCH_KNOCK_ID = JaymosPowers.id("rig_punch_knockback"),
		JUMP_ID = JaymosPowers.id("rig_jump");

	private RigPassives() {
	}

	static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
				|| !Rig.wears(player, Rig.Piece.ARMS) || !KitchenCompat.isHeavy(entity) && !KitchenCompat.isTooBig(entity)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer server) {
				if (KitchenCompat.isCarryingCorpse(server)) {
					return InteractionResult.PASS;
				}
				KitchenCompat.take(server, entity);
				if (KitchenCompat.isCarryingCorpse(server)) {
					level.playSound(null, server.getX(), server.getY(), server.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.6F, 0.7F);
					server.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.lift"));
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	static void tick(ServerPlayer player, boolean wearing) {
		boolean arms = wearing && Rig.wears(player, Rig.Piece.ARMS) && player.getMainHandItem().isEmpty();
		Rig.Augment augment = arms ? Rig.augment(player) : null;
		set(player, Attributes.ATTACK_DAMAGE, PUNCH_ID, arms ? PUNCH_DAMAGE + (augment == Rig.Augment.GRAVITY ? GRAVITY_PUNCH_DAMAGE : 0.0) : 0.0);
		set(player, Attributes.ATTACK_KNOCKBACK, PUNCH_KNOCK_ID,
			arms ? PUNCH_KNOCKBACK + (augment == Rig.Augment.ZFIGHTER ? ZFIGHTER_PUNCH_KNOCKBACK : 0.0) : 0.0);
		set(player, Attributes.JUMP_STRENGTH, JUMP_ID, wearing && Rig.wears(player, Rig.Piece.THRUSTERS) ? JUMP_BOOST : 0.0);
	}

	/** Keeps a flat bonus on (amount non-zero) or off. */
	private static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount != 0.0) {
			AttributeModifier current = instance.getModifier(id);
			if (current == null || current.amount() != amount) {
				instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
			}
		} else if (instance.hasModifier(id)) {
			instance.removeModifier(id);
		}
	}
}
