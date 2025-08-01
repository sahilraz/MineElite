package com.cryptos.mining;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.*;
import com.google.firebase.database.*;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends AppCompatActivity {
    private EditText emailEditText, passwordEditText;
    private Button signInButton;
    private TextView goToRegisterText;
    private TextView forgotPasswordText;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private void requestPermissionsIfNeeded() {
        List<String> permissionsToRequest = new ArrayList<>();

        // Notification permission (Android 13+ only)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        // Storage permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        // Optionally check for WRITE_EXTERNAL_STORAGE for older versions
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && // API 28 or lower
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        // Request only if there are missing permissions
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    1001
            );
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        requestPermissionsIfNeeded();

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Keep user logged in for 7 days (Firebase handles this by default, but this check ensures redirect)
        if (mAuth.getCurrentUser() != null) {
            goToHome();
            return;
        }

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        signInButton = findViewById(R.id.signInButton);
        goToRegisterText = findViewById(R.id.goToRegisterText);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);

        signInButton.setOnClickListener(v -> loginUser());

        forgotPasswordText.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, ResetPasswordActivity.class));
        });

        goToRegisterText.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });
    }

    private void loginUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        signInButton.setText(R.string.please_wait);
        signInButton.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Check if user is banned
                            mDatabase.child("users").child(user.getUid()).child("isBanned")
                                    .get().addOnCompleteListener(banCheck -> {
                                        if (banCheck.isSuccessful()) {
                                            Long isBanned = banCheck.getResult().getValue(Long.class);
                                            if (isBanned != null && isBanned == 1) {
                                                Toast.makeText(this, "You are banned from using this app", Toast.LENGTH_LONG).show();
                                                FirebaseAuth.getInstance().signOut();
                                                signInButton.setText(R.string.login);
                                                signInButton.setEnabled(true);
                                            } else {
                                                // Proceed if not banned
                                                checkAndUpdateFcmToken(user.getUid());
                                            }
                                        } else {
                                            Toast.makeText(this, "Failed to verify ban status", Toast.LENGTH_SHORT).show();
                                            FirebaseAuth.getInstance().signOut();
                                            signInButton.setText(R.string.login);
                                            signInButton.setEnabled(true);
                                        }
                                    });
                        }
                    } else {
                        Toast.makeText(this, "Authentication failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        signInButton.setText(R.string.login);
                        signInButton.setEnabled(true);
                    }
                });
    }


    private void checkAndUpdateFcmToken(String uid) {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(tokenTask -> {
                if (!tokenTask.isSuccessful()) {
                    Toast.makeText(this, "Failed to get FCM token", Toast.LENGTH_SHORT).show();
                    return;
                }

                String currentFcmToken = tokenTask.getResult();

                // Fetch stored FCM token from database
                mDatabase.child("users").child(uid).child("fcmToken")
                        .get().addOnCompleteListener(dbTask -> {
                            if (dbTask.isSuccessful()) {
                                String storedToken = dbTask.getResult().getValue(String.class);

                                if (storedToken == null || !storedToken.equals(currentFcmToken)) {
                                    // Update token in database
                                    mDatabase.child("users").child(uid).child("fcmToken")
                                            .setValue(currentFcmToken)
                                            .addOnCompleteListener(updateTask -> {
                                                if (updateTask.isSuccessful()) {
                                                    Toast.makeText(this, "Login Success", Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Toast.makeText(this, "Failed to update token", Toast.LENGTH_SHORT).show();
                                                }
                                                goToHome();
                                            });
                                } else {
                                    // Token matches
                                    goToHome();
                                }
                            } else {
                                Toast.makeText(this, "Failed to read stored token", Toast.LENGTH_SHORT).show();
                                signInButton.setText(R.string.login);
                                signInButton.setEnabled(true);
                            }
                        });
            });
    }

    private void goToHome() {
        startActivity(new Intent(LoginActivity.this, HomeActivity.class));
        finish();
    }
}
