# Global Wind, ideas ported into Jaymo's Kingdoms

**Global Wind** is by **Depixelation** (Modrinth project `global-wind`, source on GitHub under Depixelation), MIT licence
(see `LICENSE`, Copyright (c) 2025 Depixelation). Jaymo's Kingdoms' `sailing/WindPush` carries over what its
`EntityMixin` and `LivingEntityMixin` do (an entity under open sky is pushed by the wind: items and the like drift when
airborne, and slide on slippery ground such as ice in a hard wind), rewritten for Kingdoms' own wind (`Storms` through
`Winds`), so the push, ships' sails, windmills and smoke all agree. Its skylight test is dropped for a short upwind ray;
its mixins are replaced by tick handlers. The MIT notice is kept in `WindPush`'s class comment and here.
