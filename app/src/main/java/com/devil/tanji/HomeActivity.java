package com.devil.tanji;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.content.ActivityNotFoundException;
import android.net.Uri;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.google.firebase.auth.FirebaseAuth;

import com.devil.tanji.utils.UserManager;
import com.devil.tanji.utils.AdminManager;

import android.app.AlarmManager;
import android.app.PendingIntent;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.Locale;
import java.util.Random;

public class HomeActivity extends AppCompatActivity {
    private TextView coinText, statusText, speedText, streakText, timerText, hyperlinkText, rewardClaimedText;
    private Button startButton, extendButton;
    private ImageButton telegramButton;
    private CountDownTimer countDownTimer;
    private CountDownTimer rewardTimer;
    private Handler minuteHandler = new Handler();
    private boolean isMining = false;
    private long timeLeftInMillis = 0;
    private long MINING_DURATION = 60 * 60 * 1000;
    private long EXTEND_DURATION = 60 * 60 * 1000;

    private final Handler handler = new Handler();
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        coinText = findViewById(R.id.coinText);
        statusText = findViewById(R.id.statusText);
        speedText = findViewById(R.id.speedText);
        streakText = findViewById(R.id.streakText);
        timerText = findViewById(R.id.timerText);
        startButton = findViewById(R.id.startButton);
        extendButton = findViewById(R.id.extendButton);
        hyperlinkText = findViewById(R.id.hyperlinkText);
        rewardClaimedText = findViewById(R.id.rewardClaimedText);
        telegramButton = findViewById(R.id.telegramButton);

        if (rewardClaimedText == null) {
            Log.e("RewardDebug", "rewardClaimedText is NULL!");
        } else {
            Log.d("RewardDebug", "rewardClaimedText initialized successfully");
        }

        startRewardClaimedTextUpdater();

        hyperlinkText.setText(R.string.hyper_text);
        hyperlinkText.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
        hyperlinkText.setPaintFlags(hyperlinkText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        AdminManager.getInstance().init(admin -> {
            if (admin == null) return;

            hyperlinkText.setOnClickListener(v -> {
                String url = admin.hyperLink;
                if (url != null && !url.isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Link is not available", Toast.LENGTH_SHORT).show();
                }
            });

            telegramButton.setOnClickListener(v -> {
                String url = admin.tgUrl;
                if (url != null && !url.isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Telegram link not available", Toast.LENGTH_SHORT).show();
                }
            });
        });


        startButton.setOnClickListener(v -> UserManager.getInstance().init(user -> {
            if (user == null) {
                Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show();
                return;
            }
            if (user.verified == 1) {
                startMining();
            } else {
                createVerificationAndRedirect("start", FirebaseAuth.getInstance().getCurrentUser());
            }
        }));

        extendButton.setOnClickListener(v -> UserManager.getInstance().init(user -> {
            if (user == null) {
                Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show();
                return;
            }
            if (user.extendVerify == 1) {
                extendMining();
            } else {
                createVerificationAndRedirect("extend", FirebaseAuth.getInstance().getCurrentUser());
            }
        }));

        updateUI();
        listenForCoinChanges();
        setupBottomNav();
    }

    private void startRewardClaimedTextUpdater() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String randomEmail = generateRandomEmail();
                int amount = 400 + random.nextInt(1601); // 400 to 2000
                String displayText = randomEmail + " claimed ₹" + amount + "!";
                rewardClaimedText.setText(displayText);

                // Fade-in animation
                rewardClaimedText.setAlpha(0f);
                rewardClaimedText.setVisibility(View.VISIBLE);
                rewardClaimedText.animate()
                        .alpha(1f)
                        .setDuration(500)
                        .setListener(null);

