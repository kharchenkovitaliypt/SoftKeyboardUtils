package com.kharchenkovitaliypt.softkeyboard;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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

        return new ShowChangedCancelable(view, globalLayoutListener);
    }

    private static class ShowChangedCancelable implements Cancelable {
        private View view;
        private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;

        ShowChangedCancelable(View view,
                              ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener) {

            this.view = view;
            this.globalLayoutListener = globalLayoutListener;
        }

        @Override
        public void cancel() {
            if(view != null) {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
                view = null;
                globalLayoutListener = null;
            }
        }
    }

    private static class ShowChangedContext {
        final OnShowChangedListener listener;
        final InputMethodManager imm;
        Boolean shown;
        int onAppHeight;

        ShowChangedContext(InputMethodManager imm, OnShowChangedListener listener) {
            this.listener = listener;
            this.imm = imm;
        }

        void notifyListener(final View view) {
            boolean shown = isShown(view);
            int onAppHeight = getOnAppVisibleHeight(view);

            if(this.shown == null || this.shown != shown
                    || this.onAppHeight != onAppHeight) {
                this.shown = shown;
                this.onAppHeight = onAppHeight;
                listener.onShowChanged(shown, onAppHeight);

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

    public static InputMethodManager getInputMethodManager(Context ctx) {
        return (InputMethodManager) ctx.getApplicationContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    interface Cancelable {
        void cancel();
    }

    interface OnShowChangedListener {
        void onShowChanged(boolean shown, int onAppHeight);
    }
}
