package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoFlow;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Time Flow on the client: an attack aimed toward an enemy picks it (the one nearest where you look, within
 * {@link ChronoFlow#RANGE}) and carries you to it for the blow (movement is the client's); right click is the counter;
 * the combo count shows over the crosshair.
 */
public final class ChronoFlowClient {
	/** How far off your look a target can be (cosine), and how much nearness counts against aim when choosing. */
	private static final double AIM_CONE = 0.55, DISTANCE_WEIGHT = 0.05;
	/** Lunge and flying kick: ticks spent closing in, stopping this far short, and the kick's hop. */
	private static final int LUNGE_TICKS = 4, KICK_TICKS = 7;
	private static final double STOP_SHORT = 1.5, KICK_HOP = 0.38;
	/** Circle counter: a short duck, then this many ticks round to the attacker's back, at this distance from it. */
	private static final int CIRCLE_DELAY = 2, CIRCLE_TICKS = 10;
	private static final double CIRCLE_MIN_RADIUS = 1.6, CIRCLE_MAX_RADIUS = 2.6, CIRCLE_MAX_STEP = 1.0;
	/** Slip counter: a quick sidestep (and a touch back) out of the blow. */
	private static final int SLIP_TICKS = 3;
	private static final double SLIP_SPEED = 0.42, SLIP_BACK = 0.12;

	/** Your own blow, shown at once (the server's copy arrives a moment later). */
	static ChronoFlow.@Nullable Move local;
	private static int closeBlows;
	private static long busyUntil;
	private static int dashTicks;
	private static int dashTarget = -1;
	private static Vec3 dashVelocity = Vec3.ZERO;
	/** The counter under way (from the server, which picks the variant), and where the circle started round its target. */
	private static ChronoFlow.@Nullable Move counter;
	private static long counterSeen = Long.MIN_VALUE;
	private static double arcAngle, arcRadius;

	private ChronoFlowClient() {
	}

	static void init() {
		ClientPreAttackCallback.EVENT.register((minecraft, player, clicks) -> clicks > 0 && ChronoFlow.on(player) && strike(minecraft, player));
		ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
			LocalPlayer player = minecraft.player;
			if (player == null || !ChronoFlow.on(player) || minecraft.gui.screen() != null || player.isShiftKeyDown()) {
				return;
			}
			// right click is the counter
			boolean pressed = false;
			while (minecraft.options.keyUse.consumeClick()) {
				pressed = true;
			}
			if (pressed && counter == null) {
				ClientPlayNetworking.send(new ChronoFlow.Parry());
				local = new ChronoFlow.Move(ChronoFlow.COUNTER, player.level().getGameTime(), -1, 1);
			}
			minecraft.options.keyUse.setDown(false);
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			dash(minecraft);
			counterMotion(minecraft);
		});
	}

	/** An attack in Time Flow: at an enemy, a strike (and the swing is ours, not vanilla's). */
	private static boolean strike(Minecraft minecraft, LocalPlayer player) {
		long now = player.level().getGameTime();
		if (now < busyUntil) {
			return true; // mid-blow: the click is swallowed
		}
		LivingEntity target = pick(player);
		if (target == null) {
			return false;
		}
		Vec3 to = target.position().subtract(player.position());
		double distance = to.horizontalDistance();
		int style = ChronoFlow.styleFor(distance, closeBlows);
		if (style <= ChronoFlow.UPPERCUT) {
			closeBlows++;
		}
		busyUntil = now + ChronoFlow.HIT_AT[style] + 1;
		local = new ChronoFlow.Move(style, now, target.getId(), closeBlows % 2 == 0 ? 1 : -1);
		ClientPlayNetworking.send(new ChronoFlow.Strike(target.getId(), style));
		// face it
		float yaw = (float)(Math.atan2(to.z, to.x) * 180.0 / Math.PI) - 90.0F;
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		if (style == ChronoFlow.LUNGE || style == ChronoFlow.KICK) {
			int ticks = style == ChronoFlow.KICK ? KICK_TICKS : LUNGE_TICKS;
			Vec3 flat = new Vec3(to.x, 0.0, to.z).normalize();
			dashVelocity = flat.scale(Math.max(0.0, distance - STOP_SHORT) / ticks);
			dashTicks = ticks;
			dashTarget = target.getId();
			if (style == ChronoFlow.KICK) {
				player.setDeltaMovement(dashVelocity.x, KICK_HOP, dashVelocity.z);
			}
		}
		return true;
	}

	/** Carried to the target over a few ticks (stopping short of it). */
	private static void dash(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || dashTicks <= 0) {
			return;
		}
		Entity target = player.level().getEntity(dashTarget);
		if (target == null || player.distanceTo(target) - target.getBbWidth() * 0.5 < STOP_SHORT - 0.2) {
			dashTicks = 0;
			player.setDeltaMovement(player.getDeltaMovement().multiply(0.2, 1.0, 0.2));
			return;
		}
		dashTicks--;
		player.setDeltaMovement(dashVelocity.x, player.getDeltaMovement().y, dashVelocity.z);
		player.resetFallDistance();
		if (player.tickCount % 2 == 0) {
			player.level().addParticle(net.minecraft.core.particles.ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 0.0, 0.0, 0.0);
		}
	}

	/** A counter against one attacker: the circle carries you round to its back, the slip steps you aside. */
	private static void counterMotion(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			counter = null;
			return;
		}
		long now = player.level().getGameTime();
		ChronoFlow.Move move = player.getAttached(ChronoFlow.MOVE);
		if (move != null && move.style() == ChronoFlow.COUNTER && move.variant() != ChronoFlow.CROWD && move.start() != counterSeen) {
			counterSeen = move.start();
			counter = move;
			if (local != null && local.style() == ChronoFlow.COUNTER) {
				local = null; // the server's counter replaces the raised guard
			}
			busyUntil = Math.max(busyUntil, move.start() + move.duration());
			dashTicks = 0;
			Entity target = player.level().getEntity(move.target());
			if (target != null) {
				Vec3 from = player.position().subtract(target.position());
				arcAngle = Math.atan2(from.z, from.x);
				arcRadius = Math.clamp(from.horizontalDistance(), CIRCLE_MIN_RADIUS, CIRCLE_MAX_RADIUS);
			}
		}
		if (counter == null) {
			return;
		}
		long t = now - counter.start();
		Entity target = player.level().getEntity(counter.target());
		if (t >= counter.duration() || target == null || !player.isAlive()) {
			counter = null;
			return;
		}
		Vec3 to = target.position().subtract(player.position());
		if (counter.variant() == ChronoFlow.CIRCLE) {
			if (t < CIRCLE_DELAY) {
				return;
			}
			if (t > CIRCLE_DELAY + CIRCLE_TICKS) {
				if (t == CIRCLE_DELAY + CIRCLE_TICKS + 1) {
					player.setDeltaMovement(player.getDeltaMovement().multiply(0.2, 1.0, 0.2));
				}
			} else {
				double f = Math.min(1.0, (t + 1 - CIRCLE_DELAY) / (double)CIRCLE_TICKS);
				f = f * f * (3.0 - 2.0 * f);
				double a = arcAngle + counter.side() * Math.PI * f;
				Vec3 step = new Vec3(target.getX() + Math.cos(a) * arcRadius - player.getX(), 0.0,
					target.getZ() + Math.sin(a) * arcRadius - player.getZ());
				if (step.length() > CIRCLE_MAX_STEP) {
					step = step.normalize().scale(CIRCLE_MAX_STEP);
				}
				player.setDeltaMovement(step.x, player.getDeltaMovement().y, step.z);
				if (t % 2 == 0) {
					player.level().addParticle(net.minecraft.core.particles.ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 0.0, 0.0, 0.0);
				}
			}
		} else if (counter.variant() == ChronoFlow.SLIP && t <= SLIP_TICKS) {
			Vec3 flat = to.horizontal().lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal().normalize() : to.horizontal().normalize();
			if (t == SLIP_TICKS) {
				player.setDeltaMovement(player.getDeltaMovement().multiply(0.2, 1.0, 0.2));
			} else {
				Vec3 aside = new Vec3(-flat.z, 0.0, flat.x).scale(counter.side() * SLIP_SPEED).subtract(flat.scale(SLIP_BACK));
				player.setDeltaMovement(aside.x, player.getDeltaMovement().y, aside.z);
			}
		}
		if (counter.variant() == ChronoFlow.DEFLECT) {
			return; // a deflect stands its ground: the view stays yours
		}
		// eyes on them while moving round them
		float yaw = (float)(Math.atan2(to.z, to.x) * 180.0 / Math.PI) - 90.0F;
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
	}

	/** The enemy nearest where you look (and nearest you), within reach and in sight. */
	private static @Nullable LivingEntity pick(LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity e : player.level().getEntities(player, player.getBoundingBox().inflate(ChronoFlow.RANGE),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > ChronoFlow.RANGE || distance < 1.0E-3) {
				continue;
			}
			double aim = to.scale(1.0 / distance).dot(look);
			if (aim < AIM_CONE || !player.hasLineOfSight(e)) {
				continue;
			}
			double score = (1.0 - aim) + distance * DISTANCE_WEIGHT;
			if (score < bestScore) {
				bestScore = score;
				best = (LivingEntity)e;
			}
		}
		return best;
	}

	/** The combo count, big and gold, over the crosshair. */
	static void renderHud(GuiGraphicsExtractor graphics, LocalPlayer player) {
		Integer combo = player.getAttached(ChronoFlow.COMBO);
		if (combo == null || combo < 2 || !ChronoFlow.on(player)) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		Component text = Component.translatable("hud.jaymospowers.chrono_flow_combo", combo);
		int x = graphics.guiWidth() / 2 - font.width(text) / 2;
		int y = graphics.guiHeight() / 2 - 30;
		graphics.text(font, text, x, y, combo >= 10 ? 0xFFFFE08A : 0xFFF2C14E);
	}
}
