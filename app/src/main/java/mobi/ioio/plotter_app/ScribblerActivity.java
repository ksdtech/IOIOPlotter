package mobi.ioio.plotter_app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
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
import org.opencv.core.Point;
import org.opencv.core.Rect;
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
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

import mobi.ioio.plotter.Plotter.MultiCurve;
import mobi.ioio.plotter.shapes.PointsCurve;
import mobi.ioio.plotter.shapes.SingleCurveMultiCurve;
import mobi.ioio.plotter_app.ScribblerParams.Mode;

public class ScribblerActivity extends Activity implements OnClickListener {
	Uri imageUri_;
	ImageView imageView_;
	TextView selectImageTextView_;
	TextView blurTextView_;
	TextView thresholdTextView_;
	TextView statusTextView_;
	SeekBar blurSeekBar_;
	SeekBar thresholdSeekBar_;
	CheckBox previewCheckbox_;
	CheckBox continuousCheckbox_;
	Button doneButton_;

	private ScribblerTask scribbler_;

	private float darkness_ = 1;
	private int numLines_ = 0;
	private static final int GET_IMAGE_REQUEST_CODE = 100;
	private boolean donePressed_ = false;

	private static final String TAG = "ScribblerActivity";

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
	protected void onDestroy() {
		if (scribbler_ != null) {
			scribbler_.cancel(true);
		}
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == GET_IMAGE_REQUEST_CODE) {
			if (resultCode == RESULT_OK) {
				cancelTask();

				if (scribbler_ != null) {
					scribbler_.cancel(true);
				}
				imageUri_ = data.getData();
				selectImageTextView_.setVisibility(View.GONE);
				imageView_.setVisibility(View.VISIBLE);

				startTask();

			} else if (resultCode == RESULT_CANCELED) {
				// User cancelled the image capture
			} else {
				// Image capture failed, advise user
			}
		}
	}

	private void startTask() {
		Log.v(TAG, "startTask");
		if (scribbler_ == null) {
			scribbler_ = new ScribblerTask();
		}
		AsyncTask.Status status = scribbler_.getStatus();
		if (status != AsyncTask.Status.FINISHED && status != AsyncTask.Status.RUNNING) {
			ScribblerParams params = new ScribblerParams(this, imageUri_,
					getBlur(), getThreshold(), getContinuous(), getMode());
			scribbler_.execute(params);
		} else {
			Log.e(TAG, "Error: Cannot start task - status is " + status.name());
		}
	}

	private void cancelTask() {
		Log.v(TAG, "cancelTask");
		if (scribbler_ != null) {
			scribbler_.cancel(true);
			scribbler_ = null;
		}
	}

	private Mode getMode() {
		return previewCheckbox_.isChecked() ? Mode.Vector : Mode.Raster;
	}

	private float getThreshold() {
		return thresholdSeekBar_.getProgress() / 100.f;
	}

	private float getBlur() {
		return blurSeekBar_.getProgress() / 10.f;
	}

	private boolean getContinuous() {
		return continuousCheckbox_.isChecked();
	}

	private BaseLoaderCallback mOpenCVCallBack = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			if (status == LoaderCallbackInterface.SUCCESS) {
				ScribblerActivity.this.onManagerConnected();
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
			if (seekBar == thresholdSeekBar_) {
				thresholdTextView_.setText(String.format("%.0f%%", getThreshold() * 100.f));
				if (scribbler_ != null) {
					scribbler_.cancel(true);
					startTask();
				}
			} else if (seekBar == blurSeekBar_) {
				blurTextView_.setText(String.format("%.1f", getBlur()));
				if (scribbler_ != null) {
					scribbler_.cancel(true);
					startTask();
				}
			}
		}

		@Override
		public void onCheckedChanged(CompoundButton button, boolean value) {
			if (scribbler_ != null) {
				if (button == previewCheckbox_) {
					scribbler_.cancel(true);
					startTask();
				}
			}
		}
	}

	private UpdateListener updateListener_ = new UpdateListener();

	private class ScribblerTask extends AsyncTask<ScribblerParams, ScribblerProgressData, ScribblerResult> {

		private static final String TAG = "ScribblerTask";

		private Context context_;
		private Uri uri_;
		private float blur_;
		private float threshold_;
		private boolean continuous_;
		private Mode mode_;

		// Internals
		private static final float PREVIEW_COLS = 450.f;
		private static final float GRAY_RESOLUTION = 128.f;
		private static final int NUM_ATTEMPTS = 100;
		private static final int MAX_LINES = 2000;
		private Random random_;
		private float thresholdScale_;
		private int scaledThreshold_;
		private Mat srcImage_;
		private Mat imageScaledToPreview_;
		private Mat imageResidue_;
		private Mat previewImage_;
		private float residualDarkness_;
		private SortedMap<Integer, Point> lines_ = new TreeMap<Integer, Point>();
		private boolean cancelled_;
		private boolean done_;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			random_ = new Random();
			srcImage_ = null;
			imageScaledToPreview_ = null;
			imageResidue_ = null;
			previewImage_ = null;
			residualDarkness_ = 0.f;
			lines_.clear();
			cancelled_ = false;
			done_ = false;
			thresholdScale_ = -10000.f;
			scaledThreshold_ = 0;
		}

		@Override
		protected void onProgressUpdate(ScribblerProgressData... values) {
			updateProgressUi(values[0].darkness_, values[0].numLines_);
			if (values[0].preview_ != null) {
				updatePreviewUi(values[0].preview_);
			}
		}

		@Override
		protected void onCancelled(ScribblerResult result) {
			cancelled_ = true;

			Log.v(TAG, "onCancelled");
			if (Build.VERSION.SDK_INT >= 11) {
				super.onCancelled(result);
			}
		}

		@Override
		protected void onPostExecute(ScribblerResult result) {
			Log.v(TAG, "onPostExecute");
			onScribblerResult(result.points_, result.bounds_, result.thumbnail_);
		}

		@Override
		protected ScribblerResult doInBackground(ScribblerParams... params) {
			context_ = params[0].context_;
			uri_ = params[0].uri_;
			blur_ = params[0].blur_;
			threshold_ = params[0].threshold_;
			continuous_ = params[0].continuous_;
			mode_ = params[0].mode_;

			done_ = false;
			cancelled_ = false;
			Log.v(TAG, "doInBackground blur=" + blur_ + ", threshold=" + threshold_ + ", mode=" + mode_.name());

			try {
				load();
			} catch (Exception e) {
				Log.e(TAG, "Error loading image");
				e.printStackTrace();
				done_ = true;
			}

			while (!done_ && !cancelled_) {
				try {
					done_ = step();
				} catch (InterruptedException e) {
				} catch (Exception e) {
					e.printStackTrace();
					done_ = true;
				}
			}

			Log.v(TAG, "doInBackground done");
			return getScribblerResult(blur_, threshold_);
		}

		private void load() throws MalformedURLException, IOException {
			InputStream stream = resolveUri(context_, uri_);
			Mat buf = streamToMat(stream);
			srcImage_ = Imgcodecs.imdecode(buf, Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);

			float scale = PREVIEW_COLS / srcImage_.cols();
			int newRows = Math.round(scale * srcImage_.rows());
			Size previewSize = new Size(PREVIEW_COLS, newRows);

			imageScaledToPreview_ = new Mat();
			Imgproc.resize(srcImage_, imageScaledToPreview_, previewSize, 0, 0,
					Imgproc.INTER_AREA);

			previewImage_ = null;
			imageResidue_ = null;
		}

		private boolean step() throws InterruptedException {
			boolean sameDarkness = false;
			boolean allLinesDirty = imageResidue_ == null;
			boolean needMoreLines = !cancelled_;
			if (needMoreLines && !allLinesDirty) {
				int numLines = lines_.size();
				if (numLines >= MAX_LINES) {
					Log.v(TAG, "step done! MAX_LINES " + MAX_LINES + " exceeded ( " + numLines + " )");
					needMoreLines = false;
				} else if (numLines > 0) {
					int lastKey = lines_.lastKey();
					if (lastKey > scaledThreshold_) {
						Log.v(TAG, "step done! threshold " + scaledThreshold_ + " exceeded ( " + lastKey + " )");
						needMoreLines = false;
					}
				}
			}
			boolean previewDirty = previewImage_ == null || (needMoreLines && mode_ == Mode.Vector);

			if (!cancelled_ && allLinesDirty) {
				initLines(blur_);
			}
			if (!cancelled_ && needMoreLines) {
				generateLine(blur_);
			}
			if (!cancelled_ && previewDirty) {
				renderPreview(blur_, threshold_, mode_);
			}
			if (!cancelled_ && (needMoreLines || previewDirty)) {
				publishProgress(new ScribblerProgressData(residualDarkness_, lines_.size(), previewImage_));
			}
			return !needMoreLines;
		}

		private ScribblerResult getScribblerResult(float blur, float threshold) {

			// Generate point array.
			Point[] points = null;
			Rect bounds = null;
			if (!lines_.isEmpty() && imageResidue_ != null) {
				points = (Point[]) lines_.values().toArray(new Point[0]);
				bounds = new Rect(0, 0, imageResidue_.cols(), imageResidue_.rows());
			}

			// Generate thumbnail.
			Bitmap bmp = null;
			if (previewImage_ != null && imageScaledToPreview_ != null) {
				renderPreview(blur, threshold, Mode.Vector);
				Mat thumbnail = new Mat(previewImage_.rows(), previewImage_.cols() * 2, CvType.CV_8U);

				imageScaledToPreview_.copyTo(thumbnail.colRange(0, previewImage_.cols()));
				previewImage_.copyTo(thumbnail.colRange(previewImage_.cols(), previewImage_.cols() * 2));
				bmp = Bitmap.createBitmap(thumbnail.cols(), thumbnail.rows(), Bitmap.Config.ARGB_8888);
				Utils.matToBitmap(thumbnail, bmp);
			}

			return new ScribblerResult(cancelled_, residualDarkness_, lines_.size(), points, bounds, bmp);
		}

		private int addPoint(Point p, float blur) {
			residualDarkness_ =  darkness(imageResidue_) / blur;
			int key = (int) Math.floor(residualDarkness_ * thresholdScale_);
			lines_.put(key, p);
			return key;
		}

		private void initLines(float blur) {
			// Resize to native resolution divided by blur factor.
			float scale = PREVIEW_COLS / blur / srcImage_.cols();
			int newCols = Math.round(PREVIEW_COLS / blur);
			int newRows = Math.round(scale * srcImage_.rows());
			Size s = new Size(newCols, newRows);

			Mat scaled = new Mat(s, srcImage_.type());
			Imgproc.resize(srcImage_, scaled, s, 0, 0, Imgproc.INTER_AREA);

			// Negate it.
			Mat dark = new Mat(s, scaled.type());
			Core.bitwise_not(scaled, dark);

			// Convert to S16.
			imageResidue_ = new Mat();
			dark.convertTo(imageResidue_, CvType.CV_16SC1);

			// Full scale is now blur * GRAY_RESOLUTION.
			double scaledBlur = blur * GRAY_RESOLUTION / 255.f;
			Mat zeros = Mat.zeros(s, imageResidue_.type());
			Core.scaleAdd(imageResidue_, scaledBlur, zeros, imageResidue_);

			residualDarkness_ = darkness(imageResidue_) / blur;
			thresholdScale_ = -10000.f / residualDarkness_;
			scaledThreshold_ = (int) Math.floor(threshold_ * thresholdScale_);
			Log.v(TAG, "initLines darkness=" + residualDarkness_ + ", thresholdScale_=" + thresholdScale_ +
				", scaledThreshold_=" + scaledThreshold_);

			// Clear map.
			lines_.clear();
		}

		private int generateLine(float blur) {
			Point[] line = null;
			int key;
			if (lines_.isEmpty() || !continuous_) {
				line = nextLine(imageResidue_, NUM_ATTEMPTS, null);
				addPoint(line[0], blur);
				key = addPoint(line[1], blur);
			} else {
				Point startPoint = lines_.get(lines_.lastKey());
				line = nextLine(imageResidue_, NUM_ATTEMPTS, startPoint);
				key = addPoint(line[1], blur);
			}
			Log.v(TAG, "generateLine [" + line[0].toString() + " - " + line[1].toString() + "] (" + key + ")");
			return key;
		}

		private void renderPreview(float blur, float threshold, Mode mode) {
			if (imageScaledToPreview_ == null) {
				Log.v(TAG, "renderPreview: no image to render");
			} else {
				if (previewImage_ == null) {
					previewImage_ = new Mat(imageScaledToPreview_.size(), CvType.CV_8UC1);
				}

				if (mode == Mode.Raster) {
					// Gaussian blur
					if (blur > 0) {
						Imgproc.GaussianBlur(imageScaledToPreview_, previewImage_, new Size(), blur);
					} else {
						imageScaledToPreview_.assignTo(previewImage_);
					}

					// Simulate threshold
					Mat add = new Mat(previewImage_.size(), previewImage_.type());
					add.setTo(new Scalar(threshold * 255));
					Core.scaleAdd(previewImage_, 1, add, previewImage_);
				} else {
					previewImage_.setTo(new Scalar(255));
					final Scalar black = new Scalar(0);
					Point prevPoint = null;
					int i = 0;
					for (Map.Entry<Integer, Point> e : lines_.entrySet()) {
						int key = e.getKey();
						if (key > scaledThreshold_) {
							break;
						}
						if (i == 0) {
							prevPoint = scalePoint(e.getValue(), blur);
							i = 1;
						} else {
							Point p = scalePoint(e.getValue(), blur);
							Imgproc.line(previewImage_, prevPoint, p, black);
							if (continuous_) {
								prevPoint = p;
							} else {
								i = 0;
							}
						}
					}
				}
			}
		}

		private Point scalePoint(Point p, float scale) {
			return new Point(p.x * scale, p.y * scale);
		}

		private InputStream resolveUri(Context context, Uri uri) throws IOException,
				MalformedURLException, FileNotFoundException {
			InputStream stream;
			if (uri.getScheme().startsWith("http")) {
				stream = new java.net.URL(uri.toString()).openStream();
			} else {
				stream = context.getContentResolver().openInputStream(uri);
			}
			return stream;
		}

		private Mat streamToMat(InputStream stream) throws IOException {
			byte[] data = new byte[1024];
			MatOfByte chunk = new MatOfByte();
			MatOfByte buf = new MatOfByte();
			int read;
			while ((read = stream.read(data)) > 0) {
				chunk.fromArray(data);
				Mat subchunk = chunk.submat(0, read, 0, 1);
				buf.push_back(subchunk);
			}
			return buf;
		}

		/**
		 * Gets the best of several random lines.
		 *
		 * The number of candidates is determined by the numAttempts argument. The criterion for
		 * determining the winner is the one which covers the highest average darkness in the image. As
		 * a side-effect, the winner will be subtracted from the image.
		 *
		 * @param image
		 *            The image to approximate. Expected to be of floating point format, with higher
		 *            values representing darker areas. Should be scaled such that subtracting a value
		 *            of GRAY_RESOLUTION from a pixel corresponds to how much darkness a line going
		 *            through it adds. When the method returns, the winning line will be subtracted from
		 *            this image.
		 * @param numAttempts
		 *            How many candidates to examine.
		 * @param startPoint
		 *            Possibly, force the line to start at a certain point. In case of null, the line
		 *            will comprise two random point.
		 * @return The optimal line.
		 */
		private Point[] nextLine(Mat image, int numAttempts, Point startPoint) {
			Mat mask = new Mat(image.size(), CvType.CV_8UC1);
			Point[] line = new Point[2];
			Point[] bestLine = null;
			Scalar gray = new Scalar(GRAY_RESOLUTION);
			double bestScore = Float.NEGATIVE_INFINITY;
			List<Point> points = generateRandomPoints(image.size(), startPoint, numAttempts);
			for (int i = 1; i < points.size(); ++i) {
				if (i == 1 || startPoint == null) {
					line[0] = points.get(i-1);
				}
				line[1] = points.get(i);

				mask.setTo(new Scalar(0));
				Imgproc.line(mask, line[0], line[1], gray);

				double score = Core.mean(image, mask).val[0];
				if (score > bestScore) {
					bestScore = score;
					bestLine = line.clone();
				}
			}
			if (bestLine != null) {
				// Core.subtract seems seriously broken, so we use scaleAdd with negative mask
				Mat subt = Mat.zeros(image.size(), image.type());
				Imgproc.line(subt, bestLine[0], bestLine[1], new Scalar(-GRAY_RESOLUTION));

				Mat result = new Mat(image.size(), image.type());
				Core.scaleAdd(image, 1, subt, result);

				// Copy to imageResidue_, removing negative results
				// Core.max also seriously broken
				short[] v = { 0 };
				for (int i = result.rows()-1; i >= 0; --i) {
					for (int j = result.cols()-1; j >= 0; --j) {
						result.get(i, j, v);
						if (v[0] < 0) {
							v[0] = 0;
						}
						imageResidue_.put(i, j, v);
					}
				}
			}
			return bestLine;
		}

		private List<Point> generateRandomPoints(Size s, Point startPoint, int numPoints) {
			List<Point> list = new ArrayList<Point>();
			if (numPoints >= s.area()) {
				numPoints = (int) s.area()-1;
			}
			while (list.size() < numPoints) {
				int col = (int) Math.round(random_.nextDouble() * s.width);
				int row = (int) Math.round(random_.nextDouble() * s.height);
				Point p = new Point(col, row);
				if ((startPoint == null || !p.equals(startPoint)) && !list.contains(p)) {
					list.add(p);
				}
			}
			Collections.shuffle(list);
			if (startPoint != null) {
				list.add(0, startPoint);
			}
			return list;
		}

		private float darkness(Mat in) {
			double total = Core.sumElems(in).val[0];
			return (float) (total / in.cols() / in.rows() / GRAY_RESOLUTION);
		}
	}

	private void updatePreviewUi(Mat image) {
		Bitmap previewBitmap = Bitmap.createBitmap(image.cols(), image.rows(),
				Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(image, previewBitmap);
		imageView_.setImageBitmap(previewBitmap);
	}

	private void updateProgressUi(float darkness, int numLines) {
		darkness_ = darkness;
		numLines_ = numLines;
		doneButton_.setEnabled(!donePressed_ && darkness < getThreshold());
		statusTextView_.setText(String.format("%.0f%% (%d)", darkness * 100, numLines));
	}

	private void onManagerConnected() {
		Log.i(TAG, "OpenCV loaded successfully");

		setContentView(R.layout.activity_scribbler);

		imageView_ = (ImageView) findViewById(R.id.image);
		imageView_.setOnClickListener(ScribblerActivity.this);

		selectImageTextView_ = (TextView) findViewById(R.id.select_image);
		selectImageTextView_.setOnClickListener(this);

		blurSeekBar_ = (SeekBar) findViewById(R.id.blur);
		blurSeekBar_.setProgress(50);
		blurSeekBar_.setOnSeekBarChangeListener(updateListener_);

		thresholdSeekBar_ = (SeekBar) findViewById(R.id.threshold);
		thresholdSeekBar_.setProgress(20);
		thresholdSeekBar_.setOnSeekBarChangeListener(updateListener_);

		blurTextView_ = (TextView) findViewById(R.id.blurText);
		thresholdTextView_ = (TextView) findViewById(R.id.thresholdText);
		statusTextView_ = (TextView) findViewById(R.id.status);

		previewCheckbox_ = (CheckBox) findViewById(R.id.preview);
		previewCheckbox_.setChecked(false);
		previewCheckbox_.setOnCheckedChangeListener(updateListener_);

		continuousCheckbox_ = (CheckBox) findViewById(R.id.continuous);
		continuousCheckbox_.setChecked(true);

		doneButton_ = (Button) findViewById(R.id.done);
		doneButton_.setOnClickListener(this);

	}

	@Override
	public void onClick(View view) {
		if (view == imageView_) {
			AlertDialog dialog = new AlertDialog.Builder(ScribblerActivity.this)
					.setTitle("Confirm Action")
					.setMessage("Are you sure you want to replace the image?")
					.setPositiveButton("YES", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							doneButton_.setEnabled(false);
							selectImage();
						}
					}).setNegativeButton("NO", null).create();
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

	private void onScribblerResult(Point[] points, Rect bounds, Bitmap thumbnail) {
		try {
			Intent resultIntent = new Intent();
			if (thumbnail != null) {
				// Write thumbnail file.
				File thumbnailFile = File.createTempFile("THUMB", ".png", getCacheDir());
				thumbnail.compress(CompressFormat.PNG, 100, new FileOutputStream(thumbnailFile));
				resultIntent.putExtra("thumbnail", Uri.fromFile(thumbnailFile));
			}
			if (points != null && points.length > 0 && bounds != null) {
				// Generate trace file.
				MultiCurve multiCurve = new SingleCurveMultiCurve(new PointsCurve(points),
						getBounds(bounds));
				File traceFile = File.createTempFile("TRACE", ".trc", getCacheDir());
				ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(traceFile));
				oos.writeObject(multiCurve);
				oos.close();
				resultIntent.setData(Uri.fromFile(traceFile));
			}
			setResult(RESULT_OK, resultIntent);
		} catch (IOException e) {
			e.printStackTrace();
		}
		finish();
	}

	private static float[] getBounds(Rect bounds) {
		return new float[] { bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height };
	}

	private void done() {
		donePressed_ = true;
		doneButton_.setEnabled(false);
		blurSeekBar_.setEnabled(false);
		thresholdSeekBar_.setEnabled(false);
		previewCheckbox_.setEnabled(false);
		imageView_.setEnabled(false);
	}
}
