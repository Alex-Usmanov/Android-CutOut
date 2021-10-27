package com.github.gabrielbb.cutout;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.alexvasilkov.gestures.views.interfaces.GestureView;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;
import java.io.IOException;

import pl.aprilapps.easyphotopicker.DefaultCallback;
import pl.aprilapps.easyphotopicker.EasyImage;
import top.defaults.checkerboarddrawable.CheckerboardDrawable;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static com.github.gabrielbb.cutout.CutOut.CUTOUT_EXTRA_INTRO;

enum BottomBarMode {
    DEFAULT,
    BRUSH,
    CLEAR_BACKGROUND
}

class ChangesHolder {
    int cutsCount;
    int undoneCount;

    public ChangesHolder(int cutsCount, int undoneCount) {
        this.cutsCount = cutsCount;
        this.undoneCount = undoneCount;
    }
}

public class CutOutActivity extends AppCompatActivity {
    private static final int INTRO_REQUEST_CODE = 4;
    private static final int WRITE_EXTERNAL_STORAGE_CODE = 1;
    private static final int IMAGE_CHOOSER_REQUEST_CODE = 2;
    private static final int CAMERA_REQUEST_CODE = 3;
    private static final String INTRO_SHOWN = "INTRO_SHOWN";

    private boolean didShowDialog = false;
    private Dialog dialog;

    FrameLayout loadingModal;
    private GestureView gestureView;
    private DrawView drawView;
    private LinearLayout defaultBottomBar;
    private LinearLayout brushBottomBar;
    private SeekBar seekBar;
    private TextView seekBarText;
    private Button doneButton;
    private Button undoButton;
    private Button redoButton;
    private ChangesHolder drawViewLastChanges;

    private static final short MAX_ERASER_SIZE = 100;
    private static final short BORDER_SIZE = 45;

    private BottomBarMode bottomBarMode = BottomBarMode.DEFAULT;
    private void deactivateGestureView() {
        gestureView.getController().getSettings()
                .setPanEnabled(false)
                .setZoomEnabled(false)
                .setDoubleTapEnabled(false);
    }

    private void initializeActionButtons() {
        LinearLayout autoClearButton = findViewById(R.id.auto_clear_button);
        LinearLayout manualClearButton = findViewById(R.id.manual_clear_button);
        autoClearButton.setOnClickListener((buttonView) -> {
            bottomBarMode = BottomBarMode.CLEAR_BACKGROUND;
            drawView.setAction(DrawView.DrawViewAction.AUTO_CLEAR);
            autoClearButton.setAlpha(0.7f);
            manualClearButton.setAlpha(1.0f);
            deactivateGestureView();
            handleBottomActionButtonChange();
        });

        drawView.setAction(DrawView.DrawViewAction.MANUAL_CLEAR);
        manualClearButton.setOnClickListener((buttonView) -> {
            bottomBarMode = BottomBarMode.BRUSH;
            drawView.setAction(DrawView.DrawViewAction.MANUAL_CLEAR);
            manualClearButton.setAlpha(0.7f);
            autoClearButton.setAlpha(1.0f);
            deactivateGestureView();
            handleBottomActionButtonChange();
        });

        deactivateGestureView();
    }


    private void handleBottomActionButtonChange () {
        doneButton.setVisibility(VISIBLE);
        if (bottomBarMode == BottomBarMode.DEFAULT) {
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.close);
            drawView.setAction(DrawView.DrawViewAction.NONE);
            doneButton.setVisibility(INVISIBLE);
            brushBottomBar.setVisibility(INVISIBLE);
            defaultBottomBar.setVisibility(VISIBLE);
        } else {
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.back);
            drawViewLastChanges = new ChangesHolder(
                    drawView.getUndoCount(),
                    drawView.getRedoCount()
            );

            drawView.setSkipChangesHolder(new ChangesHolder(
                    drawViewLastChanges.cutsCount,
                    drawViewLastChanges.undoneCount
            ));

