package xyz.winhok.nettap.ui.data;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CaptureSessionStoreTest {
    @Test
    public void freezeDropsNewEventsAndResumeAcceptsOnlyFutureEvents() {
        CaptureSessionStore store = new CaptureSessionStore(10, 1024 * 1024);
        CaptureUiEvent first = event("1", "https://a.example/one", 200);
        CaptureUiEvent frozen = event("2", "https://a.example/two", 200);
        CaptureUiEvent resumed = event("3", "https://a.example/three", 200);

        assertTrue(store.addFromRealtime(first));
        store.freeze();
        assertFalse(store.addFromRealtime(frozen));
        store.resume();
        assertTrue(store.addFromRealtime(resumed));

        List<CaptureUiEvent> sequence = store.sequenceOldestFirst();
        assertEquals(2, sequence.size());
        assertEquals("1", sequence.get(0).getId());
        assertEquals("3", sequence.get(1).getId());
    }

    @Test
    public void countEvictionRebuildsDomainBuckets() {
        CaptureSessionStore store = new CaptureSessionStore(2, 1024 * 1024);

        store.addFromRealtime(event("1", "https://old.example/path", 200));
        store.addFromRealtime(event("2", "https://new.example/path", 404));
        store.addFromRealtime(event("3", "https://new.example/next", 500));

        assertEquals(2, store.sequenceOldestFirst().size());
        assertEquals(1, store.domainBuckets().size());
        DomainBucket bucket = store.domainBuckets().get(0);
        assertEquals("new.example", bucket.getHost());
        assertEquals(2, bucket.getCount());
        assertEquals(1, bucket.getStatus5xxCount());
        assertEquals(1, bucket.getStatus4xxCount());
    }

    @Test
    public void specDefaultCountEvictionTrimsTwentyFiveHundredEventsToTwoThousand() {
        CaptureSessionStore store = new CaptureSessionStore(2000, 100 * 1024 * 1024L);

        for (int i = 0; i < 2500; i++) {
            store.addFromRealtime(event("id-" + i, "https://api.example/path/" + i, 200));
        }

        List<CaptureUiEvent> sequence = store.sequenceOldestFirst();
        assertEquals(2000, sequence.size());
        assertEquals("id-500", sequence.get(0).getId());
        assertEquals("id-2499", sequence.get(sequence.size() - 1).getId());
        assertEquals(1, store.domainBuckets().size());
        assertEquals(2000, store.domainBuckets().get(0).getCount());
    }

    @Test
    public void memoryEvictionRemovesOldestEventsAndReadState() {
        CaptureUiEvent oldEvent = event("old", "https://old.example/path", 200);
        CaptureUiEvent newEvent = event("new", "https://new.example/path", 200);
        long oneEventBudget = Math.max(1024L, newEvent.getApproximateBytes());
        CaptureSessionStore store = new CaptureSessionStore(10, oneEventBudget);

        store.addFromRealtime(oldEvent);
        store.markRead("old");
        store.addFromRealtime(newEvent);

        assertEquals(1, store.sequenceOldestFirst().size());
        assertEquals("new", store.sequenceOldestFirst().get(0).getId());
        assertFalse(store.isRead("old"));
        assertEquals(1, store.domainBuckets().size());
        assertEquals("new.example", store.domainBuckets().get(0).getHost());
    }

    @Test
    public void clearRemovesSessionAndDomainState() {
        CaptureSessionStore store = new CaptureSessionStore(10, 1024 * 1024);
        store.addFromRealtime(event("1", "https://example.com", 200));
        store.markRead("1");

        store.clear();

        assertTrue(store.sequenceOldestFirst().isEmpty());
        assertTrue(store.domainBuckets().isEmpty());
        assertFalse(store.isRead("1"));
        assertEquals(CaptureSessionStore.State.CLEARED, store.getState());
    }

    @Test
    public void readStateIsSessionLocal() {
        CaptureSessionStore store = new CaptureSessionStore(10, 1024 * 1024);
        store.addFromRealtime(event("1", "https://example.com", 200));

        store.markRead("1");

        assertTrue(store.isRead("1"));
        assertFalse(store.isRead("2"));
    }

    @Test
    public void clearHostBucketRemovesOnlyMatchingEventsAndReadState() {
        CaptureSessionStore store = new CaptureSessionStore(10, 1024 * 1024);
        store.addFromRealtime(event("old", "https://old.example/path", 200));
        store.addFromRealtime(event("new", "https://new.example/path", 200));
        store.markRead("old");
        store.markRead("new");

        int removed = store.clearHostBucket("old.example");

        assertEquals(1, removed);
        assertEquals(1, store.sequenceOldestFirst().size());
        assertEquals("new", store.sequenceOldestFirst().get(0).getId());
        assertFalse(store.isRead("old"));
        assertTrue(store.isRead("new"));
        assertEquals(1, store.domainBuckets().size());
        assertEquals("new.example", store.domainBuckets().get(0).getHost());
    }

    @Test
    public void invalidUrlEventsAreGroupedIntoInvalidUrlBucket() {
        CaptureSessionStore store = new CaptureSessionStore(10, 1024 * 1024);

        store.addFromRealtime(event("invalid", "", 0));

        assertEquals(1, store.domainBuckets().size());
        DomainBucket bucket = store.domainBuckets().get(0);
        assertEquals(CaptureUiEvent.INVALID_HOST, bucket.getHost());
        assertEquals("(invalid URL)", bucket.getLabel());
        assertEquals(1, bucket.getErrorCount());
    }

    private static CaptureUiEvent event(String id, String url, int status) {
        return CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(url, status, null)
                .replace("\"request-1\"", "\"" + id + "\""));
    }
}
