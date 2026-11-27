package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The willing turning (Night's Blood, patch 6; Andy's plan: "drink the Elder's blood from a bottle under a new moon and you
 * turn at the next nightfall, as if bitten"; then Andy: "you should be able to drink it at anytime, and the next new moon
 * should convert you"). The Elder's Blood ({@link #BLOOD}) is a bottle that comes from the Elder in two
 * ways: beating him down without killing him ({@link #beaten}: whoever downed him finds a bottle where he lies, once in
 * {@link #BLOOD_DAYS} days for each Elder), or bargaining with him in talk ({@link #ANSWER}: only one who has seen enough of
 * the other side, {@link Exposure#INITIATED}, may; he asks {@link #PRICE} silver, and no more than one bottle in
 * {@link #BARGAIN_DAYS} days). Drunk at any time, whatever the moon, it lays the bite on them
 * ({@link Vampires#BITTEN}) as a bite does, and a pending willing turning ({@link #WILLING}): they turn at the nightfall of
 * the next new moon whose nightfall is still ahead ({@link #due}; tonight's own is behind them if it is night already), and
 * wait until then. Until then the bitten rules hold: the priest's cure clears it, a golden apple still makes them wake a
 * dhampir ({@link VampirePlayers#ateGold}), and dying mid-turn has them rise turned at once as a bitten one does
 * ({@link VampirePlayers#midTurn}); any other death leaves them as they were. A vampire, a cursed one, or one already
 * bitten can't take it (the bottle is kept). None of it is ever said on screen: it is
 * hinted only in gossip, loose pages and a hunter's words ({@link #gossip}, {@code lore/}, {@link LairHunts}) and a loading hint.
 */
public final class OldBlood {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** The talk option, and what the Elder asks (silver). */
	public static final String ANSWER = "jaymoskingdoms_bargain";
	static final int PRICE = 60;
	/** How many days an Elder gives a bottle again after being beaten, and a player may bargain again. */
	static final long BLOOD_DAYS = 3, BARGAIN_DAYS = 8;
	/** The drink takes this long (ticks). */
	static final int DRINK_TICKS = 40;
	/** The bite's weakness and hunger (as a vampire's bite lays on a player), and the chance a villager's gossip is one of the old blood's lines. */
	static final float GOSSIP_CHANCE = 0.07F, HUNTER_GOSSIP_CHANCE = 0.25F;
	/** How many gossip lines there are, and how many words a bargain's Elder has of each kind. */
	static final int GOSSIP_LINES = 6, HUNTER_LINES = 4, WORDS = 3;

	/** The Elder's Blood. */
	public static Item BLOOD;

	/** On an Elder: the game time he last gave a bottle up. Kept. */
	static final AttachmentType<Long> GAVE = AttachmentRegistry.create(JaymosKingdoms.id("elder_blood_given"), b -> b.persistent(Codec.LONG));
	/** On a player: the game time they last bargained for a bottle. Kept. */
	static final AttachmentType<Long> BARGAINED = AttachmentRegistry.create(JaymosKingdoms.id("elder_blood_bargain"), b -> b.persistent(Codec.LONG).copyOnDeath());

	/**
	 * On a player who drank the Elder's blood and has not turned yet: the first night whose nightfall was still ahead when they
	 * drank ({@link Vampires#nightIndex}). They turn at the first new moon's nightfall from that night on ({@link #due}). Kept;
	 * it goes when they turn, or when the bite on them is lifted (the priest's rite, a rebirth), and a death that is not a rise
	 * turned leaves nothing of it, as of the bite.
	 */
	static final AttachmentType<Long> WILLING = AttachmentRegistry.create(JaymosKingdoms.id("elder_blood_willing"), b -> b.persistent(Codec.LONG));
	/** On the Overworld: the last night that was a new moon night while the server was running (so a night they were away for still counts). Kept. */
	static final AttachmentType<Long> NEW_MOON_NIGHT = AttachmentRegistry.create(JaymosKingdoms.id("elder_blood_new_moon_night"), b -> b.persistent(Codec.LONG));

	private OldBlood() {
	}

	/**
	 * Whether the willing turning that waits from night {@code first} ({@link #WILLING}) is due now: it is night, no earlier than
	 * {@code first}, and that night is a new moon night (or one was, since {@code first}, while they were away).
	 */
	static boolean due(ServerLevel overworld, long first, long nightNo) {
		if (nightNo < first || !Vampires.night(overworld.getOverworldClockTime())) {
			return false;
		}
		if (NewMoon.on(overworld.getServer())) {
			noteNewMoon(overworld, nightNo);
			return true;
		}
		return overworld.getAttachedOrElse(NEW_MOON_NIGHT, -1L) >= first;
	}

	private static void noteNewMoon(ServerLevel overworld, long nightNo) {
		if (overworld.getAttachedOrElse(NEW_MOON_NIGHT, -1L) != nightNo) {
			overworld.setAttached(NEW_MOON_NIGHT, nightNo);
		}
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("elders_blood"));
		BLOOD = Registry.register(BuiltInRegistries.ITEM, key, new Bottle(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(BLOOD));
		ServerTickEvents.END_SERVER_TICK.register(OldBlood::tick);
	}

	// ---- drinking it ---------------------------------------------------------------------------------------------------

	/** The bottle: drunk at any time, it is a bite that turns them at the next new moon. */
	static final class Bottle extends Item {
		Bottle(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (Vampires.isVampire(player) || Lycans.cursed(player) || player.hasAttached(Vampires.BITTEN)) {
				return InteractionResult.PASS;
			}
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return DRINK_TICKS;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.DRINK;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
			if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) {
				return stack;
			}
			if (Vampires.isVampire(player) || Lycans.cursed(player) || player.hasAttached(Vampires.BITTEN)) {
				return stack;
			}
			server.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
			server.sendParticles(PowersCompat.blood(0x8A0303, 1.0F), player.getX(), player.getY(0.8), player.getZ(), 10, 0.25, 0.3, 0.25, 0.0);
			// as if bitten (no saving throw), but they turn at the next new moon's nightfall, not the next nightfall: the first
			// night still ahead is the next one (tonight's nightfall is behind them if it is night already)
			long clock = server.getOverworldClockTime();
			player.setAttached(Vampires.BITTEN, clock);
			player.setAttached(WILLING, Vampires.nightIndex(clock) + 1L);
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, Vampires.PLAYER_BITE_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, Vampires.PLAYER_BITE_TICKS, 1));
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
				player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), Prediction.SERVER_ONLY);
			}
			return stack;
		}
	}

	// ---- beating him down ----------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 9) {
			return;
		}
		if (NewMoon.on(server)) {
			noteNewMoon(server.overworld(), Vampires.nightIndex(server.overworld().getOverworldClockTime()));
		}
		for (ServerLevel level : server.getAllLevels()) {
			long now = level.getGameTime();
			for (ServerPlayer p : level.players()) {
				for (Mob elder : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16.0),
					m -> Vampires.kind(m) == Vampires.Kind.ELDER && m.isAlive() && Downed.isDowned(m))) {
					beaten(level, elder, now);
				}
			}
		}
	}

	/** The Elder lies beaten (downed, not dead): the one who beat him finds a bottle where he lies. */
	private static void beaten(ServerLevel level, Mob elder, long now) {
		Long gave = elder.getAttached(GAVE);
		if (gave != null && now >= gave && now - gave < BLOOD_DAYS * 24000L) {
			return;
		}
		Player by = elder.getLastHurtByPlayer();
		if (by == null && elder.getLastHurtByMob() instanceof Player p) {
			by = p;
		}
		if (by == null || by.level() != level || by.distanceTo(elder) > 24.0 || by.isSpectator()) {
			return;
		}
		elder.setAttached(GAVE, now);
		elder.spawnAtLocation(level, new ItemStack(BLOOD));
		level.sendParticles(PowersCompat.blood(0x8A0303, 1.0F), elder.getX(), elder.getY(0.3), elder.getZ(), 12, 0.3, 0.1, 0.3, 0.0);
		level.playSound(null, elder.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.HOSTILE, 0.8F, 0.6F);
	}

	// ---- bargaining ------------------------------------------------------------------------------------------------------

	/** Whether the option shows with {@code who}: the Elder, to one who has seen enough of the other side to speak to him of it. */
	public static boolean canAsk(Player player, LivingEntity who) {
		return who instanceof Villager v && Vampires.kind(v) == Vampires.Kind.ELDER && !v.isSleeping() && !Vampires.isVampire(player)
			&& Exposure.rankOf(player) >= Exposure.INITIATED;
	}

	/** The Elder's answer to a bargain: a bottle for silver, or a word on why not. */
	public static Component bargain(ServerPlayer player, LivingEntity elder) {
		long now = player.level().getGameTime();
		Long last = player.getAttached(BARGAINED);
		if (last != null && now >= last && now - last < BARGAIN_DAYS * 24000L) {
			return Component.translatable("talk.jaymoskingdoms.old_blood.refuse." + player.getRandom().nextInt(WORDS));
		}
		if (!Money.canPay(player, Money.worth(PRICE))) {
			return Component.translatable("talk.jaymoskingdoms.old_blood.poor." + player.getRandom().nextInt(WORDS));
		}
		Money.charge(player, Money.worth(PRICE));
		player.setAttached(BARGAINED, now);
		player.getInventory().placeItemBackInInventory(new ItemStack(BLOOD), Prediction.SERVER_ONLY);
		return Component.translatable("talk.jaymoskingdoms.old_blood.deal." + player.getRandom().nextInt(WORDS));
	}

	// ---- the hints ---------------------------------------------------------------------------------------------------------

	/**
	 * Now and then a villager's gossip is a half-heard tale of the old blood (a hunter's, a fletcher's or a guard's, more often):
	 * never what it is, only that the Elder's blood, a bottle, a moon with no light and a turning that is chosen go together.
	 */
	static @Nullable Component gossip(ServerLevel level, LivingEntity who) {
		boolean hunter = who instanceof Villager v && v.getVillagerData().profession().is(VillagerProfession.FLETCHER) || who instanceof Folk f && f.combatant();
		if (level.getRandom().nextFloat() >= (hunter ? HUNTER_GOSSIP_CHANCE : GOSSIP_CHANCE)) {
			return null;
		}
		return hunter ? Component.translatable("talk.jaymoskingdoms.gossip.old_blood_hunter." + level.getRandom().nextInt(HUNTER_LINES))
			: Component.translatable("talk.jaymoskingdoms.gossip.old_blood." + level.getRandom().nextInt(GOSSIP_LINES));
	}
}