            if (bottomBarMode == BottomBarMode.BRUSH) {
                defaultBottomBar.setVisibility(INVISIBLE);
                brushBottomBar.setVisibility(VISIBLE);
            } else {
                if (!didShowDialog) {
                    didShowDialog = true;
                    showDialogAboutBackgroundRemoval();
                }

                brushBottomBar.setVisibility(INVISIBLE);
                defaultBottomBar.setVisibility(INVISIBLE);
            }
        }
    }

    private void showDialogAboutBackgroundRemoval () {
        dialog = new Dialog(this);
        dialog.setContentView(R.layout.background_remove_dialog);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        Button dialogButton = dialog.findViewById(R.id.dialog_button);
        dialogButton.setOnClickListener(v -> {
            dialog.dismiss();
        });
        dialog.show();
    }

    private void handleSeekBar () {
        seekBar = findViewById(R.id.seekbar);
        seekBar.setMax(MAX_ERASER_SIZE);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = (progress * (seekBar.getWidth() - 2 * seekBar.getThumbOffset())) / seekBar.getMax();
                seekBarText.setText("" + progress);
                seekBarText.setX(seekBar.getX() + value + seekBar.getThumbOffset() / 2);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                drawView.setStrokeWidth(seekBar.getProgress());
            }
        });

        seekBar.setProgress(50);
    }

    private void customizeDefault () {
        FrameLayout drawViewLayout = findViewById(R.id.drawViewLayout);
        int sdk = android.os.Build.VERSION.SDK_INT;
        if (sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
            drawViewLayout.setBackgroundDrawable(CheckerboardDrawable.create());
        } else {
            drawViewLayout.setBackground(CheckerboardDrawable.create());
        }

        drawView.setDrawingCacheEnabled(true);
        drawView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        drawView.setStrokeWidth(50);
        drawView.setAction(DrawView.DrawViewAction.NONE);
        loadingModal = findViewById(R.id.loadingModal);
        loadingModal.setVisibility(INVISIBLE);
        drawView.setLoadingModal(loadingModal);
    }

    /// MARK: - Main methods

    private void setUndoRedo() {
        undoButton = findViewById(R.id.undo);
        undoButton.setAlpha(DrawView.DISABLED_ALPHA);
        undoButton.setOnClickListener(v -> undo());
        redoButton = findViewById(R.id.redo);
        redoButton.setAlpha(DrawView.DISABLED_ALPHA);
        redoButton.setOnClickListener(v -> redo());

        drawView.setButtons(undoButton, redoButton);
    }

    void exitWithError(Exception e) {
        Intent intent = new Intent();
        intent.putExtra(CutOut.CUTOUT_EXTRA_RESULT, e);
        setResult(CutOut.CUTOUT_ACTIVITY_RESULT_ERROR_CODE, intent);
        finish();
    }

    private void setDrawViewBitmap(Uri uri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            drawView.setBitmap(bitmap);
        } catch (IOException e) {
            exitWithError(e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {

            CropImage.ActivityResult result = CropImage.getActivityResult(data);

            if (resultCode == Activity.RESULT_OK) {

                setDrawViewBitmap(result.getUri());

            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                exitWithError(result.getError());
            } else {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        } else if (requestCode == INTRO_REQUEST_CODE) {
            SharedPreferences.Editor editor = getPreferences(Context.MODE_PRIVATE).edit();
            editor.putBoolean(INTRO_SHOWN, true);
            editor.apply();
            start();
        } else {
            EasyImage.handleActivityResult(requestCode, resultCode, data, this, new DefaultCallback() {
                @Override
                public void onImagePickerError(Exception e, EasyImage.ImageSource source, int type) {
                    exitWithError(e);
                }

                @Override
                public void onImagePicked(File imageFile, EasyImage.ImageSource source, int type) {
                    setDrawViewBitmap(Uri.parse(imageFile.toURI().toString()));
                }

                @Override
                public void onCanceled(EasyImage.ImageSource source, int type) {
                    // Cancel handling, removing taken photo if it was canceled
                    if (source == EasyImage.ImageSource.CAMERA) {
                        File photoFile = EasyImage.lastlyTakenButCanceledPhoto(CutOutActivity.this);
                        if (photoFile != null) photoFile.delete();
                    }

                    setResult(RESULT_CANCELED);
                    finish();
                }
            });
        }
    }

    private void undo() {
        drawView.undo();
    }

    private void redo() {
        drawView.redo();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_edit);
        Toolbar toolbar = findViewById(R.id.photo_edit_toolbar);
        setSupportActionBar(toolbar);
        gestureView = findViewById(R.id.gestureView);
        drawView = findViewById(R.id.drawView);
        defaultBottomBar = findViewById(R.id.default_bottom_bar);
        brushBottomBar = findViewById(R.id.seekbar_layout);
        seekBarText = findViewById(R.id.progress_text);
        doneButton = findViewById(R.id.done_button);
        handleSeekBar();

        customizeDefault();

        setUndoRedo();
        initializeActionButtons();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.close);
            if (toolbar.getNavigationIcon() != null) {
                toolbar.getNavigationIcon().setColorFilter(getResources().getColor(R.color.black), PorterDuff.Mode.SRC_ATOP);
            }

        }

        doneButton.setOnClickListener(v -> {
            bottomBarMode = BottomBarMode.DEFAULT;
            handleBottomActionButtonChange();
        });

        Button doneNavButton = findViewById(R.id.done);
        doneNavButton.setOnClickListener(v -> startSaveDrawingTask());

        if (getIntent().getBooleanExtra(CUTOUT_EXTRA_INTRO, false) && !getPreferences(Context.MODE_PRIVATE).getBoolean(INTRO_SHOWN, false)) {
            Intent intent = new Intent(this, IntroActivity.class);
            startActivityForResult(intent, INTRO_REQUEST_CODE);
        } else {
            start();
        }

        handleBottomActionButtonChange();

        seekBar.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener()
        {
            @Override
            public boolean onPreDraw()
            {
                if (seekBar.getViewTreeObserver().isAlive())
                    seekBar.getViewTreeObserver().removeOnPreDrawListener(this);

                int value = (50 * (seekBar.getWidth() - 2 * seekBar.getThumbOffset())) / seekBar.getMax();
                seekBarText.setX(seekBar.getX() + value + seekBar.getThumbOffset() / 2);
                return true;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (bottomBarMode == BottomBarMode.DEFAULT) {
                    setResult(RESULT_CANCELED);
                    finish();
                    return true;
                } else {
                    handleUndoChanges();
                    return false;
                }
        }
        return super.onOptionsItemSelected(item);
    }

    private Uri getExtraSource() {
        return getIntent().hasExtra(CutOut.CUTOUT_EXTRA_SOURCE) ? (Uri) getIntent().getParcelableExtra(CutOut.CUTOUT_EXTRA_SOURCE) : null;
    }

    private void start() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

            Uri uri = getExtraSource();

            if (getIntent().getBooleanExtra(CutOut.CUTOUT_EXTRA_CROP, false)) {

                CropImage.ActivityBuilder cropImageBuilder;
                if (uri != null) {
                    cropImageBuilder = CropImage.activity(uri);
                } else {
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

                        cropImageBuilder = CropImage.activity();
                    } else {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.CAMERA},
                                CAMERA_REQUEST_CODE);
                        return;
                    }
                }

                cropImageBuilder = cropImageBuilder.setGuidelines(CropImageView.Guidelines.ON);
                cropImageBuilder.start(this);
            } else {
                if (uri != null) {
                    setDrawViewBitmap(uri);
                } else {
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

                        EasyImage.openChooserWithGallery(this, getString(R.string.image_chooser_message), IMAGE_CHOOSER_REQUEST_CODE);
                    } else {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.CAMERA},
                                CAMERA_REQUEST_CODE);
                    }
                }
            }

        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    WRITE_EXTERNAL_STORAGE_CODE);
        }
    }

    private void startSaveDrawingTask() {
        SaveDrawingTask task = new SaveDrawingTask(this);

        int borderColor;
        if ((borderColor = getIntent().getIntExtra(CutOut.CUTOUT_EXTRA_BORDER_COLOR, -1)) != -1) {
            Bitmap image = BitmapUtility.getBorderedBitmap(this.drawView.getDrawingCache(), borderColor, BORDER_SIZE);
            task.execute(image);
        } else {
            task.execute(this.drawView.getDrawingCache());
        }
    }

    @Override
    public void onBackPressed() {
        if (bottomBarMode == BottomBarMode.DEFAULT) {
            super.onBackPressed();
        } else {
            handleUndoChanges();
        }
    }

    private void handleUndoChanges () {
        bottomBarMode = BottomBarMode.DEFAULT;
        drawView.setAction(DrawView.DrawViewAction.NONE);
        int undoneDiff = drawView.getRedoCount() - drawViewLastChanges.undoneCount;
        int cutsDiff = drawView.getUndoCount() - drawViewLastChanges.cutsCount;

        for (int i = 0; i < undoneDiff; i++) {
            drawView.redo();
        }

        for (int i = 0; i < cutsDiff; i++) {
            drawView.undo();
        }


        handleBottomActionButtonChange();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            start();
        } else {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }
}

