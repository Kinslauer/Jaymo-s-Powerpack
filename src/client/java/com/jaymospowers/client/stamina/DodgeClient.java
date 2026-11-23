package com.jaymospowers.client.stamina;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.freerun.FreerunClient;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.freerun.LandingRoll;
import com.jaymospowers.stamina.Dodge;
import com.jaymospowers.stamina.Feathers;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.5) The dodge on this side (see {@link Dodge}): the Dodge key (Left Alt). R and Sneak + R belong to the powers.
 * The hop goes the way the movement keys point (back with none held), at once, and the server is told.
 * <p>(0.20.65) Andy: "alt should be roll". Sneak tapped twice no longer dodges (Combat Roll's old gesture): a double
 * tap held is the zoom now ({@code SneakZoom}), and OK Zoomer's own key came off Left Alt so Alt only dodges.
 * <p>Acrobatics: in the air it throws a trick when there's time before the ground, and on the ground with Back held alone
 * and the Backflip perk learnt it backflips instead of rolling back ({@code AcrobaticsClient}).
 * <p>Andy: "back dodge should only activate when locked, and they should all only activate once per press, holding the
 * button should make you cover a bit more ground, then gradually transition to prone". Each press of the key does one
 * thing, however long it's held (the key's repeats don't count). A roll goes back only while locked on to a foe
 * ({@code LockOnClient}): otherwise the back of the movement keys is dropped, so Back with Left or Right rolls to that
 * side, nothing held rolls forward, and Back alone doesn't roll. Held into a ground roll, the roll goes long
 * ({@link Dodge#HOLD_TICKS}) and, still held as it ends, you go on down into prone ({@code CrawlTaps.goProne}).
 * <p>Andy (2026-11-23): "pressing the alt key and a direction should make you do a short dash, saving the hold for our
 * current combat roll". On the ground a tap is the upright dash ({@link Dodge#DASH_SPEED}); held {@link Dodge#HOLD_TICKS}
 * the dash goes on into the long roll; let go before the roll ends and you come up on your feet, still held as it ends
 * and you go prone. A landing roll is still a roll (held, it goes long as before).
 */
public final class DodgeClient {
	private static @Nullable KeyMapping dodgeKey;
	/** Ticks until the next dodge (the server's {@link Dodge#COOLDOWN}, kept here too so the hop isn't spent twice). */
	private static int cooldown;
	/** (0.15.67) Ticks left in which a dodge pressed in the air still rolls you as you land ({@link LandingRoll}; everyone's since Motion patch 0). */
	private static int landingRoll;
	/** Ticks since this player last dodged (the HUD flashes the row). */
	public static int sinceDodge = Integer.MAX_VALUE;
	/** (0.15.74) Client game time our own last roll started and which way, drawn at once ({@link DodgeRollPose}). */
	static long rollStart = Long.MIN_VALUE;
	static float rollX, rollZ;
	/** Whether our own last roll was the flip in the air ({@link Dodge#canFlip}). */
	static boolean rollFlip;
	/** Whether our own last roll is the long one (the key held into it, {@link Dodge#HOLD_TICKS}). */
	static boolean rollLong;
	/** Our own dash or landing roll may still be held on (not yet {@link Dodge#HOLD_TICKS} in); our long roll's end may still take us prone. */
	private static boolean holdPending, proneAtEnd;
	/** Client game time our own dash started (it may still go on into the roll), or MIN_VALUE when there's none. */
	private static long dashStart = Long.MIN_VALUE;
	/** Client game time our own last dash started, drawn at once ({@link DodgeRollPose}), the way {@link #rollX}, {@link #rollZ}. */
	static long dashDrawStart = Long.MIN_VALUE;
	/** The Dodge key was down last tick: a press is the key going down, once, however long it's held. */
	private static boolean keyWasDown;
	/** Forgets our last own roll, so the next roll drawn for us is the server's copy (a leap off a mount ends in one the server throws). */
	public static void forgetRoll() {
		rollStart = Long.MIN_VALUE;
		dashStart = Long.MIN_VALUE;
		dashDrawStart = Long.MIN_VALUE;
		rollLong = false;
		holdPending = false;
		proneAtEnd = false;
		Dodge.localRollTicks = Dodge.ROLL_TICKS;
	}

	/** Leaving the ground faster than this upward (blocks/tick) is a jump, not a step off an edge. */
	static final double JUMP_RISE = 0.1;
	/** We were on the ground last tick; we're in the air off a jump; this jump's flip is spent. */
	private static boolean wasOnGround = true, jumped, flipped;
	/** This time in the air's dive over land is spent (one a jump). */
	private static boolean airDived;

	private DodgeClient() {
	}

	/**
	 * The Dodge key is held (Sneak + it zooms, see {@code SneakZoom}). (0.20.79) Read off the keyboard itself as well as the
	 * key mapping: Andy's Shift + Alt didn't zoom in 0.20.74-0.20.77 though the double tap did, so something between the
	 * key event and the mapping drops Alt while Shift is down. Left Alt itself always counts, wherever the Dodge key is.
	 */
	public static boolean keyDown() {
		KeyMapping key = dodgeKey;
		if (key == null) {
			return false;
		}
		if (key.isDown() || InputConstants.isKeyDown(InputConstants.KEY_LALT)) {
			return true;
		}
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
		return bound.getType() == InputConstants.Type.KEYBOARD && bound.getValue() > 0 && InputConstants.isKeyDown(bound.getValue());
	}

	/** The Dodge key's mapping (the move hints name its binding), or null before {@link #init}. */
	public static @Nullable KeyMapping key() {
		return dodgeKey;
	}

	public static void init() {
		dodgeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.dodge", InputConstants.KEY_LALT, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(DodgeClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (cooldown > 0) {
			cooldown--;
		}
		if (sinceDodge < Integer.MAX_VALUE) {
			sinceDodge++;
		}
		boolean down = keyDown();
		if (player == null || minecraft.gui.screen() != null) {
			landingRoll = 0;
			keyWasDown = down;
			return;
		}
		boolean ground = player.onGround();
		if (ground || player.isInWater() || player.isPassenger()) {
			com.jaymospowers.client.freerun.moves.AcrobaticsClient.grounded();
		}
		if (ground) {
			jumped = false;
			flipped = false;
			airDived = false;
		} else if (wasOnGround && player.getDeltaMovement().y > JUMP_RISE) {
			jumped = true;
		}
		wasOnGround = ground;
		boolean clicked = false;
		KeyMapping key = dodgeKey;
		while (key != null && key.consumeClick()) {
			clicked = true;
		}
		// one press, one move: the key going down (or a tap quicker than a tick), never its repeats while held;
		// (0.20.74) Sneak + Alt is the zoom (SneakZoom), not a dodge
		boolean wanted = (down || clicked) && !keyWasDown && !minecraft.options.keyShift.isDown();
		keyWasDown = down;
		if (wanted && dive(player)) {
			// (Andy, 2026-11-22) at an edge over deep water, or falling over it: a dive, head first
			wanted = false;
			landingRoll = 0;
		}
		if (wanted && vault(player)) {
			// (0.21.22) in the air over a raised shield: the vault, not a roll
			wanted = false;
		}
		if (wanted && jumped && !flipped && tryFlip(player)) {
			// in the air off a jump, armed and schooled: the flip, once a jump
			wanted = false;
			flipped = true;
			landingRoll = 0;
		}
		if (wanted && !player.onGround() && com.jaymospowers.client.freerun.moves.AcrobaticsClient.trick(player)) {
			// in the air with time to spare before the ground: a trick (Acrobatics); nearer the ground the press is the landing roll's
			wanted = false;
			landingRoll = 0;
		}
		if (wanted && !airDived && airDive(player)) {
			// (Andy, 2026-11-23) in the air over land with no flip or trick to throw: the head-first dive, then the roll as you land
			wanted = false;
			airDived = true;
			landingRoll = com.jaymospowers.water.Dives.AIR_DIVE_ROLL_TICKS;
		}
		if (wanted && !player.onGround() && player.getDeltaMovement().y < 0.0) {
			// falling: held until you land
			landingRoll = LandingRoll.EARLY_TICKS;
		} else if (wanted) {
			tryDodge(player, false);
		}
		if (landingRoll > 0) {
			if (player.onGround()) {
				landingRoll = 0;
				tryDodge(player, true);
			} else if (player.isInWater() || player.getAbilities().flying) {
				landingRoll = 0;
			} else {
				landingRoll--;
			}
		}
		tickHold(player, down);
	}

	/**
	 * (Andy, 2026-11-23) "The dive should be activated upon using alt in mid air. That way a quick space + alt gets you the
	 * dive everytime. And it can also be used to evade on the ground this way." Over land, in the air: thrown head first
	 * the way the movement keys point (forward with none, or Back alone unless locked on, as the roll), with a little
	 * lift; the landing roll that follows is the evade. Everyone sees it ({@code Dives#DIVING}, drawn by DivePose).
	 */
	private static boolean airDive(LocalPlayer player) {
		if (cooldown > 0 || !com.jaymospowers.water.Dives.canAirDive(player) || !Dodge.canDodge(player) || FreerunClient.active()
			|| !ClientPlayNetworking.canSend(com.jaymospowers.water.Dives.DiveKey.TYPE)) {
			return false;
		}
		Input input = player.input.keyPresses;
		float forward = (input.forward() ? 1.0F : 0.0F) - (input.backward() ? 1.0F : 0.0F);
		float strafe = (input.left() ? 1.0F : 0.0F) - (input.right() ? 1.0F : 0.0F);
		if (com.jaymospowers.client.LockOnClient.locked() == null && forward < 0.0F) {
			forward = 0.0F;
		}
		if (forward == 0.0F && strafe == 0.0F) {
			forward = 1.0F;
		}
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double len = Math.sqrt(forward * forward + strafe * strafe);
		double x = (strafe * Mth.cos(yaw) - forward * Mth.sin(yaw)) / len, z = (forward * Mth.cos(yaw) + strafe * Mth.sin(yaw)) / len;
		double push = com.jaymospowers.water.Dives.AIR_DIVE_PUSH;
		player.setDeltaMovement(x * push, Math.max(player.getDeltaMovement().y, com.jaymospowers.water.Dives.AIR_DIVE_LIFT), z * push);
		com.jaymospowers.client.water.DivePose.localDive = player.level().getGameTime();
		ClientPlayNetworking.send(new com.jaymospowers.water.Dives.DiveKey());
		sinceDodge = 0;
		return true;
	}

	/** The Dodge key over deep water: off the edge head first with no hop, or the fall turned into a dive (Dives). */
	private static boolean dive(LocalPlayer player) {
		int from = com.jaymospowers.water.Dives.diveFrom(player);
		if (from == com.jaymospowers.water.Dives.NO_DIVE || !ClientPlayNetworking.canSend(com.jaymospowers.water.Dives.DiveKey.TYPE)) {
			return false;
		}
		if (from == com.jaymospowers.water.Dives.EDGE) {
			Vec3 out = Vec3.directionFromRotation(0.0F, player.getYRot());
			player.setDeltaMovement(out.x * com.jaymospowers.water.Dives.LAUNCH_OUT, com.jaymospowers.water.Dives.LAUNCH_UP,
				out.z * com.jaymospowers.water.Dives.LAUNCH_OUT);
		}
		com.jaymospowers.client.water.DivePose.localDive = player.level().getGameTime();
		ClientPlayNetworking.send(new com.jaymospowers.water.Dives.DiveKey());
		return true;
	}

	/**
	 * The key held {@link Dodge#HOLD_TICKS} into our own dash: it goes on into the long roll from here, the way the dash
	 * went (the server's told, so everyone draws it). Held into a landing roll: it goes long. A long roll carries on along
	 * the ground, slowing; still held as it ends, we go on down into prone, which {@code ProneTransition} draws as a
	 * gradual crouch and lowering onto the hands. Let go before then and we come up on our feet.
	 */
	private static void tickHold(LocalPlayer player, boolean down) {
		long now = player.level().getGameTime();
		if (holdPending) {
			boolean dash = dashStart != Long.MIN_VALUE;
			long held = now - (dash ? dashStart : rollStart);
			if (held >= Dodge.HOLD_TICKS) {
				holdPending = false;
				dashStart = Long.MIN_VALUE;
				if (down && dash && player.onGround() && !player.isInWater()) {
					rollStart = now;
					rollFlip = false;
					rollLong = true;
					proneAtEnd = true;
					Dodge.localRollTicks = Dodge.LONG_ROLL_TICKS;
					// the body is the low one for the roll (Dodge#rollingLow), at once
					Dodge.localRollId = player.getId();
					Dodge.localRollStart = now;
					cooldown = Math.max(cooldown, Dodge.LONG_ROLL_TICKS);
					sendExtend();
				} else if (down && !dash && rollStart != Long.MIN_VALUE && !rollFlip && held < Dodge.LONG_ROLL_TICKS) {
					rollLong = true;
					proneAtEnd = true;
					Dodge.localRollTicks = Dodge.LONG_ROLL_TICKS;
					cooldown = Math.max(cooldown, (int)(Dodge.LONG_ROLL_TICKS - held));
					sendExtend();
				}
			}
		}
		if (rollStart == Long.MIN_VALUE || rollFlip || !rollLong) {
			return;
		}
		long elapsed = now - rollStart;
		if (elapsed < Dodge.LONG_ROLL_TICKS) {
			if (player.onGround() && !player.isInWater()) {
				// carried on the way of the roll, easing off toward its end (never slowed below what the hop still gives)
				float share = (elapsed - Dodge.HOLD_TICKS) / (float)(Dodge.LONG_ROLL_TICKS - Dodge.HOLD_TICKS);
				double speed = Mth.lerp(Mth.clamp(share, 0.0F, 1.0F), Dodge.LONG_SPEED_START, Dodge.LONG_SPEED_END);
				Vec3 motion = player.getDeltaMovement();
				double along = motion.x * rollX + motion.z * rollZ;
				if (along < speed) {
					player.setDeltaMovement(motion.x + rollX * (speed - along), motion.y, motion.z + rollZ * (speed - along));
				}
			}
		} else if (proneAtEnd) {
			proneAtEnd = false;
			if (down && player.onGround() && !com.jaymospowers.client.CrawlTaps.prone()) {
				com.jaymospowers.client.CrawlTaps.goProne(player, false);
			}
		}
	}

	private static void sendExtend() {
		if (ClientPlayNetworking.canSend(Dodge.Extend.TYPE)) {
			ClientPlayNetworking.send(new Dodge.Extend());
		}
	}

	/**
	 * (0.21.22) Andy: "the input should be jump plus alt key or roll bind in mid air". Dodge pressed in the air with a foe's
	 * raised shield close ahead vaults him ({@link com.jaymospowers.combat.ShieldVault}): pushed up and over at once, and
	 * the server told. True if it was a vault (one too big still takes the press, and the server says so).
	 */
	private static boolean vault(LocalPlayer player) {
		if (!com.jaymospowers.combat.ShieldVault.canVault(player)) {
			return false;
		}
		net.minecraft.world.entity.LivingEntity foe = com.jaymospowers.combat.ShieldVault.target(player, 0.0);
		if (foe == null) {
			return false;
		}
		if (!com.jaymospowers.core.TreeLocks.check(player, com.jaymospowers.core.TreeLocks.SHIELD_VAULT)) {
			// (Andy's skill-tree locks) learned: Shield Vault, on Strength's Combat Arts (the press goes to a plain dodge)
			return false;
		}
		if (!com.jaymospowers.combat.ShieldVault.tooBig(player, foe)) {
			Vec3 push = com.jaymospowers.combat.ShieldVault.push(player, foe);
			player.setDeltaMovement(push);
			player.setSprinting(false);
			landingRoll = 0;
		}
		ClientPlayNetworking.send(new com.jaymospowers.combat.ShieldVault.Ask(foe.getId()));
		return true;
	}

	/**
	 * Andy: "its tied to weapon forms but activated by dodging in midair, after a jump". Dodge pressed in the air off a
	 * jump, a weapon in hand and Weapon Forms learnt ({@link Dodge#canFlip}): a somersault the way the movement keys point
	 * (forward with none held), with a little push (and a lift for those {@link Dodge#flipLift} names), for {@link Dodge#FLIP_COST} feathers. It evades like a dodge.
	 */
	private static boolean tryFlip(LocalPlayer player) {
		if (cooldown > 0 || player.isSpectator() || !Dodge.canFlip(player) || FreerunClient.active() || com.jaymospowers.boxing.Boxing.boxing(player)
			|| !Feathers.canSpend(player, Dodge.FLIP_COST)) {
			return false;
		}
		Input input = player.input.keyPresses;
		float forward = (input.forward() ? 1.0F : 0.0F) - (input.backward() ? 1.0F : 0.0F);
		float strafe = (input.left() ? 1.0F : 0.0F) - (input.right() ? 1.0F : 0.0F);
		if (forward == 0.0F && strafe == 0.0F) {
			forward = 1.0F;
		}
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
		Vec3 dir = new Vec3(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin).normalize();
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(dir.x * Dodge.FLIP_SPEED + motion.x * Dodge.FLIP_KEEP, Dodge.flipLift(player) > 0.0 ? Math.max(motion.y, 0.0) * 0.5 + Dodge.flipLift(player) : motion.y,
			dir.z * Dodge.FLIP_SPEED + motion.z * Dodge.FLIP_KEEP);
		cooldown = Dodge.COOLDOWN;
		sinceDodge = 0;
		rollStart = player.level().getGameTime();
		rollX = (float)dir.x;
		rollZ = (float)dir.z;
		rollFlip = true;
		rollLong = false;
		holdPending = false;
		dashStart = Long.MIN_VALUE;
		proneAtEnd = false;
		if (ClientPlayNetworking.canSend(Dodge.Flip.TYPE)) {
			ClientPlayNetworking.send(new Dodge.Flip((float)dir.x, (float)dir.z));
		}
		return true;
	}

	/** {@code landing}: a landing roll, which goes forward when no movement key is held. */
	private static void tryDodge(LocalPlayer player, boolean landing) {
		if (cooldown > 0 || player.isSpectator() || !Dodge.canDodge(player) || FreerunClient.active()) {
			return;
		}
		if (com.jaymospowers.boxing.Boxing.boxing(player)) {
			// boxing: the style's own evade (a slip, a pull, a shoulder roll) in place of the roll
			if (com.jaymospowers.client.boxing.BoxingClient.evade(player)) {
				cooldown = com.jaymospowers.boxing.BoxingEvades.COOLDOWN;
				sinceDodge = 0;
			}
			return;
		}
		if (!landing && com.jaymospowers.client.freerun.moves.AcrobaticsClient.backflip(player)) {
			// Back held alone, Backflip learnt: the standing backflip in place of the back roll (Acrobatics); a dodge all the same
			cooldown = Dodge.COOLDOWN;
			sinceDodge = 0;
			return;
		}
		if (com.jaymospowers.client.freerun.moves.RunMovesClient.safetyTap(player, landing)) {
			// (ParCool track B) at touchdown with no direction held: the safety tap, not a roll
			cooldown = Dodge.COOLDOWN;
			return;
		}
		if (!Feathers.canSpend(player, Dodge.COST)) {
			return;
		}
		Input input = player.input.keyPresses;
		float forward = (input.forward() ? 1.0F : 0.0F) - (input.backward() ? 1.0F : 0.0F);
		float strafe = (input.left() ? 1.0F : 0.0F) - (input.right() ? 1.0F : 0.0F);
		boolean locked = com.jaymospowers.client.LockOnClient.locked() != null;
		if (!locked && forward < 0.0F) {
			// a roll goes back only while locked on to a foe: otherwise Back is dropped (Back alone doesn't roll at all)
			forward = 0.0F;
			if (strafe == 0.0F && !landing) {
				return;
			}
		}
		if (forward == 0.0F && strafe == 0.0F) {
			// nothing held: the back hop when locked on, a roll forward otherwise (and always on landing)
			forward = landing || !locked ? 1.0F : -1.0F;
		}
		// the movement keys turned to the world by the player's facing (as vanilla's own input is)
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
		Vec3 dir = new Vec3(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin).normalize();
		Vec3 motion = player.getDeltaMovement();
		long now = player.level().getGameTime();
		player.setSprinting(false);
		cooldown = Dodge.COOLDOWN;
		sinceDodge = 0;
		rollX = (float)dir.x;
		rollZ = (float)dir.z;
		rollFlip = false;
		rollLong = false;
		holdPending = true;
		proneAtEnd = false;
		if (landing) {
			// the landing roll stays a roll (it takes the fall)
			player.setDeltaMovement(dir.x * Dodge.SPEED + motion.x * 0.2, Dodge.UP, dir.z * Dodge.SPEED + motion.z * 0.2);
			rollStart = now;
			dashStart = Long.MIN_VALUE;
			Dodge.localRollTicks = Dodge.ROLL_TICKS;
			// the body is the low one for the roll (Dodge#rollingLow), at once
			Dodge.localRollId = player.getId();
			Dodge.localRollStart = now;
		} else {
			// a tap is the upright dash; held, it goes on into the roll (tickHold)
			player.setDeltaMovement(dir.x * Dodge.DASH_SPEED + motion.x * 0.2, Dodge.DASH_UP, dir.z * Dodge.DASH_SPEED + motion.z * 0.2);
			rollStart = Long.MIN_VALUE;
			dashStart = now;
			dashDrawStart = now;
		}
		if (ClientPlayNetworking.canSend(Dodge.Start.TYPE)) {
			ClientPlayNetworking.send(new Dodge.Start((float)dir.x, (float)dir.z, !landing));
		}
	}
}
