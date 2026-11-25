package net.sourceforge.opencamera.ui;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.R;
import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.cameracontroller.CameraControllerManager;
import net.sourceforge.opencamera.preview.Preview;

/** Open Camera UI Fork: portrait camera layout in the style of current phone camera apps -
 *  a quick-settings pill, a large shutter flanked by round gallery/switch buttons, a lens
 *  carousel and photo/video mode tabs. It is applied after MainUI's stock layout pass, so
 *  landscape keeps the original Open Camera layout.
 */
public class ModernUI {
    private static final String TAG = "ModernUI";

    private static final int PILL_COLOR = 0xE6262A36;
    private static final int CHIP_COLOR = 0xB3262A36;
    private static final int CHIP_SELECTED_COLOR = 0xFFF2F2F2;
    private static final int TAB_INACTIVE_COLOR = 0xFFAEB8D8;
    private static final String [] TIMER_CYCLE = {"0", "3", "10"};

    /** Stock views that this class repositions in portrait. */
    private static final int [] OVERRIDDEN_VIEWS = { R.id.take_photo, R.id.switch_camera, R.id.gallery,
            R.id.pause_video, R.id.take_photo_when_video_recording, R.id.cancel_panorama };
    /** Stock views whose job is taken over by the pill, tabs or lens carousel in portrait. */
    private static final int [] HIDDEN_IN_PORTRAIT = { R.id.settings, R.id.popup, R.id.exposure,
            R.id.cycle_flash, R.id.switch_video, R.id.switch_multi_camera };

    private final MainActivity main_activity;
    private final MainUI main_ui;
    private final Map<Integer, int[]> original_geometry = new HashMap<>();
    private final Map<Integer, ImageView.ScaleType> original_scale_types = new HashMap<>();

    private boolean views_created;
    private boolean portrait_active;
    private int last_navigation_gap;
    private int zoom_extra_applied;

    private LinearLayout quick_bar;
    private ImageButton qb_flash;
    private ImageButton qb_exposure;
    private TextView qb_timer;
    private TextView qb_aspect;
    private TextView qb_resolution;

    private HorizontalScrollView lens_scroll;
    private LinearLayout lens_row;
    private int lens_count;

    private LinearLayout mode_row;
    private TextView mode_photo;
    private TextView mode_video;

    ModernUI(MainActivity main_activity, MainUI main_ui) {
        this.main_activity = main_activity;
        this.main_ui = main_ui;
    }

