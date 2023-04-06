package com.selvasai.selvyocr.creditcard.sample2;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.selvasai.selvyocr.creditcard.ImageRecognizer;
import com.selvasai.selvyocr.creditcard.sample2.util.Utils;
import com.selvasai.selvyocr.creditcard.util.BuildInfo;
import com.selvasai.selvyocr.creditcard.util.LicenseChecker;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    /**
     * 암/복호화 키값: 32 Byte (256 Bit)
     * 적정 값으로 정의해서 사용
     */
    protected static final byte[] AES256_KEY = {
            's', 'e', 'l', 'v', 'a', 's', 'a', 'i',
            'i', 'a', 's', 'a', 'v', 'l', 'e', 's',
            'i', 'a', 's', 'a', 'v', 'l', 'e', 's',
            's', 'e', 'l', 'v', 'a', 's', 'a', 'i'};

    /**
     * 초기 벡터: 16 Byte(128 Bit)
     * 적정 값으로 정의해서 사용
     */
    protected static final byte[] AES256_IV = {
            's', 'e', 'l', 'v', 'a', 's', 'a', 'i',
            'i', 'a', 's', 'a', 'v', 'l', 'e', 's'};

    private static final int REQ_PERMISSION_RESULT = 0;

    private static final int MSG_RESULT_SUCCESS = 0x0001;
    private static final int MSG_RESULT_FAIL = 0x0002;

    private ImageView mResultImageView;
    private TextView mResultTextView;
    private TextView mCardNumberTextView;
    private TextView mValidDateTextView;
    private TextView mIsValidCardNumber;

    private Thread mResultImageThread; // 백그라운드에서 처리하기 위한 Thread

    private FloatingActionButton mFab;

    private Bitmap mCreditCardBitmap;

    private static final boolean LOAD_LIBRARY_BY_PATH = false;   // 지정된 경로의 so 파일을 읽어오기 위한 플래그, true - 지정된 경로의 so 파일 읽어서 사용하는 기능 사용
    private static final double MIN_BLUR_VALUE = 0.6;
    private ActivityResultLauncher<Intent> activityResultLauncher;
    /**
     * OCR 결과 리스너 <br/>
     * 인식에 성공하면 인식결과 정보를 넘겨줌
     */
    private ImageRecognizer.RecognitionListener mRecognitionListener = new ImageRecognizer.RecognitionListener() {

        /**
         * 인식에 성공하여 결과값 전달
         * @param result 인식 결과 데이터
         */
        @Override
        public void onFinish(ImageRecognizer.Result result) {
            Utils.progressDialog(MainActivity.this, false, null);
            mResultImageView.setImageBitmap(result.image);
            mResultImageView.setVisibility(View.VISIBLE);

            String cardNumber;
            if (AES256_KEY != null && AES256_IV != null && AES256_KEY.length == 32 && AES256_IV.length == 16) {
                cardNumber = ImageRecognizer.decrypt(result.cardNumber, AES256_KEY, AES256_IV);
            } else {
                cardNumber = result.cardNumber;
            }
            viewSetting(cardNumber, result.validYear, result.validMonth, result.isValidCardNumber);
        }

        /**
         * 인식에 실패
         * @param code 오류코드
         *              {@link ImageRecognizer#ERROR_CODE_FILE_NOT_FOUND}: ROM 파일을 찾지 못함
         *              {@link ImageRecognizer#ERROR_CODE_LICENSE_CHECK_FAILED}: 라이선스 만료
         *              {@link ImageRecognizer#ERROR_CODE_IMAGE_BLUR}: 이미지가 너무 흐림
         */
        @Override
        public void onError(int code) {
            Utils.progressDialog(MainActivity.this, false, null);
            if (code == ImageRecognizer.ERROR_CODE_FILE_NOT_FOUND) {
                Toast.makeText(MainActivity.this, "error code = " + "ERROR_CODE_FILE_NOT_FOUND", Toast.LENGTH_SHORT).show();
            } else if (code == ImageRecognizer.ERROR_CODE_LICENSE_CHECK_FAILED) {
                Toast.makeText(MainActivity.this, "error code = " + "ERROR_CODE_LICENSE_CHECK_FAILED", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "error code = " + "ERROR_CODE_IMAGE_BLUR", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_main);
        toolbar.setTitle(BuildInfo.getVersion());
        setSupportActionBar(toolbar);

        initView();

        if (LOAD_LIBRARY_BY_PATH == false) {
            Date date = LicenseChecker.getExpiredDate(getApplicationContext());
            String dateToString = new SimpleDateFormat("yyyy-MM-dd").format(date);
            Toast.makeText(getApplicationContext(), "Expiry Date : " + dateToString, Toast.LENGTH_LONG).show();
        }

        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Utils.progressDialog(MainActivity.this, true, getString(R.string.recognize));
                    Intent data = result.getData();
                    if (null == data) {
                        Utils.progressDialog(MainActivity.this, false, null);
                        Toast.makeText(getApplicationContext(), "getData is null", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Uri uri = data.getData();
                    Bitmap inputImage = null;
                    try {
                        inputImage = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                        // Use the `bitmap` object as needed
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    if (!LicenseChecker.isValidLicense(getApplicationContext())) {
                        Utils.progressDialog(MainActivity.this, false, null);
                        Toast.makeText(getApplicationContext(), "License expired!", Toast.LENGTH_SHORT).show();
                        return;
                    } else if (null == inputImage) {
                        Utils.progressDialog(MainActivity.this, false, null);
                        Toast.makeText(getApplicationContext(), "Image Load Fail!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ImageRecognizer imageRecognizer = new ImageRecognizer(getApplicationContext(), AES256_KEY, AES256_IV);

                    int width = inputImage.getWidth();
                    int height = inputImage.getHeight();
                    int[] intArray = new int[width * height];
                    inputImage.getPixels(intArray, 0, width, 0, 0, width, height);

                    imageRecognizer.startRecognition(intArray, width, height, (float)MIN_BLUR_VALUE, mRecognitionListener);
                }
        );
    }


    @Override
    protected void onDestroy() {
        mResultImageView = null;
        super.onDestroy();
    }

    /**
     * 초기화
     */
    private void initView() {
        LinearLayout textImageLayout = (LinearLayout) findViewById(R.id.text_image_layout);

        mResultImageView = new ImageView(MainActivity.this);
        mResultImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mResultImageView.setVisibility(View.GONE);
        textImageLayout.addView(mResultImageView);

        mResultTextView = new TextView(MainActivity.this);
        mResultTextView.setText("Image load fail");
        mResultTextView.setPadding(20, 15, 15, 15);
        mResultTextView.setGravity(Gravity.CENTER);
        mResultTextView.setTextSize(20);
        mResultTextView.setVisibility(View.GONE);
        textImageLayout.addView(mResultTextView);

        mCardNumberTextView = (TextView) findViewById(R.id.tv_card_number);
        mValidDateTextView = (TextView) findViewById(R.id.tv_expiry_date);
        mIsValidCardNumber = (TextView) findViewById(R.id.tv_isvalid_cardnumber);

        // 카메라 화면으로 이동 버튼
        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestPermission();
            }
        });
    }

    private void requestPermission() {
        ArrayList<String> permissions = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (permissions.size() > 0) {
            String[] temp = new String[permissions.size()];
            permissions.toArray(temp);
            ActivityCompat.requestPermissions(MainActivity.this, temp, REQ_PERMISSION_RESULT);
        } else {
            openGallery();
        }
    }

    private void openGallery() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        activityResultLauncher.launch(intent);
    }

    /**
     * 카드 인식 결과를 화면에 보여줌
     *
     * @param cardNumber 카드 번호
     * @param validYear 유효 날짜 (년)
     * @param validMonth 유효 날짜 (월)
     */
    private void viewSetting(String cardNumber, String validYear, String validMonth, boolean isValidCardNumber) {
        // 카드 번호
        mCardNumberTextView.setText(parseCardNumber(cardNumber));
        // 유효 날짜
        mValidDateTextView.setText(parseValidDate(validYear, validMonth));
        // 카드 번호 유효성
        mIsValidCardNumber.setText( isValidCardNumber ? getString(R.string.valid_card_number) : getString(R.string.not_valid_card_number));
    }


    private String parseCardNumber(String cardNumber) {
        if (cardNumber == null) {
            return null;
        }

        if (cardNumber.length() == 15) {
            return cardNumber.substring(0,4) + "-" + cardNumber.substring(4,10) + "-" + cardNumber.substring(10,15);
        } else if (cardNumber.length() == 16) {
            return cardNumber.substring(0,4) + "-" + cardNumber.substring(4,8) + "-" + cardNumber.substring(8,12) + "-" + cardNumber.substring(12,16);
        } else {
            return cardNumber;
        }
    }

    private String parseValidDate(String validYear, String validMonth) {
        if (validYear == null || validMonth == null) {
            return null;
        }

        return validMonth + "월 " + validYear + "년";
    }
}