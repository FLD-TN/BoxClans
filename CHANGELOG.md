# BoxClans: CHANGELOG

Mỗi phiên bản có file `boxclans-<cũ>-to-<mới>.diff` ở thư mục gốc `BoxClans/` (so với bản snapshot
`.snapshot-<cũ>/`). Spec: `BOXCLANS_SHOP_SPEC.md`.

## 1.4.4 (2026-10-23): Rà soát lỗi / leak / mất dữ liệu

- **Mua, rút, dùng không còn ghi dở dang**: trừ Minh Huy, thêm vào kho, đếm lượt mua tuần và lịch sử của một lần mua
  được ghi trong cùng một transaction (`Database.atomically`). Server dừng đột ngột hay ổ đĩa lỗi giữa chừng thì hoặc
  có đủ, hoặc không có gì; trước đây có thể mất Minh Huy mà chưa có đồ trong kho. Tương tự cho mua icon, rút đồ, dùng
  chai từ kho.
- **Leak RAM nhỏ**: bộ chống bấm đúp của shop / kho giữ mọi người chơi từng bấm mãi mãi; giờ tự bỏ các mục cũ.
- Layout chủ server sửa tay: `menus/clan-detail.yml` dòng cuối `HKA.I.GSL` (`text-version: 9`), `menus/clan-icons.yml`
  (nút T giữa dòng cuối, nút gỡ icon BRUSH, `text-version: 4`) để bản trên server nhận thay đổi.

**Kiểm tra**: 176 test pass (`AtomicWriteTest` 3 test mới). Mutation check: bỏ gộp transaction → test báo lỗi.

## 1.4.3 (2026-10-23): Chỉnh giao diện Shop, Kho, Lịch sử, Cài đặt; Minh Chủ màu vàng cam

- **Minh Chủ** hiện bằng gradient vàng cam (`<gradient:#FFD600:#FF8C00>`) trong menu và thông báo, ví dụ
  "Minh Chủ Alice đã mua...". Chat bang hội và placeholder cho plugin khác vẫn `&c`. `roles.yml`: `gui-color` nhận
  gradient; file còn `&#FF5C5C` (mặc định cũ) tự dùng gradient, màu đã sửa được giữ.
- **Cửa hàng**: tab dời sang phải 1 ô, dòng 2 là kính xám, ô dưới tab đang xem chuyển xanh lime (bấm ô đó cũng mở tab).
  Vật phẩm từ dòng 3 (21 ô mỗi trang). Bỏ nút Kho. Giữa dòng cuối là **Tài chính bang hội** (thạch anh tím, số dư Minh
  Huy), hai mũi tên sát hai bên.
- **Kho**: cùng kiểu tab + dòng đánh dấu, viền kính xám, bỏ nút Cửa hàng, thùng thông tin nằm giữa hai mũi tên. Lore vật
  phẩm: `• Số lượng: x2` (số màu &b), 1 dòng trống rồi dòng thao tác dạng `[Chuột trái để kích hoạt]`. Chai boost bang
  hội: chuột trái dùng, **tạm tắt rút ra túi** (`bottle-withdraw: false` trong `menus/clan-vault.yml`).
- **Lịch sử**: viền kính xám, tab dời phải 1 ô + dòng đánh dấu, 21 tờ mỗi trang, dòng cuối `B..PIN...` (đồng hồ số trang
  ở giữa, mũi tên sát hai bên).
- **Cờ thông tin** trong `/clan`: Mã bang hội, dòng trống, Mô tả (tự xuống dòng mỗi 30 ký tự), dòng trống, rồi các dòng
  còn lại. Mô tả tối đa **50** ký tự (`config.yml`; giá trị 64 cũ được hiểu là 50). Lore nút Lịch sử: "Xem các hoạt động
  của bang hội trong 30 ngày gần nhất." (2 dòng).
- **Cài đặt**: dòng 1 là Đổi tên, Cờ, Icon (ô 1-3) và 4 nút bật / tắt dồn về ô 6-9.
- Tên Minh Chủ trong danh sách bang, xem trước, bảng xếp hạng, `/clan info` cũng màu vàng cam.
- Lên `text-version`: `messages.yml` 6, `clan-detail` 8, `clan-shop` 4, `clan-vault` 5, `clan-history` 4, `settings` 4,
  `confirm` 4, `clan-list` 3, `clan-preview` 3, `leaderboard` 3 (bản cũ vào `old-configs/`).

