package tv.yeti.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Shared layout for Yeti TV's own settings pages (Network, Display, Sound, Date & time, Apps), matching the
 * Wi-Fi picker: left column = eyebrow, title, subtitle and a summary card; right column = a scrolling list of
 * rows that turn white when focused. Subclasses fill both in {@link #build()}, which runs on every resume so
 * values stay live; focus is restored by row key.
 *
 * Row types: action (title + value, opens something), toggle (drawn switch), slider (D-pad left/right),
 * section header. Sizes are on the 1920x1080 design canvas (see Ui.s).
 */
abstract class SettingsPage extends Activity {

    private static final int ROW_DARK_TEXT = Color.rgb(80, 86, 94);

    private LinearLayout summary, list;
    private TextView titleView, subView, eyebrowView;
    private FrameLayout overlay;
    private LinearLayout page;

    /** Fill {@link #summary(String, String)} / detail rows and the list rows. */
    abstract void build();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background());

        page = new LinearLayout(this);
        page.setPadding(Ui.safeX, Ui.safeY, Ui.safeX, Ui.safeY);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        root.addView(page, Apps.fill());

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        eyebrowView = Ui.eyebrow(this, "Settings", Ui.ACCENT);
        left.addView(eyebrowView);
        titleView = Ui.text(this, "", 60, Ui.TEXT, true);
        titleView.setPadding(0, Ui.s(12), 0, 0);
        left.addView(titleView);
        subView = Ui.text(this, "", 24, Ui.TEXT_DIM, false);
        subView.setPadding(0, Ui.s(12), 0, Ui.s(40));
        subView.setLineSpacing(0, 1.15f);
        left.addView(subView);
        summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        left.addView(summary);
        page.addView(left, new LinearLayout.LayoutParams(Ui.s(600), ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFadingEdgeLength(Ui.s(40));
        scroll.setVerticalFadingEdgeEnabled(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Ui.s(4), 0, Ui.s(24));
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rlp.leftMargin = Ui.s(72);
        page.addView(scroll, rlp);

        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(200, 0, 0, 0));
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);
        root.addView(overlay, Apps.fill());

        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); rebuild(); }

    @Override public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) {
            Runnable back = dialogBack;
            closeOverlay();
            if (back != null) back.run();
        }
        else super.onBackPressed();
    }

    /** Re-run {@link #build()}, keeping focus on the same row. */
    void rebuild() {
        View f = getCurrentFocus();
        Object key = f == null ? null : f.getTag();
        summary.removeAllViews();
        list.removeAllViews();
        build();
        View again = key == null ? null : list.findViewWithTag(key);
        if (again != null) again.requestFocus();
        else if (overlay.getVisibility() != View.VISIBLE) {
            for (int i = 0; i < list.getChildCount(); i++)
                if (list.getChildAt(i).isFocusable()) { list.getChildAt(i).requestFocus(); break; }
        }
    }

    void header(String eyebrow, String title, String sub) {
        eyebrowView.setText(eyebrow.toUpperCase());
        titleView.setText(title);
        subView.setText(sub);
    }

    // ---------------- left column ----------------

    private LinearLayout card;

    /** Starts the summary card: eyebrow (coloured) + big value. Follow with {@link #detail}. */
    void summary(String eyebrow, int eyebrowColor, String title) {
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.s(36), Ui.s(30), Ui.s(36), Ui.s(26));
        card.setBackground(Ui.solid(Ui.CARD, 28));
        card.addView(Ui.eyebrow(this, eyebrow, eyebrowColor));
        TextView t = Ui.text(this, title, 36, Ui.TEXT, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(0, Ui.s(10), 0, Ui.s(12));
        card.addView(t);
        summary.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    void detail(String k, String v) {
        if (card == null || v == null) return;
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, Ui.s(12), 0, Ui.s(12));
        r.addView(Ui.text(this, k, 22, Ui.TEXT_DIM, false), new LinearLayout.LayoutParams(Ui.s(170), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView val = Ui.text(this, v, 22, Ui.TEXT, true);
        val.setSingleLine(true);
        val.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(val, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(r);
    }

    /** Free view under the summary card (e.g. a big clock). */
    void summaryView(View v) { summary.addView(v); }

    // ---------------- list rows ----------------

    void section(String text) {
        TextView t = Ui.eyebrow(this, text, Ui.TEXT_DIM);
        t.setPadding(Ui.s(28), Ui.s(list.getChildCount() == 0 ? 8 : 34), 0, Ui.s(14));
        list.addView(t);
    }

    private LinearLayout baseRow(String key, float h) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.s(28), 0, Ui.s(28), 0);
        r.setFocusable(true);
        r.setClickable(true);
        r.setTag(key);
        r.setBackground(Ui.solid(Color.TRANSPARENT, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.s(h));
        lp.bottomMargin = Ui.s(6);
        list.addView(r, lp);
        return r;
    }

    private LinearLayout texts(LinearLayout r, String title, String value, int valueColor, TextView[] out) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, title, 26, Ui.TEXT, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t);
        TextView v = Ui.text(this, value == null ? "" : value, 20, valueColor, false);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setPadding(0, Ui.s(6), 0, 0);
        v.setVisibility(value == null ? View.GONE : View.VISIBLE);
        col.addView(v);
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        out[0] = t;
        out[1] = v;
        return col;
    }

    /** Row that does something on OK. {@code value} (may be null) shows under the title; a chevron when it opens a page. */
    View action(String key, String title, String value, boolean chevron, Runnable click) {
        return action(key, title, value, Ui.TEXT_DIM, chevron, click);
    }

    View action(String key, String title, String value, int valueColor, boolean chevron, Runnable click) {
        LinearLayout r = baseRow(key, 92);
        TextView[] tv = new TextView[2];
        texts(r, title, value, valueColor, tv);
        TextView arrow = Ui.text(this, chevron ? "›" : "", 40, Ui.TEXT_DIM, false);
        r.addView(arrow);
        r.setOnClickListener(v -> click.run());
        r.setOnFocusChangeListener((v, has) -> {
            ((GradientDrawable) v.getBackground()).setColor(has ? Ui.FOCUS : Color.TRANSPARENT);
            tv[0].setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
            tv[1].setTextColor(has ? (valueColor == Ui.TEXT_DIM ? ROW_DARK_TEXT : darker(valueColor)) : valueColor);
            arrow.setTextColor(has ? ROW_DARK_TEXT : Ui.TEXT_DIM);
        });
        return r;
    }

    /** On/off row with a drawn switch. {@code change} gets the new state and returns whether it was applied. */
    View toggle(String key, String title, String value, boolean on, Predicate change) {
        LinearLayout r = baseRow(key, 92);
        TextView[] tv = new TextView[2];
        texts(r, title, value, Ui.TEXT_DIM, tv);
        FrameLayout track = new FrameLayout(this);
        track.setPadding(Ui.s(5), 0, Ui.s(5), 0);
        View knob = new View(this);
        knob.setBackground(Ui.solid(Color.WHITE, 999));
        track.addView(knob, new FrameLayout.LayoutParams(Ui.s(30), Ui.s(30), Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START)));
        track.setBackground(Ui.solid(on ? Ui.ACCENT : Color.argb(80, 255, 255, 255), 999));
        r.addView(track, new LinearLayout.LayoutParams(Ui.s(72), Ui.s(40)));
        r.setOnClickListener(v -> { if (change.test(!on)) rebuild(); });
        r.setOnFocusChangeListener((v, has) -> {
            ((GradientDrawable) v.getBackground()).setColor(has ? Ui.FOCUS : Color.TRANSPARENT);
            tv[0].setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
            tv[1].setTextColor(has ? ROW_DARK_TEXT : Ui.TEXT_DIM);
            if (!on) track.setBackground(Ui.solid(has ? Color.rgb(190, 194, 200) : Color.argb(80, 255, 255, 255), 999));
        });
        return r;
    }

    interface Predicate { boolean test(boolean v); }
    interface IntChange { void set(int v); }
    interface Format { String of(int v); }

    /**
     * 0..max slider. Left/Right on the remote move it by {@code step} (applied live through {@code change});
     * OK does nothing. {@code format} turns the value into the label on the right.
     */
    View slider(String key, String title, int value, int max, int step, IntChange change, Format format) {
        return slider(key, title, value, 0, max, step, change, format);
    }

    /** min..max slider. */
    View slider(String key, String title, int value, int min, int max, int step, IntChange change, Format format) {
        LinearLayout r = baseRow(key, 116);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        TextView t = Ui.text(this, title, 26, Ui.TEXT, true);
        top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView num = Ui.text(this, format.of(value), 24, Ui.TEXT, true);
        top.addView(num);
        col.addView(top);

        FrameLayout bar = new FrameLayout(this);
        View track = new View(this);
        track.setBackground(Ui.solid(Color.argb(60, 255, 255, 255), 999));
        bar.addView(track, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.s(10), Gravity.CENTER_VERTICAL));
        View fill = new View(this);
        fill.setBackground(Ui.solid(Ui.ACCENT, 999));
        bar.addView(fill, new FrameLayout.LayoutParams(0, Ui.s(10), Gravity.CENTER_VERTICAL));
        View knob = new View(this);
        knob.setBackground(Ui.solid(Color.WHITE, 999));
        bar.addView(knob, new FrameLayout.LayoutParams(Ui.s(26), Ui.s(26), Gravity.CENTER_VERTICAL));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.s(30));
        blp.topMargin = Ui.s(16);
        col.addView(bar, blp);
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final int[] v = {value};
        Runnable layoutBar = () -> {
            int w = bar.getWidth();
            if (w == 0) return;
            int k = Ui.s(26);
            int x = Math.round((w - k) * ((v[0] - min) / (float) (max - min)));
            fill.getLayoutParams().width = x + k / 2;
            ((FrameLayout.LayoutParams) knob.getLayoutParams()).leftMargin = x;
            fill.requestLayout();
            knob.requestLayout();
        };
        bar.addOnLayoutChangeListener((view, l, tp, rt, bt, ol, ot, or, ob) -> { if (rt - l != or - ol) bar.post(layoutBar); });

        r.setOnKeyListener((view, code, e) -> {
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                int nv = Math.max(min, Math.min(max, v[0] + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? step : -step)));
                if (nv != v[0]) {
                    v[0] = nv;
                    change.set(nv);
                    num.setText(format.of(nv));
                    layoutBar.run();
                }
            }
            return true;
        });
        r.setOnFocusChangeListener((view, has) -> {
            ((GradientDrawable) view.getBackground()).setColor(has ? Ui.FOCUS : Color.TRANSPARENT);
            t.setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
            num.setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
            track.setBackground(Ui.solid(has ? Color.rgb(200, 204, 210) : Color.argb(60, 255, 255, 255), 999));
            knob.setBackground(Ui.solid(has ? Ui.TEXT_ON_FOCUS : Color.WHITE, 999));
        });
        return r;
    }

    /** Plain label/value line in the list (not focusable). */
    void info(String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(Ui.s(28), Ui.s(14), Ui.s(28), Ui.s(14));
        r.addView(Ui.text(this, k, 22, Ui.TEXT_DIM, false), new LinearLayout.LayoutParams(Ui.s(260), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView val = Ui.text(this, v, 22, Ui.TEXT, true);
        r.addView(val, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        list.addView(r);
    }

    private static int darker(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[2] *= 0.65f;
        return Color.HSVToColor(hsv);
    }

    // ---------------- dialogs ----------------

    /** Centered message with buttons; the first button gets focus. Pairs of label, action. */
    /** Runs when Back closes the current dialog (instead of a button). Reset by every new dialog. */
    Runnable dialogBack;

    /** Returns the body text view so callers can update it (e.g. a countdown). */
    TextView dialog(String title, String body, Object... buttons) {
        overlay.removeAllViews();
        dialogBack = null;
        TextView bd = null;
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(Ui.s(56), Ui.s(48), Ui.s(56), Ui.s(48));
        p.setBackground(Ui.solid(Ui.CARD, 32));
        p.setClipChildren(false);
        p.addView(Ui.text(this, title, 38, Ui.TEXT, true));
        if (body != null) {
            bd = Ui.text(this, body, 24, Ui.TEXT_DIM, false);
            bd.setPadding(0, Ui.s(14), 0, 0);
            bd.setLineSpacing(0, 1.15f);
            p.addView(bd);
        }
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        View first = null;
        for (int i = 0; i + 1 < buttons.length; i += 2) {
            final Runnable action = (Runnable) buttons[i + 1];
            View b = pill((String) buttons[i], () -> { closeOverlay(); if (action != null) action.run(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.s(60));
            lp.rightMargin = Ui.s(16);
            row.addView(b, lp);
            if (first == null) first = b;
        }
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = Ui.s(36);
        p.addView(row, rl);
        overlay.addView(p, new FrameLayout.LayoutParams(Ui.s(860), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        overlay.setVisibility(View.VISIBLE);
        page.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (first != null) first.requestFocus();
        return bd;
    }

    /** Pick one of {@code labels}; the current one is marked and focused. */
    void choose(String title, String[] labels, int selected, IntChange pick) {
        overlay.removeAllViews();
        dialogBack = null;
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(Ui.s(40), Ui.s(40), Ui.s(40), Ui.s(30));
        p.setBackground(Ui.solid(Ui.CARD, 32));
        TextView t = Ui.text(this, title, 34, Ui.TEXT, true);
        t.setPadding(Ui.s(16), 0, 0, Ui.s(20));
        p.addView(t);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        View focus = null;
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(Ui.s(24), 0, Ui.s(24), 0);
            r.setFocusable(true);
            r.setClickable(true);
            r.setBackground(Ui.solid(Color.TRANSPARENT, 16));
            TextView l = Ui.text(this, labels[i], 24, Ui.TEXT, i == selected);
            r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView mark = Ui.text(this, i == selected ? "✓" : "", 26, Ui.ACCENT, true);
            r.addView(mark);
            r.setOnFocusChangeListener((v, has) -> {
                ((GradientDrawable) v.getBackground()).setColor(has ? Ui.FOCUS : Color.TRANSPARENT);
                l.setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
                mark.setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.ACCENT);
            });
            r.setOnClickListener(v -> { closeOverlay(); pick.set(idx); });
            col.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.s(64)));
            if (i == selected || focus == null) focus = i == selected ? r : (focus == null ? r : focus);
        }
        p.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.min(Ui.s(64) * labels.length, Ui.s(720))));
        overlay.addView(p, new FrameLayout.LayoutParams(Ui.s(640), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        overlay.setVisibility(View.VISIBLE);
        page.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (focus != null) focus.requestFocus();
    }

    void closeOverlay() {
        overlay.removeAllViews();
        overlay.setVisibility(View.GONE);
        page.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        rebuild();
    }

    private View pill(String label, Runnable click) {
        LinearLayout p = new LinearLayout(this);
        p.setGravity(Gravity.CENTER);
        p.setPadding(Ui.s(30), 0, Ui.s(30), 0);
        p.setFocusable(true);
        p.setClickable(true);
        TextView t = Ui.text(this, label, 22, Ui.TEXT, true);
        p.addView(t);
        p.setOnClickListener(v -> click.run());
        Ui.pillFocus(p, 1.05f, new TextView[]{t}, null);
        return p;
    }
}
