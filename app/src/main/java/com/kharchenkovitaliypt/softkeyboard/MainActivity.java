package com.kharchenkovitaliypt.softkeyboard;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivityTag";
    SoftKeyboard.Cancelable cancelableFullScreenMode;
    SoftKeyboard.Cancelable cancelableOnShowChanged;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView fullScreenModeStateView = (TextView) findViewById(R.id.full_screen_mode_state);
        final TextView showChangedStateView = (TextView) findViewById(R.id.show_changed_state);

//        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
//            @Override
//            public void onGlobalLayout() {
//                int rootBottomPadding = ((ViewGroup)getWindow().getDecorView()).getChildAt(0).getPaddingBottom();
//                Log.d(TAG, "onGlobalLayout() rootView height: " + findViewById(R.id.scroll).getHeight() + ", root padding: " + rootBottomPadding);
//            }
//        });
//        cancelableFullScreenMode = SoftKeyboard.setFullScreenModeChangeListener(fullScreenModeStateView, new SoftKeyboard.OnFullScreenModeChangedListener() {
//            @Override
//            public void onFullScreenModeChanged(boolean fullScreen) {
//                fullScreenModeStateView.setText("onFullScreenModeChanged() fullScreen:" + fullScreen);
//            }
//        });
        cancelableOnShowChanged = SoftKeyboard.setOnShowChangedListener(this, new SoftKeyboard.OnShowChangedListener() {
            @Override
            public void onShowChanged(boolean shown, int onAppHeight) {
                String text = "onShowChanged() shown:" + shown + ", onAppHeight:" + onAppHeight;
                showChangedStateView.setText(text);
                Log.d(TAG, text);
            }
        });

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(cancelableFullScreenMode != null)cancelableFullScreenMode.cancel();
                if(cancelableOnShowChanged != null) cancelableOnShowChanged.cancel();
            }
        });
    }
}
