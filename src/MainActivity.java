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

public class MainActivity extends Activity {
    private DashboardView dashboard;
    private LinearLayout toolbar;
    private Widget weatherWidgetPending;
    private Widget photoWidgetPending;
    private Widget calendarWidgetPending;
    private Typeface customFont;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        try { customFont = Typeface.createFromAsset(getAssets(), "fonts/Inter-Regular.ttf"); } catch (Exception e) {}

        FrameLayout root = new FrameLayout(this);
        dashboard = new DashboardView(this);
        root.addView(dashboard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

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
                        if (!text.equals("BBC News - World") && !text.equals("BBC News")) headlines.append(text).append("   •   ");
                    }
                    else if (event == XmlPullParser.END_TAG && "title".equals(name)) isTitle = false;
                    event = myparser.next();
                }
                widget.condition = headlines.toString();
                widget.tickerOffset = 0;
            } catch (Exception e) {
                widget.condition = "Error loading news";
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 102 && resultCode == RESULT_OK && data != null && photoWidgetPending != null) {
            photoWidgetPending.photoUri = data.getData().toString();
            dashboard.save();
            dashboard.loadPhotoBitmap(photoWidgetPending);
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
                    boolean hasNews = false;
                    if (!pages.isEmpty() && currentPage < pages.size()) {
                        for (Widget w : pages.get(currentPage)) {
                            if ("news".equals(w.type)) hasNews = true;
                        }
                    }
                    handler.postDelayed(this, hasNews ? 16 : 1000);
                }
            };
            load();
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

        private int getDominantColor(Bitmap bitmap) {
            if (bitmap == null) return 0;
            try {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
                int color = scaled.getPixel(0, 0);
                scaled.recycle();
                return color;
            } catch (Exception e) { return 0; }
        }

        private void drawBackground(Canvas canvas) {
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
            paint.setColor(p[2] & 0x22FFFFFF);
            canvas.drawCircle(getWidth() * 0.78f, getHeight() * 0.28f, Math.min(getWidth(), getHeight()) * 0.28f, paint);
            canvas.drawCircle(getWidth() * 0.18f, getHeight() * 0.78f, Math.min(getWidth(), getHeight()) * 0.20f, paint);
            // Material You Dynamic Blend
            int targetColor = 0;
            if (MusicListenerService.albumArt != null) targetColor = getDominantColor(MusicListenerService.albumArt);
            else if (!pages.isEmpty()) {
                for (Widget w : pages.get(currentPage)) {
                    if ("photo".equals(w.type) && w.bitmap != null) {
                        targetColor = getDominantColor(w.bitmap); break;
                    }
                }
            }
            if (targetColor != 0) {
                paint.setColor(targetColor);
                paint.setAlpha(60);
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
                paint.setAlpha(255);
            }
            
            // Draw page indicators
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
            paint.setColor(widget == active && editMode ? 0x55FFFFFF : 0x24FFFFFF);
            canvas.drawRoundRect(r, dp(18), dp(18), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(widget == active && editMode ? dp(3) : dp(1));
            paint.setColor(widget == active && editMode ? 0xCCFFFFFF : 0x38FFFFFF);
            canvas.drawRoundRect(r, dp(18), dp(18), paint);
            paint.setStyle(Paint.Style.FILL);

            String primary = "";
            String secondary = "";
            Date now = new Date();
            if ("clock".equals(widget.type)) {
                primary = clockFormat.format(now);
                secondary = "Standby Canvas";
            } else if ("date".equals(widget.type)) {
                primary = dateFormat.format(now);
                secondary = "Today";
            } else if ("battery".equals(widget.type)) {
                primary = battery + "%";
                secondary = charging ? "Charging" : "Battery";
            } else if ("note".equals(widget.type)) {
                primary = widget.label == null || widget.label.length() == 0 ? "Note" : widget.label;
                secondary = "Custom text";
            } else if ("timer".equals(widget.type)) {
                long seconds = Math.max(0, (System.currentTimeMillis() - widget.startedAt) / 1000);
                primary = String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
                secondary = "Focus timer";
            } else if ("weather".equals(widget.type)) {
                primary = widget.temp;
                secondary = widget.condition;
            } else if ("music".equals(widget.type)) {
                primary = MusicListenerService.currentTrack;
                secondary = MusicListenerService.currentArtist;
                
                float btnY = r.bottom - dp(28);
                paint.setColor(0xCCFFFFFF);
                canvas.drawCircle(r.centerX(), btnY, dp(14), paint);
                canvas.drawCircle(r.centerX() - dp(40), btnY, dp(10), paint);
                canvas.drawCircle(r.centerX() + dp(40), btnY, dp(10), paint);
                
                textPaint.setTextSize(dp(10));
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("||", r.centerX(), btnY + dp(3), textPaint);
                canvas.drawText("<", r.centerX() - dp(40), btnY + dp(3), textPaint);
                canvas.drawText(">", r.centerX() + dp(40), btnY + dp(3), textPaint);
                textPaint.setTextAlign(Paint.Align.LEFT);
            } else if ("calendar".equals(widget.type)) {
                primary = "Upcoming";
                secondary = widget.condition; // reused for events
            } else if ("photo".equals(widget.type)) {
                primary = "Tap to pick photo";
                secondary = "";
            } else if ("news".equals(widget.type)) {
                primary = "Headlines";
                secondary = widget.condition;
                widget.tickerOffset -= 1.5f;
                float textWidth = textPaint.measureText(secondary);
                if (widget.tickerOffset < -textWidth) widget.tickerOffset = r.width();
                
                canvas.save();
                canvas.clipRect(r.left + dp(10), r.top + dp(34), r.right - dp(10), r.bottom - dp(10));
                canvas.drawText(secondary, r.left + dp(24) + widget.tickerOffset, r.top + dp(34) + textPaint.getTextSize(), textPaint);
                canvas.restore();
                
                canvas.drawText(primary, r.left + dp(22), r.top + dp(24), textPaint);
                return;
            }

            if ("photo".equals(widget.type) && widget.bitmap != null) {
                canvas.save();
                android.graphics.Path clipPath = new android.graphics.Path();
                clipPath.addRoundRect(r, dp(18), dp(18), android.graphics.Path.Direction.CW);
                canvas.clipPath(clipPath);
                float scale = Math.max(r.width() / widget.bitmap.getWidth(), r.height() / widget.bitmap.getHeight());
                Matrix m = new Matrix();
                m.postScale(scale, scale);
                m.postTranslate(r.left + (r.width() - widget.bitmap.getWidth() * scale) / 2f,
                                r.top + (r.height() - widget.bitmap.getHeight() * scale) / 2f);
                canvas.drawBitmap(widget.bitmap, m, paint);
                canvas.restore();
                
                if (editMode) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(widget == active ? dp(3) : dp(1));
                    paint.setColor(widget == active ? 0xCCFFFFFF : 0x38FFFFFF);
                    canvas.drawRoundRect(r, dp(18), dp(18), paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0xCCFFFFFF);
                    canvas.drawCircle(r.right - dp(18), r.bottom - dp(18), dp(7), paint);
                }
                return;
            }

            float titleSize = Math.max(dp(24), Math.min(r.height() * 0.42f, r.width() / Math.max(3.2f, primary.length() * 0.55f)));
            textPaint.setTextAlign(Paint.Align.LEFT);
            
            if ("calendar".equals(widget.type)) {
                textPaint.setTextSize(dp(16));
                textPaint.setColor(0xAAFFFFFF);
                canvas.drawText(primary, r.left + dp(22), r.top + dp(32), textPaint);
                
                textPaint.setTextSize(dp(18));
                textPaint.setColor(Color.WHITE);
                if (secondary != null && secondary.contains("\n")) {
                    String[] lines = secondary.split("\n");
                    float yy = r.top + dp(62);
                    for (String line : lines) {
                        canvas.drawText(line, r.left + dp(22), yy, textPaint);
                        yy += dp(26);
                    }
                } else {
                    canvas.drawText(secondary, r.left + dp(22), r.top + dp(62), textPaint);
                }
            } else {
                textPaint.setColor(Color.WHITE);
                textPaint.setTextSize(titleSize);
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
                } else {
                    canvas.drawText(secondary, r.left + dp(24), r.top + dp(34), textPaint);
                }
            }

            if ("battery".equals(widget.type)) {
                drawBattery(canvas, r);
            }
            if (editMode) {
                paint.setColor(0xCCFFFFFF);
                canvas.drawCircle(r.right - dp(18), r.bottom - dp(18), dp(7), paint);
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
            if ("photo".equals(widget.type)) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Photo widget")
                        .setItems(new String[]{"Delete widget", "Change photo"}, (dialog, which) -> {
                            if (which == 0) { getWidgets().remove(widget); active = null; save(); invalidate(); }
                            else { MainActivity.this.setupPhoto(widget); }
                        }).show();
                return;
            }
            if ("note".equals(widget.type)) {
                final EditText input = new EditText(MainActivity.this);
                input.setSingleLine(false);
                input.setMinLines(2);
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                input.setText(widget.label);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Configure note")
                        .setView(input)
                        .setNegativeButton("Delete", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                getWidgets().remove(widget);
                                active = null;
                                save();
                                invalidate();
                            }
                        })
                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                widget.label = input.getText().toString();
                                save();
                                invalidate();
                            }
                        })
                        .show();
            } else {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(cap(widget.type) + " widget")
                        .setItems(new String[]{"Delete widget", "Restart timer"}, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    getWidgets().remove(widget);
                                    active = null;
                                } else if ("timer".equals(widget.type)) {
                                    widget.startedAt = System.currentTimeMillis();
                                }
                                save();
                                invalidate();
                            }
                        })
                        .show();
            }
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

        Widget(String type, float x, float y, float w, float h) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
