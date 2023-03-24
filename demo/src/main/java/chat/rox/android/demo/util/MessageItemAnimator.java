package chat.rox.android.demo.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import chat.rox.android.demo.R;

public class MessageItemAnimator extends DefaultItemAnimator {

    @Override
    public boolean animateChange(@NonNull RecyclerView.ViewHolder oldHolder, @NonNull RecyclerView.ViewHolder newHolder, @NonNull ItemHolderInfo preInfo, @NonNull ItemHolderInfo postInfo) {
        if (preInfo instanceof SelectMessageItemInfo) {
            View view = oldHolder.itemView;
            ((SelectMessageItemInfo) preInfo).animateSelected(view);
            return true;
        }
        return super.animateChange(oldHolder, newHolder, preInfo, postInfo);
    }

    @Override
    public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
        return true;
    }

    @Override
    public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, @NonNull List<Object> payloads) {
        return true;
    }

    @NonNull
    @Override
    public ItemHolderInfo recordPreLayoutInformation(@NonNull RecyclerView.State state, @NonNull RecyclerView.ViewHolder viewHolder, int changeFlags, @NonNull List<Object> payloads) {

        if (changeFlags == FLAG_CHANGED) {
            for (Object payload : payloads) {
                if (payload instanceof PayloadType) {
                    if (payload == PayloadType.SELECT_MESSAGE) {
                        return new SelectMessageItemInfo();
                    }
                }
            }
        }
        return super.recordPreLayoutInformation(state, viewHolder, changeFlags, payloads);
    }

    public static class SelectMessageItemInfo extends ItemHolderInfo {
        public void animateSelected(View view) {
            int colorFrom = Color.TRANSPARENT;
            int colorTo = ContextCompat.getColor(view.getContext(), R.color.colorMessageItemSelected);
            ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
            colorAnimation.setDuration(500);
            colorAnimation.addUpdateListener(animator -> view.setBackgroundColor((int) animator.getAnimatedValue()));
            colorAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setBackgroundColor(colorFrom);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    view.setBackgroundColor(colorFrom);
                }
            });
            colorAnimation.start();
        }
    }
}
