# Changelings borrow voices

Disguised Sylvan changeling kidnappers follow lone players and call in another online player's voice from their own position. Everyone nearby can hear the lure. They call at most three times, fifteen seconds apart, then reveal themselves and flee when approached or after a minute. Grey shapeshifters keep their existing behavior.

ChangelingVoices keeps up to three short microphone clips per player in memory, never on disk. Clips are discarded when voice chat is disabled or disconnected and when the voice server stops. Silent without Simple Voice Chat or a recorded speaker. ChangelingLures handles the stalking, through Doubles and BorrowedFaces; ChangelingVoiceCompat keeps the voice API optional.

No in-game or multiplayer voice test performed. The player's eventual grab/abduction behavior still needs Andy's decision; this change uses the existing reveal-and-flee path.
