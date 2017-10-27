%% Build Java library from sources 
% Note: requires JDK to be installed and on path
if(~exist('sources.JavaImageConverter','class'))
    
    % Find source directory
    local = fileparts(mfilename('fullpath'));
    source = fullfile(local, 'sources');
    
    % Compile
    disp('started compilation');
    [status, result] = system(['javac -target 1.6 -source 1.6 ', fullfile(source,'*.java')]);
    if status ~= 0
       error(result); 
    end
    
    % Package as jar
    [status, result] = system(['jar cvf sources.jar ', ' -C ', local, ' sources/*.class']);
     if status ~= 0
       error(result); 
     end
    
    % Import
    javaaddpath('sources.jar');
    disp('finished compilation');
    
end