package ProjectThreads;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class ThreadManager {
    private final Queue<PCB> jobQueue;
    private final List<PCB> readyQueue;
    private int availableMemory;
    private final int MAX_MEMORY = 2048;
    private boolean allJobsParsed; 

    public ThreadManager() {
        this.jobQueue = new LinkedList<>();
        this.readyQueue = new ArrayList<>();
        this.availableMemory = MAX_MEMORY;
        this.allJobsParsed = false;
    }

    // add process to job queue and notify thread2
    public synchronized void addJobToQueue(PCB process) {
        jobQueue.offer(process); // offer is safer than add, returns false instead of exception
        notifyAll();
    }

    // called by thread1 when its done reading all jobs from file
    public synchronized void setAllJobsParsed() {
        this.allJobsParsed = true;
        notifyAll();
    }

    // try to move next job from job queue to ready queue
    // waits if no jobs available or not enough memory
    public synchronized void loadNextJobToReadyQueue() throws InterruptedException {

        while (jobQueue.isEmpty() && !allJobsParsed) {
            wait(); // wait until thread1 adds a job or finishes
        }

        if (!jobQueue.isEmpty()) {
            PCB nextJob = jobQueue.peek();

            if (availableMemory >= nextJob.getMemoryRequired()) {
                jobQueue.poll();
                availableMemory -= nextJob.getMemoryRequired();
                nextJob.setState("READY");
                readyQueue.add(nextJob);
                notifyAll();
            } else {
                wait(); // not enough memory, wait for some to be freed
            }
        }
    }

    // returns true if there are still jobs to load (either in queue or thread1 still reading)
    public synchronized boolean hasMoreJobsToLoad() {
        return !jobQueue.isEmpty() || !allJobsParsed;
    }

    public synchronized List<PCB> getReadyQueue() {
        return readyQueue;
    }
    
    // free memory when a process finishes, notify thread2 so it can try loading more
    public synchronized void freeMemory(int memoryFreed) {
        availableMemory += memoryFreed;
        notifyAll();
    }

    // check if everything is done
    public synchronized boolean isSimulationComplete() {
        return allJobsParsed && jobQueue.isEmpty() && readyQueue.isEmpty();
    }
}
