package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Arcana;
import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.Lockpicking;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.Pickpocket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.245) Thessaly the Learned: a fussy scholar who lectures, corrects the way you say your spells, and calls you "dear".
 * Andy: "a mage hand type of ability ... manipulating items from way farther away than you could naturally", and the spirits
 * that "kick in on their own to assist you". Rose pink, a slim glove of light over your own hand. Reach: things from far
 * away, traps, utility.
 * <ul>
 * <li><b>Long reach</b> (level 1): every item and every experience orb within 24 blocks is drawn to you over two seconds,
 * through the walls. <b>Rune trap</b> (2): a glyph on the ground in sight; the first foe to step on it is held fast three
 * seconds and hurt; at most three at once. <b>Unseen hand</b> (3): a mob in sight is disarmed, a villager or a settlement's
 * folk is pickpocketed, another player loses what is in their off hand. <b>Unlock</b> (5): a locked loot chest in sight
 * opens if she is learned enough (twice the lock's level). <b>Juggle</b> (4): lifts up to four nearby items or small
 * mobs into the air round you, and throws them one at a time.</li>
 * <li>On its own (bond 20 and up): the shared catch snatches an arrow meant for you ({@link HandMoves#catchIfReady}, wired for
 * every spirit), and every twenty minutes or so she points out a chest nearby you have not opened, even behind a wall
 * ({@link #tick}). Advice in battle comes from the core ({@link Spirits#advice}).</li>
 * </ul>
 * The per-tick state of the moves is in {@link ThessalyHands}.
 */
public final class Thessaly extends Spirit {
	/** Long reach: mana and cooldown are in the moves; the glove draws from {@link ThessalyHands#DRAW_RANGE} blocks. */
	static final float DRAW_COST = 6.0F;
	static final int DRAW_COOLDOWN = 40;
	/** Rune trap: how far a glyph can be laid (blocks), its cost and cooldown. */
	static final double TRAP_RANGE = HandMoves.REACH;
	static final float TRAP_COST = 10.0F;
	static final int TRAP_COOLDOWN = 100;
	/** Unseen hand: how far it reaches (blocks), its cost and cooldown. */
	static final double UNSEEN_RANGE = HandMoves.REACH;
	static final float UNSEEN_COST = 12.0F;
	static final int UNSEEN_COOLDOWN = 160;
	/** Unlock: how far it reaches (blocks), its cost and cooldown, and how many times a lock's level her own level must be. */
	static final double UNLOCK_RANGE = HandMoves.REACH;
	static final float UNLOCK_COST = 16.0F;
	static final int UNLOCK_COOLDOWN = 200, UNLOCK_LEVELS_PER_LOCK = 2;
	/** Juggle: mana for each item lifted, and the cooldown once the last is thrown. */
	static final float JUGGLE_COST = 8.0F;
	static final int JUGGLE_COOLDOWN = 100;
	/** The loot she points out: ticks between two, the chests she looks for within this many blocks, and how long a fruitless look waits. */
	static final int LOOT_EVERY = 1200, LOOK_EVERY = 100;
	static final double LOOT_RANGE = 16.0;
	/** What pointing out a chest costs. */
	static final float LOOT_COST = 2.0F;

	/** Its own moves, after the shared hands. */
	public static final List<Move> MOVES = new ArrayList<>();

	static {
		MOVES.add(new Hand("long_reach", DRAW_COST, DRAW_COOLDOWN, 1, Poses.PULL, Mana.WILL) {
			@Override
			boolean act(ServerPlayer player, SpiritState state) {
				return longReach(player);
			}
		});
		MOVES.add(new Hand("unseen", UNSEEN_COST, UNSEEN_COOLDOWN, 3, Poses.STEAL, Mana.WILL) {
			@Override
			boolean act(ServerPlayer player, SpiritState state) {
				return unseen(player);
			}
		});
		MOVES.add(new Hand("trap", TRAP_COST, TRAP_COOLDOWN, 2, Poses.TRAP, Mana.WARD) {
			@Override
			boolean act(ServerPlayer player, SpiritState state) {
				return trap(player);
			}
		});
		MOVES.add(new Hand("unlock", UNLOCK_COST, UNLOCK_COOLDOWN, 5, Poses.UNLOCK, Mana.WILL) {
			@Override
			boolean act(ServerPlayer player, SpiritState state) {
				return unlock(player, state);
			}
		});
		MOVES.add(new JuggleMove());
		MOVES.add(SpiritExecutions.move("execute_thessaly", Spirits.THESSALY));
		MOVES.add(new InBalm(Spirits.THESSALY));
	}

	Thessaly() {
		super(Spirits.THESSALY, "thessaly", 0xFFFF9ED8, 0.6F);
	}

	static void register() {
		for (Move move : MOVES) {
			Moves.register(move);
		}
		ThessalyHands.register();
	}

	/** Drops a keeper who left: their traps, juggled things and chests seen (called from {@link Spirits}' disconnect). */
	static void forgetKeeper(java.util.UUID id) {
		ThessalyHands.forget(id);
	}

	@Override
	public List<Move> moves() {
		return MOVES;
	}

	// ---- on its own -------------------------------------------------------------------------------------------------

	/**
	 * From bond 20, while no foe is near: once in {@link #LOOT_EVERY} ticks she points out a chest within
	 * {@link #LOOT_RANGE} blocks the keeper has not opened (through walls): motes round it, a line of sparks to it, and a remark.
	 */
	@Override
	public void tick(ServerPlayer player, SpiritState state, @Nullable LivingEntity threat, long now) {
		if (threat != null || state.bond() < Spirits.ACT_BOND || !cooled(player, "look", now) || !cooled(player, "loot", now)) {
			return;
		}
		cool(player, "look", now, LOOK_EVERY);
		if (!(player.level() instanceof ServerLevel level) || !Mana.canPay(player, LOOT_COST + Spirits.ACT_FLOOR)) {
			return;
		}
		BlockPos chest = ThessalyHands.unopenedChest(player, LOOT_RANGE);
		if (chest == null) {
			return;
		}
		if (!Spirits.act(player, Spirits.THESSALY, Spirits.FLASH_ACT, LOOT_COST)) {
			return;
		}
		cool(player, "loot", now, LOOT_EVERY);
		ThessalyHands.markSeen(level, player.getUUID(), chest);
		// (her own remark over the act's usual line)
		SpiritVoice.sayNow(player, Spirits.THESSALY, "loot");
		Vec3 at = Vec3.atCenterOf(chest);
		// her hand reaches out to it
		Spirits.show(player, Poses.REACH, Math.min(player.getEyePosition().distanceTo(at), HandMoves.REACH));
		SpiritFoes.line(level, ParticleTypes.END_ROD, player.getEyePosition().add(0.0, -0.4, 0.0), at, Math.min(30, Math.max(4, (int)(player.position().distanceTo(at) * 1.5))));
		Mana.sendSeen(level, Mana.MOTE, at.x, at.y + 0.6, at.z, 24, 0.5, 0.5, 0.5, 0.04);
		level.playSound(null, chest, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.8F);
	}

	// ---- long reach -------------------------------------------------------------------------------------------------

	private static boolean longReach(ServerPlayer player) {
		if (ThessalyHands.draw(player) == 0) {
			return false;
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.8F);
		return true;
	}

	// ---- unseen hand ------------------------------------------------------------------------------------------------

	/** A mob is disarmed, a villager or folk pickpocketed, a player loses what is in their off hand. */
	private static boolean unseen(ServerPlayer player) {
		LivingEntity target = Arcana.sighted(player);
		if (target == null || player.distanceTo(target) > UNSEEN_RANGE || HandMoves.ally(player, target) || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		boolean did;
		if (target instanceof ServerPlayer other) {
			ItemStack held = other.getOffhandItem();
			did = !held.isEmpty();
			if (did) {
				ItemStack taken = held.copy();
				held.setCount(0);
				player.spawnAtLocation(level, taken);
				other.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.unseen.lost").withStyle(ChatFormatting.GRAY));
			}
		} else if (target instanceof Villager || target instanceof Folk) {
			did = Pickpocket.liftBySpirit(player, target);
		} else if (target instanceof Mob mob) {
			ItemStack held = mob.getMainHandItem();
			did = !held.isEmpty();
			if (did) {
				ItemStack taken = held.copy();
				mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				player.spawnAtLocation(level, taken);
			}
		} else {
			did = false;
		}
		if (!did) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.unseen.nothing").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Vec3 mark = target.getBoundingBox().getCenter();
		CastHints.setReach(player, player.getEyePosition().distanceTo(mark));
		SpiritFoes.line(level, ParticleTypes.ENCHANT, player.getEyePosition().add(0.0, -0.4, 0.0), mark, Math.min(24, Math.max(3, (int)(player.distanceTo(target) * 1.5))));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.9F);
		return true;
	}

	// ---- rune trap --------------------------------------------------------------------------------------------------

	private static boolean trap(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		HitResult hit = player.pick(TRAP_RANGE, 0.0F, false);
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos pos = block.getBlockPos().relative(block.getDirection());
		if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
			return false;
		}
		if (!ThessalyHands.plant(player, level, pos)) {
			return false;
		}
		CastHints.setReach(player, player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)));
		Mana.sendSeen(level, Mana.MOTE, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 14, 0.4, 0.1, 0.4, 0.02);
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 8, 0.4, 0.05, 0.4, 0.01);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 0.8F, 1.4F);
		return true;
	}

	// ---- unlock -----------------------------------------------------------------------------------------------------

	private static boolean unlock(ServerPlayer player, SpiritState state) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		HitResult hit = player.pick(UNLOCK_RANGE, 0.0F, false);
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos pos = block.getBlockPos();
		int lock = Lockpicking.lockLevel(level, pos);
		if (lock == 0) {
			return false;
		}
		if (!Lockpicking.pickBySpirit(level, pos, player, state.level())) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.unlock.beyond", UNLOCK_LEVELS_PER_LOCK * lock)
				.withStyle(ChatFormatting.LIGHT_PURPLE));
			return false;
		}
		CastHints.setReach(player, player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)));
		Mana.sendSeen(level, Mana.MOTE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 16, 0.4, 0.4, 0.4, 0.03);
		level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.5);
		return true;
	}

	// ---- moves ------------------------------------------------------------------------------------------------------

	/** One of her simple moves: a tap that does something or nothing. Says the {@code move} line now and then. */
	private abstract static class Hand implements Move {
		private final String id;
		private final float cost;
		private final int cooldown, level, pose, intent;

		Hand(String id, float cost, int cooldown, int level, int pose, int intent) {
			this.id = id;
			this.cost = cost;
			this.cooldown = cooldown;
			this.level = level;
			this.pose = pose;
			this.intent = intent;
		}

		abstract boolean act(ServerPlayer player, SpiritState state);

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			boolean did = act(player, state);
			if (did && player.getRandom().nextFloat() < HandMoves.MOVE_SAY) {
				SpiritVoice.say(player, "move");
			}
			return did;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public float cost() {
			return cost;
		}

		@Override
		public int cooldown() {
			return cooldown;
		}

		@Override
		public int level() {
			return level;
		}

		@Override
		public int pose() {
			return pose;
		}

		@Override
		public int intent() {
			return intent;
		}
	}

	/**
	 * Juggle: the first tap lifts the things near the keeper and takes {@link #JUGGLE_COST} for each; every tap after throws the
	 * next one (free, the speed by how long the key was held), and the cooldown starts when the last is thrown or the hands tire.
	 * Like the grab, so the cost and cooldown follow the cast that just happened.
	 */
	private static final class JuggleMove implements Move {

		@Override
		public String id() {
			return "juggle";
		}

		@Override
		public float cost() {
			return JUGGLE_COST;
		}

		@Override
		public int cooldown() {
			return JUGGLE_COOLDOWN;
		}

		@Override
		public int level() {
			return 4;
		}

		@Override
		public boolean charges() {
			return true;
		}

		@Override
		public int pose() {
			return Poses.JUGGLE;
		}

		@Override
		public int intent() {
			return Mana.WILL;
		}

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			if (ThessalyHands.juggling(player)) {
				if (!ThessalyHands.hurl(player, charge)) {
					return false;
				}
				CastHints.setCost(player, 0.0F);
				CastHints.setPose(player, Poses.THROW);
				// (more in the air: no cooldown yet; the last throw starts it)
				if (ThessalyHands.juggling(player)) {
					CastHints.setCooldown(player, 0);
				}
				return true;
			}
			int lifted = ThessalyHands.lift(player, n -> Mana.canPay(player, JUGGLE_COST * n));
			if (lifted == 0) {
				return false;
			}
			CastHints.setCost(player, JUGGLE_COST * lifted);
			// (the cooldown waits for the last throw)
			CastHints.setCooldown(player, 0);
			if (player.getRandom().nextFloat() < HandMoves.MOVE_SAY) {
				SpiritVoice.say(player, "move");
			}
			return true;
		}
	}
}
