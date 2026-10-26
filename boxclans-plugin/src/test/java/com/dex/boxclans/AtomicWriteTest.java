package com.dex.boxclans;

import com.dex.boxclans.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 1.4.4: writes queued inside {@link Database#atomically} are committed together or not at all. */
class AtomicWriteTest {

    @TempDir
    File dir;
    Database db;
    final UUID clan = UUID.randomUUID();

    @BeforeEach
    void open() {
        db = Database.open(new File(dir, "clans.db"), Logger.getAnonymousLogger());
        assertNotNull(db);
    }

    @AfterEach
    void close() {
        db.close();
    }

    private void row(String detail) {
        db.addHistory(new Database.HistoryRow(0, clan, System.currentTimeMillis(), "SHOP_BUY", null, "Alice", null, "",
                1, detail));
    }

    private int rows() throws Exception {
        return db.historyPage(clan, List.of(), 0, 100).get(5, TimeUnit.SECONDS).total();
    }

    private void fail() {
        db.write("broken", c -> {
            throw new SQLException("disk full");
        });
    }

    @Test
    void aFailingWriteUndoesTheWholeGroup() throws Exception {
        db.atomically("purchase", () -> {
            row("paid");
            fail();
            row("delivered");
        });
        assertEquals(0, rows(), "nothing half-written");
    }

    @Test
    void outsideAGroupTheOtherWritesStay() throws Exception {
        row("a");
        fail();
        row("b");
        assertEquals(2, rows());
    }

    @Test
    void aGroupCommitsEverythingAndNestedGroupsJoin() throws Exception {
        db.atomically("outer", () -> {
            row("1");
            db.atomically("inner", () -> row("2"));
            row("3");
        });
        db.atomically("empty", () -> { });
        assertEquals(3, rows());
    }
}
