<img src="http://3.bp.blogspot.com/-XpjHCbZss_Y/UlY1DuhxPSI/AAAAAAAAvDw/VhE8iImcELM/s1600/DSC00310.JPG"/>

IOIOPlotter
===========

Source code for my IOIO Plotter. The design is a simple easel-mounted wall-style plotter.
A marker is suspended by two strings, each is rolled on spool, which is driven by a stepper motor.
The entire setup is controlled by an Android tablet through a IOIO board.
The app in this repo allows converting photos (from the camera or gallery) into artistic vector drawings and immediately
execute them on the plotter.

Read more and see the plotter in action on my blog:<br>
http://ytai-mer.blogspot.com/2013/05/ioio-plotter-and-motor-control-library.html<br>
http://ytai-mer.blogspot.com/2013/11/how-i-became-artist.html


PFZ Build Notes
---------------
Forked the original Eclipse ADT repository from https://github.com/ytai/IOIOPlotter and 
imported into Android Studio 2.1.

Imported IOIOLibCore and IOIOLibAndroid modules from https://github.com/ytai/ioio

Installed OpenCV for Android Studio: http://stackoverflow.com/questions/27406303/opencv-in-android-studio<br>
1. Download latest OpenCV SDK for Android from http://opencv.org/downloads.html and decompress the zip file.
2. Import OpenCV to Android Studio, From File > New > Import Module, choose sdk/java folder in the unzipped OpenCV archive.
3. Update build.gradle under imported OpenCV module to update 4 fields to match your project build.gradle a) compileSdkVersion b) buildToolsVersion c) minSdkVersion and d) targetSdkVersion.
4. Add module dependency by Application > Module Settings, and select the Dependencies tab. Click + icon at bottom, choose Module Dependency and select the imported OpenCV module.
5. For Android Studio v1.2.2, to access to Module Settings : in the project view, right-click the dependent module, then Open Module Settings
6. Copy libs folder under sdk/native to Android Studio under app/src/main.
7. In Android Studio, rename the copied libs directory to jniLibs and we are done.

Modified IOIOPlotter source code for OpenCV 3.x compatibility (Core, Imgproc, Imgcodecs and Highgui package changes).


Build and Run Issues
--------------------

I got this when compiling:

    Note: IOIOPlotter/app/src/main/java/mobi/ioio/plotter_app/PlotterService.java uses or overrides a deprecated API.
    Note: Recompile with -Xlint:deprecation for details.
    
Added "Deprecated API usage" at "Error" level to File > Other Settings > Default Settings... > Editor > Inspections > Java > "Code maturity issues".
Then ran Analyze > Inspect Code...  In Inspection window, found deprecated code for creating and setting Notifications in PlotterService.java.

I also got this in the Gradle Console when attempting to run the app on a tablet:

    To run dex in process, the Gradle daemon needs a larger heap.
    It currently has approximately 910 MB.
    For faster builds, increase the maximum heap size for the Gradle daemon to more than 2048 MB.
    To do this set org.gradle.jvmargs=-Xmx2048M in the project gradle.properties.
    For more information see https://docs.gradle.org/current/userguide/build_environment.html
    
I put this property into ~/.gradle/gradle.properties (to apply to all gradle builds, not just AS).

On Android Studio 2.1 with Java 8 or above, got 5 of these errors when attempting to run the app on a tablet:

    Error converting bytecode to dex:
    Cause: Dex cannot parse version 52 byte code.
    This is caused by library dependencies that have been compiled using Java 8 or above.
    If you are using the 'java' gradle plugin in a library submodule add 
    targetCompatibility = '1.7'
    sourceCompatibility = '1.7'
    to that submodule's build.gradle file.
    
    :app:transformClassesWithDexForDebug FAILED

I used File -> Project Structure in Android Studio to set these to 1.7 in all Android subprojects.
The IOIOLibCore was the submodule using the 'java' plugin (not 'com.android.library'). For that 
one I just added the two lines suggested to the build.gradle file.


App Runtime Issues
------------------

The app launches with a landing screen that says "Click here to select path".  When clicked
it gives you a choice of "Edge Tracer" or "Scribbler". Choosing either gets you a 
"Cannot connect to OpenCV Manager". And this happens:

    mobi.ioio.plotter_app E/WindowManager: android.view.WindowLeaked: 
    Activity mobi.ioio.plotter_app.ScribblerActivity has leaked window 
    com.android.internal.policy.impl.PhoneWindow$DecorView{1f727f3a V.E..... R.....I. 0,0-772,268} 
    that was originally added here
    ...
    at android.app.Dialog.show
    ...
    at org.opencv.android.OpenCVLoader.initAsync(OpenCVLoader.java:100)
    
