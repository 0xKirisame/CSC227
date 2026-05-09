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

        System.out.println("CPU Scheduling Simulator");
        System.out.println("Choose algorithm:");
        System.out.println("1 - SJF");
        System.out.println("2 - Round Robin (quantum=5)");
        System.out.println("3 - Priority (Non-Preemptive)");
        System.out.print("Choice: ");

        int choice = scanner.nextInt();
        scanner.close();

        ThreadManager manager = new ThreadManager();

        // thread1 - reads the input file and creates PCB objects
        Thread thread1 = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new FileReader("job.txt"));
                String line;
                int order = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    // parse line format: ID:burst:priority;memory
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
                System.out.println("Error reading file: " + e.getMessage());
            }
            manager.setAllJobsParsed();
        });

        // thread2 - moves jobs to ready queue when theres enough memory
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

        // wait for thread1 to finish before we start scheduling
        try {
            thread1.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // small delay so thread2 can load some jobs first
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

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

        try {
            thread2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        printResults(ganttChart, completedProcesses, choice);
    }

    // SJF scheduling
    private static void runSJF(ThreadManager manager, List<String> ganttChart, List<PCB> completed) {
        int currentTime = 0;
        List<PCB> readyQueue = manager.getReadyQueue();

        while (!manager.isSimulationComplete() || !readyQueue.isEmpty()) {
            PCB selected = null;

            synchronized (manager) {
                if (readyQueue.isEmpty()) {
                    try { manager.wait(50); } catch (InterruptedException e) { break; }
                    continue;
                }

                // pick shortest burst, if tie then whoever came first
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

            // update waiting time for processes still in ready queue
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
            selected.setTurnaroundTime(currentTime); // all arrive at time 0 so turnaround = finish time
            selected.setWaitingTime(selected.getTurnaroundTime() - selected.getBurstTime());
            manager.freeMemory(selected.getMemoryRequired());
            completed.add(selected);

            try { Thread.sleep(10); } catch (InterruptedException e) { break; }
        }
    }

    // Round Robin scheduling
    private static void runRoundRobin(ThreadManager manager, List<String> ganttChart, List<PCB> completed) {
        int currentTime = 0;
        int quantum = 5;
        List<PCB> readyQueue = manager.getReadyQueue();
        Queue<PCB> rrQueue = new LinkedList<>(); // separate queue for round robin

        while (!manager.isSimulationComplete() || !rrQueue.isEmpty()) {
            // move new arrivals into rr queue
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

            // check for new arrivals before putting current process back
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
                rrQueue.offer(selected); // not done yet, put back in queue
            }
        }
    }

    // Priority scheduling with starvation detection and aging
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
                int starvationThreshold = n * 5;

                // check if any process is starving
                for (PCB p : readyQueue) {
                    if (p.getTimeInReadyQueue() > starvationThreshold) {
                        p.setSufferedStarvation(true);
                    }
                }

                // aging: every 4ms waiting, bump priority up by 1
                for (PCB p : readyQueue) {
                    if (p.getTimeInReadyQueue() > 0 && p.getTimeInReadyQueue() % 4 == 0) {
                        p.ageProcess();
                    }
                }

                // pick lowest priority number (= highest priority)
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

            // other processes are waiting while this one runs
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

    // print all the results
    private static void printResults(List<String> ganttChart, List<PCB> completed, int algorithm) {
        System.out.println();

        String algoName;
        switch (algorithm) {
            case 1: algoName = "SJF"; break;
            case 2: algoName = "Round Robin (q=5)"; break;
            case 3: algoName = "Priority (Non-Preemptive)"; break;
            default: algoName = "Unknown"; break;
        }
        System.out.println("Algorithm: " + algoName);
        System.out.println("--------------------------------------------");

        // gantt chart
        System.out.println("\nGantt Chart:");
        for (String entry : ganttChart) {
            System.out.print(entry);
        }
        System.out.println("|");

        // process table
        completed.sort(Comparator.comparingInt(PCB::getProcessID));

        System.out.println("\nProcess Table:");
        System.out.printf("%-10s %-10s %-10s %-12s %-12s %-12s%n",
            "PID", "Burst", "Start", "End", "Waiting", "Turnaround");
        System.out.println("--------------------------------------------------------------");

        double totalWaiting = 0;
        double totalTurnaround = 0;

        for (PCB p : completed) {
            System.out.printf("%-10d %-10d %-10d %-12d %-12d %-12d%n",
                p.getProcessID(), p.getBurstTime(), p.getStartTime(),
                p.getTerminationTime(), p.getWaitingTime(), p.getTurnaroundTime());
            totalWaiting += p.getWaitingTime();
            totalTurnaround += p.getTurnaroundTime();
        }

        System.out.println();
        System.out.printf("Avg Waiting Time: %.2f ms%n", totalWaiting / completed.size());
        System.out.printf("Avg Turnaround Time: %.2f ms%n", totalTurnaround / completed.size());

        // starvation report for priority only
        if (algorithm == 3) {
            System.out.println("\nStarvation Report:");
            boolean anyStarved = false;
            for (PCB p : completed) {
                if (p.hasSufferedStarvation()) {
                    System.out.println("P" + p.getProcessID() + " suffered starvation (aging applied)");
                    anyStarved = true;
                }
            }
            if (!anyStarved) {
                System.out.println("No starvation detected.");
            }
        }
        System.out.println();
    }
}
