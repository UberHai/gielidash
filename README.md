# GieliDash

A delivery marketplace for Gielinor, as a RuneLite plugin.

**Requesters** post orders — items (or services) plus a delivery location and a gp fee.
**Dashers** browse open orders, accept one, source the goods themselves, and hand them
over in a regular player-to-player trade at the destination. Both sides rate each other
after the hand-off.

GieliDash is a coordination board only: it never walks, trades, chats, or acts for you.
Every in-game action is performed manually by the player, in line with Jagex's
third-party client guidelines.

## Data disclosure

When order sync is enabled (off by default), the plugin sends your account hash, display
name, world/location while on an active order, and self-reported vetting stats (combat
level, total level, quest points, travel unlocks) to the GieliDash server so orders can
be matched and tracked. Nothing is sent while sync is disabled.

## License

BSD 2-Clause — see [LICENSE](LICENSE).
