package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.9) Travel between milestones: our own take on Via Romana's idea (its licence bars a port; nothing copied). A
 * King's Road's two milestones ({@link Roads}) are linked from the start; players link signs of their own by charting a
 * road they've built between them with a {@link #CHARTER}: mark the first sign, follow the road (most of the way on road
 * blocks: paths, gravel, cobble, bricks, planks, slabs), mark the other.
 *
 * <p>Right-clicking a linked sign (sneak to edit your own instead) lists where its roads go, each two ways to get there:
 * a <b>coach</b> (cheap: a driver, his horse and a carriage come for you and drive you along the road itself, at a
 * gallop, the fare to the nearest realm) or <b>teleport</b> (dear: the fare is lost to the road, you're there after a
 * moment's blackout, worn out as if you'd walked it).
 */
public final class Milestones {
	/** Charting: a sample every this many ticks; one this far from the last means the road's lost (a pearl, a fall). */
	static final int SAMPLE_TICKS = 10;
	static final double LEAST_MOVE = 2.0, LOST_GAP = 24.0;
	/** A charted road must be at least this long, this much of it on road blocks, end this near the sign it's marked at. */
	static final double CHART_LEAST = 48.0, ON_ROAD = 0.8, END_NEAR = 16.0;
	static final int CHART_MOST = 6000;
	/** Fares (emeralds): a base and one more every so many blocks, at most so many. */
	static final int COACH_BASE = 1, COACH_PER = 160, COACH_MOST = 12;
	static final int TELEPORT_BASE = 4, TELEPORT_PER = 48, TELEPORT_MOST = 40;
	/** Teleporting wears you out like the walk: exhaustion per 100 blocks (at most 40), and a blackout. */
	static final float WEAR_PER_100 = 1.6F;
	static final int BLACKOUT_TICKS = 40, TELEPORT_COOLDOWN = 600;
	/** A sign's menu is only for those standing this near it. */
	static final double MENU_REACH = 8.0;
	/** The coach, setting off: how fast its driver urges the horse, how far ahead on the road he makes for. */
	static final double COACH_SPEED = 1.9, COACH_AHEAD = 18.0;
	/** Stuck this long (no further along), it's helped this far on; an empty coach goes after this long; at most this long a trip. */
	static final int STUCK_TICKS = 100, EMPTY_TICKS = 200, TRIP_MOST = 24000;
	static final double HOP = 10.0;
	/** Its driver's task. */
	static final String TASK = "post_coach";

	public static Item CHARTER;

