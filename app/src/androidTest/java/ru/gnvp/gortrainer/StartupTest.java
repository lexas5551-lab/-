package ru.gnvp.gortrainer;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StartupTest {
    private WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView found = findWebView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private String evaluate(Instrumentation instrumentation, WebView view, String script) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> view.evaluateJavascript(script, result -> {
            value.set(result);
            done.countDown();
        }));
        assertTrue("JavaScript callback timed out", done.await(10, TimeUnit.SECONDS));
        return value.get();
    }

    @Test
    public void homeAndPracticeWorkWithoutStorage() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = instrumentation.startActivitySync(intent);
        try {
            AtomicReference<WebView> view = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> view.set(findWebView(activity.getWindow().getDecorView())));
            assertNotNull("WebView did not start", view.get());
            boolean ready = false;
            for (int i = 0; i < 60; i++) {
                if ("true".equals(evaluate(instrumentation, view.get(),
                        "window.__trainerReady === true && document.getElementById('app').innerText.indexOf('Выберите режим') >= 0"))) {
                    ready = true;
                    break;
                }
                Thread.sleep(500);
            }
            assertTrue("Home screen was blank or failed to load", ready);
            assertEquals("145", evaluate(instrumentation, view.get(), "QUESTIONS.length"));
            assertEquals("26", evaluate(instrumentation, view.get(), "QUESTIONS.filter(q=>q.image).length"));
            assertEquals("true", evaluate(instrumentation, view.get(),
                "(function(){openPractice();startPractice();toggleOption('B');submitCurrent();movePractice(1);movePractice(-1);return state.currentSelected.has('B') && document.getElementById('feedback').innerText.includes('Ответ неверный')})()"));
            evaluate(instrumentation, view.get(), "location.reload()");
            boolean reloaded = false;
            for (int i = 0; i < 60; i++) {
                if ("true".equals(evaluate(instrumentation, view.get(), "window.__trainerReady === true && document.querySelector('.mode-card') !== null"))) { reloaded = true; break; }
                Thread.sleep(500);
            }
            assertTrue("Reload failed", reloaded);
            assertEquals("true", evaluate(instrumentation, view.get(),
                "(function(){continuePractice();return state.currentSelected.has('B') && document.getElementById('feedback').innerText.includes('Ответ неверный')})()"));
            assertEquals("true", evaluate(instrumentation, view.get(),
                "(function(){var prev=window.confirm;window.confirm=function(){return true};resetPracticeAnswers();window.confirm=prev;return Object.keys(loadPractice().answers).length===0 && stats().practiceWrong===1})()"));
            evaluate(instrumentation, view.get(), "startPractice(['GNVP-70']);showCorrect()");
            boolean imageReady = false;
            for (int i = 0; i < 60; i++) {
                if ("true".equals(evaluate(instrumentation, view.get(), "(function(){var img=document.querySelector('#visualSlot img');return !!img && img.complete && img.naturalWidth>0})()"))) { imageReady=true; break; }
                Thread.sleep(500);
            }
            assertTrue("Ready illustration did not load", imageReady);
            assertEquals("true", evaluate(instrumentation, view.get(),
                "(function(){enlargeIllustration('GNVP-70');setIllustrationZoom(200);var dialog=document.getElementById('image-dialog');var ok=dialog.open && dialog.dataset.zoom==='200';dialog.close();return ok})()"));
            assertEquals("true", evaluate(instrumentation, view.get(),
                "(function(){goHome();return !Array.from(document.querySelectorAll('.mode-card')).some(b=>b.innerText.includes('Иллюстрации'))})()"));

            assertEquals("true", evaluate(instrumentation, view.get(),
                "(function(){localStorage.setItem('trainerStatsV2','broken');goHome();return document.getElementById('app').innerText.indexOf('Выберите режим')>=0})()"));
            assertEquals("true", evaluate(instrumentation, view.get(),
                "(function(){Object.defineProperty(window,'localStorage',{configurable:true,get:function(){throw new Error('Storage unavailable')}});goHome();openPractice();startPractice();state.currentSelected=new Set(state.queue[0].correct);submitCurrent();return document.getElementById('feedback').innerText.indexOf('Верно')>=0})()"));
            assertEquals("true", evaluate(instrumentation, view.get(),
                "(function(){clearStats();goHome();return document.getElementById('app').innerText.indexOf('Выберите режим')>=0})()"));
            instrumentation.waitForIdleSync();
            Thread.sleep(1000);
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            File output = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "startup.png");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }
            try (android.os.ParcelFileDescriptor capture = instrumentation.getUiAutomation().executeShellCommand("screencap -p /sdcard/Download/startup.png");
                 java.io.FileInputStream captureOutput = new java.io.FileInputStream(capture.getFileDescriptor())) {
                while (captureOutput.read() != -1) { }
            }
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}
