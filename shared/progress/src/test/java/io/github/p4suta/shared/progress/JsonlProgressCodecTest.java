package io.github.p4suta.shared.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class JsonlProgressCodecTest {

    // --- wire format (locked exact strings) ---

    @Test
    void writesRunStarted() {
        assertThat(JsonlProgressCodec.write(new ProgressEvent.RunStarted(4)))
                .isEqualTo("{\"type\":\"runStarted\",\"stageCount\":4}");
    }

    @Test
    void writesStageStarted() {
        assertThat(JsonlProgressCodec.write(new ProgressEvent.StageStarted("despeckle", 1, 4)))
                .isEqualTo(
                        "{\"type\":\"stageStarted\",\"stage\":\"despeckle\",\"index\":1,"
                                + "\"stageCount\":4}");
    }

    @Test
    void writesPageProcessed() {
        assertThat(JsonlProgressCodec.write(new ProgressEvent.PageProcessed("register", 12, 300)))
                .isEqualTo(
                        "{\"type\":\"pageProcessed\",\"stage\":\"register\",\"done\":12,"
                                + "\"total\":300}");
    }

    @Test
    void writesStageCompleted() {
        assertThat(JsonlProgressCodec.write(new ProgressEvent.StageCompleted("spread")))
                .isEqualTo("{\"type\":\"stageCompleted\",\"stage\":\"spread\"}");
    }

    @Test
    void writesRunCompleted() {
        assertThat(JsonlProgressCodec.write(new ProgressEvent.RunCompleted()))
                .isEqualTo("{\"type\":\"runCompleted\"}");
    }

    @Test
    void writesRunFailed() {
        assertThat(JsonlProgressCodec.write(new ProgressEvent.RunFailed("IO", "boom")))
                .isEqualTo("{\"type\":\"runFailed\",\"kind\":\"IO\",\"message\":\"boom\"}");
    }

    @Test
    void escapesEveryReservedCharacterWhenWriting() {
        // quote, backslash, newline, carriage return, tab, then two other control chars (NUL, 0x1f)
        // that fall through to the six-character hex-escape form.
        String message = "a\"b\\c\nd\re\tf" + (char) 0x00 + "g" + (char) 0x1f + "h";
        assertThat(JsonlProgressCodec.write(new ProgressEvent.RunFailed("IO", message)))
                .isEqualTo(
                        "{\"type\":\"runFailed\",\"kind\":\"IO\",\"message\":"
                                + "\"a\\\"b\\\\c\\nd\\re\\tf\\u0000g\\u001fh\"}");
    }

    // --- round trip ---

    @Property
    void roundTripsEveryEvent(@ForAll("events") ProgressEvent event) {
        assertThat(JsonlProgressCodec.read(JsonlProgressCodec.write(event))).isEqualTo(event);
    }

    @Test
    void readsBackControlCharactersAndUnicode() {
        ProgressEvent event = new ProgressEvent.RunFailed("PARSE", "改行\nタブ\tとベル — \"引用\"");
        assertThat(JsonlProgressCodec.read(JsonlProgressCodec.write(event))).isEqualTo(event);
    }

    @Test
    void readsBackslashEscapesTheWriterNeverEmits() {
        // \b \f \/ are valid JSON escapes the writer never emits; the reader still accepts them.
        ProgressEvent decoded =
                JsonlProgressCodec.read(
                        "{\"type\":\"runFailed\",\"kind\":\"x\",\"message\":\"\\b\\f\\/\"}");
        assertThat(decoded).isEqualTo(new ProgressEvent.RunFailed("x", "\b\f/"));
    }

    @Test
    void toleratesInsignificantWhitespace() {
        ProgressEvent decoded =
                JsonlProgressCodec.read("  { \"type\" : \"stageCompleted\" , \"stage\" : \"x\" } ");
        assertThat(decoded).isEqualTo(new ProgressEvent.StageCompleted("x"));
    }

    @Test
    void readsEmptyObjectButRejectsTheMissingType() {
        assertThatThrownBy(() -> JsonlProgressCodec.read("{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing or non-string field: type");
    }

    // --- malformed input (every reader error arm) ---

    @Test
    void rejectsUnknownEventType() {
        assertThatThrownBy(() -> JsonlProgressCodec.read("{\"type\":\"nope\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown progress event type: nope");
    }

    @Test
    void rejectsNonStringWhereStringExpected() {
        assertThatThrownBy(
                        () -> JsonlProgressCodec.read("{\"type\":\"stageCompleted\",\"stage\":7}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing or non-string field: stage");
    }

    @Test
    void rejectsNonIntegerWhereIntegerExpected() {
        assertThatThrownBy(
                        () ->
                                JsonlProgressCodec.read(
                                        "{\"type\":\"runStarted\",\"stageCount\":\"4\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing or non-integer field: stageCount");
    }

    @Test
    void rejectsNegativeCountAfterParsing() {
        // The number parses (exercising the sign branch); the record then rejects it.
        assertThatThrownBy(
                        () ->
                                JsonlProgressCodec.read(
                                        "{\"type\":\"runStarted\",\"stageCount\":-1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stageCount must be non-negative: -1");
    }

    @Test
    void rejectsAStrayMinusSign() {
        assertThatThrownBy(
                        () -> JsonlProgressCodec.read("{\"type\":\"runStarted\",\"stageCount\":-}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid number");
    }

    @Test
    void rejectsNumberOutOfIntRange() {
        assertThatThrownBy(
                        () ->
                                JsonlProgressCodec.read(
                                        "{\"type\":\"runStarted\",\"stageCount\":99999999999}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("number out of range");
    }

    @Test
    void rejectsAValueThatIsNeitherStringNorInteger() {
        assertThatThrownBy(() -> JsonlProgressCodec.read("{\"type\":true}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected a string or integer value");
    }

    @Test
    void rejectsUnterminatedString() {
        assertThatThrownBy(() -> JsonlProgressCodec.read("{\"type\":\"runComplete"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unterminated string");
    }

    @Test
    void rejectsDanglingEscape() {
        assertThatThrownBy(() -> JsonlProgressCodec.read("{\"type\":\"x\\"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dangling escape");
    }

    @Test
    void rejectsInvalidEscape() {
        assertThatThrownBy(
                        () ->
                                JsonlProgressCodec.read(
                                        "{\"type\":\"runFailed\",\"kind\":\"x\","
                                                + "\"message\":\"\\x\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid escape");
    }

    @Test
    void rejectsTruncatedUnicodeEscape() {
        assertThatThrownBy(() -> JsonlProgressCodec.read("{\"type\":\"x\\u12\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("truncated \\u escape");
    }

    @Test
    void rejectsInvalidUnicodeEscape() {
        assertThatThrownBy(
                        () ->
                                JsonlProgressCodec.read(
                                        "{\"type\":\"runFailed\",\"kind\":\"x\","
                                                + "\"message\":\"\\uZZZZ\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid \\u escape");
    }

    @Test
    void rejectsAMissingOpeningBrace() {
        assertThatThrownBy(() -> JsonlProgressCodec.read("\"type\":\"x\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected '{'");
    }

    @Test
    void rejectsAMissingColon() {
        assertThatThrownBy(() -> JsonlProgressCodec.read("{\"type\" \"x\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected ':'");
    }

    @Test
    void rejectsAMissingCommaBetweenFields() {
        assertThatThrownBy(
                        () ->
                                JsonlProgressCodec.read(
                                        "{\"type\":\"stageCompleted\" \"stage\":\"x\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected ',' or '}'");
    }

    @Test
    void rejectsTrailingCharactersAfterTheObject() {
        assertThatThrownBy(() -> JsonlProgressCodec.read("{\"type\":\"runCompleted\"} trailing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing characters after object");
    }

    @Test
    void rejectsAnEmptyLine() {
        assertThatThrownBy(() -> JsonlProgressCodec.read("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unexpected end of line");
    }

    @Provide
    Arbitrary<ProgressEvent> events() {
        Arbitrary<String> labels =
                Arbitraries.strings().withCharRange((char) 0, (char) 0xffff).ofMaxLength(40);
        Arbitrary<Integer> counts = Arbitraries.integers().between(0, Integer.MAX_VALUE);
        Arbitrary<ProgressEvent> runStarted = counts.map(c -> new ProgressEvent.RunStarted(c));
        Arbitrary<ProgressEvent> stageStarted =
                Combinators.combine(labels, counts, counts)
                        .as((s, idx, cnt) -> new ProgressEvent.StageStarted(s, idx, cnt));
        Arbitrary<ProgressEvent> pageProcessed =
                Combinators.combine(labels, counts, counts)
                        .as((s, done, total) -> new ProgressEvent.PageProcessed(s, done, total));
        Arbitrary<ProgressEvent> stageCompleted =
                labels.map(s -> new ProgressEvent.StageCompleted(s));
        Arbitrary<ProgressEvent> runCompleted = Arbitraries.just(new ProgressEvent.RunCompleted());
        Arbitrary<ProgressEvent> runFailed =
                Combinators.combine(labels, labels)
                        .as((kind, message) -> new ProgressEvent.RunFailed(kind, message));
        return Arbitraries.oneOf(
                runStarted, stageStarted, pageProcessed, stageCompleted, runCompleted, runFailed);
    }
}
