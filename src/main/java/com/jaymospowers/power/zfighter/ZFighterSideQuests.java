package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.61, Andy's NOEA picks) Side quests: short jobs a master or the Keeper hands out, as many again as you like (a few
 * a day). Sneak and right-click one holding anything: with a job done he pays out, with goods to hand over he takes
 * them, with room for more he sets you one. Three kinds: defeat a number of hostile creatures ({@link #FOES}; the party
 * members within {@link ZFighterSagas#PARTY_RANGE} blocks of the one who struck the blow count too), hand over goods
 * ({@link #DELIVER}), and win sparring bouts against the Sparring Dummy ({@link #SPAR}). A master also pays reputation with
 * his kingdom (told to Jaymo's Kingdoms through {@link ZFighterKingdoms#changeReputation}, only if it is loaded). When a
 * job can't be offered (too many, or enough for today) and Kingdoms is loaded, the same gesture opens that villager's
 * own town errands instead. Jobs are saved on the player ({@link #DATA}) and told to his client.
 */
public final class ZFighterSideQuests {
	public static final int FOES = 0, DELIVER = 1, SPAR = 2;
	public static final int KINDS = 3;

	// ---- tuning ----
	/** Jobs held at once, and jobs finished in a day (game days) before the next one waits. */
	public static final int MAX_JOBS = 3;
	static final int DAILY_CAP = 8;
	static final long DAY = 24000L;
	/** Foes to defeat (min, max). */
	static final int FOES_MIN = 8, FOES_MAX = 16;
	/** Goods to hand over: the items, how many (a share of a stack), and TP for each one. */
	static final String[] GOODS = {"minecraft:wheat", "minecraft:iron_ingot", "minecraft:cooked_beef", "minecraft:gold_ingot", "minecraft:coal",
		"minecraft:bread"};
	static final int[] GOODS_COUNT = {32, 12, 16, 8, 32, 24};
	static final int[] GOODS_TP = {3, 12, 8, 14, 3, 4};
	/** Bouts to win (min, max), and TP for each. */
	static final int SPAR_MIN = 1, SPAR_MAX = 2;
	static final int SPAR_TP = 80;
	/** TP: a base and then so much for each foe. */
	static final int FOES_BASE_TP = 40, FOES_TP = 6;
	/** Reputation with the master's kingdom for a job (only with Jaymo's Kingdoms). */
	static final int REPUTATION = 25;
	/** The kingdom must be within this many blocks of him to think better of him. */
	static final double REPUTATION_RANGE = 600.0;
	/** A job's item prize (a Hermit Bean) comes up one time in this many. */
	static final int BEAN_ONE_IN = 6;

	/** A job: its kind, what it asks (goods: which), how many, how many so far, and who gave it (a name). */
	public record Job(int kind, int param, int need, int have, String giver) {
		public static final Codec<Job> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("kind").forGetter(Job::kind),
			Codec.INT.optionalFieldOf("param", 0).forGetter(Job::param),
			Codec.INT.fieldOf("need").forGetter(Job::need),
			Codec.INT.optionalFieldOf("have", 0).forGetter(Job::have),
			Codec.STRING.optionalFieldOf("giver", "").forGetter(Job::giver)).apply(i, Job::new));
		public static final StreamCodec<ByteBuf, Job> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Job::kind,
			ByteBufCodecs.VAR_INT, Job::param,
			ByteBufCodecs.VAR_INT, Job::need,
			ByteBufCodecs.VAR_INT, Job::have,
			ByteBufCodecs.STRING_UTF8, Job::giver,
			Job::new);

		Job with(int count) {
			return new Job(kind, param, need, count, giver);
		}
	}

	/** The jobs he holds, the day (game time / a day) last counted and how many he finished that day. */
	public record Data(List<Job> jobs, long day, int today) {
		public static final Data NONE = new Data(List.of(), -1L, 0);
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
			Job.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(Data::jobs),
			Codec.LONG.optionalFieldOf("day", -1L).forGetter(Data::day),
			Codec.INT.optionalFieldOf("today", 0).forGetter(Data::today)).apply(i, Data::new));
		public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
			Job.STREAM_CODEC.apply(ByteBufCodecs.list()), Data::jobs,
			ByteBufCodecs.VAR_LONG, Data::day,
			ByteBufCodecs.VAR_INT, Data::today,
			Data::new);
	}

	public static final AttachmentType<Data> DATA = AttachmentRegistry.create(JaymosPowers.id("zfighter_jobs"),
		builder -> builder.persistent(Data.CODEC).copyOnDeath().syncWith(Data.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private ZFighterSideQuests() {
	}

	public static Data of(Player player) {
		Data data = player.getAttached(DATA);
		return data == null ? Data.NONE : data;
	}

	/** The line for a job. */
	public static Component describe(Job job) {
		return switch (job.kind()) {
			case DELIVER -> Component.translatable("saga.jaymospowers.job.deliver", job.need(), item(job.param()));
			case SPAR -> Component.translatable("saga.jaymospowers.job.spar", job.need());
			default -> Component.translatable("saga.jaymospowers.job.foes", job.need());
		};
	}

	private static Component item(int goods) {
		Identifier id = Identifier.tryParse(GOODS[Math.floorMod(goods, GOODS.length)]);
		Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
		return new ItemStack(item).getHoverName();
	}

	// ---------------------------------------------------------------- the gesture

	/** A master or the Keeper is spoken to: goods handed over, a job paid, a new one set. True if anything was said or done. */
	static boolean talk(ServerPlayer player, Entity giver, boolean keeper) {
		Data data = of(player);
		long day = player.level().getServer().overworld().getGameTime() / DAY;
		if (data.day() != day) {
			data = new Data(data.jobs(), day, 0);
			player.setAttached(DATA, data);
		}
		boolean acted = false;
		// goods first
		for (Job job : new ArrayList<>(data.jobs())) {
			if (job.kind() == DELIVER) {
				int gave = take(player, GOODS[job.param()], job.need() - job.have());
				if (gave > 0) {
					acted = true;
					Job now = job.with(job.have() + gave);
					player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_job_gave", gave, item(job.param()), now.have(), now.need())
						.withStyle(ChatFormatting.AQUA));
					replace(player, job, now);
					if (now.have() >= now.need()) {
						complete(player, now);
					}
				}
			}
		}
		if (acted) {
			return true;
		}
		data = of(player);
		if (data.jobs().size() >= MAX_JOBS || data.today() >= DAILY_CAP) {
			if (!data.jobs().isEmpty()) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_job_busy", data.jobs().size()).withStyle(ChatFormatting.GRAY));
				return true;
			}
			return false;
		}
		Job job = roll(player, giver, data);
		List<Job> next = new ArrayList<>(data.jobs());
		next.add(job);
		player.setAttached(DATA, new Data(List.copyOf(next), data.day(), data.today()));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_job_new", giver.getDisplayName(), describe(job)).withStyle(ChatFormatting.AQUA));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
		return true;
	}

	/** A new job, never the same as one he already holds. */
	private static Job roll(ServerPlayer player, Entity giver, Data data) {
		var random = player.level().getRandom();
		String name = giver.getDisplayName().getString();
		for (int tries = 0; ; tries++) {
			int kind = random.nextInt(KINDS);
			Job job;
			if (kind == DELIVER) {
				int goods = random.nextInt(GOODS.length);
				job = new Job(DELIVER, goods, Math.max(1, GOODS_COUNT[goods] - random.nextInt(Math.max(1, GOODS_COUNT[goods] / 4))), 0, name);
			} else if (kind == SPAR) {
				job = new Job(SPAR, 0, SPAR_MIN + random.nextInt(SPAR_MAX - SPAR_MIN + 1), 0, name);
			} else {
				job = new Job(FOES, 0, FOES_MIN + random.nextInt(FOES_MAX - FOES_MIN + 1), 0, name);
			}
			boolean twin = false;
			for (Job held : data.jobs()) {
				twin |= held.kind() == job.kind() && held.param() == job.param();
			}
			if (!twin || tries > 8) {
				return job;
			}
		}
	}

	private static int take(ServerPlayer player, String id, int want) {
		int taken = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && taken < want; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
				int n = Math.min(want - taken, stack.getCount());
				stack.shrink(n);
				taken += n;
			}
		}
		return taken;
	}

	/** Gives up job {@code index} (from the Story screen). */
	static void abandon(ServerPlayer player, int index) {
		Data data = of(player);
		if (index < 0 || index >= data.jobs().size()) {
			return;
		}
		List<Job> next = new ArrayList<>(data.jobs());
		Job gone = next.remove(index);
		player.setAttached(DATA, new Data(List.copyOf(next), data.day(), data.today()));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_job_dropped", describe(gone)).withStyle(ChatFormatting.GRAY));
	}

	// ---------------------------------------------------------------- progress

	/** {@code player} (or a party member beside him) has defeated a hostile creature: it counts for every foes job. */
	static void foeFell(ServerPlayer player) {
		for (Job job : new ArrayList<>(of(player).jobs())) {
			if (job.kind() == FOES) {
				advance(player, job);
			}
		}
	}

	/** {@code owner} won a bout against the Sparring Dummy (the party members beside him are credited too). */
	static void sparWon(ServerPlayer owner) {
		for (ServerPlayer player : ZFighterSagas.neighbours(owner)) {
			for (Job job : new ArrayList<>(of(player).jobs())) {
				if (job.kind() == SPAR) {
					advance(player, job);
				}
			}
		}
	}

	private static void advance(ServerPlayer player, Job job) {
		Job now = job.with(job.have() + 1);
		replace(player, job, now);
		if (now.have() >= now.need()) {
			complete(player, now);
		} else if (now.have() % 4 == 0 || now.need() - now.have() <= 2) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_job_progress", describe(now), now.have(), now.need())
				.withStyle(ChatFormatting.AQUA));
		}
	}

	private static void replace(ServerPlayer player, Job old, Job now) {
		Data data = of(player);
		List<Job> next = new ArrayList<>(data.jobs());
		int at = next.indexOf(old);
		if (at >= 0) {
			next.set(at, now);
			player.setAttached(DATA, new Data(List.copyOf(next), data.day(), data.today()));
		}
	}

	/** The job is done: it goes, and pays. */
	private static void complete(ServerPlayer player, Job job) {
		Data data = of(player);
		List<Job> next = new ArrayList<>(data.jobs());
		next.removeIf(j -> j.kind() == job.kind() && j.param() == job.param() && j.need() == job.need());
		player.setAttached(DATA, new Data(List.copyOf(next), data.day(), data.today() + 1));
		int tp = switch (job.kind()) {
			case DELIVER -> 50 + GOODS_TP[job.param()] * job.need();
			case SPAR -> SPAR_TP * job.need();
			default -> FOES_BASE_TP + FOES_TP * job.need();
		};
		ZFighterTraining.grant(player, tp);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_job_done", describe(job), tp).withStyle(ChatFormatting.GOLD));
		if (player.level().getRandom().nextInt(BEAN_ONE_IN) == 0) {
			ZFighterSagas.give(player, new ItemStack(ZFighterItems.HERMIT_BEAN));
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
		if (ZFighterKingdoms.present()) {
			// his name with the nearest kingdom goes up a little
			ZFighterKingdoms.Settlement nearest = null;
			double best = Double.MAX_VALUE;
			for (ZFighterKingdoms.Settlement s : ZFighterKingdoms.settlements(player.level().getServer())) {
				double d = s.seat().distSqr(player.blockPosition());
				if (s.realm() && d < best && d < REPUTATION_RANGE * REPUTATION_RANGE) {
					best = d;
					nearest = s;
				}
			}
			if (nearest != null) {
				ZFighterKingdoms.changeReputation(player, nearest, REPUTATION);
			}
		}
	}
}
