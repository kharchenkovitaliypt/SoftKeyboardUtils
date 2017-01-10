package com.kharchenkovitaliypt.softkeyboard;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SoftKeyboard {
    private static final String TAG = SoftKeyboard.class.getSimpleName();

    private SoftKeyboard() {}

    public static void hide(Activity activity) {
        if(activity != null) {
            innerHide(activity, activity.getWindow().getAttributes().token);
        }
    }

    public static void hide(View view) {
        View focusedView = view.findFocus();
        if(focusedView != null) {
            innerHide(focusedView.getContext(), focusedView.getWindowToken());
        }
    }

    private static void innerHide(Context ctx, IBinder windowToken) {
        getInputMethodManager(ctx).hideSoftInputFromWindow(windowToken, 0);
    }

    public static void show(View view) {
        view.requestFocus();
        getInputMethodManager(view.getContext())
                .showSoftInput(view, InputMethodManager.SHOW_FORCED);
    }

    public static Cancelable setOnShowChangedListener(Activity activity, OnShowChangedListener listener) {
        return setOnShowChangedListener(activity.getWindow().getDecorView(), listener);
    }

    public static Cancelable setOnShowChangedListener(final View view, OnShowChangedListener listener) {
        // Init listener
        final ShowChangedContext showChangeContext =
                new ShowChangedContext(getInputMethodManager(view.getContext()), listener);
        showChangeContext.notifyListener(view);

        final ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener =
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        showChangeContext.notifyListener(view);
                    }
                };
        view.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);

        return new ShowChangedCancelable(view, globalLayoutListener, showChangeContext);
    }

    private static class ShowChangedCancelable implements Cancelable {
        private View view;
        private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;
        private ShowChangedContext showChangeContext;

        ShowChangedCancelable(View view,
                              ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener,
                              ShowChangedContext showChangeContext) {

            this.view = view;
            this.globalLayoutListener = globalLayoutListener;
            this.showChangeContext = showChangeContext;
        }

        @Override
        public void cancel() {
            if(showChangeContext != null) {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
                showChangeContext.cancelFullScreenModeListener();
                view = null;
                globalLayoutListener = null;
                showChangeContext = null;
            }
        }
    }

    private static class ShowChangedContext {
        final OnShowChangedListener listener;
        final InputMethodManager imm;
        Cancelable fullScreenModeListenerCancelable;
        Boolean shown;
        boolean fullScreenMode;
        int onAppHeight;

        ShowChangedContext(InputMethodManager imm, OnShowChangedListener listener) {
            this.listener = listener;
            this.imm = imm;
        }

        void notifyListener(final View view) {
            boolean fullScreenMode = imm.isFullscreenMode();
            boolean shown = fullScreenMode || isShown(view);
            int onAppHeight = getOnAppVisibleHeight(view);

            if(this.shown == null || this.shown != shown
                    || this.fullScreenMode != fullScreenMode
                    || this.onAppHeight != onAppHeight) {

                if(!fullScreenMode) {
                    cancelFullScreenModeListener();
                }

                this.shown = shown;
                this.fullScreenMode = fullScreenMode;
                this.onAppHeight = onAppHeight;
                listener.onShowChanged(shown, fullScreenMode, onAppHeight);

                if(fullScreenMode) {
                    // Start full screen mode listener
                    if (fullScreenModeListenerCancelable == null) {
                        fullScreenModeListenerCancelable = setFullScreenModeChangeListener(
                                view, new OnFullScreenModeChangedListener() {
                            @Override
                            public void onFullScreenModeChanged(boolean fullScreen) {
                                notifyListener(view);
                            }
                        });
                    }
                }
            }
        }

        void cancelFullScreenModeListener() {
            if (fullScreenModeListenerCancelable != null) {
                fullScreenModeListenerCancelable.cancel();
                fullScreenModeListenerCancelable = null;
            }
        }
    }

    /**
     * @param activity for which will be checked
     * @return show state
     */
    public static boolean isShown(Activity activity) {
        return isShown(activity.getWindow().getDecorView());
    }

    /**
     * @param view any belonging to the current window
     * @return show state
     */
    public static boolean isShown(View view) {
        int height;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            height = getOnDeviceVisibleHeight(view.getContext());
            if(height < 0) {
                height = getOnAppVisibleHeight(view);
            }
        } else {
            height = getOnAppVisibleHeight(view);
        }
        return height > 0;
    }

    /**
     * @return input method window visible height occupied on current app.
     */
    public static int getOnAppVisibleHeight(Activity activity) {
        return getOnAppVisibleHeight(activity.getWindow().getDecorView());
    }

    /**
     * @param view any belonging to the current window
     * @return input method window visible height occupied on current app.
     */
    public static int getOnAppVisibleHeight(View view) {
        ViewGroup rootView =(ViewGroup) view.getRootView();
        return rootView.getChildAt(0).getPaddingBottom();
    }

    /**
     * @return input method window visible height on device or -1 if method not available.
     *         Useful for multi window mode.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static int getOnDeviceVisibleHeight(Context ctx) {
        try {
            return callMethod(getInputMethodManager(ctx), "getInputMethodWindowVisibleHeight");
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T callMethod(Object target, String methodName)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return(T) method.invoke(target);
    }

    /**
     * @param activity on which will be tracked
     * @param listener which will receive an init notification and next only update notifications without duplicates
     * @return Cancelable for cancel subscription
     */
    public static Cancelable setFullScreenModeChangeListener(
            Activity activity, OnFullScreenModeChangedListener listener) {
        return setFullScreenModeChangeListener(activity.getWindow().getDecorView(), listener);
    }

    /**
     * @param view any belonging to the current window
     * @param listener which will receive an init notification and next only update notifications without duplicates
     * @return Cancelable for cancel subscription
     */
    public static Cancelable setFullScreenModeChangeListener(
            View view, OnFullScreenModeChangedListener listener) {
        return setFullScreenModeChangeListener(view, listener, 500);
    }

    /**
     * @param view any belonging to the current window
     * @param listener which will receive an init notification and next only update notifications without duplicates
     * @param checkInterval with which InputMethodManger will be asked for the full screen mode state.
     * @return Cancelable for cancel subscription
     */
    public static Cancelable setFullScreenModeChangeListener(
            final View view, OnFullScreenModeChangedListener listener, long checkInterval) {
        if(checkInterval < 100) {
            throw new IllegalArgumentException("Check interval must be 100 mls or more");
        }
        final InputMethodManager imm = getInputMethodManager(view.getContext());
        final FullScreenModeContext fullScreenModeContext = new FullScreenModeContext(
                imm, listener, checkInterval);
        // Init listener
        boolean fullScreenMode = imm.isFullscreenMode();
        fullScreenModeContext.value = fullScreenMode;
        listener.onFullScreenModeChanged(fullScreenMode);

        if(fullScreenMode) {
            fullScreenModeContext.checker = FullScreenModeChecker.start(fullScreenModeContext);
        }

        OnTouchListener onTouchListener = new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                checkFullScreenMode(fullScreenModeContext);
                return false;
            }
        };
        ViewGroup rootView =(ViewGroup) view.getRootView();
        TouchView.addListener(rootView, onTouchListener);

        return new FullScreenModeCancelable(rootView, onTouchListener, fullScreenModeContext);
    }

    private static class TouchView extends View {
        private final List<OnTouchListener> onTouchListenerList = new ArrayList<>();

        TouchView(Context ctx) {
            super(ctx);
            setMinimumHeight(0);
            setMinimumWidth(0);
            setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    Log.d(TAG, "onTouch() called with: v = [" + v + "], event = [" + event + "]");
                    for(OnTouchListener l : onTouchListenerList) {
                        l.onTouch(v, event);
                    }
                    return false;
                }
            });
        }

        static void addListener(ViewGroup rootView, OnTouchListener listener) {
            View subRootView = rootView.getChildAt(0);
            List<OnTouchListener> onTouchListenerList;
            if(subRootView instanceof TouchView) {
                onTouchListenerList = ((TouchView) subRootView).onTouchListenerList;
            } else {
                TouchView touchView = new TouchView(rootView.getContext());
                rootView.addView(touchView);
                onTouchListenerList = touchView.onTouchListenerList;
            }
            onTouchListenerList.remove(listener);
            onTouchListenerList.add(listener);
        }

        static void removeListener(ViewGroup rootView, OnTouchListener listener) {
            View subRootView = rootView.getChildAt(0);
            if(subRootView instanceof TouchView) {
                List<OnTouchListener> onTouchListenerList = ((TouchView) subRootView).onTouchListenerList;
                onTouchListenerList.remove(listener);
                if(onTouchListenerList.isEmpty()) {
                    rootView.removeView(subRootView);
                }
            }
        }
    }

    private static void checkFullScreenMode(FullScreenModeContext fullScreenModeContext) {
        boolean fullScreenMode = fullScreenModeContext.imm.isFullscreenMode();
        //Log.d(TAG, "checkFullScreenMode() called with: fullScreenModeContext = [" + fullScreenModeContext + "] + fullScreenMode: " + fullScreenMode);

        Log.d(TAG, "checkFullScreenMode() called with: fullScreenMode:" + fullScreenMode);

        if(fullScreenModeContext.value != fullScreenMode) {
            fullScreenModeContext.value = fullScreenMode;
            // Cancel checker before init notification of listener to not receive any more events
            if(!fullScreenMode && fullScreenModeContext.checker != null) {
                fullScreenModeContext.checker.cancel();
                fullScreenModeContext.checker = null;
            }

            fullScreenModeContext.listener.onFullScreenModeChanged(fullScreenMode);

            if(fullScreenMode && fullScreenModeContext.checker == null) {
                fullScreenModeContext.checker = FullScreenModeChecker.start(fullScreenModeContext);
            }
        }
    }

    private static class FullScreenModeCancelable implements Cancelable {
        private ViewGroup rooView;
        private OnTouchListener onTouchListener;
        private FullScreenModeContext fullScreenModeContext;

        FullScreenModeCancelable(ViewGroup rooView,
                                 OnTouchListener onTouchListener,
                                 FullScreenModeContext fullScreenModeContext) {

            this.rooView = rooView;
            this.onTouchListener = onTouchListener;
            this.fullScreenModeContext = fullScreenModeContext;
        }

        @Override
        public void cancel() {
            if(fullScreenModeContext != null) {
                TouchView.removeListener(rooView, onTouchListener);
                if(fullScreenModeContext.checker != null) {
                    fullScreenModeContext.checker.cancel();
                }
                fullScreenModeContext.executor.shutdown();
                rooView = null;
                onTouchListener = null;
                fullScreenModeContext = null;
            }
        }
    }

    private static class FullScreenModeContext {
        private final ExecutorService executor;
        private final InputMethodManager imm;
        private final OnFullScreenModeChangedListener listener;
        final long interval;
        boolean value;
        FullScreenModeChecker checker;

        private FullScreenModeContext(
                InputMethodManager imm, OnFullScreenModeChangedListener listener, long interval) {
            this.executor = Executors.newFixedThreadPool(1);
            this.imm = imm;
            this.listener = listener;
            this.interval = interval;
        }
    }

    private static class FullScreenModeChecker {
        private final Object lock = new Object();
        private final Runnable task;
        private Future taskFuture;

        private FullScreenModeChecker(final Handler handler, final FullScreenModeContext fullScreenModeContext) {
            this.task = new Runnable() {
                @Override
                public void run() {
                    Runnable notifyFullScreenModeChangedRunnable = new Runnable() {
                        @Override
                        public void run() {
                            checkFullScreenMode(fullScreenModeContext);
                        }
                    };
                    while (true) {
                        synchronized (lock) {
                            try {
                                lock.wait(fullScreenModeContext.interval);
                            } catch (InterruptedException e) {
                                /** Ignore. Task is canceled */
                            }
                        }
                        if(taskFuture.isCancelled()) break;

                        handler.post(notifyFullScreenModeChangedRunnable);
                    }
                }
            };
        }

        static FullScreenModeChecker start(FullScreenModeContext fullScreenModeContext) {
            final FullScreenModeChecker checker =
                    new FullScreenModeChecker(new Handler(), fullScreenModeContext);
            checker.taskFuture = fullScreenModeContext.executor.submit(checker.task);
            return checker;
        }

        void cancel() {
            taskFuture.cancel(true);
            synchronized (lock) {
                lock.notify();
            }
        }
    }

    public static InputMethodManager getInputMethodManager(Context ctx) {
        return (InputMethodManager) ctx.getApplicationContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    interface Cancelable {
        void cancel();
    }

    interface OnShowChangedListener {
        void onShowChanged(boolean shown, boolean fullScreenMode, int onAppHeight);
    }

    interface OnFullScreenModeChangedListener {
        void onFullScreenModeChanged(boolean fullScreen);
    }
}
