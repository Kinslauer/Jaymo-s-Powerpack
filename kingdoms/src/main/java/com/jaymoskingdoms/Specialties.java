package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jspecify.annotations.Nullable;

/**
 * What a companion can be taught, and the foe's own specialists (docs/Pawn-Plan.md row G2; after the pawn skills and
 * inclinations taught from books in Dragon's Dogma, a commercial game: ideas only).
 * <ul>
 * <li><b>The books</b>: a townsman who likes you well ({@link #GIFT_HEARTS} MCA hearts) gives you, once, the little book
 * of his own trade's knack ({@link #fromTrade}): a librarian or a mapmaker the tongue-speaker's, a cleric the field
 * medic's, a farmer, fisher or butcher the forager's, a leatherworker the packhorse's, a fletcher or a nobody the quiet
 * one's, any other trade the haggler's. Read it yourself; hand it to a companion (use it on them) and they learn it.
 * One knack each; a new book replaces the old.</li>
 * <li><b>Tongue-speaker</b>: with them by ({@link #NEAR}) you understand every tongue ({@link Languages#understands}).</li>
 * <li><b>Field medic</b>: minds the fallen whatever their leanings, and gets them up twice as fast ({@link FieldMedics}).</li>
 * <li><b>Forager</b>: on the road outdoors finds what the land gives ({@link #forage}) now and then and hands it over.</li>
 * <li><b>Haggler</b>: by you at a trade, every price drops {@link #HAGGLE} (at least one).</li>
 * <li><b>Packhorse</b>: carries what you throw at their feet ({@link #PACK} stacks) and gives it all back when you sit down
 * together ({@link TableTalk}), or drops it where they fall.</li>
 * <li><b>The quiet one</b>: when you sneak, they sneak, and foes farther than {@link #QUIET_SEEN} lose them.</li>
 * </ul>
 * <b>Foe specialists</b>: a band member may be one ({@link #FOE_TRACKER}, {@link #FOE_MEDIC}, {@link #FOE_TONGUE}, rolled
 * once when he first fights): a <b>tracker</b> keeps after a player he lost ({@link #TRACK_FOR}, {@link #TRACK_RANGE}), a
 * <b>medic</b> minds his fallen as a companion medic does, and a <b>tongue-speaker</b> understands your companions' calls
 * and warns the mate they're coming for ({@link #overheard}).
 */
public final class Specialties {
	public enum Specialty {
		TONGUE, MEDIC, FORAGER, HAGGLER, PACKHORSE, QUIET, TRACKER;

		public String key() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Specialty byKey(@Nullable String key) {
			if (key != null) {
				for (Specialty s : values()) {
					if (s.key().equals(key)) {
						return s;
					}
				}
			}
			return null;
		}
	}

	/** MCA hearts a townsman needs with you to give you his trade's book. */
	static final int GIFT_HEARTS = 50;
	/** A tongue-speaker or haggler this near you (blocks) counts. */
	static final double NEAR = 16.0, HAGGLE_NEAR = 10.0;
	/** A haggler's cut of each price. */
	static final float HAGGLE = 0.15F;
	/** A forager finds something about this often (ticks) on the road. */
	static final int FORAGE_EVERY = 2400;
	/** A packhorse carries this many stacks; picks up what lands this near it (blocks). */
	static final int PACK = 9;
	static final double PACK_REACH = 1.8;
	/** A quiet one is lost by foes farther than this (blocks) while you sneak. */
	static final double QUIET_SEEN = 10.0;
	/** Chances a band member is a tracker, a medic or a tongue-speaker. */
	static final float FOE_TRACKER = 0.08F, FOE_MEDIC = 0.06F, FOE_TONGUE = 0.05F;
	/** A tracker keeps after one he lost this long (ticks), this far (blocks). */
	static final int TRACK_FOR = 2400;
	static final double TRACK_RANGE = 64.0;
	/** A mate warned of what's coming braces for this long (ticks). */
	static final int BRACED = 60;
	/** Looked over this often (ticks). */
	static final int EVERY = 20;

	/** On a fighter: its specialty key, or "" (rolled, none). Saved. */
	public static final AttachmentType<String> SPECIALTY = AttachmentRegistry.create(JaymosKingdoms.id("comrade_specialty"),
		builder -> builder.persistent(Codec.STRING));
	/** On a townsman: he has given his book. Saved. */
	static final AttachmentType<Boolean> GAVE = AttachmentRegistry.create(JaymosKingdoms.id("specialty_book_given"),
		builder -> builder.persistent(Codec.BOOL));
	/** On a packhorse: what it carries. Saved. */
	static final AttachmentType<List<ItemStack>> PACKED = AttachmentRegistry.create(JaymosKingdoms.id("comrade_pack"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC.listOf()));

