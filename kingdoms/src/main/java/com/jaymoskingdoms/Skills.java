package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import com.jaymoskingdoms.money.Money;

/**
 * (0.5.52) The skill tree: three pillars, Survival, Stealth and Strength. Each pillar has its own experience, earned by
 * doing what it's about, and its own levels; every level is a point to spend on that pillar's perks (the {@code K} key
 * opens the tree). Practice counts on its own too: each level of a pillar makes you a little better at its work before
 * any point is spent.
 *
 * <p>Stealth grows with every pocket picked clean ({@link Pickpocket}) and every theft nobody saw ({@link Theft}); each
 * level shortens how long your hand must stay in a pocket, and its perks make you lighter fingered, sneakier and harder
 * to pin. Survival ({@link SurvivalSkill}) grows as you live off the land and through the night; Strength
 * ({@link StrengthSkill}) as you fight, mine and fell. The plan: {@code docs/Skills-Plan.md}.
 *
 * <p>Points come back for reinvesting ({@link #respec}) from a kingdom's court wizard, for silver, or by drinking a
 * bottle o' enchanting (sneak and use it). A pillar you've made your own shows over your head as your alignment, and
 * word of it gets round the villages ({@link Renown}).
 */
public final class Skills {
	/**
	 * A level's experience: from level {@code l} to the next takes {@code XP_BASE + XP_STEP * l}; the highest level (Strength
	 * goes further, {@link #STRENGTH_MAX_LEVEL}, for its Combat Arts, and (0.7.102) Stealth, {@link #STEALTH_MAX_LEVEL},
	 * for freerunning).
	 */
	// (0.7.464) doubled (the pack-wide training pass, from 30/15): saved experience is doubled once to match ({@link #PACE})
	public static final int XP_BASE = 60, XP_STEP = 30, MAX_LEVEL = 15;
	/**
	 * (0.7.464) The pace the saved experience is on: when {@link #XP_BASE}/{@link #XP_STEP} are scaled, raise this by the same
	 * factor and every player's saved experience is scaled to match when they next join, so nobody loses a level.
	 */
	public static final int PACE = 2;
	static final String PACE_KEY = "_pace";
	/** (0.7.464) Every this many levels in a pillar is a milestone: a bonus point to spend there, and the big fanfare. */
	public static final int MILESTONE_EVERY = 5;
	/** (0.7.464) A Weighted Gi (Jaymo's Powers) worn: Strength experience counts this much more. */
	static final float GI_STRENGTH = 1.5F;
	static final String GI_ID = "jaymospowers:weighted_gi";
	/** (0.6.5) Strength's highest level: the levels past {@link #MAX_LEVEL} pay for the Combat Arts ({@link CombatArts}). */
	public static final int STRENGTH_MAX_LEVEL = 25;
	/**
	 * (0.7.102) Stealth's highest level: its second page, freerunning (Climber to Wall Runner; the moves are Jaymo's
	 * Powers' {@code Freerun}, which reads the ranks by reflection on {@link #rank(Player, String)}), is paid for here.
	 */
	public static final int STEALTH_MAX_LEVEL = 25;
	/**
	 * (0.7.136) Survival's highest level: its second page, Medicine (Steady Hands to Second Wind; the treating is Jaymo's
	 * Powers' Wounds, which reads the ranks by reflection on {@link #rank(Player, String)}), is paid for here.
	 */
	public static final int SURVIVAL_MAX_LEVEL = 25;
	/**
	 * The Scholar pillar's highest level: its points pay for Astrometry's ranks (and the astronomy skills still to come).
	 * Its experience is fundamental astronomy, earned only by new measurements in an Astronomer's Journal
	 * ({@code astronomy.Measurements}).
	 */
	public static final int SCHOLAR_MAX_LEVEL = 20;
	/** The Scholar pillar's colour (the astronomy art's aged brass). */
	static final int SCHOLAR_COLOR = 0xA47C36;
	/** Stealth experience: a pocket picked clean with something in it, an empty one, a theft nobody saw. */
	static final int XP_LIFT = 10, XP_EMPTY = 2, XP_THEFT = 4;
	/** A guard's pocket counts this many times over; a sleeper's this share. */
	static final float XP_GUARD = 2.0F, XP_ASLEEP = 0.5F;
	/** Unseen thefts give experience at most this often (ticks), so a chest can't be farmed one item at a time. */
	static final long THEFT_XP_TICKS = 600L;
	/**
	 * (0.7.102) Stealth experience for freerunning: a move (a vault, a cling, a kick, a wall run, a bar caught, a leap of
	 * faith), at most every {@link #FREERUN_XP_TICKS} (20 s); a hidden blade leap that lands on its mark
	 * ({@link HiddenBlade}).
	 */
	static final int XP_FREERUN = 1, XP_BLADE_LEAP = 6;
	static final long FREERUN_XP_TICKS = 400L;
	/**
	 * (0.7.111) Stealth experience for mob senses ({@link Senses}): a sneak attack (at most every {@link #SNEAK_XP_TICKS}, 20 s),
	 * an assassination (every one).
	 */
	static final int XP_SNEAK_ATTACK = 1, XP_ASSASSINATION = 3;
	static final long SNEAK_XP_TICKS = 400L;
	/** (0.7.136) Stealth experience for sneaking past monsters unseen ({@link Senses#SNEAK_PAST_TICKS} of it). */
	static final int XP_SNEAK_PAST = 1;
	/**
	 * (0.7.136) Survival experience for medicine: a treatment finished or a downed one brought round (Jaymo's Powers tags
	 * the healer {@link #TREATED_TAG}), at most every {@link #TREAT_XP_TICKS} (10 s).
	 */
	static final int XP_TREAT = 2;
	static final long TREAT_XP_TICKS = 200L;
	/** (0.7.136) The entity tag Jaymo's Powers puts on a player for each treatment they finish ({@code Medicine.XP_TAG}); taken off as it's read. */
	static final String TREATED_TAG = "jaymospowers_treated";
	/** (0.7.102) The entity tag Jaymo's Powers puts on a player for each freerun move ({@code Freerun.XP_TAG}); taken off as it's read. */
	static final String FREERUN_TAG = "jaymospowers_freerun";
	/** Practice: each Stealth level takes this share off how long your hand must stay in a pocket. */
	static final float PRACTICE_PER_LEVEL = 0.03F;
	/** Light Fingers: this share off the hold per rank. */
	static final float LIGHT_FINGERS_PER_RANK = 0.08F;
	/** Deep Pockets: per rank, this much more of what a mark carries, and one more try at a pocket that turns up empty. */
	static final float DEEP_POCKETS_SHARE = 0.15F;
	/** Soft Step: per rank, sneaking witnesses see you this much less far (a share of their full sight). */
	static final double SOFT_STEP_PER_RANK = 0.1;
	/**
	 * Quick Hands: you can reach in from the side (the cosine between the way they face and you, at most), and they must
	 * turn further round before they catch you (the cosine, at most).
	 */
	static final double QUICK_HANDS_COS = 0.35, QUICK_HANDS_TURN_COS = 0.6;

