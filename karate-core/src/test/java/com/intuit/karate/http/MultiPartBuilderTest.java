package com.intuit.karate.http;

import com.intuit.karate.FileUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class MultiPartBuilderTest {

    static final Logger logger = LoggerFactory.getLogger(MultiPartBuilderTest.class);

    String join(String... lines) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> iterator = Arrays.asList(lines).iterator();
        while (iterator.hasNext()) {
            sb.append(iterator.next()).append('\r').append('\n');
        }
        return sb.toString();
    }

    @Test
    void testMultiPart() {
        MultiPartBuilder builder = new MultiPartBuilder(true, null);
        builder.part("bar", "hello world");
        byte[] bytes = builder.build();
        String boundary = builder.getBoundary();
        String actual = FileUtils.toString(bytes);
        String expected = join(
                "--" + boundary,
                "content-disposition: form-data; name=\"bar\"",
                "content-length: 11",
                "content-type: text/plain",
                "",
                "hello world",
                "--" + boundary + "--"
        );
        assertEquals(expected, actual);
    }

    /**
     * R1: If {@code value} is a list of elems and we use multipart, then
     *   the body should consist of repeated parts with the same name.
     */
    @Test
    void ifUsingMultiPartAndValueIsList_thenCreateBodyOfRepeatParts() {
        MultiPartBuilder builder = new MultiPartBuilder(true, null);
        final String name = "test-name";
        final List<String> value = List.of("val-1", "val-2", "val-3");
        
        builder.part(name, value);
        
        final byte[] bytes = builder.build();
        final String boundary = builder.getBoundary();
        final String actual = FileUtils.toString(bytes);
        final String expected = join(
                "--" + boundary,
                "content-disposition: form-data; name=\"test-name\"",
                "content-length: 5",
                "content-type: text/plain",
                "",
                "val-1",
                "--" + boundary,
                "content-disposition: form-data; name=\"test-name\"",
                "content-length: 5",
                "content-type: text/plain",
                "",
                "val-2",
                "--" + boundary,
                "content-disposition: form-data; name=\"test-name\"",
                "content-length: 5",
                "content-type: text/plain",
                "",
                "val-3",
                "--" + boundary + "--"
        );
        assertEquals(expected, actual);
    }
    
    /**
     * R2: If the content-type is defined when using multipart, then
     *   it should be used in the body header.
     */
    @Test
    void ifUsingMultiPartAndContentTypeIsDefined_thenUseThatContentTypeInBodyHeader() {
        MultiPartBuilder builder = new MultiPartBuilder(true, null);
        final String name = "test-name";
        final String value = "just a test";

        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("value", value);
        map.put("contentType", "text/plain");       // See http.ResourceType for more options
        
        builder.part(map);

        final byte[] bytes = builder.build();
        final String body = FileUtils.toString(bytes);
        
        assertTrue(body.contains("content-type: text/plain"));
    }
    
    /**
     * R3: If the content-type is invalid when using multipart, then
     *   the content-type in the header should still be set to that value.
     */
    @Test
    void ifUsingMultiPartAndContentTypeIsInvalid_thenPreserveInvalidContentTypeInHeader() {
        MultiPartBuilder builder = new MultiPartBuilder(true, null);
        final String value = "just a test";

        Map<String, Object> map = new HashMap<>();
        map.put("name", "test-name");
        map.put("value", value);
        map.put("contentType", "invalid abcxyz");       // See http.ResourceType for more options
        
        builder.part(map);

        final byte[] bytes = builder.build();
        final String boundary = builder.getBoundary();
        final String actual = FileUtils.toString(bytes);
        final String expected = join(
                "--" + boundary,
                "content-disposition: form-data; name=\"test-name\"",
                "content-length: 11",
                "content-type: invalid abcxyz",
                "",
                value,
                "--" + boundary + "--"
        );
        assertEquals(expected, actual);
    }
    
    @Test
    void testUrlEncoded() {
        MultiPartBuilder builder = new MultiPartBuilder(false, null);
        builder.part("bar", "hello world");
        byte[] bytes = builder.build();
        assertEquals("application/x-www-form-urlencoded", builder.getContentTypeHeader());
        String actual = FileUtils.toString(bytes);
        assertEquals("bar=hello+world", actual);
    }

    /**
     * R4: If the value is null and we're not using multipart, then
     *   the body should be empty (i.e., the empty string).
     */
    @Test
    void ifNotUsingMultiPartAndValueIsNull_thenCreateEmptyBody() {
        MultiPartBuilder builder = new MultiPartBuilder(false, null);
        builder.part("abc", null);

        byte[] bytes = builder.build();
        String actual = FileUtils.toString(bytes);
        assertEquals("", actual);
    }

}
