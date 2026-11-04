package com.jaymoskingdoms.champions;

import com.jaymoskingdoms.PatronChampion;
import com.jaymoskingdoms.Patron;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;

/** (0.7.518) A champion's moves. A fresh set for each champion, because the moves keep their own lingering state. */
public interface Moves {
	/** [A, B, C]: opened by phases 1, 2 and 3. */
	List<Move> moves();

	/** The fourth phase (a group of FOURTH_PHASE_FROM or more): the patron's hand. */
	void hand(ServerLevel level, PatronChampion c, int group);

	default void tick(ServerLevel level, PatronChampion c) {
	}

	default float hurt(PatronChampion c, DamageSource source, float amount) {
		return amount;
	}

	/** After a hit on the champion really landed (past the invulnerability frames). */
	default void afterHurt(PatronChampion c, DamageSource source) {
	}

	default void clear(PatronChampion c) {
	}

	static Moves create(Patron p) {
		return switch (p) {
			case HEARTHKEEPER -> new HearthWardenMoves();
			case ANVIL_FATHER -> new ForgeThaneMoves();
			case DEEP_MOTHER -> new TideBrideMoves();
			case GREEN_HUNTSMAN -> new AntleredHunterMoves();
			case LANTERN_WARDEN -> new GraveLampMoves();
			case CROWNED_SUN -> new SunMarshalMoves();
			case ASH_WHISPERER -> new CinderShadeMoves();
			case TRUTH -> new InquisitorMoves();
		};
	}
}
