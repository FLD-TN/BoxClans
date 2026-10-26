# BoxClans — Tích hợp cho plugin khác

API: `com.dex:boxclans-api:1.0.0` (package `com.dex.boxclans.api`). Jar: `BoxClans/boxclans-api/build/libs/boxclans-api-1.0.0.jar`,
cũng có trong Maven local sau `gradlew :boxclans-api:publishToMavenLocal`. API chỉ được **thêm**, không đổi tên / xoá.

## 1. Thêm phụ thuộc

```kotlin
// build.gradle.kts của plugin khác (chọn một)
compileOnly(files("libs/boxclans-api-1.0.0.jar"))      // chép jar vào libs/
compileOnly("com.dex:boxclans-api:1.0.0")               // cần repositories { mavenLocal() }
```

```yaml
# plugin.yml
softdepend: [BoxClans]      # hoặc depend: [BoxClans] nếu plugin không chạy được khi thiếu clan
```

**Không shade** API: `BoxClans-1.0.0.jar` đã chứa sẵn các class, plugin khác phải dùng đúng các class đó.

## 2. Mẫu hook (một lớp duy nhất import API)

```java
public final class BoxClansHook {
    private static volatile ClanService clans;
    private static volatile ClanPoints points;

    /** Gọi trong onEnable (và khi BoxClans bật lại) — chỉ khi Bukkit.getPluginManager().isPluginEnabled("BoxClans"). */
    public static void init() {
        clans = Bukkit.getServicesManager().load(ClanService.class);
        points = Bukkit.getServicesManager().load(ClanPoints.class);
    }

    public static boolean sameClan(Player a, Player b) {
        ClanService s = clans;
        return s != null && s.sameClan(a.getUniqueId(), b.getUniqueId());
    }
}
```

Chỉ gọi lớp hook sau khi kiểm tra `isPluginEnabled("BoxClans")`, để không nạp class khi thiếu BoxClans (`NoClassDefFoundError`).

## 3. Luồng (thread)

- Mọi hàm **đọc** của `ClanService` an toàn từ mọi luồng (đọc ảnh chụp bất biến), kể cả `isFriendly` từ luồng async.
- `ClanPoints` gọi từ luồng khác được chuyển về main thread và trả ngay `PointResult.QUEUED`. Gọi từ main thread thì nhận kết quả thật.
- API không bao giờ ném lỗi; người / clan không tồn tại thì trả `Optional.empty()` hoặc `PointResult.NO_CLAN`.
- Event của BoxClans luôn bắn trên main thread, sau khi trạng thái đã đổi (gọi `ClanService` trong listener sẽ thấy dữ liệu mới). Riêng `ClanActionEvent` bắn **trước** khi đổi.

## 4. Đọc clan: `ClanService`

| Hàm | Ghi chú |
|---|---|
| `clan(uuid)`, `clanByKey(key)`, `clanOf(player)` | `key` = mã lệnh (`RongDo`), không phân biệt hoa thường |
| `roleOf(player)`, `roleDisplay(role)`, `roleColor(role)` | `ClanRole.LEADER / VICE / MEMBER`, khoá `MinhChu / PhoBang / ThanhVien` |
| `clans()`, `top(limit)`, `rank(clanUuid)` | `rank` từ 1, trả 0 nếu không có clan |
| `relation(a, b)`, `sameClan(a, b)`, `isFriendly(a, b)` | `isFriendly` = cùng người hoặc cùng clan (kể cả clan bật PvP nội bộ) |

`Clan` là ảnh chụp chỉ đọc: `uuid()` (không bao giờ đổi), `key()`, `name()` (legacy `§x` hex, cho scoreboard / hologram),
`nameComponent()` (Adventure), `plainName()`, `nameAmpersand()` (`&c`, `&#RRGGBB`), `description()`, `leader()`, `vice()`,
`members()` (**gồm cả Minh Chủ**), `roleOf(player)`, `points()`, `contribution(member)`, `maxMembers()` (-1 = không giới hạn),
`createdAt()`, `friendlyFire()`. Ảnh chụp không tự cập nhật: hỏi lại `ClanService` khi cần dữ liệu mới.

## 5. Cộng điểm: `ClanPoints`

| Hàm | Dùng khi |
|---|---|
| `award(player, source)` | Cộng theo luật của nguồn cho clan của người chơi, tính điểm đóng góp |
| `award(player, source, amount)` | Như trên, ghi đè số điểm của nguồn |
| `awardClan(clanUuid, source)` / `awardClan(clanUuid, source, amount)` | Cộng cho cả clan (không đóng góp), ví dụ KOTH |
| `startKillEvent(profile, duration)`, `stopKillEvent()`, `activeKillEvent()` | Sự kiện kill (§5.4); profile khai báo trong `points.yml` → `kill-events` |

