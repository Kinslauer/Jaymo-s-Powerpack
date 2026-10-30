package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Soundscape;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Z Fighter: a martial artist who fights with his fists and his ki. Everything is decided here, on the server.
 * <ul>
 * <li>Ki: a meter ({@link #BASE_MAX_KI}, more with every foe that ever beat him) that trickles back on a full stomach,
 * comes in fast while charging, from landing punches, and from eating. The techniques spend it.</li>
 * <li>R (hold), Charge Ki: stand your ground, the aura flares, ki pours in (hungry work).</li>
 * <li>G, Ki Blast; V (hold), Ki Wave; B, Flash Step ({@link ZFighterKi}).</li>
 * <li>Hold Z and scroll, Flight: Wind-style flight (hover, Sprint to cruise) borne on ki alone; scrolling up lifts him
 * gently off the ground and steps through five poses of the arms (all the way up at once: a burst of ki throws him
 * into the sky), down steps back and lands. Charging works in the air.</li>
 * <li>C (ascended): up a form; sneak+C: down one. Super is reached through hardship ({@link ZFighterBattle}); Super 2
 * and 3 are bought with training ({@link ZFighterTraining}). Each hits harder and burns more ki and food; mastery
 * lifts its power and eases its drain.</li>
 * <li>X: the training screen; sneak+X: Limit Break up a tier (wraps to off), a form that stacks on top and burns
 * health.</li>
 * <li>Passive: fists first (a weapon his Kingdoms class isn't trained with hits weakly, {@code ZFighterBattle#WEAPON_DAMAGE}), strong fists that grow with mastery,
 * five-hit combos, an enormous appetite, and no foe fells him twice ({@link ZFighterBattle}).</li>
 * </ul>
 */
public final class ZFighterPower implements Power {
	public static final ZFighterPower INSTANCE = new ZFighterPower();
	public static final Identifier ID = JaymosPowers.id("zfighter");
	public static final int COLOR = 0xFF8C1A;
	public static final int SUPER_COLOR = 0xFFD54A;

	// ---- ki ----
	public static final int BASE_MAX_KI = 100;
	/** Each foe that ever beat him raises his ceiling this much (up to {@link #ZENKAI_CAP} foes). */
	static final int KI_PER_DEFEAT = 5;
	/** How many different victors count towards the growth. */
	static final int ZENKAI_CAP = 20;
	/** Ki back per tick on a full stomach, not charging, not Super (0.5 a second). */
	static final float KI_REGEN = 0.025F;
	/** Ki per tick while charging (R); the fresh awakening's weak charge. */
	static final float CHARGE_RATE = 1.0F;
	static final float STARTER_CHARGE_RATE = 0.4F;
	/** Ki per hunger point eaten. */
	static final float KI_PER_FOOD = 3.0F;
	/** Ki per punch that lands. */
	static final float KI_PER_PUNCH = 2.0F;

	// ---- the appetite (exhaustion per tick; 4 exhaustion is one hunger point) ----
	static final float BASE_EXHAUSTION = 0.01F;
	static final float CHARGE_EXHAUSTION = 0.05F;
	/** Super burns this many times the base appetite. */
	static final float SUPER_HUNGER = 2.5F;
	/** At or below this food level he's hungry: no ki comes back by itself and his fists weaken. */
	static final int HUNGRY_FOOD = 6;
	static final double HUNGRY_WEAKNESS = 0.3;
	/** Kitchen's hearty meals: stomach slots he has on top of everyone's three (he eats like three men). */
	static final int EXTRA_STOMACH = 2;

	// ---- fists (added to the player's attack damage) ----
	static final double FIST_DAMAGE = 3.0;
	static final double FIST_PER_ABILITY = 0.75;
	static final double FIST_PER_DEFEAT = 0.1;
	static final double ASCENDED_FIST = 1.0;

	// ---- movement ----
	/** Charging (R) or gathering a Ki Wave (V) slows you this much. */
	static final double CHARGE_SLOW = 0.6;
	/** Ki per tick to stay up (1 a second), plus this much per block a tick he's moving (a fast cruise costs more). */
	static final float FLY_KI = 0.05F;
	static final float FLY_KI_PER_SPEED = 0.12F;
	/** The flight poses (hold Z and scroll): 1 rising, arms at the sides ... 5 diving, hands together overhead. */
	public static final int FLIGHT_POSES = 5;
	private static final float NORMAL_FLY_SPEED = 0.05F;
	/** Mastery needed for Z (Flight): abilities unlocked. */
	static final int FLIGHT_UNLOCK = 2;

	// ---- forms (index: 0 base, 1 Super, 2 Super 2, 3 Super 3, 4 Super 4: the Warrior-born's hidden form, ApeRage) ----
	// (0.17.50) 5 False Super, 6 the god form, 7-9 the legendary line ({@link ZFighterForms}); the other track's forms start at 10
	// (0.17.190) 4 is now Super Primal Super, and 10 Primal Super, the step before it (ApeRage.PRIMAL_SUPER): the branch forms' ids
	// live in their own attachment, so 10 is free here
	/** Fists (added to the damage multiplier), ki techniques (times), speed (added). A third of their bonus before the training slowdown. */
	static final double[] FORM_FIST = {0.0, 0.2, 0.33, 0.53, 0.75, 0.4, 0.85, 0.5, 0.8, 1.1, 0.64};
	static final float[] FORM_KI = {1.0F, 1.17F, 1.27F, 1.4F, 1.55F, 1.3F, 1.7F, 1.3F, 1.55F, 1.8F, 1.47F};
	static final double[] FORM_SPEED = {0.0, 0.08, 0.12, 0.15, 0.18, 0.1, 0.22, 0.03, 0.05, 0.07, 0.16};
	/** Top cruise speed in flight (blocks a tick; Wind's is 2.5 in a plain rainy sky). */
	public static final double[] FORM_CRUISE = {1.2, 1.6, 1.9, 2.2, 2.4, 1.9, 2.7, 1.5, 1.7, 1.9, 2.3};
	/** Ki per tick to hold the form (1.5, 3, 6 a second; Super 4, at peace with the ape, 4.4), and times the base appetite. */
	static final float[] FORM_DRAIN = {0.0F, 0.075F, 0.15F, 0.3F, 0.22F, 0.06F, 0.15F, 0.2F, 0.3F, 0.45F, 0.18F};
	static final float[] FORM_HUNGER = {1.0F, 2.5F, 3.5F, 5.0F, 4.5F, 3.0F, 4.0F, 4.5F, 6.0F, 8.0F, 4.0F};
	/** Ki to go into the form (free from half mastery; Super 4 has no mastery: always). */
	static final int[] FORM_ENTER_KI = {0, 30, 50, 80, 90, 0, 100, 70, 100, 130, 85};
	static final int[] FORM_COLOR = {0xFFE6B0, 0xFFD54A, 0xFFE873, 0xFFC020, 0xE02818, 0xFFE9A0, 0xE0302A, 0xA6E64A, 0x72DA45, 0x45C83C, ApeRage.PRIMAL_SUPER_COLOR};
	/** Super 4's fire: flames a tick each of two kinds, and one in this many ticks an ember spits off him. */
	static final int SUPER_4_FLAMES = 2, SUPER_4_EMBER_ODDS = 12;
	/** (0.17.190) Primal Super's fire: one small flame every this many ticks powering up; settled, this many times rarer than Super Primal Super's. */
	static final int PRIMAL_FLAME_EVERY = 4, PRIMAL_SETTLED_SLOWER = 4;
	/**
	 * Andy: the full storm of aura, sparks and fire only for the power-up blast, this many ticks after going into a form.
	 * After that it settles to the aura and the rising debris, both stronger each form, so the body (Super 4's fur) shows.
	 */
	static final int POWER_UP_TICKS = 50;
	/** Settled: aura motes a tick per form, one in this many ticks a spark per form, one in this many a Super 4 flame lick. */
	static final int SETTLED_SPARK_EVERY = 10, SETTLED_FLAME_EVERY = 3;
	static final float SETTLED_AURA_SIZE = 0.8F;
	/** Fully mastered, a form is this much stronger (scaled by mastery) and drains this much less. */
	static final float MASTERY_POWER = 0.05F;
	static final float MASTERY_EASE = 0.25F;
	/** After dropping back to base, this long before going up again. */
	static final int SUPER_COOLDOWN = 200;
	static final double SUPER_BURST_RADIUS = 5.0;
	/** The transformation's screen shake: degrees, more for each form up, and how far it's felt. */
	static final float SUPER_SHAKE = 0.6F, SUPER_SHAKE_PER_FORM = 0.3F;
	static final double SUPER_SHAKE_RANGE = 24.0;
	/** (0.16.24) A transformation's film (ticks): low and close, sweeping round your front. */
	static final int SUPER_FILM_TICKS = 36;

	// ---- Limit Break (index: tier 1-3) ----
	static final double[] LIMIT_FIST = {0.0, 0.1, 0.2, 0.33};
	static final double[] LIMIT_SPEED = {0.0, 0.03, 0.07, 0.1};
	/** Health it burns per tick (0.4, 1, 2 a second); it ends before it would take the last heart. */
	static final float[] LIMIT_HEALTH = {0.0F, 0.02F, 0.05F, 0.1F};
	/** Stacked on a form, that form's ki drain is multiplied by this. */
	static final float LIMIT_STACK_DRAIN = 2.0F;
	static final int LIMIT_COLOR = 0xFF3A2A;
	/** In a form, ground this far below him breaks up; this many bits rise every other tick per form. */
	static final int DEBRIS_HEIGHT = 3;
	static final int DEBRIS_PER_FORM = 3;
	/** Charging: bits of ground that rise every {@link #CHARGE_DEBRIS_EVERY} ticks, per tier (base 1, Super 2, Super 2 3, Super 3 4). */
	static final int CHARGE_DEBRIS = 2;
	static final int CHARGE_DEBRIS_EVERY = 3;
	/** Charging: the chance a tick (tier in this) of a small lightning crackle over him. */
	static final int CHARGE_CRACKLE = 10;
	/** Charging: puffs of smoky fog (a Verdant's, mist) rolling out at his feet every other tick, per tier. */
	static final int CHARGE_FOG = 1;
	/** Charging: aura motes a tick (when full: {@link #CHARGE_AURA_FULL}). */
	static final int CHARGE_AURA = 2, CHARGE_AURA_FULL = 4;
	/**
	 * In a form, standing (not charging): bits of ground floating round him (this many a form, every other tick), a crackle
	 * of lightning with a chance of (form + 1) in {@link #IDLE_CRACKLE} a tick, and sparks (this many a form). No smoke.
	 */
	static final int IDLE_FLOAT_PER_FORM = 2;
	static final int IDLE_CRACKLE = 8;
	static final int IDLE_SPARKS_PER_FORM = 2;
	private static final int CRACKLE_COLOR = 0xD8ECFF;
	/**
	 * (0.15.59) Andy: "a faint ki colored aura around z fighters that intensifies with their increasing power/forms". In
	 * base form, not charging: a few small motes of his ki colour hugging him every {@link #FAINT_AURA_EVERY} ticks (one
	 * tick sooner, and a little bigger, for each step of power), this big at the weakest.
	 */
	static final int FAINT_AURA_EVERY = 6;
	static final float FAINT_AURA_SIZE = 0.55F;
	/** A step of power for the aura: every this much max ki above {@link #BASE_MAX_KI}, up to this many steps. */
	static final int AURA_KI_STEP = 40, AURA_STEPS = 4;
	/**
	 * (0.15.59) Charged to full: the aura flashes once and the power blows out round him. Blasts of smoke, motes of ki in
	 * the ring, bits of ground thrown out to this far, and the shake (felt this far).
	 */
	static final int FULL_BURST_BLASTS = 5, FULL_BURST_RING = 36, FULL_BURST_DEBRIS = 40;
	static final double FULL_BURST_RADIUS = 3.5;
	static final float FULL_BURST_SHAKE = 0.5F;
	static final double FULL_BURST_SHAKE_RANGE = 16.0;
	/** Never twice in this many ticks (ki bobbing at full). */
	static final int FULL_BURST_GAP = 40;
	/**
	 * (0.16.20) Andy: "after achieving max it stays for a bit until your ki falls". Let go of a full charge and the charging
	 * aura holds this long, or until his ki drops under this share of full.
	 * (0.17.133) Andy: "ki charge should expand the size of the aura, then it levels back out as your ki diminishes". It
	 * now holds down to 80% of his ki (was 90%), still 8 s at most, so spending it shrinks the aura back before it goes.
	 */
	static final int AURA_HOLD_TICKS = 160;
	static final float AURA_HOLD_KI = 0.8F;
	/** (0.17.133) His ki as a share of full is sent to everyone in this many steps (the aura's size; few, to send little). */
	public static final int KI_SHARE_STEPS = 20;
	/** (0.15.59) A Warrior-born's scream into each form is heard (and read) this far; lines to pick from, a form. */
	static final double SHOUT_RANGE = 32.0;
	static final int SHOUT_LINES = 3;

	private static final Identifier FIST_ID = JaymosPowers.id("zfighter_fists");
	private static final Identifier SUPER_FIST_ID = JaymosPowers.id("zfighter_super_fists");
	private static final Identifier HUNGRY_ID = JaymosPowers.id("zfighter_hungry");
	private static final Identifier SLOW_ID = JaymosPowers.id("zfighter_charging");
	private static final Identifier SUPER_SPEED_ID = JaymosPowers.id("zfighter_super_speed");
	private static final Identifier LIMIT_FIST_ID = JaymosPowers.id("zfighter_limit_fists");
	private static final Identifier LIMIT_SPEED_ID = JaymosPowers.id("zfighter_limit_speed");
	private static final Identifier VITALITY_ID = JaymosPowers.id("zfighter_vitality");
	private static final Identifier RAGE_FIST_ID = JaymosPowers.id("zfighter_rage_fists");
	private static final Identifier REACH_ID = JaymosPowers.id("zfighter_reach");
	private static final Identifier SCALE_ID = JaymosPowers.id("zfighter_scale");
	private static final Identifier APE_FIST_ID = JaymosPowers.id("zfighter_ape_fists");
	private static final Identifier APE_HEALTH_ID = JaymosPowers.id("zfighter_ape_health");
	private static final Identifier APE_SPEED_ID = JaymosPowers.id("zfighter_ape_speed");
	private static final Identifier APE_JUMP_ID = JaymosPowers.id("zfighter_ape_jump");
	private static final Identifier APE_GRAVITY_ID = JaymosPowers.id("zfighter_ape_gravity");
	private static final Identifier APE_FALL_ID = JaymosPowers.id("zfighter_ape_fall");

	/** The owner's HUD state (ki, cooldowns, what he's doing). Not saved: a relog clears it. */
	public static final AttachmentType<ZFighterState> STATE = AttachmentRegistry.create(JaymosPowers.id("zfighter_state"),
		builder -> builder.syncWith(ZFighterState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	/** Every foe that has ever beaten him (entity type ids, or {@code player:<uuid>}): his growth. Saved, kept through death. */
	public static final AttachmentType<List<String>> DEFEATS = AttachmentRegistry.create(JaymosPowers.id("zfighter_defeats"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
	/**
	 * Foes that beat him and haven't been beaten back yet: the rematch is on, and only then do they hit him for less and
	 * take more from him. Beating one settles it. Saved, kept through death.
	 */
	public static final AttachmentType<List<String>> GRUDGES = AttachmentRegistry.create(JaymosPowers.id("zfighter_grudges"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
	/** How he's flying, for everyone's client: 0 (or absent) not, 1-{@link #FLIGHT_POSES} the pose his arms hold. */
	public static final AttachmentType<Integer> FLIGHT = AttachmentRegistry.create(JaymosPowers.id("zfighter_flight"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** Charging ki right now, for everyone's client (the stance): the form he's in plus one; absent when he isn't. */
	public static final AttachmentType<Integer> CHARGE = AttachmentRegistry.create(JaymosPowers.id("zfighter_charge"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/**
	 * Hands raised to a Spirit Bomb, for everyone's client (the pose): the one raising it, and everyone lending to it (any
	 * player, Z Fighter or not). Absent otherwise; not saved.
	 */
	public static final AttachmentType<Boolean> SPIRIT_HANDS = AttachmentRegistry.create(JaymosPowers.id("zfighter_spirit_hands"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** The form he's in (1-3), for everyone's client (the hair); absent in base form. */
	public static final AttachmentType<Integer> FORM = AttachmentRegistry.create(JaymosPowers.id("zfighter_form"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/**
	 * (0.16.20) Let go of a full charge: the game time his charging aura holds until (it goes sooner if his ki falls), for
	 * everyone's client ({@code ZFighterKiAura}). Absent when it isn't holding; not saved.
	 */
	public static final AttachmentType<Long> AURA_HOLD = AttachmentRegistry.create(JaymosPowers.id("zfighter_aura_hold"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	/** (0.16.20) The game time his ki last hit full on a charge (the ki glow's pulse), for everyone's client; not saved. */
	public static final AttachmentType<Long> KI_FULL = AttachmentRegistry.create(JaymosPowers.id("zfighter_ki_full"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	/**
	 * (0.17.133) His ki out of {@link #KI_SHARE_STEPS} of full, for everyone's client: the aura swells with it
	 * ({@code ZFighterKiAura}). Absent at none; not saved.
	 */
	public static final AttachmentType<Integer> KI_SHARE = AttachmentRegistry.create(JaymosPowers.id("zfighter_ki_share"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/**
	 * (0.13.27) Andy: "every zfighter should spawn with a random ki color". The ki colour each Z Fighter is born with,
	 * drawn from {@link #KI_PALETTE} when the power comes (or the first time it's missing): his blasts, disc, waves, bomb,
	 * barrier and aura in base form (a form's own colour takes over in it). Saved, kept through death, synced to everyone.
	 */
	public static final AttachmentType<Integer> KI_COLOR = AttachmentRegistry.create(JaymosPowers.id("zfighter_ki_color"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** The ki colours one is born with: bright, clean hues that read well as glowing ki. */
	static final int[] KI_PALETTE = {
		0x8FD3FF, // sky blue
		0x4A7BFF, // deep blue
		0xE8F4FF, // white
		0xFFC85A, // gold
		0xFF8C1A, // orange
		0xFF4A3A, // red
		0xFF70D8, // pink
		0xB050FF, // purple
		0x7A5CFF, // violet
		0x5AFF8C, // green
		0xE8FF70, // lime
		0x40F0E0, // cyan
	};

	/**
	 * Hardship lived through (the road to Super). Saved, kept through death. (0.17.91) Synced to its owner, whose training
	 * screen checks it before Learn lights up.
	 */
	public static final AttachmentType<Integer> HARDSHIP = AttachmentRegistry.create(JaymosPowers.id("zfighter_hardship"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** What the server keeps on each Z Fighter between ticks. */
	static final class Live {
		float ki;
		boolean charging;
		long waveFrom = -1L;
		/** When V went down for a blast or barrage (-1: it isn't held), and when V may fire again. */
		long barrageFrom = -1L;
		long barrageReady;
		boolean flying;
		Vec3 lastFlyPos;
		/** The form he's in (0 base, 1-3) and the Limit Break tier on top (0 off). */
		int form;
		int limit;
		float tpFraction;
		Double lastX;
		double lastZ;
		double walked;
		int meditating;

		boolean superOn() {
			return form > 0;
		}
		int combo;
		long lastHit = -1000L;
		/** When his health fell into the danger zone mid-fight (-1: it hasn't). */
		long clutchFrom = -1L;
		long lastClutchCredit = -100000L;
		int lastFood = -1;
		/** Until when the movement check is off (a Flash Step). */
		long stepUntil;
		long lastGrowl = -100000L;
		long lastRefusal = -100L;
		/** Who hurt him lately (for the Spirit Bomb and Homing Volley: they're foes). */
		final Map<UUID, Long> attackers = new HashMap<>();
		/** Ticks left on the Ki Barrier. */
		int barrierTicks;
		/** Since when the Spirit Bomb has been gathering (-1: it isn't), and what it holds. */
		long spiritFrom = -1L;
		float spiritEnergy;
		/** (0.17.196) The Spirit Bomb is picked on the hold-C menu: the next tap of C raises it. */
		boolean bombPicked;
		/** Rage (before he has ever gone Super): the meter (0-1), ticks left in it, ticks before it can fill again. */
		float rage;
		int rageTicks;
		int rageCooldown;
		/** When his charge last filled him (the burst). */
		long lastFullBurst = -1000L;
		/** When he last went into a form: the power-up blast's storm of particles runs {@link #POWER_UP_TICKS} from here. */
		long formSince = -1000L;
		/** (0.16.20) Until when the charging aura holds after a full charge (-1: it isn't). */
		long auraHold = -1L;
		/** His lineage ({@link ZFighterLineage}), refreshed every tick, and how long the Great Ape has been out of the moonlight. */
		int lineage;
		/** (0.13.27) His ki colour ({@link #KI_COLOR}), kept fresh each tick; -1 before the first. */
		int kiColor = -1;
		int moonless;
		/** How far grown into the Great Ape (0-1): eases up over {@link ZFighterLineage#APE_GROW_TICKS} and back down. */
		float apeSize;
		/** How far he'd walked at the Great Ape's last footfall (the ground shakes every stride). */
		float apeStepAt;
		/** Which foot comes down next (the Great Ape's stomps alternate). */
		boolean apeLeftFoot;
		/** The highest he's been since his feet last touched down: a long drop ends in a stomp. */
		double apePeak;
		/**
		 * (0.12.3) The Great Ape's legs: blows taken to them, until when it lies crippled (-1: it isn't), until when its
		 * legs can't be taken again. The wild ape (a Warrior-born who's no Z Fighter): when it turned (-1: it isn't), when
		 * the moon was last rolled for, its quarry (entity id, -1: none) and when it may swing again.
		 */
		int legHits;
		long crippledUntil = -1L;
		long crippleSafeUntil;
		long wildFrom = -1L;
		long wildRolledAt = Long.MIN_VALUE / 2;
		int berserkTarget = -1;
		long berserkSwingAt;
		/**
		 * The wild ape on the loose: since when it's chased its quarry without landing a blow, the one it gave up on (and
		 * until when), when it next picks a spot to roam to, and until when it's running from a hurt (then can't again).
		 */
		long berserkSince;
		int berserkIgnore = -1;
		long berserkIgnoreUntil;
		/** (0.17.68) The Great Ape's grab ({@link ApeGrab}): whom he's reaching for, when he may again, where his catch was taken and is slammed. */
		int grabTarget = -1;
		long grabReadyAt;
		@org.jspecify.annotations.Nullable Vec3 grabSnatchAt, grabSlamAt;
		long roamAt;
		long fleeUntil = -1L;
		long fleeRestUntil;
		/** The guard (right click held): up, since when, when he may parry again, until when his punch counters. */
		boolean guarding;
		long guardStart;
		long parryReadyAt;
		long counterUntil;

		boolean raging() {
			return rageTicks > 0;
		}

		/** (0.17.50) How hard the form's effects play: the form's own number up to Super 4, a set tier for the newer forms. */
		int tier() {
			return ZFighterForms.tier(form);
		}

		/** (0.17.50) Silver instinct is on ({@link ZFighterInstinct}): the aura goes silver. */
		boolean silver;
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();

	private ZFighterPower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}. */
	public static void init() {
		Powers.register(INSTANCE);
		com.jaymospowers.power.web.KitchenCompat.extraStomachSlots(player -> Powers.has(player, INSTANCE) ? EXTRA_STOMACH : 0);
		// (0.17.3) what a gifted villager does with Z Fighter
		ZFighterGift.init();
		ZFighterKi.init();
		ZFighterWaveBeam.init();
		ZFighterDisc.init();
		ZFighterItems.init();
		ZFighterSparring.init();
		ZFighterDrills.init();
		ZFighterLineage.init();
		ZFighterWishes.init();
		ZFighterSites.init();
		// (0.17.60) the bigger Lookout, the gravity machine, pocket capsules, fighter weapons
		ZFighterLookout.init();
		ZFighterGravityMachine.init();
		ZFighterCapsules.init();
		ZFighterWeapons.init();
		ZFighterFusion.init();
		ZFighterMasters.init();
		// (0.17.61) story sagas and side quests
		ZFighterSagas.init();
		// (0.17.62) the space pod, the far places and their missions
		ZFighterSpace.init();
		// (0.18.51) the star charts and the Star-Sight Bench
		com.jaymospowers.power.zfighter.sky.Sky.init();
		com.jaymospowers.power.zfighter.pod.PodParts.init();
		com.jaymospowers.power.zfighter.fuel.PodFuel.init();
		com.jaymospowers.power.zfighter.breath.Breath.init();
		ZFighterRaids.init();
		ZFighterTournaments.init();
		ZFighterAfterlife.init();
		ZFighterDemonRealm.init();
		ZFighterStarts.init();
		// (0.17.146) the Z Fighter characters as MCA villagers, and their talk screens
		ZFighterNpcs.init();
		ZFighterPatrol.init();
		ZFighterRaiders.init();
		ZFighterCorruption.init();
		// (0.17.50) branch forms, Ego, the android upgrade, the metal rebuild
		ZFighterBranchForms.init();
		ZFighterNet.register();
		ZFighterBattle.init();
		ZFighterCombo.init();
		ZFighterImpact.init();
		// (0.17.45) lock-on, poise, rushes, grab and throw, the dodge, ki weapons
		ZFighterCombat.init();
		// (0.17.46) drill beam, ring of orbs, triangle cannon, rising dome, ki rain, nova sphere, rage scream, afterimage
		ZFighterArts.init();
		// (0.17.45) ki sense, scouter, instant teleport, lend ki, air burst, impact frames
		ZFighterSenses.init();
		// (0.17.50) false super, golden ape, god form ritual, silver instinct, the legendary line
		ZFighterForms.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ZFighterDebugCommand.register(dispatcher));
		ZFighterGrowth.init();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LIVE.remove(handler.player.getUUID());
			ZFighterGrowth.forget(handler.player);
			ZFighterDisc.forget(handler.player);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LIVE.clear();
			ZFighterGrowth.clear();
			ZFighterKi.clear();
			ZFighterWaveBeam.clear();
			ZFighterDisc.clear();
			ZFighterArts.clear();
		});
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
		return new ItemStack(Items.BLAZE_POWDER);
	}

	/** Weak Charge Ki on R from the start; then Charge Ki in full, Ki Blast, Flash Step, and the Ki Wave last. */
	@Override
	public PowerKey starterKey() {
		return PowerKey.ABILITY_1;
	}

	/**
	 * A charged technique bought with TP (the Ki Bomb) works on sneak+V before V has unlocked: it was paid for. The Ki
	 * Wave, which every fighter starts with, still waits for V. Letting go of V always counts, so a gathered one fires.
	 * Likewise a learned quick technique (the Ki Disc and the rest) works on G before G unlocks; the Ki Blast waits.
	 */
	@Override
	public boolean usableEarly(Player player, PowerKey key) {
		if (ZFighterLineage.ape(player)) {
			return false;
		}
		if (key == PowerKey.ABILITY_2) {
			int quick = ZFighterTraining.of(player).quick();
			return quick != ZFighterTraining.KI_BLAST && ZFighterTraining.of(player).knows(quick);
		}
		if (key != PowerKey.ABILITY_3) {
			return false;
		}
		int charged = ZFighterTraining.of(player).charged();
		boolean bought = charged != ZFighterTraining.KI_WAVE && ZFighterTraining.of(player).knows(charged);
		return bought && (player.isShiftKeyDown() || state(player).waveFrom() >= 0);
	}

	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_2, PowerKey.ABILITY_4, PowerKey.ABILITY_3};
	}

	@Override
	public boolean slotUnlocked(Player player, String slot) {
		return switch (slot) {
			case "mode" -> Mastery.unlocked(player) >= FLIGHT_UNLOCK;
			case "special" -> true;
			default -> true;
		};
	}

	@Override
	public void onCorrupted(ServerPlayer player) {
		// the mark and the corrupted strength come at once
		refresh(player, live(player));
	}

	@Override
	public void onPurified(ServerPlayer player) {
		Live live = live(player);
		ZFighterCorruption.end(player, live);
		refresh(player, live);
	}

	@Override
	public String statusSuffix(Player player) {
		int form = state(player).form();
		return form > 0 ? ".super_" + form : null;
	}

	@Override
	public void onGained(ServerPlayer player) {
		player.setAttached(STATE, ZFighterState.NONE);
		// (0.13.27) born with a ki colour of his own
		rollKiColor(player);
		// the lineage isn't chosen: it's in the blood (the heritage)
		ZFighterLineage.inherit(player);
	}

	/** (0.15.31) Downed: what was running ends. */
	@Override
	public void onDowned(ServerPlayer player) {
		// out of the air (Ki flight also ends by WindFlight.canFly) and out of any combo string
		Live live = LIVE.get(player.getUUID());
		if (live != null && live.flying) {
			endFlight(player, live);
		}
		ZFighterCombo.stop(player);
	}

	@Override
	public void onLost(ServerPlayer player) {
		Live live = LIVE.remove(player.getUUID());
		if (live != null && live.flying) {
			live.flying = false;
		}
		landFlight(player);
		for (Identifier id : new Identifier[] {FIST_ID, SUPER_FIST_ID, HUNGRY_ID, LIMIT_FIST_ID, RAGE_FIST_ID, APE_FIST_ID}) {
			remove(player, Attributes.ATTACK_DAMAGE, id);
		}
		remove(player, Attributes.MOVEMENT_SPEED, SLOW_ID);
		remove(player, Attributes.MOVEMENT_SPEED, SUPER_SPEED_ID);
		remove(player, Attributes.MOVEMENT_SPEED, LIMIT_SPEED_ID);
		remove(player, Attributes.MAX_HEALTH, VITALITY_ID);
		remove(player, Attributes.MAX_HEALTH, APE_HEALTH_ID);
		remove(player, Attributes.MOVEMENT_SPEED, APE_SPEED_ID);
		remove(player, Attributes.JUMP_STRENGTH, APE_JUMP_ID);
		remove(player, Attributes.GRAVITY, APE_GRAVITY_ID);
		remove(player, Attributes.FALL_DAMAGE_MULTIPLIER, APE_FALL_ID);
		remove(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
		remove(player, Attributes.SCALE, SCALE_ID);
		remove(player, Attributes.SCALE, ZFighterFusion.SCALE_ID);
		player.removeAttached(ZFighterGuard.GUARD);
		player.removeAttached(ZFighterWaveBeam.FIRING);
		ZFighterCombo.drop(player);
		player.removeAttached(ZFighterLineage.LINEAGE);
		player.removeAttached(ZFighterLineage.TAIL_WRAPPED);
		player.removeAttached(ZFighterLineage.GREAT_APE);
		player.removeAttached(STATE);
		player.removeAttached(FLIGHT);
		player.removeAttached(CHARGE);
		player.removeAttached(AURA_HOLD);
		player.removeAttached(KI_FULL);
		player.removeAttached(KI_SHARE);
		ZFighterDisc.forget(player);
		ZFighterCorruption.clear(player);
		ZFighterBranchForms.clear(player);
		ZFighterLineage.tailTags(player, false, false);
		player.removeAttached(FORM);
		player.removeAttached(DEFEATS);
		player.removeAttached(GRUDGES);
		player.removeAttached(ZFighterTraining.DATA);
		player.removeAttached(ZFighterGrowth.DATA);
		player.removeAttached(HARDSHIP);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player) {
		return live(player).flying || ZFighterLineage.ape(player);
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player) {
		Live live = live(player);
		return live.flying || live.stepUntil > player.level().getGameTime() || ZFighterLineage.ape(player);
	}

	@Override
	public void tick(ServerPlayer player) {
		if (!player.isAlive()) {
			return;
		}
		Live live = live(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		int max = maxKi(player);
		boolean creative = player.isCreative();
		int food = player.getFoodData().getFoodLevel();
		float startKi = live.ki;
		boolean hungry = food <= HUNGRY_FOOD && !creative;
		boolean starving = food <= 0 && !creative;
		ZFighterLineage.tick(player, live);
		// (0.17.56) the lineages' extras: adrenaline, water regen, the ki reserve, the comeback
		ZFighterRaces.tick(player, live, max);
		ZFighterCorruption.tick(player, live);
		ZFighterBranchForms.tick(player, live);

		// a Z Fighter eats like three men, and every bite comes back as ki
		if (live.lastFood >= 0 && food > live.lastFood) {
			live.ki += (food - live.lastFood) * KI_PER_FOOD;
		}
		live.lastFood = food;
		if (!creative) {
			player.causeFoodExhaustion(BASE_EXHAUSTION * FORM_HUNGER[live.form] * (live.limit > 0 ? 1.5F : 1.0F));
		}
		if (hungry && now - live.lastGrowl > 600) {
			live.lastGrowl = now;
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_hungry").withStyle(ChatFormatting.GOLD));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.6F, 0.5F);
		}

		if (live.charging) {
			if (starving) {
				live.charging = false;
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_starving").withStyle(ChatFormatting.RED));
			} else {
				live.ki += Mastery.starterOnly(player) ? STARTER_CHARGE_RATE : CHARGE_RATE;
				if (!creative) {
					player.causeFoodExhaustion(CHARGE_EXHAUSTION);
				}
				aura(player, live, live.ki >= max ? CHARGE_AURA_FULL : CHARGE_AURA);
				chargeFx(player, live, now);
				if (startKi < max && live.ki >= max && now - live.lastFullBurst >= FULL_BURST_GAP) {
					fullBurst(player, live, now);
				}
				if (Soundscape.due(player, "ki.power_up", 12, 20)) {
					float fill = Mth.clamp(live.ki / max, 0.0F, 1.0F);
					Soundscape.play(level, player.position(), SoundSource.PLAYERS, ZFighterKi.KI_HUM.louder(1.2F).pitched(0.7F + fill * 0.9F),
						ZFighterKi.KI_SURGE);
				}
			}
		} else if (!live.superOn() && !hungry && live.waveFrom < 0 && live.barrageFrom < 0 && live.spiritFrom < 0) {
			live.ki += KI_REGEN * ZFighterRealism.kiRegen() * ZFighterBioForms.regen(player) * ZFighterDivineGrace.regen(player);
		}

		ZFighterKi.tickWave(player, live);
		ZFighterKi.tickBarrage(player, live);
		ZFighterTechniques.tickBarrier(player, live);
		ZFighterTechniques.tickSpirit(player, live);

		// flight: Wind-style (the client steers it), borne on ki alone
		if (live.flying) {
			Vec3 at = player.position();
			double moved = live.lastFlyPos == null ? 0.0 : Math.min(3.0, at.distanceTo(live.lastFlyPos));
			live.lastFlyPos = at;
			if (!creative) {
				live.ki -= (FLY_KI + FLY_KI_PER_SPEED * (float)moved) * ZFighterAndroid.costFactor(player) * ZFighterRealism.flightCost() * ZFighterBioForms.flight(player)
					* ZFighterDivineGrace.flight(player);
			}
			player.resetFallDistance();
			if (now % 4 == 0) {
				level.sendParticles(dust(live), true, false, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
			}
			if (live.ki <= 0.0F || !com.jaymospowers.power.wind.WindFlight.canFly(player)) {
				endFlight(player, live);
				if (live.ki <= 0.0F) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
				}
			}
		} else if (player.getAbilities().mayfly && !creative && !player.isSpectator()) {
			// left over from the old creative-style flight: nobody else gives a Z Fighter wings
			landFlight(player);
		}

		if (live.superOn()) {
			float ease = 1.0F - MASTERY_EASE * ZFighterTraining.mastery(player, live.form);
			live.ki -= FORM_DRAIN[live.form] * ease * (live.limit > 0 ? LIMIT_STACK_DRAIN : 1.0F) * ZFighterLineage.drainFactor(live.lineage, live.form)
				* ZFighterBranchForms.drainFactor(player, live) * ZFighterDivineGrace.drain(player);
			// the aura roars harder with each form, and the ground under him breaks up and rises
			// (thinner while charging, which has its own aura, so he isn't lost in it)
			// (0.15.59) and a little harder the stronger he's grown
			boolean poweringUp = now - live.formSince < POWER_UP_TICKS;
			if (live.charging || poweringUp) {
				aura(player, live, live.charging ? live.tier() : 1 + 2 * live.tier() + auraStep(max) / 2);
			} else {
				settledAura(player, live, 1 + live.tier() / 2 + auraStep(max) / 4);
			}
			if (now % 2 == 0) {
				debris(player, live);
			}
			if (!live.charging) {
				idleFx(player, live, now, poweringUp);
			}
			if (live.ki <= 0.0F) {
				endSuper(player, live);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
			}
		} else if (!live.charging) {
			faintAura(player, live, now, max);
		}
		if (live.limit > 0) {
			float burn = LIMIT_HEALTH[live.limit] * ZFighterLineage.limitFactor(player);
			if (player.getHealth() - burn <= 2.0F) {
				endLimit(player, live);
			} else {
				if (!creative) {
					player.setHealth(player.getHealth() - burn);
				}
				if (now % 3 == 0) {
					level.sendParticles(new DustParticleOptions(LIMIT_COLOR, 1.4F), true, false, player.getX(), player.getY() + 1.0, player.getZ(),
						live.limit * 2, 0.45, 0.7, 0.45, 0.05);
				}
			}
		}
		ZFighterTraining.tick(player, live);

		ZFighterCombo.tick(player, live);
		live.ki = Mth.clamp(live.ki, 0.0F, max);
		auraHold(player, live, now, max);
		ZFighterGrowth.tick(player, live);
		ZFighterForms.tick(player, live);
		ZFighterBattle.tick(player, live);
		ZFighterSenses.tick(player, live);
		if (now % 10 == 0) {
			refresh(player, live);
		}
		sync(player, live, max);
	}

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		Live live = live(player);
		if (key == PowerKey.ABILITY_3 && ZFighterWaveBeam.out(player)) {
			// his beam is out: V only pushes it, if it has locked in a clash
			com.jaymospowers.core.BeamClash.input(player, down);
			return;
		}
		if (ZFighterLineage.ape(player) && key != PowerKey.ABILITY_1) {
			// the Great Ape has no techniques: only his fists, and the beam from his mouth on V
			if (key == PowerKey.ABILITY_3 && down && ready(player, ZFighterState.WAVE) && ZFighterKi.apeBeam(player, live)) {
				cooldown(player, ZFighterState.WAVE, ZFighterLineage.APE_BEAM_COOLDOWN);
			}
			sync(player, live, maxKi(player));
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (down && player.getFoodData().getFoodLevel() <= 0 && !player.isCreative()) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_starving").withStyle(ChatFormatting.RED));
					return;
				}
				live.charging = down;
				if (down) {
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F,
						1.4F);
				}
				refresh(player, live);
			}
			case ABILITY_2 -> {
				if (down && ready(player, ZFighterState.BLAST)) {
					quick(player, live);
				}
			}
			case ABILITY_3 -> {
				// V: tap for a Ki Blast, hold for the two-handed barrage; sneak+V (hold) gathers the charged technique
				if (down) {
					if (player.isShiftKeyDown()) {
						if (ready(player, ZFighterState.WAVE)) {
							ZFighterKi.startWave(player, live);
						}
					} else {
						ZFighterKi.pressBarrage(player, live);
					}
				} else if (ZFighterKi.releaseWave(player, live)) {
					cooldown(player, ZFighterState.WAVE, ZFighterArts.waveCooldown(player));
				} else {
					ZFighterKi.releaseBarrage(player, live);
				}
				refresh(player, live);
			}
			case ABILITY_4 -> {
				// (0.17.45) Instant Teleport, when it is on B in place of Flash Step
				if (ZFighterTeleport.key(player, live, down)) {
					sync(player, live, maxKi(player));
					return;
				}
				if (down && live.lineage == ZFighterLineage.VERDANT && player.isShiftKeyDown()) {
					if (ready(player, ZFighterState.STEP) && ZFighterLineage.heal(player, live)) {
						cooldown(player, ZFighterState.STEP, ZFighterLineage.HEAL_COOLDOWN);
					}
				} else if (down && ready(player, ZFighterState.STEP) && ZFighterKi.step(player, live)) {
					cooldown(player, ZFighterState.STEP, ZFighterKi.STEP_COOLDOWN);
				}
			}
		}
		sync(player, live, maxKi(player));
	}

	/** G: whichever quick technique is on it. */
	private static void quick(ServerPlayer player, Live live) {
		// (0.17.46) the round-2 patch-2 techniques
		if (ZFighterArts.quick(player, live, ZFighterTraining.of(player).quick())) {
			return;
		}
		if (ZFighterCombat.quick(player, live)) {
			return;
		}
		switch (ZFighterTraining.of(player).quick()) {
			case ZFighterTraining.KI_DISC -> {
				// the arm goes up and the disc forms; it's thrown after the wind-up (ZFighterDisc)
				if (ZFighterDisc.start(player, live)) {
					cooldown(player, ZFighterState.BLAST, ZFighterDisc.WINDUP_TICKS + ZFighterDisc.RELEASE_TICK + ZFighterKi.DISC_COOLDOWN);
				}
			}
			case ZFighterTraining.HOMING_VOLLEY -> {
				if (ZFighterKi.volley(player, live)) {
					cooldown(player, ZFighterState.BLAST, ZFighterKi.VOLLEY_COOLDOWN);
				}
			}
			case ZFighterTraining.SOLAR_FLASH -> {
				if (ZFighterTechniques.flash(player, live)) {
					cooldown(player, ZFighterState.BLAST, ZFighterTechniques.FLASH_COOLDOWN);
				}
			}
			case ZFighterTraining.KI_BARRIER -> {
				if (ZFighterTechniques.barrier(player, live)) {
					cooldown(player, ZFighterState.BLAST, ZFighterTechniques.BARRIER_COOLDOWN);
				}
			}
			default -> {
				if (ZFighterKi.blast(player, live)) {
					cooldown(player, ZFighterState.BLAST, ZFighterKi.BLAST_COOLDOWN);
				}
			}
		}
	}

	/**
	 * Z alone does nothing on the server: flight is hold Z and scroll ({@link #fly}), sent by the client. Sneak+Z on the
	 * ground: a Warrior-born wraps his tail away or lets it out.
	 */
	@Override
	public void onMode(ServerPlayer player, boolean down) {
		// (0.17.50) a quick tap of Z in the god form: silver instinct
		if (ZFighterInstinct.tapped(player, live(player), down)) {
			return;
		}
		if (down && !player.isShiftKeyDown()) {
			ZFighterFusion.tappedZ(player);
		}
		if (down && player.isShiftKeyDown() && !live(player).flying) {
			ZFighterLineage.toggleTail(player);
			// (0.17.56) a Verdant's merging
			ZFighterVerdantMerge.press(player);
		}
	}

	/**
	 * From the client (hold Z and scroll): take off into pose {@code pose} (1-{@link #FLIGHT_POSES}), change pose, or come
	 * down (0). The client flies him; the server pays the ki and tells everyone how he holds his arms. A plain take-off
	 * lifts him gently (a puff of dust); {@code boost} (scrolled all the way up right after) is the burst of ki.
	 */
	static void fly(ServerPlayer player, int pose, boolean boost) {
		Live live = live(player);
		pose = Mth.clamp(pose, 0, FLIGHT_POSES);
		if (pose == 0) {
			if (live.flying) {
				endFlight(player, live);
			}
		} else if (!INSTANCE.slotUnlocked(player, "mode") || Powers.silenced(player) || ZFighterLineage.ape(player)
			|| !com.jaymospowers.power.wind.WindFlight.canFly(player)) {
			if (live.flying) {
				endFlight(player, live);
			}
		} else if (!live.flying && live.ki < 5.0F && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
			player.removeAttached(FLIGHT);
		} else {
			if (!live.flying) {
				live.flying = true;
				live.lastFlyPos = player.position();
				player.resetFallDistance();
				liftOff(player, live);
			}
			if (boost) {
				takeOffBurst(player, live);
			}
			player.setAttached(FLIGHT, pose);
		}
		sync(player, live, maxKi(player));
	}

	/** Puts him in the air already flying, hovering in pose {@code pose} with no take-off burst (a fusion swap mid-flight). */
	static void hoverNow(ServerPlayer player, int pose) {
		Live live = live(player);
		if (pose <= 0 || ZFighterLineage.ape(player) || !com.jaymospowers.power.wind.WindFlight.canFly(player)) {
			return;
		}
		live.flying = true;
		live.lastFlyPos = player.position();
		player.resetFallDistance();
		player.setAttached(FLIGHT, Mth.clamp(pose, 1, FLIGHT_POSES));
		sync(player, live, maxKi(player));
	}

	/** A gentle take-off (scrolled up partway): a soft puff of dust and a wisp of his aura as he lifts. */
	private static void liftOff(ServerPlayer player, Live live) {
		ServerLevel level = player.level();
		double x = player.getX(), y = player.getY(), z = player.getZ();
		for (int i = 0; i < 10; i++) {
			double a = i * Math.PI * 2.0 / 10.0;
			level.sendParticles(ParticleTypes.CLOUD, true, false, x + Math.cos(a) * 0.5, y + 0.05, z + Math.sin(a) * 0.5, 0, Math.cos(a), 0.0,
				Math.sin(a), 0.12);
		}
		level.sendParticles(dust(live), true, false, x, y + 0.5, z, 10, 0.3, 0.5, 0.3, 0.05);
		level.playSound(null, x, y, z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.4F, 1.2F);
	}

	/** The boost (scrolled all the way up): a burst of ki from where he stood (the client throws him up), a ring of dust and a flash of his aura. */
	private static void takeOffBurst(ServerPlayer player, Live live) {
		ServerLevel level = player.level();
		double x = player.getX(), y = player.getY(), z = player.getZ();
		DustParticleOptions dust = dust(live);
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2.0 / 24.0;
			level.sendParticles(ParticleTypes.CLOUD, true, false, x + Math.cos(a) * 0.6, y + 0.1, z + Math.sin(a) * 0.6, 0, Math.cos(a), 0.02,
				Math.sin(a), 0.45);
		}
		level.sendParticles(dust, true, false, x, y + 0.8, z, 40, 0.35, 0.9, 0.35, 0.25);
		level.sendParticles(ParticleTypes.EXPLOSION, true, false, x, y + 0.2, z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, x, y, z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
		level.playSound(null, x, y, z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.8F);
		com.jaymospowers.core.Shakes.heavy(player, new net.minecraft.world.phys.Vec3(x, y, z), 12.0, 0.7F);
	}

	/** The pose his arms hold in flight (0: not flying). Both sides. */
	public static int flightPose(Player player) {
		Integer pose = player.getAttached(FLIGHT);
		return pose == null ? 0 : pose;
	}

	/**
	 * C: up a form; sneak+C: down one. In Super 3 with the Spirit Bomb learned, C raises it instead. (0.17.194) Holding C
	 * is the form menu now (client), so C comes as a tap: one raises the Spirit Bomb, the next throws it.
	 */
	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		Live live = live(player);
		if (down && ZFighterLineage.ape(player)) {
			return;
		}
		if (down && !player.isShiftKeyDown() && ZFighterFusion.tryDance(player, live)) {
			// Z, then C: the fusion dance, not a transformation
			sync(player, live, maxKi(player));
			return;
		}
		if (!down) {
			return;
		}
		if (live.spiritFrom >= 0) {
			// (0.17.194) the second tap throws the raised Spirit Bomb; (0.17.196) and C is the forms' again
			ZFighterTechniques.release(player, live);
			live.bombPicked = false;
			sync(player, live, maxKi(player));
			return;
		}
		if (live.bombPicked && !player.isShiftKeyDown()) {
			// (0.17.196) Andy: the Spirit Bomb is on the hold-C menu, "only activated by the tap when selected"
			if (ZFighterTechniques.canRaise(player, live)) {
				ZFighterTechniques.raise(player, live);
				refresh(player, live);
			} else {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_spirit_wait").withStyle(ChatFormatting.GRAY));
			}
			sync(player, live, maxKi(player));
			return;
		}
		// (0.17.50) the newer forms: the god form, the legendary line, False Super
		if (ZFighterForms.ultimate(player, live)) {
			sync(player, live, maxKi(player));
			return;
		}
		int up = ApeRage.nextUp(player, live.form);
		// (0.17.196) the Spirit Bomb is no longer raised from the top of the line: it's picked on the hold-C menu
		if (player.isShiftKeyDown()) {
			if (live.superOn()) {
				descend(player, live);
			} else {
				// in base form, a corrupted fighter's sneak+C is Corrupted mode
				ZFighterCorruption.toggle(player, live);
			}
		} else if (up == 0) {
			if (!ZFighterBranchForms.ascend(player, live)) {
				// (0.17.91) Primal Super opened but out of reach: say why, not just "no higher form"
				String blocked = ApeRage.super4Blocked(player);
				if (live.form == ApeRage.PRIMAL_SUPER && blocked == null) {
					// (0.17.192) Super Primal Super needs a fusion with another Warrior-born; only a hint, nothing spelled out
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_super_primal_wait").withStyle(ChatFormatting.GRAY));
				} else {
					player.sendOverlayMessage(Component.translatable(blocked != null ? blocked : "message.jaymospowers.zfighter_no_higher").withStyle(ChatFormatting.GRAY));
				}
			}
		} else if (live.superOn() || ready(player, ZFighterState.SUPER_SLOT)) {
			int next = up;
			boolean free = player.isCreative() || ZFighterTraining.of(player).mastery(next) >= ZFighterTraining.FREE_ENTRY_MASTERY;
			if (!free && live.ki < FORM_ENTER_KI[next]) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
			} else {
				if (!free) {
					live.ki -= FORM_ENTER_KI[next];
				}
				transform(player, live, next);
			}
		}
		sync(player, live, maxKi(player));
	}

	// ---------------------------------------------------------------- Super and flight

	/** The transformation into {@code form}: a blast of gold that throws back everything near. */
	static void transform(ServerPlayer player, Live live, int form) {
		// (0.17.50) a legendary-born takes his own line in place of Super's (the breakthrough lands him in Legend 1)
		form = ZFighterLegendary.line(player, form);
		int tier = ZFighterForms.tier(form);
		live.form = form;
		live.formSince = player.level().getGameTime();
		if (live.limit > 0 && ZFighterTraining.of(player).mastery(form) < ZFighterTraining.NEXT_FORM_MASTERY) {
			// Limit Break only holds on a form mastered a quarter of the way
			endLimit(player, live);
		}
		ServerLevel level = player.level();
		Vec3 center = player.position();
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SUPER_BURST_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			Vec3 away = near.position().subtract(center);
			double distance = Math.max(0.5, away.length());
			if (distance > SUPER_BURST_RADIUS) {
				continue;
			}
			double strength = 1.6 * (1.0 - distance / (SUPER_BURST_RADIUS + 1.0));
			ZFighterKi.push(near, away, strength, 0.4);
		}
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE08A), true, true, center.x, center.y + 1.0, center.z, 1, 0.0,
			0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION, true, false, center.x, center.y + 0.5, center.z, 6, 1.2, 0.4, 1.2, 0.0);
		level.sendParticles(new DustParticleOptions(ZFighterLineage.color(live.lineage, form), 2.0F), true, false, center.x, center.y + 1.0, center.z, 40 + 20 * tier, 1.2,
			1.2, 1.2, 0.2);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.8F - 0.25F * tier);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7F, 1.4F);
		com.jaymospowers.core.Shakes.heavy(player, center, SUPER_SHAKE_RANGE, SUPER_SHAKE + SUPER_SHAKE_PER_FORM * tier);
		com.jaymospowers.core.UltimateFilm.play(player, com.jaymospowers.core.UltimateFilm.ORBIT, SUPER_FILM_TICKS, 0.0);
		if (live.lineage == ZFighterLineage.WARRIOR) {
			shout(player, tier);
		}
		refresh(player, live);
	}

	/**
	 * (0.15.59) Andy: "supers (warrior borns) exclaiming angrily with every form". A Warrior-born screams his way into each
	 * form: a furious yell (with a roar under it from Super 2 up) everyone near hears, and the words in their chat.
	 */
	private static void shout(ServerPlayer player, int form) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VINDICATOR_CELEBRATE, SoundSource.PLAYERS, 1.6F,
			0.75F - 0.05F * form);
		if (form >= 2) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.5F + 0.3F * form,
				1.5F - 0.15F * form);
		}
		Component said = Component.translatable("message.jaymospowers.zfighter_shout", player.getDisplayName(),
			Component.translatable("message.jaymospowers.zfighter_shout." + Math.clamp(form, 1, 3) + "." + random.nextInt(SHOUT_LINES)))
			.withStyle(ChatFormatting.GOLD);
		for (ServerPlayer near : level.players()) {
			if (near.distanceToSqr(player) <= SHOUT_RANGE * SHOUT_RANGE) {
				near.sendSystemMessage(said);
			}
		}
	}

	/** Down one form (to base from Super). */
	static void descend(ServerPlayer player, Live live) {
		// (0.17.50) out of a branch form first
		if (ZFighterBranchForms.descend(player, live)) {
			return;
		}
		if (live.form <= 1) {
			endSuper(player, live);
			return;
		}
		// (0.15.148) from Super 4 back to the highest form he bought
		// (0.17.190) Super Primal Super steps down to Primal Super, Primal Super to the highest form he bought
		live.form = live.form == ApeRage.SUPER_4 || live.form == ApeRage.PRIMAL_SUPER ? ApeRage.below(player, live.form) : live.form - 1;
		if (live.form <= 0) {
			endSuper(player, live);
			return;
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.5F);
		refresh(player, live);
	}

	/**
	 * (0.17.194) The hold-C menu's pick: straight to {@code form}, if it's one he has ({@link ZFighterForms#choices}). Up
	 * is a transformation (its ki, as C would ask); down is quiet, as sneak+C is; base ends the Super.
	 */
	static void pickForm(ServerPlayer player, int form) {
		Live live = live(player);
		List<Integer> choices = ZFighterForms.choices(player);
		if (!player.isAlive() || Powers.silenced(player) || live.spiritFrom >= 0) {
			return;
		}
		if (form == ZFighterForms.SPIRIT_BOMB_PICK) {
			// (0.17.196) the Spirit Bomb's tile: C now raises it (ZFighterForms.menu only offers it once he knows it)
			if (ZFighterTraining.of(player).knows(ZFighterTraining.SPIRIT_BOMB) && !live.bombPicked) {
				live.bombPicked = true;
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.4F);
				sync(player, live, maxKi(player));
			}
			return;
		}
		// any form's tile gives C back to the forms
		boolean was = live.bombPicked;
		live.bombPicked = false;
		if (form == live.form || !choices.contains(form)) {
			if (was) {
				sync(player, live, maxKi(player));
			}
			return;
		}
		if (live.form == ZFighterForms.GOD) {
			ZFighterInstinct.end(player, live, false);
		}
		if (form == 0) {
			endSuper(player, live);
		} else if (live.superOn() && choices.indexOf(form) < choices.indexOf(live.form)) {
			live.form = form;
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.5F);
			refresh(player, live);
		} else if (live.superOn() || ready(player, ZFighterState.SUPER_SLOT)) {
			if (form == ZFighterForms.GOD) {
				ZFighterGod.enter(player, live);
			} else {
				boolean free = player.isCreative() || ZFighterTraining.of(player).mastery(ZFighterLegendary.chain(form)) >= ZFighterTraining.FREE_ENTRY_MASTERY;
				if (!free && live.ki < FORM_ENTER_KI[form]) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
				} else {
					if (!free) {
						live.ki -= FORM_ENTER_KI[form];
					}
					transform(player, live, form);
				}
			}
		}
		sync(player, live, maxKi(player));
	}

	/** All the way back to base (out of ki, or by choice); Limit Break goes with it. */
	static void endSuper(ServerPlayer player, Live live) {
		if (!live.superOn()) {
			return;
		}
		live.form = 0;
		cooldown(player, ZFighterState.SUPER_SLOT, SUPER_COOLDOWN);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.3F);
		refresh(player, live);
	}

	/** Sneak+X: Limit Break up a tier, past the highest bought back to off. */
	static void cycleLimit(ServerPlayer player) {
		if (!player.isAlive() || Powers.silenced(player)) {
			return;
		}
		Live live = live(player);
		int bought = ZFighterTraining.of(player).limit();
		if (bought <= 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_limit").withStyle(ChatFormatting.GRAY));
			return;
		}
		int next = live.limit >= bought ? 0 : live.limit + 1;
		if (next > 0 && live.superOn() && ZFighterTraining.of(player).mastery(live.form) < ZFighterTraining.NEXT_FORM_MASTERY) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_limit_unmastered").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (next > 0 && player.getHealth() <= 4.0F) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_limit_hurt").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (next == 0) {
			endLimit(player, live);
		} else {
			live.limit = next;
			ServerLevel level = player.level();
			level.sendParticles(new DustParticleOptions(LIMIT_COLOR, 2.0F), true, false, player.getX(), player.getY() + 1.0, player.getZ(), 30,
				0.6, 0.9, 0.6, 0.15);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 0.6F + 0.2F * next);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_limit", next).withStyle(ChatFormatting.RED));
			refresh(player, live);
		}
		sync(player, live, maxKi(player));
	}

	static void endLimit(ServerPlayer player, Live live) {
		if (live.limit <= 0) {
			return;
		}
		live.limit = 0;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.2F);
		refresh(player, live);
	}

	static void endFlight(ServerPlayer player, Live live) {
		live.flying = false;
		live.lastFlyPos = null;
		player.removeAttached(FLIGHT);
		landFlight(player);
	}

	/** Takes the wings away (creative and spectator keep theirs). The fall from here is a real fall. */
	private static void landFlight(ServerPlayer player) {
		player.getAbilities().setFlyingSpeed(NORMAL_FLY_SPEED);
		if (!player.isCreative() && !player.isSpectator()) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
		}
		player.onUpdateAbilities();
	}

	/** A few motes of the aura round the player, rising (white-orange; gold when Super). Everyone sees it. */
	static void aura(ServerPlayer player, Live live, int count) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		DustParticleOptions dust = dust(live);
		for (int i = 0; i < count; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = 0.55 + 0.12 * live.tier() + random.nextDouble() * (0.25 + 0.1 * live.tier());
			double h = random.nextDouble() * player.getBbHeight();
			level.sendParticles(dust, true, false, player.getX() + Math.cos(a) * r, player.getY() + h, player.getZ() + Math.sin(a) * r, 0, 0.0, 1.0,
				0.0, 0.15 + 0.07 * live.tier());
		}
	}

	/**
	 * Settled in a form: a thin shell of finer motes out past his body, rising, a little thicker and faster each form (so
	 * the body, Super 4's fur and hair, stays in sight).
	 */
	static void settledAura(ServerPlayer player, Live live, int count) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		DustParticleOptions dust = new DustParticleOptions(auraColor(live), SETTLED_AURA_SIZE + 0.1F * live.tier());
		for (int i = 0; i < count; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = 0.75 + 0.08 * live.tier() + random.nextDouble() * 0.2;
			double h = random.nextDouble() * player.getBbHeight();
			level.sendParticles(dust, true, false, player.getX() + Math.cos(a) * r, player.getY() + h, player.getZ() + Math.sin(a) * r, 0, 0.0, 1.0,
				0.0, 0.12 + 0.06 * live.tier());
		}
	}

	/**
	 * (0.16.20) While he charges at full, the aura is set to hold {@link #AURA_HOLD_TICKS} from when he lets go; it ends
	 * then, or when his ki falls under {@link #AURA_HOLD_KI} of full. Synced only once he's let go (charging shows on its own).
	 */
	static void auraHold(ServerPlayer player, Live live, long now, int max) {
		// (0.17.133) the share of ki the aura's size follows, sent only when it moves a step
		int share = max <= 0 ? 0 : Mth.clamp(Math.round(KI_SHARE_STEPS * live.ki / max), 0, KI_SHARE_STEPS);
		Integer sent = player.getAttached(KI_SHARE);
		if ((sent == null ? 0 : sent) != share) {
			if (share == 0) {
				player.removeAttached(KI_SHARE);
			} else {
				player.setAttached(KI_SHARE, share);
			}
		}
		boolean full = live.ki >= max * AURA_HOLD_KI;
		if (live.charging) {
			live.auraHold = live.ki >= max ? now + AURA_HOLD_TICKS : -1L;
			return;
		}
		if (live.auraHold >= 0L && (now >= live.auraHold || !full || ZFighterLineage.ape(player))) {
			live.auraHold = -1L;
		}
		Long synced = player.getAttached(AURA_HOLD);
		if (live.auraHold < 0L) {
			if (synced != null) {
				player.removeAttached(AURA_HOLD);
			}
		} else if (synced == null || synced != live.auraHold) {
			player.setAttached(AURA_HOLD, live.auraHold);
		}
	}

	/** (0.15.59) His aura's step of power (0 to {@link #AURA_STEPS}): how far his max ki has grown past the start. */
	static int auraStep(int max) {
		return Mth.clamp((max - BASE_MAX_KI) / AURA_KI_STEP, 0, AURA_STEPS);
	}

	/** The aura's colour: his own ki colour in base form, the form's in a form, red in a rage. */
	static int auraColor(Live live) {
		if (live.silver) {
			return ZFighterInstinct.COLOR;
		}
		if (live.raging()) {
			return ZFighterBattle.RAGE_COLOR;
		}
		return live.form == 0 ? ZFighterKi.baseColor(live, ZFighterLineage.color(live.lineage, 0)) : ZFighterLineage.color(live.lineage, live.form);
	}

	/**
	 * (0.15.59) Base form, not charging: a faint aura of his ki colour hugging his outline, drifting up. The stronger he's
	 * grown, the more often and the bigger. Not while hidden (invisible) or as the Great Ape.
	 */
	private static void faintAura(ServerPlayer player, Live live, long now, int max) {
		if (player.isInvisible() || player.isSpectator() || ZFighterLineage.ape(player)) {
			return;
		}
		int step = auraStep(max);
		if (now % (FAINT_AURA_EVERY - step) != 0) {
			return;
		}
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		DustParticleOptions dust = new DustParticleOptions(auraColor(live), FAINT_AURA_SIZE + 0.12F * step);
		for (int i = 0; i < 1 + step / 2; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = 0.4 + random.nextDouble() * 0.15;
			double h = 0.1 + random.nextDouble() * (player.getBbHeight() - 0.1);
			level.sendParticles(dust, false, false, player.getX() + Math.cos(a) * r, player.getY() + h, player.getZ() + Math.sin(a) * r, 0, 0.0,
				1.0, 0.0, 0.03 + 0.01 * step);
		}
	}

	/**
	 * (0.15.59) Andy: "at max ki charge the aura flashes once with a explosion around the user kicking up debris particles
	 * and playing a blast/tnt sound". Charged to full: a flash of his aura, a blast round his feet, a ring of ki and the
	 * ground thrown out from under him, and the boom.
	 */
	static void fullBurst(ServerPlayer player, Live live, long now) {
		live.lastFullBurst = now;
		player.setAttached(KI_FULL, now);
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		Vec3 c = player.position();
		int color = auraColor(live);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | color), true, true, c.x, c.y + 1.0, c.z, 1, 0.0, 0.0,
			0.0, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION, true, false, c.x, c.y + 0.4, c.z, FULL_BURST_BLASTS, 1.0, 0.3, 1.0, 0.0);
		DustParticleOptions ring = new DustParticleOptions(color, 1.6F + 0.2F * live.tier());
		BlockState ground = groundUnder(player);
		BlockParticleOption bits = ground == null ? null : new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (int i = 0; i < FULL_BURST_RING; i++) {
			double a = Math.PI * 2.0 * i / FULL_BURST_RING;
			double cos = Math.cos(a), sin = Math.sin(a);
			level.sendParticles(ring, true, false, c.x + cos * 0.6, c.y + 0.3 + random.nextDouble() * 1.4, c.z + sin * 0.6, 0, cos, 0.05, sin,
				0.5 + random.nextDouble() * 0.3);
			level.sendParticles(ParticleTypes.POOF, true, false, c.x + cos * 0.5, c.y + 0.1, c.z + sin * 0.5, 0, cos, 0.0, sin,
				0.25 + random.nextDouble() * 0.15);
			if (bits != null) {
				// the ground torn up and thrown out
				level.sendParticles(bits, true, false, c.x + cos * 0.7, c.y + 0.1, c.z + sin * 0.7, 0, cos, 0.6 + random.nextDouble() * 0.4, sin,
					0.4 + random.nextDouble() * 0.3);
			}
		}
		debris(player, FULL_BURST_DEBRIS, FULL_BURST_RADIUS, 0.7 + 0.1 * live.tier(), true);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4F, 0.9F + 0.05F * live.tier());
		com.jaymospowers.core.Shakes.heavy(player, c, FULL_BURST_SHAKE_RANGE, FULL_BURST_SHAKE);
	}

	/** In a form near the ground: bits of the ground under him tear loose and rip upwards, more with each form. */
	private static void debris(ServerPlayer player, Live live) {
		debris(player, DEBRIS_PER_FORM * live.tier(), 1.2 + 0.8 * live.tier(), 0.4 + 0.25 * live.tier(), false);
	}

	/**
	 * Standing in a form: bits of the ground float round him, and while {@code poweringUp} lightning crackles over him and
	 * sparks (Super 4: fire) pour off him. Settled, only a spark or a lick of flame now and then, out at the aura's edge.
	 */
	private static void idleFx(ServerPlayer player, Live live, long now, boolean poweringUp) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		if (now % 2 == 0) {
			BlockState ground = groundUnder(player);
			if (ground != null) {
				BlockParticleOption bits = new BlockParticleOption(ParticleTypes.BLOCK, ground);
				for (int i = 0; i < IDLE_FLOAT_PER_FORM * live.tier(); i++) {
					// spawned at every height round him, each drifting gently up before it drops: they seem to hang there
					double a = random.nextDouble() * Math.PI * 2.0;
					double r = 0.8 + random.nextDouble() * (0.6 + 0.4 * live.tier());
					level.sendParticles(bits, true, false, player.getX() + Math.cos(a) * r, player.getY() + 0.1 + random.nextDouble() * 2.2,
						player.getZ() + Math.sin(a) * r, 0, 0.0, 1.0, 0.0, 0.08 + random.nextDouble() * 0.1);
				}
			}
		}
		if (!poweringUp) {
			settledFx(player, live, now, random);
			return;
		}
		if (random.nextInt(IDLE_CRACKLE) <= live.tier()) {
			crackle(player, random, live.tier() + 1);
		}
		if (now % Math.max(2, 8 - live.tier() * 2) == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, player.getX(), player.getY() + 1.0, player.getZ(),
				IDLE_SPARKS_PER_FORM * live.tier() + 1, 0.5, 0.7, 0.5, 0.15);
		}
		if (live.form == ApeRage.SUPER_4) {
			// (0.15.148) Andy: Super 4 "crackling with fire and sparks"
			level.sendParticles(ParticleTypes.FLAME, true, false, player.getX(), player.getY() + 1.0, player.getZ(), SUPER_4_FLAMES, 0.4, 0.8, 0.4,
				0.03);
			level.sendParticles(ParticleTypes.SMALL_FLAME, true, false, player.getX(), player.getY() + 1.1, player.getZ(), SUPER_4_FLAMES, 0.5, 0.9, 0.5,
				0.05);
			if (random.nextInt(SUPER_4_EMBER_ODDS) == 0) {
				level.sendParticles(ParticleTypes.LAVA, true, false, player.getX(), player.getY() + 1.2, player.getZ(), 1, 0.3, 0.5, 0.3, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6F, 0.9F);
			}
		} else if (live.form == ApeRage.PRIMAL_SUPER && now % PRIMAL_FLAME_EVERY == 0) {
			// (0.17.190) Andy: Primal Super's particles "far less and dissipate faster": a small flame now and then, no embers
			level.sendParticles(ParticleTypes.SMALL_FLAME, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.4, 0.7, 0.4, 0.01);
		}
	}

	/** Settled in a form: a spark per form now and then, and Super 4's fire licking up at the aura's edge, off his body. */
	private static void settledFx(ServerPlayer player, Live live, long now, RandomSource random) {
		ServerLevel level = player.level();
		if (random.nextInt(IDLE_CRACKLE * 3) < live.tier()) {
			crackle(player, random, live.tier());
		}
		if (now % SETTLED_SPARK_EVERY == 0) {
			for (int i = 0; i < live.tier(); i++) {
				double a = random.nextDouble() * Math.PI * 2.0, r = 0.8 + 0.1 * live.tier();
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, player.getX() + Math.cos(a) * r, player.getY() + random.nextDouble() * 2.0,
					player.getZ() + Math.sin(a) * r, 0, 0.0, 1.0, 0.0, 0.1);
			}
		}
		if (live.form == ApeRage.SUPER_4) {
			if (now % SETTLED_FLAME_EVERY == 0) {
				double a = random.nextDouble() * Math.PI * 2.0, r = 0.9 + random.nextDouble() * 0.3;
				level.sendParticles(random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, true, false, player.getX() + Math.cos(a) * r,
					player.getY() + 0.1 + random.nextDouble() * 1.6, player.getZ() + Math.sin(a) * r, 0, 0.0, 1.0, 0.0, 0.04);
			}
			if (random.nextInt(SUPER_4_EMBER_ODDS * 2) == 0) {
				level.sendParticles(ParticleTypes.LAVA, true, false, player.getX(), player.getY() + 1.2, player.getZ(), 1, 0.3, 0.5, 0.3, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.5F, 0.9F);
			}
		} else if (live.form == ApeRage.PRIMAL_SUPER && now % (SETTLED_FLAME_EVERY * PRIMAL_SETTLED_SLOWER) == 0) {
			double a = random.nextDouble() * Math.PI * 2.0, r = 0.8 + random.nextDouble() * 0.2;
			level.sendParticles(ParticleTypes.SMALL_FLAME, true, false, player.getX() + Math.cos(a) * r, player.getY() + 0.2 + random.nextDouble() * 1.4,
				player.getZ() + Math.sin(a) * r, 0, 0.0, 1.0, 0.0, 0.01);
		}
	}

	/** The solid block he stands over (within {@link #DEBRIS_HEIGHT}), or null. */
	private static BlockState groundUnder(ServerPlayer player) {
		BlockPos feet = player.blockPosition();
		for (int down = 1; down <= DEBRIS_HEIGHT; down++) {
			BlockState state = player.level().getBlockState(feet.below(down));
			if (!state.isAir() && state.getFluidState().isEmpty() && state.isSolidRender()) {
				return state;
			}
		}
		return null;
	}

	/**
	 * Charging ki: the ground breaks up and rises round him, small lightning crackles over him, and a smoky fog rolls out
	 * at his feet (from a Verdant, a pale mist). All of it harder in each form.
	 */
	private static void chargeFx(ServerPlayer player, Live live, long now) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		int tier = live.tier() + 1;
		if (now % CHARGE_DEBRIS_EVERY == 0) {
			debris(player, CHARGE_DEBRIS * tier, 1.0 + 0.6 * tier, 0.25 + 0.15 * tier, false);
		}
		if (random.nextInt(CHARGE_CRACKLE) < tier) {
			crackle(player, random, tier);
		}
		if (now % 2 == 0) {
			boolean mist = live.lineage == ZFighterLineage.VERDANT;
			for (int i = 0; i < CHARGE_FOG * tier; i++) {
				double a = random.nextDouble() * Math.PI * 2.0;
				double r = 0.3 + random.nextDouble() * 0.5;
				double speed = 0.04 + 0.025 * tier + random.nextDouble() * 0.03;
				level.sendParticles(mist ? ParticleTypes.CLOUD : ParticleTypes.LARGE_SMOKE, true, false, player.getX() + Math.cos(a) * r,
					player.getY() + 0.1 + random.nextDouble() * 0.2, player.getZ() + Math.sin(a) * r, 0, Math.cos(a), 0.02, Math.sin(a), speed);
			}
		}
	}

	/** A small fork of lightning over him: a jagged line of white motes, a spark where it ends. */
	private static void crackle(ServerPlayer player, RandomSource random, int tier) {
		ServerLevel level = player.level();
		DustParticleOptions bolt = new DustParticleOptions(CRACKLE_COLOR, 0.5F + 0.1F * tier);
		double a = random.nextDouble() * Math.PI * 2.0;
		double r = 0.5 + random.nextDouble() * (0.3 + 0.15 * tier);
		Vec3 at = new Vec3(player.getX() + Math.cos(a) * r, player.getY() + random.nextDouble() * player.getBbHeight(), player.getZ() + Math.sin(a) * r);
		Vec3 heading = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
		int segments = 3 + tier;
		for (int i = 0; i < segments; i++) {
			Vec3 next = at.add(heading.scale(0.25 + 0.05 * tier)).add((random.nextDouble() - 0.5) * 0.25, (random.nextDouble() - 0.5) * 0.25,
				(random.nextDouble() - 0.5) * 0.25);
			for (int k = 0; k < 2; k++) {
				Vec3 p = at.lerp(next, k / 2.0);
				level.sendParticles(bolt, true, false, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			at = next;
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.1);
		if (random.nextInt(4) == 0) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.12F + 0.04F * tier, 1.8F);
		}
	}

	/** Bits of the ground under him rising round him: {@code count} of them, {@code spread} blocks out, flung up at {@code lift}. */
	private static void debris(ServerPlayer player, int count, double spread, double lift, boolean smoke) {
		if (count <= 0) {
			return;
		}
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		BlockPos feet = player.blockPosition();
		BlockState ground = null;
		BlockPos groundPos = null;
		for (int down = 1; down <= DEBRIS_HEIGHT; down++) {
			BlockPos at = feet.below(down);
			BlockState state = level.getBlockState(at);
			if (!state.isAir() && state.getFluidState().isEmpty() && state.isSolidRender()) {
				ground = state;
				groundPos = at;
				break;
			}
		}
		if (ground == null) {
			return;
		}
		BlockParticleOption bits = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		double top = groundPos.getY() + 1.0;
		for (int i = 0; i < count; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = random.nextDouble() * spread;
			level.sendParticles(bits, true, false, player.getX() + Math.cos(a) * r, top + 0.05, player.getZ() + Math.sin(a) * r, 0, 0.0, 1.0, 0.0,
				lift + random.nextDouble() * 0.3);
		}
		if (smoke && random.nextInt(4) == 0) {
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, player.getX(), top, player.getZ(), 0, 0.0, 1.0, 0.0, 0.05);
		}
	}

	static DustParticleOptions dust(Live live) {
		if (live.raging()) {
			return new DustParticleOptions(ZFighterBattle.RAGE_COLOR, 1.5F);
		}
		// (0.15.59) in base form, his own ki colour
		return new DustParticleOptions(auraColor(live), live.superOn() ? 1.4F + 0.2F * live.tier() : 1.2F);
	}

	// ---------------------------------------------------------------- attributes

	/** Fists, Super, hunger and the charging stance, as attribute modifiers (only rewritten when they change). */
	static void refresh(ServerPlayer player, Live live) {
		boolean hungry = player.getFoodData().getFoodLevel() <= HUNGRY_FOOD && !player.isCreative();
		set(player, Attributes.ATTACK_DAMAGE, FIST_ID, fistBonus(player), AttributeModifier.Operation.ADD_VALUE);
		float power = 1.0F + MASTERY_POWER * ZFighterTraining.mastery(player, live.form);
		set(player, Attributes.ATTACK_DAMAGE, SUPER_FIST_ID, FORM_FIST[live.form] * power, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.ATTACK_DAMAGE, LIMIT_FIST_ID, LIMIT_FIST[live.limit], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.ATTACK_DAMAGE, RAGE_FIST_ID, live.raging() ? ZFighterBattle.RAGE_FIST : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.MOVEMENT_SPEED, LIMIT_SPEED_ID, LIMIT_SPEED[live.limit], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.MAX_HEALTH, VITALITY_ID, ZFighterTraining.of(player).stat(ZFighterTraining.VITALITY) * ZFighterTraining.VITALITY_PER_LEVEL,
			AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.ATTACK_DAMAGE, HUNGRY_ID, hungry ? -HUNGRY_WEAKNESS : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.MOVEMENT_SPEED, SLOW_ID, live.charging || live.guarding || live.waveFrom >= 0 || live.barrageFrom >= 0 || live.spiritFrom >= 0
			|| ZFighterDisc.throwing(player) || ZFighterWaveBeam.out(player) ? -CHARGE_SLOW : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.MOVEMENT_SPEED, SUPER_SPEED_ID, FORM_SPEED[live.form], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// lineages: a Verdant's Giant forms (his long arm is the stretch punch, RaceMoves); the Great Ape
		int lineage = ZFighterLineage.of(player);
		boolean ape = ZFighterLineage.ape(player);
		set(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, ape ? ZFighterLineage.apeReach(player) : 0.0, AttributeModifier.Operation.ADD_VALUE);
		double scale = live.apeSize > 0.0F ? ZFighterLineage.apeScale(live.apeSize)
			: lineage == ZFighterLineage.VERDANT ? ZFighterLineage.GIANT_SCALE[Math.min(live.form, ZFighterLineage.GIANT_SCALE.length - 1)] : 0.0;
		set(player, Attributes.SCALE, SCALE_ID, scale, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(player, Attributes.ATTACK_DAMAGE, APE_FIST_ID, ape ? ZFighterLineage.APE_FISTS : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.MAX_HEALTH, APE_HEALTH_ID, ape ? ZFighterLineage.APE_HEALTH : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.MOVEMENT_SPEED, APE_SPEED_ID, ape ? ZFighterLineage.APE_SPEED : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.JUMP_STRENGTH, APE_JUMP_ID, ape ? ZFighterLineage.APE_JUMP : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.GRAVITY, APE_GRAVITY_ID, ape ? -ZFighterLineage.APE_LIGHT : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.FALL_DAMAGE_MULTIPLIER, APE_FALL_ID, ape ? -1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterCorruption.attributes(player, live);
		ZFighterBranchForms.attributes(player, live);
		// fused: the partner's fists and health added, and a little more
		set(player, Attributes.ATTACK_DAMAGE, ZFighterFusion.FIST_ID, ZFighterFusion.fists(player), AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.ATTACK_DAMAGE, ZFighterFusion.BONUS_ID, ZFighterFusion.bonus(player), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.MAX_HEALTH, ZFighterFusion.HEALTH_ID, ZFighterFusion.health(player), AttributeModifier.Operation.ADD_VALUE);
		// (0.17.53) the five-way fusion's giant
		set(player, Attributes.SCALE, ZFighterFusion.SCALE_ID, ZFighterFusion.scale(player), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		// (0.17.50) the golden ape, the legendary line's bulk, False Super's flicker
		ZFighterForms.attributes(player, live);
	}

	/** What his bare fists add to a punch: mastery, every foe that ever beat him, ascension and trained Strength. */
	static double fistBonus(Player player) {
		return FIST_DAMAGE + FIST_PER_ABILITY * Mastery.unlocked(player) + FIST_PER_DEFEAT * (zenkai(player) + ZFighterWarriorComeback.bonus(player)) * ZFighterLineage.zenkaiFactor(player)
			+ (Fate.isAscended(player) ? ASCENDED_FIST : 0.0)
			+ ZFighterTraining.STRENGTH_PER_LEVEL * ZFighterTraining.of(player).stat(ZFighterTraining.STRENGTH)
			+ ZFighterBioForms.fists(player) + ZFighterRubberBody.fists(player);
	}

	static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0) {
			if (instance.getModifier(id) != null) {
				instance.removeModifier(id);
			}
			return;
		}
		AttributeModifier now = instance.getModifier(id);
		if (now == null || now.amount() != amount || now.operation() != operation) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	private static void remove(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	// ---------------------------------------------------------------- state

	/** (0.13.27) {@code player}'s ki colour (RGB), rolled now if he has none yet. */
	public static int kiColor(ServerPlayer player) {
		Integer color = player.getAttached(KI_COLOR);
		return color != null ? color : rollKiColor(player);
	}

	/** (0.13.27) A new ki colour for {@code player}, from {@link #KI_PALETTE}; returns it. */
	public static int rollKiColor(ServerPlayer player) {
		int color = KI_PALETTE[player.getRandom().nextInt(KI_PALETTE.length)];
		player.setAttached(KI_COLOR, color);
		return color;
	}

	static Live live(ServerPlayer player) {
		return LIVE.computeIfAbsent(player.getUUID(), id -> new Live());
	}

	/** Forgets the live state (a death: the next life starts with no ki, on the ground, not Super). */
	static void forget(ServerPlayer player) {
		LIVE.remove(player.getUUID());
	}

	public static ZFighterState state(Player player) {
		ZFighterState state = player.getAttached(STATE);
		return state == null ? ZFighterState.NONE : state;
	}

	static void set(ServerPlayer player, ZFighterState state) {
		player.setAttached(STATE, state);
	}

	static boolean ready(ServerPlayer player, int slot) {
		return state(player).left(slot, player.level().getGameTime()) <= 0;
	}

	static void cooldown(ServerPlayer player, int slot, int ticks) {
		set(player, state(player).withCooldown(slot, player.level().getGameTime(), ticks));
	}

	private static void sync(ServerPlayer player, Live live, int max) {
		int flags = (live.charging ? ZFighterState.CHARGING : 0) | (live.flying ? ZFighterState.FLYING : 0)
			| (live.superOn() ? ZFighterState.SUPER : 0) | (live.raging() ? ZFighterState.RAGE : 0) | (live.barrierTicks > 0 ? ZFighterState.BARRIER : 0)
			| (live.bombPicked ? ZFighterState.BOMB_PICKED : 0);
		int bomb = live.spiritFrom < 0 ? -1 : Math.round(100.0F * live.spiritEnergy / ZFighterTechniques.SPIRIT_MAX);
		int rage = live.raging() ? 100 : Math.round(100.0F * live.rage);
		int combo = player.level().getGameTime() - live.lastHit <= ZFighterBattle.COMBO_WINDOW ? live.combo : 0;
		Integer stance = player.getAttached(CHARGE);
		int wanted = live.charging ? live.form + 1 : 0;
		if ((stance == null ? 0 : stance) != wanted) {
			if (wanted == 0) {
				player.removeAttached(CHARGE);
			} else {
				player.setAttached(CHARGE, wanted);
			}
		}
		Integer shown = player.getAttached(FORM);
		if ((shown == null ? 0 : shown) != live.form) {
			if (live.form == 0) {
				player.removeAttached(FORM);
			} else {
				player.setAttached(FORM, live.form);
			}
		}
		ZFighterState state = state(player);
		int ki = Mth.floor(live.ki);
		if (!state.sameAs(ki, max, live.waveFrom, flags, combo, live.form, live.limit, bomb, rage)) {
			set(player, state.with(ki, max, live.waveFrom, flags, combo, live.form, live.limit, bomb, rage));
		}
	}

	/** Spends {@code cost} ki if he has it (Rage pays for everything); otherwise says so and spends nothing. */
	static boolean spend(ServerPlayer player, Live live, float cost) {
		if (player.isCreative() || live.raging()) {
			return true;
		}
		cost *= ZFighterAndroid.costFactor(player);
		// (0.17.56) a Frost-born's reserve or an Earthling's blood may make up what he lacks
		if (live.ki < cost && !ZFighterRaces.cover(player, live, cost)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
			return false;
		}
		live.ki -= cost;
		return true;
	}

	/** How many different foes have ever beaten him (up to {@link #ZENKAI_CAP}): the growth from defeat. */
	public static int zenkai(Player player) {
		return Math.min(ZENKAI_CAP, player.getAttachedOrElse(DEFEATS, List.of()).size());
	}

	public static int maxKi(Player player) {
		return BASE_MAX_KI + KI_PER_DEFEAT * (zenkai(player) + ZFighterWarriorComeback.bonus(player)) * ZFighterLineage.zenkaiFactor(player)
			+ ZFighterTraining.ENERGY_PER_LEVEL * ZFighterTraining.of(player).stat(ZFighterTraining.ENERGY)
			+ ZFighterVerdantMerge.ki(player) + ZFighterBioForms.ki(player)
			+ (player instanceof ServerPlayer server ? ZFighterFusion.ki(server) : 0);
	}

	public static int hardship(Player player) {
		return player.getAttachedOrElse(HARDSHIP, 0);
	}

	static void addHardship(ServerPlayer player, int amount) {
		if (amount > 0) {
			player.setAttached(HARDSHIP, hardship(player) + amount);
		}
	}

	/** Ki techniques: stronger with mastery, trained Ki Power, and the form he's in (more as it's mastered). */
	static float kiPower(ServerPlayer player, Live live) {
		float form = 1.0F + (FORM_KI[live.form] - 1.0F) * (1.0F + MASTERY_POWER * ZFighterTraining.mastery(player, live.form));
		float trained = 1.0F + ZFighterTraining.KI_POWER_PER_LEVEL * ZFighterTraining.of(player).stat(ZFighterTraining.KI_POWER);
		return ZFighterFusion.power(player, ((0.75F + 0.0625F * Mastery.unlocked(player)) * form + (Fate.isAscended(player) ? 0.1F : 0.0F)) * trained
			* ZFighterCorruption.kiFactor(player, live) * ZFighterBranchForms.kiFactor(player, live));
	}
}
