package com.cryptos.mining;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.*;
import com.google.firebase.database.*;
import com.google.firebase.messaging.FirebaseMessaging;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;

public class RegisterActivity extends AppCompatActivity {

    private EditText phoneEditText, emailEditText, passwordEditText, referCodeEditText;
    private Button signUpButton;
    private TextView goToLoginText;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("RegisterActivity", "onCreate called ✅");
        setContentView(R.layout.activity_register);

        // Get device ID
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        Log.d("DeviceCheck", "Device ID: " + deviceId);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        phoneEditText = findViewById(R.id.phoneEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        referCodeEditText = findViewById(R.id.referCodeEditText);
        signUpButton = findViewById(R.id.signUpButton);
        goToLoginText = findViewById(R.id.goToLoginText);

        signUpButton.setOnClickListener(v -> {
            Log.d("RegisterActivity", "SignUp button clicked ✅");
            checkIfDeviceAlreadyRegistered();
        });

        goToLoginText.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void checkIfDeviceAlreadyRegistered() {
        mDatabase.child("deviceIds").child(deviceId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                Toast.makeText(this, "An account has already been created from this device", Toast.LENGTH_LONG).show();
                Log.w("DeviceCheck", "Device already registered ❌");
            } else {
                Log.d("DeviceCheck", "Device not registered ✅");
                registerUser(); // Proceed if device is not already used
            }
        });
    }

    private void registerUser() {
        String phone = phoneEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String referCode = referCodeEditText.getText().toString().trim();

        if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        signUpButton.setText(R.string.please_wait);
        signUpButton.setEnabled(false);

        if (!referCode.isEmpty()) {
            validateReferralCodeAndRegister(phone, email, password, referCode);
        } else {
            createUser(phone, email, password, null);
        }
    }

    private void validateReferralCodeAndRegister(String phone, String email, String password, String referCode) {
        mDatabase.child("referralCodes").child(referCode.trim().toUpperCase()).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        createUser(phone, email, password, referCode);
                    } else {
                        Toast.makeText(this, "Invalid referral code", Toast.LENGTH_SHORT).show();
                        signUpButton.setText(R.string.sign_up);
                        signUpButton.setEnabled(true);
                    }
                });
    }

    private void createUser(String phone, String email, String password, String usedReferCode) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser == null) return;

                        String uid = firebaseUser.getUid();
                        String createdAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(new Date());
                        String myReferCode = generateReferralCode();

                        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tokenTask -> {
                            String fcmToken = tokenTask.isSuccessful() ? tokenTask.getResult() : "";

                            HashMap<String, Object> userMap = new HashMap<>();
                            userMap.put("uid", uid);
                            userMap.put("phone", phone);
                            userMap.put("email", email);
                            userMap.put("createdAt", createdAt);
                            userMap.put("referCode", myReferCode);
                            userMap.put("usedReferCode", usedReferCode == null ? "" : usedReferCode);
                            userMap.put("fcmToken", fcmToken);
                            userMap.put("deviceId", deviceId);

                            // Default fields
                            userMap.put("coins", 0);
                            userMap.put("isMining", 0);
                            userMap.put("mining_start", null);
                            userMap.put("mining_end", null);
                            userMap.put("is_extended", 0);
                            userMap.put("extendedAt", null);
                            userMap.put("isBanned", 0);
                            userMap.put("streak_count", 0);
                            userMap.put("last_streak_date", null);
                            userMap.put("verified", 0);
                            userMap.put("verifyExpiry", null);
                            userMap.put("extendVerify", 0);
                            userMap.put("extendVerifyExpiry", null);

                            Map<String, Object> updates = new HashMap<>();
                            updates.put("/users/" + uid, userMap);
                            updates.put("/referralCodes/" + myReferCode, uid);
                            updates.put("/deviceIds/" + deviceId, uid); // ← Restriction tracking

                            mDatabase.updateChildren(updates).addOnCompleteListener(dbTask -> {
                                if (dbTask.isSuccessful()) {
                                    Toast.makeText(this, "Registration successful 🎉", Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(RegisterActivity.this, HomeActivity.class));
                                    finish();
                                } else {
                                    String errorMsg = dbTask.getException() != null ?
                                            dbTask.getException().getMessage() : "Unknown DB error";
                                    Toast.makeText(this, "Database error: " + errorMsg, Toast.LENGTH_SHORT).show();
                                    signUpButton.setText(R.string.sign_up);
                                    signUpButton.setEnabled(true);
                                }
                            });
                        });

                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(this, "Registration failed: " + error, Toast.LENGTH_SHORT).show();
                        signUpButton.setText(R.string.sign_up);
                        signUpButton.setEnabled(true);
                    }
                });
    }

    private String generateReferralCode() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder referralCode = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            int index = random.nextInt(characters.length());
            referralCode.append(characters.charAt(index));
        }

        return referralCode.toString();
    }
}
