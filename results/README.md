# STA Benchmark Results — HDFS

## Setup

- **Dataset:** HDFS_v1 (Xu et al., SOSP 2009) via Loghub
- **Preprocessing:** Drain log parsing (pre-computed by Loghub)
- **Sessions:** 575,061 blocks, ~30 log keys, 2.9% anomalous
- **Training:** 80% of normal sessions only (same protocol as DeepLog)
- **Test:** 20% normal + all anomalies
- **VLMC alpha:** 0.01 (deep tree, minimal pruning)
- **Metrics:** Precision, Recall, F1 (best threshold), AUC

## Results

_To be filled after running the benchmark._

| Method | Precision | Recall | F1 | AUC | Time (s) |
|--------|-----------|--------|----|-----|----------|
| VLMC classic | - | - | - | - | - |
| STA beta=0.1 | - | - | - | - | - |
| STA beta=1.0 | - | - | - | - | - |
| STA beta=10.0 | - | - | - | - | - |
| STA auto-beta | - | - | - | - | - |
| DeepLog (published) | 0.9500 | 0.9600 | 0.9550 | - | GPU |

## Auto-Beta Analysis

| Property | Value |
|----------|-------|
| Heuristic beta | - |
| Tree depth | - |
| Mean KL | - |
| Nodes | - |
| Leaves | - |

## How to Reproduce

### 1. Download HDFS Dataset

```bash
# Option A: Full dataset from Zenodo
# https://zenodo.org/record/3227177
# Download: HDFS.log_structured.csv + anomaly_label.csv

# Option B: 2k-line subset for quick testing
wget https://raw.githubusercontent.com/logpai/loghub/master/HDFS/HDFS_2k.log_structured.csv
```

### 2. Build the Project

```bash
mvn clean package -DskipTests
```

### 3. Run the Benchmark

```bash
java -Xmx4g -cp jfitvlmc/target/jfitvlmc-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
  sta.HdfsFullBenchmark \
  --structured-log path/to/HDFS.log_structured.csv \
  --labels path/to/anomaly_label.csv \
  --output results/hdfs_benchmark.csv
```

### 4. Run Mini-Benchmark (no download needed)

```bash
mvn test -pl jfitvlmc -Dtest=HdfsMiniTest
```

## Reference

- Du et al., "DeepLog: Anomaly Detection and Diagnosis from System Logs through Deep Learning", ACM CCS 2017
- Xu et al., "Detecting Large-Scale System Problems by Mining Console Logs", ACM SOSP 2009
