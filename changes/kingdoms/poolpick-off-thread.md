# Waking villages into kingdoms no longer freezes the server

- The server crashed on its watchdog (Deck, 2026-11-25, a new world waking snowy villages into kingdoms). Looking up which structure starts in a village's chunk (`PoolPick.starts`) asked for the chunk from the server thread, and there Minecraft waits until the chunk is made, running other queued chunk loads meanwhile; the server stopped ticking. It now asks from a background thread, which only queues the request, so the kingdom wake (`TownWakes`) and the capital's search (`StoryGround`) wait for it without holding up the server.
- Built by CI; not played.
