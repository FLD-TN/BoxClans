package com.dex.boxclans;

import com.dex.boxclans.FakeServer.FakePlayer;
import com.dex.boxclans.api.ClanCurrency;
import com.dex.boxclans.api.ClanCurrencyImpl;
import com.dex.boxclans.api.CurrencyResult;
import com.dex.boxclans.api.event.ClanMinhHuyChangeEvent;
import com.dex.boxclans.clan.ClanData;
import com.dex.boxclans.hook.PlaceholderValues;
import com.dex.boxclans.history.HistoryType;
import com.dex.boxclans.storage.Database;
import com.dex.boxclans.text.ClanNameFormatter;
import com.dex.boxclans.util.Numbers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.2.0 (BOXCLANS_SHOP_SPEC.md §2): Minh Huy commands, API, placeholders, storage and history. */
class MinhHuyTest {

    @TempDir
    File dir;
    FakeServer s;
    FakePlayer alice;
    FakePlayer bob;
    FakePlayer carl;
    FakePlayer eve;
    ClanData alpha;
    ClanData beta;

    @BeforeEach
    void setUp() {
        s = new FakeServer(dir);
        alice = s.join("Alice");
        bob = s.join("Bob");
        carl = s.join("Carl");
        eve = s.join("Eve");
        alpha = create(alice, "Alpha");
        beta = create(carl, "Beta");
        s.clan(carl, "invite", "Bob");
        s.clan(bob, "accept");
    }

    @AfterEach
    void tearDown() {
        s.close();
    }

    private ClanData create(FakePlayer p, String name) {
        s.clan(p, "create");
        s.type(p, name);
        p.click('C');
        return s.manager.of(p.id);
    }

    private void mh(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "minhhuy";
        System.arraycopy(args, 0, all, 1, args.length);
        s.admin(s.console, all);
    }

    private boolean console(String fragment) {
        return FakeServer.CONSOLE.stream().anyMatch(m -> m.contains(fragment));
    }

    private List<Database.HistoryRow> history(ClanData clan, HistoryType... types) throws Exception {
        List<String> names = new ArrayList<>();
        for (HistoryType t : types) names.add(t.key());
        return s.db.historyPage(clan.uuid(), names, 0, 100).get(5, TimeUnit.SECONDS).rows();
    }

    @Test
    void giveByPlayerOrClanTellsTheClan() throws Exception {
        mh("give", "player", "Bob", "300", "KOTH", "top", "1");
        assertEquals(300, beta.minhHuy(), "Bob is in Beta");
        assertEquals(0, alpha.minhHuy());
        assertTrue(carl.got("Bang hội nhận +300 Minh Huy (KOTH top 1). Số dư: 300"), carl.messages.toString());
        assertTrue(bob.got("+300 Minh Huy"));
        assertTrue(console("Đã cộng 300 Minh Huy cho Beta (KOTH top 1). Số dư: 300"), FakeServer.CONSOLE.toString());

        mh("add", "clan", "alpha", "1.250", "-s");
        assertEquals(1250, alpha.minhHuy(), "the key is case-insensitive, 1.250 = 1250");
        assertFalse(alice.got("Minh Huy"), "-s: the clan is not told");

        List<Database.HistoryRow> rows = history(alpha, HistoryType.MINHHUY_ADD);
        assertEquals(1, rows.size());
        assertEquals(1250, rows.get(0).amount());
        assertEquals("console", rows.get(0).actorName());
        assertEquals("Quản trị viên", rows.get(0).detail(), "no reason: the default one");
        assertTrue(FakeServer.EVENTS.stream().anyMatch(e -> e instanceof ClanMinhHuyChangeEvent m && m.delta() == 1250
                && m.balance() == 1250 && m.clan().uuid().equals(alpha.uuid())));
    }

    @Test
    void theTargetKindIsNeverGuessed() {
        // a clan whose key equals a player name
        FakePlayer zed = s.join("Zed");
        ClanData bobClan = create(zed, "Bob");
        assertEquals("Bob", bobClan.key());
        mh("give", "player", "Bob", "10");
        assertEquals(10, beta.minhHuy(), "player Bob -> Bob's clan");
        assertEquals(0, bobClan.minhHuy());
        mh("give", "clan", "Bob", "20");
        assertEquals(20, bobClan.minhHuy(), "clan Bob -> the clan with key Bob");
        assertEquals(10, beta.minhHuy());

        FakeServer.CONSOLE.clear();
        mh("give", "Bob", "10", "5");
        assertTrue(console("Phải ghi rõ clan <mã bang hội> hoặc player <tên người chơi>"), FakeServer.CONSOLE.toString());
        mh("give", "player", "Eve", "10");
        assertTrue(console("Người chơi Eve không có bang hội"));
        mh("give", "player", "Nobody", "10");
        assertTrue(console("Không tìm thấy người chơi Nobody"));
        mh("give", "clan", "Nope", "10");
        assertTrue(console("Không tìm thấy bang hội Nope"));
        assertEquals(10, beta.minhHuy());
    }

    @Test
    void takeMoreThanTheBalanceIsRefused() {
        mh("give", "clan", "Alpha", "100", "-s");
        FakeServer.CONSOLE.clear();
        mh("take", "clan", "Alpha", "150");
        assertEquals(100, alpha.minhHuy(), "nothing removed");
        assertTrue(console("Alpha chỉ có 100 Minh Huy, không đủ để trừ 150. Không trừ gì."), FakeServer.CONSOLE.toString());
        mh("take", "clan", "Alpha", "40", "Phạt");
        assertEquals(60, alpha.minhHuy());
        assertTrue(alice.got("Bang hội bị trừ 40 Minh Huy (Phạt). Số dư: 60"), alice.messages.toString());
    }

