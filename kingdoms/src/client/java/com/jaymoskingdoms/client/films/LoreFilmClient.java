package com.jaymoskingdoms.client.films;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.hud.HudZones;
import com.jaymoskingdoms.films.LoreFilms;
import com.jaymoskingdoms.inhabitants.Draugr;
import com.jaymoskingdoms.inhabitants.Inhabitants;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A lore film ({@link LoreFilms}) played on this game alone: a story staged in front of you with actors and a set only
 * your game sees, shot through letterbox bars with the HUD cleared and no words on screen (sounds only). Sneak held for a
 * second skips it.
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
 * <li>The fifth, in plain grey, blade in hand, runs at the sorcerer, "darting back and forth through trees and past bolts
 * of mana from the sorcerer as he approaches quickly ... and he lunges, but the sorcerer fades away". The dead drop where
 * they stand, the host runs. Nobody says who he was; he looks like none of the spirits.</li>
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
		CAST_TICKS = 36, QUIET = 584, REVERSE = 636, LUNGE_SHOT = 666, END = 730, LENGTH = LoreFilms.FILM_TICKS;
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
	/** The film's orcs' greens (as tools/gen_film_skins.py paints them), and the dead ones', for GreenskinLooks to tint by. */
	static final int[] ORC_GREENS = {0xFF7C8A62, 0xFF687854, 0xFF566646}, DEAD_GREENS = {0xFF5E6452, 0xFF545C48, 0xFF4E5444};

	// ---- the set ----
	/** The forest: how far it reaches behind, ahead and aside, how many trees, how far apart, and how near one a fighter can stand. */
	static final double FOREST_BACK = -16.0, FOREST_FRONT = 70.0, FOREST_HALF = 40.0, TREE_GAP = 4.6, TREE_BODY = 0.8;
	static final int TREES = 76;
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
	/** How far a kneeling actor is lowered (blocks, times its scale), and how fast actors rise and sink. */
	static final double KNEEL = 0.62, SINK_RATE = 0.12;
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
	static final double MARKED_RUN = 0.36, MARKED_HUNT = 0.2;
	/** How often Grimsby throws a bolt (ticks). */
	static final int BOLT_EVERY = 26;

	// ---- the fifth ----
	/** His run: where he starts, each turn of it ({f, s}), how fast he runs and lunges (blocks a tick), and where the lunge carries him. */
	static final double[][] QUIET_PATH = {{17.0, -1.0}, {22.0, -3.0}, {26.0, 2.5}, {30.0, -2.5}, {34.0, 3.0}, {38.0, -2.0}, {42.0, 2.0}, {46.0, -1.0},
		{49.2, 0.0}};
	static final double QUIET_RUN = 0.5, QUIET_LUNGE = 0.85, LUNGE_END = 53.6;
	/** The sorcerer's bolts at him: how often (ticks), how fast (blocks a tick), and how near he gets before the sorcerer stops throwing. */
	static final int MANA_EVERY = 9;
	static final double MANA_SPEED = 2.2, MANA_UNTIL = 45.5;
	/** The chase camera runs this far behind him and to his side, its way kept clear of trees. */
	static final double CHASE_BACK = 3.0, CHASE_SIDE = 2.6;

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
		SOLDIER, MAGE, STRANGER, SORCERER, DEAD, HOST, MARKED
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

	/** One actor: its entity, where it stands on the stage and where it's headed, how far under the ground, whom it fights, how it's posed. */
	static final class Actor {
		final LivingEntity e;
		final Role role;
		Flesh flesh = Flesh.MAN;
		double f, s, toF, toS;
		double sink, sinkTo, sinkRate = SINK_RATE, speed = WALK, scale = 1.0;
		/** 0 the defenders, 1 the sorcerer's. */
		int team;
		/** Left to its own script (the battle doesn't move it). */
		boolean scripted;
		@Nullable Actor foe, facing;
		int engagedAt = -1, nextSwing, busyUntil, stunUntil, riseAt = -1, bleedUntil, reservedUntil;
		double bleedUp;
		int fallAt = -1;
		boolean down, fled;
		float yaw, restYaw = Float.NaN;
		final float[] pose = new float[FilmPose.NUMBERS], poseO = new float[FilmPose.NUMBERS];
		float @Nullable [] poseTo;
		float weight, weightO, poseRate = 0.4F;
		int gone;

		Actor(LivingEntity e, Role role, double f, double s, float yaw) {
			this.e = e;
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
		double x, y, z, vx, vy, vz;
		float turn, spin;
		boolean bounced, rest;

		Gib(Display e, Vec3 at, Vec3 speed, float spin) {
			this.e = e;
			this.x = at.x;
			this.y = at.y;
			this.z = at.z;
			this.vx = speed.x;
			this.vy = speed.y;
			this.vz = speed.z;
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
	static final class Film {
		final ClientLevel level;
		final Vec3 origin, fwd, right;
		final float yaw;
		final RandomSource rnd = RandomSource.create();
		final List<Actor> actors = new ArrayList<>();
		/** Everyone in the battle: the defenders' line, the host, the dead (once up), Varro's orc (once marked). */
		final List<Actor> fighters = new ArrayList<>();
		/** The host and the dead: everything the mages strike at. */
		final List<Actor> legion = new ArrayList<>();
		final List<Actor> soldiers = new ArrayList<>();
		final Map<Entity, Actor> byEntity = new IdentityHashMap<>();
		/** Each orc actor → the green its orc look is tinted by ({@link #orcLook}). */
		final Map<LivingEntity, Integer> orcs = new IdentityHashMap<>();
		/** The set and everything else that isn't an actor: trees, the Keep, lightning, what's sent flying. */
		final List<Entity> props = new ArrayList<>();
		/** The trees' trunks ({f, s}), and the ones the fifth turns at. */
		final List<double[]> trees = new ArrayList<>(), dartTrees = new ArrayList<>();
		final List<Finish> finishes = new ArrayList<>();
		final List<Rise> rises = new ArrayList<>();
		final List<Gib> gibs = new ArrayList<>();
		final List<Mana> mana = new ArrayList<>();
		@Nullable Actor grimsby, thessaly, ambrose, varro, stranger, sorcerer, marked;
		@Nullable Gib axe;
		@Nullable CameraType viewBefore;
		int tick, skip, quietAt = 1, lungeAt = -1;
		float flash, flashO;

		Film(ClientLevel level, Vec3 origin, float yaw) {
			this.level = level;
			this.origin = origin;
			this.yaw = yaw;
			this.fwd = Vec3.directionFromRotation(0.0F, yaw);
			this.right = new Vec3(-this.fwd.z, 0.0, this.fwd.x);
		}

		/** The stage point {@code f} ahead and {@code s} aside, {@code up} over the ground there. */
		Vec3 at(double f, double s, double up) {
			double x = this.origin.x + this.fwd.x * f + this.right.x * s, z = this.origin.z + this.fwd.z * f + this.right.z * s;
			return new Vec3(x, ground(x, z) + up, z);
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
		ClientPlayNetworking.registerGlobalReceiver(LoreFilms.Play.TYPE, (payload, context) -> context.client().execute(() -> start(context.client(), payload.film())));
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
	}

	/** Whether a film is playing (movement keys don't answer). */
	public static boolean locked() {
		return film != null;
	}

	// ---- staging ------------------------------------------------------------------------------------------------------------

	private static void start(Minecraft minecraft, String name) {
		if (!LoreFilms.LONG_DEFENCE.equals(name) || minecraft.level == null || minecraft.player == null) {
			return;
		}
		stop(minecraft);
		Film f = new Film(minecraft.level, minecraft.player.position(), minecraft.player.getYRot());
		f.viewBefore = minecraft.options.getCameraType();
		film = f;
		float facing = f.yaw, facingBack = f.yaw + 180.0F;
		forest(f);
		keep(f);
		// the four mages behind the fight, and the fifth off to one side
		f.grimsby = mage(f, "grimsby", false, -7.0, Items.IRON_SWORD);
		f.thessaly = mage(f, "thessaly", true, -2.5, null);
		f.ambrose = mage(f, "ambrose", false, 2.5, Items.BLAZE_ROD);
		f.varro = mage(f, "varro", false, VARRO_AT[1], Items.NETHERITE_AXE);
		if (f.varro != null) {
			f.varro.e.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			f.varro.e.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			f.varro.e.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			f.varro.speed = 0.3;
		}
		LivingEntity quiet = person(f, "stranger", false);
		if (quiet != null) {
			quiet.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			f.stranger = cast(f, quiet, Role.STRANGER, STRANGER_AT[0], STRANGER_AT[1], facing);
			f.stranger.restYaw = facing;
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
			LivingEntity e = warrior ? warriorBorn(f, i) : orc(f, "orc_" + i % 3);
			if (e == null || soldier == null) {
				continue;
			}
			if (!warrior) {
				e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(i % 2 == 0 ? Items.IRON_AXE : Items.STONE_AXE));
			}
			Actor foe = cast(f, e, Role.HOST, at[0] + df, at[1] + ds, facingBack);
			foe.team = 1;
			foe.flesh = warrior ? Flesh.WARRIOR : Flesh.ORC;
			foe.scale = warrior ? 1.0 : ORC_SCALE;
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
				LivingEntity e = dead(f, i);
				if (e == null) {
					continue;
				}
				Actor a = cast(f, e, Role.DEAD, gf + spread(f, GROUP_SPREAD), gs + spread(f, GROUP_SPREAD), facingBack);
				a.team = 1;
				a.flesh = i % 2 == 0 ? Flesh.DEAD_ORC : Flesh.PIGLIN;
				a.scale = i % 2 == 0 ? ORC_SCALE : 1.0;
				a.speed = DEAD_WALK;
				a.sink = a.sinkTo = HIDDEN;
				a.sinkRate = RISE_RATE + f.rnd.nextDouble() * RISE_SPREAD;
				a.riseAt = at + f.rnd.nextInt(14);
				f.legion.add(a);
				f.fighters.add(a);
			}
			at += 3 + f.rnd.nextInt(GROUP_GAP);
		}
		// the orc Varro will mark, waiting its moment
		LivingEntity brute = orc(f, "orc_0");
		if (brute != null) {
			brute.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			f.marked = cast(f, brute, Role.MARKED, MARKED_AT[0], MARKED_AT[1], facingBack);
			f.marked.team = 1;
			f.marked.flesh = Flesh.ORC;
			f.marked.scale = ORC_SCALE;
			f.marked.scripted = true;
			f.marked.speed = MARKED_RUN;
		}
		LivingEntity grue = person(f, "sorcerer", false);
		if (grue != null) {
			f.sorcerer = cast(f, grue, Role.SORCERER, SORCERER_F, 0.0, facingBack);
			f.sorcerer.restYaw = facingBack;
		}
		sound(SoundEvents.AMBIENT_CAVE.value(), 0.7F, 0.5F);
		sound(SoundEvents.RAID_HORN.value(), 1.0F, 0.5F);
	}

	/** One of the line: a hearthlander in iron, a sword or an axe, some with shields. */
	private static @Nullable Actor soldier(Film f, int i, double fwd, double side, float yaw) {
		LivingEntity e = person(f, null, false);
		if (e == null) {
			return null;
		}
		e.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		e.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		e.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		e.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(i % 3 == 1 ? Items.IRON_AXE : Items.IRON_SWORD));
		if (i % 2 == 0) {
			e.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		}
		Actor a = cast(f, e, Role.SOLDIER, fwd, side, yaw);
		f.soldiers.add(a);
		f.fighters.add(a);
		return a;
	}

	private static @Nullable Actor mage(Film f, String skin, boolean slim, double s, @Nullable Item held) {
		LivingEntity e = person(f, skin, slim);
		if (e == null) {
			return null;
		}
		if (held != null) {
			e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(held));
		}
		Actor a = cast(f, e, Role.MAGE, MAGES_F, s, f.yaw);
		a.restYaw = f.yaw;
		return a;
	}

	/** A person (a mannequin of our own: no name over it), in {@code textures/entity/film/<skin>.png} or the plain default. */
	private static @Nullable LivingEntity person(Film f, @Nullable String skin, boolean slim) {
		LivingEntity e = EntityTypes.MANNEQUIN.create(f.level, EntitySpawnReason.LOAD);
		if (e == null) {
			return null;
		}
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("hide_description", true);
		if (skin != null) {
			CompoundTag profile = new CompoundTag();
			profile.putString("texture", JaymosKingdoms.id("entity/film/" + skin).toString());
			profile.putString("model", slim ? "slim" : "wide");
			tag.put("profile", profile);
		}
		try {
			e.load(TagValueInput.create(ProblemReporter.DISCARDING, f.level.registryAccess(), tag));
		} catch (RuntimeException ex) {
			JaymosKingdoms.LOGGER.debug("lore film actor {} kept its default look", skin, ex);
		}
		return e;
	}

	/** An orc (or a dead one): a mannequin in its skin, drawn bigger, and given the real orcs' look ({@link #orcLook}). */
	private static @Nullable LivingEntity orc(Film f, String skin) {
		LivingEntity e = person(f, skin, false);
		if (e != null) {
			var scale = e.getAttribute(Attributes.SCALE);
			if (scale != null) {
				scale.setBaseValue(ORC_SCALE);
			}
			int n = skin.charAt(skin.length() - 1) - '0';
			f.orcs.put(e, skin.startsWith("dead") ? DEAD_GREENS[n % DEAD_GREENS.length] : ORC_GREENS[n % ORC_GREENS.length]);
		}
		return e;
	}

	/**
	 * The green a lore film's orc is tinted by, for {@code GreenskinLooks} to draw the real orcs' tusks, brow, ears, marks
	 * and scars on it; 0 for anything else.
	 */
	public static int orcLook(Entity e) {
		Film f = film;
		if (f == null || e.getId() >= 0) {
			return 0;
		}
		Integer green = f.orcs.get(e);
		return green == null ? 0 : green;
	}

	/** A Warrior-born of the host: a draugr's helm, mail and fur, worn by the living man in better days (Andy). */
	private static @Nullable LivingEntity warriorBorn(Film f, int i) {
		Draugr e = Inhabitants.DRAUGR.create(f.level, EntitySpawnReason.LOAD);
		if (e != null) {
			e.stageLiving(i % 3 == 0);
		}
		return e;
	}

	/** One of the dead the sorcerer raises: dead orcs and zombified piglins, half and half. */
	private static @Nullable LivingEntity dead(Film f, int i) {
		if (i % 2 == 0) {
			LivingEntity e = orc(f, "dead_orc_" + (i / 2) % 3);
			if (e != null && i % 6 == 0) {
				e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
			}
			return e;
		}
		LivingEntity e = EntityTypes.ZOMBIFIED_PIGLIN.create(f.level, EntitySpawnReason.LOAD);
		if (e != null) {
			e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(i % 3 == 0 ? Items.IRON_SWORD : Items.GOLDEN_SWORD));
		}
		return e;
	}

	private static Actor cast(Film f, LivingEntity e, Role role, double fwd, double side, float yaw) {
		Actor a = new Actor(e, role, fwd, side, yaw);
		Vec3 at = f.at(fwd, side, 0.0);
		e.setId(nextId--);
		e.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		e.setYHeadRot(yaw);
		e.setYBodyRot(yaw);
		f.level.addEntity(e);
		f.actors.add(a);
		f.byEntity.put(e, a);
		return a;
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

	// ---- the set ------------------------------------------------------------------------------------------------------------

	/**
	 * A box of {@code block}, {@code w} wide, {@code h} high and {@code d} deep, its foot centred on the stage point at
	 * height {@code y}: a block display, seen from far off.
	 */
	private static Display.@Nullable BlockDisplay box(Film f, Block block, double fwd, double side, double y, float w, float h, float d,
		@Nullable Brightness light) {
		Display.BlockDisplay e = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, f.level);
		e.setBlockState(block.defaultBlockState());
		e.setTransformation(new Transformation(new Vector3f(-w / 2.0F, 0.0F, -d / 2.0F), null, new Vector3f(w, h, d), null));
		e.setViewRange(SET_VIEW_RANGE);
		if (light != null) {
			e.setBrightnessOverride(light);
		}
		Vec3 at = f.at(fwd, side, 0.0);
		e.setId(nextId--);
		e.snapTo(at.x, y, at.z, f.yaw, 0.0F);
		f.level.addEntity(e);
		f.props.add(e);
		return e;
	}

	/** The forest: the trees the fifth turns at, then the rest, off the road and out of the way of what's staged. */
	private static void forest(Film f) {
		for (int i = 1; i < QUIET_PATH.length - 1; i++) {
			double[] turn = QUIET_PATH[i];
			double[] trunk = {turn[0] + 0.9, turn[1] + Math.signum(turn[1]) * 0.2};
			f.dartTrees.add(trunk);
			tree(f, trunk[0], trunk[1], true);
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
		f.trees.add(new double[] {tf, ts});
		double y = f.at(tf, ts, 0.0).y;
		float tall = 7.0F + f.rnd.nextFloat() * 4.0F, girth = 0.8F + f.rnd.nextFloat() * 0.25F;
		if (spruce) {
			box(f, Blocks.SPRUCE_LOG, tf, ts, y, girth, tall, girth, null);
			box(f, Blocks.SPRUCE_LEAVES, tf, ts, y + tall - 3.2, 4.4F, 2.0F, 4.4F, null);
			box(f, Blocks.SPRUCE_LEAVES, tf, ts, y + tall - 1.2, 3.0F, 2.0F, 3.0F, null);
			box(f, Blocks.SPRUCE_LEAVES, tf, ts, y + tall + 0.8, 1.6F, 2.0F, 1.6F, null);
		} else {
			tall -= 1.5F;
			box(f, Blocks.OAK_LOG, tf, ts, y, girth, tall, girth, null);
			box(f, Blocks.OAK_LEAVES, tf, ts, y + tall - 1.4, 5.2F, 2.6F, 5.2F, null);
			box(f, Blocks.OAK_LEAVES, tf, ts, y + tall + 1.2, 3.2F, 1.8F, 3.2F, null);
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
		double y = f.at(KEEP_F, 0.0, 0.0).y + KEEP_RISE;
		Brightness lit = new Brightness(15, 15);
		for (int i = -2; i <= 2; i++) {
			box(f, Blocks.STONE_BRICKS, KEEP_F, i * 8.0, y, 8.0F, 8.0F, 3.0F, null);
			if (i != 0) {
				// merlons along the wall walk
				box(f, Blocks.STONE_BRICKS, KEEP_F, i * 8.0 - 2.0, y + 8.0, 2.0F, 1.6F, 3.0F, null);
				box(f, Blocks.STONE_BRICKS, KEEP_F, i * 8.0 + 2.0, y + 8.0, 2.0F, 1.6F, 3.0F, null);
			}
		}
		// the gatehouse, its gate, a torch either side
		box(f, Blocks.STONE_BRICKS, KEEP_F + 1.5, 0.0, y, 9.0F, 12.0F, 5.0F, null);
		box(f, Blocks.COBBLESTONE, KEEP_F + 1.5, 0.0, y + 12.0, 10.0F, 1.5F, 6.0F, null);
		box(f, Blocks.DARK_OAK_PLANKS, KEEP_F + 4.05, 0.0, y, 4.0F, 5.5F, 0.3F, null);
		box(f, Blocks.OCHRE_FROGLIGHT, KEEP_F + 4.2, -3.2, y + 4.0, 0.5F, 0.7F, 0.5F, lit);
		box(f, Blocks.OCHRE_FROGLIGHT, KEEP_F + 4.2, 3.2, y + 4.0, 0.5F, 0.7F, 0.5F, lit);
		for (int side = -1; side <= 1; side += 2) {
			box(f, Blocks.STONE_BRICKS, KEEP_F, side * 21.0, y, 7.0F, 15.0F, 7.0F, null);
			box(f, Blocks.COBBLESTONE, KEEP_F, side * 21.0, y + 15.0, 8.2F, 1.4F, 8.2F, null);
			box(f, Blocks.OCHRE_FROGLIGHT, KEEP_F + 3.55, side * 21.0, y + 10.5, 0.8F, 1.6F, 0.2F, lit);
		}
		// the great tower, behind the wall and over it
		for (int i = -1; i <= 1; i += 2) {
			box(f, Blocks.STONE_BRICKS, KEEP_F - 12.0, i * 4.0, y, 8.0F, 13.0F, 14.0F, null);
			box(f, Blocks.STONE_BRICKS, KEEP_F - 12.0, i * 4.0, y + 13.0, 8.0F, 13.0F, 14.0F, null);
			box(f, Blocks.STONE_BRICKS, KEEP_F - 12.0, i * 6.5, y + 28.0, 3.0F, 2.6F, 15.0F, null);
			box(f, Blocks.OCHRE_FROGLIGHT, KEEP_F - 4.95, i * 3.5, y + 16.0, 0.9F, 2.0F, 0.2F, lit);
			box(f, Blocks.OCHRE_FROGLIGHT, KEEP_F - 4.95, i * 4.5, y + 22.0, 0.9F, 2.0F, 0.2F, lit);
		}
		box(f, Blocks.COBBLESTONE, KEEP_F - 12.0, 0.0, y + 26.0, 17.0F, 2.0F, 15.0F, null);
		box(f, Blocks.STONE_BRICKS, KEEP_F - 12.0, 0.0, y + 28.0, 3.0F, 2.6F, 15.0F, null);
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
		f.skip = minecraft.options.keyShift.isDown() ? f.skip + 1 : 0;
		if (f.skip == SKIP_HOLD && f.tick < LENGTH - FADE) {
			f.tick = LENGTH - FADE;
		}
		f.flashO = f.flash;
		f.flash *= 0.82F;
		int t = f.tick;
		beats(f, t);
		battle(f, t);
		for (Iterator<Finish> it = f.finishes.iterator(); it.hasNext(); ) {
			if (finish(f, it.next(), t)) {
				it.remove();
			}
		}
		for (Actor a : f.actors) {
			move(f, a, t);
		}
		things(f);
		if (++f.tick >= LENGTH) {
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
			pose(grue, FilmPose.SUMMON, 0.18F);
			sound(SoundEvents.EVOKER_PREPARE_SUMMON, 0.6F, 0.9F);
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
			sound(SoundEvents.WITHER_SPAWN, 0.6F, 0.45F);
		}
		if (t == RISING + 40) {
			pose(grue, null, 0.1F);
		}
		for (Rise rise : f.rises) {
			if (t >= rise.tick - 10 && t < rise.tick + 24) {
				for (int i = 0; i < 5; i++) {
					shadowFlame(f, rise.f + spread(f, GROUP_SPREAD + 0.6), rise.s + spread(f, GROUP_SPREAD + 0.6), 0.18);
				}
			}
			if (t == rise.tick) {
				sound(SoundEvents.SOUL_ESCAPE.value(), 0.6F + f.rnd.nextFloat() * 0.3F, 0.8F);
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

	/** A bolt of lightning at {@code (fwd, side)}: seen and heard on this game alone, and the screen lit by it. */
	private static void lightning(Film f, double fwd, double side) {
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
	 * its brow. It bows, rises, takes up its axe and turns on the dead.
	 */
	private static void marking(Film f, int t) {
		Actor orc = f.marked, varro = f.varro;
		if (orc == null || varro == null) {
			return;
		}
		double kneelF = VARRO_AT[0] + 1.7;
		if (t == ORC_CHARGE) {
			orc.toF = kneelF;
			orc.toS = VARRO_AT[1];
			varro.facing = orc;
		}
		if (t == CHOP_UP) {
			orc.facing = varro;
			pose(orc, FilmPose.OVERHEAD, 0.5F);
		}
		if (t == CHOP) {
			pose(orc, FilmPose.CHOP, 0.8F);
			sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.8F);
			// inside the blow
			varro.toF = VARRO_AT[0] + 0.5;
			varro.toS = VARRO_AT[1] - 0.7;
			pose(varro, FilmPose.CATCH, 0.7F);
		}
		if (t == DISARM) {
			pose(varro, FilmPose.WRENCH, 0.7F);
			pose(orc, FilmPose.STAGGER, 0.6F);
			ItemStack axe = orc.e.getMainHandItem().copy();
			orc.e.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			Display.ItemDisplay flying = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, f.level);
			flying.setItemStack(axe);
			flying.setItemTransform(ItemDisplayContext.NONE);
			Vec3 hand = orc.e.position().add(0.0, 1.3, 0.0).subtract(f.fwd.scale(0.6));
			f.axe = fling(f, flying, hand, f.fwd.scale(0.085).add(f.right.scale(0.075)).add(0.0, 0.42, 0.0), 38.0F);
			sound(SoundEvents.SHIELD_BLOCK.value(), 1.3F, 0.9F);
			sound(SoundEvents.ANVIL_LAND, 1.6F, 0.25F);
		}
		if (t == KICK) {
			pose(varro, FilmPose.KICK, 0.7F);
			sound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.7F, 0.9F);
		}
		if (t == KNEES) {
			orc.e.hurtTime = orc.e.hurtDuration = 10;
			orc.sinkTo = KNEEL * orc.scale;
			pose(orc, FilmPose.KNEEL, 0.5F);
			sound(SoundEvents.ANVIL_LAND, 0.5F, 0.35F);
			for (int i = 0; i < 8; i++) {
				particle(f, ParticleTypes.CLOUD, orc.e.position().add(spread(f, 0.5), 0.1, spread(f, 0.5)), spread(f, 0.03), 0.01, spread(f, 0.03));
			}
		}
		if (t == GRIP) {
			// in front of it, his hand on its head, its face forced up to him
			varro.toF = kneelF - 0.95;
			varro.toS = VARRO_AT[1];
			pose(varro, FilmPose.REACH, 0.4F);
			pose(orc, FilmPose.KNEEL_UP, 0.4F);
		}
		Vec3 brow = orc.e.position().add(0.0, 1.55 * ORC_SCALE, 0.0).subtract(f.fwd.scale(0.3));
		if (t == MARK_SET) {
			// the ki mark burns on its brow
			sound(SoundEvents.FIRECHARGE_USE, 0.7F, 0.8F);
			sound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 0.8F);
			f.flash = Math.max(f.flash, 0.14F);
			for (int i = 0; i < 24; i++) {
				double turn = i * Math.PI / 12.0;
				particle(f, i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.END_ROD, brow.add(f.right.scale(Math.cos(turn) * 0.35)).add(0.0, Math.sin(turn) * 0.35, 0.0),
					0.0, 0.01, 0.0);
			}
		}
		if (t > MARK_SET && t < QUIET && t % 4 == 0 && !orc.down) {
			particle(f, ParticleTypes.SMALL_FLAME, brow, 0.0, 0.005, 0.0);
		}
		if (t == BOW) {
			pose(orc, FilmPose.KNEEL, 0.25F);
			pose(varro, null, 0.25F);
		}
		if (t == MARK_RISE) {
			// up, its axe taken back, and turned on the dead
			orc.sinkTo = 0.0;
			pose(orc, null, 0.3F);
			pose(varro, FilmPose.POINT, 0.4F);
			orc.facing = null;
			orc.speed = MARKED_HUNT;
			if (f.axe != null) {
				Vec3 to = new Vec3(f.axe.x, 0.0, f.axe.z).subtract(f.origin.x, 0.0, f.origin.z);
				orc.toF = to.dot(f.fwd);
				orc.toS = to.dot(f.right);
			}
			sound(SoundEvents.RAVAGER_ROAR, 0.9F, 0.7F);
		}
		if (t > MARK_RISE && orc.scripted && (t >= MARK_RISE + 26 || distance(orc.f, orc.s, orc.toF, orc.toS) < 0.6)) {
			if (f.axe != null) {
				orc.e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
				f.level.removeEntity(f.axe.e.getId(), Entity.RemovalReason.DISCARDED);
				f.gibs.remove(f.axe);
				f.axe = null;
			}
			orc.scripted = false;
			orc.team = 0;
			f.fighters.add(orc);
		}
		if (t == VARRO_FIELD + 8) {
			pose(varro, null, 0.2F);
			varro.facing = null;
			varro.toF = VARRO_AT[0];
			varro.toS = VARRO_AT[1];
		}
	}

	/** Grimsby's bolt: off his free hand, a streak of light into {@code at}, which falls. */
	private static void bolt(Film f, Actor mage, Actor at) {
		mage.e.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		sound(SoundEvents.EVOKER_CAST_SPELL, 1.3F, 0.5F);
		Vec3 from = mage.e.position().add(0.0, 1.4, 0.0).add(f.right.scale(-0.4)), to = at.e.position().add(0.0, 1.1, 0.0);
		int steps = Math.max(8, (int)(from.distanceTo(to) * 2.0));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.lerp(to, i / (double)steps);
			particle(f, i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ENCHANTED_HIT, p, 0.0, 0.0, 0.0);
		}
		at.fallAt = f.tick + 2;
	}

	private static void spell(Film f, int which, Actor mage, int into) {
		if (which == 2 && into < 12) {
			// Grimsby: glyphs gather and turn about him as he calls it up, his free hand raised
			if (into == 2) {
				mage.e.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
				sound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 0.8F);
			}
			for (int i = 0; i < 6; i++) {
				double turn = (into * 0.5 + i * Math.PI / 3.0);
				Vec3 p = mage.e.position().add(Math.cos(turn) * 1.3, 0.3 + into * 0.12, Math.sin(turn) * 1.3);
				particle(f, ParticleTypes.ENCHANT, p, 0.0, 0.0, 0.0);
				particle(f, ParticleTypes.ENCHANTED_HIT, p, 0.0, 0.02, 0.0);
			}
		}
		if (into == 10) {
			mage.e.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		if (into < 12) {
			return;
		}
		Vec3 aim = target(f, which, mage);
		switch (which) {
			case 0 -> {
				// Varro: fire, thrown into the thick of them
				if (into == 12) {
					sound(SoundEvents.BLAZE_SHOOT, 0.8F, 0.9F);
					sound(SoundEvents.GENERIC_EXPLODE.value(), 1.1F, 0.6F);
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
					sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.0F);
					sound(SoundEvents.BEACON_ACTIVATE, 1.4F, 0.7F);
					for (Actor a : f.legion) {
						if (a.up() && f.tick >= a.busyUntil && a.f < 19.0) {
							a.toF = a.f + 4.0;
							a.toS = a.s;
							a.stunUntil = f.tick + 30;
							a.e.hurtTime = a.e.hurtDuration = 10;
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
					sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.0F);
					sound(SoundEvents.EVOKER_CAST_SPELL, 0.8F, 0.9F);
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
					sound(SoundEvents.TRIDENT_THUNDER.value(), 1.2F, 0.5F);
					sound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.0F);
					f.flash = 0.75F;
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
	 * The fifth: he runs at the sorcerer through the trees, turning at each, the sorcerer's bolts going by him and into
	 * the trunks; he lunges, and the sorcerer isn't there. The dead drop, the host runs.
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
			quiet.toF = QUIET_PATH[1][0];
			quiet.toS = QUIET_PATH[1][1];
			quiet.restYaw = Float.NaN;
			pose(quiet, FilmPose.RUN, 0.5F);
			grue.facing = quiet;
		}
		if (t < QUIET) {
			return;
		}
		if (f.lungeAt < 0 && distance(quiet.f, quiet.s, quiet.toF, quiet.toS) < 0.3) {
			if (f.quietAt + 1 < QUIET_PATH.length) {
				f.quietAt++;
				quiet.toF = QUIET_PATH[f.quietAt][0];
				quiet.toS = QUIET_PATH[f.quietAt][1];
			} else {
				// the lunge
				f.lungeAt = t;
				quiet.speed = QUIET_LUNGE;
				quiet.toF = LUNGE_END;
				quiet.toS = 0.0;
				pose(quiet, FilmPose.LUNGE, 0.8F);
				sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.0F);
			}
		}
		// the sorcerer's bolts
		if (f.lungeAt < 0 && quiet.f < MANA_UNTIL && t > QUIET + 6 && (t - QUIET) % MANA_EVERY == 0) {
			boolean left = (t - QUIET) / MANA_EVERY % 2 == 0;
			pose(grue, left ? FilmPose.CAST_LEFT : FilmPose.CAST_RIGHT, 0.7F);
			sound(SoundEvents.EVOKER_CAST_SPELL, 0.5F, 0.7F);
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
			pose(grue, null, 0.3F);
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
			grue.e.setInvisible(true);
			grue.fled = true;
			sound(SoundEvents.SOUL_ESCAPE.value(), 0.5F, 1.0F);
			sound(SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 0.3F);
			// the dead with nothing holding them up, and the host with no one to hold it
			for (Actor a : f.legion) {
				if (!a.up() && a.sink < 0.5) {
					continue;
				}
				if (a.role == Role.DEAD) {
					a.sinkTo = a.sink;
					if (a.sink < 0.5) {
						a.fallAt = t + 2 + f.rnd.nextInt(16);
					}
				} else if (t >= a.busyUntil) {
					a.fled = true;
					a.foe = null;
					a.speed = FLEE;
					a.toF = a.f + 34.0;
					a.toS = a.s * 1.3;
				}
			}
		}
		if (since == 8) {
			pose(quiet, null, 0.2F);
			quiet.speed = 0.08;
		}
		if (since == 22) {
			// he looks about for him
			quiet.restYaw = f.yaw + 150.0F;
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
				swing(a);
				a.nextSwing = t + SWING_GAP + f.rnd.nextInt(SWING_SPREAD);
				if (f.rnd.nextInt(4) == 0) {
					sound(f.rnd.nextBoolean() ? SoundEvents.ZOMBIE_ATTACK_IRON_DOOR : SoundEvents.PLAYER_ATTACK_STRONG, 0.8F + f.rnd.nextFloat() * 0.4F, 0.22F);
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

	private static void swing(Actor a) {
		if (a.e instanceof Draugr draugr) {
			draugr.stageSwing();
		} else {
			a.e.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
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
		Vec3 chest = l.e.position().add(0.0, 1.1 * l.scale, 0.0), neck = l.e.position().add(0.0, 1.45 * l.scale, 0.0);
		switch (fin.kind) {
			case BEHEAD -> {
				if (into == 0) {
					// beaten to its knees
					swing(w);
					l.e.hurtTime = l.e.hurtDuration = 10;
					l.sinkTo = KNEEL * l.scale * (l.e instanceof Draugr ? 0.6 : 1.0);
					pose(l, FilmPose.KNEEL, 0.5F);
					blood(f, chest, along, 6, l.flesh.dark);
					sound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F, 0.5F);
				}
				if (into == 7) {
					pose(w, FilmPose.OVERHEAD, 0.5F);
				}
				if (into == 13) {
					pose(w, FilmPose.CHOP, 0.85F);
					swing(w);
					sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 0.7F);
				}
				if (into == 15) {
					maim(l, FilmPose.NO_HEAD);
					Display head = l.flesh.head != null ? item(f, new ItemStack(l.flesh.head), (float)l.scale) : cube(f, l.flesh.helm, 0.5F, 0.5F, 0.5F);
					fling(f, head, neck.add(0.0, 0.2, 0.0), across.scale(f.rnd.nextBoolean() ? 0.2 : -0.2).add(along.scale(0.08)).add(0.0, 0.42 + f.rnd.nextDouble() * 0.1, 0.0),
						30.0F);
					blood(f, neck, new Vec3(0.0, 1.0, 0.0), 16, l.flesh.dark);
					l.bleedUntil = t + 14;
					l.bleedUp = 1.45 * l.scale;
					sound(SoundEvents.PLAYER_ATTACK_STRONG, 0.6F, 0.8F);
				}
				if (into == 19) {
					l.fallAt = t;
				}
			}
			case SEVER -> {
				if (into == 0) {
					// the weapon arm, off at the shoulder
					swing(w);
					pose(w, FilmPose.CHOP, 0.8F);
					l.e.hurtTime = l.e.hurtDuration = 10;
					maim(l, FilmPose.NO_RIGHT_ARM);
					l.e.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
					pose(l, FilmPose.STAGGER, 0.6F);
					Vec3 shoulder = l.e.position().add(0.0, 1.35 * l.scale, 0.0);
					fling(f, cube(f, l.flesh.limb, 0.26F, 0.72F, 0.26F), shoulder,
						across.scale(f.rnd.nextBoolean() ? 0.17 : -0.17).add(along.scale(0.05)).add(0.0, 0.36 + f.rnd.nextDouble() * 0.1, 0.0), 34.0F);
					blood(f, shoulder, across, 14, l.flesh.dark);
					l.bleedUntil = t + 12;
					l.bleedUp = 1.35 * l.scale;
					sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.7F);
					sound(SoundEvents.PLAYER_ATTACK_STRONG, 0.6F, 0.7F);
				}
				if (into == 8) {
					pose(w, FilmPose.OVERHEAD, 0.5F);
				}
				if (into == 13) {
					// and cut down
					pose(w, FilmPose.CHOP, 0.85F);
					swing(w);
					blood(f, chest, along, 10, l.flesh.dark);
					sound(SoundEvents.PLAYER_ATTACK_STRONG, 0.7F, 0.6F);
				}
				if (into == 15) {
					l.fallAt = t;
				}
			}
			default -> {
				if (into == 0) {
					// run through
					pose(w, FilmPose.THRUST, 0.75F);
					swing(w);
					w.toF = w.f + df / d * 0.5;
					w.toS = w.s + ds / d * 0.5;
				}
				if (into == 3) {
					l.e.hurtTime = l.e.hurtDuration = 10;
					pose(l, FilmPose.STAGGER, 0.6F);
					blood(f, chest.add(along.scale(0.35)), along, 16, l.flesh.dark);
					sound(SoundEvents.PLAYER_ATTACK_STRONG, 0.6F, 0.8F);
				}
				if (into == 12) {
					// the blade drawn back out
					w.toF = w.f - df / d * 0.5;
					w.toS = w.s - ds / d * 0.5;
					blood(f, chest, along.scale(-1.0), 8, l.flesh.dark);
				}
				if (into == 14) {
					l.fallAt = t;
				}
			}
		}
		if (into >= FINISH_TICKS - 2) {
			pose(w, null, 0.3F);
			w.facing = null;
			l.facing = null;
			return true;
		}
		return false;
	}

	/** A head or an arm gone: not drawn from here on. */
	private static void maim(Actor a, int what) {
		a.gone |= what;
		if (a.e instanceof Draugr draugr) {
			draugr.stageMaim(what == FilmPose.NO_HEAD ? Draugr.MAIM_HEAD : Draugr.MAIM_ARM);
		}
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

	private static Display cube(Film f, Block block, float w, float h, float d) {
		Display.BlockDisplay e = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, f.level);
		e.setBlockState(block.defaultBlockState());
		e.setTransformation(new Transformation(new Vector3f(-w / 2.0F, -h / 2.0F, -d / 2.0F), null, new Vector3f(w, h, d), null));
		return e;
	}

	private static Display item(Film f, ItemStack stack, float scale) {
		Display.ItemDisplay e = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, f.level);
		e.setItemStack(stack);
		e.setItemTransform(ItemDisplayContext.NONE);
		e.setTransformation(new Transformation(new Vector3f(), null, new Vector3f(scale), null));
		return e;
	}

	/** Sends {@code e} flying from {@code at} at {@code speed}, turning over as it goes. */
	private static Gib fling(Film f, Display e, Vec3 at, Vec3 speed, float spin) {
		e.setId(nextId--);
		e.snapTo(at.x, at.y, at.z, f.rnd.nextFloat() * 360.0F, 0.0F);
		f.level.addEntity(e);
		f.props.add(e);
		Gib gib = new Gib(e, at, speed, spin);
		f.gibs.add(gib);
		return gib;
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
				if (g.bounced || g == f.axe) {
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
			g.e.setXRot(g.rest ? (g == f.axe ? 125.0F : 90.0F) : g.turn * 0.7F);
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
				sound(m.tree ? SoundEvents.ZOMBIE_ATTACK_IRON_DOOR : SoundEvents.FIRECHARGE_USE, m.tree ? 1.4F : 0.6F, 0.45F);
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

	/** {@code a} takes up {@code to} (null lets it go), this quickly. */
	private static void pose(Actor a, float @Nullable [] to, float rate) {
		if (to != null && a.poseTo == null && a.weight < 0.05F) {
			System.arraycopy(to, 0, a.pose, 0, FilmPose.NUMBERS);
			System.arraycopy(to, 0, a.poseO, 0, FilmPose.NUMBERS);
		}
		a.poseTo = to;
		a.poseRate = rate;
	}

	/** A film actor's pose this frame for {@link FilmPose}, or null when it has none. */
	public static float @Nullable [] pose(Entity e, float partialTicks) {
		Film f = film;
		Actor a = f == null ? null : f.byEntity.get(e);
		if (a == null || (a.weight < 0.005F && a.weightO < 0.005F && a.gone == 0)) {
			return null;
		}
		float[] out = new float[FilmPose.LENGTH];
		for (int i = 0; i < FilmPose.NUMBERS; i++) {
			out[i] = Float.isNaN(a.pose[i]) || Float.isNaN(a.poseO[i]) ? a.pose[i] : Mth.lerp(partialTicks, a.poseO[i], a.pose[i]);
		}
		out[FilmPose.WEIGHT] = Mth.lerp(partialTicks, a.weightO, a.weight);
		out[FilmPose.GONE] = a.gone;
		return out;
	}

	/** Moves one actor toward where it's headed, rises or sinks it, turns it, eases its pose, and drops it when its time comes. */
	private static void move(Film f, Actor a, int t) {
		LivingEntity e = a.e;
		if (a.fallAt >= 0 && t >= a.fallAt && !a.down) {
			a.down = true;
			a.sinkTo = Math.min(a.sinkTo, 0.0);
			a.poseTo = null;
			e.hurtTime = e.hurtDuration = 10;
			e.setHealth(0.0F);
			if (a.role == Role.DEAD) {
				particle(f, ParticleTypes.LARGE_SMOKE, e.position().add(0.0, 0.8, 0.0), 0.0, 0.05, 0.0);
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
		Vec3 at = f.at(a.f, a.s, -a.sink);
		e.setPos(at.x, at.y, at.z);
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
		e.setYRot(a.yaw);
		e.setYHeadRot(a.yaw);
		e.setYBodyRot(a.yaw);
		e.setRemainingFireTicks(0);
		e.calculateEntityAnimation(false);
		// its pose, eased
		System.arraycopy(a.pose, 0, a.poseO, 0, FilmPose.NUMBERS);
		a.weightO = a.weight;
		if (a.poseTo != null) {
			a.weight += (1.0F - a.weight) * a.poseRate;
			for (int i = 0; i < FilmPose.NUMBERS; i++) {
				float to = a.poseTo[i];
				a.pose[i] = Float.isNaN(to) || Float.isNaN(a.pose[i]) ? to : a.pose[i] + (to - a.pose[i]) * a.poseRate;
			}
		} else {
			a.weight += (0.0F - a.weight) * a.poseRate;
		}
		if (a.role == Role.SORCERER && !a.fled && f.tick % 3 == 0) {
			particle(f, ParticleTypes.WITCH, e.position().add(spread(f, 0.6), 1.2 + f.rnd.nextDouble(), spread(f, 0.6)), 0.0, 0.02, 0.0);
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

	private static void particle(Film f, ParticleOptions type, Vec3 p, double vx, double vy, double vz) {
		f.level.addParticle(type, true, true, p.x, p.y, p.z, vx, vy, vz);
	}

	private static double spread(Film f, double by) {
		return (f.rnd.nextDouble() * 2.0 - 1.0) * by;
	}

	private static void sound(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
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
			Vec3 orc = f.marked != null ? f.marked.e.getPosition(partialTicks) : f.at(VARRO_AT[0] + 1.7, VARRO_AT[1], 0.0);
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
		} else if (t < LUNGE_SHOT) {
			// from behind the sorcerer (his shoulder at the right edge), the fifth coming on, on the left third
			Vec3 q = f.stranger != null ? f.stranger.e.getPosition(partialTicks) : f.at(40.0, 0.0, 0.0);
			pos = f.at(SORCERER_F + 2.3, 1.3, 2.0);
			look = q.add(0.0, 1.2, 0.0);
			third = 0.5F;
		} else if (t < END) {
			// side on: he comes in from the left, the sorcerer on the right third, and then nobody there
			float k = smooth((t - LUNGE_SHOT) / (END - LUNGE_SHOT));
			pos = f.at(51.2, 5.6 - 1.0 * k, 1.4);
			look = f.at(50.3, 0.0, 1.3);
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
