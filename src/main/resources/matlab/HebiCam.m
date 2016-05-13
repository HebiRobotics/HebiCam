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
    
    methods (Static, Access = private)
        function load_resources()
            % Loads the backing Java files and native binaries. This 
            % method assumes that the jar file is located in the same 
            % directory as this class-script, and that the file name 
            % matches the string below.
            jarFileName = 'hebicam-1.0-SNAPSHOT-all-x86_64.jar';

            % Load only once
            if ~exist('us.hebi.matlab.streaming.BackingHebiCam','class')
                javaaddpath(...
                    fullfile(fileparts(mfilename('fullpath')), jarFileName));
            end
        end
    end
    
    methods (Access = public)
        
        function this = HebiCam(url)
            % constructor - connects to the video source
            this.url = url;
            HebiCam.load_resources();
            this.cam = us.hebi.matlab.streaming.BackingHebiCam.open(url);
            
            % Get image data and shared memory location
            this.height = this.cam.getHeight();
            this.width = this.cam.getWidth();
            this.channels = this.cam.getChannels();
            path = char(this.cam.getBackingFile());
            
            % Somehow HxWx1 does not work, so the mapping of
            % greyscale images needs to be special cased.
            if(this.channels == 1)
                % greyscale
                this.file = memmapfile(path, 'Format', ...
                    {'uint8' [this.height this.width] 'pixels';});
            else
                % color image
                this.file = memmapfile(path, 'Format', ...
                    {'uint8' [this.height this.width this.channels] 'pixels';});
            end
            
            % start retrieval
            start(this.cam);
        end
        
        function I = getsnapshot(this)
            %getsnapshot - acquires a single image frame
            hasImage = tryGetNextImageLock(this.cam);
            if hasImage
                % Mapped memory is accessed by reference, so the data
                % needs to be copied manually.
                I = this.file.Data.pixels * 1;
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