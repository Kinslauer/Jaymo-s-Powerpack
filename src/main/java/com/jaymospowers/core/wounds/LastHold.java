package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BlastGore;
import com.jaymospowers.core.Dismember;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * The last hold: a player who is attacked (a blow from someone or something, not a fall, fire, drowning, hunger or the void)
 * takes one extra hit when it would have downed them ({@link Downed}): they are left on {@link #HOLD_HEALTH} instead, and the
 * hold is spent. The next blow that would down them downs them as usual. The hold comes back after {@link #HOLD_RECHARGE_TICKS}
 * without taking any damage. A beheading, a gib, a cut in half or anything that skips the downed state ignores it, as do the
 * downed and the executed. It is kept for the session only (a relog gives it back).
 * <p>
 * It decides before every other save, so Kingdoms' Last Stand only answers a blow once the hold is spent, and the two never
 * answer the same one.
 */
public final class LastHold {
	/** The health the hold leaves them on (half a heart), and how long without damage it takes to come back (ticks). */
	static final float HOLD_HEALTH = 1.0F;
	static final long HOLD_RECHARGE_TICKS = 600L;

	/** Game time the hold was spent at; absent while it stands. Synced to its owner, whose screen pulses (DownedHud). */
	public static final AttachmentType<Long> SPENT = AttachmentRegistry.create(JaymosPowers.id("last_hold_spent"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** The decision comes before the default phase, where the other saves (Kingdoms' Last Stand among them) are. */
	private static final Identifier FIRST = JaymosPowers.id("last_hold");

	/** Player → game time of the last damage they took. Not saved. */
	private static final Map<ServerPlayer, Long> LAST_DAMAGE = new WeakHashMap<>();

	private LastHold() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && amount > 0.0F) {
				long now = player.level().getGameTime();
				Long last = LAST_DAMAGE.put(player, now);
				if (last != null && now - last >= HOLD_RECHARGE_TICKS) {
					player.removeAttached(SPENT);
				}
			}
			return true;
		});
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(FIRST, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(FIRST, (entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || player.hasAttached(SPENT) || !Downed.attack(source) || Downed.isDowned(player)
				|| Downed.FINISHING.contains(player) || !Downed.downable(player) || !Downed.spared(player, source)
				// (a blast that blows them apart, or a blow that cuts them in half, leaves nothing to hold)
				|| BlastGore.gibs(player, source, amount) || Dismember.halvable(player, source, amount)) {
				return true;
			}
			player.setAttached(SPENT, player.level().getGameTime());
			player.setHealth(HOLD_HEALTH);
			return false;
		});
	}
}
