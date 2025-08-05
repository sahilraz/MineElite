package crypto.mining.elite;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import crypto.mining.elite.utils.AdminManager;
import crypto.mining.elite.utils.UserManager;
import com.google.firebase.auth.FirebaseAuth;
import crypto.mining.elite.utils.SessionManager;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ProfileActivity extends AppCompatActivity {
    private TextView emailText, phoneText, coinText, streakText, miningText, rewardText, toggle, content, terms;
    private ImageButton logoutButton;
    private LinearLayout miningHistoryContainer;
    private TextView miningHistoryPlaceholder;
    private LinearLayout emailButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        SessionManager.startSessionMonitor(this);

        emailText = findViewById(R.id.emailText);
        phoneText = findViewById(R.id.phoneText);
        coinText = findViewById(R.id.coinText);
        streakText = findViewById(R.id.streakText);
        miningText = findViewById(R.id.miningText);
        rewardText = findViewById(R.id.rewardText);
        logoutButton = findViewById(R.id.logoutButton);
        miningHistoryContainer = findViewById(R.id.miningHistoryContainer);
        miningHistoryPlaceholder = findViewById(R.id.miningHistoryPlaceholder);
        emailButton = findViewById(R.id.emailAdminButton);
        toggle = findViewById(R.id.termsToggleTextView);
        terms = findViewById(R.id.termsContentTextView);


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

            DatabaseReference rewardsRef = FirebaseDatabase.getInstance().getReference("user_rewards");
            Query rewardQuery = rewardsRef.orderByChild("uid").equalTo(user.uid);

            rewardQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    int rewardCount = (int) snapshot.getChildrenCount();
                    rewardText.setText(String.valueOf(rewardCount));
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    rewardText.setText("0");
                    Toast.makeText(ProfileActivity.this, "Failed to load rewards", Toast.LENGTH_SHORT).show();
                }
            });

            loadMiningHistory(user.uid); // Load mining history after user loaded
        });
        AdminManager.getInstance().init(admin -> {
            if (admin == null || admin.mail == null || admin.mail.isEmpty()) {
                Toast.makeText(ProfileActivity.this, "Failed to load email", Toast.LENGTH_SHORT).show();
                return;
            }
            terms.setText(Html.fromHtml(formatTerms(admin.term)));

            emailButton.setOnClickListener(v -> {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                        "mailto", admin.mail, null));
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Support Request");
                startActivity(Intent.createChooser(emailIntent, "Send email..."));
            });

            toggle.setOnClickListener(v -> {
                if (terms.getVisibility() == View.GONE) {
                    terms.setVisibility(View.VISIBLE);
                    toggle.setText("▲ Terms & Conditions");
                } else {
                    terms.setVisibility(View.GONE);
                    toggle.setText("▼ Terms & Conditions");
                }
            });
        });
    }

    private String formatTerms(String raw) {
        // Step 1: Replace \n with <br>
        String formatted = raw.replace("\n", "<br>");

        // Step 2: Replace [#hex](text) with <font color='#hex'>text</font>
        Pattern pattern = Pattern.compile("\\[(#\\w{6})\\]\\((.*?)\\)");
        Matcher matcher = pattern.matcher(formatted);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String color = matcher.group(1);
            String text = matcher.group(2);
            String replacement = "<font color='" + color + "'>" + text + "</font>";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }


    private void loadMiningHistory(String uid) {
        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference("mining_history");
        Query query = historyRef.orderByChild("uid").equalTo(uid);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                miningHistoryContainer.removeAllViews();

                if (snapshot.getChildrenCount() == 0 || !snapshot.exists() || !snapshot.hasChildren()) {
                    miningHistoryPlaceholder.setVisibility(View.VISIBLE);
                    return;
                }

                miningHistoryPlaceholder.setVisibility(View.GONE);

                for (DataSnapshot entry : snapshot.getChildren()) {
                    long coins = entry.child("coins").getValue(Long.class) != null ? entry.child("coins").getValue(Long.class) : 0;
                    long timestamp = entry.child("timestamp").getValue(Long.class) != null ? entry.child("timestamp").getValue(Long.class) : 0;
                    String tag = entry.child("label").getValue(String.class);
                    if (tag == null) tag = "Mined Coins";

                    String dateFormatted = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            .format(new Date(timestamp * 1000L));

                    // 💳 Outer card layout
                    LinearLayout card = new LinearLayout(ProfileActivity.this);
                    card.setOrientation(LinearLayout.HORIZONTAL);
                    card.setPadding(24, 20, 24, 20);
                    card.setBackground(getDrawable(R.drawable.stat_card_bg));
                    card.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    ));
                    LinearLayout.LayoutParams cardParams = (LinearLayout.LayoutParams) card.getLayoutParams();
                    cardParams.setMargins(0, 12, 0, 0);
                    card.setLayoutParams(cardParams);
                    card.setGravity(Gravity.CENTER_VERTICAL);
                    card.setWeightSum(1f);

                    // 🔤 Left side: text (label + timestamp)
                    LinearLayout leftColumn = new LinearLayout(ProfileActivity.this);
                    leftColumn.setOrientation(LinearLayout.VERTICAL);
                    leftColumn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.75f));

                    TextView label = new TextView(ProfileActivity.this);
                    label.setText(tag);
                    label.setTextSize(15);
                    label.setTextColor(Color.WHITE);

                    TextView timeView = new TextView(ProfileActivity.this);
                    timeView.setText(dateFormatted);
                    timeView.setTextSize(12);
                    timeView.setTextColor(Color.parseColor("#B0BEC5"));
                    timeView.setPadding(0, 4, 0, 0);

                    leftColumn.addView(label);
                    leftColumn.addView(timeView);

                    // 💰 Right side: icon + amount aligned right
                    LinearLayout rightColumn = new LinearLayout(ProfileActivity.this);
                    rightColumn.setOrientation(LinearLayout.HORIZONTAL);
                    rightColumn.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                    rightColumn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.25f));

                    ImageView coinIcon = new ImageView(ProfileActivity.this);
                    coinIcon.setImageResource(R.drawable.ic_coin);
                    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(32, 32);
                    iconParams.setMargins(0, 0, 8, 0);
                    coinIcon.setLayoutParams(iconParams);

                    TextView coinAmount = new TextView(ProfileActivity.this);
                    coinAmount.setText(String.valueOf(coins));
                    coinAmount.setTextSize(16);
                    coinAmount.setTextColor(Color.parseColor("#FFD700"));
                    coinAmount.setTypeface(null, Typeface.BOLD);

                    rightColumn.addView(coinIcon);
                    rightColumn.addView(coinAmount);

                    // 👥 Add both sides to card
                    card.addView(leftColumn);
                    card.addView(rightColumn);

                    miningHistoryContainer.addView(card);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(ProfileActivity.this, "Failed to load mining history", Toast.LENGTH_SHORT).show();
                miningHistoryPlaceholder.setVisibility(View.VISIBLE);
            }
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
        findViewById(R.id.navStreak).setOnClickListener(v -> startActivity(new Intent(this, StreakActivity.class)));
        findViewById(R.id.navWithdraw).setOnClickListener(v -> startActivity(new Intent(this, WithdrawActivity.class)));
        findViewById(R.id.navRefer).setOnClickListener(v -> startActivity(new Intent(this, ReferActivity.class)));
    }
}
