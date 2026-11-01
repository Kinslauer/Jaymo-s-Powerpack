package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.loader.api.FabricLoader;
import java.util.Set;

/**
 * Lineages, never picked: they come from the player's heritage in Jaymo's Kingdoms (read off its entity tag,
 * {@code jaymoskingdoms_heritage_<key>}): Warrior-born heritage makes a Warrior-born, Frostborn a Frost-born, Verdant a
 * Verdant, and every other heritage (or no Kingdoms at all) an Earthling. Fighters who picked one before keep it; a Writ
 * of Rebirth read while sneaking, or the rebirth wish, sets it back to what their blood says:
 * <ul>
 * <li><b>Earthling</b>: the training-born. TP 1.25x; Limit Break half the price and half the burn.</li>
 * <li><b>Warrior-born</b>: has a tail (sneak+Z on the ground wraps it round the waist or lets it out). Every defeat
 * grows him twice as much. Out under a full moon at night with the tail out, he becomes the <b>Great Ape</b>: three
 * times the size, huge strength, a beam from the mouth on V, and no techniques, flight or forms until the moon is gone
 * or dawn comes.</li>
 * <li><b>Verdant</b>: heals by itself, reaches further (stretching arms), grows into Giant forms instead of Super, and
 * sneak+B heals the creature or player you look at.</li>
 * <li><b>Frost-born</b>: evolution forms (Second, Final) that cost no ki to hold, then a Golden form past Final.</li>
 * </ul>
 * Each has its own aura colour.
 * <p>(0.11.101) The lineages are races anyone can be born to, Z Fighter or not: the heritage screen (Kingdoms) has all
 * four (Earthling, Warrior-born, Frostborn, Verdant). Without the Z Fighter power a Warrior-born still has the tail
 * (wrapped or let out with the heritage key, {@link #TOGGLE_TAG}) and the villagers' fear of it, a Verdant still mends,
 * regrows limbs, is green and has the stretch punch, and a Frost-born the ice prison ({@link RaceMoves}: left mouse held). What runs on ki (forms, techniques, the Great Ape, healing others,
 * the Earthling's TP) stays the Z Fighter's.
 */
public final class ZFighterLineage {
	public static final int NONE = 0;
	public static final int EARTHLING = 1;
	public static final int WARRIOR = 2;
	public static final int VERDANT = 3;
	public static final int FROST = 4;
	public static final int LINEAGES = 5;
	public static final String[] KEYS = {"none", "earthling", "warrior", "verdant", "frost"};

	// ---- Earthling ----
	public static final float EARTHLING_TP = 1.25F;
	public static final float EARTHLING_LIMIT = 0.5F;
	// ---- Warrior-born ----
	public static final int WARRIOR_ZENKAI = 2;
	/** The Great Ape's scale added to a player's 1 (17 times: about 30 blocks tall). */
	static final double APE_SCALE = 16.0;
	/** Growing into the ape takes this long; shrinking back, this long. */
	static final int APE_GROW_TICKS = 80;
	static final int APE_SHRINK_TICKS = 40;
	/** How big the scale attribute may go (vanilla stops at 16; see {@code ZFighterScaleMixin}). */
	public static final double MAX_SCALE = 20.0;
	static final double APE_FISTS = 1.0;
	static final double APE_HEALTH = 40.0;
	/** The Great Ape lumbers: walking speed lost (a share). */
	static final double APE_SPEED = -0.4;
	static final int APE_BEAM_COOLDOWN = 80;
	/** Out of the moonlight this long, the Great Ape shrinks back. */
	static final int APE_MOONLESS = 200;
	/** Enemies underfoot (in the lowest blocks of his bulk) take this much, every this many ticks, and are flattened. */
	static final float APE_STOMP = 14.0F;
	static final int APE_STOMP_EVERY = 5;
	static final double APE_STOMP_DEPTH = 3.0;
	/**
	 * Screen shake: each footfall (every this many blocks walked, felt this far), a stomp that lands, the change itself.
	 * (0.17.54) Andy: the apes too should "move with a slow sway, keeping their move speed but removing that fast swing":
	 * a step every 5 blocks, not 3, and the client's legs keep to the steps ({@code ZFighterApeSteps}).
	 */
	static final double APE_STRIDE = 5.0, APE_STEP_RANGE = 32.0;
	static final float APE_STEP_SHAKE = 1.0F, APE_STOMP_SHAKE = 1.4F, APE_CHANGE_SHAKE = 2.5F;
	/**
	 * Every footfall is a stomp: enemies this far from the foot (blocks, at full size) are hurt this much and thrown up.
	 * (0.17.54) As the Aether Giants' (Andy: "a stomp with every step, emitting a shockwave signified by a radiating particle
	 * effect, cracking the ground and dealing damage to those who dont jump"): its shockwave's ring runs out to the reach,
	 * the ground cracks round the foot ({@link com.jaymospowers.core.GroundCracks}), and whoever is off the ground is spared.
	 */
	static final double APE_FOOT_REACH = 6.0;
	static final float APE_FOOT_DAMAGE = 8.0F;
	/** His jump (added to vanilla's 0.42) and how much of his weight he sheds, so he leaps ~20 blocks and drifts down slow. */
	static final double APE_JUMP = 0.58, APE_LIGHT = 0.85;
	/** A drop this far (blocks) lands as a stomp. */
	static final double APE_LANDING = 5.0;
	/** How far across the ground his fists reach (blocks), measured out from his body rather than his eyes up high. */
	static final double APE_REACH = 20.0;
	// ---- the Great Ape crippled, and the wild ape (0.12.3) ----
	/** A Warrior-born who's no Z Fighter may still turn under a full moon (tail out): this often, rolled once a night. */
	static final float WILD_CHANCE = 0.5F;
	/** A night's roll stands this long (ticks) before the moon is rolled for again. */
	static final long WILD_ROLL_EVERY = 12000L;
	/** The wild ape is a little smaller than a Z Fighter's (scale added to 1), and lasts at most this long (5 min). */
	static final double WILD_SCALE = 13.0;
	static final long WILD_MAX_TICKS = 6000L;
	/** Berserk, the wild ape goes for anything alive this near (blocks), swinging every this many ticks once in reach. */
	static final double BERSERK_RANGE = 40.0;
	static final int BERSERK_SWING_EVERY = 16, BERSERK_PICK_EVERY = 20;
	/** How far past its own bulk its fists reach while berserk (blocks, at full size). */
	static final double BERSERK_REACH = 4.0;
	/** (0.13.5) It only goes for what it can see and stand over: no higher or lower than this (blocks), nor under water. */
	static final double BERSERK_DROP = 10.0;
	/** Chasing one this long (ticks) without landing a blow, it gives up on it for this long and looks for another. */
	static final int BERSERK_GIVE_UP = 300, BERSERK_IGNORE = 600;
	/** With nothing to go for it roams: a spot this far off (blocks), picked again this often (ticks) or once there. */
	static final double ROAM_MIN = 16.0, ROAM_MAX = 40.0;
	static final int ROAM_EVERY = 200;
	/** Hurt, it runs from the blow: this far (blocks) for this long (ticks), and won't run again for this long after. */
	static final double FLEE_DISTANCE = 40.0;
	static final int FLEE_TICKS = 60, FLEE_REST = 160;
	/** Blows to the legs (landing from below this share of its height) cripple a Great Ape: this many, or one this hard. */
	static final double LEG_SHARE = 0.4;
	static final int CRIPPLE_HITS = 25, WILD_CRIPPLE_HITS = 15;
	static final float CRIPPLE_BLOW = 30.0F, WILD_CRIPPLE_BLOW = 20.0F;
	/** Crippled, it lies flat this long (ticks; a Z Fighter's, the wild ape's), then can't be crippled again for this long. */
	static final int CRIPPLED_TICKS = 120, WILD_CRIPPLED_TICKS = 160, CRIPPLE_SAFE_TICKS = 200;
	/** A tail cut off (a blade, while the ape lies crippled) grows back after this long (3 days). */
	static final long TAIL_REGROW_TICKS = 72000L;
	// ---- Verdant ----
	static final int REGEN_EVERY = 40;
	static final float REGEN = 1.0F;
	/** How often a Verdant grows back one lost limb (30 s; a multiple of REGEN_EVERY). */
	static final int REGROW_EVERY = 600;
	/** Giant forms grow him by this much (scale added, by form). */
	static final double[] GIANT_SCALE = {0.0, 0.4, 0.8, 1.3, 1.3};
	static final float HEAL_KI = 20.0F;
	static final float HEAL = 6.0F;
	static final double HEAL_RANGE = 8.0;
	static final int HEAL_COOLDOWN = 40;

