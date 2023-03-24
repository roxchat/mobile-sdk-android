package chat.rox.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;
import chat.rox.android.sdk.FAQSearchItem;

public class FAQSearchItemItem implements FAQSearchItem {
    @SerializedName("id")
    private String id;
    @SerializedName("title")
    private String title;
    @SerializedName("score")
    private double score;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public double getScore() {
        return score;
    }
}
