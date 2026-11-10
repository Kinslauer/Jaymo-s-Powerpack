# skyfalls caught after their water starts running
Andy (2026-11-10): "Ive still been seeing skyfalls. And we have all been in the first world tier". The load check of
`SkyfallCleanup` only saw water already pouring when a chunk loaded; a chunk made before 0.21.4 at the edge of sight could
load before its spring had run, and pour afterwards. Now the chunks within 10 of every player in a One Dimension world are
checked again every 10 seconds, so a fall that starts later is sealed too. The Dread gate (`SkySpringFeature`) now fails
closed: with Kingdoms installed, a tier it can't read counts as not Dread (before, a failed lookup let heavenfalls through).
Built, not played.
