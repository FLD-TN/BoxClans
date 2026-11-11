# BoxClans 1.0.0

Plugin bang hội (clan) cho server BoxPvP, viết lại từ đầu thay cho `Clans.jar` (shyamstudio). Paper 1.21.1, Java 21.
Tích hợp với plugin khác: `INTEGRATION.md`.

## Cài đặt

1. Build: `gradlew.bat build` (Windows) hoặc `./gradlew build`. Jar: `boxclans-plugin/build/libs/BoxClans-1.0.0.jar` (đã gộp sẵn API).
2. Chép jar vào `plugins/`. Phụ thuộc mềm (không bắt buộc): **PlaceholderAPI**, **BoxCore**.
3. Khởi động server, chỉnh các file trong `plugins/BoxClans/`, rồi `/clanadmin reload`.
4. Bắt đầu với dữ liệu mới (không chuyển dữ liệu từ Clans.jar). Gỡ `Clans.jar` **sau khi** BoxKOTH đã chuyển sang BoxClans.

## File cấu hình (`plugins/BoxClans/`)

| File | Nội dung |
|---|---|
| `config.yml` | Quy tắc tên clan (3–16 ký tự, từ cấm, cooldown đổi tên, permission màu), mô tả, chat prompt, lời mời (300 s, 10 lời mời), format chat bang, PvP trong bang, flag mặc định, placeholder, chống farm kill |
| `messages.yml` | **Toàn bộ** tin nhắn: mỗi tin có kênh `chat` và `actionbar` bật / tắt riêng |
| `roles.yml` | Tên hiển thị, màu, mô tả của 3 role và quyền của Phó Bang / Thành Viên |
| `perks.yml` | Giới hạn thành viên theo permission của Minh Chủ, số lớp hoa văn của cờ |
| `points.yml` | Nguồn điểm, trần điểm kill / ngày, múi giờ reset, profile sự kiện kill |
| `sounds.yml` | Âm thanh |
| `icons.yml` | Icon khiên động (Shop Clan): tốc độ khung, icon mặc định, danh sách icon, giá, permission |
| `menus/*.yml` | 9 GUI (có `clan-shop.yml`): tiêu đề, số hàng, layout, material, tên, lore |
| `clans.db` | Dữ liệu (SQLite). `antialt.log`: nhật ký các kill bị chặn |

Mọi file nhận `&c`, `&#RRGGBB` và MiniMessage (`<gradient:#FF0000:#FFAA00>...</gradient>`); `&&` là ký tự `&`.

**Tên clan** chỉ nhận: `&0`–`&f`, `&l` `&o` `&n`, hex dạng `&#RRGGBB`, `&x&R&R&G&G&B&B` hoặc `<#RRGGBB>`, và tag màu MiniMessage
(`<red>`, `<color:#FF0000>`). Gradient / rainbow, `&k`, `&m`, click / hover / font đều bị từ chối.
Thiếu khoá nào thì plugin lấy bản mặc định trong jar. Trong `points.yml`, nếu file có mục `sources` thì danh sách nguồn
trong file là chuẩn (xoá nguồn là mất nguồn). Không đặt id nguồn là `on`, `off`, `yes`, `no` (YAML đọc thành true / false).

## Lệnh người chơi (`/clan`, bí danh `clans`, `c`)

| Lệnh | Ai dùng |
|---|---|
| `/clan` | Có clan: menu chính. Chưa có: danh sách clan |
| `/clan create` | Ai cũng được (`boxclans.create`). Nhập tên vào chat, rồi xác nhận |
| `/clan disband` | Minh Chủ (có màn hình xác nhận) |
| `/clan invite [người]`, `/clan uninvite <người>` | Quyền role `INVITE_MEMBERS` (không ghi tên thì nhập qua chat) |
| `/clan accept [mã]`, `/clan deny [mã]`, `/clan invites` | Người được mời |
| `/clan leave` | Thành viên (Minh Chủ phải chuyển Minh Chủ hoặc giải tán) |
| `/clan kick <người>` | `KICK_MEMBERS` (Phó Bang chỉ kick Thành Viên) |
| `/clan setrole <người> <MinhChu\|PhoBang\|ThanhVien>` | Minh Chủ |
| `/clan promote \| demote \| transfer <người>` | Minh Chủ (viết tắt của setrole PhoBang / ThanhVien / MinhChu) |
| `/clan rename` | `RENAME_CLAN`. Chỉ đổi màu: không chờ. Đổi chữ: chờ 30 phút |
| `/clan description [mô tả]` | `SET_DESCRIPTION` (không ghi gì thì nhập qua chat, `-` để xoá) |
| `/clan flag` | `EDIT_FLAG` (thiết kế cờ, chỉ làm icon) |
| `/clan pvp` | `MODIFY_FRIENDLY_FIRE` |
| `/clan chat [tin]`, `/cc [tin]`, `!tin` | `USE_CLAN_CHAT`. Không có tin: bật / tắt chế độ chat bang |
| `/clan settings`, `/clan shop`, `/clan info [mã\|người]`, `/clan list`, `/clan top`, `/clan help` | Ai cũng được |

