package com.jaymospowers.power.blindness;

import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Scars;
import com.jaymospowers.power.resonance.ResonancePower;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Andy (2026-11-11): "blindness should stay as is, just no longer considered a power, meaning you can go blind from
 * battle, or malicious intent. resonance gains the new abilities. so the two stay, but resonance is buffed, and you can
 * awaken resonance while blind with the fork."
 * <p>
 * Blindness is a <b>condition</b>: anyone's eyes can be lost, one at a time or both.
 * <ul>
 * <li><b>One eye gone</b> ({@link Scars.Kind#LEFT_EYE}, {@link Scars.Kind#RIGHT_EYE}): that side of the view is dark.</li>
 * <li><b>Both gone</b> (both of those, or {@link Scars.Kind#CARVED_EYES}): the view is dark, and sound paints it: every
 * noise near you ripples on the dark from where it came ({@link BlindnessEcholocation}).</li>
 * <li><b>From battle</b> ({@link #struck}): a heavy blow to a bare head may take an eye, a shot to it more often.</li>
 * <li><b>From malicious intent</b>: sneak and use shears on a downed player, twice (the first use warns them and you):
 * their eyes are carved out. On yourself, sneak and use shears on nothing ({@link BlindnessDevotion}). The Unseeing do
 * it to their own at their services.</li>
 * <li>Fresh sockets bleed until the head is bandaged. The eyes stay gone for that life.</li>
 * <li><b>Resonance in the dark</b>: a blind player with no power who strikes a Tuning Fork awakens to Resonance
 * ({@link #forkAwakens}); so does one who kept the blindfold's vigil and still wears it. A Resonance holder who can't
 * see hears the living through the dark ({@code BlindnessDevoutSight}).</li>
 * </ul>
 * The power this used to be is gone; whoever held it holds Resonance now ({@link #OLD_POWER}).
 */
public final class Blind {
	public static final int LEFT = 1, RIGHT = 2, BOTH = 3;
	/** A blow to a bare head this heavy (points lost) may take an eye: the chance for a blow, and for a shot. */
	static final float EYE_HIT_MIN = 6.0F, EYE_CHANCE = 0.08F, EYE_CHANCE_SHOT = 0.2F;
	/** Ticks the second cut on someone else has to follow the first use (the warning), and what the cutting costs them. */
	static final int CONFIRM_TICKS = 100;
	static final float CARVE_DAMAGE = 6.0F;
	/** The power Blindness was. Whoever still carries it becomes a Resonance holder as they join. */
	static final Identifier OLD_POWER = com.jaymospowers.JaymosPowers.id("blindness");

	/** attacker → the tick they were warned, and whom about. */
	private static final Map<UUID, Long> WARNED = new HashMap<>();
	private static final Map<UUID, UUID> WARNED_ABOUT = new HashMap<>();

	private Blind() {
	}

	/** Called once from {@code JaymosPowers.onInitialize()}: the condition, the cult and everything round it. */
	public static void init() {
		BlindnessEcho.register();
		BlindnessDevotion.init();
		Unseeing.register();
		BlindnessService.init();
		BlindnessEnclaves.init();
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer attacker) || !(entity instanceof ServerPlayer victim)
				|| !player.isShiftKeyDown() || !player.getItemInHand(hand).is(Items.SHEARS) || !Downed.isDowned(victim)) {
				return InteractionResult.PASS;
			}
			return carve(attacker, victim, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			if (OLD_POWER.equals(player.getAttached(Powers.POWER))) {
				// mastery and fate carry over as they were
				player.setAttached(Powers.POWER, ResonancePower.ID);
				ResonancePower.INSTANCE.onGained(player);
				player.sendSystemMessage(Component.translatable("message.jaymospowers.blindness.now_resonance").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			WARNED.remove(handler.player.getUUID());
			WARNED_ABOUT.remove(handler.player.getUUID());
			BlindnessEcholocation.forget(handler.player);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WARNED.clear();
			WARNED_ABOUT.clear();
			BlindnessEcholocation.clear();
		});
	}

	/** Which of {@code player}'s eyes are gone: {@link #LEFT}, {@link #RIGHT}, {@link #BOTH} or 0. Both sides (scars are synced). */
	public static int eyesLost(Player player) {
		if (Scars.has(player, Scars.Kind.CARVED_EYES)) {
			return BOTH;
		}
		return (Scars.has(player, Scars.Kind.LEFT_EYE) ? LEFT : 0) | (Scars.has(player, Scars.Kind.RIGHT_EYE) ? RIGHT : 0);
	}

	/** Both eyes gone. */
	public static boolean blind(Player player) {
		return eyesLost(player) == BOTH;
	}

	/** Can't see now: both eyes gone, or a blindfold on. */
	public static boolean sightless(LivingEntity entity) {
		return BlindnessDevotion.blindfolded(entity) || entity instanceof Player player && blind(player);
	}

	/** Whether striking a Tuning Fork now would awaken {@code player} to Resonance. */
	public static boolean forkAwakens(ServerPlayer player) {
		if (player.getAttached(Powers.POWER) != null || player.isCreative() || player.isSpectator()) {
			return false;
		}
		return blind(player) || BlindnessDevotion.vigilKept(player);
	}

	/**
	 * A blow that landed on {@code victim}'s head and cost it {@code loss} points. A helmet (anything on the head but a
	 * blindfold) keeps the eyes safe.
	 */
	public static void struck(ServerPlayer victim, DamageSource source, float loss) {
		if (loss < EYE_HIT_MIN || blind(victim) || victim.isCreative() || source.getEntity() == null && !source.is(DamageTypeTags.IS_PROJECTILE)) {
			return;
		}
		if (!victim.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !BlindnessDevotion.blindfolded(victim)) {
			return;
		}
		float chance = source.is(DamageTypeTags.IS_PROJECTILE) ? EYE_CHANCE_SHOT : EYE_CHANCE;
		if (victim.getRandom().nextFloat() >= chance) {
			return;
		}
		int lost = eyesLost(victim);
		takeEye(victim, lost == LEFT ? RIGHT : lost == RIGHT ? LEFT : victim.getRandom().nextBoolean() ? LEFT : RIGHT);
	}

	/** {@code victim} loses the eye on {@code side} ({@link #LEFT}, {@link #RIGHT}, or {@link #BOTH}); the socket bleeds until bandaged. */
	public static void takeEye(ServerPlayer victim, int side) {
		if ((eyesLost(victim) | side) == eyesLost(victim)) {
			return;
		}
		if (side == BOTH) {
			Scars.add(victim, Scars.Kind.CARVED_EYES);
		} else {
			Scars.add(victim, side == LEFT ? Scars.Kind.LEFT_EYE : Scars.Kind.RIGHT_EYE);
		}
		victim.setAttached(BlindnessDevotion.FRESH, true);
		Bleeding.open(victim, BodyPart.HEAD, false);
		victim.sendSystemMessage(Component.translatable("message.jaymospowers.blindness.sockets_bleed").withStyle(ChatFormatting.DARK_RED));
		if (blind(victim)) {
			victim.sendSystemMessage(Component.translatable("message.jaymospowers.blindness.blind").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	/** Sneak + shears on a downed player: warns both the first time, carves the eyes out the second. */
	private static boolean carve(ServerPlayer attacker, ServerPlayer victim, InteractionHand hand) {
		ServerLevel level = attacker.level();
		if (blind(victim) || !level.isPvpAllowed() || !attacker.canHarmPlayer(victim)) {
			return false;
		}
		long now = level.getGameTime();
		Long warned = WARNED.get(attacker.getUUID());
		if (warned == null || now - warned > CONFIRM_TICKS || !victim.getUUID().equals(WARNED_ABOUT.get(attacker.getUUID()))) {
			WARNED.put(attacker.getUUID(), now);
			WARNED_ABOUT.put(attacker.getUUID(), victim.getUUID());
			attacker.sendOverlayMessage(Component.translatable("message.jaymospowers.blindness.carve_other_warn", victim.getDisplayName())
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			victim.sendOverlayMessage(Component.translatable("message.jaymospowers.blindness.carve_victim_warn", attacker.getDisplayName())
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			return true;
		}
		WARNED.remove(attacker.getUUID());
		WARNED_ABOUT.remove(attacker.getUUID());
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 0.6F);
		victim.hurtServer(level, level.damageSources().playerAttack(attacker), Math.min(CARVE_DAMAGE, Math.max(0.0F, victim.getHealth() - 1.0F)));
		attacker.getItemInHand(hand).hurtAndBreak(1, attacker, hand);
		takeEye(victim, BOTH);
		return true;
	}
}
