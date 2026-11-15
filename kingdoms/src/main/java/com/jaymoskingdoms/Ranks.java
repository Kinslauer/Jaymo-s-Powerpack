package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.jspecify.annotations.Nullable;

/**
 * The troop ladder, one for every realm (medieval picks gb-troops and me-ranks, lore "The realms' ways": "their soldiers
 * come in five ranks: militia, soldier, knight, veteran, leader"). Every soldier stands on it: a realm's own guards and
 * archers and the soldiers of its war parties, and the recruits a player hires ({@link Drifters}, {@link Veterans},
 * MCA's own hire), who climb it as they win fights ({@link Learners#WINS}): {@link #MILITIA}, then {@link #SOLDIER},
 * then {@link #KNIGHT} or, for one who fights with a bow when they get there, archer ({@link #BOW}), then
 * {@link #VETERAN}, and at the top {@link #LEADER}, who heads a squad (first in a formation, {@link Formations}, and at
 * the head of a war party's squad, {@link WarParties}). Each rank is tougher than the one below ({@link #HEALTH},
 * {@link #DAMAGE}) and better kitted.
 * <p>
 * Each realm keeps its own tree: a recruit is raised in the realm where they were taken on ({@link #REALM}) and wears
 * its colours and arms, its levy's weapon (spear or hatchet), its archers' (bow or crossbow), and at knight its land's
 * champions' harness: the realm's knights' plate, helm and war arm in the temperate lands ({@link HeraldryWear#knight},
 * Powers' realm patterns), gladiators in the sands and on the savanna, ronin in the jungle and Blossom, paladins in the
 * north. Iron where the realm has iron ({@link IronVeins}), bronze where it hasn't. A realm's own soldiers have their
 * rank by who they are ({@link #SHARES}, the same shares {@link Outfits} and {@link HeraldryWear} dress by); its
 * militia wear its levy leather ({@link #issue}) and carry its shield, and its leaders its banner.
 */
public final class Ranks {
	public static final int MILITIA = 0, SOLDIER = 1, KNIGHT = 2, VETERAN = 3, LEADER = 4;
	/** Added to a rank for the bow's branch: an archer at {@link #KNIGHT}, a veteran or leader of archers above it. */
	public static final int BOW = 8;
	static final String[] NAMES = {"militia", "soldier", "knight", "veteran", "leader"};
	/** The wins a recruit needs for each rank ({@link Learners#WINS}; a road veteran starts with 10, a fallen knight with 24). */
	static final int[] WINS_TO = {0, 6, 16, 30, 50};
	/** What each rank adds, on top of what a recruit has learned: health, attack damage. */
	static final double[] HEALTH = {0.0, 2.0, 5.0, 8.0, 12.0}, DAMAGE = {0.0, 0.5, 1.0, 1.5, 2.0};
	/** A realm's own soldiers by who they are: below these shares militia, soldier, knight, veteran; the rest lead. */
	static final float[] SHARES = {0.25F, 0.75F, 0.9F, 0.97F};
	/** Its archers: below this share archers, below the next veterans of the bow; the rest lead. */
	static final float[] BOW_SHARES = {0.75F, 0.95F};
	/** How often the soldiers round each player are looked over (ticks), and how far round. */
	static final int SWEEP = 100;
	static final double SWEEP_RANGE = 48.0;
	/** A promotion's few words carry this far; lines per rank ({@code speech.jaymoskingdoms.rank.<rank>.<n>}). */
	static final double SPEECH_RANGE = 12.0;
	static final int LINES = 2;
	/** The leather of a realm's levy (caps and boots). */
	static final int LEVY_BROWN = 0x4B3621;

	static final Identifier HEALTH_ID = JaymosKingdoms.id("rank_health"), DAMAGE_ID = JaymosKingdoms.id("rank_damage");