Trong menu chính, bấm vào đầu thành viên (chỉ Minh Chủ): trái = lên Phó Bang, phải = về Thành Viên,
shift-trái = chuyển Minh Chủ (xác nhận), shift-phải = kick (xác nhận; Phó Bang cũng kick được Thành Viên).

## Lệnh admin (`/clanadmin`, quyền `boxclans.admin`, chạy được từ console)

`<mã>` là **mã lệnh** của clan (tên bỏ màu, bỏ dấu, bỏ khoảng trắng, ví dụ `RongDo`); gõ `"Rồng Đỏ"` trong ngoặc kép cũng được.

| Lệnh | Tác dụng |
|---|---|
| `disband <mã>` | Giải tán clan |
| `kick <người>` | Kick khỏi clan (không kick được Minh Chủ) |
| `setrole <người> <MinhChu\|PhoBang\|ThanhVien> [confirm]` | Đổi role. Chuyển Minh Chủ có người bị kick, hoặc thay Phó Bang, thì phải gõ thêm `confirm` |
| `rename <mã> <tên màu mới>` | Đổi tên giúp clan (không cooldown) |
| `trim <mã>` | Kick ngẫu nhiên về đúng giới hạn thành viên (không kick Minh Chủ) |
| `points give <người> <nguồn> [số] [-s]` | Cộng theo luật của nguồn cho clan của người chơi, tính đóng góp |
| `points add\|take <mã> <số> [nguồn] [-s]` | Cộng / trừ thẳng (không cooldown, không boost). Nguồn mặc định `admin` |
| `points set <mã> <số>`, `points reset <mã\|all> [confirm]` | Đặt lại điểm (`all` cần `confirm`) |
| `points log <mã> [trang]` | Sổ điểm: thời gian, nguồn, người, điểm, ai cộng |
| `danhvong ...` | Tên khác của `points` (Danh Vọng) |
| `minhhuy give\|take <clan\|player> <mã\|tên> <số> [lý do] [-s]` | Cộng / trừ Minh Huy. `player <tên>` = bang của người đó (không có bang thì báo lỗi). Trừ quá số dư thì báo lỗi, không trừ |
| `minhhuy set <clan\|player> <mã\|tên> <số> [lý do]` | Đặt số dư (không báo bang) |
| `minhhuy reset clan <mã>`, `minhhuy reset all [confirm]` | Về 0 (`all` cần `confirm`) |
| `minhhuy log <clan\|player> <mã\|tên> [trang]` | Sổ Minh Huy (từ lịch sử bang hội) |
| `shop edit` | Mở Cửa hàng ở chế độ chỉnh sửa (thêm / sửa / ẩn / xoá vật phẩm bán, cần `boxclans.admin.shop`) |
| `shop add <danh mục> <giá> [giới hạn tuần]` | Thêm item đang cầm vào cửa hàng |
| `shop addcmd <id> <lệnh>`, `shop delcmd <id> <số>` | Thêm / xoá lệnh console khi rút (mục thành kiểu "chạy lệnh", dùng cho key ảo). `{player}` `{clan}` `{amount}` |
| `shop price\|limit <id> <số>`, `shop category <id> <danh mục>`, `shop mode <id> <item\|command>`, `shop hide\|show\|remove <id>`, `shop list [danh mục]` | Sửa mục bán |
| `kho give <clan\|player> <mã\|tên> <danh mục> [số] [-s]` | Cho item đang cầm vào kho bang hội |
| `kho giveshop <clan\|player> <mã\|tên> <id> [số] [-s]` | Cho bản sao của một mục cửa hàng (kể cả lệnh), dùng làm thưởng event |
| `kho view <mã>`, `kho remove <mã> <id ô> [số]` | Xem kho của bang (chỉ xem) / lấy bớt đồ khỏi kho |
| `boost start <clan\|player> <mã\|tên> <loại> <hệ số> <thời gian>`, `boost stop <clan\|player> <mã\|tên> <loại>` | Bật / dừng boost bang hội (cần BoxCore 1.5.0) |
| `history <mã>` | Xem lịch sử của một bang (GUI) |
| `killevent start <profile> <thời gian>`, `killevent stop`, `killevent status` | Sự kiện kill (ví dụ `start loan-chien 30m`) |
| `icon set <mã> <icon\|none>` | Đặt icon khiên cho clan (tự cho sở hữu), `none` để gỡ |
| `icon unlock\|lock <mã> <icon>` | Cho sở hữu / thu hồi icon (thu hồi icon đang dùng thì clan về không có icon) |
| `icon list [mã]` | Danh sách icon, hoặc icon đang dùng và đã sở hữu của một clan |
| `spy` | Xem chat của mọi bang (`boxclans.spy`) |
| `reload` | Nạp lại toàn bộ file |