                // Repeat after 2 seconds
                handler.postDelayed(this, 2000);
            }
        }, 0);
    }

    private String generateRandomEmail() {
        String characters = "abcdefghijklmnopqrstuvwxyz0123456789";
        int nameLength = 5 + random.nextInt(5); // 5 to 9 chars
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nameLength; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        String domains[] = {"gmail.com", "mail.com", "outlook.com", "hotmail.com"};
        String domain = domains[random.nextInt(domains.length)];
        return sb.toString() + "@" + domain;
    }

    private void startMining() {
        if (isMining) return;

        long startTime = System.currentTimeMillis();
        long endTime = startTime + MINING_DURATION;
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        String uid = firebaseUser.getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

        Map<String, Object> updates = new HashMap<>();
        updates.put("isMining", 1);
        updates.put("mining_start_time", startTime);
        updates.put("mining_end_time", endTime);
        updates.put("last_coin_award_time", startTime);

        userRef.updateChildren(updates).addOnSuccessListener(aVoid -> {
            isMining = true;
            startButton.setEnabled(false);
            startButton.setText(R.string.mining);
            extendButton.setEnabled(true);
            statusText.setText(R.string.active);
            statusText.setTextColor(getResources().getColor(android.R.color.holo_green_light));

            startCountdownTimer(endTime);
            AdminManager.getInstance().init(admin -> {
                if (admin == null) return;
                startMinuteRewardLoop(admin.cpm, endTime);
            });
        });

        scheduleMiningAlarms(uid, endTime);
    }

    private void resumeOrCatchUpCoins(long startTime, long endTime, long lastAwardTime) {
        long now = System.currentTimeMillis();

        AdminManager.getInstance().init(admin -> {
            if (admin == null) return;
            long cpm = admin.cpm;
            // Calculate how many full minutes have passed since last award
            long timeSinceLastAward = now - lastAwardTime;
            long fullMinutesPassed = timeSinceLastAward / 60000; // Only full minutes

            if (fullMinutesPassed > 0) {
                long coinsToAdd = fullMinutesPassed * cpm;
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null) return;

                String uid = user.getUid();
                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

                userRef.child("coins").get().addOnSuccessListener(snapshot -> {
                    long currentCoins = snapshot.exists() ? snapshot.getValue(Long.class) : 0;
                    long newCoins = currentCoins + coinsToAdd;
                    userRef.child("coins").setValue(newCoins);
                    userRef.child("last_coin_award_time").setValue(now);
                    // Update UI immediately
                    runOnUiThread(() -> coinText.setText(String.valueOf(newCoins)));
                    startMinuteRewardLoop(cpm, endTime);
                });
            } else {
                startMinuteRewardLoop(cpm, endTime);
            }
        });
    }

    private void scheduleMiningAlarms(String uid, long miningEndTime) {
        // Get 1hr before end time
        long beforeExpiryTime = miningEndTime - (60 * 60 * 1000);
        scheduleAlarm(uid, "beforeExpiry", beforeExpiryTime, 101);
        scheduleAlarm(uid, "atExpiry", miningEndTime, 102);
    }

    private void scheduleAlarm(String uid, String type, long triggerAtMillis, int requestCode) {
        Intent intent = new Intent(this, com.devil.tanji.receiver.MiningAlarmReceiver.class);
        intent.putExtra("uid", uid);
        intent.putExtra("type", type);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        }
    }

    private void cancelMiningAlarms() {
        cancelAlarm(101);
        cancelAlarm(102);
    }

    private void cancelAlarm(int requestCode) {
        Intent intent = new Intent(this, com.devil.tanji.receiver.MiningAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
            }
        }
    }

    private void startCountdownTimer(long endTime) {
        long timeLeft = endTime - System.currentTimeMillis();
        countDownTimer = new CountDownTimer(timeLeft, 1000) {
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateTimerText();
            }
            public void onFinish() {
                isMining = false;
                statusText.setText(R.string.inactive);
                statusText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                startButton.setEnabled(true);
                extendButton.setEnabled(false);
                timerText.setText("00:00:00");
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    FirebaseDatabase.getInstance().getReference("users")
                        .child(user.getUid())
                        .child("isMining").setValue(0);
                }
            }
        }.start();
    }

    private void startMinuteRewardLoop(long cpm, long endTime) {
        if (rewardTimer != null) rewardTimer.cancel();
        minuteHandler.removeCallbacksAndMessages(null);

        long now = System.currentTimeMillis();
        long msToNextMinute = 60000 - (now % 60000);
        long timeRemaining = endTime - now;

        // Wait until the next full minute
        minuteHandler.postDelayed(() -> {
            long actualRemaining = endTime - System.currentTimeMillis();
            long fullMinutesRemaining = actualRemaining / 60000;

            rewardTimer = new CountDownTimer(fullMinutesRemaining * 60000, 60000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user == null) return;
                    String uid = user.getUid();
                    DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

                    userRef.child("coins").runTransaction(new Transaction.Handler() {
                        @NonNull
                        @Override
                        public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            Long currentCoins = currentData.getValue(Long.class);
                            if (currentCoins == null) currentCoins = 0L;
                            currentData.setValue(currentCoins + cpm);
                            return Transaction.success(currentData);
                        }

                        @Override
                        public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                            if (committed) {
                                long newCoins = 0;
                                if (currentData != null && currentData.getValue() != null) {
                                    try {
                                        newCoins = Long.parseLong(currentData.getValue().toString());
                                    } catch (Exception ignored) {}
                                }
                                userRef.child("last_coin_award_time").setValue(System.currentTimeMillis());
                                long finalNewCoins = newCoins;
                                runOnUiThread(() -> coinText.setText(String.valueOf(finalNewCoins)));
                            }
                        }
                    });
                }

                @Override
                public void onFinish() {
                    rewardTimer = null;
                }
            };
            rewardTimer.start();

        }, msToNextMinute);
    }

    private void listenForCoinChanges() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();
        DatabaseReference coinRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("coins");

        coinRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long coins = snapshot.getValue(Long.class);
                if (coins != null) {
                    coinText.setText(String.valueOf(coins));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Log or handle error
            }
        });
    }


    private void extendMining() {
        if (!isMining) return;

        long newEndTime = System.currentTimeMillis() + EXTEND_DURATION;
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        String uid = firebaseUser.getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

        Map<String, Object> updates = new HashMap<>();
        updates.put("mining_end_time", newEndTime);
        updates.put("last_coin_award_time", System.currentTimeMillis());

        userRef.updateChildren(updates).addOnSuccessListener(aVoid -> {
            countDownTimer.cancel();
            startCountdownTimer(newEndTime);

            AdminManager.getInstance().init(admin -> {
                if (admin == null) return;
                startMinuteRewardLoop(admin.cpm, newEndTime);
            });

            Toast.makeText(this, "Mining extended!", Toast.LENGTH_SHORT).show();
            cancelMiningAlarms();
            scheduleMiningAlarms(uid, newEndTime);
        });
    }


    private void updateTimerText() {
        int hours = (int) (timeLeftInMillis / (1000 * 60 * 60));
        int minutes = (int) ((timeLeftInMillis / (1000 * 60)) % 60);
        int seconds = (int) ((timeLeftInMillis / 1000) % 60);
        timerText.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
    }

    private void updateUI() {
        UserManager.getInstance().init(user -> {
            if (user == null) {
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                return;
            }
            coinText.setText(String.valueOf(user.coins));
            streakText.setText(String.valueOf(user.streak_count));
            statusText.setText(getString(user.isMining == 1 ? R.string.status_active : R.string.status_inactive));
            statusText.setTextColor(ContextCompat.getColor(this, user.isMining == 1 ? R.color.green : R.color.gray));

            if (user.isMining == 1 && user.mining_end_time > System.currentTimeMillis()) {
                isMining = true;
                startCountdownTimer(user.mining_end_time);
                resumeOrCatchUpCoins(user.mining_start_time, user.mining_end_time, user.last_coin_award_time);
                startButton.setEnabled(false);
                startButton.setText(R.string.mining);
                extendButton.setEnabled(true);
            }
        });

        AdminManager.getInstance().init(admin -> {
            if (admin == null) {
                Toast.makeText(this, "Failed to load config", Toast.LENGTH_SHORT).show();
                return;
            }
            speedText.setText(admin.cpm + " C/min");
            if (admin.mining_duration > 0) {
                MINING_DURATION = admin.mining_duration * 60 * 60 * 1000L;
            }
            if (admin.extend_duration > 0) {
                EXTEND_DURATION = admin.extend_duration * 60 * 60 * 1000L;
            }
        });

        timerText.setText("00:00:00");
        startButton.setText("Start");
        startButton.setEnabled(true);
        extendButton.setEnabled(false);
    }

    private void createVerificationAndRedirect(String buttonType, FirebaseUser firebaseUser) {
        if (firebaseUser == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = firebaseUser.getUid();
        String token = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        long expireAt = createdAt + TimeUnit.HOURS.toMillis(24);

        Map<String, Object> verification = new HashMap<>();
        verification.put("token", token);
        verification.put("uid", uid);
        verification.put("button", buttonType);
        verification.put("createdAt", createdAt);
        verification.put("expireAt", expireAt);

        AdminManager.getInstance().init(admin -> {
            if (admin == null || admin.baseApiUrl == null || admin.baseApiUrl.isEmpty()) {
                Toast.makeText(this, "Failed to load verification URL", Toast.LENGTH_SHORT).show();
                return;
            }
            String finalUrl = admin.baseApiUrl + "/verify?action=verify&token=" + token + "&type=" + buttonType;
            FirebaseDatabase.getInstance().getReference("verifications").child(token)
                    .setValue(verification).addOnSuccessListener(aVoid -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)));
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(this, "No browser found to open URL", Toast.LENGTH_SHORT).show();
                        }
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to create verification", Toast.LENGTH_SHORT).show();
                    });
        });
    }

    private void setupBottomNav() {
        int active = getResources().getColor(R.color.nav_active);
        int inactive = getResources().getColor(R.color.nav_inactive);

        ((TextView)findViewById(R.id.iconHome)).setTextColor(active);
        ((TextView)findViewById(R.id.textHome)).setTextColor(active);
        ((TextView)findViewById(R.id.iconStreak)).setTextColor(inactive);
        ((TextView)findViewById(R.id.textStreak)).setTextColor(inactive);
        ((TextView)findViewById(R.id.iconWithdraw)).setTextColor(inactive);
        ((TextView)findViewById(R.id.textWithdraw)).setTextColor(inactive);
        ((TextView)findViewById(R.id.iconRefer)).setTextColor(inactive);
        ((TextView)findViewById(R.id.textRefer)).setTextColor(inactive);
        ((TextView)findViewById(R.id.iconProfile)).setTextColor(inactive);
        ((TextView)findViewById(R.id.textProfile)).setTextColor(inactive);

        findViewById(R.id.navStreak).setOnClickListener(v -> startActivity(new Intent(this, StreakRewardActivity.class)));
        findViewById(R.id.navWithdraw).setOnClickListener(v -> startActivity(new Intent(this, WithdrawalActivity.class)));
        findViewById(R.id.navRefer).setOnClickListener(v -> startActivity(new Intent(this, ReferEarnActivity.class)));
        findViewById(R.id.navProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

}
