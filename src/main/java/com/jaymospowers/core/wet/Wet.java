package com.jaymospowers.core.wet;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Andy: "Add a wet status and what that would entail with different effects." Everyone near a player keeps a wetness
 * ({@link #WETNESS}, ticks of drying left) that water and rain fill and the air dries, and it shows as one of two status
 * effects: <b>Wet</b> (damp: rain, or wading to the knees) and <b>Soaked</b> (wet through: swum, waded deep, or stood
 * out in the rain long enough). What it does, each tied to something that already reads water:
 * <ul>
 * <li><b>Cold</b> (Jaymo's Kitchen's body heat): Wet chills as before; Soaked chills twice as much, worse on cold land,
 * and soaked clothes keep only half their warmth (wool keeps all of it).</li>
 * <li><b>Fire</b>: a wet body catches fire for half as long, a soaked one a quarter ({@link #ignite}), and fire hurts it
 * less ({@link #incoming}; lava is lava). Burning dries you fast, hissing.</li>
 * <li><b>Fire powers</b>: Soaked counts as standing in the rain for the Ember power's flame, heat and kindle.</li>
 * <li><b>Lightning</b>: hurts the wet more, the soaked most, and a bolt that strikes a wet body arcs to the wet bodies
 * standing next to it ({@link #arc}).</li>
 * <li><b>Weight</b>: soaked padding (cloth, leather, furs) weighs twice what it does dry ({@code ArmourKind.load}).</li>
 * <li><b>Mud</b>: wet gear on bare earth picks up mud, not dust ({@code ArmourGrime}).</li>
 * <li><b>Rust</b>: metal armour worn wet rusts ({@link Rust}); Kitchen's leather wears soaked as it does in water.</li>
 * <li><b>Powder</b>: a soaked gunner's powder is damp, and Jaymo's Kingdoms' handgonne and arquebus often fizzle.</li>
 * </ul>
 * Drying: at the plain rate out of water and rain, faster in hot lands, by a fire and for the fire-blooded, very fast
 * in the Nether or while burning, slower in the cold.
 */
public final class Wet {
	// ---- tuning ----
	/** The most wetness (ticks of drying at the plain rate): 3 minutes. Over {@link #SOAKED_AT} you're soaked. */
	public static final int MAX = 20 * 180, SOAKED_AT = 20 * 60;
	/** How often bodies are looked at (ticks), and how far from a player (blocks). */
	static final int CHECK = 20;
	static final double RANGE = 48.0;
	/** Wetness gained a check: wading to the knees (never past {@link #SOAKED_AT}), and out in the rain. */
	static final int WADING = 200, RAIN = 40;
	/** Water this far up a body (a share of its height) soaks it as swimming does. */
	static final double DEEP = 0.5;
	/** Drying: hot lands (base temperature over {@link #HOT_LAND}), cold lands (under {@link #COLD_LAND}), a fire close by,
	 * the fire-blooded, the Nether, and burning (wetness a check). */
	static final float HOT_LAND = 1.0F, COLD_LAND = 0.3F;
	static final float HOT = 2.0F, COLD = 0.5F, FIRE = 4.0F, FIRE_BLOODED = 3.0F, NETHER = 6.0F;
	static final int BURNING = 400;
	/** A fire, lava, a lit campfire or furnace this close (blocks) dries a player. */
	static final int FIRE_REACH = 3;
	/** How long a wet and a soaked body burn, of what it would have dry. */
	static final float WET_BURN = 0.5F, SOAKED_BURN = 0.25F;
	/** Fire's harm on a wet and a soaked body (not lava's). */
	static final float WET_FIRE = 0.75F, SOAKED_FIRE = 0.5F;
	/** Lightning's harm on a wet and a soaked body; a bolt arcs this far (blocks) to wet bodies, at this share. */
	static final float WET_LIGHTNING = 1.25F, SOAKED_LIGHTNING = 1.5F, ARC_SHARE = 0.5F;
	static final double ARC_REACH = 3.0;
	/** The shown effect is put right when it drifts this far (ticks) from what wetness says. */
	static final int DRIFT = 60;

	public static Holder<MobEffect> WET, SOAKED;
	/** Ticks of drying left, saved (the effects are only how it shows). */
	public static final AttachmentType<Integer> WETNESS = AttachmentRegistry.create(JaymosPowers.id("wetness"),
		builder -> builder.persistent(Codec.INT));

	/** Set while a bolt's arc is passed on, so the arc doesn't arc again. */
	private static boolean arcing;

	private Wet() {
	}

	/** Wet and Soaked: they show the state, and do nothing on their own each tick. */
	static final class Effect extends MobEffect {
		Effect(int colour) {
			super(MobEffectCategory.NEUTRAL, colour);
		}
	}

	public static void init() {
		WET = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id("wet")),
			new Effect(0x6FA8DC));
		SOAKED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id("soaked")),
			new Effect(0x2F5F9E));
		ServerTickEvents.END_SERVER_TICK.register(Wet::tick);
	}

	// ---- asking ----

	/** Ticks of drying {@code body} has left (0: dry). */
	public static int wetness(Entity body) {
		Integer w = body.getAttached(WETNESS);
		return w == null ? 0 : w;
	}

	/** 0 dry, 1 wet, 2 soaked. */
	public static int level(Entity body) {
		int w = wetness(body);
		return w <= 0 ? 0 : w > SOAKED_AT ? 2 : 1;
	}

	public static boolean wet(Entity body) {
		return wetness(body) > 0;
	}

	public static boolean soaked(Entity body) {
		return wetness(body) > SOAKED_AT;
	}

	/** For other mods (Jaymo's Kingdoms' downpours, by reflection): {@code body} is wet for at least {@code ticks} from now. */
	public static void soak(LivingEntity body, int ticks) {
		if (ticks > wetness(body)) {
			set(body, Math.min(MAX, ticks));
		}
	}

	// ---- every check ----

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Set<LivingEntity> seen = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(RANGE), Wet::keeps)) {
					if (seen.add(body)) {
						weather(level, body);
					}
				}
			}
		}
	}

	/** Whom wetness is kept for: not what lives in water, and not armour stands. */
	private static boolean keeps(LivingEntity body) {
		return body.isAlive() && !(body instanceof ArmorStand) && !body.is(EntityTypeTags.AQUATIC) && !body.isSpectator();
	}

	private static void weather(ServerLevel level, LivingEntity body) {
		int was = wetness(body);
		int now = was;
		if (body.isInWater()) {
			if (body.isUnderWater() || body.isSwimming() || body.getFluidHeight(FluidTags.WATER) > body.getBbHeight() * DEEP) {
				now = MAX;
			} else {
				// wading: the legs are wet, the rest isn't yet
				now = Math.max(now, Math.min(SOAKED_AT, now + WADING));
			}
		} else if (body.isInWaterOrRain()) {
			now = Math.min(MAX, now + RAIN);
		} else if (now > 0) {
			now -= Math.round(CHECK * dryRate(level, body));
		}
		if (body.isOnFire() && now > 0) {
			now -= BURNING;
			level.sendParticles(ParticleTypes.CLOUD, body.getX(), body.getY() + body.getBbHeight() * 0.6, body.getZ(), 3, 0.25, 0.3, 0.25, 0.02);
			level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.3F, 1.6F);
		}
		now = Math.max(0, now);
		if (now != was) {
			set(body, now);
		} else {
			show(body, now);
		}
	}

	/** How many times the plain rate {@code body} dries at here. */
	static float dryRate(ServerLevel level, LivingEntity body) {
		BlockPos at = body.blockPosition();
		float rate = 1.0F;
		if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, at)) {
			// the Nether (where water boils away)
			return NETHER;
		}
		float temperature = level.getBiome(at).value().getBaseTemperature();
		if (temperature > HOT_LAND && level.canSeeSky(at) && level.isBrightOutside()) {
			rate *= HOT;
		} else if (temperature < COLD_LAND) {
			rate *= COLD;
		}
		if (body.fireImmune() || body instanceof Player p && com.jaymospowers.power.ember.RunningHot.user(p)) {
			rate *= FIRE_BLOODED;
		}
		if (body instanceof Player && byFire(level, at)) {
			rate *= FIRE;
		}
		return rate;
	}

	/** A fire, lava, a lit campfire or a lit furnace close by. */
	private static boolean byFire(ServerLevel level, BlockPos at) {
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-FIRE_REACH, -1, -FIRE_REACH), at.offset(FIRE_REACH, 2, FIRE_REACH))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA)
				|| (state.getBlock() instanceof CampfireBlock || state.getBlock() instanceof AbstractFurnaceBlock)
				&& state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT)) {
				return true;
			}
		}
		return false;
	}

	private static void set(LivingEntity body, int wetness) {
		if (wetness <= 0) {
			body.removeAttached(WETNESS);
		} else {
			body.setAttached(WETNESS, wetness);
		}
		show(body, wetness);
	}

	/**
	 * Puts the shown effect right: Wet counting down to dry, or Soaked counting down to only wet. The effect's own
	 * countdown keeps pace at the plain rate, so it's only put right when it drifts.
	 */
	private static void show(LivingEntity body, int wetness) {
		Holder<MobEffect> want = wetness <= 0 ? null : wetness > SOAKED_AT ? SOAKED : WET;
		Holder<MobEffect> other = want == SOAKED ? WET : SOAKED;
		if (want == null) {
			body.removeEffect(WET);
			body.removeEffect(SOAKED);
			return;
		}
		body.removeEffect(other);
		int duration = want == SOAKED ? wetness - SOAKED_AT : wetness;
		MobEffectInstance shown = body.getEffect(want);
		if (shown == null || Math.abs(shown.getDuration() - duration) > DRIFT) {
			body.forceAddEffect(new MobEffectInstance(want, duration, 0, false, false, true), null);
		}
	}

	// ---- what it does ----

	/** From {@code Entity.igniteForTicks} (mixin): a wet body catches for less long. */
	public static int ignite(Entity body, int ticks) {
		if (ticks <= 0) {
			return ticks;
		}
		int level = level(body);
		return level == 2 ? Math.max(1, Math.round(ticks * SOAKED_BURN)) : level == 1 ? Math.max(1, Math.round(ticks * WET_BURN)) : ticks;
	}

	/** From {@code LivingEntity.hurtServer}'s head (mixin): fire hurts the wet less, lightning more (and arcs). */
	public static float incoming(LivingEntity body, DamageSource source, float damage) {
		int level = level(body);
		if (level == 0 || damage <= 0.0F) {
			return damage;
		}
		if (source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypes.LAVA)) {
			return damage * (level == 2 ? SOAKED_FIRE : WET_FIRE);
		}
		if (source.is(DamageTypeTags.IS_LIGHTNING)) {
			float hurt = damage * (level == 2 ? SOAKED_LIGHTNING : WET_LIGHTNING);
			if (!arcing && body.level() instanceof ServerLevel server) {
				arc(server, body, source, hurt);
			}
			return hurt;
		}
		return damage;
	}

	/** A bolt that struck a wet body runs on into the wet bodies beside it, once, at {@link #ARC_SHARE}. */
	static void arc(ServerLevel level, LivingEntity struck, DamageSource source, float damage) {
		arcing = true;
		try {
			for (LivingEntity next : level.getEntitiesOfClass(LivingEntity.class, struck.getBoundingBox().inflate(ARC_REACH),
				e -> e != struck && e.isAlive() && wet(e) && e.distanceTo(struck) <= ARC_REACH)) {
				next.hurtServer(level, source, damage * ARC_SHARE);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, next.getX(), next.getY() + next.getBbHeight() * 0.5, next.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
			}
		} finally {
			arcing = false;
		}
	}
}
