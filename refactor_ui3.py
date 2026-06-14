import os

file_path = "src/MainActivity.java"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Update toolbar variable to settingsGear
content = content.replace("    private LinearLayout toolbar;", "    private android.widget.TextView settingsGear;")

# 2. Update onCreate UI
oncreate_old = content.split('        toolbar = new LinearLayout(this);')[1].split('        setContentView(root);')[0]
oncreate_old = '        toolbar = new LinearLayout(this);' + oncreate_old

oncreate_new = """        settingsGear = new android.widget.TextView(this);
        settingsGear.setText("⚙️");
        settingsGear.setTextSize(28);
        settingsGear.setPadding(dp(16), dp(16), dp(16), dp(16));
        settingsGear.setOnClickListener(v -> showUnifiedSettingsDialog());
        
        FrameLayout.LayoutParams gearParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        gearParams.topMargin = dp(16);
        gearParams.rightMargin = dp(16);
        root.addView(settingsGear, gearParams);
"""
content = content.replace(oncreate_old, oncreate_new)

# 3. Add Settings Dialog and modify toggleEdit
methods_old = content.split('    private Button makeButton(String label, View.OnClickListener listener) {')[1].split('    private void showAddDialog() {')[0]
methods_old = '    private Button makeButton(String label, View.OnClickListener listener) {' + methods_old

methods_new = """    private void showUnifiedSettingsDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(20), dp(20), dp(20));
        
        layout.addView(makeSettingsButton("Toggle Edit Mode", v -> toggleEdit()));
        layout.addView(makeSettingsButton("Add Widget", v -> showAddDialog()));
        layout.addView(makeSettingsButton("Background Settings", v -> showBackgroundSettings()));
        layout.addView(makeSettingsButton("Cycle Fallback Theme", v -> dashboard.nextTheme()));
        
        String currentGlass = globalPrefs.getString("glass_theme", "dark");
        Button glassBtn = makeSettingsButton("Glass Style: " + (currentGlass.equals("dark") ? "Dark" : "Light"), null);
        glassBtn.setOnClickListener(v -> {
            String newGlass = globalPrefs.getString("glass_theme", "dark").equals("dark") ? "light" : "dark";
            globalPrefs.edit().putString("glass_theme", newGlass).apply();
            glassBtn.setText("Glass Style: " + (newGlass.equals("dark") ? "Dark" : "Light"));
            if (dashboard != null) dashboard.invalidate();
        });
        layout.addView(glassBtn);
        
        layout.addView(makeSettingsButton("Reset Dashboard", v -> confirmReset()));

        new AlertDialog.Builder(this)
                .setTitle("Dashboard Settings")
                .setView(layout)
                .setPositiveButton("Close", null)
                .show();
    }

    private Button makeSettingsButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private void toggleEdit() {
        dashboard.setEditMode(!dashboard.isEditMode());
        settingsGear.setAlpha(dashboard.isEditMode() ? 1f : 0.42f);
        Toast.makeText(this, dashboard.isEditMode() ? "Edit mode: drag, resize, tap widget to configure" : "Locked dashboard", Toast.LENGTH_SHORT).show();
    }
"""
content = content.replace(methods_old, methods_new)

# 4. Overhaul drawWidget
draw_old = content.split('        private void drawWidget(Canvas canvas, Widget widget) {')[1].split('            if ("battery".equals(widget.type)) drawBattery(canvas, r);')[0]
draw_old = '        private void drawWidget(Canvas canvas, Widget widget) {' + draw_old