`source` là id trong `points.yml` → `sources`. Luật của nguồn (cooldown, `every`, `daily-cap`, `clan-daily-cap`, boost BoxCore) luôn
áp dụng. **Thêm cách kiếm điểm mới chỉ cần thêm một nguồn vào `points.yml`**, không build lại BoxClans:

```yaml
sources:
  boss-kill:
    points: 25
    cooldown: 300
    clan-daily-cap: 200
    display: 'Hạ boss thế giới'
```

`PointResult`: `status()` (`SUCCESS`, `NO_CLAN`, `UNKNOWN_SOURCE`, `SOURCE_DISABLED`, `COOLDOWN`, `DAILY_CAP`, `CLAN_DAILY_CAP`,
`ACCUMULATED`, `CANCELLED`, `BLOCKED`, `ZERO`, `QUEUED`), `amount()` (điểm thực sự cộng, đã cắt cho vừa trần), `total()`.

## 5b. Minh Huy: `ClanCurrency` (1.2.0)

Minh Huy là tiền tệ chung của **cả bang hội** (không người chơi nào sở hữu), dùng để mua trong Cửa hàng bang hội. Chỉ cộng
qua lệnh admin hoặc API này. Số dư là số nguyên từ 0 đến `ClanCurrency.MAX_BALANCE` (1.000.000.000.000).

| Hàm | Dùng khi |
|---|---|
| `balance(clanUuid)` | Đọc số dư (mọi luồng) |
| `give(player, amount, reason)` | Cộng cho bang của người chơi (`NO_CLAN` nếu không có bang), ví dụ top KOTH |
| `giveClan(clanUuid, amount, reason)` | Cộng cho một bang |
| `take(clanUuid, amount, reason)` | Trừ; thiếu thì `NOT_ENOUGH` và **không trừ gì** |

`CurrencyResult`: `status()` (`OK`, `NO_CLAN`, `INVALID_AMOUNT`, `NOT_ENOUGH`, `LIMIT`, `QUEUED`), `amount()`, `balance()`.
Gọi từ luồng khác: việc được chuyển về main thread và trả `QUEUED`. Thành viên online được báo, mọi thay đổi ghi vào lịch sử
bang hội (người thực hiện: `api`). Không cần code: lệnh console `clanadmin minhhuy give player <tên> <số> <lý do> -s`.

## 6. Event (`com.dex.boxclans.api.event`)

| Event | Huỷ / sửa được | Dùng cho |
|---|---|---|
| `ClanCreateEvent`, `ClanDisbandEvent` | — | Log, dọn cache |
| `ClanMemberJoinEvent`, `ClanMemberLeaveEvent` (`reason()`: `LEAVE`, `KICK`, `OVER_LIMIT`, `DISBAND`) | — | Cache thành viên. Giải tán: mỗi thành viên nhận `LeaveEvent(DISBAND)` trước `DisbandEvent` |
| `ClanRenameEvent` (`oldName()`, `oldKey()`, `colorOnly()`) | — | Cập nhật tên trên hologram |
| `ClanRoleChangeEvent`, `ClanLeaderChangeEvent` | — | Lấy Minh Chủ mới |
| `ClanFriendlyFireChangeEvent` | — | — |
| `ClanChatEvent` | huỷ, sửa `setMessage` | Lọc từ cấm, log chat bang |
| `ClanPointsGainEvent` | huỷ, sửa `setAmount` | Nhân điểm (x2 cuối tuần…). Không bắn cho lệnh admin `add / take / set / reset` |
| `ClanPointsAwardedEvent` | — | Hiện "+5 điểm clan" ở plugin khác |
| `ClanMinhHuyChangeEvent` (`delta()`, `balance()`, `reason()`) | — | Số dư Minh Huy đã đổi (cộng, trừ, đặt, mua) |
| `ClanKillEventStartEvent`, `ClanKillEventEndEvent` | — | Scoreboard / bossbar sự kiện |
| `ClanActionEvent` (`action()`: `CREATE`, `DISBAND`, `LEAVE`, `JOIN`, `KICK`, `INVITE`, `TRANSFER`; `player()`, `clan()`, `target()`) | huỷ | Khoá thay đổi thành viên (KOTH). Bắn **trước** khi làm, lúc bắt đầu và lần nữa ngay trước khi chạy (sau ô chat / màn xác nhận). Plugin huỷ tự gửi lý do cho người chơi. Không bắn cho lệnh admin |

## 7. Từng plugin

### CrazyEnchantments (LegacyEnchants)

Spec riêng: `CrazyEnchantments-2.5.2/BOXCLANS_SUPPORT_SPEC.md`. Tóm tắt: `BoxClansSupport.isFriendly(a, b)` →
`ClanService.sameClan(a, b)`, kiểm tra **trước** nhánh claim plugin trong `PluginSupport.isFriendly`. Listener PvP trong bang của
BoxClans chạy ở `LOW` nên CE (`NORMAL`) thấy đòn đánh đồng đội đã bị huỷ khi PvP nội bộ tắt.

### BoxKOTH