**Kiểm tra**: 173 test pass (mới: `Ui143Test` 4 test, `tabMarkersFollowTheOpenTab`, `historyLayoutAndMarkers`; test đổi
theo yêu cầu: 21 ô mỗi trang, PvP ở ô 6, mô tả tối đa 50, chuột phải không rút chai, Minh Chủ gradient). Mutation check:
ô lime không đổi, chuột phải vẫn rút chai, thông báo dùng màu chat, không nâng màu cũ của Minh Chủ, giữ 64 → đều bị bắt.

## 1.4.2 (2026-10-23): Kho và Lịch sử về lại menu chính

Thay cho 1.4.1 (bỏ menu Quản lý bang hội, theo ý chủ server). Diff so với 1.4.0: `boxclans-1.4.0-to-1.4.2.diff`.

- Menu `/clan`: hàng dưới về lại `LIHKAG.SF` như 1.4.0. Nút Kho (K) và Lịch sử (G) hiện cho **mọi thành viên**.
- **Lịch sử**: cả bang xem được (nút G hoặc `/clan lichsu`). Bỏ tin `history-not-allowed`.
- **Kho**: thành viên thường chỉ xem. Bấm vào vật phẩm không làm gì, không báo gì (bỏ tin `vault-view-only`), không có
  dòng "Chỉ Minh Chủ và Phó Bang..." (`vault-item.lore-member: []`).
- **Kho, mọi người xem**: ẩn các dòng lore gốc của vật phẩm có chữ trong `hide-lore-containing` (mặc định: "để kích hoạt",
  "chuột phải", "chuột trái", "right click", "left click"...). Ví dụ dòng `[Chuột phải để kích hoạt]` của chai boost. Trong
  kho, Minh Chủ / Phó Bang vẫn thấy dòng hướng dẫn riêng của kho (chuột trái dùng, chuột phải rút). Vật phẩm rút ra giữ
  nguyên lore.
- `menus/clan-detail.yml` lên `text-version: 7`, `menus/clan-vault.yml` lên 4, `messages.yml` lên 5 (bản cũ vào
  `old-configs/`). Server đã chạy 1.4.1 thì `menus/clan-manage.yml` không còn dùng, xoá được.

**Kiểm tra**: 167 test pass. Test mới: `everyMemberOpensIt`, `membersOnlyLookAtTheStorage`, `storageHidesHowToUseLines`.
Sửa test lỗi ngẫu nhiên `filterAndPages` (khoảng 1/4 lần chạy): trong test, menu vẽ lại trên luồng database nên cú bấm
có thể rơi vào lúc đang vẽ. Lỗi chỉ có trong test, server thật không bị. Mutation check: lịch sử chỉ Minh Chủ / Phó Bang,
thành viên bấm rút được, bỏ ẩn lore → test đều báo lỗi.

## 1.4.1 (2026-10-23): Quản lý bang hội (đã thay bằng 1.4.2)

- Menu `/clan`: nút **Quản lý bang hội** (bục giảng, cạnh phễu Sắp xếp) thay cho hai nút Kho và Lịch sử. Bấm vào mở menu
  3 dòng (`menus/clan-manage.yml`) gồm **Kho bang hội** và **Lịch sử bang hội**.
- Chỉ Minh Chủ và Phó Bang thấy và mở được Quản lý bang hội; thành viên thường thấy ô kính ở chỗ đó.
- Thành viên thường vẫn xem kho như trước qua nút Kho trong cửa hàng hoặc `/clan kho` (chỉ xem, không rút).
- Nút Quay lại của Kho và Lịch sử đưa Minh Chủ / Phó Bang về Quản lý bang hội; thành viên thường về `/clan`.
- `menus/clan-detail.yml` lên `text-version: 6` (bản cũ vào `old-configs/`). File mới `menus/clan-manage.yml`.

**Kiểm tra**: 166 test pass (1 test mới `manageMenuHoldsVaultAndHistory`, `HistoryMenuTest` đi qua nút mới). Mutation check:
hiện nút cho thành viên thường / bỏ kiểm tra quyền khi mở / Quay lại sai menu → test đều báo lỗi.