draw_new = """        private void drawWidget(Canvas canvas, Widget widget) {
            RectF r = rect(widget);
            boolean isDark = "dark".equals(globalPrefs.getString("glass_theme", "dark"));
            
            // Drop Shadow
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isDark ? 0xFF000000 : 0xFF000000);
            paint.setShadowLayer(dp(20), 0, dp(10), 0x77000000);
            canvas.drawRoundRect(r, dp(32), dp(32), paint);
            paint.clearShadowLayer();

            // Glass Fill
            int fillStart = isDark ? 0x66000000 : 0xBBFFFFFF;
            int fillEnd = isDark ? 0x22000000 : 0x77FFFFFF;
            if (widget == active && editMode) {
                fillStart = isDark ? 0x99000000 : 0xFFFFFFFF;
            }
            paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, fillStart, fillEnd, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r, dp(32), dp(32), paint);
            paint.setShader(null);

            // Specular Border
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(widget == active && editMode ? dp(2.5f) : dp(1.5f));
            int strokeStart = isDark ? 0x88FFFFFF : 0xFFFFFFFF;
            int strokeEnd = isDark ? 0x05FFFFFF : 0x44FFFFFF;
            paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, strokeStart, strokeEnd, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r, dp(32), dp(32), paint);
            paint.setShader(null);
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
                clipPath.addRoundRect(r, dp(32), dp(32), Path.Direction.CW);
                canvas.clipPath(clipPath);
                canvas.drawBitmap(Bitmap.createScaledBitmap(widget.bitmap, (int)r.width(), (int)r.height(), true), r.left, r.top, paint);
                canvas.restore();
            }

            int textColor = isDark ? Color.WHITE : 0xFF111111;
            int secTextColor = isDark ? 0xCCFFFFFF : 0xAA111111;
            textPaint.setColor(textColor);
            textPaint.setShadowLayer(dp(4), 0, dp(2), isDark ? 0x88000000 : 0x33FFFFFF);

            if ("news".equals(widget.type)) {
                textPaint.setTextAlign(Paint.Align.LEFT);
                textPaint.setTextSize(dp(22) * widget.textScale);
                canvas.drawText(primary, r.left + dp(24), r.top + dp(38) * widget.textScale, textPaint);
                
                if (widget.layoutStyle == 0) { // Ticker
                    widget.tickerOffset -= 1.5f;
                    float textW = textPaint.measureText(secondary);
                    if (widget.tickerOffset < -textW) widget.tickerOffset = r.width();
                    canvas.save();
                    canvas.clipRect(r.left + dp(10), r.top + dp(34), r.right - dp(10), r.bottom - dp(10));
                    textPaint.setTextSize(dp(18) * widget.textScale);
                    textPaint.setColor(secTextColor);
                    canvas.drawText(secondary, r.left + dp(24) + widget.tickerOffset, r.top + dp(40) + textPaint.getTextSize(), textPaint);
                    canvas.restore();
                } else { // List
                    canvas.save();
                    canvas.clipRect(r.left + dp(10), r.top + dp(45) * widget.textScale, r.right - dp(10), r.bottom - dp(10));
                    textPaint.setTextSize(dp(16) * widget.textScale);
                    textPaint.setColor(secTextColor);
                    String[] evs = secondary.split("   •   ");
                    float yy = r.top + dp(45) * widget.textScale + dp(20);
                    for (String ev : evs) {
                        if (ev.trim().length() > 0) {
                            canvas.drawCircle(r.left + dp(26), yy - dp(6) * widget.textScale, dp(3) * widget.textScale, textPaint);
                            canvas.drawText(ev, r.left + dp(38), yy, textPaint);
                            yy += dp(26) * widget.textScale;
                        }
                    }
                    canvas.restore();
                }
            } else if ("calendar".equals(widget.type)) {
                textPaint.setTextAlign(Paint.Align.LEFT);
                textPaint.setTextSize(dp(16) * widget.textScale);
                textPaint.setColor(secTextColor);
                canvas.drawText(primary, r.left + dp(22), r.top + dp(32) * widget.textScale, textPaint);
                textPaint.setTextSize(dp(18) * widget.textScale);
                textPaint.setColor(textColor);
                if (secondary != null && secondary.contains("\\n")) {
                    String[] evs = secondary.split("\\n");
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
                textPaint.setColor(textColor);
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
                textPaint.setColor(secTextColor);
                canvas.drawText(secondary, textLeft, r.top + dp(38) * widget.textScale + textPaint.getTextSize() * 1.5f, textPaint);
                
                // Playback Position and Waveform Math
                long duration = 1;
                long position = 0;
                boolean isPlaying = false;
                if (MusicListenerService.sessionToken != null) {
                    android.media.session.MediaController mc = new android.media.session.MediaController(getContext(), MusicListenerService.sessionToken);
                    android.media.MediaMetadata meta = mc.getMetadata();
                    if (meta != null && meta.containsKey(android.media.MediaMetadata.METADATA_KEY_DURATION)) {
                        duration = meta.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);
                    }
                    android.media.session.PlaybackState pb = mc.getPlaybackState();
                    if (pb != null) {
                        position = pb.getPosition();
                        if (pb.getState() == android.media.session.PlaybackState.STATE_PLAYING) {
                            isPlaying = true;
                            long timeDelta = android.os.SystemClock.elapsedRealtime() - pb.getLastPositionUpdateTime();
                            position += (long) (timeDelta * pb.getPlaybackSpeed());
                        }
                    }
                }
                if (duration <= 0) duration = 1;
                if (position > duration) position = duration;
                if (position < 0) position = 0;
                
                float progress = (float) ((double) position / duration);
                
                float waveStartX = r.left + dp(24);
                float waveEndX = r.right - dp(24);
                float currentX = waveStartX + (waveEndX - waveStartX) * progress;
                float barY = r.bottom - dp(56);
                
                // Unplayed track line
                paint.setColor(isDark ? 0x44FFFFFF : 0x44000000);
                paint.setStrokeWidth(dp(2));
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(currentX, barY, waveEndX, barY, paint);
                
                // Played track wave
                paint.setColor(textColor);
                if (currentX > waveStartX) {
                    Path wave = new Path();
                    wave.moveTo(waveStartX, barY);
                    
                    if (isPlaying) {
                        float wavePhase = (float) (System.currentTimeMillis() % 2000) / 2000f * (float)Math.PI * 2f;
                        for (float x = waveStartX; x <= currentX; x += dp(2)) {
                            float dx = x - waveStartX;
                            float totalPlay = currentX - waveStartX;
                            float env = (float) Math.sin((dx / totalPlay) * Math.PI);
                            float amp = dp(5) * env;
                            float y = barY + amp * (float) Math.sin((dx / dp(15)) - wavePhase * 4f);
                            wave.lineTo(x, y);
                        }
                        canvas.drawPath(wave, paint);
                    } else {
                        canvas.drawLine(waveStartX, barY, currentX, barY, paint);
                    }
                    
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(currentX, barY, dp(4), paint);
                }

                float btnY = r.bottom - dp(24);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(textColor);
                
                // Prev
                Path prev = new Path();
                float px = r.centerX() - dp(40);
                prev.moveTo(px + dp(6), btnY - dp(6)); prev.lineTo(px - dp(4), btnY); prev.lineTo(px + dp(6), btnY + dp(6)); prev.close();
                canvas.drawPath(prev, paint);
                canvas.drawRect(px - dp(6), btnY - dp(5), px - dp(4), btnY + dp(5), paint);
                
                // Play/Pause
                if (MusicListenerService.sessionToken != null) {
                    canvas.drawCircle(r.centerX(), btnY, dp(16), paint);
                    paint.setColor(isDark ? 0xFF121212 : 0xFFE0E0E0);
                    if (isPlaying) {
                        canvas.drawRect(r.centerX() - dp(4), btnY - dp(5), r.centerX() - dp(1), btnY + dp(5), paint);
                        canvas.drawRect(r.centerX() + dp(1), btnY - dp(5), r.centerX() + dp(4), btnY + dp(5), paint);
                    } else {
                        Path play = new Path();
                        play.moveTo(r.centerX() - dp(3), btnY - dp(6));
                        play.lineTo(r.centerX() + dp(5), btnY);
                        play.lineTo(r.centerX() - dp(3), btnY + dp(6));
                        play.close();
                        canvas.drawPath(play, paint);
                    }
                }
                
                // Next
                paint.setColor(textColor);
                Path next = new Path();
                float nx = r.centerX() + dp(40);
                next.moveTo(nx - dp(6), btnY - dp(6)); next.lineTo(nx + dp(4), btnY); next.lineTo(nx - dp(6), btnY + dp(6)); next.close();
                canvas.drawPath(next, paint);
                canvas.drawRect(nx + dp(4), btnY - dp(5), nx + dp(6), btnY + dp(5), paint);
            } else {
                if ("photo".equals(widget.type)) {
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    textPaint.setTextSize(dp(18) * widget.textScale);
                    textPaint.setColor(textColor);
                    canvas.drawText(primary, r.centerX(), r.centerY(), textPaint);
                } else {
                    float titleSize = Math.max(dp(24), Math.min(r.height() * 0.42f, r.width() / Math.max(3.2f, primary.length() * 0.55f))) * widget.textScale;
                    textPaint.setTextAlign(Paint.Align.LEFT);
                    textPaint.setColor(textColor);
                    textPaint.setTextSize(titleSize);
                    
                    if ("clock".equals(widget.type)) {
                        textPaint.setTextAlign(Paint.Align.CENTER);
                        textPaint.setTextSize(titleSize * 1.3f);
                        canvas.drawText(primary, r.centerX(), r.centerY() + titleSize * 0.2f, textPaint);
                    } else if ("note".equals(widget.type)) {
                        textPaint.setTextAlign(Paint.Align.CENTER);
                        textPaint.setTextSize(titleSize);
                        canvas.drawText(primary, r.centerX(), r.centerY() + titleSize * 0.3f, textPaint);
                    } else {
                        canvas.drawText(primary, r.left + dp(22), r.centerY() + titleSize * 0.20f, textPaint);
                        textPaint.setTextSize(Math.max(dp(13), titleSize * 0.22f));
                        textPaint.setColor(secTextColor);
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
            textPaint.clearShadowLayer();
"""
content = content.replace(draw_old, draw_new)


# 5. Battery and Settings background color for Dark/Light
content = content.replace('paint.setColor(0xDDFFFFFF);', 'paint.setColor(isDark ? 0xDDFFFFFF : 0xAA111111);')

# We need to change drawBattery to accept boolean isDark
content = content.replace('if ("battery".equals(widget.type)) drawBattery(canvas, r);', 'if ("battery".equals(widget.type)) drawBattery(canvas, r, isDark);')
content = content.replace('private void drawBattery(Canvas canvas, RectF r) {', 'private void drawBattery(Canvas canvas, RectF r, boolean isDark) {')
content = content.replace('paint.setColor(0xDDFFFFFF);', 'paint.setColor(isDark ? 0xDDFFFFFF : 0x44111111);')


with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
