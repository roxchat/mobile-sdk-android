package chat.rox.android.demo;

import static chat.rox.android.sdk.Message.Attachment;
import static chat.rox.android.sdk.Message.FileInfo;
import static chat.rox.android.sdk.Message.ImageInfo;
import static chat.rox.android.sdk.Message.Quote;
import static chat.rox.android.sdk.Message.SendStatus;
import static chat.rox.android.sdk.Message.Type.FILE_FROM_OPERATOR;
import static chat.rox.android.sdk.Message.Type.FILE_FROM_VISITOR;
import static chat.rox.android.sdk.Message.Type.KEYBOARD_RESPONSE;
import static chat.rox.android.sdk.Message.Type.OPERATOR;
import static chat.rox.android.sdk.Message.Type.VISITOR;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chat.rox.android.demo.items.ViewType;
import chat.rox.android.demo.util.LongClickSupportMovementMethod;
import chat.rox.android.demo.util.LongClickableSpan;
import chat.rox.android.sdk.Message;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MessageHolder> {
    private static final long MILLIS_IN_DAY = 1000 * 60 * 60 * 24;

    private final List<Message> messageList;
    private final RoxChatFragment roxChatFragment;

    MessagesAdapter(RoxChatFragment roxChatFragment) {
        this.roxChatFragment = roxChatFragment;
        this.messageList = new ArrayList<>();
    }

    @NonNull
    @Override
    public MessageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewType vt = ViewType.values()[viewType];
        View view = LayoutInflater.from(roxChatFragment.getContext()).inflate(getLayout(vt),
                parent, false);
        switch (vt) {
            case OPERATOR:
            case FILE_FROM_OPERATOR:
                return new ReceivedMessageHolder(view);
            case VISITOR:
            case FILE_FROM_VISITOR:
                return new SentMessageHolder(view);
            case INFO:
            case INFO_OPERATOR_BUSY:
                return new MessageHolder(view);
            case KEYBOARD:
                return new BotMessageHolder(view);
            default:
                return null;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessageHolder holder, int position) {
        Message message = messageList.get(messageList.size() - position - 1);
        Message prev = position < messageList.size() - 1
                ? messageList.get(messageList.size() - position - 2)
                : null;

        if (prev != null) {
            if ((message.getType().equals(OPERATOR)
                    || message.getType().equals(FILE_FROM_OPERATOR))
                    && (prev.getType().equals(OPERATOR)
                    || prev.getType().equals(FILE_FROM_OPERATOR))
                    && prev.getOperatorId().equals(message.getOperatorId())) {
                ((ReceivedMessageHolder) holder).showSenderInfo = false;
            }
        }

        boolean showDate = false;
        if (prev == null || (message.getTime() / MILLIS_IN_DAY != prev.getTime() / MILLIS_IN_DAY)) {
            showDate = true;
        }

        holder.bind(message, showDate);
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messageList.get(messageList.size() - position - 1);
        if (message.getSendStatus() == SendStatus.SENDING) {
            return ViewType.VISITOR.ordinal();
        }
        switch (message.getType()) {
            case OPERATOR:
                return ViewType.OPERATOR.ordinal();
            case VISITOR:
                return ViewType.VISITOR.ordinal();
            case OPERATOR_BUSY:
                return ViewType.INFO_OPERATOR_BUSY.ordinal();
            case FILE_FROM_VISITOR:
                return message.getAttachment() == null || message.getAttachment().getFileInfo().getImageInfo() != null
                        ? ViewType.VISITOR.ordinal()
                        : ViewType.FILE_FROM_VISITOR.ordinal();
            case FILE_FROM_OPERATOR:
                return message.getAttachment() == null || message.getAttachment().getFileInfo().getImageInfo() != null
                        ? ViewType.OPERATOR.ordinal()
                        : ViewType.FILE_FROM_OPERATOR.ordinal();
            case KEYBOARD:
                return ViewType.KEYBOARD.ordinal();
            case INFO:
            default:
                return ViewType.INFO.ordinal();
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    private int getLayout(ViewType viewType) {
        switch (viewType) {
            case OPERATOR:
            case FILE_FROM_OPERATOR:
                return R.layout.item_message_received;
            case VISITOR:
            case FILE_FROM_VISITOR:
                return R.layout.item_message_sent;
            case INFO_OPERATOR_BUSY:
                return R.layout.item_op_busy_message;
            case KEYBOARD:
                return R.layout.item_keyboard_message;
            case KEYBOARD_RESPONSE:
            case INFO:
            default:
                return R.layout.item_info_message;
        }
    }

    public boolean add(Message message) {
        return messageList.add(message);
    }

    public void add(int position, Message message) {
        int prevItemsCount = messageList.size();
        messageList.add(position, message);
        if (position == 0 && prevItemsCount != 0) {
            int itemsAdded = 1;
            invalidateLastItemDate(itemsAdded);
        }
    }

    public boolean addAll(int position, Collection<? extends Message> collection) {
        int prevItemsCount = messageList.size();
        boolean messagesAdded = messageList.addAll(position, collection);
        if (messagesAdded && position == 0 && prevItemsCount != 0) {
            int itemsAdded = collection.size();
            invalidateLastItemDate(itemsAdded);
        }
        return messagesAdded;
    }

    private void invalidateLastItemDate(int newItemsCount) {
        int lastItemIndex = messageList.size() - 1;
        notifyItemChanged(lastItemIndex - newItemsCount);
    }

    public Message set(int i, Message message) {
        return messageList.set(i, message);
    }

    public void invalidateMessage(String messageId) {
        for (int i = 0; i < messageList.size(); i++) {
            Message message = messageList.get(i);
            if (Objects.equals(message.getServerSideId(), messageId)) {
                int lastIndex = messageList.size() - 1;
                notifyItemChanged(lastIndex - i);
                break;
            }
        }
    }

    public Message remove(int i, String messageId) {
        roxChatFragment.closeContextDialog(messageId);
        return messageList.remove(i);
    }

    public void clear() {
        messageList.clear();
    }

    public int indexOf(Message message) {
        return messageList.indexOf(message);
    }

    public int indexOf(String messageId) {
        for (int i = 0; i < messageList.size(); i++) {
            Message message = messageList.get(i);
            if (messageId.equals(message.getServerSideId())) {
                return i;
            }
        }
        return -1;
    }

    public int lastIndexOf(Message message) {
        return messageList.lastIndexOf(message);
    }

    private void showMessage(String message, Context context) {
        Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    class MessageHolder extends RecyclerView.ViewHolder {
        Message message;
        TextView messageText;
        TextView messageDate;
        TextView messageTime;
        ImageView messageTick;
        RelativeLayout quoteLayout;
        LinearLayout quoteBody;
        TextView quoteSenderName;
        TextView quoteText;
        LinearLayout messageBody;
        private final Pattern pattern = Pattern.compile("\\b(https://|http://)?([-a-zA-Z0-9_@#]+\\.)+[a-zA-Z]+(/[-a-zA-Z0-9_@%#+.!:]+)*(\\.[-a-zA-Z_]+)?(/?\\?[-a-zA-Z0-9_@%#=&+.!:]+)?/?");

        MessageHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.text_message_body);
            messageDate = itemView.findViewById(R.id.text_message_date);
            messageTime = itemView.findViewById(R.id.text_message_time);
            messageTick = itemView.findViewById(R.id.tick);
            quoteLayout = itemView.findViewById(R.id.quote_message);
            quoteBody = itemView.findViewById(R.id.quote_body);
            quoteSenderName = itemView.findViewById(R.id.quote_sender_name);
            quoteText = itemView.findViewById(R.id.quote_text);
            messageBody = itemView.findViewById(R.id.message_body);

            messageText.setMovementMethod(LongClickSupportMovementMethod.getInstance());
            messageText.setHighlightColor(Color.TRANSPARENT);
        }

        public void bind(final Message message, boolean showDate) {
            this.message = message;

            ViewGroup.LayoutParams layoutParams = itemView.getLayoutParams();
            if (message.getType() == KEYBOARD_RESPONSE) {
                layoutParams.height = 0;
                return;
            }
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            messageBody.setVisibility(View.GONE);
            if (messageText != null &&
                    message.getType() != FILE_FROM_OPERATOR &&
                    message.getType() != FILE_FROM_VISITOR) {
                messageBody.setVisibility(View.VISIBLE);
                messageText.setText(handleHyperlinks(handleHyperlinksTemplate(message.getText())));
                messageText.setVisibility(View.VISIBLE);
            }
            if (messageDate != null) {
                if (showDate) {
                    messageDate
                        .setText(DateFormat.getDateFormat(roxChatFragment.getContext())
                        .format(message.getTime()));
                    messageDate.setVisibility(View.VISIBLE);
                } else {
                    messageDate.setVisibility(View.GONE);
                }
            }
            if (messageTime != null) {
                messageTime
                        .setText(DateFormat.getTimeFormat(roxChatFragment.getContext())
                                .format(message.getTime()));
                messageText.setVisibility(View.VISIBLE);
            }

            if (messageTick != null) {
                messageTick.setImageResource(message.isReadByOperator()
                        ? R.drawable.ic_double_tick
                        : R.drawable.ic_tick);
                messageTick.setVisibility(message.getSendStatus() == SendStatus.SENT
                        ? View.VISIBLE
                        : View.GONE);
            }

            if (quoteLayout != null) {
                if (message.getQuote() != null) {
                    quoteLayout.setVisibility(View.VISIBLE);
                    quoteSenderName.setVisibility(View.VISIBLE);
                    quoteText.setVisibility(View.VISIBLE);
                    Resources resources = roxChatFragment.getResources();
                    Quote quote = message.getQuote();
                    quoteLayout.setOnClickListener((v) -> roxChatFragment.onQuoteClicked(message.getQuote()));
                    String textQuoteSenderName = "";
                    String textQuote = "";
                    switch (quote.getState()) {
                        case PENDING:
                            textQuote = (message.getType() == OPERATOR)
                                    ? resources.getString(R.string.quote_is_pending)
                                    : quote.getMessageText();
                            textQuoteSenderName = (message.getType() == OPERATOR)
                                    ? ""
                                    : resources.getString(R.string.visitor_sender_name);
                            break;
                        case FILLED:
                            textQuote =
                                    (quote.getMessageType() == FILE_FROM_OPERATOR
                                            || quote.getMessageType() == FILE_FROM_VISITOR)
                                    ? quote.getMessageAttachment().getFileName()
                                    : quote.getMessageText();
                            textQuoteSenderName =
                                    (quote.getMessageType() == VISITOR
                                            || quote.getMessageType() == FILE_FROM_VISITOR)
                                    ? resources.getString(R.string.visitor_sender_name)
                                    : quote.getSenderName();
                            break;
                        case NOT_FOUND:
                            textQuote = resources.getString(R.string.quote_is_not_found);
                            quoteSenderName.setVisibility(View.GONE);
                            break;
                    }
                    quoteSenderName.setText(textQuoteSenderName);
                    quoteText.setText(textQuote);
                } else {
                    quoteLayout.setVisibility(View.GONE);
                    quoteSenderName.setVisibility(View.GONE);
                    quoteText.setVisibility(View.GONE);
                }
            }
        }

        private CharSequence handleHyperlinksTemplate(CharSequence originalString) {
            Pattern templatePattern = Pattern.compile("(\\[(\\S+)\\]\\((\\S+://\\S+)\\))");
            Matcher matcher = templatePattern.matcher(originalString);

            if (!matcher.find()) {
                return originalString;
            }
            SpannableStringBuilder spannableBuilder = new SpannableStringBuilder(originalString);

            do {
                int start = matcher.start();
                int end = matcher.end();

                int startLink = matcher.start(1);
                int endLink = matcher.end(1);
                if ((startLink | endLink) == -1) {
                    continue;
                }

                int startTemplate = matcher.start(2);
                int endTemplate = matcher.end(2);
                if ((startTemplate | endTemplate) == -1) {
                    continue;
                }

                String urlString = originalString.toString().substring(startLink, endLink);
                String templateString = originalString.toString().substring(startTemplate, endTemplate);

                spannableBuilder.replace(start, end, makeHyperlinkClickable(templateString, urlString));
            } while (matcher.find());

            return spannableBuilder;
        }

        private CharSequence handleHyperlinks(CharSequence originalString) {
            Matcher matcher = pattern.matcher(originalString);

            if (!matcher.find()) {
                return originalString;
            }

            SpannableStringBuilder spannableBuilder = new SpannableStringBuilder(originalString);
            boolean matchesFirst = true;

            while (matchesFirst || matcher.find()) {
                int startLink = matcher.start();
                int endLink = matcher.end();
                if ((startLink | endLink) == -1) {
                    continue;
                }
                String urlString = originalString.toString().substring(startLink, endLink);
                spannableBuilder.replace(startLink, endLink, makeHyperlinkClickable(urlString, urlString));
                matchesFirst = false;
            }
            return spannableBuilder;
        }

        private Spannable makeHyperlinkClickable(String hyperlinkText, final String url) {
            SpannableString spannableString = new SpannableString(hyperlinkText);
            LongClickableSpan clickableSpan = new LongClickableSpan() {
                @Override
                public void onLongClick(View view) {
                    if (MessageHolder.this instanceof FileMessageHolder) {
                        ((FileMessageHolder) MessageHolder.this).openContextDialog();
                    }
                }

                @Override
                public void onClick(@NonNull View widget) {
                    String urlClicked = url;
                    String httpsProtocol = "https://";
                    if (!urlClicked.contains(httpsProtocol)) {
                        urlClicked = httpsProtocol + urlClicked;
                    }
                    roxChatFragment.onLinkClicked(urlClicked);
                }
            };
            spannableString.setSpan(clickableSpan, 0, hyperlinkText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return spannableString;
        }
    }

    class BotMessageHolder extends MessageHolder {
        LinearLayout keyboardView;
        KeyboardAdapter keyboardAdapter;

        BotMessageHolder(final View itemView) {
            super(itemView);

            keyboardView = itemView.findViewById(R.id.lay_bot_keyboard);
            keyboardAdapter = new KeyboardAdapter(keyboardView, buttonId -> roxChatFragment.onKeyBoardButtonClicked(message.getServerSideId(), buttonId));
        }

        @Override
        public void bind(final Message message, boolean showDate) {
            super.bind(message, showDate);

            messageText.setVisibility(View.GONE);
            keyboardAdapter.showKeyboard(message.getKeyboard());
        }
    }

    abstract class FileMessageHolder extends MessageHolder {
        ImageView thumbView;
        ImageView quoteView;
        ImageView fileImage;
        ConstraintLayout layoutQuotedView;
        ConstraintLayout layoutFileImage;
        TextView senderNameForImage;
        TextView fileName;
        TextView fileSize;
        TextView fileError;
        TextView textEdited;
        RelativeLayout layoutAttachedFile;
        ProgressBar progressFileUpload;
        CardView cardView;

        int bubbleColor;
        int selectedColor;
        int messageBackground;

        FileMessageHolder(View itemView) {
            super(itemView);
            thumbView = itemView.findViewById(R.id.attached_image);
            quoteView = itemView.findViewById(R.id.quoted_image);
            layoutQuotedView = itemView.findViewById(R.id.const_quoted_image);
            senderNameForImage = itemView.findViewById(R.id.sender_name_for_image);
            layoutAttachedFile = itemView.findViewById(R.id.attached_file);
            fileImage = itemView.findViewById(R.id.file_image);
            layoutFileImage = itemView.findViewById(R.id.file_image_const);
            fileName = itemView.findViewById(R.id.file_name);
            fileSize = itemView.findViewById(R.id.file_size);
            fileError = itemView.findViewById(R.id.error_text);
            progressFileUpload = itemView.findViewById(R.id.progress_file_upload);
            cardView = itemView.findViewById(R.id.card_view);
            textEdited = itemView.findViewById(R.id.text_edited);

            itemView.setOnClickListener(view -> openContextDialog());
        }

        @Override
        public void bind(Message message, boolean showDate) {
            super.bind(message, showDate);

            thumbView.setVisibility(View.GONE);
            layoutAttachedFile.setVisibility(View.GONE);
            layoutQuotedView.setVisibility(View.GONE);

            messageText.setOnClickListener(v -> openContextDialog());
            Attachment attachment = message.getAttachment();
            if (attachment == null) {
                if (thumbView != null && message.getType().equals(FILE_FROM_VISITOR)) {
                    thumbView.setVisibility(View.GONE);
                    messageBody.setVisibility(View.VISIBLE);
                    Resources resources = roxChatFragment.getResources();
                    String textMessage = resources.getString(R.string.sending_file) + message.getText();
                    messageText.setText(textMessage);
                    messageText.setVisibility(View.VISIBLE);
                }
            } else {
                messageBody.setVisibility(View.GONE);
                ImageInfo imageInfo = attachment.getFileInfo().getImageInfo();
                if (imageInfo == null || mustShowAttachmentView(imageInfo)) {
                    showAttachmentView(attachment);
                } else {
                    setViewSize(thumbView, getThumbSize(imageInfo));
                    addImageInView(attachment.getFileInfo(), thumbView, messageText);
                }
            }

            Message.Quote messageQuote = message.getQuote();
            if (messageQuote != null &&
                    messageQuote.getMessageAttachment() != null &&
                    messageQuote.getMessageAttachment().getImageInfo() != null) {
                FileInfo fileInfo = messageQuote.getMessageAttachment();
                addImageInView(fileInfo, quoteView, quoteText);
                layoutQuotedView.setVisibility(View.VISIBLE);
            }

            boolean notSending = message.getSendStatus() != SendStatus.SENDING;
            textEdited.setVisibility(message.isEdited() && notSending ? View.VISIBLE : View.GONE);
        }

        private boolean mustShowAttachmentView(ImageInfo imageInfo) {
            int maxSide = Math.max(imageInfo.getWidth(), imageInfo.getHeight());
            int minSide = Math.min(imageInfo.getWidth(), imageInfo.getHeight());
            int imageSideRatio = maxSide / minSide;
            int MAX_ALLOWED_ASPECT_RATIO = 10;
            return imageSideRatio > MAX_ALLOWED_ASPECT_RATIO;
        }

        private void showAttachmentView(final Attachment attachment) {
            layoutAttachedFile.setVisibility(View.VISIBLE);
            FileInfo fileInfo = attachment.getFileInfo();
            String filename = fileInfo.getFileName();
            this.fileName.setText(filename);
            fileSize.setVisibility(View.GONE);

            Resources resources = roxChatFragment.getResources();
            switch (attachment.getState()) {
                case READY:
                    fileImage.setVisibility(View.VISIBLE);
                    if (fileInfo.getImageInfo() != null) {
                        fileImage.setImageDrawable(resources.getDrawable(R.drawable.ic_image_attachment));
                        layoutAttachedFile.setOnClickListener(view -> openImageActivity(
                            view,
                            Uri.parse(fileInfo.getUrl()))
                        );
                    } else {
                        Context context = roxChatFragment.requireContext();
                        File cacheDir = context.getExternalCacheDir();
                        String downloadFilename = getDownloadFilename(filename, message.getServerSideId());
                        File file = new File(cacheDir, downloadFilename);
                        if (file.exists()) {
                            fileImage.setImageDrawable(resources.getDrawable(R.drawable.ic_attachment));
                            fileImage.setOnClickListener(view -> roxChatFragment.onOpenFile(file, fileInfo));
                        } else {
                            fileImage.setImageDrawable(resources.getDrawable(R.drawable.ic_download_icon));
                            Uri uri = resolveDownloadUri(downloadFilename, context);
                            fileImage.setOnClickListener(view -> roxChatFragment.onDownloadFile(fileInfo, message.getServerSideId(), uri));
                        }
                    }
                    progressFileUpload.setVisibility(View.GONE);
                    fileSize.setVisibility(View.VISIBLE);
                    String size = humanReadableByteCountBin(fileInfo.getSize());
                    fileSize.setText(size);
                    fileError.setVisibility(View.GONE);
                    break;
                case UPLOAD:
                    if (progressFileUpload.getVisibility() == View.GONE) {
                        fileImage.setVisibility(View.INVISIBLE);
                        progressFileUpload.setVisibility(View.VISIBLE);
                    }
                    fileError.setVisibility(View.VISIBLE);
                    fileError.setText(resources.getString(R.string.file_transfer_by_operator));
                    break;
                case ERROR:
                    fileImage.setImageDrawable(resources.getDrawable(R.drawable.ic_error_download_file));
                    progressFileUpload.setVisibility(View.GONE);
                    fileError.setVisibility(View.VISIBLE);
                    fileError.setText(attachment.getErrorMessage());
                    fileImage.setVisibility(View.VISIBLE);
                    break;
            }

            layoutAttachedFile.setOnLongClickListener(view -> {
                openContextDialog();
                return true;
            });
        }

        private Uri resolveDownloadUri(String filename, Context context) {
            File externalCacheDir = context.getExternalCacheDir();
            return Uri.withAppendedPath(Uri.fromFile(externalCacheDir), filename);
        }

        private String humanReadableByteCountBin(long bytes) {
            long b = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
            return b < 1024L ? bytes + " B"
                : b <= 0xfffccccccccccccL >> 40 ? String.format(Locale.getDefault(), "%.0f kB", bytes / 0x1p10)
                : b <= 0xfffccccccccccccL >> 30 ? String.format(Locale.getDefault(), "%.0f MB", bytes / 0x1p20)
                : b <= 0xfffccccccccccccL >> 20 ? String.format(Locale.getDefault(), "%.0f GB", bytes / 0x1p30)
                : b <= 0xfffccccccccccccL >> 10 ? String.format(Locale.getDefault(), "%.0f TB", bytes / 0x1p40)
                : b <= 0xfffccccccccccccL ? String.format(Locale.getDefault(), "%.0f PiB", (bytes >> 10) / 0x1p40)
                : String.format(Locale.getDefault(), "%.0f EiB", (bytes >> 20) / 0x1p40);
        }

        private void addImageInView(
            FileInfo fileInfo,
            ImageView imageView,
            TextView textView) {

            String imageUrl = fileInfo.getImageInfo().getThumbUrl();
            Glide.with(roxChatFragment.getContext())
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .into(imageView);
            imageView.setOnClickListener(view -> openImageActivity(view, Uri.parse(fileInfo.getUrl())));
            imageView.setOnLongClickListener(view -> {
                openContextDialog();
                return true;
            });
            textView.setText(roxChatFragment.getResources().getString(R.string.reply_message_with_image));
            textView.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.VISIBLE);
        }


        private Size getThumbSize(ImageInfo imageInfo) {
            Size size;
            int imageWidth = imageInfo.getWidth();
            int imageHeight = imageInfo.getHeight();
            boolean isPortraitOrientation = imageHeight > imageWidth;
            if (isPortraitOrientation) {
                ScaledSize scaledSize = scalingThumbSize(
                    imageWidth,
                    imageHeight,
                    isPortraitOrientation);
                size = new Size(scaledSize.getShotSide(), scaledSize.getLongSide());
            } else {
                ScaledSize scaledSize = scalingThumbSize(
                    imageHeight,
                    imageWidth,
                    isPortraitOrientation);
                size = new Size(scaledSize.getLongSide(), scaledSize.getShotSide());
            }
            return size;
        }

        private ScaledSize scalingThumbSize(double shotSide,
                                            double longSide,
                                            boolean portraitOrientationImage) {
            double maxRatioForView = portraitOrientationImage ? 1 : 0.6;
            double minRatioForView = 0.17;
            int orientation = roxChatFragment.getActivity()
                    .getResources().getConfiguration().orientation;
            DisplayMetrics displayMetrics = new DisplayMetrics();
            roxChatFragment.getActivity().getWindowManager()
                    .getDefaultDisplay().getMetrics(displayMetrics);
            double shorterScreenSide = orientation == Configuration.ORIENTATION_PORTRAIT
                    ? displayMetrics.widthPixels
                    : displayMetrics.heightPixels;
            double maxSize = shorterScreenSide * maxRatioForView;
            double minSize = shorterScreenSide * minRatioForView;
            double newShotSide = maxSize / longSide * shotSide;
            if (newShotSide < minSize) {
                shotSide = minSize;
            } else {
                shotSide = Math.min(newShotSide, maxSize);
            }
            longSide = maxSize;
            if (portraitOrientationImage) {
                double maxRatioForPortraitView = 0.6;
                double maxWidth = shorterScreenSide * maxRatioForPortraitView;
                if (shotSide > maxWidth) {
                    longSide = maxWidth / shotSide * maxSize;
                    shotSide = maxWidth;
                }
            }
            return new ScaledSize((int) shotSide, (int) longSide);
        }

        private void setViewSize(ImageView view, Size size) {
            if (view.getParent() instanceof FrameLayout) {
                view.setLayoutParams(new FrameLayout.LayoutParams(size.getWidth(), size.getHeight(), Gravity.END));
            }
            else {
                view.setLayoutParams(new LinearLayout.LayoutParams(size.getWidth(), size.getHeight(), Gravity.END));
            }
        }

        private void openImageActivity(View view, Uri fileUrl) {
            Intent openImgIntent = new Intent(view.getContext(), ImageActivity.class);
            openImgIntent.setData(fileUrl);
            Activity activity = getRequiredActivity(view);
            if (activity != null) {
                activity.startActivity(openImgIntent);
                activity.overridePendingTransition(R.anim.pull_in_right, R.anim.push_out_left);
            }
        }

        private Activity getRequiredActivity(View view) {
            Context context = view.getContext();
            while (context instanceof ContextWrapper) {
                if (context instanceof Activity) {
                    return (Activity)context;
                }
                context = ((ContextWrapper)context).getBaseContext();
            }
            return null;
        }

        private class Size {
            private final int width;
            private final int height;

            private Size(int width, int height) {
                this.width = width;
                this.height = height;
            }

            int getWidth() {
                return width;
            }

            int getHeight() {
                return height;
            }
        }

        private class ScaledSize {
            private final int shotSide;
            private final int longSide;

            private ScaledSize(int shotSide, int longSide) {
                this.shotSide = shotSide;
                this.longSide = longSide;
            }

            int getShotSide() {
                return shotSide;
            }

            int getLongSide() {
                return longSide;
            }
        }

        void openContextDialog() {
            int adapterPosition = getAdapterPosition();
            roxChatFragment.openContextDialog(adapterPosition, message, getVisibleView());
            if (this instanceof SentMessageHolder) {
                roxChatFragment.updateSentMessageDialog(message, adapterPosition);
            } else if (this instanceof ReceivedMessageHolder) {
                roxChatFragment.updateReceivedMessageDialog(message, adapterPosition);
            }
        }

        private View getVisibleView() {
            switch (message.getType()) {
                case FILE_FROM_VISITOR:
                case FILE_FROM_OPERATOR:
                    Attachment attachment = message.getAttachment();
                    if (attachment != null && attachment.getFileInfo().getImageInfo() != null) {
                        return cardView;
                    } else {
                        return layoutAttachedFile;
                    }
                default:
                    return messageBody;
            }
        }
    }

    private class SentMessageHolder extends FileMessageHolder {
        ProgressBar sendingProgress;

        SentMessageHolder(final View itemView) {
            super(itemView);

            messageBackground = R.drawable.background_send_message;
            bubbleColor = R.color.sendingMsgBubble;
            selectedColor = R.color.sendingMsgSelect;

            sendingProgress = itemView.findViewById(R.id.sending_msg);
        }

        @Override
        public void bind(Message message, boolean showDate) {
            super.bind(message, showDate);

            roxChatFragment.updateSentMessageDialog(message, getAdapterPosition());
            boolean sending = message.getSendStatus() == SendStatus.SENDING;
            if (messageTime != null) {
                messageTime.setVisibility(sending ? View.GONE : View.VISIBLE);
            }
            if (sendingProgress != null) {
                sendingProgress.setVisibility(sending ? View.VISIBLE : View.GONE);
            }
        }
    }

    private class ReceivedMessageHolder extends FileMessageHolder {
        boolean showSenderInfo = true;
        TextView timeText;
        TextView nameText;
        TextView nameTextForImage;
        TextView nameTextForFile;
        ImageView profileImage;

        ReceivedMessageHolder(final View itemView) {
            super(itemView);

            messageBackground = R.drawable.background_received_message;
            bubbleColor = R.color.receivedMsgBubble;
            selectedColor = R.color.receivedMsgSelect;

            timeText = itemView.findViewById(R.id.text_message_time);
            nameText = itemView.findViewById(R.id.sender_name);
            nameTextForImage = itemView.findViewById(R.id.sender_name_for_image);
            nameTextForFile = itemView.findViewById(R.id.sender_name_for_file);
            profileImage = itemView.findViewById(R.id.sender_photo);
        }

        @Override
        public void bind(Message message, boolean showDate) {
            super.bind(message, showDate);

            roxChatFragment.updateReceivedMessageDialog(message, getAdapterPosition());
            timeText.setText(DateFormat.getTimeFormat(roxChatFragment.getContext())
                    .format(message.getTime()));
            if (showSenderInfo) {
                if (message.getType().equals(FILE_FROM_OPERATOR) && fileIsImage(message)) {
                    nameTextForImage.setVisibility(View.VISIBLE);
                    nameTextForImage.setText(message.getSenderName());
                } else {
                    nameTextForImage.setVisibility(View.GONE);
                    if (message.getType().equals(FILE_FROM_OPERATOR)) {
                        nameTextForFile.setVisibility(View.VISIBLE);
                        nameTextForFile.setText(message.getSenderName());
                    } else {
                        nameText.setVisibility(View.VISIBLE);
                        nameText.setText(message.getSenderName());
                    }
                }

                String avatarUrl = message.getSenderAvatarUrl();
                profileImage.setVisibility(View.VISIBLE);
                if (avatarUrl != null) {
                    if (!avatarUrl.equals(profileImage.getTag(R.id.avatarUrl))) {
                        Glide.with(roxChatFragment.getContext()).load(avatarUrl).into(profileImage);
                        profileImage.setTag(R.id.avatarUrl, avatarUrl);
                    }
                } else {
                    Resources resources = roxChatFragment.getResources();
                    profileImage.setImageDrawable(resources.getDrawable(R.drawable.default_operator_avatar));
                    profileImage.setVisibility(View.VISIBLE);
                }
            } else {
                nameText.setVisibility(View.GONE);
                nameTextForImage.setVisibility(View.GONE);
                profileImage.setVisibility(View.GONE);
                showSenderInfo = true;
            }
        }
    }

    private boolean fileIsImage(Message message) {
        return message.getAttachment() != null && message.getAttachment().getFileInfo().getImageInfo() != null;
    }

    @NonNull
    public static String getDownloadFilename(String filename, String messageId) {
        int lastIndex = filename.lastIndexOf('.');
        if (lastIndex != -1) {
            String filenameWithoutExtension = filename.substring(0, lastIndex);
            String extension = filename.substring(lastIndex);
            return filenameWithoutExtension + "-" + messageId + extension;
        } else {
            return filename + messageId;
        }
    }
}