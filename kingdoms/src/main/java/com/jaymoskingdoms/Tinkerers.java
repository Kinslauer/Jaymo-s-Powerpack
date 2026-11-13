package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Tinkerer. At a tinkering station of Jung's Powers (the tool station, tool forge, part builder, crafting station, rig forge, star
 * forge) a Tinkerer sneaks and uses a piece of gear to put a small enchantment on it, for some materials from the pack:
 *
 * <ul>
 * <li><b>Returning</b> (a trident): it comes back to the hand when thrown, as the Loyalty enchantment does. It is the real trident.</li>
 * <li><b>Springing</b> (boots): the wearer leaps higher and lands softly.</li>
 * <li><b>Glowing</b> (a shield): the holder sees by it in the dark, and a soft light moves with them.</li>
 * </ul>
 *
 * A Tinkerer keeps up to {@link #MOST} infusions at once on the gear they carry. The class key tells which they have. The Tinkerer also
 * learns, from the village smiths ({@link ClassTeachers}), to make some of the gadgets that are otherwise found in old chests
 * ({@link #GADGETS}).
 */
public final class Tinkerers {
	/** Infusions at once. */
	static final int MOST = 3;

	/** The infusions at once {@code player} may keep (the second class has a share; a Battle Smith one more). */
	static int most(net.minecraft.world.entity.player.Player player) {
		return Classes.scaled(player, PlayerClass.TINKERER, MOST) + Paths.extraInfusions(player);
	}
	/** The gadgets a Tinkerer can learn to make (their recipes are taught by a smith). */
	static final List<String> GADGETS = List.of("immovable_rod", "climbing_rope", "endless_jug", "floating_light", "smoke_bottle");
	/** The stations. */
	private static final java.util.Set<String> STATIONS = java.util.Set.of("tool_station", "tool_forge", "part_builder", "crafting_station", "rig_forge", "star_forge");
	static final String TAG = "jaymoskingdoms_infusion";
	static final int LOYALTY = 3;
	static final int SPRING_TICKS = 30, GLOW_TICKS = 260;

	private Tinkerers() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown() || !Classes.is(sp, PlayerClass.TINKERER)
				|| hand != net.minecraft.world.InteractionHand.MAIN_HAND || !station(level.getBlockState(hit.getBlockPos()))) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getMainHandItem();
			String kind = kindFor(held);
			if (kind.isEmpty()) {
				return InteractionResult.PASS;
			}
			infuse(sp, held, kind);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Tinkerers::tick);
	}

	static void clear() {
	}

	static void begin(ServerPlayer player) {
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.tinkerer.begin").withStyle(net.minecraft.ChatFormatting.GRAY));
	}

	private static boolean station(BlockState state) {
		net.minecraft.resources.Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return id.getNamespace().equals("jaymospowers") && STATIONS.contains(id.getPath());
	}

	/** Which infusion {@code stack} takes ("" for none). */
	static String kindFor(ItemStack stack) {
		if (stack.is(Items.TRIDENT)) {
			return "returning";
		}
		if (stack.is(ItemTags.FOOT_ARMOR)) {
			return "springing";
		}
		return stack.is(Items.SHIELD) ? "glowing" : "";
	}

	/** The infusion on {@code stack} ("" for none). */
	static String infusion(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr(TAG, "");
	}

	private static Item material(String kind) {
		return switch (kind) {
			case "returning" -> Items.ENDER_PEARL;
			case "springing" -> Items.SLIME_BALL;
			default -> Items.GLOWSTONE_DUST;
		};
	}

	private static int cost(String kind) {
		return switch (kind) {
			case "returning" -> 1;
			case "springing" -> 2;
			default -> 4;
		};
	}

	/** How many infusions {@code player} carries. */
	static int carried(Player player) {
		int n = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (!infusion(stack).isEmpty()) {
				n++;
			}
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() != EquipmentSlot.Type.HAND && !infusion(player.getItemBySlot(slot)).isEmpty()) {
				n++;
			}
		}
		return n + (infusion(player.getOffhandItem()).isEmpty() ? 0 : 1);
	}

	private static void infuse(ServerPlayer player, ItemStack stack, String kind) {
		if (!infusion(stack).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tinkerer.already"));
			return;
		}
		if (carried(player) >= most(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tinkerer.most", most(player)));
			return;
		}
		Item mat = material(kind);
		int need = cost(kind);
		if (!player.isCreative() && !take(player, mat, need)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tinkerer.materials", need, new net.minecraft.world.item.ItemStack(mat).getHoverName()));
			return;
		}
		CompoundTag tag = new CompoundTag();
		tag.putString(TAG, kind);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.infusion." + kind).withStyle(net.minecraft.ChatFormatting.AQUA))));
		if (kind.equals("returning")) {
			stack.enchant(player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOYALTY), LOYALTY);
		}
		if (player.level() instanceof ServerLevel level) {
			Fx.burst(level, player.getX(), player.getY() + 1.2, player.getZ(), 0x7FD6FF, 1.0F, 20, 0.4, 0.5, 0.4);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tinkerer.done", Component.translatable("item.jaymoskingdoms.infusion." + kind)));
	}

	private static boolean take(ServerPlayer player, Item item, int n) {
		int have = 0;
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (s.is(item)) {
				have += s.getCount();
			}
		}
		if (have < n) {
			return false;
		}
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (n > 0 && s.is(item)) {
				int d = Math.min(n, s.getCount());
				s.shrink(d);
				n -= d;
			}
		}
		return true;
	}

	/** The class key: what is infused. */
	static void key(ServerPlayer player, boolean sneak) {
		int n = carried(player);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tinkerer.infusions", n, most(player)));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			if (infusion(player.getItemBySlot(EquipmentSlot.FEET)).equals("springing")) {
				player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, SPRING_TICKS, 1, true, false, false));
			}
			if (infusion(player.getMainHandItem()).equals("glowing") || infusion(player.getOffhandItem()).equals("glowing")) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, GLOW_TICKS, 0, true, false, false));
				if (player.level() instanceof ServerLevel level && server.getTickCount() % 40 == 0) {
					level.sendParticles(Fx.GLOW, player.getX(), player.getY(1.0), player.getZ(), 2, 0.4, 0.5, 0.4, 0.0);
				}
			}
		}
	}
}
