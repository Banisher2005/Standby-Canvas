import os

file_path = "src/MainActivity.java"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Imports
content = content.replace(
    "import android.provider.Settings;\n\nimport org.json.JSONArray;",
    "import android.provider.Settings;\nimport android.media.session.MediaController;\nimport android.media.session.PlaybackState;\nimport android.graphics.Typeface;\n\nimport org.json.JSONArray;\nimport org.json.JSONObject;\nimport org.xmlpull.v1.XmlPullParser;\nimport org.xmlpull.v1.XmlPullParserFactory;\nimport java.io.InputStream;"
)

# 2. Variables in MainActivity
content = content.replace(
    "private Widget calendarWidgetPending;\n\n    @Override",
    "private Widget calendarWidgetPending;\n    private Typeface customFont;\n\n    @Override"
)

# 3. onCreate font init
content = content.replace(
    "getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);\n\n        FrameLayout root",
    "getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);\n\n        try { customFont = Typeface.createFromAsset(getAssets(), \"fonts/Inter-Regular.ttf\"); } catch (Exception e) {}\n\n        FrameLayout root"
)

# 4. showAddDialog News
content = content.replace(
    "final String[] types = {\"Clock\", \"Date\", \"Battery\", \"Note\", \"Timer\", \"Weather\", \"Music\", \"Photo\", \"Calendar\"};",
    "final String[] types = {\"Clock\", \"Date\", \"Battery\", \"Note\", \"Timer\", \"Weather\", \"Music\", \"Photo\", \"Calendar\", \"News\"};"
)

# 5. setupNews
news_setup = """    public void setupNews(Widget widget) {
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
"""
content = content.replace("    @Override\n    protected void onActivityResult", news_setup + "    protected void onActivityResult")

# 6. Pages architecture and Font Init in DashboardView
pages_decl = """        private final List<List<Widget>> pages = new ArrayList<>();
        private int currentPage = 0;
        private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());

        private List<Widget> getWidgets() {
            if (pages.isEmpty()) pages.add(new ArrayList<>());
            return pages.get(currentPage);
        }"""
content = content.replace("        private final List<Widget> widgets = new ArrayList<>();\n        private final SimpleDateFormat clockFormat", pages_decl + "\n        private final SimpleDateFormat clockFormat".replace("        private final SimpleDateFormat clockFormat", ""))

font_init = """            textPaint.setColor(Color.WHITE);
            if (customFont != null) {
                textPaint.setTypeface(customFont);
                paint.setTypeface(customFont);
            } else {
                textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
            }"""
content = content.replace('            textPaint.setColor(Color.WHITE);\n            textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));', font_init)

# 7. widgets usages -> getWidgets()
content = content.replace("widgets.size()", "getWidgets().size()")
content = content.replace("widgets.add(", "getWidgets().add(")
content = content.replace("widgets.clear()", "pages.clear()")
content = content.replace("for (Widget widget : widgets)", "for (Widget widget : getWidgets())")
content = content.replace("for (int i = widgets.size() - 1; i >= 0; i--) {", "for (int i = getWidgets().size() - 1; i >= 0; i--) {")
content = content.replace("Widget widget = widgets.get(i);", "Widget widget = getWidgets().get(i);")
content = content.replace("widgets.remove(widget);", "getWidgets().remove(widget);")
content = content.replace('            if ("music".equals(type)) { MainActivity.this.setupMusic(widget); }', '            if ("music".equals(type)) { MainActivity.this.setupMusic(widget); }\n            if ("news".equals(type)) { widget.w = 0.5f; MainActivity.this.setupNews(widget); }')

# 8. save() and load()
save_old = """                JSONArray array = new JSONArray();
                for (Widget widget : getWidgets()) {
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
                prefs.edit().putString("widgets", array.toString()).putInt("theme", theme).apply();"""

save_new = """                JSONArray pagesArray = new JSONArray();
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
                prefs.edit().putString("pages", pagesArray.toString()).putInt("theme", theme).apply();"""
content = content.replace(save_old, save_new)

load_old = """            String raw = prefs.getString("widgets", null);
            if (raw == null) {
                addDefaults();
                return;
            }
            try {
                JSONArray array = new JSONArray(raw);
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
                    getWidgets().add(widget);
                }
            } catch (Exception e) {
                pages.clear();
                addDefaults();
            }"""

load_new = """            String raw = prefs.getString("pages", null);
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
            }"""
content = content.replace(load_old, load_new)

# 9. Dynamic Material You Background
bg_old = """            paint.setShader(null);
            paint.setColor(p[2] & 0x22FFFFFF);
            canvas.drawCircle(getWidth() * 0.78f, getHeight() * 0.28f, Math.min(getWidth(), getHeight()) * 0.28f, paint);
            canvas.drawCircle(getWidth() * 0.18f, getHeight() * 0.78f, Math.min(getWidth(), getHeight()) * 0.20f, paint);"""
bg_new = bg_old + """
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
            paint.setAlpha(255);"""
content = content.replace(bg_old, bg_new)

dom_col = """        private int getDominantColor(Bitmap bitmap) {
            if (bitmap == null) return 0;
            try {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
                int color = scaled.getPixel(0, 0);
                scaled.recycle();
                return color;
            } catch (Exception e) { return 0; }
        }

        private void drawBackground(Canvas canvas) {"""
content = content.replace("        private void drawBackground(Canvas canvas) {", dom_col)


# 10. Draw Media Controls and RSS
draw_media = """            } else if ("music".equals(widget.type)) {
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
                textPaint.setTextAlign(Paint.Align.LEFT);"""
content = content.replace('            } else if ("music".equals(widget.type)) {\n                primary = MusicListenerService.currentTrack;\n                secondary = MusicListenerService.currentArtist;', draw_media)

draw_news = """            } else if ("photo".equals(widget.type)) {
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
            }"""
content = content.replace('            } else if ("photo".equals(widget.type)) {\n                primary = "Tap to pick photo";\n                secondary = "";\n            }', draw_news)


# 11. Touch Events for swipe and music buttons
touch_logic = """                case MotionEvent.ACTION_UP:
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
        }"""
content = content.replace("""                case MotionEvent.ACTION_UP:
                    if (active != null && Math.abs(x - startX) < dp(4) && Math.abs(y - startY) < dp(4)) {
                        showWidgetOptions(active);
                    }
                    touchMode = 0;
                    return true;
            }
            return true;
        }""", touch_logic)

# 12. Add tickerOffset to Widget
content = content.replace('        Bitmap bitmap = null;\n\n        Widget(String type', '        Bitmap bitmap = null;\n        float tickerOffset = 0;\n\n        Widget(String type')


with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
