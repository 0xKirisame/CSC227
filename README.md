# CSC 227 - Multithreaded CPU Scheduling Simulator

## Overview

This Java program simulates CPU scheduling in a single-CPU system using multiple threads. It supports three scheduling algorithms: **Shortest Job First (SJF)**, **Round Robin (RR)**, and **Priority Scheduling (Non-Preemptive)** with starvation detection and aging.

---

## File Structure

| File | Purpose |
|------|---------|
| `PCB.java` | Process Control Block - holds all data for a single process |
| `ThreadManager.java` | Shared resource manager - coordinates the job queue, ready queue, and memory between threads |
| `CPUSchedulerSimulator.java` | Main class - contains the three scheduling algorithms, thread creation, and output formatting |
| `job.txt` | Input file - list of processes to simulate |

---

## How to Compile and Run

```bash
# Create the package directory and copy files into it
mkdir -p ProjectThreads
cp PCB.java ThreadManager.java CPUSchedulerSimulator.java ProjectThreads/

# Compile
javac ProjectThreads/*.java

# Run
java ProjectThreads.CPUSchedulerSimulator
```

The program will prompt you to choose a scheduling algorithm (1, 2, or 3).

---

## Code Breakdown

### 1. PCB.java (Process Control Block)

This class represents a single process. Each PCB stores:

