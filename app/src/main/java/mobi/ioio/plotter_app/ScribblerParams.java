package mobi.ioio.plotter_app;

import android.content.Context;
import android.net.Uri;

/**
 * Created by pzingg on 5/8/16.
 */
public class ScribblerParams {
    enum Mode {
        Raster, Vector
    }

    public Context context_;
    public Uri uri_;
    public float blur_;
    public float threshold_;
    public Mode mode_;

    ScribblerParams(Context context, Uri uri, float blur, float threshold, Mode mode) {
        this.context_ = context;
        this.uri_ = uri;
        this.blur_ = blur;
        this.threshold_ = threshold;
        this.mode_ = mode;
    }
}
