%% Setup
if(exist('sources.JavaImageConverter','class'))
    clear all;
    javarmpath('sources.jar');
end
compileSources();
converter = ImageConverter();
testMode = false;

%% Test mode: Make sure all methods produce the same images
if testMode
    
    samples = dir('samples');
    for i = 1:length(samples)
        
        % Ignore non-sample entries
        name = samples(i).name;
        if length(name) < 7 || ~isequal(name(1:7), 'sample_')
            continue;
        end
        path = ['samples/' name];
        
        % Prepare image
        disp(name);
        converter.setImage(path);
        
        % Confirm that the resulting images are the same
        figure(201);
        
        subplot(3,2,1);
        imshow(converter.getOriginalImage());
        title('Original');
        
        subplot(3,2,2);
        imshow(converter.getImageFromRawFormat3D());
        title('Raw (Java)');
        
        subplot(3,2,3);
        imshow(converter.getImageFromJavaPixelFormat1D());
        title('Pixel Array (Java)');
        
        subplot(3,2,4);
        imshow(converter.getImageFromMatlabPixelFormat1D());
        title('Pixel Array (MATLAB)');
        
        subplot(3,2,5);
        imshow(converter.getImageFromCompressedArray());
        title('Compressed Array');
        
        subplot(3,2,6);
        imshow(converter.getImageFromSharedMemory());
        title('Shared Memory');
        
        pause();
        
    end
    
end

%% Benchmark mode: measure time for different ways
if ~testMode
    
    % measurement structs
    m = [];
    j = 0;
    
    samples = dir('samples');
    for i = 1:length(samples)
        
        % Ignore non-sample entries
        name = samples(i).name;
        if length(name) < 7 || ~isequal(name(1:7), 'sample_')
            continue;
        end
        path = ['samples/' name];
        
        % Prepare image
        disp(name);
        converter.setImage(path);
        [height,width,channels] = size(converter.getOriginalImage);
        
        % Meta data
        j = j+1;
        m(j).name = name;
        m(j).width = width;
        m(j).height = height;
        m(j).channels = channels; 

        % Benchmark conversions
        toMs = 1E3;
%         m(j).original = timeit(@()converter.getOriginalImage) * toMs;
        m(j).compressedArray = timeit(@()converter.getImageFromCompressedArray) * toMs;
        m(j).raw3d = timeit(@()converter.getImageFromRawFormat3D) * toMs;
        m(j).javaArray = timeit(@()converter.getImageFromJavaPixelFormat1D) * toMs;
        m(j).matlabArray = timeit(@()converter.getImageFromMatlabPixelFormat1D) * toMs;
        m(j).sharedMem = timeit(@()converter.getImageFromSharedMemory) * toMs;
        
    end
    
    % Convert to nicer looking table w/ sort
    results = struct2table(m);
    results = sortrows(results, {'channels', 'height'});
    disp(results);
    
end

