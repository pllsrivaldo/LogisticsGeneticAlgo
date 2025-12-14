package com.uas.logistics.service;

import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GeneticAlgorithmService {

    public List<Container> globalManifest = new ArrayList<>();
    
    // Default Ship
    public Ship currentShip = new Ship("DEFAULT", "Default Ship", 120, 3, 4, new HashSet<>(Arrays.asList(0, 2)), new double[]{40, 40, 40});
    public int currentOriginIdx = 0; 

    public List<String> getAvailableDestinations() {
        List<String> allPorts = Arrays.asList("Jakarta", "Surabaya", "Batam", "Semarang", "Belawan", "Makassar", "Balikpapan");
        List<String> available = new ArrayList<>();
        for (int i = 0; i < allPorts.size(); i++) {
            if (i != currentOriginIdx) available.add(allPorts.get(i));
        }
        return available;
    }

    // VALIDASI INPUT AGAR TIDAK CRASH 
    public void updateShipConfig(String id, String name, double maxTon, int stacks, int height, String secureStacksStr, String stackCapStr, int originIdx) {
        // 1. Validasi Secure Stacks
        Set<Integer> secureStacks = new HashSet<>();
        if(secureStacksStr != null && !secureStacksStr.isEmpty()) {
            for(String s : secureStacksStr.split(",")) {
                try { 
                    int idx = Integer.parseInt(s.trim());
                    // HANYA MASUKKAN JIKA INDEX VALID (Kurang dari jumlah stacks)
                    if(idx >= 0 && idx < stacks) {
                        secureStacks.add(idx);
                    }
                } catch(Exception e){}
            }
        }
        
        // 2. Validasi Kapasitas Stack
        double[] stackCaps = new double[stacks];
        if(stackCapStr != null && !stackCapStr.isEmpty()) {
            String[] caps = stackCapStr.split(";");
            for(int i=0; i<Math.min(caps.length, stacks); i++) {
                try { stackCaps[i] = Double.parseDouble(caps[i].trim()); } catch(Exception e){}
            }
            // Isi sisa stack jika input kurang
            for(int i=caps.length; i<stacks; i++) stackCaps[i] = maxTon / stacks; 
        } else {
            Arrays.fill(stackCaps, maxTon/stacks);
        }
        
        this.currentShip = new Ship(id, name, maxTon, stacks, height, secureStacks, stackCaps);
        this.currentOriginIdx = originIdx;
    }

    public void addContainerToManifest(double weight, boolean isSpecial, String destName, String source) {
        List<String> route = Arrays.asList("Jakarta", "Surabaya", "Batam", "Semarang", "Belawan", "Makassar", "Balikpapan");
        int destIdx = route.indexOf(destName);
        if (destIdx == -1) destIdx = 1; 
        int newId = globalManifest.size();
        Container c = new Container(newId, weight, isSpecial, destIdx);
        c.destName = destName;
        c.addedBy = source;
        globalManifest.add(c);
    }

    public void clearManifest() { globalManifest.clear(); }

    // CONFIG FLAGS 
    static final boolean FORCE_SPECIALS_TO_SECURE = true;
    static final boolean RESERVE_ONE_STACK_FOR_SPECIALS = false;
    static final boolean ENABLE_AGGRESSIVE_TOP_SWAPS = true;
    static final int TOP_SWAP_ATTEMPTS = 120;
    static final boolean IGNORE_SPECIAL_IN_BLOCKING = true;

    // HAPUS PER ITEM 
    public void removeContainerById(int id) {
        // 1. Hapus container dengan ID tersebut
        globalManifest.removeIf(c -> c.id == id);
        
        // 2. RE-INDEXING (PENTING)
        // ID harus urut kembali (0, 1, 2...) agar logika GA tidak error
        for (int i = 0; i < globalManifest.size(); i++) {
            globalManifest.get(i).id = i;
        }
    }

    // DATA CLASSES 
    public static class SimulationResult {
        public String consoleOutput;
        public double fitness, revenue, cost, penalty;
        public int unplacedContainers, loadedContainers;
        public List<String> overloadList = new ArrayList<>();
        public List<List<Container>> visualStacks = new ArrayList<>();
        public Ship shipData;
    }

    public static class Container {
        public int id; public double weightTon; public boolean isSpecial; public int destinationIndex; public String destName; public String addedBy; 
        public Container(int id, double weightTon, boolean isSpecial, int destinationIndex) {
            this.id = id; this.weightTon = weightTon; this.isSpecial = isSpecial; this.destinationIndex = destinationIndex;
        }
    }

    public static class Ship {
        public String id; public String name; public double maxTonnage;
        public int stacks; public int stackHeight;
        public Set<Integer> secureStacks; public double[] stackMaxTonnage;
        
        public Ship(String id, String name, double maxTonnage, int stacks, int stackHeight, Set<Integer> secureStacks, double[] stackMaxTonnage) {
            this.id = id; this.name = name; this.maxTonnage = maxTonnage; this.stacks = stacks; this.stackHeight = stackHeight;
            this.secureStacks = secureStacks == null ? new HashSet<>() : secureStacks; this.stackMaxTonnage = stackMaxTonnage;
        }
        
        // FUNGSI PENTING AGAR HTML TIDAK ERROR
        public String getCapString() {
            if (stackMaxTonnage == null) return "";
            return Arrays.stream(stackMaxTonnage)
                         .mapToObj(String::valueOf)
                         .collect(Collectors.joining(";"));
        }

        public int totalSlots() { return stacks * stackHeight; }
        public double totalTonnage() { double s = 0.0; for (double v : stackMaxTonnage) s += v; return s; }
        public int maxSpecialCount() { double t = totalTonnage(); if (t > 100.0) return 3; if (t > 40.0) return 2; return 1; }
    }

    public static class Individual {
        int[] genes; double fitness; boolean evaluated;
        public Individual(int[] genes) { this.genes = genes.clone(); this.evaluated = false; }
        public Individual(int[] genes, boolean copy) { this.genes = copy ? genes.clone() : genes; this.evaluated = false; }
    }

    public static class GAParams {
        int populationSize = 150; 
        int maxGenerations = 300; 
        double crossoverRate = 0.85; 
        double mutationRate = 0.03; 
        int tournamentSize = 3; 
        Random rnd = new Random();

        // DUMMY HARGA 
        double tariffPerTon = 1000.0;        
        double costPerKg = 0.06; 
        double distanceCostPerTonPerKm = 0.015; 
        double specialExtraCostPerTon = 150.0; 

        // DENDA  
        double penaltyOverloadPerTon = 15000.0; 
        double penaltyPerBlockingMove = 800.0; 
        double penaltyUnplacedContainer = 500.0; 
        
        double[][] perTonCost = null; 
        double penaltySpecialStacking = 12000.0; 
        double penaltySpecialExceed = 20000.0;
    }

    public static class EvalResult {
        double revenue, cost, penalty, fitness; int blockingCount, unplacedCount, specialNotSecureCount;
        double overloadTon, blockingPenalty, overloadPenalty, unplacedPenalty, specialNotSecurePenalty;
        double[] stackWeights; int[] specialsPerStack; List<List<Integer>> stacksList;
    }

    public static class Evaluator {
        List<Container> containers; Ship ship; List<String> route; double[][] distances; GAParams p;
        public Evaluator(List<Container> containers, Ship ship, List<String> route, double[][] distances, GAParams p) {
            this.containers = containers; this.ship = ship; this.route = route; this.distances = distances; this.p = p;
        }
        private int[] computeVisitOrder() {
            int n = (route == null) ? 0 : route.size(); int[] order = new int[n];
            if (n == 0) return order;
            if (distances == null || distances.length < n) { for (int i = 0; i < n; i++) order[i] = i; return order; }
            Arrays.fill(order, Integer.MAX_VALUE); boolean[] visited = new boolean[n]; int current = 0; visited[current] = true; order[current] = 0; int rank = 1;
            while (rank < n) {
                int best = -1; double bestDist = Double.MAX_VALUE;
                for (int j = 0; j < n; j++) { if (visited[j]) continue; double d = (current < distances.length && j < distances[current].length) ? distances[current][j] : Double.MAX_VALUE; if (d < bestDist) { bestDist = d; best = j; } }
                if (best == -1) break; visited[best] = true; order[best] = rank++; current = best;
            } return order;
        }
        private double perTonCostAlongRoute(int destIndex) {
            if (destIndex <= 0) return 0.0; double sum = 0.0;
            for (int i = 0; i < destIndex; i++) { int a = i, b = i + 1; if (distances != null && a < distances.length && b < distances[a].length) sum += p.distanceCostPerTonPerKm * distances[a][b]; } return sum;
        }
        public double evaluate(Individual ind) { EvalResult r = evaluateDetailed(ind); ind.fitness = r.fitness; ind.evaluated = true; return r.fitness; }

        public EvalResult evaluateDetailed(Individual ind) {
            EvalResult res = new EvalResult();
            int S = ship.stacks;
            int containerCount = containers.size();

            // 1. Inisialisasi
            List<List<Integer>> stacksList = new ArrayList<>();
            double[] currentStackWeights = new double[S]; 
            for (int s = 0; s < S; s++) { stacksList.add(new ArrayList<>()); currentStackWeights[s] = 0.0; }
            List<Integer> loadedIndices = new ArrayList<>();
            List<Integer> rejectedIndices = new ArrayList<>(); 

            // 2. Logic Loading (Reject jika Overload)
            for (int genePos = 0; genePos < containerCount; genePos++) {
                int contIndex = ind.genes[genePos];
                Container c = containers.get(contIndex);
                int stackIdx = genePos % S;
                boolean slotAvailable = stacksList.get(stackIdx).size() < ship.stackHeight;
                boolean weightSafe = (currentStackWeights[stackIdx] + c.weightTon) <= ship.stackMaxTonnage[stackIdx];
                if (slotAvailable && weightSafe) {
                    stacksList.get(stackIdx).add(contIndex);
                    currentStackWeights[stackIdx] += c.weightTon;
                    loadedIndices.add(contIndex);
                } else {
                    rejectedIndices.add(contIndex);
                }
            }

            // 3. Sorting & Check
            int[] visitOrder = computeVisitOrder();
            for (int s = 0; s < S; s++) {
                List<Integer> st = stacksList.get(s);
                Collections.sort(st, (a, b) -> {
                    int va = (containers.get(a).destinationIndex >= 0 && containers.get(a).destinationIndex < visitOrder.length) ? visitOrder[containers.get(a).destinationIndex] : Integer.MAX_VALUE;
                    int vb = (containers.get(b).destinationIndex >= 0 && containers.get(b).destinationIndex < visitOrder.length) ? visitOrder[containers.get(b).destinationIndex] : Integer.MAX_VALUE;
                    if (va == Integer.MAX_VALUE && vb == Integer.MAX_VALUE) return 0; if (va == Integer.MAX_VALUE) return 1; if (vb == Integer.MAX_VALUE) return -1;
                    return Integer.compare(vb, va); 
                });
            }

            // 4. Hitung Keuangan
            double revenue = 0.0, cost = 0.0, penalty = 0.0;
            for (int idx : loadedIndices) {
                Container c = containers.get(idx);
                revenue += p.tariffPerTon * c.weightTon;
                cost += p.costPerKg * (c.weightTon * 1000.0);
                if (c.isSpecial) cost += p.specialExtraCostPerTon * c.weightTon;
                cost += perTonCostAlongRoute(c.destinationIndex) * c.weightTon;
            }
            res.unplacedCount = rejectedIndices.size();
            for(int idx : rejectedIndices) {
                Container c = containers.get(idx);
                penalty += c.weightTon * p.penaltyUnplacedContainer; 
            }
            res.unplacedPenalty = penalty;

            // 5. Setup Report & Penalties Lain
            double[] stackWeights = new double[S]; int[] specialsPerStack = new int[S];
            for (int s = 0; s < S; s++) { double w = 0.0; int spec = 0; for (int idx : stacksList.get(s)) { Container c = containers.get(idx); w += c.weightTon; if (c.isSpecial) spec++; } stackWeights[s] = w; specialsPerStack[s] = spec; }
            res.stackWeights = stackWeights; res.specialsPerStack = specialsPerStack; res.stacksList = stacksList;

            double overloadSum = 0.0;
            for (int s = 0; s < S; s++) { double over = stackWeights[s] - ship.stackMaxTonnage[s]; if (over > 0.001) overloadSum += over; }
            if (overloadSum > 0.0) { res.overloadTon = overloadSum; res.overloadPenalty = overloadSum * p.penaltyOverloadPerTon; penalty += res.overloadPenalty; }

            int specialNotSecure = 0;
            for (int s = 0; s < S; s++) { boolean stackIsSecure = ship.secureStacks.contains(s); for (int idx : stacksList.get(s)) { if (containers.get(idx).isSpecial && !stackIsSecure) specialNotSecure++; } }
            res.specialNotSecureCount = specialNotSecure; res.specialNotSecurePenalty = specialNotSecure * 5000.0; penalty += res.specialNotSecurePenalty;

            int totalPlacedSpecials = 0;
            for (int s = 0; s < S; s++) { totalPlacedSpecials += specialsPerStack[s]; if (specialsPerStack[s] > 1) penalty += (specialsPerStack[s] - 1) * p.penaltySpecialStacking; }
            if (totalPlacedSpecials > ship.maxSpecialCount()) { penalty += (totalPlacedSpecials - ship.maxSpecialCount()) * p.penaltySpecialExceed; }

            int blockingCount = 0;
            for (int s = 0; s < S; s++) { List<Integer> stack = stacksList.get(s); for (int lower = 0; lower < stack.size(); lower++) { Container lowerC = containers.get(stack.get(lower)); int lowerDest = lowerC.destinationIndex; for (int above = lower + 1; above < stack.size(); above++) { Container aboveC = containers.get(stack.get(above)); int aboveDest = aboveC.destinationIndex; if (IGNORE_SPECIAL_IN_BLOCKING && (lowerC.isSpecial || aboveC.isSpecial)) continue; int lowerOrder = (lowerDest >= 0 && lowerDest < visitOrder.length) ? visitOrder[lowerDest] : Integer.MAX_VALUE; int aboveOrder = (aboveDest >= 0 && aboveDest < visitOrder.length) ? visitOrder[aboveDest] : Integer.MAX_VALUE; if (aboveOrder > lowerOrder) blockingCount++; } } }
            res.blockingCount = blockingCount; res.blockingPenalty = blockingCount * p.penaltyPerBlockingMove; penalty += res.blockingPenalty;

            // REVENUE 
            res.revenue = revenue;
            res.cost = cost;
            res.penalty = penalty;
            res.fitness = revenue - cost - penalty;
            return res;
        }
    }

    // HELPER METHODS 
    static int[] randomPermutation(int n, Random rnd) { int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = i; for (int i = n - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); int tmp = a[i]; a[i] = a[j]; a[j] = tmp; } return a; }
    static void placeSpecialsOnTopPerStack(Individual ind, List<Container> containers, Ship ship) { int S = ship.stacks; int slots = ship.totalSlots(); int placed = Math.min(containers.size(), slots); if (S <= 0 || placed <= 0) return; List<List<Integer>> posPerStack = new ArrayList<>(); for(int s=0; s<S; s++) posPerStack.add(new ArrayList<>()); for(int pos=0; pos<placed; pos++) posPerStack.get(pos % S).add(pos); for(int s=0; s<S; s++) { List<Integer> positions = posPerStack.get(s); if(positions.isEmpty()) continue; List<Integer> nonSpec = new ArrayList<>(); List<Integer> spec = new ArrayList<>(); for(int pos : positions) { int cidx = ind.genes[pos]; if(containers.get(cidx).isSpecial) spec.add(cidx); else nonSpec.add(cidx); } int writeIdx = 0; for(int x : nonSpec) { ind.genes[positions.get(writeIdx++)] = x; } for(int x : spec) { ind.genes[positions.get(writeIdx++)] = x; } } }
    static void repairSpecialPlacementDefault(Individual ind, List<Container> containers, Ship ship) { int S = ship.stacks; int placed = Math.min(containers.size(), ship.totalSlots()); List<List<Integer>> posPerStack = new ArrayList<>(); for(int s=0; s<S; s++) posPerStack.add(new ArrayList<>()); for(int pos=0; pos<placed; pos++) posPerStack.get(pos % S).add(pos); List<Integer> specialPositions = new ArrayList<>(); for(int pos=0; pos<placed; pos++) if(containers.get(ind.genes[pos]).isSpecial) specialPositions.add(pos); LinkedList<Integer> availableStacks = new LinkedList<>(); List<Integer> secureAvail = new ArrayList<>(); for(int s=0; s<S; s++) { boolean hasSpec = false; for(int pos : posPerStack.get(s)) if(containers.get(ind.genes[pos]).isSpecial) { hasSpec = true; break; } if(!hasSpec) { if(ship.secureStacks.contains(s)) secureAvail.add(s); else availableStacks.add(s); } } Collections.shuffle(secureAvail, new Random(ship.hashCode())); for(int s : secureAvail) availableStacks.addFirst(s); for(int pos : new ArrayList<>(specialPositions)) { if(availableStacks.isEmpty()) break; int targetStack = availableStacks.removeFirst(); List<Integer> tgtPosList = posPerStack.get(targetStack); if(tgtPosList.isEmpty()) continue; int tgtPos = tgtPosList.get(tgtPosList.size()-1); int tmp = ind.genes[pos]; ind.genes[pos] = ind.genes[tgtPos]; ind.genes[tgtPos] = tmp; specialPositions.remove((Integer)pos); } }
    static void repairSpecialPlacementForceSecure(Individual ind, List<Container> containers, Ship ship) { int S = ship.stacks; int placed = Math.min(containers.size(), ship.totalSlots()); List<List<Integer>> posPerStack = new ArrayList<>(); for(int s=0; s<S; s++) posPerStack.add(new ArrayList<>()); for(int pos=0; pos<placed; pos++) posPerStack.get(pos % S).add(pos); List<Integer> specialPositions = new ArrayList<>(); for(int pos=0; pos<placed; pos++) if(containers.get(ind.genes[pos]).isSpecial) specialPositions.add(pos); LinkedList<Integer> securePositions = new LinkedList<>(); for(int s : ship.secureStacks) { List<Integer> list = posPerStack.get(s); for(int k=list.size()-1; k>=0; k--) securePositions.add(list.get(k)); } for(int pos : new ArrayList<>(specialPositions)) { if(securePositions.isEmpty()) break; int tgt = securePositions.removeFirst(); int tmp = ind.genes[pos]; ind.genes[pos] = ind.genes[tgt]; ind.genes[tgt] = tmp; specialPositions.remove((Integer)pos); } }
    static void balanceOverloadedStacks(Individual ind, List<Container> containers, Ship ship) { int S = ship.stacks; int placed = Math.min(containers.size(), ship.totalSlots()); List<List<Integer>> posPerStack = new ArrayList<>(); for(int s=0; s<S; s++) posPerStack.add(new ArrayList<>()); for(int pos=0; pos<placed; pos++) posPerStack.get(pos % S).add(pos); double[] stackWeights = new double[S]; for(int s=0; s<S; s++) { double w = 0.0; for(int pos : posPerStack.get(s)) w += containers.get(ind.genes[pos]).weightTon; stackWeights[s] = w; } boolean improved = true; int iter = 0; while(improved && iter < 100) { improved = false; iter++; for(int sOver=0; sOver<S; sOver++) { double over = stackWeights[sOver] - ship.stackMaxTonnage[sOver]; if(over <= 1e-9) continue; List<Integer> overPositions = posPerStack.get(sOver); for(int idxPos=overPositions.size()-1; idxPos>=0 && over>1e-9; idxPos--) { int posOver = overPositions.get(idxPos); int contIdxOver = ind.genes[posOver]; double wCont = containers.get(contIdxOver).weightTon; for(int sUnder=0; sUnder<S; sUnder++) { if(sUnder == sOver) continue; double capLeft = ship.stackMaxTonnage[sUnder] - stackWeights[sUnder]; if(capLeft + 1e-9 >= wCont) { List<Integer> underPositions = posPerStack.get(sUnder); if(underPositions.isEmpty()) continue; int posUnder = underPositions.get(underPositions.size()-1); int contIdxUnder = ind.genes[posUnder]; ind.genes[posOver] = contIdxUnder; ind.genes[posUnder] = contIdxOver; stackWeights[sOver] -= wCont; stackWeights[sOver] += containers.get(contIdxUnder).weightTon; stackWeights[sUnder] -= containers.get(contIdxUnder).weightTon; stackWeights[sUnder] += wCont; over = stackWeights[sOver] - ship.stackMaxTonnage[sOver]; improved = true; break; } } if(improved) break; } if(improved) break; } } }
    static void enforceVisitOrder(Individual ind, List<Container> containers, Ship ship, List<String> route, double[][] distances) { int S = ship.stacks; int placed = Math.min(containers.size(), ship.totalSlots()); List<List<Integer>> posPerStack = new ArrayList<>(); for(int s=0; s<S; s++) posPerStack.add(new ArrayList<>()); for(int pos=0; pos<placed; pos++) posPerStack.get(pos % S).add(pos); Evaluator tmpEv = new Evaluator(containers, ship, route, distances, new GAParams()); int[] vo = tmpEv.computeVisitOrder(); for(int s=0; s<S; s++) { List<Integer> positions = posPerStack.get(s); if(positions.isEmpty()) continue; List<Integer> contIdxs = new ArrayList<>(); for(int pos : positions) contIdxs.add(ind.genes[pos]); contIdxs.sort((a, b) -> { int va = (containers.get(a).destinationIndex >= 0 && containers.get(a).destinationIndex < vo.length) ? vo[containers.get(a).destinationIndex] : Integer.MAX_VALUE; int vb = (containers.get(b).destinationIndex >= 0 && containers.get(b).destinationIndex < vo.length) ? vo[containers.get(b).destinationIndex] : Integer.MAX_VALUE; if(va == Integer.MAX_VALUE && vb == Integer.MAX_VALUE) return 0; if(va == Integer.MAX_VALUE) return 1; if(vb == Integer.MAX_VALUE) return -1; return Integer.compare(vb, va); }); for(int i=0; i<positions.size(); i++) ind.genes[positions.get(i)] = contIdxs.get(i); } placeSpecialsOnTopPerStack(ind, containers, ship); }
    static void reduceBlockingByTopSwaps(Individual ind, List<Container> containers, Ship ship, List<String> route, double[][] distances, GAParams params, int attempts) {}
    static void repairSpecialPlacement(Individual ind, List<Container> containers, Ship ship) { if(FORCE_SPECIALS_TO_SECURE) repairSpecialPlacementForceSecure(ind, containers, ship); else repairSpecialPlacementDefault(ind, containers, ship); placeSpecialsOnTopPerStack(ind, containers, ship); }
    static Individual[] orderCrossover(Individual p1, Individual p2, Random rnd) { int n = p1.genes.length; int[] c1 = new int[n]; Arrays.fill(c1, -1); int[] c2 = new int[n]; Arrays.fill(c2, -1); int a = rnd.nextInt(n), b = rnd.nextInt(n); if(a > b) { int t=a; a=b; b=t; } for(int i=a; i<=b; i++) { c1[i]=p1.genes[i]; c2[i]=p2.genes[i]; } int idx= (b+1)%n, pIdx=(b+1)%n; while(idx!=a) { int val=p2.genes[pIdx]; boolean present=false; for(int k=a; k<=b; k++) if(c1[k]==val) { present=true; break; } if(!present) { c1[idx]=val; idx=(idx+1)%n; } pIdx=(pIdx+1)%n; } idx=(b+1)%n; pIdx=(b+1)%n; while(idx!=a) { int val=p1.genes[pIdx]; boolean present=false; for(int k=a; k<=b; k++) if(c2[k]==val) { present=true; break; } if(!present) { c2[idx]=val; idx=(idx+1)%n; } pIdx=(pIdx+1)%n; } return new Individual[]{new Individual(c1), new Individual(c2)}; }
    static void mutateSwap(Individual ind, Random rnd) { int n = ind.genes.length; int i=rnd.nextInt(n), j=rnd.nextInt(n); int tmp=ind.genes[i]; ind.genes[i]=ind.genes[j]; ind.genes[j]=tmp; ind.evaluated=false; }

    // METHOD runOptimization 
    public SimulationResult runOptimization() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
        PrintStream ps = new PrintStream(baos); 
        PrintStream oldOut = System.out; 
        System.setOut(ps);
        
        List<String> route = new ArrayList<>(Arrays.asList("Jakarta", "Surabaya", "Batam", "Semarang", "Belawan", "Makassar", "Balikpapan"));
        
        List<String> orderedRoute = new ArrayList<>();
        orderedRoute.add(route.get(currentOriginIdx));
        Map<Integer, Integer> mapOldToNew = new HashMap<>();
        mapOldToNew.put(currentOriginIdx, 0);
        int newIdx = 1;
        for(int i=0; i<route.size(); i++) {
            if(i != currentOriginIdx) {
                orderedRoute.add(route.get(i));
                mapOldToNew.put(i, newIdx++);
            }
        }
        
        double[][] originalDist = new double[][] { {0,780,1150,450,2209,1700,1300},{780,0,1000,330,1500,800,830},{1150,1000,0,800,650,2200,1600},{450,330,800,0,1500,1200,1100},{2209,1500,650,1500,0,2000,1800},{1700,800,2200,1200,2000,0,530},{1300,830,1600,1100,1800,530,0} };
        double[][] distances = new double[7][7];
        for(int i=0; i<7; i++) {
            for(int j=0; j<7; j++) {
                int idxI = route.indexOf(orderedRoute.get(i));
                int idxJ = route.indexOf(orderedRoute.get(j));
                distances[i][j] = originalDist[idxI][idxJ]; 
            }
        }

        List<Container> containers = new ArrayList<>();
        for(Container c : globalManifest) {
            int newDestIdx = orderedRoute.indexOf(c.destName);
            if (newDestIdx == -1) newDestIdx = 1; 
            Container newC = new Container(c.id, c.weightTon, c.isSpecial, newDestIdx);
            newC.destName = c.destName;
            newC.addedBy = c.addedBy;
            containers.add(newC);
        }

        Ship ship = this.currentShip;
        GAParams params = new GAParams();
        Individual best = null;
        
        // PROSES GENETIC ALGORITHM 
        if(!containers.isEmpty()) {
            List<Individual> pop = new ArrayList<>();
            for(int i=0; i<params.populationSize; i++) {
                Individual ind = new Individual(randomPermutation(containers.size(), params.rnd));
                repairSpecialPlacement(ind, containers, ship); 
                enforceVisitOrder(ind, containers, ship, orderedRoute, distances);
                new Evaluator(containers, ship, orderedRoute, distances, params).evaluate(ind); 
                pop.add(ind);
            }
            pop.sort((a,b)->Double.compare(b.fitness, a.fitness)); 
            best = new Individual(pop.get(0).genes, true); 
            best.fitness = pop.get(0).fitness;
            
            for(int gen=1; gen<=params.maxGenerations; gen++) {
                List<Individual> offspring = new ArrayList<>();
                while(offspring.size() < params.populationSize) {
                    Individual p1 = pop.get(params.rnd.nextInt(pop.size())); 
                    Individual p2 = pop.get(params.rnd.nextInt(pop.size()));
                    if(params.rnd.nextDouble() < params.crossoverRate) {
                        Individual[] kids = orderCrossover(p1, p2, params.rnd);
                        if(params.rnd.nextDouble() < params.mutationRate) mutateSwap(kids[0], params.rnd);
                        if(params.rnd.nextDouble() < params.mutationRate) mutateSwap(kids[1], params.rnd);
                        repairSpecialPlacement(kids[0], containers, ship); 
                        repairSpecialPlacement(kids[1], containers, ship);
                        enforceVisitOrder(kids[0], containers, ship, orderedRoute, distances); 
                        enforceVisitOrder(kids[1], containers, ship, orderedRoute, distances);
                        new Evaluator(containers, ship, orderedRoute, distances, params).evaluate(kids[0]); 
                        new Evaluator(containers, ship, orderedRoute, distances, params).evaluate(kids[1]);
                        offspring.add(kids[0]); 
                        if(offspring.size()<params.populationSize) offspring.add(kids[1]);
                    }
                }
                pop.addAll(offspring); 
                pop.sort((a,b)->Double.compare(b.fitness, a.fitness)); 
                pop = pop.subList(0, params.populationSize);
                if(pop.get(0).fitness > best.fitness) { 
                    best = new Individual(pop.get(0).genes, true); 
                    best.fitness = pop.get(0).fitness; 
                }
            }
        } else {
            best = new Individual(new int[0]); best.fitness = 0;
        }
        
        Evaluator ev = new Evaluator(containers, ship, orderedRoute, distances, params);
        EvalResult res = (!containers.isEmpty()) ? ev.evaluateDetailed(best) : new EvalResult();
        
        System.out.flush(); 
        System.setOut(oldOut);

        SimulationResult simRes = new SimulationResult();
        simRes.consoleOutput = baos.toString(); 
        simRes.fitness = res.fitness; 
        simRes.revenue = res.revenue; 
        simRes.cost = res.cost; 
        simRes.penalty = res.penalty;
        simRes.unplacedContainers = res.unplacedCount; 
        
        // Hitung total container yang BENAR-BENAR masuk 
        int realLoadedCount = 0;
        if (res.stacksList != null) {
            for(List<Integer> s : res.stacksList) realLoadedCount += s.size();
        }
        simRes.loadedContainers = realLoadedCount;
        simRes.shipData = ship;

        if(!containers.isEmpty()) {
            Set<Integer> placedIndices = new HashSet<>(); // Gunakan index list container, bukan ID

            // 1. Masukkan data ke Visualisasi Stack
            for(int s=0; s<ship.stacks; s++) {
                List<Container> stackContainers = new ArrayList<>();
                List<Integer> containerIndices = res.stacksList.get(s);
                for(Integer idx : containerIndices) {
                    stackContainers.add(containers.get(idx));
                    placedIndices.add(idx); // Tandai bahwa index ini sukses dimuat
                }
                while(stackContainers.size() < ship.stackHeight) stackContainers.add(null);
                Collections.reverse(stackContainers); 
                simRes.visualStacks.add(stackContainers);
            }

            // 2. Generate List Overload (Yang Ditolak/Rejected)
            // Loop semua container asli, cek apakah index-nya ada di placedIndices
            for(int i=0; i<containers.size(); i++) {
                if(!placedIndices.contains(i)) {
                    Container c = containers.get(i);
                    // Tambahkan ke list overload/rejected untuk ditampilkan di UI
                    simRes.overloadList.add("ID " + c.id + " (" + c.destName + ") - " + c.weightTon + " Tons [REJECTED]");
                }
            }
        }
        return simRes;
    }
    public void generateMap() { try { String projectDir = System.getProperty("user.dir"); String os = System.getProperty("os.name").toLowerCase(); String pythonCmd = os.contains("win") ? "python" : "python3"; ProcessBuilder pb = new ProcessBuilder(pythonCmd, projectDir + "/scripts/map_generator.py"); pb.directory(new java.io.File(projectDir)); pb.redirectErrorStream(true); Process p = pb.start(); p.waitFor(); } catch (Exception e) { e.printStackTrace(); } }
}