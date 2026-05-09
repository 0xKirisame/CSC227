package ProjectThreads;
public class PCB {
    private int processID;
    private String state; 
    private int burstTime; 
    private int priority; 
    private int memoryRequired; 
    private int waitingTime;
    private int turnaroundTime;
    private int remainingBurstTime; 
    private int arrivalOrder; 
    private int timeInReadyQueue; 
    private boolean sufferedStarvation; 
    private int startTime; // when the process first gets CPU
    private int terminationTime; // when it finishes

    // constructor to initialize all fields
    public PCB(int processID, int burstTime, int priority, int memoryRequired, int arrivalOrder) {
        this.processID = processID;
        this.burstTime = burstTime;
        this.remainingBurstTime = burstTime; 
        this.priority = priority;
        this.memoryRequired = memoryRequired;
        this.arrivalOrder = arrivalOrder;
        
        this.state = "NEW";
        this.waitingTime = 0;
        this.turnaroundTime = 0;
        this.timeInReadyQueue = 0;
        this.sufferedStarvation = false;
        this.startTime = -1; // -1 means it hasnt started yet
    }

    public int getProcessID() { return processID; }    
    public String getState() { return state; }
    
    public void setState(String state) { this.state = state; }    
    public int getBurstTime() { return burstTime; } 
    public int getRemainingBurstTime() { return remainingBurstTime; }
    public void decrementRemainingBurstTime() { this.remainingBurstTime--; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    // decrease priority number by 1 (makes it higher priority) but dont go below 1
    public void ageProcess() {
        if (this.priority > 1) {
            this.priority--;
        }
    }
    
    public int getMemoryRequired() { return memoryRequired; }
    public int getWaitingTime() { return waitingTime; }
    public void setWaitingTime(int waitingTime) { this.waitingTime = waitingTime; }
    public int getTurnaroundTime() { return turnaroundTime; }
    public void setTurnaroundTime(int turnaroundTime) { this.turnaroundTime = turnaroundTime; }
    public int getArrivalOrder() { return arrivalOrder; }
    public int getTimeInReadyQueue() { return timeInReadyQueue; }
    public void incrementTimeInReadyQueue() { this.timeInReadyQueue++; }
    public void resetTimeInReadyQueue() { this.timeInReadyQueue = 0; }
    public boolean hasSufferedStarvation() { return sufferedStarvation; }
    public void setSufferedStarvation(boolean starved) { this.sufferedStarvation = starved; }
    public int getStartTime() { return startTime; }
    public void setStartTime(int startTime) { this.startTime = startTime; }
    public int getTerminationTime() { return terminationTime; }
    public void setTerminationTime(int terminationTime) { this.terminationTime = terminationTime; }
    public String toString() {
        return "Process " + processID + " [Priority=" + priority + ", Burst=" + burstTime + 
               ", Remaining=" + remainingBurstTime + ", Mem=" + memoryRequired + "MB]";
    }
}
