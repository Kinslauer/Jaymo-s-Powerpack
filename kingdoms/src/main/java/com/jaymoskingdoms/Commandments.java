package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.535) Andy: "all the patrons should have commandments like these, these are all good to keep for Truth, and truth
 * villagers will vehemently refuse if asked/forced to break any of them." Every patron has ten commandments, written in
 * its book of Commandments ({@link #ITEM}, given with the patron's lore book and on swearing). A sworn player who breaks
 * one of their own patron's loses favour ({@link #FAVOUR_BROKEN}, once a day for each) and the book counts the ones broken
 * ("Broken: n / 10") until they swear to someone else.
 * <p>
 * Each commandment is a set of acts ({@link #LAWS}); an act is reported by name from wherever it happens ({@link #act}):
 * kills and blows here, crimes from {@link Theft#commit}, talk from {@link Recollections}, the rest from small hooks.
 * <p>
 * The Truth's own folk keep its ten themselves: they will not strike the unarmed and innocent ({@link #spares}, every
 * mob's {@code canAttack}), follow anyone at night, into the Nether or up into the sky, take rot as a gift
 * ({@link #refusesCommand}), take a bribe ({@link Approaches}) or rob the dead ({@link Looters}). They say so, hotly.
 */
public final class Commandments {
	// ---- tuning ----
	/** Favour a sworn player loses for each commandment broken (each commandment at most once a day). */
	static final int FAVOUR_BROKEN = 6;
	/** How often the state commandments (night, sky, Nether, light, sea) are looked at (ticks). */
	static final int TICK_EVERY = 40;
	/** Awake this long (ticks, the vanilla rest stat) at dawn, having been on at midnight, is a night not slept. */
	static final int AWAKE_NIGHT = 11000;
	/** The Deep Mother: days without water (rain or sea) before her fifth is broken. */
	static final int DRY_DAYS = 3;
	/** Block light this low, with no light in hand, is walking in the dark. */
	static final int DARK_LIGHT = 3;
	/** Below this height with no sky above is "below" (the Lantern Warden's third). */
	static final int BELOW_Y = 50;
	/** A refusal is said at most this often by one villager (ticks); how far a following villager is looked for. */
	static final int REFUSE_GAP = 200;
	static final double FOLLOW_RANGE = 48.0;
	static final int LINES = 3;
	public static final int COUNT = 10;
	/** The custom data key holding a book's patron. */
	static final String KEY = "jaymoskingdoms_commandments";

	/** Truth's commandments by number (0-based), for the refusals. */
	static final int TONGUE = 0, INNOCENT = 1, TAKE = 3, NIGHT = 5, ROT = 6, SKY = 8, NETHER = 9;

	/** For each patron (by ordinal), its ten commandments, each the acts that break it. */
	static final String[][][] LAWS = new String[Patron.values().length][][];

	static {
		law(Patron.TRUTH,
			acts("rude", "bribe", "broken_word"), acts("kill_innocent", "kill_child"), acts("book"), acts("theft"),
			acts("explode", "wildfire", "vandal"), acts("no_sleep"), acts("eat_rot"), acts("careless_death"), acts("sky"), acts("nether"));
		law(Patron.HEARTHKEEPER,
			acts("break_hearth"), acts("hurt_child", "kill_child"), acts("hit_family"), acts("eat_raw"), acts("crop"),
			acts("trespass"), acts("assault"), acts("broken_word"), acts("kill_innocent"), acts("theft"));
		law(Patron.ANVIL_FATHER,
			acts("ocean"), acts("tool_broke"), acts("smith_struck"), acts("wooden_weapon"), acts("sea_swim"),
			acts("vandal"), acts("fell"), acts("broken_word"), acts("explode"), acts("theft"));
		law(Patron.DEEP_MOTHER,
			acts("deep_ore"), acts("kill_sea_kin"), acts("drowned"), acts("nether"), acts("dry"),
			acts("sky"), acts("eat_rot"), acts("broken_word"), acts("wildfire"), acts("theft"));
		law(Patron.GREEN_HUNTSMAN,
			acts("kill_young"), acts("kill_tame"), acts("kill_by_fire"), acts("eat_rot"), acts("wildfire"),
			acts("explode"), acts("kill_asleep"), acts("nether"), acts("livestock"), acts("kill_innocent"));
		law(Patron.LANTERN_WARDEN,
			acts("break_light"), acts("dark_walk"), acts("dark_below"), acts("necromancy"), acts("blood"),
			acts("rob_dead"), acts("undead_death"), acts("kill_asleep"), acts("broken_word"), acts("turned"));
		law(Patron.CROWNED_SUN,
			acts("hit_ruler"), acts("theft"), acts("invisible"), acts("sneak_attack"), acts("outlawed"),
			acts("arson"), acts("bribe"), acts("broken_word"), acts("kill_innocent"), acts("lockpick"));
		law(Patron.ASH_WHISPERER,
			acts("caught"), acts("accuse"), acts("fire_hurt"), acts("introduce"), acts("toll"),
			acts("kill_innocent"), acts("lock_failed"), acts("outlawed"), acts("broken_word"), acts("searched"));
	}

	private static String[] acts(String... acts) {
		return acts;
	}

	private static void law(Patron p, String[]... ten) {
		if (ten.length != COUNT) {
			throw new IllegalStateException("a patron has ten commandments: " + p.key);
		}
		LAWS[p.ordinal()] = ten;
	}

	/** The crimes ({@link Theft.Crime}) that are each act. */
	private static final Set<Theft.Crime> THEFTS = Set.of(Theft.Crime.RUMMAGE, Theft.Crime.TAKE, Theft.Crime.CHEST, Theft.Crime.STOREHOUSE,
		Theft.Crime.SADDLEBAG, Theft.Crime.PICKPOCKET, Theft.Crime.ROBBING, Theft.Crime.BURGLARY);
	private static final Set<Theft.Crime> VANDALS = Set.of(Theft.Crime.CROP, Theft.Crime.BED, Theft.Crime.JOB_BLOCK, Theft.Crime.BELL, Theft.Crime.ARSON);

	// ---- what's kept ----
	/**
	 * On a player: the patron whose commandments are counted, the ones broken (bits), the day and the ones broken that day
	 * (bits), and the last time they were wet (overworld clock). Saved, kept through death, synced to them alone (the book reads it).
	 */
	public record Kept(String patron, int broken, long day, int today, long wet, long night) {
		public static final Kept EMPTY = new Kept("", 0, -1L, 0, -1L, -1L);
		public static final Codec<Kept> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("patron", "").forGetter(Kept::patron),
			Codec.INT.optionalFieldOf("broken", 0).forGetter(Kept::broken),
			Codec.LONG.optionalFieldOf("day", -1L).forGetter(Kept::day),
			Codec.INT.optionalFieldOf("today", 0).forGetter(Kept::today),
			Codec.LONG.optionalFieldOf("wet", -1L).forGetter(Kept::wet),
			Codec.LONG.optionalFieldOf("night", -1L).forGetter(Kept::night)).apply(i, Kept::new));

		public boolean broke(int n) {
			return (this.broken & 1 << n) != 0;
		}

		public int count() {
			return Integer.bitCount(this.broken & (1 << COUNT) - 1);
		}
	}

	public static final AttachmentType<Kept> KEPT = AttachmentRegistry.create(JaymosKingdoms.id("commandments"),
		b -> b.persistent(Kept.CODEC).copyOnDeath().syncWith(ByteBufCodecs.fromCodec(Kept.CODEC), AttachmentSyncPredicate.targetOnly()));

	public static Item ITEM;
	/** Set by the client: open the book of this patron (its key). */
	public static Consumer<String> open = key -> {
	};

	/** Not kept: when each villager last said a refusal; whether each player was on at midnight. */
	private static final Map<UUID, Long> SAID = new HashMap<>();
	private static final Map<UUID, Long> MIDNIGHT = new HashMap<>();

	private Commandments() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("commandments"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Book(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.WRITABLE_BOOK, ITEM));
		JaymosKingdoms.LOGGER.debug("commandments attachment: {}", KEPT);
		ServerLivingEntityEvents.AFTER_DEATH.register(Commandments::died);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F) {
				struck(entity, source);
			}
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> broken(player, state));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp) {
				lit(sp, player.getItemInHand(hand), level.getBlockState(hit.getBlockPos()));
			}
			return InteractionResult.PASS;
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && item.getOwner() instanceof ServerPlayer sp) {
				thrown(sp, item.getItem());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Commandments::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MIDNIGHT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SAID.clear();
			MIDNIGHT.clear();
		});
	}

	// ---- reading ----

	public static Kept state(Player player) {
		return player.getAttachedOrElse(KEPT, Kept.EMPTY);
	}

	/** What a patron's commandment {@code n} says. */
	public static Component text(Patron p, int n) {
		return Component.translatable("commandment.jaymoskingdoms." + p.key + "." + n);
	}

	/** Roman numerals for the ten. */
	public static String numeral(int n) {
		return switch (n) {
			case 0 -> "I";
			case 1 -> "II";
			case 2 -> "III";
			case 3 -> "IV";
			case 4 -> "V";
			case 5 -> "VI";
			case 6 -> "VII";
			case 7 -> "VIII";
			case 8 -> "IX";
			default -> "X";
		};
	}

	// ---- breaking ----

	/**
	 * {@code player} did {@code act}. If they are sworn to a patron one of whose commandments it breaks, it is broken: favour
	 * lost (once a day for each commandment), and the book marks it.
	 */
	public static void act(@Nullable Player player, String act) {
		if (!(player instanceof ServerPlayer sp) || sp.isSpectator() || sp.isCreative()) {
			return;
		}
		// (0.7.581) the devout who see it, of whatever patron it offends, cast them out
		CastOut.blasphemy(sp, act);
		Patron p = Patrons.of(sp);
		if (p == null) {
			return;
		}
		String[][] laws = LAWS[p.ordinal()];
		for (int n = 0; n < COUNT; n++) {
			for (String a : laws[n]) {
				if (a.equals(act)) {
					broke(sp, p, n);
				}
			}
		}
	}

	private static void broke(ServerPlayer sp, Patron p, int n) {
		Kept k = state(sp);
		if (!k.patron.equals(p.key)) {
			k = new Kept(p.key, 0, -1L, 0, k.wet, k.night);
		}
		long today = Patrons.today(sp.level().getServer());
		int todays = k.day == today ? k.today : 0;
		if ((todays & 1 << n) != 0) {
			return;
		}
		sp.setAttached(KEPT, new Kept(k.patron, k.broken | 1 << n, today, todays | 1 << n, k.wet, k.night));
		// (0.7.581) strict faith: twice over
		Patrons.lose(sp, PatronWrath.strict(sp) ? FAVOUR_BROKEN * 2 : FAVOUR_BROKEN);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.commandment.broken", Patrons.coloured(p), text(p, n))
			.withStyle(ChatFormatting.GRAY));
		sp.level().playSound(null, sp.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.6F, 0.45F);
		JaymosKingdoms.LOGGER.debug("{} broke {}'s commandment {}", sp.getName().getString(), p.key, n + 1);
	}

	/** (From {@link Theft#commit}) a crime, seen or not, by {@code player}. */
	static void crime(ServerPlayer player, Theft.Crime crime, boolean seen, boolean heavy) {
		if (THEFTS.contains(crime)) {
			act(player, "theft");
		}
		if (VANDALS.contains(crime)) {
			act(player, "vandal");
		}
		switch (crime) {
			case CROP -> act(player, "crop");
			case ARSON -> act(player, "arson");
			case TRESPASS -> act(player, "trespass");
			case ASSAULT -> act(player, "assault");
			case LIVESTOCK -> act(player, "livestock");
			case ROBBING -> act(player, "rob_dead");
			case NECROMANCY -> act(player, "necromancy");
			case BLOODLETTING -> act(player, "blood");
			default -> {
			}
		}
		if (seen) {
			act(player, "caught");
			if (heavy) {
				act(player, "outlawed");
			}
		}
	}

	/** Rotten food (the Truth's seventh): rotten flesh, a spider's eye, a green potato, anything named rotten or spoiled. */
	static boolean rot(ItemStack stack) {
		if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE) || stack.is(Items.POISONOUS_POTATO)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.contains("rotten") || path.contains("spoiled") || path.contains("rotted");
	}

	/** Raw meat and fish (the Hearthkeeper's fourth). */
	static boolean raw(ItemStack stack) {
		if (stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.CHICKEN) || stack.is(Items.MUTTON) || stack.is(Items.RABBIT)
			|| stack.is(Items.COD) || stack.is(Items.SALMON)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.startsWith("raw_") && stack.has(DataComponents.FOOD);
	}

	/** (From the food mixin) {@code eater} ate {@code stack}. */
	public static void ate(LivingEntity eater, ItemStack stack) {
		if (eater instanceof ServerPlayer sp) {
			if (rot(stack)) {
				act(sp, "eat_rot");
			}
			if (raw(stack)) {
				act(sp, "eat_raw");
			}
			// (0.7.581) meat eaten pays for a beast killed
			PatronWrath.ate(sp, stack);
		}
	}

	/** (From the wear mixin) a tool in {@code player}'s hand took {@code wear} and breaks. */
	public static void wore(ItemStack stack, ServerPlayer player, int wear) {
		if (wear > 0 && stack.isDamageableItem() && stack.getDamageValue() + wear >= stack.getMaxDamage()
			&& (stack == player.getMainHandItem() || stack == player.getOffhandItem())) {
			act(player, "tool_broke");
		}
	}

	/** (From the explosion mixin) a blast that breaks blocks, set off by {@code by} (or what they lit or threw). */
	public static void blast(@Nullable LivingEntity by, boolean breaks) {
		if (breaks && by instanceof ServerPlayer sp) {
			act(sp, "explode");
		}
	}

	private static boolean villagerLike(Entity e) {
		return e instanceof Villager || e instanceof Folk;
	}

	/** Armed: a blade, an axe, a spear or a mace, a bow or a crossbow in hand. */
	private static boolean armed(LivingEntity e) {
		ItemStack hand = e.getMainHandItem();
		return hand.has(DataComponents.WEAPON) || hand.getItem() instanceof BowItem || hand.getItem() instanceof CrossbowItem;
	}

	/** Innocent of {@code by}: a villager or a townsman, unarmed, no criminal, outlaw or blood-drinker, not at {@code by}. */
	static boolean innocent(LivingEntity victim, @Nullable Entity by) {
		if (!villagerLike(victim) || Crooks.fairGame(victim, by) || Outlaws.is(victim) || Vampires.isVampire(victim)) {
			return false;
		}
		if (victim instanceof Mob m && by != null && m.getTarget() == by || by != null && victim.getLastHurtByMob() == by) {
			return false;
		}
		return victim.isBaby() || !armed(victim);
	}

	private static void died(LivingEntity victim, DamageSource source) {
		if (victim instanceof ServerPlayer dead) {
			Entity killer = source.getEntity();
			if (killer == null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				act(dead, "careless_death");
				if (source.is(DamageTypeTags.IS_FALL)) {
					act(dead, "fell");
				}
				if (source.is(DamageTypeTags.IS_DROWNING)) {
					act(dead, "drowned");
				}
			}
			if (killer instanceof LivingEntity l && l.is(EntityTypeTags.UNDEAD)) {
				act(dead, "undead_death");
			}
		}
		if (!(source.getEntity() instanceof ServerPlayer sp)) {
			return;
		}
		if (villagerLike(victim)) {
			if (victim.isBaby()) {
				act(sp, "kill_child");
			} else if (innocent(victim, sp)) {
				act(sp, "kill_innocent");
			}
		}
		if (victim instanceof TamableAnimal t && t.isTame() || victim instanceof AbstractHorse h && h.isTamed()) {
			act(sp, "kill_tame");
		}
		if (victim instanceof Animal a) {
			if (a.isBaby()) {
				act(sp, "kill_young");
			}
			if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) {
				act(sp, "kill_by_fire");
			}
		}
		if (victim instanceof net.minecraft.world.entity.animal.dolphin.Dolphin || victim instanceof net.minecraft.world.entity.animal.turtle.Turtle) {
			act(sp, "kill_sea_kin");
		}
		if (victim.isSleeping()) {
			act(sp, "kill_asleep");
		}
	}

	private static void struck(LivingEntity victim, DamageSource source) {
		if (victim instanceof ServerPlayer hurt && source.is(DamageTypeTags.IS_FIRE)) {
			act(hurt, "fire_hurt");
		}
		if (!(source.getEntity() instanceof ServerPlayer sp) || victim == sp) {
			return;
		}
		if (Rulers.is(victim)) {
			act(sp, "hit_ruler");
		}
		if (villagerLike(victim) && victim.isBaby()) {
			act(sp, "hurt_child");
		}
		if (victim instanceof Villager v) {
			if (McaCompat.family(v, sp)) {
				act(sp, "hit_family");
			}
			var job = v.getVillagerData().profession();
			if (job.is(VillagerProfession.ARMORER) || job.is(VillagerProfession.TOOLSMITH) || job.is(VillagerProfession.WEAPONSMITH)) {
				act(sp, "smith_struck");
			}
		}
		if (source.getDirectEntity() == sp) {
			ItemStack hand = sp.getMainHandItem();
			if (hand.is(Items.WOODEN_SWORD) || hand.is(Items.WOODEN_AXE) || BuiltInRegistries.ITEM.getKey(hand.getItem()).getPath().startsWith("wooden_")
				&& hand.has(DataComponents.WEAPON)) {
				act(sp, "wooden_weapon");
			}
			if (!(victim instanceof Mob m && m.getTarget() == sp) && behind(victim, sp)) {
				act(sp, "sneak_attack");
			}
		}
	}

	/** {@code by} stands behind {@code victim} (more than 120 degrees off the way its body faces). */
	private static boolean behind(LivingEntity victim, Entity by) {
		double yaw = Math.toRadians(victim.yBodyRot);
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		double dx = by.getX() - victim.getX(), dz = by.getZ() - victim.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		return len > 1.0E-3 && (fx * dx + fz * dz) / len < -0.5;
	}

	private static boolean lightBlock(BlockState state) {
		return state.getBlock() instanceof TorchBlock || state.getBlock() instanceof LanternBlock || state.is(Blocks.JACK_O_LANTERN)
			|| state.getBlock() instanceof CandleBlock && state.getValue(CandleBlock.LIT)
			|| state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
	}

	private static void broken(Player player, BlockState state) {
		if (lightBlock(state)) {
			act(player, "break_light");
		}
		if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)
			|| state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT)) {
			act(player, "break_hearth");
		}
	}

	/** Fire set with flint and steel or a fire charge to anything but a campfire, a candle, a portal frame or a charge. */
	private static void lit(ServerPlayer sp, ItemStack hand, BlockState at) {
		if (!hand.is(Items.FLINT_AND_STEEL) && !hand.is(Items.FIRE_CHARGE)) {
			return;
		}
		Block b = at.getBlock();
		if (b instanceof CampfireBlock || b instanceof CandleBlock || at.is(Blocks.OBSIDIAN) || at.is(Blocks.TNT) || at.is(Blocks.NETHERRACK)) {
			return;
		}
		act(sp, "wildfire");
	}

	/** A patron's book of commandments or its lore book, thrown away. */
	private static void thrown(ServerPlayer sp, ItemStack stack) {
		Patron mine = Patrons.of(sp);
		if (mine == null) {
			return;
		}
		Patron of = stack.is(ITEM) ? patronOf(stack) : null;
		String lore = LoreBooks.idOf(stack);
		if (of == mine || lore.equals("jaymoskingdoms:patron_" + mine.key)) {
			act(sp, "book");
		}
	}

	// ---- every two seconds ----

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % TICK_EVERY != 0) {
			return;
		}
		long clock = server.overworld().getOverworldClockTime();
		long time = Math.floorMod(clock, Shrine.DAY);
		long today = Patrons.day(clock);
		boolean night = Vampires.night(clock);
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			if (!sp.isAlive() || sp.isSpectator()) {
				continue;
			}
			boolean nether = Stacked.in(sp, Level.NETHER);
			boolean sky = sky(sp);
			followers(sp, night && !nether, nether, sky);
			Patron p = Patrons.of(sp);
			if (p == null || sp.isCreative()) {
				continue;
			}
			if (nether) {
				act(sp, "nether");
			}
			if (sky) {
				act(sp, "sky");
			}
			ServerLevel level = sp.level();
			BlockPos pos = sp.blockPosition();
			// the night slept (or not): on at midnight, still up at dawn
			if (time >= 18000 && time < 18000 + TICK_EVERY * 2) {
				MIDNIGHT.put(sp.getUUID(), today);
			}
			if (time < 1000 && MIDNIGHT.getOrDefault(sp.getUUID(), Long.MIN_VALUE) == today - 1) {
				MIDNIGHT.remove(sp.getUUID());
				if (sp.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) >= AWAKE_NIGHT) {
					act(sp, "no_sleep");
				}
			}
			// the sea
			boolean ocean = level.getBiome(pos).is(BiomeTags.IS_OCEAN);
			if (ocean && (com.jaymoskingdoms.sailing.Ships.aboard(sp) != null || sp.getVehicle() instanceof AbstractBoat)) {
				act(sp, "ocean");
			}
			if (ocean && sp.isUnderWater()) {
				act(sp, "sea_swim");
			}
			Kept k = state(sp);
			if (sp.isInWaterOrRain()) {
				if (k.wet < clock - Shrine.DAY / 4 || k.wet > clock) {
					sp.setAttached(KEPT, new Kept(k.patron, k.broken, k.day, k.today, clock, k.night));
				}
			} else if (k.wet < 0) {
				sp.setAttached(KEPT, new Kept(k.patron, k.broken, k.day, k.today, clock, k.night));
			} else if (clock - k.wet >= DRY_DAYS * Shrine.DAY) {
				act(sp, "dry");
			}
			// the dark
			if (!lightInHand(sp) && level.getBrightness(LightLayer.BLOCK, pos) <= DARK_LIGHT && !nether) {
				boolean open = level.canSeeSky(pos);
				if (open && night) {
					act(sp, "dark_walk");
				} else if (!open && pos.getY() < BELOW_Y) {
					act(sp, "dark_below");
				}
			}
			if (!night && sp.hasEffect(MobEffects.INVISIBILITY)) {
				act(sp, "invisible");
			}
		}
	}

	/** In the sky: the Aether (its own world or its band), or flying on wings. */
	static boolean sky(Player player) {
		return TempleSites.aether(Stacked.realm(player)) || player.isFallFlying();
	}

	private static boolean lightInHand(Player player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().getLightEmission() >= 10) {
				return true;
			}
		}
		return false;
	}

	// ---- the Truth's folk keep its law ----

	/** {@code e} holds The Truth. */
	public static boolean truth(Entity e) {
		return Faiths.of(e) == Patron.TRUTH;
	}

	/** A Truth's follower says why not, hotly (to {@code player}; at most every {@link #REFUSE_GAP} ticks each). */
	static void refuse(LivingEntity who, @Nullable ServerPlayer player, int n) {
		if (!(who.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		Long last = SAID.get(who.getUUID());
		if (last != null && now - last < REFUSE_GAP) {
			return;
		}
		SAID.put(who.getUUID(), now);
		Component line = Component.translatable("commandment.jaymoskingdoms.refuse." + n + "." + who.getRandom().nextInt(LINES));
		if (player != null) {
			Speech.tell(level, who, player, line, Speech.RED);
		} else {
			Speech.say(level, who, line, Speech.RED, 16.0);
		}
		level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.9F);
	}

	/**
	 * (From every mob's {@code canAttack}) A Truth villager or townsman never goes for an innocent, whoever set them on it.
	 * If they were following someone, they tell that someone so.
	 */
	public static boolean spares(LivingEntity self, LivingEntity target) {
		if (!villagerLike(target) || !(self instanceof Mob) || self.level().isClientSide() || !Faiths.holds(self) || !innocent(target, self)) {
			return false;
		}
		if (!truth(self)) {
			return false;
		}
		if (self instanceof Villager v && McaCompat.following(v) instanceof UUID id && self.level().getPlayerByUUID(id) instanceof ServerPlayer leader) {
			refuse(self, leader, INNOCENT);
		}
		return true;
	}

	/** (From MCA's command handler) an order a Truth villager won't take: following at night, into the Nether or the sky; rot as a gift. */
	public static boolean refusesCommand(Villager villager, ServerPlayer player, String command) {
		if (!truth(villager)) {
			return false;
		}
		int n = -1;
		if (command.equals("follow") || command.equals("ridehorse")) {
			if (Stacked.in(player, Level.NETHER) || Stacked.in(villager, Level.NETHER)) {
				n = NETHER;
			} else if (sky(player)) {
				n = SKY;
			} else if (Vampires.night(player.level().getServer().overworld().getOverworldClockTime())) {
				n = NIGHT;
			}
		} else if (command.equals("gift") && rot(player.getMainHandItem())) {
			n = ROT;
		}
		if (n < 0) {
			return false;
		}
		SAID.remove(villager.getUUID());
		refuse(villager, player, n);
		return true;
	}

	/** Truth villagers following {@code player} leave them at nightfall, in the Nether or up in the sky. */
	private static void followers(ServerPlayer player, boolean night, boolean nether, boolean sky) {
		if (!night && !nether && !sky) {
			return;
		}
		List<Villager> near = player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(FOLLOW_RANGE),
			v -> v.isAlive() && player.getUUID().equals(McaCompat.following(v)));
		for (Villager v : near) {
			if (v.getTarget() != null || !truth(v)) {
				continue;
			}
			McaCompat.stopFollowing(v);
			SAID.remove(v.getUUID());
			refuse(v, player, nether ? NETHER : sky ? SKY : NIGHT);
		}
	}

	// ---- the book ----

	/** The book of {@code p}'s commandments. */
	public static ItemStack book(Patron p) {
		ItemStack stack = new ItemStack(ITEM);
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, p.key);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** The patron of a book of commandments (null: not one, or blank). */
	public static @Nullable Patron patronOf(ItemStack stack) {
		if (!stack.is(ITEM)) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : Patron.byKey(data.copyTag().getStringOr(KEY, ""));
	}

	/** {@code sp} has a book of {@code p}'s commandments on them. */
	static boolean carries(ServerPlayer sp, Patron p) {
		var inv = sp.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (patronOf(inv.getItem(i)) == p) {
				return true;
			}
		}
		return false;
	}

	/** Sworn to {@code p}: their book, if they don't have one on them; and the count starts fresh. */
	static void sworn(ServerPlayer sp, Patron p) {
		Kept k = state(sp);
		if (!k.patron.equals(p.key)) {
			sp.setAttached(KEPT, new Kept(p.key, 0, -1L, 0, k.wet, k.night));
		}
		if (!carries(sp, p)) {
			ItemStack book = book(p);
			if (!sp.getInventory().add(book)) {
				sp.spawnAtLocation(sp.level(), book);
			}
		}
	}

	/** One item for every patron's book: its name and what opens are its patron's. */
	static final class Book extends Item {
		Book(Properties properties) {
			super(properties);
		}

		@Override
		public Component getName(ItemStack stack) {
			Patron p = patronOf(stack);
			return p == null ? super.getName(stack) : Component.translatable("item.jaymoskingdoms.commandments.of", p.named());
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			Patron p = patronOf(player.getItemInHand(hand));
			if (p == null) {
				p = Patrons.of(player);
			}
			if (p == null) {
				p = Patron.TRUTH;
			}
			if (level.isClientSide()) {
				open.accept(p.key);
			} else {
				level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
	}
}