	/** A soldier's rank, given ({@link #BOW} added for the bow's branch): saved, and seen by the client (the orders screen). */
	public static final AttachmentType<Integer> RANK = AttachmentRegistry.create(JaymosKingdoms.id("troop_rank"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** The realm (a faction id) a recruit was raised in. Saved. */
	public static final AttachmentType<Integer> REALM = AttachmentRegistry.create(JaymosKingdoms.id("troop_realm"),
		builder -> builder.persistent(Codec.INT));

	private Ranks() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity e && e.hasAttached(RANK)) {
				apply(e);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SWEEP == 0) {
				for (ServerLevel level : server.getAllLevels()) {
					sweep(level);
				}
			}
		});
	}

	// ---- who stands where ----------------------------------------------------------------------------------------------

	/** The rank part of {@code code} ({@link #MILITIA} to {@link #LEADER}). */
	public static int step(int code) {
		return code & 7;
	}

	/** Whether {@code code} is of the bow's branch. */
	public static boolean bow(int code) {
		return (code & BOW) != 0;
	}

	/** {@code e}'s rank ({@link #BOW} added for an archer), or -1 for one who isn't a soldier. */
	public static int of(LivingEntity e) {
		Integer given = e.getAttached(RANK);
		if (given != null) {
			return given;
		}
		MinecraftServer server = e.level().getServer();
		if (server == null) {
			return -1;
		}
		int role = realmRole(server, e);
		if (role < 0) {
			return -1;
		}
		float share = Math.floorMod(e.getUUID().hashCode(), 1000) / 1000.0F;
		if (role == Folk.ARCHER) {
			return BOW | (share < BOW_SHARES[0] ? KNIGHT : share < BOW_SHARES[1] ? VETERAN : LEADER);
		}
		int step = 0;
		while (step < SHARES.length && share >= SHARES[step]) {
			step++;
		}
		return step;
	}

	/** {@code e} heads a squad. */
	public static boolean leads(LivingEntity e) {
		int code = of(e);
		return code >= 0 && step(code) == LEADER;
	}

	/** The role ({@link Folk#GUARD} or {@link Folk#ARCHER}) of {@code e} in a standing realm's service, or -1. */
	private static int realmRole(MinecraftServer server, LivingEntity e) {
		if (!(e instanceof Worker w) || w.role() != Folk.GUARD && w.role() != Folk.ARCHER) {
			return -1;
		}
		Faction f = Factions.get(server, w.factionId());
		return f != null && f.type().isRealm() && !f.fallen() ? w.role() : -1;
	}

	/** The realm whose tree {@code e} climbs: the one they serve, or a recruit's, else where they stand; null if none. */
	static @Nullable Faction realm(ServerLevel level, LivingEntity e) {
		MinecraftServer server = level.getServer();
		Integer raised = e.getAttached(REALM);
		if (raised != null) {
			Faction f = Factions.get(server, raised);
			if (f != null) {
				return f;
			}
		}
		if (e instanceof Worker w && w.factionId() >= 0) {
			Faction f = Factions.get(server, w.factionId());
			if (f != null) {
				return f;
			}
		}
		return Factions.at(server, level, e.blockPosition());
	}

	/** (Drifters) {@code v} turned up at {@code f}: taken on, they are raised in its tree. */
	static void enlist(LivingEntity v, Faction f) {
		v.setAttached(REALM, f.id());
	}

	// ---- climbing ------------------------------------------------------------------------------------------------------

	/** The rank a recruit with {@code wins} has earned. */
	static int earned(int wins) {
		int step = 0;
		while (step + 1 < WINS_TO.length && wins >= WINS_TO[step + 1]) {
			step++;
		}
		return step;
	}

	/**
	 * (Learners, Veterans) {@code v}'s wins may have earned them a rank: if so they take it, with the realm's kit for it
	 * and (unless {@code quiet}) a few words. One who gets to knight with a bow in hand goes the archers' way.
	 */
	static void rise(VillagerEntityMCA v, boolean quiet) {
		if (!(v.level() instanceof ServerLevel level)) {
			return;
		}
		Integer had = v.getAttached(RANK);
		if (had == null && !v.hasAttached(REALM)) {
			Faction here = realm(level, v);
			if (here != null) {
				enlist(v, here);
			}
		}
		int step = earned(v.getAttachedOrElse(Learners.WINS, 0));
		if (had != null && step <= step(had)) {
			return;
		}
		boolean bow = had != null ? bow(had) : false;
		if (step >= KNIGHT && (had == null || step(had) < KNIGHT)) {
			bow = archer(v);
		}
		int code = step | (bow ? BOW : 0);
		v.setAttached(RANK, code);
		kit(level, v, code);
		apply(v);
		if (!quiet && had != null) {
			level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1.0F, 0.9F);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1.2, v.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
			Faction realm = realm(level, v);
			Speech.say(level, v, Component.translatable("speech.jaymoskingdoms.rank." + NAMES[step] + "." + v.getRandom().nextInt(LINES),
				realm == null ? Component.translatable("speech.jaymoskingdoms.rank.nowhere") : Component.literal(realm.name())), Speech.WHITE, SPEECH_RANGE);
		}
	}

	/** Whether {@code m} fights with a bow (or a crossbow) just now: they take the archers' branch. */
	private static boolean archer(Mob m) {
		return m.getMainHandItem().getItem() instanceof ProjectileWeaponItem || m.getOffhandItem().getItem() instanceof ProjectileWeaponItem
			|| m instanceof VillagerEntityMCA v && v.getProfession() == net.conczin.mca.registry.ProfessionsMCA.ARCHER;
	}

	/** (WarParties) {@code m} heads a squad of its party: a leader of its branch, with the realm's banner. */
	static void lead(Mob m) {
		int code = of(m);
		m.setAttached(RANK, LEADER | (code >= 0 && bow(code) ? BOW : 0));
		apply(m);
		if (m.level() instanceof ServerLevel level) {
			standard(level, m);
		}
	}

	/** {@code e}'s rank's toughness, as permanent modifiers (none for one who isn't a soldier). */
	static void apply(LivingEntity e) {
		int code = of(e);
		if (code < 0) {
			return;
		}
		int step = step(code);
		AttributeInstance health = e.getAttribute(Attributes.MAX_HEALTH), damage = e.getAttribute(Attributes.ATTACK_DAMAGE);
		if (health != null) {
			AttributeModifier now = health.getModifier(HEALTH_ID);
			if (now == null || now.amount() != HEALTH[step]) {
				float was = e.getMaxHealth();
				health.addOrReplacePermanentModifier(new AttributeModifier(HEALTH_ID, HEALTH[step], AttributeModifier.Operation.ADD_VALUE));
				if (e.getMaxHealth() > was) {
					e.heal(e.getMaxHealth() - was);
				}
			}
		}
		if (damage != null) {
			AttributeModifier now = damage.getModifier(DAMAGE_ID);
			if (now == null || now.amount() != DAMAGE[step]) {
				damage.addOrReplacePermanentModifier(new AttributeModifier(DAMAGE_ID, DAMAGE[step], AttributeModifier.Operation.ADD_VALUE));
			}
		}
	}

	// ---- the realm's kit -----------------------------------------------------------------------------------------------

	/** Mixes a realm's id into the choices its tree makes. */
	private static long hash(int realm) {
		long z = realm * 0xD1B54A32D192ED03L + 0x7F4A7C15L;
		z = (z ^ z >>> 31) * 0xBF58476D1CE4E5B9L;
		return z ^ z >>> 29;
	}

	/** {@code v}, a recruit, puts on the kit of rank {@code code} in their realm's tree. */
	static void kit(ServerLevel level, VillagerEntityMCA v, int code) {
		Faction realm = realm(level, v);
		Outfits.Heraldry arms = realm == null ? null : Outfits.heraldry(realm, level.getServer());
		Climate climate = realm == null ? Climate.PLAINS : Climate.at(level, realm.seat());
		boolean iron = realm != null && IronVeins.near(level, realm);
		long h = realm == null ? 0L : hash(realm.id());
		int field = arms == null ? 0x8A8A8A : Outfits.rgb(arms.field());
		int step = step(code);
		ItemStack shield = arms == null ? new ItemStack(Items.SHIELD) : shield(level, arms);
		if (bow(code)) {
			// the bow's branch: the realm's hood and tunic, a mail shirt for its veterans, its bow or its crossbow
			wear(v, EquipmentSlot.HEAD, Outfits.dyed(Items.LEATHER_HELMET, field));
			wear(v, EquipmentSlot.CHEST, trim(level, step >= VETERAN ? new ItemStack(Items.CHAINMAIL_CHESTPLATE) : Outfits.dyed(Items.LEATHER_CHESTPLATE, field), arms));
			wear(v, EquipmentSlot.LEGS, Outfits.dyed(Items.LEATHER_LEGGINGS, arms == null ? LEVY_BROWN : Outfits.rgb(arms.charge())));
			wear(v, EquipmentSlot.FEET, Outfits.dyed(Items.LEATHER_BOOTS, LEVY_BROWN));
			wear(v, EquipmentSlot.MAINHAND, new ItemStack((h >>> 3 & 1) == 0 ? Items.BOW : Items.CROSSBOW));
			if (step == LEADER) {
				standard(level, v);
			}
			return;
		}
		switch (step) {
			case MILITIA -> {
				// the levy: a leather cap and tunic in the realm's colour, its spear or its hatchet, and its shield
				wear(v, EquipmentSlot.HEAD, Outfits.dyed(Items.LEATHER_HELMET, LEVY_BROWN));
				wear(v, EquipmentSlot.CHEST, trim(level, Outfits.dyed(Items.LEATHER_CHESTPLATE, field), arms));
				wear(v, EquipmentSlot.FEET, Outfits.dyed(Items.LEATHER_BOOTS, LEVY_BROWN));
				boolean spear = climate == Climate.JUNGLE || (h & 1) == 0;
				wear(v, EquipmentSlot.MAINHAND, new ItemStack(spear ? iron ? Items.IRON_SPEAR : Items.COPPER_SPEAR : iron ? Items.IRON_AXE : Items.COPPER_AXE));
				wear(v, EquipmentSlot.OFFHAND, shield);
			}
			case SOLDIER -> {
				// the realm's harness in mail, its trim on it, a sword and the shield
				wear(v, EquipmentSlot.HEAD, trim(level, new ItemStack(Items.CHAINMAIL_HELMET), arms));
				wear(v, EquipmentSlot.CHEST, trim(level, new ItemStack(iron ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE), arms));
				wear(v, EquipmentSlot.LEGS, trim(level, new ItemStack(Items.CHAINMAIL_LEGGINGS), arms));
				wear(v, EquipmentSlot.FEET, new ItemStack(iron ? Items.IRON_BOOTS : Items.COPPER_BOOTS));
				wear(v, EquipmentSlot.MAINHAND, new ItemStack(iron ? Items.IRON_SWORD : Items.COPPER_SWORD));
				wear(v, EquipmentSlot.OFFHAND, shield);
			}
			default -> {
				// knight and above: the land's champions' harness
				champion(level, v, realm, arms, climate, iron);
				if (step >= VETERAN) {
					// a veteran's legs in plate too, and the realm's polearm where the Rogues' are about
					wear(v, EquipmentSlot.LEGS, trim(level, new ItemStack(iron ? Items.IRON_LEGGINGS : Items.COPPER_LEGGINGS), arms));
					if (iron && (h >>> 5 & 1) == 0) {
						RpgKit.item("rogues:iron_glaive").ifPresent(g -> wear(v, EquipmentSlot.MAINHAND, new ItemStack(g)));
					}
				}
				if (step == LEADER) {
					standard(level, v);
				} else {
					wear(v, EquipmentSlot.OFFHAND, shield);
				}
			}
		}
	}

	/** A knight's kit: the realm's knights' plate in the temperate lands, else the land's own champions' set, else trimmed iron. */
	private static void champion(ServerLevel level, VillagerEntityMCA v, @Nullable Faction realm, Outfits.@Nullable Heraldry arms, Climate climate, boolean iron) {
		EquipmentSlot[] knights = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
		ItemStack[] had = new ItemStack[knights.length];
		for (int i = 0; i < knights.length; i++) {
			had[i] = v.getItemBySlot(knights[i]).copy();
		}
		if (realm != null && HeraldryWear.knight(v, realm)) {
			// (his realm's knights' plate, issued as the rest is: what he had back on, and each piece put on only where it's better)
			for (int i = 0; i < knights.length; i++) {
				ItemStack plate = v.getItemBySlot(knights[i]).copy();
				v.setItemSlot(knights[i], had[i]);
				wear(v, knights[i], plate);
			}
			wear(v, EquipmentSlot.LEGS, trim(level, new ItemStack(Items.CHAINMAIL_LEGGINGS), arms));
			return;
		}
		String set = switch (climate) {
			case DESERT, BADLANDS, SAVANNA -> "gladiator";
			case JUNGLE, CHERRY -> "ronin";
			case SNOWY, TAIGA -> "paladin";
			default -> null;
		};
		int field = arms == null ? -1 : Outfits.rgb(arms.field());
		Item[] plain = iron ? new Item[] {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.IRON_BOOTS}
			: new Item[] {Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.COPPER_BOOTS};
		ArmorType[] types = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < slots.length; i++) {
			Item piece = set == null ? null : Armory.piece(set, types[i]);
			ItemStack stack = piece == null ? new ItemStack(plain[i]) : new ItemStack(piece);
			if (piece != null && field != -1) {
				stack.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(field));
			}
			wear(v, slots[i], trim(level, stack, arms));
		}
		wear(v, EquipmentSlot.MAINHAND, new ItemStack(iron ? Items.IRON_SWORD : Items.COPPER_SWORD));
	}

	/** {@code m} carries its realm's banner in its off hand: the standard its squad follows. */
	private static void standard(ServerLevel level, Mob m) {
		Faction realm = realm(level, m);
		if (realm == null || m.getOffhandItem().getItem() instanceof BannerItem) {
			return;
		}
		ItemStack banner = Outfits.banner(level, realm);
		if (m instanceof VillagerEntityMCA v) {
			wear(v, EquipmentSlot.OFFHAND, banner);
		} else {
			m.setItemSlot(EquipmentSlot.OFFHAND, banner);
			m.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
		}
	}

	/**
	 * {@code v} is issued {@code piece} for {@code slot}: put on where the slot is empty or holds something worse, else
	 * not issued (their own better gear stays on). Nothing is ever lost: what it displaces goes into their pack, or drops
	 * at their feet if that's full. Armour and weapons go in the pack as well (MCA dresses and arms them from it, and
	 * would take off what isn't there); a weapon of the other kind (a bow for a blade) only goes in the pack, so they
	 * still change between them by range.
	 */
	private static void wear(VillagerEntityMCA v, EquipmentSlot slot, ItemStack piece) {
		if (piece.isEmpty() || !(v.level() instanceof ServerLevel level)) {
			return;
		}
		SimpleContainer pack = v.getInventory();
		ItemStack worn = v.getItemBySlot(slot);
		boolean weapon = slot == EquipmentSlot.MAINHAND, packed = weapon || slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
		if (!worn.isEmpty()) {
			if (weapon && weapon(worn) && ranged(worn) != ranged(piece)) {
				// (the other kind: carried, not held)
				if (!holds(pack, piece)) {
					pack.addItem(piece.copy());
				}
				return;
			}
			if (!better(v, slot, piece, worn)) {
				return;
			}
		}
		if (packed && !pack.addItem(piece.copy()).isEmpty()) {
			// (no room in their pack, so MCA would take it off them again: not issued)
			return;
		}
		if (!worn.isEmpty()) {
			stow(level, v, worn);
		}
		// (the very stack in their pack, as MCA dresses them: wear on one is wear on the other)
		ItemStack own = packed ? held(pack, piece) : null;
		v.setItemSlot(slot, own != null ? own : piece.copy());
	}

	/** The stack in {@code pack} the same as {@code stack}, or null. */
	private static @Nullable ItemStack held(SimpleContainer pack, ItemStack stack) {
		for (int i = 0; i < pack.getContainerSize(); i++) {
			if (ItemStack.isSameItemSameComponents(pack.getItem(i), stack)) {
				return pack.getItem(i);
			}
		}
		return null;
	}

	/** {@code stack}, taken off {@code v}, goes into their pack (if it isn't there already), or drops at their feet. */
	private static void stow(ServerLevel level, VillagerEntityMCA v, ItemStack stack) {
		SimpleContainer pack = v.getInventory();
		if (holds(pack, stack)) {
			return;
		}
		ItemStack left = pack.addItem(stack.copy());
		if (!left.isEmpty()) {
			v.spawnAtLocation(level, left);
		}
	}

	private static boolean holds(SimpleContainer pack, ItemStack stack) {
		return held(pack, stack) != null;
	}

	private static boolean weapon(ItemStack stack) {
		return stack.has(DataComponents.WEAPON) || ranged(stack);
	}

	private static boolean ranged(ItemStack stack) {
		return stack.getItem() instanceof ProjectileWeaponItem;
	}

	/**
	 * Whether {@code piece} is better in {@code slot} than {@code worn}: more armour (and toughness), a weapon that hits
	 * harder (or any weapon over a thing that isn't one), a banner for a leader's hand, the realm's shield over a plain one.
	 */
	private static boolean better(LivingEntity e, EquipmentSlot slot, ItemStack piece, ItemStack worn) {
		if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
			return value(piece, Attributes.ARMOR, 0.0, slot) + value(piece, Attributes.ARMOR_TOUGHNESS, 0.0, slot)
				> value(worn, Attributes.ARMOR, 0.0, slot) + value(worn, Attributes.ARMOR_TOUGHNESS, 0.0, slot);
		}
		if (slot == EquipmentSlot.MAINHAND) {
			if (!weapon(worn)) {
				return true;
			}
			if (ranged(worn)) {
				// (a bow for a bow: theirs stays)
				return false;
			}
			double base = e.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
			return value(piece, Attributes.ATTACK_DAMAGE, base, slot) > value(worn, Attributes.ATTACK_DAMAGE, base, slot);
		}
		if (piece.getItem() instanceof BannerItem) {
			return !(worn.getItem() instanceof BannerItem);
		}
		return piece.has(DataComponents.BLOCKS_ATTACKS) && worn.has(DataComponents.BLOCKS_ATTACKS) && !worn.has(DataComponents.BANNER_PATTERNS)
			&& piece.has(DataComponents.BANNER_PATTERNS);
	}

	private static double value(ItemStack stack, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double base, EquipmentSlot slot) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
			.compute(attribute, base, slot);
	}

	/** A shield bearing {@code arms}. */
	private static ItemStack shield(ServerLevel level, Outfits.Heraldry arms) {
		ItemStack stack = new ItemStack(Items.SHIELD);
		stack.set(DataComponents.BASE_COLOR, arms.field());
		stack.set(DataComponents.BANNER_PATTERNS, Outfits.layers(level.registryAccess(), arms));
		return stack;
	}

	/** {@code stack} with {@code arms}' trim (unchanged without arms, or if either part isn't registered). */
	private static ItemStack trim(ServerLevel level, ItemStack stack, Outfits.@Nullable Heraldry arms) {
		if (arms == null) {
			return stack;
		}
		Optional<? extends Holder<TrimPattern>> p = level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).get(arms.pattern());
		Optional<? extends Holder<TrimMaterial>> m = level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).get(arms.material());
		if (p.isPresent() && m.isPresent()) {
			stack.set(DataComponents.TRIM, new ArmorTrim(m.get(), p.get()));
		}
		return stack;
	}

	// ---- a realm's own soldiers --------------------------------------------------------------------------------------

	/**
	 * From {@code McaEquipmentMixin}: {@code stack} is what {@code villager} (a realm's guard dressing for duty) is about
	 * to put in {@code slot}. Its militia wear the realm's levy leather instead: a cap, a tunic of its colour, boots.
	 */
	public static ItemStack issue(LivingEntity villager, EquipmentSlot slot, ItemStack stack) {
		if (villager.hasAttached(RANK) || !(villager.level() instanceof ServerLevel level) || FrontierArmour.setOf(stack) != null
			|| slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.FEET) {
			return stack;
		}
		int code = of(villager);
		if (code < 0 || bow(code) || step(code) != MILITIA) {
			return stack;
		}
		Faction realm = realm(level, villager);
		Outfits.Heraldry arms = realm == null ? null : Outfits.heraldry(realm, level.getServer());
		return switch (slot) {
			case CHEST -> trim(level, Outfits.dyed(Items.LEATHER_CHESTPLATE, arms == null ? LEVY_BROWN : Outfits.rgb(arms.field())), arms);
			case HEAD -> Outfits.dyed(Items.LEATHER_HELMET, LEVY_BROWN);
			default -> Outfits.dyed(Items.LEATHER_BOOTS, LEVY_BROWN);
		};
	}

	/**
	 * From {@code McaEquipmentMixin}: whether MCA's own routine leaves {@code villager}'s {@code slot} hand as it is
	 * instead of putting {@code stack} there: a soldier's shield or banner in the off hand (a militia's wall, a leader's
	 * standard), which MCA would swap for a plain shield or nothing, unless it's arming that hand. A recruit's weapons
	 * are theirs to change: MCA arms them from their pack (where their issue is), the best blade or bow for the range;
	 * one held that isn't in the pack goes into it first, so nothing is lost.
	 */
	public static boolean keeps(LivingEntity villager, EquipmentSlot slot, ItemStack stack) {
		if (slot.getType() != EquipmentSlot.Type.HAND || !(villager.level() instanceof ServerLevel level) || of(villager) < 0) {
			return false;
		}
		ItemStack held = villager.getItemBySlot(slot);
		if (held.isEmpty() || ItemStack.isSameItemSameComponents(held, stack)) {
			return false;
		}
		if (slot == EquipmentSlot.OFFHAND && (held.has(DataComponents.BLOCKS_ATTACKS) || held.getItem() instanceof BannerItem) && !weapon(stack)) {
			return true;
		}
		if (villager.hasAttached(RANK) && villager instanceof VillagerEntityMCA v && weapon(held) && !holds(v.getInventory(), held)) {
			stow(level, v, held);
		}
		return false;
	}

	/** The soldiers round each player: their rank's toughness; a realm's militia and soldiers take up its shield, its leaders its banner. */
	private static void sweep(ServerLevel level) {
		if (level.players().isEmpty()) {
			return;
		}
		List<PathfinderMob> seen = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(SWEEP_RANGE),
				m -> m.isAlive() && !m.isBaby() && (m.hasAttached(RANK) || m instanceof Worker))) {
				if (!seen.contains(m)) {
					seen.add(m);
				}
			}
		}
		for (PathfinderMob m : seen) {
			int code = of(m);
			if (code < 0) {
				continue;
			}
			apply(m);
			if (m.hasAttached(RANK) && !(m instanceof Folk)) {
				// (a recruit's kit is their own; a party's leader already has his banner)
				continue;
			}
			if (step(code) == LEADER) {
				standard(level, m);
			} else if (!bow(code) && step(code) <= SOLDIER && m.getOffhandItem().isEmpty() && !(m.getMainHandItem().getItem() instanceof ProjectileWeaponItem)
				&& !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(m.getMainHandItem().getItem()).getPath().contains("double_axe")) {
				Faction realm = realm(level, m);
				if (realm != null) {
					m.setItemSlot(EquipmentSlot.OFFHAND, shield(level, Outfits.heraldry(realm, level.getServer())));
					m.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
				}
			}
		}
	}
}
