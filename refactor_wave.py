import os

file_path = "src/MainActivity.java"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

music_old = content.split('                float btnY = r.bottom - dp(28);')[1].split('            } else {\n                if ("photo".equals(widget.type)) {')[0]
music_old = '                float btnY = r.bottom - dp(28);' + music_old

music_new = """                
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
                paint.setColor(0x44FFFFFF);
                paint.setStrokeWidth(dp(2));
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(currentX, barY, waveEndX, barY, paint);
                
                // Played track wave
                paint.setColor(0xEEFFFFFF);
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
                paint.setColor(0xEEFFFFFF);
                Path next = new Path();
                float nx = r.centerX() + dp(40);
                next.moveTo(nx - dp(6), btnY - dp(6)); next.lineTo(nx + dp(4), btnY); next.lineTo(nx - dp(6), btnY + dp(6)); next.close();
                canvas.drawPath(next, paint);
                canvas.drawRect(nx + dp(4), btnY - dp(5), nx + dp(6), btnY + dp(5), paint);
"""

content = content.replace(music_old, music_new)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
