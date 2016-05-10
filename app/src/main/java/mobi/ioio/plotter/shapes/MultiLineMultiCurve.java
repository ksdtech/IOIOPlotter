package mobi.ioio.plotter.shapes;

import java.io.Serializable;

import mobi.ioio.plotter.CurvePlotter.Curve;
import mobi.ioio.plotter.Plotter.MultiCurve;

/**
 * Created by pzingg on 5/10/16.
 */
public class MultiLineMultiCurve implements MultiCurve, Serializable {
    private static final long serialVersionUID = -8271726700104333266L;
    private final float[] bounds_;
    private final Curve[] curves_;
    private int i_;

    public MultiLineMultiCurve(Curve[] curves, float[] bounds) {
        bounds_ = bounds;
        curves_ = curves;
        i_ = 0;
    }

    @Override
    public Curve nextCurve() {
        Curve curve = null;
        if (i_ < curves_.length) {
            curve = curves_[i_];
            i_++;
        }
        return curve;
    }

    @Override
    public float[] getBounds() {
        return bounds_;
    }
}
