# Low-end mode switches itself on for the Steam Deck
- **Low-end mode on the Deck**: the first start on a Steam Deck now turns on low-end mode by itself (shaders off, Fast graphics), as on other built-in graphics. The check took the Deck's chip ("AMD Custom GPU 0932 (radeonsi, vangogh, ...)") for a full graphics card, so the Deck ran Complementary shaders.
- Every install is checked once more under the new rule; one an earlier check already put in low-end mode is left as its player set it (`/lowend off` stays off).
- LowEndMode: AMD_BUILT_IN also matches "amd custom gpu NNNN" and "vangogh"; CHECKED is checked-v2, with checked-v1's weak results carried over.
- Built; found in the Deck client test 2026-11-24 (checked-v1=fine on the Deck); the new rule not seen on a Deck yet.
