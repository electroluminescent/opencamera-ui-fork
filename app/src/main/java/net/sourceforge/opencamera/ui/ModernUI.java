package net.sourceforge.opencamera.ui;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
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
 *  - a quick-settings pill whose items the user pins/unpins from its "more" sheet, with
 *    choosers for flash, timer, aspect, resolution, focus and grid;
 *  - a large shutter flanked by round gallery/switch buttons;
 *  - a lens carousel, and a carousel of photo modes (plus video) whose modes can also be
 *    pinned/unpinned from a "more" sheet;
 *  - a top row of round toggle chips (exposure lock etc.);
 *  - a zoom ruler drawn while drag-zooming;
 *  - the stock options popup shown as a bottom sheet.
 *  It is applied after MainUI's stock layout pass, so landscape keeps the original Open Camera
 *  layout.
 */
public class ModernUI {
    private static final String TAG = "ModernUI";

    private static final int PILL_COLOR = 0xE6262A36;
    private static final int SHEET_COLOR = 0xF51C1F28;
    private static final int TILE_COLOR = 0xFF2A2F3D;
    private static final int CHIP_COLOR = 0xB3262A36;
    private static final int CHIP_SELECTED_COLOR = 0xFFF2F2F2;
    private static final int TAB_INACTIVE_COLOR = 0xFFAEB8D8;
    private static final int ACCENT_COLOR = 0xFFFFD54F;
    private static final int [] SEGMENT_COLORS = { 0xFFFFD54F, 0xFF4FC3F7, 0xFF81C784, 0xFFF48FB1, 0xFFCE93D8 };

    private static final String PINNED_QUICK_KEY = "uifork_pinned_quick_items";
    private static final String PINNED_MODES_KEY = "uifork_pinned_modes";
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

    /** Entries of the mode carousel, in carousel order. */
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

    private HorizontalScrollView lens_scroll;
    private LinearLayout lens_row;
    private int lens_count;
    // the open lens, for the zoom ruler: its zoom relative to the main lens, whether it is the
    // auto (logical multi-camera) lens, and the zoom factors of the lenses the auto lens uses
    private double selected_lens_zoom = 1.0;
    private boolean selected_lens_auto;
    private final List<Double> auto_lens_zooms = new ArrayList<>();

    private HorizontalScrollView mode_scroll;
    private LinearLayout mode_row;
    private final Map<Mode, TextView> mode_views = new EnumMap<>(Mode.class);
    private String mode_signature = "";

    private ZoomScaleView zoom_scale;

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
        this.last_navigation_gap = navigation_gap;
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
        final int lens_h = dp(48);
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

        lp = resetParams(lens_scroll, ViewGroup.LayoutParams.WRAP_CONTENT, lens_h);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lp.bottomMargin = base + mode_h;
        lens_scroll.setLayoutParams(lp);

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
        closeSheet();
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

