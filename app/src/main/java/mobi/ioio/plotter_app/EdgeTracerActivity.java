package mobi.ioio.plotter_app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import mobi.ioio.plotter.shapes.BinaryImageMultiCurve;
import mobi.ioio.plotter.trace.BinaryImage;

public class EdgeTracerActivity extends Activity implements OnClickListener {
	ImageView imageView_;
	TextView selectImageTextView_;
	SeekBar blurSeekBar_;
	SeekBar highSeekBar_;
	SeekBar lowSeekBar_;
	CheckBox grayscaleCheckbox_;
	CheckBox mirrorCheckbox_;
	Button doneButton_;

	private Mat srcImage_;
	private Mat procImage_;
	private Mat edgesImage_;
	private Mat tmpMat_;

	private static final int GET_IMAGE_REQUEST_CODE = 100;

	private static final String TAG = "EdgeTracerActivity";
	private static final int MIN_CURVE_PIXELS = 8;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG, "Trying to load OpenCV library");
		if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mOpenCVCallBack)) {
			Toast.makeText(this, "Cannot connect to OpenCV Manager", Toast.LENGTH_LONG).show();
			finish();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == GET_IMAGE_REQUEST_CODE) {
			if (resultCode == RESULT_OK) {
				loadImage(data.getData());
				selectImageTextView_.setVisibility(View.GONE);
				imageView_.setVisibility(View.VISIBLE);
				doneButton_.setEnabled(true);
			} else if (resultCode == RESULT_CANCELED) {
				// User cancelled the image capture
			} else {
				// Image capture failed, advise user
			}
		}
	}

	private BaseLoaderCallback mOpenCVCallBack = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			if (status == LoaderCallbackInterface.SUCCESS) {
				EdgeTracerActivity.this.onManagerConnected();
			} else {
				super.onManagerConnected(status);
			}
		}
	};

	private class UpdateListener implements OnSeekBarChangeListener, OnCheckedChangeListener {
		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
		}

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			if (fromUser) {
				if (seekBar == lowSeekBar_) {
					if (highSeekBar_.getProgress() < progress) {
						highSeekBar_.setProgress(progress);
					}
				} else if (seekBar == highSeekBar_) {
					if (lowSeekBar_.getProgress() > progress) {
						lowSeekBar_.setProgress(progress);
					}
				}
				updateImage();
			}
		}

		@Override
		public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
			updateImage();
		}
	}

	private UpdateListener updateListener_ = new UpdateListener();

	private void onManagerConnected() {
		Log.i(TAG, "OpenCV loaded successfully");

		HNM1POS = ArrayToMat(new byte[][] { { 0, 0, 0 }, { 0, 1, 0 }, { 1, 1, 1 } });
		HNM1NEG = ArrayToMat(new byte[][] { { 1, 1, 1 }, { 0, 0, 0 }, { 0, 0, 0 } });
		HNM2POS = ArrayToMat(new byte[][] { { 0, 0, 0 }, { 1, 1, 0 }, { 0, 1, 0 } });
		HNM2NEG = ArrayToMat(new byte[][] { { 0, 1, 1 }, { 0, 0, 1 }, { 0, 0, 0 } });

		setContentView(R.layout.activity_edge_tracer);

		imageView_ = (ImageView) findViewById(R.id.image);
		imageView_.setOnClickListener(EdgeTracerActivity.this);
		
		selectImageTextView_ = (TextView) findViewById(R.id.select_image);
		selectImageTextView_.setOnClickListener(this);

		blurSeekBar_ = (SeekBar) findViewById(R.id.blur);
		blurSeekBar_.setOnSeekBarChangeListener(updateListener_);

		highSeekBar_ = (SeekBar) findViewById(R.id.high);
		highSeekBar_.setOnSeekBarChangeListener(updateListener_);

		lowSeekBar_ = (SeekBar) findViewById(R.id.low);
		lowSeekBar_.setOnSeekBarChangeListener(updateListener_);

		grayscaleCheckbox_ = (CheckBox) findViewById(R.id.grayscale);
		grayscaleCheckbox_.setOnCheckedChangeListener(updateListener_);

		mirrorCheckbox_ = (CheckBox) findViewById(R.id.mirror);
		mirrorCheckbox_.setOnCheckedChangeListener(updateListener_);

		doneButton_ = (Button) findViewById(R.id.done);
		doneButton_.setOnClickListener(this);
	}

	private void loadImage(Uri data) {
		try {
			InputStream stream;
			if (data.getScheme().startsWith("http")) {
				stream = new URL(data.toString()).openStream();
			} else {
				getContentResolver().openInputStream(data);
				stream = getContentResolver().openInputStream(data);
			}
			List<Byte> lb = new ArrayList<Byte>(1024);
			int b;
			while ((b = stream.read()) != -1) {
				lb.add((byte) b);
			}
			MatOfByte buf = new MatOfByte();
			buf.fromList(lb);
			srcImage_ = Imgcodecs.imdecode(buf, Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
			procImage_ = new Mat(); // new Mat(srcImage_.size(), CvType.CV_8UC3);
			edgesImage_ = new Mat();
			updateImage();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void updateImage() {
		if (srcImage_ != null) {
			// /////////////////////////////////////////////////////////////////////////
			// Mirror
			long start = System.currentTimeMillis();

			if (mirrorCheckbox_.isChecked()) {
				Core.flip(srcImage_, edgesImage_, 1);
			} else {
				srcImage_.copyTo(edgesImage_);
			}

			Log.v(TAG, "Mirror took: " + (System.currentTimeMillis() - start));

			// /////////////////////////////////////////////////////////////////////////
			// Gaussian blur
			start = System.currentTimeMillis();

			final double blur = blurSeekBar_.getProgress() / 100.0;
			if (blur > 0) {
				Imgproc.GaussianBlur(edgesImage_, edgesImage_, new Size(21, 21), blur);
			}

			// /////////////////////////////////////////////////////////////////////////
			// Color conversion
			start = System.currentTimeMillis();

			Log.v(TAG, "Blur took: " + (System.currentTimeMillis() - start));

			if (grayscaleCheckbox_.isChecked()) {
				Imgproc.cvtColor(edgesImage_, procImage_, Imgproc.COLOR_GRAY2RGB);
			} else {
				procImage_.setTo(new Scalar(255, 255, 255));
			}
			Log.v(TAG, "Color conversion took: " + (System.currentTimeMillis() - start));

			final float high = highSeekBar_.getProgress() / 10.0f;
			final float low = lowSeekBar_.getProgress() / 10.0f;

			// /////////////////////////////////////////////////////////////////////////
			// Canny edge detection
			start = System.currentTimeMillis();
			Imgproc.Canny(edgesImage_, edgesImage_, high, low);
			Imgproc.threshold(edgesImage_, edgesImage_, 127, 1, Imgproc.THRESH_BINARY);
			Log.v(TAG, "Canny took: " + (System.currentTimeMillis() - start));

			procImage_.setTo(new Scalar(255, 0, 0), edgesImage_);

			Bitmap bmp = Bitmap.createBitmap(procImage_.cols(), procImage_.rows(), Config.ARGB_8888);
			Utils.matToBitmap(procImage_, bmp);
			imageView_.setImageBitmap(bmp);
		}
	}

	@Override
	public void onClick(View view) {
		if (view == imageView_) {
			AlertDialog dialog = new AlertDialog.Builder(EdgeTracerActivity.this)
			.setTitle("Confirm Action")
			.setMessage("Are you sure you want to replace the image?")
			.setPositiveButton("YES",
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							selectImage();
						}
					})
			.setNegativeButton("NO", null)
			.create();
			dialog.show();
		} else if (view == selectImageTextView_) {
			selectImage();
		} else if (view == doneButton_) {
			done();
		}
	}
	
	private void selectImage() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("image/*");
		startActivityForResult(intent, GET_IMAGE_REQUEST_CODE);
	}

	private void done() {
		// /////////////////////////////////////////////////////////////////////////
		// Mirror
		if (mirrorCheckbox_.isChecked()) {
			Core.flip(srcImage_, srcImage_, 1);
		}
		// /////////////////////////////////////////////////////////////////////////
		// Thinning
		long start = System.currentTimeMillis();
		edgesImage_ = thin(edgesImage_);
		Log.v(TAG, "Thinning took: " + (System.currentTimeMillis() - start));

		Imgproc.cvtColor(srcImage_, procImage_, Imgproc.COLOR_GRAY2BGR);
		Mat lightGray = new Mat(procImage_.size(), CvType.CV_8UC3, new Scalar(192, 192, 192));
		Core.scaleAdd(procImage_, 0.25, lightGray, procImage_);
		procImage_.setTo(new Scalar(0, 0, 255), edgesImage_);

		try {
			// Write thumbnail file.
			File thumbnail = File.createTempFile("THUMB", ".jpg", getCacheDir());
			Imgcodecs.imwrite(thumbnail.getAbsolutePath(), procImage_);

			// Generate trace file.
			BinaryImage binImage = convert(edgesImage_);
			BinaryImageMultiCurve multiCurve = new BinaryImageMultiCurve(binImage, MIN_CURVE_PIXELS);
			File traceFile = File.createTempFile("TRACE", ".trc", getCacheDir());
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(traceFile));
			oos.writeObject(multiCurve);
			oos.close();

			Intent resultIntent = new Intent();
			resultIntent.setData(Uri.fromFile(traceFile));
			resultIntent.putExtra("thumbnail", Uri.fromFile(thumbnail));
			setResult(RESULT_OK, resultIntent);
		} catch (IOException e) {
			e.printStackTrace();
		}

		finish();
	}

	static Mat HNM1POS;
	static Mat HNM1NEG;
	static Mat HNM2POS;
	static Mat HNM2NEG;

	static Mat ArrayToMat(byte[][] data) {
		Mat mat = new Mat(data.length, data[0].length, CvType.CV_8UC1);
		for (int row = 0; row < mat.rows(); ++row) {
			for (int col = 0; col < mat.cols(); ++col) {
				mat.put(row, col, new byte[] { data[row][col] });
			}
		}
		return mat;
	}

	private static void rotateCCW(Mat mat) {
		Core.transpose(mat, mat);
		Core.flip(mat, mat, 0);
	}

    /**
     * Edge thinning, based on the awesome algorithm suggested here:
     * http://homepages.inf.ed.ac.uk/rbf/HIPR2/thin.htm
	 *
	 * PFZ: could not get ytai's orginal code to work, so trying this method:
	 * http://felix.abecassis.me/2011/09/opencv-morphological-skeleton/
     */
	private Mat thin(Mat image) {
		Mat skel = new Mat(image.size(), CvType.CV_8UC1, new Scalar(0));
		Mat temp = new Mat(image.size(), CvType.CV_8UC1);
		Mat element = Imgproc.getStructuringElement(Imgproc.MORPH_CROSS, new Size(3, 3));

		int prevnz = Core.countNonZero(image);
		Log.v(TAG, "Starting thinning. NZ=" + prevnz + " out of " + (image.rows()*image.cols()));

		int j = 0;
		while (true) {
			++j;
			Imgproc.morphologyEx(image, temp, Imgproc.MORPH_OPEN, element);
			Core.bitwise_not(temp, temp);
			Core.bitwise_and(image, temp, temp);
			Core.bitwise_or(skel, temp, skel);
			Imgproc.erode(image, image, element);

			int nz = Core.countNonZero(skel);
			Log.v(TAG, "Thinning after pass " + j + ". NZ=" + nz);
			if (nz == prevnz)
				break;
			prevnz = nz;
		}
		Log.v(TAG, "Thinning done. NZ=" + prevnz);
		return skel;
	}

	private static BinaryImage convert(Mat edges) {
		final int height = edges.rows();
		final int width = edges.cols();

		BinaryImage result = new BinaryImage(width, height);
		byte[] b = new byte[1];

		for (int y = 0; y < height; ++y) {
			for (int x = 0; x < width; ++x) {
				edges.get(y, x, b);
				result.set(x, y, b[0] != 0);
			}
		}
		return result;
	}
}
