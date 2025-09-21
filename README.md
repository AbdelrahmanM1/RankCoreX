# RankCorex

**Version:** 1.0  
**Author:** 3bdoabk (Abdelrahman Moharram)   

## Description
**RankCorex** is a lightweight yet powerful **rank and permissions management plugin** for Minecraft servers and networks. It allows server owners to easily manage ranks, permissions, and player display features such as nametags and tablist formatting.

**Key Features:**
- YAML & MySQL storage support for ranks and players.
- Temporary and permanent rank assignments.
- Cross-server synchronization.
- Dynamic **nametag** and **tablist** customization.
- PlaceholderAPI integration for easy placeholder support.
- Thread-safe and optimized for performance.

---

## Commands

| Command                        | Description                                | Permission          |
|--------------------------------|--------------------------------------------|-------------------|
| `/rank set <player> <rank> [time]` | Assign a temporary or permanent rank.      | `rankcorex.set`    |
| `/rank remove <player>`         | Remove a player's rank.                     | `rankcorex.remove` |
| `/rank check <player>`          | Check a player's current rank(s).          | `rankcorex.check`  |
| `/rank list`                    | List all available ranks.                   | `rankcorex.list`   |
| `/rank reload`                  | Reload RankCorex configurations.           | `rankcorex.reload` |

---

## Permissions

| Permission               | Description                                           | Default  |
|---------------------------|-------------------------------------------------------|----------|
| `rankcorex.admin`         | Access to all admin commands.                        | OP       |
| `rankcorex.set`           | Assign temporary or permanent ranks.                | OP       |
| `rankcorex.remove`        | Remove a player's rank.                               | OP       |
| `rankcorex.check`         | Check a player's current rank(s).                    | True     |
| `rankcorex.list`          | List all available ranks.                             | True     |
| `rankcorex.reload`        | Reload plugin configurations.                         | OP       |
| `rankcorex.bypass`        | Bypass rank hierarchy restrictions.                  | False    |

---

## Configuration

- **Ranks** are defined in `ranks.yml`.  
- Each rank can have:
  - **Prefix** and **Suffix** for nametag/tablist.
  - **Weight** to determine hierarchy.
  - **Permissions** (supports positive and negative permissions).
  - **Default** boolean to assign the default rank.

- Supports **temporary ranks** with expiration timestamps.  
- Applies **permissions automatically** when players join or ranks change.  

---

## Integration

- Works with **PlaceholderAPI** for custom placeholders in chat, nametags, and tablist.
- Supports **online/offline player rank updates**.
- Fully **thread-safe** with proper locking for async database operations.

---

## Installation

1. Download the **RankCorex.jar** file.  
2. Place it in your server's `plugins` folder.  
3. Start the server to generate the default configuration files.  
4. Customize `ranks.yml` as needed.  
5. Use `/rank set <player> <rank>` to assign ranks.  

---

## Notes

- Changing ranks applies **prefix and suffix directly adjacent** to the player name (no extra spaces).  
- Tablist updates automatically when ranks are applied.  
- Temporary ranks will **expire automatically** and revert to the default rank.
- The permissions has **Some issues** doesn't work 

---

## Example `ranks.yml` snippet

```yaml
ranks:
  Default:
    prefix: "&7[Default] "
    suffix: ""
    weight: 1
    default: true
    permissions:
      - "example.permission"
  VIP:
    prefix: "&6[VIP] "
    suffix: ""
    weight: 5
    default: false
    permissions:
      - "example.vip"
```
