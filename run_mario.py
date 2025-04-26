import subprocess
import re
import csv
from collections import defaultdict
import os
import time

def run_experiment(num_runs=20):
    # Dictionary to store all results
    all_results = defaultdict(list)
    
    # Compile the Java files first
    compile_java_files()
    
    for run in range(1, num_runs + 1):
        print(f"\n=== Starting run {run}/{num_runs} ===")
        start_time = time.time()
        
        # Run the Java program and capture output
        result = subprocess.run(
            ["java", "-cp", "bin", "PlayEAMario"],
            capture_output=True,
            text=True
        )
        
        # Print errors if any
        if result.stderr:
            print("Error during execution:")
            print(result.stderr)
        
        # Parse the output
        output = result.stdout
        parse_success = parse_output(output, run, all_results)
        
        if not parse_success:
            print("Warning: Some generation data might be missing from the output")
        
        elapsed_time = time.time() - start_time
        print(f"Run {run} completed in {elapsed_time:.2f} seconds")
    
    # Save all results to CSV
    save_to_csv(all_results)
    print("\n=== All runs completed ===")

def compile_java_files():
    print("Compiling Java files...")
    compile_result = subprocess.run(
        ["javac", "-d", "bin", "-sourcepath", "src"] + 
        list(find_java_files("src")),
        capture_output=True,
        text=True
    )
    if compile_result.returncode != 0:
        print("Compilation failed:")
        print(compile_result.stderr)
        exit(1)
    print("Compilation successful")

def find_java_files(directory):
    for root, _, files in os.walk(directory):
        for file in files:
            if file.endswith(".java"):
                yield os.path.join(root, file)

def parse_output(output, run, all_results):
    # Pattern to match generation statistics
    pattern = re.compile(
        r"Generation (\d+) - Best: ([\d.]+), Avg: ([\d.]+), Best Completion: ([\d.]+), Avg Completion: ([\d.]+)"
    )
    
    # Find all matches in the output
    matches = pattern.findall(output)
    
    if not matches:
        print("Warning: No generation data found in output")
        return False
    
    for gen, best_fit, avg_fit, best_comp, avg_comp in matches:
        all_results[run].append({
            "generation": int(gen),
            "best_fitness": float(best_fit),
            "average_fitness": float(avg_fit),
            "best_completion": float(best_comp),
            "average_completion": float(avg_comp)
        })
    return True

def save_to_csv(all_results):
    filename = "evolutionary_mario_summary.csv"
    with open(filename, "w", newline="") as csvfile:
        fieldnames = [
            "run", "generation", "best_fitness", "best_completion",
            "average_fitness", "average_completion"
        ]
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()
        
        for run, generations in all_results.items():
            for gen_data in generations:
                writer.writerow({
                    "run": run,
                    "generation": gen_data["generation"],
                    "best_fitness": gen_data["best_fitness"],
                    "best_completion": gen_data["best_completion"],
                    "average_fitness": gen_data["average_fitness"],
                    "average_completion": gen_data["average_completion"]
                })
    print(f"Results saved to {filename}")

if __name__ == "__main__":
    try:
        run_experiment(20)
    except KeyboardInterrupt:
        print("\nExperiment interrupted by user")
        # Save partial results if possible
        if 'all_results' in locals():
            save_to_csv(all_results)
        exit(1)