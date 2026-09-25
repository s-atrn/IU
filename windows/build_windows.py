import os
import subprocess
import sys
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
    clean_version = version_str.lstrip('v') # Converts "v1.1.0" to "1.1.0" for Windows
    
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

def main():
    print("=== IU Windows Build & Release Automation ===")
    
    raw_version = input("Enter the release version (e.g., v1.1.0 or 1.1.0): ").strip()
    if not raw_version:
        print("[ERROR] Version cannot be empty.")
        sys.exit(1)
        
    VERSION = raw_version if raw_version.startswith('v') else f"v{raw_version}"
    
    print(f"\nStarting Windows Build Process for {VERSION} ===")
    
    # Step 1: Build Executable with PyInstaller
    pyinstaller_cmd = [
        sys.executable, "-m", "PyInstaller",
        "--onefile",
        "--windowed",
        "--noconfirm",
        f"--icon={ICON_APP if 'ICON_APP' in locals() else ICON_PATH}",
        APP_NAME
    ]
    # Quick fix for icon argument name matching
    pyinstaller_cmd[4] = f"--icon={ICON_PATH}"
    
    run_command(pyinstaller_cmd)
    
    # Step 2: Update Inno Setup script with the new version
    update_iss_version(ISS_FILE, VERSION)
    
    # Step 3: Compile Inno Setup Installer using hardcoded path
    if not os.path.exists(ISCC_PATH):
        print(f"[ERROR] Could not find ISCC.exe at hardcoded path: {ISCC_PATH}")
        sys.exit(1)
        
    print(f"[FOUND] Using ISCC at: {ISCC_PATH}")
    run_command([ISCC_PATH, ISS_FILE])
    
    print(f"\n=== Build Complete! Executable and installer for {VERSION} are ready. ===")

if __name__ == "__main__":
    main()