	/** Respec: a court wizard asks this much silver, and this many more per rank you've learned. */
	static final int RESPEC_SILVER = 5, RESPEC_PER_RANK = 1;

	/**
	 * The pillars of the tree. Only the open ones have perks yet. The Scholar's is learned in the Astronomer's Journal
	 * rather than on the web ({@link #web()}); last, so no ordinal moves.
	 */
	public enum Pillar {
		SURVIVAL(true, SURVIVAL_MAX_LEVEL, 0x6DBE45), STEALTH(true, STEALTH_MAX_LEVEL, 0x7A6FD0), STRENGTH(true, STRENGTH_MAX_LEVEL, 0xD0452F),
		SCHOLAR(true, SCHOLAR_MAX_LEVEL, SCHOLAR_COLOR);

		public final boolean open;
		/** The pillar's highest level. */
		public final int maxLevel;
		/** (0.7.464) Its colour, for the milestone sparks. */
		public final int color;

		Pillar(boolean open, int maxLevel, int color) {
			this.open = open;
			this.maxLevel = maxLevel;
			this.color = color;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		/**
		 * Whether this pillar is drawn on the skill web and counts toward your character level and alignment. The
		 * Scholar's doesn't: it is study, not the body's training, so it gives no attribute points or title, and its
		 * skills are learned in the Astronomer's Journal.
		 */
		public boolean web() {
			return this != SCHOLAR;
		}
	}

