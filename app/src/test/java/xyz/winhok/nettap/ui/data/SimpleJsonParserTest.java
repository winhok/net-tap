package xyz.winhok.nettap.ui.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class SimpleJsonParserTest {
    @Test
    public void parsesNestedObjectsArraysEscapesBooleansNullAndNumbers() {
        Object value = SimpleJsonParser.parseObjectOrArray(
                " { \"text\":\"a\\n\\u0042\\\\\\\"\","
                        + "\"flag\":true,\"off\":false,\"none\":null,"
                        + "\"items\":[1,-2,3.5,6e2,{\"k\":\"v\"}] } ");

        Map<String, Object> map = SimpleJsonParser.castMap(value);
        assertEquals("a\nB\\\"", map.get("text"));
        assertEquals(Boolean.TRUE, map.get("flag"));
        assertEquals(Boolean.FALSE, map.get("off"));
        assertEquals(null, map.get("none"));
        List<?> items = (List<?>) map.get("items");
        assertEquals(Long.valueOf(1), items.get(0));
        assertEquals(Long.valueOf(-2), items.get(1));
        assertEquals(Double.valueOf(3.5d), items.get(2));
        assertEquals(Double.valueOf(600d), items.get(3));
        assertEquals("v", SimpleJsonParser.castMap(items.get(4)).get("k"));
    }

    @Test
    public void parsesEmptyObjectAndArrayRoots() {
        assertTrue(SimpleJsonParser.parseObject("{}").isEmpty());
        assertTrue(((List<?>) SimpleJsonParser.parseObjectOrArray("[]")).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseObjectRejectsArrayRoot() {
        SimpleJsonParser.parseObject("[]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseObjectOrArrayRejectsScalarRoot() {
        SimpleJsonParser.parseObjectOrArray("true");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTrailingContent() {
        SimpleJsonParser.parseObject("{} false");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidEscape() {
        SimpleJsonParser.parseObject("{\"x\":\"\\q\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsShortUnicodeEscape() {
        SimpleJsonParser.parseObject("{\"x\":\"\\u12\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidUnicodeEscape() {
        SimpleJsonParser.parseObject("{\"x\":\"\\u12zz\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidNumber() {
        SimpleJsonParser.parseObject("{\"x\":-}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFractionWithoutDigits() {
        SimpleJsonParser.parseObject("{\"x\":1.}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsExponentWithoutDigits() {
        SimpleJsonParser.parseObject("{\"x\":1e}");
    }
}
