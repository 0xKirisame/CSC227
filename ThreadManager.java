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

    public synchronized void addJobToQueue(PCB process) {
        jobQueue.offer(process); //offer is better than add because it outputs false if element not added[better for exception (M): contact if you don't understand
        notifyAll(); 
    }

    public synchronized void setAllJobsParsed() { // if all allocated jobs are done set boolean true (M)
        this.allJobsParsed = true;
        notifyAll();
    }

    public synchronized void loadNextJobToReadyQueue() throws InterruptedException {
    	
        while (jobQueue.isEmpty() && !allJobsParsed) {
            wait();
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
                wait(); 
            }
        }
    }

    public synchronized boolean hasMoreJobsToLoad() { 
        return !jobQueue.isEmpty() || !allJobsParsed;
    }

    public synchronized List<PCB> getReadyQueue() {
        return readyQueue;
    }
    
    public synchronized void freeMemory(int memoryFreed) {
        availableMemory += memoryFreed;
        notifyAll(); 
    }

    public synchronized boolean isSimulationComplete() {
        return allJobsParsed && jobQueue.isEmpty() && readyQueue.isEmpty();
    }
}
