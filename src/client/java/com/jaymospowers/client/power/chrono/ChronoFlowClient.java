package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoFlow;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.CameraType;
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
 * the combo count shows over the crosshair. While the mode is on the view is held over your shoulder in third person
 * (so you see the fight), and goes back to what it was when the mode ends. A counter or the barrage plays through before
 * another strike; the barrage holds you on its target.
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
	/** Vault counter: a crouch, then a hop of this height over the attacker, landing this far past it. */
	private static final int VAULT_DELAY = 3, VAULT_TICKS = 8;
	private static final double VAULT_HOP = 0.5, VAULT_LAND = 1.4, VAULT_MAX_STEP = 0.8;
	/** Throw counter: a quick step in, to this close, to catch the arm. */
	private static final int THROW_TICKS = 3;
	private static final double THROW_CLOSE = 0.9;
	/** Barrage: held this far from its target, closing at most this much a tick. */
	private static final double BARRAGE_GAP = 1.3, BARRAGE_MAX_STEP = 0.6;

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
	/** The barrage under way (from the server). */
	private static ChronoFlow.@Nullable Move barrage;
	/** The view before Time Flow took it over the shoulder (null while the mode is off). */
	private static @Nullable CameraType viewBefore;

	private ChronoFlowClient() {
	}

	static void init() {
		ClientPreAttackCallback.EVENT.register((minecraft, player, clicks) -> clicks > 0 && ChronoFlow.on(player) && strike(minecraft, player));
		ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
			view(minecraft);
			LocalPlayer player = minecraft.player;
			if (player == null || !ChronoFlow.on(player) || minecraft.gui.screen() != null || player.isShiftKeyDown()) {
				return;
			}
			// right click is the counter: always sent (mid-move it answers a blow held on you); the guard is only shown when free
			boolean pressed = false;
			while (minecraft.options.keyUse.consumeClick()) {
				pressed = true;
			}
			if (pressed) {
				ClientPlayNetworking.send(new ChronoFlow.Parry());
				long now = player.level().getGameTime();
				if (now >= busyUntil && counter == null && barrage == null) {
					local = new ChronoFlow.Move(ChronoFlow.COUNTER, now, -1, 1);
				}
			}
			minecraft.options.keyUse.setDown(false);
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			track(minecraft);
			dash(minecraft);
			counterMotion(minecraft);
			barrageMotion(minecraft);
		});
	}

	/** Time Flow holds the view over your shoulder; when it ends, the view you had comes back. */
	private static void view(Minecraft minecraft) {
		boolean on = minecraft.player != null && ChronoFlow.on(minecraft.player);
		if (on) {
			if (viewBefore == null) {
				viewBefore = minecraft.options.getCameraType();
			}
			if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
				minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		} else if (viewBefore != null) {
			minecraft.options.setCameraType(viewBefore);
			viewBefore = null;
		}
	}

	/** Whether the camera should sit over your shoulder now (Time Flow on, its third-person view in place). */
	public static boolean overShoulder(Minecraft minecraft) {
		return viewBefore != null && minecraft.player != null && minecraft.getCameraEntity() == minecraft.player
			&& ChronoFlow.on(minecraft.player) && minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
	}

	/** Movement keys are dead while the barrage holds you on its target. */
	public static boolean inputLocked() {
		return barrage != null;
	}

	/**
	 * A counter or the barrage from the server: strikes wait until it has played through, and whatever was under way (a
	 * dash, an earlier counter's motion, the barrage) gives way to it.
	 */
	private static void track(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			counter = null;
			barrage = null;
			return;
		}
		ChronoFlow.Move move = player.getAttached(ChronoFlow.MOVE);
		boolean locks = move != null && (move.style() == ChronoFlow.BARRAGE || move.style() == ChronoFlow.COUNTER && move.target() >= 0);
		if (barrage != null && (move == null || move.start() != barrage.start())) {
			barrage = null; // ended early, or broken off by a counter
		}
		if (!locks || move.start() == counterSeen) {
			return;
		}
		counterSeen = move.start();
		busyUntil = move.start() + move.duration();
		dashTicks = 0;
		counter = null;
		if (local != null && local.style() == ChronoFlow.COUNTER) {
			local = null; // the server's counter replaces the raised guard
		}
		if (move.style() == ChronoFlow.BARRAGE) {
			barrage = move;
		} else if (move.variant() != ChronoFlow.CROWD) {
			counter = move;
			Entity target = player.level().getEntity(move.target());
			if (target != null) {
				Vec3 from = player.position().subtract(target.position());
				arcAngle = Math.atan2(from.z, from.x);
				arcRadius = Math.clamp(from.horizontalDistance(), CIRCLE_MIN_RADIUS, CIRCLE_MAX_RADIUS);
			}
		}
	}

	/** The barrage: held close in front of its target, eyes on it, until the last punch. */
	private static void barrageMotion(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || barrage == null) {
			return;
		}
		long t = player.level().getGameTime() - barrage.start();
		Entity target = player.level().getEntity(barrage.target());
		if (t >= barrage.duration() || target == null || !player.isAlive()) {
			barrage = null;
			return;
		}
		Vec3 to = target.position().subtract(player.position());
		Vec3 flat = to.horizontal();
		double gap = flat.length() - target.getBbWidth() * 0.5 - BARRAGE_GAP;
		Vec3 step = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(Math.clamp(gap, -BARRAGE_MAX_STEP, BARRAGE_MAX_STEP));
		player.setDeltaMovement(step.x, player.getDeltaMovement().y, step.z);
		player.resetFallDistance();
		Vec3 eyes = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
		float yaw = (float)(Math.atan2(eyes.z, eyes.x) * 180.0 / Math.PI) - 90.0F;
		float pitch = (float)(-Math.atan2(eyes.y, eyes.horizontalDistance()) * 180.0 / Math.PI);
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.setXRot(Math.clamp(pitch, -90.0F, 90.0F));
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

	/**
	 * A counter against one attacker: the circle carries you round to its back, the slip steps you aside, the vault hops
	 * you over its head and the throw steps you in to it.
	 */
	private static void counterMotion(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			counter = null;
			return;
		}
		long now = player.level().getGameTime();
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
		} else if (counter.variant() == ChronoFlow.VAULT && t >= VAULT_DELAY) {
			if (t > VAULT_DELAY + VAULT_TICKS) {
				if (t == VAULT_DELAY + VAULT_TICKS + 1) {
					player.setDeltaMovement(player.getDeltaMovement().multiply(0.2, 1.0, 0.2));
				}
			} else {
				// straight over: to the far side of where it stood when the counter began
				double a = arcAngle + Math.PI;
				long left = VAULT_DELAY + VAULT_TICKS + 1 - t;
				Vec3 step = new Vec3(target.getX() + Math.cos(a) * VAULT_LAND - player.getX(), 0.0,
					target.getZ() + Math.sin(a) * VAULT_LAND - player.getZ()).scale(1.0 / left);
				if (step.length() > VAULT_MAX_STEP) {
					step = step.normalize().scale(VAULT_MAX_STEP);
				}
				double y = t == VAULT_DELAY ? VAULT_HOP + target.getBbHeight() * 0.1 : player.getDeltaMovement().y;
				player.setDeltaMovement(step.x, y, step.z);
				player.resetFallDistance();
				player.level().addParticle(net.minecraft.core.particles.ParticleTypes.ENCHANT, player.getX(), player.getY() + 0.5, player.getZ(), 0.0, 0.0, 0.0);
			}
		} else if (counter.variant() == ChronoFlow.THROW && t < THROW_TICKS) {
			double gap = to.horizontalDistance() - THROW_CLOSE;
			Vec3 flat = to.horizontal().lengthSqr() < 1.0E-4 ? Vec3.ZERO : to.horizontal().normalize();
			Vec3 step = gap > 0.0 ? flat.scale(gap / (THROW_TICKS - t)) : Vec3.ZERO;
			player.setDeltaMovement(step.x, player.getDeltaMovement().y, step.z);
		} else if (counter.variant() == ChronoFlow.THROW && t == THROW_TICKS) {
			player.setDeltaMovement(player.getDeltaMovement().multiply(0.2, 1.0, 0.2));
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