    // ---------------------------------------------------------------- views

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
        root.addView(lens_scroll, insert_index++, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        // photo mode carousel
        mode_scroll = new HorizontalScrollView(main_activity);
        mode_scroll.setHorizontalScrollBarEnabled(false);
        mode_scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mode_row = new LinearLayout(main_activity);
        mode_row.setOrientation(LinearLayout.HORIZONTAL);
        mode_row.setGravity(Gravity.CENTER_VERTICAL);
        mode_scroll.addView(mode_row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(mode_scroll, insert_index++, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

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
        setVisibility(lens_scroll, lens_count > 1 ? gui : View.GONE);
        setVisibility(quick_bar, settings);
        if( settings != View.VISIBLE ) {
            closeChooser();
            closeSheet();
        }
        if( portrait_active )
            layoutTopRow(); // only re-lays out if the set of visible chips changed
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
        chooser_row.removeAllViews();
        setVisibility(chooser_scroll, View.GONE);
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
        setVisibility(chooser_scroll, View.VISIBLE);
    }

    private void addChooserItem(String label, int icon, boolean selected, String description, View.OnClickListener listener) {
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
            addChooserItem(null, flashIcon(value), value.equals(current), entryFromArrays(R.array.flash_entries, R.array.flash_values, value), v -> {
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
            addChooserItem(null, focusIcon(value), value.equals(current), entryFromArrays(R.array.focus_mode_entries, R.array.focus_mode_values, value), v -> {
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
            addChooserItem(entries[i], 0, value.equals(current), entries[i], v -> {
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
            addChooserItem(label, 0, value.equals(current), entries[i], v -> {
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
                addChooserItem(label, 0, label.equals(current_aspect), label, v -> selectOutputSize(size));
            }
        }
        else {
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
                boolean selected = size.width == current.width && size.height == current.height;
                addChooserItem(label, 0, selected, size.width + " x " + size.height, v -> selectOutputSize(size));
            }
        }
    }

    private void selectOutputSize(CameraController.Size size) {
        Preview preview = main_activity.getPreview();
        String key = PreferenceKeys.getResolutionPreferenceKey(preview.getCameraId(), main_activity.getApplicationInterface().getCameraIdSPhysicalPref());
        prefs().edit().putString(key, size.width + " " + size.height).apply();
        closeChooser();
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
            case X_AUTO: return main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_AUTOMATIC);
            case X_HDR: return main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_HDR);
            case X_NIGHT: return main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_NIGHT);
            case X_BOKEH: return main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_BOKEH);
            case X_BEAUTY: return main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_BEAUTY);
            default: return false;
        }
    }

    private Set<Mode> pinnedModes() {
        Set<Mode> modes = new HashSet<>();
        for(String name : prefs().getString(PINNED_MODES_KEY, DEFAULT_PINNED_MODES).split(",")) {
            try {
                modes.add(Mode.valueOf(name.trim()));
            }
            catch(IllegalArgumentException e) {
                // ignore unknown entries from other versions
            }
        }
        modes.add(Mode.STD);
        modes.add(Mode.VIDEO);
        return modes;
    }

    private void setPinnedModes(Set<Mode> modes) {
        StringBuilder value = new StringBuilder();
        for(Mode mode : Mode.values()) {
            if( modes.contains(mode) ) {
                if( value.length() > 0 )
                    value.append(',');
                value.append(mode.name());
            }
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

    /** Rebuilds the carousel if the set of modes shown changed, then highlights the current one. */
    private void refreshModes() {
        Mode current = currentMode();
        Set<Mode> pinned = pinnedModes();
        List<Mode> shown = new ArrayList<>();
        for(Mode mode : Mode.values()) {
            if( isSupported(mode) && (pinned.contains(mode) || mode == current) )
                shown.add(mode);
        }
        String signature = shown.toString();
        if( !signature.equals(mode_signature) ) {
            mode_signature = signature;
            mode_row.removeAllViews();
            mode_views.clear();
            for(final Mode mode : shown) {
                TextView tab = new TextView(main_activity);
                tab.setText(mode.label);
                tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                tab.setLetterSpacing(0.04f);
                tab.setGravity(Gravity.CENTER);
                tab.setPadding(dp(14), 0, dp(14), 0);
                tab.setOnClickListener(v -> selectMode(mode));
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
            mode_row.addView(more, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        for(Map.Entry<Mode, TextView> entry : mode_views.entrySet()) {
            boolean selected = entry.getKey() == current;
            entry.getValue().setTextColor(selected ? Color.WHITE : TAB_INACTIVE_COLOR);
            entry.getValue().setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        final TextView selected_view = mode_views.get(current);
        if( selected_view != null ) {
            // centre the current mode
            mode_scroll.post(() -> {
                int x = selected_view.getLeft() + selected_view.getWidth()/2 - mode_scroll.getWidth()/2;
                mode_scroll.smoothScrollTo(Math.max(0, x), 0);
            });
        }
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
        sheet_content.removeAllViews();
        setVisibility(sheet_scroll, View.GONE);
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
        setVisibility(sheet_scroll, View.VISIBLE);
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

    private static class Tile {
        final String label;
        final String value;
        final int icon;
        final boolean selected;
        final boolean pinned;
        final boolean locked;
        final View.OnClickListener on_use;
        final View.OnClickListener on_toggle_pin;

        Tile(String label, String value, int icon, boolean selected, boolean pinned, boolean locked, View.OnClickListener on_use, View.OnClickListener on_toggle_pin) {
            this.label = label;
            this.value = value;
            this.icon = icon;
            this.selected = selected;
            this.pinned = pinned;
            this.locked = locked;
            this.on_use = on_use;
            this.on_toggle_pin = on_toggle_pin;
        }
    }

    /** Lays out tiles four to a row. In edit mode tapping a tile pins/unpins it. */
    private void addTiles(List<Tile> tiles) {
        final int per_row = 4;
        LinearLayout row = null;
        for(int i=0;i<tiles.size();i++) {
            if( i % per_row == 0 ) {
                row = new LinearLayout(main_activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                sheet_content.addView(row);
            }
            final Tile tile = tiles.get(i);
            LinearLayout cell = new LinearLayout(main_activity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(4), dp(8), dp(4), dp(8));
            cell.setBackground(roundedRect(tile.selected ? 0xFF3D4660 : TILE_COLOR, dp(16)));
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
                cell.setAlpha(tile.locked ? 0.5f : 1.0f);
            }
            cell.setContentDescription(tile.label);
            cell.setOnClickListener(v -> {
                if( sheet_editing ) {
                    if( !tile.locked )
                        tile.on_toggle_pin.onClick(v);
                }
                else {
                    tile.on_use.onClick(v);
                }
            });
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

    private void buildQuickSheet() {
        addSheetHeader(str(R.string.uifork_quick_settings));
        final List<QuickItem> pinned = pinnedQuickItems();
        List<Tile> pinned_tiles = new ArrayList<>();
        List<Tile> other_tiles = new ArrayList<>();
        for(final QuickItem item : QuickItem.values()) {
            if( !isAvailable(item) && !pinned.contains(item) )
                continue;
            boolean is_pinned = pinned.contains(item);
            Tile tile = new Tile(str(quickLabel(item)), quickText(item), quickIcon(item), quickActive(item), is_pinned, false,
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
                    });
            if( is_pinned )
                pinned_tiles.add(tile);
            else
                other_tiles.add(tile);
        }
        // pinned tiles in bar order
        Collections.sort(pinned_tiles, (a, b) -> 0);
        addSectionTitle(str(R.string.uifork_pinned));
        addTiles(pinned_tiles);
        if( !other_tiles.isEmpty() ) {
            addSectionTitle(str(R.string.uifork_unpinned));
            addTiles(other_tiles);
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

    private void buildModesSheet() {
        addSheetHeader(str(R.string.uifork_photo_modes));
        final Set<Mode> pinned = pinnedModes();
        Mode current = currentMode();
        List<Tile> pinned_tiles = new ArrayList<>();
        List<Tile> other_tiles = new ArrayList<>();
        for(final Mode mode : Mode.values()) {
            if( !isSupported(mode) )
                continue;
            boolean is_pinned = pinned.contains(mode);
            Tile tile = new Tile(str(mode.label), str(mode.label).substring(0, 1), 0, mode == current, is_pinned, mode.alwaysPinned(),
                    v -> {
                        closeSheet();
                        selectMode(mode);
                    },
                    v -> {
                        Set<Mode> modes = pinnedModes();
                        if( modes.contains(mode) )
                            modes.remove(mode);
                        else
                            modes.add(mode);
                        setPinnedModes(modes);
                        refreshModes();
                        buildSheet();
                    });
            if( is_pinned )
                pinned_tiles.add(tile);
            else
                other_tiles.add(tile);
        }
        addSectionTitle(str(R.string.uifork_pinned));
        addTiles(pinned_tiles);
        if( !other_tiles.isEmpty() ) {
            addSectionTitle(str(R.string.uifork_unpinned));
            addTiles(other_tiles);
        }
    }

    // ---------------------------------------------------------------- lens carousel

    private static class Lens {
        final int logical_id;
        final String physical_id;
        final float view_angle;
        final boolean auto;
        final boolean part_of_auto;
        double zoom = Double.NaN;
        String label;

        Lens(int logical_id, String physical_id, float view_angle, boolean auto, boolean part_of_auto) {
            this.logical_id = logical_id;
            this.physical_id = physical_id;
            this.view_angle = view_angle;
            this.auto = auto;
            this.part_of_auto = part_of_auto;
        }
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

    /** Rebuilds the lens carousel: every lens facing the same way as the open camera, whichever
     *  of them is open, so the carousel doesn't change as lenses are selected. Standalone cameras
     *  are preferred over identical physical lenses of a multi-camera, as they support zoom.
     *  Called whenever a camera is opened.
     */
    void rebuildLensChips() {
        createViewsIfNeeded();
        lens_row.removeAllViews();
        lens_count = 0;
        selected_lens_zoom = 1.0;
        selected_lens_auto = false;
        auto_lens_zooms.clear();
        Preview preview = main_activity.getPreview();
        if( preview == null || preview.getCameraControllerManager() == null ) {
            syncVisibility();
            return;
        }
        try {
            int current_id = preview.getCameraId();
            String current_physical = main_activity.getApplicationInterface().getCameraIdSPhysicalPref();
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
                Lens logical = new Lens(logical_id, null, viewAngle(camera2_id), !physical.isEmpty(), false);
                lenses.add(logical);
                if( logical.auto && first_auto == null )
                    first_auto = logical;
                for(String physical_id : physical) {
                    lenses.add(new Lens(logical_id, physical_id, viewAngle(physical_id), false, true));
                }
            }

            // zoom factors are relative to the auto camera if there is one, else the first camera
            Lens reference = first_auto != null ? first_auto : (lenses.isEmpty() ? null : lenses.get(0));
            float reference_angle = reference != null ? reference.view_angle : -1.0f;
            for(Lens lens : lenses) {
                if( reference_angle > 0.0f && lens.view_angle > 0.0f ) {
                    lens.zoom = Math.tan(Math.toRadians(reference_angle)/2.0) / Math.tan(Math.toRadians(lens.view_angle)/2.0);
                }
            }

            // the auto camera's lenses, for the zoom ruler
            for(Lens lens : lenses) {
                if( first_auto != null && lens.part_of_auto && lens.logical_id == first_auto.logical_id && !Double.isNaN(lens.zoom) )
                    auto_lens_zooms.add(lens.zoom);
            }
            Collections.sort(auto_lens_zooms);

            // drop physical lenses that duplicate a standalone camera (e.g. an ultra-wide that is
            // exposed both on its own and as part of the multi-camera)
            List<Lens> deduped = new ArrayList<>();
            for(Lens lens : lenses) {
                boolean duplicate = false;
                if( lens.part_of_auto && lens.view_angle > 0.0f ) {
                    for(Lens other : lenses) {
                        if( other.physical_id == null && !other.auto && other.view_angle > 0.0f &&
                                Math.abs(other.view_angle - lens.view_angle) / other.view_angle < 0.03f ) {
                            duplicate = true;
                            break;
                        }
                    }
                }
                boolean selected = lens.logical_id == current_id &&
                        (lens.physical_id == null ? current_physical == null : lens.physical_id.equals(current_physical));
                if( !duplicate || selected )
                    deduped.add(lens);
            }

            Collections.sort(deduped, (a, b) -> {
                if( a.auto != b.auto )
                    return a.auto ? -1 : 1;
                boolean a_nan = Double.isNaN(a.zoom);
                boolean b_nan = Double.isNaN(b.zoom);
                if( a_nan || b_nan ) {
                    if( a_nan && b_nan )
                        return Integer.compare(a.logical_id, b.logical_id);
                    return a_nan ? 1 : -1;
                }
                return Double.compare(a.zoom, b.zoom);
            });

            Set<String> used_labels = new HashSet<>();
            for(Lens lens : deduped) {
                String label;
                if( lens.auto )
                    label = str(R.string.uifork_lens_auto);
                else if( Double.isNaN(lens.zoom) )
                    label = String.valueOf(lens.logical_id);
                else
                    label = zoomLabel(lens.zoom);
                String unique = label;
                int suffix = 2;
                while( used_labels.contains(unique) ) {
                    unique = label + " " + suffix;
                    suffix++;
                }
                used_labels.add(unique);
                lens.label = unique;
            }

            for(Lens lens : deduped) {
                boolean selected = lens.logical_id == current_id &&
                        (lens.physical_id == null ? current_physical == null : lens.physical_id.equals(current_physical));
                if( selected ) {
                    selected_lens_auto = lens.auto;
                    selected_lens_zoom = lens.auto || Double.isNaN(lens.zoom) ? 1.0 : lens.zoom;
                }
                if( deduped.size() > 1 )
                    addLensChip(lens, selected);
            }
            if( deduped.size() > 1 )
                lens_count = deduped.size();
        }
        catch(Throwable e) {
            // camera drivers can throw a variety of exceptions; the carousel is optional
            MyDebug.logStackTrace(TAG, "failed to build lens carousel", e);
            lens_row.removeAllViews();
            lens_count = 0;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "lens_count: " + lens_count);
        if( portrait_active )
            layoutPortrait();
        syncVisibility();
        refresh(); // available modes/settings depend on the camera
    }

    static String zoomLabel(double zoom) {
        if( zoom < 0.95 )
            return String.format(Locale.US, "%.1f×", zoom);
        long rounded = Math.round(zoom);
        if( Math.abs(zoom - rounded) < 0.15 )
            return rounded + "×";
        return String.format(Locale.US, "%.1f×", zoom);
    }

    private void addLensChip(final Lens lens, boolean selected) {
        TextView chip = new TextView(main_activity);
        chip.setText(lens.label);
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, selected ? 13 : 12);
        chip.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        chip.setTextColor(selected ? Color.BLACK : Color.WHITE);
        chip.setBackground(oval(selected ? CHIP_SELECTED_COLOR : CHIP_COLOR));
        chip.setContentDescription(str(R.string.uifork_lens) + " " + lens.label);
        chip.setSelected(selected);
        addPressAnimation(chip);
        chip.setOnClickListener(v -> {
            Preview preview = main_activity.getPreview();
            if( v.isSelected() || preview.isOpeningCamera() || preview.isVideoRecording() )
                return;
            closeChooser();
            main_ui.closePopup();
            main_activity.userSwitchToCamera(lens.logical_id, lens.physical_id);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.setMargins(dp(5), 0, dp(5), 0);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lens_row.addView(chip, lp);
    }

    // ---------------------------------------------------------------- zoom ruler

    /** Called by the preview while the user drags to zoom. */
    public void onDragZoom(boolean active, float raw_x, float raw_y, float ratio, float min_ratio, float max_ratio, float pixels_per_log, float precision, boolean software) {
        if( !views_created )
            return;
        if( !active ) {
            zoom_scale.end();
            return;
        }
        closeChooser();
        int [] location = new int[2];
        zoom_scale.getLocationOnScreen(location);
        // convert from the open camera's ratios to zoom relative to the main lens
        double factor = selected_lens_auto ? 1.0 : selected_lens_zoom;
        List<float []> segments = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if( selected_lens_auto && !auto_lens_zooms.isEmpty() ) {
            for(int i=0;i<auto_lens_zooms.size();i++) {
                double start = i == 0 ? Math.min(min_ratio, auto_lens_zooms.get(0)) : auto_lens_zooms.get(i);
                double end = i + 1 < auto_lens_zooms.size() ? auto_lens_zooms.get(i+1) : max_ratio;
                segments.add(new float[]{(float)start, (float)end});
                labels.add(zoomLabel(auto_lens_zooms.get(i)));
            }
        }
        else {
            segments.add(new float[]{(float)(min_ratio*factor), (float)(max_ratio*factor)});
            String label = zoomLabel(factor);
            if( software )
                label += " " + str(R.string.uifork_digital);
            labels.add(label);
        }
        zoom_scale.update(raw_x - location[0], raw_y - location[1], (float)(ratio*factor),
                (float)(min_ratio*factor), (float)(max_ratio*factor), pixels_per_log, precision, segments, labels);
    }

    /** Ruler drawn beside the finger while drag-zooming: ticks for zoom values, a coloured band
     *  for the range of each lens, and the current zoom above the finger. The ruler is drawn in
     *  the same units as the drag, so it moves with the finger; dragging sideways stretches it.
     */
    private class ZoomScaleView extends View {
        private final Paint line_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final double [] majors = { 0.5, 0.6, 0.7, 0.8, 0.9, 1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30,
                40, 50, 60, 80, 100, 150, 200 };
        private boolean active;
        private float finger_x, finger_y, current, min, max, pixels_per_log, precision;
        private List<float []> segments = new ArrayList<>();
        private List<String> segment_labels = new ArrayList<>();

        ZoomScaleView(Context context) {
            super(context);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            line_paint.setColor(Color.WHITE);
            line_paint.setStrokeWidth(dp(1.5f));
            text_paint.setColor(Color.WHITE);
            text_paint.setTextSize(dp(11));
        }

        void update(float x, float y, float current, float min, float max, float pixels_per_log, float precision, List<float []> segments, List<String> labels) {
            this.finger_x = x;
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
                setAlpha(1.0f);
                setVisibility(VISIBLE);
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
            return finger_y - (float)((Math.log(value) - Math.log(current)) * pixels_per_log);
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
            float ruler_x = finger_x - dp(84);
            if( ruler_x < dp(56) )
                ruler_x = finger_x + dp(84);
            float half = getHeight() * 0.3f;
            float window_top = Math.max(dp(8), finger_y - half);
            float window_bottom = Math.min(getHeight() - dp(8), finger_y + half);
            float line_top = Math.max(window_top, yFor(max));
            float line_bottom = Math.min(window_bottom, yFor(min));

            // backdrop
            fill_paint.setColor(0x8C000000);
            rect.set(ruler_x - dp(56), line_top - dp(14), ruler_x + dp(40), line_bottom + dp(14));
            canvas.drawRoundRect(rect, dp(14), dp(14), fill_paint);

            // lens range bands, with the lens name at the start of each band
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
                rect.set(ruler_x - dp(12), y_end + dp(1), ruler_x - dp(7), y_start - dp(1));
                canvas.drawRoundRect(rect, dp(2.5f), dp(2.5f), fill_paint);
                if( segment[0] >= min * 0.999f && yFor(segment[0]) <= line_bottom + 1 ) {
                    text_paint.setColor(color);
                    canvas.drawText(segment_labels.get(i), ruler_x - dp(16), y_start + dp(4), text_paint);
                }
            }

            // ruler line and ticks
            canvas.drawLine(ruler_x, line_top, ruler_x, line_bottom, line_paint);
            text_paint.setColor(Color.WHITE);
            text_paint.setTextAlign(Paint.Align.LEFT);
            text_paint.setTypeface(Typeface.DEFAULT);
            float last_tick_y = Float.MAX_VALUE;
            float last_label_y = Float.MAX_VALUE;
            double value = Math.ceil(min / tickStep(min) - 1.0e-6) * tickStep(min);
            int guard = 0;
            while( value <= max * 1.0001 && guard++ < 2000 ) {
                float y = yFor(value);
                if( y < line_top - 1 )
                    break;
                if( y <= line_bottom + 1 ) {
                    boolean major = isMajor(value);
                    if( last_tick_y - y >= dp(5) || major ) {
                        float length = major ? dp(12) : dp(6);
                        canvas.drawLine(ruler_x, y, ruler_x + length, y, line_paint);
                        last_tick_y = y;
                    }
                    if( major && last_label_y - y >= dp(20) ) {
                        canvas.drawText(zoomLabel(value), ruler_x + dp(15), y + dp(4), text_paint);
                        last_label_y = y;
                    }
                }
                value = Math.round((value + tickStep(value)) * 1000.0) / 1000.0;
            }

            // current position marker on the ruler
            fill_paint.setColor(Color.WHITE);
            canvas.drawCircle(ruler_x, finger_y, dp(4), fill_paint);

            // current zoom above the finger
            String text = String.format(Locale.US, current < 10 ? "%.1f×" : "%.0f×", current);
            text_paint.setTextSize(dp(20));
            text_paint.setTypeface(Typeface.DEFAULT_BOLD);
            text_paint.setTextAlign(Paint.Align.CENTER);
            float text_w = text_paint.measureText(text);
            float bubble_y = finger_y - dp(76);
            fill_paint.setColor(0xCC000000);
            rect.set(finger_x - text_w/2 - dp(12), bubble_y - dp(22), finger_x + text_w/2 + dp(12), bubble_y + dp(10));
            canvas.drawRoundRect(rect, dp(16), dp(16), fill_paint);
            canvas.drawText(text, finger_x, bubble_y + dp(3), text_paint);
            if( precision >= 1.3f ) {
                text_paint.setTextSize(dp(11));
                text_paint.setTypeface(Typeface.DEFAULT);
                String fine = String.format(Locale.US, str(R.string.uifork_fine_zoom), precision);
                canvas.drawText(fine, finger_x, bubble_y + dp(24), text_paint);
            }
            text_paint.setTextSize(dp(11));
        }
    }
}