	/**
	 * A perk: its pillar, how many ranks it has, what a rank costs, the pillar level it needs, where it sits in the tree
	 * (tier from the top, column of three) and the perks it needs a rank in first.
	 */
	public enum Perk {
		LIGHT_FINGERS(Pillar.STEALTH, 5, 1, 0, 0, 1),
		DEEP_POCKETS(Pillar.STEALTH, 3, 1, 0, 1, 0, LIGHT_FINGERS),
		SOFT_STEP(Pillar.STEALTH, 3, 1, 0, 1, 2, LIGHT_FINGERS),
		QUICK_HANDS(Pillar.STEALTH, 1, 1, 5, 2, 0, DEEP_POCKETS),
		// (0.7.234) Lockpicking Prowess took Slip Away's place (Lockpicking): ranks saved under slip_away count for nothing, so their points come back
		LOCKPICKING(Pillar.STEALTH, 3, 1, 5, 2, 2, SOFT_STEP),
		GHOST(Pillar.STEALTH, 1, 2, 10, 3, 1, QUICK_HANDS, LOCKPICKING),
		FORAGER(Pillar.SURVIVAL, 5, 1, 0, 0, 1),
		SECOND_WIND(Pillar.SURVIVAL, 3, 1, 0, 1, 0, FORAGER),
		WEATHERED(Pillar.SURVIVAL, 3, 1, 0, 1, 2, FORAGER),
		IRON_STOMACH(Pillar.SURVIVAL, 1, 1, 5, 2, 0, SECOND_WIND),
		HARDY_LANDING(Pillar.SURVIVAL, 1, 1, 5, 2, 2, WEATHERED),
		LAST_STAND(Pillar.SURVIVAL, 1, 2, 10, 3, 1, IRON_STOMACH, HARDY_LANDING),
		HEAVY_BLOWS(Pillar.STRENGTH, 5, 1, 0, 0, 1),
		BRACE(Pillar.STRENGTH, 3, 1, 0, 1, 0, HEAVY_BLOWS),
		BULLDOZER(Pillar.STRENGTH, 3, 1, 0, 1, 2, HEAVY_BLOWS),
		QUARRYMAN(Pillar.STRENGTH, 1, 1, 5, 2, 0, BRACE),
		PACKHORSE(Pillar.STRENGTH, 1, 1, 5, 2, 2, BULLDOZER),
		TITANS_GRIP(Pillar.STRENGTH, 1, 2, 10, 3, 1, QUARRYMAN, PACKHORSE),
		// (0.6.5) the Combat Arts, Strength's second page (CombatArts): chains of blows, finishers and active skills
		// (0.7.161) its first row opens on Weapon Forms alone (set in the static block below: Weapon Forms is declared later)
		MOMENTUM(Pillar.STRENGTH, 3, 1, 2, 4, 0),
		CLEAVE(Pillar.STRENGTH, 3, 1, 2, 4, 2),
		EARTHSPLITTER(Pillar.STRENGTH, 1, 2, 18, 5, 0, MOMENTUM),
		EXECUTIONER(Pillar.STRENGTH, 1, 2, 18, 5, 1, MOMENTUM, CLEAVE),
		REND(Pillar.STRENGTH, 1, 2, 18, 5, 2, CLEAVE),
		CHARGE(Pillar.STRENGTH, 1, 2, 21, 6, 0, EARTHSPLITTER, EXECUTIONER),
		WAR_CRY(Pillar.STRENGTH, 1, 2, 21, 6, 2, EXECUTIONER, REND),
		BLADESTORM(Pillar.STRENGTH, 1, 3, 24, 7, 1, CHARGE, WAR_CRY),
		// (0.7.3) a thrown shield that ricochets between foes and comes home (ShieldThrow); last, so no ordinal moves
		SHIELD_THROW(Pillar.STRENGTH, 3, 1, 21, 6, 1, EXECUTIONER),
		// (0.7.29) Survival's second root: the meditate key (Jaymo's Powers' Meditate). Rank 1 opens it, more ranks quicken it
		MEDITATION(Pillar.SURVIVAL, 3, 1, 0, 0, 0),
		// (0.7.30) Strength's second root: every weapon's full Better Combat combo (WeaponForms); without it, one or two plain swings
		WEAPON_FORMS(Pillar.STRENGTH, 1, 1, 2, 0, 0),
		// (0.7.102) Stealth's second page, freerunning (the moves are Jaymo's Powers' Freerun); last, so no ordinal moves
		CLIMBER(Pillar.STEALTH, 3, 1, 3, 4, 0),
		FREERUNNER(Pillar.STEALTH, 2, 1, 3, 4, 2),
		// (Powers' Motion patch 0) Sure Footing left the tree: the landing roll is everyone's now (Andy). Ranks saved under
		// sure_footing count for nothing, so their points come back; Bar Swing moved up into its place
		LEAP_OF_FAITH(Pillar.STEALTH, 1, 2, 8, 5, 2, FREERUNNER),
		BAR_SWING(Pillar.STEALTH, 1, 2, 12, 5, 0, CLIMBER),
		WALL_RUNNER(Pillar.STEALTH, 2, 1, 12, 6, 2, LEAP_OF_FAITH),
		// (0.7.111) Stealth's third page, Shadows: how far mobs see you, how fast they lose you, and what an unaware blow does (Senses)
		SHADOWED(Pillar.STEALTH, 3, 1, 5, 8, 0),
		QUIET_STEP(Pillar.STEALTH, 2, 1, 5, 8, 2),
		SLIP_THE_NET(Pillar.STEALTH, 1, 1, 10, 9, 0, SHADOWED),
		SNEAK_ATTACK(Pillar.STEALTH, 2, 1, 10, 9, 2, QUIET_STEP),
		ASSASSINATE(Pillar.STEALTH, 1, 2, 15, 10, 1, SLIP_THE_NET, SNEAK_ATTACK),
		// (0.7.136) Survival's second page, Medicine: the treating is Jaymo's Powers' Wounds, the plague is Nightfall's; last, so no ordinal moves
		STEADY_HANDS(Pillar.SURVIVAL, 3, 1, 3, 4, 0),
		FIELD_DRESSER(Pillar.SURVIVAL, 2, 1, 3, 4, 2),
		BONESETTER(Pillar.SURVIVAL, 1, 1, 8, 5, 0, STEADY_HANDS),
		PLAGUE_DOCTOR(Pillar.SURVIVAL, 1, 2, 8, 5, 2, FIELD_DRESSER),
		LIFESAVER(Pillar.SURVIVAL, 1, 2, 12, 6, 1, BONESETTER, PLAGUE_DOCTOR),
		// shown as "Second Wind" (the first page's old Second Wind is shown as Rally now; its key stays, so nobody's ranks move)
		SELF_REVIVE(Pillar.SURVIVAL, 1, 2, 15, 7, 1, LIFESAVER),
		// (0.7.162) any crossbow takes anything from the off hand (OmniCrossbow's Omni, as a Survival skill for every class)
		MAKE_DO(Pillar.SURVIVAL, 1, 2, 5, 1, 1, FORAGER),
		// (0.7.342) Strength's third and fourth pages, Will: presence that cows the weak, and a mind magic can't bend (Will); last, so no ordinal moves
		DREAD_PRESENCE(Pillar.STRENGTH, 3, 1, 5, 8, 0),
		GROUNDED(Pillar.STRENGTH, 3, 1, 5, 8, 2),
		STARE_DOWN(Pillar.STRENGTH, 1, 1, 8, 9, 0, DREAD_PRESENCE),
		NO_GETTING_UP(Pillar.STRENGTH, 1, 1, 8, 9, 1, DREAD_PRESENCE),
		SPELLBREAKER(Pillar.STRENGTH, 1, 2, 8, 9, 2, GROUNDED),
		BROKEN_MORALE(Pillar.STRENGTH, 1, 1, 12, 10, 0, STARE_DOWN),
		SNAP_THE_THREAD(Pillar.STRENGTH, 1, 1, 12, 10, 1, SPELLBREAKER),
		SHRUG_IT_OFF(Pillar.STRENGTH, 1, 1, 12, 10, 2, SPELLBREAKER),
		NAME_THAT_CARRIES(Pillar.STRENGTH, 1, 2, 16, 11, 0, BROKEN_MORALE),
		WITCH_HUNTER(Pillar.STRENGTH, 2, 1, 16, 11, 1, SNAP_THE_THREAD),
		WILL_OVER_FLESH(Pillar.STRENGTH, 2, 1, 16, 11, 2, SHRUG_IT_OFF),
		// (0.7.342) Will's second page, Killing Intent: crits, no mercy for the weak, and the last stand
		GRIM_HAND(Pillar.STRENGTH, 3, 1, 10, 12, 0, DREAD_PRESENCE),
		NO_MERCY(Pillar.STRENGTH, 1, 2, 10, 12, 2, NO_GETTING_UP),
		LAST_LOOK(Pillar.STRENGTH, 1, 1, 14, 13, 0, GRIM_HAND),
		REFUSE_TO_FALL(Pillar.STRENGTH, 1, 2, 14, 13, 2, NO_MERCY),
		SECOND_BREATH(Pillar.STRENGTH, 1, 2, 18, 14, 1, LAST_LOOK, REFUSE_TO_FALL),
		UNBROKEN_ROAR(Pillar.STRENGTH, 1, 3, 22, 15, 1, SECOND_BREATH),
		// (0.7.358) Andy: "lets you withstand more hits during charged interactions": rising by yourself, a revive, Second
		// Wind's eating each take one more hit a rank before they're broken off (Jaymo's Powers' WillSkill.holdHits); last, so no ordinal moves
		UNFLINCHING(Pillar.STRENGTH, 2, 1, 10, 12, 1, NO_GETTING_UP),
		// (0.7.424) Andy: "you can question downed humanoids, unlocked in the tree": the execution hold offers question or
		// kill (Jaymo's Powers' Finishers reads this rank by key), and the talk menu opens with the questions ({@link Questioning})
		INTERROGATION(Pillar.STRENGTH, 1, 1, 2, 8, 1),
		// (0.7.424) Andy: "it should be early on the tree, and available in stealth and strength as well": the same choice,
		// each with its own edge ({@link Questioning})
		ROUGH_QUESTIONS(Pillar.STRENGTH, 1, 1, 0, 1, 1, HEAVY_BLOWS),
		QUIET_WORD(Pillar.STEALTH, 1, 1, 0, 1, 1, LIGHT_FINGERS),
		// (0.7.509) the patrons' favour slips more slowly (Patrons); last, so no ordinal moves
		DEVOTION(Pillar.SURVIVAL, 3, 1, 3, 0, 2),
		// (0.7.514) bridge perks on the borders between two pillars ({@link Bridges}): each opens from a rank in any one
		// of the perks it names, on either side, and is paid from whichever side has more points; last, so no ordinal moves
		AMBUSHER(Pillar.STEALTH, Pillar.STRENGTH, 1, 1, SNEAK_ATTACK, GRIM_HAND),
		CUTTHROAT(Pillar.STEALTH, Pillar.STRENGTH, 1, 2, ASSASSINATE, LAST_LOOK),
		IRON_HIDE(Pillar.STRENGTH, Pillar.SURVIVAL, 1, 1, BULLDOZER, SECOND_WIND),
		BEAST_OF_BURDEN(Pillar.STRENGTH, Pillar.SURVIVAL, 1, 2, PACKHORSE, IRON_STOMACH),
		TRACKER(Pillar.SURVIVAL, Pillar.STEALTH, 1, 1, FIELD_DRESSER, CLIMBER),
		LIGHT_PACK(Pillar.SURVIVAL, Pillar.STEALTH, 1, 2, PLAGUE_DOCTOR, BAR_SWING),
		// (0.7.516, RPG Series patch 2) Andy: the class skills of Zsolt Molnar's Wizards, Archers and Rogues & Warriors and
		// the Weapon Forms are learned here (or taught by a trainer's drill, free: {@link Learnables}); a spell's perk is
		// for its class only (RpgSeriesGate). Last, so no ordinal moves.
		// Survival's third page, Archery (Archer, Marksman)
		POWER_SHOT(Pillar.SURVIVAL, 1, 1, 5, 8, 0),
		ENTANGLING_ROOTS(Pillar.SURVIVAL, 1, 1, 5, 8, 2),
		BARRAGE(Pillar.SURVIVAL, 1, 2, 9, 9, 0, POWER_SHOT),
		SPIRIT_WOLF(Pillar.SURVIVAL, 1, 2, 9, 9, 2, ENTANGLING_ROOTS),
		MAGIC_ARROW(Pillar.SURVIVAL, 1, 2, 13, 10, 0, BARRAGE),
		RAIN_OF_ARROWS(Pillar.SURVIVAL, 1, 2, 13, 10, 2, SPIRIT_WOLF),
		// Survival's fourth and fifth pages, Spellcraft (Mage; Andy chose Survival for it): Arcane, Fire and Frost, a column each
		ARCANE_MISSILE(Pillar.SURVIVAL, 1, 1, 5, 12, 0),
		ARCANE_EXPLOSION(Pillar.SURVIVAL, 1, 1, 5, 13, 0, ARCANE_MISSILE),
		ARCANE_BEAM(Pillar.SURVIVAL, 1, 2, 9, 14, 0, ARCANE_EXPLOSION),
		ARCANE_BARRAGE(Pillar.SURVIVAL, 1, 2, 9, 15, 0, ARCANE_BEAM),
		ARCANE_BLINK(Pillar.SURVIVAL, 1, 2, 13, 16, 0, ARCANE_BARRAGE),
		ARCANE_EVOCATION(Pillar.SURVIVAL, 1, 2, 13, 17, 0, ARCANE_BLINK),
		FIRE_BREATH(Pillar.SURVIVAL, 1, 1, 5, 12, 1),
		FIRE_SLASH(Pillar.SURVIVAL, 1, 1, 5, 13, 1, FIRE_BREATH),
		FIRE_METEOR(Pillar.SURVIVAL, 1, 2, 9, 14, 1, FIRE_SLASH),
		FIRE_STORM(Pillar.SURVIVAL, 1, 2, 9, 15, 1, FIRE_METEOR),
		FIRE_WALL(Pillar.SURVIVAL, 1, 2, 13, 16, 1, FIRE_STORM),
		FIRE_HYDRA(Pillar.SURVIVAL, 1, 2, 13, 17, 1, FIRE_WALL),
		FROST_NOVA(Pillar.SURVIVAL, 1, 1, 5, 12, 2),
		FROST_SPIKES(Pillar.SURVIVAL, 1, 1, 5, 13, 2, FROST_NOVA),
		FROST_SHIELD(Pillar.SURVIVAL, 1, 2, 9, 14, 2, FROST_SPIKES),
		FROST_LANCE(Pillar.SURVIVAL, 1, 2, 9, 15, 2, FROST_SHIELD),
		FROST_BLIZZARD(Pillar.SURVIVAL, 1, 2, 13, 16, 2, FROST_LANCE),
		FROST_ELEMENTAL(Pillar.SURVIVAL, 1, 2, 13, 17, 2, FROST_BLIZZARD),
		// Stealth's fourth page, the Rogue's Arts (Thief; the Bear Trap the Survivalist's too)
		SLICE_AND_DICE(Pillar.STEALTH, 1, 1, 5, 12, 0),
		SHOCK_POWDER(Pillar.STEALTH, 1, 1, 5, 12, 2),
		MUTILATE(Pillar.STEALTH, 1, 2, 13, 13, 0, SLICE_AND_DICE),
		BEAR_TRAP(Pillar.STEALTH, 1, 2, 9, 13, 1, SLICE_AND_DICE, SHOCK_POWDER),
		SHADOW_STEP(Pillar.STEALTH, 1, 2, 9, 13, 2, SHOCK_POWDER),
		VANISH(Pillar.STEALTH, 1, 2, 13, 14, 1, MUTILATE, SHADOW_STEP),
		// Stealth's fifth page, Knife Arts (Weapon Forms of the knife family, anyone)
		MARKED_QUARRY(Pillar.STEALTH, 1, 1, 2, 16, 0),
		TANGLE_CORD(Pillar.STEALTH, 1, 1, 2, 16, 2),
		CAST_OFF_CLOAK(Pillar.STEALTH, 1, 2, 6, 17, 1, MARKED_QUARRY, TANGLE_CORD),
		// Strength's fifth page, Warfare (Warlord, Brute, Rider; the net the Survivalist's too). Shown as "Warrior's Charge"
		// and "Warrior's Last Stand": Combat Arts' Charge and Survival's Last Stand keep their names
		THROW(Pillar.STRENGTH, 1, 1, 5, 16, 0),
		SHOUT(Pillar.STRENGTH, 1, 1, 5, 16, 2),
		THROW_NET(Pillar.STRENGTH, 1, 1, 5, 17, 0, THROW),
		WARRIOR_CHARGE(Pillar.STRENGTH, 1, 2, 9, 17, 1, THROW, SHOUT),
		WARRIOR_LAST_STAND(Pillar.STRENGTH, 1, 2, 13, 17, 2, SHOUT),
		MORTAL_STRIKE(Pillar.STRENGTH, 1, 2, 13, 18, 1, WARRIOR_CHARGE),
		// Strength's sixth page, Weapon Forms (blade, fist and claw, spear: a column each, anyone)
		SHEATH_DRAW(Pillar.STRENGTH, 1, 1, 2, 20, 0),
		CRESCENT_SWEEP(Pillar.STRENGTH, 1, 1, 4, 21, 0, SHEATH_DRAW),
		RAIN_OF_CUTS(Pillar.STRENGTH, 1, 2, 8, 22, 0, CRESCENT_SWEEP),
		GROUND_STOMP(Pillar.STRENGTH, 1, 1, 2, 20, 1),
		FALLING_FIST(Pillar.STRENGTH, 1, 1, 4, 21, 1, GROUND_STOMP),
		RENDING_CLAW(Pillar.STRENGTH, 1, 1, 6, 22, 1, FALLING_FIST),
		FERAL_POUNCE(Pillar.STRENGTH, 1, 2, 8, 23, 1, RENDING_CLAW),
		SPEAR_LUNGE(Pillar.STRENGTH, 1, 1, 2, 20, 2),
		COUCHED_LANCE(Pillar.STRENGTH, 1, 1, 4, 21, 2, SPEAR_LUNGE),
		PINNING_THROW(Pillar.STRENGTH, 1, 2, 8, 22, 2, COUCHED_LANCE),
		// (0.7.517) Spellcraft's crown, Enchanted Storage (Mage; Andy): enchant a chest into a Bag of Holding, then up a tier
		// a rank ({@link StorageEnchanting})
		ENCHANTED_STORAGE(Pillar.SURVIVAL, 3, 2, 15, 18, 0, ARCANE_EVOCATION),
		// the Scholar's first skill, learned in the Astronomer's Journal: each rank makes its readings finer
		// (astronomy.Measurements#resolution); last, so no ordinal moves
		ASTROMETRY(Pillar.SCHOLAR, 20, 1, 0, 0, 1),
		// anyone who crouches and holds still sees the prints of whatever walked there ({@link TrailReading}); last, so no ordinal moves
		READ_THE_GROUND(Pillar.SURVIVAL, 2, 1, 0, 2, 1, FORAGER),
		// the Assassin's gambits, on Stealth's fifth page ({@link RogueGambits}), and the Thief's: lifting a weapon out of a
		// pocket, the disarm rush and the dry remark ({@link com.jaymoskingdoms.forms.RogueForms}); last, so no ordinal moves
		WATCHED(Pillar.STEALTH, 2, 1, 6, 18, 0, MARKED_QUARRY),
		THE_STARE(Pillar.STEALTH, 1, 1, 8, 18, 2, TANGLE_CORD),
		SLIP_BEHIND(Pillar.STEALTH, 1, 2, 12, 19, 0, WATCHED),
		BODY_COUNT(Pillar.STEALTH, 2, 1, 10, 19, 2, THE_STARE),
		LIFT_WEAPON(Pillar.STEALTH, 1, 1, 6, 7, 1, QUICK_HANDS),
		DRY_REMARK(Pillar.STEALTH, 1, 1, 5, 11, 1, ASSASSINATE),
		DISARM_RUSH(Pillar.STEALTH, 1, 2, 9, 15, 0, MUTILATE);

