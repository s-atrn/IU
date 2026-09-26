import os
import subprocess
import sys
import shutil
import re

# --- CONFIGURATION ---
APP_NAME = "iu.py"
ICON_PATH = "resources/iu.ico"
ISS_FILE = "IU.iss"
ISCC_PATH = r"C:\Program Files\Inno Setup 7\ISCC.exe"
# ---------------------

def run_command(command):
    print(f"\n[RUNNING] {' '.join(command)}")
    result = subprocess.run(command)
    if result.returncode != 0:
        print(f"[ERROR] Command failed with exit code {result.returncode}")
        sys.exit(result.returncode)

def update_iss_version(iss_path, version_str):
    print(f"\n[UPDATING] Setting version in {iss_path} to {version_str}...")
    clean_version = version_str.lstrip('v')
    
    if not os.path.exists(iss_path):
        print(f"[ERROR] Could not find {iss_path}")
        sys.exit(1)
        
    with open(iss_path, "r", encoding="utf-8") as f:
        content = f.read()
        
    content = re.sub(r'AppVersion=.*', f'AppVersion={clean_version}', content)
    content = re.sub(r'OutputBaseFilename=.*', f'OutputBaseFilename=IU-Setup-{version_str}', content)
    
    with open(iss_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("[SUCCESS] IU.iss version updated successfully.")

def force_windowed_spec():
    print("\n[CONFIG] Generating and locking spec file to windowed mode...")
    
    # Clean old build folders
    if os.path.exists("build"):
        shutil.rmtree("build")
        
    # Step 1: Generate the spec file using makespec (removed invalid --noconfirm flag)
    makespec_cmd = [
        sys.executable, "-m", "PyInstaller.utils.cliutils.makespec",
        "--onefile",
        "--windowed",
        f"--icon={ICON_PATH}",
        APP_NAME
    ]
    run_command(makespec_cmd)
    
    # Step 2: Read iu.spec and forcibly ensure console=False
    if os.path.exists("iu.spec"):
        with open("iu.spec", "r", encoding="utf-8") as f:
            spec_content = f.read()
            
        if "console=True" in spec_content:
            spec_content = spec_content.replace("console=True", "console=False")
        elif "console=False" not in spec_content:
            spec_content = spec_content.replace("app.scripts,", "app.scripts,\n    console=False,")
            
        with open("iu.spec", "w", encoding="utf-8") as f:
            f.write(spec_content)
        print("[SUCCESS] iu.spec locked to console=False.")

def main():
    print("=== IU Windows Build & Release Automation ===")
    
    raw_version = input("Enter the release version (e.g., v1.1.0 or 1.1.0): ").strip()
    if not raw_version:
        print("[ERROR] Version cannot be empty.")
        sys.exit(1)
        
    VERSION = raw_version if raw_version.startswith('v') else f"v{raw_version}"
    
    print(f"\nStarting Windows Build Process for {VERSION} ===")
    
    # Step 1: Generate spec, force console=False, and build from spec
    force_windowed_spec()
    
    build_spec_cmd = [
        sys.executable, "-m", "PyInstaller",
        "--noconfirm",
        "iu.spec"
    ]
    run_command(build_spec_cmd)
    
    # Step 2: Update Inno Setup script with the new version
    update_iss_version(ISS_FILE, VERSION)
    
    # Step 3: Compile Inno Setup Installer
    if not os.path.exists(ISCC_PATH):
        print(f"[ERROR] Could not find ISCC.exe at hardcoded path: {ISCC_PATH}")
        sys.exit(1)
        
    print(f"[FOUND] Using ISCC at: {ISCC_PATH}")
    run_command([ISCC_PATH, ISS_FILE])
    
    print(f"\n=== Build Complete! Executable and installer for {VERSION} are ready. ===")

if __name__ == "__main__":
    main()