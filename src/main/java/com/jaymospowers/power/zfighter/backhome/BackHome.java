package com.jaymospowers.power.zfighter.backhome;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.fuel.PodFuel;
import com.jaymospowers.power.zfighter.pod.PodEntity;
import com.jaymospowers.power.zfighter.pod.PodPart;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.55, Andy's SevTech picks: "Back home") What the far worlds send home, besides metal:
 * <ul>
 * <li><b>Maw dust</b>, the Starlight essence's body: a wreck's first salvage leaves {@link #WRECK_DUST} of it, and the glass of the
 * black hole's rim sheds a little when broken ({@link #RIM_CHANCE}). The sky hermits of Jaymo's Kingdoms sell it.</li>
 * <li>The <b>Starlit Lens</b>, made at the infusion altar ({@code data/jaymospowers/alchemy/infusion/starlit_lens.json}, taught by
 * {@link #LENS_RESEARCH}): set in a pod's beacon mast by using it on the pod ({@link #fitLens}); the flight then shows the
 * mission zones of the world ahead.</li>
 * <li><b>Verdant fruit</b> (leaves broken on the Verdant World) and <b>Glacia salt</b> (ice, snow and stone broken on Glacia): the
 * ingredients of Jaymo's Kitchen's far-world dishes, whose trip buffs are {@link FarFood}.</li>
 * </ul>
 */
public final class BackHome {
	// ---- tuning ----
	/** Maw dust a wreck's first salvage leaves for each player. */
	public static final int WRECK_DUST = 2;
	/** The chance a rim glass broken in the Maw sheds dust, and the most it sheds. */
	public static final float RIM_CHANCE = 0.35F;
	public static final int RIM_MAX = 2;
	/** The chance a leaf block broken on the Verdant World drops a fruit; an ice, snow or stone block on Glacia a pinch of salt. */
	public static final float FRUIT_CHANCE = 0.06F, SALT_CHANCE = 0.05F;
	/** The research that teaches the Starlit Lens. */
	public static final String LENS_RESEARCH = "starlit_lens";

	public static Item MAW_DUST, STARLIT_LENS, VERDANT_FRUIT, GLACIA_SALT;

	private BackHome() {
	}

	public static void init() {
		MAW_DUST = register("maw_dust", Rarity.UNCOMMON, "item.jaymospowers.maw_dust.tooltip");
		STARLIT_LENS = register("starlit_lens", Rarity.RARE, "item.jaymospowers.starlit_lens.tooltip");
		ResourceKey<Item> fruitKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("verdant_fruit"));
		VERDANT_FRUIT = Registry.register(BuiltInRegistries.ITEM, fruitKey, new Item(new Item.Properties().setId(fruitKey)
			.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.4F).build())));
		ResourceKey<Item> saltKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("glacia_salt"));
		GLACIA_SALT = Registry.register(BuiltInRegistries.ITEM, saltKey, new Item(new Item.Properties().setId(saltKey)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(MAW_DUST);
			output.accept(STARLIT_LENS);
			output.accept(GLACIA_SALT);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(VERDANT_FRUIT));
		Research.register(new Research.Entry(LENS_RESEARCH, Research.Category.INFUSION, () -> STARLIT_LENS,
			EssenceList.of(Essence.STARLIGHT, 6, Essence.LIGHT, 4), List.of("infusion"), 0, 2, 2));
		FarFood.init();
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (level instanceof ServerLevel server && !player.isCreative()) {
				found(server, pos, state);
			}
		});
	}

	private static Item register(String id, Rarity rarity, String tooltip) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new PodFuel.TipItem(new Item.Properties().setId(key).rarity(rarity), tooltip));
	}

	/** A block broken: the far worlds' small finds. */
	private static void found(ServerLevel level, net.minecraft.core.BlockPos pos, BlockState state) {
		var world = level.dimension();
		if (world == ZFighterSpace.DESTS[ZFighterSpace.VERDANT].level()) {
			if (state.is(BlockTags.LEAVES) && level.getRandom().nextFloat() < FRUIT_CHANCE) {
				Block.popResource(level, pos, new ItemStack(VERDANT_FRUIT));
			}
		} else if (world == ZFighterSpace.DESTS[ZFighterSpace.GLACIA].level()) {
			if ((state.is(BlockTags.ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(BlockTags.BASE_STONE_OVERWORLD))
				&& level.getRandom().nextFloat() < SALT_CHANCE) {
				Block.popResource(level, pos, new ItemStack(GLACIA_SALT));
			}
		} else if (world == ZFighterSpace.DESTS[ZFighterSpace.MAW].level()) {
			if (rimGlass(state) && level.getRandom().nextFloat() < RIM_CHANCE) {
				Block.popResource(level, pos, new ItemStack(MAW_DUST, 1 + level.getRandom().nextInt(RIM_MAX)));
			}
		}
	}

	/** The glass ring round the black hole (the Maw builds it orange and yellow; coloured blocks have no fields, so by name). */
	private static boolean rimGlass(BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.equals("orange_stained_glass") || path.equals("yellow_stained_glass");
	}

	/**
	 * Use of {@code held} on {@code pod}: if it is a Starlit Lens, it is set in the beacon mast (told why not otherwise). False when
	 * {@code held} isn't a lens at all.
	 */
	public static boolean fitLens(ServerPlayer player, PodEntity pod, ItemStack held) {
		if (!held.is(STARLIT_LENS)) {
			return false;
		}
		if (!pod.has(PodPart.MAST, 0)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.lens_no_mast").withStyle(ChatFormatting.GRAY));
		} else if (pod.lens()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.lens_fitted").withStyle(ChatFormatting.GRAY));
		} else {
			held.consume(1, player);
			pod.setLens(true);
			ServerLevel level = player.level();
			Vec3 at = pod.getBoundingBox().getCenter();
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.6F);
			level.sendParticles(ParticleTypes.END_ROD, at.x, pod.getY() + pod.getBbHeight(), at.z, 16, 0.2, 0.4, 0.2, 0.03);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.lens_set").withStyle(ChatFormatting.AQUA));
		}
		return true;
	}
}
