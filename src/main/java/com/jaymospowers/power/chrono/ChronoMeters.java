package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;

/**
 * The speedster's small meters, shown only to the runner (bottom right, under the gear chip): how much of the held
 * phase ({@link ChronoPhase}), the slowed world ({@link ChronoSlowTime}) and a held catch ({@link ChronoRapid}) is left.
 * Each is a percentage, 0 to 100; a meter not running is not set at all.
 */
public final class ChronoMeters {
	/** Meter attachments, synced to the owner only. */
	public static final AttachmentType<Integer> PHASE = meter("chrono_meter_phase");
	public static final AttachmentType<Integer> SLOW = meter("chrono_meter_slow");
	public static final AttachmentType<Integer> CAUGHT = meter("chrono_meter_caught");
	/** Calculation ({@link ChronoCalculation}): the bar, and while it is being spent the dodging left. */
	public static final AttachmentType<Integer> CALC = meter("chrono_meter_calc");
	public static final AttachmentType<Integer> CALC_RUN = meter("chrono_meter_calc_run");

	private ChronoMeters() {
	}

	private static AttachmentType<Integer> meter(String name) {
		return AttachmentRegistry.create(JaymosPowers.id(name), builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	}

	/** Sets a meter to {@code share} (0 to 1), or takes it off when {@code share} is negative. Only sends a change. */
	static void set(ServerPlayer player, AttachmentType<Integer> meter, float share) {
		if (share < 0.0F) {
			if (player.getAttached(meter) != null) {
				player.removeAttached(meter);
			}
			return;
		}
		int percent = Math.round(Math.min(1.0F, share) * 100.0F);
		Integer old = player.getAttached(meter);
		if (old == null || old != percent) {
			player.setAttached(meter, percent);
		}
	}
}
