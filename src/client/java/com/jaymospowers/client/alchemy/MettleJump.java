package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.picks.MettleDraught;
import com.jaymospowers.client.jump.PowerJumpClient;

/**
 * The Draught of Mettle's one more jump in the air ({@link MettleDraught}): while it works, the shared second jump
 * ({@link PowerJumpClient}) is theirs too, a plain kick straight up, for someone with no power of their own.
 */
public final class MettleJump {
	private MettleJump() {
	}

	static void init() {
		PowerJumpClient.mortal(MettleDraught::active);
	}
}
