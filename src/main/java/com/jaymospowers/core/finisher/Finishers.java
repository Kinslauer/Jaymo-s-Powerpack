package com.jaymospowers.core.finisher;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.TaczGuns;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.LimbBoxes;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Breaks;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.arsenal.ArsenalFlintlock;
import com.jaymospowers.power.darktide.DarktideStatus;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.98) Finishers. Andy: "every weapon should be able to right click and hold a downed enemy, and then do a unique
 * finisher animation". Hold use (not sneaking: sneak + use stays the revive and the pick-up) on someone downed for
 * {@link #HOLD_TICKS} and you finish them: one move per kind of weapon in your main hand ({@link Kind}), read off the
 * item's tags and name, so a weapon forged at the Tool Station or another mod's falls in with its kind.
 * <p>
 * (0.15.110) Andy: "make the execution only possible with bare hands, and only activated after tapping the target three
 * times. this is to prevent accidental neck snaps. Also make all the other executions unlocked in the combat arts tree
 * replacing the current executioner function". So the neck snap is bare hands only, on the {@link #TAPS}th quick tap (a
 * hold does nothing), and anything else that isn't a weapon (a torch, a loaf) finishes nobody. The weapons' finishers
 * are Kingdoms' Combat Arts perk Executioner ({@link #weaponsUnlocked}); without it a hold with a weapon does nothing.
 * <p>
 * The move plays out on its own once begun: you stand rooted facing them (no walking, jumping, swinging or using), the
 * body drawn by {@code FinisherPose} on every client, and the blows land on its beats (sound, blood or bone, a jolt
 * through the body). The last blow is their death, credited to you ({@link Downed#execute}); an axe takes the head
 * first. It stops, sparing them, if you're downed or die, or they get up, are carried off or end up out of reach.
 * <p>
 * Not on tamed animals (someone's dog or horse), and only one finisher on a body at a time. A plain tap on a downed
 * guard still has Kingdoms take its weapons first.
 * <p>
 * (0.15.114) Andy: "the pointblank finisher should use a bullet and actually play the blast", and "neck break should take
 * 5s to finish". Guns (our TaCZ flintlocks, TaCZ's own, the Void Armory's) have their own finisher, {@link Kind#GUN}: it
 * needs a round in the gun, spends it, and fires with the gun's own shot (through TaCZ), flash and smoke. A bow or
 * crossbow likewise needs an arrow and spends it ({@link #loaded}, {@link #spend}). The neck snap is a slow strangle-hold
 * now, the snap {@link Kind#FIST}'s 100th tick.
 * <p>
 * (0.15.146) Andy: "add a basic execution to weapons that is available to all, and faster than a bare hand neck snap? a
 * wild slam with anything blunt, (using tools like a bow or fishing rod heavily damages them), and stabbing through with
 * a struggle to remove the weapon with anything bladed. Axes slam too. Also the player should be in the correct area for
 * a neck snap, like near the head on any humanoid". Without Executioner a held weapon now plays a basic move
 * ({@link #basic}): {@link Kind#IMPALE} for a blade, {@link Kind#SLAM} for everything else, axes and guns included, and
 * any tool that wears (a bow, a fishing rod, a shovel) slams too, taking {@link #IMPROVISED_WEAR} of its durability a
 * blow ({@link #wear}). A bow or crossbow with nothing to shoot slams even with Executioner. The neck snap's taps only
 * count kneeling at a person's head ({@link #atHead}).
 * <p>
 * (0.16.8) Andy: "the suits should have their own executions". With the symbiote suit showing (any form) and nothing in
 * the main hand, the hold is a weapon's ({@link #HOLD_TICKS}, no Executioner: the suit is the weapon) and plays
 * one of {@code WebExecutions}' moves instead of the neck snap; its own attachment counts as {@link #busy} and
 * {@link #targeted} here, so the roots, the camera and the body lying still are shared.
 * <p>
 * (0.17.7) Andy: "maybe execution should be on left click hold for 1.5s to confirm?", then "change it to shift + m1 hold. This
 * is so it doesnt interfere with z fighters". It is: <b>sneak + hold attack</b> on someone
 * downed or crippled for {@link #HOLD_TICKS}, any weapon and bare hands alike (the neck snap still only at the head; the
 * three taps are gone, the hold is the confirmation). (0.17.97: the click that starts it no longer
 * lands a blow, and no swing repeats while it charges: {@code FinisherClient.holdsAttack}.) Right-click held on
 * the downed is the revive now, and sneak + right-click held the lift, so none of the three is taken for another. Holding
 * attack on an entity repeats nothing in vanilla (only digging a block does), so the hold is free; the client says when it
 * starts and stops ({@link Execute}, from {@code FinisherClient}), and Z Fighter's held-attack combos stand aside while
 * it's aimed at someone helpless.
 * <p>
 * (0.17.181) Andy: "add a smashing execution with a rock". A loose rock ({@code GroundRocks}) in the main hand plays
 * {@link Kind#ROCK}, everyone's (no Executioner: anyone can pick up a rock): down on a knee at their head, the off hand
 * pinning them, the rock brought down again and again, the last with both hands, which breaks it (one rock is used up).
 * <p>
 * (0.18.46) Andy: "z fighter needs at least 3 bare handed executions, maybe more with different forms, for example
 * frostborn should use the beam". A Z Fighter's empty hands play one of the {@code Z_*} kinds ({@link Strike#KI}) instead
 * of the neck snap, as the suit's play its own: no Executioner, anywhere round the body. What they pick, cost and look like
 * is {@code ZFighterExecutions}'; the hold, roots and poses are the same as every finisher's.
 */
public final class Finishers {
	/** (0.17.7) How long attack is held on the downed before a finisher begins (ticks; Andy's 1.5 s), and when its bar shows (a click is just a blow). */
	public static final int HOLD_TICKS = 30, BAR_AFTER = 6;
	/** (0.17.7) Held at the wrong end for the neck snap: the reminder comes this often (ticks). */
	static final int HEAD_HINT_EVERY = 20;
	/** (0.15.110) The Combat Arts perk (Jaymo's Kingdoms) that unlocks every weapon's finisher; bare hands need none. */
	static final String UNLOCK_PERK = "executioner";
	/** How far they may be from you for it to go on (blocks, past your reach for the use). */
	static final double SLACK = 1.0, MAX_DISTANCE = 4.0;
	/** The hold's bar length. */
	static final int HOLD_BAR = 8;
	/** Screen shake on a blow for you and the one being finished, and the last blow's for both and those around. */
	static final float BEAT_SHAKE = 0.6F, KILL_SHAKE = 1.4F, AROUND_SHAKE = 0.8F;
	static final double AROUND_RANGE = 10.0;
	/** (0.15.114) How far the point-blank shot is heard through TaCZ (blocks), and its shake for those around. */
	static final int SHOT_HEARD = 64;
	static final float SHOT_AROUND_SHAKE = 0.5F;
	/**
	 * (0.15.146) The neck snap: how near their head your feet must be (blocks, across the ground), and where the head of
	 * someone downed lies from their middle, as a share of their height (a crawler's ahead, one on its back behind).
	 */
	static final double HEAD_REACH = 1.25, DOWNED_HEAD = 0.32, DOWNED_FEET = 0.555;
	/** (0.15.146) A basic move's wear on a tool that isn't made for it (share of its durability a blow, at least the minimum), and on a weapon. */
	static final float IMPROVISED_WEAR = 0.12F;
	static final int IMPROVISED_WEAR_MIN = 4, WEAPON_WEAR = 1;
	/**
	 * (0.18.97) Question or kill: once the hold is full, how far you turn your view to choose (degrees: left questions,
	 * right kills), and how long the choice waits before you're taken to have thought better of it (ticks).
	 */
	public static final float CHOOSE_DEGREES = 14.0F;
	static final int CHOOSE_TICKS = 100;
	/** (0.18.97) How long you may hold someone for questioning (ticks), and when their talk opens (once the pose has settled). */
	public static final int QUESTION_TICKS = 20 * 60;
	static final int QUESTION_OPEN = 10;
	/**
	 * (0.18.97) Kingdoms' perks that let you question the downed (any one): Strength's Rough Questions, Stealth's Quiet Word,
	 * Will's Interrogation. Andy: "it should be early on the tree, and available in stealth and strength as well".
	 */
	static final String ROUGH_PERK = "rough_questions", QUIET_PERK = "quiet_word", WILL_PERK = "interrogation";
	/** (0.18.97) Quiet Word's choke hold: how far round behind them you must be (degrees off the way they face). */
	static final float BEHIND_DEGREES = 110.0F;

	/** How a blow lands: what it sounds and looks like. */
	public enum Strike { CUT, STAB, CRUSH, PIERCE, SHOT, BOLT, BLAST, SNAP, KI,
		/** (0.18.97) No blow at all: they're held where they lie and questioned ({@link Kind#questioning}). */
		HOLD }

	/**
	 * One finisher per kind of weapon: how long it plays (ticks), the beats its blows land on (the last is the kill), how
	 * each lands, and what is heard as it begins. The poses are the client's ({@code FinisherPose}), keyed by name.
	 */
	public enum Kind {
		/** Run Through: the blade turned point down in both hands, raised, driven in, twisted and wrenched free. */
		SWORD(38, new int[] {17, 28}, Strike.STAB),
		/** Headsman's Stroke: a two-handed blade swung up over the shoulder and down in one great cleave. */
		GREATSWORD(36, new int[] {20}, Strike.CUT),
		/** Mercy Stroke: down on a knee, three quick stabs, and the throat drawn across. */
		DAGGER(40, new int[] {10, 14, 18, 28}, Strike.STAB),
		/** Executioner: feet planted, the axe raised high and brought down on the neck. The head comes off. */
		AXE(36, new int[] {21}, Strike.CUT),
		/** Skull Crusher: a blow, the weapon heaved up again, and a second brought down with everything behind it. */
		BLUNT(44, new int[] {14, 31}, Strike.CRUSH),
		/** Pinned: the spear turned point down, driven through, leant on, and wrenched out. */
		SPEAR(36, new int[] {14, 27}, Strike.PIERCE),
		/** Reaping: the polearm swept round behind, and down in a wide arc across them. */
		POLEARM(34, new int[] {20}, Strike.CUT),
		/** The staff twirled, then the butt driven down twice. */
		STAFF(38, new int[] {18, 27}, Strike.CRUSH),
		/** Loosed Close: an arrow nocked, drawn slow, aimed straight down, loosed. (0.15.114) Spends an arrow. */
		BOW(32, new int[] {22}, Strike.SHOT),
		/** A bolt to the heart: the crossbow levelled at them, and the trigger pulled. (0.15.114) Spends its bolt. */
		CROSSBOW(26, new int[] {13}, Strike.BOLT),
		/** (0.15.114) Point Blank: the gun brought up, cocked, aimed down at the head at arm's length, and fired. Spends a round. */
		GUN(30, new int[] {20}, Strike.BLAST),
		/**
		 * Bare hands: kneel, take the head in both hands, and twist. (0.15.114) Andy: "neck break should take 5s to finish":
		 * a hold that strains twice against them before the snap at 5 s.
		 */
		FIST(118, new int[] {40, 70, 100}, Strike.SNAP),
		/** (0.15.146) Wild Slam, everyone's: whatever's in hand brought down on them three times, fast and hard. */
		SLAM(36, new int[] {10, 19, 28}, Strike.CRUSH),
		/**
		 * (0.15.146) Stabbed Through, everyone's: the blade driven down through them, then stuck, tugged at three times
		 * and wrenched free ({@link #after}, once they're dead).
		 */
		IMPALE(46, new int[] {12}, Strike.STAB, new int[] {21, 28, 34, 40}),
		/**
		 * (0.15.148) Ball and Chain, a flail's, (0.16.1) Executioner only: the ball whirled up overhead on its chain and brought down on
		 * them, whirled again, and down once more with everything behind it.
		 */
		FLAIL(46, new int[] {18, 36}, Strike.CRUSH),
		/**
		 * (0.15.148) Garrotte, the leather whip's, (0.16.1) Executioner only: the lash cracked round their neck ({@link #WHIP_CRACK}), then
		 * hauled tight, leaning back on it, until the neck goes.
		 */
		WHIP(64, new int[] {24, 42, 58}, Strike.SNAP),
		/**
		 * (0.17.181) Stoned, a loose rock's, everyone's: knelt at their head, the off hand pinning them, the rock raised and
		 * brought down three times, then lifted high in both hands and dropped onto the skull with everything behind it.
		 */
		ROCK(52, new int[] {12, 20, 28, 40}, Strike.CRUSH),
		// ---- (0.18.46) a Z Fighter's bare hands (ZFighterExecutions) ----------------------------------------------------
		/** Point-Blank: stood over them, a palm thrust down at the chest, the ki gathered in it, and let go. */
		Z_PALM(34, new int[] {22}, Strike.KI),
		/** Flurry: down on a knee, both fists into them too fast to count, and the last one, with ki, into the ground. */
		Z_FLURRY(46, new int[] {8, 11, 14, 17, 20, 23, 26, 36}, Strike.KI),
		/** Hammer Fist: both fists clasped overhead and brought down, heaved up again, and down hard enough to crack the ground. */
		Z_HAMMER(40, new int[] {14, 30}, Strike.KI),
		/** Cupped Wave, an Earthling's: the hands cupped at the hip, the ki gathered there, and thrust down at them in a beam. */
		Z_WAVE(46, new int[] {30}, Strike.KI),
		/** Crimson Cannon, a Warrior-born's: the arms flung wide as it gathers, the palms slammed together, and a heavy beam. */
		Z_CANNON(44, new int[] {28}, Strike.KI),
		/** Spiral Lance, a Verdant's: two fingers to the brow, crackling, then pointed down, and the spiral beam through them. */
		Z_LANCE(42, new int[] {30}, Strike.KI),
		/** Frost Needle, a Frost-born's: unhurried, one finger, a hair-thin beam through a leg, an arm, and then the head. */
		Z_NEEDLE(48, new int[] {12, 22, 36}, Strike.KI),
		/** Supernova Palm, in a Super form: on a knee, the palm on their chest, the aura poured through it until nothing's left. */
		Z_NOVA(44, new int[] {30}, Strike.KI),
		// ---- (0.18.97) questioning (Andy: "different animations for each weapon"): held over them, no blow, till it's over ------
		/** At Their Throat: down on a knee at their head, the edge laid against the throat, the off hand in their collar. */
		Q_BLADE(QUESTION_TICKS, new int[0], Strike.HOLD),
		/** Raised Over Them: stood over them, the weapon held high and ready to come down. */
		Q_BLUNT(QUESTION_TICKS, new int[0], Strike.HOLD),
		/** At Their Face: the bow drawn, the crossbow or gun levelled at their face from a pace off. */
		Q_AIM(QUESTION_TICKS, new int[0], Strike.HOLD),
		/** By the Collar: down on a knee, both fists in their collar, their head hauled up off the ground. */
		Q_GRIP(QUESTION_TICKS, new int[0], Strike.HOLD),
		/** (Quiet Word) The Choke Hold: taken from behind on their feet, an arm round the throat, the blade or fist at their ribs. */
		Q_CHOKE(QUESTION_TICKS, new int[0], Strike.HOLD);

		/** (0.15.148) The tick the whip's crack lands round the neck. */
		public static final int WHIP_CRACK = 8;

		public final int length;
		public final int[] beats;
		public final Strike strike;
		/** (0.15.146) Beats after the kill, on the body where it died (the stuck blade's tugs; the last one pulls it free). */
		public final int[] after;

		Kind(int length, int[] beats, Strike strike) {
			this(length, beats, strike, new int[0]);
		}

		Kind(int length, int[] beats, Strike strike, int[] after) {
			this.length = length;
			this.beats = beats;
			this.strike = strike;
			this.after = after;
		}

		/** The beat its kill lands on (a questioning has none: its whole length). */
		public int kill() {
			return this.beats.length == 0 ? this.length : this.beats[this.beats.length - 1];
		}

		/** (0.18.97) One of the questioning holds: nobody dies of it. */
		public boolean questioning() {
			return this.strike == Strike.HOLD;
		}

		public static Kind of(int ordinal) {
			Kind[] all = values();
			return all[Math.floorMod(ordinal, all.length)];
		}
	}

	/** Name words for each kind, checked in this order (a poleaxe is a polearm before it's an axe). */
	private static final String[] POLEARM_WORDS = {"glaive", "halberd", "scythe", "naginata", "bardiche", "poleaxe", "pole_axe", "partisan",
		"guisarme", "voulge", "billhook"};
	private static final String[] STAFF_WORDS = {"staff", "quarterstaff", "bo_staff"};
	private static final String[] SPEAR_WORDS = {"spear", "lance", "pike", "trident", "javelin", "pilum"};
	private static final String[] DAGGER_WORDS = {"dagger", "knife", "kunai", "tanto", "dirk", "stiletto", "misericorde", "shiv", "athame", "kama"};
	private static final String[] GREATSWORD_WORDS = {"claymore", "longsword", "greatsword", "great_sword", "broadsword", "zweihander", "flamberge",
		"bastard_sword"};
	private static final String[] SWORD_WORDS = {"sword", "katana", "saber", "sabre", "rapier", "cutlass", "scimitar", "falchion", "machete",
		"wakizashi", "ninjato", "blade", "estoc"};
	private static final String[] AXE_WORDS = {"axe", "hatchet", "cleaver", "tomahawk"};
	/** (0.15.114) Another mod's gun, by name (TaCZ's and ours are known by their gun id). */
	private static final String[] GUN_WORDS = {"musket", "blunderbuss", "flintlock", "pistol", "rifle", "revolver", "arquebus", "handgun",
		"shotgun"};
	private static final String[] BLUNT_WORDS = {"mace", "hammer", "cudgel", "club", "flail", "morningstar", "morning_star", "maul", "sledge",
		"mattock", "pickaxe", "scepter", "sceptre", "excavator", "battlesign"};

	/** The finisher being played: which, since when (game time), and on whom (entity id). Synced to everyone, not saved. */
	public record Finish(int kind, long start, int target) {
		public static final StreamCodec<ByteBuf, Finish> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Finish::kind,
			ByteBufCodecs.VAR_LONG, Finish::start, ByteBufCodecs.VAR_INT, Finish::target, Finish::new);

		public Kind type() {
			return Kind.of(this.kind);
		}
	}

	public static final AttachmentType<Finish> FINISHING = AttachmentRegistry.create(JaymosPowers.id("finishing"),
		builder -> builder.syncWith(Finish.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Rooted while it plays: walking and jumping gone (the finisher, and a downed player being finished). */
	static final Identifier ROOT_ID = JaymosPowers.id("finisher_root"), JUMP_ID = JaymosPowers.id("finisher_jump");

	/**
	 * Use held on a downed one: on whom, since when, and when use last came; (0.15.110) bare-handed, the taps so far and
	 * when this press began.
	 */
	/** (0.17.7) Attack held on someone: who, and since when it counted (they were helpless, and you in place). */
	private static final class Hold {
		final LivingEntity body;
		long start;
		/** (0.18.97) When the choice (question or kill) was put to them, or -1 while the hold still fills. */
		long choosing = -1L;

		Hold(LivingEntity body, long start) {
			this.body = body;
			this.start = start;
		}
	}

	/** (0.17.7) Client → server: attack is held on this entity (its id), or let go / aimed away (-1). */
	public record Execute(int target) implements CustomPacketPayload {
		public static final Type<Execute> TYPE = new Type<>(JaymosPowers.id("finisher_execute"));
		public static final StreamCodec<ByteBuf, Execute> CODEC = ByteBufCodecs.VAR_INT.map(Execute::new, Execute::target);

		@Override
		public Type<Execute> type() {
			return TYPE;
		}
	}

	/** (0.18.97) Server → client: the hold on this body is full; question (turn left) or kill (turn right). -1: the choice is gone. */
	public record Choose(int target) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = new Type<>(JaymosPowers.id("finisher_choose"));
		public static final StreamCodec<ByteBuf, Choose> CODEC = ByteBufCodecs.VAR_INT.map(Choose::new, Choose::target);

		@Override
		public Type<Choose> type() {
			return TYPE;
		}
	}

	/** (0.18.97) Client → server: what was chosen for the body (entity id): question them, or kill them. */
	public record Pick(int target, boolean question) implements CustomPacketPayload {
		public static final Type<Pick> TYPE = new Type<>(JaymosPowers.id("finisher_pick"));
		public static final StreamCodec<ByteBuf, Pick> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Pick::target, ByteBufCodecs.BOOL,
			Pick::question, Pick::new);

		@Override
		public Type<Pick> type() {
			return TYPE;
		}
	}

	private static final boolean KINGDOMS = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	private static final java.lang.reflect.@Nullable Method KINGDOMS_RANK;

	static {
		java.lang.reflect.Method rank = null;
		if (KINGDOMS) {
			try {
				rank = Class.forName("com.jaymoskingdoms.Skills").getMethod("rank", Player.class, String.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				rank = null;
			}
		}
		KINGDOMS_RANK = rank;
	}

	/** A finisher playing: who, on whom. */
	private record Playing(ServerPlayer player, LivingEntity body, boolean downed) {
	}

	private static final Map<UUID, Hold> HOLDING = new HashMap<>();
	private static final Map<UUID, Playing> PLAYING = new HashMap<>();
	/** The bodies being finished, by entity UUID, and by whom. */
	private static final Map<UUID, UUID> TARGETED = new HashMap<>();

	private Finishers() {
	}

	public static void register() {
		// (0.16.8) the symbiote's own executions play out on their own loop, begun from the hold below
		com.jaymospowers.power.web.WebExecutions.register();
		// (0.18.46) and a Z Fighter's bare hands
		com.jaymospowers.power.zfighter.ZFighterExecutions.register();
		// (0.17.7) the hold is attack's: the client says when it's held on someone and when it's let go
		PayloadTypeRegistry.serverboundPlay().register(Execute.TYPE, Execute.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Execute.TYPE, (payload, ctx) -> attackHeld(ctx.player(), payload.target()));
		// (0.18.97) question or kill
		PayloadTypeRegistry.clientboundPlay().register(Choose.TYPE, Choose.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Pick.TYPE, Pick.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Pick.TYPE, (payload, ctx) -> picked(ctx.player(), payload.target(), payload.question()));
		PlayerQuestioning.register();
		// (busy: a use still held from before does nothing to them either)
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && questioning(sp)) {
				// (0.18.97) use while you hold them for questioning: you let them go
				questionDone(sp, false);
			}
			return busy(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		// rooted and busy: nothing else from your hands while it plays
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && questioning(sp)) {
				// (0.18.97) a blow while you hold them for questioning: you've heard enough, and finish them
				questionDone(sp, true);
			}
			return busy(player) ? InteractionResult.FAIL : InteractionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> busy(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> busy(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && questioning(sp)) {
				questionDone(sp, false);
			}
			return busy(player) ? InteractionResult.FAIL : InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			holds(server);
			plays(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOLDING.clear();
			PLAYING.clear();
			TARGETED.clear();
		});
	}

	// ---- queries (both sides: the attachment is synced) ------------------------------------------------------------------

	/** The finisher {@code player} is playing, or null. */
	public static @Nullable Finish of(Entity player) {
		return player.getAttached(FINISHING);
	}

	/** Whether {@code player} is in the middle of a finisher (rooted, hands busy). */
	public static boolean busy(Entity player) {
		return player.hasAttached(FINISHING) || com.jaymospowers.power.web.WebExecutions.busy(player);
	}

	/** Server side: whether someone is finishing {@code body} just now (it lies still for it: {@code Downed.step}). */
	public static boolean targeted(Entity body) {
		return TARGETED.containsKey(body.getUUID()) || com.jaymospowers.power.web.WebExecutions.targeted(body);
	}

	/** The finisher a player holding {@code stack} plays. */
	public static Kind kind(ItemStack stack) {
		if (stack.isEmpty()) {
			return Kind.FIST;
		}
		// (0.15.148) the flail (before "flail" makes it a club) and the whip have their own
		if (com.jaymospowers.armory.Lash.isFlail(stack)) {
			return Kind.FLAIL;
		}
		if (com.jaymospowers.armory.Lash.isWhip(stack)) {
			return Kind.WHIP;
		}
		if (com.jaymospowers.ground.FirstTools.rock(stack)) {
			// (0.17.181) a loose rock is a rock, whatever its name says
			return Kind.ROCK;
		}
		if (gun(stack)) {
			return Kind.GUN;
		}
		if (stack.getItem() instanceof CrossbowItem) {
			return Kind.CROSSBOW;
		}
		if (stack.getItem() instanceof BowItem) {
			return Kind.BOW;
		}
		if (stack.is(Items.TRIDENT) || stack.is(ItemTags.SPEARS)) {
			return Kind.SPEAR;
		}
		if (stack.is(Items.MACE)) {
			return Kind.BLUNT;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (path.contains("crossbow")) {
			return Kind.CROSSBOW;
		}
		if (path.endsWith("bow") && !path.endsWith("elbow") || path.contains("longbow") || path.contains("shortbow")) {
			return Kind.BOW;
		}
		if (any(path, POLEARM_WORDS)) {
			return Kind.POLEARM;
		}
		if (any(path, STAFF_WORDS)) {
			return Kind.STAFF;
		}
		if (any(path, SPEAR_WORDS)) {
			return Kind.SPEAR;
		}
		if (any(path, DAGGER_WORDS)) {
			return Kind.DAGGER;
		}
		if (any(path, GREATSWORD_WORDS)) {
			return Kind.GREATSWORD;
		}
		if (stack.is(ItemTags.SWORDS) || any(path, SWORD_WORDS)) {
			return Kind.SWORD;
		}
		if (stack.is(ItemTags.PICKAXES)) {
			return Kind.BLUNT;
		}
		if (stack.is(ItemTags.AXES) || any(path, AXE_WORDS)) {
			return Kind.AXE;
		}
		if (any(path, BLUNT_WORDS)) {
			return Kind.BLUNT;
		}
		return Kind.FIST;
	}

	/** (0.15.114) A gun: TaCZ's (any with a gun id), the Void Armory's flintlock, or another mod's by name. */
	static boolean gun(ItemStack stack) {
		if (!TaczGuns.gunId(stack).isEmpty() || ArsenalFlintlock.FLINTLOCK != null && stack.is(ArsenalFlintlock.FLINTLOCK)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.endsWith("_gun") || any(path, GUN_WORDS);
	}

	/**
	 * (0.15.114) Whether {@code player}'s {@code stack} has what its finisher shoots: a round in the gun (TaCZ's, or the
	 * Void Armory flintlock unspent; another mod's gun we can't read, so yes), an arrow for the bow, the crossbow loaded
	 * or an arrow for it. Anything else needs nothing.
	 */
	static boolean loaded(Player player, ItemStack stack, Kind kind) {
		return switch (kind) {
			case GUN -> !TaczGuns.gunId(stack).isEmpty() ? TaczGuns.ammo(stack) > 0
				: !(ArsenalFlintlock.FLINTLOCK != null && stack.is(ArsenalFlintlock.FLINTLOCK)) || !Boolean.TRUE.equals(stack.get(ArsenalFlintlock.SPENT));
			case BOW, CROSSBOW -> player.hasInfiniteMaterials() || charged(stack) || !player.getProjectile(stack).isEmpty();
			default -> true;
		};
	}

	/**
	 * (0.15.116) The move {@code player} plays with {@code stack}: its kind's, but a gun with no round in it (Andy: "with no
	 * ammunition it should just run though with the blade") runs them through with the bayonet, the sword's Run Through.
	 */
	static @Nullable Kind move(Player player, ItemStack stack) {
		if (stack.isEmpty()) {
			return Kind.FIST;
		}
		Kind kind = kind(stack);
		if (kind == Kind.FIST) {
			// (0.15.146) not a weapon: a tool that wears slams (a fishing rod, a shovel); a torch or a loaf finishes nobody
			return stack.isDamageableItem() ? Kind.SLAM : null;
		}
		if (kind == Kind.ROCK) {
			// (0.17.181) anyone can bring a rock down on someone
			return kind;
		}
		if (!weaponsUnlocked(player)) {
			return basic(kind);
		}
		if (kind == Kind.GUN && !loaded(player, stack, kind)) {
			return Kind.SWORD;
		}
		// (0.15.146) a bow with no arrow is only something to hit them with
		return (kind == Kind.BOW || kind == Kind.CROSSBOW) && !loaded(player, stack, kind) ? Kind.SLAM : kind;
	}

	/** (0.15.146) Everyone's move with a weapon of {@code kind}: a blade stabs through, the rest (axes too) slam. */
	static Kind basic(Kind kind) {
		return switch (kind) {
			case SWORD, GREATSWORD, DAGGER, SPEAR, POLEARM -> Kind.IMPALE;
			default -> Kind.SLAM;
		};
	}

	/** (0.15.146) A tool not made for hitting anyone: a bow, a crossbow, a fishing rod, a shovel, a hoe, shears. */
	static boolean improvised(ItemStack stack) {
		Kind kind = kind(stack);
		// (0.16.1) Andy: "untrained slams w the whip damage it": a whip's no club (its Garrotte needs Executioner)
		return kind == Kind.WHIP || kind == Kind.BOW || kind == Kind.CROSSBOW || kind == Kind.FIST || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
	}

	/** (0.15.146) A basic move's blow wears what struck it: a tool heavily ({@link #IMPROVISED_WEAR}), a weapon as a hit. */
	static void wear(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty() || !stack.isDamageableItem()) {
			return;
		}
		int wear = improvised(stack) ? Math.max(IMPROVISED_WEAR_MIN, Math.round(stack.getMaxDamage() * IMPROVISED_WEAR)) : WEAPON_WEAR;
		stack.hurtAndBreak(wear, player, EquipmentSlot.MAINHAND);
	}

	/**
	 * (0.15.146) Where {@code body}'s head is, about: someone downed lying on the ground (DownedPose lays a crawler face
	 * down with its head ahead, the rest on their backs with it behind), else its head's hitbox ({@link LimbBoxes}), else
	 * its eyes. Both sides (the client's finisher kneels to it).
	 */
	public static Vec3 headAt(LivingEntity body) {
		if (!(body instanceof Player) && Downed.isDowned(body) && !Carry.isCarried(body)) {
			Vec3 forward = Vec3.directionFromRotation(0.0F, body.yBodyRot);
			return body.position().add(forward.scale((Downed.crawls(body) ? 1.0 : -1.0) * DOWNED_HEAD * body.getDimensions(Pose.STANDING).height()));
		}
		for (LimbBoxes.Box box : LimbBoxes.of(body)) {
			if (box.part() == Dismember.HEAD) {
				return box.center();
			}
		}
		return body.getEyePosition();
	}

	/**
	 * (0.15.146) Andy: "the player should be in the correct area for a neck snap, like near the head on any humanoid":
	 * your feet within {@link #HEAD_REACH} of a person's head across the ground, and nearer it than their feet. Anything
	 * not built like a person can be taken from anywhere.
	 */
	static boolean atHead(Player player, LivingEntity body) {
		if (BodyPlan.of(body) != BodyPlan.HUMANOID) {
			return true;
		}
		Vec3 head = headAt(body);
		double dx = head.x - player.getX(), dz = head.z - player.getZ();
		if (dx * dx + dz * dz > HEAD_REACH * HEAD_REACH) {
			return false;
		}
		// the far end: the feet of one lying down (through the middle from the head), under one standing
		Vec3 middle = body.position();
		Vec3 feet = middle.add(middle.subtract(head).multiply(1.0, 0.0, 1.0).scale(DOWNED_FEET / DOWNED_HEAD));
		return head.distanceToSqr(player.getX(), head.y, player.getZ()) <= feet.distanceToSqr(player.getX(), feet.y, player.getZ());
	}

	private static boolean charged(ItemStack stack) {
		ChargedProjectiles charge = stack.get(DataComponents.CHARGED_PROJECTILES);
		return charge != null && !charge.isEmpty();
	}

	/** (0.15.114) The shot's spent: the gun's round, the crossbow's bolt, or an arrow (Infinity and creative keep theirs). */
	static void spend(ServerLevel level, Player player, ItemStack stack, Kind kind) {
		switch (kind) {
			case GUN -> {
				if (!TaczGuns.spendRound(stack) && ArsenalFlintlock.FLINTLOCK != null && stack.is(ArsenalFlintlock.FLINTLOCK)) {
					stack.set(ArsenalFlintlock.SPENT, true);
				}
			}
			case BOW, CROSSBOW -> {
				if (kind == Kind.CROSSBOW && charged(stack)) {
					stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
					return;
				}
				if (player.hasInfiniteMaterials()) {
					return;
				}
				ItemStack arrow = player.getProjectile(stack);
				if (!arrow.isEmpty()) {
					arrow.shrink(EnchantmentHelper.processAmmoUse(level, stack, arrow, 1));
				}
			}
			default -> {
			}
		}
	}

	private static boolean any(String path, String[] words) {
		for (String word : words) {
			if (path.contains(word)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * (0.15.110) Whether {@code player} has the weapons' finishers: Andy, "make all the other executions unlocked in the combat
	 * arts tree replacing the current executioner function". Kingdoms' Executioner perk (read by reflection, as Freerun
	 * reads its perks); everyone in creative, and everyone if Kingdoms isn't loaded.
	 */
	public static boolean weaponsUnlocked(Player player) {
		if (player.isCreative() || !KINGDOMS) {
			return true;
		}
		if (KINGDOMS_RANK == null) {
			return false;
		}
		try {
			return (int) KINGDOMS_RANK.invoke(null, player, UNLOCK_PERK) > 0;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/**
	 * (0.15.114) Whether {@code body} is down for a finisher: downed, or (Andy: "crippled enemies should be executable too")
	 * crippled, off its feet: Darktide's crawl, both legs broken ({@link Breaks#crawlOnly}), or a leg cut off. (0.18.71) Or
	 * tripped by a bolas, face down with its legs bound ({@code Tripped}).
	 */
	public static boolean helpless(LivingEntity body) {
		return Downed.isDowned(body) || DarktideStatus.crippled(body) || Breaks.crawlOnly(body) || com.jaymospowers.core.wounds.Tripped.tripped(body)
			|| (Dismember.missing(body) & (Dismember.LEFT_LEG | Dismember.RIGHT_LEG)) != 0;
	}

	/** Whether {@code player} may finish {@code body}: downed, not themselves, not someone's tamed animal, and not already being finished. */
	static boolean finishable(Player player, Entity entity) {
		return entity instanceof LivingEntity body && body != player && body.isAlive() && helpless(body) && !Carry.isCarried(body)
			&& !(body instanceof TamableAnimal tame && tame.isTame()) && !(body instanceof AbstractHorse horse && horse.isTamed())
			&& !targeted(body);
	}

	// ---- the hold ---------------------------------------------------------------------------------------------------------

	/** (0.17.7) {@code player}'s attack is held on the entity {@code id} now (-1: let go). */
	private static void attackHeld(ServerPlayer player, int id) {
		if (id < 0 || player.isSpectator() || Downed.isDowned(player) || busy(player)) {
			HOLDING.remove(player.getUUID());
			return;
		}
		Hold held = HOLDING.get(player.getUUID());
		if (held != null && held.body.getId() == id) {
			return;
		}
		if (player.level().getEntity(id) instanceof LivingEntity body && body != player) {
			HOLDING.put(player.getUUID(), new Hold(body, player.level().getGameTime()));
		} else {
			HOLDING.remove(player.getUUID());
		}
	}

	private static void holds(MinecraftServer server) {
		if (HOLDING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Hold>> it = HOLDING.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Hold hold = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !(player.level() instanceof ServerLevel level) || hold.body.level() != level || !player.isAlive()
				|| hold.body.isRemoved() || !hold.body.isAlive() || Downed.isDowned(player) || busy(player)) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			// (0.18.97) Quiet Word: one on their feet who hasn't noticed you, taken from behind
			boolean choke = !finishable(player, hold.body) && chokeable(player, hold.body) && unaware(player, hold.body);
			if (!player.isShiftKeyDown() || !player.isWithinEntityInteractionRange(hold.body, SLACK) || !finishable(player, hold.body) && !choke) {
				// (still on their feet, or not yet in reach: it counts from when they're down in front of you)
				hold.start = now;
				continue;
			}
			if (choke) {
				if (now - hold.start >= HOLD_TICKS) {
					it.remove();
					begin(level, player, hold.body, Kind.Q_CHOKE);
				} else if (now - hold.start >= BAR_AFTER) {
					int filled = Math.round(HOLD_BAR * (now - hold.start) / (float) HOLD_TICKS);
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.holding", hold.body.getDisplayName(),
						"|".repeat(filled) + ".".repeat(HOLD_BAR - filled)).withStyle(ChatFormatting.DARK_GRAY));
				}
				continue;
			}
			ItemStack stack = player.getMainHandItem();
			// (0.16.8) with the symbiote suit showing, empty hands are its own: no neck snap, one of WebExecutions' moves
			boolean suit = stack.isEmpty() && com.jaymospowers.power.web.WebExecutions.applies(player);
			// (0.18.46) and a Z Fighter's are his own: no neck snap, one of ZFighterExecutions' moves, from anywhere round them
			boolean fighter = stack.isEmpty() && !suit && com.jaymospowers.power.zfighter.ZFighterExecutions.applies(player);
			boolean bare = stack.isEmpty() && !suit && !fighter;
			if (!suit && move(player, stack) == null) {
				// (0.15.110) a torch or a loaf finishes nobody
				hold.start = now;
				continue;
			}
			if (bare && com.jaymospowers.core.wounds.Malfunction.is(hold.body)) {
				// (0.19.32) a machine has no neck to snap: only a weapon finishes it
				if ((now - hold.start) % HEAD_HINT_EVERY == BAR_AFTER) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.machine", hold.body.getDisplayName()).withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			if (bare && !atHead(player, hold.body)) {
				// (0.15.146) the neck snap is done kneeling at their head
				if ((now - hold.start) % HEAD_HINT_EVERY == BAR_AFTER) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.head", hold.body.getDisplayName()).withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			long held = now - hold.start;
			if (held >= HOLD_TICKS && hold.choosing < 0L && mayQuestion(player, hold.body)) {
				// (0.18.97) Andy: "holding right click for the execution now has you select either question or kill dragging
				// either left or right": the hold's full; the client shows the choice and says which way you turned
				hold.choosing = now;
				ServerPlayNetworking.send(player, new Choose(hold.body.getId()));
				continue;
			}
			if (hold.choosing >= 0L) {
				if (now - hold.choosing > CHOOSE_TICKS) {
					// (you thought better of it)
					it.remove();
					ServerPlayNetworking.send(player, new Choose(-1));
				}
				continue;
			}
			if (held >= HOLD_TICKS) {
				it.remove();
				kill(level, player, hold.body);
				continue;
			}
			if (held >= BAR_AFTER) {
				int filled = Math.round(HOLD_BAR * held / (float) HOLD_TICKS);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.holding", hold.body.getDisplayName(),
					"|".repeat(filled) + ".".repeat(HOLD_BAR - filled)).withStyle(ChatFormatting.DARK_RED));
			}
		}
	}

	/** The kill the hold was for: the suit's, a Z Fighter's, or the move for what's in hand. */
	private static void kill(ServerLevel level, ServerPlayer player, LivingEntity body) {
		ItemStack stack = player.getMainHandItem();
		boolean suit = stack.isEmpty() && com.jaymospowers.power.web.WebExecutions.applies(player);
		if (suit) {
			com.jaymospowers.power.web.WebExecutions.begin(level, player, body);
			return;
		}
		if (stack.isEmpty() && com.jaymospowers.power.zfighter.ZFighterExecutions.applies(player)) {
			begin(level, player, body, com.jaymospowers.power.zfighter.ZFighterExecutions.pick(player));
			return;
		}
		// (0.15.146) without Executioner, everyone's basic move; (0.15.114) with it, a bow with nothing to shoot slams
		Kind kind = move(player, stack);
		if (kind != null) {
			begin(level, player, body, kind);
		}
	}

	// ---- (0.18.97) question or kill ---------------------------------------------------------------------------------------

	/**
	 * (0.18.97) Whether the full hold on {@code body} puts the choice to {@code player}: Andy, "you can question downed
	 * humanoids, unlocked in the tree ... goblins, orcs and players count". Kingdoms' Interrogation perk (Will); a player
	 * always can be questioned, and the rest only if Kingdoms says they can talk ({@code Questioning.questionable}).
	 */
	static boolean mayQuestion(ServerPlayer player, LivingEntity body) {
		if (!KINGDOMS || KINGDOMS_RANK == null || player.isSpectator()) {
			return false;
		}
		if (!player.isCreative() && !questionPerk(player, ROUGH_PERK) && !questionPerk(player, QUIET_PERK) && !questionPerk(player, WILL_PERK)) {
			return false;
		}
		return body instanceof Player || KingdomsQuestioning.questionable(body);
	}

	/** (0.18.97) {@code player} has the Kingdoms perk {@code key} (one of the questioning perks). */
	public static boolean questionPerk(Player player, String key) {
		if (KINGDOMS_RANK == null) {
			return false;
		}
		try {
			return (int) KINGDOMS_RANK.invoke(null, player, key) > 0;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/**
	 * (0.18.97) Quiet Word: {@code body} is one who talks, on their feet, and {@code player} has Quiet Word and stands behind
	 * them. (Also asked on the client, so the hold doesn't swing at them.)
	 */
	public static boolean chokeable(Player player, Entity entity) {
		if (!(entity instanceof LivingEntity body) || body instanceof Player || !body.isAlive() || helpless(body) || targeted(body) || Carry.isCarried(body)
			|| !KingdomsQuestioning.questionable(body) || !questionPerk(player, QUIET_PERK)) {
			return false;
		}
		float toPlayer = (float) (Mth.atan2(player.getZ() - body.getZ(), player.getX() - body.getX()) * Mth.RAD_TO_DEG) - 90.0F;
		return Math.abs(Mth.wrapDegrees(toPlayer - body.getYHeadRot())) >= BEHIND_DEGREES;
	}

	/** (0.18.97) {@code body} hasn't noticed {@code player}: not after them, and not hurt by them lately. */
	static boolean unaware(Player player, LivingEntity body) {
		return !(body instanceof Mob mob && mob.getTarget() == player) && body.getLastHurtByMob() != player;
	}

	/** (0.18.97) A questioning is over, however it ended: Kingdoms remembers it, and a player's screens close. */
	private static void questionEnded(ServerPlayer player, LivingEntity body, boolean spared) {
		KingdomsQuestioning.ended(player, body, spared);
		PlayerQuestioning.ended(player, body);
	}

	/** (0.18.97) The client's pick for the body it was asked about: question or kill. */
	private static void picked(ServerPlayer player, int target, boolean question) {
		Hold hold = HOLDING.get(player.getUUID());
		if (hold == null || hold.choosing < 0L || hold.body.getId() != target || !(player.level() instanceof ServerLevel level)
			|| !hold.body.isAlive() || !finishable(player, hold.body)) {
			return;
		}
		HOLDING.remove(player.getUUID());
		if (question) {
			begin(level, player, hold.body, questioner(player));
		} else {
			kill(level, player, hold.body);
		}
	}

	/** (0.18.97) How {@code player} holds someone for questioning, by what's in hand. */
	static Kind questioner(Player player) {
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty() || com.jaymospowers.armory.Lash.isWhip(stack)) {
			return Kind.Q_GRIP;
		}
		return switch (kind(stack)) {
			case BOW, CROSSBOW, GUN -> Kind.Q_AIM;
			case SWORD, GREATSWORD, DAGGER, AXE, SPEAR, POLEARM -> Kind.Q_BLADE;
			default -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) ? Kind.Q_BLADE : Kind.Q_BLUNT;
		};
	}

	/** (0.18.97) Whether {@code player} is holding someone for questioning just now. */
	public static boolean questioning(Entity player) {
		Finish finish = of(player);
		return finish != null && finish.type().questioning();
	}

	/**
	 * (0.18.97) The questioning is over (Kingdoms' "Finish them" or "Let them go", a blow, or use): {@code kill} plays the
	 * move for what's in hand; else they're let go where they lie.
	 */
	public static void questionDone(ServerPlayer player, boolean kill) {
		Playing p = PLAYING.get(player.getUUID());
		if (p == null || !questioning(player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		PLAYING.remove(player.getUUID());
		end(player, p.body());
		questionEnded(player, p.body(), !kill);
		if (kill && p.body().isAlive() && finishable(player, p.body())) {
			kill(level, player, p.body());
		}
	}

	// ---- playing it out ---------------------------------------------------------------------------------------------------

	private static void begin(ServerLevel level, ServerPlayer player, LivingEntity body, Kind kind) {
		player.setAttached(FINISHING, new Finish(kind.ordinal(), level.getGameTime(), body.getId()));
		PLAYING.put(player.getUUID(), new Playing(player, body, Downed.isDowned(body)));
		TARGETED.put(body.getUUID(), player.getUUID());
		player.stopUsingItem();
		player.setSprinting(false);
		if (kind == Kind.FLAIL) {
			// (0.15.148) the ball's out on its chain (drawn by LashRenderer), not in the hand
			com.jaymospowers.armory.Lash.flailThrown(player, true);
		}
		root(player, true);
		if (body instanceof Player || !Downed.isDowned(body)) {
			// (0.15.114) a crippled one is held where it is too
			root(body, true);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher." + kind.name().toLowerCase(java.util.Locale.ROOT),
			body.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
		if (kind.strike == Strike.KI) {
			// (0.18.46) a Z Fighter's: the ki paid, and his own opening
			com.jaymospowers.power.zfighter.ZFighterExecutions.begin(level, player, body, kind);
			return;
		}
		if (kind == Kind.GUN && TaczGuns.gunSound(player, TaczGuns.gunId(player.getMainHandItem()), "fire_select", 16, 1.0F, 1.0F)) {
			// (0.15.114) the gun's own cock
			return;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), opening(kind), SoundSource.PLAYERS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
	}

	private static void plays(MinecraftServer server) {
		if (PLAYING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Playing>> it = PLAYING.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Playing p = e.getValue();
			ServerPlayer player = p.player();
			LivingEntity body = p.body();
			Finish finish = of(player);
			boolean online = server.getPlayerList().getPlayer(e.getKey()) == player;
			if (finish == null || !online || !(player.level() instanceof ServerLevel level) || !player.isAlive() || Downed.isDowned(player)) {
				it.remove();
				end(player, body);
				continue;
			}
			Kind kind = finish.type();
			int t = (int) (level.getGameTime() - finish.start());
			// (0.15.114) a crippled one stays held till it's over; a downed one that gets up is spared
			boolean present = body.isAlive() && !body.isRemoved() && body.level() == level && (!p.downed() || Downed.isDowned(body)) && !Carry.isCarried(body)
				&& player.distanceTo(body) <= MAX_DISTANCE;
			if (t < kind.kill() && !present) {
				// (up, carried off or gone: spared)
				it.remove();
				end(player, body);
				if (kind.questioning()) {
					questionEnded(player, body, true);
				}
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.stopped", body.getDisplayName()).withStyle(ChatFormatting.GRAY));
				continue;
			}
			// rooted where they stand, facing the body
			player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
			if (!p.downed() && body instanceof Mob mob) {
				// (0.15.114) a crippled mob, held: no crawling off, no fighting back
				mob.getNavigation().stop();
				mob.setTarget(null);
				mob.setDeltaMovement(0.0, Math.min(0.0, mob.getDeltaMovement().y), 0.0);
			}
			if (kind == Kind.WHIP && t == Kind.WHIP_CRACK && present) {
				// (0.15.148) the lash cracks round the neck
				level.playSound(null, body.getX(), body.getY() + 0.3, body.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.9F, 1.9F);
				level.playSound(null, body.getX(), body.getY() + 0.3, body.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.1F);
			}
			if (kind.strike == Strike.KI) {
				// (0.18.46) the ki gathering, the film, the beam held on them
				com.jaymospowers.power.zfighter.ZFighterExecutions.tick(level, player, body, kind, t, present);
			}
			if (kind.questioning() && t == QUESTION_OPEN && present) {
				// (0.18.97) Andy: "it opens the talk menu with the relevant options"; a player held so hears it from you
				if (body instanceof ServerPlayer held) {
					// (0.18.97) Andy: "can you make one?": their own screen to answer from, and yours to ask
					held.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.questioned", player.getDisplayName()).withStyle(ChatFormatting.RED));
					PlayerQuestioning.open(player, held);
				} else {
					KingdomsQuestioning.open(player, body, kind == Kind.Q_CHOKE);
				}
			}
			if (kind.questioning() && body instanceof Mob mob) {
				// (held: they stop what they were about)
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
			if (kind.questioning() && t >= kind.length - 1 && present) {
				// (held as long as you may: you let them go)
				it.remove();
				end(player, body);
				questionEnded(player, body, true);
				continue;
			}
			for (int beat : kind.beats) {
				if (t == beat && present) {
					blow(level, player, body, kind, beat == kind.kill());
				}
			}
			for (int i = 0; i < kind.after.length; i++) {
				if (t == kind.after[i]) {
					tug(level, player, body, i == kind.after.length - 1);
				}
			}
			if (t >= kind.length) {
				it.remove();
				end(player, body);
			}
		}
	}

	/** One of the finisher's blows: the sound and the gore, the body jolting; the last one is their death. */
	private static void blow(ServerLevel level, ServerPlayer player, LivingEntity body, Kind kind, boolean kill) {
		double x = body.getX(), y = body.getY() + 0.3, z = body.getZ();
		var random = level.getRandom();
		float pitch = 0.85F + random.nextFloat() * 0.3F;
		switch (kind.strike) {
			case CUT -> {
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, pitch * 0.8F);
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, pitch);
				BladeBlood.splash(body, kill ? 12.0F : 6.0F);
			}
			case STAB -> {
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9F, pitch * 1.1F);
				level.playSound(null, x, y, z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.5F, pitch * 1.3F);
				BladeBlood.splash(body, kill ? 9.0F : 4.0F);
			}
			case PIERCE -> {
				level.playSound(null, x, y, z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, pitch * 0.8F);
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, pitch * 0.9F);
				BladeBlood.splash(body, kill ? 10.0F : 6.0F);
			}
			case CRUSH -> {
				level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, kill ? 1.0F : 0.7F, pitch * 0.9F);
				level.playSound(null, x, y, z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.6F, pitch * 0.6F);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, body.getBlockStateOn().isAir() ? Blocks.DIRT.defaultBlockState()
					: body.getBlockStateOn()), x, body.getY() + 0.05, z, kill ? 30 : 14, 0.5, 0.05, 0.5, 0.15);
				if (kill) {
					BladeBlood.crushed(body);
				} else {
					BladeBlood.blunt(body, 7.0F);
				}
			}
			case SHOT -> {
				spend(level, player, player.getMainHandItem(), kind);
				level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, pitch);
				level.playSound(null, x, y, z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 1.0F, pitch * 0.8F);
				level.sendParticles(ParticleTypes.CRIT, x, y + 0.2, z, 12, 0.2, 0.2, 0.2, 0.3);
				BladeBlood.splash(body, 6.0F);
			}
			case BOLT -> {
				spend(level, player, player.getMainHandItem(), kind);
				level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, pitch);
				level.playSound(null, x, y, z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 1.0F, pitch * 0.7F);
				level.sendParticles(ParticleTypes.CRIT, x, y + 0.2, z, 16, 0.2, 0.2, 0.2, 0.35);
				BladeBlood.splash(body, 7.0F);
			}
			case BLAST -> {
				ItemStack gun = player.getMainHandItem();
				spend(level, player, gun, kind);
				// the muzzle at arm's length toward their head, the ball's smoke into them
				Vec3 eye = player.getEyePosition();
				Vec3 head = new Vec3(x, y + 0.1, z);
				Vec3 dir = head.subtract(eye).normalize();
				Vec3 muzzle = eye.add(dir.scale(Math.min(1.0, eye.distanceTo(head) * 0.6)));
				level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE0A0), muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.02);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 10, 0.12, 0.12, 0.12, 0.03);
				for (double d = 0.0, len = muzzle.distanceTo(head); d < len; d += 0.3) {
					Vec3 at = muzzle.add(dir.scale(d));
					level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
				level.sendParticles(ParticleTypes.CRIT, x, y + 0.1, z, 14, 0.15, 0.15, 0.15, 0.3);
				String id = TaczGuns.gunId(gun);
				if (!TaczGuns.gunSound(player, id, "shoot_3p", SHOT_HEARD, 1.0F, 0.95F + random.nextFloat() * 0.1F)) {
					level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.7F);
					level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
				}
				if (!TaczGuns.gunSound(body, id, "flesh_hit", 16, 1.0F, 0.9F)) {
					level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, pitch * 0.8F);
				}
				BladeBlood.splash(body, 10.0F);
				Shakes.around(level, muzzle, AROUND_RANGE, SHOT_AROUND_SHAKE);
			}
			// (0.18.46) a Z Fighter's: his ki, his fists
			case KI -> com.jaymospowers.power.zfighter.ZFighterExecutions.blow(level, player, body, kind, kill);
			case SNAP -> {
				if (kill) {
					level.playSound(null, x, y, z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1.0F, 0.5F);
					level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 0.6F);
				} else {
					// (0.15.114) the hold tightens: they struggle, the neck strains
					level.playSound(null, x, y, z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.35F, 0.85F + random.nextFloat() * 0.1F);
					level.playSound(null, x, y, z, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.7F, 0.7F);
				}
			}
		}
		if (kind == Kind.ROCK) {
			// (0.17.181) stone on bone: the rock's crack, and chips of it
			level.playSound(null, x, y, z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, kill ? 1.0F : 0.8F, pitch * 0.7F);
			if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem rock) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rock.getBlock().defaultBlockState()), x, y + 0.2, z, kill ? 16 : 8,
					0.15, 0.1, 0.15, 0.1);
			}
			if (kill && !player.hasInfiniteMaterials()) {
				// (0.17.181) Andy: "the rock is used up": it breaks on the last blow
				player.getMainHandItem().shrink(1);
			}
		}
		if (kind == Kind.SLAM || kind == Kind.IMPALE) {
			// (0.15.146) Andy: "using tools like a bow or fishing rod heavily damages them"
			wear(player);
		}
		// (the body jolts with it; no damage: the last blow is the death outright)
		level.broadcastDamageEvent(body, level.damageSources().playerAttack(player));
		Shakes.send(player, kill ? KILL_SHAKE : BEAT_SHAKE, body.position());
		if (body instanceof ServerPlayer victim) {
			Shakes.send(victim, kill ? KILL_SHAKE : BEAT_SHAKE, player.position());
		}
		if (!kill) {
			return;
		}
		if (kind.strike == Strike.CRUSH) {
			Shakes.around(level, body.position(), AROUND_RANGE, AROUND_SHAKE);
		}
		if (kind == Kind.AXE && Dismember.canLoseParts(body)) {
			// the head comes off first (Kitchen's corpse goes without it)
			Dismember.sever(body, Dismember.HEAD, player);
		}
		Downed.execute(level, body, player);
		// (0.18.118) now and then the blade stays in them (less, the more you've done this)
		com.jaymospowers.armory.Lodged.killingStroke(level, player, body, kind);
		if (kind.strike == Strike.CRUSH || kind == Kind.GREATSWORD || com.jaymospowers.power.zfighter.ZFighterExecutions.crushes(kind)) {
			// (0.15.117) a spider under it is crushed flat: legs, no body (Kitchen); a greatsword is heavy enough too (Andy)
			com.jaymospowers.core.Crushing.mark(body);
		}
	}

	/**
	 * (0.15.146) Stabbed Through's blade, stuck in them: a tug against it (flesh giving, a jolt for you), and the last one
	 * wrenches it free with a gout of blood. Heard where they died, whatever's become of the body.
	 */
	private static void tug(ServerLevel level, ServerPlayer player, LivingEntity body, boolean free) {
		double x = body.getX(), y = body.getY() + 0.3, z = body.getZ();
		float pitch = 0.85F + level.getRandom().nextFloat() * 0.3F;
		if (free) {
			level.playSound(null, x, y, z, SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.8F, pitch * 0.7F);
			level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, pitch * 0.6F);
			BladeBlood.splash(body, 6.0F);
			Shakes.send(player, BEAT_SHAKE, new Vec3(x, y, z));
			return;
		}
		level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.7F, pitch * 0.55F);
		level.playSound(null, x, y, z, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.6F, pitch * 0.6F);
		Shakes.send(player, BEAT_SHAKE * 0.5F, new Vec3(x, y, z));
	}

	/** What's heard as it begins: steel drawn, a bowstring, a crossbow wound, cracking knuckles. */
	private static SoundEvent opening(Kind kind) {
		return switch (kind) {
			case BOW -> SoundEvents.ARROW_SHOOT;
			case CROSSBOW -> SoundEvents.CROSSBOW_LOADING_END.value();
			case FIST -> SoundEvents.ARMOR_EQUIP_LEATHER.value();
			case BLUNT, STAFF, SLAM, FLAIL -> SoundEvents.ARMOR_EQUIP_CHAIN.value();
			case ROCK -> SoundEvents.STONE_HIT;
			case WHIP -> SoundEvents.FISHING_BOBBER_THROW;
			default -> SoundEvents.ARMOR_EQUIP_IRON.value();
		};
	}

	/** It's over (played out, or stopped): the marks off, the roots gone. */
	private static void end(ServerPlayer player, LivingEntity body) {
		Finish finish = of(player);
		if (finish != null && finish.type() == Kind.FLAIL) {
			com.jaymospowers.armory.Lash.flailThrown(player, false);
		}
		if (finish != null && finish.type().strike == Strike.KI) {
			com.jaymospowers.power.zfighter.ZFighterExecutions.end(body);
		}
		player.removeAttached(FINISHING);
		root(player, false);
		TARGETED.remove(body.getUUID());
		// (unrooting one that never was is nothing)
		root(body, false);
	}

	private static void root(LivingEntity entity, boolean on) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance jump = entity.getAttribute(Attributes.JUMP_STRENGTH);
		if (on) {
			if (speed != null) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(ROOT_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			if (jump != null) {
				jump.addOrUpdateTransientModifier(new AttributeModifier(JUMP_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		} else {
			if (speed != null) {
				speed.removeModifier(ROOT_ID);
			}
			if (jump != null) {
				jump.removeModifier(JUMP_ID);
			}
		}
	}
}