## 1.4.0 (2026-10-23): GUI lịch sử bang hội

Giai đoạn 5 (cuối) của `BOXCLANS_SHOP_SPEC.md` (§6.2, §8).

- Nút **Lịch sử** (G) ở menu `/clan`, hoặc `/clan lichsu`: chỉ Minh Chủ và Phó Bang mở được (thành viên thường không thấy
  nút, gõ lệnh thì bị báo). Admin xem lịch sử bang bất kỳ: `/clanadmin history <mã>`.
- Mỗi sự kiện là một tờ giấy: tên sự kiện, ai làm, chi tiết (vai trò cũ / mới, tên cũ / mới, icon, Minh Huy và lý do,
  vật phẩm và giá, boost và thời gian...), thời gian `dd/MM/yyyy HH:mm` (giờ Việt Nam). Mới nhất trước, 36 tờ mỗi trang.
- Tab lọc: Tất cả, Thành viên, Minh Huy, Cửa hàng, Kho, Boost, Bang hội.
- Mỗi trang được đọc từ database ở luồng riêng, không làm giật server dù lịch sử dài.
- Chữ của từng loại sự kiện nằm trong `menus/clan-history.yml` (`types.<LOẠI>`), sửa được.
- `INTEGRATION.md`: hướng dẫn cộng Minh Huy từ BoxKOTH (`rewards.yml`, chỉ ở `leader` hoặc `mvp`) và BoxQuest.
- `menus/clan-detail.yml` lên `text-version: 5`, `messages.yml` lên 4 (bản cũ vào `old-configs/`).

**Kiểm tra**: 165 test pass (4 test mới trong `HistoryMenuTest`). Test cũ đổi: kiểm tra `text-version` của clan-detail.

## 1.3.0 (2026-10-22): Cửa hàng, Kho, Icon bang hội, Boost bang hội

Giai đoạn 4 của `BOXCLANS_SHOP_SPEC.md` (§3, §4, §5). Cần BoxClans 1.2.0 trước đó; boost bang hội cần BoxCore 1.5.0
(bản có `ClanBoostEvent`, xem `BoxCore/boxcore-1.4.0-to-1.5.0.diff`).

**Icon bang hội** (Cài đặt > Icon bang hội, `/clan icons`)
- Khiên Bạc / Vàng / Tím có sẵn cho mọi bang (`default-owned: true` trong `icons.yml`); icon khác mua trong cửa hàng.
- Minh Chủ **và Phó Bang** trang bị / gỡ; thành viên chỉ xem. Icon chưa sở hữu không trang bị được.
- Giá mặc định trong `icons.yml` (300 đến 1500 Minh Huy) chỉ là gợi ý, hãy chỉnh lại.

**Cửa hàng bang hội**
- Tab: Tất cả + các danh mục trong `shop.yml` (mới; mặc định Icon, Boost, Key, Khác). Phân trang.
- Chỉ Minh Chủ / Phó Bang mua, mỗi lần 1 cái, luôn có màn hình xác nhận; lúc xác nhận mọi thứ được kiểm tra lại (giá
  đổi giữa chừng thì huỷ, số dư, giới hạn tuần, vai trò). Cả bang được báo, lịch sử ghi lại.
- Vật phẩm (trừ icon) thêm trong game: nút "Chỉnh sửa" (quyền `boxclans.admin.shop`) hoặc `/clanadmin shop ...`. Item
  được lưu nguyên vẹn (tên, lore, NBT). Mục "chạy lệnh" cho key ảo: item chỉ để trưng bày, rút ra thì chạy lệnh console.
- Giới hạn mua mỗi tuần cho từng mục, tính theo từng bang, làm mới 0h thứ Hai (giờ Việt Nam, chỉnh trong `shop.yml`).

**Kho bang hội** (`/clan kho`, nút K của menu chính)
- Chỉ chứa đồ mua trong cửa hàng hoặc admin cho. Gộp đồ giống hệt nhau, phân tab, phân trang. Cả bang xem được.
- Minh Chủ / Phó Bang rút 1 cái mỗi lần; túi đầy thì từ chối và kho không bị trừ. Mục "chạy lệnh" chạy lệnh cho người rút.
- Chai boost bang hội: chuột trái dùng luôn cho bang (thay boost cao hơn hoặc bị cắt ở 24h thì hỏi xác nhận; chai chỉ bị
  trừ khi boost thật sự thay đổi), chuột phải rút ra chai.