		/** Tiers this deep and deeper are on a pillar's second page in the tree's screen. */
		public static final int PAGE_TIERS = 4;

		public final Pillar pillar;
		/**
		 * (0.7.514) A bridge perk's second pillar (null for every other perk): it sits on the border between
		 * {@link #pillar} and this one, needs a rank in any one of {@link #requires} (not all), and is paid from either side.
		 */
		public final @Nullable Pillar other;
		public final int maxRank, cost, minLevel, tier, column;
		public List<Perk> requires;

		Perk(Pillar pillar, int maxRank, int cost, int minLevel, int tier, int column, Perk... requires) {
			this.pillar = pillar;
			this.other = null;
			this.maxRank = maxRank;
			this.cost = cost;
			this.minLevel = minLevel;
			this.tier = tier;
			this.column = column;
			this.requires = List.of(requires);
		}

		/** A bridge perk between {@code pillar} and {@code other}, opened by a rank in any one of {@code anyOf}. */
		Perk(Pillar pillar, Pillar other, int maxRank, int cost, Perk... anyOf) {
			this.pillar = pillar;
			this.other = other;
			this.maxRank = maxRank;
			this.cost = cost;
			this.minLevel = 0;
			this.tier = -1;
			this.column = -1;
			this.requires = List.of(anyOf);
		}

