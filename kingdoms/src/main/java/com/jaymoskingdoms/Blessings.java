package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.509) What the patrons give (docs/Patrons-Plan.md section 6): a passive blessing at tiers 1 to 3 and the active boon at
 * tier 4 (the boon key, every three days). Each tier keeps the ones below. Every blessing is a small function the existing system
 * calls where it already decides something (damage, sneaking, prices, guards, locks, wear...); the ones that need doing each
 * second are in {@link #tick}. Nothing here is saved; timed boon states are end times in maps, cleared on stop and disconnect.
 */
public final class Blessings {
	// ---- tuning ----------------------------------------------------------------------------------------------------------------
	static final int HEARTH_CALM_TICKS = 200, HEARTH_MEND_TICKS = 200;
	static final float HEARTH_MEND = 0.5F;
	static final float GENTLE_HANDS = 0.2F;
	static final int TENDED_REGEN_TICKS = 200, TENDED_ABSORB_TICKS = 1200;
	static final double HEARTHFIRE_RANGE = 12.0;
	static final float HEARTHFIRE_HEAL = 6.0F;
	static final float SOUND_STEEL_SPARE = 0.25F;
	static final double TRUE_STRIKE_ZONE = 0.75;
	static final float RICH_SEAM_CHANCE = 0.15F;
	static final int FATHERS_FIRE_TICKS = 3600;
	static final double MOTHERS_BREATH = 3.0, CALM_RANGE = 32.0;
	static final int WHALE_ROAD_EVERY = 3600, WHALE_ROAD_MIN = 40, WHALE_ROAD_MAX = 64;
	static final double WHALE_ROAD_CLEAR = 128.0;
	static final float WHALE_ROAD_CHANCE = 0.2F;
	static final int EMBRACE_TICKS = 6000;
	static final double SOFT_TREAD = 0.1;
	static final int CLEAN_CUTS_EXTRA = 1;
	static final double HUNTERS_EYE_RANGE = 1.5, HUNTERS_EYE_SENSE = 64.0;
	static final int WILD_HUNT_TICKS = 2400;
	static final float WILD_HUNT_DAMAGE = 0.3F;
	static final int WARDENS_EYE_LIGHT = 4, NIGHT_VISION_TICKS = 400, NIGHT_VISION_REFRESH = 220;
	static final float GRAVE_BLOWS = 0.25F;
	static final double DAWNLIGHT_RANGE = 16.0;
	static final int DAWNLIGHT_BURN = 8;
	static final float DAWNLIGHT_DAMAGE = 8.0F;
	static final float SUN_REP = 0.15F, SUN_HALT = 2.0F, SUN_VALOR = 0.2F;
	static final int SUN_PARTY_EXTRA = 2;
	static final double SUN_MUSTER_RANGE = 16.0;
	static final int SUN_MUSTER_TICKS = 2400;
	static final float QUICK_FINGERS = 0.15F, ASH_SKIN = 0.35F, SHORT_MEMORY = 0.5F;
	static final int SMOKE_TICKS = 600;
	static final double PLAIN_SIGHT_RANGE = 12.0;
	static final int PLAIN_SIGHT_EVERY = 40;
	static final float FLOCK_PRICE = 0.05F;
	static final int FLOCK_HEARTS = 1;
	static final float CERTAINTY = 0.2F, BELIEVED_GAIN = 2.0F, BELIEVED_LOSS = 0.5F, BELIEVED_HALT = 1.5F;
	static final int BELIEVED_SEARCH = 1;
	static final double JUDGEMENT_RANGE = 24.0;
	static final int JUDGEMENT_GLOW = 600;
	/** Wild Hunt silences steps: the sneak factor goes to the floor. */
	private static final double SILENT = 10.0;

	private static final String BREATH_ID = "patron_breath";

	/** The end times (game time) of the timed boons, and the hurt/mend clocks. Not saved. */
	private static final Map<UUID, Long> FATHERS_FIRE = new HashMap<>(), WILD_HUNT = new HashMap<>(), SMOKE = new HashMap<>();
	private static final Map<UUID, Long> HURT_AT = new HashMap<>(), NEXT_MEND = new HashMap<>(), NEXT_WHALE = new HashMap<>();
	private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();

	private Blessings() {
	}

	static void forget(UUID id) {
		FATHERS_FIRE.remove(id);
		WILD_HUNT.remove(id);
		SMOKE.remove(id);
		HURT_AT.remove(id);
		NEXT_MEND.remove(id);
		NEXT_WHALE.remove(id);
		LAST_HEALTH.remove(id);
	}

	static void clear() {
		FATHERS_FIRE.clear();
		WILD_HUNT.clear();
		SMOKE.clear();
		HURT_AT.clear();
		NEXT_MEND.clear();
		NEXT_WHALE.clear();
		LAST_HEALTH.clear();
	}

	private static boolean on(Map<UUID, Long> map, Entity e) {
		Long until = map.get(e.getUUID());
		return until != null && e.level().getGameTime() < until;
	}

	private static boolean has(Player p, Patron patron, int tier) {
		return Patrons.blessed(p, patron, tier);
	}

	// ---- the Hearthkeeper ------------------------------------------------------------------------------------------------------

	/** Back on Their Feet (tier 3): someone you bring round gets up strengthened. */
	static void broughtRound(ServerPlayer player, LivingEntity body) {
		if (has(player, Patron.HEARTHKEEPER, 3) && (body instanceof Villager || body instanceof Folk)) {
			body.addEffect(new MobEffectInstance(MobEffects.REGENERATION, TENDED_REGEN_TICKS, 1));
			body.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, TENDED_ABSORB_TICKS, 0));
		}
	}

	private static void heal(LivingEntity e, float amount) {
		if (PowersCompat.healEveryPart(e, amount) < 0.0F) {
			e.heal(amount);
		}
	}

	// ---- the Anvil-Father ---------------------------------------------------------------------------------------------------------

	/** True Strike (tier 2): the Smithing Anvil's perfect zone is wider. */
	static double zoneBonus(ServerPlayer smith) {
		// (0.7.513) the Anvil-Father's trial and scar narrow it
		return (has(smith, Patron.ANVIL_FATHER, 2) ? TRUE_STRIKE_ZONE : 0.0) + Turning.zone(smith);
	}

	/** Sound Steel (tier 1) and Father's Fire: a point of wear on what is in your hands, spared some of the time (or all of it). */
	public static int wear(ItemStack stack, ServerPlayer player, int wear) {
		if (wear <= 0 || stack != player.getMainHandItem() && stack != player.getOffhandItem()) {
			return wear;
		}
		if (on(FATHERS_FIRE, player)) {
			return 0;
		}
		// (0.7.513) the Anvil-Father's trial and scar: tools wear faster
		wear = Turning.wear(player, wear);
		if (!has(player, Patron.ANVIL_FATHER, 1)) {
			return wear;
		}
		int kept = 0;
		for (int i = 0; i < wear; i++) {
			if (player.getRandom().nextFloat() >= SOUND_STEEL_SPARE) {
				kept++;
			}
		}
		return kept;
	}

	/** Rich Seam (tier 3) and Father's Fire: an ore you mined drops again. */
	static void richSeam(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		if (player.preventsBlockDrops() || !player.hasCorrectToolForDrops(state)) {
			return;
		}
		boolean fire = on(FATHERS_FIRE, player);
		if (!fire && !(has(player, Patron.ANVIL_FATHER, 3) && player.getRandom().nextFloat() < RICH_SEAM_CHANCE)) {
			return;
		}
		ItemStack tool = player.getMainHandItem();
		List<ItemStack> drops = Block.getDrops(state, level, pos, be, player, tool);
		for (ItemStack drop : drops) {
			if (drop.is(state.getBlock().asItem())) {
				// (silk touch took the block itself: no second one)
				return;
			}
		}
		for (ItemStack drop : drops) {
			Block.popResource(level, pos, drop);
		}
		level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.1);
	}

	// ---- the Deep Mother -------------------------------------------------------------------------------------------------------------

	private static boolean aboard(Player p) {
		return com.jaymoskingdoms.sailing.Ships.aboard(p) != null || p.getVehicle() instanceof AbstractBoat;
	}

	/** Calm Water (tier 2): no storm bolt falls within range of someone in the water or aboard. */
	public static boolean calmsSea(ServerLevel level, BlockPos pos) {
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && has(p, Patron.DEEP_MOTHER, 2) && (p.isInWater() || aboard(p))
				&& p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= CALM_RANGE * CALM_RANGE) {
				return true;
			}
		}
		return false;
	}

	// ---- the Green Huntsman ------------------------------------------------------------------------------------------------------------

	/** Soft Tread (tier 1) and Wild Hunt: how much less of their sight sneaking leaves you. */
	static double sneak(Player player) {
		if (on(WILD_HUNT, player)) {
			return SILENT;
		}
		return has(player, Patron.GREEN_HUNTSMAN, 1) ? SOFT_TREAD : 0.0;
	}

	/** Hunter's Eye (tier 3): a legend's call comes from farther. */
	static double huntRange(Player player, double base) {
		return has(player, Patron.GREEN_HUNTSMAN, 3) ? base * HUNTERS_EYE_RANGE : base;
	}

	// ---- the Crowned Sun -----------------------------------------------------------------------------------------------------------------

	/** Valour's Light (tier 2): Valor feats are worth more. */
	static float valorGrowth(LivingEntity holder) {
		return (holder instanceof Player p && has(p, Patron.CROWNED_SUN, 2) ? 1.0F + SUN_VALOR : 1.0F) * (holder instanceof Player p ? Turning.valor(p) : 1.0F);
	}

	/** Banner-Bearer (tier 3): a war party you call up musters more. */
	static int partyExtra(Player player) {
		return has(player, Patron.CROWNED_SUN, 3) ? SUN_PARTY_EXTRA : 0;
	}

	/** Sun's Favour (tier 1) and Iron Certainty: good name is won faster with any realm that is not a haven. */
	static int reputation(Faction kingdom, ServerPlayer player, int by) {
		// (0.7.513) the Crowned Sun's trial: good name comes slower
		by = Turning.reputation(player, by);
		if (by > 0 && has(player, Patron.CROWNED_SUN, 1) && !Havens.is(player.level().getServer(), kingdom)) {
			return Math.max(1, Math.round(by * (1.0F + SUN_REP)));
		}
		return by;
	}

	/** Sun's Favour (tier 1) and Iron Certainty (tier 3): guards halt you less often. */
	static long haltGap(Player player, @Nullable Faction f, long ticks) {
		float m = Turning.halt(player);
		if (has(player, Patron.CROWNED_SUN, 1)) {
			m *= SUN_HALT;
		}
		if (truthRealm(player, f) && has(player, Patron.TRUTH, 3)) {
			m *= BELIEVED_HALT;
		}
		return (long)(ticks * m);
	}

	/** Iron Certainty (tier 3): guards of a Truth realm search you less. */
	static int searchEase(Player player, @Nullable Faction f) {
		return (truthRealm(player, f) && has(player, Patron.TRUTH, 3) ? BELIEVED_SEARCH : 0) - Turning.search(player);
	}

	private static boolean truthRealm(Player player, @Nullable Faction f) {
		if (f == null || player.level().getServer() == null) {
			return false;
		}
		Faiths.RealmFaith rf = Faiths.realmFaith(player.level().getServer(), f);
		return rf != null && rf.patron() == Patron.TRUTH;
	}

	// ---- the Ash Whisperer ---------------------------------------------------------------------------------------------------------------

	/** Quick Fingers (tier 1): a pickpocket's hold is shorter. */
	static float pickHold(Player player) {
		return (has(player, Patron.ASH_WHISPERER, 1) ? 1.0F - QUICK_FINGERS : 1.0F) * Turning.pickHold(player);
	}

	/** Quick Fingers (tier 1): a lock's sweet spot is wider. */
	static float lockWidth(Player player) {
		return (has(player, Patron.ASH_WHISPERER, 1) ? 1.0F + QUICK_FINGERS : 1.0F) * Turning.lockWidth(player);
	}

	/** Short Memories (tier 3): guards hunt you for less time after a crime. */
	static long wantedTicks(Player player, long ticks) {
		return Turning.wanted(player, has(player, Patron.ASH_WHISPERER, 3) ? Math.round(ticks * SHORT_MEMORY) : ticks);
	}

	/** Smoke and Whispers: nobody bears witness. */
	static boolean unseen(Player player) {
		return on(SMOKE, player);
	}

	// ---- The Truth ----------------------------------------------------------------------------------------------------------------------------

	/** Of the Flock (tier 2): this villager is of the Truth and a human or a cleric. */
	static boolean flock(AbstractVillager villager, Player player) {
		return has(player, Patron.TRUTH, 2) && Faiths.of(villager) == Patron.TRUTH
			&& (Accusations.priest(villager) || Faiths.human(VillagerHeritage.of(villager)));
	}

	/** Of the Flock (tier 2): one more heart on a good chat. */
	public static int flockHearts(Villager villager, ServerPlayer player, int gained) {
		return gained > 0 && flock(villager, player) ? FLOCK_HEARTS : 0;
	}

	/** Iron Certainty (tier 3): a true accusation wins more. */
	static int accuseGain(Player accuser, int gain) {
		return Math.round((has(accuser, Patron.TRUTH, 3) ? Math.round(gain * BELIEVED_GAIN) : gain) * Turning.accuse(accuser));
	}

	/** Iron Certainty (tier 3): a false one costs less. */
	static int accuseLoss(Player accuser, int loss) {
		return has(accuser, Patron.TRUTH, 3) ? Math.round(loss * BELIEVED_LOSS) : loss;
	}

	/** Iron Certainty (tier 3): the priests are deaf for half as long. */
	static long deafTicks(Player accuser, long ticks) {
		return has(accuser, Patron.TRUTH, 3) ? ticks / 2 : ticks;
	}

	// ---- blows -------------------------------------------------------------------------------------------------------------------------------

	/**
	 * Grave Blows, Ash-Skinned, Iron Certainty and Wild Hunt, for every blow to {@code target} (from the hurt mixin, right after the
	 * heritages): what a patron's gift does to a blow taken or dealt.
	 */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (target.level().isClientSide()) {
			return amount;
		}
		if (target instanceof Player hurt) {
			if (has(hurt, Patron.ASH_WHISPERER, 2) && source.is(DamageTypeTags.IS_FIRE)) {
				amount *= 1.0F - ASH_SKIN;
			}
			if (has(hurt, Patron.TRUTH, 3) && Will.magical(source, hurt)) {
				amount *= 1.0F - CERTAINTY;
			}
		}
		Entity by = source.getEntity();
		if (by instanceof ServerPlayer attacker && by == source.getDirectEntity()) {
			if (has(attacker, Patron.LANTERN_WARDEN, 2) && (target.is(EntityTypeTags.UNDEAD) || Vampires.isVampire(target))) {
				amount *= 1.0F + GRAVE_BLOWS;
			}
			// (0.7.513) the Lantern Warden's scar: blows on the dead land weaker
			if (target.is(EntityTypeTags.UNDEAD) || Vampires.isVampire(target)) {
				amount *= Turning.graveBlows(attacker);
			}
			if (on(WILD_HUNT, attacker) && beast(target)) {
				amount *= 1.0F + WILD_HUNT_DAMAGE;
			}
		}
		return amount;
	}

	private static boolean beast(LivingEntity e) {
		return e instanceof Animal || com.jaymoskingdoms.monsters.Monsters.legend(e.getType());
	}

	// ---- every second --------------------------------------------------------------------------------------------------------------------------

	/** Re-applies what a tier change moves (the attribute modifiers). */
	static void refresh(ServerPlayer sp) {
		Skills.modifier(sp, Attributes.OXYGEN_BONUS, BREATH_ID, has(sp, Patron.DEEP_MOTHER, 1) ? MOTHERS_BREATH : 0.0, AttributeModifier.Operation.ADD_VALUE);
	}

	static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		int count = server.getTickCount();
		FATHERS_FIRE.values().removeIf(t -> t <= now);
		WILD_HUNT.values().removeIf(t -> t <= now);
		SMOKE.values().removeIf(t -> t <= now);
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			if (!sp.isAlive() || sp.isSpectator()) {
				continue;
			}
			Patrons.State s = Patrons.state(sp);
			// (the breath is kept on every second: it also takes itself off when they are no longer blessed)
			refresh(sp);
			Patron p = s.patronOf();
			if (p == null || s.tier() < 1) {
				continue;
			}
			ServerLevel level = sp.level();
			switch (p) {
				case HEARTHKEEPER -> warmth(sp, now);
				case LANTERN_WARDEN -> wardensEye(sp, level);
				case TRUTH -> {
					if (count % PLAIN_SIGHT_EVERY == 0) {
						plainSight(sp, level);
					}
				}
				case GREEN_HUNTSMAN -> {
					if (s.tier() >= 3 && count % PLAIN_SIGHT_EVERY == 0) {
						huntersEye(sp, level);
					}
				}
				case DEEP_MOTHER -> {
					if (s.tier() >= 3) {
						whaleRoad(sp, level, now);
					}
				}
				default -> {
				}
			}
		}
	}

	/** Hearth's Warmth (tier 1): out of a fight, every part heals a little every few seconds. */
	private static void warmth(ServerPlayer sp, long now) {
		UUID id = sp.getUUID();
		float hp = sp.getHealth();
		Float last = LAST_HEALTH.put(id, hp);
		if (last != null && hp < last - 0.01F) {
			HURT_AT.put(id, now);
		}
		if (now - HURT_AT.getOrDefault(id, Long.MIN_VALUE / 2) < HEARTH_CALM_TICKS || now < NEXT_MEND.getOrDefault(id, 0L)) {
			return;
		}
		if (Healing.needsTreatment(sp) && !Downed.isDowned(sp)) {
			NEXT_MEND.put(id, now + HEARTH_MEND_TICKS);
			heal(sp, HEARTH_MEND);
		}
	}

	/** Warden's Eye (tier 1): night vision in the dark, topped up before it can flicker. */
	private static void wardensEye(ServerPlayer sp, ServerLevel level) {
		int light = level.getMaxLocalRawBrightness(BlockPos.containing(sp.getEyePosition()));
		if (light > WARDENS_EYE_LIGHT) {
			return;
		}
		MobEffectInstance now = sp.getEffect(MobEffects.NIGHT_VISION);
		if (now == null || now.getDuration() < NIGHT_VISION_REFRESH) {
			sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false, false));
		}
	}

	/** Plain Sight (tier 1): the cursed among townsfolk show a mark to you alone. Players are never shown. */
	private static void plainSight(ServerPlayer sp, ServerLevel level) {
		AABB box = sp.getBoundingBox().inflate(PLAIN_SIGHT_RANGE);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && (x instanceof Villager || x instanceof Folk))) {
			if ((Vampires.isVampire(e) || Lycans.cursed(e)) && sp.hasLineOfSight(e)) {
				level.sendParticles(sp, ParticleTypes.SOUL, true, false, e.getX(), e.getY(1.2), e.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
			}
		}
	}

	/** Hunter's Eye (tier 3): a legend within range shows a faint mark. */
	private static void huntersEye(ServerPlayer sp, ServerLevel level) {
		AABB box = sp.getBoundingBox().inflate(HUNTERS_EYE_SENSE);
		for (Mob m : level.getEntitiesOfClass(Mob.class, box, x -> x.isAlive() && com.jaymoskingdoms.monsters.Monsters.legend(x.getType()))) {
			level.sendParticles(sp, ParticleTypes.GLOW, true, false, m.getX(), m.getY(1.0), m.getZ(), 2, 0.3, 0.4, 0.3, 0.0);
		}
	}

	/** Whale Road (tier 3): aboard on the deep ocean, a pod of great whales now and then surfaces off the bow. */
	private static void whaleRoad(ServerPlayer sp, ServerLevel level, long now) {
		if (!aboard(sp) || !level.getBiome(sp.blockPosition()).is(BiomeTags.IS_DEEP_OCEAN)) {
			return;
		}
		UUID id = sp.getUUID();
		if (now < NEXT_WHALE.getOrDefault(id, 0L)) {
			return;
		}
		NEXT_WHALE.put(id, now + WHALE_ROAD_EVERY);
		if (sp.getRandom().nextFloat() >= WHALE_ROAD_CHANCE) {
			return;
		}
		AABB box = sp.getBoundingBox().inflate(WHALE_ROAD_CLEAR);
		if (!level.getEntitiesOfClass(com.jaymoskingdoms.whaling.GreatWhale.class, box).isEmpty()) {
			return;
		}
		int pod = 1 + sp.getRandom().nextInt(2);
		for (int i = 0; i < pod; i++) {
			double angle = sp.getRandom().nextDouble() * Math.PI * 2.0;
			double dist = WHALE_ROAD_MIN + sp.getRandom().nextDouble() * (WHALE_ROAD_MAX - WHALE_ROAD_MIN);
			BlockPos at = BlockPos.containing(sp.getX() + Math.cos(angle) * dist, level.getSeaLevel() - 2, sp.getZ() + Math.sin(angle) * dist);
			if (level.isLoaded(at) && level.getFluidState(at).is(FluidTags.WATER)) {
				com.jaymoskingdoms.whaling.Whaling.GREAT_WHALE.spawn(level, at, EntitySpawnReason.NATURAL);
			}
		}
	}

	// ---- the boons ------------------------------------------------------------------------------------------------------------------------------

	/** The tier 4 boon of {@code patron}, called by {@code sp}. */
	static void boon(ServerPlayer sp, Patron patron) {
		ServerLevel level = sp.level();
		long now = level.getGameTime();
		level.playSound(null, sp.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9F, 1.1F);
		switch (patron) {
			case HEARTHKEEPER -> hearthfire(sp, level);
			case ANVIL_FATHER -> {
				FATHERS_FIRE.put(sp.getUUID(), now + FATHERS_FIRE_TICKS);
				sp.addEffect(new MobEffectInstance(MobEffects.HASTE, FATHERS_FIRE_TICKS, 1));
				level.sendParticles(ParticleTypes.FLAME, sp.getX(), sp.getY(1.0), sp.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
			}
			case DEEP_MOTHER -> {
				sp.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, EMBRACE_TICKS, 0));
				sp.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, EMBRACE_TICKS, 0));
				sp.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, EMBRACE_TICKS, 0));
				level.sendParticles(ParticleTypes.NAUTILUS, sp.getX(), sp.getY(1.0), sp.getZ(), 20, 0.5, 0.7, 0.5, 0.5);
			}
			case GREEN_HUNTSMAN -> {
				WILD_HUNT.put(sp.getUUID(), now + WILD_HUNT_TICKS);
				sp.addEffect(new MobEffectInstance(MobEffects.SPEED, WILD_HUNT_TICKS, 0));
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sp.getX(), sp.getY(1.0), sp.getZ(), 16, 0.5, 0.7, 0.5, 0.0);
			}
			case LANTERN_WARDEN -> dawnlight(sp, level);
			case CROWNED_SUN -> muster(sp, level);
			case ASH_WHISPERER -> {
				SMOKE.put(sp.getUUID(), now + SMOKE_TICKS);
				sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, SMOKE_TICKS, 0, false, false, true));
				level.sendParticles(ParticleTypes.LARGE_SMOKE, sp.getX(), sp.getY(1.0), sp.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
			}
			case TRUTH -> judgement(sp, level);
		}
	}

	/** Hearthfire: you, your groupmates, your and their tamed animals, villagers and townsfolk not fighting you heal; festering eases; the downed are asked up. */
	private static void hearthfire(ServerPlayer sp, ServerLevel level) {
		AABB box = sp.getBoundingBox().inflate(HEARTHFIRE_RANGE);
		List<LivingEntity> near = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && (e instanceof Player p && !p.isSpectator() && Groups.together(sp, p)
			|| e instanceof TamableAnimal t && t.isTame() && t.getOwner() instanceof Player o && (o == sp || Groups.together(sp, o))
			|| e instanceof Villager || e instanceof Folk f && VillagerHeritage.humanFolk(f.role()) && f.getTarget() != sp)));
		if (!near.contains(sp)) {
			near.add(sp);
		}
		for (LivingEntity e : near) {
			heal(e, HEARTHFIRE_HEAL);
			PowersCompat.easeFever(e);
			if (Downed.isDowned(e)) {
				e.addTag(Downed.REVIVE_TAG);
			}
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY(0.6), e.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
		}
		level.sendParticles(ParticleTypes.FLAME, sp.getX(), sp.getY(0.2), sp.getZ(), 30, HEARTHFIRE_RANGE * 0.3, 0.1, HEARTHFIRE_RANGE * 0.3, 0.02);
	}

	/** Dawnlight: the undead and vampires (not players, thralls or your realm's folk) around burn and take magic harm. */
	private static void dawnlight(ServerPlayer sp, ServerLevel level) {
		AABB box = sp.getBoundingBox().inflate(DAWNLIGHT_RANGE);
		DamageSource light = level.damageSources().indirectMagic(sp, sp);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !(x instanceof Player)
			&& !Thralls.isThrall(x) && !friendlyFolk(sp, x) && (x.is(EntityTypeTags.UNDEAD) || Vampires.isVampire(x)))) {
			e.igniteForSeconds(DAWNLIGHT_BURN);
			e.hurtServer(level, light, DAWNLIGHT_DAMAGE);
			level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY(0.6), e.getZ(), 8, 0.3, 0.5, 0.3, 0.05);
		}
		level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY(1.0), sp.getZ(), 40, DAWNLIGHT_RANGE * 0.2, 0.8, DAWNLIGHT_RANGE * 0.2, 0.1);
	}

	/** Folk of the caster's realm (vampires among them) are spared by Dawnlight. */
	private static boolean friendlyFolk(ServerPlayer sp, LivingEntity e) {
		if (e instanceof Folk folk) {
			Faction f = Factions.get(sp.level().getServer(), folk.factionId());
			return f != null && f.member(sp.getUUID()) != null;
		}
		return false;
	}

	/** Sun's Muster: you, your groupmates and your realm's folk near get Strength I and Resistance I. */
	private static void muster(ServerPlayer sp, ServerLevel level) {
		AABB box = sp.getBoundingBox().inflate(SUN_MUSTER_RANGE);
		MinecraftServer server = level.getServer();
		List<LivingEntity> near = new ArrayList<>();
		near.add(sp);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && x != sp)) {
			if (Groups.together(sp, e)) {
				near.add(e);
			} else if (e instanceof Folk folk) {
				Faction f = Factions.get(server, folk.factionId());
				if (f != null && f.member(sp.getUUID()) != null) {
					near.add(e);
				}
			}
		}
		for (LivingEntity e : near) {
			e.addEffect(new MobEffectInstance(MobEffects.STRENGTH, SUN_MUSTER_TICKS, 0));
			e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SUN_MUSTER_TICKS, 0));
			level.sendParticles(ParticleTypes.WAX_ON, e.getX(), e.getY(1.0), e.getZ(), 8, 0.4, 0.6, 0.4, 0.0);
		}
	}

	/** Judgement: every harmful effect on you is lifted, and the cursed around you are shown for what they are. */
	private static void judgement(ServerPlayer sp, ServerLevel level) {
		for (MobEffectInstance effect : new ArrayList<>(sp.getActiveEffects())) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				sp.removeEffect(effect.getEffect());
			}
		}
		long now = level.getGameTime();
		AABB box = sp.getBoundingBox().inflate(JUDGEMENT_RANGE);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && x != sp)) {
			boolean shown = false;
			if (e instanceof ServerPlayer p) {
				if (Accusations.guilty(p)) {
					Accusations.expose(level, p, now);
					shown = true;
				}
			} else if (Vampires.isVampire(e)) {
				Vampires.expose(e, Vampires.nightIndex(level.getOverworldClockTime()));
				shown = true;
			} else if (Lycans.cursed(e)) {
				shown = true;
			}
			if (shown) {
				e.addEffect(new MobEffectInstance(MobEffects.GLOWING, JUDGEMENT_GLOW, 0));
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY(1.0), e.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
			}
		}
		level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY(1.0), sp.getZ(), 30, 0.6, 0.9, 0.6, 0.1);
	}
}
