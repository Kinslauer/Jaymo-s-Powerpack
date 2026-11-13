package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Poisons and the poisoned state (fifth edition's poisoned and the four kinds of poison, Andy's pick B7). Being poisoned
 * shakes the hands: bad footing on blows ({@link UpperHand}), on saves ({@link Saves#chance}), and a tremor on the poisoned
 * player's own aim (client).
 * <p>
 * <b>Five poisons</b> ({@link Kind}): a plain poison (vanilla's poison for a while), a <b>sleep draught</b> (out cold
 * ({@link HeldFast}), woken by a hurt), a <b>stiffening venom</b> (held fast), a <b>truth draught</b> (a flag other
 * systems read: {@link #truthful}, also the scoreboard tag {@link #TRUTH_TAG}), and <b>midnight poison</b> (a slow one:
 * its victim dies at the next midnight, unless an antidote reaches them first). Each is a vial. Poisons rolls a poison
 * save ({@link Saves.Kind#POISON}, Endurance) as they go in.
 * <p>
 * <b>Four ways to use one</b>, with the vial in the used hand and the thing to poison in the other:
 * <ul>
 * <li><b>On a blade</b>: a weapon in the other hand is coated; its next {@link #COAT_HITS} blows carry the poison.</li>
 * <li><b>On a surface</b>: use the vial on a door; the next to open it (not the one who smeared it, nor with a poison-proof
 * hand: no one) is dosed. {@link #touchedDoor}.</li>
 * <li><b>In a dish or a drink</b>: food or a drink in the other hand takes one dose; whoever eats or drinks it is dosed.
 * A dosed one no longer stacks with the clean ones. {@link #eaten}.</li>
 * <li><b>In the air</b>: a glass bottle in the other hand is filled with the gas; thrown, it breaks into a cloud
 * {@link #CLOUD_RADIUS} blocks wide that doses everyone breathing in it ({@link GasBottle}).</li>
 * </ul>
 * The antidote ({@code antidote}, sold by clerics and healers) cures every one of them and vanilla poison with it.
 */
public final class Poisons {
	/** Blows a coat carries; how many vials' worth a dish takes (one). */
	static final int COAT_HITS = 3;
	/** The plain poison's time (ticks), the sleep draught's, the stiffening venom's. */
	static final int PLAIN_TICKS = 20 * 12, SLEEP_TICKS = 20 * 20, STIFF_TICKS = 20 * 8;
	/** The truth draught lasts this long (ticks). */
	static final int TRUTH_TICKS = 20 * 180;
	/** Midnight in the day's clock (ticks), and a day's length. */
	static final long MIDNIGHT = 18000L, DAY = 24000L;
	/** A midnight-poisoned body is sick now and then (ticks between), for this long (ticks). */
	static final int SICK_EVERY = 20 * 90, SICK_TICKS = 20 * 6;
	/** A gas cloud's radius (blocks) and life (ticks); the same body is dosed again after this long (ticks). */
	static final double CLOUD_RADIUS = 3.0;
	static final int CLOUD_TICKS = 20 * 12, CLOUD_DOSE_EVERY = 20 * 5;
	/** The scoreboard tag on whoever has taken the truth draught (read by other systems, including other mods). */
	public static final String TRUTH_TAG = "jaymoskingdoms_truthful";

	/** The poisons. {@code color} is the colour of its gas and vial. */
	public enum Kind {
		PLAIN("poison_vial", 0x6FA03A), SLEEP("sleep_draught", 0x8A7AC8), STIFFENING("stiffening_venom", 0xB8A860),
		TRUTH("truth_draught", 0x9ADCE8), MIDNIGHT("midnight_poison", 0x3A2A52);

		public final String item;
		public final int color;

		Kind(String item, int color) {
			this.item = item;
			this.color = color;
		}

		String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Kind of(@Nullable String key) {
			if (key != null) {
				for (Kind k : values()) {
					if (k.key().equals(key)) {
						return k;
					}
				}
			}
			return null;
		}
	}

	/** On a coated blade, a dosed dish or drink, or a gas bottle: which poison. */
	public static DataComponentType<String> KIND;
	/** On a coated blade: blows left. */
	public static DataComponentType<Integer> COAT;

	public static final Map<Kind, Item> VIALS = new java.util.EnumMap<>(Kind.class);
	public static Item ANTIDOTE, GAS_BOTTLE;

	/** Until when (the overworld clock) a midnight-poisoned body lives: it dies at this time. Saved with the body. */
	private static final AttachmentType<Long> MIDNIGHT_AT = AttachmentRegistry.create(JaymosKingdoms.id("midnight_poison"), b -> b.persistent(Codec.LONG));
	/** Until when (game time) a body tells the truth. Saved. */
	private static final AttachmentType<Long> TRUTH_UNTIL = AttachmentRegistry.create(JaymosKingdoms.id("truth_draught"), b -> b.persistent(Codec.LONG));
	/** Door handles smeared with poison in a world: the door's lower half (packed position) to the poison and who smeared it. Saved. */
	private static final AttachmentType<Map<String, String>> SMEARS = AttachmentRegistry.create(JaymosKingdoms.id("smeared_doors"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));

	/** Bodies carrying midnight poison that are loaded. */
	private static final Map<LivingEntity, Boolean> MIDNIGHTERS = new WeakHashMap<>();
	/** When each last coughed (game time), and when each was last dosed by a gas (game time). */
	private static final Map<LivingEntity, Long> COUGHED = new WeakHashMap<>(), GASSED = new WeakHashMap<>();

	private record Cloud(ServerLevel level, Vec3 at, Kind kind, long until) {
	}

	private static final List<Cloud> CLOUDS = new ArrayList<>();

	private Poisons() {
	}

	static void register() {
		KIND = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("poison_kind"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		COAT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("poison_coat"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		for (Kind kind : Kind.values()) {
			VIALS.put(kind, item(kind.item, key -> new PoisonItems.Vial(new Item.Properties().setId(key).stacksTo(8), kind)));
		}
		ANTIDOTE = item("antidote", key -> new PoisonItems.Antidote(new Item.Properties().setId(key).stacksTo(8)));
		GAS_BOTTLE = item("gas_bottle", key -> new PoisonItems.GasBottleItem(new Item.Properties().setId(key).stacksTo(8)));
		GasBottle.register();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			for (Kind kind : Kind.values()) {
				output.accept(VIALS.get(kind));
			}
			output.accept(ANTIDOTE);
		});
		ServerTickEvents.END_SERVER_TICK.register(Poisons::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MIDNIGHTERS.clear();
			COUGHED.clear();
			GASSED.clear();
			CLOUDS.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof LivingEntity l && l.hasAttached(MIDNIGHT_AT)) {
				MIDNIGHTERS.put(l, Boolean.TRUE);
			}
		});
		// a smeared door handle, touched by hand
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!world.isClientSide() && hand == InteractionHand.MAIN_HAND && world instanceof ServerLevel level) {
				touchedDoor(level, hit.getBlockPos(), player);
			}
			return InteractionResult.PASS;
		});
	}

	private static Item item(String name, java.util.function.Function<ResourceKey<Item>, Item> make) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(key));
	}

	// ---- being dosed -------------------------------------------------------------------------------------------------

	/** {@code victim} takes a dose of {@code kind}, after a poison save (Endurance) that may turn it. */
	public static void dose(LivingEntity victim, Kind kind) {
		if (!(victim.level() instanceof ServerLevel level) || !victim.isAlive() || victim instanceof Mob m && Senses.boss(m)
			|| victim instanceof Player p && (p.isSpectator() || p.isCreative())) {
			return;
		}
		if (HeroesFeast.fed(victim)) {
			return;
		}
		boolean turned = Saves.resist(victim, Saves.Kind.POISON);
		switch (kind) {
			case PLAIN -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, turned ? PLAIN_TICKS / 3 : PLAIN_TICKS, 0));
			case SLEEP -> {
				if (turned) {
					victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 4, 1));
				} else {
					HeldFast.sleep(victim, SLEEP_TICKS);
				}
			}
			case STIFFENING -> {
				if (!turned) {
					HeldFast.afflict(victim, STIFF_TICKS);
				}
			}
			case TRUTH -> {
				if (!turned) {
					victim.setAttached(TRUTH_UNTIL, level.getGameTime() + TRUTH_TICKS);
					victim.addTag(TRUTH_TAG);
				}
			}
			case MIDNIGHT -> {
				if (turned) {
					victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SICK_TICKS, 0));
				} else if (!victim.hasAttached(MIDNIGHT_AT)) {
					long clock = level.getOverworldClockTime();
					long mid = Math.floorDiv(clock, DAY) * DAY + MIDNIGHT;
					victim.setAttached(MIDNIGHT_AT, clock >= mid ? mid + DAY : mid);
					MIDNIGHTERS.put(victim, Boolean.TRUE);
				}
			}
		}
		level.playSound(null, victim.getX(), victim.getEyeY(), victim.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.3F, 1.5F);
	}

	/** Whether {@code who} has taken a truth draught that hasn't worn off: other systems (an interrogation, a talk) may read this. */
	public static boolean truthful(LivingEntity who) {
		Long until = who.getAttached(TRUTH_UNTIL);
		if (until == null) {
			return false;
		}
		if (who.level().getGameTime() >= until) {
			who.removeAttached(TRUTH_UNTIL);
			who.removeTag(TRUTH_TAG);
			return false;
		}
		return true;
	}

	/** Whether {@code who} carries midnight poison. */
	public static boolean doomed(LivingEntity who) {
		return who.hasAttached(MIDNIGHT_AT);
	}

	/** The antidote: cures {@code who} of every poison, vanilla's too. */
	public static void cure(LivingEntity who) {
		who.removeEffect(MobEffects.POISON);
		who.removeEffect(MobEffects.NAUSEA);
		who.removeEffect(MobEffects.HUNGER);
		who.removeEffect(MobEffects.WITHER);
		HeldFast.release(who);
		who.removeAttached(MIDNIGHT_AT);
		MIDNIGHTERS.remove(who);
		who.removeAttached(TRUTH_UNTIL);
		who.removeTag(TRUTH_TAG);
		if (who.level() instanceof ServerLevel level) {
			Fx.ring(level, new Vec3(who.getX(), who.getY(0.5), who.getZ()), 0.8F, 0xE8F0B0);
		}
	}

	// ---- the four ways -----------------------------------------------------------------------------------------------

	/** A blow with a coated blade carries the poison, for {@link #COAT_HITS} blows. Melee only. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || Senses.cooling(target, source)) {
			return amount;
		}
		ItemStack weapon = attacker.getMainHandItem();
		Integer hits = weapon.get(COAT);
		Kind kind = Kind.of(weapon.get(KIND));
		if (hits == null || kind == null) {
			return amount;
		}
		if (hits <= 1) {
			weapon.remove(COAT);
			weapon.remove(KIND);
		} else {
			weapon.set(COAT, hits - 1);
		}
		dose(target, kind);
		return amount;
	}

	/** Someone eats or drinks {@code stack}: a dosed one doses them. */
	public static void eaten(LivingEntity who, ItemStack stack) {
		Kind kind = Kind.of(stack.get(KIND));
		if (kind != null && stack.get(COAT) == null && !who.level().isClientSide()) {
			dose(who, kind);
		}
	}

	/** Smears the handle of the door at {@code pos} with {@code kind}, for the next hand but {@code by}'s that opens it. */
	static boolean smear(ServerLevel level, BlockPos pos, Kind kind, Player by) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof DoorBlock)) {
			return false;
		}
		BlockPos low = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
		Map<String, String> all = new HashMap<>(level.getAttachedOrElse(SMEARS, Map.of()));
		all.put(Long.toString(low.asLong()), kind.key() + ";" + by.getUUID());
		level.setAttached(SMEARS, all);
		return true;
	}

	/** {@code who} touches the door at {@code pos}: if its handle is smeared (by someone else), they are dosed and it is wiped. */
	public static void touchedDoor(ServerLevel level, BlockPos pos, Entity who) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof DoorBlock door) || !door.type().canOpenByHand() || !(who instanceof LivingEntity toucher)) {
			return;
		}
		Map<String, String> all = level.getAttached(SMEARS);
		if (all == null || all.isEmpty()) {
			return;
		}
		BlockPos low = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
		String key = Long.toString(low.asLong());
		String smear = all.get(key);
		if (smear == null) {
			return;
		}
		String[] parts = smear.split(";");
		if (parts.length == 2 && parts[1].equals(who.getUUID().toString())) {
			return;
		}
		Map<String, String> rest = new HashMap<>(all);
		rest.remove(key);
		level.setAttached(SMEARS, rest);
		Kind kind = Kind.of(parts[0]);
		if (kind != null) {
			dose(toucher, kind);
		}
	}

	/** A gas bottle breaks at {@code at}: a cloud of {@code kind} hangs there. */
	static void cloud(ServerLevel level, Vec3 at, Kind kind) {
		CLOUDS.add(new Cloud(level, at, kind, level.getGameTime() + CLOUD_TICKS));
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.0F, 0.9F);
	}

	// ---- the passing of time -----------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		if (!CLOUDS.isEmpty() && tick % 10 == 0) {
			for (Cloud c : new ArrayList<>(CLOUDS)) {
				long now = c.level().getGameTime();
				if (now >= c.until()) {
					CLOUDS.remove(c);
					continue;
				}
				c.level().sendParticles(Fx.miasma(c.kind().color, 1.6F), c.at().x, c.at().y + 0.8, c.at().z, 8, CLOUD_RADIUS * 0.4, 0.5, CLOUD_RADIUS * 0.4, 0.01);
				for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c.at(), c.at()).inflate(CLOUD_RADIUS, 1.5, CLOUD_RADIUS),
					e -> e.isAlive() && !e.isUnderWater() && e.position().distanceToSqr(c.at()) <= CLOUD_RADIUS * CLOUD_RADIUS)) {
					Long last = GASSED.get(e);
					if (last == null || now - last >= CLOUD_DOSE_EVERY) {
						GASSED.put(e, now);
						dose(e, c.kind());
					}
				}
			}
		}
		if (tick % 20 != 0 || MIDNIGHTERS.isEmpty()) {
			return;
		}
		for (LivingEntity who : new ArrayList<>(MIDNIGHTERS.keySet())) {
			Long at = who == null ? null : who.getAttached(MIDNIGHT_AT);
			if (at == null || !who.isAlive() || who.isRemoved() || !(who.level() instanceof ServerLevel level)) {
				MIDNIGHTERS.remove(who);
				continue;
			}
			long clock = level.getOverworldClockTime();
			if (clock >= at) {
				who.removeAttached(MIDNIGHT_AT);
				MIDNIGHTERS.remove(who);
				who.hurtServer(level, level.damageSources().magic(), who.getMaxHealth() * 4.0F + 20.0F);
				continue;
			}
			long now = level.getGameTime();
			Long last = COUGHED.get(who);
			if (last == null || now - last >= SICK_EVERY) {
				COUGHED.put(who, now);
				if (last != null) {
					who.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SICK_TICKS, 0));
					level.playSound(null, who.getX(), who.getEyeY(), who.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 0.7F);
				}
			}
		}
	}
}
