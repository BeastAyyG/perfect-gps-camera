package com.perfectgps.camera;

import android.app.Activity;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;

import com.perfectgps.camera.ui.MainActivity;

public class MainActivityInstrumentedTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public MainActivityInstrumentedTest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setActivityInitialTouchMode(true);
    }

    public void testActivityLaunchesWithCaptureAndPrivacyControls() {
        Activity activity = getActivity();
        assertNotNull(activity);
        assertNotNull(activity.findViewById(R.id.preview));
        assertNotNull(activity.findViewById(R.id.captureButton));
        assertNotNull(activity.findViewById(R.id.flashButton));
        assertNotNull(activity.findViewById(R.id.gridButton));
        assertNotNull(activity.findViewById(R.id.lensButton));
        View permissionPanel = activity.findViewById(R.id.permissionPanel);
        assertNotNull(permissionPanel);
    }

    public void testCameraErrorDisablesCaptureControls() {
        MainActivity activity = (MainActivity) getActivity();
        activity.runOnUiThread(() -> activity.onCameraError("test"));
        SystemClock.sleep(250);
        getInstrumentation().waitForIdleSync();
        assertFalse(activity.findViewById(R.id.captureButton).isEnabled());
        assertFalse(activity.findViewById(R.id.flashButton).isEnabled());
        assertFalse(activity.findViewById(R.id.lensButton).isEnabled());
    }
}