`-s` = không báo cho thành viên. Mọi thay đổi điểm đều ghi vào sổ điểm.

## Permission

| Permission | Mặc định | Ý nghĩa |
|---|---|---|
| `boxclans.use` | mọi người | `/clan` |
| `boxclans.create` | mọi người | Tạo clan |
| `boxclans.chat` | mọi người | Chat bang |
| `boxclans.spy` | op | `/clanadmin spy` |
| `boxclans.rename.bypasscooldown` | op | Đổi chữ tên không chờ |
| `boxclans.admin.antialt` | op | Nhận cảnh báo acc phụ (luật A / B) |
| `boxclans.antialt.bypass` | không ai | Bỏ luật A / B (người chơi chung mạng đã xác minh) |
| `boxclans.admin` | op | Mọi lệnh `/clanadmin` |
| `boxclans.members.<số>` | — | Giới hạn thành viên = số đó, ví dụ `boxclans.members.20` (của **Minh Chủ**; lấy số lớn nhất; mặc định 8) |
| `boxclans.members.vip` / `.mvp` / `.legend` / `.unlimited` | — | Bậc đặt tên trong `perks.yml` (12 / 16 / 24 / không giới hạn); dùng song song với kiểu số |
| `boxclans.banner.6` / `.12` / `.16` | — | Số lớp hoa văn của cờ (`perks.yml`; mặc định 6) |
| Permission màu trong `config.yml` (`clan-name.color-permissions`) | — | Để trống = ai cũng dùng được |

Gán bằng LuckPerms, ví dụ: `lp group vip permission set boxclans.members.vip`.

**Quyền role** (`roles.yml`, cấp cho Phó Bang / Thành Viên): `INVITE_MEMBERS`, `KICK_MEMBERS`, `RENAME_CLAN`, `SET_DESCRIPTION`,
`EDIT_FLAG`, `MODIFY_FRIENDLY_FIRE`, `USE_CLAN_CHAT`. Mặc định Phó Bang có tất cả trừ `RENAME_CLAN`; Thành Viên chỉ có chat.
Đổi role, chuyển Minh Chủ và giải tán luôn chỉ Minh Chủ.

## Placeholder (PlaceholderAPI, `%boxclans_...%`)

Mọi giá trị là chữ legacy (`§` + hex), dùng được ở TAB, scoreboard, hologram, chat. Người không có clan nhận `placeholders.none-value`.
Các placeholder tên có màu (`name`, `name_prefix`, `top_<n>_name`, `clan_<mã>_name`) luôn kết thúc bằng `§r`, nên màu / in đậm của
tên không lan sang chữ phía sau; chữ phía sau nên tự có mã màu (ví dụ `%boxclans_name% &7| ...`), nếu không sẽ là màu trắng mặc định.

