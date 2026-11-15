package com.jaymoskingdoms;

import com.jaymoskingdoms.barrow.Revenant;
import com.jaymoskingdoms.barrow.ZombieKnight;
import com.jaymoskingdoms.remnants.Monoliths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Fantasy Armor (mod id {@value #MOD}, kenddie's Medieval Series; all rights reserved, nothing of it copied here) puts its
 * sets on the world's people where it's loaded, by registry id, so there's no dependency. Only on monsters, the undead
 * and kings: its pieces are made over netherite, the crown's tier (Andy, 2026-10-29: kings wear netherite, nobody below
 * them may), so royal tiers still hold. The risen dead now and then come up in a whole dark set at the higher world tiers
 * ({@link MonsterGear}); a king in gilded attire wears a gilded set instead of his trimmed netherite ({@link Regalia}).
 * The Grave Sentinel's helm is never put on anyone (Andy, 2026-11-15: "the grave sentinel helmet should be removed").
 * <p>
 * The armourers' orders in Lore.md ("What the armourers make") wear Fantasy Armor's sets (Andy chose its models over
 * new art, 2026-11-15), each order the set below. So far:
 * <ul>
 * <li><b>Barrow wardens' mail</b> ({@link #BARROW}): it falls from the barrow's dead ({@link #BARROW_DROP}), and whoever
 * wears all of it walks in the barrows as the knights' own ({@link Revenant#keeper}).</li>
 * <li><b>Giant-slayers' harness</b> ({@link #GIANT_SLAYER}): when a Giant falls, a plate of it lies where it fell for
 * each who was there ({@link #GIANT_WITNESS} blocks).</li>
 * <li><b>The demons' ashen plate</b> ({@link #ASHEN}): a captain of the Ash (Jaymo's Powers' demon realm) now and then
 * drops a piece ({@link #ASHEN_DROP}).</li>
 * <li><b>The pit harness</b> ({@link #PIT}): a fighting pit's warlord drops a piece of it when he falls; it is never sold.</li>
 * <li><b>Folded lamellar</b> ({@link #FOLDED}): some of the blossom lords' guards wear it ({@link Outfits#ronin}).</li>
 * <li><b>The Warden's hunters' coats</b> ({@link #HUNTER}): "can still be bought from the Warden's own", at the
 * armourer of a realm that keeps the Lantern Warden, by one sworn to him.</li>
 * <li><b>Dawnwatch plate</b> ({@link #DAWNWATCH}): the Crowned Sun's realms' armourers sell it to his sworn at high favour
 * ({@link #DAWNWATCH_TIER}).</li>
 * <li><b>Back to back</b> ({@link #TWINNED}): two who each wear at least {@link #TWIN_HALF} of its pieces and each use a
 * shrine they both stand at ({@link #TWIN_SWEAR} blocks of it), one after the other, are sworn to each other ({@link #SWORN}); while both have their half on
 * and are within {@link #TWIN_RANGE} blocks, {@link #TWIN_SHARE} of each hit that lands on one is borne by the other.
 * One who swears another breaks the old oath. (A blow that kills outright isn't shared.)</li>
 * </ul>
 */
public final class FantasyArmorCompat {
	public static final String MOD = "fantasy_armor";
	static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);
	/**
	 * The orders' sets (Lore.md, "What the armourers make"): the barrow wardens', the dawnwatch's, the headsmen's, the
	 * Giant-slayers', the sky folk elders' robes, the well-wardens', the demons' ashen plate, the Warden's hunters', the
	 * blossom guards' folded lamellar, the pit champions' and the paired harness sworn back to back.
	 */
	public static final String BARROW = "grave_sentinel", DAWNWATCH = "dawncrest", HEADSMAN = "golden_execution", GIANT_SLAYER = "dragonslayer",
		SKY_ROBES = "wind_worshipper", WELL_WARDEN = "fog_guard", ASHEN = "dark_lord", HUNTER = "evening_ghost", FOLDED = "ronin",
		PIT = "dead_gladiator", TWINNED = "twinned";
	/** The sets the risen dead come up in. */
	static final List<String> UNDEAD_SETS = List.of("dark_cover", BARROW);
	/**
	 * The sets a king may wear. Andy (2026-11-15): "kings can wear whatever they want, except dawncrest" (the dawnwatch's):
	 * every set but that one and those taken out of the pack (the ones named after other games' characters).
	 */
	static final List<String> KING_SETS = List.of("chess_board_knight", "dark_cover", "dark_lord", "dead_gladiator", "dragonslayer",
		"eclipse_soldier", "evening_ghost", "flesh_of_the_feaster", "fog_guard", "forgotten_trace", "gilded_hunt", "golden_execution",
		"golden_horns", "grave_sentinel", "hero", "lady_maria", "redeemer", "ronin", "spark_of_dawn", "sunset_wings", "thief", "twinned",
		"wandering_wizard", "wind_worshipper");
	/** The chance a barrow's dead drop a piece of the wardens' mail: a household knight, and the revenant his lord. */
	static final float[] BARROW_DROP = {0.08F, 0.6F};
	/** Jaymo's Powers' tag on a demon captain of the Ash (its demon realm's middle layer). */
	static final String ASH_CAPTAIN_TAG = "jaymospowers_demon_captain_1";
	/** The chance a captain of the Ash drops a piece of the ashen plate, and that a pit's warlord drops one of the pit harness. */
	static final float ASHEN_DROP = 0.35F, PIT_DROP = 1.0F;
	/** How near a falling Giant one has to be to have been there for its fall (blocks). */
	static final double GIANT_WITNESS = 48.0;
	/** Prices of a set's pieces (emeralds: helm, breastplate, leggings, boots): made over netherite, the dearest on any counter. */
	static final int[] HUNTER_PRICE = {28, 44, 36, 24}, DAWNWATCH_PRICE = {34, 52, 44, 30};
	/** The favour tier one sworn to the Crowned Sun needs before his realms' armourers sell them the dawnwatch's plate. */
	static final int DAWNWATCH_TIER = 3;
	static final int USES = 2, XP = 10;
	static final float PRICE_MULT = 0.05F;
	/** Back to back: the pieces each must wear, how near the two must stand at the shrine and stay, and the share of a hit the other bears. */
	static final int TWIN_HALF = 2;
	static final double TWIN_SWEAR = 4.0, TWIN_RANGE = 16.0;
	/** How long the first to use the shrine waits for the other's hand (ticks). */
	static final long TWIN_WAIT = 200;
	static final float TWIN_SHARE = 0.25F;
	/** On a player: the one they swore back to back with at a shrine. Saved, kept through death. */
	static final AttachmentType<UUID> SWORN = AttachmentRegistry.create(JaymosKingdoms.id("twinned_sworn"),
		builder -> builder.persistent(UUIDUtil.CODEC).copyOnDeath());
	/** Set while a share is being dealt, so the share isn't shared back. */
	private static boolean sharing;
	/** The share of the blossom guards in folded lamellar (the rest in the Armory's ronin harness). */
	static final float FOLDED_CHANCE = 0.35F;
	/** A set whose helm nobody wears (its slot is left as it was). */
	static final String NO_HELM = "grave_sentinel";
	private static final String[] PIECES = {"_helmet", "_chestplate", "_leggings", "_boots"};
	static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private FantasyArmorCompat() {
	}

	static void register() {
		if (!LOADED) {
			return;
		}
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> bear(entity, taken));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level)) {
				return;
			}
			RandomSource random = entity.getRandom();
			float barrow = entity instanceof Revenant ? BARROW_DROP[1] : entity instanceof ZombieKnight ? BARROW_DROP[0] : 0.0F;
			if (barrow > 0.0F && random.nextFloat() < barrow) {
				// (its helm is never handed out: the body, the legs or the boots)
				piece(BARROW, 1 + random.nextInt(3)).ifPresent(item -> entity.spawnAtLocation(level, new ItemStack(item)));
			}
			float ashen = entity.entityTags().contains(ASH_CAPTAIN_TAG) ? ASHEN_DROP : 0.0F;
			if (ashen > 0.0F && random.nextFloat() < ashen) {
				piece(ASHEN, random.nextInt(4)).ifPresent(item -> entity.spawnAtLocation(level, new ItemStack(item)));
			}
			if (IllagerSites.pitWarlord(level.getServer(), entity) && random.nextFloat() < PIT_DROP) {
				piece(PIT, random.nextInt(4)).ifPresent(item -> entity.spawnAtLocation(level, new ItemStack(item)));
			}
			if (Monoliths.giant(entity)) {
				for (Player player : level.players()) {
					if (!player.isSpectator() && player.distanceToSqr(entity) < GIANT_WITNESS * GIANT_WITNESS) {
						piece(GIANT_SLAYER, random.nextInt(4)).ifPresent(item -> entity.spawnAtLocation(level, new ItemStack(item)));
					}
				}
			}
		});
	}

	/** How many of {@code set}'s pieces {@code e} has on. */
	static int worn(LivingEntity e, String set) {
		int n = 0;
		for (int i = 0; i < SLOTS.length; i++) {
			Optional<Item> piece = piece(set, i);
			if (piece.isPresent() && e.getItemBySlot(SLOTS[i]).is(piece.get())) {
				n++;
			}
		}
		return n;
	}

	/**
	 * (from {@link Shrine}, {@code player} using one with an empty hand) Back to back: one in their half of the paired
	 * harness uses the shrine with another in theirs at it too ({@link #TWIN_SWEAR} blocks of it), and that one uses it
	 * after them within {@link #TWIN_WAIT} ticks: the two are sworn to each other. True if this use was part of the oath
	 * (the shrine does nothing else then); alone at it, they pray as anyone does.
	 */
	static boolean swear(ServerPlayer player, BlockPos shrine) {
		if (!LOADED || worn(player, TWINNED) < TWIN_HALF || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(shrine)) > TWIN_SWEAR * TWIN_SWEAR) {
			return false;
		}
		long now = player.level().getGameTime();
		ServerPlayer first = null, beside = null;
		for (ServerPlayer other : player.level().players()) {
			if (other == player || other.isSpectator() || worn(other, TWINNED) < TWIN_HALF
				|| other.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(shrine)) > TWIN_SWEAR * TWIN_SWEAR) {
				continue;
			}
			Offer offer = OFFERS.get(other.getUUID());
			if (offer != null && offer.shrine().equals(shrine) && now - offer.at() <= TWIN_WAIT) {
				first = other;
			}
			beside = beside == null || other.distanceToSqr(player) < beside.distanceToSqr(player) ? other : beside;
		}
		if (first != null) {
			// the second of the two: sworn
			OFFERS.remove(first.getUUID());
			player.setAttached(SWORN, first.getUUID());
			first.setAttached(SWORN, player.getUUID());
			player.level().playSound(null, shrine, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
			player.level().playSound(null, shrine, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.6F, 1.2F);
			return true;
		}
		if (beside == null) {
			return false;
		}
		// the first of the two: it waits on the other's hand
		OFFERS.put(player.getUUID(), new Offer(shrine.immutable(), now));
		player.level().playSound(null, shrine, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.9F);
		return true;
	}

	/** One who has used a shrine to swear back to back, waiting on the other. */
	private record Offer(BlockPos shrine, long at) {
	}

	private static final java.util.Map<UUID, Offer> OFFERS = new java.util.HashMap<>();

	/** {@code e} took {@code taken}: if they're sworn back to back and both have their half on, near, the other bears a share. */
	private static void bear(LivingEntity e, float taken) {
		if (sharing || taken <= 0.0F || !e.isAlive() || !(e instanceof ServerPlayer player) || worn(player, TWINNED) < TWIN_HALF) {
			return;
		}
		UUID sworn = player.getAttached(SWORN);
		ServerPlayer other = sworn == null ? null : player.level().getServer().getPlayerList().getPlayer(sworn);
		if (other == null || !other.isAlive() || other.level() != player.level() || !player.getUUID().equals(other.getAttached(SWORN))
			|| worn(other, TWINNED) < TWIN_HALF || other.distanceToSqr(player) > TWIN_RANGE * TWIN_RANGE) {
			return;
		}
		float share = taken * TWIN_SHARE;
		player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + share));
		sharing = true;
		try {
			other.hurtServer(other.level(), other.damageSources().generic(), share);
		} finally {
			sharing = false;
		}
	}

	/**
	 * (from {@link FrontierWear#offers}, an armourer of {@code f}) the orders' sets on the counter for {@code player}, or
	 * taken off it for anyone else.
	 */
	static void armourer(net.minecraft.server.MinecraftServer server, Faction f, Player player, MerchantOffers offers) {
		if (!LOADED) {
			return;
		}
		Patron realm = f.type().isRealm() ? RealmFaiths.patron(server, f) : null;
		counter(offers, HUNTER, HUNTER_PRICE, realm == Patron.LANTERN_WARDEN && Patrons.of(player) == Patron.LANTERN_WARDEN);
		counter(offers, DAWNWATCH, DAWNWATCH_PRICE, realm == Patron.CROWNED_SUN && Patrons.blessed(player, Patron.CROWNED_SUN, DAWNWATCH_TIER));
	}

	/** {@code set}'s pieces at {@code prices} on the counter if {@code sold}; else off it. */
	private static void counter(MerchantOffers offers, String set, int[] prices, boolean sold) {
		for (int i = 0; i < SLOTS.length; i++) {
			Optional<Item> piece = piece(set, i);
			if (piece.isEmpty()) {
				continue;
			}
			Item item = piece.get();
			boolean there = offers.stream().anyMatch(o -> o.getResult().is(item));
			if (sold && !there) {
				offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, prices[i]), new ItemStack(item), USES, XP, PRICE_MULT));
			} else if (!sold && there) {
				offers.removeIf(o -> o.getResult().is(item));
			}
		}
	}

	/** {@code e} has on every piece of {@code set} anyone wears (the barrow wardens' mail: all but the helm). */
	public static boolean whole(LivingEntity e, String set) {
		if (!LOADED) {
			return false;
		}
		boolean any = false;
		for (int i = 0; i < SLOTS.length; i++) {
			Optional<Item> piece = piece(set, i);
			if (piece.isPresent()) {
				if (!e.getItemBySlot(SLOTS[i]).is(piece.get())) {
					return false;
				}
				any = true;
			}
		}
		// (a set this build of the mod hasn't got is worn by nobody)
		return any;
	}

	/** {@code e} in every piece of {@code set}; false (nothing changed) where the mod or the set isn't there. */
	static boolean dress(LivingEntity e, String set) {
		boolean any = false;
		for (int i = 0; i < SLOTS.length; i++) {
			Optional<Item> piece = piece(set, i);
			if (piece.isPresent()) {
				e.setItemSlot(SLOTS[i], new ItemStack(piece.get()));
				any = true;
			}
		}
		return any;
	}

	/** One of {@code sets}, at random, or null where the mod isn't loaded. */
	static String pick(List<String> sets, RandomSource random) {
		return LOADED ? sets.get(random.nextInt(sets.size())) : null;
	}

	/** {@code set}'s piece for slot {@code i} (0 the helm to 3 the boots), if it's there and anyone may wear it. */
	static Optional<Item> piece(String set, int i) {
		if (set == null || i == 0 && set.equals(NO_HELM)) {
			return Optional.empty();
		}
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(MOD, set + PIECES[i]));
	}
}
