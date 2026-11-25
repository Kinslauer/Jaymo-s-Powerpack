package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The leech's kit: hollow thorns of bone and glass filled with draughts, the apothecaries' healing ones and the hedge
 * witches' dark ones, and the blowgun and sleep darts of the same craft. Hold use a second to stick one in yourself, or use
 * it on another living body or on a corpse to stick it in them ({@link LeechKitItems.Thorn}); the empty thorn comes back.
 * <ul>
 * <li><b>Healing:</b> staunching (every cut closed), numbing (no limp, stagger or ragdoll fall for {@link #NUMB_TICKS}),
 * quickening (speed and haste, then a crash), restoring (a full heal).</li>
 * <li><b>Dark:</b> corroding (a living body is pitted for damage over time; a corpse melts to a puddle), stripping (a corpse
 * becomes a bare skeleton), rotting (a corpse rots away in seconds, a living body withers), raising (a corpse with its head
 * on rises as the user's undead, as the death school's raising does). Each use counts as a forbidden art
 * ({@link ForbiddenArts#cast}) where a realm's people see it.</li>
 * <li><b>Knockout:</b> a draught that leaves the body limp ({@link #KNOCK_TICKS}), and the blowgun's sleep darts, which carry it.</li>
 * <li><b>Mixes</b> (two filled thorns crafted together, taught by advancements): frenzy (quickening and restoring), unyielding
 * (restoring and numbing), bonewash (corroding and stripping: a corpse to a skeleton at once, a zombie into a skeleton).</li>
 * </ul>
 * They are made at the crafting table from an empty thorn and what is in the recipe, as the hedge poisons are ({@link Poisons}).
 * Without Jaymo's Powers a staunching or restoring thorn only heals hearts; without Jaymo's Kitchen the corpse uses do nothing.
 */
public final class LeechKit {
	/** How long sticking a thorn into yourself takes (ticks). */
	static final int STICK_TICKS = 20;
	/** Numbing and unyielding last (ticks). */
	static final int NUMB_TICKS = 20 * 120, UNYIELD_TICKS = 20 * 60;
	/** Quickening: speed and haste (ticks), then the crash: slowness, weakness and hunger (ticks). */
	static final int QUICK_TICKS = 20 * 30, QUICK_CRASH_TICKS = 20 * 20;
	/** Frenzy: strength and speed (ticks), then the heavy crash (ticks). */
	static final int FRENZY_TICKS = 20 * 40, FRENZY_CRASH_TICKS = 20 * 30;
	/** A knockout draught leaves the body limp (ticks). */
	static final int KNOCK_TICKS = 20 * 15;
	/** Corroding a living body: how long it is pitted (ticks), and the damage each second. */
	static final int CORRODE_TICKS = 20 * 10;
	static final float CORRODE_DAMAGE = 1.0F;
	/** A living body rotted: wither time (ticks). */
	static final int ROT_WITHER_TICKS = 20 * 10;
	/** A corpse melting to a puddle, and one rotting away (ticks). */
	static final int MELT_TICKS = 20 * 4, ROT_AWAY_TICKS = 20 * 6;
	/** The puddle a melted body leaves: how wide (blocks) and how long it lies (ticks). */
	static final float PUDDLE_RADIUS = 0.9F;
	static final int PUDDLE_TICKS = 20 * 8;
	/** A blowgun's reach (blocks) and the wait between shots (ticks). */
	static final double BLOW_RANGE = 24.0;
	static final int BLOW_COOLDOWN = 20 * 2;

	/** The thorns. {@code color} is the colour of the draught in it (and of the fumes of a dark one). */
	public enum Kind {
		STAUNCHING("staunching_thorn", 0xC8453C, false), NUMBING("numbing_thorn", 0x8FC8E8, false), QUICKENING("quickening_thorn", 0xF0D040, false),
		RESTORING("restoring_thorn", 0xE0509A, false), KNOCKOUT("knockout_thorn", 0x7A6CC0, false),
		CORRODING("corroding_thorn", 0x78B030, true), STRIPPING("stripping_thorn", 0xD8D4B8, true), ROTTING("rotting_thorn", 0x5A6A28, true),
		RAISING("raising_thorn", 0x5A2A78, true), BONEWASH("bonewash_thorn", 0xA8D8C0, true),
		FRENZY("frenzy_thorn", 0xD8481C, false), UNYIELDING("unyielding_thorn", 0xC89A38, false);

		public final String item;
		public final int color;
		/** A dark thorn is a forbidden art to use, and can't be stuck in yourself. */
		public final boolean dark;

		Kind(String item, int color, boolean dark) {
			this.item = item;
			this.color = color;
			this.dark = dark;
		}
	}

	public static final Map<Kind, Item> THORNS = new EnumMap<>(Kind.class);
	public static Item EMPTY_THORN, BLOWGUN, SLEEP_DART;

	/** Timers on a body, by name, to the game time they end. Saved with the body. */
	private static final AttachmentType<Map<String, Long>> TIMERS = AttachmentRegistry.create(JaymosKingdoms.id("leech_kit_timers"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	private static final String NUMB = "numb", UNYIELD = "unyield", CORRODE = "corrode", QUICK_CRASH = "crash_quick", FRENZY_CRASH = "crash_frenzy";
	/** Loaded bodies that carry a timer. */
	private static final Map<LivingEntity, Boolean> TRACKED = new WeakHashMap<>();

	/** Powers' hurt-limp and battered-speed modifiers, which numbing takes off (read by id: Kingdoms never compiles against Powers). */
	private static final Identifier HURT_LIMP = Identifier.fromNamespaceAndPath("jaymospowers", "hurt_limp");
	private static final Identifier BATTERED_SPEED = Identifier.fromNamespaceAndPath("jaymospowers", "wounds_battered_speed");

	/** A corpse on its way out: melting to a puddle, or rotting away. */
	private record Melt(Entity corpse, long start, long end, boolean rot) {
	}

	private static final List<Melt> MELTS = new ArrayList<>();

	private static boolean vetoAdded;

	private LeechKit() {
	}

	static void register() {
		EMPTY_THORN = item("empty_thorn", key -> new Item(new Item.Properties().setId(key).stacksTo(16)));
		for (Kind kind : Kind.values()) {
			THORNS.put(kind, item(kind.item, key -> new LeechKitItems.Thorn(new Item.Properties().setId(key).stacksTo(8), kind)));
		}
		BLOWGUN = item("blowgun", key -> new LeechKitItems.Blowgun(new Item.Properties().setId(key).stacksTo(1).durability(120)));
		SLEEP_DART = item("sleep_dart", key -> new Item(new Item.Properties().setId(key).stacksTo(32)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(EMPTY_THORN);
			for (Kind kind : Kind.values()) {
				output.accept(THORNS.get(kind));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(BLOWGUN);
			output.accept(SLEEP_DART);
		});
		ServerTickEvents.END_SERVER_TICK.register(LeechKit::tick);
		ServerLifecycleEvents.SERVER_STARTING.register(server -> addRagdollVeto());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TRACKED.clear();
			MELTS.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof LivingEntity l && l.hasAttached(TIMERS)) {
				TRACKED.put(l, Boolean.TRUE);
			}
		});
		// numbing and unyielding: a death is turned back at one heart
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!active(entity, UNYIELD)) {
				return true;
			}
			entity.setHealth(1.0F);
			if (entity.level() instanceof ServerLevel level) {
				Fx.ring(level, new Vec3(entity.getX(), entity.getY(0.5), entity.getZ()), 0.9F, Kind.UNYIELDING.color);
			}
			return false;
		});
		// a thorn used on another body (a corpse is not a living thing, so the game's own use never reaches it)
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!(stack.getItem() instanceof LeechKitItems.Thorn thorn) || player.isSpectator() || !(entity instanceof LivingEntity || KitchenCompat.isCorpse(entity))) {
				return InteractionResult.PASS;
			}
			if (!(world instanceof ServerLevel level)) {
				// (the client takes it as used, so the thorn isn't also stuck in the hand that holds it)
				return InteractionResult.SUCCESS;
			}
			if (stick(thorn.kind, player, entity, level)) {
				spend(player, stack);
				return InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.thorn.nothing"));
			}
			return InteractionResult.FAIL;
		});
	}

	private static Item item(String name, Function<ResourceKey<Item>, Item> make) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(key));
	}

	/** The thorn is used up and the empty one comes back. */
	static void spend(Player player, ItemStack stack) {
		if (player.hasInfiniteMaterials()) {
			return;
		}
		stack.shrink(1);
		player.getInventory().placeItemBackInInventory(new ItemStack(EMPTY_THORN), net.minecraft.util.Prediction.SERVER_ONLY);
	}

	// ---- sticking a thorn in -----------------------------------------------------------------------------------------

	/**
	 * {@code by} sticks a {@code kind} thorn into {@code target}, a living body or a corpse. True if it did anything (and the
	 * thorn is used up); false if it had no work to do here. A dark one is a forbidden art, whose crime follows if it took.
	 */
	static boolean stick(Kind kind, @Nullable LivingEntity by, Entity target, ServerLevel level) {
		boolean done;
		if (target instanceof LivingEntity body) {
			if (!body.isAlive()) {
				return false;
			}
			done = living(kind, by, body, level);
		} else {
			done = KitchenCompat.isCorpse(target) && corpse(kind, by, target, level);
		}
		if (done) {
			level.playSound(null, target.getX(), target.getY(0.5), target.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
			if (kind.dark && by instanceof ServerPlayer player) {
				ForbiddenArts.cast(player, level, false, 0);
			}
		}
		return done;
	}

	private static boolean living(Kind kind, @Nullable LivingEntity by, LivingEntity body, ServerLevel level) {
		Vec3 at = new Vec3(body.getX(), body.getY(0.5), body.getZ());
		switch (kind) {
			case STAUNCHING -> {
				if (PowersCompat.injuries(body).bleeding() == 0) {
					return false;
				}
				for (int i = 0; i < 16 && PowersCompat.closeCut(body); i++) {
					// (each closes the worst open cut)
				}
				bindStumps(body);
			}
			case NUMBING -> setTimer(body, NUMB, NUMB_TICKS);
			case QUICKENING -> {
				body.addEffect(new MobEffectInstance(MobEffects.SPEED, QUICK_TICKS, 1));
				body.addEffect(new MobEffectInstance(MobEffects.HASTE, QUICK_TICKS, 1));
				setTimer(body, QUICK_CRASH, QUICK_TICKS);
			}
			case RESTORING -> {
				if (body.getHealth() >= body.getMaxHealth() && !PowersCompat.injuries(body).any()) {
					return false;
				}
				if (!healAll(body)) {
					body.setHealth(body.getMaxHealth());
				}
			}
			case KNOCKOUT -> {
				if (!knockOut(body, KNOCK_TICKS)) {
					return false;
				}
			}
			case FRENZY -> {
				body.addEffect(new MobEffectInstance(MobEffects.STRENGTH, FRENZY_TICKS, 1));
				body.addEffect(new MobEffectInstance(MobEffects.SPEED, FRENZY_TICKS, 1));
				setTimer(body, FRENZY_CRASH, FRENZY_TICKS);
			}
			case UNYIELDING -> setTimer(body, UNYIELD, UNYIELD_TICKS);
			case CORRODING -> setTimer(body, CORRODE, CORRODE_TICKS);
			case ROTTING -> body.addEffect(new MobEffectInstance(MobEffects.WITHER, ROT_WITHER_TICKS, 1));
			case BONEWASH -> {
				if (!boneWash(body, level)) {
					return false;
				}
			}
			default -> {
				// (stripping and raising work on the dead)
				return false;
			}
		}
		Fx.burst(level, at.x, at.y, at.z, kind.color, 0.6F, 6, 0.3, 0.3, 0.3);
		return true;
	}

	private static boolean corpse(Kind kind, @Nullable LivingEntity by, Entity corpse, ServerLevel level) {
		// (a player's body is theirs: nothing here takes it)
		if (KitchenCompat.isPlayerCorpse(corpse) || MELTS.stream().anyMatch(m -> m.corpse() == corpse)) {
			return false;
		}
		long now = level.getGameTime();
		switch (kind) {
			case CORRODING -> MELTS.add(new Melt(corpse, now, now + MELT_TICKS, false));
			case ROTTING -> MELTS.add(new Melt(corpse, now, now + ROT_AWAY_TICKS, true));
			case STRIPPING, BONEWASH -> {
				return strip(corpse, level);
			}
			case RAISING -> {
				return KitchenCompat.headOn(corpse) && by instanceof ServerPlayer player && Necromancy.riseFor(player, level, corpse) != null;
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** The body is taken down to a bare skeleton: what it wore and carried is gone with the flesh. */
	private static boolean strip(Entity corpse, ServerLevel level) {
		if (KitchenCompat.sourceType(corpse) == EntityTypes.SKELETON) {
			return false;
		}
		Mob bones = EntityTypes.SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (bones == null) {
			return false;
		}
		bones.snapTo(corpse.getX(), corpse.getY(), corpse.getZ(), corpse.getYRot(), 0.0F);
		Entity laid = KitchenCompat.corpseOf(bones);
		if (laid == null) {
			return false;
		}
		corpse.discard();
		Fx.burst(level, corpse.getX(), corpse.getY(0.4), corpse.getZ(), Kind.STRIPPING.color, 0.7F, 10, 0.4, 0.2, 0.4);
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.SKELETON_AMBIENT, SoundSource.NEUTRAL, 0.6F, 0.6F);
		return true;
	}

	/** A zombie (or a husk, a drowned, a zombie villager) is washed down to a skeleton at once. */
	private static boolean boneWash(LivingEntity body, ServerLevel level) {
		EntityType<?> type = body.getType();
		if (type != EntityTypes.ZOMBIE && type != EntityTypes.ZOMBIE_VILLAGER && type != EntityTypes.HUSK && type != EntityTypes.DROWNED) {
			return false;
		}
		Mob bones = EntityTypes.SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (bones == null) {
			return false;
		}
		bones.snapTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), 0.0F);
		if (body.hasCustomName()) {
			bones.setCustomName(body.getCustomName());
		}
		bones.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		bones.setPersistenceRequired();
		level.addFreshEntity(bones);
		body.discard();
		level.playSound(null, bones.getX(), bones.getY(), bones.getZ(), SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 0.8F, 0.6F);
		return true;
	}

	/**
	 * {@code body} goes limp for {@code ticks}, as a sleep draught leaves it ({@link HeldFast#sleep}); a mob is also laid out
	 * on the ground through Jaymo's Kitchen's ragdoll when it is loaded. False if nothing can hold it.
	 */
	static boolean knockOut(LivingEntity body, int ticks) {
		if (!HeldFast.sleep(body, ticks)) {
			return false;
		}
		if (body instanceof Mob mob) {
			floor(mob);
		}
		return true;
	}

	// ---- timers on a body --------------------------------------------------------------------------------------------

	private static void setTimer(LivingEntity body, String name, int ticks) {
		Map<String, Long> all = new HashMap<>(body.getAttachedOrElse(TIMERS, Map.of()));
		all.put(name, body.level().getGameTime() + ticks);
		body.setAttached(TIMERS, all);
		TRACKED.put(body, Boolean.TRUE);
	}

	private static boolean active(LivingEntity body, String name) {
		Map<String, Long> all = body.getAttached(TIMERS);
		Long until = all == null ? null : all.get(name);
		return until != null && body.level().getGameTime() < until;
	}

	/** Whether {@code body} is numbed: no limp, stagger or ragdoll fall. */
	public static boolean numbed(LivingEntity body) {
		return active(body, NUMB);
	}

	private static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		if (tick % 5 == 0 && !MELTS.isEmpty()) {
			melts();
		}
		if (tick % 10 != 0 || TRACKED.isEmpty()) {
			return;
		}
		for (LivingEntity body : new ArrayList<>(TRACKED.keySet())) {
			Map<String, Long> all = body == null ? null : body.getAttached(TIMERS);
			if (all == null || all.isEmpty() || body.isRemoved() || !body.isAlive() || !(body.level() instanceof ServerLevel level)) {
				TRACKED.remove(body);
				continue;
			}
			long now = level.getGameTime();
			Map<String, Long> left = new HashMap<>();
			for (Map.Entry<String, Long> e : all.entrySet()) {
				if (now < e.getValue()) {
					left.put(e.getKey(), e.getValue());
				} else if (e.getKey().equals(QUICK_CRASH)) {
					crash(body, QUICK_CRASH_TICKS, 1, 1, 1);
				} else if (e.getKey().equals(FRENZY_CRASH)) {
					crash(body, FRENZY_CRASH_TICKS, 2, 2, 3);
				}
			}
			if (left.containsKey(NUMB)) {
				numb(body);
			}
			if (left.containsKey(CORRODE) && tick % 20 == 0) {
				body.hurtServer(level, level.damageSources().magic(), CORRODE_DAMAGE);
				level.sendParticles(Fx.miasma(Kind.CORRODING.color, 1.0F), body.getX(), body.getY(0.5), body.getZ(), 4, 0.25, 0.4, 0.25, 0.01);
			}
			if (left.size() != all.size()) {
				if (left.isEmpty()) {
					body.removeAttached(TIMERS);
					TRACKED.remove(body);
				} else {
					body.setAttached(TIMERS, left);
				}
			}
		}
	}

	/** What a quickened body pays: slowness, weakness and hunger, each a level the stated amount (ticks, then levels from 0). */
	private static void crash(LivingEntity body, int ticks, int slow, int weak, int hunger) {
		body.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, slow));
		body.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, weak));
		body.addEffect(new MobEffectInstance(MobEffects.HUNGER, ticks, hunger));
	}

	/** A numbed body walks on a hurt leg and shakes off a battering: Powers' slowing modifiers come off. */
	private static void numb(LivingEntity body) {
		AttributeInstance speed = body.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(HURT_LIMP);
			speed.removeModifier(BATTERED_SPEED);
		}
	}

	private static void melts() {
		for (Melt m : new ArrayList<>(MELTS)) {
			Entity c = m.corpse();
			if (c.isRemoved() || !(c.level() instanceof ServerLevel level)) {
				MELTS.remove(m);
				continue;
			}
			Kind kind = m.rot() ? Kind.ROTTING : Kind.CORRODING;
			level.sendParticles(Fx.miasma(kind.color, 1.3F), c.getX(), c.getY(0.3), c.getZ(), 5, 0.3, 0.15, 0.3, 0.01);
			if (level.getGameTime() < m.end()) {
				continue;
			}
			MELTS.remove(m);
			if (m.rot()) {
				c.spawnAtLocation(level, new ItemStack(Items.ROTTEN_FLESH, 1 + level.getRandom().nextInt(2)), 0.2F);
			} else {
				Fx.pool(level, c.position().add(0.0, 0.05, 0.0), PUDDLE_RADIUS, kind.color, PUDDLE_TICKS);
			}
			level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 0.8F, m.rot() ? 0.6F : 1.0F);
			c.discard();
		}
	}

	// ---- Powers' and Kitchen's side (looked up once; each does nothing without its mod) ---------------------------------

	private static @Nullable MethodHandle healAll, bindStumps, floor;
	private static boolean looked;

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			try {
				healAll = lookup.findStatic(Class.forName("com.jaymospowers.core.wounds.Wounds"), "healAll", MethodType.methodType(void.class, LivingEntity.class));
				bindStumps = lookup.findStatic(Class.forName("com.jaymospowers.core.wounds.Bleeding"), "bindStumps",
					MethodType.methodType(int.class, LivingEntity.class, boolean.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' wounds for the leech's kit (older Powers?): {}", e.toString());
			}
		}
		if (FabricLoader.getInstance().isModLoaded(KitchenCompat.MOD)) {
			try {
				floor = lookup.findStatic(Class.forName("com.jaymoskitchen.ragdoll.MobRagdolls"), "floor", MethodType.methodType(boolean.class, Mob.class, Vec3.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Kitchen's mob ragdolls for the knockout draught: {}", e.toString());
			}
		}
	}

	/** Full health, every part whole (Powers' {@code Wounds.healAll}); false without Powers. */
	private static boolean healAll(LivingEntity body) {
		look();
		try {
			if (healAll != null) {
				healAll.invoke(body);
				return true;
			}
		} catch (Throwable e) {
			// (a body Wounds can't part out: its hearts are filled by the caller)
		}
		return false;
	}

	/** Binds every bleeding stump (Powers' {@code Bleeding.bindStumps}); nothing without Powers. */
	private static void bindStumps(LivingEntity body) {
		look();
		try {
			if (bindStumps != null) {
				bindStumps.invoke(body, false);
			}
		} catch (Throwable e) {
			// (a stump left to its own cautery)
		}
	}

	private static void floor(Mob mob) {
		look();
		try {
			if (floor != null) {
				floor.invoke(mob, (Vec3)null);
			}
		} catch (Throwable e) {
			// (it just sleeps standing)
		}
	}

	/** Players who are numbed can't be knocked limp by a fall or a blow (Kitchen's ragdoll veto), when Kitchen is loaded. */
	@SuppressWarnings("unchecked")
	private static void addRagdollVeto() {
		if (vetoAdded || !FabricLoader.getInstance().isModLoaded(KitchenCompat.MOD)) {
			return;
		}
		vetoAdded = true;
		try {
			Predicate<Player> numbed = LeechKit::numbed;
			Class.forName("com.jaymoskitchen.ragdoll.LivingRagdolls").getMethod("addVeto", Predicate.class).invoke(null, numbed);
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Kitchen's ragdoll veto for the numbing thorn: {}", e.toString());
		}
	}
}