Spec riêng: `BoxKOTH/KOTH_SPEC_UPDATE_BOXCLANS.md`. Ánh xạ `ClanProvider`:

| `ClanProvider` | BoxClans |
|---|---|
| `getClanId(player)` | `clanOf(player).map(c -> c.uuid().toString())` |
| `getClanName(id)` | `clan(UUID.fromString(id)).map(Clan::nameAmpersand)` (tên có màu dạng `&`, BoxKOTH tự dịch màu) |
| `getClanTag(id)` | `Clan::key` |
| `getLeader(id)`, `getMembers(id)`, `clanExists(id)` | `Clan::leader`, `Clan::members` (gồm Minh Chủ), `clan(id).isPresent()` |

Kết thúc KOTH có xếp hạng: `points.awardClan(clanUuid, "koth-top" + rank)` cho hạng 1 / 2 / 3 (điểm ở `points.yml`, không đặt số
trong BoxKOTH; cộng ngay kể cả khi Minh Chủ offline; clan đã giải tán trả `NO_CLAN`). Placeholder dùng được trên hologram:
`%boxclans_clan_<mã>_points%`, `%boxclans_top_1_name%`.

**Minh Huy cho top KOTH (1.2.0, không cần sửa BoxKOTH)**: thêm lệnh vào `rewards.yml` của BoxKOTH, **chỉ** ở mục `leader`
(hoặc `mvp`), **không bao giờ** ở `members`:

```yaml
ranks:
  1:
    leader: ['eco give %player% 10000', 'clanadmin minhhuy give player %player% 300 KOTH top 1']
    members: ['eco give %player% 2000']            # KHÔNG đặt minhhuy ở đây
  2:
    leader: ['eco give %player% 5000', 'clanadmin minhhuy give player %player% 150 KOTH top 2']
  3:
    leader: ['eco give %player% 2500', 'clanadmin minhhuy give player %player% 75 KOTH top 3']
```

- `members` chạy lệnh **một lần cho mỗi thành viên**, nên bang sẽ được cộng nhiều lần.
- `leader` chạy một lần. Với `vice-stands-in-for-leader: true`, Minh Chủ offline thì Phó Bang online nhận thay; cả hai
  offline thì phần thưởng `leader` (kèm Minh Huy) mất. Muốn chắc chắn có người nhận, đặt ở `mvp` (người giữ vùng lâu nhất).
- Cả bang được báo "Bang hội nhận +300 Minh Huy (KOTH top 1)"; thêm `-s` ở cuối nếu không muốn báo. Lịch sử ghi lý do.

### BoxQuest (không cần sửa BoxQuest)

Mỗi quest tự ghi số điểm clan trong `rewards.commands`:

```yaml
quests:
  stone:                       # quest dễ
    rewards:
      money: 5000
      commands:
        - command: 'boxcore gem give {player} {boost:10} quest:{quest}'
          display: '&b{boost:10} Gem'
        - command: 'clanadmin points give {player} quest 5 -s'
          display: '&#FFE1335 điểm clan'
  diamond-block:               # quest khó
    rewards:
      commands:
        - command: 'clanadmin points give {player} quest 40 -s'
          display: '&#FFE13340 điểm clan'
```

- Cộng vào clan của người hoàn thành và tính vào điểm đóng góp của họ. Người không có clan: lệnh trả "không có bang hội", quest vẫn hoàn thành.
- Muốn điểm clan nhân theo boost quest của BoxCore: dùng `{boost:5}` thay cho `5`.
- Mỗi lần cộng ghi sổ điểm với nguồn `quest` (`/clanadmin points log <mã>`).
- **Minh Huy** (1.2.0) theo cùng cách, mỗi quest tự đặt số:

```yaml
        - command: 'clanadmin minhhuy give player {player} 20 Quest {quest} -s'
          display: '&#C77DFF20 Minh Huy cho bang'
```

  Người không có bang: lệnh báo lỗi ở console, quest vẫn hoàn thành. Minh Huy không nhân theo boost (không dùng `{boost:..}`
  trừ khi bạn muốn).

### CataMines (sau này)

Hook block break trong mine gọi `points.award(player.getUniqueId(), "mine-block")`. Nguồn `mine-block` có `every: 100` nên chỉ
cộng (và ghi DB) mỗi 100 block. Bật nguồn bằng `enabled: true` trong `points.yml`. Cần spec riêng cho CataMines khi làm.

### BoxCore

`points.yml` → `boxcore-boost-type: 'clan'` (một boost type của BoxCore): điểm **dương** của các nguồn có `boost: true` được nhân
theo `BoostService.getMultiplier(player, type)`. BoxClans chỉ dùng BoxCore khi BoxCore đang bật (lớp duy nhất: `hook/BoxCoreHook`);
gỡ BoxCore thì hệ số là 1. Mùa giải và thưởng Gem cho top clan: phase sau (dùng `ClanService.top(n)` + `GemService`).
