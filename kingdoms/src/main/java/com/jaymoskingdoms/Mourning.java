package com.jaymoskingdoms;

import com.jaymoskingdoms.props.PropBlock;
import com.jaymoskingdoms.props.Props;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-12, on lasting scars with no grave): "if you have companions they should bury you and grieve and bring
 * you up if asked about their previous adventures." When a player's life ends for good (Jaymo's Powers' death by cause:
 * the final death, {@code Mortality.FINAL}), every MCA villager within {@link #COMPANION_RANGE} blocks they were involved
 * with (family, hired help, a {@link Strangers#TRUSTED_HEARTS}-heart friend: {@link Strangers#involved}) is a companion.
 * The nearest walks to the body; there they bury it ({@link #BURY_TICKS}): the body (with Jaymo's Kitchen, the player's
 * corpse) is taken up, what it carried goes into a chest under the grave, and a gravestone goes over it with a sign that
 * names them, says what took them and when. The others gather and grieve, each in their own words. Every companion
 * remembers ({@link #MOURNS}), and MCA's menu offers "Ask about their old adventures" ({@link #ANSWER}) on one who does:
 * they speak of the one they buried, to anyone, the dead one's next life too.
 */
public final class Mourning {
	/** The MCA {@code main} menu answer. */
	public static final String ANSWER = "jaymoskingdoms_old_adventures";
	/** How far from the body companions are found (blocks); how close the one burying must come. */
	static final double COMPANION_RANGE = 64.0, AT_GRAVE = 3.0;
	/** The longest the walk to the body may take before they bury where they stand by it (ticks); the burying; the grieving. */
	static final int WALK_TICKS = 20 * 90, BURY_TICKS = 20 * 6, GRIEVE_EVERY = 20 * 4;
	/** How far round the body to look for the corpse (blocks). */
	static final double CORPSE_RANGE = 6.0;
	/** Lines in the language file: grief, the words said over the grave, the old adventures. */
	static final int GRIEF_LINES = 4, RITE_LINES = 3, TALE_LINES = 4;
	/** The most a companion remembers. */
	static final int MOST_REMEMBERED = 6;

	/** On a villager: the companions they buried, each as "name\tcause\tday". */
	public static final AttachmentType<List<String>> MOURNS = AttachmentRegistry.create(JaymosKingdoms.id("mourns"),
		builder -> builder.persistent(Codec.STRING.listOf()));

	/** A burial under way. */
	private static final class Burial {
		final ResourceKey<Level> dimension;
		final BlockPos at;
		final UUID dead;
		final String name, cause;
		final List<UUID> companions;
		final long since;
		long arrived = -1L;
		int grieved;

		Burial(ResourceKey<Level> dimension, BlockPos at, UUID dead, String name, String cause, List<UUID> companions, long since) {
			this.dimension = dimension;
			this.at = at;
			this.dead = dead;
			this.name = name;
			this.cause = cause;
			this.companions = companions;
			this.since = since;
		}
	}

	private static final List<Burial> BURIALS = new ArrayList<>();
	private static @Nullable AttachmentType<?> finalDeath;
	private static boolean lookedForFinal;

	private Mourning() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && isFinal(player)) {
				died(player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Mourning::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BURIALS.clear());
	}

	/** Powers' {@code Mortality.FINAL} on {@code player}: this life is over for good (false without Powers). */
	private static boolean isFinal(ServerPlayer player) {
		if (!lookedForFinal) {
			lookedForFinal = true;
			try {
				finalDeath = (AttachmentType<?>) Class.forName("com.jaymospowers.core.wounds.Mortality").getField("FINAL").get(null);
			} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
				finalDeath = null;
			}
		}
		return finalDeath != null && player.hasAttached(finalDeath);
	}

	private static String causeOf(ServerPlayer player) {
		Object cause = finalDeath == null ? null : player.getAttached(finalDeath);
		return cause instanceof String s ? s : "other";
	}

	private static void died(ServerPlayer player) {
		ServerLevel level = player.level();
		List<UUID> companions = new ArrayList<>();
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(COMPANION_RANGE),
			v -> v.isAlive() && !v.isBaby() && McaBridge.involved(v, player))) {
			companions.add(v.getUUID());
		}
		if (companions.isEmpty()) {
			return;
		}
		String name = McaBridge.name(player);
		String cause = causeOf(player);
		long day = Festivals.day(level);
		for (UUID id : companions) {
			if (level.getEntity(id) instanceof Villager v) {
				remember(v, name + "\t" + cause + "\t" + day);
			}
		}
		BURIALS.add(new Burial(level.dimension(), player.blockPosition(), player.getUUID(), name, cause, companions, level.getGameTime()));
		JaymosKingdoms.LOGGER.info("{} has fallen for good; {} companion(s) will bury them", player.getScoreboardName(), companions.size());
	}

	private static void remember(Villager v, String entry) {
		List<String> list = new ArrayList<>(v.getAttachedOrElse(MOURNS, List.of()));
		list.add(entry);
		while (list.size() > MOST_REMEMBERED) {
			list.remove(0);
		}
		v.setAttached(MOURNS, List.copyOf(list));
	}

	private static void tick(MinecraftServer server) {
		if (BURIALS.isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		for (Iterator<Burial> it = BURIALS.iterator(); it.hasNext();) {
			Burial b = it.next();
			ServerLevel level = server.getLevel(b.dimension);
			if (level == null || !step(level, b)) {
				it.remove();
			}
		}
	}

	/** One second of a burial; false once it's over (buried and grieved, or nobody left to do it). */
	private static boolean step(ServerLevel level, Burial b) {
		List<Villager> here = new ArrayList<>();
		for (UUID id : b.companions) {
			if (level.getEntity(id) instanceof Villager v && v.isAlive() && !Downed.isDowned(v)) {
				here.add(v);
			}
		}
		if (here.isEmpty()) {
			return false;
		}
		long now = level.getGameTime();
		Villager lead = here.get(0);
		for (Villager v : here) {
			if (v.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(b.at)) < lead.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(b.at))) {
				lead = v;
			}
		}
		if (b.arrived < 0L) {
			for (Villager v : here) {
				Undertakers.walk(v, b.at);
			}
			if (lead.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(b.at)) <= AT_GRAVE * AT_GRAVE || now - b.since > WALK_TICKS) {
				b.arrived = now;
				Speech.say(level, lead, Component.translatable("talk.jaymoskingdoms.mourning.rite." + (1 + level.getRandom().nextInt(RITE_LINES)), b.name),
					Speech.WHITE, 24.0);
			}
			return true;
		}
		// at the grave: the others gather; each says their grief in turn
		for (Villager v : here) {
			if (v.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(b.at)) > 16.0) {
				Undertakers.walk(v, b.at);
			}
			v.getLookControl().setLookAt(b.at.getX() + 0.5, b.at.getY(), b.at.getZ() + 0.5);
		}
		if ((now - b.arrived) % GRIEVE_EVERY == 0 && b.grieved < here.size()) {
			Villager v = here.get(b.grieved++);
			Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.mourning.grief." + (1 + level.getRandom().nextInt(GRIEF_LINES)), b.name),
				Speech.WHITE, 20.0);
		}
		if (now - b.arrived < BURY_TICKS) {
			lead.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			return true;
		}
		if (b.grieved >= here.size() || now - b.arrived > BURY_TICKS + (long) GRIEVE_EVERY * (here.size() + 1)) {
			bury(level, b, lead);
			return false;
		}
		return true;
	}

	/** The body taken up, what it carried put in a chest under the grave, and the stone and its sign set over it. */
	private static void bury(ServerLevel level, Burial b, Villager by) {
		BlockPos spot = Undertakers.spot(level, b.at);
		if (spot == null) {
			spot = Construction.standable(level, b.at, 4);
		}
		if (spot == null) {
			spot = b.at;
		}
		List<ItemStack> belongings = new ArrayList<>();
		for (Entity corpse : level.getEntities((Entity) null, new AABB(b.at).inflate(CORPSE_RANGE), KitchenCompat::isPlayerCorpse)) {
			if (b.dead.toString().equals(KitchenCompat.corpseOwner(corpse))) {
				belongings.addAll(KitchenCompat.robCorpse(corpse, by));
				corpse.discard();
			}
		}
		Direction facing = Direction.from2DDataValue(level.getRandom().nextInt(4));
		Block stone = Props.BLOCKS.get("gravestone");
		BlockState here = level.getBlockState(spot);
		if (stone != null && (here.isAir() || here.canBeReplaced())) {
			level.setBlockAndUpdate(spot, stone.defaultBlockState().setValue(PropBlock.FACING, facing));
		}
		BlockPos under = spot.below();
		if (!belongings.isEmpty() && level.getBlockEntity(under) == null && level.getBlockState(under).getDestroySpeed(level, under) >= 0.0F) {
			level.setBlockAndUpdate(under, Blocks.CHEST.defaultBlockState());
			if (level.getBlockEntity(under) instanceof ChestBlockEntity chest) {
				int slot = 0;
				for (Iterator<ItemStack> it = belongings.iterator(); it.hasNext() && slot < chest.getContainerSize();) {
					chest.setItem(slot++, it.next());
					it.remove();
				}
			}
		}
		for (ItemStack left : belongings) {
			// (more than a chest holds: left on the grave)
			net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, spot.getX() + 0.5, spot.getY() + 0.5,
				spot.getZ() + 0.5, left);
			level.addFreshEntity(item);
		}
		BlockPos signAt = spot.relative(facing);
		if (level.getBlockState(signAt).isAir() && level.getBlockState(signAt.below()).isFaceSturdy(level, signAt.below(), Direction.UP)) {
			level.setBlockAndUpdate(signAt, Blocks.OAK_SIGN.defaultBlockState()
				.setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, (facing.get2DDataValue() * 4 + 8) % 16));
			if (level.getBlockEntity(signAt) instanceof SignBlockEntity sign) {
				long day = Festivals.day(level);
				Component[] lines = {Component.translatable("sign.jaymoskingdoms.mourning.here_lies"), Component.literal(b.name),
					Component.translatable("sign.jaymoskingdoms.mourning.cause." + b.cause), Component.translatable("sign.jaymoskingdoms.mourning.day", day)};
				sign.setText(new SignText(List.of(lines), List.of(lines), net.minecraft.world.item.DyeColor.BLACK, false),
					net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
				sign.setWaxed(true);
				sign.setChanged();
				level.sendBlockUpdated(signAt, level.getBlockState(signAt), level.getBlockState(signAt), Block.UPDATE_ALL);
			}
		}
		JaymosKingdoms.LOGGER.info("{} buried {} at {}", by.getName().getString(), b.name, spot);
	}

	/** "Ask about their old adventures" shows: they buried a companion. */
	public static boolean canAsk(Villager v) {
		return !v.getAttachedOrElse(MOURNS, List.of()).isEmpty();
	}

	/** What they say of the last companion they buried. */
	public static Component tale(ServerPlayer player, Villager v) {
		List<String> list = v.getAttachedOrElse(MOURNS, List.of());
		if (list.isEmpty()) {
			return Component.translatable("talk.jaymoskingdoms.mourning.none");
		}
		String[] entry = list.get(level(v).getRandom().nextInt(list.size())).split("\t", -1);
		String name = entry[0];
		long day = entry.length > 2 ? parse(entry[2]) : 0L;
		long ago = Math.max(0L, Festivals.day((ServerLevel) level(v)) - day);
		Component when = Component.translatable(ago < 2 ? "talk.jaymoskingdoms.mourning.lately" : "talk.jaymoskingdoms.mourning.days_ago", ago);
		boolean same = name.equals(McaBridge.name(player));
		return Component.translatable("talk.jaymoskingdoms.mourning.tale." + (1 + level(v).getRandom().nextInt(TALE_LINES)), name, when)
			.append(same ? Component.literal(" ").append(Component.translatable("talk.jaymoskingdoms.mourning.you_remind")) : Component.empty());
	}

	private static Level level(Villager v) {
		return v.level();
	}

	private static long parse(String s) {
		try {
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}
}
