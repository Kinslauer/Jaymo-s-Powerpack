package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.WildRoom;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.83) Life story road "Fresh off the bench" (Kingdoms calls {@link #start} through {@code PowersCompat.androidStart}).
 * You wake on the Mad Tinkerer's bench already rebuilt: the state his operation leaves ({@link ZFighterAndroid#DONE}: endless
 * ki, a food bar that never drops, mending with metal instead of bandages, the Overdrive form) is put on you directly, with no
 * operation and no test. The Tinkerer wanders ({@link ZFighterAndroid}) and has no workshop of his own, so a small one is
 * raised on open ground in the wild ({@link WildRoom}) and he is stood at his bench in it. A note is in your pocket: short, odd,
 * and it says nothing outright. Wake without a power and you are given the Z Fighter's (an android's ki is that power's);
 * with another power the android body is set but its ki and hunger perks wait for the Z Fighter's.
 */
public final class ZFighterAndroidStart {
	// ---- tuning ----
	/** The workshop: its size, how far from the player it is raised (blocks), the doorway's place and width. */
	static final int W = 9, D = 7, H = 4, DOOR_X = 4, DOOR_W = 2;
	static final int SITE_MIN = 60, SITE_MAX = 160;
	/** How long the Tinkerer stays at his bench (ticks). */
	static final long TINKERER_STAYS = 72000L;
	/** The blur you wake with (ticks). */
	static final int WAKE_BLUR = 60;

	private ZFighterAndroidStart() {
	}

	/**
	 * Jaymo's Kingdoms (by reflection): raises the workshop, puts {@code player} on its bench as a new android with the note in
	 * their pocket. False if no ground could be found (Kingdoms falls back to the wild).
	 */
	public static boolean start(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = player.level().dimension() == Level.OVERWORLD ? player.level() : server.overworld();
		RandomSource random = player.getRandom();
		try {
			BlockPos origin = WildRoom.find(level, player.level() == level ? player.blockPosition() : level.getRespawnData().pos(), SITE_MIN, SITE_MAX, W, D, H, random);
			if (origin == null) {
				return false;
			}
			build(level, origin, random);
			tinkerer(level, origin, random);
			player.teleportTo(level, origin.getX() + 4.5, origin.getY() + 0.5, origin.getZ() + 3.5, Set.of(), 180.0F, 0.0F, true);
			player.resetFallDistance();
			player.setDeltaMovement(Vec3.ZERO);
			rebuild(player);
			player.getInventory().placeItemBackInInventory(note(), Prediction.SERVER_ONLY);
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, WAKE_BLUR, 0, true, false, false));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.7, 0.4, 0.2);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.android_start.wake").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
			return true;
		} catch (RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't build the Tinkerer's workshop for {}", player.getGameProfile().name(), e);
			return false;
		}
	}

	/** Puts the rebuilt state on {@code player} directly: the Z Fighter's power if they have none, then the android body. */
	private static void rebuild(ServerPlayer player) {
		if (Powers.of(player) == null) {
			Powers.set(player, ZFighterPower.INSTANCE);
		}
		ZFighterAndroid.setStage(player, ZFighterAndroid.DONE);
		ZFighterBranchForms.grant(player, ZFighterBranchForms.OVERDRIVE);
		ZFighterPower.Live live = ZFighterPower.live(player);
		live.ki = ZFighterPower.maxKi(player);
		player.getFoodData().setFoodLevel(20);
		player.setHealth(player.getMaxHealth());
		ZFighterPower.refresh(player, live);
	}

	private static void build(ServerLevel level, BlockPos o, RandomSource random) {
		WildRoom.Style style = new WildRoom.Style(Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState(),
			Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
		WildRoom.shell(level, o, W, D, H, style, DOOR_X, DOOR_W, random);
		// a lamp-lit skylight and barred windows
		for (int z : new int[] {2, 4}) {
			WildRoom.set(level, o, 0, 1, z, Blocks.IRON_BARS.defaultBlockState());
			WildRoom.set(level, o, W - 1, 1, z, Blocks.IRON_BARS.defaultBlockState());
		}
		WildRoom.set(level, o, 4, H, 3, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
		WildRoom.set(level, o, 4, H, 2, Blocks.REDSTONE_BLOCK.defaultBlockState());
		// the bench you woke on (two smooth slabs with a lamp at its head) and the tools round it
		WildRoom.set(level, o, 4, 0, 3, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
		WildRoom.set(level, o, 4, 0, 4, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
		WildRoom.set(level, o, 4, 0, 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		WildRoom.set(level, o, 1, 0, 1, Blocks.SMITHING_TABLE.defaultBlockState());
		WildRoom.set(level, o, 2, 0, 1, Blocks.GRINDSTONE.defaultBlockState());
		WildRoom.set(level, o, 3, 0, 1, Blocks.ANVIL.defaultBlockState());
		WildRoom.set(level, o, 7, 0, 1, Blocks.BLAST_FURNACE.defaultBlockState());
		WildRoom.set(level, o, 6, 0, 1, Blocks.CAULDRON.defaultBlockState());
		WildRoom.set(level, o, 1, 0, 5, Blocks.COPPER_BLOCK.waxed().unaffected().defaultBlockState());
		WildRoom.set(level, o, 1, 1, 5, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		WildRoom.set(level, o, 7, 0, 5, Blocks.IRON_BLOCK.defaultBlockState());
		WildRoom.set(level, o, 7, 1, 5, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		WildRoom.set(level, o, 7, 0, 3, Blocks.BARREL.defaultBlockState());
		level.playSound(null, o.getX() + 4, o.getY(), o.getZ() + 3, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6F, 0.8F);
	}

	/** The Mad Tinkerer, held at his bench: an MCA villager with Kingdoms and MCA, else the old frozen trader. */
	private static void tinkerer(ServerLevel level, BlockPos o, RandomSource random) {
		Vec3 at = Vec3.atBottomCenterOf(o.offset(6, 0, 3));
		Entity npc = ZFighterNpcs.villager(level, at, 90.0F, ZFighterAndroid.TAG, "entity.jaymospowers.scientist", false);
		if (npc == null) {
			WanderingTrader trader = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.EVENT);
			if (trader == null) {
				return;
			}
			trader.snapTo(at.x, at.y, at.z, 90.0F, 0.0F);
			trader.setNoAi(true);
			trader.setPermanentlyInvulnerable(true);
			trader.setPersistenceRequired();
			trader.setDespawnDelay(0);
			trader.addTag(ZFighterAndroid.TAG);
			trader.setCustomName(Component.translatable("entity.jaymospowers.scientist"));
			trader.setCustomNameVisible(true);
			trader.getOffers().clear();
			level.addFreshEntity(trader);
			npc = trader;
		}
		npc.addTag(ZFighterAndroid.UNTIL_TAG + (level.getGameTime() + TINKERER_STAYS));
	}

	/** The note in your pocket: short, odd lines (lang {@code book.jaymospowers.android_note.pageN}). */
	private static ItemStack note() {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = List.of(
			Filterable.passThrough(Component.translatable("book.jaymospowers.android_note.page1")),
			Filterable.passThrough(Component.translatable("book.jaymospowers.android_note.page2")),
			Filterable.passThrough(Component.translatable("book.jaymospowers.android_note.page3")));
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Note"), "T.", 3, pages, true));
		return book;
	}
}
