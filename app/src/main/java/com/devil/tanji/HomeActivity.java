package com.devil.tanji;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.content.ActivityNotFoundException;
import android.net.Uri;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.google.firebase.auth.FirebaseAuth;

import com.devil.tanji.utils.UserManager;
import com.devil.tanji.utils.AdminManager;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class HomeActivity extends AppCompatActivity {
    private TextView coinText, statusText, speedText, streakText, timerText;
    private Button startButton, extendButton;
    private ImageButton closeButton;
    private CountDownTimer countDownTimer;
    private boolean isMining = false;
    private long timeLeftInMillis = 0;
    private long MINING_DURATION = 60 * 60 * 1000;

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
        closeButton = findViewById(R.id.closeButton);

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

        closeButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        });

        updateUI();
        setupBottomNav();
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
            extendButton.setEnabled(true);
            statusText.setText(R.string.active);
            statusText.setTextColor(getResources().getColor(android.R.color.holo_green_light));

            startCountdownTimer(endTime);
            AdminManager.getInstance().init(admin -> {
                if (admin == null) return;
                startMinuteRewardLoop(admin.cpm, endTime);
            });
        });
    }

    private void resumeOrCatchUpCoins(long startTime, long endTime, long lastAwardTime) {
        long now = System.currentTimeMillis();

        AdminManager.getInstance().init(admin -> {
            if (admin == null) return;
            long cpm = admin.cpm;
            long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(now - startTime);
            long creditedMinutes = TimeUnit.MILLISECONDS.toMinutes(lastAwardTime - startTime);
            long missedMinutes = totalMinutes - creditedMinutes;

            if (missedMinutes > 0) {
                long coinsToAdd = missedMinutes * cpm;
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

                userRef.child("coins").get().addOnSuccessListener(snapshot -> {
                    long currentCoins = snapshot.exists() ? snapshot.getValue(Long.class) : 0;
                    userRef.child("coins").setValue(currentCoins + coinsToAdd);
                    userRef.child("last_coin_award_time").setValue(now);
                    startMinuteRewardLoop(cpm, endTime);
                });
            } else {
                startMinuteRewardLoop(cpm, endTime);
            }
        });
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
                FirebaseDatabase.getInstance().getReference("users")
                        .child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                        .child("isMining").setValue(0);
            }
        }.start();
    }

    private void startMinuteRewardLoop(long cpm, long endTime) {
        new CountDownTimer(endTime - System.currentTimeMillis(), 60000) {
            public void onTick(long millisUntilFinished) {
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

                userRef.child("coins").get().addOnSuccessListener(snapshot -> {
                    long currentCoins = snapshot.exists() ? snapshot.getValue(Long.class) : 0;
                    userRef.child("coins").setValue(currentCoins + cpm);
                    userRef.child("last_coin_award_time").setValue(System.currentTimeMillis());
                });
            }
            public void onFinish() {}
        }.start();
    }

    private void extendMining() {
        if (!isMining) return;
        timeLeftInMillis += 10 * 60 * 1000;
        countDownTimer.cancel();
        startCountdownTimer(System.currentTimeMillis() + timeLeftInMillis);
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
        });

        timerText.setText("00:00:00");
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
            if (admin == null || admin.verifyBaseUrl == null || admin.verifyBaseUrl.isEmpty()) {
                Toast.makeText(this, "Failed to load verification URL", Toast.LENGTH_SHORT).show();
                return;
            }
            String finalUrl = admin.verifyBaseUrl + "?token=" + token;
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
}
