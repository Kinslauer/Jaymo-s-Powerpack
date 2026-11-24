package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * What hurt you, when nothing did it to you. Andy's clip "stop hitting me": his hearts fell half a heart at a time with
 * nobody near, and nothing on screen said why. Now a hurt with no attacker (heatstroke, thirst, bleeding, a parasite,
 * the cold) says so under the crosshair, in the character's own voice (Andy: instead of "Hurt: fire", "that BURNS"):
 * "Too hot... I'm cooking". Once per cause, again after {@link #REPEAT_TICKS}; falls
 * aren't named (you saw it). The server log gets the damage type's id too, so an unnamed one can be traced.
 */
public final class HurtCause {
	/** The same cause is named again after this long (ticks). */
	static final int REPEAT_TICKS = 200;

	/** The cause last named to each player, and when. */
	private record Said(String id, long at) {
	}

	private static final Map<ServerPlayer, Said> SAID = new WeakHashMap<>();

	private HurtCause() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(HurtCause::hurt);
	}

	private static void hurt(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (!(victim instanceof ServerPlayer player) || taken <= 0.0F || source.getEntity() != null || source.getDirectEntity() != null
			|| source.is(DamageTypeTags.IS_FALL) || player.isCreative() || player.isSpectator() || Downed.isDowned(player)) {
			return;
		}
		String id = source.getMsgId();
		long now = player.level().getGameTime();
		Said said = SAID.get(player);
		if (said != null && said.id().equals(id) && now - said.at() < REPEAT_TICKS) {
			return;
		}
		SAID.put(player, new Said(id, now));
		JaymosPowers.LOGGER.info("{} hurt by {} ({} health), nobody did it", player.getScoreboardName(), id, taken);
		// said as the character would say it ("That BURNS!"); a cause without a line of its own still gets its name
		player.sendOverlayMessage(Component.translatableWithFallback("hurt_cause.jaymospowers." + id, "Ugh... " + plain(id) + "!")
			.withStyle(ChatFormatting.RED));
	}

	/** A damage type's id as words, for one without a line of its own: {@code jaymoskingdoms.old_age} is "old age". */
	static String plain(String id) {
		String last = id.substring(id.lastIndexOf('.') + 1);
		return last.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
	}
}
