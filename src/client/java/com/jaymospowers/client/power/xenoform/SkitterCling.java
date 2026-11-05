package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoformState;
import com.jaymospowers.power.xenoform.forms.Skitter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.59) Skitter's ceiling cling, held by the clinging player's own client: the server pushing it up into the
 * ceiling was overruled by the client's own movement, so it never stuck. The same rules as the server's
 * ({@code Skitter.tick}, which keeps the {@link XenoformState#CEILING} state everyone draws): standing, hold jump under
 * a ceiling to cling and let go to drop; prone (the spider) it clings with nothing held, and a jump press lets go until
 * it lands. Called from {@code SkitterClingMixin} before each move.
 */
public final class SkitterCling {
	private static boolean clinging, letGo;

	private SkitterCling() {
	}

	public static void beforeMove(LocalPlayer player) {
		if (!"skitter".equals(player.getAttached(XenoformState.FORM)) || player.isSpectator() || player.getAbilities().flying) {
			clinging = letGo = false;
			return;
		}
		boolean prone = Skitter.crawling(player);
		boolean pressed = player.input.keyPresses.jump();
		if (player.onGround() || !prone) {
			letGo = false;
		} else if (clinging && pressed) {
			letGo = true;
		}
		boolean want = prone ? !letGo : pressed;
		if (!want || player.isInWater() || !Skitter.ceiling(player)) {
			clinging = false;
		} else if (!clinging && !player.onGround()) {
			clinging = true;
		}
		if (clinging) {
			// pressed up against it, crawling along it no faster than a crawl
			Vec3 m = player.getDeltaMovement().horizontal();
			Vec3 crawl = m.lengthSqr() > Skitter.CEILING_CRAWL * Skitter.CEILING_CRAWL ? m.normalize().scale(Skitter.CEILING_CRAWL) : m;
			player.setDeltaMovement(crawl.x, Skitter.CEILING_GRIP, crawl.z);
			player.resetFallDistance();
		}
	}
}
