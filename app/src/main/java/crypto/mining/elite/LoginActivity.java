package crypto.mining.elite;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.*;
import com.google.firebase.database.*;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LoginActivity extends AppCompatActivity {
    private EditText emailEditText, passwordEditText;
    private Button signInButton;
    private TextView goToRegisterText;
    private TextView forgotPasswordText;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String sessionId = UUID.randomUUID().toString();
    ProgressBar signInProgress;

    private void requestPermissionsIfNeeded() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

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

        if (mAuth.getCurrentUser() != null) {
            goToHome();
            return;
        }

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        signInButton = findViewById(R.id.signInButton);
        goToRegisterText = findViewById(R.id.goToRegisterText);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        signInProgress = findViewById(R.id.signInProgress);

        signInButton.setOnClickListener(v -> loginUser());

        forgotPasswordText.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, ResetActivity.class));
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

        signInButton.setEnabled(false);
        signInButton.setText(""); // Remove text to show spinner clearly
        findViewById(R.id.signInProgress).setVisibility(View.VISIBLE);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            mDatabase.child("users").child(user.getUid()).child("isBanned")
                                    .get().addOnCompleteListener(banCheck -> {
                                        if (banCheck.isSuccessful()) {
                                            Long isBanned = banCheck.getResult().getValue(Long.class);
                                            if (isBanned != null && isBanned == 1) {
                                                Toast.makeText(this, "You are banned from using this app", Toast.LENGTH_LONG).show();
                                                FirebaseAuth.getInstance().signOut();
                                                signInButton.setEnabled(true);
                                                signInButton.setText(R.string.login);
                                                findViewById(R.id.signInProgress).setVisibility(View.GONE);
                                            } else {
                                                checkAndUpdateFcmToken(user.getUid());
                                            }
                                        } else {
                                            Toast.makeText(this, "Failed to verify ban status", Toast.LENGTH_SHORT).show();
                                            FirebaseAuth.getInstance().signOut();
                                            signInButton.setEnabled(true);
                                            signInButton.setText(R.string.login);
                                            findViewById(R.id.signInProgress).setVisibility(View.GONE);
                                        }
                                    });
                        }
                    } else {
                        Toast.makeText(this, "Authentication failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        signInButton.setEnabled(true);
                        signInButton.setText(R.string.login);
                        findViewById(R.id.signInProgress).setVisibility(View.GONE);
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

                    mDatabase.child("users").child(uid).child("fcmToken")
                            .get().addOnCompleteListener(dbTask -> {
                                if (dbTask.isSuccessful()) {
                                    String storedToken = dbTask.getResult().getValue(String.class);

                                    if (storedToken == null || !storedToken.equals(currentFcmToken)) {
                                        mDatabase.child("users").child(uid).child("fcmToken")
                                                .setValue(currentFcmToken)
                                                .addOnCompleteListener(updateTask -> {
                                                    if (updateTask.isSuccessful()) {
                                                        Toast.makeText(this, "Login Success", Toast.LENGTH_SHORT).show();
                                                    } else {
                                                        Toast.makeText(this, "Failed to update token", Toast.LENGTH_SHORT).show();
                                                    }
                                                    saveSessionAndGoHome(uid);
                                                });
                                    } else {
                                        // FCM token is same, still save session
                                        saveSessionAndGoHome(uid);
                                    }
                                } else {
                                    Toast.makeText(this, "Failed to read stored token", Toast.LENGTH_SHORT).show();
                                    signInButton.setEnabled(true);
                                    signInButton.setText(R.string.login);
                                    findViewById(R.id.signInProgress).setVisibility(View.GONE);
                                }
                            });
                });
    }

    private void saveSessionAndGoHome(String uid) {
        mDatabase.child("users").child(uid).child("sessionId")
                .setValue(sessionId)
                .addOnCompleteListener(sessionTask -> {
                    if (sessionTask.isSuccessful()) {
                        getSharedPreferences("MyPrefs", MODE_PRIVATE)
                                .edit().putString("sessionId", sessionId).apply();
                        goToHome();
                    } else {
                        Toast.makeText(this, "Failed to save session", Toast.LENGTH_SHORT).show();
                        signInButton.setText(R.string.login);
                        signInButton.setEnabled(true);
                    }
                });
    }

    private void goToHome() {
        startActivity(new Intent(LoginActivity.this, HomeActivity.class));
        finish();
    }
}
