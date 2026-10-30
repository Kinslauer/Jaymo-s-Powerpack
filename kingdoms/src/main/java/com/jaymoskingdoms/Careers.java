package com.jaymoskingdoms;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.220) Player jobs. Andy: "lets draw something up" (the life story's village start gave a job that was only a word),
 * then, on the design: wages from the town's storehouse, "you can have as many as you can keep up", Hunter, Courier
 * and Watch "are great", stronger perks ("you should have a reason to pursue a job"), a Master's apprentice and stall
 * "both", and the title "overhead after introduction, and in the tab list and char screen".
 * <p>
 * A job is paid work for one settlement (its employer). Ask for it in MCA's menu ("Talk about work"), pull a Help
 * wanted notice off a bounty board ({@link TownNotices}), or start with one (the life story's village start). Each
 * morning a job's employer has a few <b>orders</b> drawn from what the town is short of: goods brought to its
 * storehouse (handed over in the same talk), or work done in the town (trees felled, crops harvested, blocks placed,
 * ore mined, ingots smelted, monsters slain, a night on watch, a parcel carried to another town). At dusk the day's
 * finished orders are paid out of the storehouse's emeralds (a poor town pays at least {@link #WAGE_FLOOR} an order),
 * and each order earns job experience and a little reputation. Experience makes an Apprentice a Journeyman and then a
 * Master; each rank opens the job's perk ({@link Job}). A Master may take a villager as their apprentice (goods and a
 * cut every day) and open a stall of their own (a chest whose goods villagers buy). Your rank in every job is kept if
 * you quit, and a job pays a little less for each other job held at once ({@link #wage}).
 * <p>
 * Hurting your employer's people, stealing from them, or their reputation of you falling below zero gets you fired,
 * and they won't take you back for {@link #BARRED_DAYS} days.
 */
public final class Careers {
	// ---- tuning ----
	/** Experience for each rank: Journeyman, Master. */
	public static final int JOURNEYMAN_XP = 100, MASTER_XP = 350;
	/** Experience and reputation an order earns. */
	static final int ORDER_XP = 10, ORDER_REPUTATION = 3;
	/** Emeralds an order pays by rank (Apprentice, Journeyman, Master), and the least a poor town pays one. */
	static final int[] WAGES = {2, 3, 5};
	static final int WAGE_FLOOR = 1;
	/** Each other job held at once takes this off every order's wage (never below the floor). */
	static final int WAGE_SPREAD = 1;
	/** (0.7.250) What a job's own building in its town ({@link Job#hall}) adds to every order's wage. */
	static final int HALL_BONUS = 1;
	/** New orders each morning per job, and the most that wait. */
	static final int ORDERS_A_DAY = 2, ORDERS_MOST = 5;
	/** How far from a settlement's seat its town reaches for on-site work (blocks, flat), and how near its storehouse goods are handed over. */
	static final double TOWN_RANGE = 96.0;
	/** How near a talker must stay (blocks). */
	static final double TALK_RANGE = 8.0;
	/** Days a town won't take you back after firing you. */
	static final int BARRED_DAYS = 3;
	/** The least reputation a town hires at (Andy's doc: "won't take you on if it dislikes you"). */
	static final int HIRE_REPUTATION = 0;
	/** A town's job offers: goods under this in its storehouse make it short of hands. */
	static final int SHORT_BELOW = 64;
	/** The most jobs a talk offers at once. */
	static final int OFFERS_MOST = 6;
	/** How often (ticks) each player's work is looked over. */
	static final int CHECK_TICKS = 20, SLOW_TICKS = 100;
	/** Time of day wages are paid from. */
	static final long DUSK = 12000L;
	/** A life story's village job waits this long (ticks) to find its village. */
	static final long PENDING_TICKS = 24000L * 3;

	/** A courier's parcels go to towns this far off (blocks), and count as this many orders; arrived within this of the seat. */
	static final double CARRY_MIN = 120.0, CARRY_MAX = 1600.0, CARRY_AT = 32.0;
	static final int CARRY_ORDERS = 2;
	/** The watch: checks of {@link #SLOW_TICKS} spent in town by night that make a night's watch. */
	static final int WATCH_CHECKS = 50;

	// perks
	/** Butcher: extra cuts per kill (Journeyman, Master); a Master's cooked meat fills this much more (food, saturation). */
	static final int[] EXTRA_CUTS = {0, 1, 2};
	static final int COOKED_FOOD = 3;
	static final float COOKED_SATURATION = 0.6F;
	/** Woodcutter: a Journeyman's chop counts as this many. A Master's first chop fells the tree. */
	static final int JOURNEYMAN_CHOP = 3;
	/** Farmhand: crops this near grow (random ticks per check); a Master's harvest gives twice this often. */
	static final int GROW_RANGE = 6, GROW_TICKS = 6;
	static final float DOUBLE_HARVEST = 0.5F;
	/** Miner: ore glints this near (every slow check); a Master's ore drops twice this often. */
	static final int GLINT_RANGE = 8;
	static final float DOUBLE_ORE = 0.35F;
	/** Fisher: lure and luck (as the enchantments' levels) by rank. */
	static final int[] LURE = {0, 2, 3}, LUCK = {0, 1, 3};
	/** Smith: a Journeyman's anvil costs this share; a Master's ingots come with one more this often. */
	static final float REPAIR_COST = 0.5F, BONUS_INGOT = 0.25F;
	/** Hunter: tracks seen this far; a Master's blows on beasts. */
	static final double TRACK_RANGE = 40.0;
	static final float BEAST_DAMAGE = 1.5F;
	/** Courier: the road's pace times this (Journeyman, Master); a Master passes freely unless hated (at or below this). */
	static final double[] ROAD_PACE = {1.0, 2.0, 2.5};
	static final int FREE_PASS_FLOOR = -500;
	/** Watch: guards this near come to your aid; a Master calls this many to follow. */
	static final double AID_RANGE = 32.0;
	static final int FOLLOWERS = 3;
	/** Master: an apprentice's daily goods (a stack of the job's first good, this many) and cut (emeralds). */
	static final int APPRENTICE_GOODS = 16, APPRENTICE_CUT = 2;
	/** Master: a stall sells at most this many emeralds' worth a day, from noon. */
	static final int STALL_EMERALDS = 12;
	static final long STALL_FROM = 6000L;

	// (0.7.369, Andy's SevTech picks: the Pilot and Prospector careers)
	/** Pilot: a fare in emeralds (Apprentice, Journeyman, Master) for each trip flown with a passenger or a parcel aboard; trips an order asks for (at most). */
	static final int[] PILOT_FARE = {0, 1, 3};
	static final int FLY_ORDERS_MOST = 2;
	/** Pilot: a ferried parcel must come down within this of the yard it is for (blocks); a ferry order counts as this many orders. */
	static final double FERRY_AT = LandingYard.RANGE;
	static final int FERRY_ORDERS = 3;
	/** Prospector: a bonus in emeralds (Apprentice, Journeyman, Master) for each first find. */
	static final int[] FIND_BONUS = {0, 2, 5};

	public static final int APPRENTICE = 0, JOURNEYMAN = 1, MASTER = 2;

	/** What a smith's furnace work counts. */
	static final TagKey<Item> INGOTS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots"));

	/** "Talk about work" (added to MCA's {@code main} menu by our dialogue data). */
	public static final String ANSWER = "jaymoskingdoms_work";

	/** The jobs. {@code goods}: what its orders ask brought to the storehouse (an item id, or {@code #tag}); {@code works}: on-site work. */
	public enum Job {
		BUTCHER("butcher", 4, List.of("minecraft:beef", "minecraft:porkchop", "minecraft:mutton", "minecraft:chicken", "minecraft:cooked_beef",
			"minecraft:cooked_porkchop", "minecraft:leather"), List.of(), "butcher"),
		WOODCUTTER("woodcutter", 16, List.of("#minecraft:logs", "#minecraft:planks"), List.of("fell"), null),
		BUILDER("builder", 32, List.of("minecraft:cobblestone", "#minecraft:planks", "minecraft:stone_bricks"), List.of("place"), null),
		FARMER("farmer", 12, List.of("minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot"), List.of("harvest"), null),
		MINER("miner", 6, List.of("minecraft:coal", "minecraft:raw_iron", "minecraft:raw_copper", "minecraft:cobblestone"), List.of("ore"), null),
		FISHER("fisher", 4, List.of("minecraft:cod", "minecraft:salmon", "minecraft:cooked_cod"), List.of(), "fishery"),
		SMITH("smith", 2, List.of("minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:gold_ingot"), List.of("smelt", "forge"), "smithy"),
		HUNTER("hunter", 6, List.of("minecraft:leather", "minecraft:feather", "minecraft:rabbit_hide", "minecraft:string"), List.of("slay"), "hunting_lodge"),
		COURIER("courier", 0, List.of(), List.of("carry"), "stable"),
		WATCH("watch", 0, List.of(), List.of("night", "slay"), null),
		/** (0.7.369) Flies pods out of a kingdom's landing yard: paid per trip with a passenger or a parcel aboard. */
		PILOT("pilot", 0, List.of(), List.of("fly", "ferry"), LandingYard.ENTRY),
		/** (0.7.369) Works the far worlds for the yard's town: paid per first find (an ore's first reveal on a world, a first metal knapped). */
		PROSPECTOR("prospector", 0, List.of(), List.of("find"), LandingYard.ENTRY);

		public final String key;
		/** How many of its goods a stall sells for an emerald (0: it has nothing to sell). */
		final int perEmerald;
		final List<String> goods, works;
		/**
		 * (0.7.250) The building it works out of ({@link Buildings}' entry: the butcher's shop, the fishery, the hunting
		 * lodge, the smithy, the stable), or null. A town where it stands is always hiring for the job, and pays
		 * {@link #HALL_BONUS} more an order.
		 */
		final @Nullable String hall;

		Job(String key, int perEmerald, List<String> goods, List<String> works, @Nullable String hall) {
			this.key = key;
			this.perEmerald = perEmerald;
			this.goods = goods;
			this.works = works;
			this.hall = hall;
		}

		public static @Nullable Job of(String key) {
			for (Job j : values()) {
				if (j.key.equals(key)) {
					return j;
				}
			}
			return null;
		}

		public Component title() {
			return Component.translatable("job.jaymoskingdoms." + this.key);
		}

		/** (0.7.369) A job only a town with a landing yard offers. */
		boolean yardOnly() {
			return this == PILOT || this == PROSPECTOR;
		}
	}

	/** One order: {@code what} is {@code give:<item id or #tag>}, a work ({@code fell}, {@code harvest}...), or {@code carry:<faction id>}. */
	public record Order(String what, int count, int progress) {
		public static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("what").forGetter(Order::what),
				Codec.INT.fieldOf("count").forGetter(Order::count),
				Codec.INT.optionalFieldOf("progress", 0).forGetter(Order::progress))
			.apply(i, Order::new));

		public boolean done() {
			return this.progress >= this.count;
		}

		public boolean give() {
			return this.what.startsWith("give:");
		}

		Order plus(int n) {
			return new Order(this.what, this.count, Math.min(this.count, this.progress + n));
		}
	}

	/** One job held: at which settlement (and its name, for the screen), its orders, the day they were last given, the orders finished and not paid, a Master's apprentice. */
	public record Post(String job, int faction, String town, List<Order> orders, long day, int done, Optional<UUID> apprentice, String apprenticeName) {
		public static final Codec<Post> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("job").forGetter(Post::job),
				Codec.INT.fieldOf("faction").forGetter(Post::faction),
				Codec.STRING.optionalFieldOf("town", "").forGetter(Post::town),
				Order.CODEC.listOf().optionalFieldOf("orders", List.of()).forGetter(Post::orders),
				Codec.LONG.optionalFieldOf("day", -1L).forGetter(Post::day),
				Codec.INT.optionalFieldOf("done", 0).forGetter(Post::done),
				UUIDUtil.CODEC.optionalFieldOf("apprentice").forGetter(Post::apprentice),
				Codec.STRING.optionalFieldOf("apprentice_name", "").forGetter(Post::apprenticeName))
			.apply(i, Post::new));

		Post withOrders(List<Order> orders) {
			return new Post(job, faction, town, List.copyOf(orders), day, done, apprentice, apprenticeName);
		}

		Post withDay(long day) {
			return new Post(job, faction, town, orders, day, done, apprentice, apprenticeName);
		}

		Post withDone(int done) {
			return new Post(job, faction, town, orders, day, done, apprentice, apprenticeName);
		}

		Post withApprentice(Optional<UUID> who, String name) {
			return new Post(job, faction, town, orders, day, done, who, name);
		}

		public @Nullable Job kind() {
			return Job.of(this.job);
		}
	}

	/**
	 * A player's working life: experience in every job ever held, the jobs held now, the towns that won't have them back
	 * (faction id → game time), the jobs whose tools were given, a life story's job still to be taken up (and until when),
	 * a Master's stall (and its town, and the day it last sold), and the day wages were last paid.
	 */
	public record Career(Map<String, Integer> xp, List<Post> posts, Map<String, Long> barred, List<String> kits, String pending, long pendingUntil,
		Optional<BlockPos> stall, int stallFaction, long soldDay, long paidDay) {
		public static final Career EMPTY = new Career(Map.of(), List.of(), Map.of(), List.of(), "", 0L, Optional.empty(), -1, -1L, -1L);

		public static final Codec<Career> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("xp", Map.of()).forGetter(Career::xp),
				Post.CODEC.listOf().optionalFieldOf("posts", List.of()).forGetter(Career::posts),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("barred", Map.of()).forGetter(Career::barred),
				Codec.STRING.listOf().optionalFieldOf("kits", List.of()).forGetter(Career::kits),
				Codec.STRING.optionalFieldOf("pending", "").forGetter(Career::pending),
				Codec.LONG.optionalFieldOf("pending_until", 0L).forGetter(Career::pendingUntil),
				BlockPos.CODEC.optionalFieldOf("stall").forGetter(Career::stall),
				Codec.INT.optionalFieldOf("stall_faction", -1).forGetter(Career::stallFaction),
				Codec.LONG.optionalFieldOf("sold_day", -1L).forGetter(Career::soldDay),
				Codec.LONG.optionalFieldOf("paid_day", -1L).forGetter(Career::paidDay))
			.apply(i, Career::new));

		public int xpIn(String job) {
			return this.xp.getOrDefault(job, 0);
		}

		public int rank(String job) {
			return rankFor(xpIn(job));
		}

		public @Nullable Post post(String job) {
			for (Post p : this.posts) {
				if (p.job.equals(job)) {
					return p;
				}
			}
			return null;
		}

		Career withPosts(List<Post> posts) {
			return new Career(xp, List.copyOf(posts), barred, kits, pending, pendingUntil, stall, stallFaction, soldDay, paidDay);
		}

		Career withPost(Post post) {
			List<Post> next = new ArrayList<>();
			boolean put = false;
			for (Post p : this.posts) {
				if (p.job.equals(post.job)) {
					next.add(post);
					put = true;
				} else {
					next.add(p);
				}
			}
			if (!put) {
				next.add(post);
			}
			return withPosts(next);
		}

		Career without(String job) {
			List<Post> next = new ArrayList<>(this.posts);
			next.removeIf(p -> p.job.equals(job));
			return withPosts(next);
		}

		Career withXp(String job, int amount) {
			Map<String, Integer> next = new HashMap<>(this.xp);
			next.put(job, amount);
			return new Career(Map.copyOf(next), posts, barred, kits, pending, pendingUntil, stall, stallFaction, soldDay, paidDay);
		}

		Career withBarred(int faction, long until) {
			Map<String, Long> next = new HashMap<>(this.barred);
			next.put(Integer.toString(faction), until);
			return new Career(xp, posts, Map.copyOf(next), kits, pending, pendingUntil, stall, stallFaction, soldDay, paidDay);
		}

		Career withKit(String job) {
			List<String> next = new ArrayList<>(this.kits);
			if (!next.contains(job)) {
				next.add(job);
			}
			return new Career(xp, posts, barred, List.copyOf(next), pending, pendingUntil, stall, stallFaction, soldDay, paidDay);
		}

		Career withPending(String job, long until) {
			return new Career(xp, posts, barred, kits, job, until, stall, stallFaction, soldDay, paidDay);
		}

		Career withStall(Optional<BlockPos> at, int faction) {
			return new Career(xp, posts, barred, kits, pending, pendingUntil, at, faction, soldDay, paidDay);
		}

		Career withSoldDay(long day) {
			return new Career(xp, posts, barred, kits, pending, pendingUntil, stall, stallFaction, day, paidDay);
		}

		Career withPaidDay(long day) {
			return new Career(xp, posts, barred, kits, pending, pendingUntil, stall, stallFaction, soldDay, day);
		}

		/** The best rank held in {@code job} among the jobs held now (-1 if it isn't held). */
		public int heldRank(Job job) {
			return post(job.key) == null ? -1 : rank(job.key);
		}

		/** Whether they're a Master in any job. */
		public boolean master() {
			return this.xp.values().stream().anyMatch(x -> rankFor(x) == MASTER);
		}
	}

	/** On a player: their working life. Saved, kept through death, synced to them alone (the character screen's Jobs tab). */
	public static final AttachmentType<Career> CAREER = AttachmentRegistry.create(JaymosKingdoms.id("career"),
		builder -> builder.persistent(Career.CODEC).copyOnDeath().syncWith(ByteBufCodecs.fromCodec(Career.CODEC), AttachmentSyncPredicate.targetOnly()));

	/** (0.7.369) On a player: the first finds made ("world|kind"), so each counts once. Saved, kept through death. */
	public static final AttachmentType<List<String>> FINDS = AttachmentRegistry.create(JaymosKingdoms.id("first_finds"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	/** Server → everyone: each player's job title ("Master Woodcutter"), for over their head and the tab list (shown only once introduced). */
	public record Titles(Map<UUID, Component> titles) implements CustomPacketPayload {
		public static final Type<Titles> TYPE = new Type<>(JaymosKingdoms.id("job_titles"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Titles> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.titles.size());
				for (var e : p.titles.entrySet()) {
					buf.writeUUID(e.getKey());
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, e.getValue());
				}
			},
			buf -> {
				int n = buf.readVarInt();
				Map<UUID, Component> titles = new HashMap<>();
				for (int i = 0; i < n; i++) {
					UUID id = buf.readUUID();
					titles.put(id, ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
				}
				return new Titles(titles);
			});

		@Override
		public Type<Titles> type() {
			return TYPE;
		}
	}

	/** The titles this client was last sent (client side). */
	public static Map<UUID, Component> shownTitles = Map.of();

	/** A talk's menu: who's talking, and what each option does. */
	private record Menu(int speaker, List<String> actions) {
	}

	private static final Map<UUID, Menu> MENUS = new HashMap<>();
	/** Player → the stall they're about to mark (the faction it's for). */
	private static final Map<UUID, Integer> MARKING = new HashMap<>();
	/** The titles last sent. */
	private static Map<UUID, Component> sent = Map.of();
	/** Faction → the building its Master Builder chose next ({@link Construction}). */
	private static final Map<Integer, String> NEXT_BUILDING = new HashMap<>();
	/** The watch's checks spent in town tonight (player → count), cleared each morning. */
	private static final Map<UUID, Integer> WATCHED = new HashMap<>();
	/** Player → the last good-to-eat stack they were eating (a Master Butcher's cooked meat). */
	private static final Map<UUID, Item> EATING = new HashMap<>();

	private Careers() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("career attachments: {} {}", CAREER, FINDS);
		PayloadTypeRegistry.clientboundPlay().register(Titles.TYPE, Titles.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Careers::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler.player, Titles.TYPE)) {
				ServerPlayNetworking.send(handler.player, new Titles(sent));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			MENUS.remove(handler.player.getUUID());
			MARKING.remove(handler.player.getUUID());
			EATING.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MENUS.clear();
			MARKING.clear();
			NEXT_BUILDING.clear();
			WATCHED.clear();
			EATING.clear();
			sent = Map.of();
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refreshTitles(newPlayer.level().getServer()));
		PlayerBlockBreakEvents.AFTER.register(Careers::broke);
		// hammering on Jaymo's Powers' Smithing Anvil is smith work (a finished forging counts one)
		PowersCompat.onForged((smith, result) -> work(smith.level(), smith, "forge", 1, smith.blockPosition()));
		ServerLivingEntityEvents.AFTER_DEATH.register(Careers::died);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> hurt(entity, source));
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (player instanceof ServerPlayer sp && world instanceof ServerLevel level && MARKING.containsKey(sp.getUUID())) {
				markStall(level, sp, hit.getBlockPos());
			}
			return InteractionResult.PASS;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
			Commands.literal("job").executes(Careers::list)));
	}

	// ---- reading ---------------------------------------------------------------------------------------------------------

	public static Career of(Player player) {
		return player.getAttachedOrElse(CAREER, Career.EMPTY);
	}

	private static void edit(ServerPlayer player, UnaryOperator<Career> change) {
		Career before = of(player);
		Career after = change.apply(before);
		if (!after.equals(before)) {
			player.setAttached(CAREER, after);
		}
	}

	/** Tags Jaymo's Powers reads for a smith's rank (its perfect zone on the hammering bar widens): Journeyman, Master. */
	static final String[] SMITH_TAGS = {"jaymoskingdoms.smith_rank_1", "jaymoskingdoms.smith_rank_2"};

	/** (Powers' Smithing Anvil) Keeps {@code player}'s smith-rank tag true to the rank they hold. */
	private static void smithTags(ServerPlayer player) {
		int rank = heldRank(player, Job.SMITH);
		for (int i = 0; i < SMITH_TAGS.length; i++) {
			boolean want = rank == i + 1;
			if (want != player.entityTags().contains(SMITH_TAGS[i])) {
				if (want) {
					player.addTag(SMITH_TAGS[i]);
				} else {
					player.removeTag(SMITH_TAGS[i]);
				}
			}
		}
	}

	/** The rank {@code xp} makes. */
	public static int rankFor(int xp) {
		return xp >= MASTER_XP ? MASTER : xp >= JOURNEYMAN_XP ? JOURNEYMAN : APPRENTICE;
	}

	/** The experience the next rank needs, or -1 at Master. */
	public static int nextRankXp(int rank) {
		return rank == APPRENTICE ? JOURNEYMAN_XP : rank == JOURNEYMAN ? MASTER_XP : -1;
	}

	/** {@code player}'s rank in {@code job} while they hold it, or -1. */
	static int heldRank(Player player, Job job) {
		return of(player).heldRank(job);
	}

	/** What an order of {@code job} at {@code rank} pays, with {@code held} jobs at once. */
	static int wage(int rank, int held) {
		return Math.max(WAGE_FLOOR, WAGES[Mth.clamp(rank, 0, 2)] - WAGE_SPREAD * Math.max(0, held - 1));
	}

	/** The same in {@code f}, with {@link #HALL_BONUS} when the job's building stands there. */
	static int wage(MinecraftServer server, Faction f, @Nullable Job job, int rank, int held) {
		return wage(rank, held) + (job != null && hasHall(server, f, job) ? HALL_BONUS : 0);
	}

	/** (0.7.250) {@code job}'s own building ({@link Job#hall}) stands finished in {@code f}. */
	static boolean hasHall(MinecraftServer server, Faction f, Job job) {
		if (job.hall == null) {
			return false;
		}
		for (Works.Built b : Works.of(server, f.id()).built()) {
			if (b.entry().equals(job.hall) && b.ruined() == 0L) {
				return true;
			}
		}
		return false;
	}

	/** "Master Woodcutter". */
	public static Component title(Job job, int rank) {
		return Component.translatable("job.jaymoskingdoms.titled", Component.translatable("job.jaymoskingdoms.rank." + rank),
			Component.translatable("job.jaymoskingdoms." + job.key + ".title"));
	}

	/** The title {@code player} goes by: their best-ranked job held now (most experience on a tie), or null for none. */
	static @Nullable Component title(Player player) {
		Career c = of(player);
		Post best = null;
		for (Post p : c.posts()) {
			if (p.kind() != null && (best == null || c.xpIn(p.job()) > c.xpIn(best.job()))) {
				best = p;
			}
		}
		return best == null ? null : title(best.kind(), c.rank(best.job()));
	}

	/** {@code pos} is in {@code f}'s town: near its seat, or on its land (the settlements' world only). */
	static boolean inTown(ServerLevel level, Faction f, BlockPos pos) {
		if (level != Factions.level(level.getServer())) {
			return false;
		}
		double dx = pos.getX() - f.seat().getX(), dz = pos.getZ() - f.seat().getZ();
		return dx * dx + dz * dz <= TOWN_RANGE * TOWN_RANGE || f.claims(ChunkPos.containing(pos));
	}

	private static long day(ServerLevel level) {
		return Quests.day(level);
	}

	// ---- the day ---------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			eating(player);
			if (t % CHECK_TICKS == 0) {
				smithTags(player);
			}
			if (t % CHECK_TICKS != 0 || player.isSpectator()) {
				continue;
			}
			Career c = of(player);
			if (c.posts().isEmpty() && c.pending().isEmpty() && c.stall().isEmpty()) {
				continue;
			}
			ServerLevel level = player.level();
			perks(level, player, c, t);
			if (t % SLOW_TICKS == 0) {
				day(level, player);
			}
		}
		if (t % SLOW_TICKS == 0) {
			refreshTitles(server);
			if (Royals.timeOfDay(Factions.level(server)) < Royals.DAWN) {
				WATCHED.clear();
			}
		}
	}

	/** A player's slow check: a story's job taken up, firings, new orders, the watch, a parcel arrived, wages, an apprentice, a stall. */
	private static void day(ServerLevel level, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		Career c = of(player);
		long now = level.getGameTime(), today = day(level);
		if (!c.pending().isEmpty()) {
			pending(level, player, c, now);
		}
		for (Post p : of(player).posts()) {
			Faction f = Factions.get(server, p.faction());
			if (f == null || f.fallen()) {
				edit(player, k -> k.without(p.job()));
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.job.gone", p.town(), p.kind() == null ? "" : p.kind().title())
					.withStyle(ChatFormatting.GRAY));
				continue;
			}
			if (Reputation.get(player, f.id()) < HIRE_REPUTATION || Theft.wanted(player, f.id())) {
				fire(player, f, "message.jaymoskingdoms.job.fired.reputation");
				continue;
			}
			if (p.day() < today) {
				orders(level, player, f, p, today);
			}
		}
		Faction here = Factions.around(server, level, player.blockPosition(), TOWN_RANGE);
		long time = Royals.timeOfDay(level);
		for (Post p : of(player).posts()) {
			if (p.kind() == Job.WATCH && here != null && here.id() == p.faction() && (time >= DUSK + 1000L || time < Royals.DAWN)) {
				int checks = WATCHED.merge(player.getUUID(), 1, Integer::sum);
				if (checks == WATCH_CHECKS) {
					progress(level, player, p.job(), "night", 1);
				}
			}
			for (Order o : p.orders()) {
				if (o.what().startsWith("carry:") && !o.done()) {
					Faction to = Factions.get(server, parse(o.what().substring(6)));
					if (to != null && level == Factions.level(server) && to.seat().distToCenterSqr(player.position()) < CARRY_AT * CARRY_AT) {
						progress(level, player, p.job(), o.what(), 1);
					}
				}
			}
		}
		if (time >= DUSK && of(player).paidDay() < today) {
			pay(level, player, today);
		}
		stall(level, player, today, time);
	}

	/** A life story's job, taken up at the first town they come to. */
	private static void pending(ServerLevel level, ServerPlayer player, Career c, long now) {
		if (now > c.pendingUntil() || Job.of(c.pending()) == null) {
			edit(player, k -> k.withPending("", 0L));
			return;
		}
		Faction f = Factions.around(level.getServer(), level, player.blockPosition(), Villages.VILLAGE_RADIUS);
		if (f != null && !f.fallen() && f.type() != FactionType.BARBARIAN) {
			Job job = Job.of(c.pending());
			edit(player, k -> k.withPending("", 0L));
			hire(level, player, f, job, null);
		}
	}

	/** A new morning's orders for {@code p} (up to {@link #ORDERS_MOST} waiting). */
	private static void orders(ServerLevel level, ServerPlayer player, Faction f, Post p, long today) {
		List<Order> orders = new ArrayList<>(p.orders());
		orders.removeIf(Order::done);
		Job job = p.kind();
		RandomSource random = level.getRandom();
		for (int i = 0; i < ORDERS_A_DAY && orders.size() < ORDERS_MOST && job != null; i++) {
			Order o = order(level, f, job, orders, random);
			if (o != null) {
				orders.add(o);
			}
		}
		Post next = p.withOrders(orders).withDay(today);
		edit(player, k -> k.withPost(next));
	}

	/** One order of {@code job} for {@code f}, not one already open ({@code open}); null if it has none to give. */
	private static @Nullable Order order(ServerLevel level, Faction f, Job job, List<Order> open, RandomSource random) {
		List<String> kinds = new ArrayList<>();
		for (String g : job.goods) {
			kinds.add("give:" + g);
		}
		kinds.addAll(job.works);
		kinds.removeIf(k -> open.stream().anyMatch(o -> o.what().equals(k) || k.equals("carry") && o.what().startsWith("carry:")
			|| k.equals("ferry") && o.what().startsWith("ferry:")));
		if (job == Job.BUILDER && Storage.loaded(level, f)) {
			// what the next building is short of first
			Map<Item, Integer> missing = Storage.missing(level, f, Planner.needs(level, f));
			for (var e : missing.entrySet()) {
				String what = "give:" + BuiltInRegistries.ITEM.getKey(e.getKey());
				if (open.stream().noneMatch(o -> o.what().equals(what))) {
					return new Order(what, Mth.clamp(e.getValue(), 8, 64), 0);
				}
			}
		}
		if (kinds.isEmpty()) {
			return null;
		}
		String what;
		List<String> gives = kinds.stream().filter(k -> k.startsWith("give:")).toList();
		if (!gives.isEmpty() && Storage.loaded(level, f) && random.nextFloat() < 0.6F) {
			// the goods the storehouse has least of
			what = gives.stream().min(Comparator.comparingInt(g -> stock(level, f, g.substring(5)) + random.nextInt(16))).orElse(gives.getFirst());
		} else {
			what = kinds.get(random.nextInt(kinds.size()));
		}
		if (what.equals("carry")) {
			Faction to = carryTo(level.getServer(), f, random);
			return to == null ? null : new Order("carry:" + to.id(), 1, 0);
		}
		if (what.equals("ferry")) {
			Faction to = ferryTo(level, f, random);
			return to == null ? null : new Order("ferry:" + to.id(), 1, 0);
		}
		return new Order(what, count(job, what, random), 0);
	}

	/** (0.7.369) Another town with a landing yard a pilot's parcel goes to, or null. */
	private static @Nullable Faction ferryTo(ServerLevel level, Faction from, RandomSource random) {
		MinecraftServer server = level.getServer();
		List<Faction> towns = Factions.all(server).stream().filter(o -> o.id() != from.id() && !o.fallen() && o.type() != FactionType.BARBARIAN)
			.filter(o -> LandingYard.has(server, o)).toList();
		return towns.isEmpty() ? null : towns.get(random.nextInt(towns.size()));
	}

	/** How many an order asks for. */
	private static int count(Job job, String what, RandomSource random) {
		return switch (what) {
			case "fell" -> Mth.nextInt(random, 24, 48);
			case "place" -> Mth.nextInt(random, 64, 128);
			case "harvest" -> Mth.nextInt(random, 24, 48);
			case "ore" -> Mth.nextInt(random, 10, 20);
			case "smelt" -> Mth.nextInt(random, 16, 32);
			case "forge" -> Mth.nextInt(random, 3, 6);
			case "slay" -> job == Job.WATCH ? Mth.nextInt(random, 4, 8) : Mth.nextInt(random, 6, 12);
			case "night", "find" -> 1;
			case "fly" -> Mth.nextInt(random, 1, FLY_ORDERS_MOST);
			default -> {
				String good = what.substring(5);
				yield good.startsWith("#") ? Mth.nextInt(random, 24, 48) : job == Job.SMITH ? Mth.nextInt(random, 8, 16)
					: good.equals("minecraft:cobblestone") ? Mth.nextInt(random, 48, 64) : Mth.nextInt(random, 10, 24);
			}
		};
	}

	/** A town a courier's parcel goes to, or null. */
	private static @Nullable Faction carryTo(MinecraftServer server, Faction from, RandomSource random) {
		List<Faction> towns = Factions.all(server).stream().filter(o -> o.id() != from.id() && !o.fallen() && o.type() != FactionType.BARBARIAN)
			.filter(o -> {
				double d = Math.sqrt(o.seat().distSqr(from.seat()));
				return d >= CARRY_MIN && d <= CARRY_MAX;
			}).toList();
		return towns.isEmpty() ? null : towns.get(random.nextInt(towns.size()));
	}

	/** Dusk: the day's finished orders paid (from each town's storehouse when it's loaded; else they wait), with an apprentice's goods and cut. */
	private static void pay(ServerLevel level, ServerPlayer player, long today) {
		MinecraftServer server = level.getServer();
		Career c = of(player);
		int held = c.posts().size();
		boolean waiting = false;
		for (Post p : c.posts()) {
			Faction f = Factions.get(server, p.faction());
			if (f == null) {
				continue;
			}
			int rank = c.rank(p.job());
			boolean apprentice = p.apprentice().isPresent() && rank == MASTER && apprenticeAlive(level, player, p);
			if (p.done() == 0 && !apprentice) {
				continue;
			}
			ServerLevel store = Factions.level(server);
			if (!Storage.loaded(store, f)) {
				waiting = true;
				continue;
			}
			int owed = p.done() * wage(server, f, p.kind(), rank, held) + (apprentice ? APPRENTICE_CUT : 0);
			int fromStore = Storage.take(store, f, Items.EMERALD, owed).getCount();
			int fresh = Math.max(0, Math.min(owed - fromStore, p.done() * WAGE_FLOOR - fromStore));
			int paid = fromStore + fresh;
			if (apprentice && p.kind() != null && !p.kind().goods.isEmpty()) {
				// their apprentice's day: a stack of the trade's goods into the storehouse
				Item good = item(p.kind().goods.getFirst());
				if (good != Items.AIR) {
					Storage.put(store, f, good, APPRENTICE_GOODS);
				}
			}
			if (paid > 0) {
				give(player, new ItemStack(Items.EMERALD, paid));
			}
			player.sendSystemMessage(Component.translatable(fromStore < owed ? "message.jaymoskingdoms.job.paid_short" : "message.jaymoskingdoms.job.paid",
				paid, f.name(), p.kind() == null ? "" : p.kind().title(), p.done()).withStyle(ChatFormatting.GREEN));
			edit(player, k -> {
				Post now = k.post(p.job());
				return now == null ? k : k.withPost(now.withDone(0));
			});
		}
		if (!waiting) {
			edit(player, k -> k.withPaidDay(today));
		}
	}

	/** {@code p}'s apprentice is still about (or out of sight); false (and they're let go) if they've died. */
	private static boolean apprenticeAlive(ServerLevel level, ServerPlayer player, Post p) {
		Entity e = Factions.level(level.getServer()).getEntity(p.apprentice().get());
		if (e != null && !e.isAlive()) {
			edit(player, k -> {
				Post now = k.post(p.job());
				return now == null ? k : k.withPost(now.withApprentice(Optional.empty(), ""));
			});
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.job.apprentice_lost", p.apprenticeName()).withStyle(ChatFormatting.GRAY));
			return false;
		}
		return true;
	}

	/** A Master's stall sells from noon, once a day, while it's loaded: villagers buy its goods (into the town's storehouse) for emeralds left in it. */
	private static void stall(ServerLevel level, ServerPlayer player, long today, long time) {
		Career c = of(player);
		if (c.stall().isEmpty() || c.soldDay() >= today || time < STALL_FROM) {
			return;
		}
		MinecraftServer server = level.getServer();
		ServerLevel store = Factions.level(server);
		BlockPos at = c.stall().get();
		Faction f = Factions.get(server, c.stallFaction());
		if (f == null || f.fallen()) {
			edit(player, k -> k.withStall(Optional.empty(), -1));
			return;
		}
		if (!store.isLoaded(at)) {
			return;
		}
		if (!(store.getBlockEntity(at) instanceof Container box)) {
			edit(player, k -> k.withStall(Optional.empty(), -1));
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.job.stall_gone").withStyle(ChatFormatting.GRAY));
			return;
		}
		int earned = 0;
		for (int slot = 0; slot < box.getContainerSize() && earned < STALL_EMERALDS; slot++) {
			ItemStack stack = box.getItem(slot);
			Job job = stack.isEmpty() ? null : mastered(c, stack);
			if (job == null) {
				continue;
			}
			int lots = Math.min(stack.getCount() / job.perEmerald, STALL_EMERALDS - earned);
			if (lots <= 0) {
				continue;
			}
			ItemStack sold = stack.split(lots * job.perEmerald);
			Storage.put(store, f, sold);
			earned += lots;
		}
		edit(player, k -> k.withSoldDay(today));
		if (earned > 0) {
			ItemStack pay = new ItemStack(Items.EMERALD, earned);
			for (int slot = 0; slot < box.getContainerSize() && !pay.isEmpty(); slot++) {
				ItemStack there = box.getItem(slot);
				if (there.isEmpty()) {
					box.setItem(slot, pay.copy());
					pay = ItemStack.EMPTY;
				} else if (there.is(Items.EMERALD) && there.getCount() < there.getMaxStackSize()) {
					int n = Math.min(pay.getCount(), there.getMaxStackSize() - there.getCount());
					there.grow(n);
					pay.shrink(n);
				}
			}
			if (!pay.isEmpty()) {
				give(player, pay);
			}
			box.setChanged();
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.job.stall_sold", earned, f.name()).withStyle(ChatFormatting.GREEN));
		}
	}

	/** The mastered job {@code stack} is the goods of (one that sells), or null. */
	private static @Nullable Job mastered(Career c, ItemStack stack) {
		for (Job job : Job.values()) {
			if (job.perEmerald > 0 && c.rank(job.key) == MASTER && job.goods.stream().anyMatch(g -> matches(stack, g))) {
				return job;
			}
		}
		return null;
	}

	// ---- work done -------------------------------------------------------------------------------------------------------

	/** {@code n} more of on-site work {@code what} for {@code player}'s {@code job}s (each held job with such an order open). */
	static void work(ServerLevel level, ServerPlayer player, String what, int n, BlockPos at) {
		Career c = of(player);
		if (c.posts().isEmpty()) {
			return;
		}
		MinecraftServer server = level.getServer();
		for (Post p : c.posts()) {
			Faction f = Factions.get(server, p.faction());
			if (f != null && inTown(level, f, at) && p.orders().stream().anyMatch(o -> o.what().equals(what) && !o.done())) {
				progress(level, player, p.job(), what, n);
			}
		}
	}

	/** {@code n} more towards {@code job}'s open {@code what} order; finished, it's counted for pay and earns experience and reputation. */
	private static void progress(ServerLevel level, ServerPlayer player, String job, String what, int n) {
		Career c = of(player);
		Post p = c.post(job);
		if (p == null) {
			return;
		}
		List<Order> orders = new ArrayList<>(p.orders());
		int finished = 0;
		for (int i = 0; i < orders.size() && n > 0; i++) {
			Order o = orders.get(i);
			if (!o.what().equals(what) || o.done()) {
				continue;
			}
			int take = Math.min(n, o.count() - o.progress());
			Order next = o.plus(take);
			n -= take;
			orders.set(i, next);
			if (next.done()) {
				finished += what.startsWith("carry:") ? CARRY_ORDERS : what.startsWith("ferry:") ? FERRY_ORDERS : 1;
			}
		}
		orders.removeIf(Order::done);
		Post next = p.withOrders(orders).withDone(p.done() + finished);
		edit(player, k -> k.withPost(next));
		if (finished > 0) {
			finish(level, player, p, finished);
		}
	}

	/** {@code n} orders finished: experience (and a rank-up), reputation, a word. */
	private static void finish(ServerLevel level, ServerPlayer player, Post p, int n) {
		Job job = p.kind();
		Faction f = Factions.get(level.getServer(), p.faction());
		if (job == null || f == null) {
			return;
		}
		int before = of(player).xpIn(p.job());
		int after = before + ORDER_XP * n;
		edit(player, k -> k.withXp(p.job(), after));
		Reputation.change(player, f, ORDER_REPUTATION * n);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.job.order_done", job.title(), f.name()).withStyle(ChatFormatting.GREEN));
		if (rankFor(after) > rankFor(before)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.job.rank_up", f.name(), title(job, rankFor(after)),
				Component.translatable("job.jaymoskingdoms." + job.key + ".perk." + rankFor(after))).withStyle(ChatFormatting.GOLD));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
				net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.2F);
			refreshTitles(level.getServer());
		}
	}

	/** A block broken: logs felled one at a time, crops harvested (a Master Farmhand's sometimes twice), ore mined (a Master Miner's sometimes twice). */
	private static void broke(net.minecraft.world.level.Level world, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		if (!(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level) || sp.isCreative() || of(sp).posts().isEmpty()) {
			return;
		}
		if (state.is(BlockTags.LOGS)) {
			work(level, sp, "fell", 1, pos);
		} else if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
			work(level, sp, "harvest", 1, pos);
			if (heldRank(sp, Job.FARMER) == MASTER && level.getRandom().nextFloat() < DOUBLE_HARVEST) {
				again(level, sp, pos, state, be);
			}
		} else if (ore(state)) {
			work(level, sp, "ore", 1, pos);
			if (heldRank(sp, Job.MINER) == MASTER && level.getRandom().nextFloat() < DOUBLE_ORE) {
				again(level, sp, pos, state, be);
			}
		}
	}

	/** The block's drops once more. */
	private static void again(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		for (ItemStack drop : Block.getDrops(state, level, pos, be, player, player.getMainHandItem())) {
			Block.popResource(level, pos, drop);
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.0);
	}

	private static boolean ore(BlockState state) {
		return state.is(BlockItemTags.COAL_ORES.block()) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
			|| state.is(BlockItemTags.REDSTONE_ORES.block()) || state.is(BlockItemTags.LAPIS_ORES.block()) || state.is(BlockItemTags.DIAMOND_ORES.block()) || state.is(BlockItemTags.EMERALD_ORES.block());
	}

	/** (from {@link Trees}) A tree felled by {@code player}: its logs count. */
	static void felled(ServerLevel level, ServerPlayer player, BlockPos cut, int logs) {
		work(level, player, "fell", logs, cut);
	}

	/**
	 * (from {@link Trees#chop}) How much one of {@code cutter}'s chops counts: a Journeyman Woodcutter's for
	 * {@link #JOURNEYMAN_CHOP}, a Master's fells the tree at once.
	 */
	static int chop(@Nullable Entity cutter) {
		if (!(cutter instanceof Player p)) {
			return 1;
		}
		int rank = heldRank(p, Job.WOODCUTTER);
		return rank == MASTER ? 10000 : rank == JOURNEYMAN ? JOURNEYMAN_CHOP : 1;
	}

	/**
	 * (from {@code BlockItemMixin}, as a block is set) A block {@code player} placed: building work in town, and a
	 * Journeyman Builder's comes out of the town's storehouse when it has one ({@code stack} given it back).
	 */
	public static void placed(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
		Career c = of(player);
		Post p = c.post(Job.BUILDER.key);
		if (p == null || player.isCreative()) {
			return;
		}
		Faction f = Factions.get(level.getServer(), p.faction());
		if (f == null || !inTown(level, f, pos)) {
			return;
		}
		work(level, player, "place", 1, pos);
		if (c.rank(Job.BUILDER.key) >= JOURNEYMAN && Storage.loaded(level, f) && !Storage.take(level, f, stack.getItem(), 1).isEmpty()) {
			// the town's stock: what's about to be used up is made good
			stack.grow(1);
		}
	}

	/** (from {@code FurnaceResultSlotMixin}) {@code player} took {@code n} of {@code result} from a furnace: smelting work, and a Master Smith's bonus ingots. */
	public static void smelted(ServerPlayer player, ItemStack result, int n) {
		if (n <= 0 || !result.is(INGOTS)) {
			return;
		}
		ServerLevel level = player.level();
		work(level, player, "smelt", n, player.blockPosition());
		if (heldRank(player, Job.SMITH) == MASTER) {
			int bonus = 0;
			for (int i = 0; i < n; i++) {
				if (level.getRandom().nextFloat() < BONUS_INGOT) {
					bonus++;
				}
			}
			if (bonus > 0) {
				give(player, result.copyWithCount(bonus));
			}
		}
	}

	/** (from {@code AnvilMenuMixin}) An anvil's cost for {@code player}: a Journeyman Smith's is cut. */
	public static int anvilCost(Player player, int cost) {
		return heldRank(player, Job.SMITH) >= JOURNEYMAN && cost > 1 ? Math.max(1, Math.round(cost * REPAIR_COST)) : cost;
	}

	/** (from {@code FishingHookMixin}) A Fisher's lure and luck, added to the rod's. */
	public static int lure(Player player) {
		int rank = heldRank(player, Job.FISHER);
		return rank < 0 ? 0 : LURE[rank];
	}

	public static int luck(Player player) {
		int rank = heldRank(player, Job.FISHER);
		return rank < 0 ? 0 : LUCK[rank];
	}

	/** (from {@link RoadPace}) A Courier's road pace, times the road's. */
	static double roadPace(LivingEntity who) {
		int rank = who instanceof Player p ? heldRank(p, Job.COURIER) : -1;
		return rank < 0 ? 1.0 : ROAD_PACE[rank];
	}

	/** A Master Courier passes freely through {@code faction}'s land, unless it hates them or they're wanted there. */
	public static boolean passes(ServerPlayer player, int faction) {
		return heldRank(player, Job.COURIER) == MASTER && Reputation.get(player, faction) > FREE_PASS_FLOOR && !Theft.wanted(player, faction);
	}

	/** (from {@code LivingEntityHurtMixin}) A Master Hunter's blows on beasts (animals and monsters that aren't people). */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (source.getEntity() instanceof ServerPlayer player && beast(target) && heldRank(player, Job.HUNTER) == MASTER) {
			return amount * BEAST_DAMAGE;
		}
		return amount;
	}

	private static boolean beast(LivingEntity e) {
		return (e instanceof Animal || e instanceof Enemy) && !(e instanceof Player) && !(e instanceof Villager) && !(e instanceof Folk)
			&& !(e instanceof net.minecraft.world.entity.monster.illager.AbstractIllager);
	}

	/** Something died: a slaying for the hunter and the watch, and a butcher's extra cuts. */
	private static void died(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || !(entity.level() instanceof ServerLevel level) || of(player).posts().isEmpty()) {
			return;
		}
		if (entity instanceof Enemy) {
			work(level, player, "slay", 1, entity.blockPosition());
		}
		int rank = heldRank(player, Job.BUTCHER);
		Item cut = rank > 0 ? cut(entity) : null;
		if (cut != null && !entity.isBaby()) {
			entity.spawnAtLocation(level, new ItemStack(cut, EXTRA_CUTS[rank]));
		}
	}

	/** The meat {@code e} gives, or null. */
	private static @Nullable Item cut(LivingEntity e) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
		return switch (id) {
			case "cow", "mooshroom" -> Items.BEEF;
			case "pig" -> Items.PORKCHOP;
			case "sheep" -> Items.MUTTON;
			case "chicken" -> Items.CHICKEN;
			case "rabbit" -> Items.RABBIT;
			case "goat" -> Items.MUTTON;
			default -> null;
		};
	}

	/** Someone hurt: the watch's guards come to a Journeyman's aid; a player who hurts their employer's people is fired. */
	private static void hurt(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (entity instanceof ServerPlayer player && source.getEntity() instanceof LivingEntity attacker && attacker != player
			&& heldRank(player, Job.WATCH) >= JOURNEYMAN) {
			Post p = of(player).post(Job.WATCH.key);
			Faction f = p == null ? null : Factions.get(server, p.faction());
			if (f != null && inTown(level, f, player.blockPosition()) && !(attacker instanceof Player)) {
				for (Worker w : Workers.near(level, player.getBoundingBox().inflate(AID_RANGE), w -> w.role() == Folk.GUARD && w.factionId() == f.id())) {
					if (w.mob().getTarget() == null) {
						w.mob().setTarget(attacker);
					}
				}
			}
		}
		if (source.getEntity() instanceof ServerPlayer player && entity != player && !of(player).posts().isEmpty()) {
			int faction = entity instanceof Worker w ? w.factionId() : -1;
			if (faction < 0) {
				Faction f = Quests.settlement(level, entity);
				faction = f == null ? -1 : f.id();
			}
			if (faction >= 0 && (entity instanceof Villager || entity instanceof Folk)) {
				for (Post p : of(player).posts()) {
					if (p.faction() == faction) {
						Faction f = Factions.get(server, faction);
						if (f != null) {
							fire(player, f, "message.jaymoskingdoms.job.fired.harm");
						}
						break;
					}
				}
			}
		}
	}

	/** Every job {@code player} holds with {@code f} ends, and it won't have them back for a few days. */
	static void fire(ServerPlayer player, Faction f, String why) {
		Career c = of(player);
		List<Post> lost = c.posts().stream().filter(p -> p.faction() == f.id()).toList();
		if (lost.isEmpty()) {
			return;
		}
		long until = player.level().getGameTime() + BARRED_DAYS * 24000L;
		edit(player, k -> {
			Career next = k.withBarred(f.id(), until);
			for (Post p : lost) {
				next = next.without(p.job());
			}
			return next.stallFaction() == f.id() ? next.withStall(Optional.empty(), -1) : next;
		});
		player.sendSystemMessage(Component.translatable(why, f.name()).withStyle(ChatFormatting.RED));
		refreshTitles(player.level().getServer());
	}

	// ---- the pilot and the prospector (0.7.369) ----------------------------------------------------------------------

	/** Adds an open order {@code what} to {@code job}'s post for {@code player} if it has none, so a trip or a find is never unpaid. */
	private static void ensureOrder(ServerPlayer player, String job, String what) {
		Post p = of(player).post(job);
		if (p != null && p.orders().stream().noneMatch(o -> o.what().equals(what) && !o.done())) {
			List<Order> orders = new ArrayList<>(p.orders());
			orders.add(new Order(what, 1, 0));
			edit(player, k -> k.withPost(p.withOrders(orders)));
		}
	}

	private static void fare(ServerPlayer player, int emeralds, String message) {
		if (emeralds > 0) {
			give(player, new ItemStack(Items.EMERALD, emeralds));
		}
		player.sendOverlayMessage(Component.translatable(message, emeralds).withStyle(ChatFormatting.GREEN));
	}

	/**
	 * (for Jaymo's Powers, by reflection; {@code ZFighterPodTrip}) {@code pilot} flew a pod trip and came down at {@code at} in
	 * {@code landedIn}, with {@code passenger} in the second seat (or null). A Pilot is paid for a trip with a passenger or
	 * a parcel aboard; a parcel carried down to the yard of the town an order named is handed over to it.
	 */
	public static void flown(ServerPlayer pilot, @Nullable ServerPlayer passenger, ServerLevel landedIn, BlockPos at) {
		Post p = of(pilot).post(Job.PILOT.key);
		if (p == null) {
			return;
		}
		boolean parcel = MerchantOrders.carriesParcel(pilot);
		if (passenger != null || parcel) {
			ensureOrder(pilot, Job.PILOT.key, "fly");
			progress(pilot.level(), pilot, Job.PILOT.key, "fly", 1);
			fare(pilot, PILOT_FARE[Mth.clamp(of(pilot).rank(Job.PILOT.key), 0, 2)], "message.jaymoskingdoms.job.fare");
		}
		if (!parcel) {
			return;
		}
		MinecraftServer server = landedIn.getServer();
		LandingYard.Yard yard = LandingYard.near(landedIn, at, FERRY_AT);
		Post now = of(pilot).post(Job.PILOT.key);
		if (yard == null || now == null) {
			return;
		}
		for (Order o : now.orders()) {
			if (!o.what().startsWith("ferry:") || o.done() || yard.faction().id() != parse(o.what().substring(6))) {
				continue;
			}
			ServerLevel store = Factions.level(server);
			if (!Storage.loaded(store, yard.faction())) {
				// no storehouse to unpack into: the parcel stays in the pack for another try
				return;
			}
			ItemStack taken = MerchantOrders.takeParcel(pilot);
			if (taken.isEmpty()) {
				return;
			}
			for (ItemStack g : MerchantOrders.contents(taken)) {
				ItemStack left = Storage.put(store, yard.faction(), g);
				if (!left.isEmpty()) {
					// a full storehouse hands the rest back rather than losing it
					give(pilot, left);
				}
			}
			com.jaymoskingdoms.Post.tidings(pilot.getUUID(), Component.translatable("message.jaymoskingdoms.job.ferried", yard.faction().name()));
			progress(pilot.level(), pilot, Job.PILOT.key, o.what(), 1);
			return;
		}
	}

	/**
	 * (for Jaymo's Powers, by reflection; {@code Prospecting}) {@code player} made a find on {@code world} (a world key such as
	 * {@code rustfall}): {@code kind} is the ore revealed, or {@code metal:<id>} for a first metal knapped. Only the first of
	 * each kind on each world counts, and only a Prospector is paid for it (at dusk, by the order it fills, and a bonus at once).
	 */
	public static void firstFind(ServerPlayer player, String world, String kind) {
		String key = world + "|" + kind;
		List<String> seen = player.getAttachedOrElse(FINDS, List.of());
		if (seen.contains(key)) {
			return;
		}
		List<String> next = new ArrayList<>(seen);
		next.add(key);
		player.setAttached(FINDS, List.copyOf(next));
		if (of(player).post(Job.PROSPECTOR.key) == null) {
			return;
		}
		ensureOrder(player, Job.PROSPECTOR.key, "find");
		progress(player.level(), player, Job.PROSPECTOR.key, "find", 1);
		fare(player, FIND_BONUS[Mth.clamp(of(player).rank(Job.PROSPECTOR.key), 0, 2)], "message.jaymoskingdoms.job.find");
	}

	// ---- perks that work as you go ---------------------------------------------------------------------------------------

	private static void perks(ServerLevel level, ServerPlayer player, Career c, int t) {
		RandomSource random = level.getRandom();
		if (c.heldRank(Job.FARMER) >= JOURNEYMAN) {
			// crops near you grow faster
			for (int i = 0; i < GROW_TICKS; i++) {
				BlockPos at = player.blockPosition().offset(random.nextInt(GROW_RANGE * 2 + 1) - GROW_RANGE, random.nextInt(5) - 2,
					random.nextInt(GROW_RANGE * 2 + 1) - GROW_RANGE);
				BlockState state = level.getBlockState(at);
				if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
					state.randomTick(level, at, random);
				}
			}
		}
		if (c.heldRank(Job.MINER) >= JOURNEYMAN && t % (CHECK_TICKS * 2) == 0) {
			// ore glints near you, for your eyes alone
			BlockPos me = player.blockPosition();
			for (BlockPos at : BlockPos.betweenClosed(me.offset(-GLINT_RANGE, -GLINT_RANGE, -GLINT_RANGE), me.offset(GLINT_RANGE, GLINT_RANGE, GLINT_RANGE))) {
				if (random.nextInt(3) == 0 && ore(level.getBlockState(at))) {
					level.sendParticles(player, ParticleTypes.WAX_OFF, true, false, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.0);
				}
			}
		}
		if (c.heldRank(Job.HUNTER) >= JOURNEYMAN) {
			// tracks where animals pass, for your eyes alone
			for (LivingEntity beast : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(TRACK_RANGE),
				e -> e != player && e.isAlive() && e instanceof Mob && beast(e) && e.onGround() && e.distanceToSqr(player) > 16.0)) {
				level.sendParticles(player, ParticleTypes.MYCELIUM, true, false, beast.getX(), beast.getY() + 0.05, beast.getZ(), 2, 0.15, 0.0, 0.15, 0.0);
			}
		}
	}

	/** A Master Butcher eating cooked meat: it fills more, as the last bite goes down. */
	private static void eating(ServerPlayer player) {
		ItemStack using = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
		boolean cooked = using.is(Items.COOKED_BEEF) || using.is(Items.COOKED_PORKCHOP) || using.is(Items.COOKED_MUTTON) || using.is(Items.COOKED_CHICKEN)
			|| using.is(Items.COOKED_RABBIT);
		if (cooked && player.getUseItemRemainingTicks() <= 1) {
			EATING.put(player.getUUID(), using.getItem());
			return;
		}
		Item was = EATING.remove(player.getUUID());
		if (was != null && !player.isUsingItem() && heldRank(player, Job.BUTCHER) == MASTER) {
			player.getFoodData().eat(COOKED_FOOD, COOKED_SATURATION);
		}
	}

	// ---- titles ----------------------------------------------------------------------------------------------------------

	/** Every player's title, sent to everyone when any changes. */
	static void refreshTitles(MinecraftServer server) {
		Map<UUID, Component> now = new HashMap<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Component t = title(p);
			if (t != null) {
				now.put(p.getUUID(), t);
			}
		}
		if (now.equals(sent)) {
			return;
		}
		sent = Map.copyOf(now);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(p, Titles.TYPE)) {
				ServerPlayNetworking.send(p, new Titles(sent));
			}
		}
	}

	// ---- hiring ----------------------------------------------------------------------------------------------------------

	/** Why {@code f} won't take {@code player} on now (a message key), or null if it will. */
	static @Nullable String refusal(ServerPlayer player, Faction f) {
		if (f.fallen() || f.type() == FactionType.BARBARIAN) {
			return "message.jaymoskingdoms.job.no_work";
		}
		Long until = of(player).barred().get(Integer.toString(f.id()));
		if (until != null && player.level().getGameTime() < until) {
			return "message.jaymoskingdoms.job.barred";
		}
		if (Reputation.get(player, f.id()) < HIRE_REPUTATION || Theft.wanted(player, f.id())) {
			return "message.jaymoskingdoms.job.disliked";
		}
		return null;
	}

	/**
	 * {@code player} takes {@code job} with {@code f} (one held elsewhere moves here): its tools the first time, and the
	 * day's orders. {@code speaker}: who took them on (they say so), or null.
	 */
	static boolean hire(ServerLevel level, ServerPlayer player, Faction f, Job job, @Nullable LivingEntity speaker) {
		String why = refusal(player, f);
		if (why != null) {
			player.sendSystemMessage(Component.translatable(why, f.name()).withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (job.yardOnly() && !LandingYard.has(level.getServer(), f)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.job.no_yard", f.name(), job.title()).withStyle(ChatFormatting.GRAY));
			return false;
		}
		Career c = of(player);
		Post old = c.post(job.key);
		if (old != null && old.faction() == f.id()) {
			return false;
		}
		if (old != null && old.done() > 0) {
			// moving the job here: what the old town owes is paid first, as best its storehouse can
			edit(player, k -> k.withPaidDay(-1L));
			pay(level, player, day(level));
		}
		Post post = new Post(job.key, f.id(), f.name(), List.of(), -1L, 0, Optional.empty(), "");
		edit(player, k -> k.withPost(post));
		if (!c.kits().contains(job.key)) {
			for (ItemStack stack : kit(job)) {
				give(player, stack);
			}
			edit(player, k -> k.withKit(job.key));
		}
		orders(level, player, f, post, day(level));
		Component title = title(job, c.rank(job.key));
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.job.hired", f.name(), title).withStyle(ChatFormatting.GREEN));
		if (speaker != null) {
			Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.work.hired." + level.getRandom().nextInt(3), job.title()), Speech.WHITE);
		}
		refreshTitles(level.getServer());
		return true;
	}

	/** (from {@link LifeStory}) A village start's job: taken up at the first town they come to (its tools were given with the story). */
	static void storyJob(ServerPlayer player, String key) {
		Job job = Job.of(key);
		if (job == null) {
			return;
		}
		long until = player.level().getGameTime() + PENDING_TICKS;
		edit(player, k -> k.withKit(job.key).withPending(job.key, until));
	}

	/** (from {@link LifeStory}) The job a village start leans to by who raised you, else one at random. */
	static String storyLean(String raised, RandomSource random) {
		return switch (raised) {
			case "farmers" -> Job.FARMER.key;
			case "smiths" -> Job.SMITH.key;
			case "fisherfolk" -> Job.FISHER.key;
			case "hunters" -> Job.HUNTER.key;
			case "soldiers" -> Job.WATCH.key;
			default -> Job.values()[random.nextInt(Job.PILOT.ordinal())].key;
		};
	}

	/** A job's tools and stock. */
	static List<ItemStack> kit(Job job) {
		return switch (job) {
			case BUTCHER -> List.of(new ItemStack(Items.IRON_AXE), new ItemStack(Items.COOKED_BEEF, 6));
			case WOODCUTTER -> List.of(new ItemStack(Items.IRON_AXE), new ItemStack(Items.OAK_LOG, 16));
			case BUILDER -> List.of(new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.OAK_PLANKS, 32), new ItemStack(Items.LADDER, 8));
			case FARMER -> List.of(new ItemStack(Items.IRON_HOE), new ItemStack(Items.WHEAT_SEEDS, 16), new ItemStack(Items.BONE_MEAL, 8));
			case MINER -> List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.TORCH, 16));
			case FISHER -> List.of(new ItemStack(Items.FISHING_ROD), new ItemStack(Items.COD, 6));
			case SMITH -> List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_INGOT, 6));
			case HUNTER -> List.of(new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 24));
			case COURIER -> List.of(new ItemStack(Items.SADDLE), new ItemStack(Items.BREAD, 8));
			case WATCH -> List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD));
			case PILOT -> List.of(new ItemStack(Items.COMPASS), new ItemStack(Items.CLOCK), new ItemStack(Items.BREAD, 8));
			case PROSPECTOR -> List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.TORCH, 16), new ItemStack(Items.COMPASS));
		};
	}

	/** The jobs {@code f} is short of hands for: goods running low in its storehouse, a building waiting on stock, a realm's walls to watch. */
	static boolean shortOf(ServerLevel level, Faction f, Job job) {
		ServerLevel store = Factions.level(level.getServer());
		if (job == Job.WATCH) {
			return f.type().isRealm();
		}
		// (0.7.250) its own building in town wants hands, whatever's in the storehouse
		if (hasHall(level.getServer(), f, job) && job != Job.COURIER) {
			return true;
		}
		if (job == Job.COURIER) {
			return Factions.all(level.getServer()).stream().anyMatch(o -> o.id() != f.id() && !o.fallen() && o.type() != FactionType.BARBARIAN
				&& o.seat().distSqr(f.seat()) <= CARRY_MAX * CARRY_MAX);
		}
		if (!Storage.loaded(store, f)) {
			return false;
		}
		if (job == Job.BUILDER) {
			return !Storage.missing(store, f, Planner.needs(store, f)).isEmpty();
		}
		return job.goods.stream().anyMatch(g -> stock(store, f, g) < SHORT_BELOW);
	}

	// ---- talking about work ---------------------------------------------------------------------------------------------

	/** {@code villager} can talk work: grown, and one of a settlement's (server side). */
	public static boolean offersWork(Villager villager) {
		return villager.level() instanceof ServerLevel level && Quests.settlement(level, villager) != null;
	}

	/** "Talk about work" with {@code speaker} (MCA's menu): what their town has for you, and what you have to hand over. */
	public static void talk(ServerPlayer player, LivingEntity speaker) {
		ServerLevel level = player.level();
		Faction f = Quests.settlement(level, speaker);
		if (f == null) {
			Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.work.none"), Speech.WHITE);
			return;
		}
		String why = refusal(player, f);
		if (why != null && of(player).posts().stream().noneMatch(p -> p.faction() == f.id())) {
			Speech.tell(level, speaker, player, Component.translatable(why.replace("message.", "talk.").replace(".job.", ".work."), f.name()), Speech.WHITE);
			return;
		}
		main(player, speaker, f);
	}

	private static void main(ServerPlayer player, LivingEntity speaker, Faction f) {
		ServerLevel level = player.level();
		Career c = of(player);
		List<String> actions = new ArrayList<>();
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		List<Job> open = offers(level, player, f);
		actions.add("jobs");
		options.add(Component.translatable("talk.jaymoskingdoms.work.ask"));
		enabled.add(!open.isEmpty() && refusal(player, f) == null);
		boolean here = c.posts().stream().anyMatch(p -> p.faction() == f.id());
		if (here) {
			actions.add("hand");
			options.add(Component.translatable("talk.jaymoskingdoms.work.hand"));
			enabled.add(c.posts().stream().anyMatch(p -> p.faction() == f.id() && p.orders().stream().anyMatch(o -> o.give() && handable(player, o) > 0)));
			actions.add("mine");
			options.add(Component.translatable("talk.jaymoskingdoms.work.mine"));
			enabled.add(true);
		}
		actions.add("close");
		options.add(Component.translatable("talk.jaymoskingdoms.work.close"));
		enabled.add(true);
		List<Job> short_ = open.stream().filter(j -> shortOf(level, f, j)).toList();
		// (0.7.270) who they are and where you stand with their town first (WorkTalk)
		Component words = WorkTalk.greeting(player, speaker, f, open);
		if (words == null) {
			words = short_.isEmpty() ? Component.translatable("talk.jaymoskingdoms.work.greet", f.name())
				: Component.translatable("talk.jaymoskingdoms.work.greet_short", f.name(), list(short_));
		}
		send(player, speaker, words, actions, options, enabled);
	}

	/** The jobs {@code f} offers {@code player} (not held there already), short ones first, at most {@link #OFFERS_MOST}. */
	private static List<Job> offers(ServerLevel level, ServerPlayer player, Faction f) {
		Career c = of(player);
		List<Job> out = new ArrayList<>();
		for (Job j : Job.values()) {
			Post p = c.post(j.key);
			if ((p == null || p.faction() != f.id()) && (j != Job.WATCH || f.type().isRealm() || f.type() == FactionType.VILLAGE)
				&& (!j.yardOnly() || LandingYard.has(level.getServer(), f))) {
				out.add(j);
			}
		}
		out.sort(Comparator.comparing(j -> !shortOf(level, f, j)));
		return out.size() > OFFERS_MOST ? out.subList(0, OFFERS_MOST) : out;
	}

	private static Component list(List<Job> jobs) {
		Component out = Component.empty();
		for (int i = 0; i < jobs.size(); i++) {
			if (i > 0) {
				out = out.copy().append(", ");
			}
			out = out.copy().append(jobs.get(i).title());
		}
		return out;
	}

	private static void send(ServerPlayer player, LivingEntity speaker, Component words, List<String> actions, List<Component> options, List<Boolean> enabled) {
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			return;
		}
		MENUS.put(player.getUUID(), new Menu(speaker.getId(), List.copyOf(actions)));
		ServerPlayNetworking.send(player, new TalkNet.Ask(speaker.getId(), speaker.getDisplayName(), words, options, enabled));
	}

	/** An answer to a talk about work; false when it wasn't one. */
	static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Menu menu = MENUS.get(player.getUUID());
		if (menu == null || menu.speaker() != answer.entityId()) {
			return false;
		}
		MENUS.remove(player.getUUID());
		ServerLevel level = player.level();
		if (answer.option() < 0 || answer.option() >= menu.actions().size() || !(level.getEntity(answer.entityId()) instanceof LivingEntity speaker)
			|| !speaker.isAlive() || speaker.distanceTo(player) > TALK_RANGE) {
			return true;
		}
		Faction f = Quests.settlement(level, speaker);
		if (f == null) {
			return true;
		}
		String action = menu.actions().get(answer.option());
		String arg = action.contains(":") ? action.substring(action.indexOf(':') + 1) : "";
		switch (action.contains(":") ? action.substring(0, action.indexOf(':')) : action) {
			case "jobs" -> jobs(player, speaker, f);
			case "hire" -> {
				Job job = Job.of(arg);
				if (job != null) {
					hire(level, player, f, job, speaker);
				}
			}
			case "hand" -> hand(level, player, speaker, f);
			case "mine" -> mine(player, speaker, f);
			case "back" -> main(player, speaker, f);
			case "quit" -> quit(player, speaker, f, arg);
			case "apprentice" -> apprentice(player, speaker, f, arg);
			case "stall" -> {
				MARKING.put(player.getUUID(), f.id());
				Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.work.stall"), Speech.WHITE);
			}
			case "build" -> buildings(player, speaker, f);
			case "next" -> {
				NEXT_BUILDING.put(f.id(), arg);
				Blueprint.Entry e = Planner.entry(f, arg);
				Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.work.next_chosen", building(arg)), Speech.WHITE);
				if (e == null) {
					NEXT_BUILDING.remove(f.id());
				}
			}
			case "guards" -> callGuards(level, player, speaker, f);
			default -> {
				// "Nothing for now"
			}
		}
		return true;
	}

	private static void jobs(ServerPlayer player, LivingEntity speaker, Faction f) {
		ServerLevel level = player.level();
		Career c = of(player);
		List<String> actions = new ArrayList<>();
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		int held = c.posts().size() + 1;
		for (Job j : offers(level, player, f)) {
			int rank = c.rank(j.key);
			Post elsewhere = c.post(j.key);
			actions.add("hire:" + j.key);
			Component line = Component.translatable(shortOf(level, f, j) ? "talk.jaymoskingdoms.work.offer_short" : "talk.jaymoskingdoms.work.offer",
				title(j, rank), wage(level.getServer(), f, j, rank, elsewhere == null ? held : held - 1));
			if (hasHall(level.getServer(), f, j)) {
				line = line.copy().append(Component.translatable("talk.jaymoskingdoms.work.offer_hall",
					Component.translatableWithFallback("building.jaymoskingdoms." + j.hall, j.hall)));
			}
			if (elsewhere != null) {
				line = line.copy().append(Component.translatable("talk.jaymoskingdoms.work.offer_moves", elsewhere.town()));
			}
			options.add(line);
			enabled.add(true);
		}
		actions.add("back");
		options.add(Component.translatable("talk.jaymoskingdoms.work.back"));
		enabled.add(true);
		send(player, speaker, Component.translatable("talk.jaymoskingdoms.work.jobs"), actions, options, enabled);
	}

	private static void mine(ServerPlayer player, LivingEntity speaker, Faction f) {
		ServerLevel level = player.level();
		Career c = of(player);
		List<String> actions = new ArrayList<>();
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		boolean villager = speaker instanceof Villager v && !Rulers.is(v) && !(speaker instanceof Worker w && w.role() == Folk.GUARD) && !apprenticed(level, speaker);
		for (Post p : c.posts()) {
			if (p.faction() != f.id() || p.kind() == null) {
				continue;
			}
			if (c.rank(p.job()) == MASTER && p.apprentice().isEmpty() && villager && !p.kind().goods.isEmpty()) {
				actions.add("apprentice:" + p.job());
				options.add(Component.translatable("talk.jaymoskingdoms.work.take_apprentice", speaker.getName(), p.kind().title()));
				enabled.add(true);
			}
			if (p.kind() == Job.BUILDER && c.rank(p.job()) == MASTER) {
				actions.add("build");
				options.add(Component.translatable("talk.jaymoskingdoms.work.choose_building"));
				enabled.add(!Planner.eligible(level.getServer(), f).isEmpty());
			}
			if (p.kind() == Job.WATCH && c.rank(p.job()) == MASTER) {
				actions.add("guards");
				options.add(Component.translatable("talk.jaymoskingdoms.work.call_guards"));
				enabled.add(McaCompat.LOADED);
			}
		}
		if (c.master() && (c.stall().isEmpty() || c.stallFaction() != f.id())) {
			actions.add("stall");
			options.add(Component.translatable(c.stall().isEmpty() ? "talk.jaymoskingdoms.work.open_stall" : "talk.jaymoskingdoms.work.move_stall"));
			enabled.add(true);
		}
		for (Post p : c.posts()) {
			if (p.faction() == f.id() && p.kind() != null) {
				actions.add("quit:" + p.job());
				options.add(Component.translatable("talk.jaymoskingdoms.work.quit", p.kind().title()));
				enabled.add(true);
			}
		}
		actions.add("back");
		options.add(Component.translatable("talk.jaymoskingdoms.work.back"));
		enabled.add(true);
		send(player, speaker, Component.translatable("talk.jaymoskingdoms.work.mine_words", orders(c, f.id())), actions, options, enabled);
	}

	/** How many orders {@code c} has open with {@code faction}. */
	private static int orders(Career c, int faction) {
		return c.posts().stream().filter(p -> p.faction() == faction).mapToInt(p -> p.orders().size()).sum();
	}

	/** Goods handed over for every open order with {@code f}: taken from the pack into its storehouse. */
	private static void hand(ServerLevel level, ServerPlayer player, LivingEntity speaker, Faction f) {
		ServerLevel store = Factions.level(level.getServer());
		int given = 0;
		for (Post p : of(player).posts()) {
			if (p.faction() != f.id()) {
				continue;
			}
			for (Order o : p.orders()) {
				if (!o.give() || o.done()) {
					continue;
				}
				int n = Math.min(handable(player, o), o.count() - o.progress());
				if (n <= 0) {
					continue;
				}
				ItemStack taken = takeFromPack(player, o.what().substring(5), n);
				if (Storage.loaded(store, f) && !taken.isEmpty()) {
					// (what a full storehouse can't hold goes to the foreman's own stores)
					Storage.put(store, f, taken);
				}
				given += n;
				progress(level, player, p.job(), o.what(), n);
			}
		}
		Speech.tell(level, speaker, player, Component.translatable(given > 0 ? "talk.jaymoskingdoms.work.handed" : "talk.jaymoskingdoms.work.nothing_to_hand", given),
			Speech.WHITE);
	}

	/** How many of an order's goods {@code player} carries. */
	private static int handable(ServerPlayer player, Order o) {
		String what = o.what().substring(5);
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && matches(s, what) && Quests.questOf(s) == null) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** Takes {@code n} of {@code what} from the pack; returns them as one stack of the first kind found (the rest are used up). */
	private static ItemStack takeFromPack(ServerPlayer player, String what, int n) {
		Inventory inv = player.getInventory();
		ItemStack first = ItemStack.EMPTY;
		for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || !matches(s, what) || Quests.questOf(s) != null) {
				continue;
			}
			int m = Math.min(n, s.getCount());
			ItemStack part = s.split(m);
			n -= m;
			if (first.isEmpty()) {
				first = part;
			} else if (ItemStack.isSameItemSameComponents(first, part)) {
				first.grow(part.getCount());
			}
		}
		inv.setChanged();
		return first;
	}

	private static void quit(ServerPlayer player, LivingEntity speaker, Faction f, String key) {
		Job job = Job.of(key);
		Post p = of(player).post(key);
		if (job == null || p == null || p.faction() != f.id()) {
			return;
		}
		if (p.done() > 0) {
			// what's owed is paid now, as best the storehouse can
			edit(player, k -> k.withPaidDay(-1L));
			pay(player.level(), player, day(player.level()));
		}
		edit(player, k -> k.without(key));
		Speech.tell(player.level(), speaker, player, Component.translatable("talk.jaymoskingdoms.work.quit_words", job.title()), Speech.WHITE);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.job.quit", job.title(), f.name()).withStyle(ChatFormatting.GRAY));
		refreshTitles(player.level().getServer());
	}

	/** Someone else's apprentice already (any online player's). */
	private static boolean apprenticed(ServerLevel level, LivingEntity who) {
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			for (Post post : of(p).posts()) {
				if (post.apprentice().isPresent() && post.apprentice().get().equals(who.getUUID())) {
					return true;
				}
			}
		}
		return false;
	}

	private static void apprentice(ServerPlayer player, LivingEntity speaker, Faction f, String key) {
		Post p = of(player).post(key);
		if (p == null || p.faction() != f.id() || of(player).rank(key) != MASTER || p.kind() == null) {
			return;
		}
		String name = speaker.getName().getString();
		edit(player, k -> k.withPost(p.withApprentice(Optional.of(speaker.getUUID()), name)));
		Speech.tell(player.level(), speaker, player, Component.translatable("talk.jaymoskingdoms.work.apprenticed", p.kind().title()), Speech.GOLD);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.job.apprentice", name, p.kind().title()).withStyle(ChatFormatting.GREEN));
	}

	/** The next stall {@code player} uses in town becomes theirs (not the town's own storage). */
	private static void markStall(ServerLevel level, ServerPlayer player, BlockPos pos) {
		Integer faction = MARKING.get(player.getUUID());
		Faction f = faction == null ? null : Factions.get(level.getServer(), faction);
		if (f == null || !(level.getBlockEntity(pos) instanceof Container)) {
			return;
		}
		MARKING.remove(player.getUUID());
		if (!inTown(level, f, pos) || f.holdings().storage().contains(pos)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.job.stall_refused", f.name()).withStyle(ChatFormatting.GRAY));
			return;
		}
		edit(player, k -> k.withStall(Optional.of(pos.immutable()), f.id()).withSoldDay(day(level)));
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.job.stall_opened", f.name(), STALL_EMERALDS).withStyle(ChatFormatting.GREEN));
	}

	private static void buildings(ServerPlayer player, LivingEntity speaker, Faction f) {
		List<String> actions = new ArrayList<>();
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Blueprint.Entry e : Planner.eligible(player.level().getServer(), f)) {
			if (seen.add(e.id()) && actions.size() < OFFERS_MOST) {
				actions.add("next:" + e.id());
				options.add(building(e.id()));
				enabled.add(true);
			}
		}
		actions.add("back");
		options.add(Component.translatable("talk.jaymoskingdoms.work.back"));
		enabled.add(true);
		send(player, speaker, Component.translatable("talk.jaymoskingdoms.work.buildings"), actions, options, enabled);
	}

	private static Component building(String id) {
		return Component.translatableWithFallback("building.jaymoskingdoms." + id, id.replace('_', ' '));
	}

	/** (from {@link Construction}) The building {@code f}'s Master Builder chose to go up next, or null. */
	static @Nullable String nextBuilding(int faction) {
		return NEXT_BUILDING.get(faction);
	}

	/** It's going up: the choice is spent. */
	static void builtNext(int faction) {
		NEXT_BUILDING.remove(faction);
	}

	/** A Master of the watch calls a few of the town's guards to follow them (MCA's follow, as a hired guard's). */
	private static void callGuards(ServerLevel level, ServerPlayer player, LivingEntity speaker, Faction f) {
		int called = 0;
		for (Worker w : Workers.near(level, player.getBoundingBox().inflate(AID_RANGE), w -> w.role() == Folk.GUARD && w.factionId() == f.id())) {
			if (called < FOLLOWERS && w.mob() instanceof Villager v && McaCompat.follow(v, player)) {
				called++;
			}
		}
		Speech.tell(level, speaker, player, Component.translatable(called > 0 ? "talk.jaymoskingdoms.work.guards_come" : "talk.jaymoskingdoms.work.no_guards", called),
			Speech.WHITE);
	}

	// ---- the journal and /job --------------------------------------------------------------------------------------------

	/** (for {@link Journal}) {@code player}'s open orders as journal entries. */
	static List<Journal.Entry> journal(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		List<Journal.Entry> out = new ArrayList<>();
		for (Post p : of(player).posts()) {
			Job job = p.kind();
			Faction f = Factions.get(server, p.faction());
			if (job == null || f == null) {
				continue;
			}
			for (Order o : p.orders()) {
				Optional<BlockPos> target = o.what().startsWith("carry:") || o.what().startsWith("ferry:")
					? Optional.ofNullable(Factions.get(server, parse(o.what().substring(6))))
					.map(Faction::seat) : Optional.of(f.seat());
				out.add(new Journal.Entry(false, false, Component.translatable("journal.jaymoskingdoms.order.title", job.title(), describe(server, o)),
					Component.translatable("journal.jaymoskingdoms.order.by", f.name()), Component.translatable("journal.jaymoskingdoms.order.text", job.title(),
						wage(server, f, job, of(player).rank(p.job()), of(player).posts().size())),
					Component.translatable("journal.jaymoskingdoms.order.progress", o.progress(), o.count()), target));
			}
		}
		return out;
	}

	/** What an order asks, in words. */
	public static Component describe(@Nullable MinecraftServer server, Order o) {
		String what = o.what();
		if (what.startsWith("give:")) {
			return Component.translatable("order.jaymoskingdoms.give", o.count(), goodName(what.substring(5)));
		}
		if (what.startsWith("ferry:")) {
			Faction to = server == null ? null : Factions.get(server, parse(what.substring(6)));
			return Component.translatable("order.jaymoskingdoms.ferry", to == null ? Component.translatable("order.jaymoskingdoms.carry.far") : Component.literal(to.name()));
		}
		if (what.startsWith("carry:")) {
			Faction to = server == null ? null : Factions.get(server, parse(what.substring(6)));
			return Component.translatable("order.jaymoskingdoms.carry", to == null ? Component.translatable("order.jaymoskingdoms.carry.far") : Component.literal(to.name()));
		}
		return Component.translatable("order.jaymoskingdoms." + what, o.count());
	}

	public static Component goodName(String good) {
		if (good.startsWith("#")) {
			String path = good.substring(good.indexOf(':') + 1);
			return Component.translatable("order.jaymoskingdoms.tag." + path);
		}
		return new ItemStack(item(good)).getHoverName();
	}

	/** {@code /job}: your jobs, ranks and open orders. */
	private static int list(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Career career = of(player);
		if (career.posts().isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.job.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		for (Post p : career.posts()) {
			if (p.kind() == null) {
				continue;
			}
			int rank = career.rank(p.job());
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.job.line", title(p.kind(), rank), p.town(), career.xpIn(p.job()),
				nextRankXp(rank) < 0 ? "-" : Integer.toString(nextRankXp(rank)), p.orders().size()).withStyle(ChatFormatting.GOLD));
			for (Order o : p.orders()) {
				player.sendSystemMessage(Component.literal("  ").append(describe(c.getSource().getServer(), o)).append(" " + o.progress() + "/" + o.count())
					.withStyle(ChatFormatting.GRAY));
			}
		}
		return career.posts().size();
	}

	// ---- items -----------------------------------------------------------------------------------------------------------

	/** {@code stack} is the good {@code good} (an item id, or {@code #tag}). */
	static boolean matches(ItemStack stack, String good) {
		if (good.startsWith("#")) {
			Identifier id = Identifier.tryParse(good.substring(1));
			return id != null && stack.is(TagKey.create(Registries.ITEM, id));
		}
		return stack.is(item(good));
	}

	private static int stock(ServerLevel level, Faction f, String good) {
		return Storage.count(level, f, s -> matches(s, good));
	}

	private static Item item(String id) {
		if (id.startsWith("#")) {
			return id.endsWith("planks") ? Items.OAK_PLANKS : Items.OAK_LOG;
		}
		Identifier key = Identifier.tryParse(id);
		return key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
	}

	private static int parse(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
