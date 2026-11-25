package net.sourceforge.opencamera.ui;

import android.animation.AnimatorInflater;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import androidx.core.view.WindowInsetsCompat;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyApplicationInterface;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.R;
import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.cameracontroller.CameraControllerManager;
import net.sourceforge.opencamera.preview.Preview;

/** Open Camera UI Fork: portrait camera layout in the style of current phone camera apps:
 *  - a quick-settings pill whose items the user pins, unpins and reorders from its "more"
 *    sheet, with choosers for flash, timer, aspect, resolution, focus and grid;
 *  - a large shutter flanked by round gallery/switch buttons;
 *  - a lens carousel, and a carousel of photo modes (plus video) whose modes can also be
 *    pinned/unpinned/reordered from a "more" sheet;
 *  - a top row of round toggle chips (exposure lock etc.);
 *  - a zoom ruler along the side of the screen while drag-zooming;
 *  - the stock options popup shown as a bottom sheet;
 *  - debug copy: long-press almost anything to copy its debug info to the clipboard.
 *  It is applied after MainUI's stock layout pass, so landscape keeps the original Open Camera
 *  layout.
 */
public class ModernUI {
    private static final String TAG = "ModernUI";

    private static final int PILL_COLOR = 0xE6262A36;
    private static final int SHEET_COLOR = 0xF51C1F28;
    private static final int TILE_COLOR = 0xFF2A2F3D;
    private static final int TILE_SELECTED_COLOR = 0xFF3D4660;
    private static final int CHIP_COLOR = 0xB3262A36;
    private static final int CHIP_SELECTED_COLOR = 0xFFF2F2F2;
    private static final int TAB_INACTIVE_COLOR = 0xFFAEB8D8;
    private static final int ACCENT_COLOR = 0xFFFFD54F;
    private static final int [] SEGMENT_COLORS = { 0xFFFFD54F, 0xFF4FC3F7, 0xFF81C784, 0xFFF48FB1, 0xFFCE93D8 };

    private static final String PINNED_QUICK_KEY = "uifork_pinned_quick_items";
    private static final String PINNED_MODES_KEY = "uifork_pinned_modes";
    private static final String DEBUG_COPY_KEY = "uifork_debug_copy";
    private static final String DEFAULT_PINNED_QUICK = "SETTINGS,FLASH,TIMER,ASPECT,RESOLUTION,EXPOSURE";
    private static final String DEFAULT_PINNED_MODES = "X_NIGHT,NR,HDR,STD,VIDEO,PANORAMA,X_BOKEH";

    /** Optional on-screen toggles from the stock icon panel, shown as round chips along the top. */
    private static final int [] TOP_ROW_VIEWS = { R.id.exposure_lock, R.id.white_balance_lock, R.id.cycle_raw,
            R.id.store_location, R.id.text_stamp, R.id.stamp, R.id.focus_peaking, R.id.auto_level,
            R.id.face_detection, R.id.audio_control, R.id.cycle_lock_orientation, R.id.preview_shots,
            R.id.kraken_icon, R.id.trash, R.id.share };
    /** Stock views that this class repositions in portrait. */
    private static final int [] OVERRIDDEN_VIEWS = { R.id.take_photo, R.id.switch_camera, R.id.gallery,
            R.id.pause_video, R.id.take_photo_when_video_recording, R.id.cancel_panorama };
    /** Stock views whose job is taken over by the pill, carousels or sheets in portrait. */
    private static final int [] HIDDEN_IN_PORTRAIT = { R.id.settings, R.id.popup, R.id.exposure,
            R.id.cycle_flash, R.id.switch_video, R.id.switch_multi_camera };

    /** Items that can be pinned to the quick-settings pill. */
    private enum QuickItem { SETTINGS, FLASH, TIMER, ASPECT, RESOLUTION, EXPOSURE, FOCUS, GRID, AUTO_LEVEL }

    /** Entries of the mode carousel; enum order is the order of unpinned modes in the sheet. */
    private enum Mode {
        X_NIGHT(MyApplicationInterface.PhotoMode.X_Night, R.string.uifork_mode_x_night),
        NR(MyApplicationInterface.PhotoMode.NoiseReduction, R.string.uifork_mode_nr),
        HDR(MyApplicationInterface.PhotoMode.HDR, R.string.uifork_mode_hdr),
        DRO(MyApplicationInterface.PhotoMode.DRO, R.string.uifork_mode_dro),
        STD(MyApplicationInterface.PhotoMode.Standard, R.string.uifork_mode_photo),
        VIDEO(null, R.string.uifork_mode_video),
        PANORAMA(MyApplicationInterface.PhotoMode.Panorama, R.string.uifork_mode_panorama),
        BURST(MyApplicationInterface.PhotoMode.FastBurst, R.string.uifork_mode_burst),
        EXPO_BRACKETING(MyApplicationInterface.PhotoMode.ExpoBracketing, R.string.uifork_mode_expo_bracketing),
        FOCUS_BRACKETING(MyApplicationInterface.PhotoMode.FocusBracketing, R.string.uifork_mode_focus_bracketing),
        X_BOKEH(MyApplicationInterface.PhotoMode.X_Bokeh, R.string.uifork_mode_x_bokeh),
        X_HDR(MyApplicationInterface.PhotoMode.X_HDR, R.string.uifork_mode_x_hdr),
        X_AUTO(MyApplicationInterface.PhotoMode.X_Auto, R.string.uifork_mode_x_auto),
        X_BEAUTY(MyApplicationInterface.PhotoMode.X_Beauty, R.string.uifork_mode_x_beauty);

        final MyApplicationInterface.PhotoMode photo_mode;
        final int label;

        Mode(MyApplicationInterface.PhotoMode photo_mode, int label) {
            this.photo_mode = photo_mode;
            this.label = label;
        }

        /** PHOTO and VIDEO are always in the carousel. */
        boolean alwaysPinned() {
            return this == STD || this == VIDEO;
        }
    }

    private enum Chooser { NONE, FLASH, TIMER, ASPECT, RESOLUTION, FOCUS, GRID }
    private enum Sheet { NONE, QUICK, MODES }

    /** Supplies debug text lazily, when the user long-presses. */
    private interface DebugInfo {
        String get();
    }

    private final MainActivity main_activity;
    private final MainUI main_ui;
    private final Map<Integer, int[]> original_geometry = new HashMap<>();
    private final Map<Integer, ImageView.ScaleType> original_scale_types = new HashMap<>();

    private boolean views_created;
    private boolean portrait_active;
    private int last_navigation_gap;
    private int pill_bottom;
    private String top_row_signature = "";

    private LinearLayout quick_bar;
    private final Map<QuickItem, View> quick_views = new EnumMap<>(QuickItem.class);

    private HorizontalScrollView chooser_scroll;
    private LinearLayout chooser_row;
    private Chooser open_chooser = Chooser.NONE;

    private ScrollView sheet_scroll;
    private LinearLayout sheet_content;
    private Sheet open_sheet = Sheet.NONE;
    private boolean sheet_editing;

    private LinearLayout lens_bar; // lens_fixed (output resolution + AUTO, always visible) then lens_scroll
    private LinearLayout lens_fixed;
    private HorizontalScrollView lens_scroll;
    private LinearLayout lens_row;
    private TextView preset_chip;
    private View mode_marker; // marks the centre of the photo mode carousel
    private Mode mode_candidate; // the mode in the centre of the carousel (selected when scrolling stops)
    private int lens_count;
    private final List<Lens> last_lenses = new ArrayList<>();
    // the open lens, for the zoom ruler: its zoom relative to the main lens, whether it is the
    // auto (logical multi-camera) lens, and the zoom factors of the lenses the auto lens uses
    private double selected_lens_zoom = 1.0;
    private boolean selected_lens_auto;
    private final List<Double> auto_lens_zooms = new ArrayList<>();

    private HorizontalScrollView mode_scroll;
    private LinearLayout mode_row;
    private final Map<Mode, TextView> mode_views = new EnumMap<>(Mode.class);
    private String mode_signature = "";
    private Mode last_selected_mode;

    private ZoomScaleView zoom_scale;

    // smart AUTO: the auto (logical multi-camera) lens doesn't necessarily use every lens (e.g.
    // Samsung's driver doesn't use the 5x telephoto for other apps), so AUTO hands off to the
    // highest-power lens itself, with software zoom on top
    private static final String SMART_AUTO_KEY = "uifork_smart_auto";
    private static final String AUTO_SWITCHING_KEY = "uifork_auto_switching";
    private static final float RULER_MARGIN = 0.15f; // of the preview height, above and below the whole zoom range
    private final List<Lens> auto_chain = new ArrayList<>(); // lenses AUTO switches between, lowest zoom first
    private ImageView freeze_view;
    private boolean quiet_rebuild; // rebuilding the lens chips after an AUTO lens switch
    // zoom ruler layout for the current drag: the screen y of the zoom level ruler_origin_log
    private boolean ruler_active;
    private float ruler_origin_y;
    private double ruler_origin_log = Double.NaN;
    private float ruler_pixels_per_log;
    private final Runnable unfreeze_runnable = this::unfreezePreview;
    private static final float SOFTWARE_ZOOM_MAX = 8.0f; // matches Preview
    private Lens auto_lens;
    private Lens tele_lens;
    private double auto_min_ratio = Double.NaN;
    private double pending_total_zoom = Double.NaN;
    private boolean handoff_in_progress;

    private int stock_navigation_gap;
    private boolean mode_scroll_touching;
    private int mode_scroll_down_x;
    private boolean mode_scroll_programmatic;
    private final Runnable mode_scroll_settle = this::onModeScrollSettled;

    ModernUI(MainActivity main_activity, MainUI main_ui) {
        this.main_activity = main_activity;
        this.main_ui = main_ui;
    }

    private int dp(float value) {
        return (int)(value * main_activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private String str(int id) {
        return main_activity.getResources().getString(id);
    }

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(main_activity);
    }

    /** Whether the portrait layout is currently applied. */
    public boolean isPortraitActive() {
        return portrait_active;
    }

    // ---------------------------------------------------------------- layout hooks

    /** Called at the start of MainUI.layoutUI(): undo our overrides so the stock layout code
     *  starts from the XML defaults.
     */
    void beforeLayout() {
        rememberOriginals();
        int [][] groups = { OVERRIDDEN_VIEWS, TOP_ROW_VIEWS };
        for(int [] group : groups) {
            for(int id : group) {
                clearRules(main_activity.findViewById(id));
                restoreGeometry(id);
            }
        }
        for(int id : HIDDEN_IN_PORTRAIT) {
            restoreGeometry(id);
        }
    }

    /** Called at the end of MainUI.layoutUI(). */
    void apply(boolean portrait, int navigation_gap) {
        createViewsIfNeeded();
        this.portrait_active = portrait;
        this.stock_navigation_gap = navigation_gap;
        this.last_navigation_gap = navigationGap();
        hideZoomSlider();
        styleTopRow();
        positionPreview(portrait);
        if( portrait ) {
            layoutPortrait();
            top_row_signature = "";
            layoutTopRow();
        }
        else {
            closeChooser();
        }
        syncVisibility();
        refresh();
    }

    private void rememberOriginals() {
        int [][] groups = { OVERRIDDEN_VIEWS, HIDDEN_IN_PORTRAIT, TOP_ROW_VIEWS };
        for(int [] group : groups) {
            for(int id : group) {
                if( original_geometry.containsKey(id) )
                    continue;
                View view = main_activity.findViewById(id);
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                original_geometry.put(id, new int[]{lp.width, lp.height,
                        view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom()});
                if( view instanceof ImageView )
                    original_scale_types.put(id, ((ImageView)view).getScaleType());
            }
        }
    }

    private void restoreGeometry(int id) {
        int [] g = original_geometry.get(id);
        if( g == null )
            return;
        View view = main_activity.findViewById(id);
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.width = g[0];
        lp.height = g[1];
        view.setLayoutParams(lp);
        view.setPadding(g[2], g[3], g[4], g[5]);
        ImageView.ScaleType scale_type = original_scale_types.get(id);
        if( scale_type != null )
            ((ImageView)view).setScaleType(scale_type);
    }

    private static void clearRules(View view) {
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)view.getLayoutParams();
        int [] rules = lp.getRules();
        for(int i=0;i<rules.length;i++) {
            lp.removeRule(i);
        }
        view.setLayoutParams(lp);
    }

