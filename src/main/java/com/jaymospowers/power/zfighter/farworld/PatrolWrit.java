package com.jaymospowers.power.zfighter.farworld;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.power.zfighter.ZFighterNpcs;
import com.jaymospowers.power.zfighter.ZFighterPatrol;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.ZFighterSpaceMissions;
import com.jaymospowers.power.zfighter.pod.PodEntity;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.55, Andy's note) The Patrol's skip writ. An officer of Sergeant's rank or better can ask a patrolman for a writ, once for each
 * world with a mission (Rustfall, Glacia, Cinder). Used on that world while its captains are still to be faced, it calls down a
 * Commander of the Patrol in a pod of his own, who takes the captain fight for the fighter and his party ({@link #BEATS} blows, one
 * for each captain), then leaves; the champion's duel and the boss stay the fighter's own. A film plays for whoever read the writ
 * ({@link UltimateFilm}). The called man is an MCA villager, so it needs Jaymo's Kingdoms; without it the writ is kept.
 */
public final class PatrolWrit {
	// ---- tuning ----
	/** The captains the Commander beats, and the ticks between his blows; the wait before the first, and after the last. */
	static final int BEATS = ZFighterSpaceMissions.CAPTAINS, BEAT_TICKS = 50, FIRST_BEAT_TICKS = 100, LAST_BEAT_TICKS = 60;
	/** A film of the Commander's landing: its length (ticks) and how far others see its bars (blocks). */
	static final int FILM_TICKS = 90;
	static final double FILM_WITNESSES = 48.0;
	/** The Commander is lost (and the writ given back) if the pod has not set down by this time, or the fighter has gone from the world. */
	static final int LAND_LIMIT_TICKS = 900;
	/** How many Commander names there are. */
	static final int NAMES = 4;

	public static final String TAG = "jaymospowers.patrol_commander";

	/** The worlds a writ has been given for (bit: {@link FarWorld#ordinal}); kept through death, so a lost writ is not given twice. */
	public static final AttachmentType<Integer> ISSUED = AttachmentRegistry.create(JaymosPowers.id("patrol_writs"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	public static Item WRIT;

	private enum Stage {
		LANDING, FIGHTING, LEAVING
	}

	/** One Commander's visit. */
	private static final class Skip {
		final UUID player;
		final FarWorld world;
		Stage stage = Stage.LANDING;
		@Nullable PodEntity pod;
		@Nullable LivingEntity man;
		int age, beats;
		long next;

		Skip(UUID player, FarWorld world) {
			this.player = player;
			this.world = world;
		}
	}

	private static final List<Skip> SKIPS = new ArrayList<>();
	/** Writs owed to a fighter who was gone or dead when the visit ended: handed back when he is next about (kept until the server stops). */
	private static final List<Skip> OWED = new ArrayList<>();

	private PatrolWrit() {
	}

	public static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("patrol_writ"));
		WRIT = Registry.register(BuiltInRegistries.ITEM, key, new WritItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			for (FarWorld world : FarWorld.values()) {
				if (ZFighterSpaceMissions.planetAt(world.dest) >= 0) {
					output.accept(stack(world));
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(PatrolWrit::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SKIPS.clear();
			OWED.clear();
		});
	}

	/** The writ of {@code world} as an item. */
	public static ItemStack stack(FarWorld world) {
		ItemStack stack = new ItemStack(WRIT);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(world.key), List.of()));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymospowers.patrol_writ.named",
			Component.translatable("space.jaymospowers.dest." + world.key)));
		return stack;
	}

	/** Which world {@code stack} is the writ of, or null. */
	static @Nullable FarWorld worldOf(ItemStack stack) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		return data == null || data.strings().isEmpty() ? null : FarWorld.byKey(data.strings().getFirst());
	}

	// ---------------------------------------------------------------- asking the Patrol

	/** "Ask for a skip writ": the worlds with a mission, one writ each. */
	public static void ask(ServerPlayer player, LivingEntity man, Runnable back) {
		ZFighterNpcs.Option backOption = ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.back", true, back);
		if (ZFighterPatrol.officerRank(player) < ZFighterPatrol.WRIT_RANK) {
			ZFighterNpcs.ask(player, man, Component.translatable("talk.jaymospowers.patrol.writ_not_yet"), List.of(backOption));
			return;
		}
		int issued = player.getAttachedOrElse(ISSUED, 0);
		List<ZFighterNpcs.Option> options = new ArrayList<>();
		for (FarWorld world : FarWorld.values()) {
			if (ZFighterSpaceMissions.planetAt(world.dest) < 0) {
				continue;
			}
			boolean given = (issued & 1 << world.ordinal()) != 0;
			options.add(ZFighterNpcs.Option.of("talk.jaymospowers.patrol.opt.writ_for", !given, () -> {
				player.setAttached(ISSUED, player.getAttachedOrElse(ISSUED, 0) | 1 << world.ordinal());
				give(player, stack(world));
				ZFighterNpcs.ask(player, man, Component.translatable("talk.jaymospowers.patrol.writ_given"), List.of(backOption));
			}, Component.translatable("space.jaymospowers.dest." + world.key)));
		}
		options.add(backOption);
		ZFighterNpcs.ask(player, man, Component.translatable("talk.jaymospowers.patrol.writ_offer"), options);
	}

	// ---------------------------------------------------------------- using it

	/** The writ item: use it while a world's captains are before you. */
	static final class WritItem extends Item {
		WritItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer server && level instanceof ServerLevel serverLevel) {
				ItemStack stack = player.getItemInHand(hand);
				if (call(serverLevel, server, stack)) {
					stack.shrink(1);
				}
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			net.minecraft.world.item.TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymospowers.patrol_writ").withStyle(ChatFormatting.GRAY));
		}
	}

	/** Calls the Commander down for {@code player}; false (the writ is kept) if it is not the time or place. */
	private static boolean call(ServerLevel level, ServerPlayer player, ItemStack stack) {
		FarWorld writ = worldOf(stack);
		FarWorld here = FarWorld.of(level);
		if (writ == null || here != writ) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.patrol_writ.wrong_world").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (ZFighterSpaceMissions.conquering(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.patrol_writ.champion").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (!ZFighterSpaceMissions.captainsAhead(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.patrol_writ.no_captains").withStyle(ChatFormatting.GRAY));
			return false;
		}
		for (Skip skip : SKIPS) {
			if (skip.player.equals(player.getUUID())) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.patrol_writ.already").withStyle(ChatFormatting.GRAY));
				return false;
			}
		}
		if (!ZFighterPatrol.writCanLand()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.patrol_writ.no_one").withStyle(ChatFormatting.GRAY));
			return false;
		}
		BlockPos ground = ZFighterPatrol.writGround(level, player, ZFighterSpace.DESTS[writ.dest].kind());
		if (ground == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.patrol_writ.no_room").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Skip skip = new Skip(player.getUUID(), writ);
		SKIPS.add(skip);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.patrol_writ.called").withStyle(ChatFormatting.AQUA));
		ZFighterPatrol.writLand(level, ground, pod -> landed(level, skip, pod, ground));
		return true;
	}

	private static void landed(ServerLevel level, Skip skip, PodEntity pod, BlockPos ground) {
		skip.pod = pod;
		int name = level.getRandom().nextInt(NAMES);
		LivingEntity man = ZFighterPatrol.writMan(level, new Vec3(ground.getX() + 2.5, ground.getY(), ground.getZ() + 0.5), TAG,
			"entity.jaymospowers.patrol_commander." + name, name % 2 == 1);
		if (man == null) {
			giveBack(level, skip);
			ZFighterPatrol.writLeave(level, pod);
			skip.stage = Stage.LEAVING;
			return;
		}
		skip.man = man;
		skip.stage = Stage.FIGHTING;
		skip.next = level.getGameTime() + FIRST_BEAT_TICKS;
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(skip.player);
		if (player != null) {
			UltimateFilm.play(player, UltimateFilm.PUSH_IN, FILM_TICKS, man.position(), man, FILM_WITNESSES);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.patrol_writ.landed", man.getDisplayName()).withStyle(ChatFormatting.AQUA));
		}
	}

	// ---------------------------------------------------------------- the fight

	private static void tick(MinecraftServer server) {
		if (!OWED.isEmpty()) {
			OWED.removeIf(owed -> {
				ServerPlayer back = server.getPlayerList().getPlayer(owed.player);
				if (back == null || !back.isAlive()) {
					return false;
				}
				giveBack(back.level(), owed);
				return true;
			});
		}
		if (SKIPS.isEmpty()) {
			return;
		}
		for (Iterator<Skip> it = SKIPS.iterator(); it.hasNext();) {
			Skip skip = it.next();
			skip.age++;
			ServerPlayer player = server.getPlayerList().getPlayer(skip.player);
			if (skip.stage == Stage.LEAVING) {
				it.remove();
				continue;
			}
			if (player == null || !player.isAlive() || FarWorld.of(player.level()) != skip.world
				|| skip.stage == Stage.LANDING && skip.age > LAND_LIMIT_TICKS
				|| skip.man != null && !skip.man.isAlive()) {
				abort(server, skip, player);
				it.remove();
				continue;
			}
			if (skip.stage != Stage.FIGHTING || skip.man == null) {
				continue;
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			if (now < skip.next) {
				continue;
			}
			if (skip.beats < BEATS) {
				beat(level, player, skip);
				skip.beats++;
				skip.next = now + (skip.beats < BEATS ? BEAT_TICKS : LAST_BEAT_TICKS);
			} else {
				finish(level, player, skip);
				it.remove();
			}
		}
	}

	/** One captain falls: the Commander strikes, a flash, a cry. */
	private static void beat(ServerLevel level, ServerPlayer player, Skip skip) {
		LivingEntity man = skip.man;
		Vec3 toward = player.position().subtract(man.position()).multiply(1.0, 0.0, 1.0);
		Vec3 side = new Vec3(-toward.z, 0.0, toward.x).normalize().scale((skip.beats % 2 == 0 ? 1.0 : -1.0) * 3.0);
		Vec3 spot = man.position().add(toward.normalize().scale(2.0)).add(side);
		if (man instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.getLookControl().setLookAt(spot.x, spot.y + 1.0, spot.z);
		}
		man.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y + 1.0, spot.z, 2, 0.5, 0.5, 0.5, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, spot.x, spot.y + 1.0, spot.z, 30, 0.6, 0.8, 0.6, 0.15);
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(spot.x, spot.y, spot.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 1.4F, 0.7F);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.8F, 1.5F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_foe_down", skip.beats + 1, BEATS).withStyle(ChatFormatting.GOLD));
	}

	/** The captains are down: the mission goes on to the boss, and the Commander climbs back in and goes. */
	private static void finish(ServerLevel level, ServerPlayer player, Skip skip) {
		ZFighterSpaceMissions.skipCaptains(player);
		Vec3 at = skip.man.position();
		player.sendSystemMessage(Component.translatable("message.jaymospowers.patrol_writ.farewell", skip.man.getDisplayName()).withStyle(ChatFormatting.AQUA));
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 20, 0.6, 0.3, 0.6, 0.05);
		skip.man.discard();
		if (skip.pod != null) {
			ZFighterPatrol.writLeave(level, skip.pod);
		}
	}

	/** The fighter has gone, died or never met the Commander: the Commander goes home and the writ comes back. */
	private static void abort(MinecraftServer server, Skip skip, @Nullable ServerPlayer player) {
		if (skip.man != null) {
			skip.man.discard();
		}
		if (skip.pod != null && !skip.pod.isRemoved() && skip.pod.level() instanceof ServerLevel level) {
			ZFighterPatrol.writLeave(level, skip.pod);
		}
		if (player != null && player.isAlive()) {
			giveBack(player.level(), skip);
		} else {
			// gone or dead: the writ cannot be handed over now, so it is owed (else the world's writ could never be asked for again)
			OWED.add(skip);
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
	}

	private static void giveBack(ServerLevel level, Skip skip) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(skip.player);
		if (player != null) {
			give(player, stack(skip.world));
			player.sendSystemMessage(Component.translatable("message.jaymospowers.patrol_writ.returned").withStyle(ChatFormatting.GRAY));
		}
	}
}
