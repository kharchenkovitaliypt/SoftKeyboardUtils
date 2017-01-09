package com.kharchenkovitaliypt.softkeyboard;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    public static boolean isShown(View view) {
        int height;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            height = getOnDeviceVisibleHeight(view.getContext());
        } else {
            height = getOnAppVisibleHeight(view);
        }
        return height > 0;
    }

    /**
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

    public static Cancelable setFullScreenModeChangeListener(
            View view, OnFullScreenModeChangedListener listener) {
        return setFullScreenModeChangeListener(view, listener, 500);
    }

    public static Cancelable setFullScreenModeChangeListener(
            final View view, OnFullScreenModeChangedListener listener, long checkInterval) {
        if(checkInterval < 100) {
            throw new IllegalArgumentException("Check interval must be 100 mls or more");
        }
        final InputMethodManager imm = getInputMethodManager(view.getContext());
        final FullScreenModeContext fullScreenModeContext = new FullScreenModeContext(
                imm, listener, checkInterval);
        // Init listener
        final boolean fullScreenMode = imm.isFullscreenMode();
        fullScreenModeContext.value = fullScreenMode;
        listener.onFullScreenModeChanged(fullScreenMode);

        if(fullScreenMode) {
            fullScreenModeContext.checker = FullScreenModeChecker.start(fullScreenModeContext);
        }

        final ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener =
                new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                checkFullScreenMode(fullScreenModeContext);
            }
        };
        view.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);

        return new Cancelable() {
            @Override
            public void cancel() {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
                if(fullScreenModeContext.checker != null) {
                    fullScreenModeContext.checker.cancel();
                }
                fullScreenModeContext.executor.shutdown();
            }
        };
    }

    private static void checkFullScreenMode(FullScreenModeContext fullScreenModeContext) {
        boolean fullScreenMode = fullScreenModeContext.imm.isFullscreenMode();
        //Log.d(TAG, "checkFullScreenMode() called with: fullScreenModeContext = [" + fullScreenModeContext + "] + fullScreenMode: " + fullScreenMode);

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

    private static class FullScreenModeContext {
        final ExecutorService executor;
        final InputMethodManager imm;
        final OnFullScreenModeChangedListener listener;
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

    @SuppressWarnings("unchecked")
    private static <T> T callMethod(Object target, String methodName)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return(T) method.invoke(target);
    }

    public static InputMethodManager getInputMethodManager(Context ctx) {
        return (InputMethodManager) ctx.getApplicationContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    interface Cancelable {
        void cancel();
    }

    interface OnShowChangedListener {
        void onShowChanged(boolean visible, boolean fullScreen, int height);
    }

    interface OnFullScreenModeChangedListener {
        void onFullScreenModeChanged(boolean fullScreen);
    }
}
