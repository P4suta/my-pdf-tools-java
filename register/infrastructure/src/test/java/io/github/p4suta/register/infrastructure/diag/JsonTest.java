package io.github.p4suta.register.infrastructure.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void escapesSpecialCharacters() {
        assertEquals("{\"k\":\"a\\\"b\"}", new Json().field("k", "a\"b").end());
        assertEquals("{\"k\":\"a\\\\b\"}", new Json().field("k", "a\\b").end());
        assertEquals("{\"k\":\"a\\nb\"}", new Json().field("k", "a\nb").end());
    }

    @Test
    void formatsNumbersBooleansAndNull() {
        assertEquals(
                "{\"a\":2,\"b\":1.5,\"c\":42,\"d\":true,\"e\":null}",
                new Json()
                        .field("a", 2.0)
                        .field("b", 1.5)
                        .field("c", 42L)
                        .field("d", true)
                        .field("e", (String) null)
                        .end());
    }

    @Test
    void nonFiniteNumbersBecomeNull() {
        assertEquals("{\"x\":null}", new Json().field("x", Double.NaN).end());
        assertEquals("{\"x\":null}", new Json().field("x", Double.POSITIVE_INFINITY).end());
    }

    @Test
    void rawFragmentsAndNullRaw() {
        assertEquals(
                "{\"obj\":{\"k\":1},\"none\":null}",
                new Json().fieldRaw("obj", "{\"k\":1}").fieldRaw("none", null).end());
    }
}
