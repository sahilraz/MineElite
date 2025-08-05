package crypto.mining.elite.models;

import android.os.Build;

import java.time.Instant;

public class UserReward {
    public String uid;
    public String reward_id;
    public String rewardTitle;
    public String claimedAt;

    public UserReward() {}

    public UserReward(String uid, String reward_id, String rewardTitle, long claimedAtMillis) {
        this.uid = uid;
        this.reward_id = reward_id;
        this.rewardTitle = rewardTitle;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.claimedAt = Instant.ofEpochMilli(claimedAtMillis).toString();
        }
    }
}


