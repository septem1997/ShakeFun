package com.septem.shakefun;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;
import com.nineoldandroids.animation.ValueAnimator;

public class RunningTextView extends TextView
{
    private static final int STOPPED = 0;
    private static final int RUNNING = 1;
    private int playingState = STOPPED;
    private int endNumber;
    private int beginNumber = 0;


    private long duration = 1500;

    private EndListener endListener = null;

    public RunningTextView(Context context)
    {
        super(context);
    }

    public RunningTextView(Context context, AttributeSet attr)
    {
        super(context, attr);
    }

    public RunningTextView(Context context, AttributeSet attr, int defStyle)
    {
        super(context, attr, defStyle);
    }


    public boolean isRunning()
    {
        return (playingState == RUNNING);
    }


    private void runNumber()
    {

        ValueAnimator valueAnimator = ValueAnimator.ofInt(beginNumber, endNumber);
        valueAnimator.setDuration(duration);

        valueAnimator
                .addUpdateListener(new ValueAnimator.AnimatorUpdateListener()
                {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator)
                    {
                        setText(valueAnimator.getAnimatedValue().toString());
                        if (valueAnimator.getAnimatedFraction() >= 1)
                        {
                            playingState = STOPPED;
                            if (endListener != null)
                                endListener.onEndFinish();
                        }
                    }
                });
        valueAnimator.start();
    }




    public void startRunning()
    {

        if (!isRunning())
        {
            playingState = RUNNING;
            runNumber();
        }
    }

    public void withNumber(int number)
    {
        this.endNumber = number;
    }

    public void setDuration(long duration)
    {
        this.duration = duration;
    }

    public void setOnEndListener(EndListener callback)
    {
        endListener = callback;
    }

    

    public interface EndListener
    {
        void onEndFinish();
    }
}
  