		/** Whether this is a bridge perk, on the border between two pillars. */
		public boolean bridge() {
			return other != null;
		}

		/** Whether this perk belongs to {@code p} (a bridge belongs to both its sides). */
		public boolean in(Pillar p) {
			return pillar == p || other == p;
		}

		/** The key a bridge's rank paid from {@code p} is saved under. */
		public String paidKey(Pillar p) {
			return key() + "@" + p.key();
		}

		static {
			MOMENTUM.requires = List.of(WEAPON_FORMS);
			CLEAVE.requires = List.of(WEAPON_FORMS);
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** A player's experience in each pillar and ranks in each perk (by their keys). */
	public record Progress(Map<String, Integer> xp, Map<String, Integer> ranks) {
		public static final Progress EMPTY = new Progress(Map.of(), Map.of());
		static final Codec<Progress> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("xp", Map.of()).forGetter(Progress::xp),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("ranks", Map.of()).forGetter(Progress::ranks)
		).apply(i, Progress::new));
		static final StreamCodec<FriendlyByteBuf, Progress> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), p -> new HashMap<>(p.xp()),
			ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), p -> new HashMap<>(p.ranks()),
			(xp, ranks) -> new Progress(Map.copyOf(xp), Map.copyOf(ranks)));

		public int xp(Pillar pillar) {
			return xp.getOrDefault(pillar.key(), 0);
		}

		public int rank(Perk perk) {
			return ranks.getOrDefault(perk.key(), 0);
		}

		public int level(Pillar pillar) {
			return Skills.level(xp(pillar), pillar.maxLevel);
		}

		/** How many ranks are learned, in all pillars, and (0.7.57) attribute points spent ({@link Stats}). */
		public int spent() {
			// (0.7.540) attribute points are tenths now (0-100): ten count as one rank
			int n = (Stats.spent(this) + Stats.STEP - 1) / Stats.STEP;
			for (Perk perk : Perk.values()) {
				n += rank(perk);
			}
			return n;
		}

		/** Points left to spend in {@code pillar}: one per level, less what its perks cost. */
		public int points(Pillar pillar) {
			int spent = 0;
			for (Perk perk : Perk.values()) {
				if (perk.bridge()) {
					// (0.7.514) a bridge's ranks count against the side each was paid from
					if (perk.in(pillar)) {
						spent += ranks.getOrDefault(perk.paidKey(pillar), 0) * perk.cost;
					}
				} else if (perk.pillar == pillar) {
					// (0.7.516) a rank a trainer taught costs nothing (Learnables)
					spent += (rank(perk) - ranks.getOrDefault(Learnables.taughtKey(perk), 0)) * perk.cost;
				}
			}
			// (0.7.464) and a bonus point at every milestone level
			return level(pillar) + level(pillar) / MILESTONE_EVERY - spent;
		}

		/** Why {@code perk} can't take another rank now (a translation key), or null if it can. */
		public @Nullable String blocked(Perk perk) {
			if (!perk.pillar.open) {
				return "gui.jaymoskingdoms.skills.blocked.later";
			}
			if (perk == Perk.MAKE_DO && !OmniCrossbows.LOADED) {
				return "gui.jaymoskingdoms.skills.blocked.omni";
			}
			if (rank(perk) >= perk.maxRank) {
				return "gui.jaymoskingdoms.skills.blocked.max";
			}
			if (level(perk.pillar) < perk.minLevel) {
				return "gui.jaymoskingdoms.skills.blocked.level";
			}
			if (perk.bridge()) {
				// (0.7.514) any one of the perks it names will do, on either side; paid from either side
				if (perk.requires.stream().noneMatch(need -> rank(need) > 0)) {
					return "gui.jaymoskingdoms.skills.blocked.requires_any";
				}
				return payer(perk) == null ? "gui.jaymoskingdoms.skills.blocked.points_either" : null;
			}
			for (Perk need : perk.requires) {
				if (rank(need) == 0) {
					return "gui.jaymoskingdoms.skills.blocked.requires";
				}
			}
			if (points(perk.pillar) < perk.cost) {
				return "gui.jaymoskingdoms.skills.blocked.points";
			}
			return null;
		}

		/** (0.7.514) The side a rank of bridge {@code perk} is paid from: the one with more points, if it has enough. */
		public @Nullable Pillar payer(Perk perk) {
			Pillar a = perk.pillar, b = perk.other == null ? perk.pillar : perk.other;
			Pillar richer = points(b) > points(a) ? b : a;
			return points(richer) >= perk.cost ? richer : null;
		}
	}

	/** Each player's progress through the tree. Saved, kept through death, synced to its own player (the tree's screen). */
	public static final AttachmentType<Progress> PROGRESS = AttachmentRegistry.create(JaymosKingdoms.id("skills"),
		builder -> builder.persistent(Progress.CODEC).copyOnDeath().syncWith(Progress.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Player → server: spend points on a rank of {@code perk} (its ordinal). */
	public record Spend(int perk) implements CustomPacketPayload {
		public static final Type<Spend> TYPE = new Type<>(JaymosKingdoms.id("skill_spend"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Spend> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.perk), buf -> new Spend(buf.readVarInt()));

		@Override
		public Type<Spend> type() {
			return TYPE;
		}
	}

	/** Player → game time an unseen theft next gives experience. */
	private static final Map<UUID, Long> NEXT_THEFT_XP = new HashMap<>();
	/** (0.7.102) Player → game time a freerun move next gives experience. */
	private static final Map<UUID, Long> NEXT_FREERUN_XP = new HashMap<>();
	/** (0.7.111) Player → game time a sneak attack next gives experience. */
	private static final Map<UUID, Long> NEXT_SNEAK_XP = new HashMap<>();
	/** (0.7.136) Player → game time a treatment next gives experience. */
	private static final Map<UUID, Long> NEXT_TREAT_XP = new HashMap<>();

	private Skills() {
	}

	/** Player → the court wizard who offered to unmake what they've learned (entity id). */
	private static final Map<UUID, Integer> RESPEC_OFFERS = new HashMap<>();

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Spend.TYPE, Spend.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Spend.TYPE, (payload, context) -> spend(context.player(), payload.perk()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NEXT_THEFT_XP.clear();
			NEXT_FREERUN_XP.clear();
			NEXT_SNEAK_XP.clear();
			NEXT_TREAT_XP.clear();
			RESPEC_OFFERS.clear();
		});
		// (0.7.102) freerun moves (Jaymo's Powers tags the player for each one): a little Stealth experience
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.entityTags().contains(FREERUN_TAG)) {
					player.removeTag(FREERUN_TAG);
					freerun(player, player.level().getGameTime());
				}
				// (0.7.136) and treatments (Powers' Wounds tags the healer): a little Survival experience
				if (player.entityTags().contains(TREATED_TAG)) {
					player.removeTag(TREATED_TAG);
					treated(player, player.level().getGameTime());
				}
			}
		});
		// (perks that work through attributes are put back on a player whenever they come into the world)
		ServerPlayerEvents.JOIN.register(player -> {
			repace(player);
			refresh(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refresh(newPlayer));
		// a bottle o' enchanting, drunk (sneak and use it), gives back every point
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.EXPERIENCE_BOTTLE) || !player.isShiftKeyDown() || of(player).spent() == 0) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp) {
				stack.consume(1, sp);
				sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 1.0F);
				respec(sp);
				sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.skills.respec.bottle").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			return InteractionResult.SUCCESS;
		});
		// (0.5.54) an MCA villager who is a court wizard: sneak and use them (their own menu opens as ever without sneaking;
		// from behind with an empty hand it's a pickpocket, which is registered first)
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()
				|| entity instanceof Folk || !(entity instanceof LivingEntity wizard) || !CourtWizards.is(wizard)) {
				return InteractionResult.PASS;
			}
			if (!Languages.understands(sp, wizard)) {
				// (0.7.20) unmaking a man's skills takes explaining, in a tongue you share
				Languages.shrug(sp, wizard);
				return InteractionResult.SUCCESS;
			}
			// (0.7.31) first, what the stars foretell
			DisasterRelief.foretell(sp.level(), sp, wizard);
			if (of(sp).spent() == 0) {
				// (0.6.0) nothing to unmake: he offers a Writ of Rebirth instead
				Classes.writOffer(sp, wizard);
			} else {
				wizard(sp, wizard);
			}
			return InteractionResult.SUCCESS;
		});
		SurvivalSkill.register();
		StrengthSkill.register();
		Stats.register();
		CombatArts.register();
		// (0.7.342) Strength's Will pages
		Will.register();
		Staredowns.register();
		WillClash.register();
		// (0.7.361) Valor, its folk and King's Valor
		Valor.register();
		ValorFolk.register();
		ValorKings.register();
		ShieldThrow.register();
		// (0.7.514) the bridge perks
		Bridges.register();
		TrailReading.register();
		Renown.register();
		KitchenCompat.skills();
	}

	/** Puts {@code player}'s attribute perks on them as they stand, and their alignment over their head. */
	static void refresh(ServerPlayer player) {
		// (0.7.516) forms known from before the web had them show as taught
		Learnables.sync(player);
		SurvivalSkill.attributes(player);
		StrengthSkill.attributes(player);
		Bridges.attributes(player);
		// (0.7.540) old saves' attribute points onto the 0-100 scale, once
		Stats.migrate(player);
		Stats.attributes(player);
		Heritage.attributes(player);
		// (0.7.96) a Survivalist's hearts grow with their level
		ClassPerks.attributes(player);
		Renown.update(player);
	}

	/** Puts (or with {@code amount} 0, takes off) the skill tree's modifier {@code id} on {@code player}'s {@code attribute}. */
	static void modifier(ServerPlayer player, Holder<Attribute> attribute, String id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		Identifier key = JaymosKingdoms.id(id);
		if (amount == 0.0) {
			instance.removeModifier(key);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(key, amount, operation));
		}
	}

	/**
	 * The factor on {@code player}'s food exhaustion: Survival practice and Packhorse make hunger drain slower, and
	 * (0.7.57) Endurance ({@link Stats}) and their heritage ({@link Heritage}) where they stand.
	 */
	public static float exhaustion(ServerPlayer player) {
		return SurvivalSkill.exhaustion(player) * StrengthSkill.exhaustion(player) * Bridges.exhaustion(player) * Stats.exhaustion(player)
			* Heritage.exhaustion(player);
	}

	/**
	 * (0.7.514) Takes every perk of {@code pillar} out of {@code ranks}, and every bridge rank paid from it (a bridge
	 * paid from its other side stays).
	 */
	static void forget(Map<String, Integer> ranks, Pillar pillar) {
		for (Perk perk : Perk.values()) {
			if (perk.bridge()) {
				int paid = ranks.getOrDefault(perk.paidKey(pillar), 0);
				ranks.remove(perk.paidKey(pillar));
				int left = ranks.getOrDefault(perk.key(), 0) - paid;
				if (left > 0) {
					ranks.put(perk.key(), left);
				} else {
					ranks.remove(perk.key());
				}
			} else if (perk.pillar == pillar && !ranks.containsKey(Learnables.taughtKey(perk))) {
				// (0.7.516) what a trainer taught stays
				ranks.remove(perk.key());
			}
		}
	}

	/** Every point {@code player} spent comes back to spend again; their experience and levels stay. */
	static void respec(ServerPlayer player) {
		// (0.7.516) forms bought with points are unlearned with them; what a trainer taught stays
		Learnables.unlearnBought(player);
		player.setAttached(PROGRESS, new Progress(of(player).xp(), Learnables.taughtOnly(of(player).ranks())));
		refresh(player);
	}

	// ---- the court wizard -------------------------------------------------------------------------------------

	/** What a court wizard asks to unmake what {@code player} has learned. */
	static int respecPrice(Player player) {
		return RESPEC_SILVER + RESPEC_PER_RANK * of(player).spent();
	}

	/** A player spoke to a court wizard: if they've learned anything, the wizard offers to unmake it, for a price. */
	static void wizard(ServerPlayer player, LivingEntity wizard) {
		if (of(player).spent() == 0 || !ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			return;
		}
		int price = respecPrice(player);
		RESPEC_OFFERS.put(player.getUUID(), wizard.getId());
		ServerPlayNetworking.send(player, new TalkNet.Ask(wizard.getId(), wizard.getDisplayName(),
			Component.translatable("talk.jaymoskingdoms.wizard.respec", Money.text(player, Money.worth(price))),
			List.of(Component.translatable("talk.jaymoskingdoms.wizard.respec.yes", Money.text(player, Money.worth(price))), Component.translatable("talk.jaymoskingdoms.wizard.respec.no")),
			List.of(Money.canPay(player, Money.worth(price)), true)));
	}

	/** An answer to the wizard's offer; false when it wasn't one (it's some other talk). */
	static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Integer offer = RESPEC_OFFERS.get(player.getUUID());
		if (offer == null || offer != answer.entityId()) {
			return Classes.answer(player, answer);
		}
		RESPEC_OFFERS.remove(player.getUUID());
		if (answer.option() != 0 || !(player.level().getEntity(answer.entityId()) instanceof LivingEntity wizard) || !CourtWizards.is(wizard) || wizard.distanceTo(player) > 8.0) {
			return true;
		}
		int price = respecPrice(player);
		if (!Money.canPay(player, Money.worth(price))) {
			return true;
		}
		Money.charge(player, Money.worth(price)); // (0.7.498) silver coins, not emeralds
		respec(player);
		ServerLevel level = (ServerLevel) player.level();
		Speech.tell(level, wizard, player, Component.translatable("talk.jaymoskingdoms.wizard.respec.done"), Speech.GOLD);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.2F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.skills.respec.wizard").withStyle(ChatFormatting.LIGHT_PURPLE));
		return true;
	}

	// ---- levels ------------------------------------------------------------------------------------------------

	/** Experience from level {@code level} to the next. */
	public static int toNext(int level) {
		return XP_BASE + XP_STEP * level;
	}

	/** Experience it takes to reach {@code level} from nothing. */
	public static int total(int level) {
		int sum = 0;
		for (int l = 0; l < level; l++) {
			sum += toNext(l);
		}
		return sum;
	}

	/** The level {@code xp} experience reaches, at most {@code max}. */
	public static int level(int xp, int max) {
		int level = 0;
		while (level < max && xp >= total(level + 1)) {
			level++;
		}
		return level;
	}

	public static Progress of(Player player) {
		return player.getAttachedOrElse(PROGRESS, Progress.EMPTY);
	}

	static int rank(Player player, Perk perk) {
		return of(player).rank(perk);
	}

	/** {@code player}'s rank in the perk named {@code key} (0 for an unknown one): for other mods, by reflection (Jaymo's Powers' Meditate). */
	public static int rank(Player player, String key) {
		for (Perk perk : Perk.values()) {
			if (perk.key().equals(key)) {
				return rank(player, perk);
			}
		}
		return 0;
	}

	/** (0.7.464) Scales {@code player}'s saved experience onto the current {@link #PACE}, once, so their levels stay put. */
	static void repace(ServerPlayer player) {
		Progress p = of(player);
		int was = Math.max(1, p.xp().getOrDefault(PACE_KEY, 1));
		if (was == PACE) {
			return;
		}
		Map<String, Integer> xp = new HashMap<>();
		for (Map.Entry<String, Integer> e : p.xp().entrySet()) {
			if (!e.getKey().equals(PACE_KEY)) {
				xp.put(e.getKey(), (int)Math.min(Integer.MAX_VALUE, (long)e.getValue() * PACE / was));
			}
		}
		xp.put(PACE_KEY, PACE);
		player.setAttached(PROGRESS, new Progress(Map.copyOf(xp), p.ranks()));
	}

	/** Gives {@code player} {@code amount} experience in {@code pillar}; a level reached is announced. */
	public static void grant(ServerPlayer player, Pillar pillar, int amount) {
		if (amount <= 0) {
			return;
		}
		// (0.7.57) Wits: every pillar learns faster
		amount = Math.max(amount, Math.round(amount * Stats.xpFactor(player) * Heritage.xpFactor(player)));
		// (0.7.464) training under weight
		if (pillar == Pillar.STRENGTH && GI_ID.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM
			.getKey(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()).toString())) {
			amount = Math.max(amount + 1, Math.round(amount * GI_STRENGTH));
		}
		Progress p = of(player);
		int before = p.xp(pillar);
		int after = Math.min(total(pillar.maxLevel), before + amount);
		if (after == before) {
			return;
		}
		Map<String, Integer> xp = new HashMap<>(p.xp());
		xp.put(pillar.key(), after);
		xp.put(PACE_KEY, PACE);
		player.setAttached(PROGRESS, new Progress(Map.copyOf(xp), p.ranks()));
		int was = level(before, pillar.maxLevel), now = level(after, pillar.maxLevel);
		if (now > was) {
			refresh(player);
			// (0.7.464) the pack's milestone fanfare: small for a level, big every fifth (with its bonus point), grand at the top
			Component name = Component.translatable("skill.jaymoskingdoms." + pillar.key());
			boolean top = now >= pillar.maxLevel;
			boolean milestone = now / MILESTONE_EVERY > was / MILESTONE_EVERY;
			int size = top ? Fanfare.GRAND : milestone ? Fanfare.MAJOR : Fanfare.MINOR;
			// (the Scholar's points are spent in the Astronomer's Journal, not on K)
			String where = pillar.web() ? "" : ".journal";
			Component chat = milestone
				? Component.translatable("message.jaymoskingdoms.skills.milestone" + where, name, now)
				: Component.translatable("message.jaymoskingdoms.skills.level" + where, name, now);
			Fanfare.fanfare(player, Component.translatable(top ? "message.jaymoskingdoms.skills.top_title" : "message.jaymoskingdoms.skills.level_title", name),
				Component.translatable("message.jaymoskingdoms.skills.level_subtitle", now), chat, pillar.color, size);
		}
	}

	private static void spend(ServerPlayer player, int ordinal) {
		Perk[] perks = Perk.values();
		if (ordinal < 0 || ordinal >= perks.length) {
			return;
		}
		Perk perk = perks[ordinal];
		Progress p = of(player);
		if (p.blocked(perk) != null) {
			return;
		}
		if (Knowledge.lockedPerk(player, perk)) {
			// (0.7.492) a higher technique: Knowledge 4
			Knowledge.refuse(player, Knowledge.HIGHER_ARTS);
			return;
		}
		if (Learnables.classLocked(player, perk)) {
			// (0.7.516) a class skill of another class
			return;
		}
		Map<String, Integer> ranks = new HashMap<>(p.ranks());
		ranks.merge(perk.key(), 1, Integer::sum);
		if (perk.bridge()) {
			// (0.7.514) note which side paid, so that side's points count it
			Pillar payer = p.payer(perk);
			if (payer == null) {
				return;
			}
			ranks.merge(perk.paidKey(payer), 1, Integer::sum);
		}
		player.setAttached(PROGRESS, new Progress(p.xp(), Map.copyOf(ranks)));
		// (0.7.516) a form's perk teaches the form
		Learnables.spent(player, perk);
		refresh(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
	}

	// ---- Stealth -----------------------------------------------------------------------------------------------

	/** A pocket picked clean: experience for it, the more for a guard's, the less for a sleeper's. Returns what it gave. */
	static int pickedClean(ServerPlayer player, boolean lifted, boolean guard, boolean asleep) {
		float xp = lifted ? XP_LIFT : XP_EMPTY;
		if (guard) {
			xp *= XP_GUARD;
		}
		if (asleep) {
			xp *= XP_ASLEEP;
		}
		int amount = Math.max(1, Math.round(xp));
		// (0.7.509) a clean pickpocket pleases the Ash Whisperer
		Patrons.deed(player, Patron.ASH_WHISPERER, Patrons.DEED_SMALL, "pickpocket");
		if (of(player).xp(Pillar.STEALTH) >= total(Pillar.STEALTH.maxLevel)) {
			return 0;
		}
		grant(player, Pillar.STEALTH, amount);
		return amount;
	}

	/** A theft nobody saw (from a store, a chest, saddlebags): a little experience, at most every so often. */
	static void unseenTheft(ServerPlayer player, long now) {
		if (NEXT_THEFT_XP.getOrDefault(player.getUUID(), 0L) > now) {
			return;
		}
		NEXT_THEFT_XP.put(player.getUUID(), now + THEFT_XP_TICKS);
		// (0.7.509) a theft nobody saw pleases the Ash Whisperer
		Patrons.deed(player, Patron.ASH_WHISPERER, Patrons.DEED_SMALL, "unseen_theft");
		grant(player, Pillar.STEALTH, XP_THEFT);
	}

	/** (0.7.102) A freerun move: a little experience, at most every {@link #FREERUN_XP_TICKS}. */
	static void freerun(ServerPlayer player, long now) {
		if (NEXT_FREERUN_XP.getOrDefault(player.getUUID(), 0L) > now) {
			return;
		}
		NEXT_FREERUN_XP.put(player.getUUID(), now + FREERUN_XP_TICKS);
		grant(player, Pillar.STEALTH, XP_FREERUN);
	}

	/** (0.7.111) A sneak attack: a little experience, at most every {@link #SNEAK_XP_TICKS}. */
	static void sneakAttack(ServerPlayer player, long now) {
		if (NEXT_SNEAK_XP.getOrDefault(player.getUUID(), 0L) > now) {
			return;
		}
		NEXT_SNEAK_XP.put(player.getUUID(), now + SNEAK_XP_TICKS);
		grant(player, Pillar.STEALTH, XP_SNEAK_ATTACK);
	}

	/** (0.7.136) Sneaking past monsters unseen for a while ({@link Senses}): a little experience. */
	static void sneakPast(ServerPlayer player) {
		grant(player, Pillar.STEALTH, XP_SNEAK_PAST);
	}

	/** (0.7.136) A treatment finished: a little Survival experience, at most every {@link #TREAT_XP_TICKS}. */
	static void treated(ServerPlayer player, long now) {
		if (NEXT_TREAT_XP.getOrDefault(player.getUUID(), 0L) > now) {
			return;
		}
		NEXT_TREAT_XP.put(player.getUUID(), now + TREAT_XP_TICKS);
		grant(player, Pillar.SURVIVAL, XP_TREAT);
	}

	/** (0.7.111) An assassination (an unaware hidden blade stab): more experience, every time. */
	static void assassination(ServerPlayer player) {
		grant(player, Pillar.STEALTH, XP_ASSASSINATION);
	}

	/** How long {@code player}'s hand must stay in a pocket, from {@code ticks}: shorter with practice and Light Fingers. */
	static float holdTicks(Player player, float ticks) {
		Progress p = of(player);
		// (0.7.102) practice counts the first page's 15 levels only: the levels past it pay for freerunning
		// (0.7.509) and the Ash Whisperer's Quick Fingers
		return ticks * (1.0F - PRACTICE_PER_LEVEL * Math.min(MAX_LEVEL, p.level(Pillar.STEALTH))) * (1.0F - LIGHT_FINGERS_PER_RANK * p.rank(Perk.LIGHT_FINGERS))
			* Blessings.pickHold(player);
	}

	/** Ghost: a guard takes no longer to rob than anyone else, and a theft nobody saw is never missed. */
	static boolean ghost(Player player) {
		return rank(player, Perk.GHOST) > 0;
	}

	/** The share of a carried stack {@code player} takes (Deep Pockets). */
	static float carriedShare(Player player, float base) {
		return Math.min(1.0F, base + DEEP_POCKETS_SHARE * rank(player, Perk.DEEP_POCKETS));
	}

	/** How many tries {@code player} gets at a pocket that turns up empty (Deep Pockets). */
	static int pocketTries(Player player) {
		return 1 + rank(player, Perk.DEEP_POCKETS);
	}

	/** The share of their sight sneaking hides {@code player} by (Soft Step). */
	static double sneakFactor(Player player, double base) {
		return Math.max(0.1, base - SOFT_STEP_PER_RANK * rank(player, Perk.SOFT_STEP) - Heritage.sneak(player) - Blessings.sneak(player));
	}

	/** How far round the mark {@code player} may stand and still reach in (Quick Hands: the side will do). */
	static double behindCos(Player player, double base) {
		return rank(player, Perk.QUICK_HANDS) > 0 ? Math.max(base, QUICK_HANDS_COS) : base;
	}

	/** How far the mark may turn toward {@code player}, hand in their pocket, before they catch them (Quick Hands: further). */
	static double turnCos(Player player, double base) {
		return rank(player, Perk.QUICK_HANDS) > 0 ? Math.max(base, QUICK_HANDS_TURN_COS) : base;
	}
}
