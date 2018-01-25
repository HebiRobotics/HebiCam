HebiCam
======

Introduction
------------

HebiCam is a MATLAB class that supports live video acquisition from a variety of sources. It is similar in functionality to MATLAB's [IP Camera](http://se.mathworks.com/hardware-support/ip-camera.html) support package, but provides support for a wider range of formats. HebiCam uses [JavaCV](https://github.com/bytedeco/javacv), and thus supports all formats that are supported by OpenCV and FFMpeg, including h264 and mjpeg streams. USB cameras are supported on Windows and OSX.

Prerequisites
------------
HebiCam has been tested on Windows7/8, Ubuntu 16.04, and OSX 10.12 using MATLAB 2015a and 2016a. However, it should work on all MATLAB versions after 2009a. It does not require any particular Toolboxes.

Note that in our testing we found problems connecting to USB cameras on Ubuntu due to issues with Video4Linux2 drivers, so USB cameras are only supported on Windows and OSX.

Installation
------------
* Download the [latest release](https://github.com/HebiRobotics/HebiCam/releases)
* Extract the .zip file into a folder of your choice
* Add the unzipped files to the [MATLAB path](http://www.mathworks.com/help/matlab/ref/path.html)

## Warning: Problems when used with other Java libraries

Typically the underlying Java libraries are loaded automatically at the first call. However, there is a limitation in MATLAB that prevents Java libraries to be loaded once any Java object is instantiated. Thus, if you are using other Java libraries, e.g., [MatlabInput](https://github.com/HebiRobotics/MatlabInput) or the [Hebi API](http://hebirobotics.com/matlab), you will need to pre-load the libraries manually before using them.

```matlab
HebiCam.loadLibs();
```

Sample Usage
------------
Axis IP Camera (mjpeg)
```matlab
% Replace url with your device specific stream
url = 'http://<ip address>/mjpg/video.mjpg?resolution=640x480';
cam = HebiCam(url);
```

Axis IP Camera (h264) with forced grayscale
```matlab
% Replace url with your device specific stream
url = 'rtsp://<ip address>/axis-media/media.amp?videocodec=h264&resolution=640x480';
cam = HebiCam(url, 'ImageMode', 'Gray');
```

Windows USB Camera
```matlab
clear cam; % make sure device is not in use
cam = HebiCam(1);
imshow(cam.getsnapshot());
```

Acquisition of a single frame
```matlab
[image, frameNumber, timestamp] = getsnapshot(cam);
```

Live display of continuously acquired frames
```matlab
figure();
fig = imshow(getsnapshot(cam));
while true
    set(fig, 'CData', getsnapshot(cam)); 
    drawnow;
end
```

Sample Use Cases
------------
* [Teleop Taxi](https://youtu.be/zaPtxre4tFc) uses HebiCam to access video from an Android phone. The [IP Webcam](https://play.google.com/store/apps/details?id=com.pas.webcam&hl=en) Android App can be downloaded for free in the Play store.
* [ICRA Demo - 4DOF Arm + Vision](https://youtu.be/R0nQSxt8uic) uses HebiCam to simultaneously access streams of two Axis IP cameras, and MATLAB's computer vision toolbox for 3D stereo vision. The robot trajectories were calculated in the same MATLAB instance.

How it works
------------
The computationally intensive nature of video decoding can be a problem for languages like MATLAB, which limit users to a single thread. MATLAB does offer bindings for other languages (e.g. Java) that enable background threading, but converting high-resolution images back into a MATLAB readable format (e.g. byte[][][]) can be prohibitively expensive. This project gets around these limitations by using Java/C++ to acquire images, and shared memory to get these images into MATLAB.

This enables accessing high quality (1080p h264) video streams with almost no overhead (<50us) to the main MATLAB thread. However, in practice we usually use 640x480 resolution images for any actual computer vision tasks. Synchronization of shared memory is done via Java locks.

**Workflow**
* MATLAB creates a Java object, which launches a background thread for video acquisition
* The Java thread uses [JavaCV](https://github.com/bytedeco/javacv), which uses JNI to wrap the C++ bindings for OpenCV and FFMpeg, to establish the connection and continuously acquire images
* Acquired images get converted into MATLAB's column major format
* The converted bytes are written into shared memory
* MATLAB interprets the raw memory as an image, and copies it into a local variable
 
Installation from Source Code
------------
* Setup [Maven](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html)
* Run `mvn package`
* Copy the resulting *-all*.jar file and all *.m files into a directory on your MATLAB path
