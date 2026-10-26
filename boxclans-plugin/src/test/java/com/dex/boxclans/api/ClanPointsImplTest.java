package com.dex.boxclans.api;

import com.dex.boxclans.points.PointsService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClanPointsImplTest {

    @Test
    void offMainThreadCallsAreQueued() {
        PointsService service = mock(PointsService.class);
        PointResult ok = new PointResult(PointResult.Status.SUCCESS, 50, 50);
        when(service.awardClan(any(), anyString(), any(), anyString(), anyBoolean())).thenReturn(ok);
        AtomicBoolean main = new AtomicBoolean(false);
        List<Runnable> queue = new ArrayList<>();
        ClanPointsImpl api = new ClanPointsImpl(() -> service, main::get, queue::add, Logger.getLogger("t"));
        UUID clan = UUID.randomUUID();

        assertSame(PointResult.QUEUED, api.awardClan(clan, "koth-top1"));
        verify(service, never()).awardClan(any(), anyString(), any(), anyString(), anyBoolean());
        assertEquals(1, queue.size());
        queue.get(0).run();
        verify(service).awardClan(eq(clan), eq("koth-top1"), eq(null), eq("api"), eq(false));

        main.set(true);
        assertSame(ok, api.awardClan(clan, "koth-top1"));
        assertSame(PointResult.NO_CLAN, api.award(null, "kill"));
        assertFalse(api.startKillEvent("loan-chien", java.time.Duration.ofMinutes(5)), "no kill event control yet");
    }

    @Test
    void neverThrows() {
        PointsService service = mock(PointsService.class);
        when(service.award(any(), anyString(), any(), anyString(), anyBoolean())).thenThrow(new IllegalStateException("boom"));
        ClanPointsImpl api = new ClanPointsImpl(() -> service, () -> true, Runnable::run, Logger.getLogger("t"));
        assertSame(PointResult.NO_CLAN, api.award(UUID.randomUUID(), "kill"));
    }
}
