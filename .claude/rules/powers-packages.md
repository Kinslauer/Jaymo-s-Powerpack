---
paths:
  - "src/main/java/com/jaymospowers/**"
  - "src/client/java/com/jaymospowers/**"
  - "src/main/resources/assets/jaymospowers/lang/**"
  - "src/main/resources/jaymospowers.*.mixins.json"
---
# Powers code layout

Keep each power in its own package (`power/<name>`, `client/power/<name>`, its own mixin configs) and its own
`lang/parts/<power>.json`; list every class that holds an attachment in `JaymosPowers.registerAttachments()`.
