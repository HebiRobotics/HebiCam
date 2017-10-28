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
            % The destructor is guaranteed to run when object is deleted 
            % (e.g. out of scope), even if the user interrupts via ctrl+c.
            %
            % Note that the constructor can still be interrupted, so the
            % lock() may have not executed correctly. Thus, there is a
            % check in the Java class to make sure that it only unlocks if
            % the lock() actually executed. Users can only interrupt
            % MATLAB code, so Java methods are guaranteed to execute
            % fully once called.
            tryUnlock(this.lock);
        end
        
    end
    
end