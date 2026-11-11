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
	public static final int TETHERS = 4;
	static final float TETHER_OUT = 0.5F, TETHER_IN = 0.35F;
	/** Slower than this (blocks a tick) it stands. */
	static final double STILL = 0.02;

	public static EntityType<FilmActor> TYPE;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** Its skin ({@code textures/entity/film/<skin>.png}), body, an orc's features (0 none, 1 or 2), what's gone, and whether it wears the cloak. */
	public String skin = "soldier_0";
	public int body = WIDE, orc, gone;
	public boolean cloak;
	/** The loops it stands and moves in. */
	public String still = Moves.IDLE, moving = Moves.RUN;
	private String gait = Moves.IDLE;
	private float gaitRate = 1.0F;
	private Vec3 last = Vec3.ZERO;
	/** Each tether: what it has hold of (null once let go), where that was, and how far out it is (0 to 1), now and a tick ago. */
	private final @Nullable Vec3[] tetherTo = new Vec3[TETHERS];
	private final Vec3[] tetherAt = new Vec3[TETHERS];
	private final float[] reach = new float[TETHERS], reachO = new float[TETHERS];

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
		for (int i = 0; i < TETHERS; i++) {
			this.reachO[i] = this.reach[i];
			this.reach[i] += this.tetherTo[i] != null ? (1.0F - this.reach[i]) * TETHER_OUT : -this.reach[i] * TETHER_IN;
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
