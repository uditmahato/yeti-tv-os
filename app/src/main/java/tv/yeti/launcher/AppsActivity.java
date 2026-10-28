package tv.yeti.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** "All apps" drawer: every installed app in a 6-column grid, scaled to the screen. */
public class AppsActivity extends Activity {

    private LinearLayout grid;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.background());
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.setPadding(Ui.safeX, Ui.safeY, Ui.safeX, Ui.safeY);
        scroll.addView(page);

        TextView title = Ui.text(this, "All apps", 48, Ui.TEXT, true);
        page.addView(title);
        TextView hint = Ui.text(this, "Hold OK (or press Menu) on an app for options", 22, Ui.TEXT_DIM, false);
        hint.setPadding(0, Ui.s(10), 0, Ui.s(40));
        page.addView(hint);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        page.addView(grid);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        grid.removeAllViews();
        List<Apps.App> apps = Apps.list(this, new HashSet<>());
        int cols = 6, gap = Ui.s(28), h = Ui.s(200);
        for (int i = 0; i < apps.size(); i += cols) {
            List<View> rowViews = new ArrayList<>();
            for (int j = i; j < Math.min(i + cols, apps.size()); j++) rowViews.add(Apps.tile(this, apps.get(j), 84, 22));
            LinearLayout row = Apps.row(this, rowViews, cols, Ui.contentW, h, gap);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
            lp.bottomMargin = gap;
            grid.addView(row, lp);
        }
        if (grid.getChildCount() > 0) ((ViewGroup) grid.getChildAt(0)).getChildAt(0).requestFocus();
    }

    @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent e) {
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f != null && f.getTag() instanceof String) { Apps.options(this, (String) f.getTag()); return true; }
        }
        return super.onKeyDown(keyCode, e);
    }

    static FrameLayout.LayoutParams unused() { return null; }
}
