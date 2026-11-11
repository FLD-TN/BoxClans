# BoxClans

Plugin bang hội của server BoxPvP: tạo clan, 3 vai trò, chat bang, điểm Danh Vọng và bảng xếp hạng, tiền chung của
bang (Minh Huy), cửa hàng và kho bang hội, icon khiên động, boost bang hội và lịch sử bang. ClanKoth, BoxPet, MyBoxMobs
và bản CrazyEnchantments custom đọc clan qua API của plugin này.

Paper 1.21.1, Java 21. Không có phụ thuộc bắt buộc. PlaceholderAPI, BoxCore (boost), LuckPerms và ItemsAdder (icon)
có thì dùng.

## Clan và vai trò

- Tên clan 3 đến 16 ký tự, có màu. Mỗi clan có một **mã lệnh** (tên bỏ màu, bỏ dấu, bỏ khoảng trắng, ví dụ `RongDo`)
  dùng trong lệnh và placeholder; hai clan không được trùng mã lệnh.
- Tên nhận `&0`-`&f`, `&l` `&o` `&n`, hex (`&#RRGGBB`, `&x&R&R...`, `<#RRGGBB>`) và tag màu MiniMessage. Gradient,
  rainbow, `&k`, `&m`, click / hover / font bị từ chối. Đổi màu tên thì không chờ; đổi chữ thì chờ 30 phút.
- 3 vai trò: Minh Chủ, Phó Bang, Thành Viên. Quyền của Phó Bang và Thành Viên chỉnh trong `roles.yml`
  (`INVITE_MEMBERS`, `KICK_MEMBERS`, `RENAME_CLAN`, `SET_DESCRIPTION`, `EDIT_FLAG`, `MODIFY_FRIENDLY_FIRE`,
  `USE_CLAN_CHAT`). Đổi vai trò, chuyển Minh Chủ và giải tán luôn chỉ Minh Chủ làm được.
- Giới hạn thành viên theo quyền của Minh Chủ: `boxclans.members.<số>` hoặc các bậc đặt tên trong `perks.yml`
  (mặc định 8). Chuyển Minh Chủ cho người có giới hạn thấp hơn thì số người thừa bị kick ngẫu nhiên (màn hình xác
  nhận ghi rõ trước), Minh Chủ cũ và mới không bao giờ bị kick.
- Cờ bang thiết kế trong game (`/clan flag`), dùng làm icon ở danh sách, bảng xếp hạng và menu. Số lớp hoa văn theo
  quyền `boxclans.banner.<số>`.
- PvP trong bang mặc định tắt (đánh tay, cung, đinh ba vào đồng đội đều bị chặn).

## Danh Vọng (điểm clan)

Nguồn điểm khai báo trong `points.yml`. Mặc định:

- Hạ người khác clan hoặc người không có clan: +10 cho clan người giết. Chết không bị trừ. Cùng clan đánh nhau không
  tính. Tối đa 500 điểm kill mỗi clan mỗi ngày, reset 0h giờ Việt Nam.
- Chống farm kill: **A** cùng IP, **B** một mạng nhiều tài khoản trong 10 phút, **C** giết cùng một người quá 3 lần
  trong 10 phút. Dính luật thì không ai được hay mất điểm, người giết bị cảnh báo, staff được báo khi dính A / B, mọi
  lần chặn ghi vào `antialt.log`.
- Nguồn khác: KOTH top 1 / 2 / 3, thắng Clan War, event, quest, boss của MyBoxMobs, đào block (mặc định tắt). Nguồn
  có `boost: true` được nhân theo boost BoxCore (`boxcore-boost-type`).
- Sự kiện kill (`kill-events`): trong lúc chạy, kill và chết tính theo điểm riêng của sự kiện. `ignore-limits` bỏ luật
  C và trần ngày, luật A / B vẫn áp dụng.

Mọi thay đổi điểm ghi vào sổ điểm (`/clanadmin points log`). Điểm đóng góp của từng người cũng được lưu.

## Minh Huy, cửa hàng, kho

- **Minh Huy** là tiền chung của bang (0 đến 1.000.000.000.000), không người chơi nào sở hữu riêng, không donate được.
  Chỉ admin hoặc plugin khác cộng / trừ.
- **Cửa hàng** (`/clan shop`): bán icon, chai boost bang hội, key và đồ khác bằng Minh Huy. Chỉ Minh Chủ / Phó Bang
  mua, mỗi lần một cái, luôn có màn hình xác nhận. Mỗi mục có thể giới hạn số lần mua mỗi tuần (làm mới 0h thứ Hai).
  Admin thêm hàng ngay trong game; item lưu nguyên tên, lore, NBT. Mục kiểu "chạy lệnh" chỉ để trưng bày, khi rút ra
  thì chạy lệnh console (dùng cho key ảo).
- **Kho** (`/clan kho`): chứa đồ đã mua hoặc admin cho. Cả bang xem được, Minh Chủ / Phó Bang rút. Túi đầy thì không
  rút và kho không bị trừ. Chai boost bang hội trong kho: chuột trái dùng luôn cho bang, chuột phải rút ra.
