package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.jaymoskingdoms.money.Money;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Deck of Fates: a thin deck of painted cards in a box of black wood. It is drawn once, from one to three cards (the
 * player names how many), and the deck is gone with the last (the cards "fly up and vanish" as in the old tales). What is
 * drawn is not chosen and cannot be put back; it happens at once: a keep and its servants, a hunter on your track, every coin
 * you carry gone, a wish, a new calling, a lost memory, a death knight to duel, a jester who gives another draw, an old relic,
 * a dark moon, the sun, a gate. Some are good, some bad; the player knew the odds when they opened the box.
 */
public final class DeckOfFates {
	public static Item ITEM, DEED;
	/** The most cards one may draw, and the chance weights of the cards. */
	static final int MOST = 3;
	/** The death knight's added health, its reward, and how far a player may stray from the duel (blocks). */
	static final double KNIGHT_HEALTH = 60.0;
	static final int KNIGHT_RANGE = 48;
	/** The wish's boons: extra hearts' worth of health, coins, learning, and standing. */
	static final double WISH_HEALTH = 10.0;
	static final int WISH_COINS = 200, WISH_LEARNING = 800, WISH_STANDING = 30;
	/** How far off the keep is raised (blocks), and how far the gate throws (blocks). */
	static final int KEEP_AHEAD = 48, GATE = 300;
	static final String KNIGHT_TAG = "jaymos_death_knight";

	private enum Card {
		THRONE(3), NEMESIS(3), RUIN(2), WISH(2), MIND(3), AMNESIA(3), KNIGHT(3), JESTER(3), RELIC(4), DARK_MOON(3), SUN(4), GATE(3);

		final int weight;

		Card(int weight) {
			this.weight = weight;
		}

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private record Duel(ServerBossEvent bar, UUID player) {
	}

	private static final Map<UUID, Duel> DUELS = new HashMap<>();

	private DeckOfFates() {
	}

