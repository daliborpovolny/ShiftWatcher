#!/usr/bin/env python3
import os
import sys
import json
import subprocess
import os.path
from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

# ==================== CONFIGURATION ====================
# Replace with your Google Drive Folder ID
FOLDER_ID = "18Gp1loKZBKLaJfKXKrjFqYQkW7wpsKTk"

# Set to "assembleRelease" if you want to deploy release builds
BUILD_TASK = "assembleDebug"
APK_PATH = "app/build/outputs/apk/debug/app-debug.apk"
# =======================================================

CREDS_FILE = 'credentials.json'
SCOPES = ['https://www.googleapis.com/auth/drive']

def get_version_info():
    version_code = None
    version_name = None
    build_gradle = "app/build.gradle.kts"
    if not os.path.exists(build_gradle):
        print(f"Error: {build_gradle} not found. Are you running this from the project root?")
        sys.exit(1)
        
    with open(build_gradle, "r") as f:
        for line in f:
            if "versionCode =" in line:
                version_code = int(line.split("=")[1].strip().split()[0])
            elif "versionName =" in line:
                version_name = line.split("=")[1].strip().split()[0].strip('"').strip("'")
                
    if version_code is None or version_name is None:
        print("Error: Could not extract version code or version name from build.gradle.kts")
        sys.exit(1)
    return version_code, version_name

def build_apk():
    print(f"Building APK using Gradle task: {BUILD_TASK}...")
    # Using gradlew in current directory
    gradlew = "./gradlew"
    if os.name == 'nt': # Windows compatibility
        gradlew = "gradlew.bat"
        
    result = subprocess.run([gradlew, BUILD_TASK], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode != 0:
        print("Build failed!")
        print(result.stderr.decode())
        sys.exit(1)
    print("Build successful!")

def get_drive_service():
    creds = None
    if os.path.exists('token.json'):
        creds = Credentials.from_authorized_user_file('token.json', SCOPES)
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            if not os.path.exists('client_secrets.json'):
                print("\nError: 'client_secrets.json' not found.")
                print("Please create OAuth 2.0 Client credentials (Desktop App) in Google Cloud Console")
                print("and download it as 'client_secrets.json' into the project root directory.")
                sys.exit(1)
            flow = InstalledAppFlow.from_client_secrets_file('client_secrets.json', SCOPES)
            creds = flow.run_local_server(port=0)
        with open('token.json', 'w') as token:
            token.write(creds.to_json())
    return build('drive', 'v3', credentials=creds)

def find_file_in_folder(service, name, folder_id):
    query = f"name = '{name}' and '{folder_id}' in parents and trashed = false"
    results = service.files().list(q=query, spaces='drive', fields='files(id, name)').execute()
    files = results.get('files', [])
    if files:
        return files[0]['id']
    return None

def upload_or_update(service, local_path, mime_type, target_name, folder_id):
    file_id = find_file_in_folder(service, target_name, folder_id)
    media = MediaFileUpload(local_path, mimetype=mime_type, resumable=True)
    
    if file_id:
        print(f"Updating existing {target_name} (ID: {file_id})...")
        file = service.files().update(
            fileId=file_id,
            media_body=media
        ).execute()
    else:
        print(f"Creating new {target_name} in folder...")
        file_metadata = {
            'name': target_name,
            'parents': [folder_id]
        }
        file = service.files().create(
            body=file_metadata,
            media_body=media,
            fields='id'
        ).execute()
        file_id = file.get('id')
    return file_id

def main():
    if FOLDER_ID == "YOUR_GOOGLE_DRIVE_FOLDER_ID":
        print("Error: Please set FOLDER_ID in deploy.py to your target Google Drive Folder ID.")
        sys.exit(1)

    version_code, version_name = get_version_info()
    print(f"Target App Version: {version_name} (Code: {version_code})")

    # 1. Build the APK
    build_apk()

    if not os.path.exists(APK_PATH):
        print(f"Error: Build completed but APK not found at {APK_PATH}")
        sys.exit(1)

    # 2. Get Drive client
    service = get_drive_service()

    # 3. Upload APK
    print("Uploading APK to Google Drive...")
    apk_file_id = upload_or_update(service, APK_PATH, 'application/vnd.android.package-archive', 'latest.apk', FOLDER_ID)
    print(f"APK Uploaded/Updated. File ID: {apk_file_id}")

    # Make the download URL for Google Drive
    download_url = f"https://docs.google.com/uc?export=download&id={apk_file_id}"

    # 4. Generate update_info.json locally
    update_info = {
        "versionCode": version_code,
        "versionName": version_name,
        "downloadUrl": download_url,
        "changelog": "Automatický build verze " + version_name
    }
    
    json_path = 'update_info.json'
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(update_info, f, indent=2, ensure_ascii=False)
        
    # 5. Upload update_info.json
    print("Uploading update_info.json to Google Drive...")
    json_file_id = upload_or_update(service, json_path, 'application/json', 'update_info.json', FOLDER_ID)
    print(f"update_info.json Uploaded/Updated. File ID: {json_file_id}")
    
    # Cleanup local json
    if os.path.exists(json_path):
        os.remove(json_path)

    print("\n==============================================")
    print("DEPLOYMENT COMPLETED SUCCESSFULLY!")
    print(f"Public update_info.json URL:")
    print(f"https://docs.google.com/uc?export=download&id={json_file_id}")
    print("==============================================")

if __name__ == '__main__':
    main()
