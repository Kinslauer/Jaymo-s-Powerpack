# fix the startup crash from the "classes" key category being registered twice
- The game crashed on launch ("Category 'jaymoskingdoms:classes' is already registered"): the Magic key and the familiar's-eyes key each registered the classes key category again after the class keys had. All three now share one category (`ClassKeys.category()`), so they still sit together under Classes in Controls.
- Built; launch not tested here.
