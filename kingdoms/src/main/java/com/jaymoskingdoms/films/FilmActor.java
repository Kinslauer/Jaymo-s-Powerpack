package com.jaymoskingdoms.films;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A lore film's actor ({@link LoreFilms}): one GeckoLib body with elbows and knees that plays every person in a film,
 * in whatever 64x64 skin it's given, moved by the humanoid move library ({@link Moves}). It only ever exists on a
 * player's own game while a film plays there ({@code LoreFilmClient} makes them); it is never spawned, saved or sent.
 * The body, the skins and the animations are our own ({@code kingdoms/tools/gen_film_actor.py}).
 */
public class FilmActor extends PathfinderMob implements GeoEntity {
	/** Its bodies: wide arms, slim arms, or a piglin's head (in the game's zombified piglin texture). */
	public static final int WIDE = 0, SLIM = 1, PIGLIN = 2;
	/** What a finisher took off it. */
	public static final int NO_HEAD = 1, NO_RIGHT_ARM = 2;
	/** The cloak's tethers: how many, how fast one runs out to what it's thrown at and comes back (a share of the way, a tick). */
	public static final int TETHERS = 5;
	static final float TETHER_OUT = 0.5F, TETHER_IN = 0.35F;
	/** Slower than this (blocks a tick) it stands. */
	static final double STILL = 0.02;

	public static EntityType<FilmActor> TYPE;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** {@link #orc} for a Sylvan elf's ears instead of an orc's features. */
	public static final int ELF = 3;
	/** Its skin ({@code textures/entity/film/<skin>.png}), body, an orc's features (0 none, 1 or 2), what's gone, and whether it wears the cloak. */
	public String skin = "soldier_0";
	public int body = WIDE, orc, gone;
	public boolean cloak;
	/** The cloak is in its ribbons (at work), not whole; and how long until it has set whole again (ticks; 0 not setting). */
	public boolean ribbons;
	private int setting;
	/** The loops it stands and moves in. */
	public String still = Moves.IDLE, moving = Moves.RUN;
	private String gait = Moves.IDLE;
	private float gaitRate = 1.0F;
	private Vec3 last = Vec3.ZERO;
	/** Each tether: what it has hold of (null once let go), where that was, and how far out it is (0 to 1), now and a tick ago. */
	private final @Nullable Vec3[] tetherTo = new Vec3[TETHERS];
	private final Vec3[] tetherAt = new Vec3[TETHERS];
	private final float[] reach = new float[TETHERS], reachO = new float[TETHERS];
	/** Lit as if by this much block light at the least (0 to 15): polished steel that still catches the fires at night. */
	public int shine;
	/** Its blade is wind-imbued: lit currents run along it and trail off it. */
	public boolean wind;
	/** Where the renderer last drew its fist and its blade's point (the world's), and the tick it did. */
	public Vec3 fistAt = Vec3.ZERO, pointAt = Vec3.ZERO;
	public int bladeSeen = -100;
	/** The wind on a blade: lit motes along it a tick, how often a current curls off it and a gust turns round it (ticks), how fast they drift. */
	static final int WIND_MOTES = 3, WIND_CURRENT = 2, WIND_GUST = 10;
	static final double WIND_DRIFT = 0.06;

	public FilmActor(EntityType<? extends FilmActor> type, Level level) {
		super(type, level);
		this.setNoAi(true);
		this.setSilent(true);
		this.noPhysics = true;
		for (int i = 0; i < TETHERS; i++) {
			this.tetherAt[i] = Vec3.ZERO;
		}
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("film_actor"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<FilmActor>of(FilmActor::new, MobCategory.MISC).sized(0.6F, 1.8F).noSave().noSummon().clientTrackingRange(8).build(key));
		FabricDefaultAttributeRegistry.register(TYPE, PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0));
	}

	/**
	 * The cloak comes apart into its ribbons ({@code loose}: they wind round the body and fly free), or winds back over the
	 * body and sets whole again.
	 */
	public void cloak(boolean loose) {
		if (loose) {
			this.ribbons = true;
			this.setting = 0;
			this.triggerAnim("cloak", Moves.CLOAK_PART);
		} else if (this.ribbons && this.setting == 0) {
			this.setting = Moves.CLOAK_FORM_TICKS;
			this.triggerAnim("cloak", Moves.CLOAK_FORM);
		}
	}

