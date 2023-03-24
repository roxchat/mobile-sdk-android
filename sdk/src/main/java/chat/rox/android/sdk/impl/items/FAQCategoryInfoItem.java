package chat.rox.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;
import chat.rox.android.sdk.FAQCategoryInfo;

public class FAQCategoryInfoItem implements FAQCategoryInfo {
    @SerializedName("id")
    private String id;
    @SerializedName("title")
    private String title;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }
}
