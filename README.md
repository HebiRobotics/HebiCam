HebiCam
======

Introduction
------------

HebiCam is a MATLAB class that supports live video acquisition from a variety of sources. It is similar in functionality to MATLAB's [IP Camera](http://se.mathworks.com/hardware-support/ip-camera.html) support package, but provides support for a wider range of formats. HebiCam uses [JavaCV](https://github.com/bytedeco/javacv), and thus supports all formats that are supported by OpenCV and FFMpeg, including h264 and mjpeg streams. USB cameras are supported as well.

Prerequisites
------------
HebiCam has been tested on Windows7/8, Ubuntu 14.04, and OSX Yosemite using MATLAB 2014a and 2015a. However, it should work on all MATLAB versions after 2009a. It does not require any particular Tooboxes.

Sample Usage
------------
```matlab
%% Connect to a device, e.g., an Axis MJPG stream
url = 'http://<ip address>/mjpg/video.mjpg?resolution=640x480';
cam = HebiCam(url);

%% Live display of continuously acquired frames
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
The computationally intensive nature of video decoding can be a problem for languages like MATLAB, which limit users to a single thread. MATLAB does offer bindings for other languages (e.g. Java) that enable background threading, but getting high-resolution images back into MATLAB can be prohibitively expensive. This project aims to get around this limitation by using Java/C++ to acquire images, and shared memory to get these images into MATLAB. Synchronization of shared memory is achieved via Java locks.

**Workflow**
* MATLAB creates a Java object, which launches a background thread for video acquisition
* The Java thread uses [JavaCV](https://github.com/bytedeco/javacv), which uses JNI to wrap the C++ bindings for OpenCV and FFMpeg, to establish the connection and continuously acquire images
* Acquired images get converted into MATLAB's column major format
* Converted images are written into shared memory
* MATLAB copies the image from shared memory into a local variable
 
This enables accessing high quality (1080p h264) video streams with almost no overhead (<50us) to the main MATLAB thread. However, in practice we usually use 640x480 resolution images for any actual computer vision tasks.

Installation from Source
------------
* Setup [Maven](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html)
* Run `mvn package`
* Copy the resulting *-all*.jar file and all *.m files into a directory on your MATLAB path