| Nhóm | Placeholder |
|---|---|
| Clan | `has_clan`, `name`, `name_plain`, `key`, `name_prefix` (tên + khoảng trắng, rỗng nếu không có clan), `points`, `rank`, `members`, `members_online`, `max_members`, `leader`, `vice`, `pvp`, `kill_points_today`, `kill_points_cap` |
| Role | `role` (`§cMinh Chủ`), `role_plain`, `role_key` (`MinhChu`), `role_color` (`§c`) |
| Cá nhân | `contribution` |
| Minh Huy (chỉ bang của người chơi) | `minhhuy` (`1250`), `minhhuy_formatted` (`1.250`) |
| Xếp hạng | `top_<n>_name`, `top_<n>_name_plain`, `top_<n>_key`, `top_<n>_points`, `top_<n>_leader`, `top_<n>_members` |
| Theo mã lệnh | `clan_<mã>_name`, `clan_<mã>_points`, `clan_<mã>_rank`, `clan_<mã>_members`, `clan_<mã>_leader` |
| Sự kiện | `kill_event` |
| Icon khiên | `icon` (động, cho tab / scoreboard / nametag), `icon_static` (tĩnh, cho chat), `icon_id` (`royal`, `none`), `top_<n>_icon`, `clan_<mã>_icon` |
| Hai người | `%rel_boxclans_relation%` (`same` / `none`), `%rel_boxclans_color%` (màu trong `placeholders.relation-colors`) |

Hai placeholder icon trả về `§f` + icon + **1 khoảng trắng**, và trả về **rỗng hoàn toàn** khi clan chưa có icon (không dùng
`none-value`). Vì vậy viết liền: `[%boxclans_icon%%boxclans_name%]` cho ra `[test]` khi không có icon và `[🛡 test]` khi có.
Icon động cần TAB / UnlimitedNameTag làm mới placeholder theo `frame-interval-ms`.

## Icon khiên động (Shop Clan)

1. Chép thư mục `LEGACY-BOXPVP/shieldranks/` vào `plugins/ItemsAdder/contents/shieldranks/` rồi chạy `/iazip`.
   Pack do `tools/build_shieldranks.py` sinh ra từ pack "ANIMATED 9+ Shield rank icons": mỗi khung GIF là một font image.
2. Cài BoxClans mới. Server có ItemsAdder thì BoxClans tự đọc ký tự icon, và đọc lại mỗi khi ItemsAdder load xong (`/iareload`).
3. Minh Chủ mở `/clan shop` (hoặc `/clan` rồi bấm nút **Shop Clan**, ô `H`) và chọn icon. Thành viên khác chỉ xem được.
4. Sửa TAB / UnlimitedNameTag / EzChat để hiện placeholder icon.

Server đang dùng `menus/clan-detail.yml` cũ thì nút Shop chưa hiện: thêm `H` vào layout (`'LIHPAN.SF'`) và mục `items.H`
(chép từ bản mới trong jar).

## Điểm clan (tóm tắt)

- Kill người khác clan hoặc người không có clan: **+10** cho clan người giết; chết **không trừ** (nguồn `death` = 0).
- Cùng clan đánh nhau (kể cả khi bật PvP trong bang): không tính.
- Trần **500 điểm kill / clan / ngày**, reset 0h giờ Việt Nam; gần trần thì cắt cho vừa; cả clan nhận thông báo một lần.
- Chống farm: **A** cùng IP, **B** một mạng nhiều acc (10 phút), **C** quá 3 kill cùng một người trong 10 phút. Dính luật: không ai được / mất điểm, người giết nhận cảnh báo, staff nhận tin khi dính A / B, mọi lần chặn ghi `antialt.log`.
- Sự kiện kill (`points.yml` → `kill-events`): trong lúc chạy, kill / chết theo điểm của profile; `ignore-limits` bỏ luật C và trần ngày (luật A / B vẫn áp dụng); điểm trong sự kiện không ăn vào trần ngày.

## Checklist test trong game (server test)

Chạy với ít nhất 3 tài khoản (A, B, C), một tài khoản staff, và vài acc phụ / VPN cho luật chống farm.

**Clan và role**
- [ ] `/clan create`, gõ tên sai (`ab`, `&kAbc`, `Rồng@`): báo lỗi đúng và chờ nhập lại; `cancel` huỷ; im 60 s thì tự huỷ. Tin gõ không hiện chat chung.
- [ ] Gõ `&c&lRồng &6Đỏ`: màn hình xác nhận hiện tên có màu và "Mã lệnh: RongDo"; xác nhận thì cả server nhận thông báo.
- [ ] Tạo clan `&aRong Do` bằng tài khoản khác: bị từ chối vì trùng mã lệnh.
- [ ] Mời B (lệnh và nút "ɪɴᴠɪᴛᴇ ᴘʟᴀʏᴇʀ"), B bấm `[Chấp nhận]` trong chat; mời C rồi C từ chối; lời mời tự hết hạn sau 5 phút.
- [ ] Clan 8 người (Minh Chủ không có permission) không mời / nhận thêm được; gán `boxclans.members.vip` cho Minh Chủ, vào lại server: giới hạn thành 12.
- [ ] Menu chính: trái / phải / shift-trái / shift-phải trên đầu thành viên đúng §4A; đã có Phó Bang mà đưa người khác lên thì có màn hình "X sẽ trở về Thành Viên".
- [ ] Chuyển Minh Chủ cho người giới hạn thấp hơn (14 người → 12): màn hình ghi rõ 2 người bị kick ngẫu nhiên; Minh Chủ cũ và mới không bao giờ bị kick; người offline bị kick nhận thông báo khi vào lại.
- [ ] `/clan rename`: chỉ đổi màu thì đổi ngay nhiều lần; đổi chữ thì lần thứ hai bị chờ 30 phút. Tab-complete `/clanadmin disband ` chỉ hiện mã lệnh, không có mã màu.
- [ ] Rời clan, kick, giải tán (có xác nhận), Minh Chủ không rời được.

