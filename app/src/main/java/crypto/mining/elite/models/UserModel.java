package crypto.mining.elite.models;

public class UserModel {
    public String email = "";
    public String phone = "";
    public String referCode = "";
    public String usedReferCode = "";  // renamed from 'referredBy'
    public String createdAt = "";
    public String deviceId = "";
    public String fcmToken = "";
    public String uid = "";

    public int coins = 0;
    public int extendVerify = 0;
    public int isBanned = 0;
    public int isMining = 0;
    public int is_extended = 0;
    public int streak_count = 0;
    public int verified = 0;
    public long mining_start_time = 0;
    public long mining_end_time = 0;
    public long last_coin_award_time = 0;


    public UserModel() {
        // Default constructor required for Firebase
    }

    public UserModel(String email, String phone, String referCode, String usedReferCode, String createdAt,
                     String deviceId, String fcmToken, String uid,
                     int coins, int extendVerify, int isBanned, int isMining,
                     int is_extended, int streak_count, int verified) {
        this.email = email;
        this.phone = phone;
        this.referCode = referCode;
        this.usedReferCode = usedReferCode;
        this.createdAt = createdAt;
        this.deviceId = deviceId;
        this.fcmToken = fcmToken;
        this.uid = uid;
        this.coins = coins;
        this.extendVerify = extendVerify;
        this.isBanned = isBanned;
        this.isMining = isMining;
        this.is_extended = is_extended;
        this.streak_count = streak_count;
        this.verified = verified;
    }
}
