package mobi.ioio.plotter_app;

import android.graphics.Bitmap;

import org.opencv.core.Point;
import org.opencv.core.Rect;

/**
 * Created by pzingg on 5/8/16.
 */
public class ScribblerResult {
    public boolean stopped_;
    public float residualDarkness_;
    public int lineCount_;
    public Point[] points_;
    public Rect bounds_;
    public Bitmap thumbnail_;

    ScribblerResult(boolean stopped, float residualDarkness, int lineCount, Point[] points, Rect bounds, Bitmap thumbnail) {
        this.stopped_ = stopped;
        this.residualDarkness_ = residualDarkness;
        this.lineCount_ = lineCount;
        this.points_ = points;
        this.bounds_ = bounds;
        this.thumbnail_ = thumbnail;
    }
}