Fixed by installing "OpenCV Manager" app in the Google Play Store (d'oh!). And made
sure to change the versions requested in EdgeTracerActivity.java and ScribblerActivity.java
to OPENCV_VERSION_3_0_0, so it wouldn't try to load the 2.4 versions (different API).

Then the Edge Tracer UI works until you click "Done". Then, app stops. Debugger shows:

    mobi.ioio.plotter_app V/EdgeTracerActivity: Starting thinning. NZ=15153
    mobi.ioio.plotter_app E/cv::error(): OpenCV Error: Assertion failed (m.dims >= 2) in 
        cv::Mat::Mat(const cv::Mat&, const cv::Range&, const cv::Range&), 
        file /home/maksim/workspace/android-pack/opencv/modules/core/src/matrix.cpp, line 441
    mobi.ioio.plotter_app E/org.opencv.imgproc: imgproc::erode_12() caught cv::Exception: 
        /home/maksim/workspace/android-pack/opencv/modules/core/src/matrix.cpp:441: 
        error: (-215) m.dims >= 2 in function cv::Mat::Mat(const cv::Mat&, const cv::Range&, const cv::Range&)
    at org.opencv.imgproc.Imgproc.erode(Imgproc.java:2007)
    at org.opencv.imgproc.Imgproc.erode_2(Native Method)
    at mobi.ioio.plotter_app.EdgeTracerActivity.HitAndMiss
    
Fixed this by setting the size of the dst matrix used in the HitAndMiss function.
But thinning doesn't seem to work--instead it actually selects all pixels in the
image. I "fixed" the thin() function by changing the algorithm to a different one 
I found on the web.

Scribbler bombs during image processing with:

    mobi.ioio.plotter_app E/cv::error(): OpenCV Error: Assertion failed ((mtype == CV_8UC1 || mtype == CV_8SC1) && _mask.sameSize(*psrc1)) in void cv::arithm_op(cv::InputArray, cv::InputArray, cv::OutputArray, cv::InputArray, int, void (**)(const uchar*, size_t, const uchar*, size_t, uchar*, size_t, cv::Size, void*), bool, void*, int), file /home/maksim/workspace/android-pack/opencv/modules/core/src/arithm.cpp, line 2043
    mobi.ioio.plotter_app E/org.opencv.core: core::subtract_10() caught cv::Exception: /home/maksim/workspace/android-pack/opencv/modules/core/src/arithm.cpp:2043: error: (-215) (mtype == CV_8UC1 || mtype == CV_8SC1) && _mask.sameSize(*psrc1) in function void cv::arithm_op(cv::InputArray, cv::InputArray, cv::OutputArray, cv::InputArray, int, void (**)(const uchar*, size_t, const uchar*, size_t, uchar*, size_t, cv::Size, void*), bool, void*, int)
    mobi.ioio.plotter_app W/System.err: CvException [org.opencv.core.CvException: cv::Exception: /home/maksim/workspace/android-pack/opencv/modules/core/src/arithm.cpp:2043: error: (-215) (mtype == CV_8UC1 || mtype == CV_8SC1) && _mask.sameSize(*psrc1) in function void cv::arithm_op(cv::InputArray, cv::InputArray, cv::OutputArray, cv::InputArray, int, void (**)(const uchar*, size_t, const uchar*, size_t, uchar*, size_t, cv::Size, void*), bool, void*, int)
    mobi.ioio.plotter_app W/System.err: ]
    mobi.ioio.plotter_app W/System.err:     at org.opencv.core.Core.subtract_0(Native Method)
    mobi.ioio.plotter_app W/System.err:     at org.opencv.core.Core.subtract(Core.java:2065)
    mobi.ioio.plotter_app W/System.err:     at mobi.ioio.plotter_app.Scribbler.nextLine(Scribbler.java:378)

In OpenCV 3.0.0 source code that assertion is:

    const _InputArray *psrc = &_src1;
    int mtype = _mask.type();
    CV_Assert( (mtype == CV_8UC1 || mtype == CV_8SC1) && _mask.sameSize(*psrc1) );
    
_src1 is the 1st and _mask is the 4th parameter passed to arithm_op and also to
cv::stubtract.

Edge Tracer activity bombs after processing:

    mobi.ioio.plotter_app A/libc: Fatal signal 11 (SIGSEGV), code 1, fault addr 0x10 in tid 10336 (Thread-2181)
    mobi.ioio.plotter_app W/ResourcesManager: Asset path '/system/framework/com.android.future.usb.accessory.jar' does not exist or contains no resources.

Opened SDK Manager with Tools > Android > SDK Manager, and added Google API
libraries. Also added <uses-feature> element to the manifest. Maybe this is necessary: 
http://jeffreysambells.com/2011/05/15/identifying-your-android-usb-accessory
