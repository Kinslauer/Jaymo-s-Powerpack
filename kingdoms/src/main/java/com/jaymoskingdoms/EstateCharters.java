package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * A late money sink: a realm's land-clerk, the cartographer who keeps its surveys, sells an <b>estate charter</b> to a
 * player of world tier Dread or beyond whom the realm thinks well of. Used on the ground of that realm's land it is the
 * realm's grant of a manor plot: the patch round that spot becomes the player's homestead ({@link Homesteads}, so what they
 * break there is no crime and a stranger breaking it is a vandal) and its guards hold them a householder of the realm for
 * good ({@link ForgedPapers#householder}: a bed on its land is a night at their own manor, {@link Lifestyle}).
 * (Nothing in the realms sold land or titles before; the cartographer stands in for the steward, as he already keeps the maps.)
 */
public final class EstateCharters {
	/** The price: this many emerald blocks and this many emeralds (1,000 in all), for one charter, once. */
	static final int PRICE_BLOCKS = 99, PRICE_EMERALDS = 109, USES = 1, XP = 60;
	/** The least reputation with the realm that it will sell one. */
	static final int REPUTATION = 400;
	/** A manor's householding runs this many days (as good as for ever). */
	static final long FOR_GOOD = 36500L;

	public static Item CHARTER;

	private EstateCharters() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("estate_charter"));
		CHARTER = Registry.register(BuiltInRegistries.ITEM, key, new Charter(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(CHARTER));
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && world instanceof ServerLevel level
				&& entity instanceof Villager v && v.isAlive() && !v.isBaby() && v.getTradingPlayer() == null
				&& v.getVillagerData().profession().is(VillagerProfession.CARTOGRAPHER)) {
				offer(level, sp, v);
			}
			return InteractionResult.PASS;
		});
	}

	/** The realm whose land {@code pos} is, if it is one that has not fallen. */
	private static @Nullable Faction realm(ServerLevel level, BlockPos pos) {
		if (level != Factions.level(level.getServer())) {
			return null;
		}
		Faction owner = Banners.owner(level.getServer(), ChunkPos.containing(pos));
		return owner == null || owner.fallen() || !owner.type().isRealm() ? null : owner;
	}

	/** Puts the charter among {@code v}'s trades, once, if {@code player} is hardened enough and well thought of. */
	private static void offer(ServerLevel level, ServerPlayer player, Villager v) {
		Faction realm = realm(level, v.blockPosition());
		if (realm == null || WorldTiers.of(player).ordinal() < WorldTiers.Tier.DREAD.ordinal() || Reputation.get(player, realm.id()) < REPUTATION) {
			return;
		}
		for (MerchantOffer offer : v.getOffers()) {
			if (offer.getResult().is(CHARTER)) {
				return;
			}
		}
		v.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD_BLOCK, PRICE_BLOCKS), java.util.Optional.of(new ItemCost(Items.EMERALD, PRICE_EMERALDS)),
			new ItemStack(CHARTER), USES, XP, 0.05F));
	}

	/** The charter: used on the ground of a realm's land that thinks well of its holder, it is spent and the manor is theirs. */
	static final class Charter extends Item {
		Charter(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos at = context.getClickedPos();
			Faction realm = realm(level, at);
			if (realm == null || Reputation.get(player, realm.id()) < REPUTATION || !Homesteads.grant(player, level, at)) {
				level.playSound(null, at, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
				level.sendParticles(player, ParticleTypes.SMOKE, true, false, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 10, 0.4, 0.3, 0.4, 0.0);
				return InteractionResult.FAIL;
			}
			Map<String, Long> houses = new HashMap<>(player.getAttachedOrElse(ForgedPapers.HOUSES, Map.of()));
			houses.put(Integer.toString(realm.id()), level.getGameTime() / 24000L + FOR_GOOD);
			player.setAttached(ForgedPapers.HOUSES, Map.copyOf(houses));
			level.playSound(null, at, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.charter.granted", realm.name()));
			context.getItemInHand().consume(1, player);
			return InteractionResult.SUCCESS_SERVER;
		}
	}
}
