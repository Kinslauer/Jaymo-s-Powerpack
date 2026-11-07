package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.438) Havens patch 6, first half: a haven keeps its gate and flies its own banner (ideas from the havens pick list;
 * our own code).
 * <ul>
 * <li><b>Its banner:</b> once a haven's seat is loaded, its blood's arms ({@link Outfits#heraldry}) go up on a banner
 * beside it, and its guards' shields bear them.</li>
 * <li><b>Strangers at the gate:</b> a human who walks into a haven ({@link #GATE_RANGE} of its seat) is stopped by the
 * nearest of its guards: told to turn back, then put out (shoved and slowed), and the third time set on. A non-human of
 * any blood walks in. So does a human with a token from a haven friend ({@link #TOKEN}: asked for in talk, given by one
 * of its folk with {@link #TOKEN_HEARTS} hearts), one walking with an escort (a non-human player or one of its folk with
 * {@link #ESCORT_HEARTS} hearts, within {@link #ESCORT_RANGE}), or one with a good name there ({@link #GOOD_NAME}).</li>
 * </ul>
 */
public final class HavenGates {
	/** A haven's gate: a human this near its seat is stopped (blocks); guards this near come to stop them. */
	static final double GATE_RANGE = 40.0, GUARD_REACH = 48.0;
	/** How long a warned human has to turn back before being put out, and again before the guards set on them (ticks). */
	static final long WARN_TICKS = 200L;
	/** How hard a guard shoves a human back out, and how long they're slowed for it (ticks). */
	static final double SHOVE = 1.4;
	static final int SHOVE_SLOW_TICKS = 60;
	/** An escort walks this near; a haven villager escorts or vouches with this many MCA hearts. */
	static final double ESCORT_RANGE = 8.0;
	static final int ESCORT_HEARTS = 40, TOKEN_HEARTS = 60;
	/** Reputation with the haven that opens its gate to a human (honoured). */
	static final int GOOD_NAME = 400;
	/** How often players are looked at (ticks); how often seats are looked at for their banner. */
	static final int CHECK_TICKS = 20, BANNER_TICKS = 200;
	/** A haven's banner goes up this near its seat, at most (blocks). */
	static final int BANNER_SEARCH = 6;
	/** Lines each: a guard's halt, his shove, and setting on them; a villager vouching or not. */
	static final int HALT_LINES = 3, SHOVE_LINES = 2, SET_ON_LINES = 2, VOUCH_LINES = 2;
	/** The talk answer that asks a haven villager for a token. */
	public static final String VOUCH_ANSWER = "jaymoskingdoms_haven_vouch";
	/** The key a token keeps its haven's id under. */
	static final String HAVEN_KEY = "jaymoskingdoms_haven";

	/** Faction ids of havens whose banner has gone up (on the Overworld). */
	static final AttachmentType<List<Integer>> BANNERED = AttachmentRegistry.create(JaymosKingdoms.id("haven_banners"),
		builder -> builder.persistent(Codec.INT.listOf()));

	public static Item TOKEN;

	/** A human stopped at a haven's gate: which haven, how far it's gone (1 warned, 2 put out, 3 set on), since when. */
	private record Stop(int haven, int stage, long since) {
	}

	private static final Map<UUID, Stop> STOPPED = new HashMap<>();

	private HavenGates() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("haven_token"));
		TOKEN = Registry.register(BuiltInRegistries.ITEM, key, new Token(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					gate(player);
				}
			}
			if (server.getTickCount() % BANNER_TICKS == 0) {
				banners(server);
			}
		});
	}

	// ---- the banner -------------------------------------------------------------------------------------------------

	private static void banners(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (level == null) {
			return;
		}
		ServerLevel store = Factions.level(server);
		List<Integer> done = store.getAttachedOrElse(BANNERED, List.of());
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || done.contains(f.id()) || !level.isLoaded(f.seat()) || !Havens.is(server, f)) {
				continue;
			}
			if (raise(level, f)) {
				List<Integer> next = new ArrayList<>(done);
				next.add(f.id());
				store.setAttached(BANNERED, List.copyOf(next));
				done = next;
			}
		}
	}

	/** Puts {@code f}'s banner up on open ground beside its seat. True once it's up (or there's nowhere: it isn't tried again). */
	private static boolean raise(ServerLevel level, Faction f) {
		BlockPos seat = f.seat();
		for (int r = 2; r <= BANNER_SEARCH; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, seat.offset(dx, 0, dz));
					if (Math.abs(at.getY() - seat.getY()) > 3 || !level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()
						|| !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
						continue;
					}
					ItemStack banner = Outfits.banner(level, f);
					BlockState state = Blocks.BANNER.pick(((net.minecraft.world.item.BannerItem) banner.getItem()).getColor()).defaultBlockState()
						.setValue(BannerBlock.ROTATION, Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(-dx, dz)) / 22.5F), 16));
					level.setBlock(at, state, 3);
					BlockEntity be = level.getBlockEntity(at);
					if (be != null) {
						be.applyComponentsFromItemStack(banner);
						be.setChanged();
					}
					return true;
				}
			}
		}
		return true;
	}

	// ---- the gate ---------------------------------------------------------------------------------------------------

	private static void gate(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || player.isCreative() || player.isSpectator() || !player.isAlive()) {
			STOPPED.remove(player.getUUID());
			return;
		}
		Faction f = Havens.at(level, player.blockPosition());
		if (f == null || player.distanceToSqr(Vec3.atCenterOf(f.seat())) > GATE_RANGE * GATE_RANGE || admitted(level, player, f)) {
			STOPPED.remove(player.getUUID());
			return;
		}
		long now = level.getGameTime();
		Stop stop = STOPPED.get(player.getUUID());
		if (stop != null && stop.haven() != f.id()) {
			stop = null;
		}
		Worker guard = guard(level, player, f);
		if (guard == null) {
			return;
		}
		PathfinderMob g = guard.mob();
		if (stop == null) {
			g.getNavigation().moveTo(player, 1.1);
			g.getLookControl().setLookAt(player, 30.0F, 30.0F);
			say(level, g, player, "halt", HALT_LINES);
			STOPPED.put(player.getUUID(), new Stop(f.id(), 1, now));
			return;
		}
		if (now - stop.since() < WARN_TICKS || stop.stage() >= 3) {
			if (stop.stage() < 3 && g.distanceToSqr(player) > 9.0) {
				g.getNavigation().moveTo(player, 1.1);
			}
			return;
		}
		if (stop.stage() == 1) {
			// put out: shoved back the way they came, and slowed
			Vec3 out = player.position().subtract(Vec3.atCenterOf(f.seat())).multiply(1.0, 0.0, 1.0);
			out = out.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : out.normalize();
			if (g.distanceToSqr(player) < 16.0) {
				g.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			}
			player.push(out.x * SHOVE, 0.3, out.z * SHOVE);
			player.needsSync = true;
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SHOVE_SLOW_TICKS, 1), g);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 0.8F, 0.9F);
			say(level, g, player, "shove", SHOVE_LINES);
			STOPPED.put(player.getUUID(), new Stop(f.id(), 2, now));
			return;
		}
		// they keep coming: the guards set on them
		say(level, g, player, "set_on", SET_ON_LINES);
		for (Worker w : Workers.near(level, g.getBoundingBox().inflate(GUARD_REACH), x -> x.isAlive() && x.factionId() == f.id() && x.role() == Folk.GUARD)) {
			Rulers.charge(w.mob(), player);
		}
		STOPPED.put(player.getUUID(), new Stop(f.id(), 3, now));
	}

	/** The nearest of haven {@code f}'s guards able to stop {@code player}, or null. */
	private static @Nullable Worker guard(ServerLevel level, ServerPlayer player, Faction f) {
		return Workers.near(level, player.getBoundingBox().inflate(GUARD_REACH),
				w -> w.isAlive() && w.factionId() == f.id() && w.role() == Folk.GUARD && !Downed.isDowned(w.mob()) && !w.mob().isSleeping())
			.stream().min(Comparator.comparingDouble(w -> w.mob().distanceToSqr(player))).orElse(null);
	}

	private static void say(ServerLevel level, PathfinderMob guard, ServerPlayer player, String what, int lines) {
		Speech.say(level, guard, Component.translatable("speech.jaymoskingdoms.haven_gate." + what + "." + guard.getRandom().nextInt(lines)),
			Speech.RED, 24.0);
	}

	/** Whether {@code player} may walk into haven {@code f}. */
	static boolean admitted(ServerLevel level, ServerPlayer player, Faction f) {
		if (Prejudice.degree(player) > 0 || Reputation.get(player, f.id()) >= GOOD_NAME || carriesToken(player, f)) {
			return true;
		}
		// an escort: a non-human player, or one of its own who thinks well of them
		for (Player other : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(ESCORT_RANGE),
			p -> p != player && p.isAlive() && !p.isSpectator() && Prejudice.degree(p) > 0)) {
			return true;
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(ESCORT_RANGE),
			v -> v.isAlive() && !v.isBaby() && Havens.home(v) != null && Havens.home(v).id() == f.id())) {
			if (McaCompat.heartsOf(v, player) >= ESCORT_HEARTS) {
				return true;
			}
		}
		return false;
	}

	private static boolean carriesToken(ServerPlayer player, Faction f) {
		return player.getInventory().contains(stack -> stack.is(TOKEN) && havenOf(stack) == f.id());
	}

	private static int havenOf(ItemStack token) {
		return token.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(HAVEN_KEY, -1);
	}

	// ---- a token from a friend --------------------------------------------------------------------------------------

	/** The vouching answer is offered by a haven's grown folk, to a human. */
	public static boolean offers(ServerPlayer player, Villager villager) {
		return !villager.isBaby() && Prejudice.degree(player) == 0 && Havens.home(villager) != null;
	}

	/**
	 * {@code player} asks {@code villager} to vouch for them: with {@link #TOKEN_HEARTS} hearts, a token for the gate of
	 * their haven; a polite no otherwise. Returns what they say.
	 */
	public static Component vouch(ServerPlayer player, Villager villager) {
		Faction f = Havens.home(villager);
		if (f == null) {
			return Component.translatable("dialogue.chat.jaymoskingdoms.haven_vouch.no." + villager.getRandom().nextInt(VOUCH_LINES));
		}
		if (McaCompat.heartsOf(villager, player) < TOKEN_HEARTS) {
			return Component.translatable("dialogue.chat.jaymoskingdoms.haven_vouch.no." + villager.getRandom().nextInt(VOUCH_LINES));
		}
		if (!carriesToken(player, f)) {
			ItemStack token = new ItemStack(TOKEN);
			CompoundTag tag = new CompoundTag();
			tag.putInt(HAVEN_KEY, f.id());
			token.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			token.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymoskingdoms.haven_token.of", f.name()));
			if (!player.getInventory().add(token)) {
				player.drop(token, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		return Component.translatable("dialogue.chat.jaymoskingdoms.haven_vouch.yes." + villager.getRandom().nextInt(VOUCH_LINES), f.name());
	}

	/** A haven friend's token: shown at its gate, its guards let a human by. */
	static final class Token extends Item {
		Token(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("item.jaymoskingdoms.haven_token.hint").withStyle(ChatFormatting.GRAY));
		}
	}
}
