package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Chalk marks: the signs crooks leave. Each dawn the Underworld chalks signs on the real places they stand for
 * ({@link #generate}): a <b>safe house</b> by the speakeasy's hatch, a <b>guard who takes bribes</b> by a corruptible
 * guard's post ({@link Liars#wouldLie}), a <b>dog inside</b> by a tame wolf, a <b>rich and careless</b> mark on the door
 * of a well-off villager's home, and <b>danger</b> where something hostile prowls near a settlement.
 * <p>Only those the Underworld trusts can read them ({@link #reads}: a start that earned its trust, {@link StartPerks#trusted},
 * or anyone a speakeasy has let in, {@link #initiate}); everyone else sees a scribble where the sign is. Anyone can leave
 * their own with {@link #CHALK}; those who cannot read can only scribble. Dawn marks fade in {@link #FADE_DAYS} days, a
 * player's in {@link #KEEP_DAYS}.
 */
public final class ChalkMarks {
	/** The glyphs, in the order of their textures; {@link #SCRIBBLE} is what the unlettered see. */
	public static final int SAFE = 0, BRIBE = 1, DOG = 2, RICH = 3, DANGER = 4, SCRIBBLE = 5;
	public static final List<String> NAMES = List.of("safe", "bribe", "dog", "rich", "danger", "scribble");
	/** Days before the Underworld's marks and a player's fade, and the most marks the world keeps. */
	static final int FADE_DAYS = 3, KEEP_DAYS = 7, MOST = 320;
	/** The most of each sign chalked per realm a dawn, and how far from its seat the Underworld looks (blocks). */
	static final int PER_REALM = 2;
	static final double LOOK_RANGE = 90.0;
	/** A mark is seen from this far (blocks), and a player reaches this far to chalk. */
	public static final double SEEN_RANGE = 24.0, REACH = 6.0;

	/** One mark: the solid block it is on, which face, the glyph, the day it was made and whether a player made it. */
	public record Mark(BlockPos pos, int face, int glyph, long day, boolean mine) {
		public static final Codec<Mark> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Mark::pos),
			Codec.INT.fieldOf("face").forGetter(Mark::face),
			Codec.INT.fieldOf("glyph").forGetter(Mark::glyph),
			Codec.LONG.fieldOf("day").forGetter(Mark::day),
			Codec.BOOL.fieldOf("mine").forGetter(Mark::mine)).apply(i, Mark::new));
	}

	/** The marks of the world (kept on the overworld). */
	static final AttachmentType<List<Mark>> MARKS = AttachmentRegistry.create(JaymosKingdoms.id("chalk_marks"),
		builder -> builder.persistent(Mark.CODEC.listOf()));
	/** The last dawn the Underworld chalked. */
	static final AttachmentType<Long> CHALKED = AttachmentRegistry.create(JaymosKingdoms.id("chalk_day"), builder -> builder.persistent(Codec.LONG));
	/** Those a speakeasy let in: they read the signs. */
	public static final AttachmentType<Boolean> INITIATED = AttachmentRegistry.create(JaymosKingdoms.id("chalk_initiated"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	/** A mark as a client draws it. {@code glyph} is {@link #SCRIBBLE} to those who cannot read. */
	public record Seen(long pos, int face, int glyph) {
	}

	/** Server → player: the marks they can see, and whether they can read them. */
	public record View(boolean reads, List<Seen> marks) implements CustomPacketPayload {
		public static final Type<View> TYPE = new Type<>(JaymosKingdoms.id("chalk_view"));
		public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBoolean(p.reads);
				buf.writeVarInt(p.marks.size());
				for (Seen s : p.marks) {
					buf.writeLong(s.pos);
					buf.writeByte(s.face);
					buf.writeByte(s.glyph);
				}
			},
			buf -> {
				boolean reads = buf.readBoolean();
				int n = buf.readVarInt();
				List<Seen> marks = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					marks.add(new Seen(buf.readLong(), buf.readByte(), buf.readByte()));
				}
				return new View(reads, marks);
			});

		@Override
		public Type<View> type() {
			return TYPE;
		}
	}

	/** Server → player: chalk used on this face; pick a glyph. */
	public record Draw(BlockPos pos, int face, boolean reads) implements CustomPacketPayload {
		public static final Type<Draw> TYPE = new Type<>(JaymosKingdoms.id("chalk_draw"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Draw> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Draw::pos, ByteBufCodecs.VAR_INT, Draw::face, ByteBufCodecs.BOOL, Draw::reads, Draw::new);

		@Override
		public Type<Draw> type() {
			return TYPE;
		}
	}

	/** Player → server: chalk this glyph there. */
	public record Place(BlockPos pos, int face, int glyph) implements CustomPacketPayload {
		public static final Type<Place> TYPE = new Type<>(JaymosKingdoms.id("chalk_place"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Place> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Place::pos, ByteBufCodecs.VAR_INT, Place::face, ByteBufCodecs.VAR_INT, Place::glyph, Place::new);

		@Override
		public Type<Place> type() {
			return TYPE;
		}
	}

	public static Item CHALK;

	private ChalkMarks() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("chalk"));
		CHALK = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(16)) {
			@Override
			public InteractionResult useOn(UseOnContext context) {
				if (context.getLevel().isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
					return InteractionResult.SUCCESS;
				}
				if (ServerPlayNetworking.canSend(player, Draw.TYPE)) {
					ServerPlayNetworking.send(player, new Draw(context.getClickedPos(), context.getClickedFace().get3DDataValue(), reads(player)));
				}
				return InteractionResult.SUCCESS;
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(CHALK));
		PayloadTypeRegistry.clientboundPlay().register(View.TYPE, View.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Draw.TYPE, Draw.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Place.TYPE, Place.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Place.TYPE, (payload, context) -> place(context.player(), payload));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> view(handler.player));
		ServerTickEvents.END_SERVER_TICK.register(ChalkMarks::tick);
	}

	/** The Underworld lets {@code player} read its signs from now on. */
	static void initiate(ServerPlayer player) {
		if (!reads(player)) {
			player.setAttached(INITIATED, true);
			view(player);
		}
	}

	/** Whether {@code player} can read the signs. */
	public static boolean reads(net.minecraft.world.entity.player.Player player) {
		return StartPerks.trusted(player) || Boolean.TRUE.equals(player.getAttached(INITIATED));
	}

	private static List<Mark> marks(MinecraftServer server) {
		List<Mark> m = server.overworld().getAttached(MARKS);
		return m == null ? List.of() : m;
	}

	private static void view(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, View.TYPE)) {
			return;
		}
		boolean reads = reads(player);
		List<Seen> seen = new ArrayList<>();
		for (Mark m : marks(player.level().getServer())) {
			seen.add(new Seen(m.pos().asLong(), m.face(), reads ? m.glyph() : SCRIBBLE));
		}
		ServerPlayNetworking.send(player, new View(reads, seen));
	}

	private static void viewAll(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			view(p);
		}
	}

	private static void place(ServerPlayer player, Place p) {
		ServerLevel level = player.level();
		ItemStack chalk = player.getMainHandItem().is(CHALK) ? player.getMainHandItem() : player.getOffhandItem();
		Direction face = Direction.from3DDataValue(p.face());
		if (!chalk.is(CHALK) || player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p.pos())) > REACH * REACH * 4 || !level.getBlockState(p.pos()).isSolid()
			|| !level.getBlockState(p.pos().relative(face)).canBeReplaced()) {
			return;
		}
		int glyph = reads(player) && p.glyph() >= 0 && p.glyph() < SCRIBBLE ? p.glyph() : SCRIBBLE;
		List<Mark> all = new ArrayList<>(marks(level.getServer()));
		all.removeIf(m -> m.pos().equals(p.pos()) && m.face() == p.face());
		all.add(new Mark(p.pos(), p.face(), glyph, level.getGameTime() / 24000L, true));
		while (all.size() > MOST) {
			all.removeFirst();
		}
		level.getServer().overworld().setAttached(MARKS, List.copyOf(all));
		if (!player.isCreative()) {
			chalk.shrink(1);
		}
		level.playSound(null, p.pos(), SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.7F, 1.6F);
		viewAll(level.getServer());
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % 200 != 0) {
			return;
		}
		long day = now / 24000L;
		Long last = server.overworld().getAttached(CHALKED);
		if (last == null || last < day) {
			server.overworld().setAttached(CHALKED, day);
			generate(server, day);
		}
	}

	/** A wall of the solid nearest {@code around} that has air before it: the block, and the way its face looks. */
	private static @Nullable Mark wall(ServerLevel level, BlockPos around, int glyph, long day) {
		for (int r = 1; r <= 4; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					for (int dy = 0; dy <= 2; dy++) {
						BlockPos solid = around.offset(dx, dy, dz);
						if (!level.hasChunkAt(solid) || !level.getBlockState(solid).isSolid()) {
							continue;
						}
						for (Direction d : Direction.Plane.HORIZONTAL) {
							BlockPos front = solid.relative(d);
							if (level.getBlockState(front).isAir() && level.getBlockState(front.above()).isAir()) {
								return new Mark(solid, d.get3DDataValue(), glyph, day, false);
							}
						}
					}
				}
			}
		}
		return null;
	}

	/** The dawn's chalking: old marks fade, and each realm's Underworld marks what is true of it today. */
	static void generate(MinecraftServer server, long day) {
		ServerLevel level = server.overworld();
		List<Mark> all = new ArrayList<>(marks(server));
		all.removeIf(m -> day - m.day() >= (m.mine() ? KEEP_DAYS : FADE_DAYS));
		all.removeIf(m -> !m.mine() && day == m.day());
		for (Faction f : Factions.all(server)) {
			if (!f.type().isRealm() || f.fallen()) {
				continue;
			}
			Underworld.Den den = Underworld.builtDen(server, f);
			if (den == null) {
				continue;
			}
			BlockPos seat = f.seat();
			if (!level.hasChunkAt(seat)) {
				continue;
			}
			add(all, wall(level, den.hatch(), SAFE, day));
			AABB box = new AABB(seat).inflate(LOOK_RANGE);
			int n = 0;
			for (net.minecraft.world.entity.LivingEntity g : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box, e -> e.isAlive() && Downed.isGuard(e)
				&& Liars.wouldLie(e, Liars.Kind.ALIBI, day))) {
				if (n++ >= PER_REALM) {
					break;
				}
				add(all, wall(level, g.blockPosition(), BRIBE, day));
			}
			n = 0;
			for (Wolf w : level.getEntitiesOfClass(Wolf.class, box, w -> w.isAlive() && w.isTame())) {
				if (n++ >= PER_REALM) {
					break;
				}
				add(all, wall(level, w.blockPosition(), DOG, day));
			}
			n = 0;
			for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isBaby() && v.getVillagerData().level() >= 4)) {
				if (n >= PER_REALM) {
					break;
				}
				net.minecraft.core.GlobalPos home = v.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
				if (home != null && home.dimension() == level.dimension()) {
					n++;
					add(all, wall(level, home.pos(), RICH, day));
				}
			}
			n = 0;
			for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, m -> m instanceof Enemy && m.isAlive() && m.onGround())) {
				if (n++ >= PER_REALM) {
					break;
				}
				add(all, wall(level, m.blockPosition(), DANGER, day));
			}
		}
		while (all.size() > MOST) {
			all.removeFirst();
		}
		level.setAttached(MARKS, List.copyOf(all));
		viewAll(server);
	}

	private static void add(List<Mark> all, @Nullable Mark mark) {
		if (mark != null) {
			all.removeIf(m -> m.pos().equals(mark.pos()) && m.face() == mark.face());
			all.add(mark);
		}
	}
}
