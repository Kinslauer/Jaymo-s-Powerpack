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
	/** How long our own last roll takes (ticks) and its pace along the ground (blocks/tick), both slower under heavy armour. */
	static int rollTicks = Dodge.ROLL_TICKS;
	static double rollPace;
	/** Whether our own last roll was the flip in the air ({@link Dodge#canFlip}). */
	static boolean rollFlip;
	/** Leaving the ground faster than this upward (blocks/tick) is a jump, not a step off an edge. */
	static final double JUMP_RISE = 0.1;
	/** We were on the ground last tick; we're in the air off a jump; this jump's flip is spent. */
	private static boolean wasOnGround = true, jumped, flipped;

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
		if (player == null || minecraft.gui.screen() != null) {
			landingRoll = 0;
			return;
		}
		boolean ground = player.onGround();
		if (ground) {
			jumped = false;
			flipped = false;
		} else if (wasOnGround && player.getDeltaMovement().y > JUMP_RISE) {
			jumped = true;
		}
		wasOnGround = ground;
		carry(player);
		boolean wanted = false;
		KeyMapping key = dodgeKey;
		while (key != null && key.consumeClick()) {
			// (0.20.74) Sneak + Alt is the zoom (SneakZoom), not a dodge
			wanted = !minecraft.options.keyShift.isDown();
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
	}

	/**
	 * The roll carries us along the ground for its whole turn rather than a hop's one push: easing in as we go down, the
	 * full pace through the middle, and easing out as we come up. A roll off an edge stops pushing and falls.
	 */
	private static void carry(LocalPlayer player) {
		if (!rolling(player, 1) || !player.onGround()) {
			return;
		}
		// (set at the end of a tick, so this is the push for the next one)
		float t = (player.level().getGameTime() + 1 - rollStart) / (float)rollTicks;
		double pace = rollPace * Math.min(1.0F, Math.min(t / CARRY_IN, (1.0F - t) / CARRY_OUT));
		player.setDeltaMovement(rollX * pace, player.getDeltaMovement().y, rollZ * pace);
	}

	/** Our own roll on the ground is under way {@code ahead} ticks from now. */
	private static boolean rolling(LocalPlayer player, int ahead) {
		long age = player.level().getGameTime() + ahead - rollStart;
		return !rollFlip && rollStart != Long.MIN_VALUE && age >= 0 && age < rollTicks;
	}

	/** While our own roll carries us, the walking keys add nothing to it ({@code StanceInputMixin}). */
	public static boolean carrying() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && rolling(player, 0);
	}

	/** Of the roll, the share spent easing up to its pace at the start, and easing off at the end. */
	static final float CARRY_IN = 0.15F, CARRY_OUT = 0.25F;

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
		if (forward == 0.0F && strafe == 0.0F) {
			forward = landing ? 1.0F : -1.0F;
		}
		// the movement keys turned to the world by the player's facing (as vanilla's own input is)
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
		Vec3 dir = new Vec3(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin).normalize();
		// no hop: the roll stays on the ground and carries us along it (#carry)
		rollTicks = Dodge.rollTicks(player);
		rollPace = Dodge.rollPace(player);
		player.setDeltaMovement(dir.x * rollPace * CARRY_IN, player.getDeltaMovement().y, dir.z * rollPace * CARRY_IN);
		player.setSprinting(false);
		// (no new roll until this one's up)
		cooldown = Math.max(Dodge.COOLDOWN, rollTicks);
		sinceDodge = 0;
		rollStart = player.level().getGameTime();
		rollX = (float)dir.x;
		rollZ = (float)dir.z;
		rollFlip = false;
		// the body is the low one for the roll (Dodge#rollingLow), at once
		Dodge.localRollId = player.getId();
		Dodge.localRollStart = player.level().getGameTime();
		Dodge.localRollTicks = rollTicks;
		if (ClientPlayNetworking.canSend(Dodge.Start.TYPE)) {
			ClientPlayNetworking.send(new Dodge.Start((float)dir.x, (float)dir.z));
		}
	}
}
