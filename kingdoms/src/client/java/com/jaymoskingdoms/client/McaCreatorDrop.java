package com.jaymoskingdoms.client;

/**
 * A story left wholly to fate (LifeStoryScreen's "Leave it all to fate") never shows MCA's creator: the creator MCA still
 * means to open on this client is let go, as MCA lets it go itself. Only touched when MCA is loaded (McaCompat.LOADED).
 */
final class McaCreatorDrop {
	private McaCreatorDrop() {
	}

	static void drop() {
		net.conczin.mca.MCAClient.getDestinyManager().allowClosing();
	}
}
