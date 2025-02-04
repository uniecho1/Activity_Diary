/*
 * ActivityDiary
 *
 * Copyright (C) 2017-2018 Raphael Mack http://www.raphael-mack.de
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.rampro.activitydiary.ui.main;

import static android.os.Environment.getExternalStorageDirectory;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import androidx.lifecycle.ViewModelProviders;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import androidx.exifinterface.media.ExifInterface;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.viewpager.widget.ViewPager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import de.rampro.activitydiary.ActivityDiaryApplication;
import de.rampro.activitydiary.BuildConfig;
import de.rampro.activitydiary.R;
import de.rampro.activitydiary.db.ActivityDiaryContract;
import de.rampro.activitydiary.helpers.ActivityHelper;
import de.rampro.activitydiary.helpers.DateHelper;
import de.rampro.activitydiary.helpers.GraphicsHelper;
import de.rampro.activitydiary.helpers.TimeSpanFormatter;
import de.rampro.activitydiary.model.DetailViewModel;
import de.rampro.activitydiary.model.DiaryActivity;
import de.rampro.activitydiary.ui.generic.BaseActivity;
import de.rampro.activitydiary.ui.generic.EditActivity;
import de.rampro.activitydiary.ui.history.HistoryDetailActivity;
import de.rampro.activitydiary.ui.settings.SettingsActivity;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;

import static de.rampro.activitydiary.model.conditions.Condition.mOpenHelper;
import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;

/*
 * MainActivity to show most of the UI, based on switching the fragments
 *
 * */
public class MainActivity extends BaseActivity implements
        SelectRecyclerViewAdapter.SelectListener,
        ActivityHelper.DataChangedListener,
        NoteEditDialog.NoteEditDialogListener,
        View.OnLongClickListener,
        SearchView.OnQueryTextListener,
        SearchView.OnCloseListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 4711;

    private static final int QUERY_CURRENT_ACTIVITY_STATS = 1;
    private static final int QUERY_CURRENT_ACTIVITY_TOTAL = 2;

    private DetailViewModel viewModel;

    private String mCurrentPhotoPath;

    private RecyclerView selectRecyclerView;
    private StaggeredGridLayoutManager selectorLayoutManager;
    private SelectRecyclerViewAdapter selectAdapter;

    private String filter = "";
    private int searchRowCount, normalRowCount;
    private FloatingActionButton fabNoteEdit;
    private FloatingActionButton fabAttachPicture;

    private FloatingActionButton fabVocalHelper;
    private SearchView searchView;
    private MenuItem searchMenuItem;
    private ViewPager viewPager;
    private TabLayout tabLayout;
    private View headerView;
    private SpeechRecognizer mIat;// 语音听写对象
    private RecognizerDialog mIatDialog;// 语音听写UI

    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();

    private SharedPreferences mSharedPreferences;//缓存

    private String mEngineType = SpeechConstant.TYPE_CLOUD;// 引擎类型
    private String language = "zh_cn";//识别语言

