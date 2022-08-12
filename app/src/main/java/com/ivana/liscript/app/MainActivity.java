package com.ivana.liscript.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.InputType;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Log.i("LIFECYCLE", "onCreate");
        setContentView(R.layout.main_activity);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // setTitle(R.string.app_name);
        // View v = MainActivity.this.getWindow().getDecorView().getRootView();
        EditText codeText = findViewById(R.id.codeText);
        EditText resultText = findViewById(R.id.resultText);
        codeText.setHorizontallyScrolling(true);
        resultText.setShowSoftInputOnFocus(false);

        findViewById(R.id.inputView).setVisibility(View.INVISIBLE); // GONE);
        final EditText inputText = findViewById(R.id.inputText);
        final ImageButton button = findViewById(R.id.inputButton);
        button.setOnClickListener(v -> {
            App app = App.getInstance();
            if (app.thread != null) {
                app.thread.inputString = inputText.getText().toString();
                synchronized (app.thread.inputLock) {
                    app.thread.inputLock.notify();
                }
            }
            closeInputView();
        });

        App app = App.getInstance();

        if (app.thread != null) app.thread.activity = this;

        if (app.env.map.isEmpty()) readEvalResource(R.raw.standard_library);

        // just for preventing delete on 'remove unused resources' command :)
        int i = R.raw.demo_1_liscript_basics
                + R.raw.demo_2_examples
                + R.raw.demo_3_lissajous
                + R.raw.demo_4_ashberry
                + R.raw.demo_5_bouncing_balls;
    }

//    @Override
//    protected void onStart() {
//        super.onStart();
//        Log.i("LIFECYCLE", "onStart");
//    }

    @Override
    protected void onResume() {
        super.onResume();
        // Log.i("LIFECYCLE","onResume");

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(App.getInstance().getApplicationContext());
        int fontSize = Integer.parseInt(preferences.getString("font_size_preference",
                getString(R.string.font_size_preference_default_value)));
        ((EditText) findViewById(R.id.codeText)).setTextSize(fontSize);
        ((EditText) findViewById(R.id.resultText)).setTextSize(fontSize);
        ((EditText) findViewById(R.id.inputText)).setTextSize(fontSize);
    }

//    @Override
//    protected void onStop() {
//        super.onStop();
//        Log.i("LIFECYCLE", "onStop");
//    }

//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        Log.i("LIFECYCLE", "onDestroy");
//    }

    /////////////////////// OPTIONS MENU

    public Menu mOptionsMenu;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        mOptionsMenu = menu;
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
//        https://developer.android.com/reference/android/view/MenuItem
        menu.findItem(R.id.menu_eval_code).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        // menu.findItem(R.id.menu_stop_close).setTitle(serviceIsRunning ? R.string.main_menu_stop_quit : R.string.main_menu_quit);
        menu.findItem(R.id.menu_open).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.findItem(R.id.menu_save).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.findItem(R.id.menu_save_as).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.findItem(R.id.menu_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.findItem(R.id.menu_open_settings).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        return super.onPrepareOptionsMenu(menu);
    }