- Sửa / xoá mục trong cửa hàng không ảnh hưởng đồ đã nằm trong kho.

**Admin**: `/clanadmin shop`, `/clanadmin kho give|giveshop|view|remove`, `/clanadmin boost start|stop`
(`<clan|player> <mã|tên>`, như `minhhuy`).

**Lịch sử**: thêm mua, rút, dùng, admin cho / lấy khỏi kho, và mọi thay đổi boost bang hội (bật, cộng thêm, thay, dừng,
**hết hạn**), kể cả khi Minh Chủ dùng chai trên tay, nhờ sự kiện `ClanBoostEvent` mới của BoxCore.

**Dữ liệu**: bảng `shop_entries`, `shop_purchases`, `vault_items` tự tạo. Bang giải tán thì kho và bộ đếm bị xoá.

**Tự cập nhật file**: `messages.yml`, `icons.yml`, `menus/clan-shop.yml`, `settings.yml`, `clan-detail.yml`,
`confirm.yml` lên `text-version` mới (bản cũ vào `old-configs/`). File mới: `shop.yml`, `menus/shop-entry.yml`,
`menus/clan-vault.yml`, `menus/clan-icons.yml`.

**Kiểm tra**: 161 test pass. Test cũ thay đổi theo hành vi mới: `IconTest` (chọn icon ở menu Icon thay vì cửa hàng,
Phó Bang được đổi, icon chưa sở hữu bị từ chối), `OptionalDependenciesTest` (cho phép đúng 2 lớp hook import BoxCore),
`VietnameseUiTest` (`text-version` tăng). 17 test mới trong `ShopTest` và `IconTest`.

## 1.2.0 (2026-10-22): Minh Huy, lịch sử bang hội

Giai đoạn 3 của `BOXCLANS_SHOP_SPEC.md` (§2, §6.1). Cần BoxClans 1.1.0 trước đó (đã có `text-version`).

**Minh Huy (tiền tệ chung của bang hội)**
- Mỗi bang có một số dư Minh Huy (0 đến 1.000.000.000.000). Không người chơi nào sở hữu, không donate.
- Lệnh `/clanadmin minhhuy give|add|take|set <clan|player> <mã|tên> <số> [lý do] [-s]`,
  `reset clan <mã>`, `reset all confirm`, `log <clan|player> <mã|tên> [trang]`. Đích luôn ghi rõ `clan` hoặc `player`;
  `player <tên>` = bang của người đó, không có bang thì báo lỗi. Trừ quá số dư thì báo lỗi và không trừ. Số nhập được
  dạng `1.250`.
- Thành viên online được báo khi bang nhận / bị trừ (trừ khi `-s`); `set` / `reset` không báo.
- API `ClanCurrency` (`balance`, `give`, `giveClan`, `take`), kết quả `CurrencyResult`, sự kiện `ClanMinhHuyChangeEvent`.
- Placeholder `%boxclans_minhhuy%`, `%boxclans_minhhuy_formatted%` (chỉ bang của chính người chơi).
- Menu `/clan`: ô thông tin có dòng "Minh Huy" (`menus/clan-detail.yml` lên `text-version: 3`, bản cũ tự chuyển vào
  `old-configs/v2/`).

**Lịch sử bang hội** (chưa có GUI, GUI ở 1.4.0)
- Bảng mới `clan_history`. Ghi: tạo bang, vào, rời, bị kick (ai kick), bị kick do vượt giới hạn, đổi vai trò, chuyển
  Minh Chủ, đổi tên (cũ và mới), đổi icon, bật / tắt PvP, mọi thay đổi Minh Huy (lý do, ai làm). Tên người chơi được lưu
  lúc ghi.
- Tự xoá bản ghi cũ hơn `history.keep-days` (mới trong `config.yml`, mặc định 30) khi khởi động và mỗi giờ. Bang giải tán
  thì lịch sử của bang bị xoá.

