# The last bowl or bottle comes back
- **Bowls and bottles**: drinking or eating the last of a stack gives its bowl, bottle or bucket back into your hand
  again (Andy, 2026-11-24: Chamomile Tea left nothing). CreativeCore, a library mod in the pack, throws away what
  `LivingEntity.completeUsingItem` gets back from the item, so the last Chamomile Tea, Bone Broth, Posset, Willow-Bark
  Tea, mushroom stew, milk bucket, honey bottle or potion lost its container in Survival. `UseLeftoverStackMixin`
  remembers what finishing the item left and `UseLeftoverHandMixin` puts it in the hand when the hand still holds the
  used-up stack. With some still in the stack the bowl went to the inventory and always did.
- Tested on a dev server with today's Kitchen and Kingdoms: without the fix and with CreativeCore 2.14.22 every one of
  them left an empty hand; with the fix all gave their bowl, bottle or bucket back, through the Creative tab and
  underwater too; without CreativeCore nothing is doubled. Not played on the Deck server.
