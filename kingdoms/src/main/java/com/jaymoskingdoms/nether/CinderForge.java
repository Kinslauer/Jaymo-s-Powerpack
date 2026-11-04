package com.jaymoskingdoms.nether;

import com.jaymoskingdoms.Stacked;
import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.money.Coinage;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.Post;
import com.jaymoskingdoms.Speech;
import com.jaymoskingdoms.VillagerHeritage;
import com.jaymoskingdoms.Classes;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.478) The Cinder Forge and its master, <b>Ignis Vael</b> (Andy's Gemini handoff, pass 2): the oldest Emberborn smith,
 * rumoured all over the Overworld, keeps one forge somewhere in the Nether.
 * <ul>
 * <li><b>Where:</b> one per world, a few hundred blocks from the Nether's heart ({@link #SITE_MIN}-{@link #SITE_MAX}),
 * fixed by the world's seed. Built ({@link FortBuildings#cinderForge}) the first time a player comes within
 * {@link #BUILD_RANGE}, and Ignis with it: an MCA villager of Emberborn blood (fire never hurts him), attuned at the
 * height of power, who can't be hurt at all. If he's ever gone, he's back at his anvil a day later.</li>
 * <li><b>His wares</b> (MCA's Interact page, the Wares button, or a right-click without MCA): the Writ of Rebirth
 * ({@link Classes#WRIT}, which unmakes your heritage, class and story alive and asks them again; Andy: "rare, from the
 * Nether forge"), one a week, besides the court wizards' and rare chests'; and he reforges iron and mail in fire, each
 * piece coming back Cinder-forged (fire protection, unbreaking) for blaze rods.</li>
 * <li><b>Finding him:</b> smiths in the Overworld (armourers, weaponsmiths, toolsmiths) now and then pass on the rumour as
 * you talk to them, in a speech bubble, never in chat ({@link #RUMOURS}); one who has told you sells a Charred
 * Wayfinder for {@link #WAYFINDER_PRICE} emeralds' worth in coin (0.7.498; sneak and use them with coins or a purse in hand). The wayfinder is a
 * compass: in the Overworld it points where a portal should go (the forge's spot times eight), in the Nether at the
 * forge itself. The first time you set foot in the Nether, your next Tidings carries word of it too.</li>
 * </ul>
 * Ops: {@code /kingdom cinderforge info | build | wayfinder}.
 */
public final class CinderForge {
	// ---- tuning ----
	/** How far from the Nether's heart the forge stands (blocks, each way). */
	static final int SITE_MIN = 300, SITE_MAX = 700;
	/** A player this near the spot (blocks, flat) has it built. */
	static final double BUILD_RANGE = 96.0;
	/** Ticks between his restocks (a week), and before he comes back if he's gone (a day). */
	static final long RESTOCK_TICKS = 24000L * 7L, RETURN_TICKS = 24000L;
	/** The Writ: emeralds (set in coin when the trade opens, (0.7.498): {@link com.jaymoskingdoms.Currencies#localize}) and netherite scrap. */
	static final int WRIT_EMERALDS = 40, WRIT_SCRAP = 2;
	/** Reforging a piece: blaze rods, and what it comes back with. */
	static final int REFORGE_RODS = 4, REFORGE_FIRE_PROTECTION = 3, REFORGE_UNBREAKING = 2, REFORGE_USES = 2;
	/** Rumours: the chance a smith passes one on as you talk, and the least time between two for one player (ticks). */
	static final float RUMOUR_CHANCE = 0.35F;
	static final long RUMOUR_COOLDOWN = 6000L;
	static final int RUMOURS = 4;
	/** A smith who's told you sells a wayfinder for this. */
	static final int WAYFINDER_PRICE = 12;
	/** Home radius round his anvil. */
	static final int HOME_RADIUS = 6;

	static final String HEARD_TAG = "jaymoskingdoms_heard_cinder_forge";
	static final String TIDINGS_TAG = "jaymoskingdoms_cinder_forge_tidings";
	static final String WAYFINDER_KEY = "jaymoskingdoms_wayfinder";

	/** The forge: its spot (y known once built), whether it's built, when Ignis was last seen there. */
	public record Forge(BlockPos site, boolean built, long lastSeen) {
		public static final Codec<Forge> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("site").forGetter(Forge::site),
				Codec.BOOL.fieldOf("built").forGetter(Forge::built),
				Codec.LONG.fieldOf("last_seen").forGetter(Forge::lastSeen))
			.apply(i, Forge::new));
	}

	/** On the Nether: the forge. */
	public static final AttachmentType<Forge> FORGE = AttachmentRegistry.create(JaymosKingdoms.id("cinder_forge"),
		builder -> builder.persistent(Forge.CODEC));
	/** On Ignis: when he last restocked (game time). Saved. */
	public static final AttachmentType<Long> IGNIS = AttachmentRegistry.create(JaymosKingdoms.id("ignis_vael"),
		builder -> builder.persistent(Codec.LONG));

	private static final Map<UUID, Long> LAST_RUMOUR = new HashMap<>();

	private CinderForge() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CinderForge::tick);
		// nothing hurts Ignis (but what hurts even the invulnerable: the void, /kill)
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !ignis(entity)
			|| source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !(player instanceof ServerPlayer sp) || !(entity instanceof Villager v)) {
				return InteractionResult.PASS;
			}
			if (ignis(v)) {
				return useIgnis(sp, v);
			}
			if (smith(v) && !Stacked.in(v, Level.NETHER)) {
				return useSmith(sp, v);
			}
			return InteractionResult.PASS;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("cinderforge").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("info").executes(CinderForge::info))
				.then(Commands.literal("build").executes(CinderForge::buildHere))
				.then(Commands.literal("wayfinder").executes(CinderForge::giveWayfinder)))));
	}

	// ---- who's who -------------------------------------------------------------------------------------------------

	/** Ignis Vael himself. */
	public static boolean ignis(Entity entity) {
		return entity.hasAttached(IGNIS);
	}

	/** An Overworld smith, who hears what miners and traders say: an armourer, weaponsmith or toolsmith. */
	static boolean smith(Villager v) {
		Holder<VillagerProfession> job = v.getVillagerData().profession();
		return job.is(VillagerProfession.ARMORER) || job.is(VillagerProfession.WEAPONSMITH) || job.is(VillagerProfession.TOOLSMITH);
	}

	/** The forge (decided the first time it's asked for, by the world's seed). */
	public static Forge forge(ServerLevel nether) {
		Forge forge = nether.getAttached(FORGE);
		if (forge == null) {
			RandomSource random = RandomSource.create(nether.getSeed() ^ 0x1C1DE4F0E5L);
			double angle = random.nextDouble() * Math.PI * 2.0;
			int dist = SITE_MIN + random.nextInt(SITE_MAX - SITE_MIN);
			forge = new Forge(new BlockPos((int)(Math.cos(angle) * dist), 64, (int)(Math.sin(angle) * dist)), false, 0L);
			nether.setAttached(FORGE, forge);
		}
		return forge;
	}

	// ---- each tick -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0) {
			return;
		}
		ServerLevel nether = Stacked.level(server, Level.NETHER);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (nether != null) {
				wayfinders(player, nether);
			}
			if (nether != null && Stacked.in(player, Level.NETHER) && !player.entityTags().contains(TIDINGS_TAG)) {
				// the first time in the Nether: word of the forge ((0.7.494) over the hotbar, not in the Tidings)
				player.addTag(TIDINGS_TAG);
				Post.aside(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.cinder_forge"));
			}
		}
		if (nether == null || server.getTickCount() % 100 != 0) {
			return;
		}
		Forge forge = forge(nether);
		for (ServerPlayer player : Stacked.players(nether.getServer(), Level.NETHER)) {
			if (player.isSpectator()) {
				continue;
			}
			double dx = player.getX() - forge.site().getX(), dz = player.getZ() - forge.site().getZ();
			if (dx * dx + dz * dz > BUILD_RANGE * BUILD_RANGE) {
				continue;
			}
			if (!forge.built()) {
				build(nether, forge, player.getBlockY());
			} else {
				watch(nether, forge);
			}
			return;
		}
	}

	/** Builds the forge near its spot and puts Ignis at his anvil; false if there's no fit ground yet. */
	static boolean build(ServerLevel nether, Forge forge, int nearY) {
		BlockPos site = NetherForts.nearSite(nether, forge.site().getX(), forge.site().getZ(), nearY, FortBuildings.FORGE_RADIUS, true, false);
		if (site == null) {
			return false;
		}
		RandomSource random = nether.getRandom();
		BlockPos anvil = FortBuildings.cinderForge(nether, site, Direction.from2DDataValue(random.nextInt(4)), random);
		Forge built = new Forge(site, true, nether.getGameTime());
		nether.setAttached(FORGE, built);
		spawnIgnis(nether, built, anvil);
		JaymosKingdoms.LOGGER.info("The Cinder Forge was built at {}", site);
		return true;
	}

	/** Near the built forge: Ignis seen (or back a day after he was last seen). */
	private static void watch(ServerLevel nether, Forge forge) {
		if (!nether.isLoaded(forge.site())) {
			return;
		}
		// (a chunk's blocks load before its entities: until they're in, Ignis would look gone and a second be made)
		int cx = forge.site().getX() >> 4, cz = forge.site().getZ() >> 4;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (!nether.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(cx + dx, cz + dz))) {
					return;
				}
			}
		}
		List<Villager> there = nether.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(forge.site()).inflate(48.0), CinderForge::ignis);
		long now = nether.getGameTime();
		if (!there.isEmpty()) {
			for (Villager v : there) {
				if (!v.blockPosition().closerThan(forge.site(), HOME_RADIUS * 2) && v.getTarget() == null) {
					v.getNavigation().moveTo(forge.site().getX() + 0.5, forge.site().getY() + 1, forge.site().getZ() + 0.5, 0.7);
				}
			}
			nether.setAttached(FORGE, new Forge(forge.site(), true, now));
		} else if (now - forge.lastSeen() >= RETURN_TICKS) {
			spawnIgnis(nether, forge, forge.site().above());
			nether.setAttached(FORGE, new Forge(forge.site(), true, now));
		}
	}

	/** Ignis Vael at {@code at}: an MCA villager (a plain one without MCA) of Emberborn blood, attuned, unhurt by anything. */
	static @Nullable Villager spawnIgnis(ServerLevel nether, Forge forge, BlockPos at) {
		Villager v = McaCompat.spawnGuard(nether, Vec3.atBottomCenterOf(at));
		if (v == null) {
			v = new Villager(net.minecraft.world.entity.EntityTypes.VILLAGER, nether);
			v.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			nether.addFreshEntity(v);
		}
		VillagerHeritage.born(v, Heritage.Kind.EMBERBORN, null);
		McaCompat.rename(v, "Ignis Vael");
		v.setAttached(IGNIS, 0L);
		v.setAttached(Mana.ATTUNED, true);
		v.setAttached(Mana.POWER, Mana.MAX_POWER);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.MACE));
		v.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		v.setHomeTo(forge.site(), HOME_RADIUS);
		v.setPersistenceRequired();
		nether.sendParticles(ParticleTypes.FLAME, v.getX(), v.getY() + 1.0, v.getZ(), 40, 0.3, 0.8, 0.3, 0.05);
		return v;
	}

	// ---- Ignis -----------------------------------------------------------------------------------------------------

	/** Talking to Ignis: a word from him; without MCA his wares open at once (with it, they're on the Interact page). */
	private static InteractionResult useIgnis(ServerPlayer player, Villager v) {
		ServerLevel level = (ServerLevel)v.level();
		Speech.tell(level, v, player, Component.translatable(Heritage.is(player, Heritage.Kind.EMBERBORN)
			? "speech.jaymoskingdoms.ignis.sibling" : "speech.jaymoskingdoms.ignis.stranger"), Speech.GOLD);
		if (!McaCompat.LOADED) {
			trade(player, v);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Opens Ignis's wares (restocked once a week). */
	public static void trade(ServerPlayer player, Villager v) {
		if (v.getTradingPlayer() != null) {
			return;
		}
		ServerLevel level = (ServerLevel)v.level();
		long now = level.getGameTime();
		Long stocked = v.getAttached(IGNIS);
		if (stocked == null || stocked == 0L || now - stocked >= RESTOCK_TICKS || v.getOffers().isEmpty()) {
			stock(level, v);
			v.setAttached(IGNIS, Math.max(1L, now));
		}
		v.setTradingPlayer(player);
		v.openTradingScreen(player, v.getDisplayName(), 1);
	}

	/** His wares: the Writ, and the reforging of iron and mail. */
	private static void stock(ServerLevel level, Villager v) {
		MerchantOffers offers = v.getOffers();
		offers.clear();
		if (Classes.WRIT != null) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, WRIT_EMERALDS), Optional.of(new ItemCost(Items.NETHERITE_SCRAP, WRIT_SCRAP)),
				new ItemStack(Classes.WRIT), 1, 0, 0.0F));
		}
		for (Item piece : new Item[] {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
				Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS}) {
			offers.add(new MerchantOffer(new ItemCost(piece), Optional.of(new ItemCost(Items.BLAZE_ROD, REFORGE_RODS)),
				cinderForged(level, piece), REFORGE_USES, 0, 0.0F));
		}
	}

	/** A piece as it comes off his anvil: fire-proofed, harder-wearing, and named for the forge. */
	static ItemStack cinderForged(ServerLevel level, Item piece) {
		ItemStack stack = new ItemStack(piece);
		var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> fire = enchantments.getOrThrow(Enchantments.FIRE_PROTECTION);
		Holder<Enchantment> unbreaking = enchantments.getOrThrow(Enchantments.UNBREAKING);
		stack.enchant(fire, REFORGE_FIRE_PROTECTION);
		stack.enchant(unbreaking, REFORGE_UNBREAKING);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.cinder_forged", stack.getHoverName())
			.withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
		return stack;
	}

	// ---- the Overworld's smiths ----------------------------------------------------------------------------------------

	/** Talking to a smith: a rumour now and then; sneaking with coins (or a purse) once they've told you, a wayfinder. */
	private static InteractionResult useSmith(ServerPlayer player, Villager v) {
		ServerLevel level = (ServerLevel)v.level();
		ItemStack held = player.getMainHandItem();
		if (player.isShiftKeyDown() && Coinage.held(held) > 0L && player.entityTags().contains(HEARD_TAG)) {
			if (!Money.charge(player, Money.worth(WAYFINDER_PRICE))) {
				Speech.tell(level, v, player, Component.translatable("speech.jaymoskingdoms.wayfinder.price", Money.text(player, Money.worth(WAYFINDER_PRICE))), Speech.WHITE);
				return InteractionResult.SUCCESS;
			}
			ServerLevel nether = Stacked.level(level.getServer(), Level.NETHER);
			if (nether != null) {
				player.getInventory().placeItemBackInInventory(wayfinder(nether, Stacked.realm(player)), net.minecraft.util.Prediction.SERVER_ONLY);
			}
			Speech.tell(level, v, player, Component.translatable("speech.jaymoskingdoms.wayfinder.sold"), Speech.GOLD);
			return InteractionResult.SUCCESS;
		}
		long now = level.getGameTime();
		Long last = LAST_RUMOUR.get(player.getUUID());
		// (a time from another world's clock, ahead of this one, counts as long ago)
		if (last != null && now >= last && now - last < RUMOUR_COOLDOWN || level.getRandom().nextFloat() >= RUMOUR_CHANCE) {
			return InteractionResult.PASS;
		}
		LAST_RUMOUR.put(player.getUUID(), now);
		ServerLevel nether = Stacked.level(level.getServer(), Level.NETHER);
		int which = level.getRandom().nextInt(RUMOURS);
		Component line = which == 0 && nether != null
			? Component.translatable("rumour.jaymoskingdoms.cinder_forge.0", Component.translatable("direction.jaymoskingdoms." + compass(forge(nether).site())))
			: Component.translatable("rumour.jaymoskingdoms.cinder_forge." + which);
		Speech.tell(level, v, player, line, Speech.YELLOW);
		if (!player.entityTags().contains(HEARD_TAG)) {
			player.addTag(HEARD_TAG);
		}
		return InteractionResult.PASS;
	}

	/** The way from the Nether's heart to {@code pos}, as a compass point ("northeast"...). */
	static String compass(BlockPos pos) {
		// (the same points as the Underworld's directions: east is +x, south +z)
		String[] points = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
		double angle = Math.toDegrees(Math.atan2(pos.getZ(), pos.getX()));
		return points[Math.floorMod((int)Math.round(angle / 45.0), 8)];
	}

	// ---- the wayfinder -----------------------------------------------------------------------------------------------

	/** A Charred Wayfinder, set for {@code dimension}: a compass marked as ours. */
	public static ItemStack wayfinder(ServerLevel nether, net.minecraft.resources.ResourceKey<Level> dimension) {
		ItemStack stack = new ItemStack(Items.COMPASS);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.wayfinder").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.wayfinder.lore").withStyle(ChatFormatting.GRAY))));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(WAYFINDER_KEY, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		point(stack, nether, dimension);
		return stack;
	}

	static boolean isWayfinder(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return stack.is(Items.COMPASS) && data != null && data.copyTag().getBooleanOr(WAYFINDER_KEY, false);
	}

	/**
	 * Points {@code stack} at the forge in the Nether, or at the forge's spot times eight anywhere else. (0.7.528) In a One
	 * Dimension world the Nether is a band under the Overworld ({@link Stacked}), so the forge is straight below its own spot:
	 * the needle points there, in the one level both share.
	 */
	static void point(ItemStack stack, ServerLevel nether, net.minecraft.resources.ResourceKey<Level> dimension) {
		BlockPos site = forge(nether).site();
		boolean stacked = nether.dimension() != Level.NETHER;
		GlobalPos target = dimension == Level.NETHER ? GlobalPos.of(nether.dimension(), site)
			: stacked ? GlobalPos.of(Level.OVERWORLD, new BlockPos(site.getX(), 64, site.getZ()))
			: GlobalPos.of(Level.OVERWORLD, new BlockPos(site.getX() * 8, 64, site.getZ() * 8));
		LodestoneTracker now = stack.get(DataComponents.LODESTONE_TRACKER);
		if (now == null || !now.target().equals(Optional.of(target))) {
			stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(target), false));
		}
	}

	/** Every wayfinder {@code player} carries, pointed for where they are. */
	private static void wayfinders(ServerPlayer player, ServerLevel nether) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && isWayfinder(stack)) {
				point(stack, nether, Stacked.realm(player));
			}
		}
	}

	// ---- commands (operators; debug: plain text) -------------------------------------------------------------------

	private static int info(CommandContext<CommandSourceStack> c) {
		ServerLevel nether = Stacked.level(c.getSource().getServer(), Level.NETHER);
		if (nether == null) {
			return 0;
		}
		Forge forge = forge(nether);
		c.getSource().sendSuccess(() -> Component.literal("Cinder Forge at " + forge.site().toShortString() + (forge.built() ? ", built" : ", not built yet")
			+ "; Overworld spot " + forge.site().getX() * 8 + " " + forge.site().getZ() * 8), false);
		return 1;
	}

	private static int buildHere(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		if (!Stacked.at(level, BlockPos.containing(c.getSource().getPosition()), Level.NETHER)) {
			c.getSource().sendFailure(Component.literal("The Cinder Forge stands in the Nether."));
			return 0;
		}
		BlockPos at = BlockPos.containing(c.getSource().getPosition());
		Forge here = new Forge(at, false, 0L);
		if (!build(level, here, at.getY())) {
			c.getSource().sendFailure(Component.literal("No fit ground near here for the Cinder Forge."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Cinder Forge built at " + forge(level).site().toShortString()), true);
		return 1;
	}

	private static int giveWayfinder(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel nether = Stacked.level(c.getSource().getServer(), Level.NETHER);
		if (nether == null) {
			return 0;
		}
		player.getInventory().add(wayfinder(nether, player.level().dimension()));
		return 1;
	}
}
