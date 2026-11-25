package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.power.zfighter.ZFighterPatrol;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.fuel.PodFuel;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Galactic Patrol posts (Patch 9, tr_patrol): a station hanging in the high sky over a few worlds ({@link PodSpace#POST_WORLDS}),
 * far above anything that grows or is built there. In the crossing a post hangs beside the world ahead; slow beside it and dock
 * and the pod comes down on its deck instead of on the ground. The post is built when the first pod docks (and only then, so a
 * world nobody docks at has none).
 * <p>
 * Its dock console (a cartography table, used with the hand) is the post's quartermaster: fuel and return stages for anyone;
 * for an officer, modules for sale by rank (what the Patrol's own pods carry): Rookie the plain ones, Sergeant the ki cannon and
 * the shield, Captain the mining beam and tow line. Sneaking at it asks for the day's warrant on the world below
 * ({@link ZFighterPatrol#postWarrant}); arrests there raise rank as ever. It needs no MCA: the trade is the vanilla trade screen.
 */
public final class PodPost {
	// ---- tuning ----
	/** Where a post hangs in a world: its middle, and how far down from the top of the world. */
	public static final int POST_X = 64, POST_Z = 64, POST_Y = 270;
	/** The deck's radius, the rail's height, the hull's depth under it. */
	static final int DECK_R = 10, HULL_DEPTH = 7;
	/** Where the console stands from the middle. */
	static final int CONSOLE_X = 7;
	/** The ranks (an index into the Patrol's ranks, Rookie 0 to Commander 4) that open the module trades. */
	static final int RANK_FITTER = 0, RANK_MECHANIC = 1, RANK_GUNNER = 2, RANK_CAPTAIN = 3;
	/** What things cost, in emeralds. */
	static final int FUEL_CAN_COST = 2, BLAZE_COST = 3, STAGE_COST = 3;
	/** How many of each trade the console makes before it is out of stock (they come back as the day turns). */
	static final int STOCK = 8;

	private PodPost() {
	}

	static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer server) || !(level instanceof ServerLevel serverLevel) || !isPostWorld(serverLevel)) {
				return InteractionResult.PASS;
			}
			if (!hit.getBlockPos().equals(console()) || !serverLevel.getBlockState(hit.getBlockPos()).is(Blocks.CARTOGRAPHY_TABLE)) {
				return InteractionResult.PASS;
			}
			if (server.isSecondaryUseActive() && player.getItemInHand(hand).isEmpty()) {
				ZFighterPatrol.postWarrant(server);
			} else {
				trade(server);
			}
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	/** Whether {@code level} is a world a post hangs over. */
	public static boolean isPostWorld(ServerLevel level) {
		for (int world : PodSpace.POST_WORLDS) {
			if (ZFighterSpace.DESTS[world].level() == level.dimension()) {
				return true;
			}
		}
		return false;
	}

	/** The middle of the deck (its floor). */
	public static BlockPos centre() {
		return new BlockPos(POST_X, POST_Y, POST_Z);
	}

	/** The dock console. */
	public static BlockPos console() {
		return centre().offset(CONSOLE_X, 1, 0);
	}

	/** Where a pod comes down on the deck (the block it stands in). */
	public static BlockPos dock(ServerLevel level) {
		ensure(level);
		return centre().above();
	}

	/** Builds the post in {@code level} if it is not there. */
	public static void ensure(ServerLevel level) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.getChunk((POST_X >> 4) + dx, (POST_Z >> 4) + dz);
			}
		}
		if (level.getBlockState(console()).is(Blocks.CARTOGRAPHY_TABLE)) {
			return;
		}
		BlockPos c = centre();
		BlockState deck = Blocks.QUARTZ_BRICKS.defaultBlockState(), pad = Blocks.POLISHED_ANDESITE.defaultBlockState(), hull = Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
			lamp = Blocks.SEA_LANTERN.defaultBlockState(), rail = Blocks.IRON_BARS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		// the hull, tapering under the deck
		for (int d = 1; d <= HULL_DEPTH; d++) {
			int r = Math.max(1, DECK_R - d * (DECK_R - 1) / HULL_DEPTH);
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + z * z <= r * r + 1) {
						set(level, c.offset(x, -d, z), d == HULL_DEPTH ? lamp : hull);
					}
				}
			}
		}
		// the deck, with a pad in the middle, a rail and room above it
		for (int x = -DECK_R; x <= DECK_R; x++) {
			for (int z = -DECK_R; z <= DECK_R; z++) {
				int d2 = x * x + z * z;
				if (d2 > DECK_R * DECK_R + 1) {
					continue;
				}
				set(level, c.offset(x, 0, z), Math.abs(x) <= 3 && Math.abs(z) <= 3 ? pad : deck);
				for (int y = 1; y <= 12; y++) {
					set(level, c.offset(x, y, z), d2 >= (DECK_R - 1) * (DECK_R - 1) && y == 1 ? rail : air);
				}
			}
		}
		// lamps round the pad, a mast with a light, the console
		for (int[] at : new int[][] {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}, {0, 5}, {0, -5}, {5, 0}}) {
			set(level, c.offset(at[0], 0, at[1]), lamp);
		}
		for (int y = 1; y <= 10; y++) {
			set(level, c.offset(-CONSOLE_X, y, 0), y == 10 ? lamp : Blocks.IRON_BARS.defaultBlockState());
		}
		set(level, console(), Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		set(level, console().above(), Blocks.LANTERN.defaultBlockState());
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS);
	}

	// ---------------------------------------------------------------- the quartermaster

	/** Opens the console's trade screen for {@code player}: what the post sells them at their rank. */
	private static void trade(ServerPlayer player) {
		MerchantOffers offers = new MerchantOffers();
		offers.add(offer(Items.EMERALD, FUEL_CAN_COST, new ItemStack(PodFuel.FUEL_CAN)));
		offers.add(offer(Items.EMERALD, BLAZE_COST, new ItemStack(Items.BLAZE_POWDER, 4)));
		offers.add(offer(Items.EMERALD, STAGE_COST, new ItemStack(PodReturn.STAGE)));
		int rank = ZFighterPatrol.officerRank(player);
		if (rank >= RANK_FITTER) {
			for (PodModules.Kind kind : new PodModules.Kind[] {PodModules.Kind.SEAT, PodModules.Kind.HOLD, PodModules.Kind.EXTRA_TANK, PodModules.Kind.SCANNER}) {
				offers.add(offer(Items.EMERALD, cost(kind), new ItemStack(PodModules.item(kind))));
			}
		}
		if (rank >= RANK_MECHANIC) {
			offers.add(offer(Items.EMERALD, cost(PodModules.Kind.KICK_STAGE), new ItemStack(PodModules.item(PodModules.Kind.KICK_STAGE))));
			offers.add(offer(Items.EMERALD, cost(PodModules.Kind.LEGS), new ItemStack(PodModules.item(PodModules.Kind.LEGS))));
		}
		if (rank >= RANK_GUNNER) {
			offers.add(offer(Items.EMERALD, cost(PodModules.Kind.KI_CANNON), new ItemStack(PodModules.item(PodModules.Kind.KI_CANNON))));
			offers.add(offer(Items.EMERALD, cost(PodModules.Kind.SHIELD), new ItemStack(PodModules.item(PodModules.Kind.SHIELD))));
		}
		if (rank >= RANK_CAPTAIN) {
			offers.add(offer(Items.EMERALD, cost(PodModules.Kind.MINING_BEAM), new ItemStack(PodModules.item(PodModules.Kind.MINING_BEAM))));
			offers.add(offer(Items.EMERALD, cost(PodModules.Kind.TOW_LINE), new ItemStack(PodModules.item(PodModules.Kind.TOW_LINE))));
		}
		if (rank < RANK_FITTER) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.patrol.post_civilian").withStyle(ChatFormatting.GRAY));
		}
		ServerLevel level = player.level();
		level.playSound(null, console(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.2F);
		new Quartermaster(offers).openTradingScreen(player, Component.translatable("screen.jaymospowers.patrol_post"), 1);
	}

	/** What a module costs in emeralds at the post. */
	static int cost(PodModules.Kind kind) {
		return switch (kind) {
			case SEAT -> 6;
			case HOLD -> 8;
			case EXTRA_TANK -> 10;
			case SCANNER -> 8;
			case KICK_STAGE -> 12;
			case LEGS -> 10;
			case KI_CANNON, SHIELD -> 24;
			case MINING_BEAM -> 20;
			case TOW_LINE -> 16;
		};
	}

	private static MerchantOffer offer(net.minecraft.world.level.ItemLike pays, int count, ItemStack gives) {
		return new MerchantOffer(new ItemCost(pays, count), gives, STOCK, 0, 0.0F);
	}

	/** The console as the one who sells: the trade screen's side of it. */
	private static final class Quartermaster implements Merchant {
		private final MerchantOffers offers;
		private Player trading;
		private int xp;

		Quartermaster(MerchantOffers offers) {
			this.offers = offers;
		}

		@Override
		public void setTradingPlayer(Player player) {
			this.trading = player;
		}

		@Override
		public Player getTradingPlayer() {
			return this.trading;
		}

		@Override
		public MerchantOffers getOffers() {
			return this.offers;
		}

		@Override
		public void overrideOffers(MerchantOffers offers) {
		}

		@Override
		public void notifyTrade(MerchantOffer offer) {
			offer.increaseUses();
		}

		@Override
		public void notifyTradeUpdated(ItemStack stack) {
		}

		@Override
		public int getVillagerXp() {
			return this.xp;
		}

		@Override
		public void overrideXp(int xp) {
			this.xp = xp;
		}

		@Override
		public boolean showProgressBar() {
			return false;
		}

		@Override
		public net.minecraft.sounds.SoundEvent getNotifyTradeSound() {
			return SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT;
		}

		@Override
		public boolean isClientSide() {
			return false;
		}

		@Override
		public boolean stillValid(Player player) {
			return player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(console())) < 64.0;
		}
	}
}
