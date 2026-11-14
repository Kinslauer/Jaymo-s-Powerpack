package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blossom smiths' steel (docs/BlossomLands-Plan.md, patch 8; Andy: "their smiths will teach you about the process").
 * <ol>
 * <li><b>Iron sand</b> lies in Mohknee's riverbeds: sand or gravel dug from under a river's water there gives a handful
 * now and then ({@link #SAND_CHANCE}).</li>
 * <li>Burnt hard in a blast furnace it sinters into a <b>steel bloom</b>.</li>
 * <li>The bloom is <b>folded</b> on an anvil: struck with the bloom in hand, {@link #FOLDS} times, it becomes folded
 * steel (Powers' Folded Steel Ingot, the only metal a katana's or ninjato's blade is hammered from; an iron ingot without Powers).</li>
 * </ol>
 * None of it is written anywhere. A smith of a Blossom settlement tells it a step at a time ("Ask About Steel" on the
 * Interact page): the first for the asking, the second to someone holding iron sand, the third to someone holding a
 * bloom. Only then will the bloom fold under that player's hand ({@link #KNOWS}).
 */
public final class BlossomSteel {
	/** The Interact action. */
	public static final String ACTION = "ask_steel";
	/** A dug river sand or gravel gives iron sand this often, and how many blows fold a bloom. */
	static final float SAND_CHANCE = 0.15F;
	static final int FOLDS = 6;
	/** How near the smith must stand. */
	static final double NEAR = 6.0;
	/** The steps a smith tells; a player who has heard them all can fold. */
	static final int STEPS = 3;

	/** How many of the steps a player has been told. Saved, kept through death. */
	static final AttachmentType<Integer> KNOWS = AttachmentRegistry.create(JaymosKingdoms.id("blossom_steel"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	public static Item IRON_SAND, BLOOM;

	private BlossomSteel() {
	}

	static void register() {
		IRON_SAND = item("iron_sand", new Item.Properties());
		BLOOM = item("steel_bloom", new Item.Properties().durability(FOLDS));
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> dug(level, player, pos, state));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> fold(player, level, hand, hit.getBlockPos()));
	}

	private static Item item(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
		net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS)
			.register(output -> output.accept(item));
		return item;
	}

	/** Sand or gravel dug from under a river's water on Mohknee: now and then, iron sand with it. */
	private static void dug(Level level, Player player, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel server) || player.isCreative() || !(state.is(Blocks.SAND) || state.is(Blocks.GRAVEL))
			|| !com.jaymoskingdoms.seas.Mohknee.on(level, pos) || !level.getBiome(pos).is(BiomeTags.IS_RIVER)
			|| !level.getFluidState(pos.above()).is(FluidTags.WATER) || server.getRandom().nextFloat() >= SAND_CHANCE) {
			return;
		}
		Block.popResource(level, pos, new ItemStack(IRON_SAND));
	}

	/** A bloom struck on an anvil: one fold, for a player who has been shown how. */
	private static InteractionResult fold(Player player, Level level, InteractionHand hand, BlockPos pos) {
		ItemStack held = player.getItemInHand(hand);
		if (player.isSpectator() || !held.is(BLOOM) || !level.getBlockState(pos).is(BlockTags.ANVIL)) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (sp.getAttachedOrElse(KNOWS, 0) < STEPS) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.steel.unknown"));
			return InteractionResult.FAIL;
		}
		level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7F, 1.3F);
		if (held.getDamageValue() + 1 < FOLDS) {
			held.setDamageValue(held.getDamageValue() + 1);
			return InteractionResult.SUCCESS;
		}
		Item steel = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymospowers", "folded_steel_ingot")).orElse(Items.IRON_INGOT);
		player.setItemInHand(hand, new ItemStack(steel));
		return InteractionResult.SUCCESS;
	}

	/** {@code v} is a smith of a Blossom settlement, near enough to ask, with something left to tell {@code player}. */
	static boolean offers(ServerPlayer player, LivingEntity v) {
		if (!(v instanceof Villager villager) || v.distanceTo(player) > NEAR || player.getAttachedOrElse(KNOWS, 0) >= STEPS) {
			return false;
		}
		var job = villager.getVillagerData().profession();
		if (!job.is(VillagerProfession.ARMORER) && !job.is(VillagerProfession.TOOLSMITH) && !job.is(VillagerProfession.WEAPONSMITH)) {
			return false;
		}
		ServerLevel level = player.level();
		Faction f = Factions.at(level.getServer(), level, v.blockPosition());
		return f != null && BlossomVillages.blossom(level, f);
	}

	/** {@code player} pressed "Ask About Steel": the next step, if they hold what it is about. */
	static void talk(ServerPlayer player, LivingEntity v) {
		if (!offers(player, v)) {
			return;
		}
		int known = player.getAttachedOrElse(KNOWS, 0);
		boolean ready = known == 0 || player.getInventory().contains(new ItemStack(known == 1 ? IRON_SAND : BLOOM));
		Speech.tell(player.level(), v, player, Component.translatable("talk.jaymoskingdoms.steel." + (ready ? "step." : "wait.") + known), Speech.WHITE);
		if (ready) {
			player.setAttached(KNOWS, known + 1);
		}
	}
}