	/** The cloak is whole and on its back at once, every cord in: for a move the whole body turns over in, so it goes with it. */
	public void gather() {
		this.ribbons = false;
		this.setting = 0;
		this.stopTriggeredAnim("cloak", null);
		for (int i = 0; i < TETHERS; i++) {
			this.tetherTo[i] = null;
			this.reach[i] = this.reachO[i] = 0.0F;
		}
	}

	/** Throws tether {@code which} at {@code to} (null lets go: it comes back in). */
	public void tether(int which, @Nullable Vec3 to) {
		this.tetherTo[which] = to;
		if (to != null) {
			this.tetherAt[which] = to;
		}
	}

	/** Where tether {@code which} runs to, and how far out it is this frame (0 is in). */
	public Vec3 tetherAt(int which) {
		return this.tetherAt[which];
	}

	public float tetherReach(int which, float partialTicks) {
		return this.reachO[which] + (this.reach[which] - this.reachO[which]) * partialTicks;
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 now = this.position();
		double speed = Math.sqrt((now.x - this.last.x) * (now.x - this.last.x) + (now.z - this.last.z) * (now.z - this.last.z));
		this.last = now;
		boolean going = speed > STILL;
		this.gait = going ? this.moving : this.still;
		this.gaitRate = going ? Moves.rate(this.moving, speed, this.getScale()) : 1.0F;
		if (this.setting > 0 && --this.setting == 0) {
			this.ribbons = false;
			this.stopTriggeredAnim("cloak", null);
		}
		if (this.wind && this.level().isClientSide() && this.tickCount - this.bladeSeen < 3 && !this.getMainHandItem().isEmpty()) {
			this.windBlade();
		}
		for (int i = 0; i < TETHERS; i++) {
			this.reachO[i] = this.reach[i];
			this.reach[i] += this.tetherTo[i] != null ? (1.0F - this.reach[i]) * TETHER_OUT : -this.reach[i] * TETHER_IN;
		}
	}

	/** The wind on its blade, this tick: lit motes down its length, a current curling off it, and now and then a gust turning round it. */
	private void windBlade() {
		Vec3 along = this.pointAt.subtract(this.fistAt);
		for (int i = 0; i < WIND_MOTES; i++) {
			Vec3 p = this.fistAt.add(along.scale(0.25 + this.random.nextDouble() * 0.85));
			this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0.0, 0.0, 0.0);
		}
		if (this.tickCount % WIND_CURRENT == 0) {
			// round the blade: off one side of it, turning with the tick
			double turn = this.tickCount * 0.9;
			Vec3 side = along.cross(new Vec3(Math.cos(turn), Math.sin(turn) * 0.6, Math.sin(turn))).normalize();
			Vec3 p = this.fistAt.add(along.scale(0.35 + this.random.nextDouble() * 0.7)).add(side.scale(0.12));
			this.level().addParticle(ParticleTypes.SCRAPE, p.x, p.y, p.z, side.x * WIND_DRIFT * 10.0, side.y * WIND_DRIFT * 10.0 + 0.2, side.z * WIND_DRIFT * 10.0);
		}
		if (this.tickCount % WIND_GUST == 0) {
			Vec3 p = this.fistAt.add(along.scale(0.7));
			this.level().addParticle(ParticleTypes.SMALL_GUST, p.x, p.y, p.z, 0.0, 0.0, 0.0);
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<FilmActor>("move", 3, test -> {
			FilmActor actor = test.animatable();
			test.setControllerSpeed(actor.gaitRate);
			return test.setAndContinue(Moves.loop(actor.gait));
		}));
		controllers.add(Moves.actions(new AnimationController<FilmActor>("action", 2, test -> PlayState.STOP)));
		controllers.add(Moves.cloak(new AnimationController<FilmActor>("cloak", 0, test -> PlayState.STOP)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
