package com.ivana.liscript.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;

import androidx.core.content.res.ResourcesCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditTextCode extends androidx.appcompat.widget.AppCompatEditText {

    public EditTextCode(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.addTextChangedListener(new CodeTextWatcher());
    }

    public EditTextCode(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.addTextChangedListener(new CodeTextWatcher());
    }

    public EditTextCode(Context context) {
        super(context);
        this.addTextChangedListener(new CodeTextWatcher());
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        checkHighlightParens(selStart, selEnd);
    }

    // text watcher - highlight code

    class CodeTextWatcher implements TextWatcher {

        private CharSequence editedChars;
        private int editedFrom;
        private int editedTo;

        final SpanScheme string = new SpanScheme(Pattern.compile("\"([^\"]*)\""), R.color.string, true, false, false);
        final SpanScheme stringError = new SpanScheme(Pattern.compile("\"([^\"]*)$"), R.color.string, true, true, true);
        final SpanScheme comment = new SpanScheme(Pattern.compile(";([^;]*);"), R.color.comment, false, false, true);
        final SpanScheme commentError = new SpanScheme(Pattern.compile(";([^;]*)$"), R.color.comment, false, true, true);
        final SpanScheme[] schemes = {string, stringError, comment, commentError};

        final Pattern utilPattern = Pattern.compile("[\\s(\\[{}\\])]+");
        final Pattern wordPattern = Pattern.compile("[^;\"\\s(\\[{}\\])]+");

        void applyStyle(Editable s, Matcher m, int color, boolean bold, boolean underline, boolean italic) {
            color = ResourcesCompat.getColor(getResources(), color, null);
            s.setSpan(new ForegroundColorSpan(color), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (underline)
                s.setSpan(new UnderlineSpan(), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int styleSpan = 0;
            if (bold && italic) styleSpan = Typeface.BOLD_ITALIC;
            else if (bold) styleSpan = Typeface.BOLD;
            else if (italic) styleSpan = Typeface.ITALIC;
            if (styleSpan > 0)
                s.setSpan(new StyleSpan(styleSpan), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // some chars were deleted
            if (count > after) editedChars = s.subSequence(start, start + count);
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // some chars were added or substituted
            if (count >= before) editedChars = s.subSequence(start, start + count);
            editedFrom = start;
            editedTo = start + count; // + Math.max(before, count);
        }

        @Override
        public void afterTextChanged(Editable s) {
            int from = 0, to = s.length();
            if (editedChars != null) {
                boolean hasEvilChars = false;
                for (int i = 0; i < editedChars.length(); i++) {
                    char c = editedChars.charAt(i);
                    if (c == '"' || c == ';') { // evil chars!
                        hasEvilChars = true;
                        break;
                    }
                }
                if (!hasEvilChars) {
                    from = editedFrom - 1;
                    while (from >= 0 && s.charAt(from) != '\n') from--;
                    from++;

                    to = editedTo;
                    while (to < s.length() && s.charAt(to) != '\n') to++;
                }
            }
            // Log.i("HHH", editedChars.toString() + " " + editedFrom + " " + editedTo + " | " + from + " " + to + " " + s.length());
            highlightText(s, from, to);
        }

        public void highlightText(Editable s, int from, int to) {
            removeSpans(s, from, to, ForegroundColorSpan.class);
            removeSpans(s, from, to, UnderlineSpan.class);
            removeSpans(s, from, to, StyleSpan.class);

            Matcher m = Pattern.compile(".").matcher(s);
            int p = from;
            while (p < to) { //s.length()) {

                // skip whitespaces & brackets
                m.usePattern(utilPattern);
                if (m.lookingAt()) {
                    p = m.end();
                    m = m.region(p, s.length());
                    continue;
                }

                // try to find & highlight string or comment (possible multiline)
                boolean found = false;
                for (SpanScheme scheme : schemes) {
                    m.usePattern(scheme.pattern);
                    if (m.lookingAt()) {
                        applyStyle(s, m, scheme.color, scheme.bold, scheme.underline, scheme.italic);
                        p = m.end();
                        m = m.region(p, s.length());
                        found = true;
                        break;
                    }
                }
                if (found) continue;

                // try to find & highlight token
                m.usePattern(wordPattern);
                if (m.lookingAt()) {
                    String t = m.group();
                    // Log.i("WORD", t);
                    if (t.equals("true") || t.equals("false"))
                        applyStyle(s, m, R.color.bool, true, false, false);
                    else
                        try {
                            Integer.parseInt(t); // Integer.valueOf(t);
                            applyStyle(s, m, R.color.number, false, false, false);
                        } catch (NumberFormatException errorInteger) {
                            try {
                                Double.parseDouble(t); // Double.valueOf(t);
                                applyStyle(s, m, R.color.number, false, false, false);
                            } catch (NumberFormatException errorDouble) {
                                if (Eval.specialFormWords.containsKey(t))
                                    applyStyle(s, m, R.color.keyword, true, false, false);
                                else if (App.getInstance().env.map.containsKey(t))
                                    applyStyle(s, m, R.color.keyword, false, false, false);
                            }
                        }
                    p = m.end();
                    m = m.region(p, s.length());
                    continue;
                }

                // impossible case, but for preventing occasional infinite loop due possible bug
                p++;
                m = m.region(p, s.length());
            }
        }

        void removeSpans(Editable e, int from, int to, Class<? extends CharacterStyle> type) { // 0, e.length()
            CharacterStyle[] spans = e.getSpans(from, to, type);
            for (CharacterStyle span : spans) e.removeSpan(span);
        }

        class SpanScheme {
            final Pattern pattern;
            final int color;
            final boolean bold;
            final boolean underline;
            final boolean italic;

            SpanScheme(Pattern pattern, int color, boolean bold, boolean underline, boolean italic) {
                this.pattern = pattern;
                this.color = color;
                this.bold = bold;
                this.underline = underline;
                this.italic = italic;
            }
        }
    }

    // cursor watcher - matched brackets highlights

    CharacterStyle start, end;

    private void clearHighlights() {
        Editable editable = this.getText();
        if (null == editable) return;

        if (start != null) editable.removeSpan(start);
        if (end   != null) editable.removeSpan(end);
        start = end = null;
    }

    private int firstNonSpaceOffset(int p, int dir) {
        Editable editable = this.getText();
        if (null == editable) return -1;

        int i = p, length = editable.length();
        for(; i >= 0 && i < length; i += dir) {
            char c = editable.charAt(i);
            if ( !Character.isWhitespace(c) || c=='\n' ) break;
        }
        return i >= length ? -1 : i;
    }

    private int findMatchingParen(int p, char c) {
        Editable editable = this.getText();
        if (null == editable) return -1;

        int dir;
        char m;
        if      (c == '(') { m = ')'; dir = 1; }
        else if (c == '[') { m = ']'; dir = 1; }
        else if (c == '{') { m = '}'; dir = 1; }
        else if (c == ')') { m = '('; dir = -1; }
        else if (c == ']') { m = '['; dir = -1; }
        else if (c == '}') { m = '{'; dir = -1; }
        else return -1;

        int length = editable.length(), parenLevel = dir, i = p+dir;
        for(; i >= 0 && i < length; i += dir) {
            char t = editable.charAt(i);
            if      (t == c) parenLevel += dir;
            else if (t == m) parenLevel -= dir;
            if (parenLevel == 0) break;
        }
        return i >= length ? -1 : i;
    }

    private boolean checkHighlightParensFromPosition(int p) {
        Editable editable = this.getText();
        if (null == editable) return false;
        if (p < 0 || p >= editable.length()) return false;

        char c = editable.charAt(p);
        if (c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}') {
            int mp = findMatchingParen(p, c);
            if (mp >= 0) {
                start = new BackgroundColorSpan(Color.YELLOW);
                editable.setSpan(start, p, p+1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                end = new BackgroundColorSpan(Color.YELLOW);
                editable.setSpan(end, mp, mp+1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                start = new BackgroundColorSpan(Color.RED);
                editable.setSpan(start, p, p + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return true;
        }
        return false;
    }

    private void checkHighlightParens(int selStart, int selEnd) {
        clearHighlights();
        if (selStart != selEnd) return;

        if (checkHighlightParensFromPosition(selStart-1)) return; // near left
        if (checkHighlightParensFromPosition(selStart)) return; // near right
        if (checkHighlightParensFromPosition(firstNonSpaceOffset(selStart-1, -1))) return; // far left
        if (checkHighlightParensFromPosition(firstNonSpaceOffset(selStart, 1)) ) return; // far right
    }
}