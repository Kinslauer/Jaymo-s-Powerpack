package com.jaymoskingdoms;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.0) Player classes: what you're good with. The first time you come into a world a screen asks you to pick one of
 * nine (Thief, Warlord, Mage, Archer, Survivalist, Brute, Marksman, Sorcerer, Rider); the pick is for keeps, unless you find a
 * Writ of Rebirth ({@link #WRIT}: rare chest loot, or a court wizard sells one a week), which unmakes it and asks again.
 * A class sits beside a power and beside the skill tree ({@link Skills}): the class is what you're good with, the tree
 * how far you've practised it.
 *
 * <p>Every class's numbers live in {@link ClassPerks}. Gear is recognised by item tags ({@code #jaymoskingdoms:light_blades},
 * {@code two_handed}, {@code bludgeons}, {@code guns}, {@code light_armor}, {@code heavy_armor}), so every mod's weapons
 * count. Heavy armour weighs on everyone a little since classes came ({@link ClassPerks#HEAVY_ARMOR_SLOW}); the
 * Warlord feels half of it, the Brute twice.
 *
 * <p>A Mage picks a first spell and a Sorcerer an element on the same screen; those come into their own with the
 * casting (stage 2). Ops: {@code /kingdom class set <players> <class>}, {@code /kingdom class clear <players>},
 * {@code /kingdom class get <player>}.
 */
public final class Classes {
	/** How often a court wizard will sell you a Writ of Rebirth (ticks): once a Minecraft week. */
	static final long WRIT_EVERY = 24000L * 7L;
	/** What he asks for one. */
	static final int WRIT_EMERALDS = 30;
	/** The chance a rare chest (stronghold library, ancient city, woodland mansion) holds a writ. */
	static final float WRIT_LOOT_CHANCE = 0.08F;
	/** The chance a rare chest holds a wizard's staff (a Mage's "find one"); a slain court wizard drops his this often. */
	static final float STAFF_LOOT_CHANCE = 0.1F, STAFF_DROP_CHANCE = 0.5F;

	/** The eight classes. {@code options}: what the class picks besides (a Mage's first spell, a Sorcerer's element). */
	public enum PlayerClass {
		THIEF, WARLORD, MAGE("fire", "arcane", "warding", "mending"), ARCHER, SURVIVALIST, BRUTE, MARKSMAN,
		SORCERER("water", "fire", "earth", "air", "lightning", "ice", "restoration", "death", "blood"), RIDER;

		public final List<String> options;

		PlayerClass(String... options) {
			this.options = List.of(options);
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static @Nullable PlayerClass byKey(String key) {
			for (PlayerClass c : values()) {
				if (c.key().equals(key)) {
					return c;
				}
			}
			return null;
		}
	}

	/**
	 * A player's class, what they picked with it ("" for nothing), the spells a Mage knows (the first is what they picked)
	 * and which of them is on their staff.
	 */
	public record Chosen(String cls, String option, List<String> spells, int selected) {
		public static final Chosen NONE = new Chosen("", "", List.of(), 0);
		static final Codec<Chosen> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("class", "").forGetter(Chosen::cls),
			Codec.STRING.optionalFieldOf("option", "").forGetter(Chosen::option),
			Codec.STRING.listOf().optionalFieldOf("spells", List.of()).forGetter(Chosen::spells),
			Codec.INT.optionalFieldOf("selected", 0).forGetter(Chosen::selected)
		).apply(i, Chosen::new));
		static final StreamCodec<RegistryFriendlyByteBuf, Chosen> STREAM_CODEC = StreamCodec.of(
			(buf, c) -> {
				buf.writeUtf(c.cls);
				buf.writeUtf(c.option);
				buf.writeVarInt(c.spells.size());
				for (String s : c.spells) {
					buf.writeUtf(s);
				}
				buf.writeVarInt(c.selected);
			}, buf -> {
				String cls = buf.readUtf(), option = buf.readUtf();
				int n = buf.readVarInt();
				List<String> spells = new java.util.ArrayList<>();
				for (int i = 0; i < n; i++) {
					spells.add(buf.readUtf());
				}
				return new Chosen(cls, option, List.copyOf(spells), buf.readVarInt());
			});

		/** The spell on the staff, or "" with none. */
		public String spell() {
			return this.spells.isEmpty() ? "" : this.spells.get(Math.floorMod(this.selected, this.spells.size()));
		}

		public @Nullable PlayerClass playerClass() {
			return PlayerClass.byKey(this.cls);
		}
	}

	/** Each player's class. Saved, kept through death, synced to its own player (the screens). */
	public static final AttachmentType<Chosen> CLASS = AttachmentRegistry.create(JaymosKingdoms.id("class"),
		builder -> builder.persistent(Chosen.CODEC).copyOnDeath().syncWith(Chosen.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	/** When a player last bought a writ from a court wizard (game time). Saved, kept through death. */
	static final AttachmentType<Long> WRIT_BOUGHT = AttachmentRegistry.create(JaymosKingdoms.id("writ_bought"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** Server → player: pick a class (the screen opens; {@code forced}: it can't be shut without picking). */
	public record Open(boolean forced) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("class_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.forced), buf -> new Open(buf.readBoolean()));

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Player → server: they picked class {@code cls} (its ordinal) and {@code option} (an index into its options, or -1). */
	public record Choose(int cls, int option) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = new Type<>(JaymosKingdoms.id("class_choose"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.cls);
				buf.writeVarInt(p.option + 1);
			}, buf -> new Choose(buf.readVarInt(), buf.readVarInt() - 1));

		@Override
		public Type<Choose> type() {
			return TYPE;
		}
	}

	public static Item WRIT;

	/** Player → the court wizard who offered them a writ (entity id). */
	private static final Map<UUID, Integer> WRIT_OFFERS = new HashMap<>();

	/** (0.7.45) While a player makes their character, mobs within this range that go for them are called off, this often (ticks). */
	static final int UNMADE_SHOO_RANGE = 32, UNMADE_SHOO_EVERY = 10;

	private Classes() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("writ_of_rebirth"));
		WRIT = Registry.register(BuiltInRegistries.ITEM, key, new Writ(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Choose.TYPE, Choose.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Choose.TYPE, (payload, context) -> choose(context.player(), payload));
		ServerPlayerEvents.JOIN.register(player -> {
			refresh(player);
			// (0.7.45) Andy: "force character creation on spawn". MCA's destiny screen (your look, where you start) comes
			// first and can't be shut; the class screen follows it ({@link #afterDestiny}) and can't be shut either.
			// (0.7.57) the heritage screen ({@link Heritage}) sits between them
			// (0.7.229) Andy: "can you choose your characters story BEFORE the MCA creator and name choosing?" a newcomer's
			// life story opens at once; MCA's creator waits for it on their client (McaDestinyWaitMixin) and comes after
			if (LifeStory.owed(player) || !McaCompat.destinyPending(player)) {
				next(player);
			}
		});
		// (0.7.45) until both are done the player can't be hurt and nothing hunts them
		// (0.7.229: and while MCA's creator is still up after the story put them somewhere, a cave or a camp's woods)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer sp && (unmade(sp) || McaCompat.destinyPending(sp) || StoryStasis.holding(sp))
			&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refresh(newPlayer));
		// (what's held and worn changes what a class is good with)
		ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previous, next) -> {
			if (entity instanceof ServerPlayer sp) {
				refresh(sp);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Classes::tick);
		Spellcraft.register();
		if (Javelins.WEAPONMOD_LOADED) {
			WeaponModGuns.register();
		}
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WRIT_OFFERS.clear();
			OFFER_KINDS.clear();
			ClassPerks.clear();
			Spellcraft.clear();
		});
		// a writ, or a wizard's staff, in the rarest chests
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (table.unwrapKey().filter(k -> k.equals(BuiltInLootTables.STRONGHOLD_LIBRARY) || k.equals(BuiltInLootTables.ANCIENT_CITY)
				|| k.equals(BuiltInLootTables.WOODLAND_MANSION)).isPresent()) {
				if (context.getRandom().nextFloat() < WRIT_LOOT_CHANCE) {
					drops.add(new ItemStack(WRIT));
				}
				if (context.getRandom().nextFloat() < STAFF_LOOT_CHANCE) {
					drops.add(new ItemStack(Arcana.WIZARD_STAFF));
				}
				// (0.7.212) and, rarely, the strongest of the healing scrolls
				if (context.getRandom().nextFloat() < Restoration.SCROLL_LOOT_CHANCE) {
					drops.add(new ItemStack(Arcana.SCROLL_RESTORATION));
				}
			}
		});
	}

	private static void tick(MinecraftServer server) {
		ClassPerks.serverTick(server);
		boolean shoo = server.getTickCount() % UNMADE_SHOO_EVERY == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (shoo && (unmade(player) || McaCompat.destinyPending(player))) {
				for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(UNMADE_SHOO_RANGE), m -> m.getTarget() == player)) {
					mob.setTarget(null);
				}
			}
			if (player.isAlive() && !player.isSpectator()) {
				ClassPerks.tick(player);
				Spellcraft.tick(player);
				Mana.tick(player);
				com.jaymoskingdoms.spirits.Spirits.tick(player);
			}
		}
	}

	public static Chosen of(Player player) {
		return player.getAttachedOrElse(CLASS, Chosen.NONE);
	}

	public static @Nullable PlayerClass classOf(Player player) {
		return of(player).playerClass();
	}

	public static boolean is(Player player, PlayerClass cls) {
		return classOf(player) == cls;
	}

	/** Puts {@code player}'s class on them as they stand (their attributes). */
	static void refresh(ServerPlayer player) {
		ClassPerks.attributes(player);
		// (0.7.57) a class gives a free point in its favoured attribute
		Stats.attributes(player);
	}

	/**
	 * (0.7.45) Whether {@code player} is still making their character: no class yet (and their client has this mod,
	 * so the screen is up for them), or MCA's destiny screen still to finish.
	 */
	public static boolean unmade(ServerPlayer player) {
		return classOf(player) == null && ServerPlayNetworking.canSend(player, Open.TYPE) || Heritage.of(player) == null && Heritage.canAsk(player);
	}

	/** (0.7.45) MCA's destiny screen was just finished: (0.7.57) the heritage screen is next, then the class screen; neither can be shut without picking. */
	public static void afterDestiny(ServerPlayer player) {
		// (0.7.304) the story told first sets out now, from stasis (StoryStasis)
		LifeStory.depart(player);
		// (0.7.229) a few safe seconds now they're really there
		LifeStory.arrived(player);
		next(player);
	}

	/**
	 * (0.7.57) Puts up the next step of character creation {@code player} still owes: their heritage, then their class.
	 * (0.7.216) A newcomer tells their life story instead ({@link LifeStory}), which makes both at once.
	 */
	static void next(ServerPlayer player) {
		if (LifeStory.owed(player)) {
			LifeStory.ask(player, true);
		} else if (Heritage.of(player) == null && Heritage.canAsk(player)) {
			Heritage.ask(player, true);
		} else if (classOf(player) == null) {
			ask(player, true);
		}
	}

	/** Opens the class screen for {@code player} (if their client has this mod). */
	static void ask(ServerPlayer player, boolean forced) {
		if (ServerPlayNetworking.canSend(player, Open.TYPE)) {
			ServerPlayNetworking.send(player, new Open(forced));
		}
	}

	private static void choose(ServerPlayer player, Choose choose) {
		PlayerClass[] all = PlayerClass.values();
		if (choose.cls() < 0 || choose.cls() >= all.length || classOf(player) != null) {
			return;
		}
		PlayerClass cls = all[choose.cls()];
		String option = choose.option() >= 0 && choose.option() < cls.options.size() ? cls.options.get(choose.option()) : "";
		if (!cls.options.isEmpty() && option.isEmpty()) {
			return;
		}
		set(player, cls, option);
	}

	/** Makes {@code player} a {@code cls}: their attributes, their first things (a Mage's staff), the word of it. */
	static void set(ServerPlayer player, PlayerClass cls, String option) {
		player.setAttached(CLASS, new Chosen(cls.key(), option, cls == PlayerClass.MAGE ? List.of(option) : List.of(), 0));
		refresh(player);
		// (no class starts with gear, Andy's word: a Mage gets the staff's recipe from a court wizard, or finds one)
		if (cls == PlayerClass.MAGE) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.class.mage_staff").withStyle(ChatFormatting.GRAY));
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 0.9F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.class.chosen", Component.translatable("class.jaymoskingdoms." + cls.key()))
			.withStyle(ChatFormatting.GOLD));
	}

	/** Takes {@code player}'s class away (their attributes with it). */
	static void clear(ServerPlayer player) {
		player.removeAttached(CLASS);
		refresh(player);
	}

	// ---- the signature gear --------------------------------------------------------------------------------------

	/**
	 * The gear a class is good with, for its card on the screen (the first of them is its portrait); nobody starts with
	 * any of it. Items of other mods (Armory's dagger, Balkon's musket) are looked up by id, with a vanilla stand-in
	 * when they're not there.
	 */
	public static List<ItemStack> gear(PlayerClass cls, String option) {
		return switch (cls) {
			case THIEF -> List.of(any(1, "jaymospowers:iron_dagger", "minecraft:iron_sword"), new ItemStack(Items.LEATHER_CHESTPLATE),
				new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.LEATHER_BOOTS));
			case WARLORD -> List.of(any(1, "jaymospowers:iron_claymore", "minecraft:iron_axe"), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_BOOTS));
			case MAGE -> List.of(new ItemStack(Arcana.WIZARD_STAFF), new ItemStack(switch (option) {
				case "fire" -> Arcana.SCROLL_FIRE;
				case "warding" -> Arcana.SCROLL_WARDING;
				default -> Arcana.SCROLL_ARCANE;
			}, 3), new ItemStack(Items.LEATHER_HELMET));
			case ARCHER -> List.of(new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 32), new ItemStack(Items.LEATHER_HELMET), new ItemStack(Items.LEATHER_BOOTS));
			case SURVIVALIST -> List.of(any(1, "jaymospowers:hand_axe", "minecraft:stone_axe"), new ItemStack(Items.BREAD, 8), new ItemStack(Items.LEATHER_BOOTS));
			case BRUTE -> List.of(any(1, "jaymospowers:iron_flanged_mace", "minecraft:iron_axe"), new ItemStack(Items.SHIELD), new ItemStack(Items.CHAINMAIL_CHESTPLATE));
			case MARKSMAN -> List.of(any(1, "weaponmod:musket", "minecraft:crossbow"), any(16, "weaponmod:bullet", "minecraft:arrow"),
				new ItemStack(Items.LEATHER_CHESTPLATE));
			case SORCERER -> List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.LEATHER_CHESTPLATE), new ItemStack(Items.LEATHER_LEGGINGS));
			case RIDER -> List.of(any(1, "jaymospowers:iron_lance", "minecraft:iron_sword"), new ItemStack(Items.SADDLE), new ItemStack(Items.LEATHER_CHESTPLATE),
				new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.WHEAT, 8));
		};
	}

	/** {@code n} of the first of {@code ids} that is an item here. */
	private static ItemStack any(int n, String... ids) {
		for (String id : ids) {
			java.util.Optional<net.minecraft.core.Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse(id));
			if (item.isPresent()) {
				return new ItemStack(item.get().value(), n);
			}
		}
		return ItemStack.EMPTY;
	}

	// ---- the writ ------------------------------------------------------------------------------------------------

	/** The Writ of Rebirth: read (right-click), it unmakes your class and asks you to pick again. */
	static final class Writ extends Item {
		Writ(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			if (classOf(sp) == null) {
				ask(sp, true);
				return InteractionResult.SUCCESS;
			}
			sp.getItemInHand(hand).consume(1, sp);
			clear(sp);
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.1F);
			sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.class.reborn").withStyle(ChatFormatting.LIGHT_PURPLE));
			ask(sp, true);
			return InteractionResult.SUCCESS;
		}
	}

	/** The wizard's staff's recipe, which a court wizard teaches a Mage. */
	static final ResourceKey<Recipe<?>> STAFF_RECIPE = ResourceKey.create(Registries.RECIPE, JaymosKingdoms.id("wizard_staff"));

	/** Whether {@code player} is a Mage who hasn't been taught the staff yet. */
	static boolean wantsStaff(ServerPlayer player) {
		return is(player, PlayerClass.MAGE) && !player.getRecipeBook().contains(STAFF_RECIPE);
	}

	/** (0.7.212) What each option of a player's open offer from a court wizard is: staff, writ, a healer's kind, no. */
	private static final Map<UUID, List<String>> OFFER_KINDS = new HashMap<>();

	/**
	 * A player spoke to a court wizard with no perks to unmake: he teaches a Mage how a staff is made, and offers a writ,
	 * if he hasn't sold them one this week. (0.7.212) And his healing: your wounds mended, a Scroll of Mending, and for a
	 * friend of the realm a Scroll of Restoration ({@link Restoration#services}).
	 */
	static void writOffer(ServerPlayer player, LivingEntity wizard) {
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = player.getAttached(WRIT_BOUGHT);
		boolean writ = last == null || now - last >= WRIT_EVERY || now < last;
		boolean staff = wantsStaff(player);
		List<String> kinds = new java.util.ArrayList<>();
		if (staff) {
			kinds.add("staff");
		}
		if (writ) {
			kinds.add("writ");
		}
		kinds.addAll(Restoration.services(player, wizard, true));
		kinds.add("no");
		WRIT_OFFERS.put(player.getUUID(), wizard.getId());
		OFFER_KINDS.put(player.getUUID(), List.copyOf(kinds));
		List<Component> options = new java.util.ArrayList<>();
		List<Boolean> enabled = new java.util.ArrayList<>();
		for (String kind : kinds) {
			switch (kind) {
				case "staff" -> {
					options.add(Component.translatable("talk.jaymoskingdoms.wizard.staff.yes"));
					enabled.add(true);
				}
				case "writ" -> {
					options.add(Component.translatable("talk.jaymoskingdoms.wizard.writ.yes", WRIT_EMERALDS));
					enabled.add(Approaches.emeralds(player) >= WRIT_EMERALDS);
				}
				default -> {
					options.add(Restoration.optionLine(kind));
					enabled.add(Restoration.optionOpen(player, kind));
				}
			}
		}
		Component words = staff ? Component.translatable("talk.jaymoskingdoms.wizard.staff", WRIT_EMERALDS)
			: writ ? Component.translatable("talk.jaymoskingdoms.wizard.writ", WRIT_EMERALDS) : Component.translatable("talk.jaymoskingdoms.healer.ask");
		ServerPlayNetworking.send(player, new TalkNet.Ask(wizard.getId(), wizard.getDisplayName(), words, options, enabled));
	}

	/** An answer to the wizard's offer; false when it wasn't one (0.7.212: then perhaps a cleric's, {@link Restoration#answer}). */
	static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Integer offer = WRIT_OFFERS.get(player.getUUID());
		if (offer == null || offer != answer.entityId()) {
			return Restoration.answer(player, answer);
		}
		WRIT_OFFERS.remove(player.getUUID());
		List<String> kinds = OFFER_KINDS.remove(player.getUUID());
		if (kinds == null || answer.option() < 0 || answer.option() >= kinds.size() || !(player.level().getEntity(answer.entityId()) instanceof LivingEntity wizard)
			|| !CourtWizards.is(wizard) || wizard.distanceTo(player) > 8.0) {
			return true;
		}
		ServerLevel level = (ServerLevel) player.level();
		String kind = kinds.get(answer.option());
		if (kind.equals("staff") && wantsStaff(player)) {
			// the staff's recipe: into the recipe book, and said out loud
			player.awardRecipesByKey(List.of(STAFF_RECIPE));
			Speech.tell(level, wizard, player, Component.translatable("talk.jaymoskingdoms.wizard.staff.done"), Speech.GOLD);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.class.staff_recipe").withStyle(ChatFormatting.LIGHT_PURPLE));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
			return true;
		}
		if (!kind.equals("writ")) {
			Restoration.serve(player, wizard, kind);
			return true;
		}
		if (Approaches.emeralds(player) < WRIT_EMERALDS) {
			return true;
		}
		Long last = player.getAttached(WRIT_BOUGHT);
		long now = level.getGameTime();
		if (last != null && now - last < WRIT_EVERY && now >= last) {
			return true;
		}
		Approaches.take(player, Items.EMERALD, WRIT_EMERALDS);
		player.setAttached(WRIT_BOUGHT, player.level().getGameTime());
		player.getInventory().placeItemBackInInventory(new ItemStack(WRIT), Prediction.SERVER_ONLY);
		Speech.tell(level, wizard, player, Component.translatable("talk.jaymoskingdoms.wizard.writ.done"), Speech.GOLD);
		return true;
	}

	// ---- commands ------------------------------------------------------------------------------------------------

	/** {@code /kingdom class set|clear|get}, for ops. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		var players = Commands.argument("players", EntityArgument.players());
		for (PlayerClass cls : PlayerClass.values()) {
			LiteralArgumentBuilder<CommandSourceStack> name = Commands.literal(cls.key());
			if (cls.options.isEmpty()) {
				name.executes(c -> setCommand(c, cls, ""));
			} else {
				for (String option : cls.options) {
					name.then(Commands.literal(option).executes(c -> setCommand(c, cls, option)));
				}
			}
			players.then(name);
		}
		return Commands.literal("class").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("set").then(players))
			.then(Commands.literal("clear").then(Commands.argument("players", EntityArgument.players()).executes(c -> {
				int n = 0;
				for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
					clear(p);
					ask(p, true);
					n++;
				}
				int count = n;
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.class.cleared", count), true);
				return n;
			})))
			.then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
				ServerPlayer p = EntityArgument.getPlayer(c, "player");
				Chosen chosen = of(p);
				PlayerClass cls = chosen.playerClass();
				c.getSource().sendSuccess(() -> cls == null ? Component.translatable("command.jaymoskingdoms.class.none", p.getDisplayName())
					: Component.translatable("command.jaymoskingdoms.class.is", p.getDisplayName(), Component.translatable("class.jaymoskingdoms." + cls.key()),
						chosen.option().isEmpty() ? "" : " (" + chosen.option() + ")"), false);
				return 1;
			})));
	}

	private static int setCommand(CommandContext<CommandSourceStack> c, PlayerClass cls, String option) throws CommandSyntaxException {
		int n = 0;
		for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
			set(p, cls, option);
			n++;
		}
		int count = n;
		c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.class.set", count, Component.translatable("class.jaymoskingdoms." + cls.key())), true);
		return n;
	}
}
