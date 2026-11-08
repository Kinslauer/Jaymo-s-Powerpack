# Blood puddles no longer crash the game

- Fix: the first blood puddle, footprint or wall mark on screen crashed the client ("Render pass color attachment
  count must match pipeline color target state count"). The blood decal pipeline (`BloodDecals.decalPipeline`) was built
  without a colour target; it now has the translucent one vanilla's translucent particles use.
