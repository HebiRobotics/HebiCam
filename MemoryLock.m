classdef MemoryLock < handle
    % A class that guarantees that lock and unlock are reliably called
    % even if users interrupt memory read with ctlr-c. Similar to how
    % 'onCleanup' works, but with better performance.
    %
    % According to https://www.mathworks.com/help/matlab/matlab_oop/handle-class-destructors.html
    %
    % "MATLAB calls the delete method reliably, even if execution is
    % interrupted with Ctrl-c or an error."
    
    properties(SetAccess = private)
        lock;
    end
    
    methods
        
        function this = MemoryLock(javaLock)
            this.lock = javaLock;
            lock(this.lock);
        end
        
        function delete(this)
            % Gets called when object is deleted, even on interrupt.
            % Note: I don't know whether constructors can be interrupted,
            % so there is a check in the Java class that makes sure it only
            % gets called when necessary. Hence 'try'-Unlock.
            tryUnlock(this.lock);
        end
        
    end
    
end