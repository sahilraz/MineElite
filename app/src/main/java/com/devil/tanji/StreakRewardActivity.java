package com.devil.tanji;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.devil.tanji.models.UserReward;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.devil.tanji.utils.UserManager;
import com.devil.tanji.utils.AdminManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

public class StreakRewardActivity extends AppCompatActivity {

    private GridLayout cardInsertionArea;
    private TextView loadingText, streakCount;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_streak_reward);

        cardInsertionArea = findViewById(R.id.cardInsertionArea);
        loadingText = findViewById(R.id.loadingText);
        streakCount = findViewById(R.id.streakCount);

        setupBottomNav();
        loadRewardsFromFirebase();
        loadStreak();
    }

    private void loadStreak() {
        UserManager.getInstance().init(user -> {
            if (user == null) {
                Toast.makeText(StreakRewardActivity.this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                return;
            }
            streakCount.setText(String.valueOf(user.streak_count));
        });
    }
    private void loadRewardsFromFirebase() {
        loadingText.setVisibility(View.VISIBLE);
        loadingText.setText("Please wait, fetching streak rewards...");

        UserManager.getInstance().init(user -> {
            if (user == null) {
                Toast.makeText(StreakRewardActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                return;
            }

            AdminManager.getInstance().init(admin -> {
                if (admin == null) {
                    Toast.makeText(StreakRewardActivity.this, "Admin config not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                String apiUrl = admin.baseApiUrl + "/rewards?uid=" + user.uid;

                new Thread(() -> {
                    try {
                        URL url = new URL(apiUrl);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");

                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        JSONObject json = new JSONObject(response.toString());

                        if (!json.getString("status").equalsIgnoreCase("success")) {
                            throw new Exception("Invalid response");
                        }

                        JSONObject rewardsObj = json.getJSONObject("rewards");

                        new Handler(Looper.getMainLooper()).post(() -> {
                            cardInsertionArea.removeAllViews();
                            Iterator<String> keys = rewardsObj.keys();

                            while (keys.hasNext()) {
                                String key = keys.next();
                                try {
                                    JSONObject reward = rewardsObj.getJSONObject(key);

                                    String title = reward.getString("title");
                                    String description = reward.getString("description");
                                    String imageUrl = reward.getString("image_url");
                                    int streakRequired = reward.getInt("streak_required");
                                    boolean isActive = reward.getBoolean("is_active");
                                    boolean isClaimed = reward.getBoolean("is_claimed");
                                    boolean claimable = reward.getBoolean("claimable");
                                    int userStreak = reward.getInt("user_streak");

                                    addRewardCard(title, description, imageUrl, isActive, streakRequired, isClaimed, claimable, userStreak, user.uid);

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }

                            loadingText.setVisibility(View.GONE);
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Toast.makeText(StreakRewardActivity.this, "Failed to load rewards", Toast.LENGTH_SHORT).show();
                            loadingText.setText("Error loading rewards.");
                        });
                    }
                }).start();
            });
        });
    }


    private void addRewardCard(String title, String description, String imageUrl, boolean isActive, int streakRequired, boolean isClaimed, boolean claimable, int userStreak, String uid) {
        LayoutInflater inflater = LayoutInflater.from(this);
        CardView cardView = (CardView) inflater.inflate(R.layout.reward_card, cardInsertionArea, false);

        ImageView rewardImage = cardView.findViewById(R.id.rewardImage);
        TextView rewardTitle = cardView.findViewById(R.id.rewardTitle);
        TextView rewardDays = cardView.findViewById(R.id.rewardDays);
        TextView rewardMoreText = cardView.findViewById(R.id.rewardMoreText);
        TextView rewardButton = cardView.findViewById(R.id.rewardButton);

        rewardTitle.setText(title);
        rewardDays.setText(streakRequired + " days streak");

        Glide.with(this).load(imageUrl).into(rewardImage);

        if (isClaimed) {
            rewardButton.setVisibility(View.VISIBLE);
            rewardButton.setText("Claimed");
            rewardButton.setEnabled(false);
            rewardButton.setBackground(ContextCompat.getDrawable(cardView.getContext(), R.drawable.bg_claimed_button));
            rewardMoreText.setVisibility(View.GONE);
        } else if (claimable) {
            rewardButton.setVisibility(View.VISIBLE);
            rewardButton.setText("Claim");
            rewardButton.setEnabled(true);
            rewardButton.setBackground(ContextCompat.getDrawable(cardView.getContext(), R.drawable.bg_claim_button));
            rewardMoreText.setVisibility(View.GONE);

            rewardButton.setOnClickListener(v -> {
                showClaimPopup(uid, streakRequired, title);
            });
        } else {
            rewardButton.setVisibility(View.VISIBLE);
            int remaining = streakRequired - userStreak;
            rewardButton.setText("Need " + remaining + " more");
            rewardButton.setEnabled(false);
            rewardButton.setBackground(ContextCompat.getDrawable(cardView.getContext(), R.drawable.bg_need_more_button));
            rewardMoreText.setVisibility(View.GONE);
        }

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cardView.setLayoutParams(params);

        cardInsertionArea.addView(cardView);
    }


    private void showClaimPopup(String uid, int streakRequired, String rewardTitle) {
        View popupView = getLayoutInflater().inflate(R.layout.dialog_reward_claimed, null);

        TextView okButton = popupView.findViewById(R.id.okButton);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(popupView)
                .setCancelable(false)
                .create();

        okButton.setOnClickListener(v -> {
            claimRewardInFirebase(uid, rewardTitle);
            dialog.dismiss();
        });

        dialog.show();
    }


    private void claimRewardInFirebase(String uid, String rewardTitle) {
        DatabaseReference rewardsRef = FirebaseDatabase.getInstance().getReference("rewards");

        rewardsRef.orderByChild("title").equalTo(rewardTitle)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            Toast.makeText(StreakRewardActivity.this, "Reward not found", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        for (DataSnapshot child : snapshot.getChildren()) {
                            String rewardId = child.getKey();

                            DatabaseReference userRewardsRef = FirebaseDatabase.getInstance()
                                    .getReference("user_rewards").push();

                            long claimedAtMillis = System.currentTimeMillis();
                            UserReward userReward = new UserReward(uid, rewardId, rewardTitle, claimedAtMillis);

                            userRewardsRef.setValue(userReward)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(StreakRewardActivity.this, "🎉 Reward Claimed!", Toast.LENGTH_SHORT).show();
                                        loadRewardsFromFirebase(); // <---- 🔁 Refresh rewards after claim
                                    });

                            break;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(StreakRewardActivity.this, "Error fetching reward info", Toast.LENGTH_SHORT).show();
                    }
                });
    }



    private void setupBottomNav() {
        int active = getResources().getColor(R.color.nav_active);
        int inactive = getResources().getColor(R.color.nav_inactive);

        ((TextView)findViewById(R.id.iconHome)).setTextColor(inactive);
        ((TextView)findViewById(R.id.textHome)).setTextColor(inactive);

        ((TextView)findViewById(R.id.iconStreak)).setTextColor(active);
        ((TextView)findViewById(R.id.textStreak)).setTextColor(active);

        ((TextView)findViewById(R.id.iconWithdraw)).setTextColor(inactive);
        ((TextView)findViewById(R.id.textWithdraw)).setTextColor(inactive);

        ((TextView)findViewById(R.id.iconRefer)).setTextColor(inactive);
        ((TextView)findViewById(R.id.textRefer)).setTextColor(inactive);

        ((TextView)findViewById(R.id.iconProfile)).setTextColor(inactive);
        ((TextView)findViewById(R.id.textProfile)).setTextColor(inactive);

        findViewById(R.id.navHome).setOnClickListener(v -> startActivity(new Intent(this, HomeActivity.class)));
        findViewById(R.id.navWithdraw).setOnClickListener(v -> startActivity(new Intent(this, WithdrawalActivity.class)));
        findViewById(R.id.navRefer).setOnClickListener(v -> startActivity(new Intent(this, ReferEarnActivity.class)));
        findViewById(R.id.navProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
    }
}