    private RelativeLayout.LayoutParams resetParams(View view, int width, int height) {
        clearRules(view);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)view.getLayoutParams();
        lp.width = width;
        lp.height = height;
        lp.setMargins(0, 0, 0, 0);
        return lp;
    }

    /** The stock zoom slider is never shown - zoom is by dragging on the viewfinder or the lens
     *  carousel. It keeps working invisibly, as the stock code routes zoom changes through it.
     */
    private void hideZoomSlider() {
        View zoom = main_activity.findViewById(R.id.zoom_seekbar);
        ViewGroup.LayoutParams lp = zoom.getLayoutParams();
        if( lp.width != 0 || lp.height != 0 ) {
            lp.width = 0;
            lp.height = 0;
            zoom.setLayoutParams(lp);
        }
    }

    private void positionPreview(boolean portrait) {
        View preview = main_activity.findViewById(R.id.preview);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)preview.getLayoutParams();
        clearRules(preview);
        if( portrait ) {
            // viewfinder sits at the top, leaving the bottom of the screen for the controls
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
            lp.topMargin = dp(40);
        }
        else {
            lp.addRule(RelativeLayout.CENTER_IN_PARENT);
            lp.topMargin = 0;
        }
        preview.setLayoutParams(lp);
    }

    private View root() {
        return (View)main_activity.findViewById(R.id.take_photo).getParent();
    }

    private int rootWidth() {
        int width = root().getWidth();
        if( width <= 0 )
            width = main_activity.getResources().getDisplayMetrics().widthPixels;
        return width;
    }

    /** How much of the bottom of our layout is covered by the navigation bar right now. Uses the
     *  live window insets, so a collapsed/hidden navigation bar gives its space back.
     */
    private int navigationGap() {
        View root = root();
        WindowInsets insets = root.getRootWindowInsets();
        if( insets == null )
            return stock_navigation_gap;
        int bottom = WindowInsetsCompat.toWindowInsetsCompat(insets, root).getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        int [] location = new int[2];
        root.getLocationOnScreen(location);
        int window_height = root.getRootView().getHeight();
        int below_root = Math.max(0, window_height - (location[1] + root.getHeight()));
        return Math.max(0, bottom - below_root);
    }

    private int rootHeight() {
        int height = root().getHeight();
        if( height <= 0 )
            height = main_activity.getResources().getDisplayMetrics().heightPixels;
        return height;
    }

    private void layoutPortrait() {
        View take_photo = main_activity.findViewById(R.id.take_photo);
        int width = rootWidth();

        final int side_margin = Math.max(dp(16), width/6 - dp(26));
        final int base = last_navigation_gap + dp(6);
        final int mode_h = dp(40);
        final int lens_h = dp(52);
        final int shutter = dp(80);
        final int side_button = dp(52);
        final int shutter_bottom = base + mode_h + (lens_count > 1 ? lens_h : dp(8));
        final int side_bottom = shutter_bottom + (shutter - side_button)/2;
        pill_bottom = shutter_bottom + shutter + dp(18);

        RelativeLayout.LayoutParams lp = resetParams(mode_scroll, ViewGroup.LayoutParams.MATCH_PARENT, mode_h);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.bottomMargin = base;
        mode_scroll.setLayoutParams(lp);
        int side_padding = Math.max(0, width/2 - dp(48));
        mode_row.setPadding(side_padding, 0, side_padding, 0);

        lp = resetParams(lens_bar, ViewGroup.LayoutParams.WRAP_CONTENT, lens_h);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lp.leftMargin = dp(6);
        lp.rightMargin = dp(6);
        lp.bottomMargin = base + mode_h;
        lens_bar.setLayoutParams(lp);

        lp = resetParams(mode_marker, dp(18), dp(3));
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lp.bottomMargin = base + dp(2);
        mode_marker.setLayoutParams(lp);

        lp = resetParams(take_photo, shutter, shutter);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lp.bottomMargin = shutter_bottom;
        take_photo.setLayoutParams(lp);
        take_photo.setPadding(0, 0, 0, 0);

        ImageButton gallery = main_activity.findViewById(R.id.gallery);
        lp = resetParams(gallery, side_button, side_button);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        lp.leftMargin = side_margin;
        lp.bottomMargin = side_bottom;
        gallery.setLayoutParams(lp);
        gallery.setPadding(0, 0, 0, 0);
        gallery.setScaleType(ImageView.ScaleType.CENTER_CROP);
        gallery.setBackgroundResource(R.drawable.circle_background);
        gallery.setClipToOutline(true);

        View switch_camera = main_activity.findViewById(R.id.switch_camera);
        lp = resetParams(switch_camera, side_button, side_button);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        lp.rightMargin = side_margin;
        lp.bottomMargin = side_bottom;
        switch_camera.setLayoutParams(lp);
        int pad = dp(13);
        switch_camera.setPadding(pad, pad, pad, pad);

        // controls that only appear while recording video or a panorama sit either side of the shutter
        int [] right_of_shutter = { R.id.pause_video, R.id.cancel_panorama };
        for(int id : right_of_shutter) {
            View view = main_activity.findViewById(id);
            lp = resetParams(view, side_button, side_button);
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            lp.addRule(RelativeLayout.RIGHT_OF, R.id.take_photo);
            lp.leftMargin = dp(14);
            lp.bottomMargin = side_bottom;
            view.setLayoutParams(lp);
            view.setPadding(dp(4), dp(4), dp(4), dp(4));
        }
        View snapshot = main_activity.findViewById(R.id.take_photo_when_video_recording);
        lp = resetParams(snapshot, side_button, side_button);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.LEFT_OF, R.id.take_photo);
        lp.rightMargin = dp(14);
        lp.bottomMargin = side_bottom;
        snapshot.setLayoutParams(lp);
        snapshot.setPadding(dp(8), dp(8), dp(8), dp(8));

        for(int id : HIDDEN_IN_PORTRAIT) {
            View view = main_activity.findViewById(id);
            ViewGroup.LayoutParams hidden_lp = view.getLayoutParams();
            hidden_lp.width = 0;
            hidden_lp.height = 0;
            view.setLayoutParams(hidden_lp);
        }

        lp = resetParams(quick_bar, ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lp.bottomMargin = pill_bottom;
        quick_bar.setLayoutParams(lp);

        lp = resetParams(chooser_scroll, ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lp.setMargins(dp(12), 0, dp(12), pill_bottom + dp(58));
        chooser_scroll.setLayoutParams(lp);

        if( open_sheet != Sheet.NONE )
            layoutSheet();
    }

    /** Round chip styling for the optional on-screen toggles (exposure lock etc.). */
    private void styleTopRow() {
        for(int id : TOP_ROW_VIEWS) {
            View view = main_activity.findViewById(id);
            if( view.getTag(R.id.exposure_lock) == null ) {
                view.setBackground(oval(CHIP_COLOR));
                view.setBackgroundTintList(null);
                view.setTag(R.id.exposure_lock, Boolean.TRUE); // mark as styled
            }
        }
    }

    /** Centres the visible top-row chips along the top of the viewfinder. */
    private void layoutTopRow() {
        List<View> visible = new ArrayList<>();
        StringBuilder signature = new StringBuilder();
        for(int id : TOP_ROW_VIEWS) {
            View view = main_activity.findViewById(id);
            if( view.getVisibility() == View.VISIBLE ) {
                visible.add(view);
                signature.append(id).append(',');
            }
        }
        if( signature.toString().equals(top_row_signature) )
            return;
        top_row_signature = signature.toString();
        final int size = dp(40);
        final int gap = dp(10);
        int total = visible.size()*size + Math.max(0, visible.size()-1)*gap;
        int left = Math.max(dp(8), (rootWidth() - total)/2);
        for(View view : visible) {
            RelativeLayout.LayoutParams lp = resetParams(view, size, size);
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            lp.leftMargin = left;
            lp.topMargin = dp(52);
            view.setLayoutParams(lp);
            int pad = dp(9);
            view.setPadding(pad, pad, pad, pad);
            if( view instanceof ImageView )
                ((ImageView)view).setScaleType(ImageView.ScaleType.FIT_CENTER);
            left += size + gap;
        }
    }

    /** Positions the stock options popup as a bottom sheet above the quick-settings pill. */
    void layoutPopup(View container) {
        if( !portrait_active || !views_created )
            return;
        closeChooser();
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)container.getLayoutParams();
        clearRules(container);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        int bottom = pill_bottom + dp(58);
        lp.setMargins(0, 0, 0, bottom);
        int max_h = rootHeight() - bottom - dp(56);
        int pad = dp(8);
        View content = container instanceof ViewGroup && ((ViewGroup)container).getChildCount() > 0 ? ((ViewGroup)container).getChildAt(0) : null;
        int content_h = content != null ? content.getHeight() : 0;
        lp.height = content_h > 0 ? Math.min(content_h + 2*pad, max_h) : max_h;
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        container.setLayoutParams(lp);
        container.setBackground(roundedRect(SHEET_COLOR, dp(24)));
        container.setClipToOutline(true);
        container.setPadding(0, pad, 0, pad);
    }

    // ---------------------------------------------------------------- views and animation

    private LayoutTransition newLayoutTransition() {
        LayoutTransition transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        transition.setDuration(160);
        return transition;
    }

    private void createViewsIfNeeded() {
        if( views_created )
            return;
        views_created = true;
        RelativeLayout root = (RelativeLayout)root();

        // zoom ruler sits just above the viewfinder, below all controls
        zoom_scale = new ZoomScaleView(main_activity);
        zoom_scale.setVisibility(View.GONE);
        int preview_index = root.indexOfChild(main_activity.findViewById(R.id.preview));
        root.addView(zoom_scale, preview_index + 1, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int insert_index = root.indexOfChild(main_activity.findViewById(R.id.locker));
        if( insert_index < 0 )
            insert_index = root.getChildCount();

        // quick-settings pill (contents built from the pinned items)
        quick_bar = new LinearLayout(main_activity);
        quick_bar.setOrientation(LinearLayout.HORIZONTAL);
        quick_bar.setGravity(Gravity.CENTER_VERTICAL);
        quick_bar.setPadding(dp(6), 0, dp(6), 0);
        quick_bar.setBackground(roundedRect(PILL_COLOR, dp(999)));
        quick_bar.setLayoutTransition(newLayoutTransition());
        root.addView(quick_bar, insert_index++, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        rebuildQuickBar();

        // chooser shown above the pill for flash / timer / aspect / resolution / focus / grid
        chooser_scroll = new HorizontalScrollView(main_activity);
        chooser_scroll.setHorizontalScrollBarEnabled(false);
        chooser_scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chooser_scroll.setVisibility(View.GONE);
        chooser_row = new LinearLayout(main_activity);
        chooser_row.setOrientation(LinearLayout.HORIZONTAL);
        chooser_row.setGravity(Gravity.CENTER_VERTICAL);
        chooser_row.setPadding(dp(6), 0, dp(6), 0);
        chooser_row.setBackground(roundedRect(PILL_COLOR, dp(999)));
        chooser_scroll.addView(chooser_row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(chooser_scroll, insert_index++, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        // lens carousel
        lens_scroll = new HorizontalScrollView(main_activity);
        lens_scroll.setHorizontalScrollBarEnabled(false);
        lens_scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        lens_row = new LinearLayout(main_activity);
        lens_row.setOrientation(LinearLayout.HORIZONTAL);
        lens_row.setGravity(Gravity.CENTER);
        lens_scroll.addView(lens_row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        lens_bar = new LinearLayout(main_activity);
        lens_bar.setOrientation(LinearLayout.HORIZONTAL);
        lens_bar.setGravity(Gravity.CENTER_VERTICAL);
        lens_fixed = new LinearLayout(main_activity);
        lens_fixed.setOrientation(LinearLayout.HORIZONTAL);
        lens_fixed.setGravity(Gravity.CENTER_VERTICAL);
        lens_bar.addView(lens_fixed, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        lens_bar.addView(lens_scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        root.addView(lens_bar, insert_index++, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));

        // photo mode carousel
        mode_scroll = new HorizontalScrollView(main_activity);
        mode_scroll.setHorizontalScrollBarEnabled(false);
        mode_scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mode_row = new LinearLayout(main_activity);
        mode_row.setOrientation(LinearLayout.HORIZONTAL);
        mode_row.setGravity(Gravity.CENTER_VERTICAL);
        mode_row.setLayoutTransition(newLayoutTransition());
        mode_scroll.addView(mode_row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // scrolling the carousel selects whichever mode ends up in the centre
        mode_scroll.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if( action == MotionEvent.ACTION_DOWN )
                mode_scroll_down_x = mode_scroll.getScrollX();
            mode_scroll_touching = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL;
            if( !mode_scroll_touching && Math.abs(mode_scroll.getScrollX() - mode_scroll_down_x) > dp(4) ) {
                // the user scrolled (rather than tapped a mode)
                mode_scroll_programmatic = false;
                mode_scroll.removeCallbacks(mode_scroll_settle);
                mode_scroll.postDelayed(mode_scroll_settle, 180);
            }
            return false;
        });
        mode_scroll.setOnScrollChangeListener((v, x, y, old_x, old_y) -> {
            if( !mode_scroll_programmatic )
                updateModeCandidate();
            if( mode_scroll_programmatic || mode_scroll_touching )
                return;
            mode_scroll.removeCallbacks(mode_scroll_settle);
            mode_scroll.postDelayed(mode_scroll_settle, 180);
        });
        root.addView(mode_scroll, insert_index++, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        mode_marker = new View(main_activity);
        mode_marker.setBackground(roundedRect(ACCENT_COLOR, dp(2)));
        mode_marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(mode_marker, insert_index++, new RelativeLayout.LayoutParams(dp(18), dp(3)));

        // pin/unpin sheet for quick settings and photo modes
        sheet_scroll = new ScrollView(main_activity);
        sheet_scroll.setVisibility(View.GONE);
        sheet_scroll.setBackground(roundedRect(SHEET_COLOR, dp(24)));
        sheet_scroll.setClipToOutline(true);
        sheet_content = new LinearLayout(main_activity);
        sheet_content.setOrientation(LinearLayout.VERTICAL);
        sheet_content.setPadding(dp(14), dp(10), dp(14), dp(14));
        sheet_scroll.addView(sheet_content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sheet_scroll, insert_index, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // mirror the stock buttons' visibility (they still track capture state, immersive mode etc.)
        root.getViewTreeObserver().addOnGlobalLayoutListener(this::syncVisibility);
    }

    private GradientDrawable roundedRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(radius);
        drawable.setColor(color);
        return drawable;
    }

    private static GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private void addPressAnimation(View view) {
        view.setStateListAnimator(AnimatorInflater.loadStateListAnimator(main_activity, R.animator.button_animation));
    }

    /** Slides and fades a panel in. */
    private void animateIn(View view) {
        view.animate().cancel();
        view.setAlpha(0.0f);
        view.setTranslationY(dp(12));
        view.setScaleX(0.97f);
        view.setScaleY(0.97f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1.0f).translationY(0.0f).scaleX(1.0f).scaleY(1.0f)
                .setDuration(180).setInterpolator(new DecelerateInterpolator()).withEndAction(null).start();
    }

    /** Slides and fades a panel out, then runs end (if still wanted). */
    private void animateOut(final View view, final Runnable end) {
        view.animate().cancel();
        if( view.getVisibility() != View.VISIBLE ) {
            if( end != null )
                end.run();
            return;
        }
        view.animate().alpha(0.0f).translationY(dp(12)).setDuration(130).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    view.setVisibility(View.GONE);
                    if( end != null )
                        end.run();
                }).start();
    }

    /** Small bounce to show a state change. */
    private static void pulse(View view) {
        view.animate().cancel();
        view.setScaleX(0.8f);
        view.setScaleY(0.8f);
        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(260).setInterpolator(new OvershootInterpolator(3.0f)).start();
    }

    private static void setVisibility(View view, int visibility) {
        if( view.getVisibility() != visibility )
            view.setVisibility(visibility);
    }

    private void syncVisibility() {
        if( !views_created )
            return;
        int gui = View.GONE;
        int settings = View.GONE;
        if( portrait_active ) {
            gui = main_activity.findViewById(R.id.switch_video).getVisibility();
            settings = main_activity.findViewById(R.id.settings).getVisibility();
        }
        setVisibility(mode_scroll, gui);
        setVisibility(lens_bar, lens_count > 1 ? gui : View.GONE);
        setVisibility(mode_marker, mode_scroll.getVisibility());
        setVisibility(quick_bar, settings);
        if( settings != View.VISIBLE )
            closeChooser();
        if( portrait_active ) {
            layoutTopRow(); // only re-lays out if the set of visible chips changed
            // reclaim (or give back) the navigation bar's space when it is collapsed or shown
            int gap = navigationGap();
            if( Math.abs(gap - last_navigation_gap) > 1 ) {
                last_navigation_gap = gap;
                layoutPortrait();
            }
        }
    }

    // ---------------------------------------------------------------- debug copy

    private boolean debugCopyEnabled() {
        return prefs().getBoolean(DEBUG_COPY_KEY, true);
    }

    /** Copies debug info for something the user long-pressed to the clipboard, along with the
     *  general camera state. Returns false (so the long-press isn't consumed) if disabled.
     */
    public boolean copyDebugInfo(String subject, String details) {
        if( !debugCopyEnabled() )
            return false;
        StringBuilder text = new StringBuilder();
        text.append("Open Camera UI Fork debug: ").append(subject).append("\n\n");
        text.append(details).append("\n");
        text.append("--- camera state ---\n").append(cameraStateInfo());
        try {
            ClipboardManager clipboard = (ClipboardManager)main_activity.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Open Camera UI Fork debug", text.toString()));
            if( main_activity.getPreview() != null )
                main_activity.getPreview().showToast(str(R.string.uifork_debug_copied) + ": " + subject, true);
        }
        catch(Throwable e) {
            MyDebug.logStackTrace(TAG, "failed to copy debug info", e);
        }
        return true;
    }

    private void attachDebug(View view, final String subject, final DebugInfo info) {
        view.setOnLongClickListener(v -> {
            String details;
            try {
                details = info.get();
            }
            catch(Throwable e) {
                details = "failed to collect details: " + e;
            }
            return copyDebugInfo(subject, details);
        });
    }

    private String cameraStateInfo() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("app version: ").append(main_activity.getPackageManager().getPackageInfo(main_activity.getPackageName(), 0).versionName).append('\n');
        }
        catch(Throwable e) {
            sb.append("app version: ?\n");
        }
        sb.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" (").append(Build.DEVICE).append("), Android SDK ").append(Build.VERSION.SDK_INT).append('\n');
        Preview preview = main_activity.getPreview();
        if( preview == null ) {
            sb.append("preview: null\n");
            return sb.toString();
        }
        MyApplicationInterface app = main_activity.getApplicationInterface();
        sb.append("camera api: ").append(preview.usingCamera2API() ? "Camera2" : "Camera1").append('\n');
        sb.append("camera id: ").append(preview.getCameraId()).append(", physical pref: ").append(app.getCameraIdSPhysicalPref()).append('\n');
        sb.append("camera open: ").append(preview.getCameraController() != null).append(", opening: ").append(preview.isOpeningCamera()).append('\n');
        sb.append("video: ").append(preview.isVideo()).append(", recording: ").append(preview.isVideoRecording()).append('\n');
        sb.append("photo mode: ").append(app.getPhotoMode()).append(" (pref ").append(prefs().getString(PreferenceKeys.PhotoModePreferenceKey, "?")).append(")\n");
        CameraController.Size capture = preview.getCurrentPictureSize();
        CameraController.Size output = app.getOutputPictureSize();
        sb.append("capture size: ").append(capture != null ? capture.width + "x" + capture.height : "null").append('\n');
        sb.append("output size: ").append(output != null ? output.width + "x" + output.height : "null").append('\n');
        sb.append("supports zoom: ").append(preview.supportsZoom()).append(", max zoom index: ").append(preview.getMaxZoom()).append('\n');
        if( preview.getCameraController() != null )
            sb.append("zoom ratio: ").append(preview.getZoomRatio()).append(" (min ").append(preview.getMinZoomRatio()).append(")\n");
        sb.append("software zoom: ").append(preview.getSoftwareZoom()).append('\n');
        sb.append("flash: ").append(preview.getCurrentFlashValue()).append(", focus: ").append(preview.getCurrentFocusValue()).append('\n');
        sb.append("selected lens zoom: ").append(selected_lens_zoom).append(", auto: ").append(selected_lens_auto).append(", auto lens zooms: ").append(auto_lens_zooms).append('\n');
        sb.append("lens chips: ").append(lens_count).append(", portrait layout: ").append(portrait_active).append('\n');
        sb.append("pinned quick items: ").append(pinnedQuickItems()).append('\n');
        sb.append("pinned modes: ").append(pinnedModes()).append('\n');
        return sb.toString();
    }

    private static String formatValue(Object value) {
        if( value == null )
            return "null";
        if( value.getClass().isArray() ) {
            StringBuilder sb = new StringBuilder("[");
            int length = java.lang.reflect.Array.getLength(value);
            for(int i=0;i<length;i++) {
                if( i > 0 )
                    sb.append(", ");
                sb.append(formatValue(java.lang.reflect.Array.get(value, i)));
            }
            return sb.append(']').toString();
        }
        return value.toString();
    }

    /** All the CameraCharacteristics for a camera id. */
    private String characteristicsDump(String camera_id) {
        if( camera_id == null )
            return "(no Camera2 id)\n";
        StringBuilder sb = new StringBuilder();
        try {
            CameraManager manager = (CameraManager)main_activity.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera_id);
            for(CameraCharacteristics.Key<?> key : characteristics.getKeys()) {
                String value;
                try {
                    value = formatValue(characteristics.get(key));
                }
                catch(Throwable e) {
                    value = "error: " + e;
                }
                sb.append(key.getName()).append(" = ").append(value).append('\n');
            }
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.P )
                sb.append("physicalCameraIds = ").append(characteristics.getPhysicalCameraIds()).append('\n');
        }
        catch(Throwable e) {
            sb.append("failed to read characteristics: ").append(e).append('\n');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- quick-settings pill

    private List<QuickItem> pinnedQuickItems() {
        List<QuickItem> items = new ArrayList<>();
        for(String name : prefs().getString(PINNED_QUICK_KEY, DEFAULT_PINNED_QUICK).split(",")) {
            try {
                QuickItem item = QuickItem.valueOf(name.trim());
                if( !items.contains(item) )
                    items.add(item);
            }
            catch(IllegalArgumentException e) {
                // ignore unknown entries from other versions
            }
        }
        return items;
    }

    private void setPinnedQuickItems(List<QuickItem> items) {
        StringBuilder value = new StringBuilder();
        for(QuickItem item : items) {
            if( value.length() > 0 )
                value.append(',');
            value.append(item.name());
        }
        prefs().edit().putString(PINNED_QUICK_KEY, value.toString()).apply();
    }

    private boolean isAvailable(QuickItem item) {
        Preview preview = main_activity.getPreview();
        if( preview == null )
            return item == QuickItem.SETTINGS;
        switch( item ) {
            case FLASH:
                return preview.supportsFlash() && preview.getCurrentFlashValue() != null && supportedFlashValues() != null;
            case ASPECT:
            case RESOLUTION:
                return !preview.isVideo() && main_activity.getApplicationInterface().getPhotoMode() != MyApplicationInterface.PhotoMode.Panorama
                        && main_activity.getApplicationInterface().getOutputPictureSize() != null;
            case EXPOSURE:
                return main_activity.supportsExposureButton();
            case FOCUS:
                return supportedFocusValues() != null;
            case AUTO_LEVEL:
                return main_activity.supportsAutoStabilise();
            default:
                return true;
        }
    }

    private int quickLabel(QuickItem item) {
        switch( item ) {
            case SETTINGS: return R.string.uifork_settings;
            case FLASH: return R.string.uifork_flash;
            case TIMER: return R.string.uifork_timer;
            case ASPECT: return R.string.uifork_aspect_ratio;
            case RESOLUTION: return R.string.uifork_resolution;
            case EXPOSURE: return R.string.uifork_exposure;
            case FOCUS: return R.string.uifork_focus;
            case GRID: return R.string.uifork_grid;
            default: return R.string.uifork_auto_level;
        }
    }

    /** Icon for an item, or 0 if it is shown as text (see quickText()). */
    private int quickIcon(QuickItem item) {
        Preview preview = main_activity.getPreview();
        switch( item ) {
            case SETTINGS: return R.drawable.settings;
            case FLASH: return flashIcon(preview != null ? preview.getCurrentFlashValue() : null);
            case TIMER: return R.drawable.ic_timer_white_48dp;
            case EXPOSURE: return R.drawable.ic_exposure_white_48dp;
            case FOCUS: return focusIcon(preview != null ? preview.getCurrentFocusValue() : null);
            case GRID: return R.drawable.uifork_grid;
            case AUTO_LEVEL: return R.drawable.auto_stabilise_icon;
            default: return 0;
        }
    }

    private String quickText(QuickItem item) {
        switch( item ) {
            case TIMER: {
                String timer = prefs().getString(PreferenceKeys.TimerPreferenceKey, "0");
                return "0".equals(timer) ? "" : timer + "s";
            }
            case ASPECT:
            case RESOLUTION: {
                CameraController.Size size = main_activity.getApplicationInterface().getOutputPictureSize();
                if( size == null )
                    return "";
                return item == QuickItem.ASPECT ? aspectLabel(size.width, size.height) : megapixelLabel(size.width, size.height);
            }
            default:
                return "";
        }
    }

    private boolean quickActive(QuickItem item) {
        switch( item ) {
            case AUTO_LEVEL:
                return prefs().getBoolean(PreferenceKeys.AutoStabilisePreferenceKey, false);
            case GRID:
                return !"preference_grid_none".equals(prefs().getString(PreferenceKeys.ShowGridPreferenceKey, "preference_grid_none"));
            default:
                return false;
        }
    }

    private String quickDebug(QuickItem item) {
        Preview preview = main_activity.getPreview();
        StringBuilder sb = new StringBuilder();
        sb.append("item: ").append(item).append('\n');
        sb.append("pinned: ").append(pinnedQuickItems().contains(item)).append(", available: ").append(isAvailable(item)).append(", active: ").append(quickActive(item)).append('\n');
        sb.append("shown text: '").append(quickText(item)).append("', icon: ").append(main_activity.getResources().getResourceEntryName(quickIcon(item) != 0 ? quickIcon(item) : R.drawable.settings)).append('\n');
        switch( item ) {
            case FLASH:
                sb.append("current: ").append(preview.getCurrentFlashValue()).append('\n');
                sb.append("supported: ").append(preview.getSupportedFlashValues()).append('\n');
                sb.append("offered: ").append(supportedFlashValues()).append('\n');
                break;
            case FOCUS:
                sb.append("current: ").append(preview.getCurrentFocusValue()).append('\n');
                sb.append("supported: ").append(preview.getSupportedFocusValues()).append('\n');
                break;
            case TIMER:
                sb.append("pref: ").append(prefs().getString(PreferenceKeys.TimerPreferenceKey, "0")).append('\n');
                break;
            case GRID:
                sb.append("pref: ").append(prefs().getString(PreferenceKeys.ShowGridPreferenceKey, "preference_grid_none")).append('\n');
                break;
            case AUTO_LEVEL:
                sb.append("pref: ").append(prefs().getBoolean(PreferenceKeys.AutoStabilisePreferenceKey, false)).append('\n');
                break;
            case EXPOSURE:
                sb.append("supports exposure button: ").append(main_activity.supportsExposureButton()).append(", current exposure: ").append(preview.getCurrentExposure()).append('\n');
                break;
            case ASPECT:
            case RESOLUTION: {
                String key = PreferenceKeys.getResolutionPreferenceKey(preview.getCameraId(), main_activity.getApplicationInterface().getCameraIdSPhysicalPref());
                sb.append("resolution pref (").append(key).append("): ").append(prefs().getString(key, "")).append('\n');
                List<CameraController.Size> sizes = preview.getSupportedPictureSizes(false);
                sb.append("supported picture sizes:");
                if( sizes != null ) {
                    for(CameraController.Size size : sizes)
                        sb.append(' ').append(size.width).append('x').append(size.height);
                }
                sb.append('\n');
                break;
            }
            default:
                break;
        }
        return sb.toString();
    }

    private void activate(QuickItem item, View view) {
        switch( item ) {
            case SETTINGS:
                closeChooser();
                main_activity.clickedSettings(view);
                break;
            case FLASH:
                toggleChooser(Chooser.FLASH);
                break;
            case TIMER:
                toggleChooser(Chooser.TIMER);
                break;
            case ASPECT:
                toggleChooser(Chooser.ASPECT);
                break;
            case RESOLUTION:
                toggleChooser(Chooser.RESOLUTION);
                break;
            case FOCUS:
                toggleChooser(Chooser.FOCUS);
                break;
            case GRID:
                toggleChooser(Chooser.GRID);
                break;
            case EXPOSURE:
                closeChooser();
                main_activity.clickedExposure(view);
                break;
            case AUTO_LEVEL:
                closeChooser();
                main_ui.getOnScreenIcons().clickedAutoLevel();
                refresh();
                break;
        }
    }

    private void rebuildQuickBar() {
        quick_bar.removeAllViews();
        quick_views.clear();
        for(final QuickItem item : pinnedQuickItems()) {
            View view;
            if( item == QuickItem.ASPECT || item == QuickItem.RESOLUTION || item == QuickItem.TIMER ) {
                TextView text = new TextView(main_activity);
                text.setTextColor(Color.WHITE);
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                text.setTypeface(Typeface.DEFAULT_BOLD);
                text.setGravity(Gravity.CENTER);
                text.setMinWidth(dp(44));
                text.setPadding(dp(8), 0, dp(8), 0);
                view = text;
                quick_bar.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
            }
            else {
                ImageButton button = new ImageButton(main_activity);
                button.setScaleType(ImageView.ScaleType.FIT_CENTER);
                button.setBackground(null);
                int pad = dp(11);
                button.setPadding(pad, pad, pad, pad);
                view = button;
                quick_bar.addView(view, new LinearLayout.LayoutParams(dp(44), dp(44)));
            }
            view.setContentDescription(str(quickLabel(item)));
            view.setOnClickListener(v -> activate(item, v));
            attachDebug(view, "Quick setting " + item, () -> quickDebug(item));
            addPressAnimation(view);
            quick_views.put(item, view);
        }
        // the "more" button is always last
        ImageButton more = new ImageButton(main_activity);
        more.setImageResource(R.drawable.popup);
        more.setScaleType(ImageView.ScaleType.FIT_CENTER);
        more.setBackground(null);
        int pad = dp(11);
        more.setPadding(pad, pad, pad, pad);
        more.setContentDescription(str(R.string.uifork_more_settings));
        more.setOnClickListener(v -> toggleSheet(Sheet.QUICK));
        attachDebug(more, "Quick settings more button", () -> "pinned: " + pinnedQuickItems() + "\nall: " + java.util.Arrays.toString(QuickItem.values()) + "\n");
        addPressAnimation(more);
        quick_bar.addView(more, new LinearLayout.LayoutParams(dp(44), dp(44)));
        refreshQuickBar();
    }

    private void refreshQuickBar() {
        for(Map.Entry<QuickItem, View> entry : quick_views.entrySet()) {
            QuickItem item = entry.getKey();
            View view = entry.getValue();
            boolean available = isAvailable(item);
            setVisibility(view, available ? View.VISIBLE : View.GONE);
            if( !available )
                continue;
            boolean active = quickActive(item);
            String state = quickIcon(item) + "|" + quickText(item) + "|" + active;
            Object previous = view.getTag();
            if( view instanceof ImageButton ) {
                ImageButton button = (ImageButton)view;
                button.setImageResource(quickIcon(item));
                if( active )
                    button.setColorFilter(ACCENT_COLOR);
                else
                    button.clearColorFilter();
            }
            else if( view instanceof TextView ) {
                TextView text = (TextView)view;
                String value = quickText(item);
                if( item == QuickItem.TIMER ) {
                    Drawable icon = main_activity.getResources().getDrawable(R.drawable.ic_timer_white_48dp, main_activity.getTheme());
                    icon.setBounds(0, 0, dp(22), dp(22));
                    text.setCompoundDrawables(icon, null, null, null);
                    text.setCompoundDrawablePadding(value.isEmpty() ? 0 : dp(3));
                }
                text.setText(value);
            }
            if( previous != null && !state.equals(previous) )
                pulse(view);
            view.setTag(state);
        }
    }

    // ---------------------------------------------------------------- state

    /** Swaps the stock shutter artwork for the large round shutter. */
    void onTakePhotoIcon(ImageButton take_photo, int stock_resource) {
        int resource = 0;
        if( stock_resource == R.drawable.take_photo_selector )
            resource = R.drawable.uifork_shutter_photo;
        else if( stock_resource == R.drawable.take_video_selector )
            resource = R.drawable.uifork_shutter_video;
        else if( stock_resource == R.drawable.take_video_recording )
            resource = R.drawable.uifork_shutter_recording;
        if( resource != 0 )
            take_photo.setImageResource(resource);
        refresh();
    }

    /** Updates the pill, mode carousel and any open sheet to reflect the current camera state. */
    void refresh() {
        if( !views_created )
            return;
        if( main_activity.getPreview() == null )
            return;
        refreshQuickBar();
        refreshModes();
        if( open_sheet != Sheet.NONE )
            buildSheet();
    }

    private List<String> supportedFlashValues() {
        Preview preview = main_activity.getPreview();
        List<String> values = preview.getSupportedFlashValues();
        if( values == null )
            return null;
        if( preview.isVideo() ) {
            List<String> filtered = new ArrayList<>();
            for(String value : values) {
                if( Preview.isFlashSupportedForVideo(value) )
                    filtered.add(value);
            }
            values = filtered;
        }
        return values.size() > 1 ? values : null;
    }

    private List<String> supportedFocusValues() {
        Preview preview = main_activity.getPreview();
        List<String> values = preview.getSupportedFocusValues();
        if( values == null )
            return null;
        values = new ArrayList<>(values);
        // only offer the continuous focus mode that matches photo/video, as the stock popup does
        values.remove(preview.isVideo() ? "focus_mode_continuous_picture" : "focus_mode_continuous_video");
        return values.size() > 1 ? values : null;
    }

    private int iconFromArrays(int icons_id, int values_id, String value, int fallback) {
        if( value == null )
            return fallback;
        try(TypedArray icons = main_activity.getResources().obtainTypedArray(icons_id)) {
            String [] values = main_activity.getResources().getStringArray(values_id);
            for(int i=0;i<values.length && i<icons.length();i++) {
                if( values[i].equals(value) )
                    return icons.getResourceId(i, fallback);
            }
        }
        return fallback;
    }

    private String entryFromArrays(int entries_id, int values_id, String value) {
        String [] values = main_activity.getResources().getStringArray(values_id);
        String [] entries = main_activity.getResources().getStringArray(entries_id);
        for(int i=0;i<values.length && i<entries.length;i++) {
            if( values[i].equals(value) )
                return entries[i];
        }
        return value;
    }

    private int flashIcon(String flash_value) {
        return iconFromArrays(R.array.flash_icons, R.array.flash_values, flash_value, R.drawable.flash_off);
    }

    private int focusIcon(String focus_value) {
        return iconFromArrays(R.array.focus_mode_icons, R.array.focus_mode_values, focus_value, R.drawable.focus_mode_auto);
    }

    static String aspectLabel(int width, int height) {
        int long_side = Math.max(width, height);
        int short_side = Math.min(width, height);
        if( short_side <= 0 )
            return "";
        double ratio = long_side / (double)short_side;
        double [] ratios = { 1.0, 4.0/3.0, 3.0/2.0, 16.0/9.0, 2.0 };
        String [] labels = { "1:1", "3:4", "2:3", "9:16", "1:2" };
        for(int i=0;i<ratios.length;i++) {
            if( Math.abs(ratio - ratios[i]) < 0.03 )
                return labels[i];
        }
        return String.format(Locale.US, "1:%.2f", ratio);
    }

    static String megapixelLabel(int width, int height) {
        double mp = width * (double)height / 1.0e6;
        if( mp >= 9.5 )
            return Math.round(mp) + "M";
        String label = String.format(Locale.US, "%.1f", mp);
        if( label.endsWith(".0") )
            label = label.substring(0, label.length() - 2);
        return label + "M";
    }

    // ---------------------------------------------------------------- choosers

    /** Closes the chooser and pin sheet if open. */
    public void closeChooser() {
        closeSheet();
        if( !views_created || open_chooser == Chooser.NONE )
            return;
        open_chooser = Chooser.NONE;
        animateOut(chooser_scroll, () -> {
            if( open_chooser == Chooser.NONE )
                chooser_row.removeAllViews();
        });
    }

    private void toggleChooser(Chooser chooser) {
        if( open_chooser == chooser ) {
            closeChooser();
            return;
        }
        closeSheet();
        main_ui.closePopup();
        main_ui.closeExposureUI();
        chooser_row.removeAllViews();
        open_chooser = chooser;
        switch( chooser ) {
            case FLASH:
                buildFlashChooser();
                break;
            case TIMER:
                buildTimerChooser();
                break;
            case ASPECT:
            case RESOLUTION:
                buildSizeChooser(chooser == Chooser.ASPECT);
                break;
            case FOCUS:
                buildFocusChooser();
                break;
            case GRID:
                buildGridChooser();
                break;
            default:
                break;
        }
        if( chooser_row.getChildCount() == 0 ) {
            closeChooser();
            return;
        }
        animateIn(chooser_scroll);
    }

    private void addChooserItem(String label, int icon, boolean selected, String description, final String debug_value, View.OnClickListener listener) {
        View item;
        if( icon != 0 ) {
            ImageButton button = new ImageButton(main_activity);
            button.setImageResource(icon);
            button.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = dp(9);
            button.setPadding(pad, pad, pad, pad);
            if( selected )
                button.setColorFilter(Color.BLACK);
            item = button;
        }
        else {
            TextView text = new TextView(main_activity);
            text.setText(label);
            text.setGravity(Gravity.CENTER);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            text.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            text.setTextColor(selected ? Color.BLACK : Color.WHITE);
            text.setMinWidth(dp(40));
            text.setPadding(dp(12), 0, dp(12), 0);
            item = text;
        }
        item.setBackground(selected ? roundedRect(CHIP_SELECTED_COLOR, dp(999)) : null);
        item.setContentDescription(description);
        item.setOnClickListener(listener);
        final Chooser chooser = open_chooser;
        attachDebug(item, "Chooser " + chooser + " option " + debug_value,
                () -> "chooser: " + chooser + "\noption value: " + debug_value + "\ndescription: " + description + "\nselected: " + selected + "\n");
        addPressAnimation(item);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(icon != 0 ? dp(40) : ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        lp.setMargins(dp(2), 0, dp(2), 0);
        lp.gravity = Gravity.CENTER_VERTICAL;
        chooser_row.addView(item, lp);
    }

    private void buildFlashChooser() {
        final Preview preview = main_activity.getPreview();
        List<String> values = supportedFlashValues();
        if( values == null )
            return;
        String current = preview.getCurrentFlashValue();
        for(final String value : values) {
            addChooserItem(null, flashIcon(value), value.equals(current), entryFromArrays(R.array.flash_entries, R.array.flash_values, value), value, v -> {
                preview.updateFlash(value);
                main_ui.setPopupIcon();
                main_ui.destroyPopup(); // the cached popup shows the old flash selection
                closeChooser();
                refresh();
            });
        }
    }

    private void buildFocusChooser() {
        final Preview preview = main_activity.getPreview();
        List<String> values = supportedFocusValues();
        if( values == null )
            return;
        String current = preview.getCurrentFocusValue();
        for(final String value : values) {
            addChooserItem(null, focusIcon(value), value.equals(current), entryFromArrays(R.array.focus_mode_entries, R.array.focus_mode_values, value), value, v -> {
                preview.updateFocus(value, false, true);
                main_ui.destroyPopup();
                closeChooser();
                refresh();
            });
        }
    }

    private void buildGridChooser() {
        String current = prefs().getString(PreferenceKeys.ShowGridPreferenceKey, "preference_grid_none");
        String [] values = main_activity.getResources().getStringArray(R.array.preference_grid_values);
        String [] entries = main_activity.getResources().getStringArray(R.array.preference_grid_entries);
        for(int i=0;i<values.length && i<entries.length;i++) {
            final String value = values[i];
            addChooserItem(entries[i], 0, value.equals(current), entries[i], value, v -> {
                prefs().edit().putString(PreferenceKeys.ShowGridPreferenceKey, value).apply();
                main_activity.getApplicationInterface().getDrawPreview().updateSettings(); // grid is cached
                main_ui.destroyPopup();
                closeChooser();
                refresh();
            });
        }
    }

    private void buildTimerChooser() {
        String current = prefs().getString(PreferenceKeys.TimerPreferenceKey, "0");
        String [] values = main_activity.getResources().getStringArray(R.array.preference_timer_values);
        String [] entries = main_activity.getResources().getStringArray(R.array.preference_timer_entries);
        for(int i=0;i<values.length && i<entries.length;i++) {
            final String value = values[i];
            String label = "0".equals(value) ? entries[i] : value + "s";
            addChooserItem(label, 0, value.equals(current), entries[i], value, v -> {
                prefs().edit().putString(PreferenceKeys.TimerPreferenceKey, value).apply();
                main_ui.destroyPopup();
                closeChooser();
                refresh();
            });
        }
    }

    /** Aspect chooser (one chip per aspect ratio) or resolution chooser (sizes with the current
     *  aspect ratio). The choice is the output resolution - capture always uses the largest size.
     */
    private void buildSizeChooser(boolean aspect_mode) {
        final Preview preview = main_activity.getPreview();
        if( preview.isVideo() )
            return;
        List<CameraController.Size> sizes = preview.getSupportedPictureSizes(true);
        CameraController.Size current = main_activity.getApplicationInterface().getOutputPictureSize();
        if( sizes == null || sizes.isEmpty() || current == null )
            return;
        final String current_aspect = aspectLabel(current.width, current.height);
        final double current_mp = current.width * (double)current.height;

        if( aspect_mode ) {
            // keep roughly the same megapixels when changing aspect ratio
            Map<String, CameraController.Size> best_for_aspect = new HashMap<>();
            List<String> order = new ArrayList<>();
            for(CameraController.Size size : sizes) {
                String label = aspectLabel(size.width, size.height);
                CameraController.Size best = best_for_aspect.get(label);
                double diff = Math.abs(Math.log(size.width * (double)size.height / current_mp));
                if( best == null ) {
                    order.add(label);
                    best_for_aspect.put(label, size);
                }
                else if( diff < Math.abs(Math.log(best.width * (double)best.height / current_mp)) ) {
                    best_for_aspect.put(label, size);
                }
            }
            for(final String label : order) {
                final CameraController.Size size = best_for_aspect.get(label);
                addChooserItem(label, 0, label.equals(current_aspect), label, size.width + "x" + size.height, v -> selectOutputSize(size, false));
            }
        }
        else {
            final String preset = main_activity.getApplicationInterface().getOutputPreset();
            final String [][] presets = { {"8", "8M"}, {"24", "24M"}, {"max", "MAX"} };
            for(final String [] entry : presets) {
                addChooserItem(entry[1], 0, entry[0].equals(preset), entry[1], "preset " + entry[0], v -> selectPreset(entry[0]));
            }
            View divider = new View(main_activity);
            divider.setBackgroundColor(0x40FFFFFF);
            LinearLayout.LayoutParams divider_lp = new LinearLayout.LayoutParams(dp(1), dp(22));
            divider_lp.setMargins(dp(6), 0, dp(6), 0);
            divider_lp.gravity = Gravity.CENTER_VERTICAL;
            chooser_row.addView(divider, divider_lp);
            List<CameraController.Size> same_aspect = new ArrayList<>();
            for(CameraController.Size size : sizes) {
                if( aspectLabel(size.width, size.height).equals(current_aspect) )
                    same_aspect.add(size);
            }
            Collections.sort(same_aspect, (a, b) -> Long.compare((long)b.width*b.height, (long)a.width*a.height));
            Set<String> used = new HashSet<>();
            for(final CameraController.Size size : same_aspect) {
                String label = megapixelLabel(size.width, size.height);
                if( used.contains(label) )
                    label = label + " " + Math.max(size.width, size.height);
                used.add(label);
                boolean selected = "custom".equals(preset) && size.width == current.width && size.height == current.height;
                addChooserItem(label, 0, selected, size.width + " x " + size.height, size.width + "x" + size.height, v -> selectOutputSize(size, true));
            }
        }
    }

    private void selectOutputSize(CameraController.Size size, boolean exact) {
        if( exact ) // picking a specific resolution switches off the 8M/24M/MAX preset
            prefs().edit().putString(MyApplicationInterface.OutputPresetPreferenceKey, "custom").apply();
        Preview preview = main_activity.getPreview();
        String key = PreferenceKeys.getResolutionPreferenceKey(preview.getCameraId(), main_activity.getApplicationInterface().getCameraIdSPhysicalPref());
        prefs().edit().putString(key, size.width + " " + size.height).apply();
        closeChooser();
        updatePresetChip();
        main_ui.destroyPopup();
        main_activity.updateForSettings(true, "", false, false);
    }

    // ---------------------------------------------------------------- photo mode carousel

    private boolean isSupported(Mode mode) {
        switch( mode ) {
            case STD:
            case VIDEO:
                return true;
            case NR: return main_activity.supportsNoiseReduction();
            case DRO: return main_activity.supportsDRO();
            case HDR: return main_activity.supportsHDR();
            case PANORAMA: return main_activity.supportsPanorama();
            case BURST: return main_activity.supportsFastBurst();
            case EXPO_BRACKETING: return main_activity.supportsExpoBracketing();
            case FOCUS_BRACKETING: return main_activity.supportsFocusBracketing();
            case X_AUTO: return main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_AUTOMATIC) || lensForExtension(CameraExtensionCharacteristics.EXTENSION_AUTOMATIC) != null;
            case X_HDR: return main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_HDR) || lensForExtension(CameraExtensionCharacteristics.EXTENSION_HDR) != null;
            case X_NIGHT: return main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_NIGHT) || lensForExtension(CameraExtensionCharacteristics.EXTENSION_NIGHT) != null;
            case X_BOKEH: return main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_BOKEH) || lensForExtension(CameraExtensionCharacteristics.EXTENSION_BOKEH) != null;
            case X_BEAUTY: return main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_BEAUTY) || lensForExtension(CameraExtensionCharacteristics.EXTENSION_BEAUTY) != null;
            default: return false;
        }
    }

    /** Pinned modes, in carousel order. PHOTO and VIDEO are always included. */
    private List<Mode> pinnedModes() {
        List<Mode> modes = new ArrayList<>();
        for(String name : prefs().getString(PINNED_MODES_KEY, DEFAULT_PINNED_MODES).split(",")) {
            try {
                Mode mode = Mode.valueOf(name.trim());
                if( !modes.contains(mode) )
                    modes.add(mode);
            }
            catch(IllegalArgumentException e) {
                // ignore unknown entries from other versions
            }
        }
        if( !modes.contains(Mode.STD) )
            modes.add(Mode.STD);
        if( !modes.contains(Mode.VIDEO) )
            modes.add(modes.indexOf(Mode.STD) + 1, Mode.VIDEO);
        return modes;
    }

    private void setPinnedModes(List<Mode> modes) {
        StringBuilder value = new StringBuilder();
        for(Mode mode : modes) {
            if( value.length() > 0 )
                value.append(',');
            value.append(mode.name());
        }
        prefs().edit().putString(PINNED_MODES_KEY, value.toString()).apply();
    }

    private Mode currentMode() {
        Preview preview = main_activity.getPreview();
        if( preview != null && preview.isVideo() )
            return Mode.VIDEO;
        MyApplicationInterface.PhotoMode photo_mode = main_activity.getApplicationInterface().getPhotoMode();
        for(Mode mode : Mode.values()) {
            if( mode.photo_mode == photo_mode )
                return mode;
        }
        return Mode.STD;
    }

    private String modeDebug(Mode mode) {
        return "mode: " + mode + "\nlabel: " + str(mode.label) + "\nphoto mode: " + mode.photo_mode +
                "\nsupported: " + isSupported(mode) + "\npinned: " + pinnedModes().contains(mode) +
                "\ncurrent: " + (mode == currentMode()) + "\nphoto mode pref: " + prefs().getString(PreferenceKeys.PhotoModePreferenceKey, "?") +
                "\ncamera extension pref: " + main_activity.getApplicationInterface().isCameraExtensionPref() + "\n";
    }

    /** Rebuilds the carousel if the set of modes shown changed, then highlights the current one. */
    private void refreshModes() {
        Mode current = currentMode();
        List<Mode> shown = new ArrayList<>();
        for(Mode mode : pinnedModes()) {
            if( isSupported(mode) )
                shown.add(mode);
        }
        if( !shown.contains(current) )
            shown.add(current);
        String signature = shown.toString();
        if( !signature.equals(mode_signature) ) {
            mode_signature = signature;
            mode_row.removeAllViews();
            mode_views.clear();
            last_selected_mode = null;
            mode_candidate = null;
            for(final Mode mode : shown) {
                TextView tab = new TextView(main_activity);
                tab.setText(mode.label);
                tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                tab.setLetterSpacing(0.04f);
                tab.setGravity(Gravity.CENTER);
                tab.setPadding(dp(14), 0, dp(14), 0);
                tab.setTextColor(TAB_INACTIVE_COLOR);
                tab.setOnClickListener(v -> selectMode(mode));
                attachDebug(tab, "Mode " + mode, () -> modeDebug(mode));
                mode_row.addView(tab, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                mode_views.put(mode, tab);
            }
            TextView more = new TextView(main_activity);
            more.setText("•••");
            more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            more.setGravity(Gravity.CENTER);
            more.setTextColor(TAB_INACTIVE_COLOR);
            more.setPadding(dp(14), 0, dp(14), 0);
            more.setContentDescription(str(R.string.uifork_more_modes));
            more.setOnClickListener(v -> toggleSheet(Sheet.MODES));
            attachDebug(more, "Mode carousel more button", () -> "pinned: " + pinnedModes() + "\nshown: " + mode_signature + "\n");
            mode_row.addView(more, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if( current != last_selected_mode ) {
            for(Map.Entry<Mode, TextView> entry : mode_views.entrySet()) {
                boolean selected = entry.getKey() == current;
                boolean was_selected = entry.getKey() == last_selected_mode;
                TextView tab = entry.getValue();
                tab.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                if( selected != was_selected )
                    animateTextColor(tab, selected ? Color.WHITE : TAB_INACTIVE_COLOR);
            }
            last_selected_mode = current;
            mode_candidate = current;
            centreMode(current);
        }
    }

    /** Scrolls the carousel so the given mode is in the centre. */
    private void centreMode(Mode mode) {
        final TextView view = mode_views.get(mode);
        if( view == null )
            return;
        mode_scroll.post(() -> {
            int x = view.getLeft() + view.getWidth()/2 - mode_scroll.getWidth()/2;
            if( Math.abs(mode_scroll.getScrollX() - x) < 2 )
                return;
            mode_scroll_programmatic = true;
            mode_scroll.smoothScrollTo(Math.max(0, x), 0);
            mode_scroll.postDelayed(() -> mode_scroll_programmatic = false, 450);
        });
    }

    /** The mode nearest the centre of the carousel. */
    private Mode nearestCentreMode() {
        int centre = mode_scroll.getScrollX() + mode_scroll.getWidth()/2;
        Mode nearest = null;
        int nearest_distance = Integer.MAX_VALUE;
        for(Map.Entry<Mode, TextView> entry : mode_views.entrySet()) {
            View view = entry.getValue();
            int distance = Math.abs(view.getLeft() + view.getWidth()/2 - centre);
            if( distance < nearest_distance ) {
                nearest_distance = distance;
                nearest = entry.getKey();
            }
        }
        return nearest;
    }

    /** While the carousel is scrolled, shows the mode in the centre (the one that will be
     *  selected when it stops) as selected.
     */
    private void updateModeCandidate() {
        Mode nearest = nearestCentreMode();
        if( nearest == null || nearest == mode_candidate )
            return;
        Mode old = mode_candidate;
        mode_candidate = nearest;
        for(Map.Entry<Mode, TextView> entry : mode_views.entrySet()) {
            Mode mode = entry.getKey();
            if( mode != old && mode != nearest )
                continue;
            TextView tab = entry.getValue();
            boolean on = mode == nearest;
            tab.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            animateTextColor(tab, on ? Color.WHITE : TAB_INACTIVE_COLOR);
        }
    }

    /** Called once the user has stopped scrolling the carousel: activate the centred mode. */
    private void onModeScrollSettled() {
        if( mode_scroll_touching || mode_views.isEmpty() )
            return;
        Mode nearest = nearestCentreMode();
        if( nearest == null )
            return;
        Preview preview = main_activity.getPreview();
        if( nearest != currentMode() && preview != null && !preview.isVideoRecording() && !preview.isOpeningCamera() ) {
            pulse(mode_views.get(nearest));
            selectMode(nearest);
        }
        else {
            Mode current = currentMode();
            centreMode(current);
            if( mode_candidate != current ) {
                Mode old = mode_candidate;
                mode_candidate = null;
                updateCandidateStyles(old, current);
            }
        }
    }

    private void updateCandidateStyles(Mode old, Mode now) {
        mode_candidate = now;
        TextView old_tab = old != null ? mode_views.get(old) : null;
        TextView new_tab = now != null ? mode_views.get(now) : null;
        if( old_tab != null && old != now ) {
            old_tab.setTypeface(Typeface.DEFAULT);
            animateTextColor(old_tab, TAB_INACTIVE_COLOR);
        }
        if( new_tab != null ) {
            new_tab.setTypeface(Typeface.DEFAULT_BOLD);
            animateTextColor(new_tab, Color.WHITE);
        }
    }

    private static void animateTextColor(final TextView view, int to) {
        ValueAnimator animator = ValueAnimator.ofArgb(view.getCurrentTextColor(), to);
        animator.setDuration(200);
        animator.addUpdateListener(animation -> view.setTextColor((int)animation.getAnimatedValue()));
        animator.start();
    }

    private void selectMode(Mode mode) {
        closeChooser();
        main_ui.closePopup();
        Preview preview = main_activity.getPreview();
        if( preview == null || preview.isVideoRecording() || mode == currentMode() )
            return;
        if( mode == Mode.VIDEO ) {
            main_activity.clickedSwitchVideo(null);
            return;
        }
        String value;
        switch( mode ) {
            case NR: value = "preference_photo_mode_noise_reduction"; break;
            case DRO: value = "preference_photo_mode_dro"; break;
            case HDR: value = "preference_photo_mode_hdr"; break;
            case PANORAMA: value = "preference_photo_mode_panorama"; break;
            case BURST: value = "preference_photo_mode_fast_burst"; break;
            case EXPO_BRACKETING: value = "preference_photo_mode_expo_bracketing"; break;
            case FOCUS_BRACKETING: value = "preference_photo_mode_focus_bracketing"; break;
            case X_AUTO: value = "preference_photo_mode_x_auto"; break;
            case X_HDR: value = "preference_photo_mode_x_hdr"; break;
            case X_NIGHT: value = "preference_photo_mode_x_night"; break;
            case X_BOKEH: value = "preference_photo_mode_x_bokeh"; break;
            case X_BEAUTY: value = "preference_photo_mode_x_beauty"; break;
            default: value = "preference_photo_mode_std"; break;
        }
        SharedPreferences sharedPreferences = prefs();
        sharedPreferences.edit().putString(PreferenceKeys.PhotoModePreferenceKey, value).apply();

        int extension = extensionOf(mode);
        if( extension >= 0 && !preview.isVideo() && !main_activity.supportsCameraExtension(extension) ) {
            Lens target = lensForExtension(extension);
            if( target != null && !preview.isOpeningCamera() ) {
                // AUTO stays selected if its camera runs the extension
                sharedPreferences.edit().putBoolean(SMART_AUTO_KEY, target == auto_lens).apply();
                main_activity.getApplicationInterface().getDrawPreview().updateSettings();
                main_ui.destroyPopup();
                switchToLens(target);
                return;
            }
        }

        String toast = str(mode.label);
        if( mode == Mode.HDR && !sharedPreferences.contains(PreferenceKeys.HDRInfoPreferenceKey) ) {
            main_ui.showInfoDialog(R.string.photo_mode_hdr, R.string.hdr_info, PreferenceKeys.HDRInfoPreferenceKey);
            toast = null;
        }
        else if( mode == Mode.PANORAMA && !sharedPreferences.contains(PreferenceKeys.PanoramaInfoPreferenceKey) ) {
            main_ui.showInfoDialog(R.string.photo_mode_panorama_full, R.string.panorama_info, PreferenceKeys.PanoramaInfoPreferenceKey);
            toast = null;
        }
        main_activity.getApplicationInterface().getDrawPreview().updateSettings(); // the photo mode is cached
        main_ui.destroyPopup();
        if( preview.isVideo() ) {
            // switching to photo mode picks up the new photo mode
            main_activity.clickedSwitchVideo(null);
        }
        else {
            main_activity.updateForSettings(true, toast, false, true);
        }
    }

    // ---------------------------------------------------------------- pin sheets

    private void closeSheet() {
        if( !views_created || open_sheet == Sheet.NONE )
            return;
        open_sheet = Sheet.NONE;
        sheet_editing = false;
        animateOut(sheet_scroll, () -> {
            if( open_sheet == Sheet.NONE )
                sheet_content.removeAllViews();
        });
    }

    private void toggleSheet(Sheet sheet) {
        if( open_sheet == sheet ) {
            closeSheet();
            return;
        }
        closeChooser();
        main_ui.closePopup();
        main_ui.closeExposureUI();
        open_sheet = sheet;
        sheet_editing = false;
        buildSheet();
        animateIn(sheet_scroll);
    }

    private void layoutSheet() {
        RelativeLayout.LayoutParams lp = resetParams(sheet_scroll, rootWidth() - dp(24), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        int bottom = open_sheet == Sheet.MODES ? last_navigation_gap + dp(52) : pill_bottom + dp(58);
        lp.bottomMargin = bottom;
        int max_h = rootHeight() - bottom - dp(56);
        sheet_content.measure(View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        lp.height = Math.min(sheet_content.getMeasuredHeight(), max_h);
        sheet_scroll.setLayoutParams(lp);
    }

    private TextView sheetText(String text, float size_sp, int color, boolean bold) {
        TextView view = new TextView(main_activity);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size_sp);
        view.setTextColor(color);
        view.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return view;
    }

    private void addSheetHeader(String title) {
        LinearLayout header = new LinearLayout(main_activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title_view = sheetText(title, 16, Color.WHITE, true);
        header.addView(title_view, new LinearLayout.LayoutParams(0, dp(44), 1.0f));
        title_view.setGravity(Gravity.CENTER_VERTICAL);
        TextView edit = sheetText(str(sheet_editing ? R.string.uifork_done : R.string.uifork_edit), 14, ACCENT_COLOR, true);
        edit.setGravity(Gravity.CENTER);
        edit.setPadding(dp(14), 0, dp(14), 0);
        edit.setBackground(roundedRect(TILE_COLOR, dp(999)));
        edit.setOnClickListener(v -> {
            sheet_editing = !sheet_editing;
            buildSheet();
        });
        addPressAnimation(edit);
        header.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        sheet_content.addView(header);
        if( sheet_editing ) {
            TextView hint = sheetText(str(R.string.uifork_edit_hint), 12, TAB_INACTIVE_COLOR, false);
            sheet_content.addView(hint);
        }
    }

    private void addSectionTitle(String title) {
        TextView view = sheetText(title.toUpperCase(Locale.getDefault()), 11, TAB_INACTIVE_COLOR, true);
        view.setLetterSpacing(0.08f);
        view.setPadding(dp(2), dp(12), 0, dp(6));
        sheet_content.addView(view);
    }

    private interface MoveListener {
        void move(String from_key, String to_key);
    }

    private static class Tile {
        final String key;
        final String label;
        final String value;
        final int icon;
        final boolean selected;
        final boolean pinned;
        final boolean locked;
        final View.OnClickListener on_use;
        final View.OnClickListener on_toggle_pin;
        final DebugInfo debug;

        Tile(String key, String label, String value, int icon, boolean selected, boolean pinned, boolean locked,
             View.OnClickListener on_use, View.OnClickListener on_toggle_pin, DebugInfo debug) {
            this.key = key;
            this.label = label;
            this.value = value;
            this.icon = icon;
            this.selected = selected;
            this.pinned = pinned;
            this.locked = locked;
            this.on_use = on_use;
            this.on_toggle_pin = on_toggle_pin;
            this.debug = debug;
        }
    }

    /** Lays out tiles four to a row. In edit mode tapping a tile pins/unpins it, and pinned
     *  tiles can be held and dragged onto another pinned tile to reorder them.
     */
    @SuppressWarnings("deprecation")
    private void addTiles(List<Tile> tiles, final MoveListener move_listener) {
        final int per_row = 4;
        LinearLayout row = null;
        for(int i=0;i<tiles.size();i++) {
            if( i % per_row == 0 ) {
                row = new LinearLayout(main_activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                sheet_content.addView(row);
            }
            final Tile tile = tiles.get(i);
            final LinearLayout cell = new LinearLayout(main_activity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(4), dp(8), dp(4), dp(8));
            final GradientDrawable background = roundedRect(tile.selected ? TILE_SELECTED_COLOR : TILE_COLOR, dp(16));
            cell.setBackground(background);
            if( tile.icon != 0 ) {
                ImageView icon = new ImageView(main_activity);
                icon.setImageResource(tile.icon);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));
            }
            else {
                TextView value = sheetText(tile.value, 15, Color.WHITE, true);
                value.setGravity(Gravity.CENTER);
                cell.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)));
            }
            TextView label = sheetText(tile.label, 11, tile.selected ? Color.WHITE : 0xFFCBD2E6, tile.selected);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if( sheet_editing ) {
                TextView badge = sheetText(tile.locked ? "•" : (tile.pinned ? "−" : "+"), 13, tile.pinned ? Color.BLACK : Color.WHITE, true);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(oval(tile.pinned ? ACCENT_COLOR : 0xFF4A5268));
                LinearLayout.LayoutParams badge_lp = new LinearLayout.LayoutParams(dp(20), dp(20));
                badge_lp.topMargin = dp(4);
                badge_lp.gravity = Gravity.CENTER_HORIZONTAL;
                cell.addView(badge, badge_lp);
                if( tile.locked )
                    badge.setAlpha(0.5f);
            }
            cell.setContentDescription(tile.label);
            cell.setOnClickListener(v -> {
                if( sheet_editing ) {
                    if( !tile.locked ) {
                        pulse(v);
                        tile.on_toggle_pin.onClick(v);
                    }
                }
                else {
                    tile.on_use.onClick(v);
                }
            });
            if( sheet_editing && tile.pinned && move_listener != null ) {
                // hold and drag a pinned tile onto another to reorder
                cell.setOnLongClickListener(v -> {
                    View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
                    if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N )
                        v.startDragAndDrop(ClipData.newPlainText("tile", tile.key), shadow, tile.key, 0);
                    else
                        v.startDrag(ClipData.newPlainText("tile", tile.key), shadow, tile.key, 0);
                    v.setAlpha(0.4f);
                    return true;
                });
                cell.setOnDragListener((v, event) -> {
                    switch( event.getAction() ) {
                        case DragEvent.ACTION_DRAG_STARTED:
                            return event.getLocalState() instanceof String;
                        case DragEvent.ACTION_DRAG_ENTERED:
                            background.setStroke(dp(2), ACCENT_COLOR);
                            v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
                            return true;
                        case DragEvent.ACTION_DRAG_EXITED:
                            background.setStroke(0, Color.TRANSPARENT);
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                            return true;
                        case DragEvent.ACTION_DROP: {
                            background.setStroke(0, Color.TRANSPARENT);
                            String from = (String)event.getLocalState();
                            if( from != null && !from.equals(tile.key) ) {
                                final String to = tile.key;
                                // apply after the drag finishes, as the drop rebuilds the tiles
                                sheet_content.post(() -> move_listener.move(from, to));
                            }
                            return true;
                        }
                        case DragEvent.ACTION_DRAG_ENDED:
                            background.setStroke(0, Color.TRANSPARENT);
                            v.setAlpha(1.0f);
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                            return true;
                        default:
                            return true;
                    }
                });
            }
            else if( tile.debug != null ) {
                attachDebug(cell, "Tile " + tile.key, tile.debug);
            }
            addPressAnimation(cell);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(cell, lp);
        }
        // pad the last row so tiles keep the same width
        if( row != null ) {
            for(int i=tiles.size() % per_row;i>0 && i<per_row;i++) {
                row.addView(new View(main_activity), new LinearLayout.LayoutParams(0, 1, 1.0f));
            }
        }
    }

    private void buildSheet() {
        if( open_sheet == Sheet.NONE )
            return;
        sheet_content.removeAllViews();
        if( open_sheet == Sheet.QUICK )
            buildQuickSheet();
        else
            buildModesSheet();
        if( portrait_active )
            layoutSheet();
    }

    private static <T> void moveItem(List<T> list, T from, T to) {
        int to_index = list.indexOf(to);
        if( !list.remove(from) || to_index < 0 )
            return;
        list.add(Math.min(to_index, list.size()), from);
    }

    private Tile quickTile(final QuickItem item, boolean is_pinned) {
        return new Tile(item.name(), str(quickLabel(item)), quickText(item), quickIcon(item), quickActive(item), is_pinned, false,
                v -> {
                    closeSheet();
                    if( isAvailable(item) )
                        activate(item, v);
                },
                v -> {
                    List<QuickItem> items = pinnedQuickItems();
                    if( items.contains(item) )
                        items.remove(item);
                    else
                        items.add(item);
                    setPinnedQuickItems(items);
                    rebuildQuickBar();
                    buildSheet();
                },
                () -> quickDebug(item));
    }

    private void buildQuickSheet() {
        addSheetHeader(str(R.string.uifork_quick_settings));
        final List<QuickItem> pinned = pinnedQuickItems();
        List<Tile> pinned_tiles = new ArrayList<>();
        List<Tile> other_tiles = new ArrayList<>();
        for(QuickItem item : pinned) {
            pinned_tiles.add(quickTile(item, true));
        }
        for(QuickItem item : QuickItem.values()) {
            if( !pinned.contains(item) && isAvailable(item) )
                other_tiles.add(quickTile(item, false));
        }
        addSectionTitle(str(R.string.uifork_pinned));
        addTiles(pinned_tiles, (from, to) -> {
            List<QuickItem> items = pinnedQuickItems();
            moveItem(items, QuickItem.valueOf(from), QuickItem.valueOf(to));
            setPinnedQuickItems(items);
            rebuildQuickBar();
            buildSheet();
        });
        if( !other_tiles.isEmpty() ) {
            addSectionTitle(str(R.string.uifork_unpinned));
            addTiles(other_tiles, null);
        }

        // everything else Open Camera offers, in its own (restyled) options sheet
        TextView all = sheetText(str(R.string.uifork_all_options), 14, Color.WHITE, true);
        all.setGravity(Gravity.CENTER);
        all.setBackground(roundedRect(TILE_COLOR, dp(999)));
        all.setOnClickListener(v -> {
            closeSheet();
            main_activity.clickedPopupSettings(v);
        });
        addPressAnimation(all);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(14);
        sheet_content.addView(all, lp);
    }

    private Tile modeTile(final Mode mode, boolean is_pinned, Mode current) {
        String label = str(mode.label);
        return new Tile(mode.name(), label, label.substring(0, 1), 0, mode == current, is_pinned, mode.alwaysPinned(),
                v -> {
                    closeSheet();
                    selectMode(mode);
                },
                v -> {
                    List<Mode> modes = pinnedModes();
                    if( modes.contains(mode) )
                        modes.remove(mode);
                    else
                        modes.add(mode);
                    setPinnedModes(modes);
                    refreshModes();
                    buildSheet();
                },
                () -> modeDebug(mode));
    }

    private void buildModesSheet() {
        addSheetHeader(str(R.string.uifork_photo_modes));
        final List<Mode> pinned = pinnedModes();
        Mode current = currentMode();
        List<Tile> pinned_tiles = new ArrayList<>();
        List<Tile> other_tiles = new ArrayList<>();
        for(Mode mode : pinned) {
            if( isSupported(mode) )
                pinned_tiles.add(modeTile(mode, true, current));
        }
        for(Mode mode : Mode.values()) {
            if( !pinned.contains(mode) && isSupported(mode) )
                other_tiles.add(modeTile(mode, false, current));
        }
        addSectionTitle(str(R.string.uifork_pinned));
        addTiles(pinned_tiles, (from, to) -> {
            List<Mode> modes = pinnedModes();
            moveItem(modes, Mode.valueOf(from), Mode.valueOf(to));
            setPinnedModes(modes);
            refreshModes();
            buildSheet();
        });
        if( !other_tiles.isEmpty() ) {
            addSectionTitle(str(R.string.uifork_unpinned));
            addTiles(other_tiles, null);
        }
    }

    // ---------------------------------------------------------------- lens carousel

    private static class Lens {
        final int logical_id;
        final String camera2_id; // the logical camera's Camera2 id
        final String physical_id;
        final float view_angle;
        final boolean auto;
        final boolean part_of_auto;
        double zoom = Double.NaN;
        String zoom_source = "none";
        Optics optics;
        String type_name; // Ultra wide, Wide, Tele, Macro, Mono, Selfie...
        final Set<Integer> extensions = new HashSet<>(); // Camera2 extensions (night etc) this camera supports
        String label;
        String sublabel;

        Lens(int logical_id, String camera2_id, String physical_id, float view_angle, boolean auto, boolean part_of_auto) {
            this.logical_id = logical_id;
            this.camera2_id = camera2_id;
            this.physical_id = physical_id;
            this.view_angle = view_angle;
            this.auto = auto;
            this.part_of_auto = part_of_auto;
        }

        @Override
        public String toString() {
            return "Lens{logical_id=" + logical_id + ", camera2_id=" + camera2_id + ", physical_id=" + physical_id +
                    ", view_angle=" + view_angle + ", zoom=" + zoom + " (" + zoom_source + ")" +
                    (optics != null && !Double.isNaN(optics.eq35()) ? String.format(Locale.US, ", %.1fmm equiv", optics.eq35()) : "") +
                    ", auto=" + auto + ", part_of_auto=" + part_of_auto + ", extensions=" + extensions +
                    ", label=" + label + (sublabel != null ? " " + sublabel : "") + "}";
        }
    }

    /** Optics of a camera or physical lens, from its sensor and lens data: the focal length
     *  relative to the width of the 4:3 area of the sensor that photos are taken from. Ratios of
     *  this between lenses give their zoom factors, allowing for sensors of different sizes and
     *  shapes and for pixels outside the active area (unlike a plain view angle, which uses the
     *  whole sensor width).
     */
    static final class Optics {
        double focal_mm = Double.NaN;
        double crop_width_mm = Double.NaN;
        double norm_mm = Double.NaN; // focal length / crop width, from millimetres
        double norm_intrinsic = Double.NaN; // focal length / crop width, from the calibrated focal length in pixels
        boolean front;
        float min_focus_diopters = -1.0f; // 0 = fixed focus
        float hyperfocal_diopters = -1.0f;
        boolean mono;
        double max_mp = Double.NaN;
        String summary = "";

        boolean fixedFocus() {
            return min_focus_diopters == 0.0f;
        }

        /** A dedicated close-up camera: fixed focus set close, or a low resolution lens that
         *  focuses within 5cm (ultra wides that also focus close are not macro cameras).
         */
        boolean macro(double zoom) {
            if( fixedFocus() && hyperfocal_diopters >= 10.0f )
                return true;
            return min_focus_diopters >= 20.0f && !Double.isNaN(max_mp) && max_mp <= 5.5 && (Double.isNaN(zoom) || zoom >= 0.8);
        }

        double norm() {
            return !Double.isNaN(norm_intrinsic) ? norm_intrinsic : norm_mm;
        }

        String source() {
            return !Double.isNaN(norm_intrinsic) ? "calibration" : !Double.isNaN(norm_mm) ? "focal length/sensor size" : "none";
        }

        /** 35mm-equivalent focal length (by diagonal) for 4:3 photos. */
        double eq35() {
            double norm = norm();
            return Double.isNaN(norm) ? Double.NaN : norm * 43.27 / 1.25;
        }
    }

    private static final String LENS_LABELS_KEY = "uifork_lens_labels";

    /** Camera2 has no lens name, so the type is worked out from the lens's data: colour filter
     *  (mono), focus range and resolution (macro), and zoom factor (ultra wide/wide/tele).
     */
    private String lensTypeName(Lens lens) {
        Optics o = lens.optics;
        if( o != null && o.mono )
            return str(R.string.uifork_lens_mono);
        if( o != null && o.macro(lens.zoom) )
            return str(R.string.uifork_lens_macro);
        if( Double.isNaN(lens.zoom) )
            return null;
        if( o != null && o.front )
            return str(lens.zoom < 0.8 ? R.string.uifork_lens_wide_selfie : R.string.uifork_lens_selfie);
        if( lens.zoom < 0.8 )
            return str(R.string.uifork_lens_ultra_wide);
        if( lens.zoom < 1.4 )
            return str(R.string.uifork_lens_wide);
        return str(R.string.uifork_lens_tele);
    }

    /** Width of the largest 4:3 area of a width x height area. */
    private static double cropWidth43(double width, double height) {
        return width / height > 4.0/3.0 ? height * 4.0/3.0 : width;
    }

    static Optics optics(CameraManager manager, String camera_id) {
        Optics optics = new Optics();
        if( manager == null || camera_id == null )
            return optics;
        StringBuilder sb = new StringBuilder();
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera_id);
            float [] focals = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            android.util.SizeF physical = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            android.util.Size pixel_array = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
            android.graphics.Rect active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            android.graphics.Rect pre_correction = null;
            float [] intrinsic = null;
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                pre_correction = characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
                intrinsic = characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
            }
            if( focals != null && focals.length > 0 )
                optics.focal_mm = focals[0];
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            optics.front = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
            Float min_focus = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
            Float hyperfocal = characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE);
            if( min_focus != null )
                optics.min_focus_diopters = min_focus;
            if( hyperfocal != null )
                optics.hyperfocal_diopters = hyperfocal;
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                Integer cfa = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
                optics.mono = cfa != null && (cfa == CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO || cfa == CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR);
            }
            android.hardware.camera2.params.StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            android.util.Size [] jpeg = map != null ? map.getOutputSizes(android.graphics.ImageFormat.JPEG) : null;
            if( jpeg != null ) {
                for(android.util.Size size : jpeg)
                    optics.max_mp = Double.isNaN(optics.max_mp) ? size.getWidth()*(double)size.getHeight()/1.0e6 : Math.max(optics.max_mp, size.getWidth()*(double)size.getHeight()/1.0e6);
            }
            sb.append(String.format(Locale.US, "facing front %b, minimum focus %.2f dioptres (%s), hyperfocal %.2f dioptres, mono %b, largest JPEG %.1f MP\n",
                    optics.front, optics.min_focus_diopters,
                    optics.fixedFocus() ? "fixed focus" : optics.min_focus_diopters > 0.0f ? String.format(Locale.US, "%.1f cm", 100.0f/optics.min_focus_diopters) : "unknown",
                    optics.hyperfocal_diopters, optics.mono, optics.max_mp));
            sb.append("focal lengths: ").append(java.util.Arrays.toString(focals)).append(" mm\n");
            sb.append("sensor physical size: ").append(physical).append(" mm, pixel array ").append(pixel_array)
                    .append(", active array ").append(active).append(", pre-correction active array ").append(pre_correction).append('\n');
            if( physical != null && pixel_array != null && active != null && pixel_array.getWidth() > 0 ) {
                double mm_per_pixel = physical.getWidth() / (double)pixel_array.getWidth();
                optics.crop_width_mm = cropWidth43(active.width(), active.height()) * mm_per_pixel;
                double diagonal = Math.hypot(physical.getWidth(), physical.getHeight());
                sb.append(String.format(Locale.US, "sensor diagonal %.2f mm (type ~1/%.2f\"), pixel pitch %.3f um, 4:3 crop width %.2f mm\n",
                        diagonal, 16.0/diagonal, mm_per_pixel*1000.0, optics.crop_width_mm));
                if( !Double.isNaN(optics.focal_mm) && optics.crop_width_mm > 0.0 )
                    optics.norm_mm = optics.focal_mm / optics.crop_width_mm;
            }
            sb.append("lens intrinsic calibration [fx, fy, cx, cy, s]: ").append(java.util.Arrays.toString(intrinsic)).append('\n');
            if( intrinsic != null && intrinsic.length >= 2 && intrinsic[0] > 0.0f ) {
                android.graphics.Rect frame = pre_correction != null ? pre_correction : active;
                if( frame != null && frame.width() > 0 ) {
                    double norm = intrinsic[0] / cropWidth43(frame.width(), frame.height());
                    if( !Double.isNaN(optics.norm_mm) && Math.abs(norm/optics.norm_mm - 1.0) > 0.25 ) {
                        sb.append(String.format(Locale.US, "calibrated focal length (%.4f) disagrees with focal length/sensor size (%.4f) by over 25%% - not used\n", norm, optics.norm_mm));
                    }
                    else {
                        optics.norm_intrinsic = norm;
                    }
                }
            }
            sb.append(String.format(Locale.US, "focal/4:3 width: from mm %.4f, from calibration %.4f -> using %s; 35mm equivalent %.1f mm\n",
                    optics.norm_mm, optics.norm_intrinsic, optics.source(), optics.eq35()));
        }
        catch(Throwable e) {
            sb.append("failed: ").append(e).append('\n');
        }
        optics.summary = sb.toString();
        return optics;
    }

    private float viewAngle(String camera_id) {
        if( camera_id == null )
            return -1.0f;
        CameraControllerManager.CameraInfo info = new CameraControllerManager.CameraInfo();
        main_activity.getPreview().getCameraControllerManager().getDescription(info, main_activity, camera_id, false, false);
        return info.view_angle != null ? info.view_angle.getWidth() : -1.0f;
    }

    /** The physical lenses behind a logical multi-camera (empty if it isn't one). */
    private static Set<String> physicalIds(CameraManager manager, String camera_id) {
        if( manager == null || camera_id == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P )
            return Collections.emptySet();
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera_id);
            Set<String> ids = characteristics.getPhysicalCameraIds();
            return ids.size() > 1 ? ids : Collections.emptySet();
        }
        catch(Throwable e) {
            MyDebug.logStackTrace(TAG, "failed to get physical camera ids", e);
            return Collections.emptySet();
        }
    }

    private boolean smartAutoPref() {
        return prefs().getBoolean(SMART_AUTO_KEY, true);
    }

    /** Whether this lens is the camera that is open right now. */
    private boolean isOpen(Lens lens) {
        if( lens == null )
            return false;
        Preview preview = main_activity.getPreview();
        String current_physical = main_activity.getApplicationInterface().getCameraIdSPhysicalPref();
        return lens.logical_id == preview.getCameraId() &&
                (lens.physical_id == null ? current_physical == null : lens.physical_id.equals(current_physical));
    }

    /** "app": AUTO switches lens itself at each lens's minimum zoom, streaming the lenses
     *  directly; "phone": the phone's multi-camera picks between the lenses it uses, and AUTO only
     *  hands off to the highest-power lens the phone doesn't use.
     */
    private boolean appSwitching() {
        return !"phone".equals(prefs().getString(AUTO_SWITCHING_KEY, "app")) && !extensionModeActive();
    }

    /** The Camera2 extension a photo mode uses, or -1. */
    private static int extensionOf(Mode mode) {
        switch( mode ) {
            case X_AUTO: return CameraExtensionCharacteristics.EXTENSION_AUTOMATIC;
            case X_HDR: return CameraExtensionCharacteristics.EXTENSION_HDR;
            case X_NIGHT: return CameraExtensionCharacteristics.EXTENSION_NIGHT;
            case X_BOKEH: return CameraExtensionCharacteristics.EXTENSION_BOKEH;
            case X_BEAUTY: return CameraExtensionCharacteristics.EXTENSION_BEAUTY;
            default: return -1;
        }
    }

    /** Whether the photo mode in use is a camera extension (night etc). */
    private boolean extensionModeActive() {
        Preview preview = main_activity.getPreview();
        return preview != null && !preview.isVideo() && extensionOf(currentMode()) >= 0;
    }

    /** A lens facing the same way that supports the extension: AUTO's camera if it does, else
     *  the one nearest 1x. Null if none does.
     */
    private Lens lensForExtension(int extension) {
        if( extension < 0 )
            return null;
        if( auto_lens != null && auto_lens.extensions.contains(extension) )
            return auto_lens;
        Lens best = null;
        for(Lens lens : last_lenses) {
            if( !lens.extensions.contains(extension) )
                continue;
            double distance = Double.isNaN(lens.zoom) ? 99.0 : Math.abs(Math.log(lens.zoom));
            double best_distance = best == null ? Double.MAX_VALUE : Double.isNaN(best.zoom) ? 99.0 : Math.abs(Math.log(best.zoom));
            if( distance < best_distance )
                best = lens;
        }
        return best;
    }

    /** The open lens, from the carousel's lenses. */
    private Lens openLens() {
        for(Lens lens : last_lenses) {
            if( isOpen(lens) )
                return lens;
        }
        return null;
    }

    private static double factorOf(Lens lens) {
        return lens == null || lens.auto || Double.isNaN(lens.zoom) ? 1.0 : lens.zoom;
    }

    /** Whether AUTO is in charge: the auto camera, or one of the lenses it switches between, is open. */
    private boolean smartAutoActive() {
        if( !smartAutoPref() || auto_lens == null )
            return false;
        if( isOpen(auto_lens) )
            return true;
        for(Lens lens : auto_chain) {
            if( isOpen(lens) )
                return true;
        }
        return false;
    }

    /** The lens AUTO uses for an overall zoom (relative to the main lens) of total: the highest
     *  power lens whose own zoom is at or below it, so each lens covers exactly its own minimum
     *  up to the next lens's minimum. This depends only on the zoom level (never on which lens
     *  happens to be open), so every zoom level has exactly one lens.
     */
    private Lens autoTargetFor(double total) {
        if( auto_chain.isEmpty() )
            return auto_lens;
        Lens target = auto_chain.get(0);
        for(int i=1;i<auto_chain.size();i++) {
            if( total >= auto_chain.get(i).zoom )
                target = auto_chain.get(i);
        }
        return target;
    }

    // a switch to the lens for the current zoom waits until the zoom has stayed in that lens's
    // range for AUTO_SETTLE_MS, unless it is well inside it, so a finger resting on a boundary
    // doesn't switch lens back and forth; the lens is always the right one once the zoom settles
    private static final long AUTO_SETTLE_MS = 250;
    private static final double AUTO_IMMEDIATE_MARGIN = Math.log(1.10);
    private Lens settle_target;
    private double settle_total = Double.NaN;
    private double settle_lo, settle_hi;
    private final Runnable settle_runnable = this::onAutoSettled;

    /** Moves AUTO to the lens for the overall zoom total, now or once it settles. */
    private void autoSwitchFor(double total, Lens open, double range_lo, double range_hi) {
        Lens target = autoTargetFor(total);
        settle_total = total;
        settle_lo = range_lo;
        settle_hi = range_hi;
        if( target == null || target == open || (target.auto && open != null && open.auto) ) {
            settle_target = null;
            root().removeCallbacks(settle_runnable);
            return;
        }
        // how far past the boundary between the open lens and the target the zoom is
        boolean up = factorOf(target) > factorOf(open);
        double boundary = up ? factorOf(target) : factorOf(open);
        double past = Math.abs(Math.log(total) - Math.log(boundary));
        if( past >= AUTO_IMMEDIATE_MARGIN ) {
            settle_target = null;
            root().removeCallbacks(settle_runnable);
            handOffTo(target, open, total, range_lo, range_hi);
        }
        else if( settle_target != target ) {
            settle_target = target;
            root().removeCallbacks(settle_runnable);
            root().postDelayed(settle_runnable, AUTO_SETTLE_MS);
        }
    }

    private void onAutoSettled() {
        Lens target = settle_target;
        settle_target = null;
        if( target == null || handoff_in_progress || Double.isNaN(settle_total) || !smartAutoActive() )
            return;
        Lens open = openLens();
        if( autoTargetFor(settle_total) == target && target != open )
            handOffTo(target, open, settle_total, settle_lo, settle_hi);
    }

    private void handOffTo(Lens target, Lens open, double total, double range_lo, double range_hi) {
        // going up, start the new lens at no less than its own minimum
        boolean up = factorOf(target) > factorOf(open);
        double carried = up ? Math.max(total, factorOf(target)) : total;
        handOff(target, Math.max(range_lo, Math.min(range_hi, carried)));
    }

    /** The lens AUTO opens when it is tapped. */
    private Lens autoEntryLens() {
        if( !appSwitching() || auto_chain.isEmpty() || auto_chain.get(0).auto )
            return auto_lens;
        return autoTargetFor(1.0);
    }

    private boolean isSelected(Lens lens) {
        if( smartAutoActive() )
            return lens == auto_lens;
        return isOpen(lens);
    }

    private static String largestSize(android.util.Size [] sizes) {
        if( sizes == null || sizes.length == 0 )
            return "none";
        android.util.Size best = null;
        for(android.util.Size size : sizes) {
            if( best == null || (long)size.getWidth()*size.getHeight() > (long)best.getWidth()*best.getHeight() )
                best = size;
        }
        return best.getWidth() + "x" + best.getHeight() + String.format(Locale.US, " (%.1f MP)", best.getWidth()*(double)best.getHeight()/1.0e6);
    }

    /** What Camera2 says about the highest resolution an app can capture from this camera. */
    private String resolutionSummary(String camera_id) {
        if( camera_id == null )
            return "";
        StringBuilder sb = new StringBuilder("--- resolution summary ---\n");
        try {
            CameraManager manager = (CameraManager)main_activity.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera_id);
            android.hardware.camera2.params.StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            sb.append("pixel array: ").append(characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)).append('\n');
            sb.append("largest JPEG (normal outputs): ").append(largestSize(map != null ? map.getOutputSizes(android.graphics.ImageFormat.JPEG) : null)).append('\n');
            sb.append("largest JPEG (high resolution outputs): ").append(largestSize(map != null ? map.getHighResolutionOutputSizes(android.graphics.ImageFormat.JPEG) : null)).append('\n');
            boolean ultra_high_resolution = false;
            int [] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            if( capabilities != null ) {
                for(int capability : capabilities) {
                    if( capability == 16 ) // REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR
                        ultra_high_resolution = true;
                }
            }
            sb.append("ultra high resolution sensor capability: ").append(ultra_high_resolution).append('\n');
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ) {
                android.hardware.camera2.params.StreamConfigurationMap max_map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION);
                sb.append("largest JPEG (maximum resolution mode): ").append(largestSize(max_map != null ? max_map.getOutputSizes(android.graphics.ImageFormat.JPEG) : null)).append('\n');
                sb.append("pixel array (maximum resolution mode): ").append(characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION)).append('\n');
                sb.append("binning factor: ").append(characteristics.get(CameraCharacteristics.SENSOR_INFO_BINNING_FACTOR)).append('\n');
            }
            sb.append("verdict: ").append(ultra_high_resolution ?
                    "the full-resolution mode is offered to apps (needs SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION)" :
                    "apps are limited to the largest normal/high resolution output above - the device doesn't offer its full-resolution mode through Camera2").append('\n');
        }
        catch(Throwable e) {
            sb.append("failed: ").append(e).append('\n');
        }
        return sb.toString();
    }

    private String lensDebug(Lens lens) {
        StringBuilder sb = new StringBuilder();
        sb.append(lens).append('\n');
        sb.append("open: ").append(isOpen(lens)).append(", selected: ").append(isSelected(lens)).append('\n');
        sb.append("smart auto: pref ").append(smartAutoPref()).append(", active ").append(smartAutoActive())
                .append(", auto lens ").append(auto_lens != null ? auto_lens.label : "none")
                .append(", hands off to ").append(tele_lens != null ? tele_lens.label + " (lens " + tele_lens.physical_id + ")" : "none").append('\n');
        sb.append("AUTO switching: ").append(appSwitching() ? "app (each lens from its minimum)" : "phone").append(", chain:");
        for(Lens chain_lens : auto_chain)
            sb.append(' ').append(chain_lens.auto ? "AUTO" : zoomLabel(chain_lens.zoom) + "(" + chain_lens.physical_id + ")");
        sb.append('\n');
        sb.append("how it is opened: ").append(lens.physical_id != null ?
                "physical lens " + lens.physical_id + " streamed through logical camera " + lens.camera2_id + " (no hardware zoom - software zoom)" :
                lens.auto ? "logical multi-camera " + lens.camera2_id + " (hardware zoom; the driver picks lenses, AUTO also hands off to the highest-power lens)" :
                "standalone camera " + lens.camera2_id + " (hardware zoom on this lens only)").append('\n');
        sb.append("all lenses: \n");
        for(Lens other : last_lenses)
            sb.append("  ").append(other).append('\n');
        String id = lens.physical_id != null ? lens.physical_id : lens.camera2_id;
        sb.append("\n--- optics (zoom factors relative to ").append(auto_lens != null ? "the AUTO camera" : "the first camera").append(") ---\n");
        for(Lens other : last_lenses) {
            double view_angle_zoom = Double.NaN;
            Lens ref = auto_lens != null ? auto_lens : (last_lenses.isEmpty() ? null : last_lenses.get(0));
            if( ref != null && ref.view_angle > 0.0f && other.view_angle > 0.0f )
                view_angle_zoom = Math.tan(Math.toRadians(ref.view_angle)/2.0) / Math.tan(Math.toRadians(other.view_angle)/2.0);
            sb.append(String.format(Locale.US, "  %s%s [type %s]: %.3fx via %s (view angle alone: %.3fx), %.1fmm equiv\n",
                    other.label, other.sublabel != null ? " " + other.sublabel : "", other.type_name, other.zoom, other.zoom_source, view_angle_zoom,
                    other.optics != null ? other.optics.eq35() : Double.NaN));
        }
        if( lens.optics != null )
            sb.append(lens.optics.summary);
        sb.append('\n').append(resolutionSummary(id));
        sb.append("\n--- characteristics of ").append(lens.physical_id != null ? "physical " + lens.physical_id : "camera " + lens.camera2_id).append(" ---\n");
        sb.append(characteristicsDump(id));
        return sb.toString();
    }

    /** Rebuilds the lens carousel: every lens facing the same way as the open camera, whichever
     *  of them is open, so the carousel doesn't change as lenses are selected. A sensor that is
     *  exposed both as a standalone camera and as a lens of a multi-camera gets a chip for each,
     *  labelled with its camera/lens id, as they behave differently (only the standalone camera
     *  supports hardware zoom). The output resolution toggle sits first. Called whenever a camera
     *  is opened.
     */
    void rebuildLensChips() {
        createViewsIfNeeded();
        restorePreviewAfterLensSwitch();
        lens_row.removeAllViews();
        lens_fixed.removeAllViews();
        preset_chip = null;
        lens_count = 0;
        last_lenses.clear();
        selected_lens_zoom = 1.0;
        selected_lens_auto = false;
        auto_lens_zooms.clear();
        auto_lens = null;
        tele_lens = null;
        Preview preview = main_activity.getPreview();
        if( preview == null || preview.getCameraControllerManager() == null ) {
            handoff_in_progress = false;
            syncVisibility();
            return;
        }
        try {
            int current_id = preview.getCameraId();
            List<Integer> logical_ids = main_activity.getSameFacingLogicalCameras(current_id);

            CameraManager manager = null;
            String [] camera2_ids = null;
            if( preview.usingCamera2API() ) {
                manager = (CameraManager)main_activity.getSystemService(Context.CAMERA_SERVICE);
                camera2_ids = manager.getCameraIdList();
            }

            List<Lens> lenses = new ArrayList<>();
            Lens first_auto = null;
            for(int logical_id : logical_ids) {
                String camera2_id = camera2_ids != null && logical_id < camera2_ids.length ? camera2_ids[logical_id] : null;
                Set<String> physical = physicalIds(manager, camera2_id);
                Lens logical = new Lens(logical_id, camera2_id, null, viewAngle(camera2_id), !physical.isEmpty(), false);
                lenses.add(logical);
                if( logical.auto && first_auto == null )
                    first_auto = logical;
                for(String physical_id : physical) {
                    lenses.add(new Lens(logical_id, camera2_id, physical_id, viewAngle(physical_id), false, true));
                }
            }

            // zoom factors are relative to the auto camera if there is one, else the first camera
            Lens reference = first_auto != null ? first_auto : (lenses.isEmpty() ? null : lenses.get(0));
            for(Lens lens : lenses) {
                lens.optics = optics(manager, lens.physical_id != null ? lens.physical_id : lens.camera2_id);
                // extensions only run on a whole camera, not a physical lens streamed on its own
                if( manager != null && lens.physical_id == null && lens.camera2_id != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ) {
                    try {
                        CameraExtensionCharacteristics extension_characteristics = manager.getCameraExtensionCharacteristics(lens.camera2_id);
                        lens.extensions.addAll(extension_characteristics.getSupportedExtensions());
                    }
                    catch(Throwable e) {
                        MyDebug.logStackTrace(TAG, "failed to get camera extensions", e);
                    }
                }
            }
            Optics ref_optics = reference != null ? reference.optics : null;
            float reference_angle = reference != null ? reference.view_angle : -1.0f;
            for(Lens lens : lenses) {
                // use the same measure for both lenses: calibrated focal lengths, else focal
                // length/sensor size, else (as stock Open Camera) the horizontal view angle
                Optics o = lens.optics;
                if( ref_optics != null && o != null && !Double.isNaN(ref_optics.norm_intrinsic) && !Double.isNaN(o.norm_intrinsic) ) {
                    lens.zoom = o.norm_intrinsic / ref_optics.norm_intrinsic;
                    lens.zoom_source = "calibration";
                }
                else if( ref_optics != null && o != null && !Double.isNaN(ref_optics.norm_mm) && !Double.isNaN(o.norm_mm) ) {
                    lens.zoom = o.norm_mm / ref_optics.norm_mm;
                    lens.zoom_source = "focal length/sensor size";
                }
                else if( reference_angle > 0.0f && lens.view_angle > 0.0f ) {
                    lens.zoom = Math.tan(Math.toRadians(reference_angle)/2.0) / Math.tan(Math.toRadians(lens.view_angle)/2.0);
                    lens.zoom_source = "view angle";
                }
            }

            // the auto camera's lenses, for the zoom ruler, and the telephoto AUTO hands off to
            auto_lens = first_auto;
            for(Lens lens : lenses) {
                if( first_auto != null && lens.part_of_auto && lens.logical_id == first_auto.logical_id && !Double.isNaN(lens.zoom) ) {
                    auto_lens_zooms.add(lens.zoom);
                    if( lens.zoom > 1.5 && (tele_lens == null || lens.zoom > tele_lens.zoom) )
                        tele_lens = lens;
                }
            }
            Collections.sort(auto_lens_zooms);

            // the lenses AUTO switches between
            auto_chain.clear();
            if( first_auto != null ) {
                List<Lens> physical_lenses = new ArrayList<>();
                for(Lens lens : lenses) {
                    if( lens.part_of_auto && lens.logical_id == first_auto.logical_id && !Double.isNaN(lens.zoom) )
                        physical_lenses.add(lens);
                }
                Collections.sort(physical_lenses, (a, b) -> Double.compare(a.zoom, b.zoom));
                if( appSwitching() && physical_lenses.size() >= 2 ) {
                    auto_chain.addAll(physical_lenses);
                }
                else {
                    auto_chain.add(first_auto);
                    if( tele_lens != null && !extensionModeActive() )
                        auto_chain.add(tele_lens);
                }
            }

            Collections.sort(lenses, (a, b) -> {
                if( a.auto != b.auto )
                    return a.auto ? -1 : 1;
                boolean a_nan = Double.isNaN(a.zoom);
                boolean b_nan = Double.isNaN(b.zoom);
                if( a_nan || b_nan ) {
                    if( a_nan && b_nan )
                        return Integer.compare(a.logical_id, b.logical_id);
                    return a_nan ? 1 : -1;
                }
                int compare = Double.compare(a.zoom, b.zoom);
                if( compare != 0 )
                    return compare;
                // exactly the same power: lenses of the multi-camera first, then standalone cameras
                compare = Boolean.compare(!a.part_of_auto, !b.part_of_auto);
                return compare != 0 ? compare : Integer.compare(a.logical_id, b.logical_id);
            });

            // every lens chip is the same design: its name (Ultra wide, Wide, Tele, Macro...,
            // worked out from the lens's data) over its exact power to 1 decimal place (from the
            // sensor and lens data); with zoom labels, or no name, just the power
            boolean names = !"zoom".equals(prefs().getString(LENS_LABELS_KEY, "names"));
            for(Lens lens : lenses) {
                lens.type_name = lens.auto ? null : lensTypeName(lens);
                lens.sublabel = null;
                if( lens.auto )
                    lens.label = str(R.string.uifork_lens_auto);
                else if( Double.isNaN(lens.zoom) )
                    lens.label = names && lens.type_name != null ? lens.type_name : String.valueOf(lens.logical_id);
                else if( names && lens.type_name != null ) {
                    lens.label = lens.type_name;
                    lens.sublabel = powerLabel(lens.zoom);
                }
                else
                    lens.label = powerLabel(lens.zoom);
            }
            if( extensionModeActive() ) {
                String extension_label = str(currentMode().label);
                for(Lens lens : lenses) {
                    if( lens.physical_id == null && isOpen(lens) )
                        lens.sublabel = extension_label;
                }
            }
            last_lenses.addAll(lenses);
            int chips = 0;
            if( !preview.isVideo() && main_activity.getApplicationInterface().getPhotoMode() != MyApplicationInterface.PhotoMode.Panorama ) {
                addPresetChip();
                chips++;
            }
            View focus_chip = null;
            for(Lens lens : lenses) {
                boolean selected = isSelected(lens);
                if( selected ) {
                    selected_lens_auto = lens.auto;
                    selected_lens_zoom = lens.auto || Double.isNaN(lens.zoom) ? 1.0 : lens.zoom;
                }
                if( lenses.size() > 1 ) {
                    View chip = addLensChip(lens, selected);
                    chips++;
                    if( !lens.auto && (selected || inUseByAuto(lens)) )
                        focus_chip = chip;
                }
            }
            if( lens_fixed.getChildCount() > 0 && lens_row.getChildCount() > 0 ) {
                View divider = new View(main_activity);
                divider.setBackgroundColor(0x40FFFFFF);
                LinearLayout.LayoutParams divider_lp = new LinearLayout.LayoutParams(dp(1), dp(22));
                divider_lp.setMargins(dp(3), 0, dp(3), 0);
                divider_lp.gravity = Gravity.CENTER_VERTICAL;
                lens_fixed.addView(divider, divider_lp);
            }
            if( focus_chip != null ) {
                final View chip = focus_chip;
                lens_scroll.post(() -> {
                    int x = chip.getLeft() + chip.getWidth()/2 - lens_scroll.getWidth()/2;
                    lens_scroll.smoothScrollTo(Math.max(0, x), 0);
                });
            }
            lens_count = chips;
        }
        catch(Throwable e) {
            // camera drivers can throw a variety of exceptions; the carousel is optional
            MyDebug.logStackTrace(TAG, "failed to build lens carousel", e);
            lens_row.removeAllViews();
        lens_fixed.removeAllViews();
        preset_chip = null;
            lens_count = 0;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "lens_count: " + lens_count);
        if( portrait_active )
            layoutPortrait();
        syncVisibility();
        refresh(); // available modes/settings depend on the camera

        quiet_rebuild = false;
        // finish an AUTO hand-off: carry the zoom level across to the newly opened camera, then
        // let the live preview show through the frozen frame
        if( !Double.isNaN(pending_total_zoom) ) {
            final double total = pending_total_zoom;
            pending_total_zoom = Double.NaN;
            root().post(() -> {
                applyTotalZoom(total);
                root().removeCallbacks(unfreeze_runnable);
                root().postDelayed(unfreeze_runnable, 250);
            });
        }
        else {
            handoff_in_progress = false;
            if( freeze_view != null && freeze_view.getVisibility() == View.VISIBLE ) {
                root().removeCallbacks(unfreeze_runnable);
                root().postDelayed(unfreeze_runnable, 300);
            }
        }
    }

    /** Sets the zoom so that the overall zoom (relative to the main lens) is total. */
    private void applyTotalZoom(double total) {
        handoff_in_progress = false;
        Preview preview = main_activity.getPreview();
        if( preview == null || preview.getCameraController() == null )
            return;
        Lens open = openLens();
        if( open != null && open.physical_id != null ) {
            // physical lenses only have software zoom
            float ratio = (float)Math.max(1.0, Math.min(SOFTWARE_ZOOM_MAX, total / factorOf(open)));
            preview.setSoftwareZoom(ratio);
            preview.releaseDragZoom(ratio);
        }
        else if( preview.supportsZoom() ) {
            float min = preview.getMinZoomRatio();
            float max = preview.getZoomRatio(preview.getMaxZoom());
            float ratio = (float)Math.max(min, Math.min(max, total / factorOf(open)));
            preview.setZoomRatio(ratio);
            preview.releaseDragZoom(ratio);
        }
        else {
            preview.releaseDragZoom(1.0f);
        }
    }

    /** Starts an AUTO hand-off to another camera, keeping the overall zoom level. The current
     *  frame is frozen over the preview while the new lens starts, so the switch doesn't flash.
     */
    private void handOff(Lens target, double total) {
        if( target == null )
            return;
        if( MyDebug.LOG )
            Log.d(TAG, "AUTO hand-off to " + target + " at " + total);
        Preview preview = main_activity.getPreview();
        if( preview.isOpeningCamera() )
            return; // the switch would be ignored
        handoff_in_progress = true;
        pending_total_zoom = total;
        quiet_rebuild = true;
        preview.holdDragZoom(); // until the new lens's zoom is set (applyTotalZoom)
        freezePreview();
        main_activity.userSwitchToCamera(target.logical_id, target.physical_id);
        main_activity.getApplicationInterface().getDrawPreview().setDimPreview(false);
    }

    /** Covers the preview with a still of its current frame. */
    private void freezePreview() {
        try {
            View view = main_activity.getPreview().getView();
            if( !(view instanceof android.view.TextureView) || !(view.getParent() instanceof ViewGroup) || view.getWidth() <= 0 )
                return;
            android.graphics.Bitmap bitmap = ((android.view.TextureView)view).getBitmap();
            if( bitmap == null )
                return;
            ViewGroup parent = (ViewGroup)view.getParent();
            if( freeze_view == null ) {
                freeze_view = new ImageView(main_activity);
                freeze_view.setScaleType(ImageView.ScaleType.FIT_XY);
                freeze_view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
            if( freeze_view.getParent() != parent ) {
                if( freeze_view.getParent() instanceof ViewGroup )
                    ((ViewGroup)freeze_view.getParent()).removeView(freeze_view);
                parent.addView(freeze_view, parent.indexOfChild(view) + 1, new ViewGroup.LayoutParams(view.getWidth(), view.getHeight()));
            }
            else {
                ViewGroup.LayoutParams lp = freeze_view.getLayoutParams();
                lp.width = view.getWidth();
                lp.height = view.getHeight();
                freeze_view.setLayoutParams(lp);
            }
            freeze_view.setImageBitmap(bitmap);
            freeze_view.setTranslationX(view.getLeft() + view.getTranslationX());
            freeze_view.setTranslationY(view.getTop() + view.getTranslationY());
            freeze_view.setPivotX(view.getPivotX());
            freeze_view.setPivotY(view.getPivotY());
            freeze_view.setScaleX(view.getScaleX());
            freeze_view.setScaleY(view.getScaleY());
            freeze_view.animate().cancel();
            freeze_view.setAlpha(1.0f);
            freeze_view.setVisibility(View.VISIBLE);
            root().removeCallbacks(unfreeze_runnable);
            root().postDelayed(unfreeze_runnable, 3000); // in case the camera doesn't open
        }
        catch(Throwable e) {
            MyDebug.logStackTrace(TAG, "failed to freeze preview", e);
        }
    }

    private void unfreezePreview() {
        if( freeze_view == null || freeze_view.getVisibility() != View.VISIBLE )
            return;
        freeze_view.animate().cancel();
        freeze_view.animate().alpha(0.0f).setDuration(160).withEndAction(() -> {
            freeze_view.setVisibility(View.GONE);
            freeze_view.setImageBitmap(null);
        }).start();
    }

    /** Overall zoom range (relative to the main lens) the ruler and dragging cover, given the
     *  open camera's zoom ratio range.
     */
    private double [] rulerRange(float min_ratio, float max_ratio) {
        Lens open = openLens();
        if( smartAutoActive() && !auto_chain.isEmpty() ) {
            if( isOpen(auto_lens) )
                auto_min_ratio = min_ratio;
            Lens first = auto_chain.get(0);
            Lens last = auto_chain.get(auto_chain.size() - 1);
            double lo;
            if( !first.auto )
                lo = first.zoom;
            else if( !Double.isNaN(auto_min_ratio) )
                lo = auto_min_ratio;
            else
                lo = auto_lens_zooms.isEmpty() ? 1.0 : Math.min(1.0, auto_lens_zooms.get(0));
            double hi = last.auto ? (isOpen(last) ? max_ratio : 10.0) : last.zoom * SOFTWARE_ZOOM_MAX;
            return new double[]{lo, Math.max(hi, lo * 1.01)};
        }
        double factor = factorOf(open);
        return new double[]{min_ratio * factor, max_ratio * factor};
    }

    /** The preview's top and height, in screen coordinates (ignoring software zoom scaling). */
    private float [] previewSpan() {
        View view = main_activity.getPreview().getView();
        if( view == null || !(view.getParent() instanceof View) || view.getHeight() <= 0 )
            return null;
        int [] location = new int[2];
        ((View)view.getParent()).getLocationOnScreen(location);
        return new float[]{location[1] + view.getTop(), view.getHeight()};
    }

    /** Drag sensitivity at the coarsest precision: the whole zoom range spans the preview's
     *  height less a margin at each end, so it can be crossed in one movement.
     */
    public float dragZoomPixelsPerLog(float min_ratio, float max_ratio) {
        if( !views_created )
            return -1.0f;
        double [] range = rulerRange(min_ratio, max_ratio);
        float [] span = previewSpan();
        double logs = Math.log(range[1] / range[0]);
        if( span == null || !(logs > 0.05) )
            return -1.0f;
        return (float)(span[1] * (1.0f - 2.0f*RULER_MARGIN) / logs);
    }

    /** A lens's power to 1 decimal place, e.g. 0.6x, 1.0x, 2.9x. */
    static String powerLabel(double zoom) {
        return String.format(Locale.US, "%.1f\u00D7", zoom);
    }

    static String zoomLabel(double zoom) {
        if( zoom < 0.95 )
            return String.format(Locale.US, "%.1f×", zoom);
        long rounded = Math.round(zoom);
        if( Math.abs(zoom - rounded) < 0.15 )
            return rounded + "×";
        return String.format(Locale.US, "%.1f×", zoom);
    }

    private String presetLabel() {
        String preset = main_activity.getApplicationInterface().getOutputPreset();
        switch( preset ) {
            case "8": return "8M";
            case "24": return "24M";
            case "max": return "MAX";
            default: {
                CameraController.Size size = main_activity.getApplicationInterface().getOutputPictureSize();
                return size != null ? megapixelLabel(size.width, size.height) : "MAX";
            }
        }
    }

    /** Output resolution toggle (8M / 24M / MAX), first in the lens row. */
    private void addPresetChip() {
        final TextView chip = new TextView(main_activity);
        chip.setText(presetLabel());
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setTextColor(ACCENT_COLOR);
        chip.setBackground(roundedRect(CHIP_COLOR, dp(999)));
        chip.setPadding(dp(12), 0, dp(12), 0);
        chip.setMinWidth(dp(42));
        chip.setContentDescription(str(R.string.uifork_photo_size) + " " + presetLabel());
        addPressAnimation(chip);
        chip.setOnClickListener(v -> {
            String preset = main_activity.getApplicationInterface().getOutputPreset();
            String next;
            switch( preset ) {
                case "8": next = "24"; break;
                case "24": next = "max"; break;
                default: next = "8"; break;
            }
            prefs().edit().putString(MyApplicationInterface.OutputPresetPreferenceKey, next).apply();
            main_ui.destroyPopup();
            chip.setText(presetLabel());
            chip.setContentDescription(str(R.string.uifork_photo_size) + " " + presetLabel());
            pulse(chip);
            refresh();
        });
        // holding it shows every resolution (and the presets) in the chooser carousel; debug info
        // for the resolutions is on the chooser's options
        chip.setOnLongClickListener(v -> {
            toggleChooser(Chooser.RESOLUTION);
            return true;
        });
        preset_chip = chip;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        lp.setMargins(dp(5), 0, dp(5), 0);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lens_fixed.addView(chip, lp);
    }

    private void updatePresetChip() {
        if( preset_chip == null )
            return;
        preset_chip.setText(presetLabel());
        preset_chip.setContentDescription(str(R.string.uifork_photo_size) + " " + presetLabel());
    }

    private void selectPreset(String preset) {
        prefs().edit().putString(MyApplicationInterface.OutputPresetPreferenceKey, preset).apply();
        main_ui.destroyPopup();
        closeChooser();
        updatePresetChip();
        if( preset_chip != null )
            pulse(preset_chip);
        refresh();
    }

    /** While AUTO is selected, whether this is the lens it is using right now. */
    private boolean inUseByAuto(Lens lens) {
        return !lens.auto && smartAutoActive() && isOpen(lens);
    }

    private View addLensChip(final Lens lens, boolean selected) {
        TextView chip = new TextView(main_activity);
        if( lens.sublabel != null ) {
            android.text.SpannableString text = new android.text.SpannableString(lens.label + "\n" + lens.sublabel);
            text.setSpan(new android.text.style.RelativeSizeSpan(0.72f), lens.label.length() + 1, text.length(), 0);
            chip.setText(text);
            chip.setLineSpacing(0, 0.9f);
        }
        else {
            chip.setText(lens.label);
        }
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, selected ? 13 : 12);
        chip.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        chip.setTextColor(selected ? Color.BLACK : Color.WHITE);
        boolean wide = lens.sublabel != null || lens.label.length() > 4;
        GradientDrawable background = wide ? roundedRect(selected ? CHIP_SELECTED_COLOR : CHIP_COLOR, dp(999)) : oval(selected ? CHIP_SELECTED_COLOR : CHIP_COLOR);
        final boolean in_use = inUseByAuto(lens);
        if( in_use ) {
            // light ring: the lens AUTO is using at the moment
            background.setStroke(dp(1.5f), (ACCENT_COLOR & 0x00FFFFFF) | 0xB0000000);
            chip.setTextColor(ACCENT_COLOR);
        }
        chip.setBackground(background);
        if( wide )
            chip.setPadding(dp(10), 0, dp(10), 0);
        chip.setContentDescription(str(R.string.uifork_lens) + " " + lens.label + (lens.sublabel != null ? " " + lens.sublabel : "") + (in_use ? " " + str(R.string.uifork_lens_in_use) : ""));
        chip.setSelected(selected);
        addPressAnimation(chip);
        chip.setOnClickListener(v -> {
            Preview preview = main_activity.getPreview();
            if( v.isSelected() || preview.isOpeningCamera() || preview.isVideoRecording() )
                return;
            closeChooser();
            main_ui.closePopup();
            // AUTO turns smart hand-off on; picking any specific lens turns it off
            prefs().edit().putBoolean(SMART_AUTO_KEY, lens.auto).apply();
            // AUTO opens the lens it would use at 1x
            Lens target = lens.auto ? autoEntryLens() : lens;
            if( target == null )
                target = lens;
            if( isOpen(target) ) {
                // already open (e.g. a lens AUTO had switched to): just reselect
                preview.setSoftwareZoom(1.0f);
                rebuildLensChips();
                return;
            }
            switchToLens(target);
        });
        attachDebug(chip, "Lens " + lens.label + (lens.sublabel != null ? " " + lens.sublabel : ""), () -> lensDebug(lens));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(wide ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(42), dp(42));
        if( wide )
            chip.setMinWidth(dp(42));
        lp.setMargins(dp(5), 0, dp(5), 0);
        lp.gravity = Gravity.CENTER_VERTICAL;
        (lens.auto ? lens_fixed : lens_row).addView(chip, lp);
        if( selected && !quiet_rebuild )
            pulse(chip);
        return chip;
    }

    /** Switches lens with a short zoom animation towards the new lens's field of view, worked out
     *  from the known view angles of the two lenses, so the change of lens looks continuous.
     */
    private void switchToLens(final Lens lens) {
        Preview preview = main_activity.getPreview();
        double current_zoom = factorOf(openLens()) * preview.getSoftwareZoom();
        if( preview.getCameraController() != null && preview.supportsZoom() )
            current_zoom *= preview.getZoomRatio();
        double target_zoom = lens.auto ? 1.0 : lens.zoom;
        final View view = preview.getView();
        Runnable switch_camera = () -> main_activity.userSwitchToCamera(lens.logical_id, lens.physical_id);
        if( view == null || Double.isNaN(target_zoom) || current_zoom <= 0.0 ) {
            switch_camera.run();
            return;
        }
        float factor = (float)Math.max(0.3, Math.min(6.0, target_zoom / current_zoom));
        view.animate().cancel();
        view.animate().scaleX(view.getScaleX() * factor).scaleY(view.getScaleY() * factor).alpha(0.25f)
                .setDuration(170).setInterpolator(new DecelerateInterpolator())
                .withEndAction(switch_camera).start();
    }

    /** Called when a camera has opened: bring the preview back from the lens switch animation. */
    private void restorePreviewAfterLensSwitch() {
        Preview preview = main_activity.getPreview();
        if( preview == null || preview.getView() == null )
            return;
        View view = preview.getView();
        if( view.getAlpha() < 1.0f ) {
            view.animate().cancel();
            float scale = preview.getSoftwareZoom();
            view.setScaleX(scale);
            view.setScaleY(scale);
            view.animate().alpha(1.0f).setDuration(220).start();
        }
    }

    // ---------------------------------------------------------------- zoom ruler

    /** Called by the preview while the user drags to zoom. In smart AUTO, also switches lens as
     *  the zoom crosses each lens's minimum. At the coarsest precision the ruler shows the whole
     *  range over the preview's height (less margins), with the current zoom level at its place
     *  on that scale; finer precision magnifies the scale around it.
     */
    public void onDragZoom(boolean active, float raw_x, float raw_y, float ratio, float intent_ratio, float min_ratio, float max_ratio, float pixels_per_log, float precision, boolean software) {
        if( !views_created )
            return;
        if( !active ) {
            ruler_active = false;
            ruler_origin_log = Double.NaN;
            zoom_scale.end();
            return;
        }
        closeChooser();
        int [] location = new int[2];
        zoom_scale.getLocationOnScreen(location);
        List<float []> segments = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        Lens open = openLens();
        double factor = factorOf(open);
        double current = ratio * factor;
        double [] range = rulerRange(min_ratio, max_ratio);
        double range_min = range[0];
        double range_max = range[1];
        boolean smart = smartAutoActive() && !auto_chain.isEmpty();
        if( smart && !handoff_in_progress ) {
            double intent = intent_ratio * factor;
            autoSwitchFor(intent, open, range_min, range_max);
        }
        if( (smart || selected_lens_auto) && !auto_lens_zooms.isEmpty() ) {
            for(int i=0;i<auto_lens_zooms.size();i++) {
                double start = i == 0 ? Math.min(range_min, auto_lens_zooms.get(0)) : auto_lens_zooms.get(i);
                double end = i + 1 < auto_lens_zooms.size() ? auto_lens_zooms.get(i+1) : range_max;
                if( end <= start )
                    continue;
                segments.add(new float[]{(float)start, (float)end});
                labels.add(zoomLabel(auto_lens_zooms.get(i)));
            }
        }
        else {
            segments.add(new float[]{(float)range_min, (float)range_max});
            String label = zoomLabel(factor);
            if( software )
                label += " " + str(R.string.uifork_digital);
            labels.add(label);
        }
        // the scale stays put on screen: a zoom level keeps its height while the finger moves
        // (the marker follows the finger), and is only re-laid out when the precision changes,
        // magnifying around the current zoom level so it stays where it is
        float anchor;
        float [] span = previewSpan();
        if( !ruler_active || Double.isNaN(ruler_origin_log) ) {
            ruler_active = true;
            if( span != null && precision > 0.0f && current > 0.0 ) {
                float base_pixels_per_log = pixels_per_log / precision;
                ruler_origin_y = span[0] + span[1] * RULER_MARGIN + (float)((Math.log(range_max) - Math.log(current)) * base_pixels_per_log);
            }
            else {
                ruler_origin_y = raw_y;
            }
            ruler_origin_log = Math.log(current);
            ruler_pixels_per_log = pixels_per_log;
        }
        else if( Math.abs(pixels_per_log - ruler_pixels_per_log) > 1.0e-3f * ruler_pixels_per_log ) {
            ruler_origin_y = (float)(ruler_origin_y - (Math.log(current) - ruler_origin_log) * ruler_pixels_per_log);
            ruler_origin_log = Math.log(current);
            ruler_pixels_per_log = pixels_per_log;
        }
        anchor = (float)(ruler_origin_y - (Math.log(current) - ruler_origin_log) * ruler_pixels_per_log) - location[1];
        zoom_scale.update(anchor, (float)current, (float)range_min, (float)range_max, pixels_per_log, precision, segments, labels);
    }

    /** Ruler fixed to the right-hand side of the screen while drag-zooming: ticks for zoom values,
     *  a coloured band for the range of each lens, and the current zoom level. The scale scrolls
     *  under a fixed marker level with the finger (in the same units as the drag), stretches as
     *  the drag gets finer, and fades out towards its top and bottom ends.
     */
    private class ZoomScaleView extends View {
        private final Paint line_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mask_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final double [] majors = { 0.5, 0.6, 0.7, 0.8, 0.9, 1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30,
                40, 50, 60, 80, 100, 150, 200 };
        private boolean active;
        private float finger_y, anchor_y, current, min, max, pixels_per_log, precision;
        private List<float []> segments = new ArrayList<>();
        private List<String> segment_labels = new ArrayList<>();

        ZoomScaleView(Context context) {
            super(context);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setLayerType(LAYER_TYPE_HARDWARE, null);
            line_paint.setColor(Color.WHITE);
            line_paint.setStrokeWidth(dp(1.5f));
            text_paint.setColor(Color.WHITE);
            text_paint.setTextSize(dp(11));
            mask_paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }

        void update(float y, float current, float min, float max, float pixels_per_log, float precision, List<float []> segments, List<String> labels) {
            this.finger_y = y;
            this.current = current;
            this.min = min;
            this.max = max;
            this.pixels_per_log = pixels_per_log;
            this.precision = precision;
            this.segments = segments;
            this.segment_labels = labels;
            if( !active ) {
                active = true;
                animate().cancel();
                setAlpha(0.0f);
                setVisibility(VISIBLE);
                animate().alpha(1.0f).setDuration(120).start();
            }
            invalidate();
        }

        void end() {
            if( !active )
                return;
            active = false;
            animate().alpha(0.0f).setDuration(350).withEndAction(() -> {
                if( !active )
                    setVisibility(GONE);
            }).start();
        }

        private float yFor(double value) {
            return anchor_y - (float)((Math.log(value) - Math.log(current)) * pixels_per_log);
        }

        private boolean isMajor(double value) {
            for(double major : majors) {
                if( Math.abs(value - major) < 1.0e-3 * major )
                    return true;
            }
            return false;
        }

        private double tickStep(double value) {
            if( value < 1.0 ) return 0.05;
            if( value < 2.0 ) return 0.1;
            if( value < 5.0 ) return 0.25;
            if( value < 10.0 ) return 0.5;
            if( value < 20.0 ) return 1.0;
            if( value < 50.0 ) return 2.5;
            if( value < 100.0 ) return 5.0;
            return 10.0;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if( pixels_per_log <= 0.0f || current <= 0.0f || min <= 0.0f )
                return;
            final float ruler_x = getWidth() - dp(30);
            final float window_top = dp(44);
            final float window_bottom = Math.max(window_top + dp(120), getHeight() - pill_bottom - dp(60));
            final float fade = Math.min(dp(64), (window_bottom - window_top) / 3.0f);
            // the current zoom sits level with the finger, kept clear of the faded ends
            anchor_y = finger_y;
            final float marker_y = Math.max(window_top + fade, Math.min(window_bottom - fade, anchor_y));
            float line_top = Math.max(window_top, yFor(max));
            float line_bottom = Math.min(window_bottom, yFor(min));

            int layer = canvas.saveLayer(0, window_top, getWidth(), window_bottom, null);

            // backdrop
            fill_paint.setColor(0x73000000);
            rect.set(ruler_x - dp(92), window_top, getWidth() - dp(4), window_bottom);
            canvas.drawRoundRect(rect, dp(16), dp(16), fill_paint);

            // lens range bands right of the line, with the lens name at the start of each band
            text_paint.setTextSize(dp(11));
            text_paint.setTextAlign(Paint.Align.RIGHT);
            text_paint.setTypeface(Typeface.DEFAULT_BOLD);
            for(int i=0;i<segments.size();i++) {
                float [] segment = segments.get(i);
                float y_start = Math.min(line_bottom, yFor(Math.max(segment[0], min)));
                float y_end = Math.max(line_top, yFor(Math.min(segment[1], max)));
                if( y_end >= y_start )
                    continue;
                int color = SEGMENT_COLORS[i % SEGMENT_COLORS.length];
                fill_paint.setColor(color);
                rect.set(ruler_x + dp(7), y_end + dp(1), ruler_x + dp(12), y_start - dp(1));
                canvas.drawRoundRect(rect, dp(2.5f), dp(2.5f), fill_paint);
                if( segment[0] >= min * 0.999f && yFor(segment[0]) <= line_bottom + 1 ) {
                    text_paint.setColor(color);
                    canvas.drawText(segment_labels.get(i), ruler_x - dp(50), y_start + dp(4), text_paint);
                }
            }

            // ruler line, ticks pointing left, labels left of the ticks
            canvas.drawLine(ruler_x, line_top, ruler_x, line_bottom, line_paint);
            text_paint.setColor(Color.WHITE);
            text_paint.setTypeface(Typeface.DEFAULT);
            float last_tick_y = Float.MAX_VALUE;
            float last_label_y = Float.MAX_VALUE;
            double value = Math.ceil(min / tickStep(min) - 1.0e-6) * tickStep(min);
            int guard = 0;
            while( value <= max * 1.0001 && guard++ < 4000 ) {
                float y = yFor(value);
                if( y < line_top - 1 )
                    break;
                if( y <= line_bottom + 1 ) {
                    boolean major = isMajor(value);
                    if( last_tick_y - y >= dp(5) || major ) {
                        float length = major ? dp(12) : dp(6);
                        canvas.drawLine(ruler_x - length, y, ruler_x, y, line_paint);
                        last_tick_y = y;
                    }
                    if( major && last_label_y - y >= dp(20) ) {
                        canvas.drawText(zoomLabel(value), ruler_x - dp(16), y + dp(4), text_paint);
                        last_label_y = y;
                    }
                }
                value = Math.round((value + tickStep(value)) * 1000.0) / 1000.0;
            }

            // fade the ends of the ruler out
            mask_paint.setShader(new LinearGradient(0, window_top, 0, window_bottom,
                    new int[]{ 0x00000000, 0xFF000000, 0xFF000000, 0x00000000 },
                    new float[]{ 0.0f, fade/(window_bottom - window_top), 1.0f - fade/(window_bottom - window_top), 1.0f },
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, window_top, getWidth(), window_bottom, mask_paint);
            canvas.restoreToCount(layer);

            // current position marker and value
            fill_paint.setColor(Color.WHITE);
            canvas.drawCircle(ruler_x, marker_y, dp(4.5f), fill_paint);
            String text = String.format(Locale.US, current < 10 ? "%.1f×" : "%.0f×", current);
            text_paint.setTextSize(dp(18));
            text_paint.setTypeface(Typeface.DEFAULT_BOLD);
            text_paint.setTextAlign(Paint.Align.RIGHT);
            float text_w = text_paint.measureText(text);
            float bubble_right = ruler_x - dp(96);
            fill_paint.setColor(0xD9000000);
            rect.set(bubble_right - text_w - dp(24), marker_y - dp(17), bubble_right, marker_y + dp(17));
            canvas.drawRoundRect(rect, dp(17), dp(17), fill_paint);
            text_paint.setColor(Color.WHITE);
            canvas.drawText(text, bubble_right - dp(12), marker_y + dp(6), text_paint);
            if( precision >= 1.3f ) {
                text_paint.setTextSize(dp(11));
                text_paint.setTypeface(Typeface.DEFAULT);
                text_paint.setColor(ACCENT_COLOR);
                String fine = String.format(Locale.US, str(R.string.uifork_fine_zoom), precision);
                canvas.drawText(fine, bubble_right - dp(12), marker_y + dp(32), text_paint);
            }
            text_paint.setTextSize(dp(11));
            text_paint.setColor(Color.WHITE);
        }
    }
}
