"""경쟁 앱 APK에서 소리 파일을 임시 폴더로 푼다. 측정용이며 프로젝트에는 넣지 않는다."""
import os
import sys
import zipfile

apk = sys.argv[1]
out = sys.argv[2]
os.makedirs(out, exist_ok=True)

z = zipfile.ZipFile(apk)
n = 0
for name in z.namelist():
    if "/sounds/" in name and name.lower().endswith(".mp3"):
        rel = name.split("/sounds/", 1)[1].replace("/", "__")
        with open(os.path.join(out, rel), "wb") as f:
            f.write(z.read(name))
        n += 1
print(n)
