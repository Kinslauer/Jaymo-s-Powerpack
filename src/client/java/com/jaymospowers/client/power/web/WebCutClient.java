package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebBridges;
import com.jaymospowers.power.web.WebCut;
import com.jaymospowers.power.web.WebHitches;
import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebPower;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Punching a web line breaks it, for anyone: an attack that would otherwise hit nothing (or something further off)
 * checks every web line in reach: anywhere along it, from the shooter's hand to where it's stuck.
 */
public final class WebCutClient {
	/** How close the crosshair must pass to a line (blocks), and near either end (where it's easier to hit). */
	private static final double LINE_RADIUS = 0.3, END_RADIUS = 0.6, END_ZONE = 0.8;

	private WebCutClient() {
	}

	public static void register() {
		ClientPreAttackCallback.EVENT.register((minecraft, player, clicks) -> clicks > 0 && tryCut(minecraft, player));
	}

	private record Hit(byte kind, int entity, Vec3 a, Vec3 b, double along) {
	}

	private static boolean tryCut(Minecraft minecraft, LocalPlayer player) {
		ClientLevel level = minecraft.level;
		if (level == null || player.isSpectator()) {
			return false;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double reach = WebCut.REACH;
		// only if the line is nearer than whatever the punch would hit anyway
		HitResult vanilla = minecraft.hitResult;
		if (vanilla != null && vanilla.getType() != HitResult.Type.MISS) {
			reach = Math.min(reach, eye.distanceTo(vanilla.getLocation()));
		}
		Vec3 end = eye.add(look.scale(reach));
		Hit best = null;
		for (Player other : level.players()) {
			Vec3 from = other.getEyePosition().add(0.0, -0.4, 0.0);
			for (int hand = WebNet.MAIN; hand <= WebNet.OFF; hand++) {
				Vec3 to = WebArms.lineEnd(minecraft, level, other, hand, 1.0F);
				if (to != null) {
					best = closer(best, test(eye, end, reach, WebCut.SWING, other.getId(), from, to));
				}
			}
		}
		for (Entity entity : level.entitiesForRendering()) {
			List<WebHitches.Line> lines = entity.getAttached(WebPower.HITCH);
			if (lines == null) {
				continue;
			}
			Vec3 center = entity.getBoundingBox().getCenter();
			for (WebHitches.Line line : lines) {
				best = closer(best, test(eye, end, reach, WebCut.HITCH, entity.getId(), center, line.anchor()));
			}
		}
		List<WebBridges.Span> spans = level.getAttached(WebBridges.SPANS);
		if (spans != null) {
			for (WebBridges.Span span : spans) {
				best = closer(best, test(eye, end, reach, WebCut.BRIDGE, -1, span.from(), span.to()));
			}
		}
		if (best == null) {
			return false;
		}
		ClientPlayNetworking.send(new WebCut.Cut(best.kind(), best.entity(), best.a(), best.b()));
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
		return true;
	}

	private static Hit closer(Hit best, Hit hit) {
		return hit == null ? best : best == null || hit.along() < best.along() ? hit : best;
	}

	/** Where the punch (eye to {@code end}) passes the line a-b, if close enough. */
	private static Hit test(Vec3 eye, Vec3 end, double reach, byte kind, int entity, Vec3 a, Vec3 b) {
		double[] st = closest(eye, end, a, b);
		Vec3 onRay = eye.add(end.subtract(eye).scale(st[0]));
		Vec3 onLine = a.add(b.subtract(a).scale(st[1]));
		double length = a.distanceTo(b);
		boolean nearEnd = st[1] * length < END_ZONE || (1.0 - st[1]) * length < END_ZONE;
		if (onRay.distanceTo(onLine) > (nearEnd ? END_RADIUS : LINE_RADIUS)) {
			return null;
		}
		return new Hit(kind, entity, a, b, st[0] * reach);
	}

	/** The parameters (0-1 along each) of the closest points between segments p0-p1 and q0-q1. */
	private static double[] closest(Vec3 p0, Vec3 p1, Vec3 q0, Vec3 q1) {
		Vec3 d1 = p1.subtract(p0), d2 = q1.subtract(q0), r = p0.subtract(q0);
		double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r);
		double s, t;
		if (a < 1.0E-9 && e < 1.0E-9) {
			return new double[] {0.0, 0.0};
		}
		if (a < 1.0E-9) {
			s = 0.0;
			t = Math.clamp(f / e, 0.0, 1.0);
		} else {
			double c = d1.dot(r);
			if (e < 1.0E-9) {
				t = 0.0;
				s = Math.clamp(-c / a, 0.0, 1.0);
			} else {
				double b = d1.dot(d2);
				double denom = a * e - b * b;
				s = denom > 1.0E-9 ? Math.clamp((b * f - c * e) / denom, 0.0, 1.0) : 0.0;
				t = (b * s + f) / e;
				if (t < 0.0) {
					t = 0.0;
					s = Math.clamp(-c / a, 0.0, 1.0);
				} else if (t > 1.0) {
					t = 1.0;
					s = Math.clamp((b - c) / a, 0.0, 1.0);
				}
			}
		}
		return new double[] {s, t};
	}
}
