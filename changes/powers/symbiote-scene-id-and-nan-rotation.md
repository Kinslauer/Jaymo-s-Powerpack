# The symbiote's meeting film no longer disconnects the client
- **Fixed**: the film where the symbiote pours back into its host (`WebMeeting.Scene`) kicked the player with a Network Protocol Error ("Tried to access entity ID before ID assignment"). The client-only copy it draws is now given an entity id of its own (`WebMeetingClient`) before it is added to the world.
- Not built locally; CI builds it. Not played.
