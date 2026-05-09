package ProjectThreads;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;

public class CPUSchedulerSimulator {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== CPU Scheduling Simulator ===");
        System.out.println("Select a scheduling algorithm:");
        System.out.println("1. Shortest Job First (SJF)");
        System.out.println("2. Round Robin (RR) - Quantum = 5 ms");
        System.out.println("3. Priority Scheduling (Non-Preemptive)");
        System.out.print("Enter your choice (1-3): ");

        int choice = scanner.nextInt();
        scanner.close();

        ThreadManager manager = new ThreadManager();

        // Thread 1: reads job.txt, creates PCBs, adds them to the job queue (M)
        Thread thread1 = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new FileReader("job.txt"));
                String line;
                int order = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    // format: ID:burst:priority;memory (M)
                    String[] parts = line.split(";");
                    String[] info = parts[0].split(":");
                    int id = Integer.parseInt(info[0]);
                    int burst = Integer.parseInt(info[1]);
                    int priority = Integer.parseInt(info[2]);
                    int memory = Integer.parseInt(parts[1]);

                    PCB process = new PCB(id, burst, priority, memory, order);
                    manager.addJobToQueue(process);
                    order++;
                }
                reader.close();
            } catch (IOException e) {
                System.out.println("Error reading job.txt: " + e.getMessage());
            }
            manager.setAllJobsParsed(); // signal that all jobs have been read (M)
        });

        // Thread 2: loads jobs from job queue to ready queue if memory is available (M)
        Thread thread2 = new Thread(() -> {
            try {
                while (manager.hasMoreJobsToLoad()) {
                    manager.loadNextJobToReadyQueue();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        thread1.start();
        thread2.start();

        // wait for thread1 to finish reading all jobs before scheduling starts (M)
        try {
            thread1.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // give thread2 a moment to load initial jobs into ready queue (M)
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Main thread: runs the selected scheduling algorithm (M)
        List<String> ganttChart = new ArrayList<>();
        List<PCB> completedProcesses = new ArrayList<>();

        switch (choice) {
            case 1:
                runSJF(manager, ganttChart, completedProcesses);
                break;
            case 2:
                runRoundRobin(manager, ganttChart, completedProcesses);
                break;
            case 3:
                runPriority(manager, ganttChart, completedProcesses);
                break;
            default:
                System.out.println("Invalid choice.");
                return;
        }

        // wait for thread2 to finish after simulation (M)
        try {
            thread2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        printResults(ganttChart, completedProcesses, choice);
    }

    // ==================== SJF (Non-Preemptive) ====================
    private static void runSJF(ThreadManager manager, List<String> ganttChart, List<PCB> completed) {
        int currentTime = 0;
        List<PCB> readyQueue = manager.getReadyQueue();

        while (!manager.isSimulationComplete() || !readyQueue.isEmpty()) {
            PCB selected = null;

            synchronized (manager) {
                if (readyQueue.isEmpty()) {
                    // wait briefly for thread2 to load more jobs (M)
                    try { manager.wait(50); } catch (InterruptedException e) { break; }
                    continue;
                }

                // pick the process with shortest burst, tie-break by arrival order (M)
                selected = readyQueue.stream()
                    .min(Comparator.comparingInt(PCB::getBurstTime)
                        .thenComparingInt(PCB::getArrivalOrder))
                    .orElse(null);

                if (selected != null) {
                    readyQueue.remove(selected);
                }
            }

            if (selected == null) continue;

            selected.setState("RUNNING");
            if (selected.getStartTime() == -1) {
                selected.setStartTime(currentTime);
            }

            int burstStart = selected.getRemainingBurstTime();
            ganttChart.add("| P" + selected.getProcessID() +
                " [" + currentTime + "-" + (currentTime + selected.getRemainingBurstTime()) +
                "] burst " + burstStart + "->" + 0 + " ");

            // increment waiting time for all other processes still in ready queue (M)
            synchronized (manager) {
                for (PCB p : readyQueue) {
                    p.setWaitingTime(p.getWaitingTime() + selected.getRemainingBurstTime());
                }
            }

            currentTime += selected.getRemainingBurstTime();
            while (selected.getRemainingBurstTime() > 0) {
                selected.decrementRemainingBurstTime();
            }

            selected.setState("TERMINATED");
            selected.setTerminationTime(currentTime);
            selected.setTurnaroundTime(currentTime - 0); // all arrive at time 0 (M)
            selected.setWaitingTime(selected.getTurnaroundTime() - selected.getBurstTime());
            manager.freeMemory(selected.getMemoryRequired());
            completed.add(selected);

            // give thread2 time to load more jobs after memory is freed (M)
            try { Thread.sleep(10); } catch (InterruptedException e) { break; }
        }
    }

    // ==================== Round Robin ====================
    private static void runRoundRobin(ThreadManager manager, List<String> ganttChart, List<PCB> completed) {
        int currentTime = 0;
        int quantum = 5;
        List<PCB> readyQueue = manager.getReadyQueue();
        Queue<PCB> rrQueue = new LinkedList<>(); // separate RR circular queue (M)

        while (!manager.isSimulationComplete() || !rrQueue.isEmpty()) {
            // move any new arrivals from ready queue into the RR queue (M)
            synchronized (manager) {
                while (!readyQueue.isEmpty()) {
                    PCB p = readyQueue.remove(0);
                    rrQueue.offer(p);
                }
            }

            if (rrQueue.isEmpty()) {
                synchronized (manager) {
                    if (manager.isSimulationComplete()) break;
                    try { manager.wait(50); } catch (InterruptedException e) { break; }
                }
                continue;
            }

            PCB selected = rrQueue.poll();
            selected.setState("RUNNING");
            if (selected.getStartTime() == -1) {
                selected.setStartTime(currentTime);
            }

            int timeSlice = Math.min(quantum, selected.getRemainingBurstTime());
            int burstBefore = selected.getRemainingBurstTime();

            ganttChart.add("| P" + selected.getProcessID() +
                " [" + currentTime + "-" + (currentTime + timeSlice) +
                "] burst " + burstBefore + "->" + (burstBefore - timeSlice) + " ");

            currentTime += timeSlice;
            for (int i = 0; i < timeSlice; i++) {
                selected.decrementRemainingBurstTime();
            }

            // move newly arrived jobs into rrQueue before re-adding current process (M)
            synchronized (manager) {
                while (!readyQueue.isEmpty()) {
                    PCB p = readyQueue.remove(0);
                    rrQueue.offer(p);
                }
            }

            if (selected.getRemainingBurstTime() == 0) {
                selected.setState("TERMINATED");
                selected.setTerminationTime(currentTime);
                selected.setTurnaroundTime(currentTime);
                selected.setWaitingTime(selected.getTurnaroundTime() - selected.getBurstTime());
                manager.freeMemory(selected.getMemoryRequired());
                completed.add(selected);
                try { Thread.sleep(10); } catch (InterruptedException e) { break; }
            } else {
                selected.setState("READY");
                rrQueue.offer(selected); // put it back at the end of the queue (M)
            }
        }
    }

    // ==================== Priority (Non-Preemptive) with Starvation + Aging ====================
    private static void runPriority(ThreadManager manager, List<String> ganttChart, List<PCB> completed) {
        int currentTime = 0;
        List<PCB> readyQueue = manager.getReadyQueue();

        while (!manager.isSimulationComplete() || !readyQueue.isEmpty()) {
            PCB selected = null;

            synchronized (manager) {
                if (readyQueue.isEmpty()) {
                    if (manager.isSimulationComplete()) break;
                    try { manager.wait(50); } catch (InterruptedException e) { break; }
                    continue;
                }

                int n = readyQueue.size();
                int starvationThreshold = n * 5; // N * 5 ms (M)

                // check starvation and apply aging (M)
                for (PCB p : readyQueue) {
                    if (p.getTimeInReadyQueue() > starvationThreshold) {
                        p.setSufferedStarvation(true);
                    }
                }

                // aging: every 4 ms in ready queue, increase priority by 1 (decrease number) (M)
                for (PCB p : readyQueue) {
                    if (p.getTimeInReadyQueue() > 0 && p.getTimeInReadyQueue() % 4 == 0) {
                        p.ageProcess();
                    }
                }

                // pick highest priority (lowest number), tie-break by arrival order (M)
                selected = readyQueue.stream()
                    .min(Comparator.comparingInt(PCB::getPriority)
                        .thenComparingInt(PCB::getArrivalOrder))
                    .orElse(null);

                if (selected != null) {
                    readyQueue.remove(selected);
                }
            }

            if (selected == null) continue;

            selected.setState("RUNNING");
            if (selected.getStartTime() == -1) {
                selected.setStartTime(currentTime);
            }

            int burstStart = selected.getRemainingBurstTime();

            ganttChart.add("| P" + selected.getProcessID() +
                " [" + currentTime + "-" + (currentTime + selected.getRemainingBurstTime()) +
                "] burst " + burstStart + "->" + 0 + " ");

            // while this process runs, increment timeInReadyQueue for waiting processes (M)
            int runTime = selected.getRemainingBurstTime();
            synchronized (manager) {
                for (PCB p : readyQueue) {
                    for (int t = 0; t < runTime; t++) {
                        p.incrementTimeInReadyQueue();
                    }
                }
            }

            currentTime += runTime;
            while (selected.getRemainingBurstTime() > 0) {
                selected.decrementRemainingBurstTime();
            }

            selected.setState("TERMINATED");
            selected.setTerminationTime(currentTime);
            selected.setTurnaroundTime(currentTime);
            selected.setWaitingTime(selected.getTurnaroundTime() - selected.getBurstTime());
            selected.resetTimeInReadyQueue();
            manager.freeMemory(selected.getMemoryRequired());
            completed.add(selected);

            try { Thread.sleep(10); } catch (InterruptedException e) { break; }
        }
    }

    // ==================== Output ====================
    private static void printResults(List<String> ganttChart, List<PCB> completed, int algorithm) {
        System.out.println();
        System.out.println("============================================================");

        String algoName;
        switch (algorithm) {
            case 1: algoName = "Shortest Job First (SJF)"; break;
            case 2: algoName = "Round Robin (RR, q=5ms)"; break;
            case 3: algoName = "Priority Scheduling (Non-Preemptive)"; break;
            default: algoName = "Unknown"; break;
        }
        System.out.println("  Scheduling Algorithm: " + algoName);
        System.out.println("============================================================");

        // Gantt Chart (M)
        System.out.println();
        System.out.println("--- Gantt Chart ---");
        for (String entry : ganttChart) {
            System.out.print(entry);
        }
        System.out.println("|");
        System.out.println();

        // Process Table (M)
        System.out.println("--- Process Table ---");
        System.out.printf("%-12s %-12s %-12s %-15s %-14s %-16s%n",
            "Process ID", "Burst Time", "Start Time", "Termination", "Waiting Time", "Turnaround Time");
        System.out.println("---------------------------------------------------------------------------------");

        // sort by process ID for clean output (M)
        completed.sort(Comparator.comparingInt(PCB::getProcessID));

        double totalWaiting = 0;
        double totalTurnaround = 0;

        for (PCB p : completed) {
            System.out.printf("%-12d %-12d %-12d %-15d %-14d %-16d%n",
                p.getProcessID(), p.getBurstTime(), p.getStartTime(),
                p.getTerminationTime(), p.getWaitingTime(), p.getTurnaroundTime());
            totalWaiting += p.getWaitingTime();
            totalTurnaround += p.getTurnaroundTime();
        }

        System.out.println();
        System.out.printf("Average Waiting Time:    %.2f ms%n", totalWaiting / completed.size());
        System.out.printf("Average Turnaround Time: %.2f ms%n", totalTurnaround / completed.size());

        // for priority scheduling: show starved processes (M)
        if (algorithm == 3) {
            System.out.println();
            System.out.println("--- Starvation Report ---");
            boolean anyStarved = false;
            for (PCB p : completed) {
                if (p.hasSufferedStarvation()) {
                    System.out.println("Process " + p.getProcessID() + " suffered from starvation (aging was applied).");
                    anyStarved = true;
                }
            }
            if (!anyStarved) {
                System.out.println("No process suffered from starvation.");
            }
        }

        System.out.println();
        System.out.println("============================================================");
    }
}
