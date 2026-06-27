package com.expense.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads a raw .html file from the web/ folder on disk and substitutes
 * UPPER_SNAKE_CASE placeholder tokens with live values computed in Java.
 * Keeps the actual markup/styling 100% in separate .html / .css files;
 * Java only ever supplies text to drop into the template.
 */
public class HtmlTemplate {

    private final String rawHtml;

    private HtmlTemplate(String rawHtml) {
        this.rawHtml = rawHtml;
    }

    public static HtmlTemplate load(Path htmlFile) {
        try {
            String content = Files.readString(htmlFile, StandardCharsets.UTF_8);
            return new HtmlTemplate(content);
        } catch (IOException e) {
            throw new RuntimeException("Could not load template: " + htmlFile, e);
        }
    }

    /** Replaces every occurrence of each key with its value. Values are inserted as-is (already HTML-safe).
     *  Keys are applied longest-first so that a key which is a prefix of another
     *  (e.g. STAT_LARGEST vs STAT_LARGEST_TITLE) can never partially clobber it. */
    public String render(Map<String, String> values) {
        String out = rawHtml;
        List<String> keys = new ArrayList<>(values.keySet());
        keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String key : keys) {
            out = out.replace(key, values.get(key));
        }
        return out;
    }

    /** Escapes raw user text for safe embedding inside HTML body content. */
    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
