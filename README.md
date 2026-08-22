<p align="center">
    <img alt="Public Teleport Logo" src=".github/banner-700.png">
</p>
<br>

An easy-to-use Fabric Teleport Mod!

- Homes, Warps, Back, Spawn and TPA functionality
- Checks if teleport destinations are safe to use to prevent suffocation
- Usable in multiplayer (Server-side) or singleplayer (Client-side)
- Server-side functionality does NOT require client-side installation, but see section "Languages" below
- Minimal configuration with predefined values

<br>
<br>
<p align="center">
    <img alt="Public Teleport Logo" src=".github/bus-500.png">
</p>

<sub>[^1]</sub>

## Commands

| Command                | Only OP | Description                                                        |
| ---------------------- | :-----: | ------------------------------------------------------------------ |
| `/helpteleport`        |         | Shows information about the commands the user can use              |
| **Spawn:**             |         |                                                                    |
| `/setspawn`            |    ✓    | Sets the Spawn point (Unrelated to the Minecraft world spawn)      |
| `/spawn`               |         | Teleports to Spawn (Needs to be set via the `/setspawn` command)   |
| **Warps:**             |         |                                                                    |
| `/setwarp <name>`      |    ✓    | Sets a Warp                                                        |
| `/delwarp <name>`      |    ✓    | Deletes a Warp                                                     |
| `/warp <name>`         |         | Teleports to a Warp                                                |
| `/warps`               |         | Lists all Warps                                                    |
| **Homes:**             |         |                                                                    |
| `/sethome [<name>]`    |         | Sets a Home (Default: `home`)                                      |
| `/delhome <name>`      |         | Deletes a Home                                                     |
| `/home [<name>]`       |         | Teleports to a Home (Default: `home`)                              |
| `/homes`               |         | Lists all your homes                                               |
| **Back:**              |         |                                                                    |
| `/back`                |         | Teleports to the last location before using a Home, Warp, or dying |
| **TPA:**               |         |                                                                    |
| `/tpa <player>`        |         | Requests teleportation to `<player>`                               |
| `/tpahere <player>`    |         | Requests `<player>` to teleport to you                             |
| `/tpahereall`          |    ✓    | Requests ALL players to teleport to you                            |
| `/tpcancel`            |         | Cancels your teleportation request                                 |
| `/tpaccept [<player>]` |         | Accepts request from `<player>` (Default: Most recent)             |
| `/tpdeny [<player>]`   |         | Denies request from `<player>` (Default: Most recent)              |

Please note:

- TPA functionality is disabled in singleplayer.
- `/setspawn`, `/setwarp` and `/sethome` use your current location rounded to a block position and your current viewing angle to create the teleport point.
- `/tpaccept` searches for a spawnable block around the target position to teleport the user to.

## Configuration

On first run a config file is created in `config/public-teleport/config.json` with these defaults:

| Field             | Default | Description                                                                              |
| ----------------- | ------- | ---------------------------------------------------------------------------------------- |
| `defaultLanguage` | `en_us` | The default language used, see section "Languages" below.                                |
| `maxHomes`        | 10      | The maximum amount of homes a player can have (Set to `0` to disable limit)              |
| `requestTimeout`  | 60      | How long a teleport request is active before it is removed in seconds                    |
| `enableSpawn`     | true    | If the `/setspawn, /spawn` commands are enabled                                          |
| `enableWarps`     | true    | If the `/setwarp, /delwarp, /warp, /warps` commands are enabled                          |
| `enableHomes`     | true    | If the `/sethome, /delhome, /home, /homes` commands are enabled                          |
| `enableBack`      | true    | If the `/back` command is enabled                                                        |
| `enableTpa`       | true    | If the `/tpa, /tpahere, /tpahereall, /tpcancel, /tpaccept, /tpdeny` commands are enabled |

To change these settings edit the config file and restart your server.

## Languages

Currently only `en_us` and `de_de` are valid values for `defaultLanguage`. If you want to provide more translations feel free to open an Issue.

### Multiplayer usage

**Server-side**: The Configuration `defaultLanguage` defines the language all messages are in. (Output on the console is always in English.)

**Client-side**: If the user has installed this mod on their client, `defaultLanguage` is overridden with the client's language setting.

### Singleplayer usage

The Configuration `defaultLanguage` has no effect; the client's language setting is used.

## Installation

Requires [Fabric API](https://modrinth.com/mod/fabric-api) to be installed.

- Download the `.jar` file from the [Releases page](https://github.com/SebiTimeWaster/Public-Teleport/releases) that fits your Minecraft version and put it into your `mods` folder on your server or client
- Restart your server or client
- If needed edit the configuration as described in the section "Configuration" above

## Migration from MiniTeleport

If you are migrating from [MiniTeleport](https://github.com/luxmiyu/miniteleport):

- Install Public Teleport, start your server and stop it once it has fully started
- Copy and overwrite all files/directories from `world/miniteleport/` to `config/public-teleport/`:
  `cp -r world/miniteleport/* config/public-teleport/`

[^1]: Bus model by [TheJeroen](https://www.planetminecraft.com/project/bravo-public-buses-arriva-netherlands-vehicles-1-19-3/)
