package crypto.mining.elite;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.content.ActivityNotFoundException;
import android.net.Uri;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.google.firebase.auth.FirebaseAuth;

import crypto.mining.elite.utils.AdminManager;
import crypto.mining.elite.utils.SessionManager;

import android.app.AlarmManager;
import android.app.PendingIntent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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

    private ValueEventListener userListener;
    private ValueEventListener adminListener;
    private DatabaseReference userRef;
    private DatabaseReference adminRef;
    private boolean userLoaded = false;
    private boolean adminLoaded = false;
    private long cachedMiningEndTime = 0;
    private long cachedMiningStartTime = 0;
    private long cachedLastCoinAwardTime = 0;
    private long cachedCpm = 0;
    private long cachedCoins = 0;
    private int cachedIsMining = 0;
    private int cachedVerified = 0;
    private int cachedExtendVerify = 0;
    private int cachedStreakCount = 0;
    private int cachedIsExtended = 0;
    private String cachedBaseApiUrl = null;
    private long cachedMiningDuration = 60 * 60 * 1000;
    private long cachedExtendDuration = 60 * 60 * 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        SessionManager.startSessionMonitor(this);

        // Initialize UI elements
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

        // Disable buttons until data loads
        startButton.setEnabled(false);
        extendButton.setEnabled(false);

        if (rewardClaimedText == null) {
            Log.e("RewardDebug", "rewardClaimedText is NULL!");
        } else {
            Log.d("RewardDebug", "rewardClaimedText initialized successfully");
        }

        startRewardClaimedTextUpdater();

        hyperlinkText.setText(R.string.hyper_text);
        hyperlinkText.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
        hyperlinkText.setPaintFlags(hyperlinkText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        // Initialize Firebase listeners
        setupFirebaseListeners();

        // Setup button click listeners
        startButton.setOnClickListener(v -> {
            if (!userLoaded || !adminLoaded) return;
            if (cachedVerified == 1) {
                startMiningRealtime();
            } else {
                createVerificationAndRedirect("start", FirebaseAuth.getInstance().getCurrentUser());
            }
        });

        extendButton.setOnClickListener(v -> {
            if (!userLoaded || !adminLoaded) return;
            if (cachedExtendVerify == 1) {
                extendMiningRealtime();
            } else {
                createVerificationAndRedirect("extend", FirebaseAuth.getInstance().getCurrentUser());
            }
        });

        setupBottomNav();
        timerText.setText("00:00:00");
        startButton.setText("Start");
        startButton.setEnabled(false);
        extendButton.setEnabled(false);
    }

    private void setupFirebaseListeners() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            String uid = firebaseUser.getUid();
            userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

            // Check if user is banned first
            userRef.child("isBanned").get().addOnCompleteListener(banCheck -> {
                if (banCheck.isSuccessful()) {
                    Long isBanned = banCheck.getResult().getValue(Long.class);
                    if (isBanned != null && isBanned == 1) {
                        Toast.makeText(this, "You are banned from using this app", Toast.LENGTH_LONG).show();
                        FirebaseAuth.getInstance().signOut();
                        return;
                    }

                    // Not banned, setup user listener
                    userListener = new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (!snapshot.exists()) return;

                            Long coins = snapshot.child("coins").getValue(Long.class);
                            Integer isMining = snapshot.child("isMining").getValue(Integer.class);
                            Integer verified = snapshot.child("verified").getValue(Integer.class);
                            Integer extendVerify = snapshot.child("extendVerify").getValue(Integer.class);
                            Integer streakCount = snapshot.child("streak_count").getValue(Integer.class);
                            Long miningEndTime = snapshot.child("mining_end_time").getValue(Long.class);
                            Long miningStartTime = snapshot.child("mining_start_time").getValue(Long.class);
                            Long lastCoinAwardTime = snapshot.child("last_coin_award_time").getValue(Long.class);
                            Integer isExtended = snapshot.child("isExtended").getValue(Integer.class);

                            // Check if data actually changed to prevent unnecessary updates
                            boolean dataChanged = false;
                            if (cachedCoins != (coins != null ? coins : 0) ||
                                    cachedIsMining != (isMining != null ? isMining : 0) ||
                                    cachedVerified != (verified != null ? verified : 0) ||
                                    cachedExtendVerify != (extendVerify != null ? extendVerify : 0) ||
                                    cachedStreakCount != (streakCount != null ? streakCount : 0) ||
                                    cachedMiningEndTime != (miningEndTime != null ? miningEndTime : 0) ||
                                    cachedMiningStartTime != (miningStartTime != null ? miningStartTime : 0) ||
                                    cachedLastCoinAwardTime != (lastCoinAwardTime != null ? lastCoinAwardTime : 0) ||
                                    cachedIsExtended != (isExtended != null ? isExtended : 0)) {
                                dataChanged = true;
                            }

                            cachedCoins = coins != null ? coins : 0;
                            cachedIsMining = isMining != null ? isMining : 0;
                            cachedVerified = verified != null ? verified : 0;
                            cachedExtendVerify = extendVerify != null ? extendVerify : 0;
                            cachedStreakCount = streakCount != null ? streakCount : 0;
                            cachedMiningEndTime = miningEndTime != null ? miningEndTime : 0;
                            cachedMiningStartTime = miningStartTime != null ? miningStartTime : 0;
                            cachedLastCoinAwardTime = lastCoinAwardTime != null ? lastCoinAwardTime : 0;
                            cachedIsExtended = isExtended != null ? isExtended : 0;

                            if (dataChanged) {
                                updateUserUI();
                            }
                            userLoaded = true;
                            checkEnableButtons();
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(HomeActivity.this, "Failed to load user data", Toast.LENGTH_SHORT).show();
                        }
                    };

                    userRef.addValueEventListener(userListener);
                } else {
                    Toast.makeText(this, "Failed to verify ban status", Toast.LENGTH_SHORT).show();
                    FirebaseAuth.getInstance().signOut();
                }
            });
        }

        // Setup admin listener
        adminRef = FirebaseDatabase.getInstance().getReference("admin");
        adminListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                Long cpm = snapshot.child("cpm").getValue(Long.class);
                Long miningDuration = snapshot.child("mining_duration").getValue(Long.class);
                Long extendDuration = snapshot.child("extend_duration").getValue(Long.class);
                String baseApiUrl = snapshot.child("baseApiUrl").getValue(String.class);
                String visit = snapshot.child("visit").getValue(String.class);
                String tgUrl = snapshot.child("tgUrl").getValue(String.class);

                cachedCpm = cpm != null ? cpm : 0;
                cachedMiningDuration = miningDuration != null ? miningDuration * 60 * 60 * 1000L : 60 * 60 * 1000;
                // Convert hours to milliseconds for extend duration
                cachedExtendDuration = extendDuration != null ? extendDuration * 60 * 60 * 1000L : 60 * 60 * 1000;
                cachedBaseApiUrl = baseApiUrl;

                speedText.setText(cachedCpm + " C/min");
                MINING_DURATION = cachedMiningDuration;
                EXTEND_DURATION = cachedExtendDuration;

                if (visit != null) {
                    hyperlinkText.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(visit));
                        startActivity(intent);
                    });
                }
                if (tgUrl != null) {
                    telegramButton.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(tgUrl));
                        startActivity(intent);
                    });
                }
                adminLoaded = true;
                checkEnableButtons();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        adminRef.addValueEventListener(adminListener);
    }

    private void checkEnableButtons() {
        if (userLoaded && adminLoaded) {
            startButton.setEnabled(true);
            extendButton.setEnabled(cachedIsMining == 1 && cachedIsExtended == 0);
        }
    }

    private void updateUserUI() {
        coinText.setText(String.valueOf(cachedCoins));
        streakText.setText(String.valueOf(cachedStreakCount));

        // Fix string resource references
        if (cachedIsMining == 1) {
            statusText.setText("Active");
            statusText.setTextColor(ContextCompat.getColor(this, R.color.green));
        } else {
            statusText.setText("Inactive");
            statusText.setTextColor(ContextCompat.getColor(this, R.color.gray));
        }

        if (cachedIsMining == 1 && cachedMiningEndTime > System.currentTimeMillis()) {
            isMining = true;
            // Only start countdown if not already running or if end time changed
            if (countDownTimer == null || timeLeftInMillis != (cachedMiningEndTime - System.currentTimeMillis())) {
                startCountdownTimer(cachedMiningEndTime);
            }
            resumeOrCatchUpCoins(cachedMiningStartTime, cachedMiningEndTime, cachedLastCoinAwardTime);
            startButton.setEnabled(false);
            startButton.setText("Mining...");
            extendButton.setEnabled(cachedIsExtended == 0);
        } else {
            isMining = false;
            startButton.setEnabled(true);
            startButton.setText("Start");
            extendButton.setEnabled(false);
        }
    }

    private void startMiningRealtime() {
        if (isMining) return;

        // Cancel any existing timers
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (rewardTimer != null) {
            rewardTimer.cancel();
        }

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
        updates.put("isExtended", 0);

        userRef.updateChildren(updates).addOnSuccessListener(aVoid -> {
            isMining = true;
            startButton.setEnabled(false);
            startButton.setText("Mining...");
            extendButton.setEnabled(true);
            statusText.setText("Active");
            statusText.setTextColor(ContextCompat.getColor(this, R.color.green));
            startCountdownTimer(endTime);
            startMinuteRewardLoop(cachedCpm, endTime);
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to start mining", Toast.LENGTH_SHORT).show();
        });

        scheduleMiningAlarms(uid, endTime);
    }

    private void extendMiningRealtime() {
        if (!isMining || cachedIsExtended == 1) return;

        // Calculate new end time: current time left + extend duration
        long currentTimeLeft = cachedMiningEndTime - System.currentTimeMillis();
        if (currentTimeLeft <= 0) {
            Toast.makeText(this, "Mining has already ended", Toast.LENGTH_SHORT).show();
            return;
        }

        long newEndTime = System.currentTimeMillis() + currentTimeLeft + cachedExtendDuration;

        // Update cached values immediately to prevent flickering
        cachedMiningEndTime = newEndTime;
        cachedIsExtended = 1;

        // Cancel existing timers
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        if (rewardTimer != null) {
            rewardTimer.cancel();
            rewardTimer = null;
        }

        // Start new timer immediately
        startCountdownTimer(newEndTime);
        startMinuteRewardLoop(cachedCpm, newEndTime);

        // Update UI immediately
        extendButton.setEnabled(false);

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;
        String uid = firebaseUser.getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
        Map<String, Object> updates = new HashMap<>();
        updates.put("mining_end_time", newEndTime);
        updates.put("last_coin_award_time", System.currentTimeMillis());
        updates.put("isExtended", 1);

        userRef.updateChildren(updates).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Mining extended by " + (cachedExtendDuration / (60 * 60 * 1000)) + " hours!", Toast.LENGTH_SHORT).show();
            cancelMiningAlarms();
            scheduleMiningAlarms(uid, newEndTime);
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to extend mining", Toast.LENGTH_SHORT).show();
            // Revert cached values on failure
            cachedIsExtended = 0;
            extendButton.setEnabled(true);
        });
    }

    private void startRewardClaimedTextUpdater() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String randomEmail = generateMaskedEmail();
                int amount = 400 + random.nextInt(1601); // 400 to 2000
                String displayText = "🎉 " + randomEmail + " earned $" + amount;
                rewardClaimedText.setText(displayText);
                rewardClaimedText.setAlpha(0f);
                rewardClaimedText.setVisibility(View.VISIBLE);
                rewardClaimedText.animate()
                        .alpha(1f)
                        .setDuration(500)
                        .setListener(null);
                handler.postDelayed(this, 4000); // 4 seconds
            }
        }, 0);
    }

    private void resumeOrCatchUpCoins(long startTime, long endTime, long lastAwardTime) {
        long now = System.currentTimeMillis();
        long cpm = cachedCpm;
        if (cpm <= 0) {
            AdminManager.getInstance().init(admin -> {
                if (admin == null) return;
                startMinuteRewardLoop(admin.cpm, endTime);
            });
            return;
        }

        long timeSinceLastAward = now - lastAwardTime;
        long fullMinutesPassed = timeSinceLastAward / 60000;

        long timeElapsedSinceStart = endTime - startTime;
        if (timeElapsedSinceStart > 0) {
            long maxMinutes = timeElapsedSinceStart / 60000;
            long minutesToAward = Math.min(fullMinutesPassed, maxMinutes);

            if (minutesToAward > 0) {
                long coinsToAdd = minutesToAward * cpm;
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
                        currentData.setValue(currentCoins + coinsToAdd);
                        return Transaction.success(currentData);
                    }

                    @Override
                    public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                        if (committed) {
                            userRef.child("last_coin_award_time").setValue(now);
                            startMinuteRewardLoop(cpm, endTime);
                        }
                    }
                });
            } else {
                startMinuteRewardLoop(cpm, endTime);
            }
        }
    }

    private void startMinuteRewardLoop(long cpm, long endTime) {
        if (rewardTimer != null) {
            rewardTimer.cancel();
            rewardTimer = null;
        }
        minuteHandler.removeCallbacksAndMessages(null);

        long now = System.currentTimeMillis();
        long timeRemaining = endTime - now;

        if (timeRemaining <= 0) {
            return;
        }

        long msToNextMinute = 60000 - (now % 60000);

        if (msToNextMinute < 1000) {
            msToNextMinute = 0;
        }

        minuteHandler.postDelayed(() -> {
            if (!isMining || System.currentTimeMillis() >= endTime) {
                return;
            }

            long actualRemaining = endTime - System.currentTimeMillis();
            long fullMinutesRemaining = actualRemaining / 60000;

            if (fullMinutesRemaining <= 0) {
                return;
            }

            rewardTimer = new CountDownTimer(fullMinutesRemaining * 60000, 60000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    if (!isMining || System.currentTimeMillis() >= endTime) {
                        cancel();
                        return;
                    }

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
                                userRef.child("last_coin_award_time").setValue(System.currentTimeMillis());
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

    private void startCountdownTimer(long endTime) {
        long timeLeft = endTime - System.currentTimeMillis();
        if (timeLeft <= 0) {
            // Mining has already ended
            isMining = false;
            statusText.setText("Inactive");
            statusText.setTextColor(ContextCompat.getColor(this, R.color.gray));
            startButton.setEnabled(true);
            extendButton.setEnabled(false);
            timerText.setText("00:00:00");
            return;
        }

        // Cancel existing timer if running
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(timeLeft, 1000) {
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateTimerText();
            }
            public void onFinish() {
                isMining = false;
                statusText.setText("Inactive");
                statusText.setTextColor(ContextCompat.getColor(HomeActivity.this, R.color.gray));
                startButton.setEnabled(true);
                extendButton.setEnabled(false);
                timerText.setText("00:00:00");
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("isMining", 0);
                    updates.put("isExtended", 0);
                    FirebaseDatabase.getInstance().getReference("users")
                            .child(user.getUid())
                            .updateChildren(updates);
                }
            }
        }.start();
    }

    private void updateTimerText() {
        int hours = (int) (timeLeftInMillis / (1000 * 60 * 60));
        int minutes = (int) ((timeLeftInMillis / (1000 * 60)) % 60);
        int seconds = (int) ((timeLeftInMillis / 1000) % 60);
        timerText.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
    }

    private void scheduleMiningAlarms(String uid, long miningEndTime) {
        long beforeExpiryTime = miningEndTime - (60 * 60 * 1000);
        scheduleAlarm(uid, "beforeExpiry", beforeExpiryTime, 101);
        scheduleAlarm(uid, "atExpiry", miningEndTime, 102);
    }

    private void scheduleAlarm(String uid, String type, long triggerAtMillis, int requestCode) {
        Intent intent = new Intent(this, crypto.mining.elite.receiver.MiningAlarmReceiver.class);
        intent.putExtra("uid", uid);
        intent.putExtra("type", type);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                flags
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
        Intent intent = new Intent(this, crypto.mining.elite.receiver.MiningAlarmReceiver.class);
        int flags = PendingIntent.FLAG_NO_CREATE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                flags
        );
        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
            }
        }
    }

    private String generateMaskedEmail() {
        String[] names = {
                "raj", "amit", "neha", "sara", "john", "alex", "mona", "vivek", "anil", "ravi",
                "suraj", "sahil", "vaibhav", "devil", "toxic", "tanji", "satish", "freefire",
                "garena", "victoria", "arjun", "rahul", "deepak", "vishal", "priya", "pooja",
                "nisha", "riya", "karan", "naman", "kiran", "gopal", "lakshmi", "manoj",
                "manish", "reena", "rekha", "kavita", "swati", "sneha", "jatin", "tarun",
                "yash", "aditya", "ansh", "arvind", "bhavesh", "chirag", "dinesh", "divya",
                "ekta", "farhan", "gautam", "harsh", "ishaan", "jaya", "kamal", "lalit",
                "meena", "naveen", "omkar", "palak", "pratik", "ruchi", "sagar", "tanya",
                "uday", "varun", "wasim", "xavier", "yuvraj", "zeeshan", "alisha", "ayush",
                "bhavna", "chitra", "disha", "esha", "faisal", "geeta", "hitesh", "indra",
                "joseph", "kashish", "lovish", "mahesh", "nikhil", "ojas", "piyush", "qureshi",
                "rekha", "shivam", "tanisha", "usha", "vicky", "waseem", "zoya", "joker", "cr7",
                "msd", "virat", "gamerboy", "indian123", "pubgking", "fflover", "demonx", "darkraj",
                "lucky123", "rockybhai", "shaktimaan", "bhaiya", "yaar", "freak", "anonymous"
        };

        String[] domains = {"gmail.com"};

        Random random = new Random();
        String name = names[random.nextInt(names.length)];
        String domain = domains[random.nextInt(domains.length)];

        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append("****@***.");
        sb.append(domain.substring(domain.indexOf('.') + 1));
        return sb.toString();
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

        // First, clean up expired verifications
        DatabaseReference verificationsRef = FirebaseDatabase.getInstance().getReference("verifications");
        verificationsRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                long currentTime = System.currentTimeMillis();
                Map<String, Object> updates = new HashMap<>();

                // Check each verification record
                for (DataSnapshot snapshot : task.getResult().getChildren()) {
                    Long expireAtValue = snapshot.child("expireAt").getValue(Long.class);
                    if (expireAtValue != null && expireAtValue < currentTime) {
                        // Mark expired verification for deletion
                        updates.put(snapshot.getKey(), null);
                    }
                }

                // Delete expired verifications if any found
                if (!updates.isEmpty()) {
                    verificationsRef.updateChildren(updates).addOnCompleteListener(deleteTask -> {
                        // Continue with creating new verification regardless of delete result
                        createNewVerification(verification, token, buttonType);
                    });
                } else {
                    // No expired verifications, create new one directly
                    createNewVerification(verification, token, buttonType);
                }
            } else {
                // No existing verifications, create new one directly
                createNewVerification(verification, token, buttonType);
            }
        });
    }

    private void createNewVerification(Map<String, Object> verification, String token, String buttonType) {
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

        findViewById(R.id.navStreak).setOnClickListener(v -> startActivity(new Intent(this, StreakActivity.class)));
        findViewById(R.id.navWithdraw).setOnClickListener(v -> startActivity(new Intent(this, WithdrawActivity.class)));
        findViewById(R.id.navRefer).setOnClickListener(v -> startActivity(new Intent(this, ReferActivity.class)));
        findViewById(R.id.navProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userRef != null && userListener != null) userRef.removeEventListener(userListener);
        if (adminRef != null && adminListener != null) adminRef.removeEventListener(adminListener);

        // Cancel all timers
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (rewardTimer != null) {
            rewardTimer.cancel();
        }
        minuteHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // No need to call updateUI() as listeners handle UI updates
    }

}
