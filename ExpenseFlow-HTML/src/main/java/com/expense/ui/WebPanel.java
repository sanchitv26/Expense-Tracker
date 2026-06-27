package com.expense.ui;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.net.URI;
import java.util.function.Consumer;

/**
 * A JEditorPane configured to render our external CSS file and to translate
 * clicks on "app://..." links into callbacks the rest of the app can act on.
 * This is the bridge that lets plain HTML <a href="app://..."> tags drive
 * real Java logic (navigation, field edits, save, delete) without any
 * JavaScript engine, since the built-in HTML renderer doesn't run JS.
 */
public class WebPanel extends JEditorPane {

    public WebPanel(Consumer<String> onAppLink) {
        setEditable(false);
        setContentType("text/html");
        setBackground(new Color(0x0F1624));

        HTMLEditorKit kit = new HTMLEditorKit();
        setEditorKit(kit);

        addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                URI uri = e.getURL() != null ? null : null; // URL may be null for app:// (non-standard scheme)
                String desc = e.getDescription(); // raw href text, works even for custom schemes
                if (desc != null && desc.startsWith("app://")) {
                    onAppLink.accept(desc);
                }
            }
        });
    }

    public void setHtml(String html) {
        setText(html);
        setCaretPosition(0);
    }
}
