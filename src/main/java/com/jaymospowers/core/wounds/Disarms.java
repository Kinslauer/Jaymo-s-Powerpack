package com.jaymospowers.core.wounds;

import com.jaymospowers.boxing.BoxingGear;
import com.jaymospowers.combat.HitSounds;
import com.jaymospowers.core.Dismember;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A hard blow to the hand or forearm knocks what's held in it loose: the weapon flies to the ground a little way off
 * ({@link DroppedArms#knock}). It works on mobs and players alike, players more rarely. The chance climbs with the
 * damage that landed on the arm and falls with the armour over it ({@link Dismember#armourGuard}) and with boxing gear
 * (gauntlets, cestus, bracers) held in the other hand. A parry's disarm ({@code combat/Guard}) and the downed's
 * ({@code Downed}) are their own.
 */
public final class Disarms {
	/** A blow to the arm of less than this many health points never loosens a grip. */
	public static final float MIN_DAMAGE = 3.0F;
	/** The chance at {@link #MIN_DAMAGE}, and how much more each point of damage adds, to at most {@link #MOST}. */
	public static final float BASE = 0.08F, PER_DAMAGE = 0.025F, MOST = 0.6F;
	/** Players keep their grip this much better than a mob. */
	public static final float PLAYER_SCALE = 0.4F;
	/** Boxing gear held in the other hand leaves this share of the chance. */
	public static final float GAUNTLET_SHARE = 0.5F;
	/** One body is disarmed no more than once in this many ticks. */
	public static final int COOLDOWN_TICKS = 60;

	/** When (game time) each body was last disarmed. */
	private static final Map<LivingEntity, Long> LAST = new WeakHashMap<>();

	private Disarms() {
	}

	/** {@code victim} took {@code loss} from {@code source}, routed to {@code landed} (from {@link Wounds}, for a blow that wasn't blocked). */
	static void onHit(LivingEntity victim, DamageSource source, @Nullable BodyPart landed, float loss) {
		if (landed != BodyPart.LEFT_ARM && landed != BodyPart.RIGHT_ARM || loss < MIN_DAMAGE || source.getEntity() == null
			|| !(victim.level() instanceof ServerLevel level) || !victim.isAlive() || Breaks.missing(victim, landed)) {
			return;
		}
		InteractionHand hand = Dismember.hand(victim, Breaks.limb(landed));
		ItemStack held = victim.getItemInHand(hand);
		if (!DroppedArms.weapon(held)) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST.get(victim);
		if (last != null && now >= last && now - last < COOLDOWN_TICKS) {
			return;
		}
		if (level.getRandom().nextFloat() >= chance(victim, landed, loss)) {
			return;
		}
		LAST.put(victim, now);
		DroppedArms.knock(level, victim, hand, source.getSourcePosition());
		level.playSound(null, victim.getX(), victim.getY(0.6), victim.getZ(), HitSounds.DISARM, victim.getSoundSource(), 0.8F, 1.0F);
		if (victim instanceof Player player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.disarmed").withStyle(ChatFormatting.RED));
		}
	}

	/** The chance a blow of {@code loss} on {@code arm} of {@code victim} loosens what its hand holds. */
	public static float chance(LivingEntity victim, BodyPart arm, float loss) {
		float chance = Math.min(MOST, BASE + (loss - MIN_DAMAGE) * PER_DAMAGE);
		chance *= Dismember.armourGuard(victim, Breaks.limb(arm));
		InteractionHand other = Dismember.hand(victim, Breaks.limb(arm)) == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		if (BoxingGear.isGear(victim.getItemInHand(other))) {
			chance *= GAUNTLET_SHARE;
		}
		if (victim instanceof Player) {
			chance *= PLAYER_SCALE;
		}
		return Mth.clamp(chance, 0.0F, MOST);
	}
}
