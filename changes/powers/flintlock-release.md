# Old guns: no loading bar, Q takes up the next loaded one, the Void flintlock fires as you let go
- Andy: "the flintlock weaponry, it should release the shot as you release right click, the next right click reloading as you hold and can be interrupted. the loading bar does not need to be there for any of this old age weaponry, also pressing q with flintlocks just swaps to the other loaded one on the hotbar, like arsenal's void flintlocks".
- **Void Armory flintlock**: right-click holds it ready and the shot goes as you let go (it fired on the press). A spent one still comes back loaded when dropped. `ArsenalFlintlock.Flintlock#releaseUsing`.
- **WeaponMod's flintlock, muskets and blunderbuss** already work this way (hold to reload, letting go early loses it; press again and the shot goes as you let go), so only the bar and Q change for them.
- **No loading bar** for any black-powder gun: WeaponMod's reload gauge beside the hotbar isn't drawn (`WeaponModReloadOverlayMixin`), and our use bar under the crosshair leaves them out (`UseBar`, `OldGuns`).
- **Q** with a black-powder gun in hand takes up the next loaded one on the hotbar instead of dropping it (`OldGunSwapMixin`). With no other loaded one there it drops as before, and a spent Void flintlock is still dropped so it comes back loaded.
- Built on CI; not played.
