# errand books, and a rare errand to a buried ruin
- **Errand books** (`ErrandBooks`, new): a town errand taken off the board now also gives a written book in the giver's hand: who asked, their settlement, where they were (coordinates, distance and bearing from where you stood), and that the errand goes back to them. It goes when the quest does.
- **A rare errand**: after 8 town errands finished (`RARE_AFTER`), the next one pulled off a board has a 1 in 4 chance (`RARE_CHANCE`) of being instead a fetch to a buried trail ruin (`QuestBoard.dig`, the existing dig quest), told in its own book. The count starts over when it is taken.
- Words are in the book text and the quest log only. Lang part `errand-books.json`.
- Built; not played.