- **Lịch sử** (`/clan lichsu`): vào / rời / kick, đổi vai trò, đổi tên, đổi icon, PvP, Minh Huy, mua, rút, boost. Tự
  xoá bản ghi cũ hơn `history.keep-days` (mặc định 30).

## Icon khiên động

Mỗi clan đeo được một icon khiên cạnh tên (tab, scoreboard, nametag, chat). Vài icon có sẵn cho mọi bang
(`default-owned: true` trong `icons.yml`), icon khác mua trong cửa hàng. Minh Chủ và Phó Bang trang bị ở
`/clan icons`.

Icon là font image của ItemsAdder. `tools/build_shieldranks.py` dựng content pack `shieldranks` từ một pack icon GIF
32x32 (pack icon không có trong repo):

```
python -I tools/build_shieldranks.py <thư mục pack icon> <thư mục ra>
```

Chép thư mục ra vào `plugins/ItemsAdder/contents/shieldranks/`, chạy `/iazip`. BoxClans tự đọc lại ký tự icon mỗi khi
ItemsAdder load xong. TAB / UnlimitedNameTag cần làm mới placeholder theo `frame-interval-ms` để icon chạy.

## Lệnh người chơi (`/clan`, `/clans`, `/c`)

| Lệnh | Ai dùng |
|---|---|
| `/clan` | Có clan: menu chính. Chưa có: danh sách clan |
| `/clan create` | Mọi người (`boxclans.create`). Nhập tên vào chat rồi xác nhận |
| `/clan disband` | Minh Chủ (có xác nhận) |
| `/clan invite [người]`, `/clan uninvite <người>` | `INVITE_MEMBERS` |
| `/clan accept [mã]`, `/clan deny [mã]`, `/clan invites` | Người được mời |
| `/clan leave` | Thành viên (Minh Chủ phải chuyển Minh Chủ hoặc giải tán trước) |
| `/clan kick <người>` | `KICK_MEMBERS` (Phó Bang chỉ kick được Thành Viên) |
| `/clan setrole <người> <MinhChu\|PhoBang\|ThanhVien>` | Minh Chủ |
| `/clan promote\|demote\|transfer <người>` | Minh Chủ |
| `/clan rename` | `RENAME_CLAN` |
| `/clan description [mô tả]` | `SET_DESCRIPTION` (`-` để xoá) |
| `/clan flag` | `EDIT_FLAG` |
| `/clan pvp` | `MODIFY_FRIENDLY_FIRE` |
| `/clan chat [tin]`, `/cc [tin]`, `!tin` | `USE_CLAN_CHAT`. Không ghi tin thì bật / tắt chế độ chat bang |
| `/clan shop`, `/clan kho`, `/clan lichsu`, `/clan icons` | Thành viên (mua, rút, đổi icon: Minh Chủ / Phó Bang) |
| `/clan settings`, `/clan info [mã\|người]`, `/clan list`, `/clan top`, `/clan help` | Mọi người |

Trong menu chính, Minh Chủ bấm vào đầu thành viên: trái lên Phó Bang, phải về Thành Viên, Shift + trái chuyển Minh Chủ,
Shift + phải kick (hai thao tác cuối có xác nhận).

## Lệnh admin (`/clanadmin`, `/ca`)

Cần `boxclans.admin`, chạy được từ console. `<mã>` là mã lệnh của clan; gõ tên trong ngoặc kép (`"Rồng Đỏ"`) cũng được.
`-s` là không báo cho thành viên.

| Lệnh | Tác dụng |
|---|---|
| `disband <mã>`, `rename <mã> <tên mới>` | Giải tán, đổi tên (không cooldown) |
| `kick <người>`, `setrole <người> <vai trò> [confirm]` | Kick, đổi vai trò |
| `trim <mã>` | Kick ngẫu nhiên cho về đúng giới hạn thành viên |
| `points give <người> <nguồn> [số] [-s]` | Cộng điểm theo luật của nguồn |
| `points add\|take <mã> <số> [nguồn] [-s]`, `points set <mã> <số>` | Sửa thẳng điểm |
| `points reset <mã\|all> [confirm]`, `points log <mã> [trang]` | Đặt lại, xem sổ điểm. `danhvong` là tên khác của `points` |
| `minhhuy give\|take\|set <clan\|player> <mã\|tên> <số> [lý do] [-s]` | Sửa Minh Huy. `player <tên>` là bang của người đó |
| `minhhuy reset clan <mã>`, `minhhuy reset all [confirm]`, `minhhuy log ...` | Đặt về 0, xem sổ |
| `shop edit`, `shop add <danh mục> <giá> [giới hạn tuần]` | Chỉnh cửa hàng, thêm item đang cầm |
| `shop addcmd\|delcmd\|price\|limit\|category\|mode\|hide\|show\|remove\|list ...` | Sửa từng mục bán |
| `kho give\|giveshop <clan\|player> <mã\|tên> ...`, `kho view <mã>`, `kho remove <mã> <ô> [số]` | Cho đồ vào kho, xem, lấy bớt |
| `boost start\|stop <clan\|player> <mã\|tên> <loại> ...` | Bật / dừng boost bang hội (cần BoxCore) |
| `history <mã>` | Xem lịch sử một bang |
| `killevent start <profile> <thời gian>`, `killevent stop\|status` | Sự kiện kill |
| `icon set <mã> <icon\|none>`, `icon unlock\|lock <mã> <icon>`, `icon list [mã]` | Quản lý icon |
| `spy` | Xem chat của mọi bang (`boxclans.spy`) |
| `reload` | Đọc lại mọi file |

