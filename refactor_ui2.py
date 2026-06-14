import os

file_path = "src/MainActivity.java"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Update Widget fields
widget_old = """    private static class Widget {
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

        Widget(String type"""

widget_new = """    private static class Widget {
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

        Widget(String type"""
content = content.replace(widget_old, widget_new)

# 2. Update save() and load()
content = content.replace(
    'item.put("photoUri", widget.photoUri == null ? "" : widget.photoUri);',
    'item.put("photoUri", widget.photoUri == null ? "" : widget.photoUri);\\n                        item.put("textScale", (double)widget.textScale);\\n                        item.put("layoutStyle", widget.layoutStyle);'.replace('\\n', '\n')
)

content = content.replace(
    'widget.photoUri = item.optString("photoUri", "");',
    'widget.photoUri = item.optString("photoUri", "");\\n                        widget.textScale = (float)item.optDouble("textScale", 1.0);\\n                        widget.layoutStyle = item.optInt("layoutStyle", 0);'.replace('\\n', '\n')
)

# 3. showWidgetOptions
show_old = content.split('        private void showWidgetOptions(final Widget widget) {')[1].split('        private Widget hit(float px, float py) {')[0]
show_old = '        private void showWidgetOptions(final Widget widget) {' + show_old

show_new = """        private void showWidgetOptions(final Widget widget) {
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

"""
content = content.replace(show_old, show_new)

# 4. Background
bg_old = content.split('        private int getDominantColor(Bitmap bitmap) {')[1].split('        private void drawWidget(Canvas canvas, Widget widget) {')[0]
bg_old = '        private int getDominantColor(Bitmap bitmap) {' + bg_old

bg_new = """        private Bitmap blurredBackground = null;
        private Bitmap lastSource = null;

        private void updateBlurredBackground(Bitmap source) {
            if (source == null) { blurredBackground = null; lastSource = null; return; }
            if (source == lastSource && blurredBackground != null) return;
            try {
                int w = source.getWidth(); int h = source.getHeight();
                int dw = 16; int dh = (int)(16f * h / w);
                Bitmap tiny = Bitmap.createScaledBitmap(source, dw, dh, true);
                blurredBackground = Bitmap.createScaledBitmap(tiny, getWidth(), getHeight(), true);
                tiny.recycle();
                lastSource = source;
            } catch (Exception e) {}
        }

        private void drawBackground(Canvas canvas) {
            Bitmap bgSource = null;
            if (MusicListenerService.albumArt != null) bgSource = MusicListenerService.albumArt;
            else if (!pages.isEmpty()) {
                for (Widget w : pages.get(currentPage)) if ("photo".equals(w.type) && w.bitmap != null) { bgSource = w.bitmap; break; }
            }
            updateBlurredBackground(bgSource);

            if (blurredBackground != null) {
                canvas.drawBitmap(blurredBackground, 0, 0, paint);
                paint.setColor(0xCC000000); // strong dark tint for glassmorphism
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

"""
content = content.replace(bg_old, bg_new)

# 5. DrawWidget
draw_old = content.split('        private void drawWidget(Canvas canvas, Widget widget) {')[1].split('        private void drawBattery(Canvas canvas, RectF r) {')[0]
draw_old = '        private void drawWidget(Canvas canvas, Widget widget) {' + draw_old

draw_new = """        private void drawWidget(Canvas canvas, Widget widget) {
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
                if (secondary != null && secondary.contains("\\n")) {
                    String[] evs = secondary.split("\\n");
                    float yy = r.top + dp(42) * widget.textScale + dp(20);
                    for (String ev : evs) {
                        if (ev.trim().length() > 0) {
                            canvas.drawCircle(r.left + dp(26), yy - dp(6) * widget.textScale, dp(3) * widget.textScale, textPaint);
                            canvas.drawText(ev, r.left + dp(38), yy, textPaint);
                            yy += dp(26) * widget.textScale;
                        }
                    }
                } else {
                    canvas.drawText(secondary, r.left + dp(22), r.top + dp(42) * widget.textScale + dp(20), textPaint);
                }
            } else if ("music".equals(widget.type)) {
                float titleSize = Math.max(dp(20), Math.min(r.height() * 0.35f, r.width() / Math.max(4f, primary.length() * 0.5f))) * widget.textScale;
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
                
                canvas.drawText(primary, textLeft, r.top + dp(38) * widget.textScale, textPaint);
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
                        if (secondary != null && secondary.contains("\\n")) {
                            String[] lines = secondary.split("\\n");
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

"""
content = content.replace(draw_old, draw_new)


with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