	/** Aura colours: [lineage][form]. */
	private static final int[][] COLORS = {
		{0xFFE6B0, 0xFFD54A, 0xFFE873, 0xFFC020, 0xFFC020},
		{0xFFFFFF, 0xFFD54A, 0xFFE873, 0xFFC020, 0xFFC020},
		// (0.15.148) the Warrior-born's Super 4: the ape's red
		{0xFFE6B0, 0xFFD54A, 0xFFE873, 0xFFC020, 0xE02818},
		{0xA8FF8A, 0x6CF060, 0x48D848, 0x2CC02C, 0x2CC02C},
		{0xD8B8FF, 0xC89BFF, 0xF0E8FF, 0xFFD030, 0xFFD030},
	};

	/** Which lineage he is ({@link #NONE} until he picks). Everyone sees it (the tail). Saved, kept through death. */
	public static final AttachmentType<Integer> LINEAGE = AttachmentRegistry.create(JaymosPowers.id("zfighter_lineage"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** A Warrior-born's tail wrapped round his waist (no Great Ape). Saved, kept through death, seen by all. */
	public static final AttachmentType<Boolean> TAIL_WRAPPED = AttachmentRegistry.create(JaymosPowers.id("zfighter_tail_wrapped"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** He's the Great Ape right now. Seen by all, not saved. */
	public static final AttachmentType<Boolean> GREAT_APE = AttachmentRegistry.create(JaymosPowers.id("zfighter_great_ape"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** (0.12.3) The Great Ape lies crippled: it can't move. Seen by all, not saved. */
	public static final AttachmentType<Boolean> CRIPPLED = AttachmentRegistry.create(JaymosPowers.id("zfighter_ape_crippled"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** (0.12.3) A Warrior-born's tail was cut off: the game time it grows back. Saved, kept through death, seen by all. */
	public static final AttachmentType<Long> TAIL_CUT = AttachmentRegistry.create(JaymosPowers.id("zfighter_tail_cut"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath().syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	/** (0.12.3) The wild ape's quarry (entity id): its own player's client walks him at it. Not saved. */
	public static final AttachmentType<Integer> BERSERK = AttachmentRegistry.create(JaymosPowers.id("zfighter_ape_berserk"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/** (0.13.5) Where the wild ape roams or runs to while it has no quarry: its own player's client walks him there. Not saved. */
	public static final AttachmentType<BlockPos> WILD_GOAL = AttachmentRegistry.create(JaymosPowers.id("zfighter_ape_goal"),
		builder -> builder.syncWith(BlockPos.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	/** (0.13.5) The wild ape is running from a hurt (to {@link #WILD_GOAL}, flat out). Not saved. */
	public static final AttachmentType<Boolean> WILD_FLEE = AttachmentRegistry.create(JaymosPowers.id("zfighter_ape_flee"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/** Jaymo's Kingdoms' heritage tag (then the heritage's key), and the heritages that make a lineage of their own. */
	private static final String HERITAGE_TAG = "jaymoskingdoms_heritage_";
	private static final String[][] HERITAGE_LINEAGES = {{"earthling", "earthling"}, {"warriorborn", "warrior"}, {"frostborn", "frost"}, {"verdant", "verdant"}};
	/** Kingdoms puts this tag on a player who pressed the heritage key as a Warrior-born: the tail is wrapped or let out. */
	public static final String TOGGLE_TAG = "jaymoskingdoms_tail_toggle";
	/** (the old passive reach of a Verdant who's no Z Fighter: taken off; the stretch punch is the long arm now) */
	private static final Identifier RACE_REACH_ID = JaymosPowers.id("race_reach");

	private static final Identifier WRIT = Identifier.fromNamespaceAndPath("jaymoskingdoms", "writ_of_rebirth");

	private ZFighterLineage() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterLineage::everyone);
		// (0.12.3) blows on the Great Ape's legs cripple him; a blade while he's down cuts his tail off
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!blocked && entity instanceof ServerPlayer player && ape(player)) {
				hurt(player, source, damageTaken);
			} else if (!blocked && VillagerApe.raging(entity)) {
				VillagerApe.hurt((net.minecraft.world.entity.Mob)entity, source, damageTaken);
			}
		});
		// a Warrior-born villager with its tail wrapped may still turn under the full moon
		VillagerApe.init();
		RaceMoves.init();
		// (0.17.56) the lineages' extras (merging, the ki reserve, ...)
		ZFighterRaces.init();
		// (0.15.148) a Z Fighter's ape has a mind of its own: its rage, its fits, his primal
		ApeRage.init();
		// a Writ of Rebirth (Jaymo's Kingdoms), read while sneaking: the lineage is unmade and asked again
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (level.isClientSide() || !player.isShiftKeyDown() || !Powers.has(player, ZFighterPower.INSTANCE) || of(player) == NONE
				|| !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(WRIT)) {
				return InteractionResult.PASS;
			}
			ServerPlayer server = (ServerPlayer)player;
			if (!reborn(server)) {
				return InteractionResult.PASS;
			}
			stack.consume(1, player);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
			return InteractionResult.SUCCESS;
		});
	}

	public static int of(Player player) {
		Integer lineage = player.getAttached(LINEAGE);
		return lineage == null ? NONE : lineage;
	}

	public static boolean tailOut(Player player) {
		return of(player) == WARRIOR && !Boolean.TRUE.equals(player.getAttached(TAIL_WRAPPED)) && !tailCut(player);
	}

	/** His tail's been cut off and hasn't grown back yet. */
	public static boolean tailCut(Player player) {
		Long back = player.getAttached(TAIL_CUT);
		return back != null && player.level().getGameTime() < back;
	}

	/** The Great Ape lies crippled. */
	public static boolean crippled(net.minecraft.world.entity.Entity player) {
		return Boolean.TRUE.equals(player.getAttached(CRIPPLED));
	}

	/** A Warrior-born with no Z Fighter power: his Great Ape is the wild one (berserk, never his to steer). */
	public static boolean wild(Player player) {
		return !Powers.has(player, ZFighterPower.INSTANCE);
	}

	/** The reach to add so his fists land {@link #APE_REACH} out along the ground, from eyes that far up. */
	static double apeReach(Player player) {
		double up = Math.max(0.0, player.getEyeHeight() - 1.0);
		return Math.sqrt(APE_REACH * APE_REACH + up * up) - player.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
	}

	public static boolean ape(Player player) {
		return Boolean.TRUE.equals(player.getAttached(GREAT_APE));
	}

	/** The aura's colour for this lineage and form. */
	public static int color(int lineage, int form) {
		// (0.17.50) the newer forms (False Super, god, legendary) have a colour of their own, whatever the lineage
		if (form > ApeRage.SUPER_4) {
			return ZFighterForms.color(form);
		}
		return COLORS[Math.clamp(lineage, 0, LINEAGES - 1)][Math.clamp(form, 0, ApeRage.SUPER_4)];
	}

	/** The lang key of form {@code form} (1-3) for this lineage: Super, Giant or Frost-born's evolutions. */
	public static String formKey(int lineage, int form) {
		String kind = form > ApeRage.SUPER_4 ? "" : lineage == VERDANT ? "verdant." : lineage == FROST ? "frost." : "";
		return "form.jaymospowers.zfighter." + kind + form;
	}

	/** The lang key of a technique: the V's Ki Wave is each lineage's own (Crimson Cannon, Spiral Lance, Frost Needle). */
	public static String techniqueKey(Player player, int technique) {
		if (technique < 0 || technique >= ZFighterTraining.TECHNIQUES) {
			// (0.18.94) a made technique has a name, not a key (techniqueName)
			return "technique.jaymospowers.zfighter.made";
		}
		String key = "technique.jaymospowers.zfighter." + ZFighterTraining.TECHNIQUE_KEYS[technique];
		int lineage = of(player);
		return technique == ZFighterTraining.KI_WAVE && lineage > EARTHLING ? key + "." + KEYS[lineage] : key;
	}

	/** (0.18.94) A technique's name as shown: a made one's own, in its colour, or the learned one's. */
	public static net.minecraft.network.chat.Component techniqueName(Player player, int technique) {
		ZFighterCreator.Made made = ZFighterCreator.made(player, technique);
		return made != null ? ZFighterCreator.name(made) : net.minecraft.network.chat.Component.translatable(techniqueKey(player, technique));
	}

	public static String formKey(Player player, int form) {
		// (0.17.50) a legendary-born's Super line is the legendary one
		if (form >= 1 && form <= ZFighterTraining.FORMS && ZFighterLegendary.mutant(player)) {
			return "form.jaymospowers.zfighter." + (ZFighterForms.LEGEND_1 + form - 1);
		}
		return formKey(of(player), form);
	}

	/** How much of a form's ki drain he pays (Frost-born's evolutions cost nothing to hold). */
	static float drainFactor(int lineage, int form) {
		return lineage == FROST && form < 3 ? 0.0F : 1.0F;
	}

	static float trainingFactor(Player player) {
		return of(player) == EARTHLING ? EARTHLING_TP : 1.0F;
	}

	public static float limitFactor(Player player) {
		return of(player) == EARTHLING ? EARTHLING_LIMIT : 1.0F;
	}

	static int zenkaiFactor(Player player) {
		return of(player) == WARRIOR ? WARRIOR_ZENKAI : 1;
	}

	/**
	 * The lineage {@code player}'s blood gives: from their Kingdoms heritage, Earthling for the rest. {@link #NONE} while
	 * Kingdoms is here but they haven't chosen a heritage yet (character creation isn't done).
	 */
	public static int fromHeritage(Player player) {
		Set<String> tags = player.entityTags();
		boolean any = false;
		for (String tag : tags) {
			if (tag.startsWith(HERITAGE_TAG)) {
				any = true;
				String key = tag.substring(HERITAGE_TAG.length());
				for (String[] pair : HERITAGE_LINEAGES) {
					if (pair[0].equals(key)) {
						return java.util.Arrays.asList(KEYS).indexOf(pair[1]);
					}
				}
			}
		}
		return any || !FabricLoader.getInstance().isModLoaded("jaymoskingdoms") ? EARTHLING : NONE;
	}

	/** (0.17.56) Whether {@code entity} is Divine-born (Kingdoms heritage {@code divine}): its ki is hidden from sense and scouters. */
	public static boolean divine(net.minecraft.world.entity.Entity entity) {
		return entity.entityTags().contains(HERITAGE_TAG + "divine");
	}

	/**
	 * The race {@code player}'s heritage gives anyone (no Z Fighter power needed): one of the four, or {@link #NONE}. Any
	 * entity Kingdoms tags with a heritage reads the same (its villagers are born to them too, {@link VillagerApe}).
	 */
	public static int race(net.minecraft.world.entity.Entity player) {
		for (String tag : player.entityTags()) {
			if (tag.startsWith(HERITAGE_TAG)) {
				String key = tag.substring(HERITAGE_TAG.length());
				for (String[] pair : HERITAGE_LINEAGES) {
					if (pair[0].equals(key)) {
						return java.util.Arrays.asList(KEYS).indexOf(pair[1]);
					}
				}
			}
		}
		return NONE;
	}

	/**
	 * Every player, every tick: the heritage key's tail toggle for anyone; and for those who aren't Z Fighters (the power
	 * runs its own lineage), their race from the heritage, a Warrior-born's tail, a Verdant's mending and reach.
	 */
	private static void everyone(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.entityTags().contains(TOGGLE_TAG)) {
				player.removeTag(TOGGLE_TAG);
				toggleTail(player);
			}
			if (Powers.has(player, ZFighterPower.INSTANCE)) {
				ZFighterPower.set(player, Attributes.ENTITY_INTERACTION_RANGE, RACE_REACH_ID, 0.0, AttributeModifier.Operation.ADD_VALUE);
				continue;
			}
			if (!player.isAlive()) {
				continue;
			}
			if (player.tickCount % 20 == 0) {
				int race = race(player);
				if (race != of(player)) {
					if (race == NONE) {
						player.removeAttached(LINEAGE);
					} else {
						player.setAttached(LINEAGE, race);
					}
					player.removeAttached(GREAT_APE);
				}
			}
			int lineage = of(player);
			tailTags(player, lineage == WARRIOR, tailOut(player));
			ZFighterPower.set(player, Attributes.ENTITY_INTERACTION_RANGE, RACE_REACH_ID, 0.0, AttributeModifier.Operation.ADD_VALUE);
			if (lineage == VERDANT) {
				mend(player, player.level().getGameTime());
			}
			ZFighterPower.Live live = ZFighterPower.live(player);
			if (lineage == WARRIOR) {
				ape(player, live, player.level(), player.level().getGameTime());
			} else if (ape(player)) {
				endApe(player, live);
			}
			if (live.apeSize > 0.0F || ape(player)) {
				grow(player, live, player.level());
			}
		}
	}

	/** A Verdant's body mends itself: health back while fed, breaks knit at once, lost limbs grow back one at a time. */
	private static void mend(ServerPlayer player, long now) {
		if (now % REGEN_EVERY != 0) {
			return;
		}
		if (player.getHealth() < player.getMaxHealth() && player.getFoodData().getFoodLevel() > ZFighterPower.HUNGRY_FOOD) {
			player.heal(REGEN);
		}
		com.jaymospowers.core.Fractures.heal(player);
		if (now % REGROW_EVERY == 0) {
			com.jaymospowers.core.Dismember.regrow(player, false);
		}
	}

	/** Gives {@code player} the lineage of their blood, if they have none yet. */
	static void inherit(ServerPlayer player) {
		if (of(player) != NONE) {
			return;
		}
		int lineage = fromHeritage(player);
		if (lineage != NONE) {
			become(player, lineage);
		}
	}

	/** A Writ of Rebirth or the rebirth wish: the lineage goes back to what the blood says. False if it already is. */
	static boolean reborn(ServerPlayer player) {
		int blood = fromHeritage(player);
		if (blood == NONE || blood == of(player)) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_lineage_same").withStyle(ChatFormatting.GRAY));
			return false;
		}
		endApe(player, ZFighterPower.live(player));
		player.removeAttached(TAIL_WRAPPED);
		become(player, blood);
		return true;
	}

	private static void become(ServerPlayer player, int lineage) {
		player.setAttached(LINEAGE, lineage);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_lineage",
			Component.translatable("lineage.jaymospowers.zfighter." + KEYS[lineage])).withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F,
			1.0F);
		ZFighterPower.refresh(player, ZFighterPower.live(player));
	}

	/** Sneak+Z on the ground (or the heritage key): a Warrior-born wraps his tail away or lets it out. */
	static void toggleTail(ServerPlayer player) {
		if (of(player) != WARRIOR || ape(player) || tailCut(player)) {
			return;
		}
		boolean wrapped = !Boolean.TRUE.equals(player.getAttached(TAIL_WRAPPED));
		if (wrapped) {
			player.setAttached(TAIL_WRAPPED, true);
		} else {
			player.removeAttached(TAIL_WRAPPED);
		}
		player.sendOverlayMessage(Component.translatable(wrapped ? "message.jaymospowers.zfighter_tail_wrapped" : "message.jaymospowers.zfighter_tail_out")
			.withStyle(ChatFormatting.GOLD));
	}

	// ---------------------------------------------------------------- every tick

	/**
	 * Entity tags Jaymo's Kingdoms reads (it needs no Powers classes): a Warrior-born, and one whose tail is out for
	 * villagers to see (they take him for a monster until he's explained it to them).
	 */
	public static final String TAILED_TAG = "jaymospowers_tailed", TAIL_TAG = "jaymospowers_tail";

	static void tailTags(ServerPlayer player, boolean tailed, boolean out) {
		tag(player, TAILED_TAG, tailed);
		tag(player, TAIL_TAG, out);
	}

	private static void tag(ServerPlayer player, String tag, boolean on) {
		if (on) {
			player.addTag(tag);
		} else {
			player.removeTag(tag);
		}
	}

	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		if (of(player) == NONE && player.tickCount % 20 == 0) {
			inherit(player);
		}
		live.lineage = of(player);
		live.kiColor = ZFighterPower.kiColor(player);
		tailTags(player, live.lineage == WARRIOR, tailOut(player));
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (live.lineage == VERDANT) {
			mend(player, now);
		}
		if (live.lineage == WARRIOR) {
			ape(player, live, level, now);
		} else if (ape(player)) {
			endApe(player, live);
		}
		grow(player, live, level);
	}

	/** The scale (added to 1) at this far grown: eased in and out, so he swells up and slows into his full size. */
	static double apeScale(float size) {
		float t = Mth.clamp(size, 0.0F, 1.0F);
		return APE_SCALE * t * t * (3.0F - 2.0F * t);
	}

	/** Grows him into the Great Ape (or shrinks him back) a little every tick, the ground shaking while he does. */
	private static void grow(ServerPlayer player, ZFighterPower.Live live, ServerLevel level) {
		boolean ape = ape(player);
		float target = ape ? 1.0F : 0.0F;
		if (live.apeSize == target) {
			return;
		}
		live.apeSize = ape ? Math.min(1.0F, live.apeSize + 1.0F / APE_GROW_TICKS) : Math.max(0.0F, live.apeSize - 1.0F / APE_SHRINK_TICKS);
		attributes(player, live);
		if (ape) {
			double width = player.getBbWidth();
			level.sendParticles(new DustParticleOptions(0x6B3A1E, 3.0F), true, false, player.getX(), player.getY() + player.getBbHeight() * 0.5,
				player.getZ(), 6, width * 0.4, player.getBbHeight() * 0.35, width * 0.4, 0.0);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, player.getX(), player.getY() + 0.2, player.getZ(), 3, width * 0.6, 0.1,
				width * 0.6, 0.02);
			if (level.getGameTime() % 10 == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 3.0F,
					0.5F + 0.3F * (1.0F - live.apeSize));
			}
			if (live.apeSize >= 1.0F) {
				// full size: the roar
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 6.0F, 0.4F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 5.0F, 0.5F);
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, false, player.getX(), player.getY() + 2.0, player.getZ(), 3, width * 0.4, 1.0,
					width * 0.4, 0.0);
				player.setHealth(player.getMaxHealth());
				com.jaymospowers.core.Shakes.heavy(player, player.position(), 48.0, 2.8F);
			}
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void ape(ServerPlayer player, ZFighterPower.Live live, ServerLevel level, long now) {
		Long back = player.getAttached(TAIL_CUT);
		if (back != null && now >= back) {
			// the tail's grown back
			player.removeAttached(TAIL_CUT);
		}
		boolean wild = wild(player);
		// (Andy, 2026-11-01) a false moon ({@link ZFighterFakeMoon}) shines like a full one, any night
		boolean fakeMoon = tailOut(player) && ZFighterFakeMoon.shines(level, player);
		boolean moonlit = fakeMoon || tailOut(player) && level.isDarkOutside() && level.canSeeSky(BlockPos.containing(player.getEyePosition()))
			&& level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO) == MoonPhase.FULL_MOON;
		if (!ape(player)) {
			live.apePeak = player.getY();
			if (moonlit && player.isAlive() && live.apeSize <= 0.0F) {
				if (!wild) {
					becomeApe(player, live);
				} else if (now - live.wildRolledAt >= WILD_ROLL_EVERY) {
					// no Z Fighter: the moon may or may not take him, once a night
					live.wildRolledAt = now;
					if (player.getRandom().nextFloat() < WILD_CHANCE) {
						becomeApe(player, live);
					}
				}
			}
			return;
		}
		live.moonless = moonlit ? 0 : live.moonless + 1;
		if (live.moonless >= APE_MOONLESS || !tailOut(player) || level.isBrightOutside() || wild && now - live.wildFrom >= WILD_MAX_TICKS) {
			endApe(player, live);
			return;
		}
		if (crippled(player)) {
			// down on his back until his legs come back to him
			if (now >= live.crippledUntil) {
				player.removeAttached(CRIPPLED);
				live.crippledUntil = -1L;
				attributes(player, live);
			}
			return;
		}
		if (ApeGrab.tick(player, live, level, now)) {
			// (0.17.68) his grab has him: he stands and plays it out
			return;
		}
		if (wild || ApeRage.tick(player, live, level, now)) {
			// (0.15.148) the wild ape is always berserk; a Z Fighter's only in a fit ({@link ApeRage})
			berserk(player, live, level, now);
		}
		if (now % 3 == 0) {
			level.sendParticles(new DustParticleOptions(0xC03020, 2.5F), true, false, player.getX(), player.getY() + player.getBbHeight() * 0.5,
				player.getZ(), 2, player.getBbWidth() * 0.4, player.getBbHeight() * 0.3, player.getBbWidth() * 0.4, 0.0);
		}
		if (now % 200 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.0F, 0.5F);
		}
		// a long drop lands as a stomp
		if (player.onGround()) {
			if (live.apePeak - player.getY() >= APE_LANDING && live.apeSize >= 0.5F) {
				footfall(player, live, level);
				footfall(player, live, level);
			}
			live.apePeak = player.getY();
		} else {
			live.apePeak = Math.max(live.apePeak, player.getY());
		}
		// every stride of his shakes the ground (sprinting, he skims over it instead)
		if (player.moveDist - live.apeStepAt >= APE_STRIDE * Math.max(0.5F, live.apeSize)) {
			live.apeStepAt = player.moveDist;
			if (player.onGround() && !player.isSprinting() && live.apeSize >= 0.5F) {
				footfall(player, live, level);
			}
		}
		if (now % APE_STOMP_EVERY == 0 && live.apeSize >= 0.5F) {
			stomp(player, level);
		}
		ApeSmash.tick(player, now);
	}

	/** One of the Great Ape's steps: the foot (left, then right) comes down like a stomp, shaking the ground and flinging what's near. */
	static void footfall(LivingEntity player, ZFighterPower.Live live, ServerLevel level) {
		live.apeLeftFoot = !live.apeLeftFoot;
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double side = (live.apeLeftFoot ? 1.0 : -1.0) * player.getBbWidth() * 0.25;
		Vec3 foot = player.position().add(Math.cos(yaw) * side, 0.0, Math.sin(yaw) * side);
		double reach = APE_FOOT_REACH * live.apeSize;
		shake(player, foot, APE_STEP_RANGE, APE_STEP_SHAKE * live.apeSize);
		level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, 0.45F);
		level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 3.0F, 0.4F);
		net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(BlockPos.containing(foot).below());
		if (!ground.isAir()) {
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground), true, false, foot.x, foot.y + 0.1,
				foot.z, 60, reach * 0.4, 0.2, reach * 0.4, 0.3);
		}
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, foot.x, foot.y + 0.2, foot.z, 6, reach * 0.3, 0.1, reach * 0.3, 0.02);
		BlockPos under = BlockPos.containing(foot.x, foot.y - 0.5, foot.z);
		com.jaymospowers.core.GroundCracks.crack(level, under, level.getRandom(), Math.max(1, (int)Math.round(reach * 0.5)));
		ZFighterNet.ApeStep step = new ZFighterNet.ApeStep(player.getId(), live.apeLeftFoot, foot.x, under.getY() + 1.0, foot.z, (float)reach,
			(float)(APE_STRIDE * Math.max(0.5F, live.apeSize)));
		for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(viewer, step);
		}
		if (player instanceof ServerPlayer self) {
			ServerPlayNetworking.send(self, step);
		}
		// the wave runs along the ground: jump it and it misses you
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(foot, foot).inflate(reach, 2.0, reach),
			e -> e != player && e.isAlive() && e.onGround() && trodden(player, e))) {
			if (victim.hurtServer(level, blow(level, player), APE_FOOT_DAMAGE)) {
				com.jaymospowers.core.BladeBlood.crushed(victim);
				Vec3 away = victim.position().subtract(foot).multiply(1.0, 0.0, 1.0).normalize();
				victim.push(away.x * 0.6, 0.5, away.z * 0.6);
			}
		}
	}

	/** The Great Ape treads on his enemies: monsters, and anything that's after him, under his feet. */
	static void stomp(LivingEntity player, ServerLevel level) {
		AABB box = player.getBoundingBox();
		AABB feet = new AABB(box.minX, box.minY - 0.5, box.minZ, box.maxX, box.minY + APE_STOMP_DEPTH, box.maxZ);
		boolean hit = false;
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, feet, e -> e != player && e.isAlive()
			&& trodden(player, e))) {
			if (victim.hurtServer(level, blow(level, player), APE_STOMP)) {
				// trodden flat: it bursts
				com.jaymospowers.core.BladeBlood.crushed(victim);
				hit = true;
				victim.setDeltaMovement(victim.getDeltaMovement().multiply(0.3, 0.0, 0.3).add(0.0, -0.4, 0.0));
				level.sendParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + 0.3, victim.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, victim.getX(), victim.getY() + 0.2, victim.getZ(), 6, 0.6, 0.1, 0.6, 0.02);
			}
		}
		if (hit) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.5F);
			shake(player, player.position(), APE_STEP_RANGE, APE_STOMP_SHAKE);
		}
	}

	/** The Great Ape's blow: a player's attack, or (a villager's wild ape, {@link VillagerApe}) a mob's. */
	static DamageSource blow(ServerLevel level, LivingEntity ape) {
		return ape instanceof Player player ? level.damageSources().playerAttack(player) : level.damageSources().mobAttack(ape);
	}

	/** The ground shakes under the Great Ape: a player's own body feels it hardest; round a villager's, everyone near. */
	static void shake(LivingEntity ape, Vec3 at, double range, float strength) {
		if (ape instanceof ServerPlayer player) {
			com.jaymospowers.core.Shakes.heavy(player, at, range, strength);
		} else if (ape.level() instanceof ServerLevel level) {
			com.jaymospowers.core.Shakes.around(level, at, range, strength);
		}
	}

	private static void becomeApe(ServerPlayer player, ZFighterPower.Live live) {
		boolean wild = wild(player);
		if (!wild) {
			if (live.flying) {
				ZFighterPower.endFlight(player, live);
			}
			if (live.superOn()) {
				ZFighterPower.endSuper(player, live);
			}
			ZFighterPower.endLimit(player, live);
			ZFighterTechniques.cancel(player, live);
		}
		live.waveFrom = -1L;
		live.barrageFrom = -1L;
		live.charging = false;
		live.moonless = 0;
		live.legHits = 0;
		live.crippledUntil = -1L;
		live.wildFrom = wild ? player.level().getGameTime() : -1L;
		live.berserkTarget = -1;
		live.berserkIgnore = -1;
		live.fleeUntil = -1L;
		live.fleeRestUntil = 0L;
		live.roamAt = 0L;
		player.setAttached(GREAT_APE, true);
		// (0.17.50) a fighter who has gone Super turns golden
		ZFighterGoldenApe.began(player);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3.0F, 0.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 2.0F, 0.6F);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), APE_STEP_RANGE * 1.5, APE_CHANGE_SHAKE);
		player.sendSystemMessage(Component.translatable(wild ? "message.jaymospowers.zfighter_ape_wild" : "message.jaymospowers.zfighter_ape")
			.withStyle(ChatFormatting.RED));
		attributes(player, live);
		player.setHealth(player.getMaxHealth());
		ApeRage.began(player);
	}

	static void endApe(ServerPlayer player, ZFighterPower.Live live) {
		if (!ape(player)) {
			return;
		}
		player.removeAttached(ApeSmash.CHARGE);
		ApeGrab.end(player, live);
		player.removeAttached(GREAT_APE);
		ZFighterGoldenApe.ended(player);
		player.removeAttached(CRIPPLED);
		player.removeAttached(BERSERK);
		player.removeAttached(WILD_GOAL);
		player.removeAttached(WILD_FLEE);
		ApeRage.ended(player);
		live.crippledUntil = -1L;
		live.wildFrom = -1L;
		live.berserkTarget = -1;
		live.fleeUntil = -1L;
		live.legHits = 0;
		player.level().sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(), 30, 1.0, 1.5, 1.0, 0.05);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_ape_end").withStyle(ChatFormatting.GRAY));
		attributes(player, live);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	// ---------------------------------------------------------------- the Great Ape: crippled, wild, berserk (0.12.3)

	/** What the Great Ape's feet crush: monsters and anything after him; everything alive, when he's berserk. */
	private static boolean trodden(LivingEntity player, LivingEntity e) {
		if (player.hasAttached(BERSERK)) {
			return !(e instanceof Player other) || !other.isCreative() && !other.isSpectator();
		}
		return e instanceof Enemy || e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == player;
	}

	private static final Identifier WILD_SCALE_ID = JaymosPowers.id("zfighter_wild_ape_scale");
	private static final Identifier WILD_FIST_ID = JaymosPowers.id("zfighter_wild_ape_fists");
	private static final Identifier WILD_HEALTH_ID = JaymosPowers.id("zfighter_wild_ape_health");
	private static final Identifier WILD_SPEED_ID = JaymosPowers.id("zfighter_wild_ape_speed");
	private static final Identifier WILD_JUMP_ID = JaymosPowers.id("zfighter_wild_ape_jump");
	private static final Identifier WILD_GRAVITY_ID = JaymosPowers.id("zfighter_wild_ape_gravity");
	private static final Identifier WILD_FALL_ID = JaymosPowers.id("zfighter_wild_ape_fall");
	private static final Identifier WILD_REACH_ID = JaymosPowers.id("zfighter_wild_ape_reach");
	private static final Identifier CRIPPLE_SPEED_ID = JaymosPowers.id("zfighter_ape_crippled_speed");
	private static final Identifier CRIPPLE_JUMP_ID = JaymosPowers.id("zfighter_ape_crippled_jump");

	/** The Great Ape's body as it stands: a Z Fighter's through his power, the wild ape's here; and crippled legs. */
	private static void attributes(ServerPlayer player, ZFighterPower.Live live) {
		boolean wild = wild(player);
		if (!wild) {
			ZFighterPower.refresh(player, live);
		}
		boolean ape = wild && ape(player);
		float t = Mth.clamp(live.apeSize, 0.0F, 1.0F);
		double scale = wild ? WILD_SCALE * t * t * (3.0F - 2.0F * t) : 0.0;
		ZFighterPower.set(player, Attributes.SCALE, WILD_SCALE_ID, scale, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, WILD_FIST_ID, ape ? APE_FISTS : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.MAX_HEALTH, WILD_HEALTH_ID, ape ? APE_HEALTH : 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, WILD_SPEED_ID, ape ? APE_SPEED : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.JUMP_STRENGTH, WILD_JUMP_ID, ape ? APE_JUMP : 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.GRAVITY, WILD_GRAVITY_ID, ape ? -APE_LIGHT : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.FALL_DAMAGE_MULTIPLIER, WILD_FALL_ID, ape ? -1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.ENTITY_INTERACTION_RANGE, WILD_REACH_ID, ape ? apeReach(player) : 0.0, AttributeModifier.Operation.ADD_VALUE);
		boolean down = ape(player) && crippled(player);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, CRIPPLE_SPEED_ID, down ? -1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.JUMP_STRENGTH, CRIPPLE_JUMP_ID, down ? -1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/**
	 * A blow on the Great Ape: low ones are on his legs, and enough of them (or one hard enough) bring him down; while he
	 * lies there, a blade cuts his tail off and he shrinks back.
	 */
	static void hurt(ServerPlayer player, DamageSource source, float taken) {
		ZFighterPower.Live live = ZFighterPower.live(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (wild(player) && !crippled(player) && taken > 0.0F) {
			flee(player, live, source, now);
		}
		ApeRage.hurt(player, taken);
		if (crippled(player)) {
			if (source.getEntity() instanceof LivingEntity by && source.getDirectEntity() == by
				&& com.jaymospowers.core.Dismember.blade(by.getMainHandItem())) {
				cutTail(player, live, level, now);
			}
			return;
		}
		Vec3 from = source.getSourcePosition();
		if (now < live.crippleSafeUntil || from == null || from.y > player.getY() + player.getBbHeight() * LEG_SHARE || live.apeSize < 0.5F) {
			return;
		}
		boolean wild = wild(player);
		live.legHits++;
		if (taken >= (wild ? WILD_CRIPPLE_BLOW : CRIPPLE_BLOW) || live.legHits >= (wild ? WILD_CRIPPLE_HITS : CRIPPLE_HITS)) {
			live.legHits = 0;
			live.crippledUntil = now + (wild ? WILD_CRIPPLED_TICKS : CRIPPLED_TICKS);
			live.crippleSafeUntil = live.crippledUntil + CRIPPLE_SAFE_TICKS;
			player.setAttached(CRIPPLED, true);
			attributes(player, live);
			ApeRage.crippled(player);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.PLAYERS, 4.0F, 0.4F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.4F);
			com.jaymospowers.core.Shakes.heavy(player, player.position(), APE_STEP_RANGE * 1.5, APE_CHANGE_SHAKE);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ape_crippled").withStyle(ChatFormatting.RED));
		}
	}

	/** The tail's cut off: he shrinks back, and it takes {@link #TAIL_REGROW_TICKS} to grow again. */
	private static void cutTail(ServerPlayer player, ZFighterPower.Live live, ServerLevel level, long now) {
		player.setAttached(TAIL_CUT, now + TAIL_REGROW_TICKS);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 3.0F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_HURT, SoundSource.PLAYERS, 4.0F, 0.4F);
		level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(0x8A0A0A, 3.0F), true, false, player.getX(), player.getY() + 1.0, player.getZ(), 40,
			player.getBbWidth() * 0.4, 0.5, player.getBbWidth() * 0.4, 0.0);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_tail_cut").withStyle(ChatFormatting.RED));
		endApe(player, live);
	}

	/**
	 * The wild ape is berserk: it picks the nearest living thing it can see and get at, its player's client walks him at it,
	 * and he swings when it's in reach. Nothing near, it roams; hurt, it runs from the blow a while ({@link #flee}).
	 */
	private static void berserk(ServerPlayer player, ZFighterPower.Live live, ServerLevel level, long now) {
		if (live.fleeUntil >= 0L) {
			if (now < live.fleeUntil && !near(player, player.getAttached(WILD_GOAL))) {
				return;
			}
			// run far enough: back to the fight
			live.fleeUntil = -1L;
			live.fleeRestUntil = now + FLEE_REST;
			player.removeAttached(WILD_FLEE);
			player.removeAttached(WILD_GOAL);
			live.roamAt = 0L;
		}
		net.minecraft.world.entity.Entity known = live.berserkTarget >= 0 ? level.getEntity(live.berserkTarget) : null;
		LivingEntity target = known instanceof LivingEntity living && living.isAlive() && living.distanceTo(player) <= BERSERK_RANGE * 1.5 ? living : null;
		if (target != null && now - live.berserkSince >= BERSERK_GIVE_UP) {
			// it can't get at this one: leave it be a while
			live.berserkIgnore = target.getId();
			live.berserkIgnoreUntil = now + BERSERK_IGNORE;
			target = null;
		}
		if (target == null || now % BERSERK_PICK_EVERY == 0) {
			LivingEntity nearest = null;
			double best = BERSERK_RANGE * BERSERK_RANGE;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BERSERK_RANGE), e -> quarry(player, live, e, now))) {
				double d = e.distanceToSqr(player);
				if (d < best) {
					best = d;
					nearest = e;
				}
			}
			if (nearest != null || target == null) {
				target = nearest;
			}
		}
		int id = target == null ? -1 : target.getId();
		if (id != live.berserkTarget || !player.hasAttached(BERSERK)) {
			live.berserkTarget = id;
			live.berserkSince = now;
			player.setAttached(BERSERK, id);
		}
		if (target == null) {
			roam(player, live, level, now);
			return;
		}
		if (player.hasAttached(WILD_GOAL)) {
			player.removeAttached(WILD_GOAL);
		}
		if (now >= live.grabReadyAt && inReach(player, live, target) && ApeGrab.begin(player, live, level, target, now)) {
			// (0.17.68) every so often the wild ape takes its quarry in hand instead
			live.berserkSince = now;
			return;
		}
		if (now >= live.berserkSwingAt && inReach(player, live, target)) {
			live.berserkSwingAt = now + BERSERK_SWING_EVERY;
			live.berserkSince = now;
			player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			player.attack(target);
			player.resetAttackStrengthTicker();
		}
	}

	/** Its fists reach {@link #BERSERK_REACH} past its bulk, across the ground and from its feet up to its shoulders. */
	static boolean inReach(LivingEntity player, ZFighterPower.Live live, LivingEntity target) {
		double reach = BERSERK_REACH * Math.max(0.25F, live.apeSize);
		AABB body = player.getBoundingBox();
		return body.inflate(reach, 0.0, reach).expandTowards(0.0, -reach, 0.0).intersects(target.getBoundingBox());
	}

	/** Nothing to go for: it wanders off somewhere new every so often, or once it gets there. */
	static void roam(LivingEntity player, ZFighterPower.Live live, ServerLevel level, long now) {
		BlockPos goal = player.getAttached(WILD_GOAL);
		if (goal != null && now < live.roamAt && !near(player, goal)) {
			return;
		}
		live.roamAt = now + ROAM_EVERY;
		float angle = player.getRandom().nextFloat() * Mth.TWO_PI;
		double far = ROAM_MIN + player.getRandom().nextDouble() * (ROAM_MAX - ROAM_MIN);
		player.setAttached(WILD_GOAL, groundAt(level, player.getX() + Math.cos(angle) * far, player.getZ() + Math.sin(angle) * far));
	}

	/** Hurt: the wild ape turns tail from the blow and runs a way off, unless it only just stopped running. */
	static void flee(LivingEntity player, ZFighterPower.Live live, DamageSource source, long now) {
		if (live.fleeUntil >= 0L || now < live.fleeRestUntil || live.apeSize < 0.5F) {
			return;
		}
		Vec3 from = source.getSourcePosition();
		Vec3 away = from == null ? Vec3.ZERO : player.position().subtract(from).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			float angle = player.getRandom().nextFloat() * Mth.TWO_PI;
			away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
		}
		away = away.normalize().scale(FLEE_DISTANCE);
		live.fleeUntil = now + FLEE_TICKS;
		live.berserkTarget = -1;
		player.setAttached(BERSERK, -1);
		player.setAttached(WILD_GOAL, groundAt((ServerLevel)player.level(), player.getX() + away.x, player.getZ() + away.z));
		player.setAttached(WILD_FLEE, true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_HURT, SoundSource.PLAYERS, 4.0F, 0.4F);
	}

	private static BlockPos groundAt(ServerLevel level, double x, double z) {
		BlockPos column = BlockPos.containing(x, 0.0, z);
		return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
	}

	/** He's stood about at {@code goal} (across the ground, within his own bulk). */
	static boolean near(LivingEntity player, @org.jspecify.annotations.Nullable BlockPos goal) {
		if (goal == null) {
			return true;
		}
		double dx = goal.getX() + 0.5 - player.getX(), dz = goal.getZ() + 0.5 - player.getZ();
		double r = Math.max(2.0, player.getBbWidth() * 0.6);
		return dx * dx + dz * dz <= r * r;
	}

	/**
	 * Who the berserk ape goes for: anything alive but himself, armour stands, and players in creative or spectating, that
	 * he can see, that isn't far above or below him or under water, and that he hasn't just given up on.
	 */
	static boolean quarry(LivingEntity player, ZFighterPower.Live live, LivingEntity e, long now) {
		return e != player && e.isAlive() && !(e instanceof ArmorStand) && !e.isInvisible()
			&& !(e instanceof Player other && (other.isCreative() || other.isSpectator()))
			&& !(e.getId() == live.berserkIgnore && now < live.berserkIgnoreUntil)
			&& Math.abs(e.getY() - player.getY()) <= BERSERK_DROP && !e.isUnderWater()
			&& player.hasLineOfSight(e);
	}

	// ---------------------------------------------------------------- Verdant: healing

	/** Sneak+B: a Verdant lays his ki on whoever he looks at (not monsters), or himself if nobody. */
	static boolean heal(ServerPlayer player, ZFighterPower.Live live) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		EntityHitResult aimed = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(HEAL_RANGE)),
			player.getBoundingBox().expandTowards(look.scale(HEAL_RANGE)).inflate(1.0),
			e -> e instanceof LivingEntity living && living.isAlive() && !(e instanceof Enemy) && !(e instanceof ArmorStand) && e != player,
			HEAL_RANGE * HEAL_RANGE);
		LivingEntity target = aimed != null && aimed.getEntity() instanceof LivingEntity living ? living : player;
		if (target.getHealth() >= target.getMaxHealth() && com.jaymospowers.core.Fractures.broken(target) == 0
			|| !ZFighterPower.spend(player, live, HEAL_KI)) {
			return false;
		}
		target.heal(HEAL);
		// sets broken bones too (only a Verdant's own body regrows what's lost)
		com.jaymospowers.core.Fractures.heal(target);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 12, 0.4, 0.5,
			0.4, 0.05);
		level.sendParticles(new DustParticleOptions(color(VERDANT, 0), 1.4F), true, false, target.getX(), target.getY() + 1.0, target.getZ(), 16,
			0.4, 0.6, 0.4, 0.05);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
		return true;
	}
}
