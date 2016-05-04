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


PFZ Notes
---------
Forked the original Eclipse ADT repository from https://github.com/ytai/IOIOPlotter and 
imported into Android Studio 2.1.

Imported IOIOLibCore and IOIOLibAndroid modules from https://github.com/ytai/ioio

Installed OpenCV for Android Studio: http://stackoverflow.com/questions/27406303/opencv-in-android-studio<br>
1. Download latest OpenCV SDK for Android from http://opencv.org/downloads.html and decompress the zip file.
2. Import OpenCV to Android Studio, From File -> New -> Import Module, choose sdk/java folder in the unzipped OpenCV archive.
3. Update build.gradle under imported OpenCV module to update 4 fields to match your project build.gradle a) compileSdkVersion b) buildToolsVersion c) minSdkVersion and d) targetSdkVersion.
4. Add module dependency by Application -> Module Settings, and select the Dependencies tab. Click + icon at bottom, choose Module Dependency and select the imported OpenCV module.
5. For Android Studio v1.2.2, to access to Module Settings : in the project view, right-click the dependent module -> Open Module Settings
6. Copy libs folder under sdk/native to Android Studio under app/src/main.
7. In Android Studio, rename the copied libs directory to jniLibs and we are done.
