package com.jaymospowers.client;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** On a skull block's render state: the player whose head it is (McaHeadBlockMixin), or null. */
public interface McaHeadOwner {
	@Nullable UUID jaymospowers$headOwner();

	void jaymospowers$setHeadOwner(@Nullable UUID owner);
}