- **processID** - unique identifier (read from input file)
- **state** - current process state: `NEW`, `READY`, `RUNNING`, or `TERMINATED`
- **burstTime** - total CPU time the process needs (never changes)
- **remainingBurstTime** - how much burst is left (decremented as the process runs, important for Round Robin where a process runs in multiple slices)
- **priority** - priority number (1-30, lower = higher priority), can change due to aging
- **memoryRequired** - how much RAM the process needs in MB
- **arrivalOrder** - the order the process was read from the file (used for tie-breaking)
- **waitingTime** - total time the process spent waiting in the ready queue
- **turnaroundTime** - total time from arrival to completion (turnaround = waiting + burst)
- **startTime** - the first time the process gets the CPU (-1 means it hasn't started)
- **terminationTime** - when the process finishes
- **timeInReadyQueue** - tracks how long a process has been waiting without being dispatched (used for starvation detection in Priority scheduling)
- **sufferedStarvation** - flag set to true if the process was detected as starved

Key methods:
- `decrementRemainingBurstTime()` - simulates 1 ms of CPU execution
- `ageProcess()` - decreases priority number by 1 (increases priority), called during aging in Priority scheduling
- `incrementTimeInReadyQueue()` / `resetTimeInReadyQueue()` - used to track starvation

### 2. ThreadManager.java (Shared Resource Manager)

This class is the central coordinator between all threads. It manages:

- **jobQueue** (`LinkedList`) - holds processes after they are read from the file but before they are admitted to the ready queue
- **readyQueue** (`ArrayList`) - holds processes that have been admitted (enough memory available) and are waiting for the CPU
- **availableMemory** - tracks remaining memory out of 2048 MB

All public methods are `synchronized` because multiple threads access this object concurrently. This prevents race conditions. Key methods:

- **`addJobToQueue(PCB)`** - Thread 1 calls this to add parsed processes. Uses `offer()` instead of `add()` for safer insertion. Calls `notifyAll()` to wake Thread 2 if it was waiting for jobs.
- **`loadNextJobToReadyQueue()`** - Thread 2 calls this in a loop. It `wait()`s if the job queue is empty and not all jobs have been parsed yet. When a job is available, it checks if enough memory exists (`availableMemory >= memoryRequired`). If yes, it moves the job to the ready queue and subtracts the memory. If not, it `wait()`s until memory is freed.
- **`setAllJobsParsed()`** - Thread 1 calls this after reading all lines from job.txt. It sets a flag and wakes Thread 2 so it knows no more jobs are coming.
- **`freeMemory(int)`** - the main thread calls this when a process terminates, returning its memory. Calls `notifyAll()` to wake Thread 2 if it was blocked waiting for memory.
- **`isSimulationComplete()`** - returns true when all jobs are parsed, the job queue is empty, and the ready queue is empty (all processes finished).
- **`hasMoreJobsToLoad()`** - Thread 2 uses this as its loop condition.

### 3. CPUSchedulerSimulator.java (Main Class)

#### Thread Architecture

The program creates three threads as required:

1. **Thread 1 (File Reader)** - reads `job.txt` line by line, parses each line into a PCB object using the format `ID:burst:priority;memory`, and adds each PCB to the job queue via `ThreadManager.addJobToQueue()`. After all lines are read, it calls `setAllJobsParsed()` and terminates.

2. **Thread 2 (Memory Loader)** - runs a loop calling `loadNextJobToReadyQueue()` which blocks (waits) until either a job is available AND memory is sufficient, or all jobs are done. This thread is what enforces the 2048 MB memory constraint. It terminates when `hasMoreJobsToLoad()` returns false.

3. **Main Thread (Scheduler)** - after Thread 1 finishes (via `join()`), the main thread runs the selected scheduling algorithm. It picks processes from the ready queue, simulates their execution, updates their PCB fields, and records the Gantt chart.

#### Scheduling Algorithms

**SJF (Shortest Job First) - Option 1:**
- Non-preemptive: once a process starts, it runs to completion.
- Selection: from the ready queue, pick the process with the smallest `burstTime`. If two processes have the same burst, the one that arrived first (lower `arrivalOrder`) is chosen.
- After execution: the process is marked `TERMINATED`, its `terminationTime` and `turnaroundTime` are set, and its memory is freed.

**Round Robin - Option 2:**
- Preemptive with a time quantum of 5 ms.
- Uses a separate `rrQueue` (a FIFO `LinkedList`) where processes are taken from the front and added back to the end if they haven't finished.
- Each process gets at most 5 ms per turn. If it finishes in less than 5 ms, only the actual remaining burst is used.
- The Gantt chart shows each time slice separately, including the burst values before and after the slice (e.g., `burst 25->20`), so you can see the process being executed in chunks.
- Newly arrived processes (loaded by Thread 2) are moved from the ready queue into `rrQueue` before the current process is re-added, ensuring FIFO order.

**Priority Scheduling (Non-Preemptive) - Option 3:**
- Selection: pick the process with the lowest priority number (highest priority). Tie-break by arrival order.
- **Starvation detection**: before each scheduling decision, the algorithm checks every process in the ready queue. A process is considered starved if its `timeInReadyQueue` exceeds `N * 5` ms, where N is the current number of processes in the ready queue. If starved, the `sufferedStarvation` flag is set to true.
- **Aging**: every 4 ms a process spends in the ready queue, its priority number is decreased by 1 (making it higher priority). This is done by calling `ageProcess()` which checks that priority doesn't go below 1.
- While a process runs (non-preemptive, full burst), all other processes in the ready queue have their `timeInReadyQueue` incremented by the running process's burst time.
- The starvation report at the end lists which processes were detected as starved.

#### Output

For all algorithms, the program displays:
1. **Gantt Chart** - shows each scheduling decision: which process ran, the time interval, and the burst values before/after execution.
2. **Process Table** - shows Process ID, Burst Time, Start Time, Termination Time, Waiting Time, and Turnaround Time for each process, sorted by Process ID.
3. **Performance Metrics** - Average Waiting Time and Average Turnaround Time.
4. **Starvation Report** (Priority only) - lists which processes suffered from starvation.

---

## Input File Format (job.txt)

Each line represents one process in the format:

```
ProcessID:BurstTime:Priority;MemoryRequired
```

Example:
```
1:25:4;500
2:13:3;700
3:20:3;100
```

- **ProcessID**: integer identifier
- **BurstTime**: CPU time needed in milliseconds
- **Priority**: integer 1-30 (1 = highest priority, only used by Priority scheduling)
- **MemoryRequired**: RAM needed in MB (total system memory is 2048 MB)

---

## Synchronization Mechanisms

The program uses Java's built-in `synchronized` keyword and `wait()`/`notifyAll()` monitor methods for thread synchronization:

- **Why synchronized?** Multiple threads (Thread 1, Thread 2, and Main) access the `ThreadManager` object concurrently. Without synchronization, race conditions could corrupt the job queue, ready queue, or memory counter.
- **Why wait/notifyAll?** Thread 2 needs to block when there are no jobs to load or not enough memory. Using `wait()` is more efficient than busy-waiting (spinning in a loop), and `notifyAll()` wakes it up when conditions change (new job added, memory freed, or all jobs parsed).

---

## Key Design Decisions

1. **ArrayList for readyQueue** instead of a Queue - because SJF and Priority need to search the entire queue to find the best process, not just the front. ArrayList allows indexed access and stream operations.
2. **Separate rrQueue in Round Robin** - the ready queue in ThreadManager is where Thread 2 loads jobs. Round Robin moves them into its own FIFO queue to maintain proper circular ordering without interfering with Thread 2's loading logic.
3. **Thread.sleep() calls** - small sleeps (10-50 ms) give Thread 2 time to load jobs after memory is freed. This is a practical concurrency measure since the simulation runs much faster than real-time.
4. **All processes arrive at time 0** - as stated in the project assumptions, so arrival time is not stored in the PCB. Turnaround time = termination time, and waiting time = turnaround time - burst time.
