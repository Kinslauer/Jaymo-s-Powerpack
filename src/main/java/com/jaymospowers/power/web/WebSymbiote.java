package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The symbiote: a living black suit a web slinger can bond with by finding one of the rare meteor craters (see
 * {@link WebCraterFeature}) and laying hands on the goo at its heart. Bonded, your webs reach further and pull harder,
 * and six abilities open up on a radial menu (X). It has to be fed, it hates fire and loud noise, and push it too far
 * and it tears itself off you for good: you'll have to find another crater.
 *
 * <p>Nothing here is explained in game: hints are one-line whispers, each heard once.
 */
public final class WebSymbiote {
	// ===== abilities (radial menu order, clockwise from the top) ====================================================
	public static final int TENDRIL = 0;
	public static final int ARMOR = 1;
	public static final int CRAWL = 2;
	public static final int CONSUME = 3;
	public static final int SPIKES = 4;
	public static final int SHROUD = 5;
	public static final int ABILITIES = 6;
	/** Not an ability: the little mask in the middle of the menu shows or hides the suit (the powers keep working). */
	public static final int SUIT_TOGGLE = 6;
	/** Cooldown of each ability after use, in ticks (toggles: after switching). */
	public static final int[] COOLDOWN = {100, 200, 20, 160, 240, 600};
	/** Satiety each ability costs. */
	public static final int[] COST = {2, 0, 0, 0, 8, 5};

	/** Tendril Grab: reach, how hard creatures are flung to you, and above this bounding-box volume you go to them. */
	public static final double TENDRIL_RANGE = 20.0;
	public static final double TENDRIL_HEAVY_VOLUME = 8.0;
	/** Living Armor: absorption (half-hearts: 16 = 8 hearts), regrown one point every so often; knockback resistance. */
	public static final double ARMOR_ABSORPTION = 16.0;
	public static final float ARMOR_REGEN = 1.0F;
	public static final int ARMOR_REGEN_TICKS = 40;
	public static final double ARMOR_KNOCKBACK_RESIST = 0.6;
	/** Living Armor makes it hungry this many times faster. */
	public static final int ARMOR_DRAIN_MULT = 3;
	/** Consume: reach, the biggest thing it'll eat (max health), what it heals you and feeds it. */
	public static final double CONSUME_RANGE = 3.0;
	public static final float CONSUME_MAX_HEALTH = 10.0F;
	public static final float CONSUME_HEAL = 6.0F;
	public static final int CONSUME_FEED = 30;
	public static final int CONSUME_CALM = 10;
	/** Spike Burst: radius, damage, how hard it throws things away. */
	public static final double SPIKE_RADIUS = 5.0;
	public static final float SPIKE_DAMAGE = 8.0F;
	public static final double SPIKE_KNOCKBACK = 1.3;
	/**
	 * The roar: when it takes over to save you (near death), and in place of the Spike Burst while the big body is up.
	 * It lasts {@code ROAR_TICKS}, holding you hunched and shuddering, its tendrils flailing; near death the armour
	 * pieces itself together over you as it goes (the suit at {@code ROAR_SUIT_AT}, the tendrils at
	 * {@code ROAR_TENDRILS_AT}, the big body at {@code ROAR_BULK_AT}; whatever you already wore stays). At
	 * {@code ROAR_BLAST_AT} it throws its head back and roars: everything within {@code ROAR_RADIUS} takes
	 * {@code ROAR_DAMAGE} and is hurled away. Near death it shrugs off {@code ROAR_RESISTANCE} levels of harm while it roars.
	 */
	public static final int ROAR_TICKS = 40, ROAR_SUIT_AT = 2, ROAR_TENDRILS_AT = 11, ROAR_BULK_AT = 20, ROAR_BLAST_AT = 28;
	public static final double ROAR_RADIUS = 6.0, ROAR_KNOCKBACK = 1.6;
	public static final float ROAR_DAMAGE = 8.0F, ROAR_SHAKE = 1.4F;
	public static final int ROAR_RESISTANCE = 2;
	/** Shroud: invisible until you attack, or for this long at most. */
	public static final int SHROUD_TICKS = 100;
	/**
	 * Living Armor: put on, it can be put away again after {@code ARMOR_TOGGLE_GAP}; without the suit it withdraws
	 * after {@code ARMOR_IDLE_TICKS} of no use (nothing held, struck, swung or torn), and it never stays out longer
	 * than {@code ARMOR_MAX_TICKS}. Its cooldown ({@code COOLDOWN[ARMOR]}) starts when it withdraws, as Shroud's starts
	 * when Shroud ends.
	 */
	public static final int ARMOR_TOGGLE_GAP = 10, ARMOR_IDLE_TICKS = 100, ARMOR_MAX_TICKS = 1200;
	/**
	 * The suit: it shows itself when you bond ({@code SUIT_DEBUT_TICKS} after the bond takes), then withdraws, and
	 * won't come at your call until it has given you {@code SUIT_ABILITIES} of its abilities. Whenever it takes over,
	 * it engulfs you in the suit and the big body.
	 */
	public static final int SUIT_DEBUT_TICKS = 30, SUIT_ABILITIES = 4;
	/** Lines it has for its panic (last-ditch) and its alarm at noise and fire; never the same panic line twice running. */
	public static final int PANIC_LINES = 8, ALARM_LINES = 4;
	/**
	 * Last ditch: it leaps away from the threat ({@code LEAP_SPEED} out, {@code LEAP_UP} up) and runs; if it's hit
	 * {@code EMERGENCY_HITS} times running, or it's still cornered ({@code STUCK_NEAR}) after {@code STUCK_TICKS},
	 * it hardens: {@code EMERGENCY_ABSORPTION} absorption, once per escape.
	 */
	public static final double LEAP_SPEED = 0.9, LEAP_UP = 0.55, STUCK_NEAR = 6.0;
	public static final int EMERGENCY_HITS = 2, STUCK_TICKS = 100;
	public static final float EMERGENCY_ABSORPTION = 20.0F;

	// ===== the bond meter (0.8.6) ===================================================================================
	/** How far it trusts you, 0-{@code BOND_MAX}. A new bond starts anywhere from nothing to {@code BOND_START_MAX} (halfway). */
	public static final int BOND_MAX = 100, BOND_START_MAX = 50;
	/**
	 * The bond each ability opens at (radial order: Tendril Grab, Living Armor, Wall Crawl, Consume, Spike Burst, Shroud).
	 * Only the Shroud is there from the start. At {@link #BOND_MAX}, Living Armor brings the big body too, yours to drive.
	 */
	public static final int[] UNLOCK_BOND = {15, 60, 30, 45, 75, 0};
	private static final String[] ABILITY_NAMES = {"tendril", "armor", "crawl", "consume", "spikes", "shroud"};
	/** It warms to you a point every {@code BOND_GAIN_TICKS} while fed and calm (stress under {@code BOND_CALM}); starving, it cools a point as often. */
	public static final int BOND_GAIN_TICKS = 2400, BOND_CALM = 50;
	/** A meal it eats warms it this much; each fright (fire, noise) cools it this much; breaking its hold on you, this much. */
	public static final int BOND_MEAL = 2, BOND_EXPOSURE = 3, BOND_BREAK_FREE = 2;
	/** A weak bond barely listens: a radial pick is ignored up to {@code IGNORE_MAX} of the time (bond 0), falling to never by {@code IGNORE_BOND}. */
	public static final float IGNORE_MAX = 0.45F;
	public static final int IGNORE_BOND = 60;
	/** In first person (First-person Model), its mask covers your view until the bond reaches this: you aren't quite in control. */
	public static final int MASK_CLEAR_BOND = 60;
	/**
	 * It takes the wheel only for a few reasons: starving ({@link #GORGE_FULL}), a look ({@link #CURIOUS_CHANCE}), or
	 * neglect: under {@code NEGLECT_BOND}, each takeover check has up to {@code NEGLECT_CHANCE} (at bond 0, none at
	 * {@code NEGLECT_BOND}) that it complains it's been ignored and goes off ripping into whatever's near, its own way.
	 */
	public static final float NEGLECT_CHANCE = 0.05F;
	public static final int NEGLECT_BOND = 25, NEGLECT_LINES = 4;
	/**
	 * Starving, it gorges: it takes over and hunts, kill after kill and meal after meal, until it's back up to
	 * {@code GORGE_FULL}; then it's sated, and its hunger doesn't drain for {@code SATED_TICKS} (5 minutes).
	 */
	public static final int GORGE_FULL = 80, SATED_TICKS = 6000;
	/**
	 * Once it takes over it keeps you until it's done with you: no key answers. With {@code BREAK_FREE_ABILITIES} of its
	 * abilities earned, fighting it (any key, spammed) breaks its hold: {@code BREAK_FREE_BASE} presses,
	 * {@code BREAK_FREE_PER_ABILITY} fewer for each ability earned (44 with all six), the count fading {@code STRUGGLE_FADE}
	 * a tick. Breaking free stresses it ({@code BREAK_FREE_STRESS}) and costs a little bond.
	 */
	public static final int BREAK_FREE_ABILITIES = 3, BREAK_FREE_BASE = 80, BREAK_FREE_PER_ABILITY = 6, BREAK_FREE_STRESS = 10;
	public static final float STRUGGLE_FADE = 0.25F;
	/**
	 * It narrates what it does, more as the bond grows: each act has a {@code NARRATE_MIN} (bond 0) to {@code NARRATE_MAX}
	 * (full) chance to be spoken, at most once every {@code NARRATE_GAP_MAX} to {@code NARRATE_GAP_MIN} ticks. Each act has
	 * {@code SAY_LINES} lines (message.jaymospowers.web_symbiote.say.ACT.N).
	 */
	public static final float NARRATE_MIN = 0.25F, NARRATE_MAX = 0.95F;
	public static final int NARRATE_GAP_MAX = 400, NARRATE_GAP_MIN = 80, SAY_LINES = 3;

	// ===== the bond ================================================================================================
	public static final int SATIETY_MAX = 100;
	public static final int SATIETY_START = 70;
	/** One point of satiety is lost every this many ticks (100 points ≈ 20 minutes, a Minecraft day). */
	public static final int SATIETY_DRAIN_TICKS = 240;
	/** At or below this it's starving: it eats from your hunger bar instead, and gets agitated. */
	public static final int STARVING = 15;
	public static final int STARVE_TICKS = 40;
	public static final float STARVE_EXHAUSTION = 1.2F;
	/** Raw meat feeds it this much per point of the meat's nutrition (and feeds you nothing). */
	public static final int MEAT_FEED_PER_NUTRITION = 5;
	public static final List<Item> RAW_MEAT = List.of(Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT,
		Items.COD, Items.SALMON, Items.ROTTEN_FLESH);
	/** Stress: 0-100. Calms by a point every so often while fed, climbs while starving. */
	public static final int STRESS_MAX = 100;
	public static final int STRESS_CALM_TICKS = 100;
	public static final int STRESS_STARVE_TICKS = 80;

	/** Takeover: how often it considers taking the wheel (see {@link #NEGLECT_CHANCE}, {@link #CURIOUS_CHANCE}). */
	public static final int TAKEOVER_CHECK_TICKS = 140;
	public static final double TAKEOVER_RANGE = 12.0;
	public static final double TAKEOVER_REACH = 3.0;
	public static final int TAKEOVER_ATTACK_TICKS = 20;
	public static final int TAKEOVER_COOLDOWN = 900;
	/**
	 * Once it has the wheel it keeps it until it has done what it set out to (its prey dead, a look had, a kill carried
	 * off and eaten) or you wrestle it back: there's no time limit. Only prey that gets clean away
	 * ({@code TAKEOVER_ESCAPE} blocks) or is gone ends it early. {@code TAKEOVER_LIMIT} is how long each hold is
	 * told to your client; it's renewed while the takeover lasts.
	 */
	public static final double TAKEOVER_ESCAPE = 64.0;
	public static final int TAKEOVER_LIMIT = 1200;
	public static final int TAKEOVER_RELIEF = 20;
	/** The hold is told to the client again this many ticks before it would run out. */
	static final int HOLD_RENEW = 40;
	/**
	 * Near death (at or under {@code DEFEND_HEALTH}), it swells into the big body and takes over to save itself,
	 * going for whatever hurt you; it costs the bond {@code DEFEND_STRESS}, and can't happen again for {@code DEFEND_COOLDOWN}.
	 */
	public static final float DEFEND_HEALTH = 6.0F;
	public static final int DEFEND_STRESS = 15, DEFEND_COOLDOWN = 1200;
	/**
	 * It will stop at nothing to live. At or under {@code DESPERATE_HEALTH} with a threat about, every
	 * {@code DESPERATE_EVERY} it throws everything it has: a Spike Burst whenever it's ready, its tendrils lashing
	 * without pause. At or under {@link #DEFEND_HEALTH} it goes into the Shroud and runs: it takes over and flees
	 * the fight, around whatever's in the way, for open sky, until nothing's within {@code FLEE_SAFE} (or
	 * {@code RUN_TICKS} have gone).
	 */
	public static final float DESPERATE_HEALTH = 10.0F;
	public static final int DESPERATE_EVERY = 20, RUN_TICKS = 300;
	public static final double FLEE_SAFE = 24.0, THREAT_RANGE = 16.0;
	/** Hungry, it goes looking for a corpse this far off (else for something to kill), and a tendril carries it in. */
	public static final double SELF_FEED_RANGE = 14.0;
	/**
	 * Fed on a kill of its own (a takeover's hunt), it often gets you out of there: during the meal or just after it
	 * ({@code ESCAPE_LAG_MIN}-{@code ESCAPE_LAG_MAX} ticks after it starts) it coils a full charge and leaps clear,
	 * {@code ESCAPE_CHANCE} of the time, and every time it's in a bad spot: in a village, or with {@code ESCAPE_CROWD}
	 * or more creatures or people within {@code ESCAPE_RANGE}. It leaps the way they're fewest, where the ground ahead
	 * is open ({@code ESCAPE_DIRECTIONS} tried); boxed in, straight up.
	 */
	public static final float ESCAPE_CHANCE = 0.6F;
	public static final int ESCAPE_CROWD = 3, ESCAPE_LAG_MIN = 10, ESCAPE_LAG_MAX = 70, ESCAPE_DIRECTIONS = 16;
	public static final double ESCAPE_RANGE = 12.0;
	/**
	 * Feeding: it never eats anything living. SEEK a corpse (else the nearest creature), KILL it (for as long as the hunt
	 * takes; then its corpse must turn up within {@code CORPSE_WAIT_TICKS}, within {@code CORPSE_FIND_RADIUS}
	 * of where it fell), GRAB it with a tendril (the reach, then {@code GRAB_HAUL_TICKS} hauling it in, at most
	 * {@code GRAB_TIMEOUT}), CARRY it in your arms (Jaymo's Kitchen; {@code CARRY_TIMEOUT}), and only then FEED.
	 */
	public static final int FEED_NONE = 0, FEED_KILL = 1, FEED_GRAB = 2, FEED_CARRY = 3, FEED_FEED = 4;
	public static final int CORPSE_WAIT_TICKS = 60, GRAB_HAUL_TICKS = 8, GRAB_TIMEOUT = 80, CARRY_TIMEOUT = 20;
	public static final double CORPSE_FIND_RADIUS = 3.0;
	/** Consume: a corpse a tendril can take from this far; something weak enough is finished off by a lash landing {@code CONSUME_FINISH_LAG} later. */
	public static final double CONSUME_CORPSE_RANGE = 6.0;
	public static final int CONSUME_FINISH_LAG = 8;
	public static final float CONSUME_FINISH_DAMAGE = 100.0F;
	/** Its voice takes over your screen: each whisper for {@code WHISPER_FX_TICKS}; a feeding keeps it up, refreshed every {@code FEED_FX_EVERY}. */
	public static final int WHISPER_FX_TICKS = 60, FEED_FX_EVERY = 40, FEED_FX_TICKS = 60;
	/**
	 * The screen's dread is as strong as the bond is weak, but at full strength only for these lines (a weak bond's own:
	 * ignored, neglected, at the edge, leaving you) and for every takeover.
	 */
	public static final List<String> LOW_BOND_LINES = List.of("ignores", "neglect.", "edge", "reject");
	/**
	 * A good bond (stress under {@code CURIOUS_STRESS}, fed) is curious: each takeover check has a
	 * {@code CURIOUS_CHANCE} it takes over just to look, grabbing something and staring at it with its own head for
	 * {@code STARE_MIN}-{@code STARE_MAX} ticks, then setting it down (or, {@code CURIOUS_ATTACK} of the time, not).
	 */
	public static final int CURIOUS_STRESS = 20;
	public static final float CURIOUS_CHANCE = 0.03F, CURIOUS_ATTACK = 0.5F;
	public static final int STARE_MIN = 40, STARE_MAX = 160;
	/**
	 * Remarks (0.9.8): unprompted, it comments on what's around you (the weather, the dark, the deep, height, heat and
	 * cold, villagers, monsters, beasts, other players, water, lava, corpses), every {@code REMARK_GAP_MAX} ticks with a
	 * weak bond down to {@code REMARK_GAP_MIN} with a full one (+ up to {@code REMARK_JITTER}); {@code REMARK_LINES} each.
	 */
	public static final int REMARK_GAP_MAX = 1400, REMARK_GAP_MIN = 500, REMARK_JITTER = 300, REMARK_LINES = 3;
	public static final double REMARK_RANGE = 16.0;
	/**
	 * Sightseeing: a calm, fed bond (as for {@link #CURIOUS_CHANCE}) has {@code TOUR_CHANCE} each takeover check to take
	 * the wheel and walk you {@code TOUR_MIN}-{@code TOUR_MAX} blocks to something it wants to see (a high point, water,
	 * flowers, a villager), giving up after {@code TOUR_TICKS}; there ({@code TOUR_REACH}), it turns your head to take it
	 * in, says so, and lets go. Never toward lava. A hidden marker ({@code TOUR_TAG}) is what it walks to.
	 */
	public static final float TOUR_CHANCE = 0.04F;
	public static final int TOUR_MIN = 10, TOUR_MAX = 28, TOUR_TICKS = 600, TOUR_SAMPLES = 28, TOUR_LINES = 3;
	public static final double TOUR_REACH = 2.5, TOUR_VIEW_RISE = 6.0;
	public static final String TOUR_TAG = "jaymospowers_symbiote_sight";
	/**
	 * Tendril Grab at a block: every tendril reaches for the wall and hauls you in; there, you're turned with your back
	 * to it and they fan out and slam into it, cracking it where each one hits, holding for {@code WALL_HOLD} ticks.
	 */
	public static final int WALL_HOLD = 50;
	/**
	 * Tethered: the tendrils lift you {@code WALL_LIFT} blocks off the wall over {@code WALL_LIFT_TICKS}, so each of the
	 * {@code WALL_TETHERS} lower ones has room to bite into a spot of its own ({@link #TETHER_SPOTS}), one a tick, cracking
	 * the block there; the two shoulder tendrils stay free for R and G. You can lean up to {@code WALL_STRETCH} blocks
	 * away on them (the harder they're stretched, the deeper the cracks) and they pull you back like elastic; dragged
	 * further than {@code WALL_SNAP} from the spot, they tear loose. Any other ability on the radial menu lets go too.
	 */
	public static final double WALL_LIFT = 1.0, WALL_STRETCH = 1.4, WALL_SNAP = 3.5;
	public static final int WALL_LIFT_TICKS = 10, WALL_TETHERS = 6;
	/** The spring: how much of the gap it closes a tick, how much speed carries over (the give), and how fast you lean. */
	public static final double WALL_SPRING = 0.14, WALL_CARRY = 0.8, WALL_LEAN_RATE = 0.12;
	/**
	 * Hung there, your view is held facing away from the wall (on a wall, up to {@code WALL_LOOK} degrees either side
	 * of straight out; on a ceiling or floor, no higher or lower than level). Where you look, you sway: the tethers let
	 * you hang up to {@code WALL_AIM_SHIFT} blocks that way along the wall, and their bites slide {@code TETHER_FOLLOW}
	 * of the way after you.
	 */
	public static final float WALL_LOOK = 80.0F;
	public static final double WALL_AIM_SHIFT = 0.7, TETHER_FOLLOW = 0.3;
	/**
	 * Where each tether bites, for tendrils 2-7 (the inner, middle and low pairs on the back): blocks to the wearer's
	 * right along the wall, and up from your middle (on a ceiling or floor, "up" is the way you face).
	 */
	public static final double[][] TETHER_SPOTS = {{0.55, 1.3}, {-0.55, 1.3}, {1.35, 0.45}, {-1.35, 0.45}, {0.95, -0.9}, {-0.95, -0.9}};
	/** A tether's crack: when it bites, then settled, plus a stage per this many blocks it's stretched past its rest. */
	public static final int TETHER_BITE_STAGE = 6, TETHER_REST_STAGE = 3;
	public static final double TETHER_STAGE_STRETCH = 0.3;

	/**
	 * A wall grab: the anchor, when it began, and when you got there (-1 on the way); then where the tendrils hold you
	 * (your feet, lifted off it), the face, and where each tether bites. Synced: everyone sees the tendrils.
	 */
	public record Wall(Vec3 anchor, long start, long landed, Vec3 rest, int face, List<Vec3> points) {
		public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Wall> STREAM_CODEC =
			net.minecraft.network.codec.StreamCodec.composite(
				Vec3.STREAM_CODEC, Wall::anchor,
				net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, Wall::start,
				net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, Wall::landed,
				Vec3.STREAM_CODEC, Wall::rest,
				net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Wall::face,
				Vec3.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list(WALL_TETHERS)), Wall::points,
				Wall::new);

		/** Where you were when the tendrils got you there, before they lifted you off. */
		public Vec3 contact() {
			return rest.subtract(Direction.from3DDataValue(face).getUnitVec3().scale(WALL_LIFT));
		}

