import os

file_path = "src/MainActivity.java"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Imports
import_chunk = """import java.util.Locale;"""
new_imports = """import java.util.Locale;
import android.media.MediaPlayer;
import android.view.TextureView;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.widget.ImageView;
import android.content.SharedPreferences;"""
content = content.replace(import_chunk, new_imports)

# 2. MainActivity Fields
fields_chunk = """    private DashboardView dashboard;
    private LinearLayout toolbar;
    private Widget weatherWidgetPending;
    private Widget photoWidgetPending;
    private Widget calendarWidgetPending;
    private Typeface customFont;"""

new_fields = """    private DashboardView dashboard;
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
    private SharedPreferences globalPrefs;"""
content = content.replace(fields_chunk, new_fields)


# 3. onCreate Overhaul
oncreate_old = content.split('    protected void onCreate(Bundle savedInstanceState) {')[1].split('        FrameLayout.LayoutParams barParams =')[0]
oncreate_old = '    protected void onCreate(Bundle savedInstanceState) {' + oncreate_old

oncreate_new = """    protected void onCreate(Bundle savedInstanceState) {
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

"""
content = content.replace(oncreate_old, oncreate_new)

# 4. Background Methods
methods_injection = """    private void showBackgroundSettings() {
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
"""

content = content.replace("    private void confirmReset() {", methods_injection + "\n    private void confirmReset() {")


# 5. Intent result 104
intent_add = """        } else if (requestCode == 104 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            globalPrefs.edit().putString("custom_bg_uri", uri.toString()).apply();
            refreshBackground();
        }"""
content = content.replace('dashboard.loadPhotoBitmap(photoWidgetPending);\n        }', 'dashboard.loadPhotoBitmap(photoWidgetPending);\n        }\n' + intent_add)


# 6. DashboardView Draw Background
bg_old = content.split('        private Bitmap blurredBackground = null;')[1].split('        private void drawWidget(Canvas canvas, Widget widget) {')[0]
bg_old = '        private Bitmap blurredBackground = null;' + bg_old

bg_new = """        private void drawBackground(Canvas canvas) {
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

"""
content = content.replace(bg_old, bg_new)

# 7. DashboardView checkBackground in Ticker
content = content.replace('                    boolean hasFastAnim = false;', '                    MainActivity.this.checkBackground();\n                    boolean hasFastAnim = false;')
content = content.replace('            load();', '            load();\n            MainActivity.this.refreshBackground();')


with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