    private int dp(float value) {
        return (int)(value * main_activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    // ---------------------------------------------------------------- layout hooks

    /** Called at the start of MainUI.layoutUI(): undo our overrides so the stock layout code
     *  starts from the XML defaults.
     */
    void beforeLayout() {
        rememberOriginals();
        for(int id : OVERRIDDEN_VIEWS) {
            View view = main_activity.findViewById(id);
            clearRules(view);
            restoreGeometry(id);
        }
        for(int id : HIDDEN_IN_PORTRAIT) {
            restoreGeometry(id);
        }
        zoom_extra_applied = 0; // the stock pass resets the zoom seekbar margins
    }

    /** Called at the end of MainUI.layoutUI(). */
    void apply(boolean portrait, int navigation_gap) {
        createViewsIfNeeded();
        this.portrait_active = portrait;
        this.last_navigation_gap = navigation_gap;
        positionPreview(portrait);
        if( portrait ) {
            layoutPortrait();
        }
        syncVisibility();
        refresh();
    }

    private void rememberOriginals() {
        int [][] groups = { OVERRIDDEN_VIEWS, HIDDEN_IN_PORTRAIT };
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

    private void layoutPortrait() {
        View take_photo = main_activity.findViewById(R.id.take_photo);
        View root = (View)take_photo.getParent();
        int width = root.getWidth();
        if( width <= 0 )
            width = main_activity.getResources().getDisplayMetrics().widthPixels;

        final int side_margin = Math.max(dp(16), width/6 - dp(26));
        final int base = last_navigation_gap + dp(6);
        final int mode_h = dp(40);
        final int lens_h = dp(48);
        final int shutter = dp(80);
        final int side_button = dp(52);
        final int shutter_bottom = base + mode_h + (lens_count > 1 ? lens_h : dp(8));
        final int side_bottom = shutter_bottom + (shutter - side_button)/2;
        final int pill_bottom = shutter_bottom + shutter + dp(18);

        RelativeLayout.LayoutParams lp = resetParams(mode_row, ViewGroup.LayoutParams.WRAP_CONTENT, mode_h);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lp.bottomMargin = base;
        mode_row.setLayoutParams(lp);

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

        // lift the (rotated) zoom slider clear of the control stack
        View zoom = main_activity.findViewById(R.id.zoom_seekbar);
        RelativeLayout.LayoutParams zoom_lp = (RelativeLayout.LayoutParams)zoom.getLayoutParams();
        int zoom_extra = pill_bottom + dp(56) - last_navigation_gap;
        zoom_lp.bottomMargin += zoom_extra - zoom_extra_applied;
        zoom_extra_applied = zoom_extra;
        zoom.setLayoutParams(zoom_lp);
    }

    // ---------------------------------------------------------------- views

    private void createViewsIfNeeded() {
        if( views_created )
            return;
        views_created = true;
        View take_photo = main_activity.findViewById(R.id.take_photo);
        RelativeLayout root = (RelativeLayout)take_photo.getParent();
        int insert_index = root.indexOfChild(main_activity.findViewById(R.id.locker));
        if( insert_index < 0 )
            insert_index = root.getChildCount();

        // quick-settings pill
        quick_bar = new LinearLayout(main_activity);
        quick_bar.setOrientation(LinearLayout.HORIZONTAL);
        quick_bar.setGravity(Gravity.CENTER_VERTICAL);
        quick_bar.setPadding(dp(6), 0, dp(6), 0);
        quick_bar.setBackground(roundedRect(PILL_COLOR));
        addQuickButton(R.drawable.settings, R.string.action_settings, v -> main_activity.clickedSettings(v));
        qb_flash = addQuickButton(R.drawable.flash_off, R.string.cycle_flash, v -> {
            main_activity.clickedCycleFlash(v);
            refresh();
        });
        qb_timer = addQuickText(R.string.uifork_timer, v -> cycleTimer());
        qb_aspect = addQuickText(R.string.uifork_photo_size, v -> main_activity.clickedPopupSettings(v));
        qb_resolution = addQuickText(R.string.uifork_photo_size, v -> main_activity.clickedPopupSettings(v));
        qb_exposure = addQuickButton(R.drawable.ic_exposure_white_48dp, R.string.exposure, v -> main_activity.clickedExposure(v));
        addQuickButton(R.drawable.popup, R.string.action_popup, v -> main_activity.clickedPopupSettings(v));
        root.addView(quick_bar, insert_index++, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        // lens carousel
        lens_scroll = new HorizontalScrollView(main_activity);
        lens_scroll.setHorizontalScrollBarEnabled(false);
        lens_scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        lens_row = new LinearLayout(main_activity);
        lens_row.setOrientation(LinearLayout.HORIZONTAL);
        lens_row.setGravity(Gravity.CENTER);
        lens_scroll.addView(lens_row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(lens_scroll, insert_index++, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        // photo / video / more tabs
        mode_row = new LinearLayout(main_activity);
        mode_row.setOrientation(LinearLayout.HORIZONTAL);
        mode_row.setGravity(Gravity.CENTER);
        mode_photo = addModeTab(R.string.uifork_mode_photo, v -> {
            Preview preview = main_activity.getPreview();
            if( preview.isVideo() && !preview.isVideoRecording() )
                main_activity.clickedSwitchVideo(v);
        });
        mode_video = addModeTab(R.string.uifork_mode_video, v -> {
            if( !main_activity.getPreview().isVideo() )
                main_activity.clickedSwitchVideo(v);
        });
        addModeTab(R.string.uifork_mode_more, v -> main_activity.clickedPopupSettings(v));
        root.addView(mode_row, insert_index, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));

        // mirror the stock buttons' visibility (they still track capture state, immersive mode etc.)
        root.getViewTreeObserver().addOnGlobalLayoutListener(this::syncVisibility);
    }

    private GradientDrawable roundedRect(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(999));
        drawable.setColor(color);
        return drawable;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private void addPressAnimation(View view) {
        view.setStateListAnimator(AnimatorInflater.loadStateListAnimator(main_activity, R.animator.button_animation));
    }

    private ImageButton addQuickButton(int icon, int description, View.OnClickListener listener) {
        ImageButton button = new ImageButton(main_activity);
        button.setImageResource(icon);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setBackground(null);
        int pad = dp(11);
        button.setPadding(pad, pad, pad, pad);
        button.setContentDescription(main_activity.getResources().getString(description));
        button.setOnClickListener(listener);
        addPressAnimation(button);
        quick_bar.addView(button, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return button;
    }

    private TextView addQuickText(int description, View.OnClickListener listener) {
        TextView text = new TextView(main_activity);
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setGravity(Gravity.CENTER);
        text.setMinWidth(dp(44));
        text.setPadding(dp(8), 0, dp(8), 0);
        text.setContentDescription(main_activity.getResources().getString(description));
        text.setOnClickListener(listener);
        addPressAnimation(text);
        quick_bar.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        return text;
    }

    private TextView addModeTab(int label, View.OnClickListener listener) {
        TextView tab = new TextView(main_activity);
        tab.setText(label);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tab.setLetterSpacing(0.04f);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(16), 0, dp(16), 0);
        tab.setTextColor(TAB_INACTIVE_COLOR);
        tab.setOnClickListener(listener);
        mode_row.addView(tab, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return tab;
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
        setVisibility(mode_row, gui);
        setVisibility(lens_scroll, lens_count > 1 ? gui : View.GONE);
        setVisibility(quick_bar, settings);
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

    /** Updates mode tabs and quick-settings pill to reflect the current camera state. */
    void refresh() {
        if( !views_created )
            return;
        Preview preview = main_activity.getPreview();
        if( preview == null )
            return;
        boolean is_video = preview.isVideo();

        styleTab(mode_photo, !is_video);
        styleTab(mode_video, is_video);

        String flash_value = preview.getCurrentFlashValue();
        boolean show_flash = !is_video && preview.supportsFlash() && flash_value != null;
        setVisibility(qb_flash, show_flash ? View.VISIBLE : View.GONE);
        if( show_flash ) {
            int icon;
            switch( flash_value ) {
                case "flash_auto":
                case "flash_frontscreen_auto":
                    icon = R.drawable.flash_auto;
                    break;
                case "flash_on":
                case "flash_frontscreen_on":
                case "flash_torch":
                case "flash_frontscreen_torch":
                case "flash_red_eye":
                    icon = R.drawable.flash_on;
                    break;
                default:
                    icon = R.drawable.flash_off;
                    break;
            }
            qb_flash.setImageResource(icon);
        }

        setVisibility(qb_exposure, main_activity.supportsExposureButton() ? View.VISIBLE : View.GONE);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(main_activity);
        String timer = prefs.getString(PreferenceKeys.TimerPreferenceKey, "0");
        Drawable timer_icon = main_activity.getResources().getDrawable(R.drawable.ic_timer_white_48dp, main_activity.getTheme());
        timer_icon.setBounds(0, 0, dp(22), dp(22));
        qb_timer.setCompoundDrawables(timer_icon, null, null, null);
        qb_timer.setCompoundDrawablePadding("0".equals(timer) ? 0 : dp(3));
        qb_timer.setText("0".equals(timer) ? "" : timer + "s");

        CameraController.Size size = is_video ? null : main_activity.getApplicationInterface().getOutputPictureSize();
        if( size != null ) {
            qb_aspect.setText(aspectLabel(size.width, size.height));
            qb_resolution.setText(megapixelLabel(size.width, size.height));
            setVisibility(qb_aspect, View.VISIBLE);
            setVisibility(qb_resolution, View.VISIBLE);
        }
        else {
            setVisibility(qb_aspect, View.GONE);
            setVisibility(qb_resolution, View.GONE);
        }
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setTextColor(selected ? Color.WHITE : TAB_INACTIVE_COLOR);
        tab.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void cycleTimer() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(main_activity);
        String timer = prefs.getString(PreferenceKeys.TimerPreferenceKey, "0");
        int index = 0;
        for(int i=0;i<TIMER_CYCLE.length;i++) {
            if( TIMER_CYCLE[i].equals(timer) )
                index = i;
        }
        String next = TIMER_CYCLE[(index + 1) % TIMER_CYCLE.length];
        prefs.edit().putString(PreferenceKeys.TimerPreferenceKey, next).apply();
        refresh();
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

    // ---------------------------------------------------------------- lens carousel

    private static class Lens {
        final int logical_id;
        final String physical_id;
        final float view_angle;
        final boolean auto;
        double zoom = Double.NaN;
        String label;

        Lens(int logical_id, String physical_id, float view_angle, boolean auto) {
            this.logical_id = logical_id;
            this.physical_id = physical_id;
            this.view_angle = view_angle;
            this.auto = auto;
        }
    }

    private float viewAngle(String camera_id) {
        if( camera_id == null )
            return -1.0f;
        CameraControllerManager.CameraInfo info = new CameraControllerManager.CameraInfo();
        main_activity.getPreview().getCameraControllerManager().getDescription(info, main_activity, camera_id, false, false);
        return info.view_angle != null ? info.view_angle.getWidth() : -1.0f;
    }

    /** Rebuilds the lens carousel for the cameras facing the same way as the current one. Called
     *  whenever a camera is opened.
     */
    void rebuildLensChips() {
        createViewsIfNeeded();
        lens_row.removeAllViews();
        lens_count = 0;
        Preview preview = main_activity.getPreview();
        if( preview == null || preview.getCameraControllerManager() == null ) {
            syncVisibility();
            return;
        }
        try {
            int current_id = preview.getCameraId();
            String current_physical = main_activity.getApplicationInterface().getCameraIdSPhysicalPref();
            List<Integer> logical_ids = main_activity.getSameFacingLogicalCameras(current_id);

            String [] camera2_ids = null;
            if( preview.usingCamera2API() ) {
                CameraManager manager = (CameraManager)main_activity.getSystemService(Context.CAMERA_SERVICE);
                camera2_ids = manager.getCameraIdList();
            }

            List<Lens> lenses = new ArrayList<>();
            for(int logical_id : logical_ids) {
                String camera2_id = camera2_ids != null && logical_id < camera2_ids.length ? camera2_ids[logical_id] : null;
                boolean has_physical = logical_id == current_id && preview.hasPhysicalCameras();
                lenses.add(new Lens(logical_id, null, viewAngle(camera2_id), has_physical));
                if( has_physical ) {
                    Set<String> physical_ids = preview.getPhysicalCameras();
                    for(String physical_id : physical_ids) {
                        lenses.add(new Lens(logical_id, physical_id, viewAngle(physical_id), false));
                    }
                }
            }

            // the first logical camera for this facing is the "main" lens (1x)
            float reference_angle = lenses.isEmpty() ? -1.0f : lenses.get(0).view_angle;
            for(Lens lens : lenses) {
                if( reference_angle > 0.0f && lens.view_angle > 0.0f ) {
                    lens.zoom = Math.tan(Math.toRadians(reference_angle)/2.0) / Math.tan(Math.toRadians(lens.view_angle)/2.0);
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
                return Double.compare(a.zoom, b.zoom);
            });

            Set<String> used_labels = new HashSet<>();
            for(Lens lens : lenses) {
                String label;
                if( lens.auto )
                    label = main_activity.getResources().getString(R.string.uifork_lens_auto);
                else if( Double.isNaN(lens.zoom) )
                    label = String.valueOf(lens.logical_id);
                else
                    label = zoomLabel(lens.zoom);
                String unique = label;
                int suffix = 2;
                while( used_labels.contains(unique) ) {
                    unique = label + suffix;
                    suffix++;
                }
                used_labels.add(unique);
                lens.label = unique;
            }

            if( lenses.size() > 1 ) {
                for(Lens lens : lenses) {
                    boolean selected = lens.logical_id == current_id &&
                            (lens.physical_id == null ? current_physical == null : lens.physical_id.equals(current_physical));
                    addLensChip(lens, selected);
                }
                lens_count = lenses.size();
            }
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
        chip.setContentDescription(main_activity.getResources().getString(R.string.uifork_lens) + " " + lens.label);
        chip.setSelected(selected);
        addPressAnimation(chip);
        chip.setOnClickListener(v -> {
            Preview preview = main_activity.getPreview();
            if( v.isSelected() || preview.isOpeningCamera() || preview.isVideoRecording() )
                return;
            main_ui.closePopup();
            main_activity.userSwitchToCamera(lens.logical_id, lens.physical_id);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.setMargins(dp(5), 0, dp(5), 0);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lens_row.addView(chip, lp);
    }
}