//    public static int dpToPx(int dp) {
//        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
//    }

    final String fileNamePrefix = "saved_file_";

    void saveCodeToFile(String fileName) {
        EditText codeText = findViewById(R.id.codeText);
        String code = codeText.getText().toString();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(App.getInstance().getApplicationContext());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(fileNamePrefix + fileName, code);
        editor.apply();
        setTitle(fileName);
    }

    void askFileNameSaveCode() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_enter_file_name);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        // FIXME add horizontal margins
        // int margin = 100; //dpToPx(20);
        //  LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        //  lp.setMargins(margin, 0, margin, 0);
        //  input.setLayoutParams(lp);
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String fileName = input.getText().toString();
            saveCodeToFile(fileName);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    String getCurrentFileName() {
        CharSequence fileName = getTitle();
        return null == fileName ? "" : fileName.toString();
    }

    void readEvalCode(String code) {
        Object lv = Read.string2LispVal(code);
        new EvalThread(lv, this).start();

        Menu menu = mOptionsMenu;
        if (menu == null) return;
        MenuItem item = menu.findItem(R.id.menu_eval_code);
        if (item == null) return;
        item.setIcon(android.R.drawable.ic_media_pause);
        item.setTitle(R.string.main_menu_interrupt);
    }

    String getResourceText(int resource) {
        Resources res = getResources();
        InputStream inputStream = res.openRawResource(resource);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        byte[] buf = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
            outputStream.close();
            inputStream.close();
            return outputStream.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    void readEvalResource(int resource) {
        String code = getResourceText(resource);
        if (code == null) return;
        readEvalCode(code);
    }

    void showStateReady() {
        if (mOptionsMenu == null) return;
        MenuItem item = mOptionsMenu.findItem(R.id.menu_eval_code);
        if (item == null) return;
        item.setIcon(android.R.drawable.ic_media_play);
        item.setTitle(R.string.main_menu_eval_code);
    }

    void showInputView() {
        findViewById(R.id.inputView).setVisibility(View.VISIBLE);
        final EditText inputText = findViewById(R.id.inputText);
        inputText.requestFocus();
    }

    void closeInputView() {
        findViewById(R.id.inputView).setVisibility(View.INVISIBLE); // GONE);
        final EditText inputText = findViewById(R.id.inputText);
        inputText.setText(null);
        inputText.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(inputText.getWindowToken(), 0);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (R.id.menu_eval_code == itemId) {
            App app = App.getInstance();
            if (null == app.thread) {
                EditText codeText = findViewById(R.id.codeText);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(codeText.getWindowToken(), 0);
                readEvalCode(codeText.getText().toString());
            } else { // if (null != app.thread) { // (!app.thread.isInterrupted()) {
                app.thread.interruptedFlag = true;
                app.thread.interrupt();
            }

        } else if (R.id.menu_open == itemId) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(App.getInstance().getApplicationContext());
            Map<String, ?> map = preferences.getAll();
            ArrayList<String> files = new ArrayList<>();
            for (Map.Entry<String, ?> entry : map.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(fileNamePrefix)) files.add(key.replaceFirst(fileNamePrefix, ""));
            }
            Collections.sort(files);
            String [] items = files.toArray(new String[0]);

            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(items.length == 0 ? R.string.dialog_title_no_saved_files : R.string.dialog_title_select_file);
            builder.setItems(items, (dialog, which) -> {
                String code = preferences.getString(fileNamePrefix + items[which], "");
                EditText codeText = findViewById(R.id.codeText);
                codeText.setText(code);
                setTitle(items[which]);
            });
            builder.show();

        } else if (R.id.menu_save == itemId) {
            String fileName = getCurrentFileName();
            if (fileName.isEmpty())
                askFileNameSaveCode();
            else
                saveCodeToFile(fileName);

        } else if (R.id.menu_save_as == itemId) {
            askFileNameSaveCode();

        } else if (R.id.menu_delete == itemId) {
            String fileName = getCurrentFileName();
            // if (!fileName.isEmpty()) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(App.getInstance().getApplicationContext());
            SharedPreferences.Editor editor = preferences.edit();
            editor.remove(fileNamePrefix + fileName);
            editor.apply();
            setTitle(null);

        } else if (R.id.menu_demo == itemId) {
            HashMap<String, Integer> map = new HashMap<>();
            Field[] fields = R.raw.class.getFields();
            for(Field f : fields) {
                String name = f.getName();
                try {
                    @RawRes int id = f.getInt(f);
                    if (name.startsWith("demo_")) map.put(name.replaceFirst("demo_", "").replace("_", " "), id);
                } catch (Throwable e) {
                    // e.printStackTrace();
                }
            }
            String [] items = map.keySet().toArray(new String[0]);
            Arrays.sort(items);

            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            //builder.setTitle(items.length == 0 ? R.string.dialog_title_no_saved_files : R.string.dialog_title_select_file);
            builder.setItems(items, (dialog, which) -> {
                Integer id = map.get(items[which]);
                if (id == null) return;
                String code = getResourceText(id);
                if (code == null) return;
                EditText codeText = findViewById(R.id.codeText);
                codeText.setText(code);
                setTitle(items[which]);
            });
            builder.show();

        } else if (R.id.menu_open_settings == itemId) {
            Intent settingsActivity = new Intent(getBaseContext(), SettingsActivity.class);
            startActivity(settingsActivity);
        }
//        supportInvalidateOptionsMenu(); ???
        return super.onOptionsItemSelected(item);
    }
}