## Permission

| Permission | Mặc định | Ý nghĩa |
|---|---|---|
| `boxclans.use`, `boxclans.create`, `boxclans.chat` | mọi người | `/clan`, tạo clan, chat bang |
| `boxclans.spy` | op | `/clanadmin spy` |
| `boxclans.rename.bypasscooldown` | op | Đổi chữ tên không chờ |
| `boxclans.admin.antialt` | op | Nhận cảnh báo luật A / B |
| `boxclans.antialt.bypass` | không ai | Bỏ luật A / B (người chơi chung mạng đã xác minh) |
| `boxclans.admin.shop` | op | Nút Chỉnh sửa trong cửa hàng |
| `boxclans.admin` | op | Mọi lệnh `/clanadmin` |
| `boxclans.members.<số>` hoặc bậc trong `perks.yml` | - | Giới hạn thành viên (của Minh Chủ, lấy số lớn nhất) |
| `boxclans.banner.<số>` | - | Số lớp hoa văn của cờ |

## Placeholder (`%boxclans_...%`)

Giá trị là chữ legacy (`§` + hex), dùng được ở TAB, scoreboard, hologram, chat. Người không có clan nhận
`placeholders.none-value`. Placeholder tên có màu luôn kết thúc bằng `§r`, nên chữ phía sau cần tự có mã màu.

| Nhóm | Placeholder |
|---|---|
| Clan | `has_clan`, `name`, `name_plain`, `key`, `name_prefix`, `points`, `rank`, `members`, `members_online`, `max_members`, `leader`, `vice`, `pvp`, `kill_points_today`, `kill_points_cap` |
| Vai trò | `role`, `role_plain`, `role_key`, `role_color` |
| Cá nhân | `contribution` |
| Minh Huy | `minhhuy`, `minhhuy_formatted` (chỉ bang của người xem) |
| Xếp hạng | `top_<n>_name`, `top_<n>_name_plain`, `top_<n>_key`, `top_<n>_points`, `top_<n>_leader`, `top_<n>_members`, `top_<n>_icon` |
| Theo mã lệnh | `clan_<mã>_name`, `clan_<mã>_points`, `clan_<mã>_rank`, `clan_<mã>_members`, `clan_<mã>_leader`, `clan_<mã>_icon` |
| Sự kiện | `kill_event` |
| Icon | `icon` (động), `icon_static` (tĩnh, cho chat), `icon_id` |
| Hai người | `%rel_boxclans_relation%` (`same` / `none`), `%rel_boxclans_color%` |

`icon` và `icon_static` trả về icon kèm một khoảng trắng, hoặc rỗng hẳn khi clan chưa có icon. Vì vậy viết liền
`[%boxclans_icon%%boxclans_name%]` sẽ ra `[test]` khi chưa có icon.

## File cấu hình (`plugins/BoxClans/`)

| File | Nội dung |
|---|---|
| `config.yml` | Luật tên clan, mô tả, lời mời, format chat bang, PvP, placeholder, chống farm, lịch sử |
| `messages.yml` | Mọi tin nhắn; mỗi tin bật / tắt riêng kênh chat và actionbar |
| `roles.yml`, `perks.yml` | Vai trò và quyền; giới hạn thành viên, số lớp cờ |
| `points.yml` | Nguồn điểm, trần ngày, sự kiện kill |
| `shop.yml`, `icons.yml`, `sounds.yml` | Danh mục cửa hàng, icon, âm thanh |
| `menus/*.yml` | Bố cục và chữ của từng GUI |
| `clans.db` | Dữ liệu (SQLite) |

Mọi file nhận `&c`, `&#RRGGBB` và MiniMessage. Thiếu khoá nào thì dùng bản mặc định trong jar. Trong `points.yml`, đừng
đặt id nguồn là `on`, `off`, `yes`, `no` (YAML đọc thành true / false).

API cho plugin khác: [INTEGRATION.md](INTEGRATION.md).

## Build

```
./gradlew build
```

Ra file `boxclans-plugin/build/libs/BoxClans-<version>.jar` (đã chứa sẵn API) và
`boxclans-api/build/libs/boxclans-api-<version>.jar` cho plugin khác compile. Repo chỉ chứa mã nguồn, không có jar nào
ngoài `gradle/wrapper/gradle-wrapper.jar` của Gradle. Trước khi build, đặt vào thư mục `libs/`:

- `boxcore-api-1.3.0.jar`: build từ [BoxCore](https://github.com/FLD-TN/BoxCore) (`boxcore-api/build/libs/`)
