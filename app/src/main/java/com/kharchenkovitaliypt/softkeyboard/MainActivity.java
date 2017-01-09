package com.kharchenkovitaliypt.softkeyboard;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    SoftKeyboard.Cancelable cancelable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int rootBottomPadding = ((ViewGroup)getWindow().getDecorView()).getChildAt(0).getPaddingBottom();
                Log.d(TAG, "onGlobalLayout() rootView height: " + findViewById(R.id.scroll).getHeight() + ", root padding: " + rootBottomPadding);
            }
        });

        cancelable = SoftKeyboard.setFullScreenModeChangeListener(getWindow().getDecorView(), new SoftKeyboard.OnFullScreenModeChangedListener() {
            @Override
            public void onFullScreenModeChanged(boolean fullScreen) {
                Toast.makeText(getApplicationContext(),
                        "onFullScreenModeChanged() called with: fullScreen = [" + fullScreen + "]",
                        Toast.LENGTH_LONG)
                .show();
            }
        });

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelable.cancel();
            }
        });
    }
}
