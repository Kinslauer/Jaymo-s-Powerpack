package com.jaymoskingdoms.client.films;

import com.geckolib.animatable.GeoEntity;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.hud.HudZones;
import com.jaymoskingdoms.films.FilmActor;
import com.jaymoskingdoms.films.LoreFilms;
import com.jaymoskingdoms.films.Moves;
import com.jaymoskingdoms.inhabitants.Draugr;
import com.jaymoskingdoms.nether.Imp;
import com.jaymoskingdoms.nether.Imps;
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
	 * rising, Varro and his orc, Varro's fire, Varro and Grimsby back to back, Thessaly and Ambrose back to back and the
	 * working they make together, the fifth's donning and his run (chased, then from behind the sorcerer, then side on), the end.
	 */
	static final int MELEE = 90, LINE = MELEE + Relay.TICKS, SUMMON = LINE + 110, STRIKE = SUMMON + 22, RISING = SUMMON + 44, VARRO = SUMMON + 146,
		VARRO_CLOSE = VARRO + 36, VARRO_CAST = VARRO + 78, CAST_TICKS = 36,
		DUO1 = VARRO_CAST + CAST_TICKS, DUO2 = DUO1 + 104, DON = DUO2 + 100, QUIET = DON + 40, REVERSE = QUIET + 52, END = REVERSE + 120,
		LENGTH = LoreFilms.FILM_TICKS;
	/** Varro's move, beat by beat: the orc runs in, raises its axe, chops; the disarm, the kick, its knees, his grip, the mark, its bow, it rises. */
	static final int ORC_CHARGE = VARRO - 34, CHOP_UP = VARRO + 12, CHOP = VARRO + 16, DISARM = VARRO + 20, KICK = VARRO + 26, KNEES = VARRO + 29,
		GRIP = VARRO + 33, MARK_SET = VARRO + 42, BOW = VARRO + 56, MARK_RISE = VARRO + 66;
	/**
	 * The heroes, one after another, near the film's start. Andy: "The heroes should be fighting from the very beginning
	 * of the clip, i want a frontal shot of each from various distances, of thessaly and ambrose shooting bolts of mana at
	 * the flying imps and the odd orc, rushing out from her frontal pov following the bolt to the impact on the imp,
	 * exploding it, out of the explosion forms ambrose light in a smooth transition, back close on him, and the same for
	 * them all except quiet". Each has a leg of it: the camera in front of them (HOLD ticks; they let fly CAST ticks in),
	 * after their bolt as it goes (FLY) to what it is for, which it bursts, and out of the burst the next one's light,
	 * which the camera goes back with (BACK) to that one's face. The last leg ends on its burst (LAST ticks in all).
	 * WHO casts each leg (as {@link #caster} numbers them), what at ({f, s, how high; on the ground it is an orc, in the
	 * air an imp}), how far in front of them the camera sits, and the move they throw with.
	 */
	static final class Relay {
		static final int HOLD = 10, FLY = 8, BACK = 18, LEG = HOLD + FLY + BACK, LAST = 30, CAST = 4, TICKS = 3 * LEG + LAST;
		static final int[] WHO = {1, 3, 2, 0};
		static final double[][] PREY = {{21.5, -5.0, 4.6}, {22.5, 5.5, 5.2}, {23.0, -9.5, 0.0}, {22.5, 12.0, 4.4}};
		static final double[] NEAR = {2.6, 4.4, 3.3, 5.6};
		static final String[] MOVES = {"cast_both", "cast_left", "cast_blade", "point"};
		static final double SHORT = 3.2, ASIDE = 0.55, EYE = 1.45, FACE = 1.35, BOB = 0.3;

		private Relay() {
		}
	}
	/**
	 * The heroes are in the fight before their own beats (Andy: "quiet, varro and grimsby are all involved in the battle
	 * among the soldiers before their specific beats as well"): where the fifth fights, in his rags, how long before the
	 * orc comes for Varro and before the white comes for the fifth each breaks off, how many of the sorcerer's are sent at
	 * each of them at a time, how often a cut of theirs kills that early, and how often Thessaly's and Ambrose's bolts
	 * go up at an imp (one in this many).
	 */
	/**
	 * The imps come through rifts. Andy: "The imps can also open portals like the arsenal uses to travel through, that
	 * should be how they make their entrance to the overworld in the long defence scene". A tear of nether light opens in
	 * the air where each will fly (GATE_OPENS ticks to its full GATE_SIZE across, half as tall again), the imp comes out
	 * of it, and GATE_STAYS ticks after it began the tear draws shut behind it over GATE_SHUTS. The sorcerer's four come
	 * from GATE_FIRST, one every GATE_EVERY ticks; the three the heroes bring down from GATE_PREY.
	 */
	static final int GATE_OPENS = 8, GATE_STAYS = 20, GATE_SHUTS = 8, GATE_FIRST = 6, GATE_EVERY = 9, GATE_PREY = 44, GATE_IDS = 900_000;
	static final float GATE_SIZE = 2.4F;
	static final int RIFT_RIM = 0xF2D8FF;
	static final double[] QUIET_FIGHTS = {10.0, -7.5};
	static final int VARRO_READY = 44, QUIET_READY = 56, EARLY_FOES = 1, BOLT_AT_IMP = 3;
	static final float EARLY_KILLS = 0.06F;
	static final double QUIET_WALK = 0.22, IMP_BOLT_REACH = 20.0;
	static final int PALE = 0xF4F4FF;
	/** Fading in and out (ticks), and how long sneak is held to skip. */
	static final int FADE = 20, SKIP_HOLD = 20;
	/** The staging, in blocks ahead of where you stood ({@code f}) and to the side ({@code s}). */
	static final double MAGES_F = 13.5, SORCERER_F = 52.0, FIELD_NEAR = 8.5, FIELD_FAR = 31.0, FIELD_HALF = 25.0;
	static final double[] STRANGER_AT = {3.5, -4.5}, VARRO_AT = {14.0, 9.0}, MARKED_AT = {27.0, 10.0};
	/**
	 * The heroes stand in the fight, not behind it (Andy: "among the fighters instead of in the back in a line"): where
	 * Grimsby, Thessaly and Ambrose start ({f, s}; Varro at VARRO_AT), how far Grimsby's sword takes him from there, and how
	 * near a pair of fighters may begin to any of them.
	 */
	static final double[][] HEROES_AT = {{15.0, -8.0}, {12.5, -3.0}, {13.5, 3.0}};
	static final double HERO_LEASH = 6.0, HERO_ROOM = 2.6;
	/** A Sylvan is drawn smaller than a man (BodySizes: the smallest of the heritages). */
	static final double SYLVAN_SCALE = 0.92;
	/**
	 * What the heroes wear. Andy: "ichorcloth robes on thessaly, steel armor on varro, no helmet, ambrose in the wizard
	 * robes, grimsby with the ranger hood and armor". Thessaly, Varro, Ambrose, Grimsby; head, chest, legs, feet.
	 */
	static final EquipmentSlot[] GEAR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	static final String[][] HERO_GEAR = {
		{"", "jaymospowers:ichorcloth_robe", "jaymospowers:ichorcloth_leggings", "jaymospowers:ichorcloth_boots"},
		{"", "jaymoskingdoms:steel_plate_chestplate", "jaymoskingdoms:steel_plate_leggings", "jaymoskingdoms:steel_plate_boots"},
		{"wizards:wizard_robe_head", "wizards:wizard_robe_chest", "wizards:wizard_robe_legs", "wizards:wizard_robe_feet"},
		{"archers:ranger_armor_head", "archers:ranger_armor_chest", "archers:ranger_armor_legs", "archers:ranger_armor_feet"}};
	/**
	 * What the hearthlanders of the line wear. Andy: "they should all have different combinations of leather and iron or
	 * chainmail". What there is for the head, the chest, the legs and the feet; each man has his own pick of them, never a
	 * whole suit of one make (FilmGear draws it on him filthy).
	 */
	static final String[][] SOLDIER_WEAR = {
		{"minecraft:iron_helmet", "minecraft:chainmail_helmet", "minecraft:leather_helmet"},
		{"minecraft:iron_chestplate", "minecraft:chainmail_chestplate", "minecraft:leather_chestplate"},
		{"minecraft:iron_leggings", "minecraft:chainmail_leggings", "minecraft:leather_leggings"},
		{"minecraft:iron_boots", "minecraft:chainmail_boots", "minecraft:leather_boots"}};
	/** Steps down the list of every such pick from one man to the next (shares no factor with how many picks there are). */
	static final int WEAR_STEP = 37, WEAR_FIRST = 11;
	/**
	 * The heroes fight in pairs. Andy: Varro "should do the cast after the orc in balming, then rush in afterward alongside
	 * grimsby, who is in his own clash with an orc, giving the two a longer shot side to side, back to back, fighting hard,
	 * same with ambrose and thessaly. They get a combination magic attack as she is a sorcerer and he, a mage".
	 * Where each pair stands ({f, s}: one DUO_APART behind that looking back, the other as far ahead looking on), how near
	 * a foe must be for them to cut at it and how far off the sorcerer's are called in on them (DUO_FOES on each at a time,
	 * looked for every DUO_FEED ticks, coming at DUO_FOE_RUN), how often they strike and how often that kills, and how fast
	 * Varro crosses the field to Grimsby.
	 */
	static final double[] DUO1_AT = {16.5, -5.5}, DUO2_AT = {13.5, 0.0};
	static final double DUO_APART = 0.6, DUO_LEASH = 2.6, DUO_REACH = 2.1, DUO_SEEK = 15.0, DUO_FOE_RUN = 0.24, VARRO_RUSH = 0.45, GRIMSBY_RUN = 0.2;
	static final int DUO_FOES = 2, DUO_FEED = 8, DUO_SWING = 7, DUO_SWING_SPREAD = 6;
	static final float DUO_KILLS = 0.45F;
	/**
	 * Grimsby's orc: sent for DUO_GATHER ticks before the cut to the two of them, at him CLASH_BEGINS before it (every blow
	 * between them blade on blade), run through CLASH_ENDS after it; GRIMSBY_CUT after the cut he lets his spell go down his
	 * sword, the field's length.
	 */
	static final int DUO_GATHER = 60, CLASH_BEGINS = 24, CLASH_ENDS = 38, GRIMSBY_CUT = 66;
	/**
	 * Thessaly and Ambrose throw a bolt each every DUO_BOLT ticks at what comes for them (within BOLT_REACH), and COMBO
	 * ticks into their shot work together: her ward drawn in about the two of them, his light called down into it, and
	 * COMBO_BURST ticks on it goes out from them as one ring, COMBO_REACH blocks in COMBO_RUN ticks, and the sorcerer's it
	 * passes fall.
	 */
	static final int DUO_BOLT = 10, COMBO = 44, COMBO_BURST = 18, COMBO_RUN = 14, COMBO_REST = 44, RING_POINTS = 56;
	static final double BOLT_REACH = 11.0, COMBO_REACH = 11.0;
	static final int VIOLET = 0xB98CFF, GOLD = 0xFFD35A;
	/** Nobody standing is nearer another than this (blocks), and is moved off no faster than this a tick. Andy: "bunched too close together and clipping into each other". */
	static final double ROOM = 1.2, ROOM_GIVE = 0.09, PAIR_ROOM = 3.6;
	/**
	 * The ground is level where a pair of heroes stands and where the camera looks at them from ({f, s}, within LEVEL_FLAT,
	 * easing back to the lie of the land over LEVEL_EASE), and none of the dead come up within DUO_KEPT of there.
	 */
	static final double[][] LEVEL = {{DUO1_AT[0] + 1.2, DUO1_AT[1] + 3.0}, {DUO2_AT[0] + 0.2, DUO2_AT[1] + 2.6}};
	static final double LEVEL_FLAT = 7.0, LEVEL_EASE = 5.0, DUO_KEPT = 6.5;
	/** How often Grimsby, Thessaly and Ambrose each throw a bolt into the fight (ticks), and when in that their own comes. */
	static final int[][] BOLTS = {{26, 0}, {34, 11}, {30, 19}};
	/** Ground the fighting is kept off while a close shot is taken across it: {from tick, to tick, f, s, how far round}. */
	/** How far in front of a hero their face shot is taken from, how far behind their shoulder shot, and how far off Varro his close one. */
	static final double HERO_FRONT = 4.4, HERO_BEHIND = 4.0, VARRO_NEAR = 3.0;
	/** The ground between a hero and their camera is cleared of fighters this many ticks before the cut to them. */
	static final int HERO_CLEAR_EARLY = 8;
	static final double[][] CAMERA_CLEAR = {{LINE - 30, SUMMON, 19.3, 9.5, 4.2}, {VARRO - 30, VARRO_CAST, VARRO_AT[0] + 1.3, VARRO_AT[1] + 2.8, 3.0},
		{DUO1 - 30, DUO2, DUO1_AT[0] + 2.4, DUO1_AT[1] + 5.0, 3.2}, {DUO1 - 30, DUO2, DUO1_AT[0] + 1.5, DUO1_AT[1] + 2.8, 2.0},
		{DUO2 - 30, DON, DUO2_AT[0] + 0.4, DUO2_AT[1] + 5.2, 2.4}, {DUO2 - 30, DON, DUO2_AT[0] + 0.2, DUO2_AT[1] + 2.9, 2.0}, {DON - 20, QUIET, STRANGER_AT[0] - 1.5, STRANGER_AT[1] - 0.8, 3.2}};
	/** How far Varro and his orc go looking for the dead once they charge. */
	static final double HUNT = 80.0;
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
	/**
	 * Steel on steel. Andy: "blades clashing and some of the fighters should have shields so the fight looks realistic,
	 * sparks flying from parries and splintering wood from shattering shields". A blow at a shield is caught on it more
	 * often (SHIELDED); CLASHED of the rest are met by the other's own cut, blade on blade. LANDS ticks into the swing the
	 * two meet: sparks off a parry or a clash (PARRY_SPARKS, CLASH_SPARKS, thrown at SPARK_SPEED), chips off a shield, and a
	 * shield that has taken as many blows as it can bear (SHIELD_LASTS, and up to SHIELD_SPREAD more) breaks: splinters
	 * (SPLINTERS), SHIELD_BOARDS boards of it sent flying, and its bearer knocked back for SHATTER_STUN ticks. One orc of
	 * the host in ORC_SHIELDS carries one too.
	 */
	static final float SHIELDED = 0.62F, CLASHED = 0.22F;
	static final int LANDS = 4, PARRY_SPARKS = 9, CLASH_SPARKS = 16, CHIPS = 5, SPLINTERS = 26, SHIELD_BOARDS = 3, SHIELD_LASTS = 2, SHIELD_SPREAD = 4,
		SHATTER_STUN = 12, ORC_SHIELDS = 3;
	static final double SPARK_SPEED = 1.1, MEET_HIGH = 1.25;
	static final int PARRY = 0, CLASH = 1, SHIELD = 2;
	/** Flying heads, limbs and Varro's orc's axe: gravity (blocks a tick, a tick), and how much of its speed one bounce keeps. */
	static final double GRAVITY = 0.045, BOUNCE = 0.35;
	/**
	 * The opening's finishers, where the first two shots see them: {f, s, kind, tick, 1 if the defender wins, 1 if the foe
	 * is a Warrior-born}.
	 */
	static final double[][] STAGED = {
		{27.0, 6.5, BEHEAD, 26, 1, 0}, {23.5, 11.0, SEVER, 50, 0, 1}, {23.2, 2.6, IMPALE, 72, 1, 1},
		{20.2, 5.0, BEHEAD, LINE + 26, 1, 1}, {22.0, -1.2, SEVER, LINE + 58, 1, 0}, {19.6, -4.2, IMPALE, LINE + 88, 0, 0}};

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
	/**
	 * The imps. Andy: "add the new imps too when they are ready, their fireballs also catch the trees and surroundings in a
	 * blaze". They are the game's own imps (nether.Imp), on the sorcerer's side: each flies a ring over the field
	 * (IMP_RINGS: {middle f, middle s, how wide, where on it it starts}; IMP_TURN round it a tick, IMP_HIGH up and IMP_STEP
	 * more for each next one). From IMP_FIRST, every IMP_EVERY ticks (each IMP_STAGGER after the last), one hangs in the air,
	 * gathers a fireball over its head for IMP_CHARGE ticks and IMP_RELEASE after that throws it (BALL_SPEED) into the crown
	 * of a tree not yet alight between IMP_NEAR and IMP_FAR of it, which lights every tree within BALL_LIGHTS. IMP_SWOOP
	 * ticks into each round it dives to IMP_LOW over the fighting for IMP_SWOOP_TICKS. When the sorcerer is gone they fly
	 * off (IMP_FLEE a tick, up to IMP_AWAY).
	 */
	static final double[][] IMP_RINGS = {{24.0, -8.0, 6.0, 0.0}, {31.0, 9.0, 7.0, 2.0}, {20.0, 6.0, 5.0, 4.0}, {35.0, -5.0, 6.0, 1.0}};
	static final String[] IMP_SKINS = {"imp", "imp", "zombified_imp", "imp"};
	static final int IMP_FIRST = 60, IMP_EVERY = 170, IMP_STAGGER = 40, IMP_CHARGE = 60, IMP_RELEASE = 5, IMP_SWOOP = 100, IMP_SWOOP_TICKS = 16;
	static final double IMP_TURN = 0.05, IMP_HIGH = 4.0, IMP_STEP = 0.5, IMP_SPEED = 0.45, IMP_NEAR = 4.0, IMP_FAR = 15.0, IMP_LOW = 1.9,
		IMP_CLIMB = 0.2, BALL_SPEED = 0.7, BALL_LIGHTS = 2.5, BALL_HEIGHT = 1.85, IMP_FLEE = 0.6, IMP_AWAY = 14.0;
	/** His cloak sets whole again this long after the sorcerer is gone (ticks). */
	static final int CLOAK_SETS = 14;
	static final double[][] SLING_TREES = {{45.0, 3.6}, {45.0, -3.6}};
	static final int SLING_TICKS = 12, GLIDE_TICKS = 54, GLIDE_CUT = 20, GLIDE_REACH = 8, GLIDE_MISS = 3;
	static final double SLING_BACK = 1.4, GLIDE_END = SORCERER_F - 1.3, GLIDE_HIGH = 0.6, GLIDE_ARC = 0.5, GLIDE_LAND = 0.3, GLIDE_DROP = 0.14;
	static final double QUIET_RUN = 0.5, LUNGE_END = 53.6;
	/** What he carries: the pack's rapier (Jaymo's Powers), or a sword where a game hasn't got it. */
	/**
	 * Until then. Andy: "The quiet one should stand in the back until his time comes, then he dons the suit with the anti
	 * symbiote. Before then he is just in tattered cloths". From DON the white comes up out of the ground over him (motes
	 * of it climbing him every tick for DON_CLIMB ticks): his legs and trunk are in it at DON_HALF, all of him at
	 * DON_WHOLE, when the cloak winds out of it in its ribbons and the blade is in his hand; at DON_SET the cloak has set
	 * whole. Then his run.
	 */
	static final String QUIET_RAGS = "stranger_rags", QUIET_HALF = "stranger_half", QUIET_SUIT = "stranger";
	static final int DON_CLIMB = 24, DON_HALF = 10, DON_WHOLE = 20, DON_SET = 30;
	static final int WHITE = 0xF2F0E6;
	static final String QUIET_BLADE = "jaymospowers:iron_rapier|minecraft:iron_sword";
	/** The turns of his run he flips off the tree at, and the ones he rolls through. */
	static final int[] QUIET_FLIPS = {2, 5}, QUIET_ROLLS = {3};
	/** His cloak's tethers: the first of them swing him from tree to tree (thrown this far up the trunk); the rest hunt for themselves. */
	static final int SWING_TETHERS = 2;
	static final double TETHER_HIGH = 5.2;
	/**
	 * Andy: the cords attach "like the tendrils do, suspending him in mid-air while attached and he flips and dodges between
	 * tethers". Through his run he hangs from them (the loop "hang"): SWING_CORDS take the trees he turns at in turn, two
	 * of them fast at a time (the tree ahead and the one he has just left), and carry him AIR_LOW off the ground, AIR_ARC
	 * higher in the middle of each swing; he is that high AIR_RISE ticks after the first takes hold.
	 */
	static final int[] SWING_CORDS = {0, 1, 4};
	static final double AIR_LOW = 1.1, AIR_ARC = 0.9;
	static final int AIR_RISE = 8;
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
	static final float DIM = 0.08F, SUMMONING = 0.45F;
	static final float BAR_SHARE = 0.11F;
	/** The rule of thirds: how far the camera is turned off its subject (degrees) to set it on a third line. */
	static final float THIRD = 18.0F;
	/** The vanilla HUD hidden while a film plays. */
	private static final Identifier[] HIDDEN_HUD = {VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS};

	enum Role {
		SOLDIER, MAGE, STRANGER, SORCERER, DEAD, HOST, MARKED, GUARD, IMP
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
		/** Goes looking for the dead wherever they are (Varro and his orc, once they charge). */
		boolean hunter;
		/** Where it keeps to, and how far from there it will follow a fight (0: anywhere). */
		double homeF, homeS, leash;
		/** One of a pair of heroes back to back: holds its ground and takes what comes from ahead (1) or from behind (-1). */
		int arc;
		/** How often one of its cuts kills what it is at (the heroes; 0 for everyone whose fights end in finishers). */
		float kills;
		/** The blows its shield has taken, and how many it can. */
		int shieldHits, shieldLasts = SHIELD_LASTS;
		/** An imp: where on its ring it is, when it began to gather its fireball (-1 not), and the tree that's for. */
		double ring;
		int chargeAt = -1;
		double @Nullable [] aim;
		/** An imp's way in: where its rift opens ({f, s, how high}); it comes through at {@code riseAt}. */
		double @Nullable [] gate;
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

	/** Two fighters' weapons about to meet: when, whose blow and who meets it, and how (PARRY, CLASH, SHIELD). */
	record Meet(int tick, Actor striker, Actor other, int kind) {
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
		final List<Meet> meets = new ArrayList<>();
		final List<Rise> rises = new ArrayList<>();
		final List<Gib> gibs = new ArrayList<>();
		final List<Mana> mana = new ArrayList<>();
		/** The imps, and their fireballs in the air ({@link Mana#tree}: always). */
		final List<Actor> imps = new ArrayList<>();
		final List<Mana> balls = new ArrayList<>();
		/** The trees on fire, in the order they caught, and every flame by its number. */
		final List<Fire> fires = new ArrayList<>();
		final Map<double[], Fire> fireOf = new IdentityHashMap<>();
		final Map<Integer, Display.BlockDisplay> flames = new HashMap<>();
		@Nullable Actor grimsby, thessaly, ambrose, varro, stranger, sorcerer, marked;
		/** The orc Grimsby is fighting when Varro reaches him. */
		@Nullable Actor clasher;
		/** What each leg of the heroes' relay is thrown at. */
		final @Nullable Actor[] prey = new Actor[Relay.WHO.length];
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
		/** The land the film laid over the world's: what each block it changed was (put back when it ends), and how high each column now stands. */
		final Map<BlockPos, BlockState> land = new java.util.LinkedHashMap<>();
		double @Nullable [] landY;
		int landX, landZ, landWide;

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

		/** The ground's height at {@code x, z}: the film's own land where it has laid some, else the world's. */
		double ground(double x, double z) {
			int ix = Mth.floor(x) - this.landX, iz = Mth.floor(z) - this.landZ;
			if (this.landY != null && ix >= 0 && iz >= 0 && ix < this.landWide && iz < this.landY.length / this.landWide
				&& !Double.isNaN(this.landY[iz * this.landWide + ix])) {
				return this.landY[iz * this.landWide + ix];
			}
			return this.real(x, z);
		}

		/** The world's own ground at {@code x, z}, looked for near the height you stood at (so a cave or a house has a floor). */
		double real(double x, double z) {
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
		ShadowFlameParticle.register();
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

	/** The hour a film is set at (ticks into the day: night, the moon well up), and a day's length. */
	static final long NIGHT = 15500L, DAY = 24000L;
	/** How bright the film's night is under its moon (the lightmap's sky factor: 1 is day, about a quarter a plain night). */
	public static final float MOONLIGHT = 0.85F;

	/** The hour this game's clocks read while a film plays, on the day {@code time} is in (FilmNightMixin). */
	public static long nightTime(long time) {
		return time - Math.floorMod(time, DAY) + NIGHT;
	}

	/**
	 * The fog. Andy: "a low rolling fog from the sorcerers side, volumetric like the smoke ability and ambient like there is
	 * on some areas of the map". It comes over the field from behind the sorcerer: its front is at FOG_FROM when the film
	 * opens and FOG_TO by its end, and behind the front, FOG_HALF either side of the road, FOG_PUFFS puffs a tick roll
	 * along the ground toward the Keep (FOG_DRIFT a tick), none higher than FOG_HIGH. It is this game's own (not on the
	 * tape: the same fog rolls over a staging and a replay).
	 */
	static final double FOG_FROM = 46.0, FOG_TO = 4.0, FOG_HALF = 30.0, FOG_HIGH = 0.4, FOG_DRIFT = 0.03, FOG_DEEP = 34.0;
	/** Banks begun a tick, how long one lasts (ticks), how big it is across (blocks, and as much again at random), its grey and how solid. */
	static final int FOG_PUFFS = 5, FOG_LIFE = 160;
	static final float FOG_SIZE = 1.0F, FOG_GREY = 0.62F, FOG_ALPHA = 0.3F;

	private static void fog(Film f) {
		double front = Mth.lerp(Math.min(1.0, f.tick / (double)(LENGTH - FADE)), FOG_FROM, FOG_TO);
		var rnd = f.level.getRandom();
		for (int i = 0; i < FOG_PUFFS; i++) {
			// (thickest at the front, thinning back toward where it comes from)
			double back = rnd.nextDouble();
			Vec3 p = f.at(front + back * back * FOG_DEEP, (rnd.nextDouble() * 2.0 - 1.0) * FOG_HALF, 0.25 + rnd.nextDouble() * FOG_HIGH);
			Vec3 drift = f.fwd.scale(-FOG_DRIFT * (0.5 + rnd.nextDouble()));
			com.jaymoskingdoms.client.disasters.StormsClient.fog(f.level, p.x, p.y, p.z, drift.x, drift.z, FOG_SIZE * (1.0F + rnd.nextFloat()),
				FOG_LIFE + rnd.nextInt(FOG_LIFE / 2), FOG_GREY + rnd.nextFloat() * 0.1F, FOG_ALPHA);
		}
	}

	// ---- the land ---------------------------------------------------------------------------------------------------------

	/**
	 * The land. Andy: "there should also be more hills in the landscape, to make it look like a real area of the world".
	 * Over the stage (LAND_BACK to LAND_FRONT ahead of you, LAND_HALF either side) the film lays hills of earth and grass on
	 * top of the ground that's there, on this screen only, and takes them away again when it ends: the field rolls (ROLL
	 * blocks at most), the sorcerer stands on a knoll, the Keep on its own rise, and wooded ridges close the field in at the
	 * sides and behind him. Nothing is raised within LAND_FLAT of where you stand. A worn track runs down from the gate to
	 * the fight, and grass and ferns grow over all of it. Everyone and everything on the stage stands on it.
	 */
	static final double LAND_BACK = -86.0, LAND_FRONT = 94.0, LAND_HALF = 62.0, LAND_EDGE = 7.0, LAND_FLAT = 2.5, LAND_EASE = 7.0;
	static final double ROLL = 2.6, KNOLL = 3.0, KNOLL_WIDE = 8.0, KEEP_HILL = 4.0, KEEP_HILL_WIDE = 15.0, RIDGE = 7.5, RIDGE_AT = 47.0, RIDGE_WIDE = 7.0,
		FAR_RIDGE = 9.0, FAR_RIDGE_AT = 79.0, TRACK_HALF = 1.7, TRACK_FROM = KEEP_F + 4.0, TRACK_TO = 30.0;
	/** One column in this many grows grass; one in this many a fern. */
	static final int GRASS_IN = 5, FERN_IN = 28;
	private static final int LAND_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** How far the land is raised over the world's ground at the stage's {@code (fw, s)}, in blocks. */
	static double hill(double fw, double s) {
		double h = rough(fw, s);
		for (double[] level : LEVEL) {
			float lie = smooth((float)((distance(fw, s, level[0], level[1]) - LEVEL_FLAT) / LEVEL_EASE));
			h = Mth.lerp(lie, rough(level[0], level[1]), h);
		}
		return h;
	}

	/** The land's own lie, before any of it is levelled. */
	private static double rough(double fw, double s) {
		double roll = 0.5 + 0.22 * Math.sin(fw * 0.11 + 1.3) * Math.cos(s * 0.09 - 0.4) + 0.16 * Math.sin(fw * 0.23 - s * 0.17 + 2.0)
			+ 0.12 * Math.cos(s * 0.31 + fw * 0.07);
		double h = ROLL * Mth.clamp(roll, 0.0, 1.0);
		h += KNOLL * Math.exp(-((fw - SORCERER_F - 2.0) * (fw - SORCERER_F - 2.0) + s * s) / (2.0 * KNOLL_WIDE * KNOLL_WIDE));
		h += KEEP_HILL * Math.exp(-((fw - KEEP_F) * (fw - KEEP_F) + s * s * 0.3) / (2.0 * KEEP_HILL_WIDE * KEEP_HILL_WIDE));
		double side = Math.abs(s) - RIDGE_AT;
		h += RIDGE * (0.65 + 0.35 * Math.sin(fw * 0.08 + Math.signum(s))) * Math.exp(-side * side / (2.0 * RIDGE_WIDE * RIDGE_WIDE));
		h += FAR_RIDGE * (0.7 + 0.3 * Math.cos(s * 0.07)) * Math.exp(-(fw - FAR_RIDGE_AT) * (fw - FAR_RIDGE_AT) / (2.0 * RIDGE_WIDE * RIDGE_WIDE));
		double edge = Math.min(Math.min(fw - LAND_BACK, LAND_FRONT - fw), LAND_HALF - Math.abs(s)) / LAND_EDGE;
		double near = (Math.sqrt(fw * fw + s * s) - LAND_FLAT) / LAND_EASE;
		return h * smooth((float)edge) * smooth((float)near);
	}

	/** Lays the land (see {@link #hill}); {@link #unlay} takes it away. */
	private static void lay(Film f) {
		double[][] corners = {{LAND_BACK, -LAND_HALF}, {LAND_BACK, LAND_HALF}, {LAND_FRONT, -LAND_HALF}, {LAND_FRONT, LAND_HALF}};
		int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
		for (double[] corner : corners) {
			int x = Mth.floor(f.origin.x + f.fwd.x * corner[0] + f.right.x * corner[1]), z = Mth.floor(f.origin.z + f.fwd.z * corner[0] + f.right.z * corner[1]);
			x0 = Math.min(x0, x);
			x1 = Math.max(x1, x);
			z0 = Math.min(z0, z);
			z1 = Math.max(z1, z);
		}
		int wide = x1 - x0 + 1, deep = z1 - z0 + 1;
		double[] heights = new double[wide * deep];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState earth = Blocks.DIRT.defaultBlockState(), turf = Blocks.GRASS_BLOCK.defaultBlockState();
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int i = (z - z0) * wide + (x - x0);
				heights[i] = Double.NaN;
				double dx = x + 0.5 - f.origin.x, dz = z + 0.5 - f.origin.z, fw = dx * f.fwd.x + dz * f.fwd.z, s = dx * f.right.x + dz * f.right.z;
				if (fw < LAND_BACK || fw > LAND_FRONT || Math.abs(s) > LAND_HALF || !f.level.hasChunkAt(pos.set(x, 0, z))) {
					continue;
				}
				int ground = Mth.floor(f.real(x + 0.5, z + 0.5)), up = (int)Math.round(hill(fw, s));
				// (one column's own number, the same every time the film is laid here)
				int roll = Math.floorMod((int)Math.round(fw * 7.0) * 73_856_093 ^ (int)Math.round(s * 7.0) * 19_349_663, 1_000);
				boolean track = fw > TRACK_FROM && fw < TRACK_TO && Math.abs(s + Math.sin(fw * 0.21) * 0.8) < TRACK_HALF - (roll % 3) * 0.3;
				BlockState under = f.level.getBlockState(pos.set(x, ground - 1, z));
				boolean soil = up > 0 || under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.DIRT);
				for (int y = ground; y < ground + up; y++) {
					put(f, pos.set(x, y, z), y == ground + up - 1 ? turf : earth);
				}
				if (up > 0 && under.is(Blocks.GRASS_BLOCK)) {
					put(f, pos.set(x, ground - 1, z), earth);
				}
				int top = ground + up;
				heights[i] = top;
				if (!soil) {
					continue;
				}
				if (track) {
					put(f, pos.set(x, top - 1, z), (roll % 4 == 0 ? Blocks.COARSE_DIRT : roll % 7 == 0 ? Blocks.GRAVEL : Blocks.DIRT_PATH).defaultBlockState());
				} else if (f.level.getBlockState(pos.set(x, top, z)).isAir() && fw * fw + s * s > 9.0) {
					if (roll % FERN_IN == 0) {
						put(f, pos, Blocks.FERN.defaultBlockState());
					} else if (roll % GRASS_IN == 0) {
						put(f, pos, Blocks.SHORT_GRASS.defaultBlockState());
					}
				}
			}
		}
		f.landY = heights;
		f.landX = x0;
		f.landZ = z0;
		f.landWide = wide;
	}

	private static void put(Film f, BlockPos pos, BlockState state) {
		f.land.putIfAbsent(pos.immutable(), f.level.getBlockState(pos));
		f.level.setBlock(pos, state, LAND_FLAGS);
	}

	/** Puts the world's own ground back. */
	private static void unlay(Film f) {
		List<Map.Entry<BlockPos, BlockState>> was = new ArrayList<>(f.land.entrySet());
		for (int i = was.size() - 1; i >= 0; i--) {
			f.level.setBlock(was.get(i).getKey(), was.get(i).getValue(), LAND_FLAGS);
		}
		f.land.clear();
		f.landY = null;
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
		lay(f);
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
			lay(f);
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
		// the four heroes in among the fighting, and the fifth off to one side
		// (Grimsby is a Sylvan elf, the others hearthlanders: long ears, and slighter than a man as the Sylvan are)
		f.grimsby = cast(f, new FilmTape.Cast(0, "grimsby", FilmActor.WIDE, FilmActor.ELF, false, Role.MAGE.ordinal(), Flesh.MAN.ordinal(), false, id(Items.IRON_SWORD), "",
			new float[] {(float)HEROES_AT[0][0], (float)HEROES_AT[0][1], 0.0F, 0.0F, (float)SYLVAN_SCALE}));
		if (f.grimsby != null) {
			f.grimsby.restYaw = f.yaw;
			f.grimsby.homeF = HEROES_AT[0][0];
			f.grimsby.homeS = HEROES_AT[0][1];
		}
		f.thessaly = mage(f, "thessaly", FilmActor.SLIM, HEROES_AT[1], null, null);
		f.ambrose = mage(f, "ambrose", FilmActor.WIDE, HEROES_AT[2], Items.BLAZE_ROD, null);
		// (Varro's axe in his left hand: his right is the one he marks with)
		f.varro = mage(f, "varro", FilmActor.WIDE, VARRO_AT, Items.NETHERITE_AXE, null);
		if (f.varro != null) {
			// in the fight from the first, about his own ground, until his orc comes for him
			f.varro.speed = 0.3;
			f.varro.leash = HERO_LEASH;
			f.varro.kills = EARLY_KILLS;
			f.varro.nextSwing = 9;
			gait(f, f.varro, Moves.GUARD, Moves.RUN);
			f.fighters.add(f.varro);
		}
		// what each of them wears is the game's own (the first of each that this game has; FilmGear draws it on them)
		Actor[] heroes = {f.thessaly, f.varro, f.ambrose, f.grimsby};
		for (int i = 0; i < heroes.length; i++) {
			for (int j = 0; j < GEAR_SLOTS.length && heroes[i] != null; j++) {
				if (!HERO_GEAR[i][j].isEmpty()) {
					hold(f, heroes[i], GEAR_SLOTS[j], HERO_GEAR[i][j]);
				}
			}
		}
		if (f.grimsby != null) {
			// Grimsby's sword is in the fight from the first, never far from where he stands
			f.grimsby.leash = HERO_LEASH;
			f.grimsby.kills = EARLY_KILLS;
			f.grimsby.nextSwing = 6;
			gait(f, f.grimsby, Moves.GUARD, Moves.RUN);
			f.fighters.add(f.grimsby);
		}
		// the Quiet One, at the back in his rags until his time comes
		// (and until then in the fight like any man of the line, a blade in his hand)
		f.stranger = cast(f, new FilmTape.Cast(0, QUIET_RAGS, FilmActor.WIDE, 0, false, Role.STRANGER.ordinal(), Flesh.MAN.ordinal(), false, "", "",
			new float[] {(float)QUIET_FIGHTS[0], (float)QUIET_FIGHTS[1], 0.0F, facing, 1.0F}));
		if (f.stranger != null) {
			f.stranger.restYaw = f.yaw + facing;
			f.stranger.speed = QUIET_WALK;
			f.stranger.homeF = QUIET_FIGHTS[0];
			f.stranger.homeS = QUIET_FIGHTS[1];
			f.stranger.leash = HERO_LEASH;
			f.stranger.kills = EARLY_KILLS;
			f.stranger.nextSwing = 12;
			hold(f, f.stranger, EquipmentSlot.MAINHAND, QUIET_BLADE);
			gait(f, f.stranger, Moves.GUARD, Moves.RUN);
			f.fighters.add(f.stranger);
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
			for (double[] hero : HEROES_AT) {
				free &= distance(at[0], at[1], hero[0], hero[1]) > HERO_ROOM;
			}
			for (double[] other : spots) {
				free &= distance(at[0], at[1], other[0], other[1]) > PAIR_ROOM;
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
			if (!warrior && i % ORC_SHIELDS == 0) {
				hold(f, foe, EquipmentSlot.OFFHAND, id(Items.SHIELD));
			}
			foe.shieldLasts = SHIELD_LASTS + f.rnd.nextInt(SHIELD_SPREAD + 1);
			soldier.shieldLasts = SHIELD_LASTS + f.rnd.nextInt(SHIELD_SPREAD + 1);
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
			if (distance(gf, gs, LEVEL[0][0], LEVEL[0][1]) < DUO_KEPT || distance(gf, gs, LEVEL[1][0], LEVEL[1][1]) < DUO_KEPT) {
				// (not under a pair of heroes or the camera on them)
				continue;
			}
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
		// his imps, on the wing over the field
		for (int i = 0; i < IMP_RINGS.length; i++) {
			double[] ring = IMP_RINGS[i];
			Actor imp = cast(f, new FilmTape.Cast(2, IMP_SKINS[i], 0, 0, false, Role.IMP.ordinal(), Flesh.ORC.ordinal(), false, "", "",
				new float[] {(float)(ring[0] + ring[2] * Math.cos(ring[3])), (float)(ring[1] + ring[2] * Math.sin(ring[3])), (float)-HIDDEN,
					facingBack, 1.0F}));
			if (imp != null) {
				// (not here yet: it comes through its rift)
				imp.gate = new double[] {imp.f, imp.s, IMP_HIGH + i * IMP_STEP};
				imp.riseAt = GATE_FIRST + i * GATE_EVERY + GATE_OPENS;
				imp.team = 1;
				imp.scripted = true;
				imp.ring = ring[3];
				imp.speed = IMP_SPEED;
				imp.sinkRate = IMP_CLIMB;
				f.imps.add(imp);
			}
		}
		// what the heroes' relay is thrown at: an imp on the wing for three of them, an orc of the host for Grimsby
		for (int i = 0; i < Relay.PREY.length; i++) {
			double[] mark = Relay.PREY[i];
			Actor prey = mark[2] > 0.0 ? cast(f, new FilmTape.Cast(2, i % 2 == 0 ? "imp" : "zombified_imp", 0, 0, false, Role.IMP.ordinal(), Flesh.ORC.ordinal(), false, "", "",
				new float[] {(float)mark[0], (float)mark[1], (float)-HIDDEN, facingBack, 1.0F}))
				: orc(f, "orc_1", 1, Role.GUARD, Flesh.ORC, Items.IRON_AXE, mark[0], mark[1], 0.0, facingBack);
			if (prey != null) {
				prey.team = 1;
				prey.scripted = true;
				prey.restYaw = f.yaw + facingBack;
				if (prey.role == Role.IMP) {
					prey.gate = new double[] {mark[0], mark[1], mark[2]};
					prey.riseAt = GATE_PREY + i * GATE_EVERY + GATE_OPENS;
				}
			}
			f.prey[i] = prey;
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
			// real armour and a helmet on every one of them (Andy: "like the draugr the human soldiers should have real armor and helmets on"),
			// no two of them in the same
			int[] wear = wear(i);
			for (int j = 0; j < GEAR_SLOTS.length; j++) {
				hold(f, a, GEAR_SLOTS[j], SOLDIER_WEAR[j][wear[j]]);
			}
			f.soldiers.add(a);
			f.fighters.add(a);
		}
		return a;
	}

	/** Which of SOLDIER_WEAR's the {@code i}th man has on his head, chest, legs and feet: mixed, and his own. */
	static int[] wear(int i) {
		List<int[]> picks = new ArrayList<>();
		for (int head = 0; head < SOLDIER_WEAR[0].length; head++) {
			for (int chest = 0; chest < SOLDIER_WEAR[1].length; chest++) {
				for (int legs = 0; legs < SOLDIER_WEAR[2].length; legs++) {
					for (int feet = 0; feet < SOLDIER_WEAR[3].length; feet++) {
						if (chest != legs || legs != feet) {
							picks.add(new int[] {head, chest, legs, feet});
						}
					}
				}
			}
		}
		return picks.get((WEAR_FIRST + i * WEAR_STEP) % picks.size());
	}

	private static @Nullable Actor mage(Film f, String skin, int body, double[] at, @Nullable Item main, @Nullable Item off) {
		Actor a = cast(f, new FilmTape.Cast(0, skin, body, 0, false, Role.MAGE.ordinal(), Flesh.MAN.ordinal(), false, id(main), id(off),
			new float[] {(float)at[0], (float)at[1], 0.0F, 0.0F, 1.0F}));
		if (a != null) {
			a.restYaw = f.yaw;
			a.homeF = at[0];
			a.homeS = at[1];
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
		if (c.kind() == 2) {
			Imp imp = (c.skin().startsWith("zombified") ? Imps.ZOMBIFIED_IMP : Imps.IMP).create(f.level, EntitySpawnReason.LOAD);
			if (imp == null) {
				return null;
			}
			e = imp;
		} else if (c.kind() == 1) {
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
			unlay(f);
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

	/** An imp's fireball over its head: how far into its charge it shows (0 none). */
	/** {@code a} is in another skin, with or without the cloak. */
	private static void dress(Film f, Actor a, String skin, boolean cloak) {
		tape(f, FilmTape.DRESS, a.index, cloak ? 1 : 0, NONE, skin);
		if (a.e instanceof FilmActor actor) {
			actor.skin = skin;
			actor.cloak = cloak;
		}
	}

	private static void charge(Film f, Actor a, int charge) {
		tape(f, FilmTape.CHARGE, a.index, charge, NONE, "");
		if (a.e instanceof Imp imp) {
			imp.stageCharge(charge);
		}
	}

	/** Flame {@code id} takes at the stage point {@code at}, turned {@code turn}: nothing to see until it's given a size. */
	private static void flame(Film f, int id, float[] at, float turn) {
		flame(f, id, at, turn, false);
	}

	/** ...or, for {@code rift}, a pane of nether-portal light in its place (an imp's way in), sized the same way. */
	private static void flame(Film f, int id, float[] at, float turn, boolean rift) {
		tape(f, FilmTape.IGNITE, id, rift ? 1 : 0, new float[] {at[0], at[1], at[2], turn}, "");
		Display.BlockDisplay e = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, f.level);
		e.setBlockState((rift ? Blocks.NETHER_PORTAL : Blocks.FIRE).defaultBlockState());
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
					flame(f, e.a(), new float[] {v[0], v[1], v[2]}, v[3], e.b() == 1);
				}
			}
			case FilmTape.FLAME -> {
				if (v.length >= 1) {
					flameSize(f, e.a(), v[0]);
				}
			}
			case FilmTape.QUENCH -> quench(f, e.a());
			case FilmTape.CHARGE -> {
				if (a != null) {
					charge(f, a, e.b());
				}
			}
			case FilmTape.CLOAK -> {
				if (a != null) {
					cloak(f, a, e.b() != 0);
				}
			}
			case FilmTape.DRESS -> {
				if (a != null) {
					dress(f, a, e.s(), e.b() != 0);
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
		if (tf > -1.5 && tf < MAGES_F + 4.0 && Math.abs(ts) < 10.5) {
			return true;
		}
		if (distance(tf, ts, VARRO_AT[0], VARRO_AT[1]) < 4.4 || segment(tf, ts, MARKED_AT[0], MARKED_AT[1], VARRO_AT[0], VARRO_AT[1]) < 1.9
			|| segment(tf, ts, VARRO_AT[0] + 1.3, VARRO_AT[1], VARRO_AT[0] + 1.3, VARRO_AT[1] + 6.0) < 2.4) {
			return true;
		}
		if (distance(tf, ts, SORCERER_F, 0.0) < 3.6 || segment(tf, ts, 51.2, 0.0, 51.2, 8.5) < 2.8 || segment(tf, ts, SORCERER_F - 6.0, 2.6, SORCERER_F, 0.0) < 1.6) {
			return true;
		}
		// the heroes' relay: each one's bolt, and the camera's way after it and back to the next of them
		double[][] homes = {HEROES_AT[1], HEROES_AT[2], HEROES_AT[0], VARRO_AT};
		for (int i = 0; i < Relay.PREY.length; i++) {
			double[] prey = Relay.PREY[i], next = homes[Math.min(i + 1, homes.length - 1)];
			if (segment(tf, ts, homes[i][0], homes[i][1], prey[0], prey[1]) < 2.6 || segment(tf, ts, prey[0], prey[1], next[0], next[1]) < 2.6) {
				return true;
			}
		}
		// where Varro and Grimsby stand back to back, and the camera's look at them
		if (distance(tf, ts, DUO1_AT[0], DUO1_AT[1]) < 5.0 || segment(tf, ts, DUO1_AT[0] + 2.4, DUO1_AT[1], DUO1_AT[0] + 2.4, DUO1_AT[1] + 7.0) < 2.4) {
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
		if (segment(tf, ts, HEROES_AT[0][0], HEROES_AT[0][1], 32.0, HEROES_AT[0][1]) < 2.2) {
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
		fog(f);
		int t = f.tick;
		if (f.play != null) {
			replay(f, f.play, t);
		} else {
			beats(f, t);
			battle(f, t);
			imps(f, t);
			douse(f, t);
			fire(f, t);
			for (Iterator<Finish> it = f.finishes.iterator(); it.hasNext(); ) {
				if (finish(f, it.next(), t)) {
					it.remove();
				}
			}
			apart(f, t);
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
		// (and Thessaly and Ambrose, each in their own colour, from where they stand in it)
		for (int i = 0; i < BOLTS.length; i++) {
			Actor mage = caster(f, i + 1);
			// (until their own shot: Grimsby's sword has an orc on it by then, and the other two throw faster in theirs)
			int until = mage == f.grimsby ? DUO1 - DUO_GATHER : DUO2 - DUO_BOLT;
			if (mage != null && t > 20 && t < until && t % BOLTS[i][0] == BOLTS[i][1] && t >= mage.busyUntil) {
				Actor d = target(f, mage, 16.0);
				if (mage != f.grimsby && f.rnd.nextInt(BOLT_AT_IMP) == 0) {
					// up at one of the imps (it takes more than one to bring one down)
					for (Actor imp : f.imps) {
						if (!imp.fled && imp.up() && distance(imp.f, imp.s, mage.f, mage.s) < IMP_BOLT_REACH) {
							d = imp;
						}
					}
				}
				if (d != null) {
					bolt(f, mage, d);
				}
			}
		}
		// Varro's fire, once his orc is up and his
		if (t >= VARRO_CAST && t < DUO1 && f.varro != null) {
			spell(f, 0, f.varro, t - VARRO_CAST);
		}
		gates(f, t);
		relay(f, t);
		early(f, t);
		pairs(f, t);
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
		// (from where they stand now, which a tape's actor only has as where its body is)
		float[] m = f.staged(mage.e.position());
		return switch (which) {
			case 0 -> f.at(m[0] + 8.0, m[1] - 1.0, 0.5);
			case 1 -> f.at(m[0] + 3.0, 0.0, 1.0);
			case 2 -> f.at(m[0] + 11.0, m[1], 1.0);
			default -> f.at(m[0] + 12.0, 0.0, 0.0);
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
		if (t == ORC_CHARGE - VARRO_READY) {
			// out of the fight he has been in and back to his ground, his axe to his left hand: his right is the one he marks with
			leave(f, varro, VARRO_AT);
			hold(f, varro, EquipmentSlot.OFFHAND, id(varro.e.getMainHandItem().getItem()));
			hold(f, varro, EquipmentSlot.MAINHAND, "");
			gait(f, varro, Moves.IDLE, Moves.RUN);
			varro.restYaw = f.yaw;
		}
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
			// it goes for its axe (Varro has his fire to throw first: pairs() sends him in after it)
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
	}

	/** Grimsby's bolt: off his free hand, a streak of light into {@code at}, which falls. */
	private static void bolt(Film f, Actor mage, Actor at) {
		int whose = mage == f.thessaly ? 1 : mage == f.ambrose ? 2 : 0;
		act(f, mage, "cast_left");
		sound(f, SoundEvents.EVOKER_CAST_SPELL, 1.3F - whose * 0.15F, 0.5F);
		Vec3 from = mage.e.position().add(0.0, 1.4, 0.0).add(f.right.scale(-0.4)), to = at.e.position().add(0.0, at.role == Role.IMP ? 0.5 : 1.1, 0.0);
		int steps = Math.max(8, (int)(from.distanceTo(to) * 2.0));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.lerp(to, i / (double)steps);
			// Grimsby's white, Thessaly's violet, Ambrose's gold
			particle(f, whose == 1 ? (i % 3 == 0 ? ParticleTypes.END_ROD : new DustParticleOptions(0xB98CFF, 0.9F))
				: whose == 2 ? (i % 3 == 0 ? ParticleTypes.SMALL_FLAME : new DustParticleOptions(0xFFD35A, 0.9F))
				: i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ENCHANTED_HIT, p, 0.0, 0.0, 0.0);
		}
		if (at.role == Role.IMP) {
			hurt(f, at);
			return;
		}
		at.fallAt = f.tick + 2;
	}

	private static void spell(Film f, int which, Actor mage, int into) {
		// the working itself: called up, let go about as the camera cuts to where it lands, then held a moment
		if (into == 0) {
			act(f, mage, CASTS[which % CASTS.length]);
			// (whoever was fighting stops to work it)
			mage.busyUntil = f.tick + CAST_TICKS - 4;
			mage.toF = mage.f;
			mage.toS = mage.s;
			mage.facing = null;
			mage.foe = null;
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
						if (a.up() && f.tick >= a.busyUntil && a.f < mage.f + 7.0) {
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
						Vec3 p = f.at(mage.f + 3.0, (f.rnd.nextDouble() * 2.0 - 1.0) * FIELD_HALF, f.rnd.nextDouble() * 3.0);
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
					double along = mage.f + 3.0 + run * (SORCERER_F - 6.0 - mage.f - 3.0) / 14.0;
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

	/** The imps' way in: each one's rift opens, it comes through, and the rift draws shut behind it. */
	private static void gates(Film f, int t) {
		for (Actor a : f.actors) {
			double[] gate = a.gate;
			if (gate == null) {
				continue;
			}
			int g = t - (a.riseAt - GATE_OPENS), id = GATE_IDS + a.index;
			if (g < 0 || g > GATE_STAYS + GATE_SHUTS) {
				continue;
			}
			float tall = GATE_SIZE * 1.25F;
			if (g == 0) {
				flame(f, id, new float[] {(float)gate[0], (float)gate[1], (float)gate[2] - tall * 0.35F}, 0.0F, true);
				sound(f, SoundEvents.PORTAL_TRIGGER, 1.6F, 0.25F);
			}
			float open = g <= GATE_OPENS ? g / (float)GATE_OPENS : g <= GATE_STAYS ? 1.0F : 1.0F - (g - GATE_STAYS) / (float)GATE_SHUTS;
			if (g == GATE_STAYS + GATE_SHUTS) {
				quench(f, id);
				continue;
			}
			flameSize(f, id, Math.max(0.02F, GATE_SIZE * smooth(open)));
			// its rim, bright, and the motes it sheds
			Vec3 middle = f.at(gate[0], gate[1], gate[2] - tall * 0.35 + tall * smooth(open) * 0.5);
			for (int i = 0; i < 12; i++) {
				double turn = t * 0.3 + i * Math.PI / 6.0, wide = GATE_SIZE * 0.55 * smooth(open), high = tall * 0.55 * smooth(open);
				Vec3 p = middle.add(f.right.scale(Math.cos(turn) * wide)).add(0.0, Math.sin(turn) * high, 0.0);
				particle(f, i % 3 == 0 ? ParticleTypes.PORTAL : new DustParticleOptions(RIFT_RIM, 1.0F), p, 0.0, 0.0, 0.0);
			}
			if (g == GATE_OPENS) {
				// through it
				a.f = a.toF = gate[0];
				a.s = a.toS = gate[1];
				a.sink = a.sinkTo = -gate[2];
				a.snap = true;
				sound(f, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 0.5F);
				sound(f, SoundEvents.PHANTOM_FLAP, 1.2F, 0.5F);
				for (int i = 0; i < 14; i++) {
					particle(f, ParticleTypes.PORTAL, middle.add(spread(f, 0.6), spread(f, 0.8), spread(f, 0.3)), 0.0, 0.0, 0.0);
				}
			}
		}
	}

	/** {@code hero} breaks off its fight and goes to {@code to}: whoever was on it looks for someone else. */
	private static void leave(Film f, Actor hero, double[] to) {
		hero.scripted = true;
		hero.foe = null;
		hero.facing = null;
		hero.toF = to[0];
		hero.toS = to[1];
		hero.leash = 0.0;
		rest(f, hero);
		for (Actor o : f.legion) {
			if (o.foe == hero) {
				o.foe = null;
				o.engagedAt = -1;
			}
		}
	}

	/** Before their own beats Varro, Grimsby and the fifth each have one of the sorcerer's on them at a time. */
	private static void early(Film f, int t) {
		if (t % DUO_FEED != 0 || f.lungeAt >= 0) {
			return;
		}
		for (Actor hero : new Actor[] {f.varro, f.grimsby, f.stranger}) {
			if (hero != null && !hero.scripted && hero.arc == 0 && hero.leash > 0.0 && hero.up() && t < DUO1 - DUO_GATHER) {
				feed(f, hero, t, EARLY_FOES);
			}
		}
	}

	/** What {@code who} (as {@link #caster} numbers them) works in: her violet, his gold, Grimsby's white, Varro's fire. */
	private static ParticleOptions glow(int who, int i) {
		return switch (who) {
			case 1 -> i % 3 == 0 ? ParticleTypes.END_ROD : new DustParticleOptions(VIOLET, 1.2F);
			case 3 -> i % 3 == 0 ? ParticleTypes.END_ROD : new DustParticleOptions(GOLD, 1.3F);
			case 2 -> i % 3 == 0 ? ParticleTypes.ENCHANTED_HIT : new DustParticleOptions(PALE, 1.1F);
			default -> i % 5 == 0 ? ParticleTypes.LAVA : i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME;
		};
	}

	/** The heroes' relay (see {@link Relay}): each leg's cast, its bolt, what the bolt bursts, and the next one's light out of the burst. */
	private static void relay(Film f, int t) {
		// what they will be thrown at: the imps hang on the wing, the orc stands bellowing
		for (int i = 0; i < f.prey.length; i++) {
			Actor prey = f.prey[i];
			if (prey != null && prey.role == Role.IMP && !prey.fled && t > prey.riseAt) {
				prey.sink = prey.sinkTo = -(Relay.PREY[i][2] + Relay.BOB * Math.sin(t * 0.21 + i * 1.7));
			}
		}
		int r = t - MELEE;
		if (r < 0 || r >= Relay.TICKS) {
			return;
		}
		int leg = Math.min(Relay.WHO.length - 1, r / Relay.LEG), u = r - leg * Relay.LEG, who = Relay.WHO[leg];
		Actor hero = caster(f, who), prey = f.prey[leg];
		if (hero == null) {
			return;
		}
		Vec3 at = relayPrey(f, leg), hand = hero.e.position().add(0.0, 1.4, 0.0), way = at.subtract(hand);
		if (u == 0) {
			// (whatever they were at, they turn from it for this)
			hero.busyUntil = t + Relay.HOLD + Relay.FLY + 4;
			hero.foe = null;
			hero.toF = hero.f;
			hero.toS = hero.s;
			hero.facing = prey;
			if (prey != null && prey.role != Role.IMP) {
				act(f, prey, "overhead");
			}
		}
		if (u == Relay.CAST) {
			act(f, hero, Relay.MOVES[leg]);
			sound(f, who == 0 ? SoundEvents.BLAZE_SHOOT : SoundEvents.EVOKER_CAST_SPELL, 1.3F - leg * 0.12F, 0.7F);
		}
		if (u >= Relay.CAST && u < Relay.HOLD) {
			// it gathers in their hand
			for (int i = 0; i < 5; i++) {
				particle(f, glow(who, i), hand.add(way.normalize().scale(0.5)).add(spread(f, 0.2), spread(f, 0.2), spread(f, 0.2)), 0.0, 0.01, 0.0);
			}
		}
		int fly = u - Relay.HOLD;
		if (fly >= 0 && fly < Relay.FLY) {
			// the bolt, a length of it each tick
			for (int i = 0; i < 14; i++) {
				Vec3 p = hand.add(way.scale((fly + i / 14.0) / Relay.FLY));
				particle(f, glow(who, i), p.add(spread(f, 0.08), spread(f, 0.08), spread(f, 0.08)), 0.0, 0.0, 0.0);
			}
		}
		int next = Relay.WHO[Math.min(leg + 1, Relay.WHO.length - 1)];
		if (fly == Relay.FLY) {
			// and what it was for bursts
			flash(f, 0.22F);
			sound(f, SoundEvents.GENERIC_EXPLODE.value(), 1.3F, 0.7F);
			sound(f, SoundEvents.FIREWORK_ROCKET_BLAST, 0.9F, 0.8F);
			for (int i = 0; i < 44; i++) {
				particle(f, i % 4 == 3 ? ParticleTypes.LARGE_SMOKE : glow(who, i), at.add(spread(f, 0.4), spread(f, 0.4), spread(f, 0.4)), spread(f, 0.35), spread(f, 0.3), spread(f, 0.35));
			}
			if (prey != null && prey.role == Role.IMP) {
				hide(f, prey);
				prey.fled = true;
				for (int i = 0; i < 12; i++) {
					particle(f, i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.LAVA, at.add(spread(f, 0.5), spread(f, 0.5), spread(f, 0.5)), spread(f, 0.2), 0.1, spread(f, 0.2));
				}
			} else if (prey != null) {
				prey.fallAt = t;
				blood(f, at, way.normalize(), 10, prey.flesh.dark);
			}
			hero.facing = null;
		}
		if (fly == Relay.FLY + 3) {
			rest(f, hero);
		}
		int back = fly - Relay.FLY;
		if (back > 0 && back <= Relay.BACK && leg < Relay.WHO.length - 1) {
			// out of the burst, the next one's light: it turns where the burst was, gathers, and goes to their hand
			Actor to = caster(f, next);
			Vec3 theirs = to != null ? to.e.position().add(0.0, 1.4, 0.0) : at;
			float k = smooth(back / (float)Relay.BACK);
			Vec3 q = at.lerp(theirs, k);
			double wide = 0.9 * (1.0 - k) + 0.15;
			for (int i = 0; i < 10; i++) {
				double turn = back * 0.6 + i * Math.PI / 5.0;
				particle(f, glow(next, i), q.add(Math.cos(turn) * wide, spread(f, wide * 0.6), Math.sin(turn) * wide), 0.0, 0.01, 0.0);
			}
			if (back < 6) {
				for (int i = 0; i < 8; i++) {
					particle(f, glow(next, i), at.add(spread(f, 0.8), spread(f, 0.8), spread(f, 0.8)), 0.0, 0.03, 0.0);
				}
			}
		}
	}

	/**
	 * The heroes in their pairs (see DUO1_AT). Grimsby is sent an orc of the host and fights it blade on blade; Varro, his
	 * fire thrown, runs the width of the field to him and they stand back to back, each cutting down what comes on their
	 * side; Grimsby runs his orc through and lets his spell go down his sword. Then Thessaly and Ambrose, back to back,
	 * bolt after bolt, and the working they make between them: she is a sorcerer and he a mage, and her ward is the vessel
	 * his light is poured into.
	 */
	private static void pairs(Film f, int t) {
		Actor grimsby = f.grimsby, varro = f.varro, thessaly = f.thessaly, ambrose = f.ambrose;
		if (grimsby != null && varro != null) {
			if (t == DUO1 - DUO_GATHER) {
				// Grimsby to his place, looking back the way the dead have come up behind the line
				grimsby.homeF = DUO1_AT[0] - DUO_APART;
				grimsby.homeS = DUO1_AT[1];
				grimsby.leash = DUO_LEASH;
				grimsby.arc = -1;
				grimsby.speed = GRIMSBY_RUN;
				grimsby.restYaw = f.yaw + 180.0F;
				// and an orc of the host for him
				Actor orc = null;
				double nearest = 60.0;
				for (Actor o : f.legion) {
					double d = distance(o.f, o.s, grimsby.homeF, grimsby.homeS);
					if (o.role == Role.HOST && o.flesh == Flesh.ORC && o.up() && !o.scripted && t >= o.busyUntil && t >= o.reservedUntil && d < nearest) {
						nearest = d;
						orc = o;
					}
				}
				if (orc != null) {
					f.clasher = orc;
					orc.scripted = true;
					orc.foe = null;
					orc.speed = MARKED_RUN;
					orc.toF = grimsby.homeF - STAND_OFF;
					orc.toS = grimsby.homeS;
					orc.reservedUntil = DUO1 + CLASH_ENDS;
					gait(f, orc, Moves.GUARD, Moves.RUN);
				}
			}
			Actor orc = f.clasher;
			if (t == DUO1 - CLASH_BEGINS && orc != null && orc.up()) {
				orc.scripted = false;
				orc.foe = grimsby;
				grimsby.foe = orc;
				orc.nextSwing = t + 2;
				grimsby.nextSwing = t + 8;
			}
			if (t == DUO1 + CLASH_ENDS && orc != null && orc.up() && grimsby.up()) {
				f.finishes.add(begin(f, grimsby, orc, IMPALE, t));
			}
			if (t == DUO1) {
				// Varro, his fire thrown, goes in (Andy: "then rush in afterward alongside grimsby"): his axe to his right hand
				rest(f, varro);
				hold(f, varro, EquipmentSlot.MAINHAND, id(varro.e.getOffhandItem().getItem()));
				hold(f, varro, EquipmentSlot.OFFHAND, "");
				gait(f, varro, Moves.GUARD, Moves.SPRINT);
				varro.facing = null;
				varro.foe = null;
				varro.speed = VARRO_RUSH;
				varro.homeF = varro.toF = DUO1_AT[0] + DUO_APART;
				varro.homeS = varro.toS = DUO1_AT[1];
				varro.leash = DUO_LEASH;
				varro.arc = 1;
				varro.busyUntil = 0;
				varro.nextSwing = t + 8;
				varro.scripted = false;
				varro.kills = DUO_KILLS;
				grimsby.kills = DUO_KILLS;
			}
			if (t >= DUO1 && t < DUO2 && t % DUO_FEED == 0) {
				feed(f, varro, t);
				if (t > DUO1 + CLASH_ENDS) {
					feed(f, grimsby, t);
				}
			}
			int cut = t - DUO1 - GRIMSBY_CUT;
			if (cut == 0) {
				// he turns from what is behind him to the field, and down his blade it goes
				grimsby.restYaw = f.yaw;
			}
			if (cut >= 0 && cut < CAST_TICKS) {
				spell(f, 2, grimsby, cut);
			}
			if (t == DUO2) {
				// and on into the fight, each their own way: Varro after the dead with his orc, Grimsby about where he stands
				grimsby.arc = 0;
				grimsby.leash = HERO_LEASH;
				varro.arc = 0;
				varro.leash = 0.0;
				varro.hunter = true;
			}
		}
		if (thessaly == null || ambrose == null) {
			return;
		}
		if (t == DUO1) {
			// to each other's backs, while the other two have the camera
			thessaly.homeF = thessaly.toF = DUO2_AT[0] - DUO_APART;
			ambrose.homeF = ambrose.toF = DUO2_AT[0] + DUO_APART;
			thessaly.homeS = thessaly.toS = ambrose.homeS = ambrose.toS = DUO2_AT[1];
			thessaly.arc = -1;
			ambrose.arc = 1;
		}
		if (t == DUO2 - DUO_GATHER) {
			thessaly.restYaw = f.yaw + 180.0F;
		}
		int combo = t - DUO2 - COMBO;
		if (t >= DUO2 - DUO_GATHER && combo < 0) {
			if (t % DUO_FEED == 0) {
				feed(f, thessaly, t);
				feed(f, ambrose, t);
			}
			// bolt after bolt, turn about, at whatever is nearest on their own side of the two of them
			for (int i = 0; i < 2 && t >= DUO2 - DUO_BOLT; i++) {
				Actor mage = i == 0 ? thessaly : ambrose;
				if (t % DUO_BOLT != i * DUO_BOLT / 2 || t < mage.busyUntil) {
					continue;
				}
				Actor at = null;
				double nearest = BOLT_REACH;
				for (Actor o : f.legion) {
					double d = distance(o.f, o.s, mage.f, mage.s);
					if (o.up() && o.sink <= 0.0 && t >= o.busyUntil && t >= o.reservedUntil && (o.f - DUO2_AT[0]) * mage.arc > -0.5 && d < nearest) {
						nearest = d;
						at = o;
					}
				}
				if (at != null) {
					mage.facing = at;
					bolt(f, mage, at);
				}
			}
		}
		if (combo < 0 || t >= DON) {
			return;
		}
		Vec3 feet = f.at(DUO2_AT[0], DUO2_AT[1], 0.0);
		if (combo == 0) {
			// shoulder to shoulder now, both to the field
			for (Actor mage : new Actor[] {thessaly, ambrose}) {
				mage.facing = null;
				mage.restYaw = f.yaw;
				mage.busyUntil = t + COMBO_REST;
			}
			act(f, thessaly, "cast_ward");
			act(f, ambrose, "cast_high");
			sound(f, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.0F);
			sound(f, SoundEvents.EVOKER_PREPARE_ATTACK, 0.9F, 0.9F);
			sound(f, SoundEvents.BEACON_ACTIVATE, 1.4F, 0.7F);
		}
		if (combo < COMBO_BURST) {
			// her ward, a ring of it turning about the two of them and climbing as it draws in...
			double wide = 2.6 - 1.3 * combo / COMBO_BURST, high = 0.2 + combo * 0.12;
			for (int i = 0; i < 12; i++) {
				double turn = combo * 0.45 + i * Math.PI / 6.0;
				Vec3 p = feet.add(f.fwd.scale(Math.cos(turn) * wide)).add(f.right.scale(Math.sin(turn) * wide)).add(0.0, high * (0.5 + 0.5 * (i % 2)), 0.0);
				particle(f, i % 3 == 0 ? ParticleTypes.END_ROD : new DustParticleOptions(VIOLET, 1.1F), p, 0.0, 0.02, 0.0);
			}
			// ...and his light, come down out of the dark into it
			for (int i = 0; i < 8; i++) {
				Vec3 p = feet.add(spread(f, 0.5), 2.5 + f.rnd.nextDouble() * 9.0, spread(f, 0.5));
				particle(f, i % 2 == 0 ? ParticleTypes.END_ROD : new DustParticleOptions(GOLD, 1.3F), p, 0.0, -0.45, 0.0);
			}
		}
		if (combo == COMBO_BURST) {
			flash(f, 0.6F);
			sound(f, SoundEvents.TRIDENT_THUNDER.value(), 1.2F, 0.5F);
			sound(f, SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.0F);
			sound(f, SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 0.4F);
		}
		int run = combo - COMBO_BURST;
		if (run >= 0 && run < COMBO_RUN) {
			// one working of the two: her ward let go as a ring, his light the edge of it
			double wide = 1.5 + (run + 1) * COMBO_REACH / COMBO_RUN;
			for (int i = 0; i < RING_POINTS; i++) {
				double turn = i * Math.PI * 2.0 / RING_POINTS + run * 0.05, cos = Math.cos(turn), sin = Math.sin(turn);
				Vec3 out = f.fwd.scale(cos).add(f.right.scale(sin)), p = feet.add(out.scale(wide)).add(0.0, 0.3 + (i % 3) * 0.7, 0.0);
				particle(f, i % 3 == 0 ? ParticleTypes.END_ROD : new DustParticleOptions(i % 3 == 1 ? VIOLET : GOLD, 1.8F), p, out.x * 0.12, 0.02, out.z * 0.12);
				if (i % 4 == 0) {
					particle(f, ParticleTypes.ENCHANTED_HIT, p.add(0.0, 0.6, 0.0), out.x * 0.2, 0.05, out.z * 0.2);
				}
			}
			for (Actor o : f.legion) {
				if (o.up() && o.sink <= 0.0 && t >= o.busyUntil && distance(o.f, o.s, DUO2_AT[0], DUO2_AT[1]) < wide) {
					o.fallAt = t;
				}
			}
		}
		if (combo == COMBO_REST) {
			rest(f, thessaly);
			rest(f, ambrose);
		}
	}

	/** Keeps DUO_FOES of the sorcerer's coming at {@code hero}, from its own side of its pair: the nearest not already spoken for. */
	private static void feed(Film f, Actor hero, int t) {
		feed(f, hero, t, DUO_FOES);
	}

	private static void feed(Film f, Actor hero, int t, int many) {
		int on = 0;
		for (Actor o : f.legion) {
			if (o.foe == hero && o.up()) {
				on++;
			}
		}
		for (; on < many; on++) {
			Actor best = null;
			double nearest = DUO_SEEK;
			for (Actor o : f.legion) {
				double d = distance(o.f, o.s, hero.homeF, hero.homeS);
				if (o.up() && o.sink <= 0.0 && !o.scripted && o != f.clasher && t >= o.busyUntil && t >= o.reservedUntil && (o.foe == null || o.foe.arc == 0)
					&& (hero.arc == 0 || (o.f - hero.homeF) * hero.arc > 0.5) && d < nearest) {
					nearest = d;
					best = o;
				}
			}
			if (best == null) {
				return;
			}
			best.foe = hero;
			best.engagedAt = -1;
			best.speed = Math.max(best.speed, DUO_FOE_RUN);
		}
	}

	/** Nobody stands in anybody else: any two on their feet nearer than ROOM are eased apart (whoever is held to a place by its scene stays, the other gives). */
	private static void apart(Film f, int t) {
		List<Actor> all = f.actors;
		for (int i = 0; i < all.size(); i++) {
			Actor a = all.get(i);
			if (!stands(a)) {
				continue;
			}
			for (int j = i + 1; j < all.size(); j++) {
				Actor b = all.get(j);
				if (!stands(b) || Math.abs(a.f - b.f) >= ROOM || Math.abs(a.s - b.s) >= ROOM) {
					continue;
				}
				double df = a.f - b.f, ds = a.s - b.s, d = Math.sqrt(df * df + ds * ds);
				boolean aGives = gives(a, t), bGives = gives(b, t);
				if (d >= ROOM || (!aGives && !bGives)) {
					continue;
				}
				if (d < 1.0E-3) {
					// (one on top of the other: any way out will do)
					df = Math.cos(i * 2.4);
					ds = Math.sin(i * 2.4);
					d = 1.0;
				}
				double give = Math.min(ROOM_GIVE, (ROOM - d) * (aGives && bGives ? 0.5 : 1.0));
				if (aGives) {
					a.f += df / d * give;
					a.s += ds / d * give;
				}
				if (bGives) {
					b.f -= df / d * give;
					b.s -= ds / d * give;
				}
			}
		}
	}

	/** On its feet on the field (not the fifth, the sorcerer or an imp, whose places are their scenes'). */
	private static boolean stands(Actor a) {
		return a.up() && a.sink <= 0.0 && a.role != Role.IMP && (a.role != Role.STRANGER || !a.scripted) && a.role != Role.SORCERER;
	}

	/** Free to be moved aside: not in a scene of its own, a finisher or a pair of heroes. */
	private static boolean gives(Actor a, int t) {
		return !a.scripted && a.arc == 0 && a.role != Role.MAGE && a.role != Role.GUARD && t >= a.busyUntil && t >= a.reservedUntil;
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
		if (t == DON - QUIET_READY) {
			// out of the fight, back to where the white will come for him
			leave(f, quiet, STRANGER_AT);
			gait(f, quiet, Moves.IDLE, Moves.RUN);
			quiet.restYaw = f.yaw;
		}
		if (t == DON) {
			quiet.speed = QUIET_RUN;
		}
		int don = t - DON;
		if (don >= 0 && don < DON_SET + 6) {
			// the white comes up over him, feet first
			Vec3 feet = quiet.e.position();
			if (don < DON_CLIMB) {
				double high = 2.0 * Math.min(1.0, (don + 2) / (double)DON_WHOLE);
				for (int i = 0; i < 10; i++) {
					double turn = don * 0.7 + i * Math.PI / 5.0, up = f.rnd.nextDouble() * high;
					particle(f, i % 5 == 0 ? ParticleTypes.END_ROD : new DustParticleOptions(WHITE, 0.8F),
						feet.add(Math.cos(turn) * 0.45, up, Math.sin(turn) * 0.45), 0.0, 0.03, 0.0);
				}
				particle(f, ParticleTypes.SNOWFLAKE, feet.add(spread(f, 0.6), 0.05, spread(f, 0.6)), 0.0, 0.05, 0.0);
			}
			if (don == 0) {
				act(f, quiet, "cast_high");
				sound(f, SoundEvents.HONEY_BLOCK_SLIDE, 0.7F, 0.8F);
				sound(f, SoundEvents.BEACON_AMBIENT, 1.6F, 0.6F);
			}
			if (don == DON_HALF) {
				dress(f, quiet, QUIET_HALF, false);
				sound(f, SoundEvents.WOOL_PLACE, 0.8F, 0.7F);
			}
			if (don == DON_WHOLE) {
				dress(f, quiet, QUIET_SUIT, true);
				cloak(f, quiet, true);
				hold(f, quiet, EquipmentSlot.MAINHAND, QUIET_BLADE);
				rest(f, quiet);
				sound(f, SoundEvents.WOOL_PLACE, 1.1F, 0.8F);
				sound(f, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 0.7F);
			}
			if (don == DON_SET) {
				cloak(f, quiet, false);
			}
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
			// his cloak goes to work: it winds over him and comes apart, and its cords take him off the ground
			cloak(f, quiet, true);
			gait(f, quiet, Moves.HANG, Moves.HANG);
			sound(f, SoundEvents.WOOL_BREAK, 0.7F, 0.6F);
		}
		if (t < QUIET) {
			return;
		}
		if (f.slingAt < 0) {
			// hung from the cords, swung from one tree's to the next's
			double[] from = QUIET_PATH[f.quietAt - 1], to = QUIET_PATH[f.quietAt];
			double leg = Math.max(0.1, distance(from[0], from[1], to[0], to[1]));
			double along = Mth.clamp(1.0 - distance(quiet.f, quiet.s, to[0], to[1]) / leg, 0.0, 1.0);
			quiet.sink = quiet.sinkTo = -(AIR_LOW + AIR_ARC * Math.sin(along * Math.PI)) * Math.min(1.0, (t - QUIET) / (double)AIR_RISE);
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
				if (i % 2 == 0) {
					particle(f, LoreFilms.SHADOWFLAME, p, spread(f, 0.01), 0.04, spread(f, 0.01));
				}
				if (i % 4 == 0) {
					particle(f, ParticleTypes.SOUL, p, 0.0, 0.05, 0.0);
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
		// (from where the cords had him hung)
		quiet.sink = quiet.sinkTo = -(AIR_LOW * (1.0 - g) + GLIDE_HIGH * smooth((float)(g / 0.15)) + GLIDE_ARC * Math.sin(g * Math.PI));
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
			gait(f, quiet, Moves.IDLE, Moves.WALK);
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
		// (the cord that had the tree before last comes in; the one on the tree he is leaving holds until the next)
		tether(f, quiet, SWING_CORDS[(k + 1) % SWING_CORDS.length], null);
		if (k - 1 < f.dartTrees.size()) {
			double[] trunk = f.dartTrees.get(k - 1);
			tether(f, quiet, SWING_CORDS[k % SWING_CORDS.length], f.at(trunk[0], trunk[1], TETHER_HIGH));
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

	// ---- the imps -----------------------------------------------------------------------------------------------------------

	/** The sorcerer's imps, each tick: round their rings, a dive now and then, and in turn a fireball into a tree. */
	private static void imps(Film f, int t) {
		for (int i = 0; i < f.imps.size(); i++) {
			Actor a = f.imps.get(i);
			if (t <= a.riseAt) {
				// (not through its rift yet)
				continue;
			}
			if (f.lungeAt >= 0) {
				if (!a.fled) {
					// no one to fly for
					a.fled = true;
					a.chargeAt = -1;
					charge(f, a, 0);
					rest(f, a);
					a.restYaw = Float.NaN;
					a.speed = IMP_FLEE;
					a.toF = a.f + 40.0;
					a.toS = a.s * 1.5;
					a.sinkTo = -IMP_AWAY;
				}
				continue;
			}
			int round = t - IMP_FIRST - i * IMP_STAGGER, into = round < 0 ? -1 : round % IMP_EVERY;
			if (into == 0) {
				a.aim = null;
				double nearest = IMP_FAR;
				for (double[] tree : f.trees) {
					double d = distance(tree[0], tree[1], a.f, a.s);
					if (d > IMP_NEAR && d < nearest && !f.fireOf.containsKey(tree)) {
						nearest = d;
						a.aim = tree;
					}
				}
				if (a.aim != null) {
					a.chargeAt = t;
					a.restYaw = f.yawOf(a.aim[0] - a.f, a.aim[1] - a.s);
					act(f, a, "charge");
					sound(f, SoundEvents.FIRECHARGE_USE, 0.6F, 0.5F);
				}
			}
			if (a.chargeAt >= 0 && a.aim != null) {
				// hung in the air, the ball swelling over its head
				int charged = t - a.chargeAt;
				a.toF = a.f;
				a.toS = a.s;
				if (charged <= IMP_CHARGE) {
					charge(f, a, Math.max(1, charged));
				}
				if (charged == IMP_CHARGE) {
					act(f, a, "throw");
				}
				if (charged >= IMP_CHARGE + IMP_RELEASE) {
					charge(f, a, 0);
					Vec3 from = a.e.position().add(0.0, BALL_HEIGHT, 0.0), to = f.at(a.aim[0], a.aim[1], 0.0).add(0.0, a.aim[2] + 0.6, 0.0);
					int ticks = Math.max(2, (int)Math.ceil(from.distanceTo(to) / BALL_SPEED));
					f.balls.add(new Mana(from, to.subtract(from).scale(1.0 / ticks), ticks, true));
					sound(f, SoundEvents.BLAZE_SHOOT, 0.8F, 0.6F);
					a.chargeAt = -1;
					a.restYaw = Float.NaN;
				}
				continue;
			}
			double[] ring = IMP_RINGS[i % IMP_RINGS.length];
			a.ring += IMP_TURN;
			a.toF = ring[0] + ring[2] * Math.cos(a.ring);
			a.toS = ring[1] + ring[2] * Math.sin(a.ring);
			boolean dive = into >= IMP_SWOOP && into < IMP_SWOOP + IMP_SWOOP_TICKS;
			a.sinkTo = -(dive ? IMP_LOW : IMP_HIGH + i * IMP_STEP);
			if (into == IMP_SWOOP) {
				act(f, a, "swoop");
				sound(f, SoundEvents.PHANTOM_SWOOP, 1.3F, 0.4F);
			}
			if (t % 11 == i) {
				sound(f, SoundEvents.PHANTOM_FLAP, 1.6F, 0.12F);
			}
		}
		for (Iterator<Mana> it = f.balls.iterator(); it.hasNext(); ) {
			Mana ball = it.next();
			for (int i = 0; i < 5; i++) {
				particle(f, ParticleTypes.FLAME, ball.at.add(spread(f, 0.3), spread(f, 0.3), spread(f, 0.3)), spread(f, 0.02), spread(f, 0.02), spread(f, 0.02));
			}
			particle(f, f.rnd.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.SMOKE, ball.at, 0.0, 0.0, 0.0);
			ball.at = ball.at.add(ball.step);
			if (--ball.left <= 0) {
				it.remove();
				float[] hit = f.staged(ball.at);
				igniteNear(f, hit[0], hit[1], BALL_LIGHTS);
				sound(f, SoundEvents.FIRECHARGE_USE, 0.5F, 0.9F);
				flash(f, 0.12F);
				for (int i = 0; i < 22; i++) {
					particle(f, i % 4 == 0 ? ParticleTypes.LAVA : i % 4 == 1 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, ball.at.add(spread(f, 0.8), spread(f, 0.8), spread(f, 0.8)),
						spread(f, 0.18), f.rnd.nextDouble() * 0.15, spread(f, 0.18));
				}
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
		for (Iterator<Meet> it = f.meets.iterator(); it.hasNext(); ) {
			Meet meet = it.next();
			if (t >= meet.tick) {
				it.remove();
				if (meet.striker.up() && meet.other.up()) {
					meet(f, meet);
				}
			}
		}
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
				// (a hero with nobody near goes back to their ground)
				a.toF = a.leash > 0.0 ? a.homeF : a.f;
				a.toS = a.leash > 0.0 ? a.homeS : a.s;
				continue;
			}
			double df = a.f - a.foe.f, ds = a.s - a.foe.s, d = Math.max(0.01, Math.sqrt(df * df + ds * ds));
			if (a.arc != 0) {
				// back to back with its partner: it keeps its place and cuts at what comes within reach of it
				a.toF = a.homeF;
				a.toS = a.homeS;
				if (d > DUO_REACH) {
					a.engagedAt = -1;
					continue;
				}
			} else if (d > REACH) {
				a.toF = a.foe.f + df / d * STAND_OFF;
				a.toS = a.foe.s + ds / d * STAND_OFF;
				a.engagedAt = -1;
				continue;
			} else {
				a.toF = a.f;
				a.toS = a.s;
			}
			if (a.engagedAt < 0) {
				a.engagedAt = t;
			}
			if (a.foe.foe == null && !a.foe.scripted) {
				a.foe.foe = a;
			}
			if (t >= a.nextSwing) {
				swing(f, a);
				a.nextSwing = t + SWING_GAP + f.rnd.nextInt(SWING_SPREAD);
				if (a.kills > 0.0F) {
					// a hero: faster than the line, and what it cuts at goes down as often as its part in the film calls for
					a.nextSwing = t + DUO_SWING + f.rnd.nextInt(DUO_SWING_SPREAD);
					Actor cut = a.foe;
					if (cut != f.clasher && t >= cut.reservedUntil && t >= cut.busyUntil && f.rnd.nextFloat() < a.kills) {
						cut.fallAt = t + LANDS;
						cut.bleedUntil = t + LANDS + 8;
						cut.bleedUp = 1.2 * cut.scale;
					}
				}
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
		boolean hunts = a.role == Role.MARKED || a.hunter;
		double nearest = a.team == 0 && !hunts ? DEFENDER_SEEK : HUNT;
		for (Actor o : f.fighters) {
			if (o.team == a.team || !o.up() || o.sink > 0.0 || o.scripted || (hunts && o.role != Role.DEAD)
				|| (a.leash > 0.0 && distance(a.homeF, a.homeS, o.f, o.s) > a.leash) || (o.f - a.homeF) * a.arc < -0.4) {
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

	/** A blow: one of the library's cuts; whoever it's at may catch it on a shield, turn it with an arm or a blade, or meet it with a cut of their own. */
	private static void swing(Film f, Actor a) {
		act(f, a, SWINGS[f.rnd.nextInt(SWINGS.length)]);
		Actor foe = a.foe;
		if (foe == null || foe.foe != a || !foe.up() || f.tick < foe.busyUntil || f.tick < foe.stunUntil) {
			return;
		}
		boolean shield = foe.e.getOffhandItem().is(Items.SHIELD);
		float roll = f.rnd.nextFloat(), blocked = shield ? SHIELDED : BLOCKED;
		if (f.clasher != null && (a == f.clasher || foe == f.clasher) && f.tick < f.clasher.reservedUntil) {
			// Grimsby and his orc: every blow met, blade on blade
			shield = false;
			roll = blocked = 0.0F;
		}
		if (roll < blocked) {
			act(f, foe, "block");
			foe.nextSwing = Math.max(foe.nextSwing, f.tick + BLOCK_TICKS);
			f.meets.add(new Meet(f.tick + LANDS, a, foe, shield ? SHIELD : PARRY));
		} else if (roll < blocked + CLASHED && !a.e.getMainHandItem().isEmpty() && !foe.e.getMainHandItem().isEmpty()) {
			act(f, foe, SWINGS[f.rnd.nextInt(SWINGS.length)]);
			foe.nextSwing = f.tick + SWING_GAP + f.rnd.nextInt(SWING_SPREAD);
			f.meets.add(new Meet(f.tick + LANDS, a, foe, CLASH));
		}
	}

	/** The blow lands on what was put up to it: sparks off steel, chips off a shield, and a shield that has had enough comes apart. */
	private static void meet(Film f, Meet meet) {
		Actor a = meet.striker, b = meet.other;
		Vec3 from = a.e.position(), to = b.e.position(), line = to.subtract(from);
		line = line.lengthSqr() < 1.0E-6 ? f.fwd : line.normalize();
		Vec3 at = from.lerp(to, meet.kind == SHIELD ? 0.7 : 0.5).add(0.0, MEET_HIGH * b.scale, 0.0);
		if (meet.kind != SHIELD) {
			boolean clash = meet.kind == CLASH;
			sparks(f, at, clash ? CLASH_SPARKS : PARRY_SPARKS);
			sound(f, SoundEvents.ANVIL_LAND, 1.7F + f.rnd.nextFloat() * 0.3F, clash ? 0.2F : 0.13F);
			if (clash) {
				sound(f, SoundEvents.TRIDENT_HIT, 1.2F + f.rnd.nextFloat() * 0.3F, 0.35F);
			}
			return;
		}
		BlockParticleOption wood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState());
		if (++b.shieldHits < b.shieldLasts) {
			sound(f, SoundEvents.SHIELD_BLOCK.value(), 0.85F + f.rnd.nextFloat() * 0.3F, 0.4F);
			for (int i = 0; i < CHIPS; i++) {
				particle(f, wood, at, -line.x * 0.2 + spread(f, 0.1), 0.1, -line.z * 0.2 + spread(f, 0.1));
			}
			return;
		}
		// it breaks: splinters, a few boards of it thrown wide, and the arm behind it knocked back
		hold(f, b, EquipmentSlot.OFFHAND, "");
		sound(f, SoundEvents.SHIELD_BREAK.value(), 0.9F + f.rnd.nextFloat() * 0.2F, 0.7F);
		sound(f, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.3F, 0.25F);
		BlockParticleOption dark = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.defaultBlockState());
		for (int i = 0; i < SPLINTERS; i++) {
			particle(f, i % 4 == 0 ? dark : wood, at.add(spread(f, 0.3), spread(f, 0.35), spread(f, 0.3)), line.x * 0.25 + spread(f, 0.3), 0.15 + f.rnd.nextDouble() * 0.2,
				line.z * 0.25 + spread(f, 0.3));
		}
		for (int i = 0; i < SHIELD_BOARDS; i++) {
			fling(f, false, id(Items.OAK_PLANKS), 0.5F, 0.07F, 0.22F, at, new Vec3(line.x * 0.12 + spread(f, 0.16), 0.2 + f.rnd.nextDouble() * 0.18, line.z * 0.12 + spread(f, 0.16)),
				28.0F + f.rnd.nextFloat() * 20.0F, false);
		}
		sparks(f, at, PARRY_SPARKS / 2);
		act(f, b, "stagger");
		b.stunUntil = f.tick + SHATTER_STUN;
		b.nextSwing = Math.max(b.nextSwing, f.tick + SHATTER_STUN + 4);
	}

	/** Sparks struck off steel at {@code at}: {@code many} of them thrown every way, hot ones among the white. */
	private static void sparks(Film f, Vec3 at, int many) {
		for (int i = 0; i < many; i++) {
			particle(f, ParticleTypes.ELECTRIC_SPARK, at, spread(f, SPARK_SPEED), f.rnd.nextDouble() * SPARK_SPEED, spread(f, SPARK_SPEED));
			if (i % 3 == 0) {
				particle(f, new DustParticleOptions(i % 2 == 0 ? 0xFFB347 : 0xFFE9A8, 0.45F), at.add(spread(f, 0.25), spread(f, 0.25), spread(f, 0.25)), 0.0, 0.0, 0.0);
			}
		}
		particle(f, ParticleTypes.CRIT, at, spread(f, 0.3), 0.2, spread(f, 0.3));
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
		// and not across the camera where it sits close in
		if (a.role != Role.MAGE && a.role != Role.MARKED && a.role != Role.STRANGER) {
			for (double[] clear : CAMERA_CLEAR) {
				double cf = a.f - clear[2], cs = a.s - clear[3], near = Math.sqrt(cf * cf + cs * cs);
				if (t >= clear[0] && t < clear[1] && near < clear[4] && near > 1.0E-4) {
					a.f = clear[2] + cf / near * clear[4];
					a.s = clear[3] + cs / near * clear[4];
				}
			}
		}
		// nor between a hero and the camera while their spell is on them (from a little before the cut to them)
		if (t >= VARRO_CAST - HERO_CLEAR_EARLY && t < DUO1 && a.role != Role.STRANGER && a.role != Role.MARKED) {
			Actor hero = f.varro;
			if (hero != null && hero != a) {
				double side = hero.s > 0.0 ? -1.0 : 1.0;
				double[][] kept = {{hero.f + HERO_FRONT * 0.5, hero.s + 0.85 * side, HERO_FRONT * 0.5 + 0.6}, {hero.f - HERO_BEHIND * 0.5, hero.s + 0.75 * side, HERO_BEHIND * 0.5 + 0.4}};
				for (double[] clear : kept) {
					double cf = a.f - clear[0], cs = a.s - clear[1], near = Math.sqrt(cf * cf + cs * cs);
					if (near < clear[2] && near > 1.0E-4 && distance(a.f, a.s, hero.f, hero.s) > 0.1) {
						a.f = clear[0] + cf / near * clear[2];
						a.s = clear[1] + cs / near * clear[2];
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

	/**
	 * Shadowflame at {@code (fwd, side)}. Andy: "The shadowflame should be soulfire, but black, retaining the blue soul
	 * wisps, and the black particles, but make sure the smoke is added". A black soul flame licking up out of the ground,
	 * black motes with it, smoke off the top of it, and now and then a blue soul let go.
	 */
	private static void shadowFlame(Film f, double fwd, double side, double rise) {
		Vec3 p = f.at(fwd, side, 0.1);
		particle(f, LoreFilms.SHADOWFLAME, p.add(0.0, f.rnd.nextDouble() * 0.5, 0.0), spread(f, 0.01), rise * 0.35 + f.rnd.nextDouble() * 0.03, spread(f, 0.01));
		if (f.rnd.nextInt(2) == 0) {
			particle(f, ParticleTypes.SQUID_INK, p, spread(f, 0.03), rise + f.rnd.nextDouble() * 0.08, spread(f, 0.03));
		}
		if (f.rnd.nextInt(3) == 0) {
			particle(f, f.rnd.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, p.add(0.0, 0.7 + f.rnd.nextDouble() * 0.6, 0.0), spread(f, 0.02),
				0.05 + f.rnd.nextDouble() * 0.04, spread(f, 0.02));
		}
		if (f.rnd.nextInt(7) == 0) {
			particle(f, ParticleTypes.SOUL, p.add(0.0, 0.4, 0.0), spread(f, 0.02), 0.04 + rise * 0.2, spread(f, 0.02));
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
		} else if (t < LINE) {
			// the heroes, one after another: before each, after their bolt, and back with the next one's light to that one's face
			float r = t - MELEE;
			int leg = Math.min(Relay.WHO.length - 1, (int)(r / Relay.LEG));
			float u = r - leg * Relay.LEG;
			Vec3[] front = relayFront(f, leg, partialTicks);
			Vec3 prey = relayPrey(f, leg), toPrey = prey.subtract(front[1]).normalize();
			Vec3 short_ = prey.subtract(toPrey.scale(Relay.SHORT)).add(0.0, 0.6, 0.0);
			if (u < Relay.HOLD) {
				pos = front[0];
				look = front[1];
			} else if (u < Relay.HOLD + Relay.FLY + 2 || leg == Relay.WHO.length - 1) {
				float k = smooth((u - Relay.HOLD) / Relay.FLY);
				pos = front[0].lerp(short_, k);
				look = turned(pos, front[1], prey, smooth(k * 1.7F));
			} else {
				float k = smooth((u - Relay.HOLD - Relay.FLY - 2) / (Relay.BACK - 2.0F));
				Vec3[] next = relayFront(f, leg + 1, partialTicks);
				pos = short_.lerp(next[0], k);
				look = turned(pos, prey, next[1], smooth(k * 1.4F));
			}
		} else if (t < SUMMON) {
			// along the line, close and low, defenders on the left and the host on the right
			float k = smooth((t - LINE) / (SUMMON - LINE));
			pos = f.at(19.5, 13.0 - 7.0 * k, 1.6);
			look = f.at(21.5, -8.0, 1.3);
		} else if (t < RISING) {
			// low before the sorcerer, on the right third, the sky he calls down filling the rest
			float k = smooth((t - SUMMON) / (RISING - SUMMON));
			pos = f.at(SORCERER_F - 5.5 + 0.8 * k, 2.4, 1.3);
			look = f.at(SORCERER_F, 0.0, 1.9);
			third = -0.85F;
		} else if (t < VARRO) {
			// from behind the mages, over the whole field as the dead come up in it; the horizon on the upper third
			float k = smooth((t - RISING) / (VARRO - RISING));
			pos = f.at(2.0 + 1.5 * k, 4.5, 3.4);
			look = f.at(30.0, -1.0, 0.5);
		} else if (t < VARRO_CLOSE) {
			// side on: Varro on the left third, the orc on the right
			pos = f.at(VARRO_AT[0] + 1.2, VARRO_AT[1] + 4.0, 1.3);
			look = f.at(VARRO_AT[0] + 1.0, VARRO_AT[1], 1.0);
		} else if (t < VARRO_CAST) {
			// a cut in on the same line: Varro on the left third, the orc on its knees on the right, his hand on its brow between
			// them, until it is up and his
			pos = f.at(VARRO_AT[0] + 1.5, VARRO_AT[1] + VARRO_NEAR, 1.3);
			look = f.at(VARRO_AT[0] + 1.3, VARRO_AT[1], 1.0);
		} else if (t < DUO1) {
			// Varro's fire
			int which = 0;
			float into = t - VARRO_CAST;
			Actor mage = f.varro;
			Vec3 m = mage != null ? mage.e.getPosition(partialTicks) : f.at(MAGES_F, 0.0, 0.0);
			// (the line is each hero's own: Andy, "the 180 can swap when swapping povs to different heroes". The camera takes
			// the side of them that is toward the middle of the field)
			double side = mage != null && f.staged(m)[1] > 0.0 ? -1.0 : 1.0;
			if (into < 12) {
				// their face as they call it up, on a third, the field they look out at behind the camera's shoulder
				// (far enough back that all of them is in the frame, head to foot: Andy, "the heroes are in full frame during their bits")
				pos = m.add(f.fwd.scale(HERO_FRONT)).add(f.right.scale(1.7 * side)).add(0.0, 1.25, 0.0);
				look = m.add(0.0, 1.0, 0.0);
				third = 0.45F * (float)side;
			} else {
				// and over their shoulder as it lands
				pos = m.subtract(f.fwd.scale(HERO_BEHIND)).add(f.right.scale(1.5 * side)).add(0.0, 2.3, 0.0);
				Vec3 lands = mage != null ? target(f, which, mage) : f.at(26.0, 0.0, 0.0);
				// (looking past them at where it lands, not so far past that they leave the frame)
				look = m.add(0.0, 1.0, 0.0).lerp(lands, 0.6);
			}
		} else if (t < DUO2) {
			// side on and long: Grimsby and his orc, Varro coming in past the camera to stand at his back, the two of them
			// whole in the frame and what comes at them either side; then along Grimsby's cut as he lets it go
			float k = smooth((t - DUO1) / (DUO2 - DUO1)), cut = smooth((t - DUO1 - GRIMSBY_CUT - 10) / 16.0F);
			pos = f.at(DUO1_AT[0] + 2.4 - 0.5 * k, DUO1_AT[1] + 5.4 - 0.8 * k, 1.5);
			look = f.at(DUO1_AT[0] + 3.5 * cut, DUO1_AT[1], 1.0);
		} else if (t < DUO2 + COMBO + COMBO_BURST - 2) {
			// the same for Thessaly and Ambrose, drawing in on them as they work it
			float k = smooth((t - DUO2) / (COMBO + COMBO_BURST - 2.0F));
			pos = f.at(DUO2_AT[0] + 0.4, DUO2_AT[1] + 5.8 - 1.2 * k, 1.5);
			look = f.at(DUO2_AT[0], DUO2_AT[1], 1.0);
		} else if (t < DON) {
			// and from behind and above them as it goes out over the field
			float k = smooth((t - DUO2 - COMBO - COMBO_BURST + 2) / (DON - DUO2 - COMBO - COMBO_BURST + 2.0F));
			pos = f.at(DUO2_AT[0] - 6.5 - k, DUO2_AT[1] + 2.4, 4.0 + 0.8 * k);
			look = f.at(DUO2_AT[0] + 7.0, DUO2_AT[1], 0.6);
		} else if (t < QUIET) {
			// the fifth, at the back where he has stood all this while (the line is his own now): on the right third, the
			// burning field beyond him, as the white comes up over him
			Vec3 q = f.stranger != null ? f.stranger.e.getPosition(partialTicks) : f.at(STRANGER_AT[0], STRANGER_AT[1], 0.0);
			float k = smooth((t - DON) / (QUIET - DON));
			pos = q.subtract(f.fwd.scale(3.4 - 0.8 * k)).subtract(f.right.scale(1.5)).add(0.0, 1.5, 0.0);
			look = q.add(0.0, 1.25, 0.0);
			third = -0.7F;
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

	/** Leg {@code leg} of the heroes' relay: where the camera sits in front of its hero, and the face it looks at. */
	private static Vec3[] relayFront(Film f, int leg, float partialTicks) {
		Actor hero = caster(f, Relay.WHO[leg]);
		double[] at = Relay.PREY[leg];
		Vec3 m = hero != null ? hero.e.getPosition(partialTicks) : f.at(MAGES_F, 0.0, 0.0);
		float[] stands = f.staged(m);
		double df = at[0] - stands[0], ds = at[1] - stands[1], d = Math.max(0.01, Math.sqrt(df * df + ds * ds));
		Vec3 out = f.fwd.scale(df / d).add(f.right.scale(ds / d)), across = new Vec3(-out.z, 0.0, out.x);
		return new Vec3[] {m.add(out.scale(Relay.NEAR[leg])).add(across.scale(Relay.ASIDE)).add(0.0, Relay.EYE, 0.0), m.add(0.0, Relay.FACE, 0.0)};
	}

	/** Where leg {@code leg}'s bolt lands: the imp, or the orc's chest. */
	private static Vec3 relayPrey(Film f, int leg) {
		double[] at = Relay.PREY[leg];
		return f.at(at[0], at[1], at[2] > 0.0 ? at[2] + 0.5 : 1.2);
	}

	/** A point ahead of a camera at {@code pos} that is turning from looking at {@code from} to looking at {@code to}, {@code k} of the way round. */
	private static Vec3 turned(Vec3 pos, Vec3 from, Vec3 to, float k) {
		Vec3 a = from.subtract(pos), b = to.subtract(pos);
		a = a.lengthSqr() < 1.0E-6 ? b : a.normalize();
		b = b.lengthSqr() < 1.0E-6 ? a : b.normalize();
		float yaw = Mth.rotLerp(k, (float)Math.toDegrees(Math.atan2(-a.x, a.z)), (float)Math.toDegrees(Math.atan2(-b.x, b.z)));
		float pitch = Mth.lerp(k, (float)Math.toDegrees(-Math.asin(Mth.clamp(a.y, -1.0, 1.0))), (float)Math.toDegrees(-Math.asin(Mth.clamp(b.y, -1.0, 1.0))));
		return pos.add(Vec3.directionFromRotation(pitch, yaw).scale(4.0));
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
