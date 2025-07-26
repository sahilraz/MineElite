package com.devil.tanji;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.devil.tanji.models.UserReward;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.devil.tanji.utils.UserManager;

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

        DatabaseReference rewardsRef = FirebaseDatabase.getInstance().getReference("rewards");

        rewardsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cardInsertionArea.removeAllViews();

                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String title = child.child("title").getValue(String.class);
                        String description = child.child("description").getValue(String.class);
                        String imageUrl = child.child("image_url").getValue(String.class);
                        Boolean isActive = child.child("is_active").getValue(Boolean.class);
                        Long streakRequired = child.child("streak_required").getValue(Long.class);

                        if (title != null && description != null && imageUrl != null && isActive != null && streakRequired != null) {
                            addRewardCard(title, description, imageUrl, isActive, streakRequired.intValue());
                        }
                    }
                    loadingText.setVisibility(View.GONE); // Hide once loaded
                } else {
                    loadingText.setText(R.string.no_rewards_found);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(StreakRewardActivity.this, "Failed to load rewards!", Toast.LENGTH_SHORT).show();
                loadingText.setText("Error loading rewards.");
            }
        });
    }

    private void addRewardCard(String title, String description, String imageUrl, boolean isActive, int streakRequired) {
        LayoutInflater inflater = LayoutInflater.from(this);
        CardView cardView = (CardView) inflater.inflate(R.layout.reward_card, cardInsertionArea, false);

        ImageView rewardImage = cardView.findViewById(R.id.rewardImage);
        TextView rewardTitle = cardView.findViewById(R.id.rewardTitle);
        TextView rewardDays = cardView.findViewById(R.id.rewardDays);
        TextView rewardMoreText = cardView.findViewById(R.id.rewardMoreText);
        TextView rewardButton = cardView.findViewById(R.id.rewardButton); // Add this in XML if not already

        rewardTitle.setText(title);
        rewardDays.setText(streakRequired + " days streak");

        Glide.with(this).load(imageUrl).into(rewardImage);

        UserManager.getInstance().init(user -> {
            if (user == null) return;

            int currentStreak = user.streak_count;

            // First check if already claimed
            FirebaseDatabase.getInstance().getReference("user_rewards")
                    .orderByChild("uid")
                    .equalTo(user.uid)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        boolean alreadyClaimed = false;

                        for (DataSnapshot snap : snapshot.getChildren()) {
                            String rewardId = snap.child("reward_id").getValue(String.class);
                            String rewardTitleDb = snap.child("rewardTitle").getValue(String.class); // Optional field if title stored

                            if (rewardTitle.getText().toString().equalsIgnoreCase(rewardTitleDb)) {
                                alreadyClaimed = true;
                                break;
                            }
                        }

                        if (alreadyClaimed) {
                            rewardButton.setText("Claimed");
                            rewardButton.setBackgroundColor(getResources().getColor(R.color.gray)); // Add gray to colors.xml
                            rewardButton.setEnabled(false);
                            rewardMoreText.setVisibility(View.GONE);
                        } else {
                            if (currentStreak >= streakRequired) {
                                rewardMoreText.setVisibility(View.GONE);
                                rewardButton.setText("Claim");
                                rewardButton.setBackgroundColor(getResources().getColor(R.color.green));
                                rewardButton.setEnabled(true);
                                rewardButton.setOnClickListener(v -> {
                                    showClaimPopup(user.uid, streakRequired, String.valueOf(rewardTitle));
                                });
                            } else {
                                int remaining = streakRequired - currentStreak;
                                rewardMoreText.setText("Need " + remaining + " more");
                                rewardButton.setVisibility(View.GONE);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(StreakRewardActivity.this, "Failed to check reward status", Toast.LENGTH_SHORT).show();
                    });
        });

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cardView.setLayoutParams(params);

        cardInsertionArea.addView(cardView);
    }

    private void showClaimPopup(String uid, int streakRequired, String rewardTitle) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Reward Claimed 🎉")
                .setMessage("We'll send your reward to your email in a few working days.")
                .setPositiveButton("OK", (dialog, which) -> {
                    claimRewardInFirebase(uid, rewardTitle);
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
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
