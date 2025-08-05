package crypto.mining.elite.models;

public class AdminModel {
    public String email = "";
    public String password = "";
    public int cpm = 0;
    public int extend_duration = 0;
    public int mining_duration = 0;
    public int reminder_notification = 0;
    public boolean notifications = true;

    public String verifyBaseUrl = "";

    public String baseApiUrl = "";
    public String visit = "";
    public String tgUrl = "";
    public int dollar = 0;
    public  String mail ="";
    public String term;
    public int date = 0;

    public AdminModel() {
        // Required for Firebase
    }

    public AdminModel(String email, String password, int cpm, int extend_duration,
                      int mining_duration, int reminder_notification, boolean notifications, String verifyBaseUrl, String baseApiUrl, String tgUrl, String visit, int dollar, String mail, String term, int date) {
        this.email = email;
        this.password = password;
        this.cpm = cpm;
        this.extend_duration = extend_duration;
        this.mining_duration = mining_duration;
        this.reminder_notification = reminder_notification;
        this.notifications = notifications;
        this.verifyBaseUrl = verifyBaseUrl;
        this.baseApiUrl = baseApiUrl;
        this.tgUrl = tgUrl;
        this.visit = visit;
        this.dollar = dollar;
        this.mail = mail;
        this.term = term;
        this.date = date;
    }
}
