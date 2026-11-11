package com.jaymoskingdoms.client.films;

import com.geckolib.animatable.GeoEntity;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.hud.HudZones;
import com.jaymoskingdoms.films.FilmActor;
import com.jaymoskingdoms.films.LoreFilms;
import com.jaymoskingdoms.films.Moves;
import com.jaymoskingdoms.inhabitants.Draugr;
import com.jaymoskingdoms.inhabitants.Inhabitants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.math.Transformation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A lore film ({@link LoreFilms}) played on this game alone: a story staged in front of you with actors and a set only
 * your game sees, shot through letterbox bars with the HUD cleared and no words on screen (sounds only). Sneak held for a
 * second skips it.
 * <p>
 * <b>Recorded, then replayed</b> (Andy: "captured from within the engine, then replayed. Not drawn up every time"). A
 * film is staged once, in the game, with the tape running ({@code /kingdomfilm record <film>}): who stood where each
 * tick, every move started, every sound, particle, bolt and flying head goes onto a {@link FilmTape}. That tape ships
 * with the mod and is what everyone sees, the same performance every time, set down on whatever ground they stand on.
 * Only when there is no tape is the film staged afresh. Everything the staging does that can be seen or heard goes
 * through the few calls under "what a film can do", which do it and put it on the tape; the player does the same
 * calls back off it.
 * <p>
 * <b>The actors</b> are {@link FilmActor}s (and, for the Warrior-born, draugr drawn alive): GeckoLib bodies with elbows
 * and knees, moved by the humanoid move library ({@link Moves}). They walk, run, guard, swing, block, kneel and die by
 * its animations; nothing is posed by hand here.
 * <p>
 * <b>The Long Defence</b> (40 seconds), staged on the ground ahead of where you stand, as a memory: dim. Andy: "The
 * setting should be a fairly wooded forest, with a Keep off in the distance", and "the battle should be spanning the
 * entire shot, as it was large in scale and legendary". The forest and the Keep are block displays; the battle is fought
 * the whole film through, in every shot, by pairs who find a new foe when theirs falls.
 * <ol>
 * <li>The forest road at night, the Keep at its far end, and the fight already joined: hearthlanders against the
 * sorcerer's living host, orcs and Warrior-born (in the draugr's gear, as they were alive). "Several finishers going off
 * and blood, heads and limbs flying": beheadings, arms taken off, men run through, here and all through the film.</li>
 * <li>Along the line, close, as more of them go down.</li>
 * <li>The sorcerer behind his host (the Grue as it was, hooded, its eyes lit violet) raises both arms. "Lightning should
 * strike when the sorcerer summons his legion".</li>
 * <li>The dead come up out of the ground in black flame, dead orcs and zombified piglins, "in random groups at random
 * intervals not all at once or at all uniform": anywhere on the field, a few at a time, each at its own pace, for the
 * rest of the battle.</li>
 * <li>An orc charges Varro. A move of his own (Andy means to give play a like one, "so whatever he does will be exclusive
 * to him"): he slips inside the axe, catches the wrist and wrenches the axe out of its hand, kicks the knee from under
 * it, and with it on its knees takes its head in his hand and sets his ki mark on its brow ({@code InBalming}). It bows
 * its head, gets up, takes its axe back and turns on the dead for him.</li>
 * <li>The mages, each in turn, each in their own colour: Varro's fire, Thessaly's ward, Grimsby's spell-cut (he carries a
 * sword and casts with the other hand, bolts all battle long), Ambrose's light.</li>
 * <li>The fifth, the Quiet One, in a white hooded cloak that is alive (Andy: "the cloak takes form of his webbing and
 * tendrils, he uses them for mobility and combat alike ... moving as if it also has a mind of its own"), a rapier in
 * his hand. He runs at the sorcerer "darting back and forth through trees and past bolts of mana from the sorcerer",
 * the cloak throwing a tether at each tree ahead and hauling him round it, "flipping dodging off the trees". The
 * sorcerer's two guards come for him and the cloak deals with them itself ("using his cloak to attach to an orc
 * nearby almost autonomously"): a tether takes each and throws it aside without his breaking stride. "And he lunges,
 * but the sorcerer fades away". The dead drop where they stand, the host runs. Nobody says who he was.</li>
 * <li>The road, wide, the Keep still standing. Fade.</li>
 * </ol>
 * <b>The camera</b> (Andy: "consider the 180 degree rule and the rule of thirds") never crosses the battle line: every
 * shot is taken from the same side of it ({@code s > 0}), so the defenders are always on the left and going right, the
 * host on the right. Who a shot is about sits on a third line, not the middle ({@link #THIRD}).
 */
public final class LoreFilmClient {
	/**
	 * The film's beats (ticks): the fight on the road, along the line, the sorcerer's summons and its lightning, the dead
	 * rising, Varro and his orc, the spells, the fifth's run (chased, then from behind the sorcerer, then side on), the end.
	 */
	static final int MELEE = 90, SUMMON = 200, STRIKE = 222, RISING = 244, VARRO = 346, VARRO_CLOSE = 382, VARRO_FIELD = 420, SPELLS = 440,
		CAST_TICKS = 36, QUIET = 584, REVERSE = 636, END = 756, LENGTH = LoreFilms.FILM_TICKS;
	/** Varro's move, beat by beat: the orc runs in, raises its axe, chops; the disarm, the kick, its knees, his grip, the mark, its bow, it rises. */
	static final int ORC_CHARGE = 312, CHOP_UP = 358, CHOP = 362, DISARM = 366, KICK = 372, KNEES = 375, GRIP = 379, MARK_SET = 388, BOW = 402,
		MARK_RISE = 412;
	/** Fading in and out (ticks), and how long sneak is held to skip. */
	static final int FADE = 20, SKIP_HOLD = 20;
	/** The staging, in blocks ahead of where you stood ({@code f}) and to the side ({@code s}). */
	static final double MAGES_F = 8.0, SORCERER_F = 52.0, FIELD_NEAR = 11.0, FIELD_FAR = 31.0, FIELD_HALF = 25.0;
	static final double[] STRANGER_AT = {3.5, -4.5}, VARRO_AT = {8.0, 7.0}, MARKED_AT = {25.0, 8.5};
	/** How many fight on each side at the start (one pair each), and how many dead are raised. */
	static final int PAIRS = 26, DEAD = 52;
	/** Orcs are drawn bigger than a man (as BodySizes draws the real ones). */
	static final double ORC_SCALE = 1.12;

	// ---- the set ----
	/** The forest: how far it reaches behind, ahead and aside, how many trees, how far apart, and how near one a fighter can stand. */
	static final double FOREST_BACK = -16.0, FOREST_FRONT = 70.0, FOREST_HALF = 40.0, TREE_GAP = 4.6, TREE_BODY = 0.8;
	static final int TREES = 76;
	/**
	 * The forest burns. Andy: "the trees should also catch fire over the course of the battle and be in a blaze by the end,
	 * the surviving warriors rushing to put it out after the threat retreats". Lightning lights the trees within
	 * STRIKE_LIGHTS of where it lands, and a bolt of mana the tree it hits ({@link #igniteNear}: whatever else throws fire
	 * calls that too). A tree is full ablaze BURN_TICKS after it catches, and from SPREADS_FROM ablaze it lights another
	 * within SPREAD_REACH about every SPREAD_EVERY ticks; every EMBER_EVERY ticks the wind carries an ember from one burning
	 * tree to another within EMBER_REACH, so the whole forest is alight before the end. DOUSE_AFTER ticks after the sorcerer
	 * is gone, each defender still standing runs (DOUSE_RUN) to the nearest burning tree, no more than DOUSE_CREW to a tree,
	 * and beats it out: one man puts a full blaze out in DOUSE_TICKS, so by the film's end they have beaten a few down and
	 * the rest still burn.
	 */
	static final int BURN_TICKS = 110, SPREAD_EVERY = 60, EMBER_EVERY = 20, FLAMES = 4, DOUSE_AFTER = 18, DOUSE_TICKS = 160, DOUSE_CREW = 2, DOUSE_SWING = 9;
	static final double STRIKE_LIGHTS = 7.0, SPREAD_REACH = 9.0, EMBER_REACH = 18.0, SPREADS_FROM = 0.35, DOUSE_RUN = 0.3, DOUSE_STAND = 1.5;
	static final float FLAME_FOOT = 1.3F, FLAME_CROWN = 2.8F;
	/** The road to the Keep: kept clear of trees this far either side of the middle, up to where the woods close over it. */
	static final double ROAD_HALF = 3.6, ROAD_END = 36.0;
	/** The Keep: how far behind you its wall stands, and how far above the ground its foot is. */
	static final double KEEP_F = -62.0, KEEP_RISE = 2.0;
	static final float SET_VIEW_RANGE = 6.0F;

	// ---- the battle ----
	/** How fast fighters walk (blocks a tick), how near a foe they stand, and how far a defender will go to meet one. */
	static final double WALK = 0.14, DEAD_WALK = 0.11, FLEE = 0.24, REACH = 1.7, STAND_OFF = 1.4, DEFENDER_SEEK = 9.0;
	/** Blows: the shortest gap between one fighter's (ticks) and how much longer it may be. */
	static final int SWING_GAP = 10, SWING_SPREAD = 16;
	/** A finisher starts about once in this many ticks; a pair must have been at it this long; one takes this long. */
	static final int FINISH_EVERY = 11, FINISH_AFTER = 14, FINISH_TICKS = 24;
	/** While more defenders than this stand, one may lose a finisher (this often); below it they always win. */
	static final int DEFENDERS_KEPT = 12;
	static final float DEFENDER_LOSES = 0.4F;
	/** A finisher: the head off (on its knees first), the weapon arm off and then cut down, or run through. */
	static final int BEHEAD = 0, SEVER = 1, IMPALE = 2;
	/** How much lower a kneeling actor's head and shoulders are (blocks, times its scale), and how fast actors rise and sink. */
	static final double KNEEL = 0.375, SINK_RATE = 0.12;
	/** The cuts a fighter makes; how often the one cut at gets something up to it, and how long that keeps it from its own next blow. */
	static final String[] SWINGS = {"swing_across", "swing_down", "swing_back"};
	static final float BLOCKED = 0.3F;
	static final int BLOCK_TICKS = 12;
	/** Flying heads, limbs and Varro's orc's axe: gravity (blocks a tick, a tick), and how much of its speed one bounce keeps. */
	static final double GRAVITY = 0.045, BOUNCE = 0.35;
	/**
	 * The opening's finishers, where the first two shots see them: {f, s, kind, tick, 1 if the defender wins, 1 if the foe
	 * is a Warrior-born}.
	 */
	static final double[][] STAGED = {
		{27.0, 6.5, BEHEAD, 26, 1, 0}, {23.5, 11.0, SEVER, 50, 0, 1}, {22.0, 3.0, IMPALE, 72, 1, 1},
		{20.8, 4.5, BEHEAD, 116, 1, 1}, {21.8, 1.0, SEVER, 148, 1, 0}, {20.3, -2.5, IMPALE, 178, 0, 0}};

	// ---- the dead ----
	/** How far under the ground a hidden actor waits; the dead rise this fast (blocks a tick) or up to this much faster. */
	static final double HIDDEN = 3.2, RISE_RATE = 0.07, RISE_SPREAD = 0.12;
	/** The dead come up in groups of up to this many, this far apart at most (ticks), one group in this many under a lightning bolt. */
	static final int GROUP_MOST = 6, GROUP_GAP = 26, GROUP_LIGHTNING = 4;
	static final double GROUP_SPREAD = 2.3;
	/** The summons: where its three bolts land ({f, s}), and how long after {@link #STRIKE} each. */
	static final double[][] STRIKES = {{58.0, -7.0, 0}, {62.0, -14.0, 4}, {55.0, -2.5, 9}};

	// ---- Varro's orc ----
	/** How fast it runs at him and hunts after, and how far in front of where he stands it pulls up to chop. */
	static final double MARKED_RUN = 0.36, MARKED_HUNT = 0.2, MARKED_STOPS = 2.0;
	/** How long getting up off its knees takes it (the "rise" move, ticks). */
	static final int RISE_TICKS = 16;
	/** Each mage's working (Varro, Thessaly, Grimsby, Ambrose), a held move of the library. */
	static final String[] CASTS = {"cast_both", "cast_ward", "cast_blade", "cast_high"};
	/** How often Grimsby throws a bolt (ticks). */
	static final int BOLT_EVERY = 26;

	// ---- the fifth ----
	/** His run: where he starts, each turn of it ({f, s}), how fast he runs and lunges (blocks a tick), and where the lunge carries him. */
	static final double[][] QUIET_PATH = {{17.0, -1.0}, {22.0, -3.0}, {26.0, 2.5}, {30.0, -2.5}, {34.0, 3.0}, {38.0, -2.0}, {41.0, 0.0}};
	/**
	 * His last jump. Andy: "one of his last jumps should grab two trees ahead of him like a web slingshot, then he spirals
	 * through the air with the tendrils trailing him like a streamer, and they unfold before he makes his strike, he stays
	 * midair through the whole glide in slow motion, and almost makes contact before the sorcerer turns into the shadowflame".
	 * At the end of his run the cloak takes the two SLING_TREES (one each side of his way), he is drawn SLING_BACK back on
	 * them over SLING_TICKS (the move "sling"), and is let fly: GLIDE_TICKS in the air (the move "glide", which is that
	 * long: laid out head first and turning over, the cloak streaming, then level for the strike), to GLIDE_END short of
	 * the sorcerer, GLIDE_HIGH up and GLIDE_ARC more at the top. Two tethers reach the sorcerer GLIDE_REACH ticks before he
	 * does, and the sorcerer is gone GLIDE_MISS ticks before. The camera stays behind the sorcerer for the first GLIDE_CUT
	 * ticks of it. He comes down at GLIDE_DROP a tick and walks on (GLIDE_LAND) through the smoke.
	 */
	/** His cloak sets whole again this long after the sorcerer is gone (ticks). */
	static final int CLOAK_SETS = 14;
	static final double[][] SLING_TREES = {{45.0, 3.6}, {45.0, -3.6}};
	static final int SLING_TICKS = 12, GLIDE_TICKS = 54, GLIDE_CUT = 20, GLIDE_REACH = 8, GLIDE_MISS = 3;
	static final double SLING_BACK = 1.4, GLIDE_END = SORCERER_F - 1.3, GLIDE_HIGH = 0.6, GLIDE_ARC = 0.5, GLIDE_LAND = 0.3, GLIDE_DROP = 0.14;
	static final double QUIET_RUN = 0.5, LUNGE_END = 53.6;
	/** What he carries: the pack's rapier (Jaymo's Powers), or a sword where a game hasn't got it. */
	static final String QUIET_BLADE = "jaymospowers:iron_rapier|minecraft:iron_sword";
	/** The turns of his run he flips off the tree at, and the ones he rolls through. */
	static final int[] QUIET_FLIPS = {2, 5}, QUIET_ROLLS = {3};
	/** His cloak's tethers: the first of them swing him from tree to tree (thrown this far up the trunk); the rest hunt for themselves. */
	static final int SWING_TETHERS = 2;
	static final double TETHER_HIGH = 3.4;
	/**
	 * The sorcerer's guards: where each waits ({f, s}) and how far into the fifth's run it goes for him (ticks). The cloak
	 * has one once it's this near, holds it this long, then throws it this far aside at this speed, and it's down.
	 */
	static final double[][] GUARDS = {{43.0, 7.0}, {48.5, -5.5}};
	static final int[] GUARD_AT = {12, 44};
	static final double SNATCH_REACH = 4.4, THROWN = 5.0, THROWN_SPEED = 0.8;
	static final int SNATCH_HOLD = 5, THROWN_TICKS = 6;
	/** How far under the ground the sorcerer is put once he's gone. */
	static final double GONE_UNDER = 40.0;
	/** The sorcerer's bolts at him: how often (ticks), how fast (blocks a tick), and how near he gets before the sorcerer stops throwing. */
	static final int MANA_EVERY = 9;
	static final double MANA_SPEED = 2.2, MANA_UNTIL = 45.5;
	/** The chase camera runs this far behind him and to his side, its way kept clear of trees. */
	static final double CHASE_BACK = 3.0, CHASE_SIDE = 2.6;

	// ---- the tape ----
	/** An actor goes on the tape again once it has moved this far (blocks) or turned this much (degrees). */
	static final double TAPE_MOVED = 0.004, TAPE_TURNED = 0.25;
	/** Set (to true) on a game that stages the films: every film played there is staged afresh and taped, as {@code /kingdomfilm record} does. */
	static final String RECORD_PROPERTY = "jaymoskingdoms.filmRecord";

	// ---- the screen ----
	/** The dark over the screen: the memory's dim, and as the sorcerer calls. */
	static final float DIM = 0.3F, SUMMONING = 0.6F;
	static final float BAR_SHARE = 0.11F;
	/** The rule of thirds: how far the camera is turned off its subject (degrees) to set it on a third line. */
	static final float THIRD = 18.0F;
	/** The vanilla HUD hidden while a film plays. */
	private static final Identifier[] HIDDEN_HUD = {VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS};

	enum Role {
		SOLDIER, MAGE, STRANGER, SORCERER, DEAD, HOST, MARKED, GUARD
	}

	/** What a fighter is made of: what flies off it, and how its blood is drawn. */
	enum Flesh {
		MAN(null, Blocks.IRON_BLOCK, Blocks.IRON_BLOCK, false),
		WARRIOR(null, Blocks.IRON_BLOCK, Blocks.POLISHED_DEEPSLATE, false),
		ORC(Items.ZOMBIE_HEAD, Blocks.MOSS_BLOCK, Blocks.MOSS_BLOCK, false),
		DEAD_ORC(Items.ZOMBIE_HEAD, Blocks.MOSS_BLOCK, Blocks.MOSS_BLOCK, true),
		PIGLIN(Items.PIGLIN_HEAD, Blocks.CHERRY_PLANKS, Blocks.CHERRY_PLANKS, true);

		/** Its head as an item, or (helmed) as a block; its arm as a block; old blood, dark. */
		final @Nullable Item head;
		final Block helm, limb;
		final boolean dark;

		Flesh(@Nullable Item head, Block helm, Block limb, boolean dark) {
			this.head = head;
			this.helm = helm;
			this.limb = limb;
			this.dark = dark;
		}
	}

	/** One actor: its entity, where it stands on the stage and where it's headed, how far under the ground, whom it fights. */
	static final class Actor {
		final LivingEntity e;
		/** Its number in the cast (what a tape knows it by). */
		final int index;
		final Role role;
		Flesh flesh = Flesh.MAN;
		double f, s, toF, toS;
		double sink, sinkTo, sinkRate = SINK_RATE, speed = WALK, scale = 1.0;
		/** 0 the defenders, 1 the sorcerer's. */
		int team;
		/** Left to its own script (the battle doesn't move it). */
		boolean scripted;
		/** The burning tree it has gone to beat out. */
		@Nullable Fire dousing;
		@Nullable Actor foe, facing;
		int engagedAt = -1, nextSwing, busyUntil, stunUntil, riseAt = -1, bleedUntil, reservedUntil;
		double bleedUp;
		int fallAt = -1;
		boolean down, fled;
		/** Still coming up out of the ground (the dead); set down where it is this tick, not moved there. */
		boolean clawing, snap = true;
		float yaw, restYaw = Float.NaN;
		int gone;
		/** How it falls when it does (a move), or null for whichever way it goes. */
		@Nullable String death;
		/** What the tape last has of it: {f, s, up, the way it faces}. */
		final float[] taped = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};

		Actor(LivingEntity e, int index, Role role, double f, double s, float yaw) {
			this.e = e;
			this.index = index;
			this.role = role;
			this.f = this.toF = f;
			this.s = this.toS = s;
			this.yaw = yaw;
		}

		boolean up() {
			return !this.down && this.fallAt < 0 && this.sink < 0.5 && !this.fled;
		}
	}

	/** A finisher under way: who wins, who loses, how, and since when. */
	record Finish(Actor winner, Actor loser, int kind, int start) {
	}

	/** A group of the dead: when it comes up, where, and whether lightning comes with it. */
	record Rise(int tick, double f, double s, boolean lightning) {
	}

	/** Something sent flying (a head, an arm, an axe): a display moved by hand each tick until it comes to rest. */
	static final class Gib {
		final Display e;
		final int id;
		/** Lies where it first lands (the axe), not bounced. */
		final boolean flat;
		double x, y, z, vx, vy, vz;
		float turn, spin;
		boolean bounced, rest;

		Gib(Display e, int id, boolean flat, Vec3 at, Vec3 speed, float turn, float spin) {
			this.e = e;
			this.id = id;
			this.flat = flat;
			this.x = at.x;
			this.y = at.y;
			this.z = at.z;
			this.vx = speed.x;
			this.vy = speed.y;
			this.vz = speed.z;
			this.turn = turn;
			this.spin = spin;
		}
	}

	/** One of the sorcerer's bolts of mana: where it is, how it moves a tick, how long it has left, and whether it ends in a tree. */
	static final class Mana {
		Vec3 at;
		final Vec3 step;
		int left;
		final boolean tree;

		Mana(Vec3 at, Vec3 step, int left, boolean tree) {
			this.at = at;
			this.step = step;
			this.left = left;
			this.tree = tree;
		}
	}

	/** A film playing: the stage (where you stood, which way you faced), how far in, its actors and its set. */
	/** A tree on fire: its flames' numbers on the tape start at {@code id}; how far ablaze it is, and who is beating it out. */
	static final class Fire {
		final double[] tree;
		final int id;
		final Vec3[] at = new Vec3[FLAMES];
		double burn = 0.05;
		int crew, helpers;
		boolean out;

		Fire(double[] tree, int id) {
			this.tree = tree;
			this.id = id;
		}
	}

	static final class Film {
		final String name;
		final ClientLevel level;
		final Vec3 origin, fwd, right;
		final float yaw;
		final RandomSource rnd = RandomSource.create();
		/** The tape being made of this staging, or the one being played back (then nothing below is staged, only replayed). */
		@Nullable FilmTape rec, play;
		/** A played tape's particles, by their numbers on it. */
		ParticleOptions[] palette = new ParticleOptions[0];
		final List<Actor> actors = new ArrayList<>();
		/** Everyone in the battle: the defenders' line, the host, the dead (once up), Varro's orc (once marked). */
		final List<Actor> fighters = new ArrayList<>();
		/** The host and the dead: everything the mages strike at. */
		final List<Actor> legion = new ArrayList<>();
		final List<Actor> soldiers = new ArrayList<>();
		final Map<Entity, Actor> byEntity = new IdentityHashMap<>();
		/** The set and everything else that isn't an actor: trees, the Keep, lightning, what's sent flying. */
		final List<Entity> props = new ArrayList<>();
		/** The trees' trunks ({f, s}), and the ones the fifth turns at. */
		final List<double[]> trees = new ArrayList<>(), dartTrees = new ArrayList<>();
		final List<Finish> finishes = new ArrayList<>();
		final List<Rise> rises = new ArrayList<>();
		final List<Gib> gibs = new ArrayList<>();
		final List<Mana> mana = new ArrayList<>();
		/** The trees on fire, in the order they caught, and every flame by its number. */
		final List<Fire> fires = new ArrayList<>();
		final Map<double[], Fire> fireOf = new IdentityHashMap<>();
		final Map<Integer, Display.BlockDisplay> flames = new HashMap<>();
		@Nullable Actor grimsby, thessaly, ambrose, varro, stranger, sorcerer, marked;
		/** The sorcerer's guards, and when the fifth's cloak took hold of each (-1 until it has). */
		final @Nullable Actor[] guards = new Actor[GUARDS.length];
		final int[] snatchAt = new int[GUARDS.length];
		@Nullable Gib axe;
		@Nullable CameraType viewBefore;
		int tick, skip, quietAt = 1, lungeAt = -1, gibIds;
		/** When the fifth's slingshot took hold, when it let him fly, and where he stood when it took hold. */
		int slingAt = -1, glideAt = -1;
		double slingF, slingS;
		float flash, flashO;

		Film(String name, ClientLevel level, Vec3 origin, float yaw) {
			this.name = name;
			this.level = level;
			this.origin = origin;
			this.yaw = yaw;
			this.fwd = Vec3.directionFromRotation(0.0F, yaw);
			this.right = new Vec3(-this.fwd.z, 0.0, this.fwd.x);
			java.util.Arrays.fill(this.snatchAt, -1);
		}

		/** The stage point {@code f} ahead and {@code s} aside, {@code up} over the ground there. */
		Vec3 at(double f, double s, double up) {
			double x = this.origin.x + this.fwd.x * f + this.right.x * s, z = this.origin.z + this.fwd.z * f + this.right.z * s;
			return new Vec3(x, ground(x, z) + up, z);
		}

		/** {@code p} in the stage's measure: {f, s, up over the ground there}. */
		float[] staged(Vec3 p) {
			double x = p.x - this.origin.x, z = p.z - this.origin.z;
			return new float[] {(float)(x * this.fwd.x + z * this.fwd.z), (float)(x * this.right.x + z * this.right.z), (float)(p.y - ground(p.x, p.z))};
		}

		/** A speed in the stage's measure ({ahead, aside, up}), and back into the world's. */
		float[] stagedWay(Vec3 v) {
			return new float[] {(float)(v.x * this.fwd.x + v.z * this.fwd.z), (float)(v.x * this.right.x + v.z * this.right.z), (float)v.y};
		}

		Vec3 way(double ahead, double aside, double up) {
			return new Vec3(this.fwd.x * ahead + this.right.x * aside, up, this.fwd.z * ahead + this.right.z * aside);
		}

		/** The ground's height at {@code x, z}, looked for near the height you stood at (so a cave or a house has a floor). */
		double ground(double x, double z) {
			int top = Mth.floor(this.origin.y) + 6;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), top, Mth.floor(z));
			boolean airAbove = false;
			for (int y = top; y > top - 20; y--) {
				pos.setY(y);
				boolean solid = !this.level.getBlockState(pos).getCollisionShape(this.level, pos).isEmpty();
				if (solid && airAbove) {
					return y + 1;
				}
				airAbove = !solid;
			}
			return this.origin.y;
		}

		/** The way an actor faces to look along the stage's {@code (df, ds)} (degrees). */
		float yawOf(double df, double ds) {
			double x = this.fwd.x * df + this.right.x * ds, z = this.fwd.z * df + this.right.z * ds;
			return (float)Math.toDegrees(Math.atan2(-x, z));
		}
	}

	private static @Nullable Film film;
	private static int nextId = -1_700_000;
	private static float bars, barsO;

	private LoreFilmClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LoreFilms.Play.TYPE,
			(payload, context) -> context.client().execute(() -> start(context.client(), payload.film(), Boolean.getBoolean(RECORD_PROPERTY))));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!minecraft.isPaused()) {
				tick(minecraft);
			}
		});
		for (Identifier id : HIDDEN_HUD) {
			HudElementRegistry.replaceElement(id, old -> (graphics, delta) -> {
				if (film == null) {
					old.extractRenderState(graphics, delta);
				}
			});
		}
		HudElementRegistry.addLast(JaymosKingdoms.id("lore_film"), (HudElement)LoreFilmClient::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			film = null;
			bars = barsO = 0.0F;
		});
		// for whoever stages the films (creative only): play one here, or stage it afresh with the tape running
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal("kingdomfilm")
			.then(ClientCommands.literal("play").then(ClientCommands.argument("film", StringArgumentType.word())
				.executes(c -> command(c.getSource().getClient(), StringArgumentType.getString(c, "film"), false))))
			.then(ClientCommands.literal("record").then(ClientCommands.argument("film", StringArgumentType.word())
				.executes(c -> command(c.getSource().getClient(), StringArgumentType.getString(c, "film"), true))))));
	}

	private static int command(Minecraft minecraft, String name, boolean record) {
		if (minecraft.player == null || !minecraft.player.isCreative() || !LoreFilms.ALL.contains(name)) {
			return 0;
		}
		// (once the chat screen has closed)
		minecraft.execute(() -> start(minecraft, name, record));
		return 1;
	}

	/** Whether a film is playing (movement keys don't answer). */
	public static boolean locked() {
		return film != null;
	}

	// ---- staging ------------------------------------------------------------------------------------------------------------

	/**
	 * Plays {@code name} where you stand: off its tape (this game's own recording, else the one shipped), or staged
	 * afresh when there's none or {@code record} asks for a new tape to be made of it.
	 */
	private static void start(Minecraft minecraft, String name, boolean record) {
		if (!LoreFilms.LONG_DEFENCE.equals(name) || minecraft.level == null || minecraft.player == null) {
			return;
		}
		stop(minecraft);
		Film f = new Film(name, minecraft.level, minecraft.player.position(), minecraft.player.getYRot());
		f.viewBefore = minecraft.options.getCameraType();
		film = f;
		FilmTape tape = record ? null : FilmTape.find(name);
		if (tape != null && load(f, tape)) {
			return;
		}
		if (tape != null) {
			// (a tape that asked for something this game hasn't got: clear away what it had set up)
			stop(minecraft);
			f = new Film(name, minecraft.level, minecraft.player.position(), minecraft.player.getYRot());
			f.viewBefore = minecraft.options.getCameraType();
			film = f;
		}
		if (record) {
			f.rec = new FilmTape();
		}
		stage(f);
	}

	/** Sets up a tape's set and cast; false if it can't be. */
	private static boolean load(Film f, FilmTape tape) {
		f.play = tape;
		for (FilmTape.Box box : tape.set) {
			float[] at = box.at();
			box(f, block(box.block()), at[0], at[1], at[2], at[3], at[4], at[5], at[6], at[7], box.lit());
		}
		for (FilmTape.Cast c : tape.cast) {
			if (spawn(f, c) == null) {
				return false;
			}
		}
		f.palette = new ParticleOptions[tape.particles.size()];
		for (int i = 0; i < f.palette.length; i++) {
			f.palette[i] = particle(tape.particles.get(i));
		}
		f.varro = named(f, tape, "varro");
		f.thessaly = named(f, tape, "thessaly");
		f.grimsby = named(f, tape, "grimsby");
		f.ambrose = named(f, tape, "ambrose");
		f.stranger = named(f, tape, "stranger");
		f.sorcerer = named(f, tape, "sorcerer");
		f.marked = named(f, tape, "marked");
		f.glideAt = tape.named.getOrDefault(GLIDE_NAME, -1);
		return true;
	}

	/** What the tape keeps the tick the fifth is let fly under (the camera cuts by it), among its names. */
	static final String GLIDE_NAME = "#glide";

	private static @Nullable Actor named(Film f, FilmTape tape, String name) {
		Integer index = tape.named.get(name);
		return index == null || index < 0 || index >= f.actors.size() ? null : f.actors.get(index);
	}

	/** The Long Defence, staged afresh: the set, the cast, and the fight already joined. */
	private static void stage(Film f) {
		float facing = 0.0F, facingBack = 180.0F;
		forest(f);
		keep(f);
		// the four mages behind the fight, and the fifth off to one side
		f.grimsby = mage(f, "grimsby", FilmActor.WIDE, -7.0, Items.IRON_SWORD, null);
		f.thessaly = mage(f, "thessaly", FilmActor.SLIM, -2.5, null, null);
		f.ambrose = mage(f, "ambrose", FilmActor.WIDE, 2.5, Items.BLAZE_ROD, null);
		// (Varro's axe in his left hand: his right is the one he marks with)
		f.varro = mage(f, "varro", FilmActor.WIDE, VARRO_AT[1], null, Items.NETHERITE_AXE);
		if (f.varro != null) {
			f.varro.speed = 0.3;
		}
		// the Quiet One: the white cloak, and a rapier where the pack has one
		f.stranger = cast(f, new FilmTape.Cast(0, "stranger", FilmActor.WIDE, 0, true, Role.STRANGER.ordinal(), Flesh.MAN.ordinal(), false, QUIET_BLADE, "",
			new float[] {(float)STRANGER_AT[0], (float)STRANGER_AT[1], 0.0F, facing, 1.0F}));
		if (f.stranger != null) {
			f.stranger.restYaw = f.yaw + facing;
			f.stranger.speed = QUIET_RUN;
		}
		// the fight, already joined: a hearthlander and one of the host to each pair, all over the field
		List<double[]> spots = new ArrayList<>();
		for (double[] staged : STAGED) {
			spots.add(staged);
		}
		for (int tries = 0; spots.size() < PAIRS && tries < 600; tries++) {
			double[] at = {FIELD_NEAR + f.rnd.nextDouble() * (FIELD_FAR - FIELD_NEAR), (f.rnd.nextDouble() * 2.0 - 1.0) * FIELD_HALF};
			boolean free = !nearTree(f, at[0], at[1], 1.6) && distance(at[0], at[1], VARRO_AT[0], VARRO_AT[1]) > 5.0
				&& segment(at[0], at[1], MARKED_AT[0], MARKED_AT[1], VARRO_AT[0], VARRO_AT[1]) > 2.4;
			for (double[] other : spots) {
				free &= distance(at[0], at[1], other[0], other[1]) > 3.0;
			}
			if (free) {
				spots.add(at);
			}
		}
		for (int i = 0; i < spots.size(); i++) {
			double[] at = spots.get(i);
			boolean staged = i < STAGED.length;
			// each pair stands at its own angle, the defender on the near side
			double turn = staged ? 0.0 : (f.rnd.nextDouble() - 0.5) * 1.6, df = Math.cos(turn) * 0.8, ds = Math.sin(turn) * 0.8;
			Actor soldier = soldier(f, i, at[0] - df, at[1] - ds, facing);
			boolean warrior = staged ? at[5] > 0.0 : i % 5 >= 3;
			Actor foe = warrior ? warriorBorn(f, i, at[0] + df, at[1] + ds, facingBack)
				: orc(f, "orc_" + i % 3, i, Role.HOST, Flesh.ORC, i % 2 == 0 ? Items.IRON_AXE : Items.STONE_AXE, at[0] + df, at[1] + ds, 0.0, facingBack);
			if (foe == null || soldier == null) {
				continue;
			}
			foe.team = 1;
			foe.foe = soldier;
			soldier.foe = foe;
			foe.engagedAt = soldier.engagedAt = 0;
			foe.nextSwing = f.rnd.nextInt(SWING_GAP + SWING_SPREAD);
			soldier.nextSwing = f.rnd.nextInt(SWING_GAP + SWING_SPREAD);
			if (staged) {
				foe.reservedUntil = soldier.reservedUntil = (int)at[3] + 1;
			}
			f.legion.add(foe);
			f.fighters.add(foe);
		}
		// the dead, under the ground until their group's time: a few here, a few there, no two groups alike
		int at = RISING + 4;
		for (int i = 0; i < DEAD; ) {
			double gf = FIELD_NEAR + 2.0 + f.rnd.nextDouble() * (SORCERER_F - 6.0 - FIELD_NEAR - 2.0), gs = (f.rnd.nextDouble() * 2.0 - 1.0) * (FIELD_HALF + 1.0);
			int many = 1 + f.rnd.nextInt(GROUP_MOST);
			f.rises.add(new Rise(at, gf, gs, f.rnd.nextInt(GROUP_LIGHTNING) == 0));
			for (int j = 0; j < many && i < DEAD; j++, i++) {
				Actor a = dead(f, i, gf + spread(f, GROUP_SPREAD), gs + spread(f, GROUP_SPREAD), facingBack);
				if (a == null) {
					continue;
				}
				a.team = 1;
				a.speed = DEAD_WALK;
				a.sinkRate = RISE_RATE + f.rnd.nextDouble() * RISE_SPREAD;
				a.riseAt = at + f.rnd.nextInt(14);
				f.legion.add(a);
				f.fighters.add(a);
			}
			at += 3 + f.rnd.nextInt(GROUP_GAP);
		}
		// the orc Varro will mark, waiting its moment
		f.marked = orc(f, "orc_0", 0, Role.MARKED, Flesh.ORC, Items.IRON_AXE, MARKED_AT[0], MARKED_AT[1], 0.0, facingBack);
		if (f.marked != null) {
			f.marked.team = 1;
			f.marked.scripted = true;
			f.marked.speed = MARKED_RUN;
		}
		f.sorcerer = cast(f, new FilmTape.Cast(0, "sorcerer", FilmActor.WIDE, 0, false, Role.SORCERER.ordinal(), Flesh.MAN.ordinal(), false, "", "",
			new float[] {(float)SORCERER_F, 0.0F, 0.0F, facingBack, 1.0F}));
		if (f.sorcerer != null) {
			f.sorcerer.restYaw = f.yaw + facingBack;
		}
		// his guard: two orcs who stand by him until the fifth comes
		for (int i = 0; i < GUARDS.length; i++) {
			Actor guard = orc(f, "orc_" + (i + 1) % 3, i + 1, Role.GUARD, Flesh.ORC, Items.IRON_AXE, GUARDS[i][0], GUARDS[i][1], 0.0, facingBack);
			if (guard != null) {
				guard.team = 1;
				guard.scripted = true;
				guard.speed = MARKED_RUN;
				guard.restYaw = f.yaw + facingBack;
			}
			f.guards[i] = guard;
		}
		if (f.rec != null) {
			name(f, "varro", f.varro);
			name(f, "thessaly", f.thessaly);
			name(f, "grimsby", f.grimsby);
			name(f, "ambrose", f.ambrose);
			name(f, "stranger", f.stranger);
			name(f, "sorcerer", f.sorcerer);
			name(f, "marked", f.marked);
		}
		sound(f, SoundEvents.AMBIENT_CAVE.value(), 0.7F, 0.5F);
		sound(f, SoundEvents.RAID_HORN.value(), 1.0F, 0.5F);
	}

	private static void name(Film f, String name, @Nullable Actor a) {
		if (a != null && f.rec != null) {
			f.rec.named.put(name, a.index);
		}
	}

	/** One of the line: a hearthlander in plate, a sword or an axe, some with shields. */
	private static @Nullable Actor soldier(Film f, int i, double fwd, double side, float turn) {
		Actor a = cast(f, new FilmTape.Cast(0, "soldier_" + i % 3, FilmActor.WIDE, 0, false, Role.SOLDIER.ordinal(), Flesh.MAN.ordinal(), false,
			id(i % 3 == 1 ? Items.IRON_AXE : Items.IRON_SWORD), id(i % 2 == 0 ? Items.SHIELD : null), new float[] {(float)fwd, (float)side, 0.0F, turn, 1.0F}));
		if (a != null) {
			f.soldiers.add(a);
			f.fighters.add(a);
		}
		return a;
	}

	private static @Nullable Actor mage(Film f, String skin, int body, double s, @Nullable Item main, @Nullable Item off) {
		Actor a = cast(f, new FilmTape.Cast(0, skin, body, 0, false, Role.MAGE.ordinal(), Flesh.MAN.ordinal(), false, id(main), id(off),
			new float[] {(float)MAGES_F, (float)s, 0.0F, 0.0F, 1.0F}));
		if (a != null) {
			a.restYaw = f.yaw;
		}
		return a;
	}

	/** An orc (or a dead one): in its skin, bigger than a man, with one of the two orc heads (tusks, brow, ears). */
	private static @Nullable Actor orc(Film f, String skin, int n, Role role, Flesh flesh, @Nullable Item main, double fwd, double side, double up,
		float turn) {
		return cast(f, new FilmTape.Cast(0, skin, FilmActor.WIDE, 1 + n % 2, false, role.ordinal(), flesh.ordinal(), false, id(main), "",
			new float[] {(float)fwd, (float)side, (float)up, turn, (float)ORC_SCALE}));
	}

	/** A Warrior-born of the host: a draugr's helm, mail and fur, worn by the living man in better days (Andy). */
	private static @Nullable Actor warriorBorn(Film f, int i, double fwd, double side, float turn) {
		return cast(f, new FilmTape.Cast(1, "", 0, 0, false, Role.HOST.ordinal(), Flesh.WARRIOR.ordinal(), i % 3 == 0, "", "",
			new float[] {(float)fwd, (float)side, 0.0F, turn, 1.0F}));
	}

	/** One of the dead the sorcerer raises, waiting under the ground: dead orcs and zombified piglins, half and half. */
	private static @Nullable Actor dead(Film f, int i, double fwd, double side, float turn) {
		if (i % 2 == 0) {
			return orc(f, "dead_orc_" + (i / 2) % 3, i / 2, Role.DEAD, Flesh.DEAD_ORC, i % 6 == 0 ? Items.STONE_AXE : null, fwd, side, -HIDDEN, turn);
		}
		return cast(f, new FilmTape.Cast(0, "", FilmActor.PIGLIN, 0, false, Role.DEAD.ordinal(), Flesh.PIGLIN.ordinal(), false,
			id(i % 3 == 0 ? Items.IRON_SWORD : Items.GOLDEN_SWORD), "", new float[] {(float)fwd, (float)side, (float)-HIDDEN, turn, 1.0F}));
	}

	/** Casts {@code c} in the staging (and onto the tape being made of it). */
	private static @Nullable Actor cast(Film f, FilmTape.Cast c) {
		Actor a = spawn(f, c);
		if (a != null && f.rec != null) {
			f.rec.cast.add(c);
		}
		return a;
	}

	/** One of the cast, made and set on the stage as {@code c} has it (a staging and a tape alike). */
	private static @Nullable Actor spawn(Film f, FilmTape.Cast c) {
		Role role = Role.values()[Mth.clamp(c.role(), 0, Role.values().length - 1)];
		LivingEntity e;
		if (c.kind() == 1) {
			Draugr draugr = Inhabitants.DRAUGR.create(f.level, EntitySpawnReason.LOAD);
			if (draugr == null) {
				return null;
			}
			draugr.stageLiving(c.axe());
			draugr.stageGait(Moves.GUARD, Moves.RUN);
			e = draugr;
		} else {
			FilmActor actor = FilmActor.TYPE.create(f.level, EntitySpawnReason.LOAD);
			if (actor == null) {
				return null;
			}
			actor.skin = c.skin();
			actor.body = c.body();
			actor.orc = c.orc();
			actor.cloak = c.cloak();
			actor.still = switch (role) {
				case SOLDIER, HOST, MARKED, GUARD -> Moves.GUARD;
				case DEAD -> Moves.CLAW_UP;
				default -> Moves.IDLE;
			};
			actor.moving = switch (role) {
				case DEAD -> Moves.SHAMBLE;
				case STRANGER, MARKED, GUARD -> Moves.SPRINT;
				default -> Moves.RUN;
			};
			actor.setItemSlot(EquipmentSlot.MAINHAND, stack(c.main()));
			actor.setItemSlot(EquipmentSlot.OFFHAND, stack(c.off()));
			e = actor;
		}
		float[] at = c.at();
		var scale = e.getAttribute(Attributes.SCALE);
		if (scale != null && at[4] != 1.0F) {
			scale.setBaseValue(at[4]);
		}
		Actor a = new Actor(e, f.actors.size(), role, at[0], at[1], f.yaw + at[3]);
		a.flesh = Flesh.values()[Mth.clamp(c.flesh(), 0, Flesh.values().length - 1)];
		a.scale = at[4];
		a.sink = a.sinkTo = -at[2];
		a.clawing = role == Role.DEAD;
		e.setId(nextId--);
		place(f, a);
		f.level.addEntity(e);
		f.actors.add(a);
		f.byEntity.put(e, a);
		return a;
	}

	/** Sets {@code a}'s entity where the actor is and facing as it faces (moved there over the tick, or set down there at once). */
	private static void place(Film f, Actor a) {
		Vec3 at = f.at(a.f, a.s, -a.sink);
		LivingEntity e = a.e;
		if (a.snap) {
			e.snapTo(at.x, at.y, at.z, a.yaw, 0.0F);
			e.yBodyRotO = e.yHeadRotO = a.yaw;
			a.snap = false;
		} else {
			e.setPos(at.x, at.y, at.z);
		}
		e.setYRot(a.yaw);
		e.setYHeadRot(a.yaw);
		e.setYBodyRot(a.yaw);
		e.setRemainingFireTicks(0);
	}

	private static String id(@Nullable Item item) {
		return item == null ? "" : BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** {@code item} in hand: an item's id, or several with {@code |} between (the first this game has), or nothing. */
	private static ItemStack stack(String item) {
		for (String one : item.split("\\|")) {
			Identifier id = one.isEmpty() ? null : Identifier.tryParse(one);
			Item found = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			if (found != null) {
				return new ItemStack(found);
			}
		}
		return ItemStack.EMPTY;
	}

	private static Block block(String block) {
		Identifier id = Identifier.tryParse(block);
		return id == null ? Blocks.STONE : BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.STONE);
	}

	private static void stop(Minecraft minecraft) {
		Film f = film;
		if (f == null) {
			return;
		}
		film = null;
		if (minecraft.level == f.level) {
			for (Actor a : f.actors) {
				f.level.removeEntity(a.e.getId(), Entity.RemovalReason.DISCARDED);
			}
			for (Entity prop : f.props) {
				f.level.removeEntity(prop.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		if (f.viewBefore != null) {
			minecraft.options.setCameraType(f.viewBefore);
		}
	}

	// ---- what a film can do (each done here, and put on the tape when one is being made) ------------------------------------------

	private static final float[] NONE = {};

	private static void tape(Film f, byte type, int a, int b, float[] v, String s) {
		if (f.rec != null) {
			f.rec.frame(f.tick).events.add(new FilmTape.Event(type, a, b, v, s));
		}
	}

	/** {@code a} starts {@code move} (one of the library's one-off or held moves). */
	private static void act(Film f, Actor a, String move) {
		tape(f, FilmTape.ACT, a.index, 0, NONE, move);
		if (a.e instanceof GeoEntity geo) {
			geo.triggerAnim("action", move);
		}
	}

	/** {@code a} lets go of whatever move it holds, back to how it stands or goes. */
	private static void rest(Film f, Actor a) {
		tape(f, FilmTape.REST, a.index, 0, NONE, "");
		if (a.e instanceof GeoEntity geo) {
			geo.stopTriggeredAnim("action", null);
		}
	}

	/** The loops {@code a} stands and moves in from now on. */
	private static void gait(Film f, Actor a, String still, String moving) {
		tape(f, FilmTape.GAIT, a.index, 0, NONE, still + " " + moving);
		if (a.e instanceof FilmActor actor) {
			actor.still = still;
			actor.moving = moving;
		} else if (a.e instanceof Draugr draugr) {
			draugr.stageGait(still, moving);
		}
	}

	/** {@code item} (an id; none for an empty hand) in {@code a}'s hand. */
	private static void hold(Film f, Actor a, EquipmentSlot slot, String item) {
		tape(f, FilmTape.ITEM, a.index, slot.ordinal(), NONE, item);
		a.e.setItemSlot(slot, stack(item));
	}

	/** A head or an arm gone: not drawn from here on. */
	private static void maim(Film f, Actor a, int what) {
		tape(f, FilmTape.MAIM, a.index, what, NONE, "");
		a.gone |= what;
		if (a.e instanceof FilmActor actor) {
			actor.gone |= what;
		} else if (a.e instanceof Draugr draugr) {
			draugr.stageMaim(what == FilmActor.NO_HEAD ? Draugr.MAIM_HEAD : Draugr.MAIM_ARM);
		}
	}

	/** {@code a} flinches red. */
	private static void hurt(Film f, Actor a) {
		tape(f, FilmTape.HURT, a.index, 0, NONE, "");
		a.e.hurtTime = a.e.hurtDuration = 10;
	}

	private static void hide(Film f, Actor a) {
		tape(f, FilmTape.HIDE, a.index, 0, NONE, "");
		a.e.setInvisible(true);
	}

	/** Tether {@code which} of {@code a}'s cloak thrown at {@code to} (null: let go). */
	private static void tether(Film f, Actor a, int which, @Nullable Vec3 to) {
		tape(f, FilmTape.TETHER, a.index, which, to == null ? NONE : f.staged(to), "");
		if (a.e instanceof FilmActor actor) {
			actor.tether(which, to);
		}
	}

	private static void sound(Film f, SoundEvent sound, float pitch, float volume) {
		if (f.rec != null) {
			Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(sound);
			if (id != null) {
				tape(f, FilmTape.SOUND, 0, 0, new float[] {pitch, volume}, id.toString());
			}
		}
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	private static void particle(Film f, ParticleOptions type, Vec3 p, double vx, double vy, double vz) {
		if (f.rec != null) {
			float[] at = f.staged(p), way = f.stagedWay(new Vec3(vx, vy, vz));
			tape(f, FilmTape.PARTICLE, f.rec.particle(name(type)), 0, new float[] {at[0], at[1], at[2], way[0], way[1], way[2]}, "");
		}
		f.level.addParticle(type, true, true, p.x, p.y, p.z, vx, vy, vz);
	}

	/** A particle as a tape names it: its id, or a block's, or dust's colour and size. */
	private static String name(ParticleOptions type) {
		if (type instanceof BlockParticleOption block) {
			return "block " + BuiltInRegistries.BLOCK.getKey(block.getState().getBlock());
		}
		if (type instanceof DustParticleOptions dust) {
			Vector3f colour = dust.getColor();
			return "dust " + (ARGB.colorFromFloat(1.0F, colour.x, colour.y, colour.z) & 0xFFFFFF) + " " + dust.getScale();
		}
		return String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(type.getType()));
	}

	/** And back (smoke for one this game doesn't know). */
	private static ParticleOptions particle(String name) {
		String[] parts = name.split(" ");
		try {
			if (parts.length == 2 && parts[0].equals("block")) {
				return new BlockParticleOption(ParticleTypes.BLOCK, block(parts[1]).defaultBlockState());
			}
			if (parts.length == 3 && parts[0].equals("dust")) {
				return new DustParticleOptions(Integer.parseInt(parts[1]), Float.parseFloat(parts[2]));
			}
			Identifier id = Identifier.tryParse(name);
			if (id != null && BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null) instanceof ParticleOptions simple) {
				return simple;
			}
		} catch (NumberFormatException e) {
			// (falls through)
		}
		return ParticleTypes.SMOKE;
	}

	/** The screen lit, this much. */
	private static void flash(Film f, float much) {
		tape(f, FilmTape.FLASH, 0, 0, new float[] {much}, "");
		f.flash = Math.max(f.flash, much);
	}

	/** {@code a}'s cloak comes apart into its ribbons ({@code loose}), or winds back over it and sets whole. */
	private static void cloak(Film f, Actor a, boolean loose) {
		tape(f, FilmTape.CLOAK, a.index, loose ? 1 : 0, NONE, "");
		if (a.e instanceof FilmActor actor) {
			actor.cloak(loose);
		}
	}

	/** Flame {@code id} takes at the stage point {@code at}, turned {@code turn}: nothing to see until it's given a size. */
	private static void flame(Film f, int id, float[] at, float turn) {
		tape(f, FilmTape.IGNITE, id, 0, new float[] {at[0], at[1], at[2], turn}, "");
		Display.BlockDisplay e = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, f.level);
		e.setBlockState(Blocks.FIRE.defaultBlockState());
		e.setViewRange(SET_VIEW_RANGE);
		e.setBrightnessOverride(new Brightness(15, 15));
		e.setTransformation(new Transformation(new Vector3f(), null, new Vector3f(0.0F), null));
		Vec3 p = f.at(at[0], at[1], at[2]);
		e.setId(nextId--);
		e.snapTo(p.x, p.y, p.z, f.yaw + turn, 0.0F);
		f.level.addEntity(e);
		f.props.add(e);
		f.flames.put(id, e);
	}

	/** Flame {@code id} grows or shrinks to {@code size} across. */
	private static void flameSize(Film f, int id, float size) {
		tape(f, FilmTape.FLAME, id, 0, new float[] {size}, "");
		Display.BlockDisplay e = f.flames.get(id);
		if (e != null) {
			e.setTransformationInterpolationDuration(3);
			e.setTransformationInterpolationDelay(0);
			e.setTransformation(new Transformation(new Vector3f(-size / 2.0F, 0.0F, -size / 2.0F), null, new Vector3f(size, size * 1.25F, size), null));
		}
	}

	/** Flame {@code id} is out. */
	private static void quench(Film f, int id) {
		tape(f, FilmTape.QUENCH, id, 0, NONE, "");
		Display.BlockDisplay e = f.flames.remove(id);
		if (e != null) {
			f.level.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	/** A bolt of lightning at {@code (fwd, side)}: seen and heard on this game alone, and the screen lit by it. */
	private static void lightning(Film f, double fwd, double side) {
		tape(f, FilmTape.BOLT, 0, 0, new float[] {(float)fwd, (float)side}, "");
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(f.level, EntitySpawnReason.LOAD);
		if (bolt == null) {
			return;
		}
		bolt.setVisualOnly(true);
		Vec3 at = f.at(fwd, side, 0.0);
		bolt.setId(nextId--);
		bolt.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		f.level.addEntity(bolt);
		f.props.add(bolt);
		f.flash = Math.max(f.flash, 0.55F);
		if (f.play == null) {
			igniteNear(f, fwd, side, STRIKE_LIGHTS);
		}
	}

	/**
	 * Sends something flying from {@code at} at {@code speed}, turning over as it goes: an item ({@code what} its id, drawn
	 * {@code w} times its size) or a box of a block ({@code w} by {@code h} by {@code d}). {@code flat}: it lies where it lands.
	 */
	private static Gib fling(Film f, boolean item, String what, float w, float h, float d, Vec3 at, Vec3 speed, float spin, boolean flat) {
		float[] from = f.staged(at), way = f.stagedWay(speed);
		return fling(f, f.gibIds++, item, what, w, h, d, from, way, spin, f.rnd.nextFloat() * 360.0F, flat);
	}

	private static Gib fling(Film f, int id, boolean item, String what, float w, float h, float d, float[] from, float[] way, float spin, float turn,
		boolean flat) {
		tape(f, FilmTape.GIB, id, (item ? 1 : 0) | (flat ? 2 : 0), new float[] {from[0], from[1], from[2], way[0], way[1], way[2], spin, turn, w, h, d}, what);
		Display e;
		if (item) {
			Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, f.level);
			display.setItemStack(stack(what));
			display.setItemTransform(ItemDisplayContext.NONE);
			display.setTransformation(new Transformation(new Vector3f(), null, new Vector3f(w), null));
			e = display;
		} else {
			Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, f.level);
			display.setBlockState(block(what).defaultBlockState());
			display.setTransformation(new Transformation(new Vector3f(-w / 2.0F, -h / 2.0F, -d / 2.0F), null, new Vector3f(w, h, d), null));
			e = display;
		}
		Vec3 at = f.at(from[0], from[1], from[2]);
		e.setId(nextId--);
		e.snapTo(at.x, at.y, at.z, turn, 0.0F);
		f.level.addEntity(e);
		f.props.add(e);
		Gib gib = new Gib(e, id, flat, at, f.way(way[0], way[1], way[2]), turn, spin);
		f.gibs.add(gib);
		return gib;
	}

	/** Something sent flying is picked up again: gone. */
	private static void ungib(Film f, int id) {
		tape(f, FilmTape.UNGIB, id, 0, NONE, "");
		for (Iterator<Gib> it = f.gibs.iterator(); it.hasNext(); ) {
			Gib gib = it.next();
			if (gib.id == id) {
				f.level.removeEntity(gib.e.getId(), Entity.RemovalReason.DISCARDED);
				it.remove();
			}
		}
	}

	// ---- the tape ------------------------------------------------------------------------------------------------------------

	/** Puts where {@code a} is on the tape, if it has moved or turned since the tape last had it. */
	private static void record(Film f, Actor a, boolean snapped) {
		FilmTape tape = f.rec;
		if (tape == null) {
			return;
		}
		float up = (float)-a.sink, turn = Mth.wrapDegrees(a.yaw - f.yaw);
		float[] had = a.taped;
		if (!snapped && Math.abs(a.f - had[0]) <= TAPE_MOVED && Math.abs(a.s - had[1]) <= TAPE_MOVED && Math.abs(up - had[2]) <= TAPE_MOVED
			&& Math.abs(Mth.wrapDegrees(turn - had[3])) <= TAPE_TURNED) {
			return;
		}
		if (snapped) {
			tape(f, FilmTape.PLACE, a.index, 0, NONE, "");
		}
		had[0] = (float)a.f;
		had[1] = (float)a.s;
		had[2] = up;
		had[3] = turn;
		tape.frame(f.tick).moves.add(new float[] {a.index, had[0], had[1], up, turn});
	}

	/** One tick off the tape: what happened in it, then where everyone is. */
	private static void replay(Film f, FilmTape tape, int t) {
		if (t < tape.frames.size()) {
			FilmTape.Frame frame = tape.frames.get(t);
			for (FilmTape.Event e : frame.events) {
				happen(f, e);
			}
			for (float[] m : frame.moves) {
				int index = (int)m[0];
				if (index >= 0 && index < f.actors.size()) {
					Actor a = f.actors.get(index);
					a.f = m[1];
					a.s = m[2];
					a.sink = -m[3];
					a.yaw = f.yaw + m[4];
				}
			}
		}
		for (Actor a : f.actors) {
			place(f, a);
		}
	}

	private static void happen(Film f, FilmTape.Event e) {
		Actor a = e.a() >= 0 && e.a() < f.actors.size() ? f.actors.get(e.a()) : null;
		float[] v = e.v();
		switch (e.type()) {
			case FilmTape.ACT -> {
				if (a != null) {
					act(f, a, e.s());
				}
			}
			case FilmTape.REST -> {
				if (a != null) {
					rest(f, a);
				}
			}
			case FilmTape.GAIT -> {
				String[] loops = e.s().split(" ");
				if (a != null && loops.length == 2) {
					gait(f, a, loops[0], loops[1]);
				}
			}
			case FilmTape.ITEM -> {
				if (a != null && e.b() >= 0 && e.b() < EquipmentSlot.values().length) {
					hold(f, a, EquipmentSlot.values()[e.b()], e.s());
				}
			}
			case FilmTape.MAIM -> {
				if (a != null) {
					maim(f, a, e.b());
				}
			}
			case FilmTape.HURT -> {
				if (a != null) {
					hurt(f, a);
				}
			}
			case FilmTape.HIDE -> {
				if (a != null) {
					hide(f, a);
				}
			}
			case FilmTape.PLACE -> {
				if (a != null) {
					a.snap = true;
				}
			}
			case FilmTape.TETHER -> {
				if (a != null && e.b() >= 0 && e.b() < FilmActor.TETHERS) {
					tether(f, a, e.b(), v.length >= 3 ? f.at(v[0], v[1], v[2]) : null);
				}
			}
			case FilmTape.SOUND -> {
				Identifier id = Identifier.tryParse(e.s());
				SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
				if (sound != null && v.length >= 2) {
					sound(f, sound, v[0], v[1]);
				}
			}
			case FilmTape.PARTICLE -> {
				if (e.a() >= 0 && e.a() < f.palette.length && v.length >= 6) {
					Vec3 way = f.way(v[3], v[4], v[5]);
					particle(f, f.palette[e.a()], f.at(v[0], v[1], v[2]), way.x, way.y, way.z);
				}
			}
			case FilmTape.BOLT -> {
				if (v.length >= 2) {
					lightning(f, v[0], v[1]);
				}
			}
			case FilmTape.FLASH -> {
				if (v.length >= 1) {
					flash(f, v[0]);
				}
			}
			case FilmTape.GIB -> {
				if (v.length >= 11) {
					fling(f, e.a(), (e.b() & 1) != 0, e.s(), v[8], v[9], v[10], new float[] {v[0], v[1], v[2]}, new float[] {v[3], v[4], v[5]}, v[6], v[7],
						(e.b() & 2) != 0);
				}
			}
			case FilmTape.UNGIB -> ungib(f, e.a());
			case FilmTape.IGNITE -> {
				if (v.length >= 4) {
					flame(f, e.a(), new float[] {v[0], v[1], v[2]}, v[3]);
				}
			}
			case FilmTape.FLAME -> {
				if (v.length >= 1) {
					flameSize(f, e.a(), v[0]);
				}
			}
			case FilmTape.QUENCH -> quench(f, e.a());
			case FilmTape.CLOAK -> {
				if (a != null) {
					cloak(f, a, e.b() != 0);
				}
			}
			default -> {
			}
		}
	}

	// ---- the set ------------------------------------------------------------------------------------------------------------

	/**
	 * A box of {@code block}, {@code w} wide, {@code h} high and {@code d} deep, its foot centred on the stage point
	 * {@code (fwd, side)}, {@code up} over the ground at {@code (anchorF, anchorS)} (so a building's parts all stand on
	 * the one level): a block display, seen from far off.
	 */
	private static void box(Film f, Block block, double anchorF, double anchorS, double fwd, double side, double up, float w, float h, float d, boolean lit) {
		if (f.rec != null) {
			f.rec.set.add(new FilmTape.Box(BuiltInRegistries.BLOCK.getKey(block).toString(),
				new float[] {(float)anchorF, (float)anchorS, (float)fwd, (float)side, (float)up, w, h, d}, lit));
		}
		Display.BlockDisplay e = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, f.level);
		e.setBlockState(block.defaultBlockState());
		e.setTransformation(new Transformation(new Vector3f(-w / 2.0F, 0.0F, -d / 2.0F), null, new Vector3f(w, h, d), null));
		e.setViewRange(SET_VIEW_RANGE);
		if (lit) {
			e.setBrightnessOverride(new Brightness(15, 15));
		}
		Vec3 at = f.at(fwd, side, 0.0);
		e.setId(nextId--);
		e.snapTo(at.x, f.at(anchorF, anchorS, 0.0).y + up, at.z, f.yaw, 0.0F);
		f.level.addEntity(e);
		f.props.add(e);
	}

	/** The forest: the trees the fifth turns at, then the rest, off the road and out of the way of what's staged. */
	private static void forest(Film f) {
		for (int i = 1; i < QUIET_PATH.length - 1; i++) {
			double[] turn = QUIET_PATH[i];
			double[] trunk = {turn[0] + 0.9, turn[1] + Math.signum(turn[1]) * 0.2};
			f.dartTrees.add(trunk);
			tree(f, trunk[0], trunk[1], true);
		}
		for (double[] sling : SLING_TREES) {
			tree(f, sling[0], sling[1], true);
		}
		for (int tries = 0; f.trees.size() < TREES && tries < 1500; tries++) {
			double tf = FOREST_BACK + f.rnd.nextDouble() * (FOREST_FRONT - FOREST_BACK), ts = (f.rnd.nextDouble() * 2.0 - 1.0) * FOREST_HALF;
			if (clearing(tf, ts) || nearTree(f, tf, ts, TREE_GAP)) {
				continue;
			}
			tree(f, tf, ts, f.rnd.nextInt(4) != 0);
		}
	}

	/** Whether {@code (tf, ts)} is kept clear of trees: the road, where people stand and run, and where the camera goes and looks. */
	private static boolean clearing(double tf, double ts) {
		if (Math.abs(ts) < ROAD_HALF && tf < ROAD_END) {
			return true;
		}
		// the mages and the fifth; Varro's ground and his orc's run at him; the sorcerer and the lunge shot across him
		if (tf > -1.5 && tf < MAGES_F + 2.5 && Math.abs(ts) < 10.0) {
			return true;
		}
		if (distance(tf, ts, VARRO_AT[0], VARRO_AT[1]) < 4.4 || segment(tf, ts, MARKED_AT[0], MARKED_AT[1], VARRO_AT[0], VARRO_AT[1]) < 1.9
			|| segment(tf, ts, VARRO_AT[0] + 1.3, VARRO_AT[1], VARRO_AT[0] + 1.3, VARRO_AT[1] + 6.0) < 2.4) {
			return true;
		}
		if (distance(tf, ts, SORCERER_F, 0.0) < 3.6 || segment(tf, ts, 51.2, 0.0, 51.2, 8.5) < 2.8 || segment(tf, ts, SORCERER_F - 6.0, 2.6, SORCERER_F, 0.0) < 1.6) {
			return true;
		}
		for (double[] strike : STRIKES) {
			if (distance(tf, ts, strike[0], strike[1]) < 1.6) {
				return true;
			}
		}
		// the fifth's glide
		if (segment(tf, ts, QUIET_PATH[QUIET_PATH.length - 1][0] - SLING_BACK - 1.0, 0.0, SORCERER_F, 0.0) < 2.2) {
			return true;
		}
		// the fifth's run, and the camera's beside it
		for (int i = 0; i + 1 < QUIET_PATH.length; i++) {
			double[] a = QUIET_PATH[i], b = QUIET_PATH[i + 1];
			if (segment(tf, ts, a[0], a[1], b[0], b[1]) < 1.4
				|| segment(tf, ts, a[0] - CHASE_BACK, a[1] + CHASE_SIDE, b[0] - CHASE_BACK, b[1] + CHASE_SIDE) < 1.1) {
				return true;
			}
		}
		// the line Grimsby's cut runs down
		if (segment(tf, ts, 8.0, -7.0, 30.0, -7.0) < 1.5) {
			return true;
		}
		// the staged finishers, and the two shots that watch them
		for (double[] staged : STAGED) {
			if (distance(tf, ts, staged[0], staged[1]) < 2.0 || (staged[3] < MELEE && segment(tf, ts, 37.0, 5.5, staged[0], staged[1]) < 1.3)) {
				return true;
			}
		}
		// the road shots' camera, and its line down to the Keep
		return segment(tf, ts, 19.5, 14.0, 19.5, 3.6) < 2.2 || segment(tf, ts, 33.0, 5.5, 42.0, 6.2) < 3.3 || segment(tf, ts, 38.0, 5.6, FOREST_BACK, 2.8) < 2.5;
	}

	/** A tree at {@code (tf, ts)}: a spruce (a tall trunk, three tiers of needles) or an oak (two of leaves). */
	private static void tree(Film f, double tf, double ts, boolean spruce) {
		float tall = 7.0F + f.rnd.nextFloat() * 4.0F, girth = 0.8F + f.rnd.nextFloat() * 0.25F;
		// {f, s, where the crown starts, where it tops out, how far out it reaches}
		f.trees.add(spruce ? new double[] {tf, ts, tall - 3.2, tall + 2.8, 2.2} : new double[] {tf, ts, tall - 2.9, tall + 1.5, 2.6});
		if (spruce) {
			box(f, Blocks.SPRUCE_LOG, tf, ts, tf, ts, 0.0, girth, tall, girth, false);
			box(f, Blocks.SPRUCE_LEAVES, tf, ts, tf, ts, tall - 3.2, 4.4F, 2.0F, 4.4F, false);
			box(f, Blocks.SPRUCE_LEAVES, tf, ts, tf, ts, tall - 1.2, 3.0F, 2.0F, 3.0F, false);
			box(f, Blocks.SPRUCE_LEAVES, tf, ts, tf, ts, tall + 0.8, 1.6F, 2.0F, 1.6F, false);
		} else {
			tall -= 1.5F;
			box(f, Blocks.OAK_LOG, tf, ts, tf, ts, 0.0, girth, tall, girth, false);
			box(f, Blocks.OAK_LEAVES, tf, ts, tf, ts, tall - 1.4, 5.2F, 2.6F, 5.2F, false);
			box(f, Blocks.OAK_LEAVES, tf, ts, tf, ts, tall + 1.2, 3.2F, 1.8F, 3.2F, false);
		}
	}

	private static boolean nearTree(Film f, double tf, double ts, double within) {
		for (double[] tree : f.trees) {
			if (distance(tf, ts, tree[0], tree[1]) < within) {
				return true;
			}
		}
		return false;
	}

	/** The Keep at the far end of the road: a curtain wall, a gatehouse, two corner towers and the great tower behind, its windows lit. */
	private static void keep(Film f) {
		for (int i = -2; i <= 2; i++) {
			box(f, Blocks.STONE_BRICKS, KEEP_F, 0.0, KEEP_F, i * 8.0, KEEP_RISE, 8.0F, 8.0F, 3.0F, false);
			if (i != 0) {
				// merlons along the wall walk
				box(f, Blocks.STONE_BRICKS, KEEP_F, 0.0, KEEP_F, i * 8.0 - 2.0, KEEP_RISE + 8.0, 2.0F, 1.6F, 3.0F, false);
				box(f, Blocks.STONE_BRICKS, KEEP_F, 0.0, KEEP_F, i * 8.0 + 2.0, KEEP_RISE + 8.0, 2.0F, 1.6F, 3.0F, false);
			}
		}
		// the gatehouse, its gate, a torch either side
		box(f, Blocks.STONE_BRICKS, KEEP_F, 0.0, KEEP_F + 1.5, 0.0, KEEP_RISE, 9.0F, 12.0F, 5.0F, false);
		box(f, Blocks.COBBLESTONE, KEEP_F, 0.0, KEEP_F + 1.5, 0.0, KEEP_RISE + 12.0, 10.0F, 1.5F, 6.0F, false);
		box(f, Blocks.DARK_OAK_PLANKS, KEEP_F, 0.0, KEEP_F + 4.05, 0.0, KEEP_RISE, 4.0F, 5.5F, 0.3F, false);
		box(f, Blocks.OCHRE_FROGLIGHT, KEEP_F, 0.0, KEEP_F + 4.2, -3.2, KEEP_RISE + 4.0, 0.5F, 0.7F, 0.5F, true);
		box(f, Blocks.OCHRE_FROGLIGHT, KEEP_F, 0.0, KEEP_F + 4.2, 3.2, KEEP_RISE + 4.0, 0.5F, 0.7F, 0.5F, true);
		for (int side = -1; side <= 1; side += 2) {
			box(f, Blocks.STONE_BRICKS, KEEP_F, 0.0, KEEP_F, side * 21.0, KEEP_RISE, 7.0F, 15.0F, 7.0F, false);
			box(f, Blocks.COBBLESTONE, KEEP_F, 0.0, KEEP_F, side * 21.0, KEEP_RISE + 15.0, 8.2F, 1.4F, 8.2F, false);
			box(f, Blocks.OCHRE_FROGLIGHT, KEEP_F, 0.0, KEEP_F + 3.55, side * 21.0, KEEP_RISE + 10.5, 0.8F, 1.6F, 0.2F, true);
		}
		// the great tower, behind the wall and over it
		for (int i = -1; i <= 1; i += 2) {
			box(f, Blocks.STONE_BRICKS, KEEP_F, 0.0, KEEP_F - 12.0, i * 4.0, KEEP_RISE, 8.0F, 13.0F, 14.0F, false);
			box(f, Blocks.STONE_BRICKS, KEEP_F, 0.0, KEEP_F - 12.0, i * 4.0, KEEP_RISE + 13.0, 8.0F, 13.0F, 14.0F, false);
			box(f, Blocks.STONE_BRICKS, KEEP_F, 0.0, KEEP_F - 12.0, i * 6.5, KEEP_RISE + 28.0, 3.0F, 2.6F, 15.0F, false);
			box(f, Blocks.OCHRE_FROGLIGHT, KEEP_F, 0.0, KEEP_F - 4.95, i * 3.5, KEEP_RISE + 16.0, 0.9F, 2.0F, 0.2F, true);
			box(f, Blocks.OCHRE_FROGLIGHT, KEEP_F, 0.0, KEEP_F - 4.95, i * 4.5, KEEP_RISE + 22.0, 0.9F, 2.0F, 0.2F, true);
		}
		box(f, Blocks.COBBLESTONE, KEEP_F, 0.0, KEEP_F - 12.0, 0.0, KEEP_RISE + 26.0, 17.0F, 2.0F, 15.0F, false);
		box(f, Blocks.STONE_BRICKS, KEEP_F, 0.0, KEEP_F - 12.0, 0.0, KEEP_RISE + 28.0, 3.0F, 2.6F, 15.0F, false);
	}

	private static double distance(double f1, double s1, double f2, double s2) {
		return Math.sqrt((f1 - f2) * (f1 - f2) + (s1 - s2) * (s1 - s2));
	}

	/** How far {@code (pf, ps)} is from the line from {@code (af, as)} to {@code (bf, bs)}. */
	private static double segment(double pf, double ps, double af, double as, double bf, double bs) {
		double df = bf - af, ds = bs - as, length = df * df + ds * ds;
		double along = length < 1.0E-6 ? 0.0 : Mth.clamp(((pf - af) * df + (ps - as) * ds) / length, 0.0, 1.0);
		return distance(pf, ps, af + df * along, as + ds * along);
	}

	// ---- the film, tick by tick ---------------------------------------------------------------------------------------------

	private static void tick(Minecraft minecraft) {
		barsO = bars;
		Film f = film;
		bars += ((f != null ? 1.0F : 0.0F) - bars) * 0.25F;
		if (f == null) {
			return;
		}
		if (minecraft.level != f.level || minecraft.player == null) {
			// a new level (a dimension change, a respawn): its actors went with the old one; give the camera back
			film = null;
			if (f.viewBefore != null) {
				minecraft.options.setCameraType(f.viewBefore);
			}
			return;
		}
		if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		// (a tape being made is never cut short)
		f.skip = minecraft.options.keyShift.isDown() && f.rec == null ? f.skip + 1 : 0;
		if (f.skip == SKIP_HOLD && f.tick < LENGTH - FADE) {
			f.tick = LENGTH - FADE;
		}
		f.flashO = f.flash;
		f.flash *= 0.82F;
		int t = f.tick;
		if (f.play != null) {
			replay(f, f.play, t);
		} else {
			beats(f, t);
			battle(f, t);
			douse(f, t);
			fire(f, t);
			for (Iterator<Finish> it = f.finishes.iterator(); it.hasNext(); ) {
				if (finish(f, it.next(), t)) {
					it.remove();
				}
			}
			for (Actor a : f.actors) {
				boolean snapped = a.snap;
				move(f, a, t);
				record(f, a, snapped);
			}
		}
		things(f);
		if (++f.tick >= LENGTH) {
			if (f.rec != null) {
				try {
					f.rec.save(f.name);
					JaymosKingdoms.LOGGER.info("lore film {}: taped to {}", f.name, FilmTape.local(f.name));
				} catch (IOException e) {
					JaymosKingdoms.LOGGER.warn("lore film {}: its tape couldn't be saved", f.name, e);
				}
			}
			stop(minecraft);
		}
	}

	private static void beats(Film f, int t) {
		// the opening's finishers, where the camera is looking
		for (double[] staged : STAGED) {
			if (t == (int)staged[3]) {
				for (Actor a : f.soldiers) {
					if (a.reservedUntil == t + 1 && a.foe != null && a.up() && a.foe.up()) {
						boolean wins = staged[4] > 0.0;
						f.finishes.add(begin(f, wins ? a : a.foe, wins ? a.foe : a, (int)staged[2], t));
					}
				}
			}
		}
		summons(f, t);
		marking(f, t);
		// Grimsby, sword in one hand, throws bolts with the other, all battle long
		if (t > 20 && t < SPELLS && t % BOLT_EVERY == 0 && f.grimsby != null) {
			Actor d = target(f, f.grimsby, 16.0);
			if (d != null) {
				bolt(f, f.grimsby, d);
			}
		}
		// the spells, each mage in turn
		if (t >= SPELLS && t < QUIET) {
			int which = (t - SPELLS) / CAST_TICKS, into = (t - SPELLS) % CAST_TICKS;
			Actor mage = caster(f, which);
			if (mage != null) {
				spell(f, which, mage, into);
			}
		}
		fifth(f, t);
	}

	/** The sorcerer calls: the dark deepens, lightning comes down behind him, and from then on the dead come up in their groups. */
	private static void summons(Film f, int t) {
		Actor grue = f.sorcerer;
		if (grue == null) {
			return;
		}
		if (t == SUMMON + 4) {
			act(f, grue, "summon");
			sound(f, SoundEvents.EVOKER_PREPARE_SUMMON, 0.6F, 0.9F);
		}
		if (t >= SUMMON && t < RISING + 20) {
			for (int i = 0; i < 6; i++) {
				shadowFlame(f, SORCERER_F + spread(f, 2.2), spread(f, 2.2), 0.25);
			}
		}
		for (double[] strike : STRIKES) {
			if (t == STRIKE + (int)strike[2]) {
				lightning(f, strike[0], strike[1]);
			}
		}
		if (t == STRIKE) {
			sound(f, SoundEvents.WITHER_SPAWN, 0.6F, 0.45F);
		}
		if (t == RISING + 40) {
			rest(f, grue);
		}
		for (Rise rise : f.rises) {
			if (t >= rise.tick - 10 && t < rise.tick + 24) {
				for (int i = 0; i < 5; i++) {
					shadowFlame(f, rise.f + spread(f, GROUP_SPREAD + 0.6), rise.s + spread(f, GROUP_SPREAD + 0.6), 0.18);
				}
			}
			if (t == rise.tick) {
				sound(f, SoundEvents.SOUL_ESCAPE.value(), 0.6F + f.rnd.nextFloat() * 0.3F, 0.8F);
				if (rise.lightning) {
					lightning(f, rise.f + spread(f, 3.0), rise.s + spread(f, 3.0));
				}
			}
		}
		for (Actor a : f.legion) {
			if (a.riseAt == t && f.lungeAt < 0) {
				a.sinkTo = 0.0;
			}
		}
	}

	/** The mage casting {@code which} (0 Varro, 1 Thessaly, 2 Grimsby, 3 Ambrose). */
	private static @Nullable Actor caster(Film f, int which) {
		return switch (which) {
			case 0 -> f.varro;
			case 1 -> f.thessaly;
			case 2 -> f.grimsby;
			case 3 -> f.ambrose;
			default -> null;
		};
	}

	/** Where {@code which}'s spell lands. */
	private static Vec3 target(Film f, int which, Actor mage) {
		return switch (which) {
			case 0 -> f.at(21.0, mage.s - 1.0, 0.5);
			case 1 -> f.at(15.0, 0.0, 1.0);
			case 2 -> f.at(26.0, mage.s, 1.0);
			default -> f.at(26.0, 0.0, 0.0);
		};
	}

	/** One of the sorcerer's, up and not in a finisher, within {@code reach} of {@code of}'s side of the field, or null. */
	private static @Nullable Actor target(Film f, Actor of, double reach) {
		List<Actor> up = f.legion.stream().filter(a -> a.up() && f.tick >= a.busyUntil && f.tick >= a.reservedUntil && a.f < 32.0 && Math.abs(a.s - of.s) < reach).toList();
		return up.isEmpty() ? null : up.get(f.rnd.nextInt(up.size()));
	}

	/**
	 * Varro's own move, and the mark. The orc runs at him and chops; he slips inside it, catches the wrist, wrenches the
	 * axe away (it flies), kicks the knee from under it, takes its head in his hand as it kneels and sets his ki mark on
	 * its brow. It bows, rises, takes up its axe and turns on the dead. His side of it is one move of the library
	 * ("disarm"), the orc's another ("disarmed"); the beats here are when each part of them lands.
	 */
	private static void marking(Film f, int t) {
		Actor orc = f.marked, varro = f.varro;
		if (orc == null || varro == null) {
			return;
		}
		double kneelF = VARRO_AT[0] + MARKED_STOPS;
		if (t == ORC_CHARGE) {
			orc.toF = kneelF;
			orc.toS = VARRO_AT[1];
			varro.facing = orc;
		}
		if (t == CHOP_UP) {
			orc.facing = varro;
			act(f, orc, "overhead");
		}
		if (t == CHOP) {
			act(f, orc, "chop");
			sound(f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.8F);
			// inside the blow
			varro.toF = VARRO_AT[0] + 0.45;
			varro.toS = VARRO_AT[1] - 0.75;
			act(f, varro, "disarm");
		}
		if (t == DISARM) {
			act(f, orc, "disarmed");
			String axe = id(orc.e.getMainHandItem().getItem());
			hold(f, orc, EquipmentSlot.MAINHAND, "");
			Vec3 hand = orc.e.position().add(0.0, 1.3, 0.0).subtract(f.fwd.scale(0.6));
			f.axe = fling(f, true, axe, 1.0F, 1.0F, 1.0F, hand, f.fwd.scale(0.085).add(f.right.scale(0.075)).add(0.0, 0.42, 0.0), 38.0F, true);
			sound(f, SoundEvents.SHIELD_BLOCK.value(), 1.3F, 0.9F);
			sound(f, SoundEvents.ANVIL_LAND, 1.6F, 0.25F);
		}
		if (t == KICK) {
			sound(f, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.7F, 0.9F);
		}
		if (t == KNEES) {
			hurt(f, orc);
			sound(f, SoundEvents.ANVIL_LAND, 0.5F, 0.35F);
			for (int i = 0; i < 8; i++) {
				particle(f, ParticleTypes.CLOUD, orc.e.position().add(spread(f, 0.5), 0.1, spread(f, 0.5)), spread(f, 0.03), 0.01, spread(f, 0.03));
			}
		}
		if (t == GRIP) {
			// in front of it, his hand on its head, its face forced up to him
			varro.toF = kneelF - 1.1;
			varro.toS = VARRO_AT[1];
			act(f, orc, "kneel_up");
		}
		Vec3 brow = orc.e.position().add(0.0, (1.55 - KNEEL) * ORC_SCALE, 0.0).subtract(f.fwd.scale(0.3));
		if (t == MARK_SET) {
			// the ki mark burns on its brow
			sound(f, SoundEvents.FIRECHARGE_USE, 0.7F, 0.8F);
			sound(f, SoundEvents.BEACON_POWER_SELECT, 0.6F, 0.8F);
			flash(f, 0.14F);
			for (int i = 0; i < 24; i++) {
				double turn = i * Math.PI / 12.0;
				particle(f, i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.END_ROD, brow.add(f.right.scale(Math.cos(turn) * 0.35)).add(0.0, Math.sin(turn) * 0.35, 0.0),
					0.0, 0.01, 0.0);
			}
		}
		if (t > MARK_SET && t < MARK_RISE && t % 4 == 0) {
			particle(f, ParticleTypes.SMALL_FLAME, brow, 0.0, 0.005, 0.0);
		}
		if (t > MARK_RISE + 14 && t < QUIET && t % 4 == 0 && !orc.down) {
			particle(f, ParticleTypes.SMALL_FLAME, orc.e.position().add(0.0, 1.6 * ORC_SCALE, 0.0), 0.0, 0.005, 0.0);
		}
		if (t == BOW) {
			act(f, orc, "kneel_bow");
			rest(f, varro);
		}
		if (t == MARK_RISE) {
			// up, its axe taken back, and turned on the dead
			act(f, orc, "rise");
			act(f, varro, "point");
			gait(f, orc, Moves.GUARD, Moves.RUN);
			sound(f, SoundEvents.RAVAGER_ROAR, 0.9F, 0.7F);
		}
		if (t == MARK_RISE + RISE_TICKS) {
			orc.facing = null;
			orc.speed = MARKED_HUNT;
			if (f.axe != null) {
				float[] to = f.staged(new Vec3(f.axe.x, f.axe.y, f.axe.z));
				orc.toF = to[0];
				orc.toS = to[1];
			}
		}
		if (t > MARK_RISE + RISE_TICKS && orc.scripted && (t >= MARK_RISE + RISE_TICKS + 26 || distance(orc.f, orc.s, orc.toF, orc.toS) < 0.6)) {
			if (f.axe != null) {
				hold(f, orc, EquipmentSlot.MAINHAND, id(Items.IRON_AXE));
				ungib(f, f.axe.id);
				f.axe = null;
			}
			orc.scripted = false;
			orc.team = 0;
			f.fighters.add(orc);
		}
		if (t == VARRO_FIELD + 8) {
			rest(f, varro);
			varro.facing = null;
			varro.toF = VARRO_AT[0];
			varro.toS = VARRO_AT[1];
		}
	}

	/** Grimsby's bolt: off his free hand, a streak of light into {@code at}, which falls. */
	private static void bolt(Film f, Actor mage, Actor at) {
		act(f, mage, "cast_left");
		sound(f, SoundEvents.EVOKER_CAST_SPELL, 1.3F, 0.5F);
		Vec3 from = mage.e.position().add(0.0, 1.4, 0.0).add(f.right.scale(-0.4)), to = at.e.position().add(0.0, 1.1, 0.0);
		int steps = Math.max(8, (int)(from.distanceTo(to) * 2.0));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.lerp(to, i / (double)steps);
			particle(f, i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ENCHANTED_HIT, p, 0.0, 0.0, 0.0);
		}
		at.fallAt = f.tick + 2;
	}

	private static void spell(Film f, int which, Actor mage, int into) {
		// the working itself: called up, let go about as the camera cuts to where it lands, then held a moment
		if (into == 0) {
			act(f, mage, CASTS[which % CASTS.length]);
		}
		if (into == CAST_TICKS - 6) {
			rest(f, mage);
		}
		if (which == 2 && into < 12) {
			// Grimsby: glyphs gather and turn about him as he calls it up, his free hand raised
			if (into == 2) {
				sound(f, SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 0.8F);
			}
			for (int i = 0; i < 6; i++) {
				double turn = (into * 0.5 + i * Math.PI / 3.0);
				Vec3 p = mage.e.position().add(Math.cos(turn) * 1.3, 0.3 + into * 0.12, Math.sin(turn) * 1.3);
				particle(f, ParticleTypes.ENCHANT, p, 0.0, 0.0, 0.0);
				particle(f, ParticleTypes.ENCHANTED_HIT, p, 0.0, 0.02, 0.0);
			}
		}
		if (into < 12) {
			return;
		}
		Vec3 aim = target(f, which, mage);
		switch (which) {
			case 0 -> {
				// Varro: fire, thrown into the thick of them
				if (into == 12) {
					sound(f, SoundEvents.BLAZE_SHOOT, 0.8F, 0.9F);
					sound(f, SoundEvents.GENERIC_EXPLODE.value(), 1.1F, 0.6F);
					for (int i = 0; i < 70; i++) {
						particle(f, i % 6 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, aim.add(spread(f, 2.5), f.rnd.nextDouble(), spread(f, 2.5)), spread(f, 0.25),
							f.rnd.nextDouble() * 0.25, spread(f, 0.25));
					}
					fell(f, aim, 4.5, 0);
				}
			}
			case 1 -> {
				// Thessaly: a wall of light across the front, and the sorcerer's thrown back from it
				if (into == 12) {
					sound(f, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.0F);
					sound(f, SoundEvents.BEACON_ACTIVATE, 1.4F, 0.7F);
					for (Actor a : f.legion) {
						if (a.up() && f.tick >= a.busyUntil && a.f < 19.0) {
							a.toF = a.f + 4.0;
							a.toS = a.s;
							a.stunUntil = f.tick + 30;
							hurt(f, a);
							act(f, a, "stagger");
						}
					}
				}
				if (into < 30) {
					for (int i = 0; i < 22; i++) {
						Vec3 p = f.at(15.0, (f.rnd.nextDouble() * 2.0 - 1.0) * FIELD_HALF, f.rnd.nextDouble() * 3.0);
						particle(f, i % 3 == 0 ? ParticleTypes.ENCHANT : ParticleTypes.END_ROD, p, 0.0, 0.04, 0.0);
					}
				}
			}
			case 2 -> {
				// Grimsby: the spell let go down his blade, one cut of light that runs the length of the field
				int run = into - 12;
				if (run == 0) {
					sound(f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.0F);
					sound(f, SoundEvents.EVOKER_CAST_SPELL, 0.8F, 0.9F);
				}
				if (run < 14) {
					double along = MAGES_F + 3.0 + run * (SORCERER_F - 6.0 - MAGES_F - 3.0) / 14.0;
					Vec3 p = f.at(along, mage.s, 1.2);
					particle(f, ParticleTypes.SWEEP_ATTACK, p, 0.0, 0.0, 0.0);
					for (int i = 0; i < 4; i++) {
						particle(f, ParticleTypes.CRIT, p.add(spread(f, 1.0), spread(f, 0.5), spread(f, 1.0)), 0.0, 0.05, 0.0);
						particle(f, ParticleTypes.ENCHANTED_HIT, p.add(spread(f, 1.2), spread(f, 0.8), spread(f, 1.2)), 0.0, 0.03, 0.0);
					}
					for (Actor a : f.legion) {
						if (a.up() && f.tick >= a.busyUntil && Math.abs(a.f - along) < 1.6 && Math.abs(a.s - mage.s) < 2.6) {
							a.fallAt = f.tick;
						}
					}
				}
			}
			default -> {
				// Ambrose: day, for a breath
				if (into == 12) {
					sound(f, SoundEvents.TRIDENT_THUNDER.value(), 1.2F, 0.5F);
					sound(f, SoundEvents.BEACON_ACTIVATE, 2.0F, 1.0F);
					flash(f, 0.75F);
					fell(f, aim, 7.0, 6);
				}
				if (into < 24) {
					for (int i = 0; i < 20; i++) {
						particle(f, ParticleTypes.END_ROD, aim.add(spread(f, 2.0), f.rnd.nextDouble() * 14.0, spread(f, 2.0)), 0.0, -0.3, 0.0);
					}
				}
			}
		}
	}

	/** The sorcerer's within {@code reach} of {@code at} fall, over {@code spreadTicks}. */
	private static void fell(Film f, Vec3 at, double reach, int spreadTicks) {
		for (Actor a : f.legion) {
			if (a.up() && f.tick >= a.busyUntil && a.e.position().distanceToSqr(at.x, a.e.getY(), at.z) < reach * reach) {
				a.fallAt = f.tick + (spreadTicks > 0 ? f.rnd.nextInt(spreadTicks) : 0);
			}
		}
	}

	/**
	 * The fifth, the Quiet One. He runs at the sorcerer through the trees, his cloak throwing a tether at each tree
	 * ahead and hauling him round it, a flip off this trunk and a roll under that bolt, the sorcerer's bolts going by him
	 * and into the trunks. The sorcerer's guards come for him; the cloak takes each of them itself and throws it aside.
	 * He lunges, and the sorcerer isn't there. The dead drop, the host runs.
	 */
	private static void fifth(Film f, int t) {
		Actor quiet = f.stranger, grue = f.sorcerer;
		if (quiet == null || grue == null) {
			return;
		}
		if (t == QUIET) {
			// (from the edge of the fight: the cut hides the step)
			quiet.f = QUIET_PATH[0][0];
			quiet.s = QUIET_PATH[0][1];
			quiet.snap = true;
			quiet.toF = QUIET_PATH[1][0];
			quiet.toS = QUIET_PATH[1][1];
			quiet.restYaw = Float.NaN;
			grue.facing = quiet;
			swingTo(f, quiet, 1);
			// his cloak goes to work: it winds over him and comes apart
			cloak(f, quiet, true);
			sound(f, SoundEvents.WOOL_BREAK, 0.7F, 0.6F);
		}
		if (t < QUIET) {
			return;
		}
		if (f.slingAt < 0 && distance(quiet.f, quiet.s, quiet.toF, quiet.toS) < 0.3) {
			if (f.quietAt + 1 < QUIET_PATH.length) {
				// round this tree: off its trunk, or under what's coming
				for (int flip : QUIET_FLIPS) {
					if (flip == f.quietAt) {
						act(f, quiet, "tree_flip");
						sound(f, SoundEvents.WOOD_HIT, 0.8F, 0.6F);
					}
				}
				for (int roll : QUIET_ROLLS) {
					if (roll == f.quietAt) {
						act(f, quiet, "dodge_roll");
					}
				}
				f.quietAt++;
				quiet.toF = QUIET_PATH[f.quietAt][0];
				quiet.toS = QUIET_PATH[f.quietAt][1];
				swingTo(f, quiet, f.quietAt);
			} else {
				// the slingshot: the cloak takes a tree either side of his way and draws him back on them
				f.slingAt = t;
				f.slingF = quiet.f;
				f.slingS = quiet.s;
				quiet.restYaw = f.yaw;
				for (int i = 0; i < FilmActor.TETHERS; i++) {
					tether(f, quiet, i, i < SLING_TREES.length ? f.at(SLING_TREES[i][0], SLING_TREES[i][1], TETHER_HIGH) : null);
				}
				act(f, quiet, "sling");
				sound(f, SoundEvents.FISHING_BOBBER_THROW, 0.6F, 0.8F);
				sound(f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
			}
		}
		glide(f, quiet, t);
		if (f.slingAt < 0) {
			guards(f, quiet, t);
		}
		// the sorcerer's bolts
		if (f.glideAt < 0 && quiet.f < MANA_UNTIL && t > QUIET + 6 && (t - QUIET) % MANA_EVERY == 0) {
			boolean left = (t - QUIET) / MANA_EVERY % 2 == 0;
			act(f, grue, left ? "cast_left" : "cast_right");
			sound(f, SoundEvents.EVOKER_CAST_SPELL, 0.5F, 0.7F);
			Vec3 from = grue.e.position().add(0.0, 1.5, 0.0).subtract(f.fwd.scale(0.6));
			Vec3 to = null;
			boolean tree = false;
			for (double[] trunk : f.dartTrees) {
				if (trunk[0] > quiet.f + 0.5 && trunk[0] < quiet.f + 5.5 && f.rnd.nextInt(3) != 0) {
					// into the trunk he's just gone behind
					to = f.at(trunk[0] + 0.45, trunk[1], 1.2 + f.rnd.nextDouble() * 0.8);
					tree = true;
					break;
				}
			}
			if (to == null) {
				// where he was headed, before he turned
				double flight = (grue.f - quiet.f) / MANA_SPEED;
				double df = quiet.toF - quiet.f, ds = quiet.toS - quiet.s, d = Math.max(0.01, Math.sqrt(df * df + ds * ds));
				to = f.at(quiet.f + df / d * QUIET_RUN * flight * 1.6, quiet.s + ds / d * QUIET_RUN * flight * 1.6 + spread(f, 0.8), 0.3);
			}
			int ticks = Math.max(2, (int)Math.ceil(from.distanceTo(to) / MANA_SPEED));
			f.mana.add(new Mana(from, to.subtract(from).scale(1.0 / ticks), ticks, tree));
		}
		if (f.lungeAt < 0 && quiet.f > SORCERER_F - 7.0) {
			// he knows: the shadow comes up round him
			for (int i = 0; i < 9; i++) {
				shadowFlame(f, SORCERER_F + spread(f, 0.9), spread(f, 0.9), 0.2);
			}
		}
		if (f.lungeAt < 0) {
			return;
		}
		int since = t - f.lungeAt;
		if (since < 26) {
			// what's left of him: smoke where he stood, thinning
			for (int i = 0; i < Math.max(1, 12 - since / 2); i++) {
				Vec3 p = f.at(SORCERER_F + spread(f, 0.7), spread(f, 0.7), f.rnd.nextDouble() * 2.0);
				particle(f, f.rnd.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SQUID_INK, p, spread(f, 0.04), 0.03, spread(f, 0.04));
				if (i % 4 == 0) {
					particle(f, ParticleTypes.WITCH, p, 0.0, 0.05, 0.0);
				}
			}
		}
		if (since == 3) {
			hide(f, grue);
			grue.fled = true;
			grue.sink = grue.sinkTo = GONE_UNDER;
			sound(f, SoundEvents.SOUL_ESCAPE.value(), 0.5F, 1.0F);
			sound(f, SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 0.3F);
			// the dead with nothing holding them up, and the host with no one to hold it
			for (Actor a : f.legion) {
				if (!a.up() && a.sink < 0.5) {
					continue;
				}
				if (a.role == Role.DEAD) {
					a.sinkTo = a.sink;
					if (a.sink < 0.5) {
						a.death = "death_drop";
						a.fallAt = t + 2 + f.rnd.nextInt(16);
					}
				} else if (t >= a.busyUntil) {
					rout(f, a);
				}
			}
			for (Actor guard : f.guards) {
				if (guard != null && guard.up()) {
					rout(f, guard);
				}
			}
		}
		if (since == CLOAK_SETS) {
			// and done: it winds back over him and sets whole
			cloak(f, quiet, false);
			sound(f, SoundEvents.WOOL_PLACE, 0.7F, 0.6F);
		}
		if (since == 8) {
			act(f, quiet, "look_about");
			quiet.speed = 0.08;
		}
	}

	/** The fifth drawn back on his slingshot, let fly, and in the air to the sorcerer; then set down past where he stood. */
	private static void glide(Film f, Actor quiet, int t) {
		if (f.slingAt < 0) {
			return;
		}
		if (f.glideAt < 0) {
			float back = smooth((t - f.slingAt) / (float)SLING_TICKS);
			quiet.f = quiet.toF = f.slingF - SLING_BACK * back;
			quiet.s = quiet.toS = f.slingS * (1.0 - back);
			if (t >= f.slingAt + SLING_TICKS) {
				f.glideAt = t;
				if (f.rec != null) {
					f.rec.named.put(GLIDE_NAME, t);
				}
				act(f, quiet, "glide");
				sound(f, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.7F, 0.7F);
				sound(f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 0.9F);
			}
			return;
		}
		int into = t - f.glideAt;
		if (into > GLIDE_TICKS) {
			// down, and on through the smoke
			quiet.sink = quiet.sinkTo = Math.min(0.0, quiet.sink + GLIDE_DROP);
			return;
		}
		double g = into / (double)GLIDE_TICKS;
		quiet.f = quiet.toF = Mth.lerp(g, f.slingF - SLING_BACK, GLIDE_END);
		quiet.s = quiet.toS = 0.0;
		quiet.sink = quiet.sinkTo = -(GLIDE_HIGH * smooth((float)(g / 0.15)) + GLIDE_ARC * Math.sin(g * Math.PI));
		if (into == 4) {
			// let go of the trees: the cloak streams behind
			tether(f, quiet, 0, null);
			tether(f, quiet, 1, null);
		}
		if (into == GLIDE_TICKS - GLIDE_REACH) {
			// the cloak gets there before the blade, and closes on smoke
			Vec3 where = f.at(SORCERER_F, 0.0, 1.1);
			tether(f, quiet, 2, where);
			tether(f, quiet, 3, where);
			sound(f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.0F);
		}
		if (into == GLIDE_TICKS - GLIDE_MISS) {
			// a hand's breadth off, and nobody there
			f.lungeAt = t;
		}
		if (into == GLIDE_TICKS) {
			tether(f, quiet, 2, null);
			tether(f, quiet, 3, null);
			quiet.speed = GLIDE_LAND;
			quiet.toF = LUNGE_END;
		}
	}

	/** One of the host runs for it. */
	private static void rout(Film f, Actor a) {
		a.fled = true;
		a.foe = null;
		a.facing = null;
		a.restYaw = Float.NaN;
		a.speed = FLEE;
		a.toF = a.f + 34.0;
		a.toS = a.s * 1.3;
		gait(f, a, Moves.GUARD, Moves.FLEE);
	}

	/** The fifth's cloak throws a tether at the tree he turns at next (turn {@code k} of his run), and lets go of the last. */
	private static void swingTo(Film f, Actor quiet, int k) {
		tether(f, quiet, (k + 1) % SWING_TETHERS, null);
		if (k - 1 < f.dartTrees.size()) {
			double[] trunk = f.dartTrees.get(k - 1);
			tether(f, quiet, k % SWING_TETHERS, f.at(trunk[0], trunk[1], TETHER_HIGH));
			sound(f, SoundEvents.FISHING_BOBBER_THROW, 0.7F, 0.5F);
		}
	}

	/**
	 * The sorcerer's guards and the cloak. Each guard goes for the fifth in its turn; once it's near, a tether of the
	 * cloak has it by the chest (it reels), holds it a moment, and throws it across his path and down. He never looks.
	 */
	private static void guards(Film f, Actor quiet, int t) {
		for (int i = 0; i < f.guards.length; i++) {
			Actor guard = f.guards[i];
			if (guard == null || guard.down || guard.fled) {
				continue;
			}
			int which = SWING_TETHERS + i % (FilmActor.TETHERS - SWING_TETHERS), since = f.snatchAt[i] < 0 ? -1 : t - f.snatchAt[i];
			Vec3 chest = guard.e.position().add(0.0, 1.15 * guard.scale, 0.0);
			if (since < 0) {
				if (f.lungeAt >= 0 || t < QUIET + GUARD_AT[i]) {
					continue;
				}
				// at him
				guard.restYaw = Float.NaN;
				guard.facing = quiet;
				guard.toF = quiet.f + 1.5;
				guard.toS = quiet.s;
				if (distance(guard.f, guard.s, quiet.f, quiet.s) < SNATCH_REACH) {
					f.snatchAt[i] = t;
					guard.toF = guard.f;
					guard.toS = guard.s;
					tether(f, quiet, which, chest);
					act(f, guard, "stagger");
					hurt(f, guard);
					sound(f, SoundEvents.FISHING_BOBBER_THROW, 0.6F, 0.8F);
					sound(f, SoundEvents.WOOL_HIT, 0.6F, 1.0F);
				}
				continue;
			}
			if (since <= SNATCH_HOLD + THROWN_TICKS) {
				// (the tether stays on it as he runs on and it goes)
				tether(f, quiet, which, chest);
			}
			if (since == SNATCH_HOLD) {
				// thrown: across his path, away from where it came from
				guard.facing = null;
				guard.speed = THROWN_SPEED;
				guard.toF = guard.f - 1.5;
				guard.toS = guard.s - Math.signum(GUARDS[i][1]) * THROWN;
				guard.death = "death_back";
				guard.fallAt = t + THROWN_TICKS;
				act(f, guard, "stagger");
				sound(f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 0.9F);
			}
			if (since == SNATCH_HOLD + THROWN_TICKS) {
				tether(f, quiet, which, null);
				sound(f, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.6F, 0.8F);
			}
		}
	}

	// ---- the fire -----------------------------------------------------------------------------------------------------------

	/** Fire lands at {@code (fwd, side)}: every tree within {@code reach} catches, or the nearest one within twice that. */
	private static void igniteNear(Film f, double fwd, double side, double reach) {
		double[] nearest = null;
		double near = reach * 2.0;
		boolean any = false;
		for (double[] tree : f.trees) {
			double d = distance(tree[0], tree[1], fwd, side);
			if (d < reach) {
				ignite(f, tree);
				any = true;
			} else if (d < near && !f.fireOf.containsKey(tree)) {
				near = d;
				nearest = tree;
			}
		}
		if (!any && nearest != null) {
			ignite(f, nearest);
		}
	}

	/** {@code tree} catches: one flame at its foot, the rest in its crown. */
	private static void ignite(Film f, double[] tree) {
		if (f.fireOf.containsKey(tree)) {
			return;
		}
		Fire fire = new Fire(tree, f.fires.size() * FLAMES);
		for (int i = 0; i < FLAMES; i++) {
			double out = i == 0 ? 0.7 : tree[4] * 0.7;
			float[] at = {(float)(tree[0] + spread(f, out)), (float)(tree[1] + spread(f, out)), 0.0F};
			// (the crown's height is above the tree's own ground)
			float up = i == 0 ? 0.0F : (float)(tree[2] + f.rnd.nextDouble() * (tree[3] - tree[2]));
			fire.at[i] = new Vec3(f.at(at[0], at[1], 0.0).x, f.at(tree[0], tree[1], 0.0).y + up, f.at(at[0], at[1], 0.0).z);
			flame(f, fire.id + i, f.staged(fire.at[i]), i * 45.0F);
		}
		f.fires.add(fire);
		f.fireOf.put(tree, fire);
	}

	/** The fire, each tick: every burning tree burns up (or down, under the men at it), throws flame and smoke, and lights its neighbours. */
	private static void fire(Film f, int t) {
		int live = 0;
		for (int n = 0, count = f.fires.size(); n < count; n++) {
			Fire fire = f.fires.get(n);
			if (fire.out) {
				continue;
			}
			double[] tree = fire.tree;
			Vec3 foot = f.at(tree[0], tree[1], 0.0);
			fire.burn = fire.helpers > 0 ? fire.burn - fire.helpers / (double)DOUSE_TICKS : Math.min(1.0, fire.burn + 1.0 / BURN_TICKS);
			if (fire.helpers > 0) {
				for (int i = 0; i < 3; i++) {
					particle(f, ParticleTypes.CLOUD, foot.add(spread(f, 1.0), 0.3 + f.rnd.nextDouble() * 2.0, spread(f, 1.0)), 0.0, 0.05, 0.0);
				}
			}
			fire.helpers = 0;
			if (fire.burn <= 0.0) {
				fire.out = true;
				sound(f, SoundEvents.FIRE_EXTINGUISH, 0.9F + f.rnd.nextFloat() * 0.3F, 0.5F);
				for (int i = 0; i < FLAMES; i++) {
					quench(f, fire.id + i);
				}
				for (int i = 0; i < 14; i++) {
					particle(f, ParticleTypes.LARGE_SMOKE, foot.add(spread(f, tree[4]), f.rnd.nextDouble() * tree[3], spread(f, tree[4])), 0.0, 0.06, 0.0);
				}
				continue;
			}
			live++;
			if ((t + n) % 3 == 0) {
				for (int i = 0; i < FLAMES; i++) {
					flameSize(f, fire.id + i, (i == 0 ? FLAME_FOOT : FLAME_CROWN) * (float)(0.25 + 0.75 * fire.burn) * (0.9F + 0.2F * f.rnd.nextFloat()));
				}
			}
			for (int i = 0, flames = 1 + (int)(fire.burn * 3.0); i < flames; i++) {
				Vec3 at = fire.at[f.rnd.nextInt(FLAMES)];
				particle(f, ParticleTypes.FLAME, at.add(spread(f, 1.2), f.rnd.nextDouble() * 2.0, spread(f, 1.2)), spread(f, 0.02), 0.04 + f.rnd.nextDouble() * 0.08, spread(f, 0.02));
			}
			if ((t + n) % 2 == 0) {
				particle(f, f.rnd.nextInt(4) == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE,
					foot.add(spread(f, tree[4] * 0.6), tree[3] + f.rnd.nextDouble(), spread(f, tree[4] * 0.6)), spread(f, 0.02), 0.07, spread(f, 0.02));
			}
			if (f.rnd.nextInt(14) == 0) {
				particle(f, ParticleTypes.LAVA, fire.at[1 + f.rnd.nextInt(FLAMES - 1)], 0.0, 0.0, 0.0);
			}
			if (fire.burn > SPREADS_FROM && f.rnd.nextInt(SPREAD_EVERY) == 0) {
				spreadFrom(f, tree, SPREAD_REACH);
			}
		}
		if (live > 0 && t % EMBER_EVERY == 0 && f.lungeAt < 0) {
			Fire from = f.fires.get(f.rnd.nextInt(f.fires.size()));
			if (!from.out) {
				spreadFrom(f, from.tree, EMBER_REACH);
			}
		}
		if (live > 0 && t % 24 == 0) {
			sound(f, SoundEvents.FIRE_AMBIENT, 0.8F + f.rnd.nextFloat() * 0.4F, Math.min(0.9F, 0.15F + live * 0.02F));
		}
	}

	/** One tree not yet alight within {@code reach} of {@code tree} catches from it. */
	private static void spreadFrom(Film f, double[] tree, double reach) {
		List<double[]> near = new ArrayList<>();
		for (double[] other : f.trees) {
			if (!f.fireOf.containsKey(other) && distance(other[0], other[1], tree[0], tree[1]) < reach) {
				near.add(other);
			}
		}
		if (!near.isEmpty()) {
			ignite(f, near.get(f.rnd.nextInt(near.size())));
		}
	}

	/** Once the sorcerer is gone and his host with him: the defenders still standing run for the burning trees and beat at them. */
	private static void douse(Film f, int t) {
		if (f.lungeAt < 0 || t < f.lungeAt + DOUSE_AFTER) {
			return;
		}
		for (Actor a : f.soldiers) {
			if (!a.up() || a.scripted || t < a.busyUntil) {
				continue;
			}
			Fire fire = a.dousing;
			if (fire == null || fire.out) {
				fire = null;
				double nearest = 200.0;
				for (Fire other : f.fires) {
					double d = distance(other.tree[0], other.tree[1], a.f, a.s);
					if (!other.out && other.crew < DOUSE_CREW && d < nearest) {
						nearest = d;
						fire = other;
					}
				}
				a.dousing = fire;
				if (fire == null) {
					continue;
				}
				fire.crew++;
			}
			a.foe = null;
			a.speed = DOUSE_RUN;
			double df = a.f - fire.tree[0], ds = a.s - fire.tree[1], d = Math.max(0.01, Math.sqrt(df * df + ds * ds));
			a.toF = fire.tree[0] + df / d * DOUSE_STAND;
			a.toS = fire.tree[1] + ds / d * DOUSE_STAND;
			if (d < DOUSE_STAND + 0.5) {
				fire.helpers++;
				if (t >= a.nextSwing) {
					swing(f, a);
					a.nextSwing = t + DOUSE_SWING;
				}
			}
		}
	}

	// ---- the battle ---------------------------------------------------------------------------------------------------------

	/**
	 * The fight, the whole film through: everyone standing finds a foe, closes with it and trades blows; now and then one
	 * pair's fight ends in a finisher. The defenders hold their ground and meet what comes; the host and the dead go
	 * looking; Varro's orc hunts the dead.
	 */
	private static void battle(Film f, int t) {
		boolean over = f.lungeAt >= 0 && t >= f.lungeAt + 3;
		for (Actor a : f.fighters) {
			if (a.down || a.fallAt >= 0 || a.sink > 0.0 || a.scripted || a.fled || t < a.busyUntil || t < a.stunUntil) {
				continue;
			}
			if (a.foe != null && (!a.foe.up() || a.foe.team == a.team)) {
				a.foe = null;
				a.engagedAt = -1;
			}
			if (a.foe == null && !over) {
				a.foe = foeFor(f, a);
			}
			if (a.foe == null) {
				a.toF = a.f;
				a.toS = a.s;
				continue;
			}
			double df = a.f - a.foe.f, ds = a.s - a.foe.s, d = Math.max(0.01, Math.sqrt(df * df + ds * ds));
			if (d > REACH) {
				a.toF = a.foe.f + df / d * STAND_OFF;
				a.toS = a.foe.s + ds / d * STAND_OFF;
				a.engagedAt = -1;
				continue;
			}
			a.toF = a.f;
			a.toS = a.s;
			if (a.engagedAt < 0) {
				a.engagedAt = t;
			}
			if (a.foe.foe == null && !a.foe.scripted) {
				a.foe.foe = a;
			}
			if (t >= a.nextSwing) {
				swing(f, a);
				a.nextSwing = t + SWING_GAP + f.rnd.nextInt(SWING_SPREAD);
				if (f.rnd.nextInt(4) == 0) {
					sound(f, f.rnd.nextBoolean() ? SoundEvents.ZOMBIE_ATTACK_IRON_DOOR : SoundEvents.PLAYER_ATTACK_STRONG, 0.8F + f.rnd.nextFloat() * 0.4F, 0.22F);
				}
			}
		}
		if (over || t < 16 || f.rnd.nextInt(FINISH_EVERY) != 0) {
			return;
		}
		// one pair's fight ends
		List<Actor> ready = new ArrayList<>();
		for (Actor a : f.legion) {
			Actor d = a.foe;
			if (a.up() && d != null && d.up() && a.engagedAt >= 0 && t - a.engagedAt >= FINISH_AFTER && t >= a.busyUntil && t >= d.busyUntil
				&& t >= a.reservedUntil && t >= d.reservedUntil && t >= a.stunUntil && !a.scripted && !d.scripted) {
				ready.add(a);
			}
		}
		if (ready.isEmpty()) {
			return;
		}
		Actor foe = ready.get(f.rnd.nextInt(ready.size())), defender = foe.foe;
		long standing = f.soldiers.stream().filter(Actor::up).count();
		boolean defenderLoses = defender.role == Role.SOLDIER && standing > DEFENDERS_KEPT && f.rnd.nextFloat() < DEFENDER_LOSES;
		f.finishes.add(begin(f, defenderLoses ? foe : defender, defenderLoses ? defender : foe, f.rnd.nextInt(3), t));
	}

	/** Whom {@code a} goes for: the nearest of the other side still up (a defender only one close by; no more than two on one man). */
	private static @Nullable Actor foeFor(Film f, Actor a) {
		Actor best = null;
		double nearest = a.team == 0 && a.role != Role.MARKED ? DEFENDER_SEEK : 80.0;
		for (Actor o : f.fighters) {
			if (o.team == a.team || !o.up() || o.sink > 0.0 || o.scripted || (a.role == Role.MARKED && o.role != Role.DEAD)) {
				continue;
			}
			double d = distance(a.f, a.s, o.f, o.s);
			if (d >= nearest) {
				continue;
			}
			if (a.team == 1) {
				int on = 0;
				for (Actor other : f.legion) {
					if (other != a && other.foe == o && other.up()) {
						on++;
					}
				}
				if (on >= 2) {
					continue;
				}
			}
			nearest = d;
			best = o;
		}
		return best;
	}

	/** A blow: one of the library's cuts; now and then whoever it's at gets a shield or an arm up to it. */
	private static void swing(Film f, Actor a) {
		act(f, a, SWINGS[f.rnd.nextInt(SWINGS.length)]);
		Actor foe = a.foe;
		if (foe != null && foe.foe == a && foe.up() && f.tick >= foe.busyUntil && f.rnd.nextFloat() < BLOCKED) {
			act(f, foe, "block");
			foe.nextSwing = Math.max(foe.nextSwing, f.tick + BLOCK_TICKS);
		}
	}

	private static Finish begin(Film f, Actor winner, Actor loser, int kind, int t) {
		winner.busyUntil = loser.busyUntil = t + FINISH_TICKS;
		winner.facing = loser;
		loser.facing = winner;
		winner.toF = winner.f;
		winner.toS = winner.s;
		loser.toF = loser.f;
		loser.toS = loser.s;
		return new Finish(winner, loser, kind, t);
	}

	/** One tick of a finisher; true when it's done. */
	private static boolean finish(Film f, Finish fin, int t) {
		Actor w = fin.winner, l = fin.loser;
		int into = t - fin.start;
		double df = l.f - w.f, ds = l.s - w.s, d = Math.max(0.01, Math.sqrt(df * df + ds * ds));
		// the way the blow goes (the world's), and across it
		Vec3 along = f.fwd.scale(df / d).add(f.right.scale(ds / d)), across = new Vec3(-along.z, 0.0, along.x);
		Vec3 chest = l.e.position().add(0.0, 1.1 * l.scale, 0.0);
		switch (fin.kind) {
			case BEHEAD -> {
				// (on its knees, bent toward whoever put it there)
				Vec3 neck = l.e.position().add(0.0, (1.45 - KNEEL) * l.scale, 0.0).subtract(along.scale(0.2 * l.scale));
				if (into == 0) {
					// beaten to its knees
					act(f, w, "swing_across");
					hurt(f, l);
					act(f, l, "kneel");
					blood(f, chest, along, 6, l.flesh.dark);
					sound(f, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F, 0.5F);
				}
				if (into == 7) {
					act(f, w, "overhead");
				}
				if (into == 13) {
					act(f, w, "chop");
					sound(f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 0.7F);
				}
				if (into == 15) {
					maim(f, l, FilmActor.NO_HEAD);
					Vec3 speed = across.scale(f.rnd.nextBoolean() ? 0.2 : -0.2).add(along.scale(0.08)).add(0.0, 0.42 + f.rnd.nextDouble() * 0.1, 0.0);
					if (l.flesh.head != null) {
						fling(f, true, id(l.flesh.head), (float)l.scale, 1.0F, 1.0F, neck.add(0.0, 0.2, 0.0), speed, 30.0F, false);
					} else {
						fling(f, false, BuiltInRegistries.BLOCK.getKey(l.flesh.helm).toString(), 0.5F, 0.5F, 0.5F, neck.add(0.0, 0.2, 0.0), speed, 30.0F, false);
					}
					blood(f, neck, new Vec3(0.0, 1.0, 0.0), 16, l.flesh.dark);
					l.bleedUntil = t + 14;
					l.bleedUp = (1.45 - KNEEL) * l.scale;
					sound(f, SoundEvents.PLAYER_ATTACK_STRONG, 0.6F, 0.8F);
				}
				if (into == 19) {
					l.death = "death_headless";
					l.fallAt = t;
				}
			}
			case SEVER -> {
				if (into == 0) {
					// the weapon arm, off at the shoulder
					act(f, w, "swing_down");
					hurt(f, l);
					maim(f, l, FilmActor.NO_RIGHT_ARM);
					hold(f, l, EquipmentSlot.MAINHAND, "");
					act(f, l, "maimed");
					Vec3 shoulder = l.e.position().add(0.0, 1.35 * l.scale, 0.0);
					fling(f, false, BuiltInRegistries.BLOCK.getKey(l.flesh.limb).toString(), 0.26F, 0.72F, 0.26F, shoulder,
						across.scale(f.rnd.nextBoolean() ? 0.17 : -0.17).add(along.scale(0.05)).add(0.0, 0.36 + f.rnd.nextDouble() * 0.1, 0.0), 34.0F, false);
					blood(f, shoulder, across, 14, l.flesh.dark);
					l.bleedUntil = t + 12;
					l.bleedUp = 1.3 * l.scale;
					sound(f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.7F);
					sound(f, SoundEvents.PLAYER_ATTACK_STRONG, 0.6F, 0.7F);
				}
				if (into == 8) {
					act(f, w, "overhead");
				}
				if (into == 13) {
					// and cut down
					act(f, w, "chop");
					blood(f, chest, along, 10, l.flesh.dark);
					sound(f, SoundEvents.PLAYER_ATTACK_STRONG, 0.7F, 0.6F);
				}
				if (into == 15) {
					l.death = "death_back";
					l.fallAt = t;
				}
			}
			default -> {
				if (into == 0) {
					// run through
					act(f, w, "thrust");
					w.toF = w.f + df / d * 0.4;
					w.toS = w.s + ds / d * 0.4;
				}
				if (into == 4) {
					hurt(f, l);
					act(f, l, "impaled");
					blood(f, chest.add(along.scale(0.35)), along, 16, l.flesh.dark);
					sound(f, SoundEvents.PLAYER_ATTACK_STRONG, 0.6F, 0.8F);
				}
				if (into == 13) {
					// the blade drawn back out
					rest(f, w);
					w.toF = w.f - df / d * 0.4;
					w.toS = w.s - ds / d * 0.4;
					blood(f, chest, along.scale(-1.0), 8, l.flesh.dark);
				}
				if (into == 15) {
					l.death = "death_front";
					l.fallAt = t;
				}
			}
		}
		if (into >= FINISH_TICKS - 2) {
			rest(f, w);
			w.facing = null;
			l.facing = null;
			return true;
		}
		return false;
	}

	/** Blood out of {@code at}, thrown the way of {@code way}: red (or the dead's, dark and thick). */
	private static void blood(Film f, Vec3 at, Vec3 way, int much, boolean dark) {
		BlockState wet = (dark ? Blocks.NETHER_WART_BLOCK : Blocks.REDSTONE_BLOCK).defaultBlockState();
		for (int i = 0; i < much; i++) {
			Vec3 p = at.add(spread(f, 0.15), spread(f, 0.15), spread(f, 0.15));
			particle(f, new BlockParticleOption(ParticleTypes.BLOCK, wet), p, way.x * 0.3 + spread(f, 0.1), way.y * 0.3 + 0.12, way.z * 0.3 + spread(f, 0.1));
			if (i % 2 == 0) {
				particle(f, new DustParticleOptions(dark ? 0x3A0A12 : 0x9A0C0C, 1.1F), p.add(way.scale(0.3 * f.rnd.nextDouble())), 0.0, 0.0, 0.0);
			}
		}
	}

	/** What's in the air: heads, limbs and the axe fall and come to rest; the sorcerer's bolts fly and burst; the wounded bleed. */
	private static void things(Film f) {
		for (Gib g : f.gibs) {
			if (g.rest) {
				continue;
			}
			g.vy -= GRAVITY;
			g.x += g.vx;
			g.y += g.vy;
			g.z += g.vz;
			g.turn += g.spin;
			double floor = f.ground(g.x, g.z) + 0.2;
			if (g.y <= floor) {
				g.y = floor;
				if (g.bounced || g.flat) {
					g.rest = true;
				} else {
					g.bounced = true;
					g.vx *= BOUNCE;
					g.vz *= BOUNCE;
					g.vy = -g.vy * BOUNCE;
					g.spin *= 0.5F;
				}
			}
			g.e.setPos(g.x, g.y, g.z);
			g.e.setYRot(g.turn);
			g.e.setXRot(g.rest ? (g.flat ? 125.0F : 90.0F) : g.turn * 0.7F);
		}
		for (Iterator<Mana> it = f.mana.iterator(); it.hasNext(); ) {
			Mana m = it.next();
			for (int i = 0; i < 4; i++) {
				Vec3 p = m.at.add(m.step.scale(i / 4.0));
				particle(f, new DustParticleOptions(0xA050FF, 1.5F), p, 0.0, 0.0, 0.0);
				particle(f, i % 2 == 0 ? ParticleTypes.WITCH : ParticleTypes.END_ROD, p, 0.0, 0.0, 0.0);
			}
			m.at = m.at.add(m.step);
			if (--m.left <= 0) {
				it.remove();
				sound(f, m.tree ? SoundEvents.ZOMBIE_ATTACK_IRON_DOOR : SoundEvents.FIRECHARGE_USE, m.tree ? 1.4F : 0.6F, 0.45F);
				if (m.tree) {
					float[] hit = f.staged(m.at);
					igniteNear(f, hit[0], hit[1], 1.5);
				}
				for (int i = 0; i < 16; i++) {
					particle(f, ParticleTypes.WITCH, m.at.add(spread(f, 0.4), spread(f, 0.4), spread(f, 0.4)), spread(f, 0.15), f.rnd.nextDouble() * 0.2, spread(f, 0.15));
					if (m.tree) {
						particle(f, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_LOG.defaultBlockState()), m.at, spread(f, 0.2), 0.15, spread(f, 0.2));
					} else if (i % 2 == 0) {
						particle(f, ParticleTypes.LARGE_SMOKE, m.at, spread(f, 0.05), 0.06, spread(f, 0.05));
					}
				}
			}
		}
		for (Actor a : f.actors) {
			if (f.tick < a.bleedUntil) {
				blood(f, a.e.position().add(0.0, a.bleedUp, 0.0), new Vec3(0.0, 1.0, 0.0), 2, a.flesh.dark);
			}
		}
	}

	/** Moves one actor toward where it's headed, rises or sinks it, turns it, and drops it when its time comes. */
	private static void move(Film f, Actor a, int t) {
		if (a.fallAt >= 0 && t >= a.fallAt && !a.down) {
			a.down = true;
			a.sinkTo = Math.min(a.sinkTo, 0.0);
			hurt(f, a);
			act(f, a, a.death != null ? a.death : f.rnd.nextBoolean() ? "death_back" : "death_front");
			if (a.role == Role.DEAD) {
				particle(f, ParticleTypes.LARGE_SMOKE, a.e.position().add(0.0, 0.8, 0.0), 0.0, 0.05, 0.0);
			}
		}
		double df = a.toF - a.f, ds = a.toS - a.s, d = Math.sqrt(df * df + ds * ds);
		boolean moving = !a.down && d > 0.05 && a.sink < 0.5;
		if (moving) {
			double step = Math.min(d, a.speed);
			a.f += df / d * step;
			a.s += ds / d * step;
			// round the trees, not through them
			if (a.role != Role.STRANGER) {
				for (double[] tree : f.trees) {
					double tf = a.f - tree[0], ts = a.s - tree[1], near = Math.sqrt(tf * tf + ts * ts);
					if (near < TREE_BODY && near > 1.0E-4) {
						a.f = tree[0] + tf / near * TREE_BODY;
						a.s = tree[1] + ts / near * TREE_BODY;
					}
				}
			}
		}
		if (a.sink != a.sinkTo) {
			a.sink = a.sink < a.sinkTo ? Math.min(a.sinkTo, a.sink + a.sinkRate) : Math.max(a.sinkTo, a.sink - a.sinkRate);
		}
		if (a.clawing && a.sink <= 0.0) {
			// out of the ground, and on its feet
			a.clawing = false;
			gait(f, a, Moves.GUARD, Moves.SHAMBLE);
		}
		// facing: whoever its scene has it face, its foe, the way it's going, or as it stood
		Actor toward = a.facing != null ? a.facing : a.foe != null && distance(a.f, a.s, a.foe.f, a.foe.s) < 7.0 ? a.foe : null;
		float want = a.yaw;
		if (a.down) {
			want = a.yaw;
		} else if (toward != null) {
			want = f.yawOf(toward.f - a.f, toward.s - a.s);
		} else if (moving && d > 0.3) {
			want = f.yawOf(df, ds);
		} else if (!Float.isNaN(a.restYaw)) {
			want = a.restYaw;
		}
		a.yaw = Mth.rotLerp(0.35F, a.yaw, want);
		place(f, a);
		if (a.role == Role.SORCERER && !a.fled && f.tick % 3 == 0) {
			particle(f, ParticleTypes.WITCH, a.e.position().add(spread(f, 0.6), 1.2 + f.rnd.nextDouble(), spread(f, 0.6)), 0.0, 0.02, 0.0);
		}
	}

	/** Black flame: ink and smoke licking up out of the ground at {@code (fwd, side)}. */
	private static void shadowFlame(Film f, double fwd, double side, double rise) {
		Vec3 p = f.at(fwd, side, 0.1);
		particle(f, f.rnd.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SQUID_INK, p, spread(f, 0.03), rise + f.rnd.nextDouble() * 0.08, spread(f, 0.03));
		if (f.rnd.nextInt(9) == 0) {
			particle(f, ParticleTypes.SOUL_FIRE_FLAME, p, 0.0, rise * 0.4, 0.0);
		}
	}

	private static double spread(Film f, double by) {
		return (f.rnd.nextDouble() * 2.0 - 1.0) * by;
	}

	// ---- the camera ---------------------------------------------------------------------------------------------------------

	/**
	 * Where the camera is and which way it looks this frame ({x, y, z, yaw, pitch}), or null when no film is playing.
	 * Every shot is taken from the {@code s > 0} side of the battle line (the 180 degree rule); {@code third} turns the
	 * camera off its subject to set it on the left (+1) or right (-1) third line.
	 */
	public static double @Nullable [] view(Minecraft minecraft, float partialTicks) {
		Film f = film;
		if (f == null || minecraft.player == null || minecraft.getCameraEntity() != minecraft.player || minecraft.level != f.level) {
			return null;
		}
		float t = Math.min(f.tick + partialTicks, LENGTH);
		Vec3 pos;
		Vec3 look;
		float third = 0.0F, tilt = 0.0F;
		if (t < MELEE) {
			// from the woods' edge back down the road: the fight across the frame, the Keep on the right third, the ground the lower third
			float k = smooth(t / MELEE);
			pos = f.at(37.0 - 3.0 * k, 5.5, 3.6 - 1.4 * k);
			look = keepTop(f);
			third = -1.0F;
			tilt = -3.0F;
		} else if (t < SUMMON) {
			// along the line, close and low, defenders on the left and the host on the right
			float k = smooth((t - MELEE) / (SUMMON - MELEE));
			pos = f.at(19.5, 13.0 - 7.0 * k, 1.6);
			look = f.at(21.5, -8.0, 1.3);
		} else if (t < RISING) {
			// low before the sorcerer, on the right third, the sky he calls down filling the rest
			float k = smooth((t - SUMMON) / (RISING - SUMMON));
			pos = f.at(SORCERER_F - 5.5 + 0.8 * k, 2.4, 0.8);
			look = f.at(SORCERER_F, 0.0, 1.9);
			third = -0.85F;
		} else if (t < VARRO) {
			// from behind the mages, over the whole field as the dead come up in it; the horizon on the upper third
			float k = smooth((t - RISING) / (VARRO - RISING));
			pos = f.at(2.0 + 1.5 * k, 4.5, 3.4);
			look = f.at(30.0, -1.0, 0.5);
		} else if (t < VARRO_CLOSE) {
			// side on: Varro on the left third, the orc on the right
			pos = f.at(VARRO_AT[0] + 1.2, VARRO_AT[1] + 3.3, 1.45);
			look = f.at(VARRO_AT[0] + 1.0, VARRO_AT[1], 1.2);
		} else if (t < QUIET && t < SPELLS) {
			// a cut in on the same line: Varro on the left third, the orc on its knees on the right, his hand on its brow between
			// them; then after it as it goes for the dead
			pos = f.at(VARRO_AT[0] + 1.5, VARRO_AT[1] + 2.0, 1.55);
			Vec3 orc = f.marked != null ? f.marked.e.getPosition(partialTicks) : f.at(VARRO_AT[0] + MARKED_STOPS, VARRO_AT[1], 0.0);
			look = t < VARRO_FIELD ? f.at(VARRO_AT[0] + 1.3, VARRO_AT[1], 1.2) : orc.add(0.0, 1.0, 0.0);
		} else if (t < QUIET) {
			int which = (int)((t - SPELLS) / CAST_TICKS);
			float into = (t - SPELLS) - which * CAST_TICKS;
			Actor mage = caster(f, which);
			Vec3 m = mage != null ? mage.e.getPosition(partialTicks) : f.at(MAGES_F, 0.0, 0.0);
			if (into < 12) {
				// their face as they call it up, on the left third, the field they look out at behind the camera's shoulder
				pos = m.add(f.fwd.scale(2.6)).add(f.right.scale(1.1)).add(0.0, 1.6, 0.0);
				look = m.add(0.0, 1.55, 0.0);
				third = 0.6F;
			} else {
				// and over their shoulder as it lands
				pos = m.subtract(f.fwd.scale(1.8)).add(f.right.scale(0.8)).add(0.0, 2.0, 0.0);
				look = mage != null ? target(f, which, mage) : f.at(26.0, 0.0, 0.0);
			}
		} else if (t < REVERSE) {
			// with him as he runs, behind and on his right, the forest weaving past and the bolts coming in: he stays left of the
			// middle, the way ahead of him open
			Vec3 q = f.stranger != null ? f.stranger.e.getPosition(partialTicks) : f.at(QUIET_PATH[0][0], QUIET_PATH[0][1], 0.0);
			pos = q.subtract(f.fwd.scale(CHASE_BACK)).add(f.right.scale(CHASE_SIDE)).add(0.0, 1.7, 0.0);
			look = q.add(f.fwd.scale(2.5)).add(0.0, 1.1, 0.0);
		} else if (f.glideAt < 0 || t < f.glideAt + GLIDE_CUT) {
			// from behind the sorcerer (his shoulder at the right edge), the fifth coming on, on the left third
			Vec3 q = f.stranger != null ? f.stranger.e.getPosition(partialTicks) : f.at(40.0, 0.0, 0.0);
			pos = f.at(SORCERER_F + 2.3, 1.3, 2.0);
			look = q.add(0.0, 1.2, 0.0);
			third = 0.5F;
		} else if (t < END) {
			// side on: he comes in from the left, the sorcerer on the right third, and then nobody there
			float k = smooth((t - f.glideAt - GLIDE_CUT) / (END - f.glideAt - GLIDE_CUT));
			pos = f.at(51.2 - 1.6 * (1.0F - k), 6.4 - 1.4 * k, 1.5);
			look = f.at(50.3 - 2.2 * (1.0F - k), 0.0, 1.4);
		} else {
			// the road again, wide, drawing up and away, the Keep still on its third
			float k = smooth((t - END) / (LENGTH - END));
			pos = f.at(38.0 + 3.0 * k, 6.0, 2.4 + 2.4 * k);
			look = keepTop(f);
			third = -1.0F;
			tilt = -2.0F;
		}
		Vec3 dir = look.subtract(pos);
		dir = dir.lengthSqr() < 1.0E-6 ? f.fwd : dir.normalize();
		double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z)) + third * THIRD;
		double pitch = Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0, 1.0))) + tilt;
		return new double[] {pos.x, pos.y, pos.z, yaw, pitch};
	}

	/** Halfway up the Keep's great tower. */
	private static Vec3 keepTop(Film f) {
		return f.at(KEEP_F, 0.0, KEEP_RISE + 10.0);
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	// ---- the screen ---------------------------------------------------------------------------------------------------------

	/** The bars, the dark (dim as a memory, deeper as the sorcerer calls), lightning and Ambrose's light, and the fades. */
	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float shown = Mth.lerp(partial, barsO, bars);
		if (shown <= 0.01F) {
			return;
		}
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		Film f = film;
		if (f != null) {
			HudZones.hideFor(100);
			float t = f.tick + partial;
			float dark = DIM;
			if (t >= SUMMON && t < RISING + 30) {
				// the dark gathers as he raises his arms, and lets go once the dead are coming
				float in = smooth((t - SUMMON) / (STRIKE - SUMMON)), out = smooth((t - RISING) / 30.0F);
				dark = Mth.lerp(in * (1.0F - out), DIM, SUMMONING);
			}
			float fade = Math.max(1.0F - t / FADE, (t - (LENGTH - FADE)) / FADE);
			dark = Math.max(dark, Mth.clamp(fade, 0.0F, 1.0F));
			graphics.fill(0, 0, width, height, (Math.round(dark * 255.0F) << 24) | 0x05060C);
			float light = Mth.lerp(partial, f.flashO, f.flash);
			if (light > 0.01F) {
				graphics.fill(0, 0, width, height, (Math.round(light * 255.0F) << 24) | 0xFFF6DC);
			}
		}
		int bar = Math.round(height * BAR_SHARE * shown);
		graphics.fill(0, 0, width, bar, 0xFF000000);
		graphics.fill(0, height - bar, width, height, 0xFF000000);
	}
}
