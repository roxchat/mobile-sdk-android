package chat.rox.android.sdk.impl.items.responses;

import com.google.gson.annotations.SerializedName;

import chat.rox.android.sdk.impl.items.FileParametersItem;

public class UploadResponse extends DefaultResponse {
    @SerializedName("data")
    private FileParametersItem data;

    public UploadResponse() {

    }

    public FileParametersItem getData() {
        return data;
    }
}
