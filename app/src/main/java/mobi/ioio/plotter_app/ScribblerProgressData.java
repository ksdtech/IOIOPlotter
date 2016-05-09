package mobi.ioio.plotter_app;

import org.opencv.core.Mat;

/**
 * Created by pzingg on 5/8/16.
 */
public class ScribblerProgressData {
    public float darkness_;
    public int numLines_;
    public Mat preview_;

    ScribblerProgressData(float darkness, int numLines, Mat preview) {
        this.darkness_ = darkness;
        this.numLines_ = numLines;
        if (preview != null) {
            this.preview_ = preview.clone();
        } else {
            this.preview_ = null;
        }
    }
}