		/** Where the spring pulls you {@code age} ticks after you got there: from the wall, out to the lifted spot. */
		public Vec3 target(float age) {
			float t = Mth.clamp(age / WALL_LIFT_TICKS, 0.0F, 1.0F);
			return contact().lerp(rest, t * t * (3.0F - 2.0F * t));
		}
	}

	/** Held off a wall by the tendrils' tethers (not just on the way to it). */
	public static boolean tethered(Player player) {
		Wall wall = player.getAttached(WALL);
		return wall != null && wall.landed() >= 0L && isBonded(player);
	}

	/** R and G work the tendrils (grab, hold, tear chunks): Living Armor is up, or you hang off a wall on them. */
	public static boolean tendrilKeys(Player player) {
		return armored(player) || tethered(player);
	}

	public static final AttachmentType<Wall> WALL = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_wall"),
		builder -> builder.syncWith(Wall.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Staring at something it grabbed (when it began). Synced to everyone, who see the head swell. */
	public static final AttachmentType<Long> STARING = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_staring"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** Weaknesses: what each exposure does to you and to the bond, and how many it takes to break it. */
	public static final float EXPOSURE_DAMAGE = 3.0F;
	public static final int EXPOSURE_STRESS = 20;
	public static final int REJECT_AT = 6;
	/** How close each noise has to be (blocks). */
	public static final double BELL_RANGE = 16.0;
	public static final double NOTE_RANGE = 8.0;
	public static final double HORN_RANGE = 24.0;
	public static final double EXPLOSION_RANGE = 16.0;
	/** After a noise counts, more noise doesn't count again for this long (one TNT chain is one fright). */
	public static final int NOISE_GRACE_TICKS = 100;
	/** A burning episode ends after this long without fire (so it's one exposure per episode, not per tick). */
	public static final int FIRE_EPISODE_GAP = 60;
	/**
	 * It's terrified of fire: every {@code FLEE_CHECK_TICKS} with fire or lava close (or you burning) it shoves you
	 * clear, toward open ground away from it (or, with nowhere to go, up and out), at most every {@code FLEE_TICKS}.
	 */
	public static final int FLEE_CHECK_TICKS = 5, FLEE_TICKS = 12;
	/** Your screen when it flinches: orange-red from fire, a bright bluish white from noise (colour, ticks). */
	public static final int FIRE_VIGNETTE = 0xFF4A10, NOISE_VIGNETTE = 0xCFE4FF, VIGNETTE_TICKS = 50;
	/** Striking a flint (or throwing a fire charge) near it: a scream, stress, and it does something you didn't ask for. */
	public static final int FLINT_STRESS = 10;
	public static final float FLINT_LASH_DAMAGE = 4.0F;

	/** Bonded, your webs reach this much further, pull this much harder, and Zip cools down this much faster. */
	public static final double REACH_SCALE = 1.5;
	public static final double PULL_SCALE = 1.25;
	public static final double ZIP_COOLDOWN_SCALE = 0.5;

	/** The goo crawling up onto you takes this long. */
	public static final int BOND_TICKS = 50;
	/** It crawls away from a rejected host for this long. */
	public static final int CRAWL_AWAY_TICKS = 50;

	/** The symbiote's black, and its purple sheen (for dust particles). */
	public static final int BLACK = 0x0E0C12;
	public static final int SHEEN = 0x34313A;

	// one-time whispers
	private static final int HINT_NOISE = 1;
	private static final int HINT_FIRE = 2;
	private static final int HINT_HUNGER = 4;
	private static final int HINT_EDGE = 8;
	private static final int HINT_MEAT = 16;
	private static final int HINT_INSTINCT = 32;

	private static final Identifier ARMOR_ABSORB_ID = JaymosPowers.id("web_symbiote_armor");
	private static final Identifier ARMOR_KNOCKBACK_ID = JaymosPowers.id("web_symbiote_knockback");

	/** The bond (saved, kept through death, synced to all: everyone sees the suit). */
	public static final AttachmentType<WebSymbioteState> STATE = AttachmentRegistry.create(JaymosPowers.id("web_symbiote"),
		builder -> builder.persistent(WebSymbioteState.CODEC).copyOnDeath().syncWith(WebSymbioteState.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** When each ability is ready (synced to the owner only, for the menu). */
	public static final AttachmentType<WebSymbioteState.Cooldowns> COOLDOWNS = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_cd"),
		builder -> builder.syncWith(WebSymbioteState.Cooldowns.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	/** Server-only bookkeeping (not saved, not synced). */
	private static final AttachmentType<Live> LIVE = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_live"));

	// ---- Living Armor, bulked up (0.5.0) ----
	/** The armour swells you: this much bigger (scale), too big for a 1x2 gap. */
	public static final double ARMOR_SCALE = 0.25;
	/** The tendrils strike on their own: how often they look for something, how far they reach, the wind-up to the hit,
	 * the whole strike (slow and deliberate), and how hard it hits. */
	public static final int STRIKE_EVERY = 50;
	public static final double STRIKE_RANGE = 5.5;
	public static final int STRIKE_TICKS = 40;
	/** Shares of a strike: it rears until {@code STRIKE_REAR}, comes in until {@code STRIKE_HIT_SHARE} (where it lands), then eases back. */
	public static final float STRIKE_REAR = 0.4F, STRIKE_HIT_SHARE = 0.5F;
	public static final int STRIKE_HIT = Math.round(STRIKE_TICKS * STRIKE_HIT_SHARE);
	public static final float STRIKE_DAMAGE = 6.0F;
	/** Each strike rolls one: a slam (reared high, crashing straight down) or a swipe across from your left or your right. */
	public static final int STRIKE_SLAM = 0, STRIKE_SWIPE_LEFT = 1, STRIKE_SWIPE_RIGHT = 2, STRIKE_VARIANTS = 3;
	/** A slam's push away and lift (an airborne target is driven down instead); a swipe's sideways fling, its share away from you, and lift. */
	public static final double SLAM_PUSH = 0.3, SLAM_LIFT = 0.05, SLAM_DOWN = -0.45;
	public static final double SWIPE_PUSH = 0.7, SWIPE_AWAY = 0.25, SWIPE_LIFT = 0.25;
	/**
	 * A tendril holding debris (a torn-out chunk or a falling block) strikes with it: further, slower, and far harder,
	 * scaled by the chunk's size (× {@code HEAVY_SIZE_BASE} + {@code HEAVY_SIZE_PER_PIECE} a block, clamped), knocking
	 * the target about ten blocks, with a heavy thud and a shake.
	 */
	public static final double HEAVY_STRIKE_RANGE = 8.5;
	public static final int HEAVY_STRIKE_TICKS = 60, HEAVY_STRIKE_EVERY = 90;
	public static final float HEAVY_STRIKE_REAR = 0.5F, HEAVY_STRIKE_HIT_SHARE = 0.58F;
	public static final int HEAVY_STRIKE_HIT = Math.round(HEAVY_STRIKE_TICKS * HEAVY_STRIKE_HIT_SHARE);
	public static final float HEAVY_STRIKE_DAMAGE = 11.0F;
	public static final float HEAVY_SIZE_BASE = 0.6F, HEAVY_SIZE_PER_PIECE = 0.1F, HEAVY_SIZE_MIN = 0.7F, HEAVY_SIZE_MAX = 1.5F;
	public static final double HEAVY_KNOCK = 1.0, HEAVY_LIFT = 0.45, HEAVY_SLAM_KNOCK = 0.75, HEAVY_SLAM_LIFT = 0.2;
	public static final float HEAVY_SHAKE = 1.6F, HEAVY_TARGET_SHAKE = 2.5F;
	/** Screen shake as Living Armor swells over you, and as the big body bursts out (felt {@code BULK_SHAKE_RANGE} off). */
	public static final float ARMOR_ON_SHAKE = 0.6F, BULK_SHAKE = 1.3F;
	public static final double BULK_SHAKE_RANGE = 16.0;
	/** The held debris is swung along with the tendril: how fast it may move (blocks/tick), how high it's reared for a slam, how far out for a swipe. */
	public static final double HEAVY_DEBRIS_SPEED = 2.6, HEAVY_REAR_UP = 2.2, HEAVY_REAR_SIDE = 2.0;
	/**
	 * In a takeover's fight the tendrils do the killing: quick, savage strikes ({@code FRENZY_TICKS} long, landing at
	 * {@code FRENZY_HIT}, reared until {@code FRENZY_REAR}) one after another ({@code FRENZY_EVERY} apart), each
	 * {@code FRENZY_DAMAGE}, far more than a punch; now and then ({@code FRENZY_SNATCH}) one snatches the prey and hurls it.
	 */
	public static final int FRENZY_TICKS = 18, FRENZY_HIT = 9, FRENZY_EVERY = 4;
	public static final float FRENZY_REAR = 0.35F, FRENZY_DAMAGE = 9.0F, FRENZY_SNATCH = 0.18F;
	/** In a rampage, every this many strikes on a player is a bite: this much more damage, and it feeds. */
	public static final int RAMPAGE_BITE_EVERY = 3;
	public static final float RAMPAGE_BITE_DAMAGE = 8.0F;
	/** Consume, on a corpse you're carrying (any size): heal and feed by its size. */
	public static final float CORPSE_HEAL_MIN = 6.0F, CORPSE_HEAL_MAX = 16.0F;
	public static final int CORPSE_FEED_MAX = 80;

	private static final Identifier ARMOR_SCALE_ID = JaymosPowers.id("web_symbiote_bulk");

	/**
	 * A tendril strike in progress: at what, when it began (game time), whether it swings held debris, and how
	 * ({@link #STRIKE_SLAM} or a swipe). Synced to everyone, who see the same swing.
	 */
	public record Strike(int target, long start, boolean heavy, int variant, boolean fast) {
		public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Strike> STREAM_CODEC =
			net.minecraft.network.codec.StreamCodec.composite(
				net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Strike::target,
				net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, Strike::start,
				net.minecraft.network.codec.ByteBufCodecs.BOOL, Strike::heavy,
				net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Strike::variant,
				net.minecraft.network.codec.ByteBufCodecs.BOOL, Strike::fast,
				Strike::new);

		public int ticks() {
			return heavy ? HEAVY_STRIKE_TICKS : fast ? FRENZY_TICKS : STRIKE_TICKS;
		}

		public int hitAt() {
			return heavy ? HEAVY_STRIKE_HIT : fast ? FRENZY_HIT : STRIKE_HIT;
		}

		public float rearShare() {
			return heavy ? HEAVY_STRIKE_REAR : fast ? FRENZY_REAR : STRIKE_REAR;
		}

		public float hitShare() {
			return hitAt() / (float)ticks();
		}
	}

	public static final AttachmentType<Strike> STRIKE = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_strike"),
		builder -> builder.syncWith(Strike.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/**
	 * Instinct: the symbiote keeps itself (and so you) alive. Every {@code INSTINCT_CHECK_TICKS} it looks for danger:
	 * fire close by, something about to blow, a long fall about to end, something hostile on you, or your health running
	 * low. Then it acts on its own: shoving you clear of fire (and smothering flames on you) or of a blast, catching a
	 * fall just before the ground, shoving you away from an attacker or snatching the attacker and flinging it. It never
	 * swells into Living Armor unasked: out of it, its tendrils burst from your back to do it, then withdraw.
	 */
	public static final int INSTINCT_CHECK_TICKS = 10;
	public static final float INSTINCT_LOW_HEALTH = 8.0F;
	public static final double INSTINCT_THREAT_RANGE = 3.0;
	public static final int INSTINCT_COOLDOWN = 30;
	public static final double INSTINCT_FIRE_SHOVE = 0.9;
	/** Something about to blow (a lit creeper, primed TNT) this close: it hurls you clear, this hard. */
	public static final double INSTINCT_BLAST_RANGE = 7.0, INSTINCT_BLAST_SHOVE = 1.25;
	public static final double INSTINCT_THREAT_SHOVE = 0.95;
	public static final float INSTINCT_GRAB_CHANCE = 0.5F;
	public static final double INSTINCT_FALL_CATCH_HEIGHT = 4.0;
	public static final float INSTINCT_CATAPULT_CHANCE = 0.05F;
	public static final double INSTINCT_CATAPULT = 1.4;

	/**
	 * Struck while Living Armor is up, it strikes back: a tendril lashes the attacker within reach (landing in
	 * {@code RETALIATE_LAG} ticks), or snatches one further off. Once every {@code RETALIATE_COOLDOWN}.
	 */
	public static final int RETALIATE_LAG = 6, RETALIATE_COOLDOWN = 25;
	/** Hungry (satiety under {@code SELF_FEED_BELOW}), Living Armor eats a corpse in reach on its own, looking every {@code SELF_FEED_EVERY}. */
	public static final int SELF_FEED_BELOW = 30, SELF_FEED_EVERY = 20;
	/**
	 * Let your own hunger run out and it turns on you: the suit comes off and it won't come back for a while. The
	 * stronger the bond, the shorter: {@code SULK_TICKS}, × (1 + {@code SULK_PER_REJECTION} per time it has rejected you
	 * before), × (0.5 + its stress).
	 */
	public static final int SULK_TICKS = 1200;
	public static final float SULK_PER_REJECTION = 0.5F;
	/** Sulking until this game time: the suit (and Living Armor) won't come. Saved; synced to the owner for the menu. */
	public static final AttachmentType<Long> SULK = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_sulk"),
		builder -> builder.persistent(com.mojang.serialization.Codec.LONG).syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG,
			AttachmentSyncPredicate.targetOnly()));

	/** What it won't eat, alive or dead: bone (skeletons), powder (creepers), the End's own (endermen). */
	static boolean inedible(net.minecraft.world.entity.@org.jspecify.annotations.Nullable EntityType<?> type) {
		// (nor an iron golem: its corpse is too heavy to carry, so it's never hunted)
		return type != null && (type.builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.SKELETONS) || type == net.minecraft.world.entity.EntityTypes.CREEPER
			|| type == net.minecraft.world.entity.EntityTypes.ENDERMAN || KitchenCompat.heavy(type));
	}

	/** Whether it'll eat this (a creature, or a corpse by what it was). */
	static boolean edible(Entity food) {
		// a player's corpse holds their belongings: it's never eaten
		return !KitchenCompat.isPlayerCorpse(food) && !inedible(com.jaymospowers.power.gravity.GravityPower.isCorpse(food) ? KitchenCompat.sourceType(food) : food.getType());
	}

	/**
	 * Out of the armour, its instinct uses only its tendrils: they burst from your back for {@code TENDRILS_OUT_TICKS}
	 * to act, then withdraw. Until this game time (synced to everyone, who see them).
	 */
	public static final int TENDRILS_OUT_TICKS = 30;
	public static final AttachmentType<Long> TENDRILS_OUT = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_tendrils_out"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** A takeover's tendrils tearing a way through: the block they're at (synced, so everyone sees them reach for it). */
	public static final AttachmentType<BlockPos> RIPPING = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_ripping"),
		builder -> builder.syncWith(BlockPos.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** The tendrils are out on their own (not the armour's): until when, else -1. */
	public static long tendrilsOut(Player player) {
		Long until = player.getAttached(TENDRILS_OUT);
		return until == null || player.level().getGameTime() >= until ? -1L : until;
	}

	/** Sulking right now (the suit won't come). */
	public static boolean sulking(Player player) {
		Long until = player.getAttached(SULK);
		return until != null && player.level().getGameTime() < until;
	}

	/**
	 * A meal, in order: a tendril picks it up and brings it to the head as the head swells into a maw
	 * ({@code EAT_REACH}); one huge bite takes something small whole, while anything cow-sized is bitten in half (blood
	 * everywhere) and finished with a second chomp {@code EAT_BITE_GAP} later; then it chews for {@code EAT_CHEW} and
	 * the head shrinks back over {@code EAT_SHRINK}.
	 */
	public static final int EAT_REACH = 14, EAT_BITE_GAP = 12, EAT_CHEW = 40, EAT_SHRINK = 8;
	/** At or above this size (width² × height / 2, clamped 0-1) a meal takes two bites. */
	public static final float BIG_MEAL = 0.5F;

	/** When bite {@code n} (0 or 1) lands, in ticks from the meal's start. */
	public static int biteAt(int n, int reach) {
		return reach + 4 + n * EAT_BITE_GAP;
	}

	/** The meal's whole length, chewing and shrinking back included. */
	public static int mealTicks(boolean big, int reach) {
		return biteAt(big ? 1 : 0, reach) + EAT_CHEW + EAT_SHRINK;
	}

	/** How long the tendril takes to bring a meal in from {@code distance} blocks off (at least {@link #EAT_REACH}). */
	public static int reachTicks(double distance) {
		return Math.max(EAT_REACH, (int)Math.ceil(distance / 0.6) + 6);
	}

	/** What a meal does for your own hunger: food points per point healed, and the saturation modifier. */
	public static final float CONSUME_HUNGER_PER_HEAL = 0.75F;
	public static final float CONSUME_SATURATION = 0.6F;

	/** A meal in progress: when it began, and whether it takes two bites. Synced to everyone, who see the maw work. */
	public record Meal(long start, boolean big, int reach) {
		public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Meal> STREAM_CODEC =
			net.minecraft.network.codec.StreamCodec.composite(
				net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, Meal::start,
				net.minecraft.network.codec.ByteBufCodecs.BOOL, Meal::big,
				net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Meal::reach,
				Meal::new);
	}

	public static final AttachmentType<Meal> EATING = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_eating"),
		builder -> builder.syncWith(Meal.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private static final class Live {
		long noiseReadyAt;
		boolean burning;
		int coolTicks;
		int bondTicks;
		Vec3 bondFrom = Vec3.ZERO;
		int crawlAwayTicks;
		Vec3 crawlAwayPos = Vec3.ZERO;
		Vec3 crawlAwayDir = Vec3.ZERO;
		int takeoverTicks;
		int takeoverTarget = -1;
		long takeoverReadyAt;
		/** How this takeover moves you (the path, stuck checks, ripping through); null when it isn't driving. */
		WebTakeoverPath.@org.jspecify.annotations.Nullable Route route;
		long shroudUntil;
		boolean clinging;
		/** Ticks of Living Armor worn without a break. */
		int armorTicks;
		/** This takeover is a rampage (from wearing the armour too long): longer, and players aren't spared. */
		boolean rampage;
		/** This takeover is only curiosity (a good bond): it looks, and maybe sets them down. Until when it stares. */
		boolean curious;
		long stareUntil;
		/** This takeover is sightseeing: what it wants to see, and where it set out from. */
		boolean touring;
		String tourSight = "";
		Vec3 tourFrom = Vec3.ZERO;
		/** When it next remarks on what's around (0: not yet set). */
		long remarkAt;
		/** When it can swell to save itself again; whether this takeover is it running for its life. */
		long defendReadyAt;
		boolean fleeing;
		long desperateAt;
		/** A wall grab: where your feet are headed, the face, how long the haul may take, and the cracked blocks. */
		Vec3 wallFeet = Vec3.ZERO;
		Direction wallFace = Direction.UP;
		int wallMaxTicks;
		final List<BlockPos> wallCracks = new java.util.ArrayList<>();
		/** Each tether's crack stage as last shown, how far its tendril reached at rest, and when the cracks were last refreshed. */
		final int[] wallStages = new int[WALL_TETHERS];
		final double[] wallRestReach = new double[WALL_TETHERS];
		long wallRefreshAt;
		/** The roar: when it began (0: none), whether it's the one near death (the armour building, a run after), and at whom. */
		long roarAt;
		boolean roarDefend;
		int roarThreat = -1;
		int rampageStrikes;
		long nextStrikeAt;
		/** Instinct: when it can act on its own again, and whether it has caught this fall already. */
		long instinctReadyAt;
		boolean caughtFall;
		/** When it can strike back again; whether it has already turned on you this time your hunger ran out. */
		long retaliateReadyAt;
		boolean sulkedThisTime;
		/** It swelled into the armour only to eat: once the meal's done it goes back (the suit as it was). */
		boolean feedingArmor;
		boolean suitBeforeFeeding;
		/** Once it has gone looking for food it keeps at it until it has eaten (not just killed). */
		boolean craving;
		/** Starving: it hunts and feeds until it's full again ({@link #GORGE_FULL}). */
		boolean gorging;
		/** Fed full after gorging: no hunger drain until this game time. */
		long satedUntil;
		int lastNeglect = -1;
		/** The suit showing itself on bonding, until then (0: shown already). */
		long suitDebutUntil;
		/** It engulfed you for a takeover: the suit as it was before, put back after. */
		boolean engulfed;
		boolean suitBeforeTakeover;
		/** Living Armor: when it came out, and when it was last used (see ARMOR_IDLE_TICKS). */
		long armorOnAt;
		long armorUsedAt;
		/** Last ditch: hits taken and when it started running; whether it has hardened yet; the panic line it used last. */
		int fleeHits;
		long fleeStartAt;
		boolean emergencyUsed;
		int lastPanic = -1;
		/** When it can shove you from fire again; the last stress band it spoke up about (0-4). */
		long fleeReadyAt;
		int stressBand;
		/** The meal: what (-1 once swallowed), whether it's two bites, where it was picked up, and what it gives. */
		int eatTarget = -1;
		boolean eatBig;
		Vec3 eatFrom = Vec3.ZERO;
		float eatHeal;
		int eatFeed;
		/** The feeding (see {@link #FEED_KILL}): its stage, its prey or corpse, when the stage began, when the prey was seen dead. */
		int feedStage;
		int feedTarget = -1;
		long feedStageAt;
		long feedDeadAt = -1L;
		Vec3 feedDeathPos = Vec3.ZERO;
		/** Consume's finishing lash lands then (0: none); the tendril that grabs; it swelled for the hunt; when the dread is next refreshed. */
		long feedFinishAt;
		int feedHand;
		boolean hunting;
		long feedFxAt;
		/** The feeding is on a takeover's kill; when it leaps clear after it (0: it won't). */
		boolean takeoverMeal;
		long escapeAt;
		/** A weak bond's takeover on a whim; when it may next speak; the struggle against its hold (presses, fading), the last press, and whether it answered it. */
		boolean feral;
		long narrateAt;
		float struggle;
		long struggleAt;
		boolean resisted;
		int frenzyVariant;
	}

	private WebSymbiote() {
	}

	static void register() {
		// a sightseeing marker left behind by a save or a crash is cleared away
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TOUR_TAG) && level.getGameTime() > SIGHTS.getOrDefault(entity.getUUID(), 0L)) {
				SIGHTS.remove(entity.getUUID());
				entity.discard();
			}
		});
		UseBlockCallback.EVENT.register(WebSymbiote::onUseBlock);
		// a flint struck (or a fire charge) in its hands: it panics
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> flint(player, hand));
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register(WebSymbiote::flint);
		ServerPlayNetworking.registerGlobalReceiver(WebNet.SymbioteUse.TYPE, (payload, ctx) -> use(ctx.player(), payload.ability()));
		// fighting its hold during a takeover
		ServerPlayNetworking.registerGlobalReceiver(WebNet.Struggle.TYPE, (payload, ctx) -> struggle(ctx.player()));
		// it never dies with you: it pours out of your body and stays near it, looking for someone new
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && state(player).bonded()) {
				withdraw(player);
			}
		});
		// and the body that comes back is nobody's: no bond, no suit, no armour (whatever reached it in between)
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				WebSymbioteState old = state(newPlayer);
				if (old.bonded() || old.has(WebSymbioteState.SUIT) || old.has(WebSymbioteState.ARMOR) || old.has(WebSymbioteState.BULK)) {
					newPlayer.setAttached(STATE, new WebSymbioteState(false, 0, 0, old.rejections(), 0, old.hints(), 0));
				}
				removeArmor(newPlayer);
				newPlayer.removeAttached(COOLDOWNS);
				newPlayer.removeAttached(LIVE);
			}
		});
		// attacking anything breaks the Shroud
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity) {
				endShroud(attacker);
			}
			if (entity instanceof ServerPlayer victim && source.getEntity() instanceof LivingEntity attacker && attacker != victim && damageTaken > 0.0F) {
				retaliate(victim, attacker);
			}
			if (entity instanceof ServerPlayer victim && damageTaken > 0.0F && victim.getAttached(LIVE) instanceof Live live && live.fleeing) {
				live.fleeHits++;
			}
		});
		// either black suit (the suit, or Living Armor and the big body) takes no fall damage at all
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player && source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) && (suitShown(player) || armored(player))));
	}

	// ---- shared queries (both sides: the bond is synced) -------------------------------------------------------------

	public static WebSymbioteState state(Player player) {
		WebSymbioteState state = player.getAttached(STATE);
		return state == null ? WebSymbioteState.NONE : state;
	}

	/** Bonded, and still a web slinger (the symbiote only answers one). */
	public static boolean isBonded(Player player) {
		return state(player).bonded() && Powers.has(player, WebPower.INSTANCE);
	}

	/** Others should see the black suit (and black webs). */
	public static boolean suitShown(Player player) {
		WebSymbioteState state = state(player);
		return state.bonded() && state.has(WebSymbioteState.SUIT) && Powers.has(player, WebPower.INSTANCE);
	}

	/**
	 * Living Armor's tendrils are out (they grab, hold, strike and tear chunks; the plain suit only swings), over any
	 * suit or none, or the big body is up.
	 */
	public static boolean armored(Player player) {
		return isToggled(player, WebSymbioteState.ARMOR) || isToggled(player, WebSymbioteState.BULK);
	}

	/** The big body is up: swollen, galloping, absorbing hits, three times as hungry. Only near death or in a takeover. */
	public static boolean bulked(Player player) {
		return isToggled(player, WebSymbioteState.BULK);
	}

	/** The suit comes at your call (it shows itself on bonding, and in every takeover, regardless). */
	public static boolean suitAllowed(Player player) {
		return player.isCreative() || unlockedCount(player) >= SUIT_ABILITIES;
	}

	/** It has given you this ability (radial index) by now: its bond has reached {@link #UNLOCK_BOND} (creative has them all). */
	public static boolean unlocked(Player player, int ability) {
		return ability < 0 || ability >= ABILITIES || player.isCreative() || state(player).bond() >= UNLOCK_BOND[ability];
	}

	/** How many of its six abilities it has given you. */
	public static int unlockedCount(Player player) {
		int count = 0;
		for (int i = 0; i < ABILITIES; i++) {
			count += unlocked(player, i) ? 1 : 0;
		}
		return count;
	}

	/** The bond is whole: Living Armor brings the big body, and it's yours to drive. */
	public static boolean fullBond(Player player) {
		return state(player).bond() >= BOND_MAX;
	}

	/** Presses it takes to break a takeover's hold now, or -1 if you can't yet (too few of its abilities earned). */
	public static int breakFreeNeeded(Player player) {
		int earned = unlockedCount(player);
		return earned < BREAK_FREE_ABILITIES ? -1 : BREAK_FREE_BASE - BREAK_FREE_PER_ABILITY * earned;
	}

	public static boolean isToggled(Player player, int flag) {
		return isBonded(player) && state(player).has(flag);
	}

	public static double reachScale(Player player) {
		return isBonded(player) ? REACH_SCALE : 1.0;
	}

	/**
	 * How far a swing reaches: wearing the suit, the swing is the tendrils' (most of one side's, grabbing close by),
	 * a short-range swing; bonded but bare, the symbiote's webs reach further than a plain web's.
	 */
	public static double swingScale(Player player) {
		return suitShown(player) ? TENDRIL_SWING_SCALE : reachScale(player);
	}

	/** The tendrils' swing reach, as a share of a web's. */
	public static final double TENDRIL_SWING_SCALE = 0.3;

	public static double pullScale(Player player) {
		return isBonded(player) ? PULL_SCALE : 1.0;
	}

	public static int zipCooldown(Player player) {
		return isBonded(player) ? (int)Math.round(WebPower.ZIP_COOLDOWN * ZIP_COOLDOWN_SCALE) : WebPower.ZIP_COOLDOWN;
	}

	/** Wall Crawl is on: the server lets them hang on walls and ceilings without the flying kick. */
	static boolean allowsFloating(ServerPlayer player) {
		return isToggled(player, WebSymbioteState.CRAWL);
	}

	// ---- server ------------------------------------------------------------------------------------------------------

	/** A takeover has the wheel (it walks you, and coils your leaps). */
	static boolean driving(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		return live != null && live.takeoverTicks > 0;
	}

	private static Live live(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live == null) {
			live = new Live();
			player.setAttached(LIVE, live);
		}
		return live;
	}

	private static void set(ServerPlayer player, WebSymbioteState state) {
		if (!state.equals(player.getAttached(STATE))) {
			player.setAttached(STATE, state);
		}
	}

	/** It speaks: the line, and your screen darkens under it with a growl only you hear (see {@link WebNet.Dread}). */
	public static void whisper(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_symbiote." + key)
			.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		dread(player, WHISPER_FX_TICKS, true, LOW_BOND_LINES.stream().anyMatch(key::startsWith));
		Live live = player.getAttached(LIVE);
		if (live != null) {
			// one voice at a time: its running commentary waits a moment after anything it says
			live.narrateAt = Math.max(live.narrateAt, player.level().getGameTime() + WHISPER_FX_TICKS);
		}
	}

	/**
	 * It narrates what it's doing ({@code act}: strike, kill, grab, feed, dig, thrash, climb, sling, ...): more often the
	 * stronger the bond (see {@link #NARRATE_MIN}). Spoken like any whisper: your screen darkens and it growls.
	 */
	public static void narrate(ServerPlayer player, String act) {
		if (!isBonded(player)) {
			return;
		}
		Live live = live(player);
		long now = player.level().getGameTime();
		float bond = Mth.clamp(state(player).bond() / (float)BOND_MAX, 0.0F, 1.0F);
		if (now < live.narrateAt || player.getRandom().nextFloat() >= Mth.lerp(bond, NARRATE_MIN, NARRATE_MAX)) {
			return;
		}
		whisper(player, "say." + act + "." + (1 + player.getRandom().nextInt(SAY_LINES)));
		live.narrateAt = now + Math.round(Mth.lerp(bond, NARRATE_GAP_MAX, NARRATE_GAP_MIN));
	}

	/** The bond moved to {@code value} (clamped): each ability it now opens is announced, as is the whole bond. */
	private static WebSymbioteState bondTo(ServerPlayer player, WebSymbioteState state, int value) {
		int to = Mth.clamp(value, 0, BOND_MAX);
		int from = state.bond();
		if (to == from) {
			return state;
		}
		if (to > from) {
			for (int i = 0; i < ABILITIES; i++) {
				if (from < UNLOCK_BOND[i] && to >= UNLOCK_BOND[i]) {
					whisper(player, "unlock_" + ABILITY_NAMES[i]);
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.8F);
				}
			}
			if (from < BOND_MAX && to >= BOND_MAX) {
				whisper(player, "unlock_bulk");
			}
		}
		return state.withBond(to);
	}

	/** Changes the bond by {@code delta} (a command, or something it did). */
	public static void addBond(ServerPlayer player, int delta) {
		WebSymbioteState state = state(player);
		if (state.bonded()) {
			set(player, bondTo(player, state, state.bond() + delta));
		}
	}

	/** Your screen, for {@code ticks}: darkened, the purple-black vignette pulsing, and (if {@code growl}) the growl. */
	static void dread(ServerPlayer player, int ticks, boolean growl) {
		dread(player, ticks, growl, false);
	}

	/** {@code full}: at its strongest (see {@link #LOW_BOND_LINES}); otherwise as strong as the bond is weak. */
	static void dread(ServerPlayer player, int ticks, boolean growl, boolean full) {
		ServerPlayNetworking.send(player, new WebNet.Dread(ticks, growl, full));
	}

	/** Whispers {@code key} the first time {@code hint} comes up. Returns the state with the hint recorded. */
	private static WebSymbioteState hintOnce(ServerPlayer player, WebSymbioteState state, int hint, String key) {
		if ((state.hints() & hint) != 0) {
			return state;
		}
		whisper(player, key);
		return state.withHint(hint);
	}

	static DustParticleOptions dust(float scale) {
		return new DustParticleOptions(BLACK, scale);
	}

	private static DustParticleOptions sheen(float scale) {
		return new DustParticleOptions(SHEEN, scale);
	}

	/** Every tick, for a web slinger (from WebPower.tick). */
	static void tick(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live != null) {
			tickEffects(player, live);
		}
		WebSymbioteState state = player.getAttached(STATE);
		if (state == null || !state.bonded()) {
			if (player.tickCount % 20 == 0) {
				removeArmor(player);
			}
			return;
		}
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		if (suitShown(player) || bulked(player)) {
			com.jaymospowers.core.Oddities.hold(player, com.jaymospowers.core.Oddities.SYMBIOTE);
		}
		if (live == null) {
			live = live(player);
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		WebSymbioteState next = state;
		boolean armor = state.has(WebSymbioteState.ARMOR) || state.has(WebSymbioteState.BULK);
		boolean bulk = state.has(WebSymbioteState.BULK);

		// hunger (the big body eats three times as fast)
		int drainTicks = bulk ? Math.max(1, SATIETY_DRAIN_TICKS / ARMOR_DRAIN_MULT) : SATIETY_DRAIN_TICKS;
		if (!player.isCreative() && player.tickCount % drainTicks == 0 && next.satiety() > 0 && now >= live.satedUntil) {
			next = next.withSatiety(next.satiety() - 1);
		}
		boolean starving = next.satiety() <= STARVING;
		if (starving && !player.isCreative()) {
			// it gorges until it's full
			live.gorging = true;
		} else if (live.gorging && next.satiety() >= GORGE_FULL) {
			live.gorging = false;
			live.satedUntil = now + SATED_TICKS;
			whisper(player, "sated");
		}
		if (starving) {
			if (player.tickCount % STARVE_TICKS == 0 && !player.isCreative()) {
				// it feeds on you instead
				player.causeFoodExhaustion(next.satiety() <= 0 ? STARVE_EXHAUSTION * 2.0F : STARVE_EXHAUSTION);
			}
			if (player.tickCount % STRESS_STARVE_TICKS == 0) {
				next = next.withStress(Math.min(STRESS_MAX, next.stress() + 1));
			}
			next = hintOnce(player, next, HINT_HUNGER, "hunger");
		} else if (player.tickCount % STRESS_CALM_TICKS == 0 && next.stress() > 0) {
			next = next.withStress(next.stress() - 1);
		}
		// the bond: it warms to you while fed and calm, and cools while it starves
		if (!player.isCreative() && player.tickCount % BOND_GAIN_TICKS == 0 && live.bondTicks <= 0) {
			if (starving) {
				next = bondTo(player, next, next.bond() - 1);
			} else if (next.stress() < BOND_CALM && !sulking(player)) {
				next = bondTo(player, next, next.bond() + 1);
			}
		}

		// your own hunger run dry: it turns on you, and the suit won't come for a while
		if (!player.isCreative() && player.getFoodData().getFoodLevel() <= 0) {
			if (!live.sulkedThisTime) {
				live.sulkedThisTime = true;
				next = sulk(player, next, now);
				armor = false;
				bulk = false;
			}
		} else {
			live.sulkedThisTime = false;
		}
		Long sulkUntil = player.getAttached(SULK);
		if (sulkUntil != null) {
			if (now >= sulkUntil) {
				player.removeAttached(SULK);
				whisper(player, "sulk_over");
			} else if (next.has(WebSymbioteState.SUIT) || next.has(WebSymbioteState.ARMOR) || next.has(WebSymbioteState.BULK)) {
				next = next.withFlag(WebSymbioteState.SUIT, false).withFlag(WebSymbioteState.ARMOR, false).withFlag(WebSymbioteState.BULK, false);
				removeArmor(player);
				armor = false;
				bulk = false;
			}
		}

		// Living Armor withdraws on its own: unused for a while without the suit, or out too long
		if (next.has(WebSymbioteState.ARMOR) && !live.feedingArmor && live.takeoverTicks <= 0) {
			if (live.armorOnAt <= 0L) {
				live.armorOnAt = now;
				live.armorUsedAt = now;
			}
			if (armorInUse(player)) {
				live.armorUsedAt = now;
			}
			// (the bond whole, Living Armor is the big body you drive: it stays until you put it away)
			boolean idle = !next.has(WebSymbioteState.SUIT) && next.bond() < BOND_MAX && now - live.armorUsedAt > ARMOR_IDLE_TICKS;
			if (idle || now - live.armorOnAt > ARMOR_MAX_TICKS) {
				next = next.withFlag(WebSymbioteState.ARMOR, false);
				armor = next.has(WebSymbioteState.BULK);
				live.armorOnAt = 0L;
				WebTendrils.releaseAll(player, false);
				WebSymbioteState.Cooldowns cooldowns = player.getAttachedOrElse(COOLDOWNS, WebSymbioteState.Cooldowns.NONE);
				player.setAttached(COOLDOWNS, cooldowns.with(ARMOR, now + COOLDOWN[ARMOR], ABILITIES));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
				level.sendParticles(dust(1.0F), player.getX(), player.getY() + 1.1, player.getZ(), 10, 0.3, 0.5, 0.3, 0.0);
			}
		} else if (!next.has(WebSymbioteState.ARMOR)) {
			live.armorOnAt = 0L;
		}

		// the big body: absorption that grows back, and planted feet
		if (bulk) {
			applyArmor(player);
			if (player.tickCount % ARMOR_REGEN_TICKS == 0 && player.getAbsorptionAmount() < ARMOR_ABSORPTION) {
				player.setAbsorptionAmount((float)Math.min(ARMOR_ABSORPTION, player.getAbsorptionAmount() + ARMOR_REGEN));
			}
		} else if (player.tickCount % 20 == 0) {
			removeArmor(player);
		}
		// the tendrils (out on their own, or with the big body): they strike on their own
		if (armor) {
			live.armorTicks++;
			tickStrikes(player, live, now);
			if (player.tickCount % INSTINCT_CHECK_TICKS == 0 && live.takeoverTicks <= 0) {
				catapult(player, live, now);
			}
		} else {
			live.armorTicks = 0;
			// (a counter's tendril strike is out without the armour: WebCounter ends it)
			if (player.getAttached(STRIKE) != null && !WebCounter.ownsStrike(player)) {
				player.removeAttached(STRIKE);
			}
		}

		// hungry, it feeds itself: a corpse, or something it kills first (swelling into the armour for it, if it wasn't);
		// once it has started it isn't satisfied until it has actually eaten
		if ((next.satiety() < SELF_FEED_BELOW || live.craving || live.gorging) && player.getAttached(EATING) == null && live.takeoverTicks <= 0
			&& live.roarAt <= 0L
			&& live.feedStage == FEED_NONE && !sulking(player) && player.tickCount % SELF_FEED_EVERY == 0 && selfFeed(player, live, now, live.gorging)) {
			live.craving = true;
			if (!armor) {
				live.feedingArmor = true;
				live.suitBeforeFeeding = next.has(WebSymbioteState.SUIT);
				next = next.withFlag(WebSymbioteState.ARMOR, true);
				armor = true;
			}
		}
		if (live.feedStage != FEED_NONE) {
			tickFeeding(player, live, now);
		}
		if (live.escapeAt > 0L && now >= live.escapeAt) {
			live.escapeAt = 0L;
			if (live.takeoverTicks > 0 && live.takeoverTarget == player.getId()) {
				// done with the meal's hold: it leaps clear on its own
				endTakeover(player, live);
			}
			escape(player, live);
		}
		if (player.getAttached(EATING) != null) {
			tickEating(player, live, now);
		}
		if (live.feedingArmor && live.feedStage == FEED_NONE && player.getAttached(EATING) == null) {
			// fed: it settles back, the tendrils withdrawing
			live.feedingArmor = false;
			next = next.withFlag(WebSymbioteState.ARMOR, false).withFlag(WebSymbioteState.SUIT, live.suitBeforeFeeding);
			armor = next.has(WebSymbioteState.BULK);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
		}
		if (player.onGround() || player.isInWater()) {
			live.caughtFall = false;
		}
		Wall wall = player.getAttached(WALL);
		if (wall != null) {
			tickWall(player, live, wall, now);
		}
		if (player.tickCount % FLEE_CHECK_TICKS == 0 && now >= live.fleeReadyAt && live.bondTicks <= 0 && !player.isCreative()) {
			fleeFire(player, live, now);
		}
		// it speaks up as it gets more wound up
		int band = Math.min(4, next.stress() / 25);
		if (band > live.stressBand && band > 0) {
			whisper(player, "stress_" + band);
		}
		live.stressBand = band;
		if (live.takeoverTicks <= 0 && live.bondTicks <= 0 && !player.isCreative() && player.tickCount % INSTINCT_CHECK_TICKS == 0) {
			next = instinct(player, live, next, now);
		}

		// Shroud runs out
		if (live.shroudUntil > 0 && now >= live.shroudUntil) {
			endShroud(player);
		}

		// fire: one exposure per burning episode
		boolean burning = player.isOnFire() || player.isInLava();
		if (burning) {
			live.coolTicks = 0;
			if (!live.burning) {
				live.burning = true;
				set(player, next);
				expose(player, Exposure.FIRE);
				return;
			}
		} else if (live.burning && ++live.coolTicks > FIRE_EPISODE_GAP) {
			live.burning = false;
		}

		// wall crawl: hanging on a wall or ceiling is like being on a ladder
		live.clinging = state.has(WebSymbioteState.CRAWL) && !player.onGround() && touchingWall(player);
		if (live.clinging) {
			player.resetFallDistance();
		}

		// takeover
		if (live.takeoverTicks > 0) {
			tickTakeover(player, live, now);
			if (live.takeoverTicks == 0) {
				next = next.withStress(Math.max(0, next.stress() - TAKEOVER_RELIEF));
			}
		} else if (live.feedStage == FEED_NONE && live.roarAt <= 0L && now >= live.takeoverReadyAt && player.tickCount % TAKEOVER_CHECK_TICKS == 0
			&& !player.isCreative()) {
			// besides starving (it gorges, above), it only takes the wheel when neglected, or for a look
			float neglect = next.bond() >= NEGLECT_BOND || sulking(player) || live.gorging ? 0.0F
				: NEGLECT_CHANCE * (1.0F - next.bond() / (float)NEGLECT_BOND);
			if (neglect > 0.0F && player.getRandom().nextFloat() < neglect) {
				// ignored too long: it does it its own way, tearing into whatever's near
				live.rampage = false;
				live.curious = false;
				live.feral = true;
				startTakeover(player, live, now);
				live.feral = live.takeoverTicks > 0;
			} else if (!starving && !live.gorging && next.stress() < CURIOUS_STRESS && next.satiety() >= SELF_FEED_BELOW && !sulking(player)
				&& player.getRandom().nextFloat() < TOUR_CHANCE) {
				// a good bond: somewhere it wants to see
				live.rampage = false;
				live.curious = false;
				startTour(player, live, now);
			} else if (!starving && !live.gorging && next.stress() < CURIOUS_STRESS && next.satiety() >= SELF_FEED_BELOW && !sulking(player)
				&& player.getRandom().nextFloat() < CURIOUS_CHANCE) {
				// a good bond: it only wants a look
				live.rampage = false;
				live.curious = true;
				startTakeover(player, live, now);
			}
		}
		// it talks: what it makes of where you are
		if (live.takeoverTicks <= 0 && live.feedStage == FEED_NONE && live.roarAt <= 0L) {
			remark(player, live, now);
		}
		// near death it will stop at nothing: everything it has, and under three hearts, the Shroud and a run for it
		LivingEntity threat = sulking(player) ? null : threat(player);
		if (threat != null && player.getHealth() <= DEFEND_HEALTH && now >= live.defendReadyAt && (live.takeoverTicks <= 0 || !live.fleeing)
			&& !(live.roarAt > 0L && live.roarDefend)) {
			live.defendReadyAt = now + DEFEND_COOLDOWN;
			abortFeeding(player, live);
			if (live.takeoverTicks > 0) {
				endTakeover(player, live);
			}
			live.rampage = false;
			live.curious = false;
			live.fleeing = true;
			live.fleeHits = 0;
			live.fleeStartAt = now;
			live.emergencyUsed = false;
			// a different scream every time
			int line = player.getRandom().nextInt(PANIC_LINES - 1);
			live.lastPanic = line >= live.lastPanic && live.lastPanic >= 0 ? line + 1 : line;
			whisper(player, "panic." + (1 + live.lastPanic));
			// first it roars, the armour piecing itself together over you, and the roar clears them off;
			// then into the Shroud and away (see endRoar)
			startRoar(player, live, now, true, threat);
			next = next.withStress(Math.min(STRESS_MAX, next.stress() + DEFEND_STRESS));
		} else if (threat != null && player.getHealth() <= DESPERATE_HEALTH && !live.fleeing && now >= live.desperateAt) {
			live.desperateAt = now + DESPERATE_EVERY;
			desperate(player, live, now, threat);
		}
		if (live.roarAt > 0L) {
			tickRoar(player, live, now);
		}
		// the roar near death builds the armour up a piece at a time
		long roarAge = live.roarAt > 0L && live.roarDefend ? now - live.roarAt : -1L;
		// whenever it takes over it engulfs you: the suit and the big body over all of you; given back after
		if ((live.takeoverTicks > 0 || roarAge >= ROAR_SUIT_AT) && !live.engulfed && !sulking(player)) {
			live.engulfed = true;
			live.suitBeforeTakeover = next.has(WebSymbioteState.SUIT);
			next = next.withFlag(WebSymbioteState.SUIT, true);
			level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.8, 0.4, 0.06);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.4F, 0.5F);
		} else if (live.takeoverTicks <= 0 && roarAge < 0L && live.engulfed) {
			live.engulfed = false;
			next = next.withFlag(WebSymbioteState.SUIT, live.suitBeforeTakeover);
		}
		// the suit shows itself on bonding, then withdraws until it trusts you with it (see SUIT_ABILITIES)
		if (next.has(WebSymbioteState.SUIT) && !suitAllowed(player) && !live.engulfed && !live.feedingArmor && live.bondTicks <= 0
			&& now >= live.suitDebutUntil) {
			next = next.withFlag(WebSymbioteState.SUIT, false);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.9F, 0.6F);
			level.sendParticles(dust(1.2F), player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.3, 0.7, 0.3, 0.0);
			if (live.suitDebutUntil > 0L) {
				whisper(player, "suit_withdraw");
			}
		}
		if (now >= live.suitDebutUntil) {
			live.suitDebutUntil = 0L;
		}
		// the big body is up exactly while it has the wheel
		// (and, the bond whole, whenever you wear Living Armor: the big body is yours to drive)
		boolean bulkWanted = live.takeoverTicks > 0 || roarAge >= ROAR_BULK_AT
			|| next.has(WebSymbioteState.ARMOR) && next.bond() >= BOND_MAX && !live.feedingArmor && live.takeoverTicks <= 0;
		if (bulkWanted != next.has(WebSymbioteState.BULK)) {
			next = next.withFlag(WebSymbioteState.BULK, bulkWanted);
			if (bulkWanted) {
				applyArmor(player);
				player.setAbsorptionAmount((float)Math.max(player.getAbsorptionAmount(), ARMOR_ABSORPTION * 0.5));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.2F, 0.5F);
				com.jaymospowers.core.Shakes.heavy(player, player.position(), BULK_SHAKE_RANGE, BULK_SHAKE);
			} else {
				removeArmor(player);
				if (!next.has(WebSymbioteState.ARMOR) || next.bond() < BOND_MAX) {
					whisper(player, "bulk_down");
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
			}
		}

		// a faint drip of black from a bonded suit, now and then (everyone sees it)
		if (state.has(WebSymbioteState.SUIT) && player.tickCount % 30 == 0 && !player.isInvisible()) {
			level.sendParticles(dust(0.8F), player.getX(), player.getY() + 0.9, player.getZ(), 1, 0.25, 0.45, 0.25, 0.0);
		}
		set(player, next);
	}

	/** Wall or ceiling right next to the player (cheap: one box check). */
	static boolean touchingWall(Player player) {
		AABB box = player.getBoundingBox();
		return !player.level().noCollision(player, box.inflate(0.12, -0.1, 0.12))
			|| !player.level().noCollision(player, box.move(0.0, 0.15, 0.0).deflate(0.05, 0.0, 0.05));
	}

	/** Bonding animation, crawling-away animation. */
	private static void tickEffects(ServerPlayer player, Live live) {
		ServerLevel level = player.level();
		if (live.bondTicks > 0) {
			// the goo crawls up from the ground, spiralling to the head
			float t = 1.0F - live.bondTicks / (float)BOND_TICKS;
			double angle = live.bondTicks * 0.7;
			double y = player.getY() + t * player.getBbHeight();
			for (int k = 0; k < 3; k++) {
				double a = angle + k * Math.PI * 2.0 / 3.0;
				double r = 0.45 - t * 0.1;
				level.sendParticles(dust(1.4F), player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r, 2, 0.04, 0.06, 0.04, 0.0);
			}
			if (live.bondTicks % 4 == 0) {
				level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), y, player.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
				level.playSound(null, player.getX(), y, player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.6F, 0.5F + t * 0.4F);
			}
			if (--live.bondTicks == 0) {
				level.sendParticles(sheen(1.6F), player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.35, 0.8, 0.35, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6F, 0.7F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.2F, 0.6F);
			}
		}
		if (live.crawlAwayTicks > 0) {
			// a black blot slithering off across the ground, thinning as it goes
			live.crawlAwayPos = live.crawlAwayPos.add(live.crawlAwayDir.scale(0.18));
			Vec3 at = live.crawlAwayPos;
			float left = live.crawlAwayTicks / (float)CRAWL_AWAY_TICKS;
			level.sendParticles(dust(0.6F + 1.2F * left), at.x, at.y + 0.1, at.z, 3, 0.12, 0.03, 0.12, 0.0);
			if (live.crawlAwayTicks % 8 == 0) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.5F * left + 0.1F, 0.6F);
			}
			if (--live.crawlAwayTicks == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.2, at.z, 6, 0.2, 0.1, 0.2, 0.01);
			}
		}
	}

	// ---- bonding -----------------------------------------------------------------------------------------------------

	private static InteractionResult onUseBlock(Player player, net.minecraft.world.level.Level level, InteractionHand hand, BlockHitResult hit) {
		if (WebPower.GOO == null || !level.getBlockState(hit.getBlockPos()).is(WebPower.GOO) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		Vec3 center = Vec3.atBottomCenterOf(pos).add(0.0, 0.3, 0.0);
		if (!Powers.has(sp, WebPower.INSTANCE) || !sp.isShiftKeyDown() || state(sp).bonded()) {
			// it recoils from anyone else (and from a careless touch)
			serverLevel.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 1.0F, 0.5F);
			serverLevel.playSound(null, pos, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 0.8F, 1.6F);
			serverLevel.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 8, 0.25, 0.15, 0.25, 0.04);
			if (!Powers.has(sp, WebPower.INSTANCE)) {
				whisper(sp, "recoil");
			}
			return InteractionResult.SUCCESS;
		}
		serverLevel.removeBlock(pos, false);
		bond(sp, center);
		return InteractionResult.SUCCESS;
	}

	/** {@code player} takes the symbiote in (from the goo at {@code from}, or from a command). */
	public static void bond(ServerPlayer player, Vec3 from) {
		WebSymbioteState old = state(player);
		// it barely knows you: anywhere from no trust at all to halfway
		set(player, new WebSymbioteState(true, SATIETY_START, 0, 0, WebSymbioteState.SUIT, old.hints(), player.getRandom().nextInt(BOND_START_MAX + 1)));
		player.setAttached(COOLDOWNS, WebSymbioteState.Cooldowns.NONE);
		Live live = live(player);
		live.bondTicks = BOND_TICKS;
		live.bondFrom = from;
		live.suitDebutUntil = player.level().getGameTime() + BOND_TICKS + SUIT_DEBUT_TICKS;
		live.engulfed = false;
		live.craving = false;
		live.burning = false;
		ServerLevel level = player.level();
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, false, false));
		level.playSound(null, from.x, from.y, from.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.5F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.sendParticles(ParticleTypes.SQUID_INK, from.x, from.y, from.z, 30, 0.4, 0.2, 0.4, 0.08);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 10, 0.4, 0.2, 0.4, 0.02);
		whisper(player, "bond");
	}

	/** The symbiote tears itself off and leaves for good (this one, at least). */
	public static void reject(ServerPlayer player) {
		WebSymbioteState old = state(player);
		if (!old.bonded()) {
			return;
		}
		endShroud(player);
		Live live = live(player);
		abortFeeding(player, live);
		if (live.takeoverTicks > 0) {
			live.takeoverTicks = 0;
			dropSight(player, live);
			WebTakeoverPath.stop(player, live.route);
			live.route = null;
			ServerPlayNetworking.send(player, new WebNet.Takeover(-1, 0, false));
		}
		set(player, new WebSymbioteState(false, 0, 0, 0, 0, old.hints(), 0));
		removeArmor(player);
		player.removeAttached(COOLDOWNS);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.45, 0.9, 0.45, 0.12);
		level.sendParticles(dust(2.0F), player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.9, 0.5, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.5, 0.4, 0.5, 0.03);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_DEATH, SoundSource.PLAYERS, 1.5F, 0.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.7F, 1.4F);
		com.jaymospowers.core.Shakes.send(player, 1.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 1.0F, 1.2F);
		player.hurtServer(level, level.damageSources().magic(), EXPOSURE_DAMAGE);
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0, false, false));
		// and it slithers away
		double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
		live.crawlAwayDir = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
		live.crawlAwayPos = player.position();
		live.crawlAwayTicks = CRAWL_AWAY_TICKS;
		whisper(player, "reject");
	}

	/**
	 * You died: it lets go of you for good (the bond is gone; earn it back from scratch with this one or another) and
	 * crawls out of your body as a {@link WebSymbioteBlob}, which stays near where you fell.
	 */
	static void withdraw(ServerPlayer player) {
		WebSymbioteState old = state(player);
		endShroud(player);
		Live live = live(player);
		abortFeeding(player, live);
		if (live.takeoverTicks > 0) {
			live.takeoverTicks = 0;
			dropSight(player, live);
			WebTakeoverPath.stop(player, live.route);
			live.route = null;
			ServerPlayNetworking.send(player, new WebNet.Takeover(-1, 0, false));
		}
		WebTendrils.releaseAll(player, false);
		set(player, new WebSymbioteState(false, 0, 0, old.rejections(), 0, old.hints(), 0));
		removeArmor(player);
		player.removeAttached(COOLDOWNS);
		WebSymbioteBlob.release(player.level(), player.position());
	}

	/** Fills it up and calms it down (a command, for testing). */
	public static void feed(ServerPlayer player) {
		WebSymbioteState state = state(player);
		if (state.bonded()) {
			set(player, state.withSatiety(SATIETY_MAX).withStress(0));
		}
	}

	/** Eating raw meat while bonded feeds it instead of you. Returns true if it took the meal. */
	public static boolean feedOnMeat(ServerPlayer player, ItemStack stack, int nutrition) {
		if (!isBonded(player) || !RAW_MEAT.contains(stack.getItem())) {
			return false;
		}
		WebSymbioteState state = state(player);
		WebSymbioteState next = state.withSatiety(Math.min(SATIETY_MAX, state.satiety() + Math.max(1, nutrition) * MEAT_FEED_PER_NUTRITION))
			.withStress(Math.max(0, state.stress() - 3));
		next = hintOnce(player, next, HINT_MEAT, "meat");
		set(player, next);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 0.5F, 0.7F);
		Vec3 mouth = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.4));
		level.sendParticles(dust(1.0F), mouth.x, mouth.y - 0.1, mouth.z, 8, 0.1, 0.1, 0.1, 0.0);
		return true;
	}

	/** The power is going away: so is the bond's grip on the body (the bond itself is dropped). */
	static void onLost(ServerPlayer player) {
		endShroud(player);
		Live live = player.getAttached(LIVE);
		if (live != null) {
			abortFeeding(player, live);
			if (live.takeoverTicks > 0) {
				// mid-takeover: it lets go of the body (and the client stops steering)
				live.takeoverTicks = 0;
				dropSight(player, live);
				WebTakeoverPath.stop(player, live.route);
				live.route = null;
				ServerPlayNetworking.send(player, new WebNet.Takeover(-1, 0, false));
			}
		}
		removeArmor(player);
		player.removeAttached(STATE);
		player.removeAttached(COOLDOWNS);
		player.removeAttached(LIVE);
	}

	// ---- weaknesses --------------------------------------------------------------------------------------------------

	enum Exposure { FIRE, NOISE }

	/** A game event somewhere in {@code level} (from the server level mixin): loud ones hurt nearby symbiotes. */
	public static void onGameEvent(ServerLevel level, Holder<GameEvent> event, Vec3 pos) {
		double range;
		if (event.is(GameEvent.EXPLODE)) {
			range = EXPLOSION_RANGE;
		} else if (event.is(GameEvent.INSTRUMENT_PLAY)) {
			range = HORN_RANGE;
		} else if (event.is(GameEvent.NOTE_BLOCK_PLAY)) {
			range = NOTE_RANGE;
		} else if (event.is(GameEvent.BLOCK_CHANGE) && level.getBlockState(BlockPos.containing(pos)).is(Blocks.BELL)) {
			range = BELL_RANGE;
		} else {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (isBonded(player) && player.isAlive() && !player.isSpectator() && player.position().distanceToSqr(pos) <= range * range) {
				Live live = live(player);
				long now = level.getGameTime();
				if (now >= live.noiseReadyAt) {
					live.noiseReadyAt = now + NOISE_GRACE_TICKS;
					expose(player, Exposure.NOISE);
				}
			}
		}
	}

	/**
	 * Flames close by (any fire effect, through {@code core/Flames}): while it's out (suit or Living Armor) it flinches
	 * just as when you burn, once per burning episode.
	 */
	public static void nearFlames(ServerPlayer player) {
		if (!(suitShown(player) || armored(player)) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		Live live = live(player);
		live.coolTicks = 0;
		if (!live.burning) {
			live.burning = true;
			expose(player, Exposure.FIRE);
		}
	}

	/** Fire or noise: a painful shudder, stress, and one step closer to it leaving. */
	private static void expose(ServerPlayer player, Exposure kind) {
		WebSymbioteState state = state(player);
		if (!state.bonded()) {
			return;
		}
		ServerLevel level = player.level();
		int rejections = state.rejections() + 1;
		WebSymbioteState next = bondTo(player, state.withRejections(rejections).withStress(Math.min(STRESS_MAX, state.stress() + EXPOSURE_STRESS)),
			state.bond() - BOND_EXPOSURE);
		player.hurtServer(level, level.damageSources().magic(), EXPOSURE_DAMAGE);
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false));
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.35, 0.7, 0.35, 0.06);
		level.sendParticles(dust(1.5F), player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.8, 0.5, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.5F, 1.8F);
		// the suit flinches off you for a moment: a little jolt upward, and your sight swims
		ServerPlayNetworking.send(player, new WebNet.Nudge(new Vec3(0.0, 0.25, 0.0)));
		vignette(player, kind == Exposure.FIRE ? FIRE_VIGNETTE : NOISE_VIGNETTE);
		if (rejections >= REJECT_AT) {
			set(player, next);
			reject(player);
			return;
		}
		set(player, next);
		if (rejections == REJECT_AT - 1 && (next.hints() & HINT_EDGE) == 0) {
			// one more and it's gone: the warning, once
			set(player, hintOnce(player, next, HINT_EDGE, "edge"));
		} else {
			// it shrieks every time (the dark screen and growl come with it)
			whisper(player, (kind == Exposure.FIRE ? "alarm_fire." : "alarm_noise.") + (1 + player.getRandom().nextInt(ALARM_LINES)));
		}
	}

	// ---- takeover ----------------------------------------------------------------------------------------------------

	/** The nearest thing it wants: creatures (not pets or named ones); in a rampage, other players too. */
	private static @org.jspecify.annotations.Nullable LivingEntity prey(ServerPlayer player, boolean rampage) {
		LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(TAKEOVER_RANGE),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand)
				&& (e instanceof Mob m ? !(m instanceof TamableAnimal tame && tame.isTame()) && !m.hasCustomName()
					: rampage && e instanceof Player p && !p.isCreative()))) {
			double d = living.distanceToSqr(player);
			if (d < best) {
				best = d;
				target = living;
			}
		}
		return target;
	}

	private static void startTakeover(ServerPlayer player, Live live, long now) {
		startTakeover(player, live, now, prey(player, live.rampage || live.curious));
	}

	private static void startTakeover(ServerPlayer player, Live live, long now, @org.jspecify.annotations.Nullable LivingEntity target) {
		live.takeoverReadyAt = now + TAKEOVER_COOLDOWN;
		if (target == null) {
			live.curious = false;
			return;
		}
		live.rampageStrikes = 0;
		live.stareUntil = 0L;
		live.struggle = 0.0F;
		live.resisted = false;
		live.takeoverTicks = TAKEOVER_LIMIT;
		live.takeoverTarget = target.getId();
		WebTakeoverPath.stop(player, live.route);
		live.route = new WebTakeoverPath.Route();
		ServerPlayNetworking.send(player, new WebNet.Takeover(target.getId(), live.takeoverTicks, live.fleeing));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 1.3F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.sendParticles(dust(1.6F), player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.4, 0.8, 0.4, 0.0);
		if (!live.fleeing) {
			if (live.feral && !live.curious && !live.hunting) {
				// a different complaint each time
				int line = player.getRandom().nextInt(NEGLECT_LINES - 1);
				live.lastNeglect = line >= live.lastNeglect && live.lastNeglect >= 0 ? line + 1 : line;
				whisper(player, "neglect." + (1 + live.lastNeglect));
			} else {
				whisper(player, live.curious ? "curious" : live.hunting ? "hunt" : "takeover");
			}
		}
	}

	/** The nearest thing set on hurting you: whatever hit you last (if it's still close), or a hostile after you. */
	private static @org.jspecify.annotations.Nullable LivingEntity threat(ServerPlayer player) {
		LivingEntity attacker = player.getLastHurtByMob();
		if (attacker != null && attacker.isAlive() && attacker.distanceTo(player) <= THREAT_RANGE) {
			return attacker;
		}
		LivingEntity best = null;
		double bestD = THREAT_RANGE * THREAT_RANGE;
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(THREAT_RANGE),
			m -> m.isAlive() && m.getTarget() == player)) {
			double d = mob.distanceToSqr(player);
			if (d < bestD) {
				bestD = d;
				best = mob;
			}
		}
		return best;
	}

	/** Last ditch, still hurt or cornered: it hardens into a shell of absorption, once per escape. */
	private static void emergency(ServerPlayer player, Live live) {
		live.emergencyUsed = true;
		player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), EMERGENCY_ABSORPTION));
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.9, 0.5, 0.08);
		level.sendParticles(sheen(1.4F), player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.9, 0.5, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.8F, 1.4F);
		whisper(player, "harden");
	}

	/** Living Armor is being used: holding something, striking, swinging a chunk, tearing at a wall or climbing one. */
	private static boolean armorInUse(ServerPlayer player) {
		WebTendrils.Grips grips = player.getAttached(WebTendrils.GRIPS);
		return grips != null && (grips.get(WebNet.MAIN) >= 0 || grips.get(WebNet.OFF) >= 0) || player.getAttached(STRIKE) != null
			|| WebTendrilCombos.active(player) || WebTendrils.crossing(player) || player.getAttached(WebTendrils.TUG) != null
			|| player.getAttached(WebTendrils.CLIMB) != null || player.getAttached(RIPPING) != null || player.getAttached(WALL) != null;
	}

	/** Low and losing: a Spike Burst whenever it can, and the tendrils lash out with nothing held back. */
	private static void desperate(ServerPlayer player, Live live, long now, LivingEntity threat) {
		tendrilsOut(player);
		player.setAttached(TENDRILS_OUT, now + DESPERATE_EVERY * 2L);
		WebSymbioteState.Cooldowns cooldowns = player.getAttachedOrElse(COOLDOWNS, WebSymbioteState.Cooldowns.NONE);
		if (now >= cooldowns.end(SPIKES) && threat.distanceTo(player) <= SPIKE_RADIUS + 1.0) {
			spikes(player);
			player.setAttached(COOLDOWNS, cooldowns.with(SPIKES, now + COOLDOWN[SPIKES], ABILITIES));
		}
		if (player.getAttached(STRIKE) == null && threat.distanceTo(player) <= STRIKE_RANGE + 1.0) {
			// a lash, almost at once
			lash(player, threat, now, RETALIATE_LAG);
		} else if (threat.distanceTo(player) <= TENDRIL_RANGE && WebTendrils.grabbable(player, threat)) {
			WebTendrils.snatch(player, threat);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.9F, 1.5F);
	}

	/** It hands control back. */
	private static void endTakeover(ServerPlayer player, Live live) {
		if (live.curious) {
			WebTendrils.release(player, WebNet.MAIN, false);
		}
		dropSight(player, live);
		live.rampage = false;
		live.curious = false;
		live.fleeing = false;
		live.hunting = false;
		live.feral = false;
		live.struggle = 0.0F;
		live.stareUntil = 0L;
		live.takeoverTicks = 0;
		live.takeoverTarget = -1;
		WebTakeoverPath.stop(player, live.route);
		live.route = null;
		player.removeAttached(STARING);
		ServerPlayNetworking.send(player, new WebNet.Takeover(-1, 0, false));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
	}

	/** The takeover's hold, told to the client again before it runs out (a takeover has no time limit). */
	private static void renewHold(ServerPlayer player, Live live) {
		if (--live.takeoverTicks <= HOLD_RENEW) {
			live.takeoverTicks = TAKEOVER_LIMIT;
			ServerPlayNetworking.send(player, new WebNet.Takeover(live.takeoverTarget, live.takeoverTicks, false));
		}
	}

	/** Its prey is dead: it keeps the wheel, holding you still, while it gets the corpse in and eats (you as the "target"). */
	private static void holdForMeal(ServerPlayer player, Live live) {
		live.takeoverTarget = player.getId();
		live.stareUntil = 0L;
		WebTakeoverPath.stop(player, live.route);
		live.route = null;
		live.takeoverTicks = TAKEOVER_LIMIT;
		ServerPlayNetworking.send(player, new WebNet.Takeover(player.getId(), live.takeoverTicks, false));
	}

	/** Moves you along the takeover's way (see {@link WebTakeoverPath}). */
	private static void drive(ServerPlayer player, Live live, Entity target, boolean flee, long now) {
		if (live.route == null) {
			live.route = new WebTakeoverPath.Route();
		}
		WebTakeoverPath.tick(player, live.route, target, flee, live.curious ? TAKEOVER_REACH + 1.0 : TAKEOVER_REACH, now);
	}

	/**
	 * A key pressed against a takeover's hold (the client swallows every key while it drives). Until enough of its
	 * abilities are earned it pays no attention; after that, enough presses close together break its hold.
	 */
	private static void struggle(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live == null || live.takeoverTicks <= 0 || !isBonded(player)) {
			return;
		}
		long now = player.level().getGameTime();
		if (now == live.struggleAt) {
			return;
		}
		live.struggleAt = now;
		int needed = breakFreeNeeded(player);
		if (needed < 0) {
			if (!live.resisted) {
				live.resisted = true;
				whisper(player, "resist_locked");
			}
			return;
		}
		live.struggle += 1.0F;
		if (live.struggle >= needed) {
			breakFree(player, live, now);
		} else if (live.struggle >= needed * 0.5F && !live.resisted) {
			live.resisted = true;
			whisper(player, "resist");
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.8F, 1.4F);
		}
	}

	/** You wrench yourself back from it: the takeover ends, it seethes, and the bond frays a little. */
	private static void breakFree(ServerPlayer player, Live live, long now) {
		if (live.hunting) {
			abortFeeding(player, live);
		}
		if (live.takeoverTicks > 0) {
			endTakeover(player, live);
		}
		live.takeoverReadyAt = now + TAKEOVER_COOLDOWN;
		WebSymbioteState state = state(player);
		set(player, bondTo(player, state.withStress(Math.min(STRESS_MAX, state.stress() + BREAK_FREE_STRESS)), state.bond() - BOND_BREAK_FREE));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ANGRY, SoundSource.PLAYERS, 0.9F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.7, 0.4, 0.05);
		com.jaymospowers.core.Shakes.send(player, 1.2F, null);
		whisper(player, "break_free");
	}

	/** The sightseeing markers in use (anything else carrying {@link #TOUR_TAG} is a leftover). */
	private static final java.util.Map<java.util.UUID, Long> SIGHTS = new java.util.HashMap<>();

	/** The tour's over: its marker goes. */
	private static void dropSight(ServerPlayer player, Live live) {
		if (live.touring && player.level().getEntity(live.takeoverTarget) instanceof net.minecraft.world.entity.Interaction sight) {
			SIGHTS.remove(sight.getUUID());
			sight.discard();
		}
		live.touring = false;
	}

	/** Every so often it says what it makes of where you are (see {@link #REMARK_GAP_MAX}). */
	private static void remark(ServerPlayer player, Live live, long now) {
		float bond = Mth.clamp(state(player).bond() / (float)BOND_MAX, 0.0F, 1.0F);
		long gap = Math.round(Mth.lerp(bond, REMARK_GAP_MAX, REMARK_GAP_MIN)) + player.getRandom().nextInt(REMARK_JITTER + 1);
		if (live.remarkAt == 0L) {
			live.remarkAt = now + gap;
			return;
		}
		if (now < live.remarkAt || now < live.narrateAt) {
			return;
		}
		live.remarkAt = now + gap;
		List<String> topics = surroundings(player);
		if (!topics.isEmpty()) {
			whisper(player, "see." + topics.get(player.getRandom().nextInt(topics.size())) + "." + (1 + player.getRandom().nextInt(REMARK_LINES)));
		}
	}

	/** What there is to remark on around you right now. */
	private static List<String> surroundings(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos at = BlockPos.containing(player.getEyePosition());
		List<String> topics = new java.util.ArrayList<>();
		boolean sky = level.canSeeSky(at);
		if (sky && level.isThundering()) {
			topics.add("storm");
		} else if (sky && level.isRainingAt(at)) {
			topics.add("rain");
		}
		if (!level.dimensionType().hasFixedTime() && level.isDarkOutside() && sky) {
			topics.add("night");
		}
		if (!sky && player.getY() < level.getSeaLevel() - 12) {
			topics.add("deep");
		}
		if (sky && player.getY() > level.getSeaLevel() + 50) {
			topics.add("high");
		}
		float heat = level.getBiome(at).value().getBaseTemperature();
		if (heat >= 1.5F) {
			topics.add("hot");
		} else if (heat <= 0.15F) {
			topics.add("cold");
		}
		if (player.isInWater()) {
			topics.add("water");
		}
		AABB around = player.getBoundingBox().inflate(REMARK_RANGE);
		if (!level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.AbstractVillager.class, around, Entity::isAlive).isEmpty()) {
			topics.add("village");
		}
		if (!level.getEntitiesOfClass(Mob.class, around, m -> m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy).isEmpty()) {
			topics.add("monsters");
		}
		if (!level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, around, Entity::isAlive).isEmpty()) {
			topics.add("beasts");
		}
		if (!level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(REMARK_RANGE * 1.5),
			p -> p != player && p.isAlive() && !p.isSpectator()).isEmpty()) {
			topics.add("player");
		}
		if (!level.getEntities(player, around, com.jaymospowers.power.gravity.GravityPower::isCorpse).isEmpty()) {
			topics.add("corpse");
		}
		if (BlockPos.betweenClosedStream(player.blockPosition().offset(-5, -3, -5), player.blockPosition().offset(5, 3, 5))
			.anyMatch(pos -> level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA))) {
			topics.add("lava");
		}
		if (topics.isEmpty()) {
			topics.add("quiet");
		}
		return topics;
	}

	/** It wants to see something: it finds it (see {@link #TOUR_CHANCE}) and takes the wheel to walk you there. */
	private static void startTour(ServerPlayer player, Live live, long now) {
		live.takeoverReadyAt = now + TAKEOVER_COOLDOWN;
		ServerLevel level = player.level();
		if (!level.canSeeSky(BlockPos.containing(player.getEyePosition()))) {
			return;
		}
		Vec3 best = null;
		String sight = null;
		double bestScore = 0.0;
		// someone to look at
		for (net.minecraft.world.entity.npc.villager.AbstractVillager villager : level.getEntitiesOfClass(
			net.minecraft.world.entity.npc.villager.AbstractVillager.class, player.getBoundingBox().inflate(TOUR_MAX), Entity::isAlive)) {
			double d = villager.distanceTo(player);
			if (d >= TOUR_MIN && 1.3 + player.getRandom().nextDouble() * 0.5 > bestScore) {
				bestScore = 1.3 + player.getRandom().nextDouble() * 0.5;
				best = villager.position();
				sight = "village";
			}
		}
		for (int i = 0; i < TOUR_SAMPLES; i++) {
			double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
			double d = TOUR_MIN + player.getRandom().nextDouble() * (TOUR_MAX - TOUR_MIN);
			BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * d, 0.0, player.getZ() + Math.sin(angle) * d);
			if (!level.hasChunkAt(column)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			net.minecraft.world.level.material.FluidState fluid = level.getFluidState(top.below());
			if (fluid.is(net.minecraft.tags.FluidTags.LAVA) || level.getFluidState(top).is(net.minecraft.tags.FluidTags.LAVA)) {
				continue;
			}
			String kind;
			double score;
			Vec3 spot = Vec3.atBottomCenterOf(top);
			if (fluid.is(net.minecraft.tags.FluidTags.WATER)) {
				// the shore nearest you, looking out over it
				Vec3 back = player.position().subtract(spot).horizontal().normalize();
				Vec3 shore = null;
				for (int step = 1; step <= 6 && shore == null; step++) {
					BlockPos dry = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						BlockPos.containing(spot.add(back.scale(step))));
					if (level.getFluidState(dry.below()).isEmpty()) {
						shore = Vec3.atBottomCenterOf(dry);
					}
				}
				if (shore == null) {
					continue;
				}
				spot = shore;
				kind = "water";
				score = 1.0;
			} else if (top.getY() >= player.getY() + TOUR_VIEW_RISE) {
				kind = "view";
				score = 1.0 + (top.getY() - player.getY()) * 0.08;
			} else if (level.getBlockState(top).is(net.minecraft.tags.BlockTags.FLOWERS)) {
				kind = "flowers";
				score = 1.1;
			} else {
				continue;
			}
			score += player.getRandom().nextDouble() * 0.5;
			if (score > bestScore) {
				bestScore = score;
				best = spot;
				sight = kind;
			}
		}
		if (best == null) {
			return;
		}
		net.minecraft.world.entity.Interaction marker = net.minecraft.world.entity.EntityTypes.INTERACTION.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		if (marker == null) {
			return;
		}
		marker.setPos(best);
		marker.addTag(TOUR_TAG);
		SIGHTS.put(marker.getUUID(), now + TOUR_TICKS + 100L);
		if (!level.addFreshEntity(marker)) {
			SIGHTS.remove(marker.getUUID());
			return;
		}
		live.touring = true;
		live.tourSight = sight;
		live.tourFrom = player.position();
		live.stareUntil = 0L;
		live.struggle = 0.0F;
		live.resisted = false;
		live.takeoverTicks = TOUR_TICKS;
		live.takeoverTarget = marker.getId();
		WebTakeoverPath.stop(player, live.route);
		live.route = new WebTakeoverPath.Route();
		ServerPlayNetworking.send(player, new WebNet.Takeover(marker.getId(), live.takeoverTicks, false));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6F, 1.3F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SNIFF, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.sendParticles(dust(1.4F), player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.8, 0.4, 0.0);
		whisper(player, "tour." + sight + ".go." + (1 + player.getRandom().nextInt(TOUR_LINES)));
	}

	/** On its way to the sight; there, it turns your head to it, says so, and lets go. */
	private static void tickTour(ServerPlayer player, Live live, long now, @org.jspecify.annotations.Nullable Entity target) {
		if (target == null || !target.isAlive() || --live.takeoverTicks <= 0) {
			endTakeover(player, live);
			return;
		}
		drive(player, live, target, false, now);
		Vec3 to = target.position().subtract(player.position());
		if (to.horizontalDistance() > TOUR_REACH || Math.abs(to.y) > 2.0) {
			if (live.takeoverTicks % 5 == 0) {
				player.level().sendParticles(dust(1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.6, 0.3, 0.0);
			}
			return;
		}
		// there: on along the way it came, and a little down (out over the view, the water, the flowers)
		Vec3 out = player.position().subtract(live.tourFrom).horizontal();
		if (live.tourSight.equals("village")) {
			out = to.horizontal();
		}
		if (out.lengthSqr() > 1.0E-4) {
			float yaw = (float)(Mth.atan2(out.z, out.x) * Mth.RAD_TO_DEG) - 90.0F;
			float pitch = live.tourSight.equals("view") ? 15.0F : live.tourSight.equals("village") ? 0.0F : 30.0F;
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket(yaw, false, pitch, false));
		}
		String sight = live.tourSight;
		endTakeover(player, live);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SNIFF, SoundSource.PLAYERS, 0.8F, 1.0F);
		whisper(player, "tour." + sight + ".there." + (1 + player.getRandom().nextInt(TOUR_LINES)));
	}

	/** Curiosity: up close it grabs them, holds them up to its face and stares; then sets them down, or doesn't. */
	private static void tickCurious(ServerPlayer player, Live live, long now, Entity target) {
		ServerLevel level = player.level();
		if (live.stareUntil == 0L) {
			if (player.distanceTo(target) - target.getBbWidth() * 0.5 > TAKEOVER_REACH + 1.0) {
				return;
			}
			live.stareUntil = now + STARE_MIN + player.getRandom().nextInt(STARE_MAX - STARE_MIN + 1);
			player.setAttached(STARING, now);
			// the tendrils stay out (and connected) for the whole look
			player.setAttached(TENDRILS_OUT, live.stareUntil + 10);
			WebTendrils.grab(player, WebNet.MAIN, target, -1L);
			narrate(player, "stare");
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.6F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SNIFF, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
		// held up in front of its face by the tendril (see WebTendrils)
		Vec3 look = player.getViewVector(1.0F).horizontal();
		look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
		target.resetFallDistance();
		if ((now - live.stareUntil) % 30 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SNIFF, SoundSource.PLAYERS, 0.7F, 0.9F);
		}
		if (now < live.stareUntil) {
			return;
		}
		if (player.getRandom().nextFloat() < CURIOUS_ATTACK && target instanceof LivingEntity living) {
			// it didn't like what it saw
			living.hurtServer(level, level.damageSources().playerAttack(player), STRIKE_DAMAGE);
			Vec3 away = living.position().subtract(player.position()).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? look : away.normalize();
			living.setDeltaMovement(away.scale(0.9).add(0.0, 0.35, 0.0));
			living.syncVelocity = true;
			level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.5F);
			whisper(player, "not_looking");
		} else {
			// set down, gently: lowered to the ground, not dropped
			WebTendrils.release(player, WebNet.MAIN, false);
			BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.blockPosition());
			if (ground.getY() <= target.getY() && target.getY() - ground.getY() < 4.0) {
				target.teleportTo(target.getX(), ground.getY(), target.getZ());
			}
			target.setDeltaMovement(Vec3.ZERO);
			target.resetFallDistance();
			target.syncVelocity = true;
			whisper(player, "just_looking");
		}
		endTakeover(player, live);
	}

	private static void tickTakeover(ServerPlayer player, Live live, long now) {
		Entity target = player.level().getEntity(live.takeoverTarget);
		live.struggle = Math.max(0.0F, live.struggle - STRUGGLE_FADE);
		if (live.fleeing) {
			// running: until nothing's after it any more, or it's far enough off
			LivingEntity threat = threat(player);
			if (--live.takeoverTicks <= 0 || threat == null || threat.distanceTo(player) > FLEE_SAFE) {
				endTakeover(player, live);
			} else {
				if (threat != target && threat.getId() != live.takeoverTarget) {
					live.takeoverTarget = threat.getId();
					ServerPlayNetworking.send(player, new WebNet.Takeover(threat.getId(), live.takeoverTicks, true));
				}
				// a way out: the safest spot it can reach (tearing out of a dead end)
				drive(player, live, threat, true, now);
				// still taking hits, or cornered: it hardens over you
				if (!live.emergencyUsed && (live.fleeHits >= EMERGENCY_HITS
					|| now - live.fleeStartAt > STUCK_TICKS && threat.distanceTo(player) < STUCK_NEAR)) {
					emergency(player, live);
				}
			}
			return;
		}
		if (live.touring) {
			tickTour(player, live, now, target);
			return;
		}
		if (target == player) {
			// its kill carried off: it holds you still through the grab, the carry and the meal, and lets go after
			if (live.feedStage == FEED_NONE) {
				endTakeover(player, live);
			} else {
				renewHold(player, live);
			}
			return;
		}
		// it lets go once its prey is dead (and, if it's hungry, eaten), or has got clean away; never on a timer
		if (target == null || !target.isAlive() || target.distanceTo(player) > TAKEOVER_ESCAPE) {
			boolean dead = target != null && !target.isAlive();
			if (dead && live.hunting && live.feedStage == FEED_KILL) {
				// the hunt's kill: on to the corpse, still at the wheel
				holdForMeal(player, live);
				return;
			}
			boolean killed = dead && !live.curious && !live.hunting;
			if (killed) {
				narrate(player, "kill");
			}
			if (killed && live.feedStage == FEED_NONE && player.getAttached(EATING) == null
				&& (state(player).satiety() < SELF_FEED_BELOW || live.craving || live.gorging)
				&& target instanceof LivingEntity prey && edible(prey) && !sulking(player)) {
				// a kill of its own: now it feeds, on the corpse, still at the wheel
				startKill(player, live, now, prey, false);
				live.rampage = false;
				live.feral = false;
				live.hunting = true;
				holdForMeal(player, live);
				return;
			}
			endTakeover(player, live);
			return;
		}
		renewHold(player, live);
		if (!live.curious || live.stareUntil == 0L) {
			// on its way: round walls, or through them
			drive(player, live, target, false, now);
		}
		if (live.curious) {
			tickCurious(player, live, now, target);
			return;
		}
		if (live.takeoverTicks % TAKEOVER_ATTACK_TICKS == 0 && player.distanceTo(target) - target.getBbWidth() * 0.5 <= TAKEOVER_REACH) {
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			player.attack(target);
			if (live.rampage && target instanceof LivingEntity living && living.isAlive()) {
				if (living instanceof Player && ++live.rampageStrikes % RAMPAGE_BITE_EVERY == 0) {
					// it bites: tearing into them (it feeds on nothing living: only on what it kills, carried)
					living.hurtServer(player.level(), player.level().damageSources().playerAttack(player), RAMPAGE_BITE_DAMAGE);
					ServerLevel level = player.level();
					level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 1.2F, 0.4F);
					level.sendParticles(ParticleTypes.SQUID_INK, living.getX(), living.getY() + 1.0, living.getZ(), 15, 0.3, 0.4, 0.3, 0.05);
				}
			}
		}
		if (live.takeoverTicks % 5 == 0) {
			player.level().sendParticles(dust(1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.3, 0.6, 0.3, 0.0);
		}
	}

	// ---- abilities ---------------------------------------------------------------------------------------------------

	/** A radial-menu pick. Server-authoritative: bond, cooldown and satiety are all checked here. */
	static void use(ServerPlayer player, int ability) {
		if ((ability == SUIT_TOGGLE || ability == ARMOR) && isBonded(player) && sulking(player)) {
			// it won't come: it's still angry with you
			whisper(player, "sulking");
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.6F, 1.5F);
			return;
		}
		if (ability == SUIT_TOGGLE && isBonded(player)) {
			WebSymbioteState state = state(player);
			boolean on = !state.has(WebSymbioteState.SUIT);
			if (on && !suitAllowed(player)) {
				// it showed you once; it won't cover you again until it trusts you more
				whisper(player, "suit_locked");
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.5F, 0.4F);
				return;
			}
			WebSymbioteState next = state.withFlag(WebSymbioteState.SUIT, on);
			// (the tendrils stay out: they work over any suit, or none)
			set(player, next);
			ServerLevel level = player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), on ? SoundEvents.SLIME_BLOCK_PLACE : SoundEvents.HONEY_BLOCK_SLIDE,
				SoundSource.PLAYERS, 0.8F, on ? 0.6F : 1.2F);
			level.sendParticles(dust(1.2F), player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.3, 0.7, 0.3, 0.0);
			return;
		}
		if (ability < 0 || ability >= ABILITIES || !isBonded(player) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		Live live = live(player);
		if (live.takeoverTicks > 0 || live.bondTicks > 0 || live.roarAt > 0L) {
			// it isn't listening right now
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!unlocked(player, ability)) {
			// it hasn't given you that (yet)
			whisper(player, "locked");
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.5F, 0.4F);
			return;
		}
		WebSymbioteState.Cooldowns cooldowns = player.getAttached(COOLDOWNS);
		if (cooldowns == null) {
			cooldowns = WebSymbioteState.Cooldowns.NONE;
		}
		if (now < cooldowns.end(ability)) {
			return;
		}
		WebSymbioteState state = state(player);
		if (state.satiety() < COST[ability] && !player.isCreative()) {
			// too weak
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.5F, 0.4F);
			whisper(player, "weak");
			return;
		}
		// a weak bond barely listens (it always lets you put a toggle away)
		boolean puttingAway = ability == ARMOR && state.has(WebSymbioteState.ARMOR) || ability == CRAWL && state.has(WebSymbioteState.CRAWL);
		if (!puttingAway && !player.isCreative() && state.bond() < IGNORE_BOND
			&& player.getRandom().nextFloat() < IGNORE_MAX * (1.0F - state.bond() / (float)IGNORE_BOND)) {
			whisper(player, "ignores");
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.5F, 1.6F);
			player.setAttached(COOLDOWNS, cooldowns.with(ability, now + 20, ABILITIES));
			return;
		}
		boolean done = switch (ability) {
			case TENDRIL -> letGoOfWall(player) || WebTendrils.radialGrab(player) || tendril(player);
			case ARMOR -> toggle(player, WebSymbioteState.ARMOR);
			case CRAWL -> toggle(player, WebSymbioteState.CRAWL);
			case CONSUME -> consume(player);
			// in the big body, the Spike Burst is a roar
			case SPIKES -> bulked(player) ? startRoar(player, live, now, false, null) : spikes(player);
			case SHROUD -> shroud(player);
			default -> false;
		};
		if (!done) {
			return;
		}
		if (ability != TENDRIL && player.getAttached(WALL) != null) {
			// anything else it does, it lets go of the wall for
			letGo(player, live);
		}
		if (ability == SPIKES || ability == SHROUD) {
			narrate(player, ability == SHROUD ? "shroud" : bulked(player) ? "roar" : "spikes");
		}
		int cooldown = COOLDOWN[ability];
		if (ability == SHROUD) {
			// its cooldown runs from when it wears off
			cooldown += SHROUD_TICKS;
		} else if (ability == ARMOR) {
			// out: it can be put away again soon; away: its cooldown starts now
			if (state(player).has(WebSymbioteState.ARMOR)) {
				cooldown = ARMOR_TOGGLE_GAP;
				live.armorOnAt = now;
				live.armorUsedAt = now;
				com.jaymospowers.core.Shakes.send(player, ARMOR_ON_SHAKE);
			}
		}
		player.setAttached(COOLDOWNS, cooldowns.with(ability, now + cooldown, ABILITIES));
		if (COST[ability] > 0 && !player.isCreative()) {
			WebSymbioteState after = state(player);
			set(player, after.withSatiety(Math.max(0, after.satiety() - COST[ability])));
		}
	}

	/** Tendril Grab: a creature under the crosshair is yanked to you; a block yanks you to it. */
	private static boolean tendril(ServerPlayer player) {
		ServerLevel level = player.level();
		Entity target = WebPower.entityInSight(player, TENDRIL_RANGE);
		Vec3 eye = player.getEyePosition();
		if (target != null) {
			Vec3 center = target.getBoundingBox().getCenter();
			double volume = target.getBbWidth() * target.getBbWidth() * target.getBbHeight();
			if (volume > TENDRIL_HEAVY_VOLUME) {
				int maxTicks = Mth.ceil(center.distanceTo(player.position()) / WebPower.ZIP_SPEED) + 12;
				WebPower.startPull(player, new WebNet.Pull(center, target.position(), target.getId(), maxTicks, (byte)-1),
					new WebLine(WebLine.YANK, center, target.getId()));
			} else {
				Vec3 to = player.position().subtract(target.position());
				double horizontal = to.horizontalDistance();
				double speed = Math.min(2.2, Math.max(0.0, horizontal - 1.5) * 0.16 + 0.2);
				double up = Mth.clamp(0.45 + to.y * 0.08 + horizontal * 0.01, 0.25, 1.2);
				Vec3 flat = new Vec3(to.x, 0.0, to.z).normalize().scale(speed);
				if (target instanceof Mob mob) {
					mob.getNavigation().stop();
				}
				target.setDeltaMovement(flat.x, up, flat.z);
				target.needsSync = true;
				target.syncVelocity = true;
				if (target instanceof ServerPlayer other) {
					ServerPlayNetworking.send(other, new WebNet.Nudge(new Vec3(flat.x, up, flat.z)));
				}
				WebPower.showLine(player, new WebLine(WebLine.YANK, center, target.getId()), 10);
			}
			trail(level, eye, center);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.6F);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.9F, 0.9F);
			return true;
		}
		BlockHitResult hit = WebAnchors.clip(player, eye, eye.add(player.getViewVector(1.0F).scale(TENDRIL_RANGE)));
		if (hit.getType() == HitResult.Type.MISS) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.4F, 0.8F);
			return false;
		}
		Vec3 anchor = hit.getLocation();
		Direction face = hit.getDirection();
		Vec3 feet = face == Direction.UP ? anchor.add(0.0, 0.05, 0.0)
			: face == Direction.DOWN ? anchor.add(0.0, -player.getBbHeight() - 0.1, 0.0)
			: anchor.add(face.getUnitVec3().scale(player.getBbWidth() * 0.5 + 0.2)).add(0.0, -1.3, 0.0);
		int maxTicks = Mth.ceil(feet.distanceTo(player.position()) / WebPower.ZIP_SPEED) + 12;
		WebPower.startPull(player, new WebNet.Pull(anchor, feet, -1, maxTicks, (byte)face.ordinal()), new WebLine(WebLine.ZIP, anchor, -1));
		trail(level, eye, anchor);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.6F);
		// every tendril reaches for it (see tickWall)
		Live live = live(player);
		clearWall(player, live);
		live.wallFeet = feet;
		live.wallFace = face;
		live.wallMaxTicks = maxTicks;
		long now = level.getGameTime();
		player.setAttached(WALL, new Wall(anchor, now, -1L, feet, face.get3DDataValue(), List.of()));
		player.setAttached(TENDRILS_OUT, now + maxTicks + WALL_HOLD);
		return true;
	}

	static void trail(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		int steps = Math.max(2, (int)(d.length() * 1.5));
		for (int i = 1; i <= steps; i++) {
			Vec3 at = from.add(d.scale(i / (double)steps));
			level.sendParticles(dust(0.9F), at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	private static boolean toggle(ServerPlayer player, int flag) {
		WebSymbioteState state = state(player);
		boolean on = !state.has(flag);
		WebSymbioteState next = state.withFlag(flag, on);
		set(player, next);
		ServerLevel level = player.level();
		if (flag == WebSymbioteState.ARMOR) {
			// the tendrils (over whatever suit, or none): the big body only comes on its own
			level.playSound(null, player.getX(), player.getY(), player.getZ(), on ? SoundEvents.SLIME_BLOCK_PLACE : SoundEvents.SLIME_BLOCK_BREAK,
				SoundSource.PLAYERS, 1.0F, on ? 0.5F : 0.8F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
		} else {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), on ? SoundEvents.SPIDER_AMBIENT : SoundEvents.SPIDER_STEP,
				SoundSource.PLAYERS, 0.7F, on ? 0.6F : 1.0F);
		}
		level.sendParticles(dust(1.2F), player.getX(), player.getY() + 1.0, player.getZ(), on ? 18 : 8, 0.35, 0.7, 0.35, 0.0);
		return true;
	}

	private static void applyArmor(ServerPlayer player) {
		AttributeInstance absorb = player.getAttribute(Attributes.MAX_ABSORPTION);
		if (absorb != null && !absorb.hasModifier(ARMOR_ABSORB_ID)) {
			absorb.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_ABSORB_ID, ARMOR_ABSORPTION, AttributeModifier.Operation.ADD_VALUE));
		}
		AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null && !knockback.hasModifier(ARMOR_KNOCKBACK_ID)) {
			knockback.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_KNOCKBACK_ID, ARMOR_KNOCKBACK_RESIST, AttributeModifier.Operation.ADD_VALUE));
		}
		// swollen with it: bigger than a body should be
		AttributeInstance scale = player.getAttribute(Attributes.SCALE);
		if (scale != null && !scale.hasModifier(ARMOR_SCALE_ID)) {
			scale.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_SCALE_ID, ARMOR_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	private static void removeArmor(ServerPlayer player) {
		AttributeInstance absorb = player.getAttribute(Attributes.MAX_ABSORPTION);
		if (absorb != null && absorb.hasModifier(ARMOR_ABSORB_ID)) {
			absorb.removeModifier(ARMOR_ABSORB_ID);
		}
		AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null && knockback.hasModifier(ARMOR_KNOCKBACK_ID)) {
			knockback.removeModifier(ARMOR_KNOCKBACK_ID);
		}
		AttributeInstance scale = player.getAttribute(Attributes.SCALE);
		if (scale != null && scale.hasModifier(ARMOR_SCALE_ID)) {
			scale.removeModifier(ARMOR_SCALE_ID);
		}
	}

	/** The symbiote looks after itself (see {@link #INSTINCT_CHECK_TICKS}). Returns the state (the armour may come up). */
	private static WebSymbioteState instinct(ServerPlayer player, Live live, WebSymbioteState state, long now) {
		ServerLevel level = player.level();
		var random = player.getRandom();
		BlockPos fire = nearFire(player);
		boolean burning = player.isOnFire() || fire != null;
		Vec3 blast = explosiveNear(player);
		boolean falling = !player.onGround() && player.getY() - player.yo < -0.55 && player.fallDistance > 5.0F;
		LivingEntity threat = null;
		double best = INSTINCT_THREAT_RANGE * INSTINCT_THREAT_RANGE;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(INSTINCT_THREAT_RANGE),
			m -> m.isAlive() && m.getTarget() == player)) {
			double d = mob.distanceToSqr(player);
			if (d < best) {
				best = d;
				threat = mob;
			}
		}
		boolean danger = burning || blast != null || falling || threat != null || player.getHealth() <= INSTINCT_LOW_HEALTH;
		if (!danger) {
			return state;
		}
		if (sulking(player)) {
			return state;
		}
		// it never swells into the armour unasked: out of it, its tendrils burst from your back to act
		state = hintOnce(player, state, HINT_INSTINCT, "instinct");
		narrate(player, "instinct");
		// a fall about to end: it reaches for the ground and takes the blow
		if (falling && !live.caughtFall && groundWithin(player, INSTINCT_FALL_CATCH_HEIGHT)) {
			live.caughtFall = true;
			tendrilsOut(player);
			player.resetFallDistance();
			ServerPlayNetworking.send(player, new WebNet.Nudge(new Vec3(0.0, -0.12, 0.0)));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 1.0F, 0.5F);
			level.sendParticles(dust(1.6F), player.getX(), player.getY(), player.getZ(), 16, 0.6, 0.1, 0.6, 0.0);
			return state;
		}
		if (now < live.instinctReadyAt) {
			return state;
		}
		if (blast != null) {
			// something's about to go off: it hurls you clear
			live.instinctReadyAt = now + INSTINCT_COOLDOWN;
			tendrilsOut(player);
			Vec3 away = player.position().subtract(blast);
			away = new Vec3(away.x, 0.0, away.z);
			away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().reverse().horizontal() : away.normalize();
			ServerPlayNetworking.send(player, new WebNet.Nudge(away.normalize().scale(INSTINCT_BLAST_SHOVE).add(0.0, 0.45, 0.0)));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.9F, 1.6F);
			level.sendParticles(dust(1.4F), player.getX(), player.getY() + 0.3, player.getZ(), 16, 0.4, 0.2, 0.4, 0.0);
			return state;
		}
		if (burning) {
			// it fears fire: it smothers the flames and shoves you clear
			live.instinctReadyAt = now + INSTINCT_COOLDOWN;
			tendrilsOut(player);
			player.clearFire();
			Vec3 away = fire == null ? player.getLookAngle().reverse() : player.position().subtract(Vec3.atCenterOf(fire));
			away = new Vec3(away.x, 0.0, away.z);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			ServerPlayNetworking.send(player, new WebNet.Nudge(away.scale(INSTINCT_FIRE_SHOVE).add(0.0, 0.4, 0.0)));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.7F, 1.4F);
			return state;
		}
		if (threat != null) {
			live.instinctReadyAt = now + INSTINCT_COOLDOWN;
			tendrilsOut(player);
			if (random.nextFloat() < INSTINCT_GRAB_CHANCE && WebTendrils.grabbable(player, threat)) {
				// it snatches the attacker and flings it off
				WebTendrils.snatch(player, threat);
			} else {
				// it shoves you out of reach
				Vec3 away = player.position().subtract(threat.position());
				away = new Vec3(away.x, 0.0, away.z);
				away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
				ServerPlayNetworking.send(player, new WebNet.Nudge(away.scale(INSTINCT_THREAT_SHOVE).add(0.0, 0.3, 0.0)));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.5F);
			}
			return state;
		}
		return state;
	}

	/** Running with the armour up, now and then it catapults you along (checked with the instinct). */
	private static void catapult(ServerPlayer player, Live live, long now) {
		if (!player.isSprinting() || !player.onGround() || now < live.instinctReadyAt
			|| player.getRandom().nextFloat() >= INSTINCT_CATAPULT_CHANCE) {
			return;
		}
		live.instinctReadyAt = now + INSTINCT_COOLDOWN * 2;
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		if (flat.lengthSqr() < 1.0E-4) {
			return;
		}
		ServerPlayNetworking.send(player, new WebNet.Nudge(flat.normalize().scale(INSTINCT_CATAPULT).add(0.0, 0.42, 0.0)));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.4F);
		level.sendParticles(dust(1.4F), player.getX(), player.getY() + 0.2, player.getZ(), 14, 0.4, 0.1, 0.4, 0.0);
	}

	/**
	 * A wall grab under way: arriving, you're turned with your back to the wall and the tendrils lift you off it, each
	 * lower one biting into its own spot; then you hang there on them (see {@link #WALL_LIFT}) until you jump off, use
	 * the grab again, use anything else on the radial menu, or get dragged too far.
	 */
	private static void tickWall(ServerPlayer player, Live live, Wall wall, long now) {
		ServerLevel level = player.level();
		if (wall.landed() < 0L) {
			if (player.position().distanceTo(live.wallFeet) > 1.4 && now - wall.start() <= live.wallMaxTicks) {
				return;
			}
			if (player.position().distanceTo(live.wallFeet) > 3.0) {
				// never got there
				clearWall(player, live);
				return;
			}
			land(player, live, wall, now);
			return;
		}
		// tethered: the tendrils dug in, holding you off the wall
		player.setAttached(TENDRILS_OUT, now + 20);
		player.resetFallDistance();
		boolean jumped = player.getLastClientInput().jump() && now - wall.landed() > 5;
		if (jumped || player.position().distanceTo(wall.rest()) > WALL_SNAP) {
			if (jumped) {
				// kicked off it, away from the wall
				Vec3 out = live.wallFace.getUnitVec3().scale(0.5).add(0.0, 0.45, 0.0);
				ServerPlayNetworking.send(player, new WebNet.Nudge(out));
			}
			letGo(player, live);
			return;
		}
		// each crack bites as its tendril does (one a tick), then deepens the harder that tendril is stretched
		long age = now - wall.landed();
		Vec3 middle = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		boolean refresh = now >= live.wallRefreshAt;
		if (refresh) {
			live.wallRefreshAt = now + 100;
		}
		for (int i = 0; i < live.wallCracks.size() && i < wall.points().size(); i++) {
			if (age < i) {
				continue;
			}
			BlockPos pos = live.wallCracks.get(i);
			Vec3 point = wall.points().get(i);
			int stage;
			if (age == i) {
				// it bites in
				stage = TETHER_BITE_STAGE;
				level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos)),
					point.x, point.y, point.z, 8, 0.12, 0.12, 0.12, 0.12);
				level.playSound(null, point.x, point.y, point.z, level.getBlockState(pos).getSoundType().getHitSound(), SoundSource.BLOCKS, 0.9F,
					0.6F + 0.08F * i);
			} else {
				double stretch = Math.max(0.0, point.distanceTo(middle) - live.wallRestReach[i]);
				int tension = (int)(stretch / TETHER_STAGE_STRETCH);
				// settling from the bite to its rest, then deeper with the strain
				int settle = Math.max(TETHER_REST_STAGE, TETHER_BITE_STAGE - (int)((age - i) / 3));
				stage = Math.min(9, Math.max(settle, TETHER_REST_STAGE + tension));
			}
			if (stage != live.wallStages[i] || refresh) {
				if (stage > live.wallStages[i] && age > i + 1 && player.getRandom().nextInt(3) == 0) {
					// it grinds deeper: grit spills from the bite
					level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos)),
						point.x, point.y, point.z, 3, 0.08, 0.08, 0.08, 0.05);
				}
				live.wallStages[i] = stage;
				level.destroyBlockProgress(crackId(player, i), pos, stage);
			}
		}
	}

	/** There: back to the wall, and the tendrils pick their spots to bite into, around you, a block out from it. */
	private static void land(ServerPlayer player, Live live, Wall wall, long now) {
		ServerLevel level = player.level();
		Direction face = live.wallFace;
		Vec3 normal = face.getUnitVec3();
		if (face.getAxis().isHorizontal()) {
			float yaw = (float)(Mth.atan2(normal.z, normal.x) * Mth.RAD_TO_DEG) - 90.0F;
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket(yaw, false, 0.0F, true));
		}
		Vec3 rest = player.position().add(normal.scale(WALL_LIFT));
		// the wall's own frame: to the wearer's right along it, and "up" (on a ceiling or floor, the way you face)
		Vec3 facing = face.getAxis().isHorizontal() ? normal : Vec3.directionFromRotation(0.0F, player.getYRot());
		Vec3 right = new Vec3(-facing.z, 0.0, facing.x).normalize();
		Vec3 up = face.getAxis().isHorizontal() ? new Vec3(0.0, 1.0, 0.0) : facing;
		Vec3 middle = rest.add(0.0, player.getBbHeight() * 0.5, 0.0);
		Vec3 onWall = middle.subtract(normal.scale(middle.subtract(wall.anchor()).dot(normal)));
		List<Vec3> points = new java.util.ArrayList<>();
		clearCracks(player, live);
		for (double[] spot : TETHER_SPOTS) {
			Vec3 point = null;
			BlockPos pos = null;
			// where it wants to bite, else nearer in, else the anchor itself
			for (double reach = 1.0; reach >= 0.0 && point == null; reach -= 0.5) {
				Vec3 at = onWall.add(right.scale(spot[0] * reach)).add(up.scale(spot[1] * reach));
				BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(at.add(normal.scale(0.8)), at.subtract(normal.scale(1.2)),
					net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
				if (hit.getType() == HitResult.Type.BLOCK && level.getBlockState(hit.getBlockPos()).getDestroySpeed(level, hit.getBlockPos()) >= 0.0F) {
					point = hit.getLocation();
					pos = hit.getBlockPos();
				}
			}
			if (point == null) {
				point = wall.anchor();
				pos = BlockPos.containing(wall.anchor().subtract(normal.scale(0.1)));
			}
			points.add(point);
			live.wallCracks.add(pos);
			live.wallRestReach[points.size() - 1] = point.distanceTo(middle);
		}
		java.util.Arrays.fill(live.wallStages, 0);
		live.wallRefreshAt = now + 100;
		player.setAttached(WALL, new Wall(wall.anchor(), wall.start(), now, rest, face.get3DDataValue(), List.copyOf(points)));
		player.setAttached(TENDRILS_OUT, now + 20);
		BlockPos center = BlockPos.containing(wall.anchor().subtract(normal.scale(0.1)));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, center, level.getBlockState(center).getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.7F);
		player.resetFallDistance();
	}

	/** Using Tendril Grab while tethered to a wall lets go of it (and does nothing else). */
	private static boolean letGoOfWall(ServerPlayer player) {
		Wall wall = player.getAttached(WALL);
		if (wall == null || wall.landed() < 0L) {
			return false;
		}
		letGo(player, live(player));
		return true;
	}

	/** The tendrils tear loose from the wall. */
	private static void letGo(ServerPlayer player, Live live) {
		clearWall(player, live);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	private static int crackId(ServerPlayer player, int i) {
		return player.getId() * 37 + 1000 + i;
	}

	/** The tendrils let go of the wall, and its cracks fade. */
	private static void clearWall(ServerPlayer player, Live live) {
		clearCracks(player, live);
		player.removeAttached(WALL);
	}

	private static void clearCracks(ServerPlayer player, Live live) {
		for (int i = 0; i < live.wallCracks.size(); i++) {
			player.level().destroyBlockProgress(crackId(player, i), live.wallCracks.get(i), -1);
		}
		live.wallCracks.clear();
		java.util.Arrays.fill(live.wallStages, 0);
	}

	/** Your screen washes with how it feels (see {@link #FIRE_VIGNETTE}). */
	static void vignette(ServerPlayer player, int color) {
		ServerPlayNetworking.send(player, new com.jaymospowers.power.wind.WindNet.ScreenFx(
			com.jaymospowers.power.wind.WindNet.ScreenFx.VIGNETTE, color, VIGNETTE_TICKS));
	}

	/** Fire close by: it shoves you toward the clearest open ground away from it, or straight up if there's none. */
	private static void fleeFire(ServerPlayer player, Live live, long now) {
		BlockPos fire = nearFire(player);
		if (fire == null && !player.isOnFire()) {
			return;
		}
		live.fleeReadyAt = now + FLEE_TICKS;
		ServerLevel level = player.level();
		tendrilsOut(player);
		player.clearFire();
		Vec3 away = fire == null ? player.getLookAngle().reverse() : player.position().subtract(Vec3.atCenterOf(fire));
		away = new Vec3(away.x, 0.0, away.z);
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
		Vec3 best = null;
		double bestScore = -2.0;
		for (int i = 0; i < 16; i++) {
			double a = i / 16.0 * Math.PI * 2.0;
			Vec3 dir = new Vec3(Math.cos(a), 0.0, Math.sin(a));
			double score = dir.dot(away);
			if (score <= bestScore || score < -0.2) {
				continue;
			}
			// room to go, and no fire where it lands
			AABB there = player.getBoundingBox().move(dir.scale(1.6)).move(0.0, 0.1, 0.0);
			BlockPos landing = BlockPos.containing(player.position().add(dir.scale(1.6)));
			if (level.noCollision(player, there) && !level.getBlockState(landing).is(net.minecraft.tags.BlockTags.FIRE)) {
				best = dir;
				bestScore = score;
			}
		}
		Vec3 shove = best == null ? new Vec3(0.0, 0.75, 0.0) : best.scale(INSTINCT_FIRE_SHOVE).add(0.0, 0.4, 0.0);
		ServerPlayNetworking.send(player, new WebNet.Nudge(shove));
		vignette(player, FIRE_VIGNETTE);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.7F, 1.4F);
		level.sendParticles(dust(1.3F), player.getX(), player.getY() + 0.4, player.getZ(), 10, 0.3, 0.2, 0.3, 0.0);
	}

	/** A flint and steel or a fire charge used while bonded: it won't let you, and it panics. */
	private static InteractionResult flint(Player player, net.minecraft.world.level.Level level, InteractionHand hand) {
		return flint(player, hand);
	}

	private static InteractionResult flint(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!(held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE)) || !isBonded(player) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			// let the use reach the server, which refuses it
			return InteractionResult.PASS;
		}
		panic(serverPlayer, hand);
		return InteractionResult.FAIL;
	}

	/** It screams, and does one thing you didn't ask for: drops what's in your hand, lashes out, or bolts. */
	private static void panic(ServerPlayer player, InteractionHand hand) {
		ServerLevel level = player.level();
		WebSymbioteState state = state(player);
		set(player, state.withStress(Math.min(STRESS_MAX, state.stress() + FLINT_STRESS)));
		tendrilsOut(player);
		vignette(player, FIRE_VIGNETTE);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GHAST_SCREAM, SoundSource.PLAYERS, 1.2F, 1.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.8F, 1.8F);
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.7, 0.4, 0.06);
		whisper(player, "flint");
		var random = player.getRandom();
		switch (random.nextInt(3)) {
			case 0 -> {
				// it flings the thing out of your hand
				ItemStack stack = player.getItemInHand(hand);
				player.setItemInHand(hand, ItemStack.EMPTY);
				player.drop(stack, true, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			case 1 -> {
				// it lashes out at whatever's nearest (you, if there's nothing else)
				LivingEntity nearest = null;
				double best = 5.0 * 5.0;
				for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5.0),
					e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
					double d = living.distanceToSqr(player);
					if (d < best) {
						best = d;
						nearest = living;
					}
				}
				if (nearest != null) {
					lash(player, nearest, level.getGameTime(), RETALIATE_LAG);
					nearest.hurtServer(level, level.damageSources().playerAttack(player), FLINT_LASH_DAMAGE);
				} else {
					player.hurtServer(level, level.damageSources().magic(), FLINT_LASH_DAMAGE * 0.5F);
				}
			}
			default -> {
				// it bolts, dragging you with it
				double a = random.nextDouble() * Math.PI * 2.0;
				ServerPlayNetworking.send(player, new WebNet.Nudge(new Vec3(Math.cos(a) * 1.3, 0.45, Math.sin(a) * 1.3)));
			}
		}
	}

	/** A lit creeper or primed TNT close by: where it is. */
	private static @org.jspecify.annotations.Nullable Vec3 explosiveNear(ServerPlayer player) {
		for (Entity e : player.level().getEntities(player, player.getBoundingBox().inflate(INSTINCT_BLAST_RANGE),
			e -> e instanceof net.minecraft.world.entity.item.PrimedTnt
				|| e instanceof net.minecraft.world.entity.monster.Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0))) {
			return e.position();
		}
		return null;
	}

	/** Struck with the armour up: a tendril lashes back (or snatches the attacker from further off). */
	private static void retaliate(ServerPlayer player, LivingEntity attacker) {
		if (!armored(player) || !attacker.isAlive() || player.isCreative() && !player.isAlive()) {
			return;
		}
		Live live = live(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < live.retaliateReadyAt || live.takeoverTicks > 0) {
			return;
		}
		live.retaliateReadyAt = now + RETALIATE_COOLDOWN;
		double distance = attacker.distanceTo(player) - attacker.getBbWidth() * 0.5;
		if (distance <= STRIKE_RANGE + 1.0) {
			// a lash, almost at once
			lash(player, attacker, now, RETALIATE_LAG);
		} else if (distance <= TENDRIL_RANGE && WebTendrils.grabbable(player, attacker)) {
			WebTendrils.snatch(player, attacker);
		} else {
			return;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.9F, 1.3F);
	}

	/**
	 * Hungry, it feeds itself (SEEK): a corpse you carry, else one lying within {@link #SELF_FEED_RANGE}, else, only
	 * when it's starving ({@code hunt}), it takes over to hunt the nearest creature there and kill it first. Returns
	 * true if a feeding began.
	 */
	private static boolean selfFeed(ServerPlayer player, Live live, long now, boolean hunt) {
		Entity carried = KitchenCompat.carriedCorpse(player);
		if (carried != null) {
			// already in your arms: straight to the meal (one it won't touch, and it waits)
			if (!edible(carried)) {
				return false;
			}
			whisper(player, "self_feed");
			startMeal(player, live, now, carried);
			return true;
		}
		Entity corpse = null;
		double best = SELF_FEED_RANGE * SELF_FEED_RANGE;
		for (Entity e : player.level().getEntities(player, player.getBoundingBox().inflate(SELF_FEED_RANGE), e -> freeCorpse(e))) {
			double d = e.distanceToSqr(player);
			if (d < best) {
				best = d;
				corpse = e;
			}
		}
		if (corpse != null) {
			whisper(player, "self_feed");
			return startGrab(player, live, now, corpse);
		}
		if (!hunt) {
			return false;
		}
		LivingEntity prey = null;
		best = SELF_FEED_RANGE * SELF_FEED_RANGE;
		for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SELF_FEED_RANGE),
			e -> e != player && huntable(player, e, live.rampage))) {
			double d = living.distanceToSqr(player);
			if (d < best) {
				best = d;
				prey = living;
			}
		}
		if (prey == null) {
			return false;
		}
		startKill(player, live, now, prey, false);
		return true;
	}

	/** A corpse lying free for the taking: edible, nobody carrying it, no tendril holding it. */
	private static boolean freeCorpse(Entity e) {
		return e.isAlive() && com.jaymospowers.power.gravity.GravityPower.isCorpse(e) && edible(e) && !KitchenCompat.isCarried(e)
			&& !WebTendrils.held(e);
	}

	/** Something it may hunt for food: a creature that leaves a corpse (no pets, no named ones); players only in a rampage. */
	private static boolean huntable(ServerPlayer player, LivingEntity e, boolean rampage) {
		if (!e.isAlive() || e.isSpectator() || e instanceof ArmorStand || !edible(e)) {
			return false;
		}
		if (e instanceof Player p) {
			return rampage && !p.isCreative() && p != player;
		}
		return e instanceof Mob m && !(m instanceof TamableAnimal tame && tame.isTame()) && !m.hasCustomName() && KitchenCompat.leavesCorpse(e);
	}

	/**
	 * KILL: it goes after {@code prey} until it dies (a lash from Consume, {@code manual}; else it takes the wheel and
	 * hunts it). A prey already dead (a kill of its own) only waits for the corpse.
	 */
	private static void startKill(ServerPlayer player, Live live, long now, LivingEntity prey, boolean manual) {
		live.takeoverMeal = !manual;
		live.feedStage = FEED_KILL;
		live.feedTarget = prey.getId();
		live.feedStageAt = now;
		live.feedDeathPos = prey.position();
		live.feedDeadAt = prey.isAlive() ? -1L : now;
		live.feedFinishAt = 0L;
		live.feedFxAt = now;
		if (!prey.isAlive()) {
			return;
		}
		if (manual) {
			// a lash finishes it off
			live.feedFinishAt = now + CONSUME_FINISH_LAG;
			lash(player, prey, now, CONSUME_FINISH_LAG);
			tendrilsOut(player);
			trail(player.level(), player.getEyePosition().add(0.0, -0.3, 0.0), prey.getBoundingBox().getCenter());
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.6F);
		} else {
			// it takes the wheel and hunts it down
			live.rampage = false;
			live.curious = false;
			live.hunting = true;
			startTakeover(player, live, now, prey);
		}
	}

	/** GRAB: a tendril reaches for the corpse and hauls it in. */
	private static boolean startGrab(ServerPlayer player, Live live, long now, Entity corpse) {
		WebTendrils.Grips grips = player.getAttachedOrElse(WebTendrils.GRIPS, WebTendrils.Grips.NONE);
		int hand = grips.right() < 0 ? WebNet.MAIN : grips.left() < 0 ? WebNet.OFF : WebNet.MAIN;
		tendrilsOut(player);
		player.setAttached(TENDRILS_OUT, now + GRAB_TIMEOUT + 20);
		WebTendrils.grab(player, hand, corpse, -1L);
		if (player.getAttachedOrElse(WebTendrils.GRIPS, WebTendrils.Grips.NONE).get(hand) != corpse.getId()) {
			abortFeeding(player, live);
			return false;
		}
		narrate(player, "grab");
		live.feedStage = FEED_GRAB;
		live.feedTarget = corpse.getId();
		live.feedHand = hand;
		live.feedStageAt = now;
		live.feedFxAt = Math.min(live.feedFxAt, now);
		trail(player.level(), player.getEyePosition().add(0.0, -0.3, 0.0), corpse.getBoundingBox().getCenter());
		return true;
	}

	/** FEED: carried, so it eats (by the corpse's size). */
	private static void startMeal(ServerPlayer player, Live live, long now, Entity corpse) {
		float size = Mth.clamp(corpse.getBbWidth() * corpse.getBbWidth() * corpse.getBbHeight() / 2.0F, 0.0F, 1.0F);
		startEating(player, corpse, Mth.lerp(size, CORPSE_HEAL_MIN, CORPSE_HEAL_MAX), Math.round(Mth.lerp(size, CONSUME_FEED, CORPSE_FEED_MAX)));
		narrate(player, "feed");
		if (live.takeoverMeal) {
			// fed on its own kill: maybe it gets you out of here, during the meal or just after
			live.escapeAt = now + ESCAPE_LAG_MIN + player.getRandom().nextInt(ESCAPE_LAG_MAX - ESCAPE_LAG_MIN + 1);
			live.takeoverMeal = false;
		}
		live.feedStage = FEED_FEED;
		live.feedTarget = corpse.getId();
		live.feedStageAt = now;
		live.feedFxAt = Math.min(live.feedFxAt, now);
	}

	/**
	 * After a takeover's meal: in a bad spot (or {@link #ESCAPE_CHANCE} of the time anyway) it coils and leaps clear,
	 * the way with the fewest about and open ground ahead (the client does the coil and the leap).
	 */
	private static void escape(ServerPlayer player, Live live) {
		ServerLevel level = player.level();
		if (!player.isAlive() || sulking(player) || live.fleeing || live.takeoverTicks > 0 || player.isInWater() || player.isPassenger()
			|| player.getAbilities().flying || player.getAttached(WALL) != null) {
			return;
		}
		Entity carried = KitchenCompat.carriedCorpse(player);
		List<LivingEntity> about = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ESCAPE_RANGE),
			e -> e != player && e != carried && e.isAlive() && !e.isSpectator());
		boolean village = level.isVillage(player.blockPosition());
		boolean cornered = village || about.size() >= ESCAPE_CROWD;
		if (!cornered && player.getRandom().nextFloat() >= ESCAPE_CHANCE) {
			return;
		}
		// the way out: away from them (the near ones count most), along open ground
		Vec3 best = Vec3.ZERO;
		double bestScore = Double.NEGATIVE_INFINITY;
		float spin = player.getRandom().nextFloat() * Mth.TWO_PI;
		for (int i = 0; i < ESCAPE_DIRECTIONS; i++) {
			float a = spin + i * Mth.TWO_PI / ESCAPE_DIRECTIONS;
			Vec3 dir = new Vec3(Mth.cos(a), 0.0, Mth.sin(a));
			if (!level.noCollision(player, player.getBoundingBox().move(dir.scale(2.0)).move(0.0, 1.0, 0.0))
				|| !level.noCollision(player, player.getBoundingBox().move(dir.scale(4.0)).move(0.0, 2.0, 0.0))) {
				continue;
			}
			double score = 0.0;
			for (LivingEntity e : about) {
				Vec3 to = e.position().subtract(player.position());
				double d = Math.max(1.0, to.horizontalDistance());
				score -= dir.dot(new Vec3(to.x, 0.0, to.z).scale(1.0 / d)) / d;
			}
			if (score > bestScore) {
				bestScore = score;
				best = dir;
			}
		}
		narrate(player, "escape");
		dread(player, WebLeap.CHARGE_FULL + 30, true, true);
		ServerPlayNetworking.send(player, new WebNet.Escape((float)best.x, (float)best.z));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/** The feeding, a tick at a time: kill, grab, carry, feed. The dread stays on your screen throughout. */
	private static void tickFeeding(ServerPlayer player, Live live, long now) {
		ServerLevel level = player.level();
		if (sulking(player) || live.fleeing) {
			abortFeeding(player, live);
			return;
		}
		if (now >= live.feedFxAt) {
			live.feedFxAt = now + FEED_FX_EVERY;
			dread(player, FEED_FX_TICKS, true);
		}
		if (!armored(player) && player.tickCount % 10 == 0 && live.feedStage != FEED_FEED) {
			player.setAttached(TENDRILS_OUT, now + 30);
		}
		Entity target = level.getEntity(live.feedTarget);
		long t = now - live.feedStageAt;
		switch (live.feedStage) {
			case FEED_KILL -> {
				if (target instanceof LivingEntity prey && prey.isAlive()) {
					live.feedDeathPos = prey.position();
					if (live.feedFinishAt > 0L) {
						// Consume's lash lands
						if (now >= live.feedFinishAt) {
							live.feedFinishAt = 0L;
							if (prey.distanceTo(player) > CONSUME_RANGE + 2.0 + prey.getBbWidth() * 0.5) {
								abortFeeding(player, live);
								return;
							}
							prey.hurtServer(level, level.damageSources().playerAttack(player), CONSUME_FINISH_DAMAGE);
							level.playSound(null, prey.getX(), prey.getY(), prey.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.5F);
							level.sendParticles(dust(1.4F), prey.getX(), prey.getY() + prey.getBbHeight() * 0.6, prey.getZ(), 14, 0.3, 0.3, 0.3, 0.0);
						}
					} else if (live.takeoverTicks <= 0) {
						// it got away (the hunt's over)
						abortFeeding(player, live);
					}
					return;
				}
				if (live.feedDeadAt < 0L) {
					live.feedDeadAt = now;
				}
				// dead: its corpse turns up where it fell (Jaymo's Kitchen leaves one once it has finished dying)
				Entity corpse = null;
				double best = CORPSE_FIND_RADIUS * CORPSE_FIND_RADIUS;
				long since = now - live.feedDeadAt;
				for (Entity e : level.getEntities(player, AABB.ofSize(live.feedDeathPos, CORPSE_FIND_RADIUS * 2.0, CORPSE_FIND_RADIUS * 2.0,
					CORPSE_FIND_RADIUS * 2.0), e -> freeCorpse(e) && e.tickCount <= since + 40)) {
					double d = e.position().distanceToSqr(live.feedDeathPos);
					if (d < best) {
						best = d;
						corpse = e;
					}
				}
				if (corpse != null) {
					// (a hunt keeps the wheel through the grab, the carry and the meal: see holdForMeal)
					startGrab(player, live, now, corpse);
				} else if (since > CORPSE_WAIT_TICKS) {
					abortFeeding(player, live);
				}
			}
			case FEED_GRAB -> {
				WebTendrils.Grips grips = player.getAttachedOrElse(WebTendrils.GRIPS, WebTendrils.Grips.NONE);
				if (target == null || !target.isAlive() || grips.get(live.feedHand) != target.getId()) {
					// taken from it, or gone
					abortFeeding(player, live);
					return;
				}
				long reach = (long)Math.ceil(target.distanceTo(player) / WebTendrils.TENDRIL_SPEED);
				if (t >= Math.min(GRAB_TIMEOUT, reach + GRAB_HAUL_TICKS) && t >= GRAB_HAUL_TICKS || t >= GRAB_TIMEOUT) {
					// hauled in: into your arms
					WebTendrils.release(player, live.feedHand, false);
					live.feedStage = FEED_CARRY;
					live.feedStageAt = now;
					KitchenCompat.carry(player, target);
				}
			}
			case FEED_CARRY -> {
				if (target == null || !target.isAlive()) {
					abortFeeding(player, live);
					return;
				}
				// it eats only once the corpse is carried
				if (KitchenCompat.carriedCorpse(player) == target) {
					startMeal(player, live, now, target);
				} else if (t > CARRY_TIMEOUT) {
					abortFeeding(player, live);
				} else {
					KitchenCompat.carry(player, target);
				}
			}
			case FEED_FEED -> {
				if (player.getAttached(EATING) == null) {
					live.feedStage = FEED_NONE;
					live.feedTarget = -1;
				}
			}
			default -> live.feedStage = FEED_NONE;
		}
	}

	/** The feeding stops where it is (the hunt ends, a grabbed corpse is let go). */
	private static void abortFeeding(ServerPlayer player, Live live) {
		live.takeoverMeal = false;
		if (live.feedStage == FEED_NONE) {
			return;
		}
		if (live.feedStage == FEED_GRAB) {
			WebTendrils.Grips grips = player.getAttachedOrElse(WebTendrils.GRIPS, WebTendrils.Grips.NONE);
			if (grips.get(live.feedHand) == live.feedTarget) {
				WebTendrils.release(player, live.feedHand, false);
			}
		}
		if (live.hunting && live.takeoverTicks > 0) {
			endTakeover(player, live);
		}
		live.hunting = false;
		live.feedStage = FEED_NONE;
		live.feedTarget = -1;
		live.feedFinishAt = 0L;
	}

	/** Where the feeding is (for debug output). */
	public static String describeFeeding(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		return live == null ? "none" : "stage=" + live.feedStage + " target=" + live.feedTarget;
	}

	/** Out of the armour, the tendrils burst out to act (and withdraw after). */
	private static void tendrilsOut(ServerPlayer player) {
		if (!armored(player)) {
			player.setAttached(TENDRILS_OUT, player.level().getGameTime() + TENDRILS_OUT_TICKS);
		}
	}

	/** Your hunger ran out: it turns on you, tears the suit away, and won't come back until it's calmed (the bond decides how long). */
	private static WebSymbioteState sulk(ServerPlayer player, WebSymbioteState state, long now) {
		float strain = (1.0F + SULK_PER_REJECTION * state.rejections()) * (0.5F + state.stress() / (float)STRESS_MAX);
		int ticks = Math.round(SULK_TICKS * strain);
		player.setAttached(SULK, now + ticks);
		removeArmor(player);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ANGRY, SoundSource.PLAYERS, 1.0F, 1.3F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
		whisper(player, "sulk");
		return state.withFlag(WebSymbioteState.SUIT, false).withFlag(WebSymbioteState.ARMOR, false).withFlag(WebSymbioteState.BULK, false)
			.withStress(Math.min(STRESS_MAX, state.stress() + 10));
	}

	/** Fire or lava touching or close beside you. */
	private static @org.jspecify.annotations.Nullable BlockPos nearFire(ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(2.5, 1.0, 2.5);
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			net.minecraft.world.level.block.state.BlockState block = player.level().getBlockState(pos);
			if (block.is(net.minecraft.tags.BlockTags.FIRE) || block.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
				return pos.immutable();
			}
		}
		return null;
	}

	private static boolean groundWithin(ServerPlayer player, double height) {
		Vec3 feet = player.position();
		return player.level().clip(new net.minecraft.world.level.ClipContext(feet, feet.subtract(0.0, height, 0.0),
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, player)).getType()
			!= HitResult.Type.MISS;
	}

	/** The tendrils, on their own: now and then they find something close and strike it, slowly and hard. */
	private static void tickStrikes(ServerPlayer player, Live live, long now) {
		Strike strike = player.getAttached(STRIKE);
		ServerLevel level = player.level();
		if (strike != null) {
			long t = now - strike.start();
			Entity target = level.getEntity(strike.target());
			double range = strike.heavy() ? HEAVY_STRIKE_RANGE : STRIKE_RANGE;
			if (t == strike.hitAt() && !WebCounter.ownsStrike(player) && target instanceof LivingEntity living && living.isAlive()
				&& living.distanceTo(player) <= range + 1.5 + living.getBbWidth() * 0.5) {
				land(player, strike, living);
			}
			if (t >= strike.ticks() || target == null) {
				player.removeAttached(STRIKE);
				live.nextStrikeAt = now + (strike.heavy() ? HEAVY_STRIKE_EVERY : strike.fast() ? FRENZY_EVERY : STRIKE_EVERY);
			}
			return;
		}
		// a takeover's fight (not a look, not a run): the tendrils tear into what it took you for
		boolean fighting = live.takeoverTicks > 0 && live.takeoverTarget != player.getId() && !live.curious && !live.touring && !live.fleeing;
		if (now < live.nextStrikeAt || live.takeoverTicks > 0 && !fighting || live.fleeing
			|| WebTendrilCombos.active(player)) {
			return;
		}
		live.nextStrikeAt = now + (fighting ? 2 : 10);
		boolean heavy = heldDebris(player) != null;
		double range = heavy ? HEAVY_STRIKE_RANGE : STRIKE_RANGE;
		LivingEntity target = null;
		// what it's hunting (or fighting) comes first
		if (live.feedStage == FEED_KILL && level.getEntity(live.feedTarget) instanceof LivingEntity prey && prey.isAlive()
			&& prey.distanceTo(player) <= range) {
			target = prey;
		}
		if (target == null && fighting && level.getEntity(live.takeoverTarget) instanceof LivingEntity prey && prey.isAlive()
			&& prey.distanceTo(player) <= range) {
			target = prey;
		}
		double best = range * range;
		if (target == null) {
			for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
				e -> e != player && e.isAlive() && !e.isSpectator() && (e instanceof net.minecraft.world.entity.monster.Enemy
					|| live.rampage && (e instanceof Mob || e instanceof Player p && !p.isCreative())))) {
				double d = living.distanceToSqr(player);
				if (d < best) {
					best = d;
					target = living;
				}
			}
		}
		if (target != null && fighting && !heavy && player.getRandom().nextFloat() < FRENZY_SNATCH && WebTendrils.freeHand(player) >= 0
			&& WebTendrils.grabbable(player, target) && target.distanceTo(player) > 1.5) {
			// a tendril whips out, snatches it, and hurls it
			WebTendrils.snatch(player, target);
			live.nextStrikeAt = now + WebTendrils.INSTINCT_HOLD_TICKS + FRENZY_EVERY;
			narrate(player, "snatch");
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.5F);
			return;
		}
		if (target != null) {
			boolean fast = fighting && !heavy;
			// a frenzy runs through every way of striking in turn: slam, left, right, at random
			int variant = fast ? (live.frenzyVariant = (live.frenzyVariant + 1 + player.getRandom().nextInt(2)) % STRIKE_VARIANTS)
				: player.getRandom().nextInt(STRIKE_VARIANTS);
			player.setAttached(STRIKE, new Strike(target.getId(), now, heavy, variant, fast));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F,
				heavy ? 0.4F : 0.6F);
			if (heavy) {
				// the weight of it, hauled up
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.4F);
			}
		}
	}

	/** A quick lash (instinct, retaliation, panic, Consume's finishing blow): it lands {@code lag} ticks from now. */
	private static void lash(ServerPlayer player, Entity target, long now, int lag) {
		player.setAttached(STRIKE, new Strike(target.getId(), now - (STRIKE_HIT - lag), false, player.getRandom().nextInt(STRIKE_VARIANTS), false));
	}

	/** A strike lands: a slam drives down, a swipe flings sideways; with debris, far harder, with a thud and a shake. */
	private static void land(ServerPlayer player, Strike strike, LivingEntity living) {
		ServerLevel level = player.level();
		Entity debris = strike.heavy() ? heldDebris(player) : null;
		boolean heavy = strike.heavy();
		float damage = strike.fast() ? FRENZY_DAMAGE : STRIKE_DAMAGE;
		narrate(player, heavy ? "smash" : "strike");
		if (heavy) {
			int pieces = debris instanceof com.jaymospowers.core.BlockCluster chunk ? chunk.pieces().size() : 1;
			damage = HEAVY_STRIKE_DAMAGE * Mth.clamp(HEAVY_SIZE_BASE + HEAVY_SIZE_PER_PIECE * pieces, HEAVY_SIZE_MIN, HEAVY_SIZE_MAX);
		}
		living.hurtServer(level, level.damageSources().playerAttack(player), damage);
		Vec3 away = living.position().subtract(player.position()).horizontal();
		if (away.lengthSqr() < 1.0E-4) {
			away = player.getLookAngle().horizontal();
		}
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
		Vec3 push;
		if (strike.variant() == STRIKE_SLAM) {
			double up = living.onGround() ? heavy ? HEAVY_SLAM_LIFT : SLAM_LIFT : SLAM_DOWN;
			push = away.scale(heavy ? HEAVY_SLAM_KNOCK : SLAM_PUSH).add(0.0, up, 0.0);
		} else {
			// along the sweep: from your left across to your right, or back
			Vec3 right = new Vec3(-away.z, 0.0, away.x);
			Vec3 sweep = strike.variant() == STRIKE_SWIPE_LEFT ? right : right.reverse();
			Vec3 dir = sweep.add(away.scale(SWIPE_AWAY)).normalize();
			push = heavy ? dir.scale(HEAVY_KNOCK).add(0.0, HEAVY_LIFT, 0.0) : dir.scale(SWIPE_PUSH).add(0.0, SWIPE_LIFT, 0.0);
		}
		living.setDeltaMovement(living.getDeltaMovement().add(push));
		living.needsSync = true;
		living.syncVelocity = true;
		if (living instanceof ServerPlayer other) {
			ServerPlayNetworking.send(other, new WebNet.Nudge(living.getDeltaMovement()));
		}
		level.sendParticles(dust(1.4F), living.getX(), living.getY() + living.getBbHeight() * 0.6, living.getZ(), 14, 0.3, 0.3, 0.3, 0.0);
		level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 1.0F, 0.5F);
		if (heavy) {
			level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.3F, 0.6F);
			level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.5F);
			net.minecraft.world.level.block.state.BlockState chunkState = debris instanceof com.jaymospowers.core.BlockCluster chunk
				&& !chunk.pieces().isEmpty() ? chunk.pieces().get(0).blockState()
				: debris instanceof net.minecraft.world.entity.item.FallingBlockEntity falling ? falling.getBlockState() : null;
			if (chunkState != null) {
				com.jaymospowers.core.Debris.puff(level, living.blockPosition().above(), chunkState, 1.6F);
			}
			com.jaymospowers.core.Shakes.send(player, HEAVY_SHAKE, living.getBoundingBox().getCenter());
			if (living instanceof ServerPlayer other) {
				com.jaymospowers.core.Shakes.send(other, HEAVY_TARGET_SHAKE, player.position());
			}
		}
	}

	/** What a tendril holds that's debris (a torn-out chunk or a falling block), if any. */
	static @org.jspecify.annotations.Nullable Entity heldDebris(Player player) {
		WebTendrils.Grips grips = player.getAttached(WebTendrils.GRIPS);
		if (grips == null) {
			return null;
		}
		for (int hand = WebNet.MAIN; hand <= WebNet.OFF; hand++) {
			Entity held = grips.get(hand) < 0 ? null : player.level().getEntity(grips.get(hand));
			if (held instanceof com.jaymospowers.core.BlockCluster || held instanceof net.minecraft.world.entity.item.FallingBlockEntity) {
				return held;
			}
		}
		return null;
	}

	/**
	 * Where held debris goes during a heavy strike (null: back to the usual hold): reared high above you for a slam,
	 * or out to the side a swipe comes from, then brought down or across onto the target as the strike lands.
	 */
	static @org.jspecify.annotations.Nullable Vec3 debrisStrikePoint(ServerPlayer player, Entity held, long now) {
		Strike strike = player.getAttached(STRIKE);
		if (strike == null || !strike.heavy() || heldDebris(player) != held) {
			return null;
		}
		Entity target = player.level().getEntity(strike.target());
		if (target == null) {
			return null;
		}
		float p = (now - strike.start()) / (float)strike.ticks();
		float hit = strike.hitShare();
		Vec3 aim = target.getBoundingBox().getCenter();
		if (p >= hit + 0.08F) {
			return null;
		}
		if (p >= hit) {
			return aim;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 dir = aim.subtract(eye).horizontal();
		dir = dir.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : dir;
		dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
		Vec3 right = new Vec3(-dir.z, 0.0, dir.x);
		Vec3 rear = strike.variant() == STRIKE_SLAM ? eye.add(0.0, HEAVY_REAR_UP, 0.0).add(dir.scale(-0.3))
			: eye.add(right.scale(strike.variant() == STRIKE_SWIPE_LEFT ? -HEAVY_REAR_SIDE : HEAVY_REAR_SIDE)).add(dir.scale(0.8)).add(0.0, 0.4, 0.0);
		float rearShare = strike.rearShare();
		if (p < rearShare) {
			return rear;
		}
		float k = Mth.clamp((p - rearShare) / (hit - rearShare), 0.0F, 1.0F);
		if (strike.variant() == STRIKE_SLAM) {
			// over the top, then straight down onto it
			Vec3 above = aim.add(0.0, HEAVY_REAR_UP, 0.0);
			return k < 0.5F ? rear.lerp(above, k * 2.0F) : above.lerp(aim, (k - 0.5F) * 2.0F);
		}
		return rear.lerp(aim, k * k);
	}

	/**
	 * Consume: nothing is eaten alive. A corpse you carry is eaten; else a corpse within {@link #CONSUME_CORPSE_RANGE}
	 * (the one you look at, else the nearest) is grabbed, carried and eaten; else something within {@link #CONSUME_RANGE}
	 * at or under {@link #CONSUME_MAX_HEALTH} is finished off by a lash, and its corpse grabbed, carried and eaten.
	 */
	private static boolean consume(ServerPlayer player) {
		ServerLevel level = player.level();
		Live live = live(player);
		long now = level.getGameTime();
		if (player.getAttached(EATING) != null || live.feedStage != FEED_NONE) {
			return false;
		}
		Entity carried = KitchenCompat.carriedCorpse(player);
		if (carried != null && edible(carried)) {
			startMeal(player, live, now, carried);
			return true;
		}
		if (carried != null) {
			// it recoils from that one
			whisper(player, "inedible");
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.6F, 0.5F);
			return false;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Entity corpse = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(CONSUME_CORPSE_RANGE), WebSymbiote::freeCorpse)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > CONSUME_CORPSE_RANGE + e.getBbWidth() * 0.5) {
				continue;
			}
			double score = distance - 2.0 * Math.max(0.0, to.normalize().dot(look));
			if (score < bestScore) {
				bestScore = score;
				corpse = e;
			}
		}
		if (corpse != null) {
			return startGrab(player, live, now, corpse);
		}
		LivingEntity best = null;
		bestScore = Double.MAX_VALUE;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CONSUME_RANGE),
			e -> e != player && !(e instanceof Player) && huntable(player, e, false) && e.getHealth() <= CONSUME_MAX_HEALTH)) {
			Vec3 to = living.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > CONSUME_RANGE + living.getBbWidth() * 0.5) {
				continue;
			}
			// favour what you're looking at
			double score = distance - 2.0 * Math.max(0.0, to.normalize().dot(look));
			if (score < bestScore) {
				bestScore = score;
				best = living;
			}
		}
		if (best == null) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.5F, 1.4F);
			return false;
		}
		startKill(player, live, now, best, true);
		return true;
	}

	/** A tendril takes it and brings it to the maw (see {@link #EAT_REACH}). */
	private static void startEating(ServerPlayer player, Entity food, float heal, int feed) {
		Live live = live(player);
		long now = player.level().getGameTime();
		float size = Mth.clamp(food.getBbWidth() * food.getBbWidth() * food.getBbHeight() / 2.0F, 0.0F, 1.0F);
		live.eatTarget = food.getId();
		live.eatBig = size >= BIG_MEAL;
		live.eatFrom = food.position();
		live.eatHeal = heal;
		live.eatFeed = feed;
		int reach = reachTicks(food.distanceTo(player));
		player.setAttached(EATING, new Meal(now, live.eatBig, reach));
		// the tendrils out for the carry and the meal
		player.setAttached(TENDRILS_OUT, now + reach + 20);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.5F, 1.6F);
	}

	private static void tickEating(ServerPlayer player, Live live, long now) {
		ServerLevel level = player.level();
		Meal meal = player.getAttached(EATING);
		long t = now - meal.start();
		if (t >= mealTicks(meal.big(), meal.reach())) {
			// chewed, and the head settles back
			player.removeAttached(EATING);
			live.eatTarget = -1;
			return;
		}
		if (live.eatTarget < 0) {
			// swallowed: chewing (a crunch now and then)
			if (t % 9 == 0 && t < mealTicks(meal.big(), meal.reach()) - EAT_SHRINK) {
				level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8F,
					0.5F + player.getRandom().nextFloat() * 0.2F);
			}
			return;
		}
		Entity food = level.getEntity(live.eatTarget);
		if (food == null || !food.isAlive() || food.distanceTo(player) > SELF_FEED_RANGE + 4.0) {
			// it got away (or was taken): the maw just closes
			live.eatTarget = -1;
			player.removeAttached(EATING);
			return;
		}
		// it only ever eats what you carry: dropped (or taken), the maw just closes
		if (food != KitchenCompat.carriedCorpse(player)) {
			live.eatTarget = -1;
			player.removeAttached(EATING);
			return;
		}
		if (t % 3 == 0) {
			KitchenCompat.jolt(food);
			if (food instanceof LivingEntity living) {
				living.hurtTime = 6;
				level.broadcastEntityEvent(living, (byte)2);
			}
		}
		Vec3 at = food.getBoundingBox().getCenter();
		DustParticleOptions blood = new DustParticleOptions(0x7A0A0A, 1.4F);
		if (meal.big() && t == biteAt(0, meal.reach())) {
			// bitten clean in half
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 1.4F, 0.35F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.5F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.4F);
			level.sendParticles(blood, at.x, at.y, at.z, 60, food.getBbWidth() * 0.5, food.getBbHeight() * 0.5, food.getBbWidth() * 0.5, 0.0);
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.2);
			level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 12, food.getBbWidth() * 0.4, food.getBbHeight() * 0.3, food.getBbWidth() * 0.4, 0.06);
			return;
		}
		if (t < biteAt(meal.big() ? 1 : 0, meal.reach())) {
			return;
		}
		// the last bite: gone
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 1.2F, 0.45F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1.3F, 0.4F);
		level.sendParticles(blood, at.x, at.y, at.z, meal.big() ? 30 : 14, food.getBbWidth() * 0.4, food.getBbHeight() * 0.4, food.getBbWidth() * 0.4, 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 20, food.getBbWidth() * 0.4, food.getBbHeight() * 0.4, food.getBbWidth() * 0.4, 0.05);
		food.discard();
		player.heal(live.eatHeal);
		feed(player, live.eatFeed, CONSUME_CALM);
		// fed, it warms to you
		addBond(player, BOND_MEAL);
		live.craving = false;
		// and you're fed too: about a hunger point per half-heart it healed
		player.getFoodData().eat(Math.max(2, Math.round(live.eatHeal * CONSUME_HUNGER_PER_HEAL)), CONSUME_SATURATION);
		live.eatTarget = -1;
	}

	/** Feeds the symbiote (and calms it). */
	private static void feed(ServerPlayer player, int satiety, int calm) {
		WebSymbioteState state = state(player);
		set(player, state.withSatiety(Math.min(SATIETY_MAX, state.satiety() + satiety)).withStress(Math.max(0, state.stress() - calm)));
	}

	/**
	 * The roar begins (see {@link #ROAR_TICKS}): you're held hunched while it gathers (everyone sees the pose), off any
	 * wall you were on. {@code defend}: it's taking over to save you, so the armour pieces itself together as it goes,
	 * and it runs with you after (from {@code threat}).
	 */
	private static boolean startRoar(ServerPlayer player, Live live, long now, boolean defend, @org.jspecify.annotations.Nullable LivingEntity threat) {
		ServerLevel level = player.level();
		if (player.getAttached(WALL) != null) {
			letGo(player, live);
		}
		live.roarAt = now;
		live.roarDefend = defend;
		live.roarThreat = threat == null ? -1 : threat.getId();
		WebPower.pose(player, WebPose.ROAR, 1);
		if (defend) {
			// it has you: your screen goes dark under it, and it shrugs off the worst while it swells
			dread(player, ROAR_TICKS + 20, true);
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ROAR_TICKS, ROAR_RESISTANCE, false, false));
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	/** The roar, a tick at a time: the armour building (near death), the tendrils flailing, and at its height, the roar. */
	private static void tickRoar(ServerPlayer player, Live live, long now) {
		ServerLevel level = player.level();
		long age = now - live.roarAt;
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.55, 0.0);
		if (age >= ROAR_TENDRILS_AT && age < ROAR_BLAST_AT + 8) {
			// the tendrils burst out and thrash (over whatever it has built so far)
			player.setAttached(TENDRILS_OUT, now + 10);
		}
		if (age == ROAR_SUIT_AT && live.roarDefend) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.4F, 0.5F);
		} else if (age == ROAR_TENDRILS_AT) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.4F, 0.5F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.4F);
		} else if (age == ROAR_BULK_AT && live.roarDefend) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.2F, 0.5F);
		} else if (age == ROAR_BLAST_AT) {
			roarBlast(player);
		}
		if (age < ROAR_BLAST_AT && age % 2 == 0) {
			// black pouring over you, spiralling up as it builds
			double angle = age * 0.8;
			double y = player.getY() + (age / (double)ROAR_BLAST_AT) * player.getBbHeight();
			for (int k = 0; k < 3; k++) {
				double a = angle + k * Math.PI * 2.0 / 3.0;
				level.sendParticles(dust(1.5F), player.getX() + Math.cos(a) * 0.5, y, player.getZ() + Math.sin(a) * 0.5, 2, 0.05, 0.08, 0.05, 0.0);
			}
			if (age % 6 == 0) {
				level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 4, 0.35, 0.5, 0.35, 0.02);
			}
		}
		if (age >= ROAR_TICKS) {
			endRoar(player, live, now);
		}
	}

	/** At the roar's height: its head thrown back, a roar that hurts and hurls everything near and shakes every screen about. */
	private static void roarBlast(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.55, 0.0);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ROAR_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator())) {
			Vec3 to = living.getBoundingBox().getCenter().subtract(center);
			if (to.length() > ROAR_RADIUS + living.getBbWidth() * 0.5) {
				continue;
			}
			living.hurtServer(level, level.damageSources().playerAttack(player), ROAR_DAMAGE);
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
			// the nearer, the harder it throws them
			double force = ROAR_KNOCKBACK * (1.2 - 0.5 * Math.min(1.0, to.length() / ROAR_RADIUS));
			Vec3 push = flat.scale(force).add(0.0, 0.5, 0.0);
			living.setDeltaMovement(living.getDeltaMovement().add(push));
			living.needsSync = true;
			living.syncVelocity = true;
			if (living instanceof ServerPlayer other) {
				ServerPlayNetworking.send(other, new WebNet.Nudge(push));
			}
		}
		// the roar itself: a ring of shock rolling out, and black spray
		Vec3 look = player.getLookAngle();
		level.sendParticles(ParticleTypes.SONIC_BOOM, center.x + look.x * 0.8, center.y + 0.4, center.z + look.z * 0.8, 1, 0.0, 0.0, 0.0, 0.0);
		for (int ring = 1; ring <= 3; ring++) {
			double r = ring * ROAR_RADIUS / 3.0;
			int count = 10 + ring * 6;
			for (int k = 0; k < count; k++) {
				double a = k * Math.PI * 2.0 / count;
				level.sendParticles(ring == 3 ? sheen(1.3F) : dust(1.7F - ring * 0.25F), center.x + Math.cos(a) * r, center.y - 0.4, center.z + Math.sin(a) * r,
					1, 0.0, 0.05, 0.0, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y + 0.4, center.z, 40, 0.5, 0.5, 0.5, 0.3);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 2.0F, 0.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.6F, 0.55F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F, 0.7F);
		// every screen near it shakes, yours most
		com.jaymospowers.core.Shakes.send(player, ROAR_SHAKE, null);
		for (ServerPlayer other : level.players()) {
			if (other != player && other.distanceTo(player) < ROAR_RADIUS * 2.5) {
				com.jaymospowers.core.Shakes.send(other, ROAR_SHAKE * 0.5F, player.position());
			}
		}
	}

	/** The roar is over: near death, it wraps you in the Shroud and runs for it with you (climbing or tearing through walls). */
	private static void endRoar(ServerPlayer player, Live live, long now) {
		boolean defend = live.roarDefend;
		live.roarAt = 0L;
		live.roarDefend = false;
		if (!defend) {
			return;
		}
		LivingEntity threat = player.level().getEntity(live.roarThreat) instanceof LivingEntity living && living.isAlive() ? living : threat(player);
		live.roarThreat = -1;
		if (threat == null || sulking(player)) {
			// nothing left to run from
			live.fleeing = false;
			return;
		}
		shroud(player);
		startTakeover(player, live, now, threat);
		live.takeoverTicks = RUN_TICKS;
		ServerPlayNetworking.send(player, new WebNet.Takeover(threat.getId(), RUN_TICKS, true));
		Vec3 away = player.position().subtract(threat.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal().reverse() : away;
		ServerPlayNetworking.send(player, new WebNet.Nudge(away.normalize().scale(LEAP_SPEED).add(0.0, LEAP_UP, 0.0)));
	}

	/** Roaring right now (held in the pose, the tendrils flailing). Both sides: the pose is synced. */
	public static boolean roaring(Player player) {
		WebPose pose = player.getAttached(WebPower.POSE);
		return pose != null && pose.kind() == WebPose.ROAR && player.level().getGameTime() - pose.start() < pose.ticks();
	}

	/** Spike Burst: black spikes burst out all around, hurting and hurling everything near. */
	private static boolean spikes(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 center = player.position().add(0.0, 1.0, 0.0);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SPIKE_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator())) {
			Vec3 to = living.getBoundingBox().getCenter().subtract(center);
			if (to.length() > SPIKE_RADIUS + living.getBbWidth() * 0.5) {
				continue;
			}
			living.hurtServer(level, level.damageSources().playerAttack(player), SPIKE_DAMAGE);
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
			Vec3 push = flat.scale(SPIKE_KNOCKBACK).add(0.0, 0.45, 0.0);
			living.setDeltaMovement(living.getDeltaMovement().add(push));
			living.needsSync = true;
			living.syncVelocity = true;
			if (living instanceof ServerPlayer other) {
				ServerPlayNetworking.send(other, new WebNet.Nudge(push));
			}
		}
		// the spikes: rays of black out from the body, in a ring
		for (int ray = 0; ray < 24; ray++) {
			double angle = ray * Math.PI * 2.0 / 24.0;
			double tilt = (ray % 3 - 1) * 0.35;
			Vec3 dir = new Vec3(Math.cos(angle), tilt, Math.sin(angle));
			for (int k = 1; k <= 5; k++) {
				Vec3 at = center.add(dir.scale(k * SPIKE_RADIUS / 5.0));
				level.sendParticles(k == 5 ? sheen(1.2F) : dust(1.6F - k * 0.2F), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 30, 0.5, 0.6, 0.5, 0.25);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_DEATH, SoundSource.PLAYERS, 0.8F, 0.5F);
		return true;
	}

	/** Shroud: the suit swallows the light; you vanish until you strike, or until it tires. */
	private static boolean shroud(ServerPlayer player) {
		ServerLevel level = player.level();
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, SHROUD_TICKS, 0, false, false));
		live(player).shroudUntil = level.getGameTime() + SHROUD_TICKS;
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.35, 0.8, 0.35, 0.03);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.8, player.getZ(), 10, 0.35, 0.6, 0.35, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	private static void endShroud(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live == null || live.shroudUntil <= 0) {
			return;
		}
		live.shroudUntil = 0;
		player.removeEffect(MobEffects.INVISIBILITY);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 15, 0.3, 0.7, 0.3, 0.03);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.6F, 0.6F);
	}

	/** Who's holding a symbiote's attention, for debug output. */
	public static String describe(Player player) {
		WebSymbioteState s = state(player);
		return "bonded=" + s.bonded() + " bond=" + s.bond() + " satiety=" + s.satiety() + " stress=" + s.stress() + " rejections=" + s.rejections() + " flags=" + s.flags()
			+ (player instanceof ServerPlayer sp ? " feeding[" + describeFeeding(sp) + "]" : "");
	}
}