**Dữ liệu**: cột `clans.minh_huy` và bảng `clan_history` tự thêm khi khởi động, dữ liệu cũ giữ nguyên.

**Kiểm tra**: 144 test pass (129 cũ + 15 mới trong `MinhHuyTest`, `HistoryTest`). Test cũ đổi duy nhất: kiểm tra
`text-version` cho phép số lớn hơn 2.

## 1.1.0 (2026-10-22): Việt hoá, Danh Vọng, dọn giao diện

Giai đoạn 1 của `BOXCLANS_SHOP_SPEC.md` (§1). Không thêm tính năng mới, dữ liệu (`clans.db`) không đổi.

**Chữ hiển thị**
- Mọi menu, tin nhắn và tên icon: bỏ small caps, viết tiếng Việt có dấu. Bỏ ký hiệu trang trí (✔ ✘ ✓ ✗ ➟ ∞).
- "Điểm clan" / Score / Points hiện là **Danh Vọng**. Lệnh `/clanadmin points`, `points.yml`,
  `%boxclans_points%` và API giữ nguyên tên.
- Thêm `/clanadmin danhvong`: cùng một lệnh với `/clanadmin points`.
- "Mã lệnh" đổi thành **Mã bang hội** (màu `&b`). Mã chỉ hiện trong nội bộ bang hội (menu `/clan`, `/clan info` của
  chính mình, tin nhắn tạo / đổi tên). Danh sách bang hội, xem bang hội, bảng xếp hạng, menu lời mời, `/clan list`,
  `/clan top` không còn hiện mã.
- Lời mời vào bang hội hiện tên bang có màu. Nút [Chấp nhận] / [Từ chối] vẫn chạy `/clan accept <mã>` ngầm.
- Lời nhắc `/clan create` và `/clan rename`: bỏ dòng ví dụ và "(không dùng gradient)", đổi thành "Các mã màu được
  hỗ trợ", thêm dòng "Tham khảo mã màu tại: https://www.birdflop.com/resources/rgb/" bấm vào để mở.
- Chat bang hội `Bang Hội |`, spy `Giám sát |`. Màu vai trò trong chat giữ nguyên.
- Màu riêng cho các giá trị: thành viên `X/X` xanh lá (đỏ khi đầy), Danh Vọng `#FFC94D`, hạng `#FFD600`.
- `roles.yml` có thêm `gui-color`: màu vai trò trong menu (Minh Chủ `#FF5C5C`, Phó Bang `#5CD6FF`, Thành Viên
  `#7CFC7C`). Chat bang hội và placeholder vẫn dùng `color`.

**Thay đổi khác**
- Tên bang hội không bắt đầu bằng mã màu hiện màu `&6`, áp dụng cả bang hội cũ (tính lúc hiển thị, không sửa dữ
  liệu, mã bang hội không đổi).
- Menu `/clan` chỉ có 1 trang (45 ô), bỏ nút trang trước / sau.

**Cập nhật file trên server (tự động)**
- `messages.yml`, `icons.yml` và mọi `menus/*.yml` có khoá mới `text-version: 2`. Khi khởi động (hoặc
  `/clanadmin reload`), file trên server có `text-version` thấp hơn (file 1.0.0 không có khoá này) được chuyển vào
  `plugins/BoxClans/old-configs/v1/` và thay bằng bản mới. File cũ không bị xoá. Console ghi
  `[messages.yml] texts updated (text-version 1 -> 2), old copy: old-configs/v1/messages.yml`.
- `config.yml` và `roles.yml` không bị thay: nếu `chat.clan-format` / `chat.spy-format` còn đúng mặc định cũ (chữ
  ᴄʟᴀɴ / ꜱᴘʏ) thì plugin tự dùng định dạng mới; `gui-color` thiếu thì lấy giá trị trong jar.

**Kiểm tra**
- 129 test pass (117 test cũ + 12 test mới trong `VietnameseUiTest`).
- Test cũ chỉ sửa phần **chữ được so sánh** (tiêu đề menu, tin nhắn đã dịch), không sửa logic: `CombatTest`,
  `CommandsTest`, `GuiTest`, `IconTest`. `FakeServer` đăng ký lệnh điểm qua `registerPoints` (giống plugin thật).
