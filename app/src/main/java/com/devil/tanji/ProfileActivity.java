package com.devil.tanji;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.devil.tanji.utils.UserManager;
import com.google.firebase.auth.FirebaseAuth;

public class ProfileActivity extends AppCompatActivity {
    private TextView emailText, phoneText, coinText, streakText, miningText, rewardText;
    private ImageButton logoutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        emailText = findViewById(R.id.emailText);
        phoneText = findViewById(R.id.phoneText);
        coinText = findViewById(R.id.coinText);
        streakText = findViewById(R.id.streakText);
        miningText = findViewById(R.id.miningText);
        rewardText = findViewById(R.id.rewardText);
        logoutButton = findViewById(R.id.logoutButton);

        loadProfile();
        setupBottomNav();
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        });
    }

    private void loadProfile() {
        UserManager.getInstance().init(user -> {
            if (user == null) {
                Toast.makeText(ProfileActivity.this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                return;
            }

            emailText.setText("Email: " + user.email);
            phoneText.setText("Phone: " + user.phone);
            coinText.setText(String.valueOf(user.coins));
            streakText.setText(String.valueOf(user.streak_count));
            miningText.setText(getString(user.isMining == 1 ? R.string.status_active : R.string.status_inactive));

        });
    }


    private void setupBottomNav() {
        int active = getResources().getColor(R.color.nav_active);
        int inactive = getResources().getColor(R.color.nav_inactive);
        ((TextView)findViewById(R.id.iconHome)).setTextColor(inactive);
        ((TextView)findViewById(R.id.textHome)).setTextColor(inactive);
        ((TextView)findViewById(R.id.iconStreak)).setTextColor(inactive);
        ((TextView)findViewById(R.id.textStreak)).setTextColor(inactive);
        ((TextView)findViewById(R.id.iconWithdraw)).setTextColor(inactive);
        ((TextView)findViewById(R.id.textWithdraw)).setTextColor(inactive);
        ((TextView)findViewById(R.id.iconRefer)).setTextColor(inactive);
        ((TextView)findViewById(R.id.textRefer)).setTextColor(inactive);
        ((TextView)findViewById(R.id.iconProfile)).setTextColor(active);
        ((TextView)findViewById(R.id.textProfile)).setTextColor(active);

        findViewById(R.id.navHome).setOnClickListener(v -> startActivity(new Intent(this, HomeActivity.class)));
        findViewById(R.id.navStreak).setOnClickListener(v -> startActivity(new Intent(this, StreakRewardActivity.class)));
        findViewById(R.id.navWithdraw).setOnClickListener(v -> startActivity(new Intent(this, WithdrawalActivity.class)));
        findViewById(R.id.navRefer).setOnClickListener(v -> startActivity(new Intent(this, ReferEarnActivity.class)));
    }
}