	private static final String TAG = "jaymoskingdoms_specialty";

	/** A tracker → whom he's after, and until when (game time). */
	private record Trail(UUID player, long until) {
	}

	private static final Map<UUID, Trail> TRAILS = new HashMap<>();
	private static final Map<UUID, Long> FORAGED = new HashMap<>();
	private static final Map<UUID, int[]> UNDERSTOOD = new HashMap<>();

	private Specialties() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Specialties::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TRAILS.clear();
			FORAGED.clear();
			UNDERSTOOD.clear();
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || world.isClientSide() || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getMainHandItem();
			Specialty taught = of(held);
			if (taught != null && entity instanceof Mob m && sp.getUUID().equals(Bonds.lordOf(m)) && !Downed.isDowned(m)) {
				teach(sp, m, held, taught);
				return InteractionResult.SUCCESS;
			}
			if (entity instanceof Villager v && taught == null) {
				give(sp, v);
			}
			return InteractionResult.PASS;
		});
	}

	// ---- who knows what ------------------------------------------------------------------------------------------------

	static @Nullable Specialty of(Entity e) {
		return Specialty.byKey(e.getAttached(SPECIALTY));
	}

	static boolean is(Entity e, Specialty s) {
		return of(e) == s;
	}

	/** The specialty a book teaches, or null. */
	static @Nullable Specialty of(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : Specialty.byKey(data.copyTag().getString(TAG).orElse(null));
	}

	// ---- the books -----------------------------------------------------------------------------------------------------

	/** The book of {@code s}'s knack, from {@code author}. */
	static ItemStack book(Specialty s, String author) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		String stem = "specialty.jaymoskingdoms." + s.key();
		List<Filterable<Component>> pages = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			pages.add(Filterable.passThrough(Component.translatable(stem + ".page." + i).withStyle(ChatFormatting.BLACK)));
		}
		pages.add(Filterable.passThrough(Component.translatable("specialty.jaymoskingdoms.teach").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(s.key()), author, 0, pages, true));
		book.set(DataComponents.ITEM_NAME, Component.translatable(stem + ".book"));
		CompoundTag tag = new CompoundTag();
		tag.putString(TAG, s.key());
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return book;
	}

	/** The knack a townsman's trade knows. */
	static Specialty fromTrade(Villager v) {
		var p = v.getVillagerData().profession();
		if (p.is(VillagerProfession.LIBRARIAN) || p.is(VillagerProfession.CARTOGRAPHER)) {
			return Specialty.TONGUE;
		}
		if (p.is(VillagerProfession.CLERIC)) {
			return Specialty.MEDIC;
		}
		if (p.is(VillagerProfession.FARMER) || p.is(VillagerProfession.FISHERMAN) || p.is(VillagerProfession.BUTCHER)) {
			return Specialty.FORAGER;
		}
		if (p.is(VillagerProfession.LEATHERWORKER)) {
			return Specialty.PACKHORSE;
		}
		if (p.is(VillagerProfession.FLETCHER) || p.is(VillagerProfession.NITWIT) || p.is(VillagerProfession.NONE)) {
			return Specialty.QUIET;
		}
		return Specialty.HAGGLER;
	}

	/** A townsman who likes {@code player} well enough gives them his trade's book, once. */
	private static void give(ServerPlayer player, Villager v) {
		if (v.isBaby() || Downed.isDowned(v) || v.getAttachedOrElse(GAVE, false) || !McaCompat.LOADED || McaCompat.heartsOf(v, player) < GIFT_HEARTS
			|| !Languages.understands(player, v)) {
			return;
		}
		v.setAttached(GAVE, true);
		Specialty s = fromTrade(v);
		ItemStack book = book(s, v.getName().getString());
		if (!player.getInventory().add(book)) {
			player.drop(book, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		Speech.say(player.level(), v, Dialects.tr(v, "specialty.jaymoskingdoms.give." + v.getRandom().nextInt(3)), Speech.GOLD, 16.0);
		player.level().playSound(null, v.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	/** {@code player} hands {@code m} the book of {@code s}: they learn it (and forget any knack they had). */
	private static void teach(ServerPlayer player, Mob m, ItemStack held, Specialty s) {
		if (s == of(m)) {
			Callouts.say(m, "spec.known", player, true);
			return;
		}
		if (of(m) == Specialty.PACKHORSE && s != Specialty.PACKHORSE) {
			unpack(m, player);
		}
		m.setAttached(SPECIALTY, s.key());
		if (!player.isCreative()) {
			held.shrink(1);
		}
		if (m.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ENCHANT, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 30, 0.4, 0.6, 0.4, 0.4);
			level.playSound(null, m.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.0F, 1.2F);
		}
		Callouts.say(m, "spec.learned", player, true, Component.translatable("specialty.jaymoskingdoms." + s.key()));
		Bonds.add(m, player.getUUID(), 2);
	}

	// ---- what each knack does ------------------------------------------------------------------------------------------

	/** (Languages) {@code player} has a tongue-speaker of theirs close by. */
	static boolean translated(ServerPlayer player) {
		int now = player.level().getServer().getTickCount();
		int[] cached = UNDERSTOOD.get(player.getUUID());
		if (cached != null && now - cached[0] < EVERY) {
			return cached[1] != 0;
		}
		boolean near = false;
		for (Mob m : Bonds.companions(player, NEAR)) {
			if (is(m, Specialty.TONGUE)) {
				near = true;
				break;
			}
		}
		UNDERSTOOD.put(player.getUUID(), new int[] {now, near ? 1 : 0});
		return near;
	}

	/** (FieldMedics) {@code m} minds the fallen by trade. */
	static boolean medic(LivingEntity m) {
		return is(m, Specialty.MEDIC);
	}

	/** (VillagerPriceMixin) a haggler of yours by you: every price drops. */
	public static void prices(Villager villager, Player player) {
		if (!(player instanceof ServerPlayer sp) || !(villager.level() instanceof ServerLevel)) {
			return;
		}
		Mob haggler = null;
		for (Mob m : Bonds.companions(sp, HAGGLE_NEAR)) {
			if (m != villager && is(m, Specialty.HAGGLER)) {
				haggler = m;
				break;
			}
		}
		if (haggler == null) {
			return;
		}
		for (MerchantOffer offer : villager.getOffers()) {
			int cost = offer.getBaseCostA().getCount();
			offer.addToSpecialPriceDiff(-Math.max(1, Math.round(cost * HAGGLE)));
		}
		Callouts.say(haggler, "spec.haggle", villager, false);
	}

	/** (Comrades) a companion called whom it's taking: a foe tongue-speaker near who understood warns that mate. */
	static void overheard(ServerLevel level, Mob caller, LivingEntity foe) {
		if (!(foe instanceof Mob mate) || !Comrades.side(caller).startsWith("p:")) {
			return;
		}
		String side = Comrades.side(mate);
		for (Mob m : level.getEntitiesOfClass(Mob.class, caller.getBoundingBox().inflate(16.0),
			m -> m.isAlive() && !Downed.isDowned(m) && is(m, Specialty.TONGUE) && Comrades.side(m).equals(side))) {
			if (Callouts.say(m, "spec.overheard", mate, false, mate.getDisplayName())) {
				mate.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, BRACED, 0));
			}
			return;
		}
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % EVERY != 0) {
			return;
		}
		long time = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			for (Mob m : Bonds.companions(player, 32.0)) {
				Specialty s = of(m);
				if (s == Specialty.FORAGER) {
					forage(player, m, time);
				} else if (s == Specialty.PACKHORSE) {
					pack(player, m);
				} else if (s == Specialty.QUIET) {
					quiet(player, m);
				}
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Mob m : Comrades.fighters(level)) {
				roll(m);
				if (is(m, Specialty.TRACKER) && Comrades.target(m) instanceof ServerPlayer p) {
					TRAILS.put(m.getUUID(), new Trail(p.getUUID(), time + TRACK_FOR));
				}
			}
		}
		track(server, time);
		// a packhorse that went down drops what it carried
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (Mob m : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0),
				m -> Downed.isDowned(m) && m.hasAttached(PACKED))) {
				unpack(m, null);
			}
		}
	}

	/** A band member's specialty, rolled once when he first fights. */
	private static void roll(Mob m) {
		if (m.hasAttached(SPECIALTY) || !FoeLore.keeps(Comrades.side(m))) {
			return;
		}
		float r = m.getRandom().nextFloat();
		Specialty s = r < FOE_TRACKER ? Specialty.TRACKER : r < FOE_TRACKER + FOE_MEDIC ? Specialty.MEDIC
			: r < FOE_TRACKER + FOE_MEDIC + FOE_TONGUE ? Specialty.TONGUE : null;
		m.setAttached(SPECIALTY, s == null ? "" : s.key());
	}

	/** Trackers who lost their player keep after them. */
	private static void track(MinecraftServer server, long time) {
		for (var it = TRAILS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Trail> e = it.next();
			Trail t = e.getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(t.player());
			if (time > t.until() || p == null || !p.isAlive() || p.isSpectator()) {
				it.remove();
				continue;
			}
			Entity found = p.level().getEntity(e.getKey());
			if (!(found instanceof Mob m) || !m.isAlive() || Downed.isDowned(m)) {
				if (found != null) {
					it.remove();
				}
				continue;
			}
			if (Comrades.target(m) != null || m.distanceTo(p) > TRACK_RANGE) {
				continue;
			}
			// on the trail: where they went, then at them when close
			if (m.distanceTo(p) < 16.0 && m.hasLineOfSight(p)) {
				Comrades.aim(m, p);
				Callouts.say(m, "spec.track", p, false, p.getDisplayName());
			} else if (time % 40 < EVERY) {
				m.getNavigation().moveTo(p.getX(), p.getY(), p.getZ(), 1.0);
			}
		}
	}

	/** A forager on the road finds what this land gives. */
	private static void forage(ServerPlayer player, Mob m, long time) {
		Long last = FORAGED.get(m.getUUID());
		if (last == null) {
			FORAGED.put(m.getUUID(), time);
			return;
		}
		if (time - last < FORAGE_EVERY || Comrades.target(m) != null || !player.level().canSeeSky(m.blockPosition())) {
			return;
		}
		FORAGED.put(m.getUUID(), time);
		Item found = forage(player.level(), m.blockPosition());
		if (found == null) {
			return;
		}
		ItemStack stack = new ItemStack(found, 1 + m.getRandom().nextInt(3));
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		m.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Callouts.say(m, "spec.forage", player, false, stack.getHoverName());
	}

	/** What the land at {@code at} gives a forager, or null (bare rock, sand, ice). */
	static @Nullable Item forage(ServerLevel level, BlockPos at) {
		String biome = level.getBiome(at).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		if (biome.contains("desert") || biome.contains("badlands") || biome.contains("peak") || biome.contains("frozen") || biome.contains("ice")) {
			return null;
		}
		if (biome.contains("ocean") || biome.contains("beach") || biome.contains("river")) {
			return Items.KELP;
		}
		if (biome.contains("swamp") || biome.contains("mushroom") || biome.contains("dark_forest")) {
			return level.getRandom().nextBoolean() ? Items.BROWN_MUSHROOM : Items.RED_MUSHROOM;
		}
		if (biome.contains("taiga") || biome.contains("snow") || biome.contains("grove")) {
			return Items.SWEET_BERRIES;
		}
		if (biome.contains("jungle")) {
			return Items.MELON_SLICE;
		}
		if (biome.contains("forest") || biome.contains("birch") || biome.contains("cherry")) {
			return Items.APPLE;
		}
		if (biome.contains("plains") || biome.contains("meadow") || biome.contains("savanna")) {
			return level.getRandom().nextBoolean() ? Items.CARROT : Items.POTATO;
		}
		return null;
	}

	/** A packhorse picks up what its player threw at its feet. */
	private static void pack(ServerPlayer player, Mob m) {
		List<ItemStack> packed = new ArrayList<>(m.getAttachedOrElse(PACKED, List.of()));
		boolean took = false;
		for (ItemEntity item : m.level().getEntitiesOfClass(ItemEntity.class, m.getBoundingBox().inflate(PACK_REACH),
			i -> i.isAlive() && i.getOwner() == player && !i.getItem().isEmpty())) {
			if (packed.size() >= PACK) {
				Callouts.say(m, "spec.pack.full", player, false);
				break;
			}
			packed.add(item.getItem().copy());
			item.discard();
			took = true;
		}
		if (took) {
			m.setAttached(PACKED, List.copyOf(packed));
			m.level().playSound(null, m.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.8F, 0.9F);
			Callouts.say(m, "spec.pack", player, false);
		}
	}

	/** A packhorse hands back (to {@code player}) or drops all it carries. True if there was anything. */
	static boolean unpack(Mob m, @Nullable ServerPlayer player) {
		List<ItemStack> packed = m.getAttachedOrElse(PACKED, List.of());
		m.removeAttached(PACKED);
		for (ItemStack stack : packed) {
			ItemStack copy = stack.copy();
			if (player == null || !player.getInventory().add(copy)) {
				m.spawnAtLocation((ServerLevel)m.level(), copy);
			}
		}
		return !packed.isEmpty();
	}

	/** The quiet one sneaks when you do, and foes far off lose them. */
	private static void quiet(ServerPlayer player, Mob m) {
		boolean sneak = player.isShiftKeyDown();
		if (Comrades.target(m) == null && !Comrades.tending(m)) {
			m.setShiftKeyDown(sneak);
		}
		if (!sneak || !(m.level() instanceof ServerLevel level)) {
			return;
		}
		for (Mob foe : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(32.0), f -> f.getTarget() == m)) {
			if (foe.distanceTo(m) > QUIET_SEEN && m.hurtTime == 0) {
				foe.setTarget(null);
			}
		}
	}
}
