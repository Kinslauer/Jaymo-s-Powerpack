# fix being kicked on join ("Network Protocol Error") by the Headed Arrows in the Combat tab
- Joining a server kicked you with "Network Protocol Error": the Combat creative tab listed Headed Arrows as stacks of 16, and 26.3's creative tabs only accept single items, so building the tab threw while the join packets were handled (also left the Combat tab missing from JEI). They're listed one at a time now.
- Built; join not tested here.
