package com.cryptos.mining;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class WithdrawalActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_withdrawal);
        setupBottomNav();

        TextView comingSoon = findViewById(R.id.comingSoonTitle);
        Shader shader = new LinearGradient(0, 0, 0, comingSoon.getTextSize(),
                new int[]{Color.parseColor("#FF5F6D"), Color.parseColor("#6A85F1")},
                null, Shader.TileMode.CLAMP);
        comingSoon.getPaint().setShader(shader);

    }

    private void setupBottomNav() {
        int active = getResources().getColor(R.color.nav_active);
        int inactive = getResources().getColor(R.color.nav_inactive);

        ((TextView) findViewById(R.id.iconHome)).setTextColor(inactive);
        ((TextView) findViewById(R.id.textHome)).setTextColor(inactive);

        ((TextView) findViewById(R.id.iconStreak)).setTextColor(inactive);
        ((TextView) findViewById(R.id.textStreak)).setTextColor(inactive);

        ((TextView) findViewById(R.id.iconWithdraw)).setTextColor(active);
        ((TextView) findViewById(R.id.textWithdraw)).setTextColor(active);

        ((TextView) findViewById(R.id.iconRefer)).setTextColor(inactive);
        ((TextView) findViewById(R.id.textRefer)).setTextColor(inactive);

        ((TextView) findViewById(R.id.iconProfile)).setTextColor(inactive);
        ((TextView) findViewById(R.id.textProfile)).setTextColor(inactive);

        findViewById(R.id.navHome).setOnClickListener(v -> startActivity(new Intent(this, HomeActivity.class)));
        findViewById(R.id.navStreak).setOnClickListener(v -> startActivity(new Intent(this, StreakRewardActivity.class)));
        findViewById(R.id.navRefer).setOnClickListener(v -> startActivity(new Intent(this, ReferEarnActivity.class)));
        findViewById(R.id.navProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        // Withdrawal tab is already active, so no action assigned
    }
} 