	static void register() {
		ITEM = Gadgets.item("deck_of_fates", Rarity.EPIC, DeckItem::new);
		DEED = Gadgets.item("keep_deed", Rarity.RARE, DeedItem::new);
		ServerTickEvents.END_SERVER_TICK.register(DeckOfFates::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DUELS.clear());
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Duel duel = DUELS.remove(entity.getUUID());
			if (duel != null && entity.level() instanceof ServerLevel level) {
				duel.bar().removeAllPlayers();
				ItemStack relic = Relics.found(level, level.getRandom());
				if (!relic.isEmpty()) {
					ServerPlayer winner = level.getServer().getPlayerList().getPlayer(duel.player());
					if (winner != null) {
						winner.getInventory().placeItemBackInInventory(relic, net.minecraft.util.Prediction.SERVER_ONLY);
						winner.sendSystemMessage(Component.translatable("deck.jaymoskingdoms.knight.won"), true);
					} else {
						entity.spawnAtLocation(level, relic);
					}
				}
			}
		});
	}

	private static void tick(MinecraftServer server) {
		if (DUELS.isEmpty() || server.getTickCount() % 10 != 0) {
			return;
		}
		DUELS.entrySet().removeIf(e -> {
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(e.getKey()) instanceof WitherSkeleton knight && knight.isAlive()) {
					ServerPlayer player = server.getPlayerList().getPlayer(e.getValue().player());
					if (player == null || player.level() != level || player.distanceTo(knight) > KNIGHT_RANGE) {
						// the one who drew it left: the knight goes back to the card
						knight.discard();
						e.getValue().bar().removeAllPlayers();
						return true;
					}
					e.getValue().bar().setProgress(knight.getHealth() / knight.getMaxHealth());
					e.getValue().bar().addPlayer(player);
					return false;
				}
			}
			e.getValue().bar().removeAllPlayers();
			return true;
		});
	}

	// ---- the draw --------------------------------------------------------------------------------------------------------

	static final class DeckItem extends Item {
		DeckItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!ItemBonds.works(sp, stack)) {
				return InteractionResult.PASS;
			}
			List<Menus.Choice> choices = new ArrayList<>();
			for (int n = 1; n <= MOST; n++) {
				int cards = n;
				choices.add(Menus.Choice.of(Component.translatable("deck.jaymoskingdoms.draw." + n), p -> {
					ItemStack deck = findDeck(p);
					if (deck.isEmpty()) {
						return;
					}
					deck.shrink(1);
					draw(p, cards);
				}));
			}
			choices.add(Menus.Choice.of(Component.translatable("deck.jaymoskingdoms.draw.none"), p -> {
			}));
			Menus.ask(sp, sp, Component.translatable("deck.jaymoskingdoms.ask"), choices);
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.deck_of_fates").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.deck_of_fates.warn").withStyle(ChatFormatting.DARK_RED));
		}
	}

	private static ItemStack findDeck(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ITEM)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Draws {@code count} cards for {@code player}, one after another. */
	static void draw(ServerPlayer player, int count) {
		ServerLevel level = player.level();
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.6F);
		Fx.sigil(level, player.position(), 2.5F, 0xB080E0, 40);
		int left = count;
		while (left-- > 0) {
			Card card = pick(player);
			player.sendSystemMessage(Component.translatable("deck.jaymoskingdoms.card", Component.translatable("deck.jaymoskingdoms.card." + card.key())).withStyle(ChatFormatting.LIGHT_PURPLE));
			if (card == Card.JESTER) {
				left += 2;
				player.sendSystemMessage(Component.translatable("deck.jaymoskingdoms.jester"));
				continue;
			}
			play(player, level, card);
		}
	}

	private static Card pick(ServerPlayer player) {
		int total = 0;
		for (Card c : Card.values()) {
			total += c.weight;
		}
		int roll = player.getRandom().nextInt(total);
		for (Card c : Card.values()) {
			roll -= c.weight;
			if (roll < 0) {
				return c;
			}
		}
		return Card.SUN;
	}

	private static void play(ServerPlayer player, ServerLevel level, Card card) {
		switch (card) {
			case THRONE -> throne(player, level);
			case NEMESIS -> Nemesis.mark(player);
			case RUIN -> {
				long coin = Money.count(player);
				if (coin > 0) {
					Money.charge(player, coin);
				}
			}
			case WISH -> wish(player);
			case MIND -> mind(player);
			case AMNESIA -> forget(player);
			case KNIGHT -> knight(player, level);
			case RELIC -> {
				ItemStack relic = Relics.found(level, player.getRandom());
				if (!relic.isEmpty()) {
					player.getInventory().placeItemBackInInventory(relic, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
			case DARK_MOON -> {
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 600, 0));
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 0));
			}
			case SUN -> {
				player.setHealth(player.getMaxHealth());
				player.getFoodData().setFoodLevel(20);
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1));
				player.giveExperienceLevels(10);
				Fx.burst(level, player.getX(), player.getY() + 1, player.getZ(), 0xFFE070, 1.0F, 30, 0.6, 0.8, 0.6);
			}
			case GATE -> gate(player, level);
			default -> {
			}
		}
	}

	/** A keep with its servants, raised where you look; if the land will not have one (a realm's, or too near a capital), its deed. */
	private static void throne(ServerPlayer player, ServerLevel level) {
		MinecraftServer server = level.getServer();
		BlockPos ahead = player.blockPosition().relative(player.getDirection(), KEEP_AHEAD);
		BlockPos at = new BlockPos(ahead.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, ahead.getX(), ahead.getZ()), ahead.getZ());
		boolean built = false;
		if (Factions.realmNear(server, at, KingdomEvents.CAPITAL_SPACING) == null && Factions.near(server, at, KingdomEvents.TOWN_RADIUS) == null) {
			try {
				Faction kingdom = KingdomEvents.found(level, at, Castle.Kind.KEEP);
				if (kingdom != null) {
					Crowns.crown(server, kingdom, player.getUUID());
					player.sendSystemMessage(Component.translatable("deck.jaymoskingdoms.throne.built", kingdom.name()));
					built = true;
				}
			} catch (RuntimeException e) {
				JaymosKingdoms.LOGGER.warn("The Deck's keep could not be raised at {}", at, e);
			}
		}
		if (!built) {
			player.getInventory().placeItemBackInInventory(new ItemStack(DEED), net.minecraft.util.Prediction.SERVER_ONLY);
			player.sendSystemMessage(Component.translatable("deck.jaymoskingdoms.throne.deed"));
		}
	}

	/** The wish: four things to ask for, one asked. */
	private static void wish(ServerPlayer player) {
		List<Menus.Choice> choices = new ArrayList<>();
		choices.add(Menus.Choice.of(Component.translatable("deck.jaymoskingdoms.wish.life"), p -> {
			var health = p.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.addOrReplacePermanentModifier(new AttributeModifier(JaymosKingdoms.id("deck_wish_life"), WISH_HEALTH, AttributeModifier.Operation.ADD_VALUE));
			}
			p.setHealth(p.getMaxHealth());
		}));
		choices.add(Menus.Choice.of(Component.translatable("deck.jaymoskingdoms.wish.riches"), p -> Money.pay(p, Money.worth(WISH_COINS))));
		choices.add(Menus.Choice.of(Component.translatable("deck.jaymoskingdoms.wish.learning"), p -> {
			for (Skills.Pillar pillar : Skills.Pillar.values()) {
				Skills.grant(p, pillar, WISH_LEARNING / 4);
			}
		}));
		choices.add(Menus.Choice.of(Component.translatable("deck.jaymoskingdoms.wish.mercy"), p -> {
			Faction near = Factions.near(p.level().getServer(), p.blockPosition(), 256);
			if (near != null) {
				Reputation.change(p, near, WISH_STANDING);
			}
		}));
		Menus.ask(player, player, Component.translatable("deck.jaymoskingdoms.wish.ask"), choices);
	}

	/** A new calling, not the one you had. */
	private static void mind(ServerPlayer player) {
		Classes.PlayerClass now = Classes.classOf(player);
		List<Classes.PlayerClass> others = new ArrayList<>();
		for (Classes.PlayerClass cls : Classes.PlayerClass.values()) {
			if (cls != now) {
				others.add(cls);
			}
		}
		Classes.PlayerClass next = others.get(player.getRandom().nextInt(others.size()));
		Classes.set(player, next, next.options.isEmpty() ? "" : next.options.get(player.getRandom().nextInt(next.options.size())));
		player.sendSystemMessage(Component.translatable("deck.jaymoskingdoms.mind.now", Component.translatable("class.jaymoskingdoms." + next.key())));
	}

	/** What you knew of your foes and the tongues you had is gone. */
	private static void forget(ServerPlayer player) {
		player.removeAttached(FoeStudy.FOUGHT);
		player.removeAttached(FoeStudy.TAUGHT);
		player.removeAttached(FoeStudy.LEARNED);
		player.removeAttached(Languages.LEARNED);
	}

	/** A death knight rises to duel you; its bar shows, and a relic falls to the one who fells it. */
	private static void knight(ServerPlayer player, ServerLevel level) {
		WitherSkeleton knight = EntityTypes.WITHER_SKELETON.create(level, EntitySpawnReason.EVENT);
		if (knight == null) {
			return;
		}
		BlockPos at = player.blockPosition().relative(player.getDirection(), 6);
		knight.snapTo(at.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ() + 0.5, 0.0F, 0.0F);
		knight.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		knight.addTag(KNIGHT_TAG);
		knight.setCustomName(Component.translatable("deck.jaymoskingdoms.knight.name"));
		knight.setCustomNameVisible(true);
		knight.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		knight.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
		knight.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
		knight.setPersistenceRequired();
		var health = knight.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.addOrReplacePermanentModifier(new AttributeModifier(JaymosKingdoms.id("death_knight_health"), KNIGHT_HEALTH, AttributeModifier.Operation.ADD_VALUE));
		}
		knight.setHealth(knight.getMaxHealth());
		knight.setTarget(player);
		level.addFreshEntity(knight);
		ServerBossEvent bar = new ServerBossEvent(knight.getUUID(), Component.translatable("deck.jaymoskingdoms.knight.name"), BossEvent.BossBarColor.PURPLE,
			BossEvent.BossBarOverlay.NOTCHED_10);
		bar.addPlayer(player);
		DUELS.put(knight.getUUID(), new Duel(bar, player.getUUID()));
		level.playSound(null, at, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6F, 1.4F);
	}

	/** The gate: thrown to some far place on the same ground. */
	private static void gate(ServerPlayer player, ServerLevel level) {
		double angle = player.getRandom().nextDouble() * Math.PI * 2;
		int x = (int)(player.getX() + Math.cos(angle) * GATE), z = (int)(player.getZ() + Math.sin(angle) * GATE);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		Fx.burst(level, player.getX(), player.getY() + 1, player.getZ(), 0x8060E0, 1.0F, 30, 0.5, 0.8, 0.5);
		player.teleportTo(x + 0.5, y, z + 0.5);
		Fx.burst(level, x + 0.5, y + 1, z + 0.5, 0x8060E0, 1.0F, 30, 0.5, 0.8, 0.5);
	}

	/** The deed to a keep: use it where you stand and a keep rises, if the land will bear one. */
	static final class DeedItem extends Item {
		DeedItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			MinecraftServer minecraft = server.getServer();
			BlockPos at = sp.blockPosition();
			if (Factions.realmNear(minecraft, at, KingdomEvents.CAPITAL_SPACING) != null || Factions.near(minecraft, at, KingdomEvents.TOWN_RADIUS) != null) {
				sp.sendSystemMessage(Component.translatable("deck.jaymoskingdoms.deed.crowded"), true);
				return InteractionResult.FAIL;
			}
			Faction kingdom = KingdomEvents.found(server, at, Castle.Kind.KEEP);
			if (kingdom == null) {
				return InteractionResult.FAIL;
			}
			Crowns.crown(minecraft, kingdom, sp.getUUID());
			player.getItemInHand(hand).shrink(1);
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.keep_deed").withStyle(ChatFormatting.GRAY));
		}
	}
}
