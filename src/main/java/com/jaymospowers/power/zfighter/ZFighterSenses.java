package com.jaymospowers.power.zfighter;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.45, Andy's NOEA picks) The hub of the senses patch: ki sense ({@link ZFighterKiSense}), the Scouter
 * ({@link ZFighterScouter}), Instant Teleport ({@link ZFighterTeleport}), lend ki ({@link ZFighterLendKi}), the air burst
 * ({@link ZFighterAirBurst}) and the impact frames sent from here. The beam clash timing game is {@code core/BeamClashTiming}.
 * {@link ZFighterPower} makes one call to {@link #init}, one to {@link #tick}, and the finishers, the Spirit Bomb and
 * the ape smash one call each to {@link #impactFrame}.
 */
public final class ZFighterSenses {
	/** Players this near (blocks) to an impact see its frames. */
	static final double IMPACT_FRAME_RANGE = 24.0;

	private ZFighterSenses() {
	}

	/** Called once from {@link ZFighterPower#init}. */
	static void init() {
		ZFighterSensesNet.register();
		ZFighterKiSense.init();
		ZFighterScouter.init();
		ZFighterTeleport.init();
		ZFighterLendKi.init();
		ZFighterAirBurst.init();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ZFighterScouter.forget(handler.player.getUUID()));
	}

	/** Every tick of a Z Fighter, from {@link ZFighterPower#tick}. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		ZFighterTeleport.tick(player, live);
	}

	/**
	 * A finisher, a Spirit Bomb's landing or a full ape smash at {@code at}: players within {@link #IMPACT_FRAME_RANGE}
	 * blocks get two frames of black and white (each player's client setting can turn them off). (0.18.30) Public: Jaymo's
	 * Kingdoms' Rain of Cuts calls it by reflection ({@code PowersCompat.impactFrame}).
	 */
	public static void impactFrame(ServerLevel level, Vec3 at) {
		ZFighterSensesNet.ImpactFrame frame = new ZFighterSensesNet.ImpactFrame();
		for (ServerPlayer viewer : level.players()) {
			if (viewer.position().distanceToSqr(at) <= IMPACT_FRAME_RANGE * IMPACT_FRAME_RANGE) {
				ZFighterSensesNet.send(viewer, frame);
			}
		}
	}
}
