package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Xenoform: the Xeno-Band, a wrist band out of a fallen star ({@link FallingStars}) that turns you into one of the aliens
 * it holds ({@link XenoForms}), each with its own body and four moves. Everything is decided here, on the server.
 * <ul>
 * <li>Z held: the wrist comes up; scrolling while it's held turns the dial ({@link #dial}). Letting Z go slams the band
 * and you become the dialled alien. As an alien, Z held {@link #HOLD_TICKS} turns you back early.</li>
 * <li>R G V B: the alien's moves ({@link XenoForm#onKey}). As a human they do nothing.</li>
 * <li>X held {@link #SCAN_TICKS} at a creature: scan it; if it carries an alien's DNA, the band unlocks that alien.</li>
 * <li>(0.18.92) The band's charge: a form spends it a point a tick, more when you're hit ({@link #HURT_DRAIN}) or a move
 * goes on cooldown ({@link #MOVE_DRAIN}); as yourself it refills over {@link #REFILL_TICKS}. Mastery grows it from
 * {@link #CHARGE_MIN} to {@link #CHARGE_MAX}. The band beeps through the last {@link #BEEP_TICKS}. When it's spent, when the
 * alien's health is gone, or when you hold Z and let go, you turn back at the health you had before; a knockout empties it
 * and locks the band for {@link #RECHARGE_KNOCKOUT}. A slam needs {@link #SLAM_MIN}.</li>
 * <li>Quick change: as an alien, hold Z and scroll, then let go: another alien for {@link #QUICK_SHARE} of the charge.</li>
 * <li>The band saves you: a killing blow as yourself slams it for you, every {@link #SAVE_COOLDOWN}.</li>
 * <li>Wild dial (C while Z is held as yourself): a random alien you know every {@link #WILD_MIN}-{@link #WILD_MAX}, no
 * turning back, moves count double toward mastery.</li>
 * <li>Jammed ({@link XenoJam}): turned back, and no slam until it wears off.</li>
 * <li>Fists only: while transformed your hands read as empty ({@code XenoformHandsMixin}), so items can't be used and
 * punches hit for the alien's strength.</li>
 * </ul>
 * Ultimate forms (C, once ascended) and the band's corruption glitches are stage 3 / stage 2 of {@code docs/xenoform.md}.
 */
public final class XenoformPower implements Power {
	public static final XenoformPower INSTANCE = new XenoformPower();
	public static final Identifier ID = JaymosPowers.id("xenoform");
	public static final int COLOR = 0x5CF07A;

	// ---- tuning (ticks) ----
	/** Z held this long slams the band. */
	public static final int HOLD_TICKS = 10;
	/** (0.18.92) The band's charge (ticks of form): its size unmastered and fully mastered (2 and 5 minutes). */
	public static final int CHARGE_MIN = 2400, CHARGE_MAX = 6000;
	/** An empty band refills in this long as yourself; a slam needs at least this much. */
	public static final int REFILL_TICKS = 1200, SLAM_MIN = 200;
	/** Charge lost per point of damage taken as an alien, and per move put on cooldown. */
	public static final int HURT_DRAIN = 10, MOVE_DRAIN = 20;
	/** After a knockout the band is empty and locked this long (no refill, no slam). */
	public static final int RECHARGE_KNOCKOUT = 1200;
	/** Quick change: its cost (share of the full charge) and the dizziness after (ticks). */
	public static final float QUICK_SHARE = 0.25F;
	public static final int QUICK_DIZZY = 20;
	/** The band saves you from a killing blow no more often than this; it leaves at least this much charge. */
	public static final int SAVE_COOLDOWN = 12000, SAVE_CHARGE = 600;
	/** Wild dial: the next random change comes after this many ticks, at random. */
	public static final int WILD_MIN = 20, WILD_MAX = 1200;
	/** The player tag that keeps the wild dial on (kept through relogs). */
	public static final String WILD_TAG = "jaymospowers_xeno_wild";
	/**
	 * (0.18.91) The band beeps and the screen flashes through the last 5 seconds of a form (Andy): once a second, then
	 * twice a second over the last {@link #BEEP_FAST_TICKS}.
	 */
	public static final int BEEP_TICKS = 100, BEEP_FAST_TICKS = 40;

	/** Whether the band beeps (and the wearer's screen flashes) with this many ticks of the form left. */
	public static boolean beepsAt(int ticksLeft) {
		return ticksLeft > 0 && ticksLeft <= BEEP_TICKS && ticksLeft % (ticksLeft <= BEEP_FAST_TICKS ? 10 : 20) == 0;
	}
	/** X held this long at a creature scans it, from this far (blocks). */
	public static final int SCAN_TICKS = 60;
	public static final double SCAN_REACH = 12.0;
	/** The HUD is sent this often while something ticks (sooner when something changes). */
	private static final int HUD_EVERY = 5;
	/** Hints about the band ("hold Z") repeat no sooner than this. */
	private static final int HINT_SPACING = 60;
	/** Each knockout corrupts the band this much (out of {@link com.jaymospowers.core.Fate#MAX_CORRUPTION}). */
	public static final int KNOCKOUT_CORRUPTION = 25;
	/** Corrupted: a slam gives the wrong alien this often, and the band changes you on its own about every this many ticks. */
	public static final float GLITCH_WRONG_CHANCE = 0.25F;
	public static final int GLITCH_SELF_TICKS = 7200;
	/** How often (ticks) a human checks whether the band is complete and the power ready to ascend. */
	public static final int ASCEND_CHECK_EVERY = 40;
	/** (0.16.24) An Ultimate's film (ticks): low and close, sweeping round the alien. */
	static final int ULTIMATE_FILM_TICKS = 36;
	/**
	 * (0.18.93, patch 2: Andy wants "real ultimate forms") The Ultimate is its own body on top of the base alien's: this
	 * much more health (share of the alien's, its own bar on top, healed in full as it comes on), more fist damage (share),
	 * bigger (scale added), and every move's cooldown times {@link #ULT_COOLDOWN}.
	 */
	public static final float ULT_HEALTH = 0.5F, ULT_DAMAGE = 0.5F, ULT_SCALE = 0.15F, ULT_COOLDOWN = 0.5F;

	private static final Identifier SCALE_ID = JaymosPowers.id("xenoform_scale");
	private static final Identifier HEALTH_ID = JaymosPowers.id("xenoform_health");
	private static final Identifier DAMAGE_ID = JaymosPowers.id("xenoform_damage");
	private static final Identifier SPEED_ID = JaymosPowers.id("xenoform_speed");
	private static final Identifier KNOCKBACK_ID = JaymosPowers.id("xenoform_knockback");
	private static final Identifier STEP_ID = JaymosPowers.id("xenoform_step");
	private static final Identifier ULT_HEALTH_ID = JaymosPowers.id("xenoform_ultimate_health");
	private static final Identifier ULT_DAMAGE_ID = JaymosPowers.id("xenoform_ultimate_damage");
	private static final Identifier ULT_SCALE_ID = JaymosPowers.id("xenoform_ultimate_scale");

	/** Server-side bookkeeping for one player (not saved: a relog turns them back first). */
	/** A projectile Standstill holds (kept by reference, so it is let go in whichever dimension it is in) and its caught state. */
	public record Held(net.minecraft.world.entity.projectile.Projectile projectile, double[] caught) {}

	public static final class Live {
		@Nullable XenoForm form;
		int ticksLeft, total;
		/** The health they had as a human, given back when they turn back. */
		float humanHealth;
		int recharge, rechargeTotal;
		/** (0.18.92) The band's charge as yourself (-1: not yet known, full). In a form it is {@link #ticksLeft}. */
		float charge = -1.0F;
		/** When the band last saved you from a killing blow (game time, 0: never); ticks to the wild dial's next change. */
		long lastSave;
		int wildNext;
		/** Quick change: the wheel turned while Z was held as an alien. */
		boolean quickDial;
		long modeDownAt = -1L;
		boolean slammed;
		boolean scanning;
		int scanTicks;
		@Nullable String scanForm;
		final long[] cooldownUntil = new long[XenoformState.Hud.SLOTS];
		final int[] cooldownLength = new int[XenoformState.Hud.SLOTS];
		public boolean hudDirty = true;
		int hudTick;
		long lastHint;
		// ---- scratch the forms use (see each form) ----
		/** Bulwark: hits left in the fist combo, and when the next lands; the creature held overhead (entity id, -1 none). */
		public int combo;
		public long comboNext;
		public int grabbed = -1;
		/** Bulwark: in a leap, and ticks since it started. */
		public boolean leaping;
		public int leapTicks;
		/** Pyre: ticks left of the magma trail; the jet is held. */
		public int trailTicks;
		public boolean jet;
		/** Velox: Velocity is switched on; the chain strike's targets and its clock. */
		public boolean velocity;
		public final List<Integer> chain = new ArrayList<>();
		public int chainTick;
		/** Velox: rapid punches left, whom they hit (entity id) and ticks to the next; ticks left of the cyclone. */
		public int punches, punchTick;
		public int punchTarget = -1;
		public int cyclone;
		/** Velox's Standstill: ticks left, and each projectile held still (entity id to its velocity when caught and whether it flew without gravity). */
		public int standstill;
		public final Map<Integer, Held> held = new HashMap<>();
		/** The Speed effect Standstill replaced (null: none), put back when it ends. */
		public net.minecraft.world.effect.MobEffectInstance savedSpeed;
		/** Prism: in the reflect stance; the wall's blocks and when it falls. */
		public boolean reflecting;
		public final List<net.minecraft.core.BlockPos> wall = new ArrayList<>();
		public long wallUntil;
		/** Wisp: shrouded; gliding; ticks left of the wail. */
		public boolean shroud, glide;
		public int wail;
		/** Gremlin: climbing; ticks since a spring (0: none). */
		public boolean climb;
		public int spring;
		/** Knell: sensing; ticks left of a burrow and where it comes up. Wyrm: the wings are held. */
		public boolean sense;
		public int burrow;
		public @Nullable Vec3 burrowTo;
		public boolean wings;
		// ---- stage 3: the Ultimate and the corruption ----
		/** In the Ultimate form (C, ascended) for the rest of this timer; the Ultimate's own move's cooldown. */
		public boolean ultimate;
		public long ultimateNext;
		/** The band is corrupted right now (a slam may give the wrong alien; see {@link XenoForm#corrupt}). Refreshed every tick. */
		public boolean corrupted;
		/** What the Ultimate summoned (entity ids): Swarm's gremlins. Dismissed when it ends. */
		public final List<Integer> minions = new ArrayList<>();
		/** Revenant: the creature possessed (entity id, -1 none). Sunheart: the beam is held. */
		public int possessed = -1;
		public boolean beam;
		/** Geode: the crystal placed and the creatures held in it, and when it shatters. The Tolling: tolls left. */
		public final List<net.minecraft.core.BlockPos> crystals = new ArrayList<>();
		public final List<Integer> frozen = new ArrayList<>();
		public long geodeUntil;
		public int tolls;
		/** This change has hurt a living thing (the voices' trial counts the ones that don't). */
		public boolean hurt;
		/** Nadir: the Beacon's chosen player, R held for this many ticks (0: not held), and when the last tap was. */
		public @Nullable UUID beaconTarget;
		public int beaconHeld;
		public long beaconTapAt;
		/** Duplex: its doubles (entity ids), and when Copper Storm's charge wears off. */
		public final List<Integer> doubles = new ArrayList<>();
		public long chargedUntil;
		/**
		 * (0.18.95) Rivet: the shell it's melded into (null: none); holding Brace, the water spray; Rebound on; mid Drop and
		 * the height it fell from; last tick's fall speed; the Chord's tick (0: not playing).
		 */
		public @Nullable String shell;
		public boolean brace, spray, rebound, anvilDrop;
		public double dropFrom, fallSpeed;
		public int song;
		/**
		 * (0.18.96, patch 4) Gorger: blocks eaten toward the blast; ticks left of the horn charge, whether it ran off a ledge
		 * (now a slam) and from what height; rolled up. Slipfin: ticks out of water. Polestar: the iron block held
		 * overhead (null: none); the field is held. Lurk: the creature it watches (entity id, -1 none); faded. Duplex:
		 * the double whose eyes you look through (entity id, -1 none).
		 */
		public int gorge, horn;
		public boolean hornSlam, rolled;
		public double hornFrom;
		public int dry;
		public net.minecraft.world.level.block.state.@Nullable BlockState lifted;
		public boolean field;
		public int watched = -1;
		public boolean faded;
		public int eyes = -1;
		/**
		 * (0.18.103, patch 5) Gyre: ticks left of the shell spin and who it has already bowled; the whirlwind's eye and its
		 * ticks left. Cortex: the bubble's last tick. Locust: ticks R has been held; mid a crash leap. Croaker: the creature
		 * in its gullet (entity id, -1 none).
		 */
		public int spin;
		public final java.util.Set<Integer> spun = new java.util.HashSet<>();
		public net.minecraft.world.phys.@Nullable Vec3 whirl;
		public int whirlTicks;
		public long bubbleUntil;
		public int leapCharge;
		public boolean crashing;
		public int swallowed = -1;
		/**
		 * (0.18.107, Scan B) Rime: ticks since it last sprinted while skating. Marrow: it has fallen apart and stood back up
		 * this change; the bone shield's last tick; lying in a heap until this tick. Swathe: ticks left of the sandstorm.
		 * Blight: the dark shield is up.
		 */
		public int skateIdle;
		public boolean breathWas;
		public boolean risen;
		public long shieldUntil, heapUntil;
		public int storm;
		public boolean darkShield;
		/** Marrow: the thrown rib's heading (its place is {@link #whirl}, its ticks {@link #spin}, who it hit {@link #spun}). */
		public net.minecraft.world.phys.@Nullable Vec3 ribDir;
		/**
		 * (0.18.109, Scan C) Howl, Prowl, Tusk, Talon: ticks left of a pounce, charge or dive; the creature held in the jaws or
		 * talons (and ticks left); the wild wolves answering the howl (and ticks left); ticks left of the scent; the stalked
		 * creature (and until when); Thick Hide until this tick.
		 */
		public int rush;
		public int latched = -1, latchTicks;
		public final java.util.List<Integer> pack = new java.util.ArrayList<>();
		public int packTicks;
		public int sniff;
		public int stalked = -1;
		public long stalkUntil, hideUntil;
		/**
		 * (0.18.111, Scan D) Murk: the creatures held in its tentacles (and ticks left); ticks left of the lure. Gnasher:
		 * ticks left of the frenzy and the death roll. Lumen: its lights (packed block positions, each with the tick it
		 * goes out); ticks left of the fetch. Keen: ticks left of the sonar; hanging from a ceiling.
		 */
		public final java.util.List<Integer> grips = new java.util.ArrayList<>();
		public int gripTicks, lure, frenzy, rolls, fetch, sonar;
		public final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap lights = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
		public boolean perched;
		/**
		 * (0.18.113, Scan E) Hive: ticks left of the scatter. Skitter: where its web line is fixed and the line's length;
		 * ticks left of its brood.
		 */
		public int scatter, brood;
		public net.minecraft.world.phys.@Nullable Vec3 anchor;
		public double rope;
		/**
		 * (0.18.117, Scan F) Ooze: squeezed small; blobs split off for the hurt taken. Fuse: ticks Self-Destruct has swelled ({@code swell});
		 * the ring blasts still to go off. Billow: ticks left of the downpour and of the fog.
		 */
		public boolean squeeze;
		public int splits, swell, blasts, rain, fog;
		/**
		 * (0.18.118, Scan G) Towerback: risen to monolith size, and when it may change size again; the moveDist at its last
		 * step, which foot came down last, its shockwaves running out; its grab (since when, -1: none; whom it went for;
		 * holding them; where it snatched them from and where it slams them).
		 */
		public boolean towering, leftFoot, grabHolding;
		public long sizeNext, grabStart = -1L;
		public float strideAt;
		public int grabPrey = -1;
		public net.minecraft.world.phys.@Nullable Vec3 grabFrom, grabTo;
		public final java.util.List<com.jaymospowers.power.xenoform.forms.Towerback.Wave> waves = new java.util.ArrayList<>();
		/** Nadir: the Rift's, Graviton's and Command's scratch (G V B). */
		public final com.jaymospowers.power.xenoform.nadir.NadirState nadir = new com.jaymospowers.power.xenoform.nadir.NadirState();

		void clearForm() {
			form = null;
			ticksLeft = total = 0;
			java.util.Arrays.fill(cooldownUntil, 0L);
			java.util.Arrays.fill(cooldownLength, 0);
			combo = 0;
			grabbed = -1;
			leaping = false;
			leapTicks = 0;
			trailTicks = 0;
			jet = false;
			velocity = false;
			chain.clear();
			chainTick = 0;
			punches = punchTick = 0;
			punchTarget = -1;
			cyclone = 0;
			standstill = 0;
			held.clear();
			savedSpeed = null;
			reflecting = false;
			wall.clear();
			shroud = glide = false;
			wail = 0;
			climb = false;
			spring = 0;
			sense = false;
			burrow = 0;
			burrowTo = null;
			wings = false;
			ultimate = false;
			ultimateNext = 0L;
			minions.clear();
			possessed = -1;
			beam = false;
			crystals.clear();
			frozen.clear();
			geodeUntil = 0L;
			tolls = 0;
			hurt = false;
			beaconTarget = null;
			beaconHeld = 0;
			doubles.clear();
			chargedUntil = 0L;
			nadir.clear();
			shell = null;
			brace = spray = rebound = anvilDrop = false;
			dropFrom = fallSpeed = 0.0;
			song = 0;
			skateIdle = storm = 0;
			risen = darkShield = breathWas = false;
			shieldUntil = heapUntil = 0L;
			ribDir = null;
			rush = latchTicks = packTicks = sniff = 0;
			latched = stalked = -1;
			pack.clear();
			stalkUntil = hideUntil = 0L;
			grips.clear();
			gripTicks = lure = frenzy = rolls = fetch = sonar = 0;
			lights.clear();
			perched = false;
			scatter = brood = 0;
			anchor = null;
			rope = 0.0;
			squeeze = false;
			splits = swell = blasts = rain = fog = 0;
			towering = leftFoot = grabHolding = false;
			sizeNext = 0L;
			grabStart = -1L;
			strideAt = 0.0F;
			grabPrey = -1;
			grabFrom = grabTo = null;
			waves.clear();
			spin = whirlTicks = leapCharge = 0;
			spun.clear();
			whirl = null;
			bubbleUntil = 0L;
			crashing = false;
			swallowed = -1;
			gorge = horn = 0;
			hornSlam = rolled = false;
			hornFrom = 0.0;
			dry = 0;
			lifted = null;
			field = false;
			watched = -1;
			faded = false;
			eyes = -1;
			hudDirty = true;
		}
	}

	private static final Map<UUID, Live> LIVES = new HashMap<>();

	private XenoformPower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}. */
	public static void init() {
		Powers.register(INSTANCE);
		XenoNet.register();
		com.jaymospowers.power.xenoform.nadir.NadirNet.register();
		XenoDouble.init();
		// (0.18.107, Scan B) Rime has Cryokinesis's moves lent
		com.jaymospowers.power.cryo.CryoLent.lender = p -> XenoformState.form(p) == XenoForms.RIME;
		XenoStalker.init();
		XenoBandItem.init();
		FallenStarBlock.init();
		FallingStars.init();
		XenoBands.init();
		XenoJam.register();
		// the alien's health is a second life bar: at zero you're knocked back into your own body instead of dying
		// (0.18.92) as yourself, the band may slam itself to save you (default phase: before Downed takes the blow)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !has(player)) {
				return true;
			}
			if (live(player).form == null) {
				return !save(player, source);
			}
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				// the void, /kill: the form can't save you, but it lets go so you respawn whole
				revert(player, false, false);
				return true;
			}
			revert(player, true, true);
			return false;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// (0.18.103) Dud can't hurt anything
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && has(attacker) && live(attacker).form == XenoForms.DUD) {
				return false;
			}
			if (!(entity instanceof ServerPlayer player) || !has(player)) {
				return true;
			}
			Live live = live(player);
			return live.form == null || live.form.allowDamage(player, live, source, amount);
		});
		// (0.18.92) hits drain the charge
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && has(player) && damageTaken > 0.0F) {
				Live live = live(player);
				if (live.form != null) {
					drain(player, live, Mth.ceil(damageTaken * HURT_DRAIN));
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer player && player != entity && has(player)) {
				Live live = live(player);
				if (live.form != null) {
					live.hurt = true;
					live.form.onDealtDamage(player, live, entity);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.player;
			if (has(player) && live(player).form != null) {
				revert(player, false, false);
			}
			LIVES.remove(player.getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(FallingStars::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LIVES.clear();
			FallingStars.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> XenoformDebugCommand.register(dispatcher));
		XenoVoices.register();
	}

	public static boolean has(Player player) {
		return Powers.has(player, INSTANCE);
	}

	static Live live(ServerPlayer player) {
		return LIVES.computeIfAbsent(player.getUUID(), id -> new Live());
	}

	/** No affinity and no random awakening: the band is the only way in. */
	@Override
	public boolean fated() {
		return false;
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return COLOR;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(XenoBandItem.BAND);
	}

	/** The badge names the alien you are. */
	@Override
	public @Nullable String statusSuffix(Player player) {
		XenoForm form = XenoformState.form(player);
		String shell = player.getAttached(XenoformState.SHELL);
		// (0.18.95) Rivet melded into a shell: that shell's keys
		return form == null ? null : shell != null ? "." + form.id + "_" + shell : "." + form.id;
	}

	/** The moves belong to the alien, not to mastery: every key works from the first slam. Mastery stretches the timer. */
	@Override
	public boolean usableEarly(PowerKey key) {
		return true;
	}

	@Override
	public void onGained(ServerPlayer player) {
		XenoformState.Band band = XenoformState.band(player);
		if (band.unlocked().isEmpty()) {
			// a fresh band holds one of the first aliens, at random
			List<XenoForm> starters = XenoForms.all().subList(0, Math.min(XenoForms.STARTERS, XenoForms.all().size()));
			XenoForm first = starters.get(player.getRandom().nextInt(starters.size()));
			player.setAttached(XenoformState.BAND, XenoformState.Band.NONE.plus(first.id));
		}
		XenoBands.worn(player, false);
		live(player).hudDirty = true;
	}

	@Override
	public void onLost(ServerPlayer player) {
		Live live = live(player);
		if (live.form != null) {
			revert(player, false, false);
		}
		LIVES.remove(player.getUUID());
		XenoBands.unworn(player);
		player.removeAttached(XenoformState.HUD);
		player.removeAttached(XenoformState.FORM);
		player.removeAttached(XenoformState.ENDING);
		player.removeAttached(XenoformState.ULTIMATE);
		player.removeAttached(XenoformState.SHELL);
		player.removeAttached(XenoformState.EYES);
		player.removeAttached(XenoformState.FLIGHT);
		player.removeAttached(XenoformState.CEILING);
		player.removeAttached(XenoformState.GRAB);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player) {
		Live live = live(player);
		return live.form != null && live.form.allowsFloating(player, live);
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player) {
		Live live = live(player);
		return live.form != null && live.form.skipsMovementCheck(player, live);
	}

	@Override
	public void tick(ServerPlayer player) {
		Live live = live(player);
		if (!player.isAlive()) {
			return;
		}
		long now = player.level().getGameTime();
		if (live.form != null && XenoJam.jammed(player)) {
			revert(player, false, true);
		}
		if (live.form != null) {
			// in a form: the charge runs down
			live.ticksLeft--;
			if (live.ticksLeft <= BEEP_TICKS && !XenoformState.ending(player)) {
				player.setAttached(XenoformState.ENDING, true);
			}
			if (beepsAt(live.ticksLeft)) {
				float pitch = live.ticksLeft <= BEEP_FAST_TICKS ? 1.8F : 1.4F;
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.6F, pitch);
				player.level().sendParticles(player, ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFFFF4040), true, false,
					player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
			}
			if (live.ticksLeft <= 0) {
				revert(player, false, true);
			} else {
				live.corrupted = com.jaymospowers.core.Fate.isCorrupted(player);
				live.form.tick(player, live);
				if (live.corrupted && live.form != null) {
					live.form.corrupt(player, live);
				}
				if (live.form != null) {
					int flight = live.form.flight(player, live);
					if (flight != XenoformState.flight(player)) {
						if (flight == XenoformState.FLY_NONE) {
							player.removeAttached(XenoformState.FLIGHT);
						} else {
							player.setAttached(XenoformState.FLIGHT, flight);
						}
					}
				}
				if (live.ultimate && live.form != null) {
					live.form.tickUltimate(player, live);
				}
				// the wild dial: now and then another alien, whether you like it or not
				if (live.form != null && wild(player) && --live.wildNext <= 0) {
					live.wildNext = WILD_MIN + player.getRandom().nextInt(WILD_MAX - WILD_MIN + 1);
					XenoForm next = randomKnown(player, live.form);
					if (next != null) {
						swap(player, live, next, 0);
					}
				}
				// a grabbed creature that got away
				if (live.grabbed >= 0) {
					Entity held = player.level().getEntity(live.grabbed);
					if (held == null || !held.isAlive() || held.getVehicle() != player) {
						live.grabbed = -1;
					}
				}
			}
		} else {
			// as yourself the charge refills (not while a knockout has the band locked)
			int capacity = capacity(player);
			boolean wasReady = live.recharge <= 0 && charge(player, live) >= SLAM_MIN;
			if (live.recharge > 0) {
				live.recharge--;
			} else if (live.charge < capacity) {
				live.charge = Math.min(capacity, live.charge + capacity / (float)REFILL_TICKS);
			}
			if (!wasReady && live.recharge <= 0 && live.charge >= SLAM_MIN) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.recharged").withStyle(ChatFormatting.GREEN));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.5F);
				live.hudDirty = true;
			}
			// the wild dial: the band changes you as soon as it can
			if (wild(player) && live.recharge <= 0 && live.charge >= SLAM_MIN && !XenoJam.jammed(player) && --live.wildNext <= 0) {
				live.wildNext = WILD_MIN + player.getRandom().nextInt(WILD_MAX - WILD_MIN + 1);
				XenoForm next = randomKnown(player, null);
				if (next != null) {
					transform(player, next, false);
				}
			}
			tickScan(player, live);
			tickAscension(player);
			XenoVoices.tick(player);
			// corrupted: now and then the band changes you without being asked
			if (live.recharge <= 0 && !player.isCreative() && com.jaymospowers.core.Fate.isCorrupted(player)
				&& player.getRandom().nextInt(GLITCH_SELF_TICKS) == 0) {
				XenoForm any = glitch(player, null);
				if (any != null && transform(player, any, false)) {
					glitchFx(player, "message.jaymospowers.xenoform.glitch_self");
				}
			}
		}
		// the HUD: on change, and every so often while a clock runs
		// (as yourself: the charge and its capacity, and how long until a slam is possible)
		boolean ticking = live.form != null || live.recharge > 0 || live.scanTicks > 0 || live.charge < capacity(player);
		if (live.hudDirty || ticking && ++live.hudTick >= HUD_EVERY) {
			live.hudDirty = false;
			live.hudTick = 0;
			int formTicks = live.form != null ? live.ticksLeft : (int)charge(player, live);
			int formTotal = live.form != null ? live.total : capacity(player);
			int wait = live.form != null ? 0 : untilReady(player, live);
			int waitTotal = live.form != null ? 0 : Math.max(wait, live.recharge > 0 ? live.rechargeTotal + Mth.ceil(SLAM_MIN * REFILL_TICKS / (float)formTotal)
				: Mth.ceil(SLAM_MIN * REFILL_TICKS / (float)formTotal));
			XenoformState.Hud hud = new XenoformState.Hud(formTicks, formTotal, wait, waitTotal, live.cooldownUntil.clone(),
				live.cooldownLength.clone(), live.scanTicks, SCAN_TICKS, live.scanForm == null ? "" : live.scanForm);
			if (!hud.same(XenoformState.hud(player))) {
				player.setAttached(XenoformState.HUD, hud);
			}
		}
	}

	/** X held as a human: the creature under the crosshair is scanned for an alien's DNA. */
	private static void tickScan(ServerPlayer player, Live live) {
		if (!live.scanning) {
			if (live.scanTicks > 0) {
				live.scanTicks = Math.max(0, live.scanTicks - 3);
				live.hudDirty = live.scanTicks == 0;
				if (live.scanTicks == 0) {
					live.scanForm = null;
				}
			}
			return;
		}
		Entity target = scanTarget(player);
		XenoForm form = target == null ? null : XenoForms.scanOf(target);
		if (form == null || target == null) {
			if (live.scanTicks > 0) {
				live.scanTicks = Math.max(0, live.scanTicks - 3);
			}
			return;
		}
		if (!form.id.equals(live.scanForm)) {
			live.scanForm = form.id;
			live.scanTicks = 0;
		}
		ServerLevel level = player.level();
		Vec3 at = target.getBoundingBox().getCenter();
		if (XenoformState.band(player).has(form.id)) {
			if (live.scanTicks == 0) {
				hint(player, live, Component.translatable("message.jaymospowers.xenoform.known", form.name()));
			}
			live.scanTicks = 1;
			return;
		}
		live.scanTicks++;
		// a thin green beam of motes between you and it
		Vec3 eye = player.getEyePosition();
		Vec3 step = at.subtract(eye).scale(1.0 / 6.0);
		for (int i = 1; i <= 6; i++) {
			Vec3 p = eye.add(step.scale(i));
			level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | COLOR), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		if (live.scanTicks % 10 == 0) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.5F + live.scanTicks / 60.0F);
		}
		if (live.scanTicks >= SCAN_TICKS) {
			unlock(player, form);
			live.scanTicks = 0;
			live.scanForm = null;
			live.hudDirty = true;
		}
	}

	/** What the crosshair rests on within {@link #SCAN_REACH}: a creature, or a piece of the Ender Dragon (its body). */
	private static @Nullable Entity scanTarget(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(SCAN_REACH));
		net.minecraft.world.phys.AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(SCAN_REACH)).inflate(1.0);
		net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, box,
			e -> e != player && e.isAlive() && !e.isSpectator() && (e instanceof LivingEntity || e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragonPart), 0.3F);
		if (hit == null) {
			return null;
		}
		return hit.getEntity() instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part ? part.parentMob : hit.getEntity();
	}

	/** The band takes in a new alien (a scan, or a command). */
	public static void unlock(ServerPlayer player, XenoForm form) {
		XenoformState.Band band = XenoformState.band(player);
		if (band.has(form.id)) {
			return;
		}
		player.setAttached(XenoformState.BAND, band.plus(form.id));
		Component name = form.name().copy().withStyle(style -> style.withColor(TextColor.fromRgb(form.color)).withBold(true));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.xenoform.unlocked", name).withStyle(ChatFormatting.GREEN));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.unlocked_overlay", name));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.5, 0.4, 0.0);
		live(player).hudDirty = true;
	}

	/**
	 * The band's corruption: a slam gives a random other unlocked alien {@link #GLITCH_WRONG_CHANCE} of the time
	 * ({@code wanted} null: any unlocked alien, for the band acting on its own). Clean bands always give what was dialled.
	 */
	private static @Nullable XenoForm glitch(ServerPlayer player, @Nullable XenoForm wanted) {
		List<String> unlocked = XenoformState.band(player).unlocked();
		if (wanted != null && (!com.jaymospowers.core.Fate.isCorrupted(player) || unlocked.size() < 2
			|| player.getRandom().nextFloat() >= GLITCH_WRONG_CHANCE)) {
			return wanted;
		}
		List<XenoForm> others = new ArrayList<>();
		for (String id : unlocked) {
			XenoForm form = XenoForms.get(id);
			if (form != null && form != wanted) {
				others.add(form);
			}
		}
		return others.isEmpty() ? wanted : others.get(player.getRandom().nextInt(others.size()));
	}

	/** A glitch always shows: static on the screen, a sour note, and a line saying what the band did. */
	private static void glitchFx(ServerPlayer player, String message) {
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, false, false));
		player.sendOverlayMessage(Component.translatable(message).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		player.sendSystemMessage(Component.translatable(message).withStyle(ChatFormatting.DARK_PURPLE));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
	}

	// ---------------------------------------------------------------- keys

	/** R G V B: the alien's moves. As a human, only a reminder. */
	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		Live live = live(player);
		if (live.form != null) {
			if (live.ultimate && key == PowerKey.ABILITY_4 && live.form.ultimateHasMove()) {
				// (0.18.93) the Ultimate's own move takes B
				if (down) {
					live.form.onUltimateAgain(player, live);
				}
				return;
			}
			live.form.onKey(player, live, key, down);
			return;
		}
		if (down) {
			hint(player, live, Component.translatable("message.jaymospowers.xenoform.hold_z"));
		}
	}

	/**
	 * Z: held, the wrist comes up and the scroll wheel turns the dial ({@link XenoNet.Dial}); let go, the band slams and you
	 * become the dialled alien. As an alien, holding it turns you back (see {@link #tick}).
	 */
	@Override
	public void onMode(ServerPlayer player, boolean down) {
		Live live = live(player);
		if (down) {
			live.modeDownAt = player.level().getGameTime();
			live.slammed = false;
			live.quickDial = false;
			return;
		}
		boolean slam = live.modeDownAt >= 0L && !live.slammed;
		long held = live.modeDownAt < 0L ? 0L : player.level().getGameTime() - live.modeDownAt;
		live.modeDownAt = -1L;
		if (live.form != null) {
			// as an alien: a turned wheel is a quick change; held long enough, back to yourself (not on the wild dial)
			if (live.quickDial) {
				live.quickDial = false;
				XenoForm target = XenoForms.get(XenoformState.band(player).dialled());
				if (target != null && target != live.form) {
					quickChange(player, live, target);
				}
			} else if (slam && held >= HOLD_TICKS) {
				if (wild(player)) {
					hint(player, live, Component.translatable("message.jaymospowers.xenoform.wild_stuck"));
				} else {
					live.slammed = true;
					revert(player, false, true);
				}
			} else if (slam) {
				// (0.18.118) a tap: the form's own use for Z (Towerback's size)
				live.form.onModeTap(player, live);
			}
			return;
		}
		if (slam) {
			live.slammed = true;
			XenoForm dialled = XenoForms.get(XenoformState.band(player).dialled());
			if (dialled != null) {
				XenoForm glitched = glitch(player, dialled);
				if (transform(player, glitched, false) && glitched != dialled) {
					glitchFx(player, "message.jaymospowers.xenoform.glitch_wrong");
				}
			}
		}
	}

	/** The scroll wheel while Z is held (from the client): turns the dial, as yourself only. */
	static void scrollDial(ServerPlayer player, int by) {
		Live live = live(player);
		if (!has(player) || live.modeDownAt < 0L || by == 0) {
			return;
		}
		if (live.form != null) {
			// (0.18.92) quick change: the wheel picks the next alien, letting go of Z changes into it
			if (wild(player)) {
				return;
			}
			live.quickDial = true;
		}
		dial(player, Integer.signum(by));
	}

	/**
	 * C, once the power has ascended: as an alien, you become its Ultimate form for the rest of the timer (C again is the
	 * Ultimate's own move, where it has one). As yourself, nothing: slam first.
	 */
	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		if (!down) {
			return;
		}
		Live live = live(player);
		XenoForm form = live.form;
		if (form == null && live.modeDownAt >= 0L) {
			// (0.18.92) C while Z is held as yourself: the wild dial on or off (letting Z go then doesn't slam)
			live.slammed = true;
			toggleWild(player, live);
			return;
		}
		if (form == null) {
			hint(player, live, Component.translatable("message.jaymospowers.xenoform.ultimate_human"));
			return;
		}
		if (form.ultimateIsMove()) {
			// (patch 6) a one-off move (Nadir's Three Worlds Film): no Ultimate form, no flash of its own
			if (form.onUltimate(player, live)) {
				Mastery.used(player);
			}
			return;
		}
		if (live.ultimate) {
			// (0.18.93) C again: back to the base alien
			dropUltimate(player, live, true);
			return;
		}
		if (!form.onUltimate(player, live)) {
			hint(player, live, Component.translatable("message.jaymospowers.xenoform.ultimate_not_now"));
			return;
		}
		live.ultimate = true;
		live.hudDirty = true;
		ultimateBody(player, form, true);
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | form.color), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 60, 0.6, 1.0, 0.6, 0.15);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y, at.z, 40, 0.5, 0.8, 0.5, 0.3);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.9F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.5F, 1.6F);
		com.jaymospowers.core.UltimateFilm.play(player, com.jaymospowers.core.UltimateFilm.ORBIT, ULTIMATE_FILM_TICKS, 0.0);
		player.sendOverlayMessage(form.ultimateName().copy().withStyle(style -> style.withColor(TextColor.fromRgb(form.color)).withBold(true)));
		Mastery.used(player);
	}

	/**
	 * Ascension: the band is complete (every alien scanned) and the power fully mastered. Checked now and then as a
	 * human; {@code Fate.ascend} then tells the player and turns C on.
	 */
	private static void tickAscension(ServerPlayer player) {
		if (player.tickCount % ASCEND_CHECK_EVERY != 0 || com.jaymospowers.core.Fate.isAscended(player)) {
			return;
		}
		if (XenoformState.band(player).unlocked().stream().filter(id -> XenoForms.get(id) != null && !XenoForms.get(id).hidden()).count() >= XenoForms.scannable()
			&& com.jaymospowers.core.Fate.canAscend(player)) {
			com.jaymospowers.core.Fate.ascend(player);
		}
	}

	/** X went down or up (from the client): the scan. */
	static void skill(ServerPlayer player, boolean down) {
		if (!has(player)) {
			return;
		}
		Live live = live(player);
		live.scanning = down;
		if (down && live.form != null) {
			hint(player, live, Component.translatable("message.jaymospowers.xenoform.scan_human"));
		}
	}

	/** Turns the dial {@code by} aliens on. */
	public static void dial(ServerPlayer player, int by) {
		XenoformState.Band band = XenoformState.band(player);
		if (band.unlocked().size() <= 1) {
			XenoForm only = XenoForms.get(band.dialled());
			if (only != null) {
				hint(player, live(player), Component.translatable("message.jaymospowers.xenoform.only_one", only.name()));
			}
			return;
		}
		band = band.withDial(band.dial() + by);
		player.setAttached(XenoformState.BAND, band);
		XenoForm form = XenoForms.get(band.dialled());
		if (form != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.dialled", form.name().copy()
				.withStyle(style -> style.withColor(TextColor.fromRgb(form.color)))));
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.6F);
	}

	private static void hint(ServerPlayer player, Live live, Component text) {
		long now = player.level().getGameTime();
		if (now - live.lastHint < HINT_SPACING) {
			return;
		}
		live.lastHint = now;
		player.sendOverlayMessage(text.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	// ---------------------------------------------------------------- transforming

	/** Becomes {@code form}. {@code force} skips the recharge (commands). */
	public static boolean transform(ServerPlayer player, XenoForm form, boolean force) {
		if (form.lent() && !force && !XenoVoices.content(player)) {
			// the void alien is lent, never owned: the voices must be content first
			XenoVoices.refuse(player);
			return false;
		}
		Live live = live(player);
		if (live.form != null || !player.isAlive()) {
			return false;
		}
		if (!force && XenoJam.jammed(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.jammed").withStyle(ChatFormatting.RED));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
			return false;
		}
		if (!force && untilReady(player, live) > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.recharging", Mth.ceil(untilReady(player, live) / 20.0F))
				.withStyle(ChatFormatting.RED));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
			return false;
		}
		if (player.isPassenger()) {
			player.stopRiding();
		}
		if (player.isSleeping()) {
			player.stopSleeping();
		}
		float charge = charge(player, live);
		live.clearForm();
		live.form = form;
		live.humanHealth = player.getHealth();
		live.total = capacity(player);
		live.ticksLeft = Math.max(1, player.isCreative() ? live.total : (int)charge);
		live.wildNext = WILD_MIN + player.getRandom().nextInt(WILD_MAX - WILD_MIN + 1);
		player.setAttached(XenoformState.FORM, form.id);
		player.removeAttached(XenoformState.ENDING);
		player.addTag(XenoformState.TAG);
		player.addTag(XenoformState.TAG + ":" + form.id);
		live.corrupted = com.jaymospowers.core.Fate.isCorrupted(player);
		apply(player, form);
		player.setHealth(form.health);
		player.removeEffect(MobEffects.LEVITATION);
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		com.jaymospowers.core.Shakes.heavy(player, at, 10.0, 0.5F);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | COLOR), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 40, 0.5, 0.8, 0.5, 0.12);
		BandFlash.flash(player, at);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.4F, 1.8F);
		player.sendOverlayMessage(form.name().copy().withStyle(style -> style.withColor(TextColor.fromRgb(form.color)).withBold(true)));
		form.onEnter(player, live);
		Mastery.used(player);
		live.hudDirty = true;
		return true;
	}

	/**
	 * Back to yourself, at the health you had before. {@code knockout}: the alien's health ran out (a longer recharge, and
	 * a moment's dizziness). {@code fx}: the flash and sound (not on a relog).
	 */
	public static void revert(ServerPlayer player, boolean knockout, boolean fx) {
		Live live = live(player);
		XenoForm form = live.form;
		if (form == null) {
			return;
		}
		if (live.ultimate) {
			form.onUltimateEnd(player, live);
			ultimateBody(player, form, false);
		}
		if (fx) {
			XenoVoices.formEnded(player, form, knockout, live.ticksLeft <= 0, live.hurt);
		}
		form.onExit(player, live);
		player.removeTag(XenoformState.TAG);
		player.removeTag(XenoformState.TAG + ":" + form.id);
		for (Entity passenger : new ArrayList<>(player.getPassengers())) {
			passenger.stopRiding();
		}
		player.removeEffect(MobEffects.LEVITATION);
		unapply(player);
		player.removeAttached(XenoformState.FORM);
		player.removeAttached(XenoformState.ENDING);
		player.removeAttached(XenoformState.FLIGHT);
		player.removeAttached(XenoformState.CEILING);
		player.setHealth(Mth.clamp(live.humanHealth, 1.0F, player.getMaxHealth()));
		player.resetFallDistance();
		int left = Math.max(0, live.ticksLeft);
		live.clearForm();
		// what's left of the charge stays in the band; a knockout empties and locks it
		live.charge = knockout ? 0.0F : left;
		live.recharge = live.rechargeTotal = knockout ? RECHARGE_KNOCKOUT : 0;
		live.wildNext = WILD_MIN + player.getRandom().nextInt(WILD_MAX - WILD_MIN + 1);
		if (player.isCreative()) {
			live.recharge = live.rechargeTotal = 0;
			live.charge = capacity(player);
		}
		if (fx) {
			ServerLevel level = player.level();
			Vec3 at = player.position().add(0.0, 1.0, 0.0);
			level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | COLOR), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 25, 0.4, 0.6, 0.4, 0.08);
			BandFlash.flash(player, at);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
			if (knockout) {
				com.jaymospowers.core.Fate.addCorruption(player, KNOCKOUT_CORRUPTION);
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, false, false));
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.knocked_out").withStyle(ChatFormatting.RED));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
			} else {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.reverted").withStyle(ChatFormatting.GRAY));
			}
		}
		live.hudDirty = true;
	}

	/**
	 * (0.18.93) C again as the Ultimate: back to the base alien, its own extras undone, the Ultimate's health gone with it.
	 * The charge runs on.
	 */
	private static void dropUltimate(ServerPlayer player, Live live, boolean fx) {
		XenoForm form = live.form;
		if (form == null || !live.ultimate) {
			return;
		}
		form.onUltimateEnd(player, live);
		live.ultimate = false;
		ultimateBody(player, form, false);
		live.hudDirty = true;
		if (fx) {
			ServerLevel level = player.level();
			Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
			level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | form.color), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 25, 0.5, 0.8, 0.5, 0.08);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
			player.sendOverlayMessage(form.name().copy().withStyle(style -> style.withColor(TextColor.fromRgb(form.color))));
		}
	}

	/** (0.18.93) The Ultimate's body on ({@code on}) or off: its health on top (healed in full), fists, size, and the look everyone sees. */
	private static void ultimateBody(ServerPlayer player, XenoForm form, boolean on) {
		if (on) {
			float extra = form.health * ULT_HEALTH;
			modify(player, Attributes.MAX_HEALTH, ULT_HEALTH_ID, extra, AttributeModifier.Operation.ADD_VALUE);
			modify(player, Attributes.ATTACK_DAMAGE, ULT_DAMAGE_ID, ULT_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			modify(player, Attributes.SCALE, ULT_SCALE_ID, ULT_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			player.heal(extra);
			player.setAttached(XenoformState.ULTIMATE, true);
		} else {
			remove(player, Attributes.MAX_HEALTH, ULT_HEALTH_ID);
			remove(player, Attributes.ATTACK_DAMAGE, ULT_DAMAGE_ID);
			remove(player, Attributes.SCALE, ULT_SCALE_ID);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			player.removeAttached(XenoformState.ULTIMATE);
		}
		player.refreshDimensions();
	}

	/** The alien's body, as attribute modifiers on the player. */
	private static void apply(ServerPlayer player, XenoForm form) {
		unapply(player);
		modify(player, Attributes.SCALE, SCALE_ID, form.scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modify(player, Attributes.MAX_HEALTH, HEALTH_ID, form.health - player.getAttributeValue(Attributes.MAX_HEALTH), AttributeModifier.Operation.ADD_VALUE);
		modify(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, form.damage - player.getAttributeValue(Attributes.ATTACK_DAMAGE), AttributeModifier.Operation.ADD_VALUE);
		modify(player, Attributes.MOVEMENT_SPEED, SPEED_ID, form.speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modify(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, form.knockbackResist, AttributeModifier.Operation.ADD_VALUE);
		modify(player, Attributes.STEP_HEIGHT, STEP_ID, form.stepUp, AttributeModifier.Operation.ADD_VALUE);
		player.refreshDimensions();
	}

	private static void unapply(ServerPlayer player) {
		remove(player, Attributes.SCALE, SCALE_ID);
		remove(player, Attributes.MAX_HEALTH, HEALTH_ID);
		remove(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
		remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
		remove(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
		remove(player, Attributes.STEP_HEIGHT, STEP_ID);
		player.refreshDimensions();
	}

	private static void modify(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id,
		double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null && Math.abs(amount) > 1.0E-6) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	private static void remove(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	// ---------------------------------------------------------------- shared queries

	/** The alien {@code player} is right now, server side, or null. */
	public static @Nullable XenoForm formOf(ServerPlayer player) {
		return has(player) ? live(player).form : null;
	}

	/** Clears every cooldown and the recharge, and fills the band (the debug command). */
	static void ready(ServerPlayer player) {
		Live live = live(player);
		java.util.Arrays.fill(live.cooldownUntil, 0L);
		live.recharge = 0;
		live.charge = capacity(player);
		live.lastSave = 0L;
		if (live.form != null) {
			live.ticksLeft = live.total;
			player.removeAttached(XenoformState.ENDING);
		}
		live.hudDirty = true;
	}

	// ---------------------------------------------------------------- (0.18.92) the band's charge

	/** The band's full charge: mastery grows it from {@link #CHARGE_MIN} to {@link #CHARGE_MAX}. */
	public static int capacity(Player player) {
		if (player.isCreative()) {
			return CHARGE_MAX;
		}
		float mastery = Mth.clamp(Mastery.unlocked(player) / (float)Mastery.ALL, 0.0F, 1.0F);
		return CHARGE_MIN + Math.round((CHARGE_MAX - CHARGE_MIN) * mastery);
	}

	/** The charge as yourself (full the first time it's asked). */
	static float charge(ServerPlayer player, Live live) {
		if (live.charge < 0.0F) {
			live.charge = capacity(player);
		}
		return live.charge;
	}

	/** Ticks until a slam is possible as yourself: the knockout lock, then the refill up to {@link #SLAM_MIN}. */
	static int untilReady(ServerPlayer player, Live live) {
		if (player.isCreative()) {
			return 0;
		}
		float charge = charge(player, live);
		int refill = charge >= SLAM_MIN ? 0 : Mth.ceil((SLAM_MIN - charge) * REFILL_TICKS / (float)capacity(player));
		return Math.max(0, live.recharge) + refill;
	}

	/** Takes {@code amount} off a form's charge (hits, moves). Never in creative. */
	public static void drain(ServerPlayer player, Live live, int amount) {
		if (live.form == null || amount <= 0 || player.isCreative()) {
			return;
		}
		live.ticksLeft = Math.max(1, live.ticksLeft - amount);
		live.hudDirty = true;
	}

	/** Quick change: {@link #QUICK_SHARE} of the full charge for another alien, then a moment's dizziness. */
	private static void quickChange(ServerPlayer player, Live live, XenoForm target) {
		int cost = Math.round(live.total * QUICK_SHARE);
		if (!player.isCreative() && live.ticksLeft - cost < SLAM_MIN) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.quick_low").withStyle(ChatFormatting.RED));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
			return;
		}
		if (target.lent() && !XenoVoices.content(player)) {
			XenoVoices.refuse(player);
			return;
		}
		if (swap(player, live, target, cost)) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, QUICK_DIZZY * 3, 0, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, QUICK_DIZZY, 1, false, false));
		}
	}

	/** From one alien straight into another, {@code cost} off the charge, keeping the health you'll turn back at. */
	private static boolean swap(ServerPlayer player, Live live, XenoForm target, int cost) {
		float human = live.humanHealth;
		int left = Math.max(SLAM_MIN, live.ticksLeft - (player.isCreative() ? 0 : cost));
		revert(player, false, false);
		live.charge = left;
		live.recharge = 0;
		if (!transform(player, target, true)) {
			return false;
		}
		live.humanHealth = human;
		return true;
	}

	/**
	 * A killing blow as yourself: the band slams itself, into an alien that fits the death, at 1 health to turn back
	 * at. Not against the void or /kill, not while jammed or locked, and only every {@link #SAVE_COOLDOWN}.
	 */
	private static boolean save(ServerPlayer player, DamageSource source) {
		Live live = live(player);
		long now = player.level().getGameTime();
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || XenoJam.jammed(player) || live.recharge > 0
			|| live.lastSave != 0L && now - live.lastSave < SAVE_COOLDOWN) {
			return false;
		}
		XenoForm form = saviour(player, source);
		if (form == null) {
			return false;
		}
		live.charge = Math.max(charge(player, live), SAVE_CHARGE);
		player.setHealth(1.0F);
		if (!transform(player, form, true)) {
			return false;
		}
		live.lastSave = now;
		live.humanHealth = 1.0F;
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.xenoform.saved", form.name()).withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** The alien that fits the blow: a fall gives wings, drowning gills, fire a fire-born, anything else armour. */
	private static @Nullable XenoForm saviour(ServerPlayer player, DamageSource source) {
		String[] wanted = source.is(DamageTypeTags.IS_FALL) ? new String[] {"wyrm", "wisp"}
			: source.is(DamageTypeTags.IS_DROWNING) ? new String[] {"slipfin", "wisp"}
			: source.is(DamageTypeTags.IS_FIRE) ? new String[] {"pyre", "prism"}
			: new String[] {"bulwark", "knell", "prism"};
		XenoformState.Band band = XenoformState.band(player);
		for (String id : wanted) {
			XenoForm form = XenoForms.get(id);
			if (form != null && band.has(id)) {
				return form;
			}
		}
		XenoForm dialled = XenoForms.get(band.dialled());
		return dialled != null && !dialled.lent() ? dialled : randomKnown(player, null);
	}

	/** A random alien the band holds (never the hidden one unless the voices are content), other than {@code not}. */
	private static @Nullable XenoForm randomKnown(ServerPlayer player, @Nullable XenoForm not) {
		List<XenoForm> known = new ArrayList<>();
		for (String id : XenoformState.band(player).unlocked()) {
			XenoForm form = XenoForms.get(id);
			if (form != null && form != not && (!form.lent() || XenoVoices.content(player))) {
				known.add(form);
			}
		}
		return known.isEmpty() ? null : known.get(player.getRandom().nextInt(known.size()));
	}

	/** Whether the wild dial is on. */
	public static boolean wild(Player player) {
		return player.entityTags().contains(WILD_TAG);
	}

	private static void toggleWild(ServerPlayer player, Live live) {
		boolean on = !wild(player);
		if (on) {
			player.addTag(WILD_TAG);
			live.wildNext = WILD_MIN + player.getRandom().nextInt(WILD_MAX - WILD_MIN + 1);
		} else {
			player.removeTag(WILD_TAG);
		}
		player.sendOverlayMessage(Component.translatable(on ? "message.jaymospowers.xenoform.wild_on" : "message.jaymospowers.xenoform.wild_off")
			.withStyle(on ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, on ? 1.8F : 0.9F);
	}
}
