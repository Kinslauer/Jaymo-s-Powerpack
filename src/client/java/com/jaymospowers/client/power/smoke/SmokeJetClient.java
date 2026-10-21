package com.jaymospowers.client.power.smoke;

import com.jaymospowers.core.Powers;
import com.jaymospowers.power.smoke.SmokeJet;
import com.jaymospowers.power.smoke.SmokeParticles;
import com.jaymospowers.power.smoke.SmokePower;
import com.jaymospowers.power.web.WebSymbiote;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Smoke Jet: X held for a Smoke user (X is registered by the symbiote's menu; a Smoke user's presses are taken here,
 * at the start of the tick, before the symbiote's handler would see them), and the jet as everyone in view sees it
 * ({@link SmokeJet#JETTING}): thick smoke driven out of the hands, slowing and billowing out as it goes. The smoke it
 * lays down is the real gas, drawn by {@link SmokeFieldClient}.
 */
final class SmokeJetClient {
	/** The power skill key's mapping (registered in {@code WebSymbioteClient}). */
	private static final String KEY_NAME = "key.jaymospowers.web_symbiote";
	/** How fast its smoke leaves the hands (blocks/tick) and how much it wanders; how many a tick. */
	private static final double SPEED = 0.7, WANDER = 0.05;
	private static final int PUFFS = 14;
	/** How far ahead of the eyes it starts (blocks); your own in first person. */
	private static final double OUT = 0.9, OWN_OUT = 1.6;

	private static @Nullable KeyMapping key;
	private static boolean holding;

	private SmokeJetClient() {
	}

	static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(SmokeJetClient::input);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!minecraft.isPaused()) {
				draw(minecraft);
			}
		});
	}

	private static @Nullable KeyMapping key(Minecraft minecraft) {
		if (key == null) {
			for (KeyMapping mapping : minecraft.options.keyMappings) {
				if (KEY_NAME.equals(mapping.getName())) {
					key = mapping;
					break;
				}
			}
		}
		return key;
	}

	private static void input(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		KeyMapping mapping = key(minecraft);
		if (mapping == null) {
			return;
		}
		if (player == null || !Powers.has(player, SmokePower.INSTANCE) || WebSymbiote.isBonded(player)) {
			holding = false;
			return;
		}
		boolean clicked = false;
		while (mapping.consumeClick()) {
			// taken: held is all that counts (but for a beam clash's quick taps)
			clicked = true;
		}
		boolean down = mapping.isDown() && minecraft.gui.screen() == null && !player.isSpectator();
		if (clicked && !down && !holding && minecraft.gui.screen() == null && com.jaymospowers.client.BeamClashClient.isClashing(player)) {
			// a tap quicker than a tick, mashing in a beam clash
			ClientPlayNetworking.send(new SmokeJet.Hold(true));
			ClientPlayNetworking.send(new SmokeJet.Hold(false));
		} else if (down != holding) {
			holding = down;
			ClientPlayNetworking.send(new SmokeJet.Hold(down));
		}
	}

	private static void draw(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Player player : level.players()) {
			if (!SmokeJet.isActive(player) || com.jaymospowers.client.BeamClashClient.isClashing(player)) {
				// (a clashing jet is drawn as a beam into the knot, by BeamClashClient)
				continue;
			}
			Vec3 look = player.getLookAngle();
			// your own starts a little further out, so it doesn't fill your view
			double out = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson() ? OWN_OUT : OUT;
			Vec3 hands = player.getEyePosition().add(look.scale(out)).add(0.0, -0.25, 0.0);
			HitResult hit = level.clip(new ClipContext(hands, hands.add(look.scale(SmokeJet.RANGE)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
			double reach = hit.getType() == HitResult.Type.MISS ? SmokeJet.RANGE : hit.getLocation().distanceTo(hands);
			// out of the hands, and all along it, thicker and slower the further it goes
			for (int i = 0; i < PUFFS; i++) {
				double t = i == 0 ? 0.0 : random.nextDouble() * reach;
				double r = (SmokeJet.MOUTH + t * SmokeJet.SPREAD) * 0.6;
				Vec3 p = hands.add(look.scale(t));
				Vec3 v = look.scale(SPEED * (1.0 - 0.5 * t / SmokeJet.RANGE) * (0.7 + random.nextDouble() * 0.5)).add(random.nextGaussian() * WANDER,
					random.nextGaussian() * WANDER, random.nextGaussian() * WANDER);
				level.addParticle(SmokeParticles.puff(player, t > reach * 0.4 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE), p.x + random.nextGaussian() * r,
					p.y + random.nextGaussian() * r, p.z + random.nextGaussian() * r, v.x, v.y, v.z);
			}
			// it billows out where it slows, and where a wall breaks it
			if (player.tickCount % 2 == 0) {
				Vec3 end = hands.add(look.scale(Math.max(0.0, reach - 0.3)));
				level.addParticle(SmokeParticles.puff(player, ParticleTypes.CAMPFIRE_COSY_SMOKE), end.x + random.nextGaussian() * 0.4, end.y + random.nextGaussian() * 0.4,
					end.z + random.nextGaussian() * 0.4, random.nextGaussian() * 0.02, 0.02, random.nextGaussian() * 0.02);
			}
		}
	}
}
