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
		if (!com.jaymospowers.combat.ShieldVault.tooBig(player, foe)) {
			Vec3 push = com.jaymospowers.combat.ShieldVault.push(player, foe);
			player.setDeltaMovement(push);
			player.setSprinting(false);
			landingRoll = 0;
		}
		ClientPlayNetworking.send(new com.jaymospowers.combat.ShieldVault.Ask(foe.getId()));
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
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(dir.x * Dodge.SPEED + motion.x * 0.2, Dodge.UP, dir.z * Dodge.SPEED + motion.z * 0.2);
		player.setSprinting(false);
		cooldown = Dodge.COOLDOWN;
		sinceDodge = 0;
		rollStart = player.level().getGameTime();
		rollX = (float)dir.x;
		rollZ = (float)dir.z;
		if (ClientPlayNetworking.canSend(Dodge.Start.TYPE)) {
			ClientPlayNetworking.send(new Dodge.Start((float)dir.x, (float)dir.z));
		}
	}
}
