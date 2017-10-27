%% Setup
if(exist('sources.JavaImage','class'))
    clear all;
    javarmpath('sources.jar');
end
compileSources();
javaObj = sources.JavaImage();

%% Benchmark various sizes
samples = dir('samples');
for i = 1:length(samples)
    
    % Ignore non-sample entries
    name = samples(i).name;
    if length(name) < 7 || ~isequal(name(1:7), 'sample_')
        continue;
    end
    path = ['samples/' name];
    
    % Load image
    disp(name);
    I = imread(path);
    [h,w,c] = size(I);
    
    % Push across Java and convert to various formats
    setImage(javaObj, I, path);
    
    % Initialize data in shared memory (not necessary to do this
    % from Java)
    memFile = pepareMemmapFile(I);

    % Confirm that the resulting images are the same
    figure(201);    
     text(-10,10.2,name) % 'suptitle' not available?
    
    subplot(3,2,1);
    imshow(I);
    title('Original');
    
    subplot(3,2,2);
    data = getRawFormat3d(javaObj);
    imshow(j2m_raw3d(data, w, h, c));
    title('Raw (Java)');
    
    subplot(3,2,3);
    data = getJavaPixelFormat1d(javaObj);
    imshow(j2m_javaFormat(data, w, h, c));
    title('Pixels (Java)');
    
    subplot(3,2,4);
    data = getMatlabPixelFormat1d(javaObj);
    imshow(j2m_matlabFormat(data, w, h, c));
    title('Pixels (MATLAB)');
    
    subplot(3,2,5);
    data = getJpegData(javaObj);
    imshow(j2m_jpeg(data, w, h, c));
    title('Jpeg');
    
    subplot(3,2,6);
    lock(javaObj);
    data = memFile.Data.pixels * 1; % force a copy
    unlock(javaObj);
    imshow(data);
    title('Memory map');
   
    memFile = [];
    pause();
    
end

function img = j2m_raw3d(data, w, h, c)
% Java 'byte' comes back as 'int8'. Images need to be 'uint8' Unfortunately,
% uint8() sets all negative values to zero, and typecast() only works
% on a 1D array. Thus, even though the data is already sized correctly,
% we need to first do a reshape to 1D, cast, and then reshape back again.

% reshape to vector, cast to uint8, then reshape back
vector = typecast(reshape(data,w*h*c,1), 'uint8');
img = reshape(vector,h,w,c);
end

function img = j2m_javaFormat(data, w, h, c)
% Memory is in the order of a Java buffered image, so we need to cast,
% reshape, transpose, and re-order memory

if c == 3 % rgb
    % Source: https://mathworks.com/matlabcentral/answers/100155-how-can-i-convert-a-java-image-object-into-a-matlab-image-matrix#answer_109503
    pixelsData = reshape(typecast(data, 'uint8'), 3, w, h);
    img = cat(3, ...
        transpose(reshape(pixelsData(3, :, :), w, h)), ...
        transpose(reshape(pixelsData(2, :, :), w, h)), ...
        transpose(reshape(pixelsData(1, :, :), w, h)));
    
elseif c == 1 % grayscale
    pixelsData = reshape(typecast(data, 'uint8'), w, h);
    img = pixelsData';
end

end

function img = j2m_matlabFormat(data, w, h, c)
% Memory is already in the right order, so we only need to cast and reshape
vector = typecast(data, 'uint8');
img = reshape(vector,h,w,c);
end

function img = j2m_jpeg(data, w, h, c)
% MATLAB doesn't provide in-memory decompression of jpeg data, so we need
% to go through a file.

% store as file
fileID = fopen('tmp.jpg','w+');
fwrite(fileID, data, 'int8');
fclose(fileID);

% read from file
img = imread('tmp.jpg');

% delete file
delete('tmp.jpg');
end

function memFile = pepareMemmapFile(data)
% Memory maps a temporary file and saves image data

path = 'memFile.bin';
delete(path);

% store data
fileID = fopen(path,'w+');
fwrite(fileID, data(:), 'uint8');
fclose(fileID);

% Some versions have problems with mapping HxWx1, so we special
% case grayscale images.
pixelFormat = size(data);

% Map memory to data
memFile = memmapfile(path,  ...
    'Format', { 'uint8' pixelFormat 'pixels' }, ...
    'Repeat', 1);

end

