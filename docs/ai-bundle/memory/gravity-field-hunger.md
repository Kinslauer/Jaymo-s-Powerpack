---
name: gravity-field-hunger
description: Immense Field (Gravity Z) drains owner hunger, less as they level + wall walk first-person fix; PR #189 MERGED (pack 0.10.95)
metadata:
  type: project
---
2026-10-14 Andy: "immense field drains ALOT of hunger but less as you level." It cost nothing before. PR #189 MERGED 2026-10-14 22:53Z (pack 0.10.95, unplaytested): GravityTrainingField.feed drains the owner ~5 shanks per full 60 s field fresh, 25% of that mastered (scaled by mastery points); field drops at 0 hunger (my choice). Tuning: EXHAUSTION / MASTERED_EXHAUSTION. Jar: /mnt/project-files/field-hunger/.

Also in #189: the wall-walk / forward-fall body turn is skipped in your own first person (First-person Model drew the turned body across the camera; same rule as Web wall run).
