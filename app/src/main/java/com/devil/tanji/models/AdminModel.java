package com.devil.tanji.models;

public class AdminModel {
    public String email = "";
    public String password = "";
    public int cpm = 0;
    public int extend_duration = 0;
    public int mining_duration = 0;
    public int reminder_notification = 0;
    public boolean notifications = true;

    public String verifyBaseUrl = "";

    public AdminModel() {
        // Required for Firebase
    }

    public AdminModel(String email, String password, int cpm, int extend_duration,
                            int mining_duration, int reminder_notification, boolean notifications, String verifyBaseUrl) {
        this.email = email;
        this.password = password;
        this.cpm = cpm;
        this.extend_duration = extend_duration;
        this.mining_duration = mining_duration;
        this.reminder_notification = reminder_notification;
        this.notifications = notifications;
        this.verifyBaseUrl = verifyBaseUrl;
    }
}
