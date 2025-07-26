package com.devil.tanji;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.devil.tanji.utils.UserManager;
import com.google.firebase.database.FirebaseDatabase;

public class ReferEarnActivity extends AppCompatActivity {

    private TextView referCode, referCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refer_earn);

        referCode = findViewById(R.id.referralCodeText);
        referCount = findViewById(R.id.totalReferralsText);

        loadRefer();
        setupBottomNav();

        findViewById(R.id.copyButton).setOnClickListener(v -> {
            String code = referCode.getText().toString();
            if (!code.isEmpty()) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Referral Code", code);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(ReferEarnActivity.this, "Referral code copied!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ReferEarnActivity.this, "Referral code is empty", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRefer() {
        UserManager.getInstance().init(user -> {
            if (user == null) {
                Toast.makeText(ReferEarnActivity.this, "Failed to load refer data", Toast.LENGTH_SHORT).show();
                return;
            }

            referCode.setText(String.valueOf(user.referCode));

            // Count users who used this refer code
            FirebaseDatabase.getInstance().getReference("users")
                    .orderByChild("usedReferCode")
                    .equalTo(user.referCode)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        long count = snapshot.getChildrenCount();
                        referCount.setText(String.valueOf(count));
                    });
        });
    }


    private void setupBottomNav() {
        int active = getResources().getColor(R.color.nav_active);
        int inactive = getResources().getColor(R.color.nav_inactive);

        ((TextView) findViewById(R.id.iconHome)).setTextColor(inactive);
        ((TextView) findViewById(R.id.textHome)).setTextColor(inactive);

        ((TextView) findViewById(R.id.iconStreak)).setTextColor(inactive);
        ((TextView) findViewById(R.id.textStreak)).setTextColor(inactive);

        ((TextView) findViewById(R.id.iconWithdraw)).setTextColor(inactive);
        ((TextView) findViewById(R.id.textWithdraw)).setTextColor(inactive);

        ((TextView) findViewById(R.id.iconRefer)).setTextColor(active);
        ((TextView) findViewById(R.id.textRefer)).setTextColor(active);

        ((TextView) findViewById(R.id.iconProfile)).setTextColor(inactive);
        ((TextView) findViewById(R.id.textProfile)).setTextColor(inactive);

        findViewById(R.id.navHome).setOnClickListener(v -> startActivity(new Intent(this, HomeActivity.class)));
        findViewById(R.id.navStreak).setOnClickListener(v -> startActivity(new Intent(this, StreakRewardActivity.class)));
        findViewById(R.id.navWithdraw).setOnClickListener(v -> startActivity(new Intent(this, WithdrawalActivity.class)));
        findViewById(R.id.navProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        // Refer & Earn tab is already active, no action needed
    }
} 