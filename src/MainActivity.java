package com.codex.standbycanvas;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.database.Cursor;
import android.provider.Settings;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.InputStream;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.media.MediaPlayer;
import android.view.TextureView;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.widget.ImageView;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
    private DashboardView dashboard;
    private LinearLayout toolbar;
    private Widget weatherWidgetPending;
    private Widget photoWidgetPending;
    private Widget calendarWidgetPending;
    private Typeface customFont;

    private ImageView bgImageView;
    private TextureView bgVideoView;
    private MediaPlayer bgMediaPlayer;
    private String currentBgSource = null;
    private Bitmap lastAlbumArt = null;
    private SharedPreferences globalPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        globalPrefs = getSharedPreferences("standby_canvas_global", MODE_PRIVATE);

        try { customFont = Typeface.createFromAsset(getAssets(), "fonts/Inter-Regular.ttf"); } catch (Exception e) {}

        FrameLayout root = new FrameLayout(this);
        
        bgImageView = new ImageView(this);
        bgImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bgImageView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bgVideoView = new TextureView(this);
        bgVideoView.setVisibility(View.GONE);
        root.addView(bgVideoView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dashboard = new DashboardView(this);
        root.addView(dashboard, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(14, 10, 14, 10);
        toolbar.setBackgroundColor(0x77000000);
        toolbar.addView(makeButton("Edit", new View.OnClickListener() {
            @Override public void onClick(View v) { toggleEdit(); }
        }));
        toolbar.addView(makeButton("Add", new View.OnClickListener() {
            @Override public void onClick(View v) { showAddDialog(); }
        }));
        toolbar.addView(makeButton("Theme", new View.OnClickListener() {
            @Override public void onClick(View v) { dashboard.nextTheme(); }
        }));
        toolbar.addView(makeButton("Background", new View.OnClickListener() {
            @Override public void onClick(View v) { showBackgroundSettings(); }
        }));
        toolbar.addView(makeButton("Reset", new View.OnClickListener() {
            @Override public void onClick(View v) { confirmReset(); }
        }));

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        barParams.topMargin = 16;
        root.addView(toolbar, barParams);
        setContentView(root);
        enterImmersive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
    }

    private Button makeButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(0x55242A2E);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(88), dp(42));
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        button.setLayoutParams(params);
        return button;
    }

    private void toggleEdit() {
        dashboard.setEditMode(!dashboard.isEditMode());
        toolbar.setAlpha(dashboard.isEditMode() ? 1f : 0.42f);
        Toast.makeText(this, dashboard.isEditMode() ? "Edit mode: drag, resize, tap widget to configure" : "Locked dashboard", Toast.LENGTH_SHORT).show();
    }

    private void showAddDialog() {
        final String[] types = {"Clock", "Date", "Battery", "Note", "Timer", "Weather", "Music", "Photo", "Calendar", "News"};
        new AlertDialog.Builder(this)
                .setTitle("Add widget")
                .setItems(types, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dashboard.addWidget(types[which].toLowerCase(Locale.US));
                    }
                })
                .show();
    }

    private void showBackgroundSettings() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(20), dp(20), dp(20));
        
        Button pickBtn = new Button(this);
        pickBtn.setText("Pick Image or Video");
        pickBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
            startActivityForResult(intent, 104);
        });
        layout.addView(pickBtn);

        Button clearBtn = new Button(this);
        clearBtn.setText("Clear Custom Background");
        clearBtn.setOnClickListener(v -> {
            globalPrefs.edit().remove("custom_bg_uri").apply();
            refreshBackground();
        });
        layout.addView(clearBtn);

        android.widget.TextView blurLabel = new android.widget.TextView(this);
        blurLabel.setText("Blur / Diffusion Level");
        blurLabel.setPadding(0, dp(10), 0, dp(10));
        layout.addView(blurLabel);
        
        android.widget.SeekBar blurBar = new android.widget.SeekBar(this);
        blurBar.setMax(100);
        blurBar.setProgress(globalPrefs.getInt("bg_blur", 25));
        blurBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar sb, int p, boolean b) {
                globalPrefs.edit().putInt("bg_blur", p).apply();
                applyBlurEffect();
            }
            public void onStartTrackingTouch(android.widget.SeekBar sb) {}
            public void onStopTrackingTouch(android.widget.SeekBar sb) {}
        });
        layout.addView(blurBar);

        new AlertDialog.Builder(this)
                .setTitle("Background Settings")
                .setView(layout)
                .setPositiveButton("Close", null)
                .show();
    }

    private void applyBlurEffect() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            int blur = globalPrefs.getInt("bg_blur", 25);
            if (blur > 0) {
                RenderEffect effect = RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP);
                bgImageView.setRenderEffect(effect);
                bgVideoView.setRenderEffect(effect);
            } else {
                bgImageView.setRenderEffect(null);
                bgVideoView.setRenderEffect(null);
            }
        }
    }

    public void checkBackground() {
        String expected = MusicListenerService.albumArt != null ? "ALBUM_ART" : globalPrefs.getString("custom_bg_uri", null);
        if (expected == null && currentBgSource != null) refreshBackground();
        else if (expected != null && !expected.equals(currentBgSource)) refreshBackground();
        else if ("ALBUM_ART".equals(currentBgSource) && lastAlbumArt != MusicListenerService.albumArt) refreshBackground();
    }

    public void refreshBackground() {
        String newSource = MusicListenerService.albumArt != null ? "ALBUM_ART" : globalPrefs.getString("custom_bg_uri", null);

        if (newSource == null) {
            bgImageView.setVisibility(View.GONE);
            bgVideoView.setVisibility(View.GONE);
            if (bgMediaPlayer != null) { bgMediaPlayer.release(); bgMediaPlayer = null; }
            currentBgSource = null;
            return;
        }

        if (newSource.equals(currentBgSource) && newSource.equals("ALBUM_ART") && lastAlbumArt == MusicListenerService.albumArt) return;

        currentBgSource = newSource;
        if ("ALBUM_ART".equals(newSource)) {
            if (bgMediaPlayer != null) { bgMediaPlayer.release(); bgMediaPlayer = null; }
            bgVideoView.setVisibility(View.GONE);
            bgImageView.setVisibility(View.VISIBLE);
            bgImageView.setImageBitmap(MusicListenerService.albumArt);
            lastAlbumArt = MusicListenerService.albumArt;
        } else {
            String mime = getContentResolver().getType(Uri.parse(newSource));
            boolean isVideo = mime != null && mime.startsWith("video");
            
            if (isVideo) {
                bgImageView.setVisibility(View.GONE);
                bgVideoView.setVisibility(View.VISIBLE);
                try {
                    if (bgMediaPlayer != null) { bgMediaPlayer.release(); bgMediaPlayer = null; }
                    bgMediaPlayer = new MediaPlayer();
                    bgMediaPlayer.setDataSource(this, Uri.parse(newSource));
                    bgMediaPlayer.setLooping(true);
                    bgMediaPlayer.setVolume(0, 0);
                    
                    if (bgVideoView.isAvailable()) {
                        bgMediaPlayer.setSurface(new android.view.Surface(bgVideoView.getSurfaceTexture()));
                        bgMediaPlayer.prepareAsync();
                        bgMediaPlayer.setOnPreparedListener(mp -> mp.start());
                    } else {
                        bgVideoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                            @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int w, int h) {
                                try {
                                    bgMediaPlayer.setSurface(new android.view.Surface(st));
                                    bgMediaPlayer.prepareAsync();
                                    bgMediaPlayer.setOnPreparedListener(mp -> mp.start());
                                } catch (Exception e) {}
                            }
                            @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int w, int h) {}
                            @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) { return false; }
                            @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) {}
                        });
                    }
                } catch (Exception e) {}
            } else {
                if (bgMediaPlayer != null) { bgMediaPlayer.release(); bgMediaPlayer = null; }
                bgVideoView.setVisibility(View.GONE);
                bgImageView.setVisibility(View.VISIBLE);
                bgImageView.setImageURI(Uri.parse(newSource));
            }
        }
        applyBlurEffect();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Reset dashboard?")
                .setMessage("This restores the starter layout.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dashboard.reset();
                    }
                })
                .show();
    }

    public void setupWeather(Widget widget) {
        weatherWidgetPending = widget;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 101);
                return;
            }
        }
        fetchLocationAndWeather(widget);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == 101) {
            if (granted && weatherWidgetPending != null) fetchLocationAndWeather(weatherWidgetPending);
            else if (weatherWidgetPending != null) { weatherWidgetPending.condition = "No permission"; if (dashboard != null) dashboard.invalidate(); }
        } else if (requestCode == 102) {
            if (granted) launchPhotoPicker();
        } else if (requestCode == 103) {
            if (granted && calendarWidgetPending != null) fetchCalendarEvents(calendarWidgetPending);
            else if (calendarWidgetPending != null) { calendarWidgetPending.condition = "No permission"; if (dashboard != null) dashboard.invalidate(); }
        }
    }

    public void setupPhoto(Widget widget) {
        photoWidgetPending = widget;
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, 102);
        } else {
            launchPhotoPicker();
        }
    }

    private void launchPhotoPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, 102);
    }

    public void setupCalendar(Widget widget) {
        calendarWidgetPending = widget;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, 103);
        } else {
            fetchCalendarEvents(widget);
        }
    }

    private void fetchCalendarEvents(Widget widget) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return;
        new Thread(() -> {
            try {
                long now = System.currentTimeMillis();
                Uri uri = CalendarContract.Events.CONTENT_URI;
                String[] projection = {CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART};
                String selection = CalendarContract.Events.DTSTART + " >= ?";
                String[] selectionArgs = {String.valueOf(now)};
                String sortOrder = CalendarContract.Events.DTSTART + " ASC LIMIT 2";
                Cursor cursor = getContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);
                StringBuilder events = new StringBuilder();
                if (cursor != null) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault());
                    while (cursor.moveToNext()) {
                        String title = cursor.getString(0);
                        long start = cursor.getLong(1);
                        events.append(title).append(" - ").append(sdf.format(new java.util.Date(start))).append("\n");
                    }
                    cursor.close();
                }
                if (events.length() == 0) events.append("No upcoming events");
                widget.condition = events.toString().trim();
                runOnUiThread(() -> { if (dashboard != null) dashboard.invalidate(); });
            } catch (Exception e) {
                widget.condition = "Error reading calendar";
                runOnUiThread(() -> { if (dashboard != null) dashboard.invalidate(); });
            }
        }).start();
    }

    public void setupMusic(Widget widget) {
        String enabledListeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean isEnabled = enabledListeners != null && enabledListeners.contains(getPackageName());
        if (!isEnabled) {
            Toast.makeText(this, "Please enable Notification Access for Standby Canvas", Toast.LENGTH_LONG).show();
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        }
    }

    public void setupNews(Widget widget) {
        new Thread(() -> {
            try {
                URL url = new URL("https://feeds.bbci.co.uk/news/world/rss.xml");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(10000);
                conn.connect();
                InputStream stream = conn.getInputStream();
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser myparser = factory.newPullParser();
                myparser.setInput(stream, null);
                int event = myparser.getEventType();
                StringBuilder headlines = new StringBuilder();
                boolean isTitle = false;
                while (event != XmlPullParser.END_DOCUMENT) {
                    String name = myparser.getName();
                    if (event == XmlPullParser.START_TAG && "title".equals(name)) isTitle = true;
                    else if (event == XmlPullParser.TEXT && isTitle) {
                        String text = myparser.getText();
                        if (!text.equals("BBC News - World") && !text.equals("BBC News") && text.trim().length() > 0) {
                            headlines.append(text).append("   •   ");
                        }
                    }
                    else if (event == XmlPullParser.END_TAG && "title".equals(name)) isTitle = false;
                    event = myparser.next();
                }
                widget.condition = headlines.toString();
                if (widget.condition.endsWith("   •   ")) {
                    widget.condition = widget.condition.substring(0, widget.condition.length() - 7);
                }
                widget.tickerOffset = 0;
            } catch (Exception e) {
                widget.condition = "Error loading news";
            }
            runOnUiThread(() -> { if (dashboard != null) dashboard.invalidate(); });
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 102 && resultCode == RESULT_OK && data != null && photoWidgetPending != null) {
            photoWidgetPending.photoUri = data.getData().toString();
            dashboard.save();
            dashboard.loadPhotoBitmap(photoWidgetPending);
        } else if (requestCode == 104 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            globalPrefs.edit().putString("custom_bg_uri", uri.toString()).apply();
            refreshBackground();
        }
    }

    private void fetchLocationAndWeather(final Widget widget) {
        widget.condition = "Loading...";
        if (dashboard != null) dashboard.invalidate();
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            Location loc = null;
            if (lm != null) {
                loc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (loc == null) loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (loc != null) {
                fetchWeatherFromApi(widget, loc.getLatitude(), loc.getLongitude());
            } else {
                widget.condition = "No location data";
                if (dashboard != null) dashboard.invalidate();
            }
        } catch (SecurityException e) {
            widget.condition = "Loc error";
            if (dashboard != null) dashboard.invalidate();
        }
    }

    private void fetchWeatherFromApi(final Widget widget, final double lat, final double lon) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current_weather=true");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setReadTimeout(5000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) response.append(line);
                    in.close();
                    JSONObject json = new JSONObject(response.toString());
                    JSONObject current = json.getJSONObject("current_weather");
                    final double temp = current.getDouble("temperature");
                    final int code = current.getInt("weathercode");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            widget.temp = temp + "°";
                            widget.condition = getWeatherDesc(code);
                            if (dashboard != null) dashboard.invalidate();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            widget.condition = "API Failed";
                            if (dashboard != null) dashboard.invalidate();
                        }
                    });
                }
            }
        }).start();
    }

    private String getWeatherDesc(int code) {
        if (code == 0) return "Clear";
        if (code == 1 || code == 2 || code == 3) return "Partly Cloudy";
        if (code >= 45 && code <= 48) return "Fog";
        if (code >= 51 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 95) return "Thunderstorm";
        return "Cloudy";
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    public class DashboardView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final List<List<Widget>> pages = new ArrayList<>();
        private int currentPage = 0;
        private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());

        private List<Widget> getWidgets() {
            if (pages.isEmpty()) pages.add(new ArrayList<>());
            return pages.get(currentPage);
        }

        private final SharedPreferences prefs;
        private final Runnable ticker;
        private int theme = 0;
        private int battery = 0;
        private boolean charging = false;
        private boolean editMode = true;
        private Widget active;
        private float lastX;
        private float lastY;
        private float startX;
        private float startY;
        private int touchMode;

        private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                battery = scale > 0 ? Math.round(level * 100f / scale) : 0;
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
                invalidate();
            }
        };

        public DashboardView(Context context) {
            super(context);
            setFocusable(true);
            prefs = context.getSharedPreferences("standby_canvas", MODE_PRIVATE);
            textPaint.setColor(Color.WHITE);
            if (customFont != null) {
                textPaint.setTypeface(customFont);
                paint.setTypeface(customFont);
            } else {
                textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
            }
            ticker = new Runnable() {
                @Override public void run() {
                    invalidate();
                    MainActivity.this.checkBackground();
                    boolean hasFastAnim = false;
                    if (!pages.isEmpty() && currentPage < pages.size()) {
                        for (Widget w : pages.get(currentPage)) {
                            if ("news".equals(w.type) || "music".equals(w.type)) hasFastAnim = true;
                        }
                    }
                    handler.postDelayed(this, hasFastAnim ? 16 : 1000);
                }
            };
            load();
            MainActivity.this.refreshBackground();
            context.registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            handler.post(ticker);
        }

        public boolean isEditMode() { return editMode; }

        public void setEditMode(boolean value) {
            editMode = value;
            active = null;
            invalidate();
        }

        public void nextTheme() {
            theme = (theme + 1) % 4;
            save();
            invalidate();
        }

        public void addWidget(String type) {
            Widget widget = new Widget(type, 0.16f, 0.18f, 0.28f, 0.22f);
            if ("clock".equals(type)) { widget.w = 0.34f; widget.h = 0.26f; }
            if ("note".equals(type)) { widget.label = "Tap to edit"; widget.w = 0.32f; widget.h = 0.24f; }
            if ("timer".equals(type)) { widget.startedAt = System.currentTimeMillis(); }
            if ("weather".equals(type)) { MainActivity.this.setupWeather(widget); }
            if ("photo".equals(type)) { MainActivity.this.setupPhoto(widget); }
            if ("calendar".equals(type)) { MainActivity.this.setupCalendar(widget); }
            if ("music".equals(type)) { MainActivity.this.setupMusic(widget); }
            if ("news".equals(type)) { widget.w = 0.5f; MainActivity.this.setupNews(widget); }
            widget.x = 0.08f + (getWidgets().size() % 4) * 0.18f;
            widget.y = 0.18f + (getWidgets().size() % 3) * 0.20f;
            getWidgets().add(widget);
            active = widget;
            editMode = true;
            save();
            invalidate();
        }

        public void reset() {
            pages.clear();
            addDefaults();
            theme = 0;
            editMode = true;
            save();
            invalidate();
        }

        public void loadPhotoBitmap(final Widget widget) {
            if (widget.photoUri == null || widget.photoUri.isEmpty()) return;
            new Thread(() -> {
                try {
                    Uri uri = Uri.parse(widget.photoUri);
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    int maxDim = 800;
                    float scale = Math.min((float) maxDim / bitmap.getWidth(), (float) maxDim / bitmap.getHeight());
                    if (scale < 1) {
                        Matrix matrix = new Matrix();
                        matrix.postScale(scale, scale);
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    }
                    widget.bitmap = bitmap;
                    runOnUiThread(() -> invalidate());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawBackground(canvas);
            for (Widget widget : getWidgets()) {
                drawWidget(canvas, widget);
            }
            if (editMode) {
                drawEditHint(canvas);
            }
        }

        private void drawBackground(Canvas canvas) {
            if (MainActivity.this.currentBgSource != null) {
                paint.setColor(0xCC000000); 
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            } else {
                int[][] palettes = {
                        {0xFF101418, 0xFF22343C, 0xFF8ED7C6},
                        {0xFF160F22, 0xFF3D233F, 0xFFFFC857},
                        {0xFF09111F, 0xFF1F4E5F, 0xFFFF6B6B},
                        {0xFF111111, 0xFF2E2E2E, 0xFFB7F000}
                };
                int[] p = palettes[theme];
                paint.setShader(new LinearGradient(0, 0, getWidth(), getHeight(), p[0], p[1], Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
                paint.setShader(null);
            }
            
            paint.setColor(0x55FFFFFF);
            float indW = dp(16);
            float startX = (getWidth() - (pages.size() * indW)) / 2f;
            for (int i=0; i<pages.size(); i++) {
                paint.setAlpha(i == currentPage ? 255 : 85);
                canvas.drawCircle(startX + (i * indW) + dp(4), getHeight() - dp(16), dp(3), paint);
            }
            paint.setAlpha(255);
        }

        private void drawWidget(Canvas canvas, Widget widget) {
            RectF r = rect(widget);
            paint.setColor(widget == active && editMode ? 0x77000000 : 0x55000000);
            canvas.drawRoundRect(r, dp(24), dp(24), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(widget == active && editMode ? dp(2) : dp(1));
            paint.setColor(widget == active && editMode ? 0x88FFFFFF : 0x2AFFFFFF);
            canvas.drawRoundRect(r, dp(24), dp(24), paint);
            paint.setStyle(Paint.Style.FILL);

            String primary = "";
            String secondary = "";
            Date now = new Date();
            if ("clock".equals(widget.type)) { primary = clockFormat.format(now); secondary = "Standby Canvas"; }
            else if ("date".equals(widget.type)) { primary = dateFormat.format(now); secondary = "Today"; }
            else if ("battery".equals(widget.type)) { primary = battery + "%"; secondary = charging ? "Charging" : "Battery"; }
            else if ("note".equals(widget.type)) { primary = widget.label == null || widget.label.length() == 0 ? "Note" : widget.label; secondary = ""; }
            else if ("timer".equals(widget.type)) { long sec = Math.max(0, (System.currentTimeMillis() - widget.startedAt) / 1000); primary = String.format(Locale.US, "%02d:%02d:%02d", sec/3600, (sec/60)%60, sec%60); secondary = "Timer"; }
            else if ("weather".equals(widget.type)) { primary = widget.temp; secondary = widget.condition; }
            else if ("music".equals(widget.type)) { primary = MusicListenerService.currentTrack; secondary = MusicListenerService.currentArtist; }
            else if ("calendar".equals(widget.type)) { primary = "Upcoming"; secondary = widget.condition; }
            else if ("photo".equals(widget.type)) { primary = "Tap to pick photo"; secondary = ""; }
            else if ("news".equals(widget.type)) { primary = "Headlines"; secondary = widget.condition; }

            if ("photo".equals(widget.type) && widget.bitmap != null) {
                canvas.save();
                Path clipPath = new Path();
                clipPath.addRoundRect(r, dp(24), dp(24), Path.Direction.CW);
                canvas.clipPath(clipPath);
                float scale = Math.max(r.width() / widget.bitmap.getWidth(), r.height() / widget.bitmap.getHeight());
                Matrix m = new Matrix();
                m.postScale(scale, scale);
                m.postTranslate(r.left + (r.width() - widget.bitmap.getWidth() * scale) / 2f, r.top + (r.height() - widget.bitmap.getHeight() * scale) / 2f);
                canvas.drawBitmap(widget.bitmap, m, paint);
                canvas.restore();
            } else if ("news".equals(widget.type)) {
                textPaint.setColor(Color.WHITE);
                textPaint.setTextAlign(Paint.Align.LEFT);
                textPaint.setTextSize(dp(22) * widget.textScale);
                canvas.drawText(primary, r.left + dp(22), r.top + dp(32) * widget.textScale, textPaint);
                
                if (widget.layoutStyle == 0) { // Ticker
                    widget.tickerOffset -= 1.5f;
                    float textW = textPaint.measureText(secondary);
                    if (widget.tickerOffset < -textW) widget.tickerOffset = r.width();
                    canvas.save();
                    canvas.clipRect(r.left + dp(10), r.top + dp(34), r.right - dp(10), r.bottom - dp(10));
                    textPaint.setTextSize(dp(18) * widget.textScale);
                    canvas.drawText(secondary, r.left + dp(24) + widget.tickerOffset, r.top + dp(40) + textPaint.getTextSize(), textPaint);
                    canvas.restore();
                } else { // List
                    textPaint.setTextSize(dp(16) * widget.textScale);
                    textPaint.setColor(0xEEFFFFFF);
                    String[] news = secondary.split("   •   ");
                    float yy = r.top + dp(48) * widget.textScale + dp(14);
                    canvas.save();
                    canvas.clipRect(r.left + dp(10), r.top + dp(34), r.right - dp(10), r.bottom - dp(10));
                    for (String item : news) {
                        if (item.trim().length() > 0) {
                            canvas.drawText("• " + item, r.left + dp(20), yy, textPaint);
                            yy += textPaint.getTextSize() * 1.5f;
                        }
                    }
                    canvas.restore();
                }
            } else if ("calendar".equals(widget.type)) {
                textPaint.setTextAlign(Paint.Align.LEFT);
                textPaint.setTextSize(dp(16) * widget.textScale);
                textPaint.setColor(0xAAFFFFFF);
                canvas.drawText(primary, r.left + dp(22), r.top + dp(32) * widget.textScale, textPaint);
                textPaint.setTextSize(dp(18) * widget.textScale);
                textPaint.setColor(Color.WHITE);
                if (secondary != null && secondary.contains("\n")) {
                    String[] evs = secondary.split("\n");
                    float yy = r.top + dp(42) * widget.textScale + dp(20);
                    for (String ev : evs) {
                        if (ev.trim().length() > 0) {
                            canvas.drawCircle(r.left + dp(26), yy - dp(6) * widget.textScale, dp(3) * widget.textScale, textPaint);
                            String[] words = ev.split(" ");
                            StringBuilder line = new StringBuilder();
                            for (String word : words) {
                                if (textPaint.measureText(line.toString() + word + " ") < r.width() - dp(50)) {
                                    line.append(word).append(" ");
                                } else {
                                    canvas.drawText(line.toString(), r.left + dp(38), yy, textPaint);
                                    yy += dp(22) * widget.textScale;
                                    line = new StringBuilder(word).append(" ");
                                }
                            }
                            canvas.drawText(line.toString(), r.left + dp(38), yy, textPaint);
                            yy += dp(26) * widget.textScale;
                        }
                    }
                } else {
                    canvas.drawText(secondary, r.left + dp(22), r.top + dp(42) * widget.textScale + dp(20), textPaint);
                }
            } else if ("music".equals(widget.type)) {
                float titleSize = Math.max(dp(20), Math.min(dp(42), r.width() / Math.max(4f, primary.length() * 0.5f))) * widget.textScale;
                textPaint.setTextAlign(Paint.Align.LEFT);
                textPaint.setColor(Color.WHITE);
                textPaint.setTextSize(titleSize);
                
                float textLeft = r.left + dp(22);
                if (MusicListenerService.albumArt != null) {
                    float artSize = dp(60) * widget.textScale;
                    canvas.save();
                    Path p = new Path();
                    p.addRoundRect(new RectF(r.left + dp(20), r.top + dp(20), r.left + dp(20) + artSize, r.top + dp(20) + artSize), dp(8), dp(8), Path.Direction.CW);
                    canvas.clipPath(p);
                    canvas.drawBitmap(Bitmap.createScaledBitmap(MusicListenerService.albumArt, (int)artSize, (int)artSize, true), r.left + dp(20), r.top + dp(20), paint);
                    canvas.restore();
                    textLeft += artSize + dp(12);
                }
                
                float maxTextW = r.right - textLeft - dp(10);
                float trackW = textPaint.measureText(primary);
                if (trackW > maxTextW) {
                    widget.tickerOffset -= 1.0f;
                    String scrollText = primary + "   ...   ";
                    float scrollW = textPaint.measureText(scrollText);
                    if (widget.tickerOffset < -scrollW) widget.tickerOffset = maxTextW;
                    canvas.save();
                    canvas.clipRect(textLeft, r.top + dp(10), r.right - dp(10), r.bottom);
                    canvas.drawText(scrollText, textLeft + widget.tickerOffset, r.top + dp(38) * widget.textScale, textPaint);
                    canvas.restore();
                } else {
                    canvas.drawText(primary, textLeft, r.top + dp(38) * widget.textScale, textPaint);
                    widget.tickerOffset = 0;
                }
                textPaint.setTextSize(Math.max(dp(12), titleSize * 0.5f));
                textPaint.setColor(0xCCFFFFFF);
                canvas.drawText(secondary, textLeft, r.top + dp(38) * widget.textScale + textPaint.getTextSize() * 1.5f, textPaint);
                
                float btnY = r.bottom - dp(28);
                paint.setColor(0xEEFFFFFF);
                
                // Prev
                Path prev = new Path();
                float px = r.centerX() - dp(40);
                prev.moveTo(px + dp(6), btnY - dp(6)); prev.lineTo(px - dp(4), btnY); prev.lineTo(px + dp(6), btnY + dp(6)); prev.close();
                canvas.drawPath(prev, paint);
                canvas.drawRect(px - dp(6), btnY - dp(5), px - dp(4), btnY + dp(5), paint);
                
                // Play/Pause
                if (MusicListenerService.sessionToken != null) {
                    canvas.drawCircle(r.centerX(), btnY, dp(16), paint);
                    paint.setColor(0xFF121212);
                    canvas.drawRect(r.centerX() - dp(4), btnY - dp(5), r.centerX() - dp(1), btnY + dp(5), paint);
                    canvas.drawRect(r.centerX() + dp(1), btnY - dp(5), r.centerX() + dp(4), btnY + dp(5), paint);
                }
                
                // Next
                paint.setColor(0xEEFFFFFF);
                Path next = new Path();
                float nx = r.centerX() + dp(40);
                next.moveTo(nx - dp(6), btnY - dp(6)); next.lineTo(nx + dp(4), btnY); next.lineTo(nx - dp(6), btnY + dp(6)); next.close();
                canvas.drawPath(next, paint);
                canvas.drawRect(nx + dp(4), btnY - dp(5), nx + dp(6), btnY + dp(5), paint);
            } else {
                if ("photo".equals(widget.type)) {
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    textPaint.setTextSize(dp(18) * widget.textScale);
                    textPaint.setColor(Color.WHITE);
                    canvas.drawText(primary, r.centerX(), r.centerY(), textPaint);
                } else {
                    float titleSize = Math.max(dp(24), Math.min(r.height() * 0.42f, r.width() / Math.max(3.2f, primary.length() * 0.55f))) * widget.textScale;
                    textPaint.setTextAlign(Paint.Align.LEFT);
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTextSize(titleSize);
                    
                    if ("clock".equals(widget.type)) {
                        textPaint.setTextSize(titleSize * 1.3f);
                        canvas.drawText(primary, r.left + dp(24), r.centerY() + titleSize * 0.4f, textPaint);
                    } else {
                        canvas.drawText(primary, r.left + dp(22), r.centerY() + titleSize * 0.20f, textPaint);
                        textPaint.setTextSize(Math.max(dp(13), titleSize * 0.22f));
                        textPaint.setColor(0xCCFFFFFF);
                        if (secondary != null && secondary.contains("\n")) {
                            String[] lines = secondary.split("\n");
                            float yy = r.top + dp(34);
                            for (String line : lines) {
                                canvas.drawText(line, r.left + dp(24), yy, textPaint);
                                yy += textPaint.getTextSize() * 1.5f;
                            }
                        } else if (secondary.length() > 0) {
                            canvas.drawText(secondary, r.left + dp(24), r.top + dp(34), textPaint);
                        }
                    }
                }
            }

            if ("battery".equals(widget.type)) drawBattery(canvas, r);

            if (editMode && widget == active) {
                paint.setColor(0xCCFFFFFF);
                canvas.drawCircle(r.right - dp(24), r.bottom - dp(24), dp(8), paint);
            }
        }

        private void drawBattery(Canvas canvas, RectF r) {
            float left = r.left + dp(24);
            float top = r.bottom - dp(42);
            float width = Math.min(r.width() - dp(60), dp(190));
            RectF body = new RectF(left, top, left + width, top + dp(18));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(0xDDFFFFFF);
            canvas.drawRoundRect(body, dp(5), dp(5), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(charging ? 0xFF8ED7C6 : 0xFFFFC857);
            canvas.drawRoundRect(new RectF(body.left + dp(3), body.top + dp(3), body.left + dp(3) + (body.width() - dp(6)) * battery / 100f, body.bottom - dp(3)), dp(3), dp(3), paint);
            canvas.drawRoundRect(new RectF(body.right + dp(3), body.top + dp(5), body.right + dp(10), body.bottom - dp(5)), dp(2), dp(2), paint);
        }

        private void drawEditHint(Canvas canvas) {
            textPaint.setTextAlign(Paint.Align.RIGHT);
            textPaint.setColor(0xAAFFFFFF);
            textPaint.setTextSize(dp(14));
            canvas.drawText("Tap a widget to edit text/delete, drag to move, corner to resize", getWidth() - dp(18), getHeight() - dp(18), textPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!editMode) return true;
            float x = event.getX();
            float y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    active = hit(x, y);
                    lastX = x;
                    lastY = y;
                    startX = x;
                    startY = y;
                    touchMode = active != null && isOnResizeHandle(active, x, y) ? 2 : 1;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (active != null) {
                        float dx = (x - lastX) / Math.max(1, getWidth());
                        float dy = (y - lastY) / Math.max(1, getHeight());
                        if (touchMode == 2) {
                            active.w = clamp(active.w + dx, 0.16f, 0.82f);
                            active.h = clamp(active.h + dy, 0.14f, 0.72f);
                        } else {
                            active.x = clamp(active.x + dx, 0f, 1f - active.w);
                            active.y = clamp(active.y + dy, 0f, 1f - active.h);
                        }
                        lastX = x;
                        lastY = y;
                        save();
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = x - startX;
                    float dy = y - startY;
                    if (!isDragging() && Math.abs(dx) > dp(80) && Math.abs(dy) < dp(60)) {
                        if (dx < 0 && currentPage < pages.size() - 1) currentPage++;
                        else if (dx > 0 && currentPage > 0) currentPage--;
                        else if (dx < 0 && currentPage == pages.size() - 1) {
                            pages.add(new ArrayList<>());
                            currentPage++;
                        }
                        invalidate();
                        return true;
                    }
                    if (active != null && "music".equals(active.type) && y > rect(active).bottom - dp(48) && Math.abs(dx) < dp(10)) {
                        if (MusicListenerService.sessionToken != null) {
                            MediaController mc = new MediaController(getContext(), MusicListenerService.sessionToken);
                            if (x < rect(active).centerX() - dp(20)) mc.getTransportControls().skipToPrevious();
                            else if (x > rect(active).centerX() + dp(20)) mc.getTransportControls().skipToNext();
                            else {
                                PlaybackState pb = mc.getPlaybackState();
                                if (pb != null && pb.getState() == PlaybackState.STATE_PLAYING) mc.getTransportControls().pause();
                                else mc.getTransportControls().play();
                            }
                        }
                        active = null; touchMode = 0; return true;
                    }
                    if (active != null && Math.abs(dx) < dp(4) && Math.abs(dy) < dp(4)) {
                        showWidgetOptions(active);
                    }
                    touchMode = 0;
                    return true;
            }
            return true;
        }

        private boolean isDragging() {
            return touchMode != 0 && (Math.abs(lastX - startX) > dp(4) || Math.abs(lastY - startY) > dp(4));
        }

        private void showWidgetOptions(final Widget widget) {
            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(dp(20), dp(20), dp(20), dp(20));

            if ("note".equals(widget.type)) {
                final EditText input = new EditText(getContext());
                input.setText(widget.label);
                layout.addView(input);
                input.addTextChangedListener(new android.text.TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    public void afterTextChanged(android.text.Editable s) { widget.label = s.toString(); invalidate(); save(); }
                });
            }

            android.widget.TextView scaleLabel = new android.widget.TextView(getContext());
            scaleLabel.setText("Text Size Scaling");
            scaleLabel.setPadding(0, dp(10), 0, dp(10));
            layout.addView(scaleLabel);
            
            android.widget.SeekBar scaleBar = new android.widget.SeekBar(getContext());
            scaleBar.setMax(20);
            scaleBar.setProgress((int)(widget.textScale * 10f));
            scaleBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(android.widget.SeekBar sb, int p, boolean b) {
                    widget.textScale = Math.max(0.4f, p / 10f);
                    invalidate();
                }
                public void onStartTrackingTouch(android.widget.SeekBar sb) {}
                public void onStopTrackingTouch(android.widget.SeekBar sb) { save(); }
            });
            layout.addView(scaleBar);

            if ("news".equals(widget.type)) {
                Button toggleBtn = new Button(getContext());
                toggleBtn.setText("Style: " + (widget.layoutStyle == 0 ? "Ticker" : "List"));
                toggleBtn.setOnClickListener(v -> {
                    widget.layoutStyle = widget.layoutStyle == 0 ? 1 : 0;
                    toggleBtn.setText("Style: " + (widget.layoutStyle == 0 ? "Ticker" : "List"));
                    save(); invalidate();
                });
                layout.addView(toggleBtn);
            }

            if ("photo".equals(widget.type)) {
                Button photoBtn = new Button(getContext());
                photoBtn.setText("Change Photo");
                photoBtn.setOnClickListener(v -> MainActivity.this.setupPhoto(widget));
                layout.addView(photoBtn);
            }
            
            if ("timer".equals(widget.type)) {
                Button restartBtn = new Button(getContext());
                restartBtn.setText("Restart Timer");
                restartBtn.setOnClickListener(v -> { widget.startedAt = System.currentTimeMillis(); save(); invalidate(); });
                layout.addView(restartBtn);
            }

            new AlertDialog.Builder(getContext())
                    .setTitle(cap(widget.type) + " Settings")
                    .setView(layout)
                    .setPositiveButton("Close", null)
                    .setNegativeButton("Delete Widget", (dialog, which) -> {
                        getWidgets().remove(widget);
                        active = null; save(); invalidate();
                    }).show();
        }

        private Widget hit(float px, float py) {
            for (int i = getWidgets().size() - 1; i >= 0; i--) {
                Widget widget = getWidgets().get(i);
                if (rect(widget).contains(px, py)) return widget;
            }
            return null;
        }

        private boolean isOnResizeHandle(Widget widget, float px, float py) {
            RectF r = rect(widget);
            return px > r.right - dp(52) && py > r.bottom - dp(52);
        }

        private RectF rect(Widget widget) {
            return new RectF(widget.x * getWidth(), widget.y * getHeight(),
                    (widget.x + widget.w) * getWidth(), (widget.y + widget.h) * getHeight());
        }

        private void addDefaults() {
            getWidgets().add(new Widget("clock", 0.06f, 0.18f, 0.38f, 0.28f));
            getWidgets().add(new Widget("date", 0.52f, 0.18f, 0.34f, 0.22f));
            getWidgets().add(new Widget("battery", 0.52f, 0.48f, 0.30f, 0.22f));
            Widget note = new Widget("note", 0.10f, 0.58f, 0.36f, 0.24f);
            note.label = "Your tablet dashboard";
            getWidgets().add(note);
        }

        private void save() {
            try {
                JSONArray pagesArray = new JSONArray();
                for (List<Widget> page : pages) {
                    JSONArray array = new JSONArray();
                    for (Widget widget : page) {
                        JSONObject item = new JSONObject();
                        item.put("type", widget.type);
                        item.put("x", widget.x);
                        item.put("y", widget.y);
                        item.put("w", widget.w);
                        item.put("h", widget.h);
                        item.put("label", widget.label);
                        item.put("startedAt", widget.startedAt);
                        item.put("photoUri", widget.photoUri == null ? "" : widget.photoUri);
                        item.put("textScale", (double)widget.textScale);
                        item.put("layoutStyle", widget.layoutStyle);
                        array.put(item);
                    }
                    pagesArray.put(array);
                }
                prefs.edit().putString("pages", pagesArray.toString()).putInt("theme", theme).apply();
            } catch (Exception ignored) {
            }
        }

        private void load() {
            theme = prefs.getInt("theme", 0);
            String raw = prefs.getString("pages", null);
            if (raw == null) {
                // Backward compat
                raw = prefs.getString("widgets", null);
                if (raw != null) raw = "[" + raw + "]";
            }
            if (raw == null) {
                addDefaults();
                return;
            }
            try {
                JSONArray pagesArray = new JSONArray(raw);
                for (int p = 0; p < pagesArray.length(); p++) {
                    JSONArray array = pagesArray.getJSONArray(p);
                    List<Widget> pageWidgets = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.getJSONObject(i);
                        Widget widget = new Widget(
                                item.optString("type", "clock"),
                                (float) item.optDouble("x", 0.1),
                                (float) item.optDouble("y", 0.1),
                                (float) item.optDouble("w", 0.3),
                                (float) item.optDouble("h", 0.22)
                        );
                        widget.label = item.optString("label", "");
                        widget.startedAt = item.optLong("startedAt", System.currentTimeMillis());
                        widget.photoUri = item.optString("photoUri", "");
                        widget.textScale = (float)item.optDouble("textScale", 1.0);
                        widget.layoutStyle = item.optInt("layoutStyle", 0);
                        if ("weather".equals(widget.type)) { MainActivity.this.setupWeather(widget); }
                        if ("calendar".equals(widget.type)) { MainActivity.this.setupCalendar(widget); }
                        if ("photo".equals(widget.type)) { loadPhotoBitmap(widget); }
                        pageWidgets.add(widget);
                    }
                    pages.add(pageWidgets);
                }
            } catch (Exception e) {
                pages.clear();
                addDefaults();
            }
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private String cap(String value) {
            if (value == null || value.length() == 0) return "Widget";
            return value.substring(0, 1).toUpperCase(Locale.US) + value.substring(1);
        }
    }

    private static class Widget {
        String type;
        String label = "";
        float x;
        float y;
        float w;
        float h;
        long startedAt = System.currentTimeMillis();
        String temp = "--°";
        String condition = "Unknown";
        String photoUri = "";
        Bitmap bitmap = null;
        float tickerOffset = 0;
        float textScale = 1.0f;
        int layoutStyle = 0;

        Widget(String type, float x, float y, float w, float h) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