	/** A road a player charted between two signs: their positions and the way it runs (packed positions, a to b). */
	public record Link(long a, long b, List<Long> points) {
		static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("a").forGetter(Link::a),
				Codec.LONG.fieldOf("b").forGetter(Link::b),
				Codec.LONG.listOf().fieldOf("points").forGetter(Link::points))
			.apply(i, Link::new));
	}

	public record Charted(List<Link> links) {
		static final Charted EMPTY = new Charted(List.of());
		static final Codec<Charted> CODEC = RecordCodecBuilder.create(i -> i.group(
				Link.CODEC.listOf().optionalFieldOf("links", List.of()).forGetter(Charted::links))
			.apply(i, Charted::new));
	}

	public static final AttachmentType<Charted> CHARTED = AttachmentRegistry.create(JaymosKingdoms.id("charted_roads"),
		builder -> builder.persistent(Charted.CODEC));

	/**
	 * Where a road from a sign goes: the far sign (a King's Road: the town at its end), its name, the way (from here to
	 * there), and whose coach serves it (a King's Road: the realm it starts from; -1, a charted road: the nearest realm's).
	 */
	record Way(BlockPos to, Component name, List<Long> points, int realm) {
	}

	/** A chart being made: the sign it started at, the way so far, how much of it was on road. */
	private static final class Chart {
		final BlockPos from;
		final List<Long> points = new ArrayList<>();
		int on, all;

		Chart(BlockPos from) {
			this.from = from;
		}
	}

	/** A coach on a trip. */
	private static final class Trip {
		final UUID rider;
		final Roads.Line line;
		final Component to;
		final long started;
		double best;
		long improvedAt, emptySince = -1;
		/** (0.7.266) Its fare's been set down (or turned back): it only waits a moment, then goes. */
		boolean over;

		Trip(UUID rider, Roads.Line line, Component to, long now) {
			this.rider = rider;
			this.line = line;
			this.to = to;
			this.started = now;
			this.improvedAt = now;
			this.best = Double.MAX_VALUE;
		}
	}

	private static final Map<UUID, Chart> CHARTS = new HashMap<>();
	private static final Map<UUID, Trip> TRIPS = new HashMap<>();
	private static final Map<UUID, Long> TELEPORTED = new HashMap<>();

	private Milestones() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("road_charter"));
		CHARTER = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.MAP, CHARTER));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			boolean charter = player.getItemInHand(hand).is(CHARTER);
			if (!(level.getBlockEntity(hit.getBlockPos()) instanceof SignBlockEntity)) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
				return charter ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos().immutable();
			if (charter) {
				chart(server, sp, pos);
				return InteractionResult.SUCCESS;
			}
			if (player.isShiftKeyDown() || server != Factions.level(server.getServer())) {
				return InteractionResult.PASS;
			}
			List<Way> ways = ways(server, pos);
			if (ways.isEmpty()) {
				return InteractionResult.PASS;
			}
			menu(server, sp, pos, ways);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SAMPLE_TICKS == 0 && !CHARTS.isEmpty()) {
				sample(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CHARTS.clear();
			TRIPS.clear();
			TELEPORTED.clear();
		});
	}

	static Charted charted(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(CHARTED, Charted.EMPTY);
	}

	// ---- charting ----------------------------------------------------------------------------------------------------

	private static void chart(ServerLevel level, ServerPlayer player, BlockPos sign) {
		if (level != Factions.level(level.getServer())) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.charter.overworld").withStyle(ChatFormatting.GRAY));
			return;
		}
		Chart chart = CHARTS.get(player.getUUID());
		if (chart == null) {
			Chart c = new Chart(sign);
			c.points.add(sign.asLong());
			CHARTS.put(player.getUUID(), c);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.charter.begun").withStyle(ChatFormatting.GOLD));
			level.playSound(null, sign, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
			return;
		}
		if (chart.from.equals(sign)) {
			CHARTS.remove(player.getUUID());
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.charter.cancelled").withStyle(ChatFormatting.GRAY));
			return;
		}
		CHARTS.remove(player.getUUID());
		long last = chart.points.getLast();
		double length = new Roads.Line(chart.points).length();
		double share = chart.all == 0 ? 0.0 : (double)chart.on / chart.all;
		if (Math.hypot(BlockPos.getX(last) - sign.getX(), BlockPos.getZ(last) - sign.getZ()) > END_NEAR) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.charter.not_here").withStyle(ChatFormatting.RED));
			return;
		}
		if (length < CHART_LEAST) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.charter.short", (int)CHART_LEAST).withStyle(ChatFormatting.RED));
			return;
		}
		if (share < ON_ROAD) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.charter.off_road", (int)Math.round(share * 100), (int)Math.round(ON_ROAD * 100))
				.withStyle(ChatFormatting.RED));
			return;
		}
		List<Long> points = new ArrayList<>(chart.points);
		points.add(sign.asLong());
		MinecraftServer server = level.getServer();
		List<Link> links = new ArrayList<>(charted(server).links());
		long a = chart.from.asLong(), b = sign.asLong();
		links.removeIf(l -> (l.a() == a && l.b() == b) || (l.a() == b && l.b() == a));
		links.add(new Link(a, b, points));
		Factions.level(server).setAttached(CHARTED, new Charted(links));
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.charter.linked", (int)length).withStyle(ChatFormatting.GOLD));
		level.playSound(null, sign, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sign.getX() + 0.5, sign.getY() + 0.8, sign.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.0);
	}

	/** Every charter's way so far, a step on; the road's lost if they've jumped. */
	private static void sample(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		for (var it = CHARTS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Chart chart = e.getValue();
			if (player == null || player.level() != level || !player.getMainHandItem().is(CHARTER) && !player.getOffhandItem().is(CHARTER)) {
				if (player != null) {
					player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.charter.put_away").withStyle(ChatFormatting.GRAY));
				}
				it.remove();
				continue;
			}
			Entity mover = player.getRootVehicle();
			BlockPos at = mover.blockPosition();
			long last = chart.points.getLast();
			double d = Math.hypot(BlockPos.getX(last) - at.getX(), BlockPos.getZ(last) - at.getZ());
			if (d < LEAST_MOVE) {
				continue;
			}
			if (d > LOST_GAP || chart.points.size() >= CHART_MOST) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.charter.lost").withStyle(ChatFormatting.RED));
				it.remove();
				continue;
			}
			chart.points.add(at.asLong());
			chart.all++;
			if (road(level.getBlockState(at)) || road(level.getBlockState(at.below()))) {
				chart.on++;
			}
		}
	}

	/** A road block: a path, gravel, cobble, bricks, tiles, planks (bridges), slabs, stairs, smooth or cut stone. */
	static boolean road(BlockState s) {
		if (s.is(BlockTags.PLANKS) || s.is(BlockTags.SLABS) || s.is(BlockTags.STAIRS) || s.is(BlockTags.STONE_BRICKS)) {
			return true;
		}
		String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		for (String part : new String[] {"path", "gravel", "cobble", "brick", "tile", "polished", "smooth", "cut_", "coarse_dirt",
			"packed_mud", "concrete", "andesite", "pavement", "road"}) {
			if (id.contains(part)) {
				return true;
			}
		}
		return false;
	}

	// ---- where a sign's roads go --------------------------------------------------------------------------------------

	/** The roads from the sign at {@code pos}: a King's Road whose milestone it is, and every road charted from it. */
	static List<Way> ways(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		List<Way> out = new ArrayList<>();
		long here = pos.asLong();
		for (Roads.Road r : Roads.network(server).roads()) {
			boolean fromA = r.stoneA() == here, fromB = r.stoneB() == here;
			if (!fromA && !fromB) {
				continue;
			}
			Faction there = Factions.get(server, fromA ? r.b() : r.a());
			if (there == null || there.fallen()) {
				continue;
			}
			List<Long> points = new ArrayList<>(r.points());
			if (fromB) {
				java.util.Collections.reverse(points);
			}
			// (it ends in the town itself; its far milestone may not be set up yet, nobody having been that way)
			out.add(new Way(BlockPos.of(points.getLast()), Component.literal(there.name()), points, fromA ? r.a() : r.b()));
		}
		for (Link l : charted(server).links()) {
			boolean fromA = l.a() == here, fromB = l.b() == here;
			if (!fromA && !fromB) {
				continue;
			}
			BlockPos other = BlockPos.of(fromA ? l.b() : l.a());
			List<Long> points = new ArrayList<>(l.points());
			if (fromB) {
				java.util.Collections.reverse(points);
			}
			out.add(new Way(other, name(level, other), points, -1));
		}
		return out;
	}

	/** What a charted sign is called: the words on its face, else where it stands. */
	private static Component name(ServerLevel level, BlockPos sign) {
		if (level.isLoaded(sign) && level.getBlockEntity(sign) instanceof SignBlockEntity s) {
			StringBuilder words = new StringBuilder();
			for (Component line : s.getText(SignTextSlot.FRONT).getMessages(false)) {
				String text = line.getString().strip();
				if (!text.isEmpty()) {
					words.append(words.isEmpty() ? "" : " ").append(text);
				}
			}
			if (!words.isEmpty()) {
				return Component.literal(words.toString());
			}
		}
		return Component.translatable("message.jaymoskingdoms.milestone.at", sign.getX(), sign.getZ());
	}

	static int coachFare(double length) {
		return Math.min(COACH_MOST, COACH_BASE + (int)(length / COACH_PER));
	}

	static int teleportFare(double length) {
		return Math.min(TELEPORT_MOST, TELEPORT_BASE + (int)(length / TELEPORT_PER));
	}

	private static void menu(ServerLevel level, ServerPlayer player, BlockPos sign, List<Way> ways) {
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.milestone.menu").withStyle(ChatFormatting.GOLD));
		for (int i = 0; i < ways.size(); i++) {
			Way w = ways.get(i);
			int length = (int)new Roads.Line(w.points()).length();
			String at = sign.getX() + " " + sign.getY() + " " + sign.getZ() + " " + i;
			MutableComponent line = Component.literal("  ").append(Component.translatable("message.jaymoskingdoms.milestone.way", w.name(), length)
				.withStyle(ChatFormatting.WHITE)).append(" ");
			line.append(button(Component.translatable("message.jaymoskingdoms.milestone.coach", coachFare(length)), "/kingdom travel coach " + at,
				Component.translatable("message.jaymoskingdoms.milestone.coach_hover")));
			line.append(" ");
			line.append(button(Component.translatable("message.jaymoskingdoms.milestone.teleport", teleportFare(length)), "/kingdom travel teleport " + at,
				Component.translatable("message.jaymoskingdoms.milestone.teleport_hover")));
			player.sendSystemMessage(line);
		}
	}

	private static Component button(Component text, String command, Component hover) {
		return text.copy().withStyle(style -> style.withColor(ChatFormatting.GREEN).withUnderlined(true)
			.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	// ---- going ---------------------------------------------------------------------------------------------------------

	/** {@code /kingdom travel coach|teleport <sign x y z> <way>}: what a milestone's offer runs when clicked. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("travel")
			.then(Commands.literal("coach").then(signArgs(c -> travel(c, false))))
			.then(Commands.literal("teleport").then(signArgs(c -> travel(c, true))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> signArgs(
		com.mojang.brigadier.Command<CommandSourceStack> run) {
		return Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("y", IntegerArgumentType.integer())
			.then(Commands.argument("z", IntegerArgumentType.integer()).then(Commands.argument("way", IntegerArgumentType.integer(0)).executes(run))));
	}

	private static int travel(CommandContext<CommandSourceStack> c, boolean teleport) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		BlockPos sign = new BlockPos(IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z"));
		int index = IntegerArgumentType.getInteger(c, "way");
		ServerLevel level = Factions.level(c.getSource().getServer());
		if (player.level() != level || !player.position().closerThan(Vec3.atCenterOf(sign), MENU_REACH)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.milestone.too_far").withStyle(ChatFormatting.RED));
			return 0;
		}
		List<Way> ways = ways(level, sign);
		if (index >= ways.size()) {
			return 0;
		}
		Way way = ways.get(index);
		level.getChunk(way.to().getX() >> 4, way.to().getZ() >> 4);
		if (way.realm() < 0 && !(level.getBlockEntity(way.to()) instanceof SignBlockEntity)) {
			// (the far sign's gone: the link with it)
			List<Link> links = new ArrayList<>(charted(level.getServer()).links());
			long gone = way.to().asLong();
			links.removeIf(l -> l.a() == gone || l.b() == gone);
			level.setAttached(CHARTED, new Charted(links));
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.milestone.gone").withStyle(ChatFormatting.RED));
			return 0;
		}
		return teleport ? teleport(level, player, way) : coach(level, player, sign, way);
	}

	private static boolean payLost(ServerPlayer player, int fare) {
		if (player.isCreative()) {
			return true;
		}
		if (Purse.count(player, Items.EMERALD) < fare) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.cant_pay", fare).withStyle(ChatFormatting.RED));
			return false;
		}
		Purse.take(player, Items.EMERALD, fare);
		return true;
	}

	/** There after a blackout, worn out, the fare gone. A horse ridden comes too. */
	private static int teleport(ServerLevel level, ServerPlayer player, Way way) {
		long now = level.getGameTime();
		Long last = TELEPORTED.get(player.getUUID());
		if (last != null && now - last < TELEPORT_COOLDOWN && !player.isCreative()) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.milestone.rest", (int)((TELEPORT_COOLDOWN - (now - last)) / 20)).withStyle(ChatFormatting.RED));
			return 0;
		}
		double length = new Roads.Line(way.points()).length();
		if (!payLost(player, teleportFare(length))) {
			return 0;
		}
		TELEPORTED.put(player.getUUID(), now);
		BlockPos to = Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, arrival(way)), 4);
		Entity mount = player.getVehicle() instanceof AbstractHorse h ? h : null;
		player.stopRiding();
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLACKOUT_TICKS, 0, false, false));
		player.causeFoodExhaustion(Math.min(40.0F, (float)(length / 100.0 * WEAR_PER_100)));
		float yaw = player.getYRot();
		if (mount != null) {
			mount.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, Set.of(), yaw, 0.0F, false);
		}
		player.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, Set.of(), yaw, player.getXRot(), false);
		if (mount != null) {
			player.startRiding(mount);
		}
		level.playSound(null, to, SoundEvents.HORSE_GALLOP, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.milestone.arrived", way.name(), (int)length).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** Where travellers come out at the far end: on the road, a few blocks short of its end. */
	private static BlockPos arrival(Way way) {
		Roads.Line line = new Roads.Line(way.points());
		Vec3 at = line.at(Math.max(0.0, line.length() - 3.0));
		return BlockPos.containing(at);
	}

	/** A coach comes for you: its driver on his horse, the carriage behind, you aboard and away along the road. */
	private static int coach(ServerLevel level, ServerPlayer player, BlockPos sign, Way way) {
		MinecraftServer server = level.getServer();
		Faction realm = way.realm() >= 0 ? Factions.get(server, way.realm()) : null;
		if (realm == null || realm.fallen() || !realm.type().isRealm()) {
			realm = Factions.all(server).stream().filter(f -> f.type().isRealm() && !f.fallen())
				.min(java.util.Comparator.comparingDouble(f -> f.seat().distSqr(sign))).orElse(null);
		}
		if (realm == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.milestone.no_coach").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (player.isPassenger()) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.milestone.dismount").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Roads.Line line = new Roads.Line(way.points());
		int fare = coachFare(line.length());
		if (!Rickshaws.pay(player, realm, fare)) {
			return 0;
		}
		BlockPos at = Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(line.at(4.0))), 4);
		Vec3 ahead = line.at(12.0);
		float yaw = (float)(Math.atan2(-(ahead.x - at.getX() - 0.5), ahead.z - at.getZ() - 0.5) * (180.0 / Math.PI));
		// ((0.7.294) with MCA, one of the realm's people)
		Worker driver = Drivers.hire(level, realm, at, yaw, TASK, 4, "entity.jaymoskingdoms.coachman_of");
		if (driver == null) {
			return 0;
		}
		AbstractHorse horse = Cavalry.mount(level, driver, realm, true);
		if (horse == null) {
			driver.mob().discard();
			return 0;
		}
		horse.setYRot(yaw);
		Wagon coach = Wagon.hitch(level, Wagon.COACH, realm, horse, -1, 0);
		long now = level.getGameTime();
		TRIPS.put(coach.getUUID(), new Trip(player.getUUID(), line, way.name(), now));
		level.sendParticles(ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 12, 0.6, 0.5, 0.6, 0.02);
		player.startRiding(coach);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.milestone.coach_off", way.name(), fare).withStyle(ChatFormatting.GOLD));
		level.playSound(null, at, SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.0F);
		// (0.7.266) seen setting off along the road, then the road screen: the road itself is never driven
		BlockPos end = Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, arrival(way)), 4);
		Journeys.begin(level, player, coach, way.name(), end, (int)line.length(), fare, realm.id(), RIDE);
		return 1;
	}

	/** (0.7.266) A coach's fare set down at the far end, or turned back: it waits a moment, then it's gone. */
	private static final Journeys.Ride RIDE = new Journeys.Ride() {
		@Override
		public void arrived(ServerLevel level, ServerPlayer player, Wagon coach) {
			Trip trip = TRIPS.get(coach.getUUID());
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rickshaw.arrived", trip != null ? trip.to : Component.empty())
				.withStyle(ChatFormatting.GOLD));
			level.playSound(null, coach.blockPosition(), SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 0.8F, 1.0F);
			over(level, coach);
		}

		@Override
		public void turnedBack(ServerLevel level, ServerPlayer player, Wagon coach) {
			over(level, coach);
		}

		private void over(ServerLevel level, Wagon coach) {
			Trip trip = TRIPS.get(coach.getUUID());
			if (trip != null) {
				trip.over = true;
				trip.emptySince = level.getGameTime() - EMPTY_TICKS + 60;
			}
		}
	};

	/** One coach's tick (from {@link Wagon#tick}): along the road at a gallop; at the end, its fare set down and away it goes. */
	static void tick(ServerLevel level, Wagon coach, @Nullable Entity horse) {
		Trip trip = TRIPS.get(coach.getUUID());
		Worker w = Drivers.of(horse);
		net.minecraft.world.entity.PathfinderMob driver = w == null ? null : w.mob();
		long now = level.getGameTime();
		if (trip == null || horse == null || !horse.isAlive() || driver == null || now - trip.started > TRIP_MOST) {
			// (after a restart, or its horse or driver lost: it's gone)
			dismiss(level, coach, horse, driver);
			return;
		}
		Entity rider = coach.getFirstPassenger();
		if (!(rider instanceof ServerPlayer player) || !player.getUUID().equals(trip.rider)) {
			if (trip.emptySince < 0) {
				trip.emptySince = now;
			}
			driver.getNavigation().stop();
			if (now - trip.emptySince > EMPTY_TICKS) {
				dismiss(level, coach, horse, driver);
			}
			return;
		}
		if (trip.over || !Journeys.travelling(player)) {
			// (its trip's over: nobody rides it again)
			player.stopRiding();
			return;
		}
		trip.emptySince = -1;
		driver.setHomeTo(driver.blockPosition(), 64);
		if (Journeys.underway(coach)) {
			// (0.7.266) on the road screen: the coach waits here till the trip's done, then it's moved to the far end
			driver.getNavigation().stop();
			trip.improvedAt = now;
			return;
		}
		// (setting off, seen)
		double[] p = trip.line.project(horse.getX(), horse.getZ());
		double left = trip.line.length() - p[0];
		if (left < trip.best - 0.5) {
			trip.best = left;
			trip.improvedAt = now;
		}
		if (now - trip.improvedAt > STUCK_TICKS) {
			// (a river, a slope too steep: helped along the road)
			Vec3 on = trip.line.at(p[0] + HOP);
			BlockPos at = Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(on)), 3);
			level.sendParticles(ParticleTypes.POOF, horse.getX(), horse.getY() + 1.0, horse.getZ(), 12, 0.4, 0.5, 0.4, 0.02);
			driver.getNavigation().stop();
			horse.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			coach.trail(horse);
			trip.improvedAt = now;
			trip.best = Double.MAX_VALUE;
			return;
		}
		if (now % 10 == 0 || driver.getNavigation().isDone()) {
			Vec3 to = trip.line.at(p[0] + Math.max(p[1], COACH_AHEAD));
			BlockPos leg = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(to));
			driver.getNavigation().moveTo(leg.getX() + 0.5, leg.getY(), leg.getZ() + 0.5, COACH_SPEED);
		}
		if (now % 40 == 0 && horse instanceof AbstractHorse h) {
			h.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, false, false));
		}
	}

	private static void dismiss(ServerLevel level, Wagon coach, @Nullable Entity horse, @Nullable Entity driver) {
		coach.ejectPassengers();
		for (Entity e : new Entity[] {driver, horse, coach}) {
			if (e != null && !e.isRemoved()) {
				level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1.0, e.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
				// ((0.7.294) a villager coachman out of MCA's family tree too)
				McaCompat.vanish(e);
			}
		}
		TRIPS.remove(coach.getUUID());
	}

	/** A player right-clicked a coach. */
	static void hail(ServerPlayer player, Wagon coach) {
		Trip trip = TRIPS.get(coach.getUUID());
		if (trip != null && !trip.over && trip.rider.equals(player.getUUID()) && !player.isPassenger()) {
			player.startRiding(coach);
			return;
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.milestone.hired").withStyle(ChatFormatting.GRAY));
	}
}
