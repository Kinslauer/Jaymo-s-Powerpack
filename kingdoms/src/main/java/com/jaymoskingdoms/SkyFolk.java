package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The people of the Aether's villages (Jaymo's Powers builds the villages and the sky lord's castle; MCA makes their
 * villagers its own). Andy: the villages are "filled with divine born, any other heritage has a reason for not only having
 * traveled but being accepted", and the halfway folk are "what the dawnborn culture becomes".
 * <ul>
 * <li><b>Blood.</b> Everyone born in the Aether is Divine-born, but for one in a few who is Dawnborn: the halfway folk,
 * whose people walked up after the sun and were let stay ({@link #born}, from {@link VillagerHeritage#roll}).</li>
 * <li><b>No one is minded up there.</b> Whatever a villager in the Aether makes of your blood below, they don't hold it
 * against you here ({@link Prejudice#minds}): no kingdom claims the villages, so there is nobody's law either.</li>
 * <li><b>Their talk</b> is their own while they are up there ({@link #talk}): hints, never an account.</li>
 * <li><b>Things that fall off.</b> Now and then something drops out of a clear sky near a player in the Overworld: a
 * tuft of cloudwool, a feather, a hat, a letter that was never meant for them ({@link #fall}).</li>
 * </ul>
 */
public final class SkyFolk {
	/** The share of those born in the Aether who are Dawnborn (the halfway folk); the rest are Divine-born. */
	static final float HALFWAY_SHARE = 0.15F;
	/** Lines each of the two has (lang {@code dialogue.chat.jaymoskingdoms.blood_sky_divine/<n>}, {@code ..._sky_dawnborn/<n>}). */
	static final int LINES = 5;
	/** Things that fall: looked at this often (ticks), one look in this many drops something (about once in six days out of doors). */
	static final int FALL_EVERY = 1200, FALL_IN = 120;
	/** How far from the player it lands (blocks), and how far up it starts. */
	static final int FALL_NEAR = 6, FALL_FAR = 16, FALL_HEIGHT = 40;
	/** How many letters there are (lang {@code item.jaymoskingdoms.sky_letter.<n>}). */
	static final int LETTERS = 5;

	private static final ResourceKey<Level> AETHER = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("jaymospowers", "aether"));

	private SkyFolk() {
	}

	static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % FALL_EVERY != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && player.isAlive() && level.getRandom().nextInt(FALL_IN) == 0) {
					fall(level, player);
				}
			}
		});
	}

	/** Whether {@code entity} is in the Aether (its own dimension, or that band of a stacked world). */
	public static boolean in(Entity entity) {
		return Stacked.in(entity, AETHER);
	}

	static boolean at(Level level, BlockPos pos) {
		return Stacked.at(level, pos, AETHER);
	}

	/** The blood of one born in the Aether. */
	static Heritage.Kind born(RandomSource random) {
		return random.nextFloat() < HALFWAY_SHARE ? Heritage.Kind.DAWNBORN : Heritage.Kind.DIVINE;
	}

	/** Which of the sky folk's own lines {@code villager} speaks while in the Aether; null for anyone else. */
	static @Nullable String talk(Entity villager, Heritage.Kind kind) {
		if (kind != Heritage.Kind.DIVINE && kind != Heritage.Kind.DAWNBORN || !in(villager)) {
			return null;
		}
		return kind == Heritage.Kind.DIVINE ? "sky_divine" : "sky_dawnborn";
	}

	// ---- things that fall off -----------------------------------------------------------------------------------------------

	private static @Nullable Item ours(String id) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("jaymospowers", id));
		return item == Items.AIR ? null : item;
	}

	private static ItemStack named(Item item, String key) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable(key).withStyle(ChatFormatting.WHITE));
		return stack;
	}

	private static ItemStack fallen(RandomSource random) {
		int roll = random.nextInt(100);
		Item wool = ours("cloudwool"), feather = ours("moa_feather"), twine = ours("cloudtwine");
		if (roll < 40) {
			ItemStack letter = named(Items.PAPER, "item.jaymoskingdoms.sky_letter");
			letter.set(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymoskingdoms.sky_letter." + random.nextInt(LETTERS)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))));
			return letter;
		}
		if (roll < 55) {
			return named(Items.LEATHER_HELMET, "item.jaymoskingdoms.sky_hat");
		}
		if (roll < 75 && wool != null) {
			return new ItemStack(wool, 1 + random.nextInt(2));
		}
		if (roll < 90 && feather != null) {
			return new ItemStack(feather);
		}
		return twine != null ? new ItemStack(twine, 1 + random.nextInt(3)) : new ItemStack(Items.STRING, 1 + random.nextInt(3));
	}

	/** Something drops out of the open sky a little way from {@code player}, in the Overworld, with a soft thump for them to turn to. */
	private static void fall(ServerLevel level, ServerPlayer player) {
		if (Stacked.realm(player) != Level.OVERWORLD || level.isRaining() || !level.canSeeSky(player.blockPosition().above())) {
			return;
		}
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0, far = FALL_NEAR + random.nextDouble() * (FALL_FAR - FALL_NEAR);
		BlockPos over = BlockPos.containing(player.getX() + Math.cos(angle) * far, player.getY() + FALL_HEIGHT, player.getZ() + Math.sin(angle) * far);
		if (!level.isLoaded(over) || !level.canSeeSky(over)) {
			return;
		}
		ItemEntity item = new ItemEntity(level, over.getX() + 0.5, over.getY(), over.getZ() + 0.5, fallen(random));
		item.setDeltaMovement(0.0, -0.1, 0.0);
		level.addFreshEntity(item);
		level.playSound(null, over.getX() + 0.5, player.getY() + 6.0, over.getZ() + 0.5, SoundEvents.WOOL_FALL, SoundSource.AMBIENT, 1.0F, 0.6F);
	}
}