//    private TextView tvResult = findViewById(R.id.tv_);//识别结果
    private String resultType = "json";//结果内容数据格式

    private void setSearchMode(boolean searchMode){
        if(searchMode){
            headerView.setVisibility(View.GONE);
            fabNoteEdit.hide();
            fabAttachPicture.hide();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            ((StaggeredGridLayoutManager)selectRecyclerView.getLayoutManager()).setSpanCount(searchRowCount);

        }else{
            ((StaggeredGridLayoutManager)selectRecyclerView.getLayoutManager()).setSpanCount(normalRowCount);

            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
            headerView.setVisibility(View.VISIBLE);
            fabNoteEdit.show();
            fabAttachPicture.show();
        }

    }

    private MainAsyncQueryHandler mQHandler = new MainAsyncQueryHandler(ActivityDiaryApplication.getAppContext().getContentResolver());

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("currentPhotoPath", mCurrentPhotoPath);

        // call superclass to save any view hierarchy
        super.onSaveInstanceState(outState);
    }
    private InitListener mInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS){
                showMsg("初始化失败，错误码：" + code + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
            }
        }
    };
    private String[] format(String[] params){
        List<String> res = new ArrayList<>();
        for(String param: params){
            param = param.trim();
            if(param.length() > 0 && param.charAt(param.length()-1) == '.')
                param = param.substring(0, param.length()-1);
            if(!param.isEmpty()) {
                param = param.toLowerCase();
                param = Character.toUpperCase(param.charAt(0)) + param.substring(1);
                res.add(param);
            }
        }
        return res.toArray(new String[0]);
    }

    private String recoverToSentence(String[] params){
        StringBuilder res = new StringBuilder();
        for(int i = 1; i < params.length; i++){
            String tmp;
            if(i == 1)
                tmp = Character.toUpperCase(params[i].charAt(0))+params[i].substring(1);
            else
                tmp = params[i].toLowerCase();
            res.append(tmp);
            if(i != params.length - 1)
                res.append(" ");
            else
                res.append(".");
        }
        return res.toString();
    }

    private boolean process(String res){
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        String[] params = format(res.split(" "));
        if(params[0].equals("Start")){
            String activity = params[1];
            Cursor tmp = db.query("activity",new String [] {"name", "_id", "color"},"name=?",new String [] {activity},null,null,null);
            if (tmp == null)
                showMsg("Wrong: cursor points to null.");
            else if (!tmp.moveToFirst()){
                showMsg("There's no such activity.");
                return false;
            }
            else{
                String activity_name = tmp.getString(tmp.getColumnIndexOrThrow("name"));
                int activity_id = tmp.getInt(tmp.getColumnIndexOrThrow("_id"));
                int activity_color = tmp.getInt(tmp.getColumnIndexOrThrow("color"));
                DiaryActivity newAct = new DiaryActivity(activity_id,activity_name,activity_color);
                if(!newAct.equals(ActivityHelper.helper.getCurrentActivity())) {

                    ActivityHelper.helper.setCurrentActivity(newAct);

                    searchView.setQuery("", false);
                    searchView.setIconified(true);


                    SpannableStringBuilder snackbarText = new SpannableStringBuilder();
                    snackbarText.append(newAct.getName());
                    int end = snackbarText.length();
                    snackbarText.setSpan(new ForegroundColorSpan(newAct.getColor()), 0, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                    snackbarText.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                    snackbarText.setSpan(new RelativeSizeSpan((float) 1.4152), 0, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);

                    Snackbar undoSnackBar = Snackbar.make(findViewById(R.id.main_layout),
                            snackbarText, Snackbar.LENGTH_LONG);
                    undoSnackBar.setAction(R.string.action_undo, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.v(TAG, "UNDO Activity Selection");
                            ActivityHelper.helper.undoLastActivitySelection();
                        }
                    });
                    undoSnackBar.show();
                }else{
                    /* clicked the currently active activity in the list, so let's terminate it due to #176 */
//                ActivityHelper.helper.setCurrentActivity(null);
                    showMsg("It's already running now.");
                    return false;
                }
            }
        }
        else if(params[0].equals("Stop")){
            if(params[1].equals("Current")){
                if(ActivityHelper.helper.getCurrentActivity() != null)
                    ActivityHelper.helper.setCurrentActivity(null);
                else
                    showMsg("No current running activity.");
            }
        }
        else if(params[0].equals("Create")){
            String activity = params[1];
            Cursor tmp = db.query("activity",new String [] {"_deleted", "name", "_id", "color"},"name=?",new String [] {activity},null,null,null);
            if (tmp == null)
                showMsg("Wrong: cursor points to null.");
            else if (tmp.moveToFirst()){
                int deleted = tmp.getInt(tmp.getColumnIndexOrThrow("_deleted"));
                if(deleted == 0)
                    showMsg("There's such an activity.");
                else {
                    ContentValues values = new ContentValues();
                    values.put(ActivityDiaryContract.DiaryActivity._DELETED, 0);

                    mQHandler.startUpdate(0,
                            null,
                            ContentUris.withAppendedId(ActivityDiaryContract.DiaryActivity.CONTENT_URI,
                                    tmp.getLong(tmp.getColumnIndex(ActivityDiaryContract.DiaryActivity._ID))),
                            values,
                            ActivityDiaryContract.DiaryActivity._ID + "=?",
                            new String[]{tmp.getString(tmp.getColumnIndex(ActivityDiaryContract.DiaryActivity._ID))}
                    );
                }
            }
            else{
                int mActivityColor = GraphicsHelper.prepareColorForNextActivity();
                ActivityHelper.helper.insertActivity(new DiaryActivity(-1, activity, mActivityColor));
            }
        }
        else if(params[0].equals("Delete")){
            String activity = params[1];
            Cursor tmp = db.query("activity",new String [] {"name", "_id", "color"},"name=?",new String [] {activity},null,null,null);
            if (tmp == null)
                showMsg("Wrong: cursor points to null.");
            else if (!tmp.moveToFirst()){
                showMsg("There's no such activity.");
                return false;
            }
            else{
                String activity_name = tmp.getString(tmp.getColumnIndexOrThrow("name"));
                int activity_id = tmp.getInt(tmp.getColumnIndexOrThrow("_id"));
                int activity_color = tmp.getInt(tmp.getColumnIndexOrThrow("color"));
                DiaryActivity tarAct = new DiaryActivity(activity_id,activity_name,activity_color);
//                if(!tarAct.equals(ActivityHelper.helper.getCurrentActivity())) {
                ActivityHelper.helper.deleteActivity(tarAct);
//                }else{
////                    其实好像可以删除正在 running 的 activity
//                   showMsg("You can't delete current running activity.");
//                }
            }
        }
        else if(params[0].equals("Note")){
            if(ActivityHelper.helper.getCurrentActivity() != null){
                String content = recoverToSentence(params);
                NoteEditDialog dialog = new NoteEditDialog();
                dialog.setText(viewModel.mNote.getValue() + content);
                dialog.show(getSupportFragmentManager(), "NoteEditDialogFragment");
            } else
                showMsg("No current running activity!");

        }
        else{
            showMsg("Undefined Option");
            return false;
        }
        return true;
    }

    private void printResult(RecognizerResult results) {
        String text = JsonParser.parseIatResult(results.getResultString());

        String sn = null;
        // 读取json结果中的sn字段
        try {
            JSONObject resultJson = new JSONObject(results.getResultString());
            sn = resultJson.optString("sn");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mIatResults.put(sn, text);

        StringBuffer resultBuffer = new StringBuffer();
        for (String key : mIatResults.keySet()) {
            resultBuffer.append(mIatResults.get(key));
        }

//        tvResult.setText(resultBuffer.toString());//听写结果显示
//        showMsg(resultBuffer.toString());


        String res = resultBuffer.toString();
        if(!process(res)){
            final View DialogView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.edit_iat_res,null);
            final EditText editText= (EditText) DialogView.findViewById(R.id.iat_res);
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("IatResult").setView(DialogView);
            editText.setText(res);
            editText.setSelection(editText.getText().length());
            builder.setPositiveButton("Ok", new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog,int which){
                    String final_res = editText.getText().toString();
                    process(final_res);
                }
            });

            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog,int which){
                    dialog.cancel();
                }
            });

            builder.create().show();
        }
    }
    private int cnt = 0;
    private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
        public void onResult(RecognizerResult results,boolean isLast) {
            if(!isLast){
                cnt++;
                printResult(results);
            }
        }
        public void onError(SpeechError error){
            showMsg(error.getPlainDescription(true));
        }
    };
    private void showMsg(String msg){
        Toast.makeText(MainActivity.this,msg,Toast.LENGTH_SHORT).show();
    }
    public void setParam() {
        mIat.setParameter(SpeechConstant.PARAMS, null);
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        mIat.setParameter(SpeechConstant.RESULT_TYPE, resultType);
        mIat.setParameter(SpeechConstant.LANGUAGE,"en_us");
        mIat.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", "4000"));
        mIat.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("iat_vadeos_preference", "1000"));
        mIat.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("iat_punc_preference", "1"));
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, getExternalFilesDir("msc").getAbsolutePath() + "/iat.wav");
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = ViewModelProviders.of(this).get(DetailViewModel.class);

        // recovering the instance state
        if (savedInstanceState != null) {
            mCurrentPhotoPath = savedInstanceState.getString("currentPhotoPath");
        }

        // 语音识别相关初始化
        mIat = SpeechRecognizer.createRecognizer(MainActivity.this, mInitListener);
        mIatDialog = new RecognizerDialog(MainActivity.this,mInitListener);
        mSharedPreferences = getSharedPreferences("ASR", Activity.MODE_PRIVATE);

        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View contentView = inflater.inflate(R.layout.activity_main_content, null, false);
        setContent(contentView);

        headerView = findViewById(R.id.header_area);
        tabLayout = findViewById(R.id.tablayout);
        viewPager = findViewById(R.id.viewpager);
        setupViewPager(viewPager);
        tabLayout.setupWithViewPager(viewPager);

        selectRecyclerView = findViewById(R.id.select_recycler);

        View selector = findViewById(R.id.activity_background);
        selector.setOnLongClickListener(this);
        selector.setOnClickListener(v -> {

            if(PreferenceManager
                    .getDefaultSharedPreferences(ActivityDiaryApplication.getAppContext())
                    .getBoolean(SettingsActivity.KEY_PREF_DISABLE_CURRENT, true)){
                ActivityHelper.helper.setCurrentActivity(null);
            }else{
                Intent i = new Intent(MainActivity.this, HistoryDetailActivity.class);
                // no diaryEntryID will edit the last one
                startActivity(i);
            }
        });

        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.listPreferredItemHeightSmall, value, true);

        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        normalRowCount = (int)Math.floor((metrics.heightPixels / value.getDimension(metrics) - 2) / 5);
        searchRowCount = normalRowCount - 2;
        if(searchRowCount <= 0) searchRowCount = 1;

        selectorLayoutManager = new StaggeredGridLayoutManager(normalRowCount, StaggeredGridLayoutManager.VERTICAL);
        selectRecyclerView.setLayoutManager(selectorLayoutManager);
        getSupportActionBar().setSubtitle(getResources().getString(R.string.activity_subtitle_main));

        likelyhoodSort();

        fabNoteEdit = (FloatingActionButton) findViewById(R.id.fab_edit_note);
        fabAttachPicture = (FloatingActionButton) findViewById(R.id.fab_attach_picture);
        fabVocalHelper = (FloatingActionButton) findViewById(R.id.vocal_helper);

        fabNoteEdit.setOnClickListener(v -> {
            // Handle the click on the FAB
            if(viewModel.currentActivity().getValue() != null) {
                NoteEditDialog dialog = new NoteEditDialog();
                dialog.setText(viewModel.mNote.getValue());
                dialog.show(getSupportFragmentManager(), "NoteEditDialogFragment");
            }else{
                Toast.makeText(MainActivity.this, getResources().getString(R.string.no_active_activity_error), Toast.LENGTH_LONG).show();
            }
        });

        fabAttachPicture.setOnClickListener(v -> {
            // Handle the click on the FAB
            if(viewModel.currentActivity() != null) {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    File photoFile = null;
                    try {
                        photoFile = createImageFile();
                        Log.i(TAG, "create file for image capture " + (photoFile == null ? "" : photoFile.getAbsolutePath()));

                    } catch (IOException ex) {
                        // Error occurred while creating the File
                        Toast.makeText(MainActivity.this, getResources().getString(R.string.camera_error), Toast.LENGTH_LONG).show();
                    }
                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        // Save a file: path for use with ACTION_VIEW intents
                        mCurrentPhotoPath = photoFile.getAbsolutePath();

                        Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
                                BuildConfig.APPLICATION_ID + ".fileprovider",
                                photoFile);
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                        takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                    }

                }
            }else{
                Toast.makeText(MainActivity.this, getResources().getString(R.string.no_active_activity_error), Toast.LENGTH_LONG).show();
            }
        });


        fabVocalHelper.setOnClickListener(v->{
            if( null == mIat){
                showMsg("wrong");
                return;
            }
            mIatResults.clear();
            setParam();
            mIatDialog.setListener(mRecognizerDialogListener);
            mIatDialog.show();
        });

        fabNoteEdit.show();
        PackageManager pm = getPackageManager();

        if(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            fabAttachPicture.show();
        }else{
            fabAttachPicture.hide();
        }

        // Get the intent, verify the action and get the search query
        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            filterActivityView(query);
        }
        onActivityChanged(); /* do this at the very end to ensure that no Loader finishes its data loading before */
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mIat != null) {
            // 退出时释放连接
            mIat.cancel();
            mIat.destroy();
        }
    }
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "IMG_";
        if(viewModel.currentActivity().getValue() != null){
            imageFileName += viewModel.currentActivity().getValue().getName();
            imageFileName += "_";
        }

        imageFileName += timeStamp;
        File storageDir = null;
        int permissionCheck = ContextCompat.checkSelfPermission(ActivityDiaryApplication.getAppContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if(permissionCheck == PackageManager.PERMISSION_GRANTED) {
            storageDir = GraphicsHelper.imageStorageDirectory();
        }else{
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                Toast.makeText(this,R.string.perm_write_external_storage_xplain, Toast.LENGTH_LONG).show();
            }
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            storageDir = null;
        }

        if(storageDir != null){
            File image = new File(storageDir, imageFileName + ".jpg");
            image.createNewFile();
/* #80            File image = File.createTempFile(
                    imageFileName,
                    ".jpg",
                    storageDir
            );
            */
            return image;
        }else{
            return null;
        }

    }

    @Override
    public void onResume() {
        mNavigationView.getMenu().findItem(R.id.nav_main).setChecked(true);
        ActivityHelper.helper.registerDataChangeListener(this);
        onActivityChanged(); /* refresh the current activity data */
        super.onResume();

        selectAdapter.notifyDataSetChanged(); // redraw the complete recyclerview
        ActivityHelper.helper.evaluateAllConditions(); // this is quite heavy and I am not so sure whether it is a good idea to do it unconditionally here...
    }

    @Override
    public void onPause() {
        ActivityHelper.helper.unregisterDataChangeListener(this);

        super.onPause();
    }

    @Override
    public boolean onLongClick(View view) {
        Intent i = new Intent(MainActivity.this, EditActivity.class);
        if(viewModel.currentActivity().getValue() != null) {
            i.putExtra("activityID", viewModel.currentActivity().getValue().getId());
        }
        startActivity(i);
        return true;
    }

    @Override
    public boolean onItemLongClick(int adapterPosition){
        Intent i = new Intent(MainActivity.this, EditActivity.class);
        i.putExtra("activityID", selectAdapter.item(adapterPosition).getId());
        startActivity(i);
        return true;
    }

    @Override
    public void onItemClick(int adapterPosition) {

        DiaryActivity newAct = selectAdapter.item(adapterPosition);
//        DiaryActivity tmp = ActivityHelper.helper.getCurrentActivity();
        if(newAct != ActivityHelper.helper.getCurrentActivity()) {

            ActivityHelper.helper.setCurrentActivity(newAct);

            searchView.setQuery("", false);
            searchView.setIconified(true);


            SpannableStringBuilder snackbarText = new SpannableStringBuilder();
            snackbarText.append(newAct.getName());
            int end = snackbarText.length();
            snackbarText.setSpan(new ForegroundColorSpan(newAct.getColor()), 0, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            snackbarText.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            snackbarText.setSpan(new RelativeSizeSpan((float) 1.4152), 0, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);

            Snackbar undoSnackBar = Snackbar.make(findViewById(R.id.main_layout),
                    snackbarText, Snackbar.LENGTH_LONG);
            undoSnackBar.setAction(R.string.action_undo, new View.OnClickListener() {
                /**
                 * Called when a view has been clicked.
                 *
                 * @param v The view that was clicked.
                 */
                @Override
                public void onClick(View v) {
                    Log.v(TAG, "UNDO Activity Selection");
                    ActivityHelper.helper.undoLastActivitySelection();
                }
            });
            undoSnackBar.show();
        }else{
            /* clicked the currently active activity in the list, so let's terminate it due to #176 */
            ActivityHelper.helper.setCurrentActivity(null);
        }
    }

    public void onActivityChanged(){
        DiaryActivity newAct = ActivityHelper.helper.getCurrentActivity();
        viewModel.mCurrentActivity.setValue(newAct);

        if(newAct != null) {
            mQHandler.startQuery(QUERY_CURRENT_ACTIVITY_STATS, null,
                    ActivityDiaryContract.DiaryActivity.CONTENT_URI,
                    new String[] {
                            ActivityDiaryContract.DiaryActivity._ID,
                            ActivityDiaryContract.DiaryActivity.NAME,
                            ActivityDiaryContract.DiaryActivity.X_AVG_DURATION,
                            ActivityDiaryContract.DiaryActivity.X_START_OF_LAST
                    },
                    ActivityDiaryContract.DiaryActivity._DELETED + " = 0 AND "
                            + ActivityDiaryContract.DiaryActivity._ID + " = ?",
                    new String[] {
                            Integer.toString(newAct.getId())
                    },
                    null);

            queryAllTotals();
        }

        viewModel.setCurrentDiaryUri(ActivityHelper.helper.getCurrentDiaryUri());
        TextView aName = findViewById(R.id.activity_name);
        // TODO: move this logic into the DetailViewModel??

        viewModel.mAvgDuration.setValue("-");
        viewModel.mStartOfLast.setValue("-");
        viewModel.mTotalToday.setValue("-");
        /* stats are updated after query finishes in mQHelper */

        if(viewModel.currentActivity().getValue() != null) {
            aName.setText(viewModel.currentActivity().getValue().getName());
            findViewById(R.id.activity_background).setBackgroundColor(viewModel.currentActivity().getValue().getColor());
            aName.setTextColor(GraphicsHelper.textColorOnBackground(viewModel.currentActivity().getValue().getColor()));
            viewModel.mNote.setValue(ActivityHelper.helper.getCurrentNote());
        }else{
            int col;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                col = ActivityDiaryApplication.getAppContext().getResources().getColor(R.color.colorPrimary, null);
            }else {
                col = ActivityDiaryApplication.getAppContext().getResources().getColor(R.color.colorPrimary);
            }
            aName.setText(getResources().getString(R.string.activity_title_no_selected_act));
            findViewById(R.id.activity_background).setBackgroundColor(col);
            aName.setTextColor(GraphicsHelper.textColorOnBackground(col));
            viewModel.mDuration.setValue("-");
            viewModel.mNote.setValue("");
        }
        selectorLayoutManager.scrollToPosition(0);
    }

    public void queryAllTotals() {
        // TODO: move this into the DetailStatFragement
        DiaryActivity a = viewModel.mCurrentActivity.getValue();
        if(a != null) {
            int id = a.getId();

            long end = System.currentTimeMillis();
            queryTotal(Calendar.DAY_OF_YEAR, end, id);
            queryTotal(Calendar.WEEK_OF_YEAR, end, id);
            queryTotal(Calendar.MONTH, end, id);
        }
    }

    private void queryTotal(int field, long end, int actID) {
        Calendar calStart = DateHelper.startOf(field, end);
        long start = calStart.getTimeInMillis();
        Uri u = ActivityDiaryContract.DiaryStats.CONTENT_URI;
        u = Uri.withAppendedPath(u, Long.toString(start));
        u = Uri.withAppendedPath(u, Long.toString(end));

        mQHandler.startQuery(QUERY_CURRENT_ACTIVITY_TOTAL, new StatParam(field, end),
                u,
                new String[] {
                        ActivityDiaryContract.DiaryStats.DURATION
                },
                ActivityDiaryContract.DiaryActivity.TABLE_NAME + "." + ActivityDiaryContract.DiaryActivity._ID
                        + " = ?",
                new String[] {
                        Integer.toString(actID)
                },
                null);
    }

    /**
     * Called on change of the activity order due to likelyhood.
     */
    @Override
    public void onActivityOrderChanged() {
        /* only do likelihood sort in case we are not in a search */
        if(filter.length() == 0){
            likelyhoodSort();
        }
    }

    /**
     * Called when the data has changed.
     */
    @Override
    public void onActivityDataChanged() {
        selectAdapter.notifyDataSetChanged();
    }

    @Override
    public void onActivityDataChanged(DiaryActivity activity){
        selectAdapter.notifyItemChanged(selectAdapter.positionOf(activity));
    }

    /**
     * Called on addition of an activity.
     *
     * @param activity
     */
    @Override
    public void onActivityAdded(DiaryActivity activity) {
        /* no need to add it, as due to the reevaluation of the conditions the order change will happen */
    }

    /**
     * Called on removale of an activity.
     *
     * @param activity
     */
    @Override
    public void onActivityRemoved(DiaryActivity activity) {
        selectAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchMenuItem = menu.findItem(R.id.action_filter);
        searchView = (SearchView) searchMenuItem.getActionView();
        // Assumes current activity is the searchable activity
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

        searchView.setOnCloseListener(this);
        searchView.setOnQueryTextListener(this);
        // setOnSuggestionListener -> for selection of a suggestion
        // setSuggestionsAdapter
        searchView.setOnSearchClickListener(v -> setSearchMode(true));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.action_add_activity:
                startActivity(new Intent(this, EditActivity.class));
                break;
            /* filtering is handled by the SearchView widget
            case R.id.action_filter:
            */
        }
        return super.onOptionsItemSelected(item);
    }

    protected void onNewIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            filterActivityView(query);
        }

        if (intent.hasExtra("SELECT_ACTIVITY_WITH_ID")) {
            int id = intent.getIntExtra("SELECT_ACTIVITY_WITH_ID", -1);
            ActivityHelper.helper.setCurrentActivity(ActivityHelper.helper.activityWithId(id));
        }
    }

    private void filterActivityView(String query){
        this.filter = query;
        if(filter.length() == 0){
            likelyhoodSort();
        }else {
            ArrayList<DiaryActivity> filtered = ActivityHelper.helper.sortedActivities(query);

            selectAdapter = new SelectRecyclerViewAdapter(MainActivity.this, filtered);
            selectRecyclerView.swapAdapter(selectAdapter, false);
            selectRecyclerView.scrollToPosition(0);
        }
    }

    private void likelyhoodSort() {
        selectAdapter = new SelectRecyclerViewAdapter(MainActivity.this, ActivityHelper.helper.getActivities());
        selectRecyclerView.swapAdapter(selectAdapter, false);
    }

    @Override
    public boolean onClose() {
        setSearchMode(false);
        likelyhoodSort();
        return false; /* we wanna clear and close the search */
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        setSearchMode(false);
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        filterActivityView(newText);
        return true; /* we handle the search directly, so no suggestions need to be show even if #70 is implemented */
    }

    @Override
    public void onNoteEditPositiveClock(String str, DialogFragment dialog) {
        ContentValues values = new ContentValues();
        values.put(ActivityDiaryContract.Diary.NOTE, str);

        mQHandler.startUpdate(0,
                null,
                viewModel.getCurrentDiaryUri(),
                values,
                null, null);

        viewModel.mNote.postValue(str);
        ActivityHelper.helper.setCurrentNote(str);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            if(mCurrentPhotoPath != null && viewModel.getCurrentDiaryUri() != null) {
                Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
                        BuildConfig.APPLICATION_ID + ".fileprovider",
                        new File(mCurrentPhotoPath));
                ContentValues values = new ContentValues();
                values.put(ActivityDiaryContract.DiaryImage.URI, photoURI.toString());
                values.put(ActivityDiaryContract.DiaryImage.DIARY_ID, viewModel.getCurrentDiaryUri().getLastPathSegment());

                mQHandler.startInsert(0,
                        null,
                        ActivityDiaryContract.DiaryImage.CONTENT_URI,
                        values);

                if(PreferenceManager
                        .getDefaultSharedPreferences(ActivityDiaryApplication.getAppContext())
                        .getBoolean(SettingsActivity.KEY_PREF_TAG_IMAGES, true)) {
                    try {
                        ExifInterface exifInterface = new ExifInterface(mCurrentPhotoPath);
                        if (viewModel.currentActivity().getValue() != null) {
                            /* TODO: #24: when using hierarchical activities tag them all here, seperated with comma */
                            /* would be great to use IPTC keywords instead of EXIF UserComment, but
                             * at time of writing (2017-11-24) it is hard to find a library able to write IPTC
                             * to JPEG for android.
                             * pixymeta-android or apache/commons-imaging could be interesting for this.
                             * */
                            exifInterface.setAttribute(ExifInterface.TAG_USER_COMMENT, viewModel.currentActivity().getValue().getName());
                            exifInterface.saveAttributes();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "writing exif data to " + mCurrentPhotoPath + " failed", e);
                    }
                }
            }
        }
    }


    private void setupViewPager(ViewPager viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new DetailStatFragement(), getResources().getString(R.string.fragment_detail_stats_title));
        adapter.addFragment(new DetailNoteFragment(), getResources().getString(R.string.fragment_detail_note_title));
        adapter.addFragment(new DetailPictureFragement(), getResources().getString(R.string.fragment_detail_pictures_title));
        viewPager.setAdapter(adapter);
    }

    class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }
    }


    private class MainAsyncQueryHandler extends AsyncQueryHandler{
        public MainAsyncQueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        public void startQuery(int token, Object cookie, Uri uri, String[] projection, String selection, String[] selectionArgs, String orderBy) {
            super.startQuery(token, cookie, uri, projection, selection, selectionArgs, orderBy);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            super.onQueryComplete(token, cookie, cursor);
            if ((cursor != null) && cursor.moveToFirst()) {
                if (token == QUERY_CURRENT_ACTIVITY_STATS) {
                    long avg = cursor.getLong(cursor.getColumnIndex(ActivityDiaryContract.DiaryActivity.X_AVG_DURATION));
                    viewModel.mAvgDuration.setValue(getResources().
                            getString(R.string.avg_duration_description, TimeSpanFormatter.format(avg)));

                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ActivityDiaryApplication.getAppContext());
                    String formatString = sharedPref.getString(SettingsActivity.KEY_PREF_DATETIME_FORMAT,
                            getResources().getString(R.string.default_datetime_format));

                    long start = cursor.getLong(cursor.getColumnIndex(ActivityDiaryContract.DiaryActivity.X_START_OF_LAST));

                    viewModel.mStartOfLast.setValue(getResources().
                            getString(R.string.last_done_description, DateFormat.format(formatString, start)));

                }else if(token == QUERY_CURRENT_ACTIVITY_TOTAL) {
                    StatParam p = (StatParam)cookie;
                    long total = cursor.getLong(cursor.getColumnIndex(ActivityDiaryContract.DiaryStats.DURATION));

                    String x = DateHelper.dateFormat(p.field).format(p.end);
                    x = x + ": " + TimeSpanFormatter.format(total);
                    switch(p.field){
                        case Calendar.DAY_OF_YEAR:
                            viewModel.mTotalToday.setValue(x);
                            break;
                        case Calendar.WEEK_OF_YEAR:
                            viewModel.mTotalWeek.setValue(x);
                            break;
                        case Calendar.MONTH:
                            viewModel.mTotalMonth.setValue(x);
                            break;
                    }
                }
            }
        }
    }

    private class StatParam {
        public int field;
        public long end;
        public StatParam(int field, long end) {
            this.field = field;
            this.end = end;
        }
    }
}
