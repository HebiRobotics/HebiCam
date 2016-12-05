classdef HebiCam < handle
    % HebiCam acquires frames from streaming video sources
    %   cam = HebiCam(uri) returns an object that acquires images
    %   from the specified URI. The URI can be the URL of an IP camera, the
    %   the location of a local device (e.g. '/dev/video0'), or the number
    %   of a local device (e.g. 1).
    %
    %   Possible sources are limited to devices that are supported by 
    %   FFMpeg or OpenCV.
    %
    %   Note that the underlying Java implementation currently only 
    %   supports RGB images.
    % 
    % HebiCam Properties:
    %    url      - video source, e.g., local device or remote ip camera
    %    width    - width of the gathered image
    %    height   - height of the gathered image
    %    channels - channel, e.g., rgb or grayscale
    %
    % HebiCam Methods:
    %    getsnapshot - acquires a single image frame
    %
    %    Example:
    %       % Connect to a device, e.g., an Axis MJPG stream
    %       url = 'http://<ip>/mjpg/video.mjpg?resolution=640x480';
    %       cam = HebiCam(url);
    %       
    %       % Live display of continuously acquired frames
    %       figure();
    %       fig = imshow(getsnapshot(cam));
    %       while true
    %           set(fig, 'CData', getsnapshot(cam)); 
    %           drawnow;
    %       end
    
    % Copyright (c) 2015-2016 HEBI Robotics
    
    properties (SetAccess = private, GetAccess = public)
        url % video source, e.g., local device or remote ip camera
        width % width of the gathered image
        height % height of the gathered image
        channels % channel, e.g., rgb vs grayscale)
    end
    
    properties (Access = private)
        file
        cam
    end
    
    methods (Static, Access = public)
        function loadLibs()
            % Loads the backing Java files and native binaries. This 
            % method assumes that the jar file is located in the same 
            % directory as this class-script, and that the file name 
            % matches the string below.
            jarFileName = 'hebicam-1.1-SNAPSHOT-all-x86_64.jar';

            % Load only once
            if ~exist('us.hebi.matlab.streaming.BackgroundFrameGrabber','class')
                javaaddpath(...
                    fullfile(fileparts(mfilename('fullpath')), jarFileName));
            end
        end
    end
    
    methods (Access = public)
        
        function this = HebiCam(uri)
            % constructor - connects to the video source
            this.url = uri;
            HebiCam.loadLibs();

            % Create an appropriate frame grabber for the requested location
            loc = us.hebi.matlab.streaming.DeviceLocation(uri);
            if loc.isNumber() % 1, 2, 3, etc.
                
                 % Java uses zero based indexing
                javaIndex = uri-1;
                grabber = org.bytedeco.javacv.OpenCVFrameGrabber(javaIndex);

            elseif loc.isUrl() % http://<ip>/mjpeg/, rtsp://...
                % Some grabbers have issues if the url is valid, but the 
                % device is not reachable, e.g., not turned on. This could
                % result in MATLAB hanging forever, so we need to check 
                % whether the device is actually on the network.
                timeoutMs = 5000;
                if ~loc.isReachableUrl(timeoutMs)
                    error('remote url is not reachable');
                end
                
                % Create Grabber
                grabber = org.bytedeco.javacv.FFmpegFrameGrabber(uri);
                
                % Set 'low-latency' options for RTSP
                % see https://www.ffmpeg.org/ffmpeg-protocols.html#rtp
                grabber.setOption('rtsp_transport', 'udp');
                grabber.setOption('max_delay', '0'); % disable reordering delay
                grabber.setOption('reorder_queue_size', '1');
                
                % Sometimes mjpeg sources complain when the format is not
                % set. For now we assume that http:// urls are mjpeg
                % streams, which has been true for all ip cameras that I've
                % tested so far. However, this may need to be adapted for
                % some more exotic cameras.
                if loc.hasUrlScheme('http')
                    grabber.setFormat('mjpeg');
                end
                
            else
                % file descriptor, e.g., /dev/usb0
                grabber = org.bytedeco.javacv.OpenCVFrameGrabber(uri);
            end

            % Set a timeout in case a camera gets disconnected or shutdown. 
            % Note that this only works for grabbing frames and not at 
            % start.
            grabber.setTimeout(1000); % [ms]
            
            % Set log level (ffmpeg only?)
            logLevel = org.bytedeco.javacpp.avutil.AV_LOG_FATAL;
            org.bytedeco.javacpp.avutil.av_log_set_level(logLevel);
            
            % Force color mode if applicable (make it an optional parameter)
%             enumClass = 'org.bytedeco.javacv.FrameGrabber$ImageMode';
%             mode = javaMethod('valueOf', enumClass, 'COLOR'); % color image
%             mode = javaMethod('valueOf', enumClass, 'GRAY'); % grayscale
%             grabber.setImageMode(mode);

            % Create a Java background thread for the FrameGrabber
            this.cam = us.hebi.matlab.streaming.BackgroundFrameGrabber(grabber);
            
            % Get image data and shared memory location
            this.height = this.cam.getHeight();
            this.width = this.cam.getWidth();
            this.channels = this.cam.getChannels();
            path = char(this.cam.getBackingFile());
            
            % Some versions have problems with mapping HxWx1, so we special
            % case grayscale images.
            pixelFormat = [this.height this.width this.channels];
            if this.channels == 1 % grayscale
               pixelFormat(3) = []; 
            end

            % Map memory to data
            this.file = memmapfile(path, 'Format', { ...
                'uint64' 1 'frame';
                'double' 1 'timestamp';
                'uint8' pixelFormat 'pixels';
                }, 'Repeat', 1);
            
            % start retrieval
            start(this.cam);
        end
        
        function [I,frame,timestamp] = getsnapshot(this)
            %getsnapshot - acquires a single image frame
            hasImage = tryGetNextImageLock(this.cam);
            if hasImage
                % Mapped memory is accessed by reference, so the data
                % needs to be copied manually.
                data = this.file.Data;
                I = data.pixels * 1;
                frame = data.frame * 1;
                timestamp = data.timestamp * 1;
                tryReleaseImageLock(this.cam);
            else
                stop(this.cam);
                error('Connection to video source was lost. Acquisition stopped.');
            end
        end
        
    end
    
     methods (Access = private)
        function delete(this)
            % destructor - frees resources
            this.file = [];
            stop(this.cam);
        end
    end
end