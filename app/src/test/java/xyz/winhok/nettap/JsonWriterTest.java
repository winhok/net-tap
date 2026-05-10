package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JsonWriterTest {
    @Test
    public void escapesJsonStrings() {
        assertEquals("\"a\\n\\\"b\\\"\"", JsonWriter.string("a\n\"b\""));
    }
}
