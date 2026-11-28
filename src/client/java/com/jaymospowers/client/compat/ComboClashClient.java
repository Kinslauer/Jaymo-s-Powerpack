package com.jaymospowers.client.compat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.Lash;
import com.jaymospowers.boxing.Boxing;
import com.jaymospowers.client.LockOnClient;
import com.jaymospowers.client.combat.GuardKeyClient;
import com.jaymospowers.client.dino.DinoClient;
import com.jaymospowers.client.finisher.FinisherClient;
import com.jaymospowers.client.power.chrono.ChronoFlowClient;
import com.jaymospowers.client.power.chrono.ChronoFlurryClient;
import com.jaymospowers.client.power.web.WebSymbioteClient;
import com.jaymospowers.client.power.zfighter.ApeSmashClient;
import com.jaymospowers.client.power.zfighter.RaceHoldClient;
import com.jaymospowers.client.power.zfighter.ZFighterPowerClient;
import com.jaymospowers.combat.Guard;
import com.jaymospowers.compat.ComboClashCompat;
import com.jaymospowers.core.Ledge;
import com.jaymospowers.power.chrono.ChronoFlow;
import com.jaymospowers.power.gravity.GravityPower;
import com.jaymospowers.power.wind.WindStaffSurge;
import com.jaymospowers.power.zfighter.ZFighterGrab;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Our side of Combo Clash (shipped as its unmodified jar, "Combo Clash" by HanHoYoung; see {@link ComboClashCompat}).
 * <p>
 * Andy (2026-11-28): "the "combat mode" should be standard, and what chrono drops into when he uses time flow". Its
 * combat mode is on for everyone who can fight, from the moment they join; its own key (the grave key, set by
 * {@code KeyConflicts}) still turns it off and on again for the rest of the session. Chrono's Time Flow puts it on
 * whatever that key said, and when the flow ends it goes back to the player's choice. Anyone who can't fight just now
 * ({@link ComboClashCompat#cantFight}: downed, carried, fainting, tied hand and foot, crippled) is out of it until they can.
 * It's set straight on its own flag (Combo Clash tells its server itself), so the camera is left as the player has it.
 * <p>
 * While it's on its 4-hit strings, dash and block are the fight, and the rest of ours steps around it:
 * <ul>
 * <li>The attack key is still ours (and vanilla's) where its string has nothing to do ({@link #yieldsAttack}, asked by
 * {@code ClientCombatMixin} both for the click and for the key held): in the air (the air combo's juggle, slam and enemy
 * step are the air game; its server keeps every blow of its string on the ground, {@code CombatManagerMixin}), digging a
 * block with no fight about, sneaking (a
 * takedown, a Quiet Word), at someone downed or helpless (the finishers), a flail or whip, boxing, an item in use or a
 * guard up, riding a dinosaur, a gravity grip, a ledge, the symbiote's hold, and the powers' own held moves (a Z Fighter's
 * grab, ape smash, race hold and hold combo, Chrono's overclocked flurry and Time Flow's lunges, the Staff Surge). Our own
 * attack callbacks also run first ({@link #BEFORE_CLASH}).</li>
 * <li>Q drops what's in hand as ever (Combo Clash's dash is on Left Alt here, so it no longer needs Q).</li>
 * <li>F is its block, unless one of the powers has it for a guard or counter ({@link GuardKeyClient#powerOwns}).</li>
 * <li>Better Combat's swings and our region swings: Better Combat sees no weapon in the hand ({@code ToolSwingGateMixin}).</li>
 * <li>Alt is its dash, cut short on the server ({@link ComboClashCompat}); held, it goes on into our long roll, crouch
 * and prone ({@code DodgeClient}).</li>
 * <li>Our arm and body poses give way while one of its motions is playing on that player ({@link #moving}).</li>
 * </ul>
 * Everything is reached by reflection or by {@code @Pseudo} mixins, so nothing breaks when Combo Clash isn't installed.
 */
public final class ComboClashClient {
	/** A phase of the client's tick and attack events that runs before Combo Clash's (which is in the default phase). */
	public static final Identifier BEFORE_CLASH = JaymosPowers.id("before_combo_clash");

	static {
		ClientTickEvents.START_CLIENT_TICK.addPhaseOrdering(BEFORE_CLASH, Event.DEFAULT_PHASE);
		ClientPreAttackCallback.EVENT.addPhaseOrdering(BEFORE_CLASH, Event.DEFAULT_PHASE);
	}

	/**
	 * Off the ground this many ticks (or flying), the attack key is vanilla's and our air combo's (Andy, 2026-11-28: "i
	 * like everything about except the air combos ... along with or enemy step for air combat"); a hop over a step stays its.
	 */
	private static final int AIR_TICKS = 3;
	private static int airTicks;

	/** How near a foe makes a click at a block the string's rather than a dig (blocks). */
	private static final double FIGHT_NEAR = 6.0;

	private static Field combatMode;
	private static KeyMapping toggle;
	private static Field clickCount;
	private static Method playing;
	/** Combat mode as we left it last tick, whether the player turned it off with its key, and whether they pressed it this tick. */
	private static boolean seen, playerOff, togglePressed;
	/** {@link #yieldsAttack}, worked out once a tick. */
	private static long yieldTick = Long.MIN_VALUE;
	private static boolean yieldCached;

	private ComboClashClient() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded(ComboClashCompat.MOD_ID)) {
			return;
		}
		try {
			Class<?> combat = Class.forName("com.comboclash.client.ClientCombat");
			combatMode = combat.getField("combatMode");
			toggle = (KeyMapping) combat.getField("TOGGLE").get(null);
			clickCount = KeyMapping.class.getDeclaredField("clickCount");
			clickCount.setAccessible(true);
			playing = Class.forName("com.comboclash.client.CombatAnim").getMethod("playing", AbstractClientPlayer.class);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't reach Combo Clash's client (a newer Combo Clash?); our combat won't step aside for it", e);
			combatMode = null;
			playing = null;
			return;
		}
		ClientTickEvents.START_CLIENT_TICK.register(BEFORE_CLASH, minecraft -> {
			// its key's press is still waiting for its own tick: a press now is the player's choice
			try {
				togglePressed = clickCount.getInt(toggle) > 0;
			} catch (ReflectiveOperationException | RuntimeException e) {
				togglePressed = false;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ComboClashClient::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> {
			seen = false;
			playerOff = false;
		});
	}

	/** Whether Combo Clash's combat mode is on for us. */
	public static boolean active() {
		if (combatMode == null) {
			return false;
		}
		try {
			return combatMode.getBoolean(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Whether one of Combo Clash's motions is playing on the player drawn as {@code state}. */
	public static boolean moving(AvatarRenderState state) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level != null && minecraft.level.getEntity(state.id) instanceof AbstractClientPlayer player && motion(player) != null;
	}

	/** The Combo Clash motion playing on {@code player} ("dash_front", "m1_2" and so on), or null for none. */
	public static String motion(AbstractClientPlayer player) {
		if (playing == null) {
			return null;
		}
		try {
			Object name = playing.invoke(null, player);
			return name == null ? null : name.toString();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** Whether Combo Clash's block (on F) stands aside for a power's own guard or counter just now. */
	public static boolean blockYields() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && GuardKeyClient.powerOwns(player);
	}

	/**
	 * Whether the attack key is ours (or vanilla's) just now rather than Combo Clash's string: its click goes on to vanilla
	 * and our own callbacks, and holding it sends no string. See the class note for the list.
	 */
	public static boolean yieldsAttack() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return false;
		}
		long now = minecraft.level.getGameTime();
		if (now != yieldTick) {
			yieldTick = now;
			yieldCached = yields(minecraft, player);
		}
		return yieldCached;
	}

	private static boolean yields(Minecraft minecraft, LocalPlayer player) {
		ItemStack main = player.getMainHandItem();
		return player.isShiftKeyDown() || airTicks >= AIR_TICKS || player.getAbilities().flying || digging(minecraft, player, main) || FinisherClient.aimingAtHelpless(minecraft)
			|| Lash.isFlail(main) || Lash.isWhip(main) || Boxing.boxing(player) || player.isUsingItem() || Guard.raised(player)
			|| DinoClient.riddenDino() != null || player.getAttached(GravityPower.GRIP) != null || Ledge.onLedge(player)
			|| WebSymbioteClient.inputLocked()
			|| ZFighterGrab.holding(player) || ApeSmashClient.holdsAttack(minecraft) || RaceHoldClient.holding() || ZFighterPowerClient.holdCombo()
			|| ChronoFlurryClient.flurrying() || ChronoFlowClient.ownsAttack(player) || WindStaffSurge.eligible(player);
	}

	/** At a block: a pick, shovel or hoe always digs it; anything else does when there's no fight about. */
	private static boolean digging(Minecraft minecraft, LocalPlayer player, ItemStack main) {
		if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		if (main.is(ItemTags.PICKAXES) || main.is(ItemTags.SHOVELS) || main.is(ItemTags.HOES)) {
			return true;
		}
		if (LockOnClient.locked() != null) {
			return false;
		}
		return player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FIGHT_NEAR),
			mob -> mob.isAlive() && (mob instanceof Enemy || mob.isAggressive())).isEmpty();
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || combatMode == null) {
			return;
		}
		airTicks = player.onGround() || player.isInWater() || player.isPassenger() || player.onClimbable() ? 0 : airTicks + 1;
		boolean mode = active();
		if (mode != seen && togglePressed) {
			// its own key: the player's choice for the rest of the session
			playerOff = !mode;
		}
		boolean fit = !player.isSpectator() && !ComboClashCompat.cantFight(player);
		boolean want = fit && (!playerOff || ChronoFlow.on(player));
		if (want != mode) {
			try {
				combatMode.setBoolean(null, want);
			} catch (ReflectiveOperationException | RuntimeException e) {
				JaymosPowers.LOGGER.warn("Couldn't set Combo Clash's combat mode", e);
				combatMode = null;
				return;
			}
		}
		seen = want;
	}
}
