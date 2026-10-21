package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.116) The surveyor's rod: the one tool for laying out what a settlement's workers do. Sneak and use it at the
 * sky to change what it marks; use it on two blocks (two corners) to mark, or on a chest for storage:
 * <ul>
 * <li><b>Design</b>: the two corners of something you built, to save it as a design ({@code /kingdom design save
 * <name>}, see {@link Designs}).</li>
 * <li><b>Plot</b>: a plot for the builders, any size from {@link Plots#MIN_SIDE} to {@link Plots#MAX_SIDE} (the
 * {@link Plots} stake only marks a square).</li>
 * <li><b>Field</b>: crops for a farmer to keep ({@link Areas#field}).</li>
 * <li><b>Quarry</b>: a pit for the miners to dig out, layer by layer ({@link Areas#quarry}).</li>
 * <li><b>Storage</b>: a chest or barrel added to (or taken out of) the settlement's storage, where builders fetch
 * materials and workers bring what they gather ({@link Areas#toggleStorage}).</li>
 * </ul>
 * While you hold it, your corners are shown with sparks.
 */
public final class Surveyor {
	/** How often the corners held are shown (ticks). */
	static final int SHOW_TICKS = 20;

	public enum Mode {
		DESIGN, PLOT, FIELD, QUARRY, STORAGE;

		String key() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}

		Mode next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}

	/** A player's corners: the first, and the second once set (null until then). */
	public record Selection(@Nullable BlockPos a, @Nullable BlockPos b) {
	}

	static Item ROD;
	private static final Map<UUID, Mode> MODES = new HashMap<>();
	private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

	private Surveyor() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("surveyor_rod"));
		ROD = Registry.register(BuiltInRegistries.ITEM, key, new Rod(new Item.Properties().setId(key).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Plots.PLOT_STAKE, ROD));
		ServerTickEvents.END_SERVER_TICK.register(Surveyor::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MODES.clear();
			SELECTIONS.clear();
			Areas.clear();
		});
	}

	static Mode mode(Player player) {
		return MODES.getOrDefault(player.getUUID(), Mode.DESIGN);
	}

	static @Nullable Selection selection(Player player) {
		return SELECTIONS.get(player.getUUID());
	}

	static BlockPos min(BlockPos a, BlockPos b) {
		return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
	}

	static BlockPos max(BlockPos a, BlockPos b) {
		return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
	}

	static final class Rod extends Item {
		Rod(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			if (player.isShiftKeyDown()) {
				Mode next = mode(player).next();
				MODES.put(player.getUUID(), next);
				SELECTIONS.remove(player.getUUID());
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.surveyor.mode", Component.translatable("surveyor.jaymoskingdoms." + next.key()))
					.withStyle(ChatFormatting.GOLD));
				sp.sendSystemMessage(Component.translatable("surveyor.jaymoskingdoms." + next.key() + ".how").withStyle(ChatFormatting.GRAY));
				level.playSound(null, player.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 0.4F, 1.8F);
			} else {
				SELECTIONS.remove(player.getUUID());
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.surveyor.cleared",
					Component.translatable("surveyor.jaymoskingdoms." + mode(player).key())).withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getPlayer() instanceof ServerPlayer player)) {
				return InteractionResult.SUCCESS;
			}
			if (player.isShiftKeyDown()) {
				// (sneaking changes the mode, wherever it's pointed)
				return this.use(player.level(), player, context.getHand());
			}
			return click(player, context.getClickedPos()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}
	}

	/** {@code player} used the rod on {@code pos}. True if it took. */
	static boolean click(ServerPlayer player, BlockPos pos) {
		Mode mode = mode(player);
		if (mode == Mode.STORAGE) {
			return Areas.toggleStorage(player, pos);
		}
		Selection sel = SELECTIONS.get(player.getUUID());
		if (sel == null || sel.a() == null || sel.b() != null) {
			SELECTIONS.put(player.getUUID(), new Selection(pos.immutable(), null));
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.surveyor.first", pos.toShortString()).withStyle(ChatFormatting.YELLOW));
			player.level().playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 0.5F, 1.4F);
			return true;
		}
		BlockPos a = sel.a(), b = pos.immutable();
		SELECTIONS.put(player.getUUID(), new Selection(a, b));
		player.level().playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 0.5F, 1.0F);
		BlockPos min = min(a, b), max = max(a, b);
		int w = max.getX() - min.getX() + 1, d = max.getZ() - min.getZ() + 1, h = max.getY() - min.getY() + 1;
		boolean done = switch (mode) {
			case DESIGN -> {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.surveyor.design", w, d, h).withStyle(ChatFormatting.GREEN));
				yield false;
			}
			case PLOT -> Plots.markBox(player, a, b);
			case FIELD -> Areas.field(player, a, b);
			case QUARRY -> Areas.quarry(player, a, b);
			default -> false;
		};
		if (done) {
			SELECTIONS.remove(player.getUUID());
		}
		return true;
	}

	// ---- showing the corners --------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SHOW_TICKS != 0 || SELECTIONS.isEmpty()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Selection sel = SELECTIONS.get(player.getUUID());
			if (sel == null || sel.a() == null || !(holding(player.getMainHandItem()) || holding(player.getOffhandItem()))) {
				continue;
			}
			BlockPos b = sel.b() != null ? sel.b() : sel.a();
			if (sel.a().distSqr(player.blockPosition()) > 96 * 96) {
				continue;
			}
			outline(player.level(), min(sel.a(), b), max(sel.a(), b));
		}
	}

	private static boolean holding(ItemStack stack) {
		return ROD != null && stack.is(ROD);
	}

	/** Sparks along the edges of the box from {@code min} to {@code max} (blocks, inclusive). */
	static void outline(ServerLevel level, BlockPos min, BlockPos max) {
		double x0 = min.getX(), y0 = min.getY(), z0 = min.getZ(), x1 = max.getX() + 1, y1 = max.getY() + 1, z1 = max.getZ() + 1;
		for (double x = x0; x <= x1; x++) {
			for (double[] yz : new double[][] {{y0, z0}, {y0, z1}, {y1, z0}, {y1, z1}}) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, yz[0], yz[1], 1, 0, 0, 0, 0);
			}
		}
		for (double y = y0; y <= y1; y++) {
			for (double[] xz : new double[][] {{x0, z0}, {x0, z1}, {x1, z0}, {x1, z1}}) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, xz[0], y, xz[1], 1, 0, 0, 0, 0);
			}
		}
		for (double z = z0; z <= z1; z++) {
			for (double[] xy : new double[][] {{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}}) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, xy[0], xy[1], z, 1, 0, 0, 0, 0);
			}
		}
	}
}
