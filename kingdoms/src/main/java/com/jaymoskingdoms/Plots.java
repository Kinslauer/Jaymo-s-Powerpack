package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Plots: ground a lord of a faction marks for its builders (with {@code /kingdom plot mark} or a plot stake). The
 * builders never touch anything built outside them; on a plot they may clear whatever stands there (it goes into the
 * faction's storage) and build, even over the lord's own builds. The lord may choose what goes up on it
 * ({@code /kingdom plot build <building>}); otherwise it gets the next building in the growth order that fits.
 */
public final class Plots {
	/** A marked rectangle's smallest and largest side; a plot stake marks a square this size (sneaking: the chunk). */
	static final int MIN_SIDE = 5, MAX_SIDE = 32, STAKE_SIDE = 11;
	/** Who may mark plots: members of this rank or above (creative players too, for testing). */
	static final Rank MARK_RANK = Rank.LORD;

	static Item PLOT_STAKE;

	private Plots() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("plot_stake"));
		PLOT_STAKE = Registry.register(BuiltInRegistries.ITEM, key, new Stake(new Item.Properties().setId(key).stacksTo(16)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.LEAD, PLOT_STAKE));
	}

	/** The plot stake: used on the ground in your faction's land, it marks a plot there. */
	static final class Stake extends Item {
		Stake(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getPlayer() instanceof ServerPlayer player)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos at = context.getClickedPos();
			boolean ok = mark(player, at, STAKE_SIDE, STAKE_SIDE, player.isShiftKeyDown());
			if (ok && !player.isCreative()) {
				context.getItemInHand().shrink(1);
			}
			return ok ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}
	}

	/** {@code player} may mark plots for {@code faction}. */
	static boolean mayMark(ServerPlayer player, Faction faction) {
		if (player.isCreative()) {
			return true;
		}
		Faction.Member member = faction.member(player.getUUID());
		return member != null && Rank.forStanding(member.standing()).ordinal() >= MARK_RANK.ordinal();
	}

	/**
	 * Marks a plot round {@code ground} (the block stood on): {@code w} x {@code d}, or the whole chunk. Tells the
	 * player how it went; true if marked.
	 */
	static boolean mark(ServerPlayer player, BlockPos ground, int w, int d, boolean chunk) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = player.level();
		Faction f = Factions.at(server, level, ground);
		if (f == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.not_land").withStyle(ChatFormatting.RED));
			return false;
		}
		if (!mayMark(player, f)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.lords_only", f.name()).withStyle(ChatFormatting.RED));
			return false;
		}
		BlockPos min, max;
		if (chunk) {
			ChunkPos c = ChunkPos.containing(ground);
			min = new BlockPos(c.getMinBlockX(), ground.getY(), c.getMinBlockZ());
			max = new BlockPos(c.getMinBlockX() + 15, ground.getY(), c.getMinBlockZ() + 15);
		} else {
			min = new BlockPos(ground.getX() - (w - 1) / 2, ground.getY(), ground.getZ() - (d - 1) / 2);
			max = min.offset(w - 1, 0, d - 1);
		}
		for (BlockPos corner : new BlockPos[] {min, max, new BlockPos(min.getX(), 0, max.getZ()), new BlockPos(max.getX(), 0, min.getZ())}) {
			if (!f.claims(ChunkPos.containing(corner))) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.outside", f.name()).withStyle(ChatFormatting.RED));
				return false;
			}
		}
		for (var e : Works.all(server).entrySet()) {
			for (Works.Plot other : e.getValue().plots()) {
				if (min.getX() <= other.max().getX() && max.getX() >= other.min().getX() && min.getZ() <= other.max().getZ() && max.getZ() >= other.min().getZ()) {
					player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.overlap", other.id()).withStyle(ChatFormatting.RED));
					return false;
				}
			}
		}
		int id = Works.of(server, f.id()).plots().stream().mapToInt(Works.Plot::id).max().orElse(0) + 1;
		Works.Plot plot = new Works.Plot(id, min, max, player.getUUID(), Optional.empty(), false);
		Works.update(server, f.id(), s -> {
			List<Works.Plot> plots = new ArrayList<>(s.plots());
			plots.add(plot);
			return s.withPlots(plots);
		});
		outline(level, plot);
		Construction.stopWaiting(f);
		level.playSound(null, ground, SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 1.0F, 0.9F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.marked", id, plot.width(), plot.depth(), f.name())
			.withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Shows a plot's edge with a line of sparks. */
	static void outline(ServerLevel level, Works.Plot plot) {
		double y = plot.min().getY() + 1.2;
		for (int x = plot.min().getX(); x <= plot.max().getX(); x++) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x + 0.5, y, plot.min().getZ() + 0.5, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x + 0.5, y, plot.max().getZ() + 0.5, 1, 0, 0, 0, 0);
		}
		for (int z = plot.min().getZ(); z <= plot.max().getZ(); z++) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, plot.min().getX() + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, plot.max().getX() + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
		}
	}

	/** The plot {@code player} stands on, with its faction, if they may manage it (else tells them why, and null). */
	static Map.@Nullable Entry<Faction, Works.Plot> managed(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		var here = Works.plotAt(server, player.blockPosition());
		if (here == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.none_here").withStyle(ChatFormatting.RED));
			return null;
		}
		Faction f = Factions.get(server, here.getKey());
		if (f == null) {
			return null;
		}
		if (!mayMark(player, f)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.lords_only", f.name()).withStyle(ChatFormatting.RED));
			return null;
		}
		return Map.entry(f, here.getValue());
	}

	/** Unmarks the plot {@code player} stands on (its project, if any, stops where it is). */
	static boolean clear(ServerPlayer player) {
		var managed = managed(player);
		if (managed == null) {
			return false;
		}
		Faction f = managed.getKey();
		Works.Plot plot = managed.getValue();
		ServerLevel level = Factions.level(player.level().getServer());
		for (Works.Project p : Works.of(level.getServer(), f.id()).projects()) {
			if (p.plot() == plot.id()) {
				Construction.cancel(level, f, p);
			}
		}
		Works.update(level.getServer(), f.id(), s -> {
			List<Works.Plot> plots = new ArrayList<>(s.plots());
			plots.removeIf(x -> x.id() == plot.id());
			return s.withPlots(plots);
		});
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.cleared", plot.id()).withStyle(ChatFormatting.YELLOW));
		return true;
	}

	/** Chooses what goes up on the plot {@code player} stands on. */
	static boolean choose(ServerPlayer player, String building) {
		var managed = managed(player);
		if (managed == null) {
			return false;
		}
		Faction f = managed.getKey();
		Works.Plot plot = managed.getValue();
		MinecraftServer server = player.level().getServer();
		boolean known = Blueprint.catalogue(f.type()).stream().anyMatch(e -> e.id().equals(building));
		if (!known) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.unknown", building).withStyle(ChatFormatting.RED));
			return false;
		}
		if (Works.of(server, f.id()).projects().stream().anyMatch(p -> p.plot() == plot.id())) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.busy", plot.id()).withStyle(ChatFormatting.RED));
			return false;
		}
		Blueprint.Entry entry = Planner.entry(f, building);
		if (entry != null && Planner.onPlot(f, plot, entry.maker().make(Factions.level(server), f)) == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.too_small", plot.id()).withStyle(ChatFormatting.RED));
			return false;
		}
		Works.update(server, f.id(), s -> {
			List<Works.Plot> plots = new ArrayList<>(s.plots());
			plots.replaceAll(x -> x.id() == plot.id() ? x.withBuilding(Optional.of(building), false) : x);
			return s.withPlots(plots);
		});
		Construction.stopWaiting(f);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.chosen", plot.id(),
			Component.translatableWithFallback("building.jaymoskingdoms." + building, building)).withStyle(ChatFormatting.GREEN));
		return true;
	}
}