    @Test
    void invalidAmountsAndTheLimit() {
        for (String bad : List.of("0", "-5", "abc", "1,5", "")) {
            FakeServer.CONSOLE.clear();
            mh("give", "clan", "Alpha", bad);
            assertEquals(0, alpha.minhHuy(), bad);
        }
        mh("set", "clan", "Alpha", String.valueOf(ClanCurrency.MAX_BALANCE));
        assertEquals(ClanCurrency.MAX_BALANCE, alpha.minhHuy());
        FakeServer.CONSOLE.clear();
        mh("give", "clan", "Alpha", "1");
        assertEquals(ClanCurrency.MAX_BALANCE, alpha.minhHuy());
        assertTrue(console("sẽ vượt mức tối đa"));
        mh("set", "clan", "Alpha", "0");
        assertEquals(0, alpha.minhHuy(), "set 0 is allowed");
    }

    @Test
    void setResetAndResetAll() throws Exception {
        mh("set", "clan", "Alpha", "500", "Bù");
        assertEquals(500, alpha.minhHuy());
        assertFalse(alice.got("Minh Huy"), "set does not tell the clan");
        mh("give", "clan", "Beta", "70", "-s");
        mh("reset", "clan", "Alpha");
        assertEquals(0, alpha.minhHuy());
        mh("reset", "all");
        assertEquals(70, beta.minhHuy(), "reset all needs confirm");
        mh("reset", "all", "confirm");
        assertEquals(0, beta.minhHuy());
        List<Database.HistoryRow> rows = history(alpha, HistoryType.MINHHUY_SET, HistoryType.MINHHUY_RESET);
        assertEquals(List.of("MINHHUY_RESET", "MINHHUY_SET"), rows.stream().map(Database.HistoryRow::type).toList());
        assertEquals(500, rows.get(0).amount(), "reset keeps the old balance");
    }

    @Test
    void logShowsTheMinhHuyHistory() throws Exception {
        mh("give", "clan", "Alpha", "300", "KOTH", "top", "1", "-s");
        mh("take", "clan", "Alpha", "100", "Phạt", "-s");
        FakeServer.CONSOLE.clear();
        mh("log", "clan", "Alpha");
        long deadline = System.currentTimeMillis() + 5000;
        while (!console("KOTH top 1") && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertTrue(console("Sổ Minh Huy của Alpha (số dư 200, trang 1/1, 2 dòng)"), FakeServer.CONSOLE.toString());
        assertTrue(console("-100 Phạt bởi console"), FakeServer.CONSOLE.toString());
        assertTrue(console("+300 KOTH top 1 bởi console"));
    }

    @Test
    void apiGiveTakeAndThreads() {
        List<Runnable> queued = new ArrayList<>();
        boolean[] main = {true};
        ClanCurrencyImpl api = new ClanCurrencyImpl(() -> s.minhHuy, s.index, () -> main[0], queued::add, FakeServer.LOG);
        CurrencyResult r = api.give(bob.id, 50, "Event");
        assertTrue(r.success());
        assertEquals(50, r.balance());
        assertEquals(50, api.balance(beta.uuid()));
        assertTrue(carl.got("+50 Minh Huy (Event)"), "the API tells the clan");
        assertEquals(CurrencyResult.Status.NO_CLAN, api.give(eve.id, 50, "x").status());
        assertEquals(CurrencyResult.Status.NOT_ENOUGH, api.take(beta.uuid(), 51, "x").status());
        assertEquals(CurrencyResult.Status.INVALID_AMOUNT, api.giveClan(alpha.uuid(), 0, "x").status());
        assertEquals(30, api.take(beta.uuid(), 20, "Shop").balance());

        main[0] = false;
        assertEquals(CurrencyResult.Status.QUEUED, api.giveClan(alpha.uuid(), 5, "async").status());
        assertEquals(0, alpha.minhHuy(), "not yet");
        assertEquals(1, queued.size());
        queued.get(0).run();
        assertEquals(5, alpha.minhHuy(), "applied on the main thread");
        assertEquals(5, api.balance(alpha.uuid()), "balance is readable from any thread");
    }

    @Test
    void placeholdersOnlyForTheOwnClan() {
        mh("set", "clan", "Alpha", "1250");
        PlaceholderValues v = new PlaceholderValues(s.plugin);
        assertEquals("1250", v.resolve(alice.id, "minhhuy"));
        assertEquals("1.250", v.resolve(alice.id, "minhhuy_formatted"));
        assertEquals("", v.resolve(eve.id, "minhhuy"), "no clan: none-value");
        assertEquals(null, v.resolve(null, "top_1_minhhuy"), "never for other clans");
        assertEquals(null, v.resolve(null, "clan_Alpha_minhhuy"));
    }

    @Test
    void balanceIsStoredAndReloaded() throws Exception {
        mh("give", "clan", "Alpha", "777", "-s");
        List<ClanData> loaded = s.db.loadClans(ClanNameFormatter::render);
        assertEquals(777, loaded.stream().filter(c -> c.uuid().equals(alpha.uuid())).findFirst().orElseThrow().minhHuy());
    }

    @Test
    void groupedNumbers() {
        assertEquals("0", Numbers.grouped(0));
        assertEquals("999", Numbers.grouped(999));
        assertEquals("1.250", Numbers.grouped(1250));
        assertEquals("1.000.000.000.000", Numbers.grouped(1_000_000_000_000L));
        assertEquals("-3.000", Numbers.grouped(-3000));
        assertEquals(1250, Numbers.parsePositive("1.250"));
        assertEquals(-1, Numbers.parsePositive("1,5"));
        assertEquals(-1, Numbers.parsePositive("-1"));
    }
}
