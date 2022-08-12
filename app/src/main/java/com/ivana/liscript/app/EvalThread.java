package com.ivana.liscript.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.Editable;
import android.widget.EditText;

import androidx.preference.PreferenceManager;

import java.util.concurrent.atomic.AtomicBoolean;

public class EvalThread extends Thread implements Eval.InOutable {

    private final Object lv;
    private final boolean iter;
    public MainActivity activity;

    private final int resultTextMaxLength;

    public final Object inputLock = new Object();
    public volatile String inputString = "";

    public volatile Boolean interruptedFlag = false;

    EvalThread(Object lv, MainActivity activity) {
        this.lv = lv;
        this.activity = activity;

        App app = App.getInstance();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(app.getApplicationContext());
        this.iter = preferences.getBoolean ("evaluator_mode_preference", false);
        this.resultTextMaxLength = Integer.parseInt(preferences.getString("max_result_text_length_preference",
                activity.getString(R.string.max_result_text_length_preference_default_value)));

        app.thread = this;
    }

    @Override
    public void out(boolean ln, String s) {
        if (s == null) return;

        AtomicBoolean needToLock = new AtomicBoolean(true);

        final Activity a = this.activity;
        if (a != null && !a.isDestroyed()) a.runOnUiThread(() -> {
            EditText resultText = a.findViewById(R.id.resultText);
            resultText.append(s);
            if (ln) resultText.append("\n");

            // limit resultText to max length
            if (resultText.length() > resultTextMaxLength) {
                Editable editable = resultText.getEditableText();
                if (editable != null) {
                    editable.delete(0, resultText.length() - resultTextMaxLength);
                }
            }
            // move cursor to bottom
            resultText.setSelection(resultText.getText().length());

            synchronized (inputLock) {
                inputLock.notify();
                needToLock.set(false);
            }
        });

        if (needToLock.get()) {
            synchronized (inputLock) {
                try {
                    inputLock.wait();
                } catch (InterruptedException e) {
                    //
                }
            }
        }
    }

    @Override
    public void outFromRead(String s) { out(false, s); }

    @Override
    public String in() {
        if (interruptedFlag) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("вычисление прервано");
        }

        inputString = "";
        final MainActivity a = this.activity;
        if (a != null && !a.isDestroyed()) a.runOnUiThread(a::showInputView);

        synchronized (inputLock) {
            try {
                inputLock.wait();
            } catch (InterruptedException e) {
                //
            }
        }
        return inputString;
    }

    @Override
    public void run() {
        App app = App.getInstance();
        // long time = System.nanoTime();
        try {
            String answer;
            if (this.iter) {
                answer = Eval.evalIter(this, app.env, lv).toString();
            } else {
                answer = Eval.eval(-1, true, this, app.env, lv).toString();
            }
            out(true, answer);
        } catch (Throwable e) {
            String exString = e.getLocalizedMessage();
            if (e instanceof StackOverflowError) exString = "Переполнение стека";
            if (exString == null) exString = e.toString();
            Thread.currentThread().interrupt();
            out(true, exString);
        }
        // time = System.nanoTime() - time;
        // app.t = String.format("%.5f", time/1.E9);

        app.thread = null;

        final MainActivity a = this.activity;
        if (a != null && !a.isDestroyed()) a.runOnUiThread(() -> {
            a.showStateReady();
            a.closeInputView();
        });
    }

}
