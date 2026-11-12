package com.jaymospowers.core.blood;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.41) Andy liked Hematic's blood on creatures, armour and weapons (ideas only: its licence allows no ports or reuse,
 * and it is Forge/NeoForge only). Whoever bleeds is splashed with their own blood ({@link Coat}, synced to everyone and
 * drawn over the body by the client's {@code BloodCoatLayer}), and so is the armour they wear; the weapon that drew it
 * (a blade, or a heavy blunt one) and whoever swung it up close get some too ({@link Blood} on the item, painted into the
 * weapon in the hand and its icon, and into armour the way grime is). Blood dries darker as it ages.
 * <p>
 * Washing it off: water washes whatever is in it (swimming under washes everything, quickly; wading only the boots and
 * greaves), rain slowly, and the hand washes of {@code ArmourGrime} (a cauldron scrub, a wet sponge, dunking the item)
 * take blood off as they take grime. A body's own coat also wears off by itself over a few minutes; the blood on gear
 * stays until washed.
 * <p>
 * (0.20.55) Andy: "the corpses should stay bloodied, if they were upon death the blood stains should just stay drawn and
 * dry like normal". A body that dies bloodied hands its coat to its corpse (Jaymo's Kitchen's, told through its
 * {@code Corpses.ON_BODY} hook, by name): {@link #CORPSE_BLOOD}, kept with the corpse, never wearing off by itself,
 * darkening as it dries like blood on gear. Andy: "rain should wash away blood": rain and water wash a corpse as they do
 * a living body ({@link #weatherCorpse}).
 * <p>
 * Andy, on Cruor's splash-back: blood "does not go away until thoroughly washed especially if allowed to dry at all.
 * rotten blood lasts the longest, stinks the worst, and is hardest to get rid of. EVERYONE will comment on it". So a
 * player's coat is saved and never wears off by itself, and all blood ages ({@link #stage}): fresh, then dried after
 * {@link #DRIES_IN}, then rotten after {@link #ROTS_IN}. Dried blood washes off at {@link #DRIED_WASH} of the rate and
 * rotten at {@link #ROTTEN_WASH}, and water and rain never take the last {@link #ROT_RESIDUE} of rotten blood: only a
 * scrub (a cauldron, a wet sponge) does. Old blood smells ({@link #reek}, added to {@code Smell}), rotten blood draws
 * flies (client {@code Flies}), and Jaymo's Kingdoms' villagers remark on blood they can see ({@link #noticed}). Heavy
 * hits throw more back on whoever struck and onto anyone standing close ({@link #HEAVY}, {@link #BYSTANDER_RANGE}).
 */
public final class Bloodied {
	/** How bloody a body or item can get (whole steps, so the data only changes now and then). */
	public static final int MAX = 100;
	/** A body's coat: this much per point of damage it bleeds for; an attacker up close gets this share of it. */
	static final float COAT_PER_DAMAGE = 7.0F, ATTACKER_SHARE = 0.35F;
	/** The weapon: this much per hit, plus this much per point of damage. */
	static final int WEAPON_PER_HIT = 10;
	static final float WEAPON_PER_DAMAGE = 2.5F;
	/** The bleeder's own armour: this much per point of damage, each piece taking its share (chest most). */
	static final float ARMOUR_PER_DAMAGE = 3.0F;
	static final float[] ARMOUR_REACH = {0.4F, 0.7F, 1.0F, 0.45F};
	/**
	 * (0.21.28) Dragged through a pool: how much blood (coat and armour points) each unit of stain soaked up gives, and its
	 * share on boots, greaves, chest and helmet (lying on the ground: the chest and legs most).
	 */
	static final float DRAG_PER_SOAK = 90.0F;
	static final float[] DRAG_REACH = {0.6F, 1.0F, 1.0F, 0.35F};
	/** An attacker this close (blocks) is splashed. */
	static final double SPLASH_RANGE = 3.2;
	/** How often bodies are checked (ticks) and how far from a player (blocks). */
	static final int CHECK_TICKS = 40;
	static final double RANGE = 48.0;
	/** Washed off per check: under water, wading (boots and greaves), rain, and a coat wearing off by itself. */
	static final int UNDER_WATER_WASH = 40, WADING_WASH = 20, RAIN_WASH = 4, COAT_WEAR = 1;
	/** Blood is rotten this long after it was laid (a day). */
	public static final int ROTS_IN = 24000;
	/** Ageing stages ({@link #stage}). */
	public static final int FRESH = 0, DRIED = 1, ROTTEN = 2;
	/** Washing dried and rotten blood: this share of the usual rate. Water and rain leave this much rotten blood on. */
	static final float DRIED_WASH = 0.4F, ROTTEN_WASH = 0.15F;
	static final int ROT_RESIDUE = 15;
	/** A hit this hard (damage) throws more back, up to {@link #HEAVY_MAX} times as much, and splashes bystanders. */
	static final float HEAVY = 6.0F, HEAVY_MAX = 2.0F;
	/** Bystanders this close to whoever bled (blocks) get this share of what the attacker does, less with distance. */
	static final double BYSTANDER_RANGE = 2.2;
	static final float BYSTANDER_SHARE = 0.5F;
	/** How much each stage smells, per unit of blood (0 to 1); the worst blood on a body counts. */
	static final float[] REEK = {0.08F, 0.25F, 1.0F};
	/** Blood others notice (Kingdoms' remarks): at least this much of it (0 to 1). */
	public static final float NOTICE = 0.15F;
	/** The armour slots, feet to head. */
	static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

	/**
	 * Blood on an item: how much ({@code 0..MAX}), its colour (RGB) and the game time it was last bloodied (it darkens as it
	 * dries, see {@link #dryness}).
	 */
	public record Blood(int amount, int color, long laid) {
		public static final Codec<Blood> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("amount").forGetter(Blood::amount),
			Codec.INT.optionalFieldOf("color", 0x80000D).forGetter(Blood::color),
			Codec.LONG.optionalFieldOf("laid", 0L).forGetter(Blood::laid)).apply(i, Blood::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, Blood> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Blood::amount,
			ByteBufCodecs.INT, Blood::color, ByteBufCodecs.VAR_LONG, Blood::laid, Blood::new);

		/** 0 (clean) to 1 (soaked). */
		public float level() {
			return Mth.clamp(this.amount / (float) MAX, 0.0F, 1.0F);
		}
	}

	/** Blood on a body itself: how much ({@code 0..MAX}), whose colour, and when it was laid (on average). */
	public record Coat(int amount, int color, long laid) {
		public static final Codec<Coat> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("amount").forGetter(Coat::amount),
			Codec.INT.fieldOf("color").forGetter(Coat::color),
			Codec.LONG.optionalFieldOf("laid", 0L).forGetter(Coat::laid)).apply(i, Coat::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, Coat> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Coat::amount,
			ByteBufCodecs.INT, Coat::color, ByteBufCodecs.VAR_LONG, Coat::laid, Coat::new);

		public float level() {
			return Mth.clamp(this.amount / (float) MAX, 0.0F, 1.0F);
		}
	}

	/** Fresh blood is fully dark (dried) after this many ticks. */
	public static final int DRIES_IN = 6000;

	public static @Nullable DataComponentType<Blood> BLOOD;
	/** On every body, synced to everyone near it, and saved: it stays until washed. */
	public static final AttachmentType<Coat> COAT = AttachmentRegistry.create(JaymosPowers.id("blood_coat"),
		builder -> builder.persistent(Coat.CODEC).syncWith(Coat.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** The server's game time at its last tick, for washes that aren't told it (item scrubs). */
	private static long now;

	/**
	 * (0.20.55) The blood on a corpse: its coat when it died, laid then, so it dries from that moment. Saved with the corpse
	 * and synced to everyone near it.
	 */
	public static final AttachmentType<Blood> CORPSE_BLOOD = AttachmentRegistry.create(JaymosPowers.id("corpse_blood"),
		builder -> builder.persistent(Blood.CODEC).syncWith(Blood.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private Bloodied() {
	}

	/** 0 (fresh) to 1 (rotten) for blood laid at {@code laid}: how far it has gone from dried to rotten. */
	public static float rot(long laid, long now) {
		return Mth.clamp((now - laid - DRIES_IN) / (float) (ROTS_IN - DRIES_IN), 0.0F, 1.0F);
	}

	/** {@link #FRESH}, {@link #DRIED} or {@link #ROTTEN}, for blood laid at {@code laid}. */
	public static int stage(long laid, long now) {
		long age = now - laid;
		return age >= ROTS_IN ? ROTTEN : age >= DRIES_IN ? DRIED : FRESH;
	}

	/** How much of a wash of {@code amount} gets blood laid at {@code laid} off (at least 1). */
	static int washes(int amount, long laid, long now) {
		return switch (stage(laid, now)) {
			case FRESH -> amount;
			case DRIED -> Math.max(1, Math.round(amount * DRIED_WASH));
			default -> Math.max(1, Math.round(amount * ROTTEN_WASH));
		};
	}

	/** How much water or rain can take off {@code amount} of blood laid at {@code laid}: rotten blood keeps a residue. */
	static int soaks(int wash, int amount, long laid, long now) {
		int off = washes(wash, laid, now);
		return stage(laid, now) == ROTTEN ? Math.max(0, Math.min(off, amount - ROT_RESIDUE)) : off;
	}

	/**
	 * How bad the blood on {@code body} smells, 0 to 1: the worst of its coat and what it wears and holds, by how much
	 * there is and how old it is (rotten blood the worst by far, see {@link #REEK}).
	 */
	public static float reek(LivingEntity body) {
		long t = body.level().getGameTime();
		Coat coat = coat(body);
		float worst = coat == null ? 0.0F : coat.level() * reekOf(coat.laid(), t);
		worst = Math.max(worst, reek(blood(body.getMainHandItem()), t));
		worst = Math.max(worst, reek(blood(body.getOffhandItem()), t));
		for (EquipmentSlot slot : SLOTS) {
			worst = Math.max(worst, reek(blood(body.getItemBySlot(slot)), t));
		}
		return worst;
	}

	private static float reek(@Nullable Blood blood, long t) {
		return blood == null ? 0.0F : blood.level() * reekOf(blood.laid(), t);
	}

	private static float reekOf(long laid, long t) {
		// fresh to dried, then dried to rotten
		return t - laid < DRIES_IN ? Mth.lerp(dryness(laid, t), REEK[FRESH], REEK[DRIED]) : Mth.lerp(rot(laid, t), REEK[DRIED], REEK[ROTTEN]);
	}

	/**
	 * The blood others would notice on {@code body} (Jaymo's Kingdoms' remarks): -1 for none worth a word, else the oldest
	 * stage ({@link #FRESH}, {@link #DRIED}, {@link #ROTTEN}) among its coat and the armour and weapons on it that are at
	 * least {@link #NOTICE} bloody.
	 */
	public static int noticed(LivingEntity body) {
		long t = body.level().getGameTime();
		int worst = -1;
		Coat coat = coat(body);
		if (coat != null && coat.level() >= NOTICE) {
			worst = stage(coat.laid(), t);
		}
		worst = Math.max(worst, noticed(blood(body.getMainHandItem()), t));
		worst = Math.max(worst, noticed(blood(body.getOffhandItem()), t));
		for (EquipmentSlot slot : SLOTS) {
			worst = Math.max(worst, noticed(blood(body.getItemBySlot(slot)), t));
		}
		return worst;
	}

	private static int noticed(@Nullable Blood blood, long t) {
		return blood == null || blood.level() < NOTICE ? -1 : stage(blood.laid(), t);
	}

	public static void init() {
		BLOOD = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("blood"),
			DataComponentType.<Blood>builder().persistent(Blood.CODEC).networkSynchronized(Blood.STREAM_CODEC).build());
		ServerTickEvents.END_SERVER_TICK.register(Bloodied::tick);
		hookCorpses();
	}

	/** (0.20.55) Kitchen tells us of each new body; an older Kitchen (no hook) just leaves bodies clean. */
	@SuppressWarnings("unchecked")
	private static void hookCorpses() {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			return;
		}
		try {
			((java.util.List<java.util.function.BiConsumer<LivingEntity, net.minecraft.world.entity.Entity>>) Class
				.forName("com.jaymoskitchen.corpse.Corpses").getField("ON_BODY").get(null)).add(Bloodied::toCorpse);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			JaymosPowers.LOGGER.warn("Jaymo's Kitchen has no body hook (older Kitchen?): corpses won't keep their blood", e);
		}
	}

	/** (0.20.55) {@code dead}'s blood stays on its new body {@code corpse}, drying from now. */
	private static void toCorpse(LivingEntity dead, net.minecraft.world.entity.Entity corpse) {
		Coat coat = coat(dead);
		if (coat != null && coat.amount() > 0) {
			// it keeps drying from when it was laid on the living body
			long laid = coat.laid() > 0 ? coat.laid() : dead.level().getGameTime();
			corpse.setAttached(CORPSE_BLOOD, new Blood(coat.amount(), coat.color(), laid));
		}
	}

	/** (0.20.55) The blood on a corpse, or null (either side). */
	public static @Nullable Blood corpseBlood(net.minecraft.world.entity.Entity corpse) {
		return corpse.getAttached(CORPSE_BLOOD);
	}

	/** The blood on {@code stack}, or null if it's clean. */
	public static @Nullable Blood blood(ItemStack stack) {
		return BLOOD == null || stack.isEmpty() ? null : stack.get(BLOOD);
	}

	/** The blood on {@code body} itself, or null. */
	public static @Nullable Coat coat(LivingEntity body) {
		return body.getAttached(COAT);
	}

	/** 0 (fresh) to 1 (dried dark) for blood laid at {@code laid}, at game time {@code now}. */
	public static float dryness(long laid, long now) {
		return Mth.clamp((now - laid) / (float) DRIES_IN, 0.0F, 1.0F);
	}

	/**
	 * {@code target} just bled ({@code BladeBlood} sprayed it) from {@code source} (null: a power's own blade) for
	 * {@code damage}, in {@code color}: its own coat and armour take some, and so do the weapon that did it and whoever
	 * swung it, if they were close.
	 */
	public static void struck(LivingEntity target, @Nullable DamageSource source, float damage, int color) {
		if (!(target.level() instanceof ServerLevel level) || damage <= 0.0F) {
			return;
		}
		long now = level.getGameTime();
		addCoat(target, Math.round(damage * COAT_PER_DAMAGE), color);
		for (int i = 0; i < SLOTS.length; i++) {
			addBlood(target.getItemBySlot(SLOTS[i]), damage * ARMOUR_PER_DAMAGE * ARMOUR_REACH[i], color, now, target);
		}
		// a heavy hit throws more blood back, and onto whoever stands close
		float heavy = damage >= HEAVY ? Math.min(HEAVY_MAX, 1.0F + (damage - HEAVY) / HEAVY) : 1.0F;
		LivingEntity striker = source != null && source.getEntity() instanceof LivingEntity e ? e : null;
		if (damage >= HEAVY) {
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(BYSTANDER_RANGE),
				near -> near != target && near != striker && near.isAlive() && !near.isSpectator())) {
				float reach = 1.0F - (float) (Math.sqrt(near.distanceToSqr(target)) / (BYSTANDER_RANGE + 1.0));
				if (reach > 0.0F) {
					splash(near, damage * COAT_PER_DAMAGE * ATTACKER_SHARE * heavy * BYSTANDER_SHARE * reach, color, now);
				}
			}
		}
		if (striker == null || striker == target) {
			return;
		}
		LivingEntity attacker = striker;
		ItemStack weapon = source.getWeaponItem();
		if (weapon != null && !weapon.isEmpty() && source.getDirectEntity() == attacker) {
			addBlood(weapon, WEAPON_PER_HIT + damage * WEAPON_PER_DAMAGE, color, now, attacker);
		}
		if (attacker.distanceToSqr(target) <= SPLASH_RANGE * SPLASH_RANGE && source.getDirectEntity() == attacker) {
			splash(attacker, damage * COAT_PER_DAMAGE * ATTACKER_SHARE * heavy, color, now);
		}
	}

	/** {@code body} is splashed with {@code splash} of blood from the front: chest and arms most, the helmet a little. */
	private static void splash(LivingEntity body, float splash, int color, long now) {
		addCoat(body, Math.round(splash * 0.5F), color);
		addBlood(body.getItemBySlot(EquipmentSlot.CHEST), splash * 0.5F, color, now, body);
		addBlood(body.getItemBySlot(EquipmentSlot.HEAD), splash * 0.2F, color, now, body);
		addBlood(body.getItemBySlot(EquipmentSlot.LEGS), splash * 0.25F, color, now, body);
	}

	/**
	 * (0.21.28) Andy: "dragging should bloody dirty and clean the armor they wear respectively". {@code body} was dragged
	 * (or crawled) through a pool and soaked up {@code soaked} of it (stain size): its own coat and everything it wears
	 * take it, the chest and legs most (they're what's on the ground), the helmet least. A corpse's blood
	 * ({@link #CORPSE_BLOOD}) takes it too, freshly laid.
	 */
	public static void dragged(net.minecraft.world.entity.Entity body, float soaked, int color) {
		if (soaked <= 0.0F || !(body.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		float gain = soaked * DRAG_PER_SOAK;
		if (body instanceof LivingEntity living) {
			addCoat(living, Math.round(gain), color);
			for (int i = 0; i < SLOTS.length; i++) {
				addBlood(living.getItemBySlot(SLOTS[i]), gain * DRAG_REACH[i], color, now, living);
			}
			return;
		}
		Blood was = body.getAttached(CORPSE_BLOOD);
		int amount = Math.min(MAX, (was == null ? 0 : was.amount()) + Math.max(1, Math.round(gain)));
		body.setAttached(CORPSE_BLOOD, new Blood(amount, color, now));
	}

	/**
	 * (0.21.28) A corpse's armour (Jaymo's Kitchen keeps it on the body), piece {@code index} (0 boots ... 3 helmet), dragged
	 * through pools that gave up {@code soaked} (stain size): bloodied as a living body's would be.
	 */
	public static void dragArmour(ItemStack stack, int index, float soaked, int color, long now, net.minecraft.util.RandomSource random) {
		float gain = soaked * DRAG_PER_SOAK * DRAG_REACH[Mth.clamp(index, 0, DRAG_REACH.length - 1)];
		if (BLOOD == null || stack.isEmpty() || gain <= 0.0F) {
			return;
		}
		int add = Mth.floor(gain) + (random.nextFloat() < gain - Mth.floor(gain) ? 1 : 0);
		if (add > 0) {
			Blood was = blood(stack);
			stack.set(BLOOD, new Blood(Math.min(MAX, (was == null ? 0 : was.amount()) + add), color, now));
		}
	}

	/** (0.21.28) A corpse dragged through water: its blood washed off by {@code amount}. */
	public static void washCorpse(net.minecraft.world.entity.Entity corpse, int amount) {
		Blood was = corpse.getAttached(CORPSE_BLOOD);
		if (was == null) {
			return;
		}
		int left = was.amount() - amount;
		if (left <= 0) {
			corpse.removeAttached(CORPSE_BLOOD);
		} else {
			corpse.setAttached(CORPSE_BLOOD, new Blood(left, was.color(), was.laid()));
		}
	}

	private static void addCoat(LivingEntity body, int add, int color) {
		if (add <= 0) {
			return;
		}
		Coat was = coat(body);
		long t = body.level().getGameTime();
		int had = was == null ? 0 : was.amount();
		int amount = Math.min(MAX, had + add);
		// the fresh blood's share of the whole sets how old the coat now is
		long laid = was == null || had <= 0 ? t : was.laid() + Math.round((t - was.laid()) * (add / (double) (had + add)));
		if (was == null || was.amount() != amount || was.color() != color || was.laid() != laid) {
			body.setAttached(COAT, new Coat(amount, color, laid));
		}
	}

	/** Bloodies {@code stack} by {@code gain} (fractions carry over by chance), in {@code color}, now. */
	static void addBlood(ItemStack stack, float gain, int color, long now, LivingEntity holder) {
		if (BLOOD == null || stack.isEmpty() || gain <= 0.0F) {
			return;
		}
		int add = Mth.floor(gain) + (holder.getRandom().nextFloat() < gain - Mth.floor(gain) ? 1 : 0);
		if (add <= 0) {
			return;
		}
		Blood was = blood(stack);
		int amount = Math.min(MAX, (was == null ? 0 : was.amount()) + add);
		stack.set(BLOOD, new Blood(amount, color, now));
	}

	/** Washes {@code amount} of blood off {@code stack} (less if it has dried or rotted); true if it had any. */
	public static boolean clean(ItemStack stack, int amount) {
		Blood was = blood(stack);
		if (was == null) {
			return false;
		}
		int left = was.amount() - washes(amount, was.laid(), now);
		if (left <= 0) {
			stack.remove(BLOOD);
		} else {
			stack.set(BLOOD, new Blood(left, was.color(), was.laid()));
		}
		return true;
	}

	/** Scrubs {@code amount} off {@code body}'s own coat (less if it has dried or rotted); true if it had any. */
	public static boolean cleanCoat(LivingEntity body, int amount) {
		Coat was = coat(body);
		if (was == null) {
			return false;
		}
		setCoat(body, was, was.amount() - washes(amount, was.laid(), body.level().getGameTime()));
		return true;
	}

	/** Water or rain on {@code body}'s coat: as a scrub, but rotten blood keeps its last {@link #ROT_RESIDUE}. */
	private static void soakCoat(LivingEntity body, int amount) {
		Coat was = coat(body);
		if (was != null) {
			setCoat(body, was, was.amount() - soaks(amount, was.amount(), was.laid(), body.level().getGameTime()));
		}
	}

	private static void setCoat(LivingEntity body, Coat was, int left) {
		if (left <= 0) {
			body.removeAttached(COAT);
		} else if (left != was.amount()) {
			body.setAttached(COAT, new Coat(left, was.color(), was.laid()));
		}
	}

	/** Water or rain on {@code stack}: as {@link #clean}, but rotten blood keeps its last {@link #ROT_RESIDUE}. */
	private static void soak(ItemStack stack, int amount) {
		Blood was = blood(stack);
		if (was == null) {
			return;
		}
		int left = was.amount() - soaks(amount, was.amount(), was.laid(), now);
		if (left <= 0) {
			stack.remove(BLOOD);
		} else if (left != was.amount()) {
			stack.set(BLOOD, new Blood(left, was.color(), was.laid()));
		}
	}

	/** Whether {@code body} or anything it wears or holds has blood on it. */
	public static boolean anyBlood(LivingEntity body) {
		if (coat(body) != null || blood(body.getMainHandItem()) != null || blood(body.getOffhandItem()) != null) {
			return true;
		}
		for (EquipmentSlot slot : SLOTS) {
			if (blood(body.getItemBySlot(slot)) != null) {
				return true;
			}
		}
		return false;
	}

	// ---- washing ---------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		now = server.overworld().getGameTime();
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Set<LivingEntity> seen = new HashSet<>();
			Set<net.minecraft.world.entity.Entity> seenCorpses = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(RANGE),
					body -> body.isAlive() && anyBlood(body))) {
					if (seen.add(body)) {
						weather(level, body);
					}
				}
				for (net.minecraft.world.entity.Entity corpse : level.getEntities((net.minecraft.world.entity.Entity) null,
					new AABB(player.blockPosition()).inflate(RANGE), e -> e.hasAttached(CORPSE_BLOOD))) {
					if (seenCorpses.add(corpse)) {
						weatherCorpse(corpse);
					}
				}
			}
		}
	}

	/** (0.20.55) A corpse's blood washes off as a living body's coat does in water and rain (it never wears off dry). */
	private static void weatherCorpse(net.minecraft.world.entity.Entity corpse) {
		int wash = corpse.isUnderWater() ? UNDER_WATER_WASH : corpse.isInWater() ? WADING_WASH / 2 : corpse.isInWaterOrRain() ? RAIN_WASH : 0;
		Blood was = corpseBlood(corpse);
		if (wash <= 0 || was == null) {
			return;
		}
		int left = was.amount() - wash;
		if (left <= 0) {
			corpse.removeAttached(CORPSE_BLOOD);
		} else {
			corpse.setAttached(CORPSE_BLOOD, new Blood(left, was.color(), was.laid()));
		}
	}

	private static void weather(ServerLevel level, LivingEntity body) {
		if (body.isUnderWater()) {
			soakCoat(body, UNDER_WATER_WASH);
			soak(body.getMainHandItem(), UNDER_WATER_WASH);
			soak(body.getOffhandItem(), UNDER_WATER_WASH);
			for (EquipmentSlot slot : SLOTS) {
				soak(body.getItemBySlot(slot), UNDER_WATER_WASH);
			}
			return;
		}
		if (body.isInWater()) {
			soakCoat(body, WADING_WASH / 2);
			soak(body.getItemBySlot(EquipmentSlot.FEET), WADING_WASH);
			soak(body.getItemBySlot(EquipmentSlot.LEGS), WADING_WASH);
			return;
		}
		if (body.isInWaterOrRain()) {
			soakCoat(body, RAIN_WASH);
			soak(body.getMainHandItem(), RAIN_WASH);
			soak(body.getOffhandItem(), RAIN_WASH);
			for (EquipmentSlot slot : SLOTS) {
				soak(body.getItemBySlot(slot), RAIN_WASH);
			}
			return;
		}
		// a beast's coat wears off by itself (licked, rubbed off); a player's stays until washed
		if (!(body instanceof net.minecraft.world.entity.player.Player)) {
			setCoatWorn(body);
		}
	}

	private static void setCoatWorn(LivingEntity body) {
		Coat was = coat(body);
		if (was != null) {
			setCoat(body, was, was.amount() - COAT_WEAR);
		}
	}
}