**Chat, PvP, GUI**
- [ ] `/cc chào &canh`, `!tin`, `/cc` (chế độ chat): định dạng `ᴄʟᴀɴ | (Minh Chủ) Tên: tin`, tên và role cùng màu role, nội dung màu vàng, `&c` hiện nguyên chữ. Staff `/clanadmin spy` thấy tin kèm tên bang.
- [ ] PvP trong bang tắt: đánh tay, bắn cung, ném đinh ba vào đồng đội bị huỷ; tin báo lỗi tối đa 1 lần / 3 giây; Minh Chủ / Phó Bang bấm vào tin thì mở settings; Thành Viên nhận bản "hãy nhờ...". Bật PvP: đánh nhau được, hạ nhau không có điểm.
- [ ] So sánh song song với Clans.jar (trên server cũ): layout, màu, font của 8 menu giống bản gốc (trừ các nút đã bỏ).
- [ ] Không lấy được item nào ra khỏi bất kỳ menu nào (kể cả shift-click, kéo, phím số).
- [ ] Flag maker: chọn nền, đổi màu hoa văn, thêm lớp (tối đa 6, hoặc 12 / 16 theo permission), undo, reset, save; cờ hiện ở danh sách clan, preview, bảng xếp hạng, menu chính.
- [ ] Sửa `menus/settings.yml` (tiêu đề, vị trí nút), `/clanadmin reload`: menu đổi theo.

**Điểm**
- [ ] Kill người khác clan: +10, cả clan thấy thông báo; chết không mất điểm; kill người không có clan: +10.
- [ ] Giết cùng một người 4 lần trong 10 phút: lần 4 không có điểm, có cảnh báo; 10 phút sau lại được.
- [ ] Hai acc cùng IP giết nhau: không có điểm, staff nhận `anti-alt`, `antialt.log` có dòng.
- [ ] Đạt 500 điểm kill trong ngày: kill tiếp không có điểm, clan nhận thông báo một lần; lore "ᴄʟᴀɴ ꜱᴄᴏʀᴇ" hiện `Hôm nay: 500/500`; sau 0h được tính lại.
- [ ] `/clanadmin killevent start loan-chien 10m`: kill vượt trần vẫn có điểm, chết bị trừ 5, cùng IP vẫn bị chặn; `stop` thì trở lại bộ đếm cũ.
- [ ] `/clanadmin points give <người> quest 5 -s` từ console; `points log`, `add`, `take`, `set`, `reset all confirm`.

**Placeholder và plugin khác**
- [ ] `/papi parse me %boxclans_name%` (tên màu hex ra đúng màu), `%boxclans_name_prefix%` trên TAB, `%boxclans_clan_RongDo_points%` trên hologram, `%rel_boxclans_color%` cho nametag.
- [ ] Gỡ PlaceholderAPI / BoxCore: BoxClans vẫn bật, không lỗi trong log.
- [ ] `boxcore-boost-type: 'clan'` + boost BoxCore đang chạy: nguồn có `boost: true` được nhân.
- [ ] CrazyEnchantments và BoxKOTH: xem checklist trong spec của từng plugin.

## Build

```
./gradlew build
```

Repo chỉ có mã nguồn, không chứa file jar nào (trừ `gradle/wrapper/gradle-wrapper.jar` chuẩn của Gradle).
Trước khi build, tự đặt các jar sau vào thư mục `libs/` ở gốc repo:

- `boxcore-api-1.3.0.jar` - build từ [BoxCore](https://github.com/FLD-TN/BoxCore) (`boxcore-api/build/libs/`)
