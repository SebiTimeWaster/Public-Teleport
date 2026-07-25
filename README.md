<p align="center">
  <img alt="Public Teleport Logo" src="src/main/resources/assets/public-teleport/icon.png">
</p>

## Teleport commands done right:

- Homes, Warps, Back, Spawn, TPA, TPAhere
- Server-side or client-side (Multiplayer/Singleplayer)
- Minimal configuration with predefined sane values
- No rights management

## Commands

| Command                | Only OP | Description                                          |
| ---------------------- | :-----: | ---------------------------------------------------- |
| **Spawn:**             |         |                                                      |
| `/setspawn`            |    ✓    | Create a`spawn` warp and set world spawn             |
| `/spawn`               |         | Teleport to spawn                                    |
| **Warps:**             |         |                                                      |
| `/setwarp <name>`      |    ✓    | Create a warp at your location                       |
| `/delwarp <name>`      |    ✓    | Delete a warp                                        |
| `/warp <name>`         |         | Teleport to a warp                                   |
| `/warps`               |         | List all warps                                       |
| **Homes:**             |         |                                                      |
| `/sethome [<name>]`    |         | Set a new home (default: home)                       |
| `/delhome <name>`      |         | Delete an existing home                              |
| `/home [<name>]`       |         | Teleport to a home (default: home)                   |
| `/homes`               |         | List all current homes                               |
| **Back:**              |         |                                                      |
| `/back`                |         | Teleport to your last location (including death)     |
| **TPA:**               |         |                                                      |
| `/tpa <player>`        |         | Request teleport to`<player>`                        |
| `/tpahere <player>`    |         | Request`<player>` to teleport to you                 |
| `/tpcancel`            |         | Cancel all your sent requests                        |
| `/tpaccept [<player>]` |         | Accept request from`<player>` (default: most recent) |
| `/tpdeny [<player>]`   |         | Deny request from`<player>` (default: most recent)   |

## Installation

Requires [Fabric API](https://modrinth.com/mod/fabric-api) to be installed.

- Download the `.jar` into your `mods` folder.
- No configuration required.

## Migration from MiniTeleport

If you are migrating from [MiniTeleport](https://github.com/luxmiyu/miniteleport):

- Install Public Teleport, start your server and stop it once it was fully started
- Copy all files in `world/miniteleport/` to `config/public-teleport